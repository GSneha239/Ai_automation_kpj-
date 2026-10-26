package com.kpj.tests.Inventory_page.Others_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// The page object is also named SellingPriceBatchAdjustment — referenced by its fully-qualified name.

/**
 * Inventory &gt; Others &gt; <b>Selling Price/Batch Adjustment</b>.
 *
 * <ol>
 *   <li>Click <b>New</b>; select the <b>Store</b> (Pharmacy Main Store); click <b>Get Items</b>.</li>
 *   <li>In the <b>Item Search</b> dialog enter the item <b>Code</b> (PH10000000) and the item <b>Name</b> (Panadol Optizorb Caplet 500mg) — a smart select: wait for the suggestions and click the
 *       option; if none appears click <b>Search</b>.</li>
 *   <li>Click the item row to select it; click <b>Yes</b> (the screen's alert popup; its only button is OK) when one appears.</li>
 *   <li>In <b>Items Batch List</b> tick the checkbox of one batch; in <b>Selected Item</b> tick the row's checkbox; click <b>OK</b>.</li>
 *   <li>Enter the <b>Adjustment Selling Price</b>, click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>The requirement names no price. The default is the batch's <i>Available Selling Price</i> + 1 (read from the grid, so the adjustment is always a real change); pin one with
 * {@code -Dmrpadjust.price=}. The store, code and name are {@code -Dmrpadjust.store=} / {@code .code=} / {@code .item=}.</p>
 *
 * <p>This test SAVES A REAL ADJUSTMENT record on every run (it appears in the list with an Approved column, so it awaits approval; this test never approves it).</p>
 */
public class SellingPriceBatchAdjustment extends DevHisBase {

    static final String STORE = System.getProperty("mrpadjust.store", "Pharmacy Main Store");
    static final String CODE = System.getProperty("mrpadjust.code", "PH10000000");
    static final String ITEM = System.getProperty("mrpadjust.item", "Panadol Optizorb Caplet 500mg");
    static final String PRICE = System.getProperty("mrpadjust.price", "");

    public SellingPriceBatchAdjustment() { super("Inventory_Others_SellingPriceBatchAdjustment"); }

    public static void main(String[] args) {
        SellingPriceBatchAdjustment t = new SellingPriceBatchAdjustment();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Others - Selling Price/Batch Adjustment", "Inventory > Others > Selling Price/Batch Adjustment",
                "New; store " + STORE + "; Get Items; item code " + CODE + "; item name (" + ITEM + ") — wait for the suggestions, click the option (Search if none); click the row; Yes in the alert; "
                        + "tick a batch in Items Batch List; tick the Selected Item row; OK; Adjustment Selling Price; Save; verify the success toast. SAVES A REAL ADJUSTMENT.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Others_page.SellingPriceBatchAdjustment sp = new com.kpj.pages.Inventory_page.Others_page.SellingPriceBatchAdjustment(page);

        boolean on = sp.navigateViaMenu();
        step("Open Selling Price/Batch Adjustment", "Click Inventory -> Others -> Selling Price/Batch Adjustment", "The Selling Price/Batch Adjustment list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Selling Price/Batch Adjustment not reached"); return; }

        boolean opened = sp.clickNew();
        step("Click New", "Click New", "The Selling Price/Batch Adjustment form opens", opened ? "Form opened (" + page.url() + ")" : "Problem — " + sp.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — New: " + sp.problems); return; }

        String store = sp.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && sp.ok();
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected", storeOk ? "Selected \"" + store + "\"" : "Not selected — " + sp.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + sp.problems); return; }

        boolean dlg = sp.clickGetItems();
        step("Click Get Items", "Click Get Items", "The Item Search dialog opens", dlg ? "Item Search dialog opened" : "Problem — " + sp.problems, dlg ? "PASS" : "FAIL");
        if (!dlg) { addSummary("Result", "FAILED — Get Items: " + sp.problems); return; }

        boolean codeOk = sp.enterCode(CODE);
        step("Enter code", "Enter the Item Code (" + CODE + ")", "The code is accepted", codeOk ? "Item Code \"" + sp.lastCode + "\" entered" : "Problem — " + sp.problems, codeOk ? "PASS" : "FAIL");
        if (!codeOk) { addSummary("Result", "FAILED — item code: " + sp.problems); return; }

        boolean typed = sp.typeItemName(ITEM);
        step("Enter name", "Type \"" + ITEM + "\" in the Item Name box (a smart select)", "The name is typed", typed ? "\"" + ITEM + "\" typed" : "Problem — " + sp.problems, typed ? "PASS" : "FAIL");
        if (!typed) { addSummary("Result", "FAILED — item name: " + sp.problems); return; }

        // wait for the option and click it; if none appears click Search
        List<String> sugg = sp.awaitSuggestions(ITEM, 15000);
        boolean viaOption = !sugg.isEmpty();
        boolean listed;
        String how;
        if (viaOption) {
            String picked = sp.pickSuggestion(ITEM);
            listed = !picked.isEmpty() && sp.ok() && !sp.itemRows().isEmpty();
            how = listed ? "Waited for the suggestions (" + sugg.size() + "), clicked \"" + picked.replaceAll("\\s+", " ") + "\" — the item grid shows " + sp.itemRows().size() + " row(s)"
                         : "The suggestion was not usable — " + sp.problems;
        } else {
            boolean searched = sp.clickSearch();
            listed = searched && !sp.itemRows().isEmpty();
            how = listed ? "No suggestion appeared (" + sp.searchDiagnosis + "), so Search was clicked — the item grid shows " + sp.itemRows().size() + " row(s)"
                         : "No suggestion appeared (" + sp.searchDiagnosis + ") and Search found nothing — " + sp.problems;
        }
        step("Wait for the option and select it (Search if none appears)", "Wait for the suggestions for \"" + ITEM + "\" and click the option; click Search if none appears",
                "The item is listed in the item grid", how, listed ? "PASS" : "FAIL");
        if (!listed) { addSummary("Result", "FAILED — item not listed: " + how); return; }

        // click the row
        String row = sp.clickItemRow();
        boolean rowOk = !row.isEmpty() && sp.ok();
        step("Click on the row to select", "Click the item row in the grid", "The row is selected and its batches load",
                rowOk ? "Clicked \"" + row.replaceAll("\\s+", " ") + "\"" + (sp.lastAlert.isEmpty() ? " — no alert popup appeared" : " — the screen raised an alert: \"" + sp.lastAlert + "\"") : "Problem — " + sp.problems,
                rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — row: " + sp.problems); return; }

        // the alert popup (its only button is OK)
        String btn = sp.answerAlert();
        boolean alertOk = sp.ok() && (sp.lastAlert.isEmpty() || !btn.isEmpty());
        step("Click Yes — confirmation/alert popup", "Click the popup's affirmative button (Yes; this alert only has OK)",
                "The popup is dismissed and the Items Batch List fills in",
                !alertOk ? "Problem — " + sp.problems
                         : sp.lastAlert.isEmpty() ? "No popup appeared for this item, so there was nothing to click; Items Batch List has " + sp.batchRows().size() + " batch(es)"
                                                  : "Clicked \"" + btn + "\" on \"" + sp.lastAlert + "\"; Items Batch List has " + sp.batchRows().size() + " batch(es)",
                alertOk ? "PASS" : "FAIL");
        if (!alertOk) { addSummary("Result", "FAILED — alert popup: " + sp.problems); return; }

        // Items Batch List
        String batch = sp.tickBatchRow();
        boolean batchOk = !batch.isEmpty() && sp.ok();
        step("In Items Batch List, tick the checkbox of a row", "Tick the checkbox of one batch row", "The batch is ticked and appears in Selected Item",
                batchOk ? "Ticked batch \"" + sp.lastBatchCode + "\" (" + batch + "); Selected Item shows " + sp.selectedRows().size() + " row(s)" : "Problem — " + sp.problems, batchOk ? "PASS" : "FAIL");
        if (!batchOk) { addSummary("Result", "FAILED — batch: " + sp.problems); return; }

        // Selected Item
        String sel = sp.tickSelectedItemRow();
        boolean selOk = !sel.isEmpty() && sp.ok();
        step("In Selected Item, tick the checkbox of the row", "Tick the checkbox of the row in Selected Item", "The row is ticked", selOk ? "Ticked \"" + sel + "\"" : "Problem — " + sp.problems, selOk ? "PASS" : "FAIL");
        if (!selOk) { addSummary("Result", "FAILED — selected item: " + sp.problems); return; }

        // OK
        boolean okOk = sp.clickOk();
        List<String> cells = sp.gridCells();
        step("Click OK", "Click OK in the Item Search dialog", "The dialog closes and the batch is added to the adjustment grid",
                okOk ? "Dialog closed; the grid shows: " + sp.lastGridRow : "Problem — " + sp.problems, okOk ? "PASS" : "FAIL");
        if (!okOk) { addSummary("Result", "FAILED — OK: " + sp.problems); return; }

        // Adjustment Selling Price
        String price = PRICE;
        if (price.isEmpty()) {
            try { price = new BigDecimal(sp.lastAvailablePrice.trim()).add(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP).toPlainString(); }
            catch (Exception e) { price = "25.00"; }
        }
        boolean priceOk = sp.enterAdjustmentPrice(price);
        step("Enter Adjustment Selling Price", "Enter the Adjustment Selling Price (" + price + (PRICE.isEmpty() ? ": the available selling price " + sp.lastAvailablePrice + " + 1" : "") + ")",
                "The price is accepted", priceOk ? "Adjustment Selling Price " + sp.lastPrice + " entered; the grid shows: " + sp.lastGridRow : "Problem — " + sp.problems, priceOk ? "PASS" : "FAIL");
        if (!priceOk) { addSummary("Result", "FAILED — adjustment price: " + sp.problems); return; }

        // Save
        String toast = sp.saveAndGetToast();
        boolean ok = sp.ok() && sp.saveSucceeded();
        step("Click Save", "Click Save" + (sp.lastDialog.isEmpty() ? "" : " and answer the confirmation \"" + sp.lastDialog + "\""), "The adjustment is saved",
                sp.lastDialog.isEmpty() ? "Save went straight through (no confirmation)" : "Confirmation: \"" + sp.lastDialog + "\" — answered", sp.problems.isEmpty() ? "PASS" : "FAIL");
        step("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (sp.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + sp.lastSaveMessage + "\"")
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + sp.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + sp.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Store", sp.lastStore);
        addSummary("Item", CODE + " — " + ITEM);
        addSummary("Batch", sp.lastBatchCode + " (" + sp.lastBatch + ")");
        addSummary("Available -> Adjustment Selling Price", sp.lastAvailablePrice + " -> " + sp.lastPrice);
        addSummary("Adjustment grid row", sp.lastGridRow);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
