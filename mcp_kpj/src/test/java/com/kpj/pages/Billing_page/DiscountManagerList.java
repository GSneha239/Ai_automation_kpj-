package com.kpj.pages.Billing_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; <b>Discount Manager/CR</b> — discount LIST screen ({@code #/ConcessionManagerList}), Page Object for READ-ONLY use.
 *
 * <p>Extends {@link ReadOnlyScreen}: write requests are aborted in the browser (Save {@code IUDBill()} and the row Approve tick
 * both post to {@code ConcessionManagerFactory.IUDConcessionBill}), API failures and console errors are recorded and JAlert
 * messages are captured. This class never ticks a row's Approve checkbox and never clicks Print.</p>
 *
 * <p>Locators read from the live DOM (devhis v2026.11.26.546, 26/11/2026):</p>
 * <ul>
 *   <li>Search panel: {@code ConcessionManagerList.fromdate / todate / MRNO}, Reg. Type radios {@code ConManager.PatientType}
 *       (0 OPD, 1 IPD), {@code ConcessionManagerList.concessionauthorizeby}, {@code ConcessionManagerList.ConcessionReasonID},
 *       Find Patient {@code SearchPatientByMRNo()}, Search Patient {@code OpenPopupScreen()}, Search {@code SearchConcession()},
 *       Page No. {@code Pageno}, {@code PreviousBtn()} / {@code NextBtn();}, Print {@code PrintConcessionAll()}, Add {@code AddConcesson()}.</li>
 *   <li>Grid row: Approve checkbox {@code bill.IsApprove} (ng-click {@code ApproveConseeion(bill)} - NEVER clicked),
 *       {@code ClickAttachment(bill);}, {@code PrintConcession(bill);}.</li>
 *   <li>Read calls: {@code /api/ConcessionManager/Fetch} (list), {@code FetchPatientData} (GetPatientInfo).</li>
 * </ul>
 * {@link DiscountManagerAdd} extends this class for the Add screen, so the table readers are shared, not copied.
 */
public class DiscountManagerList extends ReadOnlyScreen {

    public static final String ROUTE = "#/ConcessionManagerList";
    public static final String READY = "[ng-click='SearchConcession()']";
    public static final String MENU_LINK = "#li0Billing a[href='#/ConcessionManagerList']";
    public static final String LIST_API = "api/ConcessionManager/Fetch";
    public static final String PATIENT_API = "FetchPatientData";

    public static final String FROM = "ConcessionManagerList.fromdate";
    public static final String TO = "ConcessionManagerList.todate";
    public static final String MRN = "ConcessionManagerList.MRNO";
    public static final String AUTHORIZED_BY = "ConcessionManagerList.concessionauthorizeby";
    public static final String REASON = "ConcessionManagerList.ConcessionReasonID";

    /** Search panel and list buttons, label -&gt; CSS. Print / Add are checked for presence here. */
    public static final Map<String, String> SEARCH_PANEL = new LinkedHashMap<>();
    static {
        SEARCH_PANEL.put("From Date", "[ng-model='ConcessionManagerList.fromdate']");
        SEARCH_PANEL.put("To Date", "[ng-model='ConcessionManagerList.todate']");
        SEARCH_PANEL.put("MRN", "[ng-model='ConcessionManagerList.MRNO']");
        SEARCH_PANEL.put("Find Patient", "[ng-click='SearchPatientByMRNo()']");
        SEARCH_PANEL.put("Search Patient", "[ng-click='OpenPopupScreen()']");
        SEARCH_PANEL.put("Reg. Type", "input[type='radio'][ng-model='ConManager.PatientType']");
        SEARCH_PANEL.put("Dis. Authorized By", "select[ng-model='ConcessionManagerList.concessionauthorizeby']");
        SEARCH_PANEL.put("Discount Reason", "select[ng-model='ConcessionManagerList.ConcessionReasonID']");
        SEARCH_PANEL.put("Search", READY);
        SEARCH_PANEL.put("Page No.", "[ng-model='Pageno']");
        SEARCH_PANEL.put("<<", "[ng-click='PreviousBtn()']");
        SEARCH_PANEL.put(">>", "[ng-click='NextBtn();']");
        SEARCH_PANEL.put("Print", "[ng-click='PrintConcessionAll()']");
        SEARCH_PANEL.put("Add", "[ng-click='AddConcesson()']");
    }

    public DiscountManagerList(Page page) { super(page); }

    // ---- navigation ------------------------------------------------------------------

    /** Billing &gt; Discount Manager/CR through the sidebar (in-app hash change; a reload logs nHIS out). */
    public boolean open() {
        if (isOnRoute(ROUTE) && isVisible(READY)) { hookAlerts(); return true; }
        expandMenuEntry(null, "^\\s*Billing\\s*$");
        Object clicked = page.evaluate("(s) => { const vis = x => x && x.offsetParent !== null;"
                + " let a = [...document.querySelectorAll(s)].find(vis) || document.querySelector(s);"
                + " if (!a) a = [...document.querySelectorAll('#li0Billing a')].find(x => /^\\s*Discount Manager\\/CR\\s*$/i.test(x.textContent || ''));"
                + " if (!a) return false; a.click(); return true; }", MENU_LINK);
        if (!Boolean.TRUE.equals(clicked)) {
            System.out.println("DiscountManagerList.open: Billing > Discount Manager/CR link not found");
            return false;
        }
        return waitUntilShown(READY) && isOnRoute(ROUTE);
    }

    /** Leave through Home and open again, so each scenario starts with a fresh controller. */
    public boolean reopen() { goHome(); return open(); }

    /** Wait (max 90 s) for {@code css} to be visible, then let Angular settle and hook JAlert. */
    protected boolean waitUntilShown(String css) {
        try {
            page.waitForFunction("(s) => { const e = document.querySelector(s); return !!e && e.offsetParent !== null; }", css,
                    new Page.WaitForFunctionOptions().setTimeout(90000));
        } catch (Exception e) {
            System.out.println("DiscountManager: '" + css + "' did not appear within 90 s");
            return false;
        }
        waitForAngular(800);
        hookAlerts();
        return true;
    }

    // ---- reading the screen ---------------------------------------------------------------

    public Map<String, Boolean> presence(Map<String, String> controls) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : controls.entrySet()) out.put(e.getKey(), isVisible(e.getValue()));
        return out;
    }

    /** Value of the checked radio of {@code ngModel} ("0" OPD, "1" IPD), "" when none. */
    public String checkedRadio(String ngModel) {
        Object r = page.evaluate("(ng) => { const c = [...document.querySelectorAll('input[type=radio]')].find(x => x.getAttribute('ng-model') === ng && x.checked);"
                + " return c ? c.value : ''; }", ngModel);
        return r == null ? "" : r.toString();
    }

    public String regTypeChecked() { return checkedRadio("ConManager.PatientType"); }

    /** Options of a select (placeholder left out). */
    @SuppressWarnings("unchecked")
    public List<String> options(String ngModel) {
        Object r = page.evaluate("(ng) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]'); if (!s) return [];"
                + " return [...s.options].map(o => o.text.replace(/\\s+/g, ' ').trim()).filter(t => t && !/^-*\\s*select\\s*-*$/i.test(t)); }", ngModel);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /**
     * Header labels of the first visible table whose header matches {@code headerRegex}; a header cell holding only a
     * checkbox is reported as "Select checkbox".
     */
    @SuppressWarnings("unchecked")
    public List<String> tableHeaders(String headerRegex) {
        Object r = page.evaluate("(re) => { const rx = new RegExp(re); const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                + "   .find(x => rx.test(((x.querySelector('thead') || {}).innerText || '').replace(/\\s+/g, ' '))); if (!t) return null;"
                + " return [...t.querySelectorAll('thead th')].map(h => { const txt = h.innerText.replace(/\\s+/g, ' ').trim();"
                + "   return txt || (h.querySelector('input[type=checkbox]') ? 'Select checkbox' : ''); }).filter(x => x); }", headerRegex);
        return r instanceof List ? (List<String>) r : null;
    }

    /**
     * Body rows of the first visible table whose header matches {@code headerRegex}, each header -&gt; value. An input in a
     * cell is read from its value (checkbox: "true"/"false"); "__disabled" lists the headers whose input is disabled.
     * Empty list = table shown with no rows; null = table not found.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> tableRows(String headerRegex) {
        Object r = page.evaluate("(re) => { const rx = new RegExp(re); const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)"
                + "   .find(x => rx.test(((x.querySelector('thead') || {}).innerText || '').replace(/\\s+/g, ' '))); if (!t) return null;"
                + " const hs = [...t.querySelectorAll('thead th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim() || (h.querySelector('input[type=checkbox]') ? 'Select checkbox' : ''));"
                + " return [...t.querySelectorAll('tbody tr')].filter(tr => tr.offsetParent !== null && (tr.innerText.trim() || tr.querySelector('input')))"
                + "   .map(tr => { const o = {}; const dis = [];"
                + "     hs.forEach((h, i) => { const c = tr.cells[i]; if (!h || !c) return; const inp = c.querySelector('input,select,textarea');"
                + "       let v = c.innerText; if (inp) v = inp.type === 'checkbox' ? String(inp.checked) : (inp.tagName === 'SELECT' ? ((inp.selectedOptions[0] || {}).text || '') : inp.value);"
                + "       o[h] = String(v).replace(/\\s+/g, ' ').trim(); if (inp && inp.disabled) dis.push(h); });"
                + "     o['__disabled'] = dis.join('|'); return o; }); }", headerRegex);
        if (!(r instanceof List)) return null;
        List<Map<String, String>> out = new ArrayList<>();
        for (Object o : (List<Object>) r) if (o instanceof Map) out.add(new LinkedHashMap<>((Map<String, String>) o));
        return out;
    }

    /** Header regex of the discount list grid. */
    public static final String LIST_GRID = "Approved By";

    public List<Map<String, String>> listRows() {
        List<Map<String, String>> rows = tableRows(LIST_GRID);
        return rows == null ? new ArrayList<>() : rows;
    }

    /** The patient banner ("Name : ... MRN : ..."), "" when none is shown. */
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

    public void setDates(String from, String to) {
        setNgInput(FROM, from);
        setNgInput(TO, to);
    }

    public void setMrn(String mrn) { setNgInput(MRN, mrn); }

    /** Click a radio of {@code ngModel} by value (real click, so its ng-click / ng-change runs). */
    public void clickRadio(String ngModel, String value) {
        page.locator("input[type='radio'][ng-model='" + ngModel + "'][value='" + value + "']").first().click();
        waitForAngular(500);
    }

    public void setRegType(String value) { clickRadio("ConManager.PatientType", value); }

    /** Search; waits for the list request. Returns its HTTP status, 0 = no request was sent. */
    public int search() {
        clearCaptured();
        Response r = clickAndWaitForApi("SearchConcession()", LIST_API, 20000);
        waitForAngular(1500);
        return r == null ? 0 : r.status();
    }

    /** Find Patient where validation should stop it (no request expected). */
    public void findPatientExpectingValidation() { clearCaptured(); clickNg("SearchPatientByMRNo()"); waitForAngular(1500); }

    /**
     * Find Patient; waits for the patient lookup and, when the patient is found, the list refresh it triggers. Returns the
     * lookup's HTTP status, 0 = no lookup was sent.
     */
    public int findPatient() {
        clearCaptured();
        int logBefore = apiLog.size();
        Response r = clickAndWaitForApi("SearchPatientByMRNo()", PATIENT_API, 20000);
        if (r != null) waitForApiSince(logBefore, LIST_API, 8000);
        waitForAngular(1500);
        return r == null ? 0 : r.status();
    }

    /** Poll the API log (no fixed sleep) until a call containing {@code urlPart} appears after {@code logIndex}. */
    protected boolean waitForApiSince(int logIndex, String urlPart, int maxMs) {
        for (int waited = 0; waited <= maxMs; waited += 250) {
            synchronized (apiLog) {
                for (int i = logIndex; i < apiLog.size(); i++) if (apiLog.get(i).contains(urlPart)) return true;
            }
            page.waitForTimeout(250);
        }
        return false;
    }

    /** Click Add; true when the Add screen ({@code #/add-concessionManager}) is shown. */
    public boolean clickAdd() {
        if (!clickNg("AddConcesson()")) return false;
        return waitUntilShown(DiscountManagerAdd.READY) && isOnRoute(DiscountManagerAdd.ROUTE);
    }
}
