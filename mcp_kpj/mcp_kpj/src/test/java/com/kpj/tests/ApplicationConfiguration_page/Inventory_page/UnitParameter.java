package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named UnitParameter — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Unit Parameter</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Unit Parameter</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Remark</b>.</li>
 *   <li>Enter the <b>MIMS Description</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>The requested steps do not mention Add; the flow clicks it only if the form is not already on screen
 * and says which it found.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL unit parameter in the target environment.</p>
 */
public class UnitParameter extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public UnitParameter() { super("ApplicationConfiguration_Inventory_UnitParameter"); }

    public static void main(String[] args) {
        UnitParameter t = new UnitParameter();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Inventory - Unit Parameter", "Application Configuration > Inventory > Unit Parameter",
                "&#9888; Creates a REAL unit parameter: enter Code and Remark, then Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "UP" + stamp);
        String remark = System.getProperty("remark", "Auto unit parameter " + stamp);
        // Distinct from the remark on purpose: the form carries two description boxes, and identical
        // values would hide one being written into the other.
        String mimsDesc = System.getProperty("mimsdesc", "Auto MIMS description " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.UnitParameter up =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.UnitParameter(page);

        // 1) Navigate
        boolean rendered = up.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", up.lastMenu);
        step(page, "Open Unit Parameter screen",
                "Click Application Configuration -> Inventory -> Unit Parameter",
                "The Unit Parameter screen is shown",
                rendered ? "Opened " + page.url()
                           + (up.lastRoute.isEmpty() ? "" : " (menu route " + up.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", up.describeControls());

        // 2) Add. Some screens in this section put the entry form on the list route, so this clicks Add
        // only when the form is not already there and says which it found.
        String addNote = up.openFormIfNeeded();
        boolean formOpen = up.formOpen();
        addSummary("Form controls", up.describeControls());
        step(page, "Click Add", "Click Add to open the unit parameter form",
                "The entry form is open",
                (formOpen
                    ? "PASSES because the entry form is open — " + addNote + " (" + page.url() + ")"
                    : "FAILS because no entry form appeared: " + addNote + " (" + page.url() + ")"),
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 2) Code + Remark
        String entry = up.enterDetails(code, remark);
        boolean entered = up.detailsEntered(code, remark);
        step(page, "Enter code and remark",
                "Enter the Code " + code + " and the Remark " + remark,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) MIMS Description
        String mims = up.enterMimsDescription(mimsDesc);
        boolean mimsOk = up.mimsDescriptionEntered(mimsDesc);
        step(page, "Enter MIMS Description", "Enter the MIMS Description " + mimsDesc,
                "The MIMS Description is entered",
                (mimsOk
                    ? "PASSES because the box read back its own value, and it did not take the record's "
                      + "own Remark — this form carries two description boxes: " + mims
                    : "FAILS because the MIMS Description did not land in its own field: " + mims),
                mimsOk ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = up.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.UnitParameter.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = up.codeInList(code, remark);
        addSummary("List check", up.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + up.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the record IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + up.lastListCheck)
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

        step(page, "Verify the unit parameter was created", "Look for " + code + " in the list",
                "The new unit parameter is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its remark — was found in the "
                      + "list: " + up.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + up.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Remark", code + " / " + remark);
        addSummary("MIMS Description", up.lastMims);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
