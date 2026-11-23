package com.kpj.tests.Billing_page.Cancellation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

// The page object is also named Receipt - referenced by its fully-qualified name.

/**
 * Billing &gt; Cancellation &gt; <b>Receipt</b>. The steps are the ones a person performed in the built-in browser and that were recorded:
 *
 * <ol>
 *   <li>Sidebar: Billing &gt; Cancellation &gt; Receipt. From Date: two months back, day 6. Receipt Type = Bill Receipt. Search.</li>
 *   <li>Tick a receipt; in its payment row choose a cancellation reason and type a cancellation remark.</li>
 *   <li><b>Save</b>; verify the toast "Receipt Cancelled Successfully.".</li>
 * </ol>
 *
 * <p>The cancellation reason is a different one on every run (picked at random from the drop-down, as the user asked); the remark is generated text, not the recorded "test". Pin the reason with
 * {@code -Dreceipt.reason="Discount"}.</p>
 *
 * <p>&#9888; CANCELS A REAL RECEIPT on every run (the first receipt of the Bill Receipt list); it leaves the list, so every run consumes one receipt.</p>
 */
public class Receipt extends DevHisBase {

    private static final String PINNED_REASON = System.getProperty("receipt.reason", "").trim();

    public Receipt() { super("Billing_Cancellation_Receipt"); }

    public static void main(String[] args) {
        Receipt t = new Receipt();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Billing - Cancellation - Receipt", "Billing > Cancellation > Receipt",
                "&#9888; Cancels a REAL receipt: From Date, Receipt Type = Bill Receipt, Search, tick a receipt, cancellation reason (varies per run) + remark, Save, verify the toast and that the receipt leaves the list.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.Cancellation_page.Receipt rc = new com.kpj.pages.Billing_page.Cancellation_page.Receipt(page);

        boolean on = rc.navigateViaMenu();
        step(page, "Open Receipt cancellation", "Click Billing -> Cancellation -> Receipt", "The Receipt cancellation screen is shown",
                on ? "Opened " + page.url() + " (From " + rc.fromDate() + ", To " + rc.toDate() + ")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Receipt cancellation not reached"); return; }

        rc.problems.clear();
        String from = rc.pickFromDate(2, 6);
        step(page, "Set From Date", "Click From Date; go back two months; click day 6", "From Date is the 6th, two months back",
                rc.problems.isEmpty() ? "From Date = " + from + " (To Date " + rc.toDate() + ")" : "Problem - " + rc.problems, rc.problems.isEmpty() && from.startsWith("06/") ? "PASS" : "FAIL");

        String typeMsg = rc.pickReceiptType("Bill Receipt");
        step(page, "Select Receipt Type", "Choose \"Bill Receipt\" in Receipt Type", "Receipt Type = Bill Receipt",
                typeMsg.isEmpty() ? "Receipt Type = Bill Receipt" : "Problem - " + typeMsg, typeMsg.isEmpty() ? "PASS" : "FAIL");

        rc.problems.clear();
        int receipts = rc.clickSearch();
        step(page, "Click Search", "Click Search (fnSearchBill();)", "The receipts in the date range are listed",
                receipts > 0 ? receipts + " receipt(s) listed" : "No receipt listed" + (rc.problems.isEmpty() ? "" : " - " + rc.problems), receipts > 0 ? "PASS" : "FAIL");
        if (receipts == 0) { addSummary("Result", "FAILED - no receipts listed"); return; }

        List<String[]> before = rc.receiptRows();
        String[] target = before.get(0);
        String targetInfo = String.join(" | ", target);
        rc.problems.clear();
        int pays = rc.tickReceipt(0);
        boolean ticked = rc.receiptTicked(0);
        step(page, "Tick a receipt", "Tick the first receipt (SelectBillDetails(bill,$index))", "The receipt is ticked and its payment rows are listed",
                ticked && pays > 0 ? "Ticked " + targetInfo + "; " + pays + " payment row(s): " + rc.paymentRows() : "Problem - ticked=" + ticked + ", payment rows=" + pays + (rc.problems.isEmpty() ? "" : " " + rc.problems),
                ticked && pays > 0 ? "PASS" : "FAIL");
        if (!ticked || pays == 0) { addSummary("Result", "FAILED - receipt not selectable"); return; }

        // the reason varies per run (user: "cancellation reason dropdown can vary")
        List<String> reasons = rc.reasonOptions();
        String reason = !PINNED_REASON.isEmpty() ? PINNED_REASON : (reasons.isEmpty() ? "" : reasons.get(new Random().nextInt(reasons.size())));
        rc.problems.clear();
        String chosen = reason.isEmpty() ? "" : rc.chooseReason(reason);
        step(page, "Select the cancellation reason", "Choose a cancellation reason in the payment row (random each run" + (PINNED_REASON.isEmpty() ? "" : "; pinned") + "): \"" + reason + "\"",
                "A cancellation reason is chosen", chosen.isEmpty() ? "Problem - " + rc.problems + " (" + reasons.size() + " reasons offered)" : "Cancellation reason = " + chosen + " (one of " + reasons.size() + " offered)",
                !chosen.isEmpty() && chosen.equals(reason) ? "PASS" : "FAIL");

        String remark = "Receipt cancelled - automated test " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        rc.problems.clear();
        String typed = rc.typeRemark(remark);
        step(page, "Enter the cancellation remark", "Type \"" + remark + "\" in the payment row's cancellation remark", "The remark is accepted",
                typed.isEmpty() ? "Problem - " + rc.problems : "Cancellation remark = " + typed, typed.equals(remark) ? "PASS" : "FAIL");

        rc.problems.clear();
        String toast = rc.clickSave();
        boolean saved = toast.toLowerCase().contains("cancelled successfully") || toast.toLowerCase().contains("canceled successfully");
        step(page, "Click Save", "Click Save (fnCancelReceipts();)", "'Receipt Cancelled Successfully.' toast",
                toast.isEmpty() ? "No toast appeared" + (rc.problems.isEmpty() ? "" : " - " + rc.problems) : toast, saved ? "PASS" : "FAIL");
        addSummary("Receipt cancelled", targetInfo);
        addSummary("Reason / remark", (chosen.isEmpty() ? reason : chosen) + " / " + remark);
        addSummary("Save toast", toast.isEmpty() ? "(none)" : toast);
        if (!saved) { addSummary("Result", "FAILED - not cancelled"); return; }

        // After Save the screen empties its list (32 -> 0 on the first run), so look again with the same filters
        page.waitForTimeout(2500);
        int shown = rc.receiptRows().size();
        rc.problems.clear();
        rc.searchAgain(2, 6, "Bill Receipt");
        List<String[]> after = rc.receiptRows();
        boolean still = after.stream().anyMatch(r -> r[0].equals(target[0]));
        boolean countOk = after.size() == before.size() - 1;
        step(page, "Verify the receipt left the list", "After Save search again (same From Date and Receipt Type) and look for receipt " + target[0], "The cancelled receipt is no longer listed",
                (still ? "Receipt " + target[0] + " is STILL listed" : "Receipt " + target[0] + " is gone") + " (the list showed " + shown + " right after Save; searching again lists " + after.size() + ", it held " + before.size() + ")"
                        + (!still && !countOk ? " - the count did not drop by exactly one" : ""), still ? "FAIL" : "PASS");
        addSummary("Receipts listed", before.size() + " -> " + after.size());
        addSummary("Result", still ? "Cancelled, but still listed" : "Cancelled and removed from the list");
    }
}
