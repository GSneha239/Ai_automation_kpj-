package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ItemCompany — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Item Company</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Item Company</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Remark</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL item company in the target environment.</p>
 */
public class ItemCompany extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ItemCompany() { super("ApplicationConfiguration_Inventory_ItemCompany"); }

    public static void main(String[] args) {
        ItemCompany t = new ItemCompany();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Inventory - Item Company", "Application Configuration > Inventory > Item Company",
                "&#9888; Creates a REAL item company: enter Code and Remark, then Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "IC" + stamp);
        String remark = System.getProperty("remark", "Auto item company " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemCompany ico =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemCompany(page);

        // 1) Navigate
        boolean rendered = ico.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", ico.lastMenu);
        step(page, "Open Item Company screen",
                "Click Application Configuration -> Inventory -> Item Company",
                "The Item Company screen is shown",
                rendered ? "Opened " + page.url()
                           + (ico.lastRoute.isEmpty() ? "" : " (menu route " + ico.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", ico.describeControls());

        // 2) Add. Some screens in this module put the entry form on the list route, so this clicks Add
        // only when the form is not already there and says which it found — either way the step's job is
        // that the form ends up open.
        String addNote = ico.openFormIfNeeded();
        boolean formOpen = ico.formOpen();
        addSummary("Form controls", ico.describeControls());
        step(page, "Click Add", "Click Add to open the item company form",
                "The entry form is open",
                (formOpen
                    ? "PASSES because the entry form is open — " + addNote + " (" + page.url() + ")"
                    : "FAILS because no entry form appeared: " + addNote + " (" + page.url() + ")"),
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Remark
        String entry = ico.enterDetails(code, remark);
        boolean entered = ico.detailsEntered(code, remark);
        step(page, "Enter code and remark",
                "Enter the Code " + code + " and the Remark " + remark,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Submit -> toast
        String toast = ico.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemCompany.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = ico.codeInList(code, remark);
        addSummary("List check", ico.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + ico.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the item company IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + ico.lastListCheck)
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

        step(page, "Verify the item company was created", "Look for " + code + " in the list",
                "The new item company is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its remark — was found in the "
                      + "list: " + ico.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + ico.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Remark", code + " / " + remark);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
