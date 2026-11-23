package com.kpj.tests.Inventory_page.StockTakingActivity_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named StockTake — referenced by its fully-qualified name.

/**
 * Inventory &gt; Stock Taking Activity &gt; <b>Stock Take</b>.
 *
 * <ol>
 *   <li>Click <b>New</b>; select the <b>Store</b> (Pharmacy Main Store).</li>
 *   <li>Select the <b>Stock Take Category</b>: "Pharmacy,Oral Solids/None", else "Pharmacy,Oral Solids/Oncology Preparations" (the requirement allows either; the second is only used if the
 *       first lists no item with stock).</li>
 *   <li>Click <b>Get Items</b>; enter the <b>Physical Quantity</b> (1) for one row that has an Available Quantity.</li>
 *   <li>Click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>The row is chosen so the stock take does as little harm as possible: a row whose Available Quantity is exactly the physical quantity (no variance) if there is one, otherwise the row with the
 * smallest positive Available Quantity. Pin the store with {@code -Dstocktake.store=}, the category with {@code -Dstocktake.category=} (then no fallback is tried) and the quantity with
 * {@code -Dstocktake.qty=}.</p>
 *
 * <p>This test SAVES A REAL STOCK TAKE (a Physical Stock record, awaiting the Stock Take Approval screen; this test never approves it).</p>
 */
public class StockTake extends DevHisBase {

    static final String STORE = System.getProperty("stocktake.store", "Pharmacy Main Store");
    static final String CATEGORY = System.getProperty("stocktake.category", "");
    static final String QTY = System.getProperty("stocktake.qty", "1");

    public StockTake() { super("Inventory_StockTaking_StockTake"); }

    public static void main(String[] args) {
        StockTake t = new StockTake();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Stock Taking Activity - Stock Take", "Inventory > Stock Taking Activity > Stock Take",
                "New; store " + STORE + "; Stock Take Category (Pharmacy,Oral Solids/None or Pharmacy,Oral Solids/Oncology Preparations); Get Items; Physical Quantity " + QTY
                        + " for one row with an Available Quantity; Save; verify the success toast. SAVES A REAL STOCK TAKE.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.StockTakingActivity_page.StockTake st = new com.kpj.pages.Inventory_page.StockTakingActivity_page.StockTake(page);

        boolean on = st.navigateViaMenu();
        step("Open Stock Take", "Click Inventory -> Stock Taking Activity -> Stock Take", "The Stock Take list is shown", on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Stock Take not reached"); return; }

        boolean opened = st.clickNew();
        step("Click New", "Click New", "The Stock Take form opens", opened ? "Form opened (" + page.url() + ")" : "Problem — " + st.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — New: " + st.problems); return; }

        String store = st.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && st.ok();
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected", storeOk ? "Selected \"" + store + "\"" : "Not selected — " + st.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + st.problems); return; }

        // category + Get Items: the two allowed categories in order (or the pinned one), taking the first that lists an item with stock
        String[] categories = CATEGORY.isEmpty() ? new String[]{"Pharmacy,Oral Solids/None", "Pharmacy,Oral Solids/Oncology Preparations"} : new String[]{CATEGORY};
        String tried = "";
        String rowText = "";
        for (int i = 0; i < categories.length && rowText.isEmpty(); i++) {
            String cat = st.selectCategory(categories[i]);
            boolean catOk = !cat.isEmpty() && st.ok();
            boolean last = i == categories.length - 1;
            if (!catOk) {
                tried += (tried.isEmpty() ? "" : "; ") + categories[i] + ": " + st.problems;
                if (last) { step("Select stock take category", "Select the Stock Take Category (" + categories[i] + ")", "The category is selected", "Not selected — " + st.problems, "FAIL"); addSummary("Result", "FAILED — category: " + st.problems); return; }
                continue;
            }
            int rows = st.clickGetItems();
            boolean itemsOk = rows > 0 && st.ok();
            String entered = itemsOk ? st.enterPhysicalQuantity(QTY) : "";
            if (itemsOk && entered.isEmpty() && !last) { tried += (tried.isEmpty() ? "" : "; ") + cat + ": " + rows + " row(s) but " + st.problems; continue; }
            if (!itemsOk && !last) { tried += (tried.isEmpty() ? "" : "; ") + cat + ": " + st.problems; continue; }
            // this category is the one reported
            step("Select stock take category", "Select the Stock Take Category (" + categories[i] + ")", "The category is selected",
                    "Selected \"" + cat + "\"" + (tried.isEmpty() ? "" : " (earlier category had nothing usable — " + tried + ")"), "PASS");
            step("Click Get Items", "Click Get Items", "The items of the store and category are listed",
                    itemsOk ? "Get Items listed " + rows + " row(s) in " + st.lastGetItemsMs / 1000.0 + " s; first rows: " + st.sampleRows(3) : "Problem — " + st.problems, itemsOk ? "PASS" : "FAIL");
            if (!itemsOk) { addSummary("Result", "FAILED — Get Items: " + st.problems); return; }
            boolean qtyOk = !entered.isEmpty() && st.ok();
            step("Enter physical quantity", "Enter the Physical Quantity (" + QTY + ") for one row that has an Available Quantity", "The quantity is accepted and the Adjustment Quantity follows",
                    qtyOk ? "Row \"" + entered + "\": Physical Quantity " + st.lastQty + ", Adjustment Quantity " + (st.lastAdjustment.isEmpty() ? "(blank)" : st.lastAdjustment) : "Problem — " + st.problems, qtyOk ? "PASS" : "FAIL");
            if (!qtyOk) { addSummary("Result", "FAILED — physical quantity: " + st.problems); return; }
            rowText = entered;
        }
        if (rowText.isEmpty()) { addSummary("Result", "FAILED — no category had a row to count: " + tried); return; }

        String toast = st.saveAndGetToast();
        boolean ok = st.ok() && st.saveSucceeded();
        step("Click Save", "Click Save" + (st.lastDialog.isEmpty() ? "" : " and answer the confirmation \"" + st.lastDialog + "\""), "The stock take is saved",
                st.lastDialog.isEmpty() ? "Save went straight through (no confirmation)" : "Confirmation: \"" + st.lastDialog + "\" — answered", st.problems.isEmpty() ? "PASS" : "FAIL");
        step("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (st.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + st.lastSaveMessage + "\"")
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + st.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + st.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Store / category", st.lastStore + " / " + st.lastCategory);
        addSummary("Row counted", rowText);
        addSummary("Physical Quantity / Adjustment Quantity", st.lastQty + " / " + (st.lastAdjustment.isEmpty() ? "(blank)" : st.lastAdjustment));
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
