package com.kpj.tests.Inventory_page.Others_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

// The page object is also named StockAdjustmentLevel3 — referenced by its fully-qualified name.

/**
 * Inventory &gt; Others &gt; <b>Stock Adjustment Level 3</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Others</b> → <b>Stock Adjustment Level 3</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>In the list of stock, click on the tick symbol to choose any one row.</li>
 *   <li>Verify the selected row's items appear in <b>Stock Management Details</b>.</li>
 *   <li>Click <b>Print</b>; verify PDF report generation.</li>
 * </ol>
 *
 * <p>Sibling of {@link com.kpj.pages.Inventory_page.Others_page.StockAdjustmentLevel2} in the
 * {@code Inventory_page.Others_page} submodule, confirmed at route {@code #/StockAdjustmentLevel3} and
 * confirmed live to be structurally identical to that sibling (same ng-models, same handlers, same two
 * {@code ui-grid} components) — only the route and the server-side approval level differ. Every lesson
 * from that sibling carries over unchanged: the real "tick symbol" is {@code ui-grid}'s own
 * row-selection icon, distinct from a neighbouring, entirely inert {@code isapproved} checkbox column —
 * see the page object's class doc for the full detail. PDF generation is judged with {@link PdfReport},
 * the same helper already proven across this module. {@code describeControls()} is dumped into the
 * report at each stage so anything still fuzzy can be pinned exactly once this has run against the live
 * screen.</p>
 */
public class StockAdjustmentLevel3 extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public StockAdjustmentLevel3() { super("Inventory_Others_StockAdjustmentLevel3"); }

    public static void main(String[] args) {
        StockAdjustmentLevel3 t = new StockAdjustmentLevel3();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Stock Adjustment Level 3", "Inventory > Others > Stock Adjustment Level 3",
                "Search by date range, tick a row in the list of stock, verify Stock Management Details "
                        + "populates, click Print and verify PDF report generation.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Others_page.StockAdjustmentLevel3 sal3 =
                new com.kpj.pages.Inventory_page.Others_page.StockAdjustmentLevel3(page);

        // 1) Navigate
        boolean rendered = sal3.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", sal3.lastMenu);
        step(page, "Open Stock Adjustment Level 3 screen",
                "Click Inventory -> Others -> Stock Adjustment Level 3",
                "The Stock Adjustment Level 3 screen is shown",
                rendered ? "Opened " + page.url()
                           + (sal3.lastRoute.isEmpty() ? "" : " (menu route " + sal3.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean contentLoaded = sal3.waitForRealContent(15000);
        addSummary("Content loaded", contentLoaded ? "yes" : "NO — still just the header shell after 15s");

        addSummary("Screen controls", sal3.describeControls());

        // 2) Enter From Date / To Date
        String dates = sal3.enterDateRange(from, to);
        boolean datesOk = sal3.datesEntered(from, to);
        step(page, "Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                datesOk ? "PASSES: " + dates : "FAILS: " + dates,
                datesOk ? "PASS" : "FAIL");

        // 3) Click Search
        String search = sal3.clickSearch();
        boolean rowsOk = sal3.rowsFound();
        step(page, "Click Search", "Click Search",
                "Results appear in the list of stock",
                rowsOk ? "PASSES: " + search : "FAILS: " + search,
                rowsOk ? "PASS" : "FAIL");
        if (!rowsOk) { addSummary("Result", "FAILED — no row to select"); return; }

        // 4) In list of stock, click on the tick symbol to choose any one of the row
        String rowTick = sal3.clickFirstRowTick();
        boolean rowTickOk = sal3.rowTickClicked();
        step(page, "In list of stock, click on the tick symbol to choose any one of the row",
                "Click on the tick symbol",
                "The row is selected",
                rowTickOk ? "PASSES: " + rowTick : "FAILS: " + rowTick,
                rowTickOk ? "PASS" : "FAIL");
        if (!rowTickOk) { addSummary("Result", "FAILED — no row could be selected"); return; }

        // 5) Verify selected list appears in Stock Management Details
        boolean detailsOk = sal3.detailsPopulated();
        step(page, "Verify selected list appear in stock management details",
                "Check Stock Management Details after selecting the row",
                "The selected row's items appear in Stock Management Details",
                detailsOk ? "PASSES: " + sal3.lastDetailsCount : "FAILS: " + sal3.lastDetailsCount,
                detailsOk ? "PASS" : "FAIL");

        // 6) Click Print; verify pdf generated
        int tabsBeforePrint = sal3.clickPrint();
        boolean printClickedOk = sal3.printClicked();
        step(page, "Click Print", "Click Print",
                "The Print action fires",
                printClickedOk ? "PASSES: " + sal3.lastPrint : "FAILS: " + sal3.lastPrint,
                printClickedOk ? "PASS" : "FAIL");
        if (!printClickedOk) { addSummary("Result", "FAILED — the Print button was not found"); return; }

        PdfReport.Result pdf = PdfReport.capture(page, tabsBeforePrint, 15000);
        step(page, "Verify pdf generated", "Wait for the report to open",
                "A non-blank PDF is generated", pdf.diagnostics, pdf.blank ? "FAIL" : "PASS");

        addSummary("Row selected", sal3.lastRowTick);
        addSummary("Stock Management Details", sal3.lastDetailsCount);
        addSummary("Print PDF result", pdf.diagnostics);
    }
}
