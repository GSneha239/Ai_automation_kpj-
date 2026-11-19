package com.kpj.tests.Investigation_page.Lab_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Response;

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

// The page object is also named ReportDelivery — referenced by its fully-qualified name (as the sibling Lab tests do).

/**
 * Investigation &gt; Lab &gt; <b>Report Delivery</b> ({@code #/ReportDelivery}) — READ-ONLY checks, test ids RD-01 … RD-22.
 *
 * <p>A separate screen from Radiology Report Delivery ({@code com.kpj.tests.Investigation_page.Radiology_page.RadiologyReportDelivery}, which
 * DOES mark a report delivered) and from Histo Report Delivery. Nothing here delivers or prints.</p>
 *
 * <h3>Safety</h3>
 * <ul>
 *   <li>The Delivered dialog's save ({@code IUDReportDelivery()}) is never pressed; no print is produced; marking delivered is a MANUAL row.</li>
 *   <li>A network guard (the {@code PharmacyDashboardTest} pattern; {@code /api/} and {@code /API/} both handled) aborts every API POST that is
 *       not on an explicit allowlist of verified read-only endpoints + ExecFlags, and every API PUT / PATCH / DELETE — e.g.
 *       {@code /API/ReportDelivery/IUD}, {@code /API/ReportDelivery/SendSMsandEmail} — and lists it.</li>
 * </ul>
 *
 * <h3>Data</h3>
 * Defaults are read from the screen as it opens. Data-dependent checks are SKIPPED with the reason when the environment gives nothing to check
 * (Search cannot run at all when the Sub Group list is empty).
 *
 * <pre>
 *   mvn test -Dtest=com.kpj.tests.Investigation_page.Lab_page.ReportDelivery -Ddevhis.postrun=off
 * </pre>
 * Options: {@code -Dcounter=} (default OPD-B-01), {@code -Drd.subgroup=}, {@code -Drd.days=} (default 30), {@code -Drd.mrn=}.
 */
public class ReportDelivery extends DevHisBase {

    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
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
    private static final String NG = com.kpj.pages.Investigation_page.Lab_page.ReportDelivery.NG;
    private static final String TESTS = com.kpj.pages.Investigation_page.Lab_page.ReportDelivery.TESTS;
    private static final String ORDERS = com.kpj.pages.Investigation_page.Lab_page.ReportDelivery.ORDERS;
    private static final String ROUTE = com.kpj.pages.Investigation_page.Lab_page.ReportDelivery.ROUTE;
    private static final String EXEC_SEARCH = com.kpj.pages.Investigation_page.Lab_page.ReportDelivery.EXEC_SEARCH;

    public ReportDelivery() { super("Investigation_Lab_ReportDelivery"); }

    public static void main(String[] args) {
        ReportDelivery t = new ReportDelivery();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private final List<String> consoleErrors = new ArrayList<>();
    private final List<String> apiCalls = new ArrayList<>();
    private final List<String> apiFailures = new ArrayList<>();
    private final List<String> blockedWrites = new ArrayList<>();
    private final List<String> guardEchoes = new ArrayList<>();      // console lines caused by the guard's own aborts
    private final List<String> resourceFailures = new ArrayList<>(); // non-API page resources answered HTTP >= 400
    private final List<Response> dropdownResponses = new ArrayList<>();
    private final List<Response> searchResponses = new ArrayList<>();
    private final List<String> findings = new ArrayList<>();

    private com.kpj.pages.Investigation_page.Lab_page.ReportDelivery rd;
    private final Map<String, String> defaults = new LinkedHashMap<>();
    private String subGroup = "";
    private int testRows = 0;
    private final String today = LocalDate.now().format(DMY);

    @Override
    protected void body() {
        meta("Investigation - Lab - Report Delivery", "Investigation > Lab > Report Delivery (#/ReportDelivery)",
                "<b>Read-only.</b> Navigation, filters, validation, Clear, grids, the print / Delivered buttons without a selection, and API / console "
                        + "health. Nothing is delivered or printed; write API calls are blocked and listed. Data-dependent checks are SKIPPED with the "
                        + "reason when the environment gives nothing to check.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        attachMonitors();
        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("RD-00 Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in — " + page.url().replaceAll("[?].*$", ""), "PASS");
        try {
            installReadOnlyGuard();
        } catch (Throwable e) {
            // Never go on without the guard: the screens under test have Save / Delivered actions that write patient data.
            step("RD-00b Install the read-only API guard", "Route every request through the read-only guard",
                    "Guard installed; write API calls will be blocked",
                    "EXECUTION FAILURE — the guard could not be installed: " + String.valueOf(e.getMessage()).split("\\R")[0]
                            + ". The test was stopped before opening the screen.", "FAIL");
            addSummary("Result", "NOT RUN — read-only guard could not be installed");
            return;
        }

        rd = new com.kpj.pages.Investigation_page.Lab_page.ReportDelivery(page);
        try {
            section("RD-01 Menu item", this::menuItem);
            section("RD-02 Navigation", this::navigation);
            if (!rd.onScreen()) { addSummary("Result", "FAILED — the screen could not be opened"); return; }
            interceptAlerts();   // once, after the screen is open (a second call would wrap JAlert twice and record every message twice)
            section("RD-03 Page load", this::pageLoad);
            section("RD-04/05 Labels, fields and buttons", this::labelsFieldsButtons);
            section("RD-06 Default values", this::defaultValues);
            section("RD-07 Grid headers", this::gridHeaders);
            section("RD-08 Sub Group list", this::subGroupList);
            section("RD-09 Mandatory Sub Group", this::mandatorySubGroup);
            section("RD-10 Search with defaults", this::searchDefaults);
            section("RD-11 Search with filters", this::searchFilters);
            section("RD-12/13 Invalid input", this::invalidInput);
            section("RD-14 Clear", this::clearResets);
            section("RD-15 Actions without a selection", this::actionsWithoutSelection);
            section("RD-16/17 Delivered dialog and delivery", this::deliveredDialog);
            section("RD-18 Print", this::printManual);
            section("RD-19 Delivery status", this::deliveryStatus);
            section("RD-20 Layout", this::layout);
            section("RD-21 Navigation stability", this::stability);
            section("RD-22/23 Console, API and read-only guard", this::health);
        } finally {
            try { page.unroute(ALL_REQUESTS); } catch (Exception ignore) { }
            writeSummary();
        }
    }

    // =================================================================================================================

    private void menuItem() {
        List<String> hits = rd.menuEntries();
        boolean ok = hits.stream().anyMatch(h -> h.toLowerCase().endsWith("=> " + ROUTE.toLowerCase()));
        step("RD-01 Check that Investigation > Lab > Report Delivery exists",
                "Open Investigation > Lab in the side menu (it is built only when opened) and look for a 'Report Delivery' link",
                "One 'Report Delivery' entry routing to " + ROUTE, hits.isEmpty() ? "NOT FOUND under Investigation > Lab" : "Found: " + hits, ok ? "PASS" : "FAIL");
    }

    private void navigation() {
        boolean on = rd.navigateViaMenu();
        step("RD-02 Open the screen from the menu", "Click Investigation -> Lab -> Report Delivery",
                "The screen opens on " + ROUTE + " with its Search button",
                on ? "Opened " + page.url() + " — title \"" + rd.pageTitle() + "\"" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
    }

    private void pageLoad() {
        List<String> errs = onScreen(consoleErrors), fails = onScreen(apiFailures);
        step("RD-03 Page loads without errors", "Console errors and failed API calls while the screen loaded",
                "No console error and no failed (HTTP >= 400 / network) API call",
                (errs.isEmpty() ? "No console error" : errs.size() + " console error(s): " + head(errs, 5))
                        + "; " + (fails.isEmpty() ? "no failed API call" : fails.size() + " failed API call(s): " + head(fails, 5)),
                errs.isEmpty() && fails.isEmpty() ? "PASS" : "FAIL");
    }

    private void labelsFieldsButtons() {
        List<String> labels = rd.labels();
        List<String> expected = Arrays.asList("Date Range", "From Date", "To Date", "MRN", "Reg. Type", "OPD", "IPD", "All",
                "Delivered", "Not Delivered", "Sub Group", "Test Name", "Sample No", "Order No", "Location");
        List<String> missing = missing(expected, labels);
        step("RD-04 Verify the search form labels", "Read every label of the search form", "All filter labels are shown: " + expected,
                (missing.isEmpty() ? "All shown" : "MISSING " + missing) + " — labels on screen: " + labels, missing.isEmpty() ? "PASS" : "FAIL");

        List<String> stray = new ArrayList<>();
        for (String l : labels) if (l.matches(".*[A-Za-z]\\.{1,}$") && !l.matches("(?i).*\\bno\\.$|.*\\btype\\.$")) stray.add(l);
        if (!stray.isEmpty()) findings.add("Labels end in stray dots: " + stray);
        step("RD-04b Label wording", "Look for labels that end in stray punctuation", "No label ends with stray dots (e.g. \"MRN.\", \"Not Delivered.\")",
                stray.isEmpty() ? "None" : "Stray dots on: " + stray, stray.isEmpty() ? "PASS" : "FAIL");

        List<String> btns = rd.buttons();
        List<String> want = Arrays.asList("Search", "Clear", "Bulk print", "Combine print", "Separate print", "Delivered");
        List<String> missingBtn = new ArrayList<>();
        for (String b : want) if (btns.stream().noneMatch(x -> x.toLowerCase().startsWith(b.toLowerCase() + " |"))) missingBtn.add(b);
        boolean lookups = btns.stream().anyMatch(x -> x.contains("SearchPatientByMRNo")) && btns.stream().anyMatch(x -> x.contains("OpenPopupScreen"));
        boolean selects = Boolean.TRUE.equals(page.evaluate("() => !!document.getElementById('subgroupid') && !!document.getElementById('locationid')"));
        step("RD-05 Verify buttons, dropdowns and lookups", "Read the screen's buttons, the Sub Group / Location dropdowns and the MRN lookup buttons",
                "Buttons " + want + "; Sub Group and Location dropdowns; MRN find + patient-search buttons",
                (missingBtn.isEmpty() ? "All buttons shown" : "MISSING buttons " + missingBtn) + "; Location \"" + rd.chosen("locationid") + "\"; buttons " + btns,
                missingBtn.isEmpty() && lookups && selects ? "PASS" : "FAIL");
    }

    private void defaultValues() {
        defaults.put("Date Range", String.valueOf(rd.dateRangeTicked()));
        defaults.put("From Date", rd.model(NG + "FromDate"));
        defaults.put("To Date", rd.model(NG + "ToDate"));
        defaults.put("Reg. Type", rd.checkedRadio(NG + "OPDIPD"));
        defaults.put("Delivery status", rd.checkedRadio(NG + "ReportUploaded"));
        defaults.put("Location", rd.chosen("locationid"));
        defaults.put("Sub Group", rd.chosen("subgroupid"));
        boolean ok = "true".equals(defaults.get("Date Range")) && today.equals(defaults.get("From Date")) && today.equals(defaults.get("To Date"))
                && !defaults.get("Reg. Type").isEmpty() && !defaults.get("Delivery status").isEmpty() && !defaults.get("Location").isEmpty();
        step("RD-06 Verify default values", "Read the filters as the screen opens",
                "Date Range ticked; From / To Date = today (" + today + "); a Reg. Type and a delivery status chosen; Location filled",
                "Opened with " + defaults, ok ? "PASS" : "FAIL");
    }

    private void gridHeaders() {
        List<String> tests = rd.headers(TESTS), orders = rd.headers(ORDERS);
        List<String> wantTests = Arrays.asList("Print", "Report Delivered", "Order Date & Time", "Order No", "Sample No", "Test Name", "MRN No.", "Patient Name", "Sub Group Name");
        List<String> wantOrders = Arrays.asList("Order Date & Time", "MRN No.", "Patient Name", "Sponsor Name", "Referring Dr", "Referral Number", "Bill No.");
        List<String> m1 = missing(wantTests, tests), m2 = missing(wantOrders, orders);
        if (tests.stream().anyMatch(h -> h.equals("sampleno"))) findings.add("Test Details column header shows the raw field name 'sampleno' instead of 'Sample No'");
        step("RD-07 Verify the initial grids and column headers", "Read the List of Orders and Test Details column headers",
                "Test Details: " + wantTests + "; List of Orders: " + wantOrders,
                "Test Details " + (m1.isEmpty() ? "complete" : "MISSING " + m1) + " (" + tests + "); List of Orders " + (m2.isEmpty() ? "complete" : "MISSING " + m2)
                        + " (" + orders + "); Test Details holds " + rd.rowCount(TESTS) + " record(s) on opening",
                m1.isEmpty() && m2.isEmpty() ? "PASS" : "FAIL");
    }

    private void subGroupList() {
        List<String> opts = rd.subGroupOptions();
        String lab = labSubGroupApi();
        if (opts.isEmpty()) findings.add("Sub Group dropdown is empty (" + lab + ") — Search cannot run on this login");
        step("RD-08 Verify the Sub Group dropdown is populated", "Read the Sub Group options and the LABSUBGROUP dropdown API answer",
                "At least one Sub Group to choose (Search requires one)",
                opts.isEmpty() ? "EMPTY — only \"Select\" is offered. " + lab : opts.size() + " option(s): " + head(opts, 10) + ". " + lab,
                opts.isEmpty() ? "FAIL" : "PASS");
        if (!opts.isEmpty()) {
            String want = System.getProperty("rd.subgroup", "").trim();
            String auto = rd.chosen("subgroupid");
            subGroup = !want.isEmpty() ? want : (!auto.isEmpty() && !auto.matches("(?i)select") ? auto : opts.get(0));
            addSummary("Sub Group used", subGroup);
        } else addSummary("Sub Group used", "none available — data-dependent checks SKIPPED");
    }

    private void mandatorySubGroup() {
        rd.clearSubGroup();
        int before = capturedAlerts().size();
        boolean sent = rd.clickSearchSendsRequest(4000);
        List<String> msgs = newAlerts(before);
        step("RD-09 Mandatory field: Sub Group", "Leave Sub Group empty and click Search",
                "A message asks for the Sub Group and no search request is sent",
                "Message(s): " + (msgs.isEmpty() ? "none" : msgs) + "; search request " + (sent ? "WAS sent" : "not sent"),
                !sent && msgs.stream().anyMatch(m -> m.toLowerCase().contains("sub group")) ? "PASS" : "FAIL");
        boolean marked = Boolean.TRUE.equals(page.evaluate("() => { const l=[...document.querySelectorAll('label')].find(x=>/^\\s*sub\\s*group/i.test(x.innerText));"
                + " return !!l && (/\\*/.test(l.innerText) || !!l.querySelector('.required,.mandatory,.text-danger') || /required|mandatory/i.test(l.className)); }"));
        if (!marked) findings.add("Sub Group is required for Search but has no mandatory marker");
        step("RD-09b Mandatory marker on Sub Group", "Look for the required-field marker (*) on the Sub Group label",
                "Sub Group, which Search requires, is marked mandatory", marked ? "Marked" : "No mandatory marker on Sub Group", marked ? "PASS" : "FAIL");
    }

    private boolean needSubGroup(String id, String name) {
        if (!subGroup.isEmpty() && !rd.selectSubGroup(subGroup).isEmpty()) return true;
        step(id + " " + name, "Needs a Sub Group to search", "A Sub Group is available",
                "SKIPPED — the Sub Group dropdown offers nothing on this login/environment, and Search will not run without it", "SKIPPED");
        return false;
    }

    private void searchDefaults() {
        if (!needSubGroup("RD-10", "Search with default values")) return;
        int status = rd.clickSearch(30000);
        testRows = rd.rowCount(TESTS);
        boolean ok = status > 0 && status < 400;
        step("RD-10 Search with default values", "Sub Group " + subGroup + ", other filters as opened, click Search",
                "/API/ReportDelivery/fetch (" + EXEC_SEARCH + ") answers without error and Test Details shows the result",
                ok ? "HTTP " + status + "; Test Details " + testRows + " record(s)" + (testRows == 0 ? " (nothing pending delivery today for these filters)" : "")
                   : "FAILED — " + rd.problems,
                ok ? "PASS" : "FAIL");
    }

    private void searchFilters() {
        if (!needSubGroup("RD-11", "Search using filters")) return;
        int days = Integer.getInteger("rd.days", 30);
        String from = LocalDate.now().minusDays(days).format(DMY);
        rd.setDateRange(true);
        rd.enterDates(from, today);
        rd.clickRadio(NG + "OPDIPD", "^all");
        String mrn = System.getProperty("rd.mrn", "").trim();
        if (!mrn.isEmpty()) rd.typeInto(NG + "MRNO", mrn);
        StringBuilder sb = new StringBuilder();
        boolean ok = true;
        for (String status : new String[]{"^not\\s*delivered", "^delivered"}) {
            rd.clickRadio(NG + "ReportUploaded", status);
            int st = rd.clickSearch(30000);
            int t = rd.rowCount(TESTS);
            testRows = Math.max(testRows, t);
            ok &= st > 0 && st < 400;
            sb.append(rd.checkedRadio(NG + "ReportUploaded")).append(": HTTP ").append(st).append(", ").append(t).append(" test(s); ");
        }
        step("RD-11 Search using filters", "Date range " + from + " - " + today + ", Reg. Type All" + (mrn.isEmpty() ? "" : ", MRN " + mrn)
                        + ", once with Not Delivered and once with Delivered",
                "Each search answers without error", sb.toString().trim(), ok ? "PASS" : "FAIL");
    }

    private void invalidInput() {
        int before = capturedAlerts().size();
        rd.findPatient("ZZZ999999");
        List<String> msgs = newAlerts(before);
        step("RD-12 Invalid MRN", "Enter the non-existent MRN ZZZ999999 and click the find button beside the MRN box",
                "A 'patient not found' message; no patient is loaded",
                "Message(s): " + (msgs.isEmpty() ? "none" : msgs) + "; PatientID now \"" + rd.model(NG + "PatientID") + "\"",
                msgs.stream().anyMatch(m -> m.toLowerCase().contains("not found")) ? "PASS" : "FAIL");
        rd.typeInto(NG + "MRNO", "");

        if (!needSubGroup("RD-13", "Invalid / reversed date range")) return;
        rd.setDateRange(true);
        before = capturedAlerts().size();
        rd.enterDates("31/02/2026", today);
        boolean sentBad = rd.clickSearchSendsRequest(4000);
        List<String> m1 = newAlerts(before);
        String later = LocalDate.now().plusDays(5).format(DMY);
        before = capturedAlerts().size();
        rd.enterDates(later, today);
        boolean sentRev = rd.clickSearchSendsRequest(4000);
        List<String> m2 = newAlerts(before);
        if (sentBad) findings.add("An impossible From Date (31/02/2026) is searched without a warning");
        if (sentRev) findings.add("From Date later than To Date is searched without a warning");
        step("RD-13 Invalid / reversed date range", "Search with From Date 31/02/2026, then with From Date " + later + " after To Date " + today,
                "Each is stopped with a message; no search request is sent",
                "31/02/2026: " + (sentBad ? "search SENT" : "not sent") + ", message " + (m1.isEmpty() ? "none" : m1)
                        + " | From after To: " + (sentRev ? "search SENT" : "not sent") + ", message " + (m2.isEmpty() ? "none" : m2),
                !sentBad && !sentRev ? "PASS" : "FAIL");
        rd.enterDates(today, today);
    }

    private void clearResets() {
        String weekAgo = LocalDate.now().minusDays(7).format(DMY);
        rd.typeInto(NG + "MRNO", "ZZZ999999");
        rd.typeInto(NG + "sampleno", "123");
        rd.typeInto(NG + "orderno", "456");
        // Through the model, not keystrokes: typing fires the Test Name autocomplete (POST /API/SampleCollection/Search, ExecFlag SearchServices),
        // which is not on the read-only allowlist (server side unverified) and is covered separately by RD-14b. The box still shows the text.
        rd.setModel(NG + "ServiceName", "TEST NAME");
        rd.enterDates(weekAgo, today);
        rd.clickRadio(NG + "OPDIPD", "^ipd");
        rd.clickRadio(NG + "ReportUploaded", "^delivered");
        rd.setDateRange(false);
        rd.clickClear();

        Map<String, String> after = new LinkedHashMap<>();
        after.put("MRN", rd.model(NG + "MRNO"));
        after.put("Sample No", rd.model(NG + "sampleno"));
        after.put("Order No", rd.model(NG + "orderno"));
        after.put("Test Name (shown)", rd.shownValue(NG + "ServiceName"));
        after.put("From Date", rd.model(NG + "FromDate"));
        after.put("Date Range", String.valueOf(rd.dateRangeTicked()));
        after.put("Reg. Type", rd.checkedRadio(NG + "OPDIPD"));
        after.put("Delivery status", rd.checkedRadio(NG + "ReportUploaded"));
        List<String> notReset = new ArrayList<>();
        for (String k : Arrays.asList("MRN", "Sample No", "Order No", "Test Name (shown)")) if (!after.get(k).isEmpty()) notReset.add(k + "=\"" + after.get(k) + "\"");
        for (String k : Arrays.asList("From Date", "Date Range", "Reg. Type", "Delivery status"))
            if (!after.get(k).equals(defaults.getOrDefault(k, after.get(k)))) notReset.add(k + "=\"" + after.get(k) + "\" (opened as \"" + defaults.get(k) + "\")");
        if (!notReset.isEmpty()) findings.add("Clear leaves filters set: " + notReset);
        step("RD-14 Clear resets the filters", "Fill MRN, Sample No, Order No, Test Name; change From Date, Reg. Type IPD, Delivered, untick Date Range; click Clear",
                "Every filter returns to its opening value and the grids are emptied",
                (notReset.isEmpty() ? "All reset" : "NOT reset: " + notReset) + "; Test Details " + rd.rowCount(TESTS) + " record(s)",
                notReset.isEmpty() && rd.rowCount(TESTS) == 0 ? "PASS" : "FAIL");
        rd.setDateRange(true);
        rd.enterDates(today, today);
        rd.clickRadio(NG + "OPDIPD", "^opd");
        rd.clickRadio(NG + "ReportUploaded", "^not\\s*delivered");
        step("RD-14b Test Name lookup (autocomplete)", "Type part of a test name in Test Name and pick a suggestion",
                "Matching tests are suggested; picking one fills Test Name and is used by Search",
                "NOT RUN — each keystroke posts to /API/SampleCollection/Search (ExecFlag SearchServices). The browser side shows a lookup, but "
                        + "the server code is not available to confirm it is read-only, so the read-only guard keeps it blocked. Check by hand, or add it "
                        + "to the guard's allowlist once the endpoint is confirmed read-only.", "MANUAL");
    }

    private void actionsWithoutSelection() {
        String[][] actions = {
                {"Bulk print", "Bulkprintorder\\(\\)"},
                {"Combine print", "printorder\\(\\)"},
                {"Separate print", "printseprateReport\\(\\)"},
                {"Delivered", "opendeliveredpopup\\(\\);\\s*fnSetDelivered"}};
        int n = 0;
        for (String[] a : actions) {
            n++;
            int before = capturedAlerts().size();
            boolean pressed = rd.pressAction(a[1], a[0]);
            List<String> msgs = newAlerts(before);
            List<String> dialogs = rd.openDialogs();
            boolean ok = pressed && !msgs.isEmpty() && dialogs.isEmpty();
            String wording = msgs.isEmpty() ? "" : msgs.get(msgs.size() - 1);
            if (a[0].equals("Combine print") && wording.toLowerCase().contains("same order"))
                findings.add("Combine print with nothing selected says '" + wording + "' instead of asking to select a test");
            step("RD-15." + n + " " + a[0] + " with no test selected", "Click " + a[0] + " without ticking a Test Details record",
                    "A message asks to select a test; no dialog, print or delivery happens",
                    (pressed ? "" : "Button not pressed: " + rd.problems + "; ") + "message(s) " + (msgs.isEmpty() ? "none" : msgs)
                            + "; dialogs open " + (dialogs.isEmpty() ? "none" : dialogs),
                    ok ? "PASS" : "FAIL");
            if (!dialogs.isEmpty()) rd.closeDialogs();
        }
    }

    private void deliveredDialog() {
        rd.clickRadio(NG + "ReportUploaded", "^not\\s*delivered");
        if (subGroup.isEmpty() || rd.selectSubGroup(subGroup).isEmpty() || rd.clickSearch(30000) == 0 || rd.rowCount(TESTS) == 0) {
            step("RD-16 Delivered dialog with a test selected", "Tick a Not Delivered record and click Delivered", "A Not Delivered record to tick",
                    "SKIPPED — no Not Delivered record on this environment" + (subGroup.isEmpty() ? " (no Sub Group to search with)" : ""), "SKIPPED");
        } else {
            boolean sel = rd.selectFirstTest();
            rd.pressAction("opendeliveredpopup\\(\\);\\s*fnSetDelivered", "Delivered");
            List<String> dialogs = rd.openDialogs();
            String body = rd.deliveredDialog();
            boolean ok = sel && dialogs.contains("CashModal");
            step("RD-16 Delivered dialog with a test selected", "Tick the first Not Delivered record and click Delivered; the dialog is closed WITHOUT saving",
                    "The Delivered dialog opens with Delivered By and its save / cancel buttons",
                    (sel ? "Record ticked; " : "Record NOT ticked; ") + "dialogs " + dialogs + "; " + body, ok ? "PASS" : "FAIL");
            rd.closeDialogs();
        }
        step("RD-17 Mark a report as delivered", "Tick a record, click Delivered, choose Delivered By and save",
                "'Report Delivered Successfully'; the record moves to Delivered",
                "NOT RUN — saving marks a real patient report as delivered (/API/ReportDelivery/IUD) and triggers SMS/e-mail. Do this by hand on a test order.",
                "MANUAL");
    }

    private void printManual() {
        step("RD-18 Print a report (Bulk / Combine / Separate)", "Tick a delivered test and print",
                "The report prints / opens as PDF with the patient's results",
                "NOT RUN — printing generates an official patient report (and may record a print). Check by hand on a test order; the no-selection "
                        + "messages are covered by RD-15.", "MANUAL");
    }

    private void deliveryStatus() {
        List<String> legend = rd.legend();
        boolean ok = legend.stream().anyMatch(x -> x.equalsIgnoreCase("Report Delivered")) && legend.stream().anyMatch(x -> x.toLowerCase().startsWith("report pending"));
        if (legend.stream().anyMatch(x -> x.toLowerCase().contains("report pending / pending reports")))
            findings.add("Legend repeats itself: 'Report Pending / Pending Reports'");
        step("RD-19 Delivery status legend", "Read the legend under the grid", "Report Delivered and Report Pending are explained, plus Stat / VIP / Infectious",
                "Legend: " + legend, ok ? "PASS" : "FAIL");

        if (subGroup.isEmpty()) {
            step("RD-19b Delivery status matches the filter", "Search Not Delivered and Delivered and read each record's delivered flag",
                    "Records to read", "SKIPPED — no Sub Group to search with", "SKIPPED");
            return;
        }
        StringBuilder sb = new StringBuilder();
        boolean consistent = true, any = false;
        for (String[] s : new String[][]{{"^not\\s*delivered", "false"}, {"^delivered", "true"}}) {
            rd.selectSubGroup(subGroup);
            rd.clickRadio(NG + "ReportUploaded", s[0]);
            rd.clickSearch(30000);
            List<String> flags = rd.deliveredFlags();
            any |= !flags.isEmpty();
            long wrong = flags.stream().filter(f -> !f.isEmpty() && !f.equalsIgnoreCase(s[1])).count();
            consistent &= wrong == 0;
            sb.append(rd.checkedRadio(NG + "ReportUploaded")).append(": ").append(flags.size()).append(" record(s)")
                    .append(wrong > 0 ? ", " + wrong + " with the OTHER status" : "").append(flags.isEmpty() ? "" : "; first " + rd.testRecords(1)).append(" | ");
        }
        step("RD-19b Delivery status matches the filter", "Search Not Delivered and Delivered and read each record's delivered flag",
                "Not Delivered lists only undelivered reports; Delivered only delivered ones",
                any ? sb.toString() : "SKIPPED — neither search returned a record: " + sb, any ? (consistent ? "PASS" : "FAIL") : "SKIPPED");
        rd.clickRadio(NG + "ReportUploaded", "^not\\s*delivered");
    }

    private void layout() {
        String from = rd.boxFit(NG + "FromDate"), to = rd.boxFit(NG + "ToDate");
        boolean cut = clipped(from) || clipped(to);
        if (cut) findings.add("From/To Date boxes too narrow: the date is cut off (shown/needed px " + from + ", " + to + ")");
        step("RD-20 Date boxes show the full date", "Compare each date box's visible width with the width its text needs",
                "The whole dd/MM/yyyy date is visible", "From Date " + from + " px, To Date " + to + " px (shown/needed)" + (cut ? " — text is CUT OFF" : ""),
                cut ? "FAIL" : "PASS");
    }

    private void stability() {
        page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>x.getAttribute('href')==='#/home'); if(a) a.click(); }");
        page.waitForTimeout(2000);
        boolean away = !rd.onScreen();
        boolean back = rd.navigateViaMenu();
        step("RD-21 Leave and re-open the screen", "Go to Home, then open the screen again from the menu",
                "The screen opens again cleanly", (away ? "Left to " + page.url() : "Did not leave") + "; re-opened: " + (back ? "yes, " + page.url() : "NO"),
                back ? "PASS" : "FAIL");
    }

    private void health() {
        List<String> errs = onScreen(consoleErrors), fails = onScreen(apiFailures);
        step("RD-22 Console errors and API failures", "Everything recorded while the screen was open",
                "No console error; no failed API call",
                "Console: " + (errs.isEmpty() ? "none" : errs.size() + " — " + head(errs, 6)) + " | API failures: " + (fails.isEmpty() ? "none" : fails.size() + " — " + head(fails, 6))
                        + " | search answers: " + searchSummary(),
                errs.isEmpty() && fails.isEmpty() ? "PASS" : "FAIL");
        step("RD-23 Read-only guard", "Write calls stopped by the guard", "The run issues no write call",
                blockedWrites.isEmpty() ? "None attempted" : blockedWrites.size() + " blocked: " + head(blockedWrites, 10),
                blockedWrites.isEmpty() ? "PASS" : "FAIL");
    }

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

    private String labSubGroupApi() {
        for (Response r : dropdownResponses) {
            if (!String.valueOf(r.request().postData()).toUpperCase().contains("LABSUBGROUP")) continue;
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

    private List<String> onScreen(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) if (l.toLowerCase().matches("^\\[#/reportdelivery\\].*")) out.add(l);
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

    private static String key(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[\\s*]+", "").replaceAll("\\.+$", ""); }

    private static boolean clipped(String fit) {
        String[] p = fit.split("/");
        try { return p.length == 2 && Integer.parseInt(p[1].trim()) > Integer.parseInt(p[0].trim()) + 1; } catch (Exception e) { return false; }
    }

    private void writeSummary() {
        addSummary("Application URL", BASE + "/" + ROUTE);
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
