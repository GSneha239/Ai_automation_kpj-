package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application Configuration &gt; Admission &gt; <b>Room Type</b> — Page Object ({@code #/RoomTypeMaster}, form {@code #/add-RoomTypeMaster}).
 *
 * <p>Confirmed live (devhis build v2026.10.27.303). The list is a ui-grid ({@code #grid1}: Code, Room Type, Status, Edit; 5 rows a page, 63 rows in all at the time) with an <b>Add</b> link
 * ({@code AddRoomTypeMaster()}). The form ({@code RoomTypeMaster.*}) has <b>Code*</b> ({@code RoomTypeMaster.code}), <b>Room Type*</b> ({@code RoomTypeMaster.roomtype}) and the buttons
 * <b>Submit</b> ({@code fnIUDRoomTypeMaster()}), Clear and Back.</p>
 *
 * <p>Both fields must be new: the existing codes and room types (upper-case, e.g. "VIP", "VIP ROOM", "ICU ROOM") are read from the grid's own data array, which holds ALL rows rather than only the
 * five on the current page, and the new values are chosen so that neither collides with one of them ignoring case. Saving is REAL: a room type is added to the master (and stays; it can only be
 * made inactive with its Status switch, which this page never touches).</p>
 */
public class RoomType extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public RoomType(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastCode = "", lastRoomType = "", lastToast = "", lastSaveMessage = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String CODE_NG = "RoomTypeMaster.code", TYPE_NG = "RoomTypeMaster.roomtype";
    private static final String HREF = "#/roomtypemaster";

    /** Realistic room-type names to choose from (never "Auto…" junk: the master keeps whatever is saved). */
    private static final String[] NAMES = {
            "Executive Twin Room", "Family Suite", "Deluxe Twin Room", "Garden View Room", "Bariatric Room",
            "Observation Room", "Recovery Suite", "Premier Twin Room", "Twin Sharing Room", "Companion Suite"
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
     * Application Configuration &gt; Admission &gt; Room Type. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group
     * again, and a click on Admission straight after Application Configuration is lost), so the first group gets a settling pause before the next one is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Application Configuration')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__rtLink'; }", HREF);
                try { page.locator("#__rtLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("RoomType.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__rtLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll('a')].find(b=>/AddRoomTypeMaster/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("RoomType.nav: the Add link is not ready"); }
        waitForAngular(1000);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/roomtypemaster/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-roomtypemaster/?$")
                && Boolean.TRUE.equals(page.evaluate("(n) => {" + JS + " return !![...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); }", CODE_NG));
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    /** Every Room Type the list holds (all pages), as {code, room type, status}; read from the grid's data array. Empty when the grid has not loaded. */
    public List<String[]> existing() {
        Object r = page.evaluate("() => { try{ const g=document.getElementById('grid1'); const d=angular.element(g).scope().gridOptions.data;"
                + " return d.map(x=>[String(x.code==null?'':x.code), String(x.roomtype==null?'':x.roomtype), String(x.Status)]); }catch(e){ return []; } }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2))}); }
        return out;
    }

    /** Wait (up to 20 s) for the list's grid to hold rows; returns them. */
    public List<String[]> awaitExisting() {
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < 80 && rows.isEmpty(); i++) { rows = existing(); if (rows.isEmpty()) page.waitForTimeout(250); }
        return rows;
    }

    /** The pager text ("1 - 5 of 63 items"), "" when there is none. */
    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    private static boolean taken(List<String[]> rows, String code, String name) {
        for (String[] r : rows) {
            if (!code.isEmpty() && r[0].trim().equalsIgnoreCase(code.trim())) return true;
            if (!name.isEmpty() && r[1].trim().equalsIgnoreCase(name.trim())) return true;
        }
        return false;
    }

    /**
     * Choose a Code and a Room Type that neither collide with an existing row nor with each other's pattern, ignoring case. The room type comes from a pool of realistic names (the first unused one,
     * shifted by {@code skip} so a retry after a rejection tries the next); the code is its initials plus two digits, stepped until unused. Sets {@link #lastCode} and {@link #lastRoomType}.
     */
    public String[] pickNew(List<String[]> rows, int skip) {
        String name = "";
        int seen = 0;
        for (int round = 0; round < 50 && name.isEmpty(); round++) {
            for (String base : NAMES) {
                String cand = round == 0 ? base : base + " " + (round + 1);
                if (taken(rows, "", cand)) continue;
                if (seen++ < skip) continue;
                name = cand;
                break;
            }
        }
        StringBuilder ini = new StringBuilder();
        for (String w : name.split("\\s+")) if (!w.isEmpty() && Character.isLetter(w.charAt(0))) ini.append(Character.toUpperCase(w.charAt(0)));
        String prefix = ini.length() > 4 ? ini.substring(0, 4) : ini.toString();
        String code = "";
        for (int n = 10; n < 100 && code.isEmpty(); n++) { String cand = prefix + n; if (!taken(rows, cand, "")) code = cand; }
        lastRoomType = name;
        lastCode = code;
        return new String[]{code, name};
    }

    // ---- add form ----------------------------------------------------------------------------------------------

    /** Click Add on the list and wait for the form. */
    public boolean clickAdd() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('a,button')].filter(vis).find(x=>/AddRoomTypeMaster/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__rtAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add button is not on screen"); return false; }
        try { page.locator("#__rtAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__rtAdd'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        if (!onForm()) problems.add("the Room Type form did not open (" + page.url() + ")");
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
            in.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(40));
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
        waitForAngular(400);
        String box = String.valueOf(page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }", ng)).trim();
        if (!value.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + value + "\"");
        if (!value.equals(model(ng))) problems.add("the " + what + " is not held by the model");
        return ok();
    }

    public boolean enterCode(String code) { problems.clear(); return type(CODE_NG, code, "Code"); }

    public boolean enterRoomType(String name) { problems.clear(); return type(TYPE_NG, name, "Room Type"); }

    // ---- submit ------------------------------------------------------------------------------------------------

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    private void installToastWatch() {
        page.evaluate("() => { window.__rtToasts=[]; if(window.__rtObs) window.__rtObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__rtToasts.includes(m)) window.__rtToasts.push(m); });"
                + " window.__rtObs=new MutationObserver(add); window.__rtObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__rtToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Submit and wait for the toast. Returns the toast text ("" if none appeared); the server's answer is in {@link #lastSaveStatus} / {@link #lastSaveMessage}. */
    public String submitAndGetToast() {
        problems.clear();
        lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fnIUDRoomTypeMaster/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__rtSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Submit button is not on screen"); return ""; }
        try { page.locator("#__rtSubmit").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Submit button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__rtSubmit'); if(e) e.removeAttribute('id'); }"); }
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

    /** The screen refused because the code or room type already exists. */
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
