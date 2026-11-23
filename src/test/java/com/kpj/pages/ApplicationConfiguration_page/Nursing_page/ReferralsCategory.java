package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>ReferralsCategory</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>ReferralsCategory</b>
 * ({@code #/ReferralsCategory}) → (<b>Add</b>, if this screen has one) → enter <b>Code*</b> + <b>Remark*</b> →
 * <b>Submit</b> → success toast.</p>
 *
 * <p>The Nursing masters share no ng-model prefix ({@code commonmaster.*}, {@code Symptoms.*}, {@code item.*} …),
 * so Code and Remark are located <b>by their labels</b> and the discovered ng-model is echoed back in the step
 * text. {@link #clickAddIfPresent()} opens the entry form on list-style screens and is a no-op on inline-add ones.</p>
 *
 * <p><b>Duplicates:</b> the Code carries a generated unique value, but the Remark comes from a realistic pool that
 * may already exist. On "Code/Description already exists" the submit retries with the NEXT name from the pool.
 * If every name in the pool is taken, it stops and says so — it will NOT invent a junk name just to force a save,
 * because a duplicate-looking referral category is worse than a reported gap.</p>
 */
public class ReferralsCategory extends BasePage {

    public ReferralsCategory(Page page) { super(page); }

    public static final String ROUTE = "#/ReferralsCategory";
    public String lastCode = "", lastRemark = "";
    /** True when every realistic name in the pool already exists — nothing new could be created. */
    public boolean allNamesTaken = false;
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    private static final int TOAST_SHOT_WAIT_MS = 5000;

    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    /** Realistic referral categories — never "Auto referral <CODE>". */
    private static final String[] REMARKS = {
            "Specialist Referral", "Dietitian Referral", "Physiotherapy Referral", "Social Welfare Referral",
            "Psychology Referral", "Occupational Therapy Referral", "Speech Therapy Referral",
            "Palliative Care Referral", "Wound Care Referral", "Diabetic Educator Referral"};

    /** JS helper: the visible input/textarea whose surrounding label matches a regex (grid filters excluded). */
    private static final String BY_LABEL =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
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
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__rcMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*referrals?\\s*category\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/ReferralsCategory'); if(!a) return ''; a.id='__rcMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("ReferralsCategory.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__rcMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__rcMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__rcMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => { " + BY_LABEL + " return !!byLabel(/remark/i) || [...document.querySelectorAll('[ng-click],button,a')].some(e=>/^\\s*add\\s*$/i.test((e.textContent||'').trim()) && e.offsetParent!==null); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(25000));
            } catch (Exception ignore) { System.out.println("ReferralsCategory.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
            waitForAngular(1500);
        }
        return onScreen();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,45); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /**
     * On the REAL ReferralsCategory screen — verified by a Remark field or the screen's own Add button, NOT just
     * the URL: the SPA can hold the right hash while leaving a previous screen mounted, and that leftover markup
     * carries a Code + Store commonmaster form which a Code-only check would happily accept.
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("referralscategory")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL
                + " return !!byLabel(/remark/i) || [...document.querySelectorAll('[ng-click],button,a')].some(e=>/^\\s*add\\s*$/i.test((e.textContent||'').trim()) && e.offsetParent!==null); }"));
    }

    /** Click <b>Add</b> if this screen has one (no-op on inline-add screens). */
    public String clickAddIfPresent() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__rcAdd').forEach(e=>e.removeAttribute('id'));"
                + " const b=[...document.querySelectorAll('[ng-click],button,a')].find(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return ''; b.id='__rcAdd'; return b.getAttribute('ng-click')||'add'; }");
        String how = tagged == null ? "" : tagged.toString();
        if (how.isEmpty()) return "(inline-add - no Add button)";
        try { page.locator("#__rcAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickAddIfPresent: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__rcAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2500);
        return "Add clicked {" + how + "}";
    }

    /** True once an entry form with a Remark field is up. */
    public boolean onEntryForm() {
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL + " return !!byLabel(/remark/i); }"));
    }

    /** Every visible field with its label + ng-model — so a fill failure shows what the screen offered. */
    public String describeForm() {
        Object r = page.evaluate("() => { " + BY_LABEL
                + " const f=flds().map(e=>norm(labelOf(e))+' [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const btns=[...document.querySelectorAll('button,a[ng-click]')].filter(e=>e.offsetParent!==null).map(b=>norm(b.textContent)+'{'+(b.getAttribute('ng-click')||'-')+'}').filter(s=>s.length<60);"
                + " return 'fields: '+f.join(' ; ')+' || buttons: '+btns.join(' ; '); }");
        return r == null ? "" : r.toString();
    }

    // ---- form ------------------------------------------------------------

    /** Enter <b>Code*</b> and <b>Remark*</b>, located BY LABEL (this screen's ng-model prefix is not assumed). */
    public String fillDetails() {
        return fillDetails((int) (Math.abs(System.nanoTime()) % REMARKS.length));
    }

    /** Fill with a SPECIFIC name from the pool (used when retrying after "already exists"). Once every pool
     *  entry has been tried once, a short numeric suffix keeps the name realistic-looking while guaranteeing a
     *  genuinely new value, so retries do not just cycle back to an already-rejected exact string. */
    private String fillDetails(int remarkIdx) {
        lastCode = "RC" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        int cycle = remarkIdx / REMARKS.length;
        lastRemark = REMARKS[remarkIdx % REMARKS.length] + (cycle > 0 ? " " + (cycle + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular; " + BY_LABEL
                + " const fill=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v+' [ng='+(e.getAttribute('ng-model')||'?')+']'; };"
                + " const cd=fill(byLabel(/^code/i), a.code); const rm=fill(byLabel(/remark/i), a.remark);"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", lastCode, "remark", lastRemark));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /**
     * Submit, and on <b>"Code/Description already exists"</b> retry with the NEXT realistic name from the pool —
     * keep retrying until the success toast, not just until the small pool is exhausted once. Past the first
     * pass through the pool, {@link #fillDetails(int)} appends a short numeric suffix so later attempts stay
     * realistic-looking without repeating an exact value that was already rejected.
     */
    public String submitWithRetries() {
        int start = (int) (Math.abs(System.nanoTime()) % REMARKS.length);
        String toast = "";
        for (int i = 0; i < 40; i++) {
            if (i > 0) {
                System.out.println("submitWithRetries: \"" + lastRemark + "\" exists — retrying with the next name");
                fillDetails(start + i);
            }
            toast = submitAndGetToast();
            if (toast == null) toast = "";
            String t = toast.toLowerCase();
            boolean exists = t.contains("already exist") || t.contains("already exists");
            if (!exists) return toast;
        }
        allNamesTaken = true;
        System.out.println("submitWithRetries: exhausted 40 attempts — every generated name still collided");
        return toast;
    }

    /** Click <b>Submit</b> (matched by text — the handler name differs per screen) and return the toast. */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__rcToasts=[]; if(window.__rcObs) window.__rcObs.disconnect();"
                // Stop toastr auto-dismissing so the message is still fully opaque when we screenshot it, and
                // clear anything already on screen so a stale toast is not read as this submit's result.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message, .toast').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rcToasts.includes(t)) window.__rcToasts.push(t); }); };"
                + " window.__rcObs=new MutationObserver(grab); window.__rcObs.observe(document.body,{childList:true,subtree:true}); }");
        Object tagged = page.evaluate("() => { document.querySelectorAll('#__rcSubmit').forEach(e=>e.removeAttribute('id'));"
                + " const b=[...document.querySelectorAll('button,input[type=submit],a')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return ''; b.id='__rcSubmit'; return b.getAttribute('ng-click')||'submit'; }");
        String how = tagged == null ? "" : tagged.toString();
        if (how.isEmpty()) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        System.out.println("ReferralsCategory submit => " + how);
        try { page.locator("#__rcSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__rcSubmit'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__rcToasts||[]).some(a=>/saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__rcToasts||[]; return a.find(x=>/saved|added|success|updated|already|exist/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
