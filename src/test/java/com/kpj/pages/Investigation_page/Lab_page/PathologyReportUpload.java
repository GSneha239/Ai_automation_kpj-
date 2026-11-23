package com.kpj.pages.Investigation_page.Lab_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Investigation &gt; Lab &gt; <b>Pathology Report Upload</b> — Page Object.
 *
 * <h3>Which screen this drives</h3>
 * There is <b>no menu item named "Pathology Report Upload"</b> on devhis (checked live 2026-11-19, build v2026.11.18.491). The screen
 * whose code is the Pathology Report Upload screen (AngularJS controller {@code PathologyReportUploadListController}, scope object
 * {@code PathologyReportUpload}) is reached as <b>Investigation &gt; Lab &gt; Histo Pathology &gt; Histo Report Upload</b>
 * ({@code #/HistoReportUpload}, page title "Histo Report Upload"). This page object drives THAT screen and says so; it does not assume
 * the two names mean the same requirement — {@link #requestedMenuEntries()} reports whether a "Pathology Report Upload" entry exists.
 *
 * <h3>Confirmed live</h3>
 * <ul>
 *   <li>Filters: <b>Date Range</b> ({@code #IsDateRange}, ticked), <b>From Date</b> / <b>To Date</b> ({@code PathologyReportUpload.FromDate/.ToDate},
 *       dd/MM/yyyy, today, no element id), <b>MRN.</b> ({@code #textinput}, {@code PathologyReportUpload.MRNO}) with the find button
 *       ({@code SearchPatientByMRNo()}) and the patient popup ({@code OpenPopupScreen()}), <b>Reg. Type</b> OPD. / IPD. / All.
 *       ({@code PathologyReportUpload.OPDIPD} = 0 / 1 / null, OPD by default), <b>Outsource..</b> / <b>In house.</b>
 *       ({@code PathologyReportUpload.isoutsourced} = true / false, Outsource by default), <b>Sub Group</b> (select2 {@code #subgroupid},
 *       filled from CommonDropdown LABSUBGROUP), <b>Test Name</b> ({@code #txtServiceName}), <b>Sample No</b> / <b>Order No</b> (max 15),
 *       <b>Location</b> ({@code #locationid}, disabled, pre-filled).</li>
 *   <li>Buttons: <b>Search</b> ({@code fnSearchorderbooking()}), <b>Clear</b> ({@code fnClear()}), <b>Upload</b> ({@code openuploadpopoup()}).</li>
 *   <li>Grids: <b>List of Orders</b> ({@code gridOptions}) and <b>Test Details</b> ({@code gridOptions1}); legend Stat / VIP / Infected.</li>
 *   <li>Search = {@code POST /API/ReportDelivery/fetch}, ExecFlag {@code SearchReportfinalized}. Search refuses to run without a Sub Group
 *       (JAlert "Please select sub group").</li>
 *   <li>Upload dialog {@code #Reportupload}: Sample Upload Date, Time, Report Uploaded BY ({@code #uploadedby}), Attach File
 *       ({@code #SelectFile}, validated client-side in {@code fileChanged()}: max 1 000 000 bytes; pdf/txt/png/jpg/jpeg/xlsx/docx/doc),
 *       Note/Suggestions, Remarks, BillNo/ConsignmentNoteNo, SampleCollectionAgent, <b>Save</b> ({@code saveReport()} &rarr;
 *       {@code /API/ReportDelivery/IUD} + {@code api/ReportDelivery/SavePhoto}) and <b>Close</b> ({@code closepopup()}).</li>
 * </ul>
 *
 * <p><b>Read-only:</b> nothing here ever presses Save. File checks only drive the browser-side {@code fileChanged()} validation.</p>
 */
public class PathologyReportUpload extends BasePage {

    public PathologyReportUpload(Page page) { super(page); }

    /** Route of the screen this page object drives (the "Histo Report Upload" menu entry). */
    public static final String ROUTE = "#/HistoReportUpload";
    /** API the Search (and the List-of-Orders row click) calls. */
    public static final String API_FETCH = "/api/reportdelivery/fetch";
    public static final String EXEC_SEARCH = "SearchReportfinalized";
    public static final String ORDERS = "gridOptions", TESTS = "gridOptions1";
    public static final String NG = "PathologyReportUpload.";

    /** What the last action could not do (empty = it held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }
    public int lastSearchStatus = 0;
    public String lastSearchExec = "";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const grid=n=>document.querySelector(\"[ui-grid='\"+n+\"']\");"
          + "const data=n=>{ try{ return angular.element(grid(n)).scope().$eval(n+'.data')||[]; }catch(e){ return []; } };"
          + "const btn=re=>[...document.querySelectorAll('button')].filter(vis).find(x=>re.test(x.getAttribute('ng-click')||''));"
          + "const ngEl=n=>document.querySelector(\"[ng-model='\"+n+\"']\");";

    // ---- menu / navigation ---------------------------------------------------------------------------------

    /**
     * Every menu link whose text reads "Pathology Report Upload" (visible or not), as "text => href". Empty = the requested menu item does
     * not exist on this environment.
     */
    public List<String> requestedMenuEntries() {
        expandLabMenu();
        return strings(page.evaluate("() => {" + JS + " return [...document.querySelectorAll('a')]"
                + ".filter(a=>/pathology\\s*report\\s*upload/i.test(norm(a.textContent))).map(a=>norm(a.textContent)+' => '+(a.getAttribute('href')||'')); }"));
    }

    /**
     * Open Investigation &gt; Lab &gt; Histo Pathology in the side menu. The sidebar ({@code #divSideMeunGeneration}) is built LAZILY: right
     * after login a module's sub-links are not in the page at all, so the menu has to be opened before it can be read (confirmed live:
     * reading it first returned an empty Lab menu although the screen then opened through that same menu).
     */
    public void expandLabMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        expandMenuEntry(null, "^\\s*investigation\\s*$");
        expandMenuEntry("^\\s*investigation\\s*$", "^\\s*lab\\s*$");
        expandMenuEntry("^\\s*lab\\s*$", "^\\s*histo\\s*pathology\\s*$");
    }

    /** Every link under Investigation &gt; Lab (including Histo Pathology), as "text => href" — for the report. Opens the menu first. */
    public List<String> labMenuEntries() {
        expandLabMenu();
        return strings(page.evaluate("() => {" + JS
                + " const inv=[...document.querySelectorAll('a')].find(x=>/^investigation$/i.test(norm(x.textContent)));"
                + " const li=inv ? inv.closest('li') : null; if(!li) return [];"
                + " const lab=[...li.querySelectorAll('a')].find(x=>/^lab$/i.test(norm(x.textContent)));"
                + " const labLi=lab ? lab.closest('li') : null; if(!labLi) return [];"
                + " return [...labLi.querySelectorAll('a[href]')].filter(a=>(a.getAttribute('href')||'').startsWith('#/'))"
                + "   .map(a=>norm(a.textContent)+' => '+a.getAttribute('href')); }"));
    }

    /** Investigation &gt; Lab &gt; Histo Pathology &gt; Histo Report Upload through the side menu (never a typed URL: the app answers a direct hash load with a log-out prompt). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*investigation\\s*$");
            expandMenuEntry("^\\s*investigation\\s*$", "^\\s*lab\\s*$");
            expandMenuEntry("^\\s*lab\\s*$", "^\\s*histo\\s*pathology\\s*$");
            Object tagged = page.evaluate("(h) => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__pruLink'; return true; }", ROUTE.toLowerCase());
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__pruLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) {
                    // the leaf can sit in a fly-out that is not "visible" to Playwright: the link's own click does the same routing
                    page.evaluate("() => { const a=document.getElementById('__pruLink'); if(a) a.click(); }");
                }
                page.evaluate("() => { const e=document.getElementById('__pruLink'); if(e) e.removeAttribute('id'); }");
            }
            try {
                page.waitForFunction("() => /#\\/historeportupload/i.test(location.hash) && [...document.querySelectorAll('button')]"
                        + ".some(b=>/fnSearchorderbooking\\(\\)/.test(b.getAttribute('ng-click')||''))", null,
                        new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
            waitForAngular(2000);   // the controller's own dropdown loads (Sub Group, Location, Uploaded By)
        }
        return onScreen();
    }

    /** On this screen: the route AND its Search button, so a bounce to the dashboard cannot pass. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains(ROUTE.toLowerCase())) return false;
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('button')].some(b=>/fnSearchorderbooking\\(\\)/.test(b.getAttribute('ng-click')||''))"));
    }

    public String pageTitle() { Object t = page.evaluate("() => document.title"); return t == null ? "" : t.toString(); }

    /** The name of the AngularJS controller bound to the screen (evidence of which screen this really is). */
    public String controllerName() {
        Object v = page.evaluate("() => { const e=[...document.querySelectorAll('[ng-controller]')].map(x=>x.getAttribute('ng-controller'))"
                + ".find(x=>/report/i.test(x||'')); return e||''; }");
        return v == null ? "" : v.toString();
    }

    // ---- what is on the screen -----------------------------------------------------------------------------

    /** Text of every label on the screen's search form and legend, as shown (trailing dots kept). */
    public List<String> labels() {
        return strings(page.evaluate("() => {" + JS + " const m=document.querySelector('main')||document.body;"
                + " return [...m.querySelectorAll('label')].filter(l=>!l.closest('.modal')).map(l=>norm(l.innerText)).filter(Boolean); }"));
    }

    /** Text of every label inside the Upload dialog. */
    public List<String> uploadDialogLabels() {
        return strings(page.evaluate("() => {" + JS + " const m=document.getElementById('Reportupload'); if(!m) return [];"
                + " return [...m.querySelectorAll('label')].map(l=>norm(l.innerText)).filter(Boolean); }"));
    }

    /** Visible buttons of the screen (not the date pickers' / grid pager's), as "text | ng-click". */
    public List<String> buttons() {
        return strings(page.evaluate("() => {" + JS + " const m=document.querySelector('main')||document.body;"
                + " return [...m.querySelectorAll('button')].filter(vis).filter(b=>!b.closest('.modal'))"
                + "  .map(b=>norm(b.innerText)+' | '+(b.getAttribute('ng-click')||'')).filter(s=>!/page\\w*Click|toggle\\(|Month\\(|Year|Datepicker|increment|decrement/i.test(s)); }"));
    }

    /** Value of an Angular model on the screen (by the full ng-model name). */
    public String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** The text a box shows (its DOM value), which can differ from its model. */
    public String shownValue(String ng) {
        Object v = page.evaluate("(n) => { const e=[...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(x=>x.tagName!=='TIMEPICKER-POP'); return e ? String(e.value||'') : ''; }", ng);
        return v == null ? "" : v.toString();
    }

    /** The option text a native select (behind select2) currently holds. */
    public String chosen(String selectId) {
        Object v = page.evaluate("(i) => { const e=document.getElementById(i); if(!e || e.selectedIndex<0) return '';"
                + " return (e.options[e.selectedIndex].textContent||'').replace(/\\s+/g,' ').trim(); }", selectId);
        return v == null ? "" : v.toString();
    }

    public boolean dateRangeTicked() {
        return Boolean.TRUE.equals(page.evaluate("() => { const e=document.getElementById('IsDateRange'); return !!e && e.checked; }"));
    }

    /** The label of the checked radio of a radio group (by its ng-model), e.g. "OPD." or "Outsource..". */
    public String checkedRadio(String ng) {
        Object v = page.evaluate("(n) => {" + JS + " const r=[...document.querySelectorAll(\"input[type=radio][ng-model='\"+n+\"']\")].find(x=>x.checked);"
                + " if(!r) return ''; const l=r.closest('label'); return l ? norm(l.innerText) : (r.id||r.value); }", ng);
        return v == null ? "" : v.toString();
    }

    /** Click the radio of a group whose label matches (regex, case-insensitive). */
    public boolean clickRadio(String ng, String labelRegex) {
        Object v = page.evaluate("(a) => {" + JS + " const rx=new RegExp(a.re,'i');"
                + " const r=[...document.querySelectorAll(\"input[type=radio][ng-model='\"+a.ng+\"']\")].find(x=>{ const l=x.closest('label'); return rx.test(norm(l?l.innerText:'')); });"
                + " if(!r) return false; r.click(); return true; }", Map.of("ng", ng, "re", labelRegex));
        waitForAngular(300);
        return Boolean.TRUE.equals(v);
    }

    // ---- filters -------------------------------------------------------------------------------------------

    /** The Sub Group options a user can pick (the empty "Select" entry left out). */
    public List<String> subGroupOptions() {
        return strings(page.evaluate("() => { const s=document.getElementById('subgroupid'); if(!s) return [];"
                + " return [...s.options].filter(o=>o.value!=='' && o.value!=='?' && !/^\\s*-*\\s*select\\s*-*\\s*$/i.test(o.textContent)).map(o=>o.textContent.trim()); }"));
    }

    /** Choose a Sub Group by its text ("" = first available). Returns the chosen text, or "" when there is nothing to choose. */
    public String selectSubGroup(String text) {
        Object v = page.evaluate("(t) => { const s=document.getElementById('subgroupid'); if(!s) return '';"
                + " const opts=[...s.options].filter(o=>o.value!=='' && o.value!=='?' && !/^\\s*-*\\s*select\\s*-*\\s*$/i.test(o.textContent));"
                + " const o=t ? opts.find(x=>x.textContent.trim().toLowerCase()===t.toLowerCase()) : opts[0]; if(!o) return '';"
                + " s.value=o.value; try{ const c=angular.element(s).controller('ngModel'); if(c){ c.$setViewValue(o.value); c.$render(); } }catch(e){}"
                + " try{ if(window.jQuery) jQuery(s).select2('val', o.value); }catch(e){} s.dispatchEvent(new Event('change',{bubbles:true}));"
                + " return o.textContent.trim(); }", text == null ? "" : text);
        waitForAngular(400);
        return v == null ? "" : v.toString();
    }

    /** Empty the Sub Group (back to "Select"). */
    public void clearSubGroup() {
        setModel(NG + "subgroupid", null);
        page.evaluate("() => { const s=document.getElementById('subgroupid'); if(s && window.jQuery){ try{ jQuery(s).select2('val',''); }catch(e){} } }");
        waitForAngular(300);
    }

    /** Set an Angular model of the screen's scope directly (text boxes, ids). {@code null} empties it. */
    public void setModel(String ng, String value) {
        page.evaluate("(a) => { const e=document.querySelector(\"[ng-model='\"+a.ng+\"']\"); if(!e) return;"
                + " const s=angular.element(e).scope(); s.$apply(()=>{ const p=a.ng.split('.'); let o=s; for(let i=0;i<p.length-1;i++){ o=o[p[i]]=o[p[i]]||{}; }"
                + "   o[p[p.length-1]] = a.v===undefined ? null : a.v; }); }", mapOf("ng", ng, "v", value));
        waitForAngular(250);
    }

    /** Type into a text box bound to {@code ng} the way a user would (the box's ngModel takes the value). */
    public void typeInto(String ng, String value) {
        page.evaluate("(a) => { const e=[...document.querySelectorAll(\"input[ng-model='\"+a.ng+\"'],textarea[ng-model='\"+a.ng+\"']\")][0]; if(!e) return;"
                + " e.value=a.v; try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(a.v); c.$render(); } }catch(x){}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", Map.of("ng", ng, "v", value));
        waitForAngular(300);
    }

    /** Enter From / To Date (dd/MM/yyyy) through their models and close any calendar left open. */
    public void enterDates(String from, String to) {
        typeInto(NG + "FromDate", from);
        typeInto(NG + "ToDate", to);
        dismissDatePicker();
    }

    public void setDateRange(boolean ticked) {
        page.evaluate("(t) => { const e=document.getElementById('IsDateRange'); if(e && e.checked!==t) e.click(); }", ticked);
        waitForAngular(300);
    }

    // ---- actions -------------------------------------------------------------------------------------------

    /**
     * Click <b>Search</b> and wait for {@code /API/ReportDelivery/fetch} (ExecFlag SearchReportfinalized). Returns the HTTP status, or 0 when
     * no search request went out within {@code timeoutMs} (e.g. a validation message stopped it).
     */
    public int clickSearch(int timeoutMs) {
        problems.clear();
        lastSearchStatus = 0;
        lastSearchExec = "";
        if (!tagButton("fnSearchorderbooking\\(\\)", "__pruSearch")) { problems.add("the Search button is not on screen"); return 0; }
        try {
            Response r = page.waitForResponse(x -> x.url().toLowerCase().contains(API_FETCH)
                            && String.valueOf(x.request().postData()).contains(EXEC_SEARCH),
                    new Page.WaitForResponseOptions().setTimeout(timeoutMs),
                    () -> page.locator("#__pruSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            lastSearchStatus = r.status();
            lastSearchExec = EXEC_SEARCH;
            if (r.status() >= 400) problems.add("Search answered HTTP " + r.status());
        } catch (Exception e) {
            problems.add("no " + EXEC_SEARCH + " request answered within " + timeoutMs + " ms");
        } finally {
            untag("__pruSearch");
        }
        waitForAngular(1200);
        return lastSearchStatus;
    }

    /** Click Search and report whether a search request was SENT within the time (used where a validation should stop it). */
    public boolean clickSearchSendsRequest(int timeoutMs) {
        if (!tagButton("fnSearchorderbooking\\(\\)", "__pruSearch")) { problems.add("the Search button is not on screen"); return false; }
        try {
            Request q = page.waitForRequest(x -> x.url().toLowerCase().contains(API_FETCH) && String.valueOf(x.postData()).contains(EXEC_SEARCH),
                    new Page.WaitForRequestOptions().setTimeout(timeoutMs),
                    () -> page.locator("#__pruSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            return q != null;
        } catch (Exception e) {
            return false;
        } finally {
            untag("__pruSearch");
            waitForAngular(800);
        }
    }

    /** Click <b>Clear</b>. */
    public void clickClear() {
        if (tagButton("fnClear\\(\\)", "__pruClear")) {
            try { page.locator("#__pruClear").click(new Locator.ClickOptions().setTimeout(6000)); } catch (Exception e) { problems.add("Clear could not be clicked"); }
            untag("__pruClear");
        } else problems.add("the Clear button is not on screen");
        waitForAngular(800);
    }

    /** Put an MRN in the MRN box and press the find button beside it (SearchPatientByMRNo). */
    public void findPatient(String mrn) {
        typeInto(NG + "MRNO", mrn);
        if (tagButton("SearchPatientByMRNo\\(\\)", "__pruFind")) {
            try { page.locator("#__pruFind").click(new Locator.ClickOptions().setTimeout(6000)); } catch (Exception e) { problems.add("the MRN find button could not be clicked"); }
            untag("__pruFind");
        } else problems.add("the MRN find button is not on screen");
        waitForAngular(2500);
    }

    /** Click <b>Upload</b> (openuploadpopoup) — with no Test Details row selected the app answers with a message and no dialog. */
    public void clickUpload() {
        if (tagButton("openuploadpopoup\\(\\)", "__pruUpload")) {
            try { page.locator("#__pruUpload").click(new Locator.ClickOptions().setTimeout(6000)); } catch (Exception e) { problems.add("Upload could not be clicked"); }
            untag("__pruUpload");
        } else problems.add("the Upload button is not on screen");
        waitForAngular(1200);
    }

    public boolean uploadDialogOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => { const m=document.getElementById('Reportupload'); return !!m && (m.classList.contains('in')||m.classList.contains('show')) && getComputedStyle(m).display!=='none'; }"));
    }

    /**
     * Show the Upload dialog WITHOUT a selected test, only so its fields and the browser-side file check can be inspected when the environment
     * has no record to select. The app itself would refuse ("please select test"); Save is never pressed.
     */
    public boolean openUploadDialogForInspection() {
        page.evaluate("() => { if(window.jQuery) jQuery('#Reportupload').modal('show'); }");
        waitForAngular(1000);
        return uploadDialogOpen();
    }

    /** Close the Upload dialog through its own Close button (closepopup → resetForm; no request). */
    public void closeUploadDialog() {
        page.evaluate("() => { const m=document.getElementById('Reportupload'); if(!m) return;"
                + " const b=[...m.querySelectorAll('button')].find(x=>/closepopup\\(\\)/.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); else if(window.jQuery) jQuery(m).modal('hide'); }");
        waitForAngular(800);
    }

    /** Buttons inside the Upload dialog, as "text | ng-click". */
    public List<String> uploadDialogButtons() {
        return strings(page.evaluate("() => {" + JS + " const m=document.getElementById('Reportupload'); if(!m) return [];"
                + " return [...m.querySelectorAll('button')].map(b=>norm(b.innerText)+' | '+(b.getAttribute('ng-click')||''))"
                + "   .filter(s=>/saveReport|closepopup/.test(s)); }"));
    }

    /**
     * Give a file to Attach File ({@code #SelectFile}) — this only runs the screen's browser-side {@code fileChanged()} check (size and
     * extension); nothing is sent. Returns the file name the dialog then shows in its Report box (empty = the file was not taken).
     */
    public String chooseFile(Path file) {
        setModel(NG + "reportformat", null);
        try { page.setInputFiles("#SelectFile", file); }
        catch (Exception e) { problems.add("Attach File did not take " + file.getFileName() + ": " + String.valueOf(e.getMessage()).split("\n")[0]); }
        waitForAngular(1200);
        return model(NG + "reportformat");
    }

    // ---- grids ---------------------------------------------------------------------------------------------

    public int rowCount(String grid) {
        Object n = page.evaluate("(g) => {" + JS + " return data(g).length; }", grid);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public List<String> headers(String grid) {
        return strings(page.evaluate("(g) => {" + JS + " const e=grid(g); return e ? [...e.querySelectorAll('.ui-grid-header-cell')]"
                + ".map(h=>norm(h.textContent).replace(/\\s+\\d+$/,'')).filter(Boolean) : []; }", grid));
    }

    /** Up to {@code n} Test Details records as readable "column=value" text (fields as the grid binds them). */
    public List<String> testRecords(int n) {
        return strings(page.evaluate("(k) => {" + JS + " const d=data('" + TESTS + "'); return d.slice(0,k).map(x=>["
                + "'Order No='+(x.orderno||x.orderNo||''),'Sample No='+(x.sampleno||''),'Test='+(x.servicename||x.tariffservicename||x.serviceitemname||''),"
                + "'MRN='+(x.mrno||''),'Uploaded='+(x.reportuploaddatetime||x.uploadeddate||''),'Out Sourced='+(x.isoutsourced===undefined?'':x.isoutsourced),"
                + "'Report='+((x.reportfilepath||'')?'attached':'none')].join(' | ')); }", n));
    }

    /** Index of the first Test Details record that carries an uploaded report file, or -1. */
    public int firstRecordWithReport() {
        Object n = page.evaluate("() => {" + JS + " return data('" + TESTS + "').findIndex(x=>!!(x.reportfilepath||'')); }");
        return n instanceof Number ? ((Number) n).intValue() : -1;
    }

    /** View the report of a Test Details record (the screen's own opendocument: a data-URI preview in #iframedocument, no request). */
    public boolean viewReport(int index) {
        page.evaluate("(i) => {" + JS + " const g=grid('" + TESTS + "'); const s=angular.element(g).scope(); const r=data('" + TESTS + "')[i];"
                + " if(s && r && s.opendocument) s.$apply(()=>s.opendocument({entity:r})); }", index);
        waitForAngular(1500);
        boolean open = Boolean.TRUE.equals(page.evaluate("() => { const m=document.getElementById('iframedocument'); return !!m && getComputedStyle(m).display!=='none'; }"));
        page.evaluate("() => { const m=document.getElementById('iframedocument'); if(!m) return;"
                + " const b=[...m.querySelectorAll('button')].find(x=>/hidepreview/.test(x.getAttribute('ng-click')||'')); if(b) b.click(); else if(window.jQuery) jQuery(m).modal('hide'); }");
        waitForAngular(600);
        return open;
    }

    /** Select the first List of Orders record through the grid API (loads its tests into Test Details through the screen's own fetch). */
    public boolean selectFirstOrder() {
        Object v = page.evaluate("() => {" + JS + " const g=grid('" + ORDERS + "'); if(!g) return false; const d=data('" + ORDERS + "'); if(!d.length) return false;"
                + " try{ const ctl=angular.element(g).controller('uiGrid'); ctl.grid.api.selection.selectRow(d[0]); return true; }catch(e){ return false; } }");
        waitForAngular(2000);
        return Boolean.TRUE.equals(v);
    }

    /** Select the first Test Details record through the grid API (what a tick on the row does). */
    public boolean selectFirstTest() {
        Object v = page.evaluate("() => {" + JS + " const g=grid('" + TESTS + "'); if(!g) return false; const d=data('" + TESTS + "'); if(!d.length) return false;"
                + " try{ const ctl=angular.element(g).controller('uiGrid'); ctl.grid.api.selection.selectRow(d[0]); return true; }catch(e){ return false; } }");
        waitForAngular(800);
        return Boolean.TRUE.equals(v);
    }

    /** Legend words shown under the grid (Stat / VIP / Infected). */
    public List<String> legend() {
        List<String> out = new ArrayList<>();
        for (String l : labels()) if (l.matches("(?i)stat|vip|infected|infectious")) out.add(l);
        return out;
    }

    /** Width check for a box: "shown/needed" pixels; needed &gt; shown means its text is cut off. */
    public String boxFit(String ng) {
        Object v = page.evaluate("(n) => { const e=[...document.querySelectorAll(\"input[ng-model='\"+n+\"']\")][0]; return e ? e.clientWidth+'/'+e.scrollWidth : ''; }", ng);
        return v == null ? "" : v.toString();
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private boolean tagButton(String clickRegex, String id) {
        Object v = page.evaluate("(a) => {" + JS + " const b=btn(new RegExp(a.re)); if(!b) return false; b.scrollIntoView({block:'center'}); b.id=a.id; return true; }",
                Map.of("re", clickRegex, "id", id));
        return Boolean.TRUE.equals(v);
    }

    private void untag(String id) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.removeAttribute('id'); }", id);
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();   // Map.of rejects null values
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static List<String> strings(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }
}
