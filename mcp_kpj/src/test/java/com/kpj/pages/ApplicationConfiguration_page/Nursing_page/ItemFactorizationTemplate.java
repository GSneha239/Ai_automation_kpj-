package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Item Factorization Template</b> ({@code #/ItemFactorizationTemplateList}).
 *
 * <p>Flow: list screen → <b>New</b> ({@code AddNewItemFactorization()}, lands on {@code #/add-ItemFactorizationTemplate})
 * → enter <b>Code</b> ({@code ItemFactorization.code}) and <b>Template Name</b> ({@code ItemFactorization.description}
 * — the ng-model name doesn't match the UI label) → in the <i>Item Details</i> entry row, enter <b>Item Name</b>
 * ({@code ItemFactorization.ItemName}), <b>Batch</b>, <b>Expiry Date</b>, <b>Qty</b>, <b>Selling Price</b>,
 * <b>Purchase Price</b> → <b>Add</b> (appends a row to the Item Details grid) → <b>Remark</b>
 * ({@code ItemFactorization.Remark}) → <b>Submit</b> → success toast.</p>
 *
 * <p><b>This app markup reuses the same {@code id} for multiple different fields on this screen</b> (checked live:
 * {@code #txtMRP} is BOTH Code and Selling Price, {@code #txtPurchaseRate} is BOTH Template Name and Purchase
 * Price, {@code #txtBatch} is BOTH Batch and Qty) — every field here is located by its {@code ng-model}, never by
 * {@code id}.</p>
 *
 * <p><b>Reproduced live 2026-08-25: Item Name's own auto-complete request is broken — a real app defect, not a
 * script issue.</b> The field shares the same broken custom {@code auto-complete="AutoCompleteOptionsForItemName"}
 * directive as OpdCharges' Service Name, but here calling its {@code .data(query)} directly (the general
 * workaround documented on that class) still fails: {@code POST /api/GRN/fetchItemByStore} answers HTTP 400,
 * <i>twice over</i> — fixing the first rejection reveals a second:</p>
 * <pre>1) "...null entry for parameter 'PsychotropicDrug' of non-nullable type 'System.Boolean'..."
 * 2) (after supplying PsychotropicDrug) "...null entry for parameter 'requesttype' of non-nullable type 'System.Int32'..."</pre>
 * <p>Both for the same reason: the app's own JS omits these two query params (sending the literal string
 * {@code "undefined"}), which .NET Web API's model binder rejects for non-nullable value-type parameters with no
 * default — confirmed query-independent (fails identically for "a" and "para"). {@link #pickItemName} works
 * around this by capturing the EXACT request the directive's own {@code .data()} call attempts (via a temporary
 * {@code XMLHttpRequest} hook — AngularJS's {@code $http} always goes through XHR, never {@code fetch}), patching
 * only {@code PsychotropicDrug} and {@code requesttype} in that captured URL, and replaying it — this reuses
 * whatever {@code StoreID} etc. the app itself resolved, rather than hardcoding one. The replayed request answers
 * 200 with real items, and feeding the first one to the directive's own {@code itemSelected()} callback is exactly
 * what a real click on a suggestion would do.</p>
 */
public class ItemFactorizationTemplate extends BasePage {

    public ItemFactorizationTemplate(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/ItemFactorizationTemplateList";
    public String lastCode = "", lastTemplateName = "", lastItemName = "", lastBatch = "",
            lastExpiryDate = "", lastQty = "", lastSellingPrice = "", lastPurchasePrice = "", lastRemark = "";
    public String itemNameError = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses) — see
     *  {@code IntakeOutputMaster#toastPng} for why this matters more than a post-hoc {@code step(page,...)} shot. */
    public byte[] toastPng;

    private void captureToastShot() {
        waitForAngular(2000);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onListScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__ifMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/factoriz/i.test(x.textContent||'') || (x.getAttribute('href')||'')==='#/ItemFactorizationTemplateList'); if(!a) return ''; a.id='__ifMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("ItemFactorizationTemplate.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__ifMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__ifMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__ifMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddNewItemFactorization\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("ItemFactorizationTemplate.nav: list not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1000);
        }
        return onListScreen();
    }

    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,45); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    public boolean onListScreen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddNewItemFactorization\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"));
    }

    public boolean onAddForm() {
        return Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"[ng-model='ItemFactorization.code']\")"));
    }

    /** Real-click <b>New</b> ({@code AddNewItemFactorization}) and wait for the entry form. */
    public boolean clickNew() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddNewItemFactorization\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__ifNew'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("ItemFactorizationTemplate.clickNew: New button not found"); return false; }
        try { page.locator("#__ifNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("ItemFactorizationTemplate.clickNew: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ifNew'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => !!document.querySelector(\"[ng-model='ItemFactorization.code']\")",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("ItemFactorizationTemplate.clickNew: entry form did not render"); }
        waitForAngular(1000);
        return onAddForm();
    }

    // ---- form --------------------------------------------------------------

    private String fillByNgModel(String ngModel, String value) {
        Object r = page.evaluate("(a) => { const A=window.angular; const e=document.querySelector(\"[ng-model='\"+a.ng+\"']\"); if(!e) return '(no field)';"
                + " const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return 'ok'; }",
                java.util.Map.of("ng", ngModel, "v", value));
        return r == null ? "(no field)" : r.toString();
    }

    /** Enter Code and Template Name (header fields). */
    public String fillTemplateHeader() {
        String code = "IF" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastTemplateName = "Template " + code;
        String cd = fillByNgModel("ItemFactorization.code", code);
        String tn = fillByNgModel("ItemFactorization.description", lastTemplateName);
        waitForAngular(300);
        return "Code=" + cd + " (" + code + ") | TemplateName=" + tn + " (" + lastTemplateName + ")";
    }

    /**
     * Search Item Name and pick the first match. The directive's own {@code .data(query)} call fails server-side
     * (see class Javadoc), so this instead: (1) hooks {@code XMLHttpRequest} briefly to capture the exact URL and
     * body the directive's own {@code .data(query)} attempts — reusing whatever {@code StoreID} etc. the app
     * itself resolved; (2) patches only the two broken query params ({@code PsychotropicDrug=undefined} →
     * {@code false}, {@code requesttype=undefined} → {@code 0}); (3) replays that corrected request directly; and
     * (4) feeds the first real item back through the directive's own {@code itemSelected()} callback — exactly
     * what a real click on a suggestion would do. Records the real error in {@link #itemNameError} if even the
     * corrected request fails (a genuinely different problem from the known one).
     */
    public String pickItemName(String query) {
        Object r = page.evaluate("(q) => { const n=document.querySelector(\"[ng-model='ItemFactorization.ItemName']\"); if(!n) return {err:'field not found'};"
                + " const A=window.angular; const s=A.element(n).scope(); const o=s.AutoCompleteOptionsForItemName; if(!o) return {err:'no autocomplete object'};"
                + " const OrigXHR=window.XMLHttpRequest; let capturedUrl=null, capturedBody=null;"
                + " function Wrapped(){ const x=new OrigXHR(); const oo=x.open, os=x.send;"
                + "   x.open=function(m,u){ capturedUrl=u; return oo.apply(x,arguments); };"
                + "   x.send=function(b){ capturedBody=b; return os.apply(x,arguments); }; return x; }"
                + " window.XMLHttpRequest=Wrapped;"
                + " return o.data(q).catch(()=>null).then(async () => {"
                + "   window.XMLHttpRequest=OrigXHR;"
                + "   if(!capturedUrl) return {err:'could not capture the app\\'s own request'};"
                + "   const fixedUrl=capturedUrl.replace(/PsychotropicDrug=undefined/i,'PsychotropicDrug=false').replace(/requesttype=undefined/i,'requesttype=0');"
                + "   let resp; try { resp=await fetch(fixedUrl, {method:'POST', credentials:'include', headers:{'Content-Type':'application/json;charset=utf-8'}, body:capturedBody}); }"
                + "   catch(e){ return {err:'replayed request threw: '+e.message}; }"
                + "   if(!resp.ok){ const t=await resp.text().catch(()=>''); return {err:'replayed request still failed: HTTP '+resp.status+' '+t.slice(0,200)}; }"
                + "   const items=await resp.json(); const item=(items||[])[0]; if(!item) return {err:'', empty:true};"
                + "   o.itemSelected({item}); try{s.$apply&&s.$apply();}catch(e){}"
                + "   return {name:item.ItemName||'', code:item.ItemCode||''};"
                + " }); }", query);
        @SuppressWarnings("unchecked") java.util.Map<String, Object> m = (java.util.Map<String, Object>) r;
        if (m == null) return "";
        Object err = m.get("err");
        if (err != null && !err.toString().isEmpty()) { itemNameError = err.toString(); return ""; }
        if (Boolean.TRUE.equals(m.get("empty"))) { itemNameError = "no items matched \"" + query + "\""; return ""; }
        String name = String.valueOf(m.getOrDefault("name", ""));
        lastItemName = name;
        return name;
    }

    /** Enter Batch, Expiry Date, Qty, Selling Price, Purchase Price — plain inputs, not autocomplete. */
    public String fillItemDetails() {
        lastBatch = "B" + String.format("%04d", Math.abs(System.nanoTime() % 10000));
        lastExpiryDate = "31/12/2027";
        lastQty = "10";
        lastSellingPrice = "25.50";
        lastPurchasePrice = "15.00";
        String b = fillByNgModel("ItemFactorization.Batch", lastBatch);
        String ex = fillByNgModel("ItemFactorization.ExpiryDate", lastExpiryDate);
        String q = fillByNgModel("ItemFactorization.Qty", lastQty);
        String sp = fillByNgModel("ItemFactorization.SellingPrice", lastSellingPrice);
        String pp = fillByNgModel("ItemFactorization.PurchasePrice", lastPurchasePrice);
        waitForAngular(300);
        return "Batch=" + b + " (" + lastBatch + ") | Expiry=" + ex + " (" + lastExpiryDate + ") | Qty=" + q
                + " (" + lastQty + ") | SellingPrice=" + sp + " (" + lastSellingPrice + ") | PurchasePrice=" + pp + " (" + lastPurchasePrice + ")";
    }

    /** Click <b>Add</b> and report whether a row actually landed in the Item Details grid — checked live: with no
     *  Item Name set (see {@link #pickItemName}) this silently no-ops (no row, no toast, no error). */
    public String clickAddItemRow() {
        int before = rowCount();
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^add$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__ifAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) return "Add button not found";
        try { page.locator("#__ifAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { return "Add click failed - " + e.getMessage(); }
        page.evaluate("() => { const e=document.getElementById('__ifAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        int after = rowCount();
        return after > before ? "row added (" + before + " -> " + after + ")" : "NO row added (still " + after + ") — Add silently no-op'd";
    }

    /** Counts rendered rows in the visible "Item Details" grid (matched by its own header, "Item Batch" — unique
     *  to this table, unlike "Batch" which also appears in the entry row's own label). Checked live: guessing the
     *  Angular scope's array property name (e.g. {@code ItemFactorizationDetails}) got it wrong and reported 0
     *  rows even when Add had genuinely worked (proven by a subsequent real "Saved Successfully" toast) — the
     *  rendered DOM is the reliable source here, not a guessed scope property. */
    private int rowCount() {
        Object n = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(tb=>/item\\s*batch/i.test(norm((tb.querySelector('thead,tr')||{}).textContent||'')));"
                + " if(!t) return -1; const body=t.querySelector('tbody')||t; return [...body.querySelectorAll('tr')].filter(r=>norm(r.textContent)).length; }");
        return n instanceof Number ? ((Number) n).intValue() : -1;
    }

    /** Enter Remark. */
    public String fillRemark() {
        lastRemark = "Automated test remark " + System.currentTimeMillis();
        String r = fillByNgModel("ItemFactorization.Remark", lastRemark);
        waitForAngular(300);
        return "Remark=" + r + " (" + lastRemark + ")";
    }

    /** Click <b>Submit</b> and return the toast — checked live: with no item row in the grid (see
     *  {@link #clickAddItemRow}) this silently no-ops too (no API call fires at all, no toast, no error). */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__ifToasts=[]; if(window.__ifObs) window.__ifObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ifToasts.includes(t)) window.__ifToasts.push(t); }); };"
                + " window.__ifObs=new MutationObserver(grab); window.__ifObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^submit$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__ifSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) return "";
        try { page.locator("#__ifSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__ifSubmit'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__ifToasts||[]).length > 0", null, new Page.WaitForFunctionOptions().setTimeout(6000));
        } catch (Exception ignore) { page.waitForTimeout(1500); }
        captureToastShot();
        Object t = page.evaluate("() => { const a=window.__ifToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }
}
