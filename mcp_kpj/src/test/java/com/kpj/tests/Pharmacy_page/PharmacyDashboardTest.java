package com.kpj.tests.Pharmacy_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Pharmacy_page.PharmacyDashboard;
import com.kpj.pages.Pharmacy_page.PharmacyDashboard.Widget;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pharmacy &gt; <b>Pharmacy Dashboard</b> ({@code #/pharmacyDashboard}) — READ-ONLY validation of the five widgets
 * (Pending Refill Date, Pending Indents, Re Order Items, Near Expiry Items, Expired Items), their expand/collapse
 * behaviour and the responsive layout, with detection of the 14 defects found in the manual run.
 *
 * <h3>Safety</h3>
 * <ul>
 *   <li>Nothing is added, edited, approved, saved or deleted. The only clicks are the sidebar menu links and the widgets'
 *       expand/collapse buttons (client-side layout only).</li>
 *   <li>A network guard aborts any non-GET {@code /api/} request that is not a read call (Fetch/Get/Load/Search/List…)
 *       while the dashboard is under test, and reports it — a belt-and-braces stop on accidental writes.</li>
 *   <li>Credentials come ONLY from {@code DEVHIS_USER} / {@code DEVHIS_PASS} (or {@code -Ddevhis.user/-Ddevhis.pass});
 *       the test stops before logging in when they are missing, so {@code DevHisBase}'s built-in default account and
 *       {@code LoginPage}'s fallback accounts are never used. The user name/password are never written to the report or
 *       the console, and no screenshot is taken while the login form is filled.</li>
 * </ul>
 *
 * <h3>Data</h3>
 * Nothing is hardcoded: row counts, dates and the near-expiry window (scope {@code ExpiredMonthNo}) are read from the
 * live screen and its {@code POST /api/pharmacyDashboard/Fetch} responses (one per ExecFlag), which are only observed.
 *
 * <p>Each check is one row (PASS / FAIL / SKIPPED) in {@code test-output/reports/Pharmacy_Dashboard.html}. A check
 * that reproduces one of the known defects FAILS with "DEFECT-nn" in its name and is listed again in the summary.</p>
 *
 * <pre>
 *   run-pharmacydashboard.bat
 *   mvn test -Dtest=com.kpj.tests.Pharmacy_page.PharmacyDashboardTest
 * </pre>
 * Optional tuning: {@code -Dpharmacy.zeroStockMaxPct=50} (share of zero-stock Re Order rows that counts as "many"),
 * {@code -Dpharmacy.nonPharmacyStoreRegex=...} (store names that should never own pharmacy reorder lines).
 */
public class PharmacyDashboardTest extends DevHisBase {

    public PharmacyDashboardTest() { super("Pharmacy_Dashboard"); }

    public static void main(String[] args) {
        PharmacyDashboardTest t = new PharmacyDashboardTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    // ---- collected evidence ----------------------------------------------------------------------------------------
    private final List<String> defects = new ArrayList<>();
    private final List<String> consoleErrors = new ArrayList<>();
    private final List<String> apiFailures = new ArrayList<>();
    private final List<String> resourceFailures = new ArrayList<>();
    private final List<String> blockedWrites = new ArrayList<>();
    private final List<Response> fetchResponses = new ArrayList<>();
    private final Map<String, Set<String>> dateFormats = new LinkedHashMap<>();
    private PharmacyDashboard dash;
    private final LocalDate today = LocalDate.now();

    private static final int ZERO_STOCK_MAX_PCT = Integer.getInteger("pharmacy.zeroStockMaxPct", 50);
    private static final Pattern NON_PHARMACY_STORE = Pattern.compile(System.getProperty("pharmacy.nonPharmacyStoreRegex",
            "information\\s*technology|\\bI\\.?T\\.?\\b|finance|account|human\\s*resource|\\bHR\\b|admin|housekeeping|maintenance"
                    + "|kitchen|laundry|security|marketing|purchasing\\s*office"), Pattern.CASE_INSENSITIVE);
    /** Last path segment of a read-only API call (anything else that is not a GET is blocked while testing). */
    private static final Pattern READ_CALL = Pattern.compile("/(fetch|get|load|search|list|view|find|check|validate|header|dropdown|count|session|notification)[^/]*$",
            Pattern.CASE_INSENSITIVE);

    @Override
    protected void body() {
        meta("Pharmacy - Pharmacy Dashboard", "Pharmacy > Pharmacy Dashboard",
                "<b>Read-only</b> validation of the Pharmacy Dashboard: navigation, the five widgets' data (checked against the live "
                        + "<code>/api/pharmacyDashboard/Fetch</code> responses), expand/collapse and responsive layout. A FAIL named "
                        + "<b>DEFECT-nn</b> means a known defect was reproduced on this run. No record is added, edited or saved.");

        // ---------------- A. Credentials, login, navigation ------------------------------------------------------
        if (!credentialsFromEnvironment()) return;

        attachMonitors();
        login();   // DevHisBase: env-driven, single account, no fallback rotation
        step("A1 Login", "Log in with the DEVHIS_USER / DEVHIS_PASS account (values not shown)",
                "Authenticated; application shell loaded", "Logged in — " + page.url().replaceAll("[?].*$", ""), "PASS");
        installReadOnlyGuard();

        dash = new PharmacyDashboard(page);
        try {
            section("A. Login and Navigation", this::navigationAndOverview);
            if (!dash.onRoute()) { addSummary("Result", "FAILED — the Pharmacy Dashboard could not be opened"); return; }
            section("B. Pending Refill Date", this::pendingRefill);
            section("C. Pending Indents", this::pendingIndents);
            section("D. Re Order Items", this::reorderItems);
            section("E. Near Expiry Items", this::nearExpiry);
            section("F. Expired Items", this::expiredItems);
            section("Date formats across widgets", this::dateFormatConsistency);
            section("G. Expand and Collapse", this::expandCollapse);
            section("H. Responsive Layout", this::responsive);
            section("Direct dashboard URL after login", this::directUrlAfterLogin);
            section("Console and API health", this::consoleAndApiHealth);
        } finally {
            try { page.unroute("**/api/**"); } catch (Exception ignore) { }
            writeSummary();
        }
    }

    // =================================================================================================================
    // A. Login and navigation
    // =================================================================================================================

    private boolean credentialsFromEnvironment() {
        boolean user = present("devhis.user", "DEVHIS_USER"), pass = present("devhis.pass", "DEVHIS_PASS");
        if (user && pass) {
            System.out.println("[Pharmacy_Dashboard] credentials supplied via environment (values not shown)");
            return true;
        }
        String missing = (user ? "" : "DEVHIS_USER ") + (pass ? "" : "DEVHIS_PASS");
        step("A0 Credentials from environment", "Read the test account from DEVHIS_USER / DEVHIS_PASS",
                "Both variables are set (never hardcoded)", "Missing: " + missing.trim()
                        + " — set them (e.g. set DEVHIS_USER=... / set DEVHIS_PASS=...) and re-run. Login was NOT attempted.", "FAIL");
        addSummary("Result", "NOT RUN — credentials not supplied through the environment (" + missing.trim() + ")");
        return false;
    }

    private static boolean present(String prop, String env) {
        String p = System.getProperty(prop), e = System.getenv(env);
        return (p != null && !p.isBlank()) || (e != null && !e.isBlank());
    }

    private void navigationAndOverview() {
        boolean viaMenu = dash.navigateViaMenu();
        String path = dash.lastMenuPath.isEmpty() ? "sidebar > Pharmacy Dashboard" : dash.lastMenuPath;
        String notes = dash.navNotes.isEmpty() ? "" : " [" + String.join("; ", dash.navNotes) + "]";
        step("A2 Navigate through the menu", "Open the sidebar: Pharmacy (or Inventory on this build) > Pharmacy Dashboard",
                "Route #/pharmacyDashboard opens, the five widgets render and their data calls finish — with no wait timing out",
                viaMenu ? "Opened via " + path + " — " + page.url() + notes : "Menu navigation did NOT complete" + notes,
                viaMenu ? "PASS" : "FAIL");
        if (!viaMenu) {   // keep going so the widgets are still checked; the navigation failure is already recorded
            dash.navNotes.clear();
            openViaHash("pharmacyDashboard");
            if (!dash.waitForDashboard()) {
                step(page, "A2b Open the dashboard by its route (fallback)", "Navigate to " + PharmacyDashboard.ROUTE, "Dashboard loads",
                        "Not loaded [" + String.join("; ", dash.navNotes) + "]", "FAIL");
                return;
            }
        }

        String heading = dash.heading();
        boolean headingOk = heading.equalsIgnoreCase("Pharmacy Dashboard");
        step("A3 Verify the page heading", "Read the page heading (h1#mainHeader)", "Heading reads \"Pharmacy Dashboard\"",
                "Heading: \"" + heading + "\"", headingOk ? "PASS" : "FAIL");

        List<String> missing = new ArrayList<>(), found = new ArrayList<>();
        for (Widget w : Widget.values()) {
            String t = dash.title(w);
            if (dash.isDisplayed(w) && t.toLowerCase().startsWith(w.title.toLowerCase())) found.add(t); else missing.add(w.title + " (shown: \"" + t + "\")");
        }
        dash.scrollTop();
        step(fullPage(), "A4 Verify all five widgets are displayed", "Check each widget section is visible with its title",
                "Pending Refill Date, Pending Indents, Re Order Items, Near Expiry Items and Expired Items are displayed",
                missing.isEmpty() ? "All 5 displayed: " + String.join(" | ", found) : "Missing/wrong: " + String.join("; ", missing),
                missing.isEmpty() ? "PASS" : "FAIL");

        // API: the five Fetch calls that fed the widgets
        Map<String, Integer> apiCounts = new LinkedHashMap<>();
        List<String> apiNotes = new ArrayList<>();
        boolean apiOk = true;
        for (Response r : new ArrayList<>(fetchResponses)) {
            String flag = execFlag(r);
            int count = -3;
            try { count = jsonArrayLength(r.text()); } catch (Exception e) { apiNotes.add(flag + ": body unreadable"); }
            apiCounts.put(flag, count);
            if (r.status() != 200 || count < 0) apiOk = false;
            apiNotes.add(flag + " HTTP " + r.status() + " → " + (count >= 0 ? count + " row(s)" : "not a JSON array"));
        }
        List<String> mismatch = new ArrayList<>();
        for (Widget w : Widget.values()) {
            Integer api = apiCounts.get(w.execFlag);
            int ui = dataRows(w, dash.headers(w).size()).size();
            int scope = dash.scopeData(w, "itemcode").size();
            if (api == null) { mismatch.add(w.title + ": no Fetch response captured for ExecFlag " + w.execFlag); continue; }
            if (api != ui || api != scope) mismatch.add(w.title + ": API " + api + " / scope " + scope + " / table " + ui);
        }
        boolean ok = apiOk && apiCounts.size() >= Widget.values().length && mismatch.isEmpty();
        step("A5 Inspect the dashboard API responses", "Observe POST " + PharmacyDashboard.API_FETCH + " (one per ExecFlag) and compare with the tables",
                "5 calls answer HTTP 200 with a JSON array, and every widget shows exactly the rows its call returned",
                String.join("; ", apiNotes) + (mismatch.isEmpty() ? " — row counts match the tables" : " — MISMATCH: " + String.join("; ", mismatch)),
                ok ? "PASS" : "FAIL");
        addSummary("API row counts (ExecFlag → rows)", apiCounts.toString());
    }

    // =================================================================================================================
    // B. Pending Refill Date
    // =================================================================================================================

    private void pendingRefill() {
        Widget w = Widget.PENDING_REFILL;
        List<String> expected = List.of("No", "Patient Name", "Patient MRN", "Prescription Number", "Prescription Date", "Refill Date");
        List<List<String>> rows = structure(w, expected, "B1");
        if (rows.isEmpty()) { skipRest(w, "B2-B6"); return; }

        // B2 patient details
        // MRNs are not always numeric: hisdemo also issues "PRN-01-1338758"-style numbers (first run flagged those wrongly as
        // bad). A valid MRN is a non-empty code of letters/digits/dashes; the formats seen are reported for information.
        List<String> bad = new ArrayList<>();
        Map<String, Integer> mrnFormats = new TreeMap<>();
        for (List<String> r : rows) {
            if (r.get(1).isEmpty()) bad.add("row " + r.get(0) + ": no patient name");
            if (!r.get(2).matches("[A-Za-z0-9][A-Za-z0-9-]{4,}")) bad.add("row " + r.get(0) + ": MRN \"" + r.get(2) + "\"");
            mrnFormats.merge(r.get(2).replaceAll("[0-9]", "9").replaceAll("[A-Za-z]", "A"), 1, Integer::sum);
        }
        check("B2 Check patient details", "Every row has a patient name and a well-formed MRN (letters/digits/dashes)",
                "Name and MRN present on every row",
                (bad.isEmpty() ? rows.size() + " row(s) all carry a patient name and MRN" : bad.size() + " problem(s): " + head(bad, 10))
                        + " — MRN formats seen: " + mrnFormats, bad.isEmpty());

        // B3 DEFECT-05 prescription numbers with missing segments (or empty)
        List<String> malformed = new ArrayList<>();
        for (List<String> r : rows) {
            String p = r.get(3);
            if (p.isEmpty() || p.contains("--") || p.startsWith("-") || p.endsWith("-") || p.contains(" "))
                malformed.add("row " + r.get(0) + " " + r.get(1) + ": \"" + p + "\"");
        }
        defect(5, "Some prescription numbers have missing segments", "B3 Validate empty / incomplete prescription numbers",
                "Read the Prescription Number of every row",
                "Every prescription number is present and complete (no empty value, no '--' or dangling '-')",
                malformed.isEmpty() ? "All " + rows.size() + " prescription numbers are complete"
                        : malformed.size() + " empty/incomplete: " + head(malformed, 12), !malformed.isEmpty());

        // B4 DEFECT-04 duplicate prescription numbers for different patients
        Map<String, Set<String>> byPresc = new LinkedHashMap<>();
        for (List<String> r : rows) if (!r.get(3).isEmpty()) byPresc.computeIfAbsent(r.get(3), k -> new LinkedHashSet<>()).add(r.get(1) + " (" + r.get(2) + ")");
        List<String> dupDiff = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : byPresc.entrySet()) if (e.getValue().size() > 1) dupDiff.add(e.getKey() + " → " + String.join(", ", e.getValue()));
        defect(4, "Duplicate prescription numbers appear for different patients", "B4 Validate duplicate prescription numbers",
                "Group rows by Prescription Number", "A prescription number belongs to one patient only",
                dupDiff.isEmpty() ? "No prescription number is shared by different patients" : dupDiff.size() + " shared: " + head(dupDiff, 8),
                !dupDiff.isEmpty());

        // B5 DEFECT-06 refill earlier than prescription; invalid dates
        List<String> earlier = new ArrayList<>(), invalid = new ArrayList<>();
        for (List<String> r : rows) {
            LocalDate pd = date("Pending Refill Date › Prescription Date", r.get(4)), rd = date("Pending Refill Date › Refill Date", r.get(5));
            if (pd == null || rd == null) { invalid.add("row " + r.get(0) + ": \"" + r.get(4) + "\" / \"" + r.get(5) + "\""); continue; }
            if (rd.isBefore(pd)) earlier.add(r.get(3) + ": refill " + r.get(5) + " < prescription " + r.get(4));
        }
        defect(6, "Some refill dates are earlier than prescription dates", "B5 Check refill dates / date consistency",
                "Compare Refill Date with Prescription Date on every row",
                "Both dates are valid and Refill Date is on or after Prescription Date",
                (earlier.isEmpty() ? "No refill date precedes its prescription date" : earlier.size() + " row(s) with refill before prescription: " + head(earlier, 10))
                        + (invalid.isEmpty() ? "" : " — invalid/missing dates: " + head(invalid, 6)),
                !earlier.isEmpty() || !invalid.isEmpty());

        // B6 sorting (if available)
        List<String> sortable = dash.sortableHeaders(w);
        String order = orderOf(rows, 5);
        if (sortable.isEmpty()) {
            dash.scrollTo(w);
            step("B6 Verify sorting (if available)", "Look for sortable column headers", "Sorting controls on the column headers (optional)",
                    "No sortable headers on this widget — sorting not available. Displayed order of Refill Date: " + order, "SKIPPED");
        } else {
            check("B6 Verify sorting (if available)", "Sortable headers found", "Rows sorted", "Sortable: " + sortable + "; Refill Date order: " + order, true);
        }
    }

    // =================================================================================================================
    // C. Pending Indents
    // =================================================================================================================

    private void pendingIndents() {
        Widget w = Widget.PENDING_INDENTS;
        List<String> expected = List.of("Indent No.", "Date", "Due Date", "From Store", "To Store", "Pending Quantity", "Status");
        List<String> headers = dash.headers(w);
        List<List<String>> raw = dash.rows(w);
        dash.scrollTo(w);
        boolean headersOk = headers.equals(expected);
        step("C1 Verify widget visibility and column headers", "Read the Pending Indents title and header row",
                "Widget visible; headers " + expected, (dash.isDisplayed(w) ? "Visible" : "NOT visible") + "; headers " + headers + "; " + raw.size() + " row(s)",
                dash.isDisplayed(w) && headersOk ? "PASS" : "FAIL");

        // C2 DEFECT-01 extra unlabelled column → misalignment
        List<String> misaligned = new ArrayList<>();
        Map<String, Integer> extraValues = new LinkedHashMap<>();
        for (List<String> r : raw) if (r.size() != headers.size()) {
            misaligned.add(r.size() + " cells");
            if (r.size() > headers.size()) extraValues.merge("\"" + r.get(0) + "\"", 1, Integer::sum);
        }
        String sample = raw.isEmpty() ? "" : " — e.g. first row: " + pairUp(headers, raw.get(0));
        defect(1, "Pending Indents grid has an extra unlabelled column, causing column misalignment", "C2 Validate row alignment with headers",
                "Compare the number of cells in each row with the number of column headers",
                "Each row has exactly " + headers.size() + " cells, one under each header",
                misaligned.isEmpty() ? "All " + raw.size() + " rows align with the " + headers.size() + " headers"
                        : misaligned.size() + "/" + raw.size() + " rows have " + (raw.get(0).size()) + " cells for " + headers.size()
                        + " headers; leading unlabelled cell values " + extraValues + sample, !misaligned.isEmpty());

        // The API data is the trustworthy, aligned source for the field checks below.
        List<Map<String, Object>> data = dash.scopeData(w, "identno", "date", "duedate", "fromstore", "tostore", "pendingquantity", "pendingquantitystatus");
        if (data.isEmpty()) { skipRest(w, "C3-C6"); return; }

        // C3 indent number / date / status
        List<String> bad = new ArrayList<>();
        for (Map<String, Object> d : data) {
            String no = s(d.get("identno"));
            if (no.isEmpty()) bad.add("an indent without a number");
            if (date("Pending Indents › Date", s(d.get("date"))) == null) bad.add(no + ": date \"" + s(d.get("date")) + "\"");
            if (s(d.get("pendingquantitystatus")).isEmpty()) bad.add(no + ": no status");
            if (s(d.get("fromstore")).isEmpty() || s(d.get("tostore")).isEmpty()) bad.add(no + ": store missing");
        }
        check("C3 Verify indent number, date and status", "Check Indent No., Date, From/To Store and Status of every indent",
                "Every indent has a number, a valid date, both stores and a status",
                bad.isEmpty() ? data.size() + " indent(s) complete" : bad.size() + " problem(s): " + head(bad, 10), bad.isEmpty());

        // C4 DEFECT-07 missing due dates
        List<String> noDue = new ArrayList<>();
        for (Map<String, Object> d : data) {
            String due = s(d.get("duedate"));
            if (due.isEmpty()) noDue.add(s(d.get("identno")));
            else date("Pending Indents › Due Date", due);
        }
        defect(7, "Pending Indents has missing due dates", "C4 Check missing due dates", "Read the Due Date of every indent",
                "Every pending indent has a due date",
                noDue.isEmpty() ? "All " + data.size() + " indents have a due date" : noDue.size() + "/" + data.size() + " without a due date: " + head(noDue, 12),
                !noDue.isEmpty());

        // C5 DEFECT-08 overdue indents not highlighted
        List<Map<String, Object>> styles = dash.rowStyles(w);
        List<Integer> overdueIdx = new ArrayList<>();
        Set<String> normalLook = new HashSet<>();
        for (int i = 0; i < data.size(); i++) {
            LocalDate due = parse(s(data.get(i).get("duedate")));
            Map<String, Object> st = i < styles.size() ? styles.get(i) : Map.of();
            if (due != null && due.isBefore(today)) overdueIdx.add(i);
            else normalLook.add(s(st.get("color")) + "|" + s(st.get("bg")));
        }
        List<String> notHighlighted = new ArrayList<>(), highlighted = new ArrayList<>();
        for (int i : overdueIdx) {
            Map<String, Object> st = i < styles.size() ? styles.get(i) : Map.of();
            boolean hl = !s(st.get("style")).isEmpty() || s(st.get("cls")).matches("(?i).*(overdue|danger|warning|red|highlight|late).*")
                    || (!normalLook.isEmpty() && !normalLook.contains(s(st.get("color")) + "|" + s(st.get("bg"))));
            String label = s(data.get(i).get("identno")) + " (due " + s(data.get(i).get("duedate")) + ")";
            (hl ? highlighted : notHighlighted).add(label);
        }
        dash.scrollTo(w);
        if (overdueIdx.isEmpty()) {
            step("C5 Identify overdue indents / highlight [DEFECT-08 check]", "Find indents whose due date is before today (" + today + ")",
                    "Overdue indents are visually highlighted", "No indent is overdue today — highlighting cannot be checked on this data", "SKIPPED");
        } else {
            defect(8, "Overdue indents are not highlighted", "C5 Identify overdue indents and their highlighting",
                    "Find indents whose due date is before today (" + today + ") and inspect their row styling",
                    "Every overdue indent is visually highlighted (colour / class)",
                    overdueIdx.size() + " overdue: " + (notHighlighted.isEmpty() ? "all highlighted" : notHighlighted.size() + " NOT highlighted: " + head(notHighlighted, 10))
                            + (highlighted.isEmpty() ? "" : "; highlighted: " + head(highlighted, 5)), !notHighlighted.isEmpty());
        }

        // C6 status information
        Map<String, Integer> statuses = new TreeMap<>();
        for (Map<String, Object> d : data) statuses.merge(s(d.get("pendingquantitystatus")).isEmpty() ? "(blank)" : s(d.get("pendingquantitystatus")), 1, Integer::sum);
        check("C6 Verify status information", "Summarise the Status column", "Every indent shows a status",
                "Status distribution: " + statuses, !statuses.containsKey("(blank)"));
    }

    // =================================================================================================================
    // D. Re Order Items
    // =================================================================================================================

    private void reorderItems() {
        Widget w = Widget.REORDER_ITEMS;
        List<String> expected = List.of("Item Code", "Store", "Item", "Re Order Stock", "Available Stock");
        List<List<String>> rows = structure(w, expected, "D1");
        if (rows.isEmpty()) { skipRest(w, "D2-D6"); return; }

        // D2 item name / store / stock present
        List<String> bad = new ArrayList<>();
        for (List<String> r : rows) {
            if (r.get(0).isEmpty() || r.get(2).isEmpty()) bad.add("item code/name missing: " + r);
            if (r.get(1).isEmpty()) bad.add(r.get(0) + ": no store");
            if (num(r.get(4)) == null || num(r.get(3)) == null) bad.add(r.get(0) + ": non-numeric stock \"" + r.get(3) + "\"/\"" + r.get(4) + "\"");
        }
        check("D2 Validate item name, store and available stock", "Check Item Code, Item, Store, Re Order Stock and Available Stock",
                "Every line has an item, a store and numeric stock figures",
                bad.isEmpty() ? rows.size() + " line(s) complete" : bad.size() + " problem(s): " + head(bad, 10), bad.isEmpty());

        // D3 reorder level vs available stock
        List<String> above = new ArrayList<>();
        for (List<String> r : rows) {
            Double ro = num(r.get(3)), av = num(r.get(4));
            if (ro != null && av != null && av > ro) above.add(r.get(0) + " @ " + r.get(1) + ": available " + r.get(4) + " > reorder " + r.get(3));
        }
        check("D3 Check reorder level against available stock", "Compare Available Stock with Re Order Stock",
                "Only items at or below their reorder level are listed",
                above.isEmpty() ? "All " + rows.size() + " lines are at or below their reorder level" : above.size() + " listed although above reorder level: " + head(above, 10),
                above.isEmpty());

        // D4 DEFECT-10 zero stock
        List<String> zero = new ArrayList<>();
        for (List<String> r : rows) { Double av = num(r.get(4)); if (av != null && av == 0) zero.add(r.get(0) + " @ " + r.get(1)); }
        int pct = (int) Math.round(100.0 * zero.size() / rows.size());
        addSummary("Re Order Items with zero stock", zero.size() + " of " + rows.size() + " (" + pct + "%)");
        defect(10, "Many reorder items show zero available stock", "D4 Identify zero-stock items",
                "Count lines whose Available Stock is 0", "Zero-stock lines stay at or below " + ZERO_STOCK_MAX_PCT + "% of the list",
                zero.size() + " of " + rows.size() + " lines (" + pct + "%) have zero stock" + (zero.isEmpty() ? "" : ", e.g. " + head(zero, 8)),
                pct > ZERO_STOCK_MAX_PCT);

        // D5 DEFECT-09 item under the wrong store
        List<String> wrong = new ArrayList<>();
        Map<String, Integer> stores = new TreeMap<>();
        for (List<String> r : rows) {
            stores.merge(r.get(1), 1, Integer::sum);
            if (NON_PHARMACY_STORE.matcher(r.get(1)).find()) wrong.add(r.get(0) + " \"" + r.get(2) + "\" under \"" + r.get(1) + "\"");
        }
        defect(9, "Re Order Items displays an item under the wrong store", "D5 Check items belong to the correct store",
                "Check the Store of every line against non-pharmacy departments (" + NON_PHARMACY_STORE.pattern() + ")",
                "Pharmacy reorder lines belong only to pharmacy / inventory stores",
                (wrong.isEmpty() ? "No line under a non-pharmacy store" : wrong.size() + " line(s) under a non-pharmacy store: " + head(wrong, 8))
                        + " — " + stores.size() + " store(s) listed", !wrong.isEmpty());

        // D6 DEFECT-11 search / filter / pagination
        Map<String, Object> c = dash.listControls(w);
        boolean search = Boolean.TRUE.equals(c.get("search")), filter = Boolean.TRUE.equals(c.get("filter")), pag = Boolean.TRUE.equals(c.get("pagination"));
        dash.scrollTo(w);
        defect(11, "Re Order Items has no search, filter or pagination", "D6 Verify search, filter and pagination",
                "Look for a search box, a filter control and paging in the widget",
                "A " + rows.size() + "-line list offers search, filter and pagination",
                "search: " + yn(search) + ", filter: " + yn(filter) + ", pagination: " + yn(pag) + " — all " + rows.size() + " lines rendered in one scrolling table",
                !(search && filter && pag));
    }

    // =================================================================================================================
    // E. Near Expiry Items
    // =================================================================================================================

    private void nearExpiry() {
        Widget w = Widget.NEAR_EXPIRY;
        List<String> expected = List.of("Item Code", "Item Name", "Batch Code", "Store", "Expiry Date", "Today's Available Stock");
        List<List<String>> rows = structure(w, expected, "E1");
        int months = dash.nearExpiryMonths();
        Matcher m = Pattern.compile("(\\d+)\\s*Month").matcher(dash.title(w));
        int titleMonths = m.find() ? Integer.parseInt(m.group(1)) : -1;
        if (months <= 0) months = titleMonths > 0 ? titleMonths : 3;
        addSummary("Near-expiry window", months + " month(s) (screen config ExpiredMonthNo; title says " + (titleMonths > 0 ? titleMonths : "?") + ")");
        if (rows.isEmpty()) { skipRest(w, "E2-E4"); return; }

        // E2 missing / invalid expiry dates; item details
        List<String> invalid = new ArrayList<>(), detail = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();
        for (List<String> r : rows) {
            LocalDate d = date("Near Expiry › Expiry Date", r.get(4));
            if (d == null) invalid.add(r.get(0) + "/" + r.get(2) + ": \"" + r.get(4) + "\""); else dates.add(d);
            if (r.get(0).isEmpty() || r.get(1).isEmpty() || r.get(2).isEmpty() || r.get(3).isEmpty()) detail.add(String.join(" | ", r));
        }
        check("E2 Verify item details and expiry dates", "Check item code, name, batch, store and a valid Expiry Date on every row",
                "All details present; every expiry date valid",
                (invalid.isEmpty() ? "All " + rows.size() + " expiry dates valid" : invalid.size() + " missing/invalid: " + head(invalid, 8))
                        + (detail.isEmpty() ? "" : "; incomplete rows: " + head(detail, 5)), invalid.isEmpty() && detail.isEmpty());

        // E3 within the next N months
        LocalDate limit = today.plusMonths(months);
        List<String> outside = new ArrayList<>();
        for (List<String> r : rows) {
            LocalDate d = parse(r.get(4));
            if (d != null && (d.isBefore(today) || d.isAfter(limit))) outside.add(r.get(0) + "/" + r.get(2) + " expires " + r.get(4));
        }
        check("E3 Validate items fall within the next " + months + " months", "Compare each Expiry Date with today (" + today + ") and " + limit,
                "Every expiry date is between today and " + limit,
                outside.isEmpty() ? "All " + rows.size() + " items expire between " + today + " and " + limit : outside.size() + " outside the window: " + head(outside, 10),
                outside.isEmpty());

        // E4 date sorting
        int breaks = 0; String firstBreak = "";
        for (int i = 1; i < dates.size(); i++) if (dates.get(i).isBefore(dates.get(i - 1))) { if (breaks++ == 0) firstBreak = dates.get(i - 1) + " then " + dates.get(i); }
        dash.scrollTo(w);
        step("E4 Verify date sorting", "Check Expiry Date order down the list", "Rows ordered by Expiry Date (earliest first)",
                breaks == 0 ? "Sorted ascending by expiry (" + dates.size() + " dates)" : breaks + " order break(s), first: " + firstBreak,
                breaks == 0 ? "PASS" : "FAIL");
    }

    // =================================================================================================================
    // F. Expired Items
    // =================================================================================================================

    private void expiredItems() {
        Widget w = Widget.EXPIRED_ITEMS;
        List<String> expected = List.of("Item Code", "Item Name", "Batch Code", "Store", "Expiry Date", "Today's Available Stock");
        List<List<String>> rows = structure(w, expected, "F1");
        if (rows.isEmpty()) { skipRest(w, "F2-F3"); return; }

        // F2 expiry before today
        List<String> notExpired = new ArrayList<>(), invalid = new ArrayList<>();
        for (List<String> r : rows) {
            LocalDate d = date("Expired Items › Expiry Date", r.get(4));
            if (d == null) invalid.add(r.get(0) + "/" + r.get(2) + ": \"" + r.get(4) + "\"");
            else if (!d.isBefore(today)) notExpired.add(r.get(0) + "/" + r.get(2) + " expires " + r.get(4));
        }
        check("F2 Validate expiry dates against the current date", "Compare every Expiry Date with today (" + today + ")",
                "Every listed batch expired before today; no missing dates",
                (notExpired.isEmpty() ? "All " + rows.size() + " batches expired before today" : notExpired.size() + " NOT yet expired: " + head(notExpired, 8))
                        + (invalid.isEmpty() ? "" : "; invalid dates: " + head(invalid, 5)), notExpired.isEmpty() && invalid.isEmpty());

        // F3 DEFECT-12 expired batches still holding stock
        List<String> withStock = new ArrayList<>();
        double units = 0;
        for (List<String> r : rows) {
            Double q = num(r.get(5));
            if (q != null && q > 0) { withStock.add(r.get(0) + " " + r.get(1) + " [" + r.get(2) + ", " + r.get(3) + ", exp " + r.get(4) + "] = " + r.get(5)); units += q; }
        }
        if (!withStock.isEmpty()) addSummary("Expired batches still in stock", withStock.size() + " batch(es), " + Math.round(units) + " unit(s): " + head(withStock, 25));
        dash.scrollTo(w);
        defect(12, "Expired Items still display stock for expired batches", "F3 Check expired batches with available stock",
                "Read Today's Available Stock of every expired batch", "Expired batches hold no available stock (quarantined / written off)",
                withStock.isEmpty() ? "No expired batch holds stock" : withStock.size() + " of " + rows.size() + " expired batches still hold "
                        + Math.round(units) + " unit(s): " + head(withStock, 10), !withStock.isEmpty());
    }

    // =================================================================================================================
    // DEFECT-13 date formats
    // =================================================================================================================

    private void dateFormatConsistency() {
        Set<String> all = new LinkedHashSet<>();
        List<String> per = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : dateFormats.entrySet()) { all.addAll(e.getValue()); per.add(e.getKey() + " = " + e.getValue()); }
        dash.scrollTop();
        if (all.isEmpty()) {
            step("DEFECT-13 check: date formats across widgets", "Collect the date formats shown", "One date format", "No dates on screen to compare", "SKIPPED");
            return;
        }
        defect(13, "Date formats are inconsistent across widgets", "Date format consistency across widgets",
                "Collect the format of every date column on the dashboard", "All widgets show dates in one format",
                all.size() + " format(s) in use: " + all + " — " + String.join("; ", per), all.size() > 1);
    }

    // =================================================================================================================
    // G. Expand and collapse
    // =================================================================================================================

    private void expandCollapse() {
        ensureOnDashboard("G. Expand and Collapse");
        dash.scrollTop();
        // G0 DEFECT-14 labels / tooltips
        List<String> unlabelled = new ArrayList<>();
        for (Widget w : Widget.values()) {
            Map<String, Object> b = dash.expandButtonInfo(w);
            if (s(b.get("text")).isEmpty() && s(b.get("title")).isEmpty() && s(b.get("aria")).isEmpty() && s(b.get("iconTitle")).isEmpty())
                unlabelled.add(w.title + " (#" + w.buttonId() + ", icon " + s(b.get("icon")).replace("glyphicon ", "") + ")");
        }
        defect(14, "Expand buttons have no text labels or tooltips", "G0 Check expand button labels / tooltips",
                "Read the text, title and aria-label of each widget's expand button",
                "Every expand button has a text label, tooltip (title) or aria-label",
                unlabelled.isEmpty() ? "All 5 buttons are labelled" : unlabelled.size() + "/5 icon-only buttons: " + String.join("; ", unlabelled),
                !unlabelled.isEmpty());

        Map<Widget, Map<String, Object>> original = dash.layout();
        addSummary("Original widget layout", describe(original));

        int n = 1;
        for (Widget w : Widget.values()) {
            // ---- expand
            if (!dash.clickExpand(w)) {
                step("G" + n + "a Expand " + w.title, "Click the expand button #" + w.buttonId(), "Widget expands", "Button could not be clicked", "FAIL");
                n++; continue;
            }
            Map<Widget, Map<String, Object>> ex = dash.layout();
            Map<String, Object> me = ex.get(w);
            // Two different kinds of problem are kept apart: where a widget SITS (layout) and which icon its button SHOWS.
            List<String> layoutIssues = new ArrayList<>(), iconIssues = new ArrayList<>();
            if (!"full_view".equals(me.get("container"))) layoutIssues.add(w.title + " is in " + me.get("container") + " (expected the wide full_view)");
            if (!"resize-small".equals(me.get("icon"))) iconIssues.add(w.title + " shows " + me.get("icon") + " (expected resize-small = collapse)");
            for (Widget o : Widget.values()) if (o != w) {
                Map<String, Object> ot = ex.get(o);
                if (!"min_view".equals(ot.get("container"))) layoutIssues.add(o.title + " is in " + ot.get("container") + " (expected the side column)");
                if (intv(ot.get("w")) >= intv(me.get("w"))) layoutIssues.add(o.title + " is not narrower than the expanded widget");
                if ("resize-small".equals(ot.get("icon")))
                    iconIssues.add(o.title + " (#" + o.buttonId() + ") shows the COLLAPSE icon although it stays in the side column");
            }
            dash.scrollTop();
            step(fullPage(), "G" + n + "a Expand " + w.title, "Click the expand button #" + w.buttonId(),
                    "Only " + w.title + " expands (wide column) and shows the collapse icon; the other four move to the side column with the expand icon",
                    expandVerdict(w, me, layoutIssues, iconIssues, "expand"),
                    layoutIssues.isEmpty() && iconIssues.isEmpty() ? "PASS" : "FAIL");

            // ---- collapse
            dash.clickExpand(w);
            Map<Widget, Map<String, Object>> col = dash.layout();
            List<String> cLayout = new ArrayList<>(), cIcon = new ArrayList<>();
            for (Widget o : Widget.values()) {
                Map<String, Object> now = col.get(o), was = original.get(o);
                if (!"resize-full".equals(now.get("icon"))) cIcon.add(o.title + " (#" + o.buttonId() + ") still shows " + now.get("icon"));
                if ("full_view".equals(now.get("container")) || "min_view".equals(now.get("container"))) cLayout.add(o.title + " still in " + now.get("container"));
                if (Math.abs(intv(now.get("x")) - intv(was.get("x"))) > 4 || Math.abs(intv(now.get("y")) - intv(was.get("y"))) > 4
                        || Math.abs(intv(now.get("w")) - intv(was.get("w"))) > 4)
                    cLayout.add(o.title + " " + box(was) + " → " + box(now));
            }
            dash.scrollTop();
            step(fullPage(), "G" + n + "b Collapse " + w.title, "Click the same button again",
                    "The dashboard returns to its original layout (same positions and sizes) and every button shows the expand icon",
                    expandVerdict(w, null, cLayout, cIcon, "collapse"),
                    cLayout.isEmpty() && cIcon.isEmpty() ? "PASS" : "FAIL");
            n++;
        }
        Map<String, Object> wrappers = dash.wrapperCounts();
        addSummary("Observation — expand wrappers", "After 10 expand/collapse clicks the dashboard holds " + wrappers
                + " wrapper divs (maxMin() → createDivs() appends three new divs on every click and never removes them)");
        // No page.reload() here: on hisdemo a reload of #/pharmacyDashboard raises a "Leave site?" (beforeunload) prompt and,
        // once accepted, ends the session (lands on /Account/Login) — that is what broke the responsive checks in run 1.
        // The collapse above already restored the layout; the next section re-checks the dashboard is on screen.
    }

    /** Actual text for an expand/collapse step; icon-only problems are attributed to the application's maxMin() code. */
    private String expandVerdict(Widget w, Map<String, Object> me, List<String> layout, List<String> icon, String action) {
        if (layout.isEmpty() && icon.isEmpty())
            return "expand".equals(action) ? w.title + " expanded to " + me.get("w") + "px; the other four in the side column with the expand icon"
                                           : "Original layout restored; all five buttons show the expand icon";
        List<String> parts = new ArrayList<>();
        if (!layout.isEmpty()) parts.add("LAYOUT: " + String.join("; ", layout));
        if (!icon.isEmpty()) {
            parts.add("ICON STATE: " + String.join("; ", icon));
            if (layout.isEmpty()) {
                String d = "NEW-15 Expand/collapse of " + w.title + " leaves the wrong icon on another widget — " + String.join("; ", icon)
                        + " (application defect in pharmacyDashboardController.js maxMin('" + w.buttonId() + "'))";
                if (defects.stream().noneMatch(x -> x.startsWith("NEW-15 Expand/collapse of " + w.title))) defects.add(d);
                parts.add("Layout itself is correct — this is an APPLICATION defect, not an automation error: maxMin('" + w.buttonId()
                        + "') changes another widget's glyph (see pharmacyDashboardController.js)");
            }
        }
        return String.join(" | ", parts);
    }

    /** Make sure the dashboard is the page on screen before a section that depends on it; re-open via the menu if not. */
    private boolean ensureOnDashboard(String section) {
        if (dash.isOnScreen()) return true;
        String was = page.url();
        boolean back = dash.navigateViaMenu();
        System.out.println("[Pharmacy_Dashboard] " + section + ": dashboard was not on screen (" + was + ") — re-opened via menu: " + back);
        return back && dash.isOnScreen();
    }

    // =================================================================================================================
    // H. Responsive layout
    // =================================================================================================================

    private void responsive() {
        if (!ensureOnDashboard("H. Responsive Layout")) {
            step(page, "H1/H2 Responsive layout", "The responsive checks need the dashboard on screen",
                    "Pharmacy Dashboard visible", "Dashboard not on screen (" + page.url() + ") — responsive checks NOT performed ["
                            + String.join("; ", dash.navNotes) + "]", "FAIL");
            return;
        }
        // H1 desktop
        viewport(1366, 768);
        Map<String, Object> d = dash.headerOverlap();
        List<String> clipped = dash.clippedTitlesAndHeaders();
        List<?> covered = (List<?>) d.getOrDefault("covered", List.of());
        List<?> hidden = (List<?>) d.getOrDefault("hidden", List.of());
        boolean measured = dbl(d.get("navBottom")) > 0 && dash.isOnScreen();   // no vacuous PASS on a page without the header/widgets
        boolean ok = measured && clipped.isEmpty() && covered.isEmpty() && hidden.isEmpty();
        step(fullPage(), "H1 Desktop resolution 1366 × 768", "Resize the viewport to 1366×768 and check titles, headers and the fixed header",
                "All widget titles and table headers visible; nothing hidden behind the fixed header",
                (measured ? "" : "NOT MEASURED — fixed header or widgets not found on " + page.url() + "; ")
                        + "viewport " + d.get("viewport") + ", header bottom " + d.get("navBottom") + "px, content top padding " + d.get("contentPaddingTop") + "px"
                        + (covered.isEmpty() ? "; nothing covered" : "; COVERED: " + covered)
                        + (hidden.isEmpty() ? "" : "; not rendered: " + hidden) + (clipped.isEmpty() ? "" : "; not visible: " + head(clipped, 8)),
                ok ? "PASS" : "FAIL");

        // H2 narrow — the overlap is at the top of the page, so the evidence is the viewport as the user sees it
        viewport(620, 768);
        Map<String, Object> n = dash.headerOverlap();
        List<String> nClipped = dash.clippedTitlesAndHeaders();
        List<?> nCovered = (List<?>) n.getOrDefault("covered", List.of());
        List<?> nHidden = (List<?>) n.getOrDefault("hidden", List.of());
        List<?> jump = (List<?>) n.getOrDefault("coveredOnJump", List.of());
        double navBottom = dbl(n.get("navBottom")), pad = dbl(n.get("contentPaddingTop"));
        dash.scrollTop();
        if (navBottom <= 0 || !dash.isOnScreen()) {
            step(page, "H2 Narrow resolution ~620px", "Resize the viewport to 620×768", "Dashboard measurable at 620px",
                    "NOT MEASURED — fixed header or widgets not found on " + page.url(), "FAIL");
        } else {
            boolean defect = !nCovered.isEmpty() || !nHidden.isEmpty() || navBottom > pad + 1;
            defect(2, "Dashboard widgets are hidden behind the fixed header at narrow widths", "H2 Narrow resolution ~620px",
                    "Resize the viewport to 620×768 and check what the fixed header covers (screenshot = visible viewport)",
                    "Fixed header no taller than the content's top offset; every title and table header visible",
                    "viewport " + n.get("viewport") + ", header bottom " + Math.round(navBottom) + "px vs content top padding " + Math.round(pad) + "px"
                            + (nCovered.isEmpty() ? "; nothing covered at top" : "; COVERED: " + nCovered)
                            + (nHidden.isEmpty() ? "" : "; not rendered: " + nHidden)
                            + (nClipped.isEmpty() ? "" : "; not visible: " + head(nClipped, 6))
                            + (jump.isEmpty() ? "" : "; titles hidden when a widget is scrolled to the top: " + jump.size()), defect);
        }
        viewport(SHOT_W, SHOT_H);
    }

    private void viewport(int w, int h) {
        page.setViewportSize(w, h);
        try { page.waitForFunction("w => window.innerWidth === w", w, new Page.WaitForFunctionOptions().setTimeout(5000)); }
        catch (Exception ignore) { }
        dash.scrollTop();
    }

    // =================================================================================================================
    // DEFECT-03 direct URL after login
    // =================================================================================================================

    private void directUrlAfterLogin() {
        if (browser == null) { step("DEFECT-03 check: direct dashboard URL after login", "Needs its own browser context", "", "No browser handle", "SKIPPED"); return; }
        BrowserContext fresh = browser.newContext(contextOptions());
        try {
            Page p = fresh.newPage();
            p.setViewportSize(SHOT_W, SHOT_H);
            p.navigate(BASE + "/" + PharmacyDashboard.ROUTE);
            try { p.locator("input[placeholder='Login Name']").waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(15000)); }
            catch (Exception e) {
                step(p, "DEFECT-03 check: direct dashboard URL after login", "Open " + PharmacyDashboard.ROUTE + " in a fresh session",
                        "Login screen", "No login screen appeared (" + p.url() + ")", "SKIPPED");
                return;
            }
            String loginUrl = p.url().replaceAll("[?].*?(#|$)", "?…$1");
            // same field selectors DevHisBase.login() uses; values never logged
            p.locator("input[placeholder='Login Name']").fill(USER);
            p.locator("input[type='password']").fill(PASS);
            p.locator("input[type='password']").blur();
            try {
                p.waitForFunction("() => { const l=document.querySelector('#LocationID, select[ng-model=\"LocationID\"]'); return !l || !!l.value; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(10000));
            } catch (Exception ignore) { }
            p.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login")).click();
            try { p.waitForURL(u -> !u.toLowerCase().contains("/account"), new Page.WaitForURLOptions().setTimeout(20000)); }
            catch (Exception e) {
                step(p, "DEFECT-03 check: direct dashboard URL after login", "Log in from the direct-URL login screen", "Authenticated",
                        "Still on the login page — check not possible", "SKIPPED");
                return;
            }
            try {
                p.waitForFunction("() => { const h=document.getElementById('mainHeader'); return h && h.textContent.trim().length>0; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { }
            String landed = p.url();
            String heading = String.valueOf(p.evaluate("() => { const h=document.getElementById('mainHeader'); return h?h.textContent.replace(/\\s+/g,' ').trim():''; }"));
            boolean redirected = !landed.toLowerCase().contains(PharmacyDashboard.ROUTE.toLowerCase());
            defectOn(p, 3, "Direct dashboard URL redirects to the Landing Page after login", "Direct dashboard URL after login",
                    "In a fresh session open " + BASE + "/" + PharmacyDashboard.ROUTE + " (login screen: " + loginUrl + "), then log in",
                    "After login the user lands on the requested Pharmacy Dashboard",
                    "Landed on " + landed + " (heading \"" + heading + "\")", redirected);
        } finally {
            try { fresh.close(); } catch (Exception ignore) { }
        }
    }

    // =================================================================================================================
    // Console / API health
    // =================================================================================================================

    private void consoleAndApiHealth() {
        dash.scrollTop();
        // Only what happened while the dashboard itself was showing is held against it; errors raised on other pages
        // (landing page after login, login screen) are listed for information.
        List<String> onDash = new ArrayList<>(), elsewhere = new ArrayList<>();
        for (String c : consoleErrors) (c.toLowerCase().startsWith("[#/pharmacydashboard") ? onDash : elsewhere).add(c);
        List<String> resOnDash = new ArrayList<>();
        for (String r : resourceFailures) if (r.toLowerCase().startsWith("[#/pharmacydashboard")) resOnDash.add(r);
        check("Console errors on the dashboard", "Browser console errors / uncaught page errors while #/pharmacyDashboard was showing",
                "No console errors on the dashboard",
                (onDash.isEmpty() ? "None on the dashboard" : onDash.size() + ": " + head(onDash, 8))
                        + (resOnDash.isEmpty() ? "" : " — failed resources: " + head(resOnDash, 8))
                        + (elsewhere.isEmpty() ? "" : " — (info) on other pages: " + head(elsewhere, 5)),
                onDash.isEmpty());
        check("API failures", "Failed (HTTP ≥ 400 / network) /api/ calls while the dashboard was under test",
                "No failed API calls", apiFailures.isEmpty() ? "None" : apiFailures.size() + ": " + head(apiFailures, 10), apiFailures.isEmpty());
        check("Read-only guard", "Write calls (non-GET, non-read /api/ requests) stopped by the guard",
                "The dashboard issues no write call", blockedWrites.isEmpty() ? "None attempted" : blockedWrites.size() + " blocked: " + head(blockedWrites, 10),
                blockedWrites.isEmpty());
    }

    // =================================================================================================================
    // shared helpers
    // =================================================================================================================

    private interface Section { void run(); }

    /** Run one scenario group; an unexpected error is recorded as a FAIL step and the next group still runs. */
    private void section(String name, Section s) {
        System.out.println("[Pharmacy_Dashboard] --- " + name + " ---");
        try { s.run(); }
        catch (Throwable e) {
            String m = String.valueOf(e.getMessage()).split("\\R")[0];
            step(page, name + " — unexpected error", "Run the " + name + " checks", "Checks complete", "ERROR: " + m, "FAIL");
        }
    }

    /** Visible + title + headers + row/cell alignment; returns the data rows (empty when there is no data). */
    private List<List<String>> structure(Widget w, List<String> expected, String id) {
        List<String> headers = dash.headers(w);
        List<List<String>> raw = dash.rows(w);
        List<List<String>> rows = dataRows(w, headers.size());
        int bad = 0;
        for (List<String> r : raw) if (r.size() != headers.size()) bad++;
        boolean visible = dash.isDisplayed(w), headersOk = headers.equals(expected);
        dash.scrollTo(w);
        step(id + " Verify " + w.title + " visibility, headers and data", "Read the widget's title, header row and table body",
                "Widget visible; headers " + expected + "; every row aligned to the headers",
                (visible ? "Visible" : "NOT visible") + " — \"" + dash.title(w) + "\"; headers " + headers + "; " + rows.size() + " data row(s)"
                        + (bad == 0 ? "" : "; " + bad + " misaligned row(s)") + (rows.isEmpty() ? "" : "; first: " + pairUp(headers, rows.get(0))),
                visible && headersOk && bad == 0 ? "PASS" : "FAIL");
        return rows;
    }

    private List<List<String>> dataRows(Widget w, int headerCount) {
        List<List<String>> out = new ArrayList<>();
        for (List<String> r : dash.rows(w)) if (r.size() >= Math.max(2, headerCount)) out.add(r);
        return out;
    }

    private void skipRest(Widget w, String ids) {
        step(ids + " " + w.title + " data checks", "Data-dependent checks", "Rows to check",
                "The widget shows no rows on this environment today — data checks skipped", "SKIPPED");
    }

    private void check(String name, String desc, String expected, String actual, boolean ok) {
        step(page, name, desc, expected, actual, ok ? "PASS" : "FAIL");
    }

    /** A check that reproduces a known defect: FAIL + listed in the summary when found, PASS when not reproduced. */
    private void defect(int no, String title, String name, String desc, String expected, String actual, boolean found) {
        defectOn(page, no, title, name, desc, expected, actual, found);
    }

    private void defectOn(Page p, int no, String title, String name, String desc, String expected, String actual, boolean found) {
        String tag = String.format("DEFECT-%02d", no);
        if (found) defects.add(tag + " " + title + " — " + actual);
        step(p, (found ? tag + " " : "[" + tag + " check] ") + name, desc, expected,
                (found ? tag + " reproduced: " : tag + " not reproduced: ") + actual, found ? "FAIL" : "PASS");
    }

    private void writeSummary() {
        addSummary("Application URL", BASE + "/" + PharmacyDashboard.ROUTE);
        addSummary("Run date", today.toString());
        long known = defects.stream().filter(x -> x.startsWith("DEFECT-")).count(), fresh = defects.size() - known;
        addSummary("Defects detected", known + " of the 14 known defects reproduced" + (fresh > 0 ? "; " + fresh + " new defect finding(s) (NEW-nn)" : ""));
        for (String d : defects) { int sp = d.indexOf(' '); addSummary(d.substring(0, sp), d.substring(sp + 1)); }
        addSummary("Console errors", consoleErrors.isEmpty() ? "None" : consoleErrors.size() + " — " + head(consoleErrors, 10));
        addSummary("API failures", apiFailures.isEmpty() ? "None" : apiFailures.size() + " — " + head(apiFailures, 10));
        addSummary("Failed page resources (HTTP ≥ 400)", resourceFailures.isEmpty() ? "None" : resourceFailures.size() + " — " + head(resourceFailures, 10));
        addSummary("Write calls blocked (read-only guard)", blockedWrites.isEmpty() ? "None" : head(blockedWrites, 10));
    }

    // ---- monitors / guard ------------------------------------------------------------------------------------------

    private void attachMonitors() {
        page.onConsoleMessage(m -> {
            if (!"error".equals(m.type())) return;
            String where = "";
            try { where = m.location(); } catch (Exception ignore) { }
            consoleErrors.add(route() + " " + trim(m.text(), 200) + (where == null || where.isBlank() ? "" : " @ " + trim(path(where), 120)));
        });
        page.onPageError(e -> consoleErrors.add(route() + " page error: " + trim(String.valueOf(e), 220)));
        page.onResponse(r -> {
            String u = r.url();
            if (u.contains(PharmacyDashboard.API_FETCH)) fetchResponses.add(r);
            if (r.status() >= 400) {
                String line = route() + " " + r.request().method() + " " + path(u) + " → HTTP " + r.status() + " (" + r.request().resourceType() + ")";
                if (u.contains("/api/")) apiFailures.add(line); else resourceFailures.add(line);
            }
        });
        page.onRequestFailed(q -> {
            if (q.url().contains("/api/") && !String.valueOf(q.failure()).contains("ERR_ABORTED"))
                apiFailures.add(route() + " " + q.method() + " " + path(q.url()) + " → " + q.failure());
        });
    }

    /** The app route the page was on when an event happened, e.g. "[#/pharmacyDashboard]". */
    private String route() {
        try { String u = page.url(); int i = u.indexOf('#'); return "[" + (i >= 0 ? u.substring(i) : path(u)) + "]"; } catch (Exception e) { return "[?]"; }
    }

    /** Abort any write-style /api/ call (non-GET whose last segment is not a read verb) while the dashboard is tested. */
    private void installReadOnlyGuard() {
        page.route("**/api/**", route -> {
            String m = route.request().method(), u = route.request().url();
            if (!"GET".equalsIgnoreCase(m) && !"OPTIONS".equalsIgnoreCase(m) && !READ_CALL.matcher(path(u)).find()) {
                blockedWrites.add(m + " " + path(u));
                System.out.println("[Pharmacy_Dashboard] read-only guard blocked " + m + " " + path(u));
                route.abort();
            } else route.resume();
        });
    }

    // ---- small utilities -------------------------------------------------------------------------------------------

    private byte[] fullPage() {
        try { return page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(15000)); }
        catch (Exception e) { try { return page.screenshot(); } catch (Exception ignore) { return null; } }
    }

    private String execFlag(Response r) {
        try {
            Matcher m = Pattern.compile("\"ExecFlag\"\\s*:\\s*\"([^\"]+)\"").matcher(String.valueOf(r.request().postData()));
            return m.find() ? m.group(1) : "?";
        } catch (Exception e) { return "?"; }
    }

    private int jsonArrayLength(String body) {
        Object n = page.evaluate("t => { try { const j=JSON.parse(t); return Array.isArray(j)?j.length:(j&&Array.isArray(j.data)?j.data.length:-1); } catch(e) { return -2; } }", body);
        return n instanceof Number ? ((Number) n).intValue() : -2;
    }

    private static final String[][] DATE_PATTERNS = {
            {"dd-MMM-yy", "^\\d{1,2}-[A-Za-z]{3}-\\d{2}$"}, {"dd-MMM-yyyy", "^\\d{1,2}-[A-Za-z]{3}-\\d{4}$"},
            {"dd/MM/yyyy", "^\\d{1,2}/\\d{1,2}/\\d{4}$"}, {"dd/MM/yy", "^\\d{1,2}/\\d{1,2}/\\d{2}$"},
            {"yyyy-MM-dd", "^\\d{4}-\\d{2}-\\d{2}$"}, {"dd-MM-yyyy", "^\\d{1,2}-\\d{1,2}-\\d{4}$"}};

    /** Parse a dashboard date and record its display format under the given column label. */
    private LocalDate date(String column, String text) {
        LocalDate d = parse(text);
        String f = formatOf(text);
        if (d != null && f != null) dateFormats.computeIfAbsent(column, k -> new LinkedHashSet<>()).add(f);
        return d;
    }

    private static String formatOf(String text) {
        String t = text == null ? "" : text.trim();
        for (String[] p : DATE_PATTERNS) if (t.matches(p[1])) return p[0];
        return null;
    }

    private static LocalDate parse(String text) {
        String t = text == null ? "" : text.trim();
        String f = formatOf(t);
        if (f == null) return null;
        try {
            DateTimeFormatter fmt = new DateTimeFormatterBuilder().parseCaseInsensitive()
                    .appendPattern(f.replace("dd", "d").replace("MM/", "M/").replace("-MM-", "-M-")).toFormatter(Locale.ENGLISH);
            return LocalDate.parse(t, fmt);
        } catch (Exception e) { return null; }
    }

    private String orderOf(List<List<String>> rows, int col) {
        List<LocalDate> ds = new ArrayList<>();
        for (List<String> r : rows) { LocalDate d = parse(r.get(col)); if (d != null) ds.add(d); }
        boolean asc = true, desc = true;
        for (int i = 1; i < ds.size(); i++) { if (ds.get(i).isBefore(ds.get(i - 1))) asc = false; if (ds.get(i).isAfter(ds.get(i - 1))) desc = false; }
        return asc ? "ascending" : desc ? "descending" : "unsorted";
    }

    private static Double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return null; }
    }

    private static String s(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static int intv(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }
    private static double dbl(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0; }
    private static String yn(boolean b) { return b ? "yes" : "no"; }
    private static String trim(String s, int n) { s = s == null ? "" : s.replaceAll("\\s+", " "); return s.length() > n ? s.substring(0, n) + "…" : s; }
    private static String path(String url) { return url.replaceAll("^https?://[^/]+", "").replaceAll("[?#].*$", ""); }
    private static String box(Map<String, Object> m) { return m.get("container") + "(" + m.get("x") + "," + m.get("y") + " w" + m.get("w") + ")"; }

    private static String head(List<String> l, int n) {
        return l.size() <= n ? String.join("; ", l) : String.join("; ", l.subList(0, n)) + "; … (+" + (l.size() - n) + " more)";
    }

    private static String pairUp(List<String> headers, List<String> row) {
        StringBuilder sb = new StringBuilder();
        int n = Math.max(headers.size(), row.size());
        for (int i = 0; i < n; i++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(i < headers.size() ? headers.get(i) : "(no header)").append("=").append(i < row.size() ? "\"" + row.get(i) + "\"" : "—");
        }
        return sb.toString();
    }

    private static String describe(Map<Widget, Map<String, Object>> l) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Widget, Map<String, Object>> e : l.entrySet()) out.add(e.getKey().title + " " + box(e.getValue()));
        return String.join("; ", out);
    }
}
