package com.kpj.pages.AncillaryServices_page.PatientFeedback_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; <b>Patient Feedback</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Patient Feedback List</b> ({@code #/PatientFeedbackList}, a
 * search/list grid) → <b>Add</b> ({@code addPatientFeedback()}) → the feedback form
 * ({@code #/add-PatientFeedback}) → enter <b>MRN No.</b> + search → select the <b>feedback template</b> →
 * <b>Save</b> ({@code IUDPatientFeedbackDetail()}) → toast.</p>
 *
 * <p><b>Fields (discovered live 2026-08-06):</b> list screen — {@code PatientFeedbackList.fromdate},
 * {@code PatientFeedbackList.todate}, {@code PatientFeedback.MRNo}, buttons {@code fnSearchFeedbackList()},
 * {@code addPatientFeedback()}. Form — {@code PatientFeedback.MRNo} with {@code SearchPatientByMRNo()},
 * {@code PatientFeedback.Dischargeid}, plus the template control; Save {@code IUDPatientFeedbackDetail()},
 * Back {@code closeForm()}.</p>
 *
 * <p><b>The template control only exists after the MRN resolves.</b> On a freshly opened form the sole
 * select is {@code Dischargeid}; the template dropdown is rendered once a patient is loaded. So it is
 * looked up <i>after</i> the search, by ng-model or label naming "template", and
 * {@link #describeControls()} is worth calling at that point to see what the screen actually offers.</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class PatientFeedback extends BasePage {

    public PatientFeedback(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/PatientFeedbackList";
    public static final String ADD_ROUTE = "#/add-PatientFeedback";
    private static final String M = "PatientFeedback.";

    public String lastControls = "";
    public String lastMrn = "", lastSearchResult = "", lastTemplate = "";

    // ---- shared JS ---------------------------------------------------------

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const A=window.angular; const $=window.jQuery;"
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "const setInp=(ng,v)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
            + "const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
            + "  return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||''); };"
            + "const pickEl=async(e)=>{ if(!e) return '(no-field)';"
            + "  for(let k=0;k<15;k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };";

    private static final String ARM_TOASTS = ""
            + " window.__pfToasts=[]; if(window.__pfObs) window.__pfObs.disconnect();"
            + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__pfToasts.includes(t)) window.__pfToasts.push(t); }); };"
            + " window.__pfObs=new MutationObserver(grab); window.__pfObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation --------------------------------------------------------

    /** <b>Ancillary Services</b> → <b>Patient Feedback List</b>, else the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("PatientFeedback.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1200);

        // The menu entry reads "Patient Feedback List".
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^patient\\s*feedback(\\s*list)?$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/PatientFeedbackList/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__pfMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__pfMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("PatientFeedback.nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__pfMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onListScreen()) {
            System.out.println("PatientFeedback.nav: falling back to direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/addPatientFeedback/i.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("PatientFeedback.nav: list screen did not finish rendering"); }
        return onListScreen();
    }

    public boolean onListScreen() {
        String url = page.url().toLowerCase();
        return url.contains("patientfeedbacklist") && !url.contains("add-");
    }

    /**
     * Search the feedback list over a wide date window and return the MRNs it already holds.
     *
     * <p>Those are patients the screen has previously accepted feedback for, so they satisfy whatever
     * preconditions the save enforces — far more reliable than guessing an MRN. Reads the ui-grid's
     * backing array off the Angular scope rather than scraping cells, since the grid virtualises rows.</p>
     *
     * @return distinct MRNs, most recent first; empty when the list has no rows
     */
    public java.util.List<String> harvestMrnsFromList() {
        // Widen the date filter so the search actually returns history.
        page.evaluate("() => {" + JS
                + " const today=new Date(); const pad=n=>String(n).padStart(2,'0');"
                + " const fmt=d=>pad(d.getDate())+'/'+pad(d.getMonth()+1)+'/'+d.getFullYear();"
                + " const from=new Date(today.getFullYear()-2, today.getMonth(), today.getDate());"
                + " setInp('PatientFeedbackList.fromdate', fmt(from));"
                + " setInp('PatientFeedbackList.todate', fmt(today));"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/fnSearchFeedbackList/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__pfListSearch'; }");
        try { page.locator("#__pfListSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PatientFeedback.harvestMrns: list search click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pfListSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2500);

        Object r = page.evaluate("() => { let best=-1, arr=null;"
                + " document.querySelectorAll('*').forEach(el=>{ try{"
                + "   const s=window.angular.element(el).scope(); if(!s) return;"
                + "   const scan=a=>{ if(Array.isArray(a) && a.length && typeof a[0]==='object'"
                + "        && Object.keys(a[0]).some(k=>/^mrno$/i.test(k)) && a.length>best){ best=a.length; arr=a; } };"
                + "   if(s.grid && s.grid.options && s.grid.options.data) scan(s.grid.options.data);"
                + "   for(const k of Object.keys(s)){ try{ scan(s[k]); }catch(e){} } }catch(e){} });"
                + " const out=[], seen=new Set();"
                + " if(arr){ for(const row of arr){ const key=Object.keys(row).find(k=>/^mrno$/i.test(k));"
                + "   const m=key && row[key] && String(row[key]).trim();"
                + "   if(m && !seen.has(m)){ seen.add(m); out.push(m); } } }"
                + " return out; }");
        java.util.List<String> list = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) if (o != null) list.add(o.toString());
        System.out.println("PatientFeedback: harvested " + list.size() + " MRN(s) from the feedback list -> " + list);
        return list;
    }

    /**
     * Harvest candidate MRNs from the form's own <b>Search Patient</b> popup
     * ({@code OpenPatientSearchPopupScreen()} — a magnifying-glass button beside the MRN field, distinct
     * from typing an MRN and clicking {@code SearchPatientByMRNo()}).
     *
     * <p>Picking a row here via its "Select" button ({@code SetSearchPatient(PR)}) closes the popup but does
     * <b>not</b> feed the MRN back into {@code PatientFeedback.MRNo} — verified live: the value stays on the
     * popup row's own scope and the form's model is untouched. So this popup cannot be used to select-and-fill
     * directly on this screen; it is used only as a much broader MRN <i>source</i> (a blank search returns
     * every patient in the system — hundreds of rows — versus the handful the feedback list itself holds),
     * read straight off the row text and then typed into the real MRN field the normal way.</p>
     *
     * @return distinct MRNs in row order, or empty if the popup did not open / had no rows
     */
    public java.util.List<String> candidateMrnsFromPatientSearchPopup(int max) {
        return candidateMrnsFromPatientSearchPopup("", max);
    }

    /**
     * Same as {@link #candidateMrnsFromPatientSearchPopup(int)}, but filters by MRN first — e.g. a random
     * 2-3 digit fragment — so repeated runs do not all land on whichever patient an unfiltered search
     * happens to list first. Pass "" for an unfiltered search.
     *
     * <p><b>Filters through {@code PatientData.MRNo}, not {@code Search.MRNo}.</b> The popup has two
     * different MRN inputs: {@code PatientData.MRNo} is the real SERVER-SIDE search criterion that
     * {@code SearchPatient(0)} queries by (verified live: filtering "64" this way returns exactly the 6
     * patients whose MRN contains it); {@code Search.MRNo} is a CLIENT-SIDE filter applied only over
     * whichever fixed default batch (~449 rows) an unfiltered search already fetched, so it silently misses
     * any real match outside that batch — filtering "64" through it returned zero despite real matches
     * existing. Using the wrong field is exactly why an earlier version of this method returned empty for
     * some digit fragments and hundreds for others.</p>
     */
    public java.util.List<String> candidateMrnsFromPatientSearchPopup(String mrnFilter, int max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/OpenPatientSearchPopupScreen/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m=>m.offsetParent!==null)",
                null, new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(700);
        if (mrnFilter != null && !mrnFilter.isEmpty()) {
            page.evaluate("(f) => {" + JS
                    + " const e=[...document.querySelectorAll(\".modal [ng-model='PatientData.MRNo']\")].find(vis);"
                    + " if(e){ const c=A.element(e).controller('ngModel'); e.value=f; if(c){ c.$setViewValue(f); c.$render(); }"
                    + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } }", mrnFilter);
        }
        page.evaluate("() => { const b=[...document.querySelectorAll('.modal [ng-click]')].find(x=>/SearchPatient\\(0\\)/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.id='__pfPopSearch'; }");
        try { page.locator("#__pfPopSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__pfPopSearch'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal table tbody tr, .modal .ui-grid-row')].filter(r=>r.offsetParent!==null).length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("PatientFeedback.candidateMrnsFromPatientSearchPopup: no rows appeared"); }
        waitForAngular(600);
        Object mrns = page.evaluate("(max) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=[...document.querySelectorAll('.modal table tbody tr, .modal .ui-grid-row')].filter(r=>r.offsetParent!==null);"
                + " const out=[], seen=new Set();"
                + " for(const r of rows){ const t=norm(r.textContent); const m=(t.match(/^\\S+/)||[])[0];"
                + "   if(m && !seen.has(m)){ seen.add(m); out.push(m); } if(out.length>=max) break; }"
                + " return out; }", max);
        if (mrns instanceof java.util.List) for (Object o : (java.util.List<?>) mrns) out.add(String.valueOf(o));
        page.evaluate("() => { const b=[...document.querySelectorAll('.modal [ng-click]')].find(x=>/CloseModel/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(500);
        System.out.println("PatientFeedback: harvested " + out.size() + " MRN(s) from the patient search popup"
                + (mrnFilter == null || mrnFilter.isEmpty() ? "" : " (filter '" + mrnFilter + "')") + " -> " + out);
        return out;
    }

    public boolean onFeedbackForm() {
        if (page.url().toLowerCase().contains("add-patientfeedback")) return true;
        return Boolean.TRUE.equals(page.evaluate(
                "() => !![...document.querySelectorAll(\"[ng-model='" + M + "Dischargeid']\")].find(e=>e.offsetParent!==null)"));
    }

    /** Click <b>Add</b> ({@code addPatientFeedback()}); polled, as it renders after the grid. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/addPatientFeedback/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__pfAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("PatientFeedback.clickAdd: Add button not found"); return false; }
        try { page.locator("#__pfAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PatientFeedback.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pfAdd'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='" + M + "MRNo']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("PatientFeedback.clickAdd: feedback form did not render"); }
        waitForAngular(1200);
        return onFeedbackForm();
    }

    /** Diagnostics: every visible select (with option count) and input, by label + ng-model. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                // include the first option texts: this screen leaves its labels empty, so the option
                // contents are the only way to tell a template list from a discharge/visit list.
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length"
                + "     +' {'+[...e.options].slice(0,5).map(o=>norm(o.textContent)).join(' / ')+'}');"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " return sel.concat(inp).join(' || '); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("PatientFeedback controls: " + lastControls);
        return lastControls;
    }

    // ---- MRN + search ------------------------------------------------------

    /**
     * Enter the <b>MRN No.</b> ({@code PatientFeedback.MRNo}) and click search
     * ({@code SearchPatientByMRNo()}).
     *
     * <p>Success is judged on the form gaining controls: this screen has no patient-name field to watch,
     * so it waits for the number of visible selects to grow (the template/visit dropdowns the patient
     * brings with them) and reports what appeared.</p>
     */
    public String searchByMrn(String mrn) {
        lastMrn = mrn;
        Object before = page.evaluate("() => document.querySelectorAll('select').length");
        int selectsBefore = before == null ? 0 : Integer.parseInt(before.toString());

        page.evaluate("(m) => {" + JS
                + " setInp('" + M + "MRNo', m);"
                + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__pfMrnSearch'; }", mrn);
        try { page.locator("#__pfMrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PatientFeedback.searchByMrn: search click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pfMrnSearch'); if(e) e.removeAttribute('id'); }");

        try {
            page.waitForFunction("(n) => document.querySelectorAll('select').length > n",
                    selectsBefore, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(1200);

        // This screen renders NOTHING when a patient loads, so the DOM cannot tell us whether the search
        // worked. Read the AngularJS model instead — scope.PatientFeedback is what Save posts, so its
        // scalar fields (patient id / name / discharge) are the real evidence.
        Object r = page.evaluate("(n) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>e && e.offsetParent!==null;"
                + " const selects=document.querySelectorAll('select').length;"
                + " const msg=[...document.querySelectorAll('.toast-message,.toast')].map(x=>norm(x.textContent)).join(' ');"
                + " let model='(no scope)';"
                + " try { const e=[...document.querySelectorAll(\"[ng-model='PatientFeedback.MRNo']\")].find(vis);"
                + "   const s=window.angular.element(e).scope();"
                + "   const o=s && s.PatientFeedback;"
                + "   if(o){ const keys=Object.keys(o).filter(k=>o[k]!==null && o[k]!=='' && typeof o[k]!=='object'"
                + "            && typeof o[k]!=='function');"
                + "     model=keys.length? keys.map(k=>k+'='+String(o[k]).slice(0,30)).join(', ') : '(model empty)'; } }"
                + " catch(err) { model='(scope read failed: '+err.message+')'; }"
                + " return 'newSelects='+(selects-n)+' | model: '+model+(msg?' | msg='+msg:''); }", selectsBefore);
        lastSearchResult = r == null ? "" : r.toString();
        System.out.println("PatientFeedback: search MRN " + mrn + " -> " + lastSearchResult);
        return lastSearchResult;
    }

    /**
     * True when the MRN search was not rejected.
     *
     * <p>This screen gives no positive confirmation to assert on — it renders no patient-name field and
     * adds no controls, so "patient loaded" cannot be proved from the DOM. What it DOES do is raise a
     * toast when the MRN is bad. So the check is negative: a rejection message fails the step, anything
     * else passes and the Save step becomes the real verification.</p>
     */
    public boolean patientLoaded() {
        if (lastSearchResult == null || lastSearchResult.isEmpty()) return false;
        String s = lastSearchResult.toLowerCase();
        return !(s.contains("no record") || s.contains("not found") || s.contains("no data")
                || s.contains("invalid") || s.contains("does not exist") || s.contains("enter mr"));
    }

    /**
     * True when the loaded patient's {@code isdischarged} flag is set.
     *
     * <p><b>Required, not optional — verified live.</b> A patient with {@code isdischarged=false} still
     * lets the form load, still lets Save be clicked, and Save still raises a genuine, unambiguous
     * "Patient feedback saved successfully." toast — but re-checking the list (same session, immediately
     * after) shows NO new row: nothing was actually persisted. {@link #harvestMrnsFromList()} sidesteps this
     * by construction (those patients already have feedback on file, which implies they were discharged
     * when it was recorded), but any other MRN source — including
     * {@link #candidateMrnsFromPatientSearchPopup(String, int)} — can hand back a patient who is not, so
     * this must be checked before trusting a save on such a patient.</p>
     */
    public boolean patientDischarged() {
        Object r = page.evaluate("() => { const vis=e=>e && e.offsetParent!==null;"
                + " const e=[...document.querySelectorAll(\"[ng-model='" + M + "MRNo']\")].find(vis);"
                + " try { const s=window.angular.element(e).scope(); const o=s && s.PatientFeedback;"
                + "   return !!(o && o.isdischarged); } catch(err) { return false; } }");
        return Boolean.TRUE.equals(r);
    }

    // ---- template ----------------------------------------------------------

    /**
     * Select the <b>patient feedback template</b>.
     *
     * <p>Matched by {@code ng-model} or label naming "template"/"feedback"; if the screen exposes only one
     * populated select besides {@code Dischargeid}, that is used instead. Returns {@code (not-found)} when
     * no candidate exists, so the step fails loudly rather than silently skipping the template.</p>
     */
    public String selectTemplate() { return selectTemplate(null); }

    /**
     * Select the feedback template, preferring the option whose text contains {@code preferText}
     * (case-insensitive). Pass null to take the first real option.
     */
    public String selectTemplate(String preferText) {
        if (preferText != null && !preferText.isBlank()) {
            Object r0 = page.evaluate("(want) => {" + JS
                    + " const sels=[...document.querySelectorAll('select')].filter(vis)"
                    + "   .filter(x=>!/pagination/i.test(x.getAttribute('ng-model')||''));"
                    + " const e=sels.find(x=>[...x.options].some(o=>new RegExp(String(want),'i').test(norm(o.textContent))));"
                    + " if(!e) return '(no-match)';"
                    + " const i=[...e.options].findIndex(o=>new RegExp(String(want),'i').test(norm(o.textContent)));"
                    + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                    + " if($){ try{ $(e).trigger('change'); }catch(x){} }"
                    + " return norm(e.options[i].textContent)+' ['+(e.getAttribute('ng-model')||'?')+']'; }", preferText);
            String picked = r0 == null ? "" : r0.toString();
            if (!picked.startsWith("(")) {
                lastTemplate = picked;
                waitForAngular(800);
                System.out.println("PatientFeedback: Template = " + lastTemplate);
                return lastTemplate;
            }
            System.out.println("PatientFeedback: no template matching \"" + preferText + "\" — using the first option");
        }
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const sels=[...document.querySelectorAll('select')].filter(vis)"
                + "   .filter(x=>!/pagination/i.test(x.getAttribute('ng-model')||''));"
                + " const attrs=e=>(e.getAttribute('ng-model')||'')+' '+(e.getAttribute('id')||'')+' '+(e.getAttribute('name')||'');"
                // Dischargeid IS the template list on this screen (verified live: its options are
                // "Patient Diet Feedback", "Emergency Department Feedback", ...) — the ng-model name is
                // misleading and the label is empty, so match on the OPTION TEXT as well.
                + " const optText=e=>[...e.options].map(o=>norm(o.textContent)).join(' ');"
                + " let e=sels.find(x=>/template/i.test(attrs(x)) || /template/i.test(labelOf(x)));"
                + " if(!e) e=sels.find(x=>/feedback/i.test(optText(x)));"
                + " if(!e) e=sels.find(x=>/Dischargeid/i.test(attrs(x)));"
                + " if(!e) e=sels.find(x=>x.options.length>1);"
                + " if(!e) { resolve('(not-found)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " const v=await pickEl(e);"
                + " resolve(v+' '+which); })");
        lastTemplate = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("PatientFeedback: Template = " + lastTemplate);
        return lastTemplate;
    }

    public boolean templateSelected() {
        return lastTemplate != null && !lastTemplate.isEmpty() && !lastTemplate.startsWith("(");
    }

    /**
     * Read selected fields of the AngularJS {@code PatientFeedback} model — the object Save posts.
     *
     * <p>The screen renders almost nothing, so this is the only way to see whether a selection actually
     * bound. Pass a regex to filter keys, e.g. {@code "discharge|template|mrno"}.</p>
     */
    public String modelSnapshot(String keyRegex) {
        Object r = page.evaluate("(rx) => { const vis=e=>e && e.offsetParent!==null;"
                + " try { const e=[...document.querySelectorAll(\"[ng-model='PatientFeedback.MRNo']\")].find(vis);"
                + "   const s=window.angular.element(e).scope(); const o=s && s.PatientFeedback;"
                + "   if(!o) return '(no model)';"
                + "   const re=new RegExp(rx,'i');"
                + "   const keys=Object.keys(o).filter(k=>re.test(k) && typeof o[k]!=='object' && typeof o[k]!=='function');"
                + "   return keys.length? keys.map(k=>k+'='+String(o[k]).slice(0,40)).join(', ') : '(no matching keys)'; }"
                + " catch(err) { return '(scope read failed: '+err.message+')'; } }", keyRegex);
        String out = r == null ? "" : r.toString();
        System.out.println("PatientFeedback model[" + keyRegex + "]: " + out);
        return out;
    }

    // ---- save --------------------------------------------------------------

    /** Click <b>Save</b> ({@code IUDPatientFeedbackDetail()}) and return the toast ("" if none). */
    public String saveAndGetToast() {
        waitForToastsToClear();

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/IUDPatientFeedbackDetail/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__pfSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("PatientFeedback.save: Save button not found"); return ""; }

        try { page.locator("#__pfSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PatientFeedback.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pfSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(700);

        acceptSaveDialog();
        waitForAngular(400);

        try {
            page.waitForFunction("() => (window.__pfToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__pfToasts||[]).includes(t)) (window.__pfToasts=window.__pfToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__pfToasts||[];"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Back</b> ({@code closeForm()}) to return to the Patient Feedback list. */
    public boolean clickBack() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/closeForm/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*back\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__pfBack'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("PatientFeedback.clickBack: Back button not found"); return false; }
        try { page.locator("#__pfBack").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PatientFeedback.clickBack: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pfBack'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        return onListScreen();
    }

    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("PatientFeedback: a previous toast is still on screen — the next capture may be stale");
        }
    }

    // ---- verifying the new row --------------------------------------------

    /** What {@link #listSnapshot} last saw, for the report. */
    public String lastListSnapshot = "";

    /**
     * Read the feedback list: how many rows it holds in total, and how many belong to this MRN.
     *
     * <p>Called once, right after Save, to confirm the just-created record is actually in the table.</p>
     *
     * <p>Returns to the list and runs the screen's own search first, filtered From TODAY To TODAY, since
     * the record this verifies was just saved moments ago.</p>
     *
     * @return {@code {totalRows, rowsForThisMrn}}
     */
    public int[] listSnapshot(String mrn) {
        if (!onListScreen()) {
            try { page.evaluate("() => { window.location.hash = '#/PatientFeedbackList'; }"); }
            catch (Exception ignore) { }
            waitForAngular(3000);
        }
        page.evaluate("() => {" + JS
                + " const today=new Date(); const pad=n=>String(n).padStart(2,'0');"
                + " const fmt=d=>pad(d.getDate())+'/'+pad(d.getMonth()+1)+'/'+d.getFullYear();"
                + " setInp('PatientFeedbackList.fromdate', fmt(today));"
                + " setInp('PatientFeedbackList.todate', fmt(today));"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/fnSearchFeedbackList/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__pfSnapSearch'; }");
        try { page.locator("#__pfSnapSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PatientFeedback.listSnapshot: search click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pfSnapSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(3000);

        // Read the list's OWN grid from the DOM. Scope-scanning picked the wrong array twice: any array
        // whose rows carry an MRNO also matches the feedback TEMPLATE's field definitions (fieldname /
        // parametername / bindingcontrolname), and that array never changes no matter what is saved.
        // The rendered grid under the visible headers is unambiguous, and this list is small enough that
        // ui-grid's virtualisation does not hide rows; the pager's own total is read as a cross-check.
        Object r = page.evaluate("(mrn) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                + " const heads=[...document.querySelectorAll('.ui-grid-header-cell')].filter(vis)"
                + "   .map(h=>norm(h.textContent)).filter(t=>t);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(vis)"
                + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                + " const mine=rows.filter(t=>t.includes(String(mrn).trim()));"
                + " const pager=norm((document.body.innerText.match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items/i)||[''])[0]);"
                + " const sample=mine.length? mine[mine.length-1].slice(0,120)"
                + "   : 'columns: '+heads.join(' | ')+(pager? '; pager: '+pager : '');"
                + " return rows.length+'|'+mine.length+'|'+sample; }", mrn);
        String s = r == null ? "0|0|" : r.toString();
        String[] p = s.split("\\|", 3);
        int total = 0, mine = 0;
        try { total = Integer.parseInt(p[0].trim()); } catch (Exception ignore) { }
        try { mine = Integer.parseInt(p[1].trim()); } catch (Exception ignore) { }
        // Record the list's OWN filters too: a row can be missing because a filter excludes it, and the
        // report should show which filters were in force rather than leave that open.
        Object filters = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                + " return [...document.querySelectorAll('select,input')].filter(vis)"
                + "   .filter(e=>!/colFilter|pagination/i.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>(e.getAttribute('ng-model')||'?')+'='"
                + "     +(e.tagName==='SELECT'? norm((e.options[e.selectedIndex]||{}).text||'') : e.value))"
                + "   .join(', '); }");

        lastListSnapshot = total + " row(s) in the list, " + mine + " for MRN " + mrn
                + (p.length > 2 && !p[2].isEmpty() ? "; newest for this MRN: " + p[2] : "")
                + (filters == null || filters.toString().isEmpty() ? "" : "; filters in force: " + filters);
        System.out.println("PatientFeedback: list snapshot -> " + lastListSnapshot);
        return new int[] { total, mine };
    }
}
