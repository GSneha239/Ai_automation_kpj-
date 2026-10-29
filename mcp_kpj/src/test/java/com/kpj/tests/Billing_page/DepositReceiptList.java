package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The page object is named DepositReceipt (same simple name as the Add test in this package) — referenced by its
// fully-qualified name, as the existing DepositReceipt test does.

/**
 * Billing &gt; Deposit / Receipt &gt; Official Receipt Details &gt; <b>List Of Deposit / Receipt</b> — read-only checks.
 *
 * <p>Cases (IDs from the Phase 2 inspection): L01 open + panels, L02 default filters, L03 filter options, L04 search per
 * Reg. Type, L05 date range, L06 empty range, L07 From &gt; To, L09 MRN filter, L10 IPD + Cancelled, L11 ALL + Cancelled
 * (known HTTP 500 defect), L08 Type = Patient, then the grid on that result: L12 columns, L13 sort, L14 paging, L15 column
 * filter + Clear all filters, L16 footer totals, L17 row select + Modify dialog (opened and Closed, never saved), L18 grid
 * menu (read, nothing exported). L08 runs after L11 so the grid cases have a stable, filtered result to work on.</p>
 *
 * <p>Every case is isolated: an exception or a FAIL in one case is recorded as that case's step and the next case still
 * runs. Statuses use the report's existing set — PASS / FAIL / MANUAL / SKIPPED; a case that cannot run because of data or
 * environment is SKIPPED with an Actual text starting "BLOCKED:".</p>
 *
 * <p><b>Read-only:</b> no Save, Print, Reprint, export, or the Modify dialog's Save is ever clicked, and the page is never
 * reloaded.</p>
 */
public class DepositReceiptList extends DevHisBase {

    public DepositReceiptList() { super("Billing_DepositReceiptList"); }

    public static void main(String[] args) {
        DepositReceiptList t = new DepositReceiptList();
        try { t.run(); } finally { t.stop(); }
        String failure = t.junitFailure();
        if (failure != null) throw new IllegalStateException(failure);
    }

    /**
     * JUnit entry point. {@link DevHisBase#run()} records every problem as a report step and never throws, so on its own a
     * FAIL step leaves JUnit green. After run() has written the HTML report, a navigation failure (L01) or any case recorded
     * as FAIL — known application defects such as L11 and L13 included — is re-raised so JUnit / Surefire count the test as
     * failed. MANUAL (L07) and SKIPPED/BLOCKED cases do not fail it.
     */
    @org.junit.jupiter.api.Test
    void execute() {
        try { run(); } finally { stop(); }
        String failure = junitFailure();
        if (failure != null) org.junit.jupiter.api.Assertions.fail(failure);
    }

    /** The JUnit failure message for this run, or null when nothing failed. */
    private String junitFailure() {
        if (navigationFailure != null) return navigationFailureMessage();
        if (failedCases.isEmpty()) return null;
        return "Deposit / Receipt list case(s) FAILED: " + failedCases
                + (failedCases.contains("L11") ? " (L11 = known application defect: ALL + Cancelled -> HTTP 500)" : "")
                + (failedCases.contains("L13") ? " (L13 = Balance column sorts numbers as text, if the report says APPLICATION DEFECT)" : "")
                + " - details: " + reportDir.resolve("Billing_DepositReceiptList.html").toAbsolutePath();
    }

    /** Set by L01 when the Deposit / Receipt screen was not reached; null otherwise. */
    String navigationFailure = null;

    private String navigationFailureMessage() {
        return "L01 Deposit / Receipt screen not reached - " + navigationFailure
                + " (report: " + reportDir.resolve("Billing_DepositReceiptList.html").toAbsolutePath() + ")";
    }

    private static final int SEARCH_MS = 45000;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Grid columns shown on devhis (verified 25/10/2026), in order; the selection column is excluded. */
    static final List<String> EXPECTED_COLUMNS = Arrays.asList(
            "Allocation No.", "Print", "Reprint", "MRN", "Corporate/Patient Name", "Visit No.", "Deposit/Receipt Against",
            "Change Deposit/Receipt Against", "Modified DateTime", "Modified By", "Deposit/Receipt Date", "Deposit/Receipt",
            "Current Bill", "Allocated/consumed amount", "Balance", "Deposit/Receipt Type", "Remark");

    /** Deposit/Receipt Against options shown on devhis (verified 25/10/2026). */
    static final List<String> EXPECTED_AGAINST = Arrays.asList(
            "Blood Deposit", "Other Deposit", "Physioteraphy Deposit", "Receipt", "Receipt Deposit", "Receipt Recover Audit No", "Refund");

    private com.kpj.pages.Billing_page.DepositReceipt dr;
    private String ipdMrn = "";

    /** A case body returns {status, actual}. */
    private interface Case { String[] run() throws Exception; }

    /** Run one case; whatever happens inside it is recorded as this case's step and never stops the next case. */
    private void tc(String id, String name, String desc, String expected, Case c) {
        String status, actual;
        try {
            String[] r = c.run();
            status = r[0];
            actual = r[1];
        } catch (Throwable e) {
            status = "FAIL";
            actual = "ERROR: " + firstLine(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        step(page, id + " " + name, desc, expected, actual, status);
        if ("FAIL".equalsIgnoreCase(status) && !failedCases.contains(id)) failedCases.add(id);
    }

    /** IDs of the cases recorded as FAIL in this run (known application defects included). */
    final List<String> failedCases = new ArrayList<>();

    private static String[] verdict(boolean ok, String actual) { return new String[]{ ok ? "PASS" : "FAIL", actual }; }
    private static String[] blocked(String actual) { return new String[]{ "SKIPPED", "BLOCKED: " + actual }; }
    private static String[] manual(String actual) { return new String[]{ "MANUAL", actual }; }

    @Override
    protected void body() {
        meta("Billing - Deposit / Receipt - List Of Deposit / Receipt",
                "Billing > Deposit / Receipt > Official Receipt Details > List Of Deposit / Receipt",
                "Read-only checks of the search filters and the List Of Deposit / Receipt grid (L01-L18). No deposit/receipt is created,"
                        + " changed, printed or exported. L11 documents a known server defect (ALL + Cancelled -> HTTP 500).");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        dr = new com.kpj.pages.Billing_page.DepositReceipt(page);
        final String today = LocalDate.now().format(DMY);

        // ---- L01 ----
        final boolean[] onScreen = { false };
        tc("L01", "Open Deposit / Receipt screen", "Billing -> Deposit / Receipt (route #/advance)",
                "Screen opens with title 'Deposit Receipt' and the panels Official Receipt Details, List Of Deposit / Receipt, Add Deposit/Receipt",
                () -> {
                    long t0 = System.currentTimeMillis();
                    onScreen[0] = dr.navigateTo(BASE);
                    if (!onScreen[0]) {
                        navigationFailure = dr.lastNavDiagnostics.isEmpty() ? "navigateTo() returned false" : dr.lastNavDiagnostics;
                        return verdict(false, "NOT REACHED: " + navigationFailure);
                    }
                    boolean grid = dr.waitForListGrid(30000);
                    List<String> h = dr.panelHeadings();
                    String title = dr.title();
                    boolean ok = grid && "Deposit Receipt".equalsIgnoreCase(title.trim())
                            && h.contains("Official Receipt Details") && h.contains("List Of Deposit / Receipt") && h.contains("Add Deposit/Receipt");
                    return verdict(ok, "Opened in " + (System.currentTimeMillis() - t0) / 1000 + " s | URL=" + page.url() + " | title='" + title
                            + "' | breadcrumb='" + dr.breadcrumb() + "' | panels=" + h + " | list grid rendered=" + grid);
                });
        if (!onScreen[0]) {
            if (navigationFailure == null) navigationFailure = "L01 ended with an error before navigation completed";   // exception inside L01
            addSummary("Result", "FAILED - Deposit / Receipt screen not reached");
            return;
        }

        // ---- L02 ----
        tc("L02", "Default search filters", "Read the Official Receipt Details filters as the screen opens",
                "Reg. Type = IPD; From Date unticked; From/To disabled and both = today (" + today + ")",
                () -> {
                    Map<String, Object> s = dr.filterState();
                    boolean ok = "IPD".equals(s.get("regType")) && Boolean.FALSE.equals(s.get("dateTicked"))
                            && Boolean.TRUE.equals(s.get("fromDisabled")) && Boolean.TRUE.equals(s.get("toDisabled"))
                            && today.equals(s.get("from")) && today.equals(s.get("to"));
                    return verdict(ok, String.valueOf(s));
                });

        // ---- L03 ----
        tc("L03", "Filter dropdown options", "Read the Deposit/Receipt Type, Deposit/Receipt Against and Payor filter lists",
                "Type offers Company and Patient; Against offers " + EXPECTED_AGAINST,
                () -> {
                    List<String> types = dr.selectOptions("advance.AdvanceTypeid1");
                    List<String> against = dr.selectOptions("advance.againsttype1");
                    List<String> payor = dr.selectOptions("advance.receivableid");
                    List<String> missing = new ArrayList<>();
                    for (String t : Arrays.asList("Company", "Patient")) if (!types.contains(t)) missing.add("Type:" + t);
                    for (String a : EXPECTED_AGAINST) if (!against.contains(a)) missing.add("Against:" + a);
                    return verdict(missing.isEmpty(), "Type=" + types + " | Against=" + against + " | Payor (before a Type is chosen)=" + payor.size()
                            + " option(s) " + payor + (missing.isEmpty() ? "" : " | MISSING " + missing));
                });

        // ---- L04 (one step per Reg. Type) ----
        dr.setDateFilter(false);
        dr.setCancelled(false);
        for (String reg : Arrays.asList("IPD", "OPD", "ALL")) {
            tc("L04", "Search Reg. Type " + reg, "Reg. Type = " + reg + ", no date filter, click Search",
                    "HTTP 200; rows are listed and the pager total equals the rows loaded",
                    () -> {
                        dr.setRegType(reg);
                        com.kpj.pages.Billing_page.DepositReceipt.SearchResult s = dr.search(SEARCH_MS);
                        if (!s.httpOk()) return verdict(false, s.summary());
                        if ("IPD".equals(reg)) ipdMrn = firstMrn(dr.gridRows(false));
                        if (s.rows == 0) return blocked("no Deposit/Receipt records for Reg. Type " + reg + " (" + s.summary() + ")");
                        int total = pagerTotal(s.pager);
                        return verdict(total == s.rows, s.summary() + (total == s.rows ? "" : " | pager total " + total + " != rows " + s.rows));
                    });
        }

        // ---- L05 ----
        tc("L05", "Search by date range", "Reg. Type = IPD, tick From Date, From = today - 12 months, To = today, Search",
                "HTTP 200; every Deposit/Receipt Date is inside the range",
                () -> {
                    dr.setRegType("IPD");
                    LocalDate from = LocalDate.now().minusMonths(12), to = LocalDate.now();
                    dr.setDateRange(from.format(DMY), to.format(DMY));
                    com.kpj.pages.Billing_page.DepositReceipt.SearchResult s = dr.search(SEARCH_MS);
                    if (!s.httpOk()) return verdict(false, s.summary());
                    if (s.rows == 0) return blocked("no IPD deposits between " + from.format(DMY) + " and " + to.format(DMY));
                    List<String> outside = new ArrayList<>();
                    for (Map<String, Object> r : dr.gridRows(false)) {
                        LocalDate d = parseDate(String.valueOf(r.get("Date1")));
                        if (d == null || d.isBefore(from) || d.isAfter(to)) outside.add(r.get("ReceiptNo") + "=" + r.get("Date1"));
                    }
                    return verdict(outside.isEmpty(), "Range " + from.format(DMY) + " - " + to.format(DMY) + ": " + s.summary()
                            + (outside.isEmpty() ? " | all dates inside the range" : " | OUTSIDE: " + outside));
                });

        // ---- L06 ----
        tc("L06", "Search a range with no records", "Reg. Type = IPD, From Date ticked, 01/01/2001 - 31/01/2001, Search",
                "HTTP 200 and an empty grid",
                () -> {
                    dr.startToastCapture();
                    dr.setDateRange("01/01/2001", "31/01/2001");
                    com.kpj.pages.Billing_page.DepositReceipt.SearchResult s = dr.search(SEARCH_MS);
                    List<String> toasts = dr.capturedToasts();
                    return verdict(s.httpOk() && s.rows == 0, s.summary() + " | message shown: " + (toasts.isEmpty() ? "none" : toasts));
                });

        // ---- L07 ----
        tc("L07", "From Date later than To Date", "Reg. Type = IPD, From = today, To = today - 7 days, Search",
                "No records; the expected validation message is not specified (FSD not available) - record the behaviour",
                () -> {
                    dr.startToastCapture();
                    dr.setDateRange(today, LocalDate.now().minusDays(7).format(DMY));
                    com.kpj.pages.Billing_page.DepositReceipt.SearchResult s = dr.search(SEARCH_MS);
                    List<String> toasts = dr.capturedToasts();
                    String a = s.summary() + " | message shown: " + (toasts.isEmpty() ? "none" : toasts);
                    if (!s.httpOk() || s.rows > 0) return verdict(false, a);
                    return manual(a + " | no From > To validation - needs a BA decision whether one is required");
                });
        dr.setDateFilter(false);

        // ---- L09 ----
        tc("L09", "Search by MRN", "Reg. Type = IPD, enter an MRN taken from the IPD list, Search",
                "HTTP 200; every row has that MRN",
                () -> {
                    if (ipdMrn.isEmpty()) return blocked("no MRN available from the IPD search (L04)");
                    dr.setRegType("IPD");
                    dr.setMrnFilter(ipdMrn);
                    com.kpj.pages.Billing_page.DepositReceipt.SearchResult s = dr.search(SEARCH_MS);
                    List<String> other = new ArrayList<>();
                    for (Map<String, Object> r : dr.gridRows(false)) if (!ipdMrn.equals(String.valueOf(r.get("MRNo")))) other.add(String.valueOf(r.get("MRNo")));
                    dr.setMrnFilter("");
                    return verdict(s.httpOk() && s.rows > 0 && other.isEmpty(), "MRN " + ipdMrn + ": " + s.summary()
                            + (other.isEmpty() ? "" : " | OTHER MRNs: " + other));
                });

        // ---- L10 ----
        tc("L10", "Cancelled filter (IPD)", "Reg. Type = IPD, tick Cancelled, Search",
                "HTTP 200 (the cancelled records for IPD are listed, possibly none)",
                () -> {
                    dr.setRegType("IPD");
                    dr.setCancelled(true);
                    com.kpj.pages.Billing_page.DepositReceipt.SearchResult s = dr.search(SEARCH_MS);
                    return verdict(s.httpOk(), s.summary());
                });

        // ---- L11 (known defect) ----
        tc("L11", "Cancelled filter (ALL) - known defect", "Reg. Type = ALL, Cancelled ticked, Search",
                "HTTP 200 and the cancelled records for all registration types are listed",
                () -> {
                    dr.startToastCapture();
                    dr.setCancelled(true);
                    dr.setRegType("ALL");
                    com.kpj.pages.Billing_page.DepositReceipt.SearchResult s = dr.search(SEARCH_MS);
                    List<String> toasts = dr.capturedToasts();
                    if (s.httpOk()) return verdict(true, s.summary() + " | the known HTTP 500 defect did not reproduce");
                    boolean stale = s.rows == s.rowsBefore && s.rows > 0;
                    return verdict(false, "KNOWN DEFECT (seen during the Phase 2 inspection, 25/10/2026): " + s.summary()
                            + " | message shown to the user: " + (toasts.isEmpty() ? "none" : toasts)
                            + (stale ? " | the grid still shows the previous search's " + s.rows + " row(s) (stale results)" : ""));
                });
        // put the filters back for the remaining cases, whatever L11 did
        try { dr.setCancelled(false); dr.setRegType("IPD"); } catch (Exception e) { System.out.println("L11 cleanup: " + e.getMessage()); }

        // ---- L08 ----
        tc("L08", "Filter by Deposit/Receipt Type = Patient", "Reg. Type = IPD, Type = Patient (Payor list loads), Search",
                "Payor list is populated after the Type is chosen; HTTP 200; every row's Deposit/Receipt Type is Patient",
                () -> {
                    String t = dr.setFilterType("Patient");
                    List<String> payor = dr.selectOptions("advance.receivableid");
                    String payorSel = dr.selectedText("advance.receivableid");
                    com.kpj.pages.Billing_page.DepositReceipt.SearchResult s = dr.search(SEARCH_MS);
                    if (!s.httpOk()) return verdict(false, s.summary());
                    if (s.rows == 0) return blocked("no IPD Patient-type records (" + s.summary() + ")");
                    List<String> other = new ArrayList<>();
                    for (Map<String, Object> r : dr.gridRows(false)) if (!"Patient".equals(String.valueOf(r.get("AdvanceType")))) other.add(r.get("ReceiptNo") + "=" + r.get("AdvanceType"));
                    boolean ok = "Patient".equals(t) && payor.size() > 1 && other.isEmpty();
                    return verdict(ok, "Type=" + t + " | Payor list " + payor.size() + " option(s), selected '" + payorSel + "' | " + s.summary()
                            + (other.isEmpty() ? "" : " | NOT Patient: " + other));
                });

        // ---- grid cases on the L08 result ----
        tc("L12", "Grid columns", "Read the columns the List Of Deposit / Receipt grid shows",
                "Columns, in order: " + EXPECTED_COLUMNS,
                () -> {
                    List<String> cols = dr.visibleColumnHeaders();
                    return verdict(EXPECTED_COLUMNS.equals(cols), "Shown: " + cols);
                });

        tc("L13", "Sort by column header (Balance)", "From the unsorted state click the Balance header once, then once more;"
                        + " compare the Balance values displayed on page 1 with the loaded rows sorted numerically",
                "1st click: ascending - page 1 shows the smallest balances in increasing order; 2nd click: descending - page 1 shows the"
                        + " largest balances in decreasing order; sorting is client-side (no API call)",
                () -> {
                    int n = dr.gridVisibleCount();
                    if (n < 2) return blocked("fewer than 2 rows to sort");
                    // Start from the unsorted state. ui-grid cycles a header through none -> asc -> desc -> none.
                    String d0 = dr.sortDirection("Balance");
                    for (int i = 0; i < 2 && !"none".equals(d0); i++) d0 = dr.sortByHeader("Balance");
                    if (!"none".equals(d0)) return verdict(false, "could not return the Balance column to unsorted (state: " + d0 + ")");
                    if (dr.currentPage() != 1) dr.clickPager("first");
                    List<Double> all = balances();                      // every row that matches the filters (all pages)
                    List<Double> numAsc = new ArrayList<>(all); numAsc.sort(null);
                    List<Double> numDesc = new ArrayList<>(numAsc); java.util.Collections.reverse(numDesc);
                    String type = dr.columnType("Balance");
                    int api0 = dr.apiRequestCount();

                    String d1 = dr.sortByHeader("Balance");
                    List<Double> shown1 = parseAll(dr.columnCellTexts("Balance"));
                    String d2 = dr.sortByHeader("Balance");
                    List<Double> shown2 = parseAll(dr.columnCellTexts("Balance"));
                    int apiCalls = dr.apiRequestCount() - api0;

                    boolean ascOk = "asc".equals(d1) && !shown1.isEmpty() && shown1.equals(numAsc.subList(0, shown1.size()));
                    boolean descOk = "desc".equals(d2) && !shown2.isEmpty() && shown2.equals(numDesc.subList(0, shown2.size()));

                    // Cleanup, not part of the check: a 3rd click returns the column to unsorted for the cases that follow.
                    String d3 = dr.sortByHeader("Balance");

                    StringBuilder a = new StringBuilder();
                    a.append("client-side sort (").append(apiCalls).append(" API call(s) during the clicks); column type '").append(type).append("'")
                     .append(" | 1st click=").append(d1).append(", page 1 shows ").append(shown1).append(" - expected ").append(numAsc.subList(0, Math.min(shown1.size(), numAsc.size())))
                     .append(ascOk ? " OK" : " WRONG")
                     .append(" | 2nd click=").append(d2).append(", page 1 shows ").append(shown2).append(" - expected ").append(numDesc.subList(0, Math.min(shown2.size(), numDesc.size())))
                     .append(descOk ? " OK" : " WRONG")
                     .append(" | cleanup: 3rd click -> ").append(d3).append(" (unsorted again)");
                    if (!(ascOk && descOk) && "string".equalsIgnoreCase(type) && textOrder(shown1, shown2, all))
                        a.append(" | APPLICATION DEFECT: the Balance column (numeric data) is sorted as TEXT - the grid's column type is 'string',"
                                + " so e.g. 9,990 sorts above 70,000 and 250,000 is not first when descending");
                    return verdict(ascOk && descOk, a.toString());
                });

        tc("L14", "Pagination", "Make sure the list has more than 5 rows (current result; otherwise search Reg. Type IPD, then ALL),"
                        + " page size 5: Next, Last, First; then items per page 10; back to 5",
                "Next advances to page 2, Last reaches the final page, First returns to page 1, page size changes 5 -> 10; on every"
                        + " step the pager text 'a - b of N items', the current page and the rendered rows agree",
                () -> {
                    int n = dr.gridVisibleCount();
                    String source = "current result (" + n + " rows)";
                    for (String reg : Arrays.asList("IPD", "ALL")) {
                        if (n > 5) break;
                        dr.setRegType(reg);
                        com.kpj.pages.Billing_page.DepositReceipt.SearchResult s = dr.search(SEARCH_MS);
                        n = dr.gridVisibleCount();
                        source = "Reg. Type " + reg + " search: " + s.summary();
                    }
                    if (n <= 5) return blocked("no result set with more than 5 rows (" + source + ")");

                    StringBuilder a = new StringBuilder("Result set: ").append(source);
                    boolean ok = true;
                    String size5 = dr.setPageSize(5);
                    if (dr.currentPage() != 1) dr.clickPager("first");
                    ok &= check(a, "Start (page size " + size5 + ")", 1, 5, n);
                    ok &= dr.clickPager("next") && check(a, "Next", 2, 5, n);
                    ok &= dr.clickPager("last") && check(a, "Last", (n + 4) / 5, 5, n);
                    ok &= dr.clickPager("first") && check(a, "First", 1, 5, n);
                    String size10 = dr.setPageSize(10);
                    if (dr.currentPage() != 1) dr.clickPager("first");
                    ok &= "10".equals(size10) && check(a, "Page size " + size10, 1, 10, n);
                    String back = dr.setPageSize(5);    // reset for the cases that follow
                    a.append(" | reset page size -> ").append(back);
                    return verdict(ok, a.toString());
                });

        tc("L15", "Column filter and Clear all filters", "Type an MRN from the grid into the MRN column filter, then grid menu -> Clear all filters",
                "Only rows with that MRN remain; Clear all filters brings every row back",
                () -> {
                    int total = dr.gridVisibleCount();
                    String mrn = firstMrn(dr.gridRows(true));
                    if (mrn.isEmpty()) return blocked("no MRN in the grid to filter on");
                    int n = dr.filterColumn("MRN", mrn);
                    List<String> other = new ArrayList<>();
                    for (Map<String, Object> r : dr.gridRows(true)) if (!String.valueOf(r.get("MRNo")).contains(mrn)) other.add(String.valueOf(r.get("MRNo")));
                    String pagerFiltered = dr.pagerText();
                    int after = dr.clearAllFiltersViaMenu();
                    boolean ok = n > 0 && n <= total && other.isEmpty() && after == total;
                    return verdict(ok, "Filter MRN '" + mrn + "': " + total + " -> " + n + " row(s) ('" + pagerFiltered + "')"
                            + (other.isEmpty() ? "" : " | NON-MATCHING: " + other) + " | after Clear all filters: " + after + " row(s)");
                });

        tc("L16", "Footer totals", "Compare the footer totals with the sum of the rows listed",
                "Deposit/Receipt, Allocated/consumed amount and Balance totals equal the sums of their columns",
                () -> {
                    Map<String, String> f = dr.footerTotals();
                    String[][] cols = { { "Deposit/Receipt", "TotalBillAmount" }, { "Allocated/consumed amount", "AllocatedConsumedAmount" }, { "Balance", "BalanceAmount" } };
                    boolean ok = true;
                    StringBuilder a = new StringBuilder();
                    for (String[] c : cols) {
                        double sum = dr.sumField(c[1], false);
                        String shown = f.getOrDefault(c[0], "");
                        double v = parseNum(shown);
                        boolean m = !Double.isNaN(v) && Math.abs(v - sum) < 0.01;
                        ok &= m;
                        a.append(c[0]).append(": footer '").append(shown).append("' vs sum ").append(String.format("%.2f", sum)).append(m ? " OK" : " MISMATCH").append(" | ");
                    }
                    return verdict(ok, a.toString().replaceAll(" \\| $", ""));
                });

        tc("L17", "Select a row; Change Deposit/Receipt Against dialog", "Tick the first row, click its Modify button, then Close the dialog (never Save)",
                "Modify is disabled until a row is selected; the 'Change Deposite Against' dialog opens with the Against list, the restrict-consumption box, Save and Close; Close dismisses it",
                () -> {
                    if (dr.gridDataCount() < 1) return blocked("no rows to select");
                    dr.clearRowSelection();
                    Map<String, Object> before = dr.modifyButtonsState();
                    int sel = dr.selectFirstRow();
                    Map<String, Object> after = dr.modifyButtonsState();
                    String dialog = dr.openChangeAgainstDialog();
                    boolean closed = dr.closeChangeAgainstDialog();
                    dr.clearRowSelection();
                    boolean ok = num(before.get("enabledModify")) == 0 && sel == 1 && num(after.get("enabledModify")) == 1
                            && dialog.toLowerCase().contains("change deposite against") && dialog.contains("[changeDeposit.againsttypes]")
                            && dialog.contains("Close") && closed;
                    return verdict(ok, "before select " + before + " | selected " + sel + " -> " + after
                            + " | dialog: " + (dialog.isEmpty() ? "(did not open)" : dialog) + " | closed with Close: " + closed);
                });

        tc("L18", "Grid menu", "Open the grid menu (top-right of the grid), read its entries, close it - nothing is exported",
                "Menu offers Clear all filters and export to CSV, PDF and Excel",
                () -> {
                    List<String> items = dr.gridMenuItems();
                    List<String> want = Arrays.asList("Clear all filters", "Export all data as csv", "Export all data as pdf", "Export all data as Excel");
                    List<String> missing = new ArrayList<>();
                    for (String w : want) if (!items.contains(w)) missing.add(w);
                    return verdict(missing.isEmpty(), "Menu: " + items + (missing.isEmpty() ? "" : " | MISSING " + missing));
                });

        addSummary("Mode", "Read-only (no Save / Print / Reprint / export)");
        addSummary("IPD MRN used for L09", ipdMrn.isEmpty() ? "(none)" : ipdMrn);
        addSummary("Known defect", "L11: Reg. Type ALL + Cancelled -> POST /api/Advance/fetch returns HTTP 500, no message, stale grid");
    }

    // ---- helpers -------------------------------------------------------------

    private List<Double> balances() {
        List<Double> out = new ArrayList<>();
        for (Map<String, Object> r : dr.gridRows(true)) out.add(num(r.get("BalanceAmount")));
        return out;
    }

    /** One pager checkpoint: current page, pager text "from - to of N items", total pages and rows rendered must all agree
     *  for {@code page} at {@code size} rows per page. Appends the evidence to {@code a}. */
    private boolean check(StringBuilder a, String label, int page, int size, int n) {
        int from = (page - 1) * size + 1, to = Math.min(page * size, n), pages = (n + size - 1) / size;
        int cur = dr.currentPage(), tp = dr.totalPages(), rendered = dr.renderedRowCount();
        String pager = dr.pagerText();
        int[] p = pagerNums(pager);
        boolean ok = cur == page && tp == pages && p[0] == from && p[1] == to && p[2] == n && rendered == to - from + 1;
        a.append(" | ").append(label).append(": page ").append(cur).append("/").append(tp).append(", '").append(pager)
         .append("', ").append(rendered).append(" row(s) shown").append(ok ? " OK" : " - expected page " + page + "/" + pages
                 + ", '" + from + " - " + to + " of " + n + " items', " + (to - from + 1) + " row(s)");
        return ok;
    }

    /** "70,000.00" -> 70000.0 for every displayed cell (unparseable cells become NaN, which never matches). */
    private static List<Double> parseAll(List<String> cells) {
        List<Double> out = new ArrayList<>();
        for (String c : cells) out.add(parseNum(c));
        return out;
    }

    /** True when the 2nd-click page-1 values equal the loaded balances sorted as TEXT, descending — i.e. the grid compared
     *  the numbers as strings (JavaScript String(n), e.g. "9990" &gt; "70000"). */
    private static boolean textOrder(List<Double> shownAsc, List<Double> shownDesc, List<Double> all) {
        List<String> txt = new ArrayList<>();
        for (Double d : all) txt.add(java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString());
        txt.sort(java.util.Comparator.reverseOrder());
        List<Double> expect = new ArrayList<>();
        for (int i = 0; i < Math.min(shownDesc.size(), txt.size()); i++) expect.add(Double.parseDouble(txt.get(i)));
        return !shownDesc.isEmpty() && shownDesc.equals(expect);
    }

    private static String firstMrn(List<Map<String, Object>> rows) {
        for (Map<String, Object> r : rows) {
            String m = String.valueOf(r.getOrDefault("MRNo", "")).trim();
            if (!m.isEmpty() && !"null".equals(m)) return m;
        }
        return "";
    }

    /** "1 - 5 of 42 items" -> {1, 5, 42}; -1s when it does not parse. */
    private static int[] pagerNums(String pager) {
        Matcher m = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)\\s*of\\s*(\\d+)").matcher(pager == null ? "" : pager);
        return m.find() ? new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)) } : new int[]{ -1, -1, -1 };
    }

    private static int pagerTotal(String pager) { return pagerNums(pager)[2]; }

    private static LocalDate parseDate(String s) {
        try { return LocalDate.parse(s.trim().substring(0, 10), DMY); } catch (Exception e) { return null; }
    }

    private static double parseNum(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    private static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return Double.NaN; }
    }

    private static String firstLine(String s) { return s == null ? "" : s.split("\n")[0]; }
}
