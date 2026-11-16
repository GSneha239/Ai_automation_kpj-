package com.kpj.pages.AncillaryServices_page.HouseKeeping_page;

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
 * Ancillary Services &gt; House Keeping &gt; <b>Employee Tracking</b> — Page Object ({@code #/EmployeeTracking}).
 *
 * <p>Confirmed live (devhis build v2026.11.15.469). The screen: <b>Employee Name*</b> ({@code #etEmployeeName}, {@code EmployeeTracking.employeeid}, a select2 over a plain select of 1,598 employees whose
 * {@code ng-change GetActivityDetails()} fetches the rows at once), <b>From Date*</b> / <b>To Date*</b> ({@code EmployeeTracking.fromdate|todate}, today; sent with the fetch and required by OK), <b>Search</b>
 * ({@code GetActivityDetails()} again), the <b>Activities Assigned</b> table (Activity | Schedule Template Name | <b>Status</b> | <b>Remark</b> | Completed) and <b>OK</b> ({@code #submitid},
 * {@code IUDActivityDetails()}) / Clear ({@code onclear()}).</p>
 *
 * <p>Every row is an object {@code link} of the scope's {@code linklist} (fetched by {@code POST /api/EmployeeTracking/Fetch {fromdate, todate, employeeid, ExecFlag:'GetEmployeeTrackingSchedule'}}: id, templatename,
 * activityname, scheduledate, activitystatus, remark, completed ...) with its OWN inline controls: a Status select ({@code link.activitystatus}: Awaiting inspection, Cancelled, Completed, In Progress, Not completed,
 * On hold, Pending, Rework required, Verified by supervisor, some in numbered variants), a Remark textarea ({@code link.remark}) and a Completed tick ({@code link.completed}); they are disabled once a row is
 * completed. <b>OK</b> needs an employee, both dates and at least one row, then sends the WHOLE row list ({@code ActivityDetailsList2}, {@code ExecFlag UPDATEEmployeeTrackingSchedule}); on ResultStatus 1 it shows
 * the saved toast, clears the employee ({@code onclear()}) and reloads the route.</p>
 *
 * <p>Most employees have no schedule and return no rows; the dropdown starts with "Sancy Admin", whose rows come from earlier "AutoTest Template" schedules. {@link #selectEmployeeWithEditableRows} loops through the
 * employees until one has an editable row: first the dropdown in order (each choice makes the screen's own fetch), then - when none of the first ones has - a read-only scan of the remaining employees through
 * the same call to find those that do.</p>
 *
 * <p>Saving is REAL: the row's status and remark are stored for the employee.</p>
 */
public class EmployeeTracking extends BasePage {

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
            String post = r.request().postData() == null ? "" : r.request().postData();
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body, post};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public EmployeeTracking(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastEmployee = "", lastEmployeeId = "", lastToast = "", lastSaveMessage = "", lastDialog = "", lastSaveRequestInfo = "";
    public String lastRowActivity = "", lastRowTemplate = "", lastRowId = "", lastStatus = "", lastStatusId = "", lastRemark = "";
    public int lastSaveStatus = -1;
    /** One line per employee tried by {@link #selectEmployeeWithEditableRows}: "name -> n row(s), m editable". */
    public final List<String> tried = new ArrayList<>();

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const tbl=()=>[...document.querySelectorAll('table')].find(x=>/Schedule Template Name/.test(x.textContent));"
          + "const rowsOf=()=>{ const t=tbl(); return t? [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('select')) : []; };";

    private static final String HREF = "#/employeetracking";
    private static final String PARENT = "Ancillary Services", GROUP = "House Keeping";

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

    /** Ancillary Services &gt; House Keeping &gt; Employee Tracking. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Ancillary Services')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !linkVisible()) clickInner(PARENT, GROUP);
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__etLink'; }", HREF);
                try { page.locator("#__etLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("EmployeeTracking.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__etLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !employeeBoxReady(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    private boolean employeeBoxReady() {
        Object r = page.evaluate("() => { const e=document.getElementById('etEmployeeName'); return !!e && [...e.options].some(o=>o.value); }");
        return Boolean.TRUE.equals(r);
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/employeetracking/?$") && employeeBoxReady(); }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__etToasts=[]; if(window.__etObs) window.__etObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__etToasts.includes(m)) window.__etToasts.push(m); });"
                + " window.__etObs=new MutationObserver(add); window.__etObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__etToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- the employees -----------------------------------------------------------------------------------------------

    /** The employees the dropdown offers, in order, as {value, text} (the placeholder is left out). */
    public List<String[]> employeeOptions() {
        Object r = page.evaluate("() => {" + JS + " const e=document.getElementById('etEmployeeName'); if(!e) return []; return [...e.options].filter(o=>o.value && norm(o.textContent) && !/^-*\\s*select/i.test(norm(o.textContent))).map(o=>[o.value, norm(o.textContent)]); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    private static String digits(String v) { return v.replaceAll("[^0-9]", ""); }

    /** The dates the screen sends with its fetch and requires for OK. */
    public String dateBoxes() {
        Object r = page.evaluate("() => { const f=n=>{ const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }; return f('EmployeeTracking.fromdate')+' - '+f('EmployeeTracking.todate'); }");
        return r == null ? "" : r.toString();
    }

    /** Select the employee with this option value (real change events; the select carries {@code ng-change GetActivityDetails()}), wait for the screen's own fetch and return the number of rows now shown. -1 when it failed. */
    public int selectEmployeeByValue(String value, String text) {
        problems.clear();
        int rows = -1;
        try {
            page.waitForResponse(x -> x.url().contains("/EmployeeTracking/Fetch") && String.valueOf(x.request().postData()).contains("GetEmployeeTrackingSchedule"), new Page.WaitForResponseOptions().setTimeout(25000),
                    () -> page.evaluate("(v) => { const e=document.getElementById('etEmployeeName'); const i=[...e.options].findIndex(o=>o.value===v); if(i<0) return false; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                            + " try{ angular.element(e).triggerHandler('change'); }catch(x){} if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} } return true; }", value));
        } catch (Exception e) { problems.add("selecting \"" + text + "\" made no fetch: " + e.getMessage().split("\n")[0]); return -1; }
        waitForAngular(900);
        for (int i = 0; i < 12 && rowCount() < 0; i++) page.waitForTimeout(250);
        rows = rowCount();
        lastEmployee = text;
        lastEmployeeId = digits(value);
        String model = scopeValue("EmployeeTracking.employeeid");
        if (!digits(value).equals(digits(model))) problems.add("the employee is not held by the model after selecting \"" + text + "\" (model \"" + model + "\")");
        return rows;
    }

    private String scopeValue(String expr) {
        Object v = page.evaluate("(x) => { try{ const e=document.getElementById('etEmployeeName'); const m=angular.element(e).scope().$eval(x); return m==null? '' : String(m); }catch(err){ return ''; } }", expr);
        return v == null ? "" : v.toString().trim();
    }

    /** The number of rows the Activities Assigned table shows (those with their own Status select). */
    public int rowCount() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf().length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** The rows as {activity, template, status shown, remark, completed (true/false), editable (true/false)}. A row is editable when its Status select and Remark box are enabled and it is not completed. */
    public List<String[]> rows() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf().map(r=>{ const t=[...r.querySelectorAll('td')].map(x=>norm(x.textContent)); const s=r.querySelector('select'); const m=r.querySelector('textarea,input[type=text]'); const c=r.querySelector('input[type=checkbox]');"
                + " const st=s && s.selectedIndex>=0 ? norm(s.options[s.selectedIndex].textContent) : ''; const done=!!(c && c.checked);"
                + " return [t[0]||'', t[1]||'', /^-*\\s*select/i.test(st)? '' : st, m? m.value : '', String(done), String(!!s && !s.disabled && !!m && !m.disabled && !done)]; }); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) {
            List<?> c = (List<?>) row;
            String[] v = new String[6];
            for (int i = 0; i < 6; i++) v[i] = String.valueOf(c.get(i));
            out.add(v);
        }
        return out;
    }

    public int editableRows() {
        int n = 0;
        for (String[] r : rows()) if ("true".equals(r[5])) n++;
        return n;
    }

    /**
     * Loop through the employees until one has an editable row. First the dropdown in ITS ORDER, at most {@code maxUiTries} employees (each choice makes the screen's own fetch); when none of them has, a
     * read-only scan (the same fetch, asked through the page's own http service, up to {@code scanMs}) finds the other employees that have rows, and those are selected in the UI one by one. Each employee tried is
     * recorded in {@link #tried}. Returns true when the table now shows an employee with at least one editable row.
     */
    public boolean selectEmployeeWithEditableRows(int maxUiTries, long scanMs) {
        problems.clear();
        tried.clear();
        List<String[]> opts = employeeOptions();
        if (opts.isEmpty()) { problems.add("the Employee Name dropdown has no employees"); return false; }
        int ui = Math.min(maxUiTries, opts.size());
        List<String> triedValues = new ArrayList<>();
        for (int i = 0; i < ui; i++) {
            if (tryEmployee(opts.get(i))) return true;
            triedValues.add(opts.get(i)[0]);
        }
        // fallback: a read-only scan for employees that have rows at all
        List<String[]> withRows = scanEmployeesWithRows(opts, ui, 6, scanMs);
        tried.add("(read-only scan of the other " + (opts.size() - ui) + " employees found " + withRows.size() + " with rows" + (withRows.isEmpty() ? "" : ": " + withRows.stream().map(o -> o[1]).toList()) + ")");
        for (String[] o : withRows) if (!triedValues.contains(o[0]) && tryEmployee(o)) return true;
        problems.clear();
        problems.add("none of the employees tried has an editable row (" + tried.size() + " entries in the log)");
        return false;
    }

    private boolean tryEmployee(String[] o) {
        int rows = selectEmployeeByValue(o[0], o[1]);
        int editable = rows > 0 ? editableRows() : 0;
        tried.add(o[1] + " -> " + Math.max(rows, 0) + " row(s), " + editable + " editable");
        problems.clear();
        return rows > 0 && editable > 0;
    }

    /** Ask the screen's own fetch (read-only) for the employees from {@code from} on until {@code maxFound} have rows or {@code ms} has passed; returns their {value, text}. */
    private List<String[]> scanEmployeesWithRows(List<String[]> opts, int from, int maxFound, long ms) {
        List<String[]> found = new ArrayList<>();
        StringBuilder csv = new StringBuilder();
        for (int i = from; i < opts.size(); i++) csv.append(i == from ? "" : ",").append(digits(opts.get(i)[0]));
        Object r = page.evaluate("(a) => new Promise(async res => { try { const h=angular.element(document.body).injector().get('$http'); const ids=a[0].split(',').filter(Boolean); const found=[]; const t0=Date.now();"
                + " for (const id of ids) { if (found.length>=a[1] || Date.now()-t0>a[2]) break; try { const r=await h.post('/api/EmployeeTracking/Fetch', {employeeid:parseInt(id,10), ExecFlag:'GetEmployeeTrackingSchedule'}); if ((r.data||[]).length) found.push(id); } catch(er) {} }"
                + " res(found.join(',')); } catch(x){ res(''); } })", Arrays.asList(csv.toString(), maxFound, ms));
        String s = r == null ? "" : r.toString();
        if (s.isEmpty()) return found;
        for (String id : s.split(",")) for (String[] o : opts) if (digits(o[0]).equals(id)) { found.add(o); break; }
        return found;
    }

    // ---- one row: Status and Remark -----------------------------------------------------------------------------------------

    /** The first editable row (0-based among the rows shown), -1 when there is none. */
    public int firstEditableRow() {
        List<String[]> rs = rows();
        for (int i = 0; i < rs.size(); i++) if ("true".equals(rs.get(i)[5])) return i;
        return -1;
    }

    /** Tag the row's Status select / Remark box and read the row's own data (its id etc.). */
    private boolean tagRow(int index) {
        Object r = page.evaluate("(i) => {" + JS + " const r=rowsOf()[i]; if(!r) return ''; const s=r.querySelector('select'); const m=r.querySelector('textarea,input[type=text]'); s.id='__etStatus'; m.id='__etRemark'; r.scrollIntoView({block:'center'});"
                + " let ac='', tp=''; try{ const l=angular.element(r).scope().link; ac=String(l.activityname); tp=String(l.housekeepingscheduletemplate); }catch(e){} return String(i)+'\\u0001'+ac+'\\u0001'+tp; }", index);
        String s = r == null ? "" : r.toString();
        if (s.isEmpty()) return false;
        String[] p = s.split("\u0001", -1);
        lastRowId = p.length > 0 ? p[0] : "";
        lastRowActivity = p.length > 1 ? p[1] : "";
        lastRowTemplate = p.length > 2 ? p[2] : "";
        return true;
    }

    private void untag() { page.evaluate("() => { ['__etStatus','__etRemark'].forEach(i=>{ const e=document.getElementById(i); if(e) e.removeAttribute('id'); }); }"); }

    /** The Status choices of the row (placeholder excluded). */
    public List<String> statusOptions(int index) {
        Object r = page.evaluate("(i) => {" + JS + " const r=rowsOf()[i]; const s=r&&r.querySelector('select'); return s? [...s.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*\\s*select/i.test(t)) : []; }", index);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Select the Status of the row (the first of the preferred names that is offered, else the first option) with a real selection, and check the row's own data holds it. */
    public String selectStatus(int index, List<String> preferred) {
        problems.clear();
        List<String> all = statusOptions(index);
        if (all.isEmpty()) { problems.add("the row has no Status choices"); return ""; }
        String pick = all.get(0);
        outer:
        for (String p : preferred) for (String a : all) if (a.equalsIgnoreCase(p)) { pick = a; break outer; }
        if (!tagRow(index)) { problems.add("row " + (index + 1) + " is not on screen"); return ""; }
        try { page.locator("#__etStatus").selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(pick), new Locator.SelectOptionOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Status \"" + pick + "\" could not be selected: " + e.getMessage().split("\n")[0]); untag(); return ""; }
        waitForAngular(500);
        Object shown = page.evaluate("(i) => {" + JS + " const s=rowsOf()[i].querySelector('select'); return norm(s.options[s.selectedIndex].textContent); }", index);
        String model = rowField(index, "activitystatus");
        untag();
        if (shown == null || !pick.equals(shown.toString())) { problems.add("the row's Status shows \"" + shown + "\" instead of \"" + pick + "\""); return ""; }
        if (model.isEmpty()) { problems.add("the row's data holds no status after selecting \"" + pick + "\""); return ""; }
        lastStatus = pick;
        lastStatusId = model;
        return pick;
    }

    /** Type the row's Remark and check the box and the row's own data hold it. */
    public boolean enterRemark(int index, String text) {
        problems.clear();
        lastRemark = text;
        if (!tagRow(index)) { problems.add("row " + (index + 1) + " is not on screen"); return false; }
        Locator m = page.locator("#__etRemark").first();
        try {
            m.click(new Locator.ClickOptions().setTimeout(5000));
            m.fill(text, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the remark could not be typed: " + e.getMessage().split("\n")[0]); untag(); return false; }
        waitForAngular(400);
        Object box = page.evaluate("() => { const e=document.getElementById('__etRemark'); return e? e.value : ''; }");
        String model = rowField(index, "remark");
        untag();
        if (box == null || !text.equals(box.toString())) problems.add("the Remark box shows \"" + box + "\" instead of the remark typed");
        else if (!text.equals(model)) problems.add("the remark is not held by the row's data (\"" + model + "\")");
        return ok();
    }

    /** A property of the row's own data object ({@code link}), "" when unset. */
    public String rowField(int index, String field) {
        Object r = page.evaluate("(a) => {" + JS + " try{ const r=rowsOf()[a[0]]; const v=angular.element(r).scope().link[a[1]]; return v==null? '' : String(v); }catch(e){ return ''; } }", Arrays.asList(String.valueOf(index), field));
        return r == null ? "" : r.toString();
    }

    // ---- OK ----------------------------------------------------------------------------------------------------------------

    /** If a confirmation dialog (any fixed overlay with a Yes / OK / Confirm button that is not the screen's own OK) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(IUDActivityDetails|onclear)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__etDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__etDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("EmployeeTracking.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__etDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click OK, answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String clickOkAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        lastSaveRequestInfo = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=document.getElementById('submitid') || [...document.querySelectorAll('button')].filter(vis).find(x=>/^IUDActivityDetails/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='submitid'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the OK button is not on screen"); return ""; }
        try { page.locator("#submitid").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the OK button could not be clicked"); return ""; }
        long end = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < end) {
            String d = answerDialog();
            if (!d.isEmpty()) lastDialog = lastDialog.isEmpty() ? d : lastDialog + " > " + d;
            lastToast = toasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(350);
        }
        page.waitForTimeout(1000);
        lastToast = toasts();
        parseSaveAnswer();
        return lastToast;
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
        int rowsSent = 0;
        Matcher rm = Pattern.compile("\"activityname\"").matcher(c[4]);
        while (rm.find()) rowsSent++;
        lastSaveRequestInfo = c[2].replaceAll("^https?://[^/]+", "") + " with " + c[4].length() + " characters of JSON (" + rowsSent + " row(s) with an activity name, ExecFlag "
                + (c[4].contains("UPDATEEmployeeTrackingSchedule") ? "UPDATEEmployeeTrackingSchedule" : "?") + ")";
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
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !t.contains("unexpected");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    /** True once the screen has cleared the employee after a successful OK (it calls {@code onclear()} and reloads the route). */
    public boolean employeeCleared(int ms) {
        long end = System.currentTimeMillis() + ms;
        do {
            if (scopeValue("EmployeeTracking.employeeid").isEmpty() && rowCount() == 0) return true;
            page.waitForTimeout(300);
        } while (System.currentTimeMillis() < end);
        return scopeValue("EmployeeTracking.employeeid").isEmpty() && rowCount() == 0;
    }

    // ---- what the server holds ---------------------------------------------------------------------------------------------------

    /**
     * What the SERVER holds for the row at this position of this employee's list (the rows carry no id of their own - every {@code id} is 0), read with the call the employee dropdown's change makes (employee id and
     * ExecFlag only): {status value, remark, completed, number of rows holding this remark, "true"/"false" = the row at that position is the same activity and template}; null when the answer could not be read.
     */
    public String[] savedRow(String employeeId, int index, String activity, String template, String remark) {
        Object r = page.evaluate("(a) => new Promise(res => { try { const h=angular.element(document.body).injector().get('$http');"
                + " h.post('/api/EmployeeTracking/Fetch', {employeeid:parseInt(a[0],10), ExecFlag:'GetEmployeeTrackingSchedule'}).then(x=>{ const d=x.data||[]; const y=d[parseInt(a[1],10)];"
                + " if(!y){ res('NOROW'); return; } const s=v=>v==null? '' : String(v); const n=d.filter(v=>s(v.remark)===a[4]).length;"
                + " res(JSON.stringify([s(y.activitystatus), s(y.remark), s(y.completed), String(n), String(s(y.activityname)===a[2] && s(y.housekeepingscheduletemplate)===a[3])])); }, e=>res('ERR '+(e&&e.status))); } catch(x){ res('ERR '+x); } })",
                Arrays.asList(employeeId, String.valueOf(index), activity, template, remark));
        String s = r == null ? "" : r.toString();
        if (s.isEmpty() || s.equals("NOROW") || s.startsWith("ERR")) return null;
        try {
            JsonArray a = JsonParser.parseString(s).getAsJsonArray();
            String[] v = new String[5];
            for (int i = 0; i < 5; i++) { JsonElement e = a.get(i); v[i] = e.isJsonNull() ? "" : e.getAsString(); }
            return v;
        } catch (Exception e) { return null; }
    }
}
