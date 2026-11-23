package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Birth Delivery Details</b> (route {@code #/BirthDeliveryDetails}) — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>Birth Delivery Details</b> → (enter mother's MRN) → fill the mandatory
 * <b>Newborn Registration</b> fields (Name {@code babyname}, Date of Birth {@code dateofbirth}, To Birth Time
 * {@code inputTime} scope var, Gender {@code genderid}, Birth Weight {@code birthweight}, Department
 * {@code departmentid} → Paediatrician {@code doctorid} cascade, Paediatric Doctor/Nurse {@code peddoctor_nurse})
 * → <b>Add</b> ({@code AddFileDetails}) → <b>Submit</b> ({@code IUDSaveReport}) → success toast.</p>
 */
public class BirthDeliveryDetails extends BasePage {

    public BirthDeliveryDetails(Page page) { super(page); }

    public String route = "", lastMrn = "", lastBaby = "", lastDept = "", lastDoctor = "", lastGender = "", lastToast = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateTo(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('a,li>a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/birth\\s*delivery|delivery\\s*detail/i.test(norm(x.textContent)) || /birthdelivery/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__bdMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__bdMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("BirthDelivery.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        }
        if (!page.url().toLowerCase().contains("birthdelivery")) {
            try { page.evaluate("() => { window.location.hash = '#/BirthDeliveryDetails'; }"); } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.getAttribute('ng-model')||'')==='BirthDelivery.babyname' && e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("BirthDelivery.nav: form not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("birthdelivery"); }

    // ---- helpers ---------------------------------------------------------

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    private void setScopeTime(String v) {
        setNg("inputTime", v);
        page.evaluate("(v) => { document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s && Object.prototype.hasOwnProperty.call(s,'inputTime')){ s.inputTime=v; } }catch(e){} }); let done=false; document.querySelectorAll('*').forEach(el=>{ if(done) return; try{ const s=angular.element(el).scope(); if(s){ s.$applyAsync?s.$applyAsync():(s.$apply&&s.$apply()); done=true; } }catch(e){} }); }", v);
    }

    /** Real selectOption of the first real option of {@code selNg} (polls). Returns the chosen text or a marker. */
    private String realSelectFirst(String selNg, int timeoutMs) {
        String sel = "select[ng-model='" + selNg + "']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception e) { return "(no-opt)"; }
        Object idx = page.evaluate("(s) => { const e=document.querySelector(s); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
        int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("realSelectFirst " + selNg + " failed: " + e.getMessage()); }
        waitForAngular(700);
        return chosen;
    }

    // ---- patient (optional mother) --------------------------------------

    /** Enter the mother's MRN and search. Returns true if the MRN was retained (patient loaded). */
    public boolean enterMrn(String mrn) {
        page.evaluate("(m) => { const A=window.angular; const e=[...document.querySelectorAll(\"input[ng-model='BirthDelivery.MRNo']\")].find(x=>x.offsetParent!==null); if(e){ const c=A.element(e).controller('ngModel'); e.value=m; if(c){c.$setViewValue(m);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } const b=[...document.querySelectorAll('button,a,i,span,img')].find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'')); if(b) b.id='__bdMrnSearch'; }", mrn);
        try { page.locator("#__bdMrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__bdMrnSearch'); if(e) e.removeAttribute('id'); }");
        String v = "";
        for (int p = 0; p < 8 && v.isEmpty(); p++) {
            page.waitForTimeout(600);
            Object o = page.evaluate("() => { const e=[...document.querySelectorAll(\"input[ng-model='BirthDelivery.MRNo']\")].find(x=>x.offsetParent!==null); return e?e.value:''; }");
            v = o == null ? "" : o.toString().trim();
        }
        if (!v.isEmpty()) { lastMrn = v; return true; }
        return false;
    }

    // ---- fill mandatory Newborn Registration ----------------------------

    /** Fill the mandatory Newborn Registration fields. */
    public String fillNewbornRegistration() {
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String baby = "BABY" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastBaby = baby;
        setNg("BirthDelivery.babyname", baby);                  // Name *
        setNg("BirthDelivery.dateofbirth", today);              // Date of Birth *
        setScopeTime("10:30");                                   // To Birth Time * (inputTime scope var)
        setNg("BirthDelivery.birthweight", "3.20");             // Birth Weight *
        setNg("BirthDelivery.peddoctor_nurse", "Auto Nurse");   // Paediatric Doctor/Nurse *
        // Gender *
        lastGender = realSelectFirst("BirthDelivery.genderid", 8000);
        waitForAngular(400);
        // Department * (174 opts) -> Paediatrician (doctorid) cascade
        lastDept = realSelectFirst("BirthDelivery.departmentid", 8000);
        waitForAngular(1200);   // let the doctor list load
        lastDoctor = realSelectFirst("BirthDelivery.doctorid", 8000);
        waitForAngular(500);
        return "Name=" + baby + " | DOB=" + today + " | ToBirthTime=10:30 | Gender=" + lastGender
                + " | Weight=3.20 | Dept=" + lastDept + " | Paediatrician=" + lastDoctor + " | PaedDoctorNurse=Auto Nurse";
    }

    // ---- add + submit ---------------------------------------------------

    /** Click <b>Add</b> ({@code AddFileDetails}). */
    public String clickAdd() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddFileDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.id='__bdAdd'; }");
        String toastAfterAdd = captureToastAround("__bdAdd");
        return toastAfterAdd.isEmpty() ? "Add clicked (rows=" + listRows() + ")" : "Add: " + toastAfterAdd;
    }

    public int listRows() {
        Object c = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(!s) return; ['birthlist','BirthList','linklist','newbornlist'].forEach(k=>{ if(Array.isArray(s[k])) n=Math.max(n,s[k].length); }); if(s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)) n=Math.max(n,s.grid.options.data.length); }catch(e){} }); return n; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Click a tagged button and capture any toast that appears (toastr cleared first). */
    private String captureToastAround(String tagId) {
        page.evaluate("() => { window.__bdToasts=[]; if(window.__bdObs) window.__bdObs.disconnect(); try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__bdToasts.includes(t)) window.__bdToasts.push(t); }); };"
                + " window.__bdObs=new MutationObserver(grab); window.__bdObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        try { page.locator("#" + tagId).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception e) { System.out.println(tagId + " click: " + e.getMessage()); }
        page.evaluate("(id) => { const e=document.getElementById(id); if(e) e.removeAttribute('id'); }", tagId);
        try {
            page.waitForFunction("() => (window.__bdToasts||[]).some(a=>/saved|succes|added|report|please|enter|select|required|mandatory|fill|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }
        Object r = page.evaluate("() => { const a=window.__bdToasts||[]; return a.find(x=>/saved|succes|added|report/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Submit</b> ({@code IUDSaveReport}) and return the success toast. */
    public String submitAndGetToast() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].find(x=>/IUDSaveReport/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.id='__bdSubmit'; }");
        lastToast = captureToastAround("__bdSubmit");
        // wait a bit longer for a save toast specifically
        try {
            page.waitForFunction("() => (window.__bdToasts||[]).some(a=>/saved|succes|added|report generated/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
            Object r = page.evaluate("() => { const a=window.__bdToasts||[]; return a.find(x=>/saved|succes|added|report/i.test(x)) || a[0] || ''; }");
            if (r != null && !r.toString().trim().isEmpty()) lastToast = r.toString().trim();
        } catch (Exception ignore) { }
        return lastToast;
    }
}
