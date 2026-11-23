package com.kpj.tests.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Inventory_page.InventoryScreen;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared plumbing for the READ-ONLY Inventory screen tests ({@code Transfer_page.ReceiveReturnItem}, {@code Transfer_page.ReceiveIssueItem},
 * {@code Others_page.GatePassInList}). It holds NO page-specific locators, cases or workflows — each subclass defines its own.
 *
 * <p>Same conventions as the existing {@code Transfer_page.ReturnItem} test: one login per class, every check a case with its own ID, its own
 * JUnit dynamic test and its own row in the framework's standard HTML report ({@link DevHisBase} → {@code HtmlReport}). Additions:</p>
 * <ul>
 *   <li>Status <b>BLOCKED</b> = the data the case needs does not exist (or the environment prevents it) — NOT an application defect.
 *       FAIL = the application did not behave as expected (or, marked AUTOMATION ERROR, the script could not complete the case).</li>
 *   <li>Per page: report {@code test-output/reports/<testId>.html}, summary {@code <testId>_summary.txt}, execution log
 *       {@code test-output/logs/<testId>.log}, and a PNG per FAIL / BLOCKED case in {@code test-output/screenshots/<page>/}.</li>
 *   <li>API monitoring: every /api/ request and response (method, path, ExecFlag, status, time, row count) goes into the log;
 *       failures and blocked writes go into the report summary.</li>
 * </ul>
 *
 * <p><b>Read-only guard</b> (context-wide, so popups are covered too): any /api/ call whose endpoint or ExecFlag looks like a write
 * (IUD / Save / Insert / Update / Delete / Approve / Cancel / Submit / Receive...) is ABORTED before it reaches the server. A case that clicks
 * Save to observe validation does so only on a form it has verified to be incomplete; if the app still tries to save, the attempt is aborted,
 * recorded, and the case FAILS.</p>
 *
 * <p>Credentials come only from DEVHIS_USER / DEVHIS_PASS (or -Ddevhis.user / -Ddevhis.pass, or config.properties); without them the run stops
 * instead of falling back to any built-in account.</p>
 */
public abstract class InventoryReadOnlyCases extends DevHisBase {

    private final String title, module, shotFolder, logTag;

    protected InventoryReadOnlyCases(String testId, String title, String module, String shotFolder) {
        super(testId);
        this.title = title; this.module = module; this.shotFolder = shotFolder; this.logTag = shotFolder;
    }

    /** The page's own cases, in execution order. Called after a successful login. */
    protected abstract void runCases();

    /** The page object (used to clear overlays between cases). */
    protected abstract InventoryScreen screen();

    /** Prefix of this page's case IDs (e.g. "RRI"). */
    protected abstract String idPrefix();

    // ======================================================================= JUnit: one dynamic test per case

    @TestFactory
    public Stream<DynamicTest> execute() {
        try { run(); } finally { stop(); }
        return cases.stream().sorted(Comparator.comparing(c -> c.id)).map(c -> DynamicTest.dynamicTest(c.id + " - " + c.scenario, () -> {
            switch (c.status) {
                case "PASS": break;
                case "BLOCKED": Assumptions.abort("BLOCKED: " + c.actual); break;
                default: Assertions.fail(c.id + " FAILED - expected: " + c.expected + " | actual: " + c.actual);
            }
        }));
    }

    // ======================================================================= case bookkeeping

    protected static final class Case {
        final String id, scenario, steps, expected;
        String actual = "", status = "FAIL";
        boolean automationError = false, defect = false;
        byte[] png;
        Case(String id, String scenario, String steps, String expected) { this.id = id; this.scenario = scenario; this.steps = steps; this.expected = expected; }
    }

    protected static final class Outcome {
        final String status, actual; final boolean defect;
        Outcome(String status, String actual, boolean defect) { this.status = status; this.actual = actual; this.defect = defect; }
    }

    protected static Outcome pass(String a) { return new Outcome("PASS", a, false); }
    /** The application did not behave as expected. */
    protected static Outcome defect(String a) { return new Outcome("FAIL", "DEFECT: " + a, true); }
    /** The data / environment the case needs is missing — not an application defect. */
    protected static Outcome blocked(String a) { return new Outcome("BLOCKED", a, false); }
    protected static Outcome verdict(boolean ok, String a) { return ok ? pass(a) : defect(a); }

    protected final List<Case> cases = new ArrayList<>();
    private Path shotsDir, logFile;
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());

    /** Runs one case; an exception becomes FAIL (AUTOMATION ERROR). A screenshot follows every case; FAIL / BLOCKED also get a PNG file. */
    protected Case tc(String id, String scenario, String steps, String expected, Supplier<Outcome> body) {
        Case c = new Case(id, scenario, steps, expected);
        long t0 = System.currentTimeMillis();
        log("---- " + id + " " + scenario);
        String cleared = "";
        try { cleared = screen() == null ? "" : screen().closeOverlays(); } catch (Throwable ignore) { }
        try {
            Outcome o = body.get();
            c.status = o.status; c.actual = o.actual; c.defect = o.defect;
        } catch (Throwable e) {
            c.status = "FAIL";
            StackTraceElement at = Arrays.stream(e.getStackTrace()).filter(f -> f.getClassName().startsWith("com.kpj")).findFirst().orElse(null);
            String where = at == null ? "" : " at " + at.getClassName().replaceFirst("^com\\.kpj\\.", "") + ":" + at.getLineNumber();
            boolean timeout = e.getClass().getSimpleName().contains("Timeout") || String.valueOf(e.getMessage()).contains("Timeout");
            c.actual = "AUTOMATION ERROR (not verified as an application defect): " + e.getClass().getSimpleName() + where + " - " + oneLine(String.valueOf(e.getMessage()), 400)
                    + (timeout ? " - the script waited for something that did not happen; see the screenshot" : "");
            c.automationError = true;
            try { if (screen() != null) screen().closeOverlays(); } catch (Throwable ignore) { }
        }
        if (!cleared.isEmpty()) c.actual = "(before this case: " + cleared + ") " + c.actual;
        c.actual += " [execution time " + String.format(Locale.ROOT, "%.1f", (System.currentTimeMillis() - t0) / 1000.0) + " s]";
        try { c.png = page.screenshot(new Page.ScreenshotOptions().setTimeout(10000)); } catch (Throwable ignore) { }
        if (!"PASS".equals(c.status) && c.png != null) {
            try { Files.createDirectories(shotsDir); Files.write(shotsDir.resolve(id + "_" + c.status + ".png"), c.png); } catch (Exception ignore) { }
        }
        cases.add(c);
        log(String.format("%-8s [%-7s] %s -> %s", id, c.status, scenario, oneLine(c.actual, 600)));
        System.out.printf("  %-8s [%-7s] %s%n", id, c.status, scenario);
        return c;
    }

    protected void log(String line) {
        log.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + " " + line);
    }

    // ======================================================================= monitoring + read-only guard

    protected static final class ApiCall {
        final String method, path, flag, route, failure; final int status; final long ms;
        ApiCall(String method, String path, String flag, int status, long ms, String route, String failure) {
            this.method = method; this.path = path; this.flag = flag; this.status = status; this.ms = ms; this.route = route; this.failure = failure; }
    }

    protected final List<ApiCall> api = Collections.synchronizedList(new ArrayList<>());
    protected final List<String> blockedWrites = Collections.synchronizedList(new ArrayList<>());
    private final List<String> consoleErrors = Collections.synchronizedList(new ArrayList<>());
    private final List<String> pageErrors = Collections.synchronizedList(new ArrayList<>());
    private final List<String> downloads = Collections.synchronizedList(new ArrayList<>());
    private final List<String> nativeDialogs = Collections.synchronizedList(new ArrayList<>());
    private final List<String> popups = Collections.synchronizedList(new ArrayList<>());
    private final Map<Request, Long> started = Collections.synchronizedMap(new IdentityHashMap<>());

    private static final Pattern WRITE_URL = Pattern.compile("(?i)/api/[^?]*?/(IUD|Save|Insert|Update|Delete|Remove|Approve|Reject|Cancel|Submit|Create|Post)[A-Za-z]*(\\?|$)");
    private static final Pattern READ_WORD = Pattern.compile("(?i).*(get|list|search|fetch|load|detail|view|select|bind|report|number|data).*");
    private static final Pattern WRITE_FLAG = Pattern.compile("(?i)\"ExecFlag\"\\s*:\\s*\"[^\"]*(Insert|Update|Delete|Save|Approve|Cancel|Submit|Reject)[^\"]*\"");
    private static final Pattern FLAG = Pattern.compile("(?i)\"ExecFlag\"\\s*:\\s*\"([^\"]*)\"");

    /** A request that would change data: write verb in the endpoint, a write ExecFlag, or a Receive... endpoint/flag that is not a read. */
    static boolean isWrite(String method, String url, String body) {
        if ("GET".equalsIgnoreCase(method)) return false;
        if (WRITE_URL.matcher(url).find() || WRITE_FLAG.matcher(body).find()) return true;
        String last = path(url).replaceFirst(".*/", "");
        if (last.matches("(?i)receiv.*") && !READ_WORD.matcher(last).matches()) return true;
        Matcher m = FLAG.matcher(body);
        return m.find() && !m.group(1).isEmpty() && m.group(1).matches("(?i).*receiv.*") && !READ_WORD.matcher(m.group(1)).matches();
    }

    protected static String path(String url) { return url.replaceFirst("^https?://[^/]+", "").replaceFirst("\\?.*$", ""); }

    private static String route(String url) {
        int i = url.indexOf('#');
        return i >= 0 ? url.substring(i).replaceFirst("\\?.*$", "") : url.replaceFirst("^https?://[^/]+", "");
    }

    private static String flagOf(String body) { Matcher m = FLAG.matcher(body == null ? "" : body); return m.find() ? m.group(1) : ""; }

    private void installMonitors() {
        ctx.route(Pattern.compile(".*/api/.*"), r -> {
            Request q = r.request();
            String body = q.postData() == null ? "" : q.postData();
            if (isWrite(q.method(), q.url(), body)) {
                String w = q.method() + " " + path(q.url()) + " " + oneLine(body, 200) + " [" + route(page.url()) + "]";
                blockedWrites.add(w);
                log("READ-ONLY GUARD aborted: " + w);
                r.abort("blockedbyclient");
            } else r.resume();
        });
        ctx.addInitScript(InventoryScreen.MESSAGE_WATCH_JS);
        ctx.onPage(p -> { if (p != page) { popups.add(p.url()); log("popup opened: " + p.url()); } });
        page.onRequest(q -> started.put(q, System.currentTimeMillis()));
        page.onResponse(resp -> {
            Request q = resp.request();
            if (!resp.url().contains("/api/")) return;
            Long t = started.remove(q);
            String flag = flagOf(q.postData());
            long ms = t == null ? -1 : System.currentTimeMillis() - t;
            api.add(new ApiCall(q.method(), path(resp.url()), flag, resp.status(), ms, route(page.url()), ""));
            log("API " + q.method() + " " + path(resp.url()) + (flag.isEmpty() ? "" : " (" + flag + ")") + " -> " + resp.status() + " " + ms + " ms"
                    + ("POST".equals(q.method()) ? " req=" + oneLine(String.valueOf(q.postData()), 220) : ""));
        });
        page.onRequestFailed(q -> {
            Long t = started.remove(q);
            if (!q.url().contains("/api/")) return;
            api.add(new ApiCall(q.method(), path(q.url()), flagOf(q.postData()), 0, t == null ? -1 : System.currentTimeMillis() - t, route(page.url()), String.valueOf(q.failure())));
        });
        page.onConsoleMessage(m -> { if ("error".equals(m.type())) consoleErrors.add("[" + route(page.url()) + "] " + oneLine(m.text(), 300)); });
        page.onPageError(e -> pageErrors.add("[" + route(page.url()) + "] " + oneLine(e, 300)));
        page.onDownload(d -> { downloads.add(d.suggestedFilename()); try { d.cancel(); } catch (Exception ignore) { } });
        page.onDialog(d -> { nativeDialogs.add(d.type() + ": " + d.message()); try { d.dismiss(); } catch (Exception ignore) { } });
    }

    /** Writes attempted (and aborted) since index {@code from} of {@link #blockedWrites}. */
    protected List<String> writesSince(int from) {
        List<String> l = new ArrayList<>(blockedWrites);
        return l.subList(Math.min(from, l.size()), l.size());
    }

    /** Distinct "METHOD path (flag)" of API calls since index {@code from}. */
    protected List<String> apisSince(int from) {
        List<ApiCall> l = new ArrayList<>(api);
        return l.subList(Math.min(from, l.size()), l.size()).stream().map(a -> a.method + " " + a.path + (a.flag.isEmpty() ? "" : " (" + a.flag + ")") + " " + a.status)
                .distinct().collect(Collectors.toList());
    }

    /** API calls on the current screen that failed (HTTP >= 400 or network, not counting the guard's own aborts). */
    protected List<String> failedApisSince(int from) {
        List<ApiCall> l = new ArrayList<>(api);
        return l.subList(Math.min(from, l.size()), l.size()).stream().filter(a -> (a.status >= 400 || a.status == 0) && !isBlocked(a))
                .map(this::fmt).distinct().collect(Collectors.toList());
    }

    // ======================================================================= dates

    protected static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    protected final String today = LocalDate.now().format(DMY);
    /** Start of the wide search window used to find existing records. */
    protected final String wideFrom = LocalDate.now().minusYears(2).withDayOfYear(1).format(DMY);
    /** A date well before today, used as the To date of a reversed range. */
    protected final String earlier = LocalDate.now().minusMonths(10).withDayOfMonth(1).format(DMY);

    private static final Pattern DATE_IN = Pattern.compile("(/Date\\((-?\\d+))|(\\d{4}-\\d{2}-\\d{2})|(\\d{1,2}[/-][A-Za-z]{3}[/-]\\d{2,4})|(\\d{1,2}/\\d{1,2}/\\d{4})|(\\d{1,2}-\\d{1,2}-\\d{4})");

    /** Parses the date part of a grid value: /Date(ms)/, ISO, dd-MMM-yy, dd/MM/yyyy... ; null when it is not a date. */
    protected static LocalDate parseAnyDate(String s) {
        if (s == null) return null;
        Matcher m = DATE_IN.matcher(s);
        if (!m.find()) return null;
        if (m.group(2) != null) return java.time.Instant.ofEpochMilli(Long.parseLong(m.group(2))).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        String d = m.group();
        for (String p : new String[]{"yyyy-MM-dd", "dd-MMM-yy", "dd-MMM-yyyy", "d-MMM-yy", "d-MMM-yyyy", "dd/MMM/yyyy", "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy"}) {
            try { return LocalDate.parse(d, DateTimeFormatter.ofPattern(p, Locale.ENGLISH)); } catch (Exception ignore) { }
        }
        return null;
    }

    protected static double num(String s) {
        try { return Double.parseDouble(String.valueOf(s).replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    protected static boolean same(double a, double b) { return !Double.isNaN(a) && !Double.isNaN(b) && Math.abs(a - b) < 0.011; }

    protected static String money(double d) { return Double.isNaN(d) ? "n/a" : String.format(Locale.ROOT, "%.2f", d); }

    // ======================================================================= the test

    @Override
    protected final void body() {
        String startedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        meta(title, module, "READ-ONLY run: write APIs are aborted by the guard before they reach the server; Save is clicked only on a form verified to be"
                + " incomplete, to observe the validation that must block it. Each row is one test case; Description = its steps."
                + " PASS = behaved as expected; FAIL = application defect (marked DEFECT) or AUTOMATION ERROR; BLOCKED = the data the case needs does not exist"
                + " (not an application defect).");
        addSummary("Execution date / time", startedAt);
        addSummary("Environment", BASE);
        Path out = reportDir.getParent() == null ? Paths.get("test-output") : reportDir.getParent();
        shotsDir = out.resolve("screenshots").resolve(shotFolder);
        logFile = out.resolve("logs").resolve(testId + ".log");
        log("START " + title + " on " + BASE);
        if (!credentialsProvided()) {
            addSummary("Login user", "(not set)");
            tc(idPrefix() + "-00", "Credentials available", "1. Read DEVHIS_USER / DEVHIS_PASS (or -Ddevhis.user / -Ddevhis.pass, or config.properties).",
                    "Credentials are provided by the environment.",
                    () -> blocked("DEVHIS_USER / DEVHIS_PASS are not set - nothing was run (no built-in account is used). Set them and run again."));
            flush(startedAt);
            return;
        }
        addSummary("Login user", USER);
        installMonitors();
        try {
            page.addInitScript(LOGIN_ERR_JS);
            try {
                login();
            } catch (RuntimeException e) {
                Object le = null;
                try { le = page.evaluate("() => window.__le || []"); } catch (Exception ignore) { }
                final String shown = String.valueOf(le);
                tc(idPrefix() + "-00", "Log in to DevHIS", "1. Open the login page. 2. Enter DEVHIS_USER and DEVHIS_PASS. 3. Click LOGIN.",
                        "The user is logged in and the dashboard opens.",
                        () -> blocked("Login failed, so no case could run - the login form showed " + shown + ". Check DEVHIS_USER / DEVHIS_PASS for " + BASE
                                + ". " + oneLine(e.getMessage(), 200)));
                return;
            }
            log("logged in as " + USER);
            runCases();
        } finally {
            flush(startedAt);
        }
    }

    private static boolean credentialsProvided() {
        boolean user = notBlank(System.getProperty("devhis.user")) || notBlank(System.getenv("DEVHIS_USER"));
        boolean pass = notBlank(System.getProperty("devhis.pass")) || notBlank(System.getenv("DEVHIS_PASS"));
        boolean file = Files.isRegularFile(Paths.get(System.getProperty("devhis.config", "config.properties")));
        return (user && pass) || file;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /** Records each login-form message (the form clears the password after an error, so the first, real message is otherwise lost). Text only, never field values. */
    private static final String LOGIN_ERR_JS = "(() => { if (window.__leWatch) return; window.__leWatch = true; window.__le = [];"
            + " setInterval(() => { try { const i = document.querySelector(\"input[placeholder='Login Name']\"); if (!i || !window.angular) return;"
            + " const s = angular.element(i).scope(); const e = s && s.loginError; if (e && !window.__le.includes(String(e))) window.__le.push(String(e)); } catch (x) {} }, 100); })();";

    // ======================================================================= report

    private void flush(String startedAt) {
        cases.sort(Comparator.comparing(c -> c.id));
        for (Case c : cases) step(c.png, c.id + " - " + c.scenario, "Steps: " + c.steps, c.expected, c.actual, c.status);

        Map<String, Long> counts = cases.stream().collect(Collectors.groupingBy(c -> c.status, TreeMap::new, Collectors.counting()));
        long p = counts.getOrDefault("PASS", 0L), f = counts.getOrDefault("FAIL", 0L), b = counts.getOrDefault("BLOCKED", 0L);
        addSummary("Test cases (PASS / FAIL / BLOCKED)", cases.size() + " (" + p + " / " + f + " / " + b + ")");
        addSummary("Application defects (FAIL - DEFECT)", orNone(cases.stream().filter(c -> c.defect).map(c -> c.id + " " + c.scenario).collect(Collectors.toList())));
        addSummary("Automation errors (FAIL - script could not complete the case)", orNone(cases.stream().filter(c -> c.automationError).map(c -> c.id + " " + c.scenario).collect(Collectors.toList())));
        addSummary("BLOCKED - missing test data / environment", orNone(cases.stream().filter(c -> "BLOCKED".equals(c.status)).map(c -> c.id + " " + c.scenario).collect(Collectors.toList())));
        List<ApiCall> calls = new ArrayList<>(api);
        addSummary("API calls recorded", calls.size() + " - " + endpointSummary(calls));
        addSummary("API failures (HTTP >= 400 / network)", orNone(calls.stream().filter(c -> (c.status >= 400 || c.status == 0) && !isBlocked(c)).map(this::fmt).distinct().collect(Collectors.toList())));
        addSummary("Read-only guard - write calls aborted before reaching the server", orNone(blockedWrites));
        addSummary("Downloads cancelled / native dialogs dismissed / popups", orNone(downloads) + " / " + orNone(nativeDialogs) + " / " + orNone(popups));
        addSummary("Console errors", orNone(new ArrayList<>(new LinkedHashSet<>(consoleErrors))));
        addSummary("Page (script) errors", orNone(new ArrayList<>(new LinkedHashSet<>(pageErrors))));
        addSummary("Execution log", logFile.toAbsolutePath().toString());
        addSummary("FAIL / BLOCKED screenshots", shotsDir.toAbsolutePath().toString());
        String finished = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        addSummary("Finished", finished + " (started " + startedAt + ")");
        System.out.println(title + " cases: " + counts);

        StringBuilder t = new StringBuilder(title + " - execution summary\r\n");
        t.append("Environment: ").append(BASE).append("\r\nStarted: ").append(startedAt).append("   Finished: ").append(finished).append("\r\n");
        t.append("Cases: ").append(cases.size()).append("  PASS ").append(p).append("  FAIL ").append(f).append("  BLOCKED ").append(b).append("\r\n");
        t.append("Write calls aborted by the read-only guard: ").append(blockedWrites.size()).append("\r\n");
        t.append("\r\nID\tSTATUS\tSCENARIO\tEXPECTED\tACTUAL\r\n");
        for (Case c : cases) t.append(c.id).append('\t').append(c.status).append('\t').append(c.scenario).append('\t').append(oneLine(c.expected, 400)).append('\t')
                .append(oneLine(c.actual, 1500)).append("\r\n");
        try { Files.writeString(reportDir.resolve(testId + "_summary.txt"), t.toString()); } catch (Exception e) { System.out.println("summary not written: " + e.getMessage()); }
        log("END " + counts);
        try { Files.createDirectories(logFile.getParent()); Files.write(logFile, new ArrayList<>(log)); } catch (Exception e) { System.out.println("log not written: " + e.getMessage()); }
    }

    private String fmt(ApiCall c) {
        return c.method + " " + c.path + (c.flag.isEmpty() ? "" : " (" + c.flag + ")") + " -> " + (c.status == 0 ? "NETWORK " + c.failure : "HTTP " + c.status)
                + " (" + c.ms + " ms) [" + c.route + "]";
    }

    private static String endpointSummary(List<ApiCall> calls) {
        Map<String, List<ApiCall>> g = calls.stream().collect(Collectors.groupingBy(c -> c.method + " " + c.path + (c.flag.isEmpty() ? "" : " (" + c.flag + ")"), TreeMap::new, Collectors.toList()));
        return g.entrySet().stream().map(e -> e.getKey() + " x" + e.getValue().size() + " status " + e.getValue().stream().map(c -> String.valueOf(c.status)).distinct().collect(Collectors.joining("/")))
                .collect(Collectors.joining("; "));
    }

    // ======================================================================= small helpers

    protected static boolean isBlocked(ApiCall c) { return c.failure.toLowerCase().replace("_", "").contains("blockedbyclient"); }
    protected static String orNone(List<String> l) { return l == null || l.isEmpty() ? "none" : String.join(" ; ", l); }
    protected static String oneLine(String s, int max) { s = String.valueOf(s).replaceAll("\\s+", " ").trim(); return s.length() > max ? s.substring(0, max) + "…" : s; }
    protected static String head(List<String> l, int n) { return l.size() <= n ? l.toString() : l.subList(0, n) + " … (+" + (l.size() - n) + ")"; }

    /** Whether any message mentions one of the words (case-insensitive). */
    protected static boolean mentions(List<String> msgs, String regex) {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return msgs.stream().anyMatch(m -> p.matcher(m).find());
    }

    /** Messages that announce success (must never appear after an incomplete Save). */
    protected static boolean success(List<String> msgs) { return mentions(msgs, "success|saved|added|received successfully"); }
}
