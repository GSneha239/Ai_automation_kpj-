package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named CreditNote - referenced by its fully-qualified name.

/**
 * Billing &gt; <b>Credit Note</b>. The steps are the ones a person performed in the built-in browser and that were recorded:
 *
 * <ol>
 *   <li>Sidebar: Billing &gt; Credit Note. From Date: previous month, the 5th. Search.</li>
 *   <li>Click a bill (OPB000000521 when listed), tick a service.</li>
 *   <li>Credit Note To = Patient; Credit Note Reason = AmendBill-DR; Credit Note Code Paymode = "Auto credit note paymode 261111124615"; type the credit note amount 10 for the service; <b>Save</b>.</li>
 *   <li>Verify the toast "Credit Note Saved Successfully."; click <b>View Credit Note</b> and verify the credit note report is generated in another tab.</li>
 * </ol>
 *
 * <p>&#9888; SAVES A REAL CREDIT NOTE on every run (it takes the amount from the bill's "Amount Available For Credit Note"). No free text is typed on this screen, so nothing needed varying.</p>
 */
public class CreditNote extends DevHisBase {

    private static final String BILL = System.getProperty("creditnote.bill", "OPB000000521");
    private static final String AMOUNT = System.getProperty("creditnote.amount", "10");
    private static final String REASON = "AmendBill-DR";
    private static final String PAYMODE = "Auto credit note paymode 261111124615";

    public CreditNote() { super("Billing_CreditNote"); }

    public static void main(String[] args) {
        CreditNote t = new CreditNote();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Billing - Credit Note", "Billing > Credit Note",
                "&#9888; Saves a REAL credit note: From Date, Search, pick a bill, tick a service, Credit Note To / Reason / Code Paymode, amount, Save, verify the toast; View Credit Note opens the report in another tab.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.CreditNote cn = new com.kpj.pages.Billing_page.CreditNote(page);

        boolean on = cn.navigateViaMenu();
        step(page, "Open Credit Note", "Click Billing -> Credit Note", "The Credit Note screen is shown",
                on ? "Opened " + page.url() + " (From " + cn.fromDate() + ", To " + cn.toDate() + ")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Credit Note not reached"); return; }

        cn.problems.clear();
        String from = cn.pickFromDate(1, 5);
        step(page, "Set From Date", "Click From Date; go back one month; click day 5", "From Date is the 5th of last month",
                cn.problems.isEmpty() ? "From Date = " + from + " (To Date " + cn.toDate() + ")" : "Problem - " + cn.problems, cn.problems.isEmpty() && from.startsWith("05/") ? "PASS" : "FAIL");

        cn.problems.clear();
        int bills = cn.clickSearch();
        step(page, "Click Search", "Click Search (fnFillBillDetails();)", "The bills in the date range are listed",
                bills > 0 ? bills + " bill(s) listed" : "No bill listed" + (cn.problems.isEmpty() ? "" : " - " + cn.problems), bills > 0 ? "PASS" : "FAIL");
        if (bills == 0) { addSummary("Result", "FAILED - no bills listed"); return; }

        // the bill the person clicked; otherwise the first self-pay bill
        String bill = BILL;
        List<String[]> rows = cn.billRows();
        boolean listed = rows.stream().anyMatch(r -> r[1].equals(BILL));
        if (!listed) {
            for (String[] r : rows) { if (!r[10].replace(",", "").trim().equals("0.00") && r[11].replace(",", "").trim().equals("0.00")) { bill = r[1]; break; } }
        }
        cn.problems.clear();
        int services = cn.selectBill(bill);
        String billInfo = "";
        for (String[] r : rows) if (r[1].equals(bill)) billInfo = r[0] + " " + r[1] + " MRN " + r[2] + " " + r[3] + " " + r[4] + " (" + r[5] + ", net " + r[9] + ")";
        step(page, "Click the bill", "Click the bill row " + bill + (listed ? "" : " (the recorded bill is not listed - first self-pay bill used)"), "The bill's services are listed",
                services > 0 ? billInfo + "; " + services + " service(s): " + cn.serviceRows() : "No services listed" + (cn.problems.isEmpty() ? "" : " - " + cn.problems), services > 0 ? "PASS" : "FAIL");
        if (services == 0) { addSummary("Result", "FAILED - no services"); return; }

        // as recorded: tick a service first (the screen answers "Please select Credit Note To!" until the drop-downs are set)
        String tickToast = cn.tickService(0);
        boolean ticked = cn.serviceTicked(0);
        step(page, "Tick a service", "Tick the first service (GetSelectedServices(charge))", "The service is ticked",
                (ticked ? "Ticked: " + cn.serviceRows().get(0) : "Not ticked") + (tickToast.isEmpty() ? "" : " | screen said: " + tickToast), ticked ? "PASS" : "FAIL");

        cn.problems.clear();
        String to = cn.choose("PatientData.RefundTo", "Patient");
        step(page, "Select Credit Note To", "Choose \"Patient\" in Credit Note To*", "Credit Note To = Patient", to.isEmpty() ? "Problem - " + cn.problems : "Credit Note To = " + to, to.equals("Patient") ? "PASS" : "FAIL");

        cn.problems.clear();
        String reason = cn.choose("PatientData.CreditNoteReasonId", REASON);
        step(page, "Select Credit Note Reason", "Choose \"" + REASON + "\" in Credit Note Reason*", "Credit Note Reason = " + REASON,
                reason.isEmpty() ? "Problem - " + cn.problems : "Credit Note Reason = " + reason, reason.equals(REASON) ? "PASS" : "FAIL");

        cn.problems.clear();
        List<String> pay = cn.options("PatientData.CNCodePaymodeId");
        String payUse = pay.contains(PAYMODE) ? PAYMODE : (pay.isEmpty() ? PAYMODE : pay.get(0));
        String paymode = cn.choose("PatientData.CNCodePaymodeId", payUse);
        step(page, "Select Credit Note Code Paymode", "Choose \"" + PAYMODE + "\" in Credit Note Code Paymode*" + (payUse.equals(PAYMODE) ? "" : " (not offered - first option used)"), "A code paymode is chosen",
                paymode.isEmpty() ? "Problem - " + cn.problems : "Credit Note Code Paymode = " + paymode, paymode.equals(payUse) && !paymode.isEmpty() ? "PASS" : "FAIL");

        if (!cn.serviceTicked(0)) { cn.tickService(0); }
        cn.problems.clear();
        String amt = cn.typeAmount(0, AMOUNT);
        step(page, "Enter the credit note amount", "Type " + AMOUNT + " in the service's Credit Note Amount (after the drop-downs: choosing them clears an amount typed earlier)", "The amount is accepted",
                amt.isEmpty() ? "Problem - " + cn.problems : "Credit Note Amount = " + amt + " (total credit note amount shows " + cn.totalCreditNoteAmount() + ")", amt.equals(AMOUNT) ? "PASS" : "FAIL");

        cn.problems.clear();
        String toast = cn.clickSave();
        boolean saved = toast.toLowerCase().contains("saved successfully");
        step(page, "Click Save", "Click Save (fnSaveRefundDetails();)", "'Credit Note Saved Successfully.' toast",
                toast.isEmpty() ? "No toast appeared" + (cn.problems.isEmpty() ? "" : " - " + cn.problems) : toast, saved ? "PASS" : "FAIL");
        addSummary("Bill", billInfo);
        addSummary("Save toast", toast.isEmpty() ? "(none)" : toast);
        if (!saved) { addSummary("Result", "FAILED - not saved"); return; }

        cn.problems.clear();
        String url = cn.clickViewCreditNote();
        boolean tab = cn.reportTab != null;
        boolean report = url.toLowerCase().contains("billrefundcreditnotereport") && url.toLowerCase().contains("fintransheaderid");
        String actual = !report ? (url.isEmpty() ? "No report URL captured" + (cn.problems.isEmpty() ? "" : " - " + cn.problems) : "Report URL: " + url)
                                : (tab ? "Credit note report opened in another tab: " : "Credit note report requested (no tab was seen): ") + url;
        if (cn.reportPng != null && cn.reportPng.length > 0) {
            step(cn.reportPng, "Click View Credit Note and verify the report is generated in another tab", "Click View Credit Note (fnViewLastCreditNote()); the report opens in another tab",
                    "The credit note report is generated in another tab", actual, report ? "PASS" : "FAIL");
        } else {
            step(page, "Click View Credit Note and verify the report is generated in another tab", "Click View Credit Note (fnViewLastCreditNote()); the report opens in another tab",
                    "The credit note report is generated in another tab", actual, report ? "PASS" : "FAIL");
        }
        addSummary("Credit note report", url.isEmpty() ? "(none)" : url);
        addSummary("Result", report ? "Saved; report generated" : "Saved; report not confirmed");
    }
}
