package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application Configuration &gt; Admission &gt; <b>MLC Jurisdiction</b> (medico-legal case jurisdiction) — Page Object ({@code #/MLCJURISDICTION}).
 *
 * <p>Confirmed live (devhis build v2026.10.27.303). A shared "common master" screen with the entry form INLINE above the list (there is no Add button): a locked <b>Form Name*</b> select
 * ({@code MLCJURISDICTION}), <b>Code*</b> ({@code commonmaster.Code}), <b>Description*</b> ({@code commonmaster.Description}), <b>Submit</b> and Clear. The list below is a ui-grid
 * ({@code #grid1}: Code, Remark [= Description], Status switch, Edit) holding 3 rows at the time: {@code MLC0331 = Flag State Jurisdiction (Primary Authority…)}, {@code MLCJ080926 = Illegal Case} and
 * {@code MLCJ0809 = Legal Case} — no single code shape (an {@code MLC} + four digits and two {@code MLCJ} + digits codes of different lengths) and title-case descriptions.</p>
 *
 * <p>The new code follows the existing format as far as there is one: the most common prefix ({@code MLCJ}), the most used digit count under it (ties go to the SHORTER one, so the choice is the same
 * every run), numbered one above the largest in use ({@code MLCJ0810}). The description is a real kind of jurisdiction in the same style ("Territorial Jurisdiction", ...), chosen so that neither collides
 * with an existing row ignoring case. Saving is REAL: a jurisdiction is added to the master and stays (it can only be made inactive with its Status switch, which this page never touches).</p>
 */
public class MlcJurisdiction extends BasePage {

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

    public MlcJurisdiction(Page page) {
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
    private static final String HREF = "#/mlcjurisdiction";

    /** Real kinds of jurisdiction in the style of the existing ones ("Legal Case", "Flag State Jurisdiction ..."); the master keeps whatever is saved, so no "Auto…" junk. */
    private static final String[] DESCRIPTIONS = {
            "Territorial Jurisdiction", "Concurrent Jurisdiction", "Criminal Jurisdiction", "Civil Jurisdiction", "Extraterritorial Jurisdiction"
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

    /** Is the menu group with this label expanded? {@code 1} open, {@code 0} shut, {@code -1} not on screen. */
    private int groupState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickGroup(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    /**
     * Application Configuration &gt; Admission &gt; MLC Jurisdiction. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the
     * group again, and a click on Admission straight after Application Configuration is lost), so the first group gets a settling pause before the next one is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Application Configuration')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (groupState("Admission") < 0) {                              // Admission is not on screen: Application Configuration is shut
                    if (groupState("Application Configuration") != 1) clickGroup("Application Configuration");
                    for (int i = 0; i < 48 && groupState("Admission") < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (groupState("Admission") == 0 && !linkVisible()) clickGroup("Admission");
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__mljLink'; }", HREF);
                try { page.locator("#__mljLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("MlcJurisdiction.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__mljLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("(n) => !!document.querySelector(\"[ng-model='\"+n+\"']\")", CODE_NG, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("MlcJurisdiction.nav: the Code box is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/mlcjurisdiction/?$"); }

    /** The locked Form Name select's value ("MLCJURISDICTION"), "" when it is not there. */
    public String formName() {
        Object r = page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(s=>/GetGridData/.test(s.getAttribute('ng-change')||'')); if(!e) return '';"
                + " const o=e.options[e.selectedIndex]; return (o? o.textContent : e.value).replace(/\\s+/g,' ').trim(); }");
        lastFormName = r == null ? "" : r.toString();
        return lastFormName;
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    /** Every jurisdiction the list holds (all pages), as {code, description, status}; read from the grid's data array. Empty when the grid has none. */
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

    /** The pager text ("1 - 12 of 12 items"), "" when there is none. */
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
     * Choose a Code in the FORMAT of the existing ones. The codes may not share one shape (MLC0331, MLCJ0809, MLCJ080926), so the shape is chosen deterministically: the prefix used by most rows
     * first, then the digit count used by most rows under it, then the SHORTER digit count (here {@code MLCJ} + four digits); the number is one above the largest in use under that shape
     * ({@code MLCJ0810}); a retry ({@code skip}) takes the next number. With nothing to copy it falls back to {@code MLCJ0001}. Sets {@link #lastCode}.
     */
    public String pickCode(List<String[]> rows, int skip) {
        Map<String, Integer> shapes = new HashMap<>();
        Map<String, Integer> maxNo = new HashMap<>();
        Pattern p = Pattern.compile("^([A-Za-z]*)(\\d+)$");
        for (String[] r : rows) {
            Matcher m = p.matcher(r[0].trim());
            if (!m.matches()) continue;
            String shape = m.group(1) + "|" + m.group(2).length();
            shapes.merge(shape, 1, Integer::sum);
            maxNo.merge(shape, Integer.parseInt(m.group(2)), Math::max);
        }
        // the prefix with most rows, then the shape with most rows, then the shorter digit count, then alphabetical order (the sorted walk makes ties come out the same every run)
        Map<String, Integer> prefixCount = new HashMap<>();
        for (Map.Entry<String, Integer> e : shapes.entrySet()) prefixCount.merge(e.getKey().split("\\|", -1)[0], e.getValue(), Integer::sum);
        String best = null;
        for (String s : new java.util.TreeSet<>(shapes.keySet())) {
            if (best == null) { best = s; continue; }
            String bp = best.split("\\|", -1)[0], sp = s.split("\\|", -1)[0];
            int bw = Integer.parseInt(best.split("\\|", -1)[1]), sw = Integer.parseInt(s.split("\\|", -1)[1]);
            int cmp = Integer.compare(prefixCount.get(sp), prefixCount.get(bp));
            if (cmp == 0) cmp = Integer.compare(shapes.get(s), shapes.get(best));
            if (cmp == 0) cmp = Integer.compare(bw, sw);                            // the shorter digit count is the better one
            if (cmp > 0) best = s;
        }
        String prefix = best == null ? "MLCJ" : best.split("\\|", -1)[0];
        int width = best == null ? 4 : Integer.parseInt(best.split("\\|", -1)[1]);
        int n = best == null ? 1 : maxNo.get(best) + 1;
        String code = "";
        int seen = 0;
        for (; n < 1_000_000 && code.isEmpty(); n++) {
            String cand = prefix + String.format("%0" + width + "d", n);
            if (taken(rows, cand, "")) continue;
            if (seen++ < skip) continue;
            code = cand;
        }
        lastCode = code;
        return code;
    }

    /** Choose a Description from the pool of real kinds of jurisdiction that collides with no existing row, ignoring case (shifted by {@code skip} for a retry; a numeral is appended once the pool is used up). */
    public String pickDescription(List<String[]> rows, int skip) {
        String d = "";
        int seen = 0;
        for (int round = 0; round < 50 && d.isEmpty(); round++) {
            for (String base : DESCRIPTIONS) {
                String cand = round == 0 ? base : base + " (" + (round + 1) + ")";
                if (taken(rows, "", cand)) continue;
                if (seen++ < skip) continue;
                d = cand;
                break;
            }
        }
        lastDescription = d;
        return d;
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
        page.evaluate("() => { window.__mljToasts=[]; if(window.__mljObs) window.__mljObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__mljToasts.includes(m)) window.__mljToasts.push(m); });"
                + " window.__mljObs=new MutationObserver(add); window.__mljObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__mljToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Submit (the form's own button — not one in the grid) and wait for the toast. Returns the toast text ("" if none appeared); the server's answer is in {@link #lastSaveStatus} / {@link #lastSaveMessage}. */
    public String submitAndGetToast() {
        problems.clear();
        lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(vis).find(x=>/^\\s*submit\\s*$/i.test(norm(x.textContent||x.value))); if(!b) return false; b.id='__mljSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Submit button is not on screen"); return ""; }
        try { page.locator("#__mljSubmit").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Submit button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__mljSubmit'); if(e) e.removeAttribute('id'); }"); }
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
