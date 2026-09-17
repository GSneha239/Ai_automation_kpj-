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
        com.kpj.pages.MrnRetry.Result found = com.kpj.pages.MrnRetry.resolve(
                "DeathCertificate", page,
                com.kpj.pages.MrnRetry.candidates(DEFAULT_MRN),
                dc::searchByMrn, dc::patientAttached, 10);
        String searchResult = found.lastSearch;
        boolean attached = found.attached();
        if (attached) mrn = found.mrn;
        addSummary("MRNs tried", found.attempts);

        step(page, "Enter MRN & click Search",
                "Enter an MRN and click the search icon (SearchPatientByMRNo), trying candidates until a "
                        + "patient attaches",
                "The patient is attached to the certificate",
                attached ? "MRN " + mrn + " attached. " + searchResult
                         : "No patient attached with any MRN. Tried: " + found.attempts,
                attached ? "PASS" : "FAIL");

        // 4) Certificate template
        String template = dc.selectTemplate();
        boolean templateOk = !template.startsWith("(");
        step(page, "Select certificate template", "Select the Certificate Template",
                "A certificate template is selected",
                templateOk ? "Template = " + template : "Template NOT selected " + template,
                templateOk ? "PASS" : "FAIL");

        // 5) Form template body (CKEditor)
        String bodyRes = dc.enterTemplateBody(bodyText);
        boolean bodyOk = !bodyRes.startsWith("(");
        step(page, "Enter details in the form template body",
                "Type into the certificate body (a CKEditor, not a textarea)",
                "The template body holds the text",
                bodyOk ? "Body written via " + bodyRes : "Could not write the body " + bodyRes,
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
        com.kpj.pages.ReportCheck rc = com.kpj.pages.ReportCheck.after(page, tabsBefore, 15000,
                "Nursing Station - Death Certificate - generated report.png");
        addSummary("Report", rc.diagnostics);
        boolean reportOk = rc.produced() && !rc.blank;
        step(page, "Save generates the certificate report",
                "After Save, the death certificate report is generated",
                "A report opens and carries the certificate's content",
                (reportOk
                    ? "PASSES because a report was generated and it carries content: " + rc.diagnostics
                    : rc.produced()
                    ? "FAILS because the report generated by Save is BLANK — it opens, but there is "
                      + "nothing on it, so the certificate cannot be printed or issued to the family. "
                      + "Judged on the ink inside the printed page (a PDF viewer exposes no text, so an "
                      + "empty-text check would call every report blank, and counting the whole "
                      + "screenshot counts the viewer's own toolbar as content). Evidence: "
                      + rc.diagnostics
                    : "FAILS because Save generated NO report at all: " + rc.diagnostics),
                reportOk ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Patient attached", attached ? "yes" : "NO — " + dc.lastFormSearch);
        addSummary("Template", dc.lastTemplate);
        addSummary("Department / Doctor", dc.lastDepartment + " / " + dc.lastDoctor);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
