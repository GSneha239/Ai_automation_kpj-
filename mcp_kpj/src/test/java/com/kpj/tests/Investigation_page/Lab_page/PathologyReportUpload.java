package com.kpj.tests.Investigation_page.Lab_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Response;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The page object is also named PathologyReportUpload — referenced by its fully-qualified name (as the sibling Lab tests do).

/**
 * Investigation &gt; Lab &gt; <b>Pathology Report Upload</b> — READ-ONLY checks, test ids PRU-01 … PRU-24.
 *
 * <h3>Screen under test</h3>
 * The requested menu item "Pathology Report Upload" does not exist on devhis (checked live 2026-11-19). PRU-01 checks for it on every run
 * and reports what it finds. The checks then run on <b>Investigation &gt; Lab &gt; Histo Pathology &gt; Histo Report Upload</b>
 * ({@code #/HistoReportUpload}) — used as the ALTERNATIVE because its code is the Pathology Report Upload screen
 * ({@code PathologyReportUploadListController}). The report header says this; the two names are not treated as one requirement.
 *
 * <h3>Safety</h3>
 * <ul>
 *   <li>Nothing is saved, uploaded, delivered or deleted. Save in the Upload dialog is never pressed.</li>
 *   <li>A network guard (the {@code PharmacyDashboardTest} pattern; {@code /api/} and {@code /API/} both handled) aborts every API POST that is
 *       not on an explicit allowlist of verified read-only endpoints + ExecFlags, and every API PUT / PATCH / DELETE — e.g.
 *       {@code /API/ReportDelivery/IUD} and {@code api/ReportDelivery/SavePhoto} — and lists it.</li>
 *   <li>The file checks give synthetic files made on the fly in a temp folder (no patient document) to the dialog's file box; only the
 *       browser-side size / type check runs.</li>
 * </ul>
 *
 * <h3>Data</h3>
 * Nothing is assumed: defaults are read from the screen as it opens, and data-dependent checks are SKIPPED (with the reason) when the
 * environment gives nothing to check — e.g. when the Sub Group list is empty, Search cannot run at all.
 *
 * <pre>
 *   mvn test -Dtest=com.kpj.tests.Investigation_page.Lab_page.PathologyReportUpload -Ddevhis.postrun=off
 * </pre>
 * Options: {@code -Dcounter=} (default OPD-B-01), {@code -Dpru.subgroup=} (Sub Group text; default = the user's own, else the first),
 * {@code -Dpru.days=} (days back for the filter search, default 30), {@code -Dpru.mrn=} (MRN for the filter search).
 */
public class PathologyReportUpload extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Last path segment of a read-only API call; any other non-GET API call is blocked while testing. */
    /** Playwright glob for the guard: EVERY request goes through the Java handler, which decides by URL + method (no regex is sent to the browser). */
    private static final String ALL_REQUESTS = "**/*";
    /** Error code the guard aborts with; the browser reports it as net::ERR_BLOCKED_BY_CLIENT (kept apart from real failures). */
    private static final String GUARD_ABORT = "blockedbyclient";
    private static final String GUARD_ERR = "ERR_BLOCKED_BY_CLIENT";
    /** Marker in {@link #READ_ONLY_POSTS}: the endpoint takes no ExecFlag; a request that carries one is blocked. */
    private static final java.util.Set<String> NO_EXECFLAG = java.util.Set.of();
    /**
     * The ONLY POST API calls the read-only guard lets through: lower-case path (query string ignored) -> the exact ExecFlags allowed
     * ({@link #NO_EXECFLAG} = the endpoint is not ExecFlag-driven and the request must not carry one). Every entry was observed live on
     * devhis (build v2026.11.18.491) or read in the two screens' controllers / ReportDelivery.Factory.js / commonFactory.js, and is a
     * lookup: dropdowns, labels, menu rights, the message counter, the patient lookup by MRN and the two screens' searches.
     * Deliberately NOT listed (so blocked): /API/ReportDelivery/IUD (samplereportupload, REPORTDELIVERED), api/ReportDelivery/SavePhoto,
     * /API/ReportDelivery/SendSMsandEmail, /API/ReportDelivery/FetchSMSEmailInfo, /API/ReportDelivery/CheckOutStanding and the print
     * lookups GetPDFData / FetchListFirst PDFIdentifier (print is MANUAL), /API/SampleCollection/Search (not used by these tests).
     */
    private static final java.util.Map<String, java.util.Set<String>> READ_ONLY_POSTS = java.util.Map.of(
            "/api/reportdelivery/fetch", java.util.Set.of(
                    "SearchReportfinalized", "GetAllReportUploadTestDetails", "SearchpathradotherorderbookingdetailReportfinalized",   // Histo/Pathology Report Upload: Search, its test list, order row
                    "GetAllReportDeliveryTestDetails", "SearchReportUploaded", "SearchpathradotherorderbookingdetailReportUpload"),   // Report Delivery: Search, its order list, order row
            "/api/commondropdown/fetch", java.util.Set.of("LABSUBGROUP", "USERSUBGROUP", "USERLOCATION", "GETUSERDETAILS"),
            "/api/patientregistration/fetchpatientdata", java.util.Set.of("GetPatientInfo"),
            "/api/userrolemenudetails/fetch", java.util.Set.of("GetMenuRightsForUser"),
            "/api/commondropdown/fetchheader", NO_EXECFLAG,
            "/api/commondropdown/fetchalllabelheader", NO_EXECFLAG,
            "/api/commondropdown/getuserid", NO_EXECFLAG,
            "/api/auth/getinternalmsgcountonly", NO_EXECFLAG);
    private static final Pattern EXECFLAG = Pattern.compile("\"ExecFlag\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final String EMPTY_MSG = "(message with no text)";
    private static final String NG = com.kpj.pages.Investigation_page.Lab_page.PathologyReportUpload.NG;
    private static final String TESTS = com.kpj.pages.Investigation_page.Lab_page.PathologyReportUpload.TESTS;
    private static final String ORDERS = com.kpj.pages.Investigation_page.Lab_page.PathologyReportUpload.ORDERS;
    private static final String ROUTE = com.kpj.pages.Investigation_page.Lab_page.PathologyReportUpload.ROUTE;
    private static final String EXEC_SEARCH = com.kpj.pages.Investigation_page.Lab_page.PathologyReportUpload.EXEC_SEARCH;

    public PathologyReportUpload() { super("Investigation_Lab_PathologyReportUpload"); }

    public static void main(String[] args) {
        PathologyReportUpload t = new PathologyReportUpload();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    // ---- evidence ------------------------------------------------------------------------------------------------
    private final List<String> consoleErrors = new ArrayList<>();
    private final List<String> apiCalls = new ArrayList<>();
    private final List<String> apiFailures = new ArrayList<>();
    private final List<String> blockedWrites = new ArrayList<>();
    private final List<String> guardEchoes = new ArrayList<>();      // console lines caused by the guard's own aborts
    private final List<String> resourceFailures = new ArrayList<>(); // non-API page resources answered HTTP >= 400
    private final List<Response> dropdownResponses = new ArrayList<>();
    private final List<Response> searchResponses = new ArrayList<>();
    private final List<String> findings = new ArrayList<>();

    private com.kpj.pages.Investigation_page.Lab_page.PathologyReportUpload pru;
    private final Map<String, String> defaults = new LinkedHashMap<>();
    private String subGroup = "";
    private int testRows = 0;
    private final String today = LocalDate.now().format(DMY);

    @Override
    protected void body() {
        meta("Investigation - Lab - Pathology Report Upload (run on Histo Report Upload)",
                "Investigation > Lab > Pathology Report Upload — menu item not found; alternative used: Investigation > Lab > Histo Pathology > Histo Report Upload (#/HistoReportUpload)",
                "<b>Read-only.</b> The requested menu item <b>Pathology Report Upload</b> is checked first (PRU-01). The remaining checks run on "
                        + "<b>Histo Report Upload</b>, whose code is the Pathology Report Upload screen. Nothing is saved or uploaded; write API calls are "
                        + "blocked and listed. Data-dependent checks are SKIPPED with the reason when the environment gives nothing to check.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        attachMonitors();
        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("PRU-00 Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in — " + page.url().replaceAll("[?].*$", ""), "PASS");
        try {
            installReadOnlyGuard();
        } catch (Throwable e) {
            // Never go on without the guard: the screens under test have Save / Delivered actions that write patient data.
            step("PRU-00b Install the read-only API guard", "Route every request through the read-only guard",
                    "Guard installed; write API calls will be blocked",
                    "EXECUTION FAILURE — the guard could not be installed: " + String.valueOf(e.getMessage()).split("\\R")[0]
                            + ". The test was stopped before opening the screen.", "FAIL");
            addSummary("Result", "NOT RUN — read-only guard could not be installed");
            return;
        }

        pru = new com.kpj.pages.Investigation_page.Lab_page.PathologyReportUpload(page);
        try {
            section("PRU-01 Requested menu item", this::requestedMenuItem);
            section("PRU-02 Navigation", this::navigation);
            if (!pru.onScreen()) { addSummary("Result", "FAILED — the screen could not be opened"); return; }
            interceptAlerts();   // once, after the screen is open (a second call would wrap JAlert twice and record every message twice)
            section("PRU-03 Page load", this::pageLoad);
            section("PRU-04/05 Labels, fields and buttons", this::labelsFieldsButtons);
            section("PRU-06 Default values", this::defaultValues);
            section("PRU-07 Grid headers", this::gridHeaders);
            section("PRU-08 Sub Group list", this::subGroupList);
            section("PRU-09 Mandatory Sub Group", this::mandatorySubGroup);
            section("PRU-10 Search with defaults", this::searchDefaults);
            section("PRU-11 Search with filters", this::searchFilters);
            section("PRU-12/13 Invalid input", this::invalidInput);
            section("PRU-14 Clear", this::clearResets);
            section("PRU-15 Upload without a selected test", this::uploadWithoutSelection);
            section("PRU-16/17 Upload dialog and file checks", this::uploadDialogAndFiles);
            section("PRU-18 Save upload", this::saveUploadManual);
            section("PRU-19/20 Report status and view", this::statusAndView);
            section("PRU-21 Layout", this::layout);
            section("PRU-22 Navigation stability", this::stability);
            section("PRU-23/24 Console, API and read-only guard", this::health);
        } finally {
            try { page.unroute(ALL_REQUESTS); } catch (Exception ignore) { }
            writeSummary();
        }
    }

    // =================================================================================================================

    private void requestedMenuItem() {
        List<String> hits = pru.requestedMenuEntries();
        List<String> lab = pru.labMenuEntries();
        boolean present = !hits.isEmpty();
        if (!present) findings.add("Requested menu item 'Pathology Report Upload' not present; Histo Report Upload used as the alternative");
        step("PRU-01 Check that the menu item 'Pathology Report Upload' exists",
                "Open Investigation > Lab > Histo Pathology in the side menu (it is built only when opened), then search the menu links for the text "
                        + "'Pathology Report Upload' and list Investigation > Lab",
                "An entry 'Pathology Report Upload' exists under Investigation > Lab",
                present ? "Found: " + hits
                        : "NOT FOUND after opening the menu. Investigation > Lab offers: " + lab + ". The checks below run on 'Histo Report Upload' (" +
                          com.kpj.pages.Investigation_page.Lab_page.PathologyReportUpload.ROUTE + ") as the alternative — confirm with the BA "
                          + "that this is the intended screen.",
                present ? "PASS" : "MANUAL");
        addSummary("Requested screen", "Investigation > Lab > Pathology Report Upload — " + (present ? "found " + hits : "NOT in the menu"));
    }

    private void navigation() {
        boolean on = pru.navigateViaMenu();
        step("PRU-02 Open the screen from the menu", "Click Investigation -> Lab -> Histo Pathology -> Histo Report Upload",
                "The screen opens on #/HistoReportUpload with its Search button",
                on ? "Opened " + page.url() + " — title \"" + pru.pageTitle() + "\", controller " + pru.controllerName()
                   : "Did NOT reach the screen (" + page.url() + ")",
                on ? "PASS" : "FAIL");
        addSummary("Screen used", "Histo Report Upload " + ROUTE + " (controller " + pru.controllerName() + ")");
    }

    private void pageLoad() {
        List<String> errs = onScreen(consoleErrors), fails = onScreen(apiFailures);
        boolean ok = errs.isEmpty() && fails.isEmpty();
        step("PRU-03 Page loads without errors", "Console errors and failed API calls while the screen loaded",
                "No console error and no failed (HTTP >= 400 / network) API call",
                (errs.isEmpty() ? "No console error" : errs.size() + " console error(s): " + head(errs, 5))
                        + "; " + (fails.isEmpty() ? "no failed API call" : fails.size() + " failed API call(s): " + head(fails, 5)),
                ok ? "PASS" : "FAIL");
    }

    private void labelsFieldsButtons() {
        List<String> labels = pru.labels();
        List<String> expected = Arrays.asList("Date Range", "From Date", "To Date", "MRN", "Reg. Type", "OPD", "IPD", "All",
                "Outsource", "In house", "Sub Group", "Test Name", "Sample No", "Order No", "Location");
        List<String> missing = missing(expected, labels);
        step("PRU-04 Verify the search form labels", "Read every label of the search form",
                "All filter labels are shown: " + expected,
                (missing.isEmpty() ? "All shown" : "MISSING " + missing) + " — labels on screen: " + labels,
                missing.isEmpty() ? "PASS" : "FAIL");

        List<String> stray = new ArrayList<>();
        for (String l : labels) if (l.matches(".*[A-Za-z]\\.{1,}$") && !l.matches("(?i).*\\bno\\.$|.*\\btype\\.$")) stray.add(l);
        if (!stray.isEmpty()) findings.add("Labels end in stray dots: " + stray);
        step("PRU-04b Label wording", "Look for labels that end in stray punctuation", "No label ends with stray dots (e.g. \"MRN.\", \"Outsource..\")",
                stray.isEmpty() ? "None" : "Stray dots on: " + stray, stray.isEmpty() ? "PASS" : "FAIL");

        List<String> btns = pru.buttons();
        List<String> wantBtn = Arrays.asList("Search", "Clear", "Upload");
        List<String> missingBtn = new ArrayList<>();
        for (String b : wantBtn) if (btns.stream().noneMatch(x -> x.toLowerCase().startsWith(b.toLowerCase() + " |"))) missingBtn.add(b);
        boolean fields = Boolean.TRUE.equals(page.evaluate("() => !!document.getElementById('subgroupid') && !!document.getElementById('locationid')"))
                && !pru.chosen("locationid").isEmpty();
        step("PRU-05 Verify buttons, dropdowns and lookups", "Read the screen's buttons, the Sub Group / Location dropdowns and the MRN lookup buttons",
                "Search, Clear and Upload buttons; Sub Group and Location dropdowns; MRN find + patient-search buttons",
                (missingBtn.isEmpty() ? "Buttons " + wantBtn + " shown" : "MISSING buttons " + missingBtn)
                        + "; Location shows \"" + pru.chosen("locationid") + "\"; all buttons: " + btns,
                missingBtn.isEmpty() && fields && btns.stream().anyMatch(x -> x.contains("SearchPatientByMRNo")) && btns.stream().anyMatch(x -> x.contains("OpenPopupScreen"))
                        ? "PASS" : "FAIL");
    }

    private void defaultValues() {
        defaults.put("Date Range", String.valueOf(pru.dateRangeTicked()));
        defaults.put("From Date", pru.model(NG + "FromDate"));
        defaults.put("To Date", pru.model(NG + "ToDate"));
        defaults.put("Reg. Type", pru.checkedRadio(NG + "OPDIPD"));
        defaults.put("Outsource / In house", pru.checkedRadio(NG + "isoutsourced"));
        defaults.put("Location", pru.chosen("locationid"));
        defaults.put("Sub Group", pru.chosen("subgroupid"));
        defaults.put("MRN", pru.model(NG + "MRNO"));
        boolean ok = "true".equals(defaults.get("Date Range")) && today.equals(defaults.get("From Date")) && today.equals(defaults.get("To Date"))
                && !defaults.get("Reg. Type").isEmpty() && !defaults.get("Outsource / In house").isEmpty() && !defaults.get("Location").isEmpty();
        step("PRU-06 Verify default values", "Read the filters as the screen opens",
                "Date Range ticked; From / To Date = today (" + today + "); a Reg. Type and an Outsource/In house option chosen; Location filled",
                "Opened with " + defaults, ok ? "PASS" : "FAIL");
    }

    private void gridHeaders() {
        List<String> tests = pru.headers(TESTS), orders = pru.headers(ORDERS);
        List<String> wantTests = Arrays.asList("Report", "Order Date & Time", "Order No", "Uploaded Date", "Sample No", "Test Name", "MRN No.",
                "Patient Name", "Sub Group Name", "Out Sourced", "Bill Number / Consignment Note", "Sample Collection Agent Name", "Description", "Sample Quantity");
        List<String> wantOrders = Arrays.asList("Order Date & Time", "Order No", "MRN No.", "Patient Name", "Sponsor Name", "Referring Dr", "Bill No.", "Referral Number");
        List<String> m1 = missing(wantTests, tests), m2 = missing(wantOrders, orders);
        int rows = pru.rowCount(TESTS);
        step("PRU-07 Verify the initial grids and column headers", "Read the List of Orders and Test Details column headers",
                "Test Details: " + wantTests + "; List of Orders: " + wantOrders,
                "Test Details " + (m1.isEmpty() ? "complete" : "MISSING " + m1) + " (" + tests + "); List of Orders " + (m2.isEmpty() ? "complete" : "MISSING " + m2)
                        + " (" + orders + "); Test Details holds " + rows + " record(s) on opening",
                m1.isEmpty() && m2.isEmpty() ? "PASS" : "FAIL");
    }

    private void subGroupList() {
        List<String> opts = pru.subGroupOptions();
        String lab = labSubGroupApi();
        if (opts.isEmpty()) findings.add("Sub Group dropdown is empty (" + lab + ") — Search cannot run on this login");
        step("PRU-08 Verify the Sub Group dropdown is populated", "Read the Sub Group options and the LABSUBGROUP dropdown API answer",
                "At least one Sub Group to choose (Search requires one)",
                opts.isEmpty() ? "EMPTY — only \"Select\" is offered. " + lab : opts.size() + " option(s): " + head(opts, 10) + ". " + lab,
                opts.isEmpty() ? "FAIL" : "PASS");
        if (!opts.isEmpty()) {
            String want = System.getProperty("pru.subgroup", "").trim();
            String auto = pru.chosen("subgroupid");
            subGroup = !want.isEmpty() ? want : (!auto.isEmpty() && !auto.matches("(?i)select") ? auto : opts.get(0));
            addSummary("Sub Group used", subGroup);
        } else addSummary("Sub Group used", "none available — data-dependent checks SKIPPED");
    }

    private void mandatorySubGroup() {
        pru.clearSubGroup();
        int before = capturedAlerts().size();
        boolean sent = pru.clickSearchSendsRequest(4000);
        List<String> msgs = newAlerts(before);
        boolean ok = !sent && msgs.stream().anyMatch(m -> m.toLowerCase().contains("sub group"));
        step("PRU-09 Mandatory field: Sub Group", "Leave Sub Group empty and click Search",
                "A message asks for the Sub Group and no search request is sent",
                "Message(s): " + (msgs.isEmpty() ? "none" : msgs) + "; search request " + (sent ? "WAS sent" : "not sent"),
                ok ? "PASS" : "FAIL");
        boolean marked = pru.labels().stream().anyMatch(l -> l.matches("(?i)sub\\s*group\\s*\\*.*"))
                || Boolean.TRUE.equals(page.evaluate("() => { const l=[...document.querySelectorAll('label')].find(x=>/^\\s*sub\\s*group/i.test(x.innerText));"
                        + " return !!l && (/\\*/.test(l.innerText) || !!l.querySelector('.required,.mandatory,.text-danger') || /required|mandatory/i.test(l.className)); }"));
        if (!marked) findings.add("Sub Group is required for Search but has no mandatory marker");
        step("PRU-09b Mandatory marker on Sub Group", "Look for the required-field marker (*) on the Sub Group label",
                "Sub Group, which Search requires, is marked mandatory", marked ? "Marked" : "No mandatory marker on Sub Group", marked ? "PASS" : "FAIL");
    }

    private boolean needSubGroup(String id, String name) {
        if (!subGroup.isEmpty() && !pru.selectSubGroup(subGroup).isEmpty()) return true;
        step(id + " " + name, "Needs a Sub Group to search", "A Sub Group is available",
                "SKIPPED — the Sub Group dropdown offers nothing on this login/environment, and Search will not run without it", "SKIPPED");
        return false;
    }

    private void searchDefaults() {
        if (!needSubGroup("PRU-10", "Search with default values")) return;
        int status = pru.clickSearch(30000);
        testRows = pru.rowCount(TESTS);
        int orders = pru.rowCount(ORDERS);
        boolean ok = status > 0 && status < 400;
        step("PRU-10 Search with default values", "Sub Group " + subGroup + ", other filters as opened, click Search",
                "/API/ReportDelivery/fetch (" + EXEC_SEARCH + ") answers without error and the grids show the result",
                ok ? "HTTP " + status + "; List of Orders " + orders + " record(s), Test Details " + testRows + " record(s)"
                        + (orders == 0 && testRows == 0 ? " (no finalized order today for these filters)" : "")
                   : "FAILED — " + pru.problems,
                ok ? "PASS" : "FAIL");
    }

    private void searchFilters() {
        if (!needSubGroup("PRU-11", "Search using filters")) return;
        int days = Integer.getInteger("pru.days", 30);
        String from = LocalDate.now().minusDays(days).format(DMY);
        pru.setDateRange(true);
        pru.enterDates(from, today);
        pru.clickRadio(NG + "OPDIPD", "^all");
        String mrn = System.getProperty("pru.mrn", "").trim();
        if (!mrn.isEmpty()) pru.typeInto(NG + "MRNO", mrn);
        StringBuilder sb = new StringBuilder();
        boolean ok = true;
        for (String kind : new String[]{"^outsource", "^in\\s*house"}) {
            pru.clickRadio(NG + "isoutsourced", kind);
            int st = pru.clickSearch(30000);
            int o = pru.rowCount(ORDERS), t = pru.rowCount(TESTS);
            testRows = Math.max(testRows, t);
            ok &= st > 0 && st < 400;
            sb.append(pru.checkedRadio(NG + "isoutsourced")).append(": HTTP ").append(st).append(", ").append(o).append(" order(s), ").append(t).append(" test(s); ");
        }
        step("PRU-11 Search using filters", "Date range " + from + " - " + today + ", Reg. Type All" + (mrn.isEmpty() ? "" : ", MRN " + mrn)
                        + ", once with Outsource and once with In house, click Search each time",
                "Each search answers without error", sb.toString().trim(), ok ? "PASS" : "FAIL");
    }

    private void invalidInput() {
        int before = capturedAlerts().size();
        pru.findPatient("ZZZ999999");
        List<String> msgs = newAlerts(before);
        boolean ok = msgs.stream().anyMatch(m -> m.toLowerCase().contains("not found"));
        step("PRU-12 Invalid MRN", "Enter the non-existent MRN ZZZ999999 and click the find button beside the MRN box",
                "A 'patient not found' message; no patient is loaded",
                "Message(s): " + (msgs.isEmpty() ? "none" : msgs) + "; PatientID now \"" + pru.model(NG + "PatientID") + "\"", ok ? "PASS" : "FAIL");
        pru.typeInto(NG + "MRNO", "");

        if (!needSubGroup("PRU-13", "Invalid / reversed date range")) return;
        pru.setDateRange(true);
        before = capturedAlerts().size();
        pru.enterDates("31/02/2026", today);
        boolean sentBad = pru.clickSearchSendsRequest(4000);
        List<String> m1 = newAlerts(before);
        String later = LocalDate.now().plusDays(5).format(DMY);
        before = capturedAlerts().size();
        pru.enterDates(later, today);
        boolean sentRev = pru.clickSearchSendsRequest(4000);
        List<String> m2 = newAlerts(before);
        if (sentRev) findings.add("From Date later than To Date is searched without a warning");
        step("PRU-13 Invalid / reversed date range", "Search with From Date 31/02/2026, then with From Date " + later + " after To Date " + today,
                "Each is stopped with a message; no search request is sent",
                "31/02/2026: " + (sentBad ? "search SENT" : "not sent") + ", message " + (m1.isEmpty() ? "none" : m1)
                        + " | From after To: " + (sentRev ? "search SENT" : "not sent") + ", message " + (m2.isEmpty() ? "none" : m2),
                !sentBad && !sentRev ? "PASS" : "FAIL");
        pru.enterDates(today, today);
    }

    private void clearResets() {
        String weekAgo = LocalDate.now().minusDays(7).format(DMY);
        pru.typeInto(NG + "MRNO", "ZZZ999999");
        pru.typeInto(NG + "sampleno", "123");
        pru.typeInto(NG + "orderno", "456");
        // Through the model, not keystrokes: typing fires the Test Name autocomplete (POST /API/SampleCollection/Search, ExecFlag SearchServices),
        // which is not on the read-only allowlist (server side unverified) and is covered separately by PRU-14b. The box still shows the text.
        pru.setModel(NG + "ServiceName", "TEST NAME");
        pru.enterDates(weekAgo, today);
        pru.clickRadio(NG + "OPDIPD", "^ipd");
        pru.clickRadio(NG + "isoutsourced", "^in\\s*house");
        pru.setDateRange(false);
        pru.clickClear();

        Map<String, String> after = new LinkedHashMap<>();
        after.put("MRN", pru.model(NG + "MRNO"));
        after.put("Sample No", pru.model(NG + "sampleno"));
        after.put("Order No", pru.model(NG + "orderno"));
        after.put("Test Name (shown)", pru.shownValue(NG + "ServiceName"));
        after.put("From Date", pru.model(NG + "FromDate"));
        after.put("Date Range", String.valueOf(pru.dateRangeTicked()));
        after.put("Reg. Type", pru.checkedRadio(NG + "OPDIPD"));
        after.put("Outsource / In house", pru.checkedRadio(NG + "isoutsourced"));
        List<String> notReset = new ArrayList<>();
        for (String k : Arrays.asList("MRN", "Sample No", "Order No", "Test Name (shown)")) if (!after.get(k).isEmpty()) notReset.add(k + "=\"" + after.get(k) + "\"");
        for (String k : Arrays.asList("From Date", "Date Range", "Reg. Type", "Outsource / In house"))
            if (!after.get(k).equals(defaults.getOrDefault(k, after.get(k)))) notReset.add(k + "=\"" + after.get(k) + "\" (opened as \"" + defaults.get(k) + "\")");
        if (!notReset.isEmpty()) findings.add("Clear leaves filters set: " + notReset);
        step("PRU-14 Clear resets the filters", "Fill MRN, Sample No, Order No, Test Name; change From Date, Reg. Type IPD, In house, untick Date Range; click Clear",
                "Every filter returns to its opening value and the grids are emptied",
                (notReset.isEmpty() ? "All reset" : "NOT reset: " + notReset) + "; grids " + pru.rowCount(ORDERS) + "/" + pru.rowCount(TESTS) + " record(s)",
                notReset.isEmpty() && pru.rowCount(TESTS) == 0 ? "PASS" : "FAIL");
        // back to the opening state for the next checks
        pru.setDateRange(true);
        pru.enterDates(today, today);
        pru.clickRadio(NG + "OPDIPD", "^opd");
        pru.clickRadio(NG + "isoutsourced", "^outsource");
        step("PRU-14b Test Name lookup (autocomplete)", "Type part of a test name in Test Name and pick a suggestion",
                "Matching tests are suggested; picking one fills Test Name and is used by Search",
                "NOT RUN — each keystroke posts to /API/SampleCollection/Search (ExecFlag SearchServices). The browser side shows a lookup, but "
                        + "the server code is not available to confirm it is read-only, so the read-only guard keeps it blocked. Check by hand, or add it "
                        + "to the guard's allowlist once the endpoint is confirmed read-only.", "MANUAL");
    }

    private void uploadWithoutSelection() {
        int before = capturedAlerts().size();
        pru.clickUpload();
        List<String> msgs = newAlerts(before);
        boolean open = pru.uploadDialogOpen();
        step("PRU-15 Upload with no test selected", "Click Upload without ticking a Test Details record",
                "A message asks to select a test; the Upload dialog does not open",
                "Message(s): " + (msgs.isEmpty() ? "none" : msgs) + "; dialog " + (open ? "OPENED" : "not opened"),
                !open && !msgs.isEmpty() ? "PASS" : "FAIL");
        if (open) pru.closeUploadDialog();
    }

    private void uploadDialogAndFiles() {
        boolean viaRecord = testRows > 0 && pru.selectFirstTest();
        if (viaRecord) pru.clickUpload();
        boolean open = viaRecord ? pru.uploadDialogOpen() : pru.openUploadDialogForInspection();
        List<String> labels = pru.uploadDialogLabels();
        List<String> want = Arrays.asList("Sample Upload Date", "Time", "Report Uploaded By", "Attach File", "Note/Suggestions", "Remarks",
                "BillNo/ConsignmentNoteNo", "SampleCollectionAgent");
        List<String> miss = missing(want, labels);
        List<String> btns = pru.uploadDialogButtons();
        step("PRU-16 Upload dialog fields", (viaRecord ? "Tick the first Test Details record and click Upload"
                        : "No record to select on this environment: the dialog is shown directly, only to inspect it"),
                "The dialog shows " + want + " with Save and Close",
                (open ? "Dialog open; " : "Dialog NOT open; ") + (miss.isEmpty() ? "all fields shown" : "MISSING " + miss) + "; labels " + labels + "; buttons " + btns,
                open && miss.isEmpty() && btns.size() >= 2 ? "PASS" : "FAIL");
        if (!open) return;

        try {
            Path dir = Files.createTempDirectory("pru-upload-check-");
            Path pdf = synth(dir, "pru_check.pdf", 2_000, true);
            Path exe = synth(dir, "pru_check.exe", 2_000, false);
            Path big = synth(dir, "pru_check_big.pdf", 1_100_000, true);
            Path dots = synth(dir, "pru.check.final.pdf", 2_000, true);
            Path upper = synth(dir, "PRU_CHECK_UPPER.PDF", 2_000, true);
            String[][] cases = {
                    {pdf.toString(), "accept", "a small .pdf"},
                    {exe.toString(), "reject", "an .exe"},
                    {big.toString(), "reject", "a .pdf over 1 MB (1 100 000 bytes)"},
                    {dots.toString(), "accept", "a .pdf whose name has extra dots"},
                    {upper.toString(), "accept", "a .PDF in capitals"}};
            int n = 0;
            List<String> blankRejections = new ArrayList<>();
            for (String[] c : cases) {
                n++;
                Path f = Path.of(c[0]);
                int before = capturedAlerts().size();
                String taken = pru.chooseFile(f);
                List<String> msgs = newAlerts(before);
                boolean accepted = f.getFileName().toString().equals(taken);
                boolean ok = "accept".equals(c[1]) ? accepted && msgs.isEmpty() : !accepted && !msgs.isEmpty();
                if (!ok) findings.add("Attach File: " + c[2] + " was " + (accepted ? "accepted" : "rejected") + (msgs.isEmpty() ? "" : " (" + msgs + ")"));
                if (!accepted && msgs.contains(EMPTY_MSG)) blankRejections.add(f.getFileName().toString());
                step("PRU-17." + n + " File check: " + c[2], "Give " + f.getFileName() + " (" + Files.size(f) + " bytes, synthetic) to Attach File; Save is NOT pressed",
                        "accept".equals(c[1]) ? "The file is taken (its name shows in the Report box) with no error" : "The file is refused with a message",
                        (accepted ? "Taken" : "Not taken") + "; message(s) " + (msgs.isEmpty() ? "none" : msgs),
                        ok ? "PASS" : "FAIL");
            }
            if (!blankRejections.isEmpty()) findings.add("Attach File rejections show an error with no text (missing message key) for " + blankRejections);
            step("PRU-17.6 Rejection message wording", "Read the message shown for each refused file",
                    "Each refusal tells the user why (size / file type)",
                    blankRejections.isEmpty() ? "Every refusal had a message text" : "The error shown had NO text (JAlert called with an undefined message) for " + blankRejections,
                    blankRejections.isEmpty() ? "PASS" : "FAIL");
        } catch (Exception e) {
            step("PRU-17 File checks", "Make synthetic files and give them to Attach File", "Files checked", "ERROR: " + e.getMessage(), "FAIL");
        } finally {
            pru.closeUploadDialog();
        }
    }

    private void saveUploadManual() {
        step("PRU-18 Upload and save a report", "Fill the Upload dialog and press Save",
                "Report saved; 'Report Uploaded Successfully'; the record shows its Uploaded Date",
                "NOT RUN — saving uploads a document against a real patient order (/API/ReportDelivery/IUD + SavePhoto). Do this by hand on a test order.",
                "MANUAL");
    }

    private void statusAndView() {
        List<String> legend = pru.legend();
        boolean legendOk = legend.stream().anyMatch(x -> x.equalsIgnoreCase("Stat")) && legend.stream().anyMatch(x -> x.equalsIgnoreCase("VIP"))
                && legend.stream().anyMatch(x -> x.toLowerCase().startsWith("infect"));
        step("PRU-19 Status legend", "Read the legend under the grid", "Stat, VIP and Infected/Infectious are explained",
                "Legend: " + legend, legendOk ? "PASS" : "FAIL");

        if (testRows == 0 && pru.rowCount(TESTS) == 0) {
            step("PRU-19b Report status and details", "Read Test Details records", "Records to read",
                    "SKIPPED — no Test Details record on this environment for the filters used", "SKIPPED");
            step("PRU-20 View an uploaded report", "Open the Report of a record", "A record with an uploaded report",
                    "SKIPPED — no record to view", "SKIPPED");
            return;
        }
        step("PRU-19b Report status and details", "Read the first Test Details records", "Each record shows order, sample, test, MRN and upload state",
                String.join(" || ", pru.testRecords(3)), "PASS");
        int i = pru.firstRecordWithReport();
        if (i < 0) {
            step("PRU-20 View an uploaded report", "Open the Report of a record", "A record with an uploaded report",
                    "SKIPPED — none of the listed records has a report file yet", "SKIPPED");
            return;
        }
        boolean shown = pru.viewReport(i);
        step("PRU-20 View an uploaded report", "Open the Report of record " + (i + 1) + " (preview only, closed again)",
                "The report preview opens", shown ? "Preview opened and closed" : "Preview did NOT open", shown ? "PASS" : "FAIL");
    }

    private void layout() {
        String from = pru.boxFit(NG + "FromDate"), to = pru.boxFit(NG + "ToDate");
        boolean cut = clipped(from) || clipped(to);
        if (cut) findings.add("From/To Date boxes too narrow: the date is cut off (shown/needed px " + from + ", " + to + ")");
        step("PRU-21 Date boxes show the full date", "Compare each date box's visible width with the width its text needs",
                "The whole dd/MM/yyyy date is visible", "From Date " + from + " px, To Date " + to + " px (shown/needed)" + (cut ? " — text is CUT OFF" : ""),
                cut ? "FAIL" : "PASS");
    }

    private void stability() {
        page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>x.getAttribute('href')==='#/home'); if(a) a.click(); }");
        page.waitForTimeout(2000);
        boolean away = !pru.onScreen();
        boolean back = pru.navigateViaMenu();
        step("PRU-22 Leave and re-open the screen", "Go to Home, then open the screen again from the menu",
                "The screen opens again cleanly", (away ? "Left to " + page.url() : "Did not leave") + "; re-opened: " + (back ? "yes, " + page.url() : "NO"),
                back ? "PASS" : "FAIL");
    }

    private void health() {
        List<String> errs = onScreen(consoleErrors), fails = onScreen(apiFailures);
        step("PRU-23 Console errors and API failures", "Everything recorded while the screen was open",
                "No console error; no failed API call",
                "Console: " + (errs.isEmpty() ? "none" : errs.size() + " — " + head(errs, 6)) + " | API failures: " + (fails.isEmpty() ? "none" : fails.size() + " — " + head(fails, 6))
                        + " | search answers: " + searchSummary(),
                errs.isEmpty() && fails.isEmpty() ? "PASS" : "FAIL");
        step("PRU-24 Read-only guard", "Write calls stopped by the guard", "The run issues no write call",
                blockedWrites.isEmpty() ? "None attempted" : blockedWrites.size() + " blocked: " + head(blockedWrites, 10),
                blockedWrites.isEmpty() ? "PASS" : "FAIL");
    }

    // =================================================================================================================
    // monitors / guard / helpers
    // =================================================================================================================

    private void attachMonitors() {
        page.onConsoleMessage(m -> {
            if (!"error".equals(m.type())) return;
            String where = "";
            try { where = m.location(); } catch (Exception ignore) { }
            String line = route() + " " + trim(m.text(), 200) + (where == null || where.isBlank() ? "" : " @ " + trim(path(where), 140));
            // the browser's own echo of a request the read-only guard aborted: already reported by the guard step, not an application error
            if (m.text() != null && m.text().contains(GUARD_ERR)) guardEchoes.add(line); else consoleErrors.add(line);
        });
        page.onPageError(e -> consoleErrors.add(route() + " page error: " + trim(String.valueOf(e), 220)));
        page.onResponse(r -> {
            String u = r.url();
            if (!isApi(u)) {
                if (r.status() >= 400) resourceFailures.add(route() + " " + r.request().method() + " " + path(u) + " → HTTP " + r.status() + " (" + r.request().resourceType() + ")");
                return;
            }
            String line = route() + " " + r.request().method() + " " + path(u) + " " + execFlag(r) + " → HTTP " + r.status();
            apiCalls.add(line);
            if (r.status() >= 400) apiFailures.add(line);
            String lu = u.toLowerCase();
            if (lu.contains("/commondropdown/fetch")) dropdownResponses.add(r);
            if (lu.contains("/reportdelivery/fetch")) searchResponses.add(r);
        });
        page.onRequestFailed(q -> {
            String why = String.valueOf(q.failure());
            if (isApi(q.url()) && !why.contains("ERR_ABORTED") && !why.contains(GUARD_ERR))
                apiFailures.add(route() + " " + q.method() + " " + path(q.url()) + " → " + q.failure());
        });
    }

    /**
     * Read-only guard. Routes EVERY request ({@code **}{@code /*}) to this Java handler — no regular expression is passed to Playwright:
     * <ul>
     *   <li>not an API call (pages, scripts, styles, images, fonts, {@code /Investigation/Load/...} views) &rarr; continued untouched;</li>
     *   <li>API call ({@code /api/} or {@code /API/}: the URL is lower-cased with {@link Locale#ROOT}) with GET / HEAD / OPTIONS &rarr; continued;</li>
     *   <li>API POST &rarr; continued ONLY when its path is in {@link #READ_ONLY_POSTS} AND its ExecFlag is one listed for that path
     *       (or, for an endpoint listed with {@link #NO_EXECFLAG}, it carries no ExecFlag); every other POST is aborted and listed;</li>
     *   <li>API PUT / PATCH / DELETE / any other method &rarr; always aborted and listed.</li>
     * </ul>
     * If the handler itself fails on an API request, that request is aborted (never let through unchecked).
     */
    private void installReadOnlyGuard() {
        page.route(ALL_REQUESTS, route -> {
            String m = "", u = "";
            boolean api = false;
            try {
                m = String.valueOf(route.request().method()).toUpperCase(Locale.ROOT);
                u = route.request().url();
                api = isApi(u);
                if (!api || "GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m)) { route.resume(); return; }
                String flag = execFlagOf(route.request().postData());
                if ("POST".equals(m) && allowedReadPost(path(u), flag)) { route.resume(); return; }
                blockedWrites.add(route() + " " + m + " " + path(u) + (flag.isEmpty() ? "" : " [" + flag + "]"));
                System.out.println("[" + testId + "] read-only guard blocked " + m + " " + path(u) + (flag.isEmpty() ? "" : " [" + flag + "]"));
                route.abort(GUARD_ABORT);   // reported by the browser as net::ERR_BLOCKED_BY_CLIENT, so it is not mistaken for an application failure
            } catch (Throwable e) {
                System.out.println("[" + testId + "] read-only guard error on " + m + " " + path(u) + ": " + e.getMessage());
                try {
                    if (api) { blockedWrites.add(m + " " + path(u) + " (guard error)"); route.abort(GUARD_ABORT); }
                    else route.resume();
                } catch (Throwable ignore) { /* the request was already handled */ }
            }
        });
    }

    /** True only for an allowlisted read-only POST: exact path (lower-case, no query) and an allowed ExecFlag for that path. */
    private static boolean allowedReadPost(String path, String execFlag) {
        java.util.Set<String> flags = READ_ONLY_POSTS.get(path == null ? "" : path.toLowerCase(Locale.ROOT));
        if (flags == null) return false;                          // endpoint not on the list
        if (flags.isEmpty()) return execFlag.isEmpty();           // NO_EXECFLAG endpoint must not carry one
        return flags.contains(execFlag);                          // exact, case-sensitive ExecFlag match
    }

    /** The ExecFlag of a JSON request body, or "" when there is none. */
    private static String execFlagOf(String body) {
        if (body == null) return "";
        Matcher x = EXECFLAG.matcher(body);
        return x.find() ? x.group(1) : "";
    }

    /** True for an application API URL, whatever its case ({@code /api/} or {@code /API/}). */
    private static boolean isApi(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).contains("/api/");
    }

    /** What the LABSUBGROUP dropdown call answered (read from the recorded responses). */
    private String labSubGroupApi() {
        for (Response r : dropdownResponses) {
            String body = String.valueOf(r.request().postData());
            if (!body.toUpperCase().contains("LABSUBGROUP")) continue;
            try {
                String t = r.text();
                int n = t.trim().equals("[]") ? 0 : t.split("\"value\"\\s*:").length - 1;
                return "LABSUBGROUP dropdown API: HTTP " + r.status() + ", " + n + " item(s)";
            } catch (Exception e) { return "LABSUBGROUP dropdown API: HTTP " + r.status() + " (body not readable)"; }
        }
        return "LABSUBGROUP dropdown call not identified among " + dropdownResponses.size() + " CommonDropdown/Fetch call(s)";
    }

    private String searchSummary() {
        if (searchResponses.isEmpty()) return "no /API/ReportDelivery/fetch call";
        List<String> out = new ArrayList<>();
        for (Response r : searchResponses) {
            String n = "?";
            try { String t = r.text().trim(); n = t.equals("[]") ? "0" : String.valueOf(Math.max(0, t.split("\\},\\s*\\{").length)); } catch (Exception ignore) { }
            out.add(execFlag(r) + " HTTP " + r.status() + " (" + n + " row(s))");
        }
        return head(out, 8);
    }

    private interface Section { void run(); }

    private void section(String name, Section s) {
        System.out.println("[" + testId + "] --- " + name + " ---");
        try { s.run(); }
        catch (Throwable e) {
            String m = String.valueOf(e.getMessage()).split("\\R")[0];
            step(page, name + " — unexpected error", "Run the " + name + " check", "Check completes", "ERROR: " + m, "FAIL");
        }
    }

    private List<String> newAlerts(int before) {
        page.waitForTimeout(600);
        List<String> all = capturedAlerts();
        List<String> out = new ArrayList<>();
        // the app sometimes raises JAlert with an undefined text (a missing resource key) — kept, but as readable text
        for (int i = before; i < all.size(); i++) { Object m = all.get(i); out.add(m == null || String.valueOf(m).isBlank() || "undefined".equals(String.valueOf(m)) ? EMPTY_MSG : String.valueOf(m)); }
        return out;
    }

    /** Only what happened while this screen was showing is held against it. */
    private List<String> onScreen(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) if (l.toLowerCase().startsWith("[#/historeportupload")) out.add(l);
        return out;
    }

    private static List<String> missing(List<String> expected, List<String> actual) {
        List<String> out = new ArrayList<>();
        for (String e : expected) {
            String k = key(e);
            if (actual.stream().noneMatch(a -> key(a).equals(k))) out.add(e);
        }
        return out;
    }

    /** Compare labels on their words only: case, spaces and trailing dots/asterisks ignored. */
    private static String key(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[\\s*]+", "").replaceAll("\\.+$", ""); }

    private static boolean clipped(String fit) {
        String[] p = fit.split("/");
        try { return p.length == 2 && Integer.parseInt(p[1].trim()) > Integer.parseInt(p[0].trim()) + 1; } catch (Exception e) { return false; }
    }

    /** A synthetic file of {@code bytes} bytes (a minimal PDF header when {@code pdf}); never a patient document. */
    private static Path synth(Path dir, String name, int bytes, boolean pdf) throws Exception {
        byte[] b = new byte[bytes];
        Arrays.fill(b, (byte) ' ');
        byte[] head = (pdf ? "%PDF-1.4\n% synthetic test file - not a report\n" : "MZ synthetic test file\n").getBytes("US-ASCII");
        System.arraycopy(head, 0, b, 0, Math.min(head.length, b.length));
        if (pdf && bytes > 8) { byte[] eof = "\n%%EOF\n".getBytes("US-ASCII"); System.arraycopy(eof, 0, b, bytes - eof.length, eof.length); }
        Path f = dir.resolve(name);
        Files.write(f, b);
        f.toFile().deleteOnExit();
        return f;
    }

    private void writeSummary() {
        addSummary("Application URL", BASE + "/" + com.kpj.pages.Investigation_page.Lab_page.PathologyReportUpload.ROUTE);
        addSummary("Run date", LocalDate.now().toString());
        addSummary("Findings", findings.isEmpty() ? "None" : findings.size() + " — " + String.join(" || ", findings));
        addSummary("API calls seen", apiCalls.size() + " — " + head(apiCalls, 12));
        addSummary("API failures", apiFailures.isEmpty() ? "None" : head(apiFailures, 10));
        addSummary("Console errors", consoleErrors.isEmpty() ? "None" : head(consoleErrors, 10));
        addSummary("Failed page resources (HTTP >= 400, any page)", resourceFailures.isEmpty() ? "None" : head(resourceFailures, 10));
        if (!guardEchoes.isEmpty()) addSummary("Console lines from the guard's own blocks (not counted as errors)", head(guardEchoes, 5));
        addSummary("Write calls blocked (read-only guard)", blockedWrites.isEmpty() ? "None" : head(blockedWrites, 10));
    }

    private String route() {
        try { String u = page.url(); int i = u.indexOf('#'); return "[" + (i >= 0 ? u.substring(i) : path(u)) + "]"; } catch (Exception e) { return "[?]"; }
    }

    private static String execFlag(Response r) {
        try {
            Matcher m = Pattern.compile("\"ExecFlag\"\\s*:\\s*\"([^\"]+)\"").matcher(String.valueOf(r.request().postData()));
            return m.find() ? "[" + m.group(1) + "]" : "";
        } catch (Exception e) { return ""; }
    }

    private static String head(List<String> l, int n) { return l.size() <= n ? l.toString() : l.subList(0, n) + " … (+" + (l.size() - n) + ")"; }
    private static String trim(String s, int n) { s = s == null ? "" : s.replaceAll("\\s+", " "); return s.length() > n ? s.substring(0, n) + "…" : s; }
    private static String path(String url) { return url.replaceAll("^https?://[^/]+", "").replaceAll("[?#].*$", ""); }
}
