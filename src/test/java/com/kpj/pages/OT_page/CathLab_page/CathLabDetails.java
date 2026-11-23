package com.kpj.pages.OT_page.CathLab_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * OT &gt; Cath Lab &gt; <b>Cath Lab Details</b> — Page Object (list {@code #/CathLabDetailsList} → <b>New</b> → form {@code #/add-CathLabDetails}).
 *
 * <p>Confirmed live (devhis build v2026.11.17.477). The menu is OT &rarr; <b>Cath Lab</b> (a group) &rarr; Cath Lab Schedule / <b>Cath Lab Details</b>. The screen is the OT Details controller
 * (same {@code otdetails} / {@code otsheetdetails} models, same {@code OTDetails()} New button, same {@code fnIUDOTSchedules()} Save) with the heading "Cath Lab Details". The form opens with the
 * patient block (MRN search) and a row of bootstrap TABS: <b>OT Sheet Details</b> (open by default), Surgery Details, Doc/Emp Details, OT Notes, Post-Operative Instructions, Preoperative check List,
 * Sign In - Time Out - Sign Out, Preoperative Diagnosis.</p>
 *
 * <p><b>OT Sheet Details tab</b> ({@code #OTsheet}): Date (read-only, today), From Time*, To Time*, Total Minutes (read-only), OT Theatre*, OT Result*, OT Status, Procedure Type*, Wheel In / Wheel Out Time,
 * Anesthesia Start / End Time, Surgery Start / End Time, Anesthesia Type*, Surgical Team, and - the difference from OT Details - Radiation Time(Min), Fluoroscopy Time(Min), Radiation Dose, Remarks, Discussion.</p>
 * <ul>
 *   <li><b>The eight times</b> are {@code <timepicker-pop input-time="otsheetdetails.&lt;field&gt;">} boxes (typing "09:00 AM" works; the model becomes a Date). Focusing a box opens its picker popup, which
 *       stays open and would cover the next box, so it is closed again after each entry.</li>
 *   <li><b>The five selects</b> are select2 boxes over plain selects; they are picked the way a user does (open, click the entry) with {@link Select2Pick}.</li>
 *   <li><b>Surgical Team</b>, <b>Radiation Time</b> and <b>Fluoroscopy Time</b> carry {@code valid-number} (digits only: Surgical Team is a head count); Radiation Dose and Remarks take free text.</li>
 *   <li><b>Total Minutes is NOT calculated</b> when the times are typed or picked: its calculation hangs on {@code ng-blur} of the {@code timepicker-pop} element, which never receives a blur from the box inside it.</li>
 *   <li><b>Save</b> ({@code fnIUDOTSchedules}) needs a patient first ("Please select patient"); this page object never clicks it.</li>
 * </ul>
 */
public class CathLabDetails extends BasePage {

    public CathLabDetails(Page page) { super(page); }

    /** What the last fill could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }
    public void resetProblems() { problems.clear(); }

    public String lastTabPane = "";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String TOP = "OT", GROUP = "Cath Lab", MENU = "Cath Lab Details";

    // =====================================================================================================================
    // Navigation: OT > Cath Lab > Cath Lab Details, then New
    // =====================================================================================================================

    /** 1 = the OT entry is open, 0 = closed, -1 = not on screen. */
    private int topState() {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", TOP);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickTop() {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", TOP);
        return Boolean.TRUE.equals(r);
    }

    /** 1 = the Cath Lab group (inside OT) is open, 0 = closed, -1 = not on screen. */
    private int groupState() {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return -1; const pli=pa.closest('li'); if(!pli) return -1;"
                + " const g=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!g) return -1; const li=g.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }",
                Arrays.asList(TOP, GROUP));
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickGroup() {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const g=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!g) return false; g.click(); return true; }", Arrays.asList(TOP, GROUP));
        return Boolean.TRUE.equals(r);
    }

    /** Tag the "Cath Lab Details" link INSIDE the OT entry (other entries elsewhere carry similar names). */
    private boolean tagLink() {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const l=[...pli.querySelectorAll('a[href]')].filter(vis).find(x=>norm(x.textContent)===a[1]); if(!l) return false; l.id='__clLink'; return true; }", Arrays.asList(TOP, MENU));
        return Boolean.TRUE.equals(r);
    }

    private boolean linkVisible() {
        boolean t = tagLink();
        page.evaluate("() => { const e=document.getElementById('__clLink'); if(e) e.removeAttribute('id'); }");
        return t;
    }

    private boolean waitFor(java.util.function.BooleanSupplier cond, int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (cond.getAsBoolean()) return true; page.waitForTimeout(250); }
        return cond.getAsBoolean();
    }

    /**
     * OT &gt; Cath Lab &gt; Cath Lab Details. The menu builds each level lazily and a second click on a group that is still building closes it again, so the OT entry and the Cath Lab group are each opened
     * ONCE and awaited.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='OT')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!linkVisible()) {
                if (topState() != 1) clickTop();
                waitFor(() -> groupState() >= 0, 15000);
                if (groupState() == 0) clickGroup();
                waitFor(this::linkVisible, 15000);
            }
            if (tagLink()) {
                try { page.locator("#__clLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("CathLabDetails.nav: click failed - " + e.getMessage().split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__clLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        waitFor(this::newButtonVisible, 15000);
        waitForAngular(1000);
        return onList();
    }

    private boolean newButtonVisible() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('button')].some(b=>vis(b) && /^OTDetails\\(\\)/.test(b.getAttribute('ng-click')||'')); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/cathlabdetailslist/?$") && newButtonVisible(); }
    public boolean onForm() { return page.url().toLowerCase().contains("add-cathlabdetails"); }

    /** Click <b>New</b> on the list and wait for the form (its OT Sheet selects loaded). */
    public boolean clickNew() {
        Object tag = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^OTDetails\\(\\)/.test(x.getAttribute('ng-click')||'') && /^\\s*new\\s*$/i.test(norm(x.textContent)));"
                + " if(!b) return ''; b.id='__clNew'; return 'ok'; }");
        if (tag == null || tag.toString().isEmpty()) { System.out.println("CathLabDetails: no visible New button"); return false; }
        try { page.locator("#__clNew").scrollIntoViewIfNeeded(); page.locator("#__clNew").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("CathLabDetails: New click failed - " + e.getMessage().split("\n")[0]); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__clNew'); if(e) e.removeAttribute('id'); }"); }
        try {
            page.waitForFunction("() => /add-cathlabdetails/i.test(location.hash) && document.querySelector(\"select[ng-model='otsheetdetails.ottheatreid']\") && document.querySelector(\"select[ng-model='otsheetdetails.ottheatreid']\").options.length>1",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { }
        waitForAngular(1500);
        return onForm();
    }

    // =====================================================================================================================
    // Patient (the block above the tabs)
    // =====================================================================================================================

    public String lastPatient = "";

    private String patientModel(String expr) {
        Object v = page.evaluate("(x) => { const e=document.querySelector(\"input[ng-model='otdetails.MRNo']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(x); return v==null?'':String(v); }catch(x){ return ''; } }", expr);
        return v == null ? "" : v.toString().trim();
    }

    /** True when the form holds the patient with this MRN (an id, an MRN and a visit/admission). */
    public boolean patientLoaded(String mrn) {
        return mrn.equals(patientModel("otdetails.MRNo")) && Double.parseDouble("0" + patientModel("otdetails.ID")) > 0
                && Double.parseDouble("0" + patientModel("otdetails.visitadmissionid")) > 0;
    }

    /** The patient shown on the form, on one line (name, MRN, gender, age). */
    public String patientSummary() {
        return patientModel("otdetails.PatientFullDtl").replaceAll("\\s+", " ").trim();
    }

    /** Enter the MRN, click the search button ({@code SearchPatientByMRNo2()}) and wait until the form holds THAT patient (a stale earlier patient must not be taken for it). */
    public boolean searchPatient(String mrn) {
        problems.clear();
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
        try {
            Locator in = page.locator("input[ng-model='otdetails.MRNo']").first();
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(6000));
            in.fill(mrn);
        } catch (Exception e) { problems.add("the MRN could not be typed: " + e.getMessage().split("\n")[0]); return false; }
        Object tag = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^SearchPatientByMRNo2/.test(x.getAttribute('ng-click')||'')); if(!b) return ''; b.id='__clSearch'; return 'ok'; }");
        if (tag == null || tag.toString().isEmpty()) { problems.add("the MRN search button is not on screen"); return false; }
        try { page.locator("#__clSearch").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the search button could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__clSearch'); if(e) e.removeAttribute('id'); }"); }
        try {
            page.waitForFunction("(m) => { const e=document.querySelector(\"input[ng-model='otdetails.MRNo']\"); if(!e) return false;"
                    + " try{ const o=angular.element(e).scope().otdetails; return !!o && String(o.MRNo).trim()===m && Number(o.ID)>0 && Number(o.visitadmissionid)>0 && String(o.PatientFullDtl||'').indexOf(m)>=0; }catch(x){ return false; } }",
                    mrn, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { problems.add("the form did not load a patient with MRN " + mrn + " (no such patient, or the search did not answer)"); return false; }
        waitForAngular(1000);
        lastPatient = patientSummary();
        return true;
    }

    // =====================================================================================================================
    // Tabs
    // =====================================================================================================================

    /** The tab labels on the form, in order. */
    public List<String> tabNames() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('ul.nav-tabs a[data-toggle=tab]')].filter(vis).filter(a=>!a.closest('.tab-pane')).map(a=>norm(a.textContent)).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The tab that is open now ("" when none). */
    public String activeTab() {
        Object r = page.evaluate("() => {" + JS + " const a=[...document.querySelectorAll('ul.nav-tabs li.active a[data-toggle=tab]')].filter(vis)[0]; return a? norm(a.textContent) : ''; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Click the tab named {@code name} (a real click) and confirm it opened: its list item is active and the pane it targets is shown.
     *
     * @return true when the tab is open
     */
    public boolean clickTab(String name) {
        problems.clear();
        Object tag = page.evaluate("(n) => {" + JS + " const a=[...document.querySelectorAll('ul.nav-tabs a[data-toggle=tab]')].filter(vis).find(x=>norm(x.textContent)===n); if(!a) return '';"
                + " a.id='__clTab'; return a.getAttribute('data-target')||a.getAttribute('href')||''; }", name);
        if (tag == null || tag.toString().isEmpty()) { problems.add("no tab named \"" + name + "\" (tabs: " + tabNames() + ")"); return false; }
        lastTabPane = tag.toString();
        try { page.locator("#__clTab").scrollIntoViewIfNeeded(); page.locator("#__clTab").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the tab could not be clicked: " + e.getMessage().split("\n")[0]); }
        finally { page.evaluate("() => { const e=document.getElementById('__clTab'); if(e) e.removeAttribute('id'); }"); }
        page.waitForTimeout(500);
        Object shown = page.evaluate("(a) => {" + JS + " const p=document.querySelector(a[1]); const l=[...document.querySelectorAll('ul.nav-tabs a[data-toggle=tab]')].filter(vis).find(x=>norm(x.textContent)===a[0]);"
                + " return !!p && vis(p) && p.classList.contains('active') && !!l && l.parentElement.classList.contains('active'); }", Arrays.asList(name, lastTabPane));
        if (!Boolean.TRUE.equals(shown)) { problems.add("the \"" + name + "\" tab (" + lastTabPane + ") is not open after the click"); return false; }
        return true;
    }

    // =====================================================================================================================
    // Surgery Details tab (#docSurgDtls) and its Procedures List dialog (#getprcdrDtl)
    // =====================================================================================================================
    //
    // The tab holds a "Surgery Description | Delete" table (bound to ServiceDetailsList) and the button "Get Procedure Details" (a bootstrap modal trigger). The dialog "Procedures List":
    // Procedure Type (select2, OTSchedule1.proceduretypeid, fnSearchyID), Procedure Name (text), Search (fnSearch -> SearchProcedure, ALL procedures of the type, e.g. 1,874 for Minor, in one
    // plain table: Select tick PT.isselected | Procedure Name | Group | Sub Group), Add (fnAdd, closes the dialog, then fetches ServiceDetails of each ticked procedure and pushes it into the
    // Surgery Description table; a procedure already in the table answers the JAlert "ProcedurealreadyPresent"; one with no service item adds nothing), Close.

    private static final String DLG = "#getprcdrDtl";

    public boolean dialogOpen() {
        Object r = page.evaluate("(d) => { const m=document.querySelector(d); return !!m && m.classList.contains('in') && !!(m.offsetWidth||m.offsetHeight); }", DLG);
        return Boolean.TRUE.equals(r);
    }

    public String dialogTitle() {
        Object r = page.evaluate("(d) => { const t=document.querySelector(d+' .modal-title'); return t? (t.textContent||'').replace(/\\s+/g,' ').trim() : ''; }", DLG);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Get Procedure Details</b> on the Surgery Details tab and wait for the Procedures List dialog. */
    public boolean clickGetProcedureDetails() {
        problems.clear();
        try {
            Locator b = page.locator("#docSurgDtls button[data-target='#getprcdrDtl']").first();
            b.scrollIntoViewIfNeeded();
            b.click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("the Get Procedure Details button could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        try {
            page.waitForFunction("(d) => { const m=document.querySelector(d); return !!m && m.classList.contains('in') && !!(m.offsetWidth||m.offsetHeight); }", DLG, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { problems.add("the Procedures List dialog did not open"); return false; }
        waitForAngular(800);
        return true;
    }

    /** The Procedure Types the dialog offers ("-Select-" left out). */
    public List<String> procedureTypes() { return Select2Pick.names(page, "OTSchedule1.proceduretypeid"); }

    /** Choose a Procedure Type in the dialog the way a user does; {@code want} is matched exactly (case-insensitive), else the first type. Returns the type chosen ("" when it could not be). */
    public String selectProcedureType(String want) {
        List<String> names = procedureTypes();
        String pick = null;
        for (String n : names) if (want != null && n.equalsIgnoreCase(want)) { pick = n; break; }
        if (pick == null && !names.isEmpty()) pick = names.get(0);
        if (pick == null) { problems.add("the dialog offers no Procedure Type"); return ""; }
        String err = Select2Pick.pick(page, "OTSchedule1.proceduretypeid", pick);
        if (!err.isEmpty()) { problems.add("Procedure Type: " + err); return ""; }
        return pick;
    }

    public int dialogRowCount() {
        Object r = page.evaluate("(d) => document.querySelectorAll(d+' tbody tr input[type=checkbox]').length", DLG);
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Click <b>Search</b> in the dialog and wait for the procedures; returns how many rows are listed (0 = none appeared). */
    public int clickSearch() {
        problems.clear();
        try { page.locator(DLG + " button[ng-click='fnSearch()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Search button could not be clicked: " + e.getMessage().split("\n")[0]); return 0; }
        try {
            page.waitForFunction("(d) => document.querySelectorAll(d+' tbody tr input[type=checkbox]').length>0", DLG, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { }
        waitForAngular(800);
        int n = dialogRowCount();
        if (n == 0) problems.add("Search listed no procedure");
        return n;
    }

    /** Row {@code i} of the dialog's list: {Procedure Name, Group, Sub Group}. */
    public String[] dialogRow(int i) {
        Object r = page.evaluate("(a) => { const tr=document.querySelectorAll(a[0]+' tbody tr')[a[1]]; if(!tr) return []; return [...tr.querySelectorAll('td')].slice(1).map(c=>(c.textContent||'').replace(/\\s+/g,' ').trim()); }",
                Arrays.asList(DLG, i));
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        while (out.size() < 3) out.add("");
        return out.toArray(new String[0]);
    }

    public int tickedCount() {
        Object r = page.evaluate("(d) => [...document.querySelectorAll(d+' tbody tr')].filter(tr=>{ try{ return angular.element(tr).scope().PT.isselected===true; }catch(e){ return false; } }).length", DLG);
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Tick the Select box of row {@code i}; confirmed when the screen's model marks THAT row (and only it) as selected. */
    public boolean tickProcedure(int i) {
        problems.clear();
        try {
            Locator box = page.locator(DLG + " tbody tr").nth(i).locator("input[type=checkbox]").first();
            box.scrollIntoViewIfNeeded();
            box.check(new Locator.CheckOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("row " + (i + 1) + " could not be ticked: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(300);
        Object mine = page.evaluate("(a) => { const tr=document.querySelectorAll(a[0]+' tbody tr')[a[1]]; try{ return angular.element(tr).scope().PT.isselected===true; }catch(e){ return false; } }", Arrays.asList(DLG, i));
        if (!Boolean.TRUE.equals(mine)) { problems.add("row " + (i + 1) + " is not marked selected by the screen"); return false; }
        if (tickedCount() != 1) { problems.add(tickedCount() + " rows are ticked, not exactly one"); return false; }
        return true;
    }

    /** The rows of the Surgery Description table (cell text, Delete column left out). */
    public List<String> surgeryRows() {
        Object r = page.evaluate("() => { const t=document.querySelector('#docSurgDtls table tbody'); if(!t) return []; return [...t.querySelectorAll('tr')].filter(tr=>tr.querySelector('td')).map(tr=>{ const c=tr.querySelectorAll('td'); return (c[0].textContent||'').replace(/\\s+/g,' ').trim(); }); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The warnings / toasts the screen raised since {@link #armAlerts()}. */
    public List<String> alerts() {
        Object r = page.evaluate("() => window.__clAlerts||[]");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Start recording JAlert messages and toasts (they are short-lived). */
    public void armAlerts() {
        page.evaluate("() => { window.__clAlerts=[]; if(!window.__clJAlertWrapped){ window.__clJAlertWrapped=true; const og=window.JAlert; if(og){ window.JAlert=function(m,t){ window.__clAlerts.push('JAlert: '+m); return og.apply(this,arguments); }; } }"
                + " if(!window.__clToastObs){ const grab=()=>{ document.querySelectorAll('.toast-message').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); const k='toast: '+t; if(t && !(window.__clAlerts||[]).includes(k)) (window.__clAlerts=window.__clAlerts||[]).push(k); }); };"
                + "   window.__clToastObs=new MutationObserver(grab); window.__clToastObs.observe(document.body,{childList:true,subtree:true}); } }");
    }

    /** Answer a warning dialog (JAlert) that is on screen with its OK button; true when one was closed. */
    public boolean dismissAlertDialog() {
        Object r = page.evaluate("() => {" + JS + " const box=[...document.querySelectorAll('.modal,.jconfirm,.ng-confirm-box,.bootbox,.sweet-alert')].filter(vis).filter(m=>m.id!=='getprcdrDtl').pop(); if(!box) return false;"
                + " const b=[...box.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).find(x=>/^\\s*(ok|close|yes)\\s*$/i.test(norm(x.textContent||x.value))); if(!b) return false; b.click(); return true; }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Click <b>Add</b> in the dialog and wait for the outcome: the dialog closes, then a NEW row appears in the Surgery Description table (success) or the screen warns / nothing happens.
     *
     * @param before the number of rows in the Surgery Description table before the click
     * @return true when the table gained a row
     */
    public boolean clickAdd(int before) {
        problems.clear();
        armAlerts();
        try { page.locator(DLG + " .modal-footer button[ng-click='fnAdd()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        try { page.waitForFunction("(d) => { const m=document.querySelector(d); return !m || !(m.classList.contains('in') && (m.offsetWidth||m.offsetHeight)); }", DLG, new Page.WaitForFunctionOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the dialog did not close after Add"); }
        long end = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < end && surgeryRows().size() <= before) {
            if (!alerts().isEmpty()) break;
            page.waitForTimeout(300);
        }
        page.waitForTimeout(400);
        boolean gained = surgeryRows().size() > before;
        if (!gained) problems.add(alerts().isEmpty() ? "no row was added to the Surgery Description table (the screen raised no warning)" : "no row was added; the screen said " + alerts());
        return gained;
    }

    // =====================================================================================================================
    // Doc/Emp Details tab (#docEmpDtls): Doctor List + Employee List, each filled from a search dialog
    // =====================================================================================================================
    //
    // Doctor List (table headed Classification Name | Doctor Name | Surgery Name | Delete, rows = doctorlist) is filled from the "Doctor Search" dialog #getDr (button Get Doctors): Location (select2,
    // doctordetails.locationid - one entry, the logged-in hospital), Department, Classification, Search searchdoctor() = GetDoctorName (ALL the location's doctors, ~270, one plain table: Select tick
    // List.isselected | Classification Name | Doctor), OK adddoctor() (data-dismiss; a doctor already in the list answers the JAlert "AlreadyExits"). Employee List (Employee Name | Surgery Name | Delete,
    // rows = EmployeeDetailslist) from the "Staff Search" dialog #getStf (button Get Staff): Designation (select2, staffdetails.designationid), Staff Name (text), Search searchstaff() = GetStaffName
    // (table Select | Staff Name), OK EmployeeDetails(). Every row of both tables carries a PLAIN select "Surgery Name" (ng-model List.procedureid) that offers "--Select--" and the procedures of the
    // Surgery Details tab - so a procedure must have been added there first.

    private static final String DOC = "#getDr", STF = "#getStf";

    private boolean modalOpen(String dlg) {
        Object r = page.evaluate("(d) => { const m=document.querySelector(d); return !!m && m.classList.contains('in') && !!(m.offsetWidth||m.offsetHeight); }", dlg);
        return Boolean.TRUE.equals(r);
    }

    private boolean openModal(String buttonSel, String dlg, String what) {
        problems.clear();
        try {
            Locator b = page.locator(buttonSel).first();
            b.scrollIntoViewIfNeeded();
            b.click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("the " + what + " button could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        try { page.waitForFunction("(d) => { const m=document.querySelector(d); return !!m && m.classList.contains('in') && !!(m.offsetWidth||m.offsetHeight); }", dlg, new Page.WaitForFunctionOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("the " + what + " dialog did not open"); return false; }
        waitForAngular(800);
        return true;
    }

    private int rowsIn(String dlg) {
        Object r = page.evaluate("(d) => document.querySelectorAll(d+' tbody tr input[type=checkbox]').length", dlg);
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    private int searchIn(String dlg, String searchHandler, String what) {
        problems.clear();
        try { page.locator(dlg + " button[ng-click='" + searchHandler + "']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the " + what + " Search button could not be clicked: " + e.getMessage().split("\n")[0]); return 0; }
        try { page.waitForFunction("(d) => document.querySelectorAll(d+' tbody tr input[type=checkbox]').length>0", dlg, new Page.WaitForFunctionOptions().setTimeout(30000)); }
        catch (Exception ignore) { }
        waitForAngular(800);
        int n = rowsIn(dlg);
        if (n == 0) problems.add("Search listed no " + what);
        return n;
    }

    /** Index of the row of the dialog list whose cell {@code cell} (0 = the tick box) reads {@code name} (trimmed, case-insensitive); -1 when there is none. */
    private int rowIndexIn(String dlg, int cell, String name) {
        Object r = page.evaluate("(a) => { const n=a[2].trim().toLowerCase(); return [...document.querySelectorAll(a[0]+' tbody tr')].filter(r=>r.querySelector('td')).findIndex(r=>((r.querySelectorAll('td')[a[1]]||{}).textContent||'').replace(/\\s+/g,' ').trim().toLowerCase()===n); }",
                Arrays.asList(dlg, cell, name));
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private String[] rowCellsIn(String dlg, int i) {
        Object r = page.evaluate("(a) => { const tr=[...document.querySelectorAll(a[0]+' tbody tr')].filter(r=>r.querySelector('td'))[a[1]]; if(!tr) return []; return [...tr.querySelectorAll('td')].slice(1).map(c=>(c.textContent||'').replace(/\\s+/g,' ').trim()); }", Arrays.asList(dlg, i));
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        while (out.size() < 2) out.add("");
        return out.toArray(new String[0]);
    }

    /** Tick row {@code i} of a dialog list (all three lists mark it as {@code <row var>.isselected}); confirmed when the screen marks that row and only it. */
    private boolean tickRowIn(String dlg, int i) {
        problems.clear();
        try {
            Locator box = page.locator(dlg + " tbody tr").nth(i).locator("input[type=checkbox]").first();
            box.scrollIntoViewIfNeeded();
            box.check(new Locator.CheckOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("row " + (i + 1) + " could not be ticked: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(300);
        Object mine = page.evaluate("(a) => { const tr=document.querySelectorAll(a[0]+' tbody tr')[a[1]]; try{ const s=angular.element(tr).scope(); const o=s.List||s.PT; return !!o && o.isselected===true; }catch(e){ return false; } }", Arrays.asList(dlg, i));
        if (!Boolean.TRUE.equals(mine)) { problems.add("row " + (i + 1) + " is not marked selected by the screen"); return false; }
        Object n = page.evaluate("(d) => [...document.querySelectorAll(d+' tbody tr')].filter(tr=>{ try{ const s=angular.element(tr).scope(); const o=s.List||s.PT; return !!o && o.isselected===true; }catch(e){ return false; } }).length", dlg);
        if (!(n instanceof Number) || ((Number) n).intValue() != 1) { problems.add(n + " rows are ticked, not exactly one"); return false; }
        return true;
    }

    /** Click OK of a dialog; true when the dialog closed. */
    private boolean clickOkIn(String dlg, String handler, String what) {
        problems.clear();
        armAlerts();
        try { page.locator(dlg + " .modal-footer button[ng-click='" + handler + "']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the " + what + " OK button could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        try { page.waitForFunction("(d) => { const m=document.querySelector(d); return !m || !(m.classList.contains('in') && (m.offsetWidth||m.offsetHeight)); }", dlg, new Page.WaitForFunctionOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the " + what + " dialog did not close after OK"); return false; }
        waitForAngular(600);
        return true;
    }

    // ---- doctors ----

    public boolean clickGetDoctors() { return openModal("#docEmpDtls button[data-target='#getDr']", DOC, "Get Doctors"); }
    public boolean doctorDialogOpen() { return modalOpen(DOC); }
    public List<String> doctorLocations() { return Select2Pick.names(page, "doctordetails.locationid"); }

    /** Pick the Location in the Doctor Search dialog; returns the location chosen ("" when it could not be). */
    public String selectDoctorLocation(String want) {
        List<String> names = doctorLocations();
        String pick = null;
        for (String n : names) if (want != null && n.equalsIgnoreCase(want)) { pick = n; break; }
        if (pick == null && !names.isEmpty()) pick = names.get(0);
        if (pick == null) { problems.add("the dialog offers no Location"); return ""; }
        String err = Select2Pick.pick(page, "doctordetails.locationid", pick);
        if (!err.isEmpty()) { problems.add("Location: " + err); return ""; }
        return pick;
    }

    public int searchDoctors() { return searchIn(DOC, "searchdoctor()", "doctor"); }
    public int doctorRowIndex(String name) { return rowIndexIn(DOC, 2, name); }
    /** {Classification Name, Doctor} of row {@code i} of the Doctor Search list. */
    public String[] doctorRow(int i) { return rowCellsIn(DOC, i); }
    public boolean tickDoctor(int i) { return tickRowIn(DOC, i); }
    public boolean clickDoctorOk() { return clickOkIn(DOC, "adddoctor()", "Doctor Search"); }

    // ---- staff ----

    public boolean clickGetStaff() { return openModal("#docEmpDtls button[data-target='#getStf']", STF, "Get Staff"); }
    public List<String> staffDesignations() { return Select2Pick.names(page, "staffdetails.designationid"); }

    /** Pick the Designation in the Staff Search dialog; returns the designation chosen ("" when it could not be). */
    public String selectDesignation(String want) {
        List<String> names = staffDesignations();
        String pick = null;
        for (String n : names) if (want != null && n.equalsIgnoreCase(want)) { pick = n; break; }
        if (pick == null && !names.isEmpty()) pick = names.get(0);
        if (pick == null) { problems.add("the dialog offers no Designation"); return ""; }
        String err = Select2Pick.pick(page, "staffdetails.designationid", pick);
        if (!err.isEmpty()) { problems.add("Designation: " + err); return ""; }
        return pick;
    }

    public int searchStaff() { return searchIn(STF, "searchstaff()", "staff"); }
    public int staffRowIndex(String name) { return rowIndexIn(STF, 1, name); }
    /** {Staff Name} of row {@code i} of the Staff Search list. */
    public String[] staffRow(int i) { return rowCellsIn(STF, i); }
    public boolean tickStaff(int i) { return tickRowIn(STF, i); }
    public boolean clickStaffOk() { return clickOkIn(STF, "EmployeeDetails()", "Staff Search"); }

    // ---- the two tables of the tab ----

    /** Rows of the table headed {@code heading} ("Doctor Name" = Doctor List, "Employee Name" = Employee List) as "cell | cell | Surgery Name shown". */
    public List<String> tableRows(String heading) {
        Object r = page.evaluate("(h) => {" + JS + " const t=[...document.querySelectorAll('#docEmpDtls table')].find(t=>vis(t) && [...t.querySelectorAll('th')].some(x=>norm(x.textContent)===h)); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].filter(tr=>tr.querySelector('td')).map(tr=>[...tr.querySelectorAll('td')].filter(c=>!c.querySelector('span.btn')).map(c=>{ const s=c.querySelector('select'); return s? ((s.options[s.selectedIndex]||{}).textContent||'').replace(/\\s+/g,' ').trim() : (c.textContent||'').replace(/\\s+/g,' ').trim(); }).join(' | ')); }", heading);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The entries of the Surgery Name select of row {@code row} of the table headed {@code heading} ("--Select--" left out). */
    public List<String> surgeryNameOptions(String heading, int row) {
        Object r = page.evaluate("(a) => {" + JS + " const t=[...document.querySelectorAll('#docEmpDtls table')].find(t=>vis(t) && [...t.querySelectorAll('th')].some(x=>norm(x.textContent)===a[0])); if(!t) return [];"
                + " const s=[...t.querySelectorAll('tbody tr')].filter(tr=>tr.querySelector('td'))[a[1]]; const e=s&&s.querySelector('select'); if(!e) return []; return [...e.options].map(o=>norm(o.textContent)).filter(x=>x && !/^-*\\s*select\\s*-*$/i.test(x)); }",
                Arrays.asList(heading, row));
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /**
     * Select the Surgery Name in a row of the Doctor List ({@code heading} "Doctor Name") or Employee List ("Employee Name"): {@code want} (case-insensitive), else the first entry. The select is a plain
     * select; the choice is confirmed from the row's model ({@code List.procedureid}). Returns the surgery chosen ("" when it could not be).
     */
    public String selectSurgeryName(String heading, int row, String want) {
        problems.clear();
        List<String> opts = surgeryNameOptions(heading, row);
        String pick = null;
        for (String n : opts) if (want != null && n.equalsIgnoreCase(want)) { pick = n; break; }
        if (pick == null && !opts.isEmpty()) pick = opts.get(0);
        if (pick == null) { problems.add("the Surgery Name select of that row offers no surgery (add a procedure on the Surgery Details tab first)"); return ""; }
        Object tag = page.evaluate("(a) => {" + JS + " const t=[...document.querySelectorAll('#docEmpDtls table')].find(t=>vis(t) && [...t.querySelectorAll('th')].some(x=>norm(x.textContent)===a[0])); if(!t) return false;"
                + " const tr=[...t.querySelectorAll('tbody tr')].filter(tr=>tr.querySelector('td'))[a[1]]; const e=tr&&tr.querySelector('select'); if(!e) return false; e.id='__clSurg'; return true; }", Arrays.asList(heading, row));
        if (!Boolean.TRUE.equals(tag)) { problems.add("no Surgery Name select in row " + (row + 1)); return ""; }
        try {
            Locator s = page.locator("#__clSurg");
            s.scrollIntoViewIfNeeded();
            s.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(pick));
        } catch (Exception e) { problems.add("the Surgery Name could not be selected: " + e.getMessage().split("\n")[0]); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__clSurg'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(500);
        List<String> rows = tableRows(heading);
        String shown = rows.size() > row ? rows.get(row) : "";
        Object model = page.evaluate("(a) => {" + JS + " const t=[...document.querySelectorAll('#docEmpDtls table')].find(t=>vis(t) && [...t.querySelectorAll('th')].some(x=>norm(x.textContent)===a[0])); if(!t) return '';"
                + " const tr=[...t.querySelectorAll('tbody tr')].filter(tr=>tr.querySelector('td'))[a[1]]; try{ const v=angular.element(tr).scope().List.procedureid; return v==null?'':String(v); }catch(e){ return ''; } }", Arrays.asList(heading, row));
        String m = model == null ? "" : model.toString();
        if (m.isEmpty() || "0".equals(m)) { problems.add("the screen's model holds no surgery for that row"); return ""; }
        if (!shown.toLowerCase().contains(pick.toLowerCase())) { problems.add("the row shows \"" + shown + "\", not \"" + pick + "\""); return ""; }
        return pick;
    }

    // =====================================================================================================================
    // OT Notes tab (#OTnote): four sub tabs - Surgery Notes, Anesthesia Notes, Perfusionist Notes, Nursing Notes
    // =====================================================================================================================
    //
    // A second row of bootstrap tabs INSIDE the pane (ul.nav-tabs in #OTnote: #srgryNote, #anesNote, #PerfnistNotes, #NursingNotes; Surgery Notes is open by default).
    // Surgery Notes and Anesthesia Notes each hold three columns - Pre / Intra / Post - of: a modal button ("Pre Surgery Notes" ... , data-target = its dialog), the dialog ("Surgery Notes Search" /
    // "Anesthesia Notes Search": Remark text, Search SurgeryNotesSearch(n) / AnesthesiaNotesSearch(n) = GetSurgeryNotes / GetAnesthesiaNotes with ispreintrapost n, table Select | Notes, the list is EMPTY until
    // Search; the ticks are single-select; OK data-dismiss copies the ticked template's text into the column's text area) and that text area (SurgeryNotes.notedescription / .Intranotedescription /
    // .Postnotedescription, SurgeryNotes1.* for anaesthesia). Perfusionist Notes = three plain text areas (Pre / Intra / Post-Perfusionist Notes), Nursing Notes = one.

    private String noteDlg = "";

    /** The tabs of the OT Notes row, in order. */
    public List<String> subTabNames() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#OTnote ul.nav-tabs a[data-toggle=tab]')].filter(vis).map(a=>norm(a.textContent)).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The sub tab that is open now ("" when none). */
    public String activeSubTab() {
        Object r = page.evaluate("() => {" + JS + " const a=[...document.querySelectorAll('#OTnote ul.nav-tabs li.active a[data-toggle=tab]')].filter(vis)[0]; return a? norm(a.textContent) : ''; }");
        return r == null ? "" : r.toString();
    }

    private static final String NOTE_BTN = "(n) => {" + JS + " const b=[...document.querySelectorAll('#OTnote button[data-toggle=modal]')].filter(vis).find(x=>norm(x.textContent)===n); if(!b) return ''; b.id='__clNote'; return b.getAttribute('data-target')||''; }";

    /** Click a note button ("Pre Surgery Notes", "Intra Anesthesia Notes" ...) and wait for its dialog; true when it opened. */
    public boolean clickNoteButton(String name) {
        problems.clear();
        Object d = page.evaluate(NOTE_BTN, name);
        noteDlg = d == null ? "" : d.toString();
        if (noteDlg.isEmpty()) { problems.add("no visible \"" + name + "\" button (is its sub tab open?)"); return false; }
        try { page.locator("#__clNote").scrollIntoViewIfNeeded(); page.locator("#__clNote").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the \"" + name + "\" button could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__clNote'); if(e) e.removeAttribute('id'); }"); }
        try { page.waitForFunction("(d) => { const m=document.querySelector(d); return !!m && m.classList.contains('in') && !!(m.offsetWidth||m.offsetHeight); }", noteDlg, new Page.WaitForFunctionOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("the dialog of \"" + name + "\" did not open"); return false; }
        waitForAngular(800);
        return true;
    }

    public String noteDialogTitle() {
        Object r = page.evaluate("(d) => { const t=document.querySelector(d+' .modal-title'); return t? (t.textContent||'').replace(/\\s+/g,' ').trim() : ''; }", noteDlg);
        return r == null ? "" : r.toString();
    }

    public int noteRowCount() { return rowsIn(noteDlg); }

    /** Click <b>Search</b> in the open note dialog and wait for the templates; returns how many are listed. */
    public int clickNoteSearch() {
        problems.clear();
        try { page.locator(noteDlg + " button[ng-click*='NotesSearch(']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Search button could not be clicked: " + e.getMessage().split("\n")[0]); return 0; }
        try { page.waitForFunction("(d) => document.querySelectorAll(d+' tbody tr input[type=checkbox]').length>0", noteDlg, new Page.WaitForFunctionOptions().setTimeout(20000)); }
        catch (Exception ignore) { }
        waitForAngular(600);
        int n = noteRowCount();
        if (n == 0) problems.add("Search listed no note");
        return n;
    }

    /** The template name of row {@code i} of the open note dialog. */
    public String noteRowName(int i) { return rowCellsIn(noteDlg, i)[0]; }

    public boolean tickNote(int i) { return tickRowIn(noteDlg, i); }

    /** Click <b>OK</b> in the open note dialog; true when it closed. */
    public boolean clickNoteOk() {
        problems.clear();
        try { page.locator(noteDlg + " .modal-footer button[data-dismiss='modal']").filter(new Locator.FilterOptions().setHasText("OK")).first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the OK button could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        try { page.waitForFunction("(d) => { const m=document.querySelector(d); return !m || !(m.classList.contains('in') && (m.offsetWidth||m.offsetHeight)); }", noteDlg, new Page.WaitForFunctionOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the dialog did not close after OK"); return false; }
        waitForAngular(600);
        return true;
    }

    /** Close the open note dialog with its X without choosing anything (used after a failed step so a dialog is not left open). */
    public void closeNoteDialog() {
        page.evaluate("(d) => { const m=document.querySelector(d); const x=m&&m.querySelector('.modal-header button.close'); if(x) x.click(); }", noteDlg);
        page.waitForTimeout(800);
    }

    /** What the text area of the column of the note button {@code name} holds now. */
    public String noteTextOf(String name) {
        Object r = page.evaluate("(n) => {" + JS + " const b=[...document.querySelectorAll('#OTnote button[data-toggle=modal]')].find(x=>norm(x.textContent)===n); if(!b) return ''; const t=b.parentElement.querySelector(':scope > textarea'); return t? t.value : ''; }", name);
        return r == null ? "" : r.toString();
    }

    /** Type {@code text} into the text area bound to {@code ngModel} and confirm both the box and the screen's model hold exactly that. */
    public boolean typeNote(String ngModel, String text) {
        String css = "textarea[ng-model='" + ngModel + "']";
        try {
            Locator t = page.locator(css).first();
            t.scrollIntoViewIfNeeded();
            t.click(new Locator.ClickOptions().setTimeout(6000));
            t.fill(text);
            t.press("Tab");
        } catch (Exception e) { problems.add(ngModel + ": could not be typed into (" + e.getMessage().split("\n")[0] + ")"); return false; }
        waitForAngular(300);
        Object box = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.value : ''; }", css);
        Object model = page.evaluate("(a) => { const e=document.querySelector(a[0]); if(!e) return ''; try{ const v=angular.element(e).scope().$eval(a[1]); return v==null?'':String(v); }catch(x){ return ''; } }", Arrays.asList(css, ngModel));
        boolean held = text.equals(String.valueOf(box)) && text.equals(String.valueOf(model));
        if (!held) problems.add(ngModel + ": typed \"" + text + "\" but the box holds \"" + box + "\" and the model \"" + model + "\"");
        return held;
    }

    // =====================================================================================================================
    // Post-Operative Instructions tab (#postInstrctDtls)
    // =====================================================================================================================
    //
    // One select2 "Post-Operative Instructions" (PostInstruction.postinstructionid, ~63 entries: 36 real instructions - 'Avoid Spicy Food', 'Complete Bed Rest for 24 Hours' ... - and 'Auto post operative
    // instruction nnnnn' leftovers), the button Add (AddPostInstruction(): pushes the chosen instruction into PostInstructionList; the same one twice = JAlert "AlreadyExits"; nothing chosen = JAlert
    // "PleaseSelectPostInstruction"; the select keeps its choice) and an "Instruction | Delete" table (rows = PostInstructionList).

    private static final String PO = "#postInstrctDtls";

    public List<String> postInstructionOptions() { return Select2Pick.names(page, "PostInstruction.postinstructionid"); }
    public String postInstructionShown() { return Select2Pick.chosen(page, "PostInstruction.postinstructionid"); }

    /** Choose an entry of the Post-Operative Instructions select the way a user does ({@code want} exact, case-insensitive, else the first entry); returns it ("" when it could not be). */
    public String selectPostInstruction(String want) {
        problems.clear();
        List<String> names = postInstructionOptions();
        String pick = null;
        for (String n : names) if (want != null && n.equalsIgnoreCase(want)) { pick = n; break; }
        if (pick == null && !names.isEmpty()) pick = names.get(0);
        if (pick == null) { problems.add("the Post-Operative Instructions select offers no entry"); return ""; }
        page.evaluate("(s) => { const e=document.querySelector(s); if(e) e.scrollIntoView({block:'center'}); }", PO + " select[ng-model='PostInstruction.postinstructionid']");
        String err = Select2Pick.pick(page, "PostInstruction.postinstructionid", pick);
        if (!err.isEmpty()) { problems.add("Post-Operative Instructions: " + err); return ""; }
        return pick;
    }

    /** The rows of the Instruction table. */
    public List<String> postInstructionRows() {
        Object r = page.evaluate("(p) => { const t=document.querySelector(p+' table tbody'); if(!t) return []; return [...t.querySelectorAll('tr')].filter(tr=>tr.querySelector('td')).map(tr=>(tr.querySelector('td').textContent||'').replace(/\\s+/g,' ').trim()); }", PO);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /**
     * Click <b>Add</b> and wait for the outcome: the Instruction table gains a row (success) or the screen warns.
     *
     * @param before the number of rows before the click
     * @return true when the table gained a row
     */
    public boolean clickAddPostInstruction(int before) {
        problems.clear();
        armAlerts();
        try { page.locator(PO + " button[ng-click='AddPostInstruction()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end && postInstructionRows().size() <= before) {
            if (!alerts().isEmpty()) break;
            page.waitForTimeout(250);
        }
        page.waitForTimeout(300);
        boolean gained = postInstructionRows().size() > before;
        if (!gained) problems.add(alerts().isEmpty() ? "no row was added to the Instruction table (the screen raised no warning)" : "no row was added; the screen said " + alerts());
        return gained;
    }

    // =====================================================================================================================
    // Preoperative check List tab (#Preoperative)
    // =====================================================================================================================
    //
    // A table "Select | Id | Criteria | Admitting Nurse | OT Nurse" (rows = Preoperative; today one row, "1 | test"): a tick box (pre.isselected), the Admitting Nurse radios (pre.addbynurse: 1 Yes, 2 No,
    // 3 NA - the three share the id radio-1 and their labels point at other ids, so only the input itself can be clicked) and the OT Nurse radios (pre.OTNurse). Below the table: the "Admitting Nurse" select2
    // (otsheetdetails.admittingnurseid, 43 entries incl. two blank ones) with the "Admitting Nurse Sign" pad (#wPaint, a wPaint signature canvas: .wPaint-canvas-bg, .wPaint-canvas, .wPaint-canvas-temp, drawn
    // with the mouse) and its Clear button, then the same for the OT Nurse (otsheetdetails.otnurseid, #wPaint1). Save reads the pad as a JPEG.

    private static final String PRE = "#Preoperative";

    /** The checklist rows as "Id | Criteria". */
    public List<String> checklistRows() {
        Object r = page.evaluate("(p) => [...document.querySelectorAll(p+' table tbody tr')].filter(tr=>tr.querySelector('td')).map(tr=>[...tr.querySelectorAll('td')].slice(1,3).map(c=>(c.textContent||'').replace(/\\s+/g,' ').trim()).join(' | '))", PRE);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    private String checklistModel(int row, String field) {
        Object r = page.evaluate("(a) => { const tr=[...document.querySelectorAll(a[0]+' table tbody tr')].filter(tr=>tr.querySelector('td'))[a[1]]; if(!tr) return ''; try{ const v=angular.element(tr).scope().pre[a[2]]; return v==null?'':String(v); }catch(e){ return ''; } }",
                Arrays.asList(PRE, row, field));
        return r == null ? "" : r.toString();
    }

    /** Tick the Select box of checklist row {@code row}; confirmed from the row's model. */
    public boolean tickChecklistRow(int row) {
        problems.clear();
        try {
            Locator box = page.locator(PRE + " table tbody tr").nth(row).locator("input[ng-model='pre.isselected']").first();
            box.scrollIntoViewIfNeeded();
            box.check(new Locator.CheckOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("checklist row " + (row + 1) + " could not be ticked: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(300);
        if (!"true".equals(checklistModel(row, "isselected"))) { problems.add("the screen does not mark checklist row " + (row + 1) + " as selected"); return false; }
        return true;
    }

    /** Tick the Admitting Nurse radio of a row: {@code answer} Yes / No / NA (values 1 / 2 / 3); confirmed from the row's model. */
    public boolean tickAdmittingNurseRadio(int row, String answer) {
        problems.clear();
        String value = "yes".equalsIgnoreCase(answer) ? "1" : "no".equalsIgnoreCase(answer) ? "2" : "3";
        try {
            Locator r = page.locator(PRE + " table tbody tr").nth(row).locator("input[ng-model='pre.addbynurse'][value='" + value + "']").first();
            r.scrollIntoViewIfNeeded();
            r.check(new Locator.CheckOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("the Admitting Nurse \"" + answer + "\" radio could not be ticked: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(300);
        String m = checklistModel(row, "addbynurse");
        if (!value.equals(m)) { problems.add("the screen's model holds \"" + m + "\" for the Admitting Nurse radio, not \"" + value + "\""); return false; }
        return true;
    }

    public List<String> admittingNurseOptions() { return Select2Pick.names(page, "otsheetdetails.admittingnurseid"); }
    public String admittingNurseShown() { return Select2Pick.chosen(page, "otsheetdetails.admittingnurseid"); }

    /** Choose the Admitting Nurse the way a user does ({@code want} exact, case-insensitive, else the first named entry); returns the nurse ("" when it could not be). */
    public String selectAdmittingNurse(String want) {
        problems.clear();
        List<String> names = admittingNurseOptions();
        String pick = null;
        for (String n : names) if (want != null && n.equalsIgnoreCase(want)) { pick = n; break; }
        if (pick == null && !names.isEmpty()) pick = names.get(0);
        if (pick == null) { problems.add("the Admitting Nurse select offers no nurse"); return ""; }
        page.evaluate("(s) => { const e=document.querySelector(s); if(e) e.scrollIntoView({block:'center'}); }", "select[ng-model='otsheetdetails.admittingnurseid']");
        String err = Select2Pick.pick(page, "otsheetdetails.admittingnurseid", pick);
        if (!err.isEmpty()) { problems.add("Admitting Nurse: " + err); return ""; }
        return pick;
    }

    /** How many pixels of the signature pad {@code padSel} (e.g. "#wPaint") are not blank, over all its drawing canvases. */
    public int padInk(String padSel) {
        Object r = page.evaluate("(p) => { let n=0; for(const c of document.querySelectorAll(p+' canvas.wPaint-canvas')){ try{ const d=c.getContext('2d').getImageData(0,0,c.width,c.height).data; for(let i=3;i<d.length;i+=4) if(d[i]>0) n++; }catch(e){} } return n; }", padSel);
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Draw a signature (a loop-and-wave stroke) on the pad {@code padSel} with the mouse; true when the pad then holds ink it did not hold before. */
    public boolean signPad(String padSel) {
        problems.clear();
        page.evaluate("(p) => { const e=document.querySelector(p); if(e) e.scrollIntoView({block:'center'}); }", padSel);
        page.waitForTimeout(400);
        Object rect = page.evaluate("(p) => { const cs=[...document.querySelectorAll(p+' canvas.wPaint-canvas')].filter(c=>c.offsetWidth); const c=cs[cs.length-1]; if(!c) return null; const r=c.getBoundingClientRect(); return [r.x,r.y,r.width,r.height]; }", padSel);
        if (!(rect instanceof List) || ((List<?>) rect).size() < 4) { problems.add("the signature pad " + padSel + " is not on screen"); return false; }
        List<?> rc = (List<?>) rect;
        double x = ((Number) rc.get(0)).doubleValue(), y = ((Number) rc.get(1)).doubleValue(), w = ((Number) rc.get(2)).doubleValue(), h = ((Number) rc.get(3)).doubleValue();
        int before = padInk(padSel);
        page.mouse().move(x + w * 0.10, y + h * 0.55);
        page.mouse().down();
        int steps = 40;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            page.mouse().move(x + w * (0.10 + 0.80 * t), y + h * (0.50 + 0.32 * Math.sin(t * 6 * Math.PI)));
        }
        page.mouse().up();
        page.waitForTimeout(500);
        int after = padInk(padSel);
        if (after <= before) { problems.add("the pad holds no new ink after drawing (" + before + " -> " + after + " non-blank pixels)"); return false; }
        return true;
    }

    // =====================================================================================================================
    // Sign In - Time Out - Sign Out tab (#SurgicalchkLst2)
    // =====================================================================================================================
    //
    // A horizontal board of safety-checklist columns, one <section class="col-sm-4 safetychecklist"> per procedure category headed by an h4.chklistHeading: "SIGN IN" (13 groups), "TIME OUT" (5), then the
    // "Auto procedure category nnnnnnnnnnnn" leftovers (4, 3, 8, 2, 2, 2 ... groups; the third column, "Auto procedure category 111126155149", is where the first row of the board ends). Each group is a
    // ul.chklistQues: a bold group name (e.g. "test", "Auto surgery checklist 90800") and its answers - li[ng-repeat^=SL3] = a tick box (SL3.status, Singleselected(SL3)) with the answer text
    // ("Sign-in", "Time-out", "Sign-out", "Auto answer nnnnn") and a "Remarks" textarea (SL3.remark) that appears once the box is ticked. Below the board: the Surgeon's / Anesthetist's / Scrub Nurse's /
    // Circulating Nurse's sign pads (#wPaint2 .. #wPaint5).

    private static final String SC = "#SurgicalchkLst2";

    private static final String SECTION = "(a) => {" + JS + " const s=[...document.querySelectorAll('#SurgicalchkLst2 section.safetychecklist')].find(x=>{ const h=x.querySelector('h4.chklistHeading'); return h && norm(h.textContent)===a[0]; }); if(!s) return null;";

    /** The column headings of the board, in order. */
    public List<String> checklistSections() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#SurgicalchkLst2 section.safetychecklist h4.chklistHeading')].map(h=>norm(h.textContent)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** How many answer rows (tick boxes) the column {@code heading} holds. */
    public int checkItemCount(String heading) {
        Object r = page.evaluate(SECTION + " return s.querySelectorAll('li[ng-repeat^=\"SL3\"]').length; }", Arrays.asList(heading));
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** "Group name: answer" of answer row {@code idx} of the column {@code heading}. */
    public String checkItemLabel(String heading, int idx) {
        Object r = page.evaluate(SECTION + " const li=s.querySelectorAll('li[ng-repeat^=\"SL3\"]')[a[1]]; if(!li) return ''; const g=li.closest('ul.chklistQues'); const n=g&&g.querySelector('strong'); const own=norm([...li.childNodes].filter(c=>c.nodeType===3).map(c=>c.textContent).join(' '));"
                + " return (n? norm(n.textContent) : '')+': '+own; }", Arrays.asList(heading, idx));
        return r == null ? "" : r.toString();
    }

    private String checkItemModel(String heading, int idx, String field) {
        Object r = page.evaluate(SECTION + " const li=s.querySelectorAll('li[ng-repeat^=\"SL3\"]')[a[1]]; if(!li) return ''; try{ const v=angular.element(li).scope().SL3[a[2]]; return v==null?'':String(v); }catch(e){ return ''; } }",
                Arrays.asList(heading, idx, field));
        return r == null ? "" : r.toString();
    }

    /** Tag answer row {@code idx} of the column {@code heading} so Playwright can address it; false when it is not there. */
    private boolean tagCheckItem(String heading, int idx) {
        Object r = page.evaluate(SECTION + " const li=s.querySelectorAll('li[ng-repeat^=\"SL3\"]')[a[1]]; if(!li) return false; li.id='__clItem'; return true; }", Arrays.asList(heading, idx));
        return Boolean.TRUE.equals(r);
    }

    private void untagCheckItem() { page.evaluate("() => { const e=document.getElementById('__clItem'); if(e) e.removeAttribute('id'); }"); }

    /** Tick the box of answer row {@code idx} in the column {@code heading}; confirmed when the screen's model holds it as ticked and the Remarks box appears. */
    public boolean tickCheckItem(String heading, int idx) {
        problems.clear();
        if (!tagCheckItem(heading, idx)) { problems.add("the column \"" + heading + "\" has no answer row " + (idx + 1)); return false; }
        try {
            Locator box = page.locator("#__clItem input[type=checkbox]").first();
            box.scrollIntoViewIfNeeded();
            box.check(new Locator.CheckOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("the box could not be ticked: " + e.getMessage().split("\n")[0]); return false; }
        finally { untagCheckItem(); }
        waitForAngular(400);
        if (!"true".equals(checkItemModel(heading, idx, "status"))) { problems.add("the screen does not hold the row as ticked"); return false; }
        Object shown = page.evaluate(SECTION + " const t=s.querySelectorAll('li[ng-repeat^=\"SL3\"]')[a[1]].querySelector('textarea'); return !!t && !!(t.offsetWidth||t.offsetHeight); }", Arrays.asList(heading, idx));
        if (!Boolean.TRUE.equals(shown)) { problems.add("the Remarks box did not appear after ticking"); return false; }
        return true;
    }

    /** Type {@code text} into the Remarks box of answer row {@code idx} (ticked first) and confirm the box and the model hold it. */
    public boolean enterCheckRemark(String heading, int idx, String text) {
        problems.clear();
        if (!tagCheckItem(heading, idx)) { problems.add("the column \"" + heading + "\" has no answer row " + (idx + 1)); return false; }
        try {
            Locator t = page.locator("#__clItem textarea").first();
            t.scrollIntoViewIfNeeded();
            t.click(new Locator.ClickOptions().setTimeout(6000));
            t.fill(text);
            t.press("Tab");
        } catch (Exception e) { problems.add("the Remarks box could not be typed into: " + e.getMessage().split("\n")[0]); return false; }
        finally { untagCheckItem(); }
        waitForAngular(300);
        String m = checkItemModel(heading, idx, "remark");
        if (!text.equals(m)) { problems.add("typed \"" + text + "\" but the screen's model holds \"" + m + "\""); return false; }
        return true;
    }

    // =====================================================================================================================
    // OT Sheet Details
    // =====================================================================================================================

    private static String sel(String field) { return "select[ng-model='otsheetdetails." + field + "']"; }
    private static String timeBox(String field) { return "timepicker-pop[input-time='otsheetdetails." + field + "'] input[ng-model='inputTime']"; }

    /** An Angular expression of the OT sheet model ("" when empty / undefined). */
    public String sheet(String field) {
        Object v = page.evaluate("(f) => { const e=document.querySelector(\"select[ng-model='otsheetdetails.otresultid']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().otsheetdetails[f]; return v==null?'':String(v); }catch(x){ return ''; } }", field);
        return v == null ? "" : v.toString().trim();
    }

    /** The time a time box holds in the screen's model, formatted like the box ("09:00 AM"); "" when empty. */
    public String timeModel(String field) {
        Object v = page.evaluate("(f) => { const e=document.querySelector(\"select[ng-model='otsheetdetails.otresultid']\"); if(!e) return '';"
                + " try{ const d=angular.element(e).scope().otsheetdetails[f]; if(!d) return ''; const x=(d instanceof Date)? d : new Date(d); if(isNaN(x.getTime())) return String(d);"
                + " let h=x.getHours(); const m=x.getMinutes(); const ap=h>=12?'PM':'AM'; h=h%12; if(h===0) h=12; return String(h).padStart(2,'0')+':'+String(m).padStart(2,'0')+' '+ap; }catch(x){ return ''; } }", field);
        return v == null ? "" : v.toString().trim();
    }

    /** What the time box shows. */
    public String timeText(String field) {
        Object v = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.value : ''; }", timeBox(field));
        return v == null ? "" : v.toString().trim();
    }

    /** Close any open time-picker popup (focusing a box opens one; it stays open and covers the box below). */
    private void closePickers() {
        for (int i = 0; i < 3; i++) {
            Object open = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('.tmpkr')].some(vis); }");
            if (!Boolean.TRUE.equals(open)) return;
            try { page.locator("#OTsheet label.control-label").filter(new Locator.FilterOptions().setHasText("Total Minutes")).first().click(new Locator.ClickOptions().setTimeout(3000)); }
            catch (Exception e) { page.keyboard().press("Escape"); }
            page.waitForTimeout(250);
        }
    }

    /**
     * Type a time ("09:00 AM") into the time box bound to {@code otsheetdetails.<field>} and confirm the screen's model holds the same time and the box shows it.
     *
     * @return true when it held
     */
    public boolean enterTime(String field, String text) {
        Locator in = page.locator(timeBox(field)).first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(6000));
            in.fill(text);
            in.press("Tab");
        } catch (Exception e) { problems.add(field + ": the time box could not be typed into (" + e.getMessage().split("\n")[0] + ")"); closePickers(); return false; }
        page.waitForTimeout(300);
        closePickers();
        waitForAngular(300);
        String m = timeModel(field), t = timeText(field);
        boolean held = text.equalsIgnoreCase(m) && text.equalsIgnoreCase(t);
        if (!held) problems.add(field + ": entered \"" + text + "\" but the model holds \"" + m + "\" and the box shows \"" + t + "\"");
        return held;
    }

    /** The entries of a select of the OT sheet ("-Select-" left out). */
    public List<String> options(String field) { return Select2Pick.names(page, "otsheetdetails." + field); }

    /** What the select's visible box shows. */
    public String shown(String field) { return Select2Pick.chosen(page, "otsheetdetails." + field); }

    /**
     * Choose an entry of a select of the OT sheet the way a user does (open the box, click the entry). {@code want} is matched exactly (case-insensitive), then by {@code fallbackRe}, then the first entry.
     *
     * @return the entry chosen ("" when it could not be: {@link #problems} says why)
     */
    public String choose(String field, String want, String fallbackRe) {
        String ng = "otsheetdetails." + field;
        try { page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }", sel(field), new Page.WaitForFunctionOptions().setTimeout(10000)); }
        catch (Exception e) { problems.add(field + " has no entries"); return ""; }
        page.evaluate("(s) => { const e=document.querySelector(s); if(e) e.scrollIntoView({block:'center'}); }", sel(field));
        page.waitForTimeout(250);
        List<String> names = options(field);
        String pick = null;
        for (String n : names) if (want != null && n.equalsIgnoreCase(want)) { pick = n; break; }
        if (pick == null && fallbackRe != null) for (String n : names) if (n.matches("(?i)" + fallbackRe)) { pick = n; break; }
        if (pick == null && !names.isEmpty()) pick = names.get(0);
        if (pick == null) { problems.add(field + " has no selectable entry"); return ""; }
        String err = Select2Pick.pick(page, ng, pick);
        if (!err.isEmpty()) { problems.add(field + ": " + err); return ""; }
        String m = sheet(field);
        if (m.isEmpty() || "0".equals(m)) { problems.add(field + " is not held by the model"); return ""; }
        return pick;
    }

    /**
     * Type {@code value} into a text box / text area of the OT sheet ({@code otsheetdetails.<field>}) and confirm the model holds exactly that.
     *
     * @return true when it held
     */
    public boolean enterText(String field, String value) {
        String css = "[ng-model='otsheetdetails." + field + "']";
        Locator in = page.locator(css).first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(6000));
            in.fill(value);
            if (!value.equals(sheet(field))) { in.fill(""); in.pressSequentially(value); }
            in.press("Tab");
        } catch (Exception e) { problems.add(field + ": could not be typed into (" + e.getMessage().split("\n")[0] + ")"); return false; }
        waitForAngular(300);
        String m = sheet(field);
        boolean held = value.equals(m);
        if (!held) problems.add(field + ": entered \"" + value + "\" but the model holds \"" + m + "\"");
        return held;
    }

    /** The disabled Total Minutes box (it stays empty when the times are typed or picked). */
    public String totalMinutes() {
        Object v = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='otsheetdetails.totalhours']\"); return e? e.value : ''; }");
        return v == null ? "" : v.toString().trim();
    }

    /** The OT Sheet's current values, one line (for the report). */
    public String sheetValues() {
        StringBuilder sb = new StringBuilder();
        for (String f : new String[]{"fromtime", "totime", "wheelintime", "wheelouttime", "anesthesiastarttime", "anesthesiaendtime", "surgerystarttime", "surgeryendtime"})
            sb.append(f).append('=').append(timeModel(f)).append("; ");
        for (String f : new String[]{"ottheatreid", "otresultid", "otstatusid", "proceduretypeid", "anesthesiatypeid"})
            sb.append(f).append('=').append(shown(f)).append("; ");
        for (String f : new String[]{"manpower", "RadiationTime", "FluoroscopyTime", "RadiationDose", "remarks"})
            sb.append(f).append('=').append(sheet(f)).append("; ");
        return sb.toString();
    }

    /** True when the OT Sheet pane is open and its main controls are on screen (for the "click OT Sheet Details" step). */
    public boolean sheetControlsVisible() {
        Object r = page.evaluate("(a) => {" + JS + " const p=document.querySelector('#OTsheet'); if(!p || !vis(p)) return false;"
                + " return a.every(s=>{ const e=p.querySelector(s); return !!e && (vis(e) || vis(e.previousElementSibling||e)); }); }",
                Arrays.asList(timeBox("fromtime"), timeBox("totime"), sel("ottheatreid"), sel("otresultid"), sel("otstatusid"), sel("proceduretypeid"), sel("anesthesiatypeid"),
                        "[ng-model='otsheetdetails.RadiationDose']", "[ng-model='otsheetdetails.remarks']"));
        return Boolean.TRUE.equals(r);
    }
}
