# mcp_kpj — DevHIS Playwright Java Framework (prompt-driven)

A standalone, prompt-driven test framework for **DevHIS (KPJ nHIS)**. It ships:
- a **prompt package** (`prompts/`) you can feed to an AI agent (Claude Code) to run a test
  live, produce a report, and (re)generate a script, and
- **runnable Playwright Java scripts** for every test in the package, sharing a small core.

> This project is independent of `playwright-project`.

## Layout
```
mcp_kpj/
├── pom.xml                       Playwright 1.40.0 + exec plugin (Java 17)
├── REPORT_TEMPLATE.html          the standard report format (reference)
├── prompts/                      the prompt package
│   ├── README.md                 conventions + how to use
│   ├── TC01_multiple_appointment_booking.md
│   ├── TC02_registration.md
│   ├── TC03_outpatient_queue_consent.md
│   └── TestPromptPackage.xlsx     same package as an Excel workbook
├── src/main/java/com/kpj/core/
│   ├── DevHisBase.java            browser lifecycle, 2-step login, select2/grid helpers,
│   │                              confirm-save, JAlert capture, signature draw, step+report
│   └── HtmlReport.java            standard-format self-contained HTML report builder
├── src/test/java/com/kpj/tests/
│   ├── MultipleAppointmentBookingTest.java   (TC01)
│   ├── RegistrationTest.java                 (TC02)
│   └── ConsentFormsSPCTest.java              (TC03)
└── test-output/reports/          output: <TC_ID>_<timestamp>/step_*.png + TestReport.html (one folder per run)
```

## Prerequisites
- JDK 17+, Maven 3.8+
- One-time browser download:
  ```bash
  mvn compile exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
  ```

## Run tests

### Run ALL three — browser mode is a runtime toggle
```bash
# one shared browser window (default): TC01 -> TC02 -> TC03, login persists, tabs tidied
mvn -q compile exec:java -Dexec.mainClass=com.kpj.RunAll

# switch to a fresh browser per test whenever you want:
mvn -q compile exec:java -Dexec.mainClass=com.kpj.RunAll -Dbrowser=isolated
```
Every test run writes its own report into a **timestamped folder inside the project** —
`test-output/reports/<TC_ID>_<yyyy-MM-dd_HH-mm-ss>/TestReport.html` (+ its `step_*.png`), so
runs are preserved rather than overwritten (same convention as playwright_project). Extra
runtime flags (both modes): `-Dheadless=true|false` (default false), `-Dslowmo=<ms>`
(default 120), `-Dlogin=auto|manual`.

> `mvn test` also runs all three, but as isolated JUnit tests each launches its **own**
> browser. Use **`RunAll`** (default) for a single shared browser, or
> **`RunAll -Dbrowser=isolated`** for one browser per test.

### Run a SINGLE test
```bash
# via its main() (IDE-friendly)
mvn -q compile exec:java -Dexec.mainClass=com.kpj.tests.RegistrationTest
mvn -q compile exec:java -Dexec.mainClass=com.kpj.tests.ConsentFormsSPCTest
mvn -q compile exec:java -Dexec.mainClass=com.kpj.tests.MultipleAppointmentBookingTest   # manual login by default

# via JUnit (pick one by class name)
mvn test -Dtest=RegistrationTest
mvn test -Dtest=ConsentFormsSPCTest
mvn test -Dtest=MultipleAppointmentBookingTest
```
From an IDE you can also just run any test class's `main()` or its `@Test execute()` method.

**Login mode for TC01 (Multiple Appointment Booking):** manual by default (per the prompt).
Add `-Dlogin=auto` to log in automatically as `sandhya` (used by `mvn test` / `RunAll` so a
batch run never blocks):
```bash
mvn test -Dtest=MultipleAppointmentBookingTest -Dlogin=auto
```

Output lands in `test-output/reports/<TC_ID>_<timestamp>/` (a fresh folder per run):
- `step_1.png … step_N.png` (one per step)
- `TestReport.html` (self-contained: purple header, Test Summary, Execution Steps table
  `# / Step Name / Description / Expected / Actual / Status / Screenshot`, embedded gallery)

## Run via the prompt package (AI agent)
Open `prompts/TestPromptPackage.xlsx` → **Prompts** sheet → copy a test's prompt cell (or use
the matching `prompts/TCxx_*.md`) and paste it to Claude Code. It will drive the browser, build
the report, and refresh the Java script. See `prompts/README.md` for conventions.

## Why the tests are reliable (DevHIS specifics, handled in `DevHisBase`)
- **Two-step login** (credentials → select `OPD-B-01` counter → Login).
- **select2 dropdowns**: real `<select>` is offscreen → set via Angular `ngModel` + `select2('val')`.
- **ui-grid rows**: selected via `grid.api.selection.selectRow(dataRow)`.
- **Saves** require confirming the **"Do You Want To Save"** dialog before the API fires.
- **Toasts** fade in ~1s → captured by intercepting `window.JAlert`.
- **Signatures** need real `page.mouse` drawing.
- **New tabs** (Registration Report, Consent, document) captured via `context.waitForPage(...)`.

## Add a new page/test
1. Add a `prompts/TCxx_*.md` (copy an existing one; swap steps + deliverables).
2. Add a step block + a `TestCases`/`Steps`/`Prompts` row to `TestPromptPackage.xlsx`.
3. Add `src/test/java/com/kpj/tests/XxxTest.java` extending `DevHisBase` (reuse the helpers).
