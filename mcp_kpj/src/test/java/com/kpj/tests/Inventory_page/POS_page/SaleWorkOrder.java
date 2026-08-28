package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

// The page object is also named SaleWorkOrder — referenced by its fully-qualified name.

/**
 * Inventory &gt; POS &gt; <b>Sale Work Order</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>POS</b> → <b>Sale Work Order</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>In <b>Sale Work Order Details</b>, tick the checkbox for any one item to be chosen.</li>
 *   <li>In <b>Drug Details</b>, tick the checkbox for the item added.</li>
 *   <li><b>[Print]</b> Open <b>Print</b>; click <b>Print</b>; verify PDF generation.</li>
 *   <li><b>[Print Drug Label]</b> Same page, a second tab: open <b>Print Drug Label</b>; click
 *       <b>Print Drug Label</b>; verify the PDF drug label is generated. Reuses the same selected row
 *       and Drug Details tick — both actions are read-only report generation, so there is no need to
 *       search again between them.</li>
 * </ol>
 *
 * <p>Sibling of {@link com.kpj.pages.Inventory_page.POS_page.ItemSaleList} in the same
 * {@code Inventory_page.POS_page} submodule — a search/report screen with no New/Save, so both tabs end
 * at PDF generation rather than a success toast. It has not been inspected live and every control is
 * found by FUZZY matching, applying fixes proven on that sibling screen from the start: "Add a
 * [new/another] tab" is handled the same defensive way every other such step in this module has been
 * (none turned out to be a real tab), both Print buttons are matched on EXACT text only (a wildcard
 * ng-click fallback caused two button collisions between these exact same two buttons on the sibling
 * screen), and PDF generation is judged with {@link PdfReport}, the same helper already proven on the
 * Radiology report screens and on that sibling. {@code describeControls()} is dumped into the report at
 * each stage so anything still fuzzy can be pinned exactly once this has run against the live screen.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=}.</p>
 */
public class SaleWorkOrder extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public SaleWorkOrder() { super("Inventory_POS_SaleWorkOrder"); }

    public static void main(String[] args) {
        SaleWorkOrder t = new SaleWorkOrder();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Sale Work Order", "Inventory > POS > Sale Work Order",
                "Search by date range, select a Sale Work Order row, tick the Drug Details item, then "
                        + "[Print] click Print and verify PDF generation, then [Print Drug Label] click "
                        + "Print Drug Label and verify the drug label PDF is generated.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.POS_page.SaleWorkOrder swo =
                new com.kpj.pages.Inventory_page.POS_page.SaleWorkOrder(page);

        // 1) Navigate
        boolean rendered = swo.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", swo.lastMenu);
        step(page, "Open Sale Work Order screen",
                "Click Inventory -> POS -> Sale Work Order",
                "The Sale Work Order screen is shown",
                rendered ? "Opened " + page.url()
                           + (swo.lastRoute.isEmpty() ? "" : " (menu route " + swo.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean contentLoaded = swo.waitForRealContent(15000);
        addSummary("Content loaded", contentLoaded ? "yes" : "NO — still just the header shell after 15s");

        addSummary("Screen controls", swo.describeControls());

        // 2) Date range
        String dates = swo.enterDateRange(from, to);
        boolean datesOk = swo.datesEntered(from, to);
        step(page, "Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                datesOk ? "PASSES: " + dates : "FAILS: " + dates,
                datesOk ? "PASS" : "FAIL");

        addSummary("Store", swo.selectStore());

        // 3) Search
        String search = swo.clickSearch();
        boolean rowsOk = swo.rowsFound();
        step(page, "Click Search", "Click Search",
                "Results appear in Sale Work Order Details",
                rowsOk ? "PASSES: " + search : "FAILS: " + search,
                rowsOk ? "PASS" : "FAIL");
        if (!rowsOk) { addSummary("Result", "FAILED — no Sale Work Order to select"); return; }

        addSummary("Results", swo.describeRows());

        // 4) In Sale Work Order Details, in select row, tick checkbox to choose any one item
        String rowTick = swo.tickFirstRowCheckbox();
        boolean rowTickOk = swo.rowChecked();
        step(page, "In select row, tick checkbox to choose any one of the item",
                "Tick the checkbox to choose one of the items",
                "The row is selected and Drug Details populates",
                rowTickOk ? "PASSES: " + rowTick : "FAILS: " + rowTick,
                rowTickOk ? "PASS" : "FAIL");
        if (!rowTickOk) { addSummary("Result", "FAILED — no row was selected"); return; }

        addSummary("Controls after selecting the row", swo.describeControls());

        // 5) In Drug Details, tick the checkbox for the item added
        boolean drugDetailsOk = swo.drugDetailsPopulated();
        addSummary("Drug Details rows", drugDetailsOk ? swo.drugDetailsRowCount() + " row(s)" : "0 rows appeared");
        String drugTick = swo.tickDrugDetailsCheckbox();
        boolean drugTickOk = swo.drugDetailsTicked();
        step(page, "In drug details, tick the checkbox for item added",
                "Tick the checkbox for the item added in Drug Details",
                "The item is selected",
                drugTickOk ? "PASSES: " + drugTick : "FAILS: " + drugTick,
                drugTickOk ? "PASS" : "FAIL");

        // ==================== [Print] tab ====================

        // 6) "Add a new tab - Print" — reported plainly; not assumed to be a real tab.
        String printTab = swo.clickPrintTab();
        addSummary("\"Add a new tab - Print\"", printTab);

        // 7) Click Print; verify PDF generation
        int tabsBefore = swo.clickPrint();
        boolean printClickedOk = swo.printClicked();
        step(page, "[Print] Click Print", "Click Print",
                "The Print action fires",
                printClickedOk ? "PASSES: " + swo.lastPrint : "FAILS: " + swo.lastPrint,
                printClickedOk ? "PASS" : "FAIL");
        if (!printClickedOk) { addSummary("Result", "FAILED — the Print button was not found"); return; }

        PdfReport.Result pdf = PdfReport.capture(page, tabsBefore, 15000);
        step(page, "[Print] Verify pdf generation", "Wait for the report to open",
                "A non-blank PDF is generated", pdf.diagnostics, pdf.blank ? "FAIL" : "PASS");

        addSummary("Row selected", swo.lastRowTick);
        addSummary("Drug Details ticks", swo.lastDrugTick);
        addSummary("Print PDF result", pdf.diagnostics);

        // ==================== [Print Drug Label] tab (same page, a second tab) ====================

        // 8) "Add another tab - Print Drug Label" — reported plainly; not assumed to be a real tab.
        String printDrugLabelTab = swo.clickPrintDrugLabelTab();
        addSummary("\"Add another tab - Print Drug Label\"", printDrugLabelTab);

        // 9) Click Print Drug Label; verify PDF generation
        int tabsBeforeDrugLabel = swo.clickPrintDrugLabel();
        boolean printDrugLabelClickedOk = swo.printDrugLabelClicked();
        step(page, "[Print Drug Label] Click Print Drug Label", "Click Print Drug Label",
                "The Print Drug Label action fires",
                printDrugLabelClickedOk ? "PASSES: " + swo.lastPrintDrugLabel : "FAILS: " + swo.lastPrintDrugLabel,
                printDrugLabelClickedOk ? "PASS" : "FAIL");
        if (!printDrugLabelClickedOk) { addSummary("Result", "FAILED — the Print Drug Label button was not found"); return; }

        PdfReport.Result drugLabelPdf = PdfReport.capture(page, tabsBeforeDrugLabel, 15000);
        step(page, "[Print Drug Label] Verify pdf generated of drug label", "Wait for the drug label PDF to open",
                "A non-blank PDF drug label is generated", drugLabelPdf.diagnostics,
                drugLabelPdf.blank ? "FAIL" : "PASS");

        addSummary("Print Drug Label PDF result", drugLabelPdf.diagnostics);
    }
}
