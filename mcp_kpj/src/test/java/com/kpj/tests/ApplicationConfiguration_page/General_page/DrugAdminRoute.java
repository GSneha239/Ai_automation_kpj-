package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DrugAdminRoute — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>Drug Admin Route</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>Drug Admin Route</b>.</li>
 *   <li>Select the <b>Form Name</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Remark</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 *   <li>Search the table for the entered values.</li>
 * </ol>
 *
 * <p>This is the generic common-master screen (the same one as Quality Indicator): the Form Name list holds every common master and the
 * record is saved into the master that is picked. {@code -Dform=<regex on the label or value>} chooses it, default
 * {@code ^RouteMaster$} (the list has two entries labelled "Route"; only {@code RouteMaster} is saved by the server — see
 * {@link #DEFAULT_FORM}). Only an option whose value is unique can be picked.</p>
 *
 * <p>The Code and Remark are generated per run (the shared QA environment keeps every record ever saved) and a different pair is
 * entered if the server answers "already exists". The entry form is always open on this screen, so there is no Add step.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL record in the chosen master of the target environment.</p>
 */
public class DrugAdminRoute extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /**
     * Which Form Name to save into (regex on the option label OR value). Override with {@code -Dform=}. Two options carry the label
     * "Route": {@code Route} — whose save the server answers with an empty list (so the screen shows no message and nothing is
     * stored) — and {@code RouteMaster}, which saves. The value is what tells them apart.
     */
    public static final String DEFAULT_FORM = "^RouteMaster$";

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 5;

    public DrugAdminRoute() { super("ApplicationConfiguration_General_DrugAdminRoute"); }

    public static void main(String[] args) {
        DrugAdminRoute t = new DrugAdminRoute();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - General - Drug Admin Route", "Application Configuration > General > Drug Admin Route",
                "&#9888; Creates a REAL record: select the Form Name, enter Code and Remark, Submit, check the toast and the table.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String formWanted = System.getProperty("form", DEFAULT_FORM);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.DrugAdminRoute dar =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.DrugAdminRoute(page);

        // 1) Navigate
        boolean rendered = dar.navigateViaMenu();
        if (!rendered) addSummary("Menu offered", dar.lastMenu);
        step(page, "Open Drug Admin Route screen",
                "Click Application Configuration -> General -> Drug Admin Route",
                "The Drug Admin Route screen is shown",
                rendered ? "Opened " + page.url()
                           + (dar.lastRoute.isEmpty() ? "" : " (menu route " + dar.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }
        addSummary("Screen controls", dar.describeControls());

        // 2) Form Name
        String picked = dar.selectFormName(formWanted);
        boolean formOk = dar.formNameSelected();
        step(page, "Select Form Name", "Select the Form Name (option matching " + formWanted + ")",
                "A Form Name is selected",
                (formOk ? "PASSES because the select shows it and the screen's model holds it: " : "FAILS because the choice did not take: ")
                        + picked + " — " + dar.readFormName(),
                formOk ? "PASS" : "FAIL");
        if (!formOk) { addSummary("Result", "FAILED — the Form Name could not be selected"); return; }

        // 3) Code + Remark, 4) Submit -> toast. A different pair is entered when the server says "already exists".
        String entry = "", toast = "";
        boolean entered = false, ok = false, duplicate = false;
        int used = 0;
        // The steps are recorded after the retry loop, so each screenshot is taken in the loop at the moment it is meaningful: the
        // filled form BEFORE Submit (afterwards the form is cleared) and the page right after the toast was read (they fade).
        byte[] filledShot = null, toastShot = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0 && !dar.formNameSelected()) dar.selectFormName(formWanted);   // a refused save may reset the form
            entry = dar.fillDetails(attempt);
            entered = dar.detailsEntered();
            used++;
            filledShot = shot();
            if (!entered) break;                        // nothing sensible to submit — report the entry failure
            toast = dar.submitAndGetToast();
            toastShot = shot();
            ok = com.kpj.pages.ApplicationConfiguration_page.General_page.QualityIndicator.isSuccess(toast);
            duplicate = com.kpj.pages.ApplicationConfiguration_page.General_page.QualityIndicator.isDuplicate(toast);
            if (ok || !duplicate) break;
            System.out.println("DrugAdminRoute: attempt " + used + " (" + entry + ") already exists — changing the details");
        }
        String code = dar.lastCode, remark = dar.lastRemark, formName = dar.lastFormName;

        stepShot(filledShot, "Enter code and remark",
                "Enter the Code " + code + " and the Remark " + remark,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry)
                    + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                entered ? "PASS" : "FAIL");
        if (!entered) { addSummary("Result", "FAILED — the details could not be entered"); return; }

        // A message containing "undefined" FAILS the step even though the save goes through: the record identifier was never
        // substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + dar.lastSaveDiagnostics
                : (ok ? toast
                      : duplicate ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                  : "Submit not confirmed — server returned: \"" + toast + "\"");
        stepShot(toastShot, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen cannot name what it saved."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        // 5) The table — checked even when the toast was not a success, so a silent save is reported as such rather than hidden.
        boolean inList = dar.verifyInTable();
        addSummary("List check", dar.lastListCheck);
        step(page, "Search the entered values in the table", "Filter the Code column by " + code + "; check the Code and Remark of the row",
                "The new row shows the entered Code and Remark",
                (inList
                    ? "PASSES because the saved record — the Code AND its Remark — was found in the table: " + dar.lastListCheck
                    : "FAILS because the saved record could not be found in the table. " + dar.lastListCheck
                        + (ok ? "" : "  (the Submit was not confirmed either)")),
                inList ? "PASS" : "FAIL");

        addSummary("Form Name", formName);
        addSummary("Code / Remark", code + " / " + remark);
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
