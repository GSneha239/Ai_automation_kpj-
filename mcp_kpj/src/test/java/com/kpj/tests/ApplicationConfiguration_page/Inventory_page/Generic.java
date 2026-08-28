package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Generic — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Generic</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Generic</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Remark</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>The requested steps do not mention Add; the flow clicks it only if the form is not already on screen
 * and says which it found.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL generic in the target environment.</p>
 */
public class Generic extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public Generic() { super("ApplicationConfiguration_Generic"); }

    public static void main(String[] args) {
        Generic t = new Generic();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Generic", "Application Configuration > Inventory > Generic",
                "&#9888; Creates a REAL generic: enter Code and Remark, then Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "GN" + stamp);
        String remark = System.getProperty("remark", "Auto generic " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.Generic gn =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.Generic(page);

        // 1) Navigate
        boolean rendered = gn.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", gn.lastMenu);
        step(page, "Open Generic screen",
                "Click Application Configuration -> Inventory -> Generic",
                "The Generic screen is shown",
                rendered ? "Opened " + page.url()
                           + (gn.lastRoute.isEmpty() ? "" : " (menu route " + gn.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", gn.describeControls());
        addSummary("Add", gn.openFormIfNeeded());
        addSummary("Form controls", gn.describeControls());

        // 2) Code + Remark
        String entry = gn.enterDetails(code, remark);
        boolean entered = gn.detailsEntered(code, remark);
        step(page, "Enter code and remark",
                "Enter the Code " + code + " and the Remark " + remark,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 3) Submit -> toast
        String toast = gn.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.Generic.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = gn.codeInList(code, remark);
        addSummary("List check", gn.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + gn.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the generic IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + gn.lastListCheck)
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

        step(page, "Verify the generic was created", "Look for " + code + " in the list",
                "The new generic is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its remark — was found in the "
                      + "list: " + gn.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + gn.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Remark", code + " / " + remark);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
