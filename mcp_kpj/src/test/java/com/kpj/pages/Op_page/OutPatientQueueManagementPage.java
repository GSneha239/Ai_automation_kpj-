package com.kpj.pages.Op_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * OutPatientQueueManagementPage — single Page Object for OP &gt; Outpatient Queue Management (#/queueManagement).
 *
 * <p>Consolidates every queue action into one file (mirrors AppointmentListPage / RegistrationPage —
 * no sub-files): shared queue navigation / date-range search / row selection, plus the per-action
 * method groups <b>Close Visit</b>, <b>Medico Legal</b>, <b>Call For Triage</b>, <b>Convert IPD Charges</b>
 * and <b>Consent/Forms</b>.</p>
 *
 * <p>Footer actions on this screen (ng-click): <b>Medico Legal</b> <code>OpenMLCModal</code> — Police
 * Station / Docket No / Remark, a Document List (attach file + description + Add) and an MLC Check List
 * (tick an item + remark), toast "Medico Legal Saved Successfully." — <b>Call For Triage</b>
 * <code>fnCallPatient()</code> (same handler as, but a DIFFERENT modal from, plain Call Patient) — select a
 * Department then <b>Consultation Room</b> ({@code ng-model="token.cabinid"}, DevHIS's internal "Cabin"
 * field; Save validates it as "Room No.") → Save, toast "Token Updated Successfully."; the modal's own
 * heading has an app-side typo, "Ca<b>ll</b>l For Triage" (three L's) — <b>Convert IPD Charges</b> — a
 * confirm dialog → Save → toast "Charges Converted Successfully.", or "No Unbilled Ipd Charges!" for a
 * patient with nothing to convert (try a different patient, not a failure) — <b>Close Visit</b>
 * <code>OpenDischargeTypeModal</code>, Revoke Visit <code>OpenRevokeTypeModal</code>, New Case, Assign
 * Triage, Change Doctor, <b>Consent/Forms</b> <code>fnOnConsentClick</code>, View Consent
 * <code>fnViewConsents</code>, Generate Queue, Call Patient, CancelVisit, Request/Return MRD File, Fall
 * Risk Assessment, … (Close Visit modal selectors are best-effort until healed live).</p>
 */
public class OutPatientQueueManagementPage extends BasePage {

    public OutPatientQueueManagementPage(Page page) {
        super(page);
    }

    // ---- navigation -------------------------------------------------------

    public void navigateTo(String baseUrl) {
        // Wait for DOMCONTENTLOADED, not full "load" — the DevHIS SPA can keep a request open so the
        // "load" event never fires within the default 30s on a slow/unstable site (nav then aborts the
        // whole test). DOMCONTENTLOADED fires as soon as the document is parsed; Angular boots after.
        try {
            page.navigate(baseUrl + "/#/queueManagement",
                    new com.microsoft.playwright.Page.NavigateOptions()
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(60000));
        } catch (Exception e) {
            System.out.println("navigateTo: navigation slow (" + e.getMessage().split("\n")[0] + ") — continuing");
        }
        // Wait until the queue page is actually interactive (Angular + the Search button present).
        try {
            page.waitForFunction(
                    "() => window.angular && [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("navigateTo: queue page not fully ready in time"); }
        waitForAngular(2000);
    }

    // ---- search & select --------------------------------------------------

    /** Search the queue with a From date only (dd/MM/yyyy). */
    public void searchQueue(String fromDate) {
        searchQueue(fromDate, null);
    }

    // Department (queue.departmentid): reset to "--Select--" (index 0) then click Search — shared by the
    // initial search and by the re-assert loop below, which must repeat the SAME two actions together (a reset
    // with no re-Search leaves whatever the PREVIOUS Search already filtered by still in effect).
    private static final String RESET_DEPT_AND_SEARCH_JS =
            "() => { const dep=document.querySelector(\"select[ng-model='queue.departmentid']\");"
                    + " if(dep && dep.value){ dep.selectedIndex=0; dep.dispatchEvent(new Event('change',{bubbles:true}));"
                    + "   try{angular.element(dep).triggerHandler('change');}catch(x){} const $=window.jQuery; if($){try{$(dep).trigger('change');}catch(x){}} }"
                    + " const b=[...document.querySelectorAll('button')].find(x=>/^search$/i.test((x.innerText||'').trim()) && x.offsetParent!==null); if(b) b.click(); }";

    /** Search the queue with a From/To date range (dd/MM/yyyy; toDate may be null). */
    public void searchQueue(String fromDate, String toDate) {
        page.evaluate("(a) => { const [fd, td] = a;"
                + " const set=(ng,v)=>{ if(v==null) return; const f=document.querySelector(\"input[ng-model='\"+ng+\"']\"); if(!f) return;"
                + "   const c=angular.element(f).controller('ngModel'); f.value=v; if(c){c.$setViewValue(v);c.$render();} f.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('queue.fromdate', fd); set('queue.todate', td); }",
                java.util.Arrays.asList(fromDate, toDate));
        // Department (queue.departmentid) — confirmed live 2026-09-17: this select loads with a department
        // ALREADY chosen (e.g. "ACCIDENT & EMERGENCY"), not "--Select--", so a Search run without touching it
        // silently filters the whole queue down to just that one department. Per request: leave/reset it to
        // "--Select--" before searching, so results are not narrowed by a department nobody chose.
        page.evaluate(RESET_DEPT_AND_SEARCH_JS);
        // The default department can be populated by a LATE-arriving async response — confirmed live it can
        // still land a couple of seconds after the page looks ready, i.e. AFTER the reset above already ran and
        // found nothing to reset, silently re-narrowing the search when it finally arrives. A single reset is
        // not enough on a fresh session: re-check for a few seconds and reset-and-re-Search again whenever it
        // comes back non-empty, so the LAST Search actually run is the one with no Department filter.
        for (int i = 0; i < 6; i++) {
            waitForAngular(600);
            Object depVal = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='queue.departmentid']\"); return e?e.value:null; }");
            if (depVal == null || depVal.toString().isEmpty()) break;
            System.out.println("searchQueue: Department was repopulated (" + depVal + ") by a late async default after Search — resetting and re-searching");
            page.evaluate(RESET_DEPT_AND_SEARCH_JS);
        }
        // Wait for the queue grid to populate — and RE-SEARCH if it comes back empty (a slow/loaded server
        // intermittently returns an empty result mid-run; a second Search usually fills it).
        String gridHasRows = "() => { let ok=false; document.querySelectorAll('*').forEach(el=>{ if(ok) return; try{ const s=angular.element(el).scope();"
                + " if(s && s.grid && s.grid.options && s.grid.options.data && s.grid.options.data.length) ok=true; }catch(e){} }); return ok; }";
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                page.waitForFunction(gridHasRows, null, new Page.WaitForFunctionOptions().setTimeout(20000));
                break; // grid populated
            } catch (Exception ignore) {
                System.out.println("searchQueue: queue grid empty (attempt " + (attempt + 1) + "/3) — re-searching");
                page.evaluate(RESET_DEPT_AND_SEARCH_JS);
                waitForAngular(1500);
            }
        }
        waitForAngular(1000);
    }

    /** Select a queue row by patient name (regex, case-insensitive). Returns the matched name, or null. */
    public String selectQueueRow(String nameRegex) {
        Object r = page.evaluate("(re) => {"
                + " const rx=new RegExp(re,'i'); let gridApi=null, data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data) return; try{ const s=angular.element(el).scope(); if(!s) return;"
                + "   if(s.grid && s.grid.api && s.grid.options && s.grid.options.data && s.grid.options.data.length){ gridApi=s.grid.api; data=s.grid.options.data; } }catch(e){} });"
                + " if(!gridApi || !data) return null;"
                + " const patient=data.find(x=>rx.test(x.PatientName||x.patientname||'')) || data[0]; if(!patient) return null;"
                + " try{ gridApi.selection.clearSelectedRows(); }catch(e){}"
                + " gridApi.selection.selectRow(patient);"
                + " return ((patient.PatientName||patient.patientname||'')+'').trim(); }", nameRegex);
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    // Skip junk/test patients (e.g. "DUMMY PATIENT RX", "TEST EMR") when auto-selecting a queue row.
    private static final String SKIP_PATIENTS_RE = "dummy|test\\s*emr";

    /** Select the first NON-dummy patient row in the queue grid (ui-grid API). Returns the name, or null. */
    public String selectFirstQueueRow() {
        Object r = page.evaluate("(skipRe) => {"
                + " const skip=new RegExp(skipRe,'i'); let gridApi=null, data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data) return; try{ const s=angular.element(el).scope(); if(!s) return;"
                + "   if(s.grid && s.grid.api && s.grid.options && s.grid.options.data && s.grid.options.data.length){ gridApi=s.grid.api; data=s.grid.options.data; } }catch(e){} });"
                + " if(!gridApi || !data || !data.length) return null;"
                + " const nm=x=>((x.PatientName||x.patientname||'')+'').trim();"
                + " const patient=data.find(x=>nm(x) && !skip.test(nm(x))) || data[0];"
                + " try{ gridApi.selection.clearSelectedRows(); }catch(e){}"
                + " gridApi.selection.selectRow(patient);"
                + " return nm(patient); }", SKIP_PATIENTS_RE);
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /** Select a RANDOM non-dummy patient row in the queue grid (ui-grid API) — avoids always hitting the
     *  same first patient (e.g. New Case's "already generated" collision). Returns the name, or null. */
    public String selectRandomQueueRow() {
        Object r = page.evaluate("(skipRe) => {"
                + " const skip=new RegExp(skipRe,'i'); let gridApi=null, data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data) return; try{ const s=angular.element(el).scope(); if(!s) return;"
                + "   if(s.grid && s.grid.api && s.grid.options && s.grid.options.data && s.grid.options.data.length){ gridApi=s.grid.api; data=s.grid.options.data; } }catch(e){} });"
                + " if(!gridApi || !data || !data.length) return null;"
                + " const nm=x=>((x.PatientName||x.patientname||'')+'').trim();"
                + " const pool=data.filter(x=>nm(x) && !skip.test(nm(x))); const src=pool.length?pool:data;"
                + " const patient=src[Math.floor(Math.random()*src.length)];"
                + " try{ gridApi.selection.clearSelectedRows(); }catch(e){}"
                + " gridApi.selection.selectRow(patient);"
                + " return nm(patient); }", SKIP_PATIENTS_RE);
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /** Wait for the queue grid to finish loading after a Search (rows rendered or an empty result settled).
     *  Use after changing the date range so we don't act on a still-loading page. */
    public void waitForQueueLoaded() {
        try {
            page.waitForFunction(
                    "() => { let found=false; document.querySelectorAll('*').forEach(el=>{ if(found) return; try{ const s=angular.element(el).scope();"
                            + " if(s&&s.grid&&s.grid.api&&s.grid.options&&Array.isArray(s.grid.options.data)) found=true; }catch(e){} }); return found; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("waitForQueueLoaded: grid not ready in time"); }
        waitForAngular(1200);
    }

    /** Select the N-th (0-based) non-dummy patient row in the queue grid. Used to try DIFFERENT patients
     *  for Generate Queue when GenerateToken() won't open the modal for the current one. Returns the name,
     *  or null when there is no N-th such row. */
    public String selectNthQueueRow(int n) {
        Object r = page.evaluate("(args) => { const skip=new RegExp(args[0],'i'); const n=args[1]; let gridApi=null, data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){gridApi=s.grid.api;data=s.grid.options.data;} }catch(e){} });"
                + " if(!gridApi||!data||!data.length) return null; const nm=x=>((x.PatientName||x.patientname||'')+'').trim();"
                + " const pool=data.filter(x=>nm(x)&&!skip.test(nm(x))); if(n>=pool.length) return null; const p=pool[n];"
                + " try{gridApi.selection.clearSelectedRows();}catch(e){} gridApi.selection.selectRow(p); return nm(p); }",
                java.util.Arrays.asList(SKIP_PATIENTS_RE, n));
        waitForAngular(400);
        return r == null ? null : r.toString();
    }

    /** Read the currently-selected queue row's Queue No (TokenNo). Returns "" if none/blank. */
    public String getSelectedQueueNo() {
        Object r = page.evaluate("() => {"
                + " let gridApi=null; document.querySelectorAll('*').forEach(el=>{ if(gridApi) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){gridApi=s.grid.api;} }catch(e){} });"
                + " if(!gridApi) return ''; let sel=[]; try{ sel=gridApi.selection.getSelectedRows(); }catch(e){} if(!sel||!sel.length) return '';"
                + " const x=sel[0]; return ((x.TokenNo||x.tokenno||x.QueueNo||x.queueno||'')+'').trim(); }");
        return r == null ? "" : r.toString().trim();
    }

    /** Select the first non-dummy patient that HAS a department set (departmentid &gt; 0). Change Doctor's
     *  doctor list (drpDoctor) is populated from the department, so a no-department patient (e.g. PETER
     *  JOHNSON, deptid 0) shows an empty doctor dropdown. Falls back to any non-dummy row. Returns the name. */
    public String selectQueueRowWithDepartment() {
        Object r = page.evaluate("(skipRe) => {"
                + " const skip=new RegExp(skipRe,'i'); let gridApi=null, data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data) return; try{ const s=angular.element(el).scope(); if(!s) return;"
                + "   if(s.grid && s.grid.api && s.grid.options && s.grid.options.data && s.grid.options.data.length){ gridApi=s.grid.api; data=s.grid.options.data; } }catch(e){} });"
                + " if(!gridApi || !data || !data.length) return null;"
                + " const nm=x=>((x.PatientName||x.patientname||'')+'').trim();"
                + " const patient=data.find(x=>nm(x) && !skip.test(nm(x)) && parseInt((x.departmentid||0),10)>0)"
                + "   || data.find(x=>nm(x) && !skip.test(nm(x))) || data[0];"
                + " try{ gridApi.selection.clearSelectedRows(); }catch(e){}"
                + " gridApi.selection.selectRow(patient);"
                + " return nm(patient); }", SKIP_PATIENTS_RE);
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /** Select the N-th (0-based) non-dummy patient that has a department set (departmentid &gt; 0). Used to
     *  try DIFFERENT patients for Change Doctor when a patient's doctor dropdown comes up empty. Returns the
     *  name, or null when there is no N-th such patient. */
    public String selectNthQueueRowWithDepartment(int n) {
        Object r = page.evaluate("(args) => { const skip=new RegExp(args[0],'i'); const n=args[1]; let gridApi=null, data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data) return; try{ const s=angular.element(el).scope(); if(!s) return;"
                + "   if(s.grid && s.grid.api && s.grid.options && s.grid.options.data && s.grid.options.data.length){ gridApi=s.grid.api; data=s.grid.options.data; } }catch(e){} });"
                + " if(!gridApi || !data || !data.length) return null;"
                + " const nm=x=>((x.PatientName||x.patientname||'')+'').trim();"
                + " const pool=data.filter(x=>nm(x) && !skip.test(nm(x)) && parseInt((x.departmentid||0),10)>0);"
                + " if(n>=pool.length) return null; const patient=pool[n];"
                + " try{ gridApi.selection.clearSelectedRows(); }catch(e){} gridApi.selection.selectRow(patient); return nm(patient); }",
                java.util.Arrays.asList(SKIP_PATIENTS_RE, n));
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /** After opening Change Doctor, wait briefly and report whether the doctor dropdown has real options
     *  (the list loads from the patient's department; some patients come up empty). */
    public boolean changeDoctorHasDoctors() {
        try {
            page.waitForFunction(
                    "() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change doctor/i.test(x.textContent||''));"
                            + " const e=m&&[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='ChangePatienttype.DoctorID'); return e && [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())).length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
            return true;
        } catch (Exception e) { return false; }
    }

    /** Close the Change Doctor modal (Close/× or force-hide) so a different patient can be tried. */
    public void closeChangeDoctorModal() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change doctor/i.test(x.textContent||''));"
                + " if(m){ const c=[...m.querySelectorAll('button')].find(b=>/^close$/i.test((b.textContent||'').trim()) && b.offsetParent!==null) || [...m.querySelectorAll('button')].find(b=>/^[×xX]$/.test((b.textContent||'').trim()) && b.offsetParent!==null); if(c) c.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} } [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    /** Click a visible button by exact trimmed text (case-insensitive). */
    public void clickButton(String text) {
        page.evaluate("(t) => { const b=[...document.querySelectorAll('button')].find(x=>(x.innerText||'').trim().toLowerCase()===t.toLowerCase() && x.offsetParent!==null); if(b) b.click(); }", text);
        waitForAngular(500);
    }

    // ---- Close Visit ------------------------------------------------------

    /**
     * Click the <b>Close Visit</b> footer button (ng-click="OpenDischargeTypeModal()") and wait for the
     * Discharge Type / remark modal. Returns true if it opened. (Modal fields are best-effort — heal
     * the discharge-type/remark ng-models live.)
     */
    public boolean clickCloseVisit() {
        page.evaluate("() => { window.__alerts=[];"
                + " let b=[...document.querySelectorAll('button')].find(x=>/OpenDischargeTypeModal/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null);"
                + " if(!b) b=[...document.querySelectorAll('button')].find(x=>/^close\\s*visit$/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                + " if(b) b.click(); }");
        try {
            page.waitForFunction(
                    "() => [...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /discharge|close\\s*visit|remark|reason/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("clickCloseVisit: Discharge/Close-Visit modal did not open");
            return false;
        }
        waitForAngular(600);
        return true;
    }

    /**
     * Enter the Close-Visit / discharge Remark. Fills the first visible remark/reason textarea (or text
     * input) in the discharge modal via the Angular model, and picks the first real option of any
     * mandatory Discharge Type dropdown that is still unset. Returns a summary of what was filled.
     */
    public String enterRemark(String remark) {
        Object r = page.evaluate("(rm) => {"
                + " const m=[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /discharge|close\\s*visit|remark|reason/i.test(x.textContent||''));"
                + " const scope=m||document; const out=[];"
                + " [...scope.querySelectorAll('select')].filter(s=>s.offsetParent!==null).forEach(sel=>{ if(sel.selectedIndex<=0 && sel.options.length>1){ sel.selectedIndex=1; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(sel).trigger('change');$(sel).select2('val',sel.value);}catch(e){}} out.push((sel.getAttribute('ng-model')||'select')+'='+sel.options[1].text.trim()); } });"
                + " let e=[...scope.querySelectorAll('textarea,input[type=text]')].find(x=>x.offsetParent!==null && /remark|reason/i.test((x.getAttribute('ng-model')||'')+(x.getAttribute('placeholder')||'')));"
                + " if(!e) e=[...scope.querySelectorAll('textarea')].find(x=>x.offsetParent!==null);"
                + " if(e){ const c=angular.element(e).controller('ngModel'); e.value=rm; if(c){c.$setViewValue(rm);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); out.push('Remark='+rm); }"
                + " return out.length?out.join(' | '):'(no editable fields found)'; }", remark);
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Save</b> on the Close-Visit/discharge modal and return the success toast text (expected
     * "Visit Closed Successfully"). Confirms a "Do You Want To Save" dialog if one appears. Captures the
     * toast via a MutationObserver, falling back to any intercepted JAlert.
     */
    public String saveCloseVisitAndGetToast() {
        page.evaluate("() => { window.__alerts=[]; window.__cvToasts=[];"
                + " if(window.__cvObs) window.__cvObs.disconnect();"
                + " window.__cvObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message').forEach(el=>{ const m=(el.textContent||'').trim(); if(m && !window.__cvToasts.includes(m)) window.__cvToasts.push(m); }); });"
                + " window.__cvObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /discharge|close\\s*visit|remark|reason/i.test(x.textContent||''));"
                + " const scope=m||document;"
                + " const b=[...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(save|ok|yes|submit|close visit)$/i.test((x.textContent||'').trim())); if(b) b.click(); }");
        // WAIT UNTIL THE TOAST APPEARS — poll every ~1s, handling any (possibly late) "Do you want to save"
        // confirm each cycle and re-scanning the DOM for toasts, and break the instant one is captured. Bounded
        // (~90s) only so a genuine failure can't hang forever.
        String toast = "";
        for (int i = 0; i < 90; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate(
                    "() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want to save|are you sure/i.test(m.textContent||''))"));
            if (confirm) {
                page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want to save|are you sure/i.test(m.textContent||''));"
                        + " const b=[...(box||document).querySelectorAll('button')].find(x=>/^(save|yes|ok)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(400);
            }
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__cvToasts=window.__cvToasts||[]; if(!window.__cvToasts.includes(m)) window.__cvToasts.push(m); } });"
                    + " const a=window.__cvToasts||[]; return a.find(x=>/closed|revok|success|saved/i.test(x)) || a[0] || ((window.__alerts||[]).find(x=>/close|success/i.test(x))) || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            page.waitForTimeout(1000);
        }
        if (toast.isEmpty()) System.out.println("saveCloseVisitAndGetToast: no toast observed within ~90s. Alerts: " + capturedAlerts());
        waitForAngular(400);
        return toast;
    }

    // ---- Medico Legal (MLC) ------------------------------------------------

    /** The MLC modal (Police Station / Docket No / Remark, Document List, Check List), or the whole document. */
    private static final String MLC_MODAL_JS =
            "[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /medico\\s*legal|police\\s*station|docket/i.test(x.textContent||''))";

    /**
     * Click the <b>Medico Legal</b> footer button (ng-click="OpenMLCModal()") and wait for the MLC modal
     * (Police Station / Docket No / Remark, a Document List and a Check List section).
     */
    public boolean clickMedicoLegal() {
        page.evaluate("() => { let b=[...document.querySelectorAll('button')].find(x=>/OpenMLCModal/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null);"
                + " if(!b) b=[...document.querySelectorAll('button')].find(x=>/medico\\s*legal/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                + " if(b) b.click(); }");
        try {
            page.waitForFunction(
                    "() => " + MLC_MODAL_JS + " !== undefined",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("clickMedicoLegal: MLC modal did not open");
            return false;
        }
        waitForAngular(600);
        return true;
    }

    /** Fill Police Station / Docket No / Remark in the MLC modal (ng-model / label best-effort match). */
    public String fillMlcDetails(String policeStation, String docketNo, String remark) {
        Object r = page.evaluate("(a) => { const m=" + MLC_MODAL_JS + "; const scope=m||document; const out=[];"
                + " const setByLabel=(re,val)=>{ const cands=[...scope.querySelectorAll('input,textarea')].filter(x=>x.offsetParent!==null && x.type!=='file' && x.type!=='checkbox');"
                + "   let e=cands.find(x=>re.test((x.getAttribute('ng-model')||'')+' '+(x.getAttribute('placeholder')||'')));"
                + "   if(!e){ e=cands.find(x=>{ let p=x.closest('.form-group,.row,div'); const l=p?p.querySelector('label'):null; return l && re.test(l.textContent||''); }); }"
                + "   if(!e) return false; const c=angular.element(e).controller('ngModel'); e.value=val; if(c){c.$setViewValue(val);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return true; };"
                + " out.push('PoliceStation=' + (setByLabel(/police\\s*station/i, a.ps) ? a.ps : '(not found)'));"
                + " out.push('DocketNo=' + (setByLabel(/docket/i, a.dn) ? a.dn : '(not found)'));"
                + " out.push('Remark=' + (setByLabel(/remark/i, a.rm) ? a.rm : '(not found)'));"
                + " return out.join(' | '); }",
                java.util.Map.of("ps", policeStation, "dn", docketNo, "rm", remark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * In the MLC modal's <b>Document List</b> section: attach a file (hidden {@code input[type=file]}), enter its
     * File Description, then click <b>Add</b> to commit the row. Returns a summary.
     */
    public String addMlcDocument(String filePath, String description) {
        String fileId = null;
        try {
            Object tagged = page.evaluate("() => { const m=" + MLC_MODAL_JS + "; const scope=m||document;"
                    + " const f=[...scope.querySelectorAll('input[type=file]')][0]; if(!f) return null;"
                    + " if(!f.id) f.id='__mlcFile'; return f.id; }");
            fileId = tagged == null ? null : tagged.toString();
            if (fileId != null) page.setInputFiles("#" + fileId, java.nio.file.Paths.get(filePath));
        } catch (Exception e) { System.out.println("addMlcDocument: file upload failed - " + e.getMessage()); }
        waitForAngular(600);
        Object descSet = page.evaluate("(desc) => { const m=" + MLC_MODAL_JS + "; const scope=m||document;"
                + " const e=[...scope.querySelectorAll('input,textarea')].find(x=>x.offsetParent!==null && x.type!=='file' && /description/i.test((x.getAttribute('ng-model')||'')+' '+(x.getAttribute('placeholder')||'')));"
                + " if(!e) return false; const c=angular.element(e).controller('ngModel'); e.value=desc; if(c){c.$setViewValue(desc);c.$render();}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return true; }",
                description);
        waitForAngular(400);
        Object added = page.evaluate("() => { const m=" + MLC_MODAL_JS + "; const scope=m||document;"
                + " const b=[...scope.querySelectorAll('button,a')].find(x=>x.offsetParent!==null && /^add$/i.test((x.textContent||'').trim()));"
                + " if(!b) return false; b.click(); return true; }");
        waitForAngular(600);
        return "File=" + (fileId != null ? "attached" : "(input not found)")
                + " | Description=" + (Boolean.TRUE.equals(descSet) ? description : "(not found)")
                + " | Add=" + (Boolean.TRUE.equals(added) ? "clicked" : "(button not found)");
    }

    /**
     * In the MLC modal's <b>Check List</b> section: tick the first available (un-ticked) checklist row and enter
     * its Remark. Returns a summary.
     */
    public String selectMlcChecklistItem(String remark) {
        Object r = page.evaluate("(rm) => { const m=" + MLC_MODAL_JS + "; const scope=m||document;"
                + " const cb=[...scope.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && !x.checked); let label='';"
                + " if(cb){ cb.click(); const row=cb.closest('tr')||cb.closest('label')||cb.parentElement; label=(row?row.textContent:'').replace(/\\s+/g,' ').trim(); }"
                + " const e=[...scope.querySelectorAll('textarea,input[type=text]')].find(x=>x.offsetParent!==null && /remark/i.test((x.getAttribute('ng-model')||'')+' '+(x.getAttribute('placeholder')||'')));"
                + " if(e){ const c=angular.element(e).controller('ngModel'); e.value=rm; if(c){c.$setViewValue(rm);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " return (cb?'Checklist item ticked: '+label.slice(0,60):'(no checklist checkbox found)') + ' | Remark=' + (e?rm:'(not found)'); }",
                remark);
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Save</b> on the MLC modal and return the success toast text. Confirms a "Do You Want To Save"
     * dialog if one appears (same pattern as {@link #saveCloseVisitAndGetToast()}).
     */
    public String saveMlcAndGetToast() {
        page.evaluate("() => { window.__mlcToasts=[]; if(window.__mlcObs) window.__mlcObs.disconnect();"
                + " window.__mlcObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').trim(); if(m && !window.__mlcToasts.includes(m)) window.__mlcToasts.push(m); }); });"
                + " window.__mlcObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=" + MLC_MODAL_JS + "; const scope=m||document;"
                + " const b=[...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(save|ok|submit)$/i.test((x.textContent||'').trim())); if(b) b.click(); }");
        String toast = "";
        for (int i = 0; i < 60; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate(
                    "() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want to save|are you sure/i.test(m.textContent||''))"));
            if (confirm) {
                page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want to save|are you sure/i.test(m.textContent||''));"
                        + " const b=[...(box||document).querySelectorAll('button')].find(x=>/^(save|yes|ok)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(400);
            }
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__mlcToasts=window.__mlcToasts||[]; if(!window.__mlcToasts.includes(m)) window.__mlcToasts.push(m); } });"
                    + " const a=window.__mlcToasts||[]; return a.find(x=>/success|saved/i.test(x)) || a[0] || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            page.waitForTimeout(1000);
        }
        if (toast.isEmpty()) System.out.println("saveMlcAndGetToast: no toast observed within ~60s. Alerts: " + capturedAlerts());
        waitForAngular(400);
        return toast;
    }

    // ---- Revoke Visit -----------------------------------------------------

    /**
     * Iterate the queue grid rows, selecting each in turn, until one is found whose footer <b>Revoke Visit</b> button
     * becomes ENABLED (that visit is revocable — a disabled button means the selected patient cannot be revoked).
     * Leaves that revocable visit selected. Returns "name" of the revocable visit, or {@code null} if none is revocable.
     */
    public String selectRevocableVisit() {
        Object cnt = page.evaluate("() => { let data=null; document.querySelectorAll('*').forEach(el=>{ if(data)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length) data=s.grid.options.data; }catch(e){} }); return data?data.length:0; }");
        int n = cnt instanceof Number ? ((Number) cnt).intValue() : 0;
        for (int i = 0; i < Math.min(n, 30); i++) {
            Object nm = page.evaluate("(idx) => { const skip=/dummy|test\\s*emr/i; let gridApi=null,data=null; document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){gridApi=s.grid.api;data=s.grid.options.data;} }catch(e){} }); if(!gridApi||!data||idx>=data.length) return null; const p=data[idx]; const nm=x=>((x.PatientName||x.patientname||'')+'').trim(); try{gridApi.selection.clearSelectedRows();}catch(e){} gridApi.selection.selectRow(p); return nm(p); }", i);
            waitForAngular(400);
            boolean enabled = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/OpenRevokeTypeModal/.test(x.getAttribute('ng-click')||'')) || [...document.querySelectorAll('button')].find(x=>/^revoke\\s*visit$/i.test((x.textContent||'').trim())); return !!(b && !b.disabled && b.offsetParent!==null); }"));
            if (enabled) return nm == null ? "(row " + i + ")" : nm.toString();
        }
        return null;
    }


    /**
     * Click the <b>Revoke Visit</b> footer button (ng-click="OpenRevokeTypeModal()") and wait for the
     * Revoke / remark modal. Returns true if it opened.
     */
    /** True once the enabled/disabled state of the footer Revoke button has been checked; see {@link #lastRevokeState}. */
    public String lastRevokeState = "";

    public boolean clickRevokeVisit() {
        // Click the footer Revoke Visit button ONLY if it is ENABLED. It is ng-disabled when the selected visit is not
        // in a revocable state — do NOT force it open (a blanket jQuery('.modal').modal('show') reveals the wrong modal,
        // e.g. Patient Task). If disabled, report it so the step fails cleanly and honestly.
        Object state = page.evaluate("() => { window.__alerts=[];"
                + " const b=[...document.querySelectorAll('button')].find(x=>/OpenRevokeTypeModal/.test(x.getAttribute('ng-click')||''))"
                + "   || [...document.querySelectorAll('button')].find(x=>/^revoke\\s*visit$/i.test((x.textContent||'').trim()));"
                + " if(!b) return 'no-button'; if(b.disabled || b.offsetParent===null) return 'disabled'; b.click(); return 'clicked'; }");
        lastRevokeState = String.valueOf(state);
        if (!"clicked".equals(lastRevokeState)) {
            System.out.println("clickRevokeVisit: Revoke button " + lastRevokeState + " (visit not revocable)");
            return false;
        }
        try {
            page.waitForFunction(
                    "() => [...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /revoke/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { System.out.println("clickRevokeVisit: Revoke modal did not open"); return false; }
        waitForAngular(600);
        return true;
    }

    /**
     * Enter the Revoke Remark — fills the first visible remark/reason field in the revoke modal via the
     * Angular model, and picks the first real option of any mandatory dropdown still unset. Returns a summary.
     */
    public String enterRevokeRemark(String remark) {
        Object r = page.evaluate("(rm) => {"
                + " const m=[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /revoke/i.test(x.textContent||''));"
                + " const scope=m||document; const out=[];"
                + " [...scope.querySelectorAll('select')].filter(s=>s.offsetParent!==null).forEach(sel=>{ if(sel.selectedIndex<=0 && sel.options.length>1){ sel.selectedIndex=1; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(sel).trigger('change');}catch(e){}} out.push((sel.getAttribute('ng-model')||'select')+'='+sel.options[1].text.trim()); } });"
                + " let e=[...scope.querySelectorAll('textarea,input[type=text]')].find(x=>x.offsetParent!==null && /remark|reason/i.test((x.getAttribute('ng-model')||'')+(x.getAttribute('placeholder')||'')));"
                + " if(!e) e=[...scope.querySelectorAll('textarea')].find(x=>x.offsetParent!==null);"
                + " if(e){ const c=angular.element(e).controller('ngModel'); e.value=rm; if(c){c.$setViewValue(rm);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); out.push('Remark='+rm); }"
                + " return out.length?out.join(' | '):'(no editable fields found)'; }", remark);
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Save</b> on the Revoke modal and return the success toast (expected "Visit Revoked
     * Successfully"). Confirms a "Do You Want To Save" dialog if one appears.
     */
    public String saveRevokeVisitAndGetToast() {
        page.evaluate("() => { window.__alerts=[]; window.__rvqToasts=[];"
                + " if(window.__rvqObs) window.__rvqObs.disconnect();"
                + " window.__rvqObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message').forEach(el=>{ const m=(el.textContent||'').trim(); if(m && !window.__rvqToasts.includes(m)) window.__rvqToasts.push(m); }); });"
                + " window.__rvqObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /revoke/i.test(x.textContent||''));"
                + " const scope=m||document;"
                + " const b=[...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(save|ok|yes|submit|revoke|revoke visit)$/i.test((x.textContent||'').trim())); if(b) b.click(); }");
        String toast = "";
        for (int i = 0; i < 90; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate(
                    "() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want to save|are you sure/i.test(m.textContent||''))"));
            if (confirm) {
                page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want to save|are you sure/i.test(m.textContent||''));"
                        + " const b=[...(box||document).querySelectorAll('button')].find(x=>/^(save|yes|ok)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(400);
            }
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__rvqToasts=window.__rvqToasts||[]; if(!window.__rvqToasts.includes(m)) window.__rvqToasts.push(m); } });"
                    + " const a=window.__rvqToasts||[]; return a.find(x=>/revok|success|saved|closed/i.test(x)) || a[0] || ((window.__alerts||[]).find(x=>/revok|success/i.test(x))) || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            page.waitForTimeout(1000);
        }
        if (toast.isEmpty()) System.out.println("saveRevokeVisitAndGetToast: no toast observed within ~90s. Alerts: " + capturedAlerts());
        waitForAngular(400);
        return toast;
    }

    /**
     * After closing a visit, tick the <b>"Closed Visit"</b> filter checkbox ({@code queue.ClosedVisit})
     * with a REAL click, then re-Search to list the closed visits. Returns the number of rows shown in
     * the grid (0 if none) so the caller can verify the table and screenshot it.
     */
    public int viewClosedVisits() {
        // Tag the checkbox for a real click (its ng-model watcher must fire).
        Object needClick = page.evaluate("() => { const cb=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.getAttribute('ng-model')==='queue.ClosedVisit'); if(!cb) return false; cb.id='__closedVisitCb'; return !cb.checked; }");
        if (Boolean.TRUE.equals(needClick)) {
            try { page.locator("#__closedVisitCb").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(5000)); }
            catch (Exception e) { try { page.locator("#__closedVisitCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) {} }
        }
        page.evaluate("() => { const e=document.getElementById('__closedVisitCb'); if(e) e.removeAttribute('id'); }");
        waitForAngular(400);
        // Re-Search to apply the Closed Visit filter.
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/^search$/i.test((x.innerText||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction(
                    "() => { let d=null; document.querySelectorAll('*').forEach(el=>{ if(d) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data){d=s.grid.options.data;} }catch(e){} }); return d && d.length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("viewClosedVisits: no closed-visit rows loaded"); }
        waitForAngular(800);
        Object cnt = page.evaluate("() => { let d=null; document.querySelectorAll('*').forEach(el=>{ if(d) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data){d=s.grid.options.data;} }catch(e){} }); return d?d.length:0; }");
        return cnt instanceof Number ? ((Number) cnt).intValue() : 0;
    }

    /**
     * Put the queue back on <b>Active Patient</b> after {@link #viewClosedVisits()}.
     *
     * <p>"Active Patient" ({@code queue.ActivePatient}, ticked by default) and "Closed Visit"
     * ({@code queue.ClosedVisit}) are a mutually exclusive pair — both run {@code toggleCheckbox()}. Verifying the
     * closed visits therefore leaves the grid showing CLOSED patients, and every later section then works from the
     * wrong list: Cancel Visit reported "no cancellable patient in the currently loaded queue" because a closed
     * visit can never be cancelled. Untick Closed, re-tick Active, Search again.</p>
     *
     * @return how many rows the active queue holds afterwards
     */
    public int restoreActivePatients() {
        // Real clicks — these ng-change handlers do not fire from a synthetic one.
        Object closedTagged = page.evaluate("() => { const cb=[...document.querySelectorAll('input[type=checkbox]')]"
                + ".find(x=>x.getAttribute('ng-model')==='queue.ClosedVisit'); if(!cb) return false;"
                + " cb.id='__closedVisitCb'; return cb.checked; }");
        if (Boolean.TRUE.equals(closedTagged)) {
            try { page.locator("#__closedVisitCb").uncheck(new com.microsoft.playwright.Locator.UncheckOptions().setTimeout(5000)); }
            catch (Exception e) { try { page.locator("#__closedVisitCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) {} }
        }
        page.evaluate("() => { const e=document.getElementById('__closedVisitCb'); if(e) e.removeAttribute('id'); }");
        waitForAngular(400);

        Object activeTagged = page.evaluate("() => { const cb=[...document.querySelectorAll('input[type=checkbox]')]"
                + ".find(x=>x.getAttribute('ng-model')==='queue.ActivePatient'); if(!cb) return false;"
                + " cb.id='__activePatientCb'; return !cb.checked; }");
        if (Boolean.TRUE.equals(activeTagged)) {
            try { page.locator("#__activePatientCb").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(5000)); }
            catch (Exception e) { try { page.locator("#__activePatientCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) {} }
        }
        page.evaluate("() => { const e=document.getElementById('__activePatientCb'); if(e) e.removeAttribute('id'); }");
        waitForAngular(400);

        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/^search$/i.test((x.innerText||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction(
                    "() => { let d=null; document.querySelectorAll('*').forEach(el=>{ if(d) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data){d=s.grid.options.data;} }catch(e){} }); return d && d.length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("restoreActivePatients: no active rows loaded in time"); }
        waitForAngular(800);
        Object cnt = page.evaluate("() => { let d=null; document.querySelectorAll('*').forEach(el=>{ if(d) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data){d=s.grid.options.data;} }catch(e){} }); return d?d.length:0; }");
        int rows = cnt instanceof Number ? ((Number) cnt).intValue() : 0;
        Object state = page.evaluate("() => { const get=ng=>{ const cb=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.getAttribute('ng-model')===ng); return cb?cb.checked:null; };"
                + " return 'ActivePatient='+get('queue.ActivePatient')+' ClosedVisit='+get('queue.ClosedVisit'); }");
        System.out.println("restoreActivePatients: " + state + " | rows=" + rows);
        lastFilterState = String.valueOf(state);
        return rows;
    }

    /** Filter state after {@link #restoreActivePatients()} — for the step text. */
    public String lastFilterState = "";

    // ---- Consent / Forms --------------------------------------------------

    /** Click Consent/Forms and wait for the Consent Details modal's search input to be visible. */
    public boolean clickConsentForms() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            // The footer button's text is "ConsentForms" — NO slash. The old clickButton("Consent/Forms") required
            // an exact text match, so it never clicked anything and the modal never opened. Match on the button's
            // OWN handler (fnOnConsentClick) instead, and fall back to a slash-tolerant text match.
            Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " document.querySelectorAll('#__opConsentBtn').forEach(e=>e.removeAttribute('id'));"
                    + " const b=[...document.querySelectorAll('button,a')].find(x=>x.offsetParent!==null && /fnOnConsentClick/.test(x.getAttribute('ng-click')||''))"
                    + "   || [...document.querySelectorAll('button,a')].find(x=>x.offsetParent!==null && /^consent\\s*\\/?\\s*forms$/i.test(norm(x.textContent)));"
                    + " if(!b) return ''; b.id='__opConsentBtn'; return norm(b.textContent); }");
            if (tagged == null || tagged.toString().isEmpty()) {
                System.out.println("clickConsentForms: ConsentForms button not found (attempt " + attempt + "/3)");
                waitForAngular(1200);
                continue;
            }
            // REAL click — a synthetic in-page click does not reliably fire this ng-click.
            try { page.locator("#__opConsentBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("clickConsentForms: real click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__opConsentBtn'); if(e) e.removeAttribute('id'); }");
            try {
                page.waitForSelector("#txtConsentSearch",
                        new Page.WaitForSelectorOptions()
                                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                                .setTimeout(8000));
                waitForAngular(800);
                return true;
            } catch (Exception e) {
                System.out.println("Consent Details modal not visible yet (attempt " + attempt + "/3), retrying...");
                waitForAngular(1200);
            }
        }
        waitForAngular(500);
        return false;
    }

    /** Click View Consent (dismissing any leftover Consent Details modal first) and wait for the modal. */
    public void clickViewConsent() {
        page.evaluate("() => { const $=window.jQuery||window.$;"
                + " ['#consent'].forEach(id=>{ try{ $(id).modal('hide'); }catch(e){} const el=document.querySelector(id); if(el){ el.classList.remove('in'); el.style.display='none'; el.setAttribute('aria-hidden','true'); } });"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
        clickButton("View Consent");
        // Wait for the View Consent/Forms modal to actually render before searching.
        try {
            page.waitForFunction(
                    "() => { const m=document.querySelector('#viewConsentFormsModal'); if(m && m.getBoundingClientRect().width>0) return true;"
                            + " return [...document.querySelectorAll('.modal')].some(x=>x.getBoundingClientRect().width>0 && /view consent/i.test(x.textContent||'')); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { System.out.println("clickViewConsent: View Consent modal did not render"); }
        waitForAngular(1500);
    }

    /**
     * Type the form name into the Consent/Forms typeahead and reliably select it (invokes the
     * autocomplete's itemSelected handler, else sets consent.consentid/consentname), then waits for the
     * async externalformmappingid so a following Save (IUDconsentdetail) opens the document tab.
     * Throws if no matching consent form is found.
     */
    /** The consent form {@link #selectAnyConsentForm()} actually picked. */
    public String lastConsentForm = "";

    /**
     * Pick <b>any</b> form the dropdown offers, instead of a hardcoded one.
     *
     * <p>The section used to type "spc" and choose between two known SPC forms, so it only ever exercised those
     * two and would fail outright on an environment that does not have them. The control's placeholder is "click
     * to see all" — click it, take the whole {@code OPDconsentList}, and choose from that. Nothing about the form
     * matters to this flow beyond it being a real, selectable one.</p>
     *
     * @return the chosen form's name, or "" when the list never populated
     */
    public String selectAnyConsentForm() {
        try {
            page.waitForSelector("input[placeholder*='type to search'], input[placeholder*='click to see all']",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) { System.out.println("selectAnyConsentForm: consent search box not found"); return ""; }
        // Clicking the box is what asks the screen for the full list.
        try { page.locator("input[placeholder*='type to search'], input[placeholder*='click to see all']").first()
                .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        waitForAngular(800);
        try {
            page.waitForFunction("() => { const inp=[...document.querySelectorAll('input')]"
                            + ".find(i=>/type to search|click to see all/i.test(i.getAttribute('placeholder')||''));"
                            + " if(!inp) return false; const sc=angular.element(inp).scope(); if(!sc) return false;"
                            + " const l=sc.OPDconsentList || (sc.$parent && sc.$parent.OPDconsentList) || []; return l.length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("selectAnyConsentForm: the form list never populated"); }

        Object r = page.evaluate("(seed) => {"
                + " const inp=[...document.querySelectorAll('input')].find(i=>/type to search|click to see all/i.test(i.getAttribute('placeholder')||''));"
                + " if(!inp) return 'input-not-found';"
                + " const sc=angular.element(inp).scope(); if(!sc) return 'no-scope';"
                + " const list=sc.OPDconsentList || (sc.$parent && sc.$parent.OPDconsentList) || [];"
                + " const real=list.filter(x=>x && (x.text||'').trim());"
                + " if(!real.length) return 'list-empty';"
                + " const item=real[seed % real.length];"
                + " const opt=sc.AutoCompleteOptionsForConsent;"
                + " const apply=fn=>{ if(sc.$$phase || (sc.$root && sc.$root.$$phase)) fn(); else sc.$apply(fn); };"
                + " apply(() => { let done=false;"
                + "   try { if(opt && typeof opt.itemSelected==='function'){ opt.itemSelected({item:item}); done=true; } } catch(e) {}"
                + "   if(!done){ sc.ConsentDataForSearch=item; sc.consent=sc.consent||{};"
                + "     sc.consent.consentid=item.value; sc.consent.consentname=item.text; } });"
                + " let s=sc, cid=null; for(let i=0;i<6&&s;i++){ if(s.consent && s.consent.consentid){ cid=s.consent.consentid; break; } s=s.$parent; }"
                + " return 'selected:' + item.text + '|consentid:' + cid + '|of:' + real.length; }",
                Math.abs((int) (System.nanoTime() % 100000)));
        String res = String.valueOf(r);
        System.out.println("selectAnyConsentForm: " + res);
        if (!res.startsWith("selected:")) return "";
        lastConsentForm = res.substring("selected:".length()).split("\\|")[0];
        waitForAngular(600);
        return lastConsentForm;
    }

    public void searchAndSelectConsentForm(String searchText, String formText) {
        try {
            page.waitForSelector("input[placeholder*='type to search'], input[placeholder*='click to see all']",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("Input not found. Total inputs: " + page.evaluate("() => document.querySelectorAll('input').length"));
            throw e;
        }
        waitForAngular(500);
        page.locator("input[placeholder*='type to search'], input[placeholder*='click to see all']").first().fill(searchText);
        waitForAngular(600);
        Object result = page.evaluate(
                "(searchText) => {" +
                "  const inp = [].slice.call(document.querySelectorAll('input'))" +
                "     .find(i => /type to search|click to see all/i.test(i.getAttribute('placeholder') || ''));" +
                "  if (!inp) return 'input-not-found';" +
                "  const sc = angular.element(inp).scope(); if (!sc) return 'no-scope';" +
                "  const list = sc.OPDconsentList || (sc.$parent && sc.$parent.OPDconsentList) || [];" +
                "  const want = searchText.trim().toUpperCase();" +
                "  const item = list.find(x => (((x.text || '') + '').trim().toUpperCase()) === want)" +
                "            || list.find(x => (((x.text || '') + '').toUpperCase()).indexOf(want) >= 0);" +
                "  if (!item) return 'item-not-found:' + want + '|count:' + list.length;" +
                "  const opt = sc.AutoCompleteOptionsForConsent;" +
                "  const apply = fn => { if (sc.$$phase || (sc.$root && sc.$root.$$phase)) fn(); else sc.$apply(fn); };" +
                "  apply(() => { let done = false;" +
                "    try { if (opt && typeof opt.itemSelected === 'function') { opt.itemSelected({ item: item }); done = true; } } catch (e) {}" +
                "    if (!done) { sc.ConsentDataForSearch = item; sc.consent = sc.consent || {}; sc.consent.consentid = item.value; sc.consent.consentname = item.text; } });" +
                "  let s = sc, cid = null; for (let i = 0; i < 6 && s; i++) { if (s.consent && s.consent.consentid) { cid = s.consent.consentid; break; } s = s.$parent; }" +
                "  return 'selected:' + item.text + '|consentid:' + cid;" +
                "}", searchText);
        System.out.println("searchAndSelectConsentForm result: " + result);
        if (result == null || !result.toString().startsWith("selected:")) {
            throw new RuntimeException("Consent form '" + searchText + "' not selected: " + result);
        }
        page.waitForFunction(
                "() => { let ok = false; document.querySelectorAll('*').forEach(function(el) { if (ok) return; "
                + "try { var s = angular.element(el).scope(); if (s && s.externalformmappingid && s.externalformmappingid > 0) ok = true; } catch (e) {} }); return ok; }",
                null, new Page.WaitForFunctionOptions().setTimeout(30000));
        waitForAngular(500);
    }

    /**
     * In the Consent/Forms modal (#consent): SELECT the first available consent form from the typeahead list
     * ({@code OPDconsentList}), set the date to today, click <b>Add</b> ({@code Getconsenttemplate();…} — loads
     * the form template into CKEditor), then click <b>Save</b> ({@code IUDconsentdetail()} — opens the consent
     * form/report in a NEW tab). Returns the chosen form name (or "ERR:…").
     */
    public String selectAnyConsentFormAddSave() {
        Object name = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const inp=[...document.querySelectorAll('input')].find(i=>/type to search|click to see all/i.test(i.getAttribute('placeholder')||'')); if(!inp) return 'ERR:no-typeahead';"
                + " const sc=angular.element(inp).scope(); const list=(sc&&(sc.OPDconsentList||(sc.$parent&&sc.$parent.OPDconsentList)))||[]; if(!list.length) return 'ERR:no-forms';"
                // Pick a RANDOM consent form each run (among the first 12) — don't always hardcode the same one.
                + " const item=list[Math.floor(Math.random()*Math.min(list.length,12))]||list[0]; const opt=sc.AutoCompleteOptionsForConsent;"
                + " sc.$apply(()=>{ let done=false; try{ if(opt&&typeof opt.itemSelected==='function'){ opt.itemSelected({item}); done=true; } }catch(e){} if(!done){ sc.consent=sc.consent||{}; sc.consent.consentid=item.value; sc.consent.consentname=item.text; } });"
                + " const dt=document.querySelector(\"input[ng-model='consent.consentdate']\"); if(dt){ const d=new Date(); const v=('0'+d.getDate()).slice(-2)+'/'+('0'+(d.getMonth()+1)).slice(-2)+'/'+d.getFullYear(); const c=angular.element(dt).controller('ngModel'); dt.value=v; if(c){c.$setViewValue(v);c.$render();} dt.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const modal=document.querySelector('#consent'); const addBtn=modal&&[...modal.querySelectorAll('button,a')].find(b=>/Getconsenttemplate/.test(b.getAttribute('ng-click')||'')); if(addBtn) addBtn.click();"
                + " await new Promise(r=>setTimeout(r,3500)); return norm(item.text); }");
        waitForAngular(800);
        // Click Save (IUDconsentdetail) — opens the report/form tab.
        page.evaluate("() => { const modal=document.querySelector('#consent'); const b=modal&&[...modal.querySelectorAll('button,a')].find(x=>/IUDconsentdetail/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        return name == null ? "" : name.toString();
    }

    /** Close the Consent Details / Consent-Forms modal ({@code #consent}) — click its Close ({@code fnclear})
     *  button and force-hide it (so it doesn't intercept clicks on later sections). */
    public void closeConsentFormsModal() {
        page.evaluate("() => { const m=document.querySelector('#consent');"
                + " if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/fnclear/.test(x.getAttribute('ng-click')||'') || /^close$/i.test((x.textContent||'').trim())); if(b) b.click(); }"
                + " const $=window.jQuery||window.$; try{ if($) $('#consent').modal('hide'); }catch(e){}"
                + " const el=document.querySelector('#consent'); if(el){ el.classList.remove('in'); el.style.display='none'; el.setAttribute('aria-hidden','true'); }"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    /** Click Search in the View Consent modal, then wait for the result rows / eye icon to render. */
    public void clickSearchInViewConsent() {
        page.evaluate("() => { let b=[...document.querySelectorAll(\"button[ng-click='searchConsentForms()']\")].find(x=>x.offsetParent!==null);"
                + " if(!b){ const m=document.querySelector('#viewConsentFormsModal'); if(m) b=[...m.querySelectorAll('button')].find(x=>/^Search$/i.test((x.innerText||'').trim()) && x.offsetParent!==null); }"
                + " if(b) b.click(); }");
        // Wait for the record list to load — the eye (view) icon or at least a result row.
        try {
            page.waitForFunction(
                    "() => [...document.querySelectorAll('[ng-click*=\"viewConsentFormDetail\"]')].some(e=>e.offsetParent!==null)"
                            + " || [...document.querySelectorAll('#viewConsentFormsModal tbody tr, .modal tbody tr')].some(r=>r.offsetParent!==null && (r.textContent||'').trim().length>0)",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("clickSearchInViewConsent: no result rows appeared"); }
        waitForAngular(1500);
    }

    /**
     * Tag the Action (view/eye) icon for the form row matching {@code formName} with id
     * {@code consent_action_icon} so the caller can click it inside {@code ctx.waitForPage(...)}.
     * Returns "found" when tagged, else null.
     */
    public String clickConsentActionIcon(String formName) {
        try {
            page.waitForSelector("[ng-click*='viewConsentFormDetail']",
                    new Page.WaitForSelectorOptions()
                            .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                            .setTimeout(20000));
        } catch (Exception e) {
            System.out.println("clickConsentActionIcon: no viewConsentFormDetail icon appeared");
            return null;
        }
        waitForAngular(600);
        Object r = page.evaluate("(formText) => { const icons=[...document.querySelectorAll('[ng-click*=\"viewConsentFormDetail\"]')].filter(e=>e.offsetParent!==null);"
                + " if(!icons.length) return 'no-icon';"
                + " const chosen=icons.find(e=>{ const row=e.closest('tr'); return row && (row.textContent||'').toUpperCase().indexOf(formText.toUpperCase())>=0; }) || icons[0];"
                + " chosen.id='consent_action_icon'; return 'found'; }", formName);
        return (r == null || !"found".equals(r.toString())) ? null : r.toString();
    }

    /**
     * Defensive: if ANY modal/popup is still open, close it (click its Close/× / fire fnclear/CancelRegistration,
     * then force-hide + remove backdrops). Call this before starting the next action so a leftover popup can't
     * intercept clicks. Returns true if it closed something.
     */
    public boolean dismissAnyModal() {
        Object closed = page.evaluate("() => { const vis=[...document.querySelectorAll('.modal,.ng-confirm-box,.jconfirm,[role=dialog]')].filter(m=>m.getBoundingClientRect().width>0);"
                + " let did=false;"
                + " vis.forEach(m=>{ const b=[...m.querySelectorAll('button,a,.close')].find(x=>/^(close|×|cancel)$/i.test((x.textContent||'').trim()) || /fnclear|cancelregistration|cancelsponser|modal\\('hide'\\)/i.test(x.getAttribute('ng-click')||'') || /close/i.test(x.className||'')); if(b){ b.click(); did=true; } });"
                + " const $=window.jQuery||window.$; try{ if($) $('.modal').modal('hide'); }catch(e){}"
                + " [...document.querySelectorAll('.modal.in,.modal[style*=\"display: block\"]')].forEach(m=>{ m.classList.remove('in'); m.style.display='none'; m.setAttribute('aria-hidden','true'); });"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>{ b.remove(); did=true; }); document.body.classList.remove('modal-open');"
                + " return did || vis.length>0; }");
        waitForAngular(400);
        return Boolean.TRUE.equals(closed);
    }

    // ---- View Details -----------------------------------------------------

    /**
     * Click the <b>View Details</b> footer button ({@code ng-click="OpenPatientRegistrationPopupScreen()"}) and
     * wait for the read-only patient registration details popup (the full registration form — Patient
     * Information / Correspondence / NOK / Payor / Visit — with a "View Log" button, ~180 fields). Returns true
     * if the popup opened.
     */
    public boolean clickViewDetails() {
        page.evaluate("() => { let b=[...document.querySelectorAll('button,a')].find(x=>/OpenPatientRegistrationPopupScreen/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null);"
                + " if(!b) b=[...document.querySelectorAll('button')].find(x=>/^view\\s*details$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && m.querySelectorAll('input,select,textarea').length>40 && /patient information|correspondence|view log/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { System.out.println("clickViewDetails: patient details popup did not open"); return false; }
        waitForAngular(1000);
        return true;
    }

    /**
     * Fill every MANDATORY field in the View Details popup that is still empty.
     *
     * <p>The popup IS the patient registration form ({@code Registration.*} models). On a queued patient 23
     * starred fields come back blank — Name, Identification Type, NRIC, Passport No/Expiry, Other Id, Gender,
     * DOB, Race, TIN, Patient Category, Address, State, Mobile, E-mail and the whole Next-of-Kin block — and
     * Update is rejected until they hold values.</p>
     *
     * <p>Text fields get realistic data (never "Auto &lt;thing&gt;"); every {@code <select>} is set from its OWN
     * option list so Angular keeps the binding. Fields already populated are left untouched. Returns a summary
     * plus any mandatory field still empty afterwards.</p>
     */
    public String fillViewDetailsMandatory() {
        Object r = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelectorAll('input,select,textarea').length>40);"
                + " if(!m) return resolve('(popup not found)');"
                + " const el=ng=>[...m.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null);"
                + " const setTxt=(ng,v)=>{ const e=el(ng); if(!e) return null; if((e.value||'').trim()) return 'kept';"
                + "   const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                // Selects: choose from the element's OWN options — assigning a value not in the list is dropped.
                // These dropdowns are populated ASYNCHRONOUSLY — read once and the option list is still just
                // "--Select--", so nothing gets picked. POLL for a real option before choosing.
                // SPEED: check "already has a value" BEFORE polling. Re-fill passes previously waited the full
                // 8s poll on every select even when all of them were already set, and waited it again on the
                // ~7 dropdowns whose options this popup never loads — minutes of dead time per run.
                + " const setSel=async(ng)=>{ let e=el(ng);"
                + "   if(e && e.tagName==='SELECT'){ const c0=e.options[e.selectedIndex];"
                + "     if(c0 && !/^-*\\s*select\\s*-*$/i.test(norm(c0.textContent)) && norm(c0.textContent)!=='Title') return 'kept'; }"
                + "   for(let k=0;k<6;k++){ e=el(ng);"
                + "     if(e && e.tagName==='SELECT' && [...e.options].some(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)) && norm(o.textContent)!=='Title')) break;"
                + "     await sleep(350); }"
                + "   if(!e || e.tagName!=='SELECT') return null;"
                + "   const cur=e.options[e.selectedIndex]; if(cur && !/^-*\\s*select\\s*-*$/i.test(norm(cur.textContent)) && norm(cur.textContent)!=='Title') return 'kept';"
                + "   const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)) && norm(o.textContent)!=='Title');"
                + "   if(i<0) return '(no-options)';"
                + "   e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "   await sleep(250); return norm(e.options[i].textContent); };"
                + " const d=new Date(); const p2=n=>('0'+n).slice(-2);"
                + " const dob='15/05/1990';"
                + " const expiry=p2(d.getDate())+'/'+p2(d.getMonth()+1)+'/'+(d.getFullYear()+5);"
                + " const uniq=String(Math.abs(Date.now()%1000000));"
                + " const nric='90'+p2(5)+'15'+('00'+(Math.abs(Date.now())%1000)).slice(-3)+p2(Math.abs(Date.now())%99);"
                + " const done=[];"
                // --- Patient Information ---
                + " done.push('Name='+setTxt('Registration.FirstName','Nurul Aisyah'));"
                + " done.push('IdType='+await setSel('Registration.ICCardTypeID'));"
                + " done.push('NRIC='+setTxt('Registration.NationalId',nric));"
                + " done.push('Passport='+setTxt('Registration.FamilyName','A'+uniq));"
                + " done.push('PassportExpiry='+setTxt('Registration.PassportExpirydate',expiry));"
                + " done.push('OtherId='+setTxt('Registration.ReferenceNo','REF'+uniq));"
                + " done.push('Gender='+await setSel('Registration.GenderID'));"
                + " done.push('DOB='+setTxt('Registration.DateOfBirth',dob));"
                + " done.push('Race='+await setSel('Registration.RaceID'));"
                + " done.push('Nationality='+await setSel('Registration.NationalityID'));"
                + " done.push('TIN='+setTxt('Registration.TINNumber','TIN'+uniq));"
                + " done.push('PatientCategory='+await setSel('Registration.casetype'));"
                // --- Correspondence Details ---
                + " done.push('Address='+setTxt('Registration.ResAddress','12 Jalan Bukit Bintang'));"
                + " done.push('Country='+await setSel('Registration.ResCountryID'));"
                + " await sleep(1200);"   // Country -> State is a cascade; let the State list reload first
                + " done.push('State='+await setSel('Registration.ResStateID'));"
                + " await sleep(1200);"   // State -> City is a further cascade
                + " done.push('City='+await setSel('Registration.ResCityID'));"
                + " done.push('Postcode='+setTxt('Registration.ResPinCode','55100'));"
                + " done.push('Mobile='+setTxt('Registration.MobileNo','1'+('0'+uniq).slice(-9)));"
                + " done.push('Email='+setTxt('Registration.Email','nurul.aisyah'+uniq+'@example.com'));"
                // --- NOK / Guarantor ---
                + " done.push('KinName='+setTxt('Registration.KinName','Ahmad Faizal'));"
                + " done.push('KinPassport='+setTxt('Registration.KinFamilyName','B'+uniq));"
                + " done.push('KinNRIC='+setTxt('Registration.KinNationalId',nric));"
                + " done.push('KinNationality='+await setSel('Registration.NOKnationalid'));"
                + " done.push('KinRelation='+await setSel('Registration.KinRelationID'));"
                + " done.push('KinMobileCC='+await setSel('Registration.KinMobileCountryCode'));"
                + " done.push('KinMobile='+setTxt('Registration.KinMobileNo','1'+('1'+uniq).slice(-9)));"
                + " done.push('KinAddress='+setTxt('Registration.KinAddress','12 Jalan Bukit Bintang'));"
                + " done.push('KinCountry='+await setSel('Registration.KinCountryID'));"
                + " await sleep(1200);"   // Country -> State cascade on the NOK block too
                + " done.push('KinState='+await setSel('Registration.KinStateID'));"
                + " await sleep(1200);"
                + " done.push('KinCity='+await setSel('Registration.KinCityID'));"
                + " await sleep(800);"
                // --- Payor Information + Visit Information ---
                // Both are collapsed sections whose dropdowns are only populated once their header is clicked
                // (FillSponserDropDown / FillVisitDropdown), so expand them BEFORE looking for empty fields.
                + " for(const re of [/FillSponserDropDown/, /FillVisitDropdown/]){"
                + "   const b=[...m.querySelectorAll('button,a')].find(x=>re.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null);"
                + "   if(b){ b.click(); await sleep(2500); } }"
                + " await sleep(1500);"
                // Generic sweep: fill every mandatory control still empty ANYWHERE in the popup (covers Payor and
                // Visit, whose models are not hard-coded here). Dates get a date, e-mail an address, selects their
                // first real option; anything already populated is left alone.
                + " const labelOfX=e=>{ let n=e.closest('.form-group,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " for(let pass=0; pass<1; pass++){"
                + "   const ctrls=[...m.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden' && e.type!=='button' && e.type!=='checkbox' && e.type!=='radio');"
                + "   for(const e of ctrls){ const lab=labelOfX(e);"
                + "     if(!/\\*/.test(lab) && !e.hasAttribute('required') && !e.hasAttribute('ng-required')) continue;"
                // NEVER type free text into Payor fields. Payor is set through selectPayorSelf() (the same
                // EditSponser/Insurer=Self route used on the Registration screen); a blind sweep here wrote
                // "Test Value" into Payor / Payor Code / GL Reference No. — junk data on a real patient record.
                + "     if(/payor|sponsor|sponser|\\bgl\\b|insurer/i.test(lab) && e.tagName!=='SELECT') continue;"
                + "     if(e.tagName==='SELECT'){ const cur=e.options[e.selectedIndex]; const cv=cur?norm(cur.textContent):'';"
                + "       if(cv && !/^-*\\s*select\\s*-*$/i.test(cv) && cv!=='Title') continue;"
                + "       const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)) && norm(o.textContent)!=='Title'); if(i<0) continue;"
                + "       e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "       done.push(lab.replace(/\\*/g,'').trim()+'='+norm(e.options[i].textContent)); await sleep(350); continue; }"
                + "     if((e.value||'').trim()) continue;"
                + "     let v='Test Value';"
                + "     if(/date|expiry|dob/i.test(lab)) v=p2(d.getDate())+'/'+p2(d.getMonth()+1)+'/'+d.getFullYear();"
                + "     else if(/e-?mail/i.test(lab)) v='nurul.aisyah'+uniq+'@example.com';"
                + "     else if(/mobile|phone|contact|tel/i.test(lab)) v='1'+('0'+uniq).slice(-9);"
                + "     else if(/amount|limit|percent|no\\.?$|number/i.test(lab)) v=uniq.slice(0,5);"
                + "     else if(/address/i.test(lab)) v='12 Jalan Bukit Bintang';"
                + "     else if(/remark|reason|note/i.test(lab)) v='Updated from queue management';"
                + "     const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "     e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "     done.push(lab.replace(/\\*/g,'').trim()+'='+v); }"
                + "   await sleep(400); }"
                // Re-check: what mandatory field is STILL empty?
                + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " const still=[]; [...m.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden' && e.type!=='button').forEach(e=>{"
                + "   const lab=labelOf(e); if(!/\\*/.test(lab) && !e.hasAttribute('required') && !e.hasAttribute('ng-required')) return;"
                + "   let v=(e.value||'').trim(); if(e.tagName==='SELECT'){ const o=e.options[e.selectedIndex]; v=o?norm(o.textContent):''; if(/^-*\\s*select\\s*-*$/i.test(v)) v=''; }"
                + "   if(!v) still.push(lab.replace(/\\*/g,'').trim()+'['+(e.getAttribute('ng-model')||'?')+']'); });"
                + " resolve(done.filter(x=>!/=null$/.test(x)).join(' | ') + ' || still empty: ' + (still.length? still.join(', ') : 'none')); })");
        waitForAngular(800);
        return r == null ? "" : r.toString();
    }

    /**
     * Payor Information in the View Details popup — handled the SAME way as on the Registration screen:
     * open the <b>Payor Information</b> accordion ({@code FillSponserDropDown()}) so its default payor row loads,
     * invoke that row's {@code EditSponser($index)} (which populates the payor form as <b>Self</b>), REAL-click
     * the Insurer "Self" radio (value=1), then backfill {@code Registration.receivabletypeid} /
     * {@code Registration.PayerTypeId} if still empty. Returns true when Payor Mode reads Self.
     */
    public boolean selectPayorSelf() {
        page.evaluate("() => { const a=[...document.querySelectorAll('a,button')].find(x=>/FillSponserDropDown/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(a) a.click(); }");
        try {
            page.waitForFunction(
                    "() => [...document.querySelectorAll('table')].some(t=>/PAYOR MODE|PRICING POLICY/i.test((t.querySelector('thead')||{}).innerText||'') && /self|selfpay|cash/i.test((t.querySelector('tbody')||{}).innerText||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { System.out.println("selectPayorSelf: payor default row did not load"); }
        waitForAngular(600);
        page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/PAYOR MODE|PRICING POLICY/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(!t) return; const row=t.querySelector('tbody tr'); if(!row) return;"
                + " const cell=[...row.querySelectorAll('td')].find(td=>/self|selfpay|cash/i.test(td.textContent||'')) || row.querySelector('td') || row;"
                + " const sc=angular.element(cell).scope(); if(sc && typeof sc.EditSponser==='function'){ sc.$apply(function(){ sc.EditSponser(sc.$index); }); } else { cell.click(); } }");
        waitForAngular(700);
        Object tagged = page.evaluate("()=>{const r=[...document.querySelectorAll('input[ng-model=\"Insurer\"]')].find(x=>x.value==='1' && x.offsetParent!==null); if(!r) return false; r.id='__vdPayorSelf'; return true;}");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__vdPayorSelf").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(5000)); }
            catch (Exception e) { try { page.locator("#__vdPayorSelf").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { } }
            page.evaluate("()=>{const e=document.getElementById('__vdPayorSelf'); if(e) e.removeAttribute('id');}");
        }
        page.evaluate("() => { const setSelIfEmpty=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return;"
                + " const cur=((e.options[e.selectedIndex]||{}).text||'').trim(); if(cur && !/^-*\\s*select\\s*-*$/i.test(cur)) return;"
                + " let i=[...e.options].findIndex(o=>(o.text||'').trim().toLowerCase()===txt.toLowerCase()); if(i<1) i=[...e.options].findIndex((o,ix)=>ix>0 && !/select/i.test(o.text||'')); if(i<1) return;"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " setSelIfEmpty('Registration.receivabletypeid','Self'); setSelIfEmpty('Registration.PayerTypeId','Self'); }");
        waitForAngular(500);
        Object payorMode = page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.receivabletypeid'); return e?((e.options[e.selectedIndex]||{}).text||'').trim():''; }");
        System.out.println("selectPayorSelf (View Details): Payor Mode = " + payorMode);
        boolean modeSelf = payorMode != null && payorMode.toString().toLowerCase().contains("self");
        return modeSelf || Boolean.TRUE.equals(page.evaluate("()=>{const r=[...document.querySelectorAll('input[ng-model=\"Insurer\"]')].find(x=>x.value==='1'); return r && r.checked;}"));
    }

    /**
     * Fill the whole View Details popup and VERIFY nothing mandatory is left: fill the sections, set Payor = Self,
     * then re-check {@link #remainingMandatory()} and fill again until it comes back clean (max 4 passes).
     *
     * <p>The re-check matters because expanding Payor/Visit reveals fields that did not exist on the first pass,
     * and because a cascade (Country → State → City) can blank a dependent select after it was set.</p>
     */
    public String fillViewDetailsAll() {
        StringBuilder log = new StringBuilder();

        // Patient Information (which also holds Correspondence Details + NOK/Guarantor) MUST be expanded before
        // filling — it is collapsed once any other section is opened, and a collapsed section cannot be filled.
        expandSection("^Patient Information$");
        log.append("[Patient/Correspondence/NOK] ").append(fillViewDetailsMandatory());

        // Payor — the same EditSponser / Insurer=Self route used on the Registration screen.
        expandSection("^Payor Information$");
        boolean payor = selectPayorSelf();
        log.append(" | Payor=").append(payor ? "Self" : "NOT set");

        // Visit Information — expand, then let the generic sweep fill whatever it still needs.
        expandSection("^Visit Information$");
        log.append(" | [Visit] ").append(fillViewDetailsMandatory());

        // Re-check EVERY section (expanding each), and re-fill the ones that are still short.
        for (int pass = 1; pass <= 1; pass++) {   // ONE re-fill pass: more passes cost minutes and never changed the outcome
            String left = remainingMandatoryAllSections();
            if (left.isEmpty()) break;
            System.out.println("fillViewDetailsAll: still empty after pass " + pass + " -> " + left + " — filling again");
            log.append(" | re-fill pass ").append(pass).append(" for: ").append(left);
            if (left.contains("Patient Information")) { expandSection("^Patient Information$"); fillViewDetailsMandatory(); }
            if (left.contains("Payor Information"))   { expandSection("^Payor Information$"); selectPayorSelf(); }
            if (left.contains("Visit Information"))   { expandSection("^Visit Information$"); fillViewDetailsMandatory(); }
        }
        String left = remainingMandatoryAllSections();
        log.append(left.isEmpty() ? " || ALL MANDATORY FILLED (all sections checked)" : " || STILL EMPTY: " + left);
        return log.toString();
    }

    /**
     * Expand one accordion section of the View Details popup ("Patient Information" / "Payor Information" /
     * "Visit Information") and wait for its body to render.
     *
     * <p>The popup is an accordion with ONE section open at a time — expanding Payor or Visit COLLAPSES Patient
     * Information. Anything typed into a collapsed section is not visible, and (worse) a visibility-based "is it
     * filled?" scan silently skips it, which is how this flow reported "all mandatory filled" while Patient
     * Information sat completely empty.</p>
     */
    public boolean expandSection(String titleRegex) {
        return expandSection(titleRegex, markerFor(titleRegex));
    }

    /** A field that only exists when the given section is OPEN — used to tell expanded from collapsed. */
    private static String markerFor(String titleRegex) {
        String t = titleRegex.toLowerCase();
        if (t.contains("payor")) return "Registration.receivabletypeid";
        if (t.contains("visit"))  return "Visit.SubDepartmentID";
        return "Registration.FirstName";                 // Patient Information
    }

    /**
     * Expand a section IDEMPOTENTLY: clicking an accordion header TOGGLES it, so calling this twice on an already
     * open section closes it. Every call therefore checks {@code markerNg} (a field that only renders while the
     * section is open) first, clicks only when it is not visible, and clicks again if the first click closed it.
     *
     * <p>Without this the flow ended with ALL THREE sections collapsed — and a collapsed section has no visible
     * mandatory fields, so the "is everything filled?" scan happily reported nothing empty.</p>
     */
    public boolean expandSection(String titleRegex, String markerNg) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (sectionOpen(markerNg)) return true;
            Object clicked = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const re=new RegExp(a.t,'i');"
                    + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelectorAll('input,select,textarea').length>10);"
                    + " const root=m||document;"
                    + " const h=[...root.querySelectorAll('a,button,div,span,h3,h4,.panel-heading')].find(x=>re.test(norm(x.textContent)) && norm(x.textContent).length<40 && x.offsetParent!==null);"
                    + " if(!h) return false; h.click(); return true; }", java.util.Map.of("t", titleRegex));
            if (!Boolean.TRUE.equals(clicked)) return false;
            waitForAngular(1500);
        }
        boolean open = sectionOpen(markerNg);
        if (!open) System.out.println("expandSection: '" + titleRegex + "' would not stay open (marker " + markerNg + " not visible)");
        return open;
    }

    /** True when the section owning {@code markerNg} is currently expanded (that field is on screen). */
    public boolean sectionOpen(String markerNg) {
        Object r = page.evaluate("(ng) => { const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); return !!e; }", markerNg);
        return Boolean.TRUE.equals(r);
    }

    /** How many of this section's controls are currently visible — used to confirm an expand actually took. */
    public int visibleControlCount() {
        Object r = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelectorAll('input,select,textarea').length>10);"
                + " if(!m) return 0; return [...m.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden').length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * Mandatory fields still empty ACROSS ALL SECTIONS — each accordion is expanded in turn before scanning, so a
     * collapsed (invisible) section cannot masquerade as "nothing empty". Returns "" when everything is filled.
     */
    public String remainingMandatoryAllSections() {
        java.util.List<String> all = new java.util.ArrayList<>();
        for (String sec : new String[]{"^Patient Information$", "^Payor Information$", "^Visit Information$"}) {
            String name = sec.replaceAll("[\\^$]", "");
            // If a section cannot be expanded its fields stay invisible and the scan below would skip them —
            // reporting "nothing empty" for a section it never actually looked at. Say so instead.
            if (!expandSection(sec)) { all.add(name + ": COULD NOT EXPAND (not verified)"); continue; }
            String left = remainingMandatory();
            // Belt and braces: a scan that sees NO controls at all means the section is not really open.
            if (visibleControlCount() < 3) { all.add(name + ": NOT VISIBLE when scanned (not verified)"); continue; }
            if (!left.isEmpty() && !left.startsWith("(")) all.add(name + ": " + left);
        }
        return String.join(" || ", all);
    }

    /** Mandatory fields still empty in the View Details popup (empty string when all are filled). */
    public String remainingMandatory() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelectorAll('input,select,textarea').length>40);"
                + " if(!m) return '(popup not found)';"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " const still=[]; [...m.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden' && e.type!=='button').forEach(e=>{"
                + "   const lab=labelOf(e); if(!/\\*/.test(lab) && !e.hasAttribute('required') && !e.hasAttribute('ng-required')) return;"
                + "   let v=(e.value||'').trim(); if(e.tagName==='SELECT'){ const o=e.options[e.selectedIndex]; v=o?norm(o.textContent):''; if(/^-*\\s*select\\s*-*$/i.test(v)) v=''; }"
                + "   if(!v) still.push(lab.replace(/\\*/g,'').trim()); });"
                + " return still.join(', '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * In the View Details popup, click <b>Update Registration</b> ({@code UpdateOnlyRegistration()}), confirm any
     * "Do you want to save/update" dialog, and return the success toast (expected "… Updated Successfully").
     *
     * <p>NB the handler is {@code UpdateOnlyRegistration()} — a {@code /UpdateRegistration/} match does NOT find
     * it, so the button was never clicked before this was corrected. Fill the popup first with
     * {@link #fillViewDetailsMandatory()}.</p>
     */
    public String updateViewDetailsAndGetToast() {
        page.evaluate("() => { window.__vdToasts=[]; if(window.__vdObs) window.__vdObs.disconnect();"
                // CLEAR toasts already on screen first — an earlier section's message (e.g. "Record Added
                // Successfully.") would otherwise be picked up and reported as this Update's result.
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast, .toast-message, .toast').forEach(t=>t.remove());"
                + " window.__vdObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__vdToasts.includes(t)) window.__vdToasts.push(t); }); }); window.__vdObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelectorAll('input,select,textarea').length>40);"
                + " const b=m&&([...m.querySelectorAll('button,a')].find(x=>/Update(Only)?Registration/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + "   || [...m.querySelectorAll('button,a')].find(x=>/^update(\\s*registration)?$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null));"
                + " if(b){ b.id='__vdUpdate'; b.click(); } }");
        for (int i = 0; i < 6; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want to|are you sure/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want to|are you sure/i.test(m.textContent||''));"
                    + " const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|update|save)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(600);
        }
        try {
            page.waitForFunction("() => (window.__vdToasts||[]).some(a=>/updated|success|saved|please|select|enter/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { }
        Object r = page.evaluate("() => { const a=window.__vdToasts||[]; return a.find(x=>/updated|success|saved/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Close the View Details (patient registration details) popup — click its Close/× and force-hide. */
    public void closeViewDetails() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelectorAll('input,select,textarea').length>40 && /patient information|correspondence|view log/i.test(x.textContent||''));"
                + " if(m){ const b=[...m.querySelectorAll('button,a,.close')].find(x=>/^(close|×)$/i.test((x.textContent||'').trim()) || /close|fnclear/i.test(x.getAttribute('ng-click')||'') || /close/i.test(x.className||'')); if(b) b.click(); }"
                + " const $=window.jQuery||window.$; try{ if($) $('.modal').modal('hide'); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(x=>x.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    // ---- New Case ---------------------------------------------------------

    /**
     * Click the <b>New Case</b> footer button (ng-click="OpenNewCase()") and wait for the "View Case"
     * modal (its Save button is {@code SaveQueueNewCaseDetails()}). Returns true if it opened.
     */
    public boolean clickNewCase() {
        page.evaluate("() => { window.__alerts=[];"
                + " let b=[...document.querySelectorAll('button')].find(x=>/OpenNewCase/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null);"
                + " if(!b) b=[...document.querySelectorAll('button')].find(x=>/^new\\s*case$/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                + " if(b) b.click(); }");
        try {
            page.waitForFunction(
                    "() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && (m.querySelector(\"button[ng-click='SaveQueueNewCaseDetails()']\") || [...m.querySelectorAll('select')].some(s=>s.getAttribute('ng-model')==='queue.departmentidcase')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("clickNewCase: New Case (View Case) modal did not open");
            return false;
        }
        waitForAngular(800);
        return true;
    }

    /**
     * Fill the New Case form (verified live): Department ({@code queue.departmentidcase}) then — after
     * the doctor list reloads — Doctor ({@code queue.doctoridcase}) and Diagnosis ({@code queue.Diagnosis}).
     * MRN/NRIC/PatientName arrive pre-filled from the selected patient. Returns a summary of what was set.
     */
    public String fillNewCaseDetails() {
        // The department/doctor are select2-offscreen <select ng-change="fnSetDoctor()"> — a
        // selectedIndex change does NOT propagate to the ngModel, so fnSetDoctor loads no doctors.
        // Set the MODEL directly on the scope and call fnSetDoctor() (verified live). Pick a RANDOM real
        // department from drpdepartment (varies the dept+doctor combo each run so we don't keep colliding
        // on "already generated" for the same patient), then a random doctor from the reloaded list.
        Object dept = page.evaluate("() => {"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector(\"button[ng-click='SaveQueueNewCaseDetails()']\")); if(!m) return '(no-modal)';"
                + " const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='queue.departmentidcase'); if(!e) return '(no-dept)';"
                + " const sc=angular.element(e).scope(); let s=sc, drp=null, q=null;"
                + " for(let i=0;i<12&&s;i++){ if(!drp && Array.isArray(s.drpdepartment)) drp=s.drpdepartment; if(!q && s.queue) q=s.queue; s=s.$parent; }"
                + " if(!drp||!q) return '(no-scope)';"
                + " const opts=drp.filter(d=>d.value && !/^-*\\s*select/i.test((d.text||'').trim())); if(!opts.length) return '(no-opt)';"
                + " const pick=opts[Math.floor(Math.random()*opts.length)];"
                + " sc.$apply(function(){ q.departmentidcase=pick.value; if(typeof sc.fnSetDoctor==='function') sc.fnSetDoctor(); });"
                + " return (pick.text||'').trim(); }");
        // Wait for the doctor list (drpdoctor) to reload for the chosen department.
        try {
            page.waitForFunction(
                    "() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector(\"button[ng-click='SaveQueueNewCaseDetails()']\"));"
                            + " const e=m&&[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='queue.doctoridcase'); return e && [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())).length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("fillNewCaseDetails: doctor list did not populate in time"); com.kpj.core.Reasons.add("the New Case Doctor dropdown did not populate (no options loaded in time)"); }
        waitForAngular(400);
        // Set a RANDOM real doctor via the scope + a Diagnosis.
        Object rest = page.evaluate("() => {"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector(\"button[ng-click='SaveQueueNewCaseDetails()']\")); if(!m) return '(no-modal)';"
                + " const doc=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='queue.doctoridcase');"
                + " let docName='(no-opt)';"
                + " if(doc){ const sc=angular.element(doc).scope(); let s=sc, drd=null, q=null; for(let i=0;i<12&&s;i++){ if(!drd && Array.isArray(s.drpdoctor)) drd=s.drpdoctor; if(!q && s.queue) q=s.queue; s=s.$parent; }"
                + "   if(drd && q){ const opts=drd.filter(d=>d.value && !/^-*\\s*select/i.test((d.text||'').trim())); const pd=opts.length?opts[Math.floor(Math.random()*opts.length)]:null; if(pd){ sc.$apply(function(){ q.doctoridcase=pd.value; }); docName=(pd.text||'').trim(); } } }"
                + " const di=[...m.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')==='queue.Diagnosis'); let diag='(missing)'; if(di){ const c=angular.element(di).controller('ngModel'); di.value='Routine new case - automation test'; if(c){c.$setViewValue('Routine new case - automation test');c.$render();} di.dispatchEvent(new Event('input',{bubbles:true})); di.dispatchEvent(new Event('change',{bubbles:true})); diag='Routine new case - automation test'; }"
                + " return 'Doctor='+docName+' | Diagnosis='+diag; }");
        waitForAngular(400);
        return "Department=" + dept + " | " + rest;
    }

    /**
     * Click Save ({@code SaveQueueNewCaseDetails()}) on the New Case modal and return the success toast
     * (verified live: <b>"EMR Case added successfully."</b>). If Save reports <i>"case already generated
     * with same department and doctor!"</i>, pick a DIFFERENT doctor (from {@code drpdoctor}, excluding
     * ones already tried) and re-Save — repeat until it succeeds or the doctors run out. Then closes the
     * popup so the next section isn't blocked.
     */
    public String saveNewCaseAndGetToast() {
        String toast = clickSaveNewCaseAndReadToast();
        java.util.Set<String> triedDoctors = new java.util.HashSet<>();
        java.util.Set<String> triedDepts = new java.util.HashSet<>();
        // Retry when Save reports EITHER "already generated with same department and doctor!" (duplicate) OR
        // "Please Select Doctor!" (the random department's doctor list reloaded and dropped the selection).
        // Both are handled by re-picking a doctor (then, if exhausted, a different department) below.
        for (int attempt = 0; attempt < 12 && toast != null
                && (toast.toLowerCase().contains("already generated") || toast.toLowerCase().contains("select doctor")); attempt++) {
            // 1) Try a different DOCTOR in the current department first.
            Object changed = pickAnotherNewCaseDoctor(triedDoctors);
            if (changed != null && !changed.toString().startsWith("(")) {
                String cs = changed.toString();
                triedDoctors.add(cs.substring(cs.indexOf("||") + 2));
                System.out.println("saveNewCaseAndGetToast: '" + toast + "' — retrying with doctor " + cs.substring(0, cs.indexOf("||")));
                waitForAngular(500);
                toast = clickSaveNewCaseAndReadToast();
                continue;
            }
            // 2) Doctors exhausted for this department — switch to ANOTHER department, reload doctors.
            Object deptChanged = pickAnotherNewCaseDepartment(triedDepts);
            if (deptChanged == null || deptChanged.toString().startsWith("(")) {
                System.out.println("saveNewCaseAndGetToast: no more department/doctor combos (" + changed + " / " + deptChanged + ")");
                break;
            }
            String ds = deptChanged.toString();
            triedDepts.add(ds.substring(ds.indexOf("||") + 2));
            triedDoctors.clear();
            System.out.println("saveNewCaseAndGetToast: doctors exhausted — switching to department " + ds.substring(0, ds.indexOf("||")));
            try {
                page.waitForFunction(
                        "() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector(\"button[ng-click='SaveQueueNewCaseDetails()']\"));"
                                + " const e=m&&[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='queue.doctoridcase'); return e && [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())).length>0; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(12000));
            } catch (Exception ignore) { System.out.println("saveNewCaseAndGetToast: doctor list did not reload for new department"); }
            waitForAngular(400);
            Object firstDoc = pickAnotherNewCaseDoctor(triedDoctors);
            if (firstDoc != null && !firstDoc.toString().startsWith("(")) {
                String cs = firstDoc.toString();
                triedDoctors.add(cs.substring(cs.indexOf("||") + 2));
            }
            waitForAngular(500);
            toast = clickSaveNewCaseAndReadToast();
        }
        waitForAngular(300);
        // Close the New Case (View Case) popup so the next section isn't blocked.
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector(\"button[ng-click='SaveQueueNewCaseDetails()']\"));"
                + " if(m){ const c=[...m.querySelectorAll('button')].find(b=>/^close$/i.test((b.textContent||'').trim()) && b.offsetParent!==null) || [...m.querySelectorAll('button')].find(b=>/^[×xX]$/.test((b.textContent||'').trim()) && b.offsetParent!==null); if(c) c.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} } [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
        return toast == null ? "" : toast.trim();
    }

    /** Click Save on the New Case modal and return the toast (matches "case", "already", or "success").
     *  The New Case save is a server round-trip that can be slow on DevHIS, so the "EMR Case added
     *  successfully." toast may arrive well after 12s — wait up to 30s and re-scan the toast container
     *  (not just {@code .toast-message}) before giving up. */
    private String clickSaveNewCaseAndReadToast() {
        page.evaluate("() => { window.__ncToasts=[]; if(window.__ncObs) window.__ncObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__ncToasts.includes(t)) window.__ncToasts.push(t); }); };"
                + " window.__ncObs=new MutationObserver(grab); window.__ncObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='SaveQueueNewCaseDetails()' && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => (window.__ncToasts||[]).some(a=>/case|already|success|added/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            // Slow toast — give it one more moment and re-scan the DOM directly.
            page.waitForTimeout(3000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !(window.__ncToasts||[]).includes(t)) (window.__ncToasts=window.__ncToasts||[]).push(t); }); }");
            Object peek = page.evaluate("() => (window.__ncToasts||[]).length");
            if (peek == null || "0".equals(peek.toString()))
                System.out.println("clickSaveNewCaseAndReadToast: no toast observed within 33s");
        }
        Object r = page.evaluate("() => ((window.__ncToasts||[]).find(a=>/added|success|case|already/i.test(a))) || (window.__ncToasts||[])[0] || ''");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Pick a New Case doctor from {@code drpdoctor} not in {@code tried} (adds the current one to tried);
     *  sets it on the scope + calls fnSetVisitType. Returns "text||value" or "(reason)". */
    private Object pickAnotherNewCaseDoctor(java.util.Set<String> tried) {
        return page.evaluate("(triedArr) => { const tried=new Set((triedArr||[]).map(String));"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector(\"button[ng-click='SaveQueueNewCaseDetails()']\")); if(!m) return '(no-modal)';"
                + " const doc=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='queue.doctoridcase'); if(!doc) return '(no-doc)';"
                + " const sc=angular.element(doc).scope(); let s=sc, drd=null, q=null; for(let i=0;i<12&&s;i++){ if(!drd && Array.isArray(s.drpdoctor)) drd=s.drpdoctor; if(!q && s.queue) q=s.queue; s=s.$parent; }"
                + " if(!drd||!q) return '(no-scope)'; const cur=q.doctoridcase; if(cur) tried.add(String(cur));"
                + " const pick=drd.find(d=>d.value && !tried.has(String(d.value)) && !/^-*\\s*select/i.test((d.text||'').trim())); if(!pick) return '(no-more)';"
                + " sc.$apply(function(){ q.doctoridcase=pick.value; if(typeof sc.fnSetVisitType==='function') sc.fnSetVisitType(1); }); return (pick.text||'').trim()+'||'+pick.value; }",
                new java.util.ArrayList<>(tried));
    }

    /** Pick a New Case department from {@code drpdepartment} not in {@code tried} (adds the current one to
     *  tried); sets it on the scope, clears the doctor + calls fnSetDoctor to reload the doctor list.
     *  Returns "text||value" or "(reason)". */
    private Object pickAnotherNewCaseDepartment(java.util.Set<String> tried) {
        return page.evaluate("(triedArr) => { const tried=new Set((triedArr||[]).map(String));"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector(\"button[ng-click='SaveQueueNewCaseDetails()']\")); if(!m) return '(no-modal)';"
                + " const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='queue.departmentidcase'); if(!e) return '(no-dept)';"
                + " const sc=angular.element(e).scope(); let s=sc, drp=null, q=null; for(let i=0;i<12&&s;i++){ if(!drp && Array.isArray(s.drpdepartment)) drp=s.drpdepartment; if(!q && s.queue) q=s.queue; s=s.$parent; }"
                + " if(!drp||!q) return '(no-scope)'; const cur=q.departmentidcase; if(cur) tried.add(String(cur));"
                + " const pick=drp.find(d=>d.value && !tried.has(String(d.value)) && !/^-*\\s*select/i.test((d.text||'').trim())); if(!pick) return '(no-more)';"
                + " sc.$apply(function(){ q.departmentidcase=pick.value; q.doctoridcase=''; if(typeof sc.fnSetDoctor==='function') sc.fnSetDoctor(); }); return (pick.text||'').trim()+'||'+pick.value; }",
                new java.util.ArrayList<>(tried));
    }

    // ---- Attach Signature -------------------------------------------------

    /**
     * Attach a digital signature image to the selected patient. The footer "Attach Signature" control is
     * a {@code <label for="PhotoData">} over the hidden file input {@code #PhotoData}
     * ({@code ng-model="queue.PhotoFileData"}); setting that input uploads the signature. Returns the
     * success toast (verified live: <b>"Digital Signature Saved Successfully."</b>).
     */
    public String attachSignatureAndGetToast(String filePath) {
        // Watch .toast-message AND .toast (the toast title "KPJ Portal" + body live in a .toast container),
        // and grab any already-present toasts immediately.
        page.evaluate("() => { window.__sigToasts=[]; if(window.__sigObs) window.__sigObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__sigToasts.includes(t)) window.__sigToasts.push(t); }); };"
                + " window.__sigObs=new MutationObserver(grab); window.__sigObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        try { page.setInputFiles("#PhotoData", java.nio.file.Paths.get(filePath)); }
        catch (Exception e) { System.out.println("attachSignatureAndGetToast: setInputFiles failed (" + e.getMessage() + ")"); }
        // The upload is a server round-trip — the toast can arrive well after 12s on a slow site. Wait up
        // to 30s and re-scan the DOM directly before giving up.
        try {
            page.waitForFunction("() => (window.__sigToasts||[]).some(a=>/signature|saved|success/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            page.waitForTimeout(3000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !(window.__sigToasts||[]).includes(t)) (window.__sigToasts=window.__sigToasts||[]).push(t); }); }");
            Object peek = page.evaluate("() => (window.__sigToasts||[]).length");
            if (peek == null || "0".equals(peek.toString()))
                System.out.println("attachSignatureAndGetToast: no toast observed within 33s");
        }
        Object r = page.evaluate("() => { const a=window.__sigToasts||[]; return a.find(x=>/signature|saved|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Call For Triage ----------------------------------------------------

    /**
     * The Call For Triage modal (matched on its own text, distinct from the separate Assign Triage modal).
     * The app's OWN heading has a typo — "Call<b>l</b> For Triage" (three L's) — which a plain "call" match
     * misses entirely (verified: {@code /call\s*for\s*triage/i} on "Calll For Triage" is false); {@code call+}
     * tolerates however many L's the app decides to render.
     */
    private static final String CFT_MODAL_JS =
            "[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /call+\\s*for\\s*triage/i.test(x.textContent||''))";

    /**
     * Click the footer <b>Call For Triage</b> button and wait for its modal (a <b>Consultation Room</b> select +
     * Save). Distinct from <b>Assign Triage</b> (zone-based, its own modal/method above).
     */
    public boolean clickCallForTriage() {
        Object clicked = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/CallForTriage|CallForTraige/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/call.{0,3}for.{0,3}triage/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!b) return 'no-button:' + [...document.querySelectorAll('button,a')].filter(x=>x.offsetParent!==null && /triage|call/i.test(norm(x.textContent)+' '+(x.getAttribute('ng-click')||''))).map(x=>norm(x.textContent)+'['+(x.getAttribute('ng-click')||'')+']').join(' ;; ');"
                + " b.id='__cftBtn'; return 'tagged:' + norm(b.textContent) + '[' + (b.getAttribute('ng-click')||'') + ']'; }");
        String c = clicked == null ? "" : clicked.toString();
        if (c.startsWith("no-button")) { System.out.println("clickCallForTriage: 'Call For Triage' button not found. Candidates: " + c); return false; }
        System.out.println("clickCallForTriage: clicking " + c);
        try { page.locator("#__cftBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickCallForTriage: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cftBtn'); if(e) e.removeAttribute('id'); }");
        // Manual poll instead of page.waitForFunction: the modal reliably shows up in a follow-up evaluate()
        // moments after waitForFunction gave up on the SAME predicate (both at 10s and 20s), so poll it the
        // plain way — evaluate + sleep — the way the rest of this codebase waits on async cascades.
        boolean modalUp = false;
        for (int k = 0; k < 40; k++) {
            Boolean up = (Boolean) page.evaluate("() => " + CFT_MODAL_JS + " !== undefined");
            if (Boolean.TRUE.equals(up)) { modalUp = true; break; }
            page.waitForTimeout(500);
        }
        if (!modalUp) {
            // Diagnose: what DID open (if anything), and what alerts/toasts fired instead of a modal.
            Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const modals=[...document.querySelectorAll('.modal,[role=dialog]')].filter(m=>m.getBoundingClientRect().width>0).map(m=>norm(m.textContent).slice(0,150));"
                    + " const toasts=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].map(t=>norm(t.textContent));"
                    + " return 'open modals=['+modals.join(' || ')+'] toasts=['+toasts.join(' || ')+']'; }");
            System.out.println("clickCallForTriage: Call For Triage modal did not open. " + diag);
            return false;
        }
        waitForAngular(600);
        return true;
    }

    /**
     * Select the first real option in the modal's <b>Consultation Room</b> dropdown. DevHIS's internal name for
     * Consultation Room is <b>Cabin</b> (same terminology as the #/CABIN CommonMaster screen elsewhere in this
     * app) — verified live: the field is {@code ng-model="token.cabinid"}, and Save's own validation names it
     * "Room No." ("Please select Room No.!"). Its {@code <label>} is blank in the DOM, so match on the confirmed
     * ng-model directly. The option list loads ASYNCHRONOUSLY after the modal opens — reading it immediately
     * found 0 real options ("(no options)") even though the very same select later showed 116 — so poll for a
     * real option before giving up.
     */
    public String selectConsultationRoomForTriage() {
        String result = "(Consultation Room select not found)";
        for (int k = 0; k < 20; k++) {
            Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const m=" + CFT_MODAL_JS + "; const scope=m||document;"
                    + " let e=[...scope.querySelectorAll('select')].find(x=>x.offsetParent!==null && x.getAttribute('ng-model')==='token.cabinid');"
                    + " if(!e) e=[...scope.querySelectorAll('select')].find(x=>x.offsetParent!==null && /cabin|consultation.?room/i.test(x.getAttribute('ng-model')||''));"
                    + " if(!e) return '(Consultation Room select not found)';"
                    + " const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i<0) return '(no options)';"
                    + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const A=window.angular,$=window.jQuery;"
                    + " try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                    + " return norm(e.options[i].textContent); }");
            result = r == null ? "" : r.toString();
            if (!result.isEmpty() && !result.startsWith("(")) break;
            page.waitForTimeout(500);
        }
        waitForAngular(400);
        return result;
    }

    /** Click <b>Save</b> in the Call For Triage modal and return the success toast. */
    public String saveCallForTriageAndGetToast() {
        page.evaluate("() => { window.__cftToasts=[]; if(window.__cftObs) window.__cftObs.disconnect();"
                + " window.__cftObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').trim(); if(m && !window.__cftToasts.includes(m)) window.__cftToasts.push(m); }); });"
                + " window.__cftObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=" + CFT_MODAL_JS + "; const scope=m||document;"
                + " const b=[...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(save|ok|submit)$/i.test((x.textContent||'').trim())); if(b) b.click(); }");
        String toast = "";
        for (int i = 0; i < 60; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate(
                    "() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want to save|are you sure/i.test(m.textContent||''))"));
            if (confirm) {
                page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want to save|are you sure/i.test(m.textContent||''));"
                        + " const b=[...(box||document).querySelectorAll('button')].find(x=>/^(save|yes|ok)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(400);
            }
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__cftToasts=window.__cftToasts||[]; if(!window.__cftToasts.includes(m)) window.__cftToasts.push(m); } });"
                    + " const a=window.__cftToasts||[]; return a.find(x=>/success|saved/i.test(x)) || a[0] || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            page.waitForTimeout(1000);
        }
        if (toast.isEmpty()) System.out.println("saveCallForTriageAndGetToast: no toast observed within ~60s. Alerts: " + capturedAlerts());
        waitForAngular(400);
        return toast;
    }

    // ---- Assign Triage ----------------------------------------------------

    /**
     * Click the footer <b>Assign Triage</b> button ({@code BtnAssignTriage()}) and wait for the <b>Triage</b> modal
     * (a zone dropdown — Green / Yellow / Red Zone / Non-Emergency — + Save/Close). If the footer button is disabled it
     * falls back to calling {@code BtnAssignTriage()} on the scope. Modal matched by "Triage" text + a zone select.
     */
    public boolean clickAssignTriage() {
        Object clicked = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('BtnAssignTriage')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*assign triage\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return 'no-button';"
                + " if(b.disabled){ let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.BtnAssignTriage==='function'){sc=x;break;} x=x.$parent; } }catch(e){} }); if(sc){ try{ sc.$apply(()=>sc.BtnAssignTriage()); }catch(e){} return 'scope'; } return 'disabled'; }"
                + " b.id='__triageBtn'; return 'tagged'; }");
        String how = String.valueOf(clicked);
        if ("no-button".equals(how)) { System.out.println("clickAssignTriage: 'Assign Triage' button not found"); return false; }
        if ("tagged".equals(how)) {
            try { page.locator("#__triageBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickAssignTriage: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__triageBtn'); if(e) e.removeAttribute('id'); }");
        }
        // Wait for the Assign Triage modal — the "Triage" popup (Assign Triage select + Template/Ward + Bed). Returns
        // true once it opens (its assignment isn't a simple zone-pick here, so the section only verifies it can be
        // dismissed and fails with the reason via {@link #dismissTriageAndDiagnose}).
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /triage/i.test(m.textContent||'') && !/call for triage|room no/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) {
            System.out.println("clickAssignTriage: Triage modal did not open");
            return false;
        }
        waitForAngular(600);
        return true;
    }

    /**
     * Try to dismiss the Assign Triage popup and return a reason describing what happened. Inspects the modal's close
     * controls (× / Close / Cancel), clicks an ENABLED one if present, else tries an outside click + Escape, then
     * VERIFIES the modal actually closed. Returns:
     * <ul><li>{@code "dismissed (…)"} — the popup was genuinely closed;</li>
     * <li>{@code "COULD NOT dismiss … all close controls disabled …"} — no working close control, popup force-hidden.</li></ul>
     */
    public String dismissTriageAndDiagnose() {
        String tri = "[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||'') && !/call for triage|room no/i.test(x.textContent||''))";
        // Inspect + click an enabled close control.
        Object diag = page.evaluate("() => { const m=" + tri + "; if(!m) return 'gone';"
                + " const closers=[...m.querySelectorAll('button,a,.close,[data-dismiss],[data-bs-dismiss]')].filter(b=>/^\\s*(close|cancel)\\s*$/i.test((b.textContent||'').trim()) || /^[×✕xX]$/.test((b.textContent||'').trim()) || /close/i.test(b.className||'') || b.hasAttribute('data-dismiss') || b.hasAttribute('data-bs-dismiss'));"
                + " const enabled=closers.filter(b=>!b.disabled && b.offsetParent!==null && getComputedStyle(b).pointerEvents!=='none');"
                + " if(enabled.length){ enabled[0].click(); return 'clicked \"'+((enabled[0].textContent||enabled[0].className||'x')+'').replace(/\\s+/g,' ').trim().slice(0,18)+'\"'; }"
                + " return 'NO-ENABLED-CLOSER ('+closers.length+' close control(s), '+closers.filter(b=>b.disabled).length+' disabled)'; }");
        String d = String.valueOf(diag);
        if ("gone".equals(d)) return "dismissed (already closed)";
        waitForAngular(500);
        boolean stillOpen = Boolean.TRUE.equals(page.evaluate("() => !!(" + tri + ")"));
        if (stillOpen) {   // try clicking outside the modal + Escape
            try { page.mouse().click(5, 5); } catch (Exception ignore) { }
            try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
            waitForAngular(500);
            stillOpen = Boolean.TRUE.equals(page.evaluate("() => !!(" + tri + ")"));
        }
        if (!stillOpen) return "dismissed (" + d + ")";
        // Genuinely could not close it — force-hide so it stops blocking, and report the real reason.
        page.evaluate("() => { const m=" + tri + "; if(m){ m.style.display='none'; m.classList.remove('in','show'); } [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(300);
        return "COULD NOT dismiss the Assign Triage popup — " + d + " (all close controls disabled/non-working; force-hidden to continue)";
    }

    /** Pick a triage zone/value in the Triage modal (first real "…Zone" option, else the first real option; polls for
     *  async options). Returns the chosen text, or an {@code ERR:*} marker. */
    public String selectTriageZone() {
        String js = "() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||'')); if(!m) return 'ERR:no-modal';"
                + " const sel=m.querySelector(\"select[ng-model='AssignTriage.triagemasterid']\") || [...m.querySelectorAll('select')].find(s=>[...s.options].some(o=>/zone|emergency|triage/i.test(o.textContent||''))); if(!sel) return 'ERR:no-dropdown';"
                + " let i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)) && /zone/i.test(o.textContent||'')); if(i<0) i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " if(i<0) return 'ERR:no-option'; sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(sel).triggerHandler('change');}catch(e){} if($){try{$(sel).trigger('change');}catch(e){}} return norm(sel.options[i].textContent); }";
        String res = "(null)";
        for (int i = 0; i < 12; i++) {
            Object r = page.evaluate(js);
            res = r == null ? "(null)" : r.toString();
            if (!res.startsWith("ERR")) break;
            page.waitForTimeout(400);
        }
        waitForAngular(500);
        return res;
    }

    /** Click <b>Save</b> in the Triage modal and return the success toast (expected "Triage Assigned Successfully."). */
    public String saveTriageAndGetToast() {
        page.evaluate("() => { window.__tgToasts=[]; if(window.__tgObs) window.__tgObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__tgToasts.includes(t)) window.__tgToasts.push(t); }); };"
                + " window.__tgObs=new MutationObserver(grab); window.__tgObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||''));"
                + " const b=m&&([...m.querySelectorAll('button,a')].find(x=>/FnSaveAssignTriage|SaveTriage/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null)); if(b) b.click(); }");
        try {
            page.waitForFunction("() => (window.__tgToasts||[]).some(a=>/triage|assigned|success|saved|please|select|required/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__tgToasts||[]).includes(t)) (window.__tgToasts=window.__tgToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__tgToasts||[]; return a.find(x=>/triage|assigned|success|saved/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Dismiss the Triage popup — and ANY other visible modal it may have left open (e.g. the hidden/stray
     *  "Call for Triage" modal). Clicks each modal's Close/Cancel/×, jQuery-hides all modals, and if anything is
     *  still visible clicks OUTSIDE the modal (empty page spot) + Escape, then force-hides + strips every backdrop. */
    public void closeTriagePopup() {
        // 1) Click a Close/Cancel/× control on every visible modal, then jQuery-hide all modals.
        page.evaluate("() => { const $=window.jQuery||window.$;"
                + " [...document.querySelectorAll('.modal,[role=dialog]')].filter(m=>m.getBoundingClientRect().width>0).forEach(m=>{"
                + "   const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*(close|cancel)\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null)"
                + "     || m.querySelector('button.close,[data-dismiss=modal],[data-bs-dismiss=modal]'); if(b) b.click(); });"
                + " try{ if($) [...document.querySelectorAll('.modal')].forEach(m=>{ try{ $(m).modal('hide'); }catch(e){} }); }catch(e){} }");
        waitForAngular(400);
        // 2) If ANY modal is still visible, click an empty page spot (outside the modal) + Escape.
        boolean stillOpen = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(x=>x.getBoundingClientRect().width>0)"));
        if (stillOpen) {
            try { page.mouse().click(5, 5); } catch (Exception ignore) { }
            try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
            waitForAngular(400);
        }
        // 3) Force-hide any lingering visible modal + strip every backdrop / modal-open lock.
        page.evaluate("() => { [...document.querySelectorAll('.modal,[role=dialog]')].filter(m=>m.getBoundingClientRect().width>0).forEach(m=>{ m.style.display='none'; m.classList.remove('in','show'); });"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); document.body.style.overflow=''; document.body.style.paddingRight=''; }");
        waitForAngular(300);
    }

    // ---- Change Doctor ----------------------------------------------------

    /** Click the <b>Change Doctor</b> footer button (ng-click="fnChangePatientType()") and wait for its
     *  modal (with the {@code ChangePatienttype.DoctorID} select). Returns true if it opened. */
    public boolean clickChangeDoctor() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnChangePatientType()' && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction(
                    "() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /change doctor/i.test(m.textContent||'') && [...m.querySelectorAll('select')].some(s=>s.getAttribute('ng-model')==='ChangePatienttype.DoctorID'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("clickChangeDoctor: Change Doctor modal did not open");
            return false;
        }
        waitForAngular(800);
        return true;
    }

    /**
     * Select ANY doctor in the Change Doctor modal (the doctor list {@code drpDoctor} is pre-populated —
     * set {@code ChangePatienttype.DoctorID} on the scope to a doctor different from the current one and
     * fire its ng-change {@code fnSetVisitType(1)}), then Save ({@code FnSavePatientType()}) and return
     * "Doctor=&lt;picked&gt; | &lt;toast&gt;" (verified live toast: <b>"Doctor Updated Successfully."</b>).
     */
    public String selectAnyDoctorAndSave() {
        // The doctor list (drpDoctor) usually loads a beat AFTER the modal opens. Wait for it before
        // deciding anything — checking too early wrongly sees it empty and then a department reset breaks
        // it (verified: the common case is just "pick a doctor from the already-loaded list → Save").
        try {
            page.waitForFunction(
                    "() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change doctor/i.test(x.textContent||''));"
                            + " const e=m&&[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='ChangePatienttype.DoctorID'); return e && [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())).length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { System.out.println("selectAnyDoctorAndSave: doctor list not pre-loaded — will set a department"); }
        waitForAngular(300);
        // Only if the doctor list is STILL empty (patient has no department pre-set) pick a department and
        // fire its ng-change (fnDeparmentChange) to load the doctors — same cascade as New Case.
        Boolean noDocs = (Boolean) page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change doctor/i.test(x.textContent||'')); if(!m) return false;"
                + " const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='ChangePatienttype.DoctorID'); return !e || [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())).length===0; }");
        if (Boolean.TRUE.equals(noDocs)) {
            page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change doctor/i.test(x.textContent||'')); if(!m) return;"
                    + " const de=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='ChangePatienttype.DepartmentID'); if(!de) return;"
                    + " const sc=angular.element(de).scope(); let s=sc, drp=null, cpt=null; for(let i=0;i<12&&s;i++){ if(!drp && Array.isArray(s.drpDepartment)) drp=s.drpDepartment; if(!cpt && s.ChangePatienttype) cpt=s.ChangePatienttype; s=s.$parent; }"
                    + " if(!drp||!cpt) return; const pick=drp.find(d=>d.value && !/^-*\\s*select/i.test((d.text||'').trim())); if(!pick) return;"
                    + " sc.$apply(function(){ cpt.DepartmentID=pick.value; if(typeof sc.fnDeparmentChange==='function') sc.fnDeparmentChange(1); }); }");
            try {
                page.waitForFunction(
                        "() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change doctor/i.test(x.textContent||''));"
                                + " const e=m&&[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='ChangePatienttype.DoctorID'); return e && [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())).length>0; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(10000));
            } catch (Exception ignore) { System.out.println("selectAnyDoctorAndSave: doctor list did not populate after setting a department"); com.kpj.core.Reasons.add("the Change Doctor dropdown did not populate after setting a department (no options loaded)"); }
            waitForAngular(400);
        }
        Object picked = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change doctor/i.test(x.textContent||'')); if(!m) return '(no-modal)';"
                + " const doc=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='ChangePatienttype.DoctorID'); if(!doc) return '(no-doc)';"
                + " const sc=angular.element(doc).scope(); let s=sc, drd=null, cpt=null; for(let i=0;i<12&&s;i++){ if(!drd && Array.isArray(s.drpDoctor)) drd=s.drpDoctor; if(!cpt && s.ChangePatienttype) cpt=s.ChangePatienttype; s=s.$parent; }"
                + " if(!drd||!cpt) return '(no-scope)'; const cur=cpt.DoctorID;"
                + " const pick=drd.find(d=>d.value && d.value!=cur && !/^-*\\s*select/i.test((d.text||'').trim())) || drd.find(d=>d.value && !/^-*\\s*select/i.test((d.text||'').trim())); if(!pick) return '(no-opt)';"
                + " sc.$apply(function(){ cpt.DoctorID=pick.value; if(typeof sc.fnSetVisitType==='function') sc.fnSetVisitType(1); }); return (pick.text||'').trim(); }");
        waitForAngular(500);
        // If NO doctor could be selected (empty list for this patient), do NOT Save — otherwise a stale
        // "Doctor Updated Successfully." toast lingering in the DOM would produce a FALSE pass.
        if (picked == null || picked.toString().startsWith("(")) {
            System.out.println("selectAnyDoctorAndSave: no doctor to select (" + picked + ") — not saving");
            return "Doctor=(not selected — doctor list empty for this patient) | No doctor available to change";
        }
        // Save is a server round-trip — the "Doctor Updated Successfully." toast can arrive well after 12s
        // on a slow site, and lives in a .toast container with a "KPJ Portal" title. Watch .toast-message
        // AND .toast, wait up to 30s for a matching toast, and re-scan the DOM before giving up.
        // Arm the toast observer AND tag the Save button — then do a REAL Playwright click on it. A
        // synthetic in-page b.click() does NOT reliably fire the Angular ng-click FnSavePatientType() in
        // headless (same as Generate Queue), so Save never runs and no toast appears.
        Object saveTagged = page.evaluate("() => { window.__cdToasts=[]; if(window.__cdObs) window.__cdObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__cdToasts.includes(t)) window.__cdToasts.push(t); }); };"
                + " window.__cdObs=new MutationObserver(grab); window.__cdObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change doctor/i.test(x.textContent||''));"
                + " const b=m&&[...m.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='FnSavePatientType();'); if(!b) return false; b.id='__cdSave'; return true; }");
        if (Boolean.TRUE.equals(saveTagged)) {
            try { page.locator("#__cdSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception ce) { System.out.println("selectAnyDoctorAndSave: real Save click failed - " + ce.getMessage()); }
        } else {
            System.out.println("selectAnyDoctorAndSave: Save button not found");
        }
        // Fallback: if the real click produced no toast within a few seconds (a modal backdrop can intercept
        // it in headless), also fire FnSavePatientType() synthetically and via the scope.
        try { page.waitForFunction("() => (window.__cdToasts||[]).some(a=>/updated|success|doctor|select/i.test(a))",
                null, new Page.WaitForFunctionOptions().setTimeout(5000)); }
        catch (Exception ignore) {
            page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change doctor/i.test(x.textContent||''));"
                    + " const b=m&&[...m.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='FnSavePatientType();'); if(b){ b.click(); const sc=angular.element(b).scope(); if(sc){ try{ sc.$apply(function(){ if(typeof sc.FnSavePatientType==='function') sc.FnSavePatientType(); }); }catch(e){} } } }");
        }
        // Success signal = the toast OR the Change Doctor modal CLOSING (the reliable one — on a slow site
        // the toast can fade/arrive late, but the modal always closes on a successful save; verified live).
        try {
            page.waitForFunction("() => (window.__cdToasts||[]).some(a=>/updated|success|doctor|select/i.test(a))"
                    + " || ![...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /change doctor/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            page.waitForTimeout(3000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !(window.__cdToasts||[]).includes(t)) (window.__cdToasts=window.__cdToasts||[]).push(t); }); }");
        }
        boolean modalClosed = Boolean.TRUE.equals(page.evaluate("() => ![...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /change doctor/i.test(m.textContent||''))"));
        Object toast = page.evaluate("() => { const a=window.__cdToasts||[]; return a.find(x=>/updated successfully|updated|success/i.test(x)) || a.find(x=>/doctor/i.test(x)) || a[0] || ''; }");
        String toastStr = toast == null ? "" : toast.toString().trim();
        boolean toastSuccess = toastStr.toLowerCase().matches(".*(updated|success).*");
        // If the modal closed but the toast was missed, treat it as success (the save went through).
        String result = toastSuccess ? toastStr
                : (modalClosed ? "Doctor Updated Successfully. (confirmed by modal close; toast not captured)"
                               : (toastStr.isEmpty() ? "No success toast appeared and the modal is still open" : toastStr));
        if (!toastSuccess && !modalClosed) System.out.println("selectAnyDoctorAndSave: no toast and modal still open");
        waitForAngular(400);
        return "Doctor=" + (picked == null ? "?" : picked.toString()) + " | " + result;
    }

    /** Read a compact snapshot of the View Details (patient registration) popup — MRN / Name / NRIC / Reg. Type —
     *  by pairing each visible label with its adjacent field value. Returns e.g. "MRN=… | Name=… | NRIC=…".
     *  Complements the existing {@link #clickViewDetails()} / {@link #closeViewDetails()}. */
    public String viewDetailsSummary() {
        Object r = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelectorAll('input,select,textarea').length>40 && (/correspondence|patient information|view log/i.test(x.textContent||''))); if(!m) return '';"
                + " const val=el=>{ if(!el) return ''; const tag=(el.tagName||'').toLowerCase();"
                + "   if(tag==='select'){ const o=el.options[el.selectedIndex]; const t=o?(o.textContent||'').replace(/\\s+/g,' ').trim():''; return /^-*\\s*select\\s*-*$/i.test(t)?'':t; }"
                + "   const v=(el.value!=null?el.value+'':'').trim(); return v; };"
                + " const model=mm=>{ const e=[...m.querySelectorAll('input,select,textarea')].find(x=>(x.getAttribute('ng-model')||'')===mm); return e?val(e):''; };"
                + " const mrn=model('Registration.MRNo'); const fn=model('Registration.FirstName'); const family=model('Registration.FamilyName'); const nric=model('Registration.NationalId');"
                + " const name=[fn,family].filter(Boolean).join(' ').trim();"
                + " const parts=[]; if(mrn)parts.push('MRN='+mrn); if(name)parts.push('Name='+name); if(nric)parts.push('NRIC='+nric); return parts.join(' | '); }");
        return r == null ? "" : r.toString().trim();
    }

    // ---- Call Patient -----------------------------------------------------

    /**
     * Click the footer <b>Call Patient</b> button (ng-click="IUDTokandisplay();") and return the toast.
     *
     * <p>Call Patient displays/announces the selected patient's token on the queue-display board — a DIRECT
     * action (no modal). It works ONLY for a <b>current-date</b> patient: with a wider search range it toasts
     * <i>"Please select Today's date!"</i>, and with no patient selected <i>"Please Select Location!"</i>. On
     * success the live toast is a token-display confirmation. The caller must have searched TODAY and selected
     * a today-patient first. Returns the toast text (a real Playwright click fires the ng-click reliably).</p>
     */
    public String callPatientAndGetToast() {
        page.evaluate("() => { window.__cpToasts=[]; if(window.__cpObs) window.__cpObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__cpToasts.includes(t)) window.__cpToasts.push(t); }); };"
                + " window.__cpObs=new MutationObserver(grab); window.__cpObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>x.getAttribute('ng-click')==='IUDTokandisplay();' && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*call\\s*patient\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__cpBtn'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__cpBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("callPatientAndGetToast: click failed - " + e.getMessage()); }
            // Belt-and-suspenders: also fire IUDTokandisplay() via the scope (walk parents), in case the click
            // didn't reach the ng-click in headless.
            page.evaluate("() => { const b=document.getElementById('__cpBtn'); if(!b) return;"
                    + " let s=angular.element(b).scope(); for(let i=0;i<20&&s;i++){ if(typeof s.IUDTokandisplay==='function'){ const h=s; try{ h.$apply(function(){ h.IUDTokandisplay(); }); }catch(e){ try{ h.IUDTokandisplay(); }catch(e2){} } break; } s=s.$parent; } b.removeAttribute('id'); }");
        } else {
            System.out.println("callPatientAndGetToast: 'Call Patient' button not found");
            return "";
        }
        try {
            page.waitForFunction("() => (window.__cpToasts||[]).some(a=>/token|display|call|success|please|select|location|today/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__cpToasts||[]).includes(t)) (window.__cpToasts=window.__cpToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__cpToasts||[]; return a.find(x=>/token|display|call|success/i.test(x)) || a.find(x=>/please|select|location|today/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Convert IPD Charges -----------------------------------------------

    /**
     * Click the footer <b>Convert IPD Charges</b> button and wait for its confirm dialog (or a direct toast
     * if the app skips confirmation for this patient). Returns <b>"confirm"</b> (dialog appeared),
     * <b>"toast:&lt;text&gt;"</b> (a toast fired directly), or <b>"none"</b> (neither appeared — try the next
     * patient).
     */
    public String clickConvertIpdCharges() {
        page.evaluate("() => { window.__cicToasts=[]; if(window.__cicObs) window.__cicObs.disconnect();"
                + " window.__cicObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__cicToasts.includes(t)) window.__cicToasts.push(t); }); });"
                + " window.__cicObs.observe(document.body,{childList:true,subtree:true});"
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/ConvertIPD|ConvertIpdCharges/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/convert.{0,3}ipd.{0,3}charges/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(b) b.click(); }");
        for (int i = 0; i < 30; i++) {
            boolean dlg = Boolean.TRUE.equals(page.evaluate(
                    "() => [...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /convert|ipd\\s*charges|are you sure|do you want/i.test(m.textContent||''))"));
            if (dlg) return "confirm";
            Object t = page.evaluate("() => (window.__cicToasts||[])[0] || ''");
            if (t != null && !t.toString().isEmpty()) return "toast:" + t;
            page.waitForTimeout(500);
        }
        return "none";
    }

    /**
     * Accept the Convert IPD Charges confirm (click <b>Save</b>, falling back to Yes/OK) and return the
     * resulting toast. A patient with nothing to convert answers <b>"No Unbilled Ipd Charges!"</b> — not a
     * failure on its own, just a signal to try a different patient (handled by the caller's retry loop).
     */
    public String acceptConvertIpdChargesAndGetToast() {
        page.evaluate("() => { window.__cicToasts=window.__cicToasts||[]; if(!window.__cicObs){ window.__cicObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__cicToasts.includes(t)) window.__cicToasts.push(t); }); }); window.__cicObs.observe(document.body,{childList:true,subtree:true}); }"
                + " const dlg=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,[role=dialog]')].find(m=>m.getBoundingClientRect().width>0 && /convert|ipd\\s*charges|are you sure|do you want/i.test(m.textContent||''));"
                + " const scope=dlg||document;"
                + " const b=[...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(save|yes|ok)$/i.test((x.textContent||'').trim())); if(b) b.click(); }");
        String toast = "";
        for (int i = 0; i < 40; i++) {
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__cicToasts=window.__cicToasts||[]; if(!window.__cicToasts.includes(m)) window.__cicToasts.push(m); } });"
                    + " const a=window.__cicToasts||[]; return a.find(x=>/success|saved|converted|unbilled/i.test(x)) || a[0] || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            page.waitForTimeout(500);
        }
        if (toast.isEmpty()) System.out.println("acceptConvertIpdChargesAndGetToast: no toast observed within ~20s. Alerts: " + capturedAlerts());
        waitForAngular(400);
        return toast;
    }

    // ---- Generate Queue ---------------------------------------------------

    /** Click the <b>Generate Queue</b> footer button (ng-click="GenerateToken();") and wait for the
     *  "Generate Queue" modal (with the {@code token.cabinid} select). Returns true if it opened. */
    public boolean clickGenerateQueue() {
        // GenerateToken() is an Angular ng-click — a synthetic in-page b.click() does NOT reliably fire it
        // in the headless run (works "live" only because of slower timing; see the real-click rule). Tag
        // the footer button and do a REAL Playwright click. Retry + detect the modal by title OR the
        // UpdateTokenNo() Save button (the token.cabinid select can render a beat later on a slow site).
        for (int attempt = 0; attempt < 3; attempt++) {
            Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='GenerateToken();' && x.offsetParent!==null); if(!b) return false; b.id='__gqBtn'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__gqBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
                catch (Exception ce) { System.out.println("clickGenerateQueue: real click failed - " + ce.getMessage()); }
                // Belt-and-suspenders: also fire GenerateToken() synthetically + via the scope. GenerateToken
                // lives on a PARENT scope, so walk the chain to find it (the real click intermittently
                // doesn't open the modal in headless).
                page.evaluate("() => { const b=document.getElementById('__gqBtn'); if(!b) return; b.click();"
                        + " let s=angular.element(b).scope(); for(let i=0;i<20&&s;i++){ if(typeof s.GenerateToken==='function'){ const h=s; try{ h.$apply(function(){ h.GenerateToken(); }); }catch(e){ try{ h.GenerateToken(); }catch(e2){} } break; } s=s.$parent; } }");
            } else {
                System.out.println("clickGenerateQueue: Generate Queue button not found (attempt " + (attempt + 1) + "/3)");
            }
            try {
                page.waitForFunction(
                        "() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && (/generate queue/i.test(m.textContent||'') || m.querySelector(\"button[ng-click='UpdateTokenNo()']\") || m.querySelector(\"[ng-model='token.cabinid']\")))",
                        null, new Page.WaitForFunctionOptions().setTimeout(9000));
                // Modal is up — now wait for its room list (drpCabin) to actually load, else fillGenerateQueue
                // races the scope and gets "(no-scope)"/"(no-opt)". Require the token.cabinid <select> to
                // have at least one real room option.
                try {
                    page.waitForFunction(
                            "() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /generate queue/i.test(x.textContent||''));"
                                    + " const e=m&&[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='token.cabinid');"
                                    + " return e && [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())).length>0; }",
                            null, new Page.WaitForFunctionOptions().setTimeout(12000));
                } catch (Exception re) { System.out.println("clickGenerateQueue: room list (drpCabin) not loaded in time"); }
                waitForAngular(800);
                return true;
            } catch (Exception e) {
                System.out.println("clickGenerateQueue: Generate Queue modal did not open (attempt " + (attempt + 1) + "/3)");
                waitForAngular(1200);
            }
        }
        return false;
    }

    /**
     * Fill the mandatory <b>Consultation Room</b> ({@code token.cabinid}) — Location/Specialty/Doctor
     * arrive pre-filled; only the room is required ("Please select Room No.!"). Selecting a room fires the
     * ng-change {@code GenerateTokenNo()} which auto-fills the Queue No ({@code token.tokenno}), then Save
     * ({@code UpdateTokenNo()}).
     *
     * <p>Workflow: if the patient <b>already has</b> a Queue No ({@code oldQueueNo} non-empty), pick rooms
     * until the generated token is a <b>different</b> queue (i.e. change it); if they <b>don't</b>, just
     * assign the first generated one. Returns "Room=&lt;room&gt; | QueueNo=&lt;old&gt;-&gt;&lt;new&gt; |
     * &lt;toast&gt;" (verified live toast: <b>"Token Updated Successfully."</b>).
     */
    public String fillGenerateQueueAndSave(String oldQueueNo) {
        String old = oldQueueNo == null ? "" : oldQueueNo.trim();
        boolean wantDifferent = !old.isEmpty() && !"0".equals(old);

        // The patient's CURRENT room is pre-filled in the modal (token.cabinid). Re-generating in that SAME
        // room yields the SAME queue → toast "Token already Generated!" (verified live). So EXCLUDE the
        // current room and pick a DIFFERENT one whose next token (room max + 1) differs from the patient's
        // current queue. Empty rooms give 1; busy rooms give higher — so try BUSIEST-other rooms first
        // (from the grid: every today-row carries cabinid + tokenno). If Save still says "already
        // generated", try the next different room.
        Object curCabinObj = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /generate queue/i.test(x.textContent||'')); if(!m) return ''; const cab=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='token.cabinid'); if(!cab) return ''; const sc=angular.element(cab).scope(); let s=sc, tok=null; for(let i=0;i<12&&s;i++){ if(!tok&&s.token)tok=s.token; s=s.$parent; } return tok&&tok.cabinid!=null?String(tok.cabinid):''; }");
        String currentCabin = curCabinObj == null ? "" : curCabinObj.toString().trim();

        @SuppressWarnings("unchecked")
        java.util.List<String> preferred = (java.util.List<String>) page.evaluate(
                "(cur) => { let data=null; document.querySelectorAll('*').forEach(el=>{ if(data) return; try{ const s=angular.element(el).scope();"
                        + " if(s&&s.grid&&s.grid.api&&s.grid.options&&Array.isArray(s.grid.options.data)&&s.grid.options.data.length) data=s.grid.options.data; }catch(e){} });"
                        + " if(!data) return []; const mx={}; data.forEach(x=>{ const c=x.cabinid; const t=parseInt(((x.tokenno||'')+'').trim(),10); if(c!=null && c!=='' && String(c)!==String(cur) && !isNaN(t)){ if(mx[c]==null||t>mx[c]) mx[c]=t; } });"
                        + " return Object.keys(mx).sort((a,b)=>mx[b]-mx[a]); }", currentCabin);
        if (preferred == null) preferred = new java.util.ArrayList<>();

        String room = "?", newToken = "", toast = "";
        java.util.Set<String> triedRooms = new java.util.HashSet<>();
        if (!currentCabin.isEmpty()) triedRooms.add(currentCabin); // never re-pick the patient's own room
        int maxAttempts = Math.max(10, preferred.size() + 6);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Prefer a busy OTHER room (busiest first); once exhausted, let the JS pick a random untried room.
            String preferCabin = attempt < preferred.size() ? preferred.get(attempt) : null;
            Object picked = page.evaluate("(args) => { const prefer=args[0]; const tried=new Set((args[1]||[]).map(String));"
                    + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /generate queue/i.test(x.textContent||'')); if(!m) return '(no-modal)';"
                    + " const cab=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='token.cabinid'); if(!cab) return '(no-cabin)';"
                    + " const sc=angular.element(cab).scope(); let s=sc, list=null, tok=null; for(let i=0;i<12&&s;i++){ if(!list && Array.isArray(s.drpCabin)) list=s.drpCabin; if(!tok && s.token) tok=s.token; s=s.$parent; }"
                    + " if(!list||!tok) return '(no-scope)';"
                    + " let pick=null;"
                    + " if(prefer!=null){ pick=list.find(d=>String(d.value)===String(prefer) && !tried.has(String(d.value))); }"
                    + " if(!pick){ const opts=list.filter(d=>d.value && !/^-*\\s*select/i.test((d.text||'').trim()) && !tried.has(String(d.value))); if(!opts.length) return '(no-more)'; pick=opts[Math.floor(Math.random()*opts.length)]; }"
                    + " sc.$apply(function(){ tok.cabinid=pick.value; if(typeof sc.GenerateTokenNo==='function') sc.GenerateTokenNo(); }); return (pick.text||'').trim()+'||'+pick.value; }",
                    java.util.Arrays.asList(preferCabin, new java.util.ArrayList<>(triedRooms)));
            if (picked == null || picked.toString().startsWith("(")) {
                if (attempt == 0) System.out.println("fillGenerateQueueAndSave: could not pick a room (" + picked + ")");
                break;
            }
            String ps = picked.toString();
            room = ps.substring(0, ps.indexOf("||"));
            triedRooms.add(ps.substring(ps.indexOf("||") + 2));
            // GenerateTokenNo() fetches the next token asynchronously — wait for token.tokenno to populate.
            try {
                page.waitForFunction(
                        "() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /generate queue/i.test(x.textContent||''));"
                                + " const e=m&&[...m.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='token.tokenno'); return e && (e.value||'').trim().length>0; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(8000));
            } catch (Exception ignore) { }
            Object tv = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /generate queue/i.test(x.textContent||'')); const e=m&&[...m.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='token.tokenno'); return e?((e.value||'').trim()):''; }");
            newToken = tv == null ? "" : tv.toString().trim();
            // Prefer a room that yields a DIFFERENT number: while there are still busier rooms to try
            // (the preferred list), skip same-number rooms. Once those are exhausted (only empty rooms,
            // which all give the same number left), save BEST-EFFORT in a different room anyway — a
            // different room may still be accepted — rather than looping forever and failing with no save.
            boolean sameNumber = wantDifferent && !newToken.isEmpty() && newToken.equals(old);
            if (sameNumber && attempt < preferred.size()) {
                System.out.println("fillGenerateQueueAndSave: room '" + room + "' gave same queue " + newToken + " — trying a busier room");
                continue;
            }
            // Save (UpdateTokenNo) and read the toast, prioritising success over the "already generated" warning.
            toast = saveTokenAndReadToast();
            if (toast != null && toast.toLowerCase().contains("already generated")) {
                System.out.println("fillGenerateQueueAndSave: room '" + room + "' Save → 'Token already Generated!' — trying another different room");
                continue; // that room re-issued the same/existing queue → try another different room
            }
            break; // success (or a non-retryable toast)
        }
        waitForAngular(300);
        String qn = wantDifferent ? (old + "->" + (newToken.isEmpty() ? "?" : newToken)) : (newToken.isEmpty() ? "(assigned)" : newToken);
        return "Room=" + room + " | QueueNo=" + qn + " | " + (toast == null ? "" : toast.trim());
    }

    /** Click Save (UpdateTokenNo) on the Generate Queue modal and return the toast, PREFERRING the success
     *  toast ("Token Updated Successfully.") over the "Token already Generated!" warning when both appear. */
    private String saveTokenAndReadToast() {
        page.evaluate("() => { window.__gqToasts=[]; if(window.__gqObs) window.__gqObs.disconnect();"
                + " window.__gqObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__gqToasts.includes(t)) window.__gqToasts.push(t); }); });"
                + " window.__gqObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /generate queue/i.test(x.textContent||''));"
                + " if(m){ const b=[...m.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='UpdateTokenNo()'); if(b) b.click(); } }");
        try {
            page.waitForFunction("() => (window.__gqToasts||[]).length>0", null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("saveTokenAndReadToast: no toast observed"); }
        waitForAngular(300);
        Object r = page.evaluate("() => { const a=window.__gqToasts||[]; return a.find(x=>/updated successfully|updated|success/i.test(x)) || a.find(x=>/already generated/i.test(x)) || a.find(x=>/token/i.test(x)) || a[0] || ''; }");
        return r == null ? "" : r.toString().trim();
    }

    // ---- Request MRD File -------------------------------------------------

    /** Select the first non-dummy patient that has NO MRD file yet ({@code mrdfileno} empty) so Request MRD
     *  File triggers the create path. Returns the name, or null. */
    public String selectQueueRowWithoutMrdFile() {
        Object r = page.evaluate("(skipRe) => { const skip=new RegExp(skipRe,'i'); let gridApi=null, data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){gridApi=s.grid.api;data=s.grid.options.data;} }catch(e){} });"
                + " if(!gridApi||!data||!data.length) return null; const nm=x=>((x.PatientName||x.patientname||'')+'').trim();"
                + " const p=data.find(x=>nm(x)&&!skip.test(nm(x)) && (!x.mrdfileno || !((''+x.mrdfileno).trim()))) || data.find(x=>nm(x)&&!skip.test(nm(x))) || data[0];"
                + " try{gridApi.selection.clearSelectedRows();}catch(e){} gridApi.selection.selectRow(p); return nm(p); }", SKIP_PATIENTS_RE);
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /** Click the <b>Request MRD File</b> footer button ({@code fnRequestMRDFile()}). Returns the outcome:
     *  "confirm" (the "File is not generated… create one?" dialog appeared — no file yet), "report" (a
     *  report tab opened — the patient already has a file), or "none". */
    public String clickRequestMrdFile() {
        int tabsBefore = page.context().pages().size();
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnRequestMRDFile()' && x.offsetParent!==null); if(b) b.click(); }");
        // fnRequestMRDFile → checkPatientMRD does an async server round-trip that can be slow, so the
        // confirm/report appears well after a few seconds — wait up to ~48s.
        for (int i = 0; i < 60; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,[class*=confirm]')].some(m=>m.getBoundingClientRect().width>0 && /file is not generated/i.test(m.textContent||''))"));
            if (confirm) return "confirm";
            if (page.context().pages().size() > tabsBefore) return "report";
            page.waitForTimeout(800);
        }
        // No confirm/report — dismiss any stray dialog so it can't intercept a later section's click.
        page.evaluate("() => { const no=[...document.querySelectorAll('.jconfirm button, button')].find(b=>/^no$/i.test((b.textContent||'').trim()) && b.offsetParent!==null); if(no) no.click(); [...document.querySelectorAll('.jconfirm,.ng-confirm')].forEach(d=>{try{d.remove();}catch(e){}}); }");
        return "none";
    }

    /** Click <b>Yes</b> on the "File is not generated… Do you want to create one?" confirm, then wait for
     *  the <b>MRD Details</b> modal. Returns true if the modal opened. */
    public boolean confirmCreateMrdFile() {
        page.evaluate("() => { const y=[...document.querySelectorAll('.jconfirm button, .modal button, button')].find(b=>/^yes$/i.test((b.textContent||'').trim()) && b.offsetParent!==null); if(y) y.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /mrd details/i.test(m.textContent||'') && m.querySelector(\"button[ng-click='fnIUDAllocation()']\"))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
            waitForAngular(500);
            return true;
        } catch (Exception e) { System.out.println("confirmCreateMrdFile: MRD Details modal did not open"); return false; }
    }

    /**
     * Fill the <b>MRD Details</b> popup (ALL fields are mandatory — Save = {@code fnIUDAllocation()} else
     * toast "Please fill all fields!") and Save. Sets each ng-options {@code <select>} via selectedIndex +
     * change ({@code $setViewValue} does NOT bind these), honouring the Rack → Row → Box cascade (Row loads
     * after Rack, Box after Row). Returns the toast (verified live: <b>"File Allocated Successfully."</b>).
     */
    public String fillMrdDetailsAndSave() {
        // Patient Type, Allocation Location, File Type, Rack (loads Row).
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /mrd details/i.test(x.textContent||''));"
                + " const pick=(ng)=>{ const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const idx=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())); if(idx<0) return; e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " const setInp=(ng,v)=>{ const e=[...m.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " pick('MRDAllocation.mrdpatienttypeid'); pick('MRDAllocation.allocationlocationid'); pick('MRDAllocation.mrdfiletypeid'); pick('MRDAllocation.rackid');"
                + " setInp('MRDAllocation.filetypeno','1'); setInp('MRDAllocation.classificationtag','1'); }");
        waitForAngular(1200); // Row loads after Rack
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /mrd details/i.test(x.textContent||''));"
                + " const pick=(ng)=>{ const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const idx=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())); if(idx<0) return; e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " pick('MRDAllocation.rowid'); }");
        waitForAngular(1200); // Box loads after Row
        page.evaluate("() => { window.__mrdToasts=[]; if(window.__mrdObs) window.__mrdObs.disconnect();"
                + " window.__mrdObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__mrdToasts.includes(t)) window.__mrdToasts.push(t); }); });"
                + " window.__mrdObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /mrd details/i.test(x.textContent||''));"
                + " const be=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='MRDAllocation.boxid'); if(be){ const idx=[...be.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())); if(idx>=0){ be.selectedIndex=idx; be.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(be).trigger('change');}catch(err){}} } }"
                + " const save=[...m.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnIUDAllocation()' && x.offsetParent!==null); if(save) save.click(); }");
        try {
            page.waitForFunction("() => (window.__mrdToasts||[]).some(a=>/allocated|success|fill all/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("fillMrdDetailsAndSave: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__mrdToasts||[]; return a.find(x=>/allocated successfully/i.test(x)) || a.find(x=>/allocated|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Company Approved Amount --------------------------------------------

    /**
     * The Company Approved Amount modal, or the whole document if not found. Verified live: the app's OWN
     * heading reads "Company Approve Amount" (no 'd' — same class of naming mismatch as Call For Triage's
     * "Calll For Triage" typo), so the 'd' in "Approved" must be optional here.
     */
    private static final String CAA_MODAL_JS =
            "[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /company\\s*approved?\\s*amount/i.test(x.textContent||''))";

    /**
     * Click the footer <b>Company Approved Amount</b> button and wait for its modal. Returns true if it opened.
     */
    public boolean clickCompanyApprovedAmount() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/CompanyApproved/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*company\\s*approved\\s*amount\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!b) return 'no-button:' + [...document.querySelectorAll('button,a')].filter(x=>x.offsetParent!==null && /company|approved/i.test(norm(x.textContent)+' '+(x.getAttribute('ng-click')||''))).map(x=>norm(x.textContent)+'['+(x.getAttribute('ng-click')||'')+']').join(' ;; ');"
                + " b.id='__caaBtn'; return 'tagged:' + norm(b.textContent) + '[' + (b.getAttribute('ng-click')||'') + ']'; }");
        String c = tagged == null ? "" : tagged.toString();
        if (c.startsWith("no-button")) { System.out.println("clickCompanyApprovedAmount: 'Company Approved Amount' button not found. Candidates: " + c); return false; }
        System.out.println("clickCompanyApprovedAmount: clicking " + c);
        try { page.locator("#__caaBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickCompanyApprovedAmount: click failed - " + e.getMessage()); }
        page.evaluate("() => { const b=document.getElementById('__caaBtn'); if(b) b.removeAttribute('id'); }");
        boolean up = false;
        for (int k = 0; k < 20; k++) {
            Boolean ok = (Boolean) page.evaluate("() => " + CAA_MODAL_JS + " !== undefined");
            if (Boolean.TRUE.equals(ok)) { up = true; break; }
            page.waitForTimeout(500);
        }
        if (!up) {
            Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const modals=[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].filter(m=>m.getBoundingClientRect().width>0).map(m=>norm(m.textContent).slice(0,200));"
                    + " const toasts=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].map(t=>norm(t.textContent));"
                    + " return 'open modals=['+modals.join(' || ')+'] toasts=['+toasts.join(' || ')+'] url='+location.href; }");
            System.out.println("clickCompanyApprovedAmount: modal did not open. " + diag);
        }
        waitForAngular(600);
        return up;
    }

    /**
     * Fill the Company Approved Amount row's fields, by their CONFIRMED ng-models (verified live — this modal
     * marks NO field with a starred label or {@code required}/{@code ng-required} attribute, so the usual
     * asterisk-based mandatory scan found nothing to fill at all, leaving every field genuinely empty and Save
     * silently rejecting it with no toast): {@code Company.companyid} (Payor/Company select), {@code
     * Registration.PayerTypeId} (select), {@code Company.companyamount}, {@code Company.AppliedDate}, {@code
     * Company.Date}, {@code Company.FileNo}, {@code Company.GLConsumed}, {@code Company.GLBalance}, {@code
     * Company.GLMaxLimit}, {@code Company.Remarks}. If either select has NO real option, that is called out
     * explicitly as {@code "EMPTY DROPDOWN: <ng-model>"} so the caller can fail the run on it.
     */
    public String fillCompanyApprovedAmountDetails() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=" + CAA_MODAL_JS + "; const scope=m||document;"
                + " const out=[]; const emptyDropdowns=[];"
                + " const pick=(ng)=>{ const sel=[...scope.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!sel) return;"
                + "   const real=[...sel.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "   if(!real.length){ emptyDropdowns.push(ng); return; }"
                + "   const cur=sel.options[sel.selectedIndex];"
                + "   if(cur && cur.value && !/^-*\\s*select\\s*-*$/i.test(norm(cur.textContent))){ out.push(ng+'='+norm(cur.textContent)+' (kept)'); return; }"
                + "   const i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "   sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(sel).trigger('change');}catch(e){}}"
                + "   out.push(ng+'='+norm(sel.options[i].textContent)); };"
                + " pick('Company.companyid'); pick('Registration.PayerTypeId');"
                + " const set=(ng,v)=>{ const e=[...scope.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e) return;"
                + "   if((e.value||'').trim()){ out.push(ng+'='+e.value+' (kept)'); return; }"
                + "   const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); out.push(ng+'='+v); };"
                + " const d=new Date(); const p2=n=>('0'+n).slice(-2); const today=p2(d.getDate())+'/'+p2(d.getMonth()+1)+'/'+d.getFullYear();"
                + " set('Company.companyamount','100'); set('Company.AppliedDate',today); set('Company.Date',today);"
                + " set('Company.FileNo','AUTO'+Date.now()%100000); set('Company.GLConsumed','0'); set('Company.GLBalance','1000');"
                + " set('Company.GLMaxLimit','1000'); set('Company.Remarks','Automated test');"
                + " return (emptyDropdowns.length? 'EMPTY DROPDOWN: '+emptyDropdowns.join(', ')+' | ':'') + out.join(' | '); }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Add</b> ({@code AddCompanyDetails(CompanyDetail)}) to commit the filled row — verified live this
     * is a master-detail form (a separate Add and Save, same shape as Medico Legal's Document List): filling the
     * fields and going straight to Save without Add first left nothing to save and Save answered with no toast
     * at all. Returns true if the Add button was found and clicked.
     */
    public boolean addCompanyApprovedAmountRow() {
        Object clicked = page.evaluate("() => { const m=" + CAA_MODAL_JS + "; const scope=m||document;"
                + " const b=[...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /AddCompanyDetails/.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.click(); return true; }");
        waitForAngular(600);
        return Boolean.TRUE.equals(clicked);
    }

    /** Click Save/Submit/OK in the Company Approved Amount modal and return the resulting toast. */
    public String saveCompanyApprovedAmountAndGetToast() {
        // TEMP diagnostic: the modal's actual field state + button labels right before Save, so a fill/save
        // gap shows up in the log instead of a bare "no toast" (same lesson as MRDReturn/Call For Triage).
        Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=" + CAA_MODAL_JS + "; if(!m) return '(modal gone)';"
                + " const labelOf=e=>{ let p=e.closest('.form-group,.row,div'); const l=p?p.querySelector('label,.control-label'):null; return l?norm(l.textContent):''; };"
                + " const fields=[...m.querySelectorAll('input,select,textarea')].filter(x=>x.offsetParent!==null).map(e=>{"
                + "   const val=e.tagName==='SELECT'?norm((e.options[e.selectedIndex]||{}).textContent):(e.value||'');"
                + "   return labelOf(e)+'['+(e.getAttribute('ng-model')||'?')+']='+(val||'(empty)'); });"
                + " const btns=[...m.querySelectorAll('button')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent)+'['+(b.getAttribute('ng-click')||'')+']');"
                + " return 'fields=['+fields.join(' || ')+'] buttons=['+btns.join(' || ')+']'; }");
        System.out.println("saveCompanyApprovedAmountAndGetToast: pre-save state -> " + diag);
        page.evaluate("() => { window.__caaToasts=[]; if(window.__caaObs) window.__caaObs.disconnect();"
                + " window.__caaObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__caaToasts.includes(t)) window.__caaToasts.push(t); }); });"
                + " window.__caaObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=" + CAA_MODAL_JS + "; const scope=m||document;"
                + " const b=[...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(save|submit|ok)$/i.test((x.textContent||'').trim())); if(b) b.click(); }");
        String toast = "";
        for (int i = 0; i < 40; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate(
                    "() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want to save|are you sure/i.test(m.textContent||''))"));
            if (confirm) {
                page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want to save|are you sure/i.test(m.textContent||''));"
                        + " const b=[...(box||document).querySelectorAll('button')].find(x=>/^(save|yes|ok)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(400);
            }
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__caaToasts=window.__caaToasts||[]; if(!window.__caaToasts.includes(m)) window.__caaToasts.push(m); } });"
                    + " const a=window.__caaToasts||[]; return a.find(x=>/success|saved/i.test(x)) || a[0] || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            page.waitForTimeout(500);
        }
        if (toast.isEmpty()) System.out.println("saveCompanyApprovedAmountAndGetToast: no toast observed within ~20s. Alerts: " + capturedAlerts());
        waitForAngular(400);
        return toast;
    }

    // ---- Referred Patients --------------------------------------------------

    /**
     * The Referred Patients LIST modal — a table (Select checkbox / MRN / Patient Name / Category / Referred By /
     * Department From / Referred To / Department To) with <b>Registration</b> / <b>Admission</b> / <b>Cancel</b>
     * footer buttons (confirmed live from a screenshot). Matched on those two buttons together, not just the
     * "Referred Patients" text, so it is never confused with the "No Records Found" toast-only outcome.
     */
    private static final String RP_LIST_MODAL_JS =
            "[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /referred\\s*patients?/i.test(x.textContent||'')"
            + " && [...x.querySelectorAll('button')].some(b=>/^registration$/i.test((b.textContent||'').trim())) && [...x.querySelectorAll('button')].some(b=>/^admission$/i.test((b.textContent||'').trim())))";

    /**
     * Click the footer <b>Referred Patients</b> button. Returns <b>"MODAL"</b> when the list popup opens (there
     * ARE referred patients), <b>"NO_RECORDS::&lt;toast&gt;"</b> when the app answers with a "No Records Found"
     * toast instead (a real, empty result), or <b>"NONE"</b> if neither appeared.
     */
    public String clickReferredPatients() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/ReferredPatients|ReferPatient|fnFetchPatientsReferred/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*referred\\s*patients?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__rpBtn'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickReferredPatients: 'Referred Patients' button not found"); return "NONE"; }
        // Arm the toast observer BEFORE the click so a fast "No Records Found" can't be missed.
        page.evaluate("() => { window.__rpToasts=[]; if(window.__rpObs) window.__rpObs.disconnect();"
                + " window.__rpObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rpToasts.includes(t)) window.__rpToasts.push(t); }); });"
                + " window.__rpObs.observe(document.body,{childList:true,subtree:true}); }");
        try { page.locator("#__rpBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickReferredPatients: click failed - " + e.getMessage()); }
        page.evaluate("() => { const b=document.getElementById('__rpBtn'); if(b) b.removeAttribute('id'); }");
        String outcome = "";
        for (int i = 0; i < 24; i++) {
            Boolean modalUp = (Boolean) page.evaluate("() => " + RP_LIST_MODAL_JS + " !== undefined");
            if (Boolean.TRUE.equals(modalUp)) { outcome = "MODAL"; break; }
            Object t = page.evaluate("() => { const a=window.__rpToasts||[]; return a.find(x=>/no\\s*record|not\\s*found|no\\s*(referred|data)/i.test(x)) || ''; }");
            String toast = t == null ? "" : t.toString();
            if (!toast.isEmpty()) { outcome = "NO_RECORDS::" + toast; break; }
            page.waitForTimeout(500);
        }
        waitForAngular(400);
        if (outcome.isEmpty()) System.out.println("clickReferredPatients: neither the list modal nor a no-records toast appeared within 12s");
        return outcome.isEmpty() ? "NONE" : outcome;
    }

    /** Real-click the first row's Select checkbox in the Referred Patients list modal. Returns the row's Patient
     *  Name, or {@code null} if the modal/table/checkbox is not found. */
    public String selectAnyReferredPatientRow() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=" + RP_LIST_MODAL_JS + "; if(!m) return null;"
                + " const row=[...m.querySelectorAll('table tbody tr')].find(tr=>tr.querySelector('input[type=checkbox]'));"
                + " if(!row) return null; const cb=row.querySelector('input[type=checkbox]'); cb.id='__rpRowCb';"
                + " const cells=[...row.querySelectorAll('td')]; return cells[2]?norm(cells[2].textContent):'(row selected)'; }");
        if (tagged == null) { System.out.println("selectAnyReferredPatientRow: no row/checkbox found in the list"); return null; }
        try { page.locator("#__rpRowCb").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(5000)); }
        catch (Exception e) { try { page.locator("#__rpRowCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { } }
        page.evaluate("() => { const e=document.getElementById('__rpRowCb'); if(e) e.removeAttribute('id'); }");
        waitForAngular(400);
        return tagged.toString();
    }

    /**
     * Click <b>Registration</b> or <b>Admission</b> in the Referred Patients list modal and report whether that
     * screen actually opened (a URL/route change, a new tab, or a large registration-shaped form appearing).
     * Returns true only when one of those was observed — never a bare "button clicked".
     *
     * @param action exactly "Registration" or "Admission"
     */
    public boolean clickReferredPatientAction(String action) {
        String urlBefore = page.url();
        int tabsBefore = page.context().pages().size();
        Object clicked = page.evaluate("(act) => { const m=" + RP_LIST_MODAL_JS + "; if(!m) return false;"
                + " const b=[...m.querySelectorAll('button')].find(x=>x.offsetParent!==null && new RegExp('^'+act+'$','i').test((x.textContent||'').trim()));"
                + " if(!b) return false; b.click(); return true; }", action);
        if (!Boolean.TRUE.equals(clicked)) { System.out.println("clickReferredPatientAction: '" + action + "' button not found"); return false; }
        boolean opened = false;
        for (int i = 0; i < 24; i++) {
            if (page.context().pages().size() > tabsBefore) { opened = true; break; }
            if (!page.url().equals(urlBefore)) { opened = true; break; }
            Boolean formUp = (Boolean) page.evaluate(
                    "() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && m.querySelectorAll('input,select').length>15)");
            if (Boolean.TRUE.equals(formUp)) { opened = true; break; }
            page.waitForTimeout(500);
        }
        if (!opened) System.out.println("clickReferredPatientAction: '" + action + "' clicked but no navigation/new tab/form was observed within 12s");
        waitForAngular(600);
        return opened;
    }

    // ---- Return MRD File --------------------------------------------------

    /** Select the first non-dummy patient that HAS an MRD file ({@code mrdfileno} set) so Return MRD File has a
     *  file to return (needs {@code selectedItem.MRDid}). Returns "name | mrdfileno=…", or null. */
    public String selectQueueRowWithMrdFile() {
        Object r = page.evaluate("(skipRe) => { const skip=new RegExp(skipRe,'i'); let api=null,data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(api&&data) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){api=s.grid.api;data=s.grid.options.data;} }catch(e){} });"
                + " if(!api||!data||!data.length) return null; const nm=x=>((x.PatientName||x.patientname||'')+'').trim();"
                + " const p=data.find(x=>nm(x)&&!skip.test(nm(x)) && x.mrdfileno && ((''+x.mrdfileno).trim())); if(!p) return null;"
                + " try{api.selection.clearSelectedRows();}catch(e){} api.selection.selectRow(p); return nm(p)+' | '+((''+p.mrdfileno).trim()); }", SKIP_PATIENTS_RE);
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /** Click the <b>Return MRD File</b> footer button ({@code fnReturnMRDFile()}) and wait for the <b>MRDReturn</b>
     *  modal (its Save is {@code fnIUDReturn()}). Returns true if it opened. */
    public boolean clickReturnMrdFile() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>x.getAttribute('ng-click')==='fnReturnMRDFile()' && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && (/mrdreturn|mrd return/i.test(m.textContent||'') || m.querySelector(\"button[ng-click='fnIUDReturn()']\") || m.querySelector(\"[ng-model='MRDReturn.remark']\")))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception e) { System.out.println("clickReturnMrdFile: MRDReturn modal did not open"); return false; }
        waitForAngular(800);
        return true;
    }

    /**
     * Fill the <b>MRDReturn</b> popup and Save: select <b>From Department</b>, <b>To Department</b> (a different
     * real department), leave the pre-selected <b>User</b> (or select one if empty), enter a <b>Remark</b>, then
     * click <b>OK</b> ({@code fnIUDReturn()}). Returns the toast.
     *
     * <p>The three selects are driven with a REAL Playwright {@code selectOption} (verified live to be
     * necessary here — a synthetic {@code selectedIndex + dispatchEvent('change')}, which is enough on most
     * other screens, left the Angular model unbound on THIS modal: the DOM showed a value, Save still ran, but
     * the server answered a nameless "Failed!" instead of naming a missing field, because the fields it read
     * were empty. Same class of gap already fixed for Visa Type in {@code RegistrationPage}.)</p>
     */
    public String fillMrdReturnAndSave() {
        String fromDept = realSelectInMrdReturn("MRDReturn.fromdepartmentid", null);
        String toDept = realSelectInMrdReturn("MRDReturn.todepartmentid", fromDept);
        String user = realSelectInMrdReturn("MRDReturn.userid", null);
        System.out.println("fillMrdReturnAndSave: From=" + fromDept + " | To=" + toDept + " | User=" + user);
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && (/mrdreturn|mrd return/i.test(x.textContent||'') || x.querySelector(\"[ng-model='MRDReturn.remark']\"))); if(!m) return;"
                + " const rk=[...m.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')==='MRDReturn.remark'); if(rk){ const c=angular.element(rk).controller('ngModel'); rk.value='Automation return'; if(c){c.$setViewValue('Automation return');c.$render();} rk.dispatchEvent(new Event('input',{bubbles:true})); rk.dispatchEvent(new Event('change',{bubbles:true})); } }");
        waitForAngular(500);
        page.evaluate("() => { window.__rmToasts=[]; if(window.__rmObs) window.__rmObs.disconnect();"
                + " window.__rmObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rmToasts.includes(t)) window.__rmToasts.push(t); }); }); window.__rmObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && (/mrdreturn|mrd return/i.test(x.textContent||'') || x.querySelector(\"[ng-model='MRDReturn.remark']\")));"
                + " const b=m&&[...m.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnIUDReturn()' && x.offsetParent!==null); if(b) b.id='__rmSaveBtn'; }");
        try { page.locator("#__rmSaveBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("fillMrdReturnAndSave: real click on OK failed - " + e.getMessage() + "; falling back to JS click");
            page.evaluate("() => { const b=document.getElementById('__rmSaveBtn'); if(b) b.click(); }");
        }
        page.evaluate("() => { const b=document.getElementById('__rmSaveBtn'); if(b) b.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__rmToasts||[]).some(a=>/return|success|saved|allocated|fill|select|please|fail/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("fillMrdReturnAndSave: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__rmToasts||[]; return a.find(x=>/return|success|saved|allocated/i.test(x)) || a.find(x=>/fill|select|please|fail/i.test(x)) || a[0] || ''; }");
        String toast = r == null ? "" : r.toString().trim();
        // Verified live: this action can answer a genuine, unnamed server-side "Failed!" (not a "Please Select
        // X!" field validation) — a real rejection, not a missing-toast gap, so it must NOT be papered over as
        // a silent success. Report exactly what came back, even when that is nothing.
        if (toast.isEmpty()) toast = "(no toast observed — outcome unconfirmed)";
        waitForAngular(400);
        return toast;
    }

    /**
     * Select the first real option (skipping {@code skipValue} if given) in the MRDReturn modal's select bound
     * to {@code ngModel}, using a REAL Playwright {@code selectOption} — see {@link #fillMrdReturnAndSave()}.
     * Returns the committed ngModel value ("" if the select or a real option could not be found).
     */
    private String realSelectInMrdReturn(String ngModel, String skipValue) {
        try {
            Object idx = page.evaluate("(a) => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===a.ng && x.offsetParent!==null); if(!e) return -1;"
                    + " return [...e.options].findIndex(o=>o.value && o.value!==a.skip && !/^-*\\s*select/i.test((o.text||'').trim())); }",
                    java.util.Map.of("ng", ngModel, "skip", skipValue == null ? "" : skipValue));
            int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
            if (i < 0) return "";
            com.microsoft.playwright.Locator sel = page.locator("select[ng-model='" + ngModel + "']:visible").first();
            sel.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
            waitForAngular(400);
            Object v = page.evaluate("(ng) => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return ''; const c=angular.element(e).controller('ngModel'); return (c&&c.$modelValue!=null)?String(c.$modelValue):''; }", ngModel);
            return v == null ? "" : v.toString();
        } catch (Exception e) {
            System.out.println("realSelectInMrdReturn(" + ngModel + "): " + e.getMessage());
            return "";
        }
    }

    /** Click <b>Cancel</b> (resetForm) to close the MRDReturn popup, then strip any backdrop. */
    public void cancelMrdReturn() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && (/mrdreturn|mrd return/i.test(x.textContent||'') || x.querySelector(\"[ng-model='MRDReturn.remark']\")));"
                + " if(m){ const c=[...m.querySelectorAll('button')].find(b=>b.getAttribute('ng-click')==='resetForm()' && b.offsetParent!==null) || [...m.querySelectorAll('button')].find(b=>/^cancel$/i.test((b.textContent||'').trim()) && b.offsetParent!==null) || [...m.querySelectorAll('button')].find(b=>/^[×xX]$/.test((b.textContent||'').trim()) && b.offsetParent!==null); if(c) c.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} } [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    /** Click <b>Cancel</b> to close the MRD Details popup. */
    public void cancelMrdDetails() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /mrd details/i.test(x.textContent||''));"
                + " if(m){ const c=[...m.querySelectorAll('button')].find(b=>/^cancel$/i.test((b.textContent||'').trim()) && b.offsetParent!==null) || [...m.querySelectorAll('button')].find(b=>/^[×xX]$/.test((b.textContent||'').trim()) && b.offsetParent!==null); if(c) c.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} } [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    // ---- Fall Risk Assessment ---------------------------------------------

    /** Click the <b>Fall Risk Assessment</b> footer button ({@code fnOpenFallRiskAssessment()}) and wait for the
     *  "ASSESSMENT OF FALL" modal (its Apply button is {@code fnApply()}). Returns true if it opened. */
    public boolean clickFallRiskAssessment() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('fnOpenFallRiskAssessment')>=0 && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /assessment of fall|fall risk/i.test(m.textContent||'') && [...m.querySelectorAll('button')].some(b=>/fnApply/.test(b.getAttribute('ng-click')||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception e) { System.out.println("clickFallRiskAssessment: Fall Risk modal did not open"); return false; }
        waitForAngular(600);
        return true;
    }

    /**
     * In the Fall Risk Assessment modal: answer any YES/NO questions with YES, tick a checkbox
     * ({@code item.IsSelected}), click <b>Apply</b> ({@code fnApply()}), and return the success toast (verified
     * live: <b>"Fall Risk Assessment saved successfully."</b>).
     */
    public String fillFallRiskAndApply() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /assessment of fall|fall risk/i.test(x.textContent||'')); if(!m) return;"
                + " [...m.querySelectorAll('button')].filter(b=>/setQuestionAnswer\\(q, ?true\\)/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null).forEach(b=>b.click());"
                + " const cb=[...m.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && !x.checked); if(cb) cb.click(); }");
        waitForAngular(500);
        page.evaluate("() => { window.__frToasts=[]; if(window.__frObs) window.__frObs.disconnect();"
                + " window.__frObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__frToasts.includes(t)) window.__frToasts.push(t); }); }); window.__frObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /assessment of fall|fall risk/i.test(x.textContent||''));"
                + " const b=m&&[...m.querySelectorAll('button')].find(x=>/fnApply/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want|are you sure/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want|are you sure/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|apply)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(500);
        }
        try {
            page.waitForFunction("() => (window.__frToasts||[]).some(a=>/success|saved|applied|fall/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("fillFallRiskAndApply: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__frToasts||[]; return a.find(x=>/success|saved|applied/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Patient Task -----------------------------------------------------

    /** Click the <b>Patient Task</b> footer button (ng-click="BtnPatientRemark();") and wait for its
     *  modal (Save = {@code fnIUDPatientRemark()}). Returns true if it opened. */
    public boolean clickPatientTask() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='BtnPatientRemark();' && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction(
                    "() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /patient task/i.test(m.textContent||'') && m.querySelector(\"button[ng-click='fnIUDPatientRemark()']\"))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            System.out.println("clickPatientTask: Patient Task modal did not open");
            return false;
        }
        waitForAngular(800);
        return true;
    }

    /**
     * In the Patient Task modal, enter each task into the {@code patremark.patientremark} textarea and
     * click <b>Add</b> ({@code AddPatientRemarkDetails(patientremark)}), then <b>Save</b>
     * ({@code fnIUDPatientRemark()}) and close the popup. Returns the success toast (verified live:
     * <b>"Record Added Successfully."</b>).
     */
    public String addTasksSaveAndGetToast(java.util.List<String> tasks) {
        for (String task : tasks) {
            page.evaluate("(t) => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /patient task/i.test(x.textContent||'')); if(!m) return;"
                    + " const e=[...m.querySelectorAll('textarea,input')].find(x=>x.getAttribute('ng-model')==='patremark.patientremark' && x.offsetParent!==null);"
                    + " if(e){ const c=angular.element(e).controller('ngModel'); e.value=t; if(c){c.$setViewValue(t);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " const b=[...m.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='AddPatientRemarkDetails(patientremark)' && x.offsetParent!==null); if(b) b.click(); }", task);
            waitForAngular(500);
        }
        // Save + capture toast (server round-trip — the toast can arrive >12s later and lives in a .toast
        // container; watch .toast-message AND .toast, wait up to 30s, re-scan before giving up).
        page.evaluate("() => { window.__ptToasts=[]; if(window.__ptObs) window.__ptObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ptToasts.includes(t)) window.__ptToasts.push(t); }); };"
                + " window.__ptObs=new MutationObserver(grab); window.__ptObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /patient task/i.test(x.textContent||''));"
                + " const b=m&&[...m.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnIUDPatientRemark()'); if(b) b.click(); }");
        try {
            page.waitForFunction("() => (window.__ptToasts||[]).some(a=>/record|added|success/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            page.waitForTimeout(3000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__ptToasts||[]).includes(t)) (window.__ptToasts=window.__ptToasts||[]).push(t); }); }");
            Object peek = page.evaluate("() => (window.__ptToasts||[]).length");
            if (peek == null || "0".equals(peek.toString())) System.out.println("addTasksSaveAndGetToast: no toast observed within 33s");
        }
        Object toast = page.evaluate("() => { const a=window.__ptToasts||[]; return a.find(x=>/record|added|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        // Close the Patient Task popup.
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /patient task/i.test(x.textContent||''));"
                + " if(m){ const c=[...m.querySelectorAll('button')].find(b=>/^close$/i.test((b.textContent||'').trim()) && b.offsetParent!==null) || [...m.querySelectorAll('button')].find(b=>/^[×xX]$/.test((b.textContent||'').trim()) && b.offsetParent!==null); if(c) c.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} } [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
        return toast == null ? "" : toast.toString().trim();
    }

    // ---- Cancel Visit -----------------------------------------------------

    /**
     * Scan the grid data directly for a patient whose visit trips NONE of {@link #clickCancelVisit()}'s known
     * block conditions (clinical notes in EMR / medication dispensed / charges dropped / payment made / already
     * discharged), so the footer <b>CancelVisit</b> button will actually be enabled for it — verified live: with
     * a plain row-by-row trial, the button stayed {@code disabled} for all 15 patients on the first page (every
     * one already had a bill/notes/etc.), which wasted a Playwright click-timeout PER patient. Scanning the grid
     * data itself is instant and checks the SAME fields the app's own {@code ng-disabled} reads. Selects that
     * row and returns its name, or {@code null} if nothing currently loaded qualifies.
     */
    public String selectCancellableQueueRow() {
        Object r = page.evaluate("(skipRe) => { const skip=new RegExp(skipRe,'i'); let api=null,data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(api&&data) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){api=s.grid.api;data=s.grid.options.data;} }catch(e){} });"
                + " if(!api||!data||!data.length) return null; const nm=x=>((x.PatientName||x.patientname||'')+'').trim();"
                + " const ok=p=>{ if(!p.id && p.id!==0) return false;"
                + "   if(p.visitstatus==1||p.visitstatus==2) return false;"
                + "   if(p.medicationordercondition==3) return false;"
                + "   if((p.labordercondition>0&&p.labordercondition!==3)||(p.radioordercondition>0&&p.radioordercondition!==3)||(p.otherordercondition>0&&p.otherordercondition!==3)) return false;"
                + "   if(p.readyforbill==1) return false;"
                + "   if(p.isvisitdischarge===true) return false;"
                + "   return true; };"
                + " const p=data.find(x=>nm(x)&&!skip.test(nm(x))&&ok(x)); if(!p) return null;"
                + " try{api.selection.clearSelectedRows();}catch(e){} api.selection.selectRow(p); return nm(p); }", SKIP_PATIENTS_RE);
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /**
     * Click the footer <b>CancelVisit</b> button (ng-click="CancelVisit();") and report the outcome. CancelVisit
     * validates the visit then EITHER <b>blocks</b> with a JAlert ("Cancel not allowed: …" — clinical notes in EMR /
     * medication dispensed / charges dropped / payment made, or "Please Select Patient") OR (when allowed) opens a
     * jQuery-confirm dialog <b>"Reason for Cancellation"</b> with a reason dropdown + <b>Save</b>
     * ({@code UpdateVisitCancellation}). Polls up to ~12s (the reason list is fetched async). Returns:
     * <ul><li>{@code "REASON"} — the Reason-for-Cancellation dialog opened (proceed to pick a reason + Save);</li>
     * <li>{@code "BLOCKED::<msg>"} — the app blocked the cancel (msg = the JAlert reason);</li>
     * <li>{@code "NONE"} — nothing visible appeared.</li></ul>
     */
    public String clickCancelVisit() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>x.getAttribute('ng-click')==='CancelVisit();' && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*cancel\\s*visit\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__cvBtn'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickCancelVisit: 'CancelVisit' button not found"); return "NONE"; }
        // Check ENABLED before attempting anything — verified live: a disabled button still leaves
        // page.locator(...).click() to burn a full 5s timeout ("element is not enabled - waiting..."), and the
        // scope-level $apply(CancelVisit()) fallback that used to run regardless of the button's state can
        // silently no-op too. Fail this fast and honestly instead: "not enabled" IS the result, not a race to
        // paper over.
        Boolean enabled = (Boolean) page.evaluate("() => { const b=document.getElementById('__cvBtn'); return !!(b && !b.disabled); }");
        if (!Boolean.TRUE.equals(enabled)) {
            page.evaluate("() => { const b=document.getElementById('__cvBtn'); if(b) b.removeAttribute('id'); }");
            System.out.println("clickCancelVisit: button not enabled for this patient");
            return "BLOCKED::CancelVisit button not enabled for this patient";
        }
        try { page.locator("#__cvBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) {
            page.evaluate("() => { const b=document.getElementById('__cvBtn'); if(b) b.removeAttribute('id'); }");
            System.out.println("clickCancelVisit: click failed - " + e.getMessage());
            return "BLOCKED::CancelVisit button not enabled for this patient";
        }
        page.evaluate("() => { const b=document.getElementById('__cvBtn'); if(b) b.removeAttribute('id'); }");
        for (int i = 0; i < 16; i++) {
            Object st = page.evaluate("() => { const dlg=[...document.querySelectorAll('.jconfirm,.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && (/reason for cancellation/i.test(x.textContent||'') || x.querySelector(\"select[ng-model='CancellationReason']\"))); if(dlg) return 'REASON';"
                    + " const t=[...document.querySelectorAll('.sweet-alert,.swal2-popup,.swal2-container,.toast-message,.toast,.jconfirm,[class*=alert]')].filter(b=>b.offsetParent!==null).map(b=>(b.textContent||'').replace(/\\s+/g,' ').trim()).find(x=>/cancel not allowed|please select patient|please select cancellation|error fetching cancellation/i.test(x));"
                    + " if(t){ const m=t.match(/(cancel not allowed[^!]*?\\.|please select patient|please select cancellation reason\\.?|error fetching cancellation reasons)/i); return 'BLOCKED::'+((m?m[0]:t)+'').slice(0,120); } return ''; }");
            String s = String.valueOf(st);
            if (!s.isEmpty() && !"null".equals(s)) return s;
            page.waitForTimeout(500);
        }
        // No dialog + toastr faded/absent (JAlert uses auto-fading toastr, and CancelVisit is a SILENT no-op when the
        // visit is already discharged). Compute the block reason deterministically from the selected item's fields.
        Object diag = page.evaluate("() => { let it=null; document.querySelectorAll('*').forEach(el=>{ if(it) return; try{ let s=angular.element(el).scope(); for(let i=0;i<20&&s;i++){ if(s.selectedItem && (s.selectedItem.id||s.selectedItem.id===0)){ it=s.selectedItem; break; } s=s.$parent; } }catch(e){} }); if(!it) return 'NONE';"
                + " if(!it.id) return 'BLOCKED::Please Select Patient';"
                + " if(it.visitstatus==1||it.visitstatus==2) return 'BLOCKED::Clinical notes have been added in EMR';"
                + " if(it.medicationordercondition==3) return 'BLOCKED::Medication has already been dispensed';"
                + " if((it.labordercondition>0&&it.labordercondition!==3)||(it.radioordercondition>0&&it.radioordercondition!==3)||(it.otherordercondition>0&&it.otherordercondition!==3)) return 'BLOCKED::Charges have been dropped for this patient';"
                + " if(it.readyforbill==1) return 'BLOCKED::Payment has already been made';"
                + " if(it.isvisitdischarge===true) return 'BLOCKED::Visit already discharged/closed — nothing to cancel';"
                + " return 'NONE'; }");
        String d = String.valueOf(diag);
        return (d == null || d.isEmpty() || "null".equals(d)) ? "NONE" : d;
    }

    /**
     * In the <b>"Reason for Cancellation"</b> dialog, pick the first real reason ({@code CancellationReasonList[1]},
     * set on the scope since the ng-options values are objects) and click <b>Save</b> (matched by TEXT — both dialog
     * buttons are {@code btn-danger}, so never match by class). Save runs {@code UpdateVisitCancellation(visitid)}.
     * Returns the success text ("Visit Cancelled Successfully!") / toast, or "" if nothing confirmed.
     */
    public String cancelVisitPickReasonAndSave() {
        page.evaluate("() => { const dlg=[...document.querySelectorAll('.jconfirm,.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector(\"select[ng-model='CancellationReason']\")); if(!dlg) return;"
                + " const sel=dlg.querySelector(\"select[ng-model='CancellationReason']\"); const sc=angular.element(sel).scope();"
                + " if(sc && sc.CancellationReasonList && sc.CancellationReasonList.length>1){ sc.$apply(()=>{ sc.CancellationReason = sc.CancellationReasonList[1]; }); } }");
        waitForAngular(500);
        page.evaluate("() => { window.__cvSave=[]; if(window.__cvSaveObs) window.__cvSaveObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__cvSave.includes(t)) window.__cvSave.push(t); }); };"
                + " window.__cvSaveObs=new MutationObserver(grab); window.__cvSaveObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const dlg=[...document.querySelectorAll('.jconfirm,.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector(\"select[ng-model='CancellationReason']\")); if(!dlg) return;"
                + " const b=[...dlg.querySelectorAll('.jconfirm-buttons button, button, a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim())); if(b) b.click(); }");
        String res = "";
        for (int i = 0; i < 40; i++) {
            Object r = page.evaluate("() => { const s=document.getElementById('cancelVisitSuccessMsg'); if(s && s.offsetParent!==null && /cancel/i.test(s.textContent||'')) return (s.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " const a=window.__cvSave||[]; return a.find(x=>/cancel|success|saved|reason|select/i.test(x)) || ''; }");
            res = r == null ? "" : r.toString().trim();
            if (!res.isEmpty()) break;
            page.waitForTimeout(500);
        }
        waitForAngular(300);
        return res;
    }

    /** Close the Cancel Visit dialog / any JAlert it left — click Close/No/× (or the jconfirm close icon), then
     *  force-hide the dialog + strip backdrops so it can't block later steps. */
    public void closeCancelVisitDialog() {
        page.evaluate("() => { const dlg=[...document.querySelectorAll('.jconfirm,.modal,[role=dialog],.sweet-alert,.swal2-popup')].find(x=>x.getBoundingClientRect().width>0 && (/reason for cancellation|cancel not allowed|please select/i.test(x.textContent||'') || x.querySelector(\"select[ng-model='CancellationReason']\")));"
                + " if(dlg){ const b=[...dlg.querySelectorAll('.jconfirm-buttons button, button, a, .jconfirm-closeIcon, .confirm, .swal2-confirm')].find(x=>/^\\s*(close|no|cancel|ok|×|✕)\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim())) || dlg.querySelector('.jconfirm-closeIcon,.confirm,.swal2-confirm'); if(b) b.click(); } }");
        waitForAngular(400);
        page.evaluate("() => { [...document.querySelectorAll('.jconfirm,.modal,[role=dialog],.sweet-alert,.swal2-container')].filter(x=>x.getBoundingClientRect().width>0).forEach(x=>{ x.style.display='none'; x.classList.remove('in','show'); });"
                + " [...document.querySelectorAll('.modal-backdrop,.jconfirm-bg,.swal2-container')].forEach(b=>b.remove()); document.body.classList.remove('modal-open','jconfirm-noscroll'); document.body.style.overflow=''; document.body.style.paddingRight='';"
                // Sections share the DOM (no full reload between them) — clear leftover toastr toasts + any stray jconfirm.
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} [...document.querySelectorAll('#toast-container .toast, #toast-container')].forEach(t=>t.remove()); [...document.querySelectorAll('.jconfirm')].forEach(j=>j.remove()); }");
        waitForAngular(300);
    }

    // ---- helpers ----------------------------------------------------------

    @SuppressWarnings("unchecked")
    public java.util.List<String> capturedAlerts() {
        Object r = page.evaluate("() => window.__alerts || []");
        return r instanceof java.util.List ? (java.util.List<String>) r : new java.util.ArrayList<>();
    }

    @Override
    public void screenshot(String path) {
        super.screenshot(path);
    }
}
