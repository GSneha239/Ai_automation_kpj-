---
name: run-devhis-test
description: Run a DevHIS test case end-to-end with the Playwright browser tools — execute each step, capture a screenshot per step, generate the standard-format HTML report, and generate a parallel standalone Playwright Java script. Use when the user says "run TC01/TC02/TC03", names a Multiple Appointment Booking / Registration / Consent-Forms test, or points at a prompts/ file. NOTE: running a test drives the real DevHIS app and CREATES data (new appointment / patient / consent doc).
---

# Run DevHIS Test

Execute one DevHIS test case against the live app, capture evidence, and produce two deliverables: a standard-format HTML report and a parallel Playwright Java script.

## Inputs
- A **test ID**: `TC01`, `TC02`, or `TC03` (accept aliases below). If none is given, ask which test to run.

| Test ID | Prompt file | Page / Module | Creates |
|---|---|---|---|
| `TC01` (Multiple Appointment Booking, MAB) | `prompts/TC01_multiple_appointment_booking.md` | OP > Appointment > Multiple Appointment Booking | New appointment booking |
| `TC02` (Registration, VisitScreen) | `prompts/TC02_registration.md` | OP > Registration (VisitScreen) | New patient registration |
| `TC03` (Consent/Forms, SPC) | `prompts/TC03_outpatient_queue_consent.md` | OP > Outpatient Queue Management > Consent/Forms | New consent document |

## Procedure
1. **Read the matching prompt file** for the requested test ID (table above). It is the authoritative step list, test data, and per-test execution notes. Follow it exactly.
2. **Confirm before running** — running creates real data in DevHIS. State what will be created and wait for the user to confirm (unless they already said "go").
3. **Create the output folder** `./report/<TEST_ID>/` (e.g. `report/TC01_MAB/`) before starting.
4. **Log in** per the shared login convention below (or the manual-login note in the prompt file — TC01 logs in manually; TC02/TC03 use credentials).
5. **Execute each step** with the Playwright MCP tools. After every step, save `report/<TEST_ID>/step_<N>.png` (use `fullPage:true` for long forms so filled data shows). Capture success toasts immediately — they fade in ~1s — and record the toast text by intercepting `window.JAlert`.
6. **Record the generated IDs** the flow produces (Appointment ID / MRN + Visit / consent document ID) for the report summary.
7. **Build the HTML report** — see Deliverable 1.
8. **Generate the Playwright Java script** — see Deliverable 2.

## Shared login convention
- Entry URL: `https://devhis.sancyberhad.com/#/PatientDashboard` (or the page URL named in the prompt file).
- Credentials: username `sandhya`, password `User@123` — unless the prompt file says to log in manually.
- **Two-step login:** enter credentials → click **Login** → an **Organization = KPJ** dropdown and a **Login Cash Counter** dropdown appear → select the Outpatient counter **`OPD-B-01 (Outpatient)`** → click **Login** again. The Outpatient counter matters for OP flows.
- If the session is already active, the app skips to the dashboard — that's fine.

## DevHIS gotchas (apply throughout)
- **select2 dropdowns** (offscreen `<select>`): set them via Angular `ngModel` + `select2('val', …)` through `page.evaluate` / `browser_evaluate`, not `selectOption`.
- **ui-grid** rows (queue grid): select via `grid.api.selection.selectRow(dataRow)` — row clicks are unreliable headlessly.
- **Saves** usually require confirming a **"Do You Want To Save"** dialog (click its **SAVE**/**Yes**); the IUD API call only fires after the confirm.
- **Signatures** need real mouse input: `page.mouse.down()/move()/up()` across the canvas — synthetic DOM events don't draw ink.
- **New tabs**: on successful registration/consent the app auto-opens extra tabs (Registration Report/label, Consent form, SPC document). Capture them with `context.waitForPage(() -> click…)` and screenshot each.

## Deliverable 1 — HTML report (MANDATORY, matches REPORT_TEMPLATE.html)
Write to `report/<TEST_ID>/TestReport.html`, self-contained (base64-embedded images). Build it with a small Node script that reads the `step_*.png` files and base64-embeds them (write the script, run it, delete it). A ready template is at `REPORT_TEMPLATE.html` in the project root.
- **Purple gradient header** `linear-gradient(135deg,#667eea 0%,#764ba2 100%)`, white text: Title, Application, Module, Executed date, Tester.
- **Test Summary** table (key/value; bold first column `#4a3b78`, ~280px): Total / Passed / Manual / Failed, an **Overall Result** status badge, and the generated IDs (Appointment ID, or MRN + Visit, or consent document ID).
- **Execution Steps** table, columns exactly: **# / Step Name / Description / Expected / Actual / Status / Screenshot**.
- **Status badges** (`.status`): `.pass` green, `.manual` amber, `.fail` red. (TC01 step 1 = MANUAL; other steps PASS.)
- **Screenshots**: base64 `data:image/png` in a `.gallery` of `<figure id="shot_N">` with a "back to top" link. The Screenshot column links to in-page anchors `#shot_N` — **NOT** file paths, and **NO** `target="_blank"` (the preview panel blocks non-localhost/file links).

## Deliverable 2 — Playwright Java script (MANDATORY, generated in parallel)
Write to `report/<TEST_ID>/<ClassName>.java` (TC01 → `MultipleAppointmentBookingTest`, TC02 → `RegistrationTest`, TC03 → `ConsentFormsSPCTest`).
- Standalone class with `public static void main` using `com.microsoft.playwright.*` (matches the repo's Playwright 1.40.0 dependency). Headed, viewport 1920x1080.
- Reproduces the same steps; saves `step_<n>.png` per step. TC01 pauses for manual login (Scanner + ENTER); TC02/TC03 do the two-step OPD-counter login.
- Set select2/hidden dropdowns and grid selections via `page.evaluate` (Angular `ngModel` + `select2('val', …)`), not `selectOption`. Draw signatures with `page.mouse`. Handle new tabs with `context.waitForPage(...)`. Confirm the SAVE dialog and assert the success toast.
- Keep the class self-contained and runnable; no framework classes.

## Per-test specifics
The prompt file for each test ID carries the exact test data, step order, and quirks (TC01 slot/Next-Schedule handling and doctor selection; TC02 required-field chain and Next-of-Kin; TC03 consent typeahead, multi-tab document flow, and View Consent). Always defer to it.
