package com.kpj.pages.Ip;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * IP &gt; <b>Discharge Summary List</b> (route {@code #/DischargeSummaryList}) — Page Object.
 *
 * <p>Reached from the <b>IP</b> left-menu. Filtered by a discharge-date range
 * ({@code Discharge.FromDate}/{@code Discharge.ToDate}) + Search, then a patient row is selected in the ui-grid.
 * Clicking the footer <b>ReferralLetter/Medical Report</b> link navigates to the discharge-template editor
 * ({@code #/edit-DischargeTemplateMaster/{id}}) which has a set of template-field checkboxes (each with a
 * {@code toggleSelectionN()} handler), an <b>Add</b> button ({@code AddTemplateDetails}) and a <b>Submit</b>
 * button ({@code fnIUDDischargetemplate}). Verified live: only the <b>Discharge Template</b> checkbox has
 * fields to Add; the other template checkboxes have none and can be submitted directly. Submit posts to
 * {@code /api/DischargeTemplateMaster/IUD} → toast <b>"Template updated successfully."</b>.</p>
 */
public class DischargeSummaryListPage extends BasePage {

    public static final String ROUTE = "#/DischargeSummaryList";

    public DischargeSummaryListPage(Page page) { super(page); }

    // ---- navigation via the IP menu --------------------------------------

    /** Expand the <b>IP</b> left-menu and click <b>Discharge Summary List</b> ({@code #/DischargeSummaryList}). */
    public boolean navigateViaMenu() {
        String menuJs = "() => window.angular && [...document.querySelectorAll('li > a')].some(a=>/^\\s*IP\\s*$/i.test((a.textContent||'').trim()))";
        boolean menuUp = false;
        for (int attempt = 0; attempt < 3 && !menuUp; attempt++) {
            try {
                page.waitForFunction(menuJs, null, new Page.WaitForFunctionOptions().setTimeout(attempt == 0 ? 30000 : 20000));
                menuUp = true;
            } catch (Exception ignore) {
                System.out.println("DischargeSummaryList.navigateViaMenu: IP menu did not render (attempt " + (attempt + 1) + ") — reloading");
                try { page.reload(new Page.ReloadOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)); }
                catch (Exception e) { System.out.println("navigateViaMenu: reload failed - " + e.getMessage()); }
                waitForAngular(1500);
            }
        }
        waitForAngular(800);
        page.evaluate("() => { const lis=[...document.querySelectorAll('li')];"
                + " const ipLi=lis.find(li=>{ const a=li.querySelector(':scope > a'); return a && /^\\s*IP\\s*$/i.test((a.textContent||'').trim()); });"
                + " const a=ipLi&&ipLi.querySelector(':scope > a'); if(!a) return; a.id='__ipMenuTab'; }");
        try { page.locator("#__ipMenuTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("navigateViaMenu: IP menu click failed - " + e.getMessage()); }
        waitForAngular(800);
        Object tagged = page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('href')||'')==='#/DischargeSummaryList' && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('a')].find(x=>/discharge summary list/i.test((x.textContent||'').replace(/\\s+/g,' ').trim())); if(!a) return false; a.id='__dsListTab'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__dsListTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("navigateViaMenu: Discharge Summary List click failed - " + e.getMessage()); }
        } else {
            page.navigate("https://devhis.sancyberhad.com/" + ROUTE);
        }
        try {
            page.waitForFunction("() => window.angular && /dischargesummarylist/i.test(location.hash) && [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("navigateViaMenu: menu route not ready — trying the direct route"); }
        // Fallback: if the menu click didn't route here (e.g. coming from a sub-page like the template editor),
        // navigate directly to #/DischargeSummaryList.
        if (!page.url().toLowerCase().contains("dischargesummarylist")) {
            try {
                page.navigate("https://devhis.sancyberhad.com/" + ROUTE,
                        new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT).setTimeout(60000));
                page.waitForFunction("() => window.angular && /dischargesummarylist/i.test(location.hash) && [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()))",
                        null, new Page.WaitForFunctionOptions().setTimeout(30000));
            } catch (Exception ignore) { System.out.println("navigateViaMenu: direct route not ready in time"); }
        }
        waitForAngular(1200);
        return page.url().toLowerCase().contains("dischargesummarylist");
    }

    // ---- search (1-month range ending today) -----------------------------

    /** Set the date filter to a 1-month range whose To date is today, then Search. Widens to 3 months if the
     *  1-month window is empty. Returns "from → to". */
    public String searchOneMonthToToday() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        doSearch(today.minusMonths(1).format(f), today.format(f));
        if (gridRows() == 0) doSearch(today.minusMonths(3).format(f), today.format(f));
        return today.minusMonths(1).format(f) + " -> " + today.format(f);
    }

    private void doSearch(String from, String to) {
        page.evaluate("(d) => { const setD=(ng,val)=>{ const el=[...document.querySelectorAll('input')].find(i=>i.getAttribute('ng-model')===ng); if(!el) return; const c=angular.element(el).controller('ngModel'); el.value=val; if(c){c.$setViewValue(val);c.$render();} el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " setD('Discharge.FromDate', d.from); setD('Discharge.ToDate', d.to); }",
                java.util.Map.of("from", from, "to", to));
        waitForAngular(500);
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/^search$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data) n=Math.max(n,s.grid.options.data.length);}catch(e){} }); return n>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(800);
    }

    private int gridRows() {
        Object r = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data) n=Math.max(n,s.grid.options.data.length);}catch(e){} }); return n; }");
        try { return ((Number) r).intValue(); } catch (Exception e) { return 0; }
    }

    /** Select a random patient row (skips obvious dummy/test rows). Returns the name, or null. */
    public String selectRandomPatient() {
        // Pick a candidate rendered row and REAL-click its selection checkbox so the app's row-select handler fires
        // and the certificate/summary flow actually gets the patient. A synthetic grid-API selectRow is FLAKY here —
        // the certificate Save then intermittently answers "Please Select Patient!" (patient must be selected FIRST,
        // then the Medical Certificate button). Falls back to the grid API + verifies a row is really selected.
        Object r = page.evaluate("() => { const skip=/dummy|test\\s*emr/i; const nm=e=>((e&&(e.PatientName||e.patientname)||'')+'').trim();"
                + " const bodyRows=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(r=>r.offsetParent!==null);"
                + " const cands=bodyRows.map(r=>{ let ent=null; try{ent=angular.element(r).scope().row.entity;}catch(e){} return {r,ent}; }).filter(x=>x.ent && nm(x.ent) && !skip.test(nm(x.ent)));"
                + " if(!cands.length){ let gridApi=null,data=null; document.querySelectorAll('*').forEach(el=>{ if(gridApi)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){gridApi=s.grid.api;data=s.grid.options.data;} }catch(e){} }); if(!gridApi) return 'ERR:no-grid'; const p=data.find(x=>nm(x)&&!skip.test(nm(x)))||data[0]; try{gridApi.selection.clearSelectedRows();}catch(e){} gridApi.selection.selectRow(p); return 'API:'+nm(p); }"
                + " const pick=cands[Math.floor(Math.random()*Math.min(cands.length,8))]; const idx=bodyRows.indexOf(pick.r);"
                + " const leftRows=[...document.querySelectorAll('.ui-grid-render-container-left .ui-grid-row')].filter(r=>r.offsetParent!==null);"
                + " let cb=(leftRows[idx]&&leftRows[idx].querySelector('.ui-grid-selection-row-header-buttons, input[type=checkbox]')) || pick.r.querySelector('.ui-grid-selection-row-header-buttons, input[type=checkbox]');"
                + " if(cb){ cb.id='__dsSelCb'; return 'CB:'+nm(pick.ent); }"
                + " let gridApi=null; document.querySelectorAll('*').forEach(el=>{ if(gridApi)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api){gridApi=s.grid.api;} }catch(e){} }); if(gridApi){ try{gridApi.selection.clearSelectedRows();}catch(e){} gridApi.selection.selectRow(pick.ent); } return 'API:'+nm(pick.ent); }");
        String s = r == null ? null : r.toString();
        if (s == null || s.startsWith("ERR")) { System.out.println("selectRandomPatient: " + s); return null; }
        String name = s.substring(s.indexOf(':') + 1);
        if (s.startsWith("CB:")) {
            try { page.locator("#__dsSelCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("selectRandomPatient: checkbox real click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__dsSelCb'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(700);
        // Verify a row is actually selected; if not, fall back to the grid API select.
        boolean sel = Boolean.TRUE.equals(page.evaluate("() => { let ok=false; document.querySelectorAll('*').forEach(el=>{ if(ok)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.api.selection){ const rs=s.grid.api.selection.getSelectedRows(); if(rs&&rs.length) ok=true; } }catch(e){} }); return ok; }"));
        if (!sel) {
            System.out.println("selectRandomPatient: getSelectedRows empty after real click — grid-API fallback");
            page.evaluate("() => { const skip=/dummy|test\\s*emr/i; const nm=x=>((x.PatientName||x.patientname||'')+'').trim(); let gridApi=null,data=null; document.querySelectorAll('*').forEach(el=>{ if(gridApi)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){gridApi=s.grid.api;data=s.grid.options.data;} }catch(e){} }); if(gridApi){ const p=data.find(x=>nm(x)&&!skip.test(nm(x)))||data[0]; try{gridApi.selection.clearSelectedRows();}catch(e){} gridApi.selection.selectRow(p); } }");
            waitForAngular(500);
        }
        return name;
    }

    // ---- ReferralLetter/Medical Report -> template editor ----------------

    /** Click the footer <b>ReferralLetter/Medical Report</b> link and wait for the discharge-template editor
     *  ({@code #/edit-DischargeTemplateMaster/...} with its field checkboxes + Submit). Returns true if reached. */
    public boolean clickReferralLetterMedicalReport() {
        page.evaluate("() => { const a=[...document.querySelectorAll('button,a')].find(x=>/referral.?letter|medical report/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(a){ a.id='__refLetterBtn'; } }");
        try { page.locator("#__refLetterBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickReferralLetterMedicalReport: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => /edit-DischargeTemplateMaster/i.test(location.hash) && [...document.querySelectorAll('button')].some(b=>/fnIUDDischargetemplate/.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("clickReferralLetterMedicalReport: template editor not reached"); return false; }
        waitForAngular(1200);
        return true;
    }

    /**
     * Tick a template-field checkbox that has no sub-fields (<b>Post Mortem Report</b>, so no Add step is
     * needed), click <b>Submit</b> ({@code fnIUDDischargetemplate()}), and return the success toast
     * (verified live: <b>"Template updated successfully."</b>).
     */
    public String checkFieldAndSubmit() {
        // Tick a no-field checkbox via a REAL click so its toggleSelectionN() handler fires.
        page.evaluate("() => { const c=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && (x.getAttribute('ng-model')||'')==='discharge2.postmortemtemplate')"
                + " || [...document.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && /toggleSelection/.test(x.getAttribute('ng-click')||'') && !x.checked); if(c){ c.id='__dsField'; } }");
        try { page.locator("#__dsField").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("checkFieldAndSubmit: field checkbox click failed - " + e.getMessage()); }
        waitForAngular(800);
        // Observe the toast, then click Submit.
        page.evaluate("() => { window.__dsT=[]; if(window.toastr && !window.__dsHook){ window.__dsHook=true; ['success','error','info','warning'].forEach(k=>{ const o=window.toastr[k]; window.toastr[k]=function(msg,t){ try{window.__dsT.push(msg);}catch(e){} return o&&o.apply(this,arguments); }; }); }"
                + " if(window.__dsObs) window.__dsObs.disconnect(); window.__dsObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dsT.includes(t)) window.__dsT.push(t); }); }); window.__dsObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button')].find(x=>/fnIUDDischargetemplate/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        // Accept any confirm.
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].some(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].find(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|confirm|save)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(600);
        }
        try {
            page.waitForFunction("() => (window.__dsT||[]).some(a=>/template|updated|success|saved/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("checkFieldAndSubmit: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__dsT||[]; return a.find(x=>/template|updated|success|saved/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Print / report capture ------------------------------------------

    /** The URL + screenshot of the last report opened in a new tab (certificate or grid PDF). */
    public String lastReportUrl = "";
    public byte[] lastReportPng = null;

    /**
     * Click <b>Print</b> ({@code PrintGridReportList()}) and capture the discharge-grid report that opens in a
     * new tab (a {@code DischargeGridReport.aspx} <b>PDF</b>). Sets {@link #lastReportUrl} and returns a full-page
     * screenshot of the report tab (may be blank for the PDF viewer — the URL is the reliable evidence), or
     * {@code null} if the report downloaded instead of opening a page.
     */
    public byte[] clickPrintAndScreenshot() {
        page.evaluate("() => { window.__printUrl=''; if(!window.__poHook){ window.__poHook=true; const ow=window.open; window.open=function(u){ try{window.__printUrl=(''+u);}catch(e){} return ow&&ow.apply(this,arguments); }; } }");
        com.microsoft.playwright.Page report = null;
        try {
            report = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(12000), () -> {
                page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/PrintGridReportList/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
            });
        } catch (Exception e) { System.out.println("clickPrintAndScreenshot: no popup (the PDF may have downloaded) - " + e.getMessage()); }
        Object u = page.evaluate("() => window.__printUrl || ''");
        lastReportUrl = u == null ? "" : u.toString();
        byte[] png = null;
        if (report != null) {
            try { report.waitForLoadState(); } catch (Exception ignore) { }
            report.waitForTimeout(3000);
            try { report.bringToFront(); report.waitForTimeout(1500); png = report.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
            catch (Exception e) { System.out.println("clickPrintAndScreenshot: screenshot failed - " + e.getMessage()); }
            if (lastReportUrl.isEmpty()) lastReportUrl = report.url();
            try { if (report != page && !report.isClosed()) report.close(); } catch (Exception ignore) { }
            page.bringToFront();
        }
        if (lastReportUrl.startsWith("/")) lastReportUrl = "https://devhis.sancyberhad.com" + lastReportUrl;
        lastReportPng = png;
        waitForAngular(400);
        return png;
    }

    // ---- Medical Certificate (MC) ----------------------------------------

    /** Click the footer <b>Medical Certificate (MC)</b> link ({@code fnSetCertificateSession()}) and wait for the
     *  add-Certificate page ({@code #/add-Certificate} with the {@code Certificate.Dischargeid} template select).
     *  Returns true if reached. */
    public boolean clickMedicalCertificate() {
        page.evaluate("() => { const a=[...document.querySelectorAll('button,a')].find(x=>/medical certificate/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(a){ a.id='__mcBtn'; } }");
        try { page.locator("#__mcBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickMedicalCertificate: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => /add-Certificate/i.test(location.hash) && document.querySelector('select[ng-model=\"Certificate.Dischargeid\"]')",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("clickMedicalCertificate: add-Certificate not reached"); return false; }
        waitForAngular(1500);
        return true;
    }

    /** Click the footer <b>Death</b> link ({@code fnSetCertificateSession()}) and wait for the add-Certificate
     *  page — Death opens the SAME add-Certificate screen as Medical Certificate, so {@link #fillCertificateAndSave()}
     *  handles the rest. Returns true if reached. */
    public boolean clickDeath() {
        page.evaluate("() => { const a=[...document.querySelectorAll('button,a')].find(x=>/^death$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(a){ a.id='__deathBtn'; } }");
        try { page.locator("#__deathBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickDeath: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => /add-Certificate/i.test(location.hash) && document.querySelector('select[ng-model=\"Certificate.Dischargeid\"]')",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("clickDeath: add-Certificate not reached"); return false; }
        waitForAngular(1500);
        return true;
    }

    /**
     * On the add-Certificate page: select <b>Certificate Template</b> ({@code Certificate.Dischargeid}) →
     * <b>Department</b> ({@code Certificate.departmentid}, fires {@code fnSetDoctor()}) → <b>Doctor</b>
     * ({@code Certificate.payableid}); tick the <b>Authenticate</b> checkbox ({@code Certificate.isfinalized});
     * type text into the CKEditor; click <b>Save</b> ({@code IUDDischargeSummaryDetail()}). Captures the report
     * that opens in a new tab (a {@code certificateTemplate.aspx} PDF) into {@link #lastReportUrl}/{@link #lastReportPng}.
     * Returns the success toast (verified live: <b>"Certificate Saved Successfully."</b>).
     */
    public String fillCertificateAndSave() {
        String pickJs = "(ng) => { const s=[...document.querySelectorAll('select[ng-model=\"'+ng+'\"]')].find(e=>e.offsetParent!==null); if(!s) return 'no'; const i=[...s.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); if(i<0) return 'noopt'; s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(s).trigger('change');}catch(e){}} return (s.options[i].textContent||'').trim(); }";
        page.evaluate(pickJs, "Certificate.Dischargeid");   // template — loads its text into the editor
        waitForAngular(1600);
        page.evaluate(pickJs, "Certificate.departmentid");  // department — fnSetDoctor loads doctors
        waitForAngular(1800);
        page.evaluate(pickJs, "Certificate.payableid");     // doctor
        waitForAngular(800);
        // Authenticate = the Certificate.isfinalized checkbox — real click so its handler fires.
        page.evaluate("() => { const c=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null && (x.getAttribute('ng-model')||'')==='Certificate.isfinalized'); if(c && !c.checked){ c.id='__authCb'; } }");
        try { page.locator("#__authCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("fillCertificateAndSave: Authenticate checkbox click - " + e.getMessage()); }
        waitForAngular(500);
        // Type text into the (visible) CKEditor — the Save posts the editor content as textdocument.
        page.evaluate("() => { if(window.CKEDITOR){ const insts=Object.values(CKEDITOR.instances); const inst=insts.find(e=>{ try{ return e.container && e.container.$ && e.container.$.offsetParent!==null; }catch(x){ return false; } }) || insts[0]; if(inst){ try{ inst.setData((inst.getData()||'') + '<p>Automated test certificate.</p>'); }catch(x){ try{ inst.setData('<p>Automated test certificate.</p>'); }catch(y){} } } } }");
        waitForAngular(700);
        // Hook toastr + window.open, then click Save and capture the report popup.
        page.evaluate("() => { window.__certT=[]; window.__certUrl=''; if(window.toastr && !window.__certHook){ window.__certHook=true; ['success','error','info','warning'].forEach(k=>{ const o=window.toastr[k]; window.toastr[k]=function(msg){ try{window.__certT.push(msg);}catch(e){} return o&&o.apply(this,arguments); }; }); }"
                + " if(!window.__certWin){ window.__certWin=true; const ow=window.open; window.open=function(u){ try{window.__certUrl=(''+u);}catch(e){} return ow&&ow.apply(this,arguments); }; } }");
        com.microsoft.playwright.Page report = null;
        try {
            report = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(15000), () -> {
                page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/IUDDischargeSummaryDetail/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
            });
        } catch (Exception e) { System.out.println("fillCertificateAndSave: no report popup (may have downloaded) - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__certT||[]).some(a=>/certificate|saved|success/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("fillCertificateAndSave: no toast observed"); }
        Object t = page.evaluate("() => { const a=window.__certT||[]; return a.find(x=>/certificate|saved|success/i.test(x)) || a[0] || ''; }");
        Object u = page.evaluate("() => window.__certUrl || ''");
        lastReportUrl = u == null ? "" : u.toString();
        if (report != null) {
            try { report.waitForLoadState(); } catch (Exception ignore) { }
            report.waitForTimeout(3000);
            try { report.bringToFront(); report.waitForTimeout(1500); lastReportPng = report.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
            catch (Exception e) { System.out.println("fillCertificateAndSave: report screenshot failed - " + e.getMessage()); }
            if (lastReportUrl.isEmpty()) lastReportUrl = report.url();
            try { if (report != page && !report.isClosed()) report.close(); } catch (Exception ignore) { }
            page.bringToFront();
        }
        if (lastReportUrl.startsWith("/")) lastReportUrl = "https://devhis.sancyberhad.com" + lastReportUrl;
        waitForAngular(400);
        return t == null ? "" : t.toString().trim();
    }

    // ---- New (create a Discharge Summary) --------------------------------

    /** Read the first grid row's MRN (a discharged patient) — used to fill the New form's MRN search. */
    public String getGridMrn() {
        Object r = page.evaluate("() => { let data=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length) data=s.grid.options.data; }catch(e){} }); if(!data) return ''; const row=data.find(x=>(x.mrno||x.MRNo||x.mrnno)); return row?((row.mrno||row.MRNo||row.mrnno)+'').trim():''; }");
        return r == null ? "" : r.toString();
    }

    /** Click <b>New</b> ({@code AddDischarge()}) and wait for the New Discharge Summary form
     *  ({@code #/DischargeSummary} — MRN input {@code DischargeSummary.MRNo} + Save {@code IUDDischargeSummaryDetail}).
     *  Returns true if reached. */
    public boolean clickNew() {
        page.evaluate("() => { const a=[...document.querySelectorAll('button,a')].find(x=>/AddDischarge/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(a){ a.id='__newBtn'; } }");
        try { page.locator("#__newBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickNew: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => document.querySelector('input[ng-model=\"DischargeSummary.MRNo\"]') && [...document.querySelectorAll('button')].some(b=>/IUDDischargeSummaryDetail/.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("clickNew: DischargeSummary form not reached"); return false; }
        waitForAngular(1500);
        return true;
    }

    /**
     * Fill the New Discharge Summary form: enter <b>MRN</b> ({@code DischargeSummary.MRNo}) → search the patient
     * ({@code SearchPatientByMRNo()}); select the <b>Discharge Template</b> ({@code DischargeSummary.Dischargeid},
     * fires {@code Dischargedrpdwn()}); type a reason into the CKEditor; set the <b>Follow-up date</b>
     * ({@code DischargeSummary.followupdate}); click <b>Save</b> ({@code IUDDischargeSummaryDetail()}). Returns the
     * success toast (verified live: <b>"Discharge Summary saved successfully."</b>).
     */
    public String fillNewDischargeAndSave(String mrn) {
        // Enter MRN + search the patient.
        page.evaluate("(m) => { const i=document.querySelector('input[ng-model=\"DischargeSummary.MRNo\"]'); if(i){ const c=angular.element(i).controller('ngModel'); i.value=m; if(c){c.$setViewValue(m);c.$render();} i.dispatchEvent(new Event('input',{bubbles:true})); i.dispatchEvent(new Event('change',{bubbles:true})); } }", mrn);
        waitForAngular(500);
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(2500);
        // Select the discharge template (first real option) → loads its text into the editor.
        page.evaluate("() => { const s=[...document.querySelectorAll('select[ng-model=\"DischargeSummary.Dischargeid\"]')].find(e=>e.offsetParent!==null); if(!s) return; const i=[...s.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); if(i<0) return; s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(s).trigger('change');}catch(e){}} }");
        waitForAngular(1800);
        // Type a reason into the visible CKEditor (the Save posts the editor content as textdocument).
        page.evaluate("() => { if(window.CKEDITOR){ const insts=Object.values(CKEDITOR.instances); const inst=insts.find(e=>{ try{ return e.container && e.container.$ && e.container.$.offsetParent!==null; }catch(x){ return false; } }) || insts[0]; if(inst){ try{ inst.setData((inst.getData()||'') + '<p>Diagnosis: Automated test discharge summary.</p>'); }catch(x){} } } }");
        waitForAngular(600);
        // Set the follow-up date (today + 7) directly on the model.
        java.time.LocalDate f = java.time.LocalDate.now().plusDays(7);
        String fd = f.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        page.evaluate("(d) => { const i=document.querySelector('input[ng-model=\"DischargeSummary.followupdate\"]'); if(i){ const c=angular.element(i).controller('ngModel'); i.value=d; if(c){c.$setViewValue(d);c.$render();} i.dispatchEvent(new Event('input',{bubbles:true})); i.dispatchEvent(new Event('change',{bubbles:true})); } }", fd);
        waitForAngular(600);
        // Observe the toast, click Save.
        page.evaluate("() => { window.__nsT=[]; if(window.toastr && !window.__nsHook){ window.__nsHook=true; ['success','error','info','warning'].forEach(k=>{ const o=window.toastr[k]; window.toastr[k]=function(msg){ try{window.__nsT.push(msg);}catch(e){} return o&&o.apply(this,arguments); }; }); }"
                + " if(window.__nsObs) window.__nsObs.disconnect(); window.__nsObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__nsT.includes(t)) window.__nsT.push(t); }); }); window.__nsObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button')].find(x=>/IUDDischargeSummaryDetail/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].some(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert')].find(m=>m.offsetParent!==null && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|confirm|save)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(600);
        }
        try {
            page.waitForFunction("() => (window.__nsT||[]).some(a=>/discharge summary|saved|success/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("fillNewDischargeAndSave: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__nsT||[]; return a.find(x=>/discharge summary|saved|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click the New form's <b>Print</b> ({@code printReport()}) and capture the discharge-summary report that
     *  opens in a new tab (a {@code DischargeSummary.aspx} PDF). Sets {@link #lastReportUrl}; returns a screenshot
     *  (or null if it downloaded). */
    public byte[] clickNewPrintAndScreenshot() {
        page.evaluate("() => { window.__printUrl=''; if(!window.__poHook){ window.__poHook=true; const ow=window.open; window.open=function(u){ try{window.__printUrl=(''+u);}catch(e){} return ow&&ow.apply(this,arguments); }; } }");
        com.microsoft.playwright.Page report = null;
        try {
            report = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(12000), () -> {
                page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/printReport/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
            });
        } catch (Exception e) { System.out.println("clickNewPrintAndScreenshot: no popup (the PDF may have downloaded) - " + e.getMessage()); }
        Object u = page.evaluate("() => window.__printUrl || ''");
        lastReportUrl = u == null ? "" : u.toString();
        byte[] png = null;
        if (report != null) {
            try { report.waitForLoadState(); } catch (Exception ignore) { }
            report.waitForTimeout(3000);
            try { report.bringToFront(); report.waitForTimeout(1500); png = report.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
            catch (Exception e) { System.out.println("clickNewPrintAndScreenshot: screenshot failed - " + e.getMessage()); }
            if (lastReportUrl.isEmpty()) lastReportUrl = report.url();
            try { if (report != page && !report.isClosed()) report.close(); } catch (Exception ignore) { }
            page.bringToFront();
        }
        if (lastReportUrl.startsWith("/")) lastReportUrl = "https://devhis.sancyberhad.com" + lastReportUrl;
        lastReportPng = png;
        waitForAngular(400);
        return png;
    }
}
