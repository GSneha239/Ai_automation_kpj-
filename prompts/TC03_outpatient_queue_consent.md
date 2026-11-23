# TC03 — Outpatient Queue Management > Consent/Forms (SPC Fillers Buttocks)

Run this test on DevHIS with the Playwright browser tools. Capture a screenshot for **every
step** into `./report/TC03_Consent/` (create it first). Then produce the standard-format HTML
report AND a parallel Playwright Java script (see "Deliverables").

**Application:** DevHIS `https://devhis.sancyberhad.com` + document portal
`nhisformstest.sancyberhad.com`
**Module:** OP > Outpatient Queue Management > Consent/Forms → SPC Fillers Buttocks document
**Note:** This creates a NEW consent document.

## Steps
1. Login using **sandhya** / **User@123** (two-step: after Login pick counter
   **OPD-B-01 (Outpatient)**, then Login again).
2. Click the **OP** tab.
3. Click **Outpatient Queue Management** (`#/queueManagement`).
4. Search the queue (widen From Date, e.g. `01/06/2026`, then Search) and **select any
   patient** row from the table.
5. Click **Consent/Forms**.
6. The **Consent Details** modal opens.
7. In the **Consent** box type **`spc`** and pick a form
   (**SPC Fillers Buttocks** / **SPC Thread Lift**), then click **Save**.
8. It opens **another tab** (the SPC form list on `nhisformstest…`).
9. Click **Create Document**.
10. It navigates to another URL (`…/SPCFillersButtocks/Create…`).
11. The details are **auto-filled** (patient name, MRN, visit, address…).
12. **Sign** any random signature in the signature field.
13. Click **Confirm**.
14. Click **Submit**.
15. On the confirmation alert ("Are you sure you want to submit…") click **Yes**.
16. Verify **"Form Submitted Successfully!"**.
17. Click **OK**.
18. Navigate back to `https://devhis.sancyberhad.com/#/queueManagement`.
19. Re-select the patient and click **View Consent** to view the form (it lists the record).
20. Take a screenshot (also click the row's **Action / view (eye) icon** to reopen the form
    in a new tab and screenshot that too).

## Execution notes (important — these make it work)
- **Select a patient** via the ui-grid API: `grid.api.selection.selectRow(dataRow)` (row
  clicks are unreliable headlessly). Row actions (Consent/Forms, View Consent) target the
  selected row.
- The **Consent typeahead**: type `spc`, wait ~1.5s; the suggestions (`SPC FILLERS BUTTOCKS`,
  `SPC THREAD LIFT`) render hidden — do the type + click of the suggestion in **one timed
  `page.evaluate`** (set ngModel to `spc`, wait, find the `<strong>` suggestion, click its
  clickable ancestor). Then click the Consent modal's **Save** (`ng-click="IUDconsentdetail()"`)
  which **opens a new tab**.
- **Signature:** the "..." button opens a draw modal (canvas + color palette + Confirm).
  **Draw with real mouse events** (`page.mouse.down()/move()/up()` across the canvas) — DOM
  events don't leave ink — then click the visible **Confirm** (id `confirm-signature*`).
- **Submit** = `input#Submit` → "Are you sure…" dialog → **Yes** (`#btnConfirmYes`) →
  "Form Submitted Successfully!" (record the document ID from the resulting `…/Edit/<id>` URL)
  → **OK**.
- **New tabs:** the document opens in a second browser tab; grab it with
  `context.waitForPage(...)` and drive it, then return to the queue tab for View Consent.
- **View Consent** opens a "View Consent/Forms" list modal; click **Search** inside it to load
  the record, then the row's **Action (eye)** icon (`ng-click="viewConsentFormDetail(item)"`)
  opens the saved form in a new tab.

## Deliverables
1. **Report** `./report/TC03_Consent/TestReport.html` in the standard format with a
   **screenshot for every step** (# / Step Name / Description / Expected / Actual / Status /
   Screenshot; embedded base64 gallery with `#shot_N` links). Summary should include the
   patient (name/MRN/visit), the form (SPC Fillers Buttocks), and the generated document ID.
   All steps PASS.
2. **Playwright Java** `./report/TC03_Consent/ConsentFormsSPCTest.java` — standalone `main`,
   headed, two-step login (OPD counter), selects a patient via the grid API, drives the
   Consent typeahead, handles the **multi-tab** flow (`context.waitForPage`), **draws the
   signature with `page.mouse`**, Confirm → Submit → Yes → OK, verifies success, then View
   Consent + the Action-icon form tab; screenshots each step.
