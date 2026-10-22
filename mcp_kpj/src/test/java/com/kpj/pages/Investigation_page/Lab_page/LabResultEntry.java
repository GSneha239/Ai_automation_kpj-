package com.kpj.pages.Investigation_page.Lab_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Investigation &gt; Lab &gt; <b>Lab Result Entry</b> — Page Object.
 *
 * <p>Served from {@code #/LabResultEntry} (confirmed live, devhis build v2026.10.18.255). The screen carries
 * TWO ui-grids, bound as {@code gridOptions} (<b>List of Orders</b>) and {@code gridOptions1} (<b>Test
 * Details</b>), and each has its own tick: ui-grid's selection button, rendered in a PINNED LEFT container,
 * separate from the body container that holds the data cells. Every selection is therefore scoped to its
 * grid — an unscoped lookup ticks the wrong one.</p>
 *
 * <p>How the screen behaves, as confirmed live:</p>
 * <ul>
 *   <li><b>Search</b> ({@code GetTodaysResultentryTest()}) fills <b>Test Details</b> with every matching test.</li>
 *   <li>The <b>List of Orders</b> header is a collapsible accordion toggle
 *       ({@code <a id="lstOrder" data-toggle="collapse" ng-click="fnSearchResultEntry()">}): clicking it loads
 *       the orders grid and CLEARS Test Details until an order is ticked. It is a toggle, so a second click
 *       collapses it again — and {@code aria-expanded} is not a reliable read — hence the state is checked from
 *       the panel body itself.</li>
 *   <li>Ticking an order's tick reloads <b>Test Details</b> with that order's tests.</li>
 *   <li>A test row carries the same tick, plus a real checkbox ({@code row.entity.IsChecked},
 *       {@code GetcheckTest(row)}). The other checkboxes in the row (Result Entry, Finalized, Report
 *       Delivered) are read-only status columns.</li>
 *   <li><b>Result Entry Report</b> ({@code PrintPendingResultEntry()}) opens a PDF in a new tab.</li>
 * </ul>
 *
 * <p>The menu label is matched in full: Lab also carries <b>Lab Result Entry Admin Auth</b>, and a loose match
 * would open that screen instead.</p>
 */
public class LabResultEntry extends BasePage {

    public LabResultEntry(Page page) { super(page); }

    private static final String M = "LabResultEntry.";

    /** ui-grid bindings: the List of Orders grid and the Test Details grid. */
    private static final String ORDERS_GRID = "gridOptions";
    private static final String TESTS_GRID = "gridOptions1";

    public String lastMenu = "", lastRoute = "", lastDates = "", lastDateRange = "", lastSearch = "",
            lastOrders = "", lastOrderTick = "", lastTests = "", lastTestTick = "", lastTestCheck = "",
            lastReport = "";
    /** True when the last order tick read back as selected. */
    public boolean orderTicked = false;
    public boolean reportBlank = true;
    /** First page of the generated report rendered as an image (or a page screenshot when it could not be rendered). */
    public byte[] reportPng;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          // the grid for a binding name, falling back to its position (0 = orders, 1 = tests)
          + "const gridBy=(opt,idx)=>document.querySelector(\"[ui-grid='\"+opt+\"']\")"
          + "  || [...document.querySelectorAll('[ui-grid]')][idx];"
          // DATA rows only: the pinned-left container's rows carry the tick but no text, the body container's
          // rows carry the text — a row counts when it has text
          + "const dataRows=g=>g? [...g.querySelectorAll('.ui-grid-row')].filter(vis).filter(r=>norm(r.textContent)) : [];"
          + "const ticks=g=>g? [...g.querySelectorAll('.ui-grid-row .ui-grid-selection-row-header-buttons')].filter(vis) : [];";

    // ---- navigation ------------------------------------------------------

    /** The menu label, anchored to the WHOLE name — Lab also carries "Lab Result Entry Admin Auth". */
    private static final String MENU_LABEL = "^\\s*lab\\s*result\\s*entry\\s*$";

    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        Object href = "";
        for (int attempt = 1; attempt <= 3; attempt++) {
            // The two menu levels are TOGGLES: clicking one that is already open closes it again. So look for
            // the link first, and only open the levels that are still shut.
            href = findMenuLink();
            if (href == null || href.toString().isEmpty()) {
                if (!menuEntryVisible("^\\s*lab\\s*$")) clickMenu("^\\s*investigation\\s*$");
                clickMenu("^\\s*lab\\s*$");
                href = findMenuLink();
            }
            lastMenu = describeMenu();
            if (href != null && !href.toString().isEmpty()) break;
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__lreMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("LabResultEntry.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__lreMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(4000);
        }
        if (!onScreen()) {
            try { page.evaluate("() => { window.location.hash = '/LabResultEntry'; }"); }
            catch (Exception ignore) { }
            waitForAngular(5000);
        }
        try {
            page.waitForFunction("() => !!document.querySelector(\"input[ng-model='" + M + "FromDate']\")",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        return onScreen();
    }

    /** Tag the visible "Lab Result Entry" link (whole label) and return its href, or "". */
    private Object findMenuLink() {
        return page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a[href]')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__lreMenu'; return a.getAttribute('href')||''; }", MENU_LABEL);
    }

    private boolean menuEntryVisible(String textRegex) {
        return Boolean.TRUE.equals(page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " return [...document.querySelectorAll('a,li>a,span')].filter(vis).some(x=>rx.test(norm(x.textContent))); }",
                textRegex));
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

    /** On this screen — matched on the WHOLE route, so "Lab Result Entry Admin Auth" is never mistaken for it. */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        int h = u.indexOf('#');
        if (h < 0) return false;
        String route = u.substring(h).replace("#", "").replace("/", "");
        int q = route.indexOf('?');
        if (q >= 0) route = route.substring(0, q);
        return route.equals("labresultentry");
    }

    // ---- search ----------------------------------------------------------

    /**
     * Make sure the <b>Date Range</b> box is ticked — the From/To dates only apply while it is. It arrives
     * ticked, so this normally just reports that.
     */
    public String ensureDateRangeOn() {
        Object r = page.evaluate("() => {" + JS
                + " const c=document.querySelector(\"input[ng-model='" + M + "IsDateRange']\");"
                + " if(!c) return '(no Date Range box on this screen)';"
                + " const was=c.checked; if(!was) c.click();"
                + " return was? 'Date Range was already ticked' : 'ticked Date Range (it was off) -> '+c.checked; }");
        lastDateRange = r == null ? "" : r.toString();
        return lastDateRange;
    }

    /** Enter the <b>From</b> and <b>To</b> dates — cleared first, since these pickers append to what is there. */
    public String enterDateRange(String from, String to) {
        ensureDateRangeOn();
        String r1 = typeDate(M + "FromDate", from);
        String r2 = typeDate(M + "ToDate", to);
        lastDates = "FromDate=" + r1 + " | ToDate=" + r2;
        waitForAngular(500);
        System.out.println("LabResultEntry: " + lastDates + " (" + lastDateRange + ")");
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
        waitForAngular(600);
        Object back = page.evaluate("(m) => { const e=document.querySelector(\"input[ng-model='\"+m+\"']\");"
                + " return e? (e.value||'') : '(gone)'; }", model);
        String got = back == null ? "" : back.toString();
        if (!value.equals(got)) {
            // A pre-filled picker box (class txtDate, inputmode=none, maxlength 10): typed keystrokes can be
            // discarded and the box ends up empty or wrong. Set the value through the box's own model, as the
            // other screens here do.
            Object set = page.evaluate("(a) => { const e=document.querySelector(\"input[ng-model='\"+a.m+\"']\"); if(!e) return '(gone)';"
                    + " e.value=a.v;"
                    + " try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(a.v); c.$render(); } }catch(x){}"
                    + " e.dispatchEvent(new Event('input',{bubbles:true}));"
                    + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " e.dispatchEvent(new Event('blur',{bubbles:true}));"
                    + " return e.value||''; }", java.util.Map.of("m", model, "v", value));
            waitForAngular(500);
            got = set == null ? "" : set.toString();
        }
        dismissDatePicker();   // close the calendar the date entry left open
        return got;
    }

    public boolean datesEntered(String from, String to) {
        return lastDates != null && lastDates.contains("FromDate=" + from)
                && lastDates.contains("ToDate=" + to);
    }

    /** Click <b>Search</b> ({@code GetTodaysResultentryTest}) — it fills Test Details with every matching test. */
    public String clickSearch() {
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/GetTodaysResultentryTest/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(5000);
        Object said = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('.toast-message,.toast')].filter(vis)"
                + "   .map(x=>norm(x.textContent)).filter(x=>x);"
                + " return t.length? t[0] : ''; }");
        String toast = said == null ? "" : said.toString()
                .replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        lastSearch = "clicked Search [GetTodaysResultentryTest] -> " + testRows() + " test(s) in Test Details"
                + (toast.isEmpty() ? "" : "; the screen said: \"" + toast + "\"");
        System.out.println("LabResultEntry: " + lastSearch);
        return lastSearch;
    }

    // ---- grids ------------------------------------------------------------

    private int rowsIn(String opt, int idx) {
        Object n = page.evaluate("(a) => {" + JS
                + " return dataRows(gridBy(a.opt,a.idx)).length; }", java.util.Map.of("opt", opt, "idx", idx));
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public int orderRows() { return rowsIn(ORDERS_GRID, 0); }
    public int testRows() { return rowsIn(TESTS_GRID, 1); }

    private String describeGrid(String opt, int idx) {
        Object r = page.evaluate("(a) => {" + JS
                + " const g=gridBy(a.opt,a.idx); if(!g) return '(no grid)';"
                + " const rows=dataRows(g).map(x=>norm(x.textContent));"
                + " return rows.length+' row(s)'+(rows.length? ': '+rows.slice(0,2)"
                + "   .map(t=>t.slice(0,110)).join('  ;;  ') : ''); }", java.util.Map.of("opt", opt, "idx", idx));
        return r == null ? "" : r.toString();
    }

    public String describeOrders() { lastOrders = describeGrid(ORDERS_GRID, 0); return lastOrders; }
    public String describeTests() { lastTests = describeGrid(TESTS_GRID, 1); return lastTests; }

    // ---- List of Orders ---------------------------------------------------

    /**
     * Click the <b>List of Orders</b> header ({@code fnSearchResultEntry()}) and wait for the orders grid.
     *
     * <p>The header is an accordion toggle, so the state is read from the panel body itself, never from
     * {@code aria-expanded}: if a click left the panel shut (it was already open, so the click collapsed it),
     * click once more.</p>
     */
    public String openListOfOrders() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            page.evaluate("() => {" + JS
                    + " const a=document.querySelector(\"a#lstOrder\")"
                    + "   || [...document.querySelectorAll(\"[ng-click*='fnSearchResultEntry']\")][0];"
                    + " if(a) a.click(); }");
            waitForAngular(3500);
            if (orderRows() > 0 || ordersPanelOpen()) break;
        }
        // The grid fills a beat after the panel opens.
        try {
            page.waitForFunction("() => {" + JS
                    + " return dataRows(gridBy('" + ORDERS_GRID + "',0)).length>0; }", null,
                    new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        describeOrders();
        System.out.println("LabResultEntry: List of Orders -> " + lastOrders);
        return lastOrders;
    }

    private boolean ordersPanelOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS
                + " const a=document.querySelector('a#lstOrder'); if(!a) return false;"
                + " const t=document.querySelector(a.getAttribute('data-target')||'#collapseFive');"
                + " return !!t && (/\\bin\\b/.test(t.className) && vis(t)); }"));
    }

    // ---- selection --------------------------------------------------------

    /**
     * Click the tick symbol on the {@code n}-th (0-based) row of a grid, and read it back.
     *
     * <p>Scoped to DATA rows of that grid: the header's select-all is a different control, and both grids carry
     * one, so an unscoped lookup ticks the wrong grid. The tick is judged on the button itself — it takes the
     * {@code ui-grid-row-selected} class when the click lands.</p>
     *
     * @return {@code {readBackAsSelected(true|false), description}}
     */
    private String[] tickNth(String opt, int idx, int n, String what) {
        Object r = page.evaluate("(a) => {" + JS
                + " const g=gridBy(a.opt,a.idx); if(!g) return '0|(no grid)';"
                + " const t=ticks(g)[a.n];"
                + " if(!t) return '0|(no tick symbol on row '+(a.n+1)+')';"
                + " const body=dataRows(g); const text=norm((body[a.n]||{}).textContent||'').slice(0,100);"
                + " t.click();"
                + " return '1|'+text; }", java.util.Map.of("opt", opt, "idx", idx, "n", n));
        String[] first = String.valueOf(r).split("\\|", 2);
        if (!"1".equals(first[0])) return new String[]{"false", first.length > 1 ? first[1] : String.valueOf(r)};
        String rowText = first.length > 1 ? first[1] : "";
        waitForAngular(2500);
        Object after = page.evaluate("(a) => {" + JS
                + " const g=gridBy(a.opt,a.idx); if(!g) return 'false|(unreadable)';"
                + " const t=ticks(g)[a.n];"
                + " const on=!!t && /ui-grid-row-selected/.test(t.className);"
                + " let api='(unreadable)';"
                + " try{ const sc=angular.element(g).scope();"
                + "      const x=sc && (sc.gridApi1 || sc.gridApi || (sc.grid && sc.grid.api));"
                + "      if(x && x.selection) api=String(x.selection.getSelectedRows().length); }catch(e){}"
                + " return on+'|'+api; }", java.util.Map.of("opt", opt, "idx", idx, "n", n));
        String[] parts = String.valueOf(after).split("\\|");
        boolean on = "true".equals(parts[0]);
        String desc = "clicked the tick symbol on " + what + " #" + (n + 1) + " -> the tick button "
                + (on ? "now reads as selected" : "does NOT read as selected")
                + (parts.length > 1 && !parts[1].equals("(unreadable)")
                    ? ", the grid's selection API reports " + parts[1] + " selected" : "")
                + "; the row reads: " + rowText;
        System.out.println("LabResultEntry: " + desc);
        return new String[]{String.valueOf(on), desc};
    }

    /**
     * Tick the first order and judge it by what it PRODUCED: Test Details reloads to that order's tests.
     * If that order has none (which is data, not a defect), the next few are tried, up to five.
     */
    public String tickFirstOrder() {
        int available = orderRows();
        int max = Math.max(1, Math.min(5, available));
        StringBuilder note = new StringBuilder();
        for (int i = 0; i < max; i++) {
            String[] t = tickNth(ORDERS_GRID, 0, i, "order");
            orderTicked = "true".equals(t[0]);
            // Test Details reloads for the ticked order a beat later.
            try {
                page.waitForFunction("() => {" + JS + " return dataRows(gridBy('" + TESTS_GRID + "',1)).length>0; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(8000));
            } catch (Exception ignore) { }
            int tests = testRows();
            if (tests > 0 || i == max - 1) {
                lastOrderTick = t[1] + "; Test Details now holds " + tests + " test row(s) for the selected order"
                        + (i > 0 ? " (order #" + (i + 1) + " — the " + i + " before it loaded no tests: " + note + ")" : "");
                break;
            }
            note.append(note.length() == 0 ? "" : ", ").append("order #").append(i + 1).append(" -> 0 tests");
        }
        System.out.println("LabResultEntry: order tick -> " + lastOrderTick);
        return lastOrderTick;
    }

    /** Tick the first row of Test Details. */
    public String tickFirstTest() {
        String[] t = tickNth(TESTS_GRID, 1, 0, "test");
        lastTestTick = t[1];
        testTicked = "true".equals(t[0]);
        return lastTestTick;
    }
    public boolean testTicked = false;

    /**
     * Tick the real checkbox ({@code row.entity.IsChecked}) on the first Test Details row.
     *
     * <p>The row also carries read-only status checkboxes (Result Entry, Finalized, Report Delivered), which
     * are disabled and must never be mistaken for it — so the lookup names the model. A real click is used (the
     * box's {@code ng-click} is {@code GetcheckTest(row)}), read back afterwards.</p>
     */
    public String tickFirstTestCheckbox() {
        Object tagged = page.evaluate("() => {" + JS
                + " const g=gridBy('" + TESTS_GRID + "',1); if(!g) return '(no grid)';"
                + " const cb=[...g.querySelectorAll(\"input[type=checkbox][ng-model='row.entity.IsChecked']\")]"
                + "   .filter(vis)[0];"
                + " if(!cb) return '(no selectable checkbox on any test row)';"
                + " cb.id='__lreCb'; return 'ok'; }");
        if (!"ok".equals(String.valueOf(tagged))) {
            lastTestCheck = String.valueOf(tagged);
            return lastTestCheck;
        }
        try {
            page.locator("#__lreCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("LabResultEntry: real checkbox click failed (" + e.getMessage().split("\n")[0]
                    + ") — falling back to an in-page click");
            page.evaluate("() => { const c=document.getElementById('__lreCb'); if(c && !c.checked) c.click(); }");
        }
        waitForAngular(1500);
        Object state = page.evaluate("() => { const c=document.getElementById('__lreCb');"
                + " const r = c? 'checked='+c.checked : 'checked=false (the box went away)';"
                + " if(c) c.removeAttribute('id'); return r; }");
        lastTestCheck = "clicked the row checkbox [row.entity.IsChecked] -> " + state;
        System.out.println("LabResultEntry: test checkbox -> " + lastTestCheck);
        return lastTestCheck;
    }

    public boolean testCheckboxTicked() { return lastTestCheck != null && lastTestCheck.contains("checked=true"); }

    // ---- Edit -------------------------------------------------------------

    public String lastEdit = "", lastItemTab = "", lastUpload = "", lastAdd = "", lastSave = "",
            lastSaveToast = "";
    /** Document List rows before the file was added. */
    public int docRowsBefore = 0;

    /** On the Edit screen ({@code #/edit-LabResultEntry/<n>}). */
    public boolean onEditScreen() {
        String u = page.url().toLowerCase();
        int h = u.indexOf('#');
        return h >= 0 && u.substring(h).contains("edit-labresultentry");
    }

    /**
     * Click <b>Edit</b> ({@code editLabResult()}) on the footer and wait for the Edit screen. It is its own
     * route, not a dialog, and needs a test row to be selected first.
     */
    public String clickEdit() {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/editLabResult/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(3000);
        try {
            page.waitForFunction("() => /edit-labresultentry/i.test(location.hash)"
                    + " && [...document.querySelectorAll('button')].some(b=>/fnIUDResultEntry/.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        Object r = page.evaluate("() => {" + JS
                + " const h=document.body? norm(document.body.innerText).match(/Name\\s*:[^]*?OrderNo\\.\\s*:\\s*\\d+/) : null;"
                + " return h? h[0].slice(0,110) : ''; }");
        lastEdit = (onEditScreen() ? "the Edit screen opened at " + page.url() : "the Edit screen did NOT open (" + page.url() + ")")
                + (r == null || r.toString().isEmpty() ? "" : "; it reads: \"" + r + "\"");
        System.out.println("LabResultEntry: edit -> " + lastEdit);
        return lastEdit;
    }

    /**
     * Click the <b>Item Details</b> tab on the Edit screen (next to <b>Test Details</b>) and read the tab back.
     * The tabs are plain Bootstrap tabs, so the active one is the {@code <li class="active">}.
     */
    public String openItemDetailsTab() {
        page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('li > a, a')].filter(vis)"
                + "   .find(x=>/^\\s*item\\s*details\\s*$/i.test(norm(x.textContent)));"
                + " if(a) a.click(); }");
        waitForAngular(2500);
        Object r = page.evaluate("() => {" + JS
                + " const act=[...document.querySelectorAll('li.active')].filter(vis).map(x=>norm(x.textContent));"
                + " const grid=[...document.querySelectorAll('th')].filter(vis).some(t=>/item\\s*name/i.test(norm(t.textContent)));"
                + " return JSON.stringify({active:act, itemGrid:grid}); }");
        lastItemTab = "clicked Item Details -> " + r;
        System.out.println("LabResultEntry: item details -> " + lastItemTab);
        return lastItemTab;
    }

    public boolean itemDetailsOpen() {
        return lastItemTab != null && lastItemTab.toLowerCase().contains("item details\"")
                && lastItemTab.contains("\"itemGrid\":true");
    }

    /** Rows in the Document List table (File Name / Remark / Preview / Remove). */
    public int documentRows() {
        Object n = page.evaluate("() => {" + JS
                + " const t=[...document.querySelectorAll('table')].filter(vis)"
                + "   .find(x=>/file\\s*name/i.test(norm((x.querySelector('thead')||x).textContent)));"
                + " return t? [...t.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent)).length : -1; }");
        return n instanceof Number ? ((Number) n).intValue() : -1;
    }

    /** True when the Document List shows a row for {@code fileName}. */
    public boolean documentListed(String fileName) {
        return Boolean.TRUE.equals(page.evaluate("(n) => {" + JS
                + " const t=[...document.querySelectorAll('table')].filter(vis)"
                + "   .find(x=>/file\\s*name/i.test(norm((x.querySelector('thead')||x).textContent)));"
                + " return !!t && [...t.querySelectorAll('tbody tr')].some(r=>norm(r.textContent).toLowerCase().includes(n.toLowerCase())); }",
                fileName));
    }

    /**
     * Click <b>Choose File</b> and pick {@code file}. The click is real and the file chooser it raises is
     * answered, as a person would; if no chooser is raised the file is set on the input directly. Either way the
     * input's own {@code onchange} ({@code fileChanged(files)}) fires.
     */
    public String chooseFile(java.nio.file.Path file) {
        docRowsBefore = documentRows();
        String how;
        try {
            com.microsoft.playwright.FileChooser fc = page.waitForFileChooser(
                    new Page.WaitForFileChooserOptions().setTimeout(8000),
                    () -> page.locator("input#SelectFile").click());
            fc.setFiles(file);
            how = "clicked Choose File and answered the file chooser";
        } catch (Exception e) {
            try {
                page.locator("input#SelectFile").setInputFiles(file,
                        new com.microsoft.playwright.Locator.SetInputFilesOptions().setTimeout(10000));
                how = "no file chooser appeared (" + e.getMessage().split("\n")[0]
                        + "), so the file was set on the Choose File input directly";
            } catch (Exception e2) {
                lastUpload = "(could not choose the file: " + e2.getMessage().split("\n")[0] + ")";
                System.out.println("LabResultEntry: upload -> " + lastUpload);
                return lastUpload;
            }
        }
        waitForAngular(1500);
        Object chosen = page.evaluate("() => { const f=document.querySelector('input#SelectFile');"
                + " return f && f.files && f.files.length? f.files[0].name+' ('+f.files[0].size+' bytes)' : ''; }");
        lastUpload = how + " -> the input now holds " + (chosen == null || chosen.toString().isEmpty() ? "NO file" : chosen);
        System.out.println("LabResultEntry: upload -> " + lastUpload);
        return lastUpload;
    }

    public boolean fileChosen() { return lastUpload != null && lastUpload.contains("now holds ") && !lastUpload.endsWith("NO file"); }

    /** Click <b>Add</b> ({@code AddDocList}) and report the Document List before and after. */
    public String clickAdd(String fileName) {
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/AddDocList/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(2500);
        int after = documentRows();
        lastAdd = "clicked Add [AddDocList] -> the Document List went from " + docRowsBefore + " to " + after
                + " row(s); " + (documentListed(fileName) ? "\"" + fileName + "\" is listed" : "\"" + fileName + "\" is NOT listed");
        System.out.println("LabResultEntry: add -> " + lastAdd);
        return lastAdd;
    }

    public boolean documentAdded(String fileName) {
        return documentListed(fileName) && documentRows() > docRowsBefore;
    }

    /**
     * Click <b>Save</b> ({@code fnIUDResultEntry()}), answer <b>Yes</b> on the confirmation dialog, and return the
     * toast. Toasts are harvested with a MutationObserver (they fade), after clearing any stale one so an old
     * message is never read as this save's answer.
     */
    public String clickSaveConfirmAndGetToast() {
        page.evaluate("() => { window.__lreToasts=[]; if(window.__lreObs) window.__lreObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__lreToasts.includes(t)) window.__lreToasts.push(t); }); };"
                + " window.__lreObs=new MutationObserver(grab); window.__lreObs.observe(document.body,{childList:true,subtree:true}); }");
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>/fnIUDResultEntry/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        boolean answered = false;
        for (int i = 0; i < 40; i++) {
            Object ans = page.evaluate("() => {" + JS
                    + " const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].filter(vis)"
                    + "   .find(m=>/save|confirm|sure|proceed/i.test(norm(m.textContent)));"
                    + " if(box){ const y=[...box.querySelectorAll('button')].filter(vis)"
                    + "     .find(b=>/^\\s*(yes|ok|save|confirm)\\s*$/i.test(norm(b.textContent)));"
                    + "   if(y){ const t=norm(box.textContent).slice(0,90); y.click(); return t; } }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + "   const t=norm(el.textContent); window.__lreToasts=window.__lreToasts||[];"
                    + "   if(t && !window.__lreToasts.includes(t)) window.__lreToasts.push(t); });"
                    + " return ''; }");
            if (ans != null && !ans.toString().isEmpty()) {
                answered = true;
                lastSave = "clicked Save, then Yes on the confirmation dialog (\"" + ans + "\")";
            }
            boolean got = Boolean.TRUE.equals(page.evaluate(
                    "() => (window.__lreToasts||[]).some(a=>/succe|saved|updated|uploaded|complete|please|error|fail|invalid|required/i.test(a))"));
            if (got && answered) break;
            page.waitForTimeout(600);
        }
        if (!answered) lastSave = "clicked Save, but no confirmation dialog with a Yes button appeared";
        waitForAngular(1000);
        Object r = page.evaluate("() => { const a=window.__lreToasts||[];"
                + " return a.find(x=>/succe|saved|updated|uploaded|complete/i.test(x))"
                + "   || a.find(x=>/please|error|fail|invalid|required/i.test(x)) || a[0] || ''; }");
        lastSaveToast = r == null ? "" : r.toString().replaceAll("^[^A-Za-z0-9]+", "")
                .replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        System.out.println("LabResultEntry: save -> " + lastSave + "; toast: \"" + lastSaveToast + "\"");
        return lastSaveToast;
    }

    /** The save answered with a success message (and not a validation/error one). */
    public boolean saveSucceeded() {
        String t = lastSaveToast == null ? "" : lastSaveToast.toLowerCase();
        return t.matches(".*(succe|saved|updated|uploaded|complete).*")
                && !t.matches(".*(please|error|fail|invalid|not\\s*saved).*");
    }

    // ---- Result Entry Report ----------------------------------------------

    /**
     * Click <b>Result Entry Report</b> ({@code PrintPendingResultEntry}) and judge what it produced.
     *
     * <p>The report is a PDF shown in Chrome's viewer, so the FILE is judged rather than the page around it —
     * a screenshot of that viewer is blank however good the report is. The button reports on the searched
     * date range ({@code frmPendingPathoResultEntry.aspx?fromDate=..&toDate=..}), opened with
     * {@code window.open(.., '_blank')}.</p>
     */
    public String clickReportAndVerify() {
        int tabsBefore = page.context().pages().size();
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/PrintPendingResultEntry/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(3000);
        com.kpj.pages.PdfReport.Result r = com.kpj.pages.PdfReport.capture(page, tabsBefore, 15000);
        reportBlank = r.blank;
        lastReport = r.diagnostics;
        // The report step shows the first page of the PDF itself — a screenshot of Chrome's PDF viewer is blank.
        reportPng = r.png;
        if (reportPng == null || reportPng.length == 0) {
            try { reportPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(10000)); } catch (Exception ignore) { }
        }
        System.out.println("LabResultEntry: report -> " + lastReport);
        return lastReport;
    }
}
