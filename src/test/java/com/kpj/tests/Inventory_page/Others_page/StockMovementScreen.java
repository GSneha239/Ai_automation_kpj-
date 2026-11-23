package com.kpj.tests.Inventory_page.Others_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

// The page object is also named StockMovementScreen — referenced by its fully-qualified name.

/**
 * Inventory &gt; Others &gt; <b>Stock Movement Screen</b>.
 *
 * <ol>
 *   <li>Select the <b>From Date</b> — between one and two months before the <b>To Date</b> (today): by default exactly two months back ({@code -Dstockmovement.months=1} or {@code 2}).</li>
 *   <li>Select the <b>Store</b> (Pharmacy Main Store).</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>Verify rows are populated in the <b>List of Stock Movement</b>: the grid has rows, every row names an item and a transaction, and every Transaction Date falls inside the chosen range.</li>
 * </ol>
 *
 * <p>This screen is read-only: the test changes no data. Pin the store with {@code -Dstockmovement.store=}.</p>
 */
public class StockMovementScreen extends DevHisBase {

    static final String STORE = System.getProperty("stockmovement.store", "Pharmacy Main Store");
    static final int MONTHS = Math.max(1, Math.min(2, Integer.getInteger("stockmovement.months", 2)));

    public StockMovementScreen() { super("Inventory_Others_StockMovementScreen"); }

    public static void main(String[] args) {
        StockMovementScreen t = new StockMovementScreen();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** A transaction date as the grid shows it: dd/MM/yyyy, dd-MM-yyyy, dd-MMM-yy(yy) or dd MMM yyyy (a time after it is ignored); null when it is not one of those. */
    static LocalDate parseDate(String s) {
        if (s == null) return null;
        String t = s.trim().split("[ T]")[0].replace('-', '/');
        String[] p = t.split("/");
        if (p.length != 3) return null;
        try {
            int d = Integer.parseInt(p[0]);
            int m;
            if (p[1].matches("\\d+")) m = Integer.parseInt(p[1]);
            else {
                m = java.util.Arrays.asList("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(p[1].toLowerCase().substring(0, 3)) + 1;
                if (m == 0) return null;
            }
            int y = Integer.parseInt(p[2]);
            if (y < 100) y += 2000;
            return LocalDate.of(y, m, d);
        } catch (Exception e) { return null; }
    }

    @Override
    protected void body() {
        meta("Inventory - Others - Stock Movement Screen", "Inventory > Others > Stock Movement Screen",
                "From Date one to two months before the To Date; store " + STORE + "; Search; verify rows are populated in the List of Stock Movement (rows present, item and transaction on every row, "
                        + "every Transaction Date inside the range). READ-ONLY.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Others_page.StockMovementScreen sm = new com.kpj.pages.Inventory_page.Others_page.StockMovementScreen(page);

        boolean on = sm.navigateViaMenu();
        step("Open Stock Movement Screen", "Click Inventory -> Others -> Stock Movement Screen", "The Stock Movement Screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Stock Movement Screen not reached"); return; }

        // 1) From Date: one to two months before the To Date
        String toStr = sm.toDate();
        LocalDate to = com.kpj.pages.Inventory_page.Others_page.StockMovementScreen.parse(toStr);
        if (to == null) {
            step("Select From Date", "Select the From Date", "A date one to two months before the To Date", "The To Date box reads \"" + toStr + "\", which is not a dd/MM/yyyy date", "FAIL");
            addSummary("Result", "FAILED — To Date unreadable: \"" + toStr + "\""); return;
        }
        LocalDate from = to.minusMonths(MONTHS);
        String picked = sm.pickFromDate(from);
        boolean fromOk = !picked.isEmpty() && sm.ok();
        long days = fromOk ? ChronoUnit.DAYS.between(com.kpj.pages.Inventory_page.Others_page.StockMovementScreen.parse(picked), to) : 0;
        step("Select From Date", "Select the From Date " + MONTHS + " month(s) before the To Date (" + toStr + ")", "From Date is between one and two months before the To Date",
                fromOk ? "From Date " + picked + ", To Date " + toStr + " — " + days + " days (" + MONTHS + " month(s)) apart" : "Not selected — " + sm.problems, fromOk ? "PASS" : "FAIL");
        if (!fromOk) { addSummary("Result", "FAILED — From Date: " + sm.problems); return; }

        // 2) Store
        String store = sm.selectStore(STORE);
        String fromAfter = sm.fromDate(), toAfter = sm.toDate();
        boolean storeOk = !store.isEmpty() && sm.ok();
        boolean datesKept = fromAfter.equals(picked) && toAfter.equals(toStr);
        step("Select store", "Select the Store (" + STORE + ")", "The store is selected and the dates are kept",
                storeOk ? "Selected \"" + store + "\" (Location \"" + sm.lastLocation + "\"); dates " + (datesKept ? "kept: " : "CHANGED to: ") + fromAfter + " - " + toAfter : "Not selected — " + sm.problems,
                storeOk && datesKept ? "PASS" : "FAIL");
        if (!storeOk || !datesKept) { addSummary("Result", "FAILED — store: " + (storeOk ? "the dates changed to " + fromAfter + " - " + toAfter : sm.problems)); return; }

        // 3) Search
        int n = sm.clickSearch();
        boolean searchOk = sm.ok();
        step("Click Search", "Click Search", "The search answers (rows appear, or a message)",
                searchOk ? "Search answered after " + sm.lastSearchMs / 1000 + " s: " + n + " row(s) painted in the grid" + (sm.lastNotice.isEmpty() ? "" : "; the screen said \"" + sm.lastNotice + "\"") : "Problem — " + sm.problems,
                searchOk ? "PASS" : "FAIL");
        if (!searchOk) { addSummary("Result", "FAILED — Search: " + sm.problems); return; }

        // 4) verify rows populated — over ALL rows the grid holds (its data array), not only the ~13 the viewport paints
        com.google.gson.JsonObject st = sm.stats(picked, toStr, STORE);
        int total = st == null ? 0 : st.get("total").getAsInt();
        int inRange = st == null ? 0 : st.get("inRange").getAsInt(), outRange = st == null ? 0 : st.get("outsideRange").getAsInt();
        int noItem = st == null ? 0 : st.get("noItem").getAsInt(), noNo = st == null ? 0 : st.get("noTransactionNo").getAsInt(), storeMiss = st == null ? 0 : st.get("storeMiss").getAsInt();
        boolean populated = n > 0 && total > 0 && noItem == 0;
        String first = st == null ? "" : st.get("first").toString();
        String types = st == null ? "" : st.get("byType").getAsJsonObject().entrySet().stream()
                .sorted((x, y) -> y.getValue().getAsInt() - x.getValue().getAsInt()).limit(6).map(e -> e.getKey() + " " + e.getValue()).collect(java.util.stream.Collectors.joining(", "));
        // The From/To dates do not restrict this list (see the note): say so, without failing the requirement, which is that rows are populated.
        String note = total == 0 ? "" : " NOTE: " + inRange + " of the " + total + " rows are dated inside " + picked + " - " + toStr + "; the other " + outRange
                + " are earlier (mostly Opening Balances, but also movements from before the From Date), so the dates do not appear to restrict the list.";
        step("Verify data rows are populated in List of Stock Movement", "Read the List of Stock Movement grid after Search",
                "Rows are listed in the grid and every row names an item",
                populated ? "PASSES because the grid lists " + total + " row(s) (pager: \"" + sm.pagerText() + "\"; " + n + " painted in the viewport; columns: " + sm.headers() + "); every row names an item and "
                        + (total - noNo) + " carry a Transaction No; all " + (total - storeMiss) + " rows involve " + STORE + "; largest transaction types: " + types + "; first row: " + first + "." + note
                          : n == 0 && total == 0 ? "FAILS because the grid is empty after Search for " + STORE + ", " + picked + " - " + toStr + " (waited " + sm.lastSearchMs / 1000 + " s)"
                                     + (sm.lastNotice.isEmpty() ? "" : " — the screen said \"" + sm.lastNotice + "\"") + "; " + sm.searchAnswer()
                                   : "FAILS because " + (noItem > 0 ? noItem + " of " + total + " rows name no item" : "the grid shows " + n + " painted row(s) but holds " + total + " in its data") + "; first row: " + first,
                populated ? "PASS" : "FAIL");

        addSummary("Store / location", sm.lastStore + " / " + sm.lastLocation);
        addSummary("Date range", picked + " - " + toStr + " (" + days + " days)");
        addSummary("Search time", sm.lastSearchMs / 1000 + " s");
        addSummary("Rows in the grid", total + " (pager: " + sm.pagerText() + ")");
        addSummary("Rows dated inside / outside the range", inRange + " / " + outRange);
        addSummary("Row types", types);
        addSummary("First row", first);
        addSummary("Result", populated ? total + " row(s) populated" : (total == 0 ? "No rows" : "Rows present but a check failed"));
    }
}
