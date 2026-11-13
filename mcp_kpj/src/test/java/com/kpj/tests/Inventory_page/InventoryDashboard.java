package com.kpj.tests.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Inventory &gt; <b>Inventory Dashboard</b> — READ-ONLY automated checks (DASH-01 … DASH-15).
 *
 * <p>Built from the manual execution of 13/11/2026 (Inventory_Dashboard_Manual_Report.html). The dashboard has no
 * write actions; only filters, Search and card navigation are used. Known defects (counters empty on open, "all stores"
 * totals, silent date correction, GRN card opening Supplier Return Note) are expected to FAIL.</p>
 *
 * <p>Data: store {@code -Ddash.store} (default "Pharmacy Main Store"), second store {@code -Ddash.store2}
 * (default "Pharmacy (Central)"), wide range from {@code -Ddash.from} (default 01/01/2024).</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Inventory_page.InventoryDashboard" -Ddevhis.postrun=off
 *   report: test-output/reports/Inventory_Dashboard.html
 * </pre>
 */
public class InventoryDashboard extends DevHisBase {

    private static final String TODAY = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    private static final String STORE = System.getProperty("dash.store", "Pharmacy Main Store");
    private static final String STORE2 = System.getProperty("dash.store2", "Pharmacy (Central)");
    private static final String FROM = System.getProperty("dash.from", "01/01/2024");
    private int pass, fail, skipped, blocked;

    public InventoryDashboard() { super("Inventory_Dashboard"); }

    public static void main(String[] args) {
        InventoryDashboard t = new InventoryDashboard();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private interface Check { String[] run() throws Exception; }

    private void tc(String id, String name, String desc, String expected, Check c) {
        long t0 = System.currentTimeMillis();
        String status, actual;
        try {
            String[] r = c.run();
            status = r[0];
            actual = r[1];
        } catch (Throwable e) {
            status = "FAIL";
            actual = "FAILURE REASON - exception: " + String.valueOf(e.getMessage()).split("\\R")[0];
        }
        actual += " | duration " + String.format("%.1f", (System.currentTimeMillis() - t0) / 1000.0) + " s";
        step(page, id + " " + name, desc, expected, actual, status);
        switch (status) {
            case "PASS": pass++; break;
            case "SKIPPED": skipped++; break;
            case "BLOCKED": blocked++; break;
            default: fail++;
        }
    }

    private static String[] r(boolean ok, String actual) { return new String[]{ok ? "PASS" : "FAIL", actual}; }

    /** Fresh dashboard, store + range, searched; returns the counters (empty map if the search failed). */
    private Map<String, Object> counts(com.kpj.pages.Inventory_page.InventoryDashboard d, String store, String from, String to) {
        d.reopen();
        d.pickStore(store);
        d.setDates(from, to);
        int s = d.search();
        return s == 200 ? d.counters() : new java.util.LinkedHashMap<>();
    }

    @Override
    protected void body() {
        meta("Inventory - Inventory Dashboard (read-only)",
                "Inventory > Inventory Dashboard",
                "Read-only checks derived from the 13/11/2026 manual execution: widgets, filters, counters and card navigation. "
                        + "Stores: " + STORE + " / " + STORE2 + ", range from " + FROM + ".");

        com.kpj.pages.Inventory_page.InventoryDashboard d = new com.kpj.pages.Inventory_page.InventoryDashboard(page);
        d.installGuards();
        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login (existing LoginPage)", "Authenticated", "Logged in at " + page.url(), "PASS");

        tc("DASH-01", "Dashboard opens", "Inventory > Inventory Dashboard (sidebar)", "#/InventoryDashboard opens with its Search icon",
                () -> { boolean ok = d.open(); return r(ok, ok ? "Opened " + page.url() : "FAILURE REASON - dashboard did not open; at " + page.url()); });

        tc("DASH-02", "Title and layout", "Header, filter bar and widgets", "Header 'INVENTORY DASHBOARD'; Store/From/To filters; 3 cards with 11 counters",
                () -> {
                    d.open();
                    assertThat(page.locator(".page_title").first()).containsText("INVENTORY DASHBOARD",
                            new com.microsoft.playwright.assertions.LocatorAssertions.ContainsTextOptions().setIgnoreCase(true).setTimeout(15000));
                    int cards = d.cardCount();
                    int labels = d.renderedCounters().size();
                    boolean filters = d.optionCount("InvDashboardList.storeid") > 1 && TODAY.equals(d.inputValue("InvDashboardList.FromDate"));
                    return r(cards == 3 && labels >= 11 && filters, "Cards=" + cards + ", counter labels=" + labels + ", store options=" + d.optionCount("InvDashboardList.storeid")
                            + ", From=" + d.inputValue("InvDashboardList.FromDate"));
                });

        tc("DASH-03", "Widgets load data on open", "Open the dashboard and wait, without pressing Search",
                "Every counter shows a value",
                () -> {
                    d.reopen();
                    page.waitForTimeout(3000);
                    Map<String, Object> c = d.counters();
                    long missing = c.values().stream().filter(v -> v == null).count();
                    return r(missing == 0, missing + " of " + c.size() + " counters empty on open | rendered " + d.renderedCounters());
                });

        tc("DASH-04", "Search with default filters", "Store = Select, today, Search", "API 200; all counters numeric",
                () -> {
                    d.reopen();
                    int s = d.search();
                    Map<String, Object> c = d.counters();
                    boolean numeric = c.values().stream().allMatch(v -> v instanceof Number);
                    return r(s == 200 && numeric, "API " + s + ", request " + d.lastRequest + " | " + c);
                });

        tc("DASH-05", "Store filter is sent", "Store '" + STORE + "', today, Search", "Request carries the store id",
                () -> {
                    d.reopen();
                    String picked = d.pickStore(STORE);
                    int s = d.search();
                    return r(s == 200 && !picked.isEmpty() && d.lastRequest.matches(".*\"storeid\":\\d+.*"), "Picked '" + picked + "'; request " + d.lastRequest);
                });

        tc("DASH-06", "Date filter / populated data", "Store '" + STORE + "', " + FROM + " - today", "Counters populated (some above 0)",
                () -> {
                    Map<String, Object> c = counts(d, STORE, FROM, TODAY);
                    if (c.isEmpty()) return r(false, "FAILURE REASON - search did not return 200");
                    long t = com.kpj.pages.Inventory_page.InventoryDashboard.total(c);
                    if (t == 0) return new String[]{"BLOCKED", "All counters 0 for " + STORE + " since " + FROM + " - no data"};
                    return r(true, "Total " + t + " | " + c);
                });

        tc("DASH-07", "Counters follow the store", "Same range for '" + STORE2 + "'", "Counters differ from '" + STORE + "'",
                () -> {
                    Map<String, Object> a = counts(d, STORE, FROM, TODAY);
                    Map<String, Object> b = counts(d, STORE2, FROM, TODAY);
                    if (a.isEmpty() || b.isEmpty()) return r(false, "FAILURE REASON - a search did not return 200");
                    return r(!a.equals(b), STORE + " " + a + " | " + STORE2 + " " + b);
                });

        tc("DASH-08", "All stores totals", "Store = Select (no store), " + FROM + " - today, compare with '" + STORE + "'",
                "Every 'all stores' counter is >= the single-store counter (or a prompt to choose a store)",
                () -> {
                    Map<String, Object> one = counts(d, STORE, FROM, TODAY);
                    Map<String, Object> all = counts(d, "Select", FROM, TODAY);
                    List<String> alerts = d.alerts();
                    if (!alerts.isEmpty()) return r(true, "Prompted: " + alerts);
                    StringBuilder lower = new StringBuilder();
                    for (String k : one.keySet()) {
                        long o = one.get(k) instanceof Number ? ((Number) one.get(k)).longValue() : 0;
                        long a = all.get(k) instanceof Number ? ((Number) all.get(k)).longValue() : 0;
                        if (a < o) lower.append(k).append(" all=").append(a).append(" < store=").append(o).append("; ");
                    }
                    return r(lower.length() == 0, lower.length() == 0 ? "All-store counters >= single store" : "FAILURE REASON - " + lower);
                });

        tc("DASH-09", "Invalid date range", "From today, To 01/01/" + LocalDate.now().getYear() + ", Search", "A validation message",
                () -> {
                    d.reopen();
                    d.clearCaptured();
                    d.setDates(TODAY, "01/01/" + LocalDate.now().getYear());
                    d.search();
                    List<String> a = d.alerts();
                    return r(!a.isEmpty(), "Messages: " + (a.isEmpty() ? "(none)" : a) + " | request " + d.lastRequest);
                });

        tc("DASH-10", "Counters rendered match the data", "After a store search, compare card texts with the values loaded",
                "Each card shows 'Label : value' equal to the loaded value",
                () -> {
                    Map<String, Object> c = counts(d, STORE, FROM, TODAY);
                    List<String> shown = d.renderedCounters();
                    StringBuilder bad = new StringBuilder();
                    for (Map.Entry<String, String> e : com.kpj.pages.Inventory_page.InventoryDashboard.COUNTERS.entrySet()) {
                        String want = e.getValue() + " : " + c.get(e.getKey());
                        final String w = want.replaceAll("\\s*:\\s*", ":");
                        if (shown.stream().noneMatch(x -> x.replaceAll("\\s+", " ").replaceAll("\\s*:\\s*", ":").equalsIgnoreCase(w))) bad.append(want).append("; ");
                    }
                    return r(bad.length() == 0, bad.length() == 0 ? "All 11 counters match: " + shown : "Not shown as expected: " + bad + " | shown " + shown);
                });

        tc("DASH-11", "Navigation: Purchase Request card", "Click the Purchase Request card", "Opens #/PurchaseRequestList",
                () -> { d.reopen(); String u = d.clickCard("fnRedirecttoPR()"); return r(u.endsWith("#/PurchaseRequestList"), "Opened " + u); });

        tc("DASH-12", "Navigation: Purchase Order card", "Click the Purchase Order card", "Opens #/PurchaseOrderList",
                () -> { d.reopen(); String u = d.clickCard("fnRedirecttoPO()"); return r(u.endsWith("#/PurchaseOrderList"), "Opened " + u); });

        tc("DASH-13", "Navigation: Good Receive Note card", "Click the Good Receive Note card", "Opens the GRN list (#/GRNList)",
                () -> {
                    d.reopen();
                    String u = d.clickCard("fnRedirecttoGrn()");
                    return r(u.endsWith("#/GRNList"), "Opened " + u + (u.contains("GoodsReturnNote") ? " (Supplier Return Note, not GRN)" : ""));
                });

        tc("DASH-14", "Refresh / expand-collapse / charts", "Inspect the dashboard for these controls", "Present if supported",
                () -> new String[]{"SKIPPED", "Not offered: the dashboard has only counter cards and a Search icon (no charts, tables, refresh or expand/collapse)"});

        tc("DASH-15", "API status and console errors", "Everything logged during this run (dashboard and the screens its cards opened)",
                "No HTTP >= 400 and no console / page errors",
                () -> r(d.apiFailures.isEmpty() && d.consoleErrors.isEmpty(),
                        "HTTP>=400: " + d.apiFailures + " | console: " + d.consoleErrors + " | API calls logged: " + d.apiLog.size()));

        addSummary("Mode", "READ-ONLY (dashboard has no write actions; write guard active anyway)");
        addSummary("Test cases", (pass + fail + skipped + blocked) + " - PASS " + pass + " / FAIL " + fail + " / SKIPPED " + skipped + " / BLOCKED " + blocked);
        addSummary("Failed API responses", d.apiFailures.isEmpty() ? "none" : String.join(" ; ", d.apiFailures));
        addSummary("Console errors", d.consoleErrors.isEmpty() ? "none" : String.join(" ; ", d.consoleErrors));
    }
}
