package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Nursing Station &gt; <b>Monitoring Chart</b> — Page Object.
 *
 * <p>List screen {@code #/MonitoringChartList}: From/To date (today by default), <b>Search</b>
 * ({@code fnSearchMonitoringChartList()}), <b>Add</b> ({@code AddMonitoringChart()}) and a grid
 * <i>Report | MRN | Patient Name | Monitoring Template | Date | Status | Edit</i> whose rows live in the scope's
 * {@code gridOptions.data} ({@code MRNo}, {@code patientname}, {@code templatename}, {@code date}).</p>
 *
 * <p>Add form {@code #/add-MonitoringChart}: <b>MRN</b> ({@code MonitoringChart1.MRNo}) with two buttons — the
 * magnifier ({@code SearchPatientByMRNo()}) and the <b>Patient Search</b> popup
 * ({@code OpenPatientSearchPopupScreen()}, filter {@code PatientData.MRNo}, {@code SearchPatient(0)}, row
 * {@code SetSearchPatient(PR)}) — the read-only <b>Date</b> ({@code MonitoringChart.date}), the <b>Monitoring
 * Template</b> select ({@code MonitoringChart.monitoringtemplateid}, select2-wrapped, ng-options
 * {@code item.value as item.text for item in drpMonitoringTemplateList}) and <b>Save</b>
 * ({@code IUDSaveMonitoringChart()}) / Modify / Close ({@code close()}). Confirmed live on build v2026.11.05.409.2.</p>
 *
 * <p>The MRN is entered through the Patient Search popup (not by typing it and clicking the magnifier): that is the
 * route that sets the patient's admission on the form (same lesson as Patient Feedback).</p>
 */
public class MonitoringChart extends BasePage {

    public MonitoringChart(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/MonitoringChartList";

    /** Screenshot taken while the save toast was still on screen (the app may leave the form right after). */
    public byte[] toastPng;
    /** Screenshot of the filled form just before the last Save. */
    public byte[] filledPng;
    /** The patient the popup selected, as the form's banner shows it ("Prof 1900 JOHN PETER"). */
    public String lastPatientName = "";
    /** The template chosen on the last attempt, and the id the form holds for it. */
    public String lastTemplate = "";
    /** The Date the form showed when saving (dd/MM/yyyy). */
    public String lastDate = "";

    /** JS helpers shared by every snippet (no backslashes: normalisation is built from char codes). */
    private static final String JS =
            "const norm=s=>(s||'').replace(new RegExp('['+String.fromCharCode(9,10,13,160)+' ]+','g'),' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const byNg=ng=>[...document.querySelectorAll('[ng-model]')].filter(e=>e.getAttribute('ng-model')===ng);"
          + "const byClick=(txt,scope)=>[...(scope||document).querySelectorAll('[ng-click]')].filter(e=>(e.getAttribute('ng-click')||'').indexOf(txt)>=0);";

    private static final int TOAST_SETTLE_MS = 2500;

    // ---- small helpers -----------------------------------------------------

    /** Tag the first matching visible element with a temporary id and click it with a real mouse click. */
    private boolean clickTagged(String findJs, String id) {
        Object tag = page.evaluate("() => {" + JS + findJs + " if(!el) return ''; el.id='" + id + "'; return 'ok'; }");
        if (tag == null || tag.toString().isEmpty()) return false;
        try { page.locator("#" + id).click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) {
            System.out.println("MonitoringChart: click on " + id + " failed (" + e.getMessage().split("\n")[0] + ") - trying an in-page click");
            try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); return true; }
            catch (Exception e2) { return false; }
        } finally {
            try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.removeAttribute('id'); }", id); } catch (Exception ignore) { }
        }
    }

    private void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
    }

    // ---- navigation --------------------------------------------------------

    /** Nursing Station -&gt; Monitoring Chart through the sidebar, looked up by its exact href inside the Nursing Station entry. */
    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onListScreen(); attempt++) {
            try {
                expandMenuEntry(null, "^ *nursing station *$");
                boolean clicked = clickTagged(
                        " const top=[...document.querySelectorAll('a')].find(x=>/^nursing station$/i.test(norm(x.textContent)));"
                      + " const li=top?top.closest('li'):null;"
                      + " const el=li?[...li.querySelectorAll('a[href]')].find(x=>x.getAttribute('href')==='" + LIST_ROUTE + "'):null;",
                        "__mcMenu");
                if (!clicked) { System.out.println("MonitoringChart.nav: menu link not found (attempt " + (attempt + 1) + ")"); waitForAngular(1500); continue; }
                try {
                    page.waitForFunction("() => location.hash.toLowerCase().indexOf('monitoringchartlist')>=0 && [...document.querySelectorAll('[ng-click]')].some(e=>(e.getAttribute('ng-click')||'').indexOf('AddMonitoringChart')>=0)",
                            null, new Page.WaitForFunctionOptions().setTimeout(15000));
                } catch (Exception ignore) { System.out.println("MonitoringChart.nav: the list did not show (attempt " + (attempt + 1) + ")"); }
                waitForAngular(1500);
            } catch (Exception e) {
                System.out.println("MonitoringChart.nav: raced the route change (attempt " + (attempt + 1) + ") - " + e.getMessage().split("\n")[0]);
                waitForAngular(2000);
            }
        }
        return onListScreen();
    }

    /** On the real list: the route AND the screen's own Add button (the SPA can leave another screen mounted under this hash). */
    public boolean onListScreen() {
        Object r = page.evaluate("() => {" + JS
                + " return location.hash.toLowerCase().indexOf('monitoringchartlist')>=0"
                + "   && byClick('AddMonitoringChart').some(vis) && byNg('MonitoringChart.fromdate').some(vis); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean onFormScreen() {
        Object r = page.evaluate("() => {" + JS
                + " return location.hash.toLowerCase().indexOf('add-monitoringchart')>=0 && byNg('MonitoringChart1.MRNo').some(vis)"
                + "   && byNg('MonitoringChart.monitoringtemplateid').length>0; }");
        return Boolean.TRUE.equals(r);
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => {" + JS
                + " const h=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (h||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** Click <b>Add</b> on the list and wait for the entry form. */
    public boolean clickAdd() {
        boolean clicked = clickTagged(" const el=byClick('AddMonitoringChart').find(vis);", "__mcAdd");
        if (!clicked) return false;
        try {
            page.waitForFunction("() => location.hash.toLowerCase().indexOf('add-monitoringchart')>=0 && [...document.querySelectorAll('[ng-model]')].some(e=>e.getAttribute('ng-model')==='MonitoringChart1.MRNo' && e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        waitForAngular(1200);
        return onFormScreen();
    }

    // ---- list --------------------------------------------------------------

    /** Click <b>Search</b> on the list (the From/To dates default to today) and give the grid time to refill. */
    public boolean clickSearch() {
        boolean clicked = clickTagged(" const el=byClick('fnSearchMonitoringChartList').find(vis);", "__mcSearch");
        waitForAngular(2500);
        return clicked;
    }

    /**
     * The list's rows, read from the grid's data (the scope's {@code gridOptions.data}) rather than from rendered
     * cells — ui-grid only renders what is in view. Each row: {MRN, Patient Name, Monitoring Template, Date}.
     */
    public List<String[]> listRows() {
        Object r = page.evaluate("() => {" + JS
                + " let rows=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(rows) return; try{ const s=window.angular.element(el).scope();"
                + "   if(s && s.gridOptions && Array.isArray(s.gridOptions.data) && s.gridOptions.data.length>0) rows=s.gridOptions.data; }catch(e){} });"
                + " if(!rows) return []; return rows.map(x=>[String(x.MRNo||''), norm(x.patientname), norm(x.templatename), String(x.date||'')]); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) {
            for (Object o : (List<?>) r) {
                if (o instanceof List) {
                    List<?> l = (List<?>) o;
                    out.add(new String[]{String.valueOf(l.get(0)), String.valueOf(l.get(1)), String.valueOf(l.get(2)), String.valueOf(l.get(3))});
                }
            }
        }
        return out;
    }

    // ---- patient (MRN) via the Patient Search popup --------------------------

    /**
     * Enter the MRN the way the user asked — through the form's <b>Patient Search</b> popup: open it, filter by the
     * MRN, Search, and click <b>Select</b> on that patient's row. Returns what happened; a value starting with
     * "(" means it failed.
     */
    public String selectPatientViaPopup(String mrn) {
        if (!clickTagged(" const el=byClick('OpenPatientSearchPopupScreen').find(vis);", "__mcPop"))
            return "(the Patient Search button was not found on the form)";
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal')].some(m=>m.classList.contains('in') && (m.offsetWidth||m.offsetHeight||m.getClientRects().length))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { return "(the Patient Search popup did not open)"; }
        waitForAngular(900);
        // MRN filter inside the popup ('PatientData.MRNo' is the one the search really sends)
        page.evaluate("(m) => {" + JS
                + " const e=byNg('PatientData.MRNo').find(x=>x.closest('.modal') && vis(x)); if(!e) return;"
                + " const c=window.angular.element(e).controller('ngModel'); e.value=m; if(c){ c.$setViewValue(m); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", mrn);
        boolean searched = clickTagged(" const el=byClick('SearchPatient(0)', document.querySelector('.modal.in')||document).find(vis);", "__mcPopSearch");
        if (!searched) { closePopup(); return "(the popup's Search button was not found)"; }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal.in table tbody tr')].filter(r=>r.offsetParent!==null && (r.textContent||'').trim()).length>0",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { closePopup(); return "(the Patient Search popup listed no patient for MRN " + mrn + ")"; }
        waitForAngular(700);
        // the row of THIS mrn only — never a different patient
        Object row = page.evaluate("(m) => {" + JS
                + " const rows=[...document.querySelectorAll('.modal.in table tbody tr')].filter(r=>r.offsetParent!==null);"
                + " const pick=rows.find(r=>norm(r.textContent).split(' ').indexOf(m)>=0); if(!pick) return '';"
                + " const b=[...pick.querySelectorAll('[ng-click]')].find(x=>(x.getAttribute('ng-click')||'').indexOf('SetSearchPatient')>=0); if(!b) return '';"
                + " b.id='__mcPopSelect'; return norm(pick.textContent).slice(0,120); }", mrn);
        String rowText = row == null ? "" : row.toString();
        if (rowText.isEmpty()) { closePopup(); return "(the popup showed no selectable row for MRN " + mrn + ")"; }
        try { page.locator("#__mcPopSelect").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { closePopup(); return "(the popup's Select button could not be clicked)"; }
        finally { page.evaluate("() => { const e=document.getElementById('__mcPopSelect'); if(e) e.removeAttribute('id'); }"); }
        // popup closes and the MRN box takes the MRN
        try {
            page.waitForFunction("(m) => ![...document.querySelectorAll('.modal.in')].length && [...document.querySelectorAll('[ng-model]')].some(e=>e.getAttribute('ng-model')==='MonitoringChart1.MRNo' && e.value===m)",
                    mrn, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) {
            String now = String.valueOf(page.evaluate("() => { const e=[...document.querySelectorAll('[ng-model]')].find(x=>x.getAttribute('ng-model')==='MonitoringChart1.MRNo'); return e?e.value:'(no MRN box)'; }"));
            closePopup();
            return "(Select did not fill the MRN box - it holds \"" + now + "\")";
        }
        waitForAngular(1500);
        lastPatientName = patientBannerName();
        return "Patient Search popup -> MRN " + mrn + " -> Select; MRN box = " + mrn
                + (lastPatientName.isEmpty() ? "" : "; patient shown: " + lastPatientName);
    }

    private void closePopup() {
        clickTagged(" const el=byClick('CloseModel', document.querySelector('.modal.in')||document).find(vis);", "__mcPopClose");
        waitForAngular(500);
    }

    /** The patient name the form's banner shows ("Name : Prof 1900 JOHN PETER MRN: 1100339278 ..."), or "". */
    public String patientBannerName() {
        Object r = page.evaluate("() => {" + JS
                + " const t=norm(document.body?document.body.innerText:''); const m=t.match(new RegExp('Name *: *(.+?) *MRN *:'));"
                + " return m?norm(m[1]):''; }");
        return r == null ? "" : r.toString();
    }

    // ---- monitoring template -------------------------------------------------

    /** The real options of the Monitoring Template select (without "--Select--"). */
    public List<String> templateOptions() {
        Object r = page.evaluate("() => {" + JS
                + " const e=byNg('MonitoringChart.monitoringtemplateid')[0]; if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && norm(o.textContent) && !/^-*select-*$/i.test(norm(o.textContent))).map(o=>norm(o.textContent)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /**
     * Select the Monitoring Template whose text is {@code wanted} (exact, case-insensitive). Returns the chosen text with
     * the id the form now holds, or a value starting with "(" when it could not be set. The select is select2-wrapped
     * and its model is the NUMBER (option value is Angular's hash "number:8"), so the model is verified, not assumed.
     */
    public String selectTemplate(String wanted) {
        Object r = page.evaluate("(w) => {" + JS
                + " const A=window.angular; const sel=byNg('MonitoringChart.monitoringtemplateid')[0]; if(!sel) return '(no Monitoring Template select)';"
                + " const opt=[...sel.options].find(o=>o.value && norm(o.textContent).toLowerCase()===String(w).toLowerCase());"
                + " if(!opt) return '(template \"'+w+'\" is not in the list)';"
                + " sel.value=opt.value; sel.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ const c=A.element(sel).controller('ngModel'); if(c && (c.$modelValue===undefined||c.$modelValue===null||c.$modelValue==='')){"
                + "   const sc=A.element(sel).scope(); const n=Number(String(opt.value).split(':').pop()); sc.$apply(function(){ sc.MonitoringChart.monitoringtemplateid=n; }); } }catch(e){}"
                + " try{ if(window.jQuery && jQuery(sel).data('select2')) jQuery(sel).select2('val', opt.value); }catch(e){}"
                + " const c2=A.element(sel).controller('ngModel');"
                + " return norm(opt.textContent)+' (id '+(c2?c2.$modelValue:'?')+')'; }", wanted);
        waitForAngular(600);
        String res = r == null ? "" : r.toString();
        if (!res.startsWith("(")) lastTemplate = wanted;
        return res;
    }

    // ---- save ----------------------------------------------------------------

    /** The form's Date box (read-only, today). */
    public String formDate() {
        Object r = page.evaluate("() => { const e=[...document.querySelectorAll('[ng-model]')].find(x=>x.getAttribute('ng-model')==='MonitoringChart.date'); return e?e.value:''; }");
        lastDate = r == null ? "" : r.toString().trim();
        return lastDate;
    }

    /**
     * Click <b>Save</b> ({@code IUDSaveMonitoringChart()}), accept a confirm dialog if one appears, and return the
     * toast. The toast is screenshotted while it is still on screen ({@link #toastPng}); the form is screenshotted
     * just BEFORE the click ({@link #filledPng}) because a successful save leaves it.
     */
    public String saveAndGetToast() {
        try { filledPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("MonitoringChart: filled-form screenshot failed - " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { window.__mcToasts=[]; if(window.__mcObs) window.__mcObs.disconnect();"
                // keep the toast on screen so it can be screenshotted (toastr otherwise fades it out)
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').split(String.fromCharCode(10)).join(' ').trim(); if(t && !window.__mcToasts.includes(t)) window.__mcToasts.push(t); }); };"
                + " window.__mcObs=new MutationObserver(grab); window.__mcObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        boolean clicked = clickTagged(" const el=byClick('IUDSaveMonitoringChart').filter(vis).find(x=>/^save$/i.test(norm(x.textContent)));", "__mcSave");
        if (!clicked) return "";
        // "Do you want to save?" style confirmation, if the app raises one
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal.in,.sweet-alert')].some(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal.in,.sweet-alert')].find(m=>m.getBoundingClientRect().width>0 && /do you want|are you sure|confirm/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok|save|submit|confirm)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(700);
        }
        try {
            page.waitForFunction("() => (window.__mcToasts||[]).some(a=>/monitoring|chart|saved|success|added|updated|please|select|enter|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        // let the toast finish fading in (timeOut=0 above keeps it from fading out) before it is photographed
        waitForAngular(TOAST_SETTLE_MS);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("MonitoringChart: toast screenshot failed - " + e.getMessage().split("\n")[0]); }
        Object t = page.evaluate("() => { const a=window.__mcToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(300);
        return t == null ? "" : t.toString().trim();
    }

    /** After a save: leave the form for the list if the app left us on it (Close). Returns true when the list is showing. */
    public boolean backToList() {
        // the save toast (kept on screen for its screenshot) would otherwise sit over the list in the next screenshot
        clearToasts();
        if (onListScreen()) return true;
        if (onFormScreen()) {
            clearToasts();
            clickTagged(" const el=byClick('close()').filter(vis).find(x=>/^close$/i.test(norm(x.textContent)));", "__mcClose");
            try {
                page.waitForFunction("() => location.hash.toLowerCase().indexOf('monitoringchartlist')>=0", null, new Page.WaitForFunctionOptions().setTimeout(10000));
            } catch (Exception ignore) { }
            waitForAngular(1500);
        }
        return onListScreen();
    }

    /** What the form offers — for a FAIL message when something is not where it was expected. */
    public String describeForm() {
        Object r = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('input,select,textarea,button')].filter(vis).slice(0,25).map(e=>e.tagName.toLowerCase()+'['+(e.getAttribute('ng-model')||e.getAttribute('ng-click')||e.id||'')+']'+norm(e.textContent||e.value).slice(0,20)).join(' ; '); }");
        return r == null ? "" : r.toString();
    }
}
