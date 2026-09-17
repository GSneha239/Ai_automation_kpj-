package com.kpj.tests.Inventory_page.Others_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named GatePassOutList — referenced by its fully-qualified name.

/**
 * Inventory &gt; Others &gt; <b>Gate Pass Out List</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Others</b> → <b>Gate Pass Out List</b>.</li>
 *   <li><b>[New]</b> "Add a new tab - New" (reported plainly, not assumed to be a real tab); click
 *       <b>New</b> (opens a flat entry form, no item-search dialog); select <b>Store</b>; select
 *       <b>Supplier</b>; enter <b>Item Code</b>, <b>Item Name</b>, <b>Quantity</b>; enter
 *       <b>Issue To</b>; click <b>Save</b>; verify the success toast.</li>
 * </ol>
 *
 * <p>First screen in the new {@code Inventory_page.Others_page} submodule, confirmed at route
 * {@code #/GatePassOutList} under the "Others" Inventory submenu. Every control was walked by hand in a
 * live browser before writing any selector (the same discipline applied to {@link
 * com.kpj.pages.Inventory_page.Transfer_page.ReceiveIssueItem}), which surfaced a real, reproducible
 * APPLICATION BUG rather than a test defect: this screen's own Item Code/Item Name autocomplete fires
 * {@code POST /api/GRN/fetchItemByStore} on every keystroke, and that request returns HTTP 400 every
 * time — the server's own error names a missing {@code PsychotropicDrug} boolean parameter the client
 * never sends. No item line can be genuinely attached to a Gate Pass Out while this stands, on any input.
 * Confirmed live, though, that Save does not require one: with zero item rows, Save still succeeds
 * server-side ("Gate Pass Out Saved Successfully", a real TransNo assigned) — so this test still
 * completes the full requested flow end-to-end and types the requested item values into the raw fields,
 * while the report records plainly that no real item line gets attached, per {@link
 * com.kpj.pages.Inventory_page.Others_page.GatePassOutList#enterItemDetails}'s own diagnostics.</p>
 *
 * <p>&#9888; A successful run creates ONE REAL Gate Pass Out record in the target environment.</p>
 */
public class GatePassOutList extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public GatePassOutList() { super("Inventory_Others_GatePassOutList"); }

    public static void main(String[] args) {
        GatePassOutList t = new GatePassOutList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Gate Pass Out List", "Inventory > Others > Gate Pass Out List",
                "&#9888; Creates ONE REAL Gate Pass Out record: [New] click New, select Store, select "
                        + "Supplier, enter Item Code/Item Name/Quantity, enter Issue To, click Save, "
                        + "verify the success toast. Note: this screen's own Item Code/Item Name "
                        + "autocomplete is confirmed live to return HTTP 400 on every lookup (a real "
                        + "application bug, not a test defect), so no real item line gets attached — "
                        + "Save is confirmed live to succeed regardless.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String itemCode = System.getProperty("itemCode", "PARA");
        String itemName = System.getProperty("itemName", "PARACETAMOL");
        String qty = System.getProperty("qty", "1");
        String issueTo = System.getProperty("issueTo", "Test Issue To");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Others_page.GatePassOutList gpo =
                new com.kpj.pages.Inventory_page.Others_page.GatePassOutList(page);

        // 1) Navigate
        boolean rendered = gpo.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", gpo.lastMenu);
        step(page, "Open Gate Pass Out List screen",
                "Click Inventory -> Others -> Gate Pass Out List",
                "The Gate Pass Out List screen is shown",
                rendered ? "Opened " + page.url()
                           + (gpo.lastRoute.isEmpty() ? "" : " (menu route " + gpo.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean contentLoaded = gpo.waitForRealContent(15000);
        addSummary("Content loaded", contentLoaded ? "yes" : "NO — still just the header shell after 15s");

        addSummary("List screen controls", gpo.describeControls());

        // ==================== [New] tab ====================

        // 2) "Add a new tab - New" — reported plainly; not assumed to be a real tab.
        String newTab = gpo.clickNewTab();
        addSummary("\"Add a new tab - New\"", newTab);

        // 3) Click New
        String newResult = gpo.clickNew();
        boolean newOk = gpo.newClicked();
        step(page, "[New] Click New", "Click New",
                "The entry form opens",
                newOk ? "PASSES: " + newResult : "FAILS: " + newResult,
                newOk ? "PASS" : "FAIL");
        if (!newOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        addSummary("Entry form controls", gpo.describeControls());

        // 4) Select Store
        String storeResult = gpo.selectStore();
        boolean storeOk = gpo.storeSelected();
        step(page, "[New] Select store", "Select the Store",
                "A store is selected",
                storeOk ? "PASSES: " + storeResult : "FAILS: " + storeResult,
                storeOk ? "PASS" : "FAIL");

        // 5) Select Supplier
        String supplierResult = gpo.selectSupplier();
        boolean supplierOk = gpo.supplierSelected();
        step(page, "[New] Select supplier", "Select the Supplier",
                "A supplier is selected",
                supplierOk ? "PASSES: " + supplierResult : "FAILS: " + supplierResult,
                supplierOk ? "PASS" : "FAIL");

        // 6) Enter Item Code, Item Name, Quantity
        String itemResult = gpo.enterItemDetails(itemCode, itemName, qty);
        boolean itemOk = gpo.itemDetailsTyped();
        step(page, "[New] Enter item code, item name, quantity",
                "Enter Item Code " + itemCode + ", Item Name " + itemName + ", Quantity " + qty,
                "The values are typed into the fields",
                itemOk ? "PASSES: " + itemResult : "FAILS: " + itemResult,
                itemOk ? "PASS" : "FAIL");

        // 7) Enter Issue To
        String issueToResult = gpo.enterIssueTo(issueTo);
        boolean issueToOk = gpo.issueToEntered();
        step(page, "[New] Enter issue to", "Enter Issue To " + issueTo,
                "Issue To is entered",
                issueToOk ? "PASSES: " + issueToResult : "FAILS: " + issueToResult,
                issueToOk ? "PASS" : "FAIL");

        // 8) Click Save
        gpo.clickSave();
        boolean saveClickedOk = gpo.saveClicked();
        step(page, "[New] Click Save", "Click Save",
                "The Save action fires",
                saveClickedOk ? "PASSES: " + gpo.lastSaveDiagnostics : "FAILS: " + gpo.lastSaveDiagnostics,
                saveClickedOk ? "PASS" : "FAIL");
        if (!saveClickedOk) { addSummary("Result", "FAILED — the Save button was not found"); return; }

        // 9) Verify success toast message
        String toast = gpo.waitForSaveToast();
        boolean success = com.kpj.pages.Inventory_page.Others_page.GatePassOutList.isSuccess(toast);
        String actual = (toast == null || toast.isEmpty())
                ? "No message appeared — " + gpo.lastSaveDiagnostics
                : (success ? toast : "Not confirmed — the screen answered: \"" + toast + "\"");
        step(page, "[New] Verify success toast message", "Wait for the success toast",
                "'... successfully' toast", actual, success ? "PASS" : "FAIL");

        addSummary("Store", gpo.lastStore);
        addSummary("Supplier", gpo.lastSupplier);
        addSummary("Item details", gpo.lastItemDetails);
        addSummary("Issue To", gpo.lastIssueTo);
        addSummary("Save toast result", success ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
