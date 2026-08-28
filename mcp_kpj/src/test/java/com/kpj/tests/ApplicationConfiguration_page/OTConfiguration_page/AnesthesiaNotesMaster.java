package com.kpj.tests.ApplicationConfiguration_page.OTConfiguration_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AnesthesiaNotesMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; OT Configuration &gt; <b>Anesthesia Notes Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>OT Configuration</b> → <b>Anesthesia Notes Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Short Description</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}, the description with {@code -Ddescription=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL anesthesia note in the target environment.</p>
 */
public class AnesthesiaNotesMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public AnesthesiaNotesMaster() { super("ApplicationConfiguration_AnesthesiaNotesMaster"); }

    public static void main(String[] args) {
        AnesthesiaNotesMaster t = new AnesthesiaNotesMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Anesthesia Notes Master", "Application Configuration > OT Configuration > Anesthesia Notes Master",
                "&#9888; Creates a REAL anesthesia note: Add, enter Code and Short Description, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "ANM" + stamp);
        // The description carries the same stamp, so a value landing in the wrong box shows in the report.
        String description = System.getProperty("description", "Auto anesthesia note " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.AnesthesiaNotesMaster anm =
                new com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.AnesthesiaNotesMaster(page);

        // 1) Navigate
        boolean rendered = anm.navigateViaMenu(BASE);
        if (!rendered) addSummary("OT Configuration submenu offered", anm.lastMenu);
        step(page, "Open Anesthesia Notes Master screen",
                "Click Application Configuration -> OT Configuration -> Anesthesia Notes Master",
                "The Anesthesia Notes Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (anm.lastRoute.isEmpty() ? "" : " (menu route " + anm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the OT Configuration submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", anm.describeControls());

        // 2) Add
        String add = anm.openFormIfNeeded();
        boolean formOpen = anm.formOpen();
        step(page, "Click Add", "Click Add to open the entry form",
                "The entry form is shown",
                (formOpen
                    ? "PASSES because the entry form is on screen: " + add
                    : "FAILS because no entry form appeared: " + add),
                formOpen ? "PASS" : "FAIL");
        addSummary("Form controls", anm.describeControls());

        // 3) Code + Description
        String entry = anm.enterDetails(code, description);
        boolean entered = anm.detailsEntered(code, description);
        step(page, "Enter code and short description",
                "Enter the Code " + code + " and the Short Description " + description,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Submit -> toast
        String toast = anm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.AnesthesiaNotesMaster.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = anm.codeInList(code, description);
        addSummary("List check", anm.lastListCheck);
        addSummary("What the screen put on screen after Submit", anm.lastPopupsSeen);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + anm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the anesthesia note IS created (it is in the list) but the "
                              + "toast reads \"" + toast + "\" instead of a success message — the screen's "
                              + "result code has no text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + anm.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (anm.toastFromObserver
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
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
