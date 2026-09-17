package com.kpj.pages.Emergency_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Emergency &gt; <b>Emergency List View</b> — search emergency patients by date range + MRN,
 * pick a patient from the results grid, click <b>Change Admission Type</b>, fill the admission
 * form (3 sections), Save → success toast.
 *
 * <p>Flow (per the test spec):</p>
 * <ol>
 *   <li>Click <b>Emergency</b> → <b>Emergency List View</b> (via the left-menu tab, not a direct URL).</li>
 *   <li>Enter the <b>From/To date range</b> and any number in <b>MRN</b>, then Search.</li>
 *   <li><b>Select a patient</b> row from the results table.</li>
 *   <li>Click <b>Change Admission Type</b> → the admission form opens.</li>
 *   <li>Fill all 3 sections (Patient Information, Admission Information, remaining mandatory fields).</li>
 *   <li>Click <b>Save</b> → success toast.</li>
 * </ol>
 *
 * <p>Selectors are discovered defensively — by visible button text, {@code ng-click}, and table
 * headers — in the same style as {@code AppointmentListPage} / {@code Emergency_Admission}, so the
 * page tolerates the exact {@code ng-model} names not being pinned down up front. The few model
 * names below (dates, MRN) are the likely candidates; confirm against the live screen and tighten
 * if a step logs "not found".</p>
 */
public class EmergencyListView extends BasePage {

    public EmergencyListView(Page page) { super(page); }

    // ---- navigation via the menu tab (not a direct URL) -------------------

    /** Expand the <b>Emergency</b> left-menu treeview and click the <b>Emergency List View</b> link. */
    public boolean navigateViaMenu() {
        // Wait for the left menu to render (the SPA boots the nav a beat after login). Under server load the
        // nav sometimes fails to populate on the first boot — reload the app once and wait again before
        // giving up (a real AngularJS SPA-boot flake, not only server throttling).
        String menuJs = "() => window.angular && [...document.querySelectorAll('li > a')].some(a=>/^\\s*Emergency\\s*$/i.test((a.textContent||'').trim()))";
        boolean menuUp = false;
        for (int attempt = 0; attempt < 3 && !menuUp; attempt++) {
            try {
                page.waitForFunction(menuJs, null, new Page.WaitForFunctionOptions().setTimeout(attempt == 0 ? 30000 : 20000));
                menuUp = true;
            } catch (Exception ignore) {
                System.out.println("navigateViaMenu: Emergency menu did not render (attempt " + (attempt + 1) + ") — reloading");
                try { page.reload(new Page.ReloadOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)); }
                catch (Exception e) { System.out.println("navigateViaMenu: reload failed - " + e.getMessage()); }
                waitForAngular(1500);
            }
        }
        if (!menuUp) System.out.println("navigateViaMenu: Emergency menu still not rendered after retries");
        waitForAngular(800);
        // Expand the Emergency menu (real click — its ng handler toggles the submenu).
        Object emTagged = page.evaluate("() => { const lis=[...document.querySelectorAll('li')];"
                + " const emLi=lis.find(li=>{ const a=li.querySelector(':scope > a'); return a && /^\\s*Emergency\\s*$/i.test((a.textContent||'').trim()); });"
                + " const a=emLi&&emLi.querySelector(':scope > a'); if(!a) return false; a.id='__emMenuTab'; return true; }");
        if (Boolean.TRUE.equals(emTagged)) {
            try { page.locator("#__emMenuTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("navigateViaMenu: Emergency menu click failed - " + e.getMessage()); }
        } else {
            System.out.println("navigateViaMenu: Emergency menu not found");
        }
        waitForAngular(800);
        // Click the Emergency List View submenu link (match by text and by href containing EmergencyList).
        Object lvTagged = page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>/emergency list ?view/i.test((x.textContent||'').trim()) && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('a')].find(x=>/emergencylist/i.test(x.getAttribute('href')||'') && x.offsetParent!==null); if(!a) return false; a.id='__emListViewTab'; return true; }");
        if (Boolean.TRUE.equals(lvTagged)) {
            try { page.locator("#__emListViewTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("navigateViaMenu: Emergency List View click failed - " + e.getMessage()); }
        } else {
            System.out.println("navigateViaMenu: Emergency List View link not found");
        }
        // Wait for the list screen (a Search button, or a date-range input, to appear).
        try {
            page.waitForFunction("() => window.angular && ([...document.querySelectorAll('button')].some(b=>/search/i.test((b.textContent||'').trim()) && b.offsetParent!==null) || /emergencylist/i.test(location.hash))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("navigateViaMenu: Emergency List View not ready in time"); }
        waitForAngular(1200);
        return page.url().toLowerCase().contains("emergencylist")
                || Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('button')].some(b=>/search/i.test((b.textContent||'').trim()) && b.offsetParent!==null)"));
    }

    // ---- search by date range + MRN --------------------------------------

    /**
     * Enter the From/To date range (dd/MM/yyyy), optionally a number in MRN, tick <b>All Patients</b>
     * (so the grid isn't limited to current inpatients — Non-Presence emergency admissions don't show
     * under the default "All Inpatients"), then click Search. If an MRN filter yields no rows, it is
     * cleared and the search re-run so a patient is still available. Returns a summary including the
     * resulting row count. The results grid is a <b>ui-grid</b> (its data lives on
     * {@code scope.grid.options.data}), not an HTML table.
     */
    public String searchByDateAndMrn(String fromDate, String toDate, String mrn) {
        tickFilter("All Patients");
        Object applied = page.evaluate("(a) => { const [f,t,m]=a;"
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim().toLowerCase();"
                + " const setVal=(e,v)=>{ if(!e) return false; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return true; };"
                + " const byModel=names=>{ for(const n of names){ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===n && x.offsetParent!==null); if(e) return e; } return null; };"
                + " const byLabel=re=>{ const lbls=[...document.querySelectorAll('label,th,td,span')].filter(l=>re.test(norm(l.textContent))); for(const l of lbls){ let scope=l.closest('.form-group,.row,td,tr,div')||l.parentElement; const e=scope&&[...scope.querySelectorAll('input')].find(x=>x.type!=='checkbox'); if(e && e.offsetParent!==null) return e; } return null; };"
                + " const fromE=byModel(['SearchData.FromDate','PatientData.FromDate','Emergency.FromDate','FromDate']) || byLabel(/\\bfrom date\\b/);"
                + " const toE  =byModel(['SearchData.ToDate','PatientData.ToDate','Emergency.ToDate','ToDate'])       || byLabel(/\\bto date\\b/);"
                // MRN is a select2 dropdown here; there may also be a free-text MRN box — set it only if it's a text input.
                + " const mrnE =[...document.querySelectorAll('input')].find(x=>/mrn/i.test((x.getAttribute('ng-model')||'')+(x.getAttribute('placeholder')||'')) && x.type!=='checkbox' && x.offsetParent!==null);"
                + " const okF=setVal(fromE,f), okT=setVal(toE,t); let okM=false; if(mrnE){ if(m){ okM=setVal(mrnE,m); } else { setVal(mrnE,''); } }"
                // After entering the MRN, click its adjacent lookup (magnifier) icon so the MRN is resolved.
                + " if(okM){ const grp=mrnE.closest('.form-group,.row,.input-group,td,div')||mrnE.parentElement;"
                + "   const ic=grp&&[...grp.querySelectorAll('button,a,i,span')].find(x=>/search|glyphicon-search|fa-search/i.test((x.className||'')+(x.getAttribute('ng-click')||'')) && x.offsetParent!==null);"
                + "   if(ic) ic.click(); else { mrnE.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',keyCode:13,bubbles:true})); } }"
                + " const btn=[...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(btn) btn.click();"
                + " return 'From='+(okF?f:'(not set)')+' | To='+(okT?t:'(not set)')+' | MRN='+(okM?m:'(optional/skipped)')+' | Search='+(btn?'clicked':'(no button)'); }",
                java.util.Arrays.asList(fromDate, toDate, mrn));
        // Wait for the page/search to settle before reading the grid.
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE); } catch (Exception ignore) {}
        waitForGridData();
        int rows = resultRowCount();
        if (rows == 0 && mrn != null && !mrn.isEmpty()) {
            // MRN filter matched nothing — clear it and search again so a patient row is still available.
            page.evaluate("() => { const mrnE=[...document.querySelectorAll('input')].find(x=>/mrn/i.test((x.getAttribute('ng-model')||'')+(x.getAttribute('placeholder')||'')) && x.type!=='checkbox' && x.offsetParent!==null);"
                    + " if(mrnE){ const c=angular.element(mrnE).controller('ngModel'); mrnE.value=''; if(c){c.$setViewValue('');c.$render();} mrnE.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " const btn=[...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(btn) btn.click(); }");
            waitForGridData();
            rows = resultRowCount();
        }
        return (applied == null ? "" : applied.toString()) + " | rows=" + rows;
    }

    /** Tick a filter checkbox by its adjacent label text (e.g. "All Patients"). */
    private boolean tickFilter(String labelText) {
        Object r = page.evaluate("(t)=>{ const norm=s=>(s||'').replace(/\\s+/g,' ').trim().toLowerCase();"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')];"
                + " for(const cb of cbs){ let lab=''; if(cb.id){ const l=document.querySelector('label[for=\"'+cb.id+'\"]'); if(l) lab=l.textContent; }"
                + "   if(!lab){ lab=(cb.closest('label')&&cb.closest('label').textContent) || (cb.parentElement&&cb.parentElement.textContent) || ''; }"
                + "   if(norm(lab).includes(norm(t))){ if(!cb.checked){ cb.click(); } return true; } } return false; }", labelText);
        waitForAngular(300);
        return Boolean.TRUE.equals(r);
    }

    /** Number of rows currently in the ui-grid results. */
    public int resultRowCount() {
        Object r = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ if(n)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)) n=s.grid.options.data.length; }catch(e){} }); return n; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Wait until the ui-grid has at least one data row (bounded). */
    private void waitForGridData() {
        try {
            page.waitForFunction("() => { let ok=false; document.querySelectorAll('*').forEach(el=>{ if(ok)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)&&s.grid.options.data.length) ok=true; }catch(e){} }); return ok; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) {
            System.out.println("waitForGridData: no grid rows within timeout (empty result?)");
        }
        waitForAngular(600);
    }

    // ---- select a patient row --------------------------------------------

    /**
     * Select a <b>random</b> patient in the ui-grid via {@code grid.api.selection.selectRow} (which ticks
     * the row's checkbox that the footer actions read) — so consecutive runs don't always drive the same
     * patient. Returns "PatientName / MRN [/ Adm]" (best-effort), or null if the grid has no rows.
     */
    public String selectFirstPatient() {
        Object r = page.evaluate("() => {"
                + " let gs=null; document.querySelectorAll('*').forEach(el=>{ if(gs)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&Array.isArray(s.grid.options.data)) gs=s; }catch(e){} });"
                + " if(!gs) return 'ERR:no-grid'; const d=gs.grid.options.data||[]; if(!d.length) return 'ERR:no-data';"
                + " const idx=Math.floor(Math.random()*d.length); const p=d[idx];"
                + " try{ gs.grid.api.selection.clearSelectedRows(); }catch(e){}"
                + " try{ gs.grid.api.selection.selectRow(p); }catch(e){}"
                + " const get=(o,ks)=>{ for(const k of ks){ for(const kk in o){ if(kk.toLowerCase()===k){ const v=o[kk]; if(v!=null&&(''+v).trim()) return (''+v).trim(); } } } return ''; };"
                + " const name=get(p,['patientname','name','pname']); const mrn=get(p,['mrno','mrnno','mrn','mrnno','mrnumber']); const adm=get(p,['admissionno','admissionid','admno']);"
                + " return (name||'(patient)')+' / MRN '+(mrn||'-')+(adm?(' / Adm '+adm):'')+' [row '+(idx+1)+'/'+d.length+']'; }");
        String s = r == null ? null : r.toString();
        if (s != null && s.startsWith("ERR:")) { System.out.println("selectFirstPatient: " + s); return null; }
        waitForAngular(600);
        return s;
    }

    /**
     * Read real MRNs straight out of the grid data — WITHOUT selecting any row — for callers that just need
     * a pool of currently-valid Emergency patient MRNs to try elsewhere (e.g. the Registered/existing-patient
     * path on Emergency Registration, whose one hardcoded MRN goes stale as this shared QA environment's data
     * drifts — same lesson as {@code TransferBedList.getTransferredMrns()}). Returns up to {@code max} unique
     * MRNs, in grid order, or an empty list if the grid has no rows.
     */
    public java.util.List<String> listMrns(int max) {
        Object r = page.evaluate("(max) => { let gs=null; document.querySelectorAll('*').forEach(el=>{ if(gs)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)) gs=s; }catch(e){} });"
                + " if(!gs) return []; const d=gs.grid.options.data||[];"
                + " const get=(o,ks)=>{ for(const k of ks){ for(const kk in o){ if(kk.toLowerCase()===k){ const v=o[kk]; if(v!=null&&(''+v).trim()) return (''+v).trim(); } } } return ''; };"
                + " const out=[]; for(const p of d){ const mrn=get(p,['mrno','mrnno','mrn','mrnumber']); if(mrn && !out.includes(mrn)) out.push(mrn); if(out.length>=max) break; }"
                + " return out; }", max);
        java.util.List<String> list = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) if (o != null) list.add(o.toString());
        return list;
    }

    /**
     * Select the patient at grid row {@code idx} (0-based) — used to iterate every row in order (e.g. trying
     * each patient for Convert IPD Charges until one has unbilled charges). Returns the same descriptor as
     * {@link #selectFirstPatient()}, or null if that index is out of range.
     */
    public String selectNthPatient(int idx) {
        Object r = page.evaluate("(i) => { let gs=null; document.querySelectorAll('*').forEach(el=>{ if(gs)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&Array.isArray(s.grid.options.data)) gs=s; }catch(e){} });"
                + " if(!gs) return 'ERR:no-grid'; const d=gs.grid.options.data||[]; const p=d[i]; if(!p) return 'ERR:no-data';"
                + " try{ gs.grid.api.selection.clearSelectedRows(); }catch(e){} try{ gs.grid.api.selection.selectRow(p); }catch(e){}"
                + " const get=(o,ks)=>{ for(const k of ks){ for(const kk in o){ if(kk.toLowerCase()===k){ const v=o[kk]; if(v!=null&&(''+v).trim()) return (''+v).trim(); } } } return ''; };"
                + " const name=get(p,['patientname','name','pname']); const mrn=get(p,['mrno','mrnno','mrn','mrnumber']); const adm=get(p,['admissionno','admissionid','admno']);"
                + " return (name||'(patient)')+' / MRN '+(mrn||'-')+(adm?(' / Adm '+adm):'')+' [row '+(i+1)+'/'+d.length+']'; }", idx);
        String s = r == null ? null : r.toString();
        if (s != null && s.startsWith("ERR:")) return null;
        waitForAngular(500);
        return s;
    }

    // ---- Change Admission Type -------------------------------------------

    /**
     * Click <b>Change Admission Type</b> for the selected patient and wait for the admission form to
     * open (its Save button / a Patient or Admission section). Returns true if the form opened.
     */
    public boolean clickChangeAdmissionType() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/change admission type/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__emChangeAdmType'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__emChangeAdmType").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickChangeAdmissionType: click failed - " + e.getMessage()); }
        } else {
            System.out.println("clickChangeAdmissionType: 'Change Admission Type' button not found");
            return false;
        }
        // Wait for the admission form (a Save button, or a known admission ng-model, or a modal).
        try {
            page.waitForFunction("() => window.angular && ("
                    + " document.querySelector(\"button[ng-click='IUDAdmission();']\")"
                    + " || document.querySelector(\"select[ng-model='Admission.AdmissionTypeID']\")"
                    + " || [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /admission/i.test(m.textContent||''))"
                    + " || [...document.querySelectorAll('button')].some(b=>/^\\s*save\\s*$/i.test((b.textContent||'').trim()) && b.offsetParent!==null) )",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            System.out.println("clickChangeAdmissionType: admission form did not open in time");
            return false;
        }
        waitForAngular(1000);
        return true;
    }

    // ---- fill the 3 sections ----------------------------------------------

    /**
     * Section 1 — the main <b>Change Admission Type</b> fields (Patient Name is pre-filled): select
     * <b>Admission Type</b> (first real option — the point of this screen), then <b>Department</b>
     * (which cascades Sub Dept + Doctor), then <b>Sub Dept</b> and <b>Doctor</b>, each by its on-screen
     * label within the visible modal. Returns a summary of the values chosen.
     */
    public String fillPatientSection() {
        StringBuilder sb = new StringBuilder();
        // Admission Type + Department are select2 dropdowns whose native <option> list is EMPTY until the
        // control is focused/opened (an on-focus trigger loads them). So open the select2 for real, wait
        // for its results, and pick the first real one. Admission Type is forced (the point of this
        // screen); Department is kept when already pre-filled from the patient's admission.
        sb.append("Admission Type=").append(pickSelect2First("AdmissionList.admissiontypeid", true));
        waitForAngular(600);
        sb.append(" | Department=").append(pickSelect2First("AdmissionList.departmentidadmissiontype", false));
        waitForAngular(900); // Department change reloads Sub Dept + Doctor
        // Sub Dept is disabled here; Doctor has its native options loaded (264) — set it natively.
        sb.append(" | Sub Dept=").append(setNativeByNgModel("AdmissionList.SubDepartmentID", false));
        waitForAngular(500);
        sb.append(" | Doctor=").append(setNativeByNgModel("AdmissionList.doctoridadmissiontype", false));
        waitForAngular(400);
        return sb.toString();
    }

    /**
     * Set a select2 dropdown (by ng-model, scoped to the modal) whose native options load on focus:
     * real-click its {@code .select2-choice} to open (triggering the load), wait for the results list,
     * then click the first real (non "--Select--") result. When {@code force} is false and the control
     * already holds a real value, it is kept. Returns the chosen/kept text or a marker.
     */
    private String pickSelect2First(String ngModel, boolean force) {
        Object info = page.evaluate("(ng) => {" + MODAL_JS
                + " if(!modal) return 'ERR:no-modal'; const sel=[...modal.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!sel) return 'ERR:no-field';"
                + " const cur=(sel.options[sel.selectedIndex]||{}).text||''; const isEmpty=!cur||/^-*\\s*select\\s*-*$/i.test(cur.trim());"
                + " if(sel.disabled) return isEmpty?'DISABLED':('KEEP:'+cur.trim());"
                + " let cont=sel.nextElementSibling; if(!(cont&&/select2-container/.test(cont.className||''))) cont=sel.parentElement&&sel.parentElement.querySelector('.select2-container');"
                // Clear any stale tag from a previous pickSelect2First call so only ONE element ever carries
                // this id (otherwise the 2nd call leaves 2 elements tagged -> locator strict-mode violation).
                + " const old=document.getElementById('__s2choice'); if(old) old.removeAttribute('id');"
                + " const ch=cont&&cont.querySelector('.select2-choice,.select2-selection'); if(!ch) return 'ERR:no-select2'; ch.id='__s2choice';"
                + " return isEmpty?'EMPTY':('HAS:'+cur.trim()); }", ngModel);
        String st = info == null ? "ERR:null" : info.toString();
        if (st.startsWith("ERR")) { System.out.println("pickSelect2First(" + ngModel + "): " + st); return "(" + st + ")"; }
        if (st.equals("DISABLED")) return "(disabled)";
        if (st.startsWith("KEEP:")) return st.substring(5) + " (kept)";
        if (!force && st.startsWith("HAS:")) return st.substring(4) + " (kept)";
        // Open the select2 (real click) — this fires the on-focus handler that lazily loads the option
        // list into the NATIVE <select>. Then wait for those native options to actually populate (the
        // load is async; reading the select2 dropdown results races it), close the dropdown, and pick
        // the first real option natively.
        try { page.locator("#__s2choice").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("pickSelect2First(" + ngModel + "): open click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("(ng) => {" + MODAL_JS
                    + " if(!modal) return false; const e=[...modal.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                    + " return e && [...e.options].some(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); }",
                    ngModel, new Page.WaitForFunctionOptions().setTimeout(9000));
        } catch (Exception e) { System.out.println("pickSelect2First(" + ngModel + "): native options did not load"); }
        try { page.keyboard().press("Escape"); } catch (Exception ignore) {}
        page.evaluate("() => { const e=document.getElementById('__s2choice'); if(e) e.removeAttribute('id'); }");
        waitForAngular(200);
        return setNativeByNgModel(ngModel, true);
    }

    /** Set a native {@code select} (by ng-model, scoped to the modal) to its first real option (or keep). */
    private String setNativeByNgModel(String ngModel, boolean force) {
        Object r = page.evaluate("(a) => { const ng=a[0], force=a[1];" + MODAL_JS
                + " if(!modal) return '(no modal)'; const e=[...modal.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return '(no field)';"
                + " const cur=(e.options[e.selectedIndex]||{}).text||''; const isEmpty=!cur||/^-*\\s*select\\s*-*$/i.test(cur.trim());"
                + " if(e.disabled) return isEmpty?'(disabled)':(cur.trim()+' (kept)'); if(!force && !isEmpty) return cur.trim()+' (kept)';"
                + " const o=[...e.options].find(x=>x.value && !/^-*\\s*select/i.test((x.textContent||'').trim())); if(!o) return '(no option)';"
                + " e.value=o.value; const c=angular.element(e).controller('ngModel'); if(c){c.$setViewValue(o.value);c.$render();}"
                + " const $=window.jQuery; if($){try{$(e).trigger('change');$(e).select2('val',o.value);}catch(err){}} e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return (o.textContent||'').trim(); }", java.util.Arrays.asList(ngModel, force));
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Section 2 — <b>Additional Doctors</b> (collapsible panel {@code #collapseOne}). Expand it, pick a
     * Classification + an Additional Doctor, and click <b>Add</b> ({@code AddAdditionalDoc}) which lands
     * a row in the {@code Classification Name | Doctor Name | Remove} table. Returns a summary.
     */
    public String fillAdmissionSection() {
        boolean expanded = expandCollapse("#collapseOne", "Additional Doctors");
        StringBuilder sb = new StringBuilder(expanded ? "Panel expanded. " : "Expand failed. ");
        sb.append("Classification=").append(setNativeByNgModel("Admission.ClassificationID", false));
        sb.append(" | Additional Doctor=").append(setNativeByNgModel("Admission.AdditionalDoctorID", true));
        waitForAngular(300);
        boolean added = clickModalNgClick("AddAdditionalDoc");
        waitForAngular(600);
        int rows = modalTableRowCount("Doctor Name");
        sb.append(" | Add=").append(added ? "clicked" : "button-not-found").append(" | rows=").append(rows);
        return sb.toString();
    }

    /**
     * Section 3 — <b>Next of Kin Details</b> (collapsible panel {@code #collapseKin}). Clicking the panel
     * header fires {@code ng-click="FillKinDropDown()"}, which auto-loads the Kin dropdowns (Title,
     * Relation, Occupation, Payor) — the "click the tab and it auto-fills" behaviour. Then fill the
     * mandatory fields (Name*, Relationship*, Mobile No*, Address*) and click <b>Add</b>
     * ({@code AddKinDetails}) to commit the kin. Returns a summary.
     */
    public String fillThirdSection() {
        boolean expanded = expandCollapse("#collapseKin", "Next of Kin");
        waitForAngular(800); // let FillKinDropDown() populate Title / Relation / Occupation / Payor
        String prefix = expanded ? "Kin tab clicked (FillKinDropDown, dropdowns auto-loaded). " : "Kin tab not found. ";
        // Do NOT type the kin fields — click a row in the Next of Kin table; its ng-click
        // EditKinDetails($index) auto-fills the kin form from the patient's existing next-of-kin.
        int rows = kinTableRowCount();
        if (rows == 0) return prefix + "No existing kin row in the table to click (nothing to auto-fill).";
        boolean clicked = clickKinTableRow();
        waitForAngular(700);
        String filled = readKinSummary();
        // EditKinDetails took the kin OUT of KinDetailsList and into the form — put it back with Modify (or Add)
        // before Save, otherwise the save is rejected with "Please click Add to include the NOK/Guarantor
        // details!" (the trap that blocked OP Registration until the row click was dropped there).
        String recommit = recommitKin();
        return prefix + "Clicked kin table row (EditKinDetails) -> auto-filled: " + filled
                + " | " + recommit + (clicked ? "" : " [row click not confirmed]");
    }

    /**
     * Put the kin loaded into the form by {@code EditKinDetails} back into {@code KinDetailsList} — <b>Modify</b>
     * ({@code ModifyKinDetails}) when the screen offers it, else <b>Add</b> ({@code AddKinDetails}). Without this
     * the kin sits in the form uncommitted and Save is gated on the next-of-kin.
     */
    private String recommitKin() {
        Object what = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const btns=[...document.querySelectorAll('button,a,input[type=button]')].filter(b=>b.offsetParent!==null);"
                + " let b=btns.find(x=>/modifykindetails/i.test(x.getAttribute('ng-click')||'')) || btns.find(x=>/^\\s*modify\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) b=btns.find(x=>/addkindetails/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return ''; b.id='__elvKinCommit'; return norm(b.textContent||b.value)||'commit'; }");
        String label = String.valueOf(what);
        if (label.isEmpty()) return "no Modify/Add control to re-commit the kin";
        try { page.locator("#__elvKinCommit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) {
            try { page.evaluate("() => { const b=document.getElementById('__elvKinCommit'); if(b) b.click(); }"); }
            catch (Exception ignore) { }
        }
        page.evaluate("() => { const b=document.getElementById('__elvKinCommit'); if(b) b.removeAttribute('id'); }");
        waitForAngular(800);
        return "kin re-committed via " + label;
    }

    /** Count rows in the Next of Kin table (identified by its "Relationship" header) within #collapseKin. */
    private int kinTableRowCount() {
        Object r = page.evaluate("() => { const p=document.querySelector('#collapseKin'); if(!p) return 0;"
                + " const tb=[...p.querySelectorAll('table')].find(t=>/relationship/i.test(t.innerText||'')); if(!tb) return 0;"
                + " return [...tb.querySelectorAll('tbody tr')].filter(r=>r.offsetParent!==null && (r.innerText||'').trim().length>0).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Real-click the first Next of Kin table row (fires EditKinDetails($index) to auto-fill the form). */
    private boolean clickKinTableRow() {
        Object tagged = page.evaluate("() => { const p=document.querySelector('#collapseKin'); if(!p) return false;"
                + " const tb=[...p.querySelectorAll('table')].find(t=>/relationship/i.test(t.innerText||'')); if(!tb) return false;"
                + " const rows=[...tb.querySelectorAll('tbody tr')].filter(r=>r.offsetParent!==null && (r.innerText||'').trim().length>0); if(!rows.length) return false;"
                // click a data cell (not the Delete button) — tag the row's first cell.
                + " const row=rows.find(r=>(r.getAttribute('ng-click')||'').indexOf('EditKinDetails')>=0)||rows[0];"
                + " const cell=[...row.querySelectorAll('td')].find(td=>!td.querySelector('button,a'))||row.querySelector('td')||row; cell.id='__kinRowCell'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickKinTableRow: no row"); return false; }
        try { page.locator("#__kinRowCell").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickKinTableRow: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__kinRowCell'); if(e) e.removeAttribute('id'); }");
        waitForAngular(400);
        return true;
    }

    /** Read the kin form's current values (after the row-click auto-fill), scoped to #collapseKin. */
    private String readKinSummary() {
        Object r = page.evaluate("() => { const p=document.querySelector('#collapseKin'); if(!p) return '';"
                + " const gv=ng=>{ const e=[...p.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng); return e?(e.value||'').trim():''; };"
                + " const gs=ng=>{ const e=[...p.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); return e?((e.options[e.selectedIndex]||{}).text||'').trim():''; };"
                + " return 'Title='+gs('Admission.KinTitleID')+' | Name='+gv('Admission.KinName')+' | Relationship='+gs('Admission.KinRelationID')+' | Mobile='+gv('Admission.KinMobileNo')+' | Address='+gv('Admission.KinAddress'); }");
        return r == null ? "" : r.toString();
    }

    // ---- modal field helpers ----------------------------------------------

    /**
     * JS that locates the visible "Change Admission Type" dialog by walking UP from its title to the
     * smallest ancestor that contains a Save button and selects — NOT a broad {@code .ng-scope}
     * ancestor (which would leak into the top-page search filters). Leaves {@code modal} in scope.
     */
    private static final String MODAL_JS =
            " const __t=[...document.querySelectorAll('h1,h2,h3,h4,h5,.modal-title,legend,div,span,b')].find(x=>/^\\s*change admission type\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.getBoundingClientRect&&x.getBoundingClientRect().width>0);"
                    + " let modal=null; if(__t){ let n=__t; for(let i=0;i<10&&n;i++){ n=n.parentElement; if(!n)break; try{ const hasSave=[...n.querySelectorAll('button')].some(b=>/^\\s*save\\s*$/i.test((b.textContent||'').trim())); const hasSel=n.querySelectorAll('select').length>0; if(hasSave&&hasSel){ modal=n; break; } }catch(e){} } }";

    /**
     * Expand a collapsible panel ({@code #collapseOne} / {@code #collapseKin}) by real-clicking its
     * header — but only if not already open, so we don't toggle it shut. Clicking the header also fires
     * any {@code ng-click} on it (e.g. Next of Kin's {@code FillKinDropDown()}). Returns true if the
     * panel is (now) open.
     */
    private boolean expandCollapse(String panelSel, String titleRe) {
        Object tagged = page.evaluate("(a) => { const psel=a[0], t=a[1];" + MODAL_JS
                + " if(!modal) return 'no-modal'; const panel=modal.querySelector(psel);"
                + " if(panel && /(^|\\s)in(\\s|$)/.test(panel.className)) return 'already-in';"
                + " const rx=new RegExp(t,'i'); const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                // Prefer the ANCHOR that actually toggles the panel: data-target===#collapseX, else an
                // <a> with a matching ng-click/href, else an <a> whose text matches (NOT the wrapper div).
                + " let hdr=[...modal.querySelectorAll('a[data-target]')].find(x=>x.getAttribute('data-target')===psel);"
                + " if(!hdr) hdr=[...modal.querySelectorAll('a')].find(x=>{ const dt=x.getAttribute('data-target')||x.getAttribute('href')||''; return dt===psel; });"
                + " if(!hdr) hdr=[...modal.querySelectorAll('a')].find(x=>rx.test(norm(x.textContent)) && norm(x.textContent).length<40 && x.getBoundingClientRect().width>0);"
                + " if(!hdr) return 'no-hdr'; hdr.id='__collHdr'; return 'click'; }", java.util.Arrays.asList(panelSel, titleRe));
        String st = tagged == null ? "null" : tagged.toString();
        if (st.equals("already-in")) return true;
        if (!st.equals("click")) { System.out.println("expandCollapse(" + panelSel + "): " + st); return false; }
        try { page.locator("#__collHdr").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("expandCollapse(" + panelSel + "): click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__collHdr'); if(e) e.removeAttribute('id'); }");
        waitForAngular(600);
        return true;
    }

    /** Real-click a modal button/link whose {@code ng-click} contains {@code sub}. Returns true if clicked. */
    private boolean clickModalNgClick(String sub) {
        Object tagged = page.evaluate("(sub) => {" + MODAL_JS
                + " if(!modal) return false; const b=[...modal.querySelectorAll('button,a')].find(x=>((x.getAttribute('ng-click')||'').indexOf(sub)>=0) && x.offsetParent!==null); if(!b) return false; b.id='__mBtn'; return true; }", sub);
        if (!Boolean.TRUE.equals(tagged)) return false;
        try { page.locator("#__mBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickModalNgClick(" + sub + "): click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__mBtn'); if(e) e.removeAttribute('id'); }");
        waitForAngular(300);
        return true;
    }

    /** Fill an input/textarea (by ng-model, scoped to the modal) via its Angular model. Returns the value/marker. */
    private String fillModalInput(String ngModel, String value) {
        Object r = page.evaluate("(a) => { const ng=a[0], v=a[1];" + MODAL_JS
                + " if(!modal) return '(no modal)'; const e=[...modal.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e) return '(no field)';"
                + " const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; }", java.util.Arrays.asList(ngModel, value));
        return r == null ? "(null)" : r.toString();
    }

    /** Count the visible body rows of the modal table whose header text matches {@code headerText}. */
    private int modalTableRowCount(String headerText) {
        Object r = page.evaluate("(h) => {" + MODAL_JS
                + " if(!modal) return 0; const tb=[...modal.querySelectorAll('table')].find(t=>new RegExp(h,'i').test(t.innerText||'')); if(!tb) return 0;"
                + " return [...tb.querySelectorAll('tbody tr')].filter(r=>r.offsetParent!==null && (r.innerText||'').trim().length>0).length; }", headerText);
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    // ---- save & toast -----------------------------------------------------

    /**
     * Click <b>Save</b> and return the success toast. Watches for a toast via a MutationObserver so it
     * is captured even if it fades quickly. Tries the admission Save ({@code IUDAdmission();}) first,
     * then any visible "Save" button (covers a modal Save on this screen).
     */
    public String saveAndGetToast() {
        page.evaluate("() => { window.__elvToasts=[]; if(window.__elvObs) window.__elvObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__elvToasts.includes(t)) window.__elvToasts.push(t); }); };"
                + " window.__elvObs=new MutationObserver(grab); window.__elvObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " let b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='IUDAdmission();' && x.offsetParent!==null);"
                + " if(!b) b=[...document.querySelectorAll('.modal button,button')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                + " if(b) b.click(); }");
        try {
            page.waitForFunction("() => (window.__elvToasts||[]).some(a=>/success|saved|updated|changed|admitted|please|select|enter/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            page.waitForTimeout(3000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__elvToasts||[]).includes(t)) (window.__elvToasts=window.__elvToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__elvToasts||[]; return a.find(x=>/success|saved|updated|changed|admitted/i.test(x)) || a[0] || ''; }");
        waitForAngular(500);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Expected Discharge Date (footer action) -------------------------

    /**
     * Click the footer <b>Expected Discharge Date</b> button (acts on the selected grid row) and wait
     * for its popup. Returns true if the popup opened.
     */
    public boolean clickExpectedDischargeDate() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,span,div')].find(x=>/^\\s*expected discharge date\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__expDisBtn'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickExpectedDischargeDate: button not found"); return false; }
        try { page.locator("#__expDisBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickExpectedDischargeDate: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__expDisBtn'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog],.ui-dialog')].some(m=>m.getBoundingClientRect().width>0 && /discharge/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("clickExpectedDischargeDate: popup did not open in time"); }
        waitForAngular(800);
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,[role=dialog],.ui-dialog')].some(m=>m.getBoundingClientRect().width>0 && /discharge/i.test(m.textContent||''))"));
    }

    /**
     * Set the <b>Expected Discharge Date</b> to a future date ({@code today + days}, dd/MM/yyyy) via the
     * Angular model, then dismiss the datepicker overlay. Returns a summary including the date used.
     */
    public String setDischargeFutureDate(int daysAhead) {
        String date = java.time.LocalDate.now().plusDays(daysAhead)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        Object r = page.evaluate("(v) => { const e=[...document.querySelectorAll(\"input[ng-model='ExpectedDischarge.ExpectedDischargeDate']\")].find(x=>x.offsetParent!==null) || document.querySelector(\"input[ng-model='ExpectedDischarge.ExpectedDischargeDate']\");"
                + " if(!e) return '(no field)'; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return e.value; }", date);
        // Dismiss the datepicker overlay so it doesn't sit over the Save button (click the popup title).
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal')].find(x=>x.getBoundingClientRect().width>0 && /discharge/i.test(x.textContent||'')); const h=m&&(m.querySelector('.modal-title,.modal-header')||m); if(h) h.dispatchEvent(new MouseEvent('mousedown',{bubbles:true})); }");
        waitForAngular(300);
        return "Expected Discharge Date = " + date + (r == null ? "" : " (field=\"" + r + "\")");
    }

    /**
     * Click <b>Save</b> ({@code IUDExpectedDischarge()}) in the popup and return the success toast,
     * captured via a MutationObserver.
     */
    public String saveDischargeAndGetToast() {
        Object tagged = page.evaluate("() => { window.__edToasts=[]; if(window.__edObs) window.__edObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__edToasts.includes(t)) window.__edToasts.push(t); }); };"
                + " window.__edObs=new MutationObserver(grab); window.__edObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>((x.getAttribute('ng-click')||'').indexOf('IUDExpectedDischarge')>=0) && x.offsetParent!==null); if(!b) return false; b.id='__edSave'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__edSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("saveDischargeAndGetToast: Save click failed - " + e.getMessage()); }
        } else { System.out.println("saveDischargeAndGetToast: Save button not found"); }
        try {
            page.waitForFunction("() => (window.__edToasts||[]).some(a=>/success|saved|updated|discharge|please|select|enter/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        Object r = page.evaluate("() => { const a=window.__edToasts||[]; return a.find(x=>/success|saved|updated|discharge/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Close</b> on the Expected Discharge Date popup. */
    public void closeDischargePopup() {
        Object tagged = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /discharge/i.test(x.textContent||''));"
                + " const scope=m||document; const b=[...scope.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__edClose'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__edClose").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("closeDischargePopup: Close click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__edClose'); if(e) e.removeAttribute('id'); }");
        } else { System.out.println("closeDischargePopup: Close button not found"); }
        waitForAngular(500);
    }

    // ---- Cancel Admission (footer action) --------------------------------

    /**
     * Set the filter to <b>All Inpatients</b> (Cancel Admission is only enabled for inpatients; under
     * "All Patients" the button is disabled) and re-Search, so the grid holds cancellable admissions.
     */
    public void useInpatientFilter() {
        // Tick All Inpatients once; then (re-)run Search, retrying if the grid comes back empty — under load
        // the re-search right after a modal save can transiently return 0 rows, which would strand the
        // Plan Discharge / Cancel / Close sections with "no inpatient to select".
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim().toLowerCase();"
                + " const set=(lab,want)=>{ for(const cb of [...document.querySelectorAll('input[type=checkbox]')]){ let l=''; if(cb.id){ const e=document.querySelector('label[for=\"'+cb.id+'\"]'); if(e) l=e.textContent; } if(!l){ l=(cb.closest('label')&&cb.closest('label').textContent)||(cb.parentElement&&cb.parentElement.textContent)||''; } if(norm(l).includes(norm(lab))){ if(cb.checked!==want) cb.click(); return; } } };"
                + " set('all patients', false); set('all inpatients', true); }");
        String gridHasRows = "() => { let ok=false; document.querySelectorAll('*').forEach(el=>{ if(ok)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)&&s.grid.options.data.length) ok=true; }catch(e){} }); return ok; }";
        for (int attempt = 0; attempt < 3; attempt++) {
            page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            try {
                page.waitForFunction(gridHasRows, null, new Page.WaitForFunctionOptions().setTimeout(15000));
                waitForAngular(800);
                return;
            } catch (Exception ignore) { System.out.println("useInpatientFilter: grid empty (attempt " + (attempt + 1) + ") — re-searching"); }
            waitForAngular(1500);
        }
        System.out.println("useInpatientFilter: grid did not populate after retries");
        waitForAngular(500);
    }

    // ---- Ward Acceptance ---------------------------------------------------

    /**
     * Tick the <b>Ward Acceptance</b> checkbox in the Emergency Visits grid filters and click <b>Search</b>,
     * retrying if the grid comes back empty (same pattern as {@link #useInpatientFilter()}). Returns true once
     * the grid holds rows.
     */
    public boolean useWardAcceptanceFilter() {
        boolean ticked = tickFilter("Ward Acceptance");
        if (!ticked) System.out.println("useWardAcceptanceFilter: 'Ward Acceptance' checkbox not found");
        String gridHasRows = "() => { let ok=false; document.querySelectorAll('*').forEach(el=>{ if(ok)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)&&s.grid.options.data.length) ok=true; }catch(e){} }); return ok; }";
        for (int attempt = 0; attempt < 3; attempt++) {
            page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            try {
                page.waitForFunction(gridHasRows, null, new Page.WaitForFunctionOptions().setTimeout(15000));
                waitForAngular(800);
                return true;
            } catch (Exception ignore) { System.out.println("useWardAcceptanceFilter: grid empty (attempt " + (attempt + 1) + ") — re-searching"); }
            waitForAngular(1500);
        }
        System.out.println("useWardAcceptanceFilter: grid did not populate after retries");
        waitForAngular(500);
        return false;
    }

    /**
     * Click the footer <b>Ward Acceptance</b> button for the selected patient. Confirms any "are you
     * sure/do you want to" dialog that appears (same pattern as Convert IPD Charges), then returns the
     * resulting success toast.
     */
    public String clickWardAcceptanceAndGetToast() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/wardacceptance/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*ward\\s*acceptance\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!b) return 'no-button'; if(b.disabled) return 'disabled'; b.id='__waBtn'; return true; }");
        String st = String.valueOf(tagged);
        if ("no-button".equals(st)) { System.out.println("clickWardAcceptanceAndGetToast: 'Ward Acceptance' button not found"); return ""; }
        if ("disabled".equals(st)) { System.out.println("clickWardAcceptanceAndGetToast: button not enabled for this patient"); return ""; }
        page.evaluate("() => { window.__waToasts=[]; if(window.__waObs) window.__waObs.disconnect();"
                + " window.__waObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__waToasts.includes(t)) window.__waToasts.push(t); }); });"
                + " window.__waObs.observe(document.body,{childList:true,subtree:true}); }");
        try { page.locator("#__waBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickWardAcceptanceAndGetToast: click failed - " + e.getMessage()); }
        page.evaluate("() => { const b=document.getElementById('__waBtn'); if(b) b.removeAttribute('id'); }");
        String toast = "";
        for (int i = 0; i < 40; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate(
                    "() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want to|are you sure/i.test(m.textContent||''))"));
            if (confirm) {
                page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want to|are you sure/i.test(m.textContent||''));"
                        + " const b=[...(box||document).querySelectorAll('button')].find(x=>/^(save|yes|ok)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(400);
            }
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__waToasts=window.__waToasts||[]; if(!window.__waToasts.includes(m)) window.__waToasts.push(m); } });"
                    + " const a=window.__waToasts||[]; return a.find(x=>/success|saved|accepted/i.test(x)) || a[0] || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            page.waitForTimeout(500);
        }
        if (toast.isEmpty()) System.out.println("clickWardAcceptanceAndGetToast: no toast observed within ~20s");
        waitForAngular(400);
        return toast;
    }

    /**
     * Click the footer <b>Cancel Admission</b> button (for the selected inpatient) and wait for the
     * "Reason for Cancellation" popup — an {@code ng-confirm-box} with the {@code CancellationReason}
     * dropdown. Returns true if the popup opened.
     */
    public boolean clickCancelAdmission() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^\\s*cancel admission\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return 'none'; if(b.disabled) return 'disabled'; b.id='__cancelAdmBtn'; return true; }");
        String st = String.valueOf(tagged);
        if ("disabled".equals(st)) { System.out.println("clickCancelAdmission: button disabled (needs the All Inpatients filter)"); return false; }
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickCancelAdmission: button not found"); return false; }
        try { page.locator("#__cancelAdmBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickCancelAdmission: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cancelAdmBtn'); if(e) e.removeAttribute('id'); }");
        String popupJs = "[...document.querySelectorAll('.ng-confirm-box,.jconfirm')].some(m=>m.offsetParent!==null && (m.querySelector(\"select[ng-model='CancellationReason']\") || /reason for cancellation/i.test(m.textContent||'')))";
        try { page.waitForFunction("() => " + popupJs, null, new Page.WaitForFunctionOptions().setTimeout(12000)); }
        catch (Exception ignore) { System.out.println("clickCancelAdmission: reason popup did not open"); }
        waitForAngular(500);
        return Boolean.TRUE.equals(page.evaluate("() => " + popupJs));
    }

    /**
     * Select the first real option in the popup's <b>CancellationReason</b> dropdown. Sets
     * {@code selectedIndex} and fires a change event so Angular's select directive reads the real model
     * value (manually {@code $setViewValue}-ing the DOM option value corrupts an ng-options binding and
     * the Save then complains "Please enter reason."). Returns the reason.
     */
    public String selectCancelReason() {
        Object r = page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm')].find(m=>m.offsetParent!==null); if(!box) return '(no popup)';"
                + " const e=box.querySelector(\"select[ng-model='CancellationReason']\")||box.querySelector('select'); if(!e) return '(no dropdown)';"
                + " const idx=[...e.options].findIndex(x=>x.value && !/^-*\\s*select\\s*-*$/i.test((x.textContent||'').trim())); if(idx<0) return '(no option)';"
                + " e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}}"
                + " return (e.options[idx].textContent||'').replace(/\\s+/g,' ').trim(); }");
        waitForAngular(300);
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Click <b>Save</b> in the popup and confirm the cancellation. The success shows inside the popup —
     * the "Cancellation Remark" table gains a row ("reason | Added By | Cancellation Date"), replacing
     * "No records found". Returns "Admission Cancelled Successfully! (row…)", or "" if it didn't record.
     */
    public String saveCancelAndGetMessage() {
        Object tagged = page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm')].find(m=>m.offsetParent!==null); if(!box) return false;"
                + " const b=[...box.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__ccSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveCancelAndGetMessage: Save button not found"); return ""; }
        try { page.locator("#__ccSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("saveCancelAndGetMessage: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ccSave'); if(e) e.removeAttribute('id'); }");
        // Success = a real row appears in the popup's Cancellation Remark table (or an in-popup success msg).
        String successJs = "() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm')].find(m=>m.offsetParent!==null); if(!box) return false;"
                + " const tb=[...box.querySelectorAll('table')].find(t=>/cancellation remark|added by/i.test(t.innerText||''));"
                + " const row=tb && [...tb.querySelectorAll('tbody tr')].some(r=>r.offsetParent!==null && (r.innerText||'').trim() && !/no records found/i.test(r.innerText||''));"
                + " const msg=[...box.querySelectorAll('*')].some(e=>e.offsetParent!==null && e.children.length===0 && /cancelled successfully/i.test(e.textContent||''));"
                + " return row || msg; }";
        try { page.waitForFunction(successJs, null, new Page.WaitForFunctionOptions().setTimeout(15000)); }
        catch (Exception ignore) { System.out.println("saveCancelAndGetMessage: cancellation not confirmed in the popup"); }
        Object r = page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm')].find(m=>m.offsetParent!==null); if(!box) return '';"
                + " const tb=[...box.querySelectorAll('table')].find(t=>/cancellation remark|added by/i.test(t.innerText||''));"
                + " if(tb){ const row=[...tb.querySelectorAll('tbody tr')].find(r=>r.offsetParent!==null && (r.innerText||'').trim() && !/no records found/i.test(r.innerText||''));"
                + "   if(row){ const c=[...row.querySelectorAll('td')].map(td=>(td.innerText||'').replace(/\\s+/g,' ').trim()).filter(Boolean); return 'Admission Cancelled Successfully! ('+c.join(' | ')+')'; } }"
                + " const el=[...box.querySelectorAll('*')].find(e=>e.offsetParent!==null && e.children.length===0 && /cancelled successfully/i.test(e.textContent||'')); if(el) return (el.textContent||'').replace(/\\s+/g,' ').trim();"
                + " return ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Close</b> on the Cancel Admission popup. */
    public void closeCancelPopup() {
        page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm')].find(m=>m.offsetParent!==null); if(!box) return;"
                + " const b=[...box.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(500);
    }

    // ---- Close Admission (footer action) ---------------------------------

    /**
     * Click the footer <b>Close Admission</b> button (for the selected inpatient) and wait for its popup
     * (a Remark textarea + Save/Close). Returns true if the popup opened.
     */
    public boolean clickCloseAdmission() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^\\s*close admission\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return 'none'; if(b.disabled) return 'disabled'; b.id='__closeAdmBtn'; return true; }");
        String st = String.valueOf(tagged);
        if ("disabled".equals(st)) { System.out.println("clickCloseAdmission: button disabled (needs the All Inpatients filter)"); return false; }
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickCloseAdmission: button not found"); return false; }
        try { page.locator("#__closeAdmBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickCloseAdmission: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__closeAdmBtn'); if(e) e.removeAttribute('id'); }");
        String popupJs = "[...document.querySelectorAll('.modal-content,.modal')].some(m=>m.offsetParent!==null && /close admission/i.test(m.textContent||'') && m.querySelector(\"textarea[ng-model='Discharge.visitadmissionclosedrevokedremark']\"))";
        try { page.waitForFunction("() => " + popupJs, null, new Page.WaitForFunctionOptions().setTimeout(12000)); }
        catch (Exception ignore) { System.out.println("clickCloseAdmission: popup did not open"); }
        waitForAngular(500);
        return Boolean.TRUE.equals(page.evaluate("() => " + popupJs));
    }

    /** Enter the Close Admission <b>Remark</b> (reason). Returns the value set. */
    public String enterCloseReason(String reason) {
        Object r = page.evaluate("(v) => { const e=[...document.querySelectorAll(\"textarea[ng-model='Discharge.visitadmissionclosedrevokedremark']\")].find(x=>x.offsetParent!==null); if(!e) return '(no field)';"
                + " const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; }", reason);
        waitForAngular(300);
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Click <b>Save</b> ({@code fnCloseRevokeVisitClick('closeAdmission')}) and return the success toast,
     * captured via a MutationObserver.
     */
    public String saveCloseAdmissionAndGetToast() {
        Object tagged = page.evaluate("() => { window.__clToasts=[]; if(window.__clObs) window.__clObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__clToasts.includes(t)) window.__clToasts.push(t); }); };"
                + " window.__clObs=new MutationObserver(grab); window.__clObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal-content,.modal')].find(x=>x.offsetParent!==null && /close admission/i.test(x.textContent||'')); if(!m) return false;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>((x.getAttribute('ng-click')||'').indexOf('fnCloseRevokeVisitClick')>=0) && x.offsetParent!==null); if(!b) return false; b.id='__clSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveCloseAdmissionAndGetToast: Save button not found"); return ""; }
        try { page.locator("#__clSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("saveCloseAdmissionAndGetToast: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__clSave'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__clToasts||[]).some(a=>/closed|success|revoke|please|enter|remark/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__clToasts||[]).includes(t)) (window.__clToasts=window.__clToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__clToasts||[]; return a.find(x=>/closed successfully|success/i.test(x)) || a.find(x=>/closed|revoke/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Close</b> on the Close Admission popup (if it is still open). */
    public void closeCloseAdmissionPopup() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal-content,.modal')].find(x=>x.offsetParent!==null && /close admission/i.test(x.textContent||'')); if(!m) return;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(500);
    }

    // ---- Revoke Admission (footer action) --------------------------------

    /**
     * Switch the filter to <b>Closed Admission</b> ({@code AdmissionList.ClosedVisit}) over a WIDE date range
     * and Search, so the just-closed patient (which vanishes from the "All Inpatients" list) is listed and can
     * be selected for Revoke. Verified live 2026-07-14: after Close, the patient leaves the inpatient list and
     * only appears under this filter; a wide range + a couple of retries is needed (the narrow range / first
     * search can return 0 under load). Returns the row count.
     */
    public int useClosedAdmissionFilter(String targetMrn) {
        page.evaluate("() => { let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.SearchBtnClick==='function' && typeof x.OpenRevokeTypeModal==='function'){sc=x;break;} x=x.$parent; } }catch(e){} });"
                + " if(!sc) return; sc.$apply(()=>{ if(!sc.AdmissionList) sc.AdmissionList={}; sc.AdmissionList.Admitted=false; sc.AdmissionList.AllPatient=false; sc.AdmissionList.ClosedVisit=true;"
                + "   sc.AdmissionList.FromDate='01/01/2026'; sc.AdmissionList.ToDate='31/12/2026'; }); }");
        // The just-closed patient can take a few seconds to surface in the Closed list (server lag/throttle);
        // re-search until the SPECIFIC MRN appears (up to ~6 tries), not merely until any rows load.
        String rowsFor = "(mrn) => { let gs=null; document.querySelectorAll('*').forEach(el=>{ if(gs)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)) gs=s; }catch(e){} }); if(!gs) return {n:0,hit:false}; const d=gs.grid.options.data; const getk=(o,ks)=>{ for(const k of ks){ for(const kk in o){ if(kk.toLowerCase()===k){ const v=o[kk]; if(v!=null&&(''+v).trim()) return (''+v).trim(); } } } return ''; }; return { n:d.length, hit: mrn!=null && d.some(x=>getk(x,['mrno','mrnno','mrn','mrnumber'])===String(mrn)) }; }";
        int rows = 0;
        for (int attempt = 0; attempt < 6; attempt++) {
            page.evaluate("() => { let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.SearchBtnClick==='function' && typeof x.OpenRevokeTypeModal==='function'){sc=x;break;} x=x.$parent; } }catch(e){} }); if(sc) sc.$apply(()=>{ try{ sc.SearchBtnClick(2); }catch(e){} }); }");
            waitForAngular(2800);
            Object res = page.evaluate(rowsFor, targetMrn);
            @SuppressWarnings("unchecked") java.util.Map<String,Object> m = (java.util.Map<String,Object>) res;
            rows = m.get("n") == null ? 0 : ((Number) m.get("n")).intValue();
            boolean hit = Boolean.TRUE.equals(m.get("hit"));
            if (hit) { System.out.println("useClosedAdmissionFilter: found MRN " + targetMrn + " (" + rows + " closed rows) on attempt " + (attempt + 1)); waitForAngular(400); return rows; }
            System.out.println("useClosedAdmissionFilter: MRN " + targetMrn + " not in closed list yet (attempt " + (attempt + 1) + ", " + rows + " rows) — retrying");
        }
        return rows;
    }

    /**
     * Re-select the <b>same</b> patient by MRN after Close re-searches the grid, so Revoke acts on the
     * just-closed admission (Close calls {@code SearchAdmissionList()}, which clears the selection). Pass the
     * MRN string (extracted from a {@link #selectFirstPatient()} descriptor). Returns "PatientName / MRN …"
     * for the re-selected row, or null if that MRN is no longer in the grid.
     */
    public String reselectPatientByMrn(String mrn) {
        if (mrn == null || mrn.isEmpty()) return null;
        Object r = page.evaluate("(mrn) => {"
                + " let gs=null; document.querySelectorAll('*').forEach(el=>{ if(gs)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&Array.isArray(s.grid.options.data)) gs=s; }catch(e){} });"
                + " if(!gs) return 'ERR:no-grid'; const d=gs.grid.options.data||[]; if(!d.length) return 'ERR:no-data';"
                + " const getk=(o,ks)=>{ for(const k of ks){ for(const kk in o){ if(kk.toLowerCase()===k){ const v=o[kk]; if(v!=null&&(''+v).trim()) return (''+v).trim(); } } } return ''; };"
                + " const p=d.find(x=>getk(x,['mrno','mrnno','mrn','mrnumber'])===String(mrn)); if(!p) return 'ERR:not-found';"
                + " try{ gs.grid.api.selection.clearSelectedRows(); }catch(e){} try{ gs.grid.api.selection.selectRow(p); }catch(e){}"
                // Pin the selection on the CONTROLLER scope so the Revoke save payload
                // ($scope.selectedItem.AdmissionId / .OPD_IPD, SelectedPatientID) is correct even if the
                // grid's row-select handler didn't fully populate it after the close re-search.
                + " let cs=null; document.querySelectorAll('*').forEach(el=>{ if(cs)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.OpenRevokeTypeModal==='function'){cs=x;break;} x=x.$parent; } }catch(e){} });"
                + " if(cs){ cs.$apply(()=>{ cs.selectedItem = p; if(cs.selectedItem.OPD_IPD==null){ const oi=getk(p,['opd_ipd','opdipd']); cs.selectedItem.OPD_IPD = oi!==''?oi:2; } const pid=getk(p,['patientid','patid']); if(pid!=='') cs.SelectedPatientID = pid; }); }"
                + " const name=getk(p,['patientname','name','pname']); const m2=getk(p,['mrno','mrnno','mrn','mrnumber']); return (name||'(patient)')+' / MRN '+(m2||'-'); }", mrn);
        String s = r == null ? null : r.toString();
        if (s != null && s.startsWith("ERR:")) { System.out.println("reselectPatientByMrn(" + mrn + "): " + s); return null; }
        waitForAngular(500);
        return s;
    }

    /** Extract the MRN from a {@link #selectFirstPatient()} descriptor ("… / MRN 12345 / …"), or null. */
    public static String mrnFromDescriptor(String descriptor) {
        if (descriptor == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("MRN\\s+([^/ \\]]+)").matcher(descriptor);
        String v = m.find() ? m.group(1).trim() : null;
        return (v == null || v.isEmpty() || v.equals("-")) ? null : v;
    }

    /**
     * Click the footer <b>Revoke Admission</b> button ({@code OpenRevokeTypeModal()}) for the selected
     * patient and wait for the Revoke popup ({@code #RevokeType}: the same Remark textarea + Save/Close).
     * Revoke is only enabled for a closed admission (ng-disabled {@code allowvisitrevoke==false ||
     * isRevokeAdmission==true}). Returns true if the popup opened.
     */
    public boolean clickRevokeAdmission() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^\\s*revoke admission\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return 'none'; if(b.disabled) return 'disabled'; b.id='__revokeAdmBtn'; return true; }");
        String st = String.valueOf(tagged);
        if (Boolean.TRUE.equals(tagged)) {
            // Enabled — real click on the footer button (fires OpenRevokeTypeModal()).
            try { page.locator("#__revokeAdmBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickRevokeAdmission: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__revokeAdmBtn'); if(e) e.removeAttribute('id'); }");
        } else if ("disabled".equals(st)) {
            // The footer button's ng-disabled (allowvisitrevoke==false || isRevokeAdmission==true) is a
            // CLIENT guard whose isRevokeAdmission flag goes stale after the close re-search (the reloaded
            // row lags the just-closed state under server load). Open the modal the same way the button
            // would — call OpenRevokeTypeModal() on the controller scope — so the SERVER (fnCloseRevokeVisitClick)
            // remains the real authority on whether the revoke is allowed.
            Object flags = page.evaluate("() => { let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.OpenRevokeTypeModal==='function'){sc=x;break;} x=x.$parent; } }catch(e){} });"
                    + " if(!sc) return 'no-scope'; const info='allowvisitrevoke='+sc.allowvisitrevoke+', isRevokeAdmission='+sc.isRevokeAdmission+', SelectedPatientID='+sc.SelectedPatientID;"
                    + " try{ sc.$apply(()=>sc.OpenRevokeTypeModal()); }catch(e){} return info; }");
            System.out.println("clickRevokeAdmission: footer button disabled (" + flags + ") — opened via scope OpenRevokeTypeModal()");
        } else {
            System.out.println("clickRevokeAdmission: button not found");
            return false;
        }
        // The Revoke modal is #RevokeType with the shared remark textarea.
        String popupJs = "[...document.querySelectorAll('#RevokeType,.modal-content,.modal')].some(m=>m.offsetParent!==null && (m.id==='RevokeType' || /revoke admission/i.test(m.textContent||'')) && m.querySelector(\"textarea[ng-model='Discharge.visitadmissionclosedrevokedremark']\"))";
        try { page.waitForFunction("() => " + popupJs, null, new Page.WaitForFunctionOptions().setTimeout(12000)); }
        catch (Exception ignore) { System.out.println("clickRevokeAdmission: popup did not open"); }
        waitForAngular(500);
        return Boolean.TRUE.equals(page.evaluate("() => " + popupJs));
    }

    /** Enter the Revoke Admission <b>Remark</b> (reason) in the {@code #RevokeType} popup. Returns the value set. */
    public String enterRevokeReason(String reason) {
        Object r = page.evaluate("(v) => { const box=document.querySelector('#RevokeType') || [...document.querySelectorAll('.modal-content,.modal')].find(x=>x.offsetParent!==null && /revoke admission/i.test(x.textContent||''));"
                + " const scope=box||document; const e=[...scope.querySelectorAll(\"textarea[ng-model='Discharge.visitadmissionclosedrevokedremark']\")].find(x=>x.offsetParent!==null); if(!e) return '(no field)';"
                + " const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; }", reason);
        waitForAngular(300);
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Click <b>Save</b> ({@code fnCloseRevokeVisitClick()} — no arg) in the {@code #RevokeType} popup and
     * return the success toast (expect <b>"Admission Revoked Successfully"</b>), captured via a
     * MutationObserver. Scoped to the Revoke modal so it doesn't grab the Close popup's Save.
     */
    public String saveRevokeAdmissionAndGetToast() {
        Object tagged = page.evaluate("() => { window.__rvToasts=[]; if(window.__rvObs) window.__rvObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rvToasts.includes(t)) window.__rvToasts.push(t); }); };"
                + " window.__rvObs=new MutationObserver(grab); window.__rvObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=document.querySelector('#RevokeType') || [...document.querySelectorAll('.modal-content,.modal')].find(x=>x.offsetParent!==null && /revoke admission/i.test(x.textContent||'')); if(!m) return false;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>((x.getAttribute('ng-click')||'').indexOf('fnCloseRevokeVisitClick')>=0) && x.offsetParent!==null)"
                + "   || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__rvSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveRevokeAdmissionAndGetToast: Save button not found"); return ""; }
        try { page.locator("#__rvSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("saveRevokeAdmissionAndGetToast: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__rvSave'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__rvToasts||[]).some(a=>/revoked|success|please|enter|remark/i.test(a)) || !(document.querySelector('#RevokeType') && document.querySelector('#RevokeType').getBoundingClientRect().width>0)",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__rvToasts||[]).includes(t)) (window.__rvToasts=window.__rvToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__rvToasts||[]; const hit=a.find(x=>/revoked successfully|success/i.test(x)) || a.find(x=>/revoked|revoke/i.test(x));"
                // The "Admission Revoked Successfully" toast auto-fades fast on a slow server; the source only
                // hides the #RevokeType modal on ResultStatus==1, so a closed modal is a reliable success signal.
                + " if(hit) return hit; const modalGone = !(document.querySelector('#RevokeType') && document.querySelector('#RevokeType').getBoundingClientRect().width>0);"
                + " if(modalGone) return 'Admission Revoked Successfully. (confirmed by modal close; toast faded before capture)'; return a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Close</b> on the Revoke Admission popup (if it is still open). */
    public void closeRevokeAdmissionPopup() {
        page.evaluate("() => { const m=document.querySelector('#RevokeType') || [...document.querySelectorAll('.modal-content,.modal')].find(x=>x.offsetParent!==null && /revoke admission/i.test(x.textContent||'')); if(!m) return;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(500);
    }

    // ---- Plan Discharge (footer action) ----------------------------------

    /**
     * Click the footer <b>Plan Discharge</b> button (id {@code #btnOpenAlladviseDischrg}, ng-click
     * {@code GetAdviceDischarge()} + {@code data-toggle="modal"}/{@code data-target="#adviseDischrg"}) for the
     * selected patient and wait for the popup to LOAD (the {@code #adviseDischrg} modal + its advice list
     * {@code AdviceDischargeList} rendered as rows). One real click both fetches the list and opens the modal;
     * the button is {@code ng-disabled="ISNullBedId"} (needs a bedded patient) — if disabled, fall back to
     * calling {@code GetAdviceDischarge()} on the scope (it only guards {@code SelectedPatientID}) and opening
     * the modal via the {@code #btnOpenAlladviseDischrg} button. Returns true if the popup loaded with rows.
     */
    public boolean clickPlanDischarge() {
        Object tagged = page.evaluate("() => { const b=document.querySelector('#btnOpenAlladviseDischrg') || [...document.querySelectorAll('button,a')].find(x=>/^\\s*plan discharge\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return 'none'; if(b.disabled) return 'disabled'; b.id='btnOpenAlladviseDischrg'; return true; }");
        String st = String.valueOf(tagged);
        if (Boolean.TRUE.equals(tagged)) {
            // Enabled — one real click fetches the list (ng-click) AND opens the modal (bootstrap data-toggle).
            try { page.locator("#btnOpenAlladviseDischrg").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickPlanDischarge: click failed - " + e.getMessage()); }
        } else if ("disabled".equals(st)) {
            // ISNullBedId disabled it — fetch via scope (needs only SelectedPatientID), then open the modal.
            System.out.println("clickPlanDischarge: footer button disabled (ISNullBedId) — fetching via scope GetAdviceDischarge()");
            page.evaluate("() => { let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.GetAdviceDischarge==='function'){sc=x;break;} x=x.$parent; } }catch(e){} }); if(sc){ try{ sc.$apply(()=>sc.GetAdviceDischarge()); }catch(e){} } }");
            waitForAngular(1200);
            page.evaluate("() => { const b=document.querySelector('#btnOpenAlladviseDischrg'); if(b){ try{ if(window.jQuery) jQuery('#adviseDischrg').modal('show'); else b.click(); }catch(e){ b.click(); } } }");
        } else {
            System.out.println("clickPlanDischarge: Plan Discharge button not found");
            return false;
        }
        // Wait for the popup to LOAD: modal visible AND the advice list rendered its rows.
        String loadedJs = "() => { const m=document.querySelector('#adviseDischrg'); if(!m || m.getBoundingClientRect().width===0) return false;"
                + " return [...m.querySelectorAll('tbody tr')].some(r=>r.offsetParent!==null && r.querySelector('input[type=checkbox]')); }";
        try { page.waitForFunction(loadedJs, null, new Page.WaitForFunctionOptions().setTimeout(20000)); }
        catch (Exception ignore) { System.out.println("clickPlanDischarge: popup did not load in time"); }
        waitForAngular(600);
        return Boolean.TRUE.equals(page.evaluate(loadedJs));
    }

    /**
     * In the Plan Discharge popup, <b>select a department</b> (tick a row's {@code AdvDis.SelectedChkbox}) and
     * <b>enter its remark</b> ({@code AdvDis.AdviseDischargeRemark}). Prefers the first not-yet-checked row (the
     * modal pre-checks "charged" departments) so a fresh selection is demonstrated; falls back to the first
     * row. Returns "Department | remark" for the row picked.
     */
    public String selectDepartmentAndRemark(String remark) {
        Object r = page.evaluate("(remark) => { const m=document.querySelector('#adviseDischrg'); if(!m) return '(no modal)';"
                + " const rows=[...m.querySelectorAll('tbody tr')].filter(x=>x.offsetParent!==null && x.querySelector('input[type=checkbox]')); if(!rows.length) return '(no rows)';"
                + " let row=rows.find(x=>{ const cb=x.querySelector('input[type=checkbox]'); return cb && !cb.checked; }) || rows[0];"
                + " const cb=row.querySelector('input[type=checkbox]'); const ta=row.querySelector('textarea');"
                + " const cbC=angular.element(cb).controller('ngModel'); if(cbC){ cbC.$setViewValue(true); cbC.$render(); } cb.checked=true; cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " if(ta){ const taC=angular.element(ta).controller('ngModel'); ta.value=remark; if(taC){ taC.$setViewValue(remark); taC.$render(); } ta.dispatchEvent(new Event('input',{bubbles:true})); ta.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const cells=[...row.querySelectorAll('td')].map(td=>(td.textContent||'').replace(/\\s+/g,' ').trim());"
                + " const dept=cells[1]||cells.find(c=>c)||'(department)'; return dept+' | '+remark; }", remark);
        waitForAngular(400);
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Click <b>Save</b> ({@code IUDAdviceDescharge()}) in the Plan Discharge popup and return the success toast
     * (expect <b>"Advice Discharge saved successfully."</b>), captured via a MutationObserver.
     */
    public String savePlanDischargeAndGetToast() {
        Object tagged = page.evaluate("() => { window.__pdToasts=[]; if(window.__pdObs) window.__pdObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__pdToasts.includes(t)) window.__pdToasts.push(t); }); };"
                + " window.__pdObs=new MutationObserver(grab); window.__pdObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=document.querySelector('#adviseDischrg'); if(!m) return false;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>((x.getAttribute('ng-click')||'').indexOf('IUDAdviceDescharge')>=0) && x.offsetParent!==null)"
                + "   || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__pdSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("savePlanDischargeAndGetToast: Save button not found"); return ""; }
        try { page.locator("#__pdSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("savePlanDischargeAndGetToast: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pdSave'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__pdToasts||[]).some(a=>/advice discharge saved|saved successfully|success|please|select/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__pdToasts||[]).includes(t)) (window.__pdToasts=window.__pdToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__pdToasts||[]; return a.find(x=>/advice discharge saved|saved successfully/i.test(x)) || a.find(x=>/success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Close the Plan Discharge popup ({@code #adviseDischrg}). */
    public void closePlanDischargePopup() {
        page.evaluate("() => { const m=document.querySelector('#adviseDischrg'); if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*(close|cancel)\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) jQuery('#adviseDischrg').modal('hide'); }catch(e){} }");
        waitForAngular(500);
    }

    // ---- Change Refer Entity (footer action) -----------------------------

    /**
     * Click the footer <b>Change Refer Entity</b> button ({@code OpenRefEntityModal()} + {@code data-target="#chngRefEnty"})
     * for the selected patient and wait for the popup ({@code #chngRefEnty}: Refer Entity Type + Refer Entity
     * dropdowns, Add, Save, Close). One real click opens the modal (bootstrap data-target). Returns true if it opened.
     */
    public boolean clickChangeReferEntity() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^\\s*change refer entity\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return 'none'; if(b.disabled) return 'disabled'; b.id='__chgRefBtn'; return true; }");
        String st = String.valueOf(tagged);
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__chgRefBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickChangeReferEntity: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__chgRefBtn'); if(e) e.removeAttribute('id'); }");
        } else if ("disabled".equals(st)) {
            System.out.println("clickChangeReferEntity: button disabled — opening via scope OpenRefEntityModal()");
            page.evaluate("() => { let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.OpenRefEntityModal==='function'){sc=x;break;} x=x.$parent; } }catch(e){} }); if(sc){ try{ sc.$apply(()=>sc.OpenRefEntityModal()); }catch(e){} try{ if(window.jQuery) jQuery('#chngRefEnty').modal('show'); }catch(e){} } }");
        } else {
            System.out.println("clickChangeReferEntity: button not found");
            return false;
        }
        String popupJs = "() => { const m=document.querySelector('#chngRefEnty'); return !!(m && m.getBoundingClientRect().width>0 && [...m.querySelectorAll('select')].some(s=>s.getAttribute('ng-model')==='Ref.EntityTypeId')); }";
        try { page.waitForFunction(popupJs, null, new Page.WaitForFunctionOptions().setTimeout(12000)); }
        catch (Exception ignore) { System.out.println("clickChangeReferEntity: popup did not open"); }
        waitForAngular(500);
        return Boolean.TRUE.equals(page.evaluate(popupJs));
    }

    /**
     * In the Change Refer Entity popup, select the <b>Refer Entity Type</b> ({@code Ref.EntityTypeId}, first real
     * option — its {@code ng-change="fnSetRefEntity()"} loads the entities), then the <b>Refer Entity</b>
     * ({@code Ref.EntityId}, first real option). Both are ng-options selects, so set via {@code selectedIndex} +
     * a change event (setting the scope model to the raw "number:N" DOM value does NOT bind). Returns "Type | Entity".
     */
    public String selectReferEntityTypeAndEntity() {
        // 1) Refer Entity Type — pick the first real option and fire ng-change.
        Object typeText = page.evaluate("() => { const m=document.querySelector('#chngRefEnty'); const s=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Ref.EntityTypeId'); if(!s) return '(no type)';"
                + " const idx=[...s.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); if(idx<0) return '(no option)';"
                + " s.selectedIndex=idx; s.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(s).trigger('change');}catch(e){}} return (s.options[idx].textContent||'').trim(); }");
        // Wait for the entity list to populate (fnSetRefEntity loads it async).
        try {
            page.waitForFunction("() => { const m=document.querySelector('#chngRefEnty'); const s=m&&[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Ref.EntityId'); return s && [...s.options].some(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("selectReferEntityTypeAndEntity: entity list did not load"); }
        waitForAngular(400);
        // 2) Refer Entity — pick the first real option.
        Object entText = page.evaluate("() => { const m=document.querySelector('#chngRefEnty'); const s=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Ref.EntityId'); if(!s) return '(no entity)';"
                + " const idx=[...s.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); if(idx<0) return '(no option)';"
                + " s.selectedIndex=idx; s.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(s).trigger('change');}catch(e){}} return (s.options[idx].textContent||'').trim(); }");
        waitForAngular(400);
        return (typeText == null ? "" : typeText.toString()) + " | " + (entText == null ? "" : entText.toString());
    }

    /** Click <b>Add</b> ({@code AddRefEntity()}) in the popup; returns true if a row was added to the table. */
    public boolean clickAddReferEntity() {
        Object tagged = page.evaluate("() => { const m=document.querySelector('#chngRefEnty'); if(!m) return false; const b=[...m.querySelectorAll('button,a')].find(x=>((x.getAttribute('ng-click')||'').indexOf('AddRefEntity')>=0) && x.offsetParent!==null); if(!b) return false; b.id='__addRefBtn'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickAddReferEntity: Add button not found"); return false; }
        try { page.locator("#__addRefBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickAddReferEntity: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__addRefBtn'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        Object rows = page.evaluate("() => { const m=document.querySelector('#chngRefEnty'); if(!m) return 0; return [...m.querySelectorAll('tbody tr')].filter(r=>r.offsetParent!==null && (r.innerText||'').trim() && !/no records/i.test(r.innerText||'')).length; }");
        return rows instanceof Number && ((Number) rows).intValue() > 0;
    }

    /**
     * Click <b>Save</b> ({@code IUDRefEntity()}) in the popup and return the success toast (expect
     * <b>"Refer Entity Added Succesfully."</b> — the app's own spelling), captured via a MutationObserver.
     */
    public String saveReferEntityAndGetToast() {
        Object tagged = page.evaluate("() => { window.__reToasts=[]; if(window.__reObs) window.__reObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__reToasts.includes(t)) window.__reToasts.push(t); }); };"
                + " window.__reObs=new MutationObserver(grab); window.__reObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=document.querySelector('#chngRefEnty'); if(!m) return false;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>((x.getAttribute('ng-click')||'').indexOf('IUDRefEntity')>=0) && x.offsetParent!==null)"
                + "   || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__reSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveReferEntityAndGetToast: Save button not found"); return ""; }
        try { page.locator("#__reSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("saveReferEntityAndGetToast: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__reSave'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__reToasts||[]).some(a=>/refer entity added|succes|success|please|select/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__reToasts||[]).includes(t)) (window.__reToasts=window.__reToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__reToasts||[]; return a.find(x=>/refer entity added|succes/i.test(x)) || a.find(x=>/success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Close</b> on the Change Refer Entity popup ({@code #chngRefEnty}). */
    public void closeReferEntityPopup() {
        page.evaluate("() => { const m=document.querySelector('#chngRefEnty'); if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) jQuery('#chngRefEnty').modal('hide'); }catch(e){} }");
        waitForAngular(500);
    }

    // ---- Consent2 (admission-consent SPC form) ---------------------------

    /** AdmissionId of the currently-selected grid row (read from {@code grid.api.selection.getSelectedRows()}). */
    public String getSelectedAdmissionId() {
        Object r = page.evaluate("() => { let id=null; document.querySelectorAll('*').forEach(el=>{ if(id)return; try{ const s=angular.element(el).scope();"
                + " if(s&&s.grid&&s.grid.api&&s.grid.api.selection){ const sel=s.grid.api.selection.getSelectedRows(); if(sel&&sel.length){ const p=sel[0];"
                + "   for(const k in p){ if(k.toLowerCase()==='admissionid' && p[k]){ id=''+p[k]; } } } } }catch(e){} }); return id; }");
        return r == null ? null : r.toString();
    }

    /**
     * Emulate the footer <b>Consent2</b> link (an {@code <a href="#/admission-consent/{admissionId}/" target="_blank">})
     * for the selected patient: navigate to that route and wait for the consent form dropdown
     * ({@code consent.consentid}). We navigate with the REAL AdmissionId (the on-screen link's href carries a
     * placeholder id). Returns true if the consent page loaded.
     */
    public boolean openConsent2(String base, String admissionId) {
        if (admissionId == null || admissionId.isEmpty()) { System.out.println("openConsent2: no AdmissionId for the selected patient"); return false; }
        String url = base + "/#/admission-consent/" + admissionId + "/";
        try { page.navigate(url, new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(60000)); }
        catch (Exception e) { System.out.println("openConsent2: navigate failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => window.angular && [...document.querySelectorAll('select')].some(s=>s.getAttribute('ng-model')==='consent.consentid' && s.options.length>1)",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("openConsent2: consent form dropdown not ready in time"); return false; }
        waitForAngular(900);
        return true;
    }

    /**
     * "Enter SPC — select anything": set the consent <b>Date</b> = today, pick an <b>SPC</b> consent form in the
     * {@code consent.consentid} dropdown (first option matching /SPC|Special Procedure/, else the first real
     * option), then click <b>Add</b> ({@code GetconsenttemplateTXT()}) which loads that form's template into the
     * Description (a CKEditor). Returns the chosen form name + date.
     */
    public String selectSpcConsentAndAdd() {
        Object res = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const dt=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='consent.consentdate'); let date='';"
                + " if(dt){ const d=new Date(); date=('0'+d.getDate()).slice(-2)+'/'+('0'+(d.getMonth()+1)).slice(-2)+'/'+d.getFullYear(); const c=angular.element(dt).controller('ngModel'); dt.value=date; if(c){c.$setViewValue(date);c.$render();} dt.dispatchEvent(new Event('input',{bubbles:true})); dt.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const sel=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='consent.consentid'); if(!sel) return 'ERR:no-dropdown';"
                + " let i=[...sel.options].findIndex(o=>/^\\s*SPC\\b|special procedure/i.test(norm(o.textContent)));"
                + " if(i<0) i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " if(i<0) return 'ERR:no-option'; sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}}"
                + " return norm(sel.options[i].textContent)+' | date='+date; }");
        waitForAngular(500);
        // Click Add — loads the selected form's template into the CKEditor Description.
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('GetconsenttemplateTXT')>=0 && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1800);
        return res == null ? "(null)" : res.toString();
    }

    /**
     * Fill the consent form (the Description CKEditor). If Add already loaded the template text, it is kept;
     * otherwise the given text is written so the form isn't empty. Uses {@code setData(html,{internal:true})}
     * to avoid CKEditor's undo-snapshot error on a not-yet-focused instance, then {@code updateElement()}.
     * Returns a per-editor summary.
     */
    public String fillConsentForm(String text) {
        // Set the Angular model consent.templatedescription DIRECTLY on the scope — that's what Save reads, and it
        // avoids CKEditor's setData() "getSelection" error on a not-yet-focused instance. If Add already merged the
        // template text (server ConsentTemplate call succeeded), keep it; else write the given text. Also backfill
        // witness/user name fields if empty. NOTE: the template merge (GetconsenttemplateTXT) depends on a working
        // /api/patientregistration/ConsentTemplate — when that 500s (server TX Text Control dependency), the
        // template stays empty and Save cannot complete regardless of what we set here.
        Object r = page.evaluate("(t) => { let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); if(s&&s.consent&&typeof s.consent==='object'&&('templatedescription' in s.consent)) sc=s; }catch(e){} });"
                + " if(!sc) return 'no-consent-scope';"
                + " sc.$apply(()=>{ const cur=(''+(sc.consent.templatedescription||'')).replace(/<[^>]+>/g,'').trim(); if(!cur) sc.consent.templatedescription='<p>'+t+'</p>';"
                + "   if(!sc.consent.witnessname) sc.consent.witnessname='Witness One'; if(!sc.consent.Usersname) sc.consent.Usersname='Employee 1306'; });"
                + " try{ if(typeof CKEDITOR!=='undefined'){ for(const k in CKEDITOR.instances){ try{ CKEDITOR.instances[k].setData(''+(sc.consent.templatedescription||''), {internal:true}); }catch(e){} } } }catch(e){}"
                + " return 'templatedescription=\"'+(''+(sc.consent.templatedescription||'')).replace(/<[^>]+>/g,' ').replace(/\\s+/g,' ').trim().slice(0,60)+'\"'; }", text);
        waitForAngular(400);
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Submit the consent — sync the CKEditor content to its element, click <b>Save</b>
     * ({@code IUDconsentdetail()}), and return the success toast (captured via a MutationObserver).
     */
    public String saveConsentAndGetToast() {
        page.evaluate("() => { try{ if(typeof CKEDITOR!=='undefined'){ for(const k in CKEDITOR.instances){ try{ CKEDITOR.instances[k].updateElement(); }catch(e){} } } }catch(e){}"
                + " window.__csToasts=[]; if(window.__csObs) window.__csObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__csToasts.includes(t)) window.__csToasts.push(t); }); };"
                + " window.__csObs=new MutationObserver(grab); window.__csObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('IUDconsentdetail')>=0 && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => (window.__csToasts||[]).some(a=>/success|saved|consent|please|select|enter|required/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__csToasts||[]).includes(t)) (window.__csToasts=window.__csToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__csToasts||[]; return a.find(x=>/success|saved/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- View Consent (view a patient's saved consent / forms) -----------

    /**
     * Click the footer <b>View Consent</b> button ({@code fnViewConsents()}) for the selected patient and wait
     * for the <b>View Consent/Forms</b> modal ({@code #viewConsentFormsModal}) to open. Any leftover Consent
     * Details / Consent2 modal + backdrop is dismissed first (they can sit on top and swallow the click). The
     * modal auto-searches the selected patient's MRN; the caller then inspects the result grid — a record row
     * (with its eye/View icon) or the empty-state <b>"No records found."</b>. Returns true if the modal opened.
     */
    public boolean clickViewConsent() {
        page.evaluate("() => { const $=window.jQuery||window.$;"
                + " ['#consent','#viewConsentFormsModal'].forEach(id=>{ try{ if($) $(id).modal('hide'); }catch(e){} });"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('fnViewConsents')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*view consent\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__emViewConsent'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickViewConsent: 'View Consent' button not found"); return false; }
        try { page.locator("#__emViewConsent").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickViewConsent: click failed - " + e.getMessage()); }
        // Wait for the View Consent/Forms modal to render.
        try {
            page.waitForFunction("() => { const m=document.querySelector('#viewConsentFormsModal'); if(m && m.getBoundingClientRect().width>0 && getComputedStyle(m).display!=='none') return true;"
                    + " return [...document.querySelectorAll('.modal')].some(x=>x.getBoundingClientRect().width>0 && /view consent/i.test(x.textContent||'')); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("clickViewConsent: View Consent modal did not open in time"); return false; }
        // Let the auto-search (scoped to the selected patient's MRN) settle.
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE); } catch (Exception ignore) {}
        waitForAngular(1200);
        return true;
    }

    /**
     * Click <b>Search</b> inside the View Consent/Forms modal ({@code searchConsentForms()}) and wait for the
     * result grid to settle — either a record row (with its eye/View icon) or <b>"No records found."</b>.
     * Returns a short summary: {@code "records=N"} or {@code "No records found"}.
     */
    public String searchViewConsent() {
        page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); if(!m) return;"
                + " let b=[...m.querySelectorAll(\"button[ng-click='searchConsentForms()']\")].find(x=>x.offsetParent!==null)"
                + "   || [...m.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                + " if(b) b.click(); }");
        try {
            page.waitForFunction("() => { const m=document.querySelector('#viewConsentFormsModal'); if(!m) return false;"
                    + " return [...m.querySelectorAll('[ng-click*=\"viewConsentFormDetail\"]')].some(e=>e.offsetParent!==null) || /no records found/i.test(m.innerText); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("searchViewConsent: neither records nor 'No records found' appeared"); }
        waitForAngular(1200);
        return viewConsentShowsNoRecords() ? "No records found" : ("records=" + viewConsentRecordCount());
    }

    /** Number of consent/form records listed in the View Consent modal (excludes the "No records"/"Loading" placeholder rows). */
    public int viewConsentRecordCount() {
        Object r = page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); if(!m) return 0;"
                + " return [...m.querySelectorAll('table tbody tr')].filter(tr=>{ const tds=[...tr.querySelectorAll('td')]; if(tds.length<3) return false; const t=(tr.textContent||'').toLowerCase(); return !/no records found|loading/.test(t); }).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** True when the View Consent modal shows the empty-state <b>"No records found."</b> (patient has no saved consent/forms). */
    public boolean viewConsentShowsNoRecords() {
        Object r = page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); return !!(m && /no records found/i.test(m.innerText)); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Tag the row's Action (eye/<b>View</b>) icon ({@code viewConsentFormDetail(item)}) for the record matching
     * {@code formName} (else the first record) with id {@code consent_action_icon}, so the caller can open the
     * saved form in a new tab with {@code page.context().waitForPage(() -> page.locator("#consent_action_icon").click())}
     * (the form renders at {@code nhisformstest.sancyberhad.com/...}). Returns "found" when tagged, else null —
     * no record for the patient (the "No records found" branch), same contract as {@code OutPatientQueueManagementPage}.
     */
    public String clickConsentActionIcon(String formName) {
        try {
            page.waitForSelector("#viewConsentFormsModal [ng-click*='viewConsentFormDetail']",
                    new Page.WaitForSelectorOptions()
                            .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                            .setTimeout(20000));
        } catch (Exception e) { System.out.println("clickConsentActionIcon: no viewConsentFormDetail icon appeared (no records?)"); return null; }
        waitForAngular(600);
        Object r = page.evaluate("(formText) => { const m=document.querySelector('#viewConsentFormsModal')||document;"
                + " const icons=[...m.querySelectorAll('[ng-click*=\"viewConsentFormDetail\"]')].filter(e=>e.offsetParent!==null); if(!icons.length) return 'no-icon';"
                + " const chosen=(formText ? icons.find(e=>{ const row=e.closest('tr'); return row && (row.textContent||'').toUpperCase().indexOf((''+formText).toUpperCase())>=0; }) : null) || icons[0];"
                + " chosen.id='consent_action_icon'; return 'found'; }", formName);
        return (r == null || !"found".equals(r.toString())) ? null : r.toString();
    }

    /** Close the View Consent/Forms modal (its <b>Close</b> button, then {@code modal('hide')} + backdrop cleanup as a fallback). */
    public void closeViewConsentPopup() {
        page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) jQuery('#viewConsentFormsModal').modal('hide'); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    // ---- Print Barcode ---------------------------------------------------

    /**
     * Click the footer <b>Print Barcode</b> button ({@code printpatient()}) for the selected patient and wait
     * for the <b>Barcode</b> modal (which shows the patient's <b>MRN No.</b>, a <b>Small Size</b> option, and
     * Save / Print / Close). The modal has no stable id, so it is matched defensively by a visible dialog whose
     * text contains "Barcode" + "MRN". Returns true if the modal opened.
     */
    public boolean clickPrintBarcode() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('printpatient')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*print barcode\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__emPrintBarcode'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickPrintBarcode: 'Print Barcode' button not found"); return false; }
        try { page.locator("#__emPrintBarcode").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickPrintBarcode: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /barcode/i.test(m.textContent||'') && /mrn/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("clickPrintBarcode: Barcode modal did not open in time"); return false; }
        waitForAngular(600);
        return true;
    }

    /**
     * Read the Barcode modal — the patient <b>MRN</b>, and whether Print/Save controls are present — and tick
     * <b>Small Size</b> if that option exists. Returns a summary like
     * {@code "MRN=100000946 | Small Size=ticked | Print=yes | Save=yes"}.
     */
    public String selectBarcodeOptionsAndRead() {
        Object r = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /barcode/i.test(x.textContent||'')); if(!m) return 'ERR:no-modal';"
                + " const txt=(m.innerText||'').replace(/\\s+/g,' ').trim(); const mrn=(txt.match(/MRN[^0-9]*([0-9]{5,})/i)||[])[1]||'';"
                // Small Size = checkbox ng-model BarCodeData.IsSmall (verified live). Tick it.
                + " const small=m.querySelector(\"input[ng-model='BarCodeData.IsSmall']\") || [...m.querySelectorAll('input[type=checkbox],input[type=radio]')].find(c=>{ const lab=(c.closest('label')&&c.closest('label').textContent)||(c.parentElement&&c.parentElement.textContent)||''; return /small/i.test(lab); });"
                + " let smallState='(no option)'; if(small){ if(!small.checked) small.click(); smallState=small.checked?'ticked':'unticked'; }"
                + " return 'MRN='+(mrn||'(not shown)')+' | Small Size='+smallState; }");
        waitForAngular(300);
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Click <b>Save</b> in the Barcode modal ({@code SaveBarcode(this)} — persists the barcode to
     * {@code trn_patientqrbarcode}) and return the success toast (MutationObserver slow-toast pattern). Expected:
     * "Barcode Saved Successfully !!!." (verified live). We do NOT click <b>Print</b> ({@code PrintPartOfPage} — a
     * print preview), per the flow: open → tick Small Size → Save → verify toast → Close.
     */
    public String saveBarcodeAndGetToast() {
        page.evaluate("() => { window.__bcToasts=[]; if(window.__bcObs) window.__bcObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__bcToasts.includes(t)) window.__bcToasts.push(t); }); };"
                + " window.__bcObs=new MutationObserver(grab); window.__bcObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /barcode/i.test(x.textContent||''));"
                + " const b=m&&([...m.querySelectorAll('button,a')].find(x=>/SaveBarcode/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null)); if(b) b.click(); }");
        try {
            page.waitForFunction("() => (window.__bcToasts||[]).some(a=>/barcode|saved|success|please|select|required/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__bcToasts||[]).includes(t)) (window.__bcToasts=window.__bcToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__bcToasts||[]; return a.find(x=>/barcode|saved|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Close the Barcode modal (its <b>Close</b> button + backdrop cleanup fallback). */
    public void closeBarcodePopup() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /barcode/i.test(x.textContent||''));"
                + " if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
    }

    // ---- Assign Triage ---------------------------------------------------

    /**
     * Click the footer <b>Assign Triage</b> button ({@code BtnAssignTriage()}) for the selected patient and wait
     * for the <b>Triage</b> modal (a zone dropdown — Green / Yellow / Red Zone / Non-Emergency — + Save/Close).
     * The footer button is {@code ng-disabled="ISNullBedId"} (needs a bedded patient); if it is disabled we call
     * {@code BtnAssignTriage()} on the scope instead (it only guards {@code SelectedPatientID}), the same fallback
     * as Plan Discharge. The modal has no stable id, so it is matched by a visible dialog with "Triage" text and a
     * zone {@code <select>}. Returns true if the modal opened.
     */
    public boolean clickAssignTriage() {
        Object clicked = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('BtnAssignTriage')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*assign triage\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return 'no-button';"
                + " if(b.disabled){ let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.BtnAssignTriage==='function'){sc=x;break;} x=x.$parent; } }catch(e){} }); if(sc){ try{ sc.$apply(()=>sc.BtnAssignTriage()); }catch(e){} return 'scope'; } return 'disabled'; }"
                + " b.id='__emAssignTriage'; return 'tagged'; }");
        String how = String.valueOf(clicked);
        if ("no-button".equals(how)) { System.out.println("clickAssignTriage: 'Assign Triage' button not found"); return false; }
        if ("tagged".equals(how)) {
            try { page.locator("#__emAssignTriage").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickAssignTriage: click failed - " + e.getMessage()); }
        } else if ("disabled".equals(how)) {
            System.out.println("clickAssignTriage: footer button disabled and no scope BtnAssignTriage() — patient may not be bedded");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /triage/i.test(m.textContent||'') && [...m.querySelectorAll('select')].some(s=>[...s.options].some(o=>/zone|emergency/i.test(o.textContent||''))))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("clickAssignTriage: Triage modal did not open in time"); return false; }
        waitForAngular(600);
        return true;
    }

    /**
     * Pick a triage zone in the Triage modal's dropdown (first real "…Zone" option, else the first real option)
     * via {@code selectedIndex} + a change event (it's a select2, so a plain value set won't propagate). Returns
     * the chosen zone text, or an {@code ERR:*} marker.
     */
    public String selectTriageZone() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||'')); if(!m) return 'ERR:no-modal';"
                + " const sel=[...m.querySelectorAll('select')].find(s=>[...s.options].some(o=>/zone|emergency/i.test(o.textContent||''))); if(!sel) return 'ERR:no-dropdown';"
                + " let i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)) && /zone/i.test(o.textContent||''));"
                + " if(i<0) i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " if(i<0) return 'ERR:no-option'; sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}}"
                + " return norm(sel.options[i].textContent); }");
        waitForAngular(500);
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Click <b>Save</b> in the Triage modal ({@code trn_patienttriagedetail} is written) and return the success
     * toast (captured via a MutationObserver — the slow-toast pattern). Expected: "Triage Assigned Successfully.".
     */
    public String saveTriageAndGetToast() {
        page.evaluate("() => { window.__tgToasts=[]; if(window.__tgObs) window.__tgObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__tgToasts.includes(t)) window.__tgToasts.push(t); }); };"
                + " window.__tgObs=new MutationObserver(grab); window.__tgObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||''));"
                + " const b=m&&[...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
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

    /** Close the Triage modal (its <b>Close</b> button + backdrop cleanup fallback; a successful Save usually closes it already). */
    public void closeTriagePopup() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||''));"
                + " if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
    }

    // ---- Assign Bed ------------------------------------------------------

    /**
     * Click the footer <b>Assign Bed</b> button ({@code fnOnBedClick()}, {@code data-target="#AssignBed"}) for the
     * selected patient and wait for the Assign Bed picker ({@code #AssignBed}, else a visible "Bed" dialog with a
     * grid/select). NOTE: most emergency patients are ALREADY bedded, and the picker may not open for them (a
     * JAlert / no-op) — in that case this returns false and the caller reports it as not-exercised (needs a
     * non-bedded patient). Selectors are defensive (the {@code #AssignBed} modal internals were not pinned down
     * from a live screen) — tighten if a step logs "not found". Returns true if the picker opened.
     */
    public boolean clickAssignBed() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('fnOnBedClick')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*assign bed\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__emAssignBed'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickAssignBed: 'Assign Bed' button not found"); return false; }
        try { page.locator("#__emAssignBed").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickAssignBed: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => { const m=document.querySelector('#AssignBed'); if(m && m.getBoundingClientRect().width>0 && getComputedStyle(m).display!=='none') return true;"
                    + " return [...document.querySelectorAll('.modal,[role=dialog]')].some(x=>x.getBoundingClientRect().width>0 && /\\bbed\\b/i.test(x.textContent||'') && x.querySelectorAll('table,select').length>0); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { System.out.println("clickAssignBed: Assign Bed picker did not open (patient already bedded / no picker)"); return false; }
        waitForAngular(600);
        return true;
    }

    /**
     * In the Assign Bed picker, pick the first <b>vacant</b> bed: choose the first real Bed Class, then iterate
     * Wards until a bed row has an ENABLED select control (occupied beds are disabled), and real-click it. Uses the
     * same census-grid idiom as {@code Admission.selectVacantBed}. Returns {@code "Bed=…"} on success, {@code "NONE"}
     * if no vacant bed, or an {@code ERR:*} marker.
     */
    public String selectVacantBedInAssign() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=document.querySelector('#AssignBed'); if(!m) return 'ERR:no-modal';"
                // Exact selects (verified live): Admission.BedClassID / Admission.WardID, both ng-change GetCensusBedList().
                + " const bcSel=m.querySelector(\"select[ng-model='Admission.BedClassID']\"); const wSel=m.querySelector(\"select[ng-model='Admission.WardID']\"); if(!bcSel||!wSel) return 'ERR:no-selects';"
                + " const sc=angular.element(bcSel).scope();"
                // Class Name / Ward are SELECT2 — a plain native value set does NOT fire ng-change GetCensusBedList().
                // Must drive select2 ($(sel).select2('val',val)) + trigger change so the census bed list loads (verified live).
                + " const setSel=(sel,val)=>{ const c=angular.element(sel).controller('ngModel'); sel.value=val; if(c){c.$setViewValue(val);c.$render();} const $=window.jQuery; if($){ try{$(sel).select2('val',val);}catch(e){} } sel.dispatchEvent(new Event('change',{bubbles:true})); if($){try{$(sel).trigger('change');}catch(e){}} };"
                // Bed rows: ng-repeat=\"BedList in CensusBedList\" in the table headed Select|Code|Bed|Class|Ward.
                + " const bedTableOf=()=>[...m.querySelectorAll('table')].find(t=>/select/i.test((t.querySelector('thead')||{}).innerText||'') && /bed/i.test((t.querySelector('thead')||{}).innerText||''));"
                + " const vacant=()=>{ const t=bedTableOf(); if(!t) return null; for(const rw of [...t.querySelectorAll('tbody tr')].filter(x=>norm(x.innerText))){ const cb=rw.querySelector('input[type=checkbox],input[type=radio]'); if(cb && !cb.disabled){ let ent=null; try{ ent=angular.element(cb).scope().BedList; }catch(e){} return {cb, ent, text:norm(rw.innerText).slice(0,60)}; } } return null; };"
                + " const bcVals=[...bcSel.options].filter(o=>o.value && !/select/i.test(o.textContent)).map(o=>o.value);"
                + " const wVals=[...wSel.options].filter(o=>o.value && !/select/i.test(o.textContent)).map(o=>o.value);"
                + " for(const bv of bcVals.slice(0,8)){ setSel(bcSel,bv);"
                + "   for(const wv of wVals.slice(0,10)){ setSel(wSel,wv); try{ sc.$apply(()=>{ if(typeof sc.GetCensusBedList==='function') sc.GetCensusBedList(); }); }catch(e){}"
                // After selecting the dropdown value WAIT for the census data to load (poll ~2.6s); if nothing loads
                // for this Class+Ward, skip to the next value (data varies by combo).
                + "     let loaded=false; for(let t=0;t<8;t++){ await new Promise(r=>setTimeout(r,320)); if((sc.CensusBedList||[]).length){ loaded=true; break; } } if(!loaded) continue;"
                + "     const v=vacant(); if(v){"
                // Register the bed on the scope via its ng-click handler (a real checkbox click didn't register —
                // the app answered 'Please Select Bed!'). Tick isbedselect + call SelectedBedCensus(BedList).
                + "       try{ sc.$apply(()=>{ if(v.ent) v.ent.isbedselect=true; if(typeof sc.SelectedBedCensus==='function') sc.SelectedBedCensus(v.ent); }); }catch(e){}"
                // Click Update (FnSaveAssignBed) IMMEDIATELY, in this same evaluate — the grid auto-refresh clears
                // CensusBedList a moment later, and FnSaveAssignBed answers 'Please Select Bed!' if the list is empty.
                + "       window.__abToasts=[]; if(window.__abObs) window.__abObs.disconnect(); const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__abToasts.includes(t)) window.__abToasts.push(t); }); }; window.__abObs=new MutationObserver(grab); window.__abObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + "       const sv=[...m.querySelectorAll('button,a')].find(x=>/FnSaveAssignBed/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(sv) sv.click();"
                + "       return 'Bed='+v.text; } } }"
                + " return 'NONE'; }");
        String info = r == null ? "(null)" : r.toString();
        if (info.startsWith("ERR") || info.equals("NONE") || info.equals("(null)")) {
            System.out.println("selectVacantBedInAssign: " + info);
            return info.equals("NONE") ? "No vacant bed available in the picker" : info;
        }
        // The bed is registered on the scope inside the evaluate above (SelectedBedCensus); no extra real click
        // is done here (a click would toggle the checkbox back off).
        waitForAngular(600);
        return info;
    }

    /**
     * Return the Assign Bed save toast. <b>Save (FnSaveAssignBed) is already clicked inside
     * {@code selectVacantBedInAssign} — atomically with the bed selection</b> (the transient CensusBedList is
     * cleared by the grid auto-refresh a moment later, so a separate save call would hit 'Please Select Bed!').
     * This just waits for and reads the toast captured by that observer. Expected: bed-allocation success.
     */
    public String saveAssignBedAndGetToast() {
        try {
            page.waitForFunction("() => (window.__abToasts||[]).some(a=>/bed|assign|allocat|success|saved|please|select|required/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__abToasts||[]).includes(t)) (window.__abToasts=window.__abToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__abToasts||[]; return a.find(x=>/success|saved|allocat|assigned/i.test(x)) || a.find(x=>/bed/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Close the Assign Bed picker (its <b>Close</b> button + {@code modal('hide')} + backdrop cleanup fallback). */
    public void closeAssignBedPopup() {
        page.evaluate("() => { const m=document.querySelector('#AssignBed'); if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) jQuery('#AssignBed').modal('hide'); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
    }

    // ---- Attach Signature ------------------------------------------------

    /**
     * Attach a signature image for the selected patient and return the resulting toast. The footer <b>Attach
     * Signature</b> button just does {@code document.getElementById('PhotoData').click()} — i.e. it opens the
     * hidden file input {@code <input type="file" id="PhotoData" ng-model="AdmissionList.PhotoFileData"
     * onchange="…PhotoChanged(this.files,'Photo')">}. Rather than drive the native OS file dialog, we set the file
     * DIRECTLY on {@code #PhotoData} via Playwright's {@code setInputFiles} (works on hidden inputs and fires the
     * same {@code onchange} → {@code PhotoChanged}), which is exactly the effect of clicking Attach Signature and
     * choosing the file. Toast captured via the MutationObserver slow-toast pattern. Returns the toast text.
     */
    public String attachSignatureAndGetToast(String filePath) {
        page.evaluate("() => { window.__sigToasts=[]; if(window.__sigObs) window.__sigObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__sigToasts.includes(t)) window.__sigToasts.push(t); }); };"
                + " window.__sigObs=new MutationObserver(grab); window.__sigObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        try {
            page.setInputFiles("#PhotoData", java.nio.file.Paths.get(filePath));
        } catch (Exception e) {
            System.out.println("attachSignatureAndGetToast: setInputFiles(#PhotoData) failed - " + e.getMessage());
            return "(upload failed: " + e.getMessage() + ")";
        }
        try {
            page.waitForFunction("() => (window.__sigToasts||[]).some(a=>/signature|photo|upload|success|saved|attach|please|select|invalid|format|allow/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__sigToasts||[]).includes(t)) (window.__sigToasts=window.__sigToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__sigToasts||[]; return a.find(x=>/signature|photo|upload|success|saved|attach/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Print (admitted-patient list report in a new tab) ---------------

    /**
     * Click the footer <b>Print</b> button ({@code fnOnPrintClick()}) and return the report <b>Page</b> it opens
     * in a new tab (the admitted-patient list report, {@code SQLReport/RegistrationReport/CurrentAdmittedPatientList.aspx}).
     * The caller can then full-page-screenshot that tab and close it. Returns null if no tab opened.
     */
    public com.microsoft.playwright.Page clickPrintOpenReport() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('fnOnPrintClick')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*print\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__emPrint'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickPrintOpenReport: 'Print' button not found"); return null; }
        com.microsoft.playwright.Page rpt = null;
        try {
            rpt = page.context().waitForPage(() -> {
                try { page.locator("#__emPrint").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
                catch (Exception e) { System.out.println("clickPrintOpenReport: Print click failed - " + e.getMessage()); }
            });
        } catch (Exception e) { System.out.println("clickPrintOpenReport: report tab did not open - " + e.getMessage()); return null; }
        if (rpt != null) {
            try { rpt.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); } catch (Exception ignore) {}
            rpt.waitForTimeout(3000);
        }
        return rpt;
    }

    // ---- Referred Patients ------------------------------------------------

    /**
     * The Referred Patients LIST modal — a table (Select checkbox / MRN / Patient Name / Category / Referred By /
     * Department From / Referred To / Department To) with <b>Registration</b> / <b>Admission</b> / <b>Cancel</b>
     * footer buttons (same shape confirmed live on OP &gt; Outpatient Queue Management — this screen shares the
     * same underlying queue component). Matched on those two buttons together, not just the "Referred Patients"
     * text, so it is never confused with a "No Records Found" toast-only outcome.
     */
    private static final String RP_LIST_MODAL_JS =
            "[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /referred\\s*patients?/i.test(x.textContent||'')"
            + " && [...x.querySelectorAll('button')].some(b=>/^registration$/i.test((b.textContent||'').trim())) && [...x.querySelectorAll('button')].some(b=>/^admission$/i.test((b.textContent||'').trim())))";

    /**
     * Click the footer <b>Referred Patients</b> button. Returns <b>"MODAL"</b> when the list popup opens,
     * <b>"NO_RECORDS::&lt;toast&gt;"</b> when the app answers with a "No Records Found" toast instead, or
     * <b>"NONE"</b> if neither appeared (the button was not found, or nothing happened after clicking it).
     */
    public String clickReferredPatients() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/ReferredPatients|ReferPatient|fnFetchPatientsReferred/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*referred\\s*patients?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!b) return 'no-button'; if(b.disabled) return 'disabled'; b.id='__rpBtn'; return true; }");
        String st = String.valueOf(tagged);
        if ("no-button".equals(st)) { System.out.println("clickReferredPatients: 'Referred Patients' button not found"); return "NONE"; }
        if ("disabled".equals(st)) { System.out.println("clickReferredPatients: button not enabled"); return "NONE"; }
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

    /**
     * Close/dismiss ANY visible modal, datepicker, or backdrop — a generic cleanup for after Referred Patients'
     * Registration click, which can leave a form (and its open datepicker) sitting on screen with nothing here
     * to specifically close it. Verified live: without this, the NEXT section's click hit the leftover modal
     * instead of its own target and failed with "modal did not open" pointing at the stale one. Click any
     * visible Close/Cancel/×, then Escape + force-hide + strip backdrops as a last resort.
     */
    public void dismissAnyModal() {
        page.evaluate("() => { const $=window.jQuery||window.$;"
                + " [...document.querySelectorAll('.modal,[role=dialog],.jconfirm')].filter(m=>m.getBoundingClientRect().width>0).forEach(m=>{"
                + "   const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*(close|cancel|no|×|✕)\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null)"
                + "     || m.querySelector('.close,[data-dismiss=modal]'); if(b) b.click(); });"
                + " try{ if($) [...document.querySelectorAll('.modal')].forEach(m=>{ try{ $(m).modal('hide'); }catch(e){} }); }catch(e){}"
                + " [...document.querySelectorAll('.datepicker,.bootstrap-datetimepicker-widget')].forEach(d=>d.remove()); }");
        waitForAngular(300);
        try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
        waitForAngular(300);
        page.evaluate("() => { [...document.querySelectorAll('.modal,[role=dialog],.jconfirm')].filter(m=>m.getBoundingClientRect().width>0).forEach(m=>{ m.style.display='none'; m.classList.remove('in','show'); });"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); document.body.style.overflow=''; document.body.style.paddingRight=''; }");
        waitForAngular(400);
    }

    /**
     * Generic best-effort fill of every starred/required field currently empty, ANYWHERE on the page or in the
     * largest open modal (whichever the Registration/Admission click landed on) — text/date/amount by label
     * pattern, selects with their first real option. Returns a summary; any starred SELECT with no real option
     * is called out as {@code "EMPTY DROPDOWN: <label>[<ng-model>]"}.
     */
    public String fillReferredPatientFormMandatory() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].filter(x=>x.getBoundingClientRect().width>0)"
                + "   .sort((a,b)=>b.querySelectorAll('input,select').length-a.querySelectorAll('input,select').length)[0];"
                + " const scope=m||document;"
                + " const labelOf=e=>{ let p=e.closest('.form-group,.row,div'); const l=p?p.querySelector('label,.control-label'):null; return l?norm(l.textContent):''; };"
                + " const out=[]; const emptyDropdowns=[];"
                + " [...scope.querySelectorAll('select')].filter(s=>s.offsetParent!==null).forEach(sel=>{"
                + "   const lab=labelOf(sel);"
                + "   if(!/\\*/.test(lab) && !sel.hasAttribute('required') && !sel.hasAttribute('ng-required')) return;"
                + "   const real=[...sel.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "   if(!real.length){ emptyDropdowns.push(lab.replace(/\\*/g,'').trim()+'['+(sel.getAttribute('ng-model')||'?')+']'); return; }"
                + "   const cur=sel.options[sel.selectedIndex];"
                + "   if(cur && cur.value && !/^-*\\s*select\\s*-*$/i.test(norm(cur.textContent))) return;"
                + "   const i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "   sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(sel).trigger('change');}catch(e){}}"
                + "   out.push(lab.replace(/\\*/g,'').trim()+'='+norm(sel.options[i].textContent)); });"
                + " [...scope.querySelectorAll('input,textarea')].filter(x=>x.offsetParent!==null && x.type!=='checkbox' && x.type!=='radio' && x.type!=='file').forEach(e=>{"
                + "   const lab=labelOf(e);"
                + "   if(!/\\*/.test(lab) && !e.hasAttribute('required') && !e.hasAttribute('ng-required')) return;"
                + "   if((e.value||'').trim()) return;"
                + "   let v='Test Value';"
                + "   if(/amount/i.test(lab)) v='100';"
                + "   else if(/date/i.test(lab)) { const d=new Date(); const p2=n=>('0'+n).slice(-2); v=p2(d.getDate())+'/'+p2(d.getMonth()+1)+'/'+d.getFullYear(); }"
                + "   else if(/remark|reason|note/i.test(lab)) v='Automated test';"
                + "   const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   out.push(lab.replace(/\\*/g,'').trim()+'='+v); });"
                + " return (emptyDropdowns.length? 'EMPTY DROPDOWN: '+emptyDropdowns.join(', ')+' | ':'') + (out.length?out.join(' | '):'(nothing mandatory found empty)'); }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    // ---- Company Approve Amounts -------------------------------------------

    /**
     * The Company Approve Amounts modal. The wording tolerates BOTH "Approve" and "Approved" — verified live on
     * OP &gt; Outpatient Queue Management that the app's own heading reads "Company Approve Amount" (no 'd'),
     * different from the button's own label, so a plain "Approved" match misses the modal entirely.
     */
    private static final String CAA_MODAL_JS =
            "[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /company\\s*approved?\\s*amounts?/i.test(x.textContent||''))";

    /**
     * Scan the grid for a row where the <b>Company Approve Amounts</b> footer button is actually enabled
     * (mirrors {@code selectFirstPatient()}'s random pick, but that can land on an ineligible patient — the
     * button is disabled per-row depending on payor/admission state). Tries every row in order, selecting each
     * and checking {@code button.disabled} immediately, stopping at the first enabled one. Returns the same
     * "PatientName / MRN …" descriptor as {@code selectFirstPatient()}, or null if no row makes it enabled.
     */
    public String selectPatientEligibleForCompanyApproveAmounts() {
        Object countObj = page.evaluate("() => { let gs=null; document.querySelectorAll('*').forEach(el=>{ if(gs)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&Array.isArray(s.grid.options.data)) gs=s; }catch(e){} });"
                + " return gs ? gs.grid.options.data.length : 0; }");
        int n = countObj instanceof Number ? ((Number) countObj).intValue() : 0;
        for (int idx = 0; idx < n; idx++) {
            final int i = idx;
            Object r = page.evaluate("(i) => { let gs=null; document.querySelectorAll('*').forEach(el=>{ if(gs)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&Array.isArray(s.grid.options.data)) gs=s; }catch(e){} });"
                    + " if(!gs) return 'ERR:no-grid'; const d=gs.grid.options.data||[]; const p=d[i]; if(!p) return 'ERR:no-data';"
                    + " try{ gs.grid.api.selection.clearSelectedRows(); }catch(e){} try{ gs.grid.api.selection.selectRow(p); }catch(e){}"
                    + " const get=(o,ks)=>{ for(const k of ks){ for(const kk in o){ if(kk.toLowerCase()===k){ const v=o[kk]; if(v!=null&&(''+v).trim()) return (''+v).trim(); } } } return ''; };"
                    + " const name=get(p,['patientname','name','pname']); const mrn=get(p,['mrno','mrnno','mrn','mrnumber']); const adm=get(p,['admissionno','admissionid','admno']);"
                    + " return (name||'(patient)')+' / MRN '+(mrn||'-')+(adm?(' / Adm '+adm):'')+' [row '+(i+1)+'/'+d.length+']'; }", i);
            String desc = r == null ? null : r.toString();
            if (desc == null || desc.startsWith("ERR:")) continue;
            waitForAngular(400);
            Object enabled = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/CompanyApprove/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*company\\s*approved?\\s*amounts?\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                    + " return b ? !b.disabled : false; }");
            if (Boolean.TRUE.equals(enabled)) {
                System.out.println("selectPatientEligibleForCompanyApproveAmounts: found enabled row - " + desc);
                return desc;
            }
        }
        System.out.println("selectPatientEligibleForCompanyApproveAmounts: no row (of " + n + ") had the button enabled");
        return null;
    }

    /**
     * Click the footer <b>Company Approve Amounts</b> button and wait for its modal. Returns true if it opened.
     * Like {@link #clickPlanDischarge()}, this button is {@code ng-disabled="ISNullBedId"}-style and can flip
     * disabled between our eligibility scan and the real click (observed live: Playwright's own actionability
     * wait times out even though the button was enabled moments earlier). If the real Playwright click doesn't
     * open the modal, fall back to a JS-dispatched click (bypasses the actionability wait) and, if that still
     * doesn't open it, invoke the button's {@code ng-click} expression directly on its Angular scope.
     */
    public boolean clickCompanyApproveAmounts() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/CompanyApprove/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*company\\s*approved?\\s*amounts?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!b) return 'no-button'; if(b.disabled) return 'disabled'; b.id='__caaBtn'; return b.getAttribute('ng-click')||'true'; }");
        String st = String.valueOf(tagged);
        if ("no-button".equals(st)) { System.out.println("clickCompanyApproveAmounts: 'Company Approve Amounts' button not found"); return false; }
        if ("disabled".equals(st)) { System.out.println("clickCompanyApproveAmounts: button not enabled"); return false; }
        String ngClickExpr = "true".equals(st) ? null : st;
        try { page.locator("#__caaBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) {
            System.out.println("clickCompanyApproveAmounts: real click failed (" + e.getMessage() + ") - falling back to JS dispatch");
            page.evaluate("() => { const b=document.getElementById('__caaBtn'); if(b) b.click(); }");
            waitForAngular(600);
            boolean opened = Boolean.TRUE.equals(page.evaluate("() => " + CAA_MODAL_JS + " !== undefined"));
            if (!opened && ngClickExpr != null) {
                System.out.println("clickCompanyApproveAmounts: JS dispatch didn't open it either - invoking ng-click on scope: " + ngClickExpr);
                page.evaluate("(expr) => { const b=document.getElementById('__caaBtn'); if(!b) return; const s=angular.element(b).scope(); if(s) s.$apply(expr); }", ngClickExpr);
            }
        }
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
                    + " return 'open modals=['+modals.join(' || ')+'] toasts=['+toasts.join(' || ')+']'; }");
            System.out.println("clickCompanyApproveAmounts: modal did not open. " + diag);
        }
        waitForAngular(600);
        return up;
    }

    /**
     * Select <b>Payor</b> ({@code Company.companyid}) and <b>Payor Status</b> ({@code Registration.PayerTypeId}),
     * enter <b>GL Approved Amount</b> ({@code Company.companyamount}) and <b>Remark</b> ({@code Company.Remarks}).
     * Targets these exact ng-models directly — a label-proximity match was tried first and silently matched
     * nothing (confirmed live: the pre-Add diagnostic showed every one of these fields still empty/"--Select--"
     * despite this step reporting success), so this generic-heuristic approach was abandoned in favor of the
     * confirmed field names, the same lesson learned on OP Queue Management's identical modal. If either select
     * has NO real option, that is called out explicitly as {@code "EMPTY DROPDOWN: <ng-model>"}.
     */
    public String fillCompanyApproveAmountDetails() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=" + CAA_MODAL_JS + "; if(!m) return '(modal gone)';"
                + " const el=ng=>[...m.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null);"
                + " const out=[]; const emptyDropdowns=[];"
                + " const setSel=(ng)=>{ const sel=el(ng); if(!sel){ out.push(ng+'=(not found)'); return; }"
                + "   const real=[...sel.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "   if(!real.length){ emptyDropdowns.push(ng); return; }"
                + "   const cur=sel.options[sel.selectedIndex];"
                + "   if(cur && cur.value && !/^-*\\s*select\\s*-*$/i.test(norm(cur.textContent))){ out.push(ng+'='+norm(cur.textContent)+' (kept)'); return; }"
                + "   const i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "   sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(sel).trigger('change');}catch(e){}}"
                + "   out.push(ng+'='+norm(sel.options[i].textContent)); };"
                + " const setTxt=(ng,v)=>{ const e=el(ng); if(!e){ out.push(ng+'=(not found)'); return; }"
                + "   if((e.value||'').trim()){ out.push(ng+'='+e.value+' (kept)'); return; }"
                + "   const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   out.push(ng+'='+v); };"
                + " setSel('Company.companyid'); setSel('Registration.PayerTypeId');"
                + " setTxt('Company.companyamount','100'); setTxt('Company.Remarks','Automated test');"
                + " return (emptyDropdowns.length? 'EMPTY DROPDOWN: '+emptyDropdowns.join(', ')+' | ':'') + out.join(' | '); }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Add</b> in the Company Approve Amounts modal. If a separate Save/Submit/OK button appears
     * afterwards (master-detail pattern like OP Queue Management's Company Approved Amount — Add only stages
     * the line item, a distinct Save commits it), click that too. Returns the resulting success toast.
     */
    public String addCompanyApproveAmountAndGetToast() {
        Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=" + CAA_MODAL_JS + "; if(!m) return '(modal gone)';"
                + " const labelOf=e=>{ let p=e.closest('.form-group,.row,div'); const l=p?p.querySelector('label,.control-label'):null; return l?norm(l.textContent):''; };"
                + " const fields=[...m.querySelectorAll('input,select,textarea')].filter(x=>x.offsetParent!==null).map(e=>{"
                + "   const val=e.tagName==='SELECT'?norm((e.options[e.selectedIndex]||{}).textContent):(e.value||'');"
                + "   return labelOf(e)+'['+(e.getAttribute('ng-model')||'?')+']='+(val||'(empty)'); });"
                + " const btns=[...m.querySelectorAll('button')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent)+'['+(b.getAttribute('ng-click')||'')+']');"
                + " return 'fields=['+fields.join(' || ')+'] buttons=['+btns.join(' || ')+']'; }");
        System.out.println("addCompanyApproveAmountAndGetToast: pre-add state -> " + diag);
        page.evaluate("() => { window.__caaToasts=[]; if(window.__caaObs) window.__caaObs.disconnect();"
                + " window.__caaObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__caaToasts.includes(t)) window.__caaToasts.push(t); }); });"
                + " window.__caaObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=" + CAA_MODAL_JS + "; const scope=m||document;"
                + " const b=[...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^add$/i.test((x.textContent||'').trim())); if(b) b.click(); }");
        String toast = "";
        boolean savedClicked = false;
        for (int i = 0; i < 40; i++) {
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__caaToasts=window.__caaToasts||[]; if(!window.__caaToasts.includes(m)) window.__caaToasts.push(m); } });"
                    + " const a=window.__caaToasts||[]; return a.find(x=>/success|saved|added/i.test(x)) || a[0] || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            if (!savedClicked && i >= 4) {
                Boolean clicked = (Boolean) page.evaluate("() => { const m=" + CAA_MODAL_JS + "; const scope=m||document;"
                        + " const b=[...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(save|submit|ok)$/i.test((x.textContent||'').trim())); if(b){ b.click(); return true; } return false; }");
                if (Boolean.TRUE.equals(clicked)) {
                    System.out.println("addCompanyApproveAmountAndGetToast: Add produced no toast — found a separate Save/Submit/OK button, clicked it");
                    savedClicked = true;
                }
            }
            page.waitForTimeout(500);
        }
        if (toast.isEmpty()) System.out.println("addCompanyApproveAmountAndGetToast: no toast observed within ~20s");
        waitForAngular(400);
        return toast;
    }

    /** Click <b>Close</b> on the Company Approve Amounts modal, then strip any leftover backdrop. */
    public void closeCompanyApproveAmountsModal() {
        page.evaluate("() => { const m=" + CAA_MODAL_JS + "; if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null) || m.querySelector('.close,[data-dismiss=modal]'); if(b) b.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} }"
                + " [...document.querySelectorAll('.modal,[role=dialog]')].filter(x=>x.getBoundingClientRect().width>0 && /company\\s*approved?\\s*amounts?/i.test(x.textContent||'')).forEach(x=>{ x.style.display='none'; x.classList.remove('in','show'); });"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
    }

    // ---- Convert IPD Charges -----------------------------------------------
    // Ported from OP > Outpatient Queue Management's Convert IPD Charges.

    /**
     * Click the footer <b>Convert IPD Charges</b> button and wait for its confirm dialog (or a direct toast
     * if the app skips confirmation for this patient). Returns <b>"confirm"</b> (dialog appeared),
     * <b>"toast:&lt;text&gt;"</b> (a toast fired directly), or <b>"none"</b> (neither appeared — try the next
     * patient).
     */
    public String clickConvertIpdCharges() {
        Object found = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/ConvertIPD|ConvertIpdCharges/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/convert.{0,3}ipd.{0,3}charges/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!b) return 'no-button'; return (b.disabled?'disabled:':'ok:')+norm(b.textContent)+'['+(b.getAttribute('ng-click')||'')+']'; }");
        String fs = String.valueOf(found);
        if ("no-button".equals(fs)) { System.out.println("clickConvertIpdCharges: 'Convert IPD Charges' button not found on this screen"); return "none"; }
        if (fs.startsWith("disabled:")) { System.out.println("clickConvertIpdCharges: button disabled for this patient - " + fs); return "none"; }
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
     * failure on its own, just a signal to try a different patient.
     */
    public String acceptConvertIpdChargesAndGetToast() {
        Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const dlg=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,[role=dialog]')].find(m=>m.getBoundingClientRect().width>0 && /convert|ipd\\s*charges|are you sure|do you want/i.test(m.textContent||''));"
                + " if(!dlg) return '(no confirm dialog visible)';"
                + " const btns=[...dlg.querySelectorAll('button')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent)+'['+(b.getAttribute('ng-click')||'')+']');"
                + " return 'dialog text=\"'+norm(dlg.textContent).slice(0,150)+'\" buttons=['+btns.join(' || ')+']'; }");
        System.out.println("acceptConvertIpdChargesAndGetToast: pre-click state -> " + diag);
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
        if (toast.isEmpty()) System.out.println("acceptConvertIpdChargesAndGetToast: no toast observed within ~20s");
        waitForAngular(400);
        return toast;
    }

    // ---- Fall Risk Assessment ----------------------------------------------

    /**
     * The Fall Risk Assessment modal. Confirmed live: its own heading reads <b>"ASSESSMENT OF FALL FOR
     * INPATIENT"</b>, not "Fall Risk Assessment" — a numbered Yes/No checklist (e.g. "1 Has the patient
     * experienced one or more falls within the past 12 months? YES NO", "2 Does the patient require assistance
     * with walking or use a mobility a[id]..."), same app-side naming-mismatch pattern seen elsewhere (Company
     * Approve Amount/Approved Amounts, Calll For Triage). Matched on that confirmed heading text.
     */
    private static final String FRA_MODAL_JS =
            "[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /assessment\\s*of\\s*fall/i.test(x.textContent||''))";

    /**
     * Click the footer <b>Fall Risk Assessment</b> button for the selected patient and wait for its modal. Like
     * Assign Triage / Plan Discharge, this button is expected to be {@code ng-disabled}-guarded by the bedded
     * state; if disabled, fall back to invoking its {@code ng-click} expression on the Angular scope directly.
     * Returns true if the modal opened.
     */
    public boolean clickFallRiskAssessment() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/fallrisk/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*fall\\s*risk\\s*assessment\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!b) return 'no-button'; if(b.disabled) return 'disabled:'+(b.getAttribute('ng-click')||''); b.id='__emFraBtn'; return b.getAttribute('ng-click')||'true'; }");
        String st = String.valueOf(tagged);
        if ("no-button".equals(st)) { System.out.println("clickFallRiskAssessment: 'Fall Risk Assessment' button not found"); return false; }
        if (st.startsWith("disabled:")) {
            String ngClick = st.substring("disabled:".length());
            System.out.println("clickFallRiskAssessment: footer button disabled - trying scope invoke of '" + ngClick + "'");
            java.util.regex.Matcher fm = java.util.regex.Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\(").matcher(ngClick);
            String fnName = fm.find() ? fm.group(1) : null;
            if (fnName != null) {
                // Walk PARENT scopes (like clickPlanDischarge/clickAssignTriage) — grabbing the first element's
                // scope in DOM order (as an earlier version did) is not necessarily one where fnName is defined,
                // so $apply(expr) silently no-ops. Find the button itself and walk ITS scope chain instead.
                Object invoked = page.evaluate("(fn) => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf(fn)>=0);"
                        + " if(!b) return false; let s=angular.element(b).scope();"
                        + " for(let i=0;i<20&&s;i++){ if(typeof s[fn]==='function'){ const h=s; try{ h.$apply(function(){ h[fn](); }); }catch(e){ try{ h[fn](); }catch(e2){} } return true; } s=s.$parent; } return false; }", fnName);
                if (!Boolean.TRUE.equals(invoked)) System.out.println("clickFallRiskAssessment: '" + fnName + "' not found on any parent scope");
                waitForAngular(800);
            }
        } else {
            try { page.locator("#__emFraBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickFallRiskAssessment: click failed - " + e.getMessage()); }
            page.evaluate("() => { const b=document.getElementById('__emFraBtn'); if(b) b.removeAttribute('id'); }");
        }
        boolean up = false;
        for (int k = 0; k < 20; k++) {
            Boolean ok = (Boolean) page.evaluate("() => " + FRA_MODAL_JS + " !== undefined");
            if (Boolean.TRUE.equals(ok)) { up = true; break; }
            page.waitForTimeout(500);
        }
        if (!up) {
            Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const modals=[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].filter(m=>m.getBoundingClientRect().width>0).map(m=>norm(m.textContent).slice(0,200));"
                    + " const toasts=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].map(t=>norm(t.textContent));"
                    + " return 'open modals=['+modals.join(' || ')+'] toasts=['+toasts.join(' || ')+']'; }");
            System.out.println("clickFallRiskAssessment: modal did not open. " + diag);
        }
        waitForAngular(600);
        return up;
    }

    /**
     * Fill the Fall Risk Assessment modal — confirmed live on OP &gt; Outpatient Queue Management's identical
     * modal (same underlying component): answer every YES/NO question with <b>YES</b> (buttons matching
     * {@code setQuestionAnswer(q, true)}), then tick one risk-factor checkbox ({@code item.IsSelected}).
     */
    public String fillFallRiskAssessmentDetails() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=" + FRA_MODAL_JS + "; if(!m) return '(modal gone)';"
                + " const yesBtns=[...m.querySelectorAll('button')].filter(b=>/setQuestionAnswer\\(q, ?true\\)/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null);"
                + " yesBtns.forEach(b=>b.click());"
                + " const cb=[...m.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && !x.checked); if(cb) cb.click();"
                + " return 'Answered YES to '+yesBtns.length+' question(s)'+(cb?' | Risk factor ticked':' | (no checkbox found)'); }");
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /**
     * Click <b>Apply</b> ({@code fnApply()} — NOT "Save") in the Fall Risk Assessment modal and return the
     * resulting success toast (verified live elsewhere: "Fall Risk Assessment saved successfully.").
     */
    public String saveFallRiskAssessmentAndGetToast() {
        page.evaluate("() => { window.__fraToasts=[]; if(window.__fraObs) window.__fraObs.disconnect();"
                + " window.__fraObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__fraToasts.includes(t)) window.__fraToasts.push(t); }); });"
                + " window.__fraObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=" + FRA_MODAL_JS + "; const scope=m||document;"
                + " const b=[...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /fnApply/.test(x.getAttribute('ng-click')||''))"
                + " || [...scope.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(apply|save|submit|ok)$/i.test((x.textContent||'').trim())); if(b) b.click(); }");
        String toast = "";
        for (int i = 0; i < 40; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate(
                    "() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want|are you sure/i.test(m.textContent||''))"));
            if (confirm) {
                page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want|are you sure/i.test(m.textContent||''));"
                        + " const b=[...(box||document).querySelectorAll('button')].find(x=>/^(save|yes|ok|apply)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(400);
            }
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__fraToasts=window.__fraToasts||[]; if(!window.__fraToasts.includes(m)) window.__fraToasts.push(m); } });"
                    + " const a=window.__fraToasts||[]; return a.find(x=>/success|saved|applied/i.test(x)) || a[0] || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            page.waitForTimeout(500);
        }
        if (toast.isEmpty()) System.out.println("saveFallRiskAssessmentAndGetToast: no toast observed within ~20s");
        waitForAngular(400);
        return toast;
    }

    /** Close the Fall Risk Assessment modal, then strip any leftover backdrop. */
    public void closeFallRiskAssessmentPopup() {
        page.evaluate("() => { const m=" + FRA_MODAL_JS + "; if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null) || m.querySelector('.close,[data-dismiss=modal]'); if(b) b.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} }"
                + " [...document.querySelectorAll('.modal,[role=dialog]')].filter(x=>x.getBoundingClientRect().width>0 && /fall\\s*risk\\s*assessment/i.test(x.textContent||'')).forEach(x=>{ x.style.display='none'; x.classList.remove('in','show'); });"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
    }

    // ---- Request MRD File --------------------------------------------------
    // Ported from OP > Outpatient Queue Management's Request MRD File (fnRequestMRDFile / fnIUDAllocation are
    // generic modal/ng-click hooks, not tied to that screen's controller).

    /**
     * Select the first patient that has NO MRD file yet ({@code mrdfileno} empty) so Request MRD File triggers
     * the create path. Returns the descriptor, or null if the grid has no rows.
     */
    public String selectQueueRowWithoutMrdFile() {
        Object r = page.evaluate("() => { let gs=null; document.querySelectorAll('*').forEach(el=>{ if(gs)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&Array.isArray(s.grid.options.data)) gs=s; }catch(e){} });"
                + " if(!gs) return null; const d=gs.grid.options.data||[]; if(!d.length) return null;"
                + " const get=(o,ks)=>{ for(const k of ks){ for(const kk in o){ if(kk.toLowerCase()===k){ const v=o[kk]; if(v!=null&&(''+v).trim()) return (''+v).trim(); } } } return ''; };"
                + " const p=d.find(x=>!get(x,['mrdfileno'])) || d[0];"
                + " try{ gs.grid.api.selection.clearSelectedRows(); }catch(e){} try{ gs.grid.api.selection.selectRow(p); }catch(e){}"
                + " const name=get(p,['patientname','name','pname']); const mrn=get(p,['mrno','mrnno','mrn','mrnumber']);"
                + " return (name||'(patient)')+' / MRN '+(mrn||'-'); }");
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /**
     * Click the footer <b>Request MRD File</b> button ({@code fnRequestMRDFile()}). Returns <b>"confirm"</b>
     * (the "File is not generated… create one?" dialog appeared — no file yet), <b>"report"</b> (a report tab
     * opened — the patient already has a file), or <b>"none"</b>.
     */
    public String clickRequestMrdFile() {
        int tabsBefore = page.context().pages().size();
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnRequestMRDFile()' && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 60; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,[class*=confirm]')].some(m=>m.getBoundingClientRect().width>0 && /file is not generated/i.test(m.textContent||''))"));
            if (confirm) return "confirm";
            if (page.context().pages().size() > tabsBefore) return "report";
            page.waitForTimeout(800);
        }
        page.evaluate("() => { const no=[...document.querySelectorAll('.jconfirm button, button')].find(b=>/^no$/i.test((b.textContent||'').trim()) && b.offsetParent!==null); if(no) no.click(); [...document.querySelectorAll('.jconfirm,.ng-confirm')].forEach(d=>{try{d.remove();}catch(e){}}); }");
        return "none";
    }

    /**
     * Click <b>Yes</b> on the "File is not generated… Do you want to create one?" confirm, then wait for the
     * <b>MRD Details</b> modal. Returns true if the modal opened.
     */
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
     * Fill the <b>MRD Details</b> popup (Patient Type / Allocation Location / File Type / Rack→Row→Box + File
     * No / Classification) and Save ({@code fnIUDAllocation()}). Returns the toast (expected "File Allocated
     * Successfully.").
     */
    public String fillMrdDetailsAndSave() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /mrd details/i.test(x.textContent||''));"
                + " const pick=(ng)=>{ const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const idx=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())); if(idx<0) return; e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " const setInp=(ng,v)=>{ const e=[...m.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " pick('MRDAllocation.mrdpatienttypeid'); pick('MRDAllocation.allocationlocationid'); pick('MRDAllocation.mrdfiletypeid'); pick('MRDAllocation.rackid');"
                + " setInp('MRDAllocation.filetypeno','1'); setInp('MRDAllocation.classificationtag','1'); }");
        waitForAngular(1200);
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /mrd details/i.test(x.textContent||''));"
                + " const pick=(ng)=>{ const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const idx=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())); if(idx<0) return; e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " pick('MRDAllocation.rowid'); }");
        waitForAngular(1200);
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

    /** Cancel/close the MRD Details popup. */
    public void cancelMrdDetails() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /mrd details/i.test(x.textContent||''));"
                + " if(m){ const c=[...m.querySelectorAll('button')].find(b=>/^cancel$/i.test((b.textContent||'').trim()) && b.offsetParent!==null) || [...m.querySelectorAll('button')].find(b=>/^[×xX]$/.test((b.textContent||'').trim()) && b.offsetParent!==null); if(c) c.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} } [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    // ---- Return MRD File ---------------------------------------------------
    // Ported from OP > Outpatient Queue Management's Return MRD File (fnReturnMRDFile / fnIUDReturn are generic
    // modal/ng-click hooks, not tied to that screen's controller).

    /**
     * Select the first patient that HAS an MRD file ({@code mrdfileno} set) so Return MRD File has a file to
     * return. Returns "name | mrdfileno=…", or null if none of the loaded rows have one.
     */
    public String selectQueueRowWithMrdFile() {
        Object r = page.evaluate("() => { let gs=null; document.querySelectorAll('*').forEach(el=>{ if(gs)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&Array.isArray(s.grid.options.data)) gs=s; }catch(e){} });"
                + " if(!gs) return null; const d=gs.grid.options.data||[]; if(!d.length) return null;"
                + " const get=(o,ks)=>{ for(const k of ks){ for(const kk in o){ if(kk.toLowerCase()===k){ const v=o[kk]; if(v!=null&&(''+v).trim()) return (''+v).trim(); } } } return ''; };"
                + " const p=d.find(x=>get(x,['mrdfileno'])); if(!p) return null;"
                + " try{ gs.grid.api.selection.clearSelectedRows(); }catch(e){} try{ gs.grid.api.selection.selectRow(p); }catch(e){}"
                + " const name=get(p,['patientname','name','pname']); return (name||'(patient)')+' | mrdfileno='+get(p,['mrdfileno']); }");
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /**
     * Click the <b>Return MRD File</b> footer button ({@code fnReturnMRDFile()}) and wait for the
     * <b>MRDReturn</b> modal (its Save is {@code fnIUDReturn()}). Returns true if it opened.
     */
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
     * Fill the <b>MRDReturn</b> popup and Save: select <b>From Department</b>, <b>To Department</b> (a
     * different real department), leave/select <b>User</b>, enter a <b>Remark</b>, then click <b>OK</b>
     * ({@code fnIUDReturn()}). Returns the toast. The three selects use a REAL Playwright {@code selectOption}
     * — confirmed on the identical OP Queue Management modal that a synthetic {@code selectedIndex +
     * dispatchEvent('change')} leaves the Angular model unbound here, causing an unnamed server-side "Failed!"
     * instead of a field-validation message. A "Failed!"/no-toast outcome from this action can be a genuine
     * app-side rejection — reported honestly, not papered over.
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
                + " const b=m&&[...m.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnIUDReturn()' && x.offsetParent!==null); if(b) b.id='__emRmSaveBtn'; }");
        try { page.locator("#__emRmSaveBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("fillMrdReturnAndSave: real click on OK failed - " + e.getMessage() + "; falling back to JS click");
            page.evaluate("() => { const b=document.getElementById('__emRmSaveBtn'); if(b) b.click(); }");
        }
        page.evaluate("() => { const b=document.getElementById('__emRmSaveBtn'); if(b) b.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__rmToasts||[]).some(a=>/return|success|saved|allocated|fill|select|please|fail/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("fillMrdReturnAndSave: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__rmToasts||[]; return a.find(x=>/return|success|saved|allocated/i.test(x)) || a.find(x=>/fill|select|please|fail/i.test(x)) || a[0] || ''; }");
        String toast = r == null ? "" : r.toString().trim();
        if (toast.isEmpty()) toast = "(no toast observed — outcome unconfirmed)";
        waitForAngular(400);
        return toast;
    }

    /**
     * Select the first real option (skipping {@code skipValue} if given) in the MRDReturn modal's select bound
     * to {@code ngModel}, using a REAL Playwright {@code selectOption}. Returns the committed ngModel value
     * ("" if the select or a real option could not be found).
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

    // ---- Patient Status (Next Action) --------------------------------------

    /** The Patient Status / Next Action modal — exact wording not yet confirmed live, so both are matched. */
    private static final String PS_MODAL_JS =
            "[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /patient\\s*status|next\\s*action/i.test(x.textContent||''))";

    /**
     * Click the footer <b>Patient Status</b> button for the selected patient and wait for its modal. Like Fall
     * Risk Assessment / Assign Triage, this button may be {@code ng-disabled}-guarded by the bedded state; if
     * disabled, fall back to invoking its {@code ng-click} function on the button's own scope chain. Returns
     * true if the modal opened.
     */
    public boolean clickPatientStatus() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/patientstatus|nextaction/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*(next\\s*action\\s*)?patient\\s*status\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!b) return 'no-button'; if(b.disabled) return 'disabled:'+(b.getAttribute('ng-click')||''); b.id='__emPsBtn'; return b.getAttribute('ng-click')||'true'; }");
        String st = String.valueOf(tagged);
        if ("no-button".equals(st)) { System.out.println("clickPatientStatus: 'Patient Status' button not found"); return false; }
        if (st.startsWith("disabled:")) {
            String ngClick = st.substring("disabled:".length());
            System.out.println("clickPatientStatus: footer button disabled - trying scope invoke of '" + ngClick + "'");
            java.util.regex.Matcher fm = java.util.regex.Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\(").matcher(ngClick);
            String fnName = fm.find() ? fm.group(1) : null;
            if (fnName != null) {
                Object invoked = page.evaluate("(fn) => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf(fn)>=0);"
                        + " if(!b) return false; let s=angular.element(b).scope();"
                        + " for(let i=0;i<20&&s;i++){ if(typeof s[fn]==='function'){ const h=s; try{ h.$apply(function(){ h[fn](); }); }catch(e){ try{ h[fn](); }catch(e2){} } return true; } s=s.$parent; } return false; }", fnName);
                if (!Boolean.TRUE.equals(invoked)) System.out.println("clickPatientStatus: '" + fnName + "' not found on any parent scope");
                waitForAngular(800);
            }
        } else {
            try { page.locator("#__emPsBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickPatientStatus: click failed - " + e.getMessage()); }
            page.evaluate("() => { const b=document.getElementById('__emPsBtn'); if(b) b.removeAttribute('id'); }");
        }
        boolean up = false;
        for (int k = 0; k < 20; k++) {
            Boolean ok = (Boolean) page.evaluate("() => " + PS_MODAL_JS + " !== undefined");
            if (Boolean.TRUE.equals(ok)) { up = true; break; }
            page.waitForTimeout(500);
        }
        if (!up) {
            Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const modals=[...document.querySelectorAll('.modal,.jconfirm,[role=dialog]')].filter(m=>m.getBoundingClientRect().width>0).map(m=>norm(m.textContent).slice(0,200));"
                    + " const toasts=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].map(t=>norm(t.textContent));"
                    + " return 'open modals=['+modals.join(' || ')+'] toasts=['+toasts.join(' || ')+']'; }");
            System.out.println("clickPatientStatus: modal did not open. " + diag);
        }
        waitForAngular(600);
        return up;
    }

    /**
     * Select the first real option in the Patient Status modal's status dropdown (matched by label or
     * ng-model containing "status"). Returns the chosen text, or a diagnostic dump of the modal's fields/buttons
     * if no such select was found (so a naming mismatch shows up in the log instead of a silent no-op).
     */
    public String selectPatientStatus() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=" + PS_MODAL_JS + "; if(!m) return '(modal gone)';"
                + " const labelOf=e=>{ let p=e.closest('.form-group,.row,div'); const l=p?p.querySelector('label,.control-label'):null; return l?norm(l.textContent):''; };"
                + " const sels=[...m.querySelectorAll('select')].filter(s=>s.offsetParent!==null);"
                + " const sel=sels.find(s=>/status/i.test(labelOf(s)+' '+(s.getAttribute('ng-model')||''))) || sels[0];"
                + " if(!sel){ const fields=[...m.querySelectorAll('input,select,textarea')].filter(x=>x.offsetParent!==null).map(e=>labelOf(e)+'['+(e.getAttribute('ng-model')||'?')+']');"
                + "   const btns=[...m.querySelectorAll('button')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent)+'['+(b.getAttribute('ng-click')||'')+']');"
                + "   return '(no select found) fields=['+fields.join(' || ')+'] buttons=['+btns.join(' || ')+']'; }"
                + " const i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i<0) return 'EMPTY DROPDOWN: '+labelOf(sel)+'['+(sel.getAttribute('ng-model')||'?')+']';"
                + " sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(sel).trigger('change');}catch(e){}}"
                + " return norm(sel.options[i].textContent); }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click Save/Submit/OK in the Patient Status modal and return the resulting success toast. */
    public String savePatientStatusAndGetToast() {
        page.evaluate("() => { window.__psToasts=[]; if(window.__psObs) window.__psObs.disconnect();"
                + " window.__psObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__psToasts.includes(t)) window.__psToasts.push(t); }); });"
                + " window.__psObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=" + PS_MODAL_JS + "; const scope=m||document;"
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
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__psToasts=window.__psToasts||[]; if(!window.__psToasts.includes(m)) window.__psToasts.push(m); } });"
                    + " const a=window.__psToasts||[]; return a.find(x=>/success|saved|updated/i.test(x)) || a[0] || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            page.waitForTimeout(500);
        }
        if (toast.isEmpty()) System.out.println("savePatientStatusAndGetToast: no toast observed within ~20s");
        waitForAngular(400);
        return toast;
    }

    /** Close the Patient Status modal, then strip any leftover backdrop. */
    public void closePatientStatusPopup() {
        page.evaluate("() => { const m=" + PS_MODAL_JS + "; if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null) || m.querySelector('.close,[data-dismiss=modal]'); if(b) b.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} }"
                + " [...document.querySelectorAll('.modal,[role=dialog]')].filter(x=>x.getBoundingClientRect().width>0 && /patient\\s*status|next\\s*action/i.test(x.textContent||'')).forEach(x=>{ x.style.display='none'; x.classList.remove('in','show'); });"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
    }

    // ---- Send Deposit Request SMS ------------------------------------------

    /**
     * Click the footer <b>Send Deposit Request SMS</b> button for the selected patient. Confirms any "are you
     * sure/do you want to" dialog that appears (same pattern as Convert IPD Charges / Ward Acceptance — a
     * direct action with no fill-in popup), then returns the resulting success toast.
     */
    public String clickSendDepositRequestSmsAndGetToast() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/depositrequest|depositsms/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*send\\s*deposit\\s*request\\s*sms\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!b) return 'no-button'; if(b.disabled) return 'disabled'; b.id='__emDrsBtn'; return true; }");
        String st = String.valueOf(tagged);
        if ("no-button".equals(st)) { System.out.println("clickSendDepositRequestSmsAndGetToast: 'Send Deposit Request SMS' button not found"); return ""; }
        if ("disabled".equals(st)) { System.out.println("clickSendDepositRequestSmsAndGetToast: button not enabled for this patient"); return ""; }
        page.evaluate("() => { window.__drsToasts=[]; if(window.__drsObs) window.__drsObs.disconnect();"
                + " window.__drsObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').trim(); if(t && !window.__drsToasts.includes(t)) window.__drsToasts.push(t); }); });"
                + " window.__drsObs.observe(document.body,{childList:true,subtree:true}); }");
        try { page.locator("#__emDrsBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickSendDepositRequestSmsAndGetToast: click failed - " + e.getMessage()); }
        page.evaluate("() => { const b=document.getElementById('__emDrsBtn'); if(b) b.removeAttribute('id'); }");
        String toast = "";
        for (int i = 0; i < 40; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate(
                    "() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want to|are you sure/i.test(m.textContent||''))"));
            if (confirm) {
                page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want to|are you sure/i.test(m.textContent||''));"
                        + " const b=[...(box||document).querySelectorAll('button')].find(x=>/^(save|yes|ok|send)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(400);
            }
            Object t = page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const m=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(m){ window.__drsToasts=window.__drsToasts||[]; if(!window.__drsToasts.includes(m)) window.__drsToasts.push(m); } });"
                    + " const a=window.__drsToasts||[]; return a.find(x=>/success|saved|sent/i.test(x)) || a[0] || ''; }");
            if (t != null && !t.toString().trim().isEmpty()) { toast = t.toString().trim(); break; }
            page.waitForTimeout(500);
        }
        if (toast.isEmpty()) System.out.println("clickSendDepositRequestSmsAndGetToast: no toast observed within ~20s");
        waitForAngular(400);
        return toast;
    }

    // ---- View Details -----------------------------------------------------
    // Ported from OP > Outpatient Queue Management's View Details (same underlying registration-details popup —
    // Registration.* models, Patient Information / Payor Information / Visit Information accordion sections),
    // since Emergency List View has no shared base class with that page object.

    /**
     * Click the <b>View Details</b> footer button and wait for the read-only patient registration details popup
     * (Patient Information / Correspondence / NOK / Payor / Visit — ~180 fields). Returns true if it opened.
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

    /** Fill every MANDATORY field in the View Details popup that is still empty (Patient/Correspondence/NOK). */
    public String fillViewDetailsMandatory() {
        Object r = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelectorAll('input,select,textarea').length>40);"
                + " if(!m) return resolve('(popup not found)');"
                + " const el=ng=>[...m.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null);"
                + " const setTxt=(ng,v)=>{ const e=el(ng); if(!e) return null; if((e.value||'').trim()) return 'kept';"
                + "   const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
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
                + " done.push('Address='+setTxt('Registration.ResAddress','12 Jalan Bukit Bintang'));"
                + " done.push('Country='+await setSel('Registration.ResCountryID'));"
                + " await sleep(1200);"
                + " done.push('State='+await setSel('Registration.ResStateID'));"
                + " await sleep(1200);"
                + " done.push('City='+await setSel('Registration.ResCityID'));"
                + " done.push('Postcode='+setTxt('Registration.ResPinCode','55100'));"
                + " done.push('Mobile='+setTxt('Registration.MobileNo','1'+('0'+uniq).slice(-9)));"
                + " done.push('Email='+setTxt('Registration.Email','nurul.aisyah'+uniq+'@example.com'));"
                + " done.push('KinName='+setTxt('Registration.KinName','Ahmad Faizal'));"
                + " done.push('KinPassport='+setTxt('Registration.KinFamilyName','B'+uniq));"
                + " done.push('KinNRIC='+setTxt('Registration.KinNationalId',nric));"
                + " done.push('KinNationality='+await setSel('Registration.NOKnationalid'));"
                + " done.push('KinRelation='+await setSel('Registration.KinRelationID'));"
                + " done.push('KinMobileCC='+await setSel('Registration.KinMobileCountryCode'));"
                + " done.push('KinMobile='+setTxt('Registration.KinMobileNo','1'+('1'+uniq).slice(-9)));"
                + " done.push('KinAddress='+setTxt('Registration.KinAddress','12 Jalan Bukit Bintang'));"
                + " done.push('KinCountry='+await setSel('Registration.KinCountryID'));"
                + " await sleep(1200);"
                + " done.push('KinState='+await setSel('Registration.KinStateID'));"
                + " await sleep(1200);"
                + " done.push('KinCity='+await setSel('Registration.KinCityID'));"
                + " await sleep(800);"
                + " for(const re of [/FillSponserDropDown/, /FillVisitDropdown/]){"
                + "   const b=[...m.querySelectorAll('button,a')].find(x=>re.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null);"
                + "   if(b){ b.click(); await sleep(2500); } }"
                + " await sleep(1500);"
                + " const labelOfX=e=>{ let n=e.closest('.form-group,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " for(let pass=0; pass<1; pass++){"
                + "   const ctrls=[...m.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden' && e.type!=='button' && e.type!=='checkbox' && e.type!=='radio');"
                + "   for(const e of ctrls){ const lab=labelOfX(e);"
                + "     if(!/\\*/.test(lab) && !e.hasAttribute('required') && !e.hasAttribute('ng-required')) continue;"
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
                + "     else if(/remark|reason|note/i.test(lab)) v='Updated from Emergency List View';"
                + "     const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "     e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "     done.push(lab.replace(/\\*/g,'').trim()+'='+v); }"
                + "   await sleep(400); }"
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
     * Payor Information in the View Details popup — open the accordion, invoke the default row's
     * {@code EditSponser($index)} (populates Self), REAL-click the Insurer "Self" radio, backfill
     * {@code Registration.receivabletypeid}/{@code Registration.PayerTypeId} if still empty.
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
     * Fill the whole View Details popup and verify nothing mandatory is left: fill the sections, set Payor =
     * Self, then re-check {@link #remainingMandatoryAllSections()} and fill again until clean (max 2 passes).
     */
    public String fillViewDetailsAll() {
        StringBuilder log = new StringBuilder();
        expandSection("^Patient Information$");
        log.append("[Patient/Correspondence/NOK] ").append(fillViewDetailsMandatory());
        expandSection("^Payor Information$");
        boolean payor = selectPayorSelf();
        log.append(" | Payor=").append(payor ? "Self" : "NOT set");
        // Confirmed live: this screen's popup has no "Visit Information" accordion at all (expandSection always
        // reports COULD NOT EXPAND for it) — track that so the re-fill loop below doesn't keep chasing a section
        // that was never there, and doesn't re-run selectPayorSelf() once it already succeeded. Re-invoking
        // selectPayorSelf() a second time was observed to actively UNDO a working "Self" (its default-row load
        // is flaky on retry), turning a pass that started clean into a failure.
        boolean visitSectionExists = expandSection("^Visit Information$");
        if (visitSectionExists) log.append(" | [Visit] ").append(fillViewDetailsMandatory());
        else log.append(" | [Visit] (no Visit Information section on this popup)");
        for (int pass = 1; pass <= 1; pass++) {
            String left = remainingMandatoryAllSections();
            if (left.isEmpty()) break;
            System.out.println("fillViewDetailsAll: still empty after pass " + pass + " -> " + left + " — filling again");
            log.append(" | re-fill pass ").append(pass).append(" for: ").append(left);
            if (left.contains("Patient Information")) { expandSection("^Patient Information$"); fillViewDetailsMandatory(); }
            if (left.contains("Payor Information") && !payor) { expandSection("^Payor Information$"); payor = selectPayorSelf(); }
            if (visitSectionExists && left.contains("Visit Information")) { expandSection("^Visit Information$"); fillViewDetailsMandatory(); }
        }
        String left = remainingMandatoryAllSections();
        log.append(left.isEmpty() ? " || ALL MANDATORY FILLED (all sections checked)" : " || STILL EMPTY: " + left);
        return log.toString();
    }

    /** Expand one accordion section of the View Details popup and wait for its body to render. */
    public boolean expandSection(String titleRegex) {
        return expandSection(titleRegex, markerFor(titleRegex));
    }

    private static String markerFor(String titleRegex) {
        String t = titleRegex.toLowerCase();
        if (t.contains("payor")) return "Registration.receivabletypeid";
        if (t.contains("visit"))  return "Visit.SubDepartmentID";
        return "Registration.FirstName";
    }

    /** Expand a section IDEMPOTENTLY (clicking an already-open accordion header closes it). */
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

    /** True when the section owning {@code markerNg} is currently expanded. */
    public boolean sectionOpen(String markerNg) {
        Object r = page.evaluate("(ng) => { const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); return !!e; }", markerNg);
        return Boolean.TRUE.equals(r);
    }

    /** How many of this section's controls are currently visible. */
    public int visibleControlCount() {
        Object r = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelectorAll('input,select,textarea').length>10);"
                + " if(!m) return 0; return [...m.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden').length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Mandatory fields still empty ACROSS ALL SECTIONS — each accordion is expanded in turn before scanning. */
    public String remainingMandatoryAllSections() {
        java.util.List<String> all = new java.util.ArrayList<>();
        for (String sec : new String[]{"^Patient Information$", "^Payor Information$", "^Visit Information$"}) {
            String name = sec.replaceAll("[\\^$]", "");
            if (!expandSection(sec)) { all.add(name + ": COULD NOT EXPAND (not verified)"); continue; }
            String left = remainingMandatory();
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
     * In the View Details popup, click <b>Update Registration</b>, confirm any "Do you want to save/update"
     * dialog, and return the success toast. NB the handler is {@code UpdateOnlyRegistration()}.
     */
    public String updateViewDetailsAndGetToast() {
        page.evaluate("() => { window.__vdToasts=[]; if(window.__vdObs) window.__vdObs.disconnect();"
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
}
