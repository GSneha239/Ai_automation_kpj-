package com.kpj.tests.AncillaryServices_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// The page object is also named ReceiveIssueItem — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; CSSD &gt; <b>Receive Issue Item</b> ({@code #/ReceiveIssueItemList} -&gt; New {@code #/add-ReceiveIssueItem}).
 *
 * <ol>
 *   <li>Click <b>New</b>.</li>
 *   <li>Enter the <b>Received Date</b> - one month before the application's current date (the start of the search range).</li>
 *   <li>Click <b>Search</b> (it lists the transfers still waiting to be received, whatever the stores).</li>
 *   <li>Click one of the rows (the first one whose items can be received) - its items load below.</li>
 *   <li>Select <b>Transferred From</b> and <b>Received To</b> (the transfer's own From / To store - the click does not fill them), enter the <b>Remarks</b>.</li>
 *   <li>Click <b>Save</b>, verify the success toast, verify the report opened in another tab.</li>
 * </ol>
 *
 * <p>Options: {@code -Dri.months=} (how many months before the current date the Received Date is; default 1).</p>
 *
 * <p>&#9888; A successful run RECEIVES a real transfer into its store: it stops being listed as pending and its stock moves.</p>
 */
public class ReceiveIssueItem extends DevHisBase {

    public ReceiveIssueItem() { super("AncillaryServices_CSSD_ReceiveIssueItem"); }

    public static void main(String[] args) {
        ReceiveIssueItem t = new ReceiveIssueItem();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    @Override
    protected void body() {
        meta("Ancillary Services - CSSD - Receive Issue Item", "Ancillary Services > CSSD > Receive Issue Item",
                "&#9888; RECEIVES a real transfer. New; Received Date one month before today; Search; click a transfer row; select Transferred From and Received To; Remarks; Save; verify the success toast; verify the report opened in another tab.");

        final int months = Integer.getInteger("ri.months", 1);
        final DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveIssueItem ri = new com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveIssueItem(page);

        boolean on = ri.navigateViaMenu();
        step("Open Receive Issue Item", "Click Ancillary Services -> CSSD -> Receive Issue Item", "The Receive Issue Item list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Receive Issue Item not reached"); return; }

        // 1) New
        boolean opened = ri.clickNew();
        step("Click New", "Click New", "The Receive Issue Item form is shown",
                opened ? "The form opened at " + page.url() + " (Received Date " + ri.receivedDateBox() + ")" : "Problem — " + ri.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — the form did not open: " + ri.problems); return; }

        // 2) Received Date, at least a month before the current date
        LocalDate today = ri.defaultDate();
        LocalDate target = today.minusMonths(months);
        boolean dateOk = ri.enterReceivedDate(target);
        step("Enter received date", "Enter a Received Date at least one month before the current date (" + target.format(dmy) + ")", "The date is entered",
                dateOk ? "Received Date " + ri.receivedDateBox() + " entered through the calendar (the current date is " + today.format(dmy) + ", " + months + " month(s) earlier); the screen's model holds it"
                       : "Problem — " + ri.problems, dateOk ? "PASS" : "FAIL");
        if (!dateOk) { addSummary("Result", "FAILED — received date: " + ri.problems); return; }

        // 3) Search
        int found = ri.clickSearch();
        List<com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveIssueItem.Transfer> transfers = ri.transfers();
        step("Click Search", "Click Search", "The transfers waiting to be received are listed",
                found > 0 ? found + " transfer(s) listed from " + ri.receivedDateBox() + " to " + today.format(dmy) + ": " + transfers.stream().limit(4).map(Object::toString).toList() + (found > 4 ? " ..." : "")
                          : "Problem — no transfer was listed " + ri.problems, found > 0 ? "PASS" : "FAIL");
        if (found == 0) { addSummary("Result", "FAILED — Search listed no transfer"); return; }

        // 4) one of the rows: the first whose items can be received
        List<String> tried = new ArrayList<>();
        int rowIdx = -1;
        List<String[]> items = List.of();
        for (int i = 0; i < Math.min(found, 8); i++) {
            items = ri.clickRow(i);
            boolean usable = !items.isEmpty();
            for (String[] it : items) if (!(num(it[6]) > 0)) usable = false;
            com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveIssueItem.Transfer t = transfers.get(i);
            tried.add("row " + (i + 1) + " " + t.number + ": " + (items.isEmpty() ? "no items came" : items.size() + " item(s)" + (usable ? ", every Received Quantity above 0" : ", a Received Quantity is 0")));
            if (usable) { rowIdx = i; break; }
        }
        boolean rowOk = rowIdx >= 0;
        com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveIssueItem.Transfer chosen = rowOk ? transfers.get(rowIdx) : null;
        step("Click on one of the rows to select it", "Click one of the transfers in the grid", "The transfer is selected and its items are listed below",
                rowOk ? "Row " + (rowIdx + 1) + " clicked: " + chosen + "; its items: " + items.stream().map(it -> it[0] + " (batch " + it[2] + ", transfer qty " + it[4] + ", transit qty " + it[5] + ", received qty " + it[6] + ")").toList()
                        + (tried.size() > 1 ? "; rows tried: " + tried : "")
                      : "Problem — none of the first " + tried.size() + " rows has items that can be received: " + tried,
                rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — no transfer with receivable items"); return; }

        // 5) the two stores (the click does not fill them): the transfer's own From / To store
        String from = ri.selectTransferredFrom(chosen.from);
        step("Select Transferred From", "Select Transferred From \"" + chosen.from + "\" (the transfer's From Store)", "The store is selected",
                !from.isEmpty() ? "Transferred From \"" + from + "\" selected (of " + ri.options("ReceiveIssueItem.SearchStoreID").size() + " stores); the screen's model holds it" : "Problem — " + ri.problems, !from.isEmpty() ? "PASS" : "FAIL");
        if (from.isEmpty()) { addSummary("Result", "FAILED — Transferred From: " + ri.problems); return; }
        String to = ri.selectReceivedTo(chosen.to);
        step("Select Received To", "Select Received To \"" + chosen.to + "\" (the transfer's To Store)", "The store is selected",
                !to.isEmpty() ? "Received To \"" + to + "\" selected (of " + ri.options("ReceiveIssueItem.FromStore").size() + " stores); the screen's model holds it" : "Problem — " + ri.problems, !to.isEmpty() ? "PASS" : "FAIL");
        if (to.isEmpty()) { addSummary("Result", "FAILED — Received To: " + ri.problems); return; }

        // 6) remarks
        String remarks = "QA auto remarks - CSSD Receive Issue Item " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean remarksOk = ri.enterRemarks(remarks);
        step("Enter remarks", "Enter Remarks (" + remarks + ")", "The remarks are accepted", remarksOk ? "Remarks \"" + remarks + "\" entered; the screen's model holds them" : "Problem — " + ri.problems, remarksOk ? "PASS" : "FAIL");
        if (!remarksOk) { addSummary("Result", "FAILED — remarks: " + ri.problems); return; }

        // 7) Save
        int tabsBefore = ri.clickSave();
        boolean clicked = ri.ok();
        String toast = clicked ? ri.awaitToast(25000) : "";
        step("Click Save", "Click Save", "The transfer is received",
                clicked ? "Save clicked" + (ri.lastConfirm.isEmpty() ? "" : " (the screen asked \"" + ri.lastConfirm + "\" - answered Yes)") : "Problem — " + ri.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("Result", "FAILED — Save: " + ri.problems); return; }

        boolean success = ri.saveSucceeded();
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                success ? "PASSES because the toast is a success message: \"" + toast + "\"" + (ri.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + ri.lastSaveMessage.trim() + "\"")
                      : ri.savedByServer() ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED it (ResultStatus 1)"
                      : toast.isEmpty() ? "FAILS because no toast appeared — " + ri.saveDiagnosis()
                                        : "FAILS because the toast is not a success message: \"" + toast + "\" — " + ri.saveDiagnosis(),
                success ? "PASS" : "FAIL");

        // 8) the report in another tab (the save itself opens it)
        PdfReport.Result pdf = PdfReport.capture(page, tabsBefore, 20000);
        boolean reportOk = !pdf.url.isEmpty() && !pdf.blank;
        if (pdf.png != null && pdf.png.length > 0) {
            step(pdf.png, "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated",
                    pdf.diagnostics + " The screenshot is the report's first page rendered from the file itself.", reportOk ? "PASS" : "FAIL");
        } else {
            step(page, "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated", pdf.diagnostics, reportOk ? "PASS" : "FAIL");
        }

        // read back (not a step of the spec): back on the list, and the transfer no longer pending
        boolean back = success && ri.backOnList(8000);
        int pending = (success || ri.savedByServer()) ? ri.stillPending(chosen.number, target) : -1;

        addSummary("Received Date", target.format(dmy) + " (current date " + today.format(dmy) + ")");
        addSummary("Transfers listed", String.valueOf(found));
        addSummary("Transfer received", chosen + " - items " + items.stream().map(it -> it[0] + " x " + it[6]).toList());
        addSummary("Transferred From / Received To", from + " / " + to);
        addSummary("Remarks", remarks);
        addSummary("Save confirmation", ri.lastConfirm.isEmpty() ? "(none asked)" : ri.lastConfirm);
        addSummary("Server answer", ri.savedByServer() ? "saved (ResultStatus 1" + (ri.lastResponseId.isEmpty() ? "" : ", id " + ri.lastResponseId) + ")" : "(no save confirmed" + (ri.lastSaveMessage.isEmpty() ? "" : ": " + ri.lastSaveMessage) + ")");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Back on the list afterwards", success ? (back ? "yes - " + page.url() : "no (" + page.url() + ")") : "n/a");
        addSummary("Transfer still pending afterwards", pending == 0 ? "no - " + chosen.number + " is no longer listed as waiting to be received" : pending == 1 ? "YES - " + chosen.number + " is still listed" : "(could not be read)");
        addSummary("Report", pdf.url.isEmpty() ? "(none)" : pdf.url);
        addSummary("Result", success ? toast : (toast.isEmpty() ? "Not confirmed" : (ri.savedByServer() ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
