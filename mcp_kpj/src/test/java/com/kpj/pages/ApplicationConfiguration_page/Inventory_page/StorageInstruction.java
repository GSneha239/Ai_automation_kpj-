package com.kpj.pages.ApplicationConfiguration_page.Inventory_page;

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
 * Application Configuration &gt; Inventory &gt; <b>Storage Instruction</b> — Page Object ({@code #/storageinstruction}).
 *
 * <p>Confirmed live (devhis build v2026.10.28.329). A shared "common master" screen with the entry form INLINE above the list (there is no Add button): a locked <b>Form Name*</b> select
 * ({@code Storage Instruction}), <b>Code*</b> ({@code commonmaster.Code}), <b>Description*</b> ({@code commonmaster.Description}), <b>Submit</b> (a plain button with no handler name; found by its text) and
 * Clear. The list below is a ui-grid ({@code #grid1}: Code, Description, Status switch, Edit; data fields {@code Code}, {@code Description}, {@code Status}) holding 4 rows at the time:
 * {@code 1 = Below 25°C}, {@code 2 = Refrigerate}, {@code 3 = Protect from Light}, {@code 4 = NA}.</p>
 *
 * <p>The new code copies the existing format (the most common prefix + zero-padded number, here no prefix and a single unpadded digit, one above the largest: {@code 5}) and the description is a real
 * storage instruction for medicines in the same short title-case style (for example "Do Not Freeze"), chosen so that neither collides with an existing row ignoring case. Saving is REAL: an instruction
 * is added to the master and stays (it can only be made inactive with its Status switch, which this page never touches).</p>
 *
 * <p>The Inventory group is looked up INSIDE the Application Configuration entry only (Inventory is also a top-level module of the sidebar).</p>
 */
public class StorageInstruction extends BasePage {

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
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public StorageInstruction(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastCode = "", lastDescription = "", lastToast = "", lastSaveMessage = "", lastFormName = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String CODE_NG = "commonmaster.Code", DESC_NG = "commonmaster.Description";
    private static final String HREF = "#/storageinstruction";
    private static final String PARENT = "Application Configuration", GROUP = "Inventory";

    /** Real medicine storage instructions in the master's own short title-case style (it holds "Below 25°C", "Refrigerate", "Protect from Light", "NA"); the master keeps whatever is saved, so no "Auto…" junk. */
    private static final String[] DESCRIPTIONS = {
            "Do Not Freeze", "Keep in a Cool Dry Place", "Protect from Moisture", "Store at Room Temperature", "Keep Tightly Closed", "Store Upright"
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
     * Application Configuration &gt; Inventory &gt; Storage Instruction. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes
     * the group again, and a click on Inventory straight after Application Configuration is lost), so the first group gets a settling pause before the next one is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Application Configuration')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__siLink'; }", HREF);
                try { page.locator("#__siLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("StorageInstruction.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__siLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("(n) => !!document.querySelector(\"[ng-model='\"+n+\"']\")", CODE_NG, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("StorageInstruction.nav: the Code box is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/storageinstruction/?$"); }

    /** The locked Form Name select's text ("Storage Instruction"), "" when it is not there. */
    public String formName() {
        Object r = page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(s=>/GetGridData/.test(s.getAttribute('ng-change')||'')); if(!e) return '';"
                + " const o=e.options[e.selectedIndex]; return (o? o.textContent : e.value).replace(/\\s+/g,' ').trim(); }");
        lastFormName = r == null ? "" : r.toString();
        return lastFormName;
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    /** Every storage instruction the list holds (all pages), as {code, description, status}; read from the grid's data array. Empty when the grid has none. */
    public List<String[]> existing() {
        Object r = page.evaluate("() => { try{ const g=document.getElementById('grid1'); const d=angular.element(g).scope().gridOptions.data; const s=v=>v==null? '' : String(v);"
                + " return d.map(x=>[s(x.Code), s(x.Description), s(x.Status)]); }catch(e){ return []; } }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2))}); }
        return out;
    }

    /** True once the list's grid exists with a data array (it can be empty). */
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

    /** The pager text ("1 - 1 of 1 items"), "" when there is none. */
    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    private static boolean taken(List<String[]> rows, String code, String description) {
        for (String[] r : rows) {
            if (!code.isEmpty() && r[0].trim().equalsIgnoreCase(code.trim())) return true;
            if (!description.isEmpty() && r[1].trim().equalsIgnoreCase(description.trim())) return true;
        }
        return false;
    }

    /**
     * Choose a Code and a Description that collide with no existing row, ignoring case. The description is the first unused one of {@code DESCRIPTIONS} (shifted by {@code skip} for a retry; a numeral is added once the
     * pool is used up). The code COPIES THE EXISTING FORMAT: the most common "optional letters + digits" shape among the codes (here no letters and one unpadded digit: 1), numbered one above the largest in use
     * ({@code 5}), stepped past any clash; with nothing to copy it falls back to {@code 1}. Sets {@link #lastCode} and {@link #lastDescription}.
     */
    public String[] pickNew(List<String[]> rows, int skip) {
        String description = "";
        int seen = 0;
        for (int round = 0; round < 50 && description.isEmpty(); round++) {
            for (String base : DESCRIPTIONS) {
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
        String prefix = best == null ? "" : best.split("\\|", -1)[0];
        int width = best == null ? 1 : Integer.parseInt(best.split("\\|", -1)[1]);
        int num = best == null ? 1 : maxNo.get(best) + 1;
        String code = "";
        int seenCode = 0;
        for (; num < 1_000_000 && code.isEmpty(); num++) {
            String cand = prefix + String.format("%0" + width + "d", num);
            if (taken(rows, cand, "")) continue;
            if (seenCode++ < skip) continue;
            code = cand;
        }
        lastDescription = description;
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
            in.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(25));
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
        waitForAngular(400);
        String box = String.valueOf(page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }", ng)).trim();
        if (!value.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + value + "\"");
        if (!value.equals(model(ng))) problems.add("the " + what + " is not held by the model");
        return ok();
    }

    public boolean enterCode(String code) { problems.clear(); return type(CODE_NG, code, "Code"); }

    public boolean enterDescription(String description) { problems.clear(); return type(DESC_NG, description, "Description"); }

    // ---- submit ------------------------------------------------------------------------------------------------

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    private void installToastWatch() {
        page.evaluate("() => { window.__siToasts=[]; if(window.__siObs) window.__siObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__siToasts.includes(m)) window.__siToasts.push(m); });"
                + " window.__siObs=new MutationObserver(add); window.__siObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__siToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Submit (the form's own button — not one in the grid) and wait for the toast. Returns the toast text ("" if none appeared); the server's answer is in {@link #lastSaveStatus} / {@link #lastSaveMessage}. */
    public String submitAndGetToast() {
        problems.clear();
        lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(vis).find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent||x.value))); if(!b) return false; b.id='__siSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Submit button is not on screen"); return ""; }
        try { page.locator("#__siSubmit").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Submit button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__siSubmit'); if(e) e.removeAttribute('id'); }"); }
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

    /** The toast is a success message AND the server did not refuse the save. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("su(c)?cess|saved|added|created|submitted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !rejectedAsDuplicate();
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
