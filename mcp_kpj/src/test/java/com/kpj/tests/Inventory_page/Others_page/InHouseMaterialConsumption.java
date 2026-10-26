package com.kpj.tests.Inventory_page.Others_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named InHouseMaterialConsumption — referenced by its fully-qualified name.

/**
 * Inventory &gt; Others &gt; <b>In-house material consumption</b>.
 *
 * <ol>
 *   <li>Click <b>New</b>; select the <b>Store</b> (Pharmacy Main Store); enter the <b>MRN</b> (1100339490) and click <b>Search</b> — the patient loads.</li>
 *   <li>Click <b>Add Item</b>; in the <b>Item Search</b> dialog enter the item <b>Code</b> (PH10000000) and the item <b>Name</b> (Panadol Optizorb Caplet 500mg) — a smart select: wait for the
 *       suggestions and click the option; if none appears click <b>Search</b>.</li>
 *   <li>Click the item row to select it; click <b>Yes</b> (the screen's alert popup, if one appears; its only button is OK).</li>
 *   <li>In <b>Items Batch List</b> tick the checkbox of one batch; in <b>Selected Item</b> tick the row's checkbox; click <b>OK</b>.</li>
 *   <li>Enter the <b>Used Quantity</b>, click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>The store, MRN, code and name are {@code -Dmaterialconsumption.store=} / {@code .mrn=} / {@code .code=} / {@code .item=}; the used quantity is {@code -Dmaterialconsumption.qty=}
 * (default 1; the batch with stock is taken, so it must not exceed its available stock).</p>
 *
 * <p>This test SAVES A REAL CONSUMPTION RECORD for the patient on every run: the stock of the ticked batch goes down.</p>
 */
public class InHouseMaterialConsumption extends DevHisBase {

    static final String STORE = System.getProperty("materialconsumption.store", "Pharmacy Main Store");
    static final String MRN = System.getProperty("materialconsumption.mrn", "1100339490");
    static final String CODE = System.getProperty("materialconsumption.code", "PH10000000");
    static final String ITEM = System.getProperty("materialconsumption.item", "Panadol Optizorb Caplet 500mg");
    static final String QTY = System.getProperty("materialconsumption.qty", "1");

    public InHouseMaterialConsumption() { super("Inventory_Others_InHouseMaterialConsumption"); }

    public static void main(String[] args) {
        InHouseMaterialConsumption t = new InHouseMaterialConsumption();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Others - In-house material consumption", "Inventory > Others > In-house material consumption",
                "New; store " + STORE + "; MRN " + MRN + " + Search; Add Item; item code " + CODE + "; item name (" + ITEM + ") — wait for the suggestions, click the option (Search if none); click the row; "
                        + "Yes in the alert if one appears; tick a batch in Items Batch List; tick the Selected Item row; OK; Used Quantity; Save; verify the success toast. SAVES A REAL CONSUMPTION.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Others_page.InHouseMaterialConsumption mc = new com.kpj.pages.Inventory_page.Others_page.InHouseMaterialConsumption(page);

        boolean on = mc.navigateViaMenu();
        step("Open In-house material consumption", "Click Inventory -> Others -> In-house material consumption", "The In-house material consumption list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — In-house material consumption not reached"); return; }

        boolean opened = mc.clickNew();
        step("Click New", "Click New", "The In-house material consumption form opens", opened ? "Form opened (" + page.url() + ")" : "Problem — " + mc.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — New: " + mc.problems); return; }

        String store = mc.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && mc.ok();
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected", storeOk ? "Selected \"" + store + "\"" : "Not selected — " + mc.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + mc.problems); return; }

        boolean mrnOk = mc.enterMrn(MRN);
        step("Enter MRN", "Enter the MRN (" + MRN + ")", "The MRN is accepted", mrnOk ? "MRN " + mc.lastMrn + " entered" : "Problem — " + mc.problems, mrnOk ? "PASS" : "FAIL");
        if (!mrnOk) { addSummary("Result", "FAILED — MRN: " + mc.problems); return; }

        String patient = mc.clickSearchPatient();
        boolean found = !patient.isEmpty() && mc.ok();
        step("Click Search", "Click the MRN Search button", "The patient's details load", found ? "Loaded: " + patient : "No patient loaded — " + mc.problems, found ? "PASS" : "FAIL");
        if (!found) { addSummary("Result", "FAILED — Search: " + mc.problems); return; }

        boolean dlg = mc.clickAddItem();
        step("Click Add Item", "Click Add Item", "The Item Search dialog opens", dlg ? "Item Search dialog opened" : "Problem — " + mc.problems, dlg ? "PASS" : "FAIL");
        if (!dlg) { addSummary("Result", "FAILED — Add Item: " + mc.problems); return; }

        boolean codeOk = mc.enterCode(CODE);
        step("Enter code", "Enter the Item Code (" + CODE + ")", "The code is accepted", codeOk ? "Item Code \"" + mc.lastCode + "\" entered" : "Problem — " + mc.problems, codeOk ? "PASS" : "FAIL");
        if (!codeOk) { addSummary("Result", "FAILED — item code: " + mc.problems); return; }

        boolean typed = mc.typeItemName(ITEM);
        step("Enter name", "Type \"" + ITEM + "\" in the Item Name box (a smart select)", "The name is typed", typed ? "\"" + ITEM + "\" typed" : "Problem — " + mc.problems, typed ? "PASS" : "FAIL");
        if (!typed) { addSummary("Result", "FAILED — item name: " + mc.problems); return; }

        // wait for the option and click it; if none appears click Search
        List<String> sugg = mc.awaitSuggestions(ITEM, 15000);
        boolean listed;
        String how;
        if (!sugg.isEmpty()) {
            String picked = mc.pickSuggestion(ITEM);
            listed = !picked.isEmpty() && mc.ok() && !mc.itemRows().isEmpty();
            how = listed ? "Waited for the suggestions (" + sugg.size() + "), clicked \"" + picked.replaceAll("\\s+", " ") + "\" — the item grid shows " + mc.itemRows().size() + " row(s)"
                         : "The suggestion was not usable — " + mc.problems;
        } else {
            boolean searched = mc.clickSearch();
            listed = searched && !mc.itemRows().isEmpty();
            how = listed ? "No suggestion appeared (" + mc.searchDiagnosis + "), so Search was clicked — the item grid shows " + mc.itemRows().size() + " row(s)"
                         : "No suggestion appeared (" + mc.searchDiagnosis + ") and Search found nothing — " + mc.problems;
        }
        step("Wait for the option and select it (Search if none appears)", "Wait for the suggestions for \"" + ITEM + "\" and click the option; click Search if none appears",
                "The item is listed in the item grid", how, listed ? "PASS" : "FAIL");
        if (!listed) { addSummary("Result", "FAILED — item not listed: " + how); return; }

        String row = mc.clickItemRow();
        boolean rowOk = !row.isEmpty() && mc.ok();
        step("Click on the row to select", "Click the item row in the grid", "The row is selected and its batches load",
                rowOk ? "Clicked \"" + row.replaceAll("\\s+", " ") + "\"" + (mc.lastAlert.isEmpty() ? " — no alert popup appeared" : " — the screen raised an alert: \"" + mc.lastAlert + "\"") : "Problem — " + mc.problems,
                rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — row: " + mc.problems); return; }

        String btn = mc.answerAlert();
        boolean alertOk = mc.ok() && (mc.lastAlert.isEmpty() || !btn.isEmpty());
        step("Click Yes — confirmation/alert popup (if any)", "Click the popup's affirmative button (Yes; this alert only has OK) if one appeared",
                "The popup is dismissed and the Items Batch List fills in",
                !alertOk ? "Problem — " + mc.problems
                         : mc.lastAlert.isEmpty() ? "No popup appeared for this item, so there was nothing to click; Items Batch List has " + mc.batchRows().size() + " batch(es)"
                                                  : "Clicked \"" + btn + "\" on \"" + mc.lastAlert + "\"; Items Batch List has " + mc.batchRows().size() + " batch(es)",
                alertOk ? "PASS" : "FAIL");
        if (!alertOk) { addSummary("Result", "FAILED — alert popup: " + mc.problems); return; }

        String batch = mc.tickBatchRow();
        boolean batchOk = !batch.isEmpty() && mc.ok();
        step("In Items Batch List, tick the checkbox of a row", "Tick the checkbox of one batch row", "The batch is ticked and appears in Selected Item",
                batchOk ? "Ticked batch \"" + mc.lastBatchCode + "\" (" + batch + "); Selected Item shows " + mc.selectedRows().size() + " row(s)" : "Problem — " + mc.problems, batchOk ? "PASS" : "FAIL");
        if (!batchOk) { addSummary("Result", "FAILED — batch: " + mc.problems); return; }

        String sel = mc.tickSelectedItemRow();
        boolean selOk = !sel.isEmpty() && mc.ok();
        step("In Selected Item, tick the checkbox of the row", "Tick the checkbox of the row in Selected Item", "The row is ticked", selOk ? "Ticked \"" + sel + "\"" : "Problem — " + mc.problems, selOk ? "PASS" : "FAIL");
        if (!selOk) { addSummary("Result", "FAILED — selected item: " + mc.problems); return; }

        boolean okOk = mc.clickOk();
        mc.gridCells();
        step("Click OK", "Click OK in the Item Search dialog", "The dialog closes and the item is added to the consumption grid",
                okOk ? "Dialog closed; the grid shows: " + mc.lastGridRow : "Problem — " + mc.problems, okOk ? "PASS" : "FAIL");
        if (!okOk) { addSummary("Result", "FAILED — OK: " + mc.problems); return; }

        // the used quantity cannot exceed the batch's available stock
        String qty = QTY;
        try {
            double stock = Double.parseDouble(mc.lastAvailableStock.trim()), want = Double.parseDouble(QTY);
            if (want > stock && stock >= 1) qty = String.valueOf((long) stock);
        } catch (Exception ignore) { }
        boolean qtyOk = mc.enterUsedQuantity(qty);
        step("Enter used quantity", "Enter the Used Quantity (" + qty + ")", "The quantity is accepted and the totals update",
                qtyOk ? "Used Quantity " + mc.lastQty + " entered — " + mc.lastTotals + "; the grid shows: " + mc.lastGridRow : "Problem — " + mc.problems, qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED — used quantity: " + mc.problems); return; }

        String toast = mc.saveAndGetToast();
        boolean ok = mc.ok() && mc.saveSucceeded();
        step("Click Save", "Click Save" + (mc.lastDialog.isEmpty() ? "" : " and answer the confirmation \"" + mc.lastDialog + "\""), "The consumption is saved",
                mc.lastDialog.isEmpty() ? "Save went straight through (no confirmation)" : "Confirmation: \"" + mc.lastDialog + "\" — answered", mc.problems.isEmpty() ? "PASS" : "FAIL");
        step("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (mc.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + mc.lastSaveMessage + "\"")
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + mc.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + mc.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Store / patient", mc.lastStore + " / " + mc.lastPatient);
        addSummary("Item", CODE + " — " + ITEM);
        addSummary("Batch", mc.lastBatchCode + " (" + mc.lastBatch + ")");
        addSummary("Used quantity", qty + " of " + mc.lastAvailableStock + " in stock, selling price " + mc.lastSellingPrice);
        addSummary("Consumption grid row", mc.lastGridRow);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
