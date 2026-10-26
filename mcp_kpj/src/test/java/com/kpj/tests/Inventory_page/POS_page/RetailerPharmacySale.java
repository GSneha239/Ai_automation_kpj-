package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.List;

// The page object is also named RetailerPharmacySale — referenced by its fully-qualified name.

/**
 * Inventory &gt; POS &gt; <b>Retailer Pharmacy Sale</b>.
 *
 * <ol>
 *   <li>Enter the <b>Item Code</b> (PH10000000) and choose it from the suggestions.</li>
 *   <li>Enter the <b>Item Name</b> (Panadol Optizorb Caplet 500mg) and choose it from the suggestions.</li>
 *   <li>Enter the <b>Quantity</b>; press Enter (focus moves to Free Qty); enter the <b>Free Qty</b>; press Enter (the line is added).</li>
 *   <li>Verify the row in <b>List of Items</b> and in <b>List of Total Items</b>.</li>
 * </ol>
 *
 * <p><b>Two things the steps do not say, both needed on devhis:</b> the <b>Store</b> (the item search is keyed by it and offers nothing without one —
 * Pharmacy Main Store) and the <b>Batch</b> (the item needs one: Enter on a chosen item opens a Batch List, and a batch is clicked before the Quantity box takes
 * focus — the first batch with enough stock). Nothing is saved: the sale is neither saved nor externally sold, so no stock moves.</p>
 *
 * <p><b>Known application defect (devhis v2026.10.24.288):</b> the screen's item search request carries {@code PsychotropicDrug=undefined}, the server answers
 * HTTP 400, and no suggestion is ever offered — for the code or the name. This test therefore FAILS at the first step, saying exactly that. To exercise the
 * rest of the flow while the defect is open, run with {@code -Dretailersale.simulatePick=true}: the suggestion click is then simulated (reported MANUAL).</p>
 *
 * <p>Pin the store with {@code -Dretailersale.store=}, the item with {@code -Dretailersale.code=} / {@code -Dretailersale.name=}, the quantities with
 * {@code -Dretailersale.qty=} / {@code -Dretailersale.freeqty=} and the batch with {@code -Dretailersale.batch=}.</p>
 */
public class RetailerPharmacySale extends DevHisBase {

    static final String STORE = System.getProperty("retailersale.store", "Pharmacy Main Store");
    static final String CODE = System.getProperty("retailersale.code", "PH10000000");
    static final String NAME = System.getProperty("retailersale.name", "Panadol Optizorb Caplet 500mg");
    static final String QTY = System.getProperty("retailersale.qty", "2");
    static final String FREE = System.getProperty("retailersale.freeqty", "1");
    static final String BATCH = System.getProperty("retailersale.batch", "");
    static final boolean SIMULATE = Boolean.getBoolean("retailersale.simulatePick");

    public RetailerPharmacySale() { super("Inventory_POS_RetailerPharmacySale"); }

    public static void main(String[] args) {
        RetailerPharmacySale t = new RetailerPharmacySale();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static double num(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    @Override
    protected void body() {
        meta("Inventory - POS - Retailer Pharmacy Sale", "Inventory > POS > Retailer Pharmacy Sale",
                "Enter the item code (" + CODE + ") and the item name (" + NAME + "), the quantity, Enter, the free quantity, Enter; verify the row in List of Items and "
                        + "List of Total Items. Nothing is saved." + (SIMULATE ? " The suggestion click is SIMULATED (-Dretailersale.simulatePick=true)." : ""));

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.POS_page.RetailerPharmacySale rs = new com.kpj.pages.Inventory_page.POS_page.RetailerPharmacySale(page);

        // 1) Inventory > POS > Retailer Pharmacy Sale
        boolean on = rs.navigateViaMenu();
        step("Open Retailer Pharmacy Sale", "Click Inventory -> POS -> Retailer Pharmacy Sale", "The Retailer Pharmacy Sale screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Retailer Pharmacy Sale not reached"); return; }

        // 2) Store (not in the written steps: the item search is keyed by it)
        String store = rs.selectStore(STORE);
        boolean storeOk = !store.isEmpty() && rs.ok();
        step("Select store", "Select the Store (" + STORE + ") — not in the written steps: the item search is keyed by it and offers nothing without one", "The store is selected",
                storeOk ? "Selected \"" + store + "\"" : "Not selected — " + rs.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED — store: " + rs.problems); return; }

        // 3) Item code
        boolean pickedByCode = pickItem(rs, true, CODE, "Enter item code", "Type \"" + CODE + "\" in the Item Code box and choose it from the suggestions");
        if (!pickedByCode) return;

        // 4) Item name
        boolean pickedByName = pickItem(rs, false, NAME, "Enter item name", "Type \"" + NAME + "\" in the Item Name box and choose it from the suggestions");
        if (!pickedByName) return;

        // 5) Batch (not in the written steps: the item needs one before the Quantity box takes focus)
        double need = num(QTY) + num(FREE);
        String batch = rs.chooseBatch(need, BATCH);
        boolean batchOk = !batch.isEmpty() && rs.ok();
        step("Select batch", "Press Enter on the chosen item to open the Batch List and click a batch with at least " + (long) Math.ceil(need) + " available"
                        + (BATCH.isEmpty() ? "" : " (batch " + BATCH + ")") + " — not in the written steps: the item needs a batch before the quantity can be entered",
                "A batch is selected; Batch, Expiry, Selling Price and Purchase Rate fill in",
                batchOk ? "Batch \"" + batch + "\" selected — selling price " + rs.lastSellingPrice + ", available stock " + rs.lastAvailable + (rs.batchNote.isEmpty() ? "" : " (" + rs.batchNote + ")")
                        : "No batch selected — " + rs.problems, batchOk ? "PASS" : "FAIL");
        if (!batchOk) { addSummary("Result", "FAILED — batch: " + rs.problems); return; }

        // 6) Quantity
        boolean qtyOk = rs.enterQuantity(QTY);
        step("Enter quantity", "Enter the Quantity (" + QTY + ")", "The quantity is accepted", qtyOk ? "Quantity " + rs.lastQty + " entered" : "Problem — " + rs.problems, qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED — quantity: " + rs.problems); return; }

        // 7) Enter -> Free Qty
        boolean toFree = rs.pressEnterInQuantity();
        step("Click Enter after the quantity", "Press Enter in the Quantity box", "Focus moves to Free Qty",
                toFree ? "Focus is on Free Qty" : "Problem — " + rs.problems, toFree ? "PASS" : "FAIL");
        if (!toFree) { addSummary("Result", "FAILED — Enter in Quantity: " + rs.problems); return; }

        // 8) Free quantity
        boolean freeOk = rs.enterFreeQty(FREE);
        step("Enter free qty", "Enter the Free Qty (" + FREE + ")", "The free quantity is accepted", freeOk ? "Free Qty " + rs.lastFree + " entered" : "Problem — " + rs.problems, freeOk ? "PASS" : "FAIL");
        if (!freeOk) { addSummary("Result", "FAILED — free quantity: " + rs.problems); return; }

        // 9) Enter -> the line is added
        int before = rs.itemLines();
        int after = rs.pressEnterToAdd(before);
        boolean added = rs.ok() && after > before;
        step("Click Enter after the free qty", "Press Enter in the Free Qty box", "The line is added to List of Items",
                added ? "List of Items went from " + before + " to " + after + " line(s)" : "Problem — " + rs.problems, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — add line: " + rs.problems); return; }

        // 10) verify both grids
        boolean verified = rs.verifyAdded(before, CODE, NAME, batch.startsWith("(") ? "" : batch, QTY, FREE);
        List<String> itemProblems = new ArrayList<>(), totalProblems = new ArrayList<>();
        for (String p : rs.problems) (p.startsWith("List of Total Items") ? totalProblems : itemProblems).add(p);

        rs.showItemsGrid();
        step("Verify the row is added in List of Items", "Look at the List of Items grid", "A line for " + CODE + " with the entered quantity and an amount is listed",
                itemProblems.isEmpty() ? "PASSES because the grid shows: " + rs.lastItemRow + (rs.lastLineFree.isEmpty() ? "" : " (Free Qty " + rs.lastLineFree + " held on the line)")
                                       : "FAILS because " + String.join("; ", itemProblems),
                itemProblems.isEmpty() ? "PASS" : "FAIL");

        rs.showTotalsGrid();
        step("Verify the row is added in List of Total Items", "Look at the List of Total Items grid", "A row whose Total Amount adds up the lines and whose Net Amount is Total + Tax is listed",
                totalProblems.isEmpty() ? "PASSES because the grid shows: " + rs.lastTotalsRow : "FAILS because " + String.join("; ", totalProblems),
                totalProblems.isEmpty() ? "PASS" : "FAIL");

        addSummary("Store", rs.lastStore);
        addSummary("Item", CODE + " — " + NAME + (rs.simulated ? "  (suggestion pick SIMULATED: the screen's item search returns HTTP 400)" : ""));
        addSummary("Batch / quantity / free qty", batch + " / " + QTY + " / " + FREE);
        addSummary("List of Items", rs.lastItemRow);
        addSummary("List of Total Items", rs.lastTotalsRow);
        addSummary("Result", verified ? "Line added and listed in List of Items and List of Total Items (not saved)" : "FAILED — " + String.join("; ", rs.problems));
    }

    /**
     * Type {@code term} in the code / name box and choose the matching suggestion. Returns false (after recording the failure) when the item could not be chosen.
     * With {@code -Dretailersale.simulatePick=true} a failed search is followed by a simulated click, and the step is reported MANUAL.
     */
    private boolean pickItem(com.kpj.pages.Inventory_page.POS_page.RetailerPharmacySale rs, boolean byCode, String term, String stepName, String desc) {
        List<String> sugg = rs.searchItem(byCode, term);
        String diagnosis = rs.searchDiagnosis;
        boolean offered = !sugg.isEmpty();
        String picked = "";
        if (offered) picked = rs.pickSuggestion(term);
        boolean ok = offered && !picked.isEmpty() && rs.ok();
        String shown = ok ? "Chose \"" + picked + "\" -> code " + rs.lastCode + ", name \"" + rs.lastName + "\"" : "";
        String status = ok ? "PASS" : "FAIL";
        String actual = ok ? shown : (offered ? "Suggestions appeared but the item could not be chosen — " + rs.problems : "No suggestion appeared — " + diagnosis);
        if (!ok && !offered && SIMULATE) {
            String why = rs.simulatePick(NAME, CODE);
            if (why.isEmpty()) {
                ok = true;
                status = "MANUAL";
                actual = "No suggestion could appear — " + diagnosis + " | the suggestion click was SIMULATED (-Dretailersale.simulatePick=true): code " + rs.lastCode + ", name \"" + rs.lastName + "\"";
            } else actual += " | the simulated pick failed too — " + why;
        }
        step(stepName, desc, "The item is offered as a suggestion and its code and name fill in", actual, status);
        if (!ok) addSummary("Result", "FAILED — " + stepName.toLowerCase() + ": " + (offered ? rs.problems.toString() : diagnosis));
        return ok;
    }
}
