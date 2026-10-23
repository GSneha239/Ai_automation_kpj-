import { test, expect, Dialog, Download } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import {
  API, BILL_STATUS_OPTIONS, BillListPage, BillRow, CONSOLIDATED_COLUMNS, CORE_COLUMNS, EINVOICE, EXEC, FetchResult,
  INVOICE_TYPE, MODELS, PAGE_SIZES, PAGE_TITLE, REG_TYPE, SELECT_PLACEHOLDER, TOAST,
  dayOffset, ddMmYyyy, login, norm, parseBillDate,
} from '../pages/Billing_page/BillListPage';

/**
 * Billing > List Of Bills  (#/BillList)
 *
 * Everything here is READ ONLY: the tests search, filter, page, open a bill for viewing and export the loaded rows
 * to CSV in the browser. No control that writes to the server or sends a message is clicked (see BillListPage.ts
 * for the list of controls left out and why).
 *
 * Result meaning:
 *   PASS    - the functionality behaves as expected.
 *   FAIL    - the application is broken (assertions are never relaxed to hide this).
 *   BLOCKED - the check needs data the environment does not have. The test is reported as SKIPPED with a
 *             "BLOCKED:" reason and a BLOCKED annotation; "no records" on its own is never treated as a failure.
 *
 * Every test is independent: fresh page, login (DEVHIS_USER / DEVHIS_PASS), open the screen through the menu.
 */

/** How far back the "wide" searches look for bills. */
const RANGE_DAYS = 90;
const today = dayOffset(0);
const yesterday = dayOffset(-1);
const rangeStart = dayOffset(-RANGE_DAYS);
const NO_SUCH_BILL = 'ZZ-NO-SUCH-BILL-000';
const NO_SUCH_MRN = '0000000000';

let bills: BillListPage;
let initialSearch: FetchResult;

test.beforeEach(async ({ page }) => {
  await login(page);
  bills = new BillListPage(page);
  initialSearch = await bills.open();
});

/** Mark the running test BLOCKED (reported as skipped) because the environment lacks the data it needs. */
function blocked(reason: string): never {
  test.info().annotations.push({ type: 'BLOCKED', description: reason });
  test.skip(true, `BLOCKED: ${reason}`);
  throw new Error('unreachable'); // test.skip(true) throws
}

/** Search the last RANGE_DAYS days (up to `to`) and return what the server sent. */
async function searchWideRange(to: Date = today): Promise<FetchResult> {
  await bills.setDateRange(rangeStart, to);
  return bills.search();
}

const billNos = (rows: BillRow[]) => rows.map(r => norm(r.tranno));

test.describe('List Of Bills', () => {
  test('T1 page loads with search panel, tabs, grid and actions', async ({ page }) => {
    await expect(page).toHaveURL(/#\/BillList$/);
    await expect(page).toHaveTitle(PAGE_TITLE);
    await expect(bills.pageTitle()).toContainText(/list of bills/i);

    await test.step('search panel', async () => {
      await expect(bills.searchPanelHeading()).toBeVisible();
      for (const label of ['From Date', 'To Date', 'MRN', 'Bill No.', 'Bill Status', 'Payor Category', 'Location',
        'Department', 'Visit Type', 'Encounter Type', 'Reg. Type', 'Cancelled Bills', 'Type of Invoice', 'E-Invoice']) {
        await expect(bills.form().locator('label').filter({ hasText: new RegExp(`^\\s*${label.replace('.', '\\.')}\\s*$`) }).first(),
          `label "${label}"`).toBeVisible();
      }
      for (const control of [bills.fromDate(), bills.toDate(), bills.mrn(), bills.billNo(), bills.findPatientBtn(),
        bills.searchPatientBtn(), bills.cancelledBills()]) {
        await expect(control).toBeVisible();
      }
      for (const model of [MODELS.billStatus, MODELS.payorCategory, MODELS.location, MODELS.department,
        MODELS.visitType, MODELS.encounterType]) {
        await expect(bills.select2(model), `dropdown ${model}`).toBeVisible();
      }
      await expect(bills.searchBtn()).toBeEnabled();
    });

    await test.step('tabs', async () => {
      await expect(bills.tab('List of Bills')).toBeVisible();
      await expect(bills.tab('Consolidated Bills')).toBeVisible();
      await expect(bills.grid(), 'List of Bills tab is the active one').toBeVisible();
      await expect(bills.consolidatedGrid()).toBeHidden();
    });

    await test.step('grid columns and pager', async () => {
      const headers = await bills.headerTexts();
      for (const column of CORE_COLUMNS) expect(headers, `grid column "${column}"`).toContain(column);
      await expect(bills.pageInput()).toHaveValue('1');
      await expect(bills.pageSizePicker().locator('.select2-chosen')).toHaveText(PAGE_SIZES[0]);
      await expect(bills.pagerBtn('Page back')).toBeDisabled();
      await expect(bills.pagerBtn('Page to first')).toBeDisabled();
    });

    await test.step('actions', async () => {
      await expect(bills.exportBtn()).toBeEnabled();
      // Generate E-Invoice is only available when the E-Invoice filter is "Pending"
      await expect(bills.generateEInvoiceBtn()).toBeDisabled();
    });
  });

  test('T2 default filter values and the search run on load', async () => {
    const todayText = ddMmYyyy(today);

    await test.step('field defaults', async () => {
      await expect(bills.fromDate()).toHaveValue(todayText);
      await expect(bills.toDate()).toHaveValue(todayText);
      await expect(bills.fromDate()).toHaveAttribute('placeholder', 'dd/MM/yyyy');
      await expect(bills.mrn()).toBeEmpty();
      await expect(bills.billNo()).toBeEmpty();
      await expect(bills.chosen(MODELS.billStatus)).toHaveText(SELECT_PLACEHOLDER);
      await expect(bills.chosen(MODELS.payorCategory)).toHaveText(SELECT_PLACEHOLDER);
      await expect(bills.chosen(MODELS.department)).toHaveText('All');
      await expect(bills.chosen(MODELS.visitType)).toHaveText(SELECT_PLACEHOLDER);
      await expect(bills.chosen(MODELS.encounterType)).toHaveText('ALL');
      // Location defaults to the login location, never to the placeholder
      await expect(bills.chosen(MODELS.location)).not.toHaveText(SELECT_PLACEHOLDER);
      await expect(bills.chosen(MODELS.location)).toHaveText(/\S/);
      await expect(bills.nativeSelect(MODELS.consolidatedPayor)).toBeDisabled();
    });

    await test.step('radio and checkbox defaults', async () => {
      await expect(bills.radio('radiosPT', REG_TYPE.All)).toBeChecked();
      await expect(bills.radio('radiosPT', REG_TYPE.OPD)).not.toBeChecked();
      await expect(bills.radio('radiosPT', REG_TYPE.IPD)).not.toBeChecked();
      await expect(bills.radio('radiosBLT', INVOICE_TYPE.All)).toBeChecked();
      await expect(bills.radio('radiosEINS', EINVOICE.All)).toBeChecked();
      await expect(bills.cancelledBills()).not.toBeChecked();
    });

    await test.step("the screen searches today's bills on load", async () => {
      expect(initialSearch.status).toBe(200);
      expect(initialSearch.payload).toMatchObject({ ExecFlag: EXEC.list, FromDate: todayText, ToDate: todayText, PageIndex: 0 });
      expect(Number(initialSearch.payload.Opd_Ipd), 'Reg. Type All').toBe(Number(REG_TYPE.All));
      expect(Number(initialSearch.payload.BillListType), 'Type of Invoice All').toBe(Number(INVOICE_TYPE.All));
      expect(Number(initialSearch.payload.EinvoiceStatus), 'E-Invoice All').toBe(Number(EINVOICE.All));
      // bills.open() already verified that the grid shows exactly initialSearch.rows.length records
      for (const row of initialSearch.rows) {
        expect(parseBillDate(row.date)?.getTime(), `bill ${row.tranno} is dated today`).toBe(today.getTime());
      }
    });
  });

  test('T3 search by date range lists the bills of that period', async () => {
    const result = await searchWideRange();

    await test.step('request carries the entered range', async () => {
      expect(result.payload).toMatchObject({
        ExecFlag: EXEC.list, FromDate: ddMmYyyy(rangeStart), ToDate: ddMmYyyy(today), PageIndex: 0,
      });
    });

    await test.step('every returned bill is dated inside the range', async () => {
      for (const row of result.rows) {
        const date = parseBillDate(row.date);
        expect(date, `bill ${row.tranno} has a readable date (${row.date})`).not.toBeNull();
        expect(date!.getTime(), `bill ${row.tranno} dated ${row.date} is not before From Date`).toBeGreaterThanOrEqual(rangeStart.getTime());
        expect(date!.getTime(), `bill ${row.tranno} dated ${row.date} is not after To Date`).toBeLessThanOrEqual(today.getTime());
      }
    });

    // searchWideRange() already verified the record count (or the empty state when nothing matched)
    if (result.rows.length === 0) blocked(`no bills in the last ${RANGE_DAYS} days - row content could not be checked`);

    await test.step('first page shows the bills the server returned, in the same order', async () => {
      const firstPage = result.rows.slice(0, 10);
      expect(await bills.columnTexts('Bill No.')).toEqual(billNos(firstPage));
      expect(await bills.columnTexts('MRN')).toEqual(firstPage.map(r => norm(r.mrno)));
      expect(await bills.columnTexts('Bill Date')).toEqual(firstPage.map(r => norm(r.date)));
      expect(await bills.columnTexts('OPD / IPD No.')).toEqual(firstPage.map(r => norm(r.opdno)));
    });
  });

  test('T4 Reg. Type, Type of Invoice and E-Invoice radios filter immediately', async () => {
    const all = await searchWideRange(yesterday);

    await test.step('Reg. Type OPD', async () => {
      const r = await bills.searchVia(() => bills.radio('radiosPT', REG_TYPE.OPD).check());
      expect(Number(r.payload.Opd_Ipd)).toBe(0);
      expect(r.rows.filter(x => x.opd_ipd !== 0).map(x => x.tranno), 'only OPD bills are returned').toEqual([]);
      expect(await bills.selectedText(MODELS.encounterType), 'Encounter Type follows Reg. Type').toBe('OP');
      expect(r.rows.length).toBeLessThanOrEqual(all.rows.length);
    });

    await test.step('Reg. Type IPD', async () => {
      const r = await bills.searchVia(() => bills.radio('radiosPT', REG_TYPE.IPD).check());
      expect(Number(r.payload.Opd_Ipd)).toBe(1);
      expect(r.rows.filter(x => x.opd_ipd !== 1).map(x => x.tranno), 'only IPD bills are returned').toEqual([]);
      expect(await bills.selectedText(MODELS.encounterType)).toBe('IP');
      await expect(bills.select2(MODELS.ward), 'Ward filter is offered for IPD').toBeVisible();
    });

    await test.step('Reg. Type All', async () => {
      const r = await bills.searchVia(() => bills.radio('radiosPT', REG_TYPE.All).check());
      expect(Number(r.payload.Opd_Ipd)).toBe(2);
      expect(await bills.selectedText(MODELS.encounterType)).toBe('ALL');
      expect(billNos(r.rows), 'All returns the unfiltered list again').toEqual(billNos(all.rows));
    });

    await test.step('Type of Invoice Bill / CN / DN', async () => {
      const bill = await bills.searchVia(() => bills.radio('radiosBLT', INVOICE_TYPE.Bill).check());
      expect(String(bill.payload.BillListType)).toBe(INVOICE_TYPE.Bill);
      expect(billNos(bill.rows).filter(n => /^(CN|DN)/i.test(n)), 'Bill excludes credit / debit notes').toEqual([]);

      const cn = await bills.searchVia(() => bills.radio('radiosBLT', INVOICE_TYPE.CN).check());
      expect(String(cn.payload.BillListType)).toBe(INVOICE_TYPE.CN);
      expect(billNos(cn.rows).filter(n => !/^CN/i.test(n)), 'CN returns credit notes only').toEqual([]);

      const dn = await bills.searchVia(() => bills.radio('radiosBLT', INVOICE_TYPE.DN).check());
      expect(String(dn.payload.BillListType)).toBe(INVOICE_TYPE.DN);
      expect(billNos(dn.rows).filter(n => !/^DN/i.test(n)), 'DN returns debit notes only').toEqual([]);

      const back = await bills.searchVia(() => bills.radio('radiosBLT', INVOICE_TYPE.All).check());
      expect(billNos(back.rows)).toEqual(billNos(all.rows));
    });

    await test.step('E-Invoice Pending enables Generate E-Invoice', async () => {
      const pending = await bills.searchVia(() => bills.radio('radiosEINS', EINVOICE.Pending).check());
      expect(String(pending.payload.EinvoiceStatus)).toBe(EINVOICE.Pending);
      expect(pending.rows.length).toBeLessThanOrEqual(all.rows.length);
      await expect(bills.generateEInvoiceBtn()).toBeEnabled();
      await bills.searchVia(() => bills.radio('radiosEINS', EINVOICE.All).check());
      await expect(bills.generateEInvoiceBtn()).toBeDisabled();
    });
  });

  test('T5 Bill Status dropdown filters settled and unsettled bills', async () => {
    await test.step('options', async () => {
      expect(await bills.optionTexts(MODELS.billStatus)).toEqual(BILL_STATUS_OPTIONS);
    });

    // range ends yesterday so bills raised while the test runs cannot change the totals
    const all = await searchWideRange(yesterday);

    await bills.pickSelect2(MODELS.billStatus, 'Settled');
    const settled = await bills.search();
    expect(String(settled.payload.BillStatus), 'Settled is sent as status 1').toBe('1');

    await bills.pickSelect2(MODELS.billStatus, 'Unsettled');
    const unsettled = await bills.search();
    expect(String(unsettled.payload.BillStatus), 'Unsettled is sent as status 2').toBe('2');

    await test.step('settled and unsettled split the full list', async () => {
      const settledNos = new Set(billNos(settled.rows));
      expect(billNos(unsettled.rows).filter(n => settledNos.has(n)), 'no bill is both settled and unsettled').toEqual([]);
      expect(settled.rows.length + unsettled.rows.length, 'settled + unsettled = all bills').toBe(all.rows.length);
    });

    await test.step('clearing the status returns the full list', async () => {
      await bills.pickSelect2(MODELS.billStatus, SELECT_PLACEHOLDER);
      const cleared = await bills.search();
      expect(cleared.payload.BillStatus ?? '', 'no status is sent').toBe('');
      expect(billNos(cleared.rows)).toEqual(billNos(all.rows));
    });
  });

  test('T6 Bill No. search finds the bill, and an unknown Bill No. shows the empty state', async () => {
    const all = await searchWideRange();

    await test.step('unknown Bill No. -> empty grid, no error', async () => {
      await bills.clearToasts();
      await bills.billNo().fill(NO_SUCH_BILL);
      const none = await bills.search(); // search() asserts the empty state when the server returns no rows
      expect(none.payload.tranno).toBe(NO_SUCH_BILL);
      expect(none.rows, 'server returns no bill').toEqual([]);
      await bills.expectEmptyState();
      await expect(bills.errorToasts(), 'an empty result is not reported as an error').toHaveCount(0);
      await expect(bills.searchBtn(), 'the screen stays usable').toBeEnabled();
    });

    if (all.rows.length === 0) blocked(`no bills in the last ${RANGE_DAYS} days - there is no Bill No. to search for`);

    await test.step('existing Bill No. -> only that bill', async () => {
      const target = all.rows[0];
      const billNo = norm(target.tranno);
      await bills.billNo().fill(billNo);
      const found = await bills.search();
      expect(found.payload.tranno).toBe(billNo);
      expect(found.rows.length, `bill ${billNo} is found`).toBeGreaterThan(0);
      expect(billNos(found.rows).filter(n => !n.includes(billNo)), 'only matching bills are returned').toEqual([]);
      expect(await bills.columnTexts('Bill No.')).toEqual(billNos(found.rows).slice(0, 10));
      expect(await bills.columnTexts('MRN')).toContain(norm(target.mrno));
    });
  });

  test('T7 date and MRN validation messages', async () => {
    const year = today.getFullYear();
    const todayText = ddMmYyyy(today);

    await test.step('invalid From Date is rejected and reset to today', async () => {
      await bills.setDate(bills.fromDate(), `99/99/${year}`);
      const before = bills.listRequestCount;
      await bills.expectToast(() => bills.searchBtn().click(), TOAST.invalidFrom);
      await expect(bills.fromDate()).toHaveValue(todayText);
      expect(bills.listRequestCount, 'no search is sent').toBe(before);
    });

    await test.step('invalid To Date is rejected and reset to today', async () => {
      await bills.setDate(bills.toDate(), `31/02/${year}`);
      const before = bills.listRequestCount;
      await bills.expectToast(() => bills.searchBtn().click(), TOAST.invalidTo);
      await expect(bills.toDate()).toHaveValue(todayText);
      expect(bills.listRequestCount, 'no search is sent').toBe(before);
    });

    await test.step('From Date after To Date is rejected', async () => {
      await bills.setDate(bills.fromDate(), todayText);
      await bills.setDate(bills.toDate(), ddMmYyyy(dayOffset(-5)));
      const before = bills.listRequestCount;
      await bills.expectToast(() => bills.searchBtn().click(), TOAST.fromAfterTo);
      expect(bills.listRequestCount, 'no search is sent').toBe(before);
      await bills.setDate(bills.toDate(), todayText);
    });

    await test.step('Find Patient needs an MRN', async () => {
      await expect(bills.mrn()).toBeEmpty();
      const before = bills.listRequestCount;
      await bills.expectToast(() => bills.findPatientBtn().click(), TOAST.mrnRequired);
      expect(bills.listRequestCount, 'no search is sent').toBe(before);
    });

    await test.step('Find Patient with an unknown MRN lists nothing', async () => {
      await bills.clearToasts();
      await bills.mrn().fill(NO_SUCH_MRN);
      const none = await bills.searchVia(() => bills.findPatientBtn().click());
      expect(none.payload.MRNO).toBe(NO_SUCH_MRN);
      expect(none.rows).toEqual([]);
      await bills.expectEmptyState();
    });
  });

  test('T8 pagination and page size', async () => {
    const all = await searchWideRange();
    const total = all.rows.length;
    if (total <= 10) blocked(`only ${total} bill(s) in the last ${RANGE_DAYS} days - a second page needs more than 10`);
    const pages = Math.ceil(total / 10);
    const lastPageRows = total - (pages - 1) * 10;
    const before = bills.listRequestCount;

    await test.step('page 1', async () => {
      await expect(bills.maxPages()).toHaveText(new RegExp(`/\\s*${pages}\\s*$`));
      await expect(bills.pagerBtn('Page back')).toBeDisabled();
      await expect(bills.pagerBtn('Page forward')).toBeEnabled();
    });

    await test.step('next page', async () => {
      await bills.pagerBtn('Page forward').click();
      await expect(bills.pageInput()).toHaveValue('2');
      await expect(bills.pagerCount()).toHaveText(new RegExp(`^\\s*11\\s*-\\s*${Math.min(20, total)} of ${total} items\\s*$`));
      expect(await bills.columnTexts('Bill No.')).toEqual(billNos(all.rows.slice(10, 20)));
      await expect(bills.pagerBtn('Page back')).toBeEnabled();
    });

    await test.step('last page', async () => {
      await bills.pagerBtn('Page to last').click();
      await expect(bills.pageInput()).toHaveValue(String(pages));
      await expect(bills.pagerCount()).toHaveText(new RegExp(`-\\s*${total} of ${total} items\\s*$`));
      await expect(bills.rows()).toHaveCount(lastPageRows);
      await expect(bills.pagerBtn('Page forward')).toBeDisabled();
      await expect(bills.pagerBtn('Page to last')).toBeDisabled();
    });

    await test.step('previous and first page', async () => {
      await bills.pagerBtn('Page back').click();
      await expect(bills.pageInput()).toHaveValue(String(pages - 1));
      await bills.pagerBtn('Page to first').click();
      await expect(bills.pageInput()).toHaveValue('1');
      expect(await bills.columnTexts('Bill No.')).toEqual(billNos(all.rows.slice(0, 10)));
    });

    await test.step('20 items per page', async () => {
      await bills.setPageSize('20');
      await expect(bills.pagerCount()).toHaveText(new RegExp(`^\\s*1\\s*-\\s*${Math.min(20, total)} of ${total} items\\s*$`));
      await expect(bills.maxPages()).toHaveText(new RegExp(`/\\s*${Math.ceil(total / 20)}\\s*$`));
    });

    expect(bills.listRequestCount, 'paging is client side - no further search request').toBe(before);
  });

  test('T9 Consolidated Bills tab and Patient Search popup', async () => {
    await test.step('Consolidated Bills tab loads its own list', async () => {
      const result = await bills.searchVia(() => bills.tab('Consolidated Bills').click(), EXEC.consolidated);
      expect(result.payload).toMatchObject({ FromDate: ddMmYyyy(today), ToDate: ddMmYyyy(today) });
      await expect(bills.consolidatedGrid()).toBeVisible();
      await expect(bills.grid()).toBeHidden();
      expect(await bills.headerTexts(bills.consolidatedGrid())).toEqual(CONSOLIDATED_COLUMNS);
      await bills.expectGridTotal(result.rows.length, bills.consolidatedGrid());
      if (result.rows.length > 0) {
        expect(await bills.columnTexts('Bill No.', bills.consolidatedGrid())).toEqual(billNos(result.rows.slice(0, 10)));
      }
    });

    await test.step('back to List of Bills', async () => {
      await bills.tab('List of Bills').click();
      await expect(bills.grid()).toBeVisible();
      await expect(bills.consolidatedGrid()).toBeHidden();
    });

    await test.step('Patient Search popup opens and closes', async () => {
      await bills.waitForAppIdle();
      await bills.searchPatientBtn().click();
      const modal = bills.patientSearchModal();
      await expect(modal).toBeVisible();
      await expect(modal.locator('.modal-header')).toContainText('Patient Search');
      await expect(modal.locator("input[ng-model='PatientData.MRNo']")).toBeVisible();
      await expect(modal.getByRole('button', { name: 'Search', exact: true })).toBeEnabled();
      await bills.waitForAppIdle();
      await modal.locator("button.btn-danger[ng-click='CloseModel();']").click();
      await expect(modal).toBeHidden();
      await expect(bills.searchBtn(), 'the screen is usable again').toBeEnabled();
    });
  });

  test('T10 row View opens the bill', async ({ page }) => {
    await bills.setDateRange(rangeStart, today);
    await bills.searchVia(() => bills.radio('radiosPT', REG_TYPE.OPD).check());
    const opdBills = await bills.searchVia(() => bills.radio('radiosBLT', INVOICE_TYPE.Bill).check());
    if (opdBills.rows.length === 0) blocked(`no OPD bill in the last ${RANGE_DAYS} days - nothing to open`);

    // View is disabled for closed bills; use the first bill on page 1 that can be opened
    const index = opdBills.rows.slice(0, 10).findIndex(r => r.closedbill !== true);
    if (index < 0) blocked('every OPD bill on the first page is closed - View is disabled for all of them');
    const target = opdBills.rows[index];
    const view = bills.viewButtons().nth(index);
    await expect(view).toBeEnabled();

    const [lookup] = await Promise.all([
      page.waitForRequest(r => API.patientLookup.test(r.url()) && (r.postData() ?? '').includes('GetPatientInfo'), { timeout: 60_000 }),
      view.click(),
    ]);
    await expect(page).toHaveURL(/#\/OPDBill$/);
    await expect(bills.pageTitle()).toContainText(/opd bill/i);
    expect(lookup.postDataJSON().MRNo, 'the bill screen loads the patient of the selected bill').toBe(norm(target.mrno));
    await expect(page.locator("input[ng-model='PatientDetails.MRNo']")).toHaveValue(norm(target.mrno));
  });

  test('T11 Export to Excel downloads the listed bills as CSV', async ({ page }) => {
    const all = await searchWideRange();
    if (all.rows.length === 0) blocked(`no bills in the last ${RANGE_DAYS} days - there is nothing to export`);

    const [download] = await Promise.all([page.waitForEvent('download'), bills.exportBtn().click()]);
    expect(download.suggestedFilename()).toMatch(/^BillList_\d{4}-\d{2}-\d{2}\.csv$/);
    const filePath = await download.path();
    expect(filePath, 'download completed').toBeTruthy();
    const csv = await readFile(filePath, 'utf8');
    const lines = csv.split(/\r?\n/).filter(line => line.trim() !== '');

    for (const column of ['Bill Date', 'Bill No.', 'MRN', 'OPD / IPD No.', 'Patient Name', 'Payor', 'Total Bill Amount', 'Net Bill Amount']) {
      expect(lines[0], `CSV header has "${column}"`).toContain(column);
    }
    expect(lines.length, 'one line per bill plus the header').toBeGreaterThanOrEqual(all.rows.length + 1);
    expect(billNos(all.rows).filter(n => !csv.includes(n)), 'every listed bill is in the export').toEqual([]);
    await expect(page, 'exporting does not leave the screen').toHaveURL(/#\/BillList$/);
  });

  test("T12 Enter in the MRN field looks up the patient and lists that patient's bills", async ({ page }) => {
    // Enter in MRN runs SearchPatientByMRNo: POST FetchPatientData (GetPatientInfo); when the patient exists the
    // screen then searches the bills of that MRN, otherwise it shows "Patient Not Found!".
    //
    // Side effect seen in the browser, recorded here but NOT asserted: "Export to Excel" is the only type="submit"
    // button of form#billList, so the browser's implicit form submission also clicks it on Enter (a CSV download
    // when rows are listed, the "No data available to export!" alert when not). It is reported as an
    // "observation" annotation on this test so it stays visible without failing the MRN workflow.
    const sideEffects: string[] = [];
    const onDownload = (d: Download) => { sideEffects.push(`download ${d.suggestedFilename()}`); };
    const onDialog = (d: Dialog) => { sideEffects.push(`alert "${d.message()}"`); void d.dismiss().catch(() => {}); };
    page.on('download', onDownload);
    page.on('dialog', onDialog);

    const all = await searchWideRange();

    await test.step('unknown MRN + Enter -> "Patient Not Found!", bill list untouched', async () => {
      await bills.clearToasts();
      await bills.mrn().fill(NO_SUCH_MRN);
      const before = bills.listRequestCount;
      const [lookup] = await Promise.all([
        page.waitForRequest(r => API.patientLookup.test(r.url()), { timeout: 30_000 }),
        bills.mrn().press('Enter'),
      ]);
      expect(lookup.postDataJSON()).toMatchObject({ ExecFlag: 'GetPatientInfo', MRNo: NO_SUCH_MRN });
      await expect(bills.toast(TOAST.patientNotFound)).toBeVisible();
      await bills.waitForAppIdle();
      expect(bills.listRequestCount, 'no bill search is sent for an unknown patient').toBe(before);
      await bills.expectGridTotal(all.rows.length);
    });

    if (all.rows.length === 0) blocked(`no bills in the last ${RANGE_DAYS} days - there is no MRN with bills to look up`);

    await test.step("existing MRN + Enter -> that patient's bills", async () => {
      const mrn = norm(all.rows[0].mrno);
      await bills.clearToasts();
      await bills.mrn().fill(mrn);
      const lookupRequest = page.waitForRequest(
        r => API.patientLookup.test(r.url()) && (r.postData() ?? '').includes('GetPatientInfo'), { timeout: 30_000 });
      const result = await bills.searchVia(() => bills.mrn().press('Enter'));
      expect((await lookupRequest).postDataJSON()).toMatchObject({ ExecFlag: 'GetPatientInfo', MRNo: mrn });
      expect(result.payload.MRNO, 'the bill search is filtered by the MRN').toBe(mrn);
      expect(result.rows.length, `patient ${mrn} has bills in the range`).toBeGreaterThan(0);
      expect(result.rows.filter(r => norm(r.mrno) !== mrn).map(r => r.tranno), 'only bills of that patient').toEqual([]);
      expect(new Set(await bills.columnTexts('MRN')), 'grid lists that MRN only').toEqual(new Set([mrn]));
      await expect(bills.mrn()).toHaveValue(mrn);
      await expect(bills.toast(TOAST.patientNotFound)).toHaveCount(0);
      await expect(bills.errorToasts()).toHaveCount(0);
    });

    page.off('download', onDownload);
    page.off('dialog', onDialog);
    if (sideEffects.length > 0) {
      test.info().annotations.push({
        type: 'observation',
        description: `Enter in MRN also triggered Export to Excel (${sideEffects.join('; ')}) - not asserted`,
      });
    }
  });
});
