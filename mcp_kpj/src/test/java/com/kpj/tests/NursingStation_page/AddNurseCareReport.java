package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AddNurseCareReport — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; Nursing Console &gt; <b>Add Nurse Care Report</b>.
 *
 * <ol>
 *   <li>Open <b>Nursing Station</b> → <b>Nursing Console</b> and tick a patient (the console only opens a report for a
 *       ticked patient — it answers "Please Select Patient!" otherwise).</li>
 *   <li>Click <b>Add Nurse Care Report</b>.</li>
 *   <li>Select a <b>Status Tag</b>; enter the <b>Subjective Data</b>.</li>
 *   <li><b>Diagnosis</b>: search "fever" and select one of the suggestions; if nothing appears, type a suitable name and
 *       click <b>Add</b> to create a new entry. <b>Outcomes</b>, <b>Interventions</b>, <b>Rationales</b> and
 *       <b>Evaluation</b> use the same logic.</li>
 *   <li>Enter the <b>Recommendation</b>, click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>This test SAVES a real nurse care report for the first patient of the ward, and any name typed as a new entry is
 * added to that section's list. Each run adds one more report.</p>
 */
public class AddNurseCareReport extends DevHisBase {

    // Fixed, meaningful names (no timestamps): a later run finds them in the suggestions instead of adding duplicates.
    static final String SUBJECTIVE = "Patient reports feeling feverish with chills since last night.";
    static final String DIAGNOSIS = "fever";
    static final String OUTCOME = "Body temperature returns to normal range";
    static final String INTERVENTION = "Monitor temperature every 4 hours and give antipyretic as prescribed";
    static final String RATIONALE = "Regular monitoring detects changes early and the antipyretic lowers the fever";
    static final String EVALUATION = "Temperature within normal range after intervention";
    static final String RECOMMENDATION = "Continue to monitor temperature and encourage oral fluids.";

    public AddNurseCareReport() { super("NursingStation_AddNurseCareReport"); }

    public static void main(String[] args) {
        AddNurseCareReport t = new AddNurseCareReport();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Nursing Console - Add Nurse Care Report",
                "Nursing Station > Nursing Console > Add Nurse Care Report",
                "Tick a patient, open Add Nurse Care Report, pick a Status Tag, enter the Subjective Data, fill Diagnosis / "
                        + "Outcomes / Interventions / Rationales / Evaluation (pick a suggestion, or type a new entry and click Add), "
                        + "enter the Recommendation and Save; verify the success toast. SAVES a real report.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.AddNurseCareReport nc =
                new com.kpj.pages.NursingStation_page.AddNurseCareReport(page);

        // 1) Nursing Station > Nursing Console
        boolean console = nc.navigateViaMenu(BASE);
        step(page, "Open Nursing Console", "Click Nursing Station -> Nursing Console",
                "The Nursing Console patient list is shown",
                console ? "Opened " + page.url() + (nc.lastRoute.isEmpty() ? "" : " (menu route " + nc.lastRoute + ")")
                          + ". The plain Nursing Console is used, not OPD Nursing Console."
                        : "Did NOT reach the screen (" + page.url() + ")",
                console ? "PASS" : "FAIL");
        if (!console) { addSummary("Result", "FAILED — Nursing Console not reached"); return; }

        // 2) a patient has to be ticked before a report can be added
        String station = nc.ensurePatients();
        String ticked = nc.tickFirstPatient();
        boolean tickOk = nc.patientTicked;
        step(page, "Select a patient", "Tick a patient in the Nursing Station's list (needed before Add Nurse Care Report)",
                "A patient is ticked", "Station " + station + "; " + ticked, tickOk ? "PASS" : "FAIL");
        if (!tickOk) { addSummary("Result", "FAILED — no patient to select in " + station); return; }

        // 3) Add Nurse Care Report
        String blocked = nc.clickAddNurseCareReport();
        boolean form = blocked.isEmpty() && nc.onAddScreen();
        String banner = form ? nc.patientBanner() : "";
        step(page, "Click Add Nurse Care Report", "Click Add Nurse Care Report (fnAddNurseCareReport)",
                "The Add Nurse Care Report form is shown for the ticked patient",
                form ? "Form opened (" + page.url() + ") for " + banner + "; Evaluation Date and Time pre-filled with " + nc.lastEvaluationDate
                     : "Form did not open" + (blocked.isEmpty() ? "" : " — the console said: \"" + blocked + "\""),
                form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED — report form not opened: " + blocked); return; }

        // 4) Status Tag
        String status = nc.selectStatusTag();
        boolean statusOk = !status.isEmpty() && !status.startsWith("(") && nc.statusTagBound();
        step(page, "Select status tag", "Select a Status Tag", "A status tag is selected",
                statusOk ? "Selected \"" + status + "\" and the model holds it" : "No status tag selected: " + status,
                statusOk ? "PASS" : "FAIL");

        // 5) Subjective data
        boolean subj = nc.fillSubjective(SUBJECTIVE);
        step(page, "Enter subjective data", "Enter the Subjective Data", "The Subjective Data is entered",
                subj ? "Entered \"" + SUBJECTIVE + "\" and the model holds it" : "The Subjective Data did not stay in the box",
                subj ? "PASS" : "FAIL");

        // 6-10) Diagnosis, Outcomes, Interventions, Rationales, Evaluation — same search-or-add logic
        fillSection(nc, "Diagnosis", DIAGNOSIS, "^\\S+ - Fever, unspecified$", "Enter diagnosis",
                "Search \"" + DIAGNOSIS + "\" in Diagnosis and select one of the diagnoses that appear");
        fillSection(nc, "Outcomes", OUTCOME, null, "Enter outcomes",
                "Search Outcomes; select a suggestion, or type a name and click Add");
        fillSection(nc, "Interventions", INTERVENTION, null, "Enter interventions",
                "Search Interventions; select a suggestion, or type a name and click Add");
        fillSection(nc, "Rationales", RATIONALE, null, "Enter rationales",
                "Search Rationales; select a suggestion, or type a name and click Add");
        fillSection(nc, "Evaluation", EVALUATION, null, "Enter evaluation",
                "Search Evaluation; select a suggestion, or type a name and click Add");

        // 11) Recommendation
        boolean rec = nc.fillRecommendation(RECOMMENDATION);
        step(page, "Enter recommendation", "Enter the Recommendation", "The Recommendation is entered",
                rec ? "Entered \"" + RECOMMENDATION + "\" and the model holds it" : "The Recommendation did not stay in the box",
                rec ? "PASS" : "FAIL");

        // 12) Save + success toast
        String toast = nc.clickSaveAndGetToast();
        boolean ok = nc.saveSucceeded();
        step(page, "Click Save", "Click Save (fnSave)", "The report is saved",
                nc.lastSave + (toast.isEmpty() ? "; no toast appeared" : "; the screen answered \"" + toast + "\""),
                toast.isEmpty() ? "FAIL" : "PASS");
        step(page, "Verify success toast message", "Read the toast shown after Save",
                "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\""
                   : (toast.isEmpty() ? "FAILS because no toast appeared"
                                      : "FAILS because the toast is not a success message: \"" + toast + "\""),
                ok ? "PASS" : "FAIL");

        addSummary("Nursing station", nc.lastStation);
        addSummary("Patient", banner);
        addSummary("Status tag", nc.lastStatus);
        for (String s : com.kpj.pages.NursingStation_page.AddNurseCareReport.SECTIONS)
            addSummary(s, String.valueOf(nc.sectionResult.get(s)));
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }

    /** One tag section as one step: search; pick a suggestion or add the typed name; confirm the chip. */
    private void fillSection(com.kpj.pages.NursingStation_page.AddNurseCareReport nc, String section, String term,
                             String preferRe, String title, String action) {
        String result = nc.fillSection(section, term, preferRe);
        boolean ok = Boolean.TRUE.equals(nc.sectionOk.get(section));
        step(page, title, action, "The " + section + " entry is added and listed in its section",
                ok ? "PASSES because the " + section + " section now lists it — " + result
                   : "FAILS because the " + section + " section does not list it (or it is unticked) — " + result,
                ok ? "PASS" : "FAIL");
    }
}
