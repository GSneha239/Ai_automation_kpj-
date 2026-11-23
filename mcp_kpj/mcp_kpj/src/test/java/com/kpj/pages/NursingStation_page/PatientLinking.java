package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Patient Linking</b> ({@code #/IVFPatientLinking}) — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>Patient Linking</b> → in <b>Patient Detail</b> enter <b>MRN</b> +
 * search and select a <b>relationship</b> → in <b>Patient Link Details</b> enter <b>MRN</b> + search and
 * select a <b>relationship</b> → <b>Add</b> → <b>Save</b> → toast.</p>
 *
 * <h2>Two sections with the same controls</h2>
 * <p>Patient Detail and Patient Link Details each carry their own MRN box, search icon and relationship
 * dropdown. Targeting by ng-model would fill the FIRST section twice and leave the second empty — a
 * mistake that looks like the screen ignoring input. Every lookup here is therefore scoped to a section,
 * located by its heading text via {@link #sectionScope}, so each half is filled independently.</p>
 *
 * <p><b>Navigation escalates</b> (menu click → hash route → full page load) — several Nursing Station menu
 * links render an empty shell.</p>
 */
public class PatientLinking extends BasePage {

    public PatientLinking(Page page) { super(page); }

    public static final String ROUTE = "#/IVFPatientLinking";

    /** Heading text identifying each half of the screen. */
    public static final String SECTION_PATIENT = "patient\\s*detail";
    public static final String SECTION_LINK = "patient\\s*link\\s*detail";

    public String lastControls = "", lastBodyText = "";
    public String lastPatientSearch = "", lastPatientRelationship = "";
    public String lastLinkSearch = "", lastLinkRelationship = "";
    public String lastAdd = "", lastSaveDiagnostics = "";

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const A=window.angular; const $=window.jQuery;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "const attrs=e=>[e.getAttribute('ng-model'),e.getAttribute('id'),e.getAttribute('name')].filter(Boolean).join(' ');"
            + "const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
            + "  return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||e.getAttribute('title')||''); };"
            + "const setEl=(e,v)=>{ if(!e) return '(no-field)';"
            + "  if(e.readOnly||e.disabled) return '(read-only)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} } return v; };"
            + "const pickEl=async(e)=>{ if(!e) return '(no-field)';"
            + "  for(let k=0;k<12;k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };"
            // Find the container for a section by its heading, then narrow to the smallest ancestor that
            // still holds an MRN box — that keeps the two halves from overlapping.
            + "const sectionScope=(rx)=>{ const re=new RegExp(rx,'i');"
            + "  const heads=[...document.querySelectorAll('h1,h2,h3,h4,h5,legend,label,th,td,div,span,a,li')]"
            + "    .filter(vis).filter(e=>re.test(norm(e.textContent)) && norm(e.textContent).length<60);"
            + "  if(!heads.length) return null;"
            + "  heads.sort((a,b)=>norm(a.textContent).length-norm(b.textContent).length);"
            + "  let n=heads[0];"
            + "  while(n && n!==document.body){"
            + "    const mrn=[...n.querySelectorAll('input')].filter(vis)"
            + "      .find(e=>/mrno|mr\\s*no|prn/i.test(attrs(e)+' '+labelOf(e)));"
            + "    if(mrn) return n;"
            + "    n=n.parentElement; }"
            + "  return null; };";

    private static final String TOAST_ELS = ""
            + "const _tv=e=>e && e.offsetParent!==null;"
            + "const toastEls=()=>{ let n=[...document.querySelectorAll('.toast-message')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('.toast')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('[id^=toast]')].filter(_tv);"
            + "  return n; };";

    // ---- navigation --------------------------------------------------------

    /** Navigate to Patient Linking, escalating menu click → hash route → full page load. */
    public boolean navigateViaMenu(String baseUrl) {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1200);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^patient\\s*linking$/i.test(norm(x.textContent))"
                + "        || /#\\/IVFPatientLinking/i.test(x.getAttribute('href')||''));"
                + " if(a) a.click(); }");
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("PatientLinking.nav: menu click left the screen blank — trying the hash route");
        try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("PatientLinking.nav: hash route left the screen blank — trying a full page load");
        try {
            page.navigate(baseUrl + "/" + ROUTE);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        } catch (Exception e) { System.out.println("PatientLinking.nav: full load failed - " + e.getMessage()); }
        waitForAngular(4000);
        return screenRendered();
    }

    public boolean screenRendered() {
        if (!page.url().toLowerCase().contains("patientlinking")) return false;
        Object n = page.evaluate("() => document.querySelectorAll('select, input[type=text], button').length");
        return n != null && Integer.parseInt(n.toString()) > 2;
    }

    /** Diagnostics: which sections were found, and every control with its ng-model. */
    public String describeControls() {
        // The section regexes are passed as ARGUMENTS, not interpolated into the JS source: embedding
        // them mangles the backslashes (\s* became s*), which made this diagnostic report
        // "SECTION NOT FOUND" for sections that were in fact found and filled correctly.
        Object r = page.evaluate("(rxs) => {" + JS
                + " const secs=rxs"
                + "   .map(rx=>{ const s=sectionScope(rx);"
                + "     if(!s) return 'SECTION /'+rx+'/ NOT FOUND';"
                + "     const ins=[...s.querySelectorAll('input')].filter(vis).map(e=>(e.getAttribute('ng-model')||'?'));"
                + "     const sels=[...s.querySelectorAll('select')].filter(vis).map(e=>(e.getAttribute('ng-model')||'?'));"
                + "     return 'SECTION /'+rx+'/ -> inputs['+ins.join(', ')+'] selects['+sels.join(', ')+']'; });"
                + " const btn=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .filter(e=>e.getAttribute('ng-click') && !/setDatepickerDay|Month|YearsPagination|page(First|Previous|Next|Last)/.test(e.getAttribute('ng-click')))"
                + "   .map(e=>'BTN \"'+norm(e.textContent||e.value).slice(0,20)+'\" [ng-click='+e.getAttribute('ng-click')+']');"
                + " return secs.concat(btn).join('\\n'); }",
                java.util.List.of(SECTION_PATIENT, SECTION_LINK));
        lastControls = r == null ? "" : r.toString();
        Object b = page.evaluate("() => (document.body.innerText||'').replace(/\\s+/g,' ').trim().slice(0,220)");
        lastBodyText = b == null ? "" : b.toString();
        System.out.println("PatientLinking CONTROLS:\n" + lastControls);
        return lastControls;
    }

    // ---- per-section actions -----------------------------------------------

    /**
     * Enter the <b>MRN</b> and click search WITHIN one section.
     *
     * <p>The search icon is the one inside that section, so the other half's patient is never replaced.
     * Attachment is judged on the section gaining a populated non-MRN field (a patient name), because a
     * bare MRN echo proves nothing.</p>
     *
     * @param sectionRx {@link #SECTION_PATIENT} or {@link #SECTION_LINK}
     */
    public String searchInSection(String sectionRx, String mrn) {
        Object r = page.evaluate("(args) => {" + JS
                + " const [rx, m] = args;"
                + " const s=sectionScope(rx);"
                + " if(!s) return '(section-not-found)';"
                + " const e=[...s.querySelectorAll('input')].filter(vis)"
                + "   .find(x=>/mrno|mr\\s*no|prn/i.test(attrs(x)+' '+labelOf(x)));"
                + " if(!e) return '(no-mrn-field)';"
                + " setEl(e, m);"
                + " const b=[...s.querySelectorAll('button,a,i,span,img,input[type=button]')].filter(vis)"
                + "   .find(x=>/SearchPatientByMRNo|search/i.test(x.getAttribute('ng-click')||''));"
                + " if(b){ b.setAttribute('data-plsearch','1'); return 'set ['+(e.getAttribute('ng-model')||'?')+']'; }"
                + " return 'set ['+(e.getAttribute('ng-model')||'?')+'] (no search icon in this section)'; }",
                java.util.List.of(sectionRx, mrn));
        String setRes = r == null ? "" : r.toString();
        if (setRes.startsWith("(")) {
            System.out.println("PatientLinking.searchInSection: " + setRes);
            return setRes;
        }
        try { page.locator("[data-plsearch='1']").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PatientLinking.searchInSection: search click failed - " + e.getMessage()); }
        page.evaluate("() => { document.querySelectorAll('[data-plsearch]').forEach(e=>e.removeAttribute('data-plsearch')); }");
        waitForAngular(2500);

        Object v = page.evaluate("(args) => {" + JS + TOAST_ELS
                + " const [rx, m] = args;"
                + " const s=sectionScope(rx);"
                + " if(!s) return '(section-gone)';"
                + " const filled=[...s.querySelectorAll('input')].filter(vis)"
                + "   .filter(x=>x.value && !/mrno|mr\\s*no|prn/i.test(attrs(x)+' '+labelOf(x)))"
                + "   .slice(0,3).map(x=>(labelOf(x)||attrs(x))+'='+x.value);"
                + " const msg=toastEls().map(x=>norm(x.textContent)).join(' | ');"
                + " return 'mrn='+m+' | patientFields='+filled.length"
                + "   +(filled.length?' | '+filled.join(' ; '):'')+(msg?' | msg='+msg:''); }",
                java.util.List.of(sectionRx, mrn));
        String out = v == null ? "" : v.toString();
        if (SECTION_PATIENT.equals(sectionRx)) lastPatientSearch = out; else lastLinkSearch = out;
        System.out.println("PatientLinking [" + sectionRx + "]: " + out);
        return out;
    }

    /** True when that section's search populated a patient field (not just the MRN typed in). */
    public boolean patientLoaded(String sectionRx) {
        String s = SECTION_PATIENT.equals(sectionRx) ? lastPatientSearch : lastLinkSearch;
        if (s == null || s.isEmpty() || s.startsWith("(")) return false;
        if (s.toLowerCase().contains("not found")) return false;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("patientFields=(\\d+)").matcher(s);
        return m.find() && Integer.parseInt(m.group(1)) > 0;
    }

    /** Select the <b>relationship</b> within one section. */
    public String selectRelationship(String sectionRx) {
        Object r = page.evaluate("(rx) => new Promise(async resolve => {" + JS
                + " const s=sectionScope(rx);"
                + " if(!s){ resolve('(section-not-found)'); return; }"
                + " let e=[...s.querySelectorAll('select')].filter(vis)"
                + "   .find(x=>/relation/i.test(attrs(x)+' '+labelOf(x)));"
                + " if(!e) e=[...s.querySelectorAll('select')].filter(vis).find(x=>x.options.length>1);"
                + " if(!e){ resolve('(no-relationship-select)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " resolve((await pickEl(e))+' '+which); })", sectionRx);
        String out = r == null ? "" : r.toString();
        if (SECTION_PATIENT.equals(sectionRx)) lastPatientRelationship = out; else lastLinkRelationship = out;
        waitForAngular(500);
        System.out.println("PatientLinking [" + sectionRx + "] relationship = " + out);
        return out;
    }

    // ---- add + save --------------------------------------------------------

    /** Click <b>Add</b> and report the row delta. */
    public String clickAdd() {
        Object before = page.evaluate("() => document.querySelectorAll('table tbody tr, .ui-grid-row').length");
        int rowsBefore = before == null ? 0 : Integer.parseInt(before.toString());

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)))"
                + "   || c.find(x=>/add/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__plAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("PatientLinking.clickAdd: Add not found"); return "(no Add button)"; }
        try { page.locator("#__plAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PatientLinking.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__plAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);

        Object r = page.evaluate("(rowsBefore) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=document.querySelectorAll('table tbody tr, .ui-grid-row').length;"
                + " const msg=[...document.querySelectorAll('.toast-message')].map(x=>norm(x.textContent)).join(' | ');"
                + " return 'rowsAdded='+(rows-rowsBefore)+' total='+rows+(msg?' | msg='+msg:''); }", rowsBefore);
        lastAdd = r == null ? "" : r.toString();
        System.out.println("PatientLinking: Add -> " + lastAdd);
        return lastAdd;
    }

    /** Click <b>Save</b> and return the toast ("" if none appeared). */
    public String saveAndGetToast() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + TOAST_ELS
                + " window.__plToasts=[]; if(window.__plObs) window.__plObs.disconnect();"
                + " const grab=()=>{ toastEls().forEach(el=>{ const t=norm(el.textContent);"
                + "   if(t && !window.__plToasts.includes(t)) window.__plToasts.push(t); }); };"
                + " window.__plObs=new MutationObserver(grab); window.__plObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " window.__plPrior=(window.__plToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)))"
                + "   || c.find(x=>/save|IUD/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__plSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("PatientLinking.save: Save not found"); return ""; }

        try { page.locator("#__plSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PatientLinking.save: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__plSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(900);
        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__plToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }

        Object r = page.evaluate("() => { const prior=window.__plPrior||[];"
                + " const a=(window.__plToasts||[]).filter(x=>!prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated|linked)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        String toast = r == null ? "" : r.toString().trim();

        if (toast.isEmpty()) {
            Object diag = page.evaluate("() => { const seen=(window.__plToasts||[]).join(' ; ');"
                    + " const invalid=[...document.querySelectorAll('.ng-invalid, .has-error')]"
                    + "   .filter(e=>e.offsetParent!==null && e.tagName!=='FORM').length;"
                    + " return 'url='+location.hash+' invalidFields='+invalid"
                    + "   +(seen?' | toastsSeen='+seen:' | toastsSeen=none'); }");
            lastSaveDiagnostics = diag == null ? "" : diag.toString();
            System.out.println("PatientLinking.save: no toast — " + lastSaveDiagnostics);
        }
        waitForAngular(400);
        return toast;
    }
}
