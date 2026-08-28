package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Fall Risk Assessment</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Fall Risk Assessment</b>
 * ({@code #/FallRiskAssessment}) → <b>Add</b> ({@code AddFallRiskAssessment()} → {@code #/add-FallRiskAssessment})
 * → enter <b>Code*</b> ({@code fallrisk.Code}), <b>Remark*</b> ({@code fallrisk.Description}) and pick
 * <b>FRAType*</b> ({@code fallrisk.FRAType}) → <b>Submit</b> ({@code fnIUD()}) → success toast.</p>
 *
 * <p><b>FRAType</b> is the Fall Risk Assessment <i>Type</i> master maintained by the sibling screen
 * {@link FallRiskAssessmentType} (menu label <b>FRAType</b>, {@code #/FRATypeMaster}) — so this list grows as that
 * one is used. It is an ng-options {@code <select>}: the option is chosen from the select's OWN option list and the
 * bound model is read back, because assigning a value that is not in the list is silently discarded by Angular.</p>
 *
 * <p>The form also carries an optional detail line — <b>FRA</b> ({@code fallrisk.Historys}) + <b>Value</b>
 * ({@code fallrisk.Values}) with an inner <b>Add</b> ({@code AddFRADetails(FRDetailsList)}) — which is not starred
 * and is not needed for the save, so this flow leaves it alone.</p>
 */
public class FallRiskAssessment extends BasePage {

    public FallRiskAssessment(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/FallRiskAssessment";
    public String lastCode = "", lastRemark = "", lastFraType = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /** Grab a full-page screenshot with the toast still showing. Never fails the flow. */
    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    private static final String[] REMARKS = {
            "Morse Fall Scale Assessment", "Admission Fall Screening", "Post Fall Review",
            "Daily Fall Risk Review", "Paediatric Fall Assessment"};

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
            waitForAngular(1500);
            // EXACT text / href — the submenu also holds "FRAType" (#/FRATypeMaster),
            // "QuestionsForFallRiskMaster" (#/FallRiskChecklistQuestion) and
            // "FallRiskChecklistMaster" (#/FallRiskIntervention), all different screens.
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__fraMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*fall\\s*risk\\s*assessment\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/FallRiskAssessment'); if(!a) return ''; a.id='__fraMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("FallRiskAssessment.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            // The link can still be collapsed/animating — a synthetic click fires its route handler regardless.
            try { page.locator("#__fraMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__fraMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__fraMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddFallRiskAssessment\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("FallRiskAssessment.nav: list not confirmed (attempt " + (attempt + 1) + ")"); }
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
     * On the REAL Fall Risk Assessment list — verified by its own Add button ({@code AddFallRiskAssessment}), not
     * just the URL: the SPA can leave a previous screen mounted while the hash already reads
     * {@code #/FallRiskAssessment}, and that leftover markup carries a generic commonmaster form which a Code-only
     * check would happily accept.
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("fallriskassessment")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddFallRiskAssessment\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"));
    }

    /** Real-click <b>Add</b> ({@code AddFallRiskAssessment}) → {@code #/add-FallRiskAssessment}. Polls — it renders after the grid. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddFallRiskAssessment\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__fraAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("FallRiskAssessment.clickAdd: Add button not found"); return false; }
        try { page.locator("#__fraAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("FallRiskAssessment.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__fraAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='fallrisk.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("FallRiskAssessment.clickAdd: add form did not render"); }
        waitForAngular(1200);
        return page.url().toLowerCase().contains("add-fallriskassessment");
    }

    /** True once the Add form is up (its Code field is present). */
    public boolean onAddForm() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='fallrisk.Code')"));
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> ({@code fallrisk.Code}), <b>Remark*</b> ({@code fallrisk.Description}) and select
     * <b>FRAType*</b> ({@code fallrisk.FRAType}). The FRAType list is populated asynchronously, so it is polled;
     * the option is taken from the select's OWN option list (the first real one) and the bound model is read back —
     * an assignment of a value that is not in the list would be silently dropped by Angular.
     */
    public String fillDetails(int attempt) {
        String code = "FR" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('fallrisk.Code', a.code); const rm=set('fallrisk.Description', a.remark);"
                // FRAType: async ng-options select — poll for a real option, pick it from the select's own list.
                + " let ft='(no-opt)';"
                + " for(let k=0;k<15;k++){ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='fallrisk.FRAType' && x.offsetParent!==null);"
                + "   if(e){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "     if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "       await sleep(400); const c=A.element(e).controller('ngModel');"
                + "       ft=norm(e.options[i].textContent)+' [model='+(c&&c.$modelValue!=null?String(c.$modelValue):'?')+']'; break; } }"
                + "   await sleep(400); }"
                + " resolve('Code='+cd+' | Remark='+rm+' | FRAType='+ft); })",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(500);
        String s = r == null ? "" : r.toString();
        int i = s.indexOf("FRAType=");
        if (i >= 0) lastFraType = s.substring(i + 8).replaceAll("\\s*\\[model=.*$", "").trim();
        return s;
    }

    /**
     * Click <b>Submit</b> ({@code fnIUD()}) and return the toast. Code/Remark are re-applied to the
     * {@code fallrisk} model on every scope first (the form can re-render and blank the Code), and the whole thing
     * is retried — the async save occasionally yields no toast at all. FRAType is deliberately NOT re-assigned:
     * its value came from the select's own option list and re-writing it blind could drop the binding.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__fraToasts=[]; if(window.__fraObs) window.__fraObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__fraToasts.includes(t)) window.__fraToasts.push(t); }); };"
                + " window.__fraObs=new MutationObserver(grab); window.__fraObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForAngular(500);
            Object res = page.evaluate("(a) => { const A=window.angular;"
                    + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    + " setInp('fallrisk.Code', a.code); setInp('fallrisk.Description', a.remark);"
                    + " const seen=new Set(); let handlerScope=null;"
                    + " document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id);"
                    + "   if(s.fallrisk && typeof s.fallrisk==='object'){ s.fallrisk.Code=a.code; s.fallrisk.Description=a.remark; }"
                    + "   if(typeof s.fnIUD==='function' && !handlerScope) handlerScope=s; }catch(e){} });"
                    + " if(handlerScope){ try{ handlerScope.$apply(function(){ handlerScope.fnIUD(); }); }catch(e){ try{ handlerScope.fnIUD(); }catch(e2){} } return 'invoked:fnIUD'; }"
                    + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(b){ b.id='__fraSubmit'; return 'click'; } return 'none'; }",
                    java.util.Map.of("code", lastCode, "remark", lastRemark));
            System.out.println("FallRiskAssessment submit[" + attempt + "] => " + res);
            if ("click".equals(String.valueOf(res))) {
                try { page.locator("#__fraSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
                page.evaluate("() => { const e=document.getElementById('__fraSubmit'); if(e) e.removeAttribute('id'); }");
            }
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                        + " || (window.__fraToasts||[]).some(a=>/fall|risk|fra|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__fraToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            // "saved" ends the flow successfully; "already exists" is NOT retryable with the SAME Code/Remark —
            // return it immediately rather than blindly resubmitting stale data, so the caller can regenerate
            // fresh details (fillDetails) and try again.
            if (last.toLowerCase().matches(".*(saved|added|success|exist|already).*")) return last;
            page.evaluate("() => { window.__fraToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
