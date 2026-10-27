package com.kpj.pages.Inventory_page.Others_page;

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
 * Inventory &gt; Others &gt; <b>Item Factorization Compounding</b> — Page Object ({@code #/ItemFactorizationCompoundingList}, form {@code #/add-ItemFactorizationCompounding}).
 *
 * <p>Confirmed live (devhis build v2026.10.27.303). The list ({@code ItemFactorizationCompoundingList.*}) has a <b>New</b> button ({@code AddItemFacto()}); the form
 * ({@code ItemFactorizationCompounding.*}) has a Date, a <b>Store*</b> ({@code .StoreID}, select2, {@code StoreChange()}), a Template, a Transaction Type (Compounding is the default,
 * Repacking the other) and a <b>List of Items</b> table whose FIRST row is the entry strip:</p>
 * <ul>
 *   <li><b>Code</b> ({@code #txtItemCode}, {@code ItemFactorizationCompoundingDetails.itemcode}) is a TYPE-AHEAD ({@code li.auto-complete-item} from
 *       {@code /api/ItemFactorizationCompounding/fetchItemByStore}; the same suggestion is often listed twice). Clicking one fills <b>Item Name</b> ({@code #txtServiceName}), Selling Price,
 *       WAC, Conversion Factor and UOM.</li>
 *   <li>Clicking the item name and pressing <b>Enter</b> opens the <b>Batch List</b> dialog (a table; its first row is "New Batch" with no stock, the real batches follow; the
 *       Index / Id / hidden price columns are not shown, so a click has to land on a VISIBLE cell). Clicking a batch row ({@code setSelected($index,$event)}) closes the dialog and fills
 *       Batch ({@code #batchenterfocus}), Expiry Date, Available Stock and the prices.</li>
 *   <li><b>Quantity</b> ({@code #PSqty}) and <b>Added/Deducted</b> ({@code #AddedDeductedDropdownList}: Deducted = 0, Added = 1); the <b>+</b> button
 *       ({@code AddItemFactorizationCompoundingDetails($event)}) moves the strip into the table (the strip clears, the new row follows it). <b>Save</b> is {@code IUDSave()}.</li>
 * </ul>
 * Saving is REAL: stock of the Deducted item goes down and the Added item's goes up.
 */
public class ItemFactorizationCompounding extends BasePage {

    /** Every POST the screen made that is not a plain fetch, plus any API call that failed: {status, method, decoded url, body head}. */
    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final List<String[]> searchCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean search = low.contains("fetchitembystore");
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/");
            boolean bad = r.status() >= 400;
            if (!search && !write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (search) searchCalls.add(rec); if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public ItemFactorizationCompounding(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastStore = "", lastCode = "", lastName = "", lastSuggestion = "", lastBatch = "", lastBatchRow = "", lastExpiry = "", lastAvailable = "", lastQty = "", lastMode = "",
            lastRow = "", lastDialog = "", lastToast = "", lastSaveMessage = "", lastSaveId = "", searchDiagnosis = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const modal=()=>[...document.querySelectorAll('.modal')].filter(vis).pop();"
          + "const listTable=()=>[...document.querySelectorAll('table')].filter(vis).find(t=>/Added\\/Deducted/.test(norm(t.textContent)) && /Conversion Factor/.test(norm(t.textContent)));";

    private static final String STORE_SELECT = "ItemFactorizationCompounding.StoreID";
    private static final String CODE_BOX = "#txtItemCode";
    private static final String NAME_BOX = "#txtServiceName";
    private static final String QTY_BOX = "#PSqty";
    private static final String MODE_SELECT = "#AddedDeductedDropdownList";
    private static final String BATCH_BOX = "#batchenterfocus";
    private static final String HREF = "#/itemfactorizationcompoundinglist";

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
     * Inventory &gt; Others &gt; Item Factorization Compounding. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the
     * group again, and a click on Others straight after Inventory is lost), so Inventory gets a settling pause before Others is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!linkVisible()) {
                if (groupState("Others") < 0) {                                // Others is not on screen: Inventory is shut
                    if (groupState("Inventory") != 1) clickGroup("Inventory");
                    for (int i = 0; i < 48 && groupState("Others") < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);                                 // let the Inventory level finish building before Others is clicked
                }
                if (groupState("Others") == 0 && !linkVisible()) clickGroup("Others");
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__ifcLink'; }", HREF);
                try { page.locator("#__ifcLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("ItemFactorizationCompounding.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__ifcLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll('button')].find(b=>/AddItemFacto\\(/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("ItemFactorizationCompounding.nav: the New button is not ready"); }
        waitForAngular(1000);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/itemfactorizationcompoundinglist/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-itemfactorizationcompounding/?$")
                && Boolean.TRUE.equals(page.evaluate("(n) => {" + JS + " return !![...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); }", STORE_SELECT));
    }

    // ---- small helpers ---------------------------------------------------------------------------------------

    /** Tag the first visible button whose ng-click matches {@code re} and click it with a real mouse click. */
    private boolean clickByNg(String re, String what) {
        Object tagged = page.evaluate("(re) => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>new RegExp(re).test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__ifcBtn'; return true; }", re);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the " + what + " button is not on screen"); return false; }
        try { page.locator("#__ifcBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { problems.add("the " + what + " button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__ifcBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    private String boxValue(String css) {
        Object v = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.value : ''; }", css);
        return v == null ? "" : v.toString().trim();
    }

    private String selectedText(String css) {
        Object v = page.evaluate("(s) => {" + JS + " const e=document.querySelector(s); if(!e) return ''; const o=e.options[e.selectedIndex]; const t=o? norm(o.textContent) : ''; return /^-*\\s*select\\s*-*$/i.test(t)? '' : t; }", css);
        return v == null ? "" : v.toString().trim();
    }

    private boolean typeInto(String css, String text, String what) {
        Locator in = page.locator(css).first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(70));
            return true;
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
    }

    /** A visible alert / confirmation dialog's text ("" if none). */
    public String confirmText() {
        Object r = page.evaluate("() => {" + JS + " const d=[...document.querySelectorAll('.jconfirm, .ng-confirm-box')].filter(vis).map(x=>norm(x.textContent).replace(/^[^A-Za-z0-9]+/,''));"
                + " return d.length? d[d.length-1] : ''; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Click the affirmative button (YES, else OK) of the visible alert / confirmation; returns its label, "" when there is none. These dialogs can be STACKED (the same alert raised twice at
     * the same spot), so the button tagged is the one that is actually on top at its own centre, and the answer repeats while a dialog is still showing.
     */
    private String answerDialog() {
        String first = "";
        for (int round = 0; round < 4; round++) {
            Object label = page.evaluate("() => {" + JS + " const bs=[...document.querySelectorAll('.jconfirm button, .ng-confirm-box button')].filter(vis);"
                    + " const want=bs.filter(x=>/^yes$/i.test(norm(x.textContent))); const alt=bs.filter(x=>/^(ok|okay|confirm)$/i.test(norm(x.textContent)));"
                    + " const cand=(want.length? want : alt).reverse(); if(!cand.length) return '';"
                    + " const onTop=x=>{ const r=x.getBoundingClientRect(); const t=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return !!t && x.contains(t); };"
                    + " const b=cand.find(onTop) || cand[0]; b.id='__ifcAns'; return norm(b.textContent); }");
            String l = label == null ? "" : label.toString();
            if (l.isEmpty()) break;
            if (first.isEmpty()) first = l;
            try { page.locator("#__ifcAns").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) {
                System.out.println("ItemFactorizationCompounding.answerDialog: \"" + l + "\" click failed - " + e.getMessage().split("\n")[0]);
                problems.add("\"" + l + "\" could not be clicked");
                break;
            }
            finally { page.evaluate("() => { const e=document.getElementById('__ifcAns'); if(e) e.removeAttribute('id'); }"); }
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

    // ---- list -> New -> store ----------------------------------------------------------------------------------

    /** Click New on the list and wait for the form. */
    public boolean clickNew() {
        problems.clear();
        if (!clickByNg("AddItemFacto\\(", "New")) return false;
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        waitForAngular(1500);
        if (!onForm()) problems.add("the Item Factorization Compounding form did not open (" + page.url() + ")");
        return ok();
    }

    /** Choose the Store (the item search is keyed by it). Matches ignoring case and spaces. */
    public String selectStore(String want) {
        problems.clear();
        try { page.waitForFunction("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e && e.options.length>1; }", STORE_SELECT, new Page.WaitForFunctionOptions().setTimeout(10000)); }
        catch (Exception e) { problems.add("the Store list has no options"); return ""; }
        Object t = page.evaluate("(a) => {" + JS
                + " const e=[...document.querySelectorAll(\"[ng-model='\"+a.n+\"']\")].find(vis)||document.querySelector(\"[ng-model='\"+a.n+\"']\"); if(!e) return '';"
                + " const k=s=>norm(s).toLowerCase().replace(/\\s+/g,'');"
                + " const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value,i:x.index})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=o.find(x=>k(x.t)===k(a.w)); if(!p) p=o.find(x=>k(x.t).indexOf(k(a.w))>=0); if(!p) return '';"
                + " e.selectedIndex=p.i; e.dispatchEvent(new Event('change',{bubbles:true})); try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', e.value); }catch(x){} try{ $(e).trigger('change'); }catch(x){} } return p.t; }", Map.of("n", STORE_SELECT, "w", want));
        lastStore = t == null ? "" : t.toString();
        if (lastStore.isEmpty()) { problems.add("the Store list has no \"" + want + "\""); return ""; }
        waitForAngular(2000);
        Object held = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", STORE_SELECT);
        if (held == null || held.toString().isEmpty() || "0".equals(held.toString())) problems.add("the Store is not held by the model");
        return lastStore;
    }

    // ---- one item: code -> suggestion -> name + Enter -> batch -> quantity -> Added/Deducted -> + ----------------------

    /** Type the item code into the Code box (this starts the type-ahead). */
    public boolean enterCode(String code) {
        problems.clear();
        searchDiagnosis = "";
        synchronized (saveCalls) { searchCalls.clear(); }
        if (!typeInto(CODE_BOX, code, "Item Code")) return false;
        waitForAngular(400);
        lastCode = boxValue(CODE_BOX);
        if (!code.equals(lastCode)) problems.add("the Code box shows \"" + lastCode + "\" instead of \"" + code + "\"");
        return ok();
    }

    /** The visible type-ahead suggestions (de-duplicated, in order). */
    public List<String> suggestions() {
        Object r = page.evaluate("() => {" + JS + " return [...new Set([...document.querySelectorAll('li.auto-complete-item')].filter(vis).map(x=>norm(x.textContent)))]; }");
        return toList(r);
    }

    /** Wait up to {@code ms} for a suggestion that mentions {@code term}; empty when none appears (the reason is then in {@link #searchDiagnosis}). */
    public List<String> awaitSuggestions(String term, int ms) {
        long end = System.currentTimeMillis() + ms;
        List<String> s = new ArrayList<>();
        while (System.currentTimeMillis() < end) {
            s = suggestions();
            if (s.stream().anyMatch(x -> x.toLowerCase().contains(term.toLowerCase()))) return s;
            page.waitForTimeout(300);
        }
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) {
            for (String[] c : searchCalls) {
                String u = c[2].replaceAll("^https?://[^/]+", ""), b = c[3].replaceAll("\\s+", " ");
                sb.append("[").append(c[0]).append(" ").append(u, 0, Math.min(140, u.length())).append(" -> ").append(b, 0, Math.min(120, b.length())).append("] ");
            }
        }
        searchDiagnosis = sb.length() == 0 ? "the item search was never called" : sb.toString().trim();
        return new ArrayList<>();
    }

    /** Click the suggestion that mentions {@code mustContain}; the item name, prices, conversion factor and UOM fill in. Returns the suggestion's text, "" when none could be clicked. */
    public String pickSuggestion(String mustContain) {
        problems.clear();
        Object tagged = page.evaluate("(t) => {" + JS + " const s=[...document.querySelectorAll('li.auto-complete-item')].filter(vis);"
                + " const x=s.find(y=>norm(y.textContent).toLowerCase().indexOf(t.toLowerCase())>=0) || s[0]; if(!x) return ''; x.id='__ifcSug'; return norm(x.textContent); }", mustContain);
        lastSuggestion = tagged == null ? "" : tagged.toString();
        if (lastSuggestion.isEmpty()) { problems.add("there is no suggestion to click"); return ""; }
        try { page.locator("#__ifcSug").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the suggestion could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__ifcSug'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 24 && boxValue(NAME_BOX).isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(500);
        lastName = boxValue(NAME_BOX);
        if (lastName.isEmpty()) problems.add("picking the suggestion did not fill the Item Name" + noticeSuffix());
        return ok() ? lastSuggestion : "";
    }

    private boolean batchDialogOpen() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); return !!m && /Batch Code/.test(norm(m.textContent)); }");
        return Boolean.TRUE.equals(r);
    }

    /** Click the item name and press Enter; the Batch List dialog opens. */
    public boolean openBatchList() {
        problems.clear();
        Locator n = page.locator(NAME_BOX).first();
        try { n.click(new Locator.ClickOptions().setTimeout(5000)); page.waitForTimeout(400); n.press("Enter"); }
        catch (Exception e) { problems.add("the Item Name box could not be clicked / Enter pressed"); return false; }
        for (int i = 0; i < 48 && !batchDialogOpen(); i++) page.waitForTimeout(250);
        waitForAngular(600);
        if (!batchDialogOpen()) problems.add("the Batch List dialog did not open" + noticeSuffix());
        return ok();
    }

    /** The Batch List rows as text (including the "New Batch" row). */
    public List<String> batchRows() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); const t=m&&m.querySelector('table'); return t? [...t.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(Boolean) : []; }");
        return toList(r);
    }

    /**
     * Click a batch row in the Batch List: the first real batch with Available Quantity &gt; 0 (the "New Batch" row has none; for a Deducted item the stock has to be there), else the first
     * row. The click lands on a VISIBLE cell (Index and Id are hidden columns). Returns the row's text, "" when the dialog has no row.
     */
    public String pickBatch() {
        problems.clear();
        Object t = page.evaluate("() => {" + JS + " const m=modal(); const t=m&&m.querySelector('table'); if(!t) return ''; const hs=[...t.querySelectorAll('th')].map(h=>norm(h.textContent));"
                + " const qi=hs.indexOf('Available Quantity'), bi=hs.indexOf('Batch Code'); const rows=[...t.querySelectorAll('tbody tr')]; if(!rows.length) return '';"
                + " const cell=(r,i)=>i<0? '' : norm(([...r.querySelectorAll('td')][i]||{}).textContent);"
                + " const r=rows.find(x=>parseFloat(cell(x,qi))>0) || rows[0]; const c=[...r.querySelectorAll('td')].find(vis); if(!c) return '';"
                + " c.id='__ifcBatch'; return norm(r.textContent) + '||' + cell(r,bi); }");
        String s = t == null ? "" : t.toString();
        if (s.isEmpty()) { problems.add("the Batch List has no row to click"); return ""; }
        lastBatchRow = s.substring(0, s.lastIndexOf("||"));
        String expect = s.substring(s.lastIndexOf("||") + 2);
        try { page.locator("#__ifcBatch").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the batch row could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__ifcBatch'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 24 && batchDialogOpen(); i++) page.waitForTimeout(250);
        waitForAngular(800);
        lastBatch = boxValue(BATCH_BOX);
        lastExpiry = boxValueModel("ItemFactorizationCompounding.expirydate");
        lastAvailable = boxValueModel("ItemFactorizationCompoundingDetails.availablestock");
        if (batchDialogOpen()) problems.add("the Batch List stayed open after the click");
        else if (lastBatch.isEmpty()) problems.add("clicking the batch row did not fill the Batch box" + noticeSuffix());
        else if (!expect.isEmpty() && !lastBatch.equals(expect)) problems.add("the Batch box shows \"" + lastBatch + "\" instead of \"" + expect + "\"");
        return ok() ? lastBatchRow : "";
    }

    private String boxValueModel(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e? e.value : ''; }", ng);
        return v == null ? "" : v.toString().trim();
    }

    public boolean enterQuantity(String qty) {
        problems.clear();
        if (!typeInto(QTY_BOX, qty, "Quantity")) return false;
        waitForAngular(500);
        lastQty = boxValue(QTY_BOX);
        if (!qty.equals(lastQty)) problems.add("the Quantity box shows \"" + lastQty + "\" instead of \"" + qty + "\"");
        return ok();
    }

    /** Choose Added or Deducted in the strip's Added/Deducted select. Returns the option chosen. */
    public String selectMode(String want) {
        problems.clear();
        Object t = page.evaluate("(a) => {" + JS + " const e=document.querySelector(a.s); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>norm(o.textContent).toLowerCase()===a.w.toLowerCase()); if(i<0) return '';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{ angular.element(e).triggerHandler('change'); }catch(x){} return norm(e.options[i].textContent); }",
                Map.of("s", MODE_SELECT, "w", want));
        lastMode = t == null ? "" : t.toString();
        if (lastMode.isEmpty()) { problems.add("the Added/Deducted list has no \"" + want + "\""); return ""; }
        waitForAngular(800);
        if (!lastMode.equalsIgnoreCase(selectedText(MODE_SELECT))) problems.add("the Added/Deducted list shows \"" + selectedText(MODE_SELECT) + "\" instead of \"" + want + "\"");
        if (!confirmText().isEmpty() || !toastsNow().isEmpty()) problems.add("choosing " + want + " raised a message" + noticeSuffix());
        return ok() ? lastMode : "";
    }

    /** The added rows of the List of Items table (the entry strip is not counted): each row is its cell values joined by " | ". */
    public List<String> itemRows() {
        Object r = page.evaluate("() => {" + JS + " const t=listTable(); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(r=>[...r.querySelectorAll('td')].map(td=>{ const e=td.querySelector('input,select');"
                + "   if(e && e.tagName==='SELECT') return norm((e.options[e.selectedIndex]||{}).textContent).replace(/^-*\\s*select\\s*-*$/i,''); return e? e.value : norm(td.textContent).slice(0,60); })"
                + "   .join(' | ')).slice(1).filter(x=>x.split(' | ')[0].trim()!=='' ); }");        // slice(1): the first row is the entry strip
        return toList(r);
    }

    /** Click the + button; waits for one more row in the table. Returns that row's text, "" when no row was added. */
    public String clickAdd() {
        problems.clear();
        int before = itemRows().size();
        if (!clickByNg("AddItemFactorizationCompoundingDetails", "+ (add)")) return "";
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end && itemRows().size() <= before && confirmText().isEmpty() && toastsNow().isEmpty()) page.waitForTimeout(250);
        waitForAngular(600);
        List<String> rows = itemRows();
        if (rows.size() <= before) { problems.add("the + button added no row to the table" + noticeSuffix()); return ""; }
        lastRow = rows.get(rows.size() - 1);
        return lastRow;
    }

    // ---- save ------------------------------------------------------------------------------------------------

    private void installToastWatch() {
        page.evaluate("() => { window.__ifcToasts=[]; if(window.__ifcObs) window.__ifcObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__ifcToasts.includes(m)) window.__ifcToasts.push(m); });"
                + " window.__ifcObs=new MutationObserver(add); window.__ifcObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__ifcToasts||[]).join(' | ')");
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

    private static List<String> toList(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }
}
