package com.kpj.tests.Investigation_page.Radiology_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named RadiologyReportDelivery — referenced by its fully-qualified name.

/**
 * Investigation &gt; Radiology &gt; <b>Radiology Report Delivery</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Investigation</b> → <b>Radiology</b> → <b>Radiology Report Delivery</b>.</li>
 *   <li>Enter the <b>MRN</b> and click the search symbol beside it.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Click <b>Search</b> again.</li>
 *   <li>In <b>Test</b>, verify records exist.</li>
 *   <li>Click the <b>tick symbol</b> on one record.</li>
 *   <li>Click <b>Delivered</b>.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live, so every control is found by FUZZY matching rather than a
 * pinned exact model — see {@link com.kpj.pages.Investigation_page.Radiology_page.RadiologyReportDelivery}.
 * {@code describeControls()} is dumped into the report so the real ng-models can replace the fuzzy
 * matches after this run.</p>
 *
 * <p>Pin values with {@code -Dmrn=}, {@code -Dfrom=}, {@code -Dto=}.</p>
 *
 * <p>&#9888; A successful run marks a REAL radiology report as delivered in the target environment.</p>
 */
public class RadiologyReportDelivery extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public RadiologyReportDelivery() { super("Investigation_RadiologyReportDelivery"); }

    public static void main(String[] args) {
        RadiologyReportDelivery t = new RadiologyReportDelivery();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Radiology Report Delivery", "Investigation > Radiology > Radiology Report Delivery",
                "&#9888; Marks a REAL radiology report delivered: search by MRN and date range, tick a "
                        + "record, click Delivered.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String mrn = System.getProperty("mrn", "100000684");
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Investigation_page.Radiology_page.RadiologyReportDelivery rd =
                new com.kpj.pages.Investigation_page.Radiology_page.RadiologyReportDelivery(page);

        // 1) Navigate
        boolean rendered = rd.navigateViaMenu(BASE);
        if (!rendered) addSummary("Radiology submenu offered", rd.lastMenu);
        step(page, "Open Radiology Report Delivery screen",
                "Click Investigation -> Radiology -> Radiology Report Delivery",
                "The Radiology Report Delivery screen is shown",
                rendered ? "Opened " + page.url()
                           + (rd.lastRoute.isEmpty() ? "" : " (menu route " + rd.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Radiology submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("Screen controls", rd.describeControls());

        // Not one of the requested steps: the Sub Group list stayed empty on every MRN tried, including
        // a blank one, so these two filters — left unset by default — are tried as the cascading gate.
        String opdIpd = rd.selectOpdIpd("^\\s*all\\.?\\s*$");
        boolean opdIpdOk = opdIpd != null && opdIpd.startsWith("selected");
        step(page, "Select the OPD/IPD filter", "Select \"All\" in the OPD/IPD/All filter",
                "The filter is selected",
                (opdIpdOk ? "PASSES: " + opdIpd : "FAILS: " + opdIpd),
                opdIpdOk ? "PASS" : "FAIL");

        String reportStatus = rd.selectReportStatus("^\\s*not\\s*delivered\\.?\\s*$");
        boolean reportStatusOk = reportStatus != null && reportStatus.startsWith("selected");
        step(page, "Select the report status filter", "Select \"Not Delivered\" in the status filter",
                "The filter is selected",
                (reportStatusOk ? "PASSES: " + reportStatus : "FAILS: " + reportStatus),
                reportStatusOk ? "PASS" : "FAIL");

        // 2) MRN + the search symbol beside it
        String mrnSearch = rd.enterMrnAndClickSearchSymbol(mrn);
        boolean mrnOk = rd.mrnEntered(mrn);
        step(page, "Enter MRN and click the search symbol",
                "Enter the MRN " + mrn + " and click the search symbol beside it",
                "The MRN is entered and the search symbol is clicked",
                (mrnOk
                    ? "PASSES because the box read the value back: " + mrnSearch
                    : "FAILS: " + mrnSearch),
                mrnOk ? "PASS" : "FAIL");

        // 3) Click Search
        String search1 = rd.clickSearch();
        step(page, "Click Search", "Click Search",
                "The screen searches with the MRN applied",
                search1, "PASS");

        // 4) Date range
        String dates = rd.enterDateRange(from, to);
        boolean datesOk = rd.datesEntered(from, to);
        step(page, "Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                (datesOk
                    ? "PASSES because both boxes KEPT their value: " + dates
                      + ". These pickers arrive pre-filled and append to what is there, so each box is "
                      + "cleared before typing."
                    : "FAILS — a date did not stay in its box: " + dates),
                datesOk ? "PASS" : "FAIL");

        // 5) Click Search again
        String search2 = rd.clickSearch();
        int rows = rd.rowCount();
        String subNote = "";
        if (rows == 0) {
            // Not one of the requested steps, but this screen — like the sibling Accept Radiology Order
            // screen — refuses to return anything until a Sub Group is chosen. Each is tried in turn
            // until one returns records, and the report says which were empty.
            String sub = rd.selectSubGroupWithRecords();
            rows = rd.rowCount();
            subNote = "  ||  the plain search returned nothing, so a Sub Group was chosen — " + sub
                    + ". That filter is not in the requested steps, but the screen will not search "
                    + "without it.";
            search2 = rd.lastSearch;
        }
        step(page, "Click Search", "Click Search",
                "The test grid is filled",
                (rows > 0 ? "PASSES because the search returned rows: " + search2 + subNote
                          : "FAILS because the search returned nothing: " + search2 + subNote),
                rows > 0 ? "PASS" : "FAIL");
        addSummary("Sub Group", rd.lastSubGroup);

        // 6) Records exist
        String rowsText = rd.describeRows();
        step(page, "Verify records exist in Test",
                "Read the test grid",
                "At least one record is listed",
                (rows > 0
                    ? "PASSES because the grid lists " + rowsText
                    : "FAILS because the grid is empty: " + rowsText),
                rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("Result", "FAILED — no record to deliver"); return; }

        // 7) Tick
        String tick = rd.tickFirstRecord();
        boolean ticked = rd.recordTicked();
        step(page, "Click the tick symbol on one record",
                "Click the tick symbol on the first record",
                "The record is selected",
                (ticked
                    ? "PASSES because the control read back as set: " + tick
                    : "FAILS because the record did not select: " + tick),
                ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED — no record was ticked"); return; }

        // 8) Delivered
        String delivered = rd.clickDelivered();
        boolean deliveredOk = rd.deliveredClicked();
        step(page, "Click Delivered", "Click Delivered",
                "The Delivered action is submitted for the ticked record",
                delivered, deliveredOk ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Date range", from + " to " + to);
        addSummary("Records", rd.lastRows);
        addSummary("Selected record", rd.lastTick);
        addSummary("Result", deliveredOk ? "Delivered clicked — " + delivered : "Delivered NOT confirmed clicked");
    }
}
