package com.kpj.pages.Investigation_page.Radiology_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Investigation &gt; Radiology &gt; <b>Result Entry</b> — Page Object.
 *
 * <p>Served from {@code #/Result}. The screen searches a date range and lists the tests; the <b>Result
 * Entry Report</b> button prints what is listed.</p>
 */
public class RadiologyResultEntry extends BasePage {

    public RadiologyResultEntry(Page page) { super(page); }

    private static final String M = "ResultEntry.";

    public String lastMenu = "", lastRoute = "", lastDates = "", lastSearch = "", lastRows = "",
            lastSubGroup = "", lastTick = "", lastReport = "", lastRefusal = "";
    public boolean reportBlank = true;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    // ---- navigation ------------------------------------------------------

    /**
     * The menu label, anchored to the WHOLE name. Radiology carries Result Entry, Result Entry
     * Authentication and Result Entry Admin Auth, so a loose match opens the wrong screen.
     */
    protected String menuLabelRegex() { return "^\\s*result\\s*entry\\s*$"; }

    /** The route this screen is served from, without the hash. */
    protected String routeHash() { return "/Result"; }

    /** Is the browser on this screen? Compared on the whole route, not a prefix. */
    protected boolean routeMatches(String route) {
        return route.equals("result");
    }

    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*investigation\\s*$");
            clickMenu("^\\s*radiology\\s*$");
            lastMenu = describeMenu();
            // Anchored to the WHOLE label: the Radiology submenu also carries "Result Entry
            // Authentication" and "Result Entry Admin Auth", and a loose match would open one of those.
            href = page.evaluate("(re) => {" + JS
                    + " const rx=new RegExp(re,'i');"
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>rx.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__reMenu'; return a.getAttribute('href')||''; }",
                    menuLabelRegex());
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__reMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("RadiologyResultEntry.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__reMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(4000);
        }
        if (!onScreen()) {
            try { page.evaluate("(h) => { window.location.hash = h; }", routeHash()); }
            catch (Exception ignore) { }
            waitForAngular(5000);
        }
        try {
            page.waitForFunction("() => !!document.querySelector(\"input[ng-model='" + M + "FromDate']\")",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        return onScreen();
    }

    private void clickMenu(String textRegex) {
        page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a,li>a,span')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(a) a.click(); }", textRegex);
        waitForAngular(1500);
    }

    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** On this screen — matched on the WHOLE route, so a sister screen is never mistaken for it. */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        int h = u.indexOf('#');
        if (h < 0) return false;
        String route = u.substring(h).replace("#", "").replace("/", "");
        int q = route.indexOf('?');
        if (q >= 0) route = route.substring(0, q);
        return routeMatches(route);
    }

    // ---- search ----------------------------------------------------------

    public String enterDateRange(String from, String to) {
        String r1 = typeDate(M + "FromDate", from);
        String r2 = typeDate(M + "ToDate", to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("RadiologyResultEntry: " + lastDates);
        return lastDates;
    }

    private String typeDate(String model, String value) {
        try {
            com.microsoft.playwright.Locator box = page.locator("input[ng-model='" + model + "']").first();
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            box.press("Escape");
        } catch (Exception e) { return "(typing failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(500);
        Object back = page.evaluate("(m) => { const e=document.querySelector(\"input[ng-model='\"+m+\"']\");"
                + " return e? (e.value||'') : '(gone)'; }", model);
        return back == null ? "" : back.toString();
    }

    public boolean datesEntered(String from, String to) {
        return lastDates != null && lastDates.contains("FromDate=" + from)
                && lastDates.contains("ToDate=" + to);
    }

    /**
     * Click <b>Search</b> ({@code fnSearchAllorderbookingdetails}) and report what came back — including
     * anything the screen SAID, since these screens refuse to search when a filter they require is
     * missing and leave the grid untouched rather than reporting an error.
     */
    public String clickSearch() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/fnSearchAllorderbookingdetails/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(5000);
        Object said = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('.toast-message,.toast')].filter(vis)"
                + "   .map(x=>norm(x.textContent)).filter(x=>x);"
                + " return t.length? t[0] : ''; }");
        lastRefusal = said == null ? "" : said.toString()
                .replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        lastSearch = "clicked Search [fnSearchAllorderbookingdetails] -> " + rowCount() + " record(s)"
                + (lastRefusal.isEmpty() ? "" : "; the screen said: \"" + lastRefusal + "\"");
        System.out.println("RadiologyResultEntry: " + lastSearch);
        return lastSearch;
    }

    /** The screen declined to search because a filter it requires was not set. */
    public boolean searchRefused() {
        return lastRefusal != null && lastRefusal.toLowerCase().contains("please");
    }

    /** Pick a sub group and search again — used only when the screen asks for one. */
    public String selectSubGroupWithRecords() {
        Object count = page.evaluate("() => {" + JS
                + " const e=document.querySelector(\"select[ng-model='" + M + "subgroupid']\");"
                + " if(!e) return 0;"
                + " return [...e.options].filter(o=>o.value"
                + "   && !/^-*\\s*select/i.test(norm(o.text))).length; }");
        int n = count instanceof Number ? ((Number) count).intValue() : 0;
        StringBuilder tried = new StringBuilder();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Object name = page.evaluate("(i) => {" + JS
                    + " const e=document.querySelector(\"select[ng-model='" + M + "subgroupid']\");"
                    + " if(!e) return '';"
                    + " const reals=[...e.options].map((o,k)=>({o,k}))"
                    + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)));"
                    + " e.selectedIndex=reals[i].k;"
                    + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ angular.element(e).triggerHandler('change'); }catch(err){}"
                    + " return norm((e.options[e.selectedIndex]||{}).text); }", idx);
            waitForAngular(1000);
            clickSearch();
            int rows = rowCount();
            tried.append(tried.length() == 0 ? "" : ", ").append(name).append(" -> ").append(rows);
            if (rows > 0) {
                lastSubGroup = name + " — it returns " + rows + " record(s). Tried: " + tried;
                return lastSubGroup;
            }
        }
        lastSubGroup = "(no sub group returned a record. Tried: " + tried + ")";
        return lastSubGroup;
    }

    public int rowCount() {
        Object n = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .filter(r=>norm(r.textContent)).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public String describeRows() {
        Object r = page.evaluate("() => {" + JS
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .map(x=>norm(x.textContent)).filter(t=>t);"
                + " return rows.length+' record(s)'+(rows.length? ': '+rows.slice(0,2)"
                + "   .map(t=>t.slice(0,110)).join('  ;;  ') : ''); }");
        lastRows = r == null ? "" : r.toString();
        return lastRows;
    }

    /**
     * Tick the first record.
     *
     * <p>Whichever control this grid uses: a real checkbox if it has one, otherwise ui-grid's selection
     * button. Both are read back, so a click that lands without selecting is not counted.</p>
     */
    public String tickFirstRecord() {
        Object r = page.evaluate("() => {" + JS
                // The tick is ui-grid's own selection button. The checkboxes in these rows are STATUS
                // columns - Result Entry, Finalized - and clicking one reports a tick that selects
                // nothing, which is how this first read "checked=false, model=false".
                + " const sb=[...document.querySelectorAll("
                + "   '.ui-grid-row .ui-grid-selection-row-header-buttons')].filter(vis)[0];"
                + " if(sb){ const row=sb.closest('.ui-grid-row');"
                + "   const text=row? norm(row.textContent).slice(0,90) : '?';"
                + "   sb.click();"
                + "   const marked=[...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "     .filter(x=>/ui-grid-row-selected/.test(x.className)"
                + "       || x.querySelector('.ui-grid-row-selected')).length;"
                + "   let api='(unreadable)';"
                + "   try{ const g=angular.element(document.querySelector('[ui-grid]')).scope();"
                + "        const a=g && (g.gridApi || (g.grid && g.grid.api));"
                + "        if(a && a.selection) api=String(a.selection.getSelectedRows().length); }catch(e){}"
                + "   return 'clicked the tick [selectButtonClick] -> '+marked+' row element(s) marked'"
                + "     +(api==='(unreadable)'? '' : ', the grid reports '+api+' selected')"
                + "     +'; the row reads: '+text; }"
                + " const cb=[...document.querySelectorAll('.ui-grid-row input[type=checkbox]')]"
                + "   .filter(vis).filter(e=>!/resultentry|finalized|isprint|status/i"
                + "     .test(e.getAttribute('ng-model')||''))[0];"
                + " if(!cb) return '(no tick control on any record - the row carries only status boxes)';"
                + " if(!cb.checked) cb.click();"
                + " return 'ticked the checkbox ['+(cb.getAttribute('ng-model')||'?')+'] -> checked='"
                + "   +cb.checked; }");
        lastTick = r == null ? "" : r.toString();
        waitForAngular(1500);
        System.out.println("RadiologyResultEntry: tick -> " + lastTick);
        return lastTick;
    }

    /** Judged on the grid's own selection where it can be read, else on the marked row elements. */
    public boolean recordTicked() {
        if (lastTick == null || lastTick.startsWith("(")) return false;
        java.util.regex.Matcher api = java.util.regex.Pattern
                .compile("the grid reports (\\d+) selected").matcher(lastTick);
        if (api.find()) return Integer.parseInt(api.group(1)) > 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("-> (\\d+) row element\\(s\\) marked").matcher(lastTick);
        if (m.find()) return Integer.parseInt(m.group(1)) > 0;
        return lastTick.contains("checked=true");
    }

    public String lastTab = "";

    /** Open a tab by name — this screen carries <b>Test</b> and <b>Report</b>. */
    public String openTab(String name) {
        Object r = page.evaluate("(n) => {" + JS
                + " const rx=new RegExp('^\\\\s*'+n+'\\\\s*$','i');"
                + " const t=[...document.querySelectorAll('li,a')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(!t) return '(no '+n+' tab on this screen)';"
                + " (t.querySelector('a')||t).click();"
                + " return 'clicked the '+n+' tab'; }", name);
        lastTab = r == null ? "" : r.toString();
        waitForAngular(2000);
        System.out.println("RadiologyResultEntry: tab -> " + lastTab);
        return lastTab;
    }

    public boolean tabOpened() { return lastTab != null && lastTab.startsWith("clicked"); }

    /**
     * Click <b>Result Entry Report</b> ({@code PrintPendingResultEntry}) and judge what it produced.
     *
     * <p>The report is a PDF shown in Chrome's viewer, so the file itself is judged rather than the page
     * around it — screenshotting that viewer is blank however good the report is.</p>
     */
    public String clickReportAndVerify() {
        int tabsBefore = page.context().pages().size();
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/PrintPendingResultEntry/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(3000);
        com.kpj.pages.PdfReport.Result r = com.kpj.pages.PdfReport.capture(page, tabsBefore, 12000);
        reportBlank = r.blank;
        lastReport = r.diagnostics;
        System.out.println("RadiologyResultEntry: report -> " + lastReport);
        return lastReport;
    }
}
