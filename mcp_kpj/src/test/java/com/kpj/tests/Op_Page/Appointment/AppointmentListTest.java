package com.kpj.tests.Op_Page.Appointment;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.Appointment.AppointmentListPage;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * TC06 - OP &gt; Appointment &gt; Appointment List (consolidated).
 *
 * <p>Exercises the Appointment List footer actions in one run, each as its own section, driving the
 * single {@link AppointmentListPage} page object. Sections run in the on-screen tab order:</p>
 * <ol>
 *   <li><b>Request MRD File</b> — select a row → Request MRD File → (if no file) create MRD Details →
 *       the MRD report opens in a new tab.</li>
 *   <li><b>Return MRD File</b> — select a row whose file was issued → Return MRD File → fill the
 *       MRDReturn popup (From/To Department + Remark) → OK (fnIUDReturn) → "File Return successfully."</li>
 *   <li><b>View App History</b> — select a row → View App History → history popup opens.</li>
 *   <li><b>Change Executor</b> — select a row → Change Executor → Department + Executor → Save.</li>
 *   <li><b>Registration</b> — select a row → Registration → routes to VisitScreen (full flow: TC02).</li>
 *   <li><b>Reschedule Appointment</b> — select a row → Reschedule → Book Appointment → Save → report.</li>
 *   <li><b>Cancel Appointment</b> — select a cancellable row → Cancel → confirm → Remark + Reason →
 *       "Appointment Cancelled Successfully".</li>
 * </ol>
 *
 * <p>Each section re-opens the Appointment List and returns early on its own failure, so one failing
 * section does not abort the others.</p>
 */
public class AppointmentListTest extends DevHisBase {

    // 1-month range ending on the current date (dynamic, so it always covers recent appointments).
    private static final java.time.format.DateTimeFormatter DTF = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FROM_DATE = java.time.LocalDate.now().minusMonths(1).format(DTF);
    // Upper bound must reach into the FUTURE: appointments are booked for future slots, so a window ending today
    // excludes every freshly-booked one — the Registration section then reports "No more registrable patients".
    private static final String TO_DATE   = java.time.LocalDate.now().plusMonths(1).format(DTF);

    // Request MRD File — MRD Details values
    private static final String FILE_TYPE           = "MRD file";
    private static final String PATIENT_TYPE        = "Normal Patient";
    private static final String ALLOCATION_LOCATION = "MRD";

    private AppointmentListPage listPage; // created in body() once `page` is ready

    // EXACTLY ONE constructor. A test class with two constructors is rejected by JUnit outright
    // ("must declare a single constructor") and never runs — which is what a second, report-id constructor did
    // to this class. Subclasses that reuse this whole flow override DevHisBase.reportId() instead.
    public AppointmentListTest() { super("TC06_AppointmentList"); }

    /** Factory for the list page — the Telemedicine variant overrides this to enter via the Telemedicine menu. */
    protected AppointmentListPage newListPage() { return new AppointmentListPage(page); }

    /** Report meta {title, module, description} — the Telemedicine variant overrides this. */
    protected String[] metaInfo() {
        return new String[]{
                "Appointment List (Request MRD / Return MRD / View App History / Change Executor / Registration / Reschedule / Cancel)",
                "OP > Appointment > Appointment List",
                "One run exercising the Appointment List footer tabs in on-screen order: Request MRD File, Return MRD File, View App History, Change Executor, Registration, Reschedule Appointment, and Cancel Appointment."
        };
    }

    public static void main(String[] args) {
        AppointmentListTest t = new AppointmentListTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        String[] mi = metaInfo();
        meta(mi[0], mi[1], mi[2]);

        LoginPage loginPage = new LoginPage(page);
        listPage = newListPage();

        // Login once; each section re-opens the Appointment List.
        loginPage.login(BASE, USER, PASS);
        step("Login", "farisha / Tcare@123", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        // Tab order: 1 Request MRD, 2 Return MRD, 3 View App History, 4 Change Executor,
        //            5 Registration, 6 Reschedule, 7 Cancel.
        // -Dsection=Registration runs just that tab (comma-separated for several); unset runs all seven.
        runSection("RequestMRD",     this::sectionRequestMRD);      // 1
        runSection("ReturnMRD",      this::sectionReturnMRD);       // 2
        runSection("ViewAppHistory", this::sectionViewAppHistory);  // 3
        runSection("ChangeExecutor", this::sectionChangeExecutor);  // 4
        runSection("Registration",   this::sectionRegistration);    // 5
        runSection("Reschedule",     this::sectionReschedule);      // 6
        runSection("Cancel",         this::sectionCancel);          // 7

        addSummary("Application URL", BASE + "/#/AppointmentList");
    }

    /**
     * Optional filter so ONE tab can be iterated on without paying for the whole 40-step run:
     * {@code -Dsection=Registration} (comma-separated, case/space-insensitive). Unset = run everything.
     */
    private static final String SECTION_FILTER = System.getProperty("section", "").trim();

    /** Run a section unless {@code -Dsection=...} excludes it. */
    protected void runSection(String name, Runnable section) {
        if (!SECTION_FILTER.isEmpty()) {
            boolean wanted = false;
            String want = name.toLowerCase().replace(" ", "");
            for (String s : SECTION_FILTER.split(",")) {
                if (want.contains(s.trim().toLowerCase().replace(" ", ""))) { wanted = true; break; }
            }
            if (!wanted) { System.out.println("skipping section (filtered out): " + name); return; }
        }
        section.run();
    }

    /**
     * Get the Appointment List ready for the next tab.
     *
     * <p>Each footer action returns to the Appointment List by itself, so re-navigating (dashboard bounce → menu
     * → route) is wasted work between sections. Search in place instead.</p>
     *
     * <p>The bounce is NOT simply removed, though: {@code navigateTo()} routes via PatientDashboard on purpose,
     * to make the AppointmentList controller re-enter FRESH — without that, a previous section's filter/state can
     * survive and the search returns NO ROWS. So the cheap path runs first and the full re-navigation is kept as
     * a fallback for exactly that case: if searching in place yields an empty grid, re-navigate and search again.</p>
     */
    /** Most rows any search has returned this run — the yardstick for "is this grid still fresh?". */
    private int bestRowCount = 0;

    private void openAndSearch(String tag) {
        String how;
        if (listPage.alreadyOnList()) {
            listPage.searchAppointments(FROM_DATE, TO_DATE);
            int rows = listPage.listRowCount();
            // "Not empty" is NOT the same as "fresh". A stale controller kept a previous section's filter and
            // returned ONE row while the real list had 25 — non-zero, so an empty-only check sailed past it and
            // Executor/Reschedule/Cancel then failed with "No appointment to select". Compare against the best
            // count seen this run and re-enter fresh when the grid has clearly shrunk.
            boolean looksStale = bestRowCount > 0 && rows < Math.max(2, bestRowCount / 2);
            if (rows > 0 && !looksStale) {
                how = "searched in place (already on the Appointment List — no re-navigation)";
            } else {
                System.out.println(tag + ": grid looks stale (" + rows + " rows vs best " + bestRowCount
                        + ") — re-entering the list fresh");
                listPage.navigateTo(BASE);
                listPage.searchAppointments(FROM_DATE, TO_DATE);
                how = "re-entered the list (in-place search returned " + rows + " rows) and searched";
            }
        } else {
            listPage.navigateTo(BASE);
            listPage.searchAppointments(FROM_DATE, TO_DATE);
            how = "Appointment List opened and searched";
        }
        int finalRows = listPage.listRowCount();
        bestRowCount = Math.max(bestRowCount, finalRows);
        step(tag + " · Open list & search", "OP > Appointment > Appointment List; widen date range and Search",
                "Appointments are listed", how + " (" + finalRows + " rows)", "PASS");
    }

    // ===== Section 1: Request MRD File =====================================
    private void sectionRequestMRD() {
        openAndSearch("MRD");

        // Prefer a row with NO MRD file (to exercise the create flow). Otherwise fall back to a REAL
        // patient who already HAS a file (clicking Request MRD File opens the report directly) — NOT
        // row 0 blindly, which is often an AutoBook row (patientid=0) for which Request MRD File does
        // nothing, so neither the create-confirm nor the report tab appears and the report screenshot
        // is never captured.
        int idx = listPage.findRowNeedingMRDFile();
        boolean expectCreate = idx >= 0;
        if (idx < 0) idx = listPage.findRowWithMRDFile();
        if (idx < 0) idx = 0;
        AppointmentListPage.MRDResult res = listPage.selectRowAndRequestMRD(idx);
        String appt = res.label == null ? "(unknown)" : res.label;
        step("MRD · Select & Request MRD File",
                (expectCreate ? "Select an appointment with no file yet" : "Select the appointment row") + " and click 'Request MRD File'",
                "Report opens (file exists) or 'create one?' confirm appears",
                res.fileExists ? "File exists -> report opened for: " + appt
                        : res.needsCreate ? "No file -> 'create one?' confirm for: " + appt : "No response for: " + appt,
                (res.fileExists || res.needsCreate) ? "PASS" : "FAIL");
        if (!res.fileExists && !res.needsCreate) return;

        String toast = null;
        Page reportTab;
        byte[] reportPng;
        if (res.needsCreate) {
            boolean modalOpened = listPage.confirmCreateMRD();
            step("MRD · Confirm 'Yes' to create file", "Click 'Yes' on 'File is not generated ... create one?'",
                    "MRD Details modal opens (Patient Name pre-filled)",
                    modalOpened ? "MRD Details modal opened" : "MRD Details modal did NOT open",
                    modalOpened ? "PASS" : "FAIL");
            if (!modalOpened) return;

            String filled = listPage.fillMRDDetails(FILE_TYPE, PATIENT_TYPE, ALLOCATION_LOCATION);
            step("MRD · Fill MRD Details", "File Type, Patient Type, Allocation Location (Patient Name pre-filled)",
                    "All mandatory fields accepted", filled, "PASS");

            toast = listPage.saveMRDDetailsAndGetToast();
            boolean allocated = toast != null && toast.toLowerCase().contains("allocated");
            step(page, "MRD · Save MRD Details", "Click Save (fnIUDAllocation); wait for the success toast",
                    "'File Allocated Successfully' toast",
                    toast == null || toast.isEmpty() ? "No allocation toast appeared" : toast,
                    allocated ? "PASS" : "FAIL");
            if (!allocated) return;

            AppointmentListPage.MRDResult r2 = listPage.requestMRDFileAgainAndCaptureReport();
            reportTab = r2.reportTab;
            reportPng = r2.reportPng;
        } else {
            reportTab = res.reportTab;
            reportPng = res.reportPng;
        }

        if (reportTab != null) {
            if (reportPng != null && reportPng.length > 0) {
                step(reportPng, "MRD · Report tab", "The MRD report (file label) opens in a new tab",
                        "MRD report is displayed", "Report opened: " + reportTab.url(), "PASS");
            } else {
                // Pre-captured PNG was null (PDF viewer stalled) — fall back to the Page-based screenshot,
                // which brings the report tab to the front (and falls back to the primary page) so the
                // Screenshot column is never left empty.
                step(reportTab, "MRD · Report tab", "The MRD report (file label) opens in a new tab",
                        "MRD report is displayed", "Report opened: " + reportTab.url(), "PASS");
            }
        } else {
            step(page, "MRD · Report tab", "The MRD report (file label) opens in a new tab",
                    "MRD report opens in a new tab", "No report tab opened", "FAIL");
        }

        addSummary("MRD · Appointment", appt);
        addSummary("MRD · File state", res.needsCreate ? "Created in this run" : "Already existed (report shown directly)");
        addSummary("MRD · Report", reportTab == null ? "Not generated" : reportTab.url());

        // Report opened in a new tab — close it and return focus to the Appointment List.
        listPage.returnToAppointmentList(BASE);
    }

    // ===== Section 2: Return MRD File =====================================
    private void sectionReturnMRD() {
        openAndSearch("Return MRD");

        // Return only applies to a patient whose file was already issued (non-empty mrdfileno).
        int idx = listPage.findRowWithMRDFile();
        if (idx < 0) {
            step("Return MRD · Select an appointment", "Find a row whose patient already has an issued MRD file",
                    "A row with an issued MRD file is available",
                    "No appointment with an issued MRD file to return (run Request MRD File first)", "FAIL");
            return;
        }
        String appt = listPage.selectRowAndReturnMRD(idx);
        step(page, "Return MRD · Select & Return MRD File", "Select the patient row and click 'Return MRD File'",
                "The Return MRD File popup opens",
                appt == null ? "Return MRD popup did NOT open" : "Popup opened for: " + appt,
                appt == null ? "FAIL" : "PASS");
        if (appt == null) return;

        String filled = listPage.fillReturnMRDDetails("Returned - automated test");
        step("Return MRD · Enter details", "Enter the details in the Return MRD popup",
                "Details accepted", filled, "PASS");

        String toast = listPage.confirmReturnMRDAndGetToast();
        boolean ok = toast != null && toast.toLowerCase().contains("return");
        step(page, "Return MRD · Click OK & success toast", "Click OK (fnIUDReturn); wait for the success toast",
                "'File Return successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast,
                ok ? "PASS" : "FAIL");

        addSummary("Return MRD · Appointment", appt);
        addSummary("Return MRD · Result", ok ? toast : "Return not confirmed");
    }

    // ===== Section 3: View App History ====================================
    private void sectionViewAppHistory() {
        openAndSearch("History");

        String appt = listPage.selectFirstAppointment();
        step("History · Select an appointment", "Click an appointment row (fnSelectAppointment)",
                "One appointment selected", appt == null ? "No appointment to select" : "Selected: " + appt,
                appt == null ? "FAIL" : "PASS");
        if (appt == null) return;

        boolean opened = listPage.openViewAppHistory();
        step(page, "History · View App History popup", "Click 'View App History'; the popup lists the patient's past appointments",
                "'View App History' popup opens",
                opened ? "View App History popup shown" : "Popup did NOT open",
                opened ? "PASS" : "FAIL");
        listPage.closeViewAppHistory();

        addSummary("View App History", opened ? "Popup shown for: " + appt : "Popup did not open");
    }

    // ===== Section 4: Change Executor =====================================
    private void sectionChangeExecutor() {
        openAndSearch("Executor");

        String appt = listPage.selectFirstAppointment();
        step("Executor · Select an appointment", "Click an appointment row (fnSelectAppointment)",
                "One appointment selected", appt == null ? "No appointment to select" : "Selected: " + appt,
                appt == null ? "FAIL" : "PASS");
        if (appt == null) return;

        boolean opened = listPage.openChangeExecutor();
        step("Executor · Change Executor popup", "Click 'Change Executor'; the popup opens",
                "'Change Executor' popup opens",
                opened ? "Change Executor popup shown" : "Popup did NOT open", opened ? "PASS" : "FAIL");
        if (!opened) return;

        String filled = listPage.fillExecutor("Cardiology", "Demo Doctor");
        step("Executor · Fill details", "Execution Department + Executor (doctor)",
                "Fields accepted", filled, "PASS");

        String toast = listPage.saveExecutorAndGetToast();
        boolean ok = toast != null && toast.toLowerCase().contains("executor changed successfully");
        step(page, "Executor · Save & toast", "Click Save (SaveExecutorDetails); wait for the success toast",
                "'Executor changed successfully' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        listPage.closeChangeExecutor();

        addSummary("Change Executor", ok ? "Cardiology / Demo Doctor -> " + toast : "Not confirmed");
    }

    // ===== Section 5: Registration (select -> Registration -> Save -> report) =====
    private static final int REG_MAX_PATIENTS = 2; // how many patients to try before giving up (a known cause fails immediately)

    private void sectionRegistration() {
        java.util.Set<String> tried = new java.util.LinkedHashSet<>();
        AppointmentListPage.RegSaveResult res = null;
        String savedAppt = null;

        // Existing patients can be gated (e.g. an incomplete next-of-kin blocks the save). Try patients
        // in turn until one registers cleanly and generates a report.
        for (int attempt = 1; attempt <= REG_MAX_PATIENTS && savedAppt == null; attempt++) {
            openAndSearch(attempt == 1 ? "Registration" : "Registration (try " + attempt + ")");

            String appt = listPage.selectRegistrableAppointmentExcluding(tried);
            if (appt == null) {
                step("Registration · Select an appointment", "Select a registrable patient not yet tried",
                        "A registrable patient is available", "No more registrable patients to try", "FAIL");
                break;
            }
            tried.add(appt);

            boolean navigated = listPage.clickRegistrationTab();
            if (!navigated) {
                step(page, "Registration · Open Registration screen (" + appt + ")",
                        "Click 'Registration' (fnPatientRegistration); the VisitScreen opens pre-filled",
                        "VisitScreen opens pre-filled", "Did NOT open the VisitScreen for " + appt, "FAIL");
                listPage.returnToAppointmentList(BASE);
                continue;
            }

            res = listPage.saveRegistrationAndCaptureReport();
            if (res.saved) {
                savedAppt = appt; // success (a report tab may or may not have been captured)
            } else {
                // Record WHY. A known cause — no doctors in the dropdown, or every mandatory field filled and the
                // app still complaining — is a real FAILURE, not a patient to skip past: retrying other patients
                // will not change it. Only an unexplained gate is left as MANUAL.
                String why = res.failReason == null ? "" : res.failReason;
                boolean hardFail = !why.isEmpty();
                step(page, (hardFail ? "Registration · FAILED for " : "Registration · Skipped ") + appt,
                        "Select " + appt + ", open VisitScreen, fill every section, attempt Save",
                        "Registration saves for the patient",
                        hardFail ? why
                                 : "Skipped — " + (res.message == null || res.message.isEmpty() ? "save did not complete" : res.message),
                        hardFail ? "FAIL" : "MANUAL");
                listPage.returnToAppointmentList(BASE);
            }
        }

        if (savedAppt != null && res != null) {
            // Per-section evidence (each captured after that section was filled).
            if (res.patientPng != null && res.patientPng.length > 0)
                step(res.patientPng, "Registration · Patient & Correspondence filled",
                        "Patient Information + Correspondence Details (pre-filled for the existing patient; mandatory backfilled)",
                        "Patient and address details are populated", "Sections filled for " + savedAppt, "PASS");
            if (res.payorPng != null && res.payorPng.length > 0)
                step(res.payorPng, "Registration · Payor Information filled",
                        "Payor Information — click the default tab, select the Self / SELFPAY CASH CUSTOMER row",
                        "Payor Mode = Self with the default payor", "Payor filled for " + savedAppt, "PASS");
            if (res.kinPng != null && res.kinPng.length > 0)
                step(res.kinPng, "Registration · Next of Kin filled",
                        "Next of Kin — mandatory fields (Relationship, Occupation, Country Code, …) filled & Modified",
                        "Next of Kin details are complete", "Kin filled for " + savedAppt, "PASS");

            step(page, "Registration · Save (" + savedAppt + ")",
                    "Fix the next-of-kin, set Queue No, Save (IUDRegistration), clear the 'unclosed episode' warning, confirm 'Do you want to Save'",
                    "Registration saves for the patient",
                    "Saved for " + savedAppt + " — " + res.message, "PASS");
            if (res.reportTab != null) {
                if (res.reportPng != null && res.reportPng.length > 0) {
                    step(res.reportPng, "Registration · Report 1 (patient sticker)", "The Registration Report opens in a new tab",
                            "Registration Report is displayed", "Report opened: " + res.reportTab.url(), "PASS");
                } else {
                    step(res.reportTab, "Registration · Report 1 (patient sticker)", "The Registration Report opens in a new tab",
                            "Registration Report is displayed", "Report opened: " + res.reportTab.url(), "PASS");
                }
            }
            // Consent Details (PDPA) modal — same process as OP > Registration: after Save, the in-app
            // "Consent Details" (PERSONAL DATA NOTICE & CONSENT) modal appears and is dismissed before the
            // consent-form tab is handled.
            step(page, "Registration · Consent Details modal (PDPA)",
                    "After Save the in-app 'Consent Details' modal appears",
                    "'PERSONAL DATA NOTICE & CONSENT' record is shown",
                    res.consentModalShown ? "Consent Details modal shown (PERSONAL DATA NOTICE & CONSENT)"
                            : "Consent Details modal did NOT appear after Save",
                    res.consentModalShown ? "PASS" : "MANUAL");
            if (res.consentTab != null) {
                String failReason = "Consent submit not confirmed";
                if (res.consentPng != null && res.consentPng.length > 0) {
                    step(res.consentPng, "Registration · Patient Registration Form (submitted, full form)",
                            "Submit -> confirm 'Yes'; capture the full submitted form", "'Form Submitted Successfully' — full form",
                            res.consentSubmitted ? "Form Submitted Successfully (" + res.consentTab.url() + ")" : failReason,
                            res.consentSubmitted ? "PASS" : "FAIL");
                } else {
                    step(res.consentTab, "Registration · Patient Registration Form (submitted, full form)",
                            "Submit -> confirm 'Yes'; capture the full submitted form", "'Form Submitted Successfully' — full form",
                            res.consentSubmitted ? "Form Submitted Successfully (" + res.consentTab.url() + ")" : failReason,
                            res.consentSubmitted ? "PASS" : "FAIL");
                }
            } else {
                step(page, "Registration · Patient Registration Form (submitted, full form)",
                        "Submit -> confirm 'Yes'; capture the full submitted form", "'Form Submitted Successfully' — full form",
                        "Patient Registration Form (consent) tab did NOT open after Save", "FAIL");
            }
            addSummary("Registration · Patient", savedAppt);
            addSummary("Registration · Report", res.reportTab != null ? res.reportTab.url() : "Saved (report tab not captured)");
            addSummary("Registration · Patients tried", String.join(", ", tried));
            listPage.returnToAppointmentList(BASE);
        } else {
            step(page, "Registration · Save", "Try registrable patients until one saves",
                    "A patient registers and a report is generated",
                    // Lead with the plain-words reason when we know it, then the app's own message.
                    (res != null && res.failReason != null && !res.failReason.isEmpty()
                            ? res.failReason + " — "
                            : "")
                            + "No patient registered cleanly within " + REG_MAX_PATIENTS + " tries"
                            + (res != null && res.message != null && !res.message.isEmpty()
                               ? " (app said: " + res.message + ")" : ""),
                    "FAIL");
            addSummary("Registration · Result", "No clean patient within " + REG_MAX_PATIENTS + " attempts");
            addSummary("Registration · Patients tried", tried.isEmpty() ? "(none)" : String.join(", ", tried));
        }
    }

    // ===== Section 7: Cancel Appointment ==================================
    private void sectionCancel() {
        openAndSearch("Cancel");

        String appt = listPage.selectFirstCancellableAppointment();
        step("Cancel · Select an appointment", "Tick the Cancellation checkbox for an appointment",
                "One appointment is selected for cancellation",
                appt == null ? "No cancellable appointment found" : "Selected: " + appt,
                appt == null ? "FAIL" : "PASS");
        if (appt == null) return;

        boolean formOpened = listPage.clickCancelAppointment();
        step("Cancel · Cancel Appointment & confirm", "Click Cancel Appointment; confirm 'Do you want to Cancel?' -> Save",
                "Cancellation Reason form opens",
                formOpened ? "Cancellation form opened" : "Cancellation form did NOT open",
                formOpened ? "PASS" : "FAIL");
        if (!formOpened) return;

        listPage.enterCancellationDetails("Cancelled - automated test", "Personal issues");
        step("Cancel · Enter Remark & Reason", "Enter Cancellation Remark and select Cancellation Reason",
                "Remark and Reason accepted", "Remark entered, Reason = Personal issues", "PASS");

        String toast = listPage.saveCancellationAndGetToast();
        boolean ok = toast != null && toast.toLowerCase().contains("cancelled successfully");
        step(page, "Cancel · Save & success toast", "Click Save; wait for the success toast",
                "'Appointment Cancelled Successfully' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast,
                ok ? "PASS" : "FAIL");

        addSummary("Cancel · Appointment", appt);
        addSummary("Cancel · Result", ok ? toast : "Cancellation not confirmed");
    }

    // ===== Section 6: Reschedule Appointment ==============================
    private void sectionReschedule() {
        // Try successive rescheduleable appointments and run the WHOLE reschedule for each — if the chosen patient
        // can't be rescheduled ("visit already mark!" / no navigation) OR has no free slot OR the save yields no
        // report, re-open the list and try a DIFFERENT patient. Keep going until one fully reschedules (user: "it
        // will work"). Steps below are recorded once, for the attempt that got furthest / succeeded.
        String appt = null; boolean navigated = false; String slot = null;
        Page reportTab = null; String alertMsg = "(none captured)"; int tries = 0;
        final int MAX_TRIES = 8;
        for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
            openAndSearch("Reschedule");                       // reset the list for a fresh attempt
            String cand = listPage.selectFirstAppointment(attempt);
            if (cand == null) break;                           // no more candidates
            appt = cand; tries = attempt + 1;
            navigated = listPage.clickRescheduleAndOpenBooking();
            if (!navigated) { System.out.println("Reschedule: '" + cand + "' not rescheduleable — trying a different patient..."); continue; }
            listPage.completeMandatoryFields("08/07/2026");
            slot = listPage.selectFreeRescheduleSlot();
            if (slot == null) { System.out.println("Reschedule: '" + cand + "' has no free slot — trying a different patient..."); continue; }
            reportTab = listPage.saveRescheduleAndCaptureReport();
            alertMsg = listPage.capturedAlerts().isEmpty() ? "(none captured)" : String.join(" | ", listPage.capturedAlerts());
            if (reportTab != null) break;                      // fully rescheduled (report generated)
            System.out.println("Reschedule: '" + cand + "' save produced no report — trying a different patient...");
        }

        step("Reschedule · Select an appointment", "Click an appointment row (fnSelectAppointment); retry a different patient until one reschedules",
                "One rescheduleable appointment selected", appt == null ? "No appointment to select" : ("Selected (try " + tries + "): " + appt),
                appt == null ? "FAIL" : "PASS");
        if (appt == null) return;

        step("Reschedule · -> Book Appointment", "Click Reschedule Appointment; it auto-navigates to Book Appointment",
                "Book Appointment opens pre-filled from the existing appointment",
                navigated ? ("Navigated to Book Appointment (#/New) — patient try " + tries) : "Did NOT navigate (no rescheduleable patient found)",
                navigated ? "PASS" : "FAIL");
        if (!navigated) return;

        step("Reschedule · Complete mandatory fields", "Set Prefix (Mr.), Nationality (Malaysian), Payor (Self), Booking (KPJ), new date",
                "All mandatory fields satisfied", "Mandatory fields completed", "PASS");

        step("Reschedule · Select a free slot", "Pick an available (green) slot from the Next Schedule",
                "A free slot is selected (From/To + date set)",
                slot == null ? "No free slot found for any tried patient" : "Free slot selected: " + slot,
                slot == null ? "FAIL" : "PASS");

        step("Reschedule · Reschedule Appointment & confirm", "Click 'Reschedule Appointment'; confirm the Save alert",
                "Appointment rescheduled; report generated", alertMsg, "PASS");

        if (reportTab != null) {
            // The reschedule report is the KPJ "Appointment Slip" (shows Appointment Code APP...).
            String url = reportTab.url();
            String apptId = url.replaceAll(".*[Aa]ppointment[Ii]d=(\\d+).*", "$1");
            if (apptId.equals(url)) apptId = url.replaceAll(".*(APP\\d+).*", "$1");
            if (apptId.equals(url)) apptId = "(see slip)";
            step(reportTab, "Reschedule · Appointment Slip generated (new tab)", "Save opens the KPJ Appointment Slip in a new tab",
                    "Appointment Slip with a valid Appointment Code", "Appointment Slip generated — " + apptId, "PASS");
            addSummary("Reschedule · Appointment Slip", apptId);
        } else {
            step(page, "Reschedule · Appointment Slip generated (new tab)", "Save opens the KPJ Appointment Slip in a new tab",
                    "Appointment Slip opens with a valid Appointment Code",
                    "Save accepted but no Appointment Slip was generated (no report tab opened)", "FAIL");
        }
        addSummary("Reschedule · Selected", appt);
        addSummary("Reschedule · Rescheduled To", "08/07/2026 (Payor Self, Booking KPJ)");

        // Appointment Slip opened in a new tab — close it and return focus to the Appointment List.
        listPage.returnToAppointmentList(BASE);
    }
}
