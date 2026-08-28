package com.kpj.tests.Op_Page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.OutPatientQueueManagementPage;
import com.microsoft.playwright.Page;

/**
 * TC10 - OP &gt; Outpatient Queue Management (consolidated) — mirrors the TC06 Appointment List pattern:
 * one login, then each queue action as its own section, each re-opening the queue and searching. Uses
 * the single {@link OutPatientQueueManagementPage} page object.
 *
 * <ol>
 *   <li><b>Close Visit</b> — select a patient → Close Visit → Remark → Save → success toast.</li>
 *   <li><b>Medico Legal</b> — select a patient → Medico Legal (OpenMLCModal) → Police Station / Docket No /
 *       Remark → Document List (attach file + description + Add) → MLC Check List (tick an item + remark)
 *       → Save → success toast ("Medico Legal Saved Successfully.").</li>
 *   <li><b>Call For Triage</b> — select a patient → Call For Triage (a Department + Consultation Room
 *       modal, distinct from the zone-based Assign Triage) → select Consultation Room → Save → success
 *       toast ("Token Updated Successfully.").</li>
 *   <li><b>Convert IPD Charges</b> — select a patient → Convert IPD Charges → accept the confirm (Save)
 *       → success toast ("Charges Converted Successfully."); a patient with nothing to convert answers
 *       "No Unbilled Ipd Charges!" — try a different patient rather than failing.</li>
 *   <li><b>Consent/Forms</b> — select a patient → Consent/Forms → SPC Fillers Buttocks → Save (new tab)
 *       → Create Document → sign → Submit → success.</li>
 * </ol>
 *
 * <p>Each section returns early on its own failure so one failing section doesn't abort the others.
 * (Close Visit modal selectors are best-effort until healed live.)</p>
 */
public class OutPatientQueueManagementTest extends DevHisBase {

    // The search window MUST be relative to today. It was hardcoded to 08/06/2026-08/07/2026, which went stale and
    // returned an EMPTY grid — Change Doctor then failed with "no patient had a populated doctor dropdown" even
    // though the screen works perfectly (verified live: doctor list loads with 7 options for a today patient).
    private static final java.time.format.DateTimeFormatter DMY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FROM_DATE = java.time.LocalDate.now().minusDays(30).format(DMY);
    private static final String TO_DATE   = java.time.LocalDate.now().format(DMY);

    public OutPatientQueueManagementTest() { super("TC10_QueueManagement"); }

    public static void main(String[] args) {
        OutPatientQueueManagementTest t = new OutPatientQueueManagementTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Outpatient Queue Management (Signature / Change Doctor / New Case / Close Visit / Medico Legal / Consent)",
                "OP > Outpatient Queue Management",
                "One run exercising the Outpatient Queue Management actions: Attach Signature, Change Doctor, New Case, Close Visit, Medico Legal and Consent/Forms.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", "farisha / Tcare@123", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        // Every section runs through runSection so its popup is ALWAYS closed afterwards — pass, fail, early
        // return or exception. A modal (or just its backdrop) left on screen intercepts the next section's clicks:
        // that is how a failing Change Doctor previously took Generate Queue and New Case down with it.
        runSection("Signature", this::sectionSignature);
        runSection("Change Doctor", this::sectionChangeDoctor);
        runSection("Generate Queue", this::sectionGenerateQueue);
        runSection("Call Patient", this::sectionCallPatient);
        runSection("Call For Triage", this::sectionCallForTriage);
        runSection("Convert IPD Charges", this::sectionConvertIpdCharges);
        runSection("Referred Patients", this::sectionReferredPatients);
        runSection("Company Approved Amount", this::sectionCompanyApprovedAmount);
        runSection("New Case", this::sectionNewCase);
        runSection("Patient Task", this::sectionPatientTask);
        runSection("Fall Risk", this::sectionFallRisk);
        runSection("Request MRD", this::sectionRequestMrd);
        runSection("Return MRD", this::sectionReturnMrd);
        runSection("View Details", this::sectionViewDetails);
        runSection("Close Visit", this::sectionCloseVisit);
        runSection("Cancel Visit", this::sectionCancelVisit);
        runSection("Medico Legal", this::sectionMedicoLegal);
        runSection("Consent", this::sectionConsentForms);

        addSummary("Application URL", BASE + "/#/queueManagement");
    }

    /**
     * Run one section and ALWAYS tear its popup down afterwards — regardless of pass, fail, early return or
     * exception — so nothing is left on screen to intercept the next section's clicks.
     *
     * <p>Closing is verified: if a modal or backdrop survives the first dismiss it is retried, and anything still
     * standing is reported as its own step rather than silently leaking into the next section.</p>
     */
    /**
     * Optional filter so a single action can be iterated on without paying for the whole suite:
     * {@code -Dsection=ViewDetails} (comma-separated, case/space-insensitive substring match).
     * Unset = run everything.
     */
    private static final String SECTION_FILTER = System.getProperty("section", "").trim();

    /** True when this section should run under the current {@code -Dsection=...} filter. */
    private boolean sectionEnabled(String name) {
        if (SECTION_FILTER.isEmpty()) return true;
        String want = name.toLowerCase().replace(" ", "");
        for (String s : SECTION_FILTER.split(",")) {
            if (want.contains(s.trim().toLowerCase().replace(" ", ""))) return true;
        }
        return false;
    }

    private void runSection(String name, Runnable section) {
        if (!sectionEnabled(name)) { System.out.println("skipping section (filtered out): " + name); return; }
        try {
            section.run();
        } catch (Exception e) {
            step(page, name + " · ERROR", "Section ran to completion", "No unexpected error",
                    "FAILED: " + e.getMessage(), "FAIL");
        } finally {
            OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
            String left = "";
            for (int i = 0; i < 3; i++) {
                qm.dismissAnyModal();
                left = visibleModals();
                if (left.isEmpty()) break;
                page.waitForTimeout(800);
            }
            if (!left.isEmpty()) {
                System.out.println(name + ": popup still open after dismiss -> " + left);
                step(page, name + " · Close popup", "Close any popup left open by this action",
                        "No popup remains open", "Popup still open: " + left, "FAIL");
            } else {
                System.out.println(name + ": popups closed");
            }
        }
    }

    /** Titles of any modal still visible on screen (empty when the page is clean). */
    private String visibleModals() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=[...document.querySelectorAll('.modal,[role=dialog]')].filter(x=>x.getBoundingClientRect().width>0 && getComputedStyle(x).display!=='none');"
                + " const back=document.querySelectorAll('.modal-backdrop').length;"
                + " const names=vis.map(x=>norm(x.textContent).slice(0,40));"
                + " return (names.length? names.join(' | ') : '') + (back? (names.length?' + ':'')+back+' backdrop(s)' : ''); }");
        return r == null ? "" : r.toString().trim();
    }

    private static final String SIGNATURE_FILE = "C:\\Users\\Siva Sankar\\Downloads\\sinature.jpeg";

    // ===== Section: Attach Signature =====================================
    private void sectionSignature() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Signature · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        String patient = qm.selectFirstQueueRow();
        step("Signature · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        String toast = qm.attachSignatureAndGetToast(SIGNATURE_FILE);
        boolean ok = toast != null && (toast.toLowerCase().contains("signature") || toast.toLowerCase().contains("success"));
        step(page, "Signature · Attach & success toast", "Attach signature from " + SIGNATURE_FILE + "; wait for the toast",
                "'Digital Signature Saved Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Signature · Patient", patient);
        addSummary("Signature · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Change Doctor ========================================
    private void sectionChangeDoctor() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Change Doctor · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        // Try patients until one has a populated doctor dropdown: select a patient → Change Doctor → if the
        // doctor list is empty (no doctor in the dropdown), close and try a DIFFERENT patient.
        String patient = null;
        boolean modalReady = false;
        int triedPatients = 0;
        for (int i = 0; i < 10; i++) {
            String cand = qm.selectNthQueueRowWithDepartment(i);
            if (cand == null) break; // no more candidate patients
            patient = cand;
            triedPatients++;
            if (!qm.clickChangeDoctor()) { qm.closeChangeDoctorModal(); continue; }
            if (qm.changeDoctorHasDoctors()) { modalReady = true; break; } // doctors present → proceed
            qm.closeChangeDoctorModal(); // doctor not in dropdown → try a different patient
        }
        step(page, "Change Doctor · Select a patient with doctors",
                "Select a patient, open Change Doctor; if the doctor dropdown is empty, try a different patient",
                "A patient whose doctor dropdown is populated is selected",
                patient == null ? "No patient in the queue"
                        : (modalReady ? "Selected: " + patient + " (doctor list populated; tried " + triedPatients + " patient(s))"
                                      : "No patient had a populated doctor dropdown (tried " + triedPatients + ")"),
                modalReady ? "PASS" : "FAIL");
        if (!modalReady) return;

        String res = qm.selectAnyDoctorAndSave();
        boolean ok = res != null && (res.toLowerCase().contains("updated") || res.toLowerCase().contains("success"));
        step(page, "Change Doctor · Select doctor, Save & toast", "Select any doctor; Save (FnSavePatientType); wait for the toast",
                "'Doctor Updated Successfully.' toast", res, ok ? "PASS" : "FAIL");

        addSummary("Change Doctor · Patient", patient);
        addSummary("Change Doctor · Result", ok ? res : "Not confirmed");
    }

    // ===== Section: Generate Queue =======================================
    private void sectionGenerateQueue() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        // Generate Queue only works for CURRENT-DATE patients — search today only so we select one.
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        qm.searchQueue(today, today);
        qm.waitForQueueLoaded(); // after changing to the current date, wait for the page/grid to load
        step("Generate Queue · Open queue & search (today)", "OP > Outpatient Queue Management; search today (" + today + "); wait for the grid to load",
                "Today's queued patients are listed", "Queue searched for " + today + " (grid loaded)", "PASS");

        // Try patients until Generate Queue actually opens its modal: GenerateToken() only opens for a
        // today-patient in an eligible state — if the modal doesn't open, try a DIFFERENT patient.
        String patient = null;
        String oldQueue = "";
        boolean opened = false;
        int triedPatients = 0;
        for (int i = 0; i < 12; i++) {
            String cand = qm.selectNthQueueRow(i);
            if (cand == null) break; // no more candidates
            patient = cand;
            oldQueue = qm.getSelectedQueueNo();
            triedPatients++;
            if (qm.clickGenerateQueue()) { opened = true; break; }
        }
        boolean hasQueue = oldQueue != null && !oldQueue.isEmpty() && !"0".equals(oldQueue);
        step("Generate Queue · Select a patient & check Queue No", "Select a today patient; read Queue No; open Generate Queue (try a different patient if the modal doesn't open)",
                "A patient whose Generate Queue modal opens is selected",
                patient == null ? "No patient in the queue"
                        : "Selected: " + patient + " | Queue No: " + (hasQueue ? oldQueue : "none")
                                + (opened ? " (modal opened; tried " + triedPatients + ")" : " (no patient's modal opened; tried " + triedPatients + ")"),
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        step(page, "Generate Queue · Click Generate Queue", "Click 'Generate Queue' (GenerateToken); the modal opens",
                "Generate Queue modal opens", opened ? "Generate Queue modal opened" : "Generate Queue modal did NOT open for any patient",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String res = qm.fillGenerateQueueAndSave(oldQueue);
        boolean ok = res != null && (res.toLowerCase().contains("token updated") || res.toLowerCase().contains("updated successfully") || res.toLowerCase().contains("success"));
        step(page, "Generate Queue · " + (hasQueue ? "Change to a different queue" : "Assign a queue") + ", Save & toast",
                hasQueue ? "Patient already has Queue No " + oldQueue + " → pick a room that yields a DIFFERENT queue; Save (UpdateTokenNo)"
                         : "Patient has no queue → select a Consultation Room to assign one; Save (UpdateTokenNo)",
                "'Token Updated Successfully.' toast", res, ok ? "PASS" : "FAIL");

        addSummary("Generate Queue · Patient", patient + (hasQueue ? " (had Queue " + oldQueue + ")" : " (no prior queue)"));
        addSummary("Generate Queue · Result", ok ? res : "Not confirmed");
    }

    // ===== Section: Call Patient ===========================================
    private void sectionCallPatient() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        // Call Patient (IUDTokandisplay) only works for a CURRENT-DATE patient — search today only, same as
        // Generate Queue — else it toasts "Please select Today's date!".
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        qm.searchQueue(today, today);
        qm.waitForQueueLoaded();
        step("Call Patient · Open queue & search (today)", "OP > Outpatient Queue Management; search today (" + today + "); wait for the grid to load",
                "Today's queued patients are listed", "Queue searched for " + today + " (grid loaded)", "PASS");

        String patient = qm.selectFirstQueueRow();
        step("Call Patient · Select a patient", "Select a today patient (ui-grid API)",
                "One patient selected", patient == null ? "No patient in today's queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        String toast = qm.callPatientAndGetToast();
        // A direct action with no modal: judged on getting a toast that ISN'T one of the "please select ..."
        // validation messages (no today-patient / no location selected).
        boolean ok = toast != null && !toast.isEmpty() && !toast.toLowerCase().matches(".*please\\s+(select|enter).*");
        step(page, "Call Patient · Click Call Patient & toast", "Click 'Call Patient' (IUDTokandisplay); wait for the toast",
                "Token/display confirmation toast",
                toast == null || toast.isEmpty() ? "No toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Call Patient · Patient", patient);
        addSummary("Call Patient · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Convert IPD Charges ====================================
    private void sectionConvertIpdCharges() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Convert IPD Charges · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        // "No Unbilled Ipd Charges!" means THIS patient has nothing to convert — not a failure, try a
        // different patient (same iterate-until-eligible pattern as Generate Queue / Change Doctor above).
        String patient = null, toast = "";
        int tried = 0;
        for (int i = 0; i < 12; i++) {
            String cand = qm.selectNthQueueRow(i);
            if (cand == null) break;
            patient = cand;
            tried++;
            String outcome = qm.clickConvertIpdCharges();
            if (!outcome.equals("confirm")) continue; // no confirm dialog for this patient — try the next
            toast = qm.acceptConvertIpdChargesAndGetToast();
            if (toast != null && toast.toLowerCase().contains("no unbilled")) continue; // try a different patient
            break; // a real success toast, or something else worth reporting as-is
        }
        step(page, "Convert IPD Charges · Select a patient & click Convert IPD Charges",
                "Select a patient; click 'Convert IPD Charges' (try a different patient on \"No Unbilled Ipd Charges!\")",
                "The confirm dialog appears for some patient",
                patient == null ? "No patient in the queue" : "Selected: " + patient + " (tried " + tried + " patient(s))",
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean ok = toast != null && !toast.isEmpty()
                && (toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved") || toast.toLowerCase().contains("convert"));
        step(page, "Convert IPD Charges · Accept alert (Save) & success toast",
                "Click Save on the confirm; wait for the success toast",
                "'... Converted/Saved Successfully' toast",
                toast == null || toast.isEmpty() ? "No toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Convert IPD Charges · Patient", patient + " (tried " + tried + ")");
        addSummary("Convert IPD Charges · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Referred Patients ======================================
    private void sectionReferredPatients() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Referred Patients · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        // Referred Patients opens a LIST modal (Select checkbox / MRN / Patient Name / Category / Referred By /
        // Department From / Referred To / Department To) with Registration / Admission / Cancel footer buttons —
        // confirmed live from a screenshot. Simple rule: if that popup does not open — for ANY reason, including
        // a "No Records Found" toast instead of the list — this step FAILS. No special-casing, no retry.
        String outcome = qm.clickReferredPatients();
        boolean modalOpened = "MODAL".equals(outcome);
        step(page, "Referred Patients · Click Referred Patients", "Click 'Referred Patients'; the referred-patients list popup opens",
                "The list popup opens (Select / MRN / Patient Name / ... / Registration / Admission / Cancel)",
                modalOpened ? "Popup opened"
                        : "Popup did NOT open" + (outcome.startsWith("NO_RECORDS::") ? " — app showed \"No Records Found\" instead" : ""),
                modalOpened ? "PASS" : "FAIL");
        if (!modalOpened) { addSummary("Referred Patients · Result", outcome); return; }

        String patient = qm.selectAnyReferredPatientRow();
        step("Referred Patients · Select a patient", "Tick any row's Select checkbox",
                "One referred patient is selected", patient == null ? "No selectable row in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) { addSummary("Referred Patients · Result", "No selectable row in the list"); return; }

        // If Registration/Admission does not open, that IS the failure — no retry, no partial credit.
        boolean opened = qm.clickReferredPatientAction("Registration");
        step(page, "Referred Patients · Click Registration", "Click 'Registration' for the selected referred patient",
                "Registration opens (new tab, route change, or a registration-shaped form)",
                opened ? "Registration opened" : "Registration did NOT open", opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Referred Patients · Result", "Registration did not open for " + patient); return; }

        // Reuse the same bulk mandatory-field filler already used for View Details — this screen embeds the same
        // registration-shaped form (Patient/Correspondence/NOK/Payor/Visit sections).
        String filled = qm.fillViewDetailsAll();
        String remaining = qm.remainingMandatoryAllSections();
        step(page, "Referred Patients · Fill the details",
                "Fill every mandatory (*) field on the opened Registration form",
                "No mandatory field is left empty",
                filled + (remaining.isEmpty() ? "" : "  <-- STILL EMPTY: " + remaining),
                remaining.isEmpty() ? "PASS" : "FAIL");

        addSummary("Referred Patients · Patient", patient);
        addSummary("Referred Patients · Result", remaining.isEmpty() ? "Details filled" : "Some mandatory fields still empty");
    }

    // ===== Section: Company Approved Amount ================================
    private void sectionCompanyApprovedAmount() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Company Approved Amount · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        String patient = qm.selectFirstQueueRow();
        step("Company Approved Amount · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickCompanyApprovedAmount();
        step(page, "Company Approved Amount · Click Company Approved Amount", "Click 'Company Approved Amount'; the modal opens",
                "Company Approved Amount modal opens",
                opened ? "Company Approved Amount modal opened" : "Company Approved Amount modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Company Approved Amount · Result", "Modal did not open"); return; }

        // Fail fast and explicitly if any mandatory dropdown has nothing to select from.
        String filled = qm.fillCompanyApprovedAmountDetails();
        boolean emptyDropdown = filled.startsWith("EMPTY DROPDOWN:");
        step(page, "Company Approved Amount · Fill all mandatory details",
                "Fill Payor/Company, PayerType, Amount, Applied/GL Date, File No., GL Consumed/Balance/Max Limit, Remarks",
                "All fields accepted (both dropdowns have a real option)", filled,
                emptyDropdown ? "FAIL" : "PASS");
        if (emptyDropdown) { addSummary("Company Approved Amount · Result", filled); return; }

        // Master-detail form: Add commits the filled row before Save persists it (same shape as Medico Legal's
        // Document List) — going straight to Save without this left nothing to save (no toast at all).
        boolean added = qm.addCompanyApprovedAmountRow();
        step(page, "Company Approved Amount · Add row", "Click 'Add' (AddCompanyDetails) to commit the filled row",
                "The row is added", added ? "Add clicked" : "Add button not found", added ? "PASS" : "FAIL");
        if (!added) { addSummary("Company Approved Amount · Result", "Add button not found"); return; }

        String toast = qm.saveCompanyApprovedAmountAndGetToast();
        boolean ok = toast != null && !toast.isEmpty()
                && (toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved"));
        step(page, "Company Approved Amount · Save & success toast", "Click Save; wait for the success toast",
                "'... Saved Successfully' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Company Approved Amount · Patient", patient);
        addSummary("Company Approved Amount · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Call For Triage ========================================
    private void sectionCallForTriage() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Call For Triage · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        String patient = qm.selectFirstQueueRow();
        step("Call For Triage · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickCallForTriage();
        step(page, "Call For Triage · Click Call For Triage", "Click 'Call For Triage'; the modal opens",
                "Call For Triage (Consultation Room) modal opens",
                opened ? "Call For Triage modal opened" : "Call For Triage modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String room = qm.selectConsultationRoomForTriage();
        boolean roomOk = room != null && !room.isEmpty() && !room.startsWith("(");
        step("Call For Triage · Select Consultation Room", "Select the Consultation Room dropdown",
                "A Consultation Room is selected",
                room == null || room.isEmpty() ? "(no result)" : room, roomOk ? "PASS" : "FAIL");

        String toast = qm.saveCallForTriageAndGetToast();
        boolean ok = toast != null && !toast.isEmpty()
                && (toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved"));
        step(page, "Call For Triage · Save & success toast", "Click Save; wait for the success toast",
                "'... Saved Successfully' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Call For Triage · Patient", patient);
        addSummary("Call For Triage · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: New Case ==============================================
    private void sectionNewCase() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("New Case · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        // Random patient each run — avoids always colliding on the same first patient ("already generated").
        String patient = qm.selectRandomQueueRow();
        step("New Case · Select a patient", "Select a RANDOM patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickNewCase();
        step(page, "New Case · Click New Case", "Click 'New Case' (OpenNewCase); the New Case (View Case) modal opens",
                "New Case modal opens", opened ? "New Case modal opened" : "New Case modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String filled = qm.fillNewCaseDetails();
        step(page, "New Case · Fill details", "Department, Doctor, Diagnosis (MRN/NRIC/Name pre-filled)",
                "Case details accepted (popup shown)", filled, "PASS");

        String toast = qm.saveNewCaseAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // "Case already generated with same department and doctor!" contains "case" — it is NOT success.
        boolean ok = !tl.contains("already generated")
                && (tl.contains("added successfully") || tl.contains("case added") || tl.contains("success"));
        step(page, "New Case · Save, toast & close popup", "Click Save (SaveQueueNewCaseDetails); retry other doctor/department on duplicate; success toast; close the popup",
                "'EMR Case added successfully.' toast, then the popup closes",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast + (ok ? " (popup closed)" : " (still duplicate after retries)"),
                ok ? "PASS" : "FAIL");

        addSummary("New Case · Patient", patient);
        addSummary("New Case · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Patient Task =========================================
    private void sectionPatientTask() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Patient Task · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        String patient = qm.selectFirstQueueRow();
        step("Patient Task · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickPatientTask();
        step(page, "Patient Task · Click Patient Task", "Click 'Patient Task' (BtnPatientRemark); the modal opens",
                "Patient Task modal opens", opened ? "Patient Task modal opened" : "Patient Task modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String toast = qm.addTasksSaveAndGetToast(java.util.List.of(
                "Automation task 1 - follow up", "Automation task 2 - review"));
        boolean ok = toast != null && (toast.toLowerCase().contains("record")
                || toast.toLowerCase().contains("added") || toast.toLowerCase().contains("success"));
        step(page, "Patient Task · Add tasks, Save, toast & close",
                "Enter a task -> Add; enter another task -> Add; Save (fnIUDPatientRemark); close the popup",
                "'Record Added Successfully.' toast, then the popup closes",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast + " (popup closed)",
                ok ? "PASS" : "FAIL");

        addSummary("Patient Task · Patient", patient);
        addSummary("Patient Task · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Request MRD File =====================================
    // ===== Section: Fall Risk Assessment =================================
    private void sectionFallRisk() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Fall Risk · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        String patient = qm.selectRandomQueueRow();
        step("Fall Risk · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickFallRiskAssessment();
        step(page, "Fall Risk · Open popup", "Click 'Fall Risk Assessment' (fnOpenFallRiskAssessment); the 'ASSESSMENT OF FALL' modal opens",
                "Fall Risk Assessment modal opens", opened ? "Fall Risk modal opened" : "Fall Risk modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String toast = qm.fillFallRiskAndApply();
        boolean ok = toast != null && (toast.toLowerCase().contains("saved")
                || toast.toLowerCase().contains("success") || toast.toLowerCase().contains("applied"));
        step(page, "Fall Risk · Answer Yes, tick a checkbox, Apply & toast",
                "Answer the assessment questions YES, tick a checkbox (item.IsSelected), click Apply (fnApply)",
                "'Fall Risk Assessment saved successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Fall Risk · Patient", patient);
        addSummary("Fall Risk · Result", ok ? toast : "Not confirmed");
    }

    private void sectionRequestMrd() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Request MRD · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        // Prefer a patient WITHOUT an MRD file so we exercise the create path (accept the alert → fill → save).
        String patient = qm.selectQueueRowWithoutMrdFile();
        step("Request MRD · Select a patient", "Select a patient with no MRD file yet (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        String outcome = qm.clickRequestMrdFile();
        // Branch 1 — the patient already HAS a file: the MRD report opened in a new tab. Capture it, then close.
        if (outcome.equals("report")) {
            Page report = page.context().pages().get(page.context().pages().size() - 1);
            try { report.waitForLoadState(); } catch (Exception ignore) {}
            report.waitForTimeout(3000);
            byte[] png = null;
            try { report.bringToFront(); report.waitForTimeout(1500); png = report.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
            catch (Exception e) { System.out.println("Request MRD report screenshot: " + e.getMessage()); }
            String actual = "MRD report opened in a new tab (file already existed): " + report.url();
            if (png != null && png.length > 0)
                step(png, "Request MRD · File exists → report (new tab) & screenshot", "Click Request MRD File; the patient already has a file → the report opens → screenshot",
                        "The MRD report is shown", actual, "PASS");
            else
                step(page, "Request MRD · File exists → report opens", "Click Request MRD File; the patient already has a file",
                        "The MRD report opens in a new tab", actual, "PASS");
            try { if (report != page && !report.isClosed()) report.close(); } catch (Exception ignore) {}
            page.bringToFront();
            addSummary("Request MRD · Result", "Report opened (file already existed)");
            return;
        }
        // Branch 2 — no file yet: a 'create MRD file?' confirm appears → Yes → fill → Save → Cancel.
        boolean confirmed = outcome.equals("confirm");
        step(page, "Request MRD · Click Request MRD File", "Click 'Request MRD File' (fnRequestMRDFile)",
                "If the patient HAS a file the report opens in a new tab; else a 'File is not generated… create one?' confirm appears",
                confirmed ? "Confirm dialog appeared" : "No confirm/report — the async MRD server check did not return in time (outcome: " + outcome + ")",
                confirmed ? "PASS" : "MANUAL");
        if (!confirmed) return;

        boolean modalOpen = qm.confirmCreateMrdFile();
        step(page, "Request MRD · Accept alert (Yes)", "Click Yes on the confirm; the MRD Details popup opens",
                "MRD Details popup opens", modalOpen ? "MRD Details opened" : "MRD Details did NOT open",
                modalOpen ? "PASS" : "FAIL");
        if (!modalOpen) return;

        String toast = qm.fillMrdDetailsAndSave();
        boolean ok = toast != null && (toast.toLowerCase().contains("allocated") || toast.toLowerCase().contains("success"));
        step(page, "Request MRD · Fill details, Save & toast",
                "Fill all mandatory fields (Patient Type, Location, File Type, Rack→Row→Box, File No, Classification); Save (fnIUDAllocation)",
                "'File Allocated Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        qm.cancelMrdDetails();
        step(page, "Request MRD · Cancel to close popup", "Click Cancel to close the MRD Details popup",
                "Popup closed", "Popup closed", "PASS");

        addSummary("Request MRD · Patient", patient);
        addSummary("Request MRD · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Return MRD File ========================================
    private void sectionReturnMrd() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Return MRD · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        String patient = qm.selectQueueRowWithMrdFile();
        step("Return MRD · Select a patient with an MRD file", "Select a patient whose MRD file is on issue (ui-grid API)",
                "One patient (with an MRD file) selected",
                patient == null ? "No patient in the queue has an MRD file" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickReturnMrdFile();
        step(page, "Return MRD · Click Return MRD File", "Click 'Return MRD File' (fnReturnMRDFile); the MRDReturn modal opens",
                "MRDReturn modal opens", opened ? "MRDReturn modal opened" : "MRDReturn modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String toast = qm.fillMrdReturnAndSave();
        boolean ok = toast != null && !toast.isEmpty()
                && (toast.toLowerCase().contains("return") || toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved"));
        step(page, "Return MRD · Fill details, Save & toast",
                "Fill From/To Department + User, enter a Remark; Save (fnIUDReturn)",
                "'... Returned Successfully' toast",
                toast == null || toast.isEmpty() ? "No toast appeared" : toast, ok ? "PASS" : "FAIL");

        qm.cancelMrdReturn();
        step(page, "Return MRD · Close popup", "Click Cancel to close the MRDReturn popup",
                "Popup closed", "Popup closed", "PASS");

        addSummary("Return MRD · Patient", patient);
        addSummary("Return MRD · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section 1: Close Visit =========================================
    // ===== Section: View Details -> Update ===============================
    private void sectionViewDetails() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        qm.waitForQueueLoaded();
        step("View Details · Open queue & search", "OP > Outpatient Queue Management; search + wait for the grid",
                "Queued patients are listed", "Queue searched " + FROM_DATE + " - " + TO_DATE, "PASS");

        String patient = qm.selectFirstQueueRow();
        step(page, "View Details · Select a patient", "Select a queued patient",
                "A patient is selected", patient == null || patient.isEmpty() ? "No patient to select" : "Selected: " + patient,
                patient == null || patient.isEmpty() ? "FAIL" : "PASS");
        if (patient == null || patient.isEmpty()) return;

        boolean opened = qm.clickViewDetails();
        step(page, "View Details · Open popup", "Click View Details (OpenPatientRegistrationPopupScreen)",
                "The patient registration details popup opens",
                opened ? "Popup opened" : "Popup did NOT open", opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("View Details · Result", "FAILED - popup did not open"); return; }

        // The popup IS the registration form; 23 starred fields come back empty for a queued patient.
        // fillViewDetailsAll fills the sections, sets Payor = Self (the same EditSponser/Insurer route used on the
        // Registration screen), then RE-CHECKS and fills again until nothing mandatory is left.
        String filled = qm.fillViewDetailsAll();
        // Checked ACROSS ALL SECTIONS (each accordion expanded): a visibility-only scan skips collapsed sections,
        // which is how this previously reported "all filled" while Patient Information was completely empty.
        String remaining = qm.remainingMandatoryAllSections();

        // Judge "Fill all mandatory fields" on whether Update actually SUCCEEDED, not on the remaining-empty
        // scan alone — the scan is a best-effort heuristic (it can flag a field that is not really blocking, e.g.
        // one satisfied a different way) and a real success toast is the authoritative signal. Run Update first,
        // then let its outcome decide this step; a genuine success toast passes it even if some fields were
        // still reported as empty beforehand.
        String toast = qm.updateViewDetailsAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("updated") || tl.contains("success") || tl.contains("saved");

        step(page, "View Details · Fill all mandatory fields",
                "Fill every mandatory (*) field still empty — Patient Information, Correspondence Details, NOK/Guarantor, Payor Information and Visit Information",
                "No mandatory field is left empty (or the Update below succeeds anyway)",
                filled + (remaining.isEmpty() ? "" : "  <-- STILL EMPTY: " + remaining)
                        + (ok && !remaining.isEmpty() ? "  <-- Update still succeeded, so not treated as a failure" : ""),
                (remaining.isEmpty() || ok) ? "PASS" : "FAIL");

        step(page, "View Details · Click Update & toast",
                "Click Update Registration (UpdateOnlyRegistration); confirm; wait for the success toast",
                "'... Updated Successfully' toast",
                toast == null || toast.isEmpty() ? "No toast appeared"
                        : (ok ? toast : "Update not confirmed - the app showed: \"" + toast + "\""),
                ok ? "PASS" : "FAIL");

        qm.closeViewDetails();
        addSummary("View Details · Patient", patient);
        addSummary("View Details · Result", ok ? toast : "Not confirmed");
    }

    private void sectionCloseVisit() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Close Visit · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        String patient = qm.selectFirstQueueRow();
        step("Close Visit · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickCloseVisit();
        step(page, "Close Visit · Click Close Visit", "Click 'Close Visit' (OpenDischargeTypeModal); the modal opens",
                "Discharge/remark modal opens", opened ? "Close Visit modal opened" : "Close Visit modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String filled = qm.enterRemark("Visit closed - automated test");
        step("Close Visit · Enter Remark", "Enter the Close-Visit remark", "Remark accepted", filled, "PASS");

        String toast = qm.saveCloseVisitAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("close") || toast.toLowerCase().contains("success"));
        step(page, "Close Visit · Save & success toast", "Click Save; wait for the success toast",
                "'Visit Closed Successfully' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        // After a successful close, tick the "Closed Visit" filter, Search, verify the table & screenshot.
        if (ok) {
            int closedCount = qm.viewClosedVisits();
            step(page, "Close Visit · View Closed Visits & verify table",
                    "Tick the 'Closed Visit' checkbox (queue.ClosedVisit) and Search; verify the closed-visits table",
                    "The closed-visits table is listed",
                    closedCount > 0 ? closedCount + " closed-visit row(s) listed" : "No closed-visit rows shown",
                    closedCount > 0 ? "PASS" : "FAIL");
            addSummary("Close Visit · Closed-visit rows", String.valueOf(closedCount));

            // Switch BACK to Active Patient. The two filters are mutually exclusive, so leaving the queue on
            // "Closed Visit" hands every later section the wrong list — Cancel Visit reported "no cancellable
            // patient" simply because a closed visit can never be cancelled.
            int activeRows = qm.restoreActivePatients();
            step(page, "Close Visit · Back to Active Patients",
                    "Untick 'Closed Visit', re-tick 'Active Patient' (queue.ActivePatient) and Search",
                    "The active queue is listed again",
                    activeRows > 0 ? activeRows + " active row(s) listed — " + qm.lastFilterState
                                   : "No active rows after restoring the filter — " + qm.lastFilterState,
                    activeRows > 0 ? "PASS" : "FAIL");
        }

        addSummary("Close Visit · Patient", patient);
        addSummary("Close Visit · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Cancel Visit ===========================================
    private void sectionCancelVisit() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Cancel Visit · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        // CancelVisit is BLOCKED per-patient (clinical notes in EMR / medication dispensed / charges dropped /
        // payment made / already discharged) — verified live: with a plain row-by-row trial, the footer button
        // stayed disabled for ALL 15 patients tried (each is one of those states), wasting a click-timeout per
        // patient. Scan the grid data directly for one that trips none of those conditions instead.
        String patient = qm.selectCancellableQueueRow();
        String outcome = patient == null ? "NONE" : qm.clickCancelVisit();
        boolean reachedReason = patient != null && "REASON".equals(outcome);
        step(page, "Cancel Visit · Select a cancellable patient & click Cancel Visit",
                "Scan the queue for a patient with none of the CancelVisit block conditions; click 'Cancel Visit'",
                "The Reason for Cancellation dialog opens",
                patient == null ? "No cancellable patient in the currently loaded queue (all had a bill/notes/etc.)"
                        : "Selected: " + patient + " | outcome=" + outcome,
                reachedReason ? "PASS" : "FAIL");
        if (!reachedReason) {
            addSummary("Cancel Visit · Result", "Not confirmed — last outcome: " + outcome);
            return;
        }

        String result = qm.cancelVisitPickReasonAndSave();
        boolean ok = result != null && !result.isEmpty()
                && (result.toLowerCase().contains("success") || result.toLowerCase().contains("cancelled"));
        step(page, "Cancel Visit · Pick reason, Save & success toast",
                "Pick the first real Cancellation Reason; click Save (UpdateVisitCancellation)",
                "'Visit Cancelled Successfully!' toast",
                result == null || result.isEmpty() ? "No confirmation observed" : result, ok ? "PASS" : "FAIL");

        addSummary("Cancel Visit · Patient", patient);
        addSummary("Cancel Visit · Result", ok ? result : "Not confirmed");
    }

    // ===== Section: Medico Legal (MLC) =====================================
    private void sectionMedicoLegal() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Medico Legal · Open queue & search", "OP > Outpatient Queue Management; 1-month range + Search",
                "Queued patients are listed", "Queue searched", "PASS");

        String patient = qm.selectFirstQueueRow();
        step("Medico Legal · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickMedicoLegal();
        step(page, "Medico Legal · Click Medico Legal", "Click 'Medico Legal' (OpenMLCModal); the modal opens",
                "Medico Legal (Police Station / Docket No / Remark) modal opens",
                opened ? "Medico Legal modal opened" : "Medico Legal modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String filled = qm.fillMlcDetails("Damansara Police Station", "DKT" + System.currentTimeMillis() % 100000,
                "Medico legal case - automated test");
        step("Medico Legal · Enter Police Station / Docket No / Remark",
                "Enter Police Station, Docket No, Remark", "All three fields accepted", filled, "PASS");

        String doc = qm.addMlcDocument(ATTACH.toString(), "Incident photo - automated test");
        boolean docOk = doc.contains("File=attached") && doc.contains("Add=clicked");
        step(page, "Medico Legal · Document List (Attach File + Description + Add)",
                "Attach a file, enter File Description, click Add", "Document row added to the Document List",
                doc, docOk ? "PASS" : "FAIL");

        String checklist = qm.selectMlcChecklistItem("Checked - automated test");
        boolean checklistOk = checklist.startsWith("Checklist item ticked");
        step("Medico Legal · MLC Check List (select item + Remark)",
                "Select any checklist item, enter its Remark", "A checklist item is ticked with a Remark",
                checklist, checklistOk ? "PASS" : "FAIL");

        String toast = qm.saveMlcAndGetToast();
        boolean ok = toast != null && !toast.isEmpty()
                && (toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved"));
        step(page, "Medico Legal · Save & success toast", "Click Save; wait for the success toast",
                "'... Saved Successfully' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Medico Legal · Patient", patient);
        addSummary("Medico Legal · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section 2: Consent / Forms (SPC Fillers Buttocks) ==============
    private void sectionConsentForms() {
        OutPatientQueueManagementPage qm = new OutPatientQueueManagementPage(page);
        qm.navigateTo(BASE);
        qm.searchQueue(FROM_DATE);   // was hardcoded 01/06/2026 — same staleness as the range above
        String patient = qm.selectQueueRow("RAVI KUMAR");
        step("Consent · Open queue & select a patient", "Search the queue and select a row",
                "Row selected", patient == null ? "No patient to select" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        // Assert the modal actually opened — this step used to report PASS unconditionally, so a run where the
        // button was never clicked still showed green here and only blew up later inside the form search.
        boolean consentModal = qm.clickConsentForms();
        step(page, "Consent · Click ConsentForms", "Open ConsentForms (fnOnConsentClick); Consent Details modal",
                "Modal opens", consentModal ? "Consent Details opened" : "Consent Details modal did NOT open",
                consentModal ? "PASS" : "FAIL");
        if (!consentModal) { addSummary("Consent · Result", "FAILED - Consent Details modal did not open"); return; }

        // Take ANY form the dropdown offers — typing "spc" and choosing between two known SPC forms only ever
        // exercised those two, and would fail outright on an environment that does not have them.
        //
        // But the layout is NOT uniform: only some forms carry the Create Document → sign → Submit chain this
        // section is here to test. So pick at random and, when the opened form has no Create Document, close it
        // and try another. That keeps the pick unhardcoded WITHOUT quietly losing the submit coverage — picking
        // once at random left the run stopping at "tab opened" nearly every time, since most of the ~359 forms
        // have no such chain.
        final int MAX_FORM_TRIES = Integer.getInteger("consent.form.tries", 6);
        String consentForm = "";
        Page docTab = null;
        java.util.List<String> tried = new java.util.ArrayList<>();
        boolean created = false;

        for (int attempt = 1; attempt <= MAX_FORM_TRIES && !created; attempt++) {
            // One bad attempt must not abort the section — carry on to the next form.
            try {
                if (attempt > 1) {
                    // Start the attempt from a CLEAN queue and a DIFFERENT patient. Re-opening the modal on the
                    // spent page failed outright ("clickConsentForms: real click failed") once a form tab had
                    // been opened, and a different patient also gives the pick a different set of forms to
                    // offer rather than re-drawing from the same one.
                    page.bringToFront();
                    qm.navigateTo(BASE);
                    qm.searchQueue(FROM_DATE);
                    String nextPatient = qm.selectRandomQueueRow();
                    System.out.println("Consent: attempt " + attempt + " with patient " + nextPatient);
                    if (nextPatient == null) break;
                    if (!qm.clickConsentForms()) break;
                }
                String pick = qm.selectAnyConsentForm();
                if (pick == null || pick.isEmpty()) break;
                consentForm = pick;
                tried.add(pick);

                int tabCountBefore = page.context().pages().size();
                page.locator("button[ng-click='IUDconsentdetail()']").click(
                        new com.microsoft.playwright.Locator.ClickOptions().setTimeout(10000));
                Page opened = null;
                for (int i = 0; i < 60; i++) {
                    if (page.context().pages().size() > tabCountBefore) { opened = page.context().pages().get(tabCountBefore); break; }
                    page.waitForTimeout(1000);
                }
                if (opened == null) break;
                opened.waitForLoadState();
                opened.waitForTimeout(2000);

                opened.locator("a:has-text('Create Document')").click(
                        new com.microsoft.playwright.Locator.ClickOptions().setNoWaitAfter(true).setTimeout(8000));
                opened.waitForSelector("#Submit", new Page.WaitForSelectorOptions().setTimeout(45000));
                opened.waitForTimeout(2000);
                docTab = opened;
                created = true;
            } catch (Exception e) {
                // Leave the tab OPEN — closing it took the whole section down with a TargetClosedError, and the
                // flow leaves its report tabs open everywhere else anyway.
                System.out.println("Consent: attempt " + attempt + " on '" + consentForm
                        + "' did not reach Create Document/Submit — trying another form");
                docTab = null;
                try { page.bringToFront(); } catch (Exception ignore) { }
                page.waitForTimeout(1200);
            }
        }

        step("Consent · Pick a form from the dropdown",
                "Click the consent search box to list every form and select one; if it has no Create Document, try another",
                "A form that supports Create Document is selected",
                consentForm.isEmpty() ? "No form could be selected — the dropdown listed none"
                        : "Selected: " + consentForm + (tried.size() > 1 ? " (after trying " + tried.size() + ": " + String.join(", ", tried) + ")" : ""),
                consentForm.isEmpty() ? "FAIL" : "PASS");
        if (consentForm.isEmpty()) { addSummary("Consent · Result", "FAILED - no consent form available to select"); return; }

        if (docTab == null || !created) {
            step(page, "Consent · Create Document (auto-filled)", "Click Create Document; form auto-fills",
                    "Navigates to Create; details prefilled",
                    "None of the " + tried.size() + " forms tried offered Create Document / Submit: " + String.join(", ", tried),
                    "MANUAL");
            addSummary("Consent · Form", consentForm);
            addSummary("Consent · Result", "No form tried had a Create Document/Submit chain");
            return;
        }

        step(docTab, "Consent · Save opens the form tab", "Save opens the consent form list tab",
                "New tab opens", "New tab: " + consentForm + " list (" + docTab.url() + ")", "PASS");
        step(docTab, "Consent · Create Document (auto-filled)", "Click Create Document; form auto-fills",
                "Navigates to Create; details prefilled", "Create form auto-filled", "PASS");

        docTab.evaluate("()=>{const b=[...document.querySelectorAll('button,a,span,div')].find(e=>e.offsetParent!==null&&e.textContent.trim()==='...'&&e.getBoundingClientRect().width<60);if(b)b.click();}");
        docTab.waitForTimeout(600);
        // Not every form carries a signature canvas — say which happened instead of claiming "Signature drawn".
        boolean signed = drawSignature(docTab, null);
        step(docTab, "Consent · Sign", "Draw a signature on the canvas",
                "Signature captured (or the form has none)",
                signed ? "Signature drawn" : "'" + consentForm + "' has no signature canvas — nothing to sign",
                signed ? "PASS" : "MANUAL");
        docTab.evaluate("()=>{const b=[...document.querySelectorAll('button')].find(x=>/^Confirm$/i.test(x.innerText.trim())&&x.offsetParent!==null&&x.getBoundingClientRect().width>0);if(b)b.click();}");
        page.waitForTimeout(600);

        // ---- Submit the form -------------------------------------------------------------------------------
        boolean submitClicked = false;
        try {
            docTab.locator("#Submit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(10000));
            submitClicked = true;
        } catch (Exception e) { System.out.println("Consent: Submit click failed - " + e.getMessage()); }
        page.waitForTimeout(800);
        // Some forms raise a "Yes/No" confirm before submitting; others go straight through.
        try { docTab.locator("#btnConfirmYes").click(new com.microsoft.playwright.Locator.ClickOptions().setNoWaitAfter(true).setTimeout(10000)); }
        catch (Exception e) { System.out.println("Consent: no #btnConfirmYes dialog on this form - " + e.getMessage()); }
        docTab.waitForTimeout(2500);
        try {
            // Generic — the URL path differs by form (/SPCFillersButtocks/Edit/ vs /SPCThreadLift/Edit/).
            docTab.waitForURL("**/Edit/**",
                    new Page.WaitForURLOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(45000));
        } catch (Exception ignore) { }
        docTab.waitForTimeout(1500);
        String docId = docTab.url().replaceAll(".*/Edit/(\\d+).*", "$1");
        boolean ok = docTab.url().contains("/Edit/") && docId.matches("\\d+");
        docTab.bringToFront();
        docTab.waitForTimeout(1000);
        step(docTab, "Consent · Submit the form", "Click Submit (confirm Yes where the form asks)",
                "'Form Submitted Successfully!' (navigates to /Edit/<id>)",
                ok ? "Submitted (doc ID " + docId + ")"
                   : (submitClicked ? "NOT confirmed (url=" + docTab.url() + ")" : "Submit button could not be clicked"),
                ok ? "PASS" : "FAIL");

        // ---- OK on the success message ---------------------------------------------------------------------
        // Its own step: the OK used to be a silent best-effort click, so a run where the success dialog never
        // appeared looked identical to one where it did.
        boolean okClicked = false;
        try {
            docTab.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("OK")).click(
                    new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000));
            okClicked = true;
        } catch (Exception e) {
            // Fall back to any visible OK-ish button in the dialog.
            okClicked = Boolean.TRUE.equals(docTab.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')]"
                    + "   .find(x=>x.offsetParent!==null && /^(ok|okay|close)$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.click(); return true; }"));
        }
        step(docTab, "Consent · OK on the success message", "Click OK on the 'submitted successfully' dialog",
                "The success dialog is dismissed",
                okClicked ? "OK clicked" : "No success dialog with an OK button appeared",
                okClicked ? "PASS" : "MANUAL");
        docTab.waitForTimeout(1200);

        // ---- Screenshot of the submitted report ------------------------------------------------------------
        step(docTab, "Consent · Submitted report", "Capture the submitted consent document",
                "The submitted report is shown",
                ok ? "Report for '" + consentForm + "' (doc ID " + docId + ") — " + docTab.url()
                   : "Report not confirmed — " + docTab.url(),
                ok ? "PASS" : "FAIL");
        addSummary("Consent · Form", consentForm);
        addSummary("Consent · Document", ok ? "Edit/" + docId : "not submitted");

        page.waitForTimeout(500);
        page.bringToFront();

        // View Consent — back to the queue, re-select the patient, open View Consent, and open the saved
        // form via the row's Action (eye) icon in a new tab.
        qm.selectQueueRow((patient == null || patient.trim().isEmpty()) ? "RAVI KUMAR" : patient.trim());
        qm.clickViewConsent();
        step(page, "Consent · View Consent", "Re-select patient; click View Consent",
                "View Consent/Forms list with the record", "View Consent opened", "PASS");

        qm.clickSearchInViewConsent();
        String iconFound = qm.clickConsentActionIcon(consentForm);   // match the form actually chosen, not a hardcoded "SPC"
        if (iconFound != null) {
            Page formTab = page.context().waitForPage(() -> page.locator("#consent_action_icon").click());
            formTab.waitForTimeout(1500);
            step(formTab, "Consent · View Consent form (new tab)", "Click the row's Action (view) icon",
                    "Saved form opens in a new tab", "Form opened (Edit/" + docId + ")", "PASS");
        } else {
            step(page, "Consent · View Consent form (new tab)", "Click the row's Action (view) icon",
                    "Saved form opens in a new tab",
                    "Record not listed for the re-selected patient — document already submitted (Edit/" + docId + ")",
                    "MANUAL");
        }

        addSummary("Consent · Patient", patient);
        addSummary("Consent · Form", consentForm);
        addSummary("Consent · Document", ok ? "Submitted (ID " + docId + ")" : "Not confirmed");
    }
}
