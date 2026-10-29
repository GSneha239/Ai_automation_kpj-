package com.kpj.pages.AncillaryServices_page.Linen_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Ancillary Services &gt; Linen &gt; <b>Linen Category Master</b> - Page Object.
 *
 * <p>Confirmed live on devhis (2026-10-29):</p>
 * <ul>
 *   <li>Menu: <b>Ancillary Services</b> -> <b>Linen</b> -> <b>Linen Category Master</b>, link
 *       {@code a[href="#/LinenCategoryMaster"]}; document title "Linen Category Master", H1 "Linen Category".</li>
 *   <li>It is the shared <i>Common Master</i> screen: form {@code #myCommonMasterForm}
 *       ({@code ng-submit=fnIUDCommonMaster()}), <b>Form Name*</b> pre-selected to "Linen Category"
 *       ({@code selectedform.value = LinenCategoryMaster}), <b>Code*</b> ({@code commonmaster.Code}, maxlength 15),
 *       <b>Remark*</b> ({@code commonmaster.Description}, maxlength 250), optional MIMS GUID / MIMS Description /
 *       MIMS Type (select2), buttons <b>Submit</b> and <b>Clear</b>.</li>
 *   <li>Validation is client-side, BEFORE any API call: empty Code -> toastr warning "Please Enter Code";
 *       Code without Remark -> "Please Enter Description". There is no "Do You Want To Save" confirm - a valid
 *       Submit posts straight to {@code POST /api/CommonMaster/IUD} and toasts "Master Saved Successfully".</li>
 *   <li>Listing: ui-grid ({@code ui-grid=gridOptions}) with columns Code / Remark / Status / Edit, per-column
 *       filter boxes on Code / Remark / Status, pager (25/50/75/100/125 per page). It is loaded by
 *       {@code POST /api/CommonMaster/fetch {"ExecFlag":"GRID","MasterCodeFor":"LinenCategoryMaster"}}.
 *       No global search box and no Refresh button are rendered.</li>
 *   <li>A browser RELOAD on this app logs the session off - never reload; navigate via the menu only.</li>
 * </ul>
 */
public class LinenCategoryMaster extends BasePage {

    public LinenCategoryMaster(Page page) { super(page); }

    public static final String ROUTE = "#/LinenCategoryMaster";
    public static final String MASTER_CODE_FOR = "LinenCategoryMaster";
    public static final String TITLE = "Linen Category Master";

    private static final String CODE = "[ng-model='commonmaster.Code']";
    private static final String REMARK = "[ng-model='commonmaster.Description']";
    private static final String GRID = "[ui-grid='gridOptions']";
    /**
     * Any Common-Master write (IUD = insert/update/delete). The fetch calls are reads and stay allowed.
     * Verified live: Submit (INSERT), Edit + Submit (UPDATE) and the grid Status checkbox (STATUS = the screen's
     * deactivate/delete) all go to POST /api/CommonMaster/IUD.
     *
     * <p>Playwright evaluates this as a JavaScript RegExp (regexSource = pattern(), flags from Pattern flags), so the
     * source must stay JavaScript-compatible: no Java inline flags such as (?i). Public so the test can apply the
     * same rule to the requests it records in {@link #lastSubmitApiCalls}.</p>
     */
    public static final Pattern WRITE_API = Pattern.compile(".*/api/[^?#]*[Cc]ommon[Mm]aster/IUD[^?#]*");

    /** Body of the GRID fetch captured while the screen loaded (null = not seen). */
    public String gridApiBody = null;
    public int gridApiStatus = 0;
    /** Write calls that the read-only guard blocked - must stay empty in a read-only run. */
    public final List<String> blockedWrites = Collections.synchronizedList(new ArrayList<>());
    /**
     * Every /api/ request ("METHOD url") the page sent while the last {@link #submitAndGetMessage()} ran. Filled by a
     * page request listener, which also fires for requests the read-only guard then aborts, so a blocked write is
     * listed here AND in {@link #blockedWrites}.
     */
    public final List<String> lastSubmitApiCalls = Collections.synchronizedList(new ArrayList<>());
    private boolean guardOn = false;

    // ---- navigation ------------------------------------------------------

    /** How the menu link was activated on the last navigateViaMenu() ("" = not reached). */
    public String navMethod = "";
    /** First navigation problem seen ("" = none): which stage failed and why. Reported by the test in T1.1. */
    public String navProblem = "";

    /**
     * Ancillary Services -> Linen -> Linen Category Master via REAL clicks, capturing the grid's fetch response
     * on the way. Returns true when the screen is confirmed open.
     *
     * <p>Seen live at a 1536 px window: the app collapses the sidebar (body.sidebar-collapse) and the submenu
     * link can stay un-clickable for Playwright. Then - like MachineMaster.navigateViaMenu() - the SAME menu anchor
     * is activated in-page, and {@link #navMethod} / {@link #navProblem} say so, so the report never hides it.
     * The route change is awaited on location.hash (the SPA only changes the hash), and the screen-ready wait
     * always runs, even when a menu stage failed.</p>
     */
    public boolean navigateViaMenu() {
        navMethod = "";
        navProblem = "";
        Consumer<Response> onResp = r -> {
            try {
                String post = r.request().postData();
                if (r.url().toLowerCase().contains("/api/commonmaster/fetch") && post != null
                        && post.contains("\"GRID\"") && post.contains(MASTER_CODE_FOR)) {
                    gridApiStatus = r.status();
                    gridApiBody = r.text();
                }
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);
        // The menu entries TOGGLE (clicking an open group closes it), so each level is clicked only when the next
        // one is not already showing. A failed stage is recorded and the flow still goes on to the link itself.
        Locator top = page.locator("a", new Page.LocatorOptions().setHasText(Pattern.compile("^\\s*Ancillary Services\\s*$"))).first();
        Locator linen = page.locator("a", new Page.LocatorOptions().setHasText(Pattern.compile("^\\s*Linen\\s*$"))).first();
        Locator link = page.locator("a[href='" + ROUTE + "']").first();
        try {
            top.waitFor(new Locator.WaitForOptions().setTimeout(20000));
            if (!linen.isVisible()) top.click(new Locator.ClickOptions().setTimeout(10000));
        } catch (com.microsoft.playwright.PlaywrightException e) { addNavProblem("open 'Ancillary Services' failed: " + firstLine(e.getMessage())); }
        try {
            if (!link.isVisible()) linen.click(new Locator.ClickOptions().setTimeout(10000));
        } catch (com.microsoft.playwright.PlaywrightException e) { addNavProblem("open 'Linen' failed: " + firstLine(e.getMessage())); }
        try {
            link.click(new Locator.ClickOptions().setTimeout(10000));
            navMethod = "menu click";
        } catch (com.microsoft.playwright.PlaywrightException e) {
            addNavProblem("click 'Linen Category Master' (real click) failed: " + firstLine(e.getMessage()));
            Object ok = page.evaluate("(h) => { const a=[...document.querySelectorAll('a')].find(x=>x.getAttribute('href')===h);"
                    + " if(!a) return false; a.click(); return true; }", ROUTE);
            navMethod = Boolean.TRUE.equals(ok) ? "menu link activated in-page (real click not possible)" : "menu link not found";
        }
        try {
            page.waitForFunction("(h) => (location.hash||'').toLowerCase() === h.toLowerCase()", ROUTE,
                    new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (com.microsoft.playwright.PlaywrightException e) { addNavProblem("route " + ROUTE + " not reached: " + firstLine(e.getMessage())); }
        try {
            if (onRoute()) waitForScreenReady(30000);
        } catch (com.microsoft.playwright.PlaywrightException e) {
            addNavProblem("screen-ready wait failed: " + firstLine(e.getMessage()));
        } finally {
            try { page.offResponse(onResp); } catch (Exception ignore) { }
        }
        closeStraySelect2();
        return onScreen();
    }

    private void addNavProblem(String p) {
        navProblem = navProblem.isEmpty() ? p : navProblem + " ; " + p;
        System.out.println("LinenCategoryMaster.nav: " + p);
    }

    private boolean onRoute() {
        return Boolean.TRUE.equals(page.evaluate("(h) => (location.hash||'').toLowerCase() === h.toLowerCase()", ROUTE));
    }

    private static String firstLine(String m) {
        if (m == null) return "(no message)";
        String l = m.split("\\R", 2)[0].trim();
        return l.length() > 160 ? l.substring(0, 160) + "..." : l;
    }

    /** Wait until the form, the grid and the GRID fetch have all landed (no fixed sleep). */
    public void waitForScreenReady(int timeoutMs) {
        page.locator(CODE).waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
        page.locator(GRID).waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
        try {
            page.waitForFunction("() => { const g=document.querySelector(\"[ui-grid='gridOptions']\"); if(!g||!window.angular) return false;"
                    + " const s=angular.element(g).scope(); const v=angular.element(document.querySelector(\"[ng-model='commonmaster.Code']\")).scope();"
                    + " return !!(s && s.gridOptions && Array.isArray(s.gridOptions.data) && v && v.hdnMasterCodeFor==='" + MASTER_CODE_FOR + "'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception e) { System.out.println("LinenCategoryMaster: grid scope not ready - " + e.getMessage()); }
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000)); }
        catch (Exception ignore) { /* long-poll widgets can keep the network busy; the scope check above is the real gate */ }
    }

    /** Title "Linen Category Master" + route + H1 "Linen Category". */
    public boolean onScreen() {
        Object r = page.evaluate("() => /linencategorymaster/i.test(location.hash||'')"
                + " && /^\\s*linen category master\\s*$/i.test(document.title||'')"
                + " && [...document.querySelectorAll('h1')].some(h=>h.offsetParent!==null && /linen\\s*category/i.test(h.textContent||''))");
        return Boolean.TRUE.equals(r);
    }

    public String currentScreen() {
        Object r = page.evaluate("() => (document.title||'(no title)') + ' | H1=' + ([...document.querySelectorAll('h1')].filter(h=>h.offsetParent!==null).map(h=>h.textContent.replace(/\\s+/g,' ').trim()).join(' / ')||'(none)')");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    public String heading() {
        Object r = page.evaluate("() => ([...document.querySelectorAll('h1')].find(h=>h.offsetParent!==null)||{textContent:''}).textContent.replace(/\\s+/g,' ').trim()");
        return r == null ? "" : r.toString();
    }

    /**
     * select2 drop-downs (pager page-size, MIMS Type) can be left open by autofill/focus and their full-page
     * {@code #select2-drop-mask} then swallows every click. Close them.
     */
    public void closeStraySelect2() {
        try {
            page.evaluate("() => { try { if (window.jQuery) jQuery('select.select2-offscreen').select2('close'); } catch(e){}"
                    + " const m=document.getElementById('select2-drop-mask'); if(m && m.offsetParent!==null) m.style.display='none'; }");
        } catch (Exception ignore) { }
    }

    // ---- read-only guard -------------------------------------------------

    /** Abort (and record) every Common-Master write request - the safety net for read-only runs. */
    public void enableReadOnlyGuard() {
        if (guardOn) return;
        page.route(WRITE_API, (Route route) -> {
            blockedWrites.add(route.request().method() + " " + route.request().url());
            route.abort();
        });
        guardOn = true;
    }

    public void disableReadOnlyGuard() {
        if (!guardOn) return;
        try { page.unroute(WRITE_API); } catch (Exception ignore) { }
        guardOn = false;
    }

    // ---- form ------------------------------------------------------------

    /** Visible-label, presence and limits of every form control - what the report records as "UI elements". */
    @SuppressWarnings("unchecked")
    public Map<String, Object> formInfo() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && e.offsetParent!==null;"
                + " const q=s=>document.querySelector(s); const sc=angular.element(q(\"[ng-model='commonmaster.Code']\")).scope();"
                + " const fs=q(\"select[data-ng-model='selectedform.value']\");"
                + " const btn=t=>[...document.querySelectorAll('button')].find(b=>vis(b) && norm(b.textContent).toLowerCase()===t);"
                + " return {"
                + "  labels: [...document.querySelectorAll('#myCommonMasterForm label')].filter(vis).map(l=>norm(l.textContent)).filter(x=>x).join(' | '),"
                + "  formName: fs ? norm(fs.options[fs.selectedIndex] ? fs.options[fs.selectedIndex].textContent : '') : '(missing)',"
                + "  formNameValue: sc && sc.selectedform ? String(sc.selectedform.value) : '',"
                + "  masterCodeFor: sc ? String(sc.hdnMasterCodeFor||'') : '',"
                + "  codeVisible: vis(q(\"[ng-model='commonmaster.Code']\")), codeMax: (q(\"[ng-model='commonmaster.Code']\")||{}).maxLength||-1,"
                + "  remarkVisible: vis(q(\"[ng-model='commonmaster.Description']\")), remarkMax: (q(\"[ng-model='commonmaster.Description']\")||{}).maxLength||-1,"
                + "  mimsGuid: vis(q(\"[ng-model='commonmaster.mimsGUID']\")), mimsDesc: vis(q(\"[ng-model='commonmaster.mimsDescription']\")),"
                + "  mimsType: !!q(\"[ng-model='commonmaster.mimsTypeId']\"),"
                + "  submit: !!btn('submit'), clear: !!btn('clear'),"
                + "  canAdd: !!(sc && sc.Access && sc.Access.IsAdd), canEdit: !!(sc && sc.Access && sc.Access.IsEdit),"
                + "  action: sc ? String(sc.action||'') : '' }; }");
        return (Map<String, Object>) r;
    }

    public void fillCode(String v)   { page.locator(CODE).fill(v); }
    public void fillRemark(String v) { page.locator(REMARK).fill(v); }

    public String codeValue()   { return page.locator(CODE).inputValue(); }
    public String remarkValue() { return page.locator(REMARK).inputValue(); }

    /** Clear the toast area + the captured-alerts buffer so the next message is unambiguous. */
    public void resetMessages() {
        page.evaluate("() => { window.__alerts=[]; try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); }");
    }

    /**
     * Click <b>Submit</b> and return the first toast/JAlert message that appears (empty if none within 10 s).
     * Requires {@code DevHisBase.interceptAlerts()} to have been installed once on this page.
     */
    public String submitAndGetMessage() {
        resetMessages();
        closeStraySelect2();
        lastSubmitApiCalls.clear();
        Consumer<com.microsoft.playwright.Request> onReq = req -> {
            if (req.url().toLowerCase().contains("/api/")) lastSubmitApiCalls.add(req.method() + " " + req.url());
        };
        page.onRequest(onReq);
        try {
            // A click failure is a real error - let it propagate (DevHisBase.run() records it as a FAIL step).
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit").setExact(true))
                    .click(new Locator.ClickOptions().setTimeout(10000));
            try {
                page.waitForFunction("() => (window.__alerts||[]).length>0"
                        + " || [...document.querySelectorAll('#toast-container .toast-message')].some(e=>e.offsetParent!==null && e.textContent.trim())",
                        null, new Page.WaitForFunctionOptions().setTimeout(10000));
            } catch (com.microsoft.playwright.TimeoutError e) {
                System.out.println("LinenCategoryMaster.submit: no message within 10 s");
                return "";
            }
        } finally {
            try { page.offRequest(onReq); } catch (Exception ignore) { }
        }
        Object t = page.evaluate("() => { const a=window.__alerts||[]; if(a.length) return String(a[0]);"
                + " const el=[...document.querySelectorAll('#toast-container .toast-message')].find(e=>e.offsetParent!==null); return el?el.textContent.trim():''; }");
        return t == null ? "" : t.toString().trim();
    }

    /** CSS class of the visible toast (toast-warning / toast-success / toast-error), for the step evidence. */
    public String toastKind() {
        Object r = page.evaluate("() => { const t=[...document.querySelectorAll('#toast-container .toast')].find(e=>e.offsetParent!==null);"
                + " return t ? (t.className.match(/toast-(success|warning|error|info)/)||['',''])[1] : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click <b>Clear</b> (resetForm()) and wait for Code + Remark to empty. */
    public boolean clickClear() {
        closeStraySelect2();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Clear").setExact(true))
                .click(new Locator.ClickOptions().setTimeout(10000));
        try {
            page.waitForFunction("() => !document.querySelector(\"[ng-model='commonmaster.Code']\").value"
                    + " && !document.querySelector(\"[ng-model='commonmaster.Description']\").value",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- grid ------------------------------------------------------------

    /** Header captions, filter boxes, pager facts and the bound data set of the listing grid. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> gridInfo() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const g=document.querySelector(\"[ui-grid='gridOptions']\");"
                + " const go=angular.element(g).scope().gridOptions||{};"
                + " const heads=[...g.querySelectorAll('.ui-grid-header-cell .ui-grid-header-cell-label')].map(h=>norm(h.textContent)).filter(x=>x);"
                + " const filters=[...g.querySelectorAll('.ui-grid-header-cell')].filter(c=>c.querySelector('input.ui-grid-filter-input'))"
                + "   .map(c=>norm((c.querySelector('.ui-grid-header-cell-label')||{}).textContent));"
                + " const pager=g.querySelector('.ui-grid-pager-panel'); const count=pager?norm((pager.querySelector('.ui-grid-pager-count')||{}).textContent):'';"
                + " const rows=g.querySelectorAll('.ui-grid-render-container-body .ui-grid-row').length;"
                + " const vis=e=>!!e && e.offsetParent!==null;"
                + " const search=[...document.querySelectorAll('input[type=search],input[placeholder*=earch i]')].filter(vis).length;"
                + " const refresh=[...document.querySelectorAll('button,a,i,span')].filter(e=>vis(e) && /refresh|reload/i.test((e.title||'')+' '+(e.getAttribute('ng-click')||'')+' '+norm(e.textContent))).length;"
                + " return { headers: heads.join(' | '), filterColumns: filters.join(' | '), pagerCount: count,"
                + "  pagerVisible: vis(pager), pageSize: String(go.paginationPageSize), pageSizes: (go.paginationPageSizes||[]).join('/'),"
                + "  dataLength: (go.data||[]).length, totalItems: (go.totalItems==null?-1:go.totalItems), renderedRows: rows,"
                + "  globalSearch: search, refreshControls: refresh, gridMenu: !!g.querySelector('.ui-grid-menu-button'),"
                + "  firstRows: (go.data||[]).slice(0,5).map(d=>(d.Code||'')+' / '+(d.Description||'')+' / '+(d.Status)).join(' ; ') }; }");
        return (Map<String, Object>) r;
    }

    /** Number of records the GRID API returned while loading the screen (-1 = not captured / not JSON array). */
    public int gridApiCount() {
        if (gridApiBody == null) return -1;
        String b = gridApiBody.trim();
        if (!b.startsWith("[")) return -1;
        if (b.equals("[]")) return 0;
        Object n = page.evaluate("(s) => { try { const a=JSON.parse(s); return Array.isArray(a)?a.length:-1; } catch(e){ return -1; } }", b);
        return n instanceof Number ? ((Number) n).intValue() : -1;
    }

    /**
     * Type into the <b>Code</b> column filter and return the number of rows the grid keeps visible
     * (via {@code grid.api.core.getVisibleRows}). Pass "" to clear the filter.
     */
    public int filterByCode(String text) {
        Locator f = page.locator(GRID + " .ui-grid-header-cell").filter(new Locator.FilterOptions()
                .setHasText(Pattern.compile("^\\s*Code"))).locator("input.ui-grid-filter-input").first();
        f.fill(text);
        Object n = null;
        try {
            // ui-grid applies the filter term on its own digest - wait until the Code column holds it.
            page.waitForFunction("(t) => { const g=document.querySelector(\"[ui-grid='gridOptions']\"); const iso=angular.element(g).isolateScope();"
                    + " const grid=iso && iso.grid; if(!grid) return false; const c=grid.columns.find(x=>x.field==='Code');"
                    + " return !!c && (c.filters[0].term||'')===t; }",
                    text, new Page.WaitForFunctionOptions().setTimeout(5000));
            n = page.evaluate("() => { const g=document.querySelector(\"[ui-grid='gridOptions']\"); const iso=angular.element(g).isolateScope();"
                    + " const grid=iso && iso.grid; if(!grid) return -1; return grid.api.core.getVisibleRows(grid).length; }");
        } catch (Exception e) { System.out.println("filterByCode: " + e.getMessage()); }
        return n instanceof Number ? ((Number) n).intValue() : -1;
    }

    /** True when a grid row with this exact Code is in the bound data set. */
    public boolean gridHasCode(String code) {
        Object r = page.evaluate("(c) => { const g=document.querySelector(\"[ui-grid='gridOptions']\");"
                + " const d=(angular.element(g).scope().gridOptions||{}).data||[]; return d.some(x=>String(x.Code||'').trim()===c); }", code);
        return Boolean.TRUE.equals(r);
    }

    /** Wait for the grid to contain {@code code} (after a save the screen re-fetches the GRID itself). */
    public boolean waitForGridCode(String code, int timeoutMs) {
        try {
            page.waitForFunction("(c) => { const g=document.querySelector(\"[ui-grid='gridOptions']\");"
                    + " const d=(angular.element(g).scope().gridOptions||{}).data||[]; return d.some(x=>String(x.Code||'').trim()===c); }",
                    code, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) { return false; }
    }

    /** PROBE for diagnostics in a FAIL row: visible fields and buttons. */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const f=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,25)"
                + "   .map(e=>e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('data-ng-model')||e.id||'?')+']');"
                + " const b=[...document.querySelectorAll('button')].filter(e=>e.offsetParent!==null).map(e=>norm(e.textContent)).filter(x=>x).slice(0,15);"
                + " return 'FIELDS: '+f.join(' ; ')+'  ||  BUTTONS: '+b.join(' , '); }");
        return r == null ? "" : r.toString();
    }
}

