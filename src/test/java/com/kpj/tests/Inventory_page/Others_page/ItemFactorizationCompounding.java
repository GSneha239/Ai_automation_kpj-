package com.kpj.tests.Inventory_page.Others_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named ItemFactorizationCompounding — referenced by its fully-qualified name.

/**
 * Inventory &gt; Others &gt; <b>Item Factorization Compounding</b>.
 *
 * <ol>
 *   <li>Click <b>New</b>; select the <b>Store</b> (Pharmacy Main Store).</li>
 *   <li><b>Item 1 (Added):</b> enter the item <b>Code</b> (PH10000006) and select the option (smart select) that fills the Item Name; click the item name and press Enter — the
 *       <b>Batch List</b> dialog opens; click a batch row; enter the <b>Quantity</b> (1); select <b>Added</b>; click the <b>+</b> symbol.</li>
 *   <li><b>Item 2 (Deducted):</b> the same with Code PH90000098 and <b>Deducted</b>.</li>
 *   <li>Click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>The batch clicked is the first REAL batch that has stock (the dialog's first row, "New Batch", has none), so the Deducted item always has stock to take from. The store, codes,
 * quantities and modes are {@code -Dcompounding.store=} / {@code .code1=} / {@code .qty1=} / {@code .mode1=} / {@code .code2=} / {@code .qty2=} / {@code .mode2=}.</p>
 *
 * <p>This test SAVES A REAL TRANSACTION on every run: the Added item's stock goes up and the Deducted item's goes down (the list has an Approve action; this test never approves).</p>
 */
public class ItemFactorizationCompounding extends DevHisBase {

    static final String STORE = System.getProperty("compounding.store", "Pharmacy Main Store");
    static final String CODE1 = System.getProperty("compounding.code1", "PH10000006");
    static final String QTY1 = System.getProperty("compounding.qty1", "1");
    static final String MODE1 = System.getProperty("compounding.mode1", "Added");
    static final String CODE2 = System.getProperty("compounding.code2", "PH90000098");
    static final String QTY2 = System.getProperty("compounding.qty2", "1");
    static final String MODE2 = System.getProperty("compounding.mode2", "Deducted");

    public ItemFactorizationCompounding() { super("Inventory_Others_ItemFactorizationCompounding"); }

    public static void main(String[] args) {
        ItemFactorizationCompounding t = new ItemFactorizationCompounding();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** Runs one item through the entry strip and records its steps; returns false (after recording the failure) when a step failed. */
    private boolean addItem(com.kpj.pages.Inventory_page.Others_page.ItemFactorizationCompounding ic, int n, String code, String qty, String mode) {
        String tag = "Item " + n + " (" + mode + ") — ";

        boolean codeOk = ic.enterCode(code);
        step(tag + "Enter item code", "Enter the item code (" + code + ")", "The code is accepted and suggestions appear", codeOk ? "Code \"" + ic.lastCode + "\" entered" : "Problem — " + ic.problems, codeOk ? "PASS" : "FAIL");
        if (!codeOk) { addSummary("Result", "FAILED — item " + n + " code: " + ic.problems); return false; }

        List<String> sugg = ic.awaitSuggestions(code, 15000);
        String picked = sugg.isEmpty() ? "" : ic.pickSuggestion(code);
        boolean pickOk = !picked.isEmpty() && ic.ok();
        step(tag + "Select the option (smart select) for Item Name", "Wait for the suggestions and click the one for " + code, "The item name, prices, conversion factor and UOM fill in",
                pickOk ? "Clicked \"" + picked.replaceAll("\\s+", " ") + "\" — Item Name \"" + ic.lastName + "\"" : sugg.isEmpty() ? "No suggestion appeared — " + ic.searchDiagnosis : "Problem — " + ic.problems,
                pickOk ? "PASS" : "FAIL");
        if (!pickOk) { addSummary("Result", "FAILED — item " + n + " option: " + (sugg.isEmpty() ? ic.searchDiagnosis : ic.problems)); return false; }

        boolean dlg = ic.openBatchList();
        step(tag + "Click the item name and press Enter", "Click the Item Name box and press Enter", "The Batch List dialog opens",
                dlg ? "Batch List opened with " + ic.batchRows().size() + " row(s): " + ic.batchRows() : "Problem — " + ic.problems, dlg ? "PASS" : "FAIL");
        if (!dlg) { addSummary("Result", "FAILED — item " + n + " batch list: " + ic.problems); return false; }

        String batch = ic.pickBatch();
        boolean batchOk = !batch.isEmpty() && ic.ok();
        step(tag + "Click the row for the batch code (Batch List)", "Click a batch row in the Batch List popup", "The dialog closes and the batch, expiry and available stock fill in",
                batchOk ? "Clicked \"" + batch + "\" — Batch \"" + ic.lastBatch + "\", expiry " + ic.lastExpiry + ", available stock " + ic.lastAvailable : "Problem — " + ic.problems, batchOk ? "PASS" : "FAIL");
        if (!batchOk) { addSummary("Result", "FAILED — item " + n + " batch: " + ic.problems); return false; }

        boolean qtyOk = ic.enterQuantity(qty);
        step(tag + "Enter quantity", "Enter the Quantity (" + qty + ")", "The quantity is accepted", qtyOk ? "Quantity " + ic.lastQty + " entered" : "Problem — " + ic.problems, qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED — item " + n + " quantity: " + ic.problems); return false; }

        String m = ic.selectMode(mode);
        boolean modeOk = !m.isEmpty() && ic.ok();
        step(tag + "Select " + mode, "Select " + mode + " in the Added/Deducted list", "The option is selected", modeOk ? "Selected \"" + m + "\"" : "Problem — " + ic.problems, modeOk ? "PASS" : "FAIL");
        if (!modeOk) { addSummary("Result", "FAILED — item " + n + " " + mode + ": " + ic.problems); return false; }

        String row = ic.clickAdd();
        boolean addOk = !row.isEmpty() && ic.ok();
        boolean rowHas = addOk && row.contains(code) && row.contains(ic.lastBatch) && row.toLowerCase().contains(mode.toLowerCase());
        step(tag + "Click the add (+) symbol", "Click the + symbol", "The item moves into the List of Items table with its batch, quantity and " + mode,
                addOk ? (rowHas ? "Row added: " + row : "A row was added but it does not show the code, batch and " + mode + ": " + row) : "Problem — " + ic.problems, addOk && rowHas ? "PASS" : "FAIL");
        if (!addOk || !rowHas) { addSummary("Result", "FAILED — item " + n + " add: " + (addOk ? row : ic.problems)); return false; }
        return true;
    }

    @Override
    protected void body() {
        meta("Inventory - Others - Item Factorization Compounding", "Inventory > Others > Item Factorization Compounding",
                "New; store " + STORE + "; item " + CODE1 + " (code, option, name + Enter, batch row, quantity " + QTY1 + ", " + MODE1 + ", +); item " + CODE2 + " (same, quantity " + QTY2 + ", " + MODE2
                        + ", +); Save; verify the success toast. SAVES A REAL TRANSACTION.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Others_page.ItemFactorizationCompounding ic = new com.kpj.pages.Inventory_page.Others_page.ItemFactorizationCompounding(page);

        boolean on = ic.navigateViaMenu();
        step("Open Item Factorization Compounding", "Click Inventory -> Others -> Item Factorization Compounding", "The Item Factorization Compounding list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Item Factorization Compounding not reached"); return; }

        boolean opened = ic.clickNew();
        step("Click New", "Click New", "The Item Factorization Compounding form opens", opened ? "Form opened (" + page.url() + ")" : "Problem — " + ic.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — New: " + ic.problems); return; }

        String store = ic.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && ic.ok();
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected", storeOk ? "Selected \"" + store + "\"" : "Not selected — " + ic.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + ic.problems); return; }

        if (!addItem(ic, 1, CODE1, QTY1, MODE1)) return;
        String row1 = ic.lastRow, batch1 = ic.lastBatch;
        if (!addItem(ic, 2, CODE2, QTY2, MODE2)) return;
        String row2 = ic.lastRow, batch2 = ic.lastBatch;

        String toast = ic.saveAndGetToast();
        boolean ok = ic.ok() && ic.saveSucceeded();
        step("Click Save", "Click Save" + (ic.lastDialog.isEmpty() ? "" : " and answer the confirmation \"" + ic.lastDialog + "\""), "The transaction is saved",
                ic.lastDialog.isEmpty() ? "Save went straight through (no confirmation)" : "Confirmation: \"" + ic.lastDialog + "\" — answered", ic.problems.isEmpty() ? "PASS" : "FAIL");
        step("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (ic.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + ic.lastSaveMessage + "\"")
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + ic.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + ic.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Store", ic.lastStore);
        addSummary("Item 1 (" + MODE1 + ")", CODE1 + " batch " + batch1 + " x " + QTY1 + " — " + row1);
        addSummary("Item 2 (" + MODE2 + ")", CODE2 + " batch " + batch2 + " x " + QTY2 + " — " + row2);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
