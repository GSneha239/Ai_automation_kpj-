package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Auto Charges</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → (Admission) → <b>Auto Charges</b> → <b>Add</b> → select a <b>Service</b>
 * (if that service already exists, pick a different one) → <b>Submit</b> → toast.</p>
 *
 * <p>New screen — route, the Service control and the Submit handler are discovered at runtime and then wired.</p>
 */
public class AutoCharges extends BasePage {

    public AutoCharges(Page page) { super(page); }

    /** Route — captured at runtime by mining the menu href (config menu links are often hidden). */
    public static String ROUTE = "";

    // ---- discovery -------------------------------------------------------

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " out.push('tables='+document.querySelectorAll('table').length+' ui-grids='+document.querySelectorAll('[ui-grid],.ui-grid').length);"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+(b.getAttribute('ng-click')?(' {'+b.getAttribute('ng-click')+'}'):'')).filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,40).join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public String dumpAddForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let t=''; const g=e.closest('.form-group,.row,.form-line,.col-sm-6,.col-md-6,.col-sm-4,td,div'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t=l.textContent; } return norm(t).slice(0,30); };"
                + " const out=[];"
                + " const inputs=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden');"
                + " inputs.slice(0,40).forEach(e=>{ out.push(e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'+(e.tagName==='SELECT'?(' opts='+e.options.length):'')); });"
                + " out.push('ui-selects='+JSON.stringify([...document.querySelectorAll('ui-select,[ui-select]')].map(u=>norm(u.getAttribute('ng-model')||u.className).slice(0,40)).slice(0,6)));"
                + " const subs=[...document.querySelectorAll('button,input[type=submit]')].filter(b=>b.offsetParent!==null && /submit|save/i.test(b.textContent||b.value||'')).map(b=>norm(b.textContent||b.value)+' {'+(b.getAttribute('ng-click')||'')+'}');"
                + " out.push('submit/save buttons=\\n  '+subs.join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        Object appCfg = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__appCfgMenu'; return true; }");
        if (Boolean.TRUE.equals(appCfg)) {
            try { page.locator("#__appCfgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("AutoCharges.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/auto\\s*charge/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__acMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__acMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("AutoCharges.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        }
        if (ROUTE.isEmpty()) {
            Object mined = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/auto\\s*charge/i.test(norm(x.textContent)) || /autocharge/i.test(x.getAttribute('href')||'')); return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("AutoCharges.nav: mined href => " + mined);
            if (mined != null && mined.toString().contains("#/")) ROUTE = mined.toString().substring(mined.toString().indexOf("#/"));
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("AutoCharges.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /auto\\s*charge/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls — it may render after the grid). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button],span,i')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('[ng-click]')].find(x=>/add/i.test(x.getAttribute('ng-click')||'') && /add/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__acAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("AutoCharges.clickAdd: Add button not found"); return false; }
        try { page.locator("#__acAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("AutoCharges.clickAdd: click failed - " + e.getMessage()); }
        waitForAngular(1200);
        return true;
    }

    /** Select the <b>Location</b> ({@code AutoChargesMaster.locationid}) — its first real option. This is the cascade
     *  parent that populates the Service dropdown. Returns the chosen text. */
    public String selectLocationFirst() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const e=document.querySelector(\"select[ng-model='AutoChargesMaster.locationid']\"); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return '';"
                + " if(e.selectedIndex!==i){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} }"
                + " return norm(e.options[i].textContent); }");
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /** The Service dropdown's real option texts (polls — they load after the Location is selected). */
    public java.util.List<String> serviceOptions() {
        Object r = null;
        for (int i = 0; i < 15; i++) {
            r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const e=" + SERVICE_SELECT_JS + "; if(!e) return [];"
                    + " return [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))).map(o=>norm(o.textContent)); }");
            if (r instanceof java.util.List && !((java.util.List<?>) r).isEmpty()) break;
            page.waitForTimeout(400);
        }
        java.util.List<String> list = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) if (o != null) list.add(o.toString());
        return list;
    }

    /** The Service &lt;select&gt; = {@code AutoChargesMaster.serviceitemid} (falls back to a label containing "Service"). */
    private static final String SERVICE_SELECT_JS =
            "(document.querySelector(\"select[ng-model='AutoChargesMaster.serviceitemid']\") || (() => { const byLabel=[...document.querySelectorAll('select')].find(sel=>{ const g=sel.closest('.form-group,.row,.col-sm-6,.col-md-6,td,div'); const l=g?g.querySelector('label,.control-label'):null; return l && /service/i.test(l.textContent||''); }); return byLabel||null; })())";

    /** Select the service at index {@code idx} (of the real options). Returns the chosen text, or "". */
    public String selectServiceByIndex(int idx) {
        Object r = page.evaluate("(idx) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const e=" + SERVICE_SELECT_JS + "; if(!e) return '';"
                + " const opts=[...e.options].map((o,i)=>({o,i,t:norm(o.textContent),v:o.value})).filter(x=>x.v && !/^-*\\s*select/i.test(x.t));"
                + " if(!opts.length || idx>=opts.length) return '';"
                + " const pick=opts[idx]; e.selectedIndex=pick.i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + " return pick.t; }", idx);
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> and return the toast (success, "already exists", or a server error). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__acToasts=[]; if(window.__acObs) window.__acObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__acToasts.includes(t)) window.__acToasts.push(t); }); };"
                + " window.__acObs=new MutationObserver(grab); window.__acObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDAutoChargesMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__acSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__acSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__acToasts||[]).some(a=>/charge|service|saved|success|added|updated|please|select|enter|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__acToasts||[]).includes(t)) (window.__acToasts=window.__acToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__acToasts||[]; return a.find(x=>/already|exist/i.test(x)) || a.find(x=>/saved|added|success|updated/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
