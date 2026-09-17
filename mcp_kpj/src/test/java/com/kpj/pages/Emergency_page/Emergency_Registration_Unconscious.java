package com.kpj.pages.Emergency_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Emergency &gt; <b>Emergency Registration (Unconscious)</b> (route {@code #/EmergencyRegistration}).
 *
 * <p>Flow (verified live 2026-07-10): select <b>Reg.type</b> ({@code Registration.PatientTypeID} — New/
 * Registered) → fill the mandatory details if empty (Gender is required; First Name/Prefix/Doctor are
 * filled too) → <b>Save</b> ({@code IUDRegistration();}) → toast <i>"Registration Saved Successfully &amp;
 * MRN is ED0000xx."</i> and the registration report tabs open → the <b>Consent Details</b> modal
 * auto-opens with a FIXED PDPA consent pre-selected → Save ({@code IUDconsentdetail()}) opens the
 * signable form in a new tab.</p>
 */
public class Emergency_Registration_Unconscious extends BasePage {

    public Emergency_Registration_Unconscious(Page page) { super(page); }

    // ---- navigation -------------------------------------------------------

    public void navigateTo(String baseUrl) {
        // DOMCONTENTLOADED (not full "load") — the DevHIS SPA can keep a request open on a slow site.
        try {
            page.navigate(baseUrl + "/#/EmergencyRegistration",
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(60000));
        } catch (Exception e) {
            System.out.println("EmergencyRegistration.navigateTo: navigation slow — continuing");
        }
        try {
            page.waitForFunction(
                    "() => window.angular && document.querySelector(\"button[ng-click='IUDRegistration();']\")",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("EmergencyRegistration.navigateTo: form not ready in time"); }
        waitForAngular(1500);
    }

    // ---- registration -----------------------------------------------------

    /** Select the <b>Reg.type</b> ({@code Registration.PatientTypeID}): "Registered" → 2, else "New" → 1.
     *  Fires {@code PatientTypeChange()}. Returns the chosen label. */
    public String selectRegType(String type) {
        Object r = page.evaluate("(t) => { const e=document.querySelector(\"select[ng-model='Registration.PatientTypeID']\"); if(!e) return '(no-field)';"
                + " const sc=angular.element(e).scope(); let s=sc, reg=null; for(let i=0;i<15&&s;i++){ if(!reg&&s.Registration)reg=s.Registration; s=s.$parent; }"
                + " const registered=/regist/i.test(t); const val=registered?2:1;"
                + " sc.$apply(function(){ if(reg) reg.PatientTypeID=val; if(typeof sc.PatientTypeChange==='function') sc.PatientTypeChange(); });"
                + " return registered?'Registered':'New'; }", type);
        waitForAngular(800);
        return r == null ? "" : r.toString();
    }

    /** For Reg.type=<b>Registered</b>: enter an existing MRN and click <b>SearchPatientByMRNo()</b> to load
     *  that patient's details (the MRN field enables only when PatientTypeID==2). Returns the loaded
     *  patient's First Name (empty if not found). */
    public String searchExistingByMRN(String mrn) {
        // Set Registration.MRNo on the SCOPE directly (the MRN input can still be ng-disabled for a beat
        // after switching to Registered, which drops an input-level model update) then call
        // SearchPatientByMRNo() — via the scope so it reads the freshly-set MRNo.
        page.evaluate("(m) => { const e=document.querySelector(\"input[ng-model='Registration.MRNo']\"); if(!e) return;"
                + " const c=angular.element(e).controller('ngModel'); const sc=angular.element(e).scope(); let s=sc, reg=null; for(let i=0;i<15&&s;i++){ if(!reg&&s.Registration)reg=s.Registration; s=s.$parent; }"
                + " sc.$apply(function(){ if(reg) reg.MRNo=m; }); e.value=m; if(c){c.$setViewValue(m);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " sc.$apply(function(){ if(typeof sc.SearchPatientByMRNo==='function') sc.SearchPatientByMRNo(); });"
                + " const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='SearchPatientByMRNo();' && x.offsetParent!==null); if(b) b.click(); }", mrn);
        try {
            page.waitForFunction(
                    "() => { const e=document.querySelector(\"input[ng-model='Registration.FirstName']\"); if(!e) return false; const sc=angular.element(e).scope(); let s=sc,reg=null; for(let i=0;i<15&&s;i++){ if(!reg&&s.Registration)reg=s.Registration; s=s.$parent; } return reg && reg.FirstName && ((''+reg.FirstName).trim().length>0); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { System.out.println("searchExistingByMRN: patient not loaded for MRN " + mrn); }
        waitForAngular(600);
        Object nm = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='Registration.FirstName']\"); if(!e) return ''; const sc=angular.element(e).scope(); let s=sc,reg=null; for(let i=0;i<15&&s;i++){ if(!reg&&s.Registration)reg=s.Registration; s=s.$parent; } return reg&&reg.FirstName?reg.FirstName:''; }");
        return nm == null ? "" : nm.toString().trim();
    }

    /**
     * Fill ALL Emergency Registration (Unconscious) fields — Patient Information (Prefix, Gender, First Name,
     * Age) and Visit Information (Encounter, Visit Location, Department, Doctor, Visit Type, Disaster Type). The
     * form is intentionally minimal (an unconscious walk-in has no NRIC/Nationality/NOK/Payor). Selects are set
     * to their first real option (Department is set first, then the Doctor list loads before Doctor is picked).
     * Returns a summary of the values chosen.
     */
    public String fillMandatoryIfEmpty() {
        // DSH loads the dropdown master-data SLOWER than devhis — if we fill before the options exist, setSel finds an
        // empty option list and returns "(no-opt)", leaving Gender empty → Save is rejected with "Please Select
        // Gender!". So WAIT for the mandatory selects (Gender/Prefix) to populate first.
        waitForSelectOptions("Registration.GenderID", 15000);
        waitForSelectOptions("Registration.PrefixID", 15000);
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const firstReal=sel=>[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " const hasOpts=sel=>[...sel.options].some(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                // punctuation-tolerant match ("Mr." ≈ "Mr", "Male" == "Male"): exact, else strip non-alnum + startsWith, else first real.
                + " const setSel=(ng,txt)=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return '(no)'; if(e.selectedIndex>0 && !/^-*\\s*select/i.test(norm((e.options[e.selectedIndex]||{}).textContent))) return norm(e.options[e.selectedIndex].textContent)+' (kept)';"
                + "   let i=-1; if(txt){ const w=txt.toLowerCase().replace(/[^a-z0-9]/g,''); i=[...e.options].findIndex(o=>norm(o.textContent).toLowerCase()===txt.toLowerCase()); if(i<0) i=[...e.options].findIndex(o=>{ const t=norm(o.textContent).toLowerCase().replace(/[^a-z0-9]/g,''); return o.value && t && (t===w || t.startsWith(w)); }); }"
                + "   if(i<0)i=firstReal(e); if(i<0)return '(no-opt)'; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); };"
                + " const setInp=(ng,v)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\"); if(!e) return '(no)'; if((e.value||'').trim()) return e.value+' (kept)'; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const out={};"
                + " out.Prefix=setSel('Registration.PrefixID','Mr.'); out.Gender=setSel('Registration.GenderID','Male');"
                + " out.FirstName=setInp('Registration.FirstName','EMERGENCY UNKNOWN'); out.Age=setInp('Registration.Year','30');"
                + " out.Encounter=setSel('Visit.EncounterTypeID',''); out.VisitLocation=setSel('Visit.VisitLocationID','');"
                // Department's own master data loads asynchronously off the back of Visit Location, just as
                // slowly as Gender/Prefix elsewhere on DSH — a flat 600ms wait sometimes ran out before it
                // populated, leaving Department (and so Doctor, which cascades from it) unset and Save
                // rejected with "Please Select Department First Then Doctor!". POLL for it instead.
                + " for(let k=0;k<25;k++){ await new Promise(r=>setTimeout(r,400)); const dpt=document.querySelector(\"select[ng-model='Visit.DepartmentID']\"); if(dpt && hasOpts(dpt)) break; }"
                + " out.Department=setSel('Visit.DepartmentID','');"
                // Department change loads the Doctor list asynchronously — POLL for it (DSH is slow) instead of a fixed wait.
                + " for(let k=0;k<25;k++){ await new Promise(r=>setTimeout(r,400)); const d=document.querySelector(\"select[ng-model='Visit.DoctorID']\"); if(d && hasOpts(d)) break; }"
                + " out.Doctor=setSel('Visit.DoctorID',''); out.VisitType=setSel('Visit.VisitTypeID',''); out.Disaster=setSel('Visit.DisasterTypeID','');"
                + " return 'Prefix='+out.Prefix+' | Gender='+out.Gender+' | Name='+out.FirstName+' | Age='+out.Age+' | Encounter='+out.Encounter+' | Location='+out.VisitLocation+' | Dept='+out.Department+' | Doctor='+out.Doctor+' | VisitType='+out.VisitType+' | Disaster='+out.Disaster; }");
        waitForAngular(500);
        // Belt-and-suspenders: make sure Gender actually committed (the mandatory field) — retry via the model if not.
        ensureGenderCommitted();
        String res = r == null ? "" : r.toString();
        // Name the dropdown(s) that had NOTHING to pick, so the report says why Save is later rejected.
        java.util.regex.Matcher noOpt = java.util.regex.Pattern.compile("(\\w+)=\\(no-opt\\)").matcher(res);
        java.util.List<String> empty = new java.util.ArrayList<>(); while (noOpt.find()) empty.add(noOpt.group(1));
        if (!empty.isEmpty()) com.kpj.core.Reasons.add("the " + String.join(", ", empty) + " dropdown(s) had NO options (list empty on this environment), so they could not be set" + (empty.contains("Dept") ? " - Doctor cascades from Department, which is why Save answers 'Please Select Department First Then Doctor!'" : ""));
        return res;
    }

    /** Wait until a {@code <select ng-model=ng>} has at least one REAL (non-"--Select--") option. */
    private void waitForSelectOptions(String ngModel, int timeoutMs) {
        try {
            page.waitForFunction("(ng) => { const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); return e && [...e.options].some(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').replace(/\\s+/g,' ').trim())); }",
                    ngModel, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception ignore) { System.out.println("waitForSelectOptions: " + ngModel + " options did not load within " + timeoutMs + "ms"); }
    }

    /** Verify {@code Registration.GenderID} committed (the only field the Save enforces); if not, re-select "Male"
     *  (or the first real option) and confirm — a few retries in case the options were still settling. */
    private void ensureGenderCommitted() {
        for (int a = 0; a < 5; a++) {
            boolean ok = Boolean.TRUE.equals(page.evaluate("() => { const e=document.querySelector(\"select[ng-model='Registration.GenderID']\"); const c=e?angular.element(e).controller('ngModel'):null; return !!(c && c.$modelValue!=null && c.$modelValue!=='' && e.selectedIndex>0); }"));
            if (ok) return;
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const e=document.querySelector(\"select[ng-model='Registration.GenderID']\"); if(!e) return;"
                    + " let i=[...e.options].findIndex(o=>norm(o.textContent).toLowerCase()==='male'); if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); if(i<0) return;"
                    + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(x){}} }");
            waitForAngular(500);
        }
        System.out.println("ensureGenderCommitted: Gender still not committed after retries (options may be missing on this env)");
    }

    /**
     * Save the registration and return the success toast (<i>"Registration Saved Successfully &amp; MRN is
     * ED0000xx."</i>). The app's {@code IUDRegistration()} first awaits {@code ShowPassportExpiryInfo()} which
     * returns <b>undefined</b> for this minimal (no-passport) form — so {@code if(!canContinue) return;} SILENTLY
     * aborts the save (no toast, no API call). Fix: override {@code $scope.ShowPassportExpiryInfo} to return true,
     * then call {@code IUDRegistration()} on the scope (validation already passes). Captures the toast via observer.
     */
    public String saveRegistrationAndGetToast() {
        // This screen owns its save (it must stub ShowPassportExpiryInfo), so it does NOT inherit the OP
        // RegistrationPage save path. Run the SHARED Visit Type guard here so a fix there reaches this screen too
        // — otherwise Save can answer "Please Select Visit Type!" exactly as OP Registration did.
        new com.kpj.pages.Op_page.RegistrationPage(page).ensureVisitTypeSelected();
        page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " window.__erToasts=[]; if(window.__erObs) window.__erObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=norm(el.textContent); if(t && !window.__erToasts.includes(t)) window.__erToasts.push(t); }); };"
                + " window.__erObs=new MutationObserver(grab); window.__erObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='IUDRegistration();'); if(!b) return; let sc=angular.element(b).scope(); let s=sc; for(let i=0;i<15&&s;i++){ if(typeof s.ShowPassportExpiryInfo==='function' && typeof s.IUDRegistration==='function'){ sc=s; break; } s=s.$parent; }"
                + " try{ sc.ShowPassportExpiryInfo = async function(){ return true; }; }catch(e){}"
                + " try{ await sc.IUDRegistration(); }catch(e){ console.log('IUDRegistration err '+e.message); } }");
        try {
            page.waitForFunction("() => (window.__erToasts||[]).some(a=>/saved|success|mrn|gender|select|required|enter/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            page.waitForTimeout(3000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__erToasts||[]).includes(t)) (window.__erToasts=window.__erToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__erToasts||[]; return a.find(x=>/saved successfully|mrn is/i.test(x)) || a.find(x=>/saved|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(500);
        return r == null ? "" : r.toString().trim();
    }

    // ---- consent (auto-opens after save) ----------------------------------

    /** Wait for the <b>Consent Details</b> modal that auto-opens after a successful registration. */
    public boolean waitForConsentModal() {
        try {
            page.waitForFunction(
                    "() => [...document.querySelectorAll('.modal')].some(m=>m.getBoundingClientRect().width>0 && /consent/i.test(m.textContent||'') && m.querySelector(\"button[ng-click='IUDconsentdetail()']\"))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
            waitForAngular(600);
            return true;
        } catch (Exception e) {
            System.out.println("waitForConsentModal: consent modal did not auto-open");
            try {
                Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const modals=[...document.querySelectorAll('.modal')].map(m=>({visible:m.getBoundingClientRect().width>0,"
                        + "   hasConsentText:/consent/i.test(m.textContent||''), hasSaveBtn:!!m.querySelector(\"button[ng-click='IUDconsentdetail()']\"),"
                        + "   textSample:norm(m.textContent).slice(0,150), classes:m.className }));"
                        + " const toasts=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].filter(t=>t.offsetParent!==null).map(t=>norm(t.textContent));"
                        + " return { url: location.href, modalCount: modals.length, modals, visibleToasts: toasts }; }");
                System.out.println("waitForConsentModal: DIAG => " + diag);
            } catch (Exception e2) {
                System.out.println("waitForConsentModal: DIAG evaluate also failed - " + e2.getMessage());
            }
            return false;
        }
    }

    /** Return the pre-selected consent form name in the Consent Details modal. Emergency's consent is
     *  FIXED (Personal Data Notice &amp; Consent — PDPA); the name field is {@code ng-disabled}, so no form
     *  search is needed — just Save ({@code IUDconsentdetail()}) opens the signable document tab. */
    public String getConsentName() {
        Object r = page.evaluate("() => { const m=[...document.querySelectorAll('.modal')].find(x=>x.getBoundingClientRect().width>0 && /consent/i.test(x.textContent||'')); if(!m) return '';"
                + " const sc=angular.element(m).scope(); let s=sc, c=null; for(let i=0;i<15&&s;i++){ if(!c&&s.consent)c=s.consent; s=s.$parent; } return c&&c.consentname?c.consentname:''; }");
        return r == null ? "" : r.toString().trim();
    }

    // ---- report rasterization ---------------------------------------------

    /** The registration report is a server-generated PDF (RegistrationReport.aspx); a Chromium PDF-viewer
     *  screenshot is a blank gray page. Fetch the PDF bytes in-browser (shares session cookies) and
     *  rasterize page 1 to PNG; falls back to a direct screenshot for HTML reports. */
    public byte[] captureReportPng(Page reportTab) {
        try {
            String url = reportTab.url();
            if (url != null && url.startsWith("http")) {
                Object b64 = reportTab.evaluate(
                        "async (u) => { const r = await fetch(u, {credentials:'include'});"
                                + " const buf = await r.arrayBuffer(); const bytes = new Uint8Array(buf);"
                                + " let bin=''; const chunk=0x8000;"
                                + " for (let i=0;i<bytes.length;i+=chunk) bin += String.fromCharCode.apply(null, bytes.subarray(i,i+chunk));"
                                + " return btoa(bin); }", url);
                if (b64 != null && !b64.toString().isEmpty()) {
                    byte[] pdf = java.util.Base64.getDecoder().decode(b64.toString());
                    boolean isPdf = pdf.length > 4 && pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F';
                    if (isPdf) {
                        byte[] pngFromPdf = com.kpj.core.PdfUtil.firstPageToPng(pdf);
                        if (pngFromPdf != null && pngFromPdf.length > 0) return pngFromPdf;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("captureReportPng: PDF fetch/rasterize failed (" + e.getMessage() + ")");
        }
        byte[] png = null;
        try {
            reportTab.bringToFront();
            try { reportTab.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); } catch (Exception ignore) {}
            reportTab.waitForTimeout(2500);
            png = reportTab.screenshot(new Page.ScreenshotOptions().setTimeout(12000));
        } catch (Exception e) {
            System.out.println("captureReportPng: " + e.getMessage());
        } finally {
            try { page.bringToFront(); } catch (Exception ignore) {}
        }
        return png;
    }
}
