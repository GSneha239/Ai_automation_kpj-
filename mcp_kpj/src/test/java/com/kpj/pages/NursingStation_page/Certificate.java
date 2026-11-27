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

    /** True only when the screen's own Add button is drawn. */
    private static final String SCREEN_DRAWN = "() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddCertificate/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)";

    /**
     * Nursing Station &rarr; Certificate through the sidebar. The sidebar is built lazily after login, so each level is waited for and
     * clicked again if needed. There is deliberately no typed-hash fallback: the app answers it with "Reload page" / the dashboard
     * while the URL already reads {@code #/Certificate}.
     */
    public boolean navigateTo(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3; attempt++) {
            expandMenuEntry(null, "^\\s*nursing\\s*station\\s*$");
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const top=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); const li=top&&top.closest('li'); if(!li) return ''; const a=[...li.querySelectorAll('a[href]')].find(x=>(x.getAttribute('href')||'').toLowerCase()==='#/certificate'); if(!a) return ''; a.id='__certMenu'; return a.getAttribute('href'); }");
            if (href != null && !href.toString().isEmpty()) {
                route = href.toString();
                try { page.locator("#__certMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception e) { System.out.println("Certificate.nav: click failed - " + e.getMessage().split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__certMenu'); if(e) e.removeAttribute('id'); }");
                try {
                    page.waitForFunction(SCREEN_DRAWN, null, new Page.WaitForFunctionOptions().setTimeout(30000));
                    waitForAngular(800);
                    if (onScreen()) return true;
                } catch (Exception ignore) { }
            }
            System.out.println("Certificate.nav: attempt " + attempt + " - screen not shown (" + page.url() + ")");
            page.waitForTimeout(2000);
        }
        return false;
    }

    /** The exact route ({@code #/BirthCertificate} and {@code #/DeathCertificate} also contain "certificate") AND the Add button. */
    public boolean onScreen() { return page.url().toLowerCase().endsWith("#/certificate") && Boolean.TRUE.equals(page.evaluate(SCREEN_DRAWN)); }

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
