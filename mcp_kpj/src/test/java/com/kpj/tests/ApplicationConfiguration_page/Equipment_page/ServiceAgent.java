package com.kpj.tests.ApplicationConfiguration_page.Equipment_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ServiceAgent — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Equipment &gt; <b>Service Agent</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Equipment</b> → <b>Service Agent</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Agent Code</b> and <b>Agent Name</b>.</li>
 *   <li>In <b>Contact Details</b>: enter Name, Telephone Number, Designation and Cell.</li>
 *   <li>Click <b>Add</b> to put the contact in the grid.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Agent Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>Two habits carried over from the sibling screens in this module: the route comes from the menu link
 * rather than the label (Complaint Type is routed {@code #/ComplaintListType}), and the added contact is
 * confirmed by finding its name in the grid rather than by the Add click landing.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL service agent in the target environment.</p>
 */
public class ServiceAgent extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ServiceAgent() { super("ApplicationConfiguration_ServiceAgent"); }

    public static void main(String[] args) {
        ServiceAgent t = new ServiceAgent();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Service Agent", "Application Configuration > Equipment > Service Agent",
                "&#9888; Creates a REAL service agent: Add, enter the Agent Code and Name, fill the "
                        + "Contact Details, Add the contact, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "SA" + stamp);
        String agentName = System.getProperty("agentName", "Auto Service Agent " + stamp);
        String contactName = System.getProperty("contactName", "Contact " + stamp);
        String telephone = System.getProperty("telephone", "0312345" + stamp.substring(0, 3));
        String designation = System.getProperty("designation", "Engineer");
        String cell = System.getProperty("cell", "0198765" + stamp.substring(0, 3));

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Equipment_page.ServiceAgent sa =
                new com.kpj.pages.ApplicationConfiguration_page.Equipment_page.ServiceAgent(page);

        // 1) Navigate
        boolean rendered = sa.navigateViaMenu(BASE);
        if (!rendered) addSummary("Equipment submenu offered", sa.lastMenu);
        step(page, "Open Service Agent screen",
                "Click Application Configuration -> Equipment -> Service Agent",
                "The Service Agent screen is shown",
                rendered ? "Opened " + page.url()
                           + (sa.lastRoute.isEmpty() ? "" : " (menu route " + sa.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Equipment submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", sa.describeControls());

        // 2) Add
        boolean formOpen = sa.clickAdd();
        addSummary("Form controls", sa.describeControls());
        step(page, "Click Add", "Click Add to open the service agent form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Agent code + name
        String agent = sa.enterAgentDetails(code, agentName);
        step(page, "Enter agent code and agent name",
                "Enter the Agent Code " + code + " and the Agent Name",
                "Both are entered", agent, sa.agentEntered(code) ? "PASS" : "FAIL");

        // 4) Contact details
        String contact = sa.enterContactDetails(contactName, telephone, designation, cell);
        step(page, "In Contact Details, enter name, telephone number, designation and cell",
                "Fill the Contact Details block",
                "All four contact fields are entered", contact, sa.contactEntered() ? "PASS" : "FAIL");

        // 5) Add the contact row
        String added = sa.clickAddContact(contactName);
        step(page, "Click Add (the contact)", "Click Add to put the contact in the grid",
                "A contact row carrying the entered name is added", added,
                sa.contactRowAdded() ? "PASS" : "FAIL");

        // 6) Submit -> toast
        String toast = sa.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Equipment_page.ServiceAgent.isSuccess(toast);

        // "Message Not Found." is a missing message-master entry seen elsewhere in this module: the toast
        // then says nothing about whether the record was written, so the grid is the evidence.
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = sa.codeInList(code, agentName);
        addSummary("List check", sa.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + sa.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the service agent IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + sa.lastListCheck)
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

        step(page, "Verify the service agent was created",
                "Filter the list's Code column for " + code + " and check the row",
                "The new service agent is listed",
                (inList
                    ? "PASSES because the record is genuinely there: a single row carries BOTH the code "
                      + code + " and the agent name \"" + agentName + "\", so the toast is backed by a "
                      + "real row rather than taken on trust. " + sa.lastListCheck
                    : "FAILS because the saved record is NOT in the list — so whatever the toast said, "
                      + "the agent was not added. The Code column's own filter box was used (the grid has "
                      + "one per column, and filtering the wrong one empties the grid), and the row had "
                      + "to carry the agent name as well as the code, so a pre-existing record with the "
                      + "same code cannot be mistaken for this one. " + sa.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Agent", code + " / " + agentName);
        addSummary("Contact", sa.lastContact);
        addSummary("Contact row", sa.lastAddRow);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
