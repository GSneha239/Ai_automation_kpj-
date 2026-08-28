package com.kpj.pages.ApplicationConfiguration_page.MRD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; MRD &gt; <b>Allocation Location Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>MRD</b> (submenu) → <b>Allocation Location Master</b>
 * ({@code #/AllocationLocationMaster}) → enter <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → success toast.
 * INLINE-ADD — no Add button; the form sits on the screen.</p>
 *
 * <p><b>Known site defect (observed 2026-08-04):</b> this route does not render its own screen. The hash changes
 * correctly, but the header still reads <b>"Transfer"</b> and the body is the leftover generic
 * {@code myCommonMasterForm} carrying <b>Code* + Store*</b> ({@code commonmaster.Code} + {@code StoreId}, the Store
 * list holding only {@code --Select--}) — with <b>no Remark field</b>. That Code+Store pair is the known
 * wrong-page signature on this app, so {@link #onScreen()} demands this screen's own <b>Remark</b> field rather
 * than trusting the URL: filling the leftover markup would otherwise "pass" the earlier steps and then fail at
 * Submit for an unexplained reason.</p>
 */
public class AllocationLocationMaster extends BasePage {

    public AllocationLocationMaster(Page page) { super(page); }

    public static final String ROUTE = "#/AllocationLocationMaster";
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
            "Medical Records Store Room", "Active Files Shelf", "Archive Basement Store",
            "Ward Level Allocation Point", "Off Site Records Warehouse"};

    /** JS helper: the visible input/textarea whose surrounding label matches a regex (grid filters excluded). */
    private static final String BY_LABEL =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
          + "   for(let i=0;i<6&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
          + " const flds=()=>[...document.querySelectorAll('input[type=text],input:not([type]),textarea')].filter(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'').indexOf('colFilter')<0 && (e.getAttribute('ng-model')||'').indexOf('grid.')<0);"
          + " const byLabel=re=>flds().find(e=>re.test(labelOf(e)));";

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
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__alMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*allocation\\s*location\\s*master\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/AllocationLocationMaster'); if(!a) return ''; a.id='__alMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("AllocationLocationMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__alMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__alMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__alMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => { " + BY_LABEL + " return !!byLabel(/remark/i); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("AllocationLocationMaster.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
            if (!onScreen()) navigateDirect();
        }
        return onScreen();
    }

    /**
     * HARD navigation: load {@code BASE + ROUTE} as a real browser navigation (not an in-app menu click).
     *
     * <p>This screen renders perfectly when the URL is opened directly, but an in-app SPA transition from a
     * previously-mounted commonmaster screen can leave the OLD view in the DOM — the hash reads
     * {@code #/AllocationLocationMaster} while the body still shows the previous screen's Code + Store form. A full
     * page load re-bootstraps Angular and always brings up the right view, so it is used as the fallback whenever
     * the menu route does not confirm.</p>
     */
    public boolean navigateDirect() {
        String url = com.kpj.core.DevHisBase.BASE + "/" + ROUTE;
        System.out.println("AllocationLocationMaster.navigateDirect: " + url);
        try { page.navigate(url, new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED).setTimeout(45000)); }
        catch (Exception e) { System.out.println("navigateDirect: navigate failed - " + e.getMessage()); }
        // A navigate() to a URL that differs only in the HASH does not reload the document — Angular never
        // re-bootstraps and the previous view stays mounted. Force a real load so the route is resolved from
        // scratch, exactly as opening the URL in a fresh tab does.
        try { page.reload(new Page.ReloadOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED).setTimeout(45000)); }
        catch (Exception e) { System.out.println("navigateDirect: reload failed - " + e.getMessage()); }
        waitForAngular(2500);
        try {
            page.waitForFunction("() => { " + BY_LABEL + " return !!byLabel(/remark/i); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("navigateDirect: screen not confirmed after a direct load"); }
        waitForAngular(1500);
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
     * On the REAL Allocation Location Master screen — verified by its own <b>Remark</b> field, NOT the URL. The app
     * routes the hash but leaves the previous screen mounted, and the leftover generic commonmaster markup has a
     * Code field that a Code-only check would happily accept (see the class note).
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("allocationlocation")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL + " return !!byLabel(/remark/i); }"));
    }

    /** The wrong-page signature: a generic commonmaster <b>Code* + Store*</b> form and no Remark. */
    public boolean showsCodeStoreLeftover() {
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL
                + " const hasStore=[...document.querySelectorAll('select')].some(s=>s.offsetParent!==null && (s.getAttribute('ng-model')||'')==='StoreId');"
                + " return hasStore && !byLabel(/remark/i); }"));
    }

    /** Every visible form field with its label + ng-model — evidence of what the app actually served. */
    public String describeForm() {
        Object r = page.evaluate("() => { " + BY_LABEL
                + " const f=flds().map(e=>norm(labelOf(e))+' [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'').indexOf('grid.')<0).map(e=>'SELECT '+norm(labelOf(e))+' [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const forms=[...document.querySelectorAll('form[name]')].map(x=>x.getAttribute('name')+' ng-submit='+(x.getAttribute('ng-submit')||'-'));"
                + " return 'forms: '+forms.join(' ; ')+' || fields: '+f.concat(sels).join(' ; '); }");
        return r == null ? "" : r.toString();
    }

    // ---- form ------------------------------------------------------------

    /** Enter <b>Code*</b> and <b>Remark*</b>, located BY LABEL (this screen's ng-model prefix is not assumed). */
    public String fillDetails() {
        String code = "AL" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime()) % REMARKS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular; " + BY_LABEL
                + " const fill=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v+' [ng='+(e.getAttribute('ng-model')||'?')+']'; };"
                + " const cd=fill(byLabel(/^code/i), a.code); const rm=fill(byLabel(/remark/i), a.remark);"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> (matched by text — the handler name differs per screen) and return the toast. */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__alToasts=[]; if(window.__alObs) window.__alObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__alToasts.includes(t)) window.__alToasts.push(t); }); };"
                + " window.__alObs=new MutationObserver(grab); window.__alObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 40; attempt++) {
            waitForAngular(500);
            Object tagged = page.evaluate("() => { document.querySelectorAll('#__alSubmit').forEach(e=>e.removeAttribute('id')); const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return ''; b.id='__alSubmit'; return b.getAttribute('ng-click')||'submit'; }");
            String how = tagged == null ? "" : tagged.toString();
            System.out.println("AllocationLocationMaster submit[" + attempt + "] => " + (how.isEmpty() ? "(no Submit button)" : how));
            if (how.isEmpty()) return last;
            try { page.locator("#__alSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
            page.evaluate("() => { const e=document.getElementById('__alSubmit'); if(e) e.removeAttribute('id'); }");
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(el.textContent||''))"
                        + " || (window.__alToasts||[]).some(a=>/allocation|location|saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__alToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            if (last.toLowerCase().matches(".*(saved|added|success).*")) return last;
            // "… already exists!" is NOT retryable with the SAME values — a blind retry would just resubmit the
            // identical Code/Remark and get the same rejection every attempt. Regenerate the details (a fresh
            // Code plus a different Remark) and push them into the DOM before retrying.
            if (last.toLowerCase().matches(".*(exist|already).*")) {
                System.out.println("submitAndGetToast: \"" + last + "\" — regenerating the details and retrying");
                fillDetails();
                // The realistic-name pool is SMALL and can already be exhausted in this environment, so picking
                // another name can still collide — append a short number so the retry is genuinely unique, while
                // still reading like configuration ("Peanut 4821", not machine noise). The FIRST attempt keeps
                // the clean name; fillDetails() already wrote the un-suffixed value, so re-push the suffixed one.
                lastRemark = lastRemark + " " + (Math.abs(System.nanoTime()) % 10000);
                page.evaluate("(v) => { const A=window.angular; " + BY_LABEL
                        + " const e=byLabel(/remark/i); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                        lastRemark);
            }
            page.evaluate("() => { window.__alToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
