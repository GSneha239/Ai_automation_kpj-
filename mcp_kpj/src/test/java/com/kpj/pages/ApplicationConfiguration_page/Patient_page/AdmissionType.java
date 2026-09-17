package com.kpj.pages.ApplicationConfiguration_page.Patient_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Patient &gt; <b>Admission Type</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Patient</b> (submenu) → <b>Admission Type</b> → <b>Add</b> →
 * enter Admission Type + select Location (KPJ) → <b>Submit</b> → success toast.</p>
 */
public class AdmissionType extends BasePage {

    public AdmissionType(Page page) { super(page); }

    public String route = "", lastCode = "", lastDescription = "";

    /** Admission Type descriptions are real hospital admission categories, not generic filler text. */
    private static final String[] DESCRIPTIONS = {
            "Elective Admission", "Emergency Admission", "Day Care Admission", "Planned Surgery Admission",
            "Maternity Admission", "Observation Admission", "ICU Admission", "Referral Admission"
    };

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // expand the "Patient" submenu parent
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*patient\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
        waitForAngular(900);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/admission\\s*type/i.test(norm(x.textContent)) || /admissiontype/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__atMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__atMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("AdmissionType.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        } else { System.out.println("AdmissionType.nav: menu link not found"); }
        waitForAngular(1200);
        return page.url().length() > 0;
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("admissiontype"); }

    /** Real-click the top <b>Add</b> ({@code AddAdmissionType}) → the add form ({@code #/add-admissionType}). */
    public boolean clickAdd() {
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddAdmissionType/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.id='__atAdd'; }");
        try { page.locator("#__atAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("clickAdd: " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__atAdd'); if(e) e.removeAttribute('id'); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='admissionType.Code')", null, new Page.WaitForFunctionOptions().setTimeout(12000)); } catch (Exception ignore) { }
        waitForAngular(800);
        return true;
    }

    /**
     * Enter Code + Admission Type (Description) and select Location = KPJ (multi-select). {@code attempt} shifts
     * BOTH values so a retry after an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "AT" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        // Always uniquely stamped, not just once the DESCRIPTIONS pool cycles — on this shared, long-lived
        // QA environment a fixed pool of canned descriptions gets exhausted permanently, so even attempt 0
        // needs a unique suffix (same lesson as InvestigationTemplate/Notification).
        lastDescription = DESCRIPTIONS[attempt % DESCRIPTIONS.length] + " "
                + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }; set('admissionType.Code',a.code); set('admissionType.Description',a.description); }",
                java.util.Map.of("code", code, "description", lastDescription));
        waitForAngular(300);
        // Location — multi-select; select KPJ (or first real option).
        String loc = "(n/a)";
        try {
            String sel = "select[ng-model='admissionType.AdmissionTypeLocationIDs']";
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/select|please/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(8000));
            loc = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return '(no)'; const A=window.angular; let opt=[...e.options].find(o=>/kpj/i.test((o.textContent||'').trim())) || [...e.options].find(o=>o.value && (o.textContent||'').trim() && !/select|please/i.test((o.textContent||'').trim())); if(!opt) return '(no-opt)'; opt.selected=true; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} return (opt.textContent||'').trim(); }", sel)).trim();
        } catch (Exception e) { System.out.println("fillDetails: Location select failed - " + e.getMessage()); }
        waitForAngular(400);
        return "Code=" + code + " | AdmissionType=" + lastDescription + " | Location=" + loc;
    }

    /** Click <b>Submit</b> ({@code fnIUDAdmissionType}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__atToasts=[]; if(window.__atObs) window.__atObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__atToasts.includes(t)) window.__atToasts.push(t); }); };"
                + " window.__atObs=new MutationObserver(grab); window.__atObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDAdmissionType/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__atSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__atSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__atToasts||[]).some(a=>/admission|type|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__atToasts||[]).includes(t)) (window.__atToasts=window.__atToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__atToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
