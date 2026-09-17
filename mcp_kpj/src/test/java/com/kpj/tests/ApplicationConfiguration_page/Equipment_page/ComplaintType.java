package com.kpj.tests.ApplicationConfiguration_page.Equipment_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ComplaintType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Equipment &gt; <b>Complaint Type</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Equipment</b> → <b>Complaint Type</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b>, <b>Remark</b> and <b>Expected Resolution</b>.</li>
 *   <li>Select the <b>Period</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>Every field is dumped before anything is entered, because sibling screens in this module have turned
 * out to lack fields the steps assume (Red Cell Serology Group has no Remark at all) or to carry a
 * mandatory dropdown with no options. The report says what is genuinely on the screen rather than only
 * that a step failed.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL complaint type in the target environment.</p>
 */
public class ComplaintType extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ComplaintType() { super("ApplicationConfiguration_Equipment_ComplaintType"); }

    public static void main(String[] args) {
        ComplaintType t = new ComplaintType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Equipment - Complaint Type", "Application Configuration > Equipment > Complaint Type",
                "&#9888; Creates a REAL complaint type: Add, enter Code, Remark and Expected Resolution, "
                        + "select the Period, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String code = System.getProperty("code",
                "CT" + String.format("%05d", Math.abs(System.nanoTime() % 100000)));
        String remark = System.getProperty("remark", "Automated complaint type " + code);
        String resolution = System.getProperty("expectedResolution", "4");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Equipment_page.ComplaintType ct =
                new com.kpj.pages.ApplicationConfiguration_page.Equipment_page.ComplaintType(page);

        // 1) Navigate
        boolean rendered = ct.navigateViaMenu(BASE);
        if (!rendered) addSummary("Equipment submenu offered", ct.lastMenu);
        step(page, "Open Complaint Type screen",
                "Click Application Configuration -> Equipment -> Complaint Type",
                "The Complaint Type screen is shown",
                rendered ? "Opened " + page.url()
                           + (ct.lastRoute.isEmpty() ? "" : " (menu route " + ct.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Equipment submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", ct.describeControls());

        // 2) Add
        boolean formOpen = ct.clickAdd();
        addSummary("Form controls", ct.describeControls());
        step(page, "Click Add", "Click Add to open the complaint type form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Remark + Expected Resolution
        String entry = ct.enterDetails(code, remark, resolution);
        step(page, "Enter code, remark and expected resolution",
                "Enter the Code " + code + ", the Remark and the Expected Resolution " + resolution,
                "All three are entered", entry, ct.detailsEntered(code) ? "PASS" : "FAIL");

        // 4) Period
        String period = ct.selectPeriod();
        step(page, "Select period", "Select the Period",
                "A period is selected", "Period = " + period,
                ct.periodSelected() ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = ct.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Equipment_page.ComplaintType.isSuccess(toast);

        // "Message Not Found." is a missing message-master entry seen elsewhere in this module: the toast
        // then says nothing about whether the record was written, so the grid is the evidence.
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = ct.codeInList(code);
        addSummary("List check", ct.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + ct.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the complaint type IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + ct.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        step(page, "Verify the complaint type was created", "Filter the list for " + code,
                "The new complaint type is listed", ct.lastListCheck, inList ? "PASS" : "FAIL");

        addSummary("Code", code);
        addSummary("Remark", remark);
        addSummary("Expected Resolution / Period", resolution + " / " + ct.lastPeriod);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
