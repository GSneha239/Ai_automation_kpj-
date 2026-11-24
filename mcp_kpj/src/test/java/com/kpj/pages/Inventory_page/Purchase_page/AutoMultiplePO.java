package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory &gt; Purchase &gt; <b>Auto/ Multiple PO</b> — Page Object ({@code #/AutoPO}, header "Auto/ Multiple PO").
 *
 * <p>Seen live (devhis build v2026.11.24.524). One screen ({@code AutoPO.*}):</p>
 * <ul>
 *   <li><b>Store*</b> ({@code AutoPO.StoreID}, a select2 list of ~145 stores), <b>Date</b> ({@code AutoPO.PODate}, today), a select-all tick ({@code AutoPO.ChkSelectAll}) and the
 *       Item Code / Item Name filters;</li>
 *   <li><b>Search</b> ({@code getStockAutoPOSearchList()}) fills <b>Auto PO Details</b>: Item Code | Item Name | UOM | Available Qty | ReOrder Qty | PO Quantity | Free Quantity |
 *       Conversion Factor | Current Month Sale | Last Month Sale | PO Number | PO Date | Rate Contract | Supplier | T &amp; C | Planned Delivery Date, one tick box per row;</li>
 *   <li>ticking a row fills its <b>Supplier</b> by itself (from the item's rate contract); <b>Generate PO</b> ({@code IUDAutoPO()}) creates the purchase order(s) of the ticked rows.</li>
 * </ul>
 * <p>Generate PO writes a REAL purchase order.</p>
 */
public class AutoMultiplePO extends BasePage {

    public AutoMultiplePO(Page page) { super(page); }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastStore = "", lastItem = "", lastSupplier = "", lastToast = "", lastSaveHttp = "", lastPoNumber = "", lastRowFacts = "";
    public int lastRowCount = 0, lastSearchStatus = 0;

    private static final String STORE_NG = "AutoPO.StoreID";
    private static final String HREF = "#/autopo";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const detailTable=()=>[...document.querySelectorAll('table')].filter(vis).find(t=>/Item Code/.test(norm([...t.querySelectorAll('th')].map(h=>h.textContent).join(' '))) && /PO Quantity/.test(norm(t.textContent)));"
          + "const dataRows=()=>{ const t=detailTable(); return t? [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox]')) : []; };"
          // header text -> cell index (a leading tick-box column makes the cells one more than the heads: the offset is taken from the first row)
          + "const colIdx=(re)=>{ const t=detailTable(); if(!t) return -1; const heads=[...t.querySelectorAll('thead th, thead td')].map(h=>norm(h.textContent).toLowerCase());"
          + "  const i=heads.findIndex(h=>re.test(h)); if(i<0) return -1; const r=dataRows()[0]; const off=r? (r.querySelectorAll('td').length - heads.length) : 0; return i + (off>0? off : 0); };"
          + "const cellText=(r,i)=>{ if(i<0) return ''; const c=r.querySelectorAll('td')[i]; if(!c) return '';"
          + "  const s=c.querySelector('select'); if(s){ const o=s.options[s.selectedIndex]; return o? norm(o.textContent).replace(/^-*\\s*select\\s*-*$/i,'') : ''; }"
          + "  const inp=c.querySelector('input:not([type=checkbox])'); if(inp) return norm(inp.value);"
          + "  return norm(c.textContent); };";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Inventory &gt; Purchase &gt; Auto/ Multiple PO: each level opened only if shut, the leaf looked up by its exact route. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*inventory\\s*$");
            expandMenuEntry("^\\s*inventory\\s*$", "^\\s*purchase\\s*$");
            Object tagged = page.evaluate("(h) => {" + JS + " const a=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__apoLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__apoLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    System.out.println("AutoMultiplePO.nav: real click refused - clicking in the page");
                    try { page.evaluate("() => { const e=document.getElementById('__apoLink'); if(e) e.click(); }"); } catch (Exception ignore) { }
                }
                page.evaluate("() => { const e=document.getElementById('__apoLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try {
            page.waitForFunction("(n) => { const s=document.querySelector(\"[ng-model='\"+n+\"']\"); return !!s && s.options.length>1; }", STORE_NG, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("AutoMultiplePO.nav: the store list is not ready"); }
        waitForAngular(800);
        return onScreen();
    }

    /** On this screen — the route AND the screen's own title in the top bar, so a bounce to the dashboard cannot pass. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().matches(".*#/autopo/?$")) return false;
        Object t = page.evaluate("() => { const h=document.querySelector('header,.navbar'); return h? h.textContent.replace(/\\s+/g,' ').trim().slice(0,90) : ''; }");
        return t != null && t.toString().toLowerCase().replaceAll("\\s+", " ").contains("auto/ multiple po");
    }

    // ---- store -----------------------------------------------------------------------------------------------

    public List<String> stores() { return Select2Pick.names(page, STORE_NG); }

    /** Choose the Store from its list; true when the box and the screen's model both hold it. */
    public boolean selectStore(String name) {
        problems.clear();
        String p = Select2Pick.pick(page, STORE_NG, name);
        if (!p.isEmpty()) problems.add("Store: " + p);
        else lastStore = name;
        return ok();
    }

    // ---- search + Auto PO Details ----------------------------------------------------------------------------

    /** Click <b>Search</b>, wait for the answer and count the rows listed in Auto PO Details (also {@link #lastRowCount}). */
    public int clickSearch() {
        problems.clear();
        lastToast = "";
        lastSearchStatus = 0;
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/getStockAutoPOSearchList/.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.scrollIntoView({block:'center'}); b.id='__apoSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Search button is not on screen"); return 0; }
        boolean answered = false;
        try {
            Response r = page.waitForResponse(x -> x.url().toLowerCase().contains("/api/") && x.url().toLowerCase().contains("autopo"),
                    new Page.WaitForResponseOptions().setTimeout(30000), () -> page.locator("#__apoSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            lastSearchStatus = r.status();
            answered = true;
            if (r.status() >= 400) problems.add("Search answered HTTP " + r.status());
        } catch (Exception e) {
            problems.add("Search got no answer from an AutoPO API call: " + String.valueOf(e.getMessage()).split("\n")[0]);
        } finally {
            page.evaluate("() => { const e=document.getElementById('__apoSearch'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(700);
        lastRowCount = itemRowCount();
        for (int i = 0; answered && i < 10 && lastRowCount == 0; i++) {          // the answer is in; let the table render it before calling it empty
            page.waitForTimeout(300);
            lastRowCount = itemRowCount();
        }
        lastToast = toastsNow();
        return lastRowCount;
    }

    /** Rows listed in Auto PO Details — one tick box per row. */
    public int itemRowCount() {
        Object n = page.evaluate("() => {" + JS + " return dataRows().length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** The column headers of Auto PO Details. */
    public List<String> headers() {
        Object r = page.evaluate("() => {" + JS + " const t=detailTable(); return t? [...t.querySelectorAll('th')].map(h=>norm(h.textContent)).filter(Boolean) : []; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** "Item Code | Item Name" of the first {@code n} listed rows. */
    public String sample(int n) {
        Object r = page.evaluate("(n) => {" + JS + " const ic=colIdx(/^item\\s*code/), inn=colIdx(/^item\\s*name/);"
                + " return dataRows().slice(0,n).map(r=>cellText(r,ic)+' | '+cellText(r,inn)).join(' ;; '); }", n);
        return r == null ? "" : r.toString();
    }

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...new Set([...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean))].join(' | '); }");
        return r == null ? "" : r.toString();
    }

    // ---- select a row, supplier ------------------------------------------------------------------------------

    /** Tick the {@code index}-th row (a genuine click, then the model read back) and return "Item Code | Item Name", or "" when it did not register. */
    public String tickRow(int index) {
        problems.clear();
        Object tagged = page.evaluate("(i) => {" + JS + " const r=dataRows()[i]; if(!r) return ''; const cb=r.querySelector('input[type=checkbox]'); cb.scrollIntoView({block:'center'}); cb.id='__apoTick';"
                + " return cellText(r,colIdx(/^item\\s*code/))+' | '+cellText(r,colIdx(/^item\\s*name/)); }", index);
        String item = tagged == null ? "" : tagged.toString();
        if (item.isEmpty()) { problems.add("row " + (index + 1) + " of Auto PO Details is not on screen"); return ""; }
        try { page.locator("#__apoTick").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { try { page.evaluate("() => { const c=document.getElementById('__apoTick'); if(c) c.click(); }"); } catch (Exception ignore) { } }
        waitForAngular(500);
        Object on = page.evaluate("() => { const c=document.getElementById('__apoTick'); const v=!!(c&&c.checked); if(c) c.removeAttribute('id'); return v; }");
        if (!Boolean.TRUE.equals(on)) { problems.add("the tick box of row " + (index + 1) + " did not stay ticked"); return ""; }
        lastItem = item;
        return item;
    }

    /** How many rows are ticked now (the select-all box in the heading is not a row). */
    public int tickedCount() {
        Object n = page.evaluate("() => {" + JS + " return dataRows().filter(r=>r.querySelector('input[type=checkbox]').checked).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /**
     * The Supplier of the {@code index}-th row. The screen fills it by itself when the row is ticked; it can take a moment, so this waits up to {@code seconds}.
     * Also records {@link #lastRowFacts} - the row's Rate Contract, PO Quantity and PO Number cells.
     */
    public String supplierOfRow(int index, int seconds) {
        String s = "";
        long end = System.currentTimeMillis() + seconds * 1000L;
        do {
            Object r = page.evaluate("(i) => {" + JS + " const r=dataRows()[i]; return r? cellText(r,colIdx(/^supplier/)) : ''; }", index);
            s = r == null ? "" : r.toString().trim();
            if (!s.isEmpty()) break;
            page.waitForTimeout(500);
        } while (System.currentTimeMillis() < end);
        Object f = page.evaluate("(i) => {" + JS + " const r=dataRows()[i]; if(!r) return '';"
                + " return 'Rate Contract='+cellText(r,colIdx(/rate\\s*contract/))+' | PO Quantity='+cellText(r,colIdx(/^po\\s*quantity/))+' | PO Number='+cellText(r,colIdx(/^po\\s*number/))+' | Planned Delivery='+cellText(r,colIdx(/planned\\s*delivery/)); }", index);
        lastRowFacts = f == null ? "" : f.toString();
        lastSupplier = s;
        return s;
    }

    /** The PO Number the ticked row shows now ("" while empty). */
    public String poNumberOfRow(int index) {
        Object r = page.evaluate("(i) => {" + JS + " const r=dataRows()[i]; return r? cellText(r,colIdx(/^po\\s*number/)) : ''; }", index);
        lastPoNumber = r == null ? "" : r.toString().trim();
        return lastPoNumber;
    }

    // ---- Generate PO -----------------------------------------------------------------------------------------

    /**
     * Click <b>Generate PO</b> (a genuine click - the screen's validation ignores script clicks), accept a confirm dialog if one appears, and wait for the answer: a toast, or the
     * save call's reply. Everything the click sends ({@code POST}/{@code PUT}, and any failing call) is kept in {@link #lastSaveHttp}. Returns the toast ("" when nothing was shown).
     */
    public String clickGeneratePO(int waitSeconds) {
        problems.clear();
        lastToast = "";
        final List<String> http = java.util.Collections.synchronizedList(new ArrayList<>());
        final java.util.function.Consumer<Response> onResp = resp -> {
            try {
                String m = resp.request().method(), u = resp.url().toLowerCase();
                if (!u.contains("/api/") || u.contains("getinternalmsgcount")) return;
                boolean write = "POST".equals(m) || "PUT".equals(m);
                if (!(write && !u.contains("commondropdown")) && resp.status() < 400) return;
                String body = ""; try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                java.util.regex.Matcher mm = java.util.regex.Pattern.compile("\"ResultStatus\":(-?\\d+).*?\"Message\":\"([^\"]*)\"").matcher(body);
                String shown = mm.find() ? "ResultStatus=" + mm.group(1) + ", Message=\"" + mm.group(2) + "\"" : body.replaceAll("\\s+", " ").substring(0, Math.min(220, body.replaceAll("\\s+", " ").length()));
                http.add(m + " " + resp.url().replaceFirst("^https?://[^/]+", "") + " " + resp.status() + " => " + shown);
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);
        try {
            page.evaluate("() => { window.__apoToasts=[]; if(window.__apoObs) window.__apoObs.disconnect();"
                    + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__apoToasts.includes(t)) window.__apoToasts.push(t); }); };"
                    + " window.__apoObs=new MutationObserver(grab); window.__apoObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
            Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/IUDAutoPO/.test(x.getAttribute('ng-click')||'') || /^\\s*generate\\s*po\\s*$/i.test(norm(x.textContent)));"
                    + " if(!b) return ''; b.scrollIntoView({block:'center'}); b.id='__apoGen'; return b.disabled? 'disabled' : 'ok'; }");
            String t = tagged == null ? "" : tagged.toString();
            if (t.isEmpty()) { problems.add("the Generate PO button is not on screen"); return ""; }
            if ("disabled".equals(t)) problems.add("the Generate PO button is disabled");
            try { page.locator("#__apoGen").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { problems.add("Generate PO could not be clicked: " + String.valueOf(e.getMessage()).split("\n")[0]); return ""; }
            finally { page.evaluate("() => { const e=document.getElementById('__apoGen'); if(e) e.removeAttribute('id'); }"); }
            waitForAngular(400);
            try { acceptSaveDialog(); } catch (Exception ignore) { }
            long end = System.currentTimeMillis() + waitSeconds * 1000L;
            String seen = "";
            while (System.currentTimeMillis() < end) {
                Object all = page.evaluate("() => (window.__apoToasts||[]).join(' | ')");
                String now = all == null ? "" : all.toString();
                if (!now.isEmpty()) {
                    seen = now;
                    String low = now.toLowerCase();
                    if (low.contains("success") || low.contains("generated") || low.contains("fail") || low.contains("error") || low.contains("please") || low.contains("select") || low.contains("exist")) break;
                }
                page.waitForTimeout(300);
            }
            lastToast = seen.replaceAll("[×x]\\s*KPJ\\s*Portal\\s*", "").replaceAll("KPJ\\s*Portal\\s*", "").trim();
            if (lastToast.isEmpty()) lastToast = toastsNow();
        } finally {
            try { page.offResponse(onResp); } catch (Exception ignore) { }
            lastSaveHttp = http.isEmpty() ? "(no request was sent)" : String.join(" ;; ", http);
        }
        return lastToast;
    }

    public static boolean isSuccess(String toast) {
        if (toast == null) return false;
        String t = toast.toLowerCase();
        return (t.contains("success") || t.contains("generated") || t.contains("saved") || t.contains("created")) && !t.contains("not ") && !t.contains("fail");
    }
}
