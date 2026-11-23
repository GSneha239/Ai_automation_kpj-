package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.Procedure;

// The page object is also named PathologyInvestigationList — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Pathology Investigation List</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>Pathology Investigation List</b>.</li>
 *   <li>Click <b>Service Item</b> (this screen's own list has no Add) → click <b>Add</b>.</li>
 *   <li>Enter the Service Code and Service Name; select the Group and Sub Group; tick a Pricing Policy.</li>
 *   <li>Code Type: select the type, enter the Code and Service Name, click Add.</li>
 *   <li>HSN Code — the form has no HSN box (recorded as MANUAL, see {@code Procedure.enterHsn}).</li>
 *   <li>Tax Details: tick a tax, pick Applicable On / For, enter a Percentage.</li>
 *   <li>Add Set Details: pick the Item Name FROM THE SUGGESTION LIST, click Add.</li>
 *   <li>Click <b>Submit</b> → verify the success toast → verify the saved record in the table.</li>
 * </ol>
 *
 * <p>Group / Sub Group default to {@code Laboratory} / {@code Lab-Biochemistry} (a pathology investigation), Code Type to
 * {@code Service Code}; override with {@code -Dgroup=}, {@code -Dsubgroup=}, {@code -Dcodetype=} (regex on the option text) and the set item search
 * term with {@code -Ditem=}. The Service Code and the code row's Code are limited to 10 characters, so both are {@code <2 letters>ddHHmmss}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL service item in the target environment. The save is slow (every pricing policy is ticked), so the
 * toast is waited for up to 2 minutes.</p>
 */
public class PathologyInvestigationList extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public PathologyInvestigationList() { super("ApplicationConfiguration_Investigation_PathologyInvestigationList"); }

    public static void main(String[] args) {
        PathologyInvestigationList t = new PathologyInvestigationList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Pathology Investigation List",
                "Application Configuration > Investigation > Pathology Investigation List",
                "&#9888; Creates a REAL service item: Service Item > Add, service code and name, group and sub group, pricing policy, code type "
                        + "row, tax details, set details (item picked from the list), Submit, then look the record up in the table.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        // Wall-clock stamps: System.nanoTime() ticks in 100 ns steps on this machine, so its remainders repeat, and the shared QA environment
        // keeps every record ever saved. The Service Code box has maxlength=10, so "PI" + 8 digits.
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String stamp8 = now.format(java.time.format.DateTimeFormatter.ofPattern("ddHHmmss"));
        String stamp12 = now.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyHHmmss"));
        String code = System.getProperty("code", "PI" + stamp8);
        String name = System.getProperty("name", "Auto pathology investigation " + stamp12);
        String codeRowCode = "CT" + stamp8;
        String codeRowName = "Auto code row " + stamp12;
        String hsn = System.getProperty("hsn", "HSN" + stamp8);
        String groupRx = System.getProperty("group", "^Laboratory$");
        String subRx = System.getProperty("subgroup", "^Lab-Biochemistry$");
        String codeTypeRx = System.getProperty("codetype", "^Service Code$");
        String taxPct = "6";
        String[] itemTerms = System.getProperty("item") != null
                ? new String[]{System.getProperty("item")} : new String[]{"para", "amox", "vit", "cef", "tab"};

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.PathologyInvestigationList pil =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.PathologyInvestigationList(page);

        // 1) Navigate
        boolean rendered = pil.navigateViaMenu(BASE);
        if (!rendered) addSummary("Investigation submenu offered", pil.lastMenu);
        step(page, "Open Pathology Investigation List screen",
                "Click Application Configuration -> Investigation -> Pathology Investigation List",
                "The Pathology Investigation List screen is shown",
                rendered ? "Opened " + page.url() + (pil.lastRoute.isEmpty() ? "" : " (menu route " + pil.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Investigation submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Service Item (this list has no Add of its own) and 3) Add
        String serviceItem = pil.clickServiceItem();
        boolean serviceItemOk = pil.serviceItemOpened();
        step(page, "Click Service Item", "Click Service Item (the list screen's only button; it opens the Service Master list)",
                "The Service Master list is shown, with its Add control",
                serviceItemOk ? "PASSES because the click produced the screen's Add control: " + serviceItem
                              : "FAILS because no Add control appeared after clicking Service Item: " + serviceItem,
                serviceItemOk ? "PASS" : "FAIL");
        if (!serviceItemOk) { addSummary("Result", "FAILED — Service Item did not open the Service Master list"); return; }

        String add = pil.openFormIfNeeded();
        boolean formOpen = pil.formOpen();
        step(page, "Click Add", "Click Add to open the entry form", "The entry form is shown",
                formOpen ? "PASSES because the entry form is on screen: " + add : "FAILS because no entry form appeared: " + add,
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — the entry form did not open"); return; }
        addSummary("Form controls", pil.describeControls());

        // 4) Service Code + Service Name
        String service = pil.enterService(code, name);
        boolean serviceOk = pil.serviceEntered(code, name);
        step(page, "Enter service code and service name",
                "Enter the Service Code " + code + " and the Service Name " + name, "Both are entered",
                (serviceOk ? "PASSES because each box accepted its own value and read it back: " : "FAILS because a value did not land in its own field: ") + service,
                serviceOk ? "PASS" : "FAIL");

        // 5) Group, 6) Sub Group
        String group = pil.selectGroupNamed(groupRx);
        boolean groupOk = Procedure.chosen(group);
        step(page, "Select group", "Select the Group (option matching " + groupRx + ")", "A group is selected",
                (groupOk ? "PASSES because the dropdown holds the chosen group: " : "FAILS because no group could be selected: ") + group,
                groupOk ? "PASS" : "FAIL");
        String sub = pil.selectSubGroupNamed(subRx);
        boolean subOk = Procedure.chosen(sub);
        step(page, "Select sub group", "Select the Sub Group (the list cascades from the Group; option matching " + subRx + ")",
                "A sub group is selected",
                (subOk ? "PASSES because the dropdown holds the chosen sub group: " : "FAILS because no sub group could be selected: ") + sub,
                subOk ? "PASS" : "FAIL");

        // 7) Pricing Policy Details
        String policy = pil.tickPricingPolicy();
        boolean policyOk = pil.policyTicked();
        step(page, "In Pricing Policy Details, tick the checkbox for a pricing policy",
                "Tick a pricing policy in the Pricing Policy Details section", "A pricing policy is ticked",
                (policyOk ? "PASSES because the box changed state when clicked, scoped to the Pricing Policy section: " : "FAILS because no pricing policy could be ticked: ") + policy,
                policyOk ? "PASS" : "FAIL");

        // 8) Code Type, 9) Code + Service Name, 10) Add
        String codeType = pil.selectCodeType(codeTypeRx);
        boolean codeTypeOk = Procedure.chosen(codeType);
        step(page, "Select code type", "Select the Code Type (option matching " + codeTypeRx + ")", "A code type is selected",
                (codeTypeOk ? "PASSES because the dropdown holds the chosen code type: " : "FAILS because no code type could be selected: ") + codeType,
                codeTypeOk ? "PASS" : "FAIL");
        String codeRow = pil.enterCodeRow(codeRowCode, codeRowName);
        boolean codeRowOk = pil.codeRowEntered(codeRowCode, codeRowName);
        step(page, "Enter code and service name (code type row)",
                "Enter the Code " + codeRowCode + " and the Service Name " + codeRowName, "Both are entered",
                (codeRowOk ? "PASSES because each box read back its own value and the screen's model holds it: " : "FAILS because a value did not reach the screen's model: ") + codeRow,
                codeRowOk ? "PASS" : "FAIL");
        String codeAdd = pil.clickAddCode(codeRowCode);
        boolean codeAdded = pil.codeAdded();
        step(page, "Click Add (code type)", "Click Add in the Code Type section", "The code row is listed in the Code Type table",
                (codeAdded ? "PASSES because the row is listed: " : "FAILS — ") + codeAdd, codeAdded ? "PASS" : "FAIL");

        // 11) HSN Code — not on this form
        String hsnEntry = pil.enterHsn(hsn);
        boolean hsnOk = pil.hsnEntered(hsn);
        boolean hsnNa = pil.hsnNotApplicable();
        step(page, "Enter HSN code", "Enter the HSN Code " + hsn, "The HSN code is entered",
                hsnOk ? "PASSES because the field accepted the value and read it back: " + hsnEntry
                      : hsnNa ? "NOT APPLICABLE on this form: " + hsnEntry
                              : "FAILS because the value did not land in the HSN field: " + hsnEntry,
                hsnOk ? "PASS" : hsnNa ? "MANUAL" : "FAIL");

        // 12) Tax Details
        String tax = pil.selectTaxDetails(taxPct);
        boolean taxOk = pil.taxSelected(taxPct);
        step(page, "Select tax details", "Tick a tax in Tax Details, pick Applicable On / Applicable For and enter a Percentage",
                "A tax is selected with its percentage",
                (taxOk ? "PASSES because the row is ticked and the screen's model holds the choices: " : "FAILS because the tax was not selected: ") + tax,
                taxOk ? "PASS" : "FAIL");

        // 13) Add Set Details: item name picked from the list, 14) Add
        String item = pil.pickSetItem(itemTerms);
        boolean itemOk = pil.setItemPicked();
        step(page, "Select item name (Add Set Details)", "Type in Item Name and SELECT an item from the suggestion list",
                "An item is selected from the list",
                (itemOk ? "PASSES because the box holds the entry picked from the list: " : "FAILS because no item could be picked from the list: ") + item,
                itemOk ? "PASS" : "FAIL");
        String setAdd = pil.clickAddSetItem();
        boolean setAdded = pil.setItemAdded();
        step(page, "Click Add (set details)", "Click Add in Add Set Details", "The item is listed in List of Set Information",
                (setAdded ? "PASSES because the item is listed: " : "FAILS — ") + setAdd, setAdded ? "PASS" : "FAIL");

        // 15) Submit -> toast
        String toast = pil.submitAndGetToast();
        boolean ok = Procedure.isSuccess(toast);
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        String actual = toast == null || toast.isEmpty() ? "No message appeared — " + pil.lastSaveDiagnostics
                : (ok ? toast : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast (the save is slow — up to 2 minutes)",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                        + (malformed ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\"" : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        // 16) The table — checked even when the toast was not a success, so a silent save is reported rather than hidden.
        boolean inList = pil.codeInList(code, name);
        addSummary("List check", pil.lastListCheck);
        // The list is read while the page is scrolled to its pager; bring the grid (and the filtered row) into view for the screenshot.
        try { page.evaluate("() => { window.scrollTo(0,0); const r=document.querySelector('.ui-grid-row'); const g=document.querySelector('.ui-grid'); (r||g||document.body).scrollIntoView({block:'center'}); }"); } catch (Exception ignore) { }
        step(page, "Verify the table for the entered data", "Search the Service Master list for the Code " + code + " and check its name",
                "The new row shows the entered Code and Service Name",
                inList ? "PASSES because the saved record — the Code AND its Service Name — was found in the table: " + pil.lastListCheck
                       : "FAILS because the saved record could not be found in the table. " + pil.lastListCheck + (ok ? "" : "  (the Submit was not confirmed either)"),
                inList ? "PASS" : "FAIL");

        addSummary("Service code / name", code + " / " + name);
        addSummary("Group / Sub Group", pil.lastGroup + " / " + pil.lastSubGroup);
        addSummary("Code type row", pil.lastCodeType + " | " + pil.lastCodeRow);
        addSummary("Tax", pil.lastTax);
        addSummary("Set item", pil.lastSetItem);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
