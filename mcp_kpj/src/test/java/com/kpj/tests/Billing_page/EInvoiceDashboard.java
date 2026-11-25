package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Billing_page.EInvoiceDashboard.Tab;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The page object has the same simple name — it is referenced by its fully-qualified name.

/**
 * Billing &gt; E-Invoice Dashboard &gt; <b>E-Invoice Dashboard</b> — READ-ONLY functional test (EI-01 … EI-24).
 *
 * <p>Covers page loading and defaults, the filter labels, the 31-day date rule, every filter that drives the
 * <b>Transaction Summary</b> table (E-Invoice Status, Consolidated / Individual, transaction type, MRN, Patient Name,
 * Original Invoice No, Payor), whether each value reaches the search request and whether the rows shown match it, the
 * Transaction Summary columns, the three tabs (Bill List / Cancel Bill List / Bill Log List), the Transaction No. details,
 * the Status log, the Re-Submit button state, Print Master Report, search performance and console / network errors.
 * Report: {@code test-output/reports/Billing_EInvoiceDashboard.html}.</p>
 *
 * <h3>Safety</h3>
 * <ul>
 *   <li>The row <b>Re-Submit</b> button (re-sends the e-invoice to BeaconX / LHDN and saves the status) is never clicked —
 *       the page object has no method for it.</li>
 *   <li>A request-level guard is installed BEFORE the screen opens: every non-GET {@code /api/} call whose last path segment is
 *       not a read verb (Fetch…, Get…, Search, Check…) is aborted and recorded (this covers {@code /api/EInvoiceList/IUD}).
 *       Any download is cancelled and recorded. Print Master Report only opens a report tab, which is screenshotted and closed.</li>
 * </ul>
 *
 * <p>Nothing is assumed about the data: the test looks for a period that returns rows (status VALID, Individual) in the last
 * six months, or uses {@code -Dei.from=01/09/2026 -Dei.to=30/09/2026} when given. {@code -Dei.maxSearchMs=10000} sets the
 * performance limit for the median time of a search.</p>
 *
 * <pre>
 *   mvn -o test -Dtest=com.kpj.tests.Billing_page.EInvoiceDashboard -Ddevhis.postrun=off
 * </pre>
 */
public class EInvoiceDashboard extends DevHisBase {

    public EInvoiceDashboard() { super("Billing_EInvoiceDashboard"); }

    public static void main(String[] args) {
        EInvoiceDashboard t = new EInvoiceDashboard();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    // ---- constants ---------------------------------------------------------------------------------------------------

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern API_URL = Pattern.compile("^https?://[^/]+/api/", Pattern.CASE_INSENSITIVE);
    private static final Predicate<String> API_ROUTE = url -> url != null && API_URL.matcher(url).find();
    private static final Pattern READ_CALL = Pattern.compile(
            "/(fetch[a-z]*|get[a-z]*|load|search|list|view|find|check[a-z]*|filldropdown|dropdown)[^/]*$", Pattern.CASE_INSENSITIVE);
    private static final String FETCH_PATH = "/api/einvoicelist/fetch";
    private static final String BILL_FLAG = "GETEINVOICELISTSEARCH", CANCEL_FLAG = "GETEINVOICECANCELLISTSEARCH", LOG_FLAG = "GETEINVOICELOGLISTSEARCH";

    /** E-Invoice Status dropdown text -> value the request must carry. */
    private static final Map<String, String> STATUS = new LinkedHashMap<>();
    static {
        STATUS.put("ERROR", "3"); STATUS.put("INVALID", "5"); STATUS.put("RECEIVED BY MIDDLEWARE", "1");
        STATUS.put("REJECTED", "4"); STATUS.put("SENT TO TAX AUTHORITY", "2"); STATUS.put("VALID", "6");
    }

    // ---- state -------------------------------------------------------------------------------------------------------

    private com.kpj.pages.Billing_page.EInvoiceDashboard ei;
    private final LocalDate today = LocalDate.now();
    private final long maxSearchMs = Long.getLong("ei.maxSearchMs", 10000L);
    private String from = System.getProperty("ei.from", ""), to = System.getProperty("ei.to", "");

    /** One EInvoiceList/Fetch call: request body, start, server time, status. */
    private static final class Call {
        final String body; final long start; volatile long ms = -1; volatile int status = -1;
        Call(String body, long start) { this.body = body; this.start = start; }
    }
    private final List<Call> fetchCalls = new CopyOnWriteArrayList<>();
    private final Map<Request, Call> open = new ConcurrentHashMap<>();
    private final List<String> consoleErrors = new CopyOnWriteArrayList<>();
    private final List<String> apiFailures = new CopyOnWriteArrayList<>();
    private final List<String[]> blockedWrites = new CopyOnWriteArrayList<>();
    private final List<String> downloads = new CopyOnWriteArrayList<>();
    private final List<Long> searchMs = new ArrayList<>();
    private final List<String> defects = new ArrayList<>();
    private final List<String> observations = new ArrayList<>();
    private int dataRows = 0;
    private Map<String, Object> sample = new LinkedHashMap<>();   // one Bill List record used by the field filters

    /** Outcome of one test case. */
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
        meta("Billing - E-Invoice Dashboard", "Billing > E-Invoice Dashboard > E-Invoice Dashboard",
                "<b>Read-only</b> functional test: page load and defaults, filter labels, the 31-day date rule, every filter that drives the "
                        + "Transaction Summary table (status, Consolidated/Individual, transaction type, MRN, Patient Name, Original Invoice No, Payor), "
                        + "the three tabs, Transaction No. details, Status log, Re-Submit state, Print Master Report, performance and errors. "
                        + "Re-Submit is never clicked and every write call is blocked.");

        attachMonitors();
        login();
        step("EI-00 Login", "Log in with the configured test account (values not shown)", "Authenticated; application shell loaded",
                "Logged in to " + BASE, "PASS");
        installReadOnlyGuard();
        ei = new com.kpj.pages.Billing_page.EInvoiceDashboard(page);

        try {
            tc("EI-01 Page Loading", this::pageLoading);
            if (!ei.onRoute()) { addSummary("Result", "FAILED — E-Invoice Dashboard could not be opened; remaining cases not run"); return; }
            tc("EI-02 Default Filter Validation", this::defaults);
            tc("EI-03 Filter Label Validation", this::labels);
            tc("EI-04 Date Range Over 31 Days", this::dateOver31);
            tc("EI-05 Reversed Date Range", this::dateReversed);
            tc("EI-06 Search - Transaction Summary Shows Records", this::findData);
            if (dataRows == 0) observation("EI-06", "No E-Invoice record was found in the last six months; the row-level checks below are reported as MANUAL");
            tc("EI-07 Transaction Summary Columns", this::columns);
            tc("EI-08 E-Invoice Status Filter - Rows Match", this::statusFilter);
            tc("EI-09 Consolidated / Individual Filter - Rows Match", this::consIndFilter);
            tc("EI-10 Transaction Type Filter (Invoice / Credit Note ...)", this::tranTypeFilter);
            tc("EI-11 Payor Type / Payor Filter", this::payorFilter);
            tc("EI-12 Re-Submit Button State", this::resubmitState);
            tc("EI-13 Transaction No. Details", this::details);
            tc("EI-14 Status Log View", this::statusLog);
            tc("EI-15 EInvoice Cancel Bill List Tab", this::cancelTab);
            tc("EI-16 EInvoice Bill Log List Tab", this::logTab);
            tc("EI-17 Print Master Report", this::printMaster);
            tc("EI-18 MRN Search Button", this::mrnFind);
            tc("EI-19 MRN Filter", this::mrnFilter);
            tc("EI-20 Patient Name Filter", this::nameFilter);
            tc("EI-21 Original Invoice No Filter", this::origFilter);
            tc("EI-22 Search After Clearing Original Invoice No", this::origCleared);
            tc("EI-23 Search Performance", this::performance);
            tc("EI-24 Console / Network Errors", this::consoleCheck);
            tc("EI-25 Read-Only Safety", this::safety);
        } finally {
            try { page.unroute(API_ROUTE); } catch (Exception ignore) { }
            writeSummary();
        }
    }

    // =================================================================================================================
    // A. Page loading, defaults, labels, dates
    // =================================================================================================================

    private R pageLoading() {
        R r = new R("Open Billing > E-Invoice Dashboard > E-Invoice Dashboard from the sidebar; check the filters, buttons and tabs; time the load",
                "Route #/EinvoiceList loads with every filter, Search, Print Master Report and the three tabs");
        boolean opened = ei.navigateViaMenu();
        if (!opened) return r.set("Did NOT open — " + String.join("; ", ei.navNotes) + " (" + page.url() + ")", false);
        Map<String, Object> c = ei.controls();
        List<String> missing = new ArrayList<>();
        c.forEach((k, v) -> { if (!Boolean.TRUE.equals(v)) missing.add(k); });
        return r.set("Loaded in " + secs(ei.lastLoadMs) + (missing.isEmpty() ? "; all " + c.size() + " controls visible" : "; NOT visible: " + missing)
                + (ei.navNotes.isEmpty() ? "" : " [" + String.join("; ", ei.navNotes) + "]"), missing.isEmpty());
    }

    private R defaults() {
        R r = new R("Read the default filter values, click Search and compare the request",
                "From/To = today, a Location selected, E-Invoice Status and Consolidated/Individual filled; the request carries these values");
        Map<String, Object> m = ei.model();
        String loc = ei.selectedText(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_LOCATION);
        String st = ei.selectedText(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_STATUS);
        String ci = ei.selectedText(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_CONS_IND);
        String pt = ei.selectedText(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_PAYOR_TYPE);
        String py = ei.selectedText(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_PAYOR);
        Call c = searchAndGet(BILL_FLAG);
        String t = today.format(DMY);
        List<String> bad = new ArrayList<>();
        if (!t.equals(s(m.get("FromDate"))) || !t.equals(s(m.get("ToDate")))) bad.add("dates default " + m.get("FromDate") + " – " + m.get("ToDate"));
        if (c == null) bad.add("no search request captured");
        else {
            if (!t.equals(field(c, "fromdate")) || !t.equals(field(c, "todate"))) bad.add("request dates " + field(c, "fromdate") + " – " + field(c, "todate"));
            if (!s(m.get("einvoicestatus")).equals(field(c, "EinvoiceStatus"))) bad.add("EinvoiceStatus " + raw(c, "EinvoiceStatus"));
            if (!s(m.get("einvoicetype")).equals(field(c, "einvoicetypeid"))) bad.add("einvoicetypeid " + raw(c, "einvoicetypeid"));
        }
        return r.set("Dates " + m.get("FromDate") + " – " + m.get("ToDate") + ", Location \"" + loc + "\", E-Invoice Status \"" + st + "\", Consolidated/Individual \"" + ci
                + "\", Payor Type \"" + pt + "\" (disabled=" + ei.disabled(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_PAYOR_TYPE) + "), Payor \"" + py
                + "\" (disabled=" + ei.disabled(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_PAYOR) + "); " + rowsLine(c)
                + (bad.isEmpty() ? "" : " — MISMATCH: " + String.join("; ", bad)), bad.isEmpty());
    }

    private R labels() {
        R r = new R("Compare each filter's label with the list it offers and with the Transaction Summary column it filters",
                "\"Transaction Type\" offers Invoice / Bill Cancellation / Credit Note … (like the column of that name) and \"E-Invoice Type\" offers Consolidated / Individual");
        Map<String, String> lab = ei.labelOf();
        String tranLabel = lab.getOrDefault(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_TRAN_TYPE, "");
        String ciLabel = lab.getOrDefault(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_CONS_IND, "");
        List<String> tranOpts = ei.options(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_TRAN_TYPE);
        List<String> ciOpts = ei.options(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_CONS_IND);
        boolean ok = tranLabel.equalsIgnoreCase("Transaction Type") && ciLabel.equalsIgnoreCase("E-Invoice Type");
        if (!ok) defect("EI-03", "Medium", "Filter labels are swapped: the box labelled \"" + tranLabel + "\" lists " + tranOpts
                + " and the box labelled \"" + ciLabel + "\" lists " + ciOpts + " — opposite to the Transaction Summary columns \"Transaction Type\" (Consolidated) and \"E-Invoice Type\" (Invoice)");
        ei.scrollTo("select[ng-model='" + com.kpj.pages.Billing_page.EInvoiceDashboard.NG_CONS_IND + "']");
        return r.set("Label \"" + tranLabel + "\" → " + tranOpts + "; label \"" + ciLabel + "\" → " + ciOpts, ok);
    }

    private R dateOver31() {
        R r = new R("Type From = today-40 days, To = today", "Warning \"Date range should not exceed 31 days.\" and the range is not accepted");
        ei.typeDate(SEL_TO(), today.format(DMY));
        ei.spyAlerts();
        ei.typeDate(SEL_FROM(), today.minusDays(40).format(DMY));
        List<String> a = ei.spiedAlerts();
        String f = ei.fromDate();
        boolean warned = a.stream().anyMatch(x -> x.toLowerCase().contains("31 days"));
        boolean ok = warned && !today.minusDays(40).format(DMY).equals(s(ei.model().get("FromDate")));
        return r.set("Alerts: " + (a.isEmpty() ? "none" : a) + "; From Date now \"" + f + "\"", ok);
    }

    private R dateReversed() {
        R r = new R("Type To = today-5 days, then From = today", "A warning is shown and From later than To is not accepted");
        ei.typeDate(SEL_TO(), today.minusDays(5).format(DMY));
        ei.spyAlerts();
        ei.typeDate(SEL_FROM(), today.format(DMY));
        List<String> a = ei.spiedAlerts();
        boolean ok = !a.isEmpty() && !today.format(DMY).equals(s(ei.model().get("FromDate")));
        if (a.isEmpty()) defect("EI-05", "Low", "A reversed date range (From " + today.format(DMY) + " > To " + today.minusDays(5).format(DMY) + ") is accepted without a warning");
        return r.set("Alerts: " + (a.isEmpty() ? "none" : a) + "; From \"" + ei.fromDate() + "\", To \"" + ei.toDate() + "\"", ok);
    }

    // =================================================================================================================
    // B. Search and the Transaction Summary
    // =================================================================================================================

    private R findData() {
        R r = new R("Pick E-Invoice Status = VALID and the Consolidated/Individual box = Individual; search month by month (last six months, max 31 days each) until the Transaction Summary shows records",
                "The Transaction Summary lists the e-invoices of the period; every Transaction Date lies inside From – To");
        String p1 = ei.pick(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_STATUS, "VALID");
        String p2 = ei.pick(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_CONS_IND, "Individual");
        if (!p1.isEmpty() || !p2.isEmpty()) return r.set("Could not set the filters: " + p1 + " " + p2, false);
        List<String[]> windows = new ArrayList<>();
        if (!from.isEmpty() && !to.isEmpty()) windows.add(new String[]{from, to});
        else for (int i = 1; i <= 6; i++) {
            YearMonth ym = YearMonth.from(today).minusMonths(i);
            windows.add(new String[]{ym.atDay(1).format(DMY), ym.atEndOfMonth().format(DMY)});
        }
        List<String> tried = new ArrayList<>();
        Call c = null;
        for (String[] w : windows) {
            setDates(w[0], w[1]);
            c = searchAndGet(BILL_FLAG);
            int n = ei.rows(Tab.BILL);
            tried.add(w[0] + "–" + w[1] + ": " + n);
            if (n > 0) { from = w[0]; to = w[1]; dataRows = n; break; }
        }
        if (dataRows == 0) return r.set("No records in any period tried: " + tried, "MANUAL");
        List<String> outside = new ArrayList<>();
        for (String d : ei.column(Tab.BILL, "Transaction Date")) {
            LocalDate x = parseShown(d);
            if (x == null || x.isBefore(parse(from)) || x.isAfter(parse(to))) outside.add(d);
        }
        sample = ei.data(Tab.BILL, "tranno", "MRNO", "PatientName", "OriginalEinvoiceNo", "EinvoiceType", "TranType", "envoicestatus", "iseinvoicestaging", "opdipd").get(0);
        ei.scrollTo("#einvoice-tab table");
        return r.set("Periods tried: " + tried + "; using " + from + " – " + to + ": " + dataRows + " record(s) in the Transaction Summary; first "
                + sample.get("tranno") + " / MRN " + sample.get("MRNO") + "; " + rowsLine(c)
                + (outside.isEmpty() ? "; all Transaction Dates inside the range" : "; OUTSIDE the range: " + head(outside, 5)), outside.isEmpty());
    }

    private R columns() {
        R r = new R("Read the Transaction Summary column headers of the EInvoice Bill List",
                "Business columns only (Print, Location, Transaction Type, E-Invoice Type, Transaction Date, Transaction No., …, E-Invoice Status, Action, Status log); no developer field names");
        List<String> h = ei.headers(Tab.BILL);
        List<String> dev = new ArrayList<>();
        for (String x : h) if (x.matches("[a-z]+[a-z0-9]*") || x.equals("ID")) dev.add(x);
        List<String> must = List.of("Location", "Transaction Type", "E-Invoice Type", "Transaction Date", "Transaction No.", "MRN", "Patient Name",
                "Payor Type", "Payor Name", "E-Invoice Status", "Action", "Status log");
        List<String> missing = new ArrayList<>();
        for (String m : must) if (!h.contains(m)) missing.add(m);
        if (!dev.isEmpty()) defect("EI-07", "Low", "Transaction Summary shows developer column names " + dev + " as headers");
        return r.set(h.size() + " columns: " + h + (missing.isEmpty() ? "" : "; MISSING " + missing) + (dev.isEmpty() ? "" : "; developer names shown: " + dev),
                missing.isEmpty() && dev.isEmpty());
    }

    private R statusFilter() {
        R r = new R("With the found period, pick each E-Invoice Status (VALID, INVALID, REJECTED, ERROR) and Search; compare the value sent and the E-Invoice Status column of the rows",
                "The request carries the chosen status and every row's E-Invoice Status matches it (VALID → Valid, INVALID → Invalid, REJECTED → Rejected, ERROR → Error)");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        List<String> lines = new ArrayList<>(), bad = new ArrayList<>();
        for (String st : List.of("VALID", "INVALID", "REJECTED", "ERROR")) {
            String p = ei.pick(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_STATUS, st);
            if (!p.isEmpty()) { bad.add(st + ": " + p); continue; }
            Call c = searchAndGet(BILL_FLAG);
            Set<String> shown = new TreeSet<>(ei.column(Tab.BILL, "E-Invoice Status"));
            String sent = c == null ? "—" : field(c, "EinvoiceStatus");
            lines.add(st + " (sent " + sent + "): " + ei.rows(Tab.BILL) + " row(s) " + shown);
            if (c == null || !STATUS.get(st).equals(sent)) bad.add(st + " sent as " + sent);
            for (String x : shown) {
                String lx = x.toLowerCase();
                boolean match = lx.startsWith(st.toLowerCase()) || (st.equals("VALID") && lx.startsWith("valid"));
                if (st.equals("VALID") && lx.startsWith("invalid")) match = false;
                if (!match) bad.add(st + " shows \"" + x + "\"");
            }
        }
        ei.pick(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_STATUS, "VALID");
        searchAndGet(BILL_FLAG);
        if (!bad.isEmpty()) defect("EI-08", "High", "E-Invoice Status filter and the rows disagree: " + head(bad, 4));
        return r.set(String.join(" | ", lines) + (bad.isEmpty() ? "" : " — MISMATCH: " + head(bad, 6)), bad.isEmpty());
    }

    private R consIndFilter() {
        R r = new R("Pick Consolidated, Search; pick Individual, Search (the box labelled \"Transaction Type\"); compare with the Transaction Type column",
                "Consolidated lists Consolidated rows, Individual lists Individual rows");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        List<String> lines = new ArrayList<>(), bad = new ArrayList<>();
        for (String v : List.of("Consolidated", "Individual")) {
            String p = ei.pick(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_CONS_IND, v);
            if (!p.isEmpty()) { bad.add(v + ": " + p); continue; }
            Call c = searchAndGet(BILL_FLAG);
            Set<String> shown = new TreeSet<>(ei.column(Tab.BILL, "Transaction Type"));
            lines.add(v + " (sent einvoicetypeid " + (c == null ? "—" : field(c, "einvoicetypeid")) + "): " + ei.rows(Tab.BILL) + " row(s) " + shown);
            for (String x : shown) if (!x.equalsIgnoreCase(v)) bad.add(v + " shows \"" + x + "\"");
        }
        ei.pick(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_CONS_IND, "Individual");
        searchAndGet(BILL_FLAG);
        if (!bad.isEmpty()) defect("EI-09", "High", "Consolidated / Individual filter and the rows disagree: " + head(bad, 4));
        return r.set(String.join(" | ", lines) + (bad.isEmpty() ? "" : " — MISMATCH: " + head(bad, 6)), bad.isEmpty());
    }

    private R tranTypeFilter() {
        R r = new R("Pick Invoice, then Credit Note, then --All-- in the box labelled \"E-Invoice Type\" and Search; compare with the E-Invoice Type column",
                "Each value reaches the request; Invoice lists only Invoice rows, Credit Note only Credit Note rows; --All-- lists both");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        List<String> lines = new ArrayList<>(), bad = new ArrayList<>();
        Map<String, String> ids = Map.of("Invoice", "1", "Credit Note", "9", "--All--", "");
        for (String v : List.of("Invoice", "Credit Note", "--All--")) {
            String p = ei.pick(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_TRAN_TYPE, v);
            // "--All--" is the empty option: its model value is "" by design, which Select2Pick reports as "not held"
            if (!p.isEmpty() && !(v.equals("--All--") && p.contains("model does not hold"))) { bad.add(v + ": " + p); continue; }
            Call c = searchAndGet(BILL_FLAG);
            Set<String> shown = new TreeSet<>(ei.column(Tab.BILL, "E-Invoice Type"));
            String sent = c == null ? "—" : field(c, "TransactionType");
            lines.add(v + " (sent " + (sent.isEmpty() ? "none" : sent) + "): " + ei.rows(Tab.BILL) + " row(s) " + shown);
            if (c == null || !ids.get(v).equals(sent)) bad.add(v + " sent as \"" + sent + "\"");
            if (!v.equals("--All--")) for (String x : shown) if (!x.equalsIgnoreCase(v)) bad.add(v + " shows \"" + x + "\"");
        }
        return r.set(String.join(" | ", lines) + (bad.isEmpty() ? "" : " — MISMATCH: " + head(bad, 6)), bad.isEmpty());
    }

    private R mrnFind() {
        R r = new R("Type the MRN of the first record and click the magnifier beside MRN", "The patient banner loads for that MRN (OP or IP patient)");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        String mrn = s(sample.get("MRNO"));
        String line = ei.findPatient(mrn);
        boolean ok = line.contains(mrn);
        if (!ok) defect("EI-18", "Medium", "The MRN search button finds no patient for MRN " + mrn + " (" + sample.get("opdipd") + " patient with e-invoices on this list) — "
                + "the screen's GetPatientInfo call omits opd_ipd, so only outpatients are found");
        return r.set("MRN " + mrn + " (" + sample.get("opdipd") + ") → \"" + trim(line, 200) + "\"", ok);
    }

    private R mrnFilter() {
        R r = new R("Search with the MRN of the first record, then clear the MRN box", "The request carries the MRN and the Transaction Summary lists only that patient's e-invoices");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        ensureData();
        String mrn = s(sample.get("MRNO"));
        ei.typeText(com.kpj.pages.Billing_page.EInvoiceDashboard.SEL_MRN, mrn);
        Call c = searchAndGet(BILL_FLAG);
        int n = ei.rows(Tab.BILL);
        Set<String> shown = new TreeSet<>(ei.column(Tab.BILL, "MRN"));
        boolean ok = c != null && mrn.equals(field(c, "MRNo")) && n > 0 && shown.equals(Set.of(mrn));
        ei.typeText(com.kpj.pages.Billing_page.EInvoiceDashboard.SEL_MRN, "");
        return r.set("Sent MRNo \"" + (c == null ? "—" : field(c, "MRNo")) + "\"; " + n + " row(s), MRN column " + shown, ok);
    }

    private R nameFilter() {
        R r = new R("Search with the patient name of the first record, then with \"zzzz-no-such\", then clear the box", "Rows of that patient only; the made-up name returns no rows");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        ensureData();
        String name = s(sample.get("PatientName"));
        ei.typeText(com.kpj.pages.Billing_page.EInvoiceDashboard.SEL_NAME, name);
        Call c = searchAndGet(BILL_FLAG);
        int n = ei.rows(Tab.BILL);
        Set<String> shown = new TreeSet<>(ei.column(Tab.BILL, "Patient Name"));
        ei.typeText(com.kpj.pages.Billing_page.EInvoiceDashboard.SEL_NAME, "zzzz-no-such");
        searchAndGet(BILL_FLAG);
        int none = ei.rows(Tab.BILL);
        ei.typeText(com.kpj.pages.Billing_page.EInvoiceDashboard.SEL_NAME, "");
        boolean ok = c != null && n > 0 && shown.stream().allMatch(x -> x.equalsIgnoreCase(name)) && none == 0;
        return r.set("\"" + name + "\" (sent \"" + (c == null ? "—" : field(c, "PatientName")) + "\"): " + n + " row(s) " + shown + "; \"zzzz-no-such\": " + none + " row(s)", ok);
    }

    private int origRows = -1;

    private R origFilter() {
        R r = new R("Search with the Original Invoice No of the first record", "Only that e-invoice is listed");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        ensureData();
        origRows = ei.rows(Tab.BILL);
        String o = s(sample.get("OriginalEinvoiceNo"));
        ei.typeText(com.kpj.pages.Billing_page.EInvoiceDashboard.SEL_ORIG, o);
        Call c = searchAndGet(BILL_FLAG);
        Set<String> shown = new TreeSet<>(ei.column(Tab.BILL, "Original Invoice No"));
        int n = ei.rows(Tab.BILL);
        boolean ok = c != null && n > 0 && shown.equals(Set.of(o));
        return r.set("Sent \"" + (c == null ? "—" : field(c, "OriginalEinvoiceNo")) + "\": " + n + " row(s), Original Invoice No column " + shown, ok);
    }

    private R origCleared() {
        R r = new R("Delete the text in Original Invoice No (box now empty) and click Search again",
                "The list returns to all records of the period (" + (origRows < 0 ? "?" : origRows) + " before the Original Invoice No search)");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        if (origRows <= 0) return r.set("No baseline row count from EI-21 (" + origRows + ")", "MANUAL");
        ei.typeText(com.kpj.pages.Billing_page.EInvoiceDashboard.SEL_ORIG, "");
        Call c = searchAndGet(BILL_FLAG);
        int n = ei.rows(Tab.BILL);
        boolean ok = n == origRows;
        if (!ok) defect("EI-22", "High", "After the Original Invoice No box is typed in and then emptied, every search returns " + n + " record(s) instead of " + origRows
                + " — the empty box is sent as OriginalEinvoiceNo:" + raw(c, "OriginalEinvoiceNo") + " and the server filters on it");
        String res = "Box empty, sent OriginalEinvoiceNo " + (c == null ? "—" : raw(c, "OriginalEinvoiceNo")) + ": " + n + " row(s) (expected " + origRows + ")";
        if (!ok) { reopen(); res += "; after reopening the screen: " + ei.rows(Tab.BILL) + " row(s)"; }
        return r.set(res, ok);
    }

    private R payorFilter() {
        R r = new R("Read Payor Type / Payor, Search, compare with the Payor Name column of the rows",
                "Either the Payor boxes can be changed, or the rows listed belong to the Payor the screen shows");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        String py = ei.selectedText(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_PAYOR);
        boolean dis = ei.disabled(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_PAYOR) && ei.disabled(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_PAYOR_TYPE);
        Call c = searchAndGet(BILL_FLAG);
        int n = ei.rows(Tab.BILL);
        Set<String> shown = new TreeSet<>(ei.column(Tab.BILL, "Payor Name"));
        if (n == 0) return r.set("No rows to compare (Payor \"" + py + "\", disabled=" + dis + ")", "MANUAL");
        boolean ok = !dis || shown.stream().allMatch(x -> x.equalsIgnoreCase(py));
        if (!ok) defect("EI-11", "Medium", "Payor Type / Payor are disabled and show \"" + py + "\", but the Transaction Summary lists other payors " + shown
                + " — the Payor sent (" + raw(c, "Payer") + ") is not applied and cannot be changed");
        ei.scrollTo("select[ng-model='" + com.kpj.pages.Billing_page.EInvoiceDashboard.NG_PAYOR + "']");
        return r.set("Payor \"" + py + "\" (disabled=" + dis + ", sent Payer " + raw(c, "Payer") + "); " + n + " row(s), Payor Name column " + shown, ok);
    }

    private R resubmitState() {
        R r = new R("Read the Re-Submit button of every Bill List row (NOT clicked)",
                "Re-Submit is disabled while the e-invoice is being processed (staging 1–4) and enabled otherwise");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        if (ei.rows(Tab.BILL) == 0) searchAndGet(BILL_FLAG);
        List<Map<String, Object>> st = ei.resubmitStates(Tab.BILL);
        List<String> bad = new ArrayList<>();
        Map<String, Integer> tally = new TreeMap<>();
        for (Map<String, Object> m : st) {
            int stg = intv(m.get("staging"));
            boolean en = Boolean.TRUE.equals(m.get("enabled"));
            tally.merge(m.get("status") + " / " + (en ? "enabled" : "disabled"), 1, Integer::sum);
            if (en == (stg >= 1 && stg <= 4)) bad.add(m.get("tranno") + " staging " + stg + " enabled=" + en);
        }
        ei.scrollTo("#einvoice-tab table tbody button");
        return r.set(st.size() + " button(s): " + tally + (bad.isEmpty() ? "" : "; WRONG: " + head(bad, 5)) + "; none clicked", bad.isEmpty() && !st.isEmpty());
    }

    // =================================================================================================================
    // C. Tabs, print, details, status log
    // =================================================================================================================

    private R cancelTab() {
        R r = new R("Click the EInvoice Cancel Bill List tab", "The tab opens and requests the cancel list (" + CANCEL_FLAG + ") for the same filters");
        int n0 = fetchCalls.size();
        ei.openTab(Tab.CANCEL);
        List<String> flags = flagsSince(n0);
        boolean ok = Tab.CANCEL.label.equals(ei.activeTab()) && flags.contains(CANCEL_FLAG);
        return r.set("Active tab \"" + ei.activeTab() + "\"; calls " + flags + "; " + ei.rows(Tab.CANCEL) + " cancelled record(s); columns " + head(ei.headers(Tab.CANCEL), 8), ok);
    }

    private R logTab() {
        R r = new R("Click the EInvoice Bill Log List tab", "The tab opens and requests the log list (" + LOG_FLAG + ")");
        int n0 = fetchCalls.size();
        ei.openTab(Tab.LOG);
        List<String> flags = flagsSince(n0);
        boolean ok = Tab.LOG.label.equals(ei.activeTab()) && flags.contains(LOG_FLAG);
        if (!flags.contains(LOG_FLAG)) defect("EI-16", "Medium", "The EInvoice Bill Log List tab requests " + flags + " (the cancel list) instead of " + LOG_FLAG
                + " — the log list only refreshes from the Search button");
        String res = "Active tab \"" + ei.activeTab() + "\"; calls " + flags + "; " + ei.rows(Tab.LOG) + " log record(s)";
        ei.openTab(Tab.BILL);
        return r.set(res, ok);
    }

    private R printMaster() {
        R r = new R("Click Print Master Report (no MRN / Patient Name typed)", "A report tab opens with the current filters; empty filters are not sent as the word \"undefined\"");
        ensureData();
        Page rep;
        try {
            rep = page.context().waitForPage(new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(20000), () -> ei.clickPrintMasterReport());
        } catch (Exception e) { return r.set("No report tab opened: " + String.valueOf(e.getMessage()).split("\\R")[0], false); }
        try { rep.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(30000)); } catch (Exception ignore) { }
        String url = rep.url();
        List<String> bad = new ArrayList<>();
        if (url.contains("=undefined")) bad.add("empty filters sent as \"undefined\"");
        if (url.contains("=null")) bad.add("an empty filter sent as the word \"null\"");
        if (!url.contains("FromDate=" + from) || !url.contains("ToDate=" + to)) bad.add("dates not the current filter");
        if (!url.toLowerCase().contains("payer")) bad.add("Payor Type / Payor not passed");
        if (url.contains("=null") || !url.toLowerCase().contains("payer"))
            defect("EI-17", "Low", "Print Master Report URL " + (url.contains("=null") ? "sends --All-- as the word \"null\" (TransactionType=null)" : "")
                    + (!url.toLowerCase().contains("payer") ? (url.contains("=null") ? " and " : "") + "does not pass Payor Type / Payor" : ""));
        String title = "";
        try { title = rep.title(); } catch (Exception ignore) { }
        step(rep, "EI-17a Print Master Report tab", "Screenshot of the report tab", "The e-invoice master report renders",
                "Title \"" + title + "\"; URL " + trim(path(url) + url.replaceAll("^[^?]*", ""), 300), bad.contains("dates not the current filter") ? "FAIL" : "PASS");
        try { rep.close(); } catch (Exception ignore) { }
        if (url.contains("=undefined")) defect("EI-17", "Low", "Print Master Report sends empty MRN / Patient Name as \"undefined\" (" + trim(url.replaceAll("^[^?]*", ""), 200) + ")");
        return r.set("Report URL " + trim(url.replaceAll("^[^?]*", ""), 260) + (bad.isEmpty() ? "" : " — " + bad), bad.isEmpty());
    }

    private R details() {
        R r = new R("Click the Transaction No. link of the first Bill List row", "The transaction details panel opens below on the same screen");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        String tn = ei.clickFirstTransactionNo();
        String url = page.url();
        Map<String, Object> d = ei.detailsState();
        boolean ok = url.contains(com.kpj.pages.Billing_page.EInvoiceDashboard.ROUTE) && Boolean.TRUE.equals(d.get("shown"));
        if (!url.contains(com.kpj.pages.Billing_page.EInvoiceDashboard.ROUTE))
            defect("EI-13", "High", "Clicking Transaction No. " + tn + " leaves the screen for " + route() + " (the link is href=\"#\"), so the transaction details are never shown");
        String res = "Clicked " + tn + " → now on " + route() + "; details " + d;
        step(page, "EI-13a After Transaction No. click", "Screenshot right after the click", "E-Invoice Dashboard with the details panel", res, ok ? "PASS" : "FAIL");
        if (!ei.onRoute()) reopen();
        return r.set(res, ok);
    }

    private R statusLog() {
        R r = new R("Click the Status log \"View\" link of the first Bill List row", "The Status log popup opens with the BeaconX / LHDN log of that transaction");
        if (dataRows == 0) return r.set("No data period found (EI-06)", "MANUAL");
        String tn = ei.clickFirstStatusLog();
        String url = page.url();
        Map<String, Object> st = ei.statusLogState();
        boolean ok = url.contains(com.kpj.pages.Billing_page.EInvoiceDashboard.ROUTE) && Boolean.TRUE.equals(st.get("modal"));
        if (!url.contains(com.kpj.pages.Billing_page.EInvoiceDashboard.ROUTE))
            defect("EI-14", "Medium", "Clicking Status log View for " + tn + " leaves the screen for " + route() + " (the link is href=\"#\")");
        String res = "View for " + tn + " → now on " + route() + "; popup " + st;
        step(page, "EI-14a After Status log View click", "Screenshot right after the click", "Status log popup over the E-Invoice Dashboard", res, ok ? "PASS" : "FAIL");
        ei.closeStatusLog();
        if (!ei.onRoute()) reopen();
        return r.set(res, ok);
    }

    /** Open the screen again and restore the found period after a link navigated away. */
    private void reopen() {
        // Leave the screen first (a hash change, not a reload) so its controller starts again with clean filters.
        if (ei.onRoute()) {
            page.evaluate("() => { location.hash = '#/PatientDashboard'; }");
            try { page.waitForFunction("() => !/EinvoiceList/i.test(location.href)", null, new Page.WaitForFunctionOptions().setTimeout(10000)); } catch (Exception ignore) { }
            page.waitForTimeout(1500);
        }
        ei.navigateViaMenu();
        if (dataRows > 0) {
            page.waitForTimeout(1500);   // the screen re-applies its own defaults (status ERROR) right after it loads
            pickSure(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_STATUS, "VALID");
            pickSure(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_CONS_IND, "Individual");
            setDates(from, to);
            searchAndGet(BILL_FLAG);
            System.out.println("[EInvoiceDashboard] reopened: status " + ei.selectedText(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_STATUS)
                    + ", " + ei.selectedText(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_CONS_IND) + ", " + ei.rows(Tab.BILL) + " row(s)");
        }
    }

    /** Pick a dropdown value and confirm the box holds it (up to three tries). */
    private void pickSure(String ng, String text) {
        for (int i = 0; i < 3; i++) {
            ei.pick(ng, text);
            if (text.equals(ei.selectedText(ng))) return;
            page.waitForTimeout(1000);
        }
    }

    /** Before a row-level case: make sure the found period with VALID / Individual is on screen with rows. */
    private void ensureData() {
        if (dataRows == 0) return;
        if (!"VALID".equals(ei.selectedText(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_STATUS))
                || !"Individual".equals(ei.selectedText(com.kpj.pages.Billing_page.EInvoiceDashboard.NG_CONS_IND))
                || ei.rows(Tab.BILL) == 0) reopen();
    }

    // =================================================================================================================
    // D. Performance, errors, safety
    // =================================================================================================================

    private R performance() {
        R r = new R("Median and maximum time of the Search clicks made in this run", "Median search time ≤ " + secs(maxSearchMs));
        if (searchMs.isEmpty()) return r.set("No search timed", "MANUAL");
        List<Long> s = new ArrayList<>(searchMs);
        Collections.sort(s);
        long med = s.get(s.size() / 2), max = s.get(s.size() - 1);
        if (med > 5000) observation("EI-23", "Search is slow: median " + secs(med) + ", max " + secs(max) + " over " + s.size() + " searches (one month, one status)");
        return r.set(s.size() + " searches: median " + secs(med) + ", max " + secs(max), med <= maxSearchMs);
    }

    private R consoleCheck() {
        R r = new R("Console errors, page errors and failed API calls raised while the E-Invoice Dashboard was open", "No console error and no failed API call on this screen");
        List<String> mine = new ArrayList<>(), other = new ArrayList<>();
        for (String e : consoleErrors) (e.contains("EinvoiceList") ? mine : other).add(e);
        if (!other.isEmpty()) observation("EI-24", other.size() + " console error(s) on other screens visited (not this screen): " + head(other, 3));
        return r.set("This screen: " + (mine.isEmpty() ? "none" : mine.size() + " — " + head(mine, 4))
                + "; other screens: " + (other.isEmpty() ? "none" : other.size())
                + "; API failures: " + (apiFailures.isEmpty() ? "none" : apiFailures.size() + " — " + head(apiFailures, 4)), mine.isEmpty() && apiFailures.isEmpty());
    }

    private R safety() {
        R r = new R("Check that nothing was written, submitted or downloaded", "No write call reached the server; Re-Submit never clicked; no download");
        List<String> bw = new ArrayList<>();
        for (String[] b : blockedWrites) bw.add(b[0] + " " + b[1] + " " + trim(b[2], 100));
        return r.set("Write calls blocked: " + (bw.isEmpty() ? "none" : head(bw, 5)) + "; downloads: " + (downloads.isEmpty() ? "none" : downloads)
                + "; \"" + com.kpj.pages.Billing_page.EInvoiceDashboard.FORBIDDEN_BUTTON + "\" never clicked", downloads.isEmpty());
    }

    // =================================================================================================================
    // helpers
    // =================================================================================================================

    private void tc(String name, Body b) {
        System.out.println("[EInvoiceDashboard] --- " + name + " ---");
        long t0 = System.currentTimeMillis();
        R r;
        try { r = b.run(); }
        catch (Throwable e) { r = new R("Run " + name, "Case completes").set("ERROR: " + String.valueOf(e.getMessage()).split("\\R")[0], false); }
        step(page, name, r.desc, r.expected, r.actual + " [execution time " + secs(System.currentTimeMillis() - t0) + "]", r.status);
    }

    private void setDates(String f, String t) {
        // From first, then To: each change runs the 31-day check against the other box
        ei.typeDate(SEL_FROM(), f);
        ei.typeDate(SEL_TO(), t);
        if (!f.equals(s(ei.model().get("FromDate")))) ei.typeDate(SEL_FROM(), f);
    }

    /** Click Search; return the EInvoiceList/Fetch call with the given ExecFlag it made (null when none). */
    private Call searchAndGet(String flag) {
        int n0 = fetchCalls.size();
        searchMs.add(ei.search());
        Call found = null;
        for (int i = n0; i < fetchCalls.size(); i++) if (flag.equals(field(fetchCalls.get(i), "ExecFlag"))) found = fetchCalls.get(i);
        for (int i = 0; found != null && i < 20 && found.ms < 0; i++) page.waitForTimeout(250);
        return found;
    }

    private List<String> flagsSince(int n0) {
        List<String> out = new ArrayList<>();
        for (int i = n0; i < fetchCalls.size(); i++) out.add(field(fetchCalls.get(i), "ExecFlag"));
        return out;
    }

    private String rowsLine(Call c) {
        return ei.rows(Tab.BILL) + " row(s) in the Transaction Summary" + (c == null ? "" : ", server " + secs(c.ms) + ", HTTP " + c.status);
    }

    private static String raw(Call c, String key) {
        if (c == null) return "";
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(null|true|false|-?\\d+(?:\\.\\d+)?|\"(?:[^\"\\\\]|\\\\.)*\")").matcher(c.body);
        return m.find() ? m.group(1) : "";
    }

    private static String field(Call c, String key) {
        String v = raw(c, key);
        if (v.equals("null")) return "";
        return v.startsWith("\"") ? v.substring(1, v.length() - 1) : v;
    }

    private void attachMonitors() {
        page.onConsoleMessage(m -> {
            if (!"error".equals(m.type())) return;
            String txt = m.text();
            if (txt.contains("net::ERR_FAILED") && !blockedWrites.isEmpty()) return;
            consoleErrors.add(route() + " " + trim(txt, 220));
        });
        page.onPageError(e -> consoleErrors.add(route() + " page error: " + trim(String.valueOf(e), 220)));
        page.onRequest(q -> {
            if (!API_URL.matcher(q.url()).find()) return;
            if (path(q.url()).toLowerCase().equals(FETCH_PATH)) {
                Call c = new Call(String.valueOf(q.postData()), System.currentTimeMillis());
                open.put(q, c);
                fetchCalls.add(c);
            }
        });
        page.onResponse(resp -> {
            Request q = resp.request();
            if (!API_URL.matcher(resp.url()).find()) return;
            Call c = open.remove(q);
            if (c != null) { c.status = resp.status(); c.ms = System.currentTimeMillis() - c.start; }
            if (resp.status() >= 400) apiFailures.add(route() + " " + q.method() + " " + path(resp.url()) + " → HTTP " + resp.status());
        });
        page.onRequestFailed(q -> {
            if (!API_URL.matcher(q.url()).find()) return;
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
                blockedWrites.add(new String[]{m, path(u), trim(String.valueOf(route.request().postData()), 300)});
                System.out.println("[EInvoiceDashboard] read-only guard blocked " + m + " " + path(u));
                route.abort();
            } else route.resume();
        });
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
        addSummary("Application URL", BASE + "/" + com.kpj.pages.Billing_page.EInvoiceDashboard.ROUTE);
        addSummary("Run date", today.toString());
        addSummary("Data period used", dataRows == 0 ? "none found" : from + " – " + to + " (E-Invoice Status VALID, Individual): " + dataRows + " record(s)");
        addSummary("Application defects found", defects.isEmpty() ? "None" : String.valueOf(defects.size()));
        int i = 1;
        for (String d : defects) addSummary("Defect " + (i++), d);
        i = 1;
        for (String o : observations) addSummary("Observation " + (i++), o);
        addSummary("Search calls", fetchCalls.size() + " × POST /api/EInvoiceList/Fetch");
        addSummary("Console errors", consoleErrors.isEmpty() ? "None" : consoleErrors.size() + " — " + head(consoleErrors, 4));
        addSummary("API failures", apiFailures.isEmpty() ? "None" : apiFailures.size() + " — " + head(apiFailures, 4));
        List<String> bw = new ArrayList<>();
        for (String[] b : blockedWrites) bw.add(b[0] + " " + b[1]);
        addSummary("Write calls blocked (read-only guard)", bw.isEmpty() ? "None" : head(bw, 6));
        addSummary("Data modified", "No — Re-Submit never clicked, " + blockedWrites.size() + " write-type call(s) aborted, " + downloads.size() + " download(s) cancelled");
    }

    private static String SEL_FROM() { return com.kpj.pages.Billing_page.EInvoiceDashboard.SEL_FROM; }
    private static String SEL_TO() { return com.kpj.pages.Billing_page.EInvoiceDashboard.SEL_TO; }

    private String route() {
        try { String u = page.url(); int i = u.indexOf('#'); return "[" + (i >= 0 ? u.substring(i) : path(u)) + "]"; } catch (Exception e) { return "[?]"; }
    }

    private static LocalDate parse(String s) { try { return LocalDate.parse(s.trim(), DMY); } catch (Exception e) { return null; } }
    /** Transaction Date as shown, e.g. "27 Aug 26". */
    private static LocalDate parseShown(String s) {
        try { return LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH)); } catch (Exception e) { return null; }
    }
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
