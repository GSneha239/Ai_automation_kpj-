package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named IntakeOutputChart — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Intake Output Chart</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Nursing Station</b> → <b>Intake Output Chart</b>.</li>
 *   <li>Enter the <b>MRN</b> and click <b>Search</b>.</li>
 *   <li>Enter <b>Oral/NG</b>.</li>
 *   <li>Enter <b>IV/SC</b>.</li>
 *   <li>Enter <b>Intake Others</b>, <b>Headache</b>, <b>Oral Fluid Intake</b>, <b>Total Intake</b>,
 *       <b>Urine</b>, <b>Vomit</b>, <b>Bowel</b> and <b>Tube</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>Knobs: {@code -Dmrn=} the patient (default {@link #DEFAULT_MRN}), {@code -Dvalue=} the amount written
 * into every row (default 100).</p>
 *
 * <p>&#9888; A successful run RECORDS a REAL intake/output entry against the patient.</p>
 */
public class IntakeOutputChart extends DevHisBase {

    /** Used when {@code -Dmrn=} is not supplied. Override per environment. */
    public static final String DEFAULT_MRN = "100000956";

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /** The chart rows this flow fills, in the order requested. */
    private static final java.util.List<String> INTAKE_ROWS = java.util.List.of("Oral/NG", "IV/SC");
    private static final java.util.List<String> REMAINING_ROWS = java.util.List.of(
            "Intake Others", "Headache", "Oral Fluid Intake", "Total Intake",
            "Urine", "Vomit", "Bowel", "Tube");

    public IntakeOutputChart() { super("NursingStation_IntakeOutputChart"); }

    public static void main(String[] args) {
        IntakeOutputChart t = new IntakeOutputChart();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Intake Output Chart", "Nursing Station > Intake Output Chart",
                "&#9888; Records a REAL intake/output entry: search the patient by MRN, fill the intake and "
                        + "output rows, Save.");

        String mrn = System.getProperty("mrn", DEFAULT_MRN);
        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String value = System.getProperty("value", "100");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.IntakeOutputChart io =
                new com.kpj.pages.NursingStation_page.IntakeOutputChart(page);

        // 1) Navigate
        boolean onScreen = io.navigateViaMenu();
        step(page, "Open Intake Output Chart screen", "Click Nursing Station -> Intake Output Chart",
                "The Intake Output Chart screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        io.describeControls();

        // 2) MRN + Search. Candidates are tried in turn and, failing those, the screen's own patient
        //    lookup supplies MRNs this environment has.
        com.kpj.pages.MrnRetry.Result hit = com.kpj.pages.MrnRetry.resolve(
                "IntakeOutputChart", page,
                com.kpj.pages.MrnRetry.candidates(DEFAULT_MRN),
                io::searchByMrn, io::chartLoaded, 10);
        String searchResult = hit.lastSearch;
        boolean loaded = hit.attached();
        if (loaded) mrn = hit.mrn;
        addSummary("MRNs tried", hit.attempts);

        step(page, "Enter MRN & click Search",
                "Enter an MRN and click the search icon (SearchPatientByMRNo), trying candidates until the "
                        + "chart loads",
                "The patient's intake/output chart loads",
                loaded ? "MRN " + mrn + " -> " + searchResult
                       : "The chart did not load for any MRN. Tried: " + hit.attempts,
                loaded ? "PASS" : "FAIL");
        if (!loaded) {
            addSummary("MRN", mrn);
            addSummary("Result", "FAILED — the chart did not load (pass a valid MRN with -Dmrn=...)");
            return;
        }

        // The label -> cell map: these cells all share one ng-model, so this is how they are addressed.
        String chartMap = io.describeChart();
        addSummary("Chart rows offered", chartMap);

        // 3) Oral/NG
        String oral = io.enterValue("Oral/NG", value);
        boolean oralOk = !oral.startsWith("(");
        step(page, "Enter Oral/NG", "Enter " + value + " in the Oral/NG row",
                "Oral/NG holds the value",
                oralOk ? "Oral/NG = " + oral : "Oral/NG NOT set " + oral, oralOk ? "PASS" : "FAIL");

        // 4) IV/SC
        String iv = io.enterValue("IV/SC", value);
        boolean ivOk = !iv.startsWith("(");
        step(page, "Enter IV/SC", "Enter " + value + " in the IV/SC row",
                "IV/SC holds the value",
                ivOk ? "IV/SC = " + iv : "IV/SC NOT set " + iv, ivOk ? "PASS" : "FAIL");

        // 5) The remaining rows
        String rest = io.enterValues(REMAINING_ROWS, value);
        // A row the app COMPUTES (read-only, e.g. Total Intake) cannot be typed into — that is correct app
        // behaviour, not a defect, so it is reported as MANUAL rather than failed. A row that does not
        // exist on the chart at all IS a real mismatch and fails.
        String computed = io.computedRows();
        String missing = io.missingRows();
        String restStatus = !missing.isEmpty() ? "FAIL" : (computed.isEmpty() ? "PASS" : "MANUAL");
        step(page, "Enter the remaining intake/output rows",
                "Enter " + value + " in: " + String.join(", ", REMAINING_ROWS),
                "Every requested row holds the value",
                rest
                        + (computed.isEmpty() ? "" : "  ||  COMPUTED BY THE APP, not enterable: " + computed)
                        + (missing.isEmpty() ? "" : "  ||  NOT ON THIS CHART: " + missing),
                restStatus);

        // 6) Save -> toast
        String toast = io.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast",
                "Click Save (IUDIntakeoutputDetails); wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Value written", value);
        int wanted = INTAKE_ROWS.size() + REMAINING_ROWS.size();
        addSummary("Rows filled", io.filledCount() + " / " + wanted
                + (computed.isEmpty() ? "" : "  (computed by the app: " + computed + ")")
                + (missing.isEmpty() ? "" : "  (not on this chart: " + missing + ")"));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
