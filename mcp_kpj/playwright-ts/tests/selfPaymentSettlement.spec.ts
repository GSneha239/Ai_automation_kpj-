import { test, expect } from '@playwright/test';
import { dayOffset, ddMmYyyy, login, norm } from './pages/BillListPage';
import {
  API, BILL_TYPE_OPTIONS, CASH_COUNTER_PLACEHOLDER, EXEC, MODELS, PAGE_TITLE, PAYMENT_COLUMNS, PatientSearchResult,
  REG_TYPE, SERVICE_BILL_COLUMNS, SelfPaymentSettlementPage, SettlementBill, TOAST, fieldAmount, gridAmount,
} from './pages/SelfPaymentSettlementPage';

/**
 * Billing > Payment Settlement > Self  (#/SelfPyamentSettlement)
 *
 * The tests drive the live screen and the live backend - nothing is mocked. They are READ ONLY: they search,
 * select bills, open the Payment Window and cancel it. The Payment Window's OK button is never clicked, because it
 * posts a real receipt against a real bill on the shared DevHIS environment and cannot be undone from this screen
 * (see SelfPaymentSettlementPage.ts).
 *
 * Result meaning:
 *   PASS    - the functionality behaves as expected.
 *   FAIL    - the application is broken (assertions are never relaxed to hide this).
 *   BLOCKED - the check needs data the environment does not have. The test is reported as SKIPPED with a
 *             "BLOCKED:" reason; no record is ever invented.
 *
 * Test patient: the tests need a real patient with an outstanding self-pay OPD bill. Set DEVHIS_SELF_MRN to use a
 * specific MRN; otherwise the MRN is looked up through the application's own Bill List search.
 *
 * Every test is independent: fresh page, login (DEVHIS_USER / DEVHIS_PASS), open the screen through the menu.
 */

const today = dayOffset(0);
const todayText = ddMmYyyy(today);
const NO_SUCH_MRN = '0000000000';
/** How many candidate MRNs are tried before the data-dependent tests are reported as BLOCKED. */
const MAX_CANDIDATES = 8;

let screen: SelfPaymentSettlementPage;

test.beforeEach(async ({ page }) => {
  await login(page);
  screen = new SelfPaymentSettlementPage(page);
  await screen.open();
});

/** Mark the running test BLOCKED (reported as skipped) because the environment lacks the data it needs. */
function blocked(reason: string): never {
  test.info().annotations.push({ type: 'BLOCKED', description: reason });
  test.skip(true, `BLOCKED: ${reason}`);
  throw new Error('unreachable'); // test.skip(true) throws
}

const outstanding = (bills: SettlementBill[]) => bills.filter(b => Number(b.balanceamountself ?? 0) > 0);

/**
 * Load a real patient who has at least `minBills` service bills listed, with the Date Range filter switched off
 * so that bills of any date are listed. Returns the MRN and the bills the server returned.
 */
async function loadPatientWithBills(minBills = 1): Promise<{ mrn: string; result: PatientSearchResult }> {
  const fromEnv = norm(process.env.DEVHIS_SELF_MRN);
  const candidates = fromEnv ? [fromEnv] : (await screen.outstandingOpdMrns()).slice(0, MAX_CANDIDATES);
  if (candidates.length === 0) blocked('no patient with an unsettled self-pay OPD bill was found in the last 90 days');
  await screen.dateRange().uncheck();
  await expect(screen.fromDate()).toBeDisabled();
  for (const mrn of candidates) {
    const result = await screen.findPatient(mrn);
    if (result.found && outstanding(result.bills).length >= 1 && result.bills.length >= minBills) return { mrn, result };
  }
  return blocked(`none of the candidate MRNs (${candidates.join(', ')}) lists ${minBills} or more outstanding service bill(s) on this screen`);
}

/** Index of the first listed bill that still has a self balance. */
const firstOutstandingIndex = (bills: SettlementBill[]) => bills.findIndex(b => Number(b.balanceamountself ?? 0) > 0);

test.describe('Payment Settlement - Self', () => {
  test('T1 page loads with search panel, tabs, bill list and settlement controls', async ({ page }) => {
    await expect(page).toHaveURL(/#\/SelfPyamentSettlement$/);
    await expect(page).toHaveTitle(PAGE_TITLE);
    await expect(screen.pageTitle()).toContainText(/payment settlement - self/i);

    await test.step('search panel', async () => {
      await expect(screen.searchPanelHeading()).toContainText('Patient Search');
      for (const label of ['Date Range', 'From Date', 'To Date', 'Reg. Type', 'MRN No.', 'Bill Type']) {
        await expect(screen.form().locator('label').filter({ hasText: new RegExp(`^\\s*${label.replace(/\./g, '\\.')}\\s*$`) }).first(),
          `label "${label}"`).toBeVisible();
      }
      for (const control of [screen.dateRange(), screen.fromDate(), screen.toDate(), screen.mrn(), screen.findPatientBtn(),
        screen.searchPatientBtn(), screen.searchBtn(), screen.regType(REG_TYPE.OPD), screen.regType(REG_TYPE.IPD),
        screen.regType(REG_TYPE.All), screen.select2(MODELS.billType)]) {
        await expect(control).toBeVisible();
      }
      await expect(screen.searchBtn()).toHaveText(/^\s*Search\s*$/);
    });

    await test.step('tabs', async () => {
      await expect(screen.tab('#Bills')).toHaveText(/^\s*List of Outstanding Bills\s*$/);
      await expect(screen.tab('#Payments')).toHaveText(/^\s*List of Payments\s*$/);
      await expect(screen.tab('#BlockedInvoices')).toHaveText(/^\s*Block Patient Listing\s*\d+\s*$/);
      await expect(screen.billsPane(), 'List of Outstanding Bills is the active tab').toBeVisible();
      await expect(screen.paymentsPane()).toBeHidden();
      await expect(screen.blockedPane()).toBeHidden();
    });

    await test.step('Service Bills table', async () => {
      await expect(screen.billsPane().getByRole('heading', { name: 'Service Bills' })).toBeVisible();
      expect(await screen.headerTexts(screen.serviceBillsTable())).toEqual(SERVICE_BILL_COLUMNS);
    });

    await test.step('settlement controls', async () => {
      await expect(screen.servicePayAmount()).toBeVisible();
      await expect(screen.netAmount()).toBeVisible();
      await expect(screen.select2(MODELS.cashCounter)).toBeVisible();
      await expect(screen.settleBtn()).toBeVisible();
      await expect(screen.settleBtn()).toHaveText(/^\s*Settle Bill\s*$/);
    });
  });

  test('T2 default values', async () => {
    await test.step('search filters', async () => {
      await expect(screen.dateRange()).toBeChecked();
      await expect(screen.fromDate()).toBeEnabled();
      await expect(screen.toDate()).toBeEnabled();
      await expect(screen.fromDate()).toHaveValue(todayText);
      await expect(screen.toDate()).toHaveValue(todayText);
      await expect(screen.regType(REG_TYPE.OPD)).toBeChecked();
      await expect(screen.regType(REG_TYPE.IPD)).not.toBeChecked();
      await expect(screen.regType(REG_TYPE.All)).not.toBeChecked();
      await expect(screen.mrn()).toBeEmpty();
      await expect(screen.chosen(MODELS.billType)).toHaveText('ALL');
    });

    await test.step('no patient, no bills', async () => {
      await expect(screen.searchPanelHeading()).toHaveText(/^\s*Patient Search:\s*$/);
      await expect(screen.billRows()).toHaveCount(0);
      expect(screen.billRequestCount, 'no bill search runs on load').toBe(0);
    });

    await test.step('settlement controls', async () => {
      // calculated fields: read only, empty until a bill is selected
      await expect(screen.servicePayAmount()).toBeDisabled();
      await expect(screen.netAmount()).toBeDisabled();
      await expect(screen.servicePayAmount()).toHaveValue('');
      await expect(screen.netAmount()).toHaveValue('');
      // Cash Counter defaults to the login cash counter, never to the placeholder
      await expect(screen.chosen(MODELS.cashCounter)).not.toHaveText(CASH_COUNTER_PLACEHOLDER);
      await expect(screen.chosen(MODELS.cashCounter)).toHaveText(/\S/);
      await expect(screen.settleBtn()).toBeEnabled();
    });
  });

  test('T3 Bill Type and Cash Counter dropdowns', async () => {
    await test.step('Bill Type options and selection', async () => {
      expect(await screen.optionTexts(MODELS.billType)).toEqual(BILL_TYPE_OPTIONS);
      for (const option of ['Service Bill', 'Pharmacy Bill', 'ALL']) {
        await screen.pickSelect2(MODELS.billType, option); // pickSelect2 verifies widget and model
      }
    });

    await test.step('Cash Counter options and selection', async () => {
      const options = await screen.optionTexts(MODELS.cashCounter);
      expect(options[0], 'first option is the placeholder').toBe(CASH_COUNTER_PLACEHOLDER);
      const counters = options.slice(1);
      expect(counters.length, 'cash counters are loaded').toBeGreaterThan(0);
      expect(new Set(counters).size, 'cash counter names are unique').toBe(counters.length);
      expect(counters.filter(c => !c), 'no blank cash counter').toEqual([]);

      const original = await screen.selectedText(MODELS.cashCounter);
      expect(counters, 'the default cash counter is one of the options').toContain(original);
      const other = counters.find(c => c !== original);
      if (!other) blocked('only one cash counter exists - selecting a different one could not be checked');
      await screen.pickSelect2(MODELS.cashCounter, other);
      await screen.pickSelect2(MODELS.cashCounter, original);
      expect(screen.billRequestCount, 'changing a dropdown does not search by itself').toBe(0);
    });
  });

  test('T4 MRN search lists the outstanding bills of the patient', async () => {
    const { mrn, result } = await loadPatientWithBills();

    await test.step('request carries the search criteria', async () => {
      expect(result.payload).toMatchObject({ ExecFlag: EXEC.bills, mrno: mrn, fromdate: null, todate: null });
      expect(Number(result.payload.opd_ipd), 'Reg. Type OPD').toBe(Number(REG_TYPE.OPD));
      expect(String(result.payload.billtype), 'Bill Type ALL').toBe('3');
    });

    await test.step('patient banner', async () => {
      await expect(screen.searchPanelHeading()).toContainText(`MRN.No : ${mrn}`);
      await expect(screen.mrn()).toHaveValue(mrn);
    });

    await test.step('Service Bills shows what the server returned', async () => {
      expect(result.bills.filter(b => norm(b.mrno) !== mrn).map(b => b.tranno), 'only bills of that patient').toEqual([]);
      expect(await screen.billColumn('Bill No.')).toEqual(result.bills.map(b => norm(b.tranno)));
      expect(await screen.billColumn('MRN No.')).toEqual(result.bills.map(b => norm(b.mrno)));
      expect(await screen.billColumn('IPD No./OPD No.')).toEqual(result.bills.map(b => norm(b.opdno)));
      expect(await screen.billColumn('Date')).toEqual(result.bills.map(b => norm(b.date)));
      expect(await screen.billColumn('Net Amount')).toEqual(result.bills.map(b => gridAmount(b.netbillamount)));
      expect(await screen.billColumn('Self Balance')).toEqual(result.bills.map(b => gridAmount(b.balanceamountself)));
      await expect(screen.billRows().locator("input[type='checkbox']:checked"), 'no bill is pre-selected').toHaveCount(0);
    });

    await test.step('Search repeats the search with the date range when it is switched on', async () => {
      await screen.dateRange().check();
      const ranged = await screen.searchBills();
      expect(ranged.payload).toMatchObject({ ExecFlag: EXEC.bills, mrno: mrn, fromdate: todayText, todate: todayText });
      const allNos = new Set(result.bills.map(b => norm(b.tranno)));
      expect(ranged.bills.map(b => norm(b.tranno)).filter(n => !allNos.has(n)), "today's bills are a subset of all bills").toEqual([]);
    });
  });

  test('T5 search validation without a patient', async () => {
    await test.step('Search before any patient is loaded', async () => {
      await screen.expectToast(() => screen.searchBtn().click(), TOAST.searchPatientFirst);
      expect(screen.billRequestCount, 'no bill search is sent').toBe(0);
      await expect(screen.billRows()).toHaveCount(0);
    });

    await test.step('Find Patient with an empty MRN', async () => {
      await expect(screen.mrn()).toBeEmpty();
      const lookups = screen.apiCalls.filter(c => API.patientLookup.test(c)).length;
      await screen.expectToast(() => screen.findPatientBtn().click(), TOAST.mrnRequired);
      expect(screen.apiCalls.filter(c => API.patientLookup.test(c)).length, 'no patient lookup is sent').toBe(lookups);
    });

    await test.step('Enter in an empty MRN field', async () => {
      await screen.expectToast(() => screen.mrn().press('Enter'), TOAST.mrnRequired);
      expect(screen.billRequestCount, 'no bill search is sent').toBe(0);
    });
  });

  test('T6 date validation', async () => {
    const year = today.getFullYear();
    const { mrn } = await loadPatientWithBills();
    await screen.dateRange().check();
    await expect(screen.fromDate()).toBeEnabled();

    await test.step('invalid From Date', async () => {
      await screen.setDate(screen.fromDate(), `99/99/${year}`);
      const before = screen.billRequestCount;
      await screen.expectToast(() => screen.searchBtn().click(), TOAST.invalidFrom);
      expect(screen.billRequestCount, 'no bill search is sent').toBe(before);
      await screen.setDate(screen.fromDate(), todayText);
    });

    await test.step('invalid To Date', async () => {
      await screen.setDate(screen.toDate(), `31/02/${year}`);
      const before = screen.billRequestCount;
      await screen.expectToast(() => screen.searchBtn().click(), TOAST.invalidTo);
      expect(screen.billRequestCount, 'no bill search is sent').toBe(before);
      await screen.setDate(screen.toDate(), todayText);
    });

    await test.step('valid dates search again', async () => {
      await screen.clearToasts();
      const result = await screen.searchBills();
      expect(result.payload).toMatchObject({ mrno: mrn, fromdate: todayText, todate: todayText });
      await expect(screen.toasts()).toHaveCount(0);
    });

    await test.step('From Date after To Date lists nothing', async () => {
      // This screen has no "From Date cannot be after To Date" check (List Of Bills has one): the search is sent
      // and simply matches nothing. Asserted here: no bill is listed for an impossible range.
      await screen.setDate(screen.toDate(), ddMmYyyy(dayOffset(-5)));
      await screen.clearToasts();
      await screen.searchBtn().click();
      await screen.waitForAppIdle();
      await expect(screen.billRows()).toHaveCount(0);
      if ((await screen.toasts().count()) === 0) {
        test.info().annotations.push({
          type: 'observation',
          description: 'From Date after To Date shows no validation message on this screen; the search just returns no bills',
        });
      }
    });
  });

  test('T7 empty results: unknown MRN and a period without bills', async () => {
    await test.step('unknown MRN loads nothing and reports no error', async () => {
      await screen.clearToasts();
      const result = await screen.findPatient(NO_SUCH_MRN);
      expect(result.found, 'no such patient').toBe(false);
      await expect(screen.billRows()).toHaveCount(0);
      await expect(screen.searchPanelHeading(), 'no patient banner').toHaveText(/^\s*Patient Search:\s*$/);
      expect(screen.billRequestCount, 'no bill search is sent for an unknown patient').toBe(0);
      await expect(screen.errorToasts()).toHaveCount(0);
      await expect(screen.searchBtn(), 'the screen stays usable').toBeEnabled();
    });

    await test.step('existing patient, period without bills -> empty list, payments empty, amounts untouched', async () => {
      const { mrn } = await loadPatientWithBills();
      await screen.dateRange().check();
      await screen.setDate(screen.fromDate(), '01/01/2000');
      await screen.setDate(screen.toDate(), '02/01/2000');
      await screen.clearToasts();
      const result = await screen.searchBills(); // asserts the table lists exactly what the server returned
      expect(result.payload).toMatchObject({ mrno: mrn, fromdate: '01/01/2000', todate: '02/01/2000' });
      expect(result.bills, 'server returns no bill for January 2000').toEqual([]);
      await expect(screen.billRows()).toHaveCount(0);
      await expect(screen.errorToasts()).toHaveCount(0);
      await screen.tab('#Payments').click();
      await expect(screen.paymentRows()).toHaveCount(0);
    });
  });

  test('T8 Settle Bill opens the Payment Window for the selected bill (cancelled, nothing saved)', async () => {
    const { result } = await loadPatientWithBills();
    const index = firstOutstandingIndex(result.bills);
    const bill = result.bills[index];
    const amount = fieldAmount(bill.balanceamountself);

    await screen.billCheckbox(index).check();
    await expect(screen.netAmount()).toHaveValue(amount);
    const callsBefore = screen.apiCalls.length;
    await screen.settleBtn().click();
    const payment = screen.paymentWindow();

    await test.step('Payment Window shows the bill and the amount to pay', async () => {
      await expect(payment).toBeVisible();
      await expect(payment.locator('.modal-title')).toContainText('Payment Window');
      await expect(payment.locator('.modal-title')).toContainText(`MRN.No : ${norm(bill.mrno)}`);
      await screen.waitForAppIdle();
      await expect(screen.paymentField('BillType')).toHaveValue('Payment');
      await expect(screen.paymentField('BillNo')).toHaveValue(norm(bill.tranno));
      await expect(screen.paymentField('PayableAmount')).toHaveValue(amount);
      await expect(screen.paymentField('Amount')).toHaveValue(amount);
      await expect(payment.getByRole('button', { name: 'OK', exact: true })).toBeEnabled();
      await expect(payment.getByRole('button', { name: 'Cancel', exact: true })).toBeEnabled();
    });

    await test.step('Add needs a Payment Mode', async () => {
      await screen.expectToast(() => payment.getByRole('button', { name: 'Add', exact: true }).click(), TOAST.selectPaymentMode);
    });

    await test.step('Cancel closes the window without saving', async () => {
      await payment.getByRole('button', { name: 'Cancel', exact: true }).click();
      await expect(payment).toBeHidden();
      await screen.waitForAppIdle();
      const writes = screen.apiCalls.slice(callsBefore).filter(call => !API.readOnly.test(call));
      expect(writes, 'only read-only requests were made while the Payment Window was open').toEqual([]);
      await expect(screen.billCheckbox(index), 'the bill is still listed and selected').toBeChecked();
      await expect(screen.netAmount()).toHaveValue(amount);
    });

    test.info().annotations.push({
      type: 'not-verified',
      description: 'Saving the payment (Payment Window > OK) is not executed: it posts a real, irreversible receipt on DevHIS',
    });
  });

  test('T9 Settle Bill validation: a bill and a cash counter are required', async () => {
    const { result } = await loadPatientWithBills();
    const index = firstOutstandingIndex(result.bills);

    await test.step('no bill selected', async () => {
      await expect(screen.billRows().locator("input[type='checkbox']:checked")).toHaveCount(0);
      await screen.expectToast(() => screen.settleBtn().click(), TOAST.selectBills);
      await expect(screen.paymentWindow()).toHaveCount(0);
    });

    await test.step('no cash counter selected', async () => {
      const original = await screen.selectedText(MODELS.cashCounter);
      await screen.billCheckbox(index).check();
      await screen.pickSelect2(MODELS.cashCounter, CASH_COUNTER_PLACEHOLDER);
      await screen.expectToast(() => screen.settleBtn().click(), TOAST.selectCashCounter);
      await expect(screen.paymentWindow()).toHaveCount(0);
      await screen.pickSelect2(MODELS.cashCounter, original);
    });
  });

  test('T10 changing Reg. Type clears the form (the screen has no Clear button)', async () => {
    const defaultCashCounter = await screen.selectedText(MODELS.cashCounter);
    const { result } = await loadPatientWithBills();
    await screen.billCheckbox(firstOutstandingIndex(result.bills)).check();
    await expect(screen.netAmount()).not.toHaveValue('0');
    const other = (await screen.optionTexts(MODELS.cashCounter)).find(c => c !== defaultCashCounter && c !== CASH_COUNTER_PLACEHOLDER);
    if (other) await screen.pickSelect2(MODELS.cashCounter, other);

    await screen.regType(REG_TYPE.IPD).check();

    await expect(screen.mrn()).toBeEmpty();
    await expect(screen.searchPanelHeading(), 'patient banner is removed').toHaveText(/^\s*Patient Search:\s*$/);
    await expect(screen.billRows()).toHaveCount(0);
    await expect(screen.servicePayAmount()).toHaveValue('0');
    await expect(screen.netAmount()).toHaveValue('0');
    await expect(screen.chosen(MODELS.cashCounter), 'Cash Counter back to the login cash counter').toHaveText(defaultCashCounter);
    await expect(screen.regType(REG_TYPE.IPD)).toBeChecked();

    await test.step('Search now asks for a patient again', async () => {
      // after a clear the app uses this message rather than "Please search patient first!"
      const before = screen.billRequestCount;
      await screen.expectToast(() => screen.searchBtn().click(), TOAST.selectPatientFirst);
      expect(screen.billRequestCount, 'no bill search is sent').toBe(before);
    });
  });

  test('T11 bill selection: one bill at a time, amounts follow the selected bill', async ({ page }) => {
    // Enter in the MRN field is the keyboard way to run Find Patient
    const candidates = norm(process.env.DEVHIS_SELF_MRN) ? [norm(process.env.DEVHIS_SELF_MRN)] : (await screen.outstandingOpdMrns()).slice(0, MAX_CANDIDATES);
    if (candidates.length === 0) blocked('no patient with an unsettled self-pay OPD bill was found in the last 90 days');
    await screen.dateRange().uncheck();
    let loaded: PatientSearchResult | undefined;
    let single: PatientSearchResult | undefined;
    for (const mrn of candidates) {
      const result = await screen.findPatient(mrn, () => screen.mrn().press('Enter'));
      if (!result.found || outstanding(result.bills).length === 0) continue;
      single ??= result;
      if (result.bills.length >= 2) { loaded = result; break; }
    }
    if (!loaded && single) {
      // go back to the first usable patient when nobody lists two bills
      loaded = await screen.findPatient(norm(single.bills[0].mrno), () => page.keyboard.press('Enter'));
    }
    if (!loaded) blocked(`none of the candidate MRNs (${candidates.join(', ')}) lists an outstanding service bill on this screen`);
    const bills = loaded.bills;
    const first = firstOutstandingIndex(bills);

    await test.step('ticking a bill fills Service Payment Amount and Net Amount with its Self Balance', async () => {
      await screen.billCheckbox(first).check();
      await expect(screen.servicePayAmount()).toHaveValue(fieldAmount(bills[first].balanceamountself));
      await expect(screen.netAmount()).toHaveValue(fieldAmount(bills[first].balanceamountself));
      await expect(screen.servicePayAmount(), 'the amount is calculated, not typed').toBeDisabled();
    });

    await test.step('un-ticking it resets both amounts', async () => {
      await screen.billCheckbox(first).uncheck();
      await expect(screen.servicePayAmount()).toHaveValue('0');
      await expect(screen.netAmount()).toHaveValue('0');
    });

    if (bills.length < 2) blocked('no candidate patient lists two service bills - the one-bill-at-a-time rule could not be checked');

    await test.step('ticking a second bill replaces the first selection', async () => {
      const second = bills.findIndex((_, i) => i !== first);
      await screen.billCheckbox(first).check();
      await screen.billCheckbox(second).check();
      await expect(screen.billCheckbox(first), 'the first bill is un-ticked').not.toBeChecked();
      await expect(screen.billCheckbox(second)).toBeChecked();
      await expect(screen.billRows().locator("input[type='checkbox']:checked")).toHaveCount(1);
      await expect(screen.servicePayAmount()).toHaveValue(fieldAmount(bills[second].balanceamountself));
      await expect(screen.netAmount()).toHaveValue(fieldAmount(bills[second].balanceamountself));
    });
  });

  test('T12 tabs: Settle Bill is only available on List of Outstanding Bills', async ({ page }) => {
    const { mrn } = await loadPatientWithBills();

    await test.step('List of Payments', async () => {
      await screen.tab('#Payments').click();
      await expect(screen.paymentsPane()).toBeVisible();
      await expect(screen.billsPane()).toBeHidden();
      expect(await screen.headerTexts(screen.paymentsTable())).toEqual(PAYMENT_COLUMNS);
      await expect(screen.settleBtn(), 'Settle Bill is disabled while payments are shown').toBeDisabled();
    });

    await test.step('Block Patient Listing loads the blocked invoices of the patient', async () => {
      const [request] = await Promise.all([
        page.waitForRequest(r => API.blockedInvoices.test(new URL(r.url()).pathname), { timeout: 30_000 }),
        screen.tab('#BlockedInvoices').click(),
      ]);
      expect(request.postDataJSON()).toMatchObject({ mrn });
      await expect(screen.blockedPane()).toBeVisible();
      await expect(screen.paymentsPane()).toBeHidden();
      await screen.waitForAppIdle();
      await expect(screen.errorToasts()).toHaveCount(0);
    });

    await test.step('back on List of Outstanding Bills the button is enabled again', async () => {
      await screen.tab('#Bills').click();
      await expect(screen.billsPane()).toBeVisible();
      await expect(screen.settleBtn()).toBeEnabled();
    });
  });

  test('T13 Settle Bill on a freshly opened screen asks to select bills', async ({ page }) => {
    // Found during exploration: before any patient has been searched, Settle Bill shows nothing at all and the
    // browser console logs "TypeError: Cannot read properties of undefined (reading 'length')" from
    // SelfPaymentSettlementController.js (fnIUDBill reads BillDetails before it exists). Once a patient has been
    // searched the same click correctly shows "Please Select Bills!" (covered by T9).
    test.info().annotations.push({
      type: 'issue',
      description: 'Settle Bill before any search throws a TypeError in fnIUDBill and shows no validation message',
    });
    const errors: string[] = [];
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text().split('\n')[0]); });
    page.on('pageerror', error => { errors.push(error.message); });

    await expect(screen.settleBtn()).toBeEnabled();
    await screen.clearToasts();
    await screen.settleBtn().click();
    await expect.soft(screen.toast(TOAST.selectBills), `toast "${TOAST.selectBills}"`).toBeVisible();
    expect(errors.filter(e => /TypeError/.test(e)), 'no script error when Settle Bill is clicked').toEqual([]);
    await expect(screen.paymentWindow()).toHaveCount(0);
  });
});
