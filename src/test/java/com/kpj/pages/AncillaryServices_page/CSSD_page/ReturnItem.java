package com.kpj.pages.AncillaryServices_page.CSSD_page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ancillary Services &gt; CSSD &gt; <b>Return Item</b> — Page Object (list {@code #/ReturnItemListCSSD}, form {@code #/add-ReturnItem}).
 *
 * <p>Confirmed live (devhis build v2026.11.15.469). The list screen (From Store / To Store / dates, Search, return grids) has <b>New</b> ({@code AddReturnItem()}), which opens the form:</p>
 * <ul>
 *   <li><b>Return From Store</b> ({@code ReturnItem.FromStore}) and <b>Return To Store</b> ({@code ReturnItem.ToStore}) - selects over select2, blank at first; <b>Type Of Return*</b>
 *       ({@code ReturnItem.TypeOfReturn}: By Mistake, Others, None Remark - plus 51 "Auto type of return nnn" test rows); the With Transfer / Direct Transfer radios; <b>Get Issue</b>
 *       ({@code #GetIssue}, {@code OpenIssuedetailsList()}, needs the To Store); <b>Remarks*</b> ({@code ReturnItem.remark}, mandatory when the type is "Others"); <b>Save</b> ({@code IUDSave()}).</li>
 *   <li><b>Get Issue</b> opens the <b>Issue Item Search</b> dialog: its From / To stores (disabled) are the form's stores SWAPPED (the original transfer went To Store -&gt; From Store), an Issue Number box,
 *       <b>Search</b> ({@code SearchIssueItem()}, {@code ExecFlag GetIssuedItemSearch}) filling <b>Issue Item List</b> (ui-grid: Issuenumber | Issuedatetime | Patient Name | From Store Name | To Store Name/MRN |
 *       Totalamount | Totalmrp | Receivedby | Remark) and, below it, <b>Selected Item</b> (a table: tick {@code Itm.IsSelected} | Item Code | Item Name | Batch Code | Expiry Date | Available Stock | Conversion |
 *       Company Code | Selling Price | Purchase Rate). A click on an issue row fetches its items ({@code GetIssueStockItemList}) into Selected Item; <b>OK</b> ({@code OKClick()}) moves the ticked ones to the form
 *       ("Please Select Item !!!" when none is).</li>
 *   <li>The form's table (No | Item Name | Batch Code | Expiry Date | Transfer Quantity | Received Quantity | Available Stock | <b>Return Quantity</b> ({@code Itm.ReturnQuantity}, {@code RowTotal}) | Disc % |
 *       Disc Amt | Selling Price | Total Amount | delete).</li>
 *   <li><b>Save</b> ({@code ExecFlag ReturnItem}): a type and quantities above 0 are required; on ResultStatus 1 the screen opens the report (a new tab), toasts "Return Item added successfully" and goes to the
 *       Inventory list {@code #/ReturnItemList}.</li>
 * </ul>
 *
 * <p>Saving is REAL: the item is returned between the stores and stock moves.</p>
 */
public class ReturnItem extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            String post = r.request().postData() == null ? "" : r.request().postData();
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && post.contains("\"ExecFlag\":\"ReturnItem\"");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 1200 ? body.substring(0, 1200) : body, post};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public ReturnItem(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not do (empty = it held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastToast = "", lastSaveMessage = "", lastResponseId = "", lastTransNo = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const dlg=()=>[...document.querySelectorAll('.modal')].filter(vis).filter(m=>/Issue Item Search/.test(m.textContent)).pop() || null;"
          + "const dlgRows=()=>{ const m=dlg(); return m? [...m.querySelectorAll('.ui-grid-render-container-body .ui-grid-canvas .ui-grid-row')] : []; };"
          + "const selTbl=()=>{ const m=dlg(); return m? [...m.querySelectorAll('table')].find(x=>/Item Code/.test(x.textContent) && x.querySelector('tbody')) : null; };"
          + "const selRows=()=>{ const t=selTbl(); return t? [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox]')) : []; };"
          + "const mainTbl=()=>[...document.querySelectorAll('table')].find(x=>vis(x) && !x.closest('.modal') && /Return\\s+Quantity/.test(x.textContent));"
          + "const mainRows=()=>{ const t=mainTbl(); return t? [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[ng-model=\"Itm.ReturnQuantity\"]')) : []; };";

    private static final String PARENT = "Ancillary Services", GROUP = "CSSD";

    // ---- navigation ------------------------------------------------------------------------------------------

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

    /** Tag the "Return Item" link INSIDE the CSSD group (exact text: "Receive Return Item" is another entry; Inventory has its own Return Item). */
    private boolean tagLink() {
        Object r = page.evaluate("() => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)==='Ancillary Services'); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const g=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)==='CSSD'); if(!g) return false; const gli=g.closest('li'); if(!gli) return false;"
                + " const l=[...gli.querySelectorAll('a[href]')].filter(vis).find(x=>norm(x.textContent)==='Return Item'); if(!l) return false; l.id='__rtLink'; return true; }");
        return Boolean.TRUE.equals(r);
    }

    private boolean tagLinkProbe() {
        boolean t = tagLink();
        page.evaluate("() => { const e=document.getElementById('__rtLink'); if(e) e.removeAttribute('id'); }");
        return t;
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (tagLinkProbe()) return true; page.waitForTimeout(250); }
        return tagLinkProbe();
    }

    /** Ancillary Services &gt; CSSD &gt; Return Item. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Ancillary Services')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!tagLinkProbe()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !tagLinkProbe()) clickInner(PARENT, GROUP);
                waitLink(12000);
            }
            if (tagLink()) {
                try { page.locator("#__rtLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("CSSD ReturnItem.nav: click failed - " + e.getMessage().split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__rtLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !newButtonShown(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    private boolean newButtonShown() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('button')].some(b=>vis(b) && (b.getAttribute('ng-click')||'').indexOf('AddReturnItem')===0); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/returnitemlistcssd/?$") && newButtonShown(); }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS + " const e=document.querySelector(\"select[ng-model='ReturnItem.FromStore']\"); return !!e && !!e.options && e.options.length>1 && !!document.getElementById('GetIssue'); }");
        return Boolean.TRUE.equals(r) && page.url().toLowerCase().contains("#/add-returnitem");
    }

    /** Click New and wait for the form. */
    public boolean clickNew() {
        problems.clear();
        try { page.locator("button[ng-click^='AddReturnItem']").first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("New could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        for (int i = 0; i < 40 && !formOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1200);
        if (!formOpen()) problems.add("the Return Item form did not open (" + page.url() + ")");
        return ok();
    }

    // ---- selects -----------------------------------------------------------------------------------------------------------

    private String model(String sel, String expr) {
        Object r = page.evaluate("(a) => { try { const e=document.querySelector(a[0]); const sc=angular.element(e).scope(); const v=sc.$eval(a[1]); return v==null? '' : String(v); } catch(x) { return ''; } }", Arrays.asList(sel, expr));
        return r == null ? "" : r.toString();
    }

    /** The real options (placeholder excluded) of a select. */
    public List<String> options(String ngModel) {
        Object r = page.evaluate("(m) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+m+\"']\"); return e? [...e.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*\\s*select/i.test(t)) : []; }", ngModel);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Select the first of {@code preferred} the select offers (a real selection) and check the screen's model holds it. Returns the option, "" on failure. */
    private String selectOne(String ngModel, String what, String... preferred) {
        problems.clear();
        List<String> all = options(ngModel);
        String pick = null;
        pickLoop:
        for (String p : preferred) for (String a : all) if (a.equalsIgnoreCase(p)) { pick = a; break pickLoop; }
        if (pick == null) { problems.add("none of " + Arrays.toString(preferred) + " is offered by " + what + " (" + all.size() + " options)"); return ""; }
        try { page.locator("select[ng-model='" + ngModel + "']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(pick), new Locator.SelectOptionOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("\"" + pick + "\" could not be selected: " + e.getMessage().split("\n")[0]); return ""; }
        waitForAngular(900);
        Object shown = page.evaluate("(m) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+m+\"']\"); return e && e.selectedIndex>=0? norm(e.options[e.selectedIndex].textContent) : ''; }", ngModel);
        String mv = model("select[ng-model='" + ngModel + "']", ngModel);
        if (shown == null || !pick.equals(shown.toString())) { problems.add(what + " shows \"" + shown + "\" instead of \"" + pick + "\""); return ""; }
        if (mv.isEmpty() || mv.equals("0")) { problems.add("the screen's model " + ngModel + " is empty after selecting \"" + pick + "\""); return ""; }
        return pick;
    }

    public String selectFromStore(String... preferred) { return selectOne("ReturnItem.FromStore", "Return From Store", preferred); }

    public String selectToStore(String... preferred) { return selectOne("ReturnItem.ToStore", "Return To Store", preferred); }

    public String selectTypeOfReturn(String... preferred) { return selectOne("ReturnItem.TypeOfReturn", "Type Of Return", preferred); }

    // ---- Get Issue dialog ---------------------------------------------------------------------------------------------------------

    public boolean dialogOpen() {
        Object r = page.evaluate("() => {" + JS + " return !!dlg(); }");
        return Boolean.TRUE.equals(r);
    }

    /** Click Get Issue and wait for the Issue Item Search dialog. */
    public boolean clickGetIssue() {
        problems.clear();
        try { page.locator("#GetIssue").click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("Get Issue could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        for (int i = 0; i < 40 && !dialogOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1200);
        if (!dialogOpen()) problems.add("the Issue Item Search dialog did not open");
        return ok();
    }

    /** The stores the dialog shows (they are the form's, swapped): "From <x> / To <y>". */
    public String dialogStores() {
        Object r = page.evaluate("() => {" + JS + " const m=dlg(); if(!m) return ''; const f=n=>{ const e=m.querySelector(\"select[ng-model='\"+n+\"']\"); return e && e.selectedIndex>=0? norm(e.options[e.selectedIndex].textContent) : ''; };"
                + " return 'From ' + f('IssueItemSearch.FromStore') + ' / To ' + f('IssueItemSearch.ToStore'); }");
        return r == null ? "" : r.toString();
    }

    /** Click the dialog's Search and wait for rows. Returns the number of issues listed. */
    public int clickDialogSearch() {
        problems.clear();
        try {
            page.waitForResponse(x -> x.url().contains("/ReturnItem/Fetch") && String.valueOf(x.request().postData()).contains("GetIssuedItemSearch"), new Page.WaitForResponseOptions().setTimeout(40000),
                    () -> page.locator(".modal button[ng-click^='SearchIssueItem']").first().click(new Locator.ClickOptions().setTimeout(6000)));
        } catch (Exception e) { problems.add("Search did not reach the server: " + e.getMessage().split("\n")[0]); }
        waitForAngular(1500);
        for (int i = 0; i < 40 && issueRows().isEmpty(); i++) page.waitForTimeout(250);
        return issueRows().size();
    }

    /** The rows of Issue Item List as they read: Issuenumber | Issuedatetime | Patient Name | From Store Name | To Store Name/MRN | Totalamount | Totalmrp | Receivedby | Remark. */
    public List<String[]> issueRows() {
        Object r = page.evaluate("() => {" + JS + " return JSON.stringify(dlgRows().map(r=>[...r.querySelectorAll('.ui-grid-cell')].map(x=>norm(x.textContent)))); }");
        List<String[]> out = new ArrayList<>();
        try { for (JsonElement e : JsonParser.parseString(String.valueOf(r)).getAsJsonArray()) { JsonArray c = e.getAsJsonArray(); String[] v = new String[c.size()]; for (int i = 0; i < v.length; i++) v[i] = c.get(i).getAsString(); out.add(v); } }
        catch (Exception ignore) { }
        return out;
    }

    /** The rows of Selected Item: Item Code, Item Name, Batch Code, Expiry Date, Available Stock, Conversion, Company Code, Selling Price, Purchase Rate. */
    public List<String[]> selectedItems() {
        Object r = page.evaluate("() => {" + JS + " return JSON.stringify(selRows().map(r=>{ const c=[...r.querySelectorAll('td')].map(x=>norm(x.textContent)); return [c[1]||'', c[2]||'', c[3]||'', c[4]||'', c[5]||'', c[6]||'', c[7]||'', c[8]||'', c[9]||'']; })); }");
        List<String[]> out = new ArrayList<>();
        try { for (JsonElement e : JsonParser.parseString(String.valueOf(r)).getAsJsonArray()) { JsonArray c = e.getAsJsonArray(); String[] v = new String[c.size()]; for (int i = 0; i < v.length; i++) v[i] = c.get(i).getAsString(); out.add(v); } }
        catch (Exception ignore) { }
        return out;
    }

    /** Click (or double-click) the Issue Item List row with this index and wait for its items to reach Selected Item. Returns the Selected Item rows now shown (empty when none). */
    public List<String[]> clickIssueRow(int index, boolean doubleClick) {
        problems.clear();
        Object tagged = page.evaluate("(i) => {" + JS + " const r=dlgRows()[i]; if(!r) return false; const c=r.querySelector('.ui-grid-cell'); if(!c) return false; c.id='__rtRow'; r.scrollIntoView({block:'center'}); return true; }", index);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("row " + (index + 1) + " is not on screen"); return List.of(); }
        try {
            page.waitForResponse(x -> x.url().contains("/ReturnItem/Fetch") && String.valueOf(x.request().postData()).contains("GetIssueStockItemList"), new Page.WaitForResponseOptions().setTimeout(15000),
                    () -> {
                        Locator c = page.locator("#__rtRow");
                        if (doubleClick) c.dblclick(new Locator.DblclickOptions().setTimeout(6000)); else c.click(new Locator.ClickOptions().setTimeout(6000));
                    });
        } catch (Exception e) { problems.add((doubleClick ? "double-clicking" : "clicking") + " row " + (index + 1) + " fetched no items: " + e.getMessage().split("\n")[0]); }
        finally { page.evaluate("() => { const e=document.getElementById('__rtRow'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1200);
        for (int i = 0; i < 20 && selectedItems().isEmpty(); i++) page.waitForTimeout(250);
        return selectedItems();
    }

    /** Tick the checkbox of the Selected Item row (a real click) and check the row's own data holds it. */
    public boolean tickSelected(int index) {
        problems.clear();
        Object tagged = page.evaluate("(i) => {" + JS + " const r=selRows()[i]; if(!r) return false; const b=r.querySelector('input[type=checkbox]'); b.id='__rtTick'; r.scrollIntoView({block:'center'}); return true; }", index);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("row " + (index + 1) + " of Selected Item is not on screen"); return false; }
        try { page.locator("#__rtTick").check(new Locator.CheckOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the checkbox could not be ticked: " + e.getMessage().split("\n")[0]); }
        waitForAngular(600);
        Object held = page.evaluate("() => { const e=document.getElementById('__rtTick'); if(!e) return ''; let m=''; try{ m=String(angular.element(e).scope().Itm.IsSelected); }catch(x){} const r=e.checked+'/'+m; e.removeAttribute('id'); return r; }");
        if (held == null || !"true/true".equals(held.toString())) problems.add("the tick is not held (checked/model " + held + ")");
        return ok();
    }

    /** Click the dialog's OK and wait for it to close and the item to reach the form. Returns the number of item rows in the form, -1 on failure. */
    public int clickDialogOk() {
        problems.clear();
        try { page.locator(".modal button[ng-click^='OKClick']").first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("the dialog's OK could not be clicked: " + e.getMessage().split("\n")[0]); return -1; }
        for (int i = 0; i < 40 && dialogOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1200);
        for (int i = 0; i < 24 && mainItems().isEmpty(); i++) page.waitForTimeout(250);
        if (dialogOpen()) problems.add("the Issue Item Search dialog is still open after OK");
        int n = mainItems().size();
        if (n == 0) problems.add("the form's table has no item after OK");
        return ok() ? n : -1;
    }

    // ---- the form's item table ------------------------------------------------------------------------------------------------------

    /** The form's item rows: Item Name, Batch Code, Expiry Date, Transfer Quantity, Received Quantity, Available Stock, Return Quantity (the box), Selling Price, Total Amount. */
    public List<String[]> mainItems() {
        Object r = page.evaluate("() => {" + JS + " return JSON.stringify(mainRows().map(r=>{ const c=[...r.querySelectorAll('td')].map(x=>norm(x.textContent)); const q=r.querySelector('input[ng-model=\"Itm.ReturnQuantity\"]');"
                + " return [c[1]||'', c[2]||'', c[3]||'', c[4]||'', c[5]||'', c[6]||'', q? q.value : '', c[10]||'', c[11]||'']; })); }");
        List<String[]> out = new ArrayList<>();
        try { for (JsonElement e : JsonParser.parseString(String.valueOf(r)).getAsJsonArray()) { JsonArray c = e.getAsJsonArray(); String[] v = new String[c.size()]; for (int i = 0; i < v.length; i++) v[i] = c.get(i).getAsString(); out.add(v); } }
        catch (Exception ignore) { }
        return out;
    }

    /** The form's "Total Return Items" and "Total Amount" boxes as {items, amount}. */
    public String[] totals() {
        Object r = page.evaluate("() => { const f=n=>{ const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }; return JSON.stringify([f('ReturnItem.TotalReturnItems'), f('ReturnItem.TotalMRP')]); }");
        try { JsonArray a = JsonParser.parseString(String.valueOf(r)).getAsJsonArray(); return new String[]{a.get(0).getAsString(), a.get(1).getAsString()}; } catch (Exception e) { return new String[]{"", ""}; }
    }

    /** Type the Return Quantity of the row (a real fill + Tab, which fires the screen's own row calculation) and check the box and the row's data hold it. */
    public boolean enterReturnQuantity(int index, String qty) {
        problems.clear();
        Object tagged = page.evaluate("(i) => {" + JS + " const r=mainRows()[i]; if(!r) return false; const b=r.querySelector('input[ng-model=\"Itm.ReturnQuantity\"]'); if(b.disabled) return 'disabled'; b.id='__rtQty'; r.scrollIntoView({block:'center'}); return true; }", index);
        if ("disabled".equals(String.valueOf(tagged))) { problems.add("the Return Quantity of row " + (index + 1) + " is disabled"); return false; }
        if (!Boolean.TRUE.equals(tagged)) { problems.add("row " + (index + 1) + " has no Return Quantity box"); return false; }
        armToasts();
        try {
            Locator q = page.locator("#__rtQty");
            q.click(new Locator.ClickOptions().setTimeout(5000));
            q.fill(qty, new Locator.FillOptions().setTimeout(5000));
            q.press("Tab", new Locator.PressOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the Return Quantity could not be typed: " + e.getMessage().split("\n")[0]); }
        waitForAngular(900);
        Object mv = page.evaluate("() => { const e=document.getElementById('__rtQty'); let m=''; try{ m=String(angular.element(e).scope().Itm.ReturnQuantity); }catch(x){} const r=(e? e.value : '')+'|'+m; if(e) e.removeAttribute('id'); return r; }");
        String[] bm = String.valueOf(mv).split("\\|", -1);
        if (bm.length < 2 || !qty.equals(bm[0])) problems.add("the Return Quantity box shows \"" + (bm.length > 0 ? bm[0] : "") + "\" instead of " + qty + (toasts().isEmpty() ? "" : " (the screen said: " + toasts() + ")"));
        else if (!qty.equals(bm[1])) problems.add("the row's data holds \"" + bm[1] + "\" instead of " + qty);
        return ok();
    }

    // ---- remarks, Save -----------------------------------------------------------------------------------------------------------------

    public boolean enterRemarks(String text) {
        problems.clear();
        Locator m = page.locator("textarea[ng-model='ReturnItem.remark']").first();
        try {
            m.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            m.click(new Locator.ClickOptions().setTimeout(5000));
            m.fill(text, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the Remarks could not be typed: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(500);
        String box = m.inputValue();
        String mv = model("textarea[ng-model='ReturnItem.remark']", "ReturnItem.remark");
        if (!text.equals(box)) problems.add("the Remarks box shows \"" + box + "\" instead of the text typed");
        else if (!text.equals(mv)) problems.add("the Remarks are not held by the screen's model (\"" + mv + "\")");
        return ok();
    }

    /** Clear the toasts still on screen and start recording new ones. */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__rtToasts=[]; if(window.__rtObs) window.__rtObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__rtToasts.includes(m)) window.__rtToasts.push(m); });"
                + " window.__rtObs=new MutationObserver(add); window.__rtObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__rtToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Save (a real click) and return how many tabs were open just before (the save itself opens the report). The toasts are recorded from this moment. */
    public int clickSave() {
        problems.clear();
        lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        lastSaveStatus = -1; lastSaveMessage = ""; lastResponseId = ""; lastTransNo = "";
        int tabsBefore = page.context().pages().size();
        armToasts();
        Locator b = page.locator("button[ng-click^='IUDSave']").first();
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
        Matcher ri = Pattern.compile("\"ResponseId\"\\s*:\\s*\"?(\\d+)").matcher(c[3]);
        if (ri.find()) lastResponseId = ri.group(1);
        Matcher tn = Pattern.compile("\"TransNo\"\\s*:\\s*\"([^\"]*)\"").matcher(c[3]);
        if (tn.find()) lastTransNo = tn.group(1);
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
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !t.contains("unexpected");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
