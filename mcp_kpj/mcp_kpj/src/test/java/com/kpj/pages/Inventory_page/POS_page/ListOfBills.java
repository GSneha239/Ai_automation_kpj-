package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory &gt; POS &gt; <b>List Of Bills</b> — Page Object ({@code #/PharmacyBillList}). READ-ONLY: this class only types
 * dates, picks filter values, chooses radio buttons and clicks Search. It has NO method for Submit e-Bill, Generate
 * E-Invoice, SubmitTextOutput, SubmitFTP, Export to Excel, Company Approved Amount, the grid's Print/View cells or a
 * row click — those either write, call an external integration or download a file.
 *
 * <p>Confirmed live on hisdemo (v2026.11.07.433) and against {@code App/Billing/controller/BillListController.js} and
 * {@code App/Billing/factory/BillListFactory.js}:</p>
 * <ul>
 *   <li>Menu: sidebar <b>Inventory</b> &rarr; <b>POS</b> &rarr; {@code a[href='#/PharmacyBillList']} ("List Of Bills");
 *       controller {@code BillListController}; heading {@code h1#mainHeader} = "List Of Bills".</li>
 *   <li>On load the screen calls {@code POST /api/BillListSearch/IUDAttachment} with
 *       {@code {"id":0,"ExecFlag":"CheckCreditAdminEscalation"}} (factory method {@code SaveBillingFiles}) — a write-type
 *       endpoint used for a "pending more than 3 days" prompt. The test's guard blocks it.</li>
 *   <li>Search = {@code button[ng-click='OnSearchBtnClick()']} &rarr; one {@code POST /api/BillListSearch/Fetch}
 *       ({@code ExecFlag:"GetBillList"}); results are paged by the server (PageIndex, RecordCount, 10 per page).</li>
 *   <li>Filters (ng-model): {@code BillList.FromDate}/{@code ToDate} (dd/MM/yyyy, default today), {@code BillList.MRNO},
 *       {@code BillList.BillNO}, {@code BillList.BillStatus} (Settled=1 / Unsettled=2), {@code BillList.payercategoryid}
 *       (reloads the Payor list), Payor (ui-select, {@code BillList.sponsorid}), {@code BillList.LocationID},
 *       {@code BillList.departmentid}, {@code BillList.VisitTypeID}, {@code BillList.applicablefor} (Encounter Type),
 *       {@code BillList.StoreId}, {@code BillList.consolidsponsorid} (Consolidated Payor). Selects are select2-wrapped.</li>
 *   <li>Radios: Type {@code BillList.PTPBCI} — {@code #radiosPT-2} Pharmacy Credit Invoice (2), {@code #radiosMN-3}
 *       Pharmacy Bills (3, default); bill type {@code BillList.PharmacyBillOPDIPD} — {@code #radiosPB-0} OPD,
 *       {@code #radiosPB-1} IPD, {@code #radiosPB-2} External. "Type of Invoice" and "E-Invoice" radios search on click.</li>
 *   <li>Grid {@code #grid1} (ui-grid, {@code gridOptions}); row click calls {@code GetResultEntrydetails(row)} — not used.</li>
 * </ul>
 */
public class ListOfBills extends BasePage {

    public static final String ROUTE = "#/PharmacyBillList";
    public static final String HEADING = "List Of Bills";
    public static final String SEL_FROM = "input[ng-model='BillList.FromDate']";
    public static final String SEL_TO = "input[ng-model='BillList.ToDate']";
    public static final String SEL_SEARCH = "button[ng-click='OnSearchBtnClick()']";
    public static final String GRID = "grid1";

    /** Radio buttons used by the test (id, label). */
    public enum Radio {
        PHARMACY_BILLS("radiosMN-3", "Pharmacy Bills"),
        PHARMACY_CREDIT_INVOICE("radiosPT-2", "Pharmacy Credit Invoice"),
        OPD("radiosPB-0", "OPD"),
        IPD("radiosPB-1", "IPD"),
        EXTERNAL("radiosPB-2", "External");
        public final String id, label;
        Radio(String id, String label) { this.id = id; this.label = label; }
    }

    /** Labels of action buttons that must never be clicked by an automated read-only test. */
    public static final List<String> FORBIDDEN_BUTTONS = List.of("Submit e-Bill", "Generate E-Invoice", "SubmitTextOutput",
            "SubmitFTP", "Export to Excel", "Company Approved Amount");

    public ListOfBills(Page page) { super(page); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const sc=()=>angular.element(document.getElementById('" + GRID + "')).scope();";

    public final List<String> navNotes = new ArrayList<>();
    public long lastLoadMs = -1;

    // ---- navigation ------------------------------------------------------------------------------------------------

    /** Sidebar Inventory &rarr; POS &rarr; List Of Bills; true once the screen, its dropdowns and grid have loaded. */
    public boolean navigateViaMenu() {
        navNotes.clear();
        try {
            page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(a=>(a.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory' && a.offsetParent)",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) { navNotes.add("sidebar 'Inventory' entry not shown within 30 s"); return false; }
        if (!linkVisible()) {
            if (!expandMenuEntry(null, "^Inventory$")) navNotes.add("'Inventory' did not open");
            if (!expandMenuEntry("^Inventory$", "^POS$")) navNotes.add("'POS' under Inventory did not open");
        }
        Locator link = page.locator("a[href='" + ROUTE + "']:visible").first();
        long t0 = System.currentTimeMillis();
        try {
            link.waitFor(new Locator.WaitForOptions().setTimeout(10000));
            link.click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            navNotes.add("'List Of Bills' link not clickable - " + String.valueOf(e.getMessage()).split("\\R")[0]);
            return false;
        }
        boolean ok = waitLoaded();
        lastLoadMs = System.currentTimeMillis() - t0;
        return ok;
    }

    private boolean linkVisible() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll(\"a[href='" + ROUTE + "']\")].some(vis); }"));
    }

    /** Explicit wait: route active, grid scope built, Location list filled and no $http call in flight. */
    public boolean waitLoaded() {
        try {
            page.waitForFunction("() => /#\\/PharmacyBillList/i.test(location.href) && document.getElementById('" + GRID + "')"
                    + " && angular.element(document.getElementById('" + GRID + "')).scope() && angular.element(document.getElementById('" + GRID + "')).scope().BillList",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception e) { navNotes.add("route or grid not rendered within 25 s (" + page.url() + ")"); return false; }
        try {
            page.waitForFunction("() => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='BillList.LocationID'); return s && s.options.length>1; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception e) { navNotes.add("Location list not filled within 25 s"); }
        return waitForHttpIdle(45000);
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

    public String heading() {
        Object r = page.evaluate("() => {" + JS + " const h=document.getElementById('mainHeader'); return h?norm(h.textContent):''; }");
        return r == null ? "" : r.toString();
    }

    // ---- controls --------------------------------------------------------------------------------------------------

    /** Visible filter labels (the on-screen ones, not select2's off-screen accessibility labels). */
    @SuppressWarnings("unchecked")
    public List<String> visibleLabels() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('label')].filter(l=>vis(l)&&!l.classList.contains('select2-offscreen')"
                + " && getComputedStyle(l).clip!=='rect(0px, 0px, 0px, 0px)').map(l=>norm(l.textContent)).filter(Boolean); }");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Accessible names select2 generated for its hidden focus inputs ({@code label[for^=s2id_autogen]}), keyed by "for". */
    @SuppressWarnings("unchecked")
    public Map<String, String> offscreenLabels() {
        Object r = page.evaluate("() => {" + JS + " const o={}; document.querySelectorAll('label.select2-offscreen[for^=s2id_autogen]').forEach(l=>{"
                + " const f=l.getAttribute('for'); if(!/_search$/.test(f) && norm(l.textContent)) o[f]=norm(l.textContent); }); return o; }");
        Map<String, String> out = new LinkedHashMap<>();
        if (r instanceof Map) ((Map<String, Object>) r).forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    /** Element ids used by more than one visible element, e.g. {@code BLUnit=3}. */
    @SuppressWarnings("unchecked")
    public Map<String, Integer> duplicateIds() {
        Object r = page.evaluate("() => { const c={}; document.querySelectorAll('[id]').forEach(e=>{ if(e.offsetParent && !/grid-container$/.test(e.id)) c[e.id]=(c[e.id]||0)+1; });"
                + " const o={}; Object.entries(c).filter(([k,v])=>v>1).forEach(([k,v])=>o[k]=v); return o; }");
        Map<String, Integer> out = new LinkedHashMap<>();
        if (r instanceof Map) ((Map<String, Object>) r).forEach((k, v) -> out.put(k, ((Number) v).intValue()));
        return out;
    }

    /** Position (page coordinates) of each visible filter label: label -> [x, y]. */
    @SuppressWarnings("unchecked")
    public Map<String, int[]> labelPositions() {
        Object r = page.evaluate("() => {" + JS + " const o={}; [...document.querySelectorAll('label[for]')].filter(l=>vis(l)&&!l.classList.contains('select2-offscreen')&&!l.querySelector('input'))"
                + " .forEach(l=>{ const b=l.getBoundingClientRect(); const t=norm(l.textContent); if(!(t in o)) o[t]=[Math.round(b.left+scrollX), Math.round(b.top+scrollY)]; }); return o; }");
        Map<String, int[]> out = new LinkedHashMap<>();
        if (r instanceof Map) ((Map<String, Object>) r).forEach((k, v) -> {
            List<Object> l = (List<Object>) v;
            out.put(k, new int[]{((Number) l.get(0)).intValue(), ((Number) l.get(1)).intValue()});
        });
        return out;
    }

    /** Page y position of the top of the bill grid. */
    public int gridTop() {
        Object r = page.evaluate("() => { const g=document.getElementById('" + GRID + "'); return g?Math.round(g.getBoundingClientRect().top+scrollY):-1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Visible buttons: text, enabled state and their ng-click. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> buttons() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis)"
                + " .filter(b=>!b.closest('.ui-grid-pager-panel') && !b.closest('.ui-grid-canvas'))"
                + " .map(b=>({ text: norm(b.innerText||b.value), title: b.title||b.getAttribute('aria-label')||'', enabled: !b.disabled, ngClick: b.getAttribute('ng-click')||'' })); }");
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /** Presence of the main filter controls (true when visible). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> controls() {
        Object r = page.evaluate("() => {" + JS + " const q=s=>document.querySelector(s); const sel=ng=>[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + " const s2=ng=>{ const e=sel(ng); if(!e) return false; const c=e.previousElementSibling&&e.previousElementSibling.classList.contains('select2-container')?e.previousElementSibling:document.getElementById('s2id_'+e.id); return vis(e)||vis(c); };"
                + " return { fromDate: vis(q(\"" + SEL_FROM + "\")), toDate: vis(q(\"" + SEL_TO + "\")), mrn: vis(q(\"input[ng-model='BillList.MRNO']\")), billNo: vis(q(\"input[ng-model='BillList.BillNO']\")),"
                + "  billStatus: s2('BillList.BillStatus'), payorCategory: s2('BillList.payercategoryid'), payor: vis(q('.ui-select-container')), location: s2('BillList.LocationID'),"
                + "  department: s2('BillList.departmentid'), visitType: s2('BillList.VisitTypeID'), encounterType: s2('BillList.applicablefor'), store: s2('BillList.StoreId'),"
                + "  consolidatedPayor: s2('BillList.consolidsponsorid'),"
                + "  opd: vis(q('#radiosPB-0')), ipd: vis(q('#radiosPB-1')), external: vis(q('#radiosPB-2')), pci: vis(q('#radiosPT-2')), pharmacyBills: vis(q('#radiosMN-3')),"
                + "  search: vis(q(\"" + SEL_SEARCH + "\")), grid: vis(document.getElementById('" + GRID + "')) }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Current filter model values (BillList.*), as the controller holds them. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> model() {
        Object r = page.evaluate("() => {" + JS + " const b=sc().BillList||{}; const pick=['FromDate','ToDate','PTPBCI','PharmacyBillOPDIPD','BillStatus','payercategoryid','sponsorid',"
                + "'LocationID','departmentid','consolidsponsorid','VisitTypeID','applicablefor','StoreId','BillListType','EinvoiceStatus'];"
                + " const o={}; pick.forEach(k=>o[k]=(b[k]===undefined?null:b[k])); return o; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Visible text of the selected option of a select bound to {@code ngModel}. */
    public String selectedText(String ngModel) {
        Object r = page.evaluate("(ng) => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); const o=s&&s.options[s.selectedIndex]; return o?o.text.trim():''; }", ngModel);
        return r == null ? "" : r.toString();
    }

    @SuppressWarnings("unchecked")
    public List<String> options(String ngModel) {
        Object r = page.evaluate("(ng) => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); return s?[...s.options].map(o=>o.text.trim()):[]; }", ngModel);
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Number of entries in the Payor (ui-select) list currently loaded by the controller. */
    public int payorListSize() {
        Object r = page.evaluate("() => {" + JS + " const d=sc().drpSponsor; return Array.isArray(d)?d.length:-1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** id of the checked radio of a group ({@code BillList.PTPBCI} / {@code BillList.PharmacyBillOPDIPD}). */
    public String checkedRadio(String ngModel) {
        Object r = page.evaluate("(ng) => { const r=[...document.querySelectorAll('input[type=radio]')].find(x=>x.getAttribute('ng-model')===ng && x.checked); return r?r.id+'='+r.value:''; }", ngModel);
        return r == null ? "" : r.toString();
    }

    // ---- criteria and search ---------------------------------------------------------------------------------------

    /** Type a date like a user: fill, fire change/blur so the screen's own date checks run, close the calendar. */
    public void typeDate(String selector, String value) {
        Locator box = page.locator(selector);
        box.fill(value);
        box.dispatchEvent("change");
        box.dispatchEvent("blur");
        page.evaluate("() => { document.querySelectorAll('._720kb-datepicker-calendar').forEach(c=>c.classList.remove('_720kb-datepicker-open')); }");
        waitForAngular(300);
    }

    public String fromDate() { return page.locator(SEL_FROM).inputValue(); }
    public String toDate() { return page.locator(SEL_TO).inputValue(); }

    /** Choose a radio button (OPD / IPD / External / Pharmacy Bills / Pharmacy Credit Invoice). */
    public void choose(Radio r) {
        page.locator("#" + r.id).first().check(new Locator.CheckOptions().setForce(true).setTimeout(8000));
        waitForAngular(400);
        waitForHttpIdle(30000);
    }

    /** Click Search and wait (explicitly) until the search call has finished; returns the elapsed time in ms. */
    public long search() {
        closeToasts();
        long t0 = System.currentTimeMillis();
        page.locator(SEL_SEARCH).click(new Locator.ClickOptions().setTimeout(10000));
        waitForAngular(300);
        waitForHttpIdle(120000);
        long ms = System.currentTimeMillis() - t0;
        waitForAngular(300);
        return ms;
    }

    // ---- grid ------------------------------------------------------------------------------------------------------

    /** Rows held, rows on screen, RecordCount reported by the server, current page, page size and visible headers. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> gridInfo() {
        Object r = page.evaluate("() => {" + JS + " const s=sc(); const g=s.gridOptions; const host=document.getElementById('" + GRID + "');"
                + " const el=host&&(host.matches('[ui-grid]')?host:host.querySelector('[ui-grid]')); const c=el&&angular.element(el).controller('uiGrid'); const grid=c?c.grid:null;"
                + " return { rows: (g.data||[]).length, rendered: grid?grid.getVisibleRows().length:-1, recordCount: s.RecordCount===undefined?null:s.RecordCount,"
                + "  page: g.paginationCurrentPage||null, pageSize: g.paginationPageSize||null, pageSizes: g.paginationPageSizes||[],"
                + "  headers: (g.columnDefs||[]).filter(x=>x.visible!==false).map(x=>x.displayName||x.name||x.field),"
                + "  sortable: g.enableSorting!==false, viewportText: norm(((host||{}).querySelector&&host.querySelector('.ui-grid-viewport')||{}).innerText||'').slice(0,120) }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** The given fields of every record the grid holds (the Fetch response for the current page). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> gridData(String... fields) {
        Object r = page.evaluate("(f) => {" + JS + " return (sc().gridOptions.data||[]).map(x=>{ const o={}; f.forEach(k=>o[k]=x[k]===undefined?null:x[k]); return o; }); }",
                Arrays.asList(fields));
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /** Text of the cells of one column as shown on screen (current page), top to bottom. */
    @SuppressWarnings("unchecked")
    public List<String> shownColumn(String header) {
        Object r = page.evaluate("(h) => {" + JS + " const host=document.getElementById('" + GRID + "'); const hs=[...host.querySelectorAll('.ui-grid-header-cell')];"
                + " const i=hs.findIndex(x=>norm(x.innerText)===h); if(i<0) return [];"
                + " return [...host.querySelectorAll('.ui-grid-row')].map(row=>{ const c=row.querySelectorAll('.ui-grid-cell')[i]; return c?norm(c.innerText):''; }); }", header);
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Click a column header to sort (ui-grid client sort: none &rarr; ascending &rarr; descending). */
    public void clickHeader(String headerText) {
        page.locator("#" + GRID + " .ui-grid-header-cell").filter(new Locator.FilterOptions().setHasText(headerText))
                .locator(".ui-grid-cell-contents").first().click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(400);
    }

    public void pager(String action) {   // pageNextPageClick / pagePreviousPageClick / pageFirstPageClick / pageLastPageClick
        page.locator("#" + GRID + " button[ng-click='" + action + "()']").click(new Locator.ClickOptions().setTimeout(6000));
        waitForAngular(400);
        waitForHttpIdle(120000);
    }

    /** Any "no records" style message visible on the screen (grid viewport, alerts, toasts). */
    public String emptyMessage() {
        Object r = page.evaluate("() => {" + JS + " const host=document.getElementById('" + GRID + "');"
                + " const v=host&&host.querySelector('.ui-grid-viewport'); const own=v?norm(v.innerText):'';"
                + " const near=[...document.querySelectorAll('.no-data,.nodata,.ui-grid-empty,.empty-message,.alert,#toast-container .toast-message')].filter(vis)"
                + "   .map(e=>norm(e.textContent)).filter(t=>/no (record|data|bill)|not found/i.test(t)).join(' | ');"
                + " return (/no (record|data|bill)|not found/i.test(own)?own:'') + (near?' '+near:''); }");
        return r == null ? "" : r.toString().trim();
    }

    /** Scroll an element into view for a meaningful screenshot. */
    public void scrollTo(String selector) {
        page.evaluate("(s) => { const e=document.querySelector(s); if(e){ e.scrollIntoView({block:'center'}); } }", selector);
        waitForAngular(250);
    }

    // ---- toasts / alerts -------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<String> toasts() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast')].map(e=>norm(e.innerText)); }");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    public void closeToasts() {
        for (int i = 0; i < 6; i++) {
            Locator x = page.locator("#toast-container .toast-close-button");
            if (x.count() == 0) break;
            try { x.first().click(new Locator.ClickOptions().setTimeout(2000)); } catch (Exception ignore) { }
            waitForAngular(150);
        }
    }

    /** Record every JAlert (toast / warning) the screen raises into window.__lbAlerts. */
    public void spyAlerts() {
        page.evaluate("() => { window.__lbAlerts=[]; const o=window.__lbOrigJAlert||window.JAlert; window.__lbOrigJAlert=o;"
                + " window.JAlert=function(m,t){ window.__lbAlerts.push((t||'')+': '+m); try{ return o.apply(this,arguments); }catch(e){} }; }");
    }

    @SuppressWarnings("unchecked")
    public List<String> spiedAlerts() {
        Object r = page.evaluate("() => window.__lbAlerts || []");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }
}
