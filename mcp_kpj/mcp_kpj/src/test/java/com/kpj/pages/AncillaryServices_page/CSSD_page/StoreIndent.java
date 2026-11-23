package com.kpj.pages.AncillaryServices_page.CSSD_page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ancillary Services &gt; CSSD &gt; <b>Store Indent</b> — Page Object ({@code #/CSSDStoreIndentList}, the Manual Indent form {@code #/add-StoreIndent}).
 *
 * <p>Confirmed live (devhis build v2026.11.15.469). The list screen (Indent List / Indent Details, search by store, dates, status) has two buttons, <b>Auto Indent</b> and <b>Manual Indent</b>
 * ({@code AddStoreIndent()}); there is NO separate "Manual Indent" tab - the button opens the form. The form has <b>Requesting Store</b> ({@code StoreIndent.SearchStoreID}) and <b>Issuing Store</b>
 * ({@code StoreIndent.ToStore}) - selects over select2, both blank at first - then <b>Get Items</b> ({@code OpenItemSearch()}), the item grid and <b>Remarks*</b> ({@code StoreIndent.Remarks}), <b>Save</b>
 * ({@code #SaveButton}, {@code saveStoreIndent()}).</p>
 *
 * <p><b>Get Items</b> opens the <b>Item Search</b> dialog: a <b>Store*</b> select ({@code ItemSearch.SearchStoreID}; it starts on the Requesting Store, choosing another does NOT search), filters, <b>Search</b>
 * ({@code SearchItem()} - answers {@code SearchItemForIndent} for that store, ToStore = the Issuing Store; up to 100 items a page) and the item table: tick ({@code Item.IsSelected}) | Item Code | Item Name |
 * Item Group | Generic Name | <b>Available Stock (From)</b> (the stock at the dialog's Store) | <b>Available Stock (To)</b> (the stock at the Issuing Store) | ... | Issue/Transfer. <b>OK</b>
 * ({@code OKClick()}) adds the ticked rows to the form's grid, or warns "Please Select Item !!!".</p>
 *
 * <p>The grid: Select ({@code Itm.isselect}) | Sr. No. | Item Code | Item Name | UOM ({@code Itm.uomid}) | <b>Unit Quantity</b> ({@code Itm.unitqty}, disabled when the item has no stock) | Conversion Factor |
 * Avl Quantity | <b>Indent Quantity</b> | <b>Minimum Indent Quantity</b> | Pending | Min | Max | ReOrder | Issue Transfer | WAC | Amount | Remark | Delete ({@code confirmDelete(Itm,$index)}: removes a not-yet-saved
 * item at once and says "Item deleted successfully"). <b>Indent Quantity is 0 for every item until a Unit Quantity is typed</b>: {@code OnUnitQtyChange} (on blur) sets it to Unit Quantity x conversion factor, and refuses
 * - warning "Indent quantity cannot be less than the defined Minimum Indent Quantity for this item." and resetting the quantity to 0 - when that is below the item's Minimum Indent Quantity (20 on one item, 0 on all
 * others). So an item can only take a Unit Quantity of 1 when its Minimum Indent Quantity is 1 or less; the loop of the test rejects the others.</p>
 *
 * <p><b>Save</b> ({@code ExecFlag SaveIndent}) needs the form valid (validationEngine, Remarks), at least one item, a Unit Quantity, different stores, stock in the item; on ResultStatus 1 it toasts "Store Indent
 * Details Saved Successfully", opens the <b>Indent Number</b> dialog ({@code #IndentNumberList}, OK) AND opens the report at once with {@code window.open(.../frmIndentPrintReport.aspx?...&IndentId=...)} - so the report
 * tab is already open when the dialog is answered.</p>
 *
 * <p>Saving is REAL: it creates a Store Indent.</p>
 */
public class StoreIndent extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            String post = r.request().postData() == null ? "" : r.request().postData();
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && post.contains("\"ExecFlag\":\"SaveIndent\"");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 1200 ? body.substring(0, 1200) : body, post};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public StoreIndent(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not do (empty = it held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastToast = "", lastSaveMessage = "", lastTransNo = "", lastResponseId = "", lastIndentNumbers = "", lastDialogStore = "", lastRequestingStore = "", lastIssuingStore = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const dlg=()=>[...document.querySelectorAll('.modal')].filter(vis).filter(m=>/Item Search/.test(m.textContent)).pop() || null;"
          + "const dlgTbl=()=>{ const m=dlg(); return m? [...m.querySelectorAll('table')].find(x=>/Item Code/.test(x.textContent) && x.querySelector('tbody')) : null; };"
          + "const dlgRows=()=>{ const t=dlgTbl(); return t? [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox]')) : []; };"
          + "const gridTbl=()=>[...document.querySelectorAll('table')].find(x=>vis(x) && !x.closest('.modal') && /Minimum\\s+Indent\\s+Quantity/.test(x.textContent));"
          + "const gridRows=()=>{ const t=gridTbl(); return t? [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('td') && r.querySelector('input[ng-model=\"Itm.unitqty\"]')) : []; };";

    private static final String HREF = "#/cssdstoreindentlist";
    private static final String PARENT = "Ancillary Services", GROUP = "CSSD";

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

    private int topState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickTop(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    private int innerState(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return -1; const pli=pa.closest('li'); if(!pli) return -1;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return -1; const li=c.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }",
                Arrays.asList(parent, label));
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickInner(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return false; c.click(); return true; }", Arrays.asList(parent, label));
        return Boolean.TRUE.equals(r);
    }

    /** Ancillary Services &gt; CSSD &gt; Store Indent. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Ancillary Services')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !linkVisible()) clickInner(PARENT, GROUP);
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__siLink'; }", HREF);
                try { page.locator("#__siLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("CSSD StoreIndent.nav: click failed - " + e.getMessage().split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__siLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !manualIndentButtonShown(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    private boolean manualIndentButtonShown() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('button')].some(b=>vis(b) && (b.getAttribute('ng-click')||'').indexOf('AddStoreIndent')===0); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/cssdstoreindentlist/?$") && manualIndentButtonShown(); }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS + " const e=document.querySelector(\"select[ng-model='StoreIndent.ToStore']\"); return !!e && !!e.options && e.options.length>1; }");
        return Boolean.TRUE.equals(r) && page.url().toLowerCase().contains("#/add-storeindent");
    }

    /** True when the list screen has a real "Manual Indent" tab (it has not: it is the button that opens the form). */
    public boolean hasManualIndentTab() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('li,a,[role=tab],.nav-tabs *')].some(x=>vis(x) && /^\\s*manual\\s*indent\\s*$/i.test(norm(x.textContent)) && x.tagName!=='BUTTON' && !x.closest('button')); }");
        return Boolean.TRUE.equals(r);
    }

    /** The control labelled Manual Indent, "button" or "tab", with its handler. */
    public String manualIndentControl() {
        Object r = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].find(x=>vis(x) && (x.getAttribute('ng-click')||'').indexOf('AddStoreIndent')===0); return b? 'button \"'+norm(b.textContent)+'\" (ng-click '+b.getAttribute('ng-click')+')' : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click the Manual Indent button and wait for the form. */
    public boolean clickManualIndent() {
        problems.clear();
        try { page.locator("button[ng-click^='AddStoreIndent']").first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("the Manual Indent button could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        for (int i = 0; i < 40 && !formOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        if (!formOpen()) problems.add("the Manual Indent form did not open (" + page.url() + ")");
        return ok();
    }

    // ---- stores ------------------------------------------------------------------------------------------------------------

    private String model(String sel, String expr) {
        Object r = page.evaluate("(a) => { try { const e=document.querySelector(a[0]); const sc=angular.element(e).scope(); const v=sc.$eval(a[1]); return v==null? '' : String(v); } catch(x) { return ''; } }", Arrays.asList(sel, expr));
        return r == null ? "" : r.toString();
    }

    private static String digits(String v) { return v == null ? "" : v.replaceAll("[^0-9]", ""); }

    /** The real options (placeholder excluded) of a select. */
    public List<String> options(String ngModel) {
        Object r = page.evaluate("(m) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+m+\"']\"); return e? [...e.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*\\s*select/i.test(t)) : []; }", ngModel);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Select an option of one of the form's selects with a real selection (the first of {@code preferred} that is offered) and check the screen's model holds it. Returns the option chosen, "" on failure. */
    private String selectStore(String ngModel, String... preferred) {
        problems.clear();
        List<String> all = options(ngModel);
        if (all.isEmpty()) { problems.add("the select " + ngModel + " has no options"); return ""; }
        String pick = null;
        pickLoop:
        for (String p : preferred) for (String a : all) if (a.equalsIgnoreCase(p)) { pick = a; break pickLoop; }
        if (pick == null) { problems.add("none of " + Arrays.toString(preferred) + " is offered by " + ngModel + " (offered " + all.size() + " options)"); return ""; }
        try { page.locator("select[ng-model='" + ngModel + "']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(pick), new Locator.SelectOptionOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("\"" + pick + "\" could not be selected: " + e.getMessage().split("\n")[0]); return ""; }
        waitForAngular(900);
        Object shown = page.evaluate("(m) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+m+\"']\"); return e && e.selectedIndex>=0? norm(e.options[e.selectedIndex].textContent) : ''; }", ngModel);
        String mv = model("select[ng-model='" + ngModel + "']", ngModel);
        if (shown == null || !pick.equals(shown.toString())) { problems.add(ngModel + " shows \"" + shown + "\" instead of \"" + pick + "\""); return ""; }
        if (mv.isEmpty() || mv.equals("0")) { problems.add("the screen's model " + ngModel + " is empty after selecting \"" + pick + "\""); return ""; }
        return pick;
    }

    public String selectRequestingStore(String... preferred) { lastRequestingStore = selectStore("StoreIndent.SearchStoreID", preferred); return lastRequestingStore; }

    public String selectIssuingStore(String... preferred) { lastIssuingStore = selectStore("StoreIndent.ToStore", preferred); return lastIssuingStore; }

    // ---- Get Items dialog ---------------------------------------------------------------------------------------------------------

    public boolean dialogOpen() {
        Object r = page.evaluate("() => {" + JS + " return !!dlg(); }");
        return Boolean.TRUE.equals(r);
    }

    /** Click Get Items and wait for the Item Search dialog. */
    public boolean clickGetItems() {
        problems.clear();
        try { page.locator("button[ng-click^='OpenItemSearch']").first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("Get Items could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        for (int i = 0; i < 40 && !dialogOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1200);
        if (!dialogOpen()) problems.add("the Item Search dialog did not open");
        return ok();
    }

    /** Select the dialog's Store* (a real selection; choosing it does not search). Returns the store chosen, "" on failure. */
    public String selectDialogStore(String... preferred) {
        problems.clear();
        List<String> all = options("ItemSearch.SearchStoreID");
        String pick = null;
        pickLoop:
        for (String p : preferred) for (String a : all) if (a.equalsIgnoreCase(p)) { pick = a; break pickLoop; }
        if (pick == null) { problems.add("none of " + Arrays.toString(preferred) + " is offered by the dialog's Store* (" + all.size() + " options)"); return ""; }
        try { page.locator("select[ng-model='ItemSearch.SearchStoreID']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(pick), new Locator.SelectOptionOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("the dialog's Store* \"" + pick + "\" could not be selected: " + e.getMessage().split("\n")[0]); return ""; }
        waitForAngular(900);
        String mv = model("select[ng-model='ItemSearch.SearchStoreID']", "ItemSearch.SearchStoreID");
        if (mv.isEmpty() || mv.equals("0")) { problems.add("the dialog's model ItemSearch.SearchStoreID is empty after selecting \"" + pick + "\""); return ""; }
        lastDialogStore = pick;
        return pick;
    }

    public int dialogRowCount() {
        Object r = page.evaluate("() => {" + JS + " return dlgRows().length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** The dialog's paging line ("Total Items: 965 ... Page 1 of 10"), "" when none. */
    public String dialogPager() {
        Object r = page.evaluate("() => {" + JS + " const m=dlg(); if(!m) return ''; const t=norm(m.textContent); const x=/Total Items:\\s*\\d+.*?Page\\s*\\d+\\s*of\\s*\\d+/i.exec(t); return x? x[0].replace(/<<\\s*Previous/,'').trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click the dialog's Search and wait for rows. Returns the number of rows listed (0 = none). */
    public int clickDialogSearch() {
        problems.clear();
        try { page.locator(".modal button[ng-click^='SearchItem']").first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("the dialog's Search could not be clicked: " + e.getMessage().split("\n")[0]); return 0; }
        long end = System.currentTimeMillis() + 60000;
        int n = 0;
        while (System.currentTimeMillis() < end) {
            n = dialogRowCount();
            if (n > 0) break;
            page.waitForTimeout(500);
        }
        waitForAngular(800);
        return dialogRowCount();
    }

    /** One item of the dialog: code, name, stock at the dialog's Store (From), stock at the Issuing Store (To). */
    public static final class Item {
        public final String code, name;
        public final double from, to;
        Item(String code, String name, double from, double to) { this.code = code; this.name = name; this.from = from; this.to = to; }
        @Override public String toString() { return code + " " + name + " (From " + fmt(from) + ", To " + fmt(to) + ")"; }
        private static String fmt(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }
    }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return -1; }
    }

    /** The items the dialog lists now (current page). */
    public List<Item> dialogItems() {
        Object r = page.evaluate("() => {" + JS + " return JSON.stringify(dlgRows().map(r=>{ const c=[...r.querySelectorAll('td')]; return [norm(c[1].textContent), norm(c[2].textContent), norm(c[5].textContent), norm(c[6].textContent)]; })); }");
        List<Item> out = new ArrayList<>();
        try {
            JsonArray a = JsonParser.parseString(String.valueOf(r)).getAsJsonArray();
            for (JsonElement e : a) { JsonArray c = e.getAsJsonArray(); out.add(new Item(c.get(0).getAsString(), c.get(1).getAsString(), num(c.get(2).getAsString()), num(c.get(3).getAsString()))); }
        } catch (Exception ignore) { }
        return out;
    }

    /**
     * The item with the most available stock - "Available Stock (From)", the figure the grid then shows as Avl Quantity, ties broken by the (To) stock - among the items not in {@code exclude} and with stock.
     * When {@code pinnedCode} is not empty and listed, that item is returned instead.
     */
    public Item pickMostStock(Collection<String> exclude, String pinnedCode, List<Item> items) {
        if (pinnedCode != null && !pinnedCode.isEmpty()) for (Item i : items) if (i.code.equalsIgnoreCase(pinnedCode) && !exclude.contains(i.code)) return i;
        Item best = null;
        for (Item i : items) {
            if (exclude.contains(i.code) || i.from <= 0) continue;
            if (best == null || i.from > best.from || (i.from == best.from && i.to > best.to)) best = i;
        }
        return best;
    }

    /** Tick the checkbox of the dialog row with this item code (a real click) and check the row's own data holds it. */
    public boolean tickItem(String code) {
        problems.clear();
        Object tagged = page.evaluate("(c) => {" + JS + " const r=dlgRows().find(r=>norm(r.querySelectorAll('td')[1].textContent)===c); if(!r) return false; const cb=r.querySelector('input[ng-model=\"Item.IsSelected\"]'); if(!cb) return false; cb.id='__siTick'; r.scrollIntoView({block:'center'}); return true; }", code);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("item " + code + " is not listed in the dialog"); return false; }
        try { page.locator("#__siTick").check(new Locator.CheckOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the checkbox of " + code + " could not be ticked: " + e.getMessage().split("\n")[0]); }
        waitForAngular(600);
        Object held = page.evaluate("() => { const e=document.getElementById('__siTick'); if(!e) return ''; let m=''; try{ m=String(angular.element(e).scope().Item.IsSelected); }catch(x){} const r=e.checked+'/'+m; e.removeAttribute('id'); return r; }");
        if (held == null || !"true/true".equals(held.toString())) problems.add("the tick of " + code + " is not held (checked/model " + held + ")");
        return ok();
    }

    /** Click the dialog's OK and wait for it to close and the item to reach the grid. Returns the number of grid rows afterwards, -1 on failure. */
    public int clickDialogOk() {
        problems.clear();
        int before = gridRowCount();
        try { page.locator(".modal button[ng-click^='OKClick']").first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("the dialog's OK could not be clicked: " + e.getMessage().split("\n")[0]); return -1; }
        for (int i = 0; i < 40 && dialogOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1200);
        for (int i = 0; i < 24 && gridRowCount() <= before; i++) page.waitForTimeout(250);
        if (dialogOpen()) problems.add("the Item Search dialog is still open after OK");
        int now = gridRowCount();
        if (now <= before) problems.add("the grid has " + now + " row(s) after OK (" + before + " before)");
        return ok() ? now : -1;
    }

    /** Close the dialog without choosing (the Close button). */
    public void closeDialog() {
        try { page.locator(".modal button[ng-click^='Cancel']:has-text('Close')").first().click(new Locator.ClickOptions().setTimeout(4000)); } catch (Exception ignore) { }
        waitForAngular(600);
    }

    // ---- the grid ----------------------------------------------------------------------------------------------------------------

    public int gridRowCount() {
        Object r = page.evaluate("() => {" + JS + " return gridRows().length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** The grid rows as header -> value maps (inputs by value, selects by the option shown, else the cell's text). */
    public List<JsonObject> gridRows() {
        Object r = page.evaluate("() => {" + JS + " const t=gridTbl(); if(!t) return '[]'; const hs=[...t.querySelectorAll('thead th')].map(h=>norm(h.textContent));"
                + " return JSON.stringify(gridRows().map(r=>{ const c=[...r.querySelectorAll('td')]; const o={}; hs.forEach((h,i)=>{ const td=c[i]; if(!td) return; const inp=td.querySelector('input:not([type=checkbox]):not([type=radio])'); const sel=td.querySelector('select');"
                + " o[h]= inp? inp.value : sel? norm((sel.options[sel.selectedIndex]||{}).textContent) : norm(td.textContent); }); const cb=r.querySelector('input[ng-model^=\"Itm.isselect\"]'); o.__ticked=cb? String(cb.checked) : ''; o.__unitDisabled=String(r.querySelector('input[ng-model=\"Itm.unitqty\"]').disabled); return o; })); }");
        List<JsonObject> out = new ArrayList<>();
        try { for (JsonElement e : JsonParser.parseString(String.valueOf(r)).getAsJsonArray()) out.add(e.getAsJsonObject()); } catch (Exception ignore) { }
        return out;
    }

    /** A cell of a grid row, "" when absent. */
    public static String cell(JsonObject row, String header) {
        return row != null && row.has(header) && !row.get(header).isJsonNull() ? row.get(header).getAsString().replace("Batch Stock", "").trim() : "";
    }

    /** A one-line description of a grid row for the report. */
    public static String describeRow(JsonObject r) {
        return cell(r, "Item Code") + " " + cell(r, "Item Name") + ": Avl Quantity " + cell(r, "Avl Quantity") + ", Indent Quantity " + cell(r, "Indent Quantity") + ", Minimum Indent Quantity " + cell(r, "Minimum Indent Quantity")
                + ", Pending " + cell(r, "Pending Quantity") + ", Min " + cell(r, "Min Quantity") + ", Max " + cell(r, "Max Quantity") + ", ReOrder " + cell(r, "ReOrder Quantity");
    }

    /** Click the row's Delete (a not-yet-saved item is removed at once) and wait for the row to go. */
    public boolean deleteRow(int index) {
        problems.clear();
        int before = gridRowCount();
        armToasts();
        Object tagged = page.evaluate("(i) => {" + JS + " const r=gridRows()[i]; if(!r) return false; const b=r.querySelector('[ng-click^=\"confirmDelete\"]'); if(!b) return false; b.id='__siDel'; r.scrollIntoView({block:'center'}); return true; }", index);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("row " + (index + 1) + " has no Delete control"); return false; }
        try { page.locator("#__siDel").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Delete could not be clicked: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__siDel'); if(e) e.removeAttribute('id'); }");
        for (int i = 0; i < 24 && gridRowCount() >= before; i++) page.waitForTimeout(250);
        waitForAngular(500);
        if (gridRowCount() >= before) problems.add("the row is still in the grid after Delete (" + gridRowCount() + " row(s))");
        return ok();
    }

    /** Tick the row's Select checkbox (a real click) and check the row's own data holds it. */
    public boolean tickRow(int index) {
        problems.clear();
        Object tagged = page.evaluate("(i) => {" + JS + " const r=gridRows()[i]; if(!r) return false; const b=r.querySelector('input[ng-model^=\"Itm.isselect\"]'); if(!b) return false; b.id='__siRowTick'; r.scrollIntoView({block:'center'}); return true; }", index);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("row " + (index + 1) + " has no Select checkbox"); return false; }
        try { page.locator("#__siRowTick").check(new Locator.CheckOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the row's checkbox could not be ticked: " + e.getMessage().split("\n")[0]); }
        waitForAngular(500);
        Object held = page.evaluate("() => { const e=document.getElementById('__siRowTick'); if(!e) return ''; let m=''; try{ m=String(angular.element(e).scope().Itm[e.getAttribute('ng-model').split('.')[1]]); }catch(x){} const r=e.checked+'/'+m; e.removeAttribute('id'); return r; }");
        if (held == null || !"true/true".equals(held.toString())) problems.add("the row's tick is not held (checked/model " + held + ")");
        return ok();
    }

    /** Enter the Unit Quantity in the row (a real fill + Tab, which fires the screen's own blur calculation) and return what the row then shows, "" on failure. */
    public boolean enterUnitQuantity(int index, String qty) {
        problems.clear();
        Object tagged = page.evaluate("(i) => {" + JS + " const r=gridRows()[i]; if(!r) return false; const b=r.querySelector('input[ng-model=\"Itm.unitqty\"]'); if(!b) return false; if(b.disabled) return 'disabled'; b.id='__siQty'; r.scrollIntoView({block:'center'}); return true; }", index);
        if ("disabled".equals(String.valueOf(tagged))) { problems.add("the Unit Quantity of row " + (index + 1) + " is disabled (the item has no stock)"); return false; }
        if (!Boolean.TRUE.equals(tagged)) { problems.add("row " + (index + 1) + " has no Unit Quantity box"); return false; }
        armToasts();
        try {
            Locator q = page.locator("#__siQty");
            q.click(new Locator.ClickOptions().setTimeout(5000));
            q.fill(qty, new Locator.FillOptions().setTimeout(5000));
            q.press("Tab", new Locator.PressOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the Unit Quantity could not be typed: " + e.getMessage().split("\n")[0]); }
        waitForAngular(900);
        page.evaluate("() => { const e=document.getElementById('__siQty'); if(e) e.removeAttribute('id'); }");
        List<JsonObject> rows = gridRows();
        JsonObject row = index < rows.size() ? rows.get(index) : null;
        if (row == null) { problems.add("the row is gone after typing the quantity"); return false; }
        String shown = cell(row, "Unit Quantity");
        if (!shown.equals(qty)) problems.add("the Unit Quantity box shows \"" + shown + "\" instead of " + qty + (toasts().isEmpty() ? "" : " (the screen said: " + toasts() + ")"));
        return ok();
    }

    /** The UOM choices of the row (placeholder excluded). */
    public List<String> uomOptions(int index) {
        Object r = page.evaluate("(i) => {" + JS + " const r=gridRows()[i]; const s=r&&r.querySelector('select[ng-model=\"Itm.uomid\"]'); return s? [...s.options].map(o=>norm(o.textContent)).filter(t=>t) : []; }", index);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Select the row's UOM (the first real choice, or {@code preferred} when offered) with a real selection. Returns the UOM, "" on failure. */
    public String selectUom(int index, String preferred) {
        problems.clear();
        List<String> all = uomOptions(index);
        if (all.isEmpty()) { problems.add("the row has no UOM choices"); return ""; }
        String pick = all.get(0);
        for (String a : all) if (a.equalsIgnoreCase(preferred)) { pick = a; break; }
        Object tagged = page.evaluate("(i) => {" + JS + " const r=gridRows()[i]; const s=r&&r.querySelector('select[ng-model=\"Itm.uomid\"]'); if(!s) return false; s.id='__siUom'; return true; }", index);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the row has no UOM select"); return ""; }
        try { page.locator("#__siUom").selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(pick), new Locator.SelectOptionOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the UOM \"" + pick + "\" could not be selected: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__siUom'); if(e) e.removeAttribute('id'); }");
        waitForAngular(700);
        return ok() ? pick : "";
    }

    // ---- remarks, Save -----------------------------------------------------------------------------------------------------------------

    public boolean enterRemarks(String text) { return enterRemarks("StoreIndent.Remarks", text); }

    /** Type the Remarks of the form whose box is bound to {@code ngModel} (StoreIndent.Remarks on Manual Indent, AutoIndent.Remarks on Auto Indent). */
    public boolean enterRemarks(String ngModel, String text) {
        problems.clear();
        Locator m = page.locator("textarea[ng-model='" + ngModel + "']").first();
        try {
            m.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            m.click(new Locator.ClickOptions().setTimeout(5000));
            m.fill(text, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the Remarks could not be typed: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(500);
        String box = m.inputValue();
        String mv = model("textarea[ng-model='" + ngModel + "']", ngModel);
        if (!text.equals(box)) problems.add("the Remarks box shows \"" + box + "\" instead of the text typed");
        else if (!text.equals(mv)) problems.add("the Remarks are not held by the screen's model (\"" + mv + "\")");
        return ok();
    }

    /** Clear the toasts still on screen and start recording new ones. */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__siToasts=[]; if(window.__siObs) window.__siObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__siToasts.includes(m)) window.__siToasts.push(m); });"
                + " window.__siObs=new MutationObserver(add); window.__siObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__siToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Save (a real click) and return how many tabs were open just before (the report tab is opened by the save itself). The toasts are recorded from this moment. */
    public int clickSave() { return clickSave("#SaveButton"); }

    /** Click Save Indent on the Auto Indent screen; same bookkeeping as {@link #clickSave()}. */
    public int clickSaveIndent() { return clickSave("button[ng-click^='IUDAutoIndent']"); }

    private int clickSave(String selector) {
        problems.clear();
        lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        lastSaveStatus = -1; lastSaveMessage = ""; lastTransNo = ""; lastResponseId = "";
        int tabsBefore = page.context().pages().size();
        armToasts();
        Locator b = page.locator(selector).first();
        try {
            b.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            b.click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("Save could not be clicked: " + e.getMessage().split("\n")[0]); }
        return tabsBefore;
    }

    /** Wait for a toast after Save (up to {@code ms}); also reads the save call's answer. Returns the toasts shown ("" if none). */
    public String awaitToast(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            lastToast = toasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(300);
        }
        page.waitForTimeout(800);
        lastToast = toasts();
        parseSaveAnswer();
        return lastToast;
    }

    private void parseSaveAnswer() {
        String[] c = null;
        synchronized (saveCalls) { if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1); else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1); }
        if (c == null) return;
        Matcher m = Pattern.compile("\"ResultStatus\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (m.find()) lastSaveStatus = Integer.parseInt(m.group(1));
        Matcher msg = Pattern.compile("\"Message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(c[3]);
        if (msg.find()) lastSaveMessage = msg.group(1);
        Matcher tn = Pattern.compile("\"TransNo\"\\s*:\\s*\"([^\"]*)\"").matcher(c[3]);
        if (tn.find()) lastTransNo = tn.group(1);
        Matcher ri = Pattern.compile("\"ResponseId\"\\s*:\\s*\"?(\\d+)").matcher(c[3]);
        if (ri.find()) lastResponseId = ri.group(1);
        int http = Integer.parseInt(c[0]);
        if (http >= 400) lastSaveMessage = "HTTP " + http + " " + c[2].replaceAll("^https?://[^/]+", "") + " " + lastSaveMessage;
    }

    public boolean savedByServer() { return lastSaveStatus == 1; }

    /** What the save call and any failed API call answered, for the report. */
    public String saveDiagnosis() {
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) {
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(260, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    /** The toast is a success message and the server did not refuse the save. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|created|submitted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !t.contains("unexpected");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    // ---- Auto Indent (route #/AutoIndent, its own screen: Requesting Store* AutoIndent.FromStoreID, Issuing Store* AutoIndent.ToStoreID, Search getStockAutoIndentSearchList(), Save Indent IUDAutoIndent()) -------------

    /** True when the list screen has a real "Auto Indent" tab (it has not: it is the button that opens the screen). */
    public boolean hasAutoIndentTab() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('li,a,[role=tab],.nav-tabs *')].some(x=>vis(x) && /^\\s*auto\\s*indent\\s*$/i.test(norm(x.textContent)) && x.tagName!=='BUTTON' && !x.closest('button')); }");
        return Boolean.TRUE.equals(r);
    }

    /** The control labelled Auto Indent on the list screen, with its handler. */
    public String autoIndentControl() {
        Object r = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].find(x=>vis(x) && (x.getAttribute('ng-click')||'').indexOf('openautoindent')===0); return b? 'button \"'+norm(b.textContent)+'\" (ng-click '+b.getAttribute('ng-click')+')' : ''; }");
        return r == null ? "" : r.toString();
    }

    public boolean autoFormOpen() {
        Object r = page.evaluate("() => {" + JS + " const e=document.querySelector(\"select[ng-model='AutoIndent.ToStoreID']\"); return !!e && !!e.options && e.options.length>1; }");
        return Boolean.TRUE.equals(r) && page.url().toLowerCase().contains("#/autoindent");
    }

    /** Click the Auto Indent button and wait for the Auto Indent screen. */
    public boolean clickAutoIndent() {
        problems.clear();
        try { page.locator("button[ng-click^='openautoindent']").first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("the Auto Indent button could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        for (int i = 0; i < 40 && !autoFormOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1200);
        if (!autoFormOpen()) problems.add("the Auto Indent screen did not open (" + page.url() + ")");
        return ok();
    }

    public String selectAutoRequestingStore(String... preferred) { lastRequestingStore = selectStore("AutoIndent.FromStoreID", preferred); return lastRequestingStore; }

    public String selectAutoIssuingStore(String... preferred) { lastIssuingStore = selectStore("AutoIndent.ToStoreID", preferred); return lastIssuingStore; }

    /** Click Search on the Auto Indent screen and wait for rows. Returns the number of rows listed (0 = none). */
    public int clickAutoSearch() {
        problems.clear();
        try { page.locator("button[ng-click^='getStockAutoIndentSearchList']").first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("Search could not be clicked: " + e.getMessage().split("\n")[0]); return 0; }
        long end = System.currentTimeMillis() + 40000;
        while (System.currentTimeMillis() < end && gridRowCount() == 0) page.waitForTimeout(400);
        waitForAngular(800);
        return gridRowCount();
    }

    /** The Auto Indent "Total Records: n" line, "" when none. */
    public String autoTotalRecords() {
        Object r = page.evaluate("() => {" + JS + " const x=/Total Records:\\s*\\d+/i.exec(norm(document.body.innerText)); return x? x[0] : ''; }");
        return r == null ? "" : r.toString();
    }

    /** A one-line description of an Auto Indent grid row for the report. */
    public static String describeAutoRow(JsonObject r) {
        return cell(r, "Item Code") + " " + cell(r, "Item Name") + ": Unit Quantity " + cell(r, "Unit Quantity") + ", Conversion Factor " + cell(r, "Conversion Factor") + ", Available Qty " + cell(r, "Available Qty")
                + ", Min " + cell(r, "Minimum Qty") + ", Max " + cell(r, "Maximum Qty") + ", ReOrder " + cell(r, "ReOrder Qty") + ", Indent Quantity " + cell(r, "Indent Quantity")
                + ", Minimum Indent Quantity " + cell(r, "Minimum Indent Quantity") + ", Pending " + cell(r, "Pending Qty");
    }

    // ---- Indent Number dialog -------------------------------------------------------------------------------------------------------------

    private boolean indentDialogShown() {
        Object r = page.evaluate("() => {" + JS + " const m=document.getElementById('IndentNumberList'); return !!m && vis(m); }");
        return Boolean.TRUE.equals(r);
    }

    /** Wait for the Indent Number dialog; returns its text ("" when it never appeared). */
    public String awaitIndentNumberDialog(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end && !indentDialogShown()) page.waitForTimeout(300);
        if (!indentDialogShown()) return "";
        page.waitForTimeout(500);
        Object t = page.evaluate("() => {" + JS + " const m=document.getElementById('IndentNumberList'); return m? norm(m.textContent) : ''; }");
        Object n = page.evaluate("() => { try { const e=document.getElementById('SaveButton'); const v=angular.element(e).scope().StoreIndent.IndentNumberList; return v==null? '' : String(v); } catch(x) { return ''; } }");
        lastIndentNumbers = n == null ? "" : n.toString();
        return t == null ? "" : t.toString();
    }

    /** Click OK in the Indent Number dialog and check it closes. */
    public boolean clickIndentNumberOk() {
        problems.clear();
        try { page.locator("#IndentNumberList button:has-text('OK')").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("OK in the Indent Number dialog could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        for (int i = 0; i < 24 && indentDialogShown(); i++) page.waitForTimeout(250);
        waitForAngular(600);
        if (indentDialogShown()) problems.add("the Indent Number dialog is still open after OK");
        return ok();
    }
}
