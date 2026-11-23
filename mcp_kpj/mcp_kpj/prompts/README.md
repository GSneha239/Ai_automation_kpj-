# DevHIS Test Prompt Package

Feed any single prompt file in this folder back to Claude Code and it will:
1. Run the test on DevHIS with the Playwright browser tools (capturing a screenshot per step),
2. Generate an HTML report in the **standard format** (see below), and
3. Generate a **parallel Playwright Java** script.

## Files
| File | Page | Creates data? |
|---|---|---|
| `TC01_multiple_appointment_booking.md` | OP > Appointment > Multiple Appointment Booking | Yes (new appointment) |
| `TC02_registration.md` | OP > Registration (VisitScreen) | Yes (new patient) |
| `TC03_outpatient_queue_consent.md` | OP > Outpatient Queue Management > Consent/Forms | Yes (new consent doc) |

Just paste the contents of one file as your message. Nothing else is needed.

---

## Shared conventions (all prompts follow these)

### Login
- URL entry point: `https://devhis.sancyberhad.com/#/PatientDashboard`
- Credentials: username `sandhya`, password `User@123` (or log in manually if the prompt says so).
- **Login is two-step:** enter credentials → click **Login** → an **Organization = KPJ** and a **Login Cash Counter** dropdown appear → select an **Outpatient counter (`OPD-B-01 (Outpatient)`)** → click **Login** again. Using an Outpatient counter matters for OP flows.
- If the session is already active, the app skips straight to the dashboard — that's fine.

### Output location (per test)
- Put everything under `./report/<TEST_ID>/` (e.g. `report/TC01_MAB/`). Create the folder first.
- Screenshots: `step_1.png`, `step_2.png`, … (one per step; use `fullPage:true` for long forms so the filled data is visible).
- Report: `report/<TEST_ID>/TestReport.html` (self-contained).
- Java script: `report/<TEST_ID>/<ClassName>.java`.

### Report format (MANDATORY — matches REPORT_TEMPLATE.html)
- **Purple gradient header** `linear-gradient(135deg,#667eea 0%,#764ba2 100%)`, white text: Title, Application, Module, Executed date, Tester.
- **Test Summary** table (key/value; bold first column `#4a3b78`, ~280px), including Total / Passed / Manual / Failed and an Overall Result status badge.
- **Execution Steps** table with columns exactly: **# / Step Name / Description / Expected / Actual / Status / Screenshot**.
- **Status badges** (`.status`): `.pass` green, `.manual` amber, `.fail` red.
- **Screenshots** embedded as base64 `data:image/png` in a `.gallery` of `<figure id="shot_N">` with a "back to top" link. The Screenshot column links to in-page anchors `#shot_N` — **NOT** file paths, **NO** `target="_blank"` (the preview panel blocks non-localhost/file links).
- Fully **self-contained** (embedded images) so it renders in the preview panel and anywhere.
- Build the HTML with a small Node script (write it, run it, delete it) reading the `step_*.png` files and base64-embedding them. A ready template lives at `report/../REPORT_TEMPLATE.html`.

### Playwright Java script (MANDATORY, generated in parallel)
- A standalone class with `public static void main` using `com.microsoft.playwright.*` (matches the repo's Playwright 1.40.0 dependency). Headed, viewport 1920x1080.
- Reproduce the same steps; save `report/step_<n>.png` per step.
- **DevHIS is AngularJS + select2 + ui-grid.** Set select2/hidden dropdowns and grid selections via `page.evaluate` (Angular `ngModel` controller + `select2('val',…)`), not `selectOption`, because the real `<select>` elements are offscreen.
- **Signatures** need real mouse input: `page.mouse().down()/move()/up()` on the canvas (synthetic DOM events don't draw).
- **New tabs**: capture with `context.waitForPage(() -> clickThatOpensTab())`.
- Keep the class self-contained and runnable; no framework classes.

### DevHIS gotchas to remember
- Many dropdowns are **select2** (offscreen `<select>`): set them via Angular `ngModel` + `select2('val',…)`.
- The queue grid is **ui-grid**: select rows via `grid.api.selection.selectRow(dataRow)`.
- Saves often require confirming a **"Do You Want To Save"** dialog (click its **SAVE**/**Yes**) — the API call only fires after that.
- Success toasts fire and **fade in ~1s** — capture immediately, and also record the toast text programmatically by intercepting `window.JAlert`.
- On successful registration/consent the app auto-opens extra tabs (Registration Report/label, Consent form) — capture them too.
