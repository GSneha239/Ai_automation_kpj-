package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named OutcomeMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Outcome Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Outcome Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Outcome Description</b>; choose the <b>Diagnosis Description</b> from the list.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 *   <li>Verify the entered values in the table.</li>
 * </ol>
 *
 * <p>The Code and Outcome Description are generated per run (the shared QA environment keeps every record ever saved) and a different
 * set — including a different entry of the diagnosis list — is used if the server answers "already exists".</p>
 *
 * <p>The Diagnosis Description is a search box: it is typed into and an entry is CLICKED in its list, as a user does. See the page
 * object for why setting it through the model cannot work.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL outcome master in the target environment.</p>
 */
public class OutcomeMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /** How many times to re-enter fresh details when the server says the code / description already exists. */
    private static final int MAX_ATTEMPTS = 10;

    public OutcomeMaster() { super("ApplicationConfig_Nursing_OutcomeMaster"); }

    public static void main(String[] args) {
        OutcomeMaster t = new OutcomeMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Outcome Master", "Application Configuration > Nursing > Outcome Master",
                "&#9888; Creates a REAL outcome master: Add, enter Code and Outcome Description, choose the Diagnosis Description from the list, Save, check the toast and the table.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.OutcomeMaster om =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.OutcomeMaster(page);

        // 1) Navigate
        boolean rendered = om.navigateViaMenu();
        if (!rendered) addSummary("Menu offered", om.lastMenu);
        step(page, "Open Outcome Master screen",
                "Click Application Configuration -> Nursing -> Outcome Master",
                "The Outcome Master list screen is shown",
                rendered ? "Opened " + page.url()
                           + (om.lastRoute.isEmpty() ? "" : " (menu route " + om.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }
        addSummary("List screen controls", om.describeControls());

        // 2) Add
        boolean added = om.clickAdd();
        step(page, "Click Add", "Click Add", "The Outcome Master entry form opens",
                added ? om.lastAdd : "FAILS — " + om.lastAdd, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — the add form did not open"); return; }
        addSummary("Add form controls", om.describeControls());

        // 3) Code + Outcome Description, 4) Diagnosis Description from the list, 5) Save -> toast.
        // The steps are recorded after the retry loop, so each screenshot is taken in the loop at the moment it is meaningful: the form with
        // its values BEFORE Save (afterwards the form is cleared) and the page right after the toast was read (toasts fade).
        String entry = "", diagnosis = "", toast = "";
        boolean entered = false, ok = false, duplicate = false;
        int used = 0;
        byte[] enteredShot = null, diagShot = null, toastShot = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            entry = om.fillDetails(attempt);
            entered = om.detailsEntered();
            used++;
            enteredShot = shot();
            if (!entered) break;                        // nothing sensible to save — report the entry failure
            diagnosis = om.selectDiagnosis(attempt);
            diagShot = shot();
            if (diagnosis.isEmpty()) break;             // Diagnosis Description is mandatory — report why it could not be chosen
            toast = om.saveAndGetToast();
            toastShot = shot();
            ok = com.kpj.pages.ApplicationConfiguration_page.Nursing_page.OutcomeMaster.isSuccess(toast);
            duplicate = com.kpj.pages.ApplicationConfiguration_page.Nursing_page.OutcomeMaster.isDuplicate(toast);
            if (ok || !duplicate) break;
            System.out.println("OutcomeMaster: attempt " + used + " (" + entry + " / " + diagnosis + ") already exists — changing the details");
        }
        String code = om.lastCode, outcome = om.lastOutcome;

        stepShot(enteredShot, "Enter code and outcome description",
                "Enter the Code " + code + " and the Outcome Description " + outcome,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry)
                    + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                entered ? "PASS" : "FAIL");
        if (!entered) { addSummary("Result", "FAILED — the details could not be entered"); return; }

        boolean diagOk = !diagnosis.isEmpty();
        stepShot(diagShot, "Select the diagnosis description from the list",
                "Search the Diagnosis Description and choose an entry from the list",
                "A diagnosis is chosen from the list",
                diagOk ? "PASSES because an entry was chosen from the list: " + om.lastDiagnosisDiag
                       : "FAILS — " + om.lastDiagnosisDiag,
                diagOk ? "PASS" : "FAIL");
        if (!diagOk) { addSummary("Result", "FAILED — no Diagnosis Description could be chosen from the list"); return; }

        // A message containing "undefined" FAILS the step even though the save goes through: the record identifier was never
        // substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + om.lastSaveDiagnostics
                : (ok ? toast
                      : duplicate ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                  : "Save not confirmed — server returned: \"" + toast + "\"");
        stepShot(toastShot, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen cannot name what it saved."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        // 6) The table — checked even when the toast was not a success, so a silent save is reported as such rather than hidden.
        boolean inList = om.verifyInTable(diagnosis);
        addSummary("List check", om.lastListCheck);
        step(page, "Verify the entered values in the table",
                "Open the list; filter the Code column by " + code + "; check the Code, Outcome Desc and Diagnosis Description of the row",
                "The new row shows the entered Code, Outcome Description and Diagnosis Description",
                (inList
                    ? "PASSES because the saved record — Code, Outcome Description AND Diagnosis Description — was found in the table: " + om.lastListCheck
                    : "FAILS because the saved record could not be found in the table with those values. " + om.lastListCheck
                        + (ok ? "" : "  (the Save was not confirmed either)")),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Outcome Description", code + " / " + outcome);
        addSummary("Diagnosis Description", diagnosis);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }

    private byte[] shot() {
        try { return page.screenshot(); } catch (Exception e) { return null; }
    }

    /** Records a step with the given screenshot, or with a fresh one of the page when none could be taken. */
    private void stepShot(byte[] png, String name, String desc, String expected, String actual, String status) {
        if (png != null && png.length > 0) step(png, name, desc, expected, actual, status);
        else step(page, name, desc, expected, actual, status);
    }
}
