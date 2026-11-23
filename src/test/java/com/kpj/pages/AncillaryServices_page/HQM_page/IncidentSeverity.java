package com.kpj.pages.AncillaryServices_page.HQM_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ancillary Services &gt; HQM &gt; <b>Incident Severity</b> — Page Object ({@code #/INCSEV}).
 *
 * <p>Confirmed live (devhis build v2026.11.12.441). A shared "common master" screen with the entry form INLINE above the list (there is no Add button): a locked <b>Form Name*</b> select
 * ({@code INCSEV}, "Incident Severity"), <b>Code*</b> ({@code commonmaster.Code}, max 15), <b>Remark*</b> ({@code commonmaster.Description}, max 250), <b>MIMS GUID</b> ({@code .mimsGUID}),
 * <b>MIMS Description</b> ({@code .mimsDescription}) and <b>MIMS Type</b> ({@code .mimsTypeId}, a select2 over a plain select: GenericItem, GGPI, Product, Molecule, SubstanceClass = ids 1-5), then
 * <b>Submit</b> ({@code type=submit}, {@code ng-show="canSaveCommonMaster()"}, no click handler: found by its text) and Clear ({@code resetForm()}). The list is a ui-grid ({@code #grid1}: Code | Remark | Status
 * switch | Edit; data fields {@code Code}, {@code Description}, {@code Status}, {@code mimsGUID}, {@code mimsDescription}, {@code mimsTypeId}); unlike Quality Indicator / Image Tag it FILLS: it held the five
 * severity levels {@code 000 N/A, 001 Low, 002 Moderate, 003 High, 004 Extreme / Very significant}.</p>
 *
 * <p>The new code copies the existing format (three digits, one above the largest: {@code 005}) and the remark is another real severity level, chosen so that neither collides with an existing row ignoring case.
 * Saving is REAL: the severity is added to the master and stays (it can only be made inactive with its Status switch, which this page never touches).</p>
 */
public class IncidentSeverity extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body,
                    r.request().postData() == null ? "" : r.request().postData()};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public IncidentSeverity(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastCode = "", lastRemark = "", lastGuid = "", lastMimsDescription = "", lastMimsType = "", lastToast = "", lastSaveMessage = "", lastFormName = "", lastSaveRequest = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String CODE_NG = "commonmaster.Code", DESC_NG = "commonmaster.Description", GUID_NG = "commonmaster.mimsGUID", MIMSDESC_NG = "commonmaster.mimsDescription", MIMSTYPE_NG = "commonmaster.mimsTypeId";
    private static final String HREF = "#/incsev";
    private static final String PARENT = "Ancillary Services", GROUP = "HQM";

    /** Real incident severity levels above the master's own scale (N/A, Low, Moderate, High, Extreme / Very significant); the master keeps whatever is saved, so no "Auto…" junk. */
    private static final String[] REMARKS = {
            "Catastrophic / Sentinel event", "Critical / Life threatening", "Major / Serious harm", "Minor / No harm"
    };

    /** Code series used while the table has no code to copy. */
    private static final String FALLBACK_PREFIX = "";
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

    /** Ancillary Services &gt; HQM &gt; Incident Severity. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again). */
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__isLink'; }", HREF);
                try { page.locator("#__isLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("IncidentSeverity.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__isLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("(n) => !!document.querySelector(\"[ng-model='\"+n+\"']\")", CODE_NG, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("IncidentSeverity.nav: the Code box is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/incsev/?$"); }

    /** The locked Form Name select's text ("Incident Severity"), "" when it is not there. */
    public String formName() {
        Object r = page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(s=>/GetGridData/.test(s.getAttribute('ng-change')||'')); if(!e) return '';"
                + " const o=e.options[e.selectedIndex]; return (o? o.textContent : e.value).replace(/\\s+/g,' ').trim(); }");
        lastFormName = r == null ? "" : r.toString();
        return lastFormName;
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    /** Every severity the list holds (all pages), as {code, remark, status}; read from the grid's data array. Empty when the grid has none. */
    public List<String[]> existing() {
        Object r = page.evaluate("() => { try{ const g=document.getElementById('grid1'); const d=angular.element(g).scope().gridOptions.data; const s=v=>v==null? '' : String(v);"
                + " return d.map(x=>[s(x.Code), s(x.Description), s(x.Status)]); }catch(e){ return []; } }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2))}); }
        return out;
    }

    public boolean gridReady() {
        Object r = page.evaluate("() => { try{ const g=document.getElementById('grid1'); return Array.isArray(angular.element(g).scope().gridOptions.data); }catch(e){ return false; } }");
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

    /** The rows the table SHOWS on screen as {Code, Remark}; (the Status switch cell has no text worth reading). */
    public List<String[]> tableRows() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#grid1 .ui-grid-canvas .ui-grid-row')].filter(r=>r.textContent.trim().length>0)"
                + ".map(r=>{ const c=[...r.querySelectorAll('.ui-grid-cell')].map(x=>norm(x.textContent)); return [c[0]||'', c[1]||'']; }); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    /** Waits up to {@code ms} for the table to SHOW a row with this code and remark; returns that row ("Code | Remark") or "" when it never appears. */
    public String awaitRowShown(String code, String remark, int ms) {
        long end = System.currentTimeMillis() + ms;
        do {
            for (String[] r : tableRows()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(remark)) return r[0] + " | " + r[1];
            page.waitForTimeout(400);
        } while (System.currentTimeMillis() < end);
        return "";
    }

    /** The grid's own data record for this code as text (Code, Description, Status, MIMS GUID, MIMS Description, MIMS Type id), "" when there is none: proves what the server stored, MIMS fields included. */
    public String dataRowFor(String code) {
        Object r = page.evaluate("(c) => { try{ const g=document.getElementById('grid1'); const d=angular.element(g).scope().gridOptions.data; const x=d.find(v=>String(v.Code).toUpperCase()===c.toUpperCase()); if(!x) return '';"
                + " return 'Code='+x.Code+'; Description='+x.Description+'; Status='+x.Status+'; mimsGUID='+x.mimsGUID+'; mimsDescription='+x.mimsDescription+'; mimsTypeId='+x.mimsTypeId; }catch(e){ return ''; } }", code);
        return r == null ? "" : r.toString();
    }

    /**
     * What the SERVER holds for the row with this code, read with the call the Edit pencil makes ({@code POST /api/CommonMaster/fetch {ExecFlag:'SINGLEVAL', MasterCodeFor:'INCSEV', CommonMasterPkID:<id>}}),
     * as {id, code, description, mimsGUID, mimsDescription, mimsTypeId}; null when the row or the answer could not be read. A blank/null MIMS value means the server did not store what the form sent.
     */
    public String[] serverRecord(String code) {
        Object r = page.evaluate("(c) => new Promise(res => { try { const g=document.getElementById('grid1'); const d=angular.element(g).scope().gridOptions.data; const x=d.find(v=>String(v.Code).toUpperCase()===c.toUpperCase());"
                + " if(!x){ res('NOROW'); return; } const f=angular.element(document.body).injector().get('commonMasterFactory');"
                + " f.fetchCommonMaster({ExecFlag:'SINGLEVAL', MasterCodeFor:'INCSEV', CommonMasterPkID:x.CommonMasterPkID}).then(a=>{ const y=(a.data||[])[0]||{}; const s=v=>v==null? '' : String(v);"
                + " res(JSON.stringify([s(y.CommonMasterPkID||x.CommonMasterPkID), s(y.Code), s(y.Description), s(y.mimsGUID), s(y.mimsDescription), s(y.mimsTypeId)])); }, e=>res('ERR '+(e&&e.status))); } catch(x){ res('ERR '+x); } })", code);
        String s = r == null ? "" : r.toString();
        if (s.isEmpty() || s.equals("NOROW") || s.startsWith("ERR")) return null;
        try {
            com.google.gson.JsonArray a = com.google.gson.JsonParser.parseString(s).getAsJsonArray();
            String[] v = new String[6];
            for (int i = 0; i < 6; i++) v[i] = a.get(i).isJsonNull() ? "" : a.get(i).getAsString();
            return v;
        } catch (Exception e) { return null; }
    }

    private static boolean taken(List<String[]> rows, String code, String description) {
        for (String[] r : rows) {
            if (!code.isEmpty() && r[0].trim().equalsIgnoreCase(code.trim())) return true;
            if (!description.isEmpty() && r[1].trim().equalsIgnoreCase(description.trim())) return true;
        }
        return false;
    }

    /**
     * Choose a Code and a Remark that collide with no existing row, ignoring case. The remark is the first unused one of {@code REMARKS} (shifted by {@code skip} for a retry; a numeral is added once the pool is
     * used up). The code COPIES THE EXISTING FORMAT: the most common "optional letters + digits" shape among the codes (here no letters and three digits: 000-004), numbered one above the largest in use
     * ({@code 005}), stepped past any clash; with nothing to copy (an empty table) it starts at {@code 001}. Sets {@link #lastCode} and {@link #lastRemark}.
     */
    public String[] pickNew(List<String[]> rows, int skip) {
        String description = "";
        int seen = 0;
        for (int round = 0; round < 50 && description.isEmpty(); round++) {
            for (String base : REMARKS) {
                String cand = round == 0 ? base : base + " " + (round + 1);
                if (taken(rows, "", cand)) continue;
                if (seen++ < skip) continue;
                description = cand;
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
        lastRemark = description;
        lastCode = code;
        return new String[]{code, description};
    }

    // ---- the inline form -----------------------------------------------------------------------------------------

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
            in.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(15));
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
        waitForAngular(400);
        String box = String.valueOf(page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }", ng)).trim();
        if (!value.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + value + "\"");
        if (!value.equals(model(ng))) problems.add("the " + what + " is not held by the model");
        return ok();
    }

    public boolean enterCode(String code) { problems.clear(); lastCode = code; return type(CODE_NG, code, "Code"); }

    public boolean enterRemark(String remark) { problems.clear(); lastRemark = remark; return type(DESC_NG, remark, "Remark"); }

    public boolean enterMimsGuid(String guid) { problems.clear(); lastGuid = guid; return type(GUID_NG, guid, "MIMS GUID"); }

    public boolean enterMimsDescription(String text) { problems.clear(); lastMimsDescription = text; return type(MIMSDESC_NG, text, "MIMS Description"); }

    /** The MIMS Type choices (placeholder excluded). */
    public List<String> mimsTypeOptions() {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))).map(o=>norm(o.textContent)); }", MIMSTYPE_NG);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Select a MIMS Type (the first of the preferred names, else the first option) with real change events, and check the model holds it. Returns the text selected, "" when it could not be. */
    public String selectMimsType(List<String> preferred) {
        problems.clear();
        List<String> all = mimsTypeOptions();
        if (all.isEmpty()) { problems.add("the MIMS Type dropdown has no options"); return ""; }
        String pick = all.get(0);
        outer:
        for (String p : preferred) for (String a : all) if (a.equalsIgnoreCase(p)) { pick = a; break outer; }
        Object r = page.evaluate("(a) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+a[0]+\"']\"); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()===a[1].toLowerCase()); if(i<0) return '';"
                + " const picked=norm(e.options[i].textContent); e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} }"
                + " return picked; }", Arrays.asList(MIMSTYPE_NG, pick));
        String got = r == null ? "" : r.toString();
        waitForAngular(600);
        if (got.isEmpty()) { problems.add("the MIMS Type \"" + pick + "\" could not be selected"); return ""; }
        if (model(MIMSTYPE_NG).isEmpty()) problems.add("the MIMS Type is not held by the model after selecting \"" + got + "\"");
        lastMimsType = got;
        return ok() ? got : "";
    }

    // ---- submit ------------------------------------------------------------------------------------------------

    /** Remove the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    private void installToastWatch() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove()); window.__isToasts=[]; if(window.__isObs) window.__isObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__isToasts.includes(m)) window.__isToasts.push(m); });"
                + " window.__isObs=new MutationObserver(add); window.__isObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__isToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Submit (the form's own button — not one in the grid) and wait for the toast. Returns the toast text ("" if none appeared); the server's answer is in {@link #lastSaveStatus} / {@link #lastSaveMessage}. */
    public String submitAndGetToast() {
        problems.clear();
        lastToast = "";
        lastSaveRequest = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(vis).find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent||x.value))); if(!b) return false; b.id='__isSubmit'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Submit button is not on screen"); return ""; }
        try { page.locator("#__isSubmit").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Submit button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__isSubmit'); if(e) e.removeAttribute('id'); }"); }
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

    private void parseSaveAnswer() {
        lastSaveStatus = -1;
        lastSaveMessage = "";
        String[] c = null;
        synchronized (saveCalls) {
            if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1);
            else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1);
        }
        if (c == null) return;
        lastSaveRequest = c[4].length() > 360 ? c[4].substring(0, 360) + "..." : c[4];
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

    /** The screen refused because the code or description already exists. */
    public boolean rejectedAsDuplicate() {
        String t = (lastToast + " " + lastSaveMessage).toLowerCase();
        return t.contains("already") || t.contains("exist") || t.contains("duplicate");
    }

    public boolean savedByServer() { return lastSaveStatus == 1; }

    /** The toast is a success message AND the server did not refuse the save. The server spells several messages wrongly (Succesfully, sucessfully), so those are accepted too. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !rejectedAsDuplicate();
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
