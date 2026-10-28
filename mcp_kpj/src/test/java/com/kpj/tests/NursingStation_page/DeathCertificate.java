package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DeathCertificate — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Death Certificate</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Nursing Station</b> → <b>Death Certificate</b> and click <b>Add</b>.</li>
 *   <li>Enter the <b>MRN</b> and click <b>Search</b>.</li>
 *   <li>Select the <b>certificate template</b>.</li>
 *   <li>Enter details in the <b>form template body</b>.</li>
 *   <li>Select <b>Department</b> and <b>Doctor</b>.</li>
 *   <li>Tick <b>Authenticate</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p><b>MRN.</b> Unless pinned with {@code -Dmrn=}, one is harvested from the death certificate list —
 * those patients are already accepted by this screen. A death certificate needs a deceased patient, so a
 * general-purpose MRN is unlikely to attach.</p>
 *
 * <p>&#9888; A successful run CREATES a real death certificate in the target environment.</p>
 */
public class DeathCertificate extends DevHisBase {

    /** Used when {@code -Dmrn=} is not supplied and the list yields nothing. */
    public static final String DEFAULT_MRN = "100000684";

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public DeathCertificate() { super("NursingStation_DeathCertificate"); }

    public static void main(String[] args) {
        DeathCertificate t = new DeathCertificate();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Death Certificate", "Nursing Station > Death Certificate",
                "&#9888; Creates a REAL death certificate: search the patient by MRN, pick the template, "
                        + "fill the template body, pick Department + Doctor, authenticate and Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String mrn = System.getProperty("mrn", DEFAULT_MRN);
        String bodyText = System.getProperty("bodyText",
                "<p>Automated test — death certificate template body.</p>");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.DeathCertificate dc =
                new com.kpj.pages.NursingStation_page.DeathCertificate(page);

        // 1) Navigate — escalates menu click -> hash route -> full page load.
        boolean rendered = dc.navigateViaMenu(BASE);
        step(page, "Open Death Certificate screen",
                "Click Nursing Station -> Death Certificate (retrying via the route and a full page load)",
                "The Death Certificate screen is shown",
                rendered ? "Opened " + page.url()
                         : "Route resolved but rendered NO screen. Body: \"" + dc.lastBodyText + "\"",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 1b) Pick an MRN this screen already accepts, unless one was pinned.
        if (System.getProperty("mrn") == null) {
            java.util.List<String> harvested = dc.harvestMrnsFromList();
            if (!harvested.isEmpty()) mrn = harvested.get(0);
            addSummary("MRN source", harvested.isEmpty()
                    ? "default (the death certificate list is empty)"
                    : "harvested from the death certificate list");
        } else {
            addSummary("MRN source", "-Dmrn");
        }

        // 2) Add
        boolean formOpen = dc.clickAdd();
        dc.describeControls();
        step(page, "Click Add", "Click Add to open the certificate form",
                "The Death Certificate form opens",
                formOpen ? "Form opened (" + page.url() + ")" : "Form did NOT open",
                formOpen ? "PASS" : "FAIL");

        // 3) MRN + Search — verified against the model, not just the input box. Several DevHIS screens
        //    refuse an MRN that resolves elsewhere, so candidates are tried in turn and, failing those,
        //    the screen's own patient lookup is asked for MRNs this environment actually has.
        // The way a person picks the patient (recorded from a manual run on the sibling Birth Certificate screen, which shares this form): the
        // screen's own patient LOOKUP — the search icon beside the MRN box — type a few digits, Search, click a row's select icon. The form's MRN
        // box is the wrong tool: it answers "Patient Not Found!" for most MRNs (the old default 100000684 no longer exists) and, for a short
        // fragment, attaches the first living patient it gets, whose certificate prints as a blank page. A pinned -Dmrn= still goes through the box.
        String searchResult = "";
        boolean attached = false, viaLookup = false;
        String attempts = "";
        if (System.getProperty("mrn") != null) {
            com.kpj.pages.MrnRetry.Result found = com.kpj.pages.MrnRetry.resolve(
                    "DeathCertificate", page, com.kpj.pages.MrnRetry.candidates(mrn),
                    dc::searchByMrn, dc::patientAttached, 10);
            searchResult = found.lastSearch;
            attached = found.attached();
            attempts = found.attempts;
            if (attached) mrn = found.mrn;
        } else {
            java.util.List<String> fragments = new java.util.ArrayList<>();
            for (String f : new String[]{"11", "33", "12", "13"}) fragments.add(f);
            for (int n = 10; n <= 99; n++) if (!fragments.contains(String.valueOf(n))) fragments.add(String.valueOf(n));
            searchResult = dc.pickPatientFromLookup(fragments, 8);
            attached = dc.patientAttached();
            viaLookup = attached;
            attempts = dc.lastLookupNote;
            if (attached) mrn = dc.readFormFields().getOrDefault("mrn", mrn);
            if (!attached) {
                // Last resort: the form's own box with the known candidates.
                com.kpj.pages.MrnRetry.Result found = com.kpj.pages.MrnRetry.resolve(
                        "DeathCertificate", page, com.kpj.pages.MrnRetry.candidates(DEFAULT_MRN),
                        dc::searchByMrn, dc::patientAttached, 10);
                searchResult = found.lastSearch;
                attached = found.attached();
                attempts = attempts + " | form MRN box: " + found.attempts;
                if (attached) mrn = found.mrn;
            }
        }
        addSummary("MRNs tried", attempts);

        step(page, "Enter MRN & click Search",
                "Pick the patient: open the patient lookup beside the MRN box, search, and click a patient's select icon "
                        + "(or enter an MRN and click the search icon when -Dmrn= is given)",
                "The patient is attached to the certificate",
                attached ? (viaLookup
                                ? "The patient was picked from the screen's own lookup, as a person does — " + dc.lastLookupNote
                                  + " — and attached (MRN " + mrn + "): " + searchResult
                                : "MRN " + mrn + " attached. " + searchResult)
                         : "No patient attached. Tried: " + attempts,
                attached ? "PASS" : "FAIL");

        // 4) Certificate template
        String template = dc.selectTemplate();
        boolean templateOk = !template.startsWith("(");
        step(page, "Select certificate template", "Select the Certificate Template",
                "A certificate template is selected",
                templateOk ? "Template = " + template : "Template NOT selected " + template,
                templateOk ? "PASS" : "FAIL");

        // 5) Form template body (CKEditor)
        // Choosing the template loads a real "DEATH CERTIFICATE" into the editor with fifteen blanks (Government of, Registration No., Name of
        // Deceased, Sex, Age, Date/Time/Place/Cause of Death, Nationality, Occupation, Address, Name of Father/Mother, Date of Registration).
        // Those blanks are filled in place — replacing the whole template with one line printed a report with nothing on it.
        // -DbodyText= still replaces the body outright.
        boolean customBody = System.getProperty("bodyText") != null;
        String bodyRes;
        if (customBody) {
            bodyRes = dc.enterTemplateBody(bodyText);
        } else {
            java.util.Map<String, String> pf = dc.readFormFields();
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            String today = now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeNow = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            String name = pf.getOrDefault("name", "").isEmpty() ? "Auto Deceased Test" : pf.get("name");
            String sex = pf.getOrDefault("gender", "").isEmpty() ? "Male" : pf.get("gender");
            String age = pf.getOrDefault("age", "").isEmpty() ? "40 Years" : pf.get("age");
            String regNo = "DC" + String.format("%06d", System.currentTimeMillis() % 1000000);
            bodyRes = dc.fillTemplateBlanks("Malaysia", regNo, name, sex, age, today, timeNow, "KPJ Hospital",
                    "Natural causes (automated test)", "Malaysian", "Tester", "12 Jalan Test, Kuala Lumpur",
                    "Ahmad Auto Test", "Siti Auto Test", today);
            if (bodyRes.startsWith("(no-blanks")) {
                bodyRes = dc.enterTemplateBody(bodyText) + " — the template has no blanks to fill, so the text was written instead";
            }
        }
        boolean bodyOk = !bodyRes.startsWith("(") && (customBody || bodyRes.contains("model synced=true")
                || bodyRes.contains("n/a (no model)") || bodyRes.contains("so the text was written instead"));
        step(page, "Enter details in the form template body",
                "Fill the certificate body (a CKEditor, not a textarea): Government of, Registration No., Name of Deceased, Sex, Age, Date / Time / "
                        + "Place / Cause of Death, Nationality, Occupation, Address, Name of Father / Mother, Date of Registration",
                "The template body holds the details and the form's data carries them",
                bodyOk ? "PASSES: " + bodyRes : "FAILS — the body was not written or did not reach the form's data: " + bodyRes,
                bodyOk ? "PASS" : "FAIL");

        // 6) Department + doctor
        String deptDoc = dc.selectDepartmentAndDoctor();
        boolean ddOk = !deptDoc.contains("(no-option)") && !deptDoc.contains("(no-field)");
        step(page, "Select department and doctor", "Select the Department then the Doctor",
                "Both are selected", deptDoc, ddOk ? "PASS" : "FAIL");

        // 7) Authenticate
        boolean auth = dc.tickAuthenticate();
        step(page, "Tick Authenticate", "Tick the Authenticate checkbox",
                "Authenticate is ticked",
                auth ? "Authenticate ticked" : "Authenticate checkbox not found / not ticked",
                auth ? "PASS" : "FAIL");

        // 8) Save -> toast. Count the tabs first: Save is supposed to generate the certificate report,
        // which DevHIS opens in a new one.
        int tabsBefore = page.context().pages().size();
        String toast = dc.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty()
                ? "No toast appeared — " + dc.lastSaveDiagnostics
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        // 9) Save must generate the certificate report — and a blank one is a failure.
        // The report opens as a PDF in ANOTHER tab and is judged on the file itself, read from inside the browser (PdfReport) — not on a
        // screenshot of Chrome's PDF viewer, which is white however good the report is. lastReportPng is page 1 of the PDF rendered to an
        // image and is attached to this step, so the generated report shows in the test report.
        String reportInfo = dc.captureReport(tabsBefore, 15000);
        addSummary("Report", reportInfo);
        boolean reportOk = dc.reportProduced() && !dc.reportBlank;
        stepWithShot(dc.lastReportPng, "Save generates the certificate report",
                "After Save, the death certificate report is generated",
                "A report opens and carries the certificate's content",
                (reportOk
                    ? "PASSES because a report was generated and it carries content: " + reportInfo
                    : dc.reportProduced()
                    ? "FAILS because the report generated by Save is BLANK — it opens, but there is "
                      + "nothing on it, so the certificate cannot be printed or issued to the family. Judged on the file itself "
                      + "(its size and content streams), not on a screenshot of the PDF viewer. Evidence: " + reportInfo
                    : "FAILS because Save generated NO report at all: " + reportInfo),
                reportOk ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Patient attached", attached ? "yes" : "NO — " + dc.lastFormSearch);
        addSummary("Template", dc.lastTemplate);
        addSummary("Department / Doctor", dc.lastDepartment + " / " + dc.lastDoctor);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }

    /** Records a step with the given picture (the report tab), or with the app tab when no picture could be taken. */
    private void stepWithShot(byte[] png, String name, String desc, String expected, String actual, String status) {
        if (png != null && png.length > 0) step(png, name, desc, expected, actual, status);
        else step(page, name, desc, expected, actual, status);
    }
}
