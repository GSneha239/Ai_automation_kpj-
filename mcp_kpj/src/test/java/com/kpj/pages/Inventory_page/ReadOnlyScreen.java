package com.kpj.pages.Inventory_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared, READ-ONLY plumbing for the Inventory page objects written for the 2026-11-13 manual-then-automate pass
 * (Expired Item Return, Work Order Completion, Quotation Estimation).
 *
 * <p>What it adds on top of {@link BasePage}:</p>
 * <ul>
 *   <li><b>Write guard</b>: every request whose URL or ExecFlag looks like a write (IUD / Save / Insert / Update /
 *       Delete / Approve / Finalize / Complete) is <b>aborted in the browser</b> and recorded in
 *       {@link #blockedWrites}. The scripts never save, complete, submit, approve or delete anything. Confirmed
 *       manually: Quotation Estimation's Print button POSTs {@code /api/BillEstimation/IUD} (INSERTBILLESTIMATION)
 *       before printing, so without this guard a print test would create a real quotation.</li>
 *   <li><b>API + console logging</b>: every {@code /api/} response (method, URL, status), every HTTP &ge; 400,
 *       console errors and uncaught page errors are collected for the report.</li>
 *   <li><b>Toast capture</b>: nHIS toasts fade in ~1 s, so {@code window.JAlert} is wrapped and its messages kept in
 *       {@code window.__roAlerts}. {@code window.open} is wrapped too: report URLs are captured instead of opening a
 *       tab (in a single-tab browser a report tab replaces nHIS, and coming back reloads the app and logs the user
 *       out — confirmed manually).</li>
 *   <li>AngularJS helpers for ng-model inputs, plain/select2 selects and ui-grid data.</li>
 * </ul>
 */
public abstract class ReadOnlyScreen extends BasePage {

    /** Write calls are recognised by URL segment or by the ExecFlag in the JSON body. */
    private static final Pattern WRITE_URL = Pattern.compile(
            "(?i)/api/[^?]*/(IUD\\w*|\\w*Save\\w*|\\w*Insert\\w*|\\w*Update\\w*|\\w*Delete\\w*|\\w*Approve\\w*|\\w*Finali[sz]e\\w*|\\w*Complete\\w*)(\\?|$)");
    private static final Pattern WRITE_FLAG = Pattern.compile(
            "(?i)\"ExecFlag\"\\s*:\\s*\"(INSERT|UPDATE|DELETE|SAVE|IUD|APPROVE|FINALI[SZ]E|COMPLETE)\\w*\"");
    /** Read-only calls that happen to contain a write-ish word but must stay allowed. */
    private static final Pattern ALWAYS_ALLOWED = Pattern.compile("(?i)/api/(Auth|MenuMaster|CommonDropdown)/");

    public final List<String> apiLog = Collections.synchronizedList(new ArrayList<>());
    public final List<String> apiFailures = Collections.synchronizedList(new ArrayList<>());
    public final List<String> consoleErrors = Collections.synchronizedList(new ArrayList<>());
    public final List<String> blockedWrites = Collections.synchronizedList(new ArrayList<>());
    private boolean guardsInstalled = false;

    protected ReadOnlyScreen(Page page) { super(page); }

    // ---- guards & monitoring ----------------------------------------------

    /** Install the write guard and the API / console monitors once per page. Call BEFORE login. */
    public void installGuards() {
        if (guardsInstalled) return;
        guardsInstalled = true;
        page.route("**/*", route -> {
            String url = route.request().url();
            String body = "";
            try { String pd = route.request().postData(); body = pd == null ? "" : pd; } catch (Exception ignore) { }
            boolean write = !ALWAYS_ALLOWED.matcher(url).find()
                    && (WRITE_URL.matcher(url).find() || WRITE_FLAG.matcher(body).find());
            if (write) {
                String rec = route.request().method() + " " + shortUrl(url) + " " + (body.length() > 160 ? body.substring(0, 160) + "..." : body);
                blockedWrites.add(rec);
                System.out.println("READ-ONLY GUARD: blocked " + rec);
                route.abort();
            } else {
                route.resume();
            }
        });
        page.onResponse(this::recordResponse);
        page.onConsoleMessage(m -> {
            if ("error".equalsIgnoreCase(m.type()) && !m.text().contains("net::ERR_FAILED")) consoleErrors.add(trim(m.text(), 220));
        });
        page.onPageError(err -> consoleErrors.add("Uncaught: " + trim(String.valueOf(err), 220)));
    }

    private void recordResponse(Response r) {
        String url = r.url();
        if (url.contains("getInternalmsgCount")) return;
        boolean appCall = url.contains("/api/") || url.contains("/Load/") || url.contains("/SQLReport/");
        // Any failed response is recorded (static resources included) so a console "404" can be traced to its URL.
        if (r.status() >= 400) apiFailures.add(r.request().method() + " " + shortUrl(url) + " -> " + r.status());
        if (appCall) apiLog.add(r.request().method() + " " + shortUrl(url) + " -> " + r.status());
    }

    /**
     * Prove the guard is live before any button that could write: a GET to a fake IUD path must be aborted.
     * A GET to a path that does not exist is harmless even if the guard were missing.
     */
    public boolean guardVerified() {
        int before = blockedWrites.size();
        Object r = page.evaluate("async () => { try { await fetch('/api/ReadOnlyProbe/IUD?probe=1', {method:'GET'}); return 'sent'; }"
                + " catch (e) { return 'aborted'; } }");
        boolean ok = "aborted".equals(String.valueOf(r)) && blockedWrites.size() > before;
        if (ok) blockedWrites.remove(blockedWrites.size() - 1);  // the probe is not a page action
        return ok;
    }

    /** Wrap JAlert / window.open in the running app (idempotent; re-run after each in-app navigation). */
    public void hookAlerts() {
        page.evaluate("() => { window.__roAlerts = window.__roAlerts || []; window.__roOpened = window.__roOpened || [];"
                + " if (window.JAlert && !window.JAlert.__ro) { const o = window.JAlert;"
                + "   const w = function (m) { window.__roAlerts.push(String(m)); try { return o.apply(this, arguments); } catch (e) { } };"
                + "   w.__ro = true; window.JAlert = w; }"
                + " if (!window.open.__ro) { const w2 = function (u) { window.__roOpened.push(String(u)); return null; }; w2.__ro = true; window.open = w2; } }");
    }

    public void clearCaptured() {
        hookAlerts();
        page.evaluate("() => { window.__roAlerts = []; window.__roOpened = []; }");
    }

    @SuppressWarnings("unchecked")
    public List<String> alerts() {
        Object r = page.evaluate("() => window.__roAlerts || []");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public List<String> openedUrls() {
        Object r = page.evaluate("() => window.__roOpened || []");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Wait up to {@code ms} for a JAlert message to be captured; returns the messages (possibly empty). */
    public List<String> waitForAlert(int ms) {
        try {
            page.waitForFunction("() => (window.__roAlerts || []).length > 0", null, new Page.WaitForFunctionOptions().setTimeout(ms));
        } catch (Exception ignore) { }
        return alerts();
    }

    /** Text of a visible ngConfirm / jconfirm dialog, or "". */
    public String confirmDialogText() {
        // ngConfirm boxes sit in a position:fixed layer, where offsetParent is always null - use the box size instead.
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.jconfirm-box')].some(e => e.getBoundingClientRect().height > 0)",
                    null, new Page.WaitForFunctionOptions().setTimeout(4000));
        } catch (Exception ignore) { }
        Object r = page.evaluate("() => { const b = [...document.querySelectorAll('.jconfirm-box')].find(e => e.getBoundingClientRect().height > 0);"
                + " return b ? b.innerText.replace(/\\s+/g, ' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Close a visible ngConfirm dialog with its OK button. */
    public void closeConfirmDialog() {
        page.evaluate("() => { const b = [...document.querySelectorAll('.jconfirm-box')].find(e => e.getBoundingClientRect().height > 0);"
                + " if (!b) return; const ok = [...b.querySelectorAll('button')].find(x => /^\\s*ok\\s*$/i.test(x.innerText)); if (ok) ok.click(); }");
        waitForAngular(500);
    }

    /**
     * GET a captured report URL from INSIDE the browser (its session cookies and certificate trust) and say what came
     * back: status / content-type / size, "(PDF)" for a PDF and "(Crystal viewer)" for the ASP.NET report viewer page.
     * (A first version used page.context().request(), whose Node TLS stack rejected hisdemo's certificate chain.)
     */
    public String probeReport(String relativeUrl) {
        try {
            Object r = page.evaluate("async (u) => { const t0 = Date.now(); const resp = await fetch(u, {credentials: 'include'});"
                    + " const buf = await resp.arrayBuffer(); const head = new TextDecoder('latin1').decode(buf.slice(0, 2000));"
                    + " const kind = head.startsWith('%PDF') ? ' (PDF)' : (/crv_config|ReportViewer|bobj/i.test(head) ? ' (Crystal viewer)' : '');"
                    + " return resp.status + ' ' + (resp.headers.get('content-type') || '?') + ' ' + buf.byteLength + ' bytes' + kind + ' in ' + (Date.now() - t0) + ' ms'; }",
                    relativeUrl);
            return String.valueOf(r);
        } catch (Exception e) {
            return "request failed: " + trim(e.getMessage(), 160);
        }
    }

    // ---- navigation --------------------------------------------------------

    /**
     * Walk the sidebar: Inventory &gt; {@code subMenu} &gt; the link with {@code href}, then wait (up to 90 s: the
     * first load of these templates took 27-39 s during manual testing) for {@code readySelector}.
     */
    protected boolean openViaMenu(String subMenuRegex, String href, String readySelector) {
        return openViaMenuPath(href, readySelector, "^\\s*Inventory\\s*$", subMenuRegex);
    }

    /**
     * Walk any sidebar path, e.g. {@code "^\\s*Inventory\\s*$", "^\\s*POS\\s*$", "^\\s*Payment Settlement\\s*$"}, click
     * the link with {@code href}, then wait (up to 90 s) for {@code readySelector}. An in-app hash change only - no page
     * reload, which would log the user out on nHIS.
     */
    protected boolean openViaMenuPath(String href, String readySelector, String... levels) {
        if (isOnRoute(href) && isVisible(readySelector)) { hookAlerts(); return true; }
        for (int i = 0; i < levels.length; i++) expandMenuEntry(i == 0 ? null : levels[i - 1], levels[i]);
        Object clicked = page.evaluate("(h) => { const a = document.querySelector('a[href=\"' + h + '\"]'); if (!a) return false; a.click(); return true; }", href);
        if (!Boolean.TRUE.equals(clicked)) {
            System.out.println("openViaMenuPath: link " + href + " not found in the sidebar");
            return false;
        }
        try {
            page.waitForFunction("(s) => { const e = document.querySelector(s); return !!e && e.offsetParent !== null; }", readySelector,
                    new Page.WaitForFunctionOptions().setTimeout(90000));
        } catch (Exception e) {
            System.out.println("openViaMenuPath: " + readySelector + " did not appear within 90 s");
        }
        waitForAngular(800);
        hookAlerts();
        return isOnRoute(href) && isVisible(readySelector);
    }

    /** Leave the current screen through the sidebar's Home link (in-app), so the next open starts a fresh controller. */
    public void goHome() {
        page.evaluate("() => { const a = document.querySelector('a[href=\"#/LandingPage\"]') || [...document.querySelectorAll('a')].find(x => (x.textContent || '').trim() === 'Home'); if (a) a.click(); }");
        waitForAngular(1500);
    }

    /** Text of the n-th visible table's body rows (trimmed, max {@code max}). */
    @SuppressWarnings("unchecked")
    public List<String> tableRowTexts(int n, int max) {
        Object r = page.evaluate("([n, m]) => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)[n];"
                + " return t ? [...t.querySelectorAll('tbody tr')].filter(x => x.offsetParent !== null && x.innerText.trim()).slice(0, m)"
                + ".map(x => x.innerText.replace(/\\s+/g, ' ').trim()) : []; }", java.util.Arrays.asList(n, max));
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    public boolean isOnRoute(String href) {
        String u = page.url();
        return u.toLowerCase().endsWith(href.toLowerCase()) || u.toLowerCase().contains(href.toLowerCase() + "?");
    }

    public boolean isVisible(String css) {
        Object r = page.evaluate("(s) => { const e = document.querySelector(s); return !!e && e.offsetParent !== null; }", css);
        return Boolean.TRUE.equals(r);
    }

    /** Page header text, e.g. "EXPIRED ITEM RETURN (vv2026.11.13.455)". */
    public String headerText() {
        Object r = page.evaluate("() => { const e = document.querySelector('.page_title'); return e ? e.innerText.replace(/\\s+/g, ' ').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    // ---- Angular widgets ----------------------------------------------------

    /** Set an ng-model input (date boxes included) the way the app's own controls do. */
    public void setNgInput(String ngModel, String value) {
        page.evaluate("([ng, v]) => { const e = [...document.querySelectorAll('input,textarea')].find(x => x.getAttribute('ng-model') === ng && x.offsetParent !== null)"
                + " || document.querySelector('[ng-model=\"' + ng + '\"]'); if (!e) return;"
                + " const c = angular.element(e).controller('ngModel'); e.value = v; if (c) { c.$setViewValue(v); c.$render(); }"
                + " e.dispatchEvent(new Event('input', {bubbles: true})); e.dispatchEvent(new Event('change', {bubbles: true})); }",
                java.util.Arrays.asList(ngModel, value));
        dismissDatePicker();
    }

    public String inputValue(String ngModel) {
        Object r = page.evaluate("(ng) => { const e = [...document.querySelectorAll('[ng-model=\"' + ng + '\"]')].find(x => x.offsetParent !== null)"
                + " || document.querySelector('[ng-model=\"' + ng + '\"]'); return e ? String(e.value) : null; }", ngModel);
        return r == null ? null : r.toString();
    }

    /** Pick an option of a plain or select2 &lt;select&gt; by its visible text; returns the picked text or "" if absent. */
    public String pickOption(String ngModel, String optionText) {
        Object r = page.evaluate("([ng, t]) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]'); if (!s) return '';"
                + " const o = [...s.options].find(x => x.text.trim() === t); if (!o) return '';"
                + " s.value = o.value; const c = angular.element(s).controller('ngModel'); if (c) c.$setViewValue(o.value);"
                + " try { if (window.jQuery) window.jQuery(s).trigger('change'); } catch (e) { }"
                + " s.dispatchEvent(new Event('change', {bubbles: true})); return o.text.trim(); }",
                java.util.Arrays.asList(ngModel, optionText));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /** Pick the option at {@code index} (0 = the "-Select-" placeholder). */
    public String pickOptionAt(String ngModel, int index) {
        Object r = page.evaluate("([ng, i]) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]'); if (!s || s.options.length <= i) return '';"
                + " const o = s.options[i]; s.value = o.value; const c = angular.element(s).controller('ngModel'); if (c) c.$setViewValue(o.value);"
                + " try { if (window.jQuery) window.jQuery(s).trigger('change'); } catch (e) { }"
                + " s.dispatchEvent(new Event('change', {bubbles: true})); return o.text.trim(); }",
                java.util.Arrays.asList(ngModel, index));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    public int optionCount(String ngModel) {
        Object r = page.evaluate("(ng) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]'); return s ? s.options.length : -1; }", ngModel);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    public String selectedText(String ngModel) {
        Object r = page.evaluate("(ng) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]'); if (!s || s.selectedIndex < 0) return '';"
                + " return s.options[s.selectedIndex].text.trim(); }", ngModel);
        return r == null ? "" : r.toString();
    }

    /** Value the select's ng-model holds (e.g. 48 for Pharmacy Main Store). */
    public String selectedModelValue(String ngModel) {
        Object r = page.evaluate("(ng) => { const s = document.querySelector('select[ng-model=\"' + ng + '\"]'); if (!s) return '';"
                + " const c = angular.element(s).controller('ngModel'); return c ? String(c.$modelValue) : ''; }", ngModel);
        return r == null ? "" : r.toString();
    }

    /** Click the visible element whose ng-click equals {@code ngClick} (button or link). */
    public boolean clickNg(String ngClick) {
        Object r = page.evaluate("(n) => { const b = [...document.querySelectorAll('[ng-click]')].find(x => x.getAttribute('ng-click') === n && x.offsetParent !== null);"
                + " if (!b) return false; b.click(); return true; }", ngClick);
        return Boolean.TRUE.equals(r);
    }

    /** Click {@code ngClick} and wait for the response whose URL contains {@code urlPart}; returns it or null. */
    public Response clickAndWaitForApi(String ngClick, String urlPart, int timeoutMs) {
        try {
            return page.waitForResponse(r -> r.url().contains(urlPart), new Page.WaitForResponseOptions().setTimeout(timeoutMs),
                    () -> { if (!clickNg(ngClick)) throw new IllegalStateException("control not found: ng-click=" + ngClick); });
        } catch (Exception e) {
            System.out.println("clickAndWaitForApi(" + ngClick + ", " + urlPart + "): " + trim(e.getMessage(), 160));
            return null;
        }
    }

    /** Row count of a ui-grid (from its Angular data, not the virtualised DOM). */
    public int uiGridRows(String gridAttr) {
        Object r = page.evaluate("(g) => { const el = document.querySelector('[ui-grid=\"' + g + '\"]'); if (!el) return -1;"
                + " const s = angular.element(el).scope(); const o = s && s[g]; return o && o.data ? o.data.length : 0; }", gridAttr);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Values of one field across a ui-grid's data rows. */
    @SuppressWarnings("unchecked")
    public List<String> uiGridColumn(String gridAttr, String field) {
        Object r = page.evaluate("([g, f]) => { const el = document.querySelector('[ui-grid=\"' + g + '\"]'); if (!el) return [];"
                + " const s = angular.element(el).scope(); const o = s && s[g]; return (o && o.data ? o.data : []).map(x => String(x[f] == null ? '' : x[f]).trim()); }",
                java.util.Arrays.asList(gridAttr, field));
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Header labels of a ui-grid as rendered. */
    @SuppressWarnings("unchecked")
    public List<String> uiGridHeaders(String gridAttr) {
        Object r = page.evaluate("(g) => [...document.querySelectorAll('[ui-grid=\"' + g + '\"] .ui-grid-header-cell-label')]"
                + ".map(h => h.innerText.trim()).filter(x => x)", gridAttr);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Header labels of the n-th visible HTML table on the screen. */
    @SuppressWarnings("unchecked")
    public List<String> tableHeaders(int n) {
        Object r = page.evaluate("(n) => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)[n];"
                + " return t ? [...t.querySelectorAll('th')].map(h => h.innerText.replace(/\\s+/g, ' ').trim()).filter(x => x) : []; }", n);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Visible, non-empty body rows of the n-th visible HTML table. */
    public int tableRows(int n) {
        Object r = page.evaluate("(n) => { const t = [...document.querySelectorAll('table')].filter(x => x.offsetParent !== null)[n];"
                + " return t ? [...t.querySelectorAll('tbody tr')].filter(x => x.offsetParent !== null && x.innerText.trim()).length : -1; }", n);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Visible Bootstrap/uib modal count. */
    public int openModals() {
        Object r = page.evaluate("() => [...document.querySelectorAll('.modal')].filter(m => getComputedStyle(m).display === 'block' && m.classList.contains('in')).length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Dismiss every open uib modal through the app's own modal stack (no data is touched). */
    public void dismissModals() {
        page.evaluate("() => { try { const inj = angular.element(document.body).injector();"
                + " if (inj.has('$uibModalStack')) inj.get('$uibModalStack').dismissAll('cancel'); } catch (e) { } }");
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.modal')].some(m => getComputedStyle(m).display === 'block' && m.classList.contains('in'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }
        waitForAngular(400);
    }

    public static String today() {
        return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    protected static String shortUrl(String url) {
        return url.replaceFirst("^https?://[^/]+", "").replaceAll("v=v[0-9.]+", "v=..");
    }

    protected static String trim(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
