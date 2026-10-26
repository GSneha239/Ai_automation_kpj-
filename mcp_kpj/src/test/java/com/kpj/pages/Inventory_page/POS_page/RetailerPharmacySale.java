package com.kpj.pages.Inventory_page.POS_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inventory &gt; POS &gt; <b>Retailer Pharmacy Sale</b> — Page Object ({@code #/RetailerPharmacySale}).
 *
 * <p>Confirmed live (devhis build v2026.10.24.288). One screen, no patient: a header (<b>Date</b>, <b>Store</b>
 * {@code RetailerPharmacySale.StoreID}, <b>Supplier</b>, <b>Tax Type</b>), an <b>Item Search</b> strip and two grids.</p>
 * <ul>
 *   <li><b>Item Code</b> ({@code #txtItemCode}) and <b>Item Name</b> ({@code #txtServiceName}) are both TYPE-AHEAD fields over the same
 *       call, {@code POST /api/GRN/fetchItemByStore?StoreID=..&IN=<text>..}, keyed by the selected <b>Store</b> — nothing is offered until
 *       a Store is chosen. Picking a suggestion ({@code li.auto-complete-item}) fills the code, the name and the hidden item id. Typing
 *       in either box resets that item id, so a typed-but-not-picked item is not an item.</li>
 *   <li>With an item picked, <b>Enter</b> in the code/name box ({@code OnenterClick}) opens the <b>Batch List</b> dialog (one
 *       {@code tr[ng-click='setSelected($index,$event)']} per batch: Batch Code, Expiry, Selling Price, Purchase Rate, Available
 *       Quantity, ...). Clicking a row fills Batch / Expiry / Selling Price ({@code #mrpidfocus}) / Purchase Rate and moves focus to
 *       <b>Quantity</b>.</li>
 *   <li><b>Quantity</b> ({@code #PSqty}): Enter ({@code QuantityEvent}) moves focus to <b>Free Qty</b> ({@code #FreeQtyid}); Enter there
 *       ({@code AddDetails}) adds the line to <b>List of Items</b> and a row to <b>List of Total Items</b>, clears the strip and puts
 *       focus back on Item Name. A line shows SalesMan No, Code, Item Name, Batch No, Selling Price, Avl Qty, Qty (an input) and
 *       Total Amount (an input); the free quantity is kept on the line's model ({@code Itm.freequantity}) but has no column.</li>
 * </ul>
 *
 * <p><b>Known application defect (build v2026.10.24.288):</b> the type-ahead request is sent with {@code PsychotropicDrug=undefined&amp;
 * requesttype=undefined}, and the server answers <b>HTTP 400</b> ("... null entry for parameter 'PsychotropicDrug' of non-nullable type
 * 'System.Boolean' ..."), so NO suggestion ever appears for the item code or the item name. {@link #searchItem} reports exactly that.
 * {@link #simulatePick} exists only as an opt-in way to exercise the REST of the flow while the defect is open.</p>
 */
public class RetailerPharmacySale extends BasePage {

    /** Every {@code fetchItemByStore} response seen: {status, decoded url, body head}. */
    private final List<String[]> searchCalls = new ArrayList<>();
    private final Consumer<Response> searchListener = r -> {
        try {
            String u = r.url();
            if (!u.contains("fetchItemByStore")) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            synchronized (searchCalls) {
                searchCalls.add(new String[]{String.valueOf(r.status()), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 700 ? body.substring(0, 700) : body});
            }
        } catch (Exception ignore) { }
    };

    public RetailerPharmacySale(Page page) {
        super(page);
        page.onResponse(searchListener);
    }

    /** What the last fill step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastStore = "", lastSuggestion = "", lastCode = "", lastName = "", lastBatch = "", lastSellingPrice = "", lastAvailable = "", lastQty = "",
            lastFree = "", lastToast = "", batchNote = "", searchDiagnosis = "", lastItemRow = "", lastTotalsRow = "", lastLineFree = "";
    public int lastSearchStatus = 0, itemRowCount = 0, totalRowCount = 0;
    /** True once {@link #simulatePick} stood in for a suggestion click. */
    public boolean simulated = false;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String STORE_SELECT = "select[ng-model='RetailerPharmacySale.StoreID']";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Inventory &gt; POS &gt; Retailer Pharmacy Sale. Only opens the groups that are shut; the menu is awaited first (built seconds after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Inventory')",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        // Each group is opened ONCE and awaited: the menu builds each level lazily, a second click while it is still building closes the group again, and a click on POS
        // straight after Inventory is lost (seen on devhis v2026.10.26.291) — so Inventory gets a settling pause before POS is clicked.
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (groupState("POS") < 0) {                                  // POS is not on screen: Inventory is shut
                    if (groupState("Inventory") != 1) clickGroup("Inventory");
                    for (int i = 0; i < 48 && groupState("POS") < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (groupState("POS") == 0 && !linkVisible()) clickGroup("POS");
                for (int i = 0; i < 48 && !linkVisible(); i++) page.waitForTimeout(250);
            }
            if (linkVisible()) {
                page.evaluate("() => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()==='#/retailerpharmacysale'); if(l) l.id='__rpsLink'; }");
                try { page.locator("#__rpsLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("RetailerPharmacySale.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__rpsLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => !!document.getElementById('txtItemCode') && !!document.getElementById('PSqty')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("RetailerPharmacySale.nav: the item strip is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/retailerpharmacysale/?$"); }

    private boolean linkVisible() {
        Object r = page.evaluate("() => {" + JS + " return !![...document.querySelectorAll('a[href]')].find(x=>vis(x) && (x.getAttribute('href')||'').toLowerCase()==='#/retailerpharmacysale'); }");
        return Boolean.TRUE.equals(r);
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

    // ---- small helpers ---------------------------------------------------------------------------------------

    private String value(String id) {
        Object v = page.evaluate("(id) => { const e=document.getElementById(id); return e? e.value : ''; }", id);
        return v == null ? "" : v.toString().trim();
    }

    /** An Angular expression evaluated in the scope of the element matching {@code css}. */
    private String model(String css, String expr) {
        Object v = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(a.x); return v==null?'':String(v); }catch(x){ return ''; } }", Map.of("s", css, "x", expr));
        return v == null ? "" : v.toString().trim();
    }

    public String activeId() {
        Object v = page.evaluate("() => { const e=document.activeElement; return e? (e.id||e.tagName) : ''; }");
        return v == null ? "" : v.toString();
    }

    /** The text of every toast / alert dialog on screen. */
    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('#toast-container .toast, .toast-message, .jconfirm-content')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    private static double num(String s) {
        if (s == null) return Double.NaN;
        String t = s.replaceAll("[^0-9.\\-]", "");
        try { return t.isEmpty() ? Double.NaN : Double.parseDouble(t); } catch (NumberFormatException e) { return Double.NaN; }
    }

    // ---- store -----------------------------------------------------------------------------------------------

    /** Choose the Store (not in the written steps: the item search is keyed by it and returns nothing without one). */
    public String selectStore(String want) {
        problems.clear();
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }", STORE_SELECT,
                    new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add("the Store list has no options"); return ""; }
        Object t = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(a.s); const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=o.find(x=>x.t.toLowerCase()===a.w.toLowerCase()); if(!p) p=o.find(x=>x.t.toLowerCase().indexOf(a.w.toLowerCase())>=0); if(!p) return '';"
                + " e.value=p.v; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', p.v); }catch(x){} } return p.t; }", Map.of("s", STORE_SELECT, "w", want));
        lastStore = t == null ? "" : t.toString();
        if (lastStore.isEmpty()) { problems.add("the Store list has no \"" + want + "\""); return ""; }
        waitForAngular(1500);
        String m = model(STORE_SELECT, "RetailerPharmacySale.StoreID");
        if (m.isEmpty() || "0".equals(m)) problems.add("the Store is not held by the model");
        return lastStore;
    }

    // ---- item search (type-ahead) ----------------------------------------------------------------------------

    private boolean typeInto(String id, String term) {
        Locator in = page.locator("#" + id);
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(term, new Locator.PressSequentiallyOptions().setDelay(60));
            return true;
        } catch (Exception e) { problems.add("\"" + term + "\" could not be typed into #" + id); return false; }
    }

    public List<String> suggestions() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('li.auto-complete-item')].filter(vis).map(l=>norm(l.textContent)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    private String[] callFor(String term, boolean failedOnly) {
        synchronized (searchCalls) {
            for (int i = searchCalls.size() - 1; i >= 0; i--) {
                String[] c = searchCalls.get(i);
                if (!c[1].contains("IN=" + term)) continue;
                if (failedOnly && Integer.parseInt(c[0]) < 400) continue;
                return c;
            }
        }
        return null;
    }

    private static String jsonField(String body, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body == null ? "" : body);
        return m.find() ? m.group(1) : "";
    }

    private String diagnoseSearch(String term) {
        String[] bad = callFor(term, true);
        if (bad != null) {
            lastSearchStatus = Integer.parseInt(bad[0]);
            String detail = jsonField(bad[2], "MessageDetail");
            if (detail.isEmpty()) detail = jsonField(bad[2], "Message");
            if (detail.length() > 230) detail = detail.substring(0, 230) + "…";
            boolean undef = bad[1].contains("PsychotropicDrug=undefined");
            return "the item search request (/api/GRN/fetchItemByStore) failed with HTTP " + bad[0] + (detail.isEmpty() ? "" : " — " + detail)
                    + (undef ? " | CAUSE: the screen sends PsychotropicDrug=undefined and requesttype=undefined, which the server cannot read into a Boolean — an APPLICATION DEFECT "
                            + "(not a test problem): no suggestion list can appear for the item code or the item name" : "");
        }
        String[] any = callFor(term, false);
        if (any != null) {
            lastSearchStatus = Integer.parseInt(any[0]);
            return "the item search answered HTTP " + any[0] + " but offered no suggestion for \"" + term + "\" (does the selected Store hold it?)";
        }
        return "no item search request was made when \"" + term + "\" was typed (is a Store selected?)";
    }

    /** Type {@code term} into the Item Code ({@code byCode}) or Item Name box and wait for the suggestion list; fills {@link #searchDiagnosis} when there is none. */
    public List<String> searchItem(boolean byCode, String term) {
        problems.clear();
        searchDiagnosis = "";
        lastSearchStatus = 0;
        synchronized (searchCalls) { searchCalls.clear(); }
        String id = byCode ? "txtItemCode" : "txtServiceName", other = byCode ? "txtServiceName" : "txtItemCode";
        try { page.locator("#" + other).fill("", new Locator.FillOptions().setTimeout(5000)); } catch (Exception ignore) { }
        if (!typeInto(id, term)) return new ArrayList<>();
        long start = System.currentTimeMillis();
        List<String> sugg = new ArrayList<>();
        while (System.currentTimeMillis() - start < 10000) {
            sugg = suggestions();
            if (!sugg.isEmpty()) break;
            // a failed request answers at once: give a late render a few seconds, then stop waiting
            if (callFor(term, true) != null && System.currentTimeMillis() - start > 3000) break;
            page.waitForTimeout(250);
        }
        if (sugg.isEmpty()) { searchDiagnosis = diagnoseSearch(term); problems.add(searchDiagnosis); }
        return sugg;
    }

    /** Click the first suggestion containing {@code mustContain}; verifies the item id was set and the code / name boxes were filled. */
    public String pickSuggestion(String mustContain) {
        problems.clear();
        Object s = page.evaluate("(t) => {" + JS
                + " const lis=[...document.querySelectorAll('li.auto-complete-item')].filter(vis);"
                + " const li=lis.find(l=>norm(l.textContent).toLowerCase().indexOf(t.toLowerCase())>=0); if(!li) return '';"
                + " li.id='__rpsSugg'; return norm(li.textContent); }", mustContain);
        lastSuggestion = s == null ? "" : s.toString();
        if (lastSuggestion.isEmpty()) { problems.add("no suggestion contains \"" + mustContain + "\""); return ""; }
        try { page.locator("#__rpsSugg").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the suggestion could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__rpsSugg'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1200);
        recordItem();
        return lastSuggestion;
    }

    private void recordItem() {
        lastCode = value("txtItemCode");
        lastName = value("txtServiceName");
        String id = model("#txtItemCode", "RetailerPharmacySaleDetails.itemid");
        if (lastCode.isEmpty()) problems.add("choosing the item did not fill the item code");
        if (lastName.isEmpty()) problems.add("choosing the item did not fill the item name");
        if (id.isEmpty() || "0".equals(id)) problems.add("the item id was not set (the item is typed, not picked)");
    }

    /**
     * ONLY for exercising the rest of the flow while the screen's own item search is broken (see the class comment): asks the same endpoint WITH the
     * parameters the screen forgets to send, and hands the matching record to the type-ahead's own {@code itemSelected} callback — exactly what a click on a
     * suggestion does. Returns an empty string on success, otherwise why not.
     */
    public String simulatePick(String term, String wantCode) {
        Object r = page.evaluate("async (a) => {" + JS
                + " const sc=angular.element(document.getElementById('txtItemCode')).scope();"
                + " const store=sc.RetailerPharmacySale.StoreID, tax=sc.RetailerPharmacySale.TaxType;"
                + " const u='/api/GRN/fetchItemByStore?StoreID='+store+'&TaxType='+(tax==null?0:tax)+'&IC=&IN='+encodeURIComponent(a.t)"
                + "   +'&subgrpid=0&ExcelFlag=AutoCompleteOptionsForItemName&isfromsale=1&PsychotropicDrug=false&requesttype=0&opd_ipd_id=0&opd_ipd=0';"
                + " const r=await fetch(u,{method:'POST',headers:{'Content-Type':'application/json'},body:'{}',credentials:'include'});"
                + " if(!r.ok) return 'the endpoint answered HTTP '+r.status+' even with the missing parameters';"
                + " const j=await r.json(); const item=j.find(x=>!a.c || x.ItemCode===a.c); if(!item) return 'the store does not offer '+(a.c||a.t);"
                + " sc.$apply(()=>sc.AutoCompleteOptionsForItemName.itemSelected({item:item})); return ''; }",
                Map.of("t", term, "c", wantCode == null ? "" : wantCode));
        String why = r == null ? "" : r.toString();
        if (!why.isEmpty()) return why;
        simulated = true;
        waitForAngular(1200);
        problems.clear();
        recordItem();
        return ok() ? "" : String.join("; ", problems);
    }

    // ---- batch -----------------------------------------------------------------------------------------------

    public boolean batchListOpen() {
        Object r = page.evaluate("() => {" + JS + " return !![...document.querySelectorAll('.modal')].find(m=>vis(m) && /batch list/i.test(norm(m.textContent))); }");
        return Boolean.TRUE.equals(r);
    }

    private boolean awaitBatchList(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (batchListOpen()) return true; page.waitForTimeout(250); }
        return batchListOpen();
    }

    /**
     * Not in the written steps, but the screen will not take a quantity without it: open the Batch List (Enter in the item code box, unless it is already open)
     * and click the first batch with at least {@code needQty} available — or {@code pinned} when given. Returns the batch code, "" on a problem.
     */
    @SuppressWarnings("unchecked")
    public String chooseBatch(double needQty, String pinned) {
        problems.clear();
        batchNote = "";
        if (!awaitBatchList(2500)) {
            try {
                page.locator("#txtItemCode").click(new Locator.ClickOptions().setTimeout(5000));
                page.keyboard().press("Enter");
            } catch (Exception e) { problems.add("Enter could not be pressed in the item code box"); return ""; }
            if (!awaitBatchList(8000)) {
                String batchRequired = model("#txtItemCode", "RetailerPharmacySaleDetails.batchrequired");
                if (!"true".equalsIgnoreCase(batchRequired) && !"1".equals(batchRequired)) { batchNote = "the item needs no batch"; return "(no batch required)"; }
                problems.add("the Batch List did not open although the item needs a batch");
                return "";
            }
        }
        // the dialog opens first and its batches arrive a moment later
        try {
            page.waitForFunction("() => { const m=[...document.querySelectorAll('.modal')].find(x=>(x.offsetWidth||x.offsetHeight) && /batch list/i.test(x.textContent||''));"
                    + " return !!m && m.querySelectorAll('tbody tr').length>0; }", null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }
        Object rows = page.evaluate("() => {" + JS
                + " const m=[...document.querySelectorAll('.modal')].filter(vis).find(x=>/batch list/i.test(norm(x.textContent))); if(!m) return [];"
                + " const heads=[...m.querySelectorAll('th')].filter(vis).map(h=>norm(h.textContent)); const bi=heads.indexOf('Batch Code'), qi=heads.indexOf('Available Quantity');"
                + " return [...m.querySelectorAll('tbody tr')].map((tr,i)=>{ const cells=[...tr.children].filter(vis);"
                + "   return {i:i, b:norm((cells[bi]||{}).textContent), q:norm((cells[qi]||{}).textContent)}; }); }");
        int pick = -1;
        String pickBatch = "";
        StringBuilder seen = new StringBuilder();
        if (rows instanceof List) {
            for (Object o : (List<Object>) rows) {
                Map<String, Object> m = (Map<String, Object>) o;
                String b = String.valueOf(m.get("b")), q = String.valueOf(m.get("q"));
                int idx = (int) num(String.valueOf(m.get("i")));
                if (seen.length() < 160) seen.append(seen.length() == 0 ? "" : ", ").append(b).append(" (").append(q).append(")");
                if (pick >= 0) continue;
                boolean named = pinned == null || pinned.isEmpty() || b.equalsIgnoreCase(pinned);
                if (named && num(q) >= needQty) { pick = idx; pickBatch = b; }
            }
        }
        if (pick < 0) {
            problems.add(seen.length() == 0 ? "the Batch List is empty — the store holds no stock of this item"
                    : (pinned == null || pinned.isEmpty() ? "no batch has " : "batch \"" + pinned + "\" does not have ") + "at least " + (long) Math.ceil(needQty) + " available — batches: " + seen);
            closeBatchList();
            return "";
        }
        Object tagged = page.evaluate("(i) => {" + JS
                + " const m=[...document.querySelectorAll('.modal')].filter(vis).find(x=>/batch list/i.test(norm(x.textContent)));"
                + " const tr=m.querySelectorAll('tbody tr')[i]; const td=[...tr.children].find(vis); if(!td) return false; td.id='__rpsBatchCell'; return true; }", pick);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the batch row could not be located"); closeBatchList(); return ""; }
        try { page.locator("#__rpsBatchCell").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the batch row could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__rpsBatchCell'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        lastBatch = value("batchenterfocus");
        lastSellingPrice = value("mrpidfocus");
        Object av = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='RetailerPharmacySale.TotalAvailablequantity']\"); return e? e.value : ''; }");
        lastAvailable = av == null ? "" : av.toString().trim();
        if (!lastBatch.equalsIgnoreCase(pickBatch)) problems.add("the Batch box shows \"" + lastBatch + "\" instead of \"" + pickBatch + "\"");
        if (batchListOpen()) problems.add("the Batch List is still open after the click");
        return pickBatch;
    }

    private void closeBatchList() {
        page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('.modal button')].filter(vis).find(x=>/FnClose/.test(x.getAttribute('ng-click')||'') && /close/i.test(norm(x.textContent))); if(b) b.click(); }");
        waitForAngular(600);
    }

    // ---- quantity, free quantity, add ------------------------------------------------------------------------

    private boolean typeNumber(String id, String ngModel, String text, String what) {
        Locator in = page.locator("#" + id);
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(60));
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
        waitForAngular(500);
        String shown = value(id), held = model("#" + id, ngModel);
        if (!text.equals(shown)) problems.add("the " + what + " box shows \"" + shown + "\" instead of \"" + text + "\"");
        if (!text.equals(held)) problems.add("the " + what + " is not held by the model (\"" + held + "\")");
        return ok();
    }

    public boolean enterQuantity(String qty) {
        problems.clear();
        boolean r = typeNumber("PSqty", "RetailerPharmacySaleDetails.quantity", qty, "Quantity");
        lastQty = value("PSqty");
        return r;
    }

    /** Enter in the Quantity box — the screen answers by moving focus to Free Qty. */
    public boolean pressEnterInQuantity() {
        problems.clear();
        try { page.keyboard().press("Enter"); } catch (Exception e) { problems.add("Enter could not be pressed"); return false; }
        for (int i = 0; i < 12 && !"FreeQtyid".equals(activeId()); i++) page.waitForTimeout(250);
        if (!"FreeQtyid".equals(activeId())) problems.add("focus did not move to Free Qty (it is on " + activeId() + ")" + (toastsNow().isEmpty() ? "" : "; the screen said \"" + toastsNow() + "\""));
        return ok();
    }

    public boolean enterFreeQty(String free) {
        problems.clear();
        boolean r = typeNumber("FreeQtyid", "RetailerPharmacySaleDetails.freequantity", free, "Free Qty");
        lastFree = value("FreeQtyid");
        return r;
    }

    /** Enter in the Free Qty box — {@code AddDetails} adds the line. Returns the number of lines in List of Items afterwards. */
    public int pressEnterToAdd(int itemsBefore) {
        problems.clear();
        lastToast = "";
        try { page.keyboard().press("Enter"); } catch (Exception e) { problems.add("Enter could not be pressed"); return itemsBefore; }
        int n = itemsBefore;
        for (int i = 0; i < 32; i++) {
            n = readRows("Item Name").size();
            if (n > itemsBefore) break;
            page.waitForTimeout(250);
        }
        waitForAngular(800);
        lastToast = toastsNow();
        if (n <= itemsBefore) problems.add("no line was added to List of Items" + (lastToast.isEmpty() ? "" : "; the screen said \"" + lastToast + "\""));
        return n;
    }

    // ---- the two grids ---------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> toRows(Object r) {
        List<Map<String, String>> out = new ArrayList<>();
        if (!(r instanceof List)) return out;
        for (Object o : (List<Object>) r) {
            Map<String, String> m = new LinkedHashMap<>();
            if (o instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) m.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            out.add(m);
        }
        return out;
    }

    /** The rows of the (non-dialog) table that has a visible column headed {@code mustHave}, as visible-header -> value (the value of the cell's input when it has one). */
    public List<Map<String, String>> readRows(String mustHave) {
        Object r = page.evaluate("(want) => {" + JS
                + " const t=[...document.querySelectorAll('table')].find(x=>!x.closest('.modal') && [...x.querySelectorAll('th')].filter(vis).some(h=>norm(h.textContent)===want));"
                + " if(!t) return [];"
                + " const heads=[...t.querySelectorAll('th')].filter(vis).map(h=>norm(h.textContent));"
                + " return [...t.querySelectorAll('tbody tr')].map(tr=>{ const cells=[...tr.children].filter(vis); const o={};"
                + "   heads.forEach((h,i)=>{ const c=cells[i]; if(!c||!h) return; const inp=c.querySelector('input'); o[h]=inp? inp.value : norm(c.textContent); }); return o; }); }", mustHave);
        return toRows(r);
    }

    public int itemLines() { return readRows("Item Name").size(); }
    public int totalLines() { return readRows("External Sale").size(); }

    private void scrollTable(String head) {
        page.evaluate("(h) => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>!x.closest('.modal') && [...x.querySelectorAll('th')].some(y=>norm(y.textContent)===h)); if(t) t.scrollIntoView({block:'center'}); }", head);
        waitForAngular(500);
    }

    public void showItemsGrid() { scrollTable("Item Name"); }
    public void showTotalsGrid() { scrollTable("External Sale"); }

    private String lineFree(int index) {
        Object v = page.evaluate("(i) => { const trs=[...document.querySelectorAll(\"tr[ng-repeat^='Itm in RetailerPharmacySaleItemList']\")]; const tr=trs[i]; if(!tr) return '';"
                + " try{ const v=angular.element(tr).scope().Itm.freequantity; return v==null?'':String(v); }catch(e){ return ''; } }", index);
        return v == null ? "" : v.toString().trim();
    }

    /**
     * After the add: List of Items carries one more line for this item (code, name, batch, quantity, a total), and List of Total Items carries a row whose
     * Total Amount is the sum of the lines' Total Amounts and whose Net Amount is that plus the Tax Amount.
     */
    public boolean verifyAdded(int itemsBefore, String code, String name, String batch, String qty, String free) {
        problems.clear();
        List<Map<String, String>> items = readRows("Item Name"), totals = readRows("External Sale");
        itemRowCount = items.size();
        totalRowCount = totals.size();
        if (items.size() != itemsBefore + 1) problems.add("List of Items has " + items.size() + " line(s), expected " + (itemsBefore + 1));
        int at = -1;
        for (int i = items.size() - 1; i >= 0; i--) {
            Map<String, String> r = items.get(i);
            if (code.equalsIgnoreCase(r.getOrDefault("Code", "")) && (batch.isEmpty() || batch.equalsIgnoreCase(r.getOrDefault("Batch No", "")))) { at = i; break; }
        }
        lastItemRow = "";
        if (at < 0) {
            problems.add("List of Items has no line for " + code + (batch.isEmpty() ? "" : " / batch " + batch) + (items.isEmpty() ? "" : " — it holds " + items));
        } else {
            Map<String, String> r = items.get(at);
            lastItemRow = "No " + r.getOrDefault("SalesMan No", "") + " | " + r.getOrDefault("Code", "") + " | " + r.getOrDefault("Item Name", "") + " | batch " + r.getOrDefault("Batch No", "")
                    + " | selling price " + r.getOrDefault("Selling Price", "") + " | avl qty " + r.getOrDefault("Avl Qty", "") + " | qty " + r.getOrDefault("Qty", "") + " | total " + r.getOrDefault("Total Amount", "");
            if (!r.getOrDefault("Item Name", "").toLowerCase().contains(name.toLowerCase())) problems.add("the line's Item Name is \"" + r.getOrDefault("Item Name", "") + "\", not \"" + name + "\"");
            if (!(num(r.getOrDefault("Qty", "")) == num(qty))) problems.add("the line's Qty is \"" + r.getOrDefault("Qty", "") + "\" instead of " + qty);
            if (!(num(r.getOrDefault("Total Amount", "")) > 0)) problems.add("the line's Total Amount is \"" + r.getOrDefault("Total Amount", "") + "\" (expected a positive amount)");
            lastLineFree = lineFree(at);
            if (!lastLineFree.isEmpty() && !(num(lastLineFree) == num(free))) problems.add("the line holds Free Qty \"" + lastLineFree + "\" instead of " + free);
        }
        lastTotalsRow = "";
        if (totals.isEmpty()) {
            problems.add("List of Total Items has no row");
        } else {
            double sumItems = 0;
            for (Map<String, String> r : items) { double t = num(r.getOrDefault("Total Amount", "")); if (!Double.isNaN(t)) sumItems += t; }
            double sumTotals = 0, sumNet = 0, sumTax = 0;
            StringBuilder sb = new StringBuilder();
            for (Map<String, String> r : totals) {
                double t = num(r.getOrDefault("Total Amount", "")), n = num(r.getOrDefault("Net Amount", "")), x = num(r.getOrDefault("Tax Amount", ""));
                if (!Double.isNaN(t)) sumTotals += t;
                if (!Double.isNaN(n)) sumNet += n;
                if (!Double.isNaN(x)) sumTax += x;
                sb.append(sb.length() == 0 ? "" : " ; ").append("No ").append(r.getOrDefault("Number", "")).append(" | total ").append(r.getOrDefault("Total Amount", ""))
                        .append(" | tax ").append(r.getOrDefault("Tax Amount", "")).append(" | net ").append(r.getOrDefault("Net Amount", ""));
            }
            lastTotalsRow = sb.toString();
            if (itemsBefore == 0 && totals.size() != 1) problems.add("List of Total Items has " + totals.size() + " row(s), expected 1");
            if (Math.abs(sumTotals - sumItems) > 0.011) problems.add("List of Total Items shows a Total Amount of " + sumTotals + " but the lines add up to " + sumItems);
            if (Math.abs(sumNet - (sumTotals + sumTax)) > 0.011) problems.add("List of Total Items shows a Net Amount of " + sumNet + ", not Total Amount + Tax Amount (" + (sumTotals + sumTax) + ")");
            if (!(sumTotals > 0)) problems.add("List of Total Items shows no positive Total Amount");
        }
        return ok();
    }
}
