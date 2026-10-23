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

    private int gridBefore = -1, gridAfter = -1;

    private int servicesGridRows() {
        Object n = page.evaluate("() => { const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                + " return [...document.querySelectorAll('.ui-grid-row,table tbody tr')].filter(vis)"
                + "   .filter(r=>(r.textContent||'').replace(/\\s+/g,' ').trim()).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** Click the <b>Add</b> button beside the Services dropdown (puts the chosen service into the Services grid). */
    public String clickInnerAdd() {
        waitForAngular(1200);   // let the Services choice reach the model before Add reads it
        String first = clickInnerAddOnce();
        if (serviceAdded()) return first;
        waitForAngular(1500);
        return first + "  ||  retried: " + clickInnerAddOnce();
    }

    private String clickInnerAddOnce() {
        gridBefore = servicesGridRows();
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetWidth||x.offsetHeight||x.getClientRects().length)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__acInnerAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { gridAfter = gridBefore; return "no Add button found on the form"; }
        final java.util.List<com.microsoft.playwright.Response> got = new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.function.Consumer<com.microsoft.playwright.Response> onResp = r -> {
            try { if (r.url().toLowerCase().contains("/api/")) got.add(r); } catch (Exception ignore) { }
        };
        page.onResponse(onResp);
        try { page.locator("#__acInnerAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("AutoCharges.clickInnerAdd: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__acInnerAdd'); if(e) e.removeAttribute('id'); }");
        // The grid may fill after a server round trip: give it up to 6 s.
        long end = System.currentTimeMillis() + 6000;
        gridAfter = servicesGridRows();
        while (gridAfter <= gridBefore && System.currentTimeMillis() < end) { page.waitForTimeout(400); gridAfter = servicesGridRows(); }
        page.offResponse(onResp);
        StringBuilder calls = new StringBuilder();
        for (com.microsoft.playwright.Response r : got) {
            try {
                String body = r.text(); body = body == null ? "" : body.replaceAll("\\s+", " ");
                calls.append(calls.length() > 0 ? " ;; " : "").append(r.request().method()).append(' ').append(r.status()).append(' ')
                     .append(r.url().replaceFirst("^https?://[^/]+", "")).append(" => ").append(body.substring(0, Math.min(200, body.length())));
            } catch (Exception ignore) { }
        }
        return "clicked Add; Services grid rows " + gridBefore + " -> " + gridAfter
                + (calls.length() == 0 ? "  (the click made no server call)" : "  ||  server calls: " + calls);
    }

    public boolean serviceAdded() { return gridAfter > gridBefore; }

    public boolean recordInList = false;

    /**
     * After the save the app is back on the list: choose the Location, click Search, then look for the saved service in the
     * grid ({@code div[id$='-grid-container']} — the id's numeric prefix changes on every load). The Services column filter is
     * used to narrow the grid to the service code first, so a paged grid cannot hide the row.
     */
    public String verifyRecordInList(String serviceText) {
        recordInList = false;
        String code = serviceText == null ? "" : serviceText.split(" - ")[0].trim();
        String svcName = serviceText == null || !serviceText.contains(" - ") ? "" : serviceText.substring(serviceText.indexOf(" - ") + 3).trim();
        final String needle = code + "|" + svcName;
        String loc = selectLocationFirst();
        if (loc.isEmpty()) {
            Object f = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const e=[...document.querySelectorAll('select')].find(x=>/location/i.test(x.getAttribute('ng-model')||'')&&(x.offsetWidth||x.offsetHeight));"
                    + " if(!e) return ''; const i=[...e.options].findIndex(o=>o.value&&!/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return '';"
                    + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{window.angular.element(e).triggerHandler('change');}catch(x){}"
                    + " return norm(e.options[i].textContent); }");
            loc = f == null ? "" : f.toString();
        }
        waitForAngular(800);
        try {
            page.locator("button:visible:text-is('Search'), a:visible:text-is('Search')").first()
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { System.out.println("AutoCharges.verifyRecordInList: Search - " + e.getMessage().split("\n")[0]); }
        waitForAngular(3000);
        // Wait for the data to LOAD: the grid first shows empty rows, so wait until a row actually carries text (up to 20 s).
        try {
            page.waitForFunction("() => { const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                    + " const g=[...document.querySelectorAll(\"[id$='-grid-container']\")].find(vis); if(!g) return false;"
                    + " return [...g.querySelectorAll('.ui-grid-row')].filter(vis).some(r=>(r.textContent||'').replace(/\\s+/g,' ').trim().length>0); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("AutoCharges.verifyRecordInList: the grid showed no data within 20 s"); }
        waitForAngular(1000);
        // Read the grid as it stands after Search (before any column filter).
        Object pre = page.evaluate("(c) => { const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                + " const g=[...document.querySelectorAll(\"[id$='-grid-container']\")].find(vis); if(!g) return 'nogrid';"
                + " const n=[...g.querySelectorAll('.ui-grid-row')].filter(vis).length;"
                + " return n+'|'+((g.textContent||'').toLowerCase().includes(c.split('|')[0].toLowerCase())||(c.split('|')[1]&&(g.textContent||'').replace(/\s+/g,' ').toLowerCase().includes(c.split('|')[1].toLowerCase()))?'found':'absent'); }", needle);
        String preState = pre == null ? "" : pre.toString();
        // Narrow the grid to the service code through the Services column filter.
        try {
            com.microsoft.playwright.Locator filter = page.locator("input[ng-model='colFilter.term']:visible").first();
            if (!code.isEmpty() && filter.count() > 0) { filter.fill(code); waitForAngular(1500); }
        } catch (Exception ignore) { }
        Object r = page.evaluate("(c) => { const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                + " const g=[...document.querySelectorAll(\"[id$='-grid-container']\")].find(vis);"
                + " if(!g) return JSON.stringify({grid:false,rows:0,found:false,text:''});"
                + " const rows=[...g.querySelectorAll('.ui-grid-row')].filter(vis);"
                + " const text=(g.textContent||'').replace(/\\s+/g,' ').trim();"
                + " return JSON.stringify({grid:true,id:g.id,rows:rows.length,found:text.toLowerCase().includes(c.split('|')[0].toLowerCase())||(!!c.split('|')[1]&&text.toLowerCase().includes(c.split('|')[1].toLowerCase())),text:text.slice(0,200)}); }", needle);
        String s = r == null ? "" : r.toString();
        recordInList = (s.contains("\"found\":true") || preState.endsWith("|found")) && !code.isEmpty();
        return "Location = " + (loc.isEmpty() ? "(none)" : loc) + "; looked for \"" + code + "\" in the list grid -> after Search (rows|result): "
                + preState + "; after filtering by the code: " + s;
    }

    /** Click <b>Submit</b> and return the toast (success, "already exists", or a server error). */
    public String lastSaveHttp = "";

    public String submitAndGetToast() {
        // Remember the save call's answer (the toast alone says just "Error!"); bodies are read AFTER the click settles.
        final java.util.List<com.microsoft.playwright.Response> got = new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.function.Consumer<com.microsoft.playwright.Response> onResp = r -> {
            try { if (r.url().toLowerCase().contains("/api/") && "POST".equalsIgnoreCase(r.request().method())) got.add(r); }
            catch (Exception ignore) { }
        };
        page.onResponse(onResp);
        try {
            return submitInner();
        } finally {
            page.offResponse(onResp);
            StringBuilder sb = new StringBuilder();
            for (com.microsoft.playwright.Response r : got) {
                try {
                    String body = r.text(); body = body == null ? "" : body.replaceAll("\\s+", " ");
                    sb.append(sb.length() > 0 ? " ;; " : "").append(r.status()).append(' ').append(r.url().replaceFirst("^https?://[^/]+", ""))
                      .append(" => ").append(body.substring(0, Math.min(400, body.length())));
                } catch (Exception ignore) { }
            }
            lastSaveHttp = sb.toString();
        }
    }

    private String submitInner() {
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
