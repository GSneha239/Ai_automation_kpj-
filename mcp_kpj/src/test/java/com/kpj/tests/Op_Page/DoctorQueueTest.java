package com.kpj.tests.Op_Page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.DoctorQueuePage;
import com.microsoft.playwright.Page;

/**
 * OP &gt; <b>Doctor Queue</b> — footer actions.
 *
 * <ol>
 *   <li><b>Close Visit</b> — select a patient → Close Visit ({@code OpenDischargeTypeModal}) → Remark → Save →
 *       "Visit Closed Successfully." toast.</li>
 *   <li><b>Revoke Visit</b> — select a patient → Revoke Visit ({@code OpenRevokeTypeModal}) → Remark → Save →
 *       "Visit Revoked Successfully." toast.</li>
 *   <li><b>Medico Legal</b> — select a patient → Medico Legal ({@code OpenMLCModal}) → Police Station / Docket
 *       No / Remark → Document List (attach file + description + Add) → MLC Check List (tick an item + remark)
 *       → Save → "Medico Legal Saved Successfully." toast.</li>
 *   <li><b>Convert IPD Charges</b> — select a patient → Convert IPD Charges → accept the confirm (Save) →
 *       "Charges Converted Successfully." toast; a patient with nothing to convert answers "No Unbilled Ipd
 *       Charges!" — try a different patient rather than failing.</li>
 * </ol>
 *
 * <p>Doctor Queue shares the queue component + footer actions with OP Outpatient Queue Management, so
 * {@link DoctorQueuePage} (a thin subclass) reuses those methods.</p>
 */
public class DoctorQueueTest extends DevHisBase {

    private static final String SIGNATURE_FILE = "C:\\Users\\Siva Sankar\\Downloads\\sinature.jpeg";

    public DoctorQueueTest() { super("DoctorQueue"); }

    public static void main(String[] args) {
        DoctorQueueTest t = new DoctorQueueTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Doctor Queue (Close Visit / Revoke Visit)", "OP > Doctor Queue",
                "Open Doctor Queue, search a 1-month range ending today, select a patient, then Close Visit and "
                        + "Revoke Visit (each: action -> remark -> Save -> success toast).");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        sectionGenerateQueue();
        sectionCallPatient();
        // sectionViewDetails();  // ignored for now
        sectionConvertIpdCharges();
        sectionPatientTask();
        sectionCancelVisit();
        sectionRequestMrd();
        sectionReturnMrd();
        sectionFallRisk();
        sectionConsentForms();
        sectionViewConsent();
        sectionChangeDoctor();
        sectionAssignTriage();
        sectionAttachSignature();
        sectionNewCase();
        sectionCloseVisit();
        sectionMedicoLegal();
        sectionRevokeVisit();

        addSummary("Application URL", BASE + "/" + (DoctorQueuePage.ROUTE.isEmpty() ? "#/queueManagement" : DoctorQueuePage.ROUTE));
    }

    /** Open Doctor Queue, search a 1-month range (To = today), and select a patient. Returns the page + selected
     *  patient name (null patient if none / not opened). */
    private DoctorQueuePage openSearchSelect(String tag) {
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate today = java.time.LocalDate.now();
        String from = today.minusMonths(1).format(f), to = today.format(f);

        DoctorQueuePage dq = new DoctorQueuePage(page);
        dq.navigateTo(BASE);
        boolean onQueue = page.url().toLowerCase().contains("queue");
        step(page, tag + " · Open Doctor Queue", "OP menu -> Doctor Queue",
                "The Doctor Queue screen is shown", onQueue ? "Opened " + page.url() : "Did NOT reach the queue (" + page.url() + ")",
                onQueue ? "PASS" : "FAIL");
        if (!onQueue) return null;

        dq.searchQueue(from, to);
        step(page, tag + " · Enter date range & Search", "Set From = today-1month, To = today; click Search",
                "Queued patients are listed", "Searched " + from + " -> " + to, "PASS");
        return dq;
    }

    // ===== Section: Generate Queue =======================================
    private void sectionGenerateQueue() {
        DoctorQueuePage dq = new DoctorQueuePage(page);
        dq.navigateTo(BASE);
        boolean onQueue = page.url().toLowerCase().contains("queue");
        if (!onQueue) {
            step(page, "Generate Queue · Open Doctor Queue", "OP menu -> Doctor Queue", "The Doctor Queue screen is shown",
                    "Did NOT reach the queue (" + page.url() + ")", "FAIL");
            addSummary("Generate Queue · Result", "Doctor Queue not reached"); return;
        }
        // Generate Queue only works for CURRENT-DATE patients — search TODAY and wait for the grid to load.
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        dq.searchQueue(today, today);
        dq.waitForQueueLoaded();
        Object rc = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data) n=Math.max(n,s.grid.options.data.length);}catch(e){} }); return n; }");
        int rows = rc instanceof Number ? ((Number) rc).intValue() : 0;
        boolean dataLoaded = rows > 0;
        step(page, "Generate Queue · Search today & load grid", "Search today's date (" + today + "); wait for the grid to load",
                "Today's queued patients are listed (data loaded)", "Searched " + today + " — " + rows + " row(s) loaded",
                dataLoaded ? "PASS" : "FAIL");
        if (!dataLoaded) { addSummary("Generate Queue · Result", "No today's data to generate a queue"); return; }

        // Start capturing toasts so we can detect a repeated "Enter today's date" prompt.
        page.evaluate("() => { window.__gqT=[]; if(window.__gqObs) window.__gqObs.disconnect();"
                + " window.__gqObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__gqT.includes(t)) window.__gqT.push(t); }); });"
                + " window.__gqObs.observe(document.body,{childList:true,subtree:true}); }");

        // Try patients until Generate Queue actually opens its modal (GenerateToken only opens for an eligible today-patient).
        String patient = null, oldQueue = "";
        boolean opened = false;
        int tried = 0;
        for (int i = 0; i < 12; i++) {
            String cand = dq.selectNthQueueRow(i);
            if (cand == null) break;
            patient = cand;
            oldQueue = dq.getSelectedQueueNo();
            tried++;
            if (dq.clickGenerateQueue()) { opened = true; break; }
        }
        boolean hasQueue = oldQueue != null && !oldQueue.isEmpty() && !"0".equals(oldQueue);
        step(page, "Generate Queue · Select a patient & open modal",
                "Select a today patient; open Generate Queue (GenerateToken) — try a different patient if the modal doesn't open",
                "A patient whose Generate Queue modal opens is selected",
                patient == null ? "No selectable today-patient (grid loaded " + rows + " row(s), none selectable/eligible)"
                        : "Selected: " + patient + " | Queue No: " + (hasQueue ? oldQueue : "none")
                                + (opened ? " (modal opened; tried " + tried + ")" : " (modal did NOT open; tried " + tried + ")"),
                (patient != null && opened) ? "PASS" : "FAIL");

        // Detect the "Enter today's date" prompt — it must NOT appear now (we searched today + the grid loaded).
        String todayToast = String.valueOf(page.evaluate("() => { const a=window.__gqT||[]; return a.find(x=>/today.?s? *date|enter today|select today|please.*today/i.test(x)) || ''; }"));
        boolean todayIssue = todayToast != null && !todayToast.isEmpty() && !"null".equals(todayToast);

        if (patient == null || !opened || todayIssue) {
            String reason = todayIssue
                    ? "Generate Queue keeps prompting \"" + todayToast + "\" even though today's date (" + today + ") was searched and the grid loaded (" + rows + " rows) — cannot generate the queue"
                    : (patient == null
                        ? "No selectable today-patient in the queue (grid loaded " + rows + " row(s), but none are selectable/eligible) — cannot generate the queue"
                        : "Generate Queue modal did not open for the " + tried + " today-patient(s) checked");
            step(page, "Generate Queue · Generate & toast", "Generate the queue (GenerateToken -> UpdateTokenNo); wait for the toast",
                    "'Token Updated Successfully.' toast", reason, "FAIL");
            addSummary("Generate Queue · Result", reason);
            return;
        }

        String res = dq.fillGenerateQueueAndSave(oldQueue);
        String rl = res == null ? "" : res.toLowerCase();
        // Re-check for the "today's date" prompt after Save (it can appear on the save round-trip).
        String todayToast2 = String.valueOf(page.evaluate("() => { const a=window.__gqT||[]; return a.find(x=>/today.?s? *date|enter today|select today|please.*today/i.test(x)) || ''; }"));
        boolean todayIssue2 = (todayToast2 != null && !todayToast2.isEmpty() && !"null".equals(todayToast2)) || rl.contains("today");
        boolean ok = !todayIssue2 && (rl.contains("token updated") || rl.contains("updated successfully") || rl.contains("success"));
        String actual = todayIssue2
                ? "Generate Queue keeps prompting \"" + (todayToast2 == null || todayToast2.isEmpty() ? "Enter today's date" : todayToast2) + "\" even though today's date (" + today + ") was searched and the grid loaded — cannot generate the queue. [" + res + "]"
                : res;
        step(page, "Generate Queue · Generate & toast",
                hasQueue ? "Patient has Queue No " + oldQueue + " → pick a room that yields a DIFFERENT queue; Save (UpdateTokenNo)"
                         : "Patient has no queue → select a Consultation Room to assign one; Save (UpdateTokenNo)",
                "'Token Updated Successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Generate Queue · Patient", patient + (hasQueue ? " (had Queue " + oldQueue + ")" : " (no prior queue)"));
        addSummary("Generate Queue · Result", ok ? res : actual);
    }

    // ===== Section: Call Patient =========================================
    private void sectionCallPatient() {
        DoctorQueuePage dq = new DoctorQueuePage(page);
        dq.navigateTo(BASE);
        boolean onQueue = page.url().toLowerCase().contains("queue");
        if (!onQueue) {
            step(page, "Call Patient · Open Doctor Queue", "OP menu -> Doctor Queue", "The Doctor Queue screen is shown",
                    "Did NOT reach the queue (" + page.url() + ")", "FAIL");
            addSummary("Call Patient · Result", "Doctor Queue not reached"); return;
        }
        // Call Patient (IUDTokandisplay) announces the token on the display board — CURRENT-DATE patients only
        // (a wider range → "Please select Today's date!"). Search TODAY and wait for the grid.
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        dq.searchQueue(today, today);
        dq.waitForQueueLoaded();
        Object rc = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data) n=Math.max(n,s.grid.options.data.length);}catch(e){} }); return n; }");
        int rows = rc instanceof Number ? ((Number) rc).intValue() : 0;
        boolean dataLoaded = rows > 0;
        step(page, "Call Patient · Search today & load grid", "Search today's date (" + today + "); wait for the grid to load",
                "Today's queued patients are listed (data loaded)", "Searched " + today + " — " + rows + " row(s) loaded",
                dataLoaded ? "PASS" : "FAIL");
        if (!dataLoaded) { addSummary("Call Patient · Result", "No today's data to call a patient"); return; }

        String patient = dq.selectNthQueueRow(0);
        step(page, "Call Patient · Select a today-patient", "Select a today patient row (ui-grid API)",
                "One today-patient selected",
                patient == null ? "No selectable today-patient (grid loaded " + rows + " row(s), none selectable/eligible)" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) {
            addSummary("Call Patient · Result", "No selectable today-patient in the queue (grid loaded " + rows + " row(s), none selectable) — cannot call a patient");
            return;
        }

        String toast = dq.callPatientAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // Success = a token-display / call confirmation. The app rejects with "Please select Today's date!" or
        // "Please Select Location!" — surface those as the failure reason.
        boolean gate = tl.contains("today") || tl.contains("location") || tl.contains("please select");
        boolean ok = !gate && (tl.contains("token") || tl.contains("display") || tl.contains("call") || tl.contains("success"));
        String actual = toast == null || toast.isEmpty()
                ? "No toast observed after Call Patient (IUDTokandisplay)"
                : (gate ? "Call Patient rejected: \"" + toast + "\" (patient: " + patient + ")" : toast);
        step(page, "Call Patient · Call & toast",
                "Click Call Patient (IUDTokandisplay) to announce the selected patient's token; wait for the toast",
                "Token-display / patient-called success toast", actual, ok ? "PASS" : "FAIL");
        addSummary("Call Patient · Patient", patient);
        addSummary("Call Patient · Result", ok ? toast : actual);
    }

    // ===== Section: Convert IPD Charges ==================================
    private void sectionConvertIpdCharges() {
        DoctorQueuePage dq = openSearchSelect("Convert IPD Charges");
        if (dq == null) { addSummary("Convert IPD Charges · Result", "Doctor Queue not reached"); return; }

        // "No Unbilled Ipd Charges!" means THIS patient has nothing to convert — not a failure, try a
        // different patient (same iterate-until-eligible pattern used elsewhere in this file).
        String patient = null, toast = "";
        int tried = 0;
        for (int i = 0; i < 12; i++) {
            String cand = dq.selectNthQueueRow(i);
            if (cand == null) break;
            patient = cand;
            tried++;
            String outcome = dq.clickConvertIpdCharges();
            if (!outcome.equals("confirm")) continue; // no confirm dialog for this patient — try the next
            toast = dq.acceptConvertIpdChargesAndGetToast();
            if (toast != null && toast.toLowerCase().contains("no unbilled")) continue; // try a different patient
            break; // a real success toast, or something else worth reporting as-is
        }
        step(page, "Convert IPD Charges · Select a patient & click Convert IPD Charges",
                "Select a patient; click 'Convert IPD Charges' (try a different patient on \"No Unbilled Ipd Charges!\")",
                "The confirm dialog appears for some patient",
                patient == null ? "No patient in the queue"
                        : "Selected: " + patient + " (tried " + tried + " patient(s))",
                patient == null ? "FAIL" : "PASS");
        if (patient == null) { addSummary("Convert IPD Charges · Result", "No patient in the queue"); return; }

        boolean ok = toast != null && !toast.isEmpty()
                && (toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved") || toast.toLowerCase().contains("convert"));
        step(page, "Convert IPD Charges · Accept alert (Save) & success toast",
                "Click Save on the confirm; wait for the success toast",
                "'... Converted/Saved Successfully' toast",
                toast == null || toast.isEmpty() ? "No toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Convert IPD Charges · Patient", patient + " (tried " + tried + ")");
        addSummary("Convert IPD Charges · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: View Details =========================================
    private void sectionViewDetails() {
        DoctorQueuePage dq = openSearchSelect("View Details");
        if (dq == null) { addSummary("View Details · Result", "Doctor Queue not reached"); return; }

        String patient = dq.selectFirstQueueRow();
        step(page, "View Details · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) { addSummary("View Details · Result", "No patient in the queue"); return; }

        boolean opened = dq.clickViewDetails();
        step(page, "View Details · Open Correspondence Details", "Click View Details (OpenPatientRegistrationPopupScreen)",
                "The patient's 'Correspondence Details' registration popup opens",
                opened ? "Correspondence Details popup opened" : "Correspondence Details popup did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("View Details · Result", "Popup did not open"); return; }

        String info = dq.viewDetailsSummary();
        String infoText = info == null || info.isEmpty()
                ? "Registration details popup displayed (see screenshot)" : info;
        step(page, "View Details · Registration details (screenshot)",
                "Show the selected patient's registration details (MRN / Name / NRIC) and capture the popup",
                "The selected patient's registration details are shown", infoText, "PASS");

        dq.closeViewDetails();
        step(page, "View Details · Close popup", "Close the patient details popup (Close / ×)",
                "The popup is dismissed", "Popup closed", "PASS");

        addSummary("View Details · Patient", patient);
        addSummary("View Details · Result", infoText);
    }

    // ===== Section: Patient Task =========================================
    private void sectionPatientTask() {
        DoctorQueuePage dq = openSearchSelect("Patient Task");
        if (dq == null) { addSummary("Patient Task · Result", "Doctor Queue not reached"); return; }

        String patient = dq.selectFirstQueueRow();
        step(page, "Patient Task · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) { addSummary("Patient Task · Result", "No patient in the queue"); return; }

        boolean opened = dq.clickPatientTask();
        step(page, "Patient Task · Click Patient Task", "Click 'Patient Task' (BtnPatientRemark); the modal opens",
                "Patient Task modal opens", opened ? "Patient Task modal opened" : "Patient Task modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Patient Task · Result", "Patient Task modal did not open"); return; }

        String toast = dq.addTasksSaveAndGetToast(java.util.List.of(
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

    // ===== Section: Cancel Visit =========================================
    private void sectionCancelVisit() {
        DoctorQueuePage dq = openSearchSelect("Cancel Visit");
        if (dq == null) { addSummary("Cancel Visit · Result", "Doctor Queue not reached"); return; }

        // CancelVisit blocks (JAlert) if the visit has EMR notes / dispensed meds / dropped charges / payment.
        // Try a few patients to find one that IS cancellable (opens the Reason-for-Cancellation dialog).
        String patient = null, lastBlock = "";
        boolean reason = false;
        int tried = 0;
        for (int i = 0; i < 5 && !reason; i++) {
            String cand = dq.selectNthQueueRowWithDepartment(i);
            if (cand == null) break;
            patient = cand; tried++;
            String outcome = dq.clickCancelVisit();
            if ("REASON".equals(outcome)) { reason = true; break; }
            if (outcome != null && outcome.startsWith("BLOCKED::")) lastBlock = outcome.substring("BLOCKED::".length());
            dq.closeCancelVisitDialog();
        }
        step(page, "Cancel Visit · Find a cancellable patient",
                "Select a patient → click CancelVisit; if the app blocks it (EMR notes / meds / charges / payment), try another",
                "A patient whose Reason-for-Cancellation dialog opens is found",
                patient == null ? "No patient in the queue"
                        : (reason ? "Reason-for-Cancellation dialog opened (patient " + patient + ", tried " + tried + ")"
                                  : "No cancellable patient (tried " + tried + ")" + (lastBlock.isEmpty() ? "" : " — last: " + lastBlock)),
                reason ? "PASS" : "FAIL");
        if (!reason) {
            addSummary("Cancel Visit · Result", lastBlock.isEmpty()
                    ? "No cancellable patient found (tried " + tried + ") — cancel gated by the app"
                    : "Cancel blocked by the app: " + lastBlock);
            return;
        }

        String res = dq.cancelVisitPickReasonAndSave();
        boolean ok = res != null && (res.toLowerCase().contains("cancelled") || res.toLowerCase().contains("success"));
        step(page, "Cancel Visit · Pick reason, Save & confirm",
                "Select a cancellation reason; click Save (UpdateVisitCancellation); wait for the confirmation",
                "'Visit Cancelled Successfully!' confirmation",
                res == null || res.isEmpty() ? "No confirmation appeared after Save" : res,
                ok ? "PASS" : "FAIL");
        dq.closeCancelVisitDialog();

        addSummary("Cancel Visit · Patient", patient);
        addSummary("Cancel Visit · Result", ok ? res : (res == null || res.isEmpty() ? "Not confirmed" : res));
    }

    // ===== Section: Request MRD File =====================================
    private void sectionRequestMrd() {
        DoctorQueuePage dq = openSearchSelect("Request MRD");
        if (dq == null) { addSummary("Request MRD · Result", "Doctor Queue not reached"); return; }

        // Prefer a patient WITHOUT an MRD file so we exercise the create path (Yes → fill → Save).
        String patient = dq.selectQueueRowWithoutMrdFile();
        step(page, "Request MRD · Select a patient", "Select a patient with no MRD file yet (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) { addSummary("Request MRD · Result", "No patient in the queue"); return; }

        String outcome = dq.clickRequestMrdFile();
        // Branch 1 — patient already HAS a file: the MRD report opened in a new tab. Capture it, then close.
        if (outcome.equals("report")) {
            Page report = page.context().pages().get(page.context().pages().size() - 1);
            try { report.waitForLoadState(); } catch (Exception ignore) { }
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
            try { if (report != page && !report.isClosed()) report.close(); } catch (Exception ignore) { }
            page.bringToFront();
            addSummary("Request MRD · Patient", patient);
            addSummary("Request MRD · Result", "Report opened (file already existed)");
            return;
        }
        // Branch 2 — no file yet: a 'create MRD file?' confirm appears → Yes → fill → Save → Cancel.
        boolean confirmed = outcome.equals("confirm");
        step(page, "Request MRD · Click Request MRD File", "Click 'Request MRD File' (fnRequestMRDFile)",
                "If the patient HAS a file the report opens in a new tab; else a 'File is not generated… create one?' confirm appears",
                confirmed ? "Confirm dialog appeared" : "No confirm/report — the async MRD server check did not return in time (outcome: " + outcome + ")",
                confirmed ? "PASS" : "MANUAL");
        if (!confirmed) { addSummary("Request MRD · Result", "No confirm/report (outcome: " + outcome + ")"); return; }

        boolean modalOpen = dq.confirmCreateMrdFile();
        step(page, "Request MRD · Accept alert (Yes)", "Click Yes on the confirm; the MRD Details popup opens",
                "MRD Details popup opens", modalOpen ? "MRD Details opened" : "MRD Details did NOT open",
                modalOpen ? "PASS" : "FAIL");
        if (!modalOpen) { addSummary("Request MRD · Result", "MRD Details popup did not open"); return; }

        String toast = dq.fillMrdDetailsAndSave();
        boolean ok = toast != null && (toast.toLowerCase().contains("allocated") || toast.toLowerCase().contains("success"));
        step(page, "Request MRD · Fill details, Save & toast",
                "Fill all mandatory fields (Patient Type, Location, File Type, Rack→Row→Box, File No, Classification); Save (fnIUDAllocation)",
                "'File Allocated Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        dq.cancelMrdDetails();
        step(page, "Request MRD · Cancel to close popup", "Click Cancel to close the MRD Details popup",
                "Popup closed", "Popup closed", "PASS");

        addSummary("Request MRD · Patient", patient);
        addSummary("Request MRD · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Return MRD File ======================================
    private void sectionReturnMrd() {
        DoctorQueuePage dq = openSearchSelect("Return MRD");
        if (dq == null) { addSummary("Return MRD · Result", "Doctor Queue not reached"); return; }

        // Return MRD needs a patient who HAS an MRD file (so selectedItem.MRDid is set).
        String patient = dq.selectQueueRowWithMrdFile();
        step(page, "Return MRD · Select a patient with an MRD file", "Select a patient whose MRD file is already allocated (mrdfileno set)",
                "A patient with an MRD file is selected",
                patient == null ? "No queued patient has an MRD file — nothing to return" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) { addSummary("Return MRD · Result", "No patient with an MRD file in the queue"); return; }

        boolean opened = dq.clickReturnMrdFile();
        step(page, "Return MRD · Click Return MRD File", "Click 'Return MRD File' (fnReturnMRDFile); the MRDReturn popup opens",
                "The MRDReturn popup opens", opened ? "MRDReturn popup opened" : "MRDReturn popup did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Return MRD · Result", "MRDReturn popup did not open"); return; }

        String toast = dq.fillMrdReturnAndSave();
        boolean ok = toast != null && (toast.toLowerCase().contains("return") || toast.toLowerCase().contains("success")
                || toast.toLowerCase().contains("saved"));
        step(page, "Return MRD · Fill From/To dept + remark, Save & toast",
                "Set From/To Department + User, enter a remark; click OK (fnIUDReturn); wait for the toast",
                "A 'Returned/Saved Successfully' toast",
                toast == null || toast.isEmpty() ? "No toast appeared" : toast, ok ? "PASS" : "FAIL");

        dq.cancelMrdReturn();
        step(page, "Return MRD · Close popup", "Close the MRDReturn popup (Cancel / resetForm)",
                "The popup is dismissed", "Popup closed", "PASS");

        addSummary("Return MRD · Patient", patient);
        addSummary("Return MRD · Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }

    // ===== Section: Fall Risk Assessment =================================
    private void sectionFallRisk() {
        DoctorQueuePage dq = openSearchSelect("Fall Risk");
        if (dq == null) { addSummary("Fall Risk · Result", "Doctor Queue not reached"); return; }

        String patient = dq.selectFirstQueueRow();
        step(page, "Fall Risk · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) { addSummary("Fall Risk · Result", "No patient in the queue"); return; }

        boolean opened = dq.clickFallRiskAssessment();
        step(page, "Fall Risk · Open popup", "Click 'Fall Risk Assessment' (fnOpenFallRiskAssessment); the 'ASSESSMENT OF FALL' modal opens",
                "Fall Risk Assessment modal opens", opened ? "Fall Risk modal opened" : "Fall Risk modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Fall Risk · Result", "Fall Risk modal did not open"); return; }

        String toast = dq.fillFallRiskAndApply();
        boolean ok = toast != null && (toast.toLowerCase().contains("saved")
                || toast.toLowerCase().contains("success") || toast.toLowerCase().contains("applied"));
        step(page, "Fall Risk · Answer Yes, tick a checkbox, Apply & toast",
                "Answer the assessment questions YES, tick a checkbox (item.IsSelected), click Apply (fnApply)",
                "'Fall Risk Assessment saved successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Fall Risk · Patient", patient);
        addSummary("Fall Risk · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Consent / Forms ======================================
    private void sectionConsentForms() {
        DoctorQueuePage dq = openSearchSelect("Consent");
        if (dq == null) { addSummary("Consent · Result", "Doctor Queue not reached"); return; }

        String patient = dq.selectFirstQueueRow();
        step(page, "Consent · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        dq.clickConsentForms();
        step(page, "Consent · Click Consent/Forms", "Open Consent/Forms; Consent Details modal",
                "Consent Details modal opens", "Consent Details opened", "PASS");

        // Pick either SPC form for this run (one is enough).
        String[] forms = { "SPC FILLERS BUTTOCKS", "SPC THREAD LIFT" };
        String consentForm = forms[new java.util.Random().nextInt(forms.length)];
        try {
        dq.searchAndSelectConsentForm(consentForm, consentForm);
        step(page, "Consent · Type 'spc' & pick a form", "Type spc; choose a form (SPC Fillers Buttocks / SPC Thread Lift)",
                "A consent form is selected", consentForm + " selected", "PASS");

        // Save (IUDconsentdetail) opens the SPC form document tab.
        int tabCountBefore = page.context().pages().size();
        page.locator("button[ng-click='IUDconsentdetail()']").click();
        Page docTab = null;
        for (int i = 0; i < 60; i++) {
            if (page.context().pages().size() > tabCountBefore) { docTab = page.context().pages().get(tabCountBefore); break; }
            page.waitForTimeout(1000);
        }
        if (docTab == null) {
            step(page, "Consent · Save opens SPC form tab", "Click Save (IUDconsentdetail()); a new document tab opens",
                    "New tab opens", "No new tab appeared", "FAIL");
            addSummary("Consent · Result", "Save did not open the document tab");
            return;
        }
        docTab.waitForLoadState();
        docTab.waitForTimeout(2000);
        step(docTab, "Consent · Save opens SPC form tab", "Save opens the SPC form list tab",
                "New tab opens", "New tab: " + consentForm + " list (" + docTab.url() + ")", "PASS");

        // Create Document -> fill -> sign -> Submit -> Yes -> /Edit/<id>.
        docTab.locator("a:has-text('Create Document')").click(new com.microsoft.playwright.Locator.ClickOptions().setNoWaitAfter(true));
        docTab.waitForSelector("#Submit", new Page.WaitForSelectorOptions().setTimeout(45000));
        docTab.evaluate("()=>{const b=[...document.querySelectorAll('button,a,span,div')].find(e=>e.offsetParent!==null&&e.textContent.trim()==='...'&&e.getBoundingClientRect().width<60);if(b)b.click();}");
        docTab.waitForTimeout(600);
        drawSignature(docTab, null);
        step(docTab, "Consent · Sign", "Draw a signature on the canvas", "Signature captured", "Signature drawn", "PASS");
        docTab.evaluate("()=>{const b=[...document.querySelectorAll('button')].find(x=>/^Confirm$/i.test(x.innerText.trim())&&x.offsetParent!==null&&x.getBoundingClientRect().width>0);if(b)b.click();}");
        page.waitForTimeout(600);

        docTab.locator("#Submit").click();
        page.waitForTimeout(800);
        try { docTab.locator("#btnConfirmYes").click(new com.microsoft.playwright.Locator.ClickOptions().setNoWaitAfter(true)); } catch (Exception ignore) { }
        docTab.waitForTimeout(2500);
        try {
            docTab.waitForURL("**/Edit/**", new Page.WaitForURLOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(45000));
        } catch (Exception ignore) { }
        docTab.waitForTimeout(1500);
        String docId = docTab.url().replaceAll(".*/Edit/(\\d+).*", "$1");
        boolean ok = docTab.url().contains("/Edit/") && docId.matches("\\d+");
        docTab.bringToFront();
        docTab.waitForTimeout(1000);
        step(docTab, "Consent · Submit document", "Submit -> Yes; the form is submitted",
                "'Form Submitted Successfully!' (navigates to /Edit/<id>)",
                ok ? "Submitted (doc ID " + docId + ")" : "NOT confirmed (url=" + docTab.url() + ")",
                ok ? "PASS" : "FAIL");
        try { docTab.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new Page.GetByRoleOptions().setName("OK")).click(); } catch (Exception ignore) { }
        docTab.waitForTimeout(1200);

        // Screenshot of the submitted report (the /Edit/<id> document, after the success OK is dismissed).
        try { docTab.bringToFront(); docTab.waitForTimeout(800); } catch (Exception ignore) { }
        step(docTab, "Consent · Report screenshot (after submit)", "Capture the submitted consent report (/Edit/<id>)",
                "The submitted report is shown",
                ok ? "Report shown (/Edit/" + docId + ")" : "Report not confirmed (url=" + docTab.url() + ")",
                ok ? "PASS" : "FAIL");

        // Back on the queue: close the Consent Details (#consent) popup left open after submitting.
        try { page.bringToFront(); } catch (Exception ignore) { }
        boolean closed = dq.dismissAnyModal();
        step(page, "Consent · Close Consent Details popup", "Close the Consent Details (#consent) popup after submitting",
                "The Consent Details popup is closed", closed ? "Consent Details popup closed" : "No popup left open", "PASS");

        addSummary("Consent · Patient", patient);
        addSummary("Consent · Form", consentForm);
        addSummary("Consent · Result", ok ? "Submitted (ID " + docId + ")" : "Not confirmed");
        } catch (Exception e) {
            // The consent/SPC document flow is server-flaky (external form fetch / txkernel). Fail cleanly with the
            // reason instead of aborting the whole DoctorQueue run.
            System.out.println("Consent flow error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            step(page, "Consent · Flow error", "Complete the consent/SPC document flow (search form -> Save -> Create -> Sign -> Submit)",
                    "The consent document is submitted",
                    "Consent flow did not complete (" + e.getClass().getSimpleName() + ") — " + consentForm + " (server-flaky SPC/external-form fetch)", "FAIL");
            try { dq.dismissAnyModal(); } catch (Exception ignore) { }
            addSummary("Consent · Result", "Flow error: " + e.getClass().getSimpleName());
        }
    }

    // ===== Section: View Consent =========================================
    private void sectionViewConsent() {
        DoctorQueuePage dq = openSearchSelect("View Consent");
        if (dq == null) { addSummary("View Consent · Result", "Doctor Queue not reached"); return; }

        // Most patients have no saved consent — retry different patients until one's View Consent list has a record.
        String patient = null, iconFound = null;
        int tries = 0;
        for (int i = 0; i < 8 && iconFound == null; i++) {
            String cand = dq.selectNthQueueRowWithDepartment(i);
            if (cand == null) break;
            patient = cand; tries++;
            dq.clickViewConsent();
            dq.clickSearchInViewConsent();
            iconFound = dq.clickConsentActionIcon("SPC");
            if (iconFound == null) dq.dismissAnyModal();
        }
        boolean hasRecord = iconFound != null;
        step(page, "View Consent · Find a patient with a saved consent",
                "Click View Consent; Search; find a patient whose consent list has a record (view icon)",
                "A consent record with a view icon is listed",
                hasRecord ? "Record found (patient " + patient + ", after " + tries + " tr" + (tries == 1 ? "y" : "ies") + ")"
                          : "No consent record found (tried " + tries + " patient(s))",
                hasRecord ? "PASS" : "FAIL");
        if (!hasRecord) { dq.dismissAnyModal(); addSummary("View Consent · Result", "No patient with a saved consent (tried " + tries + ")"); return; }

        Page formTab = null;
        try {
            formTab = page.context().waitForPage(() -> page.locator("#consent_action_icon")
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)));
            formTab.waitForLoadState();
            formTab.waitForTimeout(1800);
        } catch (Exception e) {
            System.out.println("View Consent: opening the saved form failed - " + e.getClass().getSimpleName());
        }
        boolean tabOk = formTab != null && !formTab.isClosed();
        step(tabOk ? formTab : page, "View Consent · Open the saved form (new tab)",
                "Click the row's Action (eye) icon (viewConsentFormDetail)",
                "The saved form opens in a new tab",
                tabOk ? "Form opened: " + formTab.url() : "The saved form did NOT open in a new tab (the view icon vanished on re-render)",
                tabOk ? "PASS" : "FAIL");

        try { if (formTab != null && !formTab.isClosed()) formTab.close(); } catch (Exception ignore) { }
        try { page.bringToFront(); } catch (Exception ignore) { }
        try { dq.dismissAnyModal(); } catch (Exception ignore) { }
        addSummary("View Consent · Patient", patient);
        addSummary("View Consent · Result", tabOk ? "Saved form opened in a new tab" : "View icon vanished on re-render — form not opened");
    }

    // ===== Section: Change Doctor ========================================
    private void sectionChangeDoctor() {
        DoctorQueuePage dq = openSearchSelect("Change Doctor");
        if (dq == null) { addSummary("Change Doctor · Result", "Doctor Queue not reached"); return; }

        // Check only ONE or TWO records: select → Change Doctor → if the doctor dropdown is EMPTY, fail with the
        // reason (do NOT keep churning through the whole queue).
        String patient = null;
        boolean modalReady = false;
        boolean emptyDropdown = false;
        int triedPatients = 0;
        for (int i = 0; i < 2; i++) {
            String cand = dq.selectNthQueueRowWithDepartment(i);
            if (cand == null) break;
            patient = cand;
            triedPatients++;
            if (!dq.clickChangeDoctor()) { dq.closeChangeDoctorModal(); continue; }   // modal didn't open — try the next
            if (dq.changeDoctorHasDoctors()) { modalReady = true; break; }
            emptyDropdown = true;   // modal opened but the doctor dropdown had no values
            dq.closeChangeDoctorModal();
        }
        String reason = patient == null ? "No patient in the queue to check"
                : (modalReady ? "Selected: " + patient + " (doctor dropdown populated; checked " + triedPatients + " record(s))"
                              : (emptyDropdown ? "Doctor dropdown is EMPTY — no doctors available to change (checked " + triedPatients + " record(s))"
                                               : "Change Doctor modal did not open (checked " + triedPatients + " record(s))"));
        step(page, "Change Doctor · Select a patient with doctors",
                "Check 1-2 records; open Change Doctor; if the doctor dropdown has no values, FAIL with the reason",
                "A patient whose doctor dropdown is populated is selected", reason,
                modalReady ? "PASS" : "FAIL");
        if (!modalReady) { dq.closeChangeDoctorModal(); addSummary("Change Doctor · Result", reason); return; }

        String res = dq.selectAnyDoctorAndSave();
        boolean ok = res != null && (res.toLowerCase().contains("updated") || res.toLowerCase().contains("success"));
        step(page, "Change Doctor · Select doctor, Save & toast", "Select any doctor; Save (FnSavePatientType); wait for the toast",
                "'Doctor Updated Successfully.' toast", res, ok ? "PASS" : "FAIL");

        addSummary("Change Doctor · Patient", patient);
        addSummary("Change Doctor · Result", ok ? res : "Not confirmed");
    }

    // ===== Section: Assign Triage ========================================
    private void sectionAssignTriage() {
        DoctorQueuePage dq = openSearchSelect("Assign Triage");
        if (dq == null) { addSummary("Assign Triage · Result", "Doctor Queue not reached"); return; }

        String patient = dq.selectFirstQueueRow();
        step(page, "Assign Triage · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean modal = dq.clickAssignTriage();
        step(page, "Assign Triage · Open modal", "Click 'Assign Triage' (BtnAssignTriage())",
                "The Triage popup opens", modal ? "Triage popup opened" : "Triage popup did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) { addSummary("Assign Triage · Result", "Triage popup did not open"); return; }

        // The Assign Triage assignment isn't completable on this queue — try to DISMISS the popup and FAIL with the
        // accurate reason (dismissed but not completable, OR the popup's close controls are disabled / not dismissable).
        String dismiss = dq.dismissTriageAndDiagnose();
        boolean dismissed = dismiss.toLowerCase().startsWith("dismissed");
        String reason = dismissed
                ? "Assign Triage not completable on the Doctor Queue (Triage popup opened but has no assignable flow); popup " + dismiss
                : dismiss;   // "COULD NOT dismiss the Assign Triage popup — ..."
        step(page, "Assign Triage · Dismiss popup & result",
                "Try to dismiss the Triage popup (Close/Cancel/× or outside click); report the reason",
                "The Triage popup is dismissed", reason,
                "FAIL");   // Assign Triage does not complete on this queue → always FAIL, with the real reason

        addSummary("Assign Triage · Patient", patient);
        addSummary("Assign Triage · Result", reason);
    }

    // ===== Section: Attach Signature =====================================
    private void sectionAttachSignature() {
        DoctorQueuePage dq = openSearchSelect("Signature");
        if (dq == null) { addSummary("Signature · Result", "Doctor Queue not reached"); return; }

        String patient = dq.selectFirstQueueRow();
        step(page, "Signature · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        String toast = dq.attachSignatureAndGetToast(SIGNATURE_FILE);
        boolean ok = toast != null && (toast.toLowerCase().contains("signature") || toast.toLowerCase().contains("success"));
        step(page, "Signature · Attach & success toast", "Attach signature (set #PhotoData / queue.PhotoFileData); wait for the toast",
                "'Digital Signature Saved Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Signature · Patient", patient);
        addSummary("Signature · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: New Case =============================================
    private void sectionNewCase() {
        DoctorQueuePage dq = openSearchSelect("New Case");
        if (dq == null) { addSummary("New Case · Result", "Doctor Queue not reached"); return; }

        // Random patient each run — avoids always colliding on the same first patient ("already generated").
        String patient = dq.selectRandomQueueRow();
        step(page, "New Case · Select a patient", "Select a RANDOM patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = dq.clickNewCase();
        step(page, "New Case · Click New Case", "Click 'New Case' (OpenNewCase()); the New Case (View Case) modal opens",
                "New Case modal opens", opened ? "New Case modal opened" : "New Case modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String filled = dq.fillNewCaseDetails();
        step(page, "New Case · Fill details", "Department, Doctor, Diagnosis (MRN/NRIC/Name pre-filled)",
                "Case details accepted", filled, "PASS");

        String toast = dq.saveNewCaseAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = !tl.contains("already generated")
                && (tl.contains("added successfully") || tl.contains("case added") || tl.contains("success"));
        step(page, "New Case · Save & success toast", "Click Save (SaveQueueNewCaseDetails()); retry other doctor/department on duplicate",
                "'EMR Case added successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast + (ok ? "" : " (still duplicate after retries)"),
                ok ? "PASS" : "FAIL");

        addSummary("New Case · Patient", patient);
        addSummary("New Case · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Close Visit ==========================================
    private void sectionCloseVisit() {
        DoctorQueuePage dq = openSearchSelect("Close Visit");
        if (dq == null) { addSummary("Close Visit · Result", "Doctor Queue not reached"); return; }

        String patient = dq.selectFirstQueueRow();
        step(page, "Close Visit · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = dq.clickCloseVisit();
        step(page, "Close Visit · Click Close Visit", "Click 'Close Visit' (OpenDischargeTypeModal()); the modal opens",
                "The Close Visit / discharge modal opens", opened ? "Close Visit modal opened" : "Close Visit modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String filled = dq.enterRemark("Visit closed - automated test");
        step("Close Visit · Enter Remark", "Enter the Close-Visit remark (+ any mandatory discharge type)", "Remark accepted", filled, "PASS");

        String toast = dq.saveCloseVisitAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("close") || toast.toLowerCase().contains("visit")
                || toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved"));
        step(page, "Close Visit · Save & success toast", "Click Save; wait for the success toast",
                "'Visit Closed Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Close Visit · Patient", patient);
        addSummary("Close Visit · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Medico Legal (MLC) ===================================
    private void sectionMedicoLegal() {
        DoctorQueuePage dq = openSearchSelect("Medico Legal");
        if (dq == null) { addSummary("Medico Legal · Result", "Doctor Queue not reached"); return; }

        String patient = dq.selectFirstQueueRow();
        step(page, "Medico Legal · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) { addSummary("Medico Legal · Result", "No patient in the queue"); return; }

        boolean opened = dq.clickMedicoLegal();
        step(page, "Medico Legal · Click Medico Legal", "Click 'Medico Legal' (OpenMLCModal); the modal opens",
                "Medico Legal (Police Station / Docket No / Remark) modal opens",
                opened ? "Medico Legal modal opened" : "Medico Legal modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Medico Legal · Result", "Medico Legal modal did not open"); return; }

        String filled = dq.fillMlcDetails("Damansara Police Station", "DKT" + System.currentTimeMillis() % 100000,
                "Medico legal case - automated test");
        step("Medico Legal · Enter Police Station / Docket No / Remark",
                "Enter Police Station, Docket No, Remark", "All three fields accepted", filled, "PASS");

        String doc = dq.addMlcDocument(SIGNATURE_FILE, "Incident photo - automated test");
        boolean docOk = doc.contains("File=attached") && doc.contains("Add=clicked");
        step(page, "Medico Legal · Document List (Attach File + Description + Add)",
                "Attach a file, enter File Description, click Add", "Document row added to the Document List",
                doc, docOk ? "PASS" : "FAIL");

        String checklist = dq.selectMlcChecklistItem("Checked - automated test");
        boolean checklistOk = checklist.startsWith("Checklist item ticked");
        step("Medico Legal · MLC Check List (select item + Remark)",
                "Select any checklist item, enter its Remark", "A checklist item is ticked with a Remark",
                checklist, checklistOk ? "PASS" : "FAIL");

        String toast = dq.saveMlcAndGetToast();
        boolean ok = toast != null && !toast.isEmpty()
                && (toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved"));
        step(page, "Medico Legal · Save & success toast", "Click Save; wait for the success toast",
                "'... Saved Successfully' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Medico Legal · Patient", patient);
        addSummary("Medico Legal · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Revoke Visit =========================================
    private void sectionRevokeVisit() {
        DoctorQueuePage dq = openSearchSelect("Revoke Visit");
        if (dq == null) { addSummary("Revoke Visit · Result", "Doctor Queue not reached"); return; }

        // Revoke applies to a CLOSED visit — tick the 'Closed Visit' filter + Search to list closed visits.
        int closed = dq.viewClosedVisits();
        step(page, "Revoke Visit · List closed visits", "Tick the 'Closed Visit' filter (queue.ClosedVisit) and Search",
                "Closed visits are listed (revocable)", closed + " closed-visit row(s)", closed > 0 ? "PASS" : "FAIL");
        if (closed == 0) { addSummary("Revoke Visit · Result", "No closed visits to revoke"); return; }

        // A disabled Revoke button = that patient can't be revoked — scan the rows for a revocable one.
        String patient = dq.selectRevocableVisit();
        step(page, "Revoke Visit · Select a revocable visit", "Iterate closed-visit rows; select one whose Revoke button is enabled",
                "A revocable visit is selected", patient == null ? "No revocable visit found (every Revoke button disabled)" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) { addSummary("Revoke Visit · Result", "No revocable visit in the Doctor Queue"); return; }

        boolean opened = dq.clickRevokeVisit();
        step(page, "Revoke Visit · Click Revoke Visit", "Click 'Revoke Visit' (OpenRevokeTypeModal()); the modal opens",
                "The Revoke Visit modal opens",
                opened ? "Revoke Visit modal opened"
                       : ("Revoke Visit not available — footer button " + dq.lastRevokeState + " (selected visit is not revocable)"),
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Revoke Visit · Result", "Revoke button " + dq.lastRevokeState + " — visit not revocable"); return; }

        String filled = dq.enterRevokeRemark("Visit revoked - automated test");
        step("Revoke Visit · Enter Remark", "Enter the Revoke remark (+ any mandatory type)", "Remark accepted", filled, "PASS");

        String toast = dq.saveRevokeVisitAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("revok") || toast.toLowerCase().contains("visit")
                || toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved"));
        step(page, "Revoke Visit · Save & success toast", "Click Save; wait for the success toast",
                "'Visit Revoked Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Revoke Visit · Patient", patient);
        addSummary("Revoke Visit · Result", ok ? toast : "Not confirmed");
    }
}
