package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

// The page object is also named ItemSaleList — referenced by its fully-qualified name.

/**
 * Inventory &gt; POS &gt; <b>Item Sale List</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>POS</b> → <b>Item Sale List</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>In the list of item sales, tick the checkbox for any one item to be chosen.</li>
 *   <li><b>[Print Drug Label]</b> Open <b>Print Drug Label</b>; click <b>Print Drug Label</b>; verify a
 *       PDF drug label is generated.</li>
 *   <li><b>[Print]</b> Same page, a second tab: open <b>Print</b>; click <b>Print</b>; verify a report
 *       is generated. Reuses the same selected row — both actions are read-only report generation, so
 *       there is no need to search again between them.</li>
 * </ol>
 *
 * <p>This is the first screen in the {@code Inventory_page.POS_page} submodule — a search/report screen
 * with no New/Save, so both tabs end at report/PDF generation rather than a success toast. It has not
 * been inspected live and every control is found by FUZZY matching, reusing proven patterns from the rest
 * of this module (see {@link com.kpj.pages.Inventory_page.POS_page.ItemSaleList}): "Add a [new/another]
 * tab" is handled the same defensive way every other such step in this module has been (none turned out
 * to be a real tab), and generation is judged with {@link PdfReport}, the same helper already proven on
 * the Radiology report screens — it fetches the newly opened tab's bytes and checks its {@code %PDF}
 * header, byte count and content streams, since Chrome's PDF viewer exposes nothing a screenshot or
 * innerText check could read. {@code describeControls()} is dumped into the report at each stage so
 * anything still fuzzy can be pinned exactly once this has run against the live screen.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=}.</p>
 */
public class ItemSaleList extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ItemSaleList() { super("Inventory_POS_ItemSaleList"); }

    public static void main(String[] args) {
        ItemSaleList t = new ItemSaleList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Item Sale List", "Inventory > POS > Item Sale List",
                "Search by date range, select an item sale row, then [Print Drug Label] click Print Drug "
                        + "Label and verify the PDF drug label is generated, then [Print] click Print and "
                        + "verify a report is generated.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.POS_page.ItemSaleList isl =
                new com.kpj.pages.Inventory_page.POS_page.ItemSaleList(page);

        java.util.List<String> consoleErrors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        page.onConsoleMessage(m -> {
            if ("error".equals(m.type())) consoleErrors.add(m.text());
        });
        page.onPageError(err -> consoleErrors.add("pageerror: " + err));

        // 1) Navigate
        boolean rendered = isl.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", isl.lastMenu);
        step(page, "Open Item Sale List screen",
                "Click Inventory -> POS -> Item Sale List",
                "The Item Sale List screen is shown",
                rendered ? "Opened " + page.url()
                           + (isl.lastRoute.isEmpty() ? "" : " (menu route " + isl.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Confirmed live: this screen briefly renders nothing but its header shell right after
        // navigation, noticeably longer than the fixed wait every other screen in this module needs.
        boolean contentLoaded = isl.waitForRealContent(15000);
        addSummary("Content loaded", contentLoaded ? "yes" : "NO — still just the header shell after 15s");
        addSummary("Console errors since navigation",
                consoleErrors.isEmpty() ? "(none)" : String.join(" | ", consoleErrors));

        addSummary("Screen controls", isl.describeControls());

        // 2) Date range
        String dates = isl.enterDateRange(from, to);
        boolean datesOk = isl.datesEntered(from, to);
        step(page, "Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                datesOk ? "PASSES: " + dates : "FAILS: " + dates,
                datesOk ? "PASS" : "FAIL");

        // Confirmed live: Search returned zero rows across the full date range with Store left blank —
        // select a store first. Best-effort/non-fatal: reported plainly either way rather than blocking.
        String storeResult = isl.selectStore();
        addSummary("Store", storeResult);
        addSummary("Scope", isl.selectAllScope());

        // 3) Search
        java.util.List<String> searchRequests = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Request> reqHandler = req -> {
            if (!"get".equalsIgnoreCase(req.method()) || req.url().toLowerCase().contains("salereturn")
                    || req.url().toLowerCase().contains("itemsale")) {
                searchRequests.add(req.method() + " " + req.url());
            }
        };
        page.onRequest(reqHandler);
        java.util.List<String> searchResponses = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> respHandler = resp -> {
            String u = resp.url().toLowerCase();
            if (u.contains("salereturn") || u.contains("itemsale")) {
                try { searchResponses.add(resp.status() + " " + resp.url() + " -> "
                        + resp.text().substring(0, Math.min(300, resp.text().length()))); }
                catch (Exception e) { searchResponses.add(resp.status() + " " + resp.url() + " -> (body unreadable)"); }
            }
        };
        page.onResponse(respHandler);

        String search = isl.clickSearch();
        page.offRequest(reqHandler);
        page.offResponse(respHandler);
        addSummary("Search network requests", searchRequests.isEmpty() ? "(none)" : String.join(" | ", searchRequests));
        addSummary("Search network responses", searchResponses.isEmpty() ? "(none)" : String.join(" || ", searchResponses));

        boolean rowsOk = isl.rowsFound();
        step(page, "Click Search", "Click Search",
                "Results appear in the list of item sales",
                rowsOk ? "PASSES: " + search : "FAILS: " + search,
                rowsOk ? "PASS" : "FAIL");
        if (!rowsOk) { addSummary("Result", "FAILED — no item sale to select"); return; }

        addSummary("Results", isl.describeRows());

        // 4) In list of item sales, tick the checkbox to choose any one item
        String rowTick = isl.tickFirstRowCheckbox();
        boolean rowTickOk = isl.rowChecked();
        step(page, "In select row, tick checkbox to choose any one of the item",
                "Tick the checkbox to choose one of the items",
                "The item row is selected",
                rowTickOk ? "PASSES: " + rowTick : "FAILS: " + rowTick,
                rowTickOk ? "PASS" : "FAIL");
        if (!rowTickOk) { addSummary("Result", "FAILED — no row was selected"); return; }

        // ==================== [Print Drug Label] tab ====================

        // 5) "Add a new tab - Print Drug Label" — reported plainly; not assumed to be a real tab.
        String printTab = isl.clickPrintDrugLabelTab();
        addSummary("\"Add a new tab - Print Drug Label\"", printTab);

        // 6) Click Print Drug Label; verify PDF generation
        int tabsBefore = isl.clickPrintDrugLabel();
        boolean printClickedOk = isl.printClicked();
        step(page, "[Print Drug Label] Click Print Drug Label", "Click Print Drug Label",
                "The Print Drug Label action fires",
                printClickedOk ? "PASSES: " + isl.lastPrint : "FAILS: " + isl.lastPrint,
                printClickedOk ? "PASS" : "FAIL");
        if (!printClickedOk) { addSummary("Result", "FAILED — the Print Drug Label button was not found"); return; }

        PdfReport.Result drugLabelPdf = PdfReport.capture(page, tabsBefore, 15000);
        step(page, "[Print Drug Label] Verify pdf generation with drug label",
                "Wait for the drug label PDF to open",
                "A non-blank PDF drug label is generated", drugLabelPdf.diagnostics,
                drugLabelPdf.blank ? "FAIL" : "PASS");

        addSummary("Row selected", isl.lastRowTick);
        addSummary("Print Drug Label PDF result", drugLabelPdf.diagnostics);

        // ==================== [Print] tab (same page, a second tab) ====================

        // 7) "Add another tab - Print" — reported plainly; not assumed to be a real tab.
        String printOnlyTab = isl.clickPrintTab();
        addSummary("\"Add another tab - Print\"", printOnlyTab);

        // 8) Click Print; verify report generation
        int tabsBeforePrint = isl.clickPrint();
        boolean printOnlyClickedOk = isl.printOnlyClicked();
        step(page, "[Print] Click Print", "Click Print",
                "The Print action fires",
                printOnlyClickedOk ? "PASSES: " + isl.lastPrintOnly : "FAILS: " + isl.lastPrintOnly,
                printOnlyClickedOk ? "PASS" : "FAIL");
        if (!printOnlyClickedOk) { addSummary("Result", "FAILED — the Print button was not found"); return; }

        PdfReport.Result printPdf = PdfReport.capture(page, tabsBeforePrint, 15000);
        step(page, "[Print] Verify report generated", "Wait for the report to open",
                "A non-blank report is generated", printPdf.diagnostics, printPdf.blank ? "FAIL" : "PASS");

        addSummary("Print report result", printPdf.diagnostics);
    }
}
