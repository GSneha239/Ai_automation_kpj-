package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Inventory &gt; POS &gt; Payment Settlement &gt; <b>Payor</b> — READ-ONLY automated checks (PAY-01 … PAY-20).
 *
 * <p>Built from the manual execution of 13/11/2026 (POS_PaymentSettlement_Payor_Manual_Report.html). No payment is
 * settled: write calls are aborted in the browser, Settle Bill is only exercised up to its own validation, and the
 * settlement itself is reported SKIPPED. Expected results describe correct behaviour, so known defects (Payor shows a
 * value that is not selected, date filter on first search, raw JS date placeholder) are expected to FAIL.</p>
 *
 * <p>Data: payor {@code -Dpay.payor} (default "AIA BHD. (EB)", which had 66 deposits / 24 bills from 01/01/2024).</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Inventory_page.POS_page.PaymentSettlementPayor" -Ddevhis.postrun=off
 *   report: test-output/reports/POS_PaymentSettlement_Payor.html
 * </pre>
 */
public class PaymentSettlementPayor extends DevHisBase {

    private static final String PAYOR = System.getProperty("pay.payor", "AIA BHD. (EB)");
    private static final String FROM = System.getProperty("pay.from", "01/01/2024");
    private int pass, fail, skipped, blocked;

    public PaymentSettlementPayor() { super("POS_PaymentSettlement_Payor"); }

    public static void main(String[] args) {
        PaymentSettlementPayor t = new PaymentSettlementPayor();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private interface Check { String[] run() throws Exception; }

    /** One test case: {STATUS, actual}; duration appended; any exception = FAIL with its first line. */
    private void tc(String id, String name, String desc, String expected, Check c) {
        long t0 = System.currentTimeMillis();
        String status, actual;
        try {
            String[] r = c.run();
            status = r[0];
            actual = r[1];
        } catch (Throwable e) {
            status = "FAIL";
            actual = "FAILURE REASON - exception: " + String.valueOf(e.getMessage()).split("\\R")[0];
        }
        actual += " | duration " + String.format("%.1f", (System.currentTimeMillis() - t0) / 1000.0) + " s";
        step(page, id + " " + name, desc, expected, actual, status);
        switch (status) {
            case "PASS": pass++; break;
            case "SKIPPED": skipped++; break;
            case "BLOCKED": blocked++; break;
            default: fail++;
        }
    }

    private static String[] r(boolean ok, String actual) { return new String[]{ok ? "PASS" : "FAIL", actual}; }

    /** Fresh screen, payor + date range ticked, searched. "" = ready, otherwise the reason it is not. */
    private String loaded(com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor pay) {
        if (!pay.reopen()) return "screen did not open";
        if (pay.pickOption(com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor.PAYOR, PAYOR).isEmpty()) return "payor '" + PAYOR + "' not in the list";
        pay.setDateFilter(true);
        pay.setFromDate(FROM);
        int s = pay.search();
        if (s != 200) return "bill search HTTP " + s;
        return pay.billRows() > 0 || pay.depositRows() > 0 ? "" : "no deposits or bills for '" + PAYOR + "' since " + FROM;
    }

    @Override
    protected void body() {
        meta("Inventory - POS - Payment Settlement - Payor (read-only)",
                "Inventory > POS > Payment Settlement > Payor",
                "Read-only checks derived from the 13/11/2026 manual execution. Write calls are blocked in the browser; "
                        + "settling a payment is SKIPPED. Payor under test: " + PAYOR + ", from " + FROM + ".");

        com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor pay = new com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor(page);
        pay.installGuards();
        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login (existing LoginPage)", "Authenticated", "Logged in at " + page.url(), "PASS");

        tc("PAY-01", "Page navigation", "Inventory > POS > Payment Settlement > Payor (sidebar)",
                "#/CompanyPaymentSettlement opens with its Search button",
                () -> { boolean ok = pay.open(); return r(ok, ok ? "Opened " + page.url() : "FAILURE REASON - screen did not open; at " + page.url()); });

        tc("PAY-02", "Page title and layout", "Read header, tab title and sections",
                "Header contains 'PAYMENT SETTLEMENT'; tab title 'Payor'; both tables present",
                () -> {
                    pay.open();
                    assertThat(page.locator(".page_title").first()).containsText("PAYMENT SETTLEMENT",
                            new com.microsoft.playwright.assertions.LocatorAssertions.ContainsTextOptions().setIgnoreCase(true).setTimeout(15000));
                    List<String> dep = pay.tableHeaders(0), bill = pay.tableHeaders(1);
                    boolean ok = "Payor".equalsIgnoreCase(page.title()) && dep.contains("Payment No.") && bill.contains("Bill No.");
                    return r(ok, "Header '" + pay.headerText() + "', tab '" + page.title() + "' | deposit cols " + dep.size() + ", bill cols " + bill.size());
                });

        tc("PAY-03", "Fields, dropdowns and buttons", "Inspect Payor, Reg. Type, MRN, Cash Counter, Search, Demand Letter, Settle Bill",
                "All present; Payor list populated",
                () -> {
                    pay.open();
                    int payors = pay.optionCount(com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor.PAYOR);
                    int cash = pay.optionCount(com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor.CASH_COUNTER);
                    boolean btns = pay.isVisible("[ng-click='PrintCompanySettlementReport()']") && pay.isVisible("[ng-click='fnIUDBill()']")
                            && pay.isVisible("#mrn") && pay.isVisible("[ng-model='IsChecked']");
                    return r(payors > 1 && btns, "Payor options=" + payors + ", Cash Counter options=" + cash + (cash <= 1 ? " (only '-Select-')" : "") + ", buttons/fields present=" + btns);
                });

        tc("PAY-04", "Payor dropdown default", "Fresh screen: compare the Payor text shown with the selected value",
                "Nothing selected is shown as '-Select-' (display matches the model)",
                () -> {
                    pay.reopen();
                    String shown = pay.payorDisplayed(), model = pay.payorModel();
                    boolean modelEmpty = model == null || model.equals("undefined") || model.equals("null") || model.isEmpty();
                    boolean ok = !modelEmpty || shown.isEmpty() || shown.toLowerCase().contains("select");
                    return r(ok, "Shown '" + shown + "', model '" + model + "'" + (ok ? "" : " - FAILURE REASON: a payor is displayed but none is selected"));
                });

        tc("PAY-05", "Search validation without Payor", "Fresh screen, click Search", "'Please Select The Payor'; no bill-list request",
                () -> {
                    pay.reopen();
                    pay.clickSearchOnly();
                    List<String> a = pay.waitForAlert(3000);
                    return r(a.stream().anyMatch(x -> x.toLowerCase().contains("payor")), "Messages: " + a);
                });

        tc("PAY-06", "Payor list loads", "Payor '" + PAYOR + "', date filter on from " + FROM + ", Search",
                "Bill-list API 200; pending deposits and bills listed",
                () -> {
                    String why = loaded(pay);
                    if (!why.isEmpty()) return new String[]{why.startsWith("no deposits") ? "BLOCKED" : "FAIL", why};
                    return r(true, "API " + pay.lastBillStatus + "; deposits=" + pay.depositRows() + ", bills=" + pay.billRows()
                            + " | first bill: " + String.join("", pay.tableRowTexts(1, 1)));
                });

        tc("PAY-07", "Reg. Type filter (OPD)", "Same search with Reg. Type = OPD",
                "Request opd_ipd=0; only OP visit numbers listed",
                () -> {
                    String why = loaded(pay);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    pay.clickRegType("0");
                    pay.search();
                    List<String> v = pay.billVisitNumbers();
                    boolean ok = pay.lastBillRequest.contains("\"opd_ipd\":0") && v.stream().allMatch(x -> x.trim().toUpperCase().startsWith("OP"));
                    return r(ok, "Request " + pay.lastBillRequest + " | " + v.size() + " bills: " + v);
                });

        tc("PAY-08", "Date filter checkbox on first search", "Fresh screen, From Date NOT ticked, choose payor, Search",
                "With the box unticked no date range is applied (FromDate/ToDate null)",
                () -> {
                    pay.reopen();
                    pay.pickOption(com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor.PAYOR, PAYOR);
                    pay.setDateFilter(false);
                    pay.search();
                    boolean ok = pay.lastBillRequest.contains("\"FromDate\":null");
                    return r(ok, "Ticked=" + pay.dateFilterOn() + "; request " + pay.lastBillRequest
                            + (ok ? "" : " - FAILURE REASON: dates sent although the date filter is off"));
                });

        tc("PAY-09", "Date field placeholder", "Untick From Date and read the empty field's placeholder",
                "A readable placeholder (e.g. dd/mm/yyyy) or none",
                () -> {
                    pay.reopen();
                    pay.setDateFilter(true);
                    pay.setDateFilter(false);
                    String ph = pay.fromDatePlaceholder();
                    boolean ok = !ph.contains("GMT");
                    return r(ok, "Placeholder: '" + ph + "'");
                });

        tc("PAY-10", "Column search filters", "Payment No. and Bill No. column filters: no-match text, then cleared",
                "No-match text hides all rows; clearing restores them",
                () -> {
                    String why = loaded(pay);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    int d0 = pay.depositRows(), b0 = pay.billRows();
                    pay.filterColumn("SearchAdvanceGridLst.ReceiptNo", "ZZZ_NO_MATCH");
                    pay.filterColumn("SearchPharmacyServiceBillDetails.tranno", "ZZZ_NO_MATCH");
                    int d1 = pay.depositRows(), b1 = pay.billRows();
                    pay.filterColumn("SearchAdvanceGridLst.ReceiptNo", "");
                    pay.filterColumn("SearchPharmacyServiceBillDetails.tranno", "");
                    int d2 = pay.depositRows(), b2 = pay.billRows();
                    return r(d1 <= 0 && b1 <= 0 && d2 == d0 && b2 == b0, "Deposits " + d0 + " -> " + d1 + " -> " + d2 + "; bills " + b0 + " -> " + b1 + " -> " + b2);
                });

        tc("PAY-11", "Select-all totals", "Tick all deposits and all bills, compare the totals, untick",
                "Allocation total = sum of deposit balances; amount to be settled = sum of selected bills; both reset",
                () -> {
                    String why = loaded(pay);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    pay.toggleSelectAll("advcrfnd.SelectAllAdvanceGridLst");
                    String alloc = pay.inputValue(com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor.DEPOSIT_TOTAL);
                    double depSum = pay.depositBalanceSum();
                    pay.toggleSelectAll("SelectAllPharmacy");
                    String settle = pay.inputValue(com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor.SETTLE_TOTAL);
                    double billSum = pay.selectedBillSum();
                    pay.toggleSelectAll("advcrfnd.SelectAllAdvanceGridLst");
                    pay.toggleSelectAll("SelectAllPharmacy");
                    String alloc0 = pay.inputValue(com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor.DEPOSIT_TOTAL);
                    String settle0 = pay.inputValue(com.kpj.pages.Inventory_page.POS_page.PaymentSettlementPayor.SETTLE_TOTAL);
                    boolean ok = Math.abs(num(alloc) - depSum) < 0.01 && Math.abs(num(settle) - billSum) < 0.01 && num(alloc0) == 0 && num(settle0) == 0;
                    return r(ok, "Allocation " + alloc + " (sum " + String.format("%.2f", depSum) + "), settle " + settle + " (sum " + String.format("%.2f", billSum)
                            + "); after untick " + alloc0 + " / " + settle0);
                });

        tc("PAY-12", "List Of Allocation tab and Bill Details (view)", "Open 'List Of Allocation', then the first 'Bill Details'",
                "Allocation table listed; Bill Details popup shows the bills",
                () -> {
                    String why = loaded(pay);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    pay.openTab("List Of Allocation");
                    List<String> heads = pay.tableHeaders(0);
                    int rows = pay.openFirstBillDetails();
                    pay.dismissModals();
                    pay.openTab("List of Outstanding Bills");
                    if (rows < 0) return new String[]{"BLOCKED", "No allocation with 'Bill Details' for this payor; headers " + heads};
                    return r(heads.contains("Allocation No.") && rows > 0, "Allocation headers " + heads + " | Bill Details rows=" + rows);
                });

        tc("PAY-13", "Receipt print from Bill Details", "Bill Details popup > Print (URL captured, fetched in the browser)",
                "BillPaymentReport.aspx returns a PDF",
                () -> {
                    String why = loaded(pay);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    pay.openTab("List Of Allocation");
                    if (pay.openFirstBillDetails() <= 0) { pay.dismissModals(); return new String[]{"BLOCKED", "No Bill Details row to print"}; }
                    String url = pay.printFirstReceiptInPopup();
                    pay.dismissModals();
                    if (url.isEmpty()) return r(false, "FAILURE REASON - Print opened nothing");
                    String probe = pay.probeReport(url);
                    return r(probe.startsWith("200") && probe.contains("PDF"), url + " -> " + probe);
                });

        tc("PAY-14", "Demand Letter report", "Payor selected and searched, click Demand Letter (URL captured, fetched in the browser)",
                "CompanyPaymentSettlementReportSummary.aspx answers 200",
                () -> {
                    String why = loaded(pay);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    String url = pay.clickDemandLetter();
                    if (url.isEmpty()) return r(false, "FAILURE REASON - Demand Letter opened nothing; messages " + pay.alerts());
                    String probe = pay.probeReport(url);
                    return r(url.contains("CompanyPaymentSettlementReportSummary") && probe.startsWith("200"), url + " -> " + probe);
                });

        tc("PAY-15", "MRN lookup validation", "Search MRN blank, then an unknown MRN (ZZ999999)",
                "Blank -> 'Please Enter MRN'; unknown -> a not-found message",
                () -> {
                    pay.reopen();
                    pay.searchMrn("");
                    List<String> blank = pay.waitForAlert(2000);
                    pay.searchMrn("ZZ999999");
                    List<String> unknown = pay.waitForAlert(3000);
                    boolean ok = String.valueOf(blank).toLowerCase().contains("mrn") && !unknown.isEmpty();
                    return r(ok, "Blank: " + blank + " | unknown: " + (unknown.isEmpty() ? "(no message)" : unknown));
                });

        tc("PAY-16", "Patient Search popup", "Click the patient search icon, then close", "Popup opens and closes",
                () -> {
                    pay.reopen();
                    int n = pay.openPatientPopup();
                    pay.dismissModals();
                    return r(n > 0 && pay.openModals() == 0, "Popups opened=" + n + ", open after close=" + pay.openModals());
                });

        tc("PAY-17", "Settle Bill validation", "Bills selected, no Cash Counter, click Settle Bill",
                "Validation message; NO payment request",
                () -> {
                    if (!pay.guardVerified()) return new String[]{"SKIPPED", "Write guard could not be verified - Settle Bill not clicked"};
                    String why = loaded(pay);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    pay.toggleSelectAll("SelectAllPharmacy");
                    int before = pay.blockedWrites.size();
                    pay.clickSettleBill();
                    List<String> a = pay.waitForAlert(3000);
                    pay.toggleSelectAll("SelectAllPharmacy");
                    boolean wrote = pay.blockedWrites.size() > before;
                    return r(!a.isEmpty() && !wrote, "Messages: " + a + " | payment request attempted: " + wrote);
                });

        tc("PAY-18", "Settle a payment", "Would post INSERTPAYMENT", "(write operation - not executed)",
                () -> new String[]{"SKIPPED", "Read-only run. Also not possible for this user: the Cash Counter list is empty. Covered as MANUAL."});

        tc("PAY-19", "Add / Edit / Delete / Clear / Pagination / Sorting", "Inspect the screen for these controls",
                "Present if supported",
                () -> {
                    pay.open();
                    Object found = page.evaluate("() => [...document.querySelectorAll('button, a.btn')].filter(b => b.offsetParent !== null)"
                            + ".map(b => b.innerText.trim()).filter(t => /^(add|new|edit|delete|clear|reset)$/i.test(t))");
                    return new String[]{"SKIPPED", "Not offered on this screen (matching buttons: " + found + "; tables have no pager or sortable headers)"};
                });

        tc("PAY-20", "Console errors and failed API requests", "Everything logged during this run",
                "No HTTP >= 400, no console / page errors",
                () -> r(pay.apiFailures.isEmpty() && pay.consoleErrors.isEmpty(),
                        "HTTP>=400: " + pay.apiFailures + " | console: " + pay.consoleErrors + " | blocked writes: " + pay.blockedWrites
                                + " | API calls logged: " + pay.apiLog.size()));

        addSummary("Mode", "READ-ONLY (write calls aborted in the browser)");
        addSummary("Payor / range", PAYOR + " from " + FROM);
        addSummary("Test cases", (pass + fail + skipped + blocked) + " - PASS " + pass + " / FAIL " + fail + " / SKIPPED " + skipped + " / BLOCKED " + blocked);
        addSummary("Failed API responses", pay.apiFailures.isEmpty() ? "none" : String.join(" ; ", pay.apiFailures));
        addSummary("Console errors", pay.consoleErrors.isEmpty() ? "none" : String.join(" ; ", pay.consoleErrors));
        addSummary("Blocked write calls", pay.blockedWrites.isEmpty() ? "none" : String.join(" ; ", pay.blockedWrites));
    }

    private static double num(String s) {
        try { return s == null || s.trim().isEmpty() ? 0 : Double.parseDouble(s.replace(",", "").trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }
}
