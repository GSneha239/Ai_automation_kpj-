# TC01 — Multiple Appointment Booking

Run this test on DevHIS with the Playwright browser tools. Capture a screenshot after
every step into `./report/TC01_MAB/` (create it first). Then produce the standard-format
HTML report AND a parallel Playwright Java script (see "Deliverables").

**Application:** DevHIS — `https://devhis.sancyberhad.com/#/MultipleAppointmentBooking`
**Module:** OP > Appointment > Multiple Appointment Booking
**Note:** This creates a NEW appointment booking.

## Login
Open the URL above. I will **log in manually** when the login page appears; wait for my
confirmation, then continue. (If already logged in, just proceed.)

## Steps — save `./report/TC01_MAB/step_<N>.png` after each
1. manual login → Patient Dashboard  *(mark this step MANUAL)*
2. navigate OP > Appointment > Multiple Appointment Booking
3. fill patient: Title **Mr.**, Name **Arjun Kumar**, **Male**, DOB **15/05/1990**,
   NRIC **900515145678**, email **arjun.kumar.test@example.com**, mobile **123456789**
4. Booking Type = **KPJ**
5. Payor Type = **Self**
6. Appointment Type = **Department**
7. Appointment 1 department = **Cardiology**
8. Appointment 2 department = **Cardiology**
9. Appointment 3 department = **Cardiology**
10. pick one slot in Appointment 1
11. pick one slot in Appointment 2 (different time)
12. pick one slot in Appointment 3 (different time)
13. click **Save**
14. confirm the **"Do You Want To Save"** dialog (click SAVE)
15. verify **"Appointment Saved Successfully"** toast

## Execution notes (so it runs smoothly)
- The department/booking dropdowns are native `<select>` behind custom widgets — set by
  visible option text.
- **Slots:** today's slots are usually disabled (elapsed); a **Doctor must be selected**
  for slots to enable, and slots come from the **"Next Schedule"** (next day) tables.
  Selecting a doctor (e.g. **Demo Doctor**) for Appointment 1 enables all three schedules.
  Pick three **distinct** times (e.g. 08:00 / 09:00 / 10:00 AM). Tick the row checkbox in
  the correct "Next Schedule" table (DOM table index 1, 3, 5 for Appt 1/2/3).
- On save, a confirm dialog appears; click **SAVE**. Success = toast
  "Appointment Saved Successfully" + a Registration Report tab opens with a new
  **Appointment ID** — record it. Toast fades fast, so screenshot immediately (it's
  visible in the confirm-click screenshot).

## Deliverables
1. **Report** `./report/TC01_MAB/TestReport.html` in the standard format (purple header,
   Test Summary, Execution Steps table `# / Step Name / Description / Expected / Actual /
   Status / Screenshot`, embedded base64 screenshot gallery with `#shot_N` links).
   Mark **step 1 = MANUAL**, the rest **PASS**. Note it creates a new appointment booking
   and include the generated Appointment ID in the summary.
2. **Playwright Java** `./report/TC01_MAB/MultipleAppointmentBookingTest.java` — standalone
   `main`, headed, reproduces all steps, pauses for manual login (Scanner + ENTER), sets
   dropdowns via option label, picks slots via the JS row-tag approach, confirms the SAVE
   dialog, asserts the toast, and writes `step_<n>.png` per step.
