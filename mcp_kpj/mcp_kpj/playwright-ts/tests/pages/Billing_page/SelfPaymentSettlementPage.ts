import { expect, Locator, Page, Request, Response } from '@playwright/test';
import { ddMmYyyy, dayOffset, norm } from './BillListPage';

/**
 * Billing > Payment Settlement > Self  (#/SelfPyamentSettlement, page title "Self",
 * header "PAYMENT SETTLEMENT - SELF", controller SelfPaymentSettlementController)
 *
 * Screen facts (from live exploration of DevHIS on 07/10/2026):
 *  - Opening: like every DevHIS route, a freshly loaded URL is sent back to the dashboard; the screen is reached
 *    through the side menu: Billing > Payment Settlement > Self.
 *  - One form (#SelfPaymentSettlementForm). Panel "Patient Search:" - Date Range checkbox (#IsDateRange, checked),
 *    From Date / To Date (#FromDate / #ToDate, 720kb datepicker, dd/MM/yyyy, default today, disabled when the
 *    checkbox is off), Reg. Type radios (OPD default / IPD / All), MRN No. (+ Find Patient, Search Patient popup),
 *    Bill Type (select2: Service Bill / Pharmacy Bill / ALL), Search.
 *  - Tabs: "List of Outstanding Bills" (#Bills: "Service Bills" table, and "Pharmacy Bills" when present),
 *    "List of Payments" (#Payments: receipts grouped by bill, Print / RePrint), "Block Patient Listing n"
 *    (#BlockedInvoices).
 *  - Footer: Service Payment Amount and Net Amount (both read only, calculated), Cash Counter (select2, defaults to
 *    the login cash counter), Settle Bill.
 *  - Workflow: MRN + Find Patient (or Enter) -> POST patientregistration/FetchPatientData
 *    (GetCashCreditSalePatientInfo). When the patient exists the screen shows the patient banner in the panel
 *    heading and loads POST /api/Bill/Fetch GETBILLDATA (+ GETPAYMENTMODEDETAILS, GetPharmacyBillDataToSettle).
 *    An unknown MRN shows nothing at all (no rows, no banner, no message).
 *  - Ticking a service bill un-ticks every other one (one bill at a time) and sets Service Payment Amount and
 *    Net Amount to that bill's Self Balance; un-ticking sets both to 0.
 *  - Settle Bill opens the "Payment Window" popup (payment modes, OK = save, Cancel). It is disabled while the
 *    "List of Payments" tab is open.
 *  - There is NO Clear / Reset button. Changing Reg. Type clears the form (ClearForm): MRN, patient banner, bills,
 *    amounts (0) and Cash Counter (back to the login counter). Bill Type and the Date Range checkbox are kept.
 *  - Toasts (toastr, #toast-container): "Please search patient first!", "Please Enter MRN No.!",
 *    "Invalid From Date!", "Invalid To Date!", "Please Select Bills!", "Please Select Cash Counter!",
 *    and in the Payment Window "Please Select Payment Mode!".
 *  - Buttons carry a `title` that the app's tooltip script turns into `aria-label` (the Search button becomes
 *    "Bill Details"), and label `for` values match nothing - so controls are located by id, ng-model or ng-click.
 *
 * NOT automated on purpose: the OK button of the Payment Window. It posts a real receipt against a real bill on
 * the shared DevHIS environment and cannot be undone from this screen. The tests go as far as the Payment Window,
 * check what it shows, and cancel.
 */

export const ROUTE = '/#/SelfPyamentSettlement';
export const PAGE_TITLE = 'Self';

export const API = {
  patientLookup: /\/api\/patientregistration\/FetchPatientData/i,
  billFetch: /\/api\/Bill\/Fetch/i,
  blockedInvoices: /\/api\/PatientBlock\/FetchBlockedInvoices$/i,
  billListSearch: '/api/BillListSearch/Fetch',
  /** Read-only calls. Anything else sent while the Payment Window is open would be a write. */
  readOnly: /\/Fetch|CommonDropdown|getInternalmsg|GetUserID|AdminDashboard/i,
};

export const EXEC = {
  patient: 'GetCashCreditSalePatientInfo',
  bills: 'GETBILLDATA',
  payments: 'GETPAYMENTMODEDETAILS',
  pharmacy: 'GetPharmacyBillDataToSettle',
} as const;

export const MODELS = {
  mrn: 'PatientData.MRNO',
  billType: 'PatientData.billtype',
  cashCounter: 'PatientData.CashCounterId',
  servicePayAmount: 'BillDetails.ServicePayAmount',
  netAmount: 'BillDetails.netbillamount',
} as const;

export const BILL_TYPE_OPTIONS = ['Service Bill', 'Pharmacy Bill', 'ALL'];
export const CASH_COUNTER_PLACEHOLDER = '-Select-';
export const REG_TYPE = { OPD: '0', IPD: '1', All: '3' } as const;

export const SERVICE_BILL_COLUMNS = [
  'Date', 'Bill No.', 'MRN No.', 'IPD No./OPD No.', 'Patient Name', 'Company Name', 'Total Bill Amount',
  'Self Concession', 'Tax', 'Net Amount', 'Self Pay', 'Company Pay', 'Self Balance', 'Company Balance',
  'Sales Return Amount', 'Cash Counter',
];
export const PAYMENT_COLUMNS = [
  'Receipt No.', 'Allocation Date', 'Total Bill Amount', 'Discount Amount', 'Net Bill Amount', 'Self Pay',
  'Receipt Amount', 'Balance Amount', 'Print', 'RePrint',
];

export const TOAST = {
  searchPatientFirst: 'Please search patient first!',
  selectPatientFirst: 'Please Select Patient First For Outstanding Bills!',
  mrnRequired: 'Please Enter MRN No.!',
  invalidFrom: 'Invalid From Date!',
  invalidTo: 'Invalid To Date!',
  selectBills: 'Please Select Bills!',
  selectCashCounter: 'Please Select Cash Counter!',
  selectPaymentMode: 'Please Select Payment Mode!',
};

/** One row of the GETBILLDATA response (only the fields the tests use). */
export interface SettlementBill {
  tranno?: string | null;
  mrno?: string | null;
  opdno?: string | null;
  date?: string | null;
  balanceamountself?: number | null;
  netbillamount?: number | null;
  [key: string]: unknown;
}

export interface PatientSearchResult {
  /** false when the MRN lookup returned no patient (the screen then loads nothing). */
  found: boolean;
  /** Request payload of the GETBILLDATA call (empty when the patient was not found). */
  payload: Record<string, unknown>;
  bills: SettlementBill[];
}

const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** Amount as the bill tables show it (AngularJS `number:2`): 2233.4 -> "2,233.40". */
export const gridAmount = (n: unknown) =>
  Number(n ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Amount as the calculated fields and the Payment Window show it (`toFixed(2)`): 2233.4 -> "2233.40". */
export const fieldAmount = (n: unknown) => Number(n ?? 0).toFixed(2);

function payloadOf(request: Request): Record<string, unknown> {
  try {
    const json = request.postDataJSON();
    return json && typeof json === 'object' ? (json as Record<string, unknown>) : { __raw: json };
  } catch {
    return { __raw: request.postData() };
  }
}

const isExec = (r: Response, url: RegExp, execFlag: string) =>
  url.test(r.url()) && r.request().method() === 'POST' && payloadOf(r.request()).ExecFlag === execFlag;

export class SelfPaymentSettlementPage {
  /** Number of GETBILLDATA requests the browser has issued (used to prove a validation blocked the search). */
  private billRequests = 0;
  /** Every /api/ request seen, so a test can prove nothing but read-only calls were made. */
  readonly apiCalls: string[] = [];

  constructor(readonly page: Page) {
    page.on('request', r => {
      if (!/\/api\//i.test(r.url())) return;
      this.apiCalls.push(`${r.method()} ${new URL(r.url()).pathname}`);
      if (API.billFetch.test(r.url()) && payloadOf(r).ExecFlag === EXEC.bills) this.billRequests++;
    });
  }

  get billRequestCount() { return this.billRequests; }

  // -- shell / navigation --
  pageTitle = () => this.page.locator('header.main-header .page_title');
  billingMenu = () => this.page.locator('li.treeview:has(> a > span.menu-text:text-is("Billing"))').first();
  paymentSettlementMenu = () =>
    this.billingMenu().locator('li').filter({ has: this.page.locator(`a[href="${ROUTE.slice(1)}"]`) })
      .filter({ has: this.page.locator('> a', { hasText: /^\s*Payment Settlement\s*$/ }) }).first();
  selfMenuLink = () => this.billingMenu().locator(`a[href="${ROUTE.slice(1)}"]`);

  // -- search panel --
  form = () => this.page.locator('form#SelfPaymentSettlementForm');
  searchPanelHeading = () => this.form().locator('.panel-heading').first();
  dateRange = () => this.form().locator('#IsDateRange');
  fromDate = () => this.form().locator('#FromDate');
  toDate = () => this.form().locator('#ToDate');
  regType = (value: string) => this.form().locator(`input[type='radio'][name='PatientType'][value='${value}']`);
  mrn = () => this.form().locator(`input[ng-model='${MODELS.mrn}']`);
  findPatientBtn = () => this.form().locator("button[ng-click='FindPatient();']");
  searchPatientBtn = () => this.form().locator("button[ng-click='OpenPopupScreen()']");
  searchBtn = () => this.form().locator("button[ng-click='fnFillBillDetails();']");

  // -- tabs --
  tab = (target: '#Bills' | '#Payments' | '#BlockedInvoices') =>
    this.form().locator(`.nav-tabs a[data-toggle='tab'][data-target='${target}']`);
  billsPane = () => this.form().locator('#Bills');
  paymentsPane = () => this.form().locator('#Payments');
  blockedPane = () => this.form().locator('#BlockedInvoices');
  serviceBillsTable = () => this.billsPane().locator('table').first();
  billRows = () => this.serviceBillsTable().locator('tbody tr');
  billCheckbox = (index: number) => this.billRows().nth(index).locator("input[type='checkbox']");
  paymentsTable = () => this.paymentsPane().locator('table').first();
  /** Receipt rows of the List of Payments tab (the bold bill-number group rows are excluded). */
  paymentRows = () => this.paymentsTable().locator("tbody tr[role='row']");

  // -- footer --
  servicePayAmount = () => this.form().locator(`input[ng-model='${MODELS.servicePayAmount}']`);
  netAmount = () => this.form().locator(`input[ng-model='${MODELS.netAmount}']`);
  settleBtn = () => this.form().locator("button[ng-click='fnIUDBill()']");

  // -- select2 dropdowns (keyed on the ng-model of the native <select> behind the widget) --
  nativeSelect = (model: string) => this.form().locator(`select[ng-model='${model}']`);
  select2 = (model: string) => this.form().locator(`div.select2-container:has(+ select[ng-model='${model}'])`);
  chosen = (model: string) => this.select2(model).locator('.select2-chosen');
  private drop = () => this.page.locator('#select2-drop.select2-drop-active');

  // -- toasts / popups --
  toasts = () => this.page.locator('#toast-container .toast-message');
  errorToasts = () => this.page.locator('#toast-container .toast-error');
  openDatepicker = () => this.page.locator('._720kb-datepicker-calendar._720kb-datepicker-open');
  patientSearchModal = () => this.page.locator('.modal .patient-search-modal');
  paymentWindow = () =>
    this.page.locator('.modal').filter({ has: this.page.locator("[ng-controller='paymentWindowController']") });
  paymentField = (model: string) => this.paymentWindow().locator(`input[ng-model='PaymentDetails.${model}']`);

  // ---- waiting --------------------------------------------------------------------------------------------------

  /** Wait until the AngularJS app has no HTTP request in flight (a state check, so it cannot miss a response). */
  async waitForAppIdle() {
    await expect
      .poll(
        () =>
          this.page.evaluate(() => {
            const ng = (window as any).angular;
            const root = document.querySelector('.ng-scope');
            if (!ng || !root) return -1;
            const injector = ng.element(root).injector();
            return injector ? injector.get('$http').pendingRequests.length : -1;
          }),
        { message: 'AngularJS still has HTTP requests in flight', timeout: 45_000 },
      )
      .toBe(0);
  }

  // ---- navigation -----------------------------------------------------------------------------------------------

  /** Open the screen through the side menu: Billing > Payment Settlement > Self. */
  async open() {
    await expect(this.billingMenu(), 'side menu is loaded').toBeVisible({ timeout: 45_000 });
    await this.waitForAppIdle();
    await this.billingMenu().locator('> a').click();
    await expect(this.paymentSettlementMenu(), 'Billing menu lists "Payment Settlement"').toBeVisible({ timeout: 30_000 });
    if (!(await this.selfMenuLink().isVisible())) await this.paymentSettlementMenu().locator('> a').click();
    await expect(this.selfMenuLink(), 'Payment Settlement lists "Self"').toBeVisible();
    await this.selfMenuLink().click();
    await expect(this.page).toHaveURL(/#\/SelfPyamentSettlement$/);
    await expect(this.searchPanelHeading()).toBeVisible();
    await this.waitForAppIdle();
    // the Cash Counter list is loaded asynchronously; select2 rebuilds its widget when it arrives
    await expect.poll(() => this.nativeSelect(MODELS.cashCounter).locator('option').count(),
      { message: 'Cash Counter list is loaded' }).toBeGreaterThan(1);
    await expect(this.select2(MODELS.cashCounter)).toBeVisible();
  }

  // ---- toasts ---------------------------------------------------------------------------------------------------

  async clearToasts() {
    await this.page.evaluate(() => document.querySelectorAll('#toast-container .toast').forEach(t => t.remove()));
  }

  toast = (text: string) =>
    this.toasts().filter({ hasText: new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`) }).first();

  /** Run an action and assert it raises a toast with exactly `text`. */
  async expectToast(action: () => Promise<unknown>, text: string) {
    await this.clearToasts();
    await action();
    await expect(this.toast(text), `toast "${text}"`).toBeVisible();
  }

  // ---- dates ----------------------------------------------------------------------------------------------------

  /**
   * Type a date and close the datepicker the way the app closes it: the 720kb calendar opens on focus and closes
   * from a window click once the input is no longer focused. The page title in the fixed top bar is never covered
   * by a calendar, so it is the click target.
   */
  async setDate(input: Locator, value: string) {
    await input.fill(value);
    await expect(input).toHaveValue(value);
    await expect(async () => {
      await this.page.mouse.move(0, 0);
      await this.pageTitle().click({ timeout: 3_000 });
      await expect(this.openDatepicker()).toHaveCount(0, { timeout: 2_000 });
    }).toPass({ intervals: [250, 500, 1_000], timeout: 15_000 });
  }

  // ---- select2 --------------------------------------------------------------------------------------------------

  selectedText(model: string) {
    return this.nativeSelect(model).evaluate((s: HTMLSelectElement) =>
      (s.options[s.selectedIndex]?.text ?? '').replace(/\s+/g, ' ').trim(),
    );
  }

  optionTexts(model: string) {
    return this.nativeSelect(model).locator('option').evaluateAll(options =>
      options.map(o => (o.textContent ?? '').replace(/\s+/g, ' ').trim()),
    );
  }

  /**
   * Select an option in a select2 dropdown: open THIS field's dropdown, type the value to narrow the list, click
   * the result whose label is exactly the value (typing "CASHIER" also matches "A&E CASHIER 1", so the highlighted
   * first match is not reliable), then verify both the widget and the underlying <select>. The whole unit is
   * retried if select2 rebuilt its DOM part-way; the final verification is never relaxed.
   */
  async pickSelect2(model: string, text: string) {
    const search = this.drop().locator('input.select2-input');
    const exact = new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`);
    await expect(async () => {
      await this.waitForAppIdle();
      if (await this.drop().isVisible()) await this.page.keyboard.press('Escape');
      await expect(this.drop()).toBeHidden({ timeout: 2_000 });
      await this.select2(model).locator('a.select2-choice').click({ timeout: 3_000 });
      await expect(search).toBeVisible({ timeout: 3_000 });
      await search.fill(text);
      await this.drop().locator('.select2-result-label').filter({ hasText: exact }).first().click({ timeout: 3_000 });
      await expect(this.drop()).toBeHidden({ timeout: 3_000 });
      await expect(this.chosen(model)).toHaveText(text, { timeout: 3_000 });
      expect(await this.selectedText(model)).toBe(text);
    }).toPass({ intervals: [500, 1_000, 2_000], timeout: 30_000 });
  }

  // ---- patient / bill search ------------------------------------------------------------------------------------

  /**
   * Type an MRN and run the patient lookup with `trigger` (Find Patient by default). Returns whether the patient
   * exists and, when it does, the outstanding service bills the server returned, after the screen has caught up.
   */
  async findPatient(mrn: string, trigger: () => Promise<unknown> = () => this.findPatientBtn().click()): Promise<PatientSearchResult> {
    await this.waitForAppIdle();
    await this.mrn().fill(mrn);
    // registered before the click: the bill list is requested as soon as the lookup answers
    const billsResponse = this.page
      .waitForResponse(r => isExec(r, API.billFetch, EXEC.bills), { timeout: 60_000 })
      .catch(() => null);
    const [lookup] = await Promise.all([
      this.page.waitForResponse(r => isExec(r, API.patientLookup, EXEC.patient), { timeout: 60_000 }),
      trigger(),
    ]);
    expect(lookup.status(), 'patient lookup succeeded').toBe(200);
    expect(payloadOf(lookup.request()).MRNo, 'the lookup is for the entered MRN').toBe(mrn);
    const patients: unknown = await lookup.json();
    if (!Array.isArray(patients) || patients.length === 0) {
      await this.waitForAppIdle();
      return { found: false, payload: {}, bills: [] };
    }
    const response = await billsResponse;
    if (!response) throw new Error(`Patient ${mrn} was found but the screen never requested the bills (GETBILLDATA)`);
    return this.toResult(response);
  }

  /** Click Search and return the bill search it triggers. A patient must already be loaded. */
  async searchBills(): Promise<PatientSearchResult> {
    await this.waitForAppIdle();
    const [response] = await Promise.all([
      this.page.waitForResponse(r => isExec(r, API.billFetch, EXEC.bills), { timeout: 60_000 }),
      this.searchBtn().click(),
    ]);
    return this.toResult(response);
  }

  private async toResult(response: Response): Promise<PatientSearchResult> {
    expect(response.status(), 'GETBILLDATA request succeeded').toBe(200);
    const body: unknown = await response.json();
    const bills = Array.isArray(body) ? (body as SettlementBill[]) : [];
    await this.waitForAppIdle();
    await expect(this.billRows(), 'Service Bills lists what the server returned').toHaveCount(bills.length);
    return { found: true, payload: payloadOf(response.request()), bills };
  }

  /** Cell texts of one Service Bills column, top to bottom. The column is found by its header text. */
  async billColumn(header: string): Promise<string[]> {
    return this.serviceBillsTable().evaluate((table, wanted) => {
      const clean = (s: string | null | undefined) => (s ?? '').replace(/\s+/g, ' ').trim();
      const index = Array.from(table.querySelectorAll('thead th')).findIndex(th => clean(th.textContent) === wanted);
      if (index < 0) throw new Error(`Service Bills column "${wanted}" not found`);
      return Array.from(table.querySelectorAll('tbody tr')).map(tr => clean(tr.children[index]?.textContent));
    }, header);
  }

  async headerTexts(table: Locator): Promise<string[]> {
    const texts = await table.locator('thead th').evaluateAll(ths =>
      ths.map(th => (th.textContent ?? '').replace(/\s+/g, ' ').trim()),
    );
    return texts.filter(Boolean);
  }

  /**
   * MRNs that have unsettled OPD bills with a self balance, most bills first. Read from the application's own
   * Bill List search (POST /api/BillListSearch/Fetch) with the signed-in session - nothing is created or mocked;
   * it only tells the tests which real patient to type into the MRN field.
   */
  async outstandingOpdMrns(daysBack = 90): Promise<string[]> {
    const rows = await this.page.evaluate(async ({ url, from, to }) => {
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ExecFlag: 'GetBillList', FromDate: from, ToDate: to, BillStatus: '2',
          LocationID: Number(localStorage.getItem('LocationId')) || 0, Opd_Ipd: 0, IsInvoice: true, PageIndex: 0,
          IsforPharmacyBillList: false, VisitTypeID: 0, BillListType: '4', EinvoiceStatus: 0, Encountertypeid: 0,
        }),
      });
      if (!response.ok) return [];
      const body = await response.json();
      return Array.isArray(body)
        ? body.map((b: any) => ({ mrno: String(b.mrno ?? '').trim(), balance: Number(b.balanceamountself ?? 0) }))
        : [];
    }, { url: API.billListSearch, from: ddMmYyyy(dayOffset(-daysBack)), to: ddMmYyyy(dayOffset(0)) });
    const counts = new Map<string, number>();
    for (const row of rows) {
      if (row.mrno && row.balance > 0) counts.set(row.mrno, (counts.get(row.mrno) ?? 0) + 1);
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([mrno]) => norm(mrno));
  }
}
