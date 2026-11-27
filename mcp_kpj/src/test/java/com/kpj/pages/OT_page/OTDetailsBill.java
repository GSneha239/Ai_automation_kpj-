package com.kpj.pages.OT_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operation Theatre &gt; <b>OT Details - Bill</b> — Page Object (list {@code #/OTDetailsListopdbill}, controller
 * {@code OTDetailsListController}; New / Edit open the shared OT Details form {@code #/add-OTDetails} / {@code #/edit-OTDetails/:id/:patientid}).
 *
 * <p>Confirmed live on devhis (v2026.11.26.546.1) and against {@code App/OperationTheatre/controller/OTDetailsListController.js}:</p>
 * <ul>
 *   <li>List filters ({@code otdetailslist.*}): fromdate / todate (dd/MM/yyyy, today by default), ottheatreid (select2) and ottableid (select2,
 *       filled after the theatre), MRNo with the search button {@code SearchPatientByMRNo()} (looks the patient up with the Reg. Type, IPD only)
 *       and the Patient Search popup, Reg. Type radio {@code otdetailslist.opd_ipd} (only IPD), Search {@code fngetOTScheduleGrid()}.</li>
 *   <li>Grid {@code gridOptions} (ui-grid, one row per procedure): MRN, Patient Name, Doctor Name, Procedure Name, Ward, Bed Name, Date, Start Time,
 *       End Time, OT Room, OT Table, Emergency, Status (checkbox {@code changeStatus($event,row)}), Edit ({@code editOTSchedule(row)}, disabled while
 *       inactive), Bill ({@code GetBill(row)}: OPD rows open {@code #/OPDBill}, IPD rows open {@code #/IPDBill}, the patient handed over through
 *       sessionStorage {@code otdetailsMRNO}).</li>
 *   <li>Buttons: Investigation View (Bootstrap modal {@code #EMRinvestignView}), Print ({@code printOtReport()} → OtDetailsReport.aspx in a new tab),
 *       File Linking ({@code ViewFileLinking()}, needs a patient), New ({@code OTDetails()}), Back ({@code BackForm()} → {@code #/NursingEMR}).</li>
 *   <li>Form: MRN {@code otdetails.MRNo} + {@code SearchPatientByMRNo2()}, Reg. Type {@code otdetails.opd_ipd}; OT sheet {@code otsheetdetails.*}: date,
 *       fromtime / totime (timepicker-pop, typed as "10:00 AM", "use current time" checkboxes {@code useCurrentTime} / {@code useCurrentTime1}),
 *       ottheatreid, otresultid, otstatusid, proceduretypeid, anesthesiatypeid (select2). Save {@code fnIUDOTSchedules()} ("OT Detail Saved
 *       Successfully."), hidden when the form is opened from this list's Edit ({@code isbillandissue==1}); Back {@code BackPage()}.</li>
 * </ul>
 */
public class OTDetailsBill extends BasePage {

    public static final String ROUTE = "#/OTDetailsListopdbill";
    public static final String SEL_FROM = "input[ng-model='otdetailslist.fromdate']";
    public static final String SEL_TO = "input[ng-model='otdetailslist.todate']";
    public static final String SEL_MRN = "input[ng-model='otdetailslist.MRNo']";
    public static final String NG_THEATRE = "otdetailslist.ottheatreid", NG_TABLE = "otdetailslist.ottableid";
    public static final String F_MRN = "input[ng-model='otdetails.MRNo']";

    public OTDetailsBill(Page page) { super(page); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const gsc=()=>{ const g=document.querySelector('[ui-grid=gridOptions]'); return g?angular.element(g).scope():null; };"
          + "const fsc=()=>{ const e=document.querySelector(\"[ng-model='otdetails.MRNo']\"); return e?angular.element(e).scope():null; };";

    public final List<String> navNotes = new ArrayList<>();
    public long lastLoadMs = -1;

    // ---- navigation ------------------------------------------------------------------------------------------------

    /** Sidebar OT &rarr; OT Details - Bill; true once the list and its Search button are ready. */
    public boolean navigateViaMenu() {
        navNotes.clear();
        try {
            page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(a=>(a.textContent||'').trim()==='OT' && a.offsetParent)",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception e) { navNotes.add("sidebar 'OT' entry not shown within 30 s"); return false; }
        long t0 = System.currentTimeMillis();
        if (!linkVisible() && !expandMenuEntry(null, "^OT$")) navNotes.add("'OT' did not open");
        try {
            Locator link = page.locator("a[href='" + ROUTE + "']:visible").first();
            link.waitFor(new Locator.WaitForOptions().setTimeout(10000));
            link.click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            navNotes.add("'OT Details - Bill' link not clickable - " + String.valueOf(e.getMessage()).split("\\R")[0]);
            if (!onList()) return false;
        }
        boolean ok = waitList();
        lastLoadMs = System.currentTimeMillis() - t0;
        return ok;
    }

    /** Leave the screen (hash change, not a reload) and open it again from the menu: a fresh controller with default filters. */
    public boolean reopen() {
        page.evaluate("() => { location.hash = '#/PatientDashboard'; }");
        try { page.waitForFunction("() => /PatientDashboard/i.test(location.href)", null, new Page.WaitForFunctionOptions().setTimeout(10000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        return navigateViaMenu();
    }

    private boolean linkVisible() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll(\"a[href='" + ROUTE + "']\")].some(vis); }"));
    }

    public boolean waitList() {
        try {
            page.waitForFunction("() => /#\\/OTDetailsListopdbill/i.test(location.href) && [...document.querySelectorAll('button')].some(b=>b.getAttribute('ng-click')==='fngetOTScheduleGrid()' && (b.offsetWidth||b.offsetHeight))",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception e) { navNotes.add("list or Search button not ready within 25 s (" + page.url() + ")"); return false; }
        return waitForHttpIdle(60000);
    }

    public boolean onList() { return page.url().matches("(?i).*#/OTDetailsListopdbill/?$"); }
    public String hash() { Object r = page.evaluate("() => location.hash"); return r == null ? "" : r.toString(); }

    public boolean waitForHttpIdle(int timeoutMs) {
        try {
            page.waitForFunction("() => { try { return angular.element(document.body).injector().get('$http').pendingRequests.length===0; } catch(e) { return true; } }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs).setPollingInterval(200));
            return true;
        } catch (Exception e) { return false; }
    }

    // ---- list ------------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> listModel() {
        Object r = page.evaluate("() => {" + JS + " const s=gsc(); const m=(s&&s.otdetailslist)||{}; const radios=[...document.querySelectorAll(\"input[ng-model='otdetailslist.opd_ipd']\")];"
                + " return { from:m.fromdate||'', to:m.todate||'', mrn:m.MRNo||'', patientid:m.patientid||0, regTypes: radios.map(r=>norm((r.closest('label')||r.parentElement).textContent)+(r.checked?'(checked)':'')) }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    /** Visible list buttons by label → ng-click. */
    @SuppressWarnings("unchecked")
    public Map<String, String> listButtons() {
        Object r = page.evaluate("() => {" + JS + " const o={}; [...document.querySelectorAll('button')].filter(vis).filter(b=>!b.closest('[ui-grid]') && norm(b.textContent))"
                + " .forEach(b=>o[norm(b.textContent)]=b.getAttribute('ng-click')||b.getAttribute('data-target')||''); return o; }");
        Map<String, String> out = new LinkedHashMap<>();
        if (r instanceof Map) ((Map<String, Object>) r).forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    public void typeDate(String selector, String value) {
        Locator box = page.locator(selector);
        box.fill(value);
        box.dispatchEvent("change");
        box.dispatchEvent("blur");
        page.evaluate("() => { document.querySelectorAll('._720kb-datepicker-calendar').forEach(c=>c.classList.remove('_720kb-datepicker-open')); }");
        waitForAngular(300);
    }

    public void typeMrn(String mrn) {
        Locator box = page.locator(SEL_MRN);
        box.fill(mrn);
        box.dispatchEvent("change");
        waitForAngular(200);
    }

    public void clickListMrnSearch() { clickNg("SearchPatientByMRNo();", 30000); }

    public long search() {
        long t0 = System.currentTimeMillis();
        clickNg("fngetOTScheduleGrid()", 120000);
        return System.currentTimeMillis() - t0;
    }

    public String pick(String ngModel, String text) { String r = Select2Pick.pick(page, ngModel, text); waitForAngular(600); waitForHttpIdle(20000); return r; }

    public List<String> options(String ngModel) { return Select2Pick.names(page, ngModel); }

    /** Records the grid holds. */
    public int rows() {
        Object r = page.evaluate("() => {" + JS + " const s=gsc(); return s&&s.gridOptions&&s.gridOptions.data?s.gridOptions.data.length:-1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Given fields of every grid record. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> data(String... fields) {
        Object r = page.evaluate("(f) => {" + JS + " const s=gsc(); return ((s&&s.gridOptions&&s.gridOptions.data)||[]).map(x=>{ const o={}; f.forEach(k=>o[k]=x[k]===undefined?null:x[k]); return o; }); }",
                Arrays.asList(fields));
        List<Map<String, Object>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add((Map<String, Object>) o);
        return out;
    }

    /** Visible grid column headers. */
    @SuppressWarnings("unchecked")
    public List<String> headers() {
        Object r = page.evaluate("() => {" + JS + " const g=document.querySelector('[ui-grid=gridOptions]'); return g?[...g.querySelectorAll('.ui-grid-render-container-body .ui-grid-header-cell')]"
                + ".map(h=>{ const l=h.querySelector('.ui-grid-header-cell-label'); return norm(l?l.textContent:h.textContent); }).filter(Boolean):[]; }");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Click a row's cell button (Edit / Bill) or Status checkbox for the grid record with the given id (first match). */
    public boolean clickRow(String ngClick, Object id) {
        // ui-grid draws only the columns in view: scroll the grid to its right edge so Status / Edit / Bill exist in the page
        page.evaluate("() => { const v=document.querySelector('[ui-grid=gridOptions] .ui-grid-render-container-body .ui-grid-viewport'); if(v){ v.scrollLeft=v.scrollWidth; v.dispatchEvent(new Event('scroll')); } }");
        waitForAngular(800);
        Object tagged = page.evaluate("([nc,id]) => {" + JS + " const g=document.querySelector('[ui-grid=gridOptions]'); if(!g) return false;"
                + " const el=[...g.querySelectorAll(\"[ng-click='\"+nc+\"']\")].find(b=>{ try{ return String(angular.element(b).scope().row.entity.id)===String(id); }catch(e){ return false; } });"
                + " if(!el) return false; el.scrollIntoView({block:'center'}); el.id='__obRow'; return true; }", Arrays.asList(ngClick, String.valueOf(id)));
        if (!Boolean.TRUE.equals(tagged)) return false;
        try { page.locator("#__obRow").click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { return false; }
        finally { try { page.evaluate("() => { const e=document.getElementById('__obRow'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { } }
        waitForAngular(800);
        waitForHttpIdle(30000);
        return true;
    }

    /** Status (Active) value the grid shows for each row of a record id. */
    @SuppressWarnings("unchecked")
    public List<Object> statusOf(Object id) {
        Object r = page.evaluate("(id) => {" + JS + " const s=gsc(); return ((s&&s.gridOptions.data)||[]).filter(x=>String(x.id)===String(id)).map(x=>x.Status); }", String.valueOf(id));
        return r instanceof List ? new ArrayList<>((List<Object>) r) : new ArrayList<>();
    }

    // ---- buttons -----------------------------------------------------------------------------------------------------

    /** Click a visible button by its ng-click (exact) and wait for Angular to settle. */
    public boolean clickNg(String ngClick, int idleMs) {
        Locator b = page.locator("button[ng-click='" + ngClick + "']:visible").first();
        try { b.scrollIntoViewIfNeeded(); b.click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { return false; }
        waitForAngular(600);
        waitForHttpIdle(idleMs);
        waitForAngular(300);
        return true;
    }

    public boolean clickInvestigationView() {
        try { page.locator("button[data-target='#EMRinvestignView']:visible").first().click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { return false; }
        waitForAngular(1500);
        return true;
    }

    public boolean modalShown(String id) {
        return Boolean.TRUE.equals(page.evaluate("(id) => { const m=document.getElementById(id); return !!m && (m.classList.contains('in')||m.classList.contains('show')) && getComputedStyle(m).display!=='none'; }", id));
    }

    public void closeModal(String id) {
        page.evaluate("(id) => { try{ $('#'+id).modal('hide'); }catch(e){} }", id);
        waitForAngular(600);
    }

    // ---- form --------------------------------------------------------------------------------------------------------

    public boolean onForm() { return page.url().matches("(?i).*#/(add|edit)-OTDetails.*"); }

    public boolean waitForm() {
        try {
            page.waitForFunction("() => /#\\/(add|edit)-OTDetails/i.test(location.href) && document.querySelector(\"[ng-model='otdetails.MRNo']\")",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { return false; }
        waitForHttpIdle(30000);   // the form keeps loading lists in the background; it is usable once the MRN box is there
        waitForAngular(1500);
        return true;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> formState() {
        Object r = page.evaluate("() => {" + JS + " const s=fsc(); if(!s) return {onForm:false}; const o=s.otdetails||{}, t=s.otsheetdetails||{};"
                + " const sel=ng=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); const x=e&&e.options[e.selectedIndex]; return x?norm(x.text):''; };"
                + " const times=[...document.querySelectorAll(\"timepicker-pop input[ng-model='inputTime']\")].slice(0,2).map(i=>i.value);"
                + " const ipd=document.querySelector(\"input[ng-model='otdetails.opd_ipd']\");"
                + " const save=[...document.querySelectorAll('button')].find(b=>/fnIUDOTSchedules/.test(b.getAttribute('ng-click')||''));"
                + " const title=document.querySelector(\"select[ng-model='otdetails.PrefixID']\");"
                + " return { onForm:true, mrn:o.MRNo||'', name:norm((o.FirstName||'')+' '+(o.LastName||'')), ward:o.ward||'', bed:o.bed||'', doctor:norm(o.doctorname||''),"
                + "  ipdChecked: !!(ipd&&ipd.checked), titleOptions: title?title.options.length:-1, date:t.date||'', from:times[0]||'', to:times[1]||'',"
                + "  fromModel: t.fromtime?String(t.fromtime):'', toModel: t.totime?String(t.totime):'', minutes: t.totalhours==null?'':String(t.totalhours),"
                + "  theatre: sel('otsheetdetails.ottheatreid'), result: sel('otsheetdetails.otresultid'), status: sel('otsheetdetails.otstatusid'),"
                + "  procType: sel('otsheetdetails.proceduretypeid'), anesthesia: sel('otsheetdetails.anesthesiatypeid'), saveVisible: !!save && vis(save) }; }");
        return r instanceof Map ? (Map<String, Object>) r : new LinkedHashMap<>();
    }

    public void formMrn(String mrn) {
        Locator box = page.locator(F_MRN);
        box.fill(mrn);
        box.dispatchEvent("change");
        waitForAngular(200);
    }

    public boolean tickIpd() {
        try { page.locator("input[ng-model='otdetails.opd_ipd']").first().check(new Locator.CheckOptions().setTimeout(5000)); }
        catch (Exception e) { return false; }
        waitForAngular(300);
        return true;
    }

    public boolean clickFormMrnSearch() { return clickNg("SearchPatientByMRNo2();", 30000); }

    public boolean clickSave() {
        Locator b = page.locator("button[ng-click^='fnIUDOTSchedules']:visible").first();
        try { b.scrollIntoViewIfNeeded(); b.click(new Locator.ClickOptions().setTimeout(8000)); }
        catch (Exception e) { return false; }
        waitForAngular(1000);
        waitForHttpIdle(60000);
        waitForAngular(800);
        return true;
    }

    public boolean clickFormBack() { return clickNg("BackPage()", 30000); }

    /** Tick (or untick) the "use current time" checkbox beside From Time (0) or To Time (1). */
    public void useCurrentTime(int which, boolean on) {
        Locator c = page.locator("input[ng-model='" + (which == 0 ? "useCurrentTime" : "useCurrentTime1") + "']").first();
        try { if (on) c.check(new Locator.CheckOptions().setTimeout(5000)); else c.uncheck(new Locator.UncheckOptions().setTimeout(5000)); } catch (Exception ignore) { }
        waitForAngular(500);
    }

    /** Type a time ("10:00 AM") into From Time (0) or To Time (1). */
    public void typeTime(int which, String value) {
        Locator box = page.locator("timepicker-pop[ng-model='otsheetdetails." + (which == 0 ? "fromtime" : "totime") + "'] input[ng-model='inputTime']").first();
        box.click(new Locator.ClickOptions().setTimeout(5000));
        box.fill("");
        box.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(40));
        box.press("Tab");
        page.mouse().click(2, 2);
        waitForAngular(500);
    }

    // ---- messages --------------------------------------------------------------------------------------------------

    /** Record every JAlert (toast / warning) the app raises into window.__obAlerts — survives route changes (single-page app). */
    public void spyAlerts() {
        page.evaluate("() => { window.__obAlerts=[]; const o=window.__obOrigJAlert||window.JAlert; window.__obOrigJAlert=o;"
                + " window.JAlert=function(m,t){ window.__obAlerts.push((t||'')+': '+m); try{ return o.apply(this,arguments); }catch(e){} }; }");
    }

    @SuppressWarnings("unchecked")
    public List<String> alerts() {
        Object r = page.evaluate("() => window.__obAlerts || []");
        return r instanceof List ? new ArrayList<>((List<String>) r) : new ArrayList<>();
    }

    /** Patient details a bill screen (OPD Bill / IPD Bill) loaded: its MRN box value. */
    public String billScreenMrn() {
        Object r = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='PatientDetails.MRNo']\"); return e?e.value:''; }");
        return r == null ? "" : r.toString().trim();
    }
}
