package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DrugClass — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Drug Class</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Drug Class</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Remark</b>.</li>
 *   <li>Tick <b>Is Carbapenems</b> or <b>Is Chemotherapy</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}, the remark with {@code -Dremark=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL drug class in the target environment.</p>
 */
public class DrugClass extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public DrugClass() { super("ApplicationConfiguration_Inventory_DrugClass"); }

    public static void main(String[] args) {
        DrugClass t = new DrugClass();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Inventory - Drug Class", "Application Configuration > Inventory > Drug Class",
                "&#9888; Creates a REAL drug class: Add, enter Code and Remark, tick Is Carbapenems or "
                        + "Is Chemotherapy, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "DCL" + stamp);
        // The remark carries the same stamp, so a value landing in the wrong box is visible in the report.
        String remark = System.getProperty("remark", "Auto drug class " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.DrugClass dcl =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.DrugClass(page);

        // 1) Navigate
        boolean rendered = dcl.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", dcl.lastMenu);
        step(page, "Open Drug Class screen",
                "Click Application Configuration -> Inventory -> Drug Class",
                "The Drug Class screen is shown",
                rendered ? "Opened " + page.url()
                           + (dcl.lastRoute.isEmpty() ? "" : " (menu route " + dcl.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", dcl.describeControls());

        // 2) Add
        String add = dcl.openFormIfNeeded();
        boolean formOpen = dcl.formOpen();
        step(page, "Click Add", "Click Add to open the entry form",
                "The entry form is shown",
                (formOpen
                    ? "PASSES because the entry form is on screen: " + add
                    : "FAILS because no entry form appeared: " + add),
                formOpen ? "PASS" : "FAIL");
        addSummary("Form controls", dcl.describeControls());

        // 3) Code + Remark
        String entry = dcl.enterDetails(code, remark);
        boolean entered = dcl.detailsEntered(code, remark);
        step(page, "Enter code and remark",
                "Enter the Code " + code + " and the Remark " + remark,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Is Carbapenems / Is Chemotherapy
        String tick = dcl.tickDrugFlag();
        boolean ticked = dcl.flagTicked();
        step(page, "Tick Is Carbapenems or Is Chemotherapy",
                "Tick the Is Carbapenems or Is Chemotherapy checkbox",
                "The checkbox is ticked",
                (ticked
                    ? "PASSES because the box was CLICKED and both the control and its model read back "
                      + "as set: " + tick + ". Assigning checked by hand would leave the model untouched — "
                      + "the tick would show on screen while the record saved without it."
                    : "FAILS because the checkbox did not take: " + tick),
                ticked ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = dcl.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.DrugClass.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = dcl.codeInList(code, remark);
        addSummary("List check", dcl.lastListCheck);
        addSummary("What the screen put on screen after Submit", dcl.lastPopupsSeen);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + dcl.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the drug class IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + dcl.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (dcl.toastFromObserver
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

        addSummary("Code / Remark", code + " / " + remark);
        addSummary("Checkbox", dcl.lastCheckbox);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
