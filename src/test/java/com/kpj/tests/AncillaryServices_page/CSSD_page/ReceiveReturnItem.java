package com.kpj.tests.AncillaryServices_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// The page object is also named ReceiveReturnItem — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; CSSD &gt; <b>Receive Return Item</b> ({@code #/ReceiveReturnItemListCSSD} -&gt; New {@code #/add-ReceiveReturnItem}).
 *
 * <ol>
 *   <li>Click <b>New</b>; enter the <b>Received Date</b> one month before the application's current date.</li>
 *   <li>Select <b>Received From</b> (Pharmacy Main Store) and <b>Received To</b> (Pharmacy (Central)) - choosing Received To runs the search by itself, listing the returns waiting to be received.</li>
 *   <li>Click a row on its <b>Return Number</b> (the first one whose items have a quantity in transit) - its items load below.</li>
 *   <li>Enter the <b>Received Quantity</b> (it starts at 0: the quantity in transit is received), enter the <b>Remarks</b>.</li>
 *   <li>Click <b>Save</b>, verify the success toast, verify the report opened in another tab.</li>
 * </ol>
 *
 * <p>Options: {@code -Drr.months=} (months before the current date; default 1), {@code -Drr.qty=} (the Received Quantity instead of the quantity in transit).</p>
 *
 * <p>&#9888; A successful run RECEIVES a real return into its store: it stops being listed as pending and its stock moves.</p>
 */
public class ReceiveReturnItem extends DevHisBase {

    public ReceiveReturnItem() { super("AncillaryServices_CSSD_ReceiveReturnItem"); }

    public static void main(String[] args) {
        ReceiveReturnItem t = new ReceiveReturnItem();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    private static String plain(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }

    @Override
    protected void body() {
        meta("Ancillary Services - CSSD - Receive Return Item", "Ancillary Services > CSSD > Receive Return Item",
                "&#9888; RECEIVES a real return. New; Received Date one month before today; Received From (Pharmacy Main Store); Received To (Pharmacy (Central)); click a return on its Return Number; "
                        + "Received Quantity; Remarks; Save; verify the success toast; verify the report opened in another tab.");

        final int months = Integer.getInteger("rr.months", 1);
        final String fromStore = System.getProperty("rr.from", "Pharmacy Main Store");
        final String toStore = System.getProperty("rr.to", "Pharmacy (Central)");
        final String qtyOverride = System.getProperty("rr.qty", "");
        final DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveReturnItem rr = new com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveReturnItem(page);

        boolean on = rr.navigateViaMenu();
        step("Open Receive Return Item", "Click Ancillary Services -> CSSD -> Receive Return Item", "The Receive Return Item list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Receive Return Item not reached"); return; }

        // 1) New
        boolean opened = rr.clickNew();
        step("Click New", "Click New", "The Receive Return Item form is shown",
                opened ? "The form opened at " + page.url() + " (Received Date " + rr.receivedDateBox() + ")" : "Problem — " + rr.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — the form did not open: " + rr.problems); return; }

        // 2) Received Date, at least a month before the current date
        LocalDate today = rr.defaultDate();
        LocalDate target = today.minusMonths(months);
        boolean dateOk = rr.enterReceivedDate(target);
        step("Enter received date", "Enter a Received Date at least one month before the current date (" + target.format(dmy) + ")", "The date is entered",
                dateOk ? "Received Date " + rr.receivedDateBox() + " entered through the calendar (the current date is " + today.format(dmy) + ", " + months + " month(s) earlier); the screen's model holds it"
                       : "Problem — " + rr.problems, dateOk ? "PASS" : "FAIL");
        if (!dateOk) { addSummary("Result", "FAILED — received date: " + rr.problems); return; }

        // 3) the two stores - Received To runs the search
        String from = rr.selectReceivedFrom(fromStore);
        step("Select Received From", "Select Received From \"" + fromStore + "\"", "The store is selected",
                !from.isEmpty() ? "Received From \"" + from + "\" selected (of " + rr.options("ReceiveReturnItem.FromStore").size() + " stores); the screen's model holds it" : "Problem — " + rr.problems, !from.isEmpty() ? "PASS" : "FAIL");
        if (from.isEmpty()) { addSummary("Result", "FAILED — Received From: " + rr.problems); return; }
        String to = rr.selectReceivedTo(toStore);
        List<com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveReturnItem.Return> returns = rr.transfers();
        step("Select Received To", "Select Received To \"" + toStore + "\"", "The store is selected and the returns waiting to be received are listed",
                !to.isEmpty() ? "Received To \"" + to + "\" selected (of " + rr.options("ReceiveReturnItem.ToStore").size() + " stores); the screen's model holds it; choosing it ran the search by itself: " + returns.size() + " return(s) listed "
                        + returns.stream().limit(4).map(Object::toString).toList() + (returns.size() > 4 ? " ..." : "")
                        : "Problem — " + rr.problems, !to.isEmpty() && !returns.isEmpty() ? "PASS" : "FAIL");
        if (to.isEmpty()) { addSummary("Result", "FAILED — Received To: " + rr.problems); return; }
        if (returns.isEmpty()) { addSummary("Result", "FAILED — no return is waiting to be received"); return; }

        // 4) a row, clicked on its Return Number: the first whose items have a quantity in transit
        List<String> tried = new ArrayList<>();
        int rowIdx = -1;
        List<String[]> items = List.of();
        for (int i = 0; i < Math.min(returns.size(), 8); i++) {
            items = rr.clickRow(i);
            boolean usable = !items.isEmpty();
            for (String[] it : items) if (!(num(it[5]) > 0)) usable = false;
            tried.add("row " + (i + 1) + " " + returns.get(i).number + ": " + (items.isEmpty() ? "no items came" : items.size() + " item(s)" + (usable ? ", a quantity in transit on each" : ", nothing in transit on one")));
            if (usable) { rowIdx = i; break; }
        }
        boolean rowOk = rowIdx >= 0;
        com.kpj.pages.AncillaryServices_page.CSSD_page.ReceiveReturnItem.Return chosen = rowOk ? returns.get(rowIdx) : null;
        step("Click on one of the rows (Return Number column)", "Click the Return Number of one of the returns in the grid", "The return is selected and its items are listed below",
                rowOk ? "Return Number " + chosen.number + " (row " + (rowIdx + 1) + ") clicked: " + chosen + "; its items: "
                        + items.stream().map(it -> it[0] + " " + it[1] + " (batch " + it[2] + ", return qty " + it[4] + ", transit qty " + it[5] + ", received qty " + it[6] + ")").toList()
                        + (tried.size() > 1 ? "; rows tried: " + tried : "")
                      : "Problem — none of the first " + tried.size() + " returns has items in transit: " + tried,
                rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — no return with a quantity in transit"); return; }

        // 5) received quantity (starts at 0): the quantity in transit of each item
        boolean qtyOk = true;
        List<String> entered = new ArrayList<>();
        for (int i = 0; i < items.size() && qtyOk; i++) {
            String q = qtyOverride.isEmpty() ? plain(num(items.get(i)[5])) : qtyOverride;
            qtyOk = rr.enterReceivedQuantity(i, q);
            entered.add(items.get(i)[1] + " x " + q);
        }
        step("Enter received quantity", "Enter the Received Quantity of each item (it starts at 0)", "The quantity is accepted",
                qtyOk ? "Received Quantity entered: " + entered + " (the quantity in transit" + (qtyOverride.isEmpty() ? "" : ", overridden with -Drr.qty") + "); the row's data holds it" : "Problem — " + rr.problems, qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED — received quantity: " + rr.problems); return; }

        // 6) remarks
        String remarks = "QA auto remarks - CSSD Receive Return Item " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean remarksOk = rr.enterRemarks(remarks);
        step("Enter remarks", "Enter Remarks (" + remarks + ")", "The remarks are accepted", remarksOk ? "Remarks \"" + remarks + "\" entered; the screen's model holds them" : "Problem — " + rr.problems, remarksOk ? "PASS" : "FAIL");
        if (!remarksOk) { addSummary("Result", "FAILED — remarks: " + rr.problems); return; }

        // 7) Save
        final int fromId = rr.storeId("from"), toId = rr.storeId("to");
        int tabsBefore = rr.clickSave();
        boolean clicked = rr.ok();
        String toast = clicked ? rr.awaitToast(25000) : "";
        step("Click Save", "Click Save", "The return is received", clicked ? "Save clicked" : "Problem — " + rr.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("Result", "FAILED — Save: " + rr.problems); return; }

        boolean success = rr.saveSucceeded();
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                success ? "PASSES because the toast is a success message: \"" + toast + "\"" + (rr.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + rr.lastSaveMessage.trim() + "\"")
                      : rr.savedByServer() ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED it (ResultStatus 1)"
                      : toast.isEmpty() ? "FAILS because no toast appeared — " + rr.saveDiagnosis()
                                        : "FAILS because the toast is not a success message: \"" + toast + "\" — " + rr.saveDiagnosis(),
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

        // read back (not a step of the spec): the return no longer pending
        int pending = (success || rr.savedByServer()) && fromId > 0 && toId > 0 ? rr.stillPending(chosen.number, fromId, toId) : -1;

        addSummary("Received Date", target.format(dmy) + " (current date " + today.format(dmy) + ")");
        addSummary("Returns waiting before", returns.size() + " " + returns.stream().map(r -> r.number).toList());
        addSummary("Return received", chosen + " - items " + entered);
        addSummary("Received From / Received To", from + " / " + to);
        addSummary("Remarks", remarks);
        addSummary("Server answer", rr.savedByServer() ? "saved (ResultStatus 1" + (rr.lastResponseId.isEmpty() ? "" : ", id " + rr.lastResponseId) + ")" : "(no save confirmed" + (rr.lastSaveMessage.isEmpty() ? "" : ": " + rr.lastSaveMessage) + ")");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Screen after Save", page.url());
        addSummary("Return still pending afterwards", pending == 0 ? "no - " + chosen.number + " is no longer listed as waiting to be received" : pending == 1 ? "YES - " + chosen.number + " is still listed" : "(could not be read)");
        addSummary("Report", pdf.url.isEmpty() ? "(none)" : pdf.url);
        addSummary("Result", success ? toast : (toast.isEmpty() ? "Not confirmed" : (rr.savedByServer() ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
