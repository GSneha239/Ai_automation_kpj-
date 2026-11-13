package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory &gt; POS &gt; Payment Settlement &gt; <b>Payor</b> — Page Object (read-only use).
 *
 * <p>Locators from the live DOM (hisdemo v2026.11.13.455, manual test 13/11/2026). Route
 * {@code #/CompanyPaymentSettlement}, controller ComapnyPaymentSettlementController, header
 * "PAYMENT SETTLEMENT - COMPANY", tab title "Payor".</p>
 * <ul>
 *   <li>Filters: {@code IsChecked} (date filter on/off), {@code #FromDate}/{@code #ToDate}
 *       ({@code PatientData.FromDate/ToDate}), Reg. Type radios {@code PatientData.PatientType} (0 OPD, 1 IPD, 2 All),
 *       {@code #mrn} + {@code FindPatient();} + {@code OpenPopupScreen()}, Payor* select2 {@code #cmpny}
 *       ({@code PatientData.sponsorid}), Bill Type (disabled), Cash Counter {@code #cshcntr}.</li>
 *   <li>Search = {@code fnFillBillDetails();} → {@code api/BillListSearch/FetchBillSearchListForCompany} +
 *       {@code api/Advance/fetch} + {@code api/Bill/Fetch}. Tabs "List of Outstanding Bills" / "List Of Allocation".</li>
 *   <li>Demand Letter = {@code PrintCompanySettlementReport()} (opens CompanyPaymentSettlementReportSummary.aspx).
 *       Settle Bill = {@code fnIUDBill()} - INSERTPAYMENT, never allowed through: the read-only guard aborts it and the
 *       tests only use it up to its own "Please Select Cash Counter" validation.</li>
 * </ul>
 */
public class PaymentSettlementPayor extends ReadOnlyScreen {

    public static final String ROUTE = "#/CompanyPaymentSettlement";
    public static final String READY = "[ng-click='fnFillBillDetails();']";
    public static final String BILL_API = "api/BillListSearch/FetchBillSearchListForCompany";

    public static final String PAYOR = "PatientData.sponsorid";
    public static final String CASH_COUNTER = "PatientData.CashCounterId";
    public static final String DEPOSIT_TOTAL = "AdvanceGridLst.TotalAmountAllocation";
    public static final String SETTLE_TOTAL = "PharmacyServiceBillDetails.TotalSettledAmount1";

    public String lastBillRequest = "";
    public int lastBillStatus = 0;

    public PaymentSettlementPayor(Page page) { super(page); }

    /** Inventory &gt; POS &gt; Payment Settlement &gt; Payor. */
    public boolean open() {
        return openViaMenuPath(ROUTE, READY, "^\\s*Inventory\\s*$", "^\\s*POS\\s*$", "^\\s*Payment Settlement\\s*$");
    }

    /** Re-open from Home so the controller (and its first-load behaviour) starts fresh. */
    public boolean reopen() { goHome(); return open(); }

    public String payorModel() {
        Object r = page.evaluate("() => { const s = document.querySelector('#cmpny'); const c = s && angular.element(s).controller('ngModel');"
                + " return c ? String(c.$modelValue) : 'n/a'; }");
        return String.valueOf(r);
    }

    /** Text the select2 box shows for Payor (what the user sees). */
    public String payorDisplayed() {
        Object r = page.evaluate("() => { const c = document.querySelector('#s2id_cmpny .select2-chosen, #s2id_cmpny .select2-choice span');"
                + " if (c) return c.innerText.trim(); try { const d = jQuery('#cmpny').select2('data'); return d ? String(d.text).trim() : ''; } catch (e) { return ''; } }");
        return String.valueOf(r);
    }

    public boolean dateFilterOn() {
        return Boolean.TRUE.equals(page.evaluate("() => { const c = document.querySelector('[ng-model=\"IsChecked\"]'); return !!c && c.checked; }"));
    }

    public void setDateFilter(boolean on) {
        if (dateFilterOn() != on) { page.locator("[ng-model='IsChecked']").first().click(); waitForAngular(700); }
    }

    public void setFromDate(String d) { setNgInput("PatientData.FromDate", d); }

    public void clickRegType(String value) {
        page.locator("input[type='radio'][ng-model='PatientData.PatientType'][value='" + value + "']").click();
        waitForAngular(600);
    }

    /** Search; waits for the bill-list API. Returns its HTTP status (0 = no request, e.g. validation stopped it). */
    public int search() {
        clearCaptured();
        Response r = clickAndWaitForApi("fnFillBillDetails();", BILL_API, 20000);
        if (r == null) { lastBillStatus = 0; lastBillRequest = ""; return 0; }
        try { lastBillRequest = String.valueOf(r.request().postData()); } catch (Exception e) { lastBillRequest = ""; }
        lastBillStatus = r.status();
        // The deposit / payment-mode lists come from 2-3 further calls after the bill list.
        try {
            page.waitForFunction("() => !document.querySelector('.busy-indicator:not(.ng-hide), .global-loader[style*=\"block\"]')", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(2500);
        return lastBillStatus;
    }

    /** Click Search where validation is expected to stop it (no API). */
    public void clickSearchOnly() { clearCaptured(); clickNg("fnFillBillDetails();"); waitForAngular(1500); }

    public int depositRows() { return tableRows(0); }
    public int billRows() { return tableRows(1); }

    /** IPD No./OPD No. column of the bill table. */
    @SuppressWarnings("unchecked")
    public List<String> billVisitNumbers() {
        Object r = page.evaluate("() => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)[1]; if (!t) return [];"
                + " const hs = [...t.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim());"
                + " const i = hs.findIndex(h => /IPD No\\.\\/OPD No\\./.test(h)); if (i < 0) return [];"
                + " return [...t.querySelectorAll('tbody tr')].filter(x => x.offsetParent !== null && x.innerText.trim()).map(x => (x.cells[i] || {}).innerText || ''); }");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    public void filterColumn(String ngModel, String text) { setNgInput(ngModel, text); waitForAngular(800); }

    public void toggleSelectAll(String ngModel) { page.locator("[ng-model='" + ngModel + "']").first().click(); waitForAngular(1000); }

    /** Sum of BalanceAmount over the deposit list (the value the allocation total should show when all are ticked). */
    public double depositBalanceSum() {
        Object r = page.evaluate("() => { const s = angular.element(document.querySelector('#cmpny')).scope(); const l = (s && s.AdvanceGridLst) || [];"
                + " return l.reduce((a, x) => a + (+x.BalanceAmount || 0), 0); }");
        return r instanceof Number ? ((Number) r).doubleValue() : -1;
    }

    /** Sum of the selected bills' amount-to-settle. */
    public double selectedBillSum() {
        Object r = page.evaluate("() => { const s = angular.element(document.querySelector('#cmpny')).scope(); const l = (s && s.PharmacyServiceBillDetails) || [];"
                + " return l.filter(x => x.IsSelected).reduce((a, x) => a + (+x.totalsettledamount || 0), 0); }");
        return r instanceof Number ? ((Number) r).doubleValue() : -1;
    }

    public void openTab(String text) {
        page.evaluate("(t) => { const a = [...document.querySelectorAll('a')].find(x => x.innerText.trim() === t && x.offsetParent !== null); if (a) a.click(); }", text);
        waitForAngular(1200);
    }

    /** Allocation tab: open the first "Bill Details" popup; returns its row count (-1 = no popup). */
    public int openFirstBillDetails() {
        Object clicked = page.evaluate("() => { const b = [...document.querySelectorAll('button')].find(x => x.offsetParent !== null && x.innerText.trim() === 'Bill Details');"
                + " if (!b) return false; b.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) return -1;
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal.in')].some(m => /Bill Details/.test(m.innerText))", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return -1; }
        waitForAngular(1500);
        Object n = page.evaluate("() => { const m = [...document.querySelectorAll('.modal.in')].pop(); return [...m.querySelectorAll('tbody tr')].filter(r => r.innerText.trim()).length; }");
        return n instanceof Number ? ((Number) n).intValue() : -1;
    }

    /** In the Bill Details popup: click the first Print and return the captured report URL ("" if none). */
    public String printFirstReceiptInPopup() {
        clearCaptured();
        page.evaluate("() => { const m = [...document.querySelectorAll('.modal.in')].pop(); const b = m && m.querySelector('[ng-click^=\"fnPrintReceipt\"]'); if (b) b.click(); }");
        waitForAngular(2000);
        List<String> u = openedUrls();
        return u.isEmpty() ? "" : u.get(0);
    }

    public String clickDemandLetter() {
        clearCaptured();
        clickNg("PrintCompanySettlementReport()");
        waitForAngular(1500);
        List<String> u = openedUrls();
        return u.isEmpty() ? "" : u.get(0);
    }

    public void clickSettleBill() { clearCaptured(); clickNg("fnIUDBill()"); waitForAngular(2000); }

    public void searchMrn(String mrn) {
        setNgInput("PatientData.MRNO", mrn);
        clearCaptured();
        clickNg("FindPatient();");
        waitForAngular(2500);
    }

    public int openPatientPopup() {
        clickNg("OpenPopupScreen()");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal.in')].length > 0", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        return openModals();
    }

    /** Placeholder text of the From Date box (the manual run found a raw JS Date string there). */
    public String fromDatePlaceholder() {
        Object r = page.evaluate("() => { const e = document.querySelector('#FromDate'); return e ? e.placeholder : ''; }");
        return String.valueOf(r);
    }
}
