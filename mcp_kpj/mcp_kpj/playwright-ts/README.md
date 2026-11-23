# playwright-ts — DevHIS Playwright TypeScript tests

This folder contains the **TypeScript Playwright** automation for DevHIS (KPJ nHIS).

The existing **Java Playwright** automation is separate and unchanged. It lives under
`../src/test/java` (shared core in `../src/main/java`) and is built and run with Maven and the
runners in `../Executables`. Nothing in this folder is used by the Java build.

## Layout

Tests are grouped by application module the same way the Java project groups them
(`../src/test/java/com/kpj/tests/<Module>_page/...` and `.../pages/<Module>_page/...`): one `<Module>_page` folder
for the specs, a matching one under `pages/` for the page objects, and a sub-folder per menu group.

```
playwright-ts/
├── tests/
│   ├── Billing_page/                          specs: Billing module
│   │   ├── billList.spec.ts                   12 tests   Billing > List Of Bills
│   │   ├── cashDeskManagement.spec.ts         11 tests   Billing > Cash Desk Management
│   │   ├── selfPaymentSettlement.spec.ts      13 tests   Billing > Payment Settlement > Self
│   │   └── DoctorShare_page/                  Billing > Doctor Share
│   │       ├── doctorShare.spec.ts            15 tests   Doctor Share
│   │       └── doctorAdditionDeduction.spec.ts 6 tests   Addition Deduction
│   └── pages/
│       └── Billing_page/                      page objects: Billing module
│           ├── BillListPage.ts                also holds the shared login() and date helpers
│           ├── CashDeskManagementPage.ts
│           ├── SelfPaymentSettlementPage.ts
│           └── DoctorShare_page/
│               └── DoctorSharePage.ts
├── playwright.config.ts
├── package.json
├── package-lock.json
└── tsconfig.json
```

To add another module, create `tests/<Module>_page/` and `tests/pages/<Module>_page/` beside the Billing ones.
`playwright.config.ts` and `tsconfig.json` pick up every `*.spec.ts` / `*.ts` under `tests/`, so nothing else changes.

## Prerequisites

Node.js 20 or newer.

## Install

Run all commands from this folder (`mcp_kpj\playwright-ts`).

```
npm ci
```

Install Chromium if it is not already installed for this Playwright version:

```
npx playwright install chromium
```

## Environment variables

| Variable      | Purpose                | Default                              |
|---------------|------------------------|--------------------------------------|
| `DEVHIS_URL`  | Base URL of the app    | `https://devhis.sancyberhad.com`     |
| `DEVHIS_USER` | DevHIS login name      | none — required by the Cash Desk and Bill List tests |
| `DEVHIS_PASS` | DevHIS login password  | none — required by the Cash Desk tests |

The Cash Desk tests stop with an error if `DEVHIS_USER` or `DEVHIS_PASS` is not set.

PowerShell:

```
$env:DEVHIS_URL  = "https://devhis.sancyberhad.com"
$env:DEVHIS_USER = "<login name>"
$env:DEVHIS_PASS = "<password>"
```

Command Prompt:

```
set DEVHIS_URL=https://devhis.sancyberhad.com
set DEVHIS_USER=<login name>
set DEVHIS_PASS=<password>
```

## List tests (does not open a browser or touch DevHIS)

```
npx playwright test --list
```

## Run all tests

```
npx playwright test
```

`npm test` does the same. `npm run test:headed` runs with a visible browser.

> **Live data warning.** `doctorAdditionDeduction.spec.ts` contains the test
> "add, find, filter and delete an Addition entry", which creates and then deletes a record on
> the DevHIS environment it runs against. The Cash Desk tests only read data, or block the Save
> request before it reaches the server.

> **Bill List tests.** `billList.spec.ts` is read only (search, filter, page, view, CSV export in the browser)
> and needs `DEVHIS_USER` / `DEVHIS_PASS`. A check that needs data the environment does not have is reported as
> skipped with a `BLOCKED:` reason.

> **Doctor Share tests.** `doctorShare.spec.ts` changes no data: it searches, ticks charges and uses Process (browser
> only). A successful Save is never automated. It needs `DEVHIS_USER` / `DEVHIS_PASS`; set `DEVHIS_DS_FROM`
> (dd/MM/yyyy) to change the From Date of the data-dependent searches (default: one year back). Tests that fail
> because of a known application defect carry a `DEFECT` annotation.

> **Self Payment Settlement tests.** `selfPaymentSettlement.spec.ts` is read only: it goes as far as the Payment
> Window and cancels - no payment is saved. It needs a real patient with an outstanding self-pay OPD bill; set
> `DEVHIS_SELF_MRN` to choose one, otherwise the MRN is looked up through the Bill List search.

## Run one module, one group or one spec

```
npx playwright test tests/Billing_page
npx playwright test tests/Billing_page/DoctorShare_page
npx playwright test tests/Billing_page/billList.spec.ts
npx playwright test tests/Billing_page/cashDeskManagement.spec.ts
npx playwright test tests/Billing_page/selfPaymentSettlement.spec.ts
npx playwright test tests/Billing_page/DoctorShare_page/doctorShare.spec.ts
npx playwright test tests/Billing_page/DoctorShare_page/doctorAdditionDeduction.spec.ts
```

Run a single test by title:

```
npx playwright test -g "T1 page loads"
```

Run only the Cash Desk tests tagged read-only:

```
npx playwright test --grep "@readonly"
```

## Type check (does not run tests)

```
npx tsc --noEmit -p tsconfig.json
```

## Output

Both folders are generated on each run and are ignored by git.

- `test-results/` — screenshots (on failure), traces (kept on failure) and the last-run status.
- `playwright-report/` — the HTML report. Open it with:

```
npx playwright show-report
```
