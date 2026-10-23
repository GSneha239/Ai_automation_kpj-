package com.kpj.tests.Investigation_page.Lab_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named LabResultEntry — referenced by its fully-qualified name.

/**
 * Investigation &gt; Lab &gt; <b>Lab Result Entry</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Investigation</b> → <b>Lab</b> → <b>Lab Result Entry</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b> — the From Date is <b>15 days before</b> the To Date.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>Click <b>List of Orders</b>.</li>
 *   <li>Select an order by clicking the <b>tick symbol</b> in its row, and verify the row is populated in
 *       <b>Test Details</b>.</li>
 *   <li>In <b>Test Details</b>, click the <b>tick symbol</b> on the row, then tick its <b>checkbox</b> as well.</li>
 *   <li>Click <b>Result Entry Report</b> and verify the PDF report is generated.</li>
 * </ol>
 *
 * <p>The screen carries TWO grids and each has its own tick, so every selection is scoped to its grid — an
 * unscoped lookup ticks the wrong one. The menu label is matched in full: Lab also carries <b>Lab Result Entry
 * Admin Auth</b>, and a loose match would open that screen instead.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=} (dd/MM/yyyy). By default the To Date is today and the From Date
 * is the To Date minus 15 days. The default status filter is <b>Pending</b>, so the run needs pending tests in the
 * range — it is DATA-DEPENDENT.</p>
 *
 * <p>Read-only apart from the report it prints: nothing is saved by this flow.</p>
 */
public class LabResultEntry extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /** The From Date sits this many days before the To Date. */
    public static final int RANGE_DAYS = 15;

    private static final java.time.format.DateTimeFormatter DTF =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public LabResultEntry() { super("Investigation_LabResultEntry"); }

    public static void main(String[] args) {
        LabResultEntry t = new LabResultEntry();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Investigation - Lab - Lab Result Entry", "Investigation > Lab > Lab Result Entry",
                "Search a 15-day range, open List of Orders, tick an order and check Test Details fills, tick a "
                        + "test (tick symbol and checkbox) and print the Result Entry Report. Nothing is saved.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        // To Date = today, From Date = 15 days before it — unless pinned with -Dto= / -Dfrom=.
        String to = System.getProperty("to", java.time.LocalDate.now().format(DTF));
        String from = System.getProperty("from",
                java.time.LocalDate.parse(to, DTF).minusDays(RANGE_DAYS).format(DTF));

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Investigation_page.Lab_page.LabResultEntry lre =
                new com.kpj.pages.Investigation_page.Lab_page.LabResultEntry(page);

        // 1) Navigate
        boolean rendered = lre.navigateViaMenu(BASE);
        if (!rendered) addSummary("Lab submenu offered", lre.lastMenu);
        step(page, "Open Lab Result Entry screen",
                "Click Investigation -> Lab -> Lab Result Entry",
                "The Lab Result Entry screen is shown",
                rendered ? "Opened " + page.url()
                           + (lre.lastRoute.isEmpty() ? "" : " (menu route " + lre.lastRoute + ")")
                           + ". The label is matched in full — Lab also has Lab Result Entry Admin Auth."
                         : "Did NOT reach the screen (" + page.url() + ")",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Dates — From = To - 15 days
        String dates = lre.enterDateRange(from, to);
        boolean datesOk = lre.datesEntered(from, to);
        long span = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(from, DTF), java.time.LocalDate.parse(to, DTF));
        step(page, "Enter from date and to date",
                "Enter the To Date " + to + " and set the From Date " + RANGE_DAYS + " days before it (" + from + ")",
                "Both dates are entered, the From Date " + RANGE_DAYS + " days before the To Date",
                (datesOk
                    ? "PASSES because both boxes KEPT their value: " + dates + ". The From Date is " + span
                      + " day(s) before the To Date" + (span == RANGE_DAYS ? "." : " — NOT the " + RANGE_DAYS
                      + " asked for, because the range was pinned with -Dfrom/-Dto.")
                      + " " + lre.lastDateRange + "."
                    : "FAILS — a date did not stay in its box: " + dates),
                datesOk ? "PASS" : "FAIL");

        // 3) Search
        String search = lre.clickSearch();
        int tests = lre.testRows();
        step(page, "Click Search", "Click Search",
                "The screen returns tests for the date range",
                (tests > 0 ? "PASSES because the search returned tests: " + search
                           : "FAILS because the search returned nothing: " + search),
                tests > 0 ? "PASS" : "FAIL");
        if (tests == 0) { addSummary("Result", "FAILED — no pending tests in " + from + " to " + to); return; }

        // 4) List of Orders
        String orders = lre.openListOfOrders();
        int orderRows = lre.orderRows();
        step(page, "Click List of Orders",
                "Click the List of Orders header (fnSearchResultEntry) and read the orders grid",
                "The List of Orders opens and lists the orders",
                (orderRows > 0 ? "PASSES because the orders grid holds " + orders
                               : "FAILS because the orders grid is empty: " + orders),
                orderRows > 0 ? "PASS" : "FAIL");
        if (orderRows == 0) { addSummary("Result", "FAILED — List of Orders is empty"); return; }

        // 5) Tick an order
        String orderTick = lre.tickFirstOrder();
        step(page, "Select a row in List of Orders by clicking its tick symbol",
                "Click the tick symbol on the first order",
                "The order is selected",
                (lre.orderTicked ? "PASSES because the tick read back as selected: " + orderTick
                                 : "FAILS because the order did not select: " + orderTick),
                lre.orderTicked ? "PASS" : "FAIL");

        // 6) Test Details populated
        int detailRows = lre.testRows();
        String detail = lre.describeTests();
        step(page, "Verify the selected row is populated in Test Details",
                "Read the Test Details grid after the order is ticked",
                "Test Details lists the selected order's test(s)",
                (detailRows > 0 ? "PASSES because Test Details holds " + detail
                                : "FAILS because Test Details is empty: " + detail),
                detailRows > 0 ? "PASS" : "FAIL");
        if (detailRows == 0) { addSummary("Result", "FAILED — Test Details empty for the ticked order"); return; }

        // 7) Tick in Test Details
        String testTick = lre.tickFirstTest();
        step(page, "In Test Details, click the tick symbol to select the row",
                "Click the tick symbol on the first test row",
                "The test row is selected",
                (lre.testTicked ? "PASSES because the tick read back as selected: " + testTick
                                : "FAILS because the test row did not select: " + testTick),
                lre.testTicked ? "PASS" : "FAIL");

        // 8) Tick the checkbox
        String check = lre.tickFirstTestCheckbox();
        step(page, "Tick the checkbox in that row",
                "Tick the row's checkbox (IsChecked)",
                "The checkbox is ticked",
                (lre.testCheckboxTicked() ? "PASSES because the box read back as set: " + check
                                          : "FAILS because the box did not tick: " + check),
                lre.testCheckboxTicked() ? "PASS" : "FAIL");

        // 9) Result Entry Report
        String report = lre.clickReportAndVerify();
        boolean reportOk = !lre.reportBlank;
        addSummary("Result Entry Report", report);
        step(lre.reportPng, "Click Result Entry Report and verify the PDF report is generated",
                "Click Result Entry Report",
                "A PDF report is produced and carries content",
                (reportOk ? "PASSES because a report was produced with content in it: " + report
                          : "FAILS — " + report),
                reportOk ? "PASS" : "FAIL");

        addSummary("Date range", from + " to " + to + " (" + span + " days)");
        addSummary("Selected order", lre.lastOrderTick);
        addSummary("Test Details", lre.lastTests);
        addSummary("Selected test", lre.lastTestTick);
        addSummary("Test checkbox", lre.lastTestCheck);
        addSummary("Result", reportOk ? "Report generated" : "Report not confirmed");
    }
}
