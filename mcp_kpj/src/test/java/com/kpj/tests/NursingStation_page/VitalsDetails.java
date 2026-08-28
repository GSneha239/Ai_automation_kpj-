package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named VitalsDetails — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Vitals Details</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Nursing Station</b> → <b>Vitals Details</b>.</li>
 *   <li>Enter the <b>MRN</b> and click <b>Search</b>.</li>
 *   <li>In <b>Add Vital Details</b>, tick the <b>Select</b> checkboxes.</li>
 *   <li>Enter the <b>Value</b> on each selected row.</li>
 *   <li>Enter the <b>Remarks</b> on each selected row.</li>
 *   <li>Select <b>Taken By</b>.</li>
 *   <li>Click <b>Telemedicine Import</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>Knobs: {@code -Dmrn=} the patient (default {@link #DEFAULT_MRN}), {@code -Dvitals=} how many rows to
 * tick (default 3), {@code -Dvalue=} the reading, {@code -Dremark=} the remark text.</p>
 *
 * <h2>Telemedicine Import fails, and it can wipe the form</h2>
 * <p><b>Telemedicine Import ({@code GetQueedMessages}) answers "Error in getting record!"</b> — reproduced
 * on three consecutive runs, so it is a real server-side failure rather than a flake. Everything around it
 * works, Save included, which is why it is reported as its own failing step rather than being allowed to
 * sink the run.</p>
 *
 * <p>It also <b>reloads the vital grid</b>, and that discards the ticks, values and remarks entered before
 * it — after which Save answers "Please select at least one parameter!". A run once passed Save and the
 * next failed it for exactly this reason. The flow therefore checks whether the entries survived the import
 * and re-applies them if not, so Save is exercised on real data instead of failing for a reason unrelated
 * to saving. The report says which of the two happened.</p>
 *
 * <p>&#9888; A successful run RECORDS REAL vital signs against the patient in the target environment.</p>
 */
public class VitalsDetails extends DevHisBase {

    /** Used when {@code -Dmrn=} is not supplied. Override per environment. */
    public static final String DEFAULT_MRN = "100000956";

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public VitalsDetails() { super("NursingStation_VitalsDetails"); }

    public static void main(String[] args) {
        VitalsDetails t = new VitalsDetails();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Vitals Details", "Nursing Station > Vitals Details",
                "&#9888; Records REAL vital signs: search the patient by MRN, tick vitals, enter value + "
                        + "remarks, pick Taken By, Telemedicine Import, Save.");

        String mrn = System.getProperty("mrn", DEFAULT_MRN);
        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        int vitals = Integer.getInteger("vitals", 3);
        String value = System.getProperty("value", "36");
        String remark = System.getProperty("remark", "Automated test reading");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.VitalsDetails vd =
                new com.kpj.pages.NursingStation_page.VitalsDetails(page);

        // 1) Navigate
        boolean onScreen = vd.navigateViaMenu();
        step(page, "Open Vitals Details screen", "Click Nursing Station -> Vitals Details",
                "The Vitals Details screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        vd.describeControls();   // diagnostics only

        // 2) MRN + Search. Candidates are tried in turn and, failing those, the screen's own patient
        //    lookup supplies MRNs this environment has — a rejected MRN otherwise looks like a broken screen.
        com.kpj.pages.MrnRetry.Result hit = com.kpj.pages.MrnRetry.resolve(
                "VitalsDetails", page,
                com.kpj.pages.MrnRetry.candidates(DEFAULT_MRN),
                vd::searchByMrn, vd::patientLoaded, 10);
        String searchResult = hit.lastSearch;
        boolean found = hit.attached();
        if (found) mrn = hit.mrn;
        addSummary("MRNs tried", hit.attempts);

        step(page, "Enter MRN & click Search",
                "Enter an MRN, click the MRN search icon (SearchPatientByMRNo), then Search (VitalSearch), "
                        + "trying candidates until the patient loads",
                "The patient loads and the vitals list is shown",
                found ? "MRN " + mrn + " -> " + searchResult
                      : "Nothing loaded for any MRN. Tried: " + hit.attempts,
                found ? "PASS" : "FAIL");
        if (!found) {
            addSummary("MRN", mrn);
            addSummary("Result", "FAILED — nothing loaded for this MRN (pass a valid one with -Dmrn=...)");
            return;
        }

        // 3) Tick the Select checkboxes
        String ticked = vd.selectVitalRows(vitals);
        step(page, "Click the Select checkboxes",
                "Tick the Select checkbox on the first " + vitals + " vital rows",
                "The chosen vital rows are selected", ticked, vd.rowsSelected() ? "PASS" : "FAIL");
        if (!vd.rowsSelected()) { addSummary("Result", "FAILED — no vital row could be selected"); return; }

        // 4) Values
        String values = vd.enterValues(value);
        step(page, "Enter value", "Enter the value " + value + " on each selected row",
                "Every selected row has a value", values, vd.valuesEntered() ? "PASS" : "FAIL");

        // 5) Remarks
        String remarks = vd.enterRemarks(remark);
        step(page, "Enter remarks", "Enter the remarks on each selected row",
                "Every selected row has remarks", remarks, vd.remarksEntered() ? "PASS" : "FAIL");

        // 6) Taken By
        String takenBy = vd.selectTakenBy();
        step(page, "Select Taken By", "Select Taken By",
                "Taken By is selected",
                vd.takenBySelected() ? "Taken By = " + takenBy : "Taken By NOT selected " + takenBy,
                vd.takenBySelected() ? "PASS" : "FAIL");

        // 7) Telemedicine Import
        String imported = vd.clickTelemedicineImport();
        boolean importOk = vd.telemedicineImportOk();
        step(page, "Click Telemedicine Import", "Click Telemedicine Import (GetQueedMessages)",
                "The import completes without an error",
                imported == null || imported.isEmpty() ? "No response observed"
                        : (importOk ? imported : "Import FAILED — " + imported),
                importOk ? "PASS" : "FAIL");

        // Telemedicine Import RELOADS the vital grid, which discards the ticks, values and remarks entered
        // above — Save then answers "Please select at least one parameter!". That is worth recording as
        // screen behaviour, and the entries are re-applied so Save is still exercised on real data rather
        // than failing for a reason that has nothing to do with saving.
        boolean survived = vd.rowsSelected() && vd.valuesEntered();
        String reapplied = "";
        if (!survived) {
            reapplied = vd.selectVitalRows(vitals) + " | " + vd.enterValues(value)
                    + " | " + vd.enterRemarks(remark);
            if (!vd.takenBySelected()) vd.selectTakenBy();
        }
        step(page, "Re-enter the vitals after the import",
                "Check whether the import kept the selected rows, and re-apply them if not",
                "The selected rows, values and remarks are still present before Save",
                survived
                    ? "The import kept the entries — nothing to re-apply."
                    : "The import CLEARED the selections (it reloads the grid), so they were re-applied "
                      + "before Save: " + reapplied,
                survived ? "PASS" : "MANUAL");

        // 8) Save -> toast
        String toast = vd.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast",
                "Click Save (addselectedvitalsdetails); wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Vitals selected", vd.lastSelectedRows);
        addSummary("Value / Remarks", value + " / " + remark);
        addSummary("Taken By", vd.lastTakenBy);
        addSummary("Telemedicine Import", vd.lastImportResult);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
