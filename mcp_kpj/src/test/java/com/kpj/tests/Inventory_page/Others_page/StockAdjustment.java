package com.kpj.tests.Inventory_page.Others_page;

import com.kpj.core.DevHisBase;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.opentest4j.TestAbortedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Inventory &gt; Others &gt; <b>Stock Adjustment</b> — READ-ONLY functional checks of the Stock Adjustment list screen (#/StockAdjustmentList).
 *
 * <p>Every check is its own test case (SA-01 … SA-31) and appears as its own JUnit test (a dynamic test named after the case), as its own
 * row in the HTML report {@code test-output/reports/Inventory_Others_StockAdjustment.html} (Description = test steps, Expected, Actual,
 * Status PASS / FAIL / SKIPPED / MANUAL, screenshot) and in the aggregate TSV. One login is shared by all cases; the report lists the cases
 * in ID order whatever order they ran in.</p>
 *
 * <p><b>Read-only guard:</b> any {@code /api/} call whose name or ExecFlag looks like a write (IUD / Save / Insert / Update / Delete /
 * Approve / Cancel / Submit …) is ABORTED before it reaches the server and listed in SA-27; downloads and native dialogs are cancelled.
 * New, the grid's <b>Approved</b> checkbox (it posts IUDApproveStockAdjustment at once), the grid menu's export entries and every confirm
 * box's Ok are never clicked. Cases that would need data to change are MANUAL.</p>
 *
 * <p>Run only this class:
 * {@code mvn test "-Dtest=com.kpj.tests.Inventory_page.Others_page.StockAdjustment" -Ddevhis.postrun=off}.</p>
 */
public class StockAdjustment extends DevHisBase {

    public StockAdjustment() { super("Inventory_Others_StockAdjustment"); }

    public static void main(String[] args) {
        StockAdjustment t = new StockAdjustment();
        try { t.run(); } finally { t.stop(); }
    }

    // ======================================================================= JUnit: one dynamic test per case

    @TestFactory
    Stream<DynamicTest> execute() {
        try { run(); } finally { stop(); }
        return cases.stream().sorted(Comparator.comparing(c -> c.id)).map(c -> DynamicTest.dynamicTest(c.id + " - " + c.scenario, () -> {
            switch (c.status) {
                case "PASS": break;
                case "SKIPPED": Assumptions.abort("SKIPPED: " + c.actual); break;
                case "MANUAL": throw new TestAbortedException("MANUAL: " + c.actual);
                default: Assertions.fail(c.id + " FAILED - expected: " + c.expected + " | actual: " + c.actual);
            }
        }));
    }

    // ======================================================================= case bookkeeping

    private static final class Case {
        final String id, scenario, steps, expected;
        String actual = "", status = "FAIL";
        boolean automationError = false;
        byte[] png;
        Case(String id, String scenario, String steps, String expected) { this.id = id; this.scenario = scenario; this.steps = steps; this.expected = expected; }
    }

    private static final class Outcome {
        final String status, actual;
        Outcome(String status, String actual) { this.status = status; this.actual = actual; }
    }

    private static Outcome pass(String a) { return new Outcome("PASS", a); }
    private static Outcome fail(String a) { return new Outcome("FAIL", a); }
    private static Outcome skip(String a) { return new Outcome("SKIPPED", a); }
    private static Outcome manual(String a) { return new Outcome("MANUAL", a); }
    private static Outcome verdict(boolean ok, String a) { return ok ? pass(a) : fail(a); }

    private final List<Case> cases = new ArrayList<>();
    private final List<String> toasts = Collections.synchronizedList(new ArrayList<>());

    /** Runs one case; an exception becomes a FAIL flagged as an AUTOMATION ERROR. A screenshot is taken right after the case. */
    private Case tc(String id, String scenario, String steps, String expected, Supplier<Outcome> body) {
        Case c = new Case(id, scenario, steps, expected);
        long t0 = System.currentTimeMillis();
        String cleared = "";
        try { cleared = sa.closeOverlays(); } catch (Throwable ignore) { }
        try {
            Outcome o = body.get();
            c.status = o.status;
            c.actual = o.actual;
        } catch (Throwable e) {
            c.status = "FAIL";
            StackTraceElement at = Arrays.stream(e.getStackTrace()).filter(f -> f.getClassName().startsWith("com.kpj")).findFirst().orElse(null);
            String where = at == null ? "" : " at " + at.getClassName().replaceFirst("^com\\.kpj\\.", "") + ":" + at.getLineNumber();
            boolean timeout = e.getClass().getSimpleName().contains("Timeout") || String.valueOf(e.getMessage()).contains("Timeout");
            c.actual = "AUTOMATION ERROR (not verified as an application defect): " + e.getClass().getSimpleName() + where + " - " + oneLine(String.valueOf(e.getMessage()), 400)
                    + (timeout ? " - the script waited for something that did not happen; see the screenshot" : "");
            c.automationError = true;
            try { sa.closeOverlays(); } catch (Throwable ignore) { }
        }
        try { for (String t : sa.drainAlerts()) toasts.add("[" + id + "] " + t); } catch (Throwable ignore) { }
        if (!cleared.isEmpty()) c.actual = "(before this case: " + cleared + ") " + c.actual;
        c.actual += " [execution time " + String.format(Locale.ROOT, "%.1f", (System.currentTimeMillis() - t0) / 1000.0) + " s]";
        try { c.png = page.screenshot(new Page.ScreenshotOptions().setTimeout(10000)); } catch (Throwable ignore) { }
        cases.add(c);
        System.out.printf("  %-6s [%-7s] %s%n", id, c.status, scenario);
        return c;
    }

    // ======================================================================= monitoring (API / console / page errors / redirects)

    private static final class ApiCall {
        final String method, path, route, body; final int status; final long ms; final String failure;
        ApiCall(String method, String path, int status, long ms, String route, String failure, String body) {
            this.method = method; this.path = path; this.status = status; this.ms = ms; this.route = route; this.failure = failure; this.body = body; }
    }

    private final List<ApiCall> api = Collections.synchronizedList(new ArrayList<>());
    private final List<String> consoleErrors = Collections.synchronizedList(new ArrayList<>());
    private final List<String> pageErrors = Collections.synchronizedList(new ArrayList<>());
    private final List<String> navigations = Collections.synchronizedList(new ArrayList<>());
    private final List<String> blockedWrites = Collections.synchronizedList(new ArrayList<>());
    private final List<String> downloads = Collections.synchronizedList(new ArrayList<>());
    private final List<String> nativeDialogs = Collections.synchronizedList(new ArrayList<>());
    private final Map<Request, Long> started = Collections.synchronizedMap(new IdentityHashMap<>());

    /** A call that would change data: by its endpoint name, or by the ExecFlag it carries. */
    private static final Pattern WRITE_URL = Pattern.compile("(?i)/api/[^?]*?/(IUD|Save|Insert|Update|Delete|Remove|Approve|Reject|Cancel|Submit|Create|Post)[A-Za-z]*(\\?|$)");
    private static final Pattern WRITE_FLAG = Pattern.compile("(?i)\"ExecFlag\"\\s*:\\s*\"[^\"]*(Insert|Update|Delete|Save|Approve|Cancel|Submit)[^\"]*\"");

    private static String route(String url) {
        int i = url.indexOf('#');
        return i >= 0 ? url.substring(i) : url.replaceFirst("^https?://[^/]+", "");
    }

    private static String path(String url) { return url.replaceFirst("^https?://[^/]+", "").replaceFirst("\\?.*$", ""); }

    private static String flag(String body) {
        java.util.regex.Matcher m = Pattern.compile("\"ExecFlag\"\\s*:\\s*\"([^\"]*)\"").matcher(body == null ? "" : body);
        return m.find() ? m.group(1) : "";
    }

    private void installMonitors() {
        page.route(Pattern.compile(".*/api/.*"), r -> {
            Request q = r.request();
            String body = q.postData() == null ? "" : q.postData();
            boolean write = !"GET".equalsIgnoreCase(q.method()) && (WRITE_URL.matcher(q.url()).find() || WRITE_FLAG.matcher(body).find());
            if (write) {
                blockedWrites.add(q.method() + " " + path(q.url()) + " " + body.substring(0, Math.min(160, body.length())) + " [" + route(page.url()) + "]");
                r.abort("blockedbyclient");
            } else r.resume();
        });
        page.onRequest(q -> started.put(q, System.currentTimeMillis()));
        page.onResponse(resp -> {
            Request q = resp.request();
            String rt = q.resourceType();
            if (!resp.url().contains("/api/") && !"xhr".equals(rt) && !"fetch".equals(rt) && !"document".equals(rt) && resp.status() < 400) return;
            Long t = started.remove(q);
            api.add(new ApiCall(q.method(), path(resp.url()), resp.status(), t == null ? -1 : System.currentTimeMillis() - t, route(page.url()), "", flag(q.postData())));
        });
        page.onRequestFailed(q -> {
            Long t = started.remove(q);
            api.add(new ApiCall(q.method(), path(q.url()), 0, t == null ? -1 : System.currentTimeMillis() - t, route(page.url()), String.valueOf(q.failure()), flag(q.postData())));
        });
        page.onConsoleMessage(m -> { if ("error".equals(m.type())) consoleErrors.add("[" + route(page.url()) + "] " + oneLine(m.text(), 300)); });
        page.onPageError(e -> pageErrors.add("[" + route(page.url()) + "] " + oneLine(e, 300)));
        page.onFrameNavigated(f -> { if (f == page.mainFrame()) navigations.add(route(f.url())); });
        page.onDownload(d -> { downloads.add(d.suggestedFilename()); try { d.cancel(); } catch (Exception ignore) { } });
        page.onDialog(d -> { nativeDialogs.add(d.type() + ": " + d.message()); try { d.dismiss(); } catch (Exception ignore) { } });
    }

    private static boolean onScreenRoute(String r) { return r != null && r.startsWith(P.ROUTE); }

    // ======================================================================= the test

    /** The page object (same simple name as this class, so it is referenced through this alias). */
    private static final class P extends com.kpj.pages.Inventory_page.Others_page.StockAdjustment {
        P(Page p) { super(p); }
    }

    private P sa;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final LocalDate todayDate = LocalDate.now();
    private final String today = todayDate.format(DMY);
    private static final LocalDate WIDE_FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate FUTURE_FROM = LocalDate.of(2030, 1, 1), FUTURE_TO = LocalDate.of(2030, 1, 31);

    private static final String MG = P.MASTER_GRID;
    private static final String DG = P.DETAIL_GRID;
    private static final String FD = P.FROM_DATE;
    private static final String TD = P.TO_DATE;

    // facts gathered by earlier cases and used by later ones
    private String defaultStore = "";
    private final Map<String, String> controls = new LinkedHashMap<>();
    private P.SearchResult validSearch, allStores;
    private List<String> validRecords = new ArrayList<>();
    private final String[] selected = new String[3];   // {stock adjustment no, stockadjid, locationid}

    @Override
    protected void body() {
        String startedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        meta("Inventory - Others - Stock Adjustment", "Inventory > Others > Stock Adjustment (#/StockAdjustmentList)",
                "READ-ONLY run: write APIs are blocked; New, the grid's Approved checkbox (it approves at once), export and confirm-Ok are never clicked."
                        + " Each row below is one test case; the Description column holds its test steps. MANUAL = needs a data change or a human check;"
                        + " SKIPPED = not available on this screen or not exercisable with the data present. FAIL rows marked DEFECT are application"
                        + " findings; FAIL rows marked AUTOMATION ERROR are script problems, not application defects.");
        addSummary("Execution date / time", startedAt);
        addSummary("Environment", BASE);
        addSummary("Login user", USER);
        sa = new P(page);
        installMonitors();
        try {
            runCases();
        } finally {
            flush(startedAt);
        }
    }

    /** Wide valid search: default store, 01/01/2024 - today, status ALL — all through the screen's own controls. */
    private P.SearchResult wideSearch(String store) {
        if (store != null) {
            String e = store.isEmpty() ? sa.clearStore() : sa.pickStore(store);
            if (!e.isEmpty()) throw new IllegalStateException("Store could not be set: " + e);
        }
        String e1 = sa.pickDate(FD, WIDE_FROM), e2 = sa.pickDate(TD, todayDate);
        if (!e1.isEmpty() || !e2.isEmpty()) throw new IllegalStateException("Dates could not be picked: " + e1 + " " + e2);
        sa.setStatus(P.STATUS_ALL);
        return sa.search();
    }

    private void runCases() {
        login();

        // ---------------------------------------------------------------- loading
        tc("SA-01", "Navigate to Stock Adjustment through the Inventory menu",
                "1. Log in. 2. Open Inventory in the sidebar. 3. Open Others. 4. Click Stock Adjustment.",
                "The Stock Adjustment screen (#/StockAdjustmentList) opens from the menu with its Search button.",
                () -> {
                    String why = sa.navigateViaMenu();
                    String menu = "Inventory > Others lists: " + sa.othersMenu();
                    if (why.isEmpty()) return pass("Inventory > Others > Stock Adjustment opened " + page.url() + "; " + menu);
                    boolean recovered = sa.openViaRoute(BASE);
                    return fail("Menu navigation failed: " + why + (recovered ? " (opened via the route instead so the other cases could run)" : " (direct route also failed)") + "; " + menu);
                });

        tc("SA-02", "Verify Stock Adjustment page loads successfully",
                "1. Wait for the screen. 2. Check the APIs fired on load. 3. Read the default filter values. 4. Check both grids are empty before Search.",
                "Header, approval-level and store lists load with HTTP 2xx; From/To Date = today; Store = the user's default store; Approval Level = 1;"
                        + " both grids empty until Search.",
                () -> {
                    if (!sa.onScreen()) return fail("not on the Stock Adjustment screen (" + page.url() + ")");
                    List<ApiCall> load = new ArrayList<>(api).stream().filter(a -> onScreenRoute(a.route) && a.path.startsWith("/api/")).collect(Collectors.toList());
                    List<String> bad = load.stream().filter(a -> a.status < 200 || a.status >= 300).map(this::fmt).collect(Collectors.toList());
                    boolean header = load.stream().anyMatch(a -> a.path.equals("/api/CommonDropdown/FetchHeader") && a.status == 200);
                    boolean stores = load.stream().anyMatch(a -> a.path.equalsIgnoreCase("/api/PurchaseOrder/Fetch") && "FillStore".equals(a.body) && a.status == 200);
                    boolean levels = load.stream().anyMatch(a -> a.path.equals("/api/CommonDropdown/Fetch") && "AUTHRIZATION".equals(a.body) && a.status == 200);
                    defaultStore = sa.chosen(P.STORE);
                    String fd = sa.fieldValue(FD), td = sa.fieldValue(TD), lvl = sa.model("LevelId");
                    int m = sa.masterRows(), d = sa.detailRows();
                    boolean ok = header && stores && levels && bad.isEmpty() && today.equals(fd) && today.equals(td) && !defaultStore.isEmpty() && "1".equals(lvl) && m == 0 && d == 0;
                    return verdict(ok, "Load APIs: " + load.stream().map(a -> a.path + (a.body.isEmpty() ? "" : "(" + a.body + ")") + " " + a.status).distinct().collect(Collectors.joining(", "))
                            + (bad.isEmpty() ? "" : "; FAILED " + bad) + "; defaults: From " + fd + ", To " + td + ", Store \"" + defaultStore + "\", Approval Level " + lvl
                            + ", status radio checked: \"" + sa.checkedStatus() + "\" (none = no status sent); grid rows " + m + " / detail rows " + d);
                });

        tc("SA-03", "Verify page title and screen elements",
                "1. Read the document title. 2. Read the panel headings. 3. Read the field labels. 4. Read the colour legend.",
                "Title \"Stock Adjustment\"; panels \"List of Stock Management\" and \"Stock Management Details\"; labels From Date, To Date, Store,"
                        + " Approval Level, status, Pending, Approved, ALL; legend \"Special Order\".",
                () -> {
                    String[] t = sa.titles();
                    List<String> labels = sa.labels(), legend = sa.legend();
                    List<String> want = Arrays.asList("From Date", "To Date", "Store", "Approval Level", "status", "Pending", "Approved", "ALL");
                    List<String> missing = want.stream().filter(w -> labels.stream().noneMatch(l -> l.equalsIgnoreCase(w))).collect(Collectors.toList());
                    boolean ok = P.TITLE.equalsIgnoreCase(t[0].trim()) && t[1].contains("List of Stock Management") && t[1].contains("Stock Management Details")
                            && missing.isEmpty() && legend.contains("Special Order");
                    return verdict(ok, "Title \"" + t[0] + "\"; panels \"" + t[1] + "\"; labels " + labels + (missing.isEmpty() ? "" : " MISSING " + missing) + "; legend " + legend);
                });

        tc("SA-04", "Verify all input fields and buttons",
                "1. Check From Date, To Date, Store, Approval Level, the three status radios and Search. 2. Check both grids and the list pager."
                        + " 3. Check Print and New (New is only inspected, never clicked).",
                "Every control is visible; all are enabled except Approval Level, which is read-only on this screen.",
                () -> {
                    controls.putAll(sa.controls());
                    List<String> notOk = controls.entrySet().stream().filter(e -> e.getKey().equals("Approval Level") ? !"visible, disabled".equals(e.getValue())
                            : !"visible".equals(e.getValue())).map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.toList());
                    return verdict(notOk.isEmpty(), "Controls: " + controls + (notOk.isEmpty() ? "" : "; NOT AS EXPECTED: " + notOk)
                            + "; no Clear / Reset / View / Export button exists in the form (export lives in the grid menu)");
                });

        // Print BEFORE any record is selected (the screen keeps the last selected row for Print)
        tc("SA-17", "Verify print functionality without selecting a record",
                "1. Without selecting any row, click Print. 2. Read the message. 3. Close it with Cancel (never Ok).",
                "No report opens; a message asks the user to select a Stock Adjustment record first; the screen is usable afterwards.",
                () -> {
                    P.PrintResult p = sa.print();
                    boolean stillBlocked = !sa.confirmText().isEmpty();
                    boolean ok = p.url.isEmpty() && p.dialog.toLowerCase().contains("please select") && !stillBlocked;
                    return verdict(ok, "Print -> " + (p.url.isEmpty() ? "message \"" + p.dialog + "\"" : "OPENED " + p.url) + p.error
                            + "; closed with Cancel, box still open afterwards: " + stillBlocked);
                });

        // ---------------------------------------------------------------- dropdowns / dates
        tc("SA-05", "Verify dropdown options",
                "1. Read the Store options and open its list. 2. Check for duplicates. 3. Read the Approval Level options and try to open it."
                        + " 4. Read the list pager's page sizes. 5. Read the status radios.",
                "Store lists the stores (with -Select-) without duplicates and opens; Approval Level offers 1/2/3, holds 1 and cannot be opened;"
                        + " page sizes 25/50/75/100/125; status offers Pending / Approved / ALL.",
                () -> {
                    List<String> stores = sa.options(P.STORE), levels = sa.options(P.LEVEL), sizes = sa.pageSizes(MG);
                    int opened = sa.openDropdown(P.STORE); sa.closeDropdown();
                    int levelOpened = sa.openDropdown(P.LEVEL); sa.closeDropdown();
                    List<String> dup = dups(stores);
                    boolean ok = stores.size() > 2 && stores.get(0).matches("(?i)-*\\s*select\\s*-*") && dup.isEmpty() && opened == stores.size()
                            && levels.containsAll(Arrays.asList("1", "2", "3")) && levelOpened < 0 && "1".equals(sa.model("LevelId"))
                            && sizes.equals(Arrays.asList("25", "50", "75", "100", "125"));
                    return verdict(ok, "Store: " + stores.size() + " options (first \"" + (stores.isEmpty() ? "" : stores.get(0)) + "\"), list opened with " + opened
                            + " entries, duplicates " + dup + ", default \"" + defaultStore + "\"; Approval Level options " + levels + ", opens: " + (levelOpened >= 0)
                            + ", holds " + sa.model("LevelId") + "; page sizes " + sizes + "; status radios Pending(0) / Approved(1) / ALL(2)");
                });

        tc("SA-06", "Verify dropdown search",
                "1. Open Store. 2. Type \"pharm\" in its search box. 3. Read the list. 4. Type \"zzqx\". 5. Close the list (Escape).",
                "The list narrows to stores containing \"pharm\"; a non-matching text lists nothing and says so.",
                () -> {
                    if (sa.openDropdown(P.STORE) < 0) return fail("Store list did not open");
                    List<String> hits = sa.typeInDropdownSearch("pharm");
                    if (hits == null) { sa.closeDropdown(); return skip("Store has no search box"); }
                    List<String> none = sa.typeInDropdownSearch("zzqx");
                    String msg = sa.dropdownNoMatchText();
                    sa.closeDropdown();
                    boolean ok = !hits.isEmpty() && hits.stream().allMatch(h -> h.toLowerCase().contains("pharm")) && none != null && none.isEmpty();
                    return verdict(ok, "\"pharm\" -> " + hits + "; \"zzqx\" -> " + none + " (\"" + msg + "\"); Store still \"" + sa.chosen(P.STORE) + "\"");
                });

        tc("SA-07", "Verify date fields and date picker",
                "1. Click From Date: its calendar opens on the box's month. 2. Open the year list, choose " + WIDE_FROM.getYear()
                        + ", step back to January with ‹, click 1. 3. Click To Date and pick today. 4. Check the boxes, the screen's values and that both calendars closed.",
                "Calendars open on click, offer year and month navigation, and set the box and the screen's value to the picked date (dd/MM/yyyy).",
                () -> {
                    boolean opened = sa.openCalendar(FD);
                    String header = sa.calendarHeader();
                    String expectHeader = todayDate.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + todayDate.getYear();
                    String e1 = sa.pickDate(FD, WIDE_FROM);
                    String e2 = sa.pickDate(TD, todayDate);
                    boolean closed = !sa.calendarOpen();
                    boolean ok = opened && header.equalsIgnoreCase(expectHeader) && e1.isEmpty() && e2.isEmpty() && closed;
                    return verdict(ok, "From Date calendar opened " + opened + " on \"" + header + "\"; picked " + WIDE_FROM.format(DMY) + " -> " + (e1.isEmpty() ? "box/model "
                            + sa.fieldValue(FD) + " / " + sa.model("FromDate") : e1) + "; To Date picked " + today + " -> " + (e2.isEmpty() ? "box/model " + sa.fieldValue(TD)
                            + " / " + sa.model("ToDate") : e2) + "; calendars closed " + closed);
                });

        // ---------------------------------------------------------------- search
        tc("SA-08", "Verify search with valid filters",
                "1. Store = default store. 2. From " + WIDE_FROM.format(DMY) + ", To today (calendar). 3. Status ALL. 4. Click Search. 5. Check every row's store and date.",
                "POST /api/StockAdjustment/fetch (GetStockAdjustmentSearchList) carries the store, dates, level 1 and status 2; HTTP 200;"
                        + " every row belongs to the selected store and is dated inside the range.",
                () -> {
                    validSearch = wideSearch(defaultStore);
                    if (!validSearch.fired()) return fail("Search sent no request: " + validSearch.error);
                    validRecords = sa.masterRecords();
                    List<String> stores = sa.masterField("storename"), dates = sa.masterField("dates");
                    boolean storeOk = stores.stream().allMatch(s -> s.trim().equalsIgnoreCase(defaultStore));
                    List<String> outOfRange = dates.stream().filter(d -> { LocalDate x = parse(d); return x == null || x.isBefore(WIDE_FROM) || x.isAfter(todayDate); }).collect(Collectors.toList());
                    String storeId = sa.model("SearchStoreID");
                    boolean reqOk = validSearch.request.contains("\"fromdate\":\"" + WIDE_FROM.format(DMY) + "\"") && validSearch.request.contains("\"todate\":\"" + today + "\"")
                            && validSearch.request.contains("\"StoreID\":" + storeId) && validSearch.request.contains("\"status\":\"2\"") && validSearch.request.contains("\"approvallevel\":1");
                    if (validSearch.status == 200 && reqOk && validSearch.rows == 0) return skip("Valid search answered HTTP 200 but no Stock Adjustment exists for \""
                            + defaultStore + "\" in the range - row checks not possible. Request " + validSearch.request);
                    boolean ok = validSearch.status == 200 && reqOk && storeOk && outOfRange.isEmpty();
                    return verdict(ok, "Request " + validSearch.request + "; " + validSearch + "; stores in result " + new LinkedHashSet<>(stores)
                            + "; dates outside range " + outOfRange + "; first rows " + validRecords.stream().limit(5).collect(Collectors.toList()));
                });

        tc("SA-29", "Verify available filters in combination",
                "1. Store = -Select- (all stores), status ALL, " + WIDE_FROM.format(DMY) + " - today: Search. 2. Store = default store with the same dates and status: Search."
                        + " 3. Compare.",
                "The store-filtered result is the subset of the all-store result belonging to that store; both requests carry the dates and status.",
                () -> {
                    allStores = wideSearch("");
                    List<String> allNos = sa.masterField("stockadjustmentno"), allStoresNames = sa.masterField("storename");
                    long expected = allStoresNames.stream().filter(s -> s.trim().equalsIgnoreCase(defaultStore)).count();
                    P.SearchResult one = wideSearch(defaultStore);
                    List<String> oneNos = sa.masterField("stockadjustmentno");
                    boolean ok = allStores.status == 200 && one.status == 200 && allStores.request.contains("\"StoreID\":null") && allNos.containsAll(oneNos)
                            && oneNos.size() == expected && allStores.rows >= one.rows;
                    return verdict(ok, "All stores -> " + allStores + " (stores " + new LinkedHashSet<>(allStoresNames) + "); \"" + defaultStore + "\" -> " + one
                            + " (expected " + expected + " = all-store rows of that store); store-filtered rows all inside the all-store result: " + allNos.containsAll(oneNos));
                });

        tc("SA-31", "Verify status filter (Pending / Approved / ALL)",
                "1. Store -Select-, " + WIDE_FROM.format(DMY) + " - today. 2. Search with Pending, then Approved, then ALL. 3. Compare each result's Approved flags.",
                "Pending lists only not-approved adjustments, Approved only approved ones, ALL both; Pending + Approved = ALL.",
                () -> {
                    sa.clearStore();
                    sa.pickDate(FD, WIDE_FROM); sa.pickDate(TD, todayDate);
                    sa.setStatus(P.STATUS_PENDING); P.SearchResult pend = sa.search(); List<String> pendAppr = sa.masterField("isapproved"); List<String> pendNos = sa.masterField("stockadjustmentno");
                    sa.setStatus(P.STATUS_APPROVED); P.SearchResult appr = sa.search(); List<String> apprAppr = sa.masterField("isapproved"); List<String> apprNos = sa.masterField("stockadjustmentno");
                    sa.setStatus(P.STATUS_ALL); P.SearchResult all = sa.search();
                    boolean sent = pend.request.contains("\"status\":\"0\"") && appr.request.contains("\"status\":\"1\"") && all.request.contains("\"status\":\"2\"");
                    if (all.rows == 0) return skip("no Stock Adjustment in the range - status filter not exercisable (" + all + ")");
                    long pendApproved = pendAppr.stream().filter("true"::equals).count(), apprPending = apprAppr.stream().filter("false"::equals).count();
                    boolean ok = sent && pendApproved == 0 && apprPending == 0 && pend.rows + appr.rows == all.rows;
                    String a = "Pending -> " + pend + " (" + pendApproved + " of them approved); Approved -> " + appr + " (" + apprPending + " of them not approved); ALL -> " + all
                            + "; status sent in the requests: " + sent + "; Pending and Approved return the same list: " + pendNos.equals(apprNos);
                    if (!ok && sent) a += " — DEFECT: the status filter is sent to the server but ignored - every status returns the same adjustments";
                    return verdict(ok, a);
                });

        tc("SA-09", "Verify search with invalid or unavailable filters",
                "1. Click From Date and type letters \"ab/cd/efgh\". 2. Type digits \"31022026\". 3. Pick a store that has no Stock Adjustment (e.g. Administration),"
                        + " " + WIDE_FROM.format(DMY) + " - today, ALL, and Search.",
                "The date box does not accept typed text (calendar only); a store without adjustments returns HTTP 200 with no rows and no error.",
                () -> {
                    sa.pickDate(FD, WIDE_FROM);
                    String[] letters = sa.typeIntoDate(FD, "ab/cd/efgh");
                    String[] digits = sa.typeIntoDate(FD, "31022026");
                    boolean typingBlocked = WIDE_FROM.format(DMY).equals(letters[0]) && WIDE_FROM.format(DMY).equals(digits[0])
                            && WIDE_FROM.format(DMY).equals(letters[1]) && WIDE_FROM.format(DMY).equals(digits[1]);
                    Set<String> withData = new HashSet<>(sa.masterField("storename"));
                    if (allStores != null) withData.add(defaultStore);
                    List<String> opts = sa.options(P.STORE);
                    String empty = opts.contains("Administration") && !withData.contains("Administration") ? "Administration"
                            : opts.stream().filter(o -> !o.matches("(?i)-*\\s*select\\s*-*") && !withData.contains(o) && !o.toLowerCase().contains("pharm")).findFirst().orElse("");
                    if (empty.isEmpty()) return skip("no store without data found to test an unavailable filter; typing blocked " + typingBlocked);
                    P.SearchResult s = wideSearch(empty);
                    List<String> msgs = sa.drainAlerts(); toasts.addAll(msgs);
                    int pe = (int) pageErrors.stream().filter(e -> e.startsWith("[" + P.ROUTE)).count();
                    boolean ok = typingBlocked && s.status == 200 && s.rows == 0 && msgs.stream().noneMatch(m -> m.toLowerCase().contains("error"));
                    return verdict(ok, "Typed \"ab/cd/efgh\" -> box \"" + letters[0] + "\" / model \"" + letters[1] + "\"; typed \"31022026\" -> box \"" + digits[0] + "\" / model \""
                            + digits[1] + "\" (" + (typingBlocked ? "keyboard entry not accepted - calendar only" : "TYPING ACCEPTED") + "); store \"" + empty + "\" -> " + s
                            + "; messages " + msgs + "; page errors on screen so far " + pe);
                });

        tc("SA-10", "Verify search with empty filters",
                "1. Store -Select-. 2. Clear From Date and To Date (Ctrl+A, Backspace). 3. Status ALL. 4. Click Search.",
                "Either a message asks for the dates, or the search completes without error (HTTP 200, no script error).",
                () -> {
                    sa.clearStore();
                    String[] f = sa.clearDate(FD), t = sa.clearDate(TD);
                    sa.setStatus(P.STATUS_ALL);
                    int pe = pageErrors.size();
                    P.SearchResult s = sa.search(8000);
                    List<String> msgs = sa.drainAlerts(); toasts.addAll(msgs);
                    boolean ok = (!s.fired() && !msgs.isEmpty()) || (s.fired() && s.status == 200 && pageErrors.size() == pe);
                    return verdict(ok, "Dates cleared -> From \"" + f[0] + "\"/\"" + f[1] + "\", To \"" + t[0] + "\"/\"" + t[1] + "\"; "
                            + (s.fired() ? "Search sent " + s.request + " -> " + s : "no search sent") + "; messages " + msgs
                            + (s.fired() && msgs.isEmpty() ? "; OBSERVATION: no field is validated - empty dates are sent and silently return " + s.rows + " row(s)" : ""));
                });

        tc("SA-20", "Verify date validation - From Date later than To Date",
                "1. From Date = today, To Date = " + WIDE_FROM.format(DMY) + " (calendar). 2. Store -Select-, ALL. 3. Click Search.",
                "A warning says From Date cannot be later than To Date, and no search is sent.",
                () -> {
                    sa.clearStore();
                    String e1 = sa.pickDate(FD, todayDate), e2 = sa.pickDate(TD, WIDE_FROM);
                    if (!e1.isEmpty() || !e2.isEmpty()) return fail("AUTOMATION ERROR (not verified as an application defect): dates could not be picked: " + e1 + " " + e2);
                    sa.setStatus(P.STATUS_ALL);
                    P.SearchResult s = sa.search(8000);
                    List<String> msgs = sa.drainAlerts(); toasts.addAll(msgs);
                    boolean warned = msgs.stream().anyMatch(a -> a.toLowerCase().matches(".*(from date|to date|greater|less|after|before|invalid).*"));
                    return verdict(warned && !s.fired(), "From " + sa.fieldValue(FD) + " / To " + sa.fieldValue(TD) + "; messages " + msgs + "; search "
                            + (s.fired() ? "SENT " + s.request + " -> " + s : "not sent")
                            + (warned ? "" : " — DEFECT: the reversed range is accepted without any date-range warning and the grid is simply emptied"));
                });

        tc("SA-19", "Verify empty-data message",
                "1. From " + FUTURE_FROM.format(DMY) + ", To " + FUTURE_TO.format(DMY) + " (calendar; no adjustment can exist). 2. Store -Select-, ALL. 3. Click Search.",
                "HTTP 200 with no rows, the grid is empty and a \"No records found\" style message is shown.",
                () -> {
                    sa.clearStore();
                    String e1 = sa.pickDate(TD, FUTURE_TO), e2 = sa.pickDate(FD, FUTURE_FROM);
                    if (!e1.isEmpty() || !e2.isEmpty()) return fail("AUTOMATION ERROR (not verified as an application defect): dates could not be picked: " + e1 + " " + e2);
                    sa.setStatus(P.STATUS_ALL);
                    P.SearchResult s = sa.search();
                    List<String> msgs = sa.drainAlerts(); toasts.addAll(msgs);
                    String gridText = sa.emptyGridMessage(MG);
                    boolean msg = msgs.stream().anyMatch(a -> a.toLowerCase().matches(".*(no record|no data|not found).*")) || gridText.toLowerCase().matches(".*(no record|no data|not found).*");
                    boolean ok = s.status == 200 && s.rows == 0 && msg;
                    return verdict(ok, s + "; messages " + msgs + "; grid text \"" + gridText + "\"; pager \"" + sa.pagerText(MG) + "\""
                            + (s.status == 200 && s.rows == 0 && !msg ? " — DEFECT: an empty result shows a blank grid with no 'no records found' message" : ""));
                });

        // ---------------------------------------------------------------- grid (on the all-store result, which has the most rows)
        tc("SA-11", "Verify grid headers",
                "1. Search all stores " + WIDE_FROM.format(DMY) + " - today, ALL. 2. Read the List of Stock Management headers. 3. Read the Stock Management Details headers.",
                "List grid: " + P.MASTER_HEADERS + "; details grid: " + P.DETAIL_HEADERS,
                () -> {
                    allStores = wideSearch("");
                    List<String> m = sa.headers(MG), d = sa.headers(DG);
                    List<String> missM = new ArrayList<>(P.MASTER_HEADERS); missM.removeAll(m);
                    List<String> missD = new ArrayList<>(P.DETAIL_HEADERS); missD.removeAll(d);
                    return verdict(missM.isEmpty() && missD.isEmpty(), "List grid " + m + (missM.isEmpty() ? "" : " MISSING " + missM) + "; details grid " + d
                            + (missD.isEmpty() ? "" : " MISSING " + missD));
                });

        tc("SA-12", "Verify grid data loading",
                "1. With the all-store result on screen, compare grid rows with the API response. 2. Check each row's Stock Adjustment No, Date and Store."
                        + " 3. Check the pager count.",
                "One grid row per stock adjustment (no duplicates); every row has a number, a dd/MM/yyyy date and a store; the pager shows \"1 - n of N items\".",
                () -> {
                    if (allStores == null || allStores.rows <= 0) return skip("no Stock Adjustment records in the range (" + allStores + ") - grid data not checkable");
                    List<String> recs = sa.masterRecords();
                    List<String> ids = sa.masterField("stockadjid");
                    List<String> dupIds = dups(ids);
                    List<String> incomplete = recs.stream().filter(r -> { String[] p = r.split(" \\| ", -1);
                        return p[0].isBlank() || parse(p[1]) == null || p[2].isBlank(); }).collect(Collectors.toList());
                    int size = sa.pager(MG)[2];
                    String expectPager = "1 - " + Math.min(size, allStores.rows) + " of " + allStores.rows + " items";
                    String pagerText = sa.pagerText(MG);
                    boolean ok = allStores.status == 200 && dupIds.isEmpty() && incomplete.isEmpty() && pagerText.equals(expectPager) && sa.pageRows(MG) == Math.min(size, allStores.rows);
                    return verdict(ok, allStores + "; duplicate stock adjustments " + dupIds + (incomplete.isEmpty() ? "; all rows complete" : "; INCOMPLETE " + incomplete)
                            + "; pager \"" + pagerText + "\" (expected \"" + expectPager + "\"); page 1 holds " + sa.pageRows(MG)
                            + "; OBSERVATION: the API returns one line per adjusted item (" + allStores.apiRows + ") and the screen keeps one row per adjustment (" + allStores.rows + ")");
                });

        tc("SA-13", "Verify pagination",
                "1. Page size 25: read page 1, click next, last, previous, first. 2. Change the page size to 50 in the pager.",
                "Pages hold page-size rows; next shows other records; last holds the remainder; next/last are disabled on the last page; size 50 reduces the page count.",
                () -> {
                    int total = sa.masterRows();
                    if (sa.setPageSize(MG, 25) != 25) return fail("page size 25 could not be chosen");
                    int[] p = sa.pager(MG);
                    if (total <= 25) return skip(total + " record(s) - one page only; pagination not exercisable with this data");
                    List<String> page1 = sa.pageField(MG, "stockadjustmentno");
                    boolean nextOk = sa.pagerClick(MG, "next");
                    int[] p2 = sa.pager(MG);
                    List<String> page2 = sa.pageField(MG, "stockadjustmentno");
                    sa.pagerClick(MG, "last");
                    int[] pl = sa.pager(MG);
                    int lastRows = sa.pageRows(MG);
                    boolean lastDisabled = sa.pagerDisabled(MG, "next") && sa.pagerDisabled(MG, "last");
                    sa.pagerClick(MG, "previous");
                    int[] pp = sa.pager(MG);
                    sa.pagerClick(MG, "first");
                    int[] pf = sa.pager(MG);
                    int s50 = sa.setPageSize(MG, 50);
                    int[] p50 = sa.pager(MG);
                    int expectLast = total % 25 == 0 ? 25 : total % 25;
                    boolean ok = p[1] == (total + 24) / 25 && page1.size() == 25 && nextOk && p2[0] == 2 && Collections.disjoint(page1, page2)
                            && pl[0] == p[1] && lastRows == expectLast && lastDisabled && pp[0] == Math.max(1, p[1] - 1) && pf[0] == 1 && s50 == 50 && p50[1] == (total + 49) / 50;
                    sa.setPageSize(MG, 25);
                    return verdict(ok, total + " records; size 25 -> " + p[1] + " page(s); page 1 has " + page1.size() + "; next -> page " + p2[0] + " " + page2.stream().limit(5).collect(Collectors.toList())
                            + "...; last -> page " + pl[0] + " with " + lastRows + " row(s) (expected " + expectLast + "), next/last disabled " + lastDisabled
                            + "; previous -> page " + pp[0] + "; first -> page " + pf[0] + "; size 50 -> " + p50[1] + " page(s)");
                });

        tc("SA-14", "Verify sorting",
                "1. Page size 125. 2. Click the Stock Adjustment No header twice. 3. Click the Date header (ascending). 4. Remove the sort.",
                "Rows re-order ascending then descending by number; Date ascending lists the oldest adjustment first (chronological order).",
                () -> {
                    if (sa.masterRows() < 2) return skip("fewer than 2 rows - sorting not exercisable");
                    sa.setPageSize(MG, 125);
                    String d1 = sa.clickHeader(MG, "Stock Adjustment No");
                    List<String> n1 = sa.pageField(MG, "stockadjustmentno");
                    String d2 = sa.clickHeader(MG, "Stock Adjustment No");
                    List<String> n2 = sa.pageField(MG, "stockadjustmentno");
                    String d3 = sa.clickHeader(MG, "Date");
                    List<String> dates = sa.pageField(MG, "dates");
                    boolean noOk = "asc".equals(d1) && sorted(n1, false) && "desc".equals(d2) && sorted(n2, true);
                    boolean dateOk = "asc".equals(d3) && chronological(dates);
                    sa.clearSort(MG);
                    sa.setPageSize(MG, 25);
                    String a = "Stock Adjustment No " + d1 + " " + n1.stream().limit(4).collect(Collectors.toList()) + "... / " + d2 + " " + n2.stream().limit(4).collect(Collectors.toList())
                            + "...; Date " + d3 + " " + dates.stream().limit(8).collect(Collectors.toList()) + "...";
                    if (noOk && !dateOk && "asc".equals(d3)) a += " — DEFECT: the Date column sorts the dd/MM/yyyy text alphabetically (by day), not by date";
                    return verdict(noOk && dateOk, a);
                });

        tc("SA-22", "Verify grid column filters",
                "1. Type a full Stock Adjustment No into its column filter. 2. Type a value that matches nothing. 3. Clear the filter.",
                "Only the matching record is shown; a non-matching value shows no row; clearing restores the page.",
                () -> {
                    List<String> all = sa.masterField("stockadjustmentno");
                    if (all.isEmpty()) return skip("no rows to filter");
                    int inputs = sa.filterInputs(MG);
                    String target = all.get(all.size() / 2);
                    int before = sa.pageRows(MG);
                    sa.filterColumn(MG, 0, target);
                    List<String> shown = sa.pageField(MG, "stockadjustmentno");
                    sa.filterColumn(MG, 0, "NO_SUCH_SA_ZZ");
                    int none = sa.pageRows(MG);
                    sa.filterColumn(MG, 0, "");
                    int back = sa.pageRows(MG);
                    boolean ok = inputs >= 5 && !shown.isEmpty() && shown.stream().allMatch(x -> x.contains(target)) && none == 0 && back == before;
                    return verdict(ok, inputs + " filter box(es); \"" + target + "\" -> " + shown + "; non-matching -> " + none + " row(s); cleared -> " + back + " row(s) (before " + before + ")");
                });

        tc("SA-21", "Verify Clear/Reset functionality",
                "1. Look for a Clear or Reset button in the form. 2. Type into the Store Name and Remark column filters. 3. Grid menu (☰) > Clear all filters."
                        + " 4. Check the filters are empty and the rows are back.",
                "The grid's Clear all filters empties every column filter and restores the rows (the form itself has no Clear/Reset button).",
                () -> {
                    boolean hasReset = (Boolean) page.evaluate("() => [...document.querySelectorAll('#StockAdjustmentList button, #StockAdjustmentList input[type=button], #StockAdjustmentList input[type=reset]')]"
                            + ".some(b=>b.offsetParent!==null && /^(clear|reset)$/i.test((b.innerText||b.value||'').trim()))");
                    if (sa.masterRows() <= 0) return skip("no rows on screen to filter and reset; form Clear/Reset button present: " + hasReset);
                    int before = sa.pageRows(MG);
                    sa.filterColumn(MG, 2, "zz-no-store");
                    sa.filterColumn(MG, 5, "zz");
                    int filtered = sa.pageRows(MG);
                    boolean cleared = sa.gridMenuClearAllFilters(MG);
                    List<String> values = sa.filterValues(MG);
                    int after = sa.pageRows(MG);
                    boolean ok = cleared && values.stream().allMatch(String::isEmpty) && after == before && filtered < before;
                    return verdict(ok, "Form Clear/Reset button: " + (hasReset ? "present (not covered)" : "none") + "; rows " + before + " -> filtered " + filtered
                            + " -> after Clear all filters " + after + "; filter boxes afterwards " + values);
                });

        tc("SA-23", "Verify horizontal scrolling",
                "1. Measure the list grid and the details grid viewports. 2. Scroll each fully right. 3. Measure the list grid's box.",
                "When the columns are wider than the grid it scrolls horizontally with the header in step; otherwise no scroll is needed.",
                () -> {
                    int[] m = sa.horizontalScroll(MG), d = sa.horizontalScroll(DG), box = sa.gridBox(MG);
                    boolean mNeeds = m[0] > m[1] + 2, dNeeds = d[0] > d[1] + 2;
                    String a = "List grid content " + m[0] + "px in " + m[1] + "px (scrolled to " + m[2] + ", header " + m[3] + "); details grid " + d[0] + "px in " + d[1]
                            + "px (scrolled to " + d[2] + ", header " + d[3] + "); list grid " + box[0] + "px high inside a " + box[1] + "px panel box";
                    if (!mNeeds && !dNeeds) return skip(a + " — all columns fit at this window width; no horizontal scroll needed");
                    boolean ok = (!mNeeds || (m[2] > 0 && Math.abs(m[2] - m[3]) <= 2)) && (!dNeeds || (d[2] > 0 && Math.abs(d[2] - d[3]) <= 2));
                    return verdict(ok, a);
                });

        // ---------------------------------------------------------------- row selection, details, print
        tc("SA-15", "Verify row selection",
                "1. Click the row-header selection tick of the first row (NOT the Approved checkbox). 2. Check the grid's selection and the record the screen holds for Print.",
                "The row is selected, its details are requested (GetPhysicalStockdetails, HTTP 200) and the screen holds its stockadjid / locationid.",
                () -> {
                    if (sa.masterRows() <= 0) return skip("no record to select");
                    String no = sa.pageField(MG, "stockadjustmentno").get(0), id = sa.pageField(MG, "stockadjid").get(0), loc = sa.pageField(MG, "locationid").get(0);
                    String[] r = sa.selectMasterRow(0);
                    List<String> sel = sa.selectedRows();
                    String[] held = sa.heldSelection();
                    boolean ok = "200".equals(r[0]) && r[1].contains("\"stockadjid\":" + id) && sel.equals(Collections.singletonList(no)) && id.equals(held[0]) && loc.equals(held[1]);
                    if (ok) { selected[0] = no; selected[1] = id; selected[2] = loc; }
                    return verdict(ok, "Selected " + no + " (id " + id + "): details request HTTP " + r[0] + " " + r[1] + "; grid selection " + sel + "; held stockadjid/locationid "
                            + held[0] + "/" + held[1]);
                });

        tc("SA-16", "Verify view/details functionality",
                "1. With the row selected in SA-15, read Stock Management Details. 2. Check each line belongs to the selected adjustment."
                        + " 3. Check Balance After Adjustment = Available ± Adjustment Qty and Net Amount = WAC × Adjustment Qty.",
                "The details grid lists the selected adjustment's items with consistent quantities and amounts; no Edit is offered on this list.",
                () -> {
                    if (selected[1] == null) return skip("no record selected (SA-15)");
                    List<String> lines = sa.detailRecords();
                    if (lines.isEmpty()) return fail("Stock Management Details is empty for " + selected[0]);
                    List<String> wrong = new ArrayList<>();
                    for (String l : lines) {
                        String[] p = l.split(" \\| ", -1);
                        double avl = num(p[3]), adj = num(p[4]), bal = num(p[7]), wac = num(p[8]), net = num(p[11]);
                        int type = (int) num(p[13]);
                        double expBal = type == 1 ? avl + adj : type == 2 ? avl - adj : avl;
                        if (!p[12].equals(selected[1])) wrong.add(p[0] + " belongs to stockadjid " + p[12]);
                        if (Math.abs(bal - expBal) > 0.001) wrong.add(p[0] + " balance " + bal + " expected " + expBal);
                        if (Math.abs(net - wac * adj) > 0.01) wrong.add(p[0] + " net " + net + " expected " + (wac * adj));
                    }
                    return verdict(wrong.isEmpty(), lines.size() + " line(s) for " + selected[0] + ": " + lines.stream().map(l -> l.replaceFirst(" \\| [^|]*\\| [^|]*$", ""))
                            .limit(5).collect(Collectors.toList()) + (wrong.isEmpty() ? "; all consistent" : "; INCONSISTENT " + wrong) + "; no View / Edit button on this screen");
                });

        tc("SA-18", "Verify print functionality with a selected record",
                "1. With the record selected in SA-15, click Print. 2. Capture the report tab and its response. 3. Close the tab.",
                "A report tab opens for the selected adjustment (ID = its stockadjid, LocationID = its location) and returns a PDF (HTTP 200).",
                () -> {
                    if (selected[1] == null) return skip("no record selected (SA-15)");
                    P.PrintResult p = sa.print();
                    if (p.url.isEmpty()) return fail("No report opened: " + p.dialog + " " + p.error);
                    boolean idOk = p.url.contains("ID=" + selected[1]) && p.url.contains("LocationID=" + selected[2]);
                    boolean ok = idOk && p.status == 200 && p.contentType.contains("pdf") && p.error.isEmpty();
                    return verdict(ok, "Report " + p.url.replaceFirst("^https?://[^/]+", "") + " -> HTTP " + p.status + " " + p.contentType + " " + p.error
                            + "; carries the selected record " + selected[0] + ": " + idOk);
                });

        tc("SA-28", "Verify export functionality - not executed",
                "1. Open the list grid's menu (☰). 2. List its entries. 3. Close it without clicking any export entry.",
                "The menu offers CSV / PDF / Excel export; exporting downloads a file.",
                () -> {
                    String entries;
                    try {
                        List<String> items = sa.gridMenuEntries(MG);
                        entries = items == null ? "the grid shows no menu button" : "menu entries " + items;
                    } catch (Throwable e) {
                        entries = "the menu entries could not be listed (" + firstLine(e) + ")";
                    }
                    return manual("Export not executed (it downloads a file; this run is read-only and cancels downloads). " + entries + ". Verify the export contents manually.");
                });

        // ---------------------------------------------------------------- stability / monitoring / safety
        tc("SA-26", "Verify page stability and navigation",
                "1. Run the same all-store search twice. 2. Leave the screen and re-open it from the menu. 3. Check defaults are restored, no logout,"
                        + " no unexpected route, no browser dialog / download, no script error.",
                "Same result both times; re-opening restores default filters with empty grids; the session stays logged in on the screen.",
                () -> {
                    P.SearchResult a = wideSearch(""), b = sa.search();
                    String why = sa.reload();
                    String fd = sa.fieldValue(FD), td = sa.fieldValue(TD), store = sa.chosen(P.STORE);
                    int rows = sa.masterRows(), details = sa.detailRows();
                    List<String> nav = new ArrayList<>(navigations);
                    List<String> offRoute = nav.stream().filter(n -> n.startsWith("#/") && !onScreenRoute(n) && !n.startsWith("#/PatientDashboard") && !n.startsWith("#/LandingPage"))
                            .distinct().collect(Collectors.toList());
                    int firstApp = 0;
                    while (firstApp < nav.size() && !nav.get(firstApp).startsWith("#/")) firstApp++;
                    boolean loggedOut = false;
                    for (int i = firstApp + 1; i < nav.size(); i++) if (nav.get(i).toLowerCase().contains("/account")) loggedOut = true;
                    List<String> screenErrors = pageErrors.stream().filter(e -> e.startsWith("[" + P.ROUTE)).collect(Collectors.toList());
                    boolean ok = a.status == 200 && b.status == 200 && a.rows == b.rows && why.isEmpty() && today.equals(fd) && today.equals(td) && store.equals(defaultStore)
                            && rows == 0 && details == 0 && !loggedOut && offRoute.isEmpty() && screenErrors.isEmpty() && nativeDialogs.isEmpty() && downloads.isEmpty();
                    return verdict(ok, "Search x2 -> " + a + " / " + b + "; re-open " + (why.isEmpty() ? "OK" : "FAILED: " + why) + " -> From " + fd + ", To " + td + ", Store \"" + store
                            + "\", rows " + rows + "/" + details + "; logged out " + loggedOut + "; navigations " + new LinkedHashSet<>(nav) + "; unexpected routes " + offRoute
                            + "; page errors on screen " + screenErrors + "; dialogs " + nativeDialogs + "; downloads " + downloads);
                });

        tc("SA-24", "Verify API responses",
                "1. Record every /api/ request the run made (method, endpoint, ExecFlag, status, time). 2. Separate the Stock Adjustment screen's calls from login / dashboard calls.",
                "Every Stock Adjustment API answers HTTP 2xx; nothing fails.",
                () -> {
                    List<ApiCall> calls = new ArrayList<>(api);
                    List<ApiCall> screen = calls.stream().filter(c -> c.path.startsWith("/api/") && onScreenRoute(c.route) && !isBlocked(c)).collect(Collectors.toList());
                    List<String> bad = screen.stream().filter(c -> c.status < 200 || c.status >= 300).map(this::fmt).collect(Collectors.toList());
                    List<String> other = calls.stream().filter(c -> c.path.startsWith("/api/") && !onScreenRoute(c.route) && (c.status < 200 || c.status >= 400) && !isBlocked(c))
                            .map(this::fmt).distinct().collect(Collectors.toList());
                    return verdict(bad.isEmpty() && !screen.isEmpty(), screen.size() + " Stock Adjustment API call(s): " + endpointSummary(screen)
                            + (bad.isEmpty() ? "; none failed" : "; FAILED " + bad) + (other.isEmpty() ? "" : "; outside this screen (not counted): " + other));
                });

        tc("SA-25", "Verify browser console errors",
                "1. Collect browser console errors and uncaught page errors during the run. 2. Separate those raised on the Stock Adjustment screen.",
                "No console error and no page error on the Stock Adjustment screen.",
                () -> {
                    List<String> screen = consoleErrors.stream().filter(e -> e.startsWith("[" + P.ROUTE)).distinct().collect(Collectors.toList());
                    List<String> pe = pageErrors.stream().filter(e -> e.startsWith("[" + P.ROUTE)).distinct().collect(Collectors.toList());
                    List<String> elsewhere = consoleErrors.stream().filter(e -> !screen.contains(e)).distinct().collect(Collectors.toList());
                    return verdict(screen.isEmpty() && pe.isEmpty(), "Console errors on screen: " + (screen.isEmpty() ? "none" : screen) + "; page errors on screen: "
                            + (pe.isEmpty() ? "none" : pe) + (elsewhere.isEmpty() ? "" : "; elsewhere (login/dashboard, not counted): " + elsewhere));
                });

        tc("SA-30", "Verify no unexpected application errors",
                "1. Review every toast message the screen raised. 2. Review server answers >= 500 and network failures on the screen. 3. Review page errors and browser dialogs.",
                "No error toast, no server error, no script error and no unexpected browser dialog on the Stock Adjustment screen.",
                () -> {
                    List<String> errToasts = toasts.stream().filter(t -> t.toLowerCase().matches(".*(\\[error\\]|error|failed|exception).*")).collect(Collectors.toList());
                    List<String> server = new ArrayList<>(api).stream().filter(c -> onScreenRoute(c.route) && (c.status >= 500 || (c.status == 0 && !isBlocked(c)))).map(this::fmt).collect(Collectors.toList());
                    List<String> pe = pageErrors.stream().filter(e -> e.startsWith("[" + P.ROUTE)).distinct().collect(Collectors.toList());
                    return verdict(errToasts.isEmpty() && server.isEmpty() && pe.isEmpty() && nativeDialogs.isEmpty(), "Toasts: " + (toasts.isEmpty() ? "none" : toasts)
                            + "; server errors: " + (server.isEmpty() ? "none" : server) + "; page errors: " + (pe.isEmpty() ? "none" : pe) + "; dialogs: " + (nativeDialogs.isEmpty() ? "none" : nativeDialogs));
                });

        tc("SA-27", "Verify read-only safety",
                "1. Count write calls the guard blocked. 2. Count downloads and browser dialogs. 3. Count the Approved checkboxes left untouched. 4. Confirm New / Approve / export / confirm-Ok were never clicked.",
                "No write call attempted, nothing downloaded, no Stock Adjustment created or approved, no stock quantity changed.",
                () -> {
                    int[] cb = {0, 0};
                    try { if (sa.masterRows() <= 0) wideSearch(""); cb = sa.approveCheckboxes(); } catch (Throwable ignore) { }
                    return verdict(blockedWrites.isEmpty() && downloads.isEmpty(), "Blocked write calls: " + (blockedWrites.isEmpty() ? "none" : blockedWrites)
                            + "; downloads: " + (downloads.isEmpty() ? "none" : downloads) + "; dialogs: " + (nativeDialogs.isEmpty() ? "none" : nativeDialogs)
                            + "; Approved checkboxes on page 1: " + cb[0] + " (" + cb[1] + " enabled - each would POST /api/StockAdjustment/IUDApproveStockAdjustment at once; none clicked)"
                            + "; New (" + controls.getOrDefault("New", "?") + ") never clicked; grid export never clicked; confirm boxes closed with Cancel");
                });
    }

    // ======================================================================= report

    /** Writes the cases (in ID order) to the framework report, plus the API / console summary rows. */
    private void flush(String startedAt) {
        cases.sort(Comparator.comparing(c -> c.id));
        for (Case c : cases) step(c.png, c.id + " - " + c.scenario, "Steps: " + c.steps, c.expected, c.actual, c.status);

        Map<String, Long> counts = cases.stream().collect(Collectors.groupingBy(c -> c.status, TreeMap::new, Collectors.counting()));
        addSummary("Test cases (PASS / FAIL / SKIPPED / MANUAL)", cases.size() + " (" + counts.getOrDefault("PASS", 0L) + " / " + counts.getOrDefault("FAIL", 0L)
                + " / " + counts.getOrDefault("SKIPPED", 0L) + " / " + counts.getOrDefault("MANUAL", 0L) + ")");
        addSummary("Failed test cases", orNone(cases.stream().filter(c -> "FAIL".equals(c.status)).map(c -> c.id + " " + c.scenario).collect(Collectors.toList())));
        addSummary("Application defects (FAIL rows marked DEFECT)", orNone(cases.stream().filter(c -> "FAIL".equals(c.status) && c.actual.contains("DEFECT"))
                .map(c -> c.id + " " + c.scenario).collect(Collectors.toList())));
        addSummary("Automation errors (script could not complete the case - not application defects)", orNone(cases.stream()
                .filter(c -> c.automationError || c.actual.startsWith("AUTOMATION ERROR")).map(c -> c.id + " " + c.scenario).collect(Collectors.toList())));
        List<ApiCall> calls = new ArrayList<>(api);
        addSummary("API calls recorded", calls.size() + " (" + calls.stream().filter(c -> onScreenRoute(c.route)).count() + " on the Stock Adjustment screen)");
        addSummary("Stock Adjustment endpoints", endpointSummary(calls.stream().filter(c -> onScreenRoute(c.route) && c.path.startsWith("/api/")).collect(Collectors.toList())));
        addSummary("API failures (HTTP >= 400 / network)", orNone(calls.stream().filter(c -> (c.status >= 400 || c.status == 0) && !isBlocked(c))
                .map(this::fmt).distinct().collect(Collectors.toList())));
        addSummary("Console errors", orNone(new ArrayList<>(new LinkedHashSet<>(consoleErrors))));
        addSummary("Page (script) errors", orNone(new ArrayList<>(new LinkedHashSet<>(pageErrors))));
        addSummary("Toast messages", orNone(new ArrayList<>(toasts)));
        addSummary("Blocked write calls (read-only guard)", orNone(blockedWrites));
        addSummary("Finished", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + " (started " + startedAt + ")");
        System.out.println("Stock Adjustment cases: " + counts);
    }

    private String fmt(ApiCall c) {
        return c.method + " " + c.path + (c.body.isEmpty() ? "" : " (" + c.body + ")") + " -> " + (c.status == 0 ? "NETWORK " + c.failure : "HTTP " + c.status)
                + " (" + c.ms + " ms) [" + c.route + "]";
    }

    private static String endpointSummary(List<ApiCall> calls) {
        Map<String, List<ApiCall>> g = calls.stream().collect(Collectors.groupingBy(c -> c.method + " " + c.path + (c.body.isEmpty() ? "" : " " + c.body), TreeMap::new, Collectors.toList()));
        return g.entrySet().stream().map(e -> e.getKey() + " x" + e.getValue().size() + " status " + e.getValue().stream().map(c -> String.valueOf(c.status)).distinct().collect(Collectors.joining("/"))
                + " avg " + (long) e.getValue().stream().mapToLong(c -> c.ms).average().orElse(0) + " ms").collect(Collectors.joining("; "));
    }

    // ======================================================================= small helpers

    private static boolean isBlocked(ApiCall c) { return c.failure.toLowerCase().replace("_", "").contains("blockedbyclient"); }

    private static String orNone(List<String> l) { return l.isEmpty() ? "none" : String.join(" ; ", l); }
    private static String oneLine(String s, int max) { s = String.valueOf(s).replaceAll("\\s+", " ").trim(); return s.length() > max ? s.substring(0, max) + "…" : s; }
    private static String firstLine(Throwable e) { return String.valueOf(e.getMessage()).split("\\R")[0]; }

    private static List<String> dups(List<String> l) {
        Set<String> seen = new HashSet<>(); List<String> d = new ArrayList<>();
        for (String s : l) if (!seen.add(s.toLowerCase())) d.add(s);
        return d;
    }

    private static double num(String s) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; } }

    private static LocalDate parse(String s) { try { return LocalDate.parse(s.trim(), DMY); } catch (Exception e) { return null; } }

    private static boolean sorted(List<String> l, boolean desc) {
        for (int i = 1; i < l.size(); i++) { int c = l.get(i - 1).compareToIgnoreCase(l.get(i)); if (desc ? c < 0 : c > 0) return false; }
        return !l.isEmpty();
    }

    private static boolean chronological(List<String> l) {
        for (int i = 1; i < l.size(); i++) {
            LocalDate a = parse(l.get(i - 1)), b = parse(l.get(i));
            if (a == null || b == null || a.isAfter(b)) return false;
        }
        return !l.isEmpty();
    }
}
