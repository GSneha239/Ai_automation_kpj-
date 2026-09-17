package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named StockTakeInstruction — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Stock Take Instruction</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Stock Take Instruction</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Description</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}, the description with {@code -Ddescription=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL stock take instruction in the target environment.</p>
 */
public class StockTakeInstruction extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public StockTakeInstruction() { super("ApplicationConfiguration_Inventory_StockTakeInstruction"); }

    public static void main(String[] args) {
        StockTakeInstruction t = new StockTakeInstruction();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Inventory - Stock Take Instruction", "Application Configuration > Inventory > Stock Take Instruction",
                "&#9888; Creates a REAL stock take instruction: Add, enter Code and Description, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "STI" + stamp);
        // The description carries the same stamp, so a value landing in the wrong box shows in the report.
        String description = System.getProperty("description", "Auto stock take instruction " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.StockTakeInstruction sti =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.StockTakeInstruction(page);

        // 1) Navigate
        boolean rendered = sti.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", sti.lastMenu);
        step(page, "Open Stock Take Instruction screen",
                "Click Application Configuration -> Inventory -> Stock Take Instruction",
                "The Stock Take Instruction screen is shown",
                rendered ? "Opened " + page.url()
                           + (sti.lastRoute.isEmpty() ? "" : " (menu route " + sti.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", sti.describeControls());

        // 2) Add
        String add = sti.openFormIfNeeded();
        boolean formOpen = sti.formOpen();
        step(page, "Click Add", "Click Add to open the entry form",
                "The entry form is shown",
                (formOpen
                    ? "PASSES because the entry form is on screen: " + add
                    : "FAILS because no entry form appeared: " + add),
                formOpen ? "PASS" : "FAIL");
        addSummary("Form controls", sti.describeControls());

        // 3) Code + Description
        String entry = sti.enterDetails(code, description);
        boolean entered = sti.detailsEntered(code, description);
        step(page, "Enter code and description",
                "Enter the Code " + code + " and the Description " + description,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Submit -> toast
        String toast = sti.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.StockTakeInstruction.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = sti.codeInList(code, description);
        addSummary("List check", sti.lastListCheck);
        addSummary("What the screen put on screen after Submit", sti.lastPopupsSeen);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + sti.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the stock take instruction IS created (it is in the list) but the "
                              + "toast reads \"" + toast + "\" instead of a success message — the screen's "
                              + "result code has no text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + sti.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (sti.toastFromObserver
                        ? "  ||  HOW THE MESSAGE WAS READ: these toasts live about five seconds. This one "
                          + "was recorded by a mutation observer at the moment the screen showed it, so it "
                          + "is the screen's own text and not a reconstruction."
                        : "")
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        addSummary("Code / Description", code + " / " + description);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
