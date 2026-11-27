package com.kpj.tests.AncillaryServices_page.KitchenMenu_page;

import com.kpj.core.DevHisBase;
import com.microsoft.playwright.Request;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

// The page object has the same simple name — it is referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Kitchen &amp; Menu &gt; <b>Kitchen Order Dispatch</b> ({@code #/KitchenOrderDispatch}) — functional test (KD-01 … KD-22).
 *
 * <p>List of Orders: page load, Total Orders, meal-type columns, Date Range, Refresh, the column filters (MRN, Patient Name, Ward, Diet Type, Allergy),
 * Print (list), Print Inpatient Serving Checklist, Print Meal Label. New Order: popup, validations (Meal Type, MRN, menu item), patient search, menu
 * load per meal type, optional Save. Preparation: queue per meal type, Print Item Preparation Queue, Print Taste Report, Print Order List. Console /
 * API errors at the end. Prints are captured in the page (no window, no download). Report:
 * {@code test-output/reports/AncillaryServices_KitchenMenu_KitchenOrderDispatch.html}.</p>
 *
 * <h3>Data</h3>
 * <ul>
 *   <li>New Order uses the admitted patient {@code -Dkod.mrn} (default 1100339440) and meal type {@code -Dkod.meal} (default Breakfast).</li>
 *   <li>Saving a real meal order is OFF by default; {@code -Dkod.save=true} saves one order (first menu item) for that patient.</li>
 *   <li>The Date Range case uses {@code -Dkod.from} (default: 30 days ago) to today.</li>
 * </ul>
 *
 * <pre>
 *   mvn -o test -Dtest=com.kpj.tests.AncillaryServices_page.KitchenMenu_page.KitchenOrderDispatch -Ddevhis.postrun=off
 * </pre>
 */
public class KitchenOrderDispatch extends DevHisBase {

    public KitchenOrderDispatch() { super("AncillaryServices_KitchenMenu_KitchenOrderDispatch"); }

    public static void main(String[] args) {
        KitchenOrderDispatch t = new KitchenOrderDispatch();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern API = Pattern.compile("^https?://[^/]+/api/", Pattern.CASE_INSENSITIVE);

    private com.kpj.pages.AncillaryServices_page.KitchenMenu_page.KitchenOrderDispatch k;
    private final LocalDate today = LocalDate.now();
    private final String from = System.getProperty("kod.from", today.minusDays(30).format(DMY));
    private final String mrn = System.getProperty("kod.mrn", "1100339440");
    private final String meal = System.getProperty("kod.meal", "Breakfast");
    private final boolean doSave = "true".equalsIgnoreCase(System.getProperty("kod.save", "false"));

    private final List<String> consoleErrors = new CopyOnWriteArrayList<>();
    private final List<String> apiFailures = new CopyOnWriteArrayList<>();
    private final List<String> defects = new ArrayList<>();
    private final List<String> observations = new ArrayList<>();

    private int todayOrders = 0;
    private Map<String, Object> firstRow = new LinkedHashMap<>();
    private List<String> items = new ArrayList<>();

    private static final class R {
        String desc, expected, actual = "", status = "PASS";
        R(String desc, String expected) { this.desc = desc; this.expected = expected; }
        R set(String actual, boolean ok) { this.actual = actual; this.status = ok ? "PASS" : "FAIL"; return this; }
        R set(String actual, String status) { this.actual = actual; this.status = status; return this; }
    }
    private interface Body { R run(); }

    // =================================================================================================================

    @Override
    protected void body() {
        meta("Ancillary Services - Kitchen & Menu - Kitchen Order Dispatch", "Ancillary Services > Kitchen & Menu > Kitchen Order Dispatch",
                "Functional test of the List of Orders (date range, filters, prints, meal labels), the New Order popup (validations, patient and menu) and the "
                        + "Preparation tab (queue and prints). Patient MRN " + mrn + "; " + (doSave ? "one meal order is SAVED." : "no order is saved."));
        attachMonitors();
        login();
        step("KD-00 Login", "Log in with the configured test account (values not shown)", "Authenticated; application shell loaded", "Logged in to " + BASE, "PASS");
        k = new com.kpj.pages.AncillaryServices_page.KitchenMenu_page.KitchenOrderDispatch(page);
        try {
            tc("KD-01 Page Loading", this::pageLoading);
            if (!k.onScreen()) { addSummary("Result", "FAILED — Kitchen Order Dispatch could not be opened; remaining cases not run"); return; }
            k.spyAlerts();
            tc("KD-02 Today's Orders / Total Orders", this::todayList);
            tc("KD-03 Meal Type Columns", this::mealColumns);
            tc("KD-04 Date Range", this::dateRange);
            tc("KD-05 Refresh", this::refresh);
            tc("KD-06 Filter - MRN", this::filterMrn);
            tc("KD-07 Filter - Patient Name / Ward", this::filterNameWard);
            tc("KD-08 Filter - Diet Type", this::filterDietType);
            tc("KD-09 Filter - Allergy", this::filterAllergy);
            tc("KD-10 Print (List of Orders)", this::printList);
            tc("KD-11 Print Inpatient Serving Checklist", this::printChecklist);
            tc("KD-12 Print Meal Label", this::printMealLabel);
            tc("KD-13 New Order - Popup / Empty Save / Empty MRN", this::newOrderEmpty);
            tc("KD-14 New Order - Patient Search", this::newOrderPatient);
            tc("KD-15 New Order - Menu for the Meal Type", this::newOrderMenu);
            tc("KD-16 New Order - Save Without Item", this::newOrderNoItem);
            if (doSave) tc("KD-17 New Order - Save", this::newOrderSave);
            tc("KD-18 New Order - Close", this::newOrderClose);
            tc("KD-19 Preparation - Queue", this::prepQueue);
            tc("KD-20 Preparation - Print Item Preparation Queue", this::prepPrintQueue);
            tc("KD-21 Preparation - Print Taste Report / Order List", this::prepReports);
            tc("KD-22 Console / Network Errors", this::consoleCheck);
        } finally { try { k.disarmPrintCapture(); } catch (Exception ignore) { } writeSummary(); }
    }

    // =================================================================================================================
    // A. List of Orders
    // =================================================================================================================

    private R pageLoading() {
        R r = new R("Open Ancillary Services > Kitchen & Menu > Kitchen Order Dispatch from the sidebar",
                "The List of Orders opens with Date Range, Total Orders, Print Inpatient Serving Checklist, New Order, Print, Refresh and the order table");
        if (!k.navigateViaMenu()) return r.set("Did NOT open — " + k.navNotes + " (" + page.url() + ")", false);
        Map<String, String> b = k.buttons();
        List<String> need = List.of("Print Inpatient Serving Checklist", "New Order", "Print", "Refresh"), missing = new ArrayList<>();
        for (String n : need) if (!b.containsKey(n)) missing.add(n);
        return r.set("Loaded in " + secs(k.lastLoadMs) + "; buttons " + b.keySet() + (missing.isEmpty() ? "" : "; MISSING " + missing), missing.isEmpty());
    }

    private R todayList() {
        R r = new R("Read today's orders (click Refresh if the first load shows none)", "Date Range is off, the date is today, and Total Orders equals the patients listed");
        Map<String, Object> s = k.listState();
        if (n(s.get("list")) <= 0) {
            int api0 = apiFailures.size();
            k.refresh();
            s = k.listState();
            if (n(s.get("list")) > 0) observation("KD-02", "The screen opened with no orders; Refresh then listed " + s.get("list")
                    + (apiFailures.isEmpty() ? "" : " (failed calls so far: " + head(apiFailures, 2) + ")"));
            else if (apiFailures.size() > api0) observation("KD-02", "Refresh failed: " + head(apiFailures.subList(api0, apiFailures.size()), 2));
        }
        todayOrders = n(s.get("list"));
        List<Map<String, Object>> d = k.data("MRN", "PatientName", "Ward", "Bed", "DietType", "Allergy");
        if (!d.isEmpty()) firstRow = d.get(0);
        boolean ok = !Boolean.TRUE.equals(s.get("range")) && String.valueOf(s.get("orderdate")).equals(today.toString()) && n(s.get("total")) == todayOrders && n(s.get("rows")) >= todayOrders;
        return r.set("Order date " + s.get("orderdate") + ", range " + s.get("range") + "; Total Orders " + s.get("total") + ", list " + todayOrders + ", table rows " + s.get("rows")
                + (firstRow.isEmpty() ? "" : "; first: " + firstRow.get("MRN") + " " + s(firstRow.get("PatientName"))), todayOrders == 0 ? "MANUAL" : (ok ? "PASS" : "FAIL"));
    }

    private R mealColumns() {
        R r = new R("Read the meal-type columns of the order table", "One column per meal type, each meal type once");
        List<String> cols = k.mealColumns();
        Map<String, Integer> cnt = new LinkedHashMap<>();
        for (String c : cols) cnt.merge(c, 1, Integer::sum);
        List<String> dup = new ArrayList<>();
        cnt.forEach((n, c) -> { if (c > 1) dup.add(n + " ×" + c); });
        if (!dup.isEmpty()) defect("KD-03", "High", cols.size() + " meal-type columns for " + cnt.size() + " meal types — repeated: " + head(dup, 5)
                + " (the Inpatient Serving Checklist prints all of them and becomes unreadable)");
        return r.set(cols.size() + " column(s), " + cnt.size() + " distinct: " + head(cnt.keySet(), 12), !cols.isEmpty() && dup.isEmpty());
    }

    private R dateRange() {
        R r = new R("Tick Date Range, From " + from + " To " + today.format(DMY) + "; then untick", "The orders of the period are loaded (at least today's); unticking returns to today's orders");
        String p = k.dateRange(true, from, today.format(DMY));
        Map<String, Object> s = k.listState();
        int ranged = n(s.get("list"));
        k.dateRange(false, null, null);
        Map<String, Object> b = k.listState();
        int back = n(b.get("list"));
        String backDate = String.valueOf(b.get("orderdate"));
        boolean backToday = backDate.equals(today.toString());
        if (!backToday) {
            defect("KD-04", "High", "After Date Range is ticked (From " + from + ") and unticked, the list stays on " + backDate + " (" + back + " orders) instead of today ("
                    + todayOrders + " orders); New Order then says \"Cannot place order for a past date\"");
            // The stuck date survives leaving the screen; a today–today range followed by unticking puts the list back on today.
            k.dateRange(true, today.format(DMY), today.format(DMY));
            k.dateRange(false, null, null);
            k.refresh();
        }
        boolean ok = p.isEmpty() && ranged >= todayOrders && backToday && back == todayOrders;
        return r.set("Range " + s.get("from") + " – " + s.get("to") + ": " + ranged + " order(s), Total Orders " + s.get("total") + (p.isEmpty() ? "" : " (" + p + ")")
                + "; after unticking: order date " + backDate + ", " + back + " order(s)"
                + (backToday ? "" : " — reset with a today–today range: now " + k.listState().get("orderdate") + ", " + k.listState().get("list") + " order(s)"), ok);
    }

    private R refresh() {
        R r = new R("Click Refresh", "Today's orders are loaded again");
        long t0 = System.currentTimeMillis();
        k.refresh();
        int n = n(k.listState().get("list"));
        return r.set(n + " order(s) in " + secs(System.currentTimeMillis() - t0), n == todayOrders);
    }

    private R filterMrn() {
        String m = s(firstRow.get("MRN"));
        R r = new R("Type MRN " + m + " in the MRN column filter; then an unknown MRN", "Only that patient is listed; the unknown MRN lists none");
        if (m.isEmpty()) return r.set("No orders today (KD-02)", "MANUAL");
        k.filterText("MRN", m);
        Set<String> mrns = new TreeSet<>(); for (Map<String, Object> x : k.data("MRN")) mrns.add(s(x.get("MRN")));
        k.filterText("MRN", "0000000000");
        int none = n(k.listState().get("list"));
        k.filterText("MRN", "");
        return r.set("MRN " + m + " → " + mrns + "; unknown → " + none + " row(s)", mrns.equals(Set.of(m)) && none == 0);
    }

    private R filterNameWard() {
        String name = s(firstRow.get("PatientName")), ward = s(firstRow.get("Ward"));
        R r = new R("Type part of the patient name \"" + name + "\"; then the ward \"" + ward + "\"", "Only matching rows are listed");
        if (name.isEmpty()) return r.set("No orders today (KD-02)", "MANUAL");
        String part = name.length() > 6 ? name.substring(0, 6) : name;
        k.filterText("PatientName", part);
        List<String> badN = new ArrayList<>(); for (Map<String, Object> x : k.data("PatientName")) if (!s(x.get("PatientName")).toLowerCase().contains(part.toLowerCase())) badN.add(s(x.get("PatientName")));
        int nN = n(k.listState().get("list"));
        k.filterText("PatientName", "");
        k.filterText("Ward", ward);
        List<String> badW = new ArrayList<>(); for (Map<String, Object> x : k.data("Ward")) if (!s(x.get("Ward")).toLowerCase().contains(ward.toLowerCase())) badW.add(s(x.get("Ward")));
        int nW = n(k.listState().get("list"));
        k.filterText("Ward", "");
        return r.set("Name \"" + part + "\" → " + nN + " row(s)" + (badN.isEmpty() ? "" : " NOT matching " + badN) + "; Ward → " + nW + " row(s)" + (badW.isEmpty() ? "" : " NOT matching " + badW),
                nN > 0 && nW > 0 && badN.isEmpty() && badW.isEmpty());
    }

    private R filterDietType() {
        R r = new R("Pick each Diet Type in the column filter", "Only rows of that diet type are listed");
        List<String> opts = k.filterOptions("DietType");
        if (opts.isEmpty()) return r.set("Diet Type filter has no values", todayOrders == 0 ? "MANUAL" : "FAIL");
        List<String> res = new ArrayList<>(), bad = new ArrayList<>();
        for (String o : opts) {
            String p = k.filterSelect("DietType", o);
            if (!p.isEmpty()) { bad.add(o + ": " + p); continue; }
            Set<String> got = new TreeSet<>(); for (Map<String, Object> x : k.data("DietType")) got.add(s(x.get("DietType")));
            res.add(o + "=" + n(k.listState().get("list")));
            if (!got.isEmpty() && !got.equals(Set.of(o))) bad.add(o + " lists " + got);
        }
        k.filterSelect("DietType", "All");
        return r.set(String.join(", ", res) + (bad.isEmpty() ? "" : "; WRONG: " + bad), bad.isEmpty());
    }

    private R filterAllergy() {
        R r = new R("Tick the Allergy filter", "Only patients with an allergy are listed");
        k.allergyOnly(true);
        List<String> none = new ArrayList<>(); for (Map<String, Object> x : k.data("MRN", "Allergy")) if (s(x.get("Allergy")).isEmpty()) none.add(s(x.get("MRN")));
        int n = n(k.listState().get("list"));
        k.allergyOnly(false);
        return r.set(n + " patient(s) with allergy" + (none.isEmpty() ? "" : "; WITHOUT allergy: " + none), todayOrders == 0 ? "MANUAL" : (none.isEmpty() ? "PASS" : "FAIL"));
    }

    private R printList() {
        R r = new R("Click Print; then filter to an unknown MRN and click Print", "The printed list holds today's orders; with no rows: \"No records to print\"");
        k.armPrintCapture();
        k.clickNg("fnPrintListOfOrders();", 15000);
        Map<String, Object> c = k.captured();
        String txt = s(c.get("text"));
        boolean hasRow = firstRow.isEmpty() || txt.contains(s(firstRow.get("MRN")));
        k.filterText("MRN", "0000000000");
        int a0 = k.alerts().size();
        k.armPrintCapture();
        k.clickNg("fnPrintListOfOrders();", 15000);
        List<String> a = since(a0);
        k.filterText("MRN", "");
        boolean warn = a.stream().anyMatch(x -> x.toLowerCase().contains("no records"));
        return r.set("Printed " + c.get("htmlLength") + " chars" + (firstRow.isEmpty() ? "" : ", first MRN present: " + hasRow) + "; empty list → " + (a.isEmpty() ? "no message" : a),
                n(c.get("htmlLength")) > 0 && hasRow && warn);
    }

    private R printChecklist() {
        R r = new R("Click Print Inpatient Serving Checklist", "A readable checklist: today's date, the patients, one column group per meal type");
        k.armPrintCapture();
        k.clickNg("fnPrintInpatientServingChecklist();", 15000);
        Map<String, Object> c = k.captured();
        String txt = s(c.get("text"));
        int cols = n(c.get("tableCols"));
        boolean date = txt.contains(today.format(DMY));
        boolean pat = firstRow.isEmpty() || txt.contains(s(firstRow.get("MRN")));
        if (cols > 40) defect("KD-11", "High", "The Inpatient Serving Checklist header has " + cols + " cells (one group per repeated meal type), so the text wraps letter by letter and cannot be read");
        return r.set("Printed " + c.get("htmlLength") + " chars, " + cols + " header cell(s), date " + today.format(DMY) + " shown: " + date + ", first patient shown: " + pat,
                n(c.get("htmlLength")) > 0 && date && pat && cols <= 40);
    }

    private R printMealLabel() {
        R r = new R("Click Print Meal Label on the first row that has one", "A meal label is printed with the patient's name and MRN");
        k.armPrintCapture();
        int a0 = k.alerts().size();
        String m = k.clickMealLabel("");
        if (m.isEmpty()) return r.set("No Print Meal Label button today (no order taken)", "MANUAL");
        Map<String, Object> c = k.captured();
        List<String> a = since(a0);
        String txt = s(c.get("text"));
        boolean ok = n(c.get("htmlLength")) > 0 && a.stream().noneMatch(x -> x.toLowerCase().contains("pop-up"));
        return r.set("Meal " + m + ": label " + c.get("htmlLength") + " chars — " + trim(txt, 160) + (a.isEmpty() ? "" : "; alerts " + a), ok);
    }

    // =================================================================================================================
    // B. New Order
    // =================================================================================================================

    private R newOrderEmpty() {
        R r = new R("Click New Order; Save with nothing filled; click the MRN search with MRN empty", "The New Order popup opens; \"Please select Meal Type\"; \"Please Enter MRN No\"");
        int p0 = k.alerts().size();
        if (!k.openNewOrder()) return r.set("New Order popup did not open — " + k.lastPopupNote + "; alerts " + since(p0), false);
        int a0 = k.alerts().size();
        k.clickPopup("fnSaveNewOrder()");
        List<String> a1 = since(a0);
        a0 = k.alerts().size();
        k.clickPopup("fnSearchOrderPatient()");
        List<String> a2 = since(a0);
        boolean ok = a1.stream().anyMatch(x -> x.toLowerCase().contains("meal type")) && a2.stream().anyMatch(x -> x.toLowerCase().contains("mrn"));
        return r.set("Save → " + (a1.isEmpty() ? "no message" : a1) + "; MRN search → " + (a2.isEmpty() ? "no message" : a2) + "; meal types offered " + head(k.mealTypes(), 12), ok);
    }

    private R newOrderPatient() {
        R r = new R("Type MRN " + mrn + " and click the MRN search", "The admitted patient is loaded with the Diet Sheet (diet type)");
        if (!k.popupOpen() && !k.openNewOrder()) return r.set("New Order popup did not open", false);
        k.popupMrn(mrn);
        int a0 = k.alerts().size();
        k.clickPopup("fnSearchOrderPatient()");
        page.waitForTimeout(1500);
        Map<String, Object> p = k.popupState();
        List<String> a = since(a0);
        boolean ok = n(p.get("admissionId")) > 0 && !s(p.get("patient")).isEmpty();
        return r.set("Admission " + p.get("admissionId") + ", " + trim(s(p.get("patient")), 80) + ", diet " + p.get("dietType") + ", NBM " + p.get("nbm") + (a.isEmpty() ? "" : "; alerts " + a), ok);
    }

    private R newOrderMenu() {
        R r = new R("Pick Meal Type = " + meal, "The menu items of " + meal + " for today are listed");
        if (!k.popupOpen()) return r.set("New Order popup is not open (KD-13)", "MANUAL");
        String p = k.pickMealType(meal);
        if (!p.isEmpty()) return r.set("Could not pick " + meal + ": " + p, false);
        page.waitForTimeout(1500);
        Map<String, Object> s = k.popupState();
        @SuppressWarnings("unchecked") List<String> it = (List<String>) s.get("items");
        items = it == null ? new ArrayList<>() : it;
        return r.set(items.size() + " item(s): " + head(items, 8) + "; Meal For " + s.get("mealFor"), items.isEmpty() ? "MANUAL" : "PASS");
    }

    private R newOrderNoItem() {
        R r = new R("Click Save with no menu item ticked", "\"Please select at least one menu item\"; nothing is saved");
        if (!k.popupOpen()) return r.set("New Order popup is not open (KD-13)", "MANUAL");
        int a0 = k.alerts().size();
        k.clickPopup("fnSaveNewOrder()");
        List<String> a = since(a0);
        boolean ok = a.stream().anyMatch(x -> x.toLowerCase().contains("menu item")) && k.popupOpen();
        return r.set("Save → " + (a.isEmpty() ? "no message" : a), ok);
    }

    private R newOrderSave() {
        R r = new R("Tick the first menu item and click Save", "The order is saved and the patient's " + meal + " shows as taken");
        if (items.isEmpty()) return r.set("No menu item for " + meal + " (KD-15)", "MANUAL");
        if (!k.tickItem(0)) return r.set("Could not tick the first item", false);
        int a0 = k.alerts().size();
        k.clickPopup("fnSaveNewOrder()");
        page.waitForTimeout(1500);
        List<String> a = since(a0);
        boolean ok = a.stream().anyMatch(x -> x.toLowerCase().contains("success") || x.toLowerCase().contains("saved"));
        return r.set("Item " + items.get(0) + " → " + (a.isEmpty() ? "no message" : a), ok);
    }

    private R newOrderClose() {
        R r = new R("Close the New Order popup (×)", "The popup closes and the List of Orders is shown");
        k.closePopup();
        boolean closed = !k.popupOpen();
        k.refresh();
        return r.set("Closed: " + closed + "; " + k.listState().get("list") + " order(s) listed", closed);
    }

    // =================================================================================================================
    // C. Preparation
    // =================================================================================================================

    private R prepQueue() {
        R r = new R("Open the Preparation tab; pick each meal type", "The item preparation queue loads for each meal type");
        k.tab("Preparation");
        Map<String, Object> s0 = k.prepState();
        List<String> metas = k.prepMealTypes();
        List<String> res = new ArrayList<>();
        int withItems = 0;
        for (String m : metas.subList(0, Math.min(metas.size(), 6))) {
            String p = k.pickPrepMeal(m);
            Map<String, Object> s = k.prepState();
            res.add(m + "=" + (p.isEmpty() ? s.get("rows") : "pick failed"));
            if (n(s.get("rows")) > 0) withItems++;
        }
        boolean ok = !metas.isEmpty() && String.valueOf(s0.get("headers")).contains("Item Name");
        return r.set("Default meal " + s0.get("meal") + "; columns " + head((List<?>) s0.get("headers"), 6) + "; queue rows: " + String.join(", ", res),
                ok ? (withItems > 0 ? "PASS" : "MANUAL") : "FAIL");
    }

    private R prepPrintQueue() {
        R r = new R("Click Print Item Preparation Queue", "The preparation queue is printed");
        k.armPrintCapture();
        int a0 = k.alerts().size();
        k.clickNg("fnPrintPreparationQueue()", 15000);
        Map<String, Object> c = k.captured();
        List<String> a = since(a0);
        return r.set("Printed " + c.get("htmlLength") + " chars" + (a.isEmpty() ? "" : "; alerts " + a), n(c.get("htmlLength")) > 0 || !a.isEmpty());
    }

    private R prepReports() {
        R r = new R("Click Print Taste Report and Print Order List", "Each opens its report (PDF) for the selected date and meal type");
        List<String> res = new ArrayList<>();
        boolean ok = true;
        for (String[] b : new String[][]{{"Taste Report", "fnPrintTasteReport()"}, {"Order List", "fnPrintOrderList()"}}) {
            k.armPrintCapture();
            int a0 = k.alerts().size();
            k.clickNg(b[1], 15000);
            Map<String, Object> c = k.captured();
            List<String> a = since(a0);
            @SuppressWarnings("unchecked") List<String> urls = (List<String>) c.get("urls");
            String url = urls == null || urls.isEmpty() ? "" : urls.get(0);
            if (url.startsWith("/") || url.startsWith("http")) {
                Map<String, Object> f = k.fetchReport(url);
                boolean good = n(f.get("status")) == 200 && n(f.get("size")) > 1000;
                ok &= good;
                res.add(b[0] + " → " + url.replaceAll("\\?.*", "") + " HTTP " + f.get("status") + " " + f.get("type") + " " + f.get("size") + " bytes");
            } else if (n(c.get("htmlLength")) > 0) {
                res.add(b[0] + " → printed HTML " + c.get("htmlLength") + " chars");
            } else {
                res.add(b[0] + " → " + (a.isEmpty() ? "nothing opened" : a));
                if (a.isEmpty()) ok = false;
            }
        }
        k.disarmPrintCapture();
        return r.set(String.join("; ", res), ok);
    }

    // =================================================================================================================
    // helpers
    // =================================================================================================================

    private R consoleCheck() {
        R r = new R("Console errors, page errors and failed API calls collected during the run", "None");
        return r.set("Console: " + (consoleErrors.isEmpty() ? "none" : consoleErrors.size() + " — " + head(consoleErrors, 4))
                + "; API failures: " + (apiFailures.isEmpty() ? "none" : apiFailures.size() + " — " + head(apiFailures, 4)), consoleErrors.isEmpty() && apiFailures.isEmpty());
    }

    private void tc(String name, Body b) {
        System.out.println("[KitchenOrderDispatch] --- " + name + " ---");
        long t0 = System.currentTimeMillis();
        R r;
        try { r = b.run(); }
        catch (Throwable e) { r = new R("Run " + name, "Case completes").set("ERROR: " + String.valueOf(e.getMessage()).split("\\R")[0], false); }
        step(page, name, r.desc, r.expected, r.actual + " [execution time " + secs(System.currentTimeMillis() - t0) + "]", r.status);
    }

    private List<String> since(int n0) {
        page.waitForTimeout(500);
        List<String> a = k.alerts();
        List<String> out = new ArrayList<>();
        for (int i = Math.min(n0, a.size()); i < a.size(); i++) out.add(a.get(i).replaceFirst("^\\w*:\\s*", ""));
        return new ArrayList<>(new LinkedHashSet<>(out));
    }

    private void attachMonitors() {
        page.onConsoleMessage(c -> { if ("error".equals(c.type())) consoleErrors.add(route() + " " + trim(c.text(), 200)); });
        page.onPageError(e -> consoleErrors.add(route() + " page error: " + trim(String.valueOf(e), 200)));
        page.onResponse(resp -> {
            if (!API.matcher(resp.url()).find()) return;
            Request q = resp.request();
            if (resp.status() >= 400) apiFailures.add(route() + " " + q.method() + " " + resp.url().replaceAll("^https?://[^/]+", "").replaceAll("[?#].*$", "") + " → HTTP " + resp.status());
        });
    }

    private void defect(String id, String severity, String text) { String d = "[" + severity + "] " + id + " " + text; if (!defects.contains(d)) defects.add(d); }
    @SuppressWarnings("unused")
    private void observation(String id, String text) { String d = id + " " + text; if (!observations.contains(d)) observations.add(d); }

    private void writeSummary() {
        addSummary("Application URL", BASE + "/" + com.kpj.pages.AncillaryServices_page.KitchenMenu_page.KitchenOrderDispatch.ROUTE);
        addSummary("Run date", today.toString());
        addSummary("Today's orders", String.valueOf(todayOrders));
        addSummary("New Order patient / meal", mrn + " / " + meal + (doSave ? " (one order saved)" : " (not saved: -Dkod.save=true to save)"));
        addSummary("Application defects found", defects.isEmpty() ? "None" : String.valueOf(defects.size()));
        int i = 1;
        for (String d : defects) addSummary("Defect " + (i++), d);
        i = 1;
        for (String o : observations) addSummary("Observation " + (i++), o);
        addSummary("Console errors", consoleErrors.isEmpty() ? "None" : consoleErrors.size() + " — " + head(consoleErrors, 4));
        addSummary("API failures", apiFailures.isEmpty() ? "None" : apiFailures.size() + " — " + head(apiFailures, 4));
    }

    private String route() {
        try { String u = page.url(); int i = u.indexOf('#'); return "[" + (i >= 0 ? u.substring(i) : u.replaceAll("^https?://[^/]+", "")) + "]"; } catch (Exception e) { return "[?]"; }
    }

    private static int n(Object o) { try { return o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return -1; } }
    private static String secs(long ms) { return ms < 0 ? "—" : String.format(Locale.ROOT, "%.1f s", ms / 1000.0); }
    private static String s(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static String trim(String s, int n) { s = s == null ? "" : s.replaceAll("\\s+", " "); return s.length() > n ? s.substring(0, n) + "…" : s; }
    private static String head(Collection<?> l, int n) {
        if (l == null) return "";
        List<String> x = new ArrayList<>(); for (Object o : l) x.add(String.valueOf(o));
        return x.size() <= n ? String.join("; ", x) : String.join("; ", x.subList(0, n)) + "; … (+" + (x.size() - n) + " more)";
    }
}
