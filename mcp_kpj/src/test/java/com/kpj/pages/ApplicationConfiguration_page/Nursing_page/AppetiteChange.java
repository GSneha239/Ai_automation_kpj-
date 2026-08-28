package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Appetite Change</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Appetite Change</b>
 * ({@code #/AppetiteChange}) → enter <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → success toast.</p>
 *
 * <p><b>INLINE-ADD</b> generic commonmaster screen — no Add button, same shape as the Allergy / Advise siblings
 * under this submenu. Fields: {@code commonmaster.Code}, {@code commonmaster.Description} (Remark), plus an
 * already-selected <b>Form Name*</b> and optional MIMS GUID / Description / Type. The form is
 * {@code <form ng-submit="fnIUDCommonMaster()">}.</p>
 *
 * <p>Menu note: the submenu parent is <b>Nursing</b> — matched on EXACT text, because "Nursing Station" is a
 * separate top-level module that a prefix match hits first.</p>
 */
public class AppetiteChange extends BasePage {

    public AppetiteChange(Page page) { super(page); }

    public static final String ROUTE = "#/AppetiteChange";
    public String lastCode = "", lastRemark = "";
    /** href discovered on the menu link — used as the direct-route fallback if the click does not land. */
    private String menuHref = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Remarks that read like genuine configuration rather than machine noise. */
    private static final String[] REMARKS = {
            "Poor Appetite", "Loss of Appetite", "Increased Appetite",
            "Reduced Food Intake", "Normal Appetite", "Fluctuating Appetite"
    };

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /** Grab a full-page screenshot with the toast still showing. Never fails the flow. */
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
            // EXACT "Nursing" — "Nursing Station" is a different module and a prefix match hits it first.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*appetite\\s*change\\s*$/i.test(norm(x.textContent)) || /appetitechange/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__acMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("AppetiteChange.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            if (href.toString().startsWith("#")) menuHref = href.toString();
            try { page.locator("#__acMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("AppetiteChange.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__acMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='commonmaster.Description')",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("AppetiteChange.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            String route = menuHref.isEmpty() ? ROUTE : menuHref;
            System.out.println("AppetiteChange.nav: direct route " + route);
            try { page.evaluate("(h) => { window.location.hash = h.replace(/^#/,''); }", route); } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        return onScreen();
    }

    /**
     * Diagnostic for a wrong-page FAIL: the ng-models actually on screen. Distinguishes "the SPA never routed and
     * a previous screen is still mounted" from "the right screen is up but its fields are not commonmaster.*".
     */
    public String visibleModels() {
        Object r = page.evaluate("() => { const s=new Set();"
                + " document.querySelectorAll('input,select,textarea').forEach(e=>{ if(e.offsetParent===null) return; const m=e.getAttribute('ng-model'); if(m) s.add(m); });"
                + " return [...s].slice(0,25).join(', ') || '(no visible ng-model fields)'; }");
        return r == null ? "" : r.toString();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * On the REAL Appetite Change screen. Checked by the screen's own <b>Remark</b> field
     * ({@code commonmaster.Description}) — NOT just the URL: the SPA can leave a previous screen mounted while the
     * hash already reads the new route, and that leftover markup carries a Code + Remark commonmaster form which a
     * field-only check would happily accept.
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("appetite")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='commonmaster.Description')"));
    }

    // ---- form ------------------------------------------------------------

    /** Enter <b>Code*</b> and <b>Remark*</b> with freshly generated values. */
    public String fillDetails() { return fillDetails(0); }

    /**
     * Enter <b>Code*</b> ({@code commonmaster.Code}) and <b>Remark*</b> ({@code commonmaster.Description}).
     * {@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely different
     * details rather than the same pair again.
     */
    public String fillDetails(int attempt) {
        String code = "AC" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime() / 1000 + attempt) % REMARKS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('commonmaster.Code', a.code); const rm=set('commonmaster.Description', a.remark);"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /**
     * Submit the inline commonmaster form ({@code fnIUDCommonMaster()}) and return the toast. Re-applies
     * Code/Remark to EVERY scope's {@code commonmaster} and invokes the handler from the scope (the inline form
     * re-renders and can blank the Code), retrying — the async save occasionally yields no toast at all.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__acToasts=[]; if(window.__acObs) window.__acObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__acToasts.includes(t)) window.__acToasts.push(t); }); };"
                + " window.__acObs=new MutationObserver(grab); window.__acObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForAngular(500);
            Object res = page.evaluate("(a) => { const A=window.angular; const code=a.code, desc=a.remark;"
                    + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    + " setInp('commonmaster.Code', code); setInp('commonmaster.Description', desc);"
                    + " let handlerName=null; const f=[...document.querySelectorAll('form')].find(x=>x.getAttribute('ng-submit'));"
                    + " if(f){ const m=(f.getAttribute('ng-submit')||'').match(/([A-Za-z_$][\\w$]*)\\s*\\(/); handlerName=m?m[1]:null; }"
                    + " const seen=new Set(); let handlerScope=null;"
                    + " document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id);"
                    + "   if(s.commonmaster && typeof s.commonmaster==='object'){ if(code) s.commonmaster.Code=code; if(desc) s.commonmaster.Description=desc; }"
                    + "   if(handlerName && typeof s[handlerName]==='function' && !handlerScope) handlerScope=s; }catch(e){} });"
                    + " if(handlerScope && handlerName){ try{ handlerScope.$apply(function(){ handlerScope[handlerName](); }); }catch(e){ try{ handlerScope[handlerName](); }catch(e2){} } return 'invoked:'+handlerName; }"
                    + " const b=[...document.querySelectorAll('button[type=submit],input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(b){ b.id='__acSubmit'; return 'click'; } return 'none'; }",
                    java.util.Map.of("code", lastCode, "remark", lastRemark));
            System.out.println("AppetiteChange submit[" + attempt + "] => " + res);
            if ("click".equals(String.valueOf(res))) {
                try { page.locator("#__acSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
                page.evaluate("() => { const e=document.getElementById('__acSubmit'); if(e) e.removeAttribute('id'); }");
            }
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|required|exist|error/i.test(el.textContent||''))"
                        + " || (window.__acToasts||[]).some(a=>/appetite|master|saved|success|added|updated|please|enter|required|exist|error/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__acToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            if (last.toLowerCase().matches(".*(saved|added|success).*")) return last;
            // "Already exists" is the caller's cue to change the details — hand it straight back, no point
            // resubmitting the SAME Code/Remark two more times.
            if (last.toLowerCase().matches(".*(exist|already).*")) return last;
            page.evaluate("() => { window.__acToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
