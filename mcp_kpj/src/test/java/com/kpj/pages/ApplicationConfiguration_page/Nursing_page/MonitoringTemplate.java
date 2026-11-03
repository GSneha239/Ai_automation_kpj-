package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

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
 * Application Configuration &gt; Nursing &gt; <b>Monitoring Template</b> — Page Object (list {@code #/MonitoringTemplateList}, form {@code #/add-MonitoringTemplate}).
 *
 * <p>Confirmed live (devhis build v2026.11.03.376). The list is a ui-grid ({@code Code | Remark | Print Name | Status | Edit}, data fields {@code code}, {@code description}, {@code printname}, {@code Status};
 * fetched by {@code POST /api/MonitoringTemplate/Fetch}) with an <b>Add</b> button ({@code AddMonitoringTemplate()}); it held 2 rows at the time: {@code 123 = manual tester / test} and
 * {@code C001 = TEST / T12}. The form ({@code MonitoringTemplate.*}) has <b>Code*</b> ({@code .code}), <b>Remark*</b> ({@code .description}), <b>Print Name</b> ({@code .printname}), a
 * <b>Select | Group</b> table of tick boxes ({@code SG.ChkGroupList}; one row per Monitoring Group, the group's name in the second cell), an optional rich-text <b>Interpretation</b> (left alone) and the buttons
 * <b>Save</b> ({@code fnIUDMonitoringTemplate()}), Clear ({@code resetForm()}) and Back ({@code closeForm()}).</p>
 *
 * <p>A template is a chart for a kind of observation, so each new one is a real chart name paired with the Monitoring Group it belongs to (for example "Routine Vital Signs Chart" with the group "Vital Signs"),
 * a short print name, and that group's box ticked. The existing codes have no common format ({@code 123}, {@code C001}), so the new code uses a series of its own: {@code MT} plus three digits, one above the
 * largest {@code MT} number in use ({@code MT001} first), like the {@code MG001} of the Monitoring Group screen. Saving is REAL: the template is added to the master and stays (it can only be made inactive
 * with its Status switch, which this page never touches).</p>
 *
 * <p>The Nursing group is looked up INSIDE the Application Configuration entry only (Nursing is also a top-level module of the sidebar).</p>
 */
public class MonitoringTemplate extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall") && !low.contains("getmaster");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public MonitoringTemplate(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastCode = "", lastRemark = "", lastPrintName = "", lastGroup = "", lastToast = "", lastToastKinds = "", lastSaveMessage = "", lastDialog = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String CODE_NG = "MonitoringTemplate.code", REMARK_NG = "MonitoringTemplate.description", PRINT_NG = "MonitoringTemplate.printname", GROUP_NG = "SG.ChkGroupList";
    private static final String HREF = "#/monitoringtemplatelist";
    private static final String PARENT = "Application Configuration", GROUP = "Nursing";

    /** Real monitoring charts as {remark, print name, the Monitoring Group it belongs to}; the master keeps whatever is saved, so no "Auto…" junk. */
    private static final String[][] POOL = {
            {"Routine Vital Signs Chart", "Vital Signs Chart", "Vital Signs"},
            {"Neurological Observation Chart", "Neuro Obs Chart", "Neurological Observation"},
            {"Fluid Balance Chart", "Fluid Balance", "Fluid Balance"},
            {"Cardiac Monitoring Chart", "Cardiac Chart", "Cardiac Monitoring"},
            {"Blood Glucose Monitoring Chart", "Glucose Chart", "Glucose Monitoring"},
            {"Pain Assessment Chart", "Pain Chart", "Pain Assessment"}
    };

    // ---- navigation ------------------------------------------------------------------------------------------

    private boolean linkVisible() {
        Object r = page.evaluate("(h) => {" + JS + " return !![...document.querySelectorAll('a[href]')].find(x=>vis(x) && (x.getAttribute('href')||'').toLowerCase()===h); }", HREF);
        return Boolean.TRUE.equals(r);
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (linkVisible()) return true; page.waitForTimeout(250); }
        return linkVisible();
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

    /** Open/shut state of the group {@code label} looked up INSIDE the entry {@code parent}: 1 open, 0 shut, -1 not on screen. */
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

    /**
     * Application Configuration &gt; Nursing &gt; Monitoring Template. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group
     * again, and a click on Nursing straight after Application Configuration is lost), so the first group gets a settling pause before the next one is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Application Configuration')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!linkVisible()) {
                if (innerState(PARENT, GROUP) < 0) {                            // the group is not on screen inside Application Configuration: it is shut
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !linkVisible()) clickInner(PARENT, GROUP);
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__mtLink'; }", HREF);
                try { page.locator("#__mtLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("MonitoringTemplate.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__mtLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        waitAddButton();
        waitForAngular(1000);
        return onList();
    }

    private void waitAddButton() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a,button')].some(x=>/^AddMonitoringTemplate/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("MonitoringTemplate.nav: the Add button is not ready"); }
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/monitoringtemplatelist/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-monitoringtemplate/?$")
                && Boolean.TRUE.equals(page.evaluate("(n) => {" + JS + " return !![...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); }", CODE_NG));
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    private static final String GRID =
            "const g=document.querySelector('.ui-grid'); const sc=g? angular.element(g).scope() : null; const go=sc? (sc.gridOptions || (sc.$parent && sc.$parent.gridOptions)) : null;";

    /** Every monitoring template the list holds (all pages), as {code, remark, print name, status}; read from the grid's data array. Empty when the grid has none. */
    public List<String[]> existing() {
        Object r = page.evaluate("() => { try{ " + GRID + " if(!go||!go.data) return []; const s=v=>v==null? '' : String(v);"
                + " const cd=go.columnDefs||[]; const f=(re,def)=>{ const c=cd.find(c=>re.test(String(c.displayName||c.name||c.field||'').trim())); return c? (c.field||c.name||def) : def; };"
                + " const fc=f(/^code/i,'code'), fd=f(/^(remark|description)/i,'description'), fp=f(/^print/i,'printname'), fs=f(/^status/i,'Status');"
                + " const pick=(x,k,def)=>{ for(const n of [k,def,def.charAt(0).toUpperCase()+def.slice(1),def.charAt(0).toLowerCase()+def.slice(1)]) if(x[n]!=null) return x[n]; return ''; };"
                + " return go.data.filter(x=>x.id>0 || x.code).map(x=>[s(pick(x,fc,'code')), s(pick(x,fd,'description')), s(pick(x,fp,'printname')), s(pick(x,fs,'Status'))]); }catch(e){ return []; } }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2)), String.valueOf(c.get(3))}); }
        return out;
    }

    /** True once the list's grid exists with a data array (it can be empty). */
    public boolean gridReady() {
        Object r = page.evaluate("() => { try{ " + GRID + " return !!go && Array.isArray(go.data); }catch(e){ return false; } }");
        return Boolean.TRUE.equals(r);
    }

    /** Read the rows the list holds: wait until the grid is ready, then up to 8 s more for rows (an empty answer after that is taken as an empty table). */
    public List<String[]> awaitExisting() {
        for (int i = 0; i < 80 && !gridReady(); i++) page.waitForTimeout(250);
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < 32 && rows.isEmpty(); i++) { rows = existing(); if (rows.isEmpty()) page.waitForTimeout(250); }
        return rows;
    }

    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    private static boolean taken(List<String[]> rows, String code, String remark) {
        for (String[] r : rows) {
            if (!code.isEmpty() && r[0].trim().equalsIgnoreCase(code.trim())) return true;
            if (!remark.isEmpty() && r[1].trim().equalsIgnoreCase(remark.trim())) return true;
        }
        return false;
    }

    /**
     * Choose a {code, remark, print name, group} that collides with no existing row, ignoring case: the first unused chart of {@code POOL} (skipping {@code skip} of them for a retry; once the pool is used up
     * a numbered chart is made) and the code {@code MT} + three digits, one above the largest {@code MT} number in use, stepped past any clash. Sets the {@code last*} fields.
     */
    public String[] pickNew(List<String[]> rows, int skip) {
        String remark = "", print = "", group = "";
        int seen = 0;
        for (int round = 0; round < 50 && remark.isEmpty(); round++) {
            for (String[] p : POOL) {
                String cand = round == 0 ? p[0] : p[0] + " " + (round + 1);
                if (taken(rows, "", cand)) continue;
                if (seen++ < skip) continue;
                remark = cand;
                print = round == 0 ? p[1] : p[1] + " " + (round + 1);
                group = p[2];
                break;
            }
        }
        int max = 0;
        Pattern rx = Pattern.compile("^MT(\\d+)$", Pattern.CASE_INSENSITIVE);
        for (String[] r : rows) { Matcher m = rx.matcher(r[0].trim()); if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1))); }
        String code = "";
        int seenCode = 0;
        for (int n = max + 1; n < 100000 && code.isEmpty(); n++) {
            String cand = "MT" + String.format("%03d", n);
            if (taken(rows, cand, "")) continue;
            if (seenCode++ < skip) continue;
            code = cand;
        }
        lastCode = code;
        lastRemark = remark;
        lastPrintName = print;
        lastGroup = group;
        return new String[]{code, remark, print, group};
    }

    // ---- add form ----------------------------------------------------------------------------------------------

    /** Click Add on the list and wait for the form. */
    public boolean clickAdd() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('a,button')].filter(vis).find(x=>/^AddMonitoringTemplate/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__mtAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add button is not on screen"); return false; }
        try { page.locator("#__mtAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__mtAdd'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        if (!onForm()) problems.add("the Monitoring Template form did not open (" + page.url() + ")");
        return ok();
    }

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private boolean type(String ng, String value, String what) {
        Locator in = page.locator("input[ng-model='" + ng + "']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(30));
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
        waitForAngular(400);
        String box = String.valueOf(page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }", ng)).trim();
        if (!value.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + value + "\"");
        if (!value.equals(model(ng))) problems.add("the " + what + " is not held by the model");
        return ok();
    }

    public boolean enterCode(String code) { problems.clear(); return type(CODE_NG, code, "Code"); }

    public boolean enterRemark(String remark) { problems.clear(); return type(REMARK_NG, remark, "Remark"); }

    public boolean enterPrintName(String printName) { problems.clear(); return type(PRINT_NG, printName, "Print Name"); }

    // ---- the Group table -----------------------------------------------------------------------------------------------

    /** The groups offered in the form's Select | Group table, in order. */
    public List<String> groupNames() {
        Object r = page.evaluate("(n) => {" + JS + " return [...document.querySelectorAll(\"input[ng-model='\"+n+\"']\")].map(e=>norm((e.closest('tr')||{}).textContent)); }", GROUP_NG);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The groups whose box is ticked. */
    public List<String> tickedGroups() {
        Object r = page.evaluate("(n) => {" + JS + " return [...document.querySelectorAll(\"input[ng-model='\"+n+\"']\")].filter(e=>e.checked).map(e=>norm((e.closest('tr')||{}).textContent)); }", GROUP_NG);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Tick the box of this group (the first group if it is not offered); leaves the group's name in {@link #lastGroup}. Returns the group ticked, "" if none could be. */
    public String tickGroup(String preferred) {
        problems.clear();
        List<String> names = groupNames();
        if (names.isEmpty()) { problems.add("the Group table offers no group"); return ""; }
        String want = names.contains(preferred) ? preferred : names.get(0);
        Object tagged = page.evaluate("(a) => {" + JS + " const e=[...document.querySelectorAll(\"input[ng-model='\"+a[0]+\"']\")].find(x=>norm((x.closest('tr')||{}).textContent)===a[1]); if(!e) return false; e.id='__mtGroup'; e.scrollIntoView({block:'center'}); return true; }",
                Arrays.asList(GROUP_NG, want));
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the group \"" + want + "\" is not in the table"); return ""; }
        try { page.locator("#__mtGroup").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the box of the group \"" + want + "\" could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__mtGroup'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(500);
        if (!tickedGroups().contains(want)) { problems.add("the group \"" + want + "\" is not ticked after the click (ticked: " + tickedGroups() + ")"); return ""; }
        lastGroup = want;
        return want;
    }

    // ---- save ------------------------------------------------------------------------------------------------------------

    /** Start recording the toasts: their text and their style (toastr gives each toast a class: toast-success / toast-error / toast-warning / toast-info). */
    private void installToastWatch() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__mtToasts=[]; window.__mtKinds=[]; if(window.__mtObs) window.__mtObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim();"
                + "   const t=e.closest('.toast'); const c=t? t.className : ''; const k=/toast-success/.test(c)?'success':/toast-error/.test(c)?'error':/toast-warning/.test(c)?'warning':/toast-info/.test(c)?'info':'';"
                + "   if(m && !window.__mtToasts.includes(m)){ window.__mtToasts.push(m); window.__mtKinds.push(k); } });"
                + " window.__mtObs=new MutationObserver(add); window.__mtObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__mtToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** The style of each toast recorded since the watch started ("success", "error", "warning", "info", "" when the class is unknown). */
    private String watchedKinds() {
        Object r = page.evaluate("() => (window.__mtKinds||[]).join(',')");
        return r == null ? "" : r.toString();
    }

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(fnIUDMonitoringTemplate|AddMonitoringTemplate)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__mtDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__mtDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("MonitoringTemplate.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__mtDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Save, answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared); the server's answer is in {@link #lastSaveStatus} / {@link #lastSaveMessage}. */
    public String saveAndGetToast() {
        problems.clear();
        lastToast = "";
        lastToastKinds = "";
        lastDialog = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^fnIUDMonitoringTemplate/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__mtSave'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Save button is not on screen"); return ""; }
        try { page.locator("#__mtSave").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__mtSave'); if(e) e.removeAttribute('id'); }"); }
        long end = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < end) {
            String d = answerDialog();
            if (!d.isEmpty()) lastDialog = lastDialog.isEmpty() ? d : lastDialog + " > " + d;
            lastToast = watchedToasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(350);
        }
        page.waitForTimeout(800);
        lastToast = watchedToasts();
        lastToastKinds = watchedKinds();
        parseSaveAnswer();
        return lastToast;
    }

    /** After a save the screen should return to the list; if it stays on the form, click Back. Returns true once the list is shown. */
    public boolean returnToList() {
        for (int i = 0; i < 32 && !onList(); i++) page.waitForTimeout(250);
        if (!onList()) {
            Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,a')].filter(vis).find(x=>/^closeForm/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__mtBack'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__mtBack").click(new Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
                page.evaluate("() => { const e=document.getElementById('__mtBack'); if(e) e.removeAttribute('id'); }");
            }
            for (int i = 0; i < 32 && !onList(); i++) page.waitForTimeout(250);
        }
        waitAddButton();
        waitForAngular(1000);
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
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[1]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(200, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    /** The screen refused because the code or remark already exists. */
    public boolean rejectedAsDuplicate() {
        String t = (lastToast + " " + lastSaveMessage).toLowerCase();
        return t.contains("already") || t.contains("exist") || t.contains("duplicate");
    }

    /** A toast of the success style (green, toast-success) appeared after Save. */
    public boolean successToastShown() { return lastToastKinds.contains("success"); }

    /** The toast's TEXT reads as a success message. The server spells several messages wrongly (Succesfully, sucessfully), so those are accepted too. */
    public boolean toastTextIsSuccess() {
        String t = lastToast.toLowerCase();
        return Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ");
    }

    /**
     * THE USER'S RULE: the check is only whether a success toast appears. This screen shows a green success toast whose text is the placeholder "Message Not Found." even though the save worked, so the
     * toast's style counts, not its wording: pass when a success-style toast appeared (or, if the style could not be read, the text is a success message), and the save was not refused as a duplicate.
     */
    public boolean saveSucceeded() {
        boolean toastOk = lastToastKinds.isEmpty() || lastToastKinds.replace(",", "").isEmpty() ? toastTextIsSuccess() : successToastShown();
        return toastOk && !rejectedAsDuplicate();
    }
}
