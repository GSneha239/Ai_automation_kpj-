package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Nursing &gt; <b>Biohazard Category Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Nursing</b> (submenu) → <b>Biohazard Category Master</b>
 * ({@code #/BiohazardCategoryMasterList}) → <b>Add</b> ({@code AddDBiohazardCategory()} →
 * {@code #/add-BiohazardCategoryMaster}) → enter <b>Code*</b> + <b>Remark*</b> → select <b>Applicability</b>
 * (radio) → enter <b>Diagnosis Code</b> + <b>Diagnosis Description</b> → inner <b>Add</b>
 * ({@code AddDiagnosis()}) so a row lands in the "Diagnosis Code · Diagnosis Description · Delete" grid →
 * <b>Submit</b> ({@code fnIUDBiohazardCategoryMaster()}) → toast.</p>
 *
 * <p>Field notes: Remark is {@code BiohazardCategoryMaster.description} (lower-case d), Applicability is a pair of
 * radios on {@code BiohazardCategoryMaster.applicability}, and the Diagnosis Description box carries the id
 * {@code txtDiagnosisName} — like Cluster's {@code #txtDoctorName} it is an autocomplete that looks itself up by
 * id, so NEVER retag it.</p>
 */
public class BiohazardCategoryMaster extends BasePage {

    public BiohazardCategoryMaster(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/BiohazardCategoryMasterList";
    public String lastCode = "", lastRemark = "", lastApplicability = "", lastDiagnosis = "";
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
            "Infectious Waste", "Sharps Waste", "Cytotoxic Waste", "Pathological Waste", "Chemical Waste"};

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
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*biohazard\\s*category\\s*master\\s*$/i.test(norm(x.textContent)) || (x.getAttribute('href')||'')==='#/BiohazardCategoryMasterList'); if(!a) return ''; a.id='__bhMenu'; return a.getAttribute('href')||'link'; }");
            if (href == null || href.toString().isEmpty()) { System.out.println("BiohazardCategoryMaster.nav: menu link not found (attempt " + (attempt + 1) + ")"); continue; }
            try { page.locator("#__bhMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("BiohazardCategoryMaster.nav: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__bhMenu'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddDBiohazardCategory\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { System.out.println("BiohazardCategoryMaster.nav: screen not confirmed (attempt " + (attempt + 1) + ")"); }
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

    /** On the REAL list — verified by its own Add ({@code AddDBiohazardCategory}) button, not just the URL. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("biohazardcategorymaster")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddDBiohazardCategory\\s*\\(/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)"
                + " || [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='BiohazardCategoryMaster.Code')"));
    }

    /** Real-click <b>Add</b> ({@code AddDBiohazardCategory} — note the stray D) → the add form. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddDBiohazardCategory\\s*\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__bhAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("BiohazardCategoryMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__bhAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("BiohazardCategoryMaster.clickAdd: click failed - " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__bhAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='BiohazardCategoryMaster.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("BiohazardCategoryMaster.clickAdd: add form did not render"); }
        waitForAngular(1000);
        return page.url().toLowerCase().contains("add-biohazardcategorymaster");
    }

    // ---- form ------------------------------------------------------------

    /**
     * Enter <b>Code*</b> and <b>Remark*</b> ({@code BiohazardCategoryMaster.description} — lower-case d).
     * {@code attempt} shifts BOTH values so a retry after an "already exists" toast submits genuinely
     * different details; once the small realistic-name pool is exhausted a short number is appended so the
     * retry is still guaranteed unique.
     */
    public String fillCodeAndRemark(int attempt) {
        String code = "BH" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        if (attempt >= REMARKS.length) lastRemark = lastRemark + " " + (Math.abs(System.nanoTime()) % 10000);
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('BiohazardCategoryMaster.Code', a.code); const rm=set('BiohazardCategoryMaster.description', a.remark);"
                + " return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /** Select <b>Applicability</b> — a pair of radios on {@code BiohazardCategoryMaster.applicability}. Real-click
     *  the first one so its Angular binding fires. Returns the label that was picked. */
    public String selectApplicability() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const r=[...document.querySelectorAll(\"input[type=radio][ng-model='BiohazardCategoryMaster.applicability']\")].find(x=>x.offsetParent!==null);"
                + " if(!r) return ''; r.id='__bhAppl';"
                + " const lab=(r.closest('label')||{}).textContent || (r.parentElement||{}).textContent || '';"
                + " return norm(lab).slice(0,30) || 'applicability'; }");
        String label = tagged == null ? "" : tagged.toString();
        if (label.isEmpty()) { System.out.println("selectApplicability: no applicability radio"); return "(no radio)"; }
        try { page.locator("#__bhAppl").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectApplicability: click failed - " + e.getMessage()); }
        waitForAngular(600);
        Object checked = page.evaluate("() => { const e=document.getElementById('__bhAppl'); return !!(e && e.checked); }");
        page.evaluate("() => { const e=document.getElementById('__bhAppl'); if(e) e.removeAttribute('id'); }");
        lastApplicability = label;
        return label + " (selected=" + checked + ")";
    }

    /** Rows in the "Diagnosis Code · Diagnosis Description · Delete" grid. */
    public int diagnosisRowCount() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/diagnosis code/i.test(((x.querySelector('thead')||{}).innerText)||''));"
                + " if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>norm(r.innerText) && !/no record/i.test(r.innerText)).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * Enter <b>Diagnosis Code</b> and <b>Diagnosis Description</b>, then click the inner <b>Add</b>
     * ({@code AddDiagnosis()}) so the row lands in the grid.
     *
     * <p>The Description box is {@code #txtDiagnosisName}, an autocomplete that looks itself up by id — so it is
     * typed into by its REAL id and never retagged (same trap as Cluster's {@code #txtDoctorName}).</p>
     */
    public String enterDiagnosisAndAdd() {
        String dcode = "A0" + String.format("%02d", Math.abs(System.nanoTime() % 100));
        String ddesc = new String[]{"Cholera", "Typhoid Fever", "Tuberculosis", "Dengue Fever", "Hepatitis B"}
                [(int) (Math.abs(System.nanoTime()) % 5)];
        lastDiagnosis = dcode + " - " + ddesc;
        int before = diagnosisRowCount();
        Object r = page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"input[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const dc=set('BiohazardCategoryMaster.DiagnosisCodeId', a.dcode);"
                + " const dd=set('BiohazardCategoryMaster.DiagnosisDescriptionId', a.ddesc);"
                + " return 'DiagnosisCode='+dc+' | DiagnosisDescription='+dd; }",
                java.util.Map.of("dcode", dcode, "ddesc", ddesc));
        waitForAngular(1200);
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddDiagnosis/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__bhAddDiag'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("enterDiagnosisAndAdd: AddDiagnosis button not found"); return (r == null ? "" : r.toString()) + " | (no Add button)"; }
        try { page.locator("#__bhAddDiag").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("enterDiagnosisAndAdd: Add click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__bhAddDiag'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        return (r == null ? "" : r.toString()) + " | diagnosis rows " + before + " -> " + diagnosisRowCount();
    }

    /** Click <b>Submit</b> ({@code fnIUDBiohazardCategoryMaster}) and return the toast; screenshots it while shown. */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__bhToasts=[]; if(window.__bhObs) window.__bhObs.disconnect();"
                // Stop toastr auto-dismissing so the success message is still fully opaque when we screenshot it.
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__bhToasts.includes(t)) window.__bhToasts.push(t); }); };"
                + " window.__bhObs=new MutationObserver(grab); window.__bhObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDBiohazardCategoryMaster/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__bhSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__bhSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__bhSubmit'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|error/i.test(el.textContent||''))"
                    + " || (window.__bhToasts||[]).some(a=>/biohazard|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        captureToastShot();
        Object r = page.evaluate("() => { const a=window.__bhToasts||[]; return a.find(x=>/(biohazard|record|master|category).*(saved|added)|saved success|added success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
