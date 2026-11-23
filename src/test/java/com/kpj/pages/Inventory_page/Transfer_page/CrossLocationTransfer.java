package com.kpj.pages.Inventory_page.Transfer_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inventory &gt; Transfer &gt; <b>Cross Location Transfer</b> — Page Object ({@code #/CrossLocationSaleList}, form {@code #/add-CrossLocationSale}).
 *
 * <p>Confirmed live (devhis build v2026.10.26.293). The menu puts this screen under <b>Inventory &gt; Transfer</b>. List screen with a
 * <b>New</b> button ({@code AddCrossLocationSale()}); the form is {@code IssueToStore.*}:</p>
 * <ul>
 *   <li><b>From Store</b> ({@code IssueToStore.SearchStoreID}, select2) is the <b>requesting</b> store and <b>To Store</b> ({@code IssueToStore.ToStore}) the <b>issuing</b> store
 *       — the Item Search dialog opened by Get Indent shows them as "Requesting Store" ({@code IdentItemSearch.FromStore}) and "Issuing Store"
 *       ({@code IdentItemSearch.ToStore}), pre-filled from the form. The store names are "Pharmacy (Central)" (with a space) and "Pharmacy Main Store".</li>
 *   <li><b>Get Indent</b> ({@code #GetIndent}, {@code OpenIndentdetailsList()}) opens the <b>Item Search</b> dialog (the same controller as CSSD &gt; Transfer): a <b>Search</b> button and
 *       four tables stacked top to bottom — <b>Selected Item</b> (the indents found; ticking one, {@code GetIndentItemlist}, fills the next table), <b>Indent Item List</b> (that indent's items;
 *       ticking one, {@code GetBatchList}, fills the next two and auto-ticks the batch), <b>Item Batch List</b> (the batches) and a second <b>Selected Item</b> (the batch queued for the
 *       transfer; its row stays UNticked, and <b>OK</b> answers "Please Select Item !!!" until it is ticked).</li>
 *   <li>OK adds the item to the form's grid: <b>Quantity</b> is {@code Itm.IssuedQuantity} (starts at 0). <b>Save</b> ({@code IUDSave()}) stores the transfer.</li>
 * </ul>
 * Saving is REAL: stock moves from the issuing store to the requesting store and the indent's pending quantity falls.
 */
public class CrossLocationTransfer extends BasePage {

    /** Every {@code /api/IssueToStore/} call that is not a plain Fetch, plus any API call that failed: {status, method, decoded url, body head}. */
    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean save = low.contains("/api/issuetostore/") && !low.contains("/fetch") || low.contains("/api/crosslocation");
            boolean bad = r.status() >= 400;
            if (!save && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (save) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public CrossLocationTransfer(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastRequesting = "", lastIssuing = "", lastIndent = "", lastIndentItem = "", lastBatchState = "", lastRow = "", lastQty = "", lastDialog = "", lastToast = "",
            lastSaveMessage = "", lastSaveId = "", lastTransNo = "", lastTotals = "";
    public int lastSaveStatus = -1;
    /** How many indents the last Search listed. */
    public int lastIndentCount = 0;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const headings=t=>[...document.querySelectorAll('*')].filter(e=>e.children.length===0 && vis(e) && norm(e.textContent)===t);"
          + "const tableAfter=h=>{ let el=h; for(let i=0;i<20&&el;i++){ el=el.nextElementSibling||(el.parentElement&&el.parentElement.nextElementSibling);"
          + "  if(el&&el.querySelector&&el.querySelector('table')) return el.querySelector('table'); if(el&&el.tagName==='TABLE') return el; } return null; };"
          + "const rowsOf=t=>t?[...t.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(x=>x):[];"
          + "const gridTable=(h,n)=>{ const hs=headings(h); const x=n<0? hs[hs.length+n] : hs[n]; return x? tableAfter(x) : null; };";

    private static final String FROM_STORE = "IssueToStore.SearchStoreID", TO_STORE = "IssueToStore.ToStore";
    private static final String MODAL_REQ = "IdentItemSearch.FromStore", MODAL_ISS = "IdentItemSearch.ToStore";
    private static final String HREF = "#/crosslocationsalelist";

    // ---- navigation ------------------------------------------------------------------------------------------

    private boolean linkVisible() {
        Object r = page.evaluate("(h) => {" + JS + " return !![...document.querySelectorAll('a[href]')].find(x=>vis(x) && (x.getAttribute('href')||'').toLowerCase()===h); }", HREF);
        return Boolean.TRUE.equals(r);
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (linkVisible()) return true; page.waitForTimeout(250); }
        return linkVisible();
    }

    /** Is the menu group with this label expanded? {@code 1} open, {@code 0} shut, {@code -1} not on screen. */
    private int groupState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickGroup(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    /**
     * Inventory &gt; Transfer &gt; Cross Location Transfer. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again,
     * and a click on Transfer straight after Inventory is lost), so Inventory gets a settling pause before Transfer is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!linkVisible()) {
                if (groupState("Transfer") < 0) {                              // Transfer is not on screen: Inventory is shut
                    if (groupState("Inventory") != 1) clickGroup("Inventory");
                    for (int i = 0; i < 48 && groupState("Transfer") < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);                                 // let the Inventory level finish building before Transfer is clicked
                }
                if (groupState("Transfer") == 0 && !linkVisible()) clickGroup("Transfer");
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__xlLink'; }", HREF);
                try { page.locator("#__xlLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("CrossLocationTransfer.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__xlLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll('button')].find(b=>/AddCrossLocationSale/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("CrossLocationTransfer.nav: the New button is not ready"); }
        waitForAngular(1000);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/crosslocationsalelist/?$"); }

    public boolean onForm() { return page.url().toLowerCase().matches(".*#/add-crosslocationsale/?$") && !selectVisible(FROM_STORE).isEmpty(); }

    private String selectVisible(String ng) {
        Object r = page.evaluate("(n) => {" + JS + " const e=[...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); return e? 'y' : ''; }", ng);
        return r == null ? "" : r.toString();
    }

    // ---- small helpers ---------------------------------------------------------------------------------------

    /** Tag the first visible button whose ng-click matches {@code re} and click it with a real mouse click. */
    private boolean clickByNg(String re, String what) {
        Object tagged = page.evaluate("(re) => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>new RegExp(re).test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__xlBtn'; return true; }", re);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the " + what + " button is not on screen"); return false; }
        try { page.locator("#__xlBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { problems.add("the " + what + " button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__xlBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    private String model(String ngModel, String expr) {
        Object v = page.evaluate("(a) => { const e=[...document.querySelectorAll(\"[ng-model='\"+a.n+\"']\")][0]; if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(a.x); return v==null?'':String(v); }catch(x){ return ''; } }", Map.of("n", ngModel, "x", expr));
        return v == null ? "" : v.toString().trim();
    }

    private String selectedText(String ng) {
        Object v = page.evaluate("(n) => {" + JS + " const e=[...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); if(!e) return ''; const o=e.options[e.selectedIndex]; const t=o? norm(o.textContent) : '';"
                + " return /^-*\\s*select\\s*-*$/i.test(t)? '' : t; }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** Choose the option of a select2-backed select whose text matches {@code want} ignoring case and spaces ("Pharmacy(central)" == "Pharmacy (Central)"). Returns the option's text, "" if none. */
    private String pickOption(String ng, String want) {
        Object t = page.evaluate("(a) => {" + JS
                + " const e=[...document.querySelectorAll(\"[ng-model='\"+a.n+\"']\")].find(vis)||document.querySelector(\"[ng-model='\"+a.n+\"']\"); if(!e) return '';"
                + " const k=s=>norm(s).toLowerCase().replace(/\\s+/g,'');"
                + " const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value,i:x.index})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=o.find(x=>k(x.t)===k(a.w)); if(!p) p=o.find(x=>k(x.t).indexOf(k(a.w))>=0); if(!p) return '';"
                + " e.selectedIndex=p.i; e.dispatchEvent(new Event('change',{bubbles:true})); try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', e.value); }catch(x){} try{ $(e).trigger('change'); }catch(x){} } return p.t; }", Map.of("n", ng, "w", want));
        return t == null ? "" : t.toString();
    }

    private List<String> rows(String heading, int nth) {
        Object r = page.evaluate("(a) => {" + JS + " return rowsOf(gridTable(a[0], a[1])); }", Arrays.asList(heading, nth));
        return toList(r);
    }

    /** Click the checkbox of row {@code idx} of a grid when its checked state is not already {@code want}; "" when there is no such row. */
    private String setRowTick(String heading, int nth, int idx, boolean want) {
        Object r = page.evaluate("(a) => {" + JS + " const t=gridTable(a[0], a[1]); const rs=t?[...t.querySelectorAll('tbody tr')]:[]; const row=rs[a[2]]; if(!row) return '';"
                + " const cb=row.querySelector('input[type=checkbox]'); if(!cb) return ''; const t0=norm(row.textContent); if(cb.checked!==a[3]) cb.click(); return t0; }",
                Arrays.asList(heading, nth, idx, want));
        return r == null ? "" : r.toString();
    }

    private boolean rowTicked(String heading, int nth, int idx) {
        Object r = page.evaluate("(a) => {" + JS + " const t=gridTable(a[0], a[1]); const row=t?[...t.querySelectorAll('tbody tr')][a[2]]:null; const cb=row&&row.querySelector('input[type=checkbox]'); return !!(cb && cb.checked); }",
                Arrays.asList(heading, nth, idx));
        return Boolean.TRUE.equals(r);
    }

    private boolean waitRows(String heading, int nth, int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (!rows(heading, nth).isEmpty()) return true; page.waitForTimeout(250); }
        return !rows(heading, nth).isEmpty();
    }

    // ---- list -> New -> stores -------------------------------------------------------------------------------

    /** Click New on the list and wait for the form. */
    public boolean clickNew() {
        problems.clear();
        if (!clickByNg("AddCrossLocationSale", "New")) return false;
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        waitForAngular(1500);
        if (!onForm()) problems.add("the Cross Location Transfer form did not open (" + page.url() + ")");
        return ok();
    }

    /** Select the <b>requesting</b> store (the form's From Store). */
    public String selectRequestingStore(String want) {
        problems.clear();
        try { page.waitForFunction("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e && e.options.length>1; }", FROM_STORE, new Page.WaitForFunctionOptions().setTimeout(10000)); }
        catch (Exception e) { problems.add("the From Store list has no options"); return ""; }
        lastRequesting = pickOption(FROM_STORE, want);
        if (lastRequesting.isEmpty()) { problems.add("the From Store list has no \"" + want + "\""); return ""; }
        waitForAngular(1500);
        String m = model(FROM_STORE, "IssueToStore.SearchStoreID");
        if (m.isEmpty() || "0".equals(m)) problems.add("the requesting store is not held by the model");
        return lastRequesting;
    }

    /** Select the <b>issuing</b> store (the form's To Store). */
    public String selectIssuingStore(String want) {
        problems.clear();
        try { page.waitForFunction("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e && e.options.length>1; }", TO_STORE, new Page.WaitForFunctionOptions().setTimeout(10000)); }
        catch (Exception e) { problems.add("the To Store list has no options"); return ""; }
        lastIssuing = pickOption(TO_STORE, want);
        if (lastIssuing.isEmpty()) { problems.add("the To Store list has no \"" + want + "\""); return ""; }
        waitForAngular(1500);
        String m = model(TO_STORE, "IssueToStore.ToStore");
        if (m.isEmpty() || "0".equals(m)) problems.add("the issuing store is not held by the model");
        return lastIssuing;
    }

    // ---- Get Indent dialog -----------------------------------------------------------------------------------

    /** Click Get Indent and wait for the Item Search dialog. The dialog pre-fills the two stores from the form; this checks that and corrects it if not. */
    public boolean clickGetIndent() {
        problems.clear();
        try { page.locator("#GetIndent").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Get Indent button could not be clicked"); return false; }
        try { page.waitForFunction("(n) => { const e=[...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(x=>x.offsetWidth||x.offsetHeight||x.getClientRects().length); return !!e; }",
                MODAL_REQ, new Page.WaitForFunctionOptions().setTimeout(15000)); }
        catch (Exception e) { problems.add("the Item Search dialog did not open"); return false; }
        waitForAngular(1200);
        if (!sameStore(selectedText(MODAL_REQ), lastRequesting)) { pickOption(MODAL_REQ, lastRequesting); waitForAngular(600); }
        if (!sameStore(selectedText(MODAL_ISS), lastIssuing)) { pickOption(MODAL_ISS, lastIssuing); waitForAngular(600); }
        if (!sameStore(selectedText(MODAL_REQ), lastRequesting)) problems.add("the dialog's Requesting Store shows \"" + selectedText(MODAL_REQ) + "\" instead of \"" + lastRequesting + "\"");
        if (!sameStore(selectedText(MODAL_ISS), lastIssuing)) problems.add("the dialog's Issuing Store shows \"" + selectedText(MODAL_ISS) + "\" instead of \"" + lastIssuing + "\"");
        return ok();
    }

    public String dialogStores() { return "Requesting Store \"" + selectedText(MODAL_REQ) + "\", Issuing Store \"" + selectedText(MODAL_ISS) + "\""; }

    private static boolean sameStore(String a, String b) {
        return a != null && b != null && !a.isEmpty() && a.replaceAll("\\s+", "").equalsIgnoreCase(b.replaceAll("\\s+", ""));
    }

    /** Click the dialog's Search; returns how many indents were listed (also in {@link #lastIndentCount}). */
    public int clickSearch() {
        problems.clear();
        lastIndentCount = 0;
        for (int attempt = 1; attempt <= 3; attempt++) {                       // the dialog's scope can still be initialising on the first click
            if (!clickByNg("SearchIndentItem", "Search")) return 0;
            if (waitRows("Selected Item", 0, 6000)) break;
            waitForAngular(800);
        }
        waitForAngular(500);
        lastIndentCount = rows("Selected Item", 0).size();
        if (lastIndentCount == 0) problems.add("Search listed no indents for " + dialogStores() + notice());
        return lastIndentCount;
    }

    /** Visible toast / dialog text, formatted as a suffix for a problem message. */
    private String notice() {
        String t = toastsNow();
        return t.isEmpty() ? "" : " — the screen said \"" + t + "\"";
    }

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    /** The indent headers listed by Search (top Selected Item grid). */
    public List<String> indentRows() { return rows("Selected Item", 0); }

    /**
     * Walk the indents and their items until one has batch stock at the issuing store: tick an indent (top <b>Selected Item</b>), tick one of its items (<b>Indent Item List</b>) and look for rows in
     * <b>Item Batch List</b> and the bottom <b>Selected Item</b>. An item with no stock is un-ticked and the next tried; an indent with none is un-ticked and the next tried. Leaves the winning
     * indent and item ticked. Fills {@link #lastIndent}, {@link #lastIndentItem} and {@link #lastBatchState}.
     *
     * @return true when a ticked item has a batch queued
     */
    public boolean selectIndentItemWithStock(int maxIndents) {
        problems.clear();
        lastIndent = lastIndentItem = lastBatchState = "";
        int indents = indentRows().size();
        if (indents == 0) { problems.add("there are no indents to pick from"); return false; }
        int tried = 0;
        for (int i = 0; i < Math.min(indents, maxIndents); i++) {
            if (i > 0) setRowTick("Selected Item", 0, i - 1, false);            // un-tick the previous indent
            String ind = setRowTick("Selected Item", 0, i, true);
            if (ind.isEmpty()) continue;
            tried++;
            waitRows("Indent Item List", 0, 6000);
            waitForAngular(400);
            int items = rows("Indent Item List", 0).size();
            for (int j = 0; j < Math.min(items, 10); j++) {
                String item = setRowTick("Indent Item List", 0, j, true);
                if (item.isEmpty()) continue;
                waitRows("Item Batch List", 0, 5000);
                waitRows("Selected Item", -1, 1500);
                waitForAngular(300);
                List<String> batch = rows("Item Batch List", 0), queued = rows("Selected Item", -1);
                boolean topIsBottom = queued.equals(rows("Selected Item", 0));  // guards the "only one Selected Item heading on screen" case
                if (!batch.isEmpty() && !queued.isEmpty() && !topIsBottom) {
                    lastIndent = ind.split(" ")[0];
                    lastIndentItem = item;
                    lastBatchState = "Item Batch List: " + batch.size() + " row(s) \"" + batch.get(0) + "\"; Selected Item (bottom): " + queued.size() + " row(s) \"" + queued.get(0) + "\"";
                    return true;
                }
                setRowTick("Indent Item List", 0, j, false);                    // no stock here: un-tick, try the next item
                waitForAngular(300);
            }
        }
        problems.add("none of the first " + Math.min(indents, maxIndents) + " indents (" + tried + " tried) has an item with batch stock at the issuing store");
        return false;
    }

    /** Make sure the Item Batch List row is ticked (the dialog usually ticks it itself when the item is ticked; clicking a ticked box would clear it). Returns the row text. */
    public String ensureBatchTicked() {
        problems.clear();
        String t = setRowTick("Item Batch List", 0, 0, true);
        waitForAngular(500);
        if (t.isEmpty()) { problems.add("Item Batch List has no row to tick"); return ""; }
        if (!rowTicked("Item Batch List", 0, 0)) problems.add("the Item Batch List row did not stay ticked");
        if (rows("Selected Item", -1).isEmpty()) problems.add("the bottom Selected Item table is empty after ticking the batch");
        return t;
    }

    /** Tick the bottom <b>Selected Item</b> row (the batch queued for transfer). Returns the row text. */
    public String tickSelectedItemRow() {
        problems.clear();
        String t = setRowTick("Selected Item", -1, 0, true);
        waitForAngular(500);
        if (t.isEmpty()) { problems.add("the bottom Selected Item table has no row to tick"); return ""; }
        if (!rowTicked("Selected Item", -1, 0)) problems.add("the bottom Selected Item row did not stay ticked");
        return t;
    }

    /** Click OK; waits for the dialog to close and the item to reach the form's grid. Returns true when the grid has a row. */
    public boolean clickOk() {
        problems.clear();
        if (!clickByNg("OKClick", "OK")) return false;
        long end = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < end && (dialogOpen() || gridRowCount() == 0)) page.waitForTimeout(250);
        waitForAngular(800);
        if (dialogOpen()) problems.add("the dialog stayed open after OK" + notice());
        else if (gridRowCount() == 0) problems.add("OK closed the dialog but no item reached the grid" + notice());
        return ok();
    }

    /** Close the Item Search dialog without choosing (the Close button). */
    public void closeDialog() {
        clickByNg("^Cancel\\(\\)", "Close");
        for (int i = 0; i < 20 && dialogOpen(); i++) page.waitForTimeout(250);
        waitForAngular(500);
        problems.clear();
    }

    private boolean dialogOpen() {
        Object r = page.evaluate("(n) => {" + JS + " return !![...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); }", MODAL_REQ);
        return Boolean.TRUE.equals(r);
    }

    // ---- the form's grid: quantity ---------------------------------------------------------------------------

    private static final String GRID_ROWS =
            "const gridRows=()=>[...document.querySelectorAll('table tbody tr')].filter(r=>vis(r) && r.querySelector(\"input[ng-model='Itm.IssuedQuantity']\"));";

    public int gridRowCount() {
        Object r = page.evaluate("() => {" + JS + GRID_ROWS + " return gridRows().length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** The first grid row's text plus its editable fields, e.g. {@code "1 PH40000246 Voren Supp ... | Quantity=3"}. */
    public String gridRowText() {
        Object r = page.evaluate("() => {" + JS + GRID_ROWS + " const r=gridRows()[0]; if(!r) return '';"
                + " const q=r.querySelector(\"input[ng-model='Itm.IssuedQuantity']\"); return norm(r.textContent) + ' | Quantity=' + (q? q.value : '?'); }");
        lastRow = r == null ? "" : r.toString();
        return lastRow;
    }

    /** The first grid row's cell texts in column order (Sr. No., Item Code, Item Name, Batch Code, Expiry Date, Indent Quantity, Pending Indent Quantity, Available Stock, ...). */
    public List<String> gridCells() {
        Object r = page.evaluate("() => {" + JS + GRID_ROWS + " const r=gridRows()[0]; if(!r) return []; return [...r.querySelectorAll('td')].map(td=>norm(td.textContent)); }");
        return toList(r);
    }

    /** Enter the Quantity of the first grid row (typed, then Tab so the totals recalculate). */
    public boolean enterQuantity(String qty) {
        problems.clear();
        Locator q = page.locator("input[ng-model='Itm.IssuedQuantity']").first();
        try {
            q.scrollIntoViewIfNeeded();
            q.click(new Locator.ClickOptions().setTimeout(5000));
            q.fill("", new Locator.FillOptions().setTimeout(5000));
            q.pressSequentially(qty, new Locator.PressSequentiallyOptions().setDelay(60));
            q.press("Tab");
        } catch (Exception e) { problems.add("the Quantity box could not be typed into"); return false; }
        waitForAngular(900);
        Object v = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='Itm.IssuedQuantity']\"); return e? e.value : ''; }");
        lastQty = v == null ? "" : v.toString().trim();
        if (!qty.equals(lastQty)) problems.add("the Quantity box shows \"" + lastQty + "\" instead of \"" + qty + "\"");
        String held = model("Itm.IssuedQuantity", "Itm.IssuedQuantity");
        if (!held.isEmpty() && !qty.equals(held.replaceAll("\\.0+$", ""))) problems.add("the quantity is not held by the model (" + held + ")");
        lastTotals = totals();
        gridRowText();
        return ok();
    }

    public String totals() {
        Object r = page.evaluate("() => { const g=n=>{ const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e? e.value : '?'; };"
                + " return 'Total Items ' + g('IssueToStore.TotalIssuedItems') + ', Total Amount ' + g('IssueToStore.TotalAmount') + ', Total Net Amount ' + g('IssueToStore.TotalNetAmount'); }");
        return r == null ? "" : r.toString();
    }

    // ---- save ------------------------------------------------------------------------------------------------

    private void installToastWatch() {
        page.evaluate("() => { window.__xlToasts=[]; if(window.__xlObs) window.__xlObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__xlToasts.includes(m)) window.__xlToasts.push(m); });"
                + " window.__xlObs=new MutationObserver(add); window.__xlObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__xlToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** A visible confirmation dialog's text ("" if none). */
    public String confirmText() {
        Object r = page.evaluate("() => {" + JS + " const d=[...document.querySelectorAll('.jconfirm, .ng-confirm-box')].filter(vis).map(x=>norm(x.textContent).replace(/^[^A-Za-z0-9]+/,''));"
                + " return d.length? d[d.length-1] : ''; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Click Save, answer YES if the screen asks for a confirmation, and wait for the toast. Returns the toast text ("" if none appeared); {@link #lastDialog} holds the confirmation's text
     * (empty when Save went straight through) and {@link #lastSaveStatus}/{@link #lastSaveMessage}/{@link #lastSaveId} the server's answer to the save call.
     */
    public String saveAndGetToast() {
        problems.clear();
        lastDialog = lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Locator save = page.locator("button[ng-click^='IUDSave']").first();
        try { save.scrollIntoViewIfNeeded(); save.click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        // a confirmation may or may not appear: answer it if it does
        for (int i = 0; i < 24; i++) {
            lastDialog = confirmText();
            if (!lastDialog.isEmpty()) break;
            if (!watchedToasts().isEmpty() && i > 4) break;
            page.waitForTimeout(250);
        }
        if (!lastDialog.isEmpty()) {
            Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('.jconfirm button, .ng-confirm-box button')].filter(vis).find(x=>/^(yes|ok)$/i.test(norm(x.textContent)));"
                    + " if(!b) return false; b.id='__xlYes'; return true; }");
            if (!Boolean.TRUE.equals(tagged)) problems.add("a confirmation (\"" + lastDialog + "\") has no YES button");
            else {
                try { page.locator("#__xlYes").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { problems.add("YES could not be clicked"); }
                finally { page.evaluate("() => { const e=document.getElementById('__xlYes'); if(e) e.removeAttribute('id'); }"); }
            }
        }
        for (int i = 0; i < 56; i++) {                                           // up to ~14 s for the toast
            lastToast = watchedToasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(250);
        }
        page.waitForTimeout(800);
        lastToast = watchedToasts();
        parseSaveAnswer();
        return lastToast;
    }

    private void parseSaveAnswer() {
        lastSaveStatus = -1;
        lastSaveMessage = lastSaveId = lastTransNo = "";
        String[] c = null;
        synchronized (saveCalls) {
            for (int i = saveCalls.size() - 1; i >= 0 && c == null; i--) if ("POST".equalsIgnoreCase(saveCalls.get(i)[1])) c = saveCalls.get(i);
            if (c == null && !failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1);
        }
        if (c == null) return;
        Matcher m = Pattern.compile("\"ResultStatus\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (m.find()) lastSaveStatus = Integer.parseInt(m.group(1));
        lastSaveMessage = jsonField(c[3], "Message");
        Matcher id = Pattern.compile("\"ResponseId\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (id.find()) lastSaveId = id.group(1);
        lastTransNo = jsonField(c[3], "TransNo");
        int http = Integer.parseInt(c[0]);
        if (http >= 400) lastSaveMessage = "HTTP " + http + " " + c[2].replace("https://devhis.sancyberhad.com", "") + " " + lastSaveMessage;
    }

    /** What the save call and any failed API call answered, for the report. */
    public String saveDiagnosis() {
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) {
            for (String[] c : saveCalls) sb.append("[").append(c[0]).append(" ").append(c[1]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(c[3].replaceAll("\\s+", " "), 0, Math.min(200, c[3].replaceAll("\\s+", " ").length())).append("] ");
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    /**
     * The toast is a success message AND the server did not refuse the save. The screen's own wording is "Cross location transfered sucessfully." (sic), so the match is on
     * {@code sucess}/{@code success}/{@code saved}/{@code added}, not the correctly spelled word alone.
     */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("su(c)?cess|saved|added").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    private static String jsonField(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json == null ? "" : json);
        return m.find() ? m.group(1) : "";
    }

    private static List<String> toList(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }
}
