package com.kpj.tests.Inventory_page.Others_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named WriteOff — referenced by its fully-qualified name.

/**
 * Inventory &gt; Others &gt; <b>Write Off</b> (the screen's own title is "Write-Off").
 *
 * <ol>
 *   <li>Click <b>New</b>; select the <b>Store</b> (Pharmacy Main Store); click <b>Search Items</b>.</li>
 *   <li>In the <b>Item Search</b> dialog enter the item <b>Code</b> (PH10000000) and the item <b>Name</b> (Panadol Optizorb Caplet 500mg) — a smart select: wait for the suggestions and click the
 *       option; if none appears click <b>Search</b>.</li>
 *   <li>Click the item row to select it; click <b>Yes</b> (the screen's alert popup, if one appears; its only button is OK).</li>
 *   <li>In <b>Items Batch List</b> tick the checkbox of one batch; in <b>Selected Item</b> tick the row's checkbox; click <b>OK</b>.</li>
 *   <li>Enter the <b>Write-Off Quantity</b>, select the <b>Reason Of Write Off</b>, enter the <b>Justification</b>, click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>The requirement names no quantity, reason or justification: the defaults are quantity 1, reason "Damaged" and a short justification text. Pin them with
 * {@code -Dwriteoff.qty=}, {@code -Dwriteoff.reason=} and {@code -Dwriteoff.justification=}; the store, code and name are {@code -Dwriteoff.store=} / {@code .code=} / {@code .item=}.</p>
 *
 * <p>This test SAVES A REAL WRITE-OFF record on every run (the list has Approved / Rejected columns, so it awaits approval; this test never approves it).</p>
 */
public class WriteOff extends DevHisBase {

    static final String STORE = System.getProperty("writeoff.store", "Pharmacy Main Store");
    static final String CODE = System.getProperty("writeoff.code", "PH10000000");
    static final String ITEM = System.getProperty("writeoff.item", "Panadol Optizorb Caplet 500mg");
    static final String QTY = System.getProperty("writeoff.qty", "1");
    static final String REASON = System.getProperty("writeoff.reason", "Damaged");
    static final String JUSTIFICATION = System.getProperty("writeoff.justification", "Automated test: item damaged, written off");

    public WriteOff() { super("Inventory_Others_WriteOff"); }

    public static void main(String[] args) {
        WriteOff t = new WriteOff();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Others - Write Off", "Inventory > Others > Write Off",
                "New; store " + STORE + "; Search Items; item code " + CODE + "; item name (" + ITEM + ") — wait for the suggestions, click the option (Search if none); click the row; Yes in the alert if one "
                        + "appears; tick a batch in Items Batch List; tick the Selected Item row; OK; Write-Off Quantity; reason; justification; Save; verify the success toast. SAVES A REAL WRITE-OFF.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Others_page.WriteOff wo = new com.kpj.pages.Inventory_page.Others_page.WriteOff(page);

        boolean on = wo.navigateViaMenu();
        step("Open Write Off", "Click Inventory -> Others -> Write Off", "The Write-Off list is shown", on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Write Off not reached"); return; }

        boolean opened = wo.clickNew();
        step("Click New", "Click New", "The Write-Off form opens", opened ? "Form opened (" + page.url() + ")" : "Problem — " + wo.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — New: " + wo.problems); return; }

        String store = wo.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && wo.ok();
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected", storeOk ? "Selected \"" + store + "\"" : "Not selected — " + wo.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + wo.problems); return; }

        boolean dlg = wo.clickSearchItems();
        step("Click Search Items", "Click Search Items", "The Item Search dialog opens", dlg ? "Item Search dialog opened" : "Problem — " + wo.problems, dlg ? "PASS" : "FAIL");
        if (!dlg) { addSummary("Result", "FAILED — Search Items: " + wo.problems); return; }

        boolean codeOk = wo.enterCode(CODE);
        step("Enter code", "Enter the Item Code (" + CODE + ")", "The code is accepted", codeOk ? "Item Code \"" + wo.lastCode + "\" entered" : "Problem — " + wo.problems, codeOk ? "PASS" : "FAIL");
        if (!codeOk) { addSummary("Result", "FAILED — item code: " + wo.problems); return; }

        boolean typed = wo.typeItemName(ITEM);
        step("Enter name", "Type \"" + ITEM + "\" in the Item Name box (a smart select)", "The name is typed", typed ? "\"" + ITEM + "\" typed" : "Problem — " + wo.problems, typed ? "PASS" : "FAIL");
        if (!typed) { addSummary("Result", "FAILED — item name: " + wo.problems); return; }

        // wait for the option and click it; if none appears click Search
        List<String> sugg = wo.awaitSuggestions(ITEM, 15000);
        boolean listed;
        String how;
        if (!sugg.isEmpty()) {
            String picked = wo.pickSuggestion(ITEM);
            listed = !picked.isEmpty() && wo.ok() && !wo.itemRows().isEmpty();
            how = listed ? "Waited for the suggestions (" + sugg.size() + "), clicked \"" + picked.replaceAll("\\s+", " ") + "\" — the item grid shows " + wo.itemRows().size() + " row(s)"
                         : "The suggestion was not usable — " + wo.problems;
        } else {
            boolean searched = wo.clickSearch();
            listed = searched && !wo.itemRows().isEmpty();
            how = listed ? "No suggestion appeared (" + wo.searchDiagnosis + "), so Search was clicked — the item grid shows " + wo.itemRows().size() + " row(s)"
                         : "No suggestion appeared (" + wo.searchDiagnosis + ") and Search found nothing — " + wo.problems;
        }
        step("Wait for the option and select it (Search if none appears)", "Wait for the suggestions for \"" + ITEM + "\" and click the option; click Search if none appears",
                "The item is listed in the item grid", how, listed ? "PASS" : "FAIL");
        if (!listed) { addSummary("Result", "FAILED — item not listed: " + how); return; }

        String row = wo.clickItemRow();
        boolean rowOk = !row.isEmpty() && wo.ok();
        step("Click on the row to select", "Click the item row in the grid", "The row is selected and its batches load",
                rowOk ? "Clicked \"" + row.replaceAll("\\s+", " ") + "\"" + (wo.lastAlert.isEmpty() ? " — no alert popup appeared" : " — the screen raised an alert: \"" + wo.lastAlert + "\"") : "Problem — " + wo.problems,
                rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — row: " + wo.problems); return; }

        String btn = wo.answerAlert();
        boolean alertOk = wo.ok() && (wo.lastAlert.isEmpty() || !btn.isEmpty());
        step("Click Yes — confirmation/alert popup (if any)", "Click the popup's affirmative button (Yes; this alert only has OK) if one appeared",
                "The popup is dismissed and the Items Batch List fills in",
                !alertOk ? "Problem — " + wo.problems
                         : wo.lastAlert.isEmpty() ? "No popup appeared for this item, so there was nothing to click; Items Batch List has " + wo.batchRows().size() + " batch(es)"
                                                  : "Clicked \"" + btn + "\" on \"" + wo.lastAlert + "\"; Items Batch List has " + wo.batchRows().size() + " batch(es)",
                alertOk ? "PASS" : "FAIL");
        if (!alertOk) { addSummary("Result", "FAILED — alert popup: " + wo.problems); return; }

        String batch = wo.tickBatchRow();
        boolean batchOk = !batch.isEmpty() && wo.ok();
        step("In Items Batch List, tick the checkbox of a row", "Tick the checkbox of one batch row", "The batch is ticked and appears in Selected Item",
                batchOk ? "Ticked batch \"" + wo.lastBatchCode + "\" (" + batch + "); Selected Item shows " + wo.selectedRows().size() + " row(s)" : "Problem — " + wo.problems, batchOk ? "PASS" : "FAIL");
        if (!batchOk) { addSummary("Result", "FAILED — batch: " + wo.problems); return; }

        String sel = wo.tickSelectedItemRow();
        boolean selOk = !sel.isEmpty() && wo.ok();
        step("In Selected Item, tick the checkbox of the row", "Tick the checkbox of the row in Selected Item", "The row is ticked", selOk ? "Ticked \"" + sel + "\"" : "Problem — " + wo.problems, selOk ? "PASS" : "FAIL");
        if (!selOk) { addSummary("Result", "FAILED — selected item: " + wo.problems); return; }

        boolean okOk = wo.clickOk();
        wo.gridCells();
        step("Click OK", "Click OK in the Item Search dialog", "The dialog closes and the batch is added to the write-off grid",
                okOk ? "Dialog closed; the grid shows: " + wo.lastGridRow : "Problem — " + wo.problems, okOk ? "PASS" : "FAIL");
        if (!okOk) { addSummary("Result", "FAILED — OK: " + wo.problems); return; }

        // the write-off quantity cannot exceed the batch's available stock
        String qty = QTY;
        try {
            double stock = Double.parseDouble(wo.lastAvailableStock.trim()), want = Double.parseDouble(QTY);
            if (want > stock && stock >= 1) qty = String.valueOf((long) stock);
        } catch (Exception ignore) { }
        boolean qtyOk = wo.enterWriteOffQuantity(qty);
        step("Enter Write-Off Quantity", "Enter the Write-Off Quantity (" + qty + ")", "The quantity is accepted and the total updates",
                qtyOk ? "Write-Off Quantity " + wo.lastQty + " entered of " + wo.lastAvailableStock + " in stock — " + wo.lastTotals : "Problem — " + wo.problems, qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED — write-off quantity: " + wo.problems); return; }

        String reason = wo.selectReason(REASON);
        boolean reasonOk = !reason.isEmpty() && wo.ok();
        step("Select reason of write-off", "Select the Reason Of Write Off (" + REASON + ")", "The reason is selected", reasonOk ? "Selected \"" + reason + "\"" : "Not selected — " + wo.problems, reasonOk ? "PASS" : "FAIL");
        if (!reasonOk) { addSummary("Result", "FAILED — reason: " + wo.problems); return; }

        boolean justOk = wo.enterJustification(JUSTIFICATION);
        step("Enter justification", "Enter the Justification (" + JUSTIFICATION + ")", "The justification is accepted", justOk ? "Justification \"" + wo.lastJustification + "\" entered" : "Problem — " + wo.problems,
                justOk ? "PASS" : "FAIL");
        if (!justOk) { addSummary("Result", "FAILED — justification: " + wo.problems); return; }

        String toast = wo.saveAndGetToast();
        boolean ok = wo.ok() && wo.saveSucceeded();
        step("Click Save", "Click Save" + (wo.lastDialog.isEmpty() ? "" : " and answer the confirmation \"" + wo.lastDialog + "\""), "The write-off is saved",
                wo.lastDialog.isEmpty() ? "Save went straight through (no confirmation)" : "Confirmation: \"" + wo.lastDialog + "\" — answered", wo.problems.isEmpty() ? "PASS" : "FAIL");
        step("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (wo.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + wo.lastSaveMessage + "\"")
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + wo.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + wo.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Store", wo.lastStore);
        addSummary("Item", CODE + " — " + ITEM);
        addSummary("Batch", wo.lastBatchCode + " (" + wo.lastBatch + ")");
        addSummary("Write-Off Quantity / reason", qty + " of " + wo.lastAvailableStock + " in stock / " + wo.lastReason);
        addSummary("Justification", wo.lastJustification);
        addSummary("Write-off grid row", wo.lastGridRow);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
