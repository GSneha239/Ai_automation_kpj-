import { test, expect, Page } from '@playwright/test';
import {
  CashDeskManagementPage,
  CashDeskRow,
  FetchResult,
  MODELS,
  SaveGuard,
  SaveResult,
  SELECT_PLACEHOLDER,
  TABLE_HEADERS,
  TYPE_OPTIONS,
  ddMmYyyy,
  login,
  norm,
} from '../pages/Billing_page/CashDeskManagementPage';

/**
 * Billing > Cash Desk Management  (#/CashDeskManagement) — tests T1–T11.
 *
 * SAFETY: the screen has no Edit/Delete, so a saved cash-desk record can never be removed. SaveGuard is installed
 * on the page and its browser context BEFORE the first navigation of every test: every POST /api/CashDeskManagement/IUD is answered
 * in the browser (200, [{"ResultStatus":1}], x-cdm-mock: 1) and never reaches the server. afterEach proves it.
 *
 * Known problems are marked, not fixed:
 *  - known-defect (test.fail, called right before the defect assertion so setup failures still fail normally):
 *      KD-1 an empty search leaves the previous rows on screen     (T8)
 *      KD-2 the entry Date is not sent in the Save payload         (T11)
 *  - known-gap (test passes and records today's behaviour):
 *      GAP-1 no client-side required-field validation on Save      (T9)
 *      GAP-2 Amount accepts non-numeric input                      (T10)
 *  - observation: Balance is a hard-coded label and not sent on Save; Save's tooltip says "Search";
 *      the success toast spells "succesfully".
 *
 * Credentials: DEVHIS_USER / DEVHIS_PASS (required).
 */

test.use({ serviceWorkers: 'block' }); // a service worker could otherwise answer the Save outside page.route
// Traces keep the config's mode (retain-on-failure) but without DOM and accessibility snapshots: both record what is
// typed into the login form's password field. Screenshots (password shown as dots), actions and network stay in.
test.use({ trace: { mode: 'retain-on-failure', snapshots: { dom: false, aria: false, screen: true } } });

const today = ddMmYyyy(0);
const yesterday = ddMmYyyy(-1);
const SAVE_TOAST = 'Record saved succesfully'; // the application's own spelling

let screen: CashDeskManagementPage;
let guard: SaveGuard;
let initialFetch: FetchResult;

test.beforeEach(async ({ page }) => {
  guard = await SaveGuard.install(page); // FIRST — before any navigation
  await login(page);
  screen = new CashDeskManagementPage(page);
  initialFetch = await screen.open();
});

test.afterEach(async () => {
  await guard.verifyNothingLeaked();
});

const observe = (description: string) => test.info().annotations.push({ type: 'observation', description });

/** Count requests to the dropdown / cash-desk APIs fired while `action` runs (dependent-dropdown detection). */
async function apiRequestsDuring(page: Page, action: () => Promise<unknown>): Promise<string[]> {
  const seen: string[] = [];
  const onRequest = (r: { url(): string }) => {
    if (/\/api\/(CommonDropdown|CashDeskManagement)\//i.test(r.url())) seen.push(r.url());
  };
  page.on('request', onRequest);
  try {
    await action();
    await screen.waitForAppIdle();
  } finally {
    page.off('request', onRequest);
  }
  return seen;
}

/** Fill the entry form with valid values taken from the live dropdowns (nothing hard-coded except Type). */
async function fillValidEntry(amount: string, remark: string) {
  const bank = await screen.realOption(MODELS.entry.bank, 1);
  const from = await screen.realOption(MODELS.entry.cashFrom, 1);
  const to = await screen.realOption(MODELS.entry.cashTo, 2);
  await screen.selectType('Bank To User');
  await screen.selectBank(bank);
  await screen.selectCashFrom(from);
  await screen.selectCashTo(to);
  await screen.enterAmount(amount);
  await screen.enterRemark(remark);
  return { bank, from, to };
}

/** Save with the blocker in place and prove the response was the mock, not the server. */
async function saveBlocked(): Promise<SaveResult> {
  await screen.clearToasts();
  const result = await screen.clickSave();
  expect(result.mocked, 'Save was answered by the mock (x-cdm-mock: 1)').toBe(true);
  expect(result.status).toBe(200);
  await expect(screen.toast()).toHaveText(new RegExp('^\\s*' + SAVE_TOAST));
  return result;
}

test.describe('Cash Desk Management', () => {
  // ------------------------------------------------------------------------------------------------------------
  test.describe('A. Page / UI', () => {
    test('T1 page loads with heading, both tabs, entry form, defaults and Balance', { tag: '@readonly' }, async ({ page }) => {
      await test.step('1. screen loads', async () => {
        await expect(page).toHaveURL(/#\/CashDeskManagement/);
        expect(initialFetch.status, 'list call made on load succeeded').toBe(200);
      });

      await test.step('2. page heading', async () => {
        await expect(screen.heading()).toBeVisible();
        await expect(screen.heading()).toHaveText('Cash Desk Management');
      });

      await test.step('3. entry tab is shown and active', async () => {
        await expect(screen.entryTab()).toBeVisible();
        await expect(screen.tabItem(screen.entryTab())).toHaveClass(/\bactive\b/);
        await expect(screen.entryPane()).toBeVisible();
      });

      await test.step('4. list tab is shown and not active', async () => {
        await expect(screen.listTab()).toBeVisible();
        await expect(screen.tabItem(screen.listTab())).not.toHaveClass(/\bactive\b/);
        await expect(screen.listPane()).toBeHidden();
      });

      await test.step('5. entry fields are present', async () => {
        for (const label of ['Date', 'Type', 'Bank Name', 'Cash From', 'Cash To', 'Balance', 'Amount', 'Remark']) {
          await expect(screen.label('entry', label), `label "${label}"`).toBeVisible();
        }
        await expect(screen.dateInput('entry')).toBeVisible();
        for (const model of [MODELS.entry.type, MODELS.entry.bank, MODELS.entry.cashFrom, MODELS.entry.cashTo]) {
          await expect(screen.select2(model), `select2 for ${model}`).toBeVisible();
        }
        await expect(screen.amount()).toBeVisible();
        await expect(screen.remark()).toBeVisible();
        await expect(screen.saveBtn()).toBeEnabled();
        // OBS-1: Save's tooltip is "Search" (copied from the list pane's button)
        await expect(screen.saveBtn()).toHaveAttribute('title', 'Search');
        observe('OBS-1: the Save button tooltip (title) is "Search".');
      });

      await test.step('6. default values', async () => {
        await expect(screen.dateInput('entry')).toHaveValue(today);
        for (const model of [MODELS.entry.type, MODELS.entry.bank, MODELS.entry.cashFrom, MODELS.entry.cashTo]) {
          await expect(screen.chosen(model), `${model} widget`).toHaveText(SELECT_PLACEHOLDER);
          expect(await screen.selectedText(model), `${model} selection`).toBe(SELECT_PLACEHOLDER);
        }
        await expect(screen.amount()).toBeEmpty();
        await expect(screen.remark()).toBeEmpty();
      });

      await test.step('7. Balance is displayed', async () => {
        await expect(screen.balance()).toBeVisible();
        await expect(screen.balance()).toHaveText('RM 10000000');
        observe('OBS-1: Balance is a hard-coded label ("RM 10000000"), not bound to any data.');
      });
    });
  });

  // ------------------------------------------------------------------------------------------------------------
  test.describe('B. Dropdowns', () => {
    test('T2 dropdowns load their options (Type, Bank Name, Cash From, Cash To)', { tag: '@readonly' }, async () => {
      await test.step('8. Type has exactly the 3 expected options', async () => {
        expect(await screen.optionTexts(MODELS.entry.type)).toEqual([SELECT_PLACEHOLDER, ...TYPE_OPTIONS]);
      });

      await test.step('9. Bank Name list is loaded', async () => {
        const banks = await screen.optionTexts(MODELS.entry.bank);
        expect(banks[0]).toBe(SELECT_PLACEHOLDER);
        expect(banks.length).toBeGreaterThan(1);
        expect(banks.slice(1).every(b => b.length > 0), 'no empty bank names').toBe(true);
      });

      let cashFromCount = 0;
      await test.step('10. Cash From employee list is loaded', async () => {
        cashFromCount = await screen.optionCount(MODELS.entry.cashFrom);
        expect(cashFromCount).toBeGreaterThan(1);
      });

      await test.step('11. Cash To employee list is loaded (same list as Cash From)', async () => {
        const cashToCount = await screen.optionCount(MODELS.entry.cashTo);
        expect(cashToCount).toBeGreaterThan(1);
        expect(cashToCount).toBe(cashFromCount);
      });
    });

    test('T3 selecting Type does not change Bank Name', { tag: '@readonly' }, async ({ page }) => {
      const bank = await screen.realOption(MODELS.entry.bank, 1);
      const bankCount = await screen.optionCount(MODELS.entry.bank);

      await test.step(`choose Bank Name "${bank}"`, async () => {
        await screen.selectBank(bank);
      });

      for (const type of ['User To User', 'Bank To User']) {
        await test.step(`12. choose Type "${type}" — Bank Name is unchanged`, async () => {
          const requests = await apiRequestsDuring(page, () => screen.selectType(type));
          expect(requests, 'no dependent list was reloaded').toEqual([]);
          await expect(screen.chosen(MODELS.entry.bank)).toHaveText(bank);
          expect(await screen.selectedText(MODELS.entry.bank)).toBe(bank);
          expect(await screen.optionCount(MODELS.entry.bank)).toBe(bankCount);
          await expect(screen.select2(MODELS.entry.bank)).not.toHaveClass(/select2-container-disabled/);
          await expect(screen.nativeSelect(MODELS.entry.bank)).not.toBeDisabled();
        });
      }
    });

    test('T4 selecting Cash From does not change Cash To', { tag: '@readonly' }, async ({ page }) => {
      const e1 = await screen.realOption(MODELS.entry.cashFrom, 1);
      const e2 = await screen.realOption(MODELS.entry.cashFrom, 2);
      const cashToCount = await screen.optionCount(MODELS.entry.cashTo);

      await test.step(`choose Cash To "${e1}"`, async () => {
        await screen.selectCashTo(e1);
      });

      await test.step(`13. choose Cash From "${e2}" — Cash To is unchanged`, async () => {
        const requests = await apiRequestsDuring(page, () => screen.selectCashFrom(e2));
        expect(requests, 'no dependent list was reloaded').toEqual([]);
        await expect(screen.chosen(MODELS.entry.cashTo)).toHaveText(e1);
        expect(await screen.selectedText(MODELS.entry.cashTo)).toBe(e1);
        expect(await screen.optionCount(MODELS.entry.cashTo)).toBe(cashToCount);
      });
    });
  });

  // ------------------------------------------------------------------------------------------------------------
  test.describe('C. List / Search', () => {
    test('T5 list tab loads, shows the headers and matches the Search-by-today response', { tag: '@readonly' }, async () => {
      await test.step('14. list tab loads', async () => {
        const result = await screen.openListTab();
        expect(result.status).toBe(200);
        await expect(screen.tabItem(screen.listTab())).toHaveClass(/\bactive\b/);
        await expect(screen.entryPane()).toBeHidden();
      });

      await test.step('15. table headers', async () => {
        expect(await screen.readHeaders()).toEqual(TABLE_HEADERS);
      });

      await test.step('16 + 19. Search by today — table matches the Fetch response', async () => {
        await screen.setDate('list', today);
        const result = await screen.search();
        expect(result.status).toBe(200);
        expect(result.mocked).toBe(false);
        expect(result.requestPayload).toMatchObject({ date: today, ExecFlag: 'CashDeskList' });
        await screen.expectRowsMatch(result.rows);
        test.info().annotations.push({ type: 'data', description: `${result.rows.length} row(s) for ${today}` });
      });
    });

    test('T6 Search by Cash From shows only that employee (value taken from live data)', { tag: '@readonly' }, async () => {
      await screen.openListTab();
      await screen.setDate('list', today);
      const all = await screen.search();
      const name = all.rows.map(r => norm(r.cashfromname)).find(n => n.length > 0);
      test.skip(!name, `No Cash Desk record with a Cash From name exists for ${today}; this read-only filter test needs existing data.`);

      await test.step(`17 + 19. filter Cash From = "${name}"`, async () => {
        await screen.selectCashFrom(name!, 'list');
        const result = await screen.search();
        expect(result.status).toBe(200);
        expect(result.requestPayload.date).toBe(today);
        expect(typeof result.requestPayload.cashfrom).toBe('number');
        expect(result.requestPayload.cashfrom as number).toBeGreaterThan(0);
        expect(result.rows.length, 'the employee taken from today\'s data is found again').toBeGreaterThan(0);
        expect(result.rows.map(r => norm(r.cashfromname)).every(n => n === name), 'every row has that Cash From').toBe(true);
        await screen.expectRowsMatch(result.rows);
      });
    });

    test('T7 Search by Bank Name shows only that bank (value taken from live data)', { tag: '@readonly' }, async () => {
      await screen.openListTab();
      await screen.setDate('list', today);
      const all = await screen.search();
      const bankNames = all.rows.map(r => norm(r.bankname));
      const distinctBanks = [...new Set(bankNames.filter(n => n.length > 0))];
      test.skip(distinctBanks.length === 0,
        `No Cash Desk record with a Bank Name exists for ${today}; this read-only filter test needs existing data.`);
      // The filter is only proven if it EXCLUDES something: pick a bank that at least one of today's rows does not have.
      const bank = distinctBanks.find(b => bankNames.some(n => n !== b));
      test.skip(!bank,
        `NOT DETERMINABLE: all ${all.rows.length} Cash Desk row(s) for ${today} have the same Bank Name `
        + `("${distinctBanks[0]}"), so the live data cannot show whether the Bank Name filter excludes other banks. `
        + 'No bank is invented and no data is created; re-run when rows for a second bank exist.');
      const excluded = bankNames.filter(n => n !== bank).length;
      test.info().annotations.push({
        type: 'data',
        description: `${all.rows.length} row(s) for ${today}; filtering on "${bank}" must exclude ${excluded} of them`,
      });

      await test.step(`18 + 19. filter Bank Name = "${bank}"`, async () => {
        await screen.selectBank(bank!, 'list');
        const result = await screen.search();
        expect(result.status).toBe(200);
        expect(result.requestPayload.date).toBe(today);
        expect(typeof result.requestPayload.bankid).toBe('number');
        expect(result.requestPayload.bankid as number).toBeGreaterThan(0);
        expect(result.rows.length, 'the bank taken from today\'s data is found again').toBeGreaterThan(0);
        expect(result.rows.length, 'the filter excluded the rows of other banks').toBeLessThan(all.rows.length);
        expect(result.rows.map(r => norm(r.bankname)).every(n => n === bank), 'every row has that Bank Name').toBe(true);
        await screen.expectRowsMatch(result.rows);
      });
    });

    test(
      'T8 KNOWN DEFECT KD-1 – an empty search leaves the previous rows on screen',
      {
        tag: ['@mock-fetch', '@known-defect'],
        annotation: { type: 'known-defect', description: 'KD-1: when Search returns no data the table keeps the previous rows and shows no "no records" message.' },
      },
      async () => {
        const mockRow: CashDeskRow = {
          typename: 'Bank To User', bankname: 'MOCK BANK CDM', cashfromname: 'MOCK FROM CDM',
          cashtoname: 'MOCK TO CDM', amount: 1, remark: 'MOCK-ROW CDM',
        };
        await screen.openListTab();

        await test.step('first Search returns one mocked row and the table shows it', async () => {
          await screen.mockFetchOnce([mockRow]);
          const first = await screen.search();
          expect(first.mocked, 'first Search answered by the mock').toBe(true);
          await screen.expectRowsMatch([mockRow]);
        });

        let second: FetchResult | undefined;
        await test.step('second Search returns no data', async () => {
          await screen.mockFetchOnce([]);
          second = await screen.search();
          expect(second.mocked, 'second Search answered by the mock').toBe(true);
          expect(second.rows).toEqual([]);
        });

        // Everything above is setup and must pass; only the assertion below is the known defect.
        await guard.verifyNothingLeaked();
        test.fail(true, 'KD-1: an empty search leaves the previous rows on screen.');
        await test.step('KD-1: the table should now be empty', async () => {
          await screen.expectRowsMatch(second!.rows, { timeout: 3_000, message: 'KD-1: table cleared after an empty search' });
        });
      },
    );
  });

  // ------------------------------------------------------------------------------------------------------------
  test.describe('D. Save (blocked — never reaches the server)', () => {
    test(
      'T9 KNOWN GAP GAP-1 – Save with an empty form is not validated on the client',
      {
        tag: ['@mock-save', '@known-gap'],
        annotation: { type: 'known-gap', description: 'GAP-1: Save has no client-side required-field validation; an empty form is posted.' },
      },
      async () => {
        const result = await test.step('21. click Save with the default (empty) form', async () => saveBlocked());

        await test.step('the request was intercepted and its payload captured', async () => {
          expect(guard.calls.length).toBe(1);
          expect(guard.lastPayload()).toEqual(result.payload);
          expect(result.payload).toEqual({ ExecFlag: 'INSERT' });
        });

        await test.step('no validation message or field highlight appears', async () => {
          await expect(screen.errorToasts()).toHaveCount(0);
          await expect(screen.entryPane().locator('.has-error, .ng-invalid')).toHaveCount(0);
          observe(`OBS-1: the success toast uses the application's spelling "${SAVE_TOAST}".`);
        });
      },
    );

    test(
      'T10 KNOWN GAP GAP-2 – Amount accepts non-numeric input',
      {
        tag: ['@mock-save', '@known-gap'],
        annotation: { type: 'known-gap', description: 'GAP-2: the Amount field accepts and posts non-numeric text.' },
      },
      async () => {
        const amount = 'abc-1.5e9';

        await test.step(`22. type "${amount}" into Amount — the field keeps it`, async () => {
          await screen.enterAmount(amount);
          await expect(screen.amount()).toHaveValue(amount);
        });

        await test.step('fill the remaining fields', async () => {
          await fillValidEntry(amount, 'AUTO-TEST CDM T10 (save blocked)');
          await expect(screen.amount()).toHaveValue(amount);
        });

        await test.step('Save (blocked) posts the text unchanged', async () => {
          const result = await saveBlocked();
          expect(result.payload.amount).toBe(amount);
          expect(result.payload.ExecFlag).toBe('INSERT');
          await expect(screen.errorToasts()).toHaveCount(0);
        });
      },
    );

    test(
      'T11 KNOWN DEFECT KD-2 – the entry Date is missing from the Save payload',
      {
        tag: ['@mock-save', '@known-defect'],
        annotation: { type: 'known-defect', description: 'KD-2: the Date chosen on the entry form is not sent on Save.' },
      },
      async () => {
        await test.step(`23. set the entry Date to yesterday (${yesterday}) and fill valid values`, async () => {
          await screen.setDate('entry', yesterday);
          await fillValidEntry('1', 'AUTO-TEST CDM T11 (save blocked)');
          await expect(screen.dateInput('entry')).toHaveValue(yesterday);
        });

        await test.step(`the application's model holds the chosen Date (CashDesk.date = ${yesterday})`, async () => {
          await expect
            .poll(() => screen.controllerModel(MODELS.entry.date), {
              message: 'CashDesk.date on the controller scope (the scope Save reads) equals the chosen Date',
            })
            .toBe(yesterday);
        });

        const result = await test.step('Save (blocked) and capture the payload', async () => saveBlocked());
        await test.info().attach('save-payload.json', {
          body: JSON.stringify(result.payload, null, 2),
          contentType: 'application/json',
        });

        await test.step('the other fields are in the payload', async () => {
          expect(result.payload).toMatchObject({ amount: '1', ExecFlag: 'INSERT' });
          for (const key of ['typeid', 'bankid', 'cashfrom', 'cashto']) {
            expect(typeof result.payload[key], `${key} is sent`).toBe('number');
          }
          // OBS-1: Balance is a static label, so nothing is sent for it.
          expect(result.payload).not.toHaveProperty('balance');
          observe('OBS-1: no "balance" value is sent on Save.');
        });

        // Everything above is setup and must pass; only the assertion below is the known defect.
        await guard.verifyNothingLeaked();
        test.fail(true, 'KD-2: the entry Date is not included in the Save payload.');
        await test.step('KD-2: the payload should contain the chosen Date', async () => {
          expect(result.payload, 'KD-2: Save payload carries the entry Date').toHaveProperty('date', yesterday);
        });
      },
    );
  });
});
