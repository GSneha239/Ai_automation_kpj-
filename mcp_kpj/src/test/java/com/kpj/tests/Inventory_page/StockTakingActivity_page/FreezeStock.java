package com.kpj.tests.Inventory_page.StockTakingActivity_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named FreezeStock — referenced by its fully-qualified name.

/**
 * Inventory &gt; Stock Taking Activity &gt; <b>Freeze Stock</b>.
 *
 * <ol>
 *   <li>Click <b>New</b>; select the <b>Store</b> (Pharmacy Main Store).</li>
 *   <li>Click <b>Get Items</b>; verify the item rows are populated in the table.</li>
 *   <li>Click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p><b>This test FREEZES THE STORE'S STOCK for real, and nothing in this test un-freezes it.</b> The freeze stays in force until it is released on the UnFreeze screen
 * ({@code #/UnFreezeList}); while it is, other transactions on that store may be blocked. Pin the store with {@code -Dfreezestock.store=}.</p>
 */
public class FreezeStock extends DevHisBase {

    static final String STORE = System.getProperty("freezestock.store", "Pharmacy Main Store");

    public FreezeStock() { super("Inventory_StockTaking_FreezeStock"); }

    public static void main(String[] args) {
        FreezeStock t = new FreezeStock();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Stock Taking Activity - Freeze Stock", "Inventory > Stock Taking Activity > Freeze Stock",
                "New; store " + STORE + "; Get Items; verify the item rows are populated in the table; Save; verify the success toast. FREEZES THE STORE'S STOCK until it is released on the UnFreeze screen.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.StockTakingActivity_page.FreezeStock fs = new com.kpj.pages.Inventory_page.StockTakingActivity_page.FreezeStock(page);

        boolean on = fs.navigateViaMenu();
        step("Open Freeze Stock", "Click Inventory -> Stock Taking Activity -> Freeze Stock", "The Freeze Stock list is shown", on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Freeze Stock not reached"); return; }

        boolean opened = fs.clickNew();
        step("Click New", "Click New", "The Freeze Stock form opens", opened ? "Form opened (" + page.url() + "), Date " + fs.lastDate : "Problem — " + fs.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — New: " + fs.problems); return; }

        String store = fs.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && fs.ok();
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected", storeOk ? "Selected \"" + store + "\"" : "Not selected — " + fs.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + fs.problems); return; }

        int rows = fs.clickGetItems();
        boolean itemsOk = rows > 0 && fs.ok();
        step("Click Get Items", "Click Get Items", "The store's items are fetched", itemsOk ? "Get Items answered in " + fs.lastGetItemsMs / 1000.0 + " s with " + rows + " row(s)" : "Problem — " + fs.problems, itemsOk ? "PASS" : "FAIL");
        if (!itemsOk) { addSummary("Result", "FAILED — Get Items: " + fs.problems); return; }

        // verify the item rows are populated (all rows of the table, not a sample)
        com.google.gson.JsonObject st = fs.stats(3);
        int total = st == null ? 0 : st.get("total").getAsInt();
        int noName = st == null ? 0 : st.get("noName").getAsInt(), noBatch = st == null ? 0 : st.get("noBatch").getAsInt(), noExpiry = st == null ? 0 : st.get("noExpiry").getAsInt();
        int badQty = st == null ? 0 : st.get("badQty").getAsInt(), withStock = st == null ? 0 : st.get("withStock").getAsInt();
        // A batch code is not required of every row (items that are not batch-tracked have none), so it is reported, not asserted.
        boolean populated = total > 0 && noName == 0 && badQty == 0;
        String first = st == null ? "" : st.get("first").toString();
        String noBatchNote = noBatch == 0 ? "" : " " + noBatch + " row(s) have no batch code (items without a batch), e.g. " + st.get("noBatchRows") + ".";
        step("Verify the item rows are populated in the table", "Read the item table after Get Items", "Rows are listed and every row has an item name and a numeric Available Quantity",
                populated ? "PASSES because the table (columns " + fs.headers() + ") lists " + total + " row(s); every row has an item name and a numeric Available Quantity; " + withStock
                        + " row(s) have stock; " + (total - noExpiry) + " carry an expiry date; first rows: " + first + "." + noBatchNote
                          : "FAILS because " + (total == 0 ? "the table is empty" : noName + " row(s) lack an item name and " + badQty + " have a non-numeric Available Quantity (of " + total + ")") + "; first rows: " + first,
                populated ? "PASS" : "FAIL");
        if (!populated) { addSummary("Result", "FAILED — the item rows are not as expected (" + total + " row(s))"); return; }

        String toast = fs.saveAndGetToast();
        boolean ok = fs.ok() && fs.saveSucceeded();
        step("Click Save", "Click Save" + (fs.lastDialog.isEmpty() ? "" : " and answer the confirmation \"" + fs.lastDialog + "\""), "The stock is frozen",
                fs.lastDialog.isEmpty() ? "Save went straight through (no confirmation)" : "Confirmation: \"" + fs.lastDialog + "\" — answered", fs.problems.isEmpty() ? "PASS" : "FAIL");
        step("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (fs.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + fs.lastSaveMessage + "\"")
                        + " — the stock of " + fs.lastStore + " is now FROZEN until it is released on the UnFreeze screen"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + fs.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + fs.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Store / date", fs.lastStore + " / " + fs.lastDate);
        addSummary("Rows in the table", total + " (" + withStock + " with stock)");
        addSummary("First rows", first);
        addSummary("Result", ok ? toast + " (stock frozen until UnFreeze)" : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
