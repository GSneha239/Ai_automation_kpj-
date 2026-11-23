package com.kpj.tests.AncillaryServices_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// The page object is also named ReturnItem — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; CSSD &gt; <b>Return Item</b> ({@code #/ReturnItemListCSSD} -&gt; New {@code #/add-ReturnItem}).
 *
 * <ol>
 *   <li>Click <b>New</b> (the list screen has to open the form), select the <b>Return From Store</b> (Pharmacy Main Store) and the <b>Return To Store</b> (Pharmacy (Central)).</li>
 *   <li>Click <b>Get Issue</b>; in the <b>Issue Item Search</b> dialog click <b>Search</b> and click one of the rows of <b>Issue Item List</b>.</li>
 *   <li>Verify the row's items appear in <b>Selected Item</b>; when they do not (or cannot be returned), double-click the row, then take the next issue - a LOOP.</li>
 *   <li>Tick the item, click <b>OK</b>, enter the <b>Return Quantity</b> (1), select the <b>Type Of Return</b>, enter the <b>Remarks</b>.</li>
 *   <li>Click <b>Save</b>, verify the success toast, verify the report opened in another tab.</li>
 * </ol>
 *
 * <p>Options: {@code -Drt.type=} (the Type Of Return; default "By Mistake"), {@code -Drt.qty=}.</p>
 *
 * <p>&#9888; A successful run RETURNS a real item from one store to the other: stock moves and a Return Item is created.</p>
 */
public class ReturnItem extends DevHisBase {

    public ReturnItem() { super("AncillaryServices_CSSD_ReturnItem"); }

    public static void main(String[] args) {
        ReturnItem t = new ReturnItem();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    @Override
    protected void body() {
        meta("Ancillary Services - CSSD - Return Item", "Ancillary Services > CSSD > Return Item",
                "&#9888; RETURNS a real item. Return From Store (Pharmacy Main Store), Return To Store (Pharmacy (Central)); Get Issue; in Issue Item Search click Search, click an issue row and verify its item appears in Selected Item "
                        + "(loop over other issues / double-click otherwise); tick it, OK; Return Quantity 1; Type Of Return; Remarks; Save; verify the success toast; verify the report opened in another tab.");

        final String fromStore = System.getProperty("rt.from", "Pharmacy Main Store");
        final String toStore = System.getProperty("rt.to", "Pharmacy (Central)");
        final String type = System.getProperty("rt.type", "By Mistake");
        final String qty = System.getProperty("rt.qty", "1");
        final int maxRows = 8;

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CSSD_page.ReturnItem rt = new com.kpj.pages.AncillaryServices_page.CSSD_page.ReturnItem(page);

        boolean on = rt.navigateViaMenu();
        step("Open Return Item", "Click Ancillary Services -> CSSD -> Return Item", "The Return Item list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Return Item not reached"); return; }

        // 0) New (the list has to open the form)
        boolean opened = rt.clickNew();
        step("Click New", "Click New (opens the Return Item form)", "The Return Item form is shown",
                opened ? "The form opened at " + page.url() : "Problem — " + rt.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — the form did not open: " + rt.problems); return; }

        // 1) the two stores
        String from = rt.selectFromStore(fromStore);
        step("Select From Store", "Select Return From Store \"" + fromStore + "\"", "The store is selected",
                !from.isEmpty() ? "Return From Store \"" + from + "\" selected (of " + rt.options("ReturnItem.FromStore").size() + " stores); the screen's model holds it" : "Problem — " + rt.problems, !from.isEmpty() ? "PASS" : "FAIL");
        if (from.isEmpty()) { addSummary("Result", "FAILED — From Store: " + rt.problems); return; }
        String to = rt.selectToStore(toStore);
        step("Select To Store", "Select Return To Store \"" + toStore + "\"", "The store is selected",
                !to.isEmpty() ? "Return To Store \"" + to + "\" selected (of " + rt.options("ReturnItem.ToStore").size() + " stores); the screen's model holds it" : "Problem — " + rt.problems, !to.isEmpty() ? "PASS" : "FAIL");
        if (to.isEmpty()) { addSummary("Result", "FAILED — To Store: " + rt.problems); return; }

        // 2) Get Issue -> dialog
        boolean got = rt.clickGetIssue();
        step("Click Get Issue", "Click Get Issue", "The Issue Item Search dialog opens",
                got ? "The Issue Item Search dialog opened (its stores, disabled and swapped: " + rt.dialogStores() + ")" : "Problem — " + rt.problems, got ? "PASS" : "FAIL");
        if (!got) { addSummary("Result", "FAILED — Get Issue: " + rt.problems); return; }

        int issues = rt.clickDialogSearch();
        List<String[]> rows = rt.issueRows();
        step("Click Search", "In the Issue Item Search dialog click Search", "The issues are listed in Issue Item List",
                issues > 0 ? issues + " issue(s) listed: " + rows.stream().limit(4).map(r -> r[0] + " (" + r[1] + ", " + r[3] + " -> " + r[4] + ", " + r[5] + ")").toList() + (issues > 4 ? " ..." : "")
                           : "Problem — no issue was listed " + rt.problems, issues > 0 ? "PASS" : "FAIL");
        if (issues == 0) { addSummary("Result", "FAILED — Search listed no issue"); return; }

        // 3) click a row, verify Selected Item shows its item - a loop over the issues
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
                // step 8 records the first click as asked; the loop below decides about the verification
                step("Click on one of the rows in Issue Item List", "Click one of the rows of Issue Item List", "The issue is selected",
                        rows.size() > 0 ? "Row 1 (" + rows.get(0)[0] + ", " + rows.get(0)[1] + ", " + rows.get(0)[3] + " -> " + rows.get(0)[4] + ") " + how : "Problem — " + rt.problems, "PASS");
            }
            if (usable) { rowIdx = i; break; }
        }
        boolean populated = rowIdx >= 0;
        step("Verify the selected row appears in Selected Item", "In Selected Item verify the data row of the selected issue is populated (otherwise select another issue or double-click the row - loop)",
                "Selected Item lists the item(s) of the selected issue",
                populated ? "PASSES: " + selected.stream().map(it -> it[0] + " " + it[1] + " (batch " + it[2] + ", expiry " + it[3] + ", available stock " + it[4] + ", selling price " + it[7] + ")").toList()
                        + (attempts.size() > 1 ? "; attempts: " + attempts : "; attempts: " + attempts)
                        : "FAILS: none of the first " + attempts.size() + " issues put a returnable item in Selected Item: " + attempts,
                populated ? "PASS" : "FAIL");
        if (!populated) { addSummary("Attempts", String.valueOf(attempts)); addSummary("Result", "FAILED — no issue with a returnable item"); return; }
        final String issueNo = rows.get(rowIdx)[0];
        final String[] item = selected.get(0);

        // 4) tick, OK
        boolean ticked = rt.tickSelected(0);
        step("Tick the checkbox to select it", "Tick the checkbox of the item in Selected Item", "The item is ticked",
                ticked ? "Ticked " + item[0] + " " + item[1] + "; the row's data holds it" : "Problem — " + rt.problems, ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED — tick: " + rt.problems); return; }
        int n = rt.clickDialogOk();
        step("Click OK", "Click OK in the Issue Item Search dialog", "The dialog closes and the item is added to the form",
                n > 0 ? "OK clicked; the form lists " + n + " item(s): " + rt.mainItems().stream().map(it -> it[0] + " (batch " + it[1] + ", transfer qty " + it[3] + ", received qty " + it[4] + ", available stock " + it[5] + ")").toList()
                      : "Problem — " + rt.problems, n > 0 ? "PASS" : "FAIL");
        if (n <= 0) { addSummary("Result", "FAILED — OK: " + rt.problems); return; }

        // 5) return quantity
        boolean qtyOk = rt.enterReturnQuantity(0, qty);
        String[] totals = rt.totals();
        step("Enter return quantity", "Enter Return Quantity " + qty, "The quantity is accepted",
                qtyOk ? "Return Quantity " + qty + " entered; the row's data holds it (Total Return Items " + totals[0] + ")" : "Problem — " + rt.problems, qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED — return quantity: " + rt.problems); return; }

        // 6) type of return
        String typeShown = rt.selectTypeOfReturn(type, "By Mistake", "None Remark", "Others");
        step("Select type of return", "Select Type Of Return \"" + type + "\"", "The type is selected",
                !typeShown.isEmpty() ? "Type Of Return \"" + typeShown + "\" selected (of " + rt.options("ReturnItem.TypeOfReturn").stream().filter(s -> !s.startsWith("Auto type of return")).toList() + " and "
                        + rt.options("ReturnItem.TypeOfReturn").stream().filter(s -> s.startsWith("Auto type of return")).count() + " 'Auto type of return' test entries); the screen's model holds it" : "Problem — " + rt.problems,
                !typeShown.isEmpty() ? "PASS" : "FAIL");
        if (typeShown.isEmpty()) { addSummary("Result", "FAILED — type of return: " + rt.problems); return; }

        // 7) remarks
        String remarks = "QA auto remarks - CSSD Return Item " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean remarksOk = rt.enterRemarks(remarks);
        step("Enter remarks", "Enter Remarks (" + remarks + ")", "The remarks are accepted", remarksOk ? "Remarks \"" + remarks + "\" entered; the screen's model holds them" : "Problem — " + rt.problems, remarksOk ? "PASS" : "FAIL");
        if (!remarksOk) { addSummary("Result", "FAILED — remarks: " + rt.problems); return; }

        // 8) Save
        int tabsBefore = rt.clickSave();
        boolean clicked = rt.ok();
        String toast = clicked ? rt.awaitToast(25000) : "";
        step("Click Save", "Click Save", "The item is returned", clicked ? "Save clicked" : "Problem — " + rt.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("Result", "FAILED — Save: " + rt.problems); return; }

        boolean success = rt.saveSucceeded();
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                success ? "PASSES because the toast is a success message: \"" + toast + "\"" + (rt.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + rt.lastSaveMessage.trim() + "\"")
                      : rt.savedByServer() ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED it (ResultStatus 1)"
                      : toast.isEmpty() ? "FAILS because no toast appeared — " + rt.saveDiagnosis()
                                        : "FAILS because the toast is not a success message: \"" + toast + "\" — " + rt.saveDiagnosis(),
                success ? "PASS" : "FAIL");

        // 9) the report in another tab (the save itself opens it)
        PdfReport.Result pdf = PdfReport.capture(page, tabsBefore, 20000);
        boolean reportOk = !pdf.url.isEmpty() && !pdf.blank;
        if (pdf.png != null && pdf.png.length > 0) {
            step(pdf.png, "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated",
                    pdf.diagnostics + " The screenshot is the report's first page rendered from the file itself.", reportOk ? "PASS" : "FAIL");
        } else {
            step(page, "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated", pdf.diagnostics, reportOk ? "PASS" : "FAIL");
        }

        addSummary("Return From / To Store", from + " / " + to);
        addSummary("Issue returned against", issueNo + " - " + item[0] + " " + item[1] + " (batch " + item[2] + ", available stock " + item[4] + ")");
        addSummary("Attempts", String.valueOf(attempts));
        addSummary("Return Quantity / Type Of Return", qty + " / " + typeShown);
        addSummary("Remarks", remarks);
        addSummary("Server answer", rt.savedByServer() ? "saved (ResultStatus 1" + (rt.lastTransNo.isEmpty() ? "" : ", " + rt.lastTransNo) + (rt.lastResponseId.isEmpty() ? "" : ", id " + rt.lastResponseId) + ")" : "(no save confirmed" + (rt.lastSaveMessage.isEmpty() ? "" : ": " + rt.lastSaveMessage) + ")");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Report", pdf.url.isEmpty() ? "(none)" : pdf.url);
        addSummary("Result", success ? toast : (toast.isEmpty() ? "Not confirmed" : (rt.savedByServer() ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
