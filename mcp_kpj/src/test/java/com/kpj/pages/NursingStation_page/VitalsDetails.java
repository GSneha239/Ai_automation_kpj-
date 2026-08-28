package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Vitals Details</b> — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>Vitals Details</b> ({@code #/VitalsDetails}) → enter <b>MRN</b> +
 * <b>Search</b> → in <b>Add Vital Details</b> tick the <b>Select</b> checkboxes, enter <b>Value</b> and
 * <b>Remarks</b> → select <b>Taken By</b> → <b>Telemedicine Import</b> → <b>Save</b> → toast.</p>
 *
 * <p><b>Fields (discovered live 2026-08-10):</b> {@code VitalsDetails.MRNo} with a
 * {@code SearchPatientByMRNo()} icon and a {@code VitalSearch()} Search button;
 * {@code VitalsDetails.takenbyid} (Taken By — empty until the search runs, then ~311 options);
 * {@code VitalsDetails.date} / {@code VitalsDetails.dates} and {@code inputTime}. Buttons:
 * {@code GetQueedMessages()} (Telemedicine Import), {@code addselectedvitalsdetails()} (Save),
 * {@code CancelVital()} (Clear), {@code closeForm()} (Back).</p>
 *
 * <h2>The vitals list is an ng-repeat — every row shares the same ng-models</h2>
 * <p>Each vital row carries {@code list.IsSelecteddata} (the Select checkbox), {@code list.defaultvalue}
 * (Value) and {@code list.vdremark} (Remarks). With ~15 rows on screen, those model names are NOT unique —
 * targeting them by ng-model would always hit the first row. So every row operation here resolves the
 * checkbox first and then works <i>within that row's own {@code <tr>}</i>, which is the only way the Value
 * and Remarks land against the vital that was actually ticked.</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class VitalsDetails extends BasePage {

    public VitalsDetails(Page page) { super(page); }

    public static final String ROUTE = "#/VitalsDetails";
    private static final String M = "VitalsDetails.";

    public String lastControls = "";
    public String lastMrn = "", lastSearchResult = "";
    public String lastSelectedRows = "", lastValues = "", lastRemarks = "";
    public String lastTakenBy = "", lastImportResult = "";
    public int selectedRowCount = 0;

    // ---- shared JS ---------------------------------------------------------

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const A=window.angular; const $=window.jQuery;"
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "const setEl=(e,v)=>{ if(!e) return '(no-field)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} } return v; };"
            + "const setInp=(ng,v)=>setEl(byNg(ng), v);"
            + "const pick=async(ng)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  for(let k=0;k<15;k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };"
            // every ticked row, in document order
            + "const tickedRows=()=>[...document.querySelectorAll(\"input[type=checkbox][ng-model='list.IsSelecteddata']\")]"
            + "  .filter(vis).filter(c=>c.checked).map(c=>c.closest('tr')).filter(r=>r);";

    /**
     * Collect toasts from the NARROWEST elements available.
     *
     * <p>Querying {@code '.toast-message,.toast,[id^=toast]'} together matches the container as well as
     * its children, so two separate messages come back as one concatenated blob — which is how a Save
     * result ended up reading "Patient Vital Details added successfully.×KPJ PortalError in getting
     * record!". Taking {@code .toast-message} first keeps each message a distinct entry, so they can be
     * told apart and filtered.</p>
     */
    private static final String TOAST_ELS = ""
            // self-contained: this is injected into evaluates that do not all define `vis`
            + "const _tv=e=>e && e.offsetParent!==null;"
            + "const toastEls=()=>{ let n=[...document.querySelectorAll('.toast-message')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('.toast')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('[id^=toast]')].filter(_tv);"
            + "  return n; };";

    private static final String ARM_TOASTS = TOAST_ELS
            + " window.__vdToasts=[]; if(window.__vdObs) window.__vdObs.disconnect();"
            + " const grab=()=>{ toastEls().forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__vdToasts.includes(t)) window.__vdToasts.push(t); }); };"
            + " window.__vdObs=new MutationObserver(grab); window.__vdObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation --------------------------------------------------------

    /** <b>Nursing Station</b> → <b>Vitals Details</b>, else the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("VitalsDetails.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1200);

        // Vitals Details is a LEAF link — clicking it navigates straight away.
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^vitals?\\s*details?$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/VitalsDetails/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__vdMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__vdMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("VitalsDetails.nav: menu click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("VitalsDetails.nav: falling back to direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='" + M + "MRNo']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("VitalsDetails.nav: screen did not finish rendering"); }
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("vitalsdetails");
    }

    /** Diagnostics: non-repeating controls plus the vitals row count. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||''); };"
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/list\\.|colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+']');"
                + " const rows=[...document.querySelectorAll(\"input[type=checkbox][ng-model='list.IsSelecteddata']\")].filter(vis).length;"
                + " return 'vitalRows='+rows+' || '+sel.concat(inp).join(' || '); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("VitalsDetails controls: " + lastControls);
        return lastControls;
    }

    // ---- step 1: MRN + search ----------------------------------------------

    /**
     * Enter the <b>MRN</b> and search.
     *
     * <p>Two controls are involved: the magnifier beside the MRN box ({@code SearchPatientByMRNo()}) which
     * resolves the patient, and the <b>Search</b> button ({@code VitalSearch()}) which loads the vitals
     * list. Both are clicked, in that order. Success is judged on the screen coming to life — the Taken By
     * list populating (it starts empty) and/or vital rows appearing.</p>
     */
    public String searchByMrn(String mrn) {
        lastMrn = mrn;

        page.evaluate("(m) => {" + JS
                + " setInp('" + M + "MRNo', m);"
                + " const b=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')]"
                + "   .find(x=>/SearchPatientByMRNo/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__vdMrnSearch'; }", mrn);
        try { page.locator("#__vdMrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("VitalsDetails.searchByMrn: MRN icon click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vdMrnSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/VitalSearch/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*search\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(b) b.id='__vdSearch'; }");
        try { page.locator("#__vdSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("VitalsDetails.searchByMrn: Search click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vdSearch'); if(e) e.removeAttribute('id'); }");

        // Wait for the screen to populate rather than guessing a delay.
        try {
            page.waitForFunction("() => { const t=document.querySelector(\"[ng-model='" + M + "takenbyid']\");"
                    + " const rows=document.querySelectorAll(\"input[type=checkbox][ng-model='list.IsSelecteddata']\").length;"
                    + " return (t && t.options.length > 1) || rows > 0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(800);

        Object r = page.evaluate("() => {" + JS
                + " const t=byNg('" + M + "takenbyid');"
                + " const rows=[...document.querySelectorAll(\"input[type=checkbox][ng-model='list.IsSelecteddata']\")].filter(vis).length;"
                + " const msg=[...document.querySelectorAll('.toast-message,.toast')].map(x=>norm(x.textContent)).join(' ');"
                + " return 'vitalRows='+rows+' takenByOptions='+(t?t.options.length:-1)+(msg?' | msg='+msg:''); }");
        lastSearchResult = r == null ? "" : r.toString();
        System.out.println("VitalsDetails: search MRN " + mrn + " -> " + lastSearchResult);
        return lastSearchResult;
    }

    /** True when the search brought the screen to life (vital rows and/or a populated Taken By list). */
    public boolean patientLoaded() {
        if (lastSearchResult == null || lastSearchResult.isEmpty()) return false;
        java.util.regex.Matcher rows = java.util.regex.Pattern.compile("vitalRows=(\\d+)").matcher(lastSearchResult);
        java.util.regex.Matcher opts = java.util.regex.Pattern.compile("takenByOptions=(-?\\d+)").matcher(lastSearchResult);
        boolean hasRows = rows.find() && Integer.parseInt(rows.group(1)) > 0;
        boolean hasTakenBy = opts.find() && Integer.parseInt(opts.group(1)) > 1;
        return hasRows || hasTakenBy;
    }

    // ---- step 2: tick the Select checkboxes --------------------------------

    /**
     * Tick the <b>Select</b> checkbox on the first {@code count} vital rows.
     *
     * <p>Rows are addressed positionally, not by ng-model — {@code list.IsSelecteddata} repeats once per
     * row, so a model lookup would only ever reach row one.</p>
     *
     * @return which rows were ticked, with the vital name from each row
     */
    public String selectVitalRows(int count) {
        Object r = page.evaluate("(n) => {" + JS
                + " const boxes=[...document.querySelectorAll(\"input[type=checkbox][ng-model='list.IsSelecteddata']\")].filter(vis);"
                + " const picked=[];"
                + " for(const b of boxes){ if(picked.length>=n) break;"
                + "   if(!b.checked) b.click();"
                + "   const row=b.closest('tr');"
                + "   picked.push(row? norm(row.textContent).slice(0,32) : '(row?)'); }"
                + " return 'ticked='+picked.length+'/'+boxes.length+' | '+picked.join(' ; '); }", count);
        lastSelectedRows = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("ticked=(\\d+)").matcher(lastSelectedRows);
        selectedRowCount = m.find() ? Integer.parseInt(m.group(1)) : 0;
        waitForAngular(600);
        System.out.println("VitalsDetails: " + lastSelectedRows);
        return lastSelectedRows;
    }

    public boolean rowsSelected() { return selectedRowCount > 0; }

    // ---- step 3: values ----------------------------------------------------

    /**
     * Enter the <b>Value</b> on every ticked row.
     *
     * <p>Each value is written into its own row's {@code list.defaultvalue} input, found via the ticked
     * checkbox's {@code <tr>} — not by ng-model, which would put every value in row one.</p>
     */
    public String enterValues(String value) {
        Object r = page.evaluate("(v) => {" + JS
                + " const rows=tickedRows(); const done=[];"
                + " for(const row of rows){"
                + "   const e=[...row.querySelectorAll(\"input[ng-model='list.defaultvalue']\")].find(vis);"
                + "   done.push(e? setEl(e, v) : '(no-value-field)'); }"
                + " return 'values='+done.length+' | '+done.join(' ; '); }", value);
        lastValues = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("VitalsDetails: " + lastValues);
        return lastValues;
    }

    public boolean valuesEntered() {
        return lastValues != null && lastValues.startsWith("values=") && !lastValues.startsWith("values=0")
                && !lastValues.contains("(no-value-field)");
    }

    // ---- step 4: remarks ---------------------------------------------------

    /** Enter the <b>Remarks</b> on every ticked row ({@code list.vdremark}, resolved per row). */
    public String enterRemarks(String remark) {
        Object r = page.evaluate("(v) => {" + JS
                + " const rows=tickedRows(); const done=[];"
                + " for(const row of rows){"
                + "   const e=[...row.querySelectorAll(\"input[ng-model='list.vdremark']\")].find(vis);"
                + "   done.push(e? setEl(e, v) : '(no-remark-field)'); }"
                + " return 'remarks='+done.length+' | '+done.join(' ; '); }", remark);
        lastRemarks = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("VitalsDetails: " + lastRemarks);
        return lastRemarks;
    }

    public boolean remarksEntered() {
        return lastRemarks != null && lastRemarks.startsWith("remarks=") && !lastRemarks.startsWith("remarks=0")
                && !lastRemarks.contains("(no-remark-field)");
    }

    // ---- step 5: taken by --------------------------------------------------

    /** Select <b>Taken By</b> ({@code VitalsDetails.takenbyid}) — polled, it fills after the search. */
    public String selectTakenBy() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('" + M + "takenbyid')); })");
        lastTakenBy = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("VitalsDetails: Taken By = " + lastTakenBy);
        return lastTakenBy;
    }

    public boolean takenBySelected() { return isReal(lastTakenBy); }

    // ---- step 6: telemedicine import ---------------------------------------

    /**
     * Click <b>Telemedicine Import</b> ({@code GetQueedMessages()}) and report what it produced — a toast,
     * a popup, or nothing.
     */
    public String clickTelemedicineImport() {
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/GetQueedMessages/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/telemedicine\\s*import/i.test(norm(x.textContent||x.value)));"
                + " if(b) b.id='__vdImport'; }");
        try { page.locator("#__vdImport").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("VitalsDetails.import: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vdImport'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2500);

        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const toast=(window.__vdToasts||[]).join(' ');"
                + " const modal=[...document.querySelectorAll('.modal,.modal-dialog,.jAlert,[role=dialog]')]"
                + "   .filter(e=>e.offsetParent!==null).map(e=>norm(e.textContent)).filter(t=>t);"
                + " const rows=document.querySelectorAll(\"input[type=checkbox][ng-model='list.IsSelecteddata']\").length;"
                + " return 'rows='+rows+(toast?' | toast='+toast:'')+(modal.length?' | popup='+modal[0].slice(0,120):''); }");
        lastImportResult = r == null ? "" : r.toString();
        System.out.println("VitalsDetails: Telemedicine Import -> " + lastImportResult);
        return lastImportResult;
    }

    /**
     * True when Telemedicine Import did NOT report a failure.
     *
     * <p>A response of any kind is not success: this button answers
     * "×KPJ PortalError in getting record!" on devhis, and judging the step merely on "something came
     * back" turns that error into a green tick.</p>
     */
    public boolean telemedicineImportOk() {
        if (lastImportResult == null || lastImportResult.isEmpty()) return false;
        String t = lastImportResult.toLowerCase();
        return !(t.contains("error") || t.contains("failed") || t.contains("fail!")
                || t.contains("not found") || t.contains("no record"));
    }

    // ---- step 7: save ------------------------------------------------------

    /** Click <b>Save</b> ({@code addselectedvitalsdetails()}) and return the toast ("" if none). */
    public String saveAndGetToast() {
        waitForToastsToClear();

        // Belt and braces. Waiting for a clear DOM is the primary guard, but the Telemedicine Import error
        // toast can outlive that wait — which produced a Save result reading
        // "Patient Vital Details added successfully.×KPJ PortalError in getting record!". So snapshot
        // whatever is STILL on screen and exclude those exact texts from this capture. (The DOM-clear wait
        // stays first because text exclusion alone would also suppress a genuine repeat of the same
        // message.)
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                // Arming grabs whatever is already on screen; that snapshot IS the prior set to exclude.
                + " window.__vdPrior=(window.__vdToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/addselectedvitalsdetails/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__vdSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("VitalsDetails.save: Save button not found"); return ""; }

        try { page.locator("#__vdSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("VitalsDetails.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vdSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);

        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__vdToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__vdToasts||[]).includes(t)) (window.__vdToasts=window.__vdToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const prior=window.__vdPrior||[];"
                + " const a=(window.__vdToasts||[]).filter(x=>!prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("VitalsDetails: a previous toast is still on screen — the next capture may be stale");
        }
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }
}
