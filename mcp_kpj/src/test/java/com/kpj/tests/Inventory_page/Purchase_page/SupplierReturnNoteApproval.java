package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SupplierReturnNoteApproval — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Supplier Return Note Approval</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Supplier Return Note Approval</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>Open <b>Print</b>.</li>
 *   <li>In <b>List of GRN Return</b>, click on a row at the tick symbol to choose it.</li>
 *   <li>In <b>Return Item Details</b>, verify the selected row's data is displayed.</li>
 *   <li>Click <b>Print</b> → verify report generation.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live except for its search form and the <b>List of GRN
 * Return</b> grid layout (confirmed via a screenshot): a leading tick-icon column on every row, separate
 * from that row's own <b>Approved</b>/<b>Cancel</b> checkbox columns further right. "Add a new tab -
 * Print" is handled the same defensive way as every other "add a [new/another] tab" step in this module
 * ({@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequestApproval},
 * {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrderApproval}) — none of those turned out
 * to be a real tab, only the same action button used later, and clicking it early misfired the action on
 * sibling screens; so this never clicks anything that could be the real Print button before the row is
 * selected. See {@link com.kpj.pages.Inventory_page.Purchase_page.SupplierReturnNoteApproval} for the
 * fuzzy-matching approach and the "verify report generation" check (mirrors
 * {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrderApproval} and
 * {@link com.kpj.pages.Inventory_page.Purchase_page.SupplierReturnNote}).
 * {@code describeControls()} is dumped into the report at each stage so anything still fuzzy can be
 * pinned exactly once this has run against the live screen.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=}.</p>
 */
public class SupplierReturnNoteApproval extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public SupplierReturnNoteApproval() { super("Inventory_Purchase_SupplierReturnNoteApproval"); }

    public static void main(String[] args) {
        SupplierReturnNoteApproval t = new SupplierReturnNoteApproval();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Supplier Return Note Approval", "Inventory > Purchase > Supplier Return Note Approval",
                "Search by date range, open Print, select a GRN Return row, verify its item details "
                        + "show, click Print, verify report generation.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.SupplierReturnNoteApproval srna =
                new com.kpj.pages.Inventory_page.Purchase_page.SupplierReturnNoteApproval(page);

        // 1) Navigate
        boolean rendered = srna.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", srna.lastMenu);
        step(page, "Open Supplier Return Note Approval screen",
                "Click Inventory -> Purchase -> Supplier Return Note Approval",
                "The Supplier Return Note Approval screen is shown",
                rendered ? "Opened " + page.url()
                           + (srna.lastRoute.isEmpty() ? "" : " (menu route " + srna.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("Screen controls", srna.describeControls());

        // 2) Date range
        String dates = srna.enterDateRange(from, to);
        boolean datesOk = srna.datesEntered(from, to);
        step(page, "Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                datesOk ? "PASSES: " + dates : "FAILS: " + dates,
                datesOk ? "PASS" : "FAIL");

        // 3) Search
        String search = srna.clickSearch();
        boolean rowsOk = srna.rowsFound();
        step(page, "Click Search", "Click Search",
                "Results appear in List of GRN Return",
                rowsOk ? "PASSES: " + search : "FAILS: " + search,
                rowsOk ? "PASS" : "FAIL");
        if (!rowsOk) { addSummary("Result", "FAILED — no GRN Return to select"); return; }

        addSummary("Results", srna.describeRows());

        // 4) "Add a new tab - Print" — reported plainly; not assumed to be a real tab.
        String printTab = srna.clickPrintTab();
        addSummary("\"Add a new tab - Print\"", printTab);

        // 5) In List of GRN Return, click on the row at the tick symbol
        String rowClick = srna.clickFirstRowTick();
        boolean rowClickOk = srna.rowTickClicked();
        step(page, "In List of GRN Return, click on the row at the tick symbol",
                "Click on the row at the tick symbol to choose one of the items",
                "The row is selected",
                rowClickOk ? "PASSES: " + rowClick : "FAILS: " + rowClick,
                rowClickOk ? "PASS" : "FAIL");
        if (!rowClickOk) { addSummary("Result", "FAILED — no row could be selected"); return; }

        addSummary("Controls after selecting the row", srna.describeControls());

        // 6) Verify Return Item Details shows the selected row's data
        boolean detailsShown = srna.returnItemDetailsShown();
        step(page, "In Return Item Details, verify selected row data is displayed",
                "Observe Return Item Details after selecting the row",
                "The selected row's item data appears in Return Item Details",
                detailsShown ? "PASSES: item data is shown" : "FAILS: Return Item Details is still empty",
                detailsShown ? "PASS" : "FAIL");

        // 7) Click Print; verify report generation
        String reportResult = srna.clickPrintAndVerifyReportGeneration();
        boolean printClickedOk = srna.printClicked();
        step(page, "Click Print; verify report generation", "Click Print",
                "The Print action fires and a report is generated",
                printClickedOk ? "PASSES: " + reportResult : "FAILS: " + reportResult,
                printClickedOk ? "PASS" : "FAIL");

        addSummary("Row selected", srna.lastRowClick);
        addSummary("Result", printClickedOk ? reportResult : "FAILED — the Print button was not found");
    }
}
