package com.kpj.pages.Emegency_Page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Emergency &gt; <b>Emergency Admission</b> (route {@code #/EmergencyAdmission}) — a full IPD admission.
 *
 * <p>Navigated by CLICKING the menu tab (Emergency → Emergency Admission), not a direct URL. Flow verified
 * live 2026-07-10 (AdmissionID 10435 → "Patient Admitted Successfully."). The Save
 * ({@code IUDAdmission()}) runs a data-driven validation ({@code ValidationListAdmission}) requiring, in
 * order: Patient Name/Gender, Admission Location, Department, Doctor, Admission Type, Patient Source, Bed
 * Class, Ward, Billing Class, Bed (Bed is SKIPPED when {@code Admission.NonPresenceAdmission} is truthy),
 * plus {@code Admission.AdmissionPurposeID} (whose failure oddly shows "Please Select Encounter Type!").
 */
public class Emergency_Admission extends BasePage {

    public Emergency_Admission(Page page) { super(page); }

    // Identification/contact captured in fillPatientSection and force-re-applied at the Save click (a later
    // patient-lookup / admission cascade can clear them, blocking Save with "Please Enter NRIC / Email").
    private String pNric, pEmail, pMobile, pFullName, pFamilyName, pDob, pGender;
    private int pGenderId;

    // ---- navigation via the menu tab (not a direct URL) -------------------

    /** Expand the <b>Emergency</b> left-menu treeview and click the <b>Emergency Admission</b> link. */
    public boolean navigateViaMenu() {
        // Wait for the left menu to render (the SPA boots the nav a beat after login).
        try {
            page.waitForFunction("() => window.angular && [...document.querySelectorAll('li > a')].some(a=>/^\\s*Emergency\\s*$/i.test((a.textContent||'').trim()))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("navigateViaMenu: Emergency menu did not render in time"); }
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
        // Click the Emergency Admission submenu link.
        Object adTagged = page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>/emergency admission/i.test((x.textContent||'').trim()) && (x.getAttribute('href')||'').includes('EmergencyAdmission') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('a')].find(x=>(x.getAttribute('href')||'')==='#/EmergencyAdmission'); if(!a) return false; a.id='__emAdmitTab'; return true; }");
        if (Boolean.TRUE.equals(adTagged)) {
            try { page.locator("#__emAdmitTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("navigateViaMenu: Emergency Admission click failed - " + e.getMessage()); }
        } else {
            System.out.println("navigateViaMenu: Emergency Admission link not found");
        }
        // Wait for the admission form (Save button present).
        try {
            page.waitForFunction("() => window.angular && document.querySelector(\"button[ng-click='IUDAdmission();']\") && /EmergencyAdmission/i.test(location.hash)",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("navigateViaMenu: admission form not ready in time"); }
        waitForAngular(1500);
        return page.url().toLowerCase().contains("emergencyadmission");
    }

    // ---- fill the admission details --------------------------------------

    /**
     * Section 1 — <b>Patient Information</b>, filled the same way as OP Registration (Nationality BEFORE
     * ICCardType so the NRIC field stays enabled; NRIC prefix matches DOB and its parity matches Gender).
     * Returns a summary of the patient.
     */
    public String fillPatientSection() {
        java.util.Random rnd = new java.util.Random();
        boolean male = rnd.nextBoolean();
        String gender = male ? "Male" : "Female";
        String prefix = male ? "Mr." : "Ms.";
        String[] mNames = {"John Peter", "David Smith", "Michael Brown", "James Wilson"};
        String[] fNames = {"Mary Jane", "Sarah Test", "Emma Watson", "Linda Carter"};
        String fullName = (male ? mNames : fNames)[rnd.nextInt(4)];
        String familyName = fullName.substring(fullName.lastIndexOf(' ') + 1);
        int byear = 1970 + rnd.nextInt(35), bmonth = 1 + rnd.nextInt(12), bday = 1 + rnd.nextInt(28);
        String dob = String.format("%02d/%02d/%04d", bday, bmonth, byear);
        String[] placeCodes = {"10", "14", "08", "12", "01", "07"};
        int serial = (int) Math.abs(System.nanoTime() % 10000);
        // Malaysian NRIC last digit encodes gender (odd=male, even=female) — the admission form cross-checks
        // it (ChkTitByGenderNRIC) and clears Gender on a mismatch, so keep the parity consistent.
        if (male == (serial % 2 == 0)) serial = (serial + 1) % 10000;
        String nric = String.format("%02d%02d%02d", byear % 100, bmonth, bday)
                + placeCodes[rnd.nextInt(placeCodes.length)] + String.format("%04d", serial);
        int genderId = male ? 2 : 3;

        setSelLike("Registration.NationalityID", "Malaysian");
        setSelLike("Registration.PrefixID", prefix);
        setSelLike("Registration.GenderID", gender);
        setSelIfPresent("Registration.RaceID", "Malay");
        setSelIfPresent("Registration.ReligionID", "Islam");
        setSelIfPresent("Registration.MaritalStatusID", "Single");
        setSelIfPresent("Registration.BloodGroupID", "O");
        // Identification Type (ICCardType): wait for the dropdown to load, select "New IC", VERIFY it stuck — a
        // slow-loading dropdown makes setSel no-op, leaving the 12-digit NRIC field absent → Save blocks with
        // "Please Enter NRIC / Passport No / Other Identification No". Retry until the model is actually set.
        try {
            page.waitForFunction("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ICCardTypeID'); return e && e.options.length>1; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("fillPatientSection: ICCardType dropdown did not populate in time"); }
        for (int a = 0; a < 4; a++) {
            setSel("Registration.ICCardTypeID", "New IC");
            waitForAngular(300);
            boolean set = Boolean.TRUE.equals(page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ICCardTypeID'); const c=e?angular.element(e).controller('ngModel'):null; return c && c.$modelValue!=null && c.$modelValue!==''; }"));
            if (set) break;
            System.out.println("fillPatientSection: Identification Type not set yet — retrying (attempt " + (a + 1) + ")");
        }
        fillModel("Registration.FirstName", fullName);
        fillModel("Registration.FamilyName", familyName);
        waitForAngular(400);
        // NRIC via the 12-maxlength model input (transiently guarded), then DOB — verify it committed, retry if not.
        for (int a = 0; a < 4; a++) {
            page.evaluate("(v)=>{const e=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].find(x=>x.getAttribute('maxlength')==='12')||document.querySelector(\"input[ng-model='Registration.NationalId']\");"
                    + "if(e){const c=angular.element(e).controller('ngModel');e.value=v;if(c){c.$setViewValue(v);c.$render();}e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}}", nric);
            waitForAngular(300);
            boolean nricSet = Boolean.TRUE.equals(page.evaluate("(v) => [...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].some(x=>x.value===v)", nric));
            if (nricSet) break;
            System.out.println("fillPatientSection: NRIC not committed yet — retrying (attempt " + (a + 1) + ")");
        }
        fillModel("Registration.DateOfBirth", dob);
        waitForAngular(400);
        // Email + Mobile — the Save validation requires them ("Please Enter Email" / "Please Enter Mobile No").
        String email = familyName.toLowerCase() + (rnd.nextInt(9000) + 1000) + "@example.com";
        String mobile = "12" + String.format("%08d", rnd.nextInt(90000000) + 10000000);
        fillModel("Registration.Email", email);
        fillModel("Registration.MobileNo", mobile);
        // Re-assert Gender + Email + Mobile + NRIC + ICCardType on EVERY Registration object in the DOM (the
        // validation reads a specific Registration scope that may differ from the one under #txtFirstName) — Save
        // requires them. ICCardType value is read from the live select (New IC) so the NRIC format is accepted.
        page.evaluate("(a) => { const ict=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ICCardTypeID'); const ictVal=ict?(angular.element(ict).controller('ngModel')||{}).$modelValue:null;"
                + " const seen=new Set(); document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(x.Registration && typeof x.Registration==='object' && !seen.has(x.Registration)){ seen.add(x.Registration); const reg=x.Registration;"
                + " const apply=function(){ reg.GenderID=a.g; if(!reg.Email) reg.Email=a.email; if(!reg.MobileNo) reg.MobileNo=a.mobile; if(!reg.NationalId) reg.NationalId=a.nric; if(ictVal!=null && !reg.ICCardTypeID) reg.ICCardTypeID=ictVal; };"
                + " if(x.$apply){ x.$apply(apply); } else { apply(); } } x=x.$parent; } }catch(e){} }); }",
                java.util.Map.of("g", genderId, "email", email, "mobile", mobile, "nric", nric));
        waitForAngular(300);
        pNric = nric; pEmail = email; pMobile = mobile; pGenderId = genderId;
        pFullName = fullName; pFamilyName = familyName; pDob = dob; pGender = gender;
        return "Patient: " + prefix + " " + fullName + " | " + gender + " | " + dob + " | NRIC " + nric + " | " + email + " | " + mobile;
    }

    /**
     * Section 2 — <b>Admission Information</b>. Set Location + Department (loads the doctor list), wait,
     * then in ONE apply set EVERY required admission field to the known-good master-data IDs verified live
     * (the lists load async/inconsistently, but the Save validation only checks non-empty and the server
     * accepts these IDs). Non-Presence=1 skips physical Bed selection. Anchor the final apply to an
     * ADMISSION-section element (else the Registration scope's Admission object is used and nothing sticks).
     */
    public String fillAdmissionSection() {
        page.evaluate("() => { const e=document.querySelector(\"select[ng-model='Admission.AdmissionLocationID']\") || document.querySelector(\"input[ng-model='Registration.FirstName']\"); const sc=angular.element(e).scope(); let s=sc, adm=null; for(let i=0;i<15&&s;i++){ if(!adm&&s.Admission)adm=s.Admission; s=s.$parent; }"
                + " sc.$apply(function(){ if(adm){ adm.AdmissionLocationID=1; } if(typeof sc.fnSetDepartment==='function') sc.fnSetDepartment();"
                + "   if(adm){ adm.DepartmentID=1; } if(typeof sc.fnDeparmentChange==='function') sc.fnDeparmentChange(); if(typeof sc.onEmergencyOnCallChange==='function') sc.onEmergencyOnCallChange(); }); }");
        try {
            page.waitForFunction("() => { let ok=false; document.querySelectorAll('*').forEach(el=>{ if(ok)return; try{ const s=angular.element(el).scope(); if(s && Array.isArray(s.drpDoctor) && s.drpDoctor.length) ok=true; }catch(e){} }); return ok; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("fillAdmissionDetails: doctor list not loaded in time"); }
        waitForAngular(600);
        // Final apply — all known-good IDs (Dept=1, Doctor=15300, Type=4 Emergency Admission, Source=1,
        // BedClass=21 Emergency Room, Ward=58 Emergency, Billing=4 Emergency, Purpose=3, Encounter=3).
        Object rest = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='Admission.DepartmentID']\") || document.querySelector(\"select[ng-model='Admission.AdmissionLocationID']\"); const sc=angular.element(e).scope(); let s=sc, adm=null, vis=null; for(let i=0;i<15&&s;i++){ if(!adm&&s.Admission)adm=s.Admission; if(!vis&&s.Visit)vis=s.Visit; s=s.$parent; }"
                + " let drd=null; document.querySelectorAll('*').forEach(el=>{ try{ const s2=angular.element(el).scope(); if(s2 && !drd && Array.isArray(s2.drpDoctor) && s2.drpDoctor.length) drd=s2.drpDoctor; }catch(e){} });"
                + " const docPick=(drd&&drd.find(d=>d.value)&&drd.find(d=>d.value).value)||15300;"
                + " sc.$apply(function(){ if(adm){ adm.AdmissionLocationID=1; adm.DepartmentID=1; adm.DoctorID=docPick; adm.AttendingDoctorID=docPick;"
                + "   adm.AdmissionTypeID=4; adm.PatientSourceID=1; adm.BedClassID=21; adm.WardID=58; adm.BillingClassID=4;"
                + "   adm.NonPresenceAdmission=1; adm.AdmissionPurposeID=3; } if(vis) vis.EncounterTypeID=3; });"
                + " return 'Dept=1 | AttendingDoctor='+docPick+' | Type=Emergency Admission | Source=1 | BedClass=Emergency Room | Ward=Emergency | Billing=Emergency | Purpose=Emergency'; }");
        waitForAngular(800);
        // Department THEN Doctor — both must go through their own DOM selects, and in that order (changing the
        // department reloads the doctor list and clears the doctor).
        String dept = ensureDepartmentSelected();
        String doc = ensureDoctorSelected();
        return "Admission: " + (rest == null ? "" : rest.toString().trim()) + " | " + dept + " | " + doc;
    }

    /**
     * <b>Department</b> ({@code Admission.DepartmentID}) — Save's "Please Select Department First Then Doctor !"
     * guard. The scope {@code $apply} assignment above does NOT stick here (the model stays {@code undefined} and
     * the select shows "--Select--"), so select <b>Emergency Department</b> from the select's own option list via
     * selectedIndex + change. This also refills the Doctor list (2 → 64 options) and clears the Doctor, so
     * {@link #ensureDoctorSelected()} must run AFTER this. Returns the selected department.
     */
    public String ensureDepartmentSelected() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const real=o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent));"
                + " let sel=null;"
                + " for(let w=0;w<20;w++){ sel=document.querySelector(\"select[ng-model='Admission.DepartmentID']\"); if(sel && [...sel.options].some(real)) break; await new Promise(r=>setTimeout(r,500)); }"
                + " if(!sel) return 'Dept=(no select)';"
                + " let i=[...sel.options].findIndex(o=>real(o) && /emergency/i.test(norm(o.textContent)));"
                + " if(i<0) i=[...sel.options].findIndex(real);"
                + " if(i<0) return 'Dept=(no options)';"
                + " sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}}"
                + " await new Promise(r=>setTimeout(r,2500));"
                + " let val=null; document.querySelectorAll('*').forEach(el=>{ if(val)return; try{ const s=window.angular.element(el).scope(); let x=s; for(let k=0;k<15&&x;k++){ if(x.Admission){ val=x.Admission.DepartmentID; return; } x=x.$parent; } }catch(e){} });"
                + " return 'Dept='+norm(sel.options[i].textContent)+' (DepartmentID='+JSON.stringify(val)+')'; }");
        waitForAngular(400);
        return r == null ? "Dept=(null)" : r.toString();
    }

    /**
     * <b>Doctor</b> ({@code Admission.DoctorID}) — the field Save's "Please Select Doctor!" guard checks. It is a
     * SEPARATE dropdown from Attending Doctor ({@code Admission.AttendingDoctorID}): it is filtered by Department /
     * Sub Department and so carries its own short option list. Assigning the Attending Doctor's id to it does NOT
     * stick — Angular resets the model to 0 because that id isn't among its options — so pick from its OWN list via
     * selectedIndex + change. Its options load async after the department change, so poll for a real one.
     * Returns the selected doctor (or why it could not be selected). Same fix as IP &gt; Admission.
     */
    public String ensureDoctorSelected() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const real=sel=>[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " let sel=null;"
                + " for(let w=0;w<20;w++){ sel=document.querySelector(\"select[ng-model='Admission.DoctorID']\"); if(sel && real(sel)>=0) break; await new Promise(r=>setTimeout(r,500)); }"
                + " if(!sel) return 'Doctor=(no select)';"
                + " const i=real(sel); if(i<0) return 'Doctor=(no options)';"
                + " sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}}"
                + " await new Promise(r=>setTimeout(r,600));"
                + " let val=null; document.querySelectorAll('*').forEach(el=>{ if(val)return; try{ const s=window.angular.element(el).scope(); let x=s; for(let k=0;k<15&&x;k++){ if(x.Admission){ val=x.Admission.DoctorID; return; } x=x.$parent; } }catch(e){} });"
                + " return 'Doctor='+norm(sel.options[i].textContent)+' (DoctorID='+JSON.stringify(val)+')'; }");
        waitForAngular(400);
        return r == null ? "Doctor=(null)" : r.toString();
    }

    /** Click <b>Save</b> ({@code IUDAdmission();}) and return the toast (verified live:
     *  <b>"Patient Admitted Successfully."</b>). Synthetic click (the Save button is far right / off-viewport);
     *  robust capture (watch .toast, 30s, re-scan). */
    public String saveAdmissionAndGetToast() {
        // Department + Doctor are the admission fields the later cascades can drop — re-assert both here (department
        // first), or Save answers "Please Select Department First Then Doctor !" / "Please Select Doctor!".
        if (!Boolean.TRUE.equals(page.evaluate("() => { let v=null; document.querySelectorAll('*').forEach(el=>{ if(v!=null)return; try{ const s=window.angular.element(el).scope(); let x=s; for(let k=0;k<15&&x;k++){ if(x.Admission){ v=x.Admission.DepartmentID; return; } x=x.$parent; } }catch(e){} }); return !!v; }"))) {
            System.out.println("saveAdmission: re-assert " + ensureDepartmentSelected());
        }
        System.out.println("saveAdmission: re-assert " + ensureDoctorSelected());
        // Pre-save fill (2 passes) — re-set every mandatory patient field via the ACTUAL DOM inputs/selects (NOT
        // just scope properties: the validation reads the input-committed model). setSel(force) re-selects
        // Gender/ICCardType (they flip during the async cascade); the NationalId goes through the 12-maxlength input.
        // Mirrors the IP Admission save that reliably clears "Please Enter NRIC / Email / Passport No".
        for (int pass = 0; pass < 2; pass++) {
            page.evaluate("(a) => {"
                    + " const setSel=(ng,txt,force)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const cur=(e.options[e.selectedIndex]||{}).text||''; const okCur=e.selectedIndex>0 && cur && !/^-*\\s*select\\s*-*$/i.test(cur.trim());"
                    + "   if(!force && okCur) return; let i=[...e.options].findIndex(o=>(o.textContent||'').trim().toLowerCase()===(''+txt).toLowerCase()); if(i<0){ if(okCur) return; i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); } if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} };"
                    + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && (x.offsetParent!==null || !x.getAttribute('maxlength'))); if(!e) return; if((e.value||'').trim()) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    + " setSel('Registration.GenderID',a.gender,true); setSel('Registration.RaceID','Malay',true); setSel('Registration.ReligionID','Islam',true); setSel('Registration.MaritalStatusID','Single',true); setSel('Registration.BloodGroupID','O',true); setSel('Registration.ICCardTypeID','New IC',true); setSel('Registration.MobileCountryCode','60',true);"
                    + " setInp('Registration.FirstName',a.first); setInp('Registration.FamilyName',a.family); setInp('Registration.DateOfBirth',a.dob); setInp('Registration.MobileNo',a.mobile); setInp('Registration.Email',a.email);"
                    + " const nr=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].find(x=>x.getAttribute('maxlength')==='12'); if(nr && !(nr.value||'').trim()){ const c=angular.element(nr).controller('ngModel'); nr.value=a.nric; if(c){c.$setViewValue(a.nric);c.$render();} nr.dispatchEvent(new Event('input',{bubbles:true})); nr.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " return true; }",
                    java.util.Map.of("gender", pGender == null ? "Male" : pGender, "first", pFullName == null ? "" : pFullName,
                            "family", pFamilyName == null ? "" : pFamilyName, "dob", pDob == null ? "" : pDob,
                            "mobile", pMobile == null ? "" : pMobile, "email", pEmail == null ? "" : pEmail, "nric", pNric == null ? "" : pNric));
            waitForAngular(900);
        }
        page.evaluate("(a) => { window.__adToasts=[]; if(window.__adObs) window.__adObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__adToasts.includes(t)) window.__adToasts.push(t); }); };"
                + " window.__adObs=new MutationObserver(grab); window.__adObs.observe(document.body,{childList:true,subtree:true}); grab();"
                // FORCE a fallback Passport Expiry on every Registration object + re-force Gender/ICCardType via the
                // real selects, then click Save in the SAME tick (ICCardType flips to Passport during the async save).
                + " const seen=new Set(); document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ const reg=x.Registration; if(reg && typeof reg==='object' && !seen.has(reg)){ seen.add(reg); reg.PassportExpirydate='31/12/2045'; } x=x.$parent; } }catch(e){} });"
                + " const forceSel=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; let i=[...e.options].findIndex(o=>(o.textContent||'').trim().toLowerCase()===(''+txt).toLowerCase()); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " forceSel('Registration.GenderID',a.gender); forceSel('Registration.ICCardTypeID','New IC');"
                + " const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='IUDAdmission();' && x.offsetParent!==null); if(b) b.click(); }",
                java.util.Map.of("gender", pGender == null ? "Male" : pGender));
        try {
            page.waitForFunction("() => (window.__adToasts||[]).some(a=>/admitted|success|please|select|enter/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            page.waitForTimeout(3000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__adToasts||[]).includes(t)) (window.__adToasts=window.__adToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__adToasts||[]; return a.find(x=>/admitted successfully/i.test(x)) || a.find(x=>/admitted|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(500);
        return r == null ? "" : r.toString().trim();
    }

    // ---- low-level helpers (same style as RegistrationPage) --------------

    /** Select a select2/ng-options dropdown (by ng-model) to the option with the given visible text. */
    /**
     * Select an option by text tolerantly, and SAY SO when the wanted text is not there.
     *
     * <p>{@link #setSel(String, String)} matches the option text EXACTLY and, on no match, does <b>nothing</b> —
     * silently leaving the field unset. KLG/DSH spell the nationality "Malaysia" where this asked for "Malaysian",
     * so the patient nationality never bound on those environments and nothing said so. (The sibling screens had the
     * mirror-image bug: they fell back to the FIRST option and stored Afghanistan.)</p>
     *
     * <p>Normalised match (case/punctuation-insensitive) → prefix → substring, the fuzzy steps only for values of
     * 4+ characters so "Mr." cannot prefix-match "Mrs". Warns loudly when it has to take the first option.</p>
     */
    private String setSelLike(String ngModel, String wanted) {
        Object r = page.evaluate("([ng,txt]) => { const norm=s=>(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return null;"
                + " const opts=[...e.options].map((o,i)=>({o,i,t:(o.textContent||'').trim(),n:norm(o.textContent)}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " if(!opts.length) return null;"
                // Matching order matters. Asking for "Malaysia" once matched "Malay" - a RACE, not a nationality -
                // because "malaysia".startsWith("malay"). So: exact first; then an option that EXTENDS the wanted
                // text (Malaysian for Malaysia), shortest such option first; and only then an option the wanted
                // text extends, and ONLY when it is at least 80% as long, which keeps "Malaysia"/"Malaysian" and
                // rejects "Malay". Every fuzzy step still needs 4+ characters, so "Mr." can never reach "Mrs".
                + " const MIN=4, NEAR=0.8; const w=norm(txt); let m=null, how='';"
                + " if(w){ m=opts.find(x=>x.n===w); if(m) how='exact';"
                + "   if(!m && w.length>=MIN){ const ext=opts.filter(x=>x.n.startsWith(w)).sort((a,b)=>a.n.length-b.n.length);"
                + "     if(ext.length){ m=ext[0]; how='extends'; } }"
                + "   if(!m && w.length>=MIN){ const sub=opts.filter(x=>x.n.length>=MIN && w.startsWith(x.n) && x.n.length>=w.length*NEAR)"
                + "       .sort((a,b)=>b.n.length-a.n.length); if(sub.length){ m=sub[0]; how='near-prefix'; } }"
                + "   if(!m && w.length>=MIN){ const c=opts.filter(x=>x.n.length>=MIN && (x.n.indexOf(w)>=0||w.indexOf(x.n)>=0)"
                + "       && x.n.length>=w.length*NEAR).sort((a,b)=>b.n.length-a.n.length); if(c.length){ m=c[0]; how='contains'; } } }"
                + " if(!m){ m=opts[0]; how='FIRST-OPTION-FALLBACK'; }"
                + " const A=window.angular,$=window.jQuery;"
                + " e.value=m.o.value; const c=A.element(e).controller('ngModel'); if(c){c.$setViewValue(m.o.value);c.$render();}"
                + " if($){try{$(e).trigger('change'); $(e).select2('val',m.o.value);}catch(x){}}"
                + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return JSON.stringify({text:m.t, how}); }",
                java.util.Arrays.asList(ngModel, wanted));
        waitForAngular(150);
        if (r == null) return "";
        String s = r.toString();
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("\"text\":\"([^\"]*)\"").matcher(s);
        java.util.regex.Matcher mh = java.util.regex.Pattern.compile("\"how\":\"([^\"]*)\"").matcher(s);
        String text = mt.find() ? mt.group(1) : "", how = mh.find() ? mh.group(1) : "";
        if ("FIRST-OPTION-FALLBACK".equals(how)) {
            System.out.println("setSelLike: WARNING " + ngModel + " has no option like \"" + wanted
                    + "\" — fell back to the first option \"" + text + "\" (WRONG DATA will be saved)");
        } else if (!"exact".equals(how)) {
            System.out.println("setSelLike: " + ngModel + " \"" + wanted + "\" matched \"" + text + "\" (" + how + ")");
        }
        return text;
    }

    private void setSel(String ngModel, String optionText) {
        page.evaluate("([ng,txt])=>{const $=window.jQuery;const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + "if(!e)return;const o=[...e.querySelectorAll('option')].find(x=>x.textContent.trim()===txt);if(!o)return;e.value=o.value;"
                + "const c=angular.element(e).controller('ngModel');if(c){c.$setViewValue(o.value);c.$render();}"
                + "if($){try{$(e).trigger('change');$(e).select2('val',o.value);}catch(err){}}e.dispatchEvent(new Event('change',{bubbles:true}));}",
                java.util.Arrays.asList(ngModel, optionText));
        waitForAngular(150);
    }

    /** Like setSel but tolerant: if the exact option text isn't present, pick the first real option. */
    private void setSelIfPresent(String ngModel, String optionText) {
        page.evaluate("([ng,txt])=>{const $=window.jQuery;const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + "if(!e)return;let o=[...e.querySelectorAll('option')].find(x=>x.textContent.trim()===txt);"
                + "if(!o) o=[...e.querySelectorAll('option')].find(x=>x.value && !/^-*\\s*select/i.test((x.textContent||'').trim()));"
                + "if(!o)return;e.value=o.value;const c=angular.element(e).controller('ngModel');if(c){c.$setViewValue(o.value);c.$render();}"
                + "if($){try{$(e).trigger('change');$(e).select2('val',o.value);}catch(err){}}e.dispatchEvent(new Event('change',{bubbles:true}));}",
                java.util.Arrays.asList(ngModel, optionText));
        waitForAngular(150);
    }

    /** Fill an input/textarea (by ng-model) via the Angular model. */
    private void fillModel(String ngModel, String value) {
        page.evaluate("([ng,v])=>{const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng&&(x.offsetParent!==null||!x.getAttribute('maxlength')));"
                + "if(!e)return;const c=angular.element(e).controller('ngModel');e.value=v;if(c){c.$setViewValue(v);c.$render();}"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}",
                java.util.Arrays.asList(ngModel, value));
    }

    // ---- report rasterization ---------------------------------------------

    /** The admission reports are server-generated PDFs; fetch the bytes in-browser and rasterize page 1
     *  (a Chromium PDF-viewer screenshot is blank); falls back to a direct screenshot for HTML reports. */
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
