package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Prescription Type</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Prescription Type</b>
 * ({@code #/Prescription Type}) → enter <b>Code*</b> + <b>Remark*</b> → <b>Submit</b> → success toast.
 * INLINE-ADD — no Add button; the form sits on the screen.</p>
 *
 * <p><b>The route contains a literal space</b> — {@code #/Prescription Type}, which the browser shows
 * percent-encoded as {@code #/Prescription%20Type} — so the URL check accepts either form.</p>
 *
 * <p><b>Known site defect (observed 2026-08-04):</b> this route does not render its own screen. The hash changes
 * correctly, but the header still reads <b>"Transfer"</b> and the body is the leftover generic
 * {@code myCommonMasterForm} carrying <b>Code* + Store*</b> ({@code commonmaster.Code} + {@code StoreId}, the Store
 * list holding only {@code --Select--}) — with <b>no Remark field</b>. That Code+Store pair is the known
 * wrong-page signature on this app, so {@link #onScreen()} demands this screen's own <b>Remark</b> field rather
 * than trusting the URL: filling the leftover markup would otherwise "pass" three steps and then fail at Submit
 * for an unexplained reason.</p>
 */
public class PrescriptionType extends BasePage {

    public PrescriptionType(Page page) { super(page); }

    public static final String ROUTE = "#/Prescription Type";
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
            "Outpatient Prescription", "Discharge Prescription", "Take Home Medication",
            "Emergency Prescription", "Repeat Prescription"};

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
            // EXACT "Nursing" — "Nursing Station" is a different module and a prefix match hits it first.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__ptMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*prescription\\s*type\\s*$/i.test(norm(x.textContent)) || /prescription(%20|\\s)type/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__ptMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("PrescriptionType.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__ptMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__ptMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__ptMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => { " + BY_LABEL + " return !!byLabel(/remark/i); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("PrescriptionType.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
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
     * On the REAL Prescription Type screen — verified by its own <b>Remark</b> field, NOT the URL. The app routes
     * the hash but leaves the previous screen mounted, and the leftover generic commonmaster markup has a Code
     * field that a Code-only check would happily accept (see the class note).
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        if (!(u.contains("prescription%20type") || u.contains("prescription type"))) return false;
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

    /**
     * Enter <b>Code*</b> and <b>Remark*</b>, located BY LABEL (this screen's ng-model prefix is not assumed).
     * {@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely different
     * details.
     */
    public String fillDetails(int attempt) {
        String code = "PT" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
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
        page.evaluate("() => { window.__ptToasts=[]; if(window.__ptObs) window.__ptObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ptToasts.includes(t)) window.__ptToasts.push(t); }); };"
                + " window.__ptObs=new MutationObserver(grab); window.__ptObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForAngular(500);
            Object tagged = page.evaluate("() => { document.querySelectorAll('#__ptSubmit').forEach(e=>e.removeAttribute('id')); const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return ''; b.id='__ptSubmit'; return b.getAttribute('ng-click')||'submit'; }");
            String how = tagged == null ? "" : tagged.toString();
            System.out.println("PrescriptionType submit[" + attempt + "] => " + (how.isEmpty() ? "(no Submit button)" : how));
            if (how.isEmpty()) return last;
            try { page.locator("#__ptSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
            page.evaluate("() => { const e=document.getElementById('__ptSubmit'); if(e) e.removeAttribute('id'); }");
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(el.textContent||''))"
                        + " || (window.__ptToasts||[]).some(a=>/prescription|saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__ptToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            // "saved" ends the flow successfully; "already exists" is NOT retryable with the SAME Code/Remark —
            // return it immediately rather than blindly resubmitting stale data, so the caller can regenerate
            // fresh details (fillDetails) and try again.
            if (last.toLowerCase().matches(".*(saved|added|success|exist|already).*")) return last;
            page.evaluate("() => { window.__ptToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
