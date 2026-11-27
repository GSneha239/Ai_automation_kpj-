package com.kpj.pages.Billing_page.Cancellation_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; Cancellation &gt; <b>Bill</b> — Page Object for READ-ONLY use.
 *
 * <p>Extends {@link ReadOnlyScreen}, so the browser-level write guard (IUD / Save / Insert / Update / Delete / Approve
 * requests are aborted), the API / console monitors and the JAlert capture apply. Nothing in this class clicks Save
 * ({@code fnCancelReceipts();}) or any button inside the Company Approve Amount pop-up other than its close (×).</p>
 *
 * <p>Locators confirmed in the live DOM (devhis v2026.11.26.546, 26/11/2026):</p>
 * <ul>
 *   <li>Sidebar: {@code #li0Billing} &gt; {@code #li1Cancellation} &gt; {@code #li2Bill > a[href='#/BillCancellation']};
 *       header "BILL", browser tab "Bill".</li>
 *   <li>Search panel: {@code #FromDate} / {@code #ToDate} ({@code PatientData.FromDate/ToDate}), Reg. Type radios
 *       {@code PatientData.PatientType} (0 OPD, 1 IPD), MRN {@code PatientData.MRNO}, {@code FindPatient();},
 *       {@code OpenPopupScreen()}, Payor {@code select PatientData.ReceivableID}, Bill No. {@code PatientData.ReceiptNo},
 *       Search {@code fnSearchBill();}.</li>
 *   <li>Cancellation panel (form {@code #BillCancellationForm}): Cash Counter {@code PatientData.CashCounterId}, Remark
 *       {@code PatientData.Remark}, Cancellation Reason {@code PatientData.CancellationReasonID} (class
 *       {@code validate[required]}), {@code BtnCompanyAprvAmt();}, Save {@code fnCancelReceipts();}.</li>
 *   <li>Bill grid: header checkbox {@code checkallService}, row checkbox {@code bill.IsSelected}
 *       ({@code SelectBillDetails(bill,$index)} - client side only).</li>
 *   <li>Read calls (all pass the guard): FetchPatientData GetPatientInfo, Fetch GetSponserByVisit, Bill/Fetch
 *       GetBillDetailsToCancel / GetCancelledBills, FetchAdmissionData GetCompanyApproveDataOPD ...</li>
 * </ul>
 */
public class BillCancellation extends ReadOnlyScreen {

    public static final String ROUTE = "#/BillCancellation";
    public static final String READY = "[ng-click='fnSearchBill();']";
    public static final String MENU_LINK = "#li0Billing a[href='#/BillCancellation']";
    public static final String BILL_API = "api/Bill/Fetch";
    public static final String PATIENT_API = "FetchPatientData";

    public static final String MRN = "PatientData.MRNO";
    public static final String PAYOR = "PatientData.ReceivableID";
    public static final String CASH_COUNTER = "PatientData.CashCounterId";
    public static final String REASON = "PatientData.CancellationReasonID";
    public static final String BILL_CHECKBOX = "input[ng-model='bill.IsSelected']";

    /** Search panel, label -&gt; CSS. */
    public static final Map<String, String> SEARCH_PANEL = new LinkedHashMap<>();
    /** Cancellation panel, label -&gt; CSS. Save is only checked for presence - never clicked. */
    public static final Map<String, String> CANCELLATION_PANEL = new LinkedHashMap<>();
    static {
        SEARCH_PANEL.put("From Date", "[ng-model='PatientData.FromDate']");
        SEARCH_PANEL.put("To Date", "[ng-model='PatientData.ToDate']");
        SEARCH_PANEL.put("Reg. Type", "input[type='radio'][ng-model='PatientData.PatientType']");
        SEARCH_PANEL.put("MRN", "[ng-model='PatientData.MRNO']");
        SEARCH_PANEL.put("Find Patient", "[ng-click='FindPatient();']");
        SEARCH_PANEL.put("Search Patient", "[ng-click='OpenPopupScreen()']");
        SEARCH_PANEL.put("Payor", "select[ng-model='PatientData.ReceivableID']");
        SEARCH_PANEL.put("Bill No.", "[ng-model='PatientData.ReceiptNo']");
        SEARCH_PANEL.put("Search button", READY);

        CANCELLATION_PANEL.put("Cash Counter", "select[ng-model='PatientData.CashCounterId']");
        CANCELLATION_PANEL.put("Remark", "[ng-model='PatientData.Remark']");
        CANCELLATION_PANEL.put("Cancellation Reason", "select[ng-model='PatientData.CancellationReasonID']");
        CANCELLATION_PANEL.put("Company Approved Amount", "[ng-click='BtnCompanyAprvAmt();']");
        CANCELLATION_PANEL.put("Save", "[ng-click='fnCancelReceipts();']");
    }

    public BillCancellation(Page page) { super(page); }

    // ---- navigation ------------------------------------------------------------------

    /** Billing &gt; Cancellation &gt; Bill through the sidebar (in-app hash change; a reload logs nHIS out). */
    public boolean open() {
        if (isOnRoute(ROUTE) && isVisible(READY)) { hookAlerts(); return true; }
        expandMenuEntry(null, "^\\s*Billing\\s*$");
        expandMenuEntry("^\\s*Billing\\s*$", "^\\s*Cancellation\\s*$");
        Object clicked = page.evaluate("(s) => { const a = [...document.querySelectorAll(s)].find(x => x.offsetParent !== null) || document.querySelector(s);"
                + " if (!a) return false; a.click(); return true; }", MENU_LINK);
        if (!Boolean.TRUE.equals(clicked)) {
            System.out.println("BillCancellation.open: Billing > Cancellation > Bill link not found");
            return false;
        }
        try {
            page.waitForFunction("(s) => { const e = document.querySelector(s); return !!e && e.offsetParent !== null; }", READY,
                    new Page.WaitForFunctionOptions().setTimeout(90000));
        } catch (Exception e) {
            System.out.println("BillCancellation.open: Search button did not appear within 90 s");
        }
        waitForAngular(800);
        hookAlerts();
        return isOnRoute(ROUTE) && isVisible(READY);
    }

    /** Leave through Home and open again, so each scenario starts with a fresh controller. */
    public boolean reopen() { goHome(); return open(); }

    // ---- reading the screen ---------------------------------------------------------------

    public Map<String, Boolean> presence(Map<String, String> controls) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : controls.entrySet()) out.put(e.getKey(), isVisible(e.getValue()));
        return out;
    }

    public String fromDate() { return inputValue("PatientData.FromDate"); }
    public String toDate() { return inputValue("PatientData.ToDate"); }

    /** Value of the checked Reg. Type radio ("0" OPD, "1" IPD), "" when none. */
    public String regTypeChecked() {
        Object r = page.evaluate("() => { const c = [...document.querySelectorAll(\"input[type='radio'][ng-model='PatientData.PatientType']\")].find(x => x.checked);"
                + " return c ? c.value : ''; }");
        return r == null ? "" : r.toString();
    }

    public String payorSelected() { return selectedText(PAYOR); }
    public String cashCounterSelected() { return selectedText(CASH_COUNTER); }

    /** Cancellation reasons offered (placeholder left out). */
    @SuppressWarnings("unchecked")
    public List<String> reasonOptions() {
        Object r = page.evaluate("(ng) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]'); if (!s) return [];"
                + " return [...s.options].map(o => o.text.replace(/\\s+/g, ' ').trim()).filter(t => t && !/^-*\\s*select\\s*-*$/i.test(t)); }", REASON);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** True when the Cancellation Reason select carries the form's validationEngine "required" rule. */
    public boolean reasonMarkedRequired() {
        Object r = page.evaluate("(ng) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]');"
                + " return !!s && /validate\\[[^\\]]*required/.test(s.className); }", REASON);
        return Boolean.TRUE.equals(r);
    }

    /** Header labels of the bill grid; the header checkbox column is reported as "Select checkbox". */
    @SuppressWarnings("unchecked")
    public List<String> billGridHeaders() {
        Object r = page.evaluate("() => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                + "   .find(x => /Bill No\\./.test((x.querySelector('thead') || {}).innerText || '')); if (!t) return [];"
                + " return [...t.querySelectorAll('thead th')].map(h => { const txt = h.innerText.replace(/\\s+/g, ' ').trim();"
                + "   return txt || (h.querySelector('input[type=checkbox]') ? 'Select checkbox' : ''); }).filter(x => x); }");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Bill numbers listed in the bill grid. */
    @SuppressWarnings("unchecked")
    public List<String> billNumbers() {
        Object r = page.evaluate("() => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                + "   .find(x => /Bill No\\./.test((x.querySelector('thead') || {}).innerText || '')); if (!t) return [];"
                + " const hs = [...t.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim()); const i = hs.indexOf('Bill No.');"
                + " return [...t.querySelectorAll('tbody tr')].filter(x => x.offsetParent !== null && x.innerText.trim()).map(x => ((x.cells[i] || {}).innerText || '').trim()); }");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Row of {@code billNo} as header -&gt; cell text (empty map = not listed). Read by HEADER, so a shifted column shows. */
    @SuppressWarnings("unchecked")
    public Map<String, String> billRow(String billNo) {
        Object r = page.evaluate("(no) => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                + "   .find(x => /Bill No\\./.test((x.querySelector('thead') || {}).innerText || '')); if (!t) return {};"
                + " const hs = [...t.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim()); const bi = hs.indexOf('Bill No.');"
                + " const tr = [...t.querySelectorAll('tbody tr')].find(x => x.offsetParent !== null && ((x.cells[bi] || {}).innerText || '').trim() === no);"
                + " if (!tr) return {}; const out = {}; hs.forEach((h, i) => { if (h && tr.cells[i]) out[h] = tr.cells[i].innerText.replace(/\\s+/g, ' ').trim(); });"
                + " return out; }", billNo);
        return r instanceof Map ? new LinkedHashMap<>((Map<String, String>) r) : new LinkedHashMap<>();
    }

    /** The patient banner text ("Name : ... MRN : ..."), "" when no patient is shown. */
    public String patientBanner() {
        Object r = page.evaluate("() => { const v = document.querySelector('[ui-view]') || document.body;"
                + " const b = [...v.querySelectorAll('div,table,section')].filter(e => e.offsetParent !== null && /Name\\s*:/.test(e.innerText || '')"
                + "   && /MRN\\s*:/.test(e.innerText || '') && (e.innerText || '').length < 700).pop();"
                + " return b ? b.innerText.replace(/\\s+/g, ' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Error text shown on the screen (server error body or a visible error dialog), "" = none. */
    public String visibleErrorText() {
        Object r = page.evaluate("() => { const body = document.body ? document.body.innerText || '' : '';"
                + " const srv = body.match(/server error in|unhandled exception|runtime error|an error has occurred|the resource cannot be found/i); if (srv) return srv[0];"
                + " const box = [...document.querySelectorAll('.jconfirm-box, .toast-error, .alert-danger, .sweet-alert')]"
                + "   .find(e => e.getBoundingClientRect().height > 0 && /error|exception|failed/i.test(e.innerText || ''));"
                + " return box ? box.innerText.replace(/\\s+/g, ' ').trim().slice(0, 200) : ''; }");
        return r == null ? "" : r.toString();
    }

    // ---- read-only actions ------------------------------------------------------------------

    public void setMrn(String mrn) { setNgInput(MRN, mrn); }

    public void setDates(String from, String to) {
        setNgInput("PatientData.FromDate", from);
        setNgInput("PatientData.ToDate", to);
    }

    public void setRegType(String value) {
        page.locator("input[type='radio'][ng-model='PatientData.PatientType'][value='" + value + "']").first().click();
        waitForAngular(600);
    }

    /** Find Patient; waits for the patient lookup (and the bill search it triggers). Returns the lookup's HTTP status, 0 = none. */
    public int findPatient() {
        clearCaptured();
        com.microsoft.playwright.Response r = clickAndWaitForApi("FindPatient();", PATIENT_API, 20000);
        waitForAngular(2500);
        return r == null ? 0 : r.status();
    }

    /** Search; waits for the bill list. Returns its HTTP status, 0 = no request (validation stopped it). */
    public int search() {
        clearCaptured();
        com.microsoft.playwright.Response r = clickAndWaitForApi("fnSearchBill();", BILL_API, 20000);
        waitForAngular(2000);
        return r == null ? 0 : r.status();
    }

    /** Click Search where validation is expected to stop it (no wait for an API). */
    public void clickSearchOnly() { clearCaptured(); clickNg("fnSearchBill();"); waitForAngular(1500); }

    /** Open the Company Approve Amount pop-up (read-only view). True when it is showing. */
    public boolean openCompanyApprovedAmount() {
        clearCaptured();
        if (!clickNg("BtnCompanyAprvAmt();")) return false;
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal.in')].some(m => /Company Approve Amount/i.test(m.innerText || ''))", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        waitForAngular(1500);
        return true;
    }

    /** GL rows of the open Company Approve Amount pop-up, each header -&gt; cell text. */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> glRows() {
        Object r = page.evaluate("() => { const m = [...document.querySelectorAll('.modal.in')].find(x => /Company Approve Amount/i.test(x.innerText || '')); if (!m) return [];"
                + " const t = [...m.querySelectorAll('table')].find(x => /GL Reference/.test(x.innerText || '')); if (!t) return [];"
                + " const hs = [...t.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim());"
                + " return [...t.querySelectorAll('tbody tr')].filter(x => x.innerText.trim()).map(tr => { const o = {};"
                + "   hs.forEach((h, i) => { if (h && tr.cells[i]) o[h] = tr.cells[i].innerText.replace(/\\s+/g, ' ').trim(); }); return o; }); }");
        List<Map<String, String>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) if (o instanceof Map) out.add(new LinkedHashMap<>((Map<String, String>) o));
        return out;
    }

    /**
     * Close the pop-up with its × (ng-click {@code Cancel();}) - never its Approve / Edit / Delete buttons. Falls back to the
     * app's modal stack. True when no pop-up is left open.
     */
    public boolean closeCompanyApprovedAmount() {
        page.evaluate("() => { const m = [...document.querySelectorAll('.modal.in')].find(x => /Company Approve Amount/i.test(x.innerText || ''));"
                + " const x = m && [...m.querySelectorAll('button.close')].find(b => b.offsetParent !== null); if (x) x.click(); }");
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.modal')].some(m => getComputedStyle(m).display === 'block' && m.classList.contains('in'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { dismissModals(); }
        waitForAngular(500);
        return openModals() == 0;
    }

    private Locator billCheckbox(String billNo) {
        return page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(billNo)).locator(BILL_CHECKBOX).first();
    }

    public boolean isBillTicked(String billNo) {
        try { return billCheckbox(billNo).isChecked(); } catch (Exception e) { return false; }
    }

    /** Tick / untick a bill row with a real click (client-side selection only). */
    public void setBillTicked(String billNo, boolean ticked) {
        if (isBillTicked(billNo) == ticked) return;
        Locator cb = billCheckbox(billNo);
        cb.scrollIntoViewIfNeeded();
        cb.click(new Locator.ClickOptions().setTimeout(15000));
        waitForAngular(1000);
    }
}
