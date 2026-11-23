package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory &gt; Purchase &gt; <b>Enquiry Dashboard</b> — Page Object ({@code #/EnquiryDashBoard}). READ-ONLY: the screen
 * itself has no save / approve / cancel action; this class only types search criteria, clicks Search, the statistic
 * tiles, column headers, column filters and the pager.
 *
 * <p>Confirmed live (hisdemo v2026.11.05.424) and against {@code App/Inventory/controller/EnquiryDashboardController.js}:</p>
 * <ul>
 *   <li>Menu: sidebar <b>Inventory</b> &rarr; <b>Purchase</b> &rarr; {@code a[href='#/EnquiryDashBoard']} ("Enquiry Dashboard").
 *       The sidebar builds each level's children only when the level is clicked.</li>
 *   <li>Heading {@code h1#mainHeader} = "Enquiry Dashboard".</li>
 *   <li>Search criteria: {@code input[ng-model='EnqDashboard.FromDate']}, {@code input[ng-model='EnqDashboard.ToDate']}
 *       (720kb date pickers, dd/MM/yyyy, both default to today), {@code select[ng-model='EnqDashboard.StoreId']}
 *       (list STOREBYUSER, defaults to its FIRST entry) and an icon-only Search button
 *       {@code button[ng-click='searchcompleteEnquiry()']} (aria-label "Search").</li>
 *   <li>Search = six {@code POST /API/EnquiryDashBoard/fetch} count calls (EnquiryComplete, EnquiryInComplete,
 *       QuotationComplete, QuotationInComplete, PurchaseComplete, PurchaseInComplete) followed by three
 *       {@code POST /API/EnquiryDashBoard/fetchList} calls that fill the three "Complete" grids.</li>
 *   <li>Statistics tiles: "Complete :n" / "Pending :n" per Enquiry / Quotation / Purchase, each clickable
 *       ({@code completeEnquiryGrid()}, {@code incompleteEnquiryGrid()}, ...). A Pending tile loads and shows the
 *       matching Pending grid (one more fetchList call).</li>
 *   <li>Grids (ui-grid, client-side sorting, column filters, pager with sizes 25/50/75/100/125, grid menu with CSV/PDF
 *       export): {@code #grid1} Enquiry Complete, {@code #grid2} Enquiry Pending, {@code #grid3} Quotation Complete,
 *       {@code #grid4} Quotation Pending, {@code #grid5} Purchase Complete, {@code #grid6} Purchase Pending.
 *       No row-level View / Details / Print action exists.</li>
 * </ul>
 */
public class EnquiryDashboard extends BasePage {

    public static final String ROUTE = "#/EnquiryDashBoard";
    public static final String SEL_FROM = "input[ng-model='EnqDashboard.FromDate']";
    public static final String SEL_TO = "input[ng-model='EnqDashboard.ToDate']";
    public static final String SEL_STORE = "select[ng-model='EnqDashboard.StoreId']";
    public static final String SEL_SEARCH = "button[ng-click='searchcompleteEnquiry()']";

    /** The six grids: DOM id, panel title, the tile that opens it, the record field that identifies a row. */
    public enum Grid {
        ENQUIRY_COMPLETE("grid1", "Enquiry Complete", "completeEnquiryGrid()", "enquiryno", "Enquirydate"),
        ENQUIRY_PENDING("grid2", "Enquiry Pending", "incompleteEnquiryGrid()", "enquiryno", "Enquirydate"),
        QUOTATION_COMPLETE("grid3", "Quotation Complete", "completeQuotationGrid()", "quotationno", "Date"),
        QUOTATION_PENDING("grid4", "Quotation Pending", "incompleteQuotationGrid()", "quotationno", "Date"),
        PURCHASE_COMPLETE("grid5", "Purchase Complete", "completePurchaseGrid()", "PONumber", "PODate"),
        PURCHASE_PENDING("grid6", "Purchase Pending", "incompletePurchaseGrid()", "PONumber", "PODate");

        public final String id, title, tile, keyField, dateField;
        Grid(String id, String title, String tile, String keyField, String dateField) {
            this.id = id; this.title = title; this.tile = tile; this.keyField = keyField; this.dateField = dateField;
        }
    }

    public EnquiryDashboard(Page page) { super(page); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const gridOf=id=>{ const h=document.getElementById(id); if(!h) return null; const el=h.matches('[ui-grid]')?h:h.querySelector('[ui-grid]');"
          + "  const c=el&&angular.element(el).controller('uiGrid'); return c?c.grid:null; };";

    /** Problems and timings met during the last navigation / wait — reported in the step, not just printed. */
    public final List<String> navNotes = new ArrayList<>();
    public String lastMenuPath = "";

    // ---- navigation ------------------------------------------------------------------------------------------------

    /** Sidebar Inventory &rarr; Purchase &rarr; Enquiry Dashboard; true once the screen and its store list have loaded. */
    public boolean navigateViaMenu() {
        navNotes.clear();
        long t0 = System.currentTimeMillis();
        try {
            page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(a=>(a.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory' && a.offsetParent)",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) { navNotes.add("sidebar 'Inventory' entry not shown within 30 s"); return false; }
        if (!linkVisible()) {
            if (!expandMenuEntry(null, "^Inventory$")) navNotes.add("'Inventory' did not open");
            if (!expandMenuEntry("^Inventory$", "^Purchase$")) navNotes.add("'Purchase' under Inventory did not open");
        }
        Locator link = page.locator("a[href='" + ROUTE + "']:visible").first();
        try {
            link.waitFor(new Locator.WaitForOptions().setTimeout(10000));
            link.click(new Locator.ClickOptions().setTimeout(8000));
            lastMenuPath = "Inventory > Purchase > Enquiry Dashboard";
        } catch (Exception e) {
            navNotes.add("'Enquiry Dashboard' link not clickable - " + String.valueOf(e.getMessage()).split("\\R")[0]);
            return false;
        }
        boolean ok = waitLoaded();
        navNotes.add(0, "menu + load " + (System.currentTimeMillis() - t0) / 1000.0 + " s");
        return ok;
    }

    private boolean linkVisible() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll(\"a[href='" + ROUTE + "']\")].some(vis); }"));
    }

    /** Explicit wait: route active, the six grids built, the store list filled and no $http call in flight. */
    public boolean waitLoaded() {
        try {
            page.waitForFunction("() => /#\\/EnquiryDashBoard/i.test(location.href) && ['grid1','grid3','grid5'].every(id=>document.getElementById(id))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { navNotes.add("route or grids not rendered within 20 s (" + page.url() + ")"); return false; }
        try {
            page.waitForFunction("() => { const s=document.querySelector(\"" + SEL_STORE + "\"); return s && s.options.length>0 && s.selectedIndex>=0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { navNotes.add("store list not filled within 20 s"); return false; }
        return waitForHttpIdle(30000);
    }

    /** AngularJS reports no pending $http request; false on timeout. */
    public boolean waitForHttpIdle(int timeoutMs) {
        try {
            page.waitForFunction("() => { try { return angular.element(document.body).injector().get('$http').pendingRequests.length===0; } catch(e) { return true; } }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(250));
            return true;
        } catch (Exception e) { return false; }
    }

    public boolean onRoute() { return page.url().toLowerCase().contains(ROUTE.toLowerCase()); }

    // ---- screen reads ----------------------------------------------------------------------------------------------

    public String heading() {
        Object r = page.evaluate("() => {" + JS + " const h=document.getElementById('mainHeader'); return h?norm(h.textContent):''; }");
        return r == null ? "" : r.toString();
    }

    /** Presence / attributes of the search controls, the tiles and the grid panels. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> controls() {
        Object r = page.evaluate("() => {" + JS
                + " const q=s=>document.querySelector(s); const b=q(\"" + SEL_SEARCH + "\");"
                + " const tiles=[...document.querySelectorAll('[ng-click]')].filter(e=>/^(in)?complete(Enquiry|Quotation|Purchase)Grid\\(\\)$/.test(e.getAttribute('ng-click'))&&vis(e));"
                + " const titles=[...document.querySelectorAll('.box-title,.panel-heading,.panel-title')].filter(vis).map(e=>norm(e.textContent)).filter(t=>t&&t.length<40);"
                + " return { fromDate: vis(q(\"" + SEL_FROM + "\")), toDate: vis(q(\"" + SEL_TO + "\")), store: vis(q(\"" + SEL_STORE + "\")),"
                + "  search: vis(b), searchLabel: b?(b.getAttribute('aria-label')||b.title||norm(b.innerText)):'',"
                + "  fromPlaceholder: (q(\"" + SEL_FROM + "\")||{}).placeholder||'', toPlaceholder: (q(\"" + SEL_TO + "\")||{}).placeholder||'',"
                + "  tiles: tiles.length, panelTitles: [...new Set(titles)] }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    public String fromDate() { return String.valueOf(page.locator(SEL_FROM).inputValue()); }
    public String toDate() { return String.valueOf(page.locator(SEL_TO).inputValue()); }

    public String selectedStore() {
        Object r = page.evaluate("() => { const s=document.querySelector(\"" + SEL_STORE + "\"); const o=s&&s.options[s.selectedIndex]; return o?o.text.trim():''; }");
        return r == null ? "" : r.toString();
    }

    @SuppressWarnings("unchecked")
    public List<String> storeOptions() {
        Object r = page.evaluate("() => [...document.querySelector(\"" + SEL_STORE + "\").options].map(o=>o.text.trim())");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    // ---- criteria and search ---------------------------------------------------------------------------------------

    /**
     * Type a date the way a user does: fill the box, then fire its change/blur so the screen's own date validation runs,
     * then close the calendar popup. An empty string clears the box.
     */
    public void typeDate(String selector, String value) {
        Locator box = page.locator(selector);
        box.fill(value);
        box.dispatchEvent("change");
        box.dispatchEvent("blur");
        page.evaluate("() => { document.querySelectorAll('._720kb-datepicker-calendar').forEach(c=>c.classList.remove('_720kb-datepicker-open')); }");
        waitForAngular(300);
    }

    public void selectStore(String label) {
        page.locator(SEL_STORE).selectOption(new SelectOption().setLabel(label));
        waitForAngular(300);
    }

    /** Click Search and wait (explicitly) until all its chained API calls have finished. */
    public boolean search() {
        closeToasts();
        page.locator(SEL_SEARCH).click(new Locator.ClickOptions().setTimeout(10000));
        waitForAngular(300);
        return waitForHttpIdle(45000);
    }

    // ---- tiles -----------------------------------------------------------------------------------------------------

    /** Tile counts keyed by their ng-click function, e.g. {@code completePurchaseGrid()=47}; -1 when unreadable. */
    @SuppressWarnings("unchecked")
    public Map<String, Integer> tiles() {
        Object r = page.evaluate("() => {" + JS + " const o={};"
                + " [...document.querySelectorAll('[ng-click]')].filter(e=>/^(in)?complete(Enquiry|Quotation|Purchase)Grid\\(\\)$/.test(e.getAttribute('ng-click')))"
                + "  .forEach(e=>{ const m=norm(e.textContent).match(/(\\d+)\\s*$/); o[e.getAttribute('ng-click')]= m?Number(m[1]):-1; }); return o; }");
        Map<String, Integer> out = new LinkedHashMap<>();
        if (r instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) r).entrySet())
            out.put(e.getKey(), e.getValue() instanceof Number ? ((Number) e.getValue()).intValue() : -1);
        return out;
    }

    public boolean clickTile(Grid g) {
        try {
            page.locator("[ng-click='" + g.tile + "']").first().click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) { return false; }
        waitForAngular(300);
        return waitForHttpIdle(30000);
    }

    // ---- grids -----------------------------------------------------------------------------------------------------

    /** Bring a grid's panel into view (for a meaningful screenshot). */
    public void scrollTo(Grid g) {
        page.evaluate("(id) => { const h=document.getElementById(id); const b=h&&(h.closest('.box,.panel')||h); if(b){ b.scrollIntoView({block:'start'}); window.scrollBy(0,-60); } }", g.id);
        waitForAngular(250);
    }

    public boolean gridVisible(Grid g) {
        return Boolean.TRUE.equals(page.evaluate("(id) => {" + JS + " return vis(document.getElementById(id)); }", g.id));
    }

    /** Panel title, column headers, total rows held, rows on the current page, current page and page size. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> gridInfo(Grid g) {
        Object r = page.evaluate("(id) => {" + JS + " const host=document.getElementById(id); const grid=gridOf(id); if(!host||!grid) return {missing:true};"
                + " const box=host.closest('.box,.panel'); const t=box&&box.querySelector('.box-title,.panel-heading,.panel-title');"
                + " return { title: t?norm(t.textContent):'', headers: grid.options.columnDefs.map(c=>c.displayName||c.name||c.field),"
                + "  total: (grid.options.data||[]).length, visible: grid.getVisibleRows().length, page: grid.options.paginationCurrentPage,"
                + "  pageSize: grid.options.paginationPageSize, pageSizes: grid.options.paginationPageSizes,"
                + "  pagerText: norm((host.querySelector('.ui-grid-pager-max-pages-number')||{}).textContent),"
                + "  sorting: grid.options.enableSorting!==false, filtering: !!grid.options.enableFiltering, gridMenu: !!host.querySelector('.ui-grid-menu-button') }; }", g.id);
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** The given fields of every record the grid holds (= the fetchList response). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> gridData(Grid g, String... fields) {
        Object r = page.evaluate("([id, f]) => {" + JS + " const grid=gridOf(id); if(!grid) return [];"
                + " return (grid.options.data||[]).map(x=>{ const o={}; f.forEach(k=>o[k]=x[k]===undefined?null:x[k]); return o; }); }",
                Arrays.asList(g.id, Arrays.asList(fields)));
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /** One field of the rows currently shown (current page, after sorting/filtering), top to bottom. */
    @SuppressWarnings("unchecked")
    public List<String> visibleValues(Grid g, String field) {
        Object r = page.evaluate("([id, f]) => {" + JS + " const grid=gridOf(id); if(!grid) return [];"
                + " return grid.getVisibleRows().map(r=>r.entity[f]==null?'':String(r.entity[f])); }", Arrays.asList(g.id, field));
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Click a column header (ui-grid toggles none &rarr; ascending &rarr; descending). */
    public void clickHeader(Grid g, String headerText) {
        page.locator("#" + g.id + " .ui-grid-header-cell").filter(new Locator.FilterOptions().setHasText(headerText))
                .locator(".ui-grid-cell-contents").first().click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(400);
    }

    /** The sort direction ui-grid currently holds for a column ("asc", "desc" or ""). */
    public String sortDirection(Grid g, String field) {
        Object r = page.evaluate("([id, f]) => {" + JS + " const grid=gridOf(id); const c=grid&&grid.columns.find(c=>c.field===f);"
                + " return c&&c.sort&&c.sort.direction?c.sort.direction:''; }", Arrays.asList(g.id, field));
        return r == null ? "" : r.toString();
    }

    /** Remove any sort the test applied, so later checks see the server order again. */
    public void clearSort(Grid g) {
        page.evaluate("(id) => {" + JS + " const grid=gridOf(id); if(grid){ grid.columns.forEach(c=>{ if(c.sort) c.sort={}; }); grid.refresh(); } }", g.id);
        waitForAngular(300);
    }

    /** Type into the filter box under the column at {@code columnIndex} (0-based); "" clears it. */
    public void filterColumn(Grid g, int columnIndex, String text) {
        page.locator("#" + g.id + " input[ng-model='colFilter.term']").nth(columnIndex).fill(text);
        waitForAngular(700);
    }

    public void setPageSize(Grid g, int size) {
        page.locator("#" + g.id + " select[ng-model='grid.options.paginationPageSize']").selectOption(String.valueOf(size));
        waitForAngular(500);
    }

    public void pager(Grid g, String action) {   // pageNextPageClick / pagePreviousPageClick / pageFirstPageClick / pageLastPageClick
        page.locator("#" + g.id + " button[ng-click='" + action + "()']").click(new Locator.ClickOptions().setTimeout(6000));
        waitForAngular(400);
    }

    /** Text the grid shows when it holds no row ("No records found" or similar), or "" when it shows nothing. */
    public String emptyGridMessage(Grid g) {
        Object r = page.evaluate("(id) => {" + JS + " const h=document.getElementById(id); if(!h) return '';"
                + " const v=h.querySelector('.ui-grid-viewport'); const own=v?norm(v.innerText):'';"
                + " const near=[...(h.closest('.box,.panel')||h).querySelectorAll('.no-data,.nodata,.ui-grid-empty,.empty-message,.alert')].filter(vis).map(e=>norm(e.textContent)).join(' ');"
                + " return (own+' '+near).trim(); }", g.id);
        return r == null ? "" : r.toString();
    }

    /** Row-level actions inside the grids (View / Details / Print / Report links or buttons). */
    @SuppressWarnings("unchecked")
    public List<String> rowActions() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('[id^=grid] .ui-grid-canvas a, [id^=grid] .ui-grid-canvas button, [id^=grid] .ui-grid-canvas [ng-click]')]"
                + "  .map(e=>norm(e.textContent)||e.title||e.getAttribute('ng-click')).filter(Boolean); }");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** View / Details / Print / Report / Reset / Clear controls anywhere on the screen outside the grids. */
    @SuppressWarnings("unchecked")
    public List<String> pageActions(String regex) {
        Object r = page.evaluate("(re) => {" + JS + " const rx=new RegExp(re,'i');"
                + " return [...document.querySelectorAll('button,a.btn,input[type=button],input[type=reset]')].filter(vis)"
                + "  .map(e=>norm(e.innerText||e.value)||e.title||e.getAttribute('aria-label')||'').filter(t=>rx.test(t)); }", regex);
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<String> toasts() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast-message')].map(e=>norm(e.textContent)); }");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    public void closeToasts() {
        for (int i = 0; i < 6; i++) {
            Locator x = page.locator("#toast-container .toast-close-button");
            if (x.count() == 0) break;
            try { x.first().click(new Locator.ClickOptions().setTimeout(2000)); } catch (Exception ignore) { }
            waitForAngular(200);
        }
    }

    /** What sits on top of the centre of the Search button: "" when the button itself, else a description of the cover. */
    public String searchButtonCoveredBy() {
        Object r = page.evaluate("() => { const b=document.querySelector(\"" + SEL_SEARCH + "\"); if(!b) return 'no button';"
                + " b.scrollIntoView({block:'center'}); const rc=b.getBoundingClientRect(); const t=document.elementFromPoint(rc.left+rc.width/2, rc.top+rc.height/2);"
                + " if(!t || b.contains(t)) return ''; const c=t.closest('#toast-container .toast')||t;"
                + " return (c.className||c.tagName)+': '+(c.textContent||'').replace(/\\s+/g,' ').trim().slice(0,90); }");
        return r == null ? "" : r.toString();
    }
}
