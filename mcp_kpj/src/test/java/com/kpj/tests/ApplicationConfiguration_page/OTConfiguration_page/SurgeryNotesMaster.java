package com.kpj.tests.ApplicationConfiguration_page.OTConfiguration_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SurgeryNotesMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; OT Configuration &gt; <b>Surgery Notes Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>OT Configuration</b> → <b>Surgery Notes Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Short Description</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}, the description with {@code -Ddescription=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL surgery note in the target environment.</p>
 */
public class SurgeryNotesMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public SurgeryNotesMaster() { super("ApplicationConfiguration_SurgeryNotesMaster"); }

    public static void main(String[] args) {
        SurgeryNotesMaster t = new SurgeryNotesMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Surgery Notes Master", "Application Configuration > OT Configuration > Surgery Notes Master",
                "&#9888; Creates a REAL surgery note: Add, enter Code and Short Description, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "SNM" + stamp);
        // The description carries the same stamp, so a value landing in the wrong box shows in the report.
        String description = System.getProperty("description", "Auto surgery note " + stamp);
        String remark = System.getProperty("remark", "Auto surgery remark " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.SurgeryNotesMaster snm =
                new com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.SurgeryNotesMaster(page);

        // 1) Navigate
        boolean rendered = snm.navigateViaMenu(BASE);
        if (!rendered) addSummary("OT Configuration submenu offered", snm.lastMenu);
        step(page, "Open Surgery Notes Master screen",
                "Click Application Configuration -> OT Configuration -> Surgery Notes Master",
                "The Surgery Notes Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (snm.lastRoute.isEmpty() ? "" : " (menu route " + snm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the OT Configuration submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", snm.describeControls());

        // 2) Add
        String add = snm.openFormIfNeeded();
        boolean formOpen = snm.formOpen();
        step(page, "Click Add", "Click Add to open the entry form",
                "The entry form is shown",
                (formOpen
                    ? "PASSES because the entry form is on screen: " + add
                    : "FAILS because no entry form appeared: " + add),
                formOpen ? "PASS" : "FAIL");
        addSummary("Form controls", snm.describeControls());

        // 3) Code + Description
        String entry = snm.enterDetails(code, description);
        boolean entered = snm.detailsEntered(code, description);
        step(page, "Enter code and short description",
                "Enter the Code " + code + " and the Short Description " + description,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Pre / Intra / Post
        String noteType = snm.tickNoteType();
        boolean noteTypeOk = snm.noteTypeTicked();
        step(page, "Tick Pre, Intra or Post", "Tick the Pre, Intra or Post checkbox",
                "The checkbox is ticked",
                (noteTypeOk
                    ? "PASSES because the box was CLICKED and both the control and its model read back "
                      + "as set: " + noteType + ". Assigning checked by hand would leave the model "
                      + "untouched — the tick would show on screen while the record saved without it."
                    : "FAILS because the checkbox did not take: " + noteType),
                noteTypeOk ? "PASS" : "FAIL");

        // 5) Remark
        String remarkEntry = snm.enterRemark(remark);
        boolean remarkOk = snm.remarkEntered(remark);
        step(page, "Enter the remark", "Enter the Remark " + remark,
                "The remark is entered",
                (remarkOk
                    ? "PASSES because the field read the value back: " + remarkEntry
                      + ". It is pinned to the textarea — the short description above it has a model "
                      + "ending in \"description\" too, so a keyword match writes into the wrong box."
                    : "FAILS because the remark did not land in its own field: " + remarkEntry),
                remarkOk ? "PASS" : "FAIL");

        // 4) Submit -> toast
        String toast = snm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.SurgeryNotesMaster.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = snm.codeInList(code, description);
        addSummary("List check", snm.lastListCheck);
        addSummary("What the screen put on screen after Submit", snm.lastPopupsSeen);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + snm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the surgery note IS created (it is in the list) but the "
                              + "toast reads \"" + toast + "\" instead of a success message — the screen's "
                              + "result code has no text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + snm.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (snm.toastFromObserver
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

        addSummary("Code / Short Description", code + " / " + description);
        addSummary("Pre / Intra / Post", snm.lastNoteType);
        addSummary("Remark", snm.lastRemark);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
