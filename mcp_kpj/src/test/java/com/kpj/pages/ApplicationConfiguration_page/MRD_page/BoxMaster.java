package com.kpj.pages.ApplicationConfiguration_page.MRD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; MRD &gt; <b>Box Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>MRD</b> (submenu) → <b>Box Master</b>
 * ({@code #/BoxMasterList}) → <b>Add</b> ({@code AddBoxMaster()} → {@code #/add-BoxMaster}) → pick <b>Row*</b>
 * ({@code BoxMaster.rowid}) and enter <b>Box Code*</b> ({@code BoxMaster.code}) + <b>Remark*</b>
 * ({@code BoxMaster.description}) → <b>Submit</b> ({@code fnIUDBoxMaster()}) → success toast.</p>
 *
 * <p><b>Row</b> is an ng-options {@code <select>} fed by the sibling <b>Row Master</b> screen
 * ({@code #/RowMasterList}) — currently just {@code ---Select---} and {@code Row1}. The option is taken from the
 * select's OWN option list and the bound model read back, because assigning a value that is not in the list is
 * silently discarded by Angular.</p>
 *
 * <p>This list renders <b>well after the route settles</b> (a probe caught it still showing the previous screen's
 * "Transfer" header seconds in), so navigation waits on the screen's own Add button rather than the URL, and the
 * Add click is retried.</p>
 */
public class BoxMaster extends BasePage {

    public BoxMaster(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/BoxMasterList";
    public String lastRow = "", lastCode = "", lastRemark = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    private static final String[] REMARKS = {
            "Active Files Box", "Archived Records Box", "Discharge Summaries Box",
            "Radiology Reports Box", "Consent Forms Box"};

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            // EXACT "MRD" — matched on the whole label so it can't hit another submenu that merely contains it.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*mrd\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__bmMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*box\\s*master\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/BoxMasterList'); if(!a) return ''; a.id='__bmMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("BoxMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__bmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__bmMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__bmMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddBoxMaster\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(25000));
            } catch (Exception ignore) { System.out.println("BoxMaster.nav: list not confirmed (attempt " + (attempt + 1) + ")"); }
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

    /**
     * On the REAL Box Master list — verified by its own Add button ({@code AddBoxMaster}), NOT the URL: this screen
     * holds the right hash for seconds while the previous screen ("Transfer") is still mounted.
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("boxmaster")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddBoxMaster\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"));
    }

    /** Real-click <b>Add</b> ({@code AddBoxMaster}) → {@code #/add-BoxMaster}. Polls, and retries the click. */
    public boolean clickAdd() {
        for (int round = 0; round < 3 && !onAddForm(); round++) {
            boolean tagged = false;
            for (int i = 0; i < 30 && !tagged; i++) {
                tagged = Boolean.TRUE.equals(page.evaluate("() => { document.querySelectorAll('#__bmAdd').forEach(e=>e.removeAttribute('id')); const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddBoxMaster\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__bmAdd'; return true; }"));
                if (!tagged) page.waitForTimeout(500);
            }
            if (!tagged) { System.out.println("BoxMaster.clickAdd: Add button not found (round " + (round + 1) + ")"); return false; }
            try { page.locator("#__bmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("BoxMaster.clickAdd: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__bmAdd'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='BoxMaster.code')",
                        null, new Page.WaitForFunctionOptions().setTimeout(12000));
            } catch (Exception ignore) { System.out.println("BoxMaster.clickAdd: entry form did not render (round " + (round + 1) + ")"); }
            waitForAngular(1200);
        }
        return onAddForm();
    }

    /** True once the Add form is up (its Box Code field is present). */
    public boolean onAddForm() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='BoxMaster.code')"));
    }

    // ---- form ------------------------------------------------------------

    /**
     * Pick <b>Row*</b> ({@code BoxMaster.rowid}) and enter <b>Box Code*</b> ({@code BoxMaster.code}) +
     * <b>Remark*</b> ({@code BoxMaster.description}). The Row list is populated asynchronously, so it is polled;
     * the option is taken from the select's own list and the bound model read back — assigning a value absent from
     * the list would be silently dropped by Angular.
     */
    public String fillDetails() {
        String code = "BX" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime()) % REMARKS.length)];
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                // Row: async ng-options select — poll for a real option, pick it from the select's own list.
                + " let rw='(no-opt)';"
                + " for(let k=0;k<15;k++){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='BoxMaster.rowid' && x.offsetParent!==null);"
                + "   if(e){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "     if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "       await sleep(400); const c=A.element(e).controller('ngModel');"
                + "       rw=norm(e.options[i].textContent)+' [model='+(c&&c.$modelValue!=null?String(c.$modelValue):'?')+']'; break; } }"
                + "   await sleep(400); }"
                + " const cd=set('BoxMaster.code', a.code); const rm=set('BoxMaster.description', a.remark);"
                + " resolve('Row='+rw+' | BoxCode='+cd+' | Remark='+rm); })",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(600);
        String s = r == null ? "" : r.toString();
        int i = s.indexOf("Row=");
        if (i >= 0) lastRow = s.substring(i + 4, s.indexOf(" | ", i)).replaceAll("\\s*\\[model=.*$", "").trim();
        return s;
    }

    /** Click <b>Submit</b> ({@code fnIUDBoxMaster}) and return the toast. */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__bmToasts=[]; if(window.__bmObs) window.__bmObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__bmToasts.includes(t)) window.__bmToasts.push(t); }); };"
                + " window.__bmObs=new MutationObserver(grab); window.__bmObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 40; attempt++) {
            waitForAngular(500);
            Object tagged = page.evaluate("() => { document.querySelectorAll('#__bmSubmit').forEach(e=>e.removeAttribute('id')); const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return ''; b.id='__bmSubmit'; return b.getAttribute('ng-click')||'submit'; }");
            String how = tagged == null ? "" : tagged.toString();
            System.out.println("BoxMaster submit[" + attempt + "] => " + (how.isEmpty() ? "(no Submit button)" : how));
            if (how.isEmpty()) return last;
            try { page.locator("#__bmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
            page.evaluate("() => { const e=document.getElementById('__bmSubmit'); if(e) e.removeAttribute('id'); }");
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(el.textContent||''))"
                        + " || (window.__bmToasts||[]).some(a=>/box|saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__bmToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            if (last.toLowerCase().matches(".*(saved|added|success).*")) return last;
            // "… already exists!" is NOT retryable with the SAME values — the old loop re-submitted the
            // identical Code/Remark and simply got the same rejection every attempt. Regenerate the
            // details (fillDetails() picks a fresh Code and a different description) and try again.
            if (last.toLowerCase().matches(".*(exist|already).*")) {
                System.out.println("submitAndGetToast: \"" + last + "\" — regenerating the details and retrying");
                fillDetails();
                // The realistic-name pool is SMALL and this environment already holds EVERY entry, so
                // picking another name collides again — that is why the retries kept failing. Append a
                // short number so the retry is genuinely unique, while still reading like configuration
                // ("Peanut 4821", not machine noise). The FIRST attempt keeps the clean name.
                lastRemark = lastRemark + " " + (Math.abs(System.nanoTime()) % 10000);
            }
            page.evaluate("() => { window.__bmToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
