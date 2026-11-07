package com.kpj.pages.Pharmacy_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pharmacy &gt; <b>Pharmacy Dashboard</b> — Page Object ({@code #/pharmacyDashboard}). READ-ONLY: nothing here enters,
 * saves, approves or deletes data; the only clicks are the menu links and the widgets' expand/collapse buttons, which
 * just move the widgets around on screen (client-side {@code maxMin()}, no API call).
 *
 * <p>Confirmed live (hisdemo build v2026.11.05.424) and against {@code App/Inventory/controller/pharmacyDashboardController.js}:</p>
 * <ul>
 *   <li>Menu: the link is {@code a[href='#/pharmacyDashboard']} ("Pharmacy Dashboard"). On hisdemo it sits under the
 *       <b>Inventory</b> sidebar entry (there is no top-level "Pharmacy" entry there) — both are tried.</li>
 *   <li>Heading: {@code h1#mainHeader} = "Pharmacy Dashboard".</li>
 *   <li>Widgets: {@code section.DashWin} inside {@code section#hmsDashboardBlock}. The section ids are recycled from the
 *       Front-Office dashboard, so they do NOT match their titles — see {@link Widget}. Each has an
 *       {@code h4.box-title}, an icon-only expand button {@code #<id>Btn} ({@code ng-click="maxMin('<id>Btn')"}, icon
 *       {@code glyphicon-resize-full} / {@code -small}) and a {@code table} whose body rows are
 *       {@code tr[ng-repeat="X in <ScopeList>"]}.</li>
 *   <li>Data: five {@code POST /api/pharmacyDashboard/Fetch} calls on load, one per {@code ExecFlag}
 *       (PendingIndents, CurrentExpireItem, ReOrderItems, ExpiredItem, PendingRefillItem). Each response array is
 *       assigned straight to the scope list the table repeats over.</li>
 *   <li>Expand: {@code maxMin} moves the chosen widget into a new {@code div.full_view} (70%) and the other four into a
 *       {@code div.min_view} (30%); collapsing moves all five into a {@code div.normal_view} at 49.7% width.</li>
 * </ul>
 */
public class PharmacyDashboard extends BasePage {

    public static final String ROUTE = "#/pharmacyDashboard";
    public static final String API_FETCH = "/api/pharmacyDashboard/Fetch";

    /** The five widgets: DOM section id, on-screen title, the Angular scope list its table repeats, and its API ExecFlag. */
    public enum Widget {
        PENDING_REFILL("pendingRefillDateSec", "Pending Refill Date", "PendingRefillItemList", "PendingRefillItem"),
        PENDING_INDENTS("appointList", "Pending Indents", "PendingIndentsList", "PendingIndents"),
        REORDER_ITEMS("queueMgmnt", "Re Order Items", "ReOrderItems", "ReOrderItems"),
        NEAR_EXPIRY("dischrgeSec", "Near Expiry Items", "CurrentExpireItemList", "CurrentExpireItem"),
        EXPIRED_ITEMS("admitList", "Expired Items", "ExpiredItemList", "ExpiredItem");

        public final String id, title, scopeList, execFlag;
        Widget(String id, String title, String scopeList, String execFlag) {
            this.id = id; this.title = title; this.scopeList = scopeList; this.execFlag = execFlag;
        }
        public String buttonId() { return id + "Btn"; }
        public static Widget byExecFlag(String flag) {
            for (Widget w : values()) if (w.execFlag.equalsIgnoreCase(flag)) return w;
            return null;
        }
    }

    public PharmacyDashboard(Page page) { super(page); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    public String lastMenuPath = "";

    // ---- navigation ------------------------------------------------------------------------------------------------

    /**
     * Pharmacy → Pharmacy Dashboard through the sidebar. Tries a "Pharmacy" parent first (as the module is named in the
     * test plan) and falls back to "Inventory" (where hisdemo actually hosts it). Returns true once the dashboard has
     * rendered and its data calls have finished.
     */
    public boolean navigateViaMenu() {
        navNotes.clear();
        long t0 = System.currentTimeMillis();
        // The sidebar builds each level's children only when that level is clicked, so the Pharmacy Dashboard link does
        // NOT exist in the DOM until its parent is expanded. (The first run waited 30 s for the link itself and timed out.)
        // Wait for a top-level parent entry instead, then expand it.
        String parent = null;
        try {
            Object p = page.waitForFunction("() => { if(!window.angular) return null; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const names=[...document.querySelectorAll('a')].filter(vis).map(a=>norm(a.textContent));"
                    + " return names.includes('Pharmacy') ? 'Pharmacy' : names.includes('Inventory') ? 'Inventory' : null; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000)).jsonValue();
            parent = p == null ? null : p.toString();
        } catch (Exception e) {
            navNotes.add("sidebar never showed a 'Pharmacy' or 'Inventory' entry within 30 s");
            return false;
        }
        if (!linkVisible()) {
            if (expandMenuEntry(null, "^" + parent + "$")) lastMenuPath = parent + " > Pharmacy Dashboard";
            else navNotes.add("'" + parent + "' menu entry did not open");
        }
        try {
            page.locator("a[href='#/pharmacyDashboard']:visible").first().waitFor(new Locator.WaitForOptions().setTimeout(10000));
        } catch (Exception e) {
            navNotes.add("'Pharmacy Dashboard' link not shown under '" + parent + "'");
            return false;
        }
        try {
            page.locator("a[href='#/pharmacyDashboard']:visible").first().click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            navNotes.add("menu click failed - " + firstLine(e));
            return false;
        }
        boolean ok = waitForDashboard();
        navNotes.add(0, "menu + load took " + (System.currentTimeMillis() - t0) / 1000.0 + " s");
        return ok;
    }

    /** Problems / timings met by the last navigateViaMenu() / waitForDashboard() — reported in the step, not only the console. */
    public final List<String> navNotes = new ArrayList<>();

    private static String firstLine(Exception e) { return String.valueOf(e.getMessage()).split("\\R")[0]; }

    private boolean linkVisible() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll(\"a[href='#/pharmacyDashboard']\")].some(vis); }"));
    }

    /** True when the URL hash is the dashboard route. */
    public boolean onRoute() { return page.url().toLowerCase().contains(ROUTE.toLowerCase()); }

    /**
     * Explicit wait: the route is active, all five widget sections exist, and Angular's {@code $http} has no request in
     * flight (i.e. the five Fetch calls have answered and been bound). No fixed sleep.
     */
    public boolean waitForDashboard() {
        try {
            page.waitForFunction("() => /#\\/pharmacydashboard/i.test(location.href)", null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) {
            navNotes.add("route #/pharmacyDashboard not reached within 20 s (now at " + page.url() + ")");
            return false;
        }
        try {
            page.waitForFunction("() => ['pendingRefillDateSec','appointList','queueMgmnt','dischrgeSec','admitList'].every(id=>document.getElementById(id))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) {
            navNotes.add("route reached but the five widget sections did not render within 20 s");
            return false;
        }
        if (!waitForHttpIdle(30000)) { navNotes.add("widget data calls still pending after 30 s"); return false; }
        return true;
    }

    /** Wait until AngularJS reports no pending $http requests; false on timeout. */
    public boolean waitForHttpIdle(int timeoutMs) {
        try {
            page.waitForFunction("() => { try { const inj=angular.element(document.body).injector();"
                    + " return inj && inj.get('$http').pendingRequests.length===0; } catch(e) { return true; } }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(250));
            return true;
        } catch (Exception e) { System.out.println("PharmacyDashboard: $http still busy after " + timeoutMs + "ms"); return false; }
    }

    /** True when the dashboard is on screen right now: right route, five widgets rendered and visible. */
    public boolean isOnScreen() {
        return onRoute() && Boolean.TRUE.equals(page.evaluate("() => {" + JS
                + " return ['pendingRefillDateSec','appointList','queueMgmnt','dischrgeSec','admitList'].every(id=>vis(document.getElementById(id))); }"));
    }

    // ---- page-level reads ------------------------------------------------------------------------------------------

    public String heading() {
        Object r = page.evaluate("() => {" + JS + " const h=document.getElementById('mainHeader'); return h?norm(h.textContent):''; }");
        return r == null ? "" : r.toString();
    }

    /** The near-expiry window in months the screen uses (scope ExpiredMonthNo from AncillaryConfigApp), or -1. */
    public int nearExpiryMonths() {
        Object r = page.evaluate("() => { try { const s=angular.element(document.getElementById('hmsDashboardBlock')).scope();"
                + " return Number(s.ExpiredMonthNo)||-1; } catch(e) { return -1; } }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    // ---- widget reads ----------------------------------------------------------------------------------------------

    public boolean isDisplayed(Widget w) {
        return Boolean.TRUE.equals(page.evaluate("(id) => {" + JS + " return vis(document.getElementById(id)); }", w.id));
    }

    public String title(Widget w) {
        Object r = page.evaluate("(id) => {" + JS + " const s=document.getElementById(id); if(!s) return '';"
                + " const h=s.querySelector('.box-title'); return h?norm(h.textContent):''; }", w.id);
        return r == null ? "" : r.toString();
    }

    @SuppressWarnings("unchecked")
    public List<String> headers(Widget w) {
        Object r = page.evaluate("(id) => {" + JS + " const s=document.getElementById(id); if(!s) return [];"
                + " const tr=s.querySelector('thead tr'); return tr?[...tr.children].map(c=>norm(c.textContent)):[]; }", w.id);
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Body rows as cell texts, exactly as rendered (one inner list per {@code tbody tr}). */
    @SuppressWarnings("unchecked")
    public List<List<String>> rows(Widget w) {
        Object r = page.evaluate("(id) => {" + JS + " const s=document.getElementById(id); if(!s) return [];"
                + " return [...s.querySelectorAll('tbody tr')].map(tr=>[...tr.children].map(c=>norm(c.textContent))); }", w.id);
        List<List<String>> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<Object>) r) out.add(new ArrayList<>((List<String>) row));
        return out;
    }

    /** Per body row: inline style, class list and computed text/background colour — used to see whether a row is highlighted. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> rowStyles(Widget w) {
        Object r = page.evaluate("(id) => { const s=document.getElementById(id); if(!s) return [];"
                + " return [...s.querySelectorAll('tbody tr')].map(tr=>{ const cs=getComputedStyle(tr);"
                + "  return {style:tr.getAttribute('style')||'', cls:tr.className||'', color:cs.color, bg:cs.backgroundColor}; }); }", w.id);
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /**
     * The widget's data as Angular holds it (= the API response it was bound from), reduced to the given fields.
     * Read-only: the scope is only read.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> scopeData(Widget w, String... fields) {
        Object r = page.evaluate("([list, fields]) => { try { const s=angular.element(document.getElementById('hmsDashboardBlock')).scope();"
                + " return (s[list]||[]).map(x=>{ const o={}; fields.forEach(f=>o[f]=x[f]===undefined?null:x[f]); return o; }); } catch(e) { return null; } }",
                Arrays.asList(w.scopeList, Arrays.asList(fields)));
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /** Search box / filter control / paging control inside the widget (each true when present). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listControls(Widget w) {
        Object r = page.evaluate("(id) => {" + JS + " const s=document.getElementById(id); if(!s) return {};"
                + " const inputs=[...s.querySelectorAll('input,select,textarea')].filter(vis);"
                + " return { search: inputs.some(i=>/search|filter|find/i.test((i.placeholder||'')+' '+(i.getAttribute('ng-model')||'')+' '+(i.type||''))) || !!s.querySelector('.dataTables_filter'),"
                + "  filter: inputs.some(i=>i.tagName==='SELECT') || !!s.querySelector('[ng-model*=filter i]'),"
                + "  pagination: !!s.querySelector('.pagination,.dataTables_paginate,[uib-pagination],[pagination],.ui-grid-pager-panel'),"
                + "  controls: inputs.length }; }", w.id);
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Column headers that offer sorting (an ng-click or a sort class), empty when none do. */
    @SuppressWarnings("unchecked")
    public List<String> sortableHeaders(Widget w) {
        Object r = page.evaluate("(id) => {" + JS + " const s=document.getElementById(id); if(!s) return [];"
                + " return [...s.querySelectorAll('thead th')].filter(th=>th.getAttribute('ng-click')||/sort/i.test(th.className||'')||th.querySelector('[class*=sort]'))"
                + "  .map(th=>norm(th.textContent)); }", w.id);
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Scroll the widget's title into view (for a meaningful screenshot). */
    public void scrollTo(Widget w) {
        page.evaluate("(id) => { const s=document.getElementById(id); if(s) s.scrollIntoView({block:'start'}); window.scrollBy(0,-60); }", w.id);
        waitForAngular(250);
    }

    public void scrollTop() { page.evaluate("() => window.scrollTo(0,0)"); waitForAngular(200); }

    // ---- expand / collapse -----------------------------------------------------------------------------------------

    /** The expand button's accessible labelling: its visible text, title, aria-label and icon class. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> expandButtonInfo(Widget w) {
        Object r = page.evaluate("(bid) => {" + JS + " const b=document.getElementById(bid); if(!b) return {exists:false};"
                + " const i=b.querySelector('i');"
                + " return {exists:true, visible:vis(b), text:norm(b.innerText), title:b.getAttribute('title')||'',"
                + "  aria:b.getAttribute('aria-label')||'', icon:i?i.className:'', iconTitle:i?(i.getAttribute('title')||''):'' }; }", w.buttonId());
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Real click on the widget's expand/collapse button, then wait for the icon to flip (explicit wait). */
    public boolean clickExpand(Widget w) {
        String before = String.valueOf(expandButtonInfo(w).get("icon"));
        try {
            page.locator("#" + w.buttonId()).scrollIntoViewIfNeeded();
            page.locator("#" + w.buttonId()).click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            System.out.println("PharmacyDashboard.clickExpand(" + w + "): " + e.getMessage());
            return false;
        }
        try {
            page.waitForFunction("([bid, before]) => { const i=document.querySelector('#'+bid+' i'); return i && i.className!==before; }",
                    Arrays.asList(w.buttonId(), before), new Page.WaitForFunctionOptions().setTimeout(5000));
        } catch (Exception e) { System.out.println("PharmacyDashboard.clickExpand(" + w + "): icon did not change"); }
        return true;
    }

    /**
     * Where every widget is right now: the container it sits in (full_view / min_view / normal_view / dashboard),
     * its maximize/minimize class, the icon state and its on-screen box. Keyed by widget.
     */
    @SuppressWarnings("unchecked")
    public Map<Widget, Map<String, Object>> layout() {
        Object r = page.evaluate("(ids) => { const out={}; for(const id of ids){ const s=document.getElementById(id); if(!s){out[id]=null;continue;}"
                + " const p=s.parentElement; const rc=s.getBoundingClientRect(); const i=document.querySelector('#'+id+'Btn i');"
                + " const pc=(p.className||'')+' '+(p.id||'');"
                + " out[id]={ container: /full_view/.test(pc)?'full_view':/min_view/.test(pc)?'min_view':/normal_view/.test(pc)?'normal_view':(p.id==='hmsDashboardBlock'?'dashboard':pc.trim()),"
                + "  state: /minimize/.test(s.className)?'minimize':/maximize/.test(s.className)?'maximize':'',"
                + "  icon: i?(/resize-small/.test(i.className)?'resize-small':/resize-full/.test(i.className)?'resize-full':i.className):'',"
                + "  x:Math.round(rc.left+window.scrollX), y:Math.round(rc.top+window.scrollY), w:Math.round(rc.width), h:Math.round(rc.height),"
                + "  order:[...document.querySelectorAll('section.DashWin')].indexOf(s) }; } return out; }",
                idList());
        Map<Widget, Map<String, Object>> out = new LinkedHashMap<>();
        if (r instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) r;
            for (Widget w : Widget.values()) out.put(w, m.get(w.id) instanceof Map ? (Map<String, Object>) m.get(w.id) : new LinkedHashMap<>());
        }
        return out;
    }

    /** How many full_view / min_view / normal_view wrapper divs the expand logic has created so far (it adds 3 per click). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> wrapperCounts() {
        Object r = page.evaluate("() => ({ full: document.querySelectorAll('#hmsDashboardBlock > .full_view').length,"
                + " min: document.querySelectorAll('#hmsDashboardBlock > .min_view').length,"
                + " normal: document.querySelectorAll('#hmsDashboardBlock > .normal_view').length })");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    private List<String> idList() {
        List<String> ids = new ArrayList<>();
        for (Widget w : Widget.values()) ids.add(w.id);
        return ids;
    }

    // ---- responsive / fixed header ---------------------------------------------------------------------------------

    /**
     * Fixed-header overlap probe at the current viewport. Reports the fixed top navbar's bottom edge, the content
     * wrapper's top padding, and every widget title / table header row that is (a) not visible at all, or (b) covered
     * by the fixed navbar at the top of the page ({@code covered}). {@code coveredOnJump} additionally lists titles hidden
     * when a widget is scrolled to the very top of the viewport (as {@code scrollIntoView} / anchor jumps do).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> headerOverlap() {
        Object r = page.evaluate("() => {" + JS
                + " const nav=[...document.querySelectorAll('.navbar-fixed-top,nav.navbar')].find(n=>getComputedStyle(n).position==='fixed' && n.getBoundingClientRect().top<=1 && n.getBoundingClientRect().width>window.innerWidth*0.5);"
                + " const navBottom=nav?Math.round(nav.getBoundingClientRect().bottom):0;"
                + " const cw=document.querySelector('.content-wrapper'); const pad=cw?parseFloat(getComputedStyle(cw).paddingTop)||0:0;"
                + " const covered=[], coveredOnJump=[], hidden=[];"
                + " const probe=(el,label,list)=>{ if(!vis(el)){ hidden.push(label); return; } if(nav && nav.contains(el)) return; const rc=el.getBoundingClientRect();"
                + "   if(rc.bottom<=0 || rc.top>=window.innerHeight) return;"
                + "   const x=Math.min(window.innerWidth-2, Math.max(1, rc.left+Math.min(rc.width/2, 40))); const y=Math.max(1, rc.top+Math.min(rc.height/2,10));"
                + "   const top=document.elementFromPoint(x,y); if(nav && top && nav.contains(top)) (list||covered).push(label+' (top '+Math.round(rc.top)+'px < header '+navBottom+'px)'); };"
                + " window.scrollTo(0,0);"
                + " const ids={pendingRefillDateSec:'Pending Refill Date',appointList:'Pending Indents',queueMgmnt:'Re Order Items',dischrgeSec:'Near Expiry Items',admitList:'Expired Items'};"
                + " for(const [id,name] of Object.entries(ids)){ const s=document.getElementById(id); if(!s){hidden.push(name+' widget'); continue;}"
                + "   probe(s.querySelector('.box-title'), name+' title @top'); probe(s.querySelector('thead tr'), name+' headers @top'); }"
                + " for(const [id,name] of Object.entries(ids)){ const s=document.getElementById(id); if(!s) continue;"
                + "   s.scrollIntoView({block:'start'}); probe(s.querySelector('.box-title'), name+' title (scrolled to widget)', coveredOnJump); }"
                + " window.scrollTo(0,0);"
                + " return {navBottom, contentPaddingTop:pad, viewport:window.innerWidth+'x'+window.innerHeight, covered, coveredOnJump, hidden}; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Titles and header cells of every widget that are clipped (text wider than its box) or not visible. */
    @SuppressWarnings("unchecked")
    public List<String> clippedTitlesAndHeaders() {
        Object r = page.evaluate("() => {" + JS + " const out=[];"
                + " document.querySelectorAll('#hmsDashboardBlock section.DashWin').forEach(s=>{ const t=s.querySelector('.box-title'); const name=t?norm(t.textContent):s.id;"
                + "  if(!vis(t)) out.push(name+': title not visible');"
                + "  s.querySelectorAll('thead th').forEach(th=>{ if(!vis(th)) out.push(name+': header \"'+norm(th.textContent)+'\" not visible'); }); });"
                + " return out; }");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }
}
