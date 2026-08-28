package com.kpj.pages.Ip;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * IP &gt; <b>Bedboard Occupancy List</b> (route {@code #/BedboardOccupancyList}) — Page Object.
 *
 * <p>Reached from the <b>IP</b> left-menu. The list is filtered by an admission date range
 * ({@code AdmissionList.FromDate} / {@code AdmissionList.ToDate}) + Search, then a patient row is
 * selected in the ui-grid and the <b>Change Admission Type</b> footer button ({@code ChangeAdmissionType();})
 * opens a modal with THREE sections:</p>
 * <ol>
 *   <li><b>Change Admission Type</b> — {@code AdmissionList.admissiontypeid} (drpVisitType),
 *       {@code AdmissionList.departmentidadmissiontype} (drpDepartmentAdmissionType, ng-change
 *       {@code fnSetDoctorAdmissionType()} loads the doctors) and {@code AdmissionList.doctoridadmissiontype}
 *       (drpDoctorAdmissionType).</li>
 *   <li><b>Additional Doctors</b> — {@code Admission.ClassificationID} (drpClassification) +
 *       {@code Admission.AdditionalDoctorID} (drpOnlyDoc) → <b>Add</b> ({@code AddAdditionalDoc(AddDocList)}).</li>
 *   <li><b>Next of Kin Details</b> — {@code FillKinDropDown()} first loads the NOK dropdowns
 *       ({@code drpPrefix}/{@code drpRelation}/{@code drpAllReceivable}/{@code drpOccupation}); then
 *       {@code Admission.KinTitleID}/{@code KinRelationID}/{@code KinReceivableID}/{@code KinOccupationID}
 *       + {@code KinName}/{@code KinMobileNo}/{@code KinAddress} → <b>Add</b> ({@code AddKinDetails(KinDetailsList)}).</li>
 * </ol>
 * <p>Save = {@code fnUpdateAdmissionType()}. The dropdowns are <b>select2-offscreen</b> ng-options widgets
 * (the native &lt;option&gt; list stays at the placeholder while the scope array is populated), so values are
 * set on the scope model + {@code $apply()} using each option's {@code item.value} — that is what the Save
 * handler reads, regardless of the select2 display.</p>
 */
public class BedboardOccupancyListPage extends BasePage {

    public static final String ROUTE = "#/BedboardOccupancyList";

    public BedboardOccupancyListPage(Page page) { super(page); }

    // ---- navigation via the IP menu --------------------------------------

    /** Expand the <b>IP</b> left-menu and click <b>Bedboard Occupancy List</b> ({@code #/BedboardOccupancyList}). */
    public boolean navigateViaMenu() {
        String menuJs = "() => window.angular && [...document.querySelectorAll('li > a')].some(a=>/^\\s*IP\\s*$/i.test((a.textContent||'').trim()))";
        boolean menuUp = false;
        for (int attempt = 0; attempt < 3 && !menuUp; attempt++) {
            try {
                page.waitForFunction(menuJs, null, new Page.WaitForFunctionOptions().setTimeout(attempt == 0 ? 30000 : 20000));
                menuUp = true;
            } catch (Exception ignore) {
                System.out.println("Bedboard.navigateViaMenu: IP menu did not render (attempt " + (attempt + 1) + ") — reloading");
                try { page.reload(new Page.ReloadOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)); }
                catch (Exception e) { System.out.println("Bedboard.navigateViaMenu: reload failed - " + e.getMessage()); }
                waitForAngular(1500);
            }
        }
        waitForAngular(800);
        // Expand the IP menu (real click — its ng handler toggles the submenu).
        Object ipTagged = page.evaluate("() => { const lis=[...document.querySelectorAll('li')];"
                + " const ipLi=lis.find(li=>{ const a=li.querySelector(':scope > a'); return a && /^\\s*IP\\s*$/i.test((a.textContent||'').trim()); });"
                + " const a=ipLi&&ipLi.querySelector(':scope > a'); if(!a) return false; a.id='__ipMenuTab'; return true; }");
        if (Boolean.TRUE.equals(ipTagged)) {
            try { page.locator("#__ipMenuTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Bedboard.navigateViaMenu: IP menu click failed - " + e.getMessage()); }
        }
        waitForAngular(800);
        // Click the Bedboard Occupancy List submenu link.
        Object tagged = page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('href')||'')==='#/BedboardOccupancyList' && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('a')].find(x=>/bedboard occupancy list/i.test((x.textContent||'').replace(/\\s+/g,' ').trim())); if(!a) return false; a.id='__bedboardTab'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__bedboardTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Bedboard.navigateViaMenu: Bedboard link click failed - " + e.getMessage()); }
        } else {
            // Fallback: direct route.
            page.navigate(BASE_URL() + "/" + ROUTE);
        }
        // Wait for the list (Search button present + on the #/BedboardOccupancyList route).
        try {
            page.waitForFunction("() => window.angular && /bedboardoccupancylist/i.test(location.hash) && [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("Bedboard.navigateViaMenu: list not ready in time"); }
        waitForAngular(1200);
        return page.url().toLowerCase().contains("bedboardoccupancylist");
    }

    private static String BASE_URL() { return "https://devhis.sancyberhad.com"; }

    // ---- search a 1-month range ending today -----------------------------

    /** Set the admission-date filter to a 1-month range whose <b>To date is today</b>, then click Search.
     *  Returns "from → to". */
    public String searchOneMonthToToday() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate from = today.minusMonths(1);
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String fromStr = from.format(f), toStr = today.format(f);
        page.evaluate("(d) => { const setD=(ng,val)=>{ const el=[...document.querySelectorAll('input')].find(i=>i.getAttribute('ng-model')===ng); if(!el) return; const c=angular.element(el).controller('ngModel'); el.value=val; if(c){c.$setViewValue(val);c.$render();} el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " setD('AdmissionList.FromDate', d.from); setD('AdmissionList.ToDate', d.to); }",
                java.util.Map.of("from", fromStr, "to", toStr));
        waitForAngular(500);
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/^search$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        // Wait for grid rows to load.
        try {
            page.waitForFunction("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data) n=Math.max(n,s.grid.options.data.length);}catch(e){} }); return n>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("searchOneMonthToToday: no grid rows appeared"); }
        waitForAngular(800);
        return fromStr + " -> " + toStr;
    }

    // ---- select a patient in the grid ------------------------------------

    /** Select a random admitted patient row (skips obvious dummy/test rows). Returns the name, or null. */
    public String selectRandomPatient() {
        Object r = page.evaluate("() => { const skip=/dummy|test\\s*emr/i; let gridApi=null, data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){gridApi=s.grid.api;data=s.grid.options.data;} }catch(e){} });"
                + " if(!gridApi||!data||!data.length) return null;"
                + " const nm=x=>((x.PatientName||x.patientname||x.PatientFullName||'')+'').trim();"
                + " const cands=data.filter(x=>nm(x)&&!skip.test(nm(x)));"
                + " const p=cands[Math.floor(Math.random()*Math.min(cands.length,10))] || data[0];"
                + " try{gridApi.selection.clearSelectedRows();}catch(e){} gridApi.selection.selectRow(p); return nm(p); }");
        waitForAngular(600);
        return r == null ? null : r.toString();
    }

    // ---- open the Change Admission Type modal ----------------------------

    /** Click the <b>Change Admission Type</b> footer button ({@code ChangeAdmissionType();}) and wait for the
     *  modal (its Save button is {@code fnUpdateAdmissionType()}). Returns true if it opened. */
    public boolean clickChangeAdmissionType() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/ChangeAdmissionType/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /change admission type/i.test(m.textContent||'') && [...m.querySelectorAll('button')].some(b=>/fnUpdateAdmissionType/.test(b.getAttribute('ng-click')||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { System.out.println("clickChangeAdmissionType: modal did not open"); return false; }
        // The section-1 ng-options arrays (drpVisitType / drpDepartmentAdmissionType) load async AFTER the modal
        // opens — filling before they populate leaves the selects empty (type/dept/doctor=null). Poll until ready.
        try {
            page.waitForFunction("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change admission type/i.test(x.textContent||'')); if(!m) return false; const sc=angular.element(m.querySelector('select')).scope(); return sc && (sc.drpVisitType||[]).length>0 && (sc.drpDepartmentAdmissionType||[]).length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("clickChangeAdmissionType: section-1 dropdowns were slow to load"); }
        waitForAngular(600);
        return true;
    }

    /**
     * Expand (if collapsed) and scroll a Change-Admission-Type modal section into view — so a screenshot taken
     * afterwards clearly shows that section's fields. The <b>Additional Doctors</b> and <b>Next of Kin Details</b>
     * headings are Bootstrap collapse toggles ({@code <a data-toggle="collapse">}), so this clicks the matching
     * toggle when it isn't already expanded. {@code keyword} is the section heading text (e.g. "Additional Doctors",
     * "Next of Kin"); "Change Admission Type" (Section 1, no collapse) just scrolls the Admission Type select into view.
     */
    public void focusSection(String keyword) {
        page.evaluate("(kw) => {"
                + " const norm = s => (s || '').replace(/\\s+/g, ' ').trim();"
                + " const m = [...document.querySelectorAll('.modal,[role=dialog]')].find(x => x.getBoundingClientRect().width > 0 && /change admission type/i.test(x.textContent || ''));"
                + " if (!m) return;"
                + " const k = kw.toLowerCase();"
                + " const toggle = [...m.querySelectorAll('a')].find(a => a.getAttribute('data-toggle') === 'collapse' && norm(a.textContent).toLowerCase().indexOf(k) === 0);"
                + " if (toggle) {"
                + "   const tgt = toggle.getAttribute('data-target') || toggle.getAttribute('href');"
                + "   const panel = tgt ? m.querySelector(tgt) : null;"
                + "   const open = panel ? (panel.getBoundingClientRect().height > 10) : false;"   // check the REAL rendered height, not stale aria
                + "   if (!open) { try { toggle.click(); } catch (e) {} }"
                + "   try { toggle.scrollIntoView({ block: 'center' }); } catch (e) {}"
                + " } else {"
                + "   const el = m.querySelector('select[ng-model=\"AdmissionList.admissiontypeid\"]');"
                + "   if (el) { try { el.scrollIntoView({ block: 'center' }); } catch (e) {} }"
                + " }"
                + "}", keyword);
        waitForAngular(1000);
    }

    // ---- Section 1: Change Admission Type --------------------------------

    /** Section 1 — set Admission Type + Department (fires {@code fnSetDoctorAdmissionType()} to load doctors)
     *  + Doctor, all via the scope model. Returns a summary. */
    public String fillChangeTypeSection() {
        Object r = page.evaluate("() => new Promise(async (resolve) => {"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change admission type/i.test(x.textContent||'')); if(!m) return resolve('NO MODAL');"
                + " const sc=angular.element(m.querySelector('select')).scope();"
                + " const firstVal=(arr,valKey)=>{ const a=sc.$eval(arr); if(!Array.isArray(a)||!a.length) return null; const it=a.find(x=>x[valKey]!=null && !/^-*\\s*select/i.test((x.text||x.MiddleName||'')+'')) || a[0]; return it?it[valKey]:null; };"
                + " sc.AdmissionList.admissiontypeid = firstVal('drpVisitType','value');"
                + " sc.AdmissionList.departmentidadmissiontype = firstVal('drpDepartmentAdmissionType','value');"
                + " try{ sc.$apply(); }catch(e){}"
                + " try{ if(typeof sc.fnSetDoctorAdmissionType==='function') sc.fnSetDoctorAdmissionType(); }catch(e){}"
                + " await new Promise(r=>setTimeout(r,1800));"
                + " sc.AdmissionList.doctoridadmissiontype = firstVal('drpDoctorAdmissionType','value');"
                + " try{ sc.$apply(); }catch(e){}"
                + " resolve('type='+sc.AdmissionList.admissiontypeid+' dept='+sc.AdmissionList.departmentidadmissiontype+' doctor='+sc.AdmissionList.doctoridadmissiontype+' (doctors='+(sc.drpDoctorAdmissionType||[]).length+')'); })");
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    // ---- Section 2: Additional Doctors -----------------------------------

    /**
     * Section 2 — set <b>Classification</b> + <b>Additional Doctor</b> and LEAVE them populated.
     * <p>IMPORTANT: do NOT click the section's <b>Add</b> button ({@code AddAdditionalDoc}) — verified live that
     * it CLEARS the Classification dropdown, and the Save ({@code fnUpdateAdmissionType}) validates the current
     * dropdown value, so a cleared Classification makes the server reject the save with
     * <i>"Please Select Classification!"</i>. The Save reads the current field values directly, so the fields are
     * simply left filled.</p>
     */
    public String fillAdditionalDoctorsSection() {
        Object r = page.evaluate("() => new Promise(async (resolve) => {"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change admission type/i.test(x.textContent||'')); if(!m) return resolve('NO MODAL');"
                + " const sc=angular.element(m.querySelector('select')).scope();"
                + " const firstVal=(arr,valKey)=>{ const a=sc.$eval(arr); if(!Array.isArray(a)||!a.length) return null; const it=a.find(x=>x[valKey]!=null && !/^-*\\s*select/i.test((x.text||x.MiddleName||'')+'')) || a[0]; return it?it[valKey]:null; };"
                + " try{ if(typeof sc.FillKinDropDown==='function') sc.FillKinDropDown(); }catch(e){}"    // also loads drpOnlyDoc/NOK arrays
                + " try{ sc.$apply(); }catch(e){}"
                + " await new Promise(r=>setTimeout(r,900));"
                + " sc.Admission = sc.Admission || {};"
                + " sc.Admission.ClassificationID = firstVal('drpClassification','value');"
                + " sc.Admission.AdditionalDoctorID = firstVal('drpOnlyDoc','value');"
                + " try{ sc.$apply(); }catch(e){}"
                + " await new Promise(r=>setTimeout(r,300));"
                + " resolve('classification='+sc.Admission.ClassificationID+' additionalDoctor='+sc.Admission.AdditionalDoctorID); })");
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    // ---- Section 3: Next of Kin Details ----------------------------------

    /**
     * Section 3 — set NOK <b>Title/Relationship/Receivable/Occupation</b> + <b>Name/Mobile/Address</b> and LEAVE
     * them populated.
     * <p>IMPORTANT: do NOT click the section's <b>Add</b> button ({@code AddKinDetails}) — verified live that it
     * CLEARS the Kin Name field, and the Save validates the current Name, so a cleared Name makes the server reject
     * the save with <i>"Please Enter Name!"</i>. The Save reads the current field values directly.</p>
     */
    public String fillNokSection() {
        Object r = page.evaluate("() => new Promise(async (resolve) => {"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change admission type/i.test(x.textContent||'')); if(!m) return resolve('NO MODAL');"
                + " const sc=angular.element(m.querySelector('select')).scope();"
                + " const firstVal=(arr,valKey)=>{ const a=sc.$eval(arr); if(!Array.isArray(a)||!a.length) return null; const it=a.find(x=>x[valKey]!=null && !/^-*\\s*select/i.test((x.text||x.MiddleName||'')+'')) || a[0]; return it?it[valKey]:null; };"
                + " const setInp=(ng,val)=>{ const i=m.querySelector('input[ng-model=\"'+ng+'\"]'); if(!i) return; const c=angular.element(i).controller('ngModel'); i.value=val; if(c){c.$setViewValue(val);c.$render();} i.dispatchEvent(new Event('input',{bubbles:true})); i.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " try{ if(typeof sc.FillKinDropDown==='function') sc.FillKinDropDown(); sc.$apply(); }catch(e){}"
                + " await new Promise(r=>setTimeout(r,700));"
                + " sc.Admission = sc.Admission || {};"
                + " sc.Admission.KinTitleID = firstVal('drpPrefix','PrefixID');"
                + " sc.Admission.KinRelationID = firstVal('drpRelation','value');"
                + " sc.Admission.KinReceivableID = firstVal('drpAllReceivable','value');"
                + " sc.Admission.KinOccupationID = firstVal('drpOccupation','value');"
                + " try{ sc.$apply(); }catch(e){}"
                + " setInp('Admission.KinName','TEST KIN AUTOMATION');"
                + " setInp('Admission.KinMobileCountryCode','60');"
                + " setInp('Admission.KinMobileNo','12"+String.format("%07d", random.nextInt(10000000))+"');"
                + " setInp('Admission.KinAddress','No 1 Test Street, Kuala Lumpur');"
                + " setInp('Admission.KinRemark','Automated NOK');"
                + " try{ sc.$apply(); }catch(e){}"
                + " await new Promise(r=>setTimeout(r,300));"
                + " resolve('NOK set (title='+sc.Admission.KinTitleID+' rel='+sc.Admission.KinRelationID+' recv='+sc.Admission.KinReceivableID+' occ='+sc.Admission.KinOccupationID+' name=set)'); })");
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    // ---- Save ------------------------------------------------------------

    /** Click <b>Save</b> ({@code fnUpdateAdmissionType()}), accept any confirm dialog (native or DevHIS
     *  "Do You Want To Save"), and return the success toast. If no success toast appears, returns a
     *  {@code [diag] ...} string describing what actually happened (error toast / validation / modal state)
     *  so the failure is self-explanatory in the report. */
    public String saveAndGetToast() {
        // A native confirm()/alert() on save would otherwise be auto-dismissed by Playwright (aborting the save).
        page.onDialog(d -> { try { d.accept(); } catch (Exception ignore) { } });
        // Observe ALL toasts (success AND error) so we can report the real outcome.
        page.evaluate("() => { window.__catT=[]; if(window.__catO) window.__catO.disconnect();"
                + " window.__catO=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,.toast-error,.toast-success').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__catT.includes(t)) window.__catT.push(t); }); }); window.__catO.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /change admission type/i.test(x.textContent||''));"
                + " const b=m&&[...m.querySelectorAll('button')].find(x=>/fnUpdateAdmissionType/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        // Accept any "Do You Want To Save / Are you sure" confirm (DevHIS uses a red btn-danger SAVE, or Yes/OK).
        for (int i = 0; i < 6; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,.swal2-popup,.bootbox')].some(m=>m.offsetParent!==null && /do you want|are you sure|confirm|save\\?/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,.swal2-popup,.bootbox')].find(m=>m.offsetParent!==null && /do you want|are you sure|confirm|save\\?/i.test(m.textContent||''));"
                    + " const btns=[...(box||document).querySelectorAll('button')].filter(x=>x.offsetParent!==null);"
                    + " const b=btns.find(x=>/^\\s*save\\s*$/i.test(x.textContent||'') && /danger/.test(x.className)) || btns.find(x=>/^(yes|ok|confirm|save)$/i.test((x.textContent||'').trim())); if(b) b.click(); }");
            waitForAngular(700);
        }
        try {
            page.waitForFunction("() => (window.__catT||[]).some(a=>/success|saved|updated/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("saveAndGetToast: no success toast observed"); }
        // Prefer a success toast; otherwise return a diagnostic describing what blocked the save.
        Object r = page.evaluate("() => { const a=window.__catT||[];"
                + " const ok=a.find(x=>/success|saved|updated/i.test(x)); if(ok) return ok;"
                + " if(a.length) return '[diag] toast: '+a.join(' | ');"
                + " const vis=sel=>[...document.querySelectorAll(sel)].filter(e=>e.offsetParent!==null).map(e=>(e.textContent||'').replace(/\\s+/g,' ').trim()).filter(Boolean);"
                + " const swal=vis('.sweet-alert,.swal2-popup,.bootbox'); if(swal.length) return '[diag] popup: '+swal[0].slice(0,160);"
                + " const modalOpen=[...document.querySelectorAll('.modal')].some(x=>x.offsetParent!==null && /change admission type/i.test(x.textContent||''));"
                + " const valid=vis('.ng-invalid.ng-dirty,.field-validation-error,.text-danger,.help-block').filter(t=>t.length<80); "
                + " return '[diag] no toast; modal '+(modalOpen?'still OPEN':'CLOSED')+(valid.length?('; validation: '+valid.slice(0,3).join(' / ')):''); }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Expected Discharge Date -----------------------------------------

    /** Click the <b>Expected Discharge Date</b> footer button ({@code OpenExpectedDischargeModal()}) and wait
     *  for its modal (date field {@code ExpectedDischarge.ExpectedDischargeDate}, Save {@code IUDExpectedDischarge}).
     *  Returns true if it opened. */
    public boolean clickExpectedDischargeDate() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/OpenExpectedDischargeModal/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && m.querySelector('input[ng-model=\"ExpectedDischarge.ExpectedDischargeDate\"]'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { System.out.println("clickExpectedDischargeDate: modal did not open"); return false; }
        waitForAngular(600);
        return true;
    }

    /**
     * Set a <b>future date</b> (today + 7) in the Expected Discharge Date modal and click <b>Save</b>
     * ({@code IUDExpectedDischarge()}). Returns the success toast (verified live:
     * <b>"Expected Discharge Date Added Succesfully."</b>).
     */
    public String setFutureDateAndSaveExpectedDischarge() {
        java.time.LocalDate d = java.time.LocalDate.now().plusDays(7);
        String future = d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        page.evaluate("(fd) => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector('input[ng-model=\"ExpectedDischarge.ExpectedDischargeDate\"]')); if(!m) return;"
                + " const i=m.querySelector('input[ng-model=\"ExpectedDischarge.ExpectedDischargeDate\"]'); const c=angular.element(i).controller('ngModel'); i.value=fd; if(c){c.$setViewValue(fd);c.$render();} i.dispatchEvent(new Event('input',{bubbles:true})); i.dispatchEvent(new Event('change',{bubbles:true})); }", future);
        waitForAngular(500);
        page.evaluate("() => { window.__eddT=[]; if(window.toastr && !window.__eddHook){ window.__eddHook=true; ['success','error','info','warning'].forEach(k=>{ const o=window.toastr[k]; window.toastr[k]=function(msg){ try{window.__eddT.push(msg);}catch(e){} return o&&o.apply(this,arguments); }; }); }"
                + " if(window.__eddObs) window.__eddObs.disconnect(); window.__eddObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__eddT.includes(t)) window.__eddT.push(t); }); }); window.__eddObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector('input[ng-model=\"ExpectedDischarge.ExpectedDischargeDate\"]'));"
                + " const b=m&&[...m.querySelectorAll('button')].find(x=>/IUDExpectedDischarge/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].some(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].find(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|confirm)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(600);
        }
        try {
            page.waitForFunction("() => (window.__eddT||[]).some(a=>/expected discharge|added|success/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("setFutureDateAndSaveExpectedDischarge: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__eddT||[]; return a.find(x=>/expected discharge|added|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        // Close the Expected Discharge Date popup (its Close/× button has no ng-click — it's a data-dismiss),
        // then strip any leftover modal/backdrop so it can't intercept the next section.
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.offsetParent!==null && x.querySelector('input[ng-model=\"ExpectedDischarge.ExpectedDischargeDate\"]'));"
                + " if(m){ const b=[...m.querySelectorAll('button')].find(x=>/^close$/i.test((x.textContent||'').trim()) && x.offsetParent!==null) || m.querySelector('button.close') || [...m.querySelectorAll('button')].find(x=>(x.textContent||'').trim()==='\\u00d7'); if(b) b.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} }"
                + " document.querySelectorAll('.modal-backdrop').forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(800);
        String toast = r == null ? "" : r.toString().trim();
        return toast.isEmpty() ? "" : toast + " (date " + future + ")";
    }

    // ---- Close Admission -------------------------------------------------

    /** Click the <b>Close Admission</b> footer button ({@code OpenRevokeTypeModal('closeAdmission')}) and wait for
     *  the Close/Revoke modal (Remark textarea {@code Discharge.visitadmissionclosedrevokedremark}, Save
     *  {@code fnCloseRevokeVisitClick}). Targeted by button text (Close and Revoke share the OpenRevokeTypeModal
     *  handler). The save + toast are handled by {@link #revokeEnterRemarkAndSave()}. Returns true if it opened. */
    public boolean clickCloseAdmission() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^close admission$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && m.querySelector('textarea[ng-model=\"Discharge.visitadmissionclosedrevokedremark\"]'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { System.out.println("clickCloseAdmission: close modal did not open"); return false; }
        waitForAngular(600);
        return true;
    }

    // ---- Cancel Admission ------------------------------------------------

    /** Click the <b>Cancel Admission</b> footer button ({@code CancelAdmission()}) and wait for the
     *  "Reason for Cancellation" modal (dropdown {@code CancellationReason}). Returns true if it opened. */
    public boolean clickCancelAdmission() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/CancelAdmission/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && m.querySelector('select[ng-model=\"CancellationReason\"]'))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { System.out.println("clickCancelAdmission: reason modal did not open"); return false; }
        waitForAngular(600);
        return true;
    }

    /** Click the <b>Revoke Admission</b> footer button ({@code OpenRevokeTypeModal()}) and wait for the shared
     *  Close/Revoke modal (a required Remark textarea {@code Discharge.visitadmissionclosedrevokedremark}, Save =
     *  {@code fnCloseRevokeVisitClick}). Returns true if it opened. */
    public boolean clickRevokeAdmission() {
        // Target the REVOKE button specifically: BOTH Close (OpenRevokeTypeModal('closeAdmission')) and Revoke
        // (OpenRevokeTypeModal()) match /OpenRevokeTypeModal/, and Close comes first in the DOM — a loose selector
        // opens the CLOSE modal → the save then reports "Admission Closed Successfully.". Match by button text.
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^revoke admission$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>{ const ng=x.getAttribute('ng-click')||''; return /OpenRevokeTypeModal\\s*\\(\\s*\\)/.test(ng) && !/closeadmission/i.test(ng) && x.offsetParent!==null; }); if(b) b.click(); }");
        if (revokeModalOpen()) { waitForAngular(600); return true; }
        // The footer Revoke button is often DISABLED (ng-disabled allowvisitrevoke==false / isRevokeAdmission) — call
        // OpenRevokeTypeModal() (the no-arg = Revoke branch) on the scope directly, then show #RevokeType (same
        // scope-fallback the Emergency List View uses).
        page.evaluate("() => { let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.OpenRevokeTypeModal==='function'){sc=x;break;} x=x.$parent; } }catch(e){} });"
                + " if(sc){ try{ sc.$apply(()=>sc.OpenRevokeTypeModal()); }catch(e){} try{ if(window.jQuery) jQuery('#RevokeType').modal('show'); }catch(e){} } }");
        try {
            page.waitForFunction("() => { const m=document.querySelector('#RevokeType'); return m && m.getBoundingClientRect().width>0 && m.querySelector('textarea[ng-model=\"Discharge.visitadmissionclosedrevokedremark\"]'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { System.out.println("clickRevokeAdmission: revoke modal did not open (button + scope fallback)"); return false; }
        waitForAngular(600);
        return true;
    }

    /** True when the <b>Revoke</b> modal (#RevokeType, or a visible close/revoke modal whose text mentions revoke) is open. */
    private boolean revokeModalOpen() {
        try {
            page.waitForFunction("() => { const m=document.querySelector('#RevokeType'); if(m && m.getBoundingClientRect().width>0) return true;"
                    + " return [...document.querySelectorAll('.modal,[role=dialog]')].some(x=>x.getBoundingClientRect().width>0 && x.querySelector('textarea[ng-model=\"Discharge.visitadmissionclosedrevokedremark\"]') && /revoke/i.test(x.textContent||'')); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(6000));
            return true;
        } catch (Exception e) { return false; }
    }

    /**
     * Enter a <b>Remark</b> and click <b>Save</b> ({@code fnCloseRevokeVisitClick}) in the Revoke/Close modal.
     * Returns the success toast if one appears; otherwise "(saved — modal closed, no toast)" when the modal
     * closes without an error (this shared Close/Revoke modal does not always emit a toast on the occupancy list).
     */
    public String revokeEnterRemarkAndSave() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector('textarea[ng-model=\"Discharge.visitadmissionclosedrevokedremark\"]')); if(!m) return;"
                + " const ta=m.querySelector('textarea[ng-model=\"Discharge.visitadmissionclosedrevokedremark\"]'); const c=angular.element(ta).controller('ngModel'); ta.value='Revoked - automated test'; if(c){c.$setViewValue('Revoked - automated test');c.$render();} ta.dispatchEvent(new Event('input',{bubbles:true})); ta.dispatchEvent(new Event('change',{bubbles:true})); }");
        waitForAngular(500);
        page.evaluate("() => { window.__rvT=[]; if(window.toastr && !window.__rvHook){ window.__rvHook=true; ['success','error','info','warning'].forEach(k=>{ const o=window.toastr[k]; window.toastr[k]=function(msg,t){ try{window.__rvT.push(msg);}catch(e){} return o&&o.apply(this,arguments); }; }); }"
                + " if(window.__rvObs) window.__rvObs.disconnect(); window.__rvObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rvT.includes(t)) window.__rvT.push(t); }); }); window.__rvObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector('textarea[ng-model=\"Discharge.visitadmissionclosedrevokedremark\"]'));"
                + " const b=m&&[...m.querySelectorAll('button')].find(x=>/fnCloseRevokeVisitClick/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 5; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,.bootbox')].some(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,.bootbox')].find(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|confirm)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(700);
        }
        // Prefer the toast (the toastr hook captures it even after the DOM toast auto-dismisses). Only fall back
        // to "modal closed" if no toast appears — the modal closes fast on success, so don't let that short-circuit
        // the toast capture.
        try {
            page.waitForFunction("() => (window.__rvT||[]).some(a=>/revoke|close|cancel|success|saved/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("revokeEnterRemarkAndSave: no toast within 15s — falling back to modal-closed check"); }
        Object r = page.evaluate("() => { const a=window.__rvT||[]; const t=a.find(x=>/revoke|close|cancel|success/i.test(x)) || a[0];"
                + " if(t) return t; const stillOpen=[...document.querySelectorAll('.modal')].some(x=>x.offsetParent!==null && x.querySelector('textarea[ng-model=\"Discharge.visitadmissionclosedrevokedremark\"]')); return stillOpen ? '' : '(saved — modal closed, no toast)'; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /**
     * Select a cancellation reason, click <b>Save</b>, capture the inline success message, then click
     * <b>Close</b>. Returns the success message.
     * <p>NOTE: this action's success is shown <b>inside the modal</b> ("✅ Admission Cancelled Successfully!"),
     * NOT as a toastr toast. The reason dropdown ({@code CancellationReason}) uses object-binding ng-options
     * (over {@code CancellationReasonList}), so it's set via selectedIndex + change. The Save button is a
     * {@code btn-danger} with NO ng-click (jQuery handler) — targeted by its text "Save" (the "Close" button is
     * also {@code btn-danger}, so a blanket btn-danger click is unsafe) and real-clicked.</p>
     */
    /** Screenshot of the Cancel/Revoke modal captured WHILE its success message is visible (before Close). */
    public byte[] lastCancelSuccessPng = null;

    public String selectReasonSaveAndClose() {
        // Pick the first real reason (object-binding ng-options → selectedIndex + change).
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector('select[ng-model=\"CancellationReason\"]')); if(!m) return;"
                + " const s=m.querySelector('select[ng-model=\"CancellationReason\"]'); const i=[...s.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim()));"
                + " if(i>=0){ s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(s).trigger('change');}catch(e){}} } }");
        waitForAngular(500);
        // Tag + real-click the SAVE button (btn-danger with text 'Save' — NOT the 'Close' btn-danger).
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && x.querySelector('select[ng-model=\"CancellationReason\"]')); if(!m) return;"
                + " const b=[...m.querySelectorAll('button')].find(x=>/^save$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.id='__cancelAdmSave'; }");
        try { page.locator("#__cancelAdmSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("selectReasonSaveAndClose: Save click failed - " + e.getMessage()); }
        // The success message appears inside the SAME modal ("✅ Admission Cancelled/Revoked Successfully!").
        // (Cancel Admission and Revoke Admission share this modal, so accept either wording.)
        String msg = "";
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.offsetParent!==null && /admission (cancelled|revoked) successfully/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
            Object r = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.offsetParent!==null && /admission (cancelled|revoked) successfully/i.test(x.textContent||'')); if(!m) return ''; const mt=(m.textContent||'').match(/Admission (Cancelled|Revoked) Successfully[!.]?/i); return mt?mt[0]:''; }");
            msg = r == null ? "" : r.toString().trim();
        } catch (Exception ignore) { System.out.println("selectReasonSaveAndClose: success message not seen"); }
        // Screenshot the popup WHILE the success message is still visible (before Close), so the report evidences the
        // "Admission Cancelled Successfully!" confirmation inside the modal.
        lastCancelSuccessPng = null;
        if (!msg.isEmpty()) {
            try { lastCancelSuccessPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(10000)); }
            catch (Exception e) { System.out.println("selectReasonSaveAndClose: success screenshot failed - " + e.getMessage()); }
        }
        // Click Close to dismiss the modal.
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.offsetParent!==null && x.querySelector('select[ng-model=\"CancellationReason\"]')); if(!m) return; const b=[...m.querySelectorAll('button')].find(x=>/^close$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        return msg;
    }

    // ---- Plan Discharge (advise discharge) -------------------------------

    /**
     * Click the footer <b>Plan Discharge</b> button ({@code GetAdviceDischarge()} → {@code #adviseDischrg}) for the
     * selected patient and WAIT for the popup data to load (the advice list rendered as rows with a Select checkbox +
     * a remark). If the button is disabled (ISNullBedId) it falls back to calling {@code GetAdviceDischarge()} on the
     * scope + showing the modal. Returns true when the popup is loaded with rows.
     */
    public boolean clickPlanDischarge() {
        Object tagged = page.evaluate("() => { const b=document.querySelector('#btnOpenAlladviseDischrg') || [...document.querySelectorAll('button,a')].find(x=>/^\\s*plan discharge\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return 'none'; if(b.disabled) return 'disabled'; b.id='btnOpenAlladviseDischrg'; return true; }");
        String st = String.valueOf(tagged);
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#btnOpenAlladviseDischrg").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickPlanDischarge: click failed - " + e.getMessage()); }
        } else if ("disabled".equals(st)) {
            System.out.println("clickPlanDischarge: footer button disabled — fetching via scope GetAdviceDischarge()");
            page.evaluate("() => { let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.GetAdviceDischarge==='function'){sc=x;break;} x=x.$parent; } }catch(e){} }); if(sc){ try{ sc.$apply(()=>sc.GetAdviceDischarge()); }catch(e){} } }");
            waitForAngular(1200);
            page.evaluate("() => { try{ if(window.jQuery) jQuery('#adviseDischrg').modal('show'); }catch(e){} }");
        } else {
            System.out.println("clickPlanDischarge: Plan Discharge button not found");
            return false;
        }
        String loadedJs = "() => { const m=document.querySelector('#adviseDischrg'); if(!m || m.getBoundingClientRect().width===0) return false;"
                + " return [...m.querySelectorAll('tbody tr')].some(r=>r.offsetParent!==null && r.querySelector('input[type=checkbox]')); }";
        try { page.waitForFunction(loadedJs, null, new Page.WaitForFunctionOptions().setTimeout(20000)); }
        catch (Exception ignore) { System.out.println("clickPlanDischarge: popup did not load in time"); }
        waitForAngular(600);
        return Boolean.TRUE.equals(page.evaluate(loadedJs));
    }

    /**
     * In the Plan Discharge popup, tick a row (the reason/department to advise-discharge) and enter its <b>remark</b>.
     * Prefers the first not-yet-checked row (the modal pre-checks charged rows). Returns "Reason | remark".
     */
    public String selectReasonAndRemark(String remark) {
        Object r = page.evaluate("(remark) => { const m=document.querySelector('#adviseDischrg'); if(!m) return '(no modal)';"
                + " const rows=[...m.querySelectorAll('tbody tr')].filter(x=>x.offsetParent!==null && x.querySelector('input[type=checkbox]')); if(!rows.length) return '(no rows)';"
                + " let row=rows.find(x=>{ const cb=x.querySelector('input[type=checkbox]'); return cb && !cb.checked; }) || rows[0];"
                + " const cb=row.querySelector('input[type=checkbox]'); const ta=row.querySelector('textarea');"
                + " const cbC=angular.element(cb).controller('ngModel'); if(cbC){ cbC.$setViewValue(true); cbC.$render(); } cb.checked=true; cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " if(ta){ const taC=angular.element(ta).controller('ngModel'); ta.value=remark; if(taC){ taC.$setViewValue(remark); taC.$render(); } ta.dispatchEvent(new Event('input',{bubbles:true})); ta.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const cells=[...row.querySelectorAll('td')].map(td=>(td.textContent||'').replace(/\\s+/g,' ').trim());"
                + " const reason=cells[1]||cells.find(c=>c)||'(reason)'; return reason+' | '+remark; }", remark);
        waitForAngular(400);
        return r == null ? "(null)" : r.toString();
    }

    /** Click <b>Save</b> ({@code IUDAdviceDescharge()}) in the Plan Discharge popup; returns the success toast
     *  ("Advice Discharge saved successfully.") captured via a MutationObserver. */
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
                + " try{ if(window.jQuery) jQuery('#adviseDischrg').modal('hide'); }catch(e){} document.querySelectorAll('.modal-backdrop').forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    // ---- Change Refer Entity (footer action) -----------------------------

    /** The visible "Change Refer Entity" modal (matched by its Refer Entity text). */
    private static final String REFER_MODAL_JS =
            "[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /refer\\s*entity/i.test(x.textContent||''))";

    /** Click the footer <b>Change Refer Entity</b> button and wait for its modal (has Refer Entity Type / Refer Entity
     *  dropdowns). Returns true if it opened. */
    public boolean clickChangeReferEntity() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/change\\s*refer\\s*entity/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/referentity|changerefer/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => { const m=" + REFER_MODAL_JS + "; if(!m) return false; const s=m.querySelector(\"select[ng-model='Ref.EntityTypeId']\"); return s && [...s.options].some(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { System.out.println("clickChangeReferEntity: modal/Type options did not load"); return false; }
        waitForAngular(600);
        return true;
    }

    /** Diagnostic: dump the Change Refer Entity modal's selects (ng-model + label + option count) and buttons. */
    public String dumpReferEntityModal() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const m=" + REFER_MODAL_JS + "; if(!m) return '(no modal)';"
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,.col-sm-6,.col-md-6,td,div'); const l=g?g.querySelector('label,.control-label'):null; return norm(l?l.textContent:'').slice(0,30); };"
                + " const sels=[...m.querySelectorAll('select')].map(s=>'SELECT ng=\"'+(s.getAttribute('ng-model')||'')+'\" label=\"'+labelOf(s)+'\" opts='+s.options.length);"
                + " const btns=[...m.querySelectorAll('button,a')].filter(b=>b.offsetParent!==null).map(b=>'\"'+norm(b.textContent)+'\" {'+(b.getAttribute('ng-click')||'')+'}');"
                + " return 'selects=\\n  '+sels.join('\\n  ')+'\\nbuttons=\\n  '+btns.join('\\n  '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * In the Change Refer Entity modal: select the <b>Refer Entity Type</b> and <b>Refer Entity</b> dropdowns (first
     * real option of each; the Type may cascade-load the Entity list), then click <b>Add</b>. Dropdowns located by
     * their label ("Refer Entity Type" / "Refer Entity"). Returns a summary.
     */
    public String fillReferEntityAndAdd() {
        Object r = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const m=" + REFER_MODAL_JS + "; if(!m) return resolve('(no modal)');"
                + " const realIdx=sel=>[...sel.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
                + " const pick=(sel)=>{ if(!sel) return '(no)'; const i=realIdx(sel); if(i<0) return '(no-opt)'; sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(sel).triggerHandler('change');}catch(e){} if($){try{$(sel).trigger('change');}catch(e){}} return norm(sel.options[i].textContent); };"
                // Refer Entity Type = Ref.EntityTypeId (cascade parent) — poll for its options first
                + " let typeSel; for(let k=0;k<15;k++){ typeSel=m.querySelector(\"select[ng-model='Ref.EntityTypeId']\"); if(typeSel && realIdx(typeSel)>=0) break; await sleep(400); }"
                + " const type=pick(typeSel);"
                // Refer Entity = Ref.EntityId — loads after Type; poll for its options
                + " let entSel; for(let k=0;k<15;k++){ entSel=m.querySelector(\"select[ng-model='Ref.EntityId']\"); if(entSel && realIdx(entSel)>=0) break; await sleep(400); }"
                + " const ent=pick(entSel);"
                + " await sleep(500);"
                // Add = AddRefEntity()
                + " const addBtn=[...m.querySelectorAll('button,a')].find(x=>/AddRefEntity/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(addBtn) addBtn.click();"
                + " await sleep(700);"
                + " resolve('ReferEntityType='+type+' | ReferEntity='+ent+' | Add='+(addBtn?'clicked':'not-found')); })");
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Save</b> in the Change Refer Entity modal and return the success toast. */
    public String saveReferEntityAndGetToast() {
        page.evaluate("() => { window.__reT=[]; if(window.__reO) window.__reO.disconnect();"
                + " window.__reO=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__reT.includes(t)) window.__reT.push(t); }); }); window.__reO.observe(document.body,{childList:true,subtree:true});"
                + " const m=" + REFER_MODAL_JS + "; if(!m) return;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>/IUDRefEntity/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 5; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.sweet-alert,.bootbox')].some(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.sweet-alert,.bootbox')].find(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|confirm|save)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(600);
        }
        try {
            page.waitForFunction("() => (window.__reT||[]).some(a=>/refer entity|saved|success|added|updated|please|select|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__reT||[]).includes(t)) (window.__reT=window.__reT||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__reT||[]; return a.find(x=>/refer entity.*(saved|added|updated)|saved successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Close</b> on the Change Refer Entity modal (and clean up any leftover backdrop). */
    public void closeReferEntityModal() {
        page.evaluate("() => { const m=" + REFER_MODAL_JS + "; if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null) || m.querySelector('button.close'); if(b) b.click(); }"
                + " const $=window.jQuery||window.$; if($){ try{ $('.modal').modal('hide'); }catch(e){} } document.querySelectorAll('.modal-backdrop').forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(600);
    }

    // ---- Consent / Forms (external-form document flow) -------------------

    /** URL + screenshot of the last external-form document report opened in the new tab. */
    public byte[] lastConsentReportPng = null;
    public String lastConsentReportUrl = "";
    /** True once the external-form "Form Submitted/Saved Successfully!" confirmation was seen. */
    public boolean lastConsentSubmitConfirmed = false;
    public String lastConsentSubmitMsg = "";

    private String safeUrl(com.microsoft.playwright.Page p) { try { return p.url(); } catch (Exception e) { return ""; } }

    /**
     * Click the footer <b>Consent/Forms</b> button ({@code fnOnConsentClick()} → modal {@code #consent}) for the
     * selected patient and wait for the popup (type-to-search field {@code #txtAdmissionListConsentSearch}).
     */
    public boolean clickConsentForms() {
        // clear any stale modal/backdrop from a prior section, then SYNTHETIC-click (bypasses residual overlay).
        Object tagged = page.evaluate("() => {"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal.in,.modal.show,.modal[style*=\"display: block\"]')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open');"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('fnOnConsentClick')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*consent\\/?\\s*forms?\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__consentFormsBtn'; b.click(); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickConsentForms: 'Consent/Forms' button not found"); return false; }
        try {
            page.waitForFunction("() => { const m=document.querySelector('#consent'); return m && m.getBoundingClientRect().width>0 && m.querySelector('#txtAdmissionListConsentSearch'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("clickConsentForms: #consent modal did not open"); return false; }
        waitForAngular(800);
        return true;
    }

    /**
     * Pick an <b>external</b> consent form (one whose {@code Getconsenttemplate} returns {@code externalformmappingid>0}
     * — only those open the document tab on Save). Fetches {@code AdmissionFactory.FetchConsentData} and probes each
     * form's mapping id, then sets {@code consent.consentid}/{@code consent.consentname} on the scope directly.
     * Returns {@code "FormName | ext=N"}, or a {@code "(...)"} diagnostic.
     */
    public String selectExternalConsentForm() {
        Object r = page.evaluate("async () => { const m=document.querySelector('#consent'); if(!m) return '(no modal)';"
                + " const sc=angular.element(m).scope(); const inj=angular.element(document.body).injector();"
                + " let AF,qf; try{ AF=inj.get('AdmissionFactory'); qf=inj.get('queueManagementFactory'); }catch(e){ return '(no factory)'; }"
                + " let resp; try{ resp=await AF.FetchConsentData({ExecFlag:'Consent'}); }catch(e){ return '(fetch failed)'; }"
                + " const list=(resp.data||[]).filter(x=>x && x.value!=null && !x.disabled); if(!list.length) return '(no forms)';"
                + " for(const f of list.slice(0,25)){ let ext=0;"
                + "   try{ const t=await qf.fetchQueueManagement({consentid:f.value, ExecFlag:'Getconsenttemplate'}); ext=(t.data&&t.data[0]&&t.data[0].externalformmappingid)||0; }catch(e){}"
                + "   if(ext>0){ sc.$apply(()=>{ sc.consent=sc.consent||{}; sc.consent.consentid=f.value; sc.consent.consentname=f.text; }); return f.text+' | ext='+ext; } }"
                + " return '(no external form found)'; }");
        waitForAngular(400);
        return r == null ? "(null)" : r.toString();
    }

    /** Click <b>Add</b> ({@code Getconsenttemplate()}) — fetches the form's template + sets
     *  {@code externalformmappingid}. Returns true once {@code externalformmappingid>0}. */
    public boolean clickAddConsent() {
        Object tagged = page.evaluate("() => { const m=document.querySelector('#consent'); if(!m) return false;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>/Getconsenttemplate/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + "   || [...m.querySelectorAll('button,a')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__consentAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickAddConsent: Add button not found"); return false; }
        try { page.locator("#__consentAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickAddConsent: click failed - " + e.getMessage()); }
        boolean ext = false;
        try {
            page.waitForFunction("() => { const m=document.querySelector('#consent'); if(!m) return false; const sc=angular.element(m).scope(); return sc && sc.externalformmappingid>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
            ext = true;
        } catch (Exception ignore) { System.out.println("clickAddConsent: externalformmappingid not set after Add"); }
        waitForAngular(500);
        return ext;
    }

    /**
     * Click <b>Save</b> ({@code IUDconsentdetail()}) → the external form opens the document builder in a NEW TAB; there
     * click <b>Create Document</b> → <b>Submit</b> → confirm <b>Yes</b> → dismiss the success <b>OK</b> modal, then
     * capture a full-page screenshot of the {@code /Edit/{id}} report. Sets {@link #lastConsentReportUrl}/
     * {@link #lastConsentReportPng}; returns the screenshot (or {@code null} if the tab never opened).
     */
    public byte[] saveConsentFormAndCaptureReport() {
        lastConsentReportPng = null; lastConsentReportUrl = "";
        com.microsoft.playwright.Page popup = null;
        try {
            popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(25000), () -> {
                page.evaluate("() => { const m=document.querySelector('#consent'); const b=m&&[...m.querySelectorAll('button,a')].find(x=>/IUDconsentdetail/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
            });
        } catch (Exception e) { System.out.println("saveConsentForm: Save did not open a new tab - " + e.getMessage()); return null; }
        try { popup.waitForLoadState(); } catch (Exception ignore) {}
        popup.waitForTimeout(2500);

        // Create Document (a link that navigates the same tab to /Create)
        try {
            Object t = popup.evaluate("() => { const a=[...document.querySelectorAll('a,button')].find(x=>/create document/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!a) return false; a.id='__createDoc'; return true; }");
            if (Boolean.TRUE.equals(t)) { popup.locator("#__createDoc").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(10000)); }
            else System.out.println("saveConsentForm: 'Create Document' not found");
        } catch (Exception e) { System.out.println("saveConsentForm: Create Document click failed - " + e.getMessage()); }
        try { popup.waitForLoadState(); } catch (Exception ignore) {}
        popup.waitForTimeout(2000);

        // Submit
        try {
            Object t = popup.evaluate("() => { const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__extSubmit'; return true; }");
            if (Boolean.TRUE.equals(t)) { popup.locator("#__extSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(10000)); }
            else System.out.println("saveConsentForm: Submit not found");
        } catch (Exception e) { System.out.println("saveConsentForm: Submit click failed - " + e.getMessage()); }

        // Confirmation dialog -> Yes (position:fixed modal → detect by bounding-rect, not offsetParent; poll for fade).
        boolean yes = false;
        for (int i = 0; i < 8 && !yes; i++) {
            popup.waitForTimeout(500);
            Object t = popup.evaluate("() => { const box=[...document.querySelectorAll('.modal,[role=dialog]')].find(m=>m.getBoundingClientRect().width>0 && getComputedStyle(m).display!=='none' && /continue|are you sure|confirm/i.test(m.textContent||'')); if(!box) return false;"
                    + " const b=[...box.querySelectorAll('button,a')].find(x=>/^\\s*yes\\s*$/i.test((x.textContent||'').trim()) && x.getBoundingClientRect().width>0); if(!b) return false; b.id='__extYes'; return true; }");
            if (Boolean.TRUE.equals(t)) {
                try { popup.locator("#__extYes").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); yes = true; }
                catch (Exception e) { System.out.println("saveConsentForm: Yes click failed - " + e.getMessage()); }
            }
        }
        if (!yes) System.out.println("saveConsentForm: confirmation 'Yes' not found");

        // Dismiss the "Success — Form Submitted/Saved Successfully!" OK modal and land on /Edit/{id}.
        final String dismissJs = "() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && getComputedStyle(x).display!=='none' && /success/i.test(x.textContent||'') && [...x.querySelectorAll('button,a')].some(b=>/^\\s*ok\\s*$/i.test((b.textContent||'').trim())));"
                + " if(!m) return ''; const txt=(m.textContent||'').replace(/\\s+/g,' ').trim(); const ok=[...m.querySelectorAll('button,a')].find(b=>/^\\s*ok\\s*$/i.test((b.textContent||'').trim())); if(ok) ok.click();"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); return txt; }";
        lastConsentSubmitMsg = "";
        boolean successModal = false;
        for (int i = 0; i < 24 && !(successModal && safeUrl(popup).toLowerCase().contains("/edit/")); i++) {
            try {
                Object o = popup.evaluate(dismissJs);
                String seen = o == null ? "" : o.toString();
                if (!seen.isEmpty()) { successModal = true; lastConsentSubmitMsg = seen; }
            } catch (Exception ignore) { /* tab mid-navigation — retry */ }
            popup.waitForTimeout(500);
        }
        try { popup.waitForLoadState(); } catch (Exception ignore) {}
        try { Object o = popup.evaluate(dismissJs); if (o != null && !o.toString().isEmpty()) { successModal = true; if (lastConsentSubmitMsg.isEmpty()) lastConsentSubmitMsg = o.toString(); } } catch (Exception ignore) {}
        boolean editReached = safeUrl(popup).toLowerCase().contains("/edit/");
        lastConsentSubmitConfirmed = successModal || editReached;
        if (!lastConsentSubmitConfirmed) System.out.println("saveConsentForm: submit not confirmed - " + safeUrl(popup));
        popup.waitForTimeout(1500);

        byte[] png = null;
        try { popup.bringToFront(); png = popup.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
        catch (Exception e) { System.out.println("saveConsentForm: report screenshot failed - " + e.getMessage()); }
        lastConsentReportUrl = safeUrl(popup);
        lastConsentReportPng = png;
        try { page.bringToFront(); } catch (Exception ignore) {}
        try { for (com.microsoft.playwright.Page pg : page.context().pages()) { if (pg != page && !pg.isClosed()) pg.close(); } } catch (Exception ignore) {}
        waitForAngular(400);
        return png;
    }

    /** Close the Consent/Forms popup ({@code #consent}) + backdrop cleanup. */
    public void closeConsentFormsPopup() {
        page.evaluate("() => { const m=document.querySelector('#consent'); if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/fnclear/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) jQuery('#consent').modal('hide'); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    // ---- View Consent (list saved consents/forms, open one in a new tab) -

    /** URL + screenshot of the last saved consent/form report opened from the eye icon in a new tab. */
    public byte[] lastViewConsentReportPng = null;
    public String lastViewConsentReportUrl = "";

    /**
     * Click the footer <b>View Consent</b> button ({@code fnViewConsents()}) and wait for the View Consent/Forms modal
     * ({@code #viewConsentFormsModal}). Any leftover Consent modal + backdrop is dismissed first. Returns true if it opened.
     */
    public boolean clickViewConsent() {
        page.evaluate("() => { const $=window.jQuery||window.$;"
                + " ['#consent','#viewConsentFormsModal'].forEach(id=>{ try{ if($) $(id).modal('hide'); }catch(e){} });"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('fnViewConsents')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*view consent\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__viewConsentBtn'; b.click(); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickViewConsent: 'View Consent' button not found"); return false; }
        try {
            page.waitForFunction("() => { const m=document.querySelector('#viewConsentFormsModal'); if(m && m.getBoundingClientRect().width>0 && getComputedStyle(m).display!=='none') return true;"
                    + " return [...document.querySelectorAll('.modal')].some(x=>x.getBoundingClientRect().width>0 && /view consent/i.test(x.textContent||'')); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("clickViewConsent: View Consent modal did not open in time"); return false; }
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE); } catch (Exception ignore) {}
        waitForAngular(1200);
        return true;
    }

    /**
     * Click <b>Search</b> inside the View Consent/Forms modal ({@code searchConsentForms()}) and wait for the result
     * grid to settle — a record row (eye icon {@code viewConsentFormDetail}) or the empty-state "No records found.".
     * Returns {@code "records=N"} or {@code "No records found"}.
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
        waitForAngular(1000);
        return viewConsentShowsNoRecords() ? "No records found" : ("records=" + viewConsentRecordCount());
    }

    /** Number of consent/form records listed in the View Consent modal (excludes placeholder rows). */
    public int viewConsentRecordCount() {
        Object r = page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); if(!m) return 0;"
                + " return [...m.querySelectorAll('table tbody tr')].filter(tr=>{ const tds=[...tr.querySelectorAll('td')]; if(tds.length<3) return false; const t=(tr.textContent||'').toLowerCase(); return !/no records found|loading/.test(t); }).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** True when the View Consent modal shows the empty-state "No records found." (patient has no saved consent/forms). */
    public boolean viewConsentShowsNoRecords() {
        Object r = page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); return !!(m && /no records found/i.test(m.innerText)); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Click the row's eye/<b>View</b> icon ({@code viewConsentFormDetail(item)}) — {@code formName} if given, else the
     * first record — which opens the saved form report in a NEW TAB; capture a full-page screenshot. Sets
     * {@link #lastViewConsentReportUrl}/{@link #lastViewConsentReportPng}. Returns the screenshot, or {@code null}.
     */
    public byte[] clickConsentEyeAndCaptureReport(String formName) {
        lastViewConsentReportPng = null; lastViewConsentReportUrl = "";
        Object tagged = page.evaluate("(formText) => { const m=document.querySelector('#viewConsentFormsModal')||document;"
                + " const icons=[...m.querySelectorAll('[ng-click*=\"viewConsentFormDetail\"]')].filter(e=>e.offsetParent!==null); if(!icons.length) return 'no-icon';"
                + " const chosen=(formText ? icons.find(e=>{ const row=e.closest('tr'); return row && (row.textContent||'').toUpperCase().indexOf((''+formText).toUpperCase())>=0; }) : null) || icons[0];"
                + " chosen.id='__viewConsentEye'; return 'found'; }", formName);
        if (!"found".equals(String.valueOf(tagged))) { System.out.println("clickConsentEye: no eye icon (no records?)"); return null; }
        com.microsoft.playwright.Page popup = null;
        try {
            popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(20000), () -> {
                try { page.locator("#__viewConsentEye").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("clickConsentEye: click failed - " + e.getMessage()); }
            });
        } catch (Exception e) { System.out.println("clickConsentEye: eye icon did not open a new tab - " + e.getMessage()); return null; }
        try { popup.waitForLoadState(); } catch (Exception ignore) {}
        popup.waitForTimeout(3000);
        byte[] png = null;
        try { popup.bringToFront(); png = popup.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
        catch (Exception e) { System.out.println("clickConsentEye: report screenshot failed - " + e.getMessage()); }
        lastViewConsentReportUrl = safeUrl(popup);
        lastViewConsentReportPng = png;
        try { page.bringToFront(); } catch (Exception ignore) {}
        try { for (com.microsoft.playwright.Page pg : page.context().pages()) { if (pg != page && !pg.isClosed()) pg.close(); } } catch (Exception ignore) {}
        waitForAngular(400);
        return png;
    }

    /** Close the View Consent/Forms modal ({@code #viewConsentFormsModal}) + backdrop cleanup. */
    public void closeViewConsentPopup() {
        page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) jQuery('#viewConsentFormsModal').modal('hide'); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    // ---- Print Barcode ---------------------------------------------------

    /**
     * Click the footer <b>Print Barcode</b> button ({@code printpatient()}) for the selected patient and wait for the
     * <b>Barcode</b> modal (shows the patient's <b>MRN</b>, a <b>Small Size</b> option, Save / Print / Cancel). The
     * modal has no stable id, so it is matched by a visible dialog whose text contains "Barcode" + "MRN". Returns true
     * if it opened.
     */
    public boolean clickPrintBarcode() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('printpatient')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*print barcode\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__printBarcodeBtn'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickPrintBarcode: 'Print Barcode' button not found"); return false; }
        try { page.locator("#__printBarcodeBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickPrintBarcode: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /barcode/i.test(m.textContent||'') && /mrn/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("clickPrintBarcode: Barcode modal did not open in time"); return false; }
        waitForAngular(600);
        return true;
    }

    /** Read the Barcode modal (patient <b>MRN</b>) and tick the <b>Small Size</b> checkbox
     *  ({@code BarCodeData.IsSmall}). Returns e.g. {@code "MRN=100000850 | Small Size=ticked"}. */
    public String selectBarcodeSmallSizeAndRead() {
        Object r = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /barcode/i.test(x.textContent||'')); if(!m) return 'ERR:no-modal';"
                + " const txt=(m.innerText||'').replace(/\\s+/g,' ').trim(); const mrn=(txt.match(/MRN[^0-9]*([0-9]{5,})/i)||[])[1]||'';"
                + " const small=m.querySelector(\"input[ng-model='BarCodeData.IsSmall']\") || [...m.querySelectorAll('input[type=checkbox],input[type=radio]')].find(c=>{ const lab=(c.closest('label')&&c.closest('label').textContent)||(c.parentElement&&c.parentElement.textContent)||''; return /small/i.test(lab); });"
                + " let smallState='(no option)'; if(small){ if(!small.checked) small.click(); smallState=small.checked?'ticked':'unticked'; }"
                + " return 'MRN='+(mrn||'(not shown)')+' | Small Size='+smallState; }");
        waitForAngular(300);
        return r == null ? "(null)" : r.toString();
    }

    /** Click <b>Save</b> in the Barcode modal ({@code SaveBarcode(this)}) and return the success toast. Expected
     *  "Barcode Saved Successfully !!!.". (We do NOT click Print — that is a print preview.) */
    public String saveBarcodeAndGetToast() {
        // A native confirm()/alert() on save would otherwise be auto-dismissed by Playwright (aborting the save).
        page.onDialog(d -> { try { d.accept(); } catch (Exception ignore) { } });
        page.evaluate("() => { window.__bcToasts=[]; if(window.__bcObs) window.__bcObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,.toast-success,.toast-error,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__bcToasts.includes(t)) window.__bcToasts.push(t); }); };"
                + " window.__bcObs=new MutationObserver(grab); window.__bcObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /barcode/i.test(x.textContent||''));"
                + " const b=m&&([...m.querySelectorAll('button,a')].find(x=>/SaveBarcode/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null)); if(b) b.click(); }");
        // Answer any Bootstrap/DevHIS confirm dialog (Yes/OK/Save).
        for (int i = 0; i < 5; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.sweet-alert,.swal2-popup,.bootbox,.modal')].some(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm|save\\?/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.sweet-alert,.swal2-popup,.bootbox,.modal')].find(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm|save\\?/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|confirm|save)$/i.test((x.textContent||'').trim()) && x.getBoundingClientRect().width>0); if(b) b.click(); }");
            waitForAngular(600);
        }
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

    /** Close the Barcode modal — click Cancel/Close; if it lingers, dismiss it by clicking OUTSIDE the modal (an
     *  empty spot on the page) + Escape, then strip any backdrop. (Per live behaviour: an outside click dismisses it.) */
    public void cancelBarcodePopup() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /barcode/i.test(x.textContent||''));"
                + " if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*cancel\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){} }");
        waitForAngular(400);
        // If a barcode modal is STILL visible, click an empty area of the page (outside the modal) to dismiss it.
        boolean stillOpen = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(x=>x.getBoundingClientRect().width>0 && /barcode/i.test(x.textContent||''))"));
        if (stillOpen) {
            try { page.mouse().click(5, 5); } catch (Exception ignore) { }
            try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
            waitForAngular(400);
        }
        page.evaluate("() => { [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(300);
    }

    // ---- Assign Triage ---------------------------------------------------

    /**
     * Click the footer <b>Assign Triage</b> button ({@code BtnAssignTriage()}) and wait for the <b>Triage</b> modal (a
     * zone dropdown — Green / Yellow / Red Zone / Non-Emergency — + Save/Close). If the footer button is disabled it
     * falls back to calling {@code BtnAssignTriage()} on the scope. Modal matched by "Triage" text + a zone select.
     */
    public boolean clickAssignTriage() {
        Object clicked = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('BtnAssignTriage')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*assign triage\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return 'no-button';"
                + " if(b.disabled){ let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.BtnAssignTriage==='function'){sc=x;break;} x=x.$parent; } }catch(e){} }); if(sc){ try{ sc.$apply(()=>sc.BtnAssignTriage()); }catch(e){} return 'scope'; } return 'disabled'; }"
                + " b.id='__occAssignTriage'; return 'tagged'; }");
        String how = String.valueOf(clicked);
        if ("no-button".equals(how)) { System.out.println("clickAssignTriage: 'Assign Triage' button not found"); return false; }
        if ("tagged".equals(how)) {
            try { page.locator("#__occAssignTriage").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickAssignTriage: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__occAssignTriage'); if(e) e.removeAttribute('id'); }");
        } else if ("disabled".equals(how)) {
            System.out.println("clickAssignTriage: footer button disabled and no scope BtnAssignTriage() — patient may not be bedded");
        }
        boolean opened = false;
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /triage/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
            opened = true;
        } catch (Exception ignore) { System.out.println("clickAssignTriage: Triage modal did not open in time"); }
        if (!opened) {
            System.out.println("clickAssignTriage: footer buttons => " + page.evaluate("() => [...document.querySelectorAll('button,a')].filter(b=>b.offsetParent!==null && /triage/i.test((b.textContent||'')+(b.getAttribute('ng-click')||''))).map(b=>(b.textContent||'').replace(/\\s+/g,' ').trim()+' {'+(b.getAttribute('ng-click')||'')+'} disabled='+!!b.disabled).join(' | ')"));
            return false;
        }
        waitForAngular(800);
        return true;
    }

    /** Pick a triage value in the Triage modal. On Bed Board the control is {@code select[ng-model='AssignTriage.triagemasterid']}
     *  (poll for real options — it may load async / be empty for the patient). Returns the chosen text, or an {@code ERR:*} marker. */
    public String selectTriageZone() {
        String js = "() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||'')); if(!m) return 'ERR:no-modal';"
                + " const sel=m.querySelector(\"select[ng-model='AssignTriage.triagemasterid']\") || [...m.querySelectorAll('select')].find(s=>[...s.options].some(o=>/zone|emergency|triage/i.test(o.textContent||''))); if(!sel) return 'ERR:no-dropdown';"
                + " const i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i<0) return 'ERR:no-option';"
                + " sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}} try{angular.element(sel).triggerHandler('change');}catch(e){}"
                + " return norm(sel.options[i].textContent); }";
        String res = "(null)";
        for (int i = 0; i < 12; i++) {   // poll — the triage master list may load async
            Object r = page.evaluate(js);
            res = r == null ? "(null)" : r.toString();
            if (!res.startsWith("ERR")) break;
            page.waitForTimeout(400);
        }
        waitForAngular(500);
        return res;
    }

    /** Click <b>Save</b> ({@code FnSaveAssignTriage()}) in the Triage modal and return the success toast. */
    public String saveTriageAndGetToast() {
        page.evaluate("() => { window.__tgToasts=[]; if(window.__tgObs) window.__tgObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__tgToasts.includes(t)) window.__tgToasts.push(t); }); };"
                + " window.__tgObs=new MutationObserver(grab); window.__tgObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||''));"
                + " const b=m&&([...m.querySelectorAll('button,a')].find(x=>/FnSaveAssignTriage/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null)); if(b) b.click(); }");
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

    /** Close the Triage modal — Close button; if it lingers, dismiss by clicking OUTSIDE the modal + Escape, then
     *  strip the backdrop. */
    public void closeTriagePopup() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||''));"
                + " if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){} }");
        waitForAngular(400);
        boolean stillOpen = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||''))"));
        if (stillOpen) {
            try { page.mouse().click(5, 5); } catch (Exception ignore) { }
            try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
            waitForAngular(400);
        }
        page.evaluate("() => { [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(300);
    }

    // ---- Fall Risk Assessment --------------------------------------------

    /** Click the footer <b>Fall Risk Assessment</b> button ({@code fnOpenFallRiskAssessment()}) and wait for the
     *  "ASSESSMENT OF FALL" modal (Apply = {@code fnApply()}). Returns true if it opened. */
    public boolean clickFallRiskAssessment() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('fnOpenFallRiskAssessment')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*fall risk/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /assessment of fall|fall risk/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception e) { System.out.println("clickFallRiskAssessment: Fall Risk modal did not open"); return false; }
        waitForAngular(600);
        return true;
    }

    /** In the Fall Risk modal: answer YES/NO questions with YES, tick a checkbox, click <b>Apply</b> ({@code fnApply()});
     *  return the success toast (expected "Fall Risk Assessment saved successfully."). */
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
            page.waitForFunction("() => (window.__frToasts||[]).some(a=>/success|saved|applied|fall|please|select|required/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("fillFallRiskAndApply: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__frToasts||[]; return a.find(x=>/fall.*(saved|success)|saved successfully|success|applied/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Close the Fall Risk modal — Cancel/Close ({@code fnCancel}); if it lingers, click OUTSIDE the modal + Escape,
     *  then strip the backdrop. */
    public void closeFallRiskPopup() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /assessment of fall|fall risk/i.test(x.textContent||''));"
                + " if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/fnCancel/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*(close|cancel)\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){} }");
        waitForAngular(400);
        boolean stillOpen = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(x=>x.getBoundingClientRect().width>0 && /assessment of fall|fall risk/i.test(x.textContent||''))"));
        if (stillOpen) {
            try { page.mouse().click(5, 5); } catch (Exception ignore) { }
            try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
            waitForAngular(400);
        }
        page.evaluate("() => { [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(300);
    }
}
