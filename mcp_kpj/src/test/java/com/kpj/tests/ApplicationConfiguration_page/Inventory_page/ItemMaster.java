package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ItemMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Item Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Item Master</b>.</li>
 *   <li>Enter the <b>Service Code</b> and <b>Service Name</b>.</li>
 *   <li>Select the <b>Group</b>, then the <b>Sub Group</b>.</li>
 *   <li>In <b>Pricing Policy Details</b>, tick a pricing policy.</li>
 *   <li>Enter the <b>HSN Code</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Service Code is generated per run, since configuration screens reject a duplicate. Pin the
 * values with {@code -Dcode=}, {@code -Dname=} and {@code -Dhsn=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL item in the target environment.</p>
 */
public class ItemMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ItemMaster() { super("ApplicationConfiguration_ItemMaster"); }

    public static void main(String[] args) {
        ItemMaster t = new ItemMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Item Master", "Application Configuration > Inventory > Item Master",
                "&#9888; Creates a REAL item: enter the Service Code and Name, select Group and Sub "
                        + "Group, tick a pricing policy, enter the HSN Code, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "IM" + stamp);
        String name = System.getProperty("name", "Auto item " + stamp);
        String hsn = System.getProperty("hsn", "HSN" + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemMaster im =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemMaster(page);

        // 1) Navigate
        boolean rendered = im.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", im.lastMenu);
        step(page, "Open Item Master screen",
                "Click Application Configuration -> Inventory -> Item Master",
                "The Item Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (im.lastRoute.isEmpty() ? "" : " (menu route " + im.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", im.describeControls());
        // The requested steps do not mention Add; this clicks it only if the form is not already on
        // screen and says which it found.
        addSummary("Add", im.openFormIfNeeded());
        addSummary("Form controls", im.describeControls());

        // 2) Service Code + Service Name
        String service = im.enterService(code, name);
        boolean serviceOk = im.serviceEntered(code, name);
        step(page, "Enter service code and service name",
                "Enter the Service Code " + code + " and the Service Name " + name,
                "Both are entered",
                (serviceOk
                    ? "PASSES because each box accepted its own value and read it back: " + service
                    : "FAILS because a value did not land in its own field: " + service),
                serviceOk ? "PASS" : "FAIL");

        // 3) Group
        String group = im.selectGroup();
        boolean groupOk = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemMaster.chosen(group);
        step(page, "Select group", "Select the Group",
                "A group is selected",
                (groupOk
                    ? "PASSES because the dropdown holds the chosen group: " + group
                    : "FAILS because no group could be selected: " + group),
                groupOk ? "PASS" : "FAIL");

        // 4) Sub Group — cascades from the Group, so its list is waited for after that choice.
        String sub = im.selectSubGroup();
        boolean subOk = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemMaster.chosen(sub);
        step(page, "Select sub group", "Select the Sub Group (the list cascades from the Group)",
                "A sub group is selected",
                (subOk
                    ? "PASSES because the dropdown holds the chosen sub group: " + sub
                    : "FAILS because no sub group could be selected — the list was waited for after the "
                      + "Group was chosen, so this is not a list read before it filled: " + sub),
                subOk ? "PASS" : "FAIL");

        // 5) Pricing Policy Details -> tick
        String policy = im.tickPricingPolicy();
        boolean policyOk = im.policyTicked();
        step(page, "In Pricing Policy Details, tick the checkbox for a pricing policy",
                "Tick a pricing policy in the Pricing Policy Details section",
                "A pricing policy is ticked",
                (policyOk
                    ? "PASSES because the box changed state when clicked, read back from the control "
                      + "and scoped to the Pricing Policy section: " + policy
                    : "FAILS because no pricing policy could be ticked: " + policy),
                policyOk ? "PASS" : "FAIL");

        // 6) HSN Code
        String hsnEntry = im.enterHsn(hsn);
        boolean hsnOk = im.hsnEntered(hsn);
        step(page, "Enter HSN code", "Enter the HSN Code " + hsn,
                "The HSN code is entered",
                (hsnOk
                    ? "PASSES because the field accepted the value and read it back: " + hsnEntry
                    : "FAILS because the value did not land in the HSN field: " + hsnEntry),
                hsnOk ? "PASS" : "FAIL");

        // 7) Submit -> toast
        String toast = im.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.ItemMaster.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = im.codeInList(code, name);
        addSummary("List check", im.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + im.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the item IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + im.lastListCheck)
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

        addSummary("Service code / name", code + " / " + name);
        addSummary("Group / Sub Group", im.lastGroup + " / " + im.lastSubGroup);
        addSummary("Pricing policy", im.lastPolicy);
        addSummary("HSN", im.lastHsn);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
