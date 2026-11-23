package com.kpj.pages.AncillaryServices_page.CSSD_page;

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
 * Ancillary Services &gt; CSSD &gt; <b>Sterilization Machine Quality Control</b> — Page Object (list {@code #/SterilizationMachineQualityControl}, form {@code #/add-SterilizationMachineQualityControl}).
 *
 * <p>Confirmed live (devhis build v2026.11.17.477). The list screen (From / To Date, Machine, Test Type, Status, Search, Clear, a grid with Edit and View Log History) has a <b>New</b> control
 * ({@code fnAddMachineQualityControl()}, an {@code a.btn} in the page footer - not a {@code button} - below the fold), which opens the form. The form has TWO REAL TABS:</p>
 * <ul>
 *   <li><b>Quality Template</b> (the active one): <b>Quality Template*</b> ({@code QualityTemplate.qualitytemplateid}, plain select: Sterilization Quality Assurance, Sterilization Quality Control/Test),
 *       <b>Add</b> ({@code fnGetQualityTemplate()}, {@code POST /api/Sterilization/Fetchstertemplate {SterilizationID, ExecFlag sterilizationtemplate}}) which loads the template text into <b>Remark*</b> - a
 *       CKEditor ({@code CKEDITOR.instances.editor1}, an iframe {@code cke_wysiwyg_frame}) bound to {@code QualityTemplate.qualitytemplatedata} through a hidden textarea with the {@code ck-editor} directive.</li>
 *   <li><b>Sterilization Machine Quality Control</b>: <b>Date*</b> ({@code MachineQualityControl.testdate}, today), <b>Time*</b> (a {@code timepicker-pop} directive whose box {@code inputTime} accepts typed text such as
 *       "10:30 AM" and holds it as a Date in {@code MachineQualityControl.testtime} once the box loses focus; its clock button opens an HH / MM / AM-PM spinner), <b>Machine*</b>
 *       ({@code MachineQualityControl.machineid}, 65), <b>Test Type*</b> ({@code .testtype}, 25) and the <b>Status*</b> radios ({@code .teststatus}: Success / Fail).</li>
 *   <li><b>Save</b> ({@code fnSave()}): needs the template, its text, date, time, machine, test type and status (it switches to the tab that lacks one and warns); on ResultStatus 1 it toasts the server's
 *       message without its trailing dot (JAlert adds one) and returns to the list.</li>
 * </ul>
 *
 * <p>Saving is REAL: a Sterilization Machine Quality Control record is created.</p>
 */
public class SterilizationMachineQualityControl extends BasePage {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            String post = r.request().postData() == null ? "" : r.request().postData();
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && low.contains("machinequalitycontrol") && post.contains("\"qualitytemplatedata\"");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 1200 ? body.substring(0, 1200) : body, post};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public SterilizationMachineQualityControl(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not do (empty = it held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastToast = "", lastSaveMessage = "", lastResponseId = "", lastSaveRequest = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const owner=()=>{ const e=document.querySelector(\"select[ng-model='QualityTemplate.qualitytemplateid']\"); let o=e? angular.element(e).scope() : null; while(o && !Object.prototype.hasOwnProperty.call(o,'fnSave')) o=o.$parent; return o; };"
          + "const tabs=()=>[...document.querySelectorAll('.nav-tabs li')].filter(vis);";

    private static final String PARENT = "Ancillary Services", GROUP = "CSSD", MENU = "Sterilization Machine Quality Control";

    // ---- navigation ------------------------------------------------------------------------------------------

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

    /** Tag the menu link INSIDE the CSSD group. */
    private boolean tagLink() {
        Object r = page.evaluate("(t) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)==='Ancillary Services'); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const g=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)==='CSSD'); if(!g) return false; const gli=g.closest('li'); if(!gli) return false;"
                + " const l=[...gli.querySelectorAll('a[href]')].filter(vis).find(x=>norm(x.textContent)===t); if(!l) return false; l.id='__smLink'; return true; }", MENU);
        return Boolean.TRUE.equals(r);
    }

    private boolean tagLinkProbe() {
        boolean t = tagLink();
        page.evaluate("() => { const e=document.getElementById('__smLink'); if(e) e.removeAttribute('id'); }");
        return t;
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (tagLinkProbe()) return true; page.waitForTimeout(250); }
        return tagLinkProbe();
    }

    /** Ancillary Services &gt; CSSD &gt; Sterilization Machine Quality Control. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Ancillary Services')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!tagLinkProbe()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !tagLinkProbe()) clickInner(PARENT, GROUP);
                waitLink(12000);
            }
            if (tagLink()) {
                try { page.locator("#__smLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("CSSD SterilizationMachineQualityControl.nav: click failed - " + e.getMessage().split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__smLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !newControlShown(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    private boolean newControlShown() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('[ng-click]')].some(b=>vis(b) && (b.getAttribute('ng-click')||'').indexOf('fnAddMachineQualityControl')===0); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/sterilizationmachinequalitycontrol/?$") && newControlShown(); }

    public boolean formOpen() {
        Object r = page.evaluate("() => {" + JS + " const e=document.querySelector(\"select[ng-model='QualityTemplate.qualitytemplateid']\"); return !!e && !!e.options && e.options.length>1 && tabs().length>=2; }");
        return Boolean.TRUE.equals(r) && page.url().toLowerCase().contains("#/add-sterilizationmachinequalitycontrol");
    }

    /** What the list screen holds for today (read from its grid data): number of records. */
    public int listRows() {
        Object r = page.evaluate("() => { const e=document.querySelector('#grid1'); try { const sc=angular.element(e).scope(); const d=(sc.gridOptions&&sc.gridOptions.data)||[]; return d.length; } catch(x) { return -1; } }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Click New (a footer control below the fold, scrolled into view and clicked for real) and wait for the form. */
    public boolean clickNew() {
        problems.clear();
        Locator n = page.locator("[ng-click^='fnAddMachineQualityControl']").first();
        try {
            n.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(6000));
            n.click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) { problems.add("New could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        for (int i = 0; i < 40 && !formOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1200);
        if (!formOpen()) problems.add("the form did not open (" + page.url() + ")");
        return ok();
    }

    // ---- the two tabs ------------------------------------------------------------------------------------------------------------

    /** The names of the form's tabs, in order. */
    public List<String> tabNames() {
        Object r = page.evaluate("() => {" + JS + " return tabs().map(t=>norm(t.textContent)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The name of the tab that is active now. */
    public String activeTab() {
        Object r = page.evaluate("() => {" + JS + " const t=tabs().find(x=>x.classList.contains('active')); return t? norm(t.textContent) : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click the tab with this name (a real click) and check it is the active one. */
    public boolean clickTab(String name) {
        problems.clear();
        Object tagged = page.evaluate("(n) => {" + JS + " const li=tabs().find(x=>norm(x.textContent)===n); if(!li) return false; const a=li.querySelector('a')||li; a.id='__smTab'; return true; }", name);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("there is no tab \"" + name + "\""); return false; }
        try { page.locator("#__smTab").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the tab \"" + name + "\" could not be clicked: " + e.getMessage().split("\n")[0]); }
        finally { page.evaluate("() => { const e=document.getElementById('__smTab'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(900);
        if (!name.equals(activeTab())) problems.add("the active tab is \"" + activeTab() + "\" after clicking \"" + name + "\"");
        return ok();
    }

    // ---- selects ----------------------------------------------------------------------------------------------------------------

    private String model(String expr) {
        Object r = page.evaluate("(x) => {" + JS + " try { const o=owner(); const v=o.$eval(x); return v==null? '' : String(v); } catch(e) { return ''; } }", expr);
        return r == null ? "" : r.toString();
    }

    /** The real options (placeholder excluded) of a select. */
    public List<String> options(String ngModel) {
        Object r = page.evaluate("(m) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+m+\"']\"); return e? [...e.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*\\s*select/i.test(t)) : []; }", ngModel);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Select the first of {@code preferred} the select offers (a real selection) - else the first real option - and check the screen's model holds it. Returns the option, "" on failure. */
    private String selectOne(String ngModel, String what, String... preferred) {
        problems.clear();
        List<String> all = options(ngModel);
        if (all.isEmpty()) { problems.add(what + " has no options"); return ""; }
        String pick = null;
        pickLoop:
        for (String p : preferred) for (String a : all) if (a.equalsIgnoreCase(p)) { pick = a; break pickLoop; }
        if (pick == null) pick = all.get(0);
        try { page.locator("select[ng-model='" + ngModel + "']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(pick), new Locator.SelectOptionOptions().setTimeout(8000)); }
        catch (Exception e) { problems.add("\"" + pick + "\" could not be selected: " + e.getMessage().split("\n")[0]); return ""; }
        waitForAngular(900);
        Object shown = page.evaluate("(m) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+m+\"']\"); return e && e.selectedIndex>=0? norm(e.options[e.selectedIndex].textContent) : ''; }", ngModel);
        String mv = model(ngModel);
        if (shown == null || !pick.equals(shown.toString())) { problems.add(what + " shows \"" + shown + "\" instead of \"" + pick + "\""); return ""; }
        if (mv.isEmpty() || mv.equals("0")) { problems.add("the screen's model " + ngModel + " is empty after selecting \"" + pick + "\""); return ""; }
        return pick;
    }

    public String selectQualityTemplate(String... preferred) { return selectOne("QualityTemplate.qualitytemplateid", "Quality Template", preferred); }

    public String selectMachine(String... preferred) { return selectOne("MachineQualityControl.machineid", "Machine", preferred); }

    public String selectTestType(String... preferred) { return selectOne("MachineQualityControl.testtype", "Test Type", preferred); }

    // ---- Add: the template into Remark (CKEditor) ---------------------------------------------------------------------------------

    /** The text the Remark editor holds now (CKEditor's own data, tags stripped). */
    public String editorText() {
        Object r = page.evaluate("() => { try { const d=CKEDITOR.instances.editor1.getData(); const t=document.createElement('div'); t.innerHTML=d; return (t.textContent||'').replace(/\\s+/g,' ').trim(); } catch(e) { return ''; } }");
        return r == null ? "" : r.toString();
    }

    /** The text the screen's own model {@code QualityTemplate.qualitytemplatedata} holds (tags stripped). */
    public String modelText() {
        Object r = page.evaluate("() => {" + JS + " try { const o=owner(); const d=o.QualityTemplate.qualitytemplatedata; const t=document.createElement('div'); t.innerHTML=d||''; return (t.textContent||'').replace(/\\s+/g,' ').trim(); } catch(e) { return ''; } }");
        return r == null ? "" : r.toString();
    }

    /** Click Add and wait for the template text to appear in Remark. Returns that text ("" when nothing came). */
    public String clickAddTemplate() {
        problems.clear();
        armToasts();
        try {
            page.waitForResponse(x -> x.url().contains("/Sterilization/Fetchstertemplate"), new Page.WaitForResponseOptions().setTimeout(25000),
                    () -> page.locator("[ng-click^='fnGetQualityTemplate']").first().click(new Locator.ClickOptions().setTimeout(6000)));
        } catch (Exception e) { problems.add("Add fetched no template: " + e.getMessage().split("\n")[0]); }
        waitForAngular(1200);
        for (int i = 0; i < 24 && editorText().isEmpty(); i++) page.waitForTimeout(250);
        String t = editorText();
        if (t.isEmpty()) problems.add("the Remark editor stayed empty after Add" + (toasts().isEmpty() ? "" : " (the screen said: " + toasts() + ")"));
        return t;
    }

    /**
     * Fill in details in Remark: click into the editor (a real click in its iframe), go to the end of the template text, start a new paragraph and type. Then leave the editor (click on the tab bar) and check
     * CKEditor's data AND the screen's model hold the typed text.
     */
    public boolean appendDetails(String text) {
        problems.clear();
        try {
            Locator body = page.frameLocator("iframe.cke_wysiwyg_frame").locator("body");
            body.click(new Locator.ClickOptions().setTimeout(8000));
            page.keyboard().press("Control+End");
            page.keyboard().press("Enter");
            page.keyboard().type(text);
        } catch (Exception e) { problems.add("the details could not be typed into Remark: " + e.getMessage().split("\n")[0]); return false; }
        page.waitForTimeout(600);
        // leave the editor so a blur-bound model is updated
        try { page.locator(".nav-tabs li a").first().click(new Locator.ClickOptions().setTimeout(4000)); } catch (Exception ignore) { }
        waitForAngular(900);
        String ed = editorText(), mv = modelText();
        String marker = text.length() > 40 ? text.substring(0, 40) : text;
        if (!ed.contains(marker)) problems.add("the Remark editor does not hold the details typed (it holds " + ed.length() + " characters)");
        else if (!mv.contains(marker)) problems.add("the details are in the editor but NOT in the screen's model (" + mv.length() + " characters)");
        return ok();
    }

    // ---- the Time box -----------------------------------------------------------------------------------------------------------------

    /** What the Time box shows. */
    public String timeBox() {
        Object r = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='inputTime']\"); return e? e.value : ''; }");
        return r == null ? "" : r.toString();
    }

    /** The time the screen's model {@code MachineQualityControl.testtime} holds, as HH:mm ("" when none). */
    public String timeModel() {
        Object r = page.evaluate("() => {" + JS + " try { const o=owner(); const d=o.MachineQualityControl.testtime; if(!d) return ''; const x=new Date(d); const p=n=>String(n).padStart(2,'0'); return p(x.getHours())+':'+p(x.getMinutes()); } catch(e) { return ''; } }");
        return r == null ? "" : r.toString();
    }

    /** Type the time (text such as "10:30 AM") into the Time box, leave it, and check the box and the model (HH:mm) hold it. */
    public boolean enterTime(String text, String expectedHhmm) {
        problems.clear();
        Locator t = page.locator("input[ng-model='inputTime']").first();
        try {
            t.click(new Locator.ClickOptions().setTimeout(5000));
            t.fill(text, new Locator.FillOptions().setTimeout(5000));
            page.keyboard().press("Tab");
        } catch (Exception e) { problems.add("the time could not be typed: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(900);
        String mv = timeModel();
        if (timeBox().isEmpty()) problems.add("the Time box is empty after typing \"" + text + "\"");
        else if (!expectedHhmm.equals(mv)) problems.add("the Time box shows \"" + timeBox() + "\" but the screen's model holds \"" + mv + "\" instead of " + expectedHhmm);
        return ok();
    }

    // ---- Status -------------------------------------------------------------------------------------------------------------------------

    /** Tick the Status radio with this value (Success / Fail) with a real click and check the model holds it. */
    public boolean tickStatus(String value) {
        problems.clear();
        Locator r = page.locator("input[type='radio'][ng-model='MachineQualityControl.teststatus'][value='" + value + "']").first();
        try { r.check(new Locator.CheckOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Status radio \"" + value + "\" could not be ticked: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(600);
        String mv = model("MachineQualityControl.teststatus");
        if (!value.equals(mv)) problems.add("the model holds \"" + mv + "\" instead of \"" + value + "\"");
        return ok();
    }

    // ---- Save --------------------------------------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones. */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__smToasts=[]; if(window.__smObs) window.__smObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__smToasts.includes(m)) window.__smToasts.push(m); });"
                + " window.__smObs=new MutationObserver(add); window.__smObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__smToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Save (a real click). The toasts are recorded from this moment. */
    public void clickSave() {
        problems.clear();
        lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        lastSaveStatus = -1; lastSaveMessage = ""; lastResponseId = ""; lastSaveRequest = "";
        armToasts();
        Locator b = page.locator("[ng-click^='fnSave']:visible").first();
        try {
            b.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            b.click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("Save could not be clicked: " + e.getMessage().split("\n")[0]); }
    }

    /** Wait for a toast after Save (up to {@code ms}); also reads the save call's answer. Returns the toasts shown ("" if none). */
    public String awaitToast(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            lastToast = toasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(300);
        }
        page.waitForTimeout(800);
        lastToast = toasts();
        parseSaveAnswer();
        return lastToast;
    }

    private void parseSaveAnswer() {
        String[] c = null;
        synchronized (saveCalls) { if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1); else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1); }
        if (c == null) return;
        lastSaveRequest = c[2].replaceAll("^https?://[^/]+", "") + " (" + c[4].length() + " characters of JSON)";
        Matcher m = Pattern.compile("\"ResultStatus\"\\s*:\\s*(-?\\d+)").matcher(c[3]);
        if (m.find()) lastSaveStatus = Integer.parseInt(m.group(1));
        Matcher msg = Pattern.compile("\"Message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(c[3]);
        if (msg.find()) lastSaveMessage = msg.group(1);
        Matcher ri = Pattern.compile("\"ResponseId\"\\s*:\\s*\"?(\\d+)").matcher(c[3]);
        if (ri.find()) lastResponseId = ri.group(1);
        int http = Integer.parseInt(c[0]);
        if (http >= 400) lastSaveMessage = "HTTP " + http + " " + c[2].replaceAll("^https?://[^/]+", "") + " " + lastSaveMessage;
    }

    public boolean savedByServer() { return lastSaveStatus == 1; }

    /** What the save call and any failed API call answered, for the report. */
    public String saveDiagnosis() {
        StringBuilder sb = new StringBuilder();
        synchronized (saveCalls) {
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(260, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    /** The toast is a success message and the server did not refuse the save. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !t.contains("unexpected");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    /** True once the screen has returned to the list after a successful Save. */
    public boolean backOnList(int ms) {
        long end = System.currentTimeMillis() + ms;
        do { if (onScreen()) return true; page.waitForTimeout(300); } while (System.currentTimeMillis() < end);
        return onScreen();
    }

    // ---- what the server holds ---------------------------------------------------------------------------------------------------------

    /** The machine id the screen's model holds (the grid names a machine differently from the dropdown, so records are matched by id). -1 when none. */
    public int machineId() {
        try { return Integer.parseInt(model("MachineQualityControl.machineid").replaceAll("[^0-9]", "")); } catch (Exception e) { return -1; }
    }

    /** The Remark text the SERVER holds for this record (the call the list's Edit pencil makes, ExecFlag SINGLEVAL, read-only), tags stripped; "" when it could not be read. */
    public String savedRemark(String id) {
        Object r = page.evaluate("(i) => new Promise(res => { try { const h=angular.element(document.body).injector().get('$http');"
                + " h.post('/api/MachineQualityControl/fetch', {id:i, ExecFlag:'SINGLEVAL'}).then(x=>{ const v=(x.data||[])[0]; if(!v){ res(''); return; } const t=document.createElement('div'); t.innerHTML=v.qualitytemplatedata||''; res((t.textContent||'').split(String.fromCharCode(10)).join(' ').trim()); }, e=>res('')); } catch(x) { res(''); } })", id);
        return r == null ? "" : r.toString();
    }

    /** The records the list call returns for this date (the call the Search button makes, read-only), each as {machine name, time, test type, status, template id, record id, machine id}. */
    public List<String[]> records(LocalDate date) {
        Object r = page.evaluate("(d) => new Promise(res => { try { const h=angular.element(document.body).injector().get('$http');"
                + " h.post('/api/MachineQualityControl/fetch', {FromDate:d, ToDate:d, machineid:0, testtype:0, teststatus:'', ExecFlag:'LIST'}).then(x=>{ const l=x.data||[]; res(JSON.stringify(l.map(v=>[String(v.machinename), String(v.testtime), String(v.testtypename), String(v.teststatus), String(v.qualitytemplateid), String(v.id), String(v.machineid)]))); }, e=>res('ERR')); } catch(x) { res('ERR'); } })",
                date.format(DMY));
        List<String[]> out = new ArrayList<>();
        try { for (JsonElement e : JsonParser.parseString(String.valueOf(r)).getAsJsonArray()) { JsonArray c = e.getAsJsonArray(); String[] v = new String[c.size()]; for (int i = 0; i < v.length; i++) v[i] = c.get(i).getAsString(); out.add(v); } }
        catch (Exception ignore) { }
        return out;
    }
}
