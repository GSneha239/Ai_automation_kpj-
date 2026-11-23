package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Encounter Type</b> — Page Object.
 *
 * <p>Route {@code #/EncounterType} (reached from the sidebar: Application Configuration → Patient → Encounter Type; a typed hash bounces to the
 * dashboard). One inline screen, no Add button: <b>Code*</b> ({@code encountertypelist.Code}), <b>Encounter Type*</b>
 * ({@code encountertypelist.Description}, labelled "Remark" in the table below), <b>Store*</b> ({@code EncounterType.StoreId}, a select) and
 * <b>Submit</b> ({@code fnIUDEncounterType()}). Below sit the "Encounter Type Location Details" line (Applicable For / Store / Prefix) and the
 * records table (Code | Remark | Status | Edit), whose rows are read from the grid's own data.</p>
 */
public class EncounterType extends BasePage {

    public EncounterType(Page page) { super(page); }

    public static final String ROUTE = "#/EncounterType";
    private static final String CODE = "encountertypelist.Code";
    private static final String DESC = "encountertypelist.Description";
    private static final String STORE = "EncounterType.StoreId";

    public String lastCode = "", lastType = "", lastStore = "", lastToast = "", lastSaveHttp = "", lastMenu = "", lastForm = "";
    public byte[] toastPng;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const gridOpts=()=>{ const el=document.querySelector('.ui-grid'); if(!el) return null; let s=angular.element(el).scope();"
          + "  for(let i=0;i<10&&s;i++){ const o=s.gridOptions; if(o && Array.isArray(o.data) && o.columnDefs) return o; s=s.$parent; } return null; };";

    // ---- navigation ------------------------------------------------------

    /** Application Configuration &rarr; Patient &rarr; Encounter Type, each level looked up INSIDE its parent's li. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*application\\s*configuration\\s*$");
        expandMenuEntry("^\\s*application\\s*configuration\\s*$", "^\\s*patient\\s*$");
        waitForAngular(800);
        Object menu = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis).map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||'')).filter(t=>t.length>4&&t.length<70))].slice(0,140).join('\\n'); }");
        lastMenu = menu == null ? "" : menu.toString();
        Object href = page.evaluate("() => {" + JS
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*application\\s*configuration\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const pat=[...topLi.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && vis(x));"
                + " const scope=(pat&&pat.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*encounter\\s*type\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__etMenu'; return a.getAttribute('href')||'link'; }");
        String h = href == null ? "" : href.toString();
        if (!h.isEmpty()) {
            try { page.locator("#__etMenu").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("EncounterType.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__etMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        return onScreen();
    }

    /** On the screen: the route AND its own Code box + Submit button. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().endsWith("#/encountertype")) return false;
        try {
            page.waitForFunction("() => { const e=document.querySelector(\"input[ng-model='" + CODE + "']\"); return !!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
            return true;
        } catch (Exception e) { return false; }
    }

    public String currentScreen() { return page.url(); }

    // ---- form ------------------------------------------------------------

    /** Enter the unique Code and Encounter Type (through each box's model), then read both back. */
    public String fillCodeAndType(int attempt) {
        long ms = System.currentTimeMillis() + attempt * 7919L;
        lastCode = "AT" + String.format("%08d", ms % 100000000L);
        lastType = "Auto Encounter " + String.format("%06d", ms % 1000000L);
        Object r = page.evaluate("(a) => {" + JS + " const A=window.angular;"
                + " const put=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"']\")].find(vis); if(!e) return '(no field)';"
                + "   const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " return 'Code='+put('" + CODE + "',a.code)+' | Encounter Type='+put('" + DESC + "',a.type); }",
                java.util.Map.of("code", lastCode, "type", lastType));
        waitForAngular(400);
        lastForm = r == null ? "" : r.toString();
        return lastForm;
    }

    /** What the form's model holds now: code, type, store id. */
    public java.util.Map<String, Object> model() {
        Object r = page.evaluate("() => {" + JS + " const e=[...document.querySelectorAll(\"input[ng-model='" + CODE + "']\")].find(vis);"
                + " if(!e) return {};"
                + " const s=angular.element(e).scope(); const l=s.encountertypelist||{}; const et=s.EncounterType||{};"
                + " return {code:l.Code, desc:l.Description, storeid:et.StoreId}; }");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = r instanceof java.util.Map ? (java.util.Map<String, Object>) r : new java.util.HashMap<>();
        return m;
    }

    /** The store names the Store select offers (placeholder excluded). */
    public java.util.List<String> storeOptions() {
        Object r = page.evaluate("() => {" + JS + " const s=[...document.querySelectorAll(\"select[ng-model='" + STORE + "']\")].find(vis); if(!s) return [];"
                + " return [...s.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*\\s*select\\s*-*$/i.test(t)); }");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Select the named store (or the first real one when blank) and verify the model took it. */
    public boolean selectStore(String name) {
        java.util.List<String> opts = storeOptions();
        if (opts.isEmpty()) { lastStore = "(no options)"; return false; }
        String want = name == null || name.isEmpty() ? opts.get(0) : name;
        Object r = page.evaluate("(w) => {" + JS + " const A=window.angular; const s=[...document.querySelectorAll(\"select[ng-model='" + STORE + "']\")].find(vis); if(!s) return '';"
                + " const i=[...s.options].findIndex(o=>norm(o.textContent).toLowerCase()===String(w).toLowerCase()); if(i<0) return '';"
                + " s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{A.element(s).triggerHandler('change');}catch(x){} const j=window.jQuery; if(j){try{j(s).trigger('change');}catch(x){}}"
                + " return norm(s.options[i].textContent); }", want);
        waitForAngular(500);
        lastStore = r == null ? "" : r.toString();
        Object sid = model().get("storeid");
        return !lastStore.isEmpty() && sid != null && !String.valueOf(sid).isEmpty() && !"0".equals(String.valueOf(sid));
    }

    /** Click Submit (the form's own) and return the toast text; the save call's HTTP answer goes to {@link #lastSaveHttp}. */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__etToasts=[]; if(window.__etObs) window.__etObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__etToasts.includes(t)) window.__etToasts.push(t); }); };"
                + " window.__etObs=new MutationObserver(grab); window.__etObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("encountertype") || resp.status() >= 400)) return;
                String body; try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll(\"button[ng-click='fnIUDEncounterType()']\")].find(vis); if(!b) return false; b.id='__etSubmit'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__etSubmit").click(new Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("EncounterType submit: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__etSubmit'); if(e) e.removeAttribute('id'); }");
        }
        try {
            page.waitForFunction("() => (window.__etToasts||[]).some(a=>/saved|success|added|updated|please|enter|select|required|exist|already|error|fail/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        waitForAngular(1200);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); } catch (Exception ignore) { }
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        Object t = page.evaluate("() => { const a=window.__etToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        lastToast = t == null ? "" : t.toString().trim();
        return lastToast;
    }

    // ---- table -----------------------------------------------------------

    /** Every row of the records table, from the grid's own data: {code, remark, status}. */
    public java.util.List<java.util.Map<String, Object>> tableRows() {
        Object r = page.evaluate("() => {" + JS + " const o=gridOpts(); if(!o) return [];"
                + " return o.data.map(x=>({code:norm(x.Code||x.code), remark:norm(x.Description||x.description||x.remark||x.Remark), status:String(x.Status!==undefined?x.Status:(x.status!==undefined?x.status:'')), keys:Object.keys(x).slice(0,12).join(',')})); }");
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) {
            @SuppressWarnings("unchecked") java.util.Map<String, Object> m = (java.util.Map<String, Object>) o;
            out.add(m);
        }
        return out;
    }

    /** The table's headings and the footer count text, for the report. */
    public String tableHeadings() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('.ui-grid-header-cell')].filter(vis).map(h=>norm(h.textContent).replace(/\\s+\\d+$/,'')).filter(t=>t).join(' | '); }");
        return r == null ? "" : r.toString();
    }
}
