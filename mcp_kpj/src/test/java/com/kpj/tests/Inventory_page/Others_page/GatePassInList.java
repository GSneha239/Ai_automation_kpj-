package com.kpj.tests.Inventory_page.Others_page;

import com.kpj.pages.Inventory_page.InventoryScreen;
import com.kpj.pages.Inventory_page.InventoryScreen.Call;
import com.kpj.pages.Inventory_page.InventoryScreen.PrintResult;
import com.kpj.tests.Inventory_page.InventoryReadOnlyCases;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.kpj.pages.Inventory_page.Others_page.GatePassInList.*;

/**
 * Inventory &gt; <b>Gate Pass In List</b> — READ-ONLY functional + defect-verification cases GPI-01 … GPI-16.
 *
 * <p>Report {@code test-output/reports/Inventory_GatePassInList.html}, summary {@code Inventory_GatePassInList_summary.txt},
 * log {@code test-output/logs/Inventory_GatePassInList.log}, FAIL/BLOCKED PNGs in {@code test-output/screenshots/GatePassInList/}.</p>
 *
 * <p>Expected results describe CORRECT behaviour: a defect makes the case FAIL (marked DEFECT); a case whose data does not exist (no Gate Pass
 * In / Gate Pass Out records) is BLOCKED. No Gate Pass is ever completed: Save is clicked only in GPI-12 / GPI-13 on a form verified to be
 * incomplete, and the write guard aborts any save attempt.</p>
 *
 * <p>Run only this class: {@code run-gatepassinlist.bat}, or
 * {@code mvn test "-Dtest=com.kpj.tests.Inventory_page.Others_page.GatePassInList" -Ddevhis.postrun=off}.</p>
 */
public class GatePassInList extends InventoryReadOnlyCases {

    public GatePassInList() {
        super("Inventory_GatePassInList", "Inventory - Gate Pass In List", "Inventory > Gate Pass In List (#/GatePassInList, #/add-GatePass)", "GatePassInList");
    }

    public static void main(String[] args) {
        GatePassInList t = new GatePassInList();
        try { t.run(); } finally { t.stop(); }
    }

    private com.kpj.pages.Inventory_page.Others_page.GatePassInList gp;

    @Override protected InventoryScreen screen() { return gp; }
    @Override protected String idPrefix() { return "GPI"; }

    private String defaultStore = "";
    private int gpoRecords = -1;
    private final List<String> gpoSuppliers = new ArrayList<>();

    @Override
    protected void runCases() {
        gp = new com.kpj.pages.Inventory_page.Others_page.GatePassInList(page);
        addSummary("Search window used to find records", wideFrom + " - " + today);

        tc("GPI-01", "Verify page navigation",
                "1. Open Inventory in the sidebar. 2. Click Gate Pass In List. 3. Check title, filters, buttons, grids and load APIs.",
                "Gate Pass In List (state " + LIST_STATE + ") opens; title \"" + LIST_TITLE + "\"; Select Date (+From/To), Select Range (+From/To), Store, Supplier,"
                        + " Search, Print, New are shown; the list grid has columns " + MASTER_HEADERS + "; load APIs answer HTTP 2xx.",
                () -> {
                    int a0 = api.size();
                    String why = gp.open();
                    if (!why.isEmpty()) {
                        boolean rec = gp.goState(LIST_STATE);
                        return defect("Menu navigation failed: " + why + (rec ? " (opened via the ui-router state only so the other cases can run)" : ""));
                    }
                    Map<String, String> btn = gp.buttons();
                    List<String> missing = new ArrayList<>();
                    for (String b : Arrays.asList(BTN_SEARCH, BTN_PRINT, BTN_NEW)) if (!btn.containsKey(b)) missing.add("button " + b);
                    for (String m : Arrays.asList(SELECT_DATE, FROM_DATE, TO_DATE, SELECT_RANGE, STORE, SUPPLIER))
                        if (page.locator("[ng-model='" + m + "']").count() == 0) missing.add("control " + m);
                    List<String> mh = gp.headers(MASTER_GRID), bad = failedApisSince(a0);
                    defaultStore = gp.chosen(STORE);
                    boolean ok = LIST_TITLE.equals(page.title().trim()) && missing.isEmpty() && mh.equals(MASTER_HEADERS) && bad.isEmpty();
                    return verdict(ok, "Opened " + gp.route() + " (state " + gp.currentState() + "), title \"" + page.title() + "\"; buttons " + btn.keySet()
                            + (missing.isEmpty() ? "" : "; MISSING " + missing) + "; grid " + mh + "; default Store \"" + defaultStore + "\"; load APIs " + apisSince(a0)
                            + (bad.isEmpty() ? "" : "; FAILED " + bad));
                });

        tc("GPI-02", "Verify default search",
                "1. Keep the defaults (Select Date / Select Range unticked, Store pre-selected, Supplier --Select--). 2. Click Search.",
                "POST " + FETCH_API + " (" + LIST_FLAG + ") carries the selected Store, answers HTTP 200; the grid shows exactly the records returned"
                        + " (an empty result shows a no-records message).",
                () -> {
                    gp.clearMessages();
                    Call c = gp.search();
                    int grid = gp.gridRows(MASTER_GRID);
                    List<String> msgs = gp.messages(c.rows == 0 ? 2000 : 300);
                    if (!c.fired()) return defect("Search sent no request: " + c.error);
                    String store = gp.model(STORE);
                    boolean ok = c.status == 200 && c.request.contains(LIST_FLAG) && c.request.matches("(?s).*\"store\"\\s*:\\s*\"?" + Pattern.quote(store) + "\\b.*")
                            && (c.rows < 0 || grid == c.rows) && (grid > 0 || !msgs.isEmpty());
                    return verdict(ok, "Store \"" + defaultStore + "\" (id " + store + "); request " + oneLine(c.request, 250) + "; " + c + "; grid rows " + grid
                            + (msgs.isEmpty() ? "" : "; messages " + msgs));
                });

        tc("GPI-03", "Verify search by date range",
                "1. Tick Select Date. 2. From = " + wideFrom + ", To = today. 3. Click Search (if nothing is found for the default Store, also with Store --Select--).",
                "The request carries both dates; every listed Gate Pass In is dated within the range.",
                () -> {
                    gp.dateRange(wideFrom, today);
                    Call c = gp.search();
                    if (!c.fired() || c.status != 200) return defect("Search failed: " + c);
                    boolean datesSent = c.request.contains(wideFrom) && c.request.contains(today);
                    List<Map<String, String>> rows = gp.gridData(MASTER_GRID);
                    String scope = "Store \"" + defaultStore + "\"";
                    if (rows.isEmpty()) {
                        gp.reset(STORE);
                        Call c2 = gp.search();
                        rows = gp.gridData(MASTER_GRID);
                        scope = "all stores (" + c2 + ")";
                        gp.choose(STORE, defaultStore);
                    }
                    if (!datesSent) return defect("The dates were NOT sent with Select Date ticked: request " + oneLine(c.request, 250));
                    if (rows.isEmpty()) return blocked("No Gate Pass In records exist between " + wideFrom + " and " + today + " for " + scope
                            + " (dates were sent correctly: " + oneLine(c.request, 200) + ").");
                    LocalDate f = LocalDate.parse(wideFrom, DMY), t = LocalDate.parse(today, DMY);
                    List<String> outOfRange = rows.stream().filter(r -> { LocalDate d = parseAnyDate(r.get(C_DATE)); return d != null && (d.isBefore(f) || d.isAfter(t)); })
                            .map(r -> r.get(C_NO) + " " + r.get(C_DATE)).collect(Collectors.toList());
                    return verdict(outOfRange.isEmpty(), rows.size() + " record(s) for " + scope + (outOfRange.isEmpty() ? ", all within the range" : "; OUTSIDE the range " + outOfRange));
                });

        tc("GPI-04", "Verify Select Date checkbox behaviour",
                "1. Untick Select Date - check the From/To boxes. 2. Tick it - check again. 3. Untick it and click Search.",
                "Unticked: From/To are disabled and the search sends NO date filter; ticked: From/To are enabled.",
                () -> {
                    gp.setChecked(SELECT_DATE, false);
                    boolean offDisabled = !gp.dateInputsEnabled();
                    gp.setChecked(SELECT_DATE, true);
                    boolean onEnabled = gp.dateInputsEnabled();
                    gp.setDate(FROM_DATE, wideFrom); gp.setDate(TO_DATE, today);
                    gp.setChecked(SELECT_DATE, false);
                    Call c = gp.search();
                    boolean datesSent = c.request.matches("(?is).*\"(fromdate|todate)\"\\s*:\\s*\"\\d.*");
                    boolean ok = offDisabled && onEnabled && c.status == 200 && !datesSent;
                    return verdict(ok, "Unticked -> From/To " + (offDisabled ? "disabled" : "ENABLED") + "; ticked -> " + (onEnabled ? "enabled" : "STILL DISABLED")
                            + "; unticked search request " + oneLine(c.request, 250) + (datesSent ? " - date values were SENT although Select Date is unticked" : "") + "; " + c);
                });

        tc("GPI-05", "Verify Number Range filter",
                "1. Tick Select Range. 2. From = 1, To = 999999 in the Number panel. 3. Click Search. 4. Untick Select Range.",
                "The Number From/To boxes are enabled when ticked and the search request carries the range 1 - 999999.",
                () -> {
                    gp.setChecked(SELECT_RANGE, true);
                    String[] box = gp.rangeInputs();
                    if (box.length < 2) { gp.setChecked(SELECT_RANGE, false); return defect("The Number panel's From/To boxes were not found after ticking Select Range"); }
                    boolean enabled = !gp.inputDisabled(box[0]) && !gp.inputDisabled(box[1]);
                    page.locator(box[0]).fill("1"); page.locator(box[1]).fill("999999");
                    Call c = gp.search();
                    page.locator(box[0]).fill(""); page.locator(box[1]).fill("");
                    gp.setChecked(SELECT_RANGE, false);
                    boolean sent = c.request.contains("999999");
                    return verdict(enabled && c.status == 200 && sent, "Boxes " + (enabled ? "enabled" : "DISABLED") + " after ticking; request " + oneLine(c.request, 250) + "; " + c
                            + (sent ? "" : "; the number range was NOT sent to the server"));
                });

        tc("GPI-06", "Verify Store filter",
                "1. Choose another Store. 2. Click Search. 3. Put the default Store back.",
                "The request carries the chosen store's id and the result lists only that store's Gate Pass Ins.",
                () -> {
                    List<String> stores = gp.options(STORE);
                    String other = stores.stream().filter(s -> !s.equals(defaultStore)).findFirst().orElse("");
                    if (other.isEmpty()) return blocked("Only one store is available, so the filter cannot be changed.");
                    String err = gp.choose(STORE, other);
                    if (!err.isEmpty()) return defect("Store \"" + other + "\" could not be chosen: " + err);
                    String id = gp.model(STORE);
                    Call c = gp.search();
                    if (!defaultStore.isEmpty()) gp.choose(STORE, defaultStore);
                    boolean sent = c.request.matches("(?s).*\"store\"\\s*:\\s*\"?" + Pattern.quote(id) + "\\b.*");
                    return verdict(c.status == 200 && sent, "Store \"" + other + "\" (id " + id + "): request " + oneLine(c.request, 250) + "; " + c
                            + (sent ? "" : "; the chosen store id was NOT sent"));
                });

        tc("GPI-07", "Verify Supplier filter",
                "1. Choose a Supplier. 2. Click Search. 3. Put Supplier back to --Select--.",
                "The search request carries the chosen supplier's id, so the result is limited to that supplier.",
                () -> {
                    List<String> sup = gp.options(SUPPLIER);
                    if (sup.isEmpty()) return blocked("The Supplier list is empty.");
                    String err = gp.choose(SUPPLIER, sup.get(0));
                    if (!err.isEmpty()) return defect("Supplier \"" + sup.get(0) + "\" could not be chosen: " + err);
                    String id = gp.model(SUPPLIER);
                    Call c = gp.search();
                    gp.reset(SUPPLIER);
                    boolean sent = c.request.matches("(?is).*\"supplier\\w*\"\\s*:\\s*\"?" + Pattern.quote(id) + "\\b.*");
                    return verdict(c.status == 200 && sent, "Supplier \"" + sup.get(0) + "\" (id " + id + "): request " + oneLine(c.request, 250) + "; " + c
                            + (sent ? "" : "; the supplier was NOT sent - the filter has no effect"));
                });

        tc("GPI-08", "Verify reversed date validation",
                "1. Tick Select Date. 2. From = today, To = " + earlier + ". 3. Click Search. 4. Untick Select Date.",
                "The search is not sent; a message says From Date must not be later than To Date.",
                () -> {
                    gp.dateRange(today, earlier);
                    gp.clearMessages();
                    Call c = gp.searchExpectingNoRequest(5000);
                    List<String> msgs = gp.messages(1500);
                    gp.dismissBoxes();
                    gp.setChecked(SELECT_DATE, false);
                    boolean ok = !c.fired() && mentions(msgs, "date");
                    return verdict(ok, "From " + today + " / To " + earlier + ": " + (c.fired() ? "the search WAS sent - " + c + " request " + oneLine(c.request, 200) : "no request sent")
                            + "; messages " + msgs);
                });

        tc("GPI-09", "Verify Search with blank dates",
                "1. Tick Select Date. 2. Clear From and To. 3. Click Search. 4. Untick Select Date.",
                "The search is not sent; a message asks for the From / To dates.",
                () -> {
                    gp.setChecked(SELECT_DATE, true);
                    gp.setDate(FROM_DATE, ""); gp.setDate(TO_DATE, "");
                    gp.clearMessages();
                    Call c = gp.searchExpectingNoRequest(5000);
                    List<String> msgs = gp.messages(1500);
                    gp.dismissBoxes();
                    gp.setChecked(SELECT_DATE, false);
                    boolean ok = !c.fired() && mentions(msgs, "date");
                    return verdict(ok, (c.fired() ? "the search WAS sent with blank dates - " + c + " request " + oneLine(c.request, 200) : "no request sent") + "; messages " + msgs);
                });

        tc("GPI-10", "Verify Print without selecting a record",
                "1. Re-open the screen, click Search. 2. Click Print without selecting a row.",
                "No report opens; a message asks the user to select a Gate Pass In first.",
                () -> {
                    String r = gp.reopen();
                    if (!r.isEmpty()) return defect("Could not re-open the screen for a clean state: " + r);
                    gp.search();
                    PrintResult p = gp.print(BTN_PRINT, BASE);
                    gp.dismissBoxes();
                    return verdict(!p.opened() && !p.messages.isEmpty(), p + (p.messages.isEmpty() && !p.opened() ? " - Print did nothing and gave no feedback" : ""));
                });

        tc("GPI-15", "Verify available Gate Pass Out records",
                "1. Open Inventory > Gate Pass Out List (read only). 2. Click Search. 3. Read the suppliers of the records found.",
                "Gate Pass Out records exist, so a Gate Pass In can be made against them (data prerequisite for GPI-13 / GPI-14).",
                () -> {
                    String why = gp.openGatePassOut();
                    if (!why.isEmpty()) return defect("Gate Pass Out List could not be opened: " + why);
                    Call c = gp.searchGatePassOut();
                    List<Map<String, String>> rows = gp.gridData(MASTER_GRID);
                    gpoRecords = rows.size();
                    for (Map<String, String> row : rows)
                        row.entrySet().stream().filter(e -> e.getKey().toLowerCase().contains("supplier")).map(Map.Entry::getValue).filter(v -> !v.isBlank())
                                .findFirst().ifPresent(v -> { if (!gpoSuppliers.contains(v)) gpoSuppliers.add(v); });
                    String back = gp.open();
                    if (c.status != 200) return defect("Gate Pass Out search failed: " + c);
                    if (rows.isEmpty()) return blocked("Gate Pass Out List has no records (" + c + "), so no Gate Pass In can be made - GP Out No has nothing to offer."
                            + (back.isEmpty() ? "" : " Return to Gate Pass In List: " + back));
                    return pass(rows.size() + " Gate Pass Out record(s); suppliers " + gpoSuppliers + "; " + c);
                });

        // ---------------------------------------------------------------- add form (no Gate Pass is completed)
        tc("GPI-11", "Verify New button navigation",
                "1. On Gate Pass In List click New. 2. Check the state, route and page title.",
                "The add form opens at its registered route " + ADD_ROUTE + " (state " + ADD_STATE + ") with Store, Supplier, GP Out No, Search, Save, Back,"
                        + " and the tab title names the Gate Pass In entry form (not \"" + LIST_TITLE + "\").",
                () -> {
                    if (!LIST_STATE.equals(gp.currentState())) gp.reopen();
                    String reached = gp.clickNew();
                    String routeAfter = gp.route();
                    boolean navOk = ADD_STATE.equals(reached) && ADD_ROUTE.equals(routeAfter);
                    String note = "";
                    if (!navOk) {
                        boolean rec = gp.goState(ADD_STATE);
                        note = rec ? " (the add form was then opened via its registered state so the other cases can run)" : " (the add form could not be opened at all)";
                    }
                    String title = page.title().trim();
                    Map<String, String> btn = gp.buttons();
                    List<String> missing = new ArrayList<>();
                    for (String b : Arrays.asList(BTN_SEARCH, BTN_SAVE, BTN_BACK)) if (!btn.containsKey(b)) missing.add("button " + b);
                    for (String m : Arrays.asList(ADD_STORE, ADD_SUPPLIER, ADD_GP_OUT)) if (page.locator("[ng-model='" + m + "']").count() == 0) missing.add("control " + m);
                    boolean titleOk = !title.equalsIgnoreCase(LIST_TITLE);
                    return verdict(navOk && titleOk && missing.isEmpty(), "New -> state \"" + reached + "\", route " + routeAfter
                            + (navOk ? "" : " - New did NOT open the registered " + ADD_ROUTE) + note + "; add-form title \"" + title + "\""
                            + (titleOk ? "" : " (the list's title)") + (missing.isEmpty() ? "" : "; MISSING " + missing));
                });

        tc("GPI-12", "Verify Supplier mandatory validation",
                "1. Confirm the add form is incomplete (Supplier, GP Out No, Invoice No., Gate Entry No. empty; no item). 2. Click Save (any save request is aborted by the guard).",
                "Save is refused: a message asks for the Supplier; no save request is sent; no success message.",
                () -> {
                    if (!ADD_STATE.equals(gp.currentState()) && !gp.goState(ADD_STATE)) return blocked("The add form could not be opened (see GPI-11).");
                    String why = incompleteForm(true);
                    if (!why.isEmpty()) return blocked(why + " - Save was NOT clicked, to stay read-only.");
                    int w0 = blockedWrites.size();
                    gp.clearMessages();
                    gp.clickSaveForValidation("Supplier, GP Out No, Invoice, Gate Entry empty; no item");
                    List<String> msgs = gp.messages(2500);
                    gp.dismissBoxes();
                    List<String> writes = writesSince(w0);
                    return verdict(mentions(msgs, "supplier") && writes.isEmpty() && !success(msgs), "Messages " + msgs
                            + (writes.isEmpty() ? "; no save request" : "; save request SENT (aborted by the guard): " + writes));
                });

        tc("GPI-13", "Verify GP Out No mandatory validation",
                "1. Choose a Supplier (GP Out No stays empty; no item). 2. Click Save (any save request is aborted by the guard). 3. Put Supplier back.",
                "Save is refused: a message asks for the Gate Pass Out No.; no save request is sent; no success message.",
                () -> {
                    if (!ADD_STATE.equals(gp.currentState()) && !gp.goState(ADD_STATE)) return blocked("The add form could not be opened (see GPI-11).");
                    List<String> sup = gp.options(ADD_SUPPLIER);
                    if (sup.isEmpty()) return blocked("The Supplier list is empty.");
                    gp.pickSupplier(sup.get(0));
                    String why = incompleteForm(false);
                    if (!why.isEmpty()) { gp.reset(ADD_SUPPLIER); return blocked(why + " - Save was NOT clicked, to stay read-only."); }
                    int w0 = blockedWrites.size();
                    gp.clearMessages();
                    gp.clickSaveForValidation("Supplier set; GP Out No, Invoice, Gate Entry empty; no item");
                    List<String> msgs = gp.messages(2500);
                    gp.dismissBoxes();
                    gp.reset(ADD_SUPPLIER);
                    List<String> writes = writesSince(w0);
                    return verdict(mentions(msgs, "gate pass out|gp out|pass out") && writes.isEmpty() && !success(msgs), "Supplier \"" + sup.get(0) + "\"; messages " + msgs
                            + (writes.isEmpty() ? "; no save request" : "; save request SENT (aborted by the guard): " + writes));
                });

        tc("GPI-14", "Verify Supplier -> GP Out No dropdown dependency",
                "1. Pick suppliers one by one (those with Gate Pass Out records first, then the first 5 of the list); read GP Out No each time."
                        + " 2. With a supplier chosen and GP Out No empty, click Search on the add form.",
                "Picking a supplier loads its Gate Pass Out numbers (" + GP_OUT_LIST_FLAG + ", HTTP 200) into GP Out No - non-empty for a supplier that has"
                        + " Gate Pass Out records; Search without a GP Out No gives a validation message, not \"Error in getting record\".",
                () -> {
                    if (!ADD_STATE.equals(gp.currentState()) && !gp.goState(ADD_STATE)) return blocked("The add form could not be opened (see GPI-11).");
                    List<String> all = gp.options(ADD_SUPPLIER);
                    if (all.isEmpty()) return blocked("The Supplier list is empty.");
                    List<String> tryList = new ArrayList<>();
                    for (String s : gpoSuppliers) if (all.contains(s)) tryList.add(s);
                    for (String s : all) if (tryList.size() < gpoSuppliers.size() + 5 && !tryList.contains(s)) tryList.add(s);
                    List<String> report = new ArrayList<>(), failedCalls = new ArrayList<>(), emptyForGpo = new ArrayList<>();
                    int withNumbers = 0;
                    for (String s : tryList) {
                        Call c = gp.pickSupplier(s);
                        List<String> nos = gp.options(ADD_GP_OUT);
                        if (c.fired() && c.status != 200) failedCalls.add(s + " " + c);
                        if (!c.fired()) failedCalls.add(s + ": no GP Out request sent");
                        if (!nos.isEmpty()) withNumbers++;
                        else if (gpoSuppliers.contains(s)) emptyForGpo.add(s);
                        report.add(s + " -> " + nos.size() + " GP Out No(s)");
                    }
                    gp.clearMessages();
                    Call search = gp.addFormSearch();
                    List<String> msgs = gp.messages(2000);
                    gp.dismissBoxes();
                    gp.reset(ADD_SUPPLIER);
                    boolean genericError = mentions(msgs, "error in getting record");
                    String detail = String.join("; ", report) + "; add-form Search with no GP Out No -> " + msgs + " [" + search + "]";
                    if (!failedCalls.isEmpty() || genericError || !emptyForGpo.isEmpty())
                        return defect(detail + (failedCalls.isEmpty() ? "" : "; GP Out request problems " + failedCalls)
                                + (emptyForGpo.isEmpty() ? "" : "; GP Out No EMPTY for suppliers that have Gate Pass Out records " + emptyForGpo)
                                + (genericError ? "; Search without GP Out No gives the generic \"Error in getting record!\" instead of a validation message" : ""));
                    if (withNumbers == 0) return blocked(detail + " - no supplier tried has Gate Pass Out numbers (Gate Pass Out records: "
                            + (gpoRecords < 0 ? "unknown" : String.valueOf(gpoRecords)) + "), so the list content cannot be verified.");
                    return pass(detail);
                });

        tc("GPI-16", "Verify Back navigation",
                "1. On the add form click Back.",
                "The Gate Pass In List screen (state " + LIST_STATE + ") is shown again; no request that changes data is sent.",
                () -> {
                    if (!ADD_STATE.equals(gp.currentState()) && !gp.goState(ADD_STATE)) return blocked("The add form could not be opened (see GPI-11).");
                    int w0 = blockedWrites.size();
                    String why = gp.back();
                    List<String> writes = writesSince(w0);
                    return verdict(why.isEmpty() && writes.isEmpty(), (why.isEmpty() ? "Back returned to " + gp.route() : why) + (writes.isEmpty() ? "" : "; write request attempted " + writes));
                });
    }

    /** "" when the add form is incomplete (nothing that could form a Gate Pass In), otherwise what is filled. */
    private String incompleteForm(boolean supplierMustBeEmpty) {
        String sup = gp.model(ADD_SUPPLIER), gpo = gp.model(ADD_GP_OUT);
        String inv = gp.fieldValue("[ng-model='" + ADD_INVOICE + "']"), entry = gp.fieldValue("[ng-model='" + ADD_ENTRY + "']");
        int rows = gp.gridRows(ADD_GRID);
        List<String> filled = new ArrayList<>();
        if (supplierMustBeEmpty && !(sup.isEmpty() || sup.equals("0"))) filled.add("Supplier " + sup);
        if (!(gpo.isEmpty() || gpo.equals("0"))) filled.add("GP Out No " + gpo);
        if (!inv.isBlank()) filled.add("Invoice No. " + inv);
        if (!entry.isBlank()) filled.add("Gate Entry No. " + entry);
        if (rows > 0) filled.add(rows + " item row(s)");
        return filled.isEmpty() ? "" : "The add form is not incomplete: " + filled;
    }
}
