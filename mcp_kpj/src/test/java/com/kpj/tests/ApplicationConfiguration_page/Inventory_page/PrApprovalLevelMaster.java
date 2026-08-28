package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PrApprovalLevelMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>PR Approval Level Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>PR Approval Level Master</b>.</li>
 *   <li>Select the <b>PR Approval Level Master</b>.</li>
 *   <li>Enter the <b>Min</b> and <b>Max</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>Min and Max are generated per run, so repeated runs do not collide with a band already
 * configured. Pin them with {@code -Dmin=} / {@code -Dmax=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL PR approval level in the target environment.</p>
 */
public class PrApprovalLevelMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public PrApprovalLevelMaster() { super("ApplicationConfiguration_PrApprovalLevelMaster"); }

    public static void main(String[] args) {
        PrApprovalLevelMaster t = new PrApprovalLevelMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("PR Approval Level Master", "Application Configuration > Inventory > PR Approval Level Master",
                "&#9888; Creates a REAL PR approval level: select the level, enter Min and Max, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        int n = (int) Math.abs(System.nanoTime() % 100000);
        // A band that is unlikely to collide with one already configured, and Min < Max so an ordering
        // rule cannot be what fails.
        String min = System.getProperty("min", String.valueOf(100000 + n));
        String max = System.getProperty("max", String.valueOf(200000 + n));

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.PrApprovalLevelMaster pr =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.PrApprovalLevelMaster(page);

        // 1) Navigate
        boolean rendered = pr.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", pr.lastMenu);
        step(page, "Open PR Approval Level Master screen",
                "Click Application Configuration -> Inventory -> PR Approval Level Master",
                "The PR Approval Level Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (pr.lastRoute.isEmpty() ? "" : " (menu route " + pr.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", pr.describeControls());

        // The requested steps do not include Add, and this screen has none: its entry fields sit on the
        // list route itself. Reported rather than assumed.
        addSummary("Add", pr.openFormIfNeeded());
        addSummary("Form controls", pr.describeControls());

        // 2) Select the PR Approval Level Master
        String level = pr.selectLevel();
        boolean levelOk = pr.levelSelected();
        step(page, "Select PR approval level master", "Select the PR Approval Level Master",
                "A level is selected",
                (levelOk
                    ? "PASSES because the dropdown holds the choice: " + level
                    : "FAILS because no level could be selected: " + level),
                levelOk ? "PASS" : "FAIL");

        // 3) Min + Max
        String entry = pr.enterMinMax(min, max);
        boolean entered = pr.minMaxEntered(min, max);
        step(page, "Enter min and max", "Enter the Min " + min + " and the Max " + max,
                "Both are entered",
                (entered
                    ? "PASSES because each box read back its OWN value — checked separately, since \"min\" "
                      + "and \"max\" differ by one letter and a loose match fills one box twice: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Submit -> toast. If the screen refuses, set the Location (it defaults to "--All--", and is
        // not part of the requested steps) and try once more, so the report can say whether the refusal
        // was a missing precondition or the save itself failing.
        String toast = pr.submitAndGetToast();
        String retryNote = "";
        if (!com.kpj.pages.ApplicationConfiguration_page.Inventory_page.PrApprovalLevelMaster.isSuccess(toast)) {
            String first = toast;
            String loc = pr.selectLocation();      // the Location dropdown, left on whatever it holds
            String second = pr.submitAndGetToast();
            boolean secondOk =
                    com.kpj.pages.ApplicationConfiguration_page.Inventory_page.PrApprovalLevelMaster.isSuccess(second);
            retryNote = "  ||  FIRST Submit was refused with \"" + first + "\". The same form was then "
                    + "submitted a SECOND time, with nothing changed but a touch of the Location dropdown "
                    + "(which stayed on " + loc + "): Submit answered \"" + second + "\"."
                    + (secondOk
                        ? " So the data was valid all along — the screen rejects the first attempt and "
                          + "accepts an identical second one. A user would see an error and have to click "
                          + "Submit again. Worth raising: the first save should not fail."
                        : " The second attempt was refused too, so this is not a first-attempt problem.");
            if (secondOk) toast = second;
            addSummary("Submit retry", retryNote);
        }

        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.PrApprovalLevelMaster.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = pr.rowInList(min, max);
        String retry = retryNote;
        addSummary("List check", pr.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + pr.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the level IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + pr.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual + retry
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        addSummary("Level", pr.lastLevel);
        addSummary("Min / Max", min + " / " + max);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
