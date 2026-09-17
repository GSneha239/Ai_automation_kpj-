package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PurchaseRequest — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Purchase Request</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Purchase Request</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>In Get Items, click <b>Get Items</b>.</li>
 *   <li>Click <b>Search</b>; verify rows of data.</li>
 *   <li>Tick the checkbox beside the item code to choose an item.</li>
 *   <li>Click <b>OK</b>.</li>
 *   <li>Enter the <b>PR Quantity</b>, <b>Unit Price</b> and <b>Amount</b>.</li>
 *   <li>In <b>Purchase Request Details</b>: select the <b>PR Type</b>, enter the <b>Delivery Lead
 *       Time</b> and select the <b>Delivery Place</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live, so every control is found by FUZZY matching rather than a
 * pinned exact model — see {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequest}. It is a
 * sibling of {@link com.kpj.pages.Inventory_page.Purchase_page.ItemEnquiry} and
 * {@link com.kpj.pages.Inventory_page.Purchase_page.Quotation} in the same Purchase module, so proven
 * fixes from those screens (atomic-JS checkbox ticks, the shared "Item Search" picker dialog and its
 * blank-search fallback, targeting the LAST matching element for per-row fields) are applied from the
 * start. {@code describeControls()} is dumped into the report at each stage so anything still fuzzy can
 * be pinned exactly once this has run.</p>
 *
 * <p>Pin values with {@code -Dqty=}, {@code -DunitPrice=}, {@code -Damount=}, {@code -DleadTime=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL purchase request record in the target environment.</p>
 */
public class PurchaseRequest extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public PurchaseRequest() { super("Inventory_Purchase_PurchaseRequest"); }

    public static void main(String[] args) {
        PurchaseRequest t = new PurchaseRequest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Purchase - Purchase Request", "Inventory > Purchase > Purchase Request",
                "&#9888; Creates a REAL purchase request: New, Get Items, search and select an item, "
                        + "enter PR Quantity/Unit Price/Amount, select PR Type, enter Delivery Lead Time, "
                        + "select Delivery Place, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String qty = System.getProperty("qty", "10");
        String unitPrice = System.getProperty("unitPrice", "5.00");
        String amount = System.getProperty("amount", "50.00");
        String leadTime = System.getProperty("leadTime", "7");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequest pr =
                new com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequest(page);

        // 1) Navigate
        boolean rendered = pr.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", pr.lastMenu);
        step(page, "Open Purchase Request screen",
                "Click Inventory -> Purchase -> Purchase Request",
                "The Purchase Request screen is shown",
                rendered ? "Opened " + page.url()
                           + (pr.lastRoute.isEmpty() ? "" : " (menu route " + pr.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        String newResult = pr.clickNew();
        boolean newOk = pr.newClicked();
        step(page, "Click New", "Click New",
                "The entry form opens",
                newOk ? "PASSES: " + newResult : "FAILS: " + newResult,
                newOk ? "PASS" : "FAIL");
        if (!newOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        addSummary("Form controls", pr.describeControls());

        // 3) Get Items
        String getItems = pr.clickGetItems();
        boolean pickerOpen = pr.itemPickerOpen();
        step(page, "In Get Items, click Get Items", "Click Get Items",
                "The item-picker dialog opens",
                pickerOpen ? "PASSES: " + getItems : "FAILS: " + getItems,
                pickerOpen ? "PASS" : "FAIL");
        if (!pickerOpen) { addSummary("Result", "FAILED — the item picker never opened"); return; }

        addSummary("Item picker controls", pr.describeControls());

        // 4) Search + verify rows
        String rowsResult = pr.clickSearchAndVerifyRows();
        boolean rowsOk = pr.rowsFound();
        if (!rowsOk) {
            addSummary("Item picker controls (diagnostic, 0 rows)", pr.describeControls());
            try {
                page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                        .setPath(java.nio.file.Paths.get("test-output", "PurchaseRequest-zeroRows-diagnostic.png"))
                        .setFullPage(true));
            } catch (Exception e) { System.out.println("diagnostic screenshot failed: " + e.getMessage()); }
        }
        step(page, "Click Search; verify rows of data", "Click Search",
                "The item picker lists results",
                rowsOk ? "PASSES: " + rowsResult + "  ||  " + pr.lastPickerSearch
                       : "FAILS: " + rowsResult + "  ||  " + pr.lastPickerSearch,
                rowsOk ? "PASS" : "FAIL");
        if (!rowsOk) { addSummary("Result", "FAILED — no item to select"); return; }

        // 5) Tick the checkbox
        String tick = pr.tickFirstItemInPicker();
        boolean ticked = pr.pickerItemTicked();
        step(page, "Tick the checkbox to choose an item",
                "Tick the checkbox beside the item code, for the first item",
                "The item is selected",
                ticked ? "PASSES: " + tick : "FAILS: " + tick,
                ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED — no item was selected"); return; }

        // 6) OK
        String ok = pr.clickOkInPicker();
        boolean okOk = pr.pickerOkClicked();
        step(page, "Click OK", "Click OK",
                "The item is added to the form",
                okOk ? "PASSES: " + ok : "FAILS: " + ok,
                okOk ? "PASS" : "FAIL");
        if (!okOk) { addSummary("Result", "FAILED — OK did not confirm the selection"); return; }

        addSummary("Main form controls after OK", pr.describeControls());

        // 7) PR Quantity / Unit Price / Amount
        String prDetails = pr.enterPrQuantityUnitPriceAmount(qty, unitPrice, amount);
        boolean prDetailsOk = pr.prQuantityUnitPriceAmountEntered();
        step(page, "Enter PR Quantity, Unit Price and Amount",
                "Enter the PR Quantity " + qty + ", Unit Price " + unitPrice + " and Amount " + amount,
                "All three are entered",
                prDetailsOk ? "PASSES: " + prDetails : "FAILS — a field was not found: " + prDetails,
                prDetailsOk ? "PASS" : "FAIL");

        // 8) Purchase Request Details: PR Type, Delivery Lead Time, Delivery Place
        String prType = pr.selectPrType();
        boolean prTypeOk = pr.prTypeSelected();
        step(page, "In Purchase Request Details, select PR Type", "Select the PR Type",
                "A PR type is selected",
                prTypeOk ? "PASSES: " + prType : "FAILS: " + prType,
                prTypeOk ? "PASS" : "FAIL");

        String leadTimeResult = pr.enterDeliveryLeadTime(leadTime);
        boolean leadTimeOk = pr.deliveryLeadTimeEntered();
        step(page, "Enter Delivery Lead Time", "Enter the Delivery Lead Time " + leadTime,
                "The Delivery Lead Time is entered",
                leadTimeOk ? "PASSES: " + leadTimeResult : "FAILS: " + leadTimeResult,
                leadTimeOk ? "PASS" : "FAIL");

        String deliveryPlace = pr.selectDeliveryPlace();
        boolean deliveryPlaceOk = pr.deliveryPlaceSelected();
        step(page, "Select Delivery Place", "Select the Delivery Place",
                "A delivery place is selected",
                deliveryPlaceOk ? "PASSES: " + deliveryPlace : "FAILS: " + deliveryPlace,
                deliveryPlaceOk ? "PASS" : "FAIL");

        // 9) Save -> toast
        String toast = pr.saveAndGetToast();
        boolean success = com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequest.isSuccess(toast);
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + pr.lastSaveDiagnostics
                : (success ? toast : "Save not confirmed — the screen answered: \"" + toast + "\"");
        step(page, "Click Save & verify success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, success ? "PASS" : "FAIL");

        addSummary("Selected item", pr.lastPickerTick);
        addSummary("PR Quantity / Unit Price / Amount", pr.lastPrDetails);
        addSummary("PR Type", pr.lastPrType);
        addSummary("Delivery Lead Time", pr.lastLeadTime);
        addSummary("Delivery Place", pr.lastDeliveryPlace);
        addSummary("Result", success ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
