package com.kpj.tests.Inventory_page.StockTakingActivity_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named UnFreeze — referenced by its fully-qualified name.

/**
 * Inventory &gt; Stock Taking Activity &gt; <b>UnFreeze</b>.
 *
 * <ol>
 *   <li>Click <b>New</b>; select the <b>Store</b> (Pharmacy Main Store) — the form comes with another store ("Administration") pre-selected, so the choice is always made explicitly.</li>
 *   <li>Click <b>Get Items</b>; verify the item rows are populated in the table.</li>
 *   <li>Click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>This test RELEASES THE STORE'S FROZEN STOCK for real: it is the counterpart of the Freeze Stock test and undoes it. It is safe to run when the store is not frozen only as far as the
 * server allows (the screen has no frozen/not-frozen indicator, so the test cannot tell; the toast and the server's answer are reported as they come). Pin the store with
 * {@code -Dunfreeze.store=}.</p>
 */
public class UnFreeze extends DevHisBase {

    static final String STORE = System.getProperty("unfreeze.store", "Pharmacy Main Store");

    public UnFreeze() { super("Inventory_StockTaking_UnFreeze"); }

    public static void main(String[] args) {
        UnFreeze t = new UnFreeze();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Stock Taking Activity - UnFreeze", "Inventory > Stock Taking Activity > UnFreeze",
                "New; store " + STORE + "; Get Items; verify the item rows are populated in the table; Save; verify the success toast. RELEASES THE STORE'S FROZEN STOCK (undoes Freeze Stock).");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.StockTakingActivity_page.UnFreeze uf = new com.kpj.pages.Inventory_page.StockTakingActivity_page.UnFreeze(page);

        boolean on = uf.navigateViaMenu();
        step("Open UnFreeze", "Click Inventory -> Stock Taking Activity -> UnFreeze", "The UnFreeze list is shown", on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — UnFreeze not reached"); return; }

        boolean opened = uf.clickNew();
        step("Click New", "Click New", "The UnFreeze form opens", opened ? "Form opened (" + page.url() + "), Date " + uf.lastDate : "Problem — " + uf.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — New: " + uf.problems); return; }

        String store = uf.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && uf.ok();
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected", storeOk ? "Selected \"" + store + "\"" : "Not selected — " + uf.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + uf.problems); return; }

        int rows = uf.clickGetItems();
        boolean itemsOk = rows > 0 && uf.ok();
        step("Click Get Items", "Click Get Items", "The store's items are fetched", itemsOk ? "Get Items answered in " + uf.lastGetItemsMs / 1000.0 + " s with " + rows + " row(s)" : "Problem — " + uf.problems, itemsOk ? "PASS" : "FAIL");
        if (!itemsOk) { addSummary("Result", "FAILED — Get Items: " + uf.problems); return; }

        // verify the item rows are populated (all rows of the table, not a sample)
        com.google.gson.JsonObject st = uf.stats(3);
        int total = st == null ? 0 : st.get("total").getAsInt();
        int noName = st == null ? 0 : st.get("noName").getAsInt(), noBatch = st == null ? 0 : st.get("noBatch").getAsInt(), noExpiry = st == null ? 0 : st.get("noExpiry").getAsInt();
        int badQty = st == null ? 0 : st.get("badQty").getAsInt(), withStock = st == null ? 0 : st.get("withStock").getAsInt();
        // A batch code is not required of every row (items that are not batch-tracked have none), so it is reported, not asserted.
        boolean populated = total > 0 && noName == 0 && badQty == 0;
        String first = st == null ? "" : st.get("first").toString();
        String noBatchNote = noBatch == 0 ? "" : " " + noBatch + " row(s) have no batch code (items without a batch), e.g. " + st.get("noBatchRows") + ".";
        step("Verify the item rows are populated in the table", "Read the item table after Get Items", "Rows are listed and every row has an item name and a numeric Available Quantity",
                populated ? "PASSES because the table (columns " + uf.headers() + ") lists " + total + " row(s); every row has an item name and a numeric Available Quantity; " + withStock
                        + " row(s) have stock; " + (total - noExpiry) + " carry an expiry date; first rows: " + first + "." + noBatchNote
                          : "FAILS because " + (total == 0 ? "the table is empty" : noName + " row(s) lack an item name and " + badQty + " have a non-numeric Available Quantity (of " + total + ")") + "; first rows: " + first,
                populated ? "PASS" : "FAIL");
        if (!populated) { addSummary("Result", "FAILED — the item rows are not as expected (" + total + " row(s))"); return; }

        String toast = uf.saveAndGetToast();
        boolean ok = uf.ok() && uf.saveSucceeded();
        step("Click Save", "Click Save" + (uf.lastDialog.isEmpty() ? "" : " and answer the confirmation \"" + uf.lastDialog + "\""), "The stock is released",
                uf.lastDialog.isEmpty() ? "Save went straight through (no confirmation)" : "Confirmation: \"" + uf.lastDialog + "\" — answered", uf.problems.isEmpty() ? "PASS" : "FAIL");
        step("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (uf.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + uf.lastSaveMessage + "\"")
                        + " — the stock of " + uf.lastStore + " is released (UNFROZEN)"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + uf.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + uf.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Store / date", uf.lastStore + " / " + uf.lastDate);
        addSummary("Rows in the table", total + " (" + withStock + " with stock)");
        addSummary("First rows", first);
        addSummary("Result", ok ? toast + " (stock released)" : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
