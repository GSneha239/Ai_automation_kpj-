package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Title Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Title</b> ({@code #/title}) →
 * <b>Add</b> ({@code AddTitle()} → {@code #/add-title}) → select <b>Gender</b> ({@code title.GenderID}) and enter
 * <b>Code*</b> ({@code title.Code}) + <b>Title*</b> ({@code title.Description}) → <b>Submit</b>
 * ({@code fnIUDTitle()}) → success toast.</p>
 */
public class TitleMaster extends BasePage {

    public TitleMaster(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/title";
    public String lastCode = "", lastTitle = "", lastGender = "";
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
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*title(\\s*master)?\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/title'); if(!a) return ''; a.id='__tiMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("TitleMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__tiMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("TitleMaster.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__tiMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddTitle\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("TitleMaster.nav: Title screen not confirmed (attempt " + (attempt + 1) + ")"); }
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

    /** On the REAL Title Master list — verified by its own Add ({@code AddTitle}) button, not just the URL. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("title")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddTitle\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"));
    }

    /** Real-click <b>Add</b> ({@code AddTitle}) → {@code #/add-title}. Polls — it renders after the grid. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddTitle\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__tiAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("TitleMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__tiAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("TitleMaster.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__tiAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='title.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("TitleMaster.clickAdd: add form did not render"); }
        waitForAngular(1000);
        return page.url().toLowerCase().contains("add-title");
    }

    // ---- form ------------------------------------------------------------

    /**
     * Select <b>Gender</b> ({@code title.GenderID} — first real option of --Select--/Ambiguous/Female/Male/
     * No Information/Other) and enter <b>Code*</b> ({@code title.Code}) + <b>Title*</b>
     * ({@code title.Description}). Returns a summary.
     */
    public String fillDetails() {
        String code = "TI" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastTitle = new String[]{"Datuk","Datin","Prof","Tuan","Puan","Encik"}[(int)(Math.abs(System.nanoTime())%6)];
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const pick=async(ng)=>{ for(let k=0;k<15;k++){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(e){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); } } await sleep(400); } return '(no-opt)'; };"
                + " const g=await pick('title.GenderID');"
                + " const cd=set('title.Code', a.code); const ti=set('title.Description', a.title);"
                + " resolve('Gender='+g+' | Code='+cd+' | Title='+ti); })",
                java.util.Map.of("code", code, "title", lastTitle));
        waitForAngular(500);
        String s = r == null ? "" : r.toString();
        int i = s.indexOf("Gender=");
        if (i >= 0) { int j = s.indexOf(" |", i); lastGender = s.substring(i + 7, j < 0 ? s.length() : j).trim(); }
        return s;
    }

    /** Click <b>Submit</b> ({@code fnIUDTitle}) and return the toast; screenshots it while still on screen. */
    /**
     * Submit; on "… already exists!" regenerate the Code/Title and submit again (up to 3 attempts).
     * Unlike its siblings this screen had NO retry at all, so a duplicate simply failed the run.
     */
    public String submitAndGetToast() { return submitAndGetToast(0); }

    private String submitAndGetToast(int attempt) {
        Object tagged = page.evaluate("() => { window.__tiToasts=[]; if(window.__tiObs) window.__tiObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__tiToasts.includes(t)) window.__tiToasts.push(t); }); };"
                + " window.__tiObs=new MutationObserver(grab); window.__tiObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__tiSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__tiSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__tiSubmit'); if(e) e.removeAttribute('id'); }");
        // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN.
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                    + " || (window.__tiToasts||[]).some(a=>/title|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__tiToasts||[]; return a.find(x=>/(title|record|master).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        String toast = r == null ? "" : r.toString().trim();
        if (attempt < 2 && toast.toLowerCase().matches(".*(exist|already).*")) {
            System.out.println("submitAndGetToast: \"" + toast + "\" — regenerating the details and retrying");
            fillDetails();
            // The title pool is small and this environment already holds every entry, so another pick collides
            // again. Append a short number so the retry is genuinely unique. First attempt keeps the clean name.
            lastTitle = lastTitle + " " + (Math.abs(System.nanoTime()) % 10000);
            // …and PUSH IT BACK INTO THE FORM. fillDetails() has already written the un-suffixed name to
            // title.Description; this screen's submit does NOT re-apply the stored values (its siblings do), so
            // changing only the Java field left the DOM — and therefore the save — with the duplicate title.
            page.evaluate("(v) => { const A=window.angular;"
                    + " const e=[...document.querySelectorAll(\"input[ng-model='title.Description'],textarea[ng-model='title.Description']\")].find(x=>x.offsetParent!==null);"
                    + " if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", lastTitle);
            waitForAngular(300);
            return submitAndGetToast(attempt + 1);
        }
        return toast;
    }
}
