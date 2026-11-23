package com.kpj.pages.Inventory_page.Others_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Page object — Inventory &gt; Others &gt; <b>Stock Adjustment</b> (list screen, route {@code #/StockAdjustmentList}).
 *
 * <p>Everything here was taken from the live hisdemo screen and its own controller
 * ({@code App/Inventory/controller/StockAdjustmentListController.js}, inspected 10/11/2026), not assumed:</p>
 * <ul>
 *   <li>Filters: <b>From Date</b> {@code #txtFromDate} / <b>To Date</b> {@code #txtToDate} (720kb date pickers, dd/MM/yyyy, both default to
 *       today; keyboard entry is NOT accepted — only the calendar sets a date, Backspace clears it), <b>Store</b> (select2, ng-model
 *       {@code StockAdjustment.SearchStoreID}, defaults to the user's default store, has a search box), <b>Approval Level</b> (select2,
 *       {@code StockAdjustment.LevelId}, disabled, fixed to 1 on this screen) and <b>status</b> radios Pending / Approved / ALL
 *       ({@code #radios-0/1/2}, {@code StockAdjustment.status}, none checked by default).</li>
 *   <li>Buttons: <b>Search</b> ({@code getStockAdjustmentSearchList()}), <b>Print</b> ({@code printReport()}) and <b>New</b>
 *       ({@code AddStockAdjustment()} -&gt; #/add-StockAdjustment, a {@code type=submit} button — so Enter is never pressed in this form).</li>
 *   <li>Master ui-grid {@code gridOptions} "List of Stock Management": Stock Adjustment No, Date, Store Name, Approved (checkbox), Level, Remark;
 *       client paging 25/50/75/100/125, sorting, per-column filters, grid menu (Clear all filters, CSV/PDF/Excel export, column toggles).
 *       Selecting a row with ui-grid's own selection tick loads the detail grid {@code gridOptionsone} "Stock Management Details":
 *       Item Code, Item Name, Batch Code, Available Quantity, Adjustment Quantity, Adjustment Type, Reason, Balance After Adjustment, WAC,
 *       Tax Percent, Tax Amount, Net Amount.</li>
 *   <li>APIs: {@code POST /api/StockAdjustment/fetch} ExecFlag {@code GetStockAdjustmentSearchList} (search) and {@code GetPhysicalStockdetails}
 *       (row details); on load {@code /api/CommonDropdown/FetchHeader?TObject=StockAdjustmentList}, {@code /api/CommonDropdown/Fetch}
 *       (ExecFlag AUTHRIZATION) and {@code /api/PurchaseOrder/Fetch} (ExecFlag FillStore). Print opens
 *       {@code /SQLReport/InventoryReport/frmStockAdjustment.aspx?ID=<stockadjid>&LocationID=<locationid>} (GET, PDF).</li>
 * </ul>
 *
 * <p><b>READ-ONLY.</b> The <b>Approved</b> checkbox in the master grid is a WRITE: its {@code changeisapproved()} posts
 * {@code /api/StockAdjustment/IUDApproveStockAdjustment} at once. This class never touches it (rows are selected with the row-header tick),
 * never clicks New, never uses the grid menu's export entries, and closes confirm boxes with Cancel only.</p>
 *
 * <p>Note on select2: closed drop-downs keep the {@code select2-drop-active} class on this screen, so every list lookup here is restricted to
 * the drop-down that is actually visible.</p>
 */
public class StockAdjustment extends BasePage {

    public static final String ROUTE = "#/StockAdjustmentList";
    public static final java.util.regex.Pattern ROUTE_URL = java.util.regex.Pattern.compile(".*#/StockAdjustmentList([?/].*)?$");
    public static final String TITLE = "Stock Adjustment";

    public static final String FROM_DATE = "#txtFromDate";
    public static final String TO_DATE = "#txtToDate";
    public static final String STORE = "StockAdjustment.SearchStoreID";
    public static final String LEVEL = "StockAdjustment.LevelId";
    public static final String STATUS_PENDING = "#radios-0";
    public static final String STATUS_APPROVED = "#radios-1";
    public static final String STATUS_ALL = "#radios-2";
    public static final String SEARCH_BTN = "button[ng-click='getStockAdjustmentSearchList()']";
    public static final String PRINT_BTN = "button[ng-click='printReport()']";
    public static final String NEW_BTN = "button[ng-click='AddStockAdjustment()']";
    public static final String MASTER_GRID = ".ui-grid[ui-grid='gridOptions']";
    public static final String DETAIL_GRID = ".ui-grid[ui-grid='gridOptionsone']";
    public static final String FETCH_API = "/api/stockadjustment/fetch";          // compared lower-case: the app calls "api/StockAdjustment/fetch"
    public static final String SEARCH_FLAG = "GetStockAdjustmentSearchList";
    public static final String DETAIL_FLAG = "GetPhysicalStockdetails";
    public static final String REPORT_PAGE = "frmStockAdjustment.aspx";

    public static final List<String> MASTER_HEADERS = Arrays.asList("Stock Adjustment No", "Date", "Store Name", "Approved", "Level", "Remark");
    public static final List<String> DETAIL_HEADERS = Arrays.asList("Item Code", "Item Name", "Batch Code", "Available Quantity", "Adjustment Quantity",
            "Adjustment Type", "Reason", "Balance After Adjustment", "WAC", "Tax Percent", "Tax Amount", "Net Amount");

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String NORM = "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();";
    /** The screen's controller scope (the Search button sits directly in it). */
    private static final String SCOPE = "const sc=angular.element(document.querySelector(\"" + SEARCH_BTN + "\")).scope();";
    /** The select2 drop-down that is really open (closed ones keep the -active class here). */
    private static final String OPEN_DROP = "const drop=[...document.querySelectorAll('.select2-drop-active')].find(d=>{const r=d.getBoundingClientRect(); return r.width>0 && r.height>0;});";
    private static final String OPEN_CAL = "._720kb-datepicker-calendar._720kb-datepicker-open";

    /** Poll every 250 ms instead of on animation frames (rAF stops when the window is minimised or covered). */
    private static Page.WaitForFunctionOptions poll(int timeoutMs) { return new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(250); }

    public StockAdjustment(Page page) { super(page); }

    // ---------------------------------------------------------------- navigation

    /** Inventory &gt; Others &gt; Stock Adjustment through the sidebar, as a user does. @return "" on success, else why it failed */
    public String navigateViaMenu() {
        if (!expandMenuEntry(null, "^Inventory$")) return "the sidebar entry \"Inventory\" was not found";
        if (!expandMenuEntry("^Inventory$", "^Others$")) return "the Inventory > Others group did not expand";
        Locator link = page.locator("a[href='" + ROUTE + "']");
        try {
            link.first().click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            return "the \"Stock Adjustment\" menu link could not be clicked (" + firstLine(e) + ")";
        }
        return waitLoaded(20000) ? "" : "the Stock Adjustment screen did not finish loading (URL " + page.url() + ")";
    }

    /** The menu entries listed under Inventory &gt; Others (text -&gt; href), for the report. */
    public List<String> othersMenu() {
        return strings(page.evaluate("() => {" + NORM + " const inv=[...document.querySelectorAll('a')].find(x=>/^Inventory$/i.test(norm(x.textContent)));"
                + " const li=inv&&inv.closest('li'); const o=li&&[...li.querySelectorAll('a')].find(x=>/^Others$/i.test(norm(x.textContent)));"
                + " const oli=o&&o.closest('li'); return oli? [...oli.querySelectorAll('a')].filter(a=>a!==o).map(a=>norm(a.textContent)+' -> '+a.getAttribute('href')) : []; }"));
    }

    /** Direct route — only to recover so later checks can still run when the menu step fails. */
    public boolean openViaRoute(String base) {
        page.navigate(base + "/" + ROUTE);
        return waitLoaded(20000);
    }

    /** Leave the screen and re-open it from the menu (fresh controller: default filters, empty grids, no selection). */
    public String reload() {
        try { page.evaluate("() => { location.hash = '#/LandingPage'; }"); } catch (Exception ignore) { }
        waitForAngular(1200);
        return navigateViaMenu();
    }

    public boolean waitLoaded(int timeoutMs) {
        try {
            page.waitForURL(ROUTE_URL, new Page.WaitForURLOptions().setTimeout(timeoutMs));
            page.locator(SEARCH_BTN).waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
            // the store list (FillStore) and the approval level (AUTHRIZATION) arrive asynchronously after the template
            page.waitForFunction("() => { const s=document.querySelector(\"[ng-model='" + STORE + "']\"); const l=document.querySelector(\"[ng-model='" + LEVEL + "']\");"
                    + " return s && s.options.length>1 && l && l.options.length>1; }", null, poll(timeoutMs));
            waitForAngular(800);
            hookAlerts();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean onScreen() { return page.url().contains(ROUTE); }

    /** {document title, panel headings joined by " | "}. */
    public String[] titles() {
        List<String> l = strings(page.evaluate("() => {" + NORM + " const h=[...document.querySelectorAll('.panel-heading')].filter(e=>e.offsetParent!==null).map(e=>norm(e.textContent));"
                + " return [document.title||'', h.join(' | ')]; }"));
        return new String[]{l.size() > 0 ? l.get(0) : "", l.size() > 1 ? l.get(1) : ""};
    }

    /** Visible field labels of the filter area, in screen order. */
    public List<String> labels() {
        return strings(page.evaluate("() => {" + NORM + " return [...document.querySelectorAll('#StockAdjustmentList label')].filter(l=>l.offsetParent!==null)"
                + ".map(l=>norm(l.textContent)).filter(t=>t && t.length<40); }"));
    }

    /** Visible / enabled state of every control, in screen order. Value = "visible", "visible, disabled", "HIDDEN" or "MISSING". */
    public Map<String, String> controls() {
        Map<String, String> sel = new LinkedHashMap<>();
        sel.put("From Date", FROM_DATE);
        sel.put("To Date", TO_DATE);
        sel.put("Store", "[ng-model='" + STORE + "']");
        sel.put("Approval Level", "[ng-model='" + LEVEL + "']");
        sel.put("Status Pending", STATUS_PENDING);
        sel.put("Status Approved", STATUS_APPROVED);
        sel.put("Status ALL", STATUS_ALL);
        sel.put("Search", SEARCH_BTN);
        sel.put("List of Stock Management grid", MASTER_GRID);
        sel.put("List grid pager", MASTER_GRID + " .ui-grid-pager-panel");
        sel.put("Stock Management Details grid", DETAIL_GRID);
        sel.put("Print", PRINT_BTN);
        sel.put("New", NEW_BTN);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : sel.entrySet()) {
            Object r = page.evaluate("(s) => { let el=document.querySelector(s); if(!el) return 'MISSING';"
                    + " let dis=!!el.disabled;"
                    + " if(el.tagName==='SELECT' && el.previousElementSibling && el.previousElementSibling.classList.contains('select2-container')) {"
                    + "   el=el.previousElementSibling; dis = dis || el.classList.contains('select2-container-disabled'); }"
                    + " const vis=el.offsetParent!==null && el.getBoundingClientRect().width>0; return (vis?'visible':'HIDDEN')+(dis?', disabled':''); }", e.getValue());
            out.put(e.getKey(), String.valueOf(r));
        }
        return out;
    }

    /** The colour legend next to Print (e.g. "Special Order"). */
    public List<String> legend() {
        return strings(page.evaluate("() => {" + NORM + " return [...document.querySelectorAll('label .glyphicon-stop')].filter(s=>s.offsetParent!==null).map(s=>norm(s.parentElement.textContent)).filter(t=>t); }"));
    }

    // ---------------------------------------------------------------- dropdowns (select2) and radios

    /** Every option the native select offers, in list order (placeholder included). */
    public List<String> options(String ngModel) {
        return strings(page.evaluate("(n) => {" + NORM + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e? [...e.options].map(o=>norm(o.textContent)) : []; }", ngModel));
    }

    /** Text the select2 box shows now ("" when on the placeholder). */
    public String chosen(String ngModel) {
        Object r = page.evaluate("(n) => {" + NORM + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " const c=e.previousElementSibling; const ch=c && c.querySelector('.select2-chosen'); return ch? norm(ch.textContent) : ''; }", ngModel);
        String s = r == null ? "" : r.toString();
        return s.matches("(?i)-*\\s*select\\s*-*") ? "" : s;
    }

    /** The value the controller holds for a StockAdjustment.* field ("" for null/undefined). */
    public String model(String field) {
        Object v = page.evaluate("(f) => {" + SCOPE + " const v=sc && sc.StockAdjustment ? sc.StockAdjustment[f] : null; return v==null?'':String(v); }", field);
        return v == null ? "" : v.toString();
    }

    /** Open the select2 box of {@code ngModel}. @return number of entries in its open list, -1 = it did not open (e.g. disabled) */
    public int openDropdown(String ngModel) {
        Object tagged = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return false; const c=e.previousElementSibling;"
                + " const b=c && (c.querySelector('.select2-choice')||c); if(!b) return false; b.id='__saBox'; return true; }", ngModel);
        if (!Boolean.TRUE.equals(tagged)) return -1;
        try {
            page.locator("#__saBox").click(new Locator.ClickOptions().setTimeout(6000));
            page.waitForFunction("() => {" + OPEN_DROP + " return !!drop && drop.querySelectorAll('li.select2-result-selectable').length>0; }", null, poll(4000));
        } catch (Exception e) {
            return -1;
        } finally { untag("__saBox"); }
        return intOf(page.evaluate("() => {" + OPEN_DROP + " return drop? drop.querySelectorAll('li.select2-result-selectable').length : -1; }"));
    }

    /** Type into the open drop-down's search box. @return the entries it then lists ({@code null} = no search box) */
    public List<String> typeInDropdownSearch(String text) {
        Locator s = page.locator(".select2-drop-active:visible .select2-search input");
        if (s.count() == 0) return null;
        s.first().fill("");
        s.first().pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(50));
        waitForAngular(600);
        return strings(page.evaluate("() => {" + NORM + OPEN_DROP + " return drop? [...drop.querySelectorAll('li.select2-result-selectable')].map(l=>norm(l.textContent)) : []; }"));
    }

    /** The "no matches" text the open drop-down shows ("" when none). */
    public String dropdownNoMatchText() {
        Object r = page.evaluate("() => {" + NORM + OPEN_DROP + " const m=drop && drop.querySelector('.select2-no-results'); return m? norm(m.textContent) : ''; }");
        return r == null ? "" : r.toString();
    }

    public void closeDropdown() {
        try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
        page.evaluate("() => { try { jQuery('select').filter((i,e)=>jQuery(e).data('select2')).select2('close'); } catch(e){} }");
        waitForAngular(300);
    }

    /** Pick {@code name} in the Store list as a user does (open, click the entry). "-Select-" clears it. @return "" on success, else why not */
    public String pickStore(String name) {
        if (openDropdown(STORE) < 0) return "the Store list did not open";
        Object found = page.evaluate("(w) => {" + NORM + OPEN_DROP + " if(!drop) return false; const li=[...drop.querySelectorAll('li.select2-result-selectable')].find(x=>norm(x.textContent)===w);"
                + " if(!li) return false; li.id='__saOpt'; return true; }", name);
        if (!Boolean.TRUE.equals(found)) { closeDropdown(); return "\"" + name + "\" is not in the Store list"; }
        try { page.locator("#__saOpt").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { closeDropdown(); return "\"" + name + "\" could not be clicked (" + firstLine(e) + ")"; }
        finally { untag("__saOpt"); }
        waitForAngular(400);
        boolean placeholder = name.matches("(?i)-*\\s*select\\s*-*");
        String shown = chosen(STORE), held = model("SearchStoreID");
        if (placeholder) return held.isEmpty() || "null".equals(held) ? "" : "the Store still holds " + held;
        if (!name.equals(shown)) return "the box shows \"" + shown + "\" instead of \"" + name + "\"";
        if (held.isEmpty() || "null".equals(held)) return "\"" + name + "\" is shown but the screen does not hold it";
        return "";
    }

    public String clearStore() { return pickStore("-Select-"); }

    /** Click a status radio (STATUS_PENDING / STATUS_APPROVED / STATUS_ALL). @return the status the screen then holds */
    public String setStatus(String radio) {
        page.locator(radio).check(new Locator.CheckOptions().setTimeout(5000));
        waitForAngular(200);
        return model("status");
    }

    /** {checked radio value or "", list of "value:label"}. */
    public String checkedStatus() {
        Object r = page.evaluate("() => { const c=[...document.querySelectorAll(\"input[ng-model='StockAdjustment.status']\")].find(x=>x.checked); return c? c.value : ''; }");
        return r == null ? "" : r.toString();
    }

    // ---------------------------------------------------------------- dates (720kb date picker; keyboard entry is not accepted)

    /** Type into the date box as a user would. @return {box value, model value} afterwards */
    public String[] typeIntoDate(String selector, String text) {
        Locator box = page.locator(selector);
        box.click(new Locator.ClickOptions().setTimeout(6000));
        box.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(40));
        String[] r = {box.inputValue(), model(FROM_DATE.equals(selector) ? "FromDate" : "ToDate")};
        closeCalendar();
        return r;
    }

    /** Clear the date box with Ctrl+A, Backspace (accepted by the box). @return {box, model} */
    public String[] clearDate(String selector) {
        Locator box = page.locator(selector);
        box.click(new Locator.ClickOptions().setTimeout(6000));
        box.press("Control+A");
        box.press("Backspace");
        closeCalendar();
        return new String[]{box.inputValue(), model(FROM_DATE.equals(selector) ? "FromDate" : "ToDate")};
    }

    /** True while a calendar popup is open. */
    public boolean calendarOpen() { return page.locator(OPEN_CAL).count() > 0; }

    /** "Month YYYY" shown by the open calendar ("" when none is open). */
    public String calendarHeader() {
        if (!calendarOpen()) return "";
        return page.locator(OPEN_CAL).first().locator("._720kb-datepicker-calendar-header-middle").first().innerText().replaceAll("\\s+", " ").trim();
    }

    /** Click the date box so its calendar opens. @return true when a calendar opened */
    public boolean openCalendar(String selector) {
        page.locator(selector).click(new Locator.ClickOptions().setTimeout(6000));
        try { page.locator(OPEN_CAL).first().waitFor(new Locator.WaitForOptions().setTimeout(4000)); return true; }
        catch (Exception e) { return false; }
    }

    public void closeCalendar() {
        dismissDatePicker();
        waitForAngular(200);
    }

    /**
     * Pick {@code date} in the box's calendar the way a user does: open it, choose the year in its year list, step months with the arrows,
     * click the day. @return "" on success (box and model show the date), else why not
     */
    public String pickDate(String selector, LocalDate date) {
        if (!calendarOpen() && !openCalendar(selector)) return "the calendar of " + selector + " did not open";
        Locator cal = page.locator(OPEN_CAL).first();
        String want = date.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + date.getYear();
        try {
            if (!calendarHeader().endsWith(String.valueOf(date.getYear()))) {
                cal.locator("a[ng-click='showYearsPagination = !showYearsPagination']").click(new Locator.ClickOptions().setTimeout(4000));
                Locator year = cal.locator("a[ng-click='setNewYear(y)']").filter(new Locator.FilterOptions()
                        .setHasText(java.util.regex.Pattern.compile("^\\s*" + date.getYear() + "\\s*$")));
                for (int i = 0; i < 6 && year.count() == 0; i++) {
                    String which = date.getYear() < firstListedYear(cal) ? "paginateYears(paginationYears[0])" : "paginateYears(paginationYears[paginationYears.length -1 ])";
                    cal.locator("a[ng-click='" + which + "']").first().click(new Locator.ClickOptions().setTimeout(3000));
                    waitForAngular(200);
                }
                if (year.count() == 0) { closeCalendar(); return "year " + date.getYear() + " is not offered by the calendar"; }
                year.first().click(new Locator.ClickOptions().setTimeout(4000));
            }
            for (int i = 0; i < 13 && !calendarHeader().equalsIgnoreCase(want); i++) {
                LocalDate shown = headerDate(calendarHeader());
                String arrow = shown != null && shown.isAfter(date.withDayOfMonth(1)) ? "prevMonth()" : "nextMonth()";
                cal.locator("a[ng-click='" + arrow + "']").first().click(new Locator.ClickOptions().setTimeout(3000));
                waitForAngular(150);
            }
            if (!calendarHeader().equalsIgnoreCase(want)) { String h = calendarHeader(); closeCalendar(); return "the calendar shows " + h + " instead of " + want; }
            Locator day = cal.locator("a[ng-click='setDatepickerDay(item)']:not(._720kb-datepicker-disabled)")
                    .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^\\s*" + date.getDayOfMonth() + "\\s*$")));
            if (day.count() == 0) { closeCalendar(); return "day " + date.getDayOfMonth() + " is not selectable in " + want; }
            day.first().click(new Locator.ClickOptions().setTimeout(4000));
        } catch (Exception e) {
            closeCalendar();
            return "calendar step failed: " + firstLine(e);
        }
        waitForAngular(300);
        if (calendarOpen()) closeCalendar();
        String exp = date.format(DMY), box = fieldValue(selector), held = model(FROM_DATE.equals(selector) ? "FromDate" : "ToDate");
        return exp.equals(box) && exp.equals(held) ? "" : "after picking " + exp + " the box shows \"" + box + "\" and the screen holds \"" + held + "\"";
    }

    private int firstListedYear(Locator cal) {
        try { return Integer.parseInt(cal.locator("a[ng-click='setNewYear(y)']").first().innerText().trim()); } catch (Exception e) { return 0; }
    }

    private static LocalDate headerDate(String header) {
        try { return LocalDate.parse("01 " + header, DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH)); } catch (Exception e) { return null; }
    }

    public String fieldValue(String selector) { return page.locator(selector).inputValue(); }

    // ---------------------------------------------------------------- search

    /** Outcome of one click on Search. */
    public static class SearchResult {
        public String request = "";
        public int status = -1;
        public long ms = -1;
        /** rows in the API response (one per item line) */
        public int apiRows = -1;
        /** rows the grid holds (the screen keeps one row per stock adjustment) */
        public int rows = -1;
        public String error = "";
        public boolean fired() { return status >= 0; }
        @Override public String toString() {
            return fired() ? "HTTP " + status + ", API " + apiRows + " line(s) -> grid " + rows + " row(s), " + ms + " ms" : "no search request (" + error + ")";
        }
    }

    /** Click Search and wait for the GetStockAdjustmentSearchList response (up to {@code waitMs}). */
    public SearchResult search(int waitMs) {
        SearchResult r = new SearchResult();
        long t0 = System.currentTimeMillis();
        try {
            Response resp = page.waitForResponse(x -> x.url().toLowerCase().contains(FETCH_API) && String.valueOf(x.request().postData()).contains(SEARCH_FLAG),
                    new Page.WaitForResponseOptions().setTimeout(waitMs),
                    () -> page.locator(SEARCH_BTN).click(new Locator.ClickOptions().setTimeout(6000)));
            r.ms = System.currentTimeMillis() - t0;
            r.status = resp.status();
            r.request = String.valueOf(resp.request().postData());
            try { String b = resp.text().trim(); r.apiRows = b.startsWith("[") ? countLines(b) : -1; } catch (Exception ignore) { }
        } catch (Exception e) {
            r.error = firstLine(e);
        }
        waitForAngular(900);
        r.rows = masterRows();
        return r;
    }

    public SearchResult search() { return search(30000); }

    // ---------------------------------------------------------------- grids

    public int masterRows() { return intOf(page.evaluate("() => {" + SCOPE + " return sc && sc.gridOptions && sc.gridOptions.data ? sc.gridOptions.data.length : -1; }")); }
    public int detailRows() { return intOf(page.evaluate("() => {" + SCOPE + " return sc && sc.gridOptionsone && sc.gridOptionsone.data ? sc.gridOptionsone.data.length : -1; }")); }

    public List<String> headers(String grid) {
        return strings(page.evaluate("(g) => {" + NORM + " return [...document.querySelectorAll(g+' .ui-grid-header-cell .ui-grid-header-cell-label')].map(e=>norm(e.textContent)).filter(t=>t); }", grid));
    }

    /** One field of every row ON THE CURRENT PAGE after sorting / column filters, in display order (ui-grid's visible row cache — not
     *  the DOM, which only draws the rows scrolled into view). */
    public List<String> pageField(String grid, String field) {
        return strings(page.evaluate("([g,f]) => { const el=document.querySelector(g); if(!el) return [];"
                + " const gr=angular.element(el).isolateScope().grid; return gr.renderContainers.body.visibleRowCache.map(r=>r.entity[f]==null?'':String(r.entity[f])); }",
                Arrays.asList(grid, field)));
    }

    /** Rows on the current page after column filters. */
    public int pageRows(String grid) {
        return intOf(page.evaluate("(g) => { const el=document.querySelector(g); if(!el) return -1; return angular.element(el).isolateScope().grid.renderContainers.body.visibleRowCache.length; }", grid));
    }

    /** All master rows as "stockadjustmentno | dates | storename | isapproved | approvallevel | remark | stockadjid | locationid". */
    public List<String> masterRecords() {
        return strings(page.evaluate("() => {" + SCOPE + " return (sc.gridOptions.data||[]).map(r=>[r.stockadjustmentno,r.dates,r.storename,r.isapproved,r.approvallevel,"
                + "String(r.remark==null?'':r.remark).replace(/\\s+/g,' ').slice(0,40),r.stockadjid,r.locationid].join(' | ')); }"));
    }

    /** One field of every master row (all pages). */
    public List<String> masterField(String field) {
        return strings(page.evaluate("(f) => {" + SCOPE + " return (sc.gridOptions.data||[]).map(r=>r[f]==null?'':String(r[f])); }", field));
    }

    /** Detail rows as "code | name | batch | avlqty | adjqty | type | reason | balance | WAC | tax% | tax | net | stockadjid". */
    public List<String> detailRecords() {
        return strings(page.evaluate("() => {" + SCOPE + " return (sc.gridOptionsone.data||[]).map(d=>[d.serviceitemcode,d.serviceitemname,d.batchcode,d.avlqty,d.adjqty,"
                + "d.adjustmenttypetext,d.reason,d.BalanceAfterAdjustment,d.WAC,d.taxper,d.taxamount,d.NettAmount,d.stockadjid,d.adjustmenttype].join(' | ')); }"));
    }

    /** The pager's "1 - 25 of 37 items" text. */
    public String pagerText(String grid) {
        Object r = page.evaluate("(g) => {" + NORM + " const c=document.querySelector(g+' .ui-grid-pager-count-container'); return c? norm(c.textContent) : ''; }", grid);
        return r == null ? "" : r.toString();
    }

    /** Text the grid shows when it holds no rows ("" when it shows nothing at all). */
    public String emptyGridMessage(String grid) {
        Object r = page.evaluate("(g) => {" + NORM + " const el=document.querySelector(g); if(!el) return '';"
                + " const m=el.querySelector('.ui-grid-no-row-overlay, .watermark, .no-data, .ui-grid-empty'); if(m && m.offsetParent!==null) return norm(m.textContent);"
                + " const vp=el.querySelector('.ui-grid-render-container-body .ui-grid-viewport'); return vp? norm(vp.innerText) : ''; }", grid);
        return r == null ? "" : r.toString();
    }

    /** Type into the per-column filter box of data column {@code colIndex} (0 = Stock Adjustment No). */
    public void filterColumn(String grid, int colIndex, String text) {
        Locator f = page.locator(grid + " .ui-grid-header-cell .ui-grid-filter-input").nth(colIndex);
        f.click(new Locator.ClickOptions().setTimeout(5000));
        f.fill("");
        if (!text.isEmpty()) f.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(30));
        waitForAngular(800);
    }

    public int filterInputs(String grid) { return page.locator(grid + " .ui-grid-header-cell .ui-grid-filter-input").count(); }

    /** The current text of every column filter box. */
    public List<String> filterValues(String grid) {
        return strings(page.evaluate("(g) => [...document.querySelectorAll(g+' .ui-grid-header-cell .ui-grid-filter-input')].map(i=>i.value)", grid));
    }

    /** Click a header label to sort. @return the direction the grid then reports for that column ("asc", "desc" or "") */
    public String clickHeader(String grid, String header) {
        page.locator(grid + " .ui-grid-header-cell .ui-grid-header-cell-label").filter(new Locator.FilterOptions().setHasText(header)).first()
                .click(new Locator.ClickOptions().setTimeout(6000));
        waitForAngular(600);
        return sortDirection(grid, header);
    }

    public String sortDirection(String grid, String header) {
        Object d = page.evaluate("([g,h]) => {" + NORM + " const el=document.querySelector(g); const gr=angular.element(el).isolateScope().grid;"
                + " const c=gr.columns.find(c=>norm(c.displayName)===h); return c && c.sort && c.sort.direction ? c.sort.direction : ''; }", Arrays.asList(grid, header));
        return d == null ? "" : d.toString();
    }

    /** Remove every sort from the grid (ui-grid API; client side only). */
    public void clearSort(String grid) {
        page.evaluate("(g) => { const el=document.querySelector(g); const gr=angular.element(el).isolateScope().grid; gr.columns.forEach(c=>{ if(c.sort) c.sort={}; });"
                + " gr.refresh(); }", grid);
        waitForAngular(400);
    }

    /** Pager state: {current page, total pages, page size}. */
    public int[] pager(String grid) {
        List<?> l = (List<?>) page.evaluate("(g) => { const el=document.querySelector(g); const gr=angular.element(el).isolateScope().grid;"
                + " return [gr.options.paginationCurrentPage||1, Math.max(1, gr.api.pagination.getTotalPages()), gr.options.paginationPageSize]; }", grid);
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

    public boolean pagerDisabled(String grid, String which) {
        Locator b = page.locator(grid + " .ui-grid-pager-" + which);
        return b.count() == 0 || b.first().isDisabled();
    }

    /** The page sizes the pager offers. */
    public List<String> pageSizes(String grid) {
        return strings(page.evaluate("(g) => { const s=document.querySelector(g+' .ui-grid-pager-row-count-picker select'); return s? [...s.options].map(o=>o.text.trim()) : []; }", grid));
    }

    /** Choose a page size in the pager's own (select2) drop-down. @return the page size the grid then uses */
    public int setPageSize(String grid, int size) {
        Object tagged = page.evaluate("(g) => { const el=document.querySelector(g+' .ui-grid-pager-row-count-picker .select2-choice') || document.querySelector(g+' .ui-grid-pager-row-count-picker select');"
                + " if(!el) return false; el.id='__saSize'; return el.tagName; }", grid);
        if (Boolean.FALSE.equals(tagged)) return -1;
        try {
            if ("SELECT".equals(String.valueOf(tagged))) page.locator("#__saSize").selectOption(String.valueOf(size));
            else {
                page.locator("#__saSize").click(new Locator.ClickOptions().setTimeout(5000));
                page.locator(".select2-drop-active:visible li.select2-result-selectable")
                        .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^\\s*" + size + "\\s*$")))
                        .first().click(new Locator.ClickOptions().setTimeout(5000));
            }
        } catch (Exception e) {
            closeDropdown();
        } finally { untag("__saSize"); }
        waitForAngular(600);
        return pager(grid)[2];
    }

    /** Horizontal scroll check: {scrollWidth, clientWidth, scrollLeft after scrolling right, header scrollLeft after scrolling}. */
    public int[] horizontalScroll(String grid) {
        List<?> l = (List<?>) page.evaluate("async (g) => { const vp=document.querySelector(g+' .ui-grid-render-container-body .ui-grid-viewport'); if(!vp) return [-1,-1,-1,-1];"
                + " const hd=document.querySelector(g+' .ui-grid-render-container-body .ui-grid-header-viewport');"
                + " vp.scrollLeft=vp.scrollWidth; vp.dispatchEvent(new Event('scroll')); await new Promise(r=>setTimeout(r,500));"
                + " const out=[vp.scrollWidth, vp.clientWidth, Math.round(vp.scrollLeft), hd?Math.round(hd.scrollLeft):-1];"
                + " vp.scrollLeft=0; vp.dispatchEvent(new Event('scroll')); return out; }", grid);
        return new int[]{intOf(l.get(0)), intOf(l.get(1)), intOf(l.get(2)), intOf(l.get(3))};
    }

    /** {grid height, height of the box that holds it} — the panel's wrapper is fixed at 230 px while the grid is 530 px. */
    public int[] gridBox(String grid) {
        List<?> l = (List<?>) page.evaluate("(g) => { const el=document.querySelector(g); if(!el) return [-1,-1]; const w=el.closest('.table-responsive');"
                + " return [Math.round(el.getBoundingClientRect().height), w? Math.round(w.getBoundingClientRect().height) : -1]; }", grid);
        return new int[]{intOf(l.get(0)), intOf(l.get(1))};
    }

    /** Number of Approved checkboxes on the current page, and how many of them are enabled (a click on one APPROVES — never done here). */
    public int[] approveCheckboxes() {
        List<?> l = (List<?>) page.evaluate("() => { const c=[...document.querySelectorAll(\"" + MASTER_GRID + " input[type=checkbox][ng-click*='changeisapproved']\")];"
                + " return [c.length, c.filter(x=>!x.disabled).length]; }");
        return new int[]{intOf(l.get(0)), intOf(l.get(1))};
    }

    /**
     * Select drawn master row {@code index} with ui-grid's row-header tick (NOT the Approved checkbox) and wait for its details
     * (ExecFlag GetPhysicalStockdetails). @return {HTTP status (-1 = nothing fetched), request body}
     */
    public String[] selectMasterRow(int index) {
        try {
            Response r = page.waitForResponse(x -> x.url().toLowerCase().contains(FETCH_API) && String.valueOf(x.request().postData()).contains(DETAIL_FLAG),
                    new Page.WaitForResponseOptions().setTimeout(20000),
                    () -> page.locator(MASTER_GRID + " .ui-grid-render-container-left .ui-grid-viewport .ui-grid-row").nth(index)
                            .locator(".ui-grid-selection-row-header-buttons").click(new Locator.ClickOptions().setTimeout(6000)));
            waitForAngular(800);
            return new String[]{String.valueOf(r.status()), String.valueOf(r.request().postData())};
        } catch (Exception e) {
            return new String[]{"-1", firstLine(e)};
        }
    }

    /** Stock Adjustment Nos the master grid reports as selected. */
    public List<String> selectedRows() {
        return strings(page.evaluate("(g) => { const el=document.querySelector(g); return angular.element(el).isolateScope().grid.api.selection.getSelectedRows().map(r=>String(r.stockadjustmentno)); }", MASTER_GRID));
    }

    /** {stockadjid, locationid} the screen holds for Print ("" when none). */
    public String[] heldSelection() { return new String[]{model("stockadjid"), model("locationid")}; }

    // ---------------------------------------------------------------- print (report tab is a GET returning a PDF: read-only)

    /** What a Print click did. */
    public static class PrintResult {
        public String url = "";
        public int status = -1;
        public String contentType = "";
        public String dialog = "";
        public String error = "";
    }

    /**
     * Click Print. With a selected record the app opens the report in a new tab: its URL and the HTTP status / content type of its main
     * response are captured, then the tab is closed. Without a selection the app shows a confirm box: its text is captured and it is closed
     * with Cancel (both of its buttons only close it, but the red Ok is never used).
     */
    public PrintResult print() {
        PrintResult pr = new PrintResult();
        BrowserContext ctx = page.context();
        final List<Response> reports = java.util.Collections.synchronizedList(new ArrayList<>());
        Consumer<Response> h = r -> { if (r.url().contains(REPORT_PAGE)) reports.add(r); };
        ctx.onResponse(h);
        Page popup = null;
        try {
            try {
                popup = ctx.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(8000),
                        () -> page.locator(PRINT_BTN).click(new Locator.ClickOptions().setTimeout(6000)));
            } catch (Exception noTab) {
                waitForAngular(600);
                pr.dialog = confirmText();
                if (pr.dialog.isEmpty()) pr.error = "no report tab and no message appeared";
                dismissConfirm();
                return pr;
            }
            try { popup.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(30000)); } catch (Exception ignore) { }
            for (int i = 0; i < 40 && reports.isEmpty(); i++) popup.waitForTimeout(250);
            pr.url = popup.url();
            if (!reports.isEmpty()) {
                Response r = reports.get(reports.size() - 1);
                pr.status = r.status();
                pr.contentType = String.valueOf(r.headers().get("content-type"));
                if (pr.url.isEmpty() || pr.url.startsWith("about:")) pr.url = r.url();
            } else pr.error = "the report tab answered nothing within 10 s";
            if (!pr.contentType.contains("pdf")) {
                String err = com.kpj.core.DevHisBase.reportPageError(popup);
                if (!err.isEmpty()) pr.error = err;
            }
        } catch (Exception e) {
            pr.error = firstLine(e);
        } finally {
            ctx.offResponse(h);
            if (popup != null) { try { popup.close(); } catch (Exception ignore) { } }
            try { page.bringToFront(); } catch (Exception ignore) { }
        }
        return pr;
    }

    private static final String CONFIRM_BOXES = "const shown=e=>{const r=e.getBoundingClientRect(); const cs=getComputedStyle(e); return r.width>0 && r.height>0 && cs.visibility!=='hidden' && cs.display!=='none';};"
            + " const boxes=()=>[...document.querySelectorAll('.ng-confirm-box, .jconfirm-box, [class*=\"confirm-box\"]')].filter(shown);";

    /** Text of the confirm box on screen ("" when none). Waits up to 3 s for its open animation. */
    public String confirmText() {
        try { page.waitForFunction("() => {" + CONFIRM_BOXES + " return boxes().length>0; }", null, poll(3000)); } catch (Exception none) { return ""; }
        Object d = page.evaluate("() => {" + NORM + CONFIRM_BOXES + " const b=boxes().pop(); return b? norm(b.innerText) : ''; }");
        return d == null ? "" : d.toString();
    }

    /** Close every open confirm box with its CANCEL button (never Ok). @return how many were closed */
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

    /** Clear anything that would sit over the screen and swallow clicks: confirm boxes, an open select2 list, a calendar, the grid menu. */
    public String closeOverlays() {
        StringBuilder sb = new StringBuilder();
        int c = dismissConfirm();
        if (c > 0) sb.append(c).append(" confirm box(es) closed; ");
        Object open = page.evaluate("() => {" + OPEN_DROP + " return !!drop; }");
        if (Boolean.TRUE.equals(open)) { closeDropdown(); sb.append("dropdown list closed; "); }
        if (calendarOpen()) { closeCalendar(); sb.append("calendar closed; "); }
        Object menu = page.evaluate("() => [...document.querySelectorAll('.ui-grid-menu-items')].some(e=>e.offsetParent!==null)");
        if (Boolean.TRUE.equals(menu)) {
            try { page.locator(MASTER_GRID + " .ui-grid-menu-button").first().click(new Locator.ClickOptions().setTimeout(3000)); } catch (Exception ignore) { }
            sb.append("grid menu closed; ");
        }
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
        btn.first().click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(300);
        return items;
    }

    /** Open the grid menu and click its "Clear all filters" entry (client side only — it never calls the server). @return false when absent */
    public boolean gridMenuClearAllFilters(String grid) {
        Locator btn = page.locator(grid + " .ui-grid-menu-button");
        if (btn.count() == 0) return false;
        btn.first().click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(400);
        Locator item = page.locator(grid + " .ui-grid-menu-items li button").filter(new Locator.FilterOptions()
                .setHasText(java.util.regex.Pattern.compile("^\\s*Clear all filters\\s*$")));
        if (item.count() == 0) { btn.first().click(); return false; }
        item.first().click(new Locator.ClickOptions().setTimeout(4000));
        waitForAngular(700);
        if (Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ui-grid-menu-items')].some(e=>e.offsetParent!==null)"))) btn.first().click();
        return true;
    }

    // ---------------------------------------------------------------- toast capture

    /** Record every JAlert toast the screen raises into {@code window.__saAlerts} (re-installed after each load; idempotent). */
    public void hookAlerts() {
        page.evaluate("() => { if (window.__saHooked) return; window.__saHooked = true; window.__saAlerts = window.__saAlerts || [];"
                + " const o = window.JAlert; window.JAlert = function(m, t) { try { window.__saAlerts.push(String(m) + (t ? ' [' + t + ']' : '')); } catch(e){}"
                + " if (o) try { return o.apply(this, arguments); } catch(e){} }; }");
    }

    /** Toasts raised since the last call (and clears them). */
    public List<String> drainAlerts() {
        return strings(page.evaluate("() => { const a = window.__saAlerts || []; window.__saAlerts = []; return a; }"));
    }

    // ---------------------------------------------------------------- helpers

    private void untag(String id) {
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.removeAttribute('id'); }", id); } catch (Exception ignore) { }
    }

    /** Lines in a GetStockAdjustmentSearchList response: every line object carries exactly one "stockadjid" key. */
    private static int countLines(String json) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"stockadjid\"\\s*:").matcher(json);
        int n = 0;
        while (m.find()) n++;
        return n;
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
        return String.valueOf(e.getMessage()).split("\\R")[0];
    }
}
