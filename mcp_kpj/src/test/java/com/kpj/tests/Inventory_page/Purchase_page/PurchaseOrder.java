package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PurchaseOrder — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Purchase Order</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Purchase Order</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>Select the <b>Supplier</b>.</li>
 *   <li>Select <b>Purchase Based On</b>.</li>
 *   <li>Click <b>Search Item</b> (opens the "Item Search" dialog).</li>
 *   <li>In the dialog: click <b>Search</b>, tick the checkbox beside the item code, click <b>OK</b>.</li>
 *   <li>Verify the result appears.</li>
 *   <li>Enter the <b>PR Quantity</b>, <b>Free Qty</b>, <b>Unit Price</b> and <b>Net Unit Purchase
 *       Price</b>.</li>
 *   <li>In the Purchase Order section: select the <b>Delivery Place</b>, select the <b>PR Type</b>, enter
 *       the <b>Planned Delivery Date</b>, enter the <b>Delivery Lead Time</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live, so every control is found by FUZZY matching rather than a
 * pinned exact model — see {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrder}. It is a
 * sibling of {@link com.kpj.pages.Inventory_page.Purchase_page.ItemEnquiry},
 * {@link com.kpj.pages.Inventory_page.Purchase_page.Quotation} and
 * {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequest} in the same Purchase module, so
 * proven fixes from those screens (atomic-JS checkbox ticks, the shared "Item Search" picker dialog and
 * its blank-search fallback, targeting the LAST matching element for per-row fields, the Supplier
 * select-with-panel-fallback) are applied from the start. {@code describeControls()} is dumped into the
 * report at each stage so anything still fuzzy can be pinned exactly once this has run.</p>
 *
 * <p>Pin values with {@code -DprQty=}, {@code -DfreeQty=}, {@code -DunitPrice=}, {@code -DnetUnitPrice=},
 * {@code -DplannedDeliveryDate=}, {@code -DleadTime=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL purchase order record in the target environment.</p>
 */
public class PurchaseOrder extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public PurchaseOrder() { super("Inventory_Purchase_PurchaseOrder"); }

    public static void main(String[] args) {
        PurchaseOrder t = new PurchaseOrder();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Purchase - Purchase Order", "Inventory > Purchase > Purchase Order",
                "&#9888; Creates a REAL purchase order: New, select Supplier, select Purchase Based On, "
                        + "Search Item, search and select an item, enter PR Quantity/Free Qty/Unit "
                        + "Price/Net Unit Purchase Price, select Delivery Place/PR Type, enter Planned "
                        + "Delivery Date/Delivery Lead Time, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String prQty = System.getProperty("prQty", "10");
        String freeQty = System.getProperty("freeQty", "1");
        String unitPrice = System.getProperty("unitPrice", "5.00");
        String netUnitPrice = System.getProperty("netUnitPrice", "4.50");
        String plannedDeliveryDate = System.getProperty("plannedDeliveryDate", "31/12/2026");
        String leadTime = System.getProperty("leadTime", "7");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrder po =
                new com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrder(page);

        // 1) Navigate
        boolean rendered = po.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", po.lastMenu);
        step(page, "Open Purchase Order screen",
                "Click Inventory -> Purchase -> Purchase Order",
                "The Purchase Order screen is shown",
                rendered ? "Opened " + page.url()
                           + (po.lastRoute.isEmpty() ? "" : " (menu route " + po.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        String newResult = po.clickNew();
        boolean newOk = po.newClicked();
        step(page, "Click New", "Click New",
                "The entry form opens",
                newOk ? "PASSES: " + newResult : "FAILS: " + newResult,
                newOk ? "PASS" : "FAIL");
        if (!newOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        addSummary("Form controls", po.describeControls());

        // 3) Select Supplier
        String supplier = po.selectSupplier();
        boolean supplierOk = po.supplierSelected();
        step(page, "Select Supplier", "Select the Supplier",
                "A supplier is selected",
                supplierOk ? "PASSES: " + supplier : "FAILS: " + supplier,
                supplierOk ? "PASS" : "FAIL");

        // 4) Purchase Based On
        String basedOn = po.selectPurchaseBasedOn();
        boolean basedOnOk = po.purchaseBasedOnSelected();
        step(page, "Select Purchase Based On", "Select Purchase Based On",
                "A value is selected",
                basedOnOk ? "PASSES: " + basedOn : "FAILS: " + basedOn,
                basedOnOk ? "PASS" : "FAIL");

        // 5) Click Search Item
        String searchItem = po.clickSearchItem();
        boolean pickerOpen = po.itemPickerOpen();
        step(page, "Click Search Item", "Click Search Item",
                "The Item Search dialog opens",
                pickerOpen ? "PASSES: " + searchItem : "FAILS: " + searchItem,
                pickerOpen ? "PASS" : "FAIL");
        if (!pickerOpen) { addSummary("Result", "FAILED — the item picker never opened"); return; }

        addSummary("Item Search dialog controls", po.describeControls());

        // 6) In the dialog: Search, tick checkbox, OK
        String rowsResult = po.clickSearchAndVerifyRows();
        boolean rowsOk = po.rowsFound();
        if (!rowsOk) {
            addSummary("Item picker controls (diagnostic, 0 rows)", po.describeControls());
            try {
                page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                        .setPath(java.nio.file.Paths.get("test-output", "PurchaseOrder-zeroRows-diagnostic.png"))
                        .setFullPage(true));
            } catch (Exception e) { System.out.println("diagnostic screenshot failed: " + e.getMessage()); }
        }
        step(page, "In dialog box - Item Search: click Search", "Click Search",
                "The item picker lists results",
                rowsOk ? "PASSES: " + rowsResult + "  ||  " + po.lastPickerSearch
                       : "FAILS: " + rowsResult + "  ||  " + po.lastPickerSearch,
                rowsOk ? "PASS" : "FAIL");
        if (!rowsOk) { addSummary("Result", "FAILED — no item to select"); return; }

        String tick = po.tickFirstItemInPicker();
        boolean ticked = po.pickerItemTicked();
        step(page, "Tick the checkbox beside the item code",
                "Tick the checkbox beside the item code, for the first item",
                "The item is selected",
                ticked ? "PASSES: " + tick : "FAILS: " + tick,
                ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED — no item was selected"); return; }

        String ok = po.clickOkInPicker();
        boolean okOk = po.pickerOkClicked();
        step(page, "Click OK", "Click OK",
                "The item is added to the form",
                okOk ? "PASSES: " + ok : "FAILS: " + ok,
                okOk ? "PASS" : "FAIL");
        if (!okOk) { addSummary("Result", "FAILED — OK did not confirm the selection"); return; }

        addSummary("Main form controls after OK", po.describeControls());

        // 7) Verify result appears
        boolean resultsAppeared = po.resultsAppeared();
        step(page, "Verify result appear", "Observe the main form after OK",
                "The selected item appears with its own fields",
                resultsAppeared ? "PASSES: the item's fields are visible"
                                 : "FAILS: no per-item fields were found after OK",
                resultsAppeared ? "PASS" : "FAIL");

        // 8) PR Quantity / Free Qty / Unit Price / Net Unit Purchase Price
        String itemFields = po.enterItemFields(prQty, freeQty, unitPrice, netUnitPrice);
        boolean itemFieldsOk = po.itemFieldsEntered();
        step(page, "Enter PR Quantity, Free Qty, Unit Price and Net Unit Purchase Price",
                "Enter PR Quantity " + prQty + ", Free Qty " + freeQty + ", Unit Price " + unitPrice
                        + " and Net Unit Purchase Price " + netUnitPrice,
                "All four are entered",
                itemFieldsOk ? "PASSES: " + itemFields : "FAILS — a field was not found: " + itemFields,
                itemFieldsOk ? "PASS" : "FAIL");

        // 9) Purchase Order section: Delivery Place, PR Type, Planned Delivery Date, Delivery Lead Time
        String deliveryPlace = po.selectDeliveryPlace();
        boolean deliveryPlaceOk = po.deliveryPlaceSelected();
        step(page, "In Purchase Order, select Delivery Place", "Select the Delivery Place",
                "A delivery place is selected",
                deliveryPlaceOk ? "PASSES: " + deliveryPlace : "FAILS: " + deliveryPlace,
                deliveryPlaceOk ? "PASS" : "FAIL");

        String prType = po.selectPrType();
        boolean prTypeOk = po.prTypeSelected();
        step(page, "Select PR Type", "Select the PR Type",
                "A PR type is selected",
                prTypeOk ? "PASSES: " + prType : "FAILS: " + prType,
                prTypeOk ? "PASS" : "FAIL");

        String plannedDate = po.enterPlannedDeliveryDate(plannedDeliveryDate);
        boolean plannedDateOk = po.plannedDeliveryDateEntered();
        step(page, "Enter Planned Delivery Date", "Enter the Planned Delivery Date " + plannedDeliveryDate,
                "The Planned Delivery Date is entered",
                plannedDateOk ? "PASSES: " + plannedDate : "FAILS: " + plannedDate,
                plannedDateOk ? "PASS" : "FAIL");

        String leadTimeResult = po.enterDeliveryLeadTime(leadTime);
        boolean leadTimeOk = po.deliveryLeadTimeEntered();
        step(page, "Enter Delivery Lead Time", "Enter the Delivery Lead Time " + leadTime,
                "The Delivery Lead Time is entered",
                leadTimeOk ? "PASSES: " + leadTimeResult : "FAILS: " + leadTimeResult,
                leadTimeOk ? "PASS" : "FAIL");

        // 10) Save -> toast
        String toast = po.saveAndGetToast();
        boolean success = com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrder.isSuccess(toast);
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + po.lastSaveDiagnostics
                : (success ? toast : "Save not confirmed — the screen answered: \"" + toast + "\"");
        step(page, "Click Save & verify success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, success ? "PASS" : "FAIL");

        addSummary("Supplier", po.lastSupplier);
        addSummary("Purchase Based On", po.lastPurchaseBasedOn);
        addSummary("Selected item", po.lastPickerTick);
        addSummary("Item fields", po.lastItemFields);
        addSummary("Delivery Place / PR Type", po.lastDeliveryPlace + " / " + po.lastPrType);
        addSummary("Planned Delivery Date / Delivery Lead Time", po.lastPlannedDeliveryDate + " / " + po.lastLeadTime);
        addSummary("Result", success ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
