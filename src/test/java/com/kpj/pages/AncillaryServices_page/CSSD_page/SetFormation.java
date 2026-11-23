package com.kpj.pages.AncillaryServices_page.CSSD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ancillary Services &gt; CSSD &gt; <b>Set Formation</b> — Page Object (list {@code #/KitFormation}, form {@code #/add-KitFormation}).
 *
 * <p>Confirmed live (devhis build v2026.10.31.357). The list ({@code ui-grid #grid1}; data from {@code POST /api/KitInformation/fetch}; one row per formed set: {@code itemname} = the group name, {@code code},
 * {@code quantity}; shown after <b>Search</b>) has an <b>Add</b> link ({@code addParameterMaster()}). The form (model {@code kitinformation.*}) has:</p>
 * <ul>
 *   <li><b>Set Information</b>: <b>Store</b> ({@code #ddlstore}, {@code .Storeid}, {@code fnKitStoreChange()} fills the groups), read-only Item Group ("CSSD"), read-only <b>Group Set Code</b>,
 *       <b>Group Name</b> ({@code #ddlkitname}, {@code .Kitid}, {@code fnsetItemcode()}) and <b>Set Quantity*</b> (1).</li>
 *   <li>Two tables: <b>List of Set Information</b> (Item Code | Item Name | Batch Code | Per Set Quantity | Total Quantity | Edit | Delete | Updated Date Time | Updated By) and <b>Updated/Deleted Items</b>.</li>
 *   <li><b>Add Set Details</b>: <b>Item Name*</b> ({@code .ItemName}, a smart search: typing "pana" lists {@code ul.ac-menu > li.ac-menu-item > a[ng-click=apply_selection($index)]}), read-only Item Batch /
 *       Item Stock / UOM / Selling Price / Expiry Date, <b>Add</b> ({@code #btnAdd}, {@code AddKitItem(...)}), Clear; and below: <b>Save</b> ({@code fnIUDKitInformation()}) and Back ({@code closeForm()}).</li>
 * </ul>
 * <p>Picking a suggestion opens the <b>Batch List</b> popup (columns Index | Id | Batch Code | Expiry Date | Selling Price | Purchase Price | … | Available Quantity | UOM | Supplier; Index and Id are hidden):
 * clicking a row (on a visible cell) closes it and fills the read-only details.</p>
 *
 * <p>Choosing a group LOADS that group's items from its LAST formation ("n item(s) loaded from the last formation of this set. Check the batches and save.") — so a group that was formed before already holds
 * its items and refuses them again as "already selected". Hence the group scan: the test prefers a group that loads nothing, and the item choice falls back to the next suggestion, then to the next group.</p>
 *
 * <p>Saving is REAL: a set is formed for the store and its stock moves into the set.</p>
 */
public class SetFormation extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall") && !low.contains("getmaster") && !low.contains("get");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public SetFormation(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastStore = "", lastGroup = "", lastGroupCode = "", lastItem = "", lastBatch = "", lastBatchExpiry = "", lastBatchAvailable = "", lastToast = "", lastSaveMessage = "", lastDialog = "";
    public String lastDetails = "", lastAddedRow = "", lastBatchCost = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String M = "kitinformation.";
    private static final String HREF = "#/kitformation";
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

    /**
     * Ancillary Services &gt; CSSD &gt; Set Formation. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again, and a click on
     * CSSD straight after Ancillary Services is lost). The link is found by its exact route.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Ancillary Services')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__sfLink'; }", HREF);
                try { page.locator("#__sfLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("SetFormation.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__sfLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        waitAddLink();
        waitForAngular(1000);
        return onList();
    }

    private void waitAddLink() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a,button')].some(x=>/^addParameterMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("SetFormation.nav: the Add link is not ready"); }
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/kitformation/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-kitformation/?$")
                && Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"select[ng-model='" + M + "Storeid']\")"));
    }

    // ---- the list of formed sets ----------------------------------------------------------------------------------

    /** The formed sets the list holds, as {id, group name, code, quantity} (the list shows them after Search). */
    public List<String[]> existing() {
        Object r = page.evaluate("() => { try{ const g=document.querySelector('.ui-grid'); const sc=angular.element(g).scope(); const go=sc.gridOptions||(sc.$parent&&sc.$parent.gridOptions); const s=v=>v==null? '' : String(v);"
                + " return go.data.filter(x=>x.id>0||x.itemname).map(x=>[s(x.id), s(x.itemname), s(x.code), s(x.quantity)]); }catch(e){ return []; } }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2)), String.valueOf(c.get(3))}); }
        return out;
    }

    /** Click the list's Search button and wait for the answer; returns the formed sets listed. */
    public List<String[]> searchList() {
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent))); if(!b) return false; b.id='__sfSearch'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__sfSearch").click(new Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
            page.evaluate("() => { const e=document.getElementById('__sfSearch'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(2500);
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < 24 && rows.isEmpty(); i++) { rows = existing(); if (rows.isEmpty()) page.waitForTimeout(250); }
        return rows;
    }

    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__sfToasts=[]; if(window.__sfObs) window.__sfObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__sfToasts.includes(m)) window.__sfToasts.push(m); });"
                + " window.__sfObs=new MutationObserver(add); window.__sfObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    /** The toasts recorded since {@link #armToasts()}. */
    public String toasts() {
        Object r = page.evaluate("() => (window.__sfToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- the form -----------------------------------------------------------------------------------------------------

    /** Click Add on the list and wait for the form with its Store dropdown filled. */
    public boolean clickAdd() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('a,button')].filter(vis).find(x=>/^addParameterMaster/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__sfAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add link is not on screen"); return false; }
        try { page.locator("#__sfAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add link could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__sfAdd'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        if (!onForm()) { problems.add("the Set Formation form did not open (" + page.url() + ")"); return false; }
        for (int i = 0; i < 60 && options(M + "Storeid").size() < 2; i++) page.waitForTimeout(250);
        if (options(M + "Storeid").size() < 2) problems.add("the Store dropdown has no options");
        waitForAngular(800);
        return ok();
    }

    /** The options of the dropdown with this ng-model as {text, value} (placeholder excluded). */
    public List<String[]> options(String ng) {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent))).map(o=>[norm(o.textContent), o.value]); }", ng);
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    /** Select the option with this exact text (ignoring case) and make angular/select2 see the change; returns the selected text, "" if it could not be selected. */
    private String choose(String ng, String text, String what) {
        Object r = page.evaluate("(a) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+a[0]+\"']\"); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()===a[1].toLowerCase()); if(i<0) return '';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} }"
                + " return norm(e.options[e.selectedIndex].textContent); }", Arrays.asList(ng, text));
        String got = r == null ? "" : r.toString();
        waitForAngular(600);
        if (got.isEmpty()) { problems.add("the " + what + " \"" + text + "\" could not be selected"); return ""; }
        Object model = page.evaluate("(n) => { const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return ''; try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        if (model == null || model.toString().trim().isEmpty()) problems.add("the " + what + " is not held by the model after selecting \"" + got + "\"");
        return got;
    }

    /** Select the store (its change fills the Group Name dropdown, which is awaited). */
    public String selectStore(String store) {
        problems.clear();
        lastStore = choose(M + "Storeid", store, "Store");
        for (int i = 0; i < 60 && options(M + "Kitid").isEmpty(); i++) page.waitForTimeout(250);
        if (ok() && options(M + "Kitid").isEmpty()) problems.add("the Group Name dropdown stayed empty after selecting " + lastStore);
        waitForAngular(800);
        return lastStore;
    }

    public List<String> groupNames() {
        List<String> out = new ArrayList<>();
        for (String[] o : options(M + "Kitid")) out.add(o[0]);
        return out;
    }

    /** The rows of the "List of Set Information" table (the items of the set being formed), each as one text. */
    public List<String> setRows() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>/Per Set Quantity/.test(x.textContent)); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean).slice(0,5).join(' | ')).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The rows of the "Updated/Deleted Items" table (items the app could not load, with the reason), each as one text. */
    public List<String> deletedRows() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>/Deletion Reason/.test(x.textContent)); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean).slice(0,6).join(' | ')).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Select a group and wait for its template to load; the toast, the group's code and the items loaded from its last formation are left in the {@code last*} fields. */
    public String selectGroup(String group) {
        problems.clear();
        armToasts();
        lastGroup = choose(M + "Kitid", group, "Group Name");
        if (!ok()) return "";
        for (int i = 0; i < 24 && readValue("input[ng-model='" + M + "Code']").isEmpty(); i++) page.waitForTimeout(250);
        page.waitForTimeout(2500);                                              // the group's last formation is loaded a moment after its code
        lastGroupCode = readValue("input[ng-model='" + M + "Code']");
        lastToast = toasts();
        return lastGroup;
    }

    private String readValue(String selector) {
        Object v = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.value : ''; }", selector);
        return v == null ? "" : v.toString().trim();
    }

    // ---- the smart search and the batch popup ------------------------------------------------------------------------

    /** Type the text into Item Name (a smart search) and return the suggestions listed. */
    public List<String> typeItem(String text) {
        problems.clear();
        lastToast = "";
        Locator in = page.locator("input[ng-model='" + M + "ItemName']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(80));
        } catch (Exception e) { problems.add("the Item Name could not be typed"); return List.of(); }
        List<String> sug = suggestions();
        for (int i = 0; i < 40 && sug.isEmpty(); i++) { page.waitForTimeout(250); sug = suggestions(); }
        if (sug.isEmpty()) problems.add("no item is suggested for \"" + text + "\"");
        return sug;
    }

    public List<String> suggestions() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('li.ac-menu-item a')].filter(vis).map(a=>norm(a.textContent)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    public boolean batchPopupOpen() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('.modal')].some(m=>vis(m) && /Batch List/i.test(m.textContent||'')); }");
        return Boolean.TRUE.equals(r);
    }

    /** Click the {@code index}-th (0-based) suggestion and wait for the Batch List popup. Returns false (with the app's toast in {@link #lastToast}) when no popup came, e.g. "already selected". */
    public boolean pickSuggestion(int index) {
        problems.clear();
        armToasts();
        Object tagged = page.evaluate("(i) => {" + JS + " const a=[...document.querySelectorAll('li.ac-menu-item a')].filter(vis)[i]; if(!a) return ''; a.id='__sfSug'; return norm(a.textContent); }", index);
        String name = tagged == null ? "" : tagged.toString();
        if (name.isEmpty()) { problems.add("suggestion " + (index + 1) + " is not on screen"); return false; }
        lastItem = name;
        try { page.locator("#__sfSug").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the suggestion \"" + name + "\" could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__sfSug'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 40 && !batchPopupOpen(); i++) page.waitForTimeout(250);
        lastToast = toasts();
        if (!batchPopupOpen()) { problems.add("no Batch List popup opened for \"" + name + "\"" + (lastToast.isEmpty() ? "" : " (toast: \"" + lastToast + "\")")); return false; }
        page.waitForTimeout(600);
        return true;
    }

    /**
     * The batches in the Batch List popup as {batch code, expiry date, available quantity, supplier, purchase price}; columns are found by their header text (Index/Id are hidden). The purchase price is the
     * first "Purchase Price" column (the popup also has a "Purchase Rate" one; the larger of the two is returned).
     */
    public List<String[]> batchRows() {
        Object r = page.evaluate("() => {" + JS + " const m=[...document.querySelectorAll('.modal')].filter(x=>vis(x) && /Batch List/i.test(x.textContent||'')).pop(); if(!m) return [];"
                + " const t=m.querySelector('table'); if(!t) return []; const h=[...t.querySelectorAll('th')].map(x=>norm(x.textContent));"
                + " const ix=re=>h.findIndex(x=>re.test(x)); const b=ix(/^Batch Code/i), e=ix(/^Expiry/i), a=ix(/^Available/i), s=ix(/^Supplier/i), p=ix(/^Purchase Price/i), pr=ix(/^Purchase Rate/i);"
                + " const n=v=>parseFloat(String(v||'').replace(/[^0-9.\\-]/g,''))||0;"
                + " return [...t.querySelectorAll('tbody tr')].map(tr=>{ const c=[...tr.querySelectorAll('td')].map(td=>norm(td.textContent)); return [c[b]||'', c[e]||'', c[a]||'', c[s]||'', String(Math.max(n(c[p]), n(c[pr])))]; }).filter(c=>c[0]); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2)), String.valueOf(c.get(3)), String.valueOf(c.get(4))}); }
        return out;
    }

    /** True when {@code dd/MM/yyyy} is today or later (an expired batch must not be chosen). */
    private static boolean notExpired(String ddMMyyyy) {
        try { return !LocalDate.parse(ddMMyyyy, DateTimeFormatter.ofPattern("dd/MM/yyyy")).isBefore(LocalDate.now()); }
        catch (Exception e) { return true; }
    }

    private static double number(String s) {
        try { return Double.parseDouble(s.replaceAll("[^0-9.\\-]", "")); } catch (Exception e) { return 0; }
    }

    /**
     * Click one batch row (on its Batch Code cell: the Index/Id cells are hidden). Which one: the batch named by {@code -Dsetformation.batch=} if it has stock; else the first batch that has stock, is not
     * expired and has a purchase cost above 0 (the server refuses to create the set's batch from a zero-cost "Opening Balance" batch); else the first with stock that is not expired. The popup should close and
     * the read-only details (batch, stock, UOM, price, expiry) fill; they are left in {@link #lastDetails}. Returns false with {@link #problems} when it did not.
     */
    public boolean selectBatchRow() {
        problems.clear();
        List<String[]> rows = batchRows();
        String pick = "", wanted = System.getProperty("setformation.batch", "").trim();
        for (String[] r : rows) if (!wanted.isEmpty() && r[0].equalsIgnoreCase(wanted) && number(r[2]) > 0) { pick = r[0]; lastBatchExpiry = r[1]; lastBatchAvailable = r[2]; lastBatchCost = r[4]; break; }
        if (pick.isEmpty()) for (String[] r : rows) if (number(r[2]) > 0 && notExpired(r[1]) && number(r[4]) > 0) { pick = r[0]; lastBatchExpiry = r[1]; lastBatchAvailable = r[2]; lastBatchCost = r[4]; break; }
        if (pick.isEmpty()) for (String[] r : rows) if (number(r[2]) > 0 && notExpired(r[1])) { pick = r[0]; lastBatchExpiry = r[1]; lastBatchAvailable = r[2]; lastBatchCost = r[4]; break; }
        if (pick.isEmpty()) { problems.add("none of the " + rows.size() + " batches has stock and is unexpired"); return false; }
        lastBatch = pick;
        Object tagged = page.evaluate("(b) => {" + JS + " const m=[...document.querySelectorAll('.modal')].filter(x=>vis(x) && /Batch List/i.test(x.textContent||'')).pop(); if(!m) return false;"
                + " const td=[...m.querySelectorAll('tbody td')].filter(vis).find(x=>norm(x.textContent)===b); if(!td) return false; td.id='__sfBatch'; return true; }", pick);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the batch \"" + pick + "\" could not be found in the popup"); return false; }
        try { page.locator("#__sfBatch").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the batch row \"" + pick + "\" could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__sfBatch'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 40 && batchPopupOpen(); i++) page.waitForTimeout(250);
        if (batchPopupOpen()) { problems.add("the Batch List popup stayed open after clicking the row"); return false; }
        waitForAngular(800);
        String batch = readValue("input[ng-model='" + M + "ItemBatch']");
        lastDetails = "Item " + readValue("input[ng-model='" + M + "ItemName']") + ", batch " + batch + ", stock " + readValue("input[ng-model='" + M + "ItemStock']") + ", UOM "
                + readValue("input[ng-model='" + M + "UOM']") + ", selling price " + readValue("input[ng-model='" + M + "MRPRate']") + ", expiry " + readValue("input[ng-model='" + M + "ExpiryDate']");
        if (!pick.equalsIgnoreCase(batch)) problems.add("Item Batch shows \"" + batch + "\" instead of the chosen \"" + pick + "\"");
        return ok();
    }

    /** Click Add: the item should join the "List of Set Information" table. Returns false (toast in {@link #lastToast}) when it did not, e.g. "already selected". */
    public boolean clickAddItem() {
        problems.clear();
        lastAddedRow = "";
        List<String> before = setRows();
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^AddKitItem/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__sfAddItem'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add button is not on screen"); return false; }
        try { page.locator("#__sfAddItem").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__sfAddItem'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1200);
        List<String> after = before;
        for (int i = 0; i < 24; i++) { after = setRows(); if (after.size() > before.size()) break; page.waitForTimeout(250); }
        lastToast = toasts();
        if (after.size() <= before.size()) { problems.add("Add put no row into the List of Set Information" + (lastToast.isEmpty() ? "" : " (toast: \"" + lastToast + "\")")); return false; }
        for (String r : after) if (!before.contains(r)) lastAddedRow = r;
        if (lastAddedRow.isEmpty()) lastAddedRow = after.get(after.size() - 1);
        return true;
    }

    // ---- save ----------------------------------------------------------------------------------------------------------

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(fnIUDKitInformation|AddKitItem)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__sfDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__sfDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("SetFormation.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__sfDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Save, answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String saveAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^fnIUDKitInformation/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__sfSave'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Save button is not on screen"); return ""; }
        try { page.locator("#__sfSave").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__sfSave'); if(e) e.removeAttribute('id'); }"); }
        long end = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < end) {
            String d = answerDialog();
            if (!d.isEmpty()) lastDialog = lastDialog.isEmpty() ? d : lastDialog + " > " + d;
            lastToast = toasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(350);
        }
        page.waitForTimeout(800);
        lastToast = toasts();
        parseSaveAnswer();
        return lastToast;
    }

    /** After a successful save the screen should return to the list; if it stays on the form, click Back (answering a "leave the form" confirmation if one is asked). */
    public boolean returnToList() { return returnToList(true); }

    /**
     * Back to the list. {@code waitForAuto} first gives the screen up to 8 s to return by itself (it does after a successful save); pass false after a REFUSED save, where it stays on the form. Back is clicked
     * after the toasts are cleared (a toast can sit over it), with a script click as the fallback, up to three times, and the sidebar's Set Formation link is the last resort.
     */
    public boolean returnToList(boolean waitForAuto) {
        if (waitForAuto) for (int i = 0; i < 32 && !onList(); i++) page.waitForTimeout(250);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove()); }");
            Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,a')].filter(vis).find(x=>/^closeForm/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__sfBack'; b.scrollIntoView({block:'center'}); return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__sfBack").click(new Locator.ClickOptions().setTimeout(4000)); }
                catch (Exception e) { page.evaluate("() => { const e=document.getElementById('__sfBack'); if(e) e.click(); }"); }
                page.evaluate("() => { const e=document.getElementById('__sfBack'); if(e) e.removeAttribute('id'); }");
            }
            for (int i = 0; i < 16 && !onList(); i++) {
                if (i % 4 == 3) answerDialog();
                page.waitForTimeout(250);
            }
        }
        if (!onList() && linkVisible()) {                                        // last resort: the sidebar link (nothing is saved by leaving the form)
            page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.click(); }", HREF);
            for (int i = 0; i < 24 && !onList(); i++) page.waitForTimeout(250);
        }
        waitAddLink();
        waitForAngular(1000);
        return onList();
    }

    /** Leave a form whose Save was refused (nothing was written) and open a fresh one: Back, Add, then the store again. Returns true when the new form is open with the store selected. */
    public boolean restartForm(String store) {
        if (!returnToList(false)) { problems.add("could not get back to the list (" + page.url() + ")"); return false; }
        if (!clickAdd()) return false;
        selectStore(store);
        return ok();
    }

    private void parseSaveAnswer() {
        lastSaveStatus = -1;
        lastSaveMessage = "";
        String[] c = null;
        synchronized (saveCalls) {
            if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1);
            else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1);
        }
        if (c == null) return;
        Matcher m = Pattern.compile("\"ResultStatus\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (m.find()) lastSaveStatus = Integer.parseInt(m.group(1));
        Matcher msg = Pattern.compile("\"Message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(c[3]);
        if (msg.find()) lastSaveMessage = msg.group(1);
        int http = Integer.parseInt(c[0]);
        if (http >= 400) lastSaveMessage = "HTTP " + http + " " + c[2].replaceAll("^https?://[^/]+", "") + " " + lastSaveMessage;
    }

    /** What the save call and any failed API call answered, for the report. */
    public String saveDiagnosis() {
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) {
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[1]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(220, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    /** The toast is a success message AND the server did not refuse the save. The server spells several of its messages wrongly (Succesfully, sucessfully), so those are accepted too. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|formed|updated").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
