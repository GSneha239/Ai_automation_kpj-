# TC02 — Patient Registration

Run this test on DevHIS with the Playwright browser tools. Capture a screenshot after every
step into `./report/TC02_Registration/` (create it first). Then produce the standard-format
HTML report AND a parallel Playwright Java script (see "Deliverables").

**Application:** DevHIS — entry `https://devhis.sancyberhad.com/#/PatientDashboard`
**Module:** OP > Registration (VisitScreen)
**Note:** This creates a NEW patient registration.

## Steps
1. Login using username **sandhya** / password **User@123** (two-step: after Login, pick
   counter **OPD-B-01 (Outpatient)**, then Login again).
2. Click the **OP** tab.
3. Click **Registration** (it lives directly under OP → routes to `#/VisitScreen`; it is
   NOT inside the Appointment submenu).
4. Fill the form with random but **valid** details.
5. Click **Save**.
6. Confirm the **"Do You Want To Save"** dialog.
7. Verify the success toast **"Registration Saved Successfully & MRN is …"**.

## What "fill with valid details" means here (required-field chain)
This screen is a combined **Patient + Payor + Visit** form; the Save is gated by several
mandatory business fields (each raises a fading JAlert until satisfied). Fill:
- **Patient Information:** Title=Mr., Name (random), **Identification Type = New IC** then a
  valid 12-digit NRIC (e.g. `900101145523`), Gender=Male, DOB matching the NRIC, **Race**,
  Nationality=Malaysian (default). Optional but nice: Religion, Marital Status, Blood Group,
  Income Category, Email, Employer/Occupation.
- **Address:** Address Line 1, **Postcode** (e.g. `50000`, which auto-fills City/State),
  **Mobile No** (exactly **10 digits**, e.g. `1234567890`).
- **Payor Information:** leave as **Self** (self-pay) — Payor type Self, Insurer Self.
- **Visit Information:** **Patient Source = External**, **Encounter Type = Outpatient**,
  **Group/Department = Cardiology**, **Queue/Token No = 1**, payment **Cash** (Charges Only).
- **Next of Kin (required):** add one kin (Title, Name, Nationality=Malaysian, NRIC,
  Relationship=Father, Mobile) and tick **"Same As Patient Address"**, then click **Add**
  (kin must appear in the grid, else "Please Fill Kin Details").
- Then **Save → confirm the "Do You Want To Save" dialog (SAVE)**. The IUD save only fires
  after that confirm.

## Execution notes
- Dropdowns are **select2**; set them via Angular `ngModel` + `select2('val',…)`.
- Success toast "Registration Saved Successfully & MRN is <MRN>" fades in ~1s — screenshot
  immediately and capture the toast text via `window.JAlert` interception. Record the **MRN**
  and **Visit (OP-…)** in the summary.
- On success the app auto-opens **two tabs** — the **Registration Report / patient label**
  and a **Consent form**. Capture BOTH as additional steps.

## Deliverables
1. **Report** `./report/TC02_Registration/TestReport.html` in the standard format (purple
   header; Test Summary incl. patient, Payor=Self, visit, kin, generated MRN/Visit;
   Execution Steps table with the 7 required columns; embedded screenshot gallery with
   `#shot_N` links). Include the two auto-opened tabs (Registration Report + Consent) as
   steps. All steps PASS.
2. **Playwright Java** `./report/TC02_Registration/RegistrationTest.java` — standalone
   `main`, headed, does the two-step login (OPD counter), fills Patient/Payor/Visit + Kin
   via `page.evaluate` (Angular ngModel + select2), clicks Save, confirms the dialog,
   asserts the toast, and screenshots each step incl. the auto-opened tabs.
