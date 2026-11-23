package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;

import java.util.List;
import java.util.Map;

/**
 * Nursing Station &gt; <b>Nursing Console</b> &gt; <b>View Nurse Care Report</b> — Page Object.
 *
 * <p>Confirmed live (devhis build v2026.10.20.257):</p>
 * <ul>
 *   <li>{@code #/NursingConsole} — tick a patient ({@code Patient.IsSelected}), then <b>View Nurse Care Report</b>
 *       ({@code fnViewNurseCareReport()}) opens that patient's report list.</li>
 *   <li>{@code #/NurseCareReport} — the <b>Nursing Care Plan List</b>: a plain HTML table (not a ui-grid), one row per
 *       saved report, newest first, with <b>Add New</b>, <b>Print</b> and <b>Back</b> ({@code fnBack()}). The
 *       <b>Action</b> column carries three icons: View (eye, {@code fnView(row)}), Edit (pencil, {@code fnEdit(row)},
 *       greyed with {@code ncr-disabled} when the row cannot be edited) and Delete (trash, {@code fnDelete(row)}) —
 *       <b>Delete is never clicked here</b>; icons are found by their handler, never by position.</li>
 *   <li>View and Edit both open the SAME form route, {@code #/AddNurseCareReport}. View is read-only (every field
 *       disabled, no Save, only Print and Back); Edit is the editable form with the stored values loaded.</li>
 *   <li><b>Save</b> ({@code fnSave()}) answers "Nurse Care Report saved successfully.." and returns to the list — no
 *       confirmation dialog. <b>Back</b> on the list returns to {@code #/NursingConsole}.</li>
 * </ul>
 *
 * <p>Console navigation, the form helpers and the Save/toast handling are shared with {@link AddNurseCareReport}.</p>
 */
public class ViewNurseCareReport extends BasePage {

    private final AddNurseCareReport form;

    public ViewNurseCareReport(Page page) {
        super(page);
        this.form = new AddNurseCareReport(page);
    }

    /** The shared Add/Edit form helpers (navigation, Recommendation, Save + toast, section readers). */
    public AddNurseCareReport form() { return form; }

    public String lastStation = "", lastPatientRow = "", lastAction = "";
    public boolean patientTicked = false;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          // data rows of the report table: the patient banner is a table too, but its rows have fewer cells
          + "const dataRows=()=>[...document.querySelectorAll('table tbody tr')].filter(vis).filter(r=>r.querySelectorAll('td').length>=8);";

    // ---- Nursing Console -------------------------------------------------------------------------------------

    public boolean navigateViaMenu(String baseUrl) { return form.navigateViaMenu(baseUrl); }
    public boolean onConsole() { return form.onConsole(); }

    private boolean hasPatient(String mrn) {
        return Boolean.TRUE.equals(page.evaluate("(m) => { const rx=new RegExp('\\\\b'+m.replace(/[.*+?^${}()|\\[\\]\\\\]/g,'\\\\$&')+'\\\\b');"
                + " return [...document.querySelectorAll(\"input[ng-model='Patient.IsSelected']\")]"
                + "   .some(b=>rx.test(((b.closest('tr')||b.parentElement.parentElement).textContent)||'')); }", mrn));
    }

    private String currentStation() {
        Object t = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='NursingConsole.NursingStation']\");"
                + " return e? ((e.options[e.selectedIndex]||{}).textContent||'').replace(/\\s+/g,' ').trim() : ''; }");
        return t == null ? "" : t.toString();
    }

    /**
     * Tick the patient whose row carries {@code mrn}. The pre-selected nursing station is searched first, then each
     * station in turn. The tick is a real click, read back afterwards. Returns a description.
     */
    public String tickPatient(String mrn) {
        String searched = currentStation();
        boolean found = hasPatient(mrn);
        if (!found) {
            int opts = intOf(page.evaluate("() => { const e=document.querySelector(\"select[ng-model='NursingConsole.NursingStation']\");"
                    + " return e? [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())).length : 0; }"));
            for (int i = 0; i < opts && !found; i++) {
                try {
                    page.locator("select[ng-model='NursingConsole.NursingStation']").first().selectOption(new SelectOption().setIndex(i + 1));
                } catch (Exception e) { System.out.println("ViewNurseCareReport: station select failed - " + e.getMessage()); }
                try {
                    page.waitForFunction("() => document.querySelectorAll(\"input[ng-model='Patient.IsSelected']\").length>0",
                            null, new Page.WaitForFunctionOptions().setTimeout(6000));
                } catch (Exception ignore) { }
                waitForAngular(700);
                searched += ", " + currentStation();
                found = hasPatient(mrn);
            }
        }
        lastStation = currentStation();
        if (!found) { patientTicked = false; return "MRN " + mrn + " is not in any nursing station list (searched " + searched + ")"; }

        Object row = page.evaluate("(m) => {" + JS
                + " const rx=new RegExp('\\\\b'+m.replace(/[.*+?^${}()|\\[\\]\\\\]/g,'\\\\$&')+'\\\\b');"
                + " const c=[...document.querySelectorAll(\"input[ng-model='Patient.IsSelected']\")]"
                + "   .find(b=>rx.test(((b.closest('tr')||b.parentElement.parentElement).textContent)||''));"
                + " if(!c) return ''; c.id='__vcPatient'; return norm((c.closest('tr')||c.parentElement.parentElement).textContent).slice(0,120); }", mrn);
        lastPatientRow = row == null ? "" : row.toString();
        try {
            page.locator("#__vcPatient").scrollIntoViewIfNeeded();
            page.locator("#__vcPatient").click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) {
            System.out.println("ViewNurseCareReport: patient click failed - " + e.getMessage());
            page.evaluate("() => { const e=document.getElementById('__vcPatient'); if(e && !e.checked) e.click(); }");
        }
        waitForAngular(600);
        patientTicked = Boolean.TRUE.equals(page.evaluate("() => { const e=document.getElementById('__vcPatient'); return !!e && e.checked; }"));
        int ticked = intOf(page.evaluate("() => [...document.querySelectorAll(\"input[ng-model='Patient.IsSelected']\")].filter(b=>b.checked).length"));
        page.evaluate("() => { const e=document.getElementById('__vcPatient'); if(e) e.removeAttribute('id'); }");
        return "station " + lastStation + "; ticked: " + lastPatientRow + " -> "
                + (patientTicked ? "tick reads back as selected (" + ticked + " patient ticked)" : "tick did NOT stick");
    }

    /**
     * Click <b>View Nurse Care Report</b> ({@code fnViewNurseCareReport()}) and wait for the list. Returns "" when
     * the list opened, otherwise the toast the console answered with.
     */
    public String clickViewNurseCareReport() {
        page.evaluate("() => { window.__vcToasts=[]; if(window.__vcObs) window.__vcObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                + "   if(t && !window.__vcToasts.includes(t)) window.__vcToasts.push(t); }); };"
                + " window.__vcObs=new MutationObserver(grab); window.__vcObs.observe(document.body,{childList:true,subtree:true}); }");
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fnViewNurseCareReport/.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__vcView'; }");
        try { page.locator("#__vcView").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ViewNurseCareReport: View click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vcView'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => /#\\/nursecarereport\\/?$/i.test(location.hash)"
                    + " && [...document.querySelectorAll('button')].some(b=>/fnAddNew/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(1500);
        if (onList()) return "";
        Object t = page.evaluate("() => (window.__vcToasts||[])[0] || ''");
        return t == null || t.toString().isEmpty() ? "the list did not open and no toast appeared" : t.toString();
    }

    // ---- the report list ----------------------------------------------------------------------------------------

    /** On {@code #/NurseCareReport} exactly — not the form route {@code #/AddNurseCareReport}. */
    public boolean onList() { return page.url().toLowerCase().matches(".*#/nursecarereport/?$"); }

    public boolean onForm() { return page.url().toLowerCase().contains("addnursecarereport"); }

    public int reportRows() {
        return intOf(page.evaluate("() => {" + JS + " return dataRows().length; }"));
    }

    /** Text of report row {@code i} (0 = newest). */
    public String rowText(int i) {
        Object t = page.evaluate("(i) => {" + JS + " const r=dataRows()[i]; return r? norm(r.textContent).slice(0,150) : ''; }", i);
        return t == null ? "" : t.toString();
    }

    /**
     * Click an icon in the <b>Action</b> column of report row {@code i}: {@code "view"} (eye) or {@code "edit"}
     * (pencil). Found by handler ({@code fnView} / {@code fnEdit}); the Delete icon is never touched. Waits for the
     * form. Returns "" when the form opened, otherwise why not.
     */
    public String clickRowAction(int i, String action) {
        String handler = "view".equals(action) ? "fnView" : "fnEdit";
        Object tag = page.evaluate("(a) => {" + JS
                + " const r=dataRows()[a.i]; if(!r) return 'no row';"
                + " const el=[...r.querySelectorAll('[ng-click]')].find(x=>(x.getAttribute('ng-click')||'').indexOf(a.h)>=0);"
                + " if(!el) return 'no icon';"
                + " if(el.classList.contains('ncr-disabled')) return 'disabled';"
                + " el.id='__vcAct'; return 'ok'; }", Map.of("i", i, "h", handler));
        lastAction = tag == null ? "" : tag.toString();
        if (!"ok".equals(lastAction)) return "the " + action + " icon of row " + (i + 1) + " is unavailable (" + lastAction + ")";
        try { page.locator("#__vcAct").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ViewNurseCareReport: " + action + " click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vcAct'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => /addnursecarereport/i.test(location.hash) && document.querySelectorAll('.ncr-field').length>=5",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(1500);
        return onForm() ? "" : "the form did not open after clicking " + action;
    }

    // ---- the form (view = read-only, edit = editable) ----------------------------------------------------------

    /**
     * Describe how editable the open form is: how many of its fields are disabled, whether Save is on screen.
     * View mode: all disabled, no Save. Edit mode: none disabled, Save present.
     */
    public String formState() {
        Object r = page.evaluate("() => {" + JS
                + " const ctl=[...document.querySelectorAll('input,select,textarea')].filter(vis)"
                + "   .filter(e=>/^(Report\\.|it\\.ischecked)/.test(e.getAttribute('ng-model')||''));"
                + " const dis=ctl.filter(e=>e.disabled).length;"
                + " const save=[...document.querySelectorAll('button')].some(b=>vis(b)&&/fnSave\\(/.test(b.getAttribute('ng-click')||''));"
                + " const add=document.querySelectorAll('.ncr-addbtn').length;"
                + " return ctl.length+'|'+dis+'|'+(save?1:0)+'|'+add; }");
        String[] p = (r == null ? "0|0|0|0" : r.toString()).split("\\|");
        return p[0] + " field(s), " + p[1] + " disabled; Save " + (p[2].equals("1") ? "shown" : "not shown")
                + "; " + p[3] + " Add button(s)";
    }

    /** True when every form field is disabled and there is no Save (the View mode). */
    public boolean formReadOnly() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS
                + " const ctl=[...document.querySelectorAll('input,select,textarea')].filter(vis)"
                + "   .filter(e=>/^(Report\\.|it\\.ischecked)/.test(e.getAttribute('ng-model')||''));"
                + " const save=[...document.querySelectorAll('button')].some(b=>vis(b)&&/fnSave\\(/.test(b.getAttribute('ng-click')||''));"
                + " return ctl.length>0 && ctl.every(e=>e.disabled) && !save; }"));
    }

    /** True when the form is editable: Save is shown and the free-text fields are enabled. */
    public boolean formEditable() {
        return Boolean.TRUE.equals(page.evaluate("() => {" + JS
                + " const save=[...document.querySelectorAll('button')].some(b=>vis(b)&&/fnSave\\(/.test(b.getAttribute('ng-click')||''));"
                + " const rec=document.querySelector(\"textarea[ng-model='Report.recommendation']\");"
                + " return save && !!rec && !rec.disabled; }"));
    }

    /** One stored field of the open form, read from the box. */
    public String field(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " if(e.tagName==='SELECT') return ((e.options[e.selectedIndex]||{}).textContent||'').trim(); return e.value||''; }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** "Check through the form": a one-line read-out of everything the stored report holds. */
    public String readForm() {
        StringBuilder sb = new StringBuilder();
        sb.append("Evaluation date ").append(field("Report.evaluationdt")).append("; Status Tag \"").append(field("Report.statustagid")).append("\"");
        sb.append("; Subjective \"").append(field("Report.subjectivedata")).append("\"");
        String obj = field("Report.objectivedata");
        sb.append("; Objective ").append(obj.isEmpty() ? "(empty)" : "\"" + obj + "\"");
        for (String s : AddNurseCareReport.SECTIONS) {
            List<String> c = form.chips(s);
            sb.append("; ").append(s).append(' ').append(c.isEmpty() ? "(none)" : c);
        }
        sb.append("; Recommendation \"").append(field("Report.recommendation")).append("\"");
        return sb.toString();
    }

    /** Click <b>Back</b> ({@code fnBack()}) and wait for the route to change. Returns the route landed on. */
    public String clickBack() {
        String before = page.url();
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fnBack/.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__vcBack'; }");
        try { page.locator("#__vcBack").scrollIntoViewIfNeeded(); page.locator("#__vcBack").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ViewNurseCareReport: Back click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__vcBack'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("(u) => location.href !== u", before, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(2000);
        return page.url().replaceAll("^.*#", "#");
    }

    /** Wait until the Nursing Console patient list is back on screen. */
    public boolean consoleReady() {
        try {
            page.waitForFunction("() => /#\\/nursingconsole\\/?$/i.test(location.hash)"
                    + " && document.querySelectorAll(\"input[ng-model='Patient.IsSelected']\").length>0",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
        return true;
    }

    public int patientCount() { return form.patientCount(); }

    public String consoleStation() { return currentStation(); }

    private static int intOf(Object o) { return o instanceof Number ? ((Number) o).intValue() : -1; }
}
