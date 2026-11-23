package com.kpj.pages.Inventory_page.StockTakingActivity_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inventory &gt; Stock Taking Activity &gt; <b>Stock Take</b> — Page Object ({@code #/PhysicalStockList}, form {@code #/add-PhysicalStock}).
 *
 * <p>Confirmed live (devhis build v2026.10.27.303). The list ({@code PhysicalStock.*}) has a <b>New</b> button (its handler is named {@code AddFreezeStock()}); the form
 * ({@code PhysicalStock.*}) has:</p>
 * <ul>
 *   <li><b>Store*</b> ({@code PhysicalStock.SearchStoreID}, select2, empty by default) and the optional Rack / Shelf / Category / Sub Category / Generic / Proprietary / Item Class filters.</li>
 *   <li><b>Stock Take Category*</b> ({@code PhysicalStockData.StockTakeCategoryId}): "Pharmacy,Oral Solids/None", "Pharmacy,Oral Solids/Oncology Preparations", ... (no space after the comma).</li>
 *   <li><b>Get Items</b> ({@code GetFreezeStockList()}) fills the table (200 rows a page, {@code FirstBtn()/PreviousBtn()/NextBtn()/LastBtn()}): Sr. No., Item Code, Item Name, Batch Code, Rack,
 *       Shelf, Store Name, Expiry Date, UOM, WAC, <b>Available Quantity</b>, <b>Physical Quantity</b> (an input, {@code Itm.AdjustmentQty}), Adjustment Quantity (follows the input), Remark
 *       ({@code Itm.Remark}) and Status.</li>
 *   <li><b>Save</b> ({@code IUDSave()}) stores the stock take.</li>
 * </ul>
 * Saving is REAL: a Physical Stock record is created (the "Stock Take Approval" screen approves it; this page never does).
 */
public class StockTake extends BasePage {

    /** Every POST the screen made that is not a plain fetch, plus any API call that failed: {status, method, decoded url, body head}. */
    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public StockTake(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastStore = "", lastCategory = "", lastRow = "", lastItemCode = "", lastBatch = "", lastAvailable = "", lastQty = "", lastAdjustment = "", lastDialog = "", lastToast = "",
            lastSaveMessage = "", lastSaveId = "";
    public int lastSaveStatus = -1, lastRowCount = 0, lastRowIndex = -1;
    public long lastGetItemsMs = 0;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const itemsTable=()=>[...document.querySelectorAll('table')].filter(vis).find(t=>/Physical Quantity/.test(norm(t.textContent)) && /Available Quantity/.test(norm(t.textContent)));"
          + "const headers=t=>[...t.querySelectorAll('th')].map(h=>norm(h.textContent));";

    private static final String STORE_NG = "PhysicalStock.SearchStoreID", CATEGORY_NG = "PhysicalStockData.StockTakeCategoryId";
    private static final String QTY_BOX = "input[ng-model='Itm.AdjustmentQty']";
    private static final String HREF = "#/physicalstocklist";

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
     * Inventory &gt; Stock Taking Activity &gt; Stock Take. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group
     * again, and a click on Stock Taking Activity straight after Inventory is lost), so Inventory gets a settling pause before the next group is clicked.
     */
    public boolean navigateViaMenu() {
        final String group = "Stock Taking Activity";
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!linkVisible()) {
                if (groupState(group) < 0) {                                   // the group is not on screen: Inventory is shut
                    if (groupState("Inventory") != 1) clickGroup("Inventory");
                    for (int i = 0; i < 48 && groupState(group) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);                                 // let the Inventory level finish building before the group is clicked
                }
                if (groupState(group) == 0 && !linkVisible()) clickGroup(group);
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__stLink'; }", HREF);
                try { page.locator("#__stLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("StockTake.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__stLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll('button')].find(b=>/AddFreezeStock/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("StockTake.nav: the New button is not ready"); }
        waitForAngular(1000);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/physicalstocklist/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-physicalstock/?$")
                && Boolean.TRUE.equals(page.evaluate("(n) => {" + JS + " return !![...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); }", CATEGORY_NG));
    }

    // ---- small helpers ---------------------------------------------------------------------------------------

    private boolean clickByNg(String re, String what) {
        Object tagged = page.evaluate("(re) => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>new RegExp(re).test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__stBtn'; return true; }", re);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the " + what + " button is not on screen"); return false; }
        try { page.locator("#__stBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { problems.add("the " + what + " button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__stBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** Choose an option of a (select2-backed) select matching {@code want} ignoring case and ALL whitespace ("Pharmacy, Oral Solids/None" == "Pharmacy,Oral Solids/None"). Returns the option text. */
    private String pickOption(String ng, String want) {
        Object t = page.evaluate("(a) => {" + JS
                + " const e=[...document.querySelectorAll(\"[ng-model='\"+a.n+\"']\")].find(vis)||document.querySelector(\"[ng-model='\"+a.n+\"']\"); if(!e) return '';"
                + " const k=s=>norm(s).toLowerCase().replace(/\\s+/g,'');"
                + " const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value,i:x.index})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=o.find(x=>k(x.t)===k(a.w)); if(!p) return '';"
                + " e.selectedIndex=p.i; e.dispatchEvent(new Event('change',{bubbles:true})); try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', e.value); }catch(x){} try{ $(e).trigger('change'); }catch(x){} } return p.t; }", Map.of("n", ng, "w", want));
        return t == null ? "" : t.toString();
    }

    private boolean waitOptions(String ng) {
        try { page.waitForFunction("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e && e.options.length>1; }", ng, new Page.WaitForFunctionOptions().setTimeout(10000)); return true; }
        catch (Exception e) { return false; }
    }

    /** A visible alert / confirmation dialog's text ("" if none). */
    public String confirmText() {
        Object r = page.evaluate("() => {" + JS + " const d=[...document.querySelectorAll('.jconfirm, .ng-confirm-box')].filter(vis).map(x=>norm(x.textContent).replace(/^[^A-Za-z0-9]+/,''));"
                + " return d.length? d[d.length-1] : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click the affirmative button (YES, else OK) of the visible dialog (the one on top when they are stacked); returns its label, "" when there is none. */
    private String answerDialog() {
        String first = "";
        for (int round = 0; round < 4; round++) {
            Object label = page.evaluate("() => {" + JS + " const bs=[...document.querySelectorAll('.jconfirm button, .ng-confirm-box button')].filter(vis);"
                    + " const want=bs.filter(x=>/^yes$/i.test(norm(x.textContent))); const alt=bs.filter(x=>/^(ok|okay|confirm)$/i.test(norm(x.textContent)));"
                    + " const cand=(want.length? want : alt).reverse(); if(!cand.length) return '';"
                    + " const onTop=x=>{ const r=x.getBoundingClientRect(); const t=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return !!t && x.contains(t); };"
                    + " const b=cand.find(onTop) || cand[0]; b.id='__stAns'; return norm(b.textContent); }");
            String l = label == null ? "" : label.toString();
            if (l.isEmpty()) break;
            if (first.isEmpty()) first = l;
            try { page.locator("#__stAns").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { problems.add("\"" + l + "\" could not be clicked"); break; }
            finally { page.evaluate("() => { const e=document.getElementById('__stAns'); if(e) e.removeAttribute('id'); }"); }
            page.waitForTimeout(700);
            if (confirmText().isEmpty()) break;
        }
        for (int i = 0; i < 20 && !confirmText().isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(500);
        return first;
    }

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    private String noticeSuffix() {
        String t = toastsNow(), d = confirmText();
        return (t.isEmpty() ? "" : " — the screen said \"" + t + "\"") + (d.isEmpty() ? "" : " — a dialog says \"" + d + "\"");
    }

    // ---- list -> New -> store -> category -> Get Items ---------------------------------------------------------------

    /** Click New on the list and wait for the form. */
    public boolean clickNew() {
        problems.clear();
        if (!clickByNg("AddFreezeStock", "New")) return false;
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        waitForAngular(1500);
        if (!onForm()) problems.add("the Stock Take form did not open (" + page.url() + ")");
        return ok();
    }

    /** Choose the Store (empty by default). Matches ignoring case and spaces. */
    public String selectStore(String want) {
        problems.clear();
        if (!waitOptions(STORE_NG)) { problems.add("the Store list has no options"); return ""; }
        lastStore = pickOption(STORE_NG, want);
        if (lastStore.isEmpty()) { problems.add("the Store list has no \"" + want + "\""); return ""; }
        waitForAngular(1500);
        String held = model(STORE_NG);
        if (held.isEmpty() || "0".equals(held)) problems.add("the Store is not held by the model");
        return lastStore;
    }

    /** The Stock Take Category options, for the report. */
    public List<String> categoryOptions() {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e? [...e.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*\\s*select\\s*-*$/i.test(t)) : []; }", CATEGORY_NG);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Choose the Stock Take Category (matched ignoring case and ALL whitespace). Returns the option chosen, "" when the list has no such entry. */
    public String selectCategory(String want) {
        problems.clear();
        if (!waitOptions(CATEGORY_NG)) { problems.add("the Stock Take Category list has no options"); return ""; }
        lastCategory = pickOption(CATEGORY_NG, want);
        if (lastCategory.isEmpty()) { problems.add("the Stock Take Category list has no \"" + want + "\" (it holds " + categoryOptions().stream().limit(4).toList() + " ...)"); return ""; }
        waitForAngular(1000);
        String held = model(CATEGORY_NG);
        if (held.isEmpty() || "0".equals(held)) problems.add("the Stock Take Category is not held by the model");
        if (!confirmText().isEmpty() || !toastsNow().isEmpty()) problems.add("choosing the category raised a message" + noticeSuffix());
        return ok() ? lastCategory : "";
    }

    /** Number of rows in the item table. */
    public int rowCount() {
        Object n = page.evaluate("() => {" + JS + " const t=itemsTable(); return t? t.querySelectorAll('tbody tr').length : 0; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** Click Get Items and wait for the table to fill (or for the screen to say there is nothing). Returns the number of rows (also {@link #lastRowCount}). */
    public int clickGetItems() {
        problems.clear();
        lastRowCount = 0;
        if (!clickByNg("GetFreezeStockList", "Get Items")) return 0;
        long start = System.currentTimeMillis(), end = start + 120000;
        while (System.currentTimeMillis() < end) {
            lastRowCount = rowCount();
            if (lastRowCount > 0) break;
            if (!confirmText().isEmpty() || !toastsNow().isEmpty()) { page.waitForTimeout(1500); lastRowCount = rowCount(); break; }
            synchronized (saveCalls) { if (!failedCalls.isEmpty()) break; }
            page.waitForTimeout(400);
        }
        lastGetItemsMs = System.currentTimeMillis() - start;
        waitForAngular(800);
        lastRowCount = rowCount();
        if (lastRowCount == 0) problems.add("Get Items listed no rows" + noticeSuffix());
        synchronized (saveCalls) { for (String[] c : failedCalls) problems.add("a call failed: HTTP " + c[0] + " " + c[2].replaceAll("^https?://[^/]+", "") + " " + c[3].replaceAll("\\s+", " ")); }
        return lastRowCount;
    }

    /** The first rows of the table as "Item Code | Item Name | Batch | Available Quantity" (for the report). */
    public List<String> sampleRows(int max) {
        Object r = page.evaluate("(m) => {" + JS + " const t=itemsTable(); if(!t) return []; const hs=headers(t); const ix=n=>hs.indexOf(n);"
                + " return [...t.querySelectorAll('tbody tr')].slice(0,m).map(r=>{ const c=[...r.querySelectorAll('td')].map(td=>norm(td.textContent));"
                + "   return [c[ix('Item Code')], c[ix('Item Name')], c[ix('Batch Code')], 'available '+c[ix('Available Quantity')]].join(' | '); }); }", max);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    // ---- physical quantity ----------------------------------------------------------------------------------------------

    /**
     * Enter {@code qty} as the <b>Physical Quantity</b> of one row that has stock (Available Quantity &gt; 0). The row whose Available Quantity EQUALS the quantity is preferred (the stock take
     * then records no variance), else the row with the smallest positive Available Quantity (the smallest variance). Typed, then Tab so the Adjustment Quantity recalculates.
     * Returns the row's text ("Item Code | Item Name | Batch | available N"), "" when no row has stock.
     */
    public String enterPhysicalQuantity(String qty) {
        problems.clear();
        Object pick = page.evaluate("(q) => {" + JS + " const t=itemsTable(); if(!t) return ''; const hs=headers(t); const ai=hs.indexOf('Available Quantity'), ci=hs.indexOf('Item Code'), ni=hs.indexOf('Item Name'), bi=hs.indexOf('Batch Code');"
                + " const rows=[...t.querySelectorAll('tbody tr')]; const avail=r=>parseFloat(norm(([...r.querySelectorAll('td')][ai]||{}).textContent));"
                + " const withStock=rows.map((r,i)=>({r,i,a:avail(r)})).filter(x=>x.a>0 && x.r.querySelector(\"input[ng-model='Itm.AdjustmentQty']\"));"
                + " if(!withStock.length) return ''; let best=withStock.find(x=>x.a===parseFloat(q)); if(!best) best=withStock.sort((x,y)=>x.a-y.a)[0];"
                + " const c=[...best.r.querySelectorAll('td')].map(td=>norm(td.textContent)); const box=best.r.querySelector(\"input[ng-model='Itm.AdjustmentQty']\"); box.id='__stQty';"
                + " return JSON.stringify({i:best.i, code:c[ci], name:c[ni], batch:c[bi], avail:c[ai]}); }", qty);
        String s = pick == null ? "" : pick.toString();
        if (s.isEmpty()) { problems.add("no row in the list has an Available Quantity above 0"); return ""; }
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(s).getAsJsonObject();
        lastRowIndex = o.get("i").getAsInt();
        lastItemCode = o.get("code").getAsString();
        lastBatch = o.get("batch").getAsString();
        lastAvailable = o.get("avail").getAsString();
        lastRow = lastItemCode + " | " + o.get("name").getAsString() + " | " + lastBatch + " | available " + lastAvailable;
        Locator box = page.locator("#__stQty");
        try {
            box.scrollIntoViewIfNeeded();
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.fill("", new Locator.FillOptions().setTimeout(5000));
            box.pressSequentially(qty, new Locator.PressSequentiallyOptions().setDelay(60));
            box.press("Tab");
        } catch (Exception e) { problems.add("the Physical Quantity box could not be typed into"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__stQty'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(800);
        // read it back from the same row
        Object back = page.evaluate("(i) => {" + JS + " const t=itemsTable(); const r=t&&[...t.querySelectorAll('tbody tr')][i]; if(!r) return ''; const b=r.querySelector(\"input[ng-model='Itm.AdjustmentQty']\");"
                + " const hs=headers(t); const ai=hs.indexOf('Adjustment Quantity'); let held=''; try{ held=String(angular.element(b).scope().$eval('Itm.AdjustmentQty')); }catch(x){}"
                + " return JSON.stringify({box:b? b.value : '', held:held, adj:norm(([...r.querySelectorAll('td')][ai]||{}).textContent)}); }", lastRowIndex);
        com.google.gson.JsonObject bo = back == null || back.toString().isEmpty() ? new com.google.gson.JsonObject() : com.google.gson.JsonParser.parseString(back.toString()).getAsJsonObject();
        lastQty = bo.has("box") ? bo.get("box").getAsString() : "";
        lastAdjustment = bo.has("adj") ? bo.get("adj").getAsString() : "";
        String held = bo.has("held") ? bo.get("held").getAsString() : "";
        if (!qty.equals(lastQty)) problems.add("the Physical Quantity box shows \"" + lastQty + "\" instead of \"" + qty + "\"");
        if (held.isEmpty() || "undefined".equals(held) || "null".equals(held)) problems.add("the Physical Quantity is not held by the model");
        return ok() ? lastRow : "";
    }

    // ---- save ------------------------------------------------------------------------------------------------

    private void installToastWatch() {
        page.evaluate("() => { window.__stToasts=[]; if(window.__stObs) window.__stObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__stToasts.includes(m)) window.__stToasts.push(m); });"
                + " window.__stObs=new MutationObserver(add); window.__stObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__stToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /**
     * Click Save, answer a confirmation if the screen asks for one (YES, else OK), and wait for the toast. Returns the toast text ("" if none appeared); {@link #lastDialog} holds the
     * confirmation's text (empty when Save went straight through).
     */
    public String saveAndGetToast() {
        problems.clear();
        lastDialog = lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Locator save = page.locator("button[ng-click^='IUDSave']").first();
        try { save.scrollIntoViewIfNeeded(); save.click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        for (int i = 0; i < 24; i++) {
            lastDialog = confirmText();
            if (!lastDialog.isEmpty()) break;
            if (!watchedToasts().isEmpty() && i > 4) break;
            page.waitForTimeout(250);
        }
        if (!lastDialog.isEmpty() && answerDialog().isEmpty()) problems.add("a dialog (\"" + lastDialog + "\") has no YES/OK button");
        for (int i = 0; i < 120; i++) {                                          // up to ~30 s for the toast
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
        lastSaveMessage = lastSaveId = "";
        String[] c = null;
        synchronized (saveCalls) {
            if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1);
            else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1);
        }
        if (c == null) return;
        Matcher m = Pattern.compile("\"ResultStatus\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (m.find()) lastSaveStatus = Integer.parseInt(m.group(1));
        lastSaveMessage = jsonField(c[3], "Message");
        Matcher id = Pattern.compile("\"ResponseId\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (id.find()) lastSaveId = id.group(1);
        int http = Integer.parseInt(c[0]);
        if (http >= 400) lastSaveMessage = "HTTP " + http + " " + c[2].replaceAll("^https?://[^/]+", "") + " " + lastSaveMessage;
    }

    /** What the save call and any failed API call answered, for the report. */
    public String saveDiagnosis() {
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) {
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[1]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(200, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    /** The toast is a success message AND the server did not refuse the save. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("su(c)?cess|saved|added|created|submitted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    private static String jsonField(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json == null ? "" : json);
        return m.find() ? m.group(1).replace("\\\"", "\"") : "";
    }
}
