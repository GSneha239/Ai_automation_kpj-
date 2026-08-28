package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Patient Category</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Patient Category</b>
 * ({@code #/CaseCategory}) → enter <b>Code*</b> ({@code CaseCategory.Code}) + <b>Remark*</b>
 * ({@code CaseCategory.Description}) → <b>Submit</b> ({@code fnIUDCaseCategory()}) → success toast.</p>
 *
 * <p><b>INLINE-ADD</b> screen — no Add button; the form ({@code <form name="CaseCategoryForm">}) sits above the
 * grid. There is also an optional <b>Pricing Policy</b> dropdown ({@code CaseCategory.tariffid}, ~209 options)
 * which is NOT marked mandatory and is left alone.</p>
 *
 * <p>Menu note: the label is <b>"Patient Category"</b> but the route is {@code #/CaseCategory} — the sibling
 * <b>CaseType</b> ({@code #/CaseType}) is a different screen, so match the link text exactly or the exact href.</p>
 */
public class PatientCategory extends BasePage {

    public PatientCategory(Page page) { super(page); }

    public static final String ROUTE = "#/CaseCategory";
    public String lastCode = "", lastRemark = "";
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
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*patient\\s*category\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/CaseCategory'); if(!a) return ''; a.id='__pcMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("PatientCategory.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__pcMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("PatientCategory.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__pcMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='CaseCategory.Description')",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("PatientCategory.nav: Patient Category screen not confirmed (attempt " + (attempt + 1) + ")"); }
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

    /** On the REAL Patient Category screen — verified by its own Remark field, not just the URL. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("casecategory")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='CaseCategory.Description')"));
    }

    // ---- form ------------------------------------------------------------

    /** Realistic category names — the Remark is what a user reads in the grid, so it holds a real name rather
     *  than a generated "Auto …" string. */
    private static final String[] NAMES = {
            "Corporate Client", "Senior Citizen", "Staff Dependent", "Government Servant", "Insurance Patient",
            "Walk In Patient", "Company Panel", "Student Patient", "Retired Personnel", "Diplomatic Corps"};

    /** Enter <b>Code*</b> ({@code CaseCategory.Code}) and <b>Remark*</b> ({@code CaseCategory.Description}). */
    public String fillDetails() {
        String code = "PC" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastRemark = NAMES[(int) (Math.abs(System.nanoTime()) % NAMES.length)];
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('CaseCategory.Code', a.code); const rm=set('CaseCategory.Description', a.remark);"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Submit</b> ({@code fnIUDCaseCategory}) and return the toast; screenshots it while still on screen.
     * On "… already exists!" a fresh Code/Remark is generated and Submit is tried again (up to 3 attempts).
     */
    public String submitAndGetToast() { return submitAndGetToast(0); }

    private String submitAndGetToast(int attempt) {
        Object tagged = page.evaluate("() => { window.__pcToasts=[]; if(window.__pcObs) window.__pcObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__pcToasts.includes(t)) window.__pcToasts.push(t); }); };"
                + " window.__pcObs=new MutationObserver(grab); window.__pcObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDCaseCategory/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__pcSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__pcSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pcSubmit'); if(e) e.removeAttribute('id'); }");
        // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN.
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                    + " || (window.__pcToasts||[]).some(a=>/category|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__pcToasts||[]; return a.find(x=>/(category|record|master).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        String toast = r == null ? "" : r.toString().trim();
        if (attempt < 2 && toast.toLowerCase().matches(".*(exist|already).*")) {
            System.out.println("PatientCategory.submitAndGetToast: \"" + toast + "\" — regenerating the details and retrying");
            fillDetails();
            // The category-name pool is small and this environment may already hold every entry, so a repeat
            // pick collides again — append a short number so the retry is genuinely unique.
            lastRemark = lastRemark + " " + (Math.abs(System.nanoTime()) % 10000);
            // fillDetails() has already written the un-suffixed name to CaseCategory.Description; push the
            // suffixed value back into the DOM so the retried Submit actually sends the unique value.
            page.evaluate("(v) => { const A=window.angular;"
                    + " const e=[...document.querySelectorAll(\"input[ng-model='CaseCategory.Description'],textarea[ng-model='CaseCategory.Description']\")].find(x=>x.offsetParent!==null);"
                    + " if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", lastRemark);
            waitForAngular(300);
            return submitAndGetToast(attempt + 1);
        }
        return toast;
    }
}
