package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Questions for Fall Risk Master</b> — configuration screen
 * Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Questions for Fall Risk Master</b>
 * ({@code #/FallRiskChecklistQuestion}) → enter <b>Code*</b> ({@code item.Code}) + <b>Description*</b>
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
public class FallRiskQuestion extends BasePage {

    public FallRiskQuestion(Page page) { super(page); }

    public static final String ROUTE = "#/FallRiskChecklistQuestion";
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

    /** Real fall-risk screening questions — the Description IS the question a nurse reads. */
    private static final String[] QUESTIONS = {
            "Has the patient fallen in the past 12 months?",
            "Does the patient use a walking aid?",
            "Is the patient on sedatives or diuretics?",
            "Does the patient have impaired vision?",
            "Is the patient unsteady when standing or walking?",
            "Does the patient need assistance to use the toilet?"};

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
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*questions\\s*for\\s*fall\\s*risk\\s*master\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/FallRiskChecklistQuestion'); if(!a) return ''; a.id='__frMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("FallRiskQuestion.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__frMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("FallRiskQuestion.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__frMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='item.Description')",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("FallRiskQuestion.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
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
        if (!page.url().toLowerCase().contains("fallriskchecklistquestion")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='item.Description')"));
    }

    // ---- form ------------------------------------------------------------

    /** Enter <b>Code*</b> ({@code item.Code}) and <b>Description*</b> ({@code item.Description}) — a real question. */
    public String fillDetails() {
        String code = "FQ" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastQuestion = QUESTIONS[(int) (Math.abs(System.nanoTime()) % QUESTIONS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('item.Code', a.code); const ds=set('item.Description', a.question);"
                + " return 'Code='+cd+' | Description='+ds; }",
                java.util.Map.of("code", code, "question", lastQuestion));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Submit</b> ({@code fnSubmit}) and return the toast; screenshots it while still on screen. On
     * "… already exists!" a fresh Code/Description is generated and Submit is tried again (up to 3 attempts).
     */
    public String submitAndGetToast() { return submitAndGetToast(0); }

    private String submitAndGetToast(int attempt) {
        Object tagged = page.evaluate("() => { window.__frToasts=[]; if(window.__frObs) window.__frObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__frToasts.includes(t)) window.__frToasts.push(t); }); };"
                + " window.__frObs=new MutationObserver(grab); window.__frObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnSubmit/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__frSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__frSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__frSubmit'); if(e) e.removeAttribute('id'); }");
        // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN.
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                    + " || (window.__frToasts||[]).some(a=>/question|fall|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__frToasts||[]; return a.find(x=>/(question|record|master).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        String toast = r == null ? "" : r.toString().trim();
        if (attempt < 2 && toast.toLowerCase().matches(".*(exist|already).*")) {
            System.out.println("FallRiskQuestion.submitAndGetToast: \"" + toast + "\" — regenerating the details and retrying");
            fillDetails();
            // The question pool is small — append a short number so a repeat pick is still genuinely unique text,
            // while still reading like a real question rather than machine noise.
            lastQuestion = lastQuestion + " (" + (Math.abs(System.nanoTime()) % 10000) + ")";
            page.evaluate("(v) => { const A=window.angular;"
                    + " const e=[...document.querySelectorAll(\"input[ng-model='item.Description'],textarea[ng-model='item.Description']\")].find(x=>x.offsetParent!==null);"
                    + " if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", lastQuestion);
            waitForAngular(300);
            return submitAndGetToast(attempt + 1);
        }
        return toast;
    }
}
