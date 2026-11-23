package com.kpj.pages.Inventory_page.Transfer_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Page object — Inventory &gt; Transfer &gt; <b>Return Item</b> (list screen, route {@code #/ReturnItemList}).
 *
 * <p>Everything here was taken from the live hisdemo screen (inspected 10/11/2026), not assumed:</p>
 * <ul>
 *   <li>Filters: <b>From Store</b> (select2, ng-model {@code ReturnItem.FromStore}, defaults to the user's store),
 *       <b>To Store</b> (select2, {@code ReturnItem.ToStore}), <b>From Date</b> {@code #txtFromDate} and
 *       <b>To Date</b> {@code #txtToDate} (masked dd/MM/yyyy, both default to today).</li>
 *   <li>Buttons: <b>Search</b> ({@code getissuereturnitemslistSearch()}), <b>New</b> ({@code AddReturnItem()} -&gt; #/add-ReturnItem),
 *       <b>Print</b> ({@code printReport()}) and <b>Patient Print</b> ({@code PtntprintReport()}).</li>
 *   <li>Master ui-grid {@code gridOptions}: Return Number, Return Date, From Store Name, To Store Name, Total Items,
 *       Total Selling Price, Total Amount, Return By — client-side paging (5/10/25/50/75), sorting and per-column filters.
 *       Clicking a row loads the item detail grid {@code gridOptions1}: Item Code, Item Name, Batch Code, Expiry Date,
 *       Transfer Quantity, Return Quantity, Unit Purchase Rate, Total Amount, Unit Selling Price, Total Selling Price.</li>
 *   <li>APIs: {@code POST /api/ReturnItem/Fetch} ExecFlag {@code GetReturnItemSearch} (search) and {@code ReturnItemList} (row detail);
 *       {@code POST /api/CommonDropdown/Fetch} (store lists), {@code /api/CommonDropdown/FetchHeader?TObject=ReturnItemList},
 *       {@code /api/RecalledItem/fetch}.</li>
 * </ul>
 * READ-ONLY: this class never clicks New, never saves, and never uses the grid menu's export entries.
 */
public class ReturnItem extends BasePage {

    public static final String ROUTE = "#/ReturnItemList";
    /** URL predicate for the screen (a regex, see waitLoaded). */
    public static final java.util.regex.Pattern ROUTE_URL = java.util.regex.Pattern.compile(".*#/ReturnItemList([?/].*)?$");
    /** Poll every 250 ms instead of on animation frames: requestAnimationFrame stops firing when the browser window is
     *  minimised or covered, which stalls a default waitForFunction although the page is ready. */
    private static Page.WaitForFunctionOptions poll(int timeoutMs) { return new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(250); }
    public static final String FROM_STORE = "ReturnItem.FromStore";
    public static final String TO_STORE = "ReturnItem.ToStore";
    public static final String FROM_DATE = "#txtFromDate";
    public static final String TO_DATE = "#txtToDate";
    public static final String SEARCH_BTN = "button[ng-click='getissuereturnitemslistSearch();']";
    public static final String NEW_BTN = "button[ng-click='AddReturnItem()']";
    public static final String PRINT_BTN = "button[ng-click='printReport()']";
    public static final String PATIENT_PRINT_BTN = "button[ng-click='PtntprintReport()']";
    public static final String MASTER_GRID = ".ui-grid[ui-grid='gridOptions']";
    public static final String DETAIL_GRID = ".ui-grid[ui-grid='gridOptions1']";
    public static final String FETCH_API = "/api/ReturnItem/Fetch";

    public static final List<String> MASTER_HEADERS = Arrays.asList("Return Number", "Return Date", "From Store Name", "To Store Name",
            "Total Items", "Total Selling Price", "Total Amount", "Return By");
    public static final List<String> DETAIL_HEADERS = Arrays.asList("Item Code", "Item Name", "Batch Code", "Expiry Date",
            "Transfer Quantity", "Return Quantity", "Unit Purchase Rate", "Total Amount", "Unit Selling Price", "Total Selling Price");

    private static final String NORM = "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();";
    /** The screen's controller scope (the Search button sits directly in it). */
    private static final String SCOPE = "const sc=angular.element(document.querySelector(\"" + "button[ng-click='getissuereturnitemslistSearch();']" + "\")).scope();";

    public ReturnItem(Page page) { super(page); }

    // ---------------------------------------------------------------- navigation

    /** Inventory &gt; Transfer &gt; Return Item through the sidebar, as a user does. @return "" on success, else why it failed */
    public String navigateViaMenu() {
        if (!expandMenuEntry(null, "^Inventory$")) return "the sidebar entry \"Inventory\" was not found";
        // inside Inventory the "Transfer" GROUP (href="#") comes before its own "Transfer" leaf (#/IssueToStoreList)
        if (!expandMenuEntry("^Inventory$", "^Transfer$")) return "the Inventory > Transfer group did not expand";
        Locator link = page.locator("a[href='" + ROUTE + "']");
        try {
            link.first().click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            return "the \"Return Item\" menu link could not be clicked (" + firstLine(e) + ")";
        }
        return waitLoaded(20000) ? "" : "the Return Item screen did not finish loading (URL " + page.url() + ")";
    }

    /** Direct route — used only to recover so later checks can still run when the menu step fails. */
    public boolean openViaRoute(String base) {
        page.navigate(base + "/" + ROUTE);
        return waitLoaded(20000);
    }

    /** Re-open the screen through the menu to get a fresh controller (resets filters, grid and selection). */
    public String reload() {
        // navigating to the same hash is a no-op in ui-router, so step away first
        try { page.evaluate("() => { location.hash = '#/LandingPage'; }"); } catch (Exception ignore) { }
        waitForAngular(1200);
        return navigateViaMenu();
    }

    public boolean waitLoaded(int timeoutMs) {
        try {
            // NOT the glob "**#/ReturnItemList": Playwright 1.40 Java compiles a leading "**" to "([^/]*)" (one path segment), so that glob
            // can never match "https://host/#/ReturnItemList" and every wait timed out although the screen was loaded.
            page.waitForURL(ROUTE_URL, new Page.WaitForURLOptions().setTimeout(timeoutMs));
            page.locator(SEARCH_BTN).waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            page.waitForFunction("() => { const s=document.querySelector(\"[ng-model='ReturnItem.FromStore']\"); return s && s.options.length>1; }",
                    null, poll(timeoutMs));
            waitForAngular(800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean onScreen() { return page.url().contains(ROUTE); }

    /** Document title + the banner caption shown at the top left (e.g. "RETURN ITEM"). */
    public String[] titles() {
        Object r = page.evaluate("() => {" + NORM + " const cap=[...document.querySelectorAll('body *')].filter(e=>e.children.length===0 && e.offsetParent!==null)"
                + " .map(e=>norm(e.textContent)).find(t=>/^return item$/i.test(t)); return [document.title||'', cap||'']; }");
        List<?> l = (List<?>) r;
        return new String[]{String.valueOf(l.get(0)), String.valueOf(l.get(1))};
    }

    /** Visible / enabled state of every control on the screen, in screen order. Value = "visible", "visible, disabled" or "MISSING". */
    public Map<String, String> controls() {
        Map<String, String> sel = new LinkedHashMap<>();
        sel.put("From Store", "[ng-model='" + FROM_STORE + "']");
        sel.put("To Store", "[ng-model='" + TO_STORE + "']");
        sel.put("From Date", FROM_DATE);
        sel.put("To Date", TO_DATE);
        sel.put("Search", SEARCH_BTN);
        sel.put("Return grid", MASTER_GRID);
        sel.put("Return grid pager", MASTER_GRID + " .ui-grid-pager-panel");
        sel.put("Item detail grid", DETAIL_GRID);
        sel.put("New", NEW_BTN);
        sel.put("Print", PRINT_BTN);
        sel.put("Patient Print", PATIENT_PRINT_BTN);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : sel.entrySet()) {
            Object r = page.evaluate("(s) => { let el=document.querySelector(s); if(!el) return 'MISSING';"
                    + " if(el.tagName==='SELECT' && el.previousElementSibling && el.previousElementSibling.classList.contains('select2-container')) el=el.previousElementSibling;"
                    + " const vis=el.offsetParent!==null && el.getBoundingClientRect().width>0; return (vis?'visible':'HIDDEN')+(el.disabled?', disabled':''); }", e.getValue());
            out.put(e.getKey(), String.valueOf(r));
        }
        return out;
    }

    /** The colour legend under the grid ("Psychotropic Drug", "Special Order"). */
    public List<String> legend() {
        return strings(page.evaluate("() => {" + NORM + " return [...document.querySelectorAll('label .glyphicon-stop')].map(s=>norm(s.parentElement.textContent)).filter(t=>t); }"));
    }

    // ---------------------------------------------------------------- dropdowns (select2)

    public List<String> storeNames(String ngModel) { return Select2Pick.names(page, ngModel); }
    public String chosen(String ngModel) { return Select2Pick.chosen(page, ngModel); }
    public String model(String ngModel) { return Select2Pick.model(page, ngModel); }

    /** Pick a store from the dropdown list as a user does. @return "" on success */
    public String pickStore(String ngModel, String name) { return Select2Pick.pick(page, ngModel, name); }

    /** Open the select2 box of {@code ngModel}; @return number of entries in its open list (-1 = did not open). */
    public int openDropdown(String ngModel) {
        tagBox(ngModel);
        try {
            page.locator("#__riBox").click(new Locator.ClickOptions().setTimeout(6000));
            page.waitForFunction("() => [...document.querySelectorAll('.select2-drop-active li.select2-result-selectable')].length>0", null, poll(6000));
        } catch (Exception e) {
            return -1;
        } finally { untag("__riBox"); }
        Object n = page.evaluate("() => document.querySelectorAll('.select2-drop-active li.select2-result-selectable').length");
        return n instanceof Number ? ((Number) n).intValue() : -1;
    }

    /** Type into the open dropdown's search box. @return the entries the list then shows */
    public List<String> typeInDropdownSearch(String text) {
        Locator s = page.locator(".select2-drop-active .select2-search input");
        if (s.count() == 0 || !s.first().isVisible()) return null;               // no search box on this dropdown
        s.first().fill("");
        s.first().pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(60));
        waitForAngular(700);
        return strings(page.evaluate("() => {" + NORM + " return [...document.querySelectorAll('.select2-drop-active li.select2-result-selectable')].map(l=>norm(l.textContent)); }"));
    }

    public void closeDropdown() {
        page.evaluate("() => { try { jQuery('select').filter((i,e)=>jQuery(e).data('select2')).select2('close'); } catch(e){} }");
        waitForAngular(300);
    }

    /** Put the store dropdown back on "--Select--" from its own list (a user's way of clearing it). @return the model afterwards ("" = empty) */
    public String clearStore(String ngModel) {
        if (openDropdown(ngModel) < 0) return "the list did not open";
        Object ok = page.evaluate("() => {" + NORM + " const li=[...document.querySelectorAll('.select2-drop-active li.select2-result-selectable')]"
                + ".find(x=>/^-*\\s*select\\s*-*$/i.test(norm(x.textContent))); if(!li) return false; li.id='__riOpt'; return true; }");
        if (Boolean.TRUE.equals(ok)) {
            try { page.locator("#__riOpt").click(new Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
            untag("__riOpt");
        } else closeDropdown();
        waitForAngular(400);
        return model(ngModel);
    }

    // ---------------------------------------------------------------- dates

    /** Type a date into the masked box (select-all + type), then close the calendar. @return {box value, model value} */
    public String[] typeDate(String selector, String value) {
        Locator box = page.locator(selector);
        box.click(new Locator.ClickOptions().setTimeout(6000));
        box.press("Control+A");
        box.press("Backspace");
        if (!value.isEmpty()) box.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(40));
        dismissDatePicker();
        waitForAngular(300);
        return new String[]{box.inputValue(), dateModel(selector)};
    }

    public String dateModel(String selector) {
        String path = FROM_DATE.equals(selector) ? "FromDate" : "ToDate";
        Object v = page.evaluate("(p) => {" + SCOPE + " const v=sc && sc.ReturnItem ? sc.ReturnItem[p] : null; return v==null?'':String(v); }", path);
        return v == null ? "" : v.toString();
    }

    public String fieldValue(String selector) { return page.locator(selector).inputValue(); }

    // ---------------------------------------------------------------- search

    /** Outcome of one click on Search. */
    public static class SearchResult {
        public String request = "";
        public int status = -1;
        public long ms = -1;
        public int rows = -1;
        public String error = "";
        public boolean fired() { return status >= 0; }
        @Override public String toString() {
            return fired() ? "HTTP " + status + ", " + rows + " row(s), " + ms + " ms" : "no search request (" + error + ")";
        }
    }

    /** Click Search and wait for the GetReturnItemSearch response; the grid row count is read after it renders. */
    public SearchResult search() {
        SearchResult r = new SearchResult();
        long t0 = System.currentTimeMillis();
        try {
            Response resp = page.waitForResponse(x -> x.url().contains(FETCH_API)
                            && String.valueOf(x.request().postData()).contains("GetReturnItemSearch"),
                    new Page.WaitForResponseOptions().setTimeout(30000),
                    () -> page.locator(SEARCH_BTN).click(new Locator.ClickOptions().setTimeout(6000)));
            r.ms = System.currentTimeMillis() - t0;
            r.status = resp.status();
            r.request = String.valueOf(resp.request().postData());
        } catch (Exception e) {
            r.error = firstLine(e);
        }
        waitForAngular(900);
        r.rows = masterRows();
        return r;
    }

    // ---------------------------------------------------------------- grids

    /** Rows bound to the master grid (all pages). */
    public int masterRows() { return intOf(page.evaluate("() => {" + SCOPE + " return sc && sc.gridOptions && sc.gridOptions.data ? sc.gridOptions.data.length : -1; }")); }
    public int detailRows() { return intOf(page.evaluate("() => {" + SCOPE + " return sc && sc.gridOptions1 && sc.gridOptions1.data ? sc.gridOptions1.data.length : -1; }")); }

    /** Rows the master grid currently DRAWS (the current page after its column filters). */
    public int renderedRows(String grid) {
        return intOf(page.evaluate("(g) => document.querySelectorAll(g+' .ui-grid-render-container-body .ui-grid-viewport .ui-grid-row').length", grid));
    }

    public List<String> headers(String grid) {
        return strings(page.evaluate("(g) => {" + NORM + " return [...document.querySelectorAll(g+' .ui-grid-header-cell .ui-grid-header-cell-label')].map(e=>norm(e.textContent)).filter(t=>t); }", grid));
    }

    /** One field of every row the grid currently draws, in display order (read from the grid's own row cache). */
    public List<String> renderedField(String grid, String field) {
        return strings(page.evaluate("([g,f]) => { const el=document.querySelector(g); if(!el) return [];"
                + " const gr=angular.element(el).isolateScope().grid; return gr.renderContainers.body.renderedRows.map(r=>r.entity[f]==null?'':String(r.entity[f])); }",
                Arrays.asList(grid, field)));
    }

    /** All rows of the master grid as "returnnumber | returndate | from | to | items | mrp | amount | by". */
    public List<String> masterRecords() {
        return strings(page.evaluate("() => {" + SCOPE + " return (sc.gridOptions.data||[]).map(r=>[r.returnnumber,r.returndate,r.FromStoreName,r.ToStoreName,r.totalitems,r.totalmrp,r.totalamount,(r.ReturnBy||'').trim()].join(' | ')); }"));
    }

    /** Field values of every master row (all pages). */
    public List<String> masterField(String field) {
        return strings(page.evaluate("(f) => {" + SCOPE + " return (sc.gridOptions.data||[]).map(r=>r[f]==null?'':String(r[f])); }", field));
    }

    /** Text shown by the grid when it has no rows ("" when it shows nothing at all). */
    public String emptyGridMessage(String grid) {
        Object r = page.evaluate("(g) => {" + NORM + " const el=document.querySelector(g); if(!el) return '';"
                + " const m=el.querySelector('.ui-grid-no-row-overlay, .watermark, .no-data, .ui-grid-empty'); if(m && m.offsetParent!==null) return norm(m.textContent);"
                + " const vp=el.querySelector('.ui-grid-render-container-body .ui-grid-viewport'); return vp? norm(vp.innerText) : ''; }", grid);
        return r == null ? "" : r.toString();
    }

    /** Type into the per-column filter box under header {@code colIndex} (0-based, data columns only). */
    public void filterColumn(String grid, int colIndex, String text) {
        Locator f = page.locator(grid + " .ui-grid-header-cell .ui-grid-filter-input").nth(colIndex);
        f.click(new Locator.ClickOptions().setTimeout(5000));
        f.fill("");
        if (!text.isEmpty()) f.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(40));
        waitForAngular(900);
    }

    /** Click a header label to sort. @return the sort direction the grid then reports for that column ("asc", "desc" or "") */
    public String clickHeader(String grid, String header) {
        page.locator(grid + " .ui-grid-header-cell .ui-grid-header-cell-label").filter(new Locator.FilterOptions().setHasText(header)).first()
                .click(new Locator.ClickOptions().setTimeout(6000));
        waitForAngular(700);
        Object d = page.evaluate("([g,h]) => {" + NORM + " const el=document.querySelector(g); const gr=angular.element(el).isolateScope().grid;"
                + " const c=gr.columns.find(c=>norm(c.displayName)===h); return c && c.sort && c.sort.direction ? c.sort.direction : ''; }", Arrays.asList(grid, header));
        return d == null ? "" : d.toString();
    }

    /** Pager state of a grid: {current page, total pages, page size}. */
    public int[] pager(String grid) {
        Object r = page.evaluate("(g) => { const el=document.querySelector(g); const gr=angular.element(el).isolateScope().grid;"
                + " return [gr.options.paginationCurrentPage||1, Math.max(1, gr.api.pagination.getTotalPages()), gr.options.paginationPageSize]; }", grid);
        List<?> l = (List<?>) r;
        return new int[]{intOf(l.get(0)), intOf(l.get(1)), intOf(l.get(2))};
    }

    /** Click a pager button: "next", "previous", "first" or "last". @return false when it is disabled */
    public boolean pagerClick(String grid, String which) {
        Locator b = page.locator(grid + " .ui-grid-pager-" + which);
        if (b.count() == 0 || b.first().isDisabled()) return false;
        b.first().click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(600);
        return true;
    }

    /** Pick a page size from the pager's own dropdown. @return the page size the grid then uses */
    public int setPageSize(String grid, int size) {
        Object tagged = page.evaluate("(g) => { const el=document.querySelector(g+' .ui-grid-pager-row-count-picker .select2-choice, '+g+' .ui-grid-pager-row-count-picker select');"
                + " if(!el) return false; el.id='__riSize'; return el.tagName; }", grid);
        if (Boolean.FALSE.equals(tagged)) return -1;
        try {
            if ("SELECT".equals(String.valueOf(tagged))) page.locator("#__riSize").selectOption(String.valueOf(size));
            else {
                page.locator("#__riSize").click(new Locator.ClickOptions().setTimeout(5000));
                page.locator(".select2-drop-active li.select2-result-selectable").filter(new Locator.FilterOptions().setHasText(String.valueOf(size)))
                        .first().click(new Locator.ClickOptions().setTimeout(5000));
            }
        } catch (Exception e) {
            closeDropdown();
        } finally { untag("__riSize"); }
        waitForAngular(700);
        return pager(grid)[2];
    }

    /** Horizontal scroll check: {scrollWidth, clientWidth, scrollLeft after scrolling, header scrollLeft after scrolling}. */
    public int[] horizontalScroll(String grid) {
        Object r = page.evaluate("async (g) => { const vp=document.querySelector(g+' .ui-grid-render-container-body .ui-grid-viewport'); if(!vp) return [-1,-1,-1,-1];"
                + " const hd=document.querySelector(g+' .ui-grid-render-container-body .ui-grid-header-viewport');"
                + " vp.scrollLeft=vp.scrollWidth; vp.dispatchEvent(new Event('scroll')); await new Promise(r=>setTimeout(r,500));"
                + " const out=[vp.scrollWidth, vp.clientWidth, Math.round(vp.scrollLeft), hd?Math.round(hd.scrollLeft):-1];"
                + " vp.scrollLeft=0; vp.dispatchEvent(new Event('scroll')); return out; }", grid);
        List<?> l = (List<?>) r;
        return new int[]{intOf(l.get(0)), intOf(l.get(1)), intOf(l.get(2)), intOf(l.get(3))};
    }

    /** Click a master row (drawn row {@code index}) and wait for its item details (ExecFlag ReturnItemList). @return HTTP status, -1 when nothing was fetched */
    public int clickMasterRow(int index) {
        try {
            Response r = page.waitForResponse(x -> x.url().contains(FETCH_API) && String.valueOf(x.request().postData()).contains("\"ReturnItemList\""),
                    new Page.WaitForResponseOptions().setTimeout(20000),
                    () -> page.locator(MASTER_GRID + " .ui-grid-render-container-body .ui-grid-viewport .ui-grid-row").nth(index)
                            .locator(".ui-grid-cell").nth(1).click(new Locator.ClickOptions().setTimeout(6000)));
            waitForAngular(800);
            return r.status();
        } catch (Exception e) {
            return -1;
        }
    }

    /** The return number of the record the screen holds as selected ("" when none). */
    public String selectedReturn() {
        Object r = page.evaluate("() => {" + SCOPE + " return sc.Selectedrow ? String(sc.Selectedrow.returnnumber||sc.Selectedrow.id) : ''; }");
        return r == null ? "" : r.toString();
    }

    // ---------------------------------------------------------------- print (report tabs are GET + PDF: read-only)

    /** What a Print click did. */
    public static class PrintResult {
        public String url = "";
        public int status = -1;
        public String contentType = "";
        public String dialog = "";
        public String error = "";
    }

    /**
     * Click a print button. With a selected record the app opens the report in a new tab: its URL is captured, fetched once with the
     * session's cookies to read the HTTP status / content type, and the tab is closed. Without a selection the app shows a confirm
     * box — its text is captured and it is dismissed with Cancel (both of its buttons only close it).
     */
    public PrintResult print(String buttonSelector) {
        PrintResult pr = new PrintResult();
        Page popup = null;
        try {
            popup = page.context().waitForPage(new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(8000),
                    () -> page.locator(buttonSelector).click(new Locator.ClickOptions().setTimeout(6000)));
        } catch (Exception noTab) {
            // no new tab: expect the "please select" confirm box
            waitForAngular(800);
            pr.dialog = confirmText();
            if (pr.dialog.isEmpty()) pr.error = "no report tab and no message appeared";
            dismissConfirm();
            return pr;
        }
        try {
            try { popup.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000)); } catch (Exception ignore) { }
            pr.url = popup.url();
            if (pr.url.startsWith("about:blank")) { popup.waitForTimeout(2000); pr.url = popup.url(); }
            APIResponse a = page.request().get(pr.url, com.microsoft.playwright.options.RequestOptions.create().setTimeout(60000));
            pr.status = a.status();
            pr.contentType = String.valueOf(a.headers().get("content-type"));
            if (!pr.contentType.contains("pdf")) {
                String body = a.text();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(server error in[^<]{0,120}|logon failed[^<]{0,120}|exception[^<]{0,160})").matcher(body);
                if (m.find()) pr.error = m.group(1).trim();
            }
            a.dispose();
        } catch (Exception e) {
            pr.error = firstLine(e);
        } finally {
            try { popup.close(); } catch (Exception ignore) { }
            try { page.bringToFront(); } catch (Exception ignore) { }
        }
        return pr;
    }

    private static final String CONFIRM_BOXES = "const shown=e=>{const r=e.getBoundingClientRect(); const cs=getComputedStyle(e); return r.width>0 && r.height>0 && cs.visibility!=='hidden' && cs.display!=='none';};"
            + " const boxes=()=>[...document.querySelectorAll('.ng-confirm-box, .jconfirm-box, [class*=\"confirm-box\"]')].filter(shown);";

    /** Text of the confirm box ($ngConfirm) on screen, "" when none. Waits up to 3 s for its open animation. */
    public String confirmText() {
        try { page.waitForFunction("() => {" + CONFIRM_BOXES + " return boxes().length>0; }", null, poll(3000)); } catch (Exception none) { return ""; }
        Object d = page.evaluate("() => {" + NORM + CONFIRM_BOXES + " const b=boxes().pop(); return b? norm(b.innerText) : ''; }");
        return d == null ? "" : d.toString();
    }

    /** Close every open confirm box with its CANCEL button (never its red OK); both of this screen's buttons only close it. @return how many were closed */
    public int dismissConfirm() {
        int closed = 0;
        for (int i = 0; i < 3; i++) {
            Object r = page.evaluate("() => {" + NORM + CONFIRM_BOXES + " const box=boxes().pop(); if(!box) return false;"
                    + " const b=[...box.querySelectorAll('button')].find(x=>/^cancel$/i.test(norm(x.textContent))) || box.querySelector('.ng-confirm-closeIcon, .jconfirm-closeIcon');"
                    + " if(!b) return false; b.click(); return true; }");
            if (!Boolean.TRUE.equals(r)) break;
            closed++;
            try { page.waitForFunction("() => {" + CONFIRM_BOXES + " return boxes().length===0; }", null, poll(4000)); } catch (Exception ignore) { }
        }
        return closed;
    }

    /** Clear anything that would sit over the screen and swallow clicks: confirm boxes, an open select2 list, a calendar. */
    public String closeOverlays() {
        StringBuilder sb = new StringBuilder();
        int c = dismissConfirm();
        if (c > 0) sb.append(c).append(" confirm box(es) closed; ");
        Object open = page.evaluate("() => document.querySelectorAll('.select2-drop-active').length");
        if (open instanceof Number && ((Number) open).intValue() > 0) { closeDropdown(); sb.append("dropdown list closed; "); }
        return sb.toString().trim();
    }

    /** Grid menu (hamburger) entries — opened and closed again; nothing in it is clicked. */
    public List<String> gridMenuEntries(String grid) {
        Locator btn = page.locator(grid + " .ui-grid-menu-button");
        if (btn.count() == 0) return null;
        btn.first().click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(500);
        List<String> items = strings(page.evaluate("(g) => {" + NORM + " return [...document.querySelectorAll(g+' .ui-grid-menu-items li button, '+g+' .ui-grid-menu-item')]"
                + ".filter(e=>e.offsetParent!==null).map(e=>norm(e.textContent)).filter(t=>t); }", grid));
        btn.first().click(new Locator.ClickOptions().setTimeout(5000));   // close it again
        waitForAngular(300);
        return items;
    }

    // ---------------------------------------------------------------- helpers

    private void tagBox(String ngModel) {
        page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return; const c=e.previousElementSibling;"
                + " const b=c && (c.querySelector('.select2-choice')||c); if(b) b.id='__riBox'; }", ngModel);
    }

    private void untag(String id) {
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.removeAttribute('id'); }", id); } catch (Exception ignore) { }
    }

    private static List<String> strings(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    private static int intOf(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return -1; }
    }

    public static String firstLine(Throwable e) {
        String m = String.valueOf(e.getMessage());
        return m.split("\\R")[0];
    }
}
