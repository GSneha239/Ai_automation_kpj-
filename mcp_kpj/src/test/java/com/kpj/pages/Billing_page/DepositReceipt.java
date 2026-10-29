package com.kpj.pages.Billing_page;

import com.kpj.core.Reasons;
import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; <b>Deposit / Receipt</b> (advance) — Page Object.
 *
 * <p>Menu path (verified live on devhis, 25/10/2026): <b>Billing</b> → <b>Deposit / Receipt</b> (route {@code #/advance},
 * browser title "Deposit Receipt", breadcrumb "DEPOSIT / RECEIPT"). The screen has three panels:</p>
 * <ul>
 *   <li><b>Official Receipt Details</b> — the search filters: MRN {@code advance.MRNo} (+ {@code SearchPatientByMRNo()} and the
 *       Patient Search popup {@code #btnSearchPatient}), Reg. Type radios {@code #radios-11} OPD / {@code #radios-12} IPD
 *       (default) / {@code #radios-13} ALL, From Date checkbox {@code advance1.isselected} (the From/To dates
 *       {@code advance1.fromdate}/{@code advance1.todate} are disabled AND ignored by Search until it is ticked),
 *       Deposit/Receipt Type {@code advance.AdvanceTypeid1}, Payor {@code advance.receivableid} (loads only after a Type is
 *       chosen), Deposit/Receipt Against {@code advance.againsttype1}, Cancelled {@code #checkCancelled}, Search
 *       {@code fnAdvanceSearch()} → {@code POST /api/Advance/fetch}.</li>
 *   <li><b>List Of Deposit / Receipt</b> — a client-side ui-grid (paging 5/10/25/50/75, header-click sorting, a filter box
 *       per column, row selection, footer totals, grid menu with "Clear all filters" + CSV/PDF/Excel export). Row buttons:
 *       Print {@code printReport(row)}, Reprint {@code printReportReprint(row)}, Modify {@code Changedeposite(row)} (enabled
 *       only for a selected row; opens the "Change Deposite Against" dialog).</li>
 *   <li><b>Add Deposit/Receipt</b> — Visit {@code advance.OPDNo} (not starred; filled by the MRN search), Deposit/Receipt
 *       Type* {@code advance.AdvanceTypeid} (locks after selection unless Reg. Type is ALL), Payor* {@code #txtSponsorName}
 *       (read-only; set from the chosen Type, e.g. "self" for Patient), Deposit/Receipt Against* — a <b>ui-select
 *       multi-select</b> {@code advance.againsttypes}, Cash Counter* {@code advance.CashCounterid} (options from
 *       {@code drpCashCounterList}), Restrict consumption for selected encounter* {@code advance.restrictConsumption}
 *       (ticked + disabled), Amount* {@code advance.Balance}, Date {@code advance.AdDate}, Remark {@code advance.Remark},
 *       Cash checkbox {@code TrueValChk}, Save {@code fnSaveadvance()}, Clear {@code CancelForm()}.</li>
 * </ul>
 *
 * <p>The screen loads slowly (10-15 s; the title momentarily reads "Transfer" mid-load). Never reload this page: a browser
 * reload sent the session to {@code Home/Logoff} during the inspection.</p>
 *
 * <p><b>Read-only by design:</b> apart from the original {@link #saveAndOpenReport()}, nothing in this class clicks Save,
 * Print, Reprint, an export entry, or the Change-Against dialog's Save.</p>
 */
public class DepositReceipt extends BasePage {

    public DepositReceipt(Page page) { super(page); }

    public static String ROUTE = "#/advance";
    public String lastVisit = "", lastType = "", lastAgainst = "", lastCounter = "", lastAmount = "";

    // ---- shared JS fragments ---------------------------------------------
    private static final String NORM_JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && e.offsetParent!==null;";
    /** Resolves the List Of Deposit / Receipt ui-grid (through a header cell's scope) into {@code __g}. */
    private static final String GRID_JS =
            "const __g=(()=>{ for(const el of document.querySelectorAll('.ui-grid-header-cell')){ try{ let x=angular.element(el).scope();"
            + " for(let i=0;i<6&&x;i++){ if(x.col&&x.col.grid) return x.col.grid; x=x.$parent; } }catch(e){} } return null; })();";
    private static final String SEARCH_BTN = "button[ng-click='fnAdvanceSearch()']";
    private static final String AGAINST_UISELECT = ".ui-select-container[ng-model='advance.againsttypes']";

    // ---- navigation ------------------------------------------------------

    /** Why the last {@link #navigateTo(String)} failed (reason + URL, title and visible panel headings); "" after a success. */
    public String lastNavDiagnostics = "";

    /** Wait for the lazily-built Deposit / Receipt menu link (hisdemo, 26/10/2026: it appeared ~1.9 s after the Billing click). */
    static final int MENU_LINK_TIMEOUT_MS = 15000;
    /** Wait for the screen itself (hisdemo, 26/10/2026: panels + Search button appeared 10.4 s after the menu click). */
    static final int SCREEN_TIMEOUT_MS = 45000;

    /** Screen-open condition: the {@code #/advance} route, a visible "Official Receipt Details" panel heading and its Search
     *  button. Deliberately independent of the Visit dropdown, which only fills after a patient lookup. */
    private static final String SCREEN_READY_JS =
            "() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && e.offsetParent!==null;"
            + " if(!/#\\/advance\\b/i.test(location.hash)) return false;"
            + " const head=[...document.querySelectorAll('.panel-heading,.box-header')].some(h=>vis(h) && /^official receipt details$/i.test(norm(h.textContent)));"
            + " return head && vis(document.querySelector(\"button[ng-click='fnAdvanceSearch()']\")); }";

    /**
     * Navigate through the application's own menu: <b>Billing</b> → <b>Deposit / Receipt</b>, then wait until the screen
     * has opened (see {@link #onScreen()}).
     *
     * <p>Root cause of the earlier L01 failure (hisdemo build v2026.10.23.285-development-ui-ux): the sidebar builds the
     * Billing sub-menu lazily ({@code MenuClickEvent} fetches it), and the Deposit / Receipt link appeared ~1.9 s after the
     * Billing click — the old code looked for it once after a fixed 1 s, found nothing, and fell back to setting
     * {@code window.location.hash}, which raised nHIS's "Reload page" popup (answered No by the watcher), so the browser
     * stayed on {@code #/LandingPage}. This version waits for the link itself, clicks it for real, and never rewrites the
     * hash or reloads. On failure the reason, URL, title and visible panel headings go to {@link #lastNavDiagnostics} and
     * {@link Reasons}.</p>
     */
    public boolean navigateTo(String baseUrl) {
        lastNavDiagnostics = "";
        try {
            page.waitForFunction("() => !!window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) {
            return navFailed("AngularJS did not load within 15 s (" + firstLine(e.getMessage()) + ")");
        }
        if (!onRoute()) {
            // 1) Billing — expandMenuEntry clicks it only if it is not already open, and waits for its lazily-built children.
            boolean billingOpen = expandMenuEntry(null, "^\\s*billing\\s*$");
            // 2) Deposit / Receipt — wait for the link to exist and be visible, then a real click.
            Locator link = page.locator("a[href='#/advance'] >> visible=true").first();
            try {
                link.waitFor(new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                        .setTimeout(MENU_LINK_TIMEOUT_MS));
                link.click(new Locator.ClickOptions().setTimeout(8000));
            } catch (Exception e) {
                return navFailed("Billing menu " + (billingOpen ? "opened" : "did NOT open (no visible 'Billing' entry with children)")
                        + ", but the Deposit / Receipt link a[href='#/advance'] was not clickable within " + MENU_LINK_TIMEOUT_MS / 1000
                        + " s (" + firstLine(e.getMessage()) + ")");
            }
        }
        // 3) The screen itself — condition-based, no fixed sleep.
        try {
            page.waitForFunction(SCREEN_READY_JS, null,
                    new Page.WaitForFunctionOptions().setTimeout(SCREEN_TIMEOUT_MS).setPollingInterval(250));
        } catch (Exception e) {
            return navFailed("Deposit / Receipt did not finish opening within " + SCREEN_TIMEOUT_MS / 1000
                    + " s - expected route #/advance with a visible 'Official Receipt Details' panel and Search button ("
                    + firstLine(e.getMessage()) + ")");
        }
        return true;
    }

    /** Record why navigation failed, with the page's URL, title and visible panel headings, and return false. */
    private boolean navFailed(String reason) {
        String url = "", title = "";
        List<String> panels = new ArrayList<>();
        try { url = page.url(); title = page.title(); panels = panelHeadings(); } catch (Exception ignore) { }
        lastNavDiagnostics = reason + " | URL=" + url + " | title='" + title + "' | visible panels=" + panels;
        Reasons.add(reason);
        System.out.println("DepositReceipt.nav FAILED: " + lastNavDiagnostics);
        return false;
    }

    private boolean onRoute() {
        try { return page.url().toLowerCase().contains("#/advance"); } catch (Exception e) { return false; }
    }

    /** True when the Deposit / Receipt screen is open: route {@code #/advance}, a visible "Official Receipt Details" panel and
     *  its Search button (verified on devhis and hisdemo). Does not depend on the Visit dropdown. */
    public boolean onScreen() {
        try { return Boolean.TRUE.equals(page.evaluate(SCREEN_READY_JS)); } catch (Exception e) { return false; }
    }

    /** Wait for the List Of Deposit / Receipt panel and its ui-grid header. Records a {@link Reasons} entry on timeout. */
    public boolean waitForListGrid(int timeoutMs) {
        try {
            page.waitForFunction("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " return [...document.querySelectorAll('.panel-heading,.box-header')].some(h=>h.offsetParent!==null && /^list of deposit \\/ receipt$/i.test(norm(h.textContent)))"
                    + "   && !!document.querySelector('.ui-grid-header-cell'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(250));
            return true;
        } catch (Exception e) {
            Reasons.add("List Of Deposit / Receipt grid not rendered within " + timeoutMs / 1000 + " s");
            return false;
        }
    }

    /** Wait for the Add Deposit/Receipt panel: its heading, the Visit select (present — not necessarily populated) and the
     *  Save button. Records a {@link Reasons} entry on timeout. */
    public boolean waitForAddForm(int timeoutMs) {
        try {
            page.waitForFunction("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " return [...document.querySelectorAll('.panel-heading,.box-header')].some(h=>h.offsetParent!==null && /^add deposit\\/receipt$/i.test(norm(h.textContent)))"
                    + "   && !!document.querySelector(\"select[ng-model='advance.OPDNo']\") && !!document.querySelector(\"button[ng-click*='fnSaveadvance']\"); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(250));
            return true;
        } catch (Exception e) {
            Reasons.add("Add Deposit/Receipt form not rendered within " + timeoutMs / 1000 + " s");
            return false;
        }
    }

    /** The browser title ("Deposit Receipt" once loaded). */
    public String title() {
        try { return page.title(); } catch (Exception e) { return ""; }
    }

    /** Visible panel headings, e.g. Official Receipt Details / List Of Deposit / Receipt / Add Deposit/Receipt. */
    public List<String> panelHeadings() {
        return strList(page.evaluate("() => {" + NORM_JS
                + " return [...document.querySelectorAll('.panel-heading,.box-header')].filter(vis).map(h=>norm(h.textContent)).filter(t=>t && t.length<60); }"));
    }

    /** The breadcrumb text ("DEPOSIT / RECEIPT"), or "" when not found. */
    public String breadcrumb() {
        Object r = page.evaluate("() => {" + NORM_JS
                + " const e=[...document.querySelectorAll('body *')].filter(vis).find(x=>x.children.length===0 && /^deposit\\s*\\/\\s*receipt$/i.test(norm(x.textContent)));"
                + " return e ? norm(e.textContent) : ''; }");
        return r == null ? "" : r.toString();
    }

    // ---- low-level helpers -----------------------------------------------

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    /** Real selectOption of the first real option of {@code selNg} (polls). Returns the chosen text or a marker. */
    private String realSelectFirst(String selNg, int timeoutMs) {
        String sel = "select[ng-model='" + selNg + "']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception e) { return "(no-opt)"; }
        Object idx = page.evaluate("(s) => { const e=document.querySelector(s); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
        int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("realSelectFirst " + selNg + " failed: " + e.getMessage()); }
        waitForAngular(700);
        return chosen;
    }

    /**
     * Select an option of a (select2-backed) {@code <select ng-model=ng>} by its visible label — a real Playwright
     * selectOption first, with the Angular ngModel + select2('val') route as the fallback. Returns the selected text.
     */
    private String selectByLabel(String ng, String label) {
        String sel = "select[ng-model='" + ng + "']";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label),
                    new Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectByLabel " + ng + " real select failed (" + firstLine(e.getMessage()) + ") - using ngModel");
            page.evaluate("(a) => { const e=document.querySelector(a.sel); if(!e) return; const o=[...e.options].find(x=>(x.textContent||'').trim()===a.label); if(!o) return;"
                    + " e.value=o.value; const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(o.value); c.$render(); }"
                    + " try{ if(window.jQuery){ jQuery(e).trigger('change'); jQuery(e).select2('val', o.value); } }catch(x){} e.dispatchEvent(new Event('change',{bubbles:true})); }",
                    Map.of("sel", sel, "label", label));
        }
        waitForAngular(800);
        return selectedText(ng);
    }

    /** The visible text of the selected option of {@code <select ng-model=ng>}. */
    public String selectedText(String ng) {
        Object r = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return ''; return ((e.options[e.selectedIndex]||{}).textContent||'').trim(); }",
                "select[ng-model='" + ng + "']");
        return r == null ? "" : r.toString();
    }

    /** All option texts of {@code <select ng-model=ng>} (empty list when the select is missing). */
    public List<String> selectOptions(String ng) {
        return strList(page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return []; return [...e.options].map(o=>(o.textContent||'').replace(/\\s+/g,' ').trim()); }",
                "select[ng-model='" + ng + "']"));
    }

    // ---- Official Receipt Details: search filters --------------------------

    /** Current filter state: regType (OPD/IPD/ALL), dateTicked, from, to, fromDisabled, toDisabled, cancelled, mrn. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> filterState() {
        Object r = page.evaluate("() => { const q=s=>document.querySelector(s);"
                + " const rad=[['#radios-11','OPD'],['#radios-12','IPD'],['#radios-13','ALL']].find(x=>q(x[0]) && q(x[0]).checked);"
                + " const f=q(\"input[ng-model='advance1.fromdate']\"), t=q(\"input[ng-model='advance1.todate']\"), d=q(\"input[ng-model='advance1.isselected']\"),"
                + " c=q('#checkCancelled'), m=q(\"input[ng-model='advance.MRNo']\");"
                + " return { regType: rad?rad[1]:'', dateTicked: !!(d&&d.checked), from: f?f.value:'', to: t?t.value:'',"
                + "   fromDisabled: !!(f&&f.disabled), toDisabled: !!(t&&t.disabled), cancelled: !!(c&&c.checked), mrn: m?m.value:'' }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Click the Reg. Type radio: "OPD", "IPD" or "ALL". */
    public void setRegType(String which) {
        String id = "OPD".equalsIgnoreCase(which) ? "#radios-11" : "ALL".equalsIgnoreCase(which) ? "#radios-13" : "#radios-12";
        page.locator(id).click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(600);
    }

    /** Tick / untick the From Date checkbox ({@code advance1.isselected}) — the dates are ignored by Search while it is unticked. */
    public void setDateFilter(boolean enabled) {
        Locator cb = page.locator("input[ng-model='advance1.isselected']").first();
        if (cb.isChecked() != enabled) cb.click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(500);
    }

    /** Tick the From Date checkbox and set the From/To dates (dd/MM/yyyy). */
    public void setDateRange(String from, String to) {
        setDateFilter(true);
        setNg("advance1.fromdate", from);
        setNg("advance1.todate", to);
        dismissDatePicker();
        waitForAngular(400);
    }

    /** Tick / untick the Cancelled filter. */
    public void setCancelled(boolean cancelled) {
        Locator cb = page.locator("#checkCancelled");
        if (cb.isChecked() != cancelled) cb.click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(500);
    }

    /** Type an MRN into the filter's MRN box (does not run the patient lookup). */
    public void setMrnFilter(String mrn) {
        setNg("advance.MRNo", mrn == null ? "" : mrn);
        waitForAngular(300);
    }

    /** Choose the filter's Deposit/Receipt Type (e.g. "Patient"); its Payor list loads afterwards. Returns the selected text. */
    public String setFilterType(String label) {
        String t = selectByLabel("advance.AdvanceTypeid1", label);
        waitForAngular(2500);   // fngetCompany() loads the Payor list
        return t;
    }

    /** Result of one Search: the HTTP status / error of {@code POST /api/Advance/fetch}, plus the grid afterwards. */
    public static final class SearchResult {
        public int status = -1;
        public String error = "";
        public int rowsBefore = -1, rows = -1;
        public String pager = "";
        public long ms;
        public boolean httpOk() { return status == 200; }
        public String summary() {
            return "HTTP " + (status < 0 ? "(no response)" : String.valueOf(status)) + ", " + rows + " row(s)"
                    + (pager.isEmpty() ? "" : " [" + pager + "]") + ", " + ms + " ms"
                    + (error.isEmpty() ? "" : " | server error: " + error);
        }
    }

    /** Click <b>Search</b> ({@code fnAdvanceSearch()}) and wait for {@code /api/Advance/fetch}; records its status/error. */
    public SearchResult search(int timeoutMs) {
        SearchResult r = new SearchResult();
        r.rowsBefore = gridDataCount();
        long t0 = System.currentTimeMillis();
        try {
            Response resp = page.waitForResponse(
                    res -> res.url().contains("/api/Advance/fetch") && "POST".equalsIgnoreCase(res.request().method()),
                    new Page.WaitForResponseOptions().setTimeout(timeoutMs),
                    () -> page.locator(SEARCH_BTN).first().click(new Locator.ClickOptions().setTimeout(8000)));
            r.status = resp.status();
            if (r.status != 200) {
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { }
                r.error = extractServerError(body);
            }
        } catch (Exception e) {
            r.error = "no /api/Advance/fetch response within " + timeoutMs + " ms (" + firstLine(e.getMessage()) + ")";
        }
        waitForAngular(2000);
        r.ms = System.currentTimeMillis() - t0;
        r.rows = gridDataCount();
        r.pager = pagerText();
        System.out.println("DepositReceipt.search: " + r.summary());
        return r;
    }

    private static String extractServerError(String body) {
        if (body == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"ExceptionMessage\"\\s*:\\s*\"(.*?)\"").matcher(body);
        String first = m.find() ? m.group(1) : body.replaceAll("\\s+", " ").trim();
        return first.length() > 300 ? first.substring(0, 300) : first;
    }

    // ---- List Of Deposit / Receipt: grid ----------------------------------

    /** Rows loaded into the grid by the last search (-1 when the grid is not found). */
    public int gridDataCount() {
        Object r = page.evaluate("() => {" + GRID_JS + " return __g ? (__g.options.data||[]).length : -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Rows left after the column filters, across ALL pages.
     *  <p>Uses {@code grid.rows[].visible} (set by the filters, untouched by paging). The previous implementation used
     *  {@code api.core.getVisibleRows()}, which with paging on returns only the CURRENT page's rows — that is why L14 saw
     *  "only 5 row(s)" while the grid held 37 (verified on hisdemo, 26/10/2026: 63 rows loaded, getVisibleRows() = 5).</p> */
    public int gridVisibleCount() {
        Object r = page.evaluate("() => {" + GRID_JS + " return __g ? __g.rows.filter(x=>x.visible).length : -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Displayed text of the {@code header} column's cells in the rows rendered on the CURRENT page, top to bottom
     *  (what the user actually sees, e.g. "70,000.00"). */
    public List<String> columnCellTexts(String header) {
        return strList(page.evaluate("(h) => {" + NORM_JS
                + " const hc=[...document.querySelectorAll('.ui-grid-header-cell')].find(c=>{ const l=c.querySelector('.ui-grid-header-cell-label'); return l && norm(l.textContent)===h; });"
                + " if(!hc) return []; const cls=[...hc.classList].find(x=>/^ui-grid-coluiGrid-/.test(x)); if(!cls) return [];"
                + " return [...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(vis)"
                + "   .map(r=>{ const c=r.querySelector('.ui-grid-cell.'+cls); return c ? norm(c.textContent) : ''; }); }", header));
    }

    /** The ui-grid column type used for sorting the {@code header} column ("string", "number", ...; "" when unknown). */
    public String columnType(String header) {
        Object r = page.evaluate("(h) => {" + GRID_JS + " const c=__g && __g.columns.find(x=>x.displayName===h); return c && c.colDef ? (c.colDef.type||'') : ''; }", header);
        return r == null ? "" : r.toString();
    }

    /** Number of {@code /api/} requests the page has made so far (Resource Timing) — compare before/after an action to
     *  tell client-side behaviour (no new call) from server-side. */
    public int apiRequestCount() {
        Object r = page.evaluate("() => performance.getEntriesByType('resource').filter(e=>/\\/api\\//.test(e.name)).length");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Rows actually rendered on the current page. */
    public int renderedRowCount() {
        Object r = page.evaluate("() => [...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(e=>e.offsetParent!==null).length");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** The pager text, e.g. "1 - 5 of 42 items". */
    public String pagerText() {
        Object r = page.evaluate("() => { const e=document.querySelector('.ui-grid-pager-count-container'); return e ? (e.textContent||'').replace(/\\s+/g,' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    public int currentPage() {
        Object r = page.evaluate("() => {" + GRID_JS + " return __g ? (__g.options.paginationCurrentPage||0) : -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    public int totalPages() {
        Object r = page.evaluate("() => {" + GRID_JS + " try{ return __g ? __g.api.pagination.getTotalPages() : -1; }catch(e){ return -1; } }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Displayed names of the grid columns that are shown (selection column excluded), in order. */
    public List<String> visibleColumnHeaders() {
        return strList(page.evaluate("() => {" + GRID_JS + " if(!__g) return [];"
                + " return __g.columns.filter(c=>c.visible!==false && c.name!=='selectionRowHeaderCol' && !/treeBaseRowHeaderCol/.test(c.name)).map(c=>c.displayName); }"));
    }

    /**
     * The grid rows as maps of the business fields: ReceiptNo, MRNo, Company, visitno, AgainstType, Date1, TotalBillAmount,
     * AllocatedConsumedAmount, BalanceAmount, AdvanceType. Data order (not the display sort order) — use
     * {@link #columnCellTexts(String)} for what is displayed.
     *
     * @param visibleOnly true = rows left after the column filters (all pages); false = every row the search loaded
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> gridRows(boolean visibleOnly) {
        Object r = page.evaluate("(v) => {" + GRID_JS + " if(!__g) return [];"
                + " const rows = v ? __g.rows.filter(x=>x.visible).map(x=>x.entity) : (__g.options.data||[]);"
                + " return rows.map(e=>({ReceiptNo:(e.ReceiptNo==null?'':(''+e.ReceiptNo).trim()), MRNo:(e.MRNo==null?'':(''+e.MRNo).trim()), Company:e.Company||'',"
                + "   visitno:e.visitno||'', AgainstType:e.AgainstType||'', Date1:e.Date1||'', TotalBillAmount:+e.TotalBillAmount||0,"
                + "   AllocatedConsumedAmount:+e.AllocatedConsumedAmount||0, BalanceAmount:+e.BalanceAmount||0, AdvanceType:e.AdvanceType||''})); }", visibleOnly);
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) if (o instanceof Map) out.add((Map<String, Object>) o);
        return out;
    }

    /** Click a column header (real click) to sort it; returns the column's sort direction afterwards: asc / desc / none. */
    public String sortByHeader(String header) {
        Object tagged = page.evaluate("(h) => {" + NORM_JS
                + " const cell=[...document.querySelectorAll('.ui-grid-header-cell')].find(c=>{ const l=c.querySelector('.ui-grid-header-cell-label'); return l && norm(l.textContent)===h; });"
                + " if(!cell) return false; const t=cell.querySelector('.ui-grid-header-cell-label')||cell.querySelector('.ui-grid-cell-contents');"
                + " document.querySelectorAll('[data-dr-sort]').forEach(e=>e.removeAttribute('data-dr-sort')); t.setAttribute('data-dr-sort','1'); return true; }", header);
        if (!Boolean.TRUE.equals(tagged)) { Reasons.add("Column header '" + header + "' not found in the grid"); return "(no-header)"; }
        page.locator("[data-dr-sort='1']").first().click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(900);
        Object dir = page.evaluate("(h) => {" + GRID_JS + " const c=__g && __g.columns.find(x=>x.displayName===h); return (c && c.sort && c.sort.direction) ? c.sort.direction : 'none'; }", header);
        return dir == null ? "none" : dir.toString();
    }

    /** The {@code header} column's current sort direction (asc / desc / none) — read only, nothing is clicked. */
    public String sortDirection(String header) {
        Object dir = page.evaluate("(h) => {" + GRID_JS + " const c=__g && __g.columns.find(x=>x.displayName===h); return (c && c.sort && c.sort.direction) ? c.sort.direction : 'none'; }", header);
        return dir == null ? "none" : dir.toString();
    }

    /** Click a pager button: "first", "previous", "next" or "last". Returns false when it is missing or disabled. */
    public boolean clickPager(String which) {
        String fn = "first".equalsIgnoreCase(which) ? "pageFirstPageClick()" : "previous".equalsIgnoreCase(which) ? "pagePreviousPageClick()"
                : "last".equalsIgnoreCase(which) ? "pageLastPageClick()" : "pageNextPageClick()";
        Locator b = page.locator("button[ng-click='" + fn + "']").first();
        try {
            if (b.count() == 0 || b.isDisabled()) return false;
            b.click(new Locator.ClickOptions().setTimeout(5000));
            waitForAngular(700);
            return true;
        } catch (Exception e) {
            System.out.println("clickPager(" + which + "): " + firstLine(e.getMessage()));
            return false;
        }
    }

    /** Choose the "items per page" size. Returns the selected text. */
    public String setPageSize(int size) {
        String t = selectByLabel("grid.options.paginationPageSize", String.valueOf(size));
        waitForAngular(700);
        return t;
    }

    /** Type {@code term} into the filter box under {@code header} (real fill); returns the rows left (all pages). */
    public int filterColumn(String header, String term) {
        Object tagged = page.evaluate("(h) => {" + NORM_JS
                + " const cell=[...document.querySelectorAll('.ui-grid-header-cell')].find(c=>{ const l=c.querySelector('.ui-grid-header-cell-label'); return l && norm(l.textContent)===h; });"
                + " const i=cell && cell.querySelector(\"input[ng-model='colFilter.term']\"); if(!i) return false;"
                + " document.querySelectorAll('[data-dr-filter]').forEach(e=>e.removeAttribute('data-dr-filter')); i.setAttribute('data-dr-filter','1'); return true; }", header);
        if (!Boolean.TRUE.equals(tagged)) { Reasons.add("No filter box under column '" + header + "'"); return -1; }
        page.locator("[data-dr-filter='1']").first().fill(term == null ? "" : term, new Locator.FillOptions().setTimeout(5000));
        waitForAngular(1200);
        return gridVisibleCount();
    }

    /** Open the grid menu (≡), read its entries, and close it again — nothing is clicked inside it. */
    public List<String> gridMenuItems() {
        Locator icon = page.locator(".ui-grid-menu-button .ui-grid-icon-container").first();
        try {
            icon.click(new Locator.ClickOptions().setTimeout(5000));
            waitForAngular(800);
            List<String> items = strList(page.evaluate("() => {" + NORM_JS
                    + " return [...new Set([...document.querySelectorAll('.ui-grid-menu-items li')].map(l=>norm(l.textContent)).filter(Boolean))]; }"));
            icon.click(new Locator.ClickOptions().setTimeout(5000));
            waitForAngular(400);
            return items;
        } catch (Exception e) {
            Reasons.add("Grid menu could not be opened: " + firstLine(e.getMessage()));
            return new ArrayList<>();
        }
    }

    /** Grid menu → "Clear all filters" (real click). Returns the rows left afterwards (all pages). */
    public int clearAllFiltersViaMenu() {
        Locator icon = page.locator(".ui-grid-menu-button .ui-grid-icon-container").first();
        try {
            icon.click(new Locator.ClickOptions().setTimeout(5000));
            waitForAngular(800);
            Object tagged = page.evaluate("() => {" + NORM_JS
                    + " const li=[...document.querySelectorAll('.ui-grid-menu-items li')].find(l=>/^clear all filters$/i.test(norm(l.textContent)));"
                    + " if(!li) return false; const b=li.querySelector('button')||li; b.setAttribute('data-dr-clear','1'); return true; }");
            if (!Boolean.TRUE.equals(tagged)) {
                icon.click();
                Reasons.add("'Clear all filters' is not in the grid menu");
                return -1;
            }
            page.locator("[data-dr-clear='1']").first().click(new Locator.ClickOptions().setTimeout(5000));
            waitForAngular(1000);
        } catch (Exception e) {
            Reasons.add("Clear all filters failed: " + firstLine(e.getMessage()));
        }
        return gridVisibleCount();
    }

    /** Footer totals keyed by column header (Deposit/Receipt, Allocated/consumed amount, Balance), as displayed. */
    @SuppressWarnings("unchecked")
    public Map<String, String> footerTotals() {
        Object r = page.evaluate("() => {" + NORM_JS + " const out={};"
                + " document.querySelectorAll('.ui-grid-render-container-body .ui-grid-footer-cell').forEach(fc=>{"
                + "   const cls=[...fc.classList].find(x=>/^ui-grid-coluiGrid-/.test(x)); if(!cls) return;"
                + "   const h=document.querySelector('.ui-grid-header-cell.'+cls+' .ui-grid-header-cell-label'); const t=norm(fc.textContent);"
                + "   if(h && t) out[norm(h.textContent)]=t; });"
                + " return out; }");
        Map<String, String> out = new LinkedHashMap<>();
        if (r instanceof Map) ((Map<String, Object>) r).forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    /** Sum of a numeric field over the rows the search loaded (visibleOnly=false) or over the filtered rows (true). */
    public double sumField(String field, boolean visibleOnly) {
        Object r = page.evaluate("(a) => {" + GRID_JS + " if(!__g) return 0;"
                + " const rows = a.v ? __g.rows.filter(x=>x.visible).map(x=>x.entity) : (__g.options.data||[]);"
                + " return rows.reduce((s,e)=>s+(+e[a.f]||0),0); }", Map.of("f", field, "v", visibleOnly));
        return r instanceof Number ? ((Number) r).doubleValue() : 0;
    }

    /** selected = selected rows; enabledModify = Modify (Change Deposit/Receipt Against) buttons that are enabled on this page. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> modifyButtonsState() {
        Object r = page.evaluate("() => {" + GRID_JS
                + " const bs=[...document.querySelectorAll(\"button[ng-click*='Changedeposite']\")].filter(b=>b.offsetParent!==null);"
                + " return { selected: __g ? __g.api.selection.getSelectedRows().length : -1, buttons: bs.length, enabledModify: bs.filter(b=>!b.disabled).length }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Tick the first rendered row's selection box (real click). Returns the number of selected rows afterwards. */
    public int selectFirstRow() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('.ui-grid-render-container-left .ui-grid-row .ui-grid-selection-row-header-buttons')].find(x=>x.offsetParent!==null);"
                + " document.querySelectorAll('[data-dr-row]').forEach(e=>e.removeAttribute('data-dr-row')); if(!b) return false; b.setAttribute('data-dr-row','1'); return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("[data-dr-row='1']").first().click(new Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("selectFirstRow: click failed - " + firstLine(e.getMessage())); }
        } else {
            Reasons.add("No row selection box rendered in the grid");
        }
        waitForAngular(700);
        Object n = page.evaluate("() => {" + GRID_JS + " return __g ? __g.api.selection.getSelectedRows().length : -1; }");
        return n instanceof Number ? ((Number) n).intValue() : -1;
    }

    /** Unselect every grid row (client-side only). */
    public void clearRowSelection() {
        page.evaluate("() => {" + GRID_JS + " if(!__g) return; try{ __g.api.selection.clearSelectedRows(); }catch(e){}"
                + " try{ const s=angular.element(document.querySelector('.ui-grid')).scope(); if(s && !s.$$phase && !(s.$root&&s.$root.$$phase)) s.$apply(); }catch(e){} }");
        waitForAngular(400);
    }

    /** Click the enabled Modify button (real click) and wait for the "Change Deposite Against" dialog; returns its text + controls, or "" when it did not open. */
    public String openChangeAgainstDialog() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll(\"button[ng-click*='Changedeposite']\")].find(x=>x.offsetParent!==null && !x.disabled);"
                + " document.querySelectorAll('[data-dr-mod]').forEach(e=>e.removeAttribute('data-dr-mod')); if(!b) return false; b.setAttribute('data-dr-mod','1'); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { Reasons.add("No enabled Modify button (select a row first)"); return ""; }
        page.locator("[data-dr-mod='1']").first().click(new Locator.ClickOptions().setTimeout(5000));
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m=>m.offsetWidth>0 && /change\\s*deposite?\\s*against/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { return ""; }
        waitForAngular(500);
        Object r = page.evaluate("() => {" + NORM_JS
                + " const m=[...document.querySelectorAll('.modal')].find(x=>x.offsetWidth>0 && /change\\s*deposite?\\s*against/i.test(x.textContent||'')); if(!m) return '';"
                + " const ctrls=[...m.querySelectorAll('input,select,textarea,button,.ui-select-container')].filter(e=>!e.classList.contains('ui-select-search'))"
                + "   .map(e=>e.getAttribute('ng-model') ? '['+e.getAttribute('ng-model')+']' : norm(e.textContent)).filter(Boolean);"
                + " return norm(m.innerText) + ' || controls: ' + ctrls.join(', '); }");
        return r == null ? "" : r.toString();
    }

    /** Close the "Change Deposite Against" dialog with its <b>Close</b> button (never Save). Returns true when it is gone. */
    public boolean closeChangeAgainstDialog() {
        Object tagged = page.evaluate("() => {" + NORM_JS
                + " const m=[...document.querySelectorAll('.modal')].find(x=>x.offsetWidth>0 && /change\\s*deposite?\\s*against/i.test(x.textContent||'')); if(!m) return 'none';"
                + " const b=[...m.querySelectorAll('button')].find(x=>/^close$/i.test(norm(x.textContent))) || [...m.querySelectorAll('button.close')].find(x=>/^\\u00d7$|^x$/i.test(norm(x.textContent)));"
                + " if(!b) return 'nobutton'; b.setAttribute('data-dr-close','1'); return 'ok'; }");
        if ("none".equals(String.valueOf(tagged))) return true;
        if (!"ok".equals(String.valueOf(tagged))) { Reasons.add("The Change Deposite Against dialog has no Close button"); return false; }
        page.locator("[data-dr-close='1']").first().click(new Locator.ClickOptions().setTimeout(5000));
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.modal')].some(m=>m.offsetWidth>0 && /change\\s*deposite?\\s*against/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(6000));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- toasts -----------------------------------------------------------

    /** Start recording toast / JAlert messages into {@code window.__drToasts} (same observer {@link #saveAndOpenReport()} uses). */
    public void startToastCapture() {
        page.evaluate("() => { window.__drToasts=[]; if(window.__drObs) window.__drObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__drToasts.includes(t)) window.__drToasts.push(t); }); };"
                + " window.__drObs=new MutationObserver(grab); window.__drObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    /** Messages recorded since {@link #startToastCapture()} (de-duplicated). */
    public List<String> capturedToasts() {
        return strList(page.evaluate("() => window.__drToasts || []"));
    }

    // ---- Add Deposit/Receipt ------------------------------------------------

    /** Search a patient by MRN to populate the Visit dropdown: enter the MRN, click the search-by-MRN icon
     *  ({@code SearchPatientByMRNo()}), and wait for the Visit list ({@code drpVisit}) to load.
     *  Returns the resulting Visit option count. */
    public int searchPatient(String mrn) {
        // Reg. Type is left as it is (default IPD — deposit-record patients are inpatients). A non-admitted patient searched
        // under IPD answers "Patient Not Admitted !!!!" and loads no visit. The lookup also narrows the list grid to that patient.
        // Set MRN via ngModel and fire SearchPatientByMRNo() (real click so the ng-click handler runs).
        page.evaluate("(m) => { const A=window.angular; const e=[...document.querySelectorAll(\"input[ng-model='advance.MRNo']\")].find(x=>x.offsetParent!==null); if(e){ const c=A.element(e).controller('ngModel'); e.value=m; if(c){c.$setViewValue(m);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const b=[...document.querySelectorAll('button,a,i,span,img')].find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'')); if(b) b.id='__mrnSearch'; }", mrn);
        try { page.locator("#__mrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__mrnSearch'); if(e) e.removeAttribute('id'); }");
        // wait for the Visit list (drpVisit) or the advance.OPDNo select options to populate
        try {
            page.waitForFunction("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&Array.isArray(s.drpVisit)) n=Math.max(n,s.drpVisit.length); }catch(e){} }); if(n>0) return true; const e=document.querySelector(\"select[ng-model='advance.OPDNo']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(700);
        return visitCount();
    }

    /** Search the deposit-records grid and return distinct patient MRNs (candidates to deposit against). Stays entirely
     *  on the Deposit/Receipt screen. {@code months > 0} ticks the From Date checkbox ({@code advance1.isselected}) and
     *  searches the last {@code months} months — without the tick the dates are ignored; {@code months <= 0} searches
     *  with no date filter. */
    public java.util.List<String> candidateMrnsFromGrid(int months) {
        if (months > 0) {
            String from = java.time.LocalDate.now().minusMonths(months).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String to = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            setDateRange(from, to);
        } else {
            setDateFilter(false);
        }
        SearchResult r = search(45000);
        if (!r.httpOk()) Reasons.add("Deposit/Receipt search failed: " + r.summary());
        java.util.List<String> list = new java.util.ArrayList<>();
        for (Map<String, Object> row : gridRows(false)) {
            String m = String.valueOf(row.getOrDefault("MRNo", "")).trim();
            if (!m.isEmpty() && !list.contains(m)) list.add(m);
        }
        System.out.println("candidateMrnsFromGrid(" + months + "): " + list.size() + " distinct MRNs => " + list);
        return list;
    }

    /** How many visits the patient has (max of the drpVisit scope list and the Visit select's real options). */
    public int visitCount() {
        Object c = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&Array.isArray(s.drpVisit)) n=Math.max(n,s.drpVisit.length); }catch(e){} }); const e=document.querySelector(\"select[ng-model='advance.OPDNo']\"); if(e){ const o=[...e.options].filter(x=>x.value && (x.textContent||'').trim() && !/^-*\\s*select/i.test((x.textContent||'').trim())).length; n=Math.max(n,o); } return n; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Select an option of {@code selNg} whose text matches {@code preferRe}; if none, the first real option that
     *  does NOT match {@code avoidRe}. Returns the chosen text (or a marker). */
    private String realSelectPreferring(String selNg, String preferRe, String avoidRe, int timeoutMs) {
        String sel = "select[ng-model='" + selNg + "']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception e) { return "(no-opt)"; }
        Object idx = page.evaluate("(a) => { const e=document.querySelector(a.sel); const opts=[...e.options]; const real=o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim()); const pref=new RegExp(a.pref,'i'); const avoid=new RegExp(a.avoid,'i');"
                + " let i=opts.findIndex(o=>real(o) && pref.test((o.textContent||'').trim())); if(i<0) i=opts.findIndex(o=>real(o) && !avoid.test((o.textContent||'').trim())); if(i<0) i=opts.findIndex(real); return i; }",
                java.util.Map.of("sel", sel, "pref", preferRe, "avoid", avoidRe));
        int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("realSelectPreferring " + selNg + " failed: " + e.getMessage()); }
        waitForAngular(700);
        return chosen;
    }

    /** Choose the Add form's Deposit/Receipt Type* by label (e.g. "Patient"); its Payor* is filled from it. Returns the Payor shown. */
    public String selectAddType(String label) {
        lastType = selectByLabel("advance.AdvanceTypeid", label);
        waitForAngular(2000);
        Object p = page.evaluate("() => { const e=document.getElementById('txtSponsorName'); return e ? e.value : ''; }");
        return p == null ? "" : p.toString().trim();
    }

    /**
     * Deposit/Receipt Against* — a <b>ui-select multi-select</b> ({@code advance.againsttypes}), not a {@code <select>}.
     *
     * <p>Same technique as {@code NurseRoster.selectNurse()} (the only ui-select handling in this suite): real click on the
     * widget's search box, type, then a real click on the matching {@code .ui-select-choices-row}; the first offered
     * choice is taken when {@code preferText} is not offered. Success is read back from the widget's ngModel.</p>
     *
     * @return the selected item texts joined with ", ", or a marker "(no-field)" / "(no-choice)"
     */
    public String selectAgainst(String preferText) {
        if (page.locator(AGAINST_UISELECT).count() == 0) {
            Reasons.add("Deposit/Receipt Against: the ui-select advance.againsttypes is not on the Add form");
            return "(no-field)";
        }
        Locator search = page.locator(AGAINST_UISELECT + " input.ui-select-search").first();
        try {
            search.click(new Locator.ClickOptions().setTimeout(5000));
            waitForAngular(400);
            if (preferText != null && !preferText.isEmpty()) { search.pressSequentially(preferText); waitForAngular(700); }
            if (!clickUiSelectChoice(preferText)) {
                search.fill("");
                waitForAngular(700);
                clickUiSelectChoice(null);
            }
        } catch (Exception e) {
            System.out.println("selectAgainst: " + firstLine(e.getMessage()));
        }
        nudgePage();
        waitForAngular(400);
        List<String> sel = againstSelected();
        if (sel.isEmpty()) {
            Reasons.add("Deposit/Receipt Against: no choice could be selected in the ui-select");
            return "(no-choice)";
        }
        return String.join(", ", sel);
    }

    private boolean clickUiSelectChoice(String preferText) {
        for (int i = 0; i < 6; i++) {
            Object tagged = page.evaluate("(want) => {" + NORM_JS
                    + " const rows=[...document.querySelectorAll('.ui-select-choices-row')].filter(vis).filter(r=>norm(r.textContent) && !/^-*\\s*select/i.test(norm(r.textContent)));"
                    + " if(!rows.length) return '';"
                    + " const r=(want ? rows.find(x=>norm(x.textContent).toLowerCase()===want.toLowerCase()) : null) || (want ? null : rows[0]);"
                    + " if(!r) return 'nomatch';"
                    + " document.querySelectorAll('[data-dr-choice]').forEach(e=>e.removeAttribute('data-dr-choice'));"
                    + " (r.querySelector('.ui-select-choices-row-inner')||r).setAttribute('data-dr-choice','1'); return norm(r.textContent); }",
                    preferText == null ? "" : preferText);
            String t = tagged == null ? "" : tagged.toString();
            if ("nomatch".equals(t)) return false;
            if (!t.isEmpty()) {
                page.locator("[data-dr-choice='1']").first().click(new Locator.ClickOptions().setTimeout(4000));
                waitForAngular(500);
                return true;
            }
            page.waitForTimeout(400);
        }
        return false;
    }

    /** Items currently selected in Deposit/Receipt Against* (from the widget's ngModel). */
    public List<String> againstSelected() {
        // Read the bound scope model (advance.againsttypes); the widget's ngModel $viewValue was not reliable for this
        // multi-select during the inspection (it read as non-empty while the model was []).
        return strList(page.evaluate("(s) => { const c=document.querySelector(s); if(!c) return []; const v=angular.element(c).scope().$eval(c.getAttribute('ng-model')) || [];"
                + " return (Array.isArray(v)?v:[v]).map(x=>(x && (x.text||x.Text)) || '').filter(Boolean); }", AGAINST_UISELECT));
    }

    /** Every item the Deposit/Receipt Against* ui-select offers — read from its repeat source ({@code drpAgainstList}),
     *  not from the widget's filtered list, which keeps the last typed search. */
    public List<String> againstChoices() {
        return strList(page.evaluate("(s) => { const c=document.querySelector(s); if(!c) return []; const ch=c.querySelector('[repeat]');"
                + " const rep=ch ? ch.getAttribute('repeat') : ''; const m=/\\s+in\\s+([^|]+)/.exec(rep||''); if(!m) return [];"
                + " return (angular.element(c).scope().$eval(m[1].trim()) || []).map(x=>(x && (x.text||x.Text)) || '').filter(Boolean); }", AGAINST_UISELECT));
    }

    /** Real options in the Cash Counter* list (0 = the logged-in user has no cash counter here). */
    public int cashCounterOptionCount() {
        Object r = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='advance.CashCounterid']\"); if(!e) return -1;"
                + " return [...e.options].filter(o=>(o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())).length; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Fill ONLY the mandatory (starred) Add Deposit/Receipt fields: Deposit/Receipt Type*, Payor* (auto from Type),
     *  Deposit/Receipt Against* (ui-select), Cash Counter*, Amount*; Restrict consumption* is pre-ticked and locked. The Visit
     *  is selected first because the Type/Payor belong to it; the non-mandatory fields (Date, Remark) are left untouched.
     *  An empty Cash Counter list is reported through {@link Reasons} as BLOCKED — check {@link #missingMandatory()}. */
    public String fillMandatory() {
        lastVisit = realSelectFirst("advance.OPDNo", 12000);   // not starred; usually already picked by the MRN search
        waitForAngular(900);
        // Deposit/Receipt Type* — prefer a CASH type; never "Company" (that needs a payor company). Payor* follows from it.
        // The app locks the Type once chosen (unless Reg. Type is ALL), so an already-chosen Type is kept as it is.
        Object typeLocked = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='advance.AdvanceTypeid']\"); if(!e || !e.disabled) return false;"
                + " const o=e.options[e.selectedIndex]; return !!(o && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }");
        lastType = Boolean.TRUE.equals(typeLocked) ? selectedText("advance.AdvanceTypeid")
                : realSelectPreferring("advance.AdvanceTypeid", "cash|advance|deposit|patient", "company|corporate|credit|insurance", 8000);
        waitForAngular(1500);
        // Deposit/Receipt Against* — ui-select multi-select advance.againsttypes
        lastAgainst = selectAgainst("Receipt Deposit");
        waitForAngular(500);
        // Cash Counter*
        if (cashCounterOptionCount() <= 0) {
            lastCounter = "(no-opt)";
            Reasons.add("BLOCKED: Cash Counter* has no options for the logged-in user (drpCashCounterList is empty), so the deposit cannot be saved"
                    + " - log in with a user/location that has a cash counter");
        } else {
            lastCounter = realSelectFirst("advance.CashCounterid", 8000);
        }
        waitForAngular(400);
        // Amount*
        setNg("advance.Balance", "100");
        lastAmount = "100";
        waitForAngular(300);
        String payor = String.valueOf(page.evaluate("() => { const e=document.getElementById('txtSponsorName'); return e?e.value:''; }")).trim();
        Object restrict = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='advance.restrictConsumption']\"); return !!(e && e.checked); }");
        return "Type=" + lastType + " | Payor=" + (payor.isEmpty() ? "(empty)" : payor) + " | Against=" + lastAgainst
                + " | CashCounter=" + lastCounter + " | Amount=100 | RestrictConsumption=" + (Boolean.TRUE.equals(restrict) ? "ticked" : "NOT ticked");
    }

    /** The starred Add fields that are still empty: Deposit/Receipt Type, Payor, Deposit/Receipt Against, Cash Counter,
     *  Amount, Restrict consumption. Empty list = every mandatory field holds a value. */
    public List<String> missingMandatory() {
        return strList(page.evaluate("(s) => { const q=x=>document.querySelector(x); const out=[];"
                + " const real=e=>{ if(!e) return false; const o=e.options[e.selectedIndex]; return !!(o && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); };"
                + " if(!real(q(\"select[ng-model='advance.AdvanceTypeid']\"))) out.push('Deposit/Receipt Type');"
                + " const p=q('#txtSponsorName'); if(!p || !(p.value||'').trim()) out.push('Payor');"
                + " const c=q(s); const v=c ? angular.element(c).scope().$eval(c.getAttribute('ng-model')) : null; if(!(Array.isArray(v) ? v.length : v)) out.push('Deposit/Receipt Against');"
                + " if(!real(q(\"select[ng-model='advance.CashCounterid']\"))) out.push('Cash Counter');"
                + " const a=q(\"input[ng-model='advance.Balance']\"); if(!a || !(parseFloat(a.value)>0)) out.push('Amount');"
                + " const r=q(\"input[ng-model='advance.restrictConsumption']\"); if(!r || !r.checked) out.push('Restrict consumption for selected encounter');"
                + " return out; }", AGAINST_UISELECT));
    }

    /** Snapshot of the Add Deposit/Receipt form (read-only): visit, type, payor, against, cash counter, restrict, amount,
     *  date, remark, cash tick, and the labels marked with a red "*". */
    @SuppressWarnings("unchecked")
    public Map<String, Object> addFormState() {
        Object r = page.evaluate("(s) => {" + NORM_JS + " const q=x=>document.querySelector(x);"
                + " const selTxt=e=>e ? ((e.options[e.selectedIndex]||{}).textContent||'').trim() : '';"
                + " const v=q(\"select[ng-model='advance.OPDNo']\"), t=q(\"select[ng-model='advance.AdvanceTypeid']\"), cc=q(\"select[ng-model='advance.CashCounterid']\");"
                + " const p=q('#txtSponsorName'), rc=q(\"input[ng-model='advance.restrictConsumption']\"), am=q(\"input[ng-model='advance.Balance']\");"
                + " const dt=q(\"input[ng-model='advance.AdDate']\"), rm=q(\"textarea[ng-model='advance.Remark']\"), cash=q(\"input[ng-model='TrueValChk']\");"
                + " const c=q(s); const av=(c ? angular.element(c).scope().$eval(c.getAttribute('ng-model')) : null) || [];"
                + " const head=[...document.querySelectorAll('.panel-heading,.box-header')].find(h=>/add\\s*deposit/i.test(norm(h.textContent)));"
                + " const panel=head ? (head.closest('.panel,.box') || head.parentElement) : null;"
                + " const req=panel ? [...panel.querySelectorAll('label')].filter(vis).filter(l=>/\\*/.test(l.textContent||'')).map(l=>norm(l.textContent).replace(/\\s*\\*\\s*$/,'').trim() || '(checkbox)') : [];"
                + " return { visit: selTxt(v), visitOptions: v ? [...v.options].filter(o=>(o.textContent||'').trim()).length : -1,"
                + "   type: selTxt(t), typeDisabled: !!(t && t.disabled), payor: p ? p.value : '', payorDisabled: !!(p && p.disabled),"
                + "   against: (Array.isArray(av)?av:[av]).map(x=>(x && x.text) || '').filter(Boolean), cashCounter: selTxt(cc),"
                + "   cashCounterOptions: cc ? [...cc.options].filter(o=>(o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())).length : -1,"
                + "   restrictChecked: !!(rc && rc.checked), restrictDisabled: !!(rc && rc.disabled), amount: am ? am.value : '',"
                + "   date: dt ? dt.value : '', remark: rm ? rm.value : '', cash: !!(cash && cash.checked), required: req }; }", AGAINST_UISELECT);
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Click <b>Clear</b> ({@code CancelForm()} — resets the form and the list in the page only; no server call). */
    public void clearForm() {
        page.locator("button[ng-click='CancelForm()']").first().click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(1200);
    }

    /** Tick the <b>Cash</b> checkbox ({@code TrueValChk}). */
    public String selectCash() {
        Object tagged = page.evaluate("() => { const cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='TrueValChk']\")].find(x=>x.offsetParent!==null); if(!cb) return false; cb.id='__cashChk'; return true; }");
        String r = "(none)";
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__cashChk").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(4000)); r = "Cash ticked"; }
            catch (Exception e) { try { page.locator("#__cashChk").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); r = "Cash clicked"; } catch (Exception e2) { System.out.println("selectCash: failed - " + e2.getMessage()); } }
            page.evaluate("() => { const e=document.getElementById('__cashChk'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(300);
        return r;
    }

    /** Click <b>Save</b> ({@code fnSaveadvance}); capture the receipt report that opens in a new tab. Returns the
     *  new Page (report tab), or null if no tab opened; also collects any toast into {@link #lastToast}.
     *  <b>Creates a real deposit/receipt record.</b> */
    public String lastToast = "";
    public Page saveAndOpenReport() {
        page.evaluate("() => { window.__drToasts=[]; if(window.__drObs) window.__drObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__drToasts.includes(t)) window.__drToasts.push(t); }); };"
                + " window.__drObs=new MutationObserver(grab); window.__drObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        int tabsBefore = page.context().pages().size();
        // real click the Save button (fnSaveadvance)
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].find(x=>/fnSaveadvance/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__drSave'; return true; }");
        Page report = null;
        if (Boolean.TRUE.equals(tagged)) {
            try {
                report = page.context().waitForPage(() -> {
                    try { page.locator("#__drSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
                });
            } catch (Exception e) {
                System.out.println("saveAndOpenReport: no new tab within timeout - " + e.getClass().getSimpleName());
            }
        } else {
            System.out.println("saveAndOpenReport: Save button not found");
        }
        // capture toast (fallback)
        try {
            page.waitForFunction("() => (window.__drToasts||[]).length>0", null, new Page.WaitForFunctionOptions().setTimeout(4000));
        } catch (Exception ignore) { }
        Object t = page.evaluate("() => { const a=window.__drToasts||[]; return a.find(x=>/saved|success|receipt|deposit|please|enter|select|amount|required/i.test(x)) || a[0] || ''; }");
        lastToast = t == null ? "" : t.toString().trim();
        if (report == null && page.context().pages().size() > tabsBefore) {
            report = page.context().pages().get(page.context().pages().size() - 1);
        }
        if (report != null) { try { report.waitForLoadState(); report.waitForTimeout(2500); } catch (Exception ignore) { } }
        return report;
    }

    // ---- utility ------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) if (o != null) out.add(o.toString());
        return out;
    }

    /** A readable one-line summary of a Playwright / JS error message. Playwright's timeout errors start with "Error {"
     *  (a serialized object), so the "Timeout NNNms exceeded" phrase or the first meaningful line is used instead. */
    private static String firstLine(String s) {
        if (s == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Timeout \\d+ms exceeded[^\\n\"]*").matcher(s);
        if (m.find()) return m.group().trim();
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.matches("^Error\\s*\\{?$") && !t.startsWith("{") && !t.startsWith("}")) return t;
        }
        return s.split("\n")[0].trim();
    }
}
