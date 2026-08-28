package com.kpj.tests.ApplicationConfiguration_page.OTConfiguration_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ChecklistMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Checklist Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Checklist Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Checklist Name</b>.</li>
 *   <li>Select the <b>Tag</b> and the <b>Checklist Category</b>.</li>
 *   <li>Enter the <b>Remark</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}, the name with {@code -Dname=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL checklist in the target environment.</p>
 */
public class ChecklistMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ChecklistMaster() { super("ApplicationConfiguration_ChecklistMaster"); }

    public static void main(String[] args) {
        ChecklistMaster t = new ChecklistMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Checklist Master", "Application Configuration > OT Configuration > Checklist Master",
                "&#9888; Creates a REAL checklist: Add, enter Code and Checklist Name, select Tag and Checklist Category, enter Remark, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "CLM" + stamp);
        String name = System.getProperty("name", "Auto checklist " + stamp);
        // The remark carries the same stamp, so a value landing in the wrong box shows in the report.
        String remarkText = System.getProperty("remark", "Auto remark " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.ChecklistMaster clm =
                new com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.ChecklistMaster(page);

        // 1) Navigate
        boolean rendered = clm.navigateViaMenu(BASE);
        if (!rendered) addSummary("OT Configuration submenu offered", clm.lastMenu);
        step(page, "Open Checklist Master screen",
                "Click Application Configuration -> OT Configuration -> Checklist Master",
                "The Checklist Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (clm.lastRoute.isEmpty() ? "" : " (menu route " + clm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the OT Configuration submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", clm.describeControls());

        // 2) Add
        String add = clm.openFormIfNeeded();
        boolean formOpen = clm.formOpen();
        step(page, "Click Add", "Click Add to open the entry form",
                "The entry form is shown",
                (formOpen
                    ? "PASSES because the entry form is on screen: " + add
                    : "FAILS because no entry form appeared: " + add),
                formOpen ? "PASS" : "FAIL");
        addSummary("Form controls", clm.describeControls());

        // 3) Code + Checklist Name
        String entry = clm.enterCodeAndName(code, name);
        boolean entered = clm.codeAndNameEntered(code, name);
        step(page, "Enter code and checklist name",
                "Enter the Code " + code + " and the Checklist Name " + name,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Tag + Checklist Category
        String tag = clm.selectTag();
        boolean tagOk = clm.tagSelected();
        String category = clm.selectCategory();
        boolean catOk = clm.categorySelected();
        step(page, "Select tag and checklist category",
                "Select the Tag and the Checklist Category",
                "Both dropdowns hold a value",
                ((tagOk && catOk)
                    ? "PASSES because both dropdowns hold their choice, each read back off the control: "
                      + "Tag = " + tag + " | Checklist Category = " + category
                    : "FAILS: Tag = " + tag + " | Checklist Category = " + category),
                (tagOk && catOk) ? "PASS" : "FAIL");

        // 5) Remark
        String remark = clm.enterRemark(remarkText);
        boolean remarkOk = clm.remarkEntered(remarkText);
        step(page, "Enter remark", "Enter the Remark " + remarkText,
                "The remark is entered",
                (remarkOk
                    ? "PASSES because the box read the value back: " + remark
                    : "FAILS because the remark did not land in its field: " + remark),
                remarkOk ? "PASS" : "FAIL");

        // 6) Submit -> toast
        String toast = clm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.ChecklistMaster.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = clm.codeInList(code, name);
        addSummary("List check", clm.lastListCheck);
        addSummary("What the screen put on screen after Submit", clm.lastPopupsSeen);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + clm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the checklist IS created (it is in the list) but the "
                              + "toast reads \"" + toast + "\" instead of a success message — the screen's "
                              + "result code has no text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + clm.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (clm.toastFromObserver
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

        addSummary("Code / Checklist Name", code + " / " + name);
        addSummary("Tag / Category", clm.lastTag + " / " + clm.lastCategory);
        addSummary("Remark", clm.lastRemark);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
