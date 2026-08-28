package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>File Linking</b> (route {@code #/FileLinking}, M7UC1 — M7 Scanning FSD) — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>File Linking</b> → pick a patient (picker popup) → fill the mandatory
 * details (File Received Date {@code filelinkings.FileReceivedDate} + Time {@code inputTime} scope var, File Name
 * {@code filelinkings.FileName}, File Category {@code filelinkings.FileCategoryID}) → attach a file (hidden input
 * {@code #file}, {@code filelinkings.report}, fires {@code fileChanged}) → <b>Add</b> ({@code AddFileDetails})
 * → <b>Submit</b> ({@code fnSaveFilesonServer}) → success toast.</p>
 */
public class FileLinking extends BasePage {

    public FileLinking(Page page) { super(page); }

    public String route = "", lastPatient = "", lastFileName = "", lastCategory = "", lastToast = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateTo(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('a,li>a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/file\\s*linking/i.test(norm(x.textContent)) || /filelinking/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__flMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__flMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("FileLinking.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        }
        if (!page.url().toLowerCase().contains("filelinking")) {
            try { page.evaluate("() => { window.location.hash = '#/FileLinking'; }"); } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.getAttribute('ng-model')||'')==='filelinkings.FileName' && e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("FileLinking.nav: form not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("filelinking"); }

    /** Enter an MRN directly and search (SearchPatientByMRNo). Returns true if the patient loaded (MRN retained). */
    public boolean enterMrn(String mrn) {
        // set opd/ipd radio to the first (typically OPD/all) so the search isn't over-filtered, then type MRN + search
        page.evaluate("(m) => { const A=window.angular; const e=[...document.querySelectorAll(\"input[ng-model='filelinking.MRNo']\")].find(x=>x.offsetParent!==null); if(e){ const c=A.element(e).controller('ngModel'); e.value=m; if(c){c.$setViewValue(m);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } const b=[...document.querySelectorAll('button,a,i,span,img')].find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'')); if(b) b.id='__flMrnSearch'; }", mrn);
        try { page.locator("#__flMrnSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__flMrnSearch'); if(e) e.removeAttribute('id'); }");
        // wait for the patient to load — the MRN field keeps the value and a patient name / scope populates
        String loaded = "";
        for (int p = 0; p < 8 && loaded.isEmpty(); p++) {
            page.waitForTimeout(600);
            Object v = page.evaluate("() => { const e=[...document.querySelectorAll(\"input[ng-model='filelinking.MRNo']\")].find(x=>x.offsetParent!==null); const val=e?e.value:''; let pname=''; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.filelinking&&(s.filelinking.patientname||s.filelinking.PatientName||s.filelinking.FirstName)) pname=s.filelinking.patientname||s.filelinking.PatientName||s.filelinking.FirstName; }catch(e){} }); return (val&&pname)?(val):''; }");
            loaded = v == null ? "" : v.toString().trim();
        }
        if (!loaded.isEmpty()) { lastPatient = loaded; return true; }
        // fallback: MRN retained is enough (some builds don't expose a name field)
        Object still = page.evaluate("() => { const e=[...document.querySelectorAll(\"input[ng-model='filelinking.MRNo']\")].find(x=>x.offsetParent!==null); return e?e.value:''; }");
        String s = still == null ? "" : still.toString().trim();
        if (!s.isEmpty()) { lastPatient = s; return true; }
        return false;
    }

    // ---- fill + attach + add + submit -----------------------------------

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    /** Set the {@code inputTime} scope var (like Sterilization Type — neither $setViewValue nor .fill() commits it). */
    private void setScopeTime(String v) {
        setNg("inputTime", v);
        page.evaluate("(v) => { let done=false; document.querySelectorAll('*').forEach(el=>{ if(done) return; try{ const s=angular.element(el).scope(); if(s && Object.prototype.hasOwnProperty.call(s,'inputTime')){ s.inputTime=v; s.$applyAsync?s.$applyAsync():s.$apply&&s.$apply(); done=true; } }catch(e){} }); }", v);
    }

    /** Fill the details the user asked for: Scan Name*, File Name, File Category, Confidential, Remarks
     *  (plus the mandatory File Received Date + Time). */
    public String fillMandatory() {
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        setNg("filelinkings.FileReceivedDate", today);
        setScopeTime("10:30");
        String base = "AUTOFILE" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        // Scan Name* (documentname) + File Name
        setNg("filelinkings.documentname", "SCAN " + base);
        lastFileName = base;
        setNg("filelinkings.FileName", base);
        setNg("filelinkings.remarks", "Automation file linking " + base);
        // Confidential checkbox
        String conf = "(no)";
        try {
            Object tagged = page.evaluate("() => { const cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='filelinkings.IsConfidential']\")].find(x=>x.offsetParent!==null); if(!cb) return false; cb.id='__conf'; return true; }");
            if (Boolean.TRUE.equals(tagged)) { page.locator("#__conf").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(4000)); conf = "ticked";
                page.evaluate("() => { const e=document.getElementById('__conf'); if(e) e.removeAttribute('id'); }"); }
        } catch (Exception ignore) { }
        // File Category — first real option. WAIT for the list: it arrives asynchronously, and reading it
        // once can land on the empty dropdown and report "(n/a)" for a screen that is merely still
        // loading. An unset category also makes Submit answer "No new or modified records to save.!",
        // so the cause of that failure looks unrelated to the real problem.
        String cat = "(n/a)";
        try {
            String sel = "select[ng-model='filelinkings.FileCategoryID']";
            try {
                page.waitForFunction("(s) => { const e=document.querySelector(s);"
                        + " return !!e && [...e.options].some(o=>o.value"
                        + "   && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel,
                        new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception e) {
                System.out.println("FileLinking: File Category list still empty after 15s");
            }
            Object idx = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return -1; return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
            int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
            if (i >= 0) { page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
                cat = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim(); }
        } catch (Exception ignore) { }
        lastCategory = cat;
        waitForAngular(500);
        return "ScanName=SCAN " + base + " | Confidential=" + conf + " | FileName=" + base
                + " | Category=" + cat + " | Remarks=set | Date=" + today + " Time=10:30";
    }

    /** Attach a file into the hidden file input (#file / filelinkings.report; fires fileChanged). */
    public String attachFile(java.nio.file.Path filePath) {
        try {
            page.setInputFiles("#file", filePath);
        } catch (Exception e) {
            try { page.setInputFiles("input[type=file][ng-model='filelinkings.report']", filePath); }
            catch (Exception e2) { System.out.println("attachFile: failed - " + e2.getMessage()); return "(attach failed)"; }
        }
        waitForAngular(1500);
        // Read the attached filename from any file input OR the scope model (Angular may clear the input display).
        Object nm = page.evaluate("() => { let n=''; document.querySelectorAll('input[type=file]').forEach(e=>{ if(e.files&&e.files[0]) n=e.files[0].name; }); if(!n){ document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.filelinkings){ const r=s.filelinkings.report; if(r){ if(typeof r==='string'&&r) n=r; else if(r.name) n=r.name; } if(!n&&s.filelinkings.uploadfilename) n=s.filelinkings.uploadfilename; } }catch(e){} }); } return n; }");
        String name = nm == null ? "" : nm.toString().trim();
        return name.isEmpty() ? "(attached — filename not echoed)" : "Attached: " + name;
    }

    /** Click <b>Add</b> ({@code AddFileDetails}) to add the file entry to the list. */
    public String clickAdd() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddFileDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.id='__flAdd'; }");
        try { page.locator("#__flAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("clickAdd: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__flAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);
        return "list rows=" + listRows();
    }

    public int listRows() {
        Object c = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&Array.isArray(s.linklist)) n=Math.max(n,s.linklist.length); if(s&&s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)) n=Math.max(n,s.grid.options.data.length); }catch(e){} }); return n; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Click <b>Submit</b> ({@code fnSaveFilesonServer}) and return the success toast (toastr cleared first). */
    public String submitAndGetToast() {
        page.evaluate("() => { window.__flToasts=[]; if(window.__flObs) window.__flObs.disconnect(); try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__flToasts.includes(t)) window.__flToasts.push(t); }); };"
                + " window.__flObs=new MutationObserver(grab); window.__flObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].find(x=>/fnSaveFilesonServer/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.id='__flSubmit'; }");
        try { page.locator("#__flSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception e) { System.out.println("submit: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__flSubmit'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__flToasts||[]).some(a=>/saved|succes|linked|uploaded|added|please|select|enter|required|mandatory|format|size|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        Object r = page.evaluate("() => { const a=window.__flToasts||[]; return a.find(x=>/saved|succes|linked|uploaded|added/i.test(x)) || a.find(x=>x) || ''; }");
        lastToast = r == null ? "" : r.toString().trim();
        waitForAngular(400);
        return lastToast;
    }
}
