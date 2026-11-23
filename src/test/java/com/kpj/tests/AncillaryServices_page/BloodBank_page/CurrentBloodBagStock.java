package com.kpj.tests.AncillaryServices_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

// The page object is also named CurrentBloodBagStock — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>Current BloodBag Stock</b> (read-only).
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists); open <b>Ancillary Services</b> → <b>Blood Bank</b> → <b>Current BloodBag Stock</b>.</li>
 *   <li>Click <b>Search</b> and verify the data in the table: columns, every row complete, stock a non-negative whole number, no repeated
 *       Component / Blood Group, footer count equal to the rows, and what the table shows equal to the grid's data.</li>
 *   <li>Select a record (tick it).</li>
 *   <li>Click <b>Print</b> and verify the report it opens.</li>
 * </ol>
 *
 * <p>Nothing is saved. Print opens {@code frmcurrentbloodbagstockReport.aspx} for the Location with Component / Blood Group = All.</p>
 */
public class CurrentBloodBagStock extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public CurrentBloodBagStock() { super("AncillaryServices_BloodBank_CurrentBloodBagStock"); }

    public static void main(String[] args) {
        CurrentBloodBagStock t = new CurrentBloodBagStock();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Blood Bank - Current BloodBag Stock", "Ancillary Services > Blood Bank > Current BloodBag Stock",
                "Read-only: Search, verify the data in the stock table, select a record and click Print (the report must open with content).");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.BloodBank_page.CurrentBloodBagStock cs =
                new com.kpj.pages.AncillaryServices_page.BloodBank_page.CurrentBloodBagStock(page);

        // 1) Navigate
        boolean on = cs.navigateViaMenu();
        if (!on) addSummary("Menu offered", cs.lastMenu);
        step(page, "Open Current BloodBag Stock screen", "Click Ancillary Services -> Blood Bank -> Current BloodBag Stock",
                "The Current BloodBag Stock screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ") — see the menu dump", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Search
        int rows = cs.clickSearch();
        step(page, "Click Search", "Click Search", "The stock table is filled",
                rows > 0 ? "The table lists " + rows + " row(s), footer \"" + cs.footerCount() + "\"" : "FAILED — the table holds no rows (" + cs.lastSearch + ")",
                rows > 0 ? "PASS" : "FAIL");
        if (rows <= 0) { addSummary("Result", "FAILED — no stock rows"); return; }

        // 3) Verify the data in the table
        String heads = cs.headings();
        boolean headsOk = heads.toLowerCase().contains("location") && heads.toLowerCase().contains("component")
                && heads.toLowerCase().contains("blood group") && heads.toLowerCase().contains("available stock");
        String probs = cs.verifyData();
        boolean dataOk = headsOk && probs.isEmpty();
        step(page, "Verify the data in the table",
                "Check the columns and every row: Location, Component, Blood Group, a non-negative whole Available Stock, no repeated Component / Blood Group, footer count, table = grid data",
                "The table is complete and consistent",
                dataOk ? "Columns [" + heads + "]. " + cs.lastData
                       : "FAILED — " + (headsOk ? "" : "columns are [" + heads + "]; ") + probs + " | " + cs.lastData,
                dataOk ? "PASS" : "FAIL");

        // 4) Select a record
        boolean sel = cs.selectRecord();
        step(page, "Select a record", "Tick one record in the table (the one with the most stock)", "The record is selected",
                sel ? cs.lastSelect : "FAILED — " + cs.lastSelect, sel ? "PASS" : "FAIL");

        // 5) Print
        PdfReport.Result pdf = cs.clickPrintAndCapture();
        boolean tab = !pdf.url.isEmpty();
        boolean reportOk = tab && !pdf.blank;
        String evidence = pdf.diagnostics + (pdf.png != null && pdf.png.length > 0 ? " The screenshot is of the report itself." : "");
        if (pdf.png != null && pdf.png.length > 0) {
            step(pdf.png, "Click Print", "Click Print and wait for the report to open", "The report opens and carries content",
                    reportOk ? evidence : "FAILED — " + evidence, reportOk ? "PASS" : "FAIL");
        } else {
            step(page, "Click Print", "Click Print and wait for the report to open", "The report opens and carries content",
                    reportOk ? evidence : "FAILED — " + evidence, reportOk ? "PASS" : "FAIL");
        }

        addSummary("Table", cs.lastData);
        addSummary("Selected record", cs.selectedText);
        addSummary("Print report", tab ? pdf.url : "(none)");
    }
}
