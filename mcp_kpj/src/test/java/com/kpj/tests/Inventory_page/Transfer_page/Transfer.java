package com.kpj.tests.Inventory_page.Transfer_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

// The page object is also named Transfer — referenced by its fully-qualified name.

/**
 * Inventory &gt; Transfer &gt; <b>Transfer</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Transfer</b> → <b>Transfer</b>.</li>
 *   <li><b>[Mark Unavailable]</b> Click <b>New</b>; click <b>Get Indent</b> (opens the "Item Search"
 *       dialog); click <b>Search</b>; tick the checkbox for any one item to be chosen; open
 *       <b>Mark Unavailable</b>; click <b>Mark Unavailable</b>; verify the success toast.</li>
 *   <li><b>[Mark Close]</b> Same page, a second tab: repeats New → Get Indent → search → tick with its
 *       OWN fresh item (Mark Unavailable/Mark Close are mutating actions on a specific item, not
 *       read-only report generation like the {@code POS_page} Print tabs, so this does not reuse the
 *       item Mark Unavailable already acted on); open <b>Mark Close</b>; click <b>Mark Close</b>; verify
 *       the success toast.</li>
 * </ol>
 *
 * <p>First screen in the {@code Inventory_page.Transfer_page} submodule. It has not been inspected live
 * and every control is found by FUZZY matching, applying fixes proven across the rest of the
 * {@code Inventory_page} module from the start (see {@link
 * com.kpj.pages.Inventory_page.Transfer_page.Transfer}): "Add a [new/another] tab" is handled the same
 * defensive way every other such step in this module has been (none turned out to be a real tab), and
 * button matching avoids the bare-prefix ng-click fallback that caused two collisions on {@link
 * com.kpj.pages.Inventory_page.POS_page.ItemSaleList}. {@code describeControls()} is dumped into the
 * report at each stage so anything still fuzzy can be pinned exactly once this has run against the live
 * screen.</p>
 *
 * <p>&#9888; A successful run marks TWO REAL indent items — one unavailable, one closed — in the target
 * environment.</p>
 */
public class Transfer extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public Transfer() { super("Inventory_Transfer_Transfer"); }

    public static void main(String[] args) {
        Transfer t = new Transfer();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Transfer", "Inventory > Transfer > Transfer",
                "&#9888; Marks TWO REAL indent items: [Mark Unavailable] New, Get Indent, search and "
                        + "select an item, click Mark Unavailable; then [Mark Close] repeats with a "
                        + "fresh item, click Mark Close; then [Print] on the list view: search by date "
                        + "range, select a row, click Print, verify PDF report generation; then "
                        + "[Patient Print] on the same row, click Patient Print, verify PDF generation.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Transfer_page.Transfer trf =
                new com.kpj.pages.Inventory_page.Transfer_page.Transfer(page);

        // 1) Navigate
        boolean rendered = trf.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", trf.lastMenu);
        step(page, "Open Transfer screen",
                "Click Inventory -> Transfer -> Transfer",
                "The Transfer screen is shown",
                rendered ? "Opened " + page.url()
                           + (trf.lastRoute.isEmpty() ? "" : " (menu route " + trf.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean contentLoaded = trf.waitForRealContent(15000);
        addSummary("Content loaded", contentLoaded ? "yes" : "NO — still just the header shell after 15s");

        addSummary("Screen controls", trf.describeControls());

        // 2) New
        String newResult = trf.clickNew();
        boolean newOk = trf.newClicked();
        step(page, "Click New", "Click New",
                "The entry form opens",
                newOk ? "PASSES: " + newResult : "FAILS: " + newResult,
                newOk ? "PASS" : "FAIL");
        if (!newOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        addSummary("Form controls", trf.describeControls());

        // 3) Get Indent
        String getIndent = trf.clickGetIndent();
        boolean dialogOpen = trf.dialogOpen();
        step(page, "Click Get Indent", "Click Get Indent",
                "The Item Search dialog opens",
                dialogOpen ? "PASSES: " + getIndent : "FAILS: " + getIndent,
                dialogOpen ? "PASS" : "FAIL");
        if (!dialogOpen) { addSummary("Result", "FAILED — the Item Search dialog never opened"); return; }

        addSummary("Item Search dialog controls", trf.describeControls());

        // 4) In dialog: click Search
        String dialogSearch = trf.clickSearchInDialog();
        boolean dialogRowsOk = trf.dialogItemRowsFound();
        step(page, "In Item search dialog pop up, click Search", "Click Search",
                "Item rows appear in the dialog",
                dialogRowsOk ? "PASSES: " + dialogSearch : "FAILS: " + dialogSearch,
                dialogRowsOk ? "PASS" : "FAIL");
        if (!dialogRowsOk) { addSummary("Result", "FAILED — no item to select"); return; }

        // 5) In selected item, tick checkbox to choose any one of the item
        String itemTick = trf.tickFirstItemInDialog();
        boolean itemTickOk = trf.itemTicked();
        step(page, "In selected item, tick checkbox to choose any one of the item",
                "Tick the checkbox to choose one of the items",
                "The item is selected",
                itemTickOk ? "PASSES: " + itemTick : "FAILS: " + itemTick,
                itemTickOk ? "PASS" : "FAIL");
        if (!itemTickOk) { addSummary("Result", "FAILED — no item was selected"); return; }

        // OK, if this dialog has one — best-effort/non-fatal, some pickers in this module close on tick.
        addSummary("Dialog OK (if present)", trf.clickOkInDialogIfPresent());

        addSummary("Main form controls after selection", trf.describeControls());

        // ==================== [Mark Unavailable] tab ====================

        // 6) "Add a new tab - Mark Unavailable" — reported plainly; not assumed to be a real tab.
        String markUnavailableTab = trf.clickMarkUnavailableTab();
        addSummary("\"Add a new tab - Mark Unavailable\"", markUnavailableTab);

        // 7) Click Mark Unavailable; verify success toast
        String toast = trf.clickMarkUnavailableAndGetToast();
        boolean markUnavailableClickedOk = trf.markUnavailableClicked();
        boolean success = com.kpj.pages.Inventory_page.Transfer_page.Transfer.isSuccess(toast);
        String actual = !markUnavailableClickedOk
                ? "The Mark Unavailable button was not found — " + trf.lastSaveDiagnostics
                : (toast == null || toast.isEmpty()
                    ? "No message appeared — " + trf.lastSaveDiagnostics
                    : (success ? toast : "Not confirmed — the screen answered: \"" + toast + "\""));
        step(page, "[Mark Unavailable] Click Mark Unavailable & verify success toast",
                "Click Mark Unavailable; wait for the success toast",
                "'... successfully' toast", actual, (markUnavailableClickedOk && success) ? "PASS" : "FAIL");

        addSummary("Item selected", trf.lastItemTick);
        addSummary("Mark Unavailable result", (markUnavailableClickedOk && success) ? toast : "Not confirmed (\"" + toast + "\")");

        // ==================== [Mark Close] tab (same page, a second tab) ====================
        // Mark Unavailable/Mark Close are mutating actions on a specific item (not read-only report
        // generation like the POS_page Print tabs), so this tab repeats New -> Get Indent -> search ->
        // tick with its own fresh item rather than reusing the one Mark Unavailable already acted on.

        // 8) New (fresh entry form for this tab)
        String newResult2 = trf.clickNew();
        boolean newOk2 = trf.newClicked();
        step(page, "[Mark Close] Click New", "Click New",
                "The entry form opens",
                newOk2 ? "PASSES: " + newResult2 : "FAILS: " + newResult2,
                newOk2 ? "PASS" : "FAIL");
        if (!newOk2) { addSummary("Mark Close result", "FAILED — the entry form never opened"); return; }

        // 9) Get Indent
        String getIndent2 = trf.clickGetIndent();
        boolean dialogOpen2 = trf.dialogOpen();
        step(page, "[Mark Close] Click Get Indent", "Click Get Indent",
                "The Item Search dialog opens",
                dialogOpen2 ? "PASSES: " + getIndent2 : "FAILS: " + getIndent2,
                dialogOpen2 ? "PASS" : "FAIL");
        if (!dialogOpen2) { addSummary("Mark Close result", "FAILED — the Item Search dialog never opened"); return; }

        // 10) In dialog: click Search
        String dialogSearch2 = trf.clickSearchInDialog();
        boolean dialogRowsOk2 = trf.dialogItemRowsFound();
        step(page, "[Mark Close] In Item search dialog pop up, click Search", "Click Search",
                "Item rows appear in the dialog",
                dialogRowsOk2 ? "PASSES: " + dialogSearch2 : "FAILS: " + dialogSearch2,
                dialogRowsOk2 ? "PASS" : "FAIL");
        if (!dialogRowsOk2) { addSummary("Mark Close result", "FAILED — no item to select"); return; }

        // 11) In selected item, tick checkbox to choose any one of the item
        String itemTick2 = trf.tickFirstItemInDialog();
        boolean itemTickOk2 = trf.itemTicked();
        step(page, "[Mark Close] In selected item, tick checkbox to choose any one of the item",
                "Tick the checkbox to choose one of the items",
                "The item is selected",
                itemTickOk2 ? "PASSES: " + itemTick2 : "FAILS: " + itemTick2,
                itemTickOk2 ? "PASS" : "FAIL");
        if (!itemTickOk2) { addSummary("Mark Close result", "FAILED — no item was selected"); return; }

        addSummary("[Mark Close] Dialog OK (if present)", trf.clickOkInDialogIfPresent());

        // 12) "Add another tab - Mark Close" — reported plainly; not assumed to be a real tab.
        String markCloseTab = trf.clickMarkCloseTab();
        addSummary("\"Add another tab - Mark Close\"", markCloseTab);

        // 13) Click Mark Close; verify success toast
        String closeToast = trf.clickMarkCloseAndGetToast();
        boolean markCloseClickedOk = trf.markCloseClicked();
        boolean closeSuccess = com.kpj.pages.Inventory_page.Transfer_page.Transfer.isSuccess(closeToast);
        String closeActual = !markCloseClickedOk
                ? "The Mark Close button was not found — " + trf.lastSaveDiagnostics
                : (closeToast == null || closeToast.isEmpty()
                    ? "No message appeared — " + trf.lastSaveDiagnostics
                    : (closeSuccess ? closeToast : "Not confirmed — the screen answered: \"" + closeToast + "\""));
        step(page, "[Mark Close] Click Mark Close & verify success toast",
                "Click Mark Close; wait for the success toast",
                "'... successfully' toast", closeActual, (markCloseClickedOk && closeSuccess) ? "PASS" : "FAIL");

        addSummary("Item selected (Mark Close)", trf.lastItemTick);
        addSummary("Mark Close result", (markCloseClickedOk && closeSuccess) ? closeToast : "Not confirmed (\"" + closeToast + "\")");

        // ==================== [Print] tab (same page, a third tab) ====================
        // Unlike Mark Unavailable/Mark Close, this operates on the LIST VIEW (From Date/To Date/Search/a
        // row's tick symbol), not the New-entry "Item Search" dialog — confirmed live from the screen's
        // very first control dump. Re-navigate first rather than assume Mark Close left the list view in
        // a known state.

        boolean rendered2 = trf.navigateViaMenu(BASE);
        step(page, "[Print] Return to the Transfer list", "Navigate back to Inventory -> Transfer -> Transfer",
                "The Transfer list view is shown",
                rendered2 ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                rendered2 ? "PASS" : "FAIL");
        if (!rendered2) { addSummary("Print result", "FAILED — screen not reached"); return; }

        // 14) Enter From Date / To Date
        String listDates = trf.enterListDateRange(from, to);
        boolean listDatesOk = trf.listDatesEntered(from, to);
        step(page, "[Print] Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                listDatesOk ? "PASSES: " + listDates : "FAILS: " + listDates,
                listDatesOk ? "PASS" : "FAIL");

        // 15) Click Search
        String listSearch = trf.clickListSearch();
        boolean listRowsOk = trf.listRowsFound();
        step(page, "[Print] Click Search", "Click Search",
                "Results appear in the list",
                listRowsOk ? "PASSES: " + listSearch : "FAILS: " + listSearch,
                listRowsOk ? "PASS" : "FAIL");
        if (!listRowsOk) { addSummary("Print result", "FAILED — no row to select"); return; }

        // 16) Click on the row at the tick symbol to choose any one of the item
        String rowTick = trf.clickFirstRowTick();
        boolean rowTickOk = trf.rowTickClicked();
        step(page, "[Print] Click on the row at the tick symbol to choose any one of the item",
                "Click on the row at the tick symbol",
                "The row is selected",
                rowTickOk ? "PASSES: " + rowTick : "FAILS: " + rowTick,
                rowTickOk ? "PASS" : "FAIL");
        if (!rowTickOk) { addSummary("Print result", "FAILED — no row could be selected"); return; }

        // 17) "Add another tab - Print" — reported plainly; not assumed to be a real tab.
        String printTab = trf.clickPrintTab();
        addSummary("\"Add another tab - Print\"", printTab);

        // 18) Click Print; verify pdf report generated
        int tabsBeforePrint = trf.clickPrint();
        boolean printClickedOk = trf.printClicked();
        step(page, "[Print] Click Print", "Click Print",
                "The Print action fires",
                printClickedOk ? "PASSES: " + trf.lastPrint : "FAILS: " + trf.lastPrint,
                printClickedOk ? "PASS" : "FAIL");
        if (!printClickedOk) { addSummary("Print result", "FAILED — the Print button was not found"); return; }

        PdfReport.Result pdf = PdfReport.capture(page, tabsBeforePrint, 15000);
        step(page, "[Print] Verify pdf report generated", "Wait for the report to open",
                "A non-blank PDF is generated", pdf.diagnostics, pdf.blank ? "FAIL" : "PASS");

        addSummary("Row selected (Print)", trf.lastRowTick);
        addSummary("Print PDF result", pdf.diagnostics);

        // ==================== [Patient Print] tab (same page, a fourth tab) ====================
        // Reuses the same selected row — Patient Print, like Print, is read-only report generation on
        // the list view, so there is no need to search or select a row again.

        // 19) "Add another tab - Patient Print" — reported plainly; not assumed to be a real tab.
        String patientPrintTab = trf.clickPatientPrintTab();
        addSummary("\"Add another tab - Patient Print\"", patientPrintTab);

        // 20) Click Patient Print; verify pdf report generated
        int tabsBeforePatientPrint = trf.clickPatientPrint();
        boolean patientPrintClickedOk = trf.patientPrintClicked();
        step(page, "[Patient Print] Click Patient Print", "Click Patient Print",
                "The Patient Print action fires",
                patientPrintClickedOk ? "PASSES: " + trf.lastPatientPrint : "FAILS: " + trf.lastPatientPrint,
                patientPrintClickedOk ? "PASS" : "FAIL");
        if (!patientPrintClickedOk) { addSummary("Patient Print result", "FAILED — the Patient Print button was not found"); return; }

        PdfReport.Result patientPdf = PdfReport.capture(page, tabsBeforePatientPrint, 15000);
        step(page, "[Patient Print] Verify pdf report generated", "Wait for the report to open",
                "A non-blank PDF is generated", patientPdf.diagnostics, patientPdf.blank ? "FAIL" : "PASS");

        addSummary("Patient Print PDF result", patientPdf.diagnostics);
    }
}
