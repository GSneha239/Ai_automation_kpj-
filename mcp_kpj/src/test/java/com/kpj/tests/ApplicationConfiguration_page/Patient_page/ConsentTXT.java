package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ConsentTXT — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>ConsentTXT</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>ConsentTXT</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b>, the <b>Template Name</b> and the <b>Consent Type</b>.</li>
 *   <li>Type some text into the consent editor ({@code //*[@id="myConsentForm"]/div[2]/div}).</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 *   <li>Verify the entered values in the table.</li>
 * </ol>
 *
 * <p>The Code and Template Name are generated per run from the clock (the shared QA environment keeps every record ever saved) and a
 * different code is entered if the server answers "already exists".</p>
 *
 * <p>&#9888; A successful run CREATES a REAL consent template in the target environment.</p>
 */
public class ConsentTXT extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /** How many times to re-enter a fresh code when the server says the code / template name already exists. */
    private static final int MAX_ATTEMPTS = 5;

    public ConsentTXT() { super("ApplicationConfig_Patient_ConsentTXT"); }

    public static void main(String[] args) {
        ConsentTXT t = new ConsentTXT();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - ConsentTXT", "Application Configuration > Patient > ConsentTXT",
                "&#9888; Creates a REAL consent template: Add, enter Code, Template Name and Consent Type, type the consent text, Submit, check the toast and the table.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.ConsentTXT ct =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.ConsentTXT(page);

        // 1) Navigate
        boolean rendered = ct.navigateViaMenu();
        if (!rendered) addSummary("Menu offered", ct.lastMenu);
        step(page, "Open ConsentTXT screen",
                "Click Application Configuration -> Patient -> ConsentTXT",
                "The ConsentTXT list screen is shown",
                rendered ? "Opened " + page.url()
                           + (ct.lastRoute.isEmpty() ? "" : " (menu route " + ct.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }
        addSummary("List screen controls", ct.describeControls());

        // 2) Add
        boolean added = ct.clickAdd();
        step(page, "Click Add", "Click Add", "The ConsentTXT entry form opens",
                added ? ct.lastAdd : "FAILS — " + ct.lastAdd, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — the add form did not open"); return; }
        addSummary("Add form controls", ct.describeControls());

        // 3) Code / Template Name / Consent Type, 4) the consent text, 5) Submit -> toast. The steps are recorded after the retry
        // loop, so each screenshot is taken in the loop at the moment it is meaningful: the form with its three values, the editor with
        // its text (both BEFORE Submit — afterwards the form is cleared) and the page right after the toast was read (toasts fade).
        String entry = "", toast = "";
        boolean entered = false, texted = false, ok = false, duplicate = false;
        int used = 0;
        byte[] enteredShot = null, textShot = null, toastShot = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            entry = ct.fillDetails(attempt);
            entered = ct.detailsEntered();
            entry = ct.lastEntry;                       // carries the read-back when a box did not hold its value
            used++;
            enteredShot = shot();
            if (!entered) break;                        // nothing sensible to submit — report the entry failure
            texted = ct.enterConsentText(attempt);
            textShot = shot();
            if (!texted) break;                         // a template with no text is rejected — report why it could not be typed
            toast = ct.submitAndGetToast();
            toastShot = shot();
            ok = com.kpj.pages.ApplicationConfiguration_page.Patient_page.ConsentTXT.isSuccess(toast);
            duplicate = com.kpj.pages.ApplicationConfiguration_page.Patient_page.ConsentTXT.isDuplicate(toast);
            if (ok || !duplicate) break;
            System.out.println("ConsentTXT: attempt " + used + " (" + ct.lastCode + ") already exists — entering a new code");
        }
        String code = ct.lastCode, templateName = ct.lastTemplateName;

        stepShot(enteredShot, "Enter code, template name and consent type",
                "Enter the Code " + code + ", the Template Name " + templateName + " and choose the Consent Type",
                "All three are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry)
                    + (used > 1 ? "  [code changed " + (used - 1) + "x after 'already exists']" : ""),
                entered ? "PASS" : "FAIL");
        if (!entered) { addSummary("Result", "FAILED — the details could not be entered"); return; }

        stepShot(textShot, "Enter the consent text",
                "Click into the consent editor (//*[@id=\"myConsentForm\"]/div[2]/div) and type some text",
                "The editor shows the text and the form's consent.template holds it",
                texted ? "PASSES because " + ct.lastEditor : "FAILS — " + ct.lastEditor,
                texted ? "PASS" : "FAIL");
        if (!texted) { addSummary("Result", "FAILED — no text could be entered in the consent editor"); return; }

        // A message containing "undefined" FAILS the step even though the save goes through: the record identifier was never
        // substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + ct.lastSaveDiagnostics
                : (ok ? toast + "  (" + ct.lastSaveDiagnostics + ")"
                      : duplicate ? "Still 'already exists' after " + used + " attempt(s) with different codes: \"" + toast + "\""
                                  : "Submit not confirmed — server returned: \"" + toast + "\"  (" + ct.lastSaveDiagnostics + ")");
        stepShot(toastShot, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen cannot name what it saved."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        // 6) The table — checked even when the toast was not a success, so a silent save is reported as such rather than hidden.
        boolean inList = ct.verifyInTable();
        addSummary("List check", ct.lastListCheck);
        step(page, "Verify the entered values in the table",
                "Open the list; filter the Code column by " + code + "; check the Code and Template Name of the row",
                "The new row shows the entered Code and Template Name",
                (inList
                    ? "PASSES because the saved record — the Code AND its Template Name — was found in the table: " + ct.lastListCheck
                    : "FAILS because the saved record could not be found in the table with those values. " + ct.lastListCheck
                        + (ok ? "" : "  (the Submit was not confirmed either)")),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Template Name", code + " / " + templateName);
        addSummary("Consent Type", ct.lastConsentType);
        addSummary("Consent text", ct.lastText);
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
