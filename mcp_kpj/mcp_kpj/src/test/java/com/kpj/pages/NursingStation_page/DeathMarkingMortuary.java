package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Death Marking / Mortuary</b> (list {@code #/DeathMarkingList}, add {@code #/add-DeathMarking})
 * — Page Object.
 *
 * <p>Flow: list → top <b>Add</b> ({@code AddDeathMarkingMaster}) → enter MRN + search magnifier → fill Death Marking
 * (Time {@code inputTime} scope var, Natural/Unnatural, Post-mortem, Remark) + Mortuary (In/Handover Date-Time,
 * Mortuary Cabin No. {@code Mortuary.mortuarycabinno}, Handover To Relationship {@code Mortuary.relationid} +
 * {@code Mortuary.handoverto}, Template {@code Mortuary.template}) → <b>Save</b> ({@code fnSaveDeathMarking})
 * → success toast.</p>
 */
public class DeathMarkingMortuary extends BasePage {

    public DeathMarkingMortuary(Page page) { super(page); }

    public String route = "", lastMrn = "", lastRelation = "", lastTemplate = "", lastToast = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateTo(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('a,li>a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        // "Death Marking / Mortuary" — NOT "Death Certificate".
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>{ const t=norm(x.textContent); return (/death\\s*mark|mortuary/i.test(t) || /deathmarking|mortuary/i.test(x.getAttribute('href')||'')) && !/certificate/i.test(t); }); if(!a) return ''; a.id='__dmMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__dmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("DeathMarking.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        }
        if (!page.url().toLowerCase().contains("deathmarking")) {
            try { page.evaluate("() => { window.location.hash = '#/DeathMarkingList'; }"); } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddDeathMarkingMaster/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("DeathMarking.nav: Add button not ready"); }
        waitForAngular(800);
        return page.url().toLowerCase().contains("deathmarking");
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("deathmarking"); }

    // ---- helpers ---------------------------------------------------------

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    /** Set the {@code inputTime} scope var (shared by Time / In Date Time / Handover Date Time). */
    private void setScopeTime(String v) {
        setNg("inputTime", v);
        page.evaluate("(v) => { document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s && Object.prototype.hasOwnProperty.call(s,'inputTime')) s.inputTime=v; }catch(e){} }); let done=false; document.querySelectorAll('*').forEach(el=>{ if(done) return; try{ const s=angular.element(el).scope(); if(s){ s.$applyAsync?s.$applyAsync():(s.$apply&&s.$apply()); done=true; } }catch(e){} }); }", v);
    }

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
        waitForAngular(600);
        return chosen;
    }

    // ---- flow ------------------------------------------------------------

    /** Click the top <b>Add</b> ({@code AddDeathMarkingMaster}) → the add form ({@code #/add-DeathMarking}). */
    public boolean clickTopAdd() {
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddDeathMarkingMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.id='__dmAdd'; }");
        try { page.locator("#__dmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("clickTopAdd: " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__dmAdd'); if(e) e.removeAttribute('id'); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.getAttribute('ng-model')||'')==='deathmarking.MRNo' && e.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(10000)); } catch (Exception ignore) { }
        waitForAngular(800);
        return page.url().toLowerCase().contains("add-deathmarking");
    }

    /** Enter the MRN and click the search magnifier ({@code span.glyphicon-search}); wait until the patient loads. */
    public boolean enterMrnAndSearch(String mrn) {
        page.evaluate("(m) => { const A=window.angular; const e=[...document.querySelectorAll(\"input[ng-model='deathmarking.MRNo']\")].find(x=>x.offsetParent!==null); if(e){ const c=A.element(e).controller('ngModel'); e.value=m; if(c){c.$setViewValue(m);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const mrnIn=[...document.querySelectorAll(\"input[ng-model='deathmarking.MRNo']\")].find(x=>x.offsetParent!==null);"
                + " const spans=[...document.querySelectorAll('span.glyphicon.glyphicon-search, span.glyphicon-search')].filter(s=>s.offsetParent!==null);"
                + " let target=spans[0]; if(mrnIn){ let best=1e9; spans.forEach(s=>{ const d=Math.abs(s.getBoundingClientRect().top - mrnIn.getBoundingClientRect().top); if(d<best){best=d;target=s;} }); } if(target) target.id='__dmSearch'; }", mrn);
        try { page.locator("#__dmSearch").scrollIntoViewIfNeeded(); } catch (Exception ignore) { }
        try { page.locator("#__dmSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { try { page.locator("input[ng-model='deathmarking.MRNo']").first().press("Enter"); } catch (Exception ignore) { } }
        page.evaluate("() => { const e=document.getElementById('__dmSearch'); if(e) e.removeAttribute('id'); }");
        boolean loaded = false;
        for (int p = 0; p < 12 && !loaded; p++) {
            page.waitForTimeout(600);
            Object o = page.evaluate("() => { let pid=0, pname=''; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.deathmarking){ const c=s.deathmarking; if(c.patientid||c.PatientID||c.patientId) pid=c.patientid||c.PatientID||c.patientId; if(c.patientname||c.PatientName||c.FirstName) pname=c.patientname||c.PatientName||c.FirstName; } }catch(e){} }); return {pid, pname}; }");
            @SuppressWarnings("unchecked") java.util.Map<String,Object> m = (java.util.Map<String,Object>) o;
            boolean hasPid = m != null && m.get("pid") != null && !m.get("pid").toString().isEmpty() && !m.get("pid").toString().equals("0");
            boolean hasName = m != null && m.get("pname") != null && !m.get("pname").toString().trim().isEmpty();
            if (hasPid || hasName) loaded = true;
        }
        if (loaded) { lastMrn = mrn; return true; }
        System.out.println("enterMrnAndSearch: patient did not load for MRN " + mrn);
        return false;
    }

    /** Fill the mandatory Death Marking + Mortuary details. */
    public String fillDetails() {
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        // Death Marking
        setNg("deathmarking.date", today);
        setScopeTime("11:30");                              // Time* (also fills In/Handover Date-Time time inputs)
        setNg("deathmarking.remark", "Automation death marking");
        // Mortuary section
        setNg("Mortuary.indate", today);
        setNg("Mortuary.handoverdate", today);
        setNg("Mortuary.mortuarycabinno", "MC-01");         // Mortuary Cabin No.*
        lastRelation = realSelectFirst("Mortuary.relationid", 8000);   // Handover To (Relationship)*
        setNg("Mortuary.handoverto", "Family Member");      // Handover To*
        setNg("Mortuary.notes", "Automation notes");
        lastTemplate = realSelectFirst("Mortuary.template", 8000);     // Template*
        waitForAngular(800);
        // Commit the template via the inner Add (AddTemplateList) — Save needs "template details" added.
        String tmplAdded = addTemplateDetails();
        waitForAngular(500);
        return "Date=" + today + " | Time=11:30 | MortuaryCabin=MC-01 | Relationship=" + lastRelation
                + " | HandoverTo=Family Member | Template=" + lastTemplate + " | " + tmplAdded;
    }

    /** Click the inner <b>Add</b> ({@code AddTemplateList(Mortuary)}) to add the selected template's details. */
    public String addTemplateDetails() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddTemplateList/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__tmplAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) return "TemplateAdd=(no button)";
        try { page.locator("#__tmplAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("addTemplateDetails: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__tmplAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        Object rows = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(!s) return; ['templatelist','TemplateList','Mortuarytemplate','mortuarytemplatelist'].forEach(k=>{ if(Array.isArray(s[k])) n=Math.max(n,s[k].length); }); }catch(e){} }); return n; }");
        return "TemplateAdd=clicked(rows=" + rows + ")";
    }

    /** Click <b>Save</b> ({@code fnSaveDeathMarking}); return the success toast. */
    public String saveAndGetToast() {
        page.evaluate("() => { window.__dmToasts=[]; if(window.__dmObs) window.__dmObs.disconnect(); try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dmToasts.includes(t)) window.__dmToasts.push(t); }); };"
                + " window.__dmObs=new MutationObserver(grab); window.__dmObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].find(x=>/fnSaveDeathMarking/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.id='__dmSave'; }");
        try { page.locator("#__dmSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception e) { System.out.println("save: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__dmSave'); if(e) e.removeAttribute('id'); }");
        // answer any confirm dialog + await toast
        for (int i = 0; i < 25; i++) {
            page.evaluate("() => { const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')].find(m=>m.getBoundingClientRect().width>0 && /death|mortuary|are you sure|do you want|confirm|proceed|save/i.test(m.textContent||'')); if(box){ const y=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|save|continue|proceed)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(y) y.click(); } }");
            boolean done = Boolean.TRUE.equals(page.evaluate("() => (window.__dmToasts||[]).some(a=>/saved|succes|added|please|select|enter|required|mandatory|error/i.test(a))"));
            if (done) break;
            page.waitForTimeout(600);
        }
        Object r = page.evaluate("() => { const a=window.__dmToasts||[]; return a.find(x=>/saved|succes|added/i.test(x)) || a.find(x=>/please|select|enter|required|mandatory|error/i.test(x)) || a[0] || ''; }");
        lastToast = r == null ? "" : r.toString().trim();
        waitForAngular(400);
        return lastToast;
    }
}
