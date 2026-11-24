package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.List;

// The page object is also named AutoMultiplePO — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Auto/ Multiple PO</b> ({@code #/AutoPO}).
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists); open <b>Inventory</b> → <b>Purchase</b> → <b>Auto/ Multiple PO</b>.</li>
 *   <li>Select the <b>Store</b> — the pharmacy stores first, in this order: OPD - Fertility Pharmacy, Outpatient Pharmacy, Pharmacy (Central), Pharmacy Main Store, Pharmay Store New.</li>
 *   <li>Click <b>Search</b> — when a store lists nothing, the next store is selected and searched until one lists rows in <b>Auto PO Details</b>.</li>
 *   <li>Select (tick) one row of Auto PO Details; the <b>Supplier</b> is then filled in by the screen itself — verify it.</li>
 *   <li>Click <b>Generate PO</b> and verify the answer (a success message, the request it sends and the PO Number it gives the row).</li>
 * </ol>
 *
 * <p>&#9888; A passing run creates a REAL purchase order. Pin the store with {@code -Dapo.store=}; {@code -Dapo.maxStores=} caps how many stores are tried (default 20).</p>
 */
public class AutoMultiplePO extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /** The stores tried first, in this order (the user's list). */
    private static final String[] STORE_PRIORITY = {"OPD - Fertility Pharmacy", "Outpatient Pharmacy", "Pharmacy (Central)", "Pharmacy Main Store", "Pharmay Store New"};

    public AutoMultiplePO() { super("Inventory_Purchase_AutoMultiplePO"); }

    public static void main(String[] args) {
        AutoMultiplePO t = new AutoMultiplePO();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Purchase - Auto/ Multiple PO", "Inventory > Purchase > Auto/ Multiple PO",
                "&#9888; Creates a REAL purchase order: select the Store (pharmacy stores first), Search, tick one row of Auto PO Details, check the Supplier the screen fills in, click Generate PO.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        int maxStores = Integer.getInteger("apo.maxStores", 20);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")", "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.AutoMultiplePO apo = new com.kpj.pages.Inventory_page.Purchase_page.AutoMultiplePO(page);

        // 1) Navigate
        boolean on = apo.navigateViaMenu();
        step(page, "Open Auto/ Multiple PO screen", "Click Inventory -> Purchase -> Auto/ Multiple PO", "The Auto/ Multiple PO screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Auto/ Multiple PO not reached"); return; }

        List<String> storeList = apo.stores();
        if (storeList.isEmpty()) {
            step(page, "Select the Store", "Open the Store list", "The list offers stores", "FAILED - the Store list is empty", "FAIL");
            addSummary("Result", "FAILED - the Store list is empty");
            return;
        }
        // the order the stores are tried in: the pinned one, the priority list, then the list order
        List<String> order = new ArrayList<>();
        String pinned = System.getProperty("apo.store", "");
        if (!pinned.isEmpty() && storeList.contains(pinned)) order.add(pinned);
        for (String want : STORE_PRIORITY) for (String s : storeList) if (s.equalsIgnoreCase(want) && !order.contains(s)) order.add(s);
        for (String s : storeList) if (!order.contains(s) && order.size() < maxStores) order.add(s);
        List<String> missing = new ArrayList<>();
        for (String want : STORE_PRIORITY) if (storeList.stream().noneMatch(s -> s.equalsIgnoreCase(want))) missing.add(want);

        // 2) Store
        String first = order.get(0);
        boolean storeOk = apo.selectStore(first);
        step(page, "Select Store", "Open the Store list and click \"" + first + "\"", "The store is selected from the list",
                storeOk ? "Store \"" + apo.lastStore + "\" selected from the list (" + storeList.size() + " stores offered)" + (missing.isEmpty() ? "" : "; not in the list: " + missing)
                        : "FAILED - " + apo.problems, storeOk ? "PASS" : "FAIL");
        if (!storeOk) { addSummary("Result", "FAILED - store: " + apo.problems); return; }

        // 3) Search; the next store until Auto PO Details lists rows
        List<String> tried = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> counts = new ArrayList<>();
        tried.add(first);
        int n = apo.clickSearch();
        counts.add(first + ": " + n);
        String foundStore = n > 0 ? apo.lastStore : "";
        System.out.println("AutoMultiplePO: [1/" + order.size() + "] " + first + " -> " + n + " row(s)");
        for (int i = 1; i < order.size() && n == 0; i++) {
            String next = order.get(i);
            if (!apo.selectStore(next)) { skipped.add(next + " (" + apo.problems + ")"); continue; }
            tried.add(next);
            n = apo.clickSearch();
            counts.add(next + ": " + n);
            if (!(apo.lastSearchStatus > 0 && apo.lastSearchStatus < 400)) skipped.add(next + " (search: " + apo.problems + ")");
            System.out.println("AutoMultiplePO: [" + (i + 1) + "/" + order.size() + "] " + next + " -> " + n + " row(s)");
            if (n > 0) foundStore = apo.lastStore;
        }
        boolean found = n > 0;
        step(page, "Click Search (next store until rows come back)", "Click Search; if the store lists nothing select the next store and search again",
                "Rows are displayed in Auto PO Details",
                found ? n + " row(s) listed for Store \"" + foundStore + "\" (stores tried: " + counts + ")" + (skipped.isEmpty() ? "" : "; could not be tried: " + skipped)
                      : "FAILED - none of the " + tried.size() + " store(s) tried listed a row (" + counts + ")" + (skipped.isEmpty() ? "" : "; could not be tried: " + skipped) + (apo.ok() ? "" : "; last Search problem: " + apo.problems),
                found ? "PASS" : "FAIL");
        addSummary("Stores tried", tried.size() + " of " + order.size() + (found ? " - rows listed for \"" + foundStore + "\"" : " - no store lists a row"));
        if (!found) { addSummary("Result", "FAILED - no store lists a row in Auto PO Details"); return; }

        // 4) Auto PO Details populated
        int rows = apo.itemRowCount();
        List<String> heads = apo.headers();
        boolean shown = rows > 0 && heads.stream().anyMatch(h -> h.equalsIgnoreCase("Supplier"));
        step(page, "Verify Auto PO Details is populated", "Read Auto PO Details after Search", "The items are listed with their quantities and Supplier column",
                shown ? rows + " row(s) listed (columns " + heads + "); first: " + apo.sample(2) : "FAILED - Auto PO Details shows " + rows + " row(s), columns " + heads,
                shown ? "PASS" : "FAIL");
        if (!shown) { addSummary("Result", "FAILED - Auto PO Details not populated"); return; }

        // 5) Select 1 row
        String item = apo.tickRow(0);
        boolean ticked = !item.isEmpty() && apo.ok() && apo.tickedCount() == 1;
        step(page, "Select one row in Auto PO Details", "Tick the first row of Auto PO Details", "One row is selected", ticked ? "Ticked: " + item : "FAILED - " + apo.problems, ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED - no row could be selected"); return; }

        // 6) the Supplier is entered automatically
        String supplier = apo.supplierOfRow(0, 8);
        boolean supplierOk = !supplier.isEmpty();
        step(page, "Verify the Supplier is entered automatically", "After ticking the row read its Supplier cell", "The Supplier of the row is filled in by the screen",
                supplierOk ? "Supplier \"" + supplier + "\" was filled in (" + apo.lastRowFacts + ")" : "FAILED - the row's Supplier stayed empty after ticking it (" + apo.lastRowFacts + ")",
                supplierOk ? "PASS" : "FAIL");

        // 7) Generate PO
        String toast = apo.clickGeneratePO(60);
        boolean saved = com.kpj.pages.Inventory_page.Purchase_page.AutoMultiplePO.isSuccess(toast);
        String po = apo.poNumberOfRow(0);
        String actual;
        if (!apo.ok() && toast.isEmpty()) actual = "FAILED - " + apo.problems + "  [requests: " + apo.lastSaveHttp + "]";
        else if (saved) actual = toast + "  [" + apo.lastSaveHttp + "]" + (po.isEmpty() ? "" : "  PO Number " + po);
        else if (toast.isEmpty()) actual = "FAILED - Generate PO was clicked but nothing happened: no message appeared  [requests: " + apo.lastSaveHttp + "]" + (apo.ok() ? "" : "  " + apo.problems);
        else actual = "FAILED - the screen answered \"" + toast + "\"  [requests: " + apo.lastSaveHttp + "]";
        step(page, "Click Generate PO & success message", "Click Generate PO; wait for the answer", "A success message; the purchase order is generated", actual, saved ? "PASS" : "FAIL");

        addSummary("Store", foundStore);
        addSummary("Item", apo.lastItem);
        addSummary("Supplier", apo.lastSupplier.isEmpty() ? "(empty)" : apo.lastSupplier);
        addSummary("PO Number", po.isEmpty() ? "(none shown)" : po);
        addSummary("Result", saved ? toast : (toast.isEmpty() ? "Generate PO did nothing (no message)" : "Not confirmed (\"" + toast + "\")"));
    }
}
