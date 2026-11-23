package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Team — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>Team</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>Team</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Description</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The flow ends at the toast: the separate "verify the record was created" step was dropped on
 * request. The list is still checked behind the scenes and reported under "List check", so the toast can
 * still be told apart from a silent or malformed save.</p>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL team in the target environment.</p>
 */
public class Team extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public Team() { super("ApplicationConfiguration_General_Team"); }

    public static void main(String[] args) {
        Team t = new Team();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - General - Team", "Application Configuration > General > Team",
                "&#9888; Creates a REAL team: Add, enter Code and Description, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "TM" + stamp);
        String description = System.getProperty("description", "Auto team " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.Team tm =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.Team(page);

        // 1) Navigate
        boolean rendered = tm.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", tm.lastMenu);
        step(page, "Open Team screen",
                "Click Application Configuration -> General -> Team",
                "The Team screen is shown",
                rendered ? "Opened " + page.url()
                           + (tm.lastRoute.isEmpty() ? "" : " (menu route " + tm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", tm.describeControls());

        // 2) Add
        boolean formOpen = tm.clickAdd();
        addSummary("Form controls", tm.describeControls());
        step(page, "Click Add", "Click Add to open the team form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Description
        String entry = tm.enterDetails(code, description);
        boolean entered = tm.detailsEntered(code, description);
        step(page, "Enter code and description",
                "Enter the Code " + code + " and the Description " + description,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Submit -> toast
        String toast = tm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.Team.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = tm.codeInList(code, description);
        addSummary("List check", tm.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + tm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the team IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + tm.lastListCheck)
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

        // The list check is no longer a step of its own — the flow ends at the success toast. It still
        // runs, and its outcome is reported above and under "List check", so the evidence that the record
        // really was written is not lost.
        addSummary("Code / Description", code + " / " + description);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
