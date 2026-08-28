package com.kpj.tests.Investigation_page.Radiology_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named RadiologyResultEntry — referenced by its fully-qualified name.

/**
 * Investigation &gt; Radiology &gt; <b>Result Entry</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Investigation</b> → <b>Radiology</b> → <b>Result Entry</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>In the test grid, verify records exist.</li>
 *   <li>Click the <b>tick symbol</b> on one record.</li>
 *   <li>Click <b>Result Entry Report</b>.</li>
 *   <li>Verify the report is generated — and carries content.</li>
 * </ol>
 *
 * <p>The menu label is matched in full: Radiology also carries <b>Result Entry Authentication</b> and
 * <b>Result Entry Admin Auth</b>, and a loose match would open one of those instead.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=}.</p>
 *
 * <p>Read-only apart from the report it prints: nothing is saved by this flow.</p>
 */
public class RadiologyResultEntry extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public RadiologyResultEntry() { super("Investigation_RadiologyResultEntry"); }

    public static void main(String[] args) {
        RadiologyResultEntry t = new RadiologyResultEntry();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Radiology Result Entry", "Investigation > Radiology > Result Entry",
                "Search a date range, tick a record and print the Result Entry Report. Nothing is saved.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Investigation_page.Radiology_page.RadiologyResultEntry re =
                new com.kpj.pages.Investigation_page.Radiology_page.RadiologyResultEntry(page);

        boolean rendered = re.navigateViaMenu(BASE);
        if (!rendered) addSummary("Radiology submenu offered", re.lastMenu);
        step(page, "Open Result Entry screen",
                "Click Investigation -> Radiology -> Result Entry",
                "The Result Entry screen is shown",
                rendered ? "Opened " + page.url()
                           + (re.lastRoute.isEmpty() ? "" : " (menu route " + re.lastRoute + ")")
                           + ". The label is matched in full — Radiology also has Result Entry "
                           + "Authentication and Result Entry Admin Auth."
                         : "Did NOT reach the screen (" + page.url() + ")",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Dates
        String dates = re.enterDateRange(from, to);
        boolean datesOk = re.datesEntered(from, to);
        step(page, "Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                (datesOk
                    ? "PASSES because both boxes KEPT their value: " + dates
                      + ". These pickers arrive pre-filled and append to what is there, so each box is "
                      + "cleared before typing."
                    : "FAILS — a date did not stay in its box: " + dates),
                datesOk ? "PASS" : "FAIL");

        // Search
        String search = re.clickSearch();
        int rows = re.rowCount();
        String note = "";
        if (rows == 0 && re.searchRefused()) {
            // The sister screen refuses to search without a sub group and says so in a toast. If this
            // one does the same, satisfy it and say that the step list did not mention it.
            String sub = re.selectSubGroupWithRecords();
            rows = re.rowCount();
            note = "  ||  the screen first declined with \"" + re.lastRefusal + "\", so a sub group was "
                    + "chosen — " + sub + ". That filter is not in the requested steps, but the screen "
                    + "will not search without it.";
            search = re.lastSearch;
        }
        step(page, "Click Search", "Click Search",
                "The test grid is filled",
                (rows > 0 ? "PASSES because the search returned records: " + search + note
                          : "FAILS because the search returned nothing: " + search + note),
                rows > 0 ? "PASS" : "FAIL");

        // Records exist
        String rowsText = re.describeRows();
        step(page, "Verify records exist in the test grid",
                "Read the test grid",
                "At least one record is listed",
                (rows > 0 ? "PASSES because the grid holds " + rowsText
                          : "FAILS because the grid is empty: " + rowsText),
                rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("Result", "FAILED — no record to report on"); return; }

        // Tick
        String tick = re.tickFirstRecord();
        boolean ticked = re.recordTicked();
        step(page, "Click the tick symbol on one record",
                "Click the tick symbol on the first record",
                "The record is selected",
                (ticked
                    ? "PASSES because the control read back as set: " + tick
                    : "FAILS because the record did not select: " + tick),
                ticked ? "PASS" : "FAIL");

        // Report
        String report = re.clickReportAndVerify();
        boolean reportOk = !re.reportBlank;
        addSummary("Result Entry Report", report);
        step(page, "Click Result Entry Report and verify it is generated",
                "Click Result Entry Report",
                "A report is produced and carries content",
                (reportOk
                    ? "PASSES because a report was produced with content in it: " + report
                    : "FAILS — " + report),
                reportOk ? "PASS" : "FAIL");

        addSummary("Date range", from + " to " + to);
        addSummary("Records", re.lastRows);
        addSummary("Selected record", re.lastTick);
        addSummary("Result", reportOk ? "Report generated" : "Report not confirmed");
    }
}
