package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Inventory_page.Purchase_page.EnquiryDashboard.Grid;
import com.microsoft.playwright.Response;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

// The page object is also named EnquiryDashboard — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Enquiry Dashboard</b> — STRICTLY READ-ONLY functional test.
 *
 * <p>Covers screen loading, search (valid / different criteria / no-match / empty / invalid / reversed dates), the store
 * filter and statistic tiles, grid headers, data, sorting, column filters, pagination and page size, record counts,
 * empty-grid behaviour, view/report options, and console / API / application errors. Each check is one row in
 * {@code test-output/reports/Inventory_Purchase_EnquiryDashboard.html}, named with its test case id (ED-nn).</p>
 *
 * <h3>Safety</h3>
 * <ul>
 *   <li>The screen has no save / approve / cancel / delete action. The only clicks are the menu, Search, the statistic
 *       tiles, column headers, column filters and the pager.</li>
 *   <li>A network guard aborts every non-GET {@code /api/} call that is not a read call (fetch / fetchList / Get…),
 *       and the report lists anything it stopped.</li>
 *   <li>The grid menu's CSV / PDF export downloads a file, so it is not clicked — reported as MANUAL.</li>
 *   <li>Credentials come only from DEVHIS_USER / DEVHIS_PASS (or -Ddevhis.user / -Ddevhis.pass); the test stops before
 *       logging in when they are missing, and never writes the user name or password to the report or console.</li>
 * </ul>
 *
 * <p>Nothing is hardcoded about the data: counts, rows and dates are read from the screen and its API responses.
 * Tunables: {@code -Ded.fromDate=01/01/2024} (wide search start, dd/MM/yyyy), {@code -Ded.store="Pharmacy Main Store"}
 * (store for the data checks; falls back to the default store when absent), {@code -Ded.store2=...} (store for the
 * "different criteria" search; default: another store from the list).</p>
 *
 * <pre>
 *   mvn test -Dtest=com.kpj.tests.Inventory_page.Purchase_page.EnquiryDashboard -Ddevhis.postrun=off
 * </pre>
 */
public class EnquiryDashboard extends DevHisBase {

    public EnquiryDashboard() { super("Inventory_Purchase_EnquiryDashboard"); }

    public static void main(String[] args) {
        EnquiryDashboard t = new EnquiryDashboard();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern API = Pattern.compile("/api/", Pattern.CASE_INSENSITIVE);
    /** Host-anchored, case-insensitive /api/ URL test (this screen calls /API/…, others /api/…). */
    private static final Pattern API_URL = Pattern.compile("^https?://[^/]+/api/", Pattern.CASE_INSENSITIVE);
    /**
     * Route matcher for the read-only guard. A Java {@link java.util.function.Predicate}, NOT a {@code Pattern}: Playwright Java
     * turns a Pattern into a JavaScript RegExp, and the Java-only inline flag {@code (?i)} made the first run fail with
     * "Invalid regular expression: /(?i)^https?://[^/]+/api/.* /i: Invalid group". A predicate is evaluated in Java only,
     * keeps the match case-insensitive for /api/ and /API/, and is the same instance for route() and unroute().
     */
    private static final java.util.function.Predicate<String> API_ROUTE = url -> url != null && API_URL.matcher(url).find();
    private static final Pattern READ_CALL = Pattern.compile("/(fetch|fetchlist|fetchlistfirst|fetchheader|get[a-z]*|load|search|list|view|find|check)[^/]*$",
            Pattern.CASE_INSENSITIVE);

    private com.kpj.pages.Inventory_page.Purchase_page.EnquiryDashboard ed;
    private final LocalDate today = LocalDate.now();
    private final List<String> defects = new ArrayList<>();
    private final List<String> consoleErrors = new ArrayList<>();
    private final List<String> apiFailures = new ArrayList<>();
    private final List<String> appErrors = new ArrayList<>();
    private final List<String> blockedWrites = new ArrayList<>();
    private final List<Response> dashboardCalls = new ArrayList<>();

    @Override
    protected void body() {
        meta("Inventory - Purchase - Enquiry Dashboard", "Inventory > Purchase > Enquiry Dashboard",
                "<b>Read-only</b> functional test: search, store filter, statistic tiles, grids (headers, data, sorting, column "
                        + "filters, pagination, page size), view/report options and console/API errors. Each row is one test case "
                        + "(ED-nn). Nothing is saved, approved, cancelled or deleted.");

        if (!credentialsFromEnvironment()) return;
        attachMonitors();
        login();
        step("ED-01 Login", "Log in with the DEVHIS_USER / DEVHIS_PASS account (values not shown)", "Authenticated; application shell loaded",
                "Logged in — " + page.url().replaceAll("[?].*$", ""), "PASS");
        installReadOnlyGuard();
        ed = new com.kpj.pages.Inventory_page.Purchase_page.EnquiryDashboard(page);

        try {
            section("A. Screen loading", this::screenLoading);
            if (!ed.onRoute()) { addSummary("Result", "FAILED — Enquiry Dashboard could not be opened"); return; }
            section("B. Search", this::searchChecks);
            section("C. Filters and tiles", this::filterChecks);
            section("D. Grid", this::gridChecks);
            section("E. View and report", this::viewAndReport);
            section("F. Validation of criteria", this::criteriaValidation);
            section("G. Errors", this::errorChecks);
        } finally {
            try { page.unroute(API_ROUTE); } catch (Exception ignore) { }
            writeSummary();
        }
    }

    // =================================================================================================================
    // A. Screen loading
    // =================================================================================================================

    private void screenLoading() {
        boolean opened = ed.navigateViaMenu();
        step("ED-02 Navigate Inventory > Purchase > Enquiry Dashboard", "Open the sidebar menu path (no direct URL)",
                "Route #/EnquiryDashBoard opens and the screen finishes loading with no wait timing out",
                (opened ? "Opened via " + ed.lastMenuPath + " — " + page.url() : "Did NOT open") + notes(ed.navNotes),
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String h = ed.heading();
        check("ED-03 Verify page heading", "Read the page heading (h1#mainHeader)", "Heading reads \"Enquiry Dashboard\"",
                "Heading: \"" + h + "\"", h.equalsIgnoreCase("Enquiry Dashboard"));

        Map<String, Object> c = ed.controls();
        boolean ctlOk = isTrue(c.get("fromDate")) && isTrue(c.get("toDate")) && isTrue(c.get("store")) && isTrue(c.get("search"));
        check("ED-04 Verify search fields, dropdown and button", "Look for From Date, To Date, Store and the Search button",
                "From Date, To Date (dd/MM/yyyy), Store dropdown and a labelled Search button are visible",
                "From Date: " + yn(c.get("fromDate")) + " (" + c.get("fromPlaceholder") + "), To Date: " + yn(c.get("toDate")) + " (" + c.get("toPlaceholder")
                        + "), Store: " + yn(c.get("store")) + ", Search: " + yn(c.get("search")) + " (label \"" + c.get("searchLabel") + "\")", ctlOk);

        List<String> missing = new ArrayList<>();
        Map<Grid, List<String>> expected = expectedHeaders();
        for (Grid g : new Grid[]{Grid.ENQUIRY_COMPLETE, Grid.QUOTATION_COMPLETE, Grid.PURCHASE_COMPLETE}) {
            Map<String, Object> gi = ed.gridInfo(g);
            if (gi.containsKey("missing") || !ed.gridVisible(g)) missing.add(g.title + " grid not shown");
            else if (!String.valueOf(gi.get("title")).equalsIgnoreCase(g.title)) missing.add(g.title + " panel titled \"" + gi.get("title") + "\"");
            else if (!expected.get(g).equals(gi.get("headers"))) missing.add(g.title + " headers " + gi.get("headers"));
        }
        int tileCount = ((Number) c.getOrDefault("tiles", 0)).intValue();
        if (tileCount != 6) missing.add(tileCount + " statistic tiles (expected 6)");
        check("ED-05 Verify statistic tiles and grids", "Check the six Complete/Pending tiles and the three Complete grids with their column headers",
                "6 tiles; Enquiry Complete " + expected.get(Grid.ENQUIRY_COMPLETE) + ", Quotation Complete " + expected.get(Grid.QUOTATION_COMPLETE)
                        + ", Purchase Complete " + expected.get(Grid.PURCHASE_COMPLETE),
                missing.isEmpty() ? "6 tiles and the 3 grids with the expected headers are shown" : String.join("; ", missing), missing.isEmpty());

        String from = ed.fromDate(), to = ed.toDate(), store = ed.selectedStore();
        List<String> opts = ed.storeOptions();
        boolean datesToday = from.equals(today.format(DMY)) && to.equals(today.format(DMY));
        check("ED-06 Verify default criteria", "Read the default From/To date and Store",
                "From and To default to today (" + today.format(DMY) + ") and a store is pre-selected",
                "From " + from + ", To " + to + ", Store \"" + store + "\" (first of " + opts.size() + " stores)"
                        + (store.toLowerCase().contains("pharmacy") ? "" : " — note: the default is the first store in the list, not a pharmacy store"),
                datesToday && !store.isEmpty());
    }

    // =================================================================================================================
    // B. Search
    // =================================================================================================================

    private String store1;

    private void searchChecks() {
        // ED-07 default search
        int c0 = dashboardCalls.size();
        boolean idle = ed.search();
        List<String> calls = describeCalls(c0);
        Map<String, Integer> t = ed.tiles();
        boolean numeric = t.size() == 6 && t.values().stream().allMatch(v -> v >= 0);
        boolean allOk = idle && calls.size() >= 9 && calls.stream().allMatch(s -> s.contains("HTTP 200"));
        step("ED-07 Search with default criteria", "Click Search with today's dates and the default store",
                "9 dashboard API calls (6 counts + 3 lists) answer HTTP 200; all tiles show a number",
                calls.size() + " call(s): " + head(calls, 9) + "; tiles " + t, allOk && numeric ? "PASS" : "FAIL");

        // ED-08 valid wide search with data
        List<String> opts = ed.storeOptions();
        String wanted = System.getProperty("ed.store", "Pharmacy Main Store");
        store1 = opts.contains(wanted) ? wanted : ed.selectedStore();
        String wideFrom = System.getProperty("ed.fromDate", "01/01/2024");
        ed.typeDate(SEL_TO(), today.format(DMY));
        ed.typeDate(SEL_FROM(), wideFrom);
        ed.selectStore(store1);
        ed.closeToasts();
        c0 = dashboardCalls.size();
        ed.search();
        List<String> calls2 = describeCalls(c0);
        Map<String, Integer> t2 = ed.tiles();
        String consistency = tileGridConsistency(t2, false);
        List<String> outOfRange = datesOutside(Grid.PURCHASE_COMPLETE, parse(wideFrom), today);
        outOfRange.addAll(datesOutside(Grid.ENQUIRY_COMPLETE, parse(wideFrom), today));
        outOfRange.addAll(datesOutside(Grid.QUOTATION_COMPLETE, parse(wideFrom), today));
        int rows = intv(ed.gridInfo(Grid.PURCHASE_COMPLETE).get("total")) + intv(ed.gridInfo(Grid.ENQUIRY_COMPLETE).get("total")) + intv(ed.gridInfo(Grid.QUOTATION_COMPLETE).get("total"));
        String storeNote = storeIdNote(calls2);
        ed.scrollTo(Grid.PURCHASE_COMPLETE);
        step("ED-08 Search with valid criteria (" + store1 + ", " + wideFrom + " – today)", "Set From " + wideFrom + ", To today, Store " + store1 + " and Search",
                "Matching records are displayed: Complete grids hold exactly the tile counts and every record date lies in the searched range",
                "tiles " + t2 + "; " + consistency + "; " + rows + " row(s) in the Complete grids"
                        + (outOfRange.isEmpty() ? "; all dates within range" : "; OUTSIDE range: " + head(outOfRange, 6)) + storeNote,
                consistency.startsWith("counts match") && outOfRange.isEmpty() ? "PASS" : "FAIL");
        if (rows == 0) addSummary("Data note", "No records for " + store1 + " in " + wideFrom + " – today; data-dependent grid checks will be SKIPPED");

        // ED-09 different criteria
        String store2 = System.getProperty("ed.store2", "");
        if (store2.isEmpty() || !opts.contains(store2)) store2 = opts.stream().filter(o -> !o.equals(store1) && !o.startsWith("-")).findFirst().orElse("");
        if (store2.isEmpty()) {
            step("ED-09 Search with different criteria", "Search another store", "Results follow the criteria", "Only one store available", "SKIPPED");
        } else {
            ed.selectStore(store2);
            ed.search();
            Map<String, Integer> t3 = ed.tiles();
            String cons3 = tileGridConsistency(t3, false);
            ed.scrollTo(Grid.ENQUIRY_COMPLETE);
            step("ED-09 Search with different criteria (store " + store2 + ")", "Same dates, another store, Search",
                    "Tiles and grids are refreshed for the new store and stay consistent with each other",
                    "tiles " + t3 + " (previous store: " + t2 + "); " + cons3, cons3.startsWith("counts match") ? "PASS" : "FAIL");
        }

        // ED-10 non-existing values: a future range
        String fFrom = today.plusYears(4).withDayOfYear(1).format(DMY), fTo = today.plusYears(4).withDayOfYear(28).format(DMY);
        ed.selectStore(store1);
        ed.typeDate(SEL_TO(), fTo);
        ed.typeDate(SEL_FROM(), fFrom);
        ed.closeToasts();
        ed.search();
        Map<String, Integer> t4 = ed.tiles();
        boolean allZero = t4.values().stream().allMatch(v -> v == 0);
        int held = intv(ed.gridInfo(Grid.PURCHASE_COMPLETE).get("total")) + intv(ed.gridInfo(Grid.ENQUIRY_COMPLETE).get("total")) + intv(ed.gridInfo(Grid.QUOTATION_COMPLETE).get("total"));
        String msg = ed.emptyGridMessage(Grid.PURCHASE_COMPLETE) + " " + String.join(" ", ed.toasts());
        boolean hasMsg = msg.trim().length() > 0;
        check("ED-10 Search with non-existing values (" + fFrom + " – " + fTo + ")", "Search a future date range where no record can exist",
                "All counts 0, grids empty, and a \"no records found\" message is shown",
                "tiles " + t4 + ", rows held " + held + (hasMsg ? ", message: \"" + msg.trim() + "\"" : ", NO message shown — the grids are simply blank"),
                allZero && held == 0 && hasMsg);
        if (allZero && held == 0 && !hasMsg) defect("ED-10", "No 'no records found' message: an empty search result leaves blank grids and zero tiles with no explanation");
        // restore the data search for the following sections
        ed.typeDate(SEL_TO(), today.format(DMY));
        ed.typeDate(SEL_FROM(), wideFrom);
        ed.closeToasts();
        ed.search();
    }

    // =================================================================================================================
    // C. Filters and tiles
    // =================================================================================================================

    private void filterChecks() {
        List<String> opts = ed.storeOptions();
        Set<String> dup = new LinkedHashSet<>(), seen = new HashSet<>();
        for (String o : opts) if (!seen.add(o)) dup.add(o);
        check("ED-11 Verify Store dropdown options", "Read the Store list", "A non-empty list without duplicates",
                opts.size() + " store(s): " + head(opts, 12) + (dup.isEmpty() ? "" : " — DUPLICATES: " + dup), !opts.isEmpty() && dup.isEmpty());

        // ED-12 tiles open the matching grids with matching counts
        Map<String, Integer> t = ed.tiles();
        List<String> bad = new ArrayList<>(), good = new ArrayList<>();
        for (Grid g : new Grid[]{Grid.ENQUIRY_PENDING, Grid.QUOTATION_PENDING, Grid.PURCHASE_PENDING, Grid.ENQUIRY_COMPLETE, Grid.QUOTATION_COMPLETE, Grid.PURCHASE_COMPLETE}) {
            if (!ed.clickTile(g)) { bad.add(g.title + ": tile not clickable"); continue; }
            Map<String, Object> gi = ed.gridInfo(g);
            int tile = t.getOrDefault(g.tile, -1), rows = intv(gi.get("total"));
            boolean shown = ed.gridVisible(g) && String.valueOf(gi.get("title")).equalsIgnoreCase(g.title);
            if (!shown) bad.add(g.title + " grid not shown after its tile was clicked");
            else if (tile != rows) bad.add(g.title + ": tile " + tile + " vs grid " + rows);
            else good.add(g.title + " " + rows);
        }
        ed.scrollTo(Grid.ENQUIRY_PENDING);
        check("ED-12 Statistic tiles filter the grids", "Click each Pending and Complete tile",
                "Each tile shows its own grid, holding exactly the number on the tile",
                bad.isEmpty() ? "All 6 tiles match: " + String.join(", ", good) : String.join("; ", bad) + (good.isEmpty() ? "" : " — OK: " + String.join(", ", good)), bad.isEmpty());

        // ED-13 reset / clear
        List<String> reset = ed.pageActions("^(reset|clear|refresh)");
        step("ED-13 Clear / reset filters", "Look for a Reset or Clear control", "A way to reset the criteria (if provided)",
                reset.isEmpty() ? "No Reset/Clear control on this screen — not applicable" : "Found: " + reset, reset.isEmpty() ? "SKIPPED" : "PASS");
    }

    // =================================================================================================================
    // D. Grid
    // =================================================================================================================

    private void gridChecks() {
        Grid g = Grid.PURCHASE_COMPLETE;
        ed.clickTile(g);
        List<Map<String, Object>> data = ed.gridData(g, "PONumber", "PODate", "SupplierName", "NetAmount");
        Map<String, Object> gi = ed.gridInfo(g);

        // ED-14 headers of every grid
        List<String> hdrBad = new ArrayList<>();
        Map<Grid, List<String>> exp = expectedHeaders();
        for (Grid x : Grid.values()) {
            Object h = ed.gridInfo(x).get("headers");
            if (!exp.get(x).equals(h)) hdrBad.add(x.title + " " + h);
        }
        check("ED-14 Verify column headers of all six grids", "Read the column definitions of each grid",
                "Enquiry: Enquiry No./Enquiry Date/Supplier Name/Store Name; Quotation: Quotation No./Quotation Date/Total Amount; Purchase: PO Number/PO Date/Supplier Name/Total Amount",
                hdrBad.isEmpty() ? "All six grids have the expected headers" : "Different: " + String.join("; ", hdrBad), hdrBad.isEmpty());

        if (data.isEmpty()) {
            for (String id : new String[]{"ED-15 Data displayed correctly", "ED-16 Sorting", "ED-17 Column filter", "ED-18 Pagination", "ED-19 Page size"})
                step(id, "Data-dependent grid check", "Rows in Purchase Complete", "Purchase Complete holds no rows for the searched criteria — skipped", "SKIPPED");
            return;
        }

        // ED-15 data correctness
        List<String> bad = new ArrayList<>(), trailing = new ArrayList<>(), unformatted = new ArrayList<>();
        Set<String> nos = new HashSet<>(), dups = new LinkedHashSet<>();
        for (Map<String, Object> d : data) {
            String no = s(d.get("PONumber")), dt = s(d.get("PODate")), sup = String.valueOf(d.get("SupplierName"));
            if (no.isEmpty()) bad.add("row without PO Number");
            if (!nos.add(no)) dups.add(no);
            if (parse(dt) == null) bad.add(no + ": PO Date \"" + dt + "\"");
            if (sup == null || sup.trim().isEmpty() || "null".equals(sup)) bad.add(no + ": no supplier");
            else if (!sup.equals(sup.trim())) trailing.add(no);
            Object amt = d.get("NetAmount");
            if (!(amt instanceof Number) || ((Number) amt).doubleValue() < 0) bad.add(no + ": amount " + amt);
            else if (!String.valueOf(amt).matches("-?\\d+\\.\\d{2}")) unformatted.add(no + "=" + amt);
        }
        List<String> shownAmounts = ed.visibleValues(g, "NetAmount");
        ed.scrollTo(g);
        check("ED-15 Verify data is displayed correctly (Purchase Complete)", "Check PO Number, PO Date, Supplier and Total Amount of all " + data.size() + " rows",
                "Every row has a unique PO Number, a valid dd/MM/yyyy date, a supplier and a non-negative amount",
                (bad.isEmpty() && dups.isEmpty() ? "All " + data.size() + " rows valid" : head(bad, 8) + (dups.isEmpty() ? "" : "; duplicate PO numbers " + dups))
                        + (trailing.isEmpty() ? "" : "; (info) " + trailing.size() + " supplier name(s) carry trailing spaces")
                        + (unformatted.isEmpty() ? "" : "; (info) amounts shown without 2 decimals/thousand separators, e.g. " + head(shownAmounts, 4)),
                bad.isEmpty() && dups.isEmpty());

        // ED-16 sorting
        sortingChecks(g, data.size());

        // ED-17 column filter (PO Number)
        String first = s(data.get(0).get("PONumber"));
        String prefix = first.contains("-") ? first.substring(0, first.indexOf('-') + 1) : first.substring(0, Math.min(3, first.length()));
        long expectedMatch = data.stream().filter(d -> s(d.get("PONumber")).toLowerCase().contains(prefix.toLowerCase())).count();
        ed.setPageSize(g, 125);
        ed.filterColumn(g, 0, prefix);
        List<String> shown = ed.visibleValues(g, "PONumber");
        boolean allMatch = shown.stream().allMatch(v -> v.toLowerCase().contains(prefix.toLowerCase()));
        ed.filterColumn(g, 0, "ZZ-NO-SUCH-PO");
        int none = ed.visibleValues(g, "PONumber").size();
        String emptyMsg = ed.emptyGridMessage(g);
        ed.filterColumn(g, 0, "");
        int restored = ed.visibleValues(g, "PONumber").size();
        ed.setPageSize(g, 25);
        check("ED-17 Column filter (PO Number)", "Filter PO Number by \"" + prefix + "\", then by a non-existing value, then clear it",
                "Only matching rows (" + expectedMatch + ") shown; a non-existing value shows 0 rows; clearing restores all rows",
                "\"" + prefix + "\" → " + shown.size() + " row(s)" + (allMatch ? " all matching" : " NOT all matching") + "; \"ZZ-NO-SUCH-PO\" → " + none + " row(s)"
                        + (emptyMsg.isEmpty() ? " (no 'no records' message)" : " (\"" + emptyMsg + "\")") + "; cleared → " + restored + " row(s) of " + data.size(),
                shown.size() == expectedMatch && allMatch && none == 0 && restored == Math.min(data.size(), 125));

        // ED-18 pagination
        int size = intv(gi.get("pageSize")) > 0 ? intv(gi.get("pageSize")) : 25;
        if (data.size() <= size) {
            step("ED-18 Pagination", "Move to the next page", "Rows split into pages of " + size,
                    data.size() + " row(s) fit on one page — pagination not exercisable with this data", "SKIPPED");
        } else {
            ed.pager(g, "pageFirstPageClick");
            int p1 = ed.visibleValues(g, "PONumber").size();
            List<String> firstPage = ed.visibleValues(g, "PONumber");
            ed.pager(g, "pageNextPageClick");
            List<String> secondPage = ed.visibleValues(g, "PONumber");
            Map<String, Object> pg = ed.gridInfo(g);
            int pages = (int) Math.ceil(data.size() / (double) size);
            boolean disjoint = Collections.disjoint(firstPage, secondPage);
            int expectedSecond = Math.min(size, data.size() - size);
            ed.pager(g, "pageFirstPageClick");
            ed.scrollTo(g);
            check("ED-18 Pagination", "First page, then Next page",
                    "Page 1 shows " + size + " rows, page 2 shows the next " + expectedSecond + " different rows; pager shows " + pages + " page(s)",
                    "page 1: " + p1 + " row(s), page 2: " + secondPage.size() + " row(s)" + (disjoint ? ", no overlap" : ", OVERLAP") + ", pager \"" + pg.get("pagerText") + "\"",
                    p1 == size && secondPage.size() == expectedSecond && disjoint && String.valueOf(pg.get("pagerText")).contains(String.valueOf(pages)));
        }

        // ED-19 page size
        ed.setPageSize(g, 50);
        int at50 = ed.visibleValues(g, "PONumber").size();
        Object sizes = ed.gridInfo(g).get("pageSizes");
        ed.setPageSize(g, 25);
        int at25 = ed.visibleValues(g, "PONumber").size();
        check("ED-19 Page-size selection", "Choose 50 rows per page, then 25 again",
                "50 → " + Math.min(50, data.size()) + " rows; 25 → " + Math.min(25, data.size()) + " rows",
                "sizes offered " + sizes + "; 50 → " + at50 + " row(s); 25 → " + at25 + " row(s)",
                at50 == Math.min(50, data.size()) && at25 == Math.min(25, data.size()));
    }

    private void sortingChecks(Grid g, int rows) {
        if (rows < 2) {
            step("ED-16 Sorting", "Click column headers", "Rows re-ordered", "Fewer than 2 rows — not exercisable", "SKIPPED");
            return;
        }
        ed.setPageSize(g, 125);
        // date column must sort chronologically
        ed.clickHeader(g, "PO Date");
        List<String> asc = ed.visibleValues(g, "PODate");
        String dirAsc = ed.sortDirection(g, "PODate");
        ed.clickHeader(g, "PO Date");
        List<String> desc = ed.visibleValues(g, "PODate");
        ed.clearSort(g);
        String dateAscBreak = firstBreak(asc, true), dateDescBreak = firstBreak(desc, false);
        // amount column must sort numerically
        ed.clickHeader(g, "Total Amount");
        List<String> amtAsc = ed.visibleValues(g, "NetAmount");
        ed.clearSort(g);
        String amtBreak = firstNumBreak(amtAsc);
        ed.setPageSize(g, 25);
        boolean dateOk = dateAscBreak.isEmpty() && dateDescBreak.isEmpty();
        ed.scrollTo(g);
        step("ED-16 Sorting (PO Date, Total Amount)", "Click PO Date (ascending, then descending) and Total Amount (ascending)",
                "PO Date sorts chronologically both ways; Total Amount sorts numerically",
                "PO Date (" + dirAsc + "): " + (dateOk ? "chronological" : "NOT chronological — sorted as text: "
                        + (dateAscBreak.isEmpty() ? "" : "ascending " + dateAscBreak) + (dateDescBreak.isEmpty() ? "" : "; descending " + dateDescBreak)
                        + " (first 6 ascending: " + head(asc, 6) + ")")
                        + "; Total Amount: " + (amtBreak.isEmpty() ? "numeric order" : "NOT numeric — " + amtBreak),
                dateOk && amtBreak.isEmpty() ? "PASS" : "FAIL");
        if (!dateOk) defect("ED-16", "Date columns sort as text, not by date (e.g. 05/02/2026 is placed before 11/12/2025 when sorting PO Date ascending)");
        if (!amtBreak.isEmpty()) defect("ED-16", "Total Amount does not sort numerically — " + amtBreak);
    }

    // =================================================================================================================
    // E. View and report
    // =================================================================================================================

    private void viewAndReport() {
        List<String> rowActs = ed.rowActions();
        List<String> pageActs = ed.pageActions("view|detail|print|report|preview");
        step("ED-20 View / Details / Print / Report options", "Look for View, Details, Print or Report actions in the grids and on the screen",
                "Open the details or report preview when the screen offers one",
                rowActs.isEmpty() && pageActs.isEmpty() ? "The screen offers no View / Details / Print / Report action — not applicable"
                        : "Found: " + rowActs + " " + pageActs + " — review manually",
                rowActs.isEmpty() && pageActs.isEmpty() ? "SKIPPED" : "MANUAL");
        boolean menu = isTrue(ed.gridInfo(Grid.PURCHASE_COMPLETE).get("gridMenu"));
        step("ED-21 Grid export (CSV / PDF)", "Grid menu offers CSV / PDF export",
                "Export produces the file — MANUAL VERIFICATION REQUIRED (downloading a file is not done by this read-only test)",
                menu ? "Grid menu button present on the grids; export not clicked" : "No grid menu button found", "MANUAL");
    }

    // =================================================================================================================
    // F. Validation of criteria (empty / invalid / reversed)
    // =================================================================================================================

    private void criteriaValidation() {
        // ED-22 empty search (From Date cleared)
        ed.closeToasts();
        ed.typeDate(SEL_FROM(), "");
        appErrors.clear();
        int c0 = dashboardCalls.size();
        installAlertSpy();
        ed.search();
        List<String> alerts = spiedAlerts();
        int sent = dashboardCalls.size() - c0;
        String firstBody = sent > 0 ? bodyOf(dashboardCalls.get(c0)) : "";
        boolean validated = sent == 0 && !alerts.isEmpty();
        check("ED-22 Empty search (From Date blank)", "Clear From Date and click Search",
                "The search is stopped with a message asking for the From Date; no API call is made",
                validated ? "Stopped: " + alerts : (sent + " API call(s) sent anyway" + (firstBody.isEmpty() ? "" : " (e.g. " + firstBody + ")")
                        + (alerts.isEmpty() ? ", no message shown" : ", messages " + alerts)), validated);
        if (!validated && sent > 0) defect("ED-22", "Search runs with an empty From Date (no validation) and sends FromDate \"\" to the API");

        // ED-23 invalid date
        ed.closeToasts();
        installAlertSpy();
        ed.typeDate(SEL_FROM(), "31/02/2026");
        List<String> onChange = new ArrayList<>(ed.toasts());
        onChange.addAll(spiedAlerts());
        c0 = dashboardCalls.size();
        ed.search();
        int sentInvalid = dashboardCalls.size() - c0;
        List<String> after = spiedAlerts();
        boolean invalidOk = !onChange.isEmpty() || (sentInvalid == 0 && !after.isEmpty());
        check("ED-23 Invalid date (31/02/2026)", "Type an impossible From Date and click Search",
                "The date is rejected with a message; no search is run with it",
                "messages on entry " + onChange + ", on Search " + after + ", API calls sent " + sentInvalid, invalidOk);

        // ED-24a From Date moved past To Date (To set first) — confirmed live: the screen only checks the range when To Date changes
        ed.closeToasts();
        ed.typeDate(SEL_TO(), today.minusDays(5).format(DMY));
        installAlertSpy();
        ed.typeDate(SEL_FROM(), today.format(DMY));
        List<String> revA = new ArrayList<>(ed.toasts());
        revA.addAll(spiedAlerts());
        boolean warnedA = revA.stream().anyMatch(m -> m.toLowerCase().contains("to date"));
        check("ED-24a From Date later than To Date (From changed last)", "Set To = today-5, then From = today",
                "A warning that To Date must not be before From Date is shown",
                warnedA ? "Warned: " + revA : "NO warning — the reversed range is accepted (From " + ed.fromDate() + " > To " + ed.toDate() + ")", warnedA);
        if (!warnedA) defect("ED-24a", "The date-range check only runs when To Date changes: setting From Date after To Date is accepted without any warning");

        // ED-24b To Date moved before From Date (To changed last) + where the warning is placed
        ed.closeToasts();
        ed.typeDate(SEL_TO(), today.format(DMY));
        ed.typeDate(SEL_FROM(), today.format(DMY));
        ed.closeToasts();
        installAlertSpy();
        ed.typeDate(SEL_TO(), today.minusDays(5).format(DMY));
        List<String> revB = new ArrayList<>(ed.toasts());
        revB.addAll(spiedAlerts());
        String cover = ed.searchButtonCoveredBy();
        String toAfter = ed.toDate();
        boolean warnedB = revB.stream().anyMatch(m -> m.toLowerCase().contains("to date"));
        check("ED-24b To Date earlier than From Date (To changed last)", "Set From = today, then To = today-5",
                "A warning is shown, and it does not cover the Search button",
                "messages " + revB + "; To Date now " + toAfter + (cover.isEmpty() ? "; Search button not covered" : "; Search button COVERED by: " + cover),
                warnedB && cover.isEmpty());
        if (warnedB && !cover.isEmpty()) defect("ED-24b", "The date-range warning toast is placed over the Search button and blocks it until the toast is closed");
        // restore valid criteria
        ed.closeToasts();
        ed.typeDate(SEL_TO(), today.format(DMY));
        ed.typeDate(SEL_FROM(), System.getProperty("ed.fromDate", "01/01/2024"));
        ed.closeToasts();
    }

    // =================================================================================================================
    // G. Errors
    // =================================================================================================================

    private void errorChecks() {
        check("ED-25 Browser console errors", "Console errors and uncaught page errors while the Enquiry Dashboard was under test",
                "No console errors", consoleErrors.isEmpty() ? "None" : consoleErrors.size() + ": " + head(consoleErrors, 8), consoleErrors.isEmpty());
        check("ED-26 Failed API / network requests", "HTTP 4xx / 5xx answers and network failures of /API/ calls (the guard's own aborts excluded)",
                "No failed API call", apiFailures.isEmpty() ? "None" : apiFailures.size() + ": " + head(apiFailures, 8), apiFailures.isEmpty());
        check("ED-27 Application error messages", "Error-type alerts raised by the screen (e.g. 'Error in getting record')",
                "No application error message", appErrors.isEmpty() ? "None" : head(appErrors, 8), appErrors.isEmpty());
        check("ED-28 Read-only guard", "Write-type API calls stopped by the guard", "The screen issues no write call",
                blockedWrites.isEmpty() ? "None attempted" : blockedWrites.size() + " blocked: " + head(blockedWrites, 8), blockedWrites.isEmpty());
    }

    // =================================================================================================================
    // helpers
    // =================================================================================================================

    private interface Section { void run(); }

    private void section(String name, Section s) {
        System.out.println("[EnquiryDashboard] --- " + name + " ---");
        try { s.run(); }
        catch (Throwable e) {
            step(page, name + " — unexpected error", "Run the " + name + " checks", "Checks complete", "ERROR: " + String.valueOf(e.getMessage()).split("\\R")[0], "FAIL");
        }
    }

    private void check(String name, String desc, String expected, String actual, boolean ok) {
        step(page, name, desc, expected, actual, ok ? "PASS" : "FAIL");
    }

    private void defect(String id, String text) {
        String d = id + " " + text;
        if (!defects.contains(d)) defects.add(d);
    }

    private static String SEL_FROM() { return com.kpj.pages.Inventory_page.Purchase_page.EnquiryDashboard.SEL_FROM; }
    private static String SEL_TO() { return com.kpj.pages.Inventory_page.Purchase_page.EnquiryDashboard.SEL_TO; }

    private static Map<Grid, List<String>> expectedHeaders() {
        Map<Grid, List<String>> m = new EnumMap<>(Grid.class);
        List<String> enq = List.of("Enquiry No.", "Enquiry Date", "Supplier Name", "Store Name");
        List<String> quo = List.of("Quotation No.", "Quotation Date", "Total Amount");
        List<String> po = List.of("PO Number", "PO Date", "Supplier Name", "Total Amount");
        m.put(Grid.ENQUIRY_COMPLETE, enq); m.put(Grid.ENQUIRY_PENDING, enq);
        m.put(Grid.QUOTATION_COMPLETE, quo); m.put(Grid.QUOTATION_PENDING, quo);
        m.put(Grid.PURCHASE_COMPLETE, po); m.put(Grid.PURCHASE_PENDING, po);
        return m;
    }

    /** Compare the Complete tiles with their grids (and the Pending ones too when {@code pending}). */
    private String tileGridConsistency(Map<String, Integer> t, boolean pending) {
        List<String> diff = new ArrayList<>(), ok = new ArrayList<>();
        for (Grid g : Grid.values()) {
            if (!pending && g.tile.startsWith("incomplete")) continue;
            int tile = t.getOrDefault(g.tile, -1), rows = intv(ed.gridInfo(g).get("total"));
            if (tile != rows) diff.add(g.title + " tile " + tile + " ≠ grid " + rows); else ok.add(g.title + " " + rows);
        }
        return diff.isEmpty() ? "counts match (" + String.join(", ", ok) + ")" : "MISMATCH: " + String.join("; ", diff);
    }

    private List<String> datesOutside(Grid g, LocalDate from, LocalDate to) {
        List<String> out = new ArrayList<>();
        if (from == null) return out;
        for (Map<String, Object> d : ed.gridData(g, g.keyField, g.dateField)) {
            LocalDate x = parse(s(d.get(g.dateField)));
            if (x == null || x.isBefore(from) || x.isAfter(to)) out.add(s(d.get(g.keyField)) + " " + s(d.get(g.dateField)));
        }
        return out;
    }

    private String storeIdNote(List<String> calls) {
        for (String c : calls) if (c.contains("\"StoreID\":\"number:")) return "; (info) StoreID is sent as the Angular option value \"number:NN\" instead of a number";
        return "";
    }

    private List<String> describeCalls(int from) {
        List<String> out = new ArrayList<>();
        for (Response r : new ArrayList<>(dashboardCalls.subList(Math.min(from, dashboardCalls.size()), dashboardCalls.size()))) {
            String flag = bodyOf(r).replaceAll(".*\"ExecFlag\"\\s*:\\s*\"([^\"]+)\".*", "$1");
            out.add(flag + " HTTP " + r.status() + bodyStoreId(r));
        }
        return out;
    }

    private String bodyStoreId(Response r) {
        String b = bodyOf(r);
        return b.contains("\"StoreID\":\"number:") ? " [StoreID \"number:…\"]" : "";
    }

    private static String bodyOf(Response r) {
        try { return String.valueOf(r.request().postData()); } catch (Exception e) { return ""; }
    }

    private void installAlertSpy() {
        page.evaluate("() => { window.__edAlerts=[]; const o=window.__edOrigJAlert||window.JAlert; window.__edOrigJAlert=o;"
                + " window.JAlert=function(m,t){ window.__edAlerts.push((t||'')+': '+m); try{ return o.apply(this,arguments); }catch(e){} }; }");
    }

    @SuppressWarnings("unchecked")
    private List<String> spiedAlerts() {
        Object r = page.evaluate("() => window.__edAlerts || []");
        List<String> l = r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
        for (String a : l) if (a.toLowerCase().startsWith("error")) appErrors.add(a);
        return l;
    }

    private void attachMonitors() {
        page.onConsoleMessage(m -> {
            if (!"error".equals(m.type())) return;
            String txt = m.text();
            if (txt.contains("net::ERR_FAILED") && blockedWrites.stream().anyMatch(b -> txt.contains(b.split(" ")[1]))) return;   // the guard's own abort
            consoleErrors.add(route() + " " + trim(txt, 200));
        });
        page.onPageError(e -> consoleErrors.add(route() + " page error: " + trim(String.valueOf(e), 200)));
        page.onResponse(r -> {
            String u = r.url();
            if (!API.matcher(u).find()) return;
            if (u.toLowerCase().contains("/api/enquirydashboard/")) dashboardCalls.add(r);
            if (r.status() >= 400) apiFailures.add(route() + " " + r.request().method() + " " + path(u) + " → HTTP " + r.status());
        });
        page.onRequestFailed(q -> {
            if (!API.matcher(q.url()).find()) return;
            String f = String.valueOf(q.failure());
            boolean ours = blockedWrites.stream().anyMatch(b -> b.contains(path(q.url())));
            if (!ours) apiFailures.add(route() + " " + q.method() + " " + path(q.url()) + " → " + f);
        });
    }

    /** Abort write-type /api/ calls (non-GET whose last segment is not a read verb) for the whole test. */
    private void installReadOnlyGuard() {
        page.route(API_ROUTE, route -> {
            String m = route.request().method(), u = route.request().url();
            if (!"GET".equalsIgnoreCase(m) && !"OPTIONS".equalsIgnoreCase(m) && !READ_CALL.matcher(path(u)).find()) {
                blockedWrites.add(m + " " + path(u));
                System.out.println("[EnquiryDashboard] read-only guard blocked " + m + " " + path(u));
                route.abort();
            } else route.resume();
        });
    }

    private boolean credentialsFromEnvironment() {
        boolean u = present("devhis.user", "DEVHIS_USER"), p = present("devhis.pass", "DEVHIS_PASS");
        if (u && p) return true;
        String miss = ((u ? "" : "DEVHIS_USER ") + (p ? "" : "DEVHIS_PASS")).trim();
        step("ED-00 Credentials from environment", "Read the test account from DEVHIS_USER / DEVHIS_PASS", "Both variables set",
                "Missing: " + miss + " — set them and re-run. Login was NOT attempted.", "FAIL");
        addSummary("Result", "NOT RUN — credentials not supplied through the environment (" + miss + ")");
        return false;
    }

    private static boolean present(String prop, String env) {
        String a = System.getProperty(prop), b = System.getenv(env);
        return (a != null && !a.isBlank()) || (b != null && !b.isBlank());
    }

    private void writeSummary() {
        addSummary("Application URL", BASE + "/" + com.kpj.pages.Inventory_page.Purchase_page.EnquiryDashboard.ROUTE);
        addSummary("Run date", today.toString());
        addSummary("Store used for data checks", store1 == null ? "(not reached)" : store1);
        addSummary("Application defects found", defects.isEmpty() ? "None" : defects.size() + "");
        int i = 1;
        for (String d : defects) addSummary("Defect " + (i++), d);
        addSummary("Console errors", consoleErrors.isEmpty() ? "None" : consoleErrors.size() + " — " + head(consoleErrors, 6));
        addSummary("API failures", apiFailures.isEmpty() ? "None" : apiFailures.size() + " — " + head(apiFailures, 6));
        addSummary("Write calls blocked (read-only guard)", blockedWrites.isEmpty() ? "None" : head(blockedWrites, 6));
        addSummary("Screenshots", "Embedded in this report for every row; FAIL rows also write test-output/failures/Inventory_Purchase_EnquiryDashboard_<row>.txt");
    }

    private String route() {
        try { String u = page.url(); int i = u.indexOf('#'); return "[" + (i >= 0 ? u.substring(i) : path(u)) + "]"; } catch (Exception e) { return "[?]"; }
    }

    private static LocalDate parse(String s) {
        try { return LocalDate.parse(s.trim(), DMY); } catch (Exception e) { return null; }
    }

    /** First place where the list breaks chronological order ("" when ordered). */
    private static String firstBreak(List<String> dates, boolean ascending) {
        for (int i = 1; i < dates.size(); i++) {
            LocalDate a = parse(dates.get(i - 1)), b = parse(dates.get(i));
            if (a == null || b == null) continue;
            if (ascending ? b.isBefore(a) : b.isAfter(a)) return dates.get(i - 1) + " then " + dates.get(i);
        }
        return "";
    }

    private static String firstNumBreak(List<String> nums) {
        for (int i = 1; i < nums.size(); i++) {
            try { if (Double.parseDouble(nums.get(i)) < Double.parseDouble(nums.get(i - 1))) return nums.get(i - 1) + " then " + nums.get(i); }
            catch (Exception ignore) { }
        }
        return "";
    }

    private static String notes(List<String> n) { return n.isEmpty() ? "" : " [" + String.join("; ", n) + "]"; }
    private static String s(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static int intv(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }
    private static boolean isTrue(Object o) { return Boolean.TRUE.equals(o); }
    private static String yn(Object o) { return isTrue(o) ? "yes" : "NO"; }
    private static String trim(String s, int n) { s = s == null ? "" : s.replaceAll("\\s+", " "); return s.length() > n ? s.substring(0, n) + "…" : s; }
    private static String path(String url) { return url.replaceAll("^https?://[^/]+", "").replaceAll("[?#].*$", ""); }
    private static String head(Collection<?> l, int n) {
        List<String> x = new ArrayList<>(); for (Object o : l) x.add(String.valueOf(o));
        return x.size() <= n ? String.join("; ", x) : String.join("; ", x.subList(0, n)) + "; … (+" + (x.size() - n) + " more)";
    }
}
