package com.kpj.pages.AncillaryServices_page.CRM_page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ancillary Services &gt; CRM &gt; <b>Patient History</b> — Page Object (route {@code #/PatientHelpDesk}; the same screen is also listed under OP).
 *
 * <p>Confirmed live (devhis build v2026.11.11.438). The screen: an <b>MRN</b> box ({@code PatientHelpDesk.MRNO}) with a search button ({@code SearchPatientByMRNo()}, {@code POST /api/patientregistration/FetchPatientData}
 * {@code GetPatientInfo} then {@code GetPatientInfoForEMR}) and a patient-search popup ({@code openPopupScreen()}); once a patient is loaded: the <b>OPD</b> and <b>IPD</b> visit tables, <b>Patient Information</b>,
 * <b>Bill/Financial Details</b>, <b>Charges</b> and, at the bottom, the buttons <b>Company Approved Amount</b>, <b>Patient Record History</b> and <b>Patient Task</b> ({@code #btnpatremark}, {@code BtnPatientRemark()}).
 * "Patient Task" is a BUTTON, not a tab.</p>
 *
 * <p>Patient Task opens the popup {@code #patremark}: the patient's name and MRN, a <b>Remark Status</b> radio (Open / Completed / All), the table of the patient's tasks (Date Time | Remark | Delete | Addedby |
 * Completed | CompletedDateTime | Completed By; loaded by {@code POST /api/queueManagement/Fetch {visitid: patient id, ExecFlag: GetPatientRemarkDetails}}), the <b>Patient Task*</b> textarea
 * ({@code patremark.patientremark}), <b>Add</b> ({@code #BtnAdd}, {@code AddPatientRemarkDetails(...)}: only pushes the task into the list on screen - an empty task warns), <b>Save</b>
 * ({@code fnIUDPatientRemark()}: sends the WHOLE list as {@code IUDPatientRemark}; on ResultStatus 1 raises the "record added" toast and tries to hide {@code #patientremark}, which is NOT the popup's id
 * ({@code #patremark}), so the popup stays open) and <b>Close</b>.</p>
 *
 * <p>Saving is REAL: the task is stored for the patient and stays (it can be deleted in the popup, which this page does not do).</p>
 */
public class PatientHistory extends BasePage {

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

    public PatientHistory(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastMrn = "", lastPatient = "", lastTask = "", lastAddedRow = "", lastToast = "", lastSaveMessage = "", lastDialog = "", lastSaveRequest = "";
    public int lastSaveStatus = -1;
    public String lastPatientId = "";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String HREF = "#/patienthelpdesk";
    private static final String PARENT = "Ancillary Services", GROUP = "CRM";
    private static final String MODAL = "patremark";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** The link to the screen INSIDE the Ancillary Services > CRM group (the same screen is also listed under OP). Tags it and returns true when it is on screen. */
    private boolean tagCrmLink() {
        Object r = page.evaluate("(a) => {" + JS + " const top=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!top) return false; const tli=top.closest('li'); if(!tli) return false;"
                + " const g=[...tli.querySelectorAll('a')].find(x=>x!==top && norm(x.textContent)===a[1]); if(!g) return false; const gli=g.closest('li'); if(!gli) return false;"
                + " const l=[...gli.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===a[2]); if(!l) return false; l.id='__phLink'; return true; }", Arrays.asList(PARENT, GROUP, HREF));
        return Boolean.TRUE.equals(r);
    }

    private boolean waitCrmLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (tagCrmLink()) return true; page.waitForTimeout(250); }
        return tagCrmLink();
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

    /** Ancillary Services &gt; CRM &gt; Patient History. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='" + PARENT + "')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!tagCrmLink()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !tagCrmLink()) clickInner(PARENT, GROUP);
                waitCrmLink(12000);
            }
            if (tagCrmLink()) {
                try { page.locator("#__phLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("PatientHistory.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__phLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !onScreen(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    /** On the screen: the route AND the screen's own MRN box (a bounce to the dashboard has neither). */
    public boolean onScreen() {
        return page.url().toLowerCase().matches(".*#/patienthelpdesk/?$")
                && Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll('input')].some(e=>vis(e) && (e.getAttribute('ng-model')||'')==='PatientHelpDesk.MRNO'); }"));
    }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__phToasts=[]; if(window.__phObs) window.__phObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__phToasts.includes(m)) window.__phToasts.push(m); });"
                + " window.__phObs=new MutationObserver(add); window.__phObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__phToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- MRN and the patient ---------------------------------------------------------------------------------------------------------

    private String boxValue(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e? (e.value||'') : ''; }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); try{ const m=angular.element(e).scope().$eval(n); return m==null? '' : String(m); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** A value of the screen's own scope (e.g. {@code PatientHelpDesk.patientid}), which no input is bound to; "" when unset. */
    private String scopeValue(String expr) {
        Object v = page.evaluate("(x) => { try{ const e=document.querySelector(\"input[ng-model='PatientHelpDesk.MRNO']\"); const m=angular.element(e).scope().$eval(x); return m==null? '' : String(m); }catch(err){ return ''; } }", expr);
        return v == null ? "" : v.toString().trim();
    }

    public boolean enterMrn(String mrn) {
        problems.clear();
        lastMrn = mrn;
        Locator in = page.locator("input[ng-model='PatientHelpDesk.MRNO']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(mrn, new Locator.PressSequentiallyOptions().setDelay(50));
        } catch (Exception e) { problems.add("the MRN could not be typed"); return false; }
        waitForAngular(300);
        if (!mrn.equals(boxValue("PatientHelpDesk.MRNO"))) problems.add("the MRN box shows \"" + boxValue("PatientHelpDesk.MRNO") + "\" instead of \"" + mrn + "\"");
        else if (!mrn.equals(model("PatientHelpDesk.MRNO"))) problems.add("the MRN is not held by the model (\"" + model("PatientHelpDesk.MRNO") + "\")");
        return ok();
    }

    /** Click the search button beside the MRN box and wait until the patient is loaded (its id is known and the OPD table lists visits, or the patient's own data has arrived). */
    public boolean clickSearch() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__phSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the search button is not on screen"); return false; }
        armToasts();
        try { page.locator("#__phSearch").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the search button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__phSearch'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 120 && (scopeValue("PatientHelpDesk.patientid").isEmpty() || scopeValue("PatientHelpDesk.patientid").equals("0")); i++) page.waitForTimeout(250);
        waitForAngular(3000);
        lastPatientId = scopeValue("PatientHelpDesk.patientid");
        lastPatient = scopeValue("PatientHelpDesk.PatientName");
        lastToast = toasts();
        if (lastPatientId.isEmpty() || lastPatientId.equals("0")) problems.add("the search for MRN " + lastMrn + " loaded no patient" + (lastToast.isEmpty() ? "" : " (toast: \"" + lastToast + "\")"));
        return ok();
    }

    /** The number of visits the OPD table lists. */
    public int opdVisits() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>/Visit No/.test(x.textContent) && /Visit Date/.test(x.textContent)); return t? [...t.querySelectorAll('tbody tr')].filter(r=>r.textContent.trim().length>0).length : 0; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    // ---- the Patient Task popup ---------------------------------------------------------------------------------------------------------

    public boolean popupOpen() {
        Object r = page.evaluate("(id) => {" + JS + " const m=document.getElementById(id); return !!m && vis(m) && /Patient Task/i.test(m.textContent||''); }", MODAL);
        return Boolean.TRUE.equals(r);
    }

    /** Click the Patient Task button (a button at the foot of the screen, not a tab) and wait for its popup. */
    public boolean clickPatientTask() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^BtnPatientRemark/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__phTask'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Patient Task button is not on screen"); return false; }
        armToasts();
        try { page.locator("#__phTask").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Patient Task button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__phTask'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 40 && !popupOpen(); i++) page.waitForTimeout(250);
        if (!popupOpen()) { problems.add("the Patient Task popup did not open" + (toasts().isEmpty() ? "" : " (toast: \"" + toasts() + "\")")); return false; }
        for (int i = 0; i < 24 && taskRows().isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return true;
    }

    /** The tasks the popup's table lists, each as {Date Time, Remark, Added by}. */
    public List<String[]> taskRows() {
        Object r = page.evaluate("(id) => {" + JS + " const m=document.getElementById(id); if(!m) return []; const t=[...m.querySelectorAll('table')].find(x=>/Remark/.test(x.textContent) && /Addedby/.test(x.textContent)); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>norm(td.textContent))).filter(c=>c.length>=3 && (c[0]||c[1])).map(c=>[c[0]||'', c[1]||'', c[3]||'']); }", MODAL);
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2))}); }
        return out;
    }

    /** The patient name and MRN line the popup shows. */
    public String popupPatient() {
        Object r = page.evaluate("(id) => {" + JS + " const m=document.getElementById(id); if(!m) return ''; const b=m.querySelector('.modal-body'); return b? norm(b.textContent).slice(0,160) : ''; }", MODAL);
        return r == null ? "" : r.toString();
    }

    public boolean enterTask(String text) {
        problems.clear();
        lastTask = text;
        Locator t = page.locator("#" + MODAL + " textarea[ng-model='patremark.patientremark']").first();
        try {
            t.scrollIntoViewIfNeeded();
            t.click(new Locator.ClickOptions().setTimeout(5000));
            t.fill(text, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the patient task could not be typed: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(400);
        if (!text.equals(boxValue("patremark.patientremark"))) problems.add("the Patient Task box shows \"" + boxValue("patremark.patientremark") + "\" instead of the task typed");
        else if (!text.equals(model("patremark.patientremark"))) problems.add("the task is not held by the model");
        return ok();
    }

    /** Click Add: the task should join the list (the box empties). Returns false (toast in {@link #lastToast}) when it did not. */
    public boolean clickAdd() {
        problems.clear();
        lastAddedRow = "";
        List<String[]> before = taskRows();
        armToasts();
        Object tagged = page.evaluate("(id) => {" + JS + " const m=document.getElementById(id); const b=m && [...m.querySelectorAll('button')].filter(vis).find(x=>/^AddPatientRemarkDetails/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__phAdd'; b.scrollIntoView({block:'center'}); return true; }", MODAL);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the popup's Add button is not on screen"); return false; }
        try { page.locator("#__phAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the popup's Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__phAdd'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1000);
        List<String[]> after = before;
        for (int i = 0; i < 24; i++) { after = taskRows(); if (after.size() > before.size()) break; page.waitForTimeout(250); }
        lastToast = toasts();
        if (after.size() <= before.size()) { problems.add("Add put no row into the task list" + (lastToast.isEmpty() ? "" : " (toast: \"" + lastToast + "\")")); return false; }
        for (String[] r : after) if (r[1].equals(lastTask)) lastAddedRow = r[0] + " | " + r[1] + " | " + r[2];
        if (lastAddedRow.isEmpty()) problems.add("the list grew but no row carries the task text");
        return ok();
    }

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button that is not one of the popup's own) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(fnIUDPatientRemark|AddPatientRemarkDetails)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__phDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__phDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("PatientHistory.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__phDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click the popup's Save, answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String saveAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        lastSaveRequest = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        Object tagged = page.evaluate("(id) => {" + JS + " const m=document.getElementById(id); const b=m && [...m.querySelectorAll('button')].filter(vis).find(x=>/^fnIUDPatientRemark/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__phSave'; b.scrollIntoView({block:'center'}); return true; }", MODAL);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the popup's Save button is not on screen"); return ""; }
        try { page.locator("#__phSave").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the popup's Save button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__phSave'); if(e) e.removeAttribute('id'); }"); }
        long end = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < end) {
            String d = answerDialog();
            if (!d.isEmpty()) lastDialog = lastDialog.isEmpty() ? d : lastDialog + " > " + d;
            lastToast = toasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(350);
        }
        page.waitForTimeout(1200);
        lastToast = toasts();
        parseSaveAnswer();
        return lastToast;
    }

    /** Click the popup's Close button and wait for the popup to go; returns true when it is gone. */
    public boolean closePopup() {
        problems.clear();
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove()); }");
        Object tagged = page.evaluate("(id) => {" + JS + " const m=document.getElementById(id); const b=m && [...m.querySelectorAll('button')].filter(vis).find(x=>norm(x.textContent)==='Close'); if(!b) return false; b.id='__phClose'; b.scrollIntoView({block:'center'}); return true; }", MODAL);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the popup's Close button is not on screen"); return false; }
        try { page.locator("#__phClose").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the popup's Close button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__phClose'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 24 && popupOpen(); i++) page.waitForTimeout(250);
        waitForAngular(600);
        if (popupOpen()) problems.add("the Patient Task popup is still open after Close");
        return ok();
    }

    // ---- what the server holds ------------------------------------------------------------------------------------------------------------

    /** The tasks the SERVER holds for the loaded patient as {id, remark}, read with the call the popup itself makes; null when it could not be read. */
    public List<String[]> savedTasks() {
        Object r = page.evaluate("() => new Promise(res => { try { const e=document.querySelector(\"input[ng-model='PatientHelpDesk.MRNO']\"); const sc=angular.element(e).scope();"
                + " const h=angular.element(document.body).injector().get('$http');"
                + " h.post('/api/queueManagement/Fetch', {visitid: sc.PatientHelpDesk.patientid, ExecFlag:'GetPatientRemarkDetails', remarkStatus:2}).then(x=>res(JSON.stringify((x.data||[]).map(d=>[d.remarkid||d.id, d.patientremark||d.remark]))), er=>res('ERR '+(er&&er.status))); } catch(x){ res('ERR '+x); } })");
        String s = r == null ? "" : r.toString();
        if (s.isEmpty() || s.startsWith("ERR")) return null;
        List<String[]> out = new ArrayList<>();
        try {
            JsonArray a = JsonParser.parseString(s).getAsJsonArray();
            for (JsonElement row : a) {
                JsonArray c = row.getAsJsonArray();
                out.add(new String[]{c.get(0).isJsonNull() ? "" : c.get(0).getAsString(), c.get(1).isJsonNull() ? "" : c.get(1).getAsString().trim()});
            }
        } catch (Exception e) { return null; }
        return out;
    }

    // ---- the save answer --------------------------------------------------------------------------------------------------------------------------

    private void parseSaveAnswer() {
        lastSaveStatus = -1;
        lastSaveMessage = "";
        String[] c = null;
        synchronized (saveCalls) {
            if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1);
            else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1);
        }
        if (c == null) return;
        lastSaveRequest = c[4].length() > 400 ? c[4].substring(0, 400) + "..." : c[4];
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

    public boolean savedByServer() { return lastSaveStatus == 1; }

    /** The toast is a success message AND the server did not refuse the save. The server spells several messages wrongly (Succesfully, sucessfully), so those are accepted too. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
