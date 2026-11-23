package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Diagnosis Complexity Category</b> — configuration screen
 * Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Diagnosis Complexity Category</b>
 * ({@code #/DiagnosisComplexityCategory}) → enter <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → toast.</p>
 *
 * <p>The Nursing masters do not share one ng-model prefix — seen so far: {@code commonmaster.*} (Advise,
 * Allergy, Bifocals), {@code Symptoms.*} (Body Symptoms), {@code complaint.*} (Complaint), {@code item.*}
 * (Fall Risk). Rather than guess this screen's prefix, Code and Remark are located <b>by their labels</b> and the
 * Submit button by its text, so the flow works whatever the models are called. If the screen turns out to have an
 * Add button, {@link #clickAddIfPresent()} opens the form first; it is a no-op on inline-add screens.</p>
 */
public class DiagnosisComplexityCategory extends BasePage {

    public DiagnosisComplexityCategory(Page page) { super(page); }

    public static final String ROUTE = "#/DiagnosisComplexityCategory";
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

    private static final String[] REMARKS = {
            "Simple", "Moderate", "Complex", "Highly Complex", "Multi System"};

    /** JS helper: the visible input whose surrounding label matches a regex. */
    private static final String BY_LABEL =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
          + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
          + " const byLabel=re=>[...document.querySelectorAll('input[type=text],textarea')].find(e=>e.offsetParent!==null && re.test(labelOf(e)));";

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
            // EXACT label / href — "Diagnosis" and "Diagnosis Set" are separate screens in the same submenu.
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*diagnosis\\s*complexity\\s*category\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/DiagnosisComplexityCategory'); if(!a) return ''; a.id='__dcMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("DiagnosisComplexityCategory.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__dcMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("DiagnosisComplexityCategory.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__dcMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => { " + BY_LABEL + " return !!byLabel(/remark/i) || [...document.querySelectorAll('[ng-click]')].some(e=>/^\\s*add\\s*$/i.test((e.textContent||'').trim()) && e.offsetParent!==null); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("DiagnosisComplexityCategory.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
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

    /** On the REAL screen — a Remark field or the screen's Add button is present, not just the right URL. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("diagnosiscomplexitycategory")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL
                + " return !!byLabel(/remark/i) || [...document.querySelectorAll('[ng-click]')].some(e=>/^\\s*add\\s*$/i.test((e.textContent||'').trim()) && e.offsetParent!==null); }"));
    }

    /** Click <b>Add</b> if this screen has one (no-op on inline-add screens). */
    public String clickAddIfPresent() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return ''; b.id='__dcAdd'; return b.getAttribute('ng-click')||'add'; }");
        String how = tagged == null ? "" : tagged.toString();
        if (how.isEmpty()) return "(inline-add — no Add button)";
        try { page.locator("#__dcAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dcAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2500);
        return "Add clicked {" + how + "}";
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> and <b>Remark*</b>, located BY LABEL (this screen's ng-model prefix is not assumed).
     * {@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely
     * different details; once the small realistic-name pool is exhausted a short number is appended so the
     * retry is still guaranteed unique.
     */
    public String fillDetails(int attempt) {
        String code = "DC" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        if (attempt >= REMARKS.length) lastRemark = lastRemark + " " + (Math.abs(System.nanoTime()) % 10000);
        Object r = page.evaluate("(a) => { const A=window.angular; " + BY_LABEL
                + " const fill=(e,v)=>{ if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v+' [ng='+(e.getAttribute('ng-model')||'?')+']'; };"
                + " const cd=fill(byLabel(/^code/i), a.code); const rm=fill(byLabel(/remark/i), a.remark);"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> (matched by text — the handler name differs per screen) and return the toast. */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__dcToasts=[]; if(window.__dcObs) window.__dcObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dcToasts.includes(t)) window.__dcToasts.push(t); }); };"
                + " window.__dcObs=new MutationObserver(grab); window.__dcObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__dcSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__dcSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dcSubmit'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                    + " || (window.__dcToasts||[]).some(a=>/diagnosis|complexity|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__dcToasts||[]; return a.find(x=>/(diagnosis|complexity|record|master|category).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
