package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Inventory_page.POS_page.ListOfBills.Radio;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The page object has the same simple name — it is referenced by its fully-qualified name.

/**
 * Inventory &gt; POS &gt; <b>List Of Bills</b> — STRICTLY READ-ONLY functional test (LB-01 … LB-25).
 *
 * <p>Covers page loading, the filters (bill type OPD / IPD / External, Pharmacy Credit Invoice, Bill Status, Payor
 * Category, Location, Department, Consolidated Payor, From / To Date) and whether each value reaches the search
 * request, search results and the no-record behaviour, filter combinations (no stale values), grid columns,
 * pagination and sorting, duplicated / misplaced labels, developer-style button labels, the
 * {@code BillListSearch/IUDAttachment} call made on page load, search performance and console / network errors.
 * Report: {@code test-output/reports/Inventory_POS_ListOfBills.html}.</p>
 *
 * <h3>Safety</h3>
 * <ul>
 *   <li>The test only types dates, picks filter values, chooses radios and clicks Search. It never clicks Submit
 *       e-Bill, Generate E-Invoice, SubmitTextOutput, SubmitFTP, Export to Excel, Company Approved Amount, a grid
 *       Print / View cell or a grid row (the page object has no method for them).</li>
 *   <li>A request-level guard is installed BEFORE the screen opens: every non-GET {@code /api/} call whose last path
 *       segment is not a read verb (Fetch…, Get…, Search, List…) is aborted and recorded — including the
 *       {@code IUDAttachment} call the screen fires on load. Any download is cancelled and recorded.</li>
 *   <li>Credentials come only from DEVHIS_USER / DEVHIS_PASS (or -Ddevhis.user / -Ddevhis.pass). The test stops before
 *       logging in when they are missing (it never falls back to the framework's built-in account) and never writes
 *       the user name or password to the report or console.</li>
 * </ul>
 *
 * <p>Nothing is assumed about the data: rows and counts come from the screen and its API. Tunables:
 * {@code -Dlb.fromDate=01/01/2025} (wide search start, dd/MM/yyyy), {@code -Dlb.maxSearchMs=3000} (performance limit
 * for the median server time of a search).</p>
 *
 * <pre>
 *   mvn test -Dtest=com.kpj.tests.Inventory_page.POS_page.ListOfBills -Ddevhis.postrun=off
 * </pre>
 */
public class ListOfBills extends DevHisBase {

    public ListOfBills() { super("Inventory_POS_ListOfBills"); }

    public static void main(String[] args) {
        ListOfBills t = new ListOfBills();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    // ---- constants ---------------------------------------------------------------------------------------------------

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern API_URL = Pattern.compile("^https?://[^/]+/api/", Pattern.CASE_INSENSITIVE);
    /** Route matcher for the guard — a Java Predicate (a Pattern with (?i) cannot be turned into a JS RegExp). */
    private static final Predicate<String> API_ROUTE = url -> url != null && API_URL.matcher(url).find();
    private static final Pattern READ_CALL = Pattern.compile(
            "/(fetch|fetchlist|fetchlistfirst|fetchheader|get[a-z]*|load|search|list|view|find|check|filldropdown|dropdown)[^/]*$", Pattern.CASE_INSENSITIVE);
    private static final String SEARCH_PATH = "/api/billlistsearch/fetch";
    private static final String IUD_ATTACHMENT = "/api/BillListSearch/IUDAttachment";

    // ---- state -------------------------------------------------------------------------------------------------------

    private com.kpj.pages.Inventory_page.POS_page.ListOfBills lob;
    private final LocalDate today = LocalDate.now();
    private final String wideFrom = System.getProperty("lb.fromDate", "01/01/2025");
    private final long maxSearchMs = Long.getLong("lb.maxSearchMs", 3000L);

    /** One BillListSearch/Fetch call: request body, when it started, server time, status. */
    private static final class Call {
        final String body; final long start; volatile long ms = -1; volatile int status = -1;
        Call(String body, long start) { this.body = body; this.start = start; }
    }
    private final List<Call> searchCalls = new CopyOnWriteArrayList<>();
    private final Map<Request, Call> open = new ConcurrentHashMap<>();
    private final Map<Request, Long> started = new ConcurrentHashMap<>();
    private final List<String> loadCalls = new CopyOnWriteArrayList<>();
    private final List<String> consoleErrors = new CopyOnWriteArrayList<>();
    private final List<String> apiFailures = new CopyOnWriteArrayList<>();
    private final List<String[]> blockedWrites = new CopyOnWriteArrayList<>();   // {method, path, body, msAfterNavClick}
    private final List<String> downloads = new CopyOnWriteArrayList<>();
    private final List<String> defects = new ArrayList<>();
    private final List<String> observations = new ArrayList<>();
    private final Map<String, Integer> rowsFound = new LinkedHashMap<>();
    private volatile long navClickAt = 0;
    private volatile boolean loading = false;
    private Map<String, Object> defaults = new LinkedHashMap<>();

    /** Outcome of one test case. */
    private static final class R {
        String desc = "", expected = "", actual = "", status = "PASS";
        R(String desc, String expected) { this.desc = desc; this.expected = expected; }
        R set(String actual, boolean ok) { this.actual = actual; this.status = ok ? "PASS" : "FAIL"; return this; }
        R set(String actual, String status) { this.actual = actual; this.status = status; return this; }
    }
    private interface Body { R run(); }

    // =================================================================================================================

    @Override
    protected void body() {
        meta("Inventory - POS - List Of Bills", "Inventory > POS > List Of Bills",
                "<b>Read-only</b> functional test: page loading, filters and the search request, search results, filter "
                        + "combinations, grid, labels and buttons, the IUDAttachment call on load, performance, console / network "
                        + "errors. Each row is one test case (LB-nn). Nothing is submitted, saved, exported or downloaded.");

        if (!credentialsFromEnvironment()) return;
        attachMonitors();
        login();
        step("LB-00 Login", "Log in with the DEVHIS_USER / DEVHIS_PASS account (values not shown)", "Authenticated; application shell loaded",
                "Logged in to " + BASE, "PASS");
        installReadOnlyGuard();
        lob = new com.kpj.pages.Inventory_page.POS_page.ListOfBills(page);

        try {
            tc("LB-01 Page Loading", this::pageLoading);
            if (!lob.onRoute()) { addSummary("Result", "FAILED — List Of Bills could not be opened; remaining cases not run"); return; }
            tc("LB-02 Default Filter Validation", this::defaultFilters);
            lob.typeDate(SEL_TO(), today.format(DMY));
            lob.typeDate(SEL_FROM(), wideFrom);
            tc("LB-03 OPD Search", () -> billTypeSearch(Radio.OPD, "0"));
            tc("LB-04 IPD Search", () -> billTypeSearch(Radio.IPD, "1"));
            tc("LB-05 External Search", () -> billTypeSearch(Radio.EXTERNAL, "2"));
            tc("LB-06 Pharmacy Credit Invoice Search", this::pciSearch);
            tc("LB-07 Date Filter Validation", this::dateFilter);
            tc("LB-08 Bill Status Filter", this::billStatus);
            tc("LB-09 Payor Category Filter", this::payorCategory);
            tc("LB-10 Location Filter", this::location);
            tc("LB-11 Department Filter", this::department);
            tc("LB-12 Consolidated Payor Filter", this::consolidatedPayor);
            tc("LB-13 Combined Filter Validation", this::combined);
            tc("LB-14 Empty Search Results", this::emptyResults);
            tc("LB-15 Grid Column Validation", this::gridColumns);
            tc("LB-16 Pagination Validation", this::pagination);
            tc("LB-17 Sorting Validation", this::sorting);
            tc("LB-18 Duplicate Label Validation", this::labels);
            tc("LB-19 Button Label Validation", this::buttonLabels);
            tc("LB-20 API Request Monitoring (IUDAttachment)", this::iudAttachment);
            tc("LB-21 Search Performance Validation", this::performance);
            tc("LB-22 Console Error Monitoring", this::consoleCheck);
            tc("LB-23 Read-Only Safety Validation", this::safety);
            tc("LB-24 OPD/IPD Value Type Consistency", this::opdIpdTypes);
            tc("LB-25 Billing Data Observation", this::dataObservation);
        } finally {
            try { page.unroute(API_ROUTE); } catch (Exception ignore) { }
            writeSummary();
        }
    }

    // =================================================================================================================
    // A. Page loading and defaults
    // =================================================================================================================

    private R pageLoading() {
        R r = new R("Open Inventory > POS > List Of Bills from the sidebar; check heading, filters, buttons and grid; time the load",
                "Route #/PharmacyBillList loads with heading \"List Of Bills\", all filters, the Search button and the bill grid");
        loading = true;
        navClickAt = System.currentTimeMillis();
        boolean opened = lob.navigateViaMenu();
        loading = false;
        if (!opened) return r.set("Did NOT open — " + String.join("; ", lob.navNotes) + " (" + page.url() + ")", false);
        String h = lob.heading();
        Map<String, Object> c = lob.controls();
        List<String> missing = new ArrayList<>();
        c.forEach((k, v) -> { if (!Boolean.TRUE.equals(v)) missing.add(k); });
        List<String> failedAtLoad = new ArrayList<>(apiFailures);
        return r.set("Heading \"" + h + "\"; loaded in " + secs(lob.lastLoadMs) + " (" + loadCalls.size() + " API calls)"
                + (missing.isEmpty() ? "; all " + c.size() + " controls visible" : "; NOT visible: " + missing)
                + (failedAtLoad.isEmpty() ? "" : "; failed during load: " + head(failedAtLoad, 4))
                + (lob.navNotes.isEmpty() ? "" : " [" + String.join("; ", lob.navNotes) + "]"),
                h.equalsIgnoreCase(com.kpj.pages.Inventory_page.POS_page.ListOfBills.HEADING) && missing.isEmpty());
    }

    private R defaultFilters() {
        R r = new R("Read the default filter values, run Search with them and compare the request body",
                "From/To = today, Pharmacy Bills + OPD selected, a Location pre-selected; the request carries exactly these values");
        defaults = lob.model();
        String loc = lob.selectedText("BillList.LocationID"), enc = lob.selectedText("BillList.applicablefor");
        String type = lob.checkedRadio("BillList.PTPBCI"), bt = lob.checkedRadio("BillList.PharmacyBillOPDIPD");
        Call c = searchAndGet();
        List<String> bad = new ArrayList<>();
        String t = today.format(DMY);
        if (!t.equals(s(defaults.get("FromDate"))) || !t.equals(s(defaults.get("ToDate")))) bad.add("dates default " + defaults.get("FromDate") + " – " + defaults.get("ToDate"));
        if (!type.startsWith(Radio.PHARMACY_BILLS.id)) bad.add("type radio " + type);
        if (!bt.startsWith(Radio.OPD.id)) bad.add("bill type radio " + bt);
        if (c == null) bad.add("no search request captured");
        else {
            if (!t.equals(field(c, "FromDate")) || !t.equals(field(c, "ToDate"))) bad.add("request dates " + field(c, "FromDate") + " – " + field(c, "ToDate"));
            if (!"true".equals(field(c, "IsforPharmacyBillList")) || !"false".equals(field(c, "IsInvoice"))) bad.add("IsforPharmacyBillList/IsInvoice " + field(c, "IsforPharmacyBillList") + "/" + field(c, "IsInvoice"));
            if (!"0".equals(field(c, "Opd_Ipd"))) bad.add("Opd_Ipd " + raw(c, "Opd_Ipd"));
            if (!s(defaults.get("LocationID")).equals(field(c, "LocationID"))) bad.add("LocationID " + field(c, "LocationID") + " vs model " + defaults.get("LocationID"));
        }
        noteRows("default (today)", c);
        return r.set("Defaults: " + defaults.get("FromDate") + " – " + defaults.get("ToDate") + ", type " + type + ", bill type " + bt + ", Location \"" + loc
                + "\", Encounter \"" + enc + "\"; request " + (c == null ? "—" : trimBody(c.body)) + "; " + gridLine(c)
                + (bad.isEmpty() ? "" : " — MISMATCH: " + String.join("; ", bad)), bad.isEmpty());
    }

    // =================================================================================================================
    // B/C. Filters and search
    // =================================================================================================================

    private R billTypeSearch(Radio radio, String expectedOpdIpd) {
        R r = new R("Choose Pharmacy Bills + " + radio.label + ", From " + wideFrom + " To today, click Search",
                "Request: Opd_Ipd=" + expectedOpdIpd + ", IsforPharmacyBillList=true, IsInvoice=false, dates as typed; grid shows the response (or a no-record message)");
        lob.choose(Radio.PHARMACY_BILLS);
        lob.choose(radio);
        Call c = searchAndGet();
        if (c == null) return r.set("No BillListSearch/Fetch request was sent", false);
        boolean ok = expectedOpdIpd.equals(field(c, "Opd_Ipd")) && "true".equals(field(c, "IsforPharmacyBillList")) && "false".equals(field(c, "IsInvoice"))
                && wideFrom.equals(field(c, "FromDate")) && today.format(DMY).equals(field(c, "ToDate"));
        noteRows(radio.label, c);
        return r.set("Opd_Ipd sent as " + raw(c, "Opd_Ipd") + ", IsforPharmacyBillList " + field(c, "IsforPharmacyBillList") + ", IsInvoice " + field(c, "IsInvoice")
                + ", dates " + field(c, "FromDate") + " – " + field(c, "ToDate") + "; " + gridLine(c), ok);
    }

    private R pciSearch() {
        R r = new R("Choose Pharmacy Credit Invoice, same dates, click Search",
                "Request: IsInvoice=true, IsforPharmacyBillList=true and a defined Opd_Ipd (the controller sets Opd_Ipd=1 for credit invoices)");
        lob.choose(Radio.PHARMACY_CREDIT_INVOICE);
        String bt = lob.checkedRadio("BillList.PharmacyBillOPDIPD");
        boolean pbStillShown = Boolean.TRUE.equals(lob.controls().get("opd"));
        Call c = searchAndGet();
        lob.choose(Radio.PHARMACY_BILLS);
        lob.choose(Radio.OPD);
        if (c == null) return r.set("No BillListSearch/Fetch request was sent", false);
        String o = raw(c, "Opd_Ipd");
        boolean defined = o.matches("-?\\d+|\"\\d+\"");
        noteRows("Pharmacy Credit Invoice", c);
        if (!defined) defect("LB-06", "Medium", "Pharmacy Credit Invoice search sends Opd_Ipd " + o + ": the controller's Opd_Ipd=1 for credit invoices is overwritten by the (cleared) OPD/IPD radio value");
        return r.set("IsInvoice " + field(c, "IsInvoice") + ", IsforPharmacyBillList " + field(c, "IsforPharmacyBillList") + ", Opd_Ipd " + o
                + "; OPD/IPD/External radios " + (pbStillShown ? "still shown" : "hidden") + " with " + (bt.isEmpty() ? "none selected" : bt) + "; " + gridLine(c),
                "true".equals(field(c, "IsInvoice")) && "true".equals(field(c, "IsforPharmacyBillList")) && defined);
    }

    private R dateFilter() {
        R r = new R("(a) Search " + yearStart() + " – today; (b) set From = today then To = today-5 (reversed) and click Search",
                "(a) dates reach the request as typed; (b) a warning is shown and no search with From later than To is sent");
        lob.typeDate(SEL_TO(), today.format(DMY));
        lob.typeDate(SEL_FROM(), yearStart());
        Call a = searchAndGet();
        boolean aOk = a != null && yearStart().equals(field(a, "FromDate")) && today.format(DMY).equals(field(a, "ToDate"));
        noteRows(today.getYear() + " to date", a);

        lob.typeDate(SEL_FROM(), today.format(DMY));
        lob.closeToasts();
        lob.spyAlerts();
        String rev = today.minusDays(5).format(DMY);
        lob.typeDate(SEL_TO(), rev);
        int n0 = searchCalls.size();
        lob.search();
        List<String> alerts = lob.spiedAlerts();
        List<String> reversedSent = new ArrayList<>();
        for (Call c : searchCalls.subList(n0, searchCalls.size())) {
            LocalDate f = parse(field(c, "FromDate")), t = parse(field(c, "ToDate"));
            if (f != null && t != null && f.isAfter(t)) reversedSent.add(field(c, "FromDate") + " – " + field(c, "ToDate"));
        }
        boolean warned = !alerts.isEmpty();
        String toNow = lob.toDate();
        lob.closeToasts();
        lob.typeDate(SEL_TO(), today.format(DMY));
        lob.typeDate(SEL_FROM(), wideFrom);
        return r.set("(a) request dates " + (a == null ? "—" : field(a, "FromDate") + " – " + field(a, "ToDate")) + ", " + gridLine(a)
                + "; (b) messages " + alerts + ", To Date left at " + toNow + ", " + (reversedSent.isEmpty() ? "no reversed search sent" : "REVERSED search sent: " + reversedSent),
                aOk && warned && reversedSent.isEmpty());
    }

    private R billStatus() {
        R r = new R("Select Bill Status = Settled and click Search, then clear it",
                "Request carries BillStatus=1 (Settled)");
        setSelect2("BillList.BillStatus", "Settled");
        lob.waitForHttpIdle(20000);
        Call c = searchAndGet();
        setSelect2("BillList.BillStatus", "--Select--");
        if (c == null) return r.set("No search request", false);
        noteRows("Bill Status Settled", c);
        return r.set("BillStatus sent as " + raw(c, "BillStatus") + "; " + gridLine(c), "1".equals(field(c, "BillStatus")));
    }

    private R payorCategory() {
        R r = new R("Select Payor Category = Self Billed (Payor list reloads), click Search, then clear Payor Category",
                "The Payor list is reloaded for the category; the request is sent; clearing the category restores the Payor list without errors");
        int before = lob.payorListSize();
        String first = lob.options("BillList.payercategoryid").stream().filter(o -> !o.startsWith("--")).findFirst().orElse("");
        if (first.isEmpty()) return r.set("Payor Category list is empty", "SKIPPED");
        setSelect2("BillList.payercategoryid", first);
        lob.waitForHttpIdle(20000);
        int after = lob.payorListSize();
        Call c = searchAndGet();
        int e0 = consoleErrors.size();
        setSelect2("BillList.payercategoryid", "--Select--");
        lob.waitForHttpIdle(20000);
        page.waitForTimeout(500);
        List<String> newErrors = new ArrayList<>(consoleErrors.subList(e0, consoleErrors.size()));
        int restored = lob.payorListSize();
        boolean sentCategory = c != null && c.body.matches("(?is).*\"payer?categoryid\"\\s*:\\s*\"?\\d.*");
        observation("LB-09", "Payor Category is not part of the search request — it only narrows the Payor list; the search filters by the chosen Payor (sponsorid)"
                + (c == null ? "" : ", sent as " + raw(c, "sponsorid")));
        if (!newErrors.isEmpty()) defect("LB-09", "Low", "Clearing Payor Category raises a script error (" + trim(newErrors.get(0), 110) + ")");
        return r.set("\"" + first + "\": Payor list " + before + " → " + after + " entries; request " + (c == null ? "not sent" : (sentCategory ? "carries the category" : "has no category field, sponsorid " + raw(c, "sponsorid")))
                + "; cleared: Payor list " + restored + " entries" + (newErrors.isEmpty() ? ", no script error" : ", SCRIPT ERROR: " + trim(newErrors.get(0), 160)),
                c != null && after >= 0 && newErrors.isEmpty());
    }

    private R location() {
        R r = new R("Clear Location (--Select--), click Search, then restore the default Location",
                "The request reflects the choice (LocationID null when cleared, the default id otherwise)");
        String original = lob.selectedText("BillList.LocationID");
        setSelect2("BillList.LocationID", "--Select--");
        Call c = searchAndGet();
        setSelect2("BillList.LocationID", original);
        lob.waitForHttpIdle(20000);
        String back = s(lob.model().get("LocationID"));
        if (c == null) return r.set("No search request", false);
        String sent = raw(c, "LocationID");
        if ("null".equals(sent) || sent.isEmpty()) observation("LB-10", "Search is allowed with no Location selected (LocationID null) — confirm whether all locations should then be listed");
        return r.set("Cleared → LocationID sent as " + (sent.isEmpty() ? "(absent)" : sent) + "; restored \"" + original + "\" → model " + back + "; " + gridLine(c),
                ("null".equals(sent) || sent.isEmpty()) && back.equals(s(defaults.get("LocationID"))));
    }

    private R department() {
        R r = new R("Select the first Department in the list, click Search, then set it back to All",
                "Request carries departmentid = the selected department's id");
        String dep = lob.options("BillList.departmentid").stream().filter(o -> !o.equalsIgnoreCase("All") && !o.startsWith("--")).findFirst().orElse("");
        if (dep.isEmpty()) return r.set("Department list empty", "SKIPPED");
        String id = optionId("BillList.departmentid", dep);
        setSelect2("BillList.departmentid", dep);
        Call c = searchAndGet();
        setSelect2("BillList.departmentid", "All");
        if (c == null) return r.set("No search request", false);
        noteRows("Department " + dep, c);
        return r.set("\"" + dep + "\" (id " + id + ") → departmentid sent as " + raw(c, "departmentid") + "; " + gridLine(c), id.equals(field(c, "departmentid")));
    }

    private R consolidatedPayor() {
        R r = new R("Select the first Consolidated Payor in the list and click Search, then clear it",
                "The selected Consolidated Payor id reaches the search request (or the search is limited to it)");
        String cp = lob.options("BillList.consolidsponsorid").stream().filter(o -> !o.startsWith("--")).findFirst().orElse("");
        if (cp.isEmpty()) return r.set("Consolidated Payor list empty", "SKIPPED");
        String id = optionId("BillList.consolidsponsorid", cp);
        setSelect2("BillList.consolidsponsorid", cp);
        String model = s(lob.model().get("consolidsponsorid"));
        Call c = searchAndGet();
        setSelect2("BillList.consolidsponsorid", "--Select--");
        if (c == null) return r.set("No search request", false);
        boolean carried = c.body.matches("(?s).*:\\s*\"?" + Pattern.quote(id) + "\"?\\s*[,}].*") && !id.equals(field(c, "LocationID")) && !id.equals(field(c, "departmentid"));
        if (!carried) defect("LB-12", "Medium", "Consolidated Payor filter is not sent with the search (BillList.consolidsponsorid is only used by Generate E-Invoice), so choosing one does not change the result");
        return r.set("\"" + cp + "\" (id " + id + ", model " + model + ") → " + (carried ? "id present in the request" : "NOT in the request: " + trimBody(c.body)) + "; " + gridLine(c), carried);
    }

    private R combined() {
        R r = new R("Search IPD + Bill Status Unsettled + " + yearStart() + " – today; then OPD + Bill Status cleared + Department All and search again",
                "1st request carries all three filters; 2nd request carries the new values and no stale value from the 1st");
        lob.choose(Radio.IPD);
        setSelect2("BillList.BillStatus", "Unsettled");
        lob.typeDate(SEL_FROM(), yearStart());
        Call a = searchAndGet();
        lob.choose(Radio.OPD);
        setSelect2("BillList.BillStatus", "--Select--");
        setSelect2("BillList.departmentid", "All");
        lob.typeDate(SEL_FROM(), wideFrom);
        Call b = searchAndGet();
        List<String> bad = new ArrayList<>();
        if (a == null || b == null) return r.set("Search request missing (" + (a == null ? "1st" : "2nd") + ")", false);
        if (!"1".equals(field(a, "Opd_Ipd"))) bad.add("1st Opd_Ipd " + raw(a, "Opd_Ipd"));
        if (!"2".equals(field(a, "BillStatus"))) bad.add("1st BillStatus " + raw(a, "BillStatus"));
        if (!yearStart().equals(field(a, "FromDate"))) bad.add("1st FromDate " + field(a, "FromDate"));
        if (!"0".equals(field(b, "Opd_Ipd"))) bad.add("2nd Opd_Ipd " + raw(b, "Opd_Ipd"));
        if (!field(b, "BillStatus").isEmpty() && !"null".equals(raw(b, "BillStatus"))) bad.add("2nd BillStatus STALE " + raw(b, "BillStatus"));
        if (!field(b, "departmentid").isEmpty() && !"null".equals(raw(b, "departmentid"))) bad.add("2nd departmentid STALE " + raw(b, "departmentid"));
        if (!wideFrom.equals(field(b, "FromDate"))) bad.add("2nd FromDate " + field(b, "FromDate"));
        noteRows("IPD + Unsettled + " + today.getYear(), a);
        return r.set("1st: Opd_Ipd " + raw(a, "Opd_Ipd") + ", BillStatus " + raw(a, "BillStatus") + ", From " + field(a, "FromDate") + " (" + gridLine(a) + "); 2nd: Opd_Ipd "
                + raw(b, "Opd_Ipd") + ", BillStatus " + raw(b, "BillStatus") + ", departmentid " + raw(b, "departmentid") + ", From " + field(b, "FromDate")
                + (bad.isEmpty() ? " — no stale values" : " — PROBLEMS: " + String.join("; ", bad)), bad.isEmpty());
    }

    private R emptyResults() {
        R r = new R("Search a future range (01/01 – 31/01 of next year) where no bill can exist",
                "0 rows and a clear \"no records found\" message");
        String f = LocalDate.of(today.getYear() + 1, 1, 1).format(DMY), t = LocalDate.of(today.getYear() + 1, 1, 31).format(DMY);
        lob.typeDate(SEL_TO(), t);
        lob.typeDate(SEL_FROM(), f);
        lob.spyAlerts();
        Call c = searchAndGet();
        List<String> alerts = lob.spiedAlerts();
        String msg = (lob.emptyMessage() + " " + String.join(" ", alerts)).trim();
        int rows = intv(lob.gridInfo().get("rows"));
        lob.typeDate(SEL_FROM(), wideFrom);
        lob.typeDate(SEL_TO(), today.format(DMY));
        if (rows == 0 && msg.isEmpty()) defect("LB-14", "Low", "A search without results shows an empty grid and no \"No records found\" message");
        return r.set(f + " – " + t + ": " + rows + " row(s), server " + (c == null ? "—" : secs(c.ms)) + (msg.isEmpty() ? ", NO message shown — the grid is simply blank" : ", message \"" + msg + "\""),
                rows == 0 && !msg.isEmpty());
    }

    // =================================================================================================================
    // E. Grid
    // =================================================================================================================

    private static final List<String> KEY_COLUMNS = List.of("Bill Date", "Bill No.", "MRN", "Patient Name", "Payor", "Total Bill Amount",
            "Discount Amount", "Self Amount", "Company Amount", "Self Balance Amt", "Company Balance Amt");

    private R gridColumns() {
        R r = new R("Read the grid's visible column definitions and, when rows exist, compare the shown rows with the response",
                "The key bill columns are present " + KEY_COLUMNS + "; shown rows = response rows (up to the page size)");
        Call c = searchAndGet();
        Map<String, Object> gi = lob.gridInfo();
        @SuppressWarnings("unchecked") List<String> headers = (List<String>) gi.getOrDefault("headers", List.of());
        List<String> missing = new ArrayList<>();
        for (String k : KEY_COLUMNS) if (!headers.contains(k)) missing.add(k);
        Set<String> dup = new LinkedHashSet<>(), seen = new HashSet<>();
        for (String h : headers) if (!seen.add(h)) dup.add(h);
        int rows = intv(gi.get("rows")), rendered = intv(gi.get("rendered")), size = intv(gi.get("pageSize"));
        String data = rows == 0 ? "no rows returned — row-to-response comparison not possible"
                : "response rows " + rows + ", rendered " + rendered + (rendered == Math.min(rows, size > 0 ? size : rows) ? " (match)" : " (MISMATCH)");
        lob.scrollTo("#" + com.kpj.pages.Inventory_page.POS_page.ListOfBills.GRID);
        boolean ok = missing.isEmpty() && dup.isEmpty() && (rows == 0 || rendered == Math.min(rows, size > 0 ? size : rows));
        return r.set(headers.size() + " visible columns: " + head(headers, 40) + (missing.isEmpty() ? "" : " — MISSING " + missing) + (dup.isEmpty() ? "" : " — DUPLICATED " + dup)
                + "; " + data + (c == null ? "" : " (" + gridLine(c) + ")"), ok);
    }

    private R pagination() {
        R r = new R("When the server reports more records than one page, go to the next page",
                "Page 2 is fetched from the server (PageIndex advances) and shows different bills");
        Map<String, Object> gi = lob.gridInfo();
        int rc = intv(gi.get("recordCount")), size = intv(gi.get("pageSize")), rows = intv(gi.get("rows"));
        if (rows == 0 || rc <= Math.max(size, rows)) return r.set("Record count " + rc + ", rows " + rows + ", page size " + size + " — one page or none; pagination not exercisable with this data", "SKIPPED");
        int n0 = searchCalls.size();
        lob.pager("pageNextPageClick");
        Call c = searchCalls.size() > n0 ? searchCalls.get(searchCalls.size() - 1) : null;
        int rows2 = intv(lob.gridInfo().get("rows"));
        lob.pager("pageFirstPageClick");
        return r.set("Record count " + rc + "; next page " + (c == null ? "made NO server call" : "fetched PageIndex " + field(c, "PageIndex")) + ", " + rows2 + " row(s)",
                c != null && !"0".equals(field(c, "PageIndex")) && rows2 > 0);
    }

    private R sorting() {
        R r = new R("Click the Bill Date and Bill No. headers when at least 2 rows are shown", "Rows are re-ordered by the clicked column");
        int rows = intv(lob.gridInfo().get("rows"));
        if (rows < 2) return r.set(rows + " row(s) — sorting not exercisable with this data (grid sorting is " + (Boolean.TRUE.equals(lob.gridInfo().get("sortable")) ? "enabled" : "disabled") + ")", "SKIPPED");
        List<String> before = lob.shownColumn("Bill No.");
        lob.clickHeader("Bill No.");
        List<String> asc = lob.shownColumn("Bill No.");
        List<String> sorted = new ArrayList<>(asc);
        Collections.sort(sorted);
        lob.clickHeader("Bill Date");
        List<String> dates = lob.shownColumn("Bill Date");
        return r.set("Bill No. before " + head(before, 5) + ", after click " + head(asc, 5) + (asc.equals(sorted) ? " (ascending)" : " (NOT ascending)") + "; Bill Date after click " + head(dates, 5),
                asc.equals(sorted));
    }

    // =================================================================================================================
    // F. UI
    // =================================================================================================================

    private R labels() {
        R r = new R("Check visible labels for duplicated text, the accessible names select2 generates for each dropdown, duplicate element ids and the position of the Consolidated Payor filter",
                "Each filter has one clear label; no concatenated accessible names; no duplicate ids; all filters sit together above the grid");
        List<String> visible = lob.visibleLabels();
        List<String> visDup = new ArrayList<>();
        for (String l : visible) if (l.matches("(?i)^(.{3,})\\1$")) visDup.add(l);
        Map<String, String> off = lob.offscreenLabels();
        List<String> concatenated = new ArrayList<>();
        off.forEach((k, v) -> { if (v.matches("(?i)^(.{3,})\\1$") || v.split("(?<=[a-z])(?=[A-Z])").length > 2) concatenated.add("\"" + v + "\""); });
        Map<String, Integer> dupIds = lob.duplicateIds();
        Map<String, int[]> pos = lob.labelPositions();
        int gridTop = lob.gridTop();
        int[] cp = pos.get("Consolidated Payor");
        boolean misplaced = cp != null && gridTop > 0 && cp[1] > gridTop;
        lob.scrollTo("select[ng-model='BillList.consolidsponsorid']");
        if (!concatenated.isEmpty() || !dupIds.isEmpty())
            defect("LB-18", "Low", "Duplicate element ids " + dupIds.keySet() + " make select2 build concatenated accessible labels (e.g. " + head(new LinkedHashSet<>(concatenated), 2)
                    + "); a screen reader announces these instead of the field name. On screen the labels look normal");
        if (!visDup.isEmpty()) defect("LB-18", "Low", "Visible labels with duplicated text: " + visDup);
        if (misplaced) defect("LB-18", "Low", "The Consolidated Payor filter is placed below the grid (y " + cp[1] + " vs grid top " + gridTop + "), away from the other filters");
        return r.set("Visible labels " + (visDup.isEmpty() ? "OK (no duplicated text)" : "DUPLICATED " + visDup)
                + "; accessible names: " + (concatenated.isEmpty() ? "OK" : String.join(", ", new LinkedHashSet<>(concatenated)))
                + "; duplicate ids " + (dupIds.isEmpty() ? "none" : dupIds)
                + "; Consolidated Payor label at y " + (cp == null ? "?" : cp[1]) + ", grid top y " + gridTop + (misplaced ? " — placed BELOW the grid" : ""),
                visDup.isEmpty() && concatenated.isEmpty() && dupIds.isEmpty() && !misplaced);
    }

    private R buttonLabels() {
        R r = new R("List the visible buttons with their labels and enabled state (none of the action buttons is clicked)",
                "User-facing labels only (no developer names such as SubmitTextOutput / SubmitFTP); icon-only buttons have a tooltip");
        List<Map<String, Object>> bs = lob.buttons();
        List<String> devNames = new ArrayList<>(), noName = new ArrayList<>(), enabledActions = new ArrayList<>(), all = new ArrayList<>();
        for (Map<String, Object> b : bs) {
            String t = s(b.get("text")), title = s(b.get("title"));
            boolean en = Boolean.TRUE.equals(b.get("enabled"));
            all.add((t.isEmpty() ? "[" + title + "]" : t) + (en ? "" : " (disabled)"));
            if (t.matches("[A-Z][a-z]+(?:[A-Z][A-Za-z]*)+") && !t.contains(" ")) devNames.add(t);
            if (t.isEmpty() && title.isEmpty()) noName.add(s(b.get("ngClick")));
            if (en && com.kpj.pages.Inventory_page.POS_page.ListOfBills.FORBIDDEN_BUTTONS.contains(t)) enabledActions.add(t);
        }
        int rows = intv(lob.gridInfo().get("rows"));
        if (!devNames.isEmpty()) defect("LB-19", "Low", "Developer-style button labels shown to users: " + devNames);
        if (rows == 0 && !enabledActions.isEmpty()) observation("LB-19", "Action buttons enabled while the grid is empty (not clicked): " + enabledActions);
        lob.scrollTo("button[ng-click='IUDFTP();']");
        return r.set("Buttons: " + head(all, 20) + (devNames.isEmpty() ? "" : " — DEVELOPER LABELS " + devNames) + (noName.isEmpty() ? "" : " — NO NAME " + noName)
                + (enabledActions.isEmpty() ? "" : "; enabled with " + rows + " rows: " + enabledActions), devNames.isEmpty() && noName.isEmpty());
    }

    // =================================================================================================================
    // G. API / network
    // =================================================================================================================

    private R iudAttachment() {
        R r = new R("Monitor POST " + IUD_ATTACHMENT + " while the screen opens (the guard blocks it) and list the bill search API seen",
                "Record when it fires, method, payload, status and time, and whether it changes data — MANUAL confirmation needed because the call is blocked");
        List<String> seen = new ArrayList<>();
        for (String[] b : blockedWrites) if (b[1].equalsIgnoreCase(IUD_ATTACHMENT)) seen.add(b[0] + " " + b[1] + " " + b[2] + (b[3].isEmpty() ? "" : " (+" + b[3] + " ms after the menu click" + ")"));
        Set<String> statuses = new TreeSet<>();
        for (Call c : searchCalls) statuses.add(String.valueOf(c.status));
        String search = "Bill search API: POST /api/BillListSearch/Fetch (ExecFlag GetBillList), " + searchCalls.size() + " call(s), HTTP " + statuses;
        if (seen.isEmpty()) return r.set("IUDAttachment was NOT called while the screen opened. " + search, "MANUAL");
        observation("LB-20", "The screen calls the write endpoint IUDAttachment (factory method SaveBillingFiles, ExecFlag CheckCreditAdminEscalation) automatically on every open; "
                + "the code only reads the result to show a warning, but whether the server writes anything (log / internal message) must be confirmed by the developers");
        return r.set("Fired automatically on page load: " + head(seen, 3) + ". Blocked by the read-only guard, so status and response time were NOT measured; "
                + "the controller only reads Response.data[0].ResultStatus/Message to show a warning. Whether it changes data cannot be proven client-side — confirm with the developers. " + search, "MANUAL");
    }

    private R performance() {
        R r = new R("Server time of every BillListSearch/Fetch call in this run", "Median server time ≤ " + maxSearchMs + " ms");
        List<Long> ms = new ArrayList<>();
        int zero = 0;
        for (Call c : searchCalls) if (c.ms >= 0) ms.add(c.ms);
        for (int n : rowsFound.values()) if (n == 0) zero++;
        if (ms.isEmpty()) return r.set("No search timings captured", false);
        List<Long> sorted = new ArrayList<>(ms);
        Collections.sort(sorted);
        long median = sorted.get(sorted.size() / 2), max = sorted.get(sorted.size() - 1), min = sorted.get(0);
        boolean ok = median <= maxSearchMs;
        if (!ok) defect("LB-21", "Medium", "Every search takes " + secs(min) + " – " + secs(max) + " (median " + secs(median) + ") on the server, including searches that return no rows");
        return r.set(ms.size() + " searches: min " + secs(min) + ", median " + secs(median) + ", max " + secs(max) + " (" + zero + " of " + rowsFound.size() + " recorded searches returned 0 rows)", ok);
    }

    private R consoleCheck() {
        R r = new R("Console errors, uncaught script errors and failed /api/ calls during the run (the guard's own aborts excluded)",
                "No console error and no failed API call");
        for (String f : apiFailures) if (f.contains("UserDetailsModified")) { defect("LB-22", "Low", "POST /api/UserDetailsModified/Fetch answers HTTP 500 every time the screen opens"); break; }
        return r.set("Console: " + (consoleErrors.isEmpty() ? "none" : consoleErrors.size() + " — " + head(consoleErrors, 6)) + "; API failures: "
                + (apiFailures.isEmpty() ? "none" : apiFailures.size() + " — " + head(apiFailures, 6)), consoleErrors.isEmpty() && apiFailures.isEmpty());
    }

    private R safety() {
        R r = new R("Write-type API calls stopped by the guard, downloads, and the action buttons the test must not use",
                "Only the expected IUDAttachment check was blocked; no other write attempted; no download; no action button clicked");
        List<String> unexpected = new ArrayList<>();
        for (String[] b : blockedWrites) if (!(b[1].equalsIgnoreCase(IUD_ATTACHMENT) && b[2].contains("CheckCreditAdminEscalation"))) unexpected.add(b[0] + " " + b[1] + " " + trim(b[2], 120));
        return r.set("Blocked: " + blockedWrites.size() + " (" + (unexpected.isEmpty() ? "all = IUDAttachment CheckCreditAdminEscalation" : "UNEXPECTED: " + head(unexpected, 5)) + "); downloads: "
                + (downloads.isEmpty() ? "none" : downloads) + "; buttons never clicked: " + com.kpj.pages.Inventory_page.POS_page.ListOfBills.FORBIDDEN_BUTTONS,
                unexpected.isEmpty() && downloads.isEmpty());
    }

    private R opdIpdTypes() {
        R r = new R("Compare the JSON type of Opd_Ipd in every search request of this run", "Opd_Ipd always sent with the same type (a number)");
        Map<String, Set<String>> byType = new LinkedHashMap<>();
        for (Call c : searchCalls) {
            String v = raw(c, "Opd_Ipd");
            String type = v.isEmpty() ? "absent" : v.equals("null") ? "null" : v.startsWith("\"") ? "string" : "number";
            byType.computeIfAbsent(type, k -> new TreeSet<>()).add(v.isEmpty() ? "(absent)" : v);
        }
        boolean ok = byType.size() == 1 && byType.containsKey("number");
        if (!ok) defect("LB-24", "Low", "Opd_Ipd is sent inconsistently: " + byType + " (number on first load, string after a radio is clicked, null for Pharmacy Credit Invoice)");
        return r.set("Types seen: " + byType, ok);
    }

    private R dataObservation() {
        R r = new R("Summarise the rows each search returned (data, not a functional check)",
                "Informational: whether billing records exist for the searched periods; cross-check with Inventory > POS > Item Sale List (credit sales not yet billed)");
        int total = rowsFound.values().stream().mapToInt(Integer::intValue).sum();
        observation("LB-25", total == 0 ? "No pharmacy bill or credit invoice was returned for any searched period — a data observation, not a defect, until Item Sale List / billing confirms bills exist" : "Rows were returned: " + rowsFound);
        return r.set("Rows per search: " + rowsFound + ". " + (total == 0 ? "No bills exist in the searched periods on this environment. If Item Sale List shows credit sales with isbilled=false, no bill/invoice has been generated yet — consistent with an empty list." : ""),
                "MANUAL");
    }

    // =================================================================================================================
    // helpers
    // =================================================================================================================

    private void tc(String name, Body b) {
        System.out.println("[ListOfBills] --- " + name + " ---");
        long t0 = System.currentTimeMillis();
        R r;
        try { r = b.run(); }
        catch (Throwable e) { r = new R("Run " + name, "Case completes").set("ERROR: " + String.valueOf(e.getMessage()).split("\\R")[0], false); }
        step(page, name, r.desc, r.expected, r.actual + " [execution time " + secs(System.currentTimeMillis() - t0) + "]", r.status);
    }

    /** Click Search; return the BillListSearch/Fetch call it made (null when none was sent). */
    private Call searchAndGet() {
        int n0 = searchCalls.size();
        lob.search();
        for (int i = 0; i < 20 && searchCalls.size() > n0 && searchCalls.get(searchCalls.size() - 1).ms < 0; i++) page.waitForTimeout(250);
        return searchCalls.size() > n0 ? searchCalls.get(searchCalls.size() - 1) : null;
    }

    private String gridLine(Call c) {
        Map<String, Object> gi = lob.gridInfo();
        return intv(gi.get("rows")) + " row(s), record count " + gi.get("recordCount") + (c == null ? "" : ", server " + secs(c.ms) + ", HTTP " + c.status);
    }

    private void noteRows(String label, Call c) { if (c != null) rowsFound.put(label, intv(lob.gridInfo().get("rows"))); }

    private String optionId(String ngModel, String text) {
        Object r = page.evaluate("([ng,t]) => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); const o=s&&[...s.options].find(o=>o.text.trim()===t); return o?o.value:''; }",
                Arrays.asList(ngModel, text));
        return String.valueOf(r).replaceAll("^\\D*", "");
    }

    /** Raw JSON token of a top-level field ("1", 1, null, true …), "" when absent. */
    private static String raw(Call c, String key) {
        if (c == null) return "";
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(null|true|false|-?\\d+(?:\\.\\d+)?|\"(?:[^\"\\\\]|\\\\.)*\")").matcher(c.body);
        return m.find() ? m.group(1) : "";
    }

    /** Field value with quotes removed ("" for absent or null). */
    private static String field(Call c, String key) {
        String v = raw(c, key);
        if (v.equals("null")) return "";
        return v.startsWith("\"") ? v.substring(1, v.length() - 1) : v;
    }

    private void attachMonitors() {
        page.onConsoleMessage(m -> {
            if (!"error".equals(m.type())) return;
            String txt = m.text();
            if (txt.contains("net::ERR_FAILED") && !blockedWrites.isEmpty()) return;   // the guard's own abort
            consoleErrors.add(route() + " " + trim(txt, 220));
        });
        page.onPageError(e -> consoleErrors.add(route() + " page error: " + trim(String.valueOf(e), 220)));
        page.onRequest(q -> {
            if (!API_URL.matcher(q.url()).find()) return;
            started.put(q, System.currentTimeMillis());
            if (loading) loadCalls.add(q.method() + " " + path(q.url()));
            if (path(q.url()).toLowerCase().equals(SEARCH_PATH)) {
                Call c = new Call(String.valueOf(q.postData()), System.currentTimeMillis());
                open.put(q, c);
                searchCalls.add(c);
            }
        });
        page.onResponse(resp -> {
            Request q = resp.request();
            if (!API_URL.matcher(resp.url()).find()) return;
            Call c = open.remove(q);
            Long st = started.remove(q);
            if (c != null) { c.status = resp.status(); c.ms = System.currentTimeMillis() - c.start; }
            if (resp.status() >= 400) apiFailures.add(route() + " " + q.method() + " " + path(resp.url()) + " → HTTP " + resp.status() + (st == null ? "" : " (" + (System.currentTimeMillis() - st) + " ms)"));
        });
        page.onRequestFailed(q -> {
            if (!API_URL.matcher(q.url()).find()) return;
            started.remove(q);
            boolean ours = blockedWrites.stream().anyMatch(b -> b[1].equalsIgnoreCase(path(q.url())));
            if (!ours) apiFailures.add(route() + " " + q.method() + " " + path(q.url()) + " → " + q.failure());
        });
        page.onDownload(d -> {
            downloads.add(d.suggestedFilename());
            try { d.cancel(); } catch (Exception ignore) { }
        });
    }

    /** Abort write-type /api/ calls (non-GET whose last segment is not a read verb) for the whole test. */
    private void installReadOnlyGuard() {
        page.route(API_ROUTE, route -> {
            String m = route.request().method(), u = route.request().url();
            if (!"GET".equalsIgnoreCase(m) && !"OPTIONS".equalsIgnoreCase(m) && !READ_CALL.matcher(path(u)).find()) {
                long after = navClickAt > 0 ? System.currentTimeMillis() - navClickAt : -1;
                blockedWrites.add(new String[]{m, path(u), trim(String.valueOf(route.request().postData()), 300), loading && after >= 0 ? String.valueOf(after) : ""});
                System.out.println("[ListOfBills] read-only guard blocked " + m + " " + path(u));
                route.abort();
            } else route.resume();
        });
    }

    private boolean credentialsFromEnvironment() {
        boolean u = present("devhis.user", "DEVHIS_USER"), p = present("devhis.pass", "DEVHIS_PASS"), url = present("devhis.url", "DEVHIS_URL");
        if (u && p) {
            if (!url) addSummary("Note", "DEVHIS_URL not set — the framework default " + BASE + " was used");
            return true;
        }
        String miss = ((u ? "" : "DEVHIS_USER ") + (p ? "" : "DEVHIS_PASS")).trim();
        step("LB-00 Credentials from environment", "Read the test account from DEVHIS_USER / DEVHIS_PASS", "Both variables set",
                "Missing: " + miss + " — set them and re-run. Login was NOT attempted (the framework's built-in account is never used by this test).", "FAIL");
        addSummary("Result", "NOT RUN — credentials not supplied through the environment (" + miss + ")");
        return false;
    }

    private static boolean present(String prop, String env) {
        String a = System.getProperty(prop), b = System.getenv(env);
        return (a != null && !a.isBlank()) || (b != null && !b.isBlank());
    }

    private void defect(String id, String severity, String text) {
        String d = "[" + severity + "] " + id + " " + text;
        if (!defects.contains(d)) defects.add(d);
    }

    private void observation(String id, String text) {
        String d = id + " " + text;
        if (!observations.contains(d)) observations.add(d);
    }

    private void writeSummary() {
        addSummary("Application URL", BASE + "/" + com.kpj.pages.Inventory_page.POS_page.ListOfBills.ROUTE);
        addSummary("Run date", today.toString());
        addSummary("Wide search range", wideFrom + " – " + today.format(DMY));
        addSummary("Application defects found", defects.isEmpty() ? "None" : String.valueOf(defects.size()));
        int i = 1;
        for (String d : defects) addSummary("Defect " + (i++), d);
        i = 1;
        for (String o : observations) addSummary("Observation " + (i++), o);
        addSummary("Search calls", searchCalls.size() + " × POST /api/BillListSearch/Fetch");
        addSummary("Console errors", consoleErrors.isEmpty() ? "None" : consoleErrors.size() + " — " + head(consoleErrors, 4));
        addSummary("API failures", apiFailures.isEmpty() ? "None" : apiFailures.size() + " — " + head(apiFailures, 4));
        List<String> bw = new ArrayList<>();
        for (String[] b : blockedWrites) bw.add(b[0] + " " + b[1] + " " + trim(b[2], 80));
        addSummary("Write calls blocked (read-only guard)", bw.isEmpty() ? "None" : head(bw, 6));
        addSummary("Data modified", "No — " + blockedWrites.size() + " write-type call(s) aborted before reaching the server, " + downloads.size()
                + " download(s) cancelled, no Submit / Generate / Export / Save button clicked");
        addSummary("Screenshots", "Embedded for every row; FAIL rows also write test-output/failures/Inventory_POS_ListOfBills_<row>.txt");
    }

    private static String SEL_FROM() { return com.kpj.pages.Inventory_page.POS_page.ListOfBills.SEL_FROM; }
    private static String SEL_TO() { return com.kpj.pages.Inventory_page.POS_page.ListOfBills.SEL_TO; }
    private String yearStart() { return LocalDate.of(today.getYear(), 1, 1).format(DMY); }

    private String route() {
        try { String u = page.url(); int i = u.indexOf('#'); return "[" + (i >= 0 ? u.substring(i) : path(u)) + "]"; } catch (Exception e) { return "[?]"; }
    }

    private static LocalDate parse(String s) { try { return LocalDate.parse(s.trim(), DMY); } catch (Exception e) { return null; } }
    private static String trimBody(String b) { return trim(b.replaceAll("\"ExecFlag\":\"GetBillList\",?", ""), 260); }
    private static String secs(long ms) { return ms < 0 ? "—" : String.format(Locale.ROOT, "%.1f s", ms / 1000.0); }
    private static String s(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static int intv(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }
    private static String trim(String s, int n) { s = s == null ? "" : s.replaceAll("\\s+", " "); return s.length() > n ? s.substring(0, n) + "…" : s; }
    private static String path(String url) { return url.replaceAll("^https?://[^/]+", "").replaceAll("[?#].*$", ""); }
    private static String head(Collection<?> l, int n) {
        List<String> x = new ArrayList<>(); for (Object o : l) x.add(String.valueOf(o));
        return x.size() <= n ? String.join("; ", x) : String.join("; ", x.subList(0, n)) + "; … (+" + (x.size() - n) + " more)";
    }
}
