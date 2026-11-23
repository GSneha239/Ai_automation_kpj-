import { expect, Locator, Page, Request, Response } from '@playwright/test';

/**
 * Billing > List Of Bills  (#/BillList, page title "List Of Bills", controller BillListController)
 *
 * Screen facts (from live exploration of DevHIS on 07/10/2026):
 *  - Opening: a full page load of /#/BillList, or a hash change before the Billing menu has been opened, is
 *    redirected to the user's dashboard. The screen is reached through the side menu: Billing > List Of Bills.
 *  - One form (#billList), panel "Patient Search:" with the filters and panel "List of Bills" with two tabs:
 *    "List of Bills" (#list-of-bills-tab, ui-grid #grid1) and "Consolidated Bills" (#consolidated-bills-tab, #grid2).
 *  - Filters: From Date / To Date (720kb datepicker, dd/MM/yyyy, default today), MRN (+ Find Patient, Search Patient
 *    popup), Bill No., Bill Status / Payor Category / Location / Department / Visit Type / Encounter Type (select2),
 *    Reg. Type radios (OPD / IPD / All), Cancelled Bills checkbox, Type of Invoice radios (All / Bill / CN / DN /
 *    CN Settlement), E-Invoice radios (All / Pending / Completed), Search.
 *  - Every search is POST /api/BillListSearch/Fetch (ExecFlag GetBillList | GETBILLLISTCANCELLED |
 *    GETBILLLISTSEARCHFORCONSOLIDATED). The server returns ALL matching rows; paging is client side (ui-grid,
 *    10 / 20 / 50 / 100 per page).
 *  - Radios search immediately on click; text fields and dropdowns need the Search button.
 *  - There is NO Reset / Clear button on this screen.
 *  - Empty result: the grid simply has no rows and the "x - y of n items" text is hidden. No message, no toast.
 *  - Validation toasts (toastr, #toast-container): "Invalid From Date!", "Invalid To Date!",
 *    "From Date cannot be after To Date!", "Please Enter MRN No.!", "Patient Not Found!".
 *  - Row "View" opens the bill in #/OPDBill or #/IPDBill (read only navigation).
 *  - "Export to Excel" builds a CSV in the browser (BillList_yyyy-MM-dd.csv) from the rows already loaded.
 *  - Field ids are unreliable (BLUnit is used by four dropdowns, radio ids repeat, label `for` values match
 *    nothing), so locators are keyed on ng-model, or on name + value for radios.
 *
 * NOT automated on purpose - these write to the server or send messages and cannot be undone:
 *  Submit e-Bill, Generate E-Invoice, Company Approved Amount, SubmitTextOutput, SubmitFTP, SMS, Whatsapp,
 *  Status / Credit Admin status popups, Attachment upload, Postage Tracking Number, Print / Reprint.
 */

export const ROUTE = '/#/BillList';
export const PAGE_TITLE = 'List Of Bills';

export const API = {
  fetch: /\/api\/BillListSearch\/Fetch/i,
  patientLookup: /\/api\/patientregistration\/FetchPatientData/i,
  loginLocations: /\/api\/Auth\/GetLoginLoc/i,
};

export const EXEC = {
  list: 'GetBillList',
  cancelled: 'GETBILLLISTCANCELLED',
  consolidated: 'GETBILLLISTSEARCHFORCONSOLIDATED',
} as const;

/** ng-model of every filter control. */
export const MODELS = {
  fromDate: 'BillList.FromDate',
  toDate: 'BillList.ToDate',
  mrn: 'BillList.MRNO',
  billNo: 'BillList.BillNO',
  billStatus: 'BillList.BillStatus',
  payorCategory: 'BillList.payercategoryid',
  location: 'BillList.LocationID',
  department: 'BillList.departmentid',
  visitType: 'BillList.VisitTypeID',
  encounterType: 'BillList.applicablefor',
  ward: 'BillList.wardid',
  consolidatedPayor: 'BillList.consolidsponsorid',
  cancelled: 'selectAllCancelled',
} as const;

export const SELECT_PLACEHOLDER = '--Select--';
export const BILL_STATUS_OPTIONS = [SELECT_PLACEHOLDER, 'Settled', 'Unsettled'];
export const PAGE_SIZES = ['10', '20', '50', '100'];
export const DEFAULT_PAGE_SIZE = 10;

/** Data columns that are always inside the grid's initial viewport (ui-grid only renders the visible columns). */
export const CORE_COLUMNS = [
  'Bill Date', 'Bill No.', 'MRN', 'OPD / IPD No.', 'Encounter Type', 'Patient Name', 'Payor', 'Total Bill Amount',
];
export const CONSOLIDATED_COLUMNS = [
  'Bill Date', 'Bill No.', 'MRN', 'OPD / IPD No.', 'Patient Name', 'Payor', 'Total Bill Amount', 'Net Bill Amount', 'Print',
];

/** Radio groups: input name -> value of each option. */
export const REG_TYPE = { OPD: '0', IPD: '1', All: '2' } as const;
export const INVOICE_TYPE = { All: '0', Bill: '4', CN: '1', DN: '2', 'CN Settlement': '3' } as const;
export const EINVOICE = { All: '0', Pending: '1', Completed: '2' } as const;

export const TOAST = {
  invalidFrom: 'Invalid From Date!',
  invalidTo: 'Invalid To Date!',
  fromAfterTo: 'From Date cannot be after To Date!',
  mrnRequired: 'Please Enter MRN No.!',
  patientNotFound: 'Patient Not Found!',
};

/** One row of the /api/BillListSearch/Fetch response (only the fields the tests use). */
export interface BillRow {
  id?: number;
  tranno?: string | null;
  mrno?: string | null;
  opdno?: string | null;
  date?: string | null;
  opd_ipd?: number | null;
  closedbill?: boolean | null;
  [key: string]: unknown;
}

export interface FetchResult {
  status: number;
  payload: Record<string, unknown>;
  rows: BillRow[];
}

// ---- small utilities --------------------------------------------------------------------------------------------

export const norm = (s: unknown): string => String(s ?? '').replace(/\s+/g, ' ').trim();

const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** Date for today plus `days` (local time, midnight). */
export function dayOffset(days = 0): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + days);
  return d;
}

/** dd/MM/yyyy - the format of the From / To Date fields. */
export function ddMmYyyy(d: Date): string {
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
}

const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];

/** Parse the grid / API bill date ("07-Oct-26"). Returns null when the text is not in that format. */
export function parseBillDate(text: unknown): Date | null {
  const m = /^(\d{1,2})-([A-Za-z]{3})-(\d{2}|\d{4})$/.exec(norm(text));
  if (!m) return null;
  const month = MONTHS.indexOf(m[2].toLowerCase());
  if (month < 0) return null;
  const year = m[3].length === 2 ? 2000 + Number(m[3]) : Number(m[3]);
  return new Date(year, month, Number(m[1]));
}

function payloadOf(request: Request): Record<string, unknown> {
  try {
    const json = request.postDataJSON();
    return json && typeof json === 'object' ? (json as Record<string, unknown>) : { __raw: json };
  } catch {
    return { __raw: request.postData() };
  }
}

function isFetch(response: Response, execFlag: string): boolean {
  if (!API.fetch.test(response.url()) || response.request().method() !== 'POST') return false;
  return payloadOf(response.request()).ExecFlag === execFlag;
}

async function toFetchResult(response: Response): Promise<FetchResult> {
  const body: unknown = await response.json();
  return {
    status: response.status(),
    payload: payloadOf(response.request()),
    rows: Array.isArray(body) ? (body as BillRow[]) : [],
  };
}

// ---- login ------------------------------------------------------------------------------------------------------

const pendingSecrets = new WeakMap<Page, { value?: string }>();

/**
 * Type a secret into an input without the value appearing in Playwright's report, step titles, traces or logs
 * (same technique as CashDeskManagementPage: the page pulls the value through an exposed binding).
 */
async function fillSecret(page: Page, input: Locator, secret: string) {
  let slot = pendingSecrets.get(page);
  if (!slot) {
    const newSlot: { value?: string } = {};
    await page.exposeFunction('__blTakeSecret', () => {
      const value = newSlot.value ?? '';
      newSlot.value = undefined; // one-shot
      return value;
    });
    pendingSecrets.set(page, newSlot);
    slot = newSlot;
  }
  slot.value = secret;
  await input.evaluate(async (el: HTMLInputElement) => {
    const value: string = await (window as any).__blTakeSecret();
    el.focus();
    el.value = value;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await expect
    .poll(() => input.evaluate((el: HTMLInputElement) => el.value.length > 0), { message: 'secret was entered' })
    .toBe(true);
}

/** How many times the login form is submitted before the run is failed. */
const LOGIN_ATTEMPTS = 2;

/**
 * DevHIS login: Login Name -> Password -> LOGIN. No-op when the session is already authenticated.
 * Credentials come ONLY from DEVHIS_USER / DEVHIS_PASS - nothing is defaulted or written into the code.
 *
 * The login is retried once. Seen on DevHIS: /Account/Login answered 200 but the requests the login page sends
 * next never completed, so the page stayed on the login form for 30 s although the credentials were right.
 * A second submit (or simply re-opening the app, when the first submit did create the session) gets in.
 */
export async function login(page: Page) {
  const user = process.env.DEVHIS_USER;
  const pass = process.env.DEVHIS_PASS;
  if (!user || !pass) {
    throw new Error('Set DEVHIS_USER and DEVHIS_PASS environment variables before running the Bill List tests.');
  }
  const loginName = page.getByPlaceholder('Login Name');
  const password = page.getByPlaceholder('Password');
  for (let attempt = 1; attempt <= LOGIN_ATTEMPTS; attempt++) {
    await page.goto(ROUTE);
    const onLogin = await loginName.waitFor({ timeout: 8_000 }).then(() => true, () => false);
    if (!onLogin) return; // already authenticated
    await loginName.fill(user);
    await fillSecret(page, password, pass);
    // blurring the password fires an async location lookup that must finish before LOGIN authenticates
    await Promise.all([
      page.waitForResponse(r => API.loginLocations.test(r.url()), { timeout: 15_000 }).catch(() => null),
      password.blur(),
    ]);
    await page.getByRole('button', { name: 'LOGIN' }).click();
    // Not expect(...).not.toHaveURL(): a failing expect attaches a page snapshot taken while the password is in the field.
    const leftLogin = await page
      .waitForURL(url => !/\/Account/i.test(url.toString()), { timeout: 30_000 })
      .then(() => true, () => false);
    if (leftLogin) return;
    await password.evaluate((el: HTMLInputElement) => { el.value = ''; }).catch(() => {});
  }
  throw new Error(
    `Login failed after ${LOGIN_ATTEMPTS} attempts - check DEVHIS_USER / DEVHIS_PASS (still on the login page: ${page.url()})`,
  );
}

// ---- page object ------------------------------------------------------------------------------------------------

export class BillListPage {
  /** Number of GetBillList requests the browser has issued (used to prove a validation blocked the search). */
  private listRequests = 0;

  constructor(readonly page: Page) {
    page.on('request', r => {
      if (API.fetch.test(r.url()) && payloadOf(r).ExecFlag === EXEC.list) this.listRequests++;
    });
  }

  get listRequestCount() { return this.listRequests; }

  // -- shell / navigation --
  pageTitle = () => this.page.locator('header.main-header .page_title');
  billingMenu = () =>
    this.page.locator('li.treeview:has(> a > span.menu-text:text-is("Billing"))').first();
  billListMenuLink = () => this.billingMenu().locator('a[href="#/BillList"]');

  // -- form --
  form = () => this.page.locator('form#billList');
  searchPanelHeading = () => this.form().locator('.panel-heading', { hasText: 'Patient Search' });
  listPanelHeading = () => this.form().locator('.panel-heading', { hasText: 'List of Bills' });
  byModel = (model: string) => this.form().locator(`[ng-model='${model}']`);
  fromDate = () => this.form().locator(`input[ng-model='${MODELS.fromDate}']`);
  toDate = () => this.form().locator(`input[ng-model='${MODELS.toDate}']`);
  mrn = () => this.form().locator(`input[ng-model='${MODELS.mrn}']`);
  billNo = () => this.form().locator(`input[ng-model='${MODELS.billNo}']`);
  findPatientBtn = () => this.form().getByRole('button', { name: 'Find Patient' });
  searchPatientBtn = () => this.form().getByRole('button', { name: 'Search Patient' });
  searchBtn = () => this.form().getByRole('button', { name: 'Search', exact: true });
  exportBtn = () => this.form().getByRole('button', { name: 'Export to Excel' });
  generateEInvoiceBtn = () => this.form().getByRole('button', { name: 'Generate E-Invoice' });
  cancelledBills = () => this.form().locator(`input[type='checkbox'][ng-model='${MODELS.cancelled}']`);
  radio = (name: 'radiosPT' | 'radiosBLT' | 'radiosEINS', value: string) =>
    this.form().locator(`input[type='radio'][name='${name}'][value='${value}']`);

  // -- select2 dropdowns (keyed on the ng-model of the native <select> behind the widget) --
  nativeSelect = (model: string) => this.form().locator(`select[ng-model='${model}']`);
  select2 = (model: string) => this.form().locator(`div.select2-container:has(+ select[ng-model='${model}'])`);
  chosen = (model: string) => this.select2(model).locator('.select2-chosen');
  private drop = () => this.page.locator('#select2-drop.select2-drop-active');

  // -- tabs / grids --
  tab = (name: 'List of Bills' | 'Consolidated Bills') =>
    this.form().locator(`.nav-tabs a[data-toggle='tab']`).filter({ hasText: new RegExp(`^\\s*${name}\\s*$`) });
  grid = () => this.page.locator('#grid1');
  consolidatedGrid = () => this.page.locator('#grid2');
  rows = (grid: Locator = this.grid()) => grid.locator('.ui-grid-render-container-body .ui-grid-row');
  pagerCount = (grid: Locator = this.grid()) => grid.locator('.ui-grid-pager-count');
  pageInput = (grid: Locator = this.grid()) => grid.locator('.ui-grid-pager-control-input');
  maxPages = (grid: Locator = this.grid()) => grid.locator('.ui-grid-pager-max-pages-number');
  pagerBtn = (name: 'Page to first' | 'Page back' | 'Page forward' | 'Page to last') =>
    this.grid().getByRole('menuitem', { name, exact: true }); // ui-grid gives its pager buttons role="menuitem"
  pageSizePicker = () => this.grid().locator('.ui-grid-pager-row-count-picker');
  viewButtons = () => this.rows().locator("button[ng-click*='ViewBillDetails']");

  // -- toasts / popups --
  toasts = () => this.page.locator('#toast-container .toast-message');
  errorToasts = () => this.page.locator('#toast-container .toast-error');
  patientSearchModal = () => this.page.locator('.modal .patient-search-modal');
  openDatepicker = () => this.page.locator('._720kb-datepicker-calendar._720kb-datepicker-open');

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

  /**
   * Run `action` and return the Bill List search it triggers (request payload + rows the server returned),
   * after the grid has caught up with the response.
   */
  async searchVia(action: () => Promise<unknown>, execFlag: string = EXEC.list): Promise<FetchResult> {
    await this.waitForAppIdle();
    const [response] = await Promise.all([
      this.page.waitForResponse(r => isFetch(r, execFlag), { timeout: 60_000 }),
      action(),
    ]);
    const result = await toFetchResult(response);
    expect(result.status, `${execFlag} request succeeded`).toBe(200);
    await this.waitForAppIdle();
    if (execFlag !== EXEC.consolidated) await this.expectGridTotal(result.rows.length);
    return result;
  }

  /** Click Search and return the search it triggers. */
  search(): Promise<FetchResult> {
    return this.searchVia(() => this.searchBtn().click());
  }

  // ---- navigation -----------------------------------------------------------------------------------------------

  /**
   * Open List Of Bills through the side menu (Billing > List Of Bills) and return the search the screen runs on
   * load. A direct URL cannot be used: the app sends a freshly loaded /#/BillList back to the dashboard.
   */
  async open(): Promise<FetchResult> {
    await expect(this.billingMenu(), 'side menu is loaded').toBeVisible({ timeout: 45_000 });
    await this.waitForAppIdle();
    await this.billingMenu().locator('> a').click();
    await expect(this.billListMenuLink(), 'Billing menu lists "List Of Bills"').toBeVisible({ timeout: 30_000 });
    const [response] = await Promise.all([
      this.page.waitForResponse(r => isFetch(r, EXEC.list), { timeout: 60_000 }),
      this.billListMenuLink().click(),
    ]);
    await expect(this.page).toHaveURL(/#\/BillList$/);
    await expect(this.searchPanelHeading()).toBeVisible();
    const result = await toFetchResult(response);
    await this.waitForAppIdle();
    // every dropdown list is loaded asynchronously; select2 rebuilds its widget when a list arrives
    await expect(this.nativeSelect(MODELS.billStatus).locator('option')).toHaveCount(BILL_STATUS_OPTIONS.length);
    await expect(this.select2(MODELS.billStatus)).toBeVisible();
    await this.expectGridTotal(result.rows.length);
    return result;
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
   * from a window click once the input is no longer focused and the pointer is off the calendar. The page title
   * in the fixed top bar is never covered by a calendar, so it is the click target.
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

  /** Set From / To Date. To Date is set first when the new range starts after the current To Date. */
  async setDateRange(from: Date, to: Date) {
    await this.setDate(this.toDate(), ddMmYyyy(to));
    await this.setDate(this.fromDate(), ddMmYyyy(from));
    await expect(this.fromDate()).toHaveValue(ddMmYyyy(from));
    await expect(this.toDate()).toHaveValue(ddMmYyyy(to));
    await this.clearToasts();
  }

  // ---- select2 --------------------------------------------------------------------------------------------------

  /** Text of the option currently selected in the native <select> (what Angular's model holds). */
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

  /** Open one select2 widget, type the value, select the highlighted exact match with Enter, then verify. */
  private async pickFrom(container: Locator, text: string, verify: () => Promise<void>) {
    const search = this.drop().locator('input.select2-input');
    await expect(async () => {
      await this.waitForAppIdle();
      if (await this.drop().isVisible()) await this.page.keyboard.press('Escape');
      await expect(this.drop()).toBeHidden({ timeout: 2_000 });
      await container.locator('a.select2-choice').click({ timeout: 3_000 });
      await expect(search).toBeVisible({ timeout: 3_000 });
      await search.fill(text);
      await expect(this.drop().locator('li.select2-highlighted .select2-result-label'))
        .toHaveText(text, { timeout: 3_000 });
      await this.page.keyboard.press('Enter');
      await expect(this.drop()).toBeHidden({ timeout: 3_000 });
      await verify();
    }).toPass({ intervals: [500, 1_000, 2_000], timeout: 30_000 });
  }

  async pickSelect2(model: string, text: string) {
    await this.pickFrom(this.select2(model), text, async () => {
      await expect(this.chosen(model)).toHaveText(text, { timeout: 3_000 });
      expect(await this.selectedText(model)).toBe(text);
    });
  }

  async setPageSize(size: string) {
    await this.pickFrom(this.pageSizePicker(), size, async () => {
      await expect(this.pageSizePicker().locator('.select2-chosen')).toHaveText(size, { timeout: 3_000 });
    });
  }

  // ---- grid -----------------------------------------------------------------------------------------------------

  /** Header texts currently rendered by a grid (ui-grid renders only the columns in view). */
  async headerTexts(grid: Locator = this.grid()): Promise<string[]> {
    const texts = await grid.locator('.ui-grid-header-cell').evaluateAll(cells =>
      cells.map(c => ((c as HTMLElement).innerText ?? '').replace(/\s+/g, ' ').trim()),
    );
    return texts.filter(Boolean);
  }

  /** Cell texts of one column on the current page, top to bottom. The column is found by its header text. */
  async columnTexts(header: string, grid: Locator = this.grid()): Promise<string[]> {
    return grid.evaluate((el, wanted) => {
      const clean = (s: string | null | undefined) => (s ?? '').replace(/\s+/g, ' ').trim();
      const head = Array.from(el.querySelectorAll<HTMLElement>('.ui-grid-header-cell'))
        .find(h => clean(h.innerText) === wanted);
      if (!head) throw new Error(`Grid column "${wanted}" is not rendered`);
      const cls = Array.from(head.classList).find(c => /^ui-grid-coluiGrid-/.test(c));
      if (!cls) throw new Error(`Grid column "${wanted}" has no column class`);
      return Array.from(el.querySelectorAll<HTMLElement>(`.ui-grid-render-container-body .ui-grid-row .${cls}`))
        .map(c => clean(c.innerText));
    }, header);
  }

  /**
   * The grid shows `total` records: "x - y of <total> items" and min(total, page size) rows on page 1,
   * or - when total is 0 - the empty state (no rows, record-count text hidden).
   */
  async expectGridTotal(total: number, grid: Locator = this.grid(), pageSize = DEFAULT_PAGE_SIZE) {
    if (total === 0) {
      await this.expectEmptyState(grid);
      return;
    }
    const last = Math.min(total, pageSize);
    await expect(this.pagerCount(grid), 'record count text').toHaveText(new RegExp(`^\\s*1\\s*-\\s*${last} of ${total} items\\s*$`));
    await expect(this.rows(grid), 'rows on the first page').toHaveCount(last);
  }

  /** Empty result: no rows, the "x - y of n items" text is hidden, the pager stays on page 1 of 1. */
  async expectEmptyState(grid: Locator = this.grid()) {
    await expect(this.rows(grid), 'no rows are listed').toHaveCount(0);
    await expect(this.pagerCount(grid).locator('span'), 'record count text is hidden').toBeHidden();
    await expect(this.pageInput(grid)).toHaveValue('1');
    await expect(this.maxPages(grid)).toHaveText(/\/\s*1\s*$/);
  }
}
