package com.kpj.tests.Inventory_page.Transfer_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// The page object is also named ReturnItemFlow - referenced by its fully-qualified name.

/**
 * Inventory &gt; Transfer &gt; <b>Return Item</b> - the WRITING flow. The steps are the ones a person performed in the built-in browser and that were recorded:
 *
 * <ol>
 *   <li>Sidebar Inventory &gt; Transfer &gt; Return Item (list {@code #/ReturnItemList}); <b>New</b>.</li>
 *   <li><b>Type Of Return</b> "Others", <b>Return From Store</b> "Pharmacy Main Store", <b>Return To Store</b> "Pharmacy (Central)".</li>
 *   <li><b>Get Issue</b>; in the <b>Issue Item Search</b> dialog <b>Search</b>, click an issue row (its items appear in <b>Selected Item</b>), tick the item, <b>OK</b>.</li>
 *   <li><b>Return Quantity</b> 1, <b>Remarks</b> (mandatory for "Others"), <b>Save</b>; verify the toast and that the report opened in another tab.</li>
 * </ol>
 *
 * <p>The Remarks are generated text (the recording typed another). Options: {@code -Drt.type=}, {@code -Drt.from=}, {@code -Drt.to=}, {@code -Drt.qty=}.</p>
 *
 * <p>&#9888; A successful run RETURNS a real item: stock moves and a Return Item is created (it cannot be undone from this screen).</p>
 */
public class ReturnItemFlow extends DevHisBase {

    public ReturnItemFlow() { super("Inventory_Transfer_ReturnItemFlow"); }

    public static void main(String[] args) {
        ReturnItemFlow t = new ReturnItemFlow();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    /** A step whose screenshot is taken here with a 40 s limit (the framework's 10 s one times out now and then on the grid screens). */
    private void stepL(String name, String desc, String expected, String actual, String status) {
        byte[] png = null;
        try { png = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(40000)); } catch (Exception e) { png = null; }
        if (png != null) step(png, name, desc, expected, actual, status);
        else step(name, desc, expected, actual, status);
    }

    @Override
    protected void body() {
        final String fromStore = System.getProperty("rt.from", "Pharmacy Main Store");
        final String toStore = System.getProperty("rt.to", "Pharmacy (Central)");
        final String type = System.getProperty("rt.type", "Others");
        final String qty = System.getProperty("rt.qty", "1");
        final int maxRows = 8;

        meta("Inventory - Transfer - Return Item", "Inventory > Transfer > Return Item (#/ReturnItemList -> #/add-ReturnItem)",
                "&#9888; RETURNS a real item. New; Type Of Return (" + type + "); Return From Store (" + fromStore + "); Return To Store (" + toStore + "); Get Issue; Search; click an issue row; tick the item; OK; "
                        + "Return Quantity " + qty + "; Remarks; Save; verify the success toast; verify the report opened in another tab.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Transfer_page.ReturnItemFlow rt = new com.kpj.pages.Inventory_page.Transfer_page.ReturnItemFlow(page);

        boolean on = rt.navigateViaMenu();
        stepL("Open Inventory - Transfer - Return Item", "Click Inventory -> Transfer -> Return Item", "The Return Item list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ") - " + rt.navigationProblem, on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Return Item not reached: " + rt.navigationProblem); return; }

        boolean opened = rt.clickNew();
        stepL("Click New", "Click New (AddReturnItem())", "The Return Item form is shown",
                opened ? "The form opened at " + page.url() : "Problem - " + rt.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED - the form did not open: " + rt.problems); return; }

        // 1) Type Of Return, then the two stores (as recorded)
        String typeShown = rt.selectTypeOfReturn(type, "Others", "By Mistake");
        stepL("Select Type Of Return", "Select Type Of Return \"" + type + "\"", "The type is selected",
                !typeShown.isEmpty() ? "Type Of Return \"" + typeShown + "\" selected (of " + rt.options("ReturnItem.TypeOfReturn").stream().filter(s -> !s.startsWith("Auto type of return")).toList() + " and "
                        + rt.options("ReturnItem.TypeOfReturn").stream().filter(s -> s.startsWith("Auto type of return")).count() + " 'Auto type of return' test entries); the screen's model holds it" : "Problem - " + rt.problems,
                !typeShown.isEmpty() ? "PASS" : "FAIL");
        if (typeShown.isEmpty()) { addSummary("Result", "FAILED - type of return: " + rt.problems); return; }

        String from = rt.selectFromStore(fromStore);
        stepL("Select Return From Store", "Select Return From Store \"" + fromStore + "\"", "The store is selected",
                !from.isEmpty() ? "Return From Store \"" + from + "\" selected (of " + rt.options("ReturnItem.FromStore").size() + " stores); the screen's model holds it" : "Problem - " + rt.problems, !from.isEmpty() ? "PASS" : "FAIL");
        if (from.isEmpty()) { addSummary("Result", "FAILED - From Store: " + rt.problems); return; }
        String to = rt.selectToStore(toStore);
        stepL("Select Return To Store", "Select Return To Store \"" + toStore + "\"", "The store is selected",
                !to.isEmpty() ? "Return To Store \"" + to + "\" selected (of " + rt.options("ReturnItem.ToStore").size() + " stores); the screen's model holds it" : "Problem - " + rt.problems, !to.isEmpty() ? "PASS" : "FAIL");
        if (to.isEmpty()) { addSummary("Result", "FAILED - To Store: " + rt.problems); return; }

        // 2) Get Issue -> dialog -> Search
        boolean got = rt.clickGetIssue();
        stepL("Click Get Issue", "Click Get Issue (OpenIssuedetailsList();)", "The Issue Item Search dialog opens",
                got ? "The Issue Item Search dialog opened (its stores, disabled and swapped: " + rt.dialogStores() + ")" : "Problem - " + rt.problems, got ? "PASS" : "FAIL");
        if (!got) { addSummary("Result", "FAILED - Get Issue: " + rt.problems); return; }

        int issues = rt.clickDialogSearch();
        List<String[]> rows = rt.issueRows();
        stepL("Click Search", "In the Issue Item Search dialog click Search (SearchIssueItem())", "The issues are listed in Issue Item List",
                issues > 0 ? issues + " issue(s) listed: " + rows.stream().limit(4).map(r -> r[0] + " (" + r[1] + ", " + r[3] + " -> " + r[4] + ", " + r[5] + ")").toList() + (issues > 4 ? " ..." : "")
                           : "Problem - no issue was listed " + rt.problems, issues > 0 ? "PASS" : "FAIL");
        if (issues == 0) { addSummary("Result", "FAILED - Search listed no issue"); return; }

        // 3) click an issue row; its items must reach Selected Item (loop over the issues when one has nothing returnable)
        List<String> attempts = new ArrayList<>();
        int rowIdx = -1;
        List<String[]> selected = List.of();
        for (int i = 0; i < Math.min(issues, maxRows); i++) {
            selected = rt.clickIssueRow(i, false);
            String how = "clicked";
            if (selected.isEmpty()) { selected = rt.clickIssueRow(i, true); how = "clicked, then double-clicked"; }
            boolean usable = false;
            for (String[] it : selected) if (num(it[4]) >= num(qty)) usable = true;
            attempts.add("row " + (i + 1) + " " + rows.get(i)[0] + " " + how + ": Selected Item shows " + (selected.isEmpty() ? "nothing" : selected.size() + " row(s) " + selected.stream().map(it -> it[1] + " batch " + it[2] + " stock " + it[4]).toList())
                    + (!selected.isEmpty() && !usable ? " - no stock to return" : ""));
            if (i == 0) {
                stepL("Click on one of the rows in Issue Item List", "Click one of the rows of Issue Item List", "The issue is selected",
                        "Row 1 (" + rows.get(0)[0] + ", " + rows.get(0)[1] + ", " + rows.get(0)[3] + " -> " + rows.get(0)[4] + ") " + how, "PASS");
            }
            if (usable) { rowIdx = i; break; }
        }
        boolean populated = rowIdx >= 0;
        stepL("Verify the selected row appears in Selected Item", "In Selected Item verify the data row of the selected issue is populated (otherwise another issue / double-click - loop)",
                "Selected Item lists the item(s) of the selected issue",
                populated ? "PASSES: " + selected.stream().map(it -> it[0] + " " + it[1] + " (batch " + it[2] + ", expiry " + it[3] + ", available stock " + it[4] + ", selling price " + it[7] + ")").toList() + "; attempts: " + attempts
                          : "FAILS: none of the first " + attempts.size() + " issues put a returnable item in Selected Item: " + attempts,
                populated ? "PASS" : "FAIL");
        if (!populated) { addSummary("Attempts", String.valueOf(attempts)); addSummary("Result", "FAILED - no issue with a returnable item"); return; }
        final String issueNo = rows.get(rowIdx)[0];
        final String[] item = selected.get(0);

        // 4) tick, OK
        boolean ticked = rt.tickSelected(0);
        stepL("Tick the checkbox to select it", "Tick the checkbox of the item in Selected Item", "The item is ticked",
                ticked ? "Ticked " + item[0] + " " + item[1] + "; the row's data holds it" : "Problem - " + rt.problems, ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED - tick: " + rt.problems); return; }
        int n = rt.clickDialogOk();
        stepL("Click OK", "Click OK in the Issue Item Search dialog (OKClick();)", "The dialog closes and the item is added to the form",
                n > 0 ? "OK clicked; the form lists " + n + " item(s): " + rt.mainItems().stream().map(it -> it[0] + " (batch " + it[1] + ", transfer qty " + it[3] + ", received qty " + it[4] + ", available stock " + it[5] + ")").toList()
                      : "Problem - " + rt.problems, n > 0 ? "PASS" : "FAIL");
        if (n <= 0) { addSummary("Result", "FAILED - OK: " + rt.problems); return; }

        // 5) Return Quantity, Remarks
        boolean qtyOk = rt.enterReturnQuantity(0, qty);
        String[] totals = rt.totals();
        stepL("Enter return quantity", "Enter Return Quantity " + qty, "The quantity is accepted",
                qtyOk ? "Return Quantity " + qty + " entered; the row's data holds it (Total Return Items " + totals[0] + ")" : "Problem - " + rt.problems, qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED - return quantity: " + rt.problems); return; }

        String remarks = "Inventory return item automated test " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean remarksOk = rt.enterRemarks(remarks);
        stepL("Enter remarks", "Enter Remarks \"" + remarks + "\" (generated; the recording typed another)", "The remarks are accepted (mandatory for the type \"Others\")",
                remarksOk ? "Remarks \"" + remarks + "\" entered; the screen's model holds them" : "Problem - " + rt.problems, remarksOk ? "PASS" : "FAIL");
        if (!remarksOk) { addSummary("Result", "FAILED - remarks: " + rt.problems); return; }

        // 6) Save
        int tabsBefore = rt.clickSave();
        boolean clicked = rt.ok();
        String toast = clicked ? rt.awaitToast(25000) : "";
        stepL("Click Save", "Click Save (IUDSave();)", "The item is returned", clicked ? "Save clicked" : "Problem - " + rt.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("Result", "FAILED - Save: " + rt.problems); return; }

        boolean success = rt.saveSucceeded();
        stepL("Verify success toast message", "Read the toast shown after Save", "A success toast appears (\"Return Item added successfully.\")",
                success ? "PASSES because the toast is a success message: \"" + toast + "\"" + (rt.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + rt.lastSaveMessage.trim() + "\"")
                      : rt.savedByServer() ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED it (ResultStatus 1)"
                      : toast.isEmpty() ? "FAILS because no toast appeared - " + rt.saveDiagnosis()
                                        : "FAILS because the toast is not a success message: \"" + toast + "\" - " + rt.saveDiagnosis(),
                success ? "PASS" : "FAIL");

        // 7) the report in another tab (the save itself opens it - the recording shows window.open of frmInvreturnfromstoreReport.aspx)
        PdfReport.Result pdf = PdfReport.capture(page, tabsBefore, 20000);
        boolean reportOk = !pdf.url.isEmpty() && !pdf.blank;
        if (pdf.png != null && pdf.png.length > 0) {
            step(pdf.png, "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated",
                    pdf.diagnostics + " The screenshot is the report's first page rendered from the file itself.", reportOk ? "PASS" : "FAIL");
        } else {
            step(page, "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated", pdf.diagnostics, reportOk ? "PASS" : "FAIL");
        }

        addSummary("Type Of Return", typeShown);
        addSummary("Return From / To Store", from + " / " + to);
        addSummary("Issue returned against", issueNo + " - " + item[0] + " " + item[1] + " (batch " + item[2] + ", available stock " + item[4] + ")");
        addSummary("Attempts", String.valueOf(attempts));
        addSummary("Return Quantity", qty);
        addSummary("Remarks", remarks);
        addSummary("Server answer", rt.savedByServer() ? "saved (ResultStatus 1" + (rt.lastTransNo.isEmpty() ? "" : ", " + rt.lastTransNo) + (rt.lastResponseId.isEmpty() ? "" : ", id " + rt.lastResponseId) + ")" : "(no save confirmed" + (rt.lastSaveMessage.isEmpty() ? "" : ": " + rt.lastSaveMessage) + ")");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Report", pdf.url.isEmpty() ? "(none)" : pdf.url);
        addSummary("Result", success ? toast : (toast.isEmpty() ? "Not confirmed" : (rt.savedByServer() ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
