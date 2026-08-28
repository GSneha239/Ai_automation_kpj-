package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Visit Type Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Visit Type</b> ({@code #/visitType})
 * → <b>Add</b> ({@code AddVisitType()} → {@code #/add-visitType}) → enter <b>Code*</b> ({@code visitType.Code}),
 * <b>Visit Type*</b> ({@code visitType.Description}) and pick <b>Location*</b>
 * ({@code visitType.VisitTypeLocationIDs}) → <b>Submit</b> ({@code fnIUDVisitType()}) → success toast.</p>
 *
 * <p><b>Location is a select2 MULTI-select</b> ({@code <select multiple id="ddlLocation">} with a select2 overlay,
 * so the real {@code <select>} is hidden behind {@code #s2id_autogen1}). It is set by flipping
 * {@code option.selected} and firing change (plus a select2 refresh) — a plain {@code selectOption} on a hidden
 * select2 does not stick. There is also an optional Service multi-select ({@code visitType.VisitTypeServiceIDs}),
 * not required for the save.</p>
 */
public class VisitTypeMaster extends BasePage {

    public VisitTypeMaster(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/visitType";
    public String lastCode = "", lastVisitType = "", lastLocation = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
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
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*visit\\s*type(\\s*master)?\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/visitType'); if(!a) return ''; a.id='__vtMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("VisitTypeMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__vtMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("VisitTypeMaster.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__vtMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddVisitType\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("VisitTypeMaster.nav: Visit Type screen not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
        }
        return onScreen();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** On the REAL Visit Type list — verified by its own Add ({@code AddVisitType}) button, not just the URL. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("visittype")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddVisitType\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"));
    }

    /** Real-click <b>Add</b> ({@code AddVisitType}) → {@code #/add-visitType}. Polls — it renders after the grid. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddVisitType\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__vtAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("VisitTypeMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__vtAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("VisitTypeMaster.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__vtAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='visitType.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("VisitTypeMaster.clickAdd: add form did not render"); }
        waitForAngular(1000);
        return page.url().toLowerCase().contains("add-visittype");
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b>, <b>Visit Type*</b> and select <b>Location*</b>. Location is a select2 multi-select — set
     * by {@code option.selected = true} + change (+ select2 refresh); the underlying select is hidden, so
     * {@code selectOption} would not take.
     */
    public String fillDetails() {
        String code = "VT" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastVisitType = new String[]{"Walk In","Follow Up","Health Screening","Second Opinion","Day Care","Referral Visit"}[(int)(Math.abs(System.nanoTime())%6)];
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('visitType.Code', a.code); const vt=set('visitType.Description', a.vt);"
                // Location: MULTI-select behind select2 (hidden) → flip option.selected + fire change + refresh select2.
                + " let loc='(no)';"
                + " for(let k=0;k<15;k++){ const e=document.querySelector(\"select[ng-model='visitType.VisitTypeLocationIDs']\") || document.getElementById('ddlLocation');"
                + "   if(e){ const opts=[...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "     if(opts.length){ const pick=opts.find(o=>/kpj/i.test(norm(o.textContent)))||opts[0]; pick.selected=true;"
                + "       e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){}"
                + "       if($){ try{ $(e).trigger('change'); }catch(x){} try{ $(e).select2('val', [pick.value]); }catch(x){} }"
                + "       await sleep(400); loc=norm(pick.textContent); break; } }"
                + "   await sleep(400); }"
                + " resolve('Code='+cd+' | VisitType='+vt+' | Location='+loc); })",
                java.util.Map.of("code", code, "vt", lastVisitType));
        waitForAngular(600);
        String s = r == null ? "" : r.toString();
        int i = s.indexOf("Location=");
        if (i >= 0) lastLocation = s.substring(i + 9).trim();
        return s;
    }

    /**
     * Click <b>Submit</b> ({@code fnIUDVisitType}) and return the toast; screenshots it while still on screen.
     * On "… already exists!" the Code/Visit Type/Location are regenerated and Submit is tried again (up to 3
     * attempts) — unlike its {@link TitleMaster} sibling this screen had NO retry at all, so a duplicate simply
     * failed the run.
     */
    public String submitAndGetToast() { return submitAndGetToast(0); }

    private String submitAndGetToast(int attempt) {
        Object tagged = page.evaluate("() => { window.__vtToasts=[]; if(window.__vtObs) window.__vtObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__vtToasts.includes(t)) window.__vtToasts.push(t); }); };"
                + " window.__vtObs=new MutationObserver(grab); window.__vtObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDVisitType/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__vtSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__vtSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vtSubmit'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                    + " || (window.__vtToasts||[]).some(a=>/visit|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__vtToasts||[]; return a.find(x=>/(visit|record|master).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        String toast = r == null ? "" : r.toString().trim();
        if (attempt < 2 && toast.toLowerCase().matches(".*(exist|already).*")) {
            System.out.println("VisitTypeMaster.submitAndGetToast: \"" + toast + "\" — regenerating the details and retrying");
            fillDetails();
            // The visit-type name pool is small and this environment may already hold every entry, so a repeat
            // pick collides again. Append a short number so the retry is genuinely unique.
            lastVisitType = lastVisitType + " " + (Math.abs(System.nanoTime()) % 10000);
            // …and PUSH IT BACK INTO THE FORM. fillDetails() has already written the un-suffixed name to
            // visitType.Description, and this screen's submit does not re-apply the stored Java values, so
            // changing only the Java field would leave the DOM — and therefore the save — with the duplicate.
            page.evaluate("(v) => { const A=window.angular;"
                    + " const e=[...document.querySelectorAll(\"input[ng-model='visitType.Description'],textarea[ng-model='visitType.Description']\")].find(x=>x.offsetParent!==null);"
                    + " if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", lastVisitType);
            waitForAngular(300);
            return submitAndGetToast(attempt + 1);
        }
        return toast;
    }
}
