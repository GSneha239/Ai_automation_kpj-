import { test, expect, Page } from '@playwright/test';

/**
 * Billing > Doctor Addition / Deduction  (#/DoctorAddition-Deduction, heading "Addition Deduction")
 *
 * Screen facts (from the manual testing already done):
 *  - Search: From/To Date, Payable Type + Payable Name (select2), Addition / Deduction / ALL radios, Search.
 *  - Grid "List of Addition / Deduction": Payable Type, Payable Name, Date, Amount, Addition / Deduction, Remark, Delete.
 *  - Entry: Addition/Deduction radios, Date*, Payable Type*, Payable Name*, Amount* (numbers, max 10), Remark, Save.
 *  - Save validates in order: Doctor Type -> Doctor -> Amount (empty or 0) -> Date. No confirm dialog.
 *  - Delete removes the row immediately (no confirm dialog).
 *  - No Edit / Clear / Cancel controls on this screen.
 *
 * Every test is independent (fresh page, login, open screen). Credentials: DEVHIS_USER / DEVHIS_PASS. The Add test creates ONE entry
 * (amount 1, unique remark) and always deletes it again at the end.
 */

const ROUTE = '/#/DoctorAddition-Deduction';
const PAYABLE_TYPE = 'Doctor';
const PAYABLE_NAME = 'Demo Doctor';

const today = (() => {
  const d = new Date();
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
})();

/** DevHIS login: Login Name -> Password -> LOGIN. No-op when the session is already authenticated. */
async function login(page: Page) {
  await page.goto(ROUTE);
  const loginName = page.getByPlaceholder('Login Name');
  const onLogin = await loginName.waitFor({ timeout: 8_000 }).then(() => true, () => false);
  if (!onLogin) return;
  await loginName.fill(process.env.DEVHIS_USER ?? 'tieba');
  const password = page.getByPlaceholder('Password');
  await password.fill(process.env.DEVHIS_PASS ?? 'User@123');
  // blurring the password fires an async location lookup that must finish before LOGIN authenticates
  await Promise.all([
    page.waitForResponse(r => r.url().includes('/api/Auth/GetLoginLoc'), { timeout: 15_000 }).catch(() => null),
    password.blur(),
  ]);
  await page.getByRole('button', { name: 'LOGIN' }).click();
  await expect(page, 'Login failed - check DEVHIS_USER / DEVHIS_PASS').not.toHaveURL(/\/Account/i, { timeout: 30_000 });
}

/** Screen object: locators + small actions. Labels have no `for`, so date inputs / select2 use their ng-model. */
class DoctorAdditionDeductionScreen {
  constructor(readonly page: Page) {}

  main = () => this.page.locator('main');
  heading = () => this.page.getByRole('heading', { name: 'Addition Deduction' });
  dateInput = (model: string) => this.page.locator(`input[ng-model='DoctorAdditionDeduction.${model}']`);
  select2 = (model: string) =>
    this.page.locator(`div.select2-container:has(+ select[ng-model='DoctorAdditionDeduction.${model}'])`);
  chosen = (model: string) => this.select2(model).locator('.select2-chosen');
  saveBtn = () => this.page.getByRole('button', { name: 'Save', exact: true });
  searchBtn = () => this.page.getByRole('button', { name: 'Search', exact: true });
  amount = () => this.page.getByPlaceholder('Enter Amount');
  remark = () => this.page.getByPlaceholder('Remark');
  gridTitle = () => this.main().getByText('List of Addition / Deduction');
  gridHeaders = () => this.main().locator('table thead th');
  rowWith = (text: string) => this.main().locator('table tbody tr').filter({ hasText: text });
  toast = () => this.page.locator('#toast-container .toast-message').last();
  // search radios Addition/Deduction/ALL = #radios-0/1/2; entry radios Addition/Deduction = #radios-3/4
  searchAddition = () => this.page.locator('#radios-0');
  searchDeduction = () => this.page.locator('#radios-1');
  searchAll = () => this.page.locator('#radios-2');
  entryAddition = () => this.page.locator('#radios-3');
  entryDeduction = () => this.page.locator('#radios-4');

  /** Native (offscreen) <select> behind a select2 widget. */
  nativeSelect = (model: string) => this.page.locator(`select[ng-model='DoctorAdditionDeduction.${model}']`);

  /**
   * Wait until the AngularJS app has no HTTP request in flight.
   *
   * This is a STATE check, not a response listener: it cannot "miss" a response that arrived before the wait
   * started. It matters because every dropdown list on this screen is loaded by an async request, and when the
   * list arrives Angular re-renders the <select> options and select2 rebuilds its widget. Touching a select2 while
   * that happens loses the selection or closes the dropdown under Playwright's hands.
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
   * Open the screen and wait until it is USABLE, not just rendered: heading visible, no request in flight, and
   * both Payable Type lists (search + entry) populated. On a slow response the Payable Type list arrived ~1 s
   * after the grid, re-rendered the select and wiped a selection that had already been made.
   */
  async open() {
    if (this.page.url().includes('DoctorAddition-Deduction')) await this.page.reload();
    else await this.page.goto(ROUTE);
    await expect(this.heading()).toBeVisible();
    await this.waitForAppIdle();
    for (const model of ['departmentid', 'departmentid1']) {
      await expect(this.nativeSelect(model).locator('option').filter({ hasText: PAYABLE_TYPE }).first()).toBeAttached();
    }
    await expect(this.select2('departmentid')).toBeVisible();
  }

  async clearToasts() {
    await this.page.evaluate(() => document.querySelectorAll('#toast-container .toast').forEach(t => t.remove()));
  }

  /** Run an action and assert the toast it raises starts with `text`. */
  async expectToast(action: () => Promise<unknown>, text: string) {
    await this.clearToasts();
    await action();
    await expect(this.toast()).toHaveText(new RegExp('^\\s*' + text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }

  /** Text of the option currently selected in the native <select> (i.e. what Angular's model holds). */
  selectedText(model: string) {
    return this.nativeSelect(model).evaluate(
      (s: HTMLSelectElement) => (s.options[s.selectedIndex]?.text ?? '').trim(),
    );
  }

  /**
   * Select an option in a select2 dropdown, robust to select2 rebuilding its DOM:
   *  1. app idle (no list being loaded that would rebuild the widget)
   *  2. open THIS field's dropdown (its own `a.select2-choice`)
   *  3. wait for the search input to be visible
   *  4. type the value, wait until the exact option is the highlighted one, press Enter
   *     (keyboard selection: no option element can go stale between "found" and "clicked")
   *  5. verify both the widget text and the underlying <select> value
   * Steps 1-5 run as one unit and are retried if select2 was rebuilt part-way (the dropdown closed,
   * the input vanished, or the selection was reset). The final verification is never relaxed.
   */
  async pickSelect2(model: string, text: string) {
    const drop = this.page.locator('#select2-drop.select2-drop-active');
    const search = drop.locator('input.select2-input');
    await expect(async () => {
      await this.waitForAppIdle();
      if (await drop.isVisible()) await this.page.keyboard.press('Escape');
      await expect(drop).toBeHidden({ timeout: 2_000 });
      await this.select2(model).locator('a.select2-choice').click({ timeout: 3_000 });
      await expect(search).toBeVisible({ timeout: 3_000 });
      await search.fill(text);
      await expect(drop.locator('li.select2-highlighted .select2-result-label')).toHaveText(text, { timeout: 3_000 });
      await this.page.keyboard.press('Enter');
      await expect(drop).toBeHidden({ timeout: 3_000 });
      await expect(this.chosen(model)).toHaveText(text, { timeout: 3_000 });
      expect(await this.selectedText(model)).toBe(text);
    }).toPass({ intervals: [500, 1_000, 2_000], timeout: 30_000 });
  }

  /**
   * Choose the entry Payable Type, then wait until the Payable Name list it loads is present and the app is idle
   * again (list re-rendered, select2 rebuilt) — so the next Payable Name interaction is safe.
   */
  async selectPayableType(type: string) {
    await this.pickSelect2('departmentid', type);
    await expect.poll(() => this.nativeSelect('doctorid').locator('option').count(), {
      message: `Payable Name list loaded for Payable Type "${type}"`, timeout: 30_000,
    }).toBeGreaterThan(1);
    await this.waitForAppIdle();
    expect(await this.selectedText('departmentid')).toBe(type);
  }

  /** Any 720kb datepicker calendar that is currently open. */
  openDatepicker = () => this.page.locator('._720kb-datepicker-calendar._720kb-datepicker-open');

  /**
   * Type a date, then close the datepicker the way the app itself closes it.
   *
   * The 720kb datepicker opens on focus/click of the input and closes from a window "click" handler, but only when
   * the input is no longer focused AND the pointer is not over the calendar. It ignores Escape on the input.
   * The To Date calendar also covers the middle of the grid's title bar, so the old "click the grid title"
   * dismissal was intercepted by the calendar itself.
   *
   * So: move the pointer off any calendar, click the page heading (top bar, never under a calendar), and require
   * that no calendar is open any more; then prove the overlay no longer intercepts pointer events with a trial
   * click (actionability + hit-target check, no side effects) on the grid title.
   */
  async setDate(model: string, value: string) {
    const input = this.dateInput(model);
    await input.fill(value);
    await expect(input).toHaveValue(value);
    await expect(async () => {
      await this.page.mouse.move(0, 0);
      await this.heading().click({ timeout: 3_000 });
      await expect(this.openDatepicker()).toHaveCount(0, { timeout: 2_000 });
    }).toPass({ intervals: [250, 500, 1_000], timeout: 15_000 });
    await expect(input).toHaveValue(value);
    await this.gridTitle().click({ trial: true });
  }

  async search() {
    await Promise.all([
      this.page.waitForResponse(r => r.url().includes('/api/DoctorAddition-Deduction/fetch')),
      this.searchBtn().click(),
    ]);
  }

  async searchToday(filter: 'Addition' | 'Deduction' | 'ALL') {
    await this.setDate('fromdate', today);
    await this.setDate('todate', today);
    await { Addition: this.searchAddition(), Deduction: this.searchDeduction(), ALL: this.searchAll() }[filter].check();
    await this.search();
  }
}

let screen: DoctorAdditionDeductionScreen;

test.beforeEach(async ({ page }) => {
  await login(page);
  screen = new DoctorAdditionDeductionScreen(page);
  await screen.open();
});

test.describe('Doctor Addition / Deduction', () => {
  test('page loads with search panel, grid and entry form', async ({ page }) => {
    await expect(page).toHaveURL(/#\/DoctorAddition-Deduction/);

    await test.step('search panel controls and defaults', async () => {
      await expect(screen.main().getByText('From Date', { exact: true })).toBeVisible();
      await expect(screen.main().getByText('To Date', { exact: true })).toBeVisible();
      await expect(screen.dateInput('fromdate')).toHaveValue(today);
      await expect(screen.dateInput('todate')).toHaveValue(today);
      await expect(screen.select2('departmentid1')).toBeVisible();
      await expect(screen.select2('doctorid1')).toBeVisible();
      await expect(screen.searchAddition()).toBeChecked();
      await expect(screen.searchDeduction()).not.toBeChecked();
      await expect(screen.searchAll()).not.toBeChecked();
      await expect(screen.searchBtn()).toBeEnabled();
    });

    await test.step('grid columns', async () => {
      await expect(screen.gridTitle()).toBeVisible();
      await expect(screen.gridHeaders()).toHaveText([
        'Payable Type', 'Payable Name', 'Date', 'Amount', 'Addition / Deduction', 'Remark', 'Delete',
      ]);
    });

    await test.step('entry form controls and defaults', async () => {
      for (const label of ['Date*', 'Payable Type*', 'Payable Name*', 'Amount*']) {
        await expect(screen.main().getByText(label, { exact: true })).toBeVisible();
      }
      await expect(screen.dateInput('date')).toHaveValue(today);
      await expect(screen.entryAddition()).toBeChecked();
      await expect(screen.entryDeduction()).not.toBeChecked();
      await expect(screen.chosen('departmentid')).toHaveText('--Select--');
      await expect(screen.chosen('doctorid')).toHaveText('--Select--');
      await expect(screen.amount()).toBeEmpty();
      await expect(screen.remark()).toBeEmpty();
      await expect(screen.saveBtn()).toBeEnabled();
    });
  });

  test('Save validates required fields in order', async ({ page }) => {
    await test.step('Payable Type required', async () => {
      await screen.expectToast(() => screen.saveBtn().click(), 'Please Select Doctor Type');
    });
    await test.step('Payable Type loads the Payable Name list', async () => {
      await screen.selectPayableType(PAYABLE_TYPE);
      const names = page.locator("select[ng-model='DoctorAdditionDeduction.doctorid'] option");
      await expect.poll(() => names.count()).toBeGreaterThan(1);
      await expect(names.filter({ hasText: PAYABLE_NAME })).toHaveCount(1);
    });
    await test.step('Payable Name required', async () => {
      await screen.expectToast(() => screen.saveBtn().click(), 'Please Select Doctor!');
    });
    await test.step('Amount required', async () => {
      await screen.pickSelect2('doctorid', PAYABLE_NAME);
      await screen.expectToast(() => screen.saveBtn().click(), 'Please Enter Amount');
    });
    await test.step('Amount must be non-zero', async () => {
      await screen.amount().fill('0');
      await screen.expectToast(() => screen.saveBtn().click(), 'Please Enter Amount');
    });
  });

  test('Amount accepts numbers only, max 10 characters', async () => {
    await screen.amount().pressSequentially('abc');
    await expect(screen.amount()).toBeEmpty();
    await screen.amount().fill('12345678901234');
    await expect(screen.amount()).toHaveValue('1234567890');
  });

  test('invalid entry Date is rejected on Save', async () => {
    await screen.selectPayableType(PAYABLE_TYPE);
    await screen.pickSelect2('doctorid', PAYABLE_NAME);
    await screen.amount().fill('1');
    await screen.setDate('date', '45/13/2026');
    // the app reuses its "Invalid From Date" message for the entry Date
    await screen.expectToast(() => screen.saveBtn().click(), 'Invalid From Date');
  });

  test('Search rejects an invalid From Date', async () => {
    await screen.setDate('fromdate', '99/99/2026');
    await screen.expectToast(() => screen.searchBtn().click(), 'Invalid From Date');
  });

  test('add, find, filter and delete an Addition entry', async ({ page }) => {
    const remark = `AUTO-TEST DAD ${Date.now()}`;
    let created = false;
    try {
      await test.step('save a valid Addition entry', async () => {
        await screen.entryAddition().check();
        await screen.selectPayableType(PAYABLE_TYPE);
        await screen.pickSelect2('doctorid', PAYABLE_NAME);
        await screen.amount().fill('1');
        await screen.remark().fill(remark);
        await screen.clearToasts();
        const [resp] = await Promise.all([
          page.waitForResponse(r => r.url().includes('/api/DoctorAddition-Deduction/IUD')),
          screen.saveBtn().click(),
        ]);
        created = resp.ok() && (await resp.text()).includes('"ResultStatus":1');
        await expect(screen.toast()).toHaveText(/^\s*Doctor Addition deduction Details Added Successfully/);
        expect(created, 'server confirmed the insert').toBe(true);
      });

      await test.step('form is reset after save', async () => {
        await expect(screen.amount()).toBeEmpty();
        await expect(screen.remark()).toBeEmpty();
        await expect(screen.chosen('departmentid')).toHaveText('--Select--');
        await expect(screen.chosen('doctorid')).toHaveText('--Select--');
      });

      await test.step('Search (Addition) lists the entry with its values', async () => {
        await screen.searchToday('Addition');
        const row = screen.rowWith(remark);
        await expect(row).toHaveCount(1);
        await expect(row.locator('td')).toHaveText([
          PAYABLE_TYPE, /.*/, today, /^1(\.00)?$/, /^addition$/i, remark, /Delete/,
        ]);
        // soft: a wrong value is reported as a failure but the flow (and the clean-up) continues
        await expect.soft(row.locator('td').nth(1), 'Payable Name column shows the doctor').toHaveText(PAYABLE_NAME);
      });

      await test.step('Search (Deduction) does not list it', async () => {
        await screen.searchToday('Deduction');
        await expect(screen.rowWith(remark)).toHaveCount(0);
      });

      await test.step('Search (ALL) lists it', async () => {
        await screen.searchToday('ALL');
        await expect.soft(screen.rowWith(remark), 'ALL filter includes Addition entries').toHaveCount(1);
      });
    } finally {
      if (created) {
        await test.step('Delete removes the entry', async () => {
          await screen.clearToasts();
          await screen.searchToday('Addition');
          await expect(screen.rowWith(remark)).toHaveCount(1);
          await Promise.all([
            page.waitForResponse(r => r.url().includes('/api/DoctorAddition-Deduction/IUD')),
            screen.rowWith(remark).getByRole('button', { name: 'Delete' }).click(),
          ]);
          await expect(screen.toast()).toHaveText(/^\s*Deleted successfully/);
          await expect(screen.rowWith(remark)).toHaveCount(0);
        });
      }
    }
  });
});
