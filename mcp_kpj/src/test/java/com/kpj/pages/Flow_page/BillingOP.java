package com.kpj.pages.Flow_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Op_page.OutPatientQueueManagementPage;
import com.kpj.pages.Op_page.RegistrationPage;
import com.kpj.pages.Op_page.RegistrationPage.PatientProfile;
import com.kpj.pages.Op_page.RegistrationPage.SaveOutcome;
import com.microsoft.playwright.Page;

import java.util.List;
import java.util.Map;

/**
 * <b>Billing OP flow</b> (workflow document "Billing Module_OP Flow", Part C, stages C1-C6) — screen-level actions
 * for the screens the flow touches that have NO page object of their own yet. Everything else is delegated to the
 * existing page objects, and the test class ({@code tests.Flow_page.BillingOP}) owns the sequence and reporting:
 *
 * <pre>
 *  C1 OP &gt; Registration                      -> RegistrationPage (reused)  + {@link #lookupMrnByName}
 *  C2 OP &gt; Outpatient Queue Mgmt &gt; Action &gt; EMR -> OutPatientQueueManagementPage (reused: search, Fall Risk)
 *                                               + {@link #openEmrFromQueue}, EMR methods below
 *  C3 Investigation &gt; Radiology/Lab &gt; Work Order List, Inventory &gt; POS &gt; Dispense Process   -> NEW here
 *  C4 Billing &gt; OPD Charges                   -> OpdCharges (reused)
 *  C5 Billing &gt; OPD Bill (Final)              -> OpdBill (reused for patient load / cost centres) + {@link #saveFinalBill}
 *  C6 Billing &gt; Payment Settlement &gt; Self    -> NEW here
 * </pre>
 *
 * <p>Locators were pinned against the live NHIS Test screens (nhistest.sancyberhad.com, 05/10/2026, read-only
 * inspection): menu routes {@code #/DoctorWorkOrderR}, {@code #/DoctorWorkOrderL}, {@code #/MedicationOrder},
 * {@code #/SelfPyamentSettlement}, {@code #/NursingEMR}; ng-models/ng-clicks quoted at each method. The WRITE paths
 * (EMR service/drug pick, work-order Accept dialogs, screening, payment window) could not be exercised without
 * creating data, so each reports a control dump on failure ({@link #lastDump}) to make the first-run healing a
 * one-pass job.</p>
 *
 * <p><b>Findings vs the workflow document</b> (kept here so the report explains them):
 * the queue's <i>EMR</i> action is gated by a mandatory <b>Fall Risk Assessment</b> (not in the document); in EMR
 * the Tag is <i>Normal EMR</i> and an Investigation row only reaches the Work Order List when its
 * <i>Finalize - sent to the Work Order List</i> box is ticked (the document says "Add"); both Work Order List
 * screens are the same screen shape (PathologyWorkOrder.*, Accept = {@code addsampleAccept()}).</p>
 */
public class BillingOP extends BasePage {

    /** The tab this page object drives. Shadows BasePage.page so EMR steps can run in the EMR tab the app opens, then switch back. */
    private Page page;
    private final Page mainPage;

    public BillingOP(Page page) { super(page); this.page = page; this.mainPage = page; }

    /** The tab currently being driven (the EMR tab during Stage 2) - what screenshots should capture. */
    public Page workPage() { return page; }

    /** Close the EMR tab (if one is being driven) and go back to the main tab. */
    public void backToMainTab() {
        if (page != mainPage) {
            try { if (!page.isClosed()) page.close(); } catch (Exception ignore) { }
            page = mainPage;
        }
        try { mainPage.bringToFront(); } catch (Exception ignore) { }
        waitForAngular(800);
    }

    /** Last control/dialog dump taken on a failure — appended to the step's Actual text by the test class. */
    public String lastDump = "";
    public String lastOrderNote = "";
    public String lastToast = "";
    public String lastBillNo = "";
    public String lastNetAmount = "";
    public String lastPaymentNote = "";
    public String lastFallRiskNote = "";

    /** The services ordered in EMR (name as picked by the screen) — checked later against Charges / Bill. */
    public final List<String> orderedServices = new java.util.ArrayList<>();

    // ---- shared JS ------------------------------------------------------------------------------------------

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && (e.offsetParent!==null || (e.getClientRects && e.getClientRects().length>0));"
          + "const ngEl=(m)=>[...document.querySelectorAll('[ng-model]')].filter(e=>e.getAttribute('ng-model')===m);"
          + "const setEl=(e,v)=>{ if(!e) return false; e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return true; };"
          + "const setSel=(e,re)=>{ if(!e) return ''; const o=[...e.options].find(x=>re.test(x.text)&&x.value!==''&&!/^-*\\s*select/i.test(x.text.trim())); if(!o) return '';"
          + "  e.value=o.value; try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(o.value); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('change',{bubbles:true})); return o.text.trim(); };"
          + "const gridScope=()=>{ let g=null; document.querySelectorAll('*').forEach(el=>{ if(g) return; try{ const s=angular.element(el).scope();"
          + "  if(s&&s.grid&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length) g=s; }catch(e){} }); return g; };";

    /** Start recording toasts (the app's toastr fades in ~1s, so poll-and-read is too late). */
    public void watchToasts() {
        page.evaluate("() => { window.__bopToasts=[]; if(window.__bopObs) window.__bopObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('[class*=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                + "   if(t && !window.__bopToasts.includes(t)) window.__bopToasts.push(t); }); };"
                + " window.__bopObs=new MutationObserver(grab); window.__bopObs.observe(document.body,{childList:true,subtree:true,characterData:true}); }");
    }

    public String toasts() {
        Object t = page.evaluate("() => (window.__bopToasts||[]).join(' | ')");
        lastToast = t == null ? "" : t.toString().trim();
        return lastToast;
    }

    private static boolean looksLikeError(String toast) {
        String t = toast == null ? "" : toast.toLowerCase();
        return t.contains("error") || t.contains("fail") || t.contains("please ") || t.contains("mandatory") || t.contains("not allowed");
    }

    // ---- menu navigation (the app prompts to reload/log out on a raw hash change, so click the menu) -----------

    /** Open a screen the way a user does: top menu, then the submenu link whose href is {@code hashRoute}. */
    public boolean openScreen(String topMenu, String hashRoute, String routeFragment) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        // The submenu is rendered after its top menu is clicked, and slowly right after login: retry, waiting for the link itself.
        for (int attempt = 0; attempt < 3; attempt++) {
            page.evaluate("(m) => { const a=[...document.querySelectorAll('a')].find(x=>x.textContent.replace(/\\s+/g,' ').trim()===m && x.getAttribute('href')==='#'); if(a) a.click(); }", topMenu);
            try {
                page.waitForFunction("(h) => !!document.querySelector('a[href=\"' + h + '\"]')", hashRoute, new Page.WaitForFunctionOptions().setTimeout(6000));
            } catch (Exception ignore) { System.out.println("openScreen: link " + hashRoute + " not present after clicking " + topMenu + " (attempt " + (attempt + 1) + ")"); }
            page.evaluate("(h) => { const a=document.querySelector('a[href=\"' + h + '\"]'); if(a) a.click(); }", hashRoute);
            waitForAngular(3000);
            if (page.url().toLowerCase().contains(routeFragment.toLowerCase())) return true;
        }
        // Direct hash as a last resort; a "Reload page / you will be logged out" prompt may appear - answer No.
        try { page.evaluate("(h) => { window.location.hash = h; }", hashRoute); } catch (Exception ignore) { }
        waitForAngular(1500);
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/^no$/i.test((x.innerText||'').trim()) && x.offsetParent!==null"
                + " && /reload the page/i.test((x.closest('.modal,.ng-confirm-box,.jconfirm,.sweet-alert')||document.body).innerText||'')); if(b) b.click(); }");
        waitForAngular(2500);
        return page.url().toLowerCase().contains(routeFragment.toLowerCase());
    }

    // ============================================================================================================
    //  C1  Registration  (RegistrationPage is reused; this wraps the exact sequence of RegistrationTest)
    // ============================================================================================================

    /**
     * Run the whole OP registration for a new random Malaysian patient on Self Pay and Save it. Reuses every
     * {@link RegistrationPage} fill method in the same order as {@code RegistrationTest}.
     *
     * @return null on success, else the reason registration did not complete
     */
    public String registerNewSelfPayPatient(RegistrationPage reg, String baseUrl, PatientProfile p) {
        // Reach the form by menu first: on nhistest a raw hash change raises "Reload page - you will be logged out".
        openScreen("OP", "#/VisitScreen", "VisitScreen");
        if (!reg.open(baseUrl)) return "Registration form / dropdown master-data did not load";
        reg.fillPatientInformation(p);
        reg.fillCorrespondence(p);
        reg.fillOtherDetails();
        boolean kinAdded = reg.addNextOfKin(p);
        if (!kinAdded) {
            // devhis / nhistest require the Next of Kin NRIC, Family Name and TIN before Add ("TIN for Next of Kin is mandatory!");
            // RegistrationPage fills only the NRIC and clears Family Name (it treats that control as a passport). The blocked
            // Add leaves the form populated: fill the three fields and click Add again.
            for (int i = 0; i < 3 && !kinAdded; i++) {
                fillKinMandatory(p);
                page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/AddKinDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
                waitForAngular(1800);
                kinAdded = reg.kinGridRowCount() > 0;
                System.out.println("registerNewSelfPayPatient: kin Add with NRIC/Family Name/TIN (attempt " + (i + 1) + ") -> rows " + reg.kinGridRowCount());
            }
        }
        if (!kinAdded) return "Next of Kin was not added to the NOK grid" + (reg.lastKinFailReason.isEmpty() ? "" : " - " + reg.lastKinFailReason);
        boolean payorSelf = reg.selectPayorSelf();
        for (int i = 0; i < 3 && !payorSelf; i++) {      // the Payor Mode master-data can arrive late on this environment
            waitForAngular(3000);
            payorSelf = reg.selectPayorSelf();
        }
        if (!payorSelf) return "Payor = Self could not be selected - " + reg.lastPayorState;
        // Self Pay: Pricing Policy has no options and is filled by the app itself for Insurance/Company payors, and Payor Mode/Status
        // come from selectPayorSelf() - so nothing to fill here (fillPayorSmart() is kept for a future Insurance/Company flow).
        reg.fillVisitInformation();
        if (!reg.visitInformationComplete()) {
            // The shared page picks a Department at random and walks Sub Departments looking for a doctor, which wastes minutes and
            // often ends with an empty Sub Dept / Doctor / Visit Type. Fall back to a combination known to have values in every
            // connected dropdown (override with -Dbillingop.dept / .doctor / .visittype).
            System.out.println("registerNewSelfPayPatient: Visit Information incomplete after the shared fill (" + reg.lastVisitState + ") - applying the known-good Department / Sub Dept / Doctor / Visit Type");
            System.out.println("registerNewSelfPayPatient: known-good visit -> " + applyKnownGoodVisit());
        }
        if (!reg.visitInformationComplete() && !visitModelComplete())
            return "Visit Information incomplete - " + reg.lastVisitState;
        // devhis marks "Other Identification No." (Registration.ReferenceNo) mandatory and RegistrationPage does not fill it.
        final String otherId = "OID" + (100000 + new java.util.Random().nextInt(900000));
        fillOtherIdentification(otherId);
        refillIfCleared(p, otherId);              // anything a late list reload wiped is put back before Save
        fillProfession();                         // devhis flags "Profession*" (Registration.Profession) as mandatory
        fillCashCounter();                        // devhis flags "Cash Counter*" (Visit Information) when it is left empty
        fillRegistrationTime();                   // devhis shows "11:30 AM": the 24-hour value written by RegistrationPage is flagged invalid
        String blocked = reg.clickSaveAwaitConfirm(p);
        if (blocked != null && blocked.toLowerCase().contains("add")) {
            // "Please click Add to include the NOK/Guarantor details!": the kin form is filled but not committed - Add it, then Save again
            page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/AddKinDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(1500);
            fillOtherIdentification(otherId);
            blocked = reg.clickSaveAwaitConfirm(p);
        }
        if (blocked != null && (blocked + " " + reg.lastMandatoryFields).contains("ReferenceNo")) {
            fillOtherIdentification(otherId);             // the screen still flags it: set it again and retry Save once
            blocked = reg.clickSaveAwaitConfirm(p);
        }
        if (blocked != null) {
            Object inv = page.evaluate("() => { const st=e=>{ const cs=getComputedStyle(e); return (e.getAttribute('ng-model')||e.id||e.name||e.tagName)+'{'+(e.offsetParent!==null?'vis':'hidden')+(e.disabled?',disabled':'')+',val='+String(e.value||'').slice(0,18)+'}'; };"
                    + " const bad=[...document.querySelectorAll('input.ng-invalid,select.ng-invalid,textarea.ng-invalid')].map(st);"
                    + " const req=[...document.querySelectorAll('input[required],select[required],textarea[required],[ng-required]')].filter(e=>!e.value && e.offsetParent!==null && !e.disabled).map(st);"
                    + " const oid=[...document.querySelectorAll('[ng-model]')].filter(e=>/ReferenceNo/.test(e.getAttribute('ng-model'))).map(st);"
                    + " return 'ng-invalid: [' + bad.join(', ') + '] | empty required+visible: [' + req.join(', ') + '] | Other-Id field: [' + oid.join(', ') + ']'; }");
            return "Save blocked: " + blocked + (reg.lastMandatoryFields.isEmpty() ? "" : " - the screen flagged: " + reg.lastMandatoryFields) + " | " + inv + " | Payor section: [" + describePayorSection() + "]";
        }
        SaveOutcome out = reg.confirmSaveAndAwaitSuccess();
        if (out == null || !out.saved) {
            // No toast / report tab was seen. That is not proof the save failed (the toast fades in ~1s and the tabs can be slow):
            // carry on - the next step looks the patient up in the OP queue by name, which is the real evidence.
            System.out.println("registerNewSelfPayPatient: Save not confirmed by toast/report tab - verifying through the OP queue instead");
            waitForAngular(4000);
        }
        // The save is confirmed above; the consent modal / report tabs are clean-up. The app can navigate right
        // after Save ("Execution context was destroyed"), so retry once and never fail the registration on it.
        for (int i = 0; i < 2; i++) {
            try {
                if (reg.waitConsentDetailsModal()) reg.closeConsentDetailsModal();
                break;
            } catch (com.microsoft.playwright.PlaywrightException e) {
                System.out.println("registerNewSelfPayPatient: consent modal clean-up raced a navigation - " + e.getMessage().split("\\n")[0]);
                waitForAngular(2500);
            }
        }
        // Close the auto-opened report / consent tabs so later steps drive the main tab.
        try {
            for (Page pg : new java.util.ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
        } catch (Exception ignore) { }
        return null;
    }

    /**
     * Workflow doc v4 C1 step 6: "Payor Mode*, Payor Status* (filled from the mode), Payor* (smart fill)". RegistrationPage types
     * "self" into the Payor box, which leaves Payor Code empty and the app flags "Payor*". Re-do it as a user would: type
     * into the Payor box and PICK the suggestion, then make sure the Pricing Policy* (Registration.tariffid) has a value.
     */
    private void fillPayorSmart() {
        try {
            String code = String.valueOf(page.evaluate("() => { const e=[...document.querySelectorAll('[ng-model]')].find(x=>x.getAttribute('ng-model')==='Registration.receivabletypecode'); return e? e.value : 'n/a'; }"));
            if (code.isBlank() || "null".equals(code)) {
                var box = page.locator("input[ng-model='Registration.receivablename']").first();
                if (box.count() > 0) {
                    box.scrollIntoViewIfNeeded(new com.microsoft.playwright.Locator.ScrollIntoViewIfNeededOptions().setTimeout(4000));
                    box.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
                    box.fill("", new com.microsoft.playwright.Locator.FillOptions().setTimeout(4000));
                    box.pressSequentially("SELF", new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(150));
                    waitForAngular(2500);
                    Object picked = page.evaluate("() => { " + JS + " const li=[...document.querySelectorAll('ul.dropdown-menu li, .ui-autocomplete li, [class*=autocomplete] li, [class*=suggest] li')].find(vis);"
                            + " if(!li) return 'no suggestion'; const t=norm(li.textContent); (li.querySelector('a')||li).click(); return t; }");
                    System.out.println("fillPayorSmart: suggestion -> " + picked);
                    waitForAngular(1200);
                }
            }
        } catch (Exception e) { System.out.println("fillPayorSmart: payor pick failed - " + String.valueOf(e.getMessage()).replaceAll("\\s+", " ")); }
        Object pp = page.evaluate("() => { " + JS + " const s=ngEl('Registration.tariffid')[0]; if(!s) return 'no Pricing Policy field'; if(s.value && !/^-*\\s*select/i.test((s.options[s.selectedIndex]||{}).text||'')) return 'already ' + s.options[s.selectedIndex].text;"
                + " const o=[...s.options].find(x=>/self|cash/i.test(x.text) && x.value) || [...s.options].find(x=>x.value && !/^-*\\s*select/i.test(x.text.trim())); if(!o) return 'no option';"
                + " s.value=o.value; try{ angular.element(s).controller('ngModel').$setViewValue(o.value); }catch(e){} s.dispatchEvent(new Event('change',{bubbles:true})); return o.text.trim(); }");
        System.out.println("fillPayorSmart: Pricing Policy -> " + pp);
        waitForAngular(600);
    }

    /** Cash Counter (Visit Information) - mandatory; leave a value that was already chosen, otherwise pick a cashier-type counter. */
    private void fillCashCounter() {
        Object r = page.evaluate("() => { " + JS + " const s=[...document.querySelectorAll('select')].find(x=>/cash\\s*counter/i.test((x.getAttribute('ng-model')||'')+' '+(x.id||'')));"
                + " if(!s) return 'no Cash Counter select; selects with cash/counter in model or id: [' + [...document.querySelectorAll('select')].filter(x=>/cash|counter/i.test((x.getAttribute('ng-model')||'')+' '+(x.id||''))).map(x=>(x.getAttribute('ng-model')||x.id)).join(', ') + ']'; const cur=((s.options[s.selectedIndex]||{}).text||'').trim(); if(s.value && !/^-*\\s*select/i.test(cur)) return 'already ' + cur;"
                + " const real=[...s.options].filter(x=>x.value && !/^-*\\s*select/i.test(x.text.trim())); const o=real.find(x=>/cashier|opd/i.test(x.text)) || real[0]; if(!o) return 'no option';"
                + " s.value=o.value; try{ angular.element(s).controller('ngModel').$setViewValue(o.value); }catch(e){} s.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const $=window.jQuery; if($){ try{ $(s).trigger('change'); $(s).select2('val', o.value); }catch(e){} } return 'set ' + o.text.trim(); }");
        System.out.println("fillCashCounter: " + r);
        waitForAngular(500);
    }

    /**
     * Known-good Visit Information, where every connected dropdown has values: Department (default Emergency Department, which has Sub
     * Departments) -> first Sub Department that offers a doctor (default Doctor_Tieba or Demo Doctor) -> Visit Type (default New). Each choice is
     * made through the model + select2 and followed by a wait, because every choice reloads the next list.
     * Returns what was chosen, or why nothing worked.
     */
    private String applyKnownGoodVisit() {
        String dept = System.getProperty("billingop.dept", "Emergency Department");
        String doctor = System.getProperty("billingop.doctor", "Doctor_Tieba|Demo Doctor");
        String vtype = System.getProperty("billingop.visittype", "New");
        Object d = page.evaluate("(a) => { " + JS + JS_SEL_EXACT + " return selExact('Visit.DepartmentID', new RegExp(a,'i'), true); }", dept);
        waitForAngular(2500);
        // Sub Departments: try each until the preferred doctor (or at least a real one) is offered, then the Visit Type
        for (int w = 0; w < 10; w++) {
            Object n = page.evaluate("() => { " + JS + " const s=ngEl('Visit.SubDepartmentID')[0]; return s? [...s.options].filter(o=>o.value && !/^-*\\s*select/i.test(o.text.trim())).length : 0; }");
            if (n != null && ((Number) n).intValue() > 0) break;
            waitForAngular(1000);
        }
        Object subs = page.evaluate("() => { " + JS + " const s=ngEl('Visit.SubDepartmentID')[0]; return s? [...s.options].filter(o=>o.value && !/^-*\\s*select/i.test(o.text.trim())).map(o=>o.text.trim()) : []; }");
        java.util.List<?> subList = subs instanceof java.util.List ? (java.util.List<?>) subs : java.util.List.of();
        String chosen = "dept=" + d + "; subs=" + subList.size();
        for (int i = 0; i < Math.min(subList.size(), 8); i++) {
            Object sd = page.evaluate("(t) => { " + JS + JS_SEL_EXACT + " return selExact('Visit.SubDepartmentID', new RegExp('^'+t.replace(/[.*+?^${}()|\\[\\]\\\\]/g,'\\\\$&')+'$','i'), false); }", String.valueOf(subList.get(i)));
            waitForAngular(2200);
            Object doc = page.evaluate("(a) => { " + JS + JS_SEL_EXACT + " const m=[...document.querySelectorAll('select')].map(x=>x.getAttribute('ng-model')||'').find(x=>/^Visit\\.\\w*Doctor\\w*$/i.test(x));"
                    + " if(!m) return 'no doctor select'; return selExact(m, new RegExp(a,'i'), true); }", doctor);
            waitForAngular(1800);
            Object vt = page.evaluate("(a) => { " + JS + JS_SEL_EXACT + " return selExact('Visit.VisitTypeID', new RegExp('^'+a+'$','i'), true); }", vtype);
            waitForAngular(800);
            chosen += "; sub=" + sd + " doctor=" + doc + " visitType=" + vt;
            if (!String.valueOf(doc).isEmpty() && !String.valueOf(vt).isEmpty() && !"no doctor select".equals(String.valueOf(doc))) break;
        }
        return chosen;
    }

    /** JS helper: choose an option of a (select2-wrapped) select by model + text regex, falling back to the first real option; returns its text. */
    private static final String JS_SEL_EXACT =
            "const selExact=(m,re,anyFirst)=>{ const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===m); if(!s) return '';"
          + "  const real=[...s.options].filter(o=>o.value && !/^-*\\s*select/i.test(o.text.trim()));"
          + "  const o=real.find(x=>re.test(x.text.trim())) || (anyFirst ? real[0] : null); if(!o) return '';"
          + "  s.value=o.value; try{ angular.element(s).controller('ngModel').$setViewValue(o.value); }catch(e){} s.dispatchEvent(new Event('change',{bubbles:true}));"
          + "  const $=window.jQuery; if($){ try{ $(s).trigger('change'); $(s).select2('val', o.value); }catch(e){} } return o.text.trim(); };";

    /** Department / Sub Dept / Doctor / Visit Type all hold a value in the select itself (the app can reset the Department DISPLAY after a doctor is chosen while the id stays). */
    private boolean visitModelComplete() {
        Object r = page.evaluate("() => { " + JS + " const miss=[]; ['Visit.DepartmentID','Visit.SubDepartmentID','Visit.VisitTypeID'].forEach(m=>{ const s=ngEl(m)[0]; if(!s || !s.value) miss.push(m); });"
                + " const d=[...document.querySelectorAll('select')].find(x=>/^Visit\\.\\w*Doctor\\w*$/i.test(x.getAttribute('ng-model')||'')); if(!d || !d.value) miss.push('Doctor'); return miss.join(','); }");
        String missing = r == null ? "?" : r.toString();
        System.out.println("visitModelComplete: " + (missing.isEmpty() ? "all four hold a value" : "missing " + missing));
        return missing.isEmpty();
    }

    /** Put back, right before Save, any registration field a late list reload has cleared (values come from the patient profile). */
    private void refillIfCleared(PatientProfile p, String otherId) {
        Object r = page.evaluate("(a) => { " + JS + JS_SEL_EXACT + " const done=[];"
                + " const txt=(m,v)=>{ if(v==null||v==='') return; const e=ngEl(m)[0]; if(e && !e.value){ setEl(e,v); done.push(m); } };"
                + " const sel=(m,t)=>{ if(!t) return; const s=ngEl(m)[0]; if(!s) return; const cur=((s.options[s.selectedIndex]||{}).text||'').trim(); if(s.value && !/^-*\\s*select/i.test(cur)) return;"
                + "   const x=selExact(m,new RegExp('^'+String(t).replace(/[.*+?^${}()|\\[\\]\\\\]/g,'\\\\$&')+'\\\\.?$','i'),false); if(x) done.push(m); };"
                + " txt('Registration.FirstName',a.name); txt('Registration.NationalId',a.nric); txt('Registration.DateOfBirth',a.dob); txt('Registration.TINNumber',a.tin);"
                + " txt('Registration.ReferenceNo',a.otherId);"
                + " txt('Registration.KinNationalId',a.kinNric); txt('Registration.KinFamilyName',a.kinFamily); txt('Registration.KinTinNo',a.tin);"
                + " sel('Registration.GenderID',a.gender); sel('Registration.PrefixID',a.prefix);"
                + " return done.join(', '); }",
                java.util.Map.of("name", String.valueOf(p.fullName), "nric", String.valueOf(p.nric), "dob", String.valueOf(p.dob), "tin", String.valueOf(p.tin),
                        "otherId", otherId, "gender", String.valueOf(p.gender), "prefix", String.valueOf(p.prefix),
                        "kinNric", String.valueOf(p.kinNric), "kinFamily", String.valueOf(p.kinFamily)));
        System.out.println("refillIfCleared: " + (r == null || r.toString().isEmpty() ? "nothing was cleared" : "re-filled " + r));
        waitForAngular(300);
    }

    /** Profession (Registration.Profession) - mandatory on devhis, not filled by RegistrationPage. Works for a select or a text box. */
    private void fillProfession() {
        Object r = page.evaluate("() => { " + JS + " const e=ngEl('Registration.Profession')[0]; if(!e) return 'not on this screen';"
                + " if(e.value) return 'already ' + e.value;"
                + " if(e.tagName==='SELECT'){ return 'select: ' + setSel(e,/./); }"
                + " setEl(e,'Employee'); return 'text: Employee'; }");
        System.out.println("fillProfession: " + r);
        waitForAngular(500);
    }

    /** Payor Information section controls (model, value, required?) - attached to a blocked-Save message to pin the "Payor*" field. */
    private String describePayorSection() {
        Object r = page.evaluate("() => { " + JS + " const h=[...document.querySelectorAll('.panel-heading,.panel-title,h3,h4,div,a,span')].find(e=>e.children.length<3 && /^\\s*Payor Information\\s*$/i.test(e.textContent||''));"
                + " const box=h? (h.closest('.panel')||h.parentElement.parentElement) : document;"
                + " return [...box.querySelectorAll('input,select,textarea')].map(e=>(e.getAttribute('ng-model')||e.id||e.placeholder||e.tagName)+'{'+(vis(e)?'vis':'hidden')+(e.disabled?',disabled':'')+',val='+String(e.value||'').slice(0,16)+(e.required||e.className.indexOf('required')>=0?',req':'')+'}').slice(0,40).join(', '); }");
        return r == null ? "" : r.toString();
    }

    /** Registration time in the format the screen displays ("hh:mm AM/PM"); RegistrationPage writes 24-hour "16:42", which devhis marks ng-invalid. */
    private void fillRegistrationTime() {
        String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.US));
        page.evaluate("(v) => { const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='inputTime'); if(!e) return;"
                + " e.value=v; try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(x){}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", t);
        waitForAngular(300);
    }

    /** Next of Kin form fields devhis requires at Save: Kin NRIC, Kin family name and Kin TIN (values from the kin of the profile). */
    private void fillKinMandatory(PatientProfile p) {
        page.evaluate("(a) => { " + JS + " const set=(m,v)=>{ ngEl(m).forEach(e=>{ if(vis(e) && !e.disabled && !e.value) setEl(e,v); }); };"
                + " set('Registration.KinNationalId', a[0]); set('Registration.KinFamilyName', a[1]); set('Registration.KinTinNo', a[2]); }",
                java.util.Arrays.asList(String.valueOf(p.kinNric), String.valueOf(p.kinFamily), String.valueOf(p.tin)));
        waitForAngular(500);
    }

    /** "Other Identification No." (Patient Information, Registration.ReferenceNo): type it for real, then also set the Angular model. */
    private void fillOtherIdentification(String value) {
        try {
            var f = page.locator("input[ng-model='Registration.ReferenceNo']").first();
            if (f.count() > 0) {
                f.scrollIntoViewIfNeeded(new com.microsoft.playwright.Locator.ScrollIntoViewIfNeededOptions().setTimeout(3000));
                f.fill(value, new com.microsoft.playwright.Locator.FillOptions().setTimeout(4000));
            }
        } catch (Exception e) { System.out.println("fillOtherIdentification: typing failed - " + String.valueOf(e.getMessage()).replaceAll("\\s+", " ").substring(0, Math.min(300, String.valueOf(e.getMessage()).length()))); }
        page.evaluate("(v) => { const e=[...document.querySelectorAll('[ng-model]')].find(x=>x.getAttribute('ng-model')==='Registration.ReferenceNo'); if(!e) return;"
                + " try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(x){}"
                + " try{ const sc=angular.element(e).scope(); sc.$evalAsync(()=>{ if(sc.Registration) sc.Registration.ReferenceNo=v; }); }catch(x){} }", value);
        waitForAngular(500);
    }

    /**
     * The MRN the system assigned. The Save does not hand it back to the script, so read it from the OP queue
     * (the new visit is listed today) by patient name. Retries because the queue can lag the save by a moment.
     */
    public String lookupMrnByName(OutPatientQueueManagementPage queue, String baseUrl, String fullName, String todayDdMmYyyy) {
        String esc = fullName.trim().replaceAll("[.*+?^${}()|\\[\\]\\\\/]", "\\\\$0");   // JS-regex safe literal
        for (int attempt = 0; attempt < 3; attempt++) {
            openQueue();
            queue.searchQueue(todayDdMmYyyy);
            Object mrn = page.evaluate("(re) => { " + JS + " const g=gridScope(); if(!g) return '';"
                    + " const rx=new RegExp(re,'i'); const r=g.grid.options.data.find(x=>rx.test(x.patientname||x.PatientName||''));"
                    + " return r ? String(r.MRNo||'') : ''; }", esc);
            if (mrn != null && !mrn.toString().isBlank()) return mrn.toString().trim();
            waitForAngular(2500);
        }
        return "";
    }

    /**
     * Builds from v2026.10.06.182 open EMR in a NEW browser tab (v...180 used the same tab) and the main tab stays on the queue.
     * When an EMR tab appears the flow keeps working in IT - exactly what a user does - and returns to the main tab afterwards
     * ({@link #backToMainTab()}).
     */
    public boolean adoptEmrTab() {
        Page emr = null;
        for (Page pg : page.context().pages()) {
            if (pg == page || pg == mainPage || pg.isClosed()) continue;
            String u = pg.url().toLowerCase();
            if (u.contains("nursingemr") || u.contains("emrgroupwise")) emr = pg;
        }
        if (emr == null) return false;
        System.out.println("adoptEmrTab: EMR opened in a new tab (" + emr.url() + ") - continuing the EMR steps in that tab");
        try { emr.bringToFront(); } catch (Exception ignore) { }
        try { com.kpj.core.DevHisBase.ensureLargeWindow(emr); } catch (Throwable ignore) { }
        this.page = emr;
        waitForAngular(2500);
        return true;
    }

    public boolean onEmr() {
        String u = page.url().toLowerCase();
        return u.contains("nursingemr") || u.contains("emrgroupwise");
    }

    /** Select the MRN's row (that is what enables the Action menu), open Action and click EMR; retried, as the selection can miss. */
    private String clickEmrMenuItem(String mrn) {
        String state = "missing";
        for (int attempt = 0; attempt < 3; attempt++) {
            // Selecting the row is what enables the Action menu: the queue clears entity.printstatus on row selection.
            page.evaluate("(m) => { " + JS + " const g=gridScope(); if(!g) return; const r=g.grid.options.data.find(x=>String(x.MRNo)===m); if(!r) return;"
                    + " try{ g.grid.api.selection.clearSelectedRows(); }catch(e){} g.grid.api.selection.selectRow(r);"
                    + " if(r.printstatus){ const b=document.querySelector('.ui-grid-canvas .ui-grid-selection-row-header-buttons'); if(b) b.click(); }"
                    + " try{ g.$apply(); }catch(e){} }", mrn);
            waitForAngular(1200);
            page.evaluate("() => { const b=[...document.querySelectorAll('.op-act-toggle')].find(x=>x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(800);
            Object item = page.evaluate("() => { const it=[...document.querySelectorAll('.op-action-menu__item')].find(x=>x.offsetParent!==null && /^\\s*EMR\\s*$/.test(x.textContent));"
                    + " if(!it) return 'missing'; if(it.disabled || it.classList.contains('is-disabled')) return 'disabled'; it.click(); return 'clicked'; }");
            state = item == null ? "missing" : item.toString();
            if ("clicked".equals(state)) return state;
            page.evaluate("() => document.body.click()");   // close the menu, wait, select again
            waitForAngular(2000);
        }
        return state;
    }

    /** OP > Outpatient Queue Management by menu (see {@link #openScreen}) and wait until its Search button is live. */
    public void openQueue() {
        openScreen("OP", "#/queueManagement", "queueManagement");
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()))",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        waitForAngular(1500);
    }

    // ============================================================================================================
    //  C2  Queue -> EMR
    // ============================================================================================================

    /**
     * On the OP queue, narrow to the MRN, open the row's <b>Action</b> menu and click <b>EMR</b>. This environment
     * forces a <b>Fall Risk Assessment</b> before EMR opens ({@code ShowEMR} -> {@code fnOpenFallRiskAssessment});
     * it is completed through the existing {@link OutPatientQueueManagementPage} helper and noted in
     * {@link #lastFallRiskNote}. Returns null once the EMR screen is open, else the reason it did not.
     */
    private String emrMrn = "";

    public String openEmrFromQueue(OutPatientQueueManagementPage queue, String baseUrl, String mrn, String todayDdMmYyyy) {
        emrMrn = mrn;
        openQueue();
        page.evaluate("(m) => { " + JS + " const e=ngEl('queue.MRNo')[0]; setEl(e,m); }", mrn);
        queue.searchQueue(todayDdMmYyyy);
        Object rows = page.evaluate("(m) => { " + JS + " const g=gridScope(); if(!g) return 0; return g.grid.options.data.filter(x=>String(x.MRNo)===m).length; }", mrn);
        if (rows == null || ((Number) rows).intValue() == 0) return "MRN " + mrn + " is not in today's OP queue";
        watchToasts();
        String state = clickEmrMenuItem(mrn);
        if (!"clicked".equals(state)) {
            lastDump = "Action menu: " + page.evaluate("() => [...document.querySelectorAll('.op-action-menu__item')].filter(x=>x.offsetParent!==null).map(x=>x.textContent.trim()+(x.disabled?'(disabled)':'')).join(', ')");
            return "Action > EMR is " + state + " for MRN " + mrn + " (the item is disabled until the visit is marked / not yet printed)";
        }
        // Fall Risk Assessment gate (or straight through to EMR). If the assessment is saved but the app does not
        // continue to EMR by itself, click EMR again - the second click passes the (now satisfied) gate.
        int reclicks = 0, fallTries = 0;
        long lastClick = System.currentTimeMillis();
        for (int i = 0; i < 60; i++) {
            if (onEmr() || adoptEmrTab()) break;
            boolean fall = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /assessment of fall|fall risk/i.test(m.textContent||''))"));
            if (fall && lastFallRiskNote.isEmpty() && fallTries < 4) {
                fallTries++;
                String toast = queue.fillFallRiskAndApply();
                // The helper may return an OLDER success toast (e.g. "Registration Saved Successfully"): only a "Fall Risk ... saved"
                // toast proves the assessment was stored.
                boolean saved = Boolean.TRUE.equals(page.evaluate("() => (window.__frToasts||[]).some(t=>/fall risk[^|]*saved|saved[^|]*fall risk/i.test(t))"));
                if (saved) {
                    lastFallRiskNote = "Fall Risk Assessment (mandatory before EMR on this environment, not in the workflow document) saved - " + toast;
                } else {
                    System.out.println("openEmrFromQueue: Fall Risk save NOT confirmed (attempt " + fallTries + "), toast='" + toast + "'");
                }
                lastClick = System.currentTimeMillis();
                waitForAngular(2500);
            } else if (!fall && lastFallRiskNote.isEmpty() && fallTries > 0 && fallTries < 4 && System.currentTimeMillis() - lastClick > 4000) {
                String again = clickEmrMenuItem(mrn);       // modal gone without a confirmed save: open it again
                System.out.println("openEmrFromQueue: reopening the Fall Risk Assessment via EMR -> " + again);
                lastClick = System.currentTimeMillis();
            } else if (!fall && !lastFallRiskNote.isEmpty() && reclicks < 3 && System.currentTimeMillis() - lastClick > 5000) {
                reclicks++;                       // assessment is saved but the app did not continue: click EMR again
                String again = clickEmrMenuItem(mrn);
                System.out.println("openEmrFromQueue: re-click " + reclicks + " -> " + again + "; url=" + page.url());
                lastClick = System.currentTimeMillis();
            }
            waitForAngular(700);
        }
        if (!onEmr() && !lastFallRiskNote.isEmpty()) {
            // The Fall Risk Assessment was completed through the UI above, but the app did not continue to EMR by itself and the
            // menu click is not answered under automation. Take the app's own continuation (what ShowEMR calls once the assessment
            // exists): fnNavigateToEmrFromQueue(row). Never used when the assessment was not done here (the gate is not skipped).
            Object nav = page.evaluate("(m) => { " + JS + " const g=gridScope(); if(!g) return 'no grid'; const r=g.grid.options.data.find(x=>String(x.MRNo)===m); if(!r) return 'row not found';"
                    + " const S=(g.$parent&&g.$parent.fnNavigateToEmrFromQueue)?g.$parent:g; if(!S.fnNavigateToEmrFromQueue) return 'fnNavigateToEmrFromQueue missing';"
                    + " S.$apply(()=>S.fnNavigateToEmrFromQueue({entity:r})); return 'navigated'; }", mrn);
            System.out.println("openEmrFromQueue: fallback fnNavigateToEmrFromQueue -> " + nav);
            lastFallRiskNote += " | the app did not open EMR by itself after the assessment, so its own continuation (fnNavigateToEmrFromQueue) was used";
            waitForAngular(4000);
            adoptEmrTab();
        }
        try { page.waitForFunction("() => [...document.querySelectorAll('select')].some(s=>s.getAttribute('ng-model')==='EMRTagID')", null, new Page.WaitForFunctionOptions().setTimeout(60000)); }
        catch (Exception e) {
            return "EMR screen did not open (url " + page.url() + ") | fall-risk: " + (lastFallRiskNote.isEmpty() ? "modal never appeared" : lastFallRiskNote)
                    + " | toasts: " + toasts() + " | dialogs: " + page.evaluate("() => [...document.querySelectorAll('.modal,[role=dialog],.ng-confirm-box,.jconfirm')].filter(m=>m.getBoundingClientRect().width>0).map(m=>m.innerText.replace(/\s+/g,' ').slice(0,100)).join('|')");
        }
        waitForAngular(1500);
        return null;
    }

    // ---- EMR ---------------------------------------------------------------------------------------------------

    /** Document step C2.3 "Select Tag: Normal" -> the tag list calls it <b>Normal EMR</b> (EMRTagID). */
    public String selectNormalEmrTag() {
        // The tag list is filled after the screen opens: wait for a "Normal" option before choosing it. If it stays empty (devhis:
        // only "-Select-"), re-load the patient with the EMR screen's own MRN search and wait again, up to three times.
        final String normalOpt = "() => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='EMRTagID'); return !!s && [...s.options].some(o=>/normal/i.test(o.text)); }";
        try {
            for (int attempt = 0; ; attempt++) {
                // devhis build v2026.10.13.222: the Tag options are only filled once the dropdown has been OPENED/focused (a user's
                // click does it). Open it for real, then close it again, before waiting for "Normal EMR".
                try {
                    var tag = page.locator("select[ng-model='EMRTagID']").first();
                    tag.focus(new com.microsoft.playwright.Locator.FocusOptions().setTimeout(4000));
                    tag.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
                    waitForAngular(1500);
                    page.keyboard().press("Escape");
                } catch (Exception openFail) { System.out.println("selectNormalEmrTag: opening the Tag dropdown failed - " + String.valueOf(openFail.getMessage()).replaceAll("\\s+", " ")); }
                try {
                    page.waitForFunction(normalOpt, null, new Page.WaitForFunctionOptions().setTimeout(attempt == 0 ? 12000 : 20000));
                    break;
                } catch (Exception waitFail) {
                    if (attempt >= 3 || emrMrn.isEmpty()) throw waitFail;
                    System.out.println("selectNormalEmrTag: tag list still empty (attempt " + (attempt + 1) + ") - re-loading the patient in EMR (" + emrMrn + ")");
                    page.evaluate("(m) => { " + JS + " const e=ngEl('EMR.mrno')[0]; if(e) setEl(e,m);"
                            + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/SearchPatientByMRNo\\(\\)/.test(x.getAttribute('ng-click')||'') && vis(x)); if(b) b.click(); }", emrMrn);
                    waitForAngular(5000);
                }
            }
        } catch (Exception e) {
            lastDump = "Tag options: " + page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='EMRTagID'); return s? [...s.options].map(o=>o.text.trim()).join(' | ') : '(no EMRTagID select) url=' + location.hash; }");
            return "";
        }
        String optText = String.valueOf(page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='EMRTagID'); const o=[...s.options].find(x=>/normal/i.test(x.text)); return o? o.text.trim() : ''; }"));
        Object r = optText;
        boolean loaded = false;
        for (int attempt = 0; attempt < 4 && !loaded; attempt++) {
            waitForAngular(2500);   // let the screen finish its own initialisation (it resets the tag while it loads)
            try {
                page.locator("select[ng-model='EMRTagID']").selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(optText),
                        new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(8000));
            } catch (Exception e) { System.out.println("selectNormalEmrTag: selectOption failed - " + e.getMessage().split("\n")[0]); }
            waitForAngular(1200);
            answerDialogs(2);          // "Confirm! Do you want to save new tag for this case?" -> Yes
            try {
                page.waitForFunction("() => [...document.querySelectorAll('h4')].some(h=>/Investigations.Services/i.test(h.textContent||'') && h.offsetParent!==null)",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
                // the screen can reset the Tag to "-Select-" after the confirm (then every save says "Please Select Tag!"): require it to hold
                Object held = page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='EMRTagID'); return !!s && !!s.options[s.selectedIndex] && /normal/i.test(s.options[s.selectedIndex].text); }");
                loaded = Boolean.TRUE.equals(held);
                if (!loaded) System.out.println("selectNormalEmrTag: attempt " + (attempt + 1) + " - Tag reset to -Select-, choosing again");
            } catch (Exception e) { System.out.println("selectNormalEmrTag: attempt " + (attempt + 1) + " - sections not loaded"); }
        }
        if (!loaded) {
            lastDump = "Tag chosen (" + r + ") but the sections did not load; toasts: " + toasts() + " | tagNow="
                    + page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='EMRTagID'); return s&&s.options[s.selectedIndex]? s.options[s.selectedIndex].text : '?'; }")
                    + " | header: " + page.evaluate("() => document.body.innerText.replace(/\s+/g,' ').slice(0,400)") + " | " + describeVisibleControls();
            return "";
        }
        waitForAngular(1000);
        return r == null ? "" : r.toString();
    }

    /**
     * devhis (v2026.10.13.222) starts the EMR sections COLLAPSED and loads their content only when the header is clicked
     * (the Allergy header is the only allergy control on screen until then; Prescription shows no store options and no drug row).
     * Open the section when its probe control is not visible; never click a header whose section is already open (that would collapse it).
     */
    private void ensureSectionOpen(String sectionCode, String probeModel) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Object open = page.evaluate("(m) => { " + JS + " return ngEl(m).some(vis); }", probeModel);
            if (Boolean.TRUE.equals(open)) return;
            Object clicked = page.evaluate("(c) => { " + JS + " const h=[...document.querySelectorAll('[ng-click]')].find(x=>(x.getAttribute('ng-click')||'').indexOf(\"SetFlagToExecuteSectionCode('\" + c + \"')\")>=0 && vis(x));"
                    + " if(!h) return false; h.scrollIntoView({block:'center'}); h.click(); return true; }", sectionCode);
            System.out.println("ensureSectionOpen: " + sectionCode + " header clicked=" + clicked);
            waitForAngular(2500);
        }
    }

    /** The EMR refuses Allergy / Finalize until the mandatory vital sign (Weight) exists: open Vitals, enter Weight, Save. */
    private void fillWeightVital() {
        Object vc = page.evaluate("() => { " + JS + " const l=[...document.querySelectorAll('[ng-click]')].filter(x=>/vital/i.test(x.getAttribute('ng-click')||'')); return l.map(x=>(vis(x)?'V ':'h ')+x.tagName+':'+x.getAttribute('ng-click').slice(0,60)).join(' | '); }");
        System.out.println("fillWeightVital: vital controls: " + vc);
        page.evaluate("() => { " + JS + " const l=[...document.querySelectorAll('[ng-click]')].filter(x=>vis(x) && /(SetFlagToExecuteSectionCode..Vital|Vital.*(Open|Add|Show|Popup|Modal))/i.test(x.getAttribute('ng-click')||'') && !/save|graph|chart|trend/i.test(x.getAttribute('ng-click')||'')); if(l[0]){ l[0].scrollIntoView({block:'center'}); l[0].click(); } }");
        waitForAngular(2500);
        Object r = page.evaluate("() => { " + JS + " const vals=[...document.querySelectorAll('input')].filter(x=>vis(x) && x.getAttribute('ng-model')==='list.defaultvalue');"
                + " const rowOf=(el)=>{ let n=el; for(let i=0;i<6 && n;i++){ n=n.parentElement; if(!n) break; const k=n.querySelectorAll('input[ng-model*=defaultvalue]').length; if(k>=1 && k<=3 && (n.innerText||'').trim().length>2) return n; } return null; };"
                + " const val=(t)=>{ if(t==='weight') return '70'; if(t==='height') return '170'; if(t==='body temperature'||t==='temperature') return '36.8'; if(/^pulse .heart rate.$|^heart rate$|^pulse$/.test(t)) return '72';"
                + "   if(/^(respiratory rate|respiration rate)/.test(t)) return '18'; if(/systolic/.test(t)) return '120'; if(/diastolic/.test(t)) return '80'; if(/^blood pressure/.test(t)) return 'BP'; return null; };"
                + " const seen={}; const done=[];"
                + " for(const v of vals){ const rw=rowOf(v); if(!rw) continue; const name=(rw.innerText||'').split('\\n').map(x=>x.trim().toLowerCase()).filter(x=>x)[0]||''; const w=val(name); if(!w || seen[name]) continue; seen[name]=1;"
                + "   const ins=[...rw.querySelectorAll('input[ng-model*=defaultvalue]')].filter(vis); ins.forEach((x,i)=>{ setEl(x, w==='BP' ? (i===0?'120':'80') : w); });"
                + "   const tick=rw.querySelector('input[ng-model*=IsSelect]'); if(tick && !tick.checked) tick.click(); done.push(name); }"
                + " if(!done.length) return 'no vital rows matched; rows=' + vals.length;"
                + " const sv=[...document.querySelectorAll('[ng-click]')].find(x=>/addselectedvitalsdetails/.test(x.getAttribute('ng-click')||'')); if(!sv) return 'entered ' + done.join(',') + '; no save control';"
                + " sv.scrollIntoView({block:'center'}); sv.click(); return 'entered ' + done.join(',') + ' and Save clicked'; }");
        System.out.println("fillWeightVital: " + r);
        waitForAngular(2000);
        answerDialogs(2);
    }

    /** Document step C2.4: tick "No Known Drug Allergy" and Save (EMRAllergyNKDA / SaveEMRAllergy()). */
    public String saveNoKnownDrugAllergy() {
        watchToasts();
        fillWeightVital();
        scrollTo("Allergy");
        ensureSectionOpen("Allergy", "EMRAllergyNKDA");
        try {
            var cb = page.locator("input[ng-model='EMRAllergyNKDA']").first();
            cb.scrollIntoViewIfNeeded(new com.microsoft.playwright.Locator.ScrollIntoViewIfNeededOptions().setTimeout(4000));
            if (!cb.isChecked()) cb.check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(5000).setForce(true));
        } catch (Exception e) { System.out.println("saveNoKnownDrugAllergy: real tick failed - " + String.valueOf(e.getMessage()).replaceAll("\\s+", " ")); }
        Object r = page.evaluate("() => { " + JS + " const c=ngEl('EMRAllergyNKDA')[0]; if(!c) return 'checkbox missing'; if(!c.checked) c.click();"
                + " const all=[...document.querySelectorAll('[ng-click]')].filter(x=>/allerg/i.test(x.getAttribute('ng-click')||'') && vis(x));"
                + " const sv=all.find(x=>/save/i.test(x.getAttribute('ng-click')||'')) || all.find(x=>/SaveEMRAllergy/.test(x.getAttribute('ng-click')||''));"
                + " if(sv){ sv.click(); return 'ticked + saved'; }"
                + " return c.checked ? 'ticked (this build has no separate Save control for Allergy; controls: ' + all.map(x=>(x.getAttribute('ng-click')||'').slice(0,34)).join(' | ') + ')' : 'could not tick the checkbox'; }");
        waitForAngular(1500);
        answerDialogs(2);
        waitForAngular(800);
        String t = toasts();
        return (r == null ? "" : r.toString()) + (t.isEmpty() ? "" : " - toast: " + t);
    }

    /**
     * Order a service in <i>Investigations/Services/Procedures</i>. The Service Name box is the same bespoke
     * {@code auto-complete} directive as OPD Charges (see {@code OpdCharges} Javadoc): real typing first, then the
     * directive's own {@code data()/itemSelected()} as the documented workaround. Returns the name the screen
     * picked, or "" when nothing matched {@code query}.
     */
    public String orderService(String query) {
        scrollTo("Investigations/Services");
        ensureSectionOpen("Investigations", "OrderSetId");
        String picked = pickViaAutoComplete("#txtServiceName", "AutoCompleteOptionsForServiceName", query);
        if (!picked.isEmpty()) {
            orderedServices.add(picked);
            // The row cannot be committed without a Remark/Indication ("Remark/Indication is Mandatory!") - and a Priority.
            page.evaluate("() => { " + JS + " ['row.entity.note','row.entity.userremark'].forEach(m=>ngEl(m).filter(vis).forEach(e=>{ if(!e.value) setEl(e,'Billing OP flow - auto order'); }));"
                    + " ngEl('row.entity.priority').filter(vis).forEach(s=>{ if(!s.value || /^-*\s*select/i.test((s.options[s.selectedIndex]||{}).text||'')) setSel(s,/./); }); }");
            waitForAngular(600);
            // Commit the line with its own row button (fnCheckAndInvestigationSaveRow); that also opens the next empty row.
            page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].filter(vis).find(x=>/fnCheckAndInvestigationSaveRow/.test(x.getAttribute('ng-click')||'')); if(b) b.click(); }");
            waitForAngular(1500);
            answerDialogs(2);
            waitForAngular(800);
            lastOrderNote = " | after commit: toasts=" + toasts() + "; service inputs visible/empty="
                    + page.evaluate("() => { " + JS + " const a=[...document.querySelectorAll('#txtServiceName')]; return a.filter(vis).length + '/' + a.filter(vis).filter(x=>!x.value).length + ' of ' + a.length; }");
        } else {
            lastDump = "no pick for '" + query + "'; previous order" + lastOrderNote + " | visible service inputs: "
                    + page.evaluate("() => { " + JS + " return [...document.querySelectorAll('#txtServiceName')].map(x=>{ let r=''; try{ const sc=angular.element(x).scope(); r='sel='+sc.row.entity.isselected+',id='+sc.row.entity.ServiceItemId+',idx='+sc.rowRenderIndex+',rows='+sc.grid.options.data.length; }catch(e){ r='noscope'; } return (vis(x)?'V':'h')+':'+x.value+'{'+r+'}'; }).join(' ; '); }");
        }
        return picked;
    }

    /**
     * Document steps C2.5-C2.7: send the new rows to the Work Order List by ticking each row's
     * <i>Finalize - sent to the Work Order List</i> box, then Save ({@code SaveInvestigation()}).
     */
    public String finalizeAndSaveInvestigations() {
        watchToasts();
        scrollTo("Investigations/Services");
        Object n = page.evaluate("() => { " + JS + " let k=0; const all=ngEl('row.entity.isfinalize'); all.forEach(c=>{ if(!c.checked && !c.disabled){ c.click(); k++; } });"
                + " return k + ' (finalize boxes: ' + all.length + ', now checked: ' + all.filter(c=>c.checked).length + ', disabled: ' + all.filter(c=>c.disabled).length + ')'; }");
        waitForAngular(600);
        page.evaluate("() => { " + JS + " const sv=[...document.querySelectorAll('[ng-click]')].find(x=>/^SaveInvestigation\\(\\)/.test((x.getAttribute('ng-click')||'').trim()) && vis(x)); if(sv) sv.click(); }");
        waitForAngular(1500);
        answerDialogs(4);
        waitForAngular(1500);
        String t = toasts();
        return "ticked Finalize on " + n + " row(s)" + (t.isEmpty() ? "" : " - toast: " + t);
    }

    /** Store + Prescription Type are plain selects the screen re-renders; set them with real selectOption and re-assert. */
    private String ensureStoreAndType() {
        String[][] want = {{"EMR.StoreId", "(?i).*pharmacy main store.*"}, {"EMR.PrescriptionTypeID", "(?i).*regular.*"}};
        StringBuilder got = new StringBuilder();
        for (String[] w : want) {
            if (w[0].equals("EMR.PrescriptionTypeID")) {
                // Do not open/touch this dropdown when "Regular Prescription" is already chosen: its list also holds "Cytotoxic Prescription",
                // and picking that raises "Restriction - You are not authorized to prescribe chemotherapy drugs".
                String cur = String.valueOf(page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='EMR.PrescriptionTypeID'); return s&&s.options[s.selectedIndex]? s.options[s.selectedIndex].text.trim() : ''; }"));
                if (cur.toLowerCase().contains("regular")) { got.append(cur).append(" / "); continue; }
            }
            // devhis (v2026.10.13.222) fills these lists only once the dropdown has been opened: focus + click it first.
            for (int tries = 0; tries < 3; tries++) {
                try {
                    var dd = page.locator("select[ng-model='" + w[0] + "']").first();
                    dd.focus(new com.microsoft.playwright.Locator.FocusOptions().setTimeout(3000));
                    dd.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(3000));
                    waitForAngular(1200);
                    page.keyboard().press("Escape");
                } catch (Exception ignore) { }
                Object n = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===m); return s? [...s.options].filter(o=>o.value && !/^-*\\s*(select|type)/i.test(o.text.trim())).length : -1; }", w[0]);
                if (n != null && ((Number) n).intValue() != 0) break;
            }
            try {
                String label = String.valueOf(page.evaluate("(a) => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===a[0]); if(!s) return '';"
                        + " const real=[...s.options].filter(x=>x.value && !/^-*\\s*select/i.test(x.text.trim()) && !/^-*\\s*type/i.test(x.text.trim()));"
                        + " const o=real.find(x=>new RegExp(a[1].replace('(?i)',''),'i').test(x.text)) || (a[0]==='EMR.StoreId' ? (real.find(x=>/pharmacy/i.test(x.text)) || real[0]) : null); return o? o.text.trim() : ''; }", java.util.Arrays.asList(w[0], w[1])));
                if (!label.isEmpty())
                    page.locator("select[ng-model='" + w[0] + "']").selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label),
                            new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
                got.append(label.isEmpty() ? "?" : label).append(" / ");
            } catch (Exception e) { got.append("? / "); System.out.println("ensureStoreAndType: " + w[0] + " - " + e.getMessage().split("\n")[0]); }
        }
        waitForAngular(500);
        return got.toString();
    }

    /** Document step C2.8: Store = pharmacy main store, Regular prescription, drug + frequency + qty, Save. */
    public String prescribeDrug(String drugQuery, String frequencyQuery) {
        watchToasts();
        scrollTo("Prescription");
        ensureSectionOpen("Prescription", "EMR.PrescriptionTypeID");
        Object st = ensureStoreAndType();
        Object storeCount = page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='EMR.StoreId'); return s? [...s.options].filter(o=>o.value && !/^-*\\s*select/i.test(o.text.trim())).length : -1; }");
        if (storeCount != null && ((Number) storeCount).intValue() == 0)
            System.out.println("prescribeDrug: the Store list is EMPTY for this login (no prescribing store assigned) - the prescription cannot be saved");
        waitForAngular(800);
        String drug = pickViaAutoComplete("input[ng-model='row.entity.DrugName'], #txtBrandName", "BrandNameAutoCompleteOptions", drugQuery);
        if (drug.isEmpty()) {
            lastDump = "no drug for '" + drugQuery + "' | store/type chosen: " + st + " | " + page.evaluate("() => { " + JS
                    + " const q=m=>[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===m);"
                    + " const d=m=>{ const s=q(m); return s? (m+' selected=['+((s.options[s.selectedIndex]||{}).text||'').trim()+'] options=['+[...s.options].map(o=>o.text.trim()).slice(0,12).join(' | ')+']') : m+' not found'; };"
                    + " const b=[...document.querySelectorAll('input[ng-model*=DrugName], #txtBrandName')]; return d('EMR.StoreId') + ' ; ' + d('EMR.PrescriptionTypeID') + ' ; drug inputs=' + b.length + ' (visible ' + b.filter(vis).length + ')'; }");
            return "";
        }
        answerMimsAlert();
        pickViaAutoComplete("input[ng-model='row.entity.FrequencyText'], #txtFrequency", "FrequencyAutoCompleteOptions", frequencyQuery);
        page.evaluate("() => { " + JS + " const q=ngEl('row.entity.dosageqty').filter(vis).find(e=>!e.value); if(q) setEl(q,'1');"
                + " const d=ngEl('row.entity.FrqDuration').filter(vis).find(e=>!e.value); if(d) setEl(d,'1');"
                // the computed Qty can be 0 for a sparse frequency ("Quantity must be greater than 0!!"): state it explicitly
                + " ngEl('row.entity.Quantity').filter(vis).forEach(q=>{ if(!q.value || Number(q.value)<=0) setEl(q,'10'); }); }");
        waitForAngular(500);
        // Duration needs its unit, then the row is committed with its own green "+" (fnCheckAndSaveRow) before the section Save.
        page.evaluate("() => { " + JS + " [...document.querySelectorAll('select')].filter(vis).filter(s=>[...s.options].some(o=>/^days$/i.test(o.text.trim())) && !/dosageunitid|flowrateunitid/.test(s.getAttribute('ng-model')||''))"
                + ".forEach(s=>{ if(!s.value || /^-*\s*select/i.test((s.options[s.selectedIndex]||{}).text||'')) setSel(s,/^days$/i); }); }");
        waitForAngular(500);
        ensureStoreAndType();
        // changing the unit recalculates Qty - set it again last, so it is what gets committed
        page.evaluate("() => { " + JS + " ngEl('row.entity.Quantity').filter(vis).forEach(q=>{ if(!q.value || Number(q.value)<=0) setEl(q,'10'); }); }");
        waitForAngular(400);
        page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].filter(vis).find(x=>/fnCheckAndSaveRow/.test(x.getAttribute('ng-click')||'')); if(b) b.click(); }");
        waitForAngular(1500);
        answerMimsAlert();
        answerDialogs(2);
        ensureStoreAndType();
        page.evaluate("() => { " + JS + " const sv=[...document.querySelectorAll('[ng-click]')].find(x=>/^SavePrescription\\(\\)/.test((x.getAttribute('ng-click')||'').trim()) && vis(x)); if(sv) sv.click(); }");
        waitForAngular(1500);
        answerMimsAlert();
        answerDialogs(4);
        waitForAngular(1200);
        String t = toasts();
        return drug + " [" + st + "]" + (t.isEmpty() ? "" : " - toast: " + t);
    }

    /** Clear the prescription section (clearPrescription()) so a failed attempt can be repeated from a clean row. */
    public void clearPrescription() {
        page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/^clearPrescription/.test((x.getAttribute('ng-click')||'').trim()) && vis(x)); if(b) b.click(); }");
        waitForAngular(1500);
        answerDialogs(2);
    }

    private void answerMimsAlert() {
        // "override the MIMS alert if it shows" (document C2.8)
        for (int i = 0; i < 4; i++) {
            Object h = page.evaluate("() => { " + JS + " const box=[...document.querySelectorAll('.modal,.ng-confirm-box,.jconfirm,.sweet-alert')].find(m=>vis(m)&&/mims|interaction|alert/i.test(m.textContent||''));"
                    + " if(!box) return false; const b=[...box.querySelectorAll('button,a')].find(x=>vis(x)&&/^(override|ok|continue|yes|proceed|accept)$/i.test(norm(x.textContent)));"
                    + " if(!b) return false; b.click(); return true; }");
            if (!Boolean.TRUE.equals(h)) return;
            waitForAngular(700);
        }
    }

    /**
     * Pick the first suggestion of one of the app's {@code auto-complete} inputs. Strategy 1: real keystrokes into
     * the first visible EMPTY input, click the first suggestion. Strategy 2 (the documented workaround for this
     * directive): call {@code grid.appScope.<options>.data(q)} and {@code .itemSelected({item})} directly.
     */
    private String pickViaAutoComplete(String inputSelector, String optionsName, String query) {
        // Candidate: the first visible empty input; for the order grid, the entry row = the one whose entity is not yet
        // committed (isselected false), which ui-grid may have virtualised out of view (then only the direct pick works).
        Object tagged = page.evaluate("(s) => { " + JS + " const all=[...document.querySelectorAll(s)];"
                + " let e=all.filter(vis).find(x=>!x.value && !x.disabled);"
                + " if(!e) e=all.find(x=>{ try{ const r=angular.element(x).scope().row; return r && r.entity && !r.entity.isselected && !x.disabled; }catch(err){ return false; } });"
                + " if(!e) return false; e.id='__bopAC'; if(vis(e)) e.scrollIntoView({block:'center'}); return vis(e) ? 'visible' : 'hidden'; }", inputSelector);
        if (tagged == null || Boolean.FALSE.equals(tagged)) {
            // No entry input in the DOM (ui-grid virtualised row 0 away). The app's own itemSelected() writes to
            // gridOptions.data[0] regardless, so drive it through any rendered input's grid scope.
            Object viaGrid = page.evaluate("(a) => { const n=[...document.querySelectorAll(a.sel)][0]; if(!n) return null; const s=angular.element(n).scope();"
                    + " const o=(s.grid&&s.grid.appScope&&s.grid.appScope[a.opt]) || s[a.opt]; if(!o||!o.data) return null;"
                    + " return Promise.resolve(o.data(a.q)).then(items=>{ const item=(items||[])[0]; if(!item) return null; o.itemSelected({item});"
                    + "   const nm=item.ServiceName||item.DrugName||item.BrandName||item.FrequencyText||item.Name||'';"
                    + "   const d0=s.grid.options.data[0]; if(d0 && nm) d0.serviceitemname=nm;"
                    + "   try{s.$apply&&s.$apply();}catch(e){} return nm||'picked'; }); }",
                    Map.of("q", query, "sel", inputSelector, "opt", optionsName));
            return viaGrid == null ? "" : viaGrid.toString();
        }
        if ("visible".equals(tagged)) {
            try {
                page.locator("#__bopAC").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
                page.locator("#__bopAC").pressSequentially(query, new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(120));
                waitForAngular(2500);
                Object clicked = page.evaluate("() => { " + JS + " const li=[...document.querySelectorAll('ul.dropdown-menu li, .ui-autocomplete li, [class*=autocomplete] li, [class*=suggest] li')].find(vis);"
                        + " if(!li) return false; (li.querySelector('a')||li).click(); return true; }");
                waitForAngular(1200);
                Object v = page.evaluate("() => { const e=document.getElementById('__bopAC'); return e? e.value : ''; }");
                // a real selection replaces the typed text with the full name; unchanged text means the click did not select
                if (Boolean.TRUE.equals(clicked) && v != null && v.toString().trim().length() > query.length() + 3) { cleanTag(); return v.toString().trim(); }
            } catch (Exception e) {
                System.out.println("pickViaAutoComplete: typing failed - " + e.getMessage());
            }
        }
        // Workaround strategy
        Object r = page.evaluate("(a) => { const n=document.getElementById('__bopAC'); if(!n) return null; const s=angular.element(n).scope();"
                + " const o=(s.grid&&s.grid.appScope&&s.grid.appScope[a.opt]) || s[a.opt]; if(!o||!o.data) return null;"
                + " return Promise.resolve(o.data(a.q)).then(items=>{ const item=(items||[])[0]; if(!item) return null; o.itemSelected({item});"
                // itemSelected fills the row's ids/group but NOT the input's own model (row.entity.serviceitemname / DrugName ...),
                // which the row commit validates ("Please Select Service!") - set it exactly as a click on the suggestion would.
                + "   const nm=item.ServiceName||item.DrugName||item.BrandName||item.FrequencyText||item.Name||'';"
                + "   if(nm){ n.value=nm; try{ const c=angular.element(n).controller('ngModel'); c.$setViewValue(nm); c.$render(); }catch(e){} n.dispatchEvent(new Event('input',{bubbles:true})); }"
                + "   try{s.$apply&&s.$apply();}catch(e){}"
                + "   return nm||n.value||'picked'; }); }",
                Map.of("q", query, "opt", optionsName));
        cleanTag();
        return r == null ? "" : r.toString();
    }

    private void cleanTag() {
        page.evaluate("() => { const e=document.getElementById('__bopAC'); if(e) e.removeAttribute('id'); }");
    }

    private void scrollTo(String headerRe) {
        page.evaluate("(re) => { const h=[...document.querySelectorAll('h4')].find(x=>new RegExp(re,'i').test(x.textContent||'') && x.offsetParent!==null); if(h) h.scrollIntoView({block:'start'}); }", headerRe);
        waitForAngular(500);
    }

    // ============================================================================================================
    //  C3  Work Order Lists + Dispense Process
    // ============================================================================================================

    /**
     * Investigation &gt; Radiology|Lab &gt; <b>Work Order List</b> (routes {@code #/DoctorWorkOrderR} /
     * {@code #/DoctorWorkOrderL}; identical controls): search the MRN, tick its orders, click <b>Accept</b>.
     * Note the two MRN buttons are labelled backwards (title "Find Patient" is {@code SearchPatientByMRNo()}).
     *
     * @return rows accepted (&gt;0) or a negative code: -1 screen, -2 no order found for the MRN, -3 Accept missing
     */
    public int acceptWorkOrders(String hashRoute, String mrn, String groupRe) {
        if (!openScreen("Investigation", hashRoute, hashRoute.replace("#/", ""))) { lastDump = "screen " + hashRoute + " not reached"; return -1; }
        watchToasts();
        try { page.waitForFunction("() => document.getElementById('textinput') && document.getElementById('textinput').offsetParent!==null", null, new Page.WaitForFunctionOptions().setTimeout(15000)); }
        catch (Exception ignore) { }
        page.evaluate("(m) => { " + JS + " setEl(document.getElementById('textinput'), m); }", mrn);
        page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/SearchPatientByMRNo\\(\\)/.test(x.getAttribute('ng-click')||'') && vis(x)); if(b) b.click(); }");
        waitForAngular(1200);
        // Status = All so an already-listed order is not filtered out; then Search.
        // The screen defaults to Group = Laboratory on both routes: choose the group this list is meant for.
        page.evaluate("(g) => { " + JS + " setSel(ngEl('PathologyWorkOrder.groupid')[0], new RegExp(g,'i')); }", groupRe);
        waitForAngular(800);
        page.evaluate("() => { " + JS + " setSel(ngEl('PathologyWorkOrder.isorderacceptreject')[0], /pending|all/i); }");
        page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/fnSearchorderbookingdetails\\(1\\)/.test(x.getAttribute('ng-click')||'') && vis(x)); if(b) b.click(); }");
        waitForAngular(4000);
        // Count this patient's rows, then press the TICK symbol (row-select button) beside each of them with a real mouse
        // click - the tick sits in the pinned left column, so it is matched to the row by its on-screen position.
        Object n = page.evaluate("(m) => { " + JS + " const g=gridScope(); if(!g) return 0; return g.grid.rows.filter(r=>JSON.stringify(r.entity).indexOf(m)>=0).length; }", mrn);
        int rows = n == null ? 0 : ((Number) n).intValue();
        if (rows == 0) { lastDump = describeVisibleControls(); return -2; }
        waitForAngular(800);
        Object ticks = page.evaluate("(m) => { " + JS
                + " const out=[]; [...document.querySelectorAll('[ng-click*=selectButtonClick]')].forEach((b,i)=>{ try{ const row=angular.element(b).scope().row;"
                + "   if(row && JSON.stringify(row.entity).indexOf(m)>=0){ b.scrollIntoView({block:'nearest'}); const q=b.getBoundingClientRect();"
                + "     out.push({k:i,x:q.left+q.width/2,y:q.top+q.height/2,sel:b.classList.contains('ui-grid-row-selected')||!!row.isSelected}); } }catch(e){} });"
                + " return out; }", mrn);
        if (!(ticks instanceof List) || ((List<?>) ticks).isEmpty()) {
            // fall back: the n-th rendered row of this MRN -> the n-th tick button in the pinned column
            ticks = page.evaluate("(m) => { " + JS + " const g=gridScope(); if(!g) return []; const rr=(g.grid.renderContainers&&g.grid.renderContainers.body&&g.grid.renderContainers.body.renderedRows)||[];"
                    + " const btns=[...document.querySelectorAll('.ui-grid-canvas .ui-grid-selection-row-header-buttons')]; const out=[];"
                    + " rr.forEach((r,i)=>{ if(JSON.stringify(r.entity).indexOf(m)>=0 && btns[i]){ btns[i].scrollIntoView({block:'nearest'}); const q=btns[i].getBoundingClientRect(); out.push({k:i,x:q.left+q.width/2,y:q.top+q.height/2,sel:btns[i].classList.contains('ui-grid-row-selected')}); } });"
                    + " return out; }", mrn);
        }
        int clicked = 0;
        if (ticks instanceof List) {
            for (Object o : (List<?>) ticks) {
                Map<?, ?> t = (Map<?, ?>) o;
                if (Boolean.TRUE.equals(t.get("sel"))) { clicked++; continue; }
                page.mouse().click(((Number) t.get("x")).doubleValue(), ((Number) t.get("y")).doubleValue());
                clicked++;
                waitForAngular(500);
            }
        }
        System.out.println("acceptWorkOrders: ticked " + clicked + " row(s) for MRN " + mrn + " of " + rows);
        if (clicked == 0) {
            lastDump = "no tick located for MRN " + mrn + " | " + page.evaluate("(m) => { " + JS
                    + " const b=[...document.querySelectorAll('[ng-click*=selectButtonClick]')]; const g=gridScope();"
                    + " return 'selectButtonClick elements=' + b.length + ' (visible ' + b.filter(vis).length + '); grid rows=' + (g? g.grid.rows.length : '?')"
                    + " + '; first rows: ' + (g? g.grid.rows.slice(0,3).map(r=>JSON.stringify({m:r.entity.MRNo||r.entity.MRNO||r.entity.mrno, n:r.entity.PatientName||r.entity.patientname, s:r.entity.ServiceName||r.entity.servicename||r.entity.serviceitemname})).join(' ; ') : ''); }", mrn);
        }
        waitForAngular(800);
        Object acc = page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/addsampleAccept\\(\\)/.test(x.getAttribute('ng-click')||'') && vis(x)); if(!b) return false; b.click(); return true; }");
        if (!Boolean.TRUE.equals(acc)) { lastDump = describeVisibleControls(); return -3; }
        waitForAngular(1500);
        answerDialogs(8);
        waitForAngular(1500);
        toasts();
        return rows;
    }

    /**
     * Inventory &gt; POS &gt; <b>Dispense Process</b> ({@code #/MedicationOrder}), document C3 "Medicine": Progress =
     * Screening, advanced filter, prescribing store = pharmacy main store, Search, tick the patient and each drug,
     * tick <i>Screened</i> (MedicationOrder.ChkFullyScreened), then Save. Returns "" on success else the reason.
     */
    public String screenDrugs(String mrn) {
        if (!openScreen("Inventory", "#/MedicationOrder", "MedicationOrder")) return "Dispense Process screen not reached";
        watchToasts();
        page.evaluate("(m) => { " + JS + " setEl(ngEl('MedicationOrder.MRNO')[0], m); }", mrn);
        page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/SearchPatientByMRNo\\(\\)/.test(x.getAttribute('ng-click')||'') && vis(x)); if(b) b.click(); }");
        waitForAngular(1200);
        page.evaluate("() => { " + JS + " setSel(ngEl('MedicationOrder.Progress')[0], /screening/i); }");
        page.evaluate("() => { " + JS + " const t=document.getElementById('toggle-filter-btn'); if(t && /show advanced/i.test(t.innerText)) t.click(); }");
        waitForAngular(800);
        // Prescribing Store (document: "Select the prescribing store (pharmacy main store)") - NOT the Dispensing Store next to it.
        Object store = page.evaluate("() => { " + JS + " const lab=[...document.querySelectorAll('label,span,div')].filter(vis).find(e=>e.children.length===0 && /^\s*Prescribing Store\s*$/i.test(e.textContent||''));"
                + " if(!lab) return 'label not found'; let box=lab.parentElement; let sel=null; for(let i=0;i<4 && box && !sel;i++){ sel=[...box.querySelectorAll('select')].filter(vis).find(x=>[...x.options].some(o=>/pharmacy main store/i.test(o.text))); box=box.parentElement; }"
                + " return sel ? setSel(sel,/pharmacy main store/i) : 'select not found'; }");
        page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/Getprescriptionlistsearch\\(\\)/.test(x.getAttribute('ng-click')||'') && vis(x)); if(b) b.click(); }");
        waitForAngular(4000);
        // Tick the patient's prescription (checkbox in the list) then each populated drug, then Screened.
        Object listed = page.evaluate("() => { " + JS + " const hdr=[...document.querySelectorAll('div,h3,h4')].find(e=>e.children.length<6 && /^\s*List Of Prescription\s*$/i.test((e.firstChild&&e.firstChild.textContent)||e.textContent||''));"
                + " const panel=hdr? (hdr.closest('.panel,.box,.card')||hdr.parentElement.parentElement) : document;"
                + " const c=[...panel.querySelectorAll('table tbody input[type=checkbox], .ui-grid-canvas input[type=checkbox]')].filter(vis);"
                + " c.forEach(e=>{ if(!e.checked) e.click(); }); return c.length; }");
        waitForAngular(1500);
        if (listed == null || ((Number) listed).intValue() == 0) {
            lastDump = "prescribing store: " + store + " | " + describeVisibleControls();
            return "no prescription listed for MRN " + mrn + " (prescribing store '" + store + "'); is the EMR prescription saved?";
        }
        Object drugs = page.evaluate("() => { " + JS + " const a=ngEl('MedicationOrder.ChkSelectAllPDrug')[0]; if(a && !a.checked) a.click(); return !!a; }");
        waitForAngular(800);
        page.evaluate("() => { " + JS + " const s=ngEl('MedicationOrder.ChkFullyScreened')[0]; if(s){ s.scrollIntoView({block:'center'}); if(!s.checked) s.click(); } }");
        waitForAngular(1500);
        answerDialogs(3);
        String screenedToast = toasts();
        // "Select the patient again and click Save"
        page.evaluate("() => { " + JS + " const c=[...document.querySelectorAll('input[type=checkbox]')].filter(vis).filter(e=>/IsSelectedPrescription/.test(e.getAttribute('ng-model')||'')); c.forEach(e=>{ if(!e.checked) e.click(); }); }");
        waitForAngular(800);
        Object saved = page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('button')].find(x=>vis(x)&&/^save$/i.test(norm(x.innerText))); if(!b) return false; b.click(); return true; }");
        waitForAngular(2000);
        answerDialogs(4);
        waitForAngular(1500);
        String t = toasts();
        if (!Boolean.TRUE.equals(saved)) { lastDump = describeVisibleControls(); return "Save button not found on Dispense Process"; }
        lastToast = (screenedToast.isEmpty() ? "" : "screened: " + screenedToast + " | ") + t;
        // Success = the app's own confirmation ("Medication Order insert successfully"); stray validation toasts from the
        // intermediate clicks ("Please Select Store!") are not the outcome.
        String all = (screenedToast + " | " + t).toLowerCase();
        return all.contains("insert successfully") || all.contains("saved successfully") || all.contains("screened successfully") ? ""
                : "Screening was not confirmed: " + lastToast;
    }

    // ============================================================================================================
    //  C5  OPD Bill - Final bill + payment window
    // ============================================================================================================

    /** The OPD Bill header totals the document says must stay the same across charges / bill / receipt. */
    public String readBillTotals() {
        Object r = page.evaluate("() => { " + JS + " const v=m=>{ const e=ngEl(m)[0]; return e? (e.value||'') : ''; };"
                + " return 'Total ' + v('PatientDetails.TotalAmount') + ' | Discount ' + v('PatientDetails.TotalConcessionAmount') + ' | Self ' + v('PatientDetails.SelfPayAmount') + ' | Net ' + v('PatientDetails.TotalNetAmount'); }");
        Object net = page.evaluate("() => { " + JS + " const e=ngEl('PatientDetails.TotalNetAmount')[0]; return e? e.value : ''; }");
        lastNetAmount = net == null ? "" : net.toString().trim();
        return r == null ? "" : r.toString();
    }

    /** Document C5.6: make sure the <b>Final</b> radio (PatientDetails.isdraftbill = 1) is on and Credit is off. */
    public boolean selectFinalBill() {
        Object r = page.evaluate("() => { " + JS + " const f=ngEl('PatientDetails.isdraftbill').find(e=>e.value==='1'); if(!f) return false; if(!f.checked) f.click();"
                + " const cr=ngEl('IsCredite')[0]; if(cr && cr.checked) cr.click(); return f.checked; }");
        waitForAngular(400);
        return Boolean.TRUE.equals(r);
    }

    /**
     * Click Save ({@code IUDBill()}) on a FINAL bill and drive everything that follows by dialog text: the
     * interim/final confirm (Yes), cost-centre confirm (Validated), close-visit (No), IC-not-validated (Yes) and the
     * <b>payment window</b> the document describes ("Final: ... a payment window opens"). Success = a Bill No.
     * populated on the screen. {@link #lastPaymentNote} says whether the payment window appeared (so Stage 6 knows
     * whether the receipt was already raised here or must be settled on Payment Settlement &gt; Self).
     */
    public boolean saveFinalBill() {
        watchToasts();
        lastPaymentNote = "";
        payFatal = false;
        page.evaluate("() => { window.__bopPay = false; }");
        Object tagged = page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('button,a')].find(x=>/^IUDBill\\(\\)/.test((x.getAttribute('ng-click')||'').trim()) && vis(x)); if(!b) return false; b.scrollIntoView({block:'center'}); b.id='__bopSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { lastDump = describeVisibleControls(); return false; }
        try { page.locator("#__bopSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__bopSave'); if(e) e.removeAttribute('id'); }");
        for (int i = 0; i < 30; i++) {
            String did = answerBillDialogsOnce();
            if (payFatal) { lastPaymentNote = did; break; }
            if (!did.isEmpty()) { System.out.println("saveFinalBill: " + did); waitForAngular(900); }
            else waitForAngular(600);
            String bn = readBillNo();
            if (!bn.isEmpty()) { lastBillNo = bn; break; }
            if (did.isEmpty() && i > 12 && !anyDialogOpen()) break;
        }
        waitForAngular(1500);
        toasts();
        if (lastBillNo.isEmpty()) lastBillNo = readBillNo();
        // The screen clears the Bill No. box after a successful save, so the success signal is the app's own toast
        // ("Bill saved successfully."); a populated Bill No. is accepted too.
        String tl = lastToast.toLowerCase();
        boolean saved = (tl.contains("bill saved successfully") || tl.contains("saved successfully") || !lastBillNo.isEmpty())
                && !tl.contains("error") && !tl.contains("mandatory");
        if (!saved) lastDump = describeVisibleControls();
        return saved;
    }

    private String readBillNo() {
        Object v = page.evaluate("() => { " + JS + " const e=ngEl('PatientDetails.BillNo')[0]; return e? (e.value||'').trim() : ''; }");
        return v == null ? "" : v.toString().trim();
    }

    private boolean anyDialogOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].some(m=>m.getBoundingClientRect().width>0 && (m.textContent||'').trim())"));
    }

    private boolean payFatal = false;
    public String payModes = "";

    /** Payment Window (OPD Bill and Payment Settlement share it): Cash, Total Amount, Add, then OK - with real clicks/typing. */
    private String payInPaymentWindow() {
        try {
            if (!page.locator("select[ng-model='PaymentDetails.PaymentModeID']").first().isVisible()) return "";
        } catch (Exception e) { return ""; }
        Object added = page.evaluate("() => { const add=document.getElementById('btnAdd'); if(!add) return -1; try{ const sc=angular.element(add).scope(); const l=sc.PaymentDtlList||(sc.$parent&&sc.$parent.PaymentDtlList)||[]; return l.length; }catch(e){ return 0; } }");
        int n = added == null ? 0 : ((Number) added).intValue();
        try {
            if (n <= 0) {
                String all = String.valueOf(page.evaluate("() => [...document.querySelector(\"select[ng-model='PaymentDetails.PaymentModeID']\").options].map(o=>o.text.trim()).join(' | ')"));
                String label = String.valueOf(page.evaluate("() => { const s=document.querySelector(\"select[ng-model='PaymentDetails.PaymentModeID']\"); const real=[...s.options].filter(x=>x.value && !/^-*\\s*select/i.test(x.text.trim()));"
                        + " const o=real.find(x=>/^cash$/i.test(x.text.trim()))||real.find(x=>/cash/i.test(x.text))||real.find(x=>!/card|cheque|credit|debit/i.test(x.text))||real[0]; return o? o.text.trim() : ''; }"));
                if (label.isEmpty()) { payFatal = true; return "payment window: Payment Mode list has no usable option; options: [" + all + "]"; }
                if (!label.toLowerCase().contains("cash")) System.out.println("payInPaymentWindow: no Cash mode - using '" + label + "'; options: [" + all + "]");
                payModes = all;
                page.locator("select[ng-model='PaymentDetails.PaymentModeID']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label),
                        new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
                waitForAngular(600);
                String tot = String.valueOf(page.evaluate("() => { const e=document.querySelector(\"input[ng-model='PaymentDetails.PayTotalAmount']\"); return e? e.value : ''; }")).trim();
                var amt = page.locator("input[ng-model='PaymentDetails.Amount']").first();
                amt.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
                amt.fill(tot, new com.microsoft.playwright.Locator.FillOptions().setTimeout(5000));
                waitForAngular(500);
                // v4 C6.7: a card payment makes Transaction Number, Payor Bank, Credit Card Type and Date mandatory - fill whatever is empty.
                page.evaluate("() => { " + JS + " const box=document.querySelector('.modal.in, .modal') || document;"
                        + " const q=m=>[...box.querySelectorAll('[ng-model]')].find(e=>e.getAttribute('ng-model')===m);"
                        + " const num=q('PaymentDetails.Number'); if(num && vis(num) && !num.value) setEl(num,'TXN'+Date.now().toString().slice(-8));"
                        + " ['PaymentDetails.BankID','PaymentDetails.DepositBankID'].forEach(m=>{ const b=q(m); if(b && vis(b) && (!b.value || /^-*\\s*select/i.test((b.options[b.selectedIndex]||{}).text||''))) setSel(b,/./); });"
                        + " [...box.querySelectorAll('select')].filter(vis).filter(b=>/credit\\s*card\\s*type|cardtype/i.test((b.getAttribute('ng-model')||'')+' '+(b.id||'')+' '+((b.closest('div')||{}).innerText||'').slice(0,60))).forEach(b=>{ if(!b.disabled && (!b.value || /^-*\\s*select/i.test((b.options[b.selectedIndex]||{}).text||''))) setSel(b,/./); });"
                        + " const d=q('PaymentDetails.PayDate'); if(d && vis(d) && !d.value){ const t=new Date(); const f=n=>String(n).padStart(2,'0'); setEl(d, f(t.getDate())+'/'+f(t.getMonth()+1)+'/'+t.getFullYear()); } }");
                waitForAngular(700);
                page.locator("#btnAdd").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
                waitForAngular(900);
                lastPaymentNote = "payment window: " + label + " " + tot + " -> Add";
                return lastPaymentNote;
            }
            page.locator("#GetOk").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            lastPaymentNote = "payment window: payment added -> OK";
            return lastPaymentNote;
        } catch (Exception e) {
            return "payment window error: " + e.getMessage().split("\n")[0];
        }
    }

    /** Answer ONE dialog (if any) by its text; returns a short description of what was done, "" when none matched. */
    private String answerBillDialogsOnce() {
        // A "Bill Amount is Pending - Save Partial Amount?" confirm may stack on the payment window: answer it first.
        Object partial = page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].find(m=>m.getBoundingClientRect().width>0 && /save partial amount|amount is pending/i.test(m.textContent||''));"
                + " if(!box) return false; const b=[...box.querySelectorAll('button,a')].find(x=>/^yes$/i.test((x.textContent||'').trim()) && x.getBoundingClientRect().width>0); if(!b) return false; b.click(); return true; }");
        if (Boolean.TRUE.equals(partial)) return "partial/pending amount confirm -> Yes";
        String pw = payInPaymentWindow();
        if (!pw.isEmpty()) return pw;
        Object r = page.evaluate("() => { " + JS
                + " const boxes=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].filter(m=>m.getBoundingClientRect().width>0);"
                + " const click=(box,re)=>{ const b=[...box.querySelectorAll('button,a')].find(x=>vis(x)&&re.test(norm(x.textContent))); if(b){ b.click(); return true;} return false; };"
                // A confirm stacked on top of the payment window must be answered before the window behind it.
                + " for (const box of boxes) { const t0=norm(box.textContent).toLowerCase(); if(/save partial amount|amount is pending/.test(t0)){ if(click(box,/^yes$/i)) return 'partial/pending amount confirm -> Yes'; } }"
                + " for (const box of boxes) { const txt=norm(box.textContent).toLowerCase();"
                + "   if (/proceed saving the (interim|final) bill/.test(txt)) { if(click(box,/^yes$/i)) return 'confirm final bill -> Yes'; }"
                + "   else if (/cost centers are defined/.test(txt)) { if(click(box,/^validated$/i)) return 'cost centres -> Validated'; }"
                + "   else if (/do you want to close a visit/.test(txt)) { if(click(box,/^no$/i)) return 'close visit -> No'; }"
                + "   else if (/do you want to close the admission/.test(txt)) { if(click(box,/^no$/i)) return 'close admission -> No'; }"
                + "   else if (/admission is closed/.test(txt)) { if(click(box,/^ok$/i)) return 'admission is closed -> OK'; }"
                + "   else if (/ic number which is not validated/.test(txt)) { if(click(box,/^yes$/i)) return 'IC not validated -> Yes'; }"
                + "   else if (/payment|pay mode|payment mode|amount paid|tender/.test(txt)) { return 'PAYMENT:' + paymentWindow(box); }"
                + " } return ''; "
                + " function lab(box,re){ const l=[...box.querySelectorAll('label,span,td,div')].find(e=>e.children.length===0 && re.test(norm(e.textContent)) && vis(e)); if(!l) return null;"
                + "   let c=l.parentElement; for(let i=0;i<3&&c;i++){ const f=[...c.querySelectorAll('input,select')].filter(vis).find(x=>!x.type||x.type!=='hidden'); if(f) return f; c=c.parentElement; } return null; }"
                + " function fire(e){ try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(e.value); c.$render(); } }catch(x){} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " function paymentWindow(box){"
                // The Payment Window: Payment Mode defaults to Card and Amount to 0.00 - choose Cash, enter the Total Amount, Add, then OK.
                + "   if(/payment window/i.test(norm(box.textContent))){"
                // Exact controls (read live): PaymentDetails.PaymentModeID / .Amount / .PayTotalAmount, #btnAdd -> AddPaymentDetails, #GetOk -> SavePayment
                + "     const add=box.querySelector('#btnAdd'); const okb=box.querySelector('#GetOk');"
                + "     let list=[]; try{ const sc=angular.element(add).scope(); list=sc.PaymentDtlList || (sc.$parent&&sc.$parent.PaymentDtlList) || []; }catch(x){}"
                + "     if(!list.length){"
                + "       const q=m=>[...box.querySelectorAll('[ng-model]')].find(e=>e.getAttribute('ng-model')===m);"
                + "       const mode=q('PaymentDetails.PaymentModeID'); const amt=q('PaymentDetails.Amount'); const tot=q('PaymentDetails.PayTotalAmount')||q('PaymentDetails.PayableAmount');"
                + "       if(mode){ const o=[...mode.options].find(x=>/^cash$/i.test(x.text.trim())) || [...mode.options].find(x=>/cash/i.test(x.text)); if(o){ mode.value=o.value; fire(mode); } }"
                + "       if(amt && tot){ amt.value=tot.value; fire(amt); }"
                + "       if(add){ add.click(); return 'payment window: Cash '+(tot?tot.value:'?')+' -> Add'; } return 'payment window: Add button not found'; }"
                + "     if(okb){ okb.click(); return 'payment window: payment added -> OK'; } return 'payment window open, OK not found'; }"
                + "   const ok=[...box.querySelectorAll('button,a')].find(x=>vis(x)&&/^(save|ok|pay|submit|confirm|settle)$/i.test(norm(x.textContent)));"
                + "   if(ok){ ok.click(); return 'payment dialog -> '+norm(ok.textContent); } return 'payment dialog open, no confirm button'; } }");
        String s = r == null ? "" : r.toString();
        if (s.startsWith("PAYMENT:")) { lastPaymentNote = s.substring(8); return lastPaymentNote; }
        return s;
    }

    // ============================================================================================================
    //  C6  Billing > Payment Settlement > Self
    // ============================================================================================================

    /** Visible rows of the settlement tables (plain HTML tables, not ui-grid) whose text contains the MRN. */
    @SuppressWarnings("unchecked")
    private List<String> settlementRows(String mrn) {
        Object r = page.evaluate("(m) => { " + JS + " return [...document.querySelectorAll('table tbody tr')].filter(vis).map(tr=>norm(tr.innerText)).filter(t=>t.indexOf(m)>=0); }", mrn);
        return r instanceof List ? (List<String>) r : new java.util.ArrayList<>();
    }

    private void searchSettlement(String mrn) {
        page.evaluate("(m) => { " + JS + " setEl(ngEl('PatientData.MRNO')[0], m); }", mrn);
        page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/FindPatient\\(\\)/.test(x.getAttribute('ng-click')||'') && vis(x)); if(b) b.click(); }");
        waitForAngular(1500);
        page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/fnFillBillDetails\\(\\)/.test(x.getAttribute('ng-click')||'') && vis(x)); if(b) b.click(); }");
        waitForAngular(3500);
    }

    /**
     * Document C6: search the MRN, select the outstanding bill, pick a cash counter, <b>Settle Bill</b>, complete
     * the payment window. The bill is found by reading the Service Bills table (a plain HTML table); its Bill No.
     * is kept in {@link #lastBillNo}. If no bill is outstanding it is reported through {@link #lastPaymentNote}.
     *
     * @return "settled", "already-settled" or a failure reason
     */
    public String settleBillSelf(String mrn) {
        if (!openScreen("Billing", "#/SelfPyamentSettlement", "SelfPyamentSettlement")) return "Payment Settlement > Self screen not reached";
        watchToasts();
        searchSettlement(mrn);
        List<String> rows = settlementRows(mrn);
        for (int i = 0; i < 6 && rows.isEmpty(); i++) { waitForAngular(1500); rows = settlementRows(mrn); }
        if (rows.isEmpty()) { lastPaymentNote = "no outstanding bill for MRN " + mrn + " (already paid, e.g. in the OPD Bill payment window)"; return "already-settled"; }
        java.util.regex.Matcher bm = java.util.regex.Pattern.compile("OPB\\d+").matcher(rows.get(0));
        if (bm.find()) lastBillNo = bm.group();
        // tick the bill row, then Settle Bill
        page.evaluate("(m) => { " + JS + " const tr=[...document.querySelectorAll('table tbody tr')].filter(vis).find(t=>norm(t.innerText).indexOf(m)>=0); if(!tr) return; const c=tr.querySelector('input[type=checkbox]'); if(c && !c.checked) c.click(); }", mrn);
        waitForAngular(1200);
        Object cc = page.evaluate("() => { " + JS + " const s=ngEl('PatientData.CashCounterId')[0]; if(!s) return ''; if(!s.value || /^-*\\s*select/i.test((s.options[s.selectedIndex]||{}).text||'')) return setSel(s,/./); return s.options[s.selectedIndex].text.trim(); }");
        waitForAngular(600);
        Object net = page.evaluate("() => { " + JS + " const e=ngEl('BillDetails.netbillamount')[0]; return e? e.value : ''; }");
        Object clicked = page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/fnIUDBill\\(\\)/.test(x.getAttribute('ng-click')||'') && vis(x)); if(!b) return false; b.scrollIntoView({block:'center'}); b.click(); return true; }");
        if (!Boolean.TRUE.equals(clicked)) { lastDump = describeVisibleControls(); return "Settle Bill button not found"; }
        lastPaymentNote = "";
        payFatal = false;
        page.evaluate("() => { window.__bopPay = true; }");           // pay in the window only here (Stage 6)
        for (int i = 0; i < 20; i++) {
            waitForAngular(900);
            String did = answerBillDialogsOnce();
            if (payFatal) { lastPaymentNote = did; break; }
            if (!did.isEmpty()) System.out.println("settleBillSelf: " + did);
            if (did.isEmpty() && i > 6 && !anyDialogOpen()) break;
        }
        waitForAngular(1500);
        page.evaluate("() => { window.__bopPay = false; }");
        String t = toasts();
        lastPaymentNote = "Bill " + lastBillNo + " (net " + net + "), cash counter '" + cc + "'; payment: "
                + (lastPaymentNote.isEmpty() ? "window not detected" : lastPaymentNote) + (t.isEmpty() ? "" : "; toast: " + t);
        String tl = t.toLowerCase();
        if (looksLikeError(t) && !tl.contains("success")) { lastDump = describeVisibleControls(); return "Settle Bill reported: " + t; }
        if (!tl.contains("success") && !tl.contains("settle") && !tl.contains("receipt")) { lastDump = describeVisibleControls(); return "Settle Bill was not confirmed: " + (t.isEmpty() ? "(no toast)" : t); }
        return "settled";
    }

    /**
     * Final validation: with the MRN loaded on Payment Settlement &gt; Self, the bill must NOT be listed under
     * <i>List of Outstanding Bills</i> and must appear under <i>List of Payments</i> (the receipt).
     */
    public String[] verifySettled(String mrn, String billNo) {
        openScreen("Billing", "#/SelfPyamentSettlement", "SelfPyamentSettlement");
        searchSettlement(mrn);
        List<String> outstanding = settlementRows(mrn);
        boolean stillOutstanding = billNo != null && !billNo.isBlank() ? outstanding.stream().anyMatch(r -> r.contains(billNo)) : !outstanding.isEmpty();
        page.evaluate("() => { " + JS + " const a=[...document.querySelectorAll('[ng-click]')].find(x=>/IsPaymentEnabled=1/.test(x.getAttribute('ng-click')||'') && vis(x)); if(a) a.click(); }");
        waitForAngular(1500);
        page.evaluate("() => { " + JS + " const b=[...document.querySelectorAll('[ng-click]')].find(x=>/fnFillBillDetails\\(\\)/.test(x.getAttribute('ng-click')||'') && vis(x)); if(b) b.click(); }");
        waitForAngular(3500);
        List<String> payments = settlementRows(mrn);
        for (int i = 0; i < 5 && payments.isEmpty(); i++) { waitForAngular(1500); payments = settlementRows(mrn); }
        final List<String> pay = payments;
        boolean paid = billNo != null && !billNo.isBlank() ? pay.stream().anyMatch(r -> r.contains(billNo)) : !pay.isEmpty();
        boolean ok = !stillOutstanding && paid;
        return new String[]{ ok ? "PASS" : "FAIL",
                "Bill " + (billNo == null || billNo.isBlank() ? "(no. unknown)" : billNo) + ": " + (stillOutstanding ? "STILL listed under Outstanding Bills" : "not outstanding")
                        + "; " + (paid ? "found under List of Payments (" + pay.size() + " payment row(s))" : "NOT found under List of Payments (" + pay.size() + " row(s))") };
    }

    /**
     * OOS-12.9 receipt check. Looks for the receipt tab opened by the payment (a new tab holding a PDF), reads its text with
     * PDFBox and reports what it says: the heading ("Official Receipt" for the original) and whether the footer
     * "Kindly keep the original receipt properly, as it can only be printed once ..." is present. Returns null when no
     * receipt tab was found (the caller records that as MANUAL - it cannot be judged from here).
     */
    public String[] readOriginalReceipt() {
        Page tab = null;
        for (Page pg : page.context().pages()) {
            if (pg == page || pg.isClosed()) continue;
            String u = pg.url().toLowerCase();
            if (u.contains("receipt") || u.contains("report") || u.endsWith(".pdf") || u.contains("rpt")) tab = pg;
        }
        if (tab == null) return null;
        try {
            tab.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(15000));
            Object b64 = tab.evaluate("() => fetch(location.href,{credentials:'include'}).then(r=>r.arrayBuffer()).then(b=>{ let s=''; const a=new Uint8Array(b); for(let i=0;i<a.length;i+=32768) s+=String.fromCharCode.apply(null,a.subarray(i,i+32768)); return btoa(s); })");
            byte[] pdf = java.util.Base64.getDecoder().decode(String.valueOf(b64));
            String text;
            try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
                text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replaceAll("\\s+", " ").trim();
            }
            String tl = text.toLowerCase();
            boolean official = tl.contains("official receipt");
            boolean duplicate = tl.contains("duplicate");
            boolean footer = tl.contains("can only be printed once");
            boolean prejudice = tl.contains("without prejudice");
            boolean paymode = tl.contains("paymode");
            boolean invDate = tl.contains("invoice number-date") || tl.contains("invoice number - date") || tl.contains("invoice number date");
            boolean ok = official && !duplicate && footer && !prejudice && paymode && invDate;
            return new String[]{ ok ? "PASS" : "FAIL", "Receipt tab " + tab.url() + ": heading 'Official Receipt' " + (official ? "present" : "MISSING")
                    + "; 'Duplicate' " + (duplicate ? "PRESENT (expected absent on the original)" : "absent")
                    + "; footer 'Kindly keep the original receipt ... can only be printed once' " + (footer ? "present" : "MISSING")
                    + "; 'Without Prejudice' " + (prejudice ? "PRESENT (only for legally-unblocked patients)" : "absent (correct for a normal patient)")
                    + "; column 'Paymode' " + (paymode ? "present" : "MISSING (OOS-12.9: 'Payment type' renamed)")
                    + "; column 'Invoice Number-Date' " + (invDate ? "present" : "MISSING (OOS-12.9: 'Paid for' renamed)") };
        } catch (Exception e) {
            return new String[]{ "FAIL", "Receipt tab found (" + tab.url() + ") but could not be read: " + e.getMessage().split("\\n")[0] };
        }
    }

    // ============================================================================================================
    //  shared helpers
    // ============================================================================================================

    /** Click through up to {@code max} generic confirmation dialogs (Yes/OK/Accept/Save/Mark Visit...), filling any
     *  required remark box first so an "Acceptance Remark"-style dialog cannot stall the flow. */
    public void answerDialogs(int max) {
        for (int i = 0; i < max; i++) {
            Object did = page.evaluate("() => { " + JS
                    + " const box=[...document.querySelectorAll('.modal,.ng-confirm-box,.jconfirm,.sweet-alert')].filter(m=>m.getBoundingClientRect().width>0 && norm(m.textContent)).pop();"
                    + " if(!box) return ''; const txt=norm(box.textContent);"
                    + " box.querySelectorAll('textarea,input[type=text]').forEach(e=>{ if(vis(e)&&!e.value&&!e.disabled&&!/date|time/i.test((e.getAttribute('ng-model')||'')+e.type)) setEl(e,'Auto - Billing OP flow'); });"
                    + " const b=[...box.querySelectorAll('button,a')].find(x=>vis(x)&&/^(yes|ok|accept|save|submit|confirm|mark visit|proceed|validated|close)$/i.test(norm(x.textContent)));"
                    + " if(!b) return ''; b.click(); return txt.slice(0,70)+' -> '+norm(b.textContent); }");
            String s = did == null ? "" : did.toString();
            if (s.isEmpty()) return;
            System.out.println("answerDialogs: " + s);
            waitForAngular(1000);
        }
    }

    /** Compact listing of the visible controls + any open dialog text — attached to a FAIL so locators can be fixed in one pass. */
    public String describeVisibleControls() {
        Object r = page.evaluate("() => { " + JS
                + " const dlg=[...document.querySelectorAll('.modal,.ng-confirm-box,.jconfirm,.sweet-alert')].filter(m=>m.getBoundingClientRect().width>0).map(m=>'DIALOG['+norm(m.textContent).slice(0,160)+']');"
                + " const ctl=[...document.querySelectorAll('input,select,textarea,button,[ng-click]')].filter(vis).filter(e=>!/setDatepickerDay|prevMonth|nextMonth/.test(e.getAttribute('ng-click')||''))"
                + "   .map(e=>e.tagName.toLowerCase()+'#'+e.id+'{m='+(e.getAttribute('ng-model')||'')+',c='+(e.getAttribute('ng-click')||'').slice(0,34)+',t='+norm(e.innerText||e.placeholder||'').slice(0,18)+'}');"
                + " return dlg.concat(ctl).slice(0,90).join(' ; '); }");
        return r == null ? "" : r.toString();
    }
}
