package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named VisaType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>Visa Type</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>Visa Type</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Visa Type</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>The requested steps do not mention Add; the flow clicks it only if the form is not already on screen
 * and says which it found.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL visa type in the target environment.</p>
 */
public class VisaType extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public VisaType() { super("ApplicationConfiguration_VisaType"); }

    public static void main(String[] args) {
        VisaType t = new VisaType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Visa Type", "Application Configuration > General > Visa Type",
                "&#9888; Creates a REAL visa type: enter Code and Visa Type, then Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "VT" + stamp);
        String visaType = System.getProperty("visatype", "Auto Visa Type " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.VisaType vt =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.VisaType(page);

        // 1) Navigate
        boolean rendered = vt.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", vt.lastMenu);
        step(page, "Open Visa Type screen",
                "Click Application Configuration -> General -> Visa Type",
                "The Visa Type screen is shown",
                rendered ? "Opened " + page.url()
                           + (vt.lastRoute.isEmpty() ? "" : " (menu route " + vt.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", vt.describeControls());
        addSummary("Add", vt.openFormIfNeeded());
        addSummary("Form controls", vt.describeControls());

        // 2) Code + Visa Type
        String entry = vt.enterDetails(code, visaType);
        boolean entered = vt.detailsEntered(code, visaType);
        step(page, "Enter code and visa type",
                "Enter the Code " + code + " and the Visa Type " + visaType,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 3) Submit -> toast
        String toast = vt.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.VisaType.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = vt.codeInList(code, visaType);
        addSummary("List check", vt.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + vt.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the visa type IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + vt.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        step(page, "Verify the visa type was created", "Look for " + code + " in the list",
                "The new visa type is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its visa type — was found in the "
                      + "list: " + vt.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + vt.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Visa Type", code + " / " + visaType);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
