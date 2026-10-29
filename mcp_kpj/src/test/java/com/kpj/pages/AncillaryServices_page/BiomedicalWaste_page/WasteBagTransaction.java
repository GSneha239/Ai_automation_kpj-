package com.kpj.pages.AncillaryServices_page.BiomedicalWaste_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ancillary Services &gt; Biomedical Waste &gt; <b>Waste Bag Transaction</b> — Page Object.
 *
 * <p>Verified live on HIS Demo (v2026.10.28.322-development-ui-ux, 29/10/2026):</p>
 * <ul>
 *   <li><b>List</b> — route {@code #/WasteBagTransaction} (state {@code WasteBagTransaction}, template
 *       {@code AncillaryServices/Load/WasteBagTransactionList}, controller {@code WasteBagTransactionListController}).
 *       Sidebar: Ancillary Services &gt; Biomedical Waste &gt; {@code a[href='#/WasteBagTransaction']}.
 *       Filters: <b>From Date</b> / <b>To Date</b> ({@code WasteBagTransaction.fromdate/todate}, 720kb date picker,
 *       dd/MM/yyyy, both default to today), <b>Search</b> button and an <b>Add</b> link. ui-grid {@code gridOptions}
 *       with columns <b>Transaction Date</b> ({@code date}), <b>Bag Type</b> ({@code bagtype}), <b>Quantity</b>,
 *       <b>Weight</b>, <b>Edit</b>; per-column filter boxes, pagination 25/50/75/100/125.</li>
 *   <li>List API {@code POST /API//WasteBagTransaction/fetch} body {@code {fromdate, todate, ExecFlag:"GRID"}}. The
 *       first load sends no dates. Empty dates are NOT validated: the call goes out with "" dates.</li>
 *   <li>After every fetch the list controller throws {@code TypeError ... reading 'IsEdit'} ({@code $scope.Access} is
 *       never set), so the Edit-column permission toggle never runs. The rows are still bound before the error.</li>
 *   <li><b>Add</b> — route {@code #/addWasteBagTransaction} (controller {@code WasteBagTransactionController}), form
 *       {@code #mynewbiomedicalWasteform} "Add / Update Bag Details": <b>Date</b> (defaults to today),
 *       <b>Bag Type</b> (select2 over {@code select#BMWbagTyp}; options from
 *       {@code POST /api/CommonDropdown/FetchListForAncillary {ExecFlag:"WasteBagType"}}), <b>Quantity</b>
 *       ({@code #BMWqty}, maxlength 6) and <b>Weight</b> ({@code #BMWwt}, maxlength 6), <b>Save</b> and <b>Back</b>.
 *       No field is marked mandatory and {@code IUDWasteTransaction()} has no client-side checks — Save posts straight
 *       to {@code POST /API//WasteBagTransaction/IUD}. Messages (JAlert): "Waste bag save successfully" (then back to
 *       the list), "Failed", "Error".</li>
 * </ul>
 *
 * <p>Write safety: {@link #saveWithSaveBlocked()} aborts the IUD call at the network layer, so it can never create a
 * record. Only {@link #saveForReal()} lets the call through — the test calls it only behind
 * {@code -Ddevhis.allowSave=true}.</p>
 */
public class WasteBagTransaction extends BasePage {

    public static final String LIST_ROUTE = "#/WasteBagTransaction";
    public static final String ADD_ROUTE = "#/addWasteBagTransaction";
    public static final List<String> EXPECTED_COLUMNS = List.of("Transaction Date", "Bag Type", "Quantity", "Weight", "Edit");

    public static final String MSG_SAVED = "Waste bag save successfully";
    public static final String DATE_FORMAT = "dd/MM/yyyy";

    private static final String FETCH_API = "/WasteBagTransaction/fetch";
    private static final String SAVE_API = "/WasteBagTransaction/IUD";
    private static final String SAVE_ROUTE_GLOB = "**" + SAVE_API + "**";
    private static final String BAGTYPE_API = "/CommonDropdown/FetchListForAncillary";

    private static final String LIST_CTRL = "[ng-controller='WasteBagTransactionListController']";
    private static final String FROM_DATE = LIST_CTRL + " input[ng-model='WasteBagTransaction.fromdate']";
    private static final String TO_DATE = LIST_CTRL + " input[ng-model='WasteBagTransaction.todate']";
    private static final String GRID = LIST_CTRL + " div[ui-grid='gridOptions']";

    private static final String FORM = "form#mynewbiomedicalWasteform";
    private static final String FORM_DATE = FORM + " input[ng-model='WasteBagTransaction.date']";
    private static final String BAG_TYPE = FORM + " select#BMWbagTyp";
    private static final String QTY = FORM + " input#BMWqty";
    private static final String WEIGHT = FORM + " input#BMWwt";

    // ---- observed network / console --------------------------------------------------------------------------------

    /** One answer of the list API: what was asked and what came back. */
    public static final class FetchResult {
        public final int status;
        public final String requestBody;
        public final int rows;          // -1 when the body was not a JSON array
        public final String note;
        FetchResult(int status, String requestBody, int rows, String note) {
            this.status = status; this.requestBody = requestBody; this.rows = rows; this.note = note;
        }
        @Override public String toString() {
            return "HTTP " + status + ", " + (rows < 0 ? "unparsed body" : rows + " row(s)") + ", request " + requestBody
                    + (note == null || note.isEmpty() ? "" : " (" + note + ")");
        }
    }

    /** Outcome of a Save click with the IUD call blocked. */
    public static final class BlockedSave {
        public final boolean saveCallAttempted;
        public final String postedBody;
        public final String alerts;
        BlockedSave(boolean attempted, String body, String alerts) {
            this.saveCallAttempted = attempted; this.postedBody = body; this.alerts = alerts;
        }
    }

    public volatile FetchResult lastFetch = null;
    public volatile String bagTypeApi = "(bag type API not observed)";
    private final List<FetchResult> fetches = Collections.synchronizedList(new ArrayList<>());
    private final List<String> appErrors = Collections.synchronizedList(new ArrayList<>());

    private final Consumer<Response> responseWatcher = resp -> {
        try {
            String url = resp.url();
            if (url.contains(FETCH_API)) {
                String body = String.valueOf(resp.request().postData());
                if (!body.contains("GRID")) return;               // the add form's INSERT/SINGLEVAL lookups are not list calls
                String text = "";
                try { text = resp.text(); } catch (Exception ignore) { }
                FetchResult r = new FetchResult(resp.status(), body,
                        text.trim().startsWith("[") ? countJsonRows(text) : -1,
                        resp.status() == 200 ? "" : shorten(text, 160));
                lastFetch = r;
                fetches.add(r);
            } else if (url.contains(BAGTYPE_API)) {
                String body = String.valueOf(resp.request().postData());
                if (!body.contains("WasteBagType")) return;
                String text = "";
                try { text = resp.text(); } catch (Exception ignore) { }
                bagTypeApi = "HTTP " + resp.status() + ", "
                        + (text.trim().startsWith("[") ? countJsonRows(text) + " option(s)" : "body: " + shorten(text, 120));
            }
        } catch (Exception ignore) { }
    };

    private final Consumer<ConsoleMessage> consoleWatcher = msg -> {
        try {
            if (!"error".equals(msg.type())) return;
            String t = msg.text();
            if (t != null && t.contains("WasteBagTransaction")) appErrors.add(shorten(t.split("\n")[0], 200));
        } catch (Exception ignore) { }
    };

    public WasteBagTransaction(Page page) {
        super(page);
        page.onResponse(responseWatcher);
        page.onConsoleMessage(consoleWatcher);
    }

    /** Stop listening to the page (call when the test is done with this page object). */
    public void dispose() {
        try { page.offResponse(responseWatcher); } catch (Exception ignore) { }
        try { page.offConsoleMessage(consoleWatcher); } catch (Exception ignore) { }
        try { page.unroute(SAVE_ROUTE_GLOB); } catch (Exception ignore) { }
    }

    /** JavaScript errors raised by the Waste Bag Transaction controllers so far (distinct, in order). */
    public List<String> appErrors() {
        synchronized (appErrors) { return new ArrayList<>(new java.util.LinkedHashSet<>(appErrors)); }
    }

    public int fetchCount() { return fetches.size(); }

    // ---- navigation ------------------------------------------------------------------------------------------------

    /** True when the last {@link #navigateViaMenu()} reached the list through the sidebar link. */
    public boolean usedMenu = false;

    /** Ancillary Services -&gt; Biomedical Waste -&gt; Waste Bag Transaction; falls back to the hash route. */
    public boolean navigateViaMenu() {
        boolean menu = expandMenuEntry(null, "^ancillary services$")
                && expandMenuEntry("^ancillary services$", "^biomedical waste$");
        if (menu) {
            try {
                page.locator("a[href='" + LIST_ROUTE + "']").first().click(new Locator.ClickOptions().setTimeout(8000));
            } catch (Exception e) {
                System.out.println("WasteBagTransaction.nav: menu link not clickable - " + firstLine(e));
                menu = false;
            }
        } else {
            System.out.println("WasteBagTransaction.nav: Ancillary Services > Biomedical Waste could not be expanded");
        }
        if (!waitForListScreen(15000)) {
            System.out.println("WasteBagTransaction.nav: falling back to direct route " + LIST_ROUTE);
            page.evaluate("(h) => { window.location.hash = h.replace(/^#/, ''); }", LIST_ROUTE);
            waitForListScreen(15000);
        }
        usedMenu = menu;
        return onListScreen();
    }

    /** Waits for the list route, its controller, the date filters and the grid. */
    public boolean waitForListScreen(int timeoutMs) {
        try {
            page.waitForFunction("([from, grid]) => /#\\/WasteBagTransaction$/i.test(location.hash)"
                    + " && !!document.querySelector(from) && document.querySelector(from).offsetParent !== null"
                    + " && !!document.querySelector(grid)", List.of(FROM_DATE, GRID),
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean onListScreen() {
        Object r = page.evaluate("([from, grid]) => /#\\/WasteBagTransaction$/i.test(location.hash)"
                + " && !!document.querySelector(from) && document.querySelector(from).offsetParent !== null"
                + " && !!document.querySelector(grid) && /waste bag transaction/i.test(document.title || '')",
                List.of(FROM_DATE, GRID));
        return Boolean.TRUE.equals(r);
    }

    /** Title + URL actually shown — for reporting a wrong page. */
    public String currentScreen() {
        return page.evaluate("() => document.title || '(no title)'") + " @ " + page.url();
    }

    // ---- list: filters & search -----------------------------------------------------------------------------------

    public Locator searchButton() {
        return page.locator(LIST_CTRL).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Search").setExact(true));
    }

    /** The Add control is an {@code <a>} with ng-click="addWasteBagTransaction();". */
    public Locator addButton() {
        return page.locator(LIST_CTRL + " a[ng-click^='addWasteBagTransaction']").first();
    }

    /** Which list controls are missing — "" when all are present. */
    public String listProblems() {
        List<String> missing = new ArrayList<>();
        if (!page.locator(FROM_DATE).isVisible()) missing.add("From Date");
        if (!page.locator(TO_DATE).isVisible()) missing.add("To Date");
        if (!searchButton().isVisible()) missing.add("Search button");
        if (!addButton().isVisible()) missing.add("Add button");
        if (page.locator(GRID).count() == 0) missing.add("transaction grid");
        if (page.locator(GRID + " .ui-grid-pager-panel").count() == 0) missing.add("pagination bar");
        return String.join(", ", missing);
    }

    public String fromDate() { return page.locator(FROM_DATE).inputValue(); }
    public String toDate() { return page.locator(TO_DATE).inputValue(); }

    /** Type the From / To dates (dd/MM/yyyy or "" to clear) and close the calendar the date picker opens. */
    public void setDates(String from, String to) {
        page.locator(FROM_DATE).fill(from == null ? "" : from);
        page.locator(TO_DATE).fill(to == null ? "" : to);
        dismissDatePicker();
    }

    /** Click Search and wait for the list API's answer and the grid to re-render. */
    public FetchResult search() {
        try {
            page.waitForResponse(rs -> rs.url().contains(FETCH_API) && String.valueOf(rs.request().postData()).contains("GRID"),
                    new Page.WaitForResponseOptions().setTimeout(20000), () -> searchButton().click());
        } catch (Exception e) {
            return new FetchResult(-1, "(no list call)", -1, "no list API answer within 20s: " + firstLine(e));
        }
        page.waitForTimeout(600);  // the watcher records the answer; let ui-grid render the rows it was given
        return lastFetch;
    }

    /** Waits until the list's initial load has answered (or the timeout passes). */
    public void waitForInitialList(int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end && fetches.isEmpty()) page.waitForTimeout(250);
        page.waitForTimeout(400);
    }

    // ---- list: grid ------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<String> gridColumns() {
        Object r = page.evaluate("(grid) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const g=document.querySelector(grid); if(!g) return [];"
                + " return [...g.querySelectorAll('.ui-grid-header-cell .ui-grid-header-cell-label')]"
                + "   .map(e=>norm(e.textContent)).filter(t=>t && !/^sel\\.?$/i.test(t)); }", GRID);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** All rows the grid holds (every page) as {id, date, bagtype, quantity, weight} strings. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> gridData() {
        Object r = page.evaluate("(grid) => { const g=document.querySelector(grid); if(!g) return [];"
                + " const o=angular.element(g).scope().$eval('gridOptions'); const d=(o && o.data) || [];"
                + " const s=v=>v==null?'':String(v);"
                + " return d.map(x=>({id:s(x.id), date:s(x.date), bagtype:s(x.bagtype), quantity:s(x.quantity), weight:s(x.weight)})); }", GRID);
        return r instanceof List ? (List<Map<String, Object>>) r : new ArrayList<>();
    }

    /** Cell texts of the rows currently rendered (current page, after filters): one list of cells per row. */
    @SuppressWarnings("unchecked")
    public List<List<String>> visibleRows() {
        Object r = page.evaluate("(grid) => { const g=document.querySelector(grid); if(!g) return [];"
                + " const body=g.querySelector('.ui-grid-render-container-body') || g;"
                + " return [...body.querySelectorAll('.ui-grid-viewport .ui-grid-row')].map(row=>"
                + "   [...row.querySelectorAll('.ui-grid-cell .ui-grid-cell-contents')].map(c=>c.textContent.replace(/\\s+/g,' ').trim())); }", GRID);
        return r instanceof List ? (List<List<String>>) r : new ArrayList<>();
    }

    /** Pagination state: page size, current page, total pages and the offered page sizes. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> pagination() {
        Object r = page.evaluate("(grid) => { const g=document.querySelector(grid); if(!g) return {};"
                + " const o=angular.element(g).scope().$eval('gridOptions') || {};"
                + " const max=g.querySelector('.ui-grid-pager-max-pages-number');"
                + " return {pageSize:o.paginationPageSize, currentPage:o.paginationCurrentPage,"
                + "   totalPages:max?max.textContent.replace(/[^0-9]/g,''):'', sizes:o.paginationPageSizes||[]}; }", GRID);
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Click a column header to sort by it. Returns false when the header is not there. */
    public boolean clickColumnHeader(String label) {
        Locator h = page.locator(GRID + " .ui-grid-header-cell .ui-grid-header-cell-label").filter(
                new Locator.FilterOptions().setHasText(label)).first();
        if (h.count() == 0) return false;
        h.click();
        page.waitForTimeout(600);
        return true;
    }

    /** Type into a column's filter box (0 = Transaction Date, 1 = Bag Type, 2 = Quantity, 3 = Weight). */
    public boolean filterColumn(int index, String term) {
        Locator boxes = page.locator(GRID + " .ui-grid-header-cell input.ui-grid-filter-input");
        if (boxes.count() <= index) return false;
        boxes.nth(index).fill(term == null ? "" : term);
        page.waitForTimeout(800);
        return true;
    }

    public void clearColumnFilters() {
        Locator boxes = page.locator(GRID + " .ui-grid-header-cell input.ui-grid-filter-input");
        for (int i = 0; i < boxes.count(); i++) boxes.nth(i).fill("");
        page.waitForTimeout(400);
    }

    // ---- add form --------------------------------------------------------------------------------------------------

    /** Click Add and wait for the "Add / Update Bag Details" form. */
    public boolean openAddForm() {
        addButton().click();
        return waitForAddForm(15000);
    }

    public boolean waitForAddForm(int timeoutMs) {
        try {
            page.waitForFunction("(form) => /#\\/addWasteBagTransaction$/i.test(location.hash)"
                    + " && !!document.querySelector(form) && document.querySelector(form).offsetParent !== null", FORM,
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception e) {
            return false;
        }
        // the Bag Type options arrive asynchronously; give that call a moment so the form is complete
        try {
            page.waitForFunction("(sel) => { const s=document.querySelector(sel); if(!s) return false;"
                    + " const sc=angular.element(s).scope(); return !!sc && Array.isArray(sc.drpbagtype); }", BAG_TYPE,
                    new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }
        page.waitForTimeout(300);
        return true;
    }

    /** The form heading, e.g. "Add / Update Bag Details". */
    public String formHeading() {
        Object r = page.evaluate("(form) => { const h=document.querySelector(form + ' h5, ' + form + ' h4, ' + form + ' h3');"
                + " return h ? h.textContent.replace(/\\s+/g,' ').trim() : ''; }", FORM);
        return r == null ? "" : r.toString();
    }

    public Locator saveButton() {
        return page.locator(FORM).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save").setExact(true));
    }

    public Locator backButton() {
        return page.locator(FORM).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Back").setExact(true));
    }

    /**
     * Every field of the form as the user sees it: label, control, current value, maxlength, whether the app marks it
     * mandatory (a "*" on the label or a validationEngine {@code validate[required]} class) — read from the DOM.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> formFields() {
        Object r = page.evaluate("(form) => { const f=document.querySelector(form); if(!f) return [];"
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[];"
                + " for (const l of f.querySelectorAll('label.control-label')) {"
                + "   const grp=l.closest('.form-group') || l.parentElement;"
                + "   const c=grp.querySelector('select') || grp.querySelector('input:not(.select2-focusser):not(.select2-input)');"
                + "   if(!c) continue;"
                + "   const req=/\\*/.test(l.textContent) || /validate\\[[^\\]]*required/.test(c.className) || c.required"
                + "     || grp.querySelector('.text-danger,.required,.mandatory')!==null;"
                + "   const val=c.tagName==='SELECT' ? (c.selectedIndex>=0 ? norm(c.options[c.selectedIndex].text) : '') : c.value;"
                + "   out.push({label:norm(l.textContent), control:c.tagName.toLowerCase()+(c.id?'#'+c.id:''),"
                + "     model:c.getAttribute('ng-model')||'', value:val, maxlength:c.getAttribute('maxlength')||'', mandatory:req}); }"
                + " return out; }", FORM);
        return r instanceof List ? (List<Map<String, Object>>) r : new ArrayList<>();
    }

    /** Bag Type option texts offered (the "Select" placeholder excluded). */
    @SuppressWarnings("unchecked")
    public List<String> bagTypeOptions() {
        Object r = page.evaluate("(sel) => { const s=document.querySelector(sel); if(!s) return [];"
                + " return [...s.options].map(o=>o.text.replace(/\\s+/g,' ').trim()).filter(t=>t && !/^select$/i.test(t)); }", BAG_TYPE);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /**
     * Choose a Bag Type by its visible text. The real {@code <select>} sits behind select2, so the option is set on the
     * select, Angular is told through a change event, and the select2 box is refreshed to show it.
     */
    public boolean selectBagType(String text) {
        Object r = page.evaluate("([sel, t]) => { const s=document.querySelector(sel); if(!s) return false;"
                + " const o=[...s.options].find(x=>x.text.replace(/\\s+/g,' ').trim()===t); if(!o) return false;"
                + " s.value=o.value; s.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try { if(window.jQuery && jQuery(s).data('select2')) jQuery(s).select2('val', o.value); } catch(e) {}"
                + " return true; }", List.of(BAG_TYPE, text));
        page.waitForTimeout(400);
        return Boolean.TRUE.equals(r);
    }

    /** The values the form's Angular model holds right now — what Save would send. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> formModel() {
        Object r = page.evaluate("(form) => { const f=document.querySelector(form); if(!f) return {};"
                + " const m=angular.element(f).scope().$eval('WasteBagTransaction') || {}; const s=v=>v==null?'':String(v);"
                + " const sel=f.querySelector('select#BMWbagTyp');"
                + " return {date:s(m.date), bagtypeid:s(m.bagtypeid), bagtype:sel && sel.selectedIndex>0 ? sel.options[sel.selectedIndex].text.trim() : '',"
                + "   quantity:s(m.quantity), weight:s(m.weight)}; }", FORM);
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    public void setFormDate(String ddMMyyyy) {
        page.locator(FORM_DATE).fill(ddMMyyyy == null ? "" : ddMMyyyy);
        dismissDatePicker();
    }

    /** Type Quantity key by key, so the page's own key filtering applies as it would for a user. */
    public String typeQuantity(String text) { return typeInto(QTY, text); }

    /** Type Weight key by key, so the page's own key filtering applies as it would for a user. */
    public String typeWeight(String text) { return typeInto(WEIGHT, text); }

    private String typeInto(String selector, String text) {
        Locator box = page.locator(selector);
        box.fill("");
        if (text != null && !text.isEmpty()) box.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(40));
        return box.inputValue();
    }

    public String quantityValue() { return page.locator(QTY).inputValue(); }
    public String weightValue() { return page.locator(WEIGHT).inputValue(); }

    /** Empty every field (Date, Bag Type, Quantity, Weight). */
    public void clearForm() {
        setFormDate("");
        page.evaluate("(sel) => { const s=document.querySelector(sel); if(!s) return; s.selectedIndex=0;"
                + " s.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try { if(window.jQuery && jQuery(s).data('select2')) jQuery(s).select2('val', ''); } catch(e) {} }", BAG_TYPE);
        page.locator(QTY).fill("");
        page.locator(WEIGHT).fill("");
        page.waitForTimeout(200);
    }

    // ---- alerts ----------------------------------------------------------------------------------------------------

    /** Start recording JAlert messages (type:message) into window.__wbtAlerts. */
    public void captureAlerts() {
        page.evaluate("() => { window.__wbtAlerts = [];"
                + " if (window.JAlert && !window.JAlert.__wbtWrapped) { const o = window.JAlert;"
                + "   window.JAlert = function (m, t) { try { window.__wbtAlerts.push((t || '') + ':' + m); } catch (e) {} return o.apply(this, arguments); };"
                + "   window.JAlert.__wbtWrapped = true; } }");
    }

    @SuppressWarnings("unchecked")
    public List<String> alerts() {
        Object r = page.evaluate("() => window.__wbtAlerts || []");
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    /** Validation prompts validationEngine is showing in the form (if any). */
    @SuppressWarnings("unchecked")
    public List<String> validationPrompts() {
        Object r = page.evaluate("(form) => [...document.querySelectorAll(form + ' .formError, ' + form + ' .formErrorContent, ' + form + ' .help-block, ' + form + ' .error')]"
                + ".filter(e=>e.offsetParent!==null).map(e=>e.textContent.replace(/\\s+/g,' ').trim()).filter(t=>t)", FORM);
        return r instanceof List ? (List<String>) r : new ArrayList<>();
    }

    private void waitForAlert(int timeoutMs) {
        try {
            page.waitForFunction("() => (window.__wbtAlerts || []).length > 0", null,
                    new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception ignore) { }
        page.waitForTimeout(300);
    }

    // ---- save ------------------------------------------------------------------------------------------------------

    /**
     * Click Save while the IUD call is <b>aborted at the network layer</b> — it can never create a record. Reports
     * whether the page tried to save anyway (i.e. had no client-side validation for the values in the form), what it
     * would have sent, and the messages shown. An "error:Error" alert after an attempted call is the app reacting to
     * the blocked request, not a validation message.
     */
    public BlockedSave saveWithSaveBlocked() {
        final List<String> posted = Collections.synchronizedList(new ArrayList<>());
        Consumer<Route> blocker = route -> { posted.add(String.valueOf(route.request().postData())); route.abort(); };
        page.route(SAVE_ROUTE_GLOB, blocker);
        try {
            captureAlerts();
            saveButton().click();
            waitForAlert(4000);
            page.waitForTimeout(500);
            List<String> prompts = validationPrompts();
            String alerts = String.join(" | ", alerts());
            if (!prompts.isEmpty()) alerts = (alerts.isEmpty() ? "" : alerts + " | ") + "field prompts: " + prompts;
            return new BlockedSave(!posted.isEmpty(), posted.isEmpty() ? "" : posted.get(0), alerts);
        } finally {
            try { page.unroute(SAVE_ROUTE_GLOB, blocker); } catch (Exception ignore) { }
        }
    }

    /** Last real save-API result (status + body). */
    public String lastSaveApi = "(no save call made)";

    /**
     * REAL save: click Save and wait for the IUD answer and the message it raises. Creates a record. Call ONLY when the
     * run was explicitly authorised with {@code -Ddevhis.allowSave=true}.
     */
    public String saveForReal() {
        captureAlerts();
        try {
            Response resp = page.waitForResponse(rs -> rs.url().contains(SAVE_API),
                    new Page.WaitForResponseOptions().setTimeout(20000), () -> saveButton().click());
            String body = "";
            try { body = resp.text(); } catch (Exception ignore) { }
            lastSaveApi = "HTTP " + resp.status() + " " + shorten(body.replaceAll("\\s+", " ").trim(), 300);
        } catch (Exception e) {
            lastSaveApi = "no save call answered within 20s (" + firstLine(e) + ")";
        }
        waitForAlert(8000);
        return String.join(" | ", alerts());
    }

    /** Back: returns to the list (history.back()). */
    public boolean back() {
        backButton().click();
        return waitForListScreen(15000);
    }

    // ---- utility ---------------------------------------------------------------------------------------------------

    private static String firstLine(Exception e) { return String.valueOf(e.getMessage()).split("\n")[0]; }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** Counts top-level objects in a JSON array without a JSON library. */
    private static int countJsonRows(String json) {
        String t = json.trim();
        if (t.equals("[]")) return 0;
        int depth = 0, rows = 0;
        boolean inStr = false, esc = false;
        for (char c : t.toCharArray()) {
            if (inStr) {
                if (esc) esc = false; else if (c == '\\') esc = true; else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == '{') { if (depth == 1) rows++; depth++; }
            else if (c == '}') depth--;
            else if (c == '[') depth++;
            else if (c == ']') depth--;
        }
        return rows;
    }
}
