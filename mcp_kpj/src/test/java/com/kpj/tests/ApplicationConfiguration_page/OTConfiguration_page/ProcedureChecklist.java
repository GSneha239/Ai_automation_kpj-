package com.kpj.tests.ApplicationConfiguration_page.OTConfiguration_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ProcedureChecklist — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; OT Configuration &gt; <b>Procedure Checklist</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>OT Configuration</b> → <b>Procedure Checklist</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Remark</b>.</li>
 *   <li>Select the <b>Store</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>Fields are addressed by the LAST SEGMENT of their ng-model, never by a keyword: every model on this
 * screen begins with the screen's own name, so a substring match would hit almost anything.</p>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin values with
 * {@code -Dcode=}, {@code -Dremark=}, {@code -Dseed=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL OT theatre in the target environment.</p>
 */
public class ProcedureChecklist extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ProcedureChecklist() { super("ApplicationConfiguration_ProcedureChecklist"); }

    public static void main(String[] args) {
        ProcedureChecklist t = new ProcedureChecklist();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Procedure Checklist", "Application Configuration > OT Configuration > Procedure Checklist",
                "&#9888; Creates a REAL procedure checklist: Add, enter the Code and Remark, select the Store, "
                        + "Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        int seed = Integer.getInteger("seed", Integer.parseInt(stamp));
        String code = System.getProperty("code", "PCL" + stamp);
        // The remark carries the same stamp, so a value landing in the wrong box shows in the report.
        String remark = System.getProperty("remark", "Auto procedure checklist " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.ProcedureChecklist pc =
                new com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.ProcedureChecklist(page);

        // 1) Navigate
        boolean rendered = pc.navigateViaMenu(BASE);
        if (!rendered) addSummary("OT Configuration submenu offered", pc.lastMenu);
        step(page, "Open Procedure Checklist screen",
                "Click Application Configuration -> OT Configuration -> Procedure Checklist",
                "The Procedure Checklist screen is shown",
                rendered ? "Opened " + page.url()
                           + (pc.lastRoute.isEmpty() ? "" : " (menu route " + pc.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url()
                           + ") — see the OT Configuration submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", pc.describeControls());

        // 2) Add
        String add = pc.openFormIfNeeded();
        boolean formOpen = pc.formOpen();
        step(page, "Click Add", "Click Add to open the entry form",
                "The entry form is shown",
                (formOpen
                    ? "PASSES because the entry form is on screen: " + add
                    : "FAILS because no entry form appeared: " + add),
                formOpen ? "PASS" : "FAIL");
        addSummary("Form controls", pc.describeControls());

        // 3) Category
        String category = pc.selectCategory(seed);
        boolean categoryOk = pc.categorySelected();
        step(page, "Select the category", "Select the Category",
                "A category is selected",
                (categoryOk
                    ? "PASSES because the dropdown holds the choice: " + category
                      + ". Which category is used varies per run, so repeated runs do not all point at "
                      + "the same one."
                    : "FAILS because no category could be selected: " + category),
                categoryOk ? "PASS" : "FAIL");

        // 4) Code + Remark
        String entry = pc.enterCodeAndRemark(code, remark);
        boolean entered = pc.codeAndRemarkEntered(code, remark);
        step(page, "Enter code and remark",
                "Enter the Code " + code + " and the Remark " + remark,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its OWN value and read it back: " + entry
                      + ". Each is addressed by its model's last segment, so the remark cannot be "
                      + "written over the code."
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = pc.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.ProcedureChecklist.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = pc.codeInList(code, remark);
        addSummary("List check", pc.lastListCheck);
        addSummary("What the screen put on screen after Submit", pc.lastPopupsSeen);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + pc.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the procedure checklist IS created (it is in the list) but the toast reads "
                              + "\"" + toast + "\" instead of a success message — the screen's result "
                              + "code has no text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + pc.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (pc.toastFromObserver
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
        addSummary("Category", pc.lastCategory);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
