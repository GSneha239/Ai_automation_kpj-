package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application Configuration &gt; General &gt; <b>MIMSOverrideJustificationMaster</b> — Page Object ({@code #/MIMSOverrideJustificationMaster}).
 *
 * <p>Confirmed live (devhis build v2026.10.28.329). A list of the reasons a prescriber can pick when overriding a MIMS drug alert (the note in the dialog says active entries feed the Override
 * Justification dropdown of the MIMS override windows in EMR and Pharmacy; the "Others" entry makes free-text remarks mandatory). The list is a ui-grid (Code, Remark, Status switch, Edit) holding 6
 * rows at the time: {@code BOR = Benefit outweighs risk}, {@code DAM = Dose adjusted to mitigate risk}, {@code NSA = No suitable alternative available}, {@code OTH = Others},
 * {@code PTC = Patient previously tolerated this combination}, {@code WMC = Will monitor closely}.</p>
 *
 * <p><b>Add</b> ({@code addJustification()}) opens the modal "Add Override Justification" ({@code #MIMSJustificationModal}): <b>Code*</b> ({@code Justification.code}, placeholder "e.g. BOR"),
 * <b>Description*</b> ({@code Justification.description}, "Text shown in the override dropdown"), <b>Status</b> (an "Active" tick, on by default and left alone), <b>Save</b>
 * ({@code saveJustification()}) and Close.</p>
 *
 * <p>Codes are three upper-case letters that abbreviate the description, and the descriptions are short clinical sentences; the new entry follows both (for example {@code NCS = Not clinically
 * significant for this patient}) and collides with no existing row, ignoring case. Saving is REAL: the entry is added to the master and stays (it can only be made inactive with its Status switch, which
 * this page never touches), and it appears in the override dropdown of the MIMS windows.</p>
 */
public class MimsOverrideJustificationMaster extends BasePage {

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

    public MimsOverrideJustificationMaster(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastCode = "", lastDescription = "", lastToast = "", lastSaveMessage = "", lastModalTitle = "", lastModalNote = "";
    public boolean lastStatusActive;
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String MODAL = "#MIMSJustificationModal";
    private static final String CODE_NG = "Justification.code", DESC_NG = "Justification.description", STATUS_NG = "Justification.status";
    private static final String HREF = "#/mimsoverridejustificationmaster";
    private static final String PARENT = "Application Configuration", GROUP = "General";

    /** Real override justifications for a MIMS drug alert, as {code, description}: three upper-case letters abbreviating a short clinical sentence, like the six already in the master. */
    private static final String[][] POOL = {
            {"NCS", "Not clinically significant for this patient"},
            {"SHA", "Specialist has advised this combination"},
            {"SCT", "Short course of therapy only"},
            {"LPM", "Laboratory parameters will be monitored"},
            {"DTS", "Dose timing separated to reduce interaction"},
            {"PIC", "Patient informed and consented to therapy"}
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
     * Application Configuration &gt; General &gt; MIMSOverrideJustificationMaster. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes
     * the group again, and a click on General straight after Application Configuration is lost), so the first group gets a settling pause before the next one is clicked.
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__mimsLink'; }", HREF);
                try { page.locator("#__mimsLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("MimsOverrideJustificationMaster.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__mimsLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => { const a=[...document.querySelectorAll('a[ng-click]')]; return a.some(x=>/^addJustification/.test(x.getAttribute('ng-click')||'')); }", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("MimsOverrideJustificationMaster.nav: the Add button is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/mimsoverridejustificationmaster/?$"); }

    // ---- the list ---------------------------------------------------------------------------------------------

    private static final String GRID =
            "const g=document.querySelector('.ui-grid'); const sc=g? angular.element(g).scope() : null; const go=sc? (sc.gridOptions || (sc.$parent && sc.$parent.gridOptions)) : null;";

    /** Every justification the list holds (all pages), as {code, description, status}; read from the grid's data array. Empty when the grid has none. */
    public List<String[]> existing() {
        Object r = page.evaluate("() => { try{ " + GRID + " if(!go||!go.data) return []; const s=v=>v==null? '' : String(v);"
                + " const cd=go.columnDefs||[]; const f=(re,def)=>{ const c=cd.find(c=>re.test(String(c.displayName||c.name||c.field||'').trim())); return c? (c.field||c.name||def) : def; };"
                + " const fc=f(/^code/i,'code'), fd=f(/^(remark|description)/i,'description'), fs=f(/^status/i,'status');"
                + " const pick=(x,k,def)=>{ for(const n of [k,def,def.charAt(0).toUpperCase()+def.slice(1)]) if(x[n]!=null) return x[n]; return ''; };"      // a column without its own field (template cell) falls back to the row's own key (the row also holds a null 'Status' next to the real 'status')
                + " return go.data.map(x=>[s(pick(x,fc,'code')), s(pick(x,fd,'description')), s(pick(x,fs,'status'))]); }catch(e){ return []; } }");
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

    /** The pager text ("1 - 6 of 6 items"), "" when there is none. */
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
     * Choose a {code, description} pair that collides with no existing row, ignoring case: the first unused pair of {@code POOL} (skipping {@code skip} of them for a retry). Once the pool is used up a
     * numbered pair is made ("Clinical justification n", three letters counting up from AAA) so a run can still go on. Sets {@link #lastCode} and {@link #lastDescription}.
     */
    public String[] pickNew(List<String[]> rows, int skip) {
        String code = "", description = "";
        int seen = 0;
        for (String[] p : POOL) {
            if (taken(rows, p[0], p[1])) continue;
            if (seen++ < skip) continue;
            code = p[0];
            description = p[1];
            break;
        }
        for (int n = 0; code.isEmpty() && n < 17576; n++) {
            String c = "" + (char) ('A' + n / 676) + (char) ('A' + (n / 26) % 26) + (char) ('A' + n % 26);
            String d = "Clinical justification " + (n + 1);
            if (taken(rows, c, d)) continue;
            if (seen++ < skip) continue;
            code = c;
            description = d;
        }
        lastCode = code;
        lastDescription = description;
        return new String[]{code, description};
    }

    // ---- the modal -----------------------------------------------------------------------------------------------

    /** Is the Add Override Justification dialog open? */
    public boolean modalOpen() {
        Object r = page.evaluate("(m) => {" + JS + " const e=document.querySelector(m); return !!e && vis(e) && e.classList.contains('in'); }", MODAL);
        return Boolean.TRUE.equals(r);
    }

    /** Click <b>Add</b> and wait for the dialog (and its Code box) to render. */
    public boolean clickAdd() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('a,button')].filter(vis).find(x=>/^addJustification/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__mimsAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add button is not on screen"); return false; }
        try { page.locator("#__mimsAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__mimsAdd'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 40 && !modalOpen(); i++) page.waitForTimeout(250);
        if (!modalOpen()) { problems.add("the Add Override Justification dialog did not open"); return false; }
        try {
            page.locator(MODAL + " input[ng-model='" + CODE_NG + "']").first().waitFor(new Locator.WaitForOptions().setTimeout(8000));
        } catch (Exception e) { problems.add("the dialog's Code box never appeared"); }
        page.waitForTimeout(600);                                                // the dialog's slide-in animation
        Object t = page.evaluate("(m) => {" + JS + " const e=document.querySelector(m); const h=e && e.querySelector('.modal-title,.modal-header'); return h? norm(h.textContent).replace(/^[×x]\\s*/i,'') : ''; }", MODAL);
        lastModalTitle = t == null ? "" : t.toString();
        Object n = page.evaluate("(m) => {" + JS + " const e=document.querySelector(m); const b=e && e.querySelector('.modal-body'); return b? norm(b.textContent) : ''; }", MODAL);
        lastModalNote = n == null ? "" : n.toString().replaceFirst("^.*?Active\\s+", "");
        Object a = page.evaluate("(a) => { const e=document.querySelector(a[0]+\" input[ng-model='\"+a[1]+\"']\"); return !!e && e.checked; }", java.util.Arrays.asList(MODAL, STATUS_NG));
        lastStatusActive = Boolean.TRUE.equals(a);
        return ok();
    }

    private String model(String ng) {
        Object v = page.evaluate("(a) => { const e=document.querySelector(a[0]+\" [ng-model='\"+a[1]+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(a[1]); return v==null?'':String(v); }catch(x){ return ''; } }", java.util.Arrays.asList(MODAL, ng));
        return v == null ? "" : v.toString().trim();
    }

    private boolean type(String ng, String value, String what) {
        Locator in = page.locator(MODAL + " input[ng-model='" + ng + "']").first();
        try {
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(25));
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
        waitForAngular(400);
        String box = String.valueOf(page.evaluate("(a) => { const e=document.querySelector(a[0]+\" input[ng-model='\"+a[1]+\"']\"); return e? e.value : ''; }", java.util.Arrays.asList(MODAL, ng))).trim();
        if (!value.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + value + "\"");
        if (!value.equals(model(ng))) problems.add("the " + what + " is not held by the model");
        return ok();
    }

    public boolean enterCode(String code) { problems.clear(); return type(CODE_NG, code, "Code"); }

    public boolean enterDescription(String description) { problems.clear(); return type(DESC_NG, description, "Description"); }

    // ---- save --------------------------------------------------------------------------------------------------

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    private void installToastWatch() {
        page.evaluate("() => { window.__mimsToasts=[]; if(window.__mimsObs) window.__mimsObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__mimsToasts.includes(m)) window.__mimsToasts.push(m); });"
                + " window.__mimsObs=new MutationObserver(add); window.__mimsObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__mimsToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Save inside the dialog and wait for the toast. Returns the toast text ("" if none appeared); the server's answer is in {@link #lastSaveStatus} / {@link #lastSaveMessage}. */
    public String saveAndGetToast() {
        problems.clear();
        lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Locator save = page.locator(MODAL + " button[ng-click^='saveJustification']").first();
        try { save.click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
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

    /** After a successful save the dialog should close; true once it has (waits up to 8 s). */
    public boolean waitModalClosed() {
        for (int i = 0; i < 32 && modalOpen(); i++) page.waitForTimeout(250);
        return !modalOpen();
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
