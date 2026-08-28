package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Certificate</b> (route {@code #/Certificate}) — Page Object.
 *
 * <p>Flow: list screen → top <b>Add</b> ({@code AddCertificate}) → enter MRN ({@code Certificate.MRNo}) + search
 * ({@code SearchPatientByMRNo}) → Certificate Template ({@code Certificate.Dischargeid}), Department
 * ({@code Certificate.departmentid}), Doctor ({@code Certificate.payableid}), Authenticate
 * ({@code Certificate.isfinalized}) → <b>Save</b> ({@code IUDDischargeSummaryDetail}) → the certificate report
 * opens in a new tab (no success toast — report opening = success).</p>
 */
public class Certificate extends BasePage {

    public Certificate(Page page) { super(page); }

    public String route = "", lastMrn = "", lastTemplate = "", lastDept = "", lastDoctor = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateTo(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('a,li>a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*certificate\\s*$/i.test(norm(x.textContent)) || /#\\/Certificate\\b/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__certMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__certMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("Certificate.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        }
        if (!page.url().toLowerCase().contains("certificate")) {
            try { page.evaluate("() => { window.location.hash = '#/Certificate'; }"); } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddCertificate/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("Certificate.nav: Add button not ready"); }
        waitForAngular(800);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("certificate"); }

    // ---- helpers ---------------------------------------------------------

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

    // ---- flow ------------------------------------------------------------

    /** Click the top <b>Add</b> ({@code AddCertificate}) to open the form. */
    public boolean clickTopAdd() {
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddCertificate/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.id='__certAdd'; }");
        try { page.locator("#__certAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("clickTopAdd: " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__certAdd'); if(e) e.removeAttribute('id'); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.getAttribute('ng-model')||'')==='Certificate.MRNo' && e.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(10000)); } catch (Exception ignore) { }
        waitForAngular(800);
        return true;
    }

    /** Enter the MRN and click the search button ({@code SearchPatientByMRNo}); wait until the PATIENT actually
     *  loads (a patientid / patient name is set — the MRN field alone keeps whatever was typed). */
    public boolean enterMrnAndSearch(String mrn) {
        page.evaluate("(m) => { const A=window.angular; const e=[...document.querySelectorAll(\"input[ng-model='Certificate.MRNo']\")].find(x=>x.offsetParent!==null); if(e){ const c=A.element(e).controller('ngModel'); e.value=m; if(c){c.$setViewValue(m);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const mrnIn=[...document.querySelectorAll(\"input[ng-model='Certificate.MRNo']\")].find(x=>x.offsetParent!==null);"
                + " const spans=[...document.querySelectorAll('span.glyphicon.glyphicon-search, span.glyphicon-search')].filter(s=>s.offsetParent!==null);"
                + " let target=spans[0]; if(mrnIn){ let best=1e9; spans.forEach(s=>{ const d=Math.abs(s.getBoundingClientRect().top - mrnIn.getBoundingClientRect().top); if(d<best){ best=d; target=s; } }); } if(target) target.id='__certSearch'; }", mrn);
        try { page.locator("#__certSearch").scrollIntoViewIfNeeded(); } catch (Exception ignore) { }
        try { page.locator("#__certSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { try { page.locator("input[ng-model='Certificate.MRNo']").first().press("Enter"); } catch (Exception ignore) { } }
        page.evaluate("() => { const e=document.getElementById('__certSearch'); if(e) e.removeAttribute('id'); }");
        // wait for the patient to actually load (patientid / patientname set on the Certificate scope)
        boolean loaded = false;
        for (int p = 0; p < 12 && !loaded; p++) {
            page.waitForTimeout(600);
            Object o = page.evaluate("() => { let pid=0, pname=''; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.Certificate){ const c=s.Certificate; if(c.patientid||c.PatientID||c.patientId) pid=c.patientid||c.PatientID||c.patientId; if(c.patientname||c.PatientName||c.FirstName) pname=c.patientname||c.PatientName||c.FirstName; } }catch(e){} }); return {pid, pname}; }");
            @SuppressWarnings("unchecked") java.util.Map<String,Object> m = (java.util.Map<String,Object>) o;
            boolean hasPid = m != null && m.get("pid") != null && !m.get("pid").toString().isEmpty() && !m.get("pid").toString().equals("0");
            boolean hasName = m != null && m.get("pname") != null && !m.get("pname").toString().trim().isEmpty();
            if (hasPid || hasName) loaded = true;
        }
        if (loaded) { lastMrn = mrn; return true; }
        System.out.println("enterMrnAndSearch: patient did not load for MRN " + mrn);
        return false;
    }

    /** Select Certificate Template, Department, Doctor and tick Authenticate. */
    public String fillFields() {
        lastTemplate = realSelectFirst("Certificate.Dischargeid", 8000);   // Certificate Template
        waitForAngular(500);
        lastDept = realSelectFirst("Certificate.departmentid", 8000);      // Department
        waitForAngular(900);
        lastDoctor = realSelectFirst("Certificate.payableid", 8000);       // Doctor
        waitForAngular(500);
        // Authenticate checkbox
        String auth = "(no)";
        Object tagged = page.evaluate("() => { const cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='Certificate.isfinalized']\")].find(x=>x.offsetParent!==null); if(!cb) return false; cb.id='__certAuth'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__certAuth").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(4000)); auth = "ticked"; }
            catch (Exception e) { try { page.locator("#__certAuth").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); auth = "clicked"; } catch (Exception ignore) { } }
            page.evaluate("() => { const e=document.getElementById('__certAuth'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(400);
        return "Template=" + lastTemplate + " | Department=" + lastDept + " | Doctor=" + lastDoctor + " | Authenticate=" + auth;
    }

    /** Click <b>Save</b> ({@code IUDDischargeSummaryDetail}); capture the certificate report opening in a new tab. */
    public String lastToast = "";
    public Page saveAndOpenReport() {
        page.evaluate("() => { window.__certToasts=[]; if(window.__certObs) window.__certObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__certToasts.includes(t)) window.__certToasts.push(t); }); };"
                + " window.__certObs=new MutationObserver(grab); window.__certObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        int before = page.context().pages().size();
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].find(x=>/IUDDischargeSummaryDetail/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__certSave'; return true; }");
        Page report = null;
        if (Boolean.TRUE.equals(tagged)) {
            try {
                report = page.context().waitForPage(() -> {
                    try { page.locator("#__certSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
                });
            } catch (Exception e) { System.out.println("saveAndOpenReport: no new tab - " + e.getClass().getSimpleName()); }
        } else { System.out.println("saveAndOpenReport: Save button not found"); }
        try { page.waitForFunction("() => (window.__certToasts||[]).length>0", null, new Page.WaitForFunctionOptions().setTimeout(3000)); } catch (Exception ignore) { }
        Object t = page.evaluate("() => { const a=window.__certToasts||[]; return a.find(x=>/saved|success|please|select|enter|required|mandatory|error/i.test(x)) || a[0] || ''; }");
        lastToast = t == null ? "" : t.toString().trim();
        if (report == null && page.context().pages().size() > before) report = page.context().pages().get(page.context().pages().size() - 1);
        if (report != null) { try { report.waitForLoadState(); report.waitForTimeout(2500); } catch (Exception ignore) { } }
        return report;
    }
}
