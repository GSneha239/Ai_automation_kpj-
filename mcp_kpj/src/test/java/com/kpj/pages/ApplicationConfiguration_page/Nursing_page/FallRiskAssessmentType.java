package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Fall Risk Assessment Type</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>FRAType</b> ({@code #/FRATypeMaster})
 * → <b>Add</b> ({@code AddFRAType()} → {@code #/add-FRAType}) → enter <b>Code*</b> ({@code FRAType.Code}),
 * <b>Remark*</b> ({@code FRAType.Description}) and <b>Interpretation*</b> ({@code FRAType.Interpretation}) →
 * <b>Submit</b> ({@code fnIUD()}) → success toast.</p>
 *
 * <p><b>The menu label is the abbreviation "FRAType", not "Fall Risk Assessment Type"</b>, and the list route is
 * {@code #/FRATypeMaster}. The spelled-out name matches nothing in the Nursing submenu — the neighbouring entries
 * are <b>Fall Risk Assessment</b> ({@code #/FallRiskAssessment}), <b>QuestionsForFallRiskMaster</b>
 * ({@code #/FallRiskChecklistQuestion}) and <b>FallRiskChecklistMaster</b> ({@code #/FallRiskIntervention}). The
 * spelled-out name only appears once the Add form is open, as its header.</p>
 *
 * <p><b>Interpretation is a CKEditor</b>, not a text box: the {@code ck-editor} directive hides the real
 * {@code <textarea ng-model="FRAType.Interpretation">} ({@code display:none}) behind the {@code editor1} instance.
 * A DOM write to that textarea is therefore invisible to Angular — the value is pushed with
 * {@code CKEDITOR.instances.editor1.setData(...)} and then asserted on the scope model as well.</p>
 */
public class FallRiskAssessmentType extends BasePage {

    public FallRiskAssessmentType(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/FRATypeMaster";
    public String lastCode = "", lastRemark = "", lastInterpretation = "";
    /** Screenshot taken while the success toast was still visible (toastr auto-dismisses). */
    public byte[] toastPng;

    /** Extra settle time before the toast screenshot, so the message is fully painted (and the grid refreshed). */
    private static final int TOAST_SHOT_WAIT_MS = 5000;

    /** Grab a full-page screenshot with the toast still showing. Never fails the flow. */
    private void captureToastShot() {
        waitForAngular(TOAST_SHOT_WAIT_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("captureToastShot: " + e.getMessage()); }
    }

    private static final String[] REMARKS = {
            "Low Fall Risk", "Moderate Fall Risk", "High Fall Risk", "Post Operative Assessment", "Paediatric Assessment"};
    private static final String[] INTERPRETATIONS = {
            "Standard fall precautions apply; reassess every shift.",
            "Hourly rounding and bed alarm required for this patient.",
            "Assist with all transfers and ambulation at all times.",
            "Reassess immediately after any fall or change in condition.",
            "Keep the bed low with side rails raised and call bell within reach."};

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
            // The menu label is the abbreviation "FRAType" (the spelled-out name matches nothing); accept either
            // spelling, but match EXACTLY — "Fall Risk Assessment" is a different, neighbouring screen.
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); document.querySelectorAll('#__fatMenu').forEach(e=>e.removeAttribute('id')); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*(fra\\s*type|fall\\s*risk\\s*assessment\\s*type)\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/FRATypeMaster'); if(!a) return ''; a.id='__fatMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("FallRiskAssessmentType.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            // The link can still be collapsed/animating — a synthetic click fires its route handler regardless.
            try { page.locator("#__fatMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { page.evaluate("() => { const a=document.getElementById('__fatMenu'); if(a) a.click(); }"); }
            page.evaluate("() => { const e=document.getElementById('__fatMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddFRAType\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("FallRiskAssessmentType.nav: FRAType list not confirmed (attempt " + (attempt + 1) + ")"); }
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
     * On the REAL FRAType list — verified by its own Add button ({@code AddFRAType}), not just the URL: the SPA can
     * leave a previous screen mounted while the hash already reads {@code #/FRATypeMaster} (the list even renders
     * under a stale "Transfer" header), and that leftover markup carries a generic commonmaster form which a
     * Code-only check would happily accept.
     */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("fratype")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddFRAType\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"));
    }

    /** Real-click <b>Add</b> ({@code AddFRAType}) → {@code #/add-FRAType}. Polls — it renders after the grid. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddFRAType\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__fatAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("FallRiskAssessmentType.clickAdd: Add button not found"); return false; }
        try { page.locator("#__fatAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("FallRiskAssessmentType.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__fatAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='FRAType.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("FallRiskAssessmentType.clickAdd: add form did not render"); }
        waitForAngular(1200);
        return page.url().toLowerCase().contains("add-fratype");
    }

    /** True once the Add form is up (its Code field is present). */
    public boolean onAddForm() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='FRAType.Code')"));
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> ({@code FRAType.Code}), <b>Remark*</b> ({@code FRAType.Description}) and
     * <b>Interpretation*</b> ({@code FRAType.Interpretation}). Interpretation is a CKEditor whose backing textarea
     * is {@code display:none}, so it is written through {@code CKEDITOR.instances[..].setData()} and then also
     * asserted on the scope — a plain DOM write would never reach the model. Returns a summary including the value
     * Angular actually holds for Interpretation.
     */
    public String fillDetails() {
        String code = "FA" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        lastRemark = REMARKS[(int) (Math.abs(System.nanoTime()) % REMARKS.length)];
        lastInterpretation = INTERPRETATIONS[(int) (Math.abs(System.nanoTime() / 7) % INTERPRETATIONS.length)];
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const A=window.angular; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('FRAType.Code', a.code); const rm=set('FRAType.Description', a.remark);"
                // Interpretation: CKEditor instance bound to the hidden textarea.
                + " let how='(no editor)'; const ta=document.querySelector(\"textarea[ng-model='FRAType.Interpretation']\");"
                + " if(window.CKEDITOR){ const insts=Object.values(CKEDITOR.instances||{});"
                + "   const inst=(ta&&ta.id&&CKEDITOR.instances[ta.id])||insts[0];"
                + "   if(inst){ try{ inst.setData('<p>'+a.interpretation+'</p>'); how='CKEDITOR:'+inst.name; }catch(e){ how='(setData failed)'; } } }"
                + " await sleep(600);"
                // Whatever the directive did or didn't sync, assert the model on the scope that owns FRAType.
                + " if(ta){ const s=A.element(ta).scope(); if(s){ try{ s.$apply(()=>{ s.FRAType=s.FRAType||{}; s.FRAType.Interpretation='<p>'+a.interpretation+'</p>'; }); }catch(e){ s.FRAType=s.FRAType||{}; s.FRAType.Interpretation='<p>'+a.interpretation+'</p>'; } } }"
                + " await sleep(300);"
                + " let model='(unknown)'; if(ta){ const s=A.element(ta).scope(); if(s&&s.FRAType) model=String(s.FRAType.Interpretation||'').replace(/<[^>]*>/g,'').trim(); }"
                + " resolve('Code='+cd+' | Remark='+rm+' | Interpretation='+how+' -> model=\"'+model+'\"'); })",
                java.util.Map.of("code", code, "remark", lastRemark, "interpretation", lastInterpretation));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Submit</b> ({@code fnIUD()}) and return the toast. All three values are re-applied to the
     * {@code FRAType} model on every scope first (the CKEditor can re-sync an empty body over the model between
     * fill and submit), and the whole thing is retried — the async save occasionally yields no toast at all.
     */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__fatToasts=[]; if(window.__fatObs) window.__fatObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__fatToasts.includes(t)) window.__fatToasts.push(t); }); };"
                + " window.__fatObs=new MutationObserver(grab); window.__fatObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        String last = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            waitForAngular(500);
            Object res = page.evaluate("(a) => { const A=window.angular; const html='<p>'+a.interpretation+'</p>';"
                    + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    + " setInp('FRAType.Code', a.code); setInp('FRAType.Description', a.remark);"
                    + " const seen=new Set(); let handlerScope=null;"
                    + " document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); if(!s||seen.has(s.$id)) return; seen.add(s.$id);"
                    + "   if(s.FRAType && typeof s.FRAType==='object'){ s.FRAType.Code=a.code; s.FRAType.Description=a.remark; s.FRAType.Interpretation=html; }"
                    + "   if(typeof s.fnIUD==='function' && !handlerScope) handlerScope=s; }catch(e){} });"
                    + " if(handlerScope){ try{ handlerScope.$apply(function(){ handlerScope.fnIUD(); }); }catch(e){ try{ handlerScope.fnIUD(); }catch(e2){} } return 'invoked:fnIUD'; }"
                    + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(b){ b.id='__fatSubmit'; return 'click'; } return 'none'; }",
                    java.util.Map.of("code", lastCode, "remark", lastRemark, "interpretation", lastInterpretation));
            System.out.println("FallRiskAssessmentType submit[" + attempt + "] => " + res);
            if ("click".equals(String.valueOf(res))) {
                try { page.locator("#__fatSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
                page.evaluate("() => { const e=document.getElementById('__fatSubmit'); if(e) e.removeAttribute('id'); }");
            }
            // AWAIT the toast, then screenshot it WHILE IT IS STILL ON SCREEN — toastr auto-dismisses after a few
            // seconds, so a screenshot taken once this method returns would show an empty page.
            try {
                page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                        + " || (window.__fatToasts||[]).some(a=>/fall|risk|fra|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
            } catch (Exception ignore) { page.waitForTimeout(1500); }
            captureToastShot();
            Object t = page.evaluate("() => { const a=window.__fatToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
            last = t == null ? "" : t.toString().trim();
            if (last.toLowerCase().matches(".*(saved|added|success).*")) return last;
            // "… already exists!" is NOT retryable with the SAME values — the old loop re-submitted the
            // identical Code/Remark and simply got the same rejection every attempt. Regenerate the
            // details (fillDetails() picks a fresh Code and a different description) and try again.
            if (last.toLowerCase().matches(".*(exist|already).*")) {
                System.out.println("submitAndGetToast: \"" + last + "\" — regenerating the details and retrying");
                fillDetails();
                // The realistic-name pool is SMALL and this environment already holds EVERY entry, so
                // picking another name collides again — that is why the retries kept failing. Append a
                // short number so the retry is genuinely unique, while still reading like configuration
                // ("Peanut 4821", not machine noise). The FIRST attempt keeps the clean name.
                lastRemark = lastRemark + " " + (Math.abs(System.nanoTime()) % 10000);
            }
            page.evaluate("() => { window.__fatToasts=[]; document.querySelectorAll('.toast-message,.toast').forEach(t=>t.remove()); }");
        }
        return last;
    }
}
