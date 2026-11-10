package com.kpj.tests.Inventory_page.Transfer_page;

import com.kpj.core.DevHisBase;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
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
 * Inventory &gt; Transfer &gt; <b>Return Item</b> — READ-ONLY functional checks of the Return Item list screen (#/ReturnItemList).
 *
 * <p>Every check is its own test case (RI-01 … RI-30) and appears as its own JUnit test (a dynamic test named after the case), as its own
 * row in the HTML report {@code test-output/reports/Inventory_Transfer_ReturnItem.html} (Description = test steps, Expected, Actual, Status
 * PASS / FAIL / SKIPPED / MANUAL, screenshot) and in the aggregate TSV. The browser session is shared by the cases (one login), and the
 * report lists the cases in ID order whatever order they ran in.</p>
 *
 * <p><b>Read-only guard:</b> any {@code /api/} call whose name or ExecFlag looks like a write (IUD / Save / Insert / Update / Delete /
 * Approve / Cancel / Submit …) is ABORTED before it reaches the server and listed in RI-30; downloads and native dialogs are cancelled.
 * New, Save, the grid menu's export entries and every confirm action are never clicked. Cases that would need data to change are MANUAL.</p>
 *
 * <p>Run only this class:
 * {@code mvn test "-Dtest=com.kpj.tests.Inventory_page.Transfer_page.ReturnItem" -Ddevhis.postrun=off} (or run-returnitem.bat).</p>
 */
public class ReturnItem extends DevHisBase {

    public ReturnItem() { super("Inventory_Transfer_ReturnItem"); }

    public static void main(String[] args) {
        ReturnItem t = new ReturnItem();
        try { t.run(); } finally { t.stop(); }
    }

    // ======================================================================= JUnit: one dynamic test per case

    @TestFactory
    Stream<DynamicTest> execute() {
        // DevHisBase.run() does not close the browser. Left open, the Playwright driver was still running when the JVM's shutdown hook deleted
        // its temporary folder, and the driver then failed on the missing package.json ("Cannot find module ...package.json") during cleanup.
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

    /** Runs one case; an exception becomes a FAIL carrying the exception details. A screenshot is taken right after the case. */
    private Case tc(String id, String scenario, String steps, String expected, Supplier<Outcome> body) {
        Case c = new Case(id, scenario, steps, expected);
        long t0 = System.currentTimeMillis();
        // A confirm box or dropdown left open by an earlier case sits over the screen and swallows every click (that is what made
        // RI-04 ... RI-29 time out in the first run, after RI-20 left the "Please select" box open). Clear it first.
        String cleared = "";
        try { cleared = ri.closeOverlays(); } catch (Throwable ignore) { }
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
            try { ri.closeOverlays(); } catch (Throwable ignore) { }
        }
        if (!cleared.isEmpty()) c.actual = "(before this case: " + cleared + ") " + c.actual;
        c.actual += " [execution time " + String.format(Locale.ROOT, "%.1f", (System.currentTimeMillis() - t0) / 1000.0) + " s]";
        try { c.png = page.screenshot(new Page.ScreenshotOptions().setTimeout(10000)); } catch (Throwable ignore) { }
        cases.add(c);
        System.out.printf("  %-6s [%-7s] %s%n", id, c.status, scenario);
        return c;
    }

    // ======================================================================= monitoring (API / console / page errors / redirects)

    private static final class ApiCall {
        final String method, path, route; final int status; final long ms; final String failure;
        ApiCall(String method, String path, int status, long ms, String route, String failure) {
            this.method = method; this.path = path; this.status = status; this.ms = ms; this.route = route; this.failure = failure; }
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
            api.add(new ApiCall(q.method(), path(resp.url()), resp.status(), t == null ? -1 : System.currentTimeMillis() - t, route(page.url()), ""));
        });
        page.onRequestFailed(q -> {
            Long t = started.remove(q);
            String why = String.valueOf(q.failure());
            api.add(new ApiCall(q.method(), path(q.url()), 0, t == null ? -1 : System.currentTimeMillis() - t, route(page.url()), why));
        });
        page.onConsoleMessage(m -> { if ("error".equals(m.type())) consoleErrors.add("[" + route(page.url()) + "] " + oneLine(m.text(), 300)); });
        page.onPageError(e -> pageErrors.add("[" + route(page.url()) + "] " + oneLine(e, 300)));
        page.onFrameNavigated(f -> { if (f == page.mainFrame()) navigations.add(route(f.url())); });
        page.onDownload(d -> { downloads.add(d.suggestedFilename()); try { d.cancel(); } catch (Exception ignore) { } });
        page.onDialog(d -> { nativeDialogs.add(d.type() + ": " + d.message()); try { d.dismiss(); } catch (Exception ignore) { } });
    }

    private boolean onScreenRoute(String r) { return r != null && r.startsWith(com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.ROUTE); }

    // ======================================================================= the test

    private com.kpj.pages.Inventory_page.Transfer_page.ReturnItem ri;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter GRID_DATE = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);
    private final String today = LocalDate.now().format(DMY);
    private static final String WIDE_FROM = "01/01/2024";

    // facts gathered by earlier cases and used by later ones
    private String defaultFromStore = "";
    private final Map<String, String> controls = new LinkedHashMap<>();
    private com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.SearchResult validSearch;
    private List<String> validRecords = new ArrayList<>();

    private static final String F = com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.FROM_STORE;
    private static final String T = com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.TO_STORE;
    private static final String FD = com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.FROM_DATE;
    private static final String TD = com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.TO_DATE;
    private static final String MG = com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.MASTER_GRID;
    private static final String DG = com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.DETAIL_GRID;

    @Override
    protected void body() {
        String started = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        meta("Inventory - Transfer - Return Item", "Inventory > Transfer > Return Item (#/ReturnItemList)",
                "READ-ONLY run: write APIs are blocked, New / Save / export / confirm actions are never clicked. Each row below is one test case;"
                        + " the Description column holds its test steps. MANUAL = needs a data change or a human check; SKIPPED = not available on this screen"
                        + " or not exercisable with the data present.");
        addSummary("Execution date / time", started);
        addSummary("Environment", BASE);
        addSummary("Login user", USER);
        ri = new com.kpj.pages.Inventory_page.Transfer_page.ReturnItem(page);
        installMonitors();
        try {
            runCases();
        } finally {
            flush(started);
        }
    }

    private void runCases() {
        login();

        // ---------------------------------------------------------------- loading
        tc("RI-01", "Verify Return Item screen navigation",
                "1. Log in. 2. Open Inventory in the sidebar. 3. Open Transfer. 4. Click Return Item.",
                "The Return Item screen (#/ReturnItemList) opens from the menu with its Search button.",
                () -> {
                    String why = ri.navigateViaMenu();
                    if (why.isEmpty()) return pass("Inventory > Transfer > Return Item opened " + page.url());
                    boolean recovered = ri.openViaRoute(BASE);
                    return fail("Menu navigation failed: " + why + (recovered ? " (opened via the route instead so the other cases could run)" : " (direct route also failed)"));
                });

        tc("RI-02", "Verify screen loading",
                "1. Open the screen. 2. Check the page title and banner caption. 3. Check the APIs fired on load. 4. Check the default filter values.",
                "Title \"Return Item\"; store lists, header labels and recalled-item keys load with HTTP 2xx; From Store pre-selected, To Store empty,"
                        + " From/To Date = today; the grid is empty until Search is clicked.",
                () -> {
                    String[] t = ri.titles();
                    List<ApiCall> load = new ArrayList<>(api).stream().filter(a -> onScreenRoute(a.route) && a.path.startsWith("/api/")).collect(Collectors.toList());
                    List<String> bad = load.stream().filter(a -> a.status < 200 || a.status >= 300).map(a -> a.method + " " + a.path + " -> " + a.status + " " + a.failure).collect(Collectors.toList());
                    boolean stores = load.stream().anyMatch(a -> a.path.equals("/api/CommonDropdown/Fetch") && a.status == 200);
                    defaultFromStore = ri.chosen(F);
                    String toStore = ri.chosen(T), fd = ri.fieldValue(FD), td = ri.fieldValue(TD);
                    int rows = ri.masterRows();
                    boolean ok = "Return Item".equalsIgnoreCase(t[0].trim()) && stores && bad.isEmpty() && !defaultFromStore.isEmpty()
                            && toStore.isEmpty() && today.equals(fd) && today.equals(td);
                    return verdict(ok, "Title \"" + t[0] + "\", banner \"" + t[1] + "\"; load APIs: "
                            + load.stream().map(a -> a.path + " " + a.status).distinct().collect(Collectors.joining(", "))
                            + (bad.isEmpty() ? "" : "; FAILED: " + bad) + "; defaults: From Store \"" + defaultFromStore + "\", To Store \""
                            + (toStore.isEmpty() ? "--Select--" : toStore) + "\", From " + fd + ", To " + td + "; grid rows before search " + rows);
                });

        tc("RI-03", "Verify all fields and buttons",
                "1. Check From Store, To Store, From Date, To Date, Search. 2. Check the Return grid with its pager and the item detail grid."
                        + " 3. Check New, Print and Patient Print. 4. Check the colour legend.",
                "Every control is visible and enabled; legend shows Psychotropic Drug and Special Order.",
                () -> {
                    controls.putAll(ri.controls());
                    List<String> missing = controls.entrySet().stream().filter(e -> !"visible".equals(e.getValue())).map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.toList());
                    List<String> legend = ri.legend();
                    boolean ok = missing.isEmpty() && legend.containsAll(Arrays.asList("Psychotropic Drug", "Special Order"));
                    return verdict(ok, "Controls: " + controls + "; legend " + legend + (missing.isEmpty() ? "" : "; NOT OK: " + missing)
                            + "; no Clear/Reset, View or Edit button exists on this screen");
                });

        // Print / Patient Print BEFORE any record is selected (the screen keeps the last clicked row)
        tc("RI-20", "Verify Print and Patient Print without a selected record",
                "1. Without clicking any row, click Print. 2. Read the message, close it with Cancel. 3. Repeat with Patient Print.",
                "No report opens; a message asks the user to select a record first.",
                () -> {
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.PrintResult p1 = ri.print(com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.PRINT_BTN);
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.PrintResult p2 = ri.print(com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.PATIENT_PRINT_BTN);
                    boolean ok = p1.url.isEmpty() && p2.url.isEmpty() && !p1.dialog.isEmpty() && !p2.dialog.isEmpty();
                    return verdict(ok, "Print -> " + (p1.url.isEmpty() ? "message \"" + p1.dialog + "\"" : "OPENED " + p1.url) + p1.error
                            + "; Patient Print -> " + (p2.url.isEmpty() ? "message \"" + p2.dialog + "\"" : "OPENED " + p2.url) + p2.error);
                });

        // ---------------------------------------------------------------- dropdowns
        tc("RI-04", "Verify dropdown functionality (open, values, select)",
                "1. Open the From Store list and count its entries. 2. Open the To Store list. 3. Check for duplicate entries."
                        + " 4. Pick a store in To Store from its list. 5. Put To Store back to --Select--.",
                "Both lists open with the stores; no duplicates; the picked store is shown and held by the screen; --Select-- clears it.",
                () -> {
                    int nFrom = ri.openDropdown(F); ri.closeDropdown();
                    int nTo = ri.openDropdown(T); ri.closeDropdown();
                    List<String> from = ri.storeNames(F), to = ri.storeNames(T);
                    List<String> dupFrom = dups(from), dupTo = dups(to);
                    String target = to.contains("Pharmacy (Central)") ? "Pharmacy (Central)" : to.stream().filter(s -> !s.equals(defaultFromStore)).findFirst().orElse("");
                    String err = target.isEmpty() ? "no store to pick" : ri.pickStore(T, target);
                    String held = ri.model(T);
                    String cleared = ri.clearStore(T);
                    boolean ok = nFrom > 1 && nTo > 1 && dupFrom.isEmpty() && dupTo.isEmpty() && err.isEmpty() && cleared.isEmpty();
                    return verdict(ok, "From Store list " + nFrom + " entries (" + from.size() + " stores), To Store list " + nTo + " entries (" + to.size() + " stores);"
                            + " duplicates From " + dupFrom + ", To " + dupTo + "; picked \"" + target + "\" -> " + (err.isEmpty() ? "shown, model " + held : err)
                            + "; back to --Select-- -> model \"" + cleared + "\"" + "; From Store list differs from To Store list by " + Math.abs(to.size() - from.size()) + " entries");
                });

        tc("RI-26", "Verify dropdown search functionality",
                "1. Open To Store. 2. Type \"pharm\" in its search box. 3. Read the list. 4. Type a text that matches nothing. 5. Close the list.",
                "The list narrows to stores containing \"pharm\"; a non-matching text shows no store.",
                () -> {
                    if (ri.openDropdown(T) < 0) return fail("To Store list did not open");
                    List<String> hits = ri.typeInDropdownSearch("pharm");
                    if (hits == null) { ri.closeDropdown(); return skip("To Store has no search box"); }
                    List<String> none = ri.typeInDropdownSearch("zzqx");
                    ri.closeDropdown();
                    boolean ok = !hits.isEmpty() && hits.stream().allMatch(h -> h.toLowerCase().contains("pharm")) && none != null && none.isEmpty();
                    return verdict(ok, "\"pharm\" -> " + hits + "; \"zzqx\" -> " + none);
                });

        // ---------------------------------------------------------------- search
        tc("RI-05", "Verify search with valid input",
                "1. Keep From Store = default store. 2. From Date " + WIDE_FROM + ", To Date today. 3. Click Search.",
                "POST /api/ReturnItem/Fetch (GetReturnItemSearch) carries the store and dates as entered, returns HTTP 200,"
                        + " and the grid shows the returned records, all from the selected From Store.",
                () -> {
                    ri.typeDate(FD, WIDE_FROM); ri.typeDate(TD, today);
                    validSearch = ri.search();
                    if (!validSearch.fired()) return fail("Search sent no request: " + validSearch.error);
                    validRecords = ri.masterRecords();
                    List<String> fromNames = ri.masterField("FromStoreName");
                    boolean storeOk = fromNames.stream().allMatch(n -> n.trim().equalsIgnoreCase(defaultFromStore));
                    boolean reqOk = validSearch.request.contains("\"fromdate\":\"" + WIDE_FROM + "\"") && validSearch.request.contains("\"todate\":\"" + today + "\"")
                            && validSearch.request.contains("\"fromstoreid\":") && !validSearch.request.contains("\"fromstoreid\":null");
                    boolean ok = validSearch.status == 200 && reqOk && storeOk;
                    return verdict(ok, "Request " + validSearch.request + "; " + validSearch + "; From Store names in result " + new LinkedHashSet<>(fromNames)
                            + (validSearch.rows == 0 ? "; NOTE: no Return Item records exist for this store and period" : "; records: " + validRecords));
                });

        tc("RI-06", "Verify search with invalid input",
                "1. Type letters \"ab/cd/efgh\" into From Date. 2. Type the impossible date 31/02/2026 into From Date. 3. Click Search.",
                "Letters are not accepted by the date box; an impossible date is rejected with a message, or at least not sent to the server.",
                () -> {
                    String[] letters = ri.typeDate(FD, "ab/cd/efgh");
                    boolean lettersRejected = !letters[0].matches(".*[A-Za-z].*") && !letters[1].matches(".*[A-Za-z].*");
                    String[] impossible = ri.typeDate(FD, "31/02/2026");
                    ri.typeDate(TD, today);
                    interceptAlerts();
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.SearchResult s = ri.search();
                    List<String> alerts = capturedAlerts();
                    boolean sentImpossible = s.fired() && s.request.contains("31/02/2026");
                    boolean ok = lettersRejected && !sentImpossible && (s.status < 500);
                    String a = "Letters -> box \"" + letters[0] + "\", model \"" + letters[1] + "\" (" + (lettersRejected ? "rejected" : "ACCEPTED") + ");"
                            + " 31/02/2026 -> box \"" + impossible[0] + "\", model \"" + impossible[1] + "\"; Search: " + (s.fired() ? s.request + " " + s : s.toString())
                            + "; messages " + alerts;
                    if (sentImpossible) a += " — DEFECT: the impossible date reached the server without any validation message";
                    return verdict(ok, a);
                });

        tc("RI-07", "Verify search with empty fields",
                "1. Put From Store and To Store on --Select--. 2. Clear From Date and To Date. 3. Click Search.",
                "Either a message asks for the mandatory date range, or the search completes without error (HTTP 200, no script error).",
                () -> {
                    ri.clearStore(F); ri.clearStore(T);
                    ri.typeDate(FD, ""); ri.typeDate(TD, "");
                    int pe = pageErrors.size();
                    interceptAlerts();
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.SearchResult s = ri.search();
                    List<String> alerts = capturedAlerts();
                    boolean ok = (!s.fired() && !alerts.isEmpty()) || (s.fired() && s.status == 200 && pageErrors.size() == pe);
                    return verdict(ok, (s.fired() ? "Search sent " + s.request + " -> " + s : "No search sent") + "; messages " + alerts
                            + (s.fired() && alerts.isEmpty() ? "; OBSERVATION: no field is mandatory - an empty search lists every return" : ""));
                });

        tc("RI-11", "Verify pagination",
                "1. Search all stores " + WIDE_FROM + " to today. 2. Page size 5: read pages, click next / last / first. 3. Change page size to 10.",
                "Pages hold page-size rows; next shows different records; last page holds the remainder; a larger page size reduces the page count.",
                () -> {
                    ri.clearStore(F); ri.clearStore(T); ri.typeDate(FD, WIDE_FROM); ri.typeDate(TD, today);
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.SearchResult s = ri.search();
                    int total = s.rows;
                    if (ri.setPageSize(MG, 5) != 5) return fail("Page size 5 could not be chosen");
                    int[] p = ri.pager(MG);
                    if (total <= 5) return skip(total + " record(s) — one page only; pagination not exercisable with this data (" + s + ")");
                    List<String> page1 = ri.renderedField(MG, "returnnumber");
                    boolean nextOk = ri.pagerClick(MG, "next");
                    List<String> page2 = ri.renderedField(MG, "returnnumber");
                    int[] p2 = ri.pager(MG);
                    ri.pagerClick(MG, "last");
                    int[] pl = ri.pager(MG);
                    int lastRows = ri.renderedRows(MG);
                    ri.pagerClick(MG, "first");
                    int[] pf = ri.pager(MG);
                    int size10 = ri.setPageSize(MG, 10);
                    int[] p10 = ri.pager(MG);
                    int expectLast = total % 5 == 0 ? 5 : total % 5;
                    boolean ok = p[1] == (total + 4) / 5 && page1.size() == 5 && nextOk && p2[0] == 2 && Collections.disjoint(page1, page2)
                            && pl[0] == p[1] && lastRows == expectLast && pf[0] == 1 && size10 == 10 && p10[1] == (total + 9) / 10;
                    ri.setPageSize(MG, 5);
                    return verdict(ok, total + " records; size 5 -> " + p[1] + " pages; page 1 " + page1 + "; next -> page " + p2[0] + " " + page2
                            + "; last -> page " + pl[0] + " with " + lastRows + " row(s) (expected " + expectLast + "); first -> page " + pf[0]
                            + "; size 10 -> " + p10[1] + " pages");
                });

        tc("RI-12", "Verify sorting",
                "1. With the all-store result on screen, click the Return Number header twice. 2. Click the Total Amount header twice.",
                "Rows are re-ordered ascending then descending on each column.",
                () -> {
                    if (ri.masterRows() < 2) return skip("fewer than 2 rows — sorting not exercisable");
                    ri.setPageSize(MG, 75);
                    String d1 = ri.clickHeader(MG, "Return Number");
                    List<String> rn1 = ri.renderedField(MG, "returnnumber");
                    String d2 = ri.clickHeader(MG, "Return Number");
                    List<String> rn2 = ri.renderedField(MG, "returnnumber");
                    String d3 = ri.clickHeader(MG, "Total Amount");
                    List<Double> am1 = nums(ri.renderedField(MG, "totalamount"));
                    String d4 = ri.clickHeader(MG, "Total Amount");
                    List<Double> am2 = nums(ri.renderedField(MG, "totalamount"));
                    boolean ok = "asc".equals(d1) && sorted(rn1, false) && "desc".equals(d2) && sorted(rn2, true)
                            && "asc".equals(d3) && sortedNum(am1, false) && "desc".equals(d4) && sortedNum(am2, true);
                    ri.setPageSize(MG, 5);
                    return verdict(ok, "Return Number " + d1 + " " + rn1 + " / " + d2 + " " + rn2 + "; Total Amount " + d3 + " " + am1 + " / " + d4 + " " + am2);
                });

        tc("RI-23", "Verify individual filter functionality (grid column filter)",
                "1. Type a full Return Number into the Return Number column filter. 2. Type a value that matches nothing. 3. Clear the filter.",
                "Only the matching record is shown; a non-matching value shows no row; clearing restores all rows.",
                () -> {
                    List<String> all = ri.masterField("returnnumber");
                    if (all.isEmpty()) return skip("no rows to filter");
                    ri.setPageSize(MG, 75);
                    String target = all.get(all.size() - 1);
                    ri.filterColumn(MG, 0, target);
                    List<String> shown = ri.renderedField(MG, "returnnumber");
                    ri.filterColumn(MG, 0, "NO_SUCH_RETURN_ZZ");
                    int none = ri.renderedRows(MG);
                    ri.filterColumn(MG, 0, "");
                    int back = ri.renderedRows(MG);
                    ri.setPageSize(MG, 5);
                    boolean ok = !shown.isEmpty() && shown.stream().allMatch(x -> x.contains(target)) && none == 0 && back == Math.min(75, all.size());
                    return verdict(ok, "Filter \"" + target + "\" -> " + shown + "; non-matching -> " + none + " row(s); cleared -> " + back + " row(s) of " + all.size());
                });

        tc("RI-25", "Verify horizontal scrolling",
                "1. Measure the Return grid and the item detail grid viewports. 2. Scroll each one fully right.",
                "When the columns are wider than the grid, it scrolls horizontally and the header scrolls with the rows.",
                () -> {
                    int[] m = ri.horizontalScroll(MG), d = ri.horizontalScroll(DG);
                    boolean mNeeds = m[0] > m[1] + 2, dNeeds = d[0] > d[1] + 2;
                    String a = "Return grid content " + m[0] + "px in " + m[1] + "px (scrolled to " + m[2] + ", header " + m[3] + "); detail grid " + d[0] + "px in " + d[1]
                            + "px (scrolled to " + d[2] + ", header " + d[3] + ")";
                    if (!mNeeds && !dNeeds) return skip(a + " — all columns fit at this window width; no horizontal scroll needed");
                    boolean ok = (!mNeeds || (m[2] > 0 && Math.abs(m[2] - m[3]) <= 2)) && (!dNeeds || (d[2] > 0 && Math.abs(d[2] - d[3]) <= 2));
                    return verdict(ok, a);
                });

        // ---------------------------------------------------------------- filters / dates
        tc("RI-24", "Verify combination of multiple filters",
                "1. From Store = default store. 2. To Store = the store most returned to. 3. From " + WIDE_FROM + " to today. 4. Search.",
                "The request carries both store ids and the dates; every row matches both stores; no more rows than with From Store alone.",
                () -> {
                    if (validRecords.isEmpty()) return skip("RI-05 found no records for the default store - nothing to combine with");
                    Map<String, Long> byTo = validRecords.stream().map(r -> r.split(" \\| ")[3].trim()).collect(Collectors.groupingBy(x -> x, LinkedHashMap::new, Collectors.counting()));
                    String to = byTo.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
                    String e1 = ri.pickStore(F, defaultFromStore), e2 = ri.pickStore(T, to);
                    ri.typeDate(FD, WIDE_FROM); ri.typeDate(TD, today);
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.SearchResult s = ri.search();
                    List<String> froms = ri.masterField("FromStoreName"), tos = ri.masterField("ToStoreName");
                    boolean ok = e1.isEmpty() && e2.isEmpty() && s.status == 200 && s.request.contains("\"tostoreid\":") && !s.request.contains("\"tostoreid\":null")
                            && froms.stream().allMatch(x -> x.trim().equalsIgnoreCase(defaultFromStore)) && tos.stream().allMatch(x -> x.trim().equalsIgnoreCase(to))
                            && s.rows == byTo.get(to).intValue() && s.rows <= validRecords.size();
                    ri.clearStore(T);
                    return verdict(ok, "From \"" + defaultFromStore + "\" + To \"" + to + "\" -> " + s + " (expected " + byTo.get(to) + " of " + validRecords.size() + ");"
                            + " request " + s.request + (e1 + e2).trim());
                });

        tc("RI-08", "Verify date range filtering",
                "1. From Store = default store. 2. Set From and To to the date of the latest return found by RI-05. 3. Search. 4. Check every row's Return Date.",
                "Only returns dated inside the range are listed, and the record of that date is among them.",
                () -> {
                    if (validRecords.isEmpty()) return skip("RI-05 found no records - no known date to filter on");
                    LocalDate latest = null; String latestNo = "";
                    for (String r : validRecords) {
                        String[] p = r.split(" \\| ");
                        LocalDate d = parseGridDate(p[1].trim());
                        if (d != null && (latest == null || d.isAfter(latest))) { latest = d; latestNo = p[0].trim(); }
                    }
                    if (latest == null) return fail("Return Date values could not be read as dates: " + validRecords);
                    ri.typeDate(FD, latest.format(DMY)); ri.typeDate(TD, latest.format(DMY));
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.SearchResult s = ri.search();
                    List<String> dates = ri.masterField("returndate"), nos = ri.masterField("returnnumber");
                    final LocalDate day = latest;
                    boolean inRange = dates.stream().allMatch(x -> day.equals(parseGridDate(x.trim())));
                    boolean ok = s.status == 200 && inRange && nos.contains(latestNo) && s.request.contains(latest.format(DMY));
                    return verdict(ok, "Range " + latest.format(DMY) + " - " + latest.format(DMY) + " -> " + s + "; dates " + dates + "; returns " + nos
                            + " (expected to include " + latestNo + ")");
                });

        tc("RI-21", "Verify From Date later than To Date",
                "1. From Date = today. 2. To Date = " + WIDE_FROM + ". 3. Click Search.",
                "A warning says From Date cannot be later than To Date, and no search is sent.",
                () -> {
                    ri.typeDate(FD, today); ri.typeDate(TD, WIDE_FROM);
                    interceptAlerts();
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.SearchResult s = ri.search();
                    List<String> alerts = capturedAlerts();
                    boolean warned = alerts.stream().anyMatch(a -> a.toLowerCase().matches(".*(from date|to date|greater|less|after|before).*"));
                    return verdict(warned && !s.fired(), "Messages " + alerts + "; search " + (s.fired() ? "SENT " + s.request + " -> " + s : "not sent")
                            + (warned ? "" : " — DEFECT: the reversed range is accepted without a date-range warning"));
                });

        tc("RI-22", "Verify empty data message",
                "1. From Date 01/01/2030, To Date 31/01/2030 (no returns can exist). 2. Click Search.",
                "HTTP 200 with no rows, the grid is empty and a \"No records found\" message is shown.",
                () -> {
                    ri.typeDate(FD, "01/01/2030"); ri.typeDate(TD, "31/01/2030");
                    interceptAlerts();
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.SearchResult s = ri.search();
                    List<String> alerts = capturedAlerts();
                    boolean msg = alerts.stream().anyMatch(a -> a.toLowerCase().contains("no record"));
                    return verdict(s.status == 200 && s.rows == 0 && ri.renderedRows(MG) == 0 && msg, s + "; messages " + alerts
                            + "; grid text \"" + ri.emptyGridMessage(MG) + "\"");
                });

        // ---------------------------------------------------------------- grid content, view, print (on the valid search result)
        tc("RI-09", "Verify grid headers",
                "1. Read the Return grid header labels. 2. Read the item detail grid header labels.",
                "Return grid: " + com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.MASTER_HEADERS + "; detail grid: "
                        + com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.DETAIL_HEADERS,
                () -> {
                    List<String> m = ri.headers(MG), d = ri.headers(DG);
                    List<String> missM = new ArrayList<>(com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.MASTER_HEADERS); missM.removeAll(m);
                    List<String> missD = new ArrayList<>(com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.DETAIL_HEADERS); missD.removeAll(d);
                    return verdict(missM.isEmpty() && missD.isEmpty(), "Return grid " + m + (missM.isEmpty() ? "" : " MISSING " + missM)
                            + "; detail grid " + d + (missD.isEmpty() ? "" : " MISSING " + missD));
                });

        tc("RI-10", "Verify grid data",
                "1. Re-run the valid search (default From Store, " + WIDE_FROM + " - today). 2. Compare the grid rows with the API response."
                        + " 3. Check each row's mandatory values and amounts.",
                "Grid rows = response rows; first page shows min(page size, total) rows; every row has a Return Number, Return Date, both stores and numeric amounts.",
                () -> {
                    ri.pickStore(F, defaultFromStore); ri.clearStore(T); ri.typeDate(FD, WIDE_FROM); ri.typeDate(TD, today);
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.SearchResult s = ri.search();
                    if (s.rows <= 0) return skip("no Return Item records for \"" + defaultFromStore + "\" (" + s + ") - grid data not checkable");
                    int pageSize = ri.pager(MG)[2], drawn = ri.renderedRows(MG);
                    List<String> recs = ri.masterRecords();
                    List<String> incomplete = recs.stream().filter(r -> { String[] p = r.split(" \\| ", -1);
                        return p[0].isBlank() || p[1].isBlank() || p[2].isBlank() || p[3].isBlank() || !isNum(p[4]) || !isNum(p[5]) || !isNum(p[6]); })
                            .collect(Collectors.toList());
                    List<String> noReturnBy = recs.stream().filter(r -> r.endsWith("| ")).map(r -> r.split(" \\| ")[0]).collect(Collectors.toList());
                    boolean ok = s.status == 200 && drawn == Math.min(pageSize, s.rows) && incomplete.isEmpty() && s.rows == validRecords.size();
                    return verdict(ok, s.rows + " rows (RI-05 had " + validRecords.size() + "), drawn on page 1: " + drawn + " (page size " + pageSize + ");"
                            + (incomplete.isEmpty() ? " all rows complete" : " INCOMPLETE rows " + incomplete)
                            + (noReturnBy.isEmpty() ? "" : "; OBSERVATION: \"Return By\" is blank for " + noReturnBy));
                });

        String[] selected = new String[1];
        tc("RI-14", "Verify View functionality (row click shows item details)",
                "1. Click the first record in the Return grid. 2. Wait for its items. 3. Read the item detail grid.",
                "The record is selected and its returned items load (HTTP 200) into the detail grid; no Edit is offered on this list screen.",
                () -> {
                    if (ri.masterRows() <= 0) return skip("no record to view");
                    int st = ri.clickMasterRow(0);
                    selected[0] = ri.selectedReturn();
                    int items = ri.detailRows();
                    List<String> codes = ri.renderedField(DG, "serviceitemcode"), names = ri.renderedField(DG, "serviceitemname");
                    boolean ok = st == 200 && !selected[0].isEmpty() && items > 0;
                    return verdict(ok, "Row click -> item fetch HTTP " + st + "; selected " + selected[0] + "; " + items + " item(s): " + codes + " " + names
                            + "; View/Edit buttons: none on this screen (details open on row click)");
                });

        tc("RI-15", "Verify Print functionality",
                "1. With the record selected in RI-14, click Print. 2. Capture the report tab, check it loads, close it.",
                "A report tab opens for the selected return (Returnid = its id, From/To store = the record's stores) and returns a PDF (HTTP 200).",
                () -> {
                    if (selected[0] == null || selected[0].isEmpty()) return skip("no record selected (RI-14)");
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.PrintResult p = ri.print(com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.PRINT_BTN);
                    if (p.url.isEmpty()) return fail("No report opened: " + p.dialog + " " + p.error);
                    boolean pdf = p.status == 200 && p.contentType.contains("pdf") && p.error.isEmpty();
                    boolean nullParam = p.url.matches(".*=(null|undefined)(&.*|$)");
                    String a = "Report " + route(p.url).replaceFirst("^https?://[^/]+", "") + " -> HTTP " + p.status + " " + p.contentType + " " + p.error;
                    if (nullParam) a += " — DEFECT: the report URL carries a null/undefined parameter (Print takes the store ids from the search filters,"
                            + " not from the selected record, so To Store is null when the To Store filter is empty) - verify the printed content";
                    return verdict(pdf && !nullParam, a);
                });

        tc("RI-31", "Verify Print with the store filters left empty",
                "1. Keep the record selected in RI-14. 2. Put From Store and To Store on --Select-- (filters only, no data change). 3. Click Print. 4. Check the report tab.",
                "The report of the selected return opens as a PDF (HTTP 200) - the filters should not matter once a record is selected.",
                () -> {
                    if (selected[0] == null || selected[0].isEmpty()) return skip("no record selected (RI-14)");
                    ri.clearStore(F); ri.clearStore(T);
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.PrintResult p = ri.print(com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.PRINT_BTN);
                    ri.pickStore(F, defaultFromStore);
                    if (p.url.isEmpty()) return fail("No report opened: " + p.dialog + " " + p.error);
                    boolean ok = p.status == 200 && p.contentType.contains("pdf") && p.error.isEmpty();
                    return verdict(ok, "Report " + p.url.replaceFirst("^https?://[^/]+", "") + " -> HTTP " + p.status + " " + p.contentType + " " + p.error
                            + (ok ? "" : " — DEFECT: Print sends the search filters' store ids (null here) instead of the selected record's stores, and the report server fails"));
                });

        tc("RI-19", "Verify Patient Print functionality",
                "1. With the record selected in RI-14, click Patient Print. 2. Capture the report tab, check it loads, close it.",
                "A report tab opens for the selected return and returns a PDF (HTTP 200).",
                () -> {
                    if (selected[0] == null || selected[0].isEmpty()) return skip("no record selected (RI-14)");
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.PrintResult p = ri.print(com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.PATIENT_PRINT_BTN);
                    if (p.url.isEmpty()) return fail("No report opened: " + p.dialog + " " + p.error);
                    boolean ok = p.status == 200 && p.contentType.contains("pdf") && p.error.isEmpty() && !p.url.matches(".*=(null|undefined)(&.*|$)");
                    return verdict(ok, "Report " + p.url.replaceFirst("^https?://[^/]+", "") + " -> HTTP " + p.status + " " + p.contentType + " " + p.error
                            + "; NOTE: this is the inpatient-return report layout - confirm manually it suits a store return");
                });

        tc("RI-28", "Verify New (create Return Item) - not executed",
                "1. Check the New button is visible and enabled. 2. Do NOT click it.",
                "New opens the Return Item entry form (#/add-ReturnItem) where a transaction is created.",
                () -> manual("New is " + controls.getOrDefault("New", "?") + " but was not clicked: it leads to creating and saving a Return Item transaction,"
                        + " which this read-only run must not do. Verify the entry form manually on a test store."));

        tc("RI-29", "Verify grid menu / export - export not executed",
                "1. Open the Return grid's menu (☰). 2. List its entries. 3. Close it without clicking any.",
                "The menu opens with column and export entries; exporting downloads a file.",
                () -> {
                    // Export is never executed in this read-only run, so this case is MANUAL whatever happens while the menu is listed.
                    String entries;
                    try {
                        List<String> items = ri.gridMenuEntries(MG);
                        entries = items == null ? "the grid shows no menu button" : "menu entries " + items;
                    } catch (Throwable e) {
                        entries = "the menu entries could not be listed (" + firstLine(e) + ")";
                    }
                    return manual("Export not executed (it downloads a file; read-only run). " + entries + ". Verify the export contents manually.");
                });

        // ---------------------------------------------------------------- reset / stability / monitoring
        tc("RI-13", "Verify Clear/Reset functionality",
                "1. Look for a Clear or Reset button. 2. Change the filters, re-open the screen from the menu and read the filters.",
                "A Clear/Reset button restores the default filters.",
                () -> {
                    boolean hasReset = (Boolean) page.evaluate("() => [...document.querySelectorAll('button,a.btn,input[type=button]')].some(b=>b.offsetParent!==null && /^(clear|reset)$/i.test((b.innerText||b.value||'').trim()))");
                    if (hasReset) return fail("A Clear/Reset button exists but this script does not cover it yet - extend RI-13");
                    ri.pickStore(T, ri.storeNames(T).get(0)); ri.typeDate(FD, WIDE_FROM);
                    String why = ri.reload();
                    String a = "No Clear/Reset button on this screen. Re-opening from the menu " + (why.isEmpty() ? "restored: From Store \"" + ri.chosen(F)
                            + "\", To Store \"" + ri.chosen(T) + "\", dates " + ri.fieldValue(FD) + " - " + ri.fieldValue(TD) + ", grid rows " + ri.masterRows() : "failed: " + why);
                    return skip(a);
                });

        tc("RI-18", "Verify page stability",
                "1. Run the same valid search twice. 2. Check the session never left for the login page, the screen route is kept,"
                        + " no browser dialog / download appeared and no script error occurred.",
                "Same result both times; no redirect, no logout, no page error.",
                () -> {
                    ri.typeDate(FD, WIDE_FROM); ri.typeDate(TD, today);
                    com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.SearchResult a = ri.search(), b = ri.search();
                    List<String> offRoute = navigations.stream().filter(n -> n.startsWith("#/") && !onScreenRoute(n) && !n.startsWith("#/PatientDashboard")
                            && !n.startsWith("#/LandingPage")).distinct().collect(Collectors.toList());
                    List<String> screenErrors = pageErrors.stream().filter(e -> e.startsWith("[" + com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.ROUTE)).collect(Collectors.toList());
                    List<String> nav = new ArrayList<>(navigations);
                    int firstApp = 0;
                    while (firstApp < nav.size() && !nav.get(firstApp).startsWith("#/")) firstApp++;
                    boolean loggedOut = false;
                    for (int i = firstApp + 1; i < nav.size(); i++) if (nav.get(i).toLowerCase().contains("/account")) loggedOut = true;
                    boolean ok = a.status == 200 && b.status == 200 && a.rows == b.rows && ri.onScreen() && screenErrors.isEmpty() && nativeDialogs.isEmpty() && downloads.isEmpty()
                            && !loggedOut && offRoute.isEmpty();
                    return verdict(ok, "Search x2 -> " + a + " / " + b + "; still on " + route(page.url()) + "; logged out " + loggedOut + "; navigations " + new LinkedHashSet<>(nav)
                            + "; unexpected routes " + offRoute + "; page errors on screen " + screenErrors + "; dialogs " + nativeDialogs + "; downloads " + downloads);
                });

        tc("RI-16", "Verify API responses",
                "1. Record every /api/ request the run made (method, endpoint, status, time). 2. Separate the Return Item screen's calls from login/dashboard calls.",
                "Every Return Item API answers HTTP 2xx; nothing fails, nothing is redirected.",
                () -> {
                    List<ApiCall> calls = new ArrayList<>(api);
                    List<ApiCall> screen = calls.stream().filter(c -> c.path.startsWith("/api/") && onScreenRoute(c.route) && !isBlocked(c)).collect(Collectors.toList());
                    List<String> bad = screen.stream().filter(c -> c.status < 200 || c.status >= 300).map(this::fmt).collect(Collectors.toList());
                    List<String> other = calls.stream().filter(c -> c.path.startsWith("/api/") && !onScreenRoute(c.route) && (c.status < 200 || c.status >= 400) && !isBlocked(c))
                            .map(this::fmt).distinct().collect(Collectors.toList());
                    return verdict(bad.isEmpty() && !screen.isEmpty(), screen.size() + " Return Item API call(s): " + endpointSummary(screen)
                            + (bad.isEmpty() ? "; none failed" : "; FAILED " + bad) + (other.isEmpty() ? "" : "; outside this screen (not counted): " + other));
                });

        tc("RI-17", "Verify console errors",
                "1. Collect browser console errors and uncaught page errors during the run. 2. Separate those raised on the Return Item screen.",
                "No console error and no page error on the Return Item screen.",
                () -> {
                    List<String> screen = consoleErrors.stream().filter(e -> e.startsWith("[" + com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.ROUTE)).distinct().collect(Collectors.toList());
                    List<String> pe = pageErrors.stream().filter(e -> e.startsWith("[" + com.kpj.pages.Inventory_page.Transfer_page.ReturnItem.ROUTE)).distinct().collect(Collectors.toList());
                    List<String> elsewhere = consoleErrors.stream().filter(e -> !screen.contains(e)).distinct().collect(Collectors.toList());
                    return verdict(screen.isEmpty() && pe.isEmpty(), "Console errors on screen: " + (screen.isEmpty() ? "none" : screen)
                            + "; page errors on screen: " + (pe.isEmpty() ? "none" : pe) + (elsewhere.isEmpty() ? "" : "; elsewhere (login/dashboard, not counted): " + elsewhere));
                });

        tc("RI-30", "Verify read-only safety",
                "1. Count write calls the guard blocked. 2. Count downloads and browser dialogs. 3. Confirm New / Save / export were never clicked.",
                "No write call attempted, nothing downloaded, no transaction created.",
                () -> verdict(blockedWrites.isEmpty() && downloads.isEmpty(), "Blocked write calls: " + (blockedWrites.isEmpty() ? "none" : blockedWrites)
                        + "; downloads: " + (downloads.isEmpty() ? "none" : downloads) + "; dialogs: " + (nativeDialogs.isEmpty() ? "none" : nativeDialogs)
                        + "; New, Save, grid export and confirm actions were never clicked"));
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
        addSummary("Automation errors (script could not complete the case - not application defects)", orNone(cases.stream().filter(c -> c.automationError)
                .map(c -> c.id + " " + c.scenario).collect(Collectors.toList())));
        List<ApiCall> calls = new ArrayList<>(api);
        addSummary("API calls recorded", calls.size() + " (" + calls.stream().filter(c -> onScreenRoute(c.route)).count() + " on the Return Item screen)");
        addSummary("API failures (HTTP >= 400 / network)", orNone(calls.stream().filter(c -> (c.status >= 400 || c.status == 0) && !isBlocked(c))
                .map(this::fmt).distinct().collect(Collectors.toList())));
        addSummary("Console errors", orNone(new ArrayList<>(new LinkedHashSet<>(consoleErrors))));
        addSummary("Page (script) errors", orNone(new ArrayList<>(new LinkedHashSet<>(pageErrors))));
        addSummary("Blocked write calls (read-only guard)", orNone(blockedWrites));
        addSummary("Finished", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + " (started " + startedAt + ")");
        System.out.println("Return Item cases: " + counts);
    }

    private String fmt(ApiCall c) {
        return c.method + " " + c.path + " -> " + (c.status == 0 ? "NETWORK " + c.failure : "HTTP " + c.status) + " (" + c.ms + " ms) [" + c.route + "]";
    }

    private static String endpointSummary(List<ApiCall> calls) {
        Map<String, List<ApiCall>> g = calls.stream().collect(Collectors.groupingBy(c -> c.method + " " + c.path, TreeMap::new, Collectors.toList()));
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

    private static boolean isNum(String s) { try { Double.parseDouble(s.trim()); return true; } catch (Exception e) { return false; } }

    private static List<Double> nums(List<String> l) {
        List<Double> out = new ArrayList<>();
        for (String s : l) { try { out.add(Double.parseDouble(s.trim())); } catch (Exception e) { out.add(Double.NaN); } }
        return out;
    }

    private static boolean sorted(List<String> l, boolean desc) {
        for (int i = 1; i < l.size(); i++) { int c = l.get(i - 1).compareToIgnoreCase(l.get(i)); if (desc ? c < 0 : c > 0) return false; }
        return !l.isEmpty();
    }

    private static boolean sortedNum(List<Double> l, boolean desc) {
        for (int i = 1; i < l.size(); i++) { int c = Double.compare(l.get(i - 1), l.get(i)); if (desc ? c < 0 : c > 0) return false; }
        return !l.isEmpty();
    }

    private static LocalDate parseGridDate(String s) {
        try { return LocalDate.parse(s, GRID_DATE); } catch (Exception e) { }
        try { return LocalDate.parse(s, DMY); } catch (Exception e) { return null; }
    }
}
