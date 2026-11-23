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
        // Unique per run (a fixed pool is exhausted for good on the shared QA env), kept within 30 characters.
        String suffix = " " + code.substring(2);
        String base = REMARKS[attempt % REMARKS.length];
        lastRemark = (base.length() + suffix.length() > 30 ? base.substring(0, 30 - suffix.length()).trim() : base) + suffix;
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
    public String lastIcdVersion = "";

    /**
     * Select the <b>ICD Version</b> ({@code BiohazardCategoryMaster.ICDVersionId}) — "ICD10" when offered, else the first real
     * option. The Diagnosis Code lookup belongs to this version, and Submit answers "Failed!" without it.
     */
    public String selectIcdVersion() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=document.querySelector(\"select[ng-model='BiohazardCategoryMaster.ICDVersionId']\"); if(!e) return '(no ICD Version dropdown)';"
                + " const reals=[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.textContent)));"
                + " if(!reals.length) return '(the ICD Version list is empty)';"
                + " const pick=reals.find(x=>/^icd\\s*-?\\s*10$/i.test(norm(x.o.textContent)))||reals[0];"
                + " e.selectedIndex=pick.i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ window.angular.element(e).triggerHandler('change'); }catch(x){}"
                + " return norm(pick.o.textContent); }");
        waitForAngular(1200);
        lastIcdVersion = r == null ? "" : r.toString();
        return lastIcdVersion;
    }

    public boolean icdVersionSelected() { return !lastIcdVersion.isEmpty() && !lastIcdVersion.startsWith("("); }

    public boolean listFound = false;

    /**
     * After a successful Submit, look for the new row IN THE LIST TABLE: wait for the grid data to load, search the Code
     * column filter with real keystrokes, and if the filter does not show it page through every page. Sets {@link #listFound}.
     */
    public String verifyInList(String code) {
        listFound = false;
        try {
            if (page.url().toLowerCase().contains("add-")) {
                page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('button,a')]"
                        + ".filter(x=>x.offsetWidth||x.offsetHeight).find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')||/^\\s*back\\s*$/i.test(norm(x.textContent))); if(b) b.click(); }");
                waitForAngular(2000);
            }
        } catch (Exception ignore) { }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row')].filter(r=>r.offsetWidth||r.offsetHeight)"
                    + ".some(r=>(r.textContent||'').replace(/\\s+/g,' ').trim().length>0)",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { }
        waitForAngular(800);
        final String gridText = "() => ({rows:[...document.querySelectorAll('.ui-grid-row')].filter(r=>r.offsetWidth||r.offsetHeight).length,"
                + " text:[...document.querySelectorAll('.ui-grid-row')].filter(r=>r.offsetWidth||r.offsetHeight).map(r=>(r.textContent||'').replace(/\\s+/g,' ').trim()).join(' || ')})";
        String viaFilter = "no column filter box";
        try {
            com.microsoft.playwright.Locator filter = page.locator("input[ng-model='colFilter.term']:visible").first();
            if (filter.count() > 0) {
                filter.click();
                filter.pressSequentially(code, new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(60));
                waitForAngular(2500);
                Object g = page.evaluate(gridText);
                String txt = g instanceof java.util.Map ? String.valueOf(((java.util.Map<?, ?>) g).get("text")) : "";
                boolean hit = txt.contains(code);
                viaFilter = "searched the Code filter for \"" + code + "\" -> " + (hit ? "FOUND" : "not shown")
                        + (g instanceof java.util.Map ? " (" + ((java.util.Map<?, ?>) g).get("rows") + " row(s) shown)" : "");
                if (hit) { listFound = true; return viaFilter; }
                filter.fill("");
                waitForAngular(1500);
            }
        } catch (Exception e) { viaFilter = "filter failed: " + e.getMessage().split("\n")[0]; }
        int pages = 0;
        String last = null;
        try { page.evaluate("() => { const b=document.querySelector(\"[ng-click^='pageFirstPageClick']\"); if(b&&!b.disabled) b.click(); }"); waitForAngular(1000); }
        catch (Exception ignore) { }
        for (int p = 0; p < 80; p++) {
            Object g = page.evaluate(gridText);
            String txt = g instanceof java.util.Map ? String.valueOf(((java.util.Map<?, ?>) g).get("text")) : "";
            pages++;
            if (txt.contains(code)) { listFound = true; return viaFilter + "; found on page " + pages + " while paging through the list"; }
            if (txt.equals(last)) break;
            last = txt;
            Object moved = page.evaluate("() => { const b=document.querySelector(\"[ng-click^='pageNextPageClick']\"); if(!b||b.disabled) return false; b.click(); return true; }");
            if (!Boolean.TRUE.equals(moved)) break;
            waitForAngular(900);
        }
        return viaFilter + "; paged through " + pages + " page(s) of the list without finding \"" + code + "\"";
    }

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
        // The Diagnosis Description box is an AUTOCOMPLETE: a diagnosis only counts once it is CHOSEN from the suggestions, because
        // choosing it is what gives the row its diagnosis id. Writing a made-up code / description straight into the model (as this
        // used to) adds a row with NO id, and Submit then fails on the server with "Cannot insert the value NULL into column
        // 'diagnosisid'" (HTTP 500, shown as a bare "Error!"). So type a real diagnosis name with real key presses and pick the first
        // suggestion.
        String term = new String[]{"Cholera", "Typhoid", "Tuberculosis", "Dengue", "Hepatitis B"}[(int) (Math.abs(System.nanoTime()) % 5)];
        int before = diagnosisRowCount();
        String picked = "(no-suggestion)";
        final String sugSel = "li.auto-complete-item, .ui-autocomplete li, ul.dropdown-menu li";
        try {
            com.microsoft.playwright.Locator box = page.locator("#txtDiagnosisName");
            box.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            box.fill("");
            box.pressSequentially(term, new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(90));
            page.waitForFunction("(sel) => [...document.querySelectorAll(sel)].some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    sugSel, new Page.WaitForFunctionOptions().setTimeout(10000));
            Object t = page.evaluate("(sel) => { const li=[...document.querySelectorAll(sel)].find(e=>e.offsetParent!==null && (e.textContent||'').trim());"
                    + " if(!li) return ''; li.setAttribute('data-bhsug','1'); return (li.textContent||'').replace(/\\s+/g,' ').trim(); }", sugSel);
            picked = t == null ? "" : t.toString();
            if (!picked.isEmpty()) page.locator("[data-bhsug='1']").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            else picked = "(no-suggestion)";
        } catch (Exception e) { System.out.println("enterDiagnosisAndAdd: choosing a suggestion failed - " + e.getMessage()); }
        page.evaluate("() => document.querySelectorAll('[data-bhsug]').forEach(e=>e.removeAttribute('data-bhsug'))");
        waitForAngular(1200);
        Object dcv = page.evaluate("() => { const e=[...document.querySelectorAll(\"input[ng-model='BiohazardCategoryMaster.DiagnosisCodeId']\")].find(x=>x.offsetParent!==null); return e?e.value:'(no)'; }");
        lastDiagnosis = (dcv == null ? "" : dcv.toString()) + " - " + picked;
        Object r = "DiagnosisCode=" + (dcv == null ? "" : dcv.toString()) + " | DiagnosisDescription=" + picked + " (typed \"" + term + "\")";
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
