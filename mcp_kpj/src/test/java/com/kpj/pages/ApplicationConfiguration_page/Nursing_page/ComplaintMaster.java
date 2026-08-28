package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Complaint</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Complaint</b>
 * ({@code #/Complaint}) → select <b>Group*</b> ({@code complaint.groupid}) → enter <b>Code*</b>
 * ({@code complaint.Code}) + <b>Complaint*</b> ({@code complaint.Description}) → <b>Submit</b> → toast.</p>
 *
 * <p>This is a <b>list → Add → form</b> screen: <b>Add</b> ({@code AddComplaint()}) opens
 * {@code #/addComplaint}. Gotcha: Submit's handler is <b>{@code fnIUDState()}</b> — the same reused/misnamed
 * handler as Body Symptoms, nothing to do with states.</p>
 */
public class ComplaintMaster extends BasePage {

    public ComplaintMaster(Page page) { super(page); }

    public static final String ROUTE = "#/Complaint";
    public String lastCode = "", lastComplaint = "", lastGroup = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    /** Real complaint names — the Complaint field is what a nurse reads in the grid. */
    private static final String[] COMPLAINTS = {
            "Chest Pain", "Shortness of Breath", "Persistent Headache", "High Fever", "Abdominal Pain", "Dizziness"};

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            // EXACT "Nursing" — "Nursing Station" is a different module and a prefix match hits it first.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1800);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*complaint\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/Complaint'); if(!a) return ''; a.id='__cmMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("ComplaintMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__cmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("ComplaintMaster.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__cmMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddComplaint\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("ComplaintMaster.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
        }
        return onScreen();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,45); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** On the REAL Complaint screen — its own Add ({@code AddComplaint}) button or the add form is present. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("complaint")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddComplaint\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"
                + " || [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='complaint.Description')"));
    }

    /** Real-click <b>Add</b> ({@code AddComplaint}) → {@code #/addComplaint}. Polls — it renders after the grid. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddComplaint\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__cmAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("ComplaintMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__cmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ComplaintMaster.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__cmAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='complaint.Description')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("ComplaintMaster.clickAdd: add form did not render"); }
        waitForAngular(1000);
        return page.url().toLowerCase().contains("addcomplaint");
    }

    // ---- form ------------------------------------------------------------

    /**
     * Select <b>Group*</b> ({@code complaint.groupid}) then enter <b>Code*</b> and <b>Complaint*</b>.
     * {@code attempt} shifts BOTH Code and Complaint so a retry after an "already exists" toast submits
     * genuinely different details; once the small realistic-name pool is exhausted a short number is
     * appended so the retry is still guaranteed unique.
     */
    public String fillDetails(int attempt) {
        String code = "CM" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastComplaint = COMPLAINTS[attempt % COMPLAINTS.length] + (attempt >= COMPLAINTS.length ? " " + (attempt / COMPLAINTS.length + 1) : "");
        if (attempt >= COMPLAINTS.length) lastComplaint = lastComplaint + " " + (Math.abs(System.nanoTime()) % 10000);
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const pick=async(ng)=>{ for(let k=0;k<15;k++){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(e){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); } } await sleep(400); } return '(no-opt)'; };"
                + " const cat=await pick('complaint.groupid');"
                + " const cd=set('complaint.Code', a.code); const sy=set('complaint.Description', a.symptom);"
                + " resolve('Group='+cat+' | Code='+cd+' | Complaint='+sy); })",
                java.util.Map.of("code", code, "symptom", lastComplaint));
        waitForAngular(500);
        String s = r == null ? "" : r.toString();
        int i = s.indexOf("Group=");
        if (i >= 0) { int j = s.indexOf(" |", i); lastGroup = s.substring(i + 6, j < 0 ? s.length() : j).trim(); }
        return s;
    }

    /** Click <b>Submit</b> (handler {@code fnIUDState}) and return the toast; screenshots it while still shown. */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__cmToasts=[]; if(window.__cmObs) window.__cmObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__cmToasts.includes(t)) window.__cmToasts.push(t); }); };"
                + " window.__cmObs=new MutationObserver(grab); window.__cmObs.observe(document.body,{childList:true,subtree:true}); grab();"
                // Submit's handler is fnIUDState() on this screen (reused/misnamed) — match it, then fall back to text.
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDState/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__cmSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__cmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cmSubmit'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                    + " || (window.__cmToasts||[]).some(a=>/symptom|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__cmToasts||[]; return a.find(x=>/(symptom|record|master).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
