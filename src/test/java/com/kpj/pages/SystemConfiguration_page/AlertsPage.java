package com.kpj.pages.SystemConfiguration_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * System Configuration &gt; <b>Alerts</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>System Configuration</b> → <b>Alerts</b> ({@code #/AlertsConfiguration}) → select
 * <b>Location Name*</b> ({@code alertconfig.locationid}) → in <b>Alerts and notifications</b> select <b>Event</b>
 * ({@code alertconfig.eventid}) → tick a channel checkbox ({@code alertconfig.sms} / {@code .whatsapp} /
 * {@code .email}) → <b>Add</b> ({@code AddAlertConfig(AlertDetailsList)}) → <b>Save</b>
 * ({@code fnSaveAlertsConfig()}) → success toast.</p>
 *
 * <p><b>Two sets of checkboxes.</b> The entry row uses {@code alertconfig.*}; once a Location is chosen the
 * screen also renders a GRID of already-configured rows using {@code AD.sms} / {@code AD.email} /
 * {@code AD.whatsapp} (45+ boxes). Only the {@code alertconfig.*} ones belong to the row being added — ticking a
 * grid box would edit an existing configuration instead.</p>
 */
public class AlertsPage extends BasePage {

    public AlertsPage(Page page) { super(page); }

    public static final String ROUTE = "#/AlertsConfiguration";
    public String lastLocation = "", lastEvent = "", lastChannel = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/system\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*system\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1500);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__alMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*alerts?\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/AlertsConfiguration'); if(!a) return ''; a.id='__alMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("AlertsPage.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__alMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__alMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__alMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('select')].some(s=>s.getAttribute('ng-model')==='alertconfig.locationid' && s.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(25000));
            } catch (Exception ignore) { System.out.println("AlertsPage.nav: Alerts screen not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1200);
        }
        return onScreen();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** On the REAL Alerts screen — verified by its own Location select, not just the URL. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("alertsconfiguration")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('select')].some(s=>s.getAttribute('ng-model')==='alertconfig.locationid' && s.offsetParent!==null)"));
    }

    // ---- form ------------------------------------------------------------

    /**
     * Select <b>Location Name*</b> then <b>Event</b>. Location is chosen FIRST — it loads the existing-alerts grid
     * and the Event list. Both are picked from their own option lists and the bound value read back.
     */
    public String selectLocationAndEvent() {
        Object r = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const pick=async(ng)=>{ let e=null;"
                + "   for(let k=0;k<15;k++){ e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null);"
                + "     if(e && [...e.options].some(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)))) break; await sleep(400); }"
                + "   if(!e) return '(no select)';"
                + "   const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i<0) return '(no options)';"
                + "   e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "   await sleep(600); return norm(e.options[i].textContent); };"
                + " const loc=await pick('alertconfig.locationid');"
                + " await sleep(2500);"   // Location loads the alerts grid + the Event list
                + " const ev=await pick('alertconfig.eventid');"
                + " resolve('Location='+loc+' | Event='+ev); })");
        waitForAngular(800);
        String s = r == null ? "" : r.toString();
        int i = s.indexOf("Location=");
        if (i >= 0) lastLocation = s.substring(i + 9, s.indexOf(" | ", i)).trim();
        int j = s.indexOf("Event=");
        if (j >= 0) lastEvent = s.substring(j + 6).trim();
        return s;
    }

    /**
     * Tick ONE channel checkbox on the entry row — {@code alertconfig.sms} / {@code .whatsapp} / {@code .email}.
     *
     * <p>Deliberately NOT the grid's {@code AD.*} boxes: those belong to already-configured rows, and ticking one
     * would silently edit an existing alert instead of the row being added.</p>
     */
    public String tickChannel() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__alCb').forEach(e=>e.removeAttribute('id'));"
                + " const c=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && !x.checked"
                + "   && /^alertconfig\\.(sms|whatsapp|email)$/.test(x.getAttribute('ng-model')||''));"
                + " if(!c) return ''; c.id='__alCb'; return (c.getAttribute('ng-model')||'').split('.').pop(); }");
        String ch = tagged == null ? "" : tagged.toString();
        if (ch.isEmpty()) { System.out.println("tickChannel: no entry-row channel checkbox found"); return "(no channel checkbox found)"; }
        try { page.locator("#__alCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(6000)); }
        catch (Exception e) { System.out.println("tickChannel: click failed - " + e.getMessage()); }
        Object ok = page.evaluate("() => { const c=document.getElementById('__alCb'); const v=c?c.checked:false; if(c) c.removeAttribute('id'); return v; }");
        lastChannel = ch;
        waitForAngular(500);
        return ch + (Boolean.TRUE.equals(ok) ? " (ticked)" : " (NOT ticked)");
    }

    /** Click <b>Add</b> ({@code AddAlertConfig}) to commit the row; returns the resulting {@code AlertDetailsList} size. */
    public String clickAdd() {
        Object tagged = page.evaluate("() => { document.querySelectorAll('#__alAdd').forEach(e=>e.removeAttribute('id'));"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/AddAlertConfig/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null);"
                + " if(!b) return ''; b.id='__alAdd'; return b.getAttribute('ng-click'); }");
        String how = tagged == null ? "" : tagged.toString();
        if (how.isEmpty()) { System.out.println("clickAdd: Add button not found"); return "(Add not found)"; }
        try { page.locator("#__alAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__alAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        Object n = page.evaluate("() => { let v=-1; document.querySelectorAll('*').forEach(el=>{ if(v>=0) return; try{ const s=angular.element(el).scope();"
                + " if(s && Array.isArray(s.AlertDetailsList)) v=s.AlertDetailsList.length; }catch(e){} }); return v; }");
        int rows = n instanceof Number ? ((Number) n).intValue() : -1;
        return "Add clicked {" + how + "} -> AlertDetailsList=" + rows;
    }

    /** Click <b>Save</b> ({@code fnSaveAlertsConfig}) and return the toast. */
    public String saveAndGetToast() {
        page.evaluate("() => { window.__alToasts=[]; if(window.__alObs) window.__alObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message, .toast').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__alToasts.includes(t)) window.__alToasts.push(t); }); };"
                + " window.__alObs=new MutationObserver(grab); window.__alObs.observe(document.body,{childList:true,subtree:true}); }");
        Object tagged = page.evaluate("() => { document.querySelectorAll('#__alSave').forEach(e=>e.removeAttribute('id'));"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnSaveAlertsConfig/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return ''; b.id='__alSave'; return b.getAttribute('ng-click')||'save'; }");
        String how = tagged == null ? "" : tagged.toString();
        if (how.isEmpty()) { System.out.println("saveAndGetToast: Save button not found"); return ""; }
        System.out.println("AlertsPage save => " + how);
        try { page.locator("#__alSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("saveAndGetToast: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__alSave'); if(e) e.removeAttribute('id'); }");
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,.jconfirm,.ng-confirm-box')].some(m=>m.offsetParent!==null && /do you want to|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.modal,.jconfirm,.ng-confirm-box')].find(m=>m.offsetParent!==null && /do you want to|are you sure|confirm/i.test(m.textContent||''));"
                    + " const b=[...(box||document).querySelectorAll('button,a')].find(x=>/^(yes|ok|save|submit)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(800);
        }
        try {
            page.waitForFunction("() => (window.__alToasts||[]).some(a=>/saved|success|added|updated|please|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__alToasts||[]; return a.find(x=>/saved|added|success|updated/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
