package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Patient Vital</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Patient Vital</b>
 * ({@code #/PatientVitalMaster}) → <b>Add</b> ({@code AddPatientVital()}) → enter <b>Code</b>, <b>Remark</b>,
 * <b>Default Value</b>, <b>Min Value</b> and <b>Max Value</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>The entry form's ng-models are not assumed (the Nursing masters share no prefix): each field is located
 * <b>by its label</b> — {@code /^code/}, {@code /remark/}, {@code /default/}, {@code /min/}, {@code /max/}, which
 * are unambiguous here — and the discovered ng-model of each is echoed back in the step text.
 * {@link #describeForm()} dumps what the screen really offered so a fill failure is diagnosable in one run.</p>
 */
public class PatientVital extends BasePage {

    public PatientVital(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/PatientVitalMaster";
    public String lastCode = "", lastRemark = "", lastDefault = "", lastMin = "", lastMax = "";
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
            "Systolic Blood Pressure", "Diastolic Blood Pressure", "Pulse Rate", "Respiratory Rate",
            "Body Temperature", "Oxygen Saturation"};

    /** JS helper: the visible input/textarea whose surrounding label matches a regex (grid filters excluded). */
    private static final String BY_LABEL =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
          + "   for(let i=0;i<6&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
          + " const flds=()=>[...document.querySelectorAll('input[type=text],input[type=number],input:not([type]),textarea')].filter(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'').indexOf('colFilter')<0 && (e.getAttribute('ng-model')||'').indexOf('grid.')<0);"
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
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__pvMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*patient\\s*vital(\\s*master)?\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/PatientVitalMaster'); if(!a) return ''; a.id='__pvMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("PatientVital.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__pvMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__pvMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__pvMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddPatientVital\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("PatientVital.nav: list not confirmed (attempt " + (attempt + 1) + ")"); }
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

    /** On the REAL Patient Vital list — verified by its own Add button ({@code AddPatientVital}), not just the URL. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("patientvital")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddPatientVital\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"));
    }

    /**
     * Real-click <b>Add</b> ({@code AddPatientVital}) and wait for the entry form. Polls for the button (it renders
     * after the grid) and retries the click — a single early click on these Nursing lists has been seen doing
     * nothing at all.
     */
    public boolean clickAdd() {
        for (int round = 0; round < 3 && !onAddForm(); round++) {
            boolean tagged = false;
            for (int i = 0; i < 25 && !tagged; i++) {
                tagged = Boolean.TRUE.equals(page.evaluate("() => { document.querySelectorAll('#__pvAdd').forEach(e=>e.removeAttribute('id')); const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddPatientVital\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__pvAdd'; return true; }"));
                if (!tagged) page.waitForTimeout(500);
            }
            if (!tagged) { System.out.println("PatientVital.clickAdd: Add button not found (round " + (round + 1) + ")"); return false; }
            try { page.locator("#__pvAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("PatientVital.clickAdd: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__pvAdd'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => { " + BY_LABEL + " return !!byLabel(/^code/i); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(12000));
            } catch (Exception ignore) { System.out.println("PatientVital.clickAdd: entry form did not render (round " + (round + 1) + ")"); }
            waitForAngular(1200);
        }
        return onAddForm();
    }

    /** True once an entry form with a Code field is up (route or modal). */
    public boolean onAddForm() {
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL + " return !!byLabel(/^code/i); }"));
    }

    /** Every visible form field with its label + ng-model — so a failure shows what the screen actually offered. */
    public String describeForm() {
        Object r = page.evaluate("() => { " + BY_LABEL
                + " const f=flds().map(e=>norm(labelOf(e))+' [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'').indexOf('grid.')<0).map(e=>'SELECT '+norm(labelOf(e))+' [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const btns=[...document.querySelectorAll('button,a[ng-click]')].filter(e=>e.offsetParent!==null).map(b=>norm(b.textContent)+'{'+(b.getAttribute('ng-click')||'-')+'}').filter(s=>s.length<60);"
                + " return 'fields: '+f.concat(sels).join(' ; ')+' || buttons: '+btns.join(' ; '); }");
        return r == null ? "" : r.toString();
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code</b>, <b>Remark</b>, <b>Default Value</b>, <b>Min Value</b> and <b>Max Value</b>, located BY
     * LABEL. The three numeric values are kept consistent (min &lt; default &lt; max) so the screen's own range
     * validation cannot reject the save for a reason unrelated to the test. {@code attempt} shifts Code and Remark
     * so a retry after an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "PV" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        // min < default < max, so a range check can't fail the save for an unrelated reason.
        int min = 10 + (int) (Math.abs(System.nanoTime() / 7) % 20);
        lastMin = String.valueOf(min);
        lastDefault = String.valueOf(min + 20);
        lastMax = String.valueOf(min + 60);
        Object r = page.evaluate("(a) => { const A=window.angular; " + BY_LABEL
                + " const fill=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v+' [ng='+(e.getAttribute('ng-model')||'?')+']'; };"
                + " const cd=fill(byLabel(/^code/i), a.code);"
                + " const rm=fill(byLabel(/remark/i), a.remark);"
                + " const df=fill(byLabel(/default/i), a.def);"
                + " const mn=fill(byLabel(/min/i), a.min);"
                + " const mx=fill(byLabel(/max/i), a.max);"
                + " return 'Code='+cd+' | Remark='+rm+' | Default='+df+' | Min='+mn+' | Max='+mx; }",
                java.util.Map.of("code", code, "remark", lastRemark, "def", lastDefault, "min", lastMin, "max", lastMax));
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> (matched by text — the handler name differs per screen) and return the toast. */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__pvToasts=[]; if(window.__pvObs) window.__pvObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__pvToasts.includes(t)) window.__pvToasts.push(t); }); };"
                + " window.__pvObs=new MutationObserver(grab); window.__pvObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForAngular(500);
            Object tagged = page.evaluate("() => { document.querySelectorAll('#__pvSubmit').forEach(e=>e.removeAttribute('id')); const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return ''; b.id='__pvSubmit'; return b.getAttribute('ng-click')||'submit'; }");
            String how = tagged == null ? "" : tagged.toString();
            System.out.println("PatientVital submit[" + attempt + "] => " + (how.isEmpty() ? "(no Submit button)" : how));
            if (how.isEmpty()) return last;
            try { page.locator("#__pvSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
            page.evaluate("() => { const e=document.getElementById('__pvSubmit'); if(e) e.removeAttribute('id'); }");
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(el.textContent||''))"
                        + " || (window.__pvToasts||[]).some(a=>/vital|saved|success|added|updated|please|enter|select|required|exist|error|not found/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__pvToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            // "saved" ends the flow successfully; "already exists" is NOT retryable with the SAME details —
            // return it immediately rather than blindly resubmitting stale data, so the caller can regenerate
            // fresh details (fillDetails) and try again.
            if (last.toLowerCase().matches(".*(saved|added|success|exist|already).*")) return last;
            page.evaluate("() => { window.__pvToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
