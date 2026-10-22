package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ViewNurseCareReport — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; Nursing Console &gt; <b>View Nurse Care Report</b>.
 *
 * <ol>
 *   <li>Open <b>Nursing Station</b> → <b>Nursing Console</b>; tick the patient with MRN <b>1000338160</b>.</li>
 *   <li>Click <b>View Nurse Care Report</b>.</li>
 *   <li>In the <b>Action</b> column click <b>View</b> (eye); click <b>Back</b>.</li>
 *   <li>Click <b>Edit</b> (pencil); check through the form; edit a detail; click <b>Save</b>; verify the success toast.</li>
 *   <li>Click <b>Back</b> to return to the Nursing Console.</li>
 * </ol>
 *
 * <p>The Action icons are taken from the NEWEST report in the list (the top row). The detail edited is the
 * <b>Recommendation</b>: a "(Reviewed &lt;time&gt;)" note is appended — replacing the note of an earlier run — so every
 * run makes a real change. One extra, read-only check follows the toast: the same report is opened with View and its
 * Recommendation read back, because a toast alone does not prove the edit was stored.</p>
 *
 * <p>This test CHANGES a stored report (its Recommendation). The patient comes from {@code -Dmrn=} (default
 * 1000338160). It needs at least one report to exist for the patient — run {@code AddNurseCareReport} first if there
 * is none.</p>
 */
public class ViewNurseCareReport extends DevHisBase {

    static final String DEFAULT_MRN = "1000338160";

    public ViewNurseCareReport() { super("NursingStation_ViewNurseCareReport"); }

    public static void main(String[] args) {
        ViewNurseCareReport t = new ViewNurseCareReport();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Nursing Console - View Nurse Care Report",
                "Nursing Station > Nursing Console > View Nurse Care Report",
                "Tick the patient (MRN 1000338160), open View Nurse Care Report, View (eye) the newest report and go Back, "
                        + "Edit (pencil) it, check the form, edit the Recommendation, Save and verify the success toast, "
                        + "then Back to the Nursing Console. CHANGES a stored report.");

        String mrn = System.getProperty("mrn", DEFAULT_MRN);

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.ViewNurseCareReport vc =
                new com.kpj.pages.NursingStation_page.ViewNurseCareReport(page);

        // 1) Nursing Station > Nursing Console
        boolean console = vc.navigateViaMenu(BASE);
        step(page, "Open Nursing Console", "Click Nursing Station -> Nursing Console",
                "The Nursing Console patient list is shown",
                console ? "Opened " + page.url() + ". The plain Nursing Console is used, not OPD Nursing Console."
                        : "Did NOT reach the screen (" + page.url() + ")",
                console ? "PASS" : "FAIL");
        if (!console) { addSummary("Result", "FAILED — Nursing Console not reached"); return; }

        // 2) tick the patient
        String ticked = vc.tickPatient(mrn);
        step(page, "Select the patient", "Tick the check box of the patient with MRN " + mrn,
                "The patient with MRN " + mrn + " is ticked", ticked, vc.patientTicked ? "PASS" : "FAIL");
        if (!vc.patientTicked) { addSummary("Result", "FAILED — patient " + mrn + " could not be selected"); return; }

        // 3) View Nurse Care Report
        String blocked = vc.clickViewNurseCareReport();
        boolean list = blocked.isEmpty() && vc.onList();
        int rows = list ? vc.reportRows() : 0;
        step(page, "Click View Nurse Care Report", "Click View Nurse Care Report (fnViewNurseCareReport)",
                "The patient's Nursing Care Plan List is shown with at least one report",
                list ? "List opened (" + page.url() + ") with " + rows + " report(s); newest: " + vc.rowText(0)
                     : "List did not open" + (blocked.isEmpty() ? "" : " — the console said: \"" + blocked + "\""),
                list && rows > 0 ? "PASS" : "FAIL");
        if (!list || rows == 0) {
            addSummary("Result", list ? "FAILED — the patient has no nurse care report (run AddNurseCareReport first)"
                                      : "FAILED — report list not opened: " + blocked);
            return;
        }
        String newest = vc.rowText(0);

        // 4) Actions > View (eye)
        String viewErr = vc.clickRowAction(0, "view");
        boolean viewed = viewErr.isEmpty() && vc.onForm();
        boolean readOnly = viewed && vc.formReadOnly();
        step(page, "Click View (eye) in the Actions column", "Click the eye icon in the Action column of the newest report",
                "The report opens read-only",
                viewed ? "Opened " + page.url() + " — " + vc.formState()
                         + (readOnly ? ": read-only, as expected for View." : ": NOT read-only.")
                       : "The report did not open — " + viewErr,
                readOnly ? "PASS" : "FAIL");
        if (!viewed) { addSummary("Result", "FAILED — View did not open the report: " + viewErr); return; }

        // 5) Back
        String back1 = vc.clickBack();
        boolean onList1 = vc.onList();
        step(page, "Click Back", "Click Back on the read-only report",
                "The report list is shown again",
                onList1 ? "Back on the list (" + back1 + ") with " + vc.reportRows() + " report(s)"
                        : "Did NOT return to the list — landed on " + back1,
                onList1 ? "PASS" : "FAIL");
        if (!onList1) { addSummary("Result", "FAILED — Back did not return to the list"); return; }

        // 6) Edit (pencil)
        String editErr = vc.clickRowAction(0, "edit");
        boolean editing = editErr.isEmpty() && vc.onForm() && vc.formEditable();
        step(page, "Click the Edit symbol", "Click the pencil icon in the Action column of the newest report",
                "The report opens in the editable form",
                editing ? "Opened " + page.url() + " — " + vc.formState()
                        : "The edit form did not open — " + (editErr.isEmpty() ? "form not editable (" + vc.formState() + ")" : editErr),
                editing ? "PASS" : "FAIL");
        if (!editing) { addSummary("Result", "FAILED — Edit did not open an editable form"); return; }

        // 7) check through the form
        String stored = vc.readForm();
        String recommendation = vc.field("Report.recommendation");
        boolean loaded = !vc.field("Report.statustagid").isEmpty() && !vc.field("Report.subjectivedata").isEmpty()
                && !recommendation.isEmpty();
        step(page, "Check through the form", "Read the stored values loaded into the Edit form",
                "The saved report's values are loaded",
                loaded ? "Loaded: " + stored : "Some stored values are missing: " + stored,
                loaded ? "PASS" : "FAIL");

        // 8) edit a detail — the Recommendation, with a fresh review note
        String note = " (Reviewed " + java.time.LocalTime.now().withNano(0) + ")";
        String edited = recommendation.replaceAll("(?i)\\s*\\(reviewed [^)]*\\)\\s*$", "") + note;
        boolean typed = vc.form().fillRecommendation(edited);
        step(page, "Edit a detail", "Change the Recommendation: add a review note",
                "The Recommendation shows the change",
                typed ? "Recommendation changed from \"" + recommendation + "\" to \"" + edited + "\" (and the model holds it)"
                      : "The new Recommendation did not stay in the box",
                typed ? "PASS" : "FAIL");

        // 9) Save + success toast
        String toast = vc.form().clickSaveAndGetToast();
        boolean ok = vc.form().saveSucceeded();
        boolean backOnList = vc.onList();
        step(page, "Click Save", "Click Save (fnSave)", "The edited report is saved",
                vc.form().lastSave + (toast.isEmpty() ? "; no toast appeared" : "; the screen answered \"" + toast + "\"")
                        + (backOnList ? "; the app then returned to the list (" + vc.reportRows() + " report(s))" : "; the app stayed on " + page.url()),
                toast.isEmpty() ? "FAIL" : "PASS");
        step(page, "Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\""
                   : (toast.isEmpty() ? "FAILS because no toast appeared"
                                      : "FAILS because the toast is not a success message: \"" + toast + "\""),
                ok ? "PASS" : "FAIL");

        // 10) extra, read-only: was the edit really stored?
        String storedCheck;
        boolean storedOk = false;
        if (!vc.onList()) {
            storedCheck = "not checked — the app is not on the report list (" + page.url() + ")";
        } else {
            String err = vc.clickRowAction(0, "view");
            if (!err.isEmpty() || !vc.onForm()) {
                storedCheck = "could not re-open the report with View — " + err;
            } else {
                String now = vc.field("Report.recommendation");
                storedOk = now.equals(edited.trim());
                storedCheck = storedOk ? "re-opened with View: the Recommendation reads \"" + now + "\""
                                       : "re-opened with View, but the Recommendation reads \"" + now + "\" — expected \"" + edited + "\"";
                vc.clickBack();   // back to the list; the View is read-only, nothing is changed
            }
        }
        step(page, "Verify the edit was stored (extra check)", "Open the same report with View and read the Recommendation back",
                "The Recommendation shows the edit",
                storedOk ? "PASSES because " + storedCheck : "FAILS because " + storedCheck, storedOk ? "PASS" : "FAIL");

        // 11) Back to the Nursing Console
        String backTo = vc.onList() ? vc.clickBack() : page.url().replaceAll("^.*#", "#");
        boolean consoleBack = vc.consoleReady();
        step(page, "Click Back — back to the Nursing Console", "Click Back on the report list",
                "The Nursing Console screen is shown",
                consoleBack ? "Back on the Nursing Console (" + backTo + "), station " + vc.consoleStation()
                              + ", " + vc.patientCount() + " patients listed"
                            : "Did NOT reach the Nursing Console — landed on " + page.url(),
                consoleBack ? "PASS" : "FAIL");

        addSummary("Patient (MRN)", mrn);
        addSummary("Report edited", newest);
        addSummary("Recommendation", edited);
        addSummary("Stored check", storedCheck);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
