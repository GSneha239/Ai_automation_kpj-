package com.kpj.pages.Billing_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; E-Invoice Dashboard &gt; <b>E-Invoice Dashboard</b> — Page Object ({@code #/EinvoiceList}). READ-ONLY: this class
 * types dates and text filters, picks dropdown values, clicks Search, the MRN search button, the three tabs, a Transaction
 * No. link, a Status log "View" link and Print Master Report. It has NO method for the row <b>Re-Submit</b> button (it sends
 * the e-invoice to BeaconX / LHDN again and saves the status through {@code POST /api/EInvoiceList/IUD}).
 *
 * <p>Confirmed live on devhis (v2026.11.25.534) and against {@code App/Billing/controller/EinvoiceListController.js},
 * {@code App/Billing/factory/EInvoiceFactory.js} and the view {@code Billing/Load/EinvoiceList}:</p>
 * <ul>
 *   <li>Filters (ng-model {@code einvoice.*}): MRNo ({@code #textinput}, search button {@code SearchPatientByMRNo()}),
 *       PatientName, LocationID, payertypeid / payerid (Payor Type / Payor — disabled on screen), TransactionType
 *       (Invoice=1, Bill Cancellation=18, CN Cancellation=33, Credit Note=9, CN Refund=31, Debit Note=28 — <i>shown under the
 *       label "E-Invoice Type"</i>), einvoicetype (Consolidated=1, Individual=2 — <i>shown under the label "Transaction Type"</i>),
 *       einvoicestatus (ERROR=3, INVALID=5, RECEIVED BY MIDDLEWARE=1, REJECTED=4, SENT TO TAX AUTHORITY=2, VALID=6),
 *       OriginalEinvoiceNo, FromDate {@code #from} / ToDate {@code #to} (dd/MM/yyyy, ng-change {@code validateDates()}: more
 *       than 31 days or a reversed range raises "Date range should not exceed 31 days." and clears From Date).</li>
 *   <li>Search = {@code fnGetEInvoiceSearchList(); fnGetEInvoiceCancelSearchList(); fnGetEInvoiceSearchLogList();} — three
 *       {@code POST /api/EInvoiceList/Fetch} calls (ExecFlag GETEINVOICELISTSEARCH / GETEINVOICECANCELLISTSEARCH /
 *       GETEINVOICELOGLISTSEARCH).</li>
 *   <li>Tabs: EInvoice Bill List ({@code #einvoice-tab}, rows {@code env in einvoicelist}), EInvoice Cancel Bill List
 *       ({@code #einvoice-cancel-tab}, {@code einvoicecancelbilllist}), EInvoice Bill Log List ({@code #einvoice-log-tab},
 *       {@code einvoiceloglist}). The table under each tab is the "Transaction Summary".</li>
 *   <li>Row: Print icon, Transaction No. link ({@code fnGetEInvoiceDetSearchList(env)}, details panel {@code showDetailsTrans}),
 *       Re-Submit ({@code fnIUDEInvoiceStatus(env)}, disabled when {@code iseinvoicestaging} is 1–4), Status log "View"
 *       ({@code viewStatusLog(env)}, modal {@code #Statusid}).</li>
 *   <li>Print Master Report = {@code PrintReport()} — opens {@code /SQLReport/RegistrationReport/EinvoiceOPDReport.aspx?...}
 *       in a new tab.</li>
 * </ul>
 */
public class EInvoiceDashboard extends BasePage {

    public static final String ROUTE = "#/EinvoiceList";
    public static final String SEL_FROM = "#from";
    public static final String SEL_TO = "#to";
    public static final String SEL_SEARCH = "button[ng-click^='fnGetEInvoiceSearchList();fnGetEInvoiceCancelSearchList()']";
    public static final String SEL_MRN = "input[ng-model='einvoice.MRNo']";
    public static final String SEL_MRN_SEARCH = "[ng-click='SearchPatientByMRNo();']";
    public static final String SEL_NAME = "input[ng-model='einvoice.PatientName']";
    public static final String SEL_ORIG = "input[ng-model='einvoice.OriginalEinvoiceNo']";
    public static final String SEL_PRINT_MASTER = "button[ng-click='PrintReport()']";

    public static final String NG_STATUS = "einvoice.einvoicestatus";
    public static final String NG_CONS_IND = "einvoice.einvoicetype";      // Consolidated / Individual — labelled "Transaction Type"
    public static final String NG_TRAN_TYPE = "einvoice.TransactionType";  // Invoice / Bill Cancellation / … — labelled "E-Invoice Type"
    public static final String NG_LOCATION = "einvoice.LocationID";
    public static final String NG_PAYOR_TYPE = "einvoice.payertypeid";
    public static final String NG_PAYOR = "einvoice.payerid";

    /** The three tabs: label, tab pane id, list held by the controller. */
    public enum Tab {
        BILL("EInvoice Bill List", "einvoice-tab", "einvoicelist"),
        CANCEL("EInvoice Cancel Bill List", "einvoice-cancel-tab", "einvoicecancelbilllist"),
        LOG("EInvoice Bill Log List", "einvoice-log-tab", "einvoiceloglist");
        public final String label, paneId, list;
        Tab(String label, String paneId, String list) { this.label = label; this.paneId = paneId; this.list = list; }
    }

    /** The row action that must never be clicked by this test. */
    public static final String FORBIDDEN_BUTTON = "Re-Submit";

    public EInvoiceDashboard(Page page) { super(page); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const sc=()=>angular.element(document.querySelector(\"[ng-model='einvoice.FromDate']\")).scope();";

    public final List<String> navNotes = new ArrayList<>();
    public long lastLoadMs = -1;

    // ---- navigation ------------------------------------------------------------------------------------------------

    /** Sidebar Billing &rarr; E-Invoice Dashboard &rarr; E-Invoice Dashboard; true once the screen and its lists have loaded. */
    public boolean navigateViaMenu() {
        navNotes.clear();
        try {
            page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(a=>(a.textContent||'').replace(/\\s+/g,' ').trim()==='Billing' && a.offsetParent)",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) { navNotes.add("sidebar 'Billing' entry not shown within 30 s"); return false; }
        long t0 = System.currentTimeMillis();
        if (!linkVisible()) {
            if (!expandMenuEntry(null, "^Billing$")) navNotes.add("'Billing' did not open");
            // The sub-menu and its leaf are both called "E-Invoice Dashboard": open the sub-menu only (the anchor WITHOUT the route).
            Object st = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('a')].find(a=>norm(a.textContent)==='Billing' && vis(a)); const li=b&&b.closest('li'); if(!li) return 'no billing';"
                    + " const sub=[...li.querySelectorAll('a')].find(a=>norm(a.textContent)==='E-Invoice Dashboard' && a.getAttribute('href')!=='" + ROUTE + "' && vis(a)); if(!sub) return 'no sub-menu';"
                    + " if([...sub.closest('li').querySelectorAll(\"a[href='" + ROUTE + "']\")].some(vis)) return 'open'; sub.click(); return 'clicked'; }");
            waitForAngular(1000);
            if (!"open".equals(String.valueOf(st)) && !"clicked".equals(String.valueOf(st))) navNotes.add("E-Invoice Dashboard sub-menu: " + st);
        }
        if (!onRoute()) {
            Locator link = page.locator("a[href='" + ROUTE + "']:visible").first();
            try {
                link.waitFor(new Locator.WaitForOptions().setTimeout(10000));
                link.click(new Locator.ClickOptions().setTimeout(8000));
            } catch (Exception e) {
                navNotes.add("'E-Invoice Dashboard' link not clickable - " + String.valueOf(e.getMessage()).split("\\R")[0]);
                if (!onRoute()) return false;
            }
        }
        boolean ok = waitLoaded();
        lastLoadMs = System.currentTimeMillis() - t0;
        return ok;
    }

    private boolean linkVisible() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll(\"a[href='" + ROUTE + "']\")].some(vis); }"));
    }

    /** Explicit wait: route active, filter scope built, Status / Location lists filled and no $http call in flight. */
    public boolean waitLoaded() {
        try {
            page.waitForFunction("() => /#\\/EinvoiceList/i.test(location.href) && document.querySelector(\"[ng-model='einvoice.FromDate']\")"
                    + " && angular.element(document.querySelector(\"[ng-model='einvoice.FromDate']\")).scope().einvoice",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception e) { navNotes.add("route or filters not rendered within 25 s (" + page.url() + ")"); return false; }
        try {
            page.waitForFunction("() => { const s=document.querySelector(\"select[ng-model='" + NG_STATUS + "']\"); return s && s.options.length>1; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception e) { navNotes.add("E-Invoice Status list not filled within 25 s"); }
        return waitForHttpIdle(60000);
    }

    /** AngularJS reports no pending $http request; false on timeout. */
    public boolean waitForHttpIdle(int timeoutMs) {
        try {
            page.waitForFunction("() => { try { return angular.element(document.body).injector().get('$http').pendingRequests.length===0; } catch(e) { return true; } }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(200));
            return true;
        } catch (Exception e) { return false; }
    }

    public boolean onRoute() { return page.url().contains(ROUTE); }

    // ---- controls --------------------------------------------------------------------------------------------------

    /** Visibility of the main controls (true when visible). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> controls() {
        Object r = page.evaluate("() => {" + JS + " const q=s=>document.querySelector(s);"
                + " const s2=ng=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return false; const c=e.previousElementSibling; return vis(e)||vis(c); };"
                + " const tab=t=>[...document.querySelectorAll('a[data-toggle=tab]')].some(a=>norm(a.textContent)===t && vis(a));"
                + " return { mrn: vis(q(\"" + SEL_MRN + "\")), mrnSearch: vis(q(\"" + SEL_MRN_SEARCH + "\")), patientName: vis(q(\"" + SEL_NAME + "\")),"
                + "  location: s2('" + NG_LOCATION + "'), payorType: s2('" + NG_PAYOR_TYPE + "'), payor: s2('" + NG_PAYOR + "'),"
                + "  eInvoiceTypeLabelled: s2('" + NG_TRAN_TYPE + "'), transactionTypeLabelled: s2('" + NG_CONS_IND + "'), status: s2('" + NG_STATUS + "'),"
                + "  originalInvoiceNo: vis(q(\"" + SEL_ORIG + "\")), fromDate: vis(q('#from')), toDate: vis(q('#to')),"
                + "  printMasterReport: vis(q(\"" + SEL_PRINT_MASTER + "\")), search: vis(q(\"" + SEL_SEARCH + "\")),"
                + "  tabBill: tab('EInvoice Bill List'), tabCancel: tab('EInvoice Cancel Bill List'), tabLog: tab('EInvoice Bill Log List') }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** For every einvoice.* filter: the on-screen label of its form group, keyed by ng-model. */
    @SuppressWarnings("unchecked")
    public Map<String, String> labelOf() {
        Object r = page.evaluate("() => {" + JS + " const o={}; document.querySelectorAll(\"select[ng-model^='einvoice.'],input[ng-model^='einvoice.']\").forEach(e=>{"
                + " let p=e, lab=''; for(let i=0;i<5&&p&&!lab;i++){ p=p.parentElement; const l=p&&[...p.querySelectorAll('label')].find(x=>!x.classList.contains('select2-offscreen'));"
                + "  if(l) lab=norm(l.textContent).replace(/\\*$/,''); } o[e.getAttribute('ng-model')]=lab; }); return o; }");
        Map<String, String> out = new LinkedHashMap<>();
        if (r instanceof Map) ((Map<String, Object>) r).forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    /** The controller's current filter model (einvoice.*). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> model() {
        Object r = page.evaluate("() => {" + JS + " const e=sc().einvoice||{}; const o={};"
                + " ['FromDate','ToDate','LocationID','payertypeid','payerid','TransactionType','einvoicetype','einvoicestatus','MRNo','PatientName','OriginalEinvoiceNo']"
                + "  .forEach(k=>o[k]=(e[k]===undefined?null:e[k])); return o; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Visible text of the option a select (by ng-model) holds. */
    public String selectedText(String ngModel) {
        Object r = page.evaluate("(ng) => { const s=document.querySelector(\"select[ng-model='\"+ng+\"']\"); const o=s&&s.options[s.selectedIndex]; return o?o.text.trim():''; }", ngModel);
        return r == null ? "" : r.toString();
    }

    public boolean disabled(String ngModel) {
        return Boolean.TRUE.equals(page.evaluate("(ng) => { const s=document.querySelector(\"select[ng-model='\"+ng+\"']\"); return !!s && s.disabled; }", ngModel));
    }

    @SuppressWarnings("unchecked")
    public List<String> options(String ngModel) {
        Object r = page.evaluate("(ng) => { const s=document.querySelector(\"select[ng-model='\"+ng+\"']\"); return s?[...s.options].map(o=>o.text.trim()):[]; }", ngModel);
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Pick a dropdown entry the way a user does (open the select2 box, click the entry). "" = done, else what went wrong. */
    public String pick(String ngModel, String text) {
        String r = Select2Pick.pick(page, ngModel, text);
        waitForAngular(400);
        return r;
    }

    // ---- criteria and search ---------------------------------------------------------------------------------------

    /** Type a date like a user: fill, fire change/blur so validateDates() runs, close the calendar. */
    public void typeDate(String selector, String value) {
        Locator box = page.locator(selector);
        box.fill(value);
        box.dispatchEvent("change");
        box.dispatchEvent("blur");
        page.evaluate("() => { document.querySelectorAll('._720kb-datepicker-calendar').forEach(c=>c.classList.remove('_720kb-datepicker-open')); }");
        waitForAngular(400);
    }

    public String fromDate() { return page.locator(SEL_FROM).inputValue(); }
    public String toDate() { return page.locator(SEL_TO).inputValue(); }

    public void typeText(String selector, String value) {
        Locator box = page.locator(selector);
        box.fill(value);
        box.dispatchEvent("change");
        waitForAngular(200);
    }

    /** Click Search and wait until its three list calls have finished; returns the elapsed time in ms. */
    public long search() {
        closeToasts();
        long t0 = System.currentTimeMillis();
        page.locator(SEL_SEARCH).click(new Locator.ClickOptions().setTimeout(10000));
        waitForAngular(400);
        waitForHttpIdle(180000);
        long ms = System.currentTimeMillis() - t0;
        waitForAngular(400);
        return ms;
    }

    /** Type an MRN and click the magnifier beside it; returns the patient line the screen loaded ("" when none). */
    public String findPatient(String mrn) {
        typeText(SEL_MRN, mrn);
        page.locator(SEL_MRN_SEARCH).first().click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(500);
        waitForHttpIdle(30000);
        Object r = page.evaluate("() => {" + JS + " return String(sc().einvoice.PatientFullDtl||''); }");
        return r == null ? "" : r.toString().replaceAll("\\s+", " ").trim();
    }

    /** Clear the text filters (MRN, Patient Name, Original Invoice No). */
    public void clearTextFilters() {
        for (String s : new String[]{SEL_MRN, SEL_NAME, SEL_ORIG}) typeText(s, "");
        page.evaluate("() => {" + JS + " const s=sc(); s.$apply(()=>{ s.einvoice.PatientFullDtl=''; }); }");
    }

    // ---- tabs and the Transaction Summary ---------------------------------------------------------------------------

    public void openTab(Tab t) {
        page.locator("a[data-toggle='tab'][data-target='#" + t.paneId + "']").click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(500);
        waitForHttpIdle(120000);
        waitForAngular(300);
    }

    /** Label of the active tab. */
    public String activeTab() {
        Object r = page.evaluate("() => {" + JS + " const a=[...document.querySelectorAll('li.active > a[data-toggle=tab], a[data-toggle=tab].active')].find(vis); return a?norm(a.textContent):''; }");
        return r == null ? "" : r.toString();
    }

    /** Records the controller holds for a tab. */
    public int rows(Tab t) {
        Object r = page.evaluate("(l) => {" + JS + " const d=sc()[l]; return Array.isArray(d)?d.length:-1; }", t.list);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Given fields of every record of a tab's list. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> data(Tab t, String... fields) {
        Object r = page.evaluate("([l,f]) => {" + JS + " return (sc()[l]||[]).map(x=>{ const o={}; f.forEach(k=>o[k]=x[k]===undefined?null:x[k]); return o; }); }",
                Arrays.asList(t.list, Arrays.asList(fields)));
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /** Header texts of the Transaction Summary table under a tab (the first table in its pane). */
    @SuppressWarnings("unchecked")
    public List<String> headers(Tab t) {
        Object r = page.evaluate("(id) => {" + JS + " const tb=document.querySelector('#'+id+' table'); return tb?[...tb.querySelectorAll('thead th')].filter(vis).map(h=>norm(h.textContent)):[]; }", t.paneId);
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Cell texts of one column (by header) as shown in the Transaction Summary table of a tab. */
    @SuppressWarnings("unchecked")
    public List<String> column(Tab t, String header) {
        Object r = page.evaluate("([id,h]) => {" + JS + " const tb=document.querySelector('#'+id+' table'); if(!tb) return [];"
                + " const hs=[...tb.querySelectorAll('thead th')]; const i=hs.findIndex(x=>norm(x.textContent)===h); if(i<0) return [];"
                + " return [...tb.querySelectorAll('tbody > tr')].filter(vis).map(r=>{ const c=r.children[i]; return c?norm(c.textContent):''; }); }",
                Arrays.asList(t.paneId, header));
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Re-Submit buttons of the Bill List rows: tranno, staging value and enabled state. Read only — never clicked. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> resubmitStates(Tab t) {
        Object r = page.evaluate("(id) => {" + JS + " const tb=document.querySelector('#'+id+' table'); if(!tb) return [];"
                + " return [...tb.querySelectorAll(\"tbody button[ng-click^='fnIUDEInvoiceStatus']\")].map(b=>{ const s=angular.element(b).scope(); const e=s.env||s.envc||{};"
                + "  return { tranno: e.tranno, staging: e.iseinvoicestaging, status: e.envoicestatus, enabled: !b.disabled }; }); }", t.paneId);
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /** Click the Transaction No. link of the first Bill List row; returns its tranno. */
    public String clickFirstTransactionNo() {
        Locator a = page.locator("#" + Tab.BILL.paneId + " table tbody tr a[ng-click^='fnGetEInvoiceDetSearchList']").first();
        String t = a.innerText().trim();
        a.scrollIntoViewIfNeeded();
        a.click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(800);
        waitForHttpIdle(30000);
        return t;
    }

    /** Click the Status log "View" link of the first Bill List row; returns its tranno. */
    public String clickFirstStatusLog() {
        Locator a = page.locator("#" + Tab.BILL.paneId + " table tbody tr a[ng-click^='viewStatusLog']").first();
        Object t = page.evaluate("() => { const a=document.querySelector(\"#" + Tab.BILL.paneId + " table tbody tr a[ng-click^='viewStatusLog']\"); return a?angular.element(a).scope().env.tranno:''; }");
        a.scrollIntoViewIfNeeded();
        a.click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(800);
        waitForHttpIdle(30000);
        return t == null ? "" : t.toString();
    }

    /** Details panel state after a Transaction No. click: shown flag and number of detail records. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> detailsState() {
        Object r = page.evaluate("() => {" + JS + " let s=null; try{ s=sc(); }catch(e){} if(!s) return { onScreen:false };"
                + " const d=s.EinvoiceListDetails||[]; return { onScreen:true, shown: !!s.showDetailsTrans, records: d.length,"
                + "  supplier: d[0]?norm(String(d[0].suppliers_name||'')):'', buyer: d[0]?norm(String(d[0].buyer_name||'')):'' }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Status log modal: visible flag and the BeaconX / LHDN log record counts. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> statusLogState() {
        Object r = page.evaluate("() => {" + JS + " let s=null; try{ s=sc(); }catch(e){} const m=document.getElementById('Statusid');"
                + " return { onScreen: !!s, modal: !!m && (m.classList.contains('in')||m.classList.contains('show')) && vis(m),"
                + "  beacon: s&&Array.isArray(s.Statuslist)?s.Statuslist.length:-1, lhdn: s&&Array.isArray(s.LHDNlist)?s.LHDNlist.length:-1 }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    public void closeStatusLog() {
        page.evaluate("() => { try{ $('#Statusid').modal('hide'); }catch(e){} }");
        waitForAngular(500);
    }

    public void clickPrintMasterReport() {
        page.locator(SEL_PRINT_MASTER).click(new Locator.ClickOptions().setTimeout(8000));
    }

    public void scrollTo(String selector) {
        page.evaluate("(s) => { const e=document.querySelector(s); if(e){ e.scrollIntoView({block:'center'}); } }", selector);
        waitForAngular(250);
    }

    // ---- toasts / alerts -------------------------------------------------------------------------------------------

    public void closeToasts() {
        for (int i = 0; i < 6; i++) {
            Locator x = page.locator("#toast-container .toast-close-button");
            if (x.count() == 0) break;
            try { x.first().click(new Locator.ClickOptions().setTimeout(2000)); } catch (Exception ignore) { }
            waitForAngular(150);
        }
    }

    /** Record every JAlert (toast / warning) the screen raises into window.__eiAlerts. */
    public void spyAlerts() {
        page.evaluate("() => { window.__eiAlerts=[]; const o=window.__eiOrigJAlert||window.JAlert; window.__eiOrigJAlert=o;"
                + " window.JAlert=function(m,t){ window.__eiAlerts.push((t||'')+': '+m); try{ return o.apply(this,arguments); }catch(e){} }; }");
    }

    @SuppressWarnings("unchecked")
    public List<String> spiedAlerts() {
        Object r = page.evaluate("() => window.__eiAlerts || []");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }
}
