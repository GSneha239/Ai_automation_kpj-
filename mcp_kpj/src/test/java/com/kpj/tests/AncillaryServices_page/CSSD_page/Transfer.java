package com.kpj.tests.AncillaryServices_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named Transfer — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; CSSD &gt; <b>Transfer</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>CSSD</b> → <b>Transfer</b>, then <b>New</b>.</li>
 *   <li>Click <b>Get Indent</b> → the Item Search modal opens.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>In <b>Selected Item</b> (indent headers): tick a row → verify it populates <b>Indent Item List</b>.</li>
 *   <li>In <b>Indent Item List</b>: tick a row → verify it populates <b>Item Batch List</b> and the
 *       (second) <b>Selected Item</b> grid.</li>
 *   <li>Ensure the batch is ticked in <b>Selected Item</b> → click <b>OK</b>.</li>
 *   <li>Enter <b>Unit Quantity</b> on the item row now in the main form.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>&#9888; A successful run CREATES a real transfer in the target environment.</p>
 *
 * <h2>Data dependency, re-confirmed live 2026-11-02</h2>
 * <p>Steps 5-6 do not depend on any particular indent existing — search is against whatever indent(s)
 * currently target CSSD as issuing store. But steps 7 onward require the ticked item to have <b>real stock</b>
 * at the issuing store. Ticking an Indent Item List row calls {@code BatcList} (the Item Batch List — it DOES
 * return batch records, e.g. SURGICAL BLADE NO.11 batches 100123 / 05 / 08, and auto-ticks the first); ticking
 * a batch then calls {@code GetIndentStockItemList} (issuing store id, MRP, purchase rate, service item, indent),
 * whose answer fills the bottom Selected Item grid. On 2026-11-02 that stock query answered {@code []} for every
 * item of all five CSSD indents, so nothing can be queued for transfer: a real stock gap at CSSD, not a script
 * defect. When that happens the indent-route step is recorded as MANUAL (with the server's stock-query answer) and
 * the transfer is made the other way the screen offers: <b>Without Indent</b> → Issuing Store CSSD + a Requesting Store
 * (default Operation Theatres, {@code -DrequestingStore=}) → <b>Get Items</b> → Search → an item whose Available
 * Quantity is above 0 (CSSD held 15 of 46 on 2026-11-02, e.g. BD SYRINGE 5CC) → its batch → OK → Unit Quantity → Save.
 * If CSSD holds no stock for any item either, the test fails at that step and says so.</p>
 */
public class Transfer extends DevHisBase {

    public Transfer() { super("AncillaryServices_CSSD_Transfer"); }

    public static void main(String[] args) {
        Transfer t = new Transfer();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - CSSD - Transfer", "Ancillary Services > CSSD > Transfer",
                "&#9888; Creates a REAL store transfer: Get Indent, Search, select item + batch, Unit Quantity, Save.");

        String issuingStore = System.getProperty("issuingStore", "CSSD");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CSSD_page.Transfer xfer =
                new com.kpj.pages.AncillaryServices_page.CSSD_page.Transfer(page);

        // 1) Navigate
        boolean onScreen = xfer.navigateViaMenu();
        step(page, "Open CSSD Transfer screen", "Click Ancillary Services -> CSSD -> Transfer",
                "The Transfer list screen is shown",
                onScreen ? "Opened (Transfer list screen)" : "Did NOT reach the screen",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        boolean formOpen = xfer.clickNew();
        step(page, "Click New", "Click New (AddIssueToStore)",
                "The add-transfer form opens (Get Indent / Get Items visible)",
                formOpen ? "Form opened" : "Form did NOT open",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — add-transfer form not reached"); return; }

        // 3) Get Indent
        boolean modalOpen = xfer.clickGetIndent();
        step(page, "Click Get Indent", "Click Get Indent (OpenIndentdetailsList)",
                "The Item Search modal opens", modalOpen ? "Modal opened" : "Modal did NOT open",
                modalOpen ? "PASS" : "FAIL");
        if (!modalOpen) { addSummary("Result", "FAILED — Item Search modal not reached"); return; }

        // Narrow the search to the issuing store this screen is about, so the result is meaningful
        // rather than an unfiltered dump.
        String storePicked = xfer.selectModalIssuingStore(issuingStore);

        // 4) Search
        boolean searched = xfer.clickModalSearch();
        List<String> indentRows = xfer.selectedIndentRows();
        step(page, "Click Search", "Click Search (SearchIndentItem), Issuing Store = " + storePicked,
                "The Selected Item list shows matching indent(s)",
                searched
                    ? (indentRows.isEmpty() ? "Search ran, but no indent currently targets " + storePicked
                                             : indentRows.size() + " indent(s) found: " + indentRows)
                    : "Search click failed",
                searched && !indentRows.isEmpty() ? "PASS" : "FAIL");
        if (!searched || indentRows.isEmpty()) {
            addSummary("Result", "FAILED — no indent available to select (searched Issuing Store = " + storePicked + ")");
            return;
        }

        // 5) Selected Item -> tick -> verify Indent Item List
        String indentRow = xfer.tickFirstSelectedIndentRow();
        List<String> itemRows = xfer.indentItemListRows();
        boolean itemListOk = !itemRows.isEmpty();
        step(page, "Selected Item · Tick checkbox to select the item", "Tick the first Selected Item (indent) row",
                "The item populates in Indent Item List",
                itemListOk
                    ? "PASSES because Indent Item List shows: " + itemRows
                    : "FAILS — ticked indent \"" + indentRow + "\" but Indent Item List stayed empty",
                itemListOk ? "PASS" : "FAIL");
        if (!itemListOk) { addSummary("Result", "FAILED — Indent Item List did not populate"); return; }

        // 6) Indent Item List -> tick -> verify Item Batch List + Selected Item (bottom)
        // Walk indents/items until one has batch stock (the first item alone is often out of stock at the Issuing Store).
        String itemRow = xfer.tickIndentItemWithStock();
        if (itemRow.isEmpty()) itemRow = xfer.tickFirstIndentItemRow();
        List<String> batchRows = xfer.itemBatchListRows();
        List<String> bottomRows = xfer.bottomSelectedItemRows();
        boolean batchOk = !batchRows.isEmpty() && !bottomRows.isEmpty();
        String route = "Get Indent";
        if (batchOk) {
            step(page, "Indent Item List · Tick checkbox to select the item", "Tick the first Indent Item List row",
                    "The item populates in Item Batch List and Selected Item",
                    "Item Batch List: " + batchRows + " | Selected Item: " + bottomRows, "PASS");
        } else {
            // The indents' items have no stock at the Issuing Store (the server's stock query answers []), so no transfer can be queued from them. That is recorded here, and the transfer is
            // made the other way the screen offers: WITHOUT an indent, picking an item the store really holds through Get Items.
            String indentWhy = "no stock could be selected for any of the " + xfer.triedItems.size() + " indent item(s) tried: " + xfer.triedItems + ". The Item Batch List lists batch records, but ticking a batch "
                    + "leaves the bottom Selected Item grid empty because the server has no stock to offer: " + (xfer.lastStockQuery.isEmpty() ? "(no stock query was seen)" : xfer.lastStockQuery)
                    + " — " + storePicked + " holds no stock for these indent items. " + xfer.lastBatchListState;
            step(page, "Indent Item List · Tick checkbox to select the item", "Tick the first Indent Item List row",
                    "The item populates in Item Batch List and Selected Item",
                    "Not available — " + indentWhy + "  ||  The transfer is made through Without Indent > Get Items instead (next steps).", "MANUAL");
            addSummary("Indent route", "no stock at " + storePicked + " for its " + xfer.triedItems.size() + " indent item(s) — " + xfer.lastStockQuery);

            String requesting = System.getProperty("requestingStore", "Operation Theatres");
            boolean modalClosed = xfer.closeModal();
            String storesProblem = xfer.selectMainStores(storePicked, requesting);
            boolean withoutIndent = storesProblem.isEmpty() && xfer.chooseWithoutIndent();
            step(page, "Without Indent · Select Issuing Store and Requesting Store", "Close Get Indent; select Issuing Store " + storePicked + " and Requesting Store " + requesting + " from their lists; choose Without Indent",
                    "Both stores are selected and Get Items becomes available",
                    withoutIndent ? "Issuing Store \"" + xfer.lastIssuingStore + "\", Requesting Store \"" + xfer.lastRequestingStore + "\" selected; Without Indent chosen, Get Items enabled"
                                  : "FAILED — " + (storesProblem.isEmpty() ? "Get Items stayed disabled after choosing Without Indent" : storesProblem) + (modalClosed ? "" : " (the Get Indent window did not close)"),
                    withoutIndent ? "PASS" : "FAIL");
            if (!withoutIndent) { addSummary("Result", "FAILED — Without Indent route: " + storesProblem); return; }

            boolean itemModal = xfer.clickGetItems();
            step(page, "Without Indent · Click Get Items", "Click Get Items", "The Item Search window opens", itemModal ? "Item Search window opened" : "FAILED — the Item Search window did not open",
                    itemModal ? "PASS" : "FAIL");
            if (!itemModal) { addSummary("Result", "FAILED — Item Search window not reached"); return; }

            boolean searchedItems = xfer.itemSearch();
            String stocked = searchedItems ? xfer.findStockedItem() : "";
            String stockedCode = stocked.contains(" | ") ? stocked.substring(0, stocked.indexOf(" | ")) : "";
            int batches = stockedCode.isEmpty() ? 0 : xfer.tickStockedItem(stockedCode);
            step(page, "Without Indent · Search and select an item that has stock", "Click Search for Store " + storePicked + "; pick the first item with Available Quantity above 0 and click its row",
                    "The item's batches are listed in Items Batch List",
                    batches > 0 ? "Item " + stocked + " selected — Items Batch List: " + xfer.stockedBatchRows()
                                : "FAILED — " + (!searchedItems ? "the search returned no items" : stockedCode.isEmpty() ? "none of the listed items has Available Quantity above 0" : "clicking \"" + stocked + "\" listed no batch"),
                    batches > 0 ? "PASS" : "FAIL");
            if (batches == 0) { addSummary("Result", "FAILED — no item with stock at " + storePicked); return; }
            itemRow = stocked;

            String queued = xfer.tickFirstStockedBatch();
            step(page, "Without Indent · Tick a batch", "Tick the first batch with Available Quantity above 0 in Items Batch List",
                    "The batch is queued in Selected Item", queued.isEmpty() ? "FAILED — ticking the batch queued nothing in Selected Item" : "Queued in Selected Item: " + queued,
                    queued.isEmpty() ? "FAIL" : "PASS");
            if (queued.isEmpty()) { addSummary("Result", "FAILED — the batch was not queued"); return; }
            route = "Without Indent > Get Items";
        }

        // 7) Ensure Selected Item (bottom) is ticked, then OK
        String bottomRow = route.equals("Get Indent") ? xfer.ensureBottomSelectedItemTicked() : xfer.ensureStockedSelectionTicked();
        boolean okClicked = xfer.clickOK();
        step(page, "Selected Item · Tick checkbox & click OK", "Ensure the batch is selected in Selected Item, click OK",
                "The modal closes and the item is added to the main Transfer form",
                okClicked ? "Modal closed; row = " + bottomRow : "Modal did NOT close (likely rejected — no item selected)",
                okClicked ? "PASS" : "FAIL");
        if (!okClicked) { addSummary("Result", "FAILED — OK did not close the modal"); return; }

        // 8) Unit Quantity
        String qty = System.getProperty("unitQty", "1");
        String qtyEntered = xfer.enterUnitQuantity(qty);
        boolean qtyOk = qtyEntered != null && qtyEntered.equals(qty);
        step(page, "Enter unit quantity", "Enter Unit Quantity = " + qty,
                "The quantity is entered on the item row",
                qtyOk ? "Unit Quantity = " + qtyEntered : "Unit Quantity NOT entered (" + qtyEntered + ")",
                qtyOk ? "PASS" : "FAIL");

        // 9) Save -> success toast
        String toast = xfer.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean saveOk = tl.contains("success") || tl.contains("saved");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (saveOk ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save (IUDSave)",
                "'... saved successfully' toast", actual, saveOk ? "PASS" : "FAIL");

        addSummary("Route", route);
        addSummary("Issuing Store", storePicked);
        addSummary("Indent", route.equals("Get Indent") ? indentRow : "(none — transferred without an indent)");
        addSummary("Item", itemRow);
        addSummary("Unit Quantity", qtyEntered);
        addSummary("Result", saveOk ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
