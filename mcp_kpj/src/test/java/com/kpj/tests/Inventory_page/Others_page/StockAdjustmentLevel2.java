package com.kpj.tests.Inventory_page.Others_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

// The page object is also named StockAdjustmentLevel2 — referenced by its fully-qualified name.

/**
 * Inventory &gt; Others &gt; <b>Stock Adjustment Level 2</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Others</b> → <b>Stock Adjustment Level 2</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>In the list of stock, click on the tick symbol to choose any one row.</li>
 *   <li>Verify the selected row's items appear in <b>Stock Management Details</b>.</li>
 *   <li>Click <b>Print</b>; verify PDF report generation.</li>
 * </ol>
 *
 * <p>Sibling of {@link com.kpj.pages.Inventory_page.Others_page.GatePassOutList} in the
 * {@code Inventory_page.Others_page} submodule — a pure search/report screen with no New/Save, confirmed
 * at route {@code #/StockAdjustmentLevel2}. Every control was walked by hand in a live browser before
 * writing any selector (the same discipline applied to that sibling and to {@link
 * com.kpj.pages.Inventory_page.Transfer_page.ReceiveIssueItem}), which is how this screen's real "tick
 * symbol" ({@code ui-grid}'s own row-selection icon) was distinguished from a neighbouring, entirely
 * inert {@code isapproved} checkbox column that looks similar but does nothing when clicked — see the
 * page object's class doc for the full detail. PDF generation is judged with {@link PdfReport}, the same
 * helper already proven on the Radiology report screens and across this module's POS_page and
 * Transfer_page screens. {@code describeControls()} is dumped into the report at each stage so anything
 * still fuzzy can be pinned exactly once this has run against the live screen.</p>
 */
public class StockAdjustmentLevel2 extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public StockAdjustmentLevel2() { super("Inventory_Others_StockAdjustmentLevel2"); }

    public static void main(String[] args) {
        StockAdjustmentLevel2 t = new StockAdjustmentLevel2();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Stock Adjustment Level 2", "Inventory > Others > Stock Adjustment Level 2",
                "Search by date range, tick a row in the list of stock, verify Stock Management Details "
                        + "populates, click Print and verify PDF report generation.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Others_page.StockAdjustmentLevel2 sal2 =
                new com.kpj.pages.Inventory_page.Others_page.StockAdjustmentLevel2(page);

        // 1) Navigate
        boolean rendered = sal2.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", sal2.lastMenu);
        step(page, "Open Stock Adjustment Level 2 screen",
                "Click Inventory -> Others -> Stock Adjustment Level 2",
                "The Stock Adjustment Level 2 screen is shown",
                rendered ? "Opened " + page.url()
                           + (sal2.lastRoute.isEmpty() ? "" : " (menu route " + sal2.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean contentLoaded = sal2.waitForRealContent(15000);
        addSummary("Content loaded", contentLoaded ? "yes" : "NO — still just the header shell after 15s");

        addSummary("Screen controls", sal2.describeControls());

        // 2) Enter From Date / To Date
        String dates = sal2.enterDateRange(from, to);
        boolean datesOk = sal2.datesEntered(from, to);
        step(page, "Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                datesOk ? "PASSES: " + dates : "FAILS: " + dates,
                datesOk ? "PASS" : "FAIL");

        // 3) Click Search
        String search = sal2.clickSearch();
        boolean rowsOk = sal2.rowsFound();
        step(page, "Click Search", "Click Search",
                "Results appear in the list of stock",
                rowsOk ? "PASSES: " + search : "FAILS: " + search,
                rowsOk ? "PASS" : "FAIL");
        if (!rowsOk) { addSummary("Result", "FAILED — no row to select"); return; }

        // 4) In list of stock, click on the tick symbol to choose any one of the row
        String rowTick = sal2.clickFirstRowTick();
        boolean rowTickOk = sal2.rowTickClicked();
        step(page, "In list of stock, click on the tick symbol to choose any one of the row",
                "Click on the tick symbol",
                "The row is selected",
                rowTickOk ? "PASSES: " + rowTick : "FAILS: " + rowTick,
                rowTickOk ? "PASS" : "FAIL");
        if (!rowTickOk) { addSummary("Result", "FAILED — no row could be selected"); return; }

        // 5) Verify selected list appears in Stock Management Details
        boolean detailsOk = sal2.detailsPopulated();
        step(page, "Verify selected list appear in stock management details",
                "Check Stock Management Details after selecting the row",
                "The selected row's items appear in Stock Management Details",
                detailsOk ? "PASSES: " + sal2.lastDetailsCount : "FAILS: " + sal2.lastDetailsCount,
                detailsOk ? "PASS" : "FAIL");

        // 6) Click Print; verify pdf generated
        int tabsBeforePrint = sal2.clickPrint();
        boolean printClickedOk = sal2.printClicked();
        step(page, "Click Print", "Click Print",
                "The Print action fires",
                printClickedOk ? "PASSES: " + sal2.lastPrint : "FAILS: " + sal2.lastPrint,
                printClickedOk ? "PASS" : "FAIL");
        if (!printClickedOk) { addSummary("Result", "FAILED — the Print button was not found"); return; }

        PdfReport.Result pdf = PdfReport.capture(page, tabsBeforePrint, 15000);
        step(page, "Verify pdf generated", "Wait for the report to open",
                "A non-blank PDF is generated", pdf.diagnostics, pdf.blank ? "FAIL" : "PASS");

        addSummary("Row selected", sal2.lastRowTick);
        addSummary("Stock Management Details", sal2.lastDetailsCount);
        addSummary("Print PDF result", pdf.diagnostics);
    }
}
