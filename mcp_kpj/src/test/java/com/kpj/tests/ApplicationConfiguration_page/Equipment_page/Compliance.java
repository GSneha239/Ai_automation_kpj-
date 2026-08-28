package com.kpj.tests.ApplicationConfiguration_page.Equipment_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Compliance — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Equipment &gt; <b>Compliance</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Equipment</b> → <b>Compliance</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and <b>Remark</b>.</li>
 *   <li>Select the <b>Compliance</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>The route is read off the menu link rather than guessed. The sibling Complaint Type screen is labelled
 * "Complaint Type" but routed {@code #/ComplaintListType}, so in this module the label is no guide to the
 * URL — guessing lands on the dashboard.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL compliance record in the target environment.</p>
 */
public class Compliance extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public Compliance() { super("ApplicationConfiguration_Compliance"); }

    public static void main(String[] args) {
        Compliance t = new Compliance();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Compliance", "Application Configuration > Equipment > Compliance",
                "&#9888; Creates a REAL compliance record: Add, enter Code and Remark, select the "
                        + "Compliance, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String code = System.getProperty("code",
                "CMP" + String.format("%05d", Math.abs(System.nanoTime() % 100000)));
        String remark = System.getProperty("remark", "Automated compliance " + code);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Equipment_page.Compliance cm =
                new com.kpj.pages.ApplicationConfiguration_page.Equipment_page.Compliance(page);

        // 1) Navigate
        boolean rendered = cm.navigateViaMenu(BASE);
        if (!rendered) addSummary("Equipment submenu offered", cm.lastMenu);
        step(page, "Open Compliance screen",
                "Click Application Configuration -> Equipment -> Compliance",
                "The Compliance screen is shown",
                rendered ? "Opened " + page.url()
                           + (cm.lastRoute.isEmpty() ? "" : " (menu route " + cm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Equipment submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", cm.describeControls());

        // 2) Add
        boolean formOpen = cm.clickAdd();
        addSummary("Form controls", cm.describeControls());
        step(page, "Click Add", "Click Add to open the compliance form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Remark
        String entry = cm.enterDetails(code, remark);
        step(page, "Enter code and remark", "Enter the Code " + code + " and the Remark",
                "Both are entered", entry, cm.detailsEntered(code) ? "PASS" : "FAIL");

        // 4) Compliance
        String compliance = cm.selectCompliance();
        step(page, "Select compliance", "Select the Compliance",
                "A compliance is selected", "Compliance = " + compliance,
                cm.complianceSelected() ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = cm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Equipment_page.Compliance.isSuccess(toast);

        // "Message Not Found." is a missing message-master entry seen elsewhere in this module: the toast
        // then says nothing about whether the record was written, so the grid is the evidence.
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = cm.codeInList(code);
        addSummary("List check", cm.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + cm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the compliance IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + cm.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        step(page, "Verify the compliance was created", "Filter the list for " + code,
                "The new compliance is listed", cm.lastListCheck, inList ? "PASS" : "FAIL");

        addSummary("Code", code);
        addSummary("Remark", remark);
        addSummary("Compliance", cm.lastCompliance);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
