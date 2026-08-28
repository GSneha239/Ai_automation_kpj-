package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Intake Output Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Intake Output Master</b>
 * ({@code #/IntakeOutput}) → <b>Add</b> ({@code addIntakeOutput()}) → enter <b>Code</b>, <b>Remark</b> and
 * <b>Intake Output Unit</b>, select <b>Intake</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>This screen's entry-form ng-models are not known up front (the Nursing masters do not share one prefix), so
 * the fields are located <b>by their labels</b> and the discovered ng-model of each is echoed back in the step
 * text. The Intake selector is matched as a radio/checkbox whose adjacent text reads "Intake" (falling back to a
 * select option), and the Submit button by its text.</p>
 *
 * <p><b>Known instability (observed 2026-08-04):</b> the list renders inconsistently — sometimes with its real
 * "Intake Output Master" header and Add button, sometimes under a stale "Transfer" header with no grid at all, and
 * one Add click routed the app to {@code #/PatientDashboard} instead of opening the form. Navigation and Add are
 * therefore retried, and {@link #currentScreen()} reports what actually rendered so a failure names the screen the
 * app served rather than blaming the automation.</p>
 */
public class IntakeOutputMaster extends BasePage {

    public IntakeOutputMaster(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/IntakeOutput";
    public String lastCode = "", lastRemark = "", lastUnit = "", lastIntake = "";
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
            "Oral Fluid Intake", "Intravenous Fluid", "Nasogastric Feed", "Urine Output", "Drain Output"};
    private static final String[] UNITS = {"ml", "litre", "cc", "ounce"};

    /** JS helper: the visible input/textarea whose surrounding label matches a regex. */
    private static final String BY_LABEL =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
          + "   for(let i=0;i<6&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
          + " const byLabel=re=>[...document.querySelectorAll('input[type=text],input:not([type]),textarea')].find(e=>e.offsetParent!==null && re.test(labelOf(e)) && (e.getAttribute('ng-model')||'').indexOf('colFilter')<0);";

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
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__ioMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*intake\\s*output(\\s*master)?\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/IntakeOutput'); if(!a) return ''; a.id='__ioMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("IntakeOutputMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__ioMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__ioMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__ioMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/addIntakeOutput\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("IntakeOutputMaster.nav: list not confirmed (attempt " + (attempt + 1) + ")"); }
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
     * On the REAL Intake Output Master list — verified by its own Add button ({@code addIntakeOutput}), not just the
     * URL: this screen has been seen holding the {@code #/IntakeOutput} hash while rendering a stale "Transfer"
     * header and no grid at all.
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("intakeoutput")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/addIntakeOutput\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"));
    }

    /** Real-click <b>Add</b> ({@code addIntakeOutput}) and wait for an entry form (route OR modal). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/addIntakeOutput\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__ioAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("IntakeOutputMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__ioAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("IntakeOutputMaster.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ioAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => { " + BY_LABEL + " return !!byLabel(/^code/i); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("IntakeOutputMaster.clickAdd: entry form did not render"); }
        waitForAngular(1200);
        return onAddForm();
    }

    /** True once an entry form with a Code field is up (route or modal). */
    public boolean onAddForm() {
        return Boolean.TRUE.equals(page.evaluate("() => { " + BY_LABEL + " return !!byLabel(/^code/i); }"));
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code</b>, <b>Remark</b> and <b>Intake Output Unit</b> (all located BY LABEL — this screen's ng-model
     * prefix is not assumed) and select <b>Intake</b>: a radio/checkbox whose adjacent text reads "Intake" is
     * real-clicked so its ng-change fires, else an "Intake" option is chosen in a select.
     */
    public String fillDetails() {
        String code = "IO" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime()) % REMARKS.length)];
        lastUnit = UNITS[(int) (Math.abs(System.nanoTime() / 7) % UNITS.length)];
        Object r = page.evaluate("(a) => { const A=window.angular; " + BY_LABEL
                + " const fill=(e,v)=>{ if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v+' [ng='+(e.getAttribute('ng-model')||'?')+']'; };"
                + " const cd=fill(byLabel(/^code/i), a.code);"
                + " const rm=fill(byLabel(/remark/i), a.remark);"
                + " const un=fill(byLabel(/unit/i), a.unit);"
                // "Intake": a radio/checkbox whose own label text says Intake (but not "Intake Output ..."), else a select option.
                + " let ik='(not found)';"
                + " const boxes=[...document.querySelectorAll('input[type=radio],input[type=checkbox]')].filter(x=>x.offsetParent!==null);"
                + " const near=e=>norm(((e.closest('label')||e.parentElement||{}).textContent)||'');"
                + " const hit=boxes.find(x=>/^intake$/i.test(near(x))) || boxes.find(x=>/\\bintake\\b/i.test(near(x)) && !/output/i.test(near(x)));"
                + " if(hit){ hit.id='__ioIntake'; ik='radio:\"'+near(hit)+'\" [ng='+(hit.getAttribute('ng-model')||'?')+']'; }"
                + " else { const sel=[...document.querySelectorAll('select')].find(s=>s.offsetParent!==null && [...s.options].some(o=>/^intake$/i.test(norm(o.textContent))));"
                + "   if(sel){ const i=[...sel.options].findIndex(o=>/^intake$/i.test(norm(sel.options[[...sel.options].indexOf(o)].textContent)));"
                + "     sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(sel).triggerHandler('change');}catch(x){}"
                + "     ik='select:Intake [ng='+(sel.getAttribute('ng-model')||'?')+']'; } }"
                + " return 'Code='+cd+' | Remark='+rm+' | Unit='+un+' | Intake='+ik; }",
                java.util.Map.of("code", code, "remark", lastRemark, "unit", lastUnit));
        // The Intake radio needs a REAL click so its ng-click/ng-change actually fires.
        if (Boolean.TRUE.equals(page.evaluate("() => !!document.getElementById('__ioIntake')"))) {
            try { page.locator("#__ioIntake").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("fillDetails: Intake real click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__ioIntake'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(600);
        String s = r == null ? "" : r.toString();
        int i = s.indexOf("Intake=");
        if (i >= 0) lastIntake = s.substring(i + 7).trim();
        return s;
    }

    /**
     * Did the record actually persist? Re-opens the list and looks for {@code code} in the grid's own row data.
     * <p>This screen answers a successful save with the unhelpful toast <b>"Message Not Found."</b> (a server-side
     * message-key lookup failure, not a validation error), so the toast alone cannot decide pass/fail — the grid
     * is the authority.</p>
     */
    public boolean recordExists(String code) {
        if (code == null || code.isEmpty()) return false;
        if (!onScreen()) navigateViaMenu();
        waitForAngular(1500);
        for (int attempt = 0; attempt < 6; attempt++) {
            Object found = page.evaluate("(code) => { let hit=false;"
                    + " document.querySelectorAll('*').forEach(el=>{ if(hit) return; try{ const s=angular.element(el).scope();"
                    + "   const d=s&&s.grid&&s.grid.options&&s.grid.options.data; if(!d||!d.length) return;"
                    + "   if(d.some(r=>Object.keys(r).some(k=>String(r[k]).trim().toUpperCase()===code.toUpperCase()))) hit=true; }catch(e){} });"
                    + " if(!hit) hit=(document.body.innerText||'').toUpperCase().indexOf(code.toUpperCase())>=0; return hit; }", code);
            if (Boolean.TRUE.equals(found)) return true;
            page.waitForTimeout(1500);
        }
        return false;
    }

    /** Click <b>Submit</b> (matched by text — the handler name differs per screen) and return the toast. */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__ioToasts=[]; if(window.__ioObs) window.__ioObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ioToasts.includes(t)) window.__ioToasts.push(t); }); };"
                + " window.__ioObs=new MutationObserver(grab); window.__ioObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForAngular(500);
            Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return ''; b.id='__ioSubmit'; return b.getAttribute('ng-click')||'submit'; }");
            String how = tagged == null ? "" : tagged.toString();
            System.out.println("IntakeOutputMaster submit[" + attempt + "] => " + (how.isEmpty() ? "(no Submit button)" : how));
            if (how.isEmpty()) return last;
            try { page.locator("#__ioSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
            page.evaluate("() => { const e=document.getElementById('__ioSubmit'); if(e) e.removeAttribute('id'); }");
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                        + " || (window.__ioToasts||[]).some(a=>/intake|output|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__ioToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            if (last.toLowerCase().matches(".*(saved|added|success).*")) return last;
            page.evaluate("() => { window.__ioToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
