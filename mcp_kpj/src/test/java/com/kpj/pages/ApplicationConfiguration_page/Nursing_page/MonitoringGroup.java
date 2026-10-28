package com.kpj.pages.ApplicationConfiguration_page.Nursing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application Configuration &gt; Nursing &gt; <b>Monitoring Group</b> — Page Object (list {@code #/MonitoringGroupList}, form {@code #/add-MonitoringGroup}).
 *
 * <p>Confirmed live (devhis build v2026.10.28.329). The list is a ui-grid ({@code #grid1}: Code, Remark, Status switch, Edit; 5 rows per page) fed by {@code POST /api/MonitoringGroup/Fetch}; it was EMPTY at the
 * time (the call answers 200 with {@code []}). An <b>Add</b> button ({@code AddMonitoringGroup()}) opens the form: <b>Code*</b> ({@code MonitoringGroup.Code}), <b>Remark*</b>
 * ({@code MonitoringGroup.Description}), an optional <b>Print Name</b> ({@code MonitoringGroup.PrintName}, left alone) and a <b>Monitoring Service</b> table of tick boxes ({@code Item.IsSelected}; the header
 * box {@code MonitoringGroup.ChkSelectAll} selects all) with the buttons <b>Submit</b> ({@code IDUMonitoringGroup()}) and Back ({@code CancelForm1()}).</p>
 *
 * <p>The written steps are only "enter code, enter remark, submit", so Submit is clicked WITHOUT a service first; only if the app refuses with a message about the service is one real service ticked
 * ({@link #tickFirstRealService()}) and Submit clicked again — the report says so.</p>
 *
 * <p>The new code copies the existing format (most common prefix + zero-padded number, one above the largest; with an empty table it starts at {@code MG001}). The remark is a real kind of monitoring group
 * (for example "Vital Signs"), chosen so that neither collides with an existing row ignoring case. Saving is REAL: the group is added to the master and stays (it can only be made inactive with its Status
 * switch, which this page never touches).</p>
 *
 * <p>The Nursing group is looked up INSIDE the Application Configuration entry only (Nursing is also a top-level module of the sidebar).</p>
 */
public class MonitoringGroup extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public MonitoringGroup(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastCode = "", lastRemark = "", lastToast = "", lastSaveMessage = "", lastTickedService = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String CODE_NG = "MonitoringGroup.Code", REMARK_NG = "MonitoringGroup.Description", SERVICE_NG = "Item.IsSelected";
    private static final String HREF = "#/monitoringgrouplist";
    private static final String PARENT = "Application Configuration", GROUP = "Nursing";

    /** Real kinds of nursing monitoring group (the master keeps whatever is saved, so no "Auto…" junk). */
    private static final String[] REMARKS = {
            "Vital Signs", "Neurological Observation", "Fluid Balance", "Cardiac Monitoring", "Glucose Monitoring", "Pain Assessment"
    };

    /** Used only while the table has no code to copy. */
    private static final String FALLBACK_PREFIX = "MG";
    private static final int FALLBACK_WIDTH = 3;

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

    /** Is the top-level menu entry {@code label} expanded? {@code 1} open, {@code 0} shut, {@code -1} not on screen. */
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
                java.util.Arrays.asList(parent, label));
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickInner(String parent, String label) {
        Object r = page.evaluate("(a) => {" + JS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!pa) return false; const pli=pa.closest('li'); if(!pli) return false;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===a[1]); if(!c) return false; c.click(); return true; }", java.util.Arrays.asList(parent, label));
        return Boolean.TRUE.equals(r);
    }

    /**
     * Application Configuration &gt; Nursing &gt; Monitoring Group. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__mgLink'; }", HREF);
                try { page.locator("#__mgLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("MonitoringGroup.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__mgLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        waitAddButton();
        waitForAngular(1000);
        return onList();
    }

    private void waitAddButton() {
        try {
            page.waitForFunction("() => { const a=[...document.querySelectorAll('a,button')]; return a.some(x=>/^AddMonitoringGroup/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); }", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("MonitoringGroup.nav: the Add button is not ready"); }
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/monitoringgrouplist/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-monitoringgroup/?$")
                && Boolean.TRUE.equals(page.evaluate("(n) => {" + JS + " return !![...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); }", CODE_NG));
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    private static final String GRID =
            "const g=document.querySelector('.ui-grid'); const sc=g? angular.element(g).scope() : null; const go=sc? (sc.gridOptions || (sc.$parent && sc.$parent.gridOptions)) : null;";

    /** Every monitoring group the list holds (all pages), as {code, remark, status}; read from the grid's data array. Empty when the grid has none. */
    public List<String[]> existing() {
        Object r = page.evaluate("() => { try{ " + GRID + " if(!go||!go.data) return []; const s=v=>v==null? '' : String(v);"
                + " const cd=go.columnDefs||[]; const f=(re,def)=>{ const c=cd.find(c=>re.test(String(c.displayName||c.name||c.field||'').trim())); return c? (c.field||c.name||def) : def; };"
                + " const fc=f(/^code/i,'Code'), fd=f(/^(remark|description)/i,'Description'), fs=f(/^status/i,'Status');"
                + " const pick=(x,k,def)=>{ for(const n of [k,def,def.charAt(0).toLowerCase()+def.slice(1)]) if(x[n]!=null) return x[n]; return ''; };"
                + " return go.data.map(x=>[s(pick(x,fc,'Code')), s(pick(x,fd,'Description')), s(pick(x,fs,'Status'))]); }catch(e){ return []; } }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2))}); }
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

    /** The pager text ("1 - 5 of 7 items"), "" when there is none. */
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
     * Choose a Code and a Remark that collide with no existing row, ignoring case. The remark is the first unused kind of {@code REMARKS} (shifted by {@code skip} for a retry; a numeral is added once the pool is
     * used up). The code COPIES THE EXISTING FORMAT: the most common "optional letters + digits" shape among the codes, numbered one above the largest in use, stepped past any clash; with nothing to copy (an
     * empty table) it starts at {@code MG001}. Sets {@link #lastCode} and {@link #lastRemark}.
     */
    public String[] pickNew(List<String[]> rows, int skip) {
        String remark = "";
        int seen = 0;
        for (int round = 0; round < 50 && remark.isEmpty(); round++) {
            for (String base : REMARKS) {
                String cand = round == 0 ? base : base + " " + (round + 1);
                if (taken(rows, "", cand)) continue;
                if (seen++ < skip) continue;
                remark = cand;
                break;
            }
        }
        Map<String, Integer> shapes = new HashMap<>(), maxNo = new HashMap<>();
        Pattern shapeRx = Pattern.compile("^([A-Za-z]*)(\\d+)$");
        for (String[] r : rows) {
            Matcher m = shapeRx.matcher(r[0].trim());
            if (!m.matches()) continue;
            String shape = m.group(1) + "|" + m.group(2).length();
            shapes.merge(shape, 1, Integer::sum);
            maxNo.merge(shape, Integer.parseInt(m.group(2)), Math::max);
        }
        String best = null;
        for (String s : new TreeSet<>(shapes.keySet())) if (best == null || shapes.get(s) > shapes.get(best)) best = s;      // sorted walk: ties come out the same every run
        String prefix = best == null ? FALLBACK_PREFIX : best.split("\\|", -1)[0];
        int width = best == null ? FALLBACK_WIDTH : Integer.parseInt(best.split("\\|", -1)[1]);
        int num = best == null ? 1 : maxNo.get(best) + 1;
        String code = "";
        int seenCode = 0;
        for (; num < 1_000_000 && code.isEmpty(); num++) {
            String cand = prefix + String.format("%0" + width + "d", num);
            if (taken(rows, cand, "")) continue;
            if (seenCode++ < skip) continue;
            code = cand;
        }
        lastRemark = remark;
        lastCode = code;
        return new String[]{code, remark};
    }

    // ---- add form ----------------------------------------------------------------------------------------------

    /** Click Add on the list and wait for the form. */
    public boolean clickAdd() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('a,button')].filter(vis).find(x=>/^AddMonitoringGroup/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__mgAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add button is not on screen"); return false; }
        try { page.locator("#__mgAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__mgAdd'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        if (!onForm()) problems.add("the Monitoring Group form did not open (" + page.url() + ")");
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

    // ---- the Monitoring Service table ------------------------------------------------------------------------------

    /** The names in the form's Monitoring Service table, in order. */
    @SuppressWarnings("unchecked")
    public List<String> serviceNames() {
        Object r = page.evaluate("(n) => {" + JS + " return [...document.querySelectorAll(\"input[ng-model='\"+n+\"']\")].map(e=>norm((e.closest('tr')||{}).textContent)); }", SERVICE_NG);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /**
     * Tick the first Monitoring Service that is not the placeholder "test" (only used when the app refuses a group without one). Returns the ticked service's name, "" if none could be ticked; sets
     * {@link #lastTickedService}.
     */
    public String tickFirstRealService() {
        problems.clear();
        lastTickedService = "";
        Object name = page.evaluate("(n) => {" + JS + " const e=[...document.querySelectorAll(\"input[ng-model='\"+n+\"']\")].find(x=>!/^test$/i.test(norm((x.closest('tr')||{}).textContent)) && norm((x.closest('tr')||{}).textContent));"
                + " if(!e) return ''; e.id='__mgSvc'; e.scrollIntoView({block:'center'}); return norm(e.closest('tr').textContent); }", SERVICE_NG);
        String n = name == null ? "" : name.toString();
        if (n.isEmpty()) { problems.add("no Monitoring Service is listed to tick"); return ""; }
        try { page.locator("#__mgSvc").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Monitoring Service \"" + n + "\" could not be ticked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__mgSvc'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(400);
        Object checked = page.evaluate("(n) => {" + JS + " const e=[...document.querySelectorAll(\"input[ng-model='\"+n[0]+\"']\")].find(x=>norm((x.closest('tr')||{}).textContent)===n[1]); return !!e && e.checked; }",
                java.util.Arrays.asList(SERVICE_NG, n));
        if (!Boolean.TRUE.equals(checked)) { problems.add("the Monitoring Service \"" + n + "\" is not ticked after the click"); return ""; }
        lastTickedService = n;
        return n;
    }

    // ---- submit ------------------------------------------------------------------------------------------------

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    /** Clear the toasts still on screen first: a refused first Submit leaves its toast up for several seconds and it would otherwise be read as part of the second Submit's answer. */
    private void installToastWatch() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__mgToasts=[]; if(window.__mgObs) window.__mgObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__mgToasts.includes(m)) window.__mgToasts.push(m); });"
                + " window.__mgObs=new MutationObserver(add); window.__mgObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__mgToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Submit and wait for the toast. Returns the toast text ("" if none appeared); the server's answer is in {@link #lastSaveStatus} / {@link #lastSaveMessage}. */
    public String submitAndGetToast() {
        problems.clear();
        lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^IDUMonitoringGroup/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__mgSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Submit button is not on screen"); return ""; }
        try { page.locator("#__mgSubmit").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Submit button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__mgSubmit'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80; i++) {                                           // up to ~20 s for the toast
            lastToast = watchedToasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(250);
        }
        page.waitForTimeout(800);
        lastToast = watchedToasts();
        parseSaveAnswer();
        return lastToast;
    }

    /** After a save the screen should return to the list; if it stays on the form, click Back. Returns true once the list is shown with its grid. */
    public boolean returnToList() {
        for (int i = 0; i < 32 && !onList(); i++) page.waitForTimeout(250);
        if (!onList()) {
            Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,a')].filter(vis).find(x=>/^CancelForm1/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__mgBack'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__mgBack").click(new Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
                page.evaluate("() => { const e=document.getElementById('__mgBack'); if(e) e.removeAttribute('id'); }");
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

    /** The app refused the group because no Monitoring Service is selected. */
    public boolean needsService() {
        String t = (lastToast + " " + lastSaveMessage).toLowerCase();
        return t.contains("service");
    }

    /** The toast is a success message AND the server did not refuse the save. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("su(c)?cess|saved|added|created|submitted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !rejectedAsDuplicate();
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
