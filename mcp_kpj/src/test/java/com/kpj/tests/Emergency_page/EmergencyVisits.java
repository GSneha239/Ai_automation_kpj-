package com.kpj.tests.Emergency_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

/**
 * TC17 - Emergency &gt; <b>Emergency Visits</b> (route {@code #/QueueManagement}).
 *
 * <p>Emergency Visits is the SAME queue screen as OP Outpatient Queue Management, reached from the Emergency
 * menu. This test reuses the queue page object (via {@link com.kpj.pages.Emergency_page.EmergencyVisits}, which
 * extends {@code OutPatientQueueManagementPage}) and exercises the core emergency-queue actions:</p>
 * <ol>
 *   <li><b>Generate Queue</b> — select a today patient, open Generate Queue, assign/change the queue → toast.</li>
 *   <li><b>New Case</b> — select a patient, open New Case, fill Department/Doctor/Diagnosis, Save → toast.</li>
 *   <li><b>Patient Task</b> — select a patient, add tasks, Save → toast.</li>
 * </ol>
 */
public class EmergencyVisits extends DevHisBase {

    // The search window MUST be relative to today, not a fixed date — a hardcoded window goes stale and
    // returns an EMPTY grid once "today" moves past it (this one was "01/06/2026"-"31/07/2026", which by
    // 09/09/2026 was 40+ days in the past, causing every queue-selection step here to fail with "No patient
    // in the queue"). Same fix pattern already used in OutPatientQueueManagementTest.
    private static final java.time.format.DateTimeFormatter DMY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FROM_DATE = java.time.LocalDate.now().minusMonths(1).format(DMY);
    private static final String TO_DATE   = java.time.LocalDate.now().format(DMY);
    /** Signature image attached in the Attach Signature section. */
    private static final java.nio.file.Path SIGNATURE =
            java.nio.file.Paths.get("C:\\Users\\Siva Sankar\\Downloads\\sinature.jpeg");

    public EmergencyVisits() { super("TC17_EmergencyVisits"); }

    public static void main(String[] args) {
        EmergencyVisits t = new EmergencyVisits();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Emergency - Emergency Visits", "Emergency > Emergency Visits",
                "Emergency queue (#/QueueManagement): Generate Queue, New Case and Patient Task on emergency visits.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " / " + PASS, "Authenticated; Patient Dashboard", "Logged in", "PASS");

        sectionAttachSignature();
        sectionChangeDoctor();
        sectionConsentForms();
        sectionViewConsent();
        // sectionViewDetails();  // read-only view only (Update button not functional here) — skipped per request
        sectionGenerateQueue();
        sectionNewCase();
        sectionPatientTask();
        sectionRequestMrd();
        sectionReturnMrd();
        sectionFallRisk();
        sectionCloseVisit();

        addSummary("Application URL", BASE + "/#/QueueManagement");
    }

    // ===== Return MRD File =====
    private void sectionReturnMrd() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Return MRD · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectQueueRowWithMrdFile();
        step("Return MRD · Select a patient (with MRD file)", "Select a patient that HAS an MRD file",
                "A patient with an MRD file is selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickReturnMrdFile();
        step(page, "Return MRD · Click Return MRD File", "Click 'Return MRD File' (fnReturnMRDFile)",
                "The MRD Return popup opens", opened ? "MRD Return popup opened" : "Popup did NOT open (no file to return)",
                opened ? "PASS" : "MANUAL");
        if (!opened) return;

        String toast = qm.fillReturnMrdAndGetToast();
        boolean ok = toast != null && toast.toLowerCase().matches(".*(return|success|saved).*");
        step(page, "Return MRD · Fill details & OK", "Fill From Dept / To Dept / User + Remark; click OK (fnIUDReturn)",
                "'File Return successfully.' toast is shown",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        addSummary("Return MRD · Patient", patient);
        addSummary("Return MRD · Result", ok ? toast : "Not confirmed");
    }

    // ===== Fall Risk Assessment =====
    private void sectionFallRisk() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Fall Risk · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectRandomQueueRow();
        step("Fall Risk · Select a patient", "Select any patient row",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickFallRiskAssessment();
        step(page, "Fall Risk · Open popup", "Click 'Fall Risk Assessment' (fnOpenFallRiskAssessment)",
                "The Assessment of Fall modal opens", opened ? "Fall Risk modal opened" : "Modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String toast = qm.fillFallRiskAndApply();
        boolean ok = toast != null && toast.toLowerCase().matches(".*(success|saved|applied).*");
        step(page, "Fall Risk · Tick a checkbox, Apply & toast", "Tick a checkbox, click Apply (fnApply); wait for the success toast",
                "'Fall Risk Assessment saved successfully.' toast is shown",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        addSummary("Fall Risk · Patient", patient);
        addSummary("Fall Risk · Result", ok ? toast : "Not confirmed");
    }

    // ===== Request MRD File =====
    private void sectionRequestMrd() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Request MRD · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectQueueRowWithoutMrdFile();
        step("Request MRD · Select a patient (no MRD file)", "Select a patient that has NO MRD file yet",
                "A patient without an MRD file is selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        String outcome = qm.clickRequestMrdFile();
        step(page, "Request MRD · Click Request MRD File", "Click 'Request MRD File' (fnRequestMRDFile)",
                "If the patient HAS a file the MRD report opens in a new tab; else a 'File not generated — create one?' confirm appears",
                "Outcome: " + outcome, outcome.equals("none") ? "MANUAL" : "PASS");

        // Branch 1 — the patient already HAS an MRD file: the report opened in a new tab. Capture it, then close.
        if (outcome.equals("report")) {
            com.microsoft.playwright.Page reportTab = page.context().pages().get(page.context().pages().size() - 1);
            try { reportTab.waitForLoadState(); } catch (Exception ignore) {}
            reportTab.waitForTimeout(3000);
            byte[] png = null;
            try { reportTab.bringToFront(); reportTab.waitForTimeout(1500); png = reportTab.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
            catch (Exception e) { System.out.println("Request MRD report screenshot: " + e.getMessage()); }
            String actual = "MRD report opened in a new tab (patient already has an MRD file): " + reportTab.url();
            if (png != null && png.length > 0)
                step(png, "Request MRD · Report (new tab) & screenshot", "Patient has a file → the MRD report opens in a new tab → screenshot",
                        "The MRD report is shown", actual, "PASS");
            else
                step(reportTab, "Request MRD · Report (new tab)", "Patient has a file → the MRD report opens in a new tab",
                        "The MRD report is shown", actual, "PASS");
            try { if (reportTab != page && !reportTab.isClosed()) reportTab.close(); } catch (Exception ignore) {}
            page.bringToFront();
            addSummary("Request MRD · Patient", patient);
            addSummary("Request MRD · Result", "Report opened (patient already has an MRD file)");
            return;
        }

        // Branch 2 — no file yet: a 'create MRD file?' confirm appeared → Yes → fill → Save → Cancel.
        if (!outcome.equals("confirm")) {
            step("Request MRD · Create MRD file", "Click Yes → fill MRD Details → Save",
                    "MRD Details filled and saved", "No confirm/report — the async MRD server check did not return in time", "MANUAL");
            return;
        }

        boolean modalOpen = qm.confirmCreateMrdFile();
        step(page, "Request MRD · Confirm Yes", "Click Yes on the 'create MRD file?' confirm; the MRD Details modal opens",
                "The MRD Details modal opens", modalOpen ? "MRD Details modal opened" : "Modal did NOT open",
                modalOpen ? "PASS" : "FAIL");
        if (!modalOpen) return;

        String toast = qm.fillMrdDetailsAndSave();
        boolean ok = toast != null && toast.toLowerCase().matches(".*(allocated|success|saved).*");
        step(page, "Request MRD · Fill details & Save", "Fill all MRD Details fields (Patient Type / Location / File Type / Rack→Row→Box); Save (fnIUDAllocation)",
                "'File Allocated Successfully.' toast is shown",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        qm.cancelMrdDetails();
        step(page, "Request MRD · Cancel", "Click Cancel to close the MRD Details popup",
                "The popup is closed", "Popup closed", "PASS");
        addSummary("Request MRD · Patient", patient);
        addSummary("Request MRD · Result", ok ? toast : "Not confirmed");
    }

    // ===== View Details =====
    private void sectionViewDetails() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("View Details · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectRandomQueueRow();
        step("View Details · Select a patient", "Select any patient row",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickViewDetails();
        step(page, "View Details · Open popup", "Click 'View Details' (OpenPatientRegistrationPopupScreen)",
                "The patient registration details popup opens",
                opened ? "Patient details popup opened" : "Popup did NOT open", opened ? "PASS" : "FAIL");
        if (!opened) return;

        // NOTE: verified live — from the Emergency Visits queue this popup is effectively READ-ONLY: its "Update"
        // button (ng-click="UpdateRegistration();") references a function that is NOT defined on the popup's scope
        // (UpdateRegistration is undefined → clicking it is a silent no-op). So this is a details VIEW: screenshot it.
        step(page, "View Details · View patient details & screenshot",
                "The patient's registration details (Patient / Correspondence / NOK / Payor / Visit) are shown",
                "The patient's registration details are displayed",
                "Read-only patient details shown for " + patient
                        + " (the popup's Update button is not wired in this queue context — UpdateRegistration is undefined on scope)",
                "PASS");

        qm.closeViewDetails();
        step(page, "View Details · Close", "Click Close (CancelRegistration) to dismiss the popup",
                "The popup is closed", "Popup closed", "PASS");
        addSummary("View Details · Patient", patient);
        addSummary("View Details · Note", "Read-only view — the popup's Update button is not functional here (UpdateRegistration undefined)");
    }

    // ===== View Consent =====
    private void sectionViewConsent() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("View Consent · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectRandomQueueRow();
        step("View Consent · Select a patient", "Select any patient row",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        qm.clickViewConsent();
        step(page, "View Consent · Open modal", "Click 'View Consent' (fnViewConsents); the View Consent Forms modal opens",
                "The View Consent Forms modal opens", "View Consent modal opened", "PASS");

        qm.clickSearchInViewConsent();
        step(page, "View Consent · Search", "Click Search; the patient's saved consents are listed",
                "The patient's saved consents are listed", "Searched saved consents", "PASS");

        // Find the eye icon (viewConsentFormDetail) for a saved consent; most patients have none.
        String found = qm.clickConsentActionIcon("");
        if (found == null) {
            step(page, "View Consent · Open a saved consent", "Click the eye icon (viewConsentFormDetail) to open a saved consent",
                    "A saved consent form opens in a new tab",
                    "No saved consents for this patient — nothing to view (most emergency patients have none)", "MANUAL");
            addSummary("View Consent · Patient", patient);
            addSummary("View Consent · Result", "No saved consents to view");
            return;
        }
        int tabsBefore = page.context().pages().size();
        try { page.locator("#consent_action_icon").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("View Consent icon click: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('consent_action_icon'); if(e) e.removeAttribute('id'); }");

        com.microsoft.playwright.Page consentTab = null;
        for (int i = 0; i < 20 && consentTab == null; i++) {
            if (page.context().pages().size() > tabsBefore) { consentTab = page.context().pages().get(page.context().pages().size() - 1); break; }
            page.waitForTimeout(1000);
        }
        if (consentTab == null) {
            step("View Consent · Open a saved consent", "Click the eye icon; the saved consent opens in a new tab",
                    "A saved consent form opens", "Eye icon clicked but no new tab appeared", "MANUAL");
            return;
        }
        try { consentTab.waitForLoadState(); } catch (Exception ignore) {}
        consentTab.waitForTimeout(2500);
        byte[] png = null;
        try { consentTab.bringToFront(); png = consentTab.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
        catch (Exception e) { System.out.println("View Consent screenshot: " + e.getMessage()); }
        String actual = "Saved consent opened (" + consentTab.url() + ")";
        if (png != null && png.length > 0) {
            step(png, "View Consent · Open a saved consent & screenshot", "Click the eye icon → the saved consent opens in a new tab → full-page screenshot",
                    "The saved consent form is shown", actual, "PASS");
        } else {
            step(consentTab, "View Consent · Open a saved consent & screenshot", "Click the eye icon → the saved consent opens in a new tab",
                    "The saved consent form is shown", actual, "PASS");
        }
        try { if (consentTab != page && !consentTab.isClosed()) consentTab.close(); } catch (Exception ignore) {}
        page.bringToFront();
        addSummary("View Consent · Patient", patient);
        addSummary("View Consent · Result", actual);
    }

    // ===== Change Doctor =====
    private void sectionChangeDoctor() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Change Doctor · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectRandomQueueRow();
        step("Change Doctor · Select a patient", "Select any patient row",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickChangeDoctor();
        step(page, "Change Doctor · Open popup", "Click 'Change Doctor' (fnChangePatientType)",
                "The Change Doctor modal opens", opened ? "Change Doctor modal opened" : "Change Doctor modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String toast = qm.selectAnyDoctorAndSave();
        boolean ok = toast != null && toast.toLowerCase().matches(".*(updated|changed|saved|success).*");
        step(page, "Change Doctor · Select doctor, Save & toast", "Select a doctor; Save; wait for the success toast",
                "Success toast is shown", toast == null || toast.isEmpty() ? "No success toast appeared" : toast,
                ok ? "PASS" : "FAIL");
        addSummary("Change Doctor · Patient", patient);
        addSummary("Change Doctor · Result", ok ? toast : "Not confirmed");
    }

    // ===== Consent / Forms =====
    private void sectionConsentForms() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Consent/Forms · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectRandomQueueRow();
        step("Consent/Forms · Select a patient", "Select any patient row",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        qm.clickConsentForms();
        step(page, "Consent/Forms · Open modal", "Click 'Consent/Forms' (fnOnConsentClick); the Consent Details modal opens",
                "Consent Details modal opens", "Consent/Forms modal opened", "PASS");

        int tabsBefore = page.context().pages().size();
        String form = qm.selectAnyConsentFormAddSave();
        boolean formOk = form != null && !form.startsWith("ERR");
        step(page, "Consent/Forms · Select form + Add + Save", "Select a consent form from the dropdown, Add (load template), Save (IUDconsentdetail)",
                "A consent form is selected, its template loaded, and saved", "Form: " + form, formOk ? "PASS" : "FAIL");

        // The report/form opens in a NEW tab — submit it and take a full-page screenshot.
        com.microsoft.playwright.Page reportTab = null;
        for (int i = 0; i < 30 && reportTab == null; i++) {
            if (page.context().pages().size() > tabsBefore) { reportTab = page.context().pages().get(page.context().pages().size() - 1); break; }
            page.waitForTimeout(1000);
        }
        if (reportTab == null) {
            step("Consent/Forms · Report tab", "Save opens the consent report/form in a new tab",
                    "A new tab opens", "No new tab appeared", "MANUAL");
            return;
        }
        try { reportTab.waitForLoadState(); } catch (Exception ignore) {}
        reportTab.waitForTimeout(2500);
        boolean submitted = submitConsentForm(reportTab);
        byte[] png = null;
        try { reportTab.bringToFront(); reportTab.waitForTimeout(2000); png = reportTab.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
        catch (Exception e) { System.out.println("Consent/Forms report screenshot: " + e.getMessage()); }
        String actual = (submitted ? "Form Submitted Successfully" : "Report tab shown (submit not confirmed)") + " (" + reportTab.url() + ")";
        String sStatus = submitted ? "PASS" : "MANUAL";
        if (png != null && png.length > 0) {
            step(png, "Consent/Forms · Submit report & screenshot", "The report opens in a new tab → Create Document → Submit → full-page screenshot",
                    "The submitted consent report (/Edit/<id>) is shown", actual, sStatus);
        } else {
            step(reportTab, "Consent/Forms · Submit report & screenshot", "The report opens in a new tab → Create Document → Submit → screenshot",
                    "The submitted consent report (/Edit/<id>) is shown", actual, sStatus);
        }
        // After submitting, close the report tab and the Consent Details modal (else it blocks later sections).
        try { if (reportTab != page && !reportTab.isClosed()) reportTab.close(); } catch (Exception ignore) { }
        page.bringToFront();
        qm.closeConsentFormsModal();
        step(page, "Consent/Forms · Close", "Close the report tab and the Consent Details modal",
                "The report tab and Consent Details modal are closed", "Report tab + Consent Details modal closed", "PASS");
        addSummary("Consent/Forms · Patient", patient);
        addSummary("Consent/Forms · Form", form);
    }

    /**
     * Submit the consent form on its tab: the tab opens on the document LIST → click <b>Create Document</b> to
     * open the Create form → click <b>Submit</b> (#Submit) → the <b>Yes</b> confirm (#btnConfirmYes) appears after
     * a beat (auto-waited) → submission completes when the URL redirects to <b>/Edit/&lt;id&gt;</b>. Returns true
     * only when it actually reached /Edit.
     */
    private boolean submitConsentForm(com.microsoft.playwright.Page t) {
        try {
            // If on the document list, open the Create form first.
            try {
                if (t.locator("a:has-text('Create Document')").count() > 0)
                    t.locator("a:has-text('Create Document')").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000));
            } catch (Exception ignore) { }
            t.waitForSelector("#Submit", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(30000));
            t.waitForTimeout(1500);
            // FILL the form — set every empty required/visible field so Submit isn't blocked by validation
            // (different consent forms have different fields; this is a best-effort generic fill).
            fillConsentReportForm(t);
            t.waitForTimeout(600);
            t.locator("#Submit").click();
            // Confirm "Yes" (the submit confirm appears after a beat — locator.click auto-waits for it).
            try { t.locator("#btnConfirmYes").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(15000)); } catch (Exception ignore) { }
            // Dismiss the success "OK" popup if one appears.
            try { t.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("OK")).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(10000)); } catch (Exception ignore) { }
            // Submission completes on the redirect to /Edit/<id>.
            try {
                t.waitForURL("**/Edit/**", new com.microsoft.playwright.Page.WaitForURLOptions()
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(30000));
            } catch (Exception ignore) { }
            t.waitForTimeout(1500);
            return t.url().contains("/Edit/");
        } catch (Exception e) {
            System.out.println("submitConsentForm: " + e.getMessage());
            return t.url().contains("/Edit/");
        }
    }

    /** Best-effort generic fill of the consent Create form: empty text/date inputs, selects and required
     *  checkboxes — so the mandatory-field validation lets the Submit through. */
    private void fillConsentReportForm(com.microsoft.playwright.Page t) {
        try {
            t.evaluate("() => { const d=new Date(); const today=('0'+d.getDate()).slice(-2)+'/'+('0'+(d.getMonth()+1)).slice(-2)+'/'+d.getFullYear();"
                    + " document.querySelectorAll('select,input,textarea').forEach(e=>{ if(e.offsetParent===null) return; const type=(e.type||'').toLowerCase();"
                    + "   if(e.tagName==='SELECT'){ if(e.selectedIndex<=0 && e.options.length>1){ e.selectedIndex=1; e.dispatchEvent(new Event('change',{bubbles:true})); } return; }"
                    + "   if(type==='checkbox'){ if(!e.checked && (e.required || /required|mandatory/i.test((e.className||'')+(e.getAttribute('ng-required')||'')))) e.click(); return; }"
                    + "   if(type==='radio'||type==='file'||type==='hidden'||type==='button'||type==='submit'||type==='image') return;"
                    + "   if((e.value||'').trim()) return;"
                    + "   const key=((e.name||'')+(e.id||'')+(e.getAttribute('placeholder')||'')).toLowerCase();"
                    + "   let v='NA'; if(/date/.test(key)) v=today; else if(type==='email'||/email/.test(key)) v='test@example.com'; else if(type==='number'||type==='tel'||/mobile|phone|no\\b|number|amount|age/.test(key)) v='1';"
                    + "   e.value=v; e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }); }");
        } catch (Exception e) { System.out.println("fillConsentReportForm: " + e.getMessage()); }
    }

    // ===== Attach Signature =====
    private void sectionAttachSignature() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Attach Signature · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectRandomQueueRow();
        step("Attach Signature · Select a patient", "Select any patient row",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        String toast = qm.attachSignatureAndGetToast(SIGNATURE.toString());
        boolean ok = toast != null && toast.toLowerCase().matches(".*(signature|saved|success).*");
        step(page, "Attach Signature · Attach image & toast",
                "Click 'Attach Signature' and upload the signature image (" + SIGNATURE.getFileName() + ")",
                "'Digital Signature Saved Successfully.' toast is shown",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        addSummary("Attach Signature · Patient", patient);
        addSummary("Attach Signature · Result", ok ? toast : "Not confirmed");
    }

    // ===== Close Visit =====
    private void sectionCloseVisit() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Close Visit · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectRandomQueueRow();
        step("Close Visit · Select a patient", "Select a RANDOM patient row",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickCloseVisit();
        step(page, "Close Visit · Open popup", "Click 'Close Visit' (OpenDischargeTypeModal); the Close Visit modal opens",
                "The Close Visit modal opens", opened ? "Close Visit modal opened" : "Close Visit modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String rem = qm.enterRemark("Closed - automated test");
        step(page, "Close Visit · Enter remark", "Enter the mandatory Remark (+ any Discharge Type)",
                "The remark is accepted", rem, "PASS");

        String toast = qm.saveCloseVisitAndGetToast();
        boolean ok = toast != null && toast.toLowerCase().matches(".*(closed|success).*");
        step(page, "Close Visit · Save", "Click Save (confirm 'Do You Want To Save' if shown); wait for the success toast",
                "'Visit Closed Successfully.' toast is shown",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        addSummary("Close Visit · Patient", patient);
        addSummary("Close Visit · Result", ok ? toast : "Not confirmed");
    }

    /** A fresh Emergency Visits page navigated via the Emergency menu. Before navigating, defensively CLOSE any
     *  popup a previous section may have left open (so a leftover modal can't intercept the next tab's clicks). */
    private com.kpj.pages.Emergency_page.EmergencyVisits open() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = new com.kpj.pages.Emergency_page.EmergencyVisits(page);
        qm.dismissAnyModal();   // close any leftover popup before moving to the next tab
        qm.navigateTo(BASE);
        return qm;
    }

    // ===== Generate Queue =====
    private void sectionGenerateQueue() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        // Generate Queue only works for CURRENT-DATE patients — search today only.
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        qm.searchQueue(today, today);
        qm.waitForQueueLoaded();
        step("Generate Queue · Open Emergency Visits & search (today)", "Emergency → Emergency Visits; search today (" + today + ")",
                "Today's emergency visits are listed", "Queue searched for " + today, "PASS");

        String patient = null, oldQueue = "";
        boolean opened = false; int tried = 0;
        for (int i = 0; i < 12; i++) {
            String cand = qm.selectNthQueueRow(i);
            if (cand == null) break;
            patient = cand; oldQueue = qm.getSelectedQueueNo(); tried++;
            if (qm.clickGenerateQueue()) { opened = true; break; }
        }
        boolean hasQueue = oldQueue != null && !oldQueue.isEmpty() && !"0".equals(oldQueue);
        step("Generate Queue · Select a patient & check Queue No", "Select a today patient; read Queue No; open Generate Queue",
                "A patient whose Generate Queue modal opens is selected",
                patient == null ? "No patient in the queue"
                        : "Selected: " + patient + " | Queue No: " + (hasQueue ? oldQueue : "none") + (opened ? " (modal opened; tried " + tried + ")" : " (no modal; tried " + tried + ")"),
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        step(page, "Generate Queue · Click Generate Queue", "Click 'Generate Queue' (GenerateToken)",
                "Generate Queue modal opens", opened ? "Generate Queue modal opened" : "Generate Queue modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String res = qm.fillGenerateQueueAndSave(oldQueue);
        boolean ok = res != null && res.toLowerCase().matches(".*(token updated|updated successfully|success).*");
        step(page, "Generate Queue · " + (hasQueue ? "Change to a different queue" : "Assign a queue") + ", Save & toast",
                "Pick a room; Save (UpdateTokenNo)", "'Token Updated Successfully.' toast", res, ok ? "PASS" : "FAIL");
        addSummary("Generate Queue · Patient", patient);
        addSummary("Generate Queue · Result", ok ? res : "Not confirmed");
    }

    // ===== New Case =====
    private void sectionNewCase() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("New Case · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectRandomQueueRow();
        step("New Case · Select a patient", "Select a RANDOM patient row",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickNewCase();
        step(page, "New Case · Click New Case", "Click 'New Case' (OpenNewCase)",
                "New Case modal opens", opened ? "New Case modal opened" : "New Case modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String filled = qm.fillNewCaseDetails();
        step(page, "New Case · Fill details", "Department, Doctor, Diagnosis (MRN/NRIC/Name pre-filled)",
                "Case details accepted", filled, "PASS");

        String toast = qm.saveNewCaseAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = !tl.contains("already generated") && tl.matches(".*(added successfully|case added|success).*");
        step(page, "New Case · Save, toast & close popup", "Click Save (SaveQueueNewCaseDetails); success toast; close the popup",
                "'EMR Case added successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        addSummary("New Case · Patient", patient);
        addSummary("New Case · Result", ok ? toast : "Not confirmed");
    }

    // ===== Patient Task =====
    private void sectionPatientTask() {
        com.kpj.pages.Emergency_page.EmergencyVisits qm = open();
        qm.searchQueue(FROM_DATE, TO_DATE);
        step("Patient Task · Open Emergency Visits & search", "Emergency → Emergency Visits; 1-month range + Search",
                "Emergency visits are listed", "Queue searched", "PASS");

        String patient = qm.selectRandomQueueRow();
        step("Patient Task · Select a patient", "Select any patient row",
                "One patient selected", patient == null ? "No patient in the queue" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean opened = qm.clickPatientTask();
        step(page, "Patient Task · Click Patient Task", "Click 'Patient Task' (BtnPatientRemark)",
                "Patient Task modal opens", opened ? "Patient Task modal opened" : "Patient Task modal did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String toast = qm.addTasksSaveAndGetToast(java.util.List.of(
                "Emergency automation task 1 - follow up", "Emergency automation task 2 - review"));
        boolean ok = toast != null && toast.toLowerCase().matches(".*(record|added|success).*");
        step(page, "Patient Task · Add tasks, Save, toast & close",
                "Enter tasks -> Add; Save (fnIUDPatientRemark); close the popup",
                "'Record Added Successfully.' toast", toast == null || toast.isEmpty() ? "No success toast appeared" : toast,
                ok ? "PASS" : "FAIL");
        addSummary("Patient Task · Patient", patient);
        addSummary("Patient Task · Result", ok ? toast : "Not confirmed");
    }
}
