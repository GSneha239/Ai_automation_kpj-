package com.kpj.pages.Ip;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * IP &gt; <b>Discharge</b> (route {@code #/DischargeList}) — Page Object.
 *
 * <p>Reached from the <b>IP</b> left-menu. The list is filtered by a discharge-date range
 * ({@code Discharge.FromDate} / {@code Discharge.ToDate}) plus a view radio
 * ({@code Discharge.dischargeoption}: option1 = <b>Discharge</b> = already-discharged patients, option2 =
 * Expected Discharge List [default], option3 = Plan Discharge List, option4 = All). To Cancel Discharge you
 * need already-discharged patients, so this page selects the <b>Discharge</b> radio (option1) — otherwise the
 * grid is empty. Selecting a row + <b>Cancel Discharge</b> ({@code GetBillGeneratedOrNot()}) cancels the
 * discharge and toasts <b>"Discharge Cancel successfully."</b> (no modal/confirm for a patient without a
 * generated bill).</p>
 */
public class DischargePage extends BasePage {

    public static final String ROUTE = "#/DischargeList";

    public DischargePage(Page page) { super(page); }

    // ---- navigation via the IP menu --------------------------------------

    /** Expand the <b>IP</b> left-menu and click <b>Discharge</b> ({@code #/DischargeList}). */
    public boolean navigateViaMenu() {
        String menuJs = "() => window.angular && [...document.querySelectorAll('li > a')].some(a=>/^\\s*IP\\s*$/i.test((a.textContent||'').trim()))";
        boolean menuUp = false;
        for (int attempt = 0; attempt < 3 && !menuUp; attempt++) {
            try {
                page.waitForFunction(menuJs, null, new Page.WaitForFunctionOptions().setTimeout(attempt == 0 ? 30000 : 20000));
                menuUp = true;
            } catch (Exception ignore) {
                System.out.println("Discharge.navigateViaMenu: IP menu did not render (attempt " + (attempt + 1) + ") — reloading");
                try { page.reload(new Page.ReloadOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)); }
                catch (Exception e) { System.out.println("Discharge.navigateViaMenu: reload failed - " + e.getMessage()); }
                waitForAngular(1500);
            }
        }
        waitForAngular(800);
        page.evaluate("() => { const lis=[...document.querySelectorAll('li')];"
                + " const ipLi=lis.find(li=>{ const a=li.querySelector(':scope > a'); return a && /^\\s*IP\\s*$/i.test((a.textContent||'').trim()); });"
                + " const a=ipLi&&ipLi.querySelector(':scope > a'); if(!a) return; a.id='__ipMenuTab'; }");
        try { page.locator("#__ipMenuTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("Discharge.navigateViaMenu: IP menu click failed - " + e.getMessage()); }
        waitForAngular(800);
        Object tagged = page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('href')||'')==='#/DischargeList' && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('a')].find(x=>/^\\s*discharge\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && (x.getAttribute('href')||'')==='#/DischargeList'); if(!a) return false; a.id='__dischargeTab'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__dischargeTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Discharge.navigateViaMenu: Discharge link click failed - " + e.getMessage()); }
        } else {
            page.navigate("https://devhis.sancyberhad.com/" + ROUTE);
        }
        try {
            page.waitForFunction("() => window.angular && /dischargelist/i.test(location.hash) && [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("Discharge.navigateViaMenu: list not ready in time"); }
        waitForAngular(1200);
        return page.url().toLowerCase().contains("dischargelist");
    }

    // ---- search discharged patients (1-month range, To = today) ----------

    /**
     * Select the <b>Discharge</b> view radio (option1 = already-discharged patients), set the date filter to a
     * 1-month range whose <b>To date is today</b>, then Search. If the 1-month range returns no rows, widens to
     * 3 months (so a sparse recent window doesn't leave the test with nothing to cancel). Returns "from → to".
     */
    public String searchDischargedOneMonthToToday() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        // Select the "Discharge" radio (option1) — without it the grid stays on Expected Discharge List (empty).
        page.evaluate("() => { const r=[...document.querySelectorAll('input[type=radio]')].find(x=>x.getAttribute('ng-model')==='Discharge.dischargeoption' && (x.getAttribute('value')||x.value)==='option1'); if(r){ r.click(); r.dispatchEvent(new Event('change',{bubbles:true})); } }");
        waitForAngular(700);
        String fromStr = doSearch(today.minusMonths(1).format(f), today.format(f));
        if (gridRows() == 0) {
            System.out.println("searchDischargedOneMonthToToday: 1-month range empty — widening to 3 months");
            fromStr = doSearch(today.minusMonths(3).format(f), today.format(f));
        }
        return fromStr + " -> " + today.format(f);
    }

    private String doSearch(String from, String to) {
        page.evaluate("(d) => { const setD=(ng,val)=>{ const el=[...document.querySelectorAll('input')].find(i=>i.getAttribute('ng-model')===ng); if(!el) return; const c=angular.element(el).controller('ngModel'); el.value=val; if(c){c.$setViewValue(val);c.$render();} el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " setD('Discharge.FromDate', d.from); setD('Discharge.ToDate', d.to); }",
                java.util.Map.of("from", from, "to", to));
        waitForAngular(500);
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/^search$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data) n=Math.max(n,s.grid.options.data.length);}catch(e){} }); return n>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(800);
        return from;
    }

    private int gridRows() {
        Object r = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data) n=Math.max(n,s.grid.options.data.length);}catch(e){} }); return n; }");
        try { return ((Number) r).intValue(); } catch (Exception e) { return 0; }
    }

    // ---- select a discharged patient -------------------------------------

    /** Select a random discharged patient row (skips obvious dummy/test rows). Returns the name, or null. */
    public String selectRandomPatient() {
        Object r = page.evaluate("() => { const skip=/dummy|test\\s*emr/i; let gridApi=null, data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){gridApi=s.grid.api;data=s.grid.options.data;} }catch(e){} });"
                + " if(!gridApi||!data||!data.length) return null;"
                + " const nm=x=>((x.PatientName||x.patientname||x.PatientFullName||'')+'').trim();"
                + " const cands=data.filter(x=>nm(x)&&!skip.test(nm(x)));"
                + " const p=cands[Math.floor(Math.random()*Math.min(cands.length,10))] || data[0];"
                + " try{gridApi.selection.clearSelectedRows();}catch(e){} gridApi.selection.selectRow(p); return nm(p); }");
        waitForAngular(600);
        return r == null ? null : r.toString();
    }

    // ---- Cancel Discharge ------------------------------------------------

    /**
     * Click <b>Cancel Discharge</b> ({@code GetBillGeneratedOrNot()}), accept any "bill generated / are you
     * sure" confirm, and return the success toast (verified live: <b>"Discharge Cancel successfully."</b>).
     */
    public String cancelDischargeAndGetToast() {
        // Hook toastr + observe DOM toasts (this action toasts on success; it may also JAlert).
        page.evaluate("() => { window.__dcT=[]; if(window.toastr && !window.__dcHook){ window.__dcHook=true; ['success','error','info','warning'].forEach(k=>{ const o=window.toastr[k]; window.toastr[k]=function(msg,t){ try{window.__dcT.push(msg);}catch(e){} return o&&o.apply(this,arguments); }; }); }"
                + " if(window.__dcObs) window.__dcObs.disconnect(); window.__dcObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dcT.includes(t)) window.__dcT.push(t); }); }); window.__dcObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/GetBillGeneratedOrNot/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        // Accept any confirm (e.g. bill-generated warning or "are you sure").
        for (int i = 0; i < 5; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,.bootbox')].some(m=>m.offsetParent!==null && /do you want|are you sure|confirm|bill/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.sweet-alert,.bootbox')].find(m=>m.offsetParent!==null && /do you want|are you sure|confirm|bill/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|confirm)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(700);
        }
        try {
            page.waitForFunction("() => (window.__dcT||[]).some(a=>/cancel|success/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("cancelDischargeAndGetToast: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__dcT||[]; return a.find(x=>/cancel|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Print ------------------------------------------------------------

    /** The URL of the last Print report (a {@code DischargeGridReport.aspx} PDF), captured even if the report
     *  downloads instead of opening a page. */
    public String lastReportUrl = "";

    /**
     * Click <b>Print</b> ({@code PrintGridReportList()}) and capture the report that opens in a new tab. The
     * report is a {@code DischargeGridReport.aspx} <b>PDF</b> opened via {@code window.open}, so this hooks
     * {@code window.open} to record the URL and waits for the popup page. Returns a full-page screenshot of the
     * report tab (may be blank for the PDF viewer — the URL in {@link #lastReportUrl} is the reliable evidence),
     * or {@code null} if no page opened (the PDF downloaded instead).
     */
    public byte[] clickPrintAndScreenshot() {
        // Hook window.open so the report URL is captured whether the PDF opens a tab or downloads.
        page.evaluate("() => { window.__printUrl=''; if(!window.__poHook){ window.__poHook=true; const ow=window.open; window.open=function(u){ try{window.__printUrl=(''+u);}catch(e){} return ow&&ow.apply(this,arguments); }; } }");
        com.microsoft.playwright.Page report = null;
        try {
            report = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(12000), () -> {
                page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/PrintGridReportList/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
            });
        } catch (Exception e) { System.out.println("clickPrintAndScreenshot: no popup page (the PDF may have downloaded) - " + e.getMessage()); }
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
        // Make the URL absolute for the report if it came back relative.
        if (lastReportUrl.startsWith("/")) lastReportUrl = "https://devhis.sancyberhad.com" + lastReportUrl;
        waitForAngular(400);
        return png;
    }
}
