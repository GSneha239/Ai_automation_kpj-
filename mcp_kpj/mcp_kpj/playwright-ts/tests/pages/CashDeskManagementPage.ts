import { expect, Locator, Page, Request, Response, Route } from '@playwright/test';

/**
 * Billing > Cash Desk Management  (#/CashDeskManagement, heading "Cash Desk Management")
 *
 * Screen facts (from live exploration):
 *  - One form, two tabs: "Cash Desk Management" (entry pane #CashBack) and "Cash Desk Management List" (#CashBackDeskList).
 *  - Entry: Date (720kb datepicker), Type / Bank Name / Cash From / Cash To (select2), Balance (static label),
 *    Amount, Remark, Save. No client-side validation; Save posts to /api/CashDeskManagement/IUD (ExecFlag INSERT).
 *  - List: Date, Cash From, Cash To, Bank Name filters + Search -> /api/CashDeskManagement/Fetch; read-only table.
 *  - Field ids (cashfrom, cashto, bankid and their s2id_* wrappers) are DUPLICATED across the two panes, and the
 *    labels' `for` attributes match nothing — so every locator is scoped to its pane and keyed on ng-model.
 *  - No Edit / Delete exists, so a real Save can never be undone: SaveGuard must be installed before navigation.
 */

export const ROUTE = '/#/CashDeskManagement';

export const API = {
  save: /\/api\/CashDeskManagement\/IUD/i,
  fetch: /\/api\/CashDeskManagement\/Fetch/i,
  loginLocations: /\/api\/Auth\/GetLoginLoc/i,
};

/** Header put on every response this file fabricates, so a mocked response can be told from a real one. */
export const MOCK_HEADER = 'x-cdm-mock';
export const MOCK_SAVE_BODY = '[{"ResultStatus":1}]';

export type Pane = 'entry' | 'list';

/** ng-model of every control, per pane. Entry models are CashDesk.*, list models are CashDesklist.*. */
export const MODELS = {
  entry: {
    date: 'CashDesk.date',
    type: 'CashDesk.typeid',
    bank: 'CashDesk.bankid',
    cashFrom: 'CashDesk.cashfrom',
    cashTo: 'CashDesk.cashto',
    amount: 'CashDesk.amount',
    remark: 'CashDesk.remark',
  },
  list: {
    date: 'CashDesklist.cashdate',
    bank: 'CashDesklist.bankid',
    cashFrom: 'CashDesklist.cashfrom',
    cashTo: 'CashDesklist.cashto',
  },
} as const;

export const SELECT_PLACEHOLDER = '-Select-';
export const TYPE_OPTIONS = ['Bank To User', 'User To Bank', 'User To User'];
export const TABLE_HEADERS = ['Type', 'Bank Name', 'Cash From', 'Cash To', 'Amount', 'Remark'];

/** One row of the /api/CashDeskManagement/Fetch response (only the fields the table shows). */
export interface CashDeskRow {
  typename?: string | null;
  bankname?: string | null;
  cashfromname?: string | null;
  cashtoname?: string | null;
  amount?: number | string | null;
  remark?: string | null;
  [key: string]: unknown;
}

export interface FetchResult {
  status: number;
  mocked: boolean;
  requestPayload: Record<string, unknown>;
  rows: CashDeskRow[];
}

export interface SaveResult {
  payload: Record<string, unknown>;
  status: number;
  mocked: boolean;
}

// ---- small utilities --------------------------------------------------------------------------------------------

/** Collapse whitespace and trim — the app pads employee names with trailing spaces. */
export const norm = (s: unknown): string => String(s ?? '').replace(/\s+/g, ' ').trim();

const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** dd/MM/yyyy (the app's date format) for today plus `days`. */
export function ddMmYyyy(days = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
}

function payloadOf(request: Request): Record<string, unknown> {
  try {
    const json = request.postDataJSON();
    return json && typeof json === 'object' ? (json as Record<string, unknown>) : { __raw: json };
  } catch {
    return { __raw: request.postData() };
  }
}

async function toFetchResult(response: Response): Promise<FetchResult> {
  return {
    status: response.status(),
    mocked: response.headers()[MOCK_HEADER] !== undefined,
    requestPayload: payloadOf(response.request()),
    rows: (await response.json()) as CashDeskRow[],
  };
}

const isFetch = (r: Response) => API.fetch.test(r.url()) && r.request().method() === 'POST';

// ---- login ------------------------------------------------------------------------------------------------------

/** Per page: the secret waiting to be taken by the page (exposeFunction can only be registered once per page). */
const pendingSecrets = new WeakMap<Page, { value?: string }>();

/**
 * Type a secret into an input WITHOUT the value appearing in Playwright's report, step titles, traces or logs.
 *
 * `locator.fill(value)` records the value as the step title ('Fill "<value>"') and as a traced action parameter.
 * Here the value never travels as an action parameter: the page asks for it through an exposed binding, and the
 * in-page code does what fill() does — focus, set the value, fire `input` (AngularJS ng-model listens to it) and
 * `change`. The recorded step is only "Evaluate" with the (secret-free) function source.
 */
async function fillSecret(page: Page, input: Locator, secret: string) {
  let slot = pendingSecrets.get(page);
  if (!slot) {
    const newSlot: { value?: string } = {};
    await page.exposeFunction('__cdmTakeSecret', () => {
      const value = newSlot.value ?? '';
      newSlot.value = undefined; // one-shot
      return value;
    });
    pendingSecrets.set(page, newSlot);
    slot = newSlot;
  }
  slot.value = secret;
  await input.evaluate(async (el: HTMLInputElement) => {
    const value: string = await (window as any).__cdmTakeSecret();
    el.focus();
    el.value = value;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await expect
    .poll(() => input.evaluate((el: HTMLInputElement) => el.value.length > 0), { message: 'secret was entered' })
    .toBe(true);
}

/**
 * DevHIS login: Login Name -> Password -> LOGIN. No-op when the session is already authenticated.
 * Credentials come ONLY from DEVHIS_USER / DEVHIS_PASS — nothing is defaulted or written into the code.
 */
export async function login(page: Page) {
  const user = process.env.DEVHIS_USER;
  const pass = process.env.DEVHIS_PASS;
  if (!user || !pass) {
    throw new Error('Set DEVHIS_USER and DEVHIS_PASS environment variables before running the Cash Desk tests.');
  }
  await page.goto(ROUTE);
  const loginName = page.getByPlaceholder('Login Name');
  const onLogin = await loginName.waitFor({ timeout: 8_000 }).then(() => true, () => false);
  if (!onLogin) return;
  await loginName.fill(user);
  const password = page.getByPlaceholder('Password');
  await fillSecret(page, password, pass);
  // blurring the password fires an async location lookup that must finish before LOGIN authenticates
  await Promise.all([
    page.waitForResponse(r => API.loginLocations.test(r.url()), { timeout: 15_000 }).catch(() => null),
    password.blur(),
  ]);
  await page.getByRole('button', { name: 'LOGIN' }).click();
  // Not expect(...).not.toHaveURL(): a failing expect attaches a page snapshot taken while the password is still in
  // the field. Wait without asserting; on failure blank the field first, then fail.
  const leftLogin = await page
    .waitForURL(url => !/\/Account/i.test(url.toString()), { timeout: 30_000 })
    .then(() => true, () => false);
  if (!leftLogin) {
    await password.evaluate((el: HTMLInputElement) => { el.value = ''; }).catch(() => {});
    throw new Error(`Login failed - check DEVHIS_USER / DEVHIS_PASS (still on the login page: ${page.url()})`);
  }
}

// ---- Save blocker -----------------------------------------------------------------------------------------------

export interface SaveCall {
  method: string;
  url: string;
  payload: Record<string, unknown>;
}

/**
 * Guarantees that NO Cash Desk Save ever reaches the server.
 *
 * Installed on the page AND its browser context before the first navigation. Every request matching /api/CashDeskManagement/IUD
 * (any method) is answered in the browser with HTTP 200, body [{"ResultStatus":1}] and header x-cdm-mock: 1 —
 * route.fulfill() means the request is never sent over the network. Independently, every IUD request the browser
 * issues and every IUD response it receives is counted, so verifyNothingLeaked() can prove each one was answered by
 * this mock and none came from the real server.
 */
export class SaveGuard {
  readonly calls: SaveCall[] = [];
  private requested = 0;
  private answered = 0;
  private readonly realResponses: string[] = [];

  private constructor() {}

  static async install(page: Page): Promise<SaveGuard> {
    const guard = new SaveGuard();
    const context = page.context();
    context.on('request', r => {
      if (API.save.test(r.url())) guard.requested++;
    });
    context.on('response', r => {
      if (API.save.test(r.url()) && r.headers()[MOCK_HEADER] !== '1') {
        guard.realResponses.push(`${r.status()} ${r.request().method()} ${r.url()}`);
      }
    });
    const answer = async (route: Route) => {
      const request = route.request();
      guard.calls.push({ method: request.method(), url: request.url(), payload: payloadOf(request) });
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { [MOCK_HEADER]: '1' },
        body: MOCK_SAVE_BODY,
      });
      guard.answered++;
    };
    // Page routes take precedence over context routes, so the blocker is registered on BOTH: the page route wins for
    // this page, the context route also covers any popup/new tab. Do not add a broader page.route() that could match
    // IUD after this — verifyNothingLeaked() would then report the un-mocked response.
    await context.route(API.save, answer);
    await page.route(API.save, answer);
    return guard;
  }

  get requestCount() { return this.requested; }
  get answeredCount() { return this.answered; }

  lastPayload(): Record<string, unknown> | undefined {
    return this.calls.at(-1)?.payload;
  }

  /** Every Save request was answered by the mock; no Save response came from the real server. */
  async verifyNothingLeaked() {
    await expect
      .poll(() => this.answered, { message: 'every Save (IUD) request was answered by the mock', timeout: 5_000 })
      .toBe(this.requested);
    expect(this.calls.length, 'every Save (IUD) request was captured').toBe(this.requested);
    expect(this.realResponses, 'no Save (IUD) response came from the real server').toEqual([]);
  }
}

// ---- page object ------------------------------------------------------------------------------------------------

export class CashDeskManagementPage {
  constructor(readonly page: Page) {}

  // locators — panes and tabs
  heading = () => this.page.locator('h1#mainHeader');
  entryTab = () => this.page.getByRole('tab', { name: 'Cash Desk Management', exact: true });
  listTab = () => this.page.getByRole('tab', { name: 'Cash Desk Management List', exact: true });
  entryPane = () => this.page.locator('#CashBack');
  listPane = () => this.page.locator('#CashBackDeskList');
  pane = (p: Pane) => (p === 'entry' ? this.entryPane() : this.listPane());

  /** The pane that owns an ng-model (entry = CashDesk.*, list = CashDesklist.*). */
  private paneOf = (model: string) => (model.startsWith('CashDesklist.') ? this.listPane() : this.entryPane());

  // locators — fields (pane + ng-model scoped)
  label = (p: Pane, text: string) =>
    this.pane(p).locator('label.control-label').filter({ hasText: new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`) });
  dateInput = (p: Pane) => this.pane(p).locator(`input[ng-model='${MODELS[p].date}']`);
  nativeSelect = (model: string) => this.paneOf(model).locator(`select[ng-model='${model}']`);
  select2 = (model: string) => this.paneOf(model).locator(`div.select2-container:has(+ select[ng-model='${model}'])`);
  chosen = (model: string) => this.select2(model).locator('.select2-chosen');
  amount = () => this.entryPane().locator(`input[ng-model='${MODELS.entry.amount}']`);
  remark = () => this.entryPane().locator(`textarea[ng-model='${MODELS.entry.remark}']`);
  balance = () => this.entryPane().locator("label[for='balance'] + div label");
  saveBtn = () => this.entryPane().getByRole('button', { name: 'Save', exact: true });
  searchBtn = () => this.listPane().getByRole('button', { name: 'Search', exact: true });
  tableHeaders = () => this.listPane().locator('table thead th');
  tableRows = () => this.listPane().locator('table tbody tr');
  toast = () => this.page.locator('#toast-container .toast-message').last();
  errorToasts = () => this.page.locator('#toast-container .toast-error, #toast-container .toast-warning');
  openDatepicker = () => this.page.locator('._720kb-datepicker-calendar._720kb-datepicker-open');

  // ---- navigation / readiness ---------------------------------------------------------------------------------

  /**
   * Wait until the AngularJS app has no HTTP request in flight (a state check — it cannot miss a response that
   * arrived before the wait started). Every dropdown list is loaded asynchronously and select2 rebuilds when it lands.
   */
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
        { message: 'AngularJS still has HTTP requests in flight', timeout: 30_000 },
      )
      .toBe(0);
  }

  /**
   * Open the screen and wait until it is USABLE: the list call made on load has answered, the heading is shown,
   * no request is in flight and every dropdown list (both panes) is populated. Returns that first list response.
   */
  async open(): Promise<FetchResult> {
    const firstFetch = this.page.waitForResponse(isFetch, { timeout: 45_000 });
    if (this.page.url().includes('#/CashDeskManagement')) await this.page.reload();
    else await this.page.goto(ROUTE);
    const result = await toFetchResult(await firstFetch);
    await expect(this.heading()).toHaveText('Cash Desk Management');
    await this.waitForAppIdle();
    await expect
      .poll(() => this.optionCount(MODELS.entry.type), { message: 'Type list loaded' })
      .toBe(TYPE_OPTIONS.length + 1);
    for (const model of [MODELS.entry.bank, MODELS.entry.cashFrom, MODELS.entry.cashTo,
      MODELS.list.bank, MODELS.list.cashFrom, MODELS.list.cashTo]) {
      await expect.poll(() => this.optionCount(model), { message: `${model} list loaded` }).toBeGreaterThan(1);
    }
    await expect(this.entryPane()).toBeVisible();
    return result;
  }

  async openEntryTab() {
    await this.entryTab().click();
    await expect(this.entryPane()).toBeVisible();
  }

  /** Click the List tab; it reloads the list. Returns that list response. */
  async openListTab(): Promise<FetchResult> {
    const [response] = await Promise.all([this.page.waitForResponse(isFetch), this.listTab().click()]);
    await expect(this.listPane()).toBeVisible();
    await this.waitForAppIdle();
    return toFetchResult(response);
  }

  /** The <li> around a tab carries Bootstrap's `active` class. */
  tabItem = (tab: Locator) => tab.locator('xpath=..');

  // ---- select2 dropdowns --------------------------------------------------------------------------------------

  /** Text of the option currently selected in the native <select> (what Angular's model holds). */
  selectedText(model: string): Promise<string> {
    return this.nativeSelect(model).evaluate(
      (s: HTMLSelectElement) => (s.options[s.selectedIndex]?.text ?? '').replace(/\s+/g, ' ').trim(),
    );
  }

  optionTexts(model: string): Promise<string[]> {
    return this.nativeSelect(model).evaluate((s: HTMLSelectElement) =>
      Array.from(s.options).map(o => o.text.replace(/\s+/g, ' ').trim()),
    );
  }

  optionCount(model: string): Promise<number> {
    return this.nativeSelect(model).locator('option').count();
  }

  /**
   * Select an option in a select2 dropdown, robust to select2 rebuilding its DOM:
   * app idle -> open THIS field's dropdown -> type the value -> hover the EXACT matching result (select2 highlights
   * on hover) -> Enter -> verify the widget text and the underlying <select>. Retried as one unit; the final
   * verification is never relaxed. Exact matching matters: typing "Account Admin_A" also lists "Account Admin_Amrita".
   */
  async pickSelect2(model: string, text: string) {
    const want = norm(text);
    const drop = this.page.locator('#select2-drop.select2-drop-active');
    const search = drop.locator('input.select2-input');
    await expect(async () => {
      await this.waitForAppIdle();
      if (await drop.isVisible()) await this.page.keyboard.press('Escape');
      await expect(drop).toBeHidden({ timeout: 2_000 });
      await this.select2(model).locator('a.select2-choice').click({ timeout: 3_000 });
      await expect(search).toBeVisible({ timeout: 3_000 });
      await search.fill(want);
      const result = drop.locator('li.select2-result-selectable').filter({
        has: this.page.locator('.select2-result-label').getByText(want, { exact: true }),
      }).first();
      await result.hover({ timeout: 3_000 });
      await expect(result).toHaveClass(/select2-highlighted/, { timeout: 2_000 });
      await this.page.keyboard.press('Enter');
      await expect(drop).toBeHidden({ timeout: 3_000 });
      await expect(this.chosen(model)).toHaveText(want, { timeout: 3_000 });
      expect(await this.selectedText(model)).toBe(want);
    }).toPass({ intervals: [500, 1_000, 2_000], timeout: 30_000 });
  }

  selectType(text: string) { return this.pickSelect2(MODELS.entry.type, text); }
  selectBank(text: string, p: Pane = 'entry') { return this.pickSelect2(MODELS[p].bank, text); }
  selectCashFrom(text: string, p: Pane = 'entry') { return this.pickSelect2(MODELS[p].cashFrom, text); }
  selectCashTo(text: string, p: Pane = 'entry') { return this.pickSelect2(MODELS[p].cashTo, text); }

  /** A real (non-placeholder) option text from a dropdown, by position (1 = first real option). */
  async realOption(model: string, position = 1): Promise<string> {
    const texts = await this.optionTexts(model);
    const value = texts[position];
    expect(value, `${model} has a real option at position ${position}`).toBeTruthy();
    return value;
  }

  // ---- inputs -------------------------------------------------------------------------------------------------

  /**
   * Type a date, then close the 720kb datepicker the way the app closes it: it closes from a window click only when
   * the input is no longer focused and the pointer is not over the calendar (Escape is ignored). So: move the pointer
   * away, click the page heading (top bar, never under a calendar) and require that no calendar is open.
   */
  async setDate(p: Pane, value: string) {
    const input = this.dateInput(p);
    await input.fill(value);
    await expect(input).toHaveValue(value);
    await expect(async () => {
      await this.page.mouse.move(0, 0);
      await this.heading().click({ timeout: 3_000 });
      await expect(this.openDatepicker()).toHaveCount(0, { timeout: 2_000 });
    }).toPass({ intervals: [250, 500, 1_000], timeout: 15_000 });
    await expect(input).toHaveValue(value);
  }

  /**
   * The value the application itself holds for an Angular expression (e.g. 'CashDesk.date'), read from the
   * CashDeskManagementController scope — the same $scope that fnSaveCashDeskDetails() builds the Save payload from.
   */
  controllerModel(expression: string): Promise<unknown> {
    return this.page.evaluate(expr => {
      const ng = (window as any).angular;
      const el = document.querySelector("[ng-controller='CashDeskManagementController']");
      if (!ng || !el) return '__no-controller__';
      return ng.element(el).scope().$eval(expr) ?? null;
    }, expression);
  }

  async enterAmount(value: string) { await this.amount().fill(value); }
  async enterRemark(value: string) { await this.remark().fill(value); }

  // ---- actions ------------------------------------------------------------------------------------------------

  /**
   * Click Save and return the captured request payload and the response it got. The response is expected to come
   * from SaveGuard (x-cdm-mock: 1) — this method never decides that by itself; callers assert `mocked`.
   */
  async clickSave(): Promise<SaveResult> {
    const [request] = await Promise.all([
      this.page.waitForRequest(r => API.save.test(r.url())),
      this.saveBtn().click(),
    ]);
    const response = await request.response();
    return {
      payload: payloadOf(request),
      status: response?.status() ?? -1,
      mocked: response?.headers()[MOCK_HEADER] === '1',
    };
  }

  /** Click Search (list pane) and return the real — or route-mocked — list response. */
  async search(): Promise<FetchResult> {
    const [response] = await Promise.all([this.page.waitForResponse(isFetch), this.searchBtn().click()]);
    await this.waitForAppIdle();
    return toFetchResult(response);
  }

  /**
   * Answer the NEXT list call (only) with `rows` instead of asking the server. Used to make the stale-row defect
   * deterministic. Register one, trigger one call, then register the next — a later route takes precedence.
   */
  async mockFetchOnce(rows: CashDeskRow[]) {
    await this.page.route(
      API.fetch,
      route => route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { [MOCK_HEADER]: 'fetch' },
        body: JSON.stringify(rows),
      }),
      { times: 1 },
    );
  }

  // ---- table --------------------------------------------------------------------------------------------------

  async readHeaders(): Promise<string[]> {
    return (await this.tableHeaders().allTextContents()).map(norm);
  }

  async readRows(): Promise<string[][]> {
    return this.tableRows().evaluateAll(rows =>
      rows.map(r => Array.from((r as HTMLTableRowElement).cells).map(c => (c.textContent ?? '').replace(/\s+/g, ' ').trim())),
    );
  }

  /** How one Fetch item is shown in the table: Type, Bank Name, Cash From, Cash To, Amount, Remark. */
  static toTableRow(item: CashDeskRow): string[] {
    return [item.typename, item.bankname, item.cashfromname, item.cashtoname, item.amount, item.remark].map(norm);
  }

  /** The table shows exactly `items`, in order, cell by cell (whitespace-normalised). */
  async expectRowsMatch(items: CashDeskRow[], options: { timeout?: number; message?: string } = {}) {
    await expect
      .poll(() => this.readRows(), {
        message: options.message ?? 'table rows match the list response',
        timeout: options.timeout ?? 10_000,
      })
      .toEqual(items.map(CashDeskManagementPage.toTableRow));
  }

  // ---- toasts -------------------------------------------------------------------------------------------------

  async clearToasts() {
    await this.page.evaluate(() => document.querySelectorAll('#toast-container .toast').forEach(t => t.remove()));
  }

  async readToast(): Promise<string> {
    await expect(this.toast()).toBeVisible();
    return norm(await this.toast().textContent());
  }

  /** Run an action and assert the toast it raises starts with `text`. */
  async expectToast(action: () => Promise<unknown>, text: string) {
    await this.clearToasts();
    await action();
    await expect(this.toast()).toHaveText(new RegExp('^\\s*' + escapeRegExp(text)));
  }
}
