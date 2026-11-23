package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PostMortem — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Post Mortem Report</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Nursing Station</b> → <b>Post Mortem Report</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>MRN</b> and click <b>Search</b>.</li>
 *   <li>Select the <b>post mortem template</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p><b>MRN.</b> Unless pinned with {@code -Dmrn=}, one is harvested from the post mortem list — those
 * patients are already accepted by this screen. A post mortem plausibly requires a <b>deceased</b>
 * patient, so a general-purpose MRN is unlikely to attach (this is exactly what blocks the sibling Birth
 * Certificate flow).</p>
 *
 * <p>&#9888; A successful run CREATES a real post mortem record in the target environment.</p>
 */
public class PostMortem extends DevHisBase {

    /** Used when {@code -Dmrn=} is not supplied and the list yields nothing. */
    public static final String DEFAULT_MRN = "100000956";

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public PostMortem() { super("NursingStation_PostMortem"); }

    public static void main(String[] args) {
        PostMortem t = new PostMortem();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Post Mortem Report", "Nursing Station > Post Mortem Report",
                "&#9888; Creates a REAL post mortem record: Add, search the patient by MRN, select the "
                        + "template, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String mrn = System.getProperty("mrn", DEFAULT_MRN);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.PostMortem pm =
                new com.kpj.pages.NursingStation_page.PostMortem(page);

        // 1) Navigate — escalates menu click -> hash route -> full page load.
        boolean rendered = pm.navigateViaMenu(BASE);
        step(page, "Open Post Mortem Report screen",
                "Click Nursing Station -> Post Mortem Report (retrying via the route and a full page load)",
                "The Post Mortem Report screen is shown",
                rendered ? "Opened " + page.url()
                         : "Route resolved but rendered NO screen. Body: \"" + pm.lastBodyText + "\"",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 1b) Pick an MRN the screen already accepts, unless one was pinned.
        if (System.getProperty("mrn") == null) {
            java.util.List<String> harvested = pm.harvestMrnsFromList();
            if (!harvested.isEmpty()) mrn = harvested.get(0);
            addSummary("MRN source", harvested.isEmpty()
                    ? "default (the post mortem list is empty)" : "harvested from the post mortem list");
        } else {
            addSummary("MRN source", "-Dmrn");
        }

        // 2) Add
        boolean formOpen = pm.clickAdd();
        pm.describeControls();
        step(page, "Click Add", "Click Add to open the post mortem form",
                "The Post Mortem form opens",
                formOpen ? "Form opened (" + page.url() + ")" : "Form did NOT open (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — post mortem form not reached"); return; }

        // 3) MRN + Search — verified against the model, not just the field. Candidates are tried in turn
        //    and, failing those, the screen's own patient lookup supplies MRNs this environment has.
        com.kpj.pages.MrnRetry.Result found = com.kpj.pages.MrnRetry.resolve(
                "PostMortem", page,
                com.kpj.pages.MrnRetry.candidates(DEFAULT_MRN),
                pm::searchByMrn, pm::patientAttached, 10);
        String searchResult = found.lastSearch;
        boolean attached = found.attached();
        if (attached) mrn = found.mrn;
        addSummary("MRNs tried", found.attempts);

        step(page, "Enter MRN & click Search",
                "Enter an MRN and click the search icon (SearchPatientByMRNo), trying candidates until a "
                        + "patient attaches",
                "The patient is attached to the post mortem record",
                attached ? "MRN " + mrn + " attached. " + searchResult
                         : "No patient attached with any MRN. Tried: " + found.attempts,
                attached ? "PASS" : "FAIL");

        // 4) Template
        String template = pm.selectTemplate();
        boolean templateOk = pm.templateSelected();
        step(page, "Select post mortem template",
                "Select the template (ng-model PostMortemTemplate.Dischargeid)",
                "A post mortem template is selected",
                templateOk ? "Template = " + template : "Template NOT selected " + template,
                templateOk ? "PASS" : "FAIL");

        // 5) Save -> toast
        String toast = pm.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty()
                ? "No toast appeared — " + pm.lastSaveDiagnostics
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast",
                "Click Save (IUDPostMortemDetail); wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Patient attached", attached ? "yes" : "NO — " + pm.lastFormSearch);
        addSummary("Template", pm.lastTemplate);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
