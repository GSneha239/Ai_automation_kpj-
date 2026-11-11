package com.kpj.tests.Inventory_page.Transfer_page;

import com.kpj.pages.Inventory_page.InventoryScreen;
import com.kpj.pages.Inventory_page.InventoryScreen.Call;
import com.kpj.pages.Inventory_page.InventoryScreen.PrintResult;
import com.kpj.tests.Inventory_page.InventoryReadOnlyCases;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.kpj.pages.Inventory_page.Transfer_page.ReceiveIssueItem.*;

/**
 * Inventory &gt; <b>Receive Issue Item</b> — READ-ONLY functional + defect-verification cases RII-01 … RII-16.
 *
 * <p>Report {@code test-output/reports/Inventory_ReceiveIssueItem.html}, summary {@code Inventory_ReceiveIssueItem_summary.txt},
 * log {@code test-output/logs/Inventory_ReceiveIssueItem.log}, FAIL/BLOCKED PNGs in {@code test-output/screenshots/ReceiveIssueItem/}.</p>
 *
 * <p>Expected results describe CORRECT behaviour: a defect makes the case FAIL (marked DEFECT); a case whose data does not exist (no pending
 * transfer, a frozen store) is BLOCKED. Nothing is received: Save is clicked only in RII-12 on a form verified to hold no item, and the write
 * guard aborts any save attempt.</p>
 *
 * <p>Run only this class: {@code run-receiveissueitem.bat}, or
 * {@code mvn test "-Dtest=com.kpj.tests.Inventory_page.Transfer_page.ReceiveIssueItem" -Ddevhis.postrun=off}.</p>
 */
public class ReceiveIssueItem extends InventoryReadOnlyCases {

    public ReceiveIssueItem() {
        super("Inventory_ReceiveIssueItem", "Inventory - Receive Issue Item", "Inventory > Receive Issue Item (#/ReceiveIssueItemList, #/add-ReceiveIssueItem)",
                "ReceiveIssueItem");
    }

    public static void main(String[] args) {
        ReceiveIssueItem t = new ReceiveIssueItem();
        try { t.run(); } finally { t.stop(); }
    }

    private com.kpj.pages.Inventory_page.Transfer_page.ReceiveIssueItem ri;

    @Override protected InventoryScreen screen() { return ri; }
    @Override protected String idPrefix() { return "RII"; }

    private String toStore = "";
    private List<Map<String, String>> wide = new ArrayList<>();

    @Override
    protected void runCases() {
        ri = new com.kpj.pages.Inventory_page.Transfer_page.ReceiveIssueItem(page);
        addSummary("Search window used to find records", wideFrom + " - " + today);

        // ---------------------------------------------------------------- list screen
        tc("RII-01", "Verify page navigation",
                "1. Open Inventory in the sidebar. 2. Click Receive Issue Item (the Inventory entry, not CSSD). 3. Check title, controls, buttons, grids and load APIs.",
                "Receive Issue Item (state " + LIST_STATE + ") opens; title \"" + LIST_TITLE + "\"; Issued From Store, Received To Store, Status, From/To Date, Search,"
                        + " New and Print are shown; grids " + MASTER_HEADERS + " / " + DETAIL_HEADERS + "; load APIs answer HTTP 2xx.",
                () -> {
                    int a0 = api.size();
                    String why = ri.open();
                    if (!why.isEmpty()) {
                        boolean rec = ri.goState(LIST_STATE);
                        return defect("Menu navigation failed: " + why + (rec ? " (opened via the ui-router state only so the other cases can run)" : ""));
                    }
                    Map<String, String> btn = ri.buttons();
                    List<String> missing = new ArrayList<>();
                    for (String b : Arrays.asList(BTN_SEARCH, BTN_NEW, BTN_PRINT)) if (!btn.containsKey(b)) missing.add("button " + b);
                    for (String m : Arrays.asList(FROM_STORE, TO_STORE, STATUS, FROM_DATE, TO_DATE))
                        if (page.locator("[ng-model='" + m + "']").count() == 0) missing.add("control " + m);
                    List<String> mh = ri.headers(MASTER_GRID), dh = ri.headers(DETAIL_GRID), bad = failedApisSince(a0);
                    boolean ok = LIST_TITLE.equals(page.title().trim()) && missing.isEmpty() && mh.equals(MASTER_HEADERS) && dh.equals(DETAIL_HEADERS) && bad.isEmpty();
                    return verdict(ok, "Opened " + ri.route() + " (state " + ri.currentState() + "), title \"" + page.title() + "\"; buttons " + btn.keySet()
                            + (missing.isEmpty() ? "" : "; MISSING " + missing) + "; grids " + mh + " / " + dh + "; load APIs " + apisSince(a0)
                            + (bad.isEmpty() ? "" : "; FAILED " + bad));
                });

        tc("RII-02", "Verify default search",
                "1. Keep the default filters (Received To Store pre-selected, dates = today). 2. Click Search.",
                "POST " + FETCH_API + " (" + SEARCH_FLAG + ") carries today's dates and the To Store, answers HTTP 200; the grid shows exactly the records returned"
                        + " (an empty result shows a no-records message).",
                () -> {
                    toStore = ri.chosen(TO_STORE);
                    ri.clearMessages();
                    Call c = ri.search();
                    int grid = ri.gridRows(MASTER_GRID);
                    List<String> msgs = ri.messages(c.rows == 0 ? 2000 : 300);
                    if (!c.fired()) return defect("Search sent no request: " + c.error);
                    boolean ok = c.status == 200 && c.request.contains(SEARCH_FLAG) && c.request.contains(today) && (c.rows < 0 || grid == c.rows) && (grid > 0 || !msgs.isEmpty());
                    return verdict(ok, "To Store \"" + toStore + "\"; request " + oneLine(c.request, 250) + "; " + c + "; grid rows " + grid + (msgs.isEmpty() ? "" : "; messages " + msgs));
                });

        tc("RII-03", "Verify search with wider date range",
                "1. From Date = " + wideFrom + ", To Date = today. 2. Click Search.",
                "HTTP 200; the grid lists the received transfers of the period; every Receive Datetime lies within the range and every row's To Store is the selected store.",
                () -> {
                    ri.dates(wideFrom, today);
                    Call c = ri.search();
                    if (!c.fired() || c.status != 200) return defect("Search failed: " + c);
                    wide = ri.gridData(MASTER_GRID);
                    if (wide.isEmpty()) return blocked("No Receive Issue Item records exist for \"" + toStore + "\" between " + wideFrom + " and " + today + ". " + c);
                    LocalDate f = LocalDate.parse(wideFrom, DMY), t = LocalDate.parse(today, DMY);
                    List<String> outOfRange = new ArrayList<>(), otherStore = new ArrayList<>();
                    for (Map<String, String> r : wide) {
                        LocalDate d = parseAnyDate(r.get(C_DATE));
                        if (d != null && (d.isBefore(f) || d.isAfter(t))) outOfRange.add(r.get(C_NO) + " " + d);
                        if (!toStore.isEmpty() && !toStore.equalsIgnoreCase(String.valueOf(r.get(C_TO)).trim())) otherStore.add(r.get(C_NO) + " -> " + r.get(C_TO));
                    }
                    boolean ok = outOfRange.isEmpty() && otherStore.isEmpty() && (c.rows < 0 || c.rows == wide.size());
                    return verdict(ok, c + "; " + wide.size() + " transfers: " + head(numbers(wide), 12)
                            + (outOfRange.isEmpty() ? "" : "; OUTSIDE the range " + outOfRange) + (otherStore.isEmpty() ? "" : "; OTHER store " + otherStore));
                });

        tc("RII-04", "Verify Status filter",
                "1. Keep the wide date range. 2. Status = " + STATUS_IN_TRANSIT + ", Search. 3. Status = " + STATUS_DELIVERED + ", Search. 4. Status back to --Select--.",
                "The two status results do not overlap and together equal the unfiltered result.",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RII-03), so the filter cannot be verified.");
                    Map<String, Set<String>> by = new LinkedHashMap<>();
                    for (String s : Arrays.asList(STATUS_IN_TRANSIT, STATUS_DELIVERED)) {
                        String err = ri.choose(STATUS, s);
                        if (!err.isEmpty()) { ri.reset(STATUS); return defect("Status \"" + s + "\" could not be chosen: " + err); }
                        Call c = ri.search();
                        if (c.status != 200) { ri.reset(STATUS); return defect("Search with Status \"" + s + "\" failed: " + c); }
                        by.put(s, new TreeSet<>(numbers(ri.gridData(MASTER_GRID))));
                    }
                    ri.reset(STATUS);
                    Set<String> a = by.get(STATUS_IN_TRANSIT), d = by.get(STATUS_DELIVERED);
                    Set<String> overlap = new TreeSet<>(a); overlap.retainAll(d);
                    Set<String> union = new TreeSet<>(a); union.addAll(d);
                    Set<String> all = new TreeSet<>(numbers(wide));
                    boolean ok = overlap.isEmpty() && union.equals(all);
                    return verdict(ok, STATUS_IN_TRANSIT + " " + a + "; " + STATUS_DELIVERED + " " + d + "; unfiltered " + all.size()
                            + (overlap.isEmpty() ? "" : "; IN BOTH " + overlap) + (union.equals(all) ? "" : "; missing from the status results " + minus(all, union)));
                });

        tc("RII-05", "Verify From Store filter",
                "1. Keep the wide date range. 2. Choose the Issued From Store that has the most transfers. 3. Search. 4. Put it back to --Select--.",
                "Only transfers from the chosen store are listed, and all of that store's transfers from the unfiltered result are listed.",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RII-03), so the filter cannot be verified.");
                    String from = mostFrequent(wide, C_FROM);
                    String err = ri.choose(FROM_STORE, from);
                    if (!err.isEmpty()) return defect("Issued From Store \"" + from + "\" (seen in the results) could not be chosen: " + err);
                    Call c = ri.search();
                    List<Map<String, String>> got = ri.gridData(MASTER_GRID);
                    ri.reset(FROM_STORE);
                    Set<String> expected = wide.stream().filter(r -> from.equals(r.get(C_FROM))).map(r -> r.get(C_NO)).collect(Collectors.toCollection(TreeSet::new));
                    Set<String> actual = new TreeSet<>(numbers(got));
                    List<String> foreign = got.stream().filter(r -> !from.equals(r.get(C_FROM))).map(r -> r.get(C_NO) + " from " + r.get(C_FROM)).collect(Collectors.toList());
                    boolean ok = c.status == 200 && foreign.isEmpty() && actual.equals(expected);
                    return verdict(ok, "From \"" + from + "\": " + c + "; listed " + actual + "; expected " + expected + (foreign.isEmpty() ? "" : "; OTHER stores " + foreign));
                });

        tc("RII-06", "Verify reversed date validation",
                "1. From Date = today, To Date = " + earlier + ". 2. Click Search. 3. Restore the wide range.",
                "The search is not sent; a message says From Date must not be later than To Date.",
                () -> {
                    ri.dates(today, earlier);
                    ri.clearMessages();
                    Call c = ri.searchExpectingNoRequest(5000);
                    List<String> msgs = ri.messages(1500);
                    ri.dismissBoxes();
                    ri.dates(wideFrom, today);
                    boolean ok = !c.fired() && mentions(msgs, "date");
                    return verdict(ok, "From " + today + " / To " + earlier + ": " + (c.fired() ? "the search WAS sent - " + c : "no request sent") + "; messages " + msgs);
                });

        tc("RII-09", "Verify Print without selection",
                "1. Re-open the screen, search the wide range. 2. Click Print without clicking any row.",
                "No report opens; a clear message asks the user to select a transfer first (no placeholder text).",
                () -> {
                    String r = ri.reopen();
                    if (!r.isEmpty()) return defect("Could not re-open the screen for a clean state: " + r);
                    ri.dates(wideFrom, today); ri.search();
                    PrintResult p = ri.print(BTN_PRINT, BASE);
                    ri.dismissBoxes();
                    boolean placeholder = mentions(p.messages, "not found");
                    boolean ok = !p.opened() && !p.messages.isEmpty() && !placeholder && mentions(p.messages, "select");
                    return verdict(ok, p + (placeholder ? "; the message is a placeholder, not an instruction to select a record" : ""));
                });

        tc("RII-07", "Verify selecting a record",
                "1. Search the wide range. 2. Click the first transfer.",
                "The click loads that transfer's items (POST " + FETCH_API + " HTTP 200) and the item grid fills.",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RII-03) to select.");
                    ensureWide();
                    String no = wide.get(0).get(C_NO);
                    Call c = ri.selectTransfer(no);
                    int items = ri.gridRows(DETAIL_GRID);
                    return verdict(c.status == 200 && items > 0, "Clicked " + no + ": " + c + "; item grid rows " + items);
                });

        tc("RII-08", "Verify item details and quantity totals",
                "For every transfer of the wide search (up to 25): click it and compare its items with the header.",
                "Per transfer: header Receive Issued Quantity = sum of item Received Quantity; header Total Amount = sum of item Total Amount; no item receives more"
                        + " than was transferred; every item line has Item Code and Item Name; Received By is a staff user, not a supplier/vendor.",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RII-03) to check.");
                    ensureWide();
                    List<String> problems = new ArrayList<>(), checked = new ArrayList<>();
                    for (Map<String, String> r : wide.subList(0, Math.min(25, wide.size()))) {
                        String no = r.get(C_NO);
                        Call c = ri.selectTransfer(no);
                        if (c.status != 200) { problems.add(no + ": items did not load (" + c + ")"); continue; }
                        List<Map<String, String>> items = ri.gridData(DETAIL_GRID);
                        double qty = 0, amt = 0;
                        for (Map<String, String> i : items) {
                            qty += num(i.get(D_RCV_QTY)); amt += num(i.get(D_AMT));
                            if (num(i.get(D_RCV_QTY)) > num(i.get(D_TRF_QTY))) problems.add(no + " item " + i.get(D_CODE) + ": Received " + i.get(D_RCV_QTY) + " > Transfer " + i.get(D_TRF_QTY));
                            if (blank(i.get(D_CODE)) || blank(i.get(D_NAME))) problems.add(no + ": an item line has no code/name");
                        }
                        if (!same(qty, num(r.get(C_QTY)))) problems.add(no + ": header quantity " + r.get(C_QTY) + " but items received " + money(qty) + " (" + items.size() + " line(s))");
                        if (!same(amt, num(r.get(C_AMT)))) problems.add(no + ": header Total Amount " + r.get(C_AMT) + " but items add up to " + money(amt));
                        if (String.valueOf(r.get(C_BY)).matches("(?i)\\s*(vendor|supplier)\\b.*")) problems.add(no + ": Received By shows \"" + r.get(C_BY) + "\" (a vendor, not a staff user)");
                        checked.add(no);
                    }
                    return verdict(problems.isEmpty(), "Checked " + checked.size() + " transfer(s) " + head(checked, 10)
                            + (problems.isEmpty() ? "; all quantities, totals and Received By agree" : "; MISMATCHES: " + String.join("; ", problems)));
                });

        tc("RII-10", "Verify Print with selection",
                "1. Click a transfer. 2. Click Print (the report URL is captured and fetched with GET in the same session).",
                "The report for the selected transfer opens and renders (HTTP 200, no server error).",
                () -> {
                    if (wide.isEmpty()) return blocked("No records in the wide search (RII-03) to print.");
                    ensureWide();
                    String no = wide.get(0).get(C_NO);
                    ri.selectTransfer(no);
                    PrintResult p = ri.print(BTN_PRINT, BASE);
                    ri.dismissBoxes();
                    boolean undefinedIds = p.urls.stream().anyMatch(u -> u.contains("undefined") || u.contains("=null"));
                    return verdict(p.allReportsOk() && !undefinedIds, "Selected " + no + "; " + p + (undefinedIds ? "; URL carries undefined/null ids" : ""));
                });

        // ---------------------------------------------------------------- add form (nothing is received)
        tc("RII-11", "Verify New form navigation",
                "1. Click New. 2. Check the route, fields, buttons, grid and labels.",
                "The add form " + ADD_ROUTE + " opens with Received Date, Transferred From, Received To, Item Group, Search, Save, Back and the grid "
                        + ADD_GRID_HEADERS + "; no field label is duplicated.",
                () -> {
                    if (!LIST_STATE.equals(ri.currentState())) ri.reopen();
                    String why = ri.openAddForm();
                    if (!why.isEmpty()) {
                        boolean rec = ri.goState(ADD_STATE);
                        return defect("New did not open the add form: " + why + (rec ? " (opened via its state so the other cases can run)" : ""));
                    }
                    Map<String, String> btn = ri.buttons();
                    List<String> labels = ri.labels(), dup = ri.duplicateLabels(), missing = new ArrayList<>();
                    for (String l : Arrays.asList("Received Date", "Transferred From", "Received To", "Item Group")) if (labels.stream().noneMatch(x -> x.startsWith(l))) missing.add("label " + l);
                    for (String b : Arrays.asList(BTN_SEARCH, BTN_SAVE, BTN_BACK)) if (!btn.containsKey(b)) missing.add("button " + b);
                    List<String> gh = ri.headers(ADD_GRID);
                    boolean ok = ADD_ROUTE.equals(ri.route()) && missing.isEmpty() && gh.equals(ADD_GRID_HEADERS) && dup.isEmpty();
                    return verdict(ok, "Route " + ri.route() + "; buttons " + btn.keySet() + "; grid " + gh + (missing.isEmpty() ? "" : "; MISSING " + missing)
                            + (dup.isEmpty() ? "; no duplicate labels" : "; DUPLICATE labels " + dup));
                });

        tc("RII-12", "Verify mandatory field validations",
                "1. Confirm the form holds no transfer and no item. 2. Click Save with Transferred From empty. 3. Choose a Transferred From store (still no item),"
                        + " click Save again. (Any save request is aborted by the read-only guard.)",
                "Step 2: \"Please Select Transferred From\"-type message; step 3: \"Please add at least one item\"-type message; no save request is sent and no"
                        + " success message appears.",
                () -> {
                    if (!ADD_STATE.equals(ri.currentState()) && !ri.goState(ADD_STATE)) return blocked("The add form could not be opened (see RII-11).");
                    int rows = ri.gridRows(ADD_GRID);
                    String from = ri.model(ADD_TRANSFERRED_FROM);
                    if (rows > 0 || !(from.isEmpty() || from.equals("0"))) return blocked("The add form is not empty (rows " + rows + ", Transferred From \"" + from
                            + "\") - Save was NOT clicked, to stay read-only.");
                    int w0 = blockedWrites.size();
                    ri.clearMessages();
                    ri.clickSaveForValidation("no transfer listed, no item, Transferred From empty");
                    List<String> m1 = ri.messages(2500);
                    ri.dismissBoxes();
                    List<String> opts = ri.options(ADD_TRANSFERRED_FROM);
                    String store = opts.stream().filter(o -> !o.equalsIgnoreCase(ri.chosen(ADD_RECEIVED_TO))).findFirst().orElse("");
                    if (store.isEmpty()) return blocked("Transferred From has no store to choose. First Save gave " + m1);
                    ri.choose(ADD_TRANSFERRED_FROM, store);
                    if (ri.gridRows(ADD_GRID) > 0) return blocked("Choosing \"" + store + "\" listed transfers; Save was not clicked again. First Save gave " + m1);
                    ri.clearMessages();
                    ri.clickSaveForValidation("Transferred From set but no transfer/item listed");
                    List<String> m2 = ri.messages(2500);
                    ri.dismissBoxes();
                    List<String> writes = writesSince(w0);
                    boolean ok = mentions(m1, "transferred from|select") && mentions(m2, "item") && writes.isEmpty() && !success(m1) && !success(m2);
                    return verdict(ok, "Save on empty form -> " + m1 + "; with Transferred From \"" + store + "\" and no item -> " + m2
                            + (writes.isEmpty() ? "; no save request sent" : "; save request SENT (aborted by the guard): " + writes));
                });

        tc("RII-13", "Verify Item Group dropdown",
                "1. Open Item Group. 2. Pick its first group.",
                "Item Group lists the item groups and holds the picked one.",
                () -> {
                    if (!ADD_STATE.equals(ri.currentState()) && !ri.goState(ADD_STATE)) return blocked("The add form could not be opened (see RII-11).");
                    List<String> groups = ri.options(ADD_GROUP);
                    if (groups.isEmpty()) return defect("Item Group has no entries");
                    String err = ri.choose(ADD_GROUP, groups.get(0));
                    String held = ri.model(ADD_GROUP);
                    return verdict(err.isEmpty() && !held.isEmpty(), groups.size() + " groups (first " + head(groups, 5) + "); picked \"" + groups.get(0) + "\" -> "
                            + (err.isEmpty() ? "held as " + held : err));
                });

        tc("RII-14", "Verify Sub-Category dependency",
                "1. With no group chosen, check Item Sub-Category. 2. Pick groups one by one (up to 10) until one offers sub-categories.",
                "Sub-Category is disabled until a group is picked, is enabled after, and lists the picked group's sub-categories.",
                () -> {
                    if (!ADD_STATE.equals(ri.currentState()) && !ri.goState(ADD_STATE)) return blocked("The add form could not be opened (see RII-11).");
                    ri.reset(ADD_GROUP);
                    boolean disabledBefore = ri.disabled(ADD_SUBCATEGORY);
                    List<String> groups = ri.options(ADD_GROUP);
                    String found = ""; boolean enabledAfter = false; List<String> subs = new ArrayList<>();
                    for (String g : groups.subList(0, Math.min(10, groups.size()))) {
                        int a0 = api.size();
                        ri.choose(ADD_GROUP, g);
                        page.waitForTimeout(400);
                        enabledAfter = !ri.disabled(ADD_SUBCATEGORY);
                        subs = ri.options(ADD_SUBCATEGORY);
                        if (!subs.isEmpty()) { found = g + " (APIs " + apisSince(a0) + ")"; break; }
                    }
                    if (!enabledAfter) return defect("Sub-Category stayed disabled after picking a group (disabled before: " + disabledBefore + ")");
                    if (found.isEmpty()) return blocked("Sub-Category was enabled, but none of the first " + Math.min(10, groups.size())
                            + " groups has sub-categories, so the list content cannot be verified. Disabled before a group: " + disabledBefore);
                    return verdict(disabledBefore, "Disabled before a group: " + disabledBefore + "; group " + found + " -> " + subs.size() + " sub-categories " + head(subs, 6));
                });

        tc("RII-15", "Verify pending issue records",
                "1. On the add form, Search with Received To = the user's store and Transferred From = each store that issued to it (from the list, up to 5).",
                "Pending (issued, not yet received) transfers are listed; when a store has none, a clear no-records message is shown.",
                () -> {
                    if (!ADD_STATE.equals(ri.currentState()) && !ri.goState(ADD_STATE)) return blocked("The add form could not be opened (see RII-11).");
                    ri.reset(ADD_GROUP);
                    List<String> froms = wide.stream().map(r -> String.valueOf(r.get(C_FROM)).trim()).distinct().limit(5).collect(Collectors.toList());
                    if (froms.isEmpty()) froms = ri.options(ADD_TRANSFERRED_FROM).stream().limit(3).collect(Collectors.toList());
                    List<String> report = new ArrayList<>(), silent = new ArrayList<>();
                    boolean frozen = false; int pending = 0;
                    for (String f : froms) {
                        ri.clearMessages();
                        Call c = ri.addFormSearch(f);
                        int n = ri.gridRows(ADD_GRID);
                        List<String> msgs = ri.messages(1500);
                        ri.dismissBoxes();
                        if (mentions(msgs, "freez|frozen")) frozen = true;
                        if (n > 0) pending += n;
                        else if (msgs.isEmpty()) silent.add(f);
                        report.add(f + ": " + (n > 0 ? n + " pending" : "0 rows") + (msgs.isEmpty() ? "" : " " + msgs) + " [" + c + "]");
                    }
                    String detail = "Received To \"" + ri.chosen(ADD_RECEIVED_TO) + "\"; " + String.join("; ", report);
                    if (pending > 0) return pass(detail);
                    if (!silent.isEmpty()) return defect(detail + "; Search gave NO feedback for " + silent);
                    if (frozen) return blocked(detail + " - the receiving store is frozen (stock take), so nothing can be received there.");
                    return blocked(detail + " - no pending transfer exists for the stores tried.");
                });

        tc("RII-16", "Verify Back navigation",
                "1. On the add form click Back.",
                "The list screen (state " + LIST_STATE + ") is shown again; no request that changes data is sent.",
                () -> {
                    if (!ADD_STATE.equals(ri.currentState()) && !ri.goState(ADD_STATE)) return blocked("The add form could not be opened (see RII-11).");
                    int w0 = blockedWrites.size();
                    String why = ri.back();
                    List<String> writes = writesSince(w0);
                    return verdict(why.isEmpty() && writes.isEmpty(), (why.isEmpty() ? "Back returned to " + ri.route() : why) + (writes.isEmpty() ? "" : "; write request attempted " + writes));
                });
    }

    // ======================================================================= page-specific helpers

    private void ensureWide() {
        if (!LIST_STATE.equals(ri.currentState())) ri.reopen();
        if (new HashSet<>(numbers(ri.gridData(MASTER_GRID))).equals(new HashSet<>(numbers(wide)))) return;
        ri.reset(FROM_STORE);
        ri.reset(STATUS);
        ri.dates(wideFrom, today);
        ri.search();
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
