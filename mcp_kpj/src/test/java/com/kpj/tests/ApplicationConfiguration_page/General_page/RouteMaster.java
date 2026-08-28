package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named RouteMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>Route Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>Route Master</b>.</li>
 *   <li>Enter the <b>Code</b>, <b>Remark</b>, <b>Instruction</b>, <b>Statutory Code</b>, <b>Statutory
 *       Description</b>, <b>Route Instruction</b> and <b>MIMS Description</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>The requested steps do not mention Add; the flow clicks it only if the form is not already on screen
 * and says which it found.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL route in the target environment.</p>
 */
public class RouteMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public RouteMaster() { super("ApplicationConfiguration_RouteMaster"); }

    public static void main(String[] args) {
        RouteMaster t = new RouteMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Route Master", "Application Configuration > General > Route Master",
                "&#9888; Creates a REAL route: enter Code, Remark, Instruction, Statutory Code, Statutory "
                        + "Description, Route Instruction and MIMS Description, then Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "RT" + stamp);
        // Every value is distinct AND carries its own field name, so a value that lands in a neighbouring
        // box is visible in the report instead of looking like a pass.
        String remark = System.getProperty("remark", "Auto remark " + stamp);
        String instruction = System.getProperty("instruction", "Auto instruction " + stamp);
        String statCode = System.getProperty("statcode", "SC" + stamp);
        String statDesc = System.getProperty("statdesc", "Auto statutory description " + stamp);
        String routeInstruction = System.getProperty("routeinstruction", "Auto route instruction " + stamp);
        String mimsDesc = System.getProperty("mimsdesc", "Auto MIMS description " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.RouteMaster rm =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.RouteMaster(page);

        // 1) Navigate
        boolean rendered = rm.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", rm.lastMenu);
        step(page, "Open Route Master screen",
                "Click Application Configuration -> General -> Route Master",
                "The Route Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (rm.lastRoute.isEmpty() ? "" : " (menu route " + rm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", rm.describeControls());
        addSummary("Add", rm.openFormIfNeeded());
        addSummary("Form controls", rm.describeControls());

        // 2) All seven boxes
        String entry = rm.enterAll(code, remark, instruction, statCode, statDesc, routeInstruction, mimsDesc);
        String[] expected = {
                "Code", code, "Remark", remark, "Instruction", instruction,
                "StatutoryCode", statCode, "StatutoryDescription", statDesc,
                "RouteInstruction", routeInstruction, "MIMSDescription", mimsDesc };
        boolean entered = rm.allEntered(expected);
        String missing = rm.missing(expected);
        step(page, "Enter code, remark, instruction, statutory code, statutory description, route "
                        + "instruction and MIMS description",
                "Enter all seven values, each in its own field",
                "All seven are entered",
                (entered
                    ? "PASSES because every one of the seven boxes read back its OWN value — checked "
                      + "individually, since Statutory Code and Code (and Route Instruction and "
                      + "Instruction) are easily confused for one another: " + entry
                    : "FAILS — these fields did not receive their value: " + missing + ". Detail: " + entry),
                entered ? "PASS" : "FAIL");

        // 3) Submit -> toast
        String toast = rm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.RouteMaster.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = rm.codeInList(code, remark);
        addSummary("List check", rm.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + rm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the route IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + rm.lastListCheck)
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

        step(page, "Verify the route was created", "Look for " + code + " in the list",
                "The new route is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its remark — was found in the list: "
                      + rm.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + rm.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Values entered", entry);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
