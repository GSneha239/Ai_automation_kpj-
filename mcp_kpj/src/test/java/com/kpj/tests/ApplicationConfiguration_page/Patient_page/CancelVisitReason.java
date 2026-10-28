package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named CancelVisitReason — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Cancel Visit Reason</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Cancel Visit Reason</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Description</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 *   <li>Verify the entered values in the table.</li>
 * </ol>
 *
 * <p>The Code and Description are generated per run (the shared QA environment keeps every record ever saved) and a different pair
 * is entered if the server answers "already exists".</p>
 *
 * <p>The entry form is always open on this screen (there is no Add button), so there is no Add step.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL cancel visit reason in the target environment.</p>
 */
public class CancelVisitReason extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /** How many times to re-enter fresh details when the server says the code / description already exists. */
    private static final int MAX_ATTEMPTS = 10;

    public CancelVisitReason() { super("ApplicationConfig_Patient_CancelVisitReason"); }

    public static void main(String[] args) {
        CancelVisitReason t = new CancelVisitReason();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Cancel Visit Reason", "Application Configuration > Patient > Cancel Visit Reason",
                "&#9888; Creates a REAL cancel visit reason: enter Code and Description, Submit, check the toast and the table.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.CancelVisitReason dnp =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.CancelVisitReason(page);

        // 1) Navigate
        boolean rendered = dnp.navigateViaMenu();
        if (!rendered) addSummary("Menu offered", dnp.lastMenu);
        step(page, "Open Cancel Visit Reason screen",
                "Click Application Configuration -> Patient -> Cancel Visit Reason",
                "The Cancel Visit Reason screen is shown",
                rendered ? "Opened " + page.url()
                           + (dnp.lastRoute.isEmpty() ? "" : " (menu route " + dnp.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }
        addSummary("Screen controls", dnp.describeControls());

        // 2) Code + Description, 3) Submit -> toast. A different pair is entered when the server says "already exists".
        String entry = "", toast = "";
        boolean entered = false, ok = false, duplicate = false;
        int used = 0;
        // The steps are recorded after the retry loop, so each screenshot is taken in the loop at the moment it is meaningful: the
        // filled form BEFORE Submit (afterwards the form is cleared) and the page right after the toast was read (they fade).
        byte[] filledShot = null, toastShot = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            entry = dnp.fillDetails(attempt);
            entered = dnp.detailsEntered();
            used++;
            filledShot = shot();
            if (!entered) break;                        // nothing sensible to submit — report the entry failure
            toast = dnp.submitAndGetToast();
            toastShot = shot();
            ok =com.kpj.pages.ApplicationConfiguration_page.Patient_page.CancelVisitReason.isSuccess(toast);
            duplicate = com.kpj.pages.ApplicationConfiguration_page.Patient_page.CancelVisitReason.isDuplicate(toast);
            if (ok || !duplicate) break;
            System.out.println("CancelVisitReason: attempt " + used + " (" + entry + ") already exists — changing the details");
        }
        String code = dnp.lastCode, description = dnp.lastDescription;

        stepShot(filledShot, "Enter code and description",
                "Enter the Code " + code + " and the Description " + description,
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
                ? "No message appeared — " + dnp.lastSaveDiagnostics
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

        // 4) The table — checked even when the toast was not a success, so a silent save is reported as such rather than hidden.
        boolean inList = dnp.verifyInTable();
        addSummary("List check", dnp.lastListCheck);
        step(page, "Verify the entered values in the table", "Filter the Code column by " + code + "; check the Code and Description of the row",
                "The new row shows the entered Code and Description",
                (inList
                    ? "PASSES because the saved record — the Code AND its Description — was found in the table: " + dnp.lastListCheck
                    : "FAILS because the saved record could not be found in the table. " + dnp.lastListCheck
                        + (ok ? "" : "  (the Submit was not confirmed either)")),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Description", code + " / " + description);
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
