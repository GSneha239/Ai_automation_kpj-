package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ItemClassMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Item Class Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Item Class Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Remark</b>.</li>
 *   <li>Enter the <b>Instruction</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>The requested steps do not mention Add; the flow clicks it only if the form is not already on screen
 * and says which it found.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL item class in the target environment.</p>
 */
public class ItemClassMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ItemClassMaster() { super("ApplicationConfiguration_ItemClassMaster"); }

    public static void main(String[] args) {
        ItemClassMaster t = new ItemClassMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Item Class Master", "Application Configuration > Inventory > Item Class Master",
                "&#9888; Creates a REAL item class: enter Code and Remark, then Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "IC" + stamp);
        String remark = System.getProperty("remark", "Auto item class " + stamp);
        // Distinct from the remark on purpose, so a value landing in the wrong box is visible.
        String instruction = System.getProperty("instruction", "Auto instruction " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemClassMaster icm =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemClassMaster(page);

        // 1) Navigate
        boolean rendered = icm.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", icm.lastMenu);
        step(page, "Open Item Class Master screen",
                "Click Application Configuration -> Inventory -> Item Class Master",
                "The Item Class Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (icm.lastRoute.isEmpty() ? "" : " (menu route " + icm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", icm.describeControls());

        // 2) Add. Some screens in this section put the entry form on the list route, so this clicks Add
        // only when the form is not already there and says which it found.
        String addNote = icm.openFormIfNeeded();
        boolean formOpen = icm.formOpen();
        addSummary("Form controls", icm.describeControls());
        step(page, "Click Add", "Click Add to open the item class form",
                "The entry form is open",
                (formOpen
                    ? "PASSES because the entry form is open — " + addNote + " (" + page.url() + ")"
                    : "FAILS because no entry form appeared: " + addNote + " (" + page.url() + ")"),
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 2) Code + Remark
        String entry = icm.enterDetails(code, remark);
        boolean entered = icm.detailsEntered(code, remark);
        step(page, "Enter code and remark",
                "Enter the Code " + code + " and the Remark " + remark,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Instruction
        String instr = icm.enterInstruction(instruction);
        boolean instrOk = icm.instructionEntered(instruction);
        step(page, "Enter instruction", "Enter the Instruction " + instruction,
                "The instruction is entered",
                (instrOk
                    ? "PASSES because the box read back its own value, and it did not take the Remark's "
                      + "box: " + instr
                    : "FAILS because the instruction did not land in its own field: " + instr),
                instrOk ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = icm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemClassMaster.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = icm.codeInList(code, remark);
        addSummary("List check", icm.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + icm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the record IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + icm.lastListCheck)
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

        step(page, "Verify the item class was created", "Look for " + code + " in the list",
                "The new item class is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its remark — was found in the "
                      + "list: " + icm.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + icm.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Remark", code + " / " + remark);
        addSummary("Instruction", icm.lastInstruction);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
