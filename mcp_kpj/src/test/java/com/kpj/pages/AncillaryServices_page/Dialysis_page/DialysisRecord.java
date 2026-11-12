package com.kpj.pages.AncillaryServices_page.Dialysis_page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ancillary Services &gt; Dialysis &gt; <b>Dialysis Record</b> — Page Object (list {@code #/DialysisRecord}, form {@code #/add-DialysisRecord}).
 *
 * <p>Confirmed live (devhis build v2026.11.12.441). The list: From Date / To Date ({@code DialysisRecord.FromDate|ToDate}, today by default), Diagnosis, Type, Access, <b>Search</b>
 * ({@code fngetDialysisGrid();}, {@code POST /api/DialysisRecord/Fetch {ExecFlag:'GRID', FromDate, ToDate}} in dd/MM/yyyy) and <b>Add</b> ({@code AddDialysisRecord();}); the grid {@code #grid1} is
 * Sel | MRN | Patient Name | Dialysis Date | Edit.</p>
 *
 * <p>The form has two real TABS: <b>Dialysis Record Details</b> (active when the form opens) and <b>Timewise Parameter Details</b>. The first holds: an <b>MRN</b> box ({@code DialysisRecord.PRN}) with a lookup button
 * ({@code SearchPatientByMRNo()}, which loads the patient: {@code PatientID}, {@code PatientFullDtl}) and a patient-search popup, a <b>Reg. Type</b> OPD / IPD radio, and the text boxes {@code preBP}, {@code postBP}
 * (mm/Hg), {@code dialyzer}, {@code tubbing}, {@code preweight}, {@code postweight}, {@code weightlose}, {@code weightgain} (Kg), {@code hrofdailysis}, {@code heparin}, {@code bloodflow} (ml), {@code dialysate},
 * {@code specialinstruction}, {@code ultrafiltration} (Kg), {@code tbp} (Ltrs), {@code trasfusion} (sic), {@code dialysismembrane}; the selects <b>Done By</b> ({@code donebyid}), <b>Department</b>
 * ({@code departmentid}, {@code ng-change fnSetDoctor()}) and <b>Doctor</b> ({@code doctorid}); the {@code remark} textarea; <b>Save</b> ({@code SaveDialysisRecord();}), Cancel and Back.</p>
 *
 * <p>Rules read from the screen's own code: Save has NO client-side validation. {@code fnSetDoctor()} loads the department's doctors and, when no doctor is set yet, AUTOFILLS the Doctor with the first one. Save sends
 * {@code visitadmissionId} (taken from {@code SharedPatientDtl.PatientInfo.visitadmissionid}, which the MRN lookup of this screen never fills), {@code opd_ipd} and the fields - but NO MRN and NO patient id; on
 * ResultStatus 1 it shows the "saved" toast and redirects to the list, on 2 an "updated" toast, otherwise "Failed".</p>
 *
 * <p>Saving is REAL: a dialysis record is written and stays.</p>
 */
public class DialysisRecord extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall") && !low.contains("getmaster") && !low.contains("getuser");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body,
                    r.request().postData() == null ? "" : r.request().postData()};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public DialysisRecord(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastMrn = "", lastPatient = "", lastPatientId = "", lastDoneBy = "", lastDepartment = "", lastDoctor = "", lastToast = "", lastSaveMessage = "", lastDialog = "", lastSaveRequest = "", lastPager = "";
    public boolean doctorWasAutofilled = false;
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String M = "DialysisRecord.";
    private static final String HREF = "#/dialysisrecord";
    private static final String PARENT = "Ancillary Services", GROUP = "Dialysis";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ---- navigation ------------------------------------------------------------------------------------------

    /** The link to the screen INSIDE the Ancillary Services > Dialysis group. Tags it and returns true when it is on screen. */
    private boolean tagGroupLink() {
        Object r = page.evaluate("(a) => {" + JS + " const top=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!top) return false; const tli=top.closest('li'); if(!tli) return false;"
                + " const g=[...tli.querySelectorAll('a')].find(x=>x!==top && norm(x.textContent)===a[1]); if(!g) return false; const gli=g.closest('li'); if(!gli) return false;"
                + " const l=[...gli.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===a[2]); if(!l) return false; l.id='__drLink'; return true; }", Arrays.asList(PARENT, GROUP, HREF));
        return Boolean.TRUE.equals(r);
    }

    private boolean waitGroupLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (tagGroupLink()) return true; page.waitForTimeout(250); }
        return tagGroupLink();
    }

    private int topState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickTop(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    private int innerState(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return -1; const pli=pa.closest('li'); if(!pli) return -1;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return -1; const li=c.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }",
                Arrays.asList(parent, label));
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickInner(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return false; c.click(); return true; }", Arrays.asList(parent, label));
        return Boolean.TRUE.equals(r);
    }

    /** Ancillary Services &gt; Dialysis &gt; Dialysis Record. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='" + PARENT + "')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!tagGroupLink()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !tagGroupLink()) clickInner(PARENT, GROUP);
                waitGroupLink(12000);
            }
            if (tagGroupLink()) {
                try { page.locator("#__drLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("DialysisRecord.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__drLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        waitAddButton();
        waitForAngular(1000);
        return onList();
    }

    private void waitAddButton() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button,a')].some(x=>/^AddDialysisRecord/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("DialysisRecord.nav: the Add button is not ready"); }
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/dialysisrecord/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-dialysisrecord/?$")
                && Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"input[ng-model='" + M + "PRN']\")"));
    }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__drToasts=[]; if(window.__drObs) window.__drObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__drToasts.includes(m)) window.__drToasts.push(m); });"
                + " window.__drObs=new MutationObserver(add); window.__drObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__drToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- the list --------------------------------------------------------------------------------------------------

    /** The rows the list shows as {MRN, Patient Name, Dialysis Date}. */
    public List<String[]> rows() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#grid1 .ui-grid-canvas .ui-grid-row')].filter(r=>r.textContent.trim().length>0)"
                + ".map(r=>{ const c=[...r.querySelectorAll('.ui-grid-cell')].map(x=>norm(x.textContent)); return [c[1]||'', c[2]||'', c[3]||'']; }); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2))}); }
        return out;
    }

    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click the list's Search ({@code fngetDialysisGrid();}) and wait for the server's answer. */
    public boolean clickListSearch() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^fngetDialysisGrid/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__drSearch'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the list's Search button is not on screen"); return false; }
        try {
            page.waitForResponse(x -> x.url().contains("/DialysisRecord/Fetch") && String.valueOf(x.request().postData()).contains("GRID"), new Page.WaitForResponseOptions().setTimeout(25000),
                    () -> page.locator("#__drSearch").click(new Locator.ClickOptions().setTimeout(6000)));
        } catch (Exception e) { problems.add("Search did not reach the server: " + e.getMessage().split("\n")[0]); }
        finally { page.evaluate("() => { const e=document.getElementById('__drSearch'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        lastPager = pagerText();
        return ok();
    }

    /** The records the SERVER holds between these dates (dd/MM/yyyy) as {id, MRN, patient name, dialysis date}, asked with the call the list makes; null when it could not be read. */
    public List<String[]> serverRows(String from, String to) {
        Object r = page.evaluate("(a) => new Promise(res => { try { const h=angular.element(document.body).injector().get('$http');"
                + " h.post('/api/DialysisRecord/Fetch', {ExecFlag:'GRID', FromDate:a[0], ToDate:a[1]}).then(x=>res(JSON.stringify((x.data||[]).map(d=>[d.id, d.PRN, d.PatientName, d.datetime1||d.FromDate||'']))), e=>res('ERR '+(e&&e.status))); } catch(x){ res('ERR '+x); } })",
                Arrays.asList(from, to));
        String s = r == null ? "" : r.toString();
        if (s.isEmpty() || s.startsWith("ERR")) return null;
        List<String[]> out = new ArrayList<>();
        try {
            JsonArray a = JsonParser.parseString(s).getAsJsonArray();
            for (JsonElement row : a) {
                JsonArray c = row.getAsJsonArray();
                String[] v = new String[4];
                for (int i = 0; i < 4; i++) v[i] = c.get(i).isJsonNull() ? "" : c.get(i).getAsString().trim();
                out.add(v);
            }
        } catch (Exception e) { return null; }
        return out;
    }

    public static String dmy(LocalDate d) { return d.format(DMY); }

    // ---- Add and the tabs -----------------------------------------------------------------------------------------------

    /** Click Add on the list and wait for the form. */
    public boolean clickAdd() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,a')].filter(vis).find(x=>/^AddDialysisRecord/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__drAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add button is not on screen"); return false; }
        try { page.locator("#__drAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__drAdd'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        if (!onForm()) { problems.add("the Dialysis Record form did not open (" + page.url() + ")"); return false; }
        waitForAngular(1500);
        return true;
    }

    /** The names of the form's tabs, in order, with the active one marked "*". */
    public List<String> tabs() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('li')].filter(vis).filter(li=>li.querySelector(':scope > a') && /^(Dialysis Record Details|Timewise Parameter Details)$/.test(norm(li.textContent)))"
                + ".map(li=>(li.classList.contains('active')?'*':'')+norm(li.textContent)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Click a tab by its name and check it is the active one afterwards. */
    public boolean clickTab(String name) {
        problems.clear();
        Object tagged = page.evaluate("(n) => {" + JS + " const li=[...document.querySelectorAll('li')].filter(vis).find(x=>x.querySelector(':scope > a') && norm(x.textContent)===n); if(!li) return false; const a=li.querySelector(':scope > a'); a.id='__drTab'; return true; }", name);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the tab \"" + name + "\" is not on screen"); return false; }
        try { page.locator("#__drTab").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the tab \"" + name + "\" could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__drTab'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(800);
        if (!tabs().contains("*" + name)) problems.add("the tab \"" + name + "\" is not the active one afterwards (tabs: " + tabs() + ")");
        return ok();
    }

    // ---- MRN ---------------------------------------------------------------------------------------------------------------

    private String boxValue(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e? (e.value||'') : ''; }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); try{ const m=angular.element(e).scope().$eval(n); return m==null? '' : String(m); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    public boolean enterMrn(String mrn) {
        problems.clear();
        lastMrn = mrn;
        Locator in = page.locator("input[ng-model='" + M + "PRN']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(mrn, new Locator.PressSequentiallyOptions().setDelay(50));
        } catch (Exception e) { problems.add("the MRN could not be typed"); return false; }
        waitForAngular(300);
        if (!mrn.equals(boxValue(M + "PRN"))) problems.add("the MRN box shows \"" + boxValue(M + "PRN") + "\" instead of \"" + mrn + "\"");
        else if (!mrn.equals(model(M + "PRN"))) problems.add("the MRN is not held by the model (\"" + model(M + "PRN") + "\")");
        return ok();
    }

    /** Click the lookup button beside the MRN box and wait until the patient is loaded ({@code PatientID} and {@code PatientFullDtl} set). */
    public boolean clickMrnSearch() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__drMrn'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the MRN lookup button is not on screen"); return false; }
        armToasts();
        try { page.locator("#__drMrn").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the MRN lookup button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__drMrn'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && model(M + "PatientID").isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(1500);
        lastPatientId = scopeValue(M + "PatientID");
        lastPatient = scopeValue(M + "PatientFullDtl").replaceAll("\\s+", " ").trim();
        if (lastPatientId.isEmpty()) problems.add("the lookup of MRN " + lastMrn + " loaded no patient" + (toasts().isEmpty() ? "" : " (toast: \"" + toasts() + "\")"));
        return ok();
    }

    /** A value of the screen's scope that no box is bound to (e.g. {@code DialysisRecord.PatientID}); "" when unset. */
    private String scopeValue(String expr) {
        Object v = page.evaluate("(x) => { try{ const e=document.querySelector(\"input[ng-model='" + M + "PRN']\"); const m=angular.element(e).scope().$eval(x); return m==null? '' : String(m); }catch(err){ return ''; } }", expr);
        return v == null ? "" : v.toString().trim();
    }

    /** The visit / admission id the screen will send with the record ("" when the lookup left it unset - it is read from the shared patient object, which this screen's lookup does not fill). */
    public String visitAdmissionId() { return scopeValue(M + "visitadmissionId"); }

    // ---- the text boxes -----------------------------------------------------------------------------------------------------------

    /** Type a value into the text box / textarea {@code DialysisRecord.<field>} and check the box and the model hold it. */
    public boolean enter(String field, String value, String label) {
        String ng = M + field;
        Locator in = page.locator("[ng-model='" + ng + "']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill(value, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the " + label + " could not be typed: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(250);
        String box = boxValue(ng), mod = model(ng);
        if (!value.equals(box)) { problems.add("the " + label + " box shows \"" + box + "\" instead of \"" + value + "\""); return false; }
        if (!value.equals(mod)) { problems.add("the " + label + " is not held by the model (\"" + mod + "\")"); return false; }
        return true;
    }

    /** Type several values; returns true when all of them were taken. Problems name the box that refused. */
    public boolean enterAll(String[][] fieldValueLabel) {
        problems.clear();
        boolean all = true;
        for (String[] f : fieldValueLabel) all &= enter(f[0], f[1], f[2]);
        return all;
    }

    // ---- Done By, Department, Doctor ---------------------------------------------------------------------------------------------------

    public List<String> optionNames(String ng) {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test(norm(o.textContent))).map(o=>norm(o.textContent)); }", ng);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Select the FIRST option with this exact text (ignoring case) and make angular/select2 see the change; returns the text picked, "" if it could not be selected. */
    private String choose(String ng, String text, String what) {
        Object r = page.evaluate("(a) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+a[0]+\"']\"); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()===a[1].toLowerCase()); if(i<0) return '';"
                + " const picked=norm(e.options[i].textContent); e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} }"
                + " return picked; }", Arrays.asList(ng, text));
        String got = r == null ? "" : r.toString();
        waitForAngular(700);
        if (got.isEmpty()) { problems.add("the " + what + " \"" + text + "\" could not be selected"); return ""; }
        if (model(ng).isEmpty()) problems.add("the " + what + " is not held by the model after selecting \"" + got + "\"");
        return got;
    }

    private String pickFrom(String ng, List<String> preferred, String what) {
        List<String> all = optionNames(ng);
        if (all.isEmpty()) { problems.add("the " + what + " dropdown has no options"); return ""; }
        String pick = all.get(0);
        outer:
        for (String p : preferred) for (String a : all) if (a.equalsIgnoreCase(p)) { pick = a; break outer; }
        return choose(ng, pick, what);
    }

    public String selectDoneBy(List<String> preferred) { problems.clear(); lastDoneBy = pickFrom(M + "donebyid", preferred, "Done By"); return lastDoneBy; }

    /** Select the Department (the first of the preferred names, else the first whose name has dialysis / renal / nephro in it, else the first) and wait for the screen to load its doctors. */
    public String selectDepartment(List<String> preferred) {
        problems.clear();
        List<String> all = optionNames(M + "departmentid");
        if (all.isEmpty()) { problems.add("the Department dropdown has no options"); return ""; }
        String pick = null;
        outer:
        for (String p : preferred) for (String a : all) if (a.equalsIgnoreCase(p)) { pick = a; break outer; }
        if (pick == null) for (String a : all) if (a.toLowerCase().matches(".*(dialysis|renal|nephro).*")) { pick = a; break; }
        if (pick == null) pick = all.get(0);
        lastDepartment = choose(M + "departmentid", pick, "Department");
        for (int i = 0; i < 28 && optionNames(M + "doctorid").isEmpty(); i++) page.waitForTimeout(250);
        page.waitForTimeout(800);
        return lastDepartment;
    }

    /** What the Doctor select shows now ("" when nothing real is selected). */
    public String doctorShown() {
        Object r = page.evaluate("() => {" + JS + " const e=document.querySelector(\"select[ng-model='" + M + "doctorid']\"); if(!e || e.selectedIndex<0) return ''; const t=norm(e.options[e.selectedIndex].textContent); return /^-*\\s*select/i.test(t)? '' : t; }");
        return r == null ? "" : r.toString();
    }

    /** True when the Doctor is already set (by the screen, after the department was chosen). */
    public boolean doctorFilled() { return !model(M + "doctorid").isEmpty() && !doctorShown().isEmpty(); }

    /** Make sure a Doctor is set: leave the autofilled one, else select the first of the department's doctors. */
    public boolean ensureDoctor() {
        problems.clear();
        doctorWasAutofilled = doctorFilled();
        if (doctorWasAutofilled) { lastDoctor = doctorShown(); return true; }
        List<String> docs = optionNames(M + "doctorid");
        if (docs.isEmpty()) { problems.add("the department offers no doctor"); return false; }
        lastDoctor = choose(M + "doctorid", docs.get(0), "Doctor");
        return ok() && doctorFilled();
    }

    public List<String> doctorOptions() { return optionNames(M + "doctorid"); }

    // ---- Save ------------------------------------------------------------------------------------------------------------------------

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(SaveDialysisRecord|clearForm|closeform)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__drDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__drDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("DialysisRecord.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__drDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Save, answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String saveAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        lastSaveRequest = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^SaveDialysisRecord/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__drSave'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Save button is not on screen"); return ""; }
        try { page.locator("#__drSave").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__drSave'); if(e) e.removeAttribute('id'); }"); }
        long end = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < end) {
            String d = answerDialog();
            if (!d.isEmpty()) lastDialog = lastDialog.isEmpty() ? d : lastDialog + " > " + d;
            lastToast = toasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(350);
        }
        page.waitForTimeout(800);
        lastToast = toasts();
        parseSaveAnswer();
        return lastToast;
    }

    /** After a successful save the screen returns to the list (waits for it). */
    public boolean awaitList(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (onList()) return true; page.waitForTimeout(250); }
        return onList();
    }

    private void parseSaveAnswer() {
        lastSaveStatus = -1;
        lastSaveMessage = "";
        String[] c = null;
        synchronized (saveCalls) {
            if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1);
            else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1);
        }
        if (c == null) return;
        lastSaveRequest = c[4].length() > 420 ? c[4].substring(0, 420) + "..." : c[4];
        Matcher m = Pattern.compile("\"ResultStatus\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (m.find()) lastSaveStatus = Integer.parseInt(m.group(1));
        Matcher msg = Pattern.compile("\"Message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(c[3]);
        if (msg.find()) lastSaveMessage = msg.group(1);
        int http = Integer.parseInt(c[0]);
        if (http >= 400) lastSaveMessage = "HTTP " + http + " " + c[2].replaceAll("^https?://[^/]+", "") + " " + lastSaveMessage;
    }

    /** What the save call and any failed API call answered, for the report. */
    public String saveDiagnosis() {
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) {
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[1]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(220, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    public boolean savedByServer() { return lastSaveStatus == 1 || lastSaveStatus == 2; }

    /** The toast is a success message AND the server did not refuse the save. The server spells several messages wrongly (Succesfully, sucessfully), so those are accepted too. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == 2 || lastSaveStatus == -1);
    }
}
