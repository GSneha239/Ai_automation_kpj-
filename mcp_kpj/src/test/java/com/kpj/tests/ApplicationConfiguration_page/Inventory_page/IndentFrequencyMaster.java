package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named IndentFrequencyMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Indent Frequency Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Indent Frequency Master</b>.</li>
 *   <li>Select the <b>Requesting Store</b>.</li>
 *   <li>Select the <b>Issuing Store</b>.</li>
 *   <li>Select the <b>Recurring Cycle</b>.</li>
 *   <li>Tick the checkbox for the day.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The store pair varies per run: this screen holds one record per pair, so repeating a pair would be
 * refused as a duplicate. Pin one with {@code -Dseed=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL indent frequency record in the target environment.</p>
 */
public class IndentFrequencyMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public IndentFrequencyMaster() { super("ApplicationConfiguration_IndentFrequencyMaster"); }

    public static void main(String[] args) {
        IndentFrequencyMaster t = new IndentFrequencyMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Indent Frequency Master", "Application Configuration > Inventory > Indent Frequency Master",
                "&#9888; Creates a REAL indent frequency record: select the Requesting Store, the Issuing "
                        + "Store and the Recurring Cycle, tick the day, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        int seed = Integer.getInteger("seed", (int) Math.abs(System.nanoTime() % 100000));

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.IndentFrequencyMaster ifm =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.IndentFrequencyMaster(page);

        // 1) Navigate
        boolean rendered = ifm.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", ifm.lastMenu);
        step(page, "Open Indent Frequency Master screen",
                "Click Application Configuration -> Inventory -> Indent Frequency Master",
                "The Indent Frequency Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (ifm.lastRoute.isEmpty() ? "" : " (menu route " + ifm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("Screen controls", ifm.describeControls());

        // 2) Requesting store + issuing store
        String stores = ifm.selectStores(seed);
        boolean storesOk = ifm.storesSelected();
        step(page, "Select the requesting store", "Select the Requesting Store",
                "A requesting store is selected",
                (com.kpj.pages.ApplicationConfiguration_page.Inventory_page.IndentFrequencyMaster.chosen(ifm.lastRequesting)
                    ? "PASSES because the dropdown holds the choice: " + ifm.lastRequesting
                    : "FAILS because no requesting store could be selected: " + ifm.lastRequesting),
                com.kpj.pages.ApplicationConfiguration_page.Inventory_page.IndentFrequencyMaster.chosen(ifm.lastRequesting)
                    ? "PASS" : "FAIL");

        step(page, "Select the issuing store", "Select the Issuing Store",
                "An issuing store is selected, different from the requesting store",
                (storesOk
                    ? "PASSES because the dropdown holds a DIFFERENT store from the requesting one: "
                      + stores + ". The pair varies per run — this screen holds one record per pair, and "
                      + "repeating a pair would be refused as a duplicate."
                    : "FAILS: " + stores),
                storesOk ? "PASS" : "FAIL");

        // 3) Recurring cycle
        String cycle = ifm.selectRecurringCycle();
        boolean cycleOk = ifm.cycleSelected();
        step(page, "Select the recurring cycle", "Select the Recurring Cycle",
                "A recurring cycle is selected",
                (cycleOk
                    ? "PASSES because the dropdown holds the choice: " + cycle
                      + " (Weekly is preferred — the day checkboxes belong to it, and under Daily there "
                      + "would be no day to tick)"
                    : "FAILS because no recurring cycle could be selected: " + cycle),
                cycleOk ? "PASS" : "FAIL");

        // 4) Day checkbox
        String day = ifm.tickDay();
        boolean dayOk = ifm.dayTicked();
        step(page, "Tick the checkbox for the day", "Tick the checkbox for the day",
                "The day is ticked",
                (dayOk
                    ? "PASSES because the box was CLICKED and both the control and its model read back as "
                      + "set: " + day + ". Assigning checked by hand would leave the model untouched — the "
                      + "tick would show on screen while the record saved without the day."
                    : "FAILS because the day checkbox did not take: " + day),
                dayOk ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = ifm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.IndentFrequencyMaster.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        String grid = ifm.recordInGrid();
        boolean found = ifm.recordFound();
        addSummary("Grid check", grid);
        addSummary("What the screen put on screen after Submit", ifm.lastPopupsSeen);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + ifm.lastSaveDiagnostics
                  + (found ? " BUT the record IS in the grid, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (found
                            ? "DEFECT: the record IS created (it is in the grid) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the grid. " + grid)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (found ? "  ||  The saved record is in the screen's own grid: " + grid : "")
                    + (ifm.toastFromObserver
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

        addSummary("Stores", stores);
        addSummary("Recurring cycle", ifm.lastCycle);
        addSummary("Day", ifm.lastDay);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
