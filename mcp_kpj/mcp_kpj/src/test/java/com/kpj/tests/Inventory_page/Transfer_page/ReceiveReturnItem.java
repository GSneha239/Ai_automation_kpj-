package com.kpj.tests.Inventory_page.Transfer_page;

import com.kpj.pages.Inventory_page.InventoryScreen;
import com.kpj.pages.Inventory_page.InventoryScreen.Call;
import com.kpj.pages.Inventory_page.InventoryScreen.PrintResult;
import com.kpj.tests.Inventory_page.InventoryReadOnlyCases;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.kpj.pages.Inventory_page.Transfer_page.ReceiveReturnItem.*;

/**
 * Inventory &gt; <b>Receive Return Item</b> — READ-ONLY functional + defect-verification cases RRI-01 … RRI-17.
 *
 * <p>Report {@code test-output/reports/Inventory_ReceiveReturnItem.html}, summary {@code Inventory_ReceiveReturnItem_summary.txt},
 * log {@code test-output/logs/Inventory_ReceiveReturnItem.log}, FAIL/BLOCKED PNGs in {@code test-output/screenshots/ReceiveReturnItem/}.</p>
 *
 * <p>Expected results describe CORRECT behaviour: a defect makes the case FAIL (marked DEFECT); a case whose data does not exist is BLOCKED.
 * Nothing is received or saved: Save is clicked only in RRI-14 on a form verified to be empty, and the write guard aborts any save attempt.</p>
 *
 * <p>Run only this class: {@code run-receivereturnitem.bat}, or
 * {@code mvn test "-Dtest=com.kpj.tests.Inventory_page.Transfer_page.ReceiveReturnItem" -Ddevhis.postrun=off}.</p>
 */
public class ReceiveReturnItem extends InventoryReadOnlyCases {

    public ReceiveReturnItem() {
        super("Inventory_ReceiveReturnItem", "Inventory - Receive Return Item", "Inventory > Receive Return Item (#/ReceiveReturnItemList, #/add-ReceiveReturnItem)",
                "ReceiveReturnItem");
    }

    public static void main(String[] args) {
        ReceiveReturnItem t = new ReceiveReturnItem();
        try { t.run(); } finally { t.stop(); }
    }

    private com.kpj.pages.Inventory_page.Transfer_page.ReceiveReturnItem rr;

    @Override protected InventoryScreen screen() { return rr; }
    @Override protected String idPrefix() { return "RRI"; }

    // facts gathered by earlier cases and used by later ones
    private String toStore = "";
    private List<Map<String, String>> wide = new ArrayList<>();
    private List<Map<String, String>> inTransit = new ArrayList<>();

    @Override
    protected void runCases() {
        rr = new com.kpj.pages.Inventory_page.Transfer_page.ReceiveReturnItem(page);
        addSummary("Search window used to find records", wideFrom + " - " + today);

        // ---------------------------------------------------------------- list screen
        tc("RRI-01", "Verify page navigation and loading",
                "1. Open Inventory in the sidebar. 2. Click Receive Return Item. 3. Check the title, the filter controls, the buttons, both grids and the load APIs.",
                "Receive Return Item (state ReceiveReturnItemList) opens from the Inventory menu; title \"Receive Return Item\"; From Store, To Store, Status,"
                        + " From/To Date, Search, Print, Print Item and New are shown; the return grid has columns " + MASTER_HEADERS + " and the item grid "
                        + DETAIL_HEADERS + "; every load API answers HTTP 2xx.",
                () -> {
                    int a0 = api.size();
                    String why = rr.open();
                    if (!why.isEmpty()) {
                        boolean rec = rr.goState(LIST_STATE);
                        return defect("Menu navigation failed: " + why + (rec ? " (opened via the ui-router state only so the other cases can run)" : ""));
                    }
                    Map<String, String> btn = rr.buttons();
                    List<String> missing = new ArrayList<>();
                    for (String b : Arrays.asList(BTN_SEARCH, BTN_PRINT, BTN_PRINT_ITEM, BTN_NEW)) if (!btn.containsKey(b)) missing.add("button " + b);
                    for (String m : Arrays.asList(FROM_STORE, TO_STORE, STATUS, FROM_DATE, TO_DATE))
                        if (page.locator("[ng-model='" + m + "']").count() == 0) missing.add("control " + m);
                    List<String> mh = rr.headers(MASTER_GRID), dh = rr.headers(DETAIL_GRID);
                    List<String> bad = failedApisSince(a0);
                    boolean ok = LIST_TITLE.equals(page.title().trim()) && missing.isEmpty() && mh.equals(MASTER_HEADERS) && dh.equals(DETAIL_HEADERS) && bad.isEmpty();
                    return verdict(ok, "Opened " + rr.route() + " (state " + rr.currentState() + "), title \"" + page.title() + "\"; buttons " + btn.keySet()
                            + (missing.isEmpty() ? "" : "; MISSING " + missing) + "; return grid " + mh + "; item grid " + dh
                            + "; load APIs " + apisSince(a0) + (bad.isEmpty() ? "" : "; FAILED " + bad));
                });

        tc("RRI-02", "Verify default search",
                "1. Keep the default filters (To Store pre-selected, From/To Date = today, Status --Select--). 2. Click Search.",
                "POST " + FETCH_API + " carries today's dates, answers HTTP 200, and the return grid shows exactly the records returned (an empty result shows a"
                        + " no-records message).",
                () -> {
                    toStore = rr.chosen(TO_STORE);
                    String fd = rr.model(FROM_DATE), td = rr.model(TO_DATE);
                    rr.clearMessages();
                    Call c = rr.search();
                    int grid = rr.gridRows(MASTER_GRID);
                    List<String> msgs = rr.messages(c.rows == 0 ? 2000 : 300);
                    if (!c.fired()) return defect("Search sent no request: " + c.error);
                    boolean datesSent = c.request.contains(today);
                    boolean ok = c.status == 200 && datesSent && (c.rows < 0 || grid == c.rows) && (grid > 0 || !msgs.isEmpty());
                    return verdict(ok, "Defaults: To Store \"" + toStore + "\", From " + fd + ", To " + td + "; request " + oneLine(c.request, 300) + "; " + c
                            + "; grid rows " + grid + (msgs.isEmpty() ? "" : "; messages " + msgs) + (datesSent ? "" : "; today's date was NOT sent"));
                });

        tc("RRI-03", "Verify search with a wider date range",
                "1. From Date = " + wideFrom + ", To Date = today. 2. Click Search.",
                "HTTP 200; the grid lists the returns of the period; every Return Date lies within the range and every row's To Store is the selected To Store.",
                () -> {
                    rr.dates(wideFrom, today);
                    Call c = rr.search();
                    if (!c.fired() || c.status != 200) return defect("Search failed: " + c);
                    wide = rr.gridData(MASTER_GRID);
                    if (wide.isEmpty()) return blocked("No Receive Return Item records exist for To Store \"" + toStore + "\" between " + wideFrom + " and " + today
                            + " - the data-dependent cases cannot be verified. " + c);
                    LocalDate f = LocalDate.parse(wideFrom, DMY), t = LocalDate.parse(today, DMY);
                    List<String> outOfRange = new ArrayList<>(), otherStore = new ArrayList<>(), unparsed = new ArrayList<>();
                    for (Map<String, String> r : wide) {
                        LocalDate d = parseAnyDate(r.get(C_DATE));
                        if (d == null) unparsed.add(r.get(C_NO) + "=" + r.get(C_DATE));
                        else if (d.isBefore(f) || d.isAfter(t)) outOfRange.add(r.get(C_NO) + " " + d);
                        if (!toStore.isEmpty() && !toStore.equalsIgnoreCase(String.valueOf(r.get(C_TO)).trim())) otherStore.add(r.get(C_NO) + " -> " + r.get(C_TO));
                    }
                    boolean ok = outOfRange.isEmpty() && otherStore.isEmpty() && (c.rows < 0 || c.rows == wide.size());
                    return verdict(ok, c + "; " + wide.size() + " returns: " + head(numbers(wide), 12)
                            + (outOfRange.isEmpty() ? "" : "; OUTSIDE the range " + outOfRange) + (otherStore.isEmpty() ? "" : "; OTHER To Store " + otherStore)
                            + (unparsed.isEmpty() ? "" : "; dates not parsed " + head(unparsed, 5)));
                });

        tc("RRI-04", "Verify From Store filter",
                "1. Keep the wide date range. 2. Choose the From Store that has the most returns. 3. Click Search. 4. Put From Store back to --Select--.",
                "Only returns from the chosen From Store are listed, and all of that store's returns from the unfiltered result are listed.",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RRI-03), so the filter cannot be verified.");
                    String from = mostFrequent(wide, C_FROM);
                    String err = rr.choose(FROM_STORE, from);
                    if (!err.isEmpty()) return defect("From Store \"" + from + "\" (seen in the results) could not be chosen: " + err);
                    Call c = rr.search();
                    List<Map<String, String>> got = rr.gridData(MASTER_GRID);
                    rr.reset(FROM_STORE);
                    Set<String> expected = wide.stream().filter(r -> from.equals(r.get(C_FROM))).map(r -> r.get(C_NO)).collect(Collectors.toCollection(TreeSet::new));
                    Set<String> actual = new TreeSet<>(numbers(got));
                    List<String> foreign = got.stream().filter(r -> !from.equals(r.get(C_FROM))).map(r -> r.get(C_NO) + " from " + r.get(C_FROM)).collect(Collectors.toList());
                    boolean ok = c.status == 200 && foreign.isEmpty() && actual.equals(expected);
                    return verdict(ok, "From Store \"" + from + "\": " + c + "; listed " + actual + "; expected " + expected
                            + (foreign.isEmpty() ? "" : "; OTHER stores listed " + foreign));
                });

        tc("RRI-05", "Verify Status filter",
                "1. Keep the wide date range. 2. Status = " + STATUS_IN_TRANSIT + ", Search. 3. Status = " + STATUS_DELIVERED + ", Search. 4. Status back to --Select--.",
                "Each status lists a subset of the unfiltered result; the two subsets do not overlap and together equal the unfiltered result.",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RRI-03), so the filter cannot be verified.");
                    Map<String, List<Map<String, String>>> by = new LinkedHashMap<>();
                    for (String s : Arrays.asList(STATUS_IN_TRANSIT, STATUS_DELIVERED)) {
                        String err = rr.choose(STATUS, s);
                        if (!err.isEmpty()) { rr.reset(STATUS); return defect("Status \"" + s + "\" could not be chosen: " + err); }
                        Call c = rr.search();
                        if (c.status != 200) { rr.reset(STATUS); return defect("Search with Status \"" + s + "\" failed: " + c); }
                        by.put(s, rr.gridData(MASTER_GRID));
                    }
                    rr.reset(STATUS);
                    inTransit = by.get(STATUS_IN_TRANSIT);
                    Set<String> a = new TreeSet<>(numbers(by.get(STATUS_IN_TRANSIT))), d = new TreeSet<>(numbers(by.get(STATUS_DELIVERED)));
                    Set<String> overlap = new TreeSet<>(a); overlap.retainAll(d);
                    Set<String> union = new TreeSet<>(a); union.addAll(d);
                    Set<String> all = new TreeSet<>(numbers(wide));
                    boolean ok = overlap.isEmpty() && union.equals(all);
                    return verdict(ok, STATUS_IN_TRANSIT + " " + a + "; " + STATUS_DELIVERED + " " + d + "; unfiltered " + all.size()
                            + (overlap.isEmpty() ? "" : "; IN BOTH " + overlap) + (union.equals(all) ? "" : "; missing from the status results " + minus(all, union)));
                });

        tc("RRI-10", "Verify Print without selecting a record",
                "A. Re-open the screen, search the wide range, click Print without clicking any row. B. Click a return, then search again"
                        + " (no row is selected any more), check the item grid, click Print.",
                "A and B: no report opens; a message asks the user to select a record. After the new search in B the item grid is empty (no stale items of the"
                        + " previously clicked return).",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RRI-03), so Print cannot be exercised.");
                    String r = rr.reopen();
                    if (!r.isEmpty()) return defect("Could not re-open the screen for a clean state: " + r);
                    rr.dates(wideFrom, today); rr.search();
                    PrintResult pa = rr.print(BTN_PRINT, BASE);
                    rr.dismissBoxes();
                    String first = wide.get(0).get(C_NO);
                    rr.selectReturn(first);
                    rr.search();
                    int stale = rr.gridRows(DETAIL_GRID), hi = rr.highlightedRows(MASTER_GRID);
                    PrintResult pb = rr.print(BTN_PRINT, BASE);
                    rr.dismissBoxes();
                    boolean okA = !pa.opened() && !pa.messages.isEmpty(), okB = !pb.opened() && !pb.messages.isEmpty() && stale <= 0;
                    return verdict(okA && okB, "A (fresh screen): " + pa + ". B (after clicking " + first + " and searching again; highlighted rows " + hi
                            + ", item grid rows " + stale + (stale > 0 ? " = STALE items of " + first : "") + "): " + pb);
                });

        tc("RRI-06", "Verify selecting a return record",
                "1. Search the wide range. 2. Click the first return in the grid.",
                "The click loads that return's items (POST " + FETCH_API + " HTTP 200) and the item grid fills.",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RRI-03) to select.");
                    ensureWide();
                    String no = wide.get(0).get(C_NO);
                    Call c = rr.selectReturn(no);
                    int items = rr.gridRows(DETAIL_GRID);
                    if (c.error.startsWith("row")) return defect("The return row could not be clicked: " + c.error);
                    // a click on the row that is already current may not re-fetch; then the items shown are still that same return's
                    boolean ok = items > 0 && (c.status == 200 || !c.fired());
                    return verdict(ok, "Clicked " + no + ": " + (c.fired() ? c.toString() : "no new request (row already current)") + "; item grid rows " + items
                            + "; rows shown as selected " + rr.highlightedRows(MASTER_GRID));
                });

        tc("RRI-07", "Verify the item grid loads correctly",
                "1. With a return selected (RRI-06), read the item grid's columns and rows.",
                "Columns " + DETAIL_HEADERS + "; the number of item lines equals the return's Total Items; every line has Item Code, Item Name and Batch Code.",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RRI-03) to select.");
                    Map<String, String> head = wide.get(0);
                    List<String> h = rr.headers(DETAIL_GRID);
                    List<Map<String, String>> items = rr.gridData(DETAIL_GRID);
                    List<String> incomplete = items.stream().filter(i -> blank(i.get(D_CODE)) || blank(i.get(D_NAME)) || blank(i.get(D_BATCH)))
                            .map(i -> String.valueOf(i.get(D_CODE))).collect(Collectors.toList());
                    double expectedLines = num(head.get(C_ITEMS));
                    boolean ok = h.equals(DETAIL_HEADERS) && !items.isEmpty() && same(items.size(), expectedLines) && incomplete.isEmpty();
                    return verdict(ok, head.get(C_NO) + ": columns " + h + "; " + items.size() + " item line(s) vs Total Items " + head.get(C_ITEMS)
                            + "; items " + items.stream().map(i -> i.get(D_CODE) + " " + i.get(D_NAME)).collect(Collectors.toList())
                            + (incomplete.isEmpty() ? "" : "; lines missing code/name/batch " + incomplete));
                });

        tc("RRI-08", "Verify item quantities and amount totals",
                "For every return of the wide search (first page, up to 25): click it and compare its items with the return's header.",
                "Per return: item lines = Total Items; sum of item Total Amount = header Total Amount; sum of item Total Selling Price = header Total Selling Price;"
                        + " per item: Total Amount = Return Quantity x Unit Purchase Rate, and Received Quantity never exceeds Return Quantity.",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RRI-03) to check.");
                    ensureWide();
                    List<String> problems = new ArrayList<>(), checked = new ArrayList<>();
                    for (Map<String, String> r : wide.subList(0, Math.min(25, wide.size()))) {
                        String no = r.get(C_NO);
                        Call c = rr.selectReturn(no);
                        if (c.status != 200) { problems.add(no + ": items did not load (" + c + ")"); continue; }
                        List<Map<String, String>> items = rr.gridData(DETAIL_GRID);
                        double amt = 0, sell = 0;
                        for (Map<String, String> i : items) {
                            double ret = num(i.get(D_RET_QTY)), rcv = num(i.get(D_RCV_QTY)), rate = num(i.get(D_RATE)), a = num(i.get(D_AMT));
                            amt += a; sell += num(i.get(D_SELL));
                            if (!Double.isNaN(rcv) && !Double.isNaN(ret) && rcv > ret)
                                problems.add(no + " item " + i.get(D_CODE) + ": Received Qty " + i.get(D_RCV_QTY) + " > Return Qty " + i.get(D_RET_QTY) + " (status " + i.get(D_STATUS) + ")");
                            if (!same(a, ret * rate)) problems.add(no + " item " + i.get(D_CODE) + ": Total Amount " + i.get(D_AMT) + " != " + i.get(D_RET_QTY) + " x " + i.get(D_RATE));
                        }
                        if (!same(items.size(), num(r.get(C_ITEMS)))) problems.add(no + ": " + items.size() + " item line(s) but Total Items " + r.get(C_ITEMS));
                        if (!same(amt, num(r.get(C_AMT)))) problems.add(no + ": items add up to " + money(amt) + " but Total Amount is " + r.get(C_AMT));
                        if (!same(sell, num(r.get(C_SELL)))) problems.add(no + ": items' selling price adds up to " + money(sell) + " but Total Selling Price is " + r.get(C_SELL));
                        checked.add(no);
                    }
                    return verdict(problems.isEmpty(), "Checked " + checked.size() + " return(s) " + head(checked, 10) + (problems.isEmpty() ? "; all totals and quantities agree"
                            : "; MISMATCHES: " + String.join("; ", problems)) + (wide.size() > 25 ? "; returns beyond the first 25 not checked" : ""));
                });

        tc("RRI-09", "Verify reversed date range validation",
                "1. From Date = today, To Date = " + earlier + " (From later than To). 2. Click Search. 3. Restore the wide range.",
                "The search is not sent; a message says From Date must not be later than To Date.",
                () -> {
                    rr.dates(today, earlier);
                    rr.clearMessages();
                    Call c = rr.searchExpectingNoRequest(5000);
                    List<String> msgs = rr.messages(1500);
                    rr.dismissBoxes();
                    rr.dates(wideFrom, today);
                    boolean ok = !c.fired() && mentions(msgs, "date");
                    return verdict(ok, "From " + today + " / To " + earlier + ": " + (c.fired() ? "the search WAS sent - " + c + " (request " + oneLine(c.request, 200) + ")" : "no request sent")
                            + "; messages " + msgs);
                });

        tc("RRI-11", "Verify Print with a selected record",
                "1. Search the wide range. 2. Click a return. 3. Click Print (the report URL is captured; it is fetched with GET in the same session).",
                "One report URL carrying the selected return opens and renders (HTTP 200, no server error).",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RRI-03) to print.");
                    ensureWide();
                    String no = wide.get(0).get(C_NO);
                    rr.selectReturn(no);
                    PrintResult p = rr.print(BTN_PRINT, BASE);
                    rr.dismissBoxes();
                    boolean undefinedIds = p.urls.stream().anyMatch(u -> u.contains("undefined") || u.contains("=null"));
                    return verdict(p.allReportsOk() && !undefinedIds, "Selected " + no + "; " + p + (undefinedIds ? "; URL carries undefined/null ids" : ""));
                });

        tc("RRI-12", "Verify Print Item",
                "1. With a return selected, click Print Item (the report URL is captured and fetched with GET).",
                "The item report for the selected return opens and renders (HTTP 200, no server error).",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RRI-03) to print.");
                    ensureWide();
                    String no = wide.get(0).get(C_NO);
                    rr.selectReturn(no);
                    PrintResult p = rr.print(BTN_PRINT_ITEM, BASE);
                    rr.dismissBoxes();
                    boolean undefinedIds = p.urls.stream().anyMatch(u -> u.contains("undefined") || u.contains("=null"));
                    return verdict(p.allReportsOk() && !undefinedIds, "Selected " + no + "; " + p + (undefinedIds ? "; URL carries undefined/null ids" : ""));
                });

        // ---------------------------------------------------------------- add form (nothing is saved)
        tc("RRI-13", "Verify New form navigation",
                "1. Click New. 2. Check the route, the fields, the buttons, the pending-returns grid and the labels.",
                "The add form " + ADD_ROUTE + " opens with Received Date, Received From, Received To, Search, Save, Back and the grid " + ADD_GRID_HEADERS
                        + "; no field label is duplicated.",
                () -> {
                    String why = rr.openAddForm();
                    if (!why.isEmpty()) {
                        boolean rec = rr.goState(ADD_STATE);
                        return defect("New did not open the add form: " + why + (rec ? " (add form opened via its state so the other cases can run)" : ""));
                    }
                    Map<String, String> btn = rr.buttons();
                    List<String> labels = rr.labels(), dup = rr.duplicateLabels();
                    List<String> missing = new ArrayList<>();
                    for (String l : Arrays.asList("Received Date", "Received From", "Received To")) if (labels.stream().noneMatch(x -> x.startsWith(l))) missing.add("label " + l);
                    for (String b : Arrays.asList(BTN_SEARCH, BTN_SAVE, BTN_BACK)) if (!btn.containsKey(b)) missing.add("button " + b);
                    List<String> gh = rr.headers(ADD_GRID);
                    boolean ok = ADD_ROUTE.equals(rr.route()) && missing.isEmpty() && gh.equals(ADD_GRID_HEADERS) && dup.isEmpty();
                    return verdict(ok, "Route " + rr.route() + ", title \"" + page.title() + "\"; buttons " + btn.keySet() + "; grid " + gh
                            + (missing.isEmpty() ? "" : "; MISSING " + missing) + (dup.isEmpty() ? "; no duplicate labels" : "; DUPLICATE labels " + dup));
                });

        tc("RRI-14", "Verify required field validations",
                "1. On the add form, confirm it is incomplete: Received From and Received To empty, no return listed, Total Received Items empty."
                        + " 2. Click Save (any save request is aborted by the read-only guard; window.open is captured).",
                "Save is refused on the client: a validation message names the missing field; no save request is sent; no success message; no print tab opens.",
                () -> {
                    if (!ADD_STATE.equals(rr.currentState()) && !rr.goState(ADD_STATE)) return blocked("The add form could not be opened (see RRI-13).");
                    String from = rr.model(ADD_FROM_STORE), to = rr.model(ADD_TO_STORE), items = rr.fieldValue(ADD_TOTAL_ITEMS);
                    int rows = rr.gridRows(ADD_GRID);
                    boolean incomplete = (from.isEmpty() || from.equals("0")) && (to.isEmpty() || to.equals("0")) && rows <= 0 && (items.isBlank() || items.trim().equals("0"));
                    if (!incomplete) return blocked("The add form is not empty (From \"" + from + "\", To \"" + to + "\", grid rows " + rows + ", items \"" + items
                            + "\") - Save was NOT clicked, to stay read-only.");
                    int w0 = blockedWrites.size();
                    rr.clearMessages();
                    List<String> opened = rr.withOpenCaptured(() -> rr.clickSaveForValidation("Received From/To empty, no return listed, no items"));
                    List<String> msgs = rr.messages(2500);
                    rr.dismissBoxes();
                    List<String> writes = writesSince(w0);
                    boolean ok = writes.isEmpty() && !success(msgs) && opened.isEmpty() && !msgs.isEmpty();
                    return verdict(ok, "Form was incomplete (From/To empty, 0 rows, 0 items). After Save: messages " + msgs
                            + (writes.isEmpty() ? "; no save request" : "; the app SENT a save request (aborted by the guard): " + writes)
                            + (opened.isEmpty() ? "" : "; tried to OPEN " + opened) + (success(msgs) ? "; a SUCCESS message was shown for an empty form" : ""));
                });

        tc("RRI-15", "Verify search using From Store and To Store",
                "1. On the add form choose Received From / Received To of an In-transit return seen on the list (RRI-05). 2. Click Search.",
                "POST " + FETCH_API + " (" + ADD_SEARCH_FLAG + ") carries both store ids, answers HTTP 200, and the grid lists at least that pending return.",
                () -> {
                    if (inTransit.isEmpty()) return blocked("No " + STATUS_IN_TRANSIT + " return exists in the list (RRI-05) - there is nothing pending to receive.");
                    if (!ADD_STATE.equals(rr.currentState()) && !rr.goState(ADD_STATE)) return blocked("The add form could not be opened (see RRI-13).");
                    Map<String, String> r = inTransit.get(0);
                    Call c = rr.addFormSearch(r.get(C_FROM), r.get(C_TO));
                    List<String> got = numbers(rr.gridData(ADD_GRID));
                    boolean idsSent = c.request.contains(ADD_SEARCH_FLAG) && !c.request.matches("(?i).*\"fromstoreid\"\\s*:\\s*(null|0)\\b.*") && !c.request.matches("(?i).*\"tostoreid\"\\s*:\\s*(null|0)\\b.*");
                    boolean ok = c.status == 200 && idsSent && got.contains(r.get(C_NO));
                    return verdict(ok, "From \"" + r.get(C_FROM) + "\" to \"" + r.get(C_TO) + "\": request " + oneLine(c.request, 250) + "; " + c + "; grid lists " + got
                            + (got.contains(r.get(C_NO)) ? "" : "; the In-transit return " + r.get(C_NO) + " is NOT offered for receiving"));
                });

        tc("RRI-16", "Verify pending return records are displayed",
                "For every From Store / To Store pair of the In-transit returns on the list (up to 5 pairs): search on the add form.",
                "Every In-transit return of the list is offered on the add form for its store pair.",
                () -> {
                    if (inTransit.isEmpty()) return blocked("No " + STATUS_IN_TRANSIT + " return exists in the list (RRI-05) - there is nothing pending to receive.");
                    if (!ADD_STATE.equals(rr.currentState()) && !rr.goState(ADD_STATE)) return blocked("The add form could not be opened (see RRI-13).");
                    Map<String, List<String>> pairs = new LinkedHashMap<>();
                    for (Map<String, String> r : inTransit) pairs.computeIfAbsent(r.get(C_FROM) + " -> " + r.get(C_TO), k -> new ArrayList<>()).add(r.get(C_NO));
                    List<String> missing = new ArrayList<>(), report = new ArrayList<>();
                    int n = 0;
                    for (Map.Entry<String, List<String>> e : pairs.entrySet()) {
                        if (n++ >= 5) break;
                        String[] st = e.getKey().split(" -> ", 2);
                        Call c = rr.addFormSearch(st[0], st[1]);
                        List<String> got = numbers(rr.gridData(ADD_GRID));
                        for (String no : e.getValue()) if (!got.contains(no)) missing.add(no + " (" + e.getKey() + ")");
                        report.add(e.getKey() + ": expected " + e.getValue() + ", offered " + got + " [" + c + "]");
                    }
                    return verdict(missing.isEmpty(), String.join("; ", report) + (missing.isEmpty() ? "" : "; NOT OFFERED: " + missing));
                });

        tc("RRI-17", "Verify Back navigation",
                "1. On the add form click Back.",
                "The list screen (state " + LIST_STATE + ") is shown again; no request that changes data is sent.",
                () -> {
                    if (!ADD_STATE.equals(rr.currentState()) && !rr.goState(ADD_STATE)) return blocked("The add form could not be opened (see RRI-13).");
                    int w0 = blockedWrites.size();
                    String why = rr.back();
                    List<String> writes = writesSince(w0);
                    return verdict(why.isEmpty() && writes.isEmpty(), (why.isEmpty() ? "Back returned to " + rr.route() : why)
                            + (writes.isEmpty() ? "" : "; write request attempted " + writes));
                });
    }

    // ======================================================================= page-specific helpers

    /** Brings the wide-range result back into the return grid (filters reset) when another case changed it. */
    private void ensureWide() {
        if (!LIST_STATE.equals(rr.currentState())) rr.reopen();
        if (rr.gridRows(MASTER_GRID) == wide.size() && new HashSet<>(numbers(rr.gridData(MASTER_GRID))).equals(new HashSet<>(numbers(wide)))) return;
        rr.reset(FROM_STORE);
        rr.reset(STATUS);
        rr.dates(wideFrom, today);
        rr.search();
    }

    private static List<String> numbers(List<Map<String, String>> rows) {
        return rows.stream().map(r -> String.valueOf(r.get(C_NO)).trim()).collect(Collectors.toList());
    }

    private static String mostFrequent(List<Map<String, String>> rows, String col) {
        return rows.stream().collect(Collectors.groupingBy(r -> String.valueOf(r.get(col)).trim(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }

    private static Set<String> minus(Set<String> a, Set<String> b) { Set<String> s = new TreeSet<>(a); s.removeAll(b); return s; }

    private static boolean blank(String s) { return s == null || s.isBlank() || "null".equals(s); }
}
