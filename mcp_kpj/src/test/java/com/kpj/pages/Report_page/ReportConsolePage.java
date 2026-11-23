package com.kpj.pages.Report_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Page object for <b>Report &gt; Report Console</b> ({@code #/reportconsole}) and the Crystal Reports viewer tab that
 * Show Report opens. READ-ONLY: it selects reports, adds filters, shows / exports / prints reports; it never saves.
 *
 * <p>Everything here was mapped live on hisdemo (23-11-2026) and from the screen's own controller
 * ({@code App/Report/controller/ReportConsoleController.js}):</p>
 * <ul>
 *   <li>Category buttons {@code #btnli0FinancialReport}, {@code #btnli0InventoryReport} load the reports
 *       ({@code POST api/ReportConsole/Fetch}) into the select2 {@code RptColsole.Report}.</li>
 *   <li>Choosing a report calls {@code funGetParameterByReportID} → {@code POST api/ReportConsole/GetReportParameter};
 *       the filters land in select2 {@code RptColsole.Parameter} and in {@code $scope.drprptparametermaster}
 *       (with {@code IsCompulsory}).</li>
 *   <li>{@code Add} ({@code AddFilter()}) puts a row in table {@code #example2}; the row's <i>Add Value</i> button opens a
 *       popup (ModalDate, ModalDateRange, ModalSingleDate, ModalLocation, ModalStoreList, ...) whose OK button is
 *       {@code fnAdd('...')}. Dates are dd/MM/yyyy strings.</li>
 *   <li>{@code Show Report} ({@code ShowReport($event)}) → {@code POST api/ReportConsole/ShowReport} (204) → a new tab:
 *       Financial {@code SQLReport/ReportConsole/FinancialReport/frmFinancialReport.aspx}, Inventory
 *       {@code SQLReport/ReportConsole/InventoryReport/frmInventoryReport.aspx}. When there is no data the tab shows
 *       "No Data Available" (Inventory: the tab closes itself and the console raises a JAlert "No Data Available").</li>
 *   <li>Viewer: Crystal toolbar {@code #CrystalReportViewer1_toptoolbar_export / _nextPg}, page field
 *       {@code #text_CrystalReportViewer1_toptoolbar_selectPg} ("1 of 5"), Print button {@code #btnPrint} →
 *       {@code Print()} → {@code print()} on the report iframe; the report body is in the iframe whose id ends in
 *       {@code _iframe}.</li>
 * </ul>
 */
public class ReportConsolePage extends BasePage {

    public static final String FINANCIAL_BTN = "btnli0FinancialReport";
    public static final String INVENTORY_BTN = "btnli0InventoryReport";

    /** Write-type API calls are aborted so a report test can never change data. */
    private static final String WRITE_API_REGEX =
            "(?i).*/api/.*(save|insert|update|delete|remove|approve|reject|submit|iud).*";

    private final List<String> abortedWrites = new ArrayList<>();
    private final List<String> consoleErrors = new ArrayList<>();
    private final List<String> apiCalls = new ArrayList<>();
    private boolean guardsInstalled = false;

    public ReportConsolePage(Page page) { super(page); }

    // ------------------------------------------------------------------ guards / listeners

    /** Abort write calls, record console errors and every Report-Console API response (main page + report tabs). */
    public void installGuards() {
        if (guardsInstalled) return;
        guardsInstalled = true;
        BrowserContext ctx = page.context();
        ctx.route(url -> url.matches(WRITE_API_REGEX) && !url.matches("(?i).*/api/ReportConsole/ShowReport.*"), route -> {
            synchronized (abortedWrites) { abortedWrites.add(route.request().method() + " " + route.request().url()); }
            System.out.println("READ-ONLY GUARD: aborted " + route.request().method() + " " + route.request().url());
            route.abort();
        });
        Consumer<ConsoleMessage> onConsole = m -> {
            if ("error".equalsIgnoreCase(m.type())) {
                synchronized (consoleErrors) { consoleErrors.add(m.text().replaceAll("\\s+", " ").trim()); }
            }
        };
        Consumer<Response> onResponse = r -> {
            String u = r.url();
            if (u.matches("(?i).*(/api/ReportConsole/|/SQLReport/ReportConsole/).*")) {
                synchronized (apiCalls) { apiCalls.add(r.request().method() + " " + u.replaceAll("^https?://[^/]+", "") + " -> " + r.status()); }
            }
        };
        page.onConsoleMessage(onConsole);
        page.onResponse(onResponse);
        ctx.onPage(p -> { p.onConsoleMessage(onConsole); p.onResponse(onResponse); });
    }

    public List<String> abortedWrites() { synchronized (abortedWrites) { return new ArrayList<>(abortedWrites); } }

    /** Console errors since the last call (and clears them). */
    public List<String> takeConsoleErrors() { synchronized (consoleErrors) { List<String> l = new ArrayList<>(consoleErrors); consoleErrors.clear(); return l; } }

    /** Report-Console API responses since the last call (and clears them). */
    public List<String> takeApiCalls() { synchronized (apiCalls) { List<String> l = new ArrayList<>(apiCalls); apiCalls.clear(); return l; } }

    /** Capture JAlert warnings/toasts (they fade in ~1s). Re-arm after each navigation. */
    public void interceptAlerts() {
        page.evaluate("() => { window.__rcAlerts = []; if (window.__rcAlertWrapped) return; window.__rcAlertWrapped = true;"
                + " const o = window.JAlert; window.JAlert = function (m, t) { try { window.__rcAlerts.push(String(t || '') + ': ' + String(m)); } catch (e) {}"
                + " if (o) try { return o.apply(this, arguments); } catch (e) {} }; }");
    }

    @SuppressWarnings("unchecked")
    public List<String> takeAlerts() {
        Object r = page.evaluate("() => { const a = window.__rcAlerts || []; window.__rcAlerts = []; return a; }");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** nHIS "Reload page - you will be logged out" popup: always answer No (the framework watcher does too). */
    public void dismissReloadPopup() {
        try {
            page.evaluate("() => { const bd = document.querySelector('.nhis-rc-backdrop'); if (!bd) return;"
                    + " const no = [...bd.querySelectorAll('button')].find(b => (b.textContent || '').trim() === 'No' || b.getAttribute('aria-label') === 'No');"
                    + " if (no) no.click(); }");
        } catch (Exception ignore) { }
    }

    // ------------------------------------------------------------------ navigation

    /** Report (sidebar) → Report Console. Direct hash navigation is redirected to the landing page, so use the menu. */
    public String openReportConsole() {
        dismissReloadPopup();
        boolean expanded = expandMenuEntry(null, "^Report$");
        Object clicked = page.evaluate("() => { const a = [...document.querySelectorAll('a')].find(x => x.getAttribute('href') === '#/reportconsole');"
                + " if (!a) return false; a.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) return "Report Console menu link (#/reportconsole) not found (Report menu expanded=" + expanded + ")";
        try {
            page.waitForFunction("() => location.hash.toLowerCase().includes('reportconsole') && !!document.getElementById('" + FINANCIAL_BTN + "')",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) {
            return "Report Console did not load: url=" + page.url();
        }
        dismissReloadPopup();
        interceptAlerts();
        return "";
    }

    // ------------------------------------------------------------------ category / report / filters

    /** Click a category button and return the report names its Reports dropdown lists. */
    @SuppressWarnings("unchecked")
    public List<String> openCategory(String btnId) {
        dismissReloadPopup();
        Object r = page.evaluate("async (id) => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim(); const sleep = ms => new Promise(r => setTimeout(r, ms));"
                + " const b = document.getElementById(id); if (!b) return null; b.click();"
                + " const rs = () => [...document.querySelectorAll('select')].find(x => x.getAttribute('ng-model') === 'RptColsole.Report');"
                + " for (let i = 0; i < 60; i++) { await sleep(250); const s = rs(); if (s && s.options.length > 1 && b.classList.contains('active')) break; }"
                + " const s = rs(); return s ? [...s.options].slice(1).map(o => norm(o.text)) : []; }", btnId);
        return r instanceof List ? (List<String>) r : null;
    }

    /** Result of choosing a report. */
    public static class Selection {
        public boolean ok; public String message = ""; public int paramApiStatus = -1;
        public final List<String> filters = new ArrayList<>();         // Filter dropdown, as shown
        public final List<String> mandatory = new ArrayList<>();       // IsCompulsory=true per GetReportParameter
        public final List<String> autoRows = new ArrayList<>();        // rows the screen pre-adds itself
    }

    /** Re-open the category (resets the screen's filters) and choose the report by its visible name. */
    @SuppressWarnings("unchecked")
    public Selection selectReport(String btnId, String reportName) {
        Selection sel = new Selection();
        openCategory(btnId);
        Response resp = null;
        Object r;
        try {
            final Object[] holder = new Object[1];
            resp = page.waitForResponse(x -> x.url().contains("/api/ReportConsole/GetReportParameter"),
                    new Page.WaitForResponseOptions().setTimeout(30000),
                    () -> holder[0] = page.evaluate(SELECT_REPORT_JS, reportName));
            r = holder[0];
        } catch (Exception e) {
            r = page.evaluate(SELECT_REPORT_JS, reportName);   // no parameter call seen: still read what the screen shows
        }
        if (resp != null) sel.paramApiStatus = resp.status();
        page.waitForTimeout(800);   // the screen's then-callback builds drprptparametermaster after the response
        Object details = page.evaluate(READ_PARAMS_JS);
        if (r instanceof Map<?, ?> m) {
            sel.ok = Boolean.TRUE.equals(m.get("ok"));
            sel.message = m.get("msg") == null ? "" : String.valueOf(m.get("msg"));
        }
        if (details instanceof Map<?, ?> d) {
            if (d.get("filters") instanceof List<?> l) l.forEach(x -> sel.filters.add(String.valueOf(x)));
            if (d.get("mandatory") instanceof List<?> l) l.forEach(x -> sel.mandatory.add(String.valueOf(x)));
            if (d.get("rows") instanceof List<?> l) l.forEach(x -> sel.autoRows.add(String.valueOf(x)));
            if (sel.ok && !String.valueOf(d.get("selected")).equals(reportName)) {
                sel.ok = false;
                sel.message = "screen shows report '" + d.get("selected") + "' instead of '" + reportName + "'";
            }
        }
        return sel;
    }

    private static final String SELECT_REPORT_JS = """
        (name) => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim();
          const s = [...document.querySelectorAll('select')].find(x => x.getAttribute('ng-model') === 'RptColsole.Report');
          if (!s) return { ok: false, msg: 'Reports dropdown (RptColsole.Report) not found' };
          const o = [...s.options].find(x => norm(x.text) === name);
          if (!o) return { ok: false, msg: 'report "' + name + '" is not in the Reports dropdown' };
          jQuery(s).select2('val', o.value).trigger('change');
          return { ok: true }; }
        """;

    private static final String READ_PARAMS_JS = """
        () => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim();
          const p = [...document.querySelectorAll('select')].find(x => x.getAttribute('ng-model') === 'RptColsole.Parameter');
          const sc = p ? angular.element(p).scope() : null;
          const master = (sc && sc.drprptparametermaster) || [];
          return { selected: sc && sc.RptColsole ? sc.RptColsole.reportName : '',
                   filters: p ? [...p.options].slice(1).map(o => norm(o.text)) : [],
                   mandatory: master.filter(x => x.IsCompulsory).map(x => norm(x.ParameterName)),
                   rows: ((sc && sc.drpfilterlist) || []).map(f => norm(f.ParameterName)) }; }
        """;

    /** Add a filter row (Filter dropdown + Add). "" on success, else the reason. */
    public String addFilter(String filterName) {
        Object r = page.evaluate(ADD_FILTER_JS, filterName);
        return r instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("ok")) ? "" : r instanceof Map<?, ?> m2 ? String.valueOf(m2.get("msg")) : "Add failed";
    }

    private static final String ADD_FILTER_JS = """
        async (pname) => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim(); const sleep = ms => new Promise(r => setTimeout(r, ms));
          const p = [...document.querySelectorAll('select')].find(x => x.getAttribute('ng-model') === 'RptColsole.Parameter');
          if (!p) return { ok: false, msg: 'Filter dropdown not found' };
          const sc = angular.element(p).scope();
          if ((sc.drpfilterlist || []).some(f => norm(f.ParameterName) === pname)) return { ok: true, already: true };
          const o = [...p.options].find(x => norm(x.text) === pname);
          if (!o) return { ok: false, msg: 'filter "' + pname + '" is not offered in the Filter dropdown' };
          jQuery(p).select2('val', o.value).trigger('change'); await sleep(400);
          const add = [...document.querySelectorAll('button')].find(x => x.getAttribute('ng-click') === 'AddFilter()');
          if (!add) return { ok: false, msg: 'Add button not found' };
          add.click();
          for (let i = 0; i < 25; i++) { await sleep(200); if ((sc.drpfilterlist || []).some(f => norm(f.ParameterName) === pname)) return { ok: true }; }
          return { ok: false, msg: 'Add did not put a "' + pname + '" row in the filter grid' }; }
        """;

    /** Result of filling a filter's value through its Add Value popup. */
    public static class Fill {
        public boolean ok; public String kind = ""; public String modal = ""; public String picked = ""; public String value = ""; public String message = "";
        public boolean unsupported() { return "unsupported".equals(kind); }
        @Override public String toString() {
            return ok ? "popup " + modal + " → picked '" + picked + "', filter value now '" + value + "'"
                      : (message.isEmpty() ? kind : message) + (modal.isEmpty() ? "" : " (popup " + modal + ")");
        }
    }

    /**
     * Open the row's Add Value popup and give it a value: From/To dates for a date-range popup, one date for a single
     * date popup, else the FIRST entry of a checkbox / option list. Popups that need typed or searched input (patient
     * search, item search, free-text numbers) are reported as {@code unsupported} — nothing is invented for them.
     */
    public Fill fillFilter(String filterName, String fromDate, String toDate) {
        dismissReloadPopup();
        Object r = page.evaluate(FILL_JS, Arrays.asList(filterName, fromDate, toDate));
        Fill f = new Fill();
        if (r instanceof Map<?, ?> m) {
            f.ok = Boolean.TRUE.equals(m.get("ok"));
            f.kind = str(m.get("kind")); f.modal = str(m.get("modal")); f.picked = str(m.get("picked"));
            f.value = str(m.get("value")); f.message = str(m.get("msg"));
        } else f.message = "popup helper returned nothing";
        return f;
    }

    private static final String FILL_JS = """
        async ([pname, from, to]) => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim(); const sleep = ms => new Promise(r => setTimeout(r, ms));
          const vis = e => !!(e && (e.offsetWidth || e.offsetHeight || e.getClientRects().length));
          const row = [...document.querySelectorAll('#example2 tbody tr')].find(r => r.children[1] && norm(r.children[1].innerText) === pname);
          if (!row) return { ok: false, kind: 'norow', msg: 'filter row "' + pname + '" is not in the filter grid' };
          const btn = [...row.querySelectorAll('button')].find(b => /Add Value/i.test(b.innerText));
          if (!btn) return { ok: false, kind: 'novaluebtn', msg: 'row has no Add Value button' };
          btn.click();
          let m = null;
          for (let i = 0; i < 24 && !m; i++) { await sleep(250); m = [...document.querySelectorAll('.modal')].find(x => getComputedStyle(x).display !== 'none' && x.offsetHeight > 0); }
          if (!m) return { ok: false, kind: 'nomodal', msg: 'Add Value opened no popup' };
          await sleep(600);   // list popups load their items asynchronously
          const title = m.id;
          const closeM = () => { const c = [...m.querySelectorAll('button')].filter(vis).find(b => /^(close|×)$/i.test(norm(b.innerText))); if (c) c.click(); };
          const inputs = [...m.querySelectorAll('input[ng-model]')].filter(vis);
          const fromI = inputs.find(i => /FromDate$/i.test(i.getAttribute('ng-model')));
          const toI = inputs.find(i => /ToDate$/i.test(i.getAttribute('ng-model')));
          const dateI = inputs.find(i => i.type === 'text' && /Date$/i.test(i.getAttribute('ng-model')) && i !== fromI && i !== toI);
          const boxes = [...m.querySelectorAll('input[type=checkbox][ng-model]')].filter(vis).filter(c => !/SelectAll/i.test(c.getAttribute('ng-model')));
          const radios = [...m.querySelectorAll('input[type=radio]')].filter(vis);
          const setNg = (el, v) => { const sc = angular.element(el).scope(); sc.$apply(el.getAttribute('ng-model') + ' = ' + JSON.stringify(v)); };
          let picked = '';
          if (fromI && toI) { setNg(fromI, from); setNg(toI, to); picked = from + ' - ' + to; }
          else if (dateI) { setNg(dateI, to); picked = to; }
          else if (boxes.length) {
            // prefer the popup's own Select All (broadest data); else the first entry
            const all = [...m.querySelectorAll('input[type=checkbox][ng-model]')].filter(vis).find(c => /SelectAll/i.test(c.getAttribute('ng-model')));
            if (all) { if (!all.checked) all.click(); picked = 'Select All (' + boxes.length + ' entries)'; }
            else { const c = boxes[0]; if (!c.checked) c.click(); picked = norm((c.closest('tr,li,label') || c.parentElement).innerText) || '(first item)'; } }
          else if (radios.length) { radios[0].click(); picked = norm((radios[0].closest('label,td') || radios[0].parentElement).innerText) || '(first option)'; }
          else { closeM(); return { ok: false, kind: 'unsupported', modal: title, msg: 'popup needs typed or searched input (no date, list or option to pick)' }; }
          await sleep(300);
          const okb = [...m.querySelectorAll('button')].filter(vis).find(b => /^\\s*fnAdd\\(/.test(b.getAttribute('ng-click') || ''))
                   || [...m.querySelectorAll('button')].filter(vis).find(b => /^OK$/i.test(norm(b.innerText)));
          if (!okb) { closeM(); return { ok: false, kind: 'nook', modal: title, msg: 'popup has no OK button' }; }
          okb.click(); await sleep(1200);
          const sc = angular.element(document.querySelector('#example2')).scope();
          const f = (sc.drpfilterlist || []).find(x => norm(x.ParameterName) === pname);
          if (getComputedStyle(m).display !== 'none' && m.offsetHeight > 0) closeM();
          return { ok: !!(f && f.FilterValue), kind: 'filled', modal: title, picked, value: f ? String(f.FilterValue) : '' }; }
        """;

    /** The value a filter row shows ("" when empty or missing). */
    public String filterValue(String filterName) {
        Object r = page.evaluate("(n) => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim();"
                + " const sc = angular.element(document.querySelector('#example2')).scope();"
                + " const f = ((sc && sc.drpfilterlist) || []).find(x => norm(x.ParameterName) === n); return f && f.FilterValue ? String(f.FilterValue) : ''; }", filterName);
        return r == null ? "" : r.toString();
    }

    /** Delete a filter row with its DELETE button. */
    public boolean deleteFilter(String filterName) {
        Object r = page.evaluate("async (n) => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim(); const sleep = ms => new Promise(r => setTimeout(r, ms));"
                + " const row = [...document.querySelectorAll('#example2 tbody tr')].find(r => r.children[1] && norm(r.children[1].innerText) === n);"
                + " if (!row) return false; const b = [...row.querySelectorAll('button')].find(x => /DeleteParameter/.test(x.getAttribute('ng-click') || ''));"
                + " if (!b) return false; b.click(); await sleep(600);"
                + " return ![...document.querySelectorAll('#example2 tbody tr')].some(r => r.children[1] && norm(r.children[1].innerText) === n); }", filterName);
        return Boolean.TRUE.equals(r);
    }

    /** Select a filter in the Filter dropdown WITHOUT adding it (for the "Add with nothing chosen" check, pass ""). */
    public void chooseFilterOnly(String filterName) {
        page.evaluate("(n) => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim();"
                + " const p = [...document.querySelectorAll('select')].find(x => x.getAttribute('ng-model') === 'RptColsole.Parameter'); if (!p) return;"
                + " const o = n ? [...p.options].find(x => norm(x.text) === n) : p.options[0]; if (o) jQuery(p).select2('val', o.value).trigger('change'); }", filterName);
        page.waitForTimeout(300);
    }

    public void clickAdd() {
        page.evaluate("() => { const b = [...document.querySelectorAll('button')].find(x => x.getAttribute('ng-click') === 'AddFilter()'); if (b) b.click(); }");
        page.waitForTimeout(800);
    }

    /** Open a filter's popup, put From AFTER To, press OK. Returns {value, alerts, popupStillOpen}. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fillInvertedDateRange(String filterName, String laterDate, String earlierDate) {
        takeAlerts();
        Fill f = fillFilter(filterName, laterDate, earlierDate);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fill", f.toString());
        out.put("value", filterValue(filterName));
        out.put("alerts", takeAlerts());
        // make sure no popup is left open
        page.evaluate("() => { const m = [...document.querySelectorAll('.modal')].find(x => getComputedStyle(x).display !== 'none' && x.offsetHeight > 0);"
                + " if (m) { const c = [...m.querySelectorAll('button')].find(b => /^(close|×)$/i.test((b.innerText || '').trim())); if (c) c.click(); } }");
        return out;
    }

    // ------------------------------------------------------------------ show report

    /** What Show Report produced. */
    public static class Shown {
        public Page tab;                        // the viewer tab, when it stayed open
        public String tabUrl = "";
        public boolean noData;                  // "No Data Available"
        public String serverError = "";         // ASP.NET / Crystal error page text
        public final List<String> alerts = new ArrayList<>();
        public int showApiStatus = -1;
        public String note = "";
        public boolean produced() { return tab != null && !tab.isClosed() && !noData && serverError.isEmpty(); }
    }

    public Shown showReport(int waitForTabMs) {
        dismissReloadPopup();
        interceptAlerts();
        takeAlerts();
        Shown s = new Shown();
        final Response[] api = new Response[1];
        Consumer<Response> watch = r -> { if (r.url().contains("/api/ReportConsole/ShowReport")) api[0] = r; };
        page.onResponse(watch);
        Page tab = null;
        try {
            tab = page.context().waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(waitForTabMs),
                    () -> page.evaluate("() => document.querySelector('button[ng-click=\"ShowReport($event)\"]').click()"));
        } catch (Exception e) {
            s.note = "no report tab opened within " + (waitForTabMs / 1000) + " s";
        }
        page.waitForTimeout(1500);
        page.offResponse(watch);
        if (api[0] != null) s.showApiStatus = api[0].status();
        if (tab != null) {
            s.tabUrl = tab.url();
            try { tab.waitForLoadState(); } catch (Exception ignore) { }
            // the Crystal viewer builds its toolbar after load; the Inventory "no data" check closes the tab instead
            for (int i = 0; i < 60 && !tab.isClosed(); i++) {
                Object st = safeEval(tab, "() => { const t = (document.body && document.body.innerText || '').trim();"
                        + " if (t === 'No Data Available' || /^No Data Available!?$/i.test(t)) return 'nodata';"
                        + " if (document.getElementById('text_CrystalReportViewer1_toptoolbar_selectPg') || document.getElementById('btnPrint')) return 'viewer';"
                        + " if (/server error|exception|logon failed|could not be found/i.test(t)) return 'error'; return ''; }");
                if (st != null && !String.valueOf(st).isEmpty()) break;
                page.waitForTimeout(500);
            }
            if (!tab.isClosed()) {
                s.tabUrl = tab.url();
                String body = String.valueOf(safeEval(tab, "() => (document.body && document.body.innerText || '').trim().slice(0, 200)"));
                if (body.matches("(?is)^No Data Available!?$")) s.noData = true;
                else s.serverError = com.kpj.core.DevHisBase.reportPageError(tab);
                s.tab = tab;
            }
        }
        page.waitForTimeout(800);
        s.alerts.addAll(takeAlerts());
        if (s.alerts.stream().anyMatch(a -> a.toLowerCase().contains("no data available"))) s.noData = true;
        return s;
    }

    // ------------------------------------------------------------------ viewer checks

    /** Report body (inside the Crystal viewer's report iframe), normalised. */
    public static String reportText(Page tab) {
        Object r = safeEval(tab, "() => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim();"
                + " const f = [...document.querySelectorAll('iframe')].find(x => /_iframe$/.test(x.id));"
                + " try { return f ? norm(f.contentDocument.body.innerText) : ''; } catch (e) { return ''; } }");
        return r == null ? "" : r.toString();
    }

    /** "1 of 5" → {1, 5}; {-1,-1} when the viewer has no page field. Crystal shows "1 of 1+" while still counting. */
    public static int[] pageInfo(Page tab) {
        Object r = safeEval(tab, "() => { const e = document.getElementById('text_CrystalReportViewer1_toptoolbar_selectPg'); return e ? e.value : ''; }");
        String v = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*of\\s*(\\d+)").matcher(v);
        return m.find() ? new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : new int[]{-1, -1};
    }

    public static String pageField(Page tab) {
        Object r = safeEval(tab, "() => { const e = document.getElementById('text_CrystalReportViewer1_toptoolbar_selectPg'); return e ? e.value : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click Next Page and wait for the page field to change. Returns the new field text ("" if it never changed). */
    public static String nextPage(Page tab) {
        String before = pageField(tab);
        safeEval(tab, "() => { const b = document.getElementById('CrystalReportViewer1_toptoolbar_nextPg'); if (b) b.click(); }");
        for (int i = 0; i < 40; i++) {
            tab.waitForTimeout(500);
            String now = pageField(tab);
            if (!now.equals(before) && now.matches("(?s).*\\d+\\s*of.*")) return now;
        }
        return "";
    }

    /** Export formats the viewer offers (from its own widget definition). */
    public static List<String> exportFormats(Page tab) {
        Object r = safeEval(tab, "() => { const s = [...document.scripts].map(x => x.text || '').find(t => t.includes('availableFormats')); if (!s) return [];"
                + " return [...s.matchAll(/'name':'([^']+)','value':'([^']+)'/g)].map(m => m[1] + '=' + m[2]); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List<?> l) l.forEach(x -> out.add(String.valueOf(x)));
        return out;
    }

    /**
     * Export through the viewer's own Export dialog: toolbar Export → pick {@code formatName} in the File Format
     * combo → the dialog's Export button. Returns the saved file, or throws with what was missing.
     */
    public static Path export(Page tab, String formatName, Path saveDir, String fileStem) throws Exception {
        safeEval(tab, "() => { const b = document.getElementById('CrystalReportViewer1_toptoolbar_export'); if (b) b.click(); }");
        tab.waitForTimeout(1500);
        Object opened = safeEval(tab, "() => { const a = [...document.querySelectorAll('[id^=iconMenu_arrow_][id$=_dialog_combo]')].find(e => e.offsetWidth || e.offsetHeight);"
                + " if (!a) return false; a.click(); return true; }");
        if (!Boolean.TRUE.equals(opened)) throw new IllegalStateException("Export dialog / File Format combo did not open");
        tab.waitForTimeout(800);
        Object picked = safeEval(tab, "(f) => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim();"
                + " const it = [...document.querySelectorAll('span,td,div')].filter(e => (e.offsetWidth || e.offsetHeight) && e.children.length === 0 && norm(e.innerText) === f);"
                + " if (!it.length) return false; const t = it[it.length - 1]; (t.closest('tr,a,[id*=menu]') || t).click(); return true; }", formatName);
        if (!Boolean.TRUE.equals(picked)) throw new IllegalStateException("format '" + formatName + "' not offered in the Export dialog");
        tab.waitForTimeout(600);
        Download d = tab.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(90000), () ->
                safeEval(tab, "() => { const norm = s => (s || '').replace(/\\s+/g, ' ').trim();"
                        + " const c = [...document.querySelectorAll('[id*=dialog] *')].filter(e => (e.offsetWidth || e.offsetHeight) && norm(e.innerText) === 'Export' && e.children.length <= 2);"
                        + " const b = c.length ? c[c.length - 1] : null; if (b) (b.closest('a,button,td,div') || b).click(); return !!b; }"));
        Files.createDirectories(saveDir);
        String name = d.suggestedFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
        Path out = saveDir.resolve(fileStem.replaceAll("[^A-Za-z0-9._-]", "_") + ext);
        d.saveAs(out);
        // close the export dialog if it stayed up
        safeEval(tab, "() => { const x = [...document.querySelectorAll('[id*=dialog] [title=Close], [id*=dialog] .dialogTitleBarClose')].find(e => e.offsetWidth || e.offsetHeight); if (x) x.click(); }");
        return out;
    }

    /** Click the viewer's Print button with {@code print()} stubbed on the report frame, so no OS dialog blocks the run. */
    public static String print(Page tab) {
        Object r = safeEval(tab, "async () => { const sleep = ms => new Promise(r => setTimeout(r, ms));"
                + " const b = document.getElementById('btnPrint'); if (!b) return 'no Print button (#btnPrint)';"
                + " window.__printed = 0; const stub = w => { try { w.print = () => { window.__printed++; }; } catch (e) {} };"
                + " stub(window); [...document.querySelectorAll('iframe')].forEach(f => { try { stub(f.contentWindow); } catch (e) {} });"
                + " b.click(); await sleep(800); return window.__printed > 0 ? 'OK' : 'Print clicked but print() was not called on the report'; }");
        return r == null ? "print check returned nothing" : r.toString();
    }

    public static Object safeEval(Page p, String js) { return safeEval(p, js, null); }

    public static Object safeEval(Page p, String js, Object arg) {
        try { return p.isClosed() ? null : (arg == null ? p.evaluate(js) : p.evaluate(js, arg)); }
        catch (Exception e) { return null; }
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
