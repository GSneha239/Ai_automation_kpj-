package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Fall Risk Checklist Master</b> — configuration screen
 * Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Fall Risk Checklist Master</b>
 * ({@code #/FallRiskIntervention}) → enter <b>Code*</b> ({@code item.Code}) + <b>Description*</b>
 * ({@code item.Description} — the fall-risk question itself) → <b>Submit</b> ({@code fnSubmit()}) → toast.</p>
 *
 * <p><b>INLINE-ADD</b> screen — no Add button; the form ({@code myFallRiskChecklistQuestionForm}) sits above the
 * grid, with a pre-selected <b>Form Name*</b> ({@code selectedFormName}). NB the ng-models are
 * {@code item.*} here, NOT the usual {@code commonmaster.*}, and Submit is the generic {@code fnSubmit()}.</p>
 *
 * <p>Menu note: the Patient submenu also holds <b>Fall Risk Checklist Master</b>
 * ({@code #/FallRiskIntervention}) and <b>Fall Risk Assessment</b> ({@code #/FallRiskAssessment}) — match the
 * link text exactly or the exact href so a loose "fall" match cannot pick the wrong screen.</p>
 */
public class FallRiskChecklist extends BasePage {

    public FallRiskChecklist(Page page) { super(page); }

    public static final String ROUTE = "#/FallRiskIntervention";
    public String lastCode = "", lastQuestion = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    /** Real fall-prevention checklist items — the Description is the intervention a nurse carries out. */
    private static final String[] QUESTIONS = {
            "Keep bed at the lowest position",
            "Ensure call bell is within reach",
            "Apply non-slip footwear",
            "Keep bed rails up at all times",
            "Ensure adequate lighting at night",
            "Assist patient when mobilising"};

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
            // EXACT label / href — "Fall Risk Checklist Master" and "Fall Risk Assessment" are different screens.
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*fall\\s*risk\\s*checklist\\s*master\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/FallRiskIntervention'); if(!a) return ''; a.id='__fcMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("FallRiskChecklist.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__fcMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("FallRiskChecklist.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__fcMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='item.Description')",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("FallRiskChecklist.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
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

    /** On the REAL screen — verified by its own Description field, not just the URL. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("fallriskintervention")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='item.Description')"));
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> ({@code item.Code}) and <b>Description*</b> ({@code item.Description}) — a real question.
     * {@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely different
     * details.
     */
    public String fillDetails(int attempt) {
        String code = "FC" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastQuestion = QUESTIONS[attempt % QUESTIONS.length] + (attempt >= QUESTIONS.length ? " " + (attempt / QUESTIONS.length + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('item.Code', a.code); const ds=set('item.Description', a.question);"
                + " return 'Code='+cd+' | Description='+ds; }",
                java.util.Map.of("code", code, "question", lastQuestion));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> ({@code fnSubmit}) and return the toast; screenshots it while still on screen. */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__fcToasts=[]; if(window.__fcObs) window.__fcObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__fcToasts.includes(t)) window.__fcToasts.push(t); }); };"
                + " window.__fcObs=new MutationObserver(grab); window.__fcObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnSubmit/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__fcSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__fcSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__fcSubmit'); if(e) e.removeAttribute('id'); }");
        // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN.
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                    + " || (window.__fcToasts||[]).some(a=>/question|fall|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__fcToasts||[]; return a.find(x=>/(question|record|master).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
