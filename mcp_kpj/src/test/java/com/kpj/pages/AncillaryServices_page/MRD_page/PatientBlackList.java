package com.kpj.pages.AncillaryServices_page.MRD_page;

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
 * Ancillary Services &gt; MRD &gt; <b>Patient Black List</b> — Page Object (route {@code #/PatientBlackList}).
 *
 * <p>Confirmed live (devhis build v2026.11.03.392). One screen, form on top and grid below:</p>
 * <ul>
 *   <li><b>From Date</b> / <b>To Date</b> ({@code PatientData.fromdate|todate}, {@code dd/MM/yyyy}, empty at first with today as the placeholder; 720kb date pickers: typed text is discarded, the day is clicked in the
 *       calendar). They are FILTERS of the grid's Search ({@code SearchBtnClick()}).</li>
 *   <li><b>Blacklist Reason*</b> ({@code .blacklistid}, {@code #drpBlackListReason}, a plain select; the list carries many QA-stamped duplicates) and <b>Location*</b> ({@code .locationId}; the screen clears it when a
 *       patient is loaded, so it is chosen afterwards).</li>
 *   <li><b>Patient Name*</b> and <b>NRIC / Passport*</b> (filled by the MRN lookup), <b>MRN*</b> ({@code .MRNO}) with a search button ({@code SearchPatientByMRNo()} -> {@code POST /api/patientregistration/FetchPatientData
 *       {ExecFlag:GetPatientInfo, MRNo}}) and a patient-search popup, <b>Unblocked Reason*</b> ({@code .unblockedreason}), <b>Blacklist Remark*</b> ({@code .Remark}) and the <b>Patient Blacklist</b> checkbox
 *       ({@code .isPatientBlackList}, {@code fnCheck(...)}: ticked = blacklist mode, Unblocked Reason is DISABLED and cleared; unticked = unblock mode, Blacklist Reason and Remark are DISABLED - but only once the
 *       box has been changed: untouched, every field is enabled).</li>
 *   <li><b>Submit</b> ({@code fnIUD()}, {@code POST /api/PatientBlackList/IUD}): with the box untouched its remark/unblock checks do not apply; on success it re-runs the grid search with the CURRENT filters, shows the
 *       message and clears the form. <b>Cancel</b> ({@code fnClear()}).</li>
 *   <li>The grid ({@code ui-grid #grid}): MR No | Patient Name | NRIC/Passport | Blacklisted | Blacklist Reason | Blocked By | Blocked Date/Time | Unblocked By | Unblocked Date/Time | Unblocked Reason |
 *       Blacklist Remark | Location | Attachment | Edit | View Activity.</li>
 * </ul>
 *
 * <p>Submitting is REAL: a black-list record is written for the patient and stays.</p>
 */
public class PatientBlackList extends BasePage {

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
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public PatientBlackList(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastFrom = "", lastTo = "", lastMrn = "", lastPatient = "", lastNric = "", lastReason = "", lastLocation = "", lastUnblockedReason = "", lastRemark = "", lastToast = "", lastSaveMessage = "", lastDialog = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String M = "PatientData.";
    private static final String HREF = "#/patientblacklist";
    private static final String PARENT = "Ancillary Services", GROUP = "MRD";
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    /**
     * Ancillary Services &gt; MRD &gt; Patient Black List. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again). The group
     * "MRD" also contains a leaf called "MRD" ({@code #/MRDFileList}), listed AFTER the group's own header, so the first match is the group. The link is found by its exact route.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='" + PARENT + "')",
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__blLink'; }", HREF);
                try { page.locator("#__blLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("PatientBlackList.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__blLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !onScreen(); i++) page.waitForTimeout(250);
        waitForAngular(1500);
        return onScreen();
    }

    /** On the screen: the route AND the screen's own MRN box (a bounce to the dashboard has neither). */
    public boolean onScreen() {
        return page.url().toLowerCase().matches(".*#/patientblacklist/?$")
                && Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll('input')].some(e=>vis(e) && (e.getAttribute('ng-model')||'')==='" + M + "MRNO'); }"));
    }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__blToasts=[]; if(window.__blObs) window.__blObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__blToasts.includes(m)) window.__blToasts.push(m); });"
                + " window.__blObs=new MutationObserver(add); window.__blObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__blToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- From Date / To Date (the 720kb date pickers) -------------------------------------------------------------------------------

    private String boxValue(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e? (e.value||'') : ''; }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); try{ const m=angular.element(e).scope().$eval(n); return m==null? '' : String(m); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private boolean calendarOpen(String sel) {
        Object r = page.evaluate("(s) => {" + JS + " const e=document.querySelector(s); const w=e && e.closest('.datepicker, datepicker'); const c=w && w.querySelector('._720kb-datepicker-calendar'); return !!c && vis(c) && [...c.querySelectorAll('._720kb-datepicker-calendar-day')].some(vis); }", sel);
        return Boolean.TRUE.equals(r);
    }

    private int[] shownMonth(String sel) {
        Object r = page.evaluate("(s) => { const e=document.querySelector(s); const w=e && e.closest('.datepicker, datepicker'); const h=w && w.querySelector('._720kb-datepicker-calendar-header-middle'); return h? h.textContent : ''; }", sel);
        String t = r == null ? "" : r.toString();
        Matcher y = Pattern.compile("(\\d{4})").matcher(t);
        if (!y.find()) return null;
        for (int i = 0; i < 12; i++) if (t.contains(MONTHS[i])) return new int[]{Integer.parseInt(y.group(1)), i + 1};
        return null;
    }

    /**
     * Open the date picker of the box with this ng-model, move its calendar to the target month with the Prev / Next links and click the target day (the picker discards typed characters). The days of the
     * neighbouring months that fill the grid share their numbers with the real ones, so a day up to the 15th is the FIRST cell with that number and a later day the LAST. Verifies the box and the model.
     */
    private boolean pickDate(String ng, LocalDate target, String what) {
        String sel = "input[ng-model='" + ng + "']";
        try { page.locator(sel).first().click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the " + what + " box could not be clicked"); return false; }
        for (int i = 0; i < 20 && !calendarOpen(sel); i++) page.waitForTimeout(250);
        if (!calendarOpen(sel)) { problems.add("the " + what + " calendar did not open"); return false; }
        for (int step = 0; step < 36; step++) {
            int[] my = shownMonth(sel);
            if (my == null) { problems.add("the month of the " + what + " calendar could not be read"); return false; }
            int diff = (target.getYear() - my[0]) * 12 + (target.getMonthValue() - my[1]);
            if (diff == 0) break;
            String link = diff < 0 ? "prevMonth()" : "nextMonth()";
            Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); const l=w && w.querySelector('a[ng-click=\"'+a[1]+'\"]'); if(!l) return false; l.id='__blNav'; return true; }", Arrays.asList(sel, link));
            if (!Boolean.TRUE.equals(tagged)) { problems.add("the " + what + " calendar has no " + link + " link"); return false; }
            try { page.locator("#__blNav").click(new Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { problems.add("the " + what + " calendar's " + link + " link could not be clicked"); return false; }
            finally { page.evaluate("() => { const e=document.getElementById('__blNav'); if(e) e.removeAttribute('id'); }"); }
            page.waitForTimeout(500);
        }
        int[] now = shownMonth(sel);
        if (now == null || now[0] != target.getYear() || now[1] != target.getMonthValue()) { problems.add("the " + what + " calendar did not reach " + MONTHS[target.getMonthValue() - 1] + " " + target.getYear()); return false; }
        Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); if(!w) return false;"
                + " const d=[...w.querySelectorAll('._720kb-datepicker-calendar-day')].filter(x=>x.offsetWidth && !/disabled/.test(x.className) && x.textContent.trim()===a[1]);"
                + " if(!d.length) return false; const x=(parseInt(a[1],10)<=15)? d[0] : d[d.length-1]; x.id='__blDay'; return true; }", Arrays.asList(sel, String.valueOf(target.getDayOfMonth())));
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the day " + target.getDayOfMonth() + " is not offered by the " + what + " calendar"); return false; }
        try { page.locator("#__blDay").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the day " + target.getDayOfMonth() + " could not be clicked in the " + what + " calendar"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__blDay'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(600);
        String want = target.format(DMY);
        String box = boxValue(ng), mod = model(ng);
        if (!want.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + want + "\"");
        else if (!want.equals(mod)) problems.add("the " + what + " is not held by the model (\"" + mod + "\")");
        if (calendarOpen(sel)) dismissDatePicker();
        return ok();
    }

    public boolean enterFromDate(LocalDate d) { problems.clear(); boolean ok = pickDate(M + "fromdate", d, "From Date"); if (ok) lastFrom = boxValue(M + "fromdate"); return ok; }

    public boolean enterToDate(LocalDate d) { problems.clear(); boolean ok = pickDate(M + "todate", d, "To Date"); if (ok) lastTo = boxValue(M + "todate"); return ok; }

    // ---- MRN ---------------------------------------------------------------------------------------------------------------------------

    private String value(String ng) { return boxValue(ng); }

    /** Type the MRN and click the search button beside it; waits for the patient's name and NRIC to fill in. */
    public boolean searchMrn(String mrn) {
        problems.clear();
        lastMrn = mrn;
        Locator in = page.locator("input[ng-model='" + M + "MRNO']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(mrn, new Locator.PressSequentiallyOptions().setDelay(50));
        } catch (Exception e) { problems.add("the MRN could not be typed"); return false; }
        if (!mrn.equals(value(M + "MRNO"))) { problems.add("the MRN box shows \"" + value(M + "MRNO") + "\" instead of \"" + mrn + "\""); return false; }
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__blMrn'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the MRN search button is not on screen"); return false; }
        armToasts();
        try { page.locator("#__blMrn").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the MRN search button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__blMrn'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 60 && value(M + "PatientName").isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(800);
        lastPatient = value(M + "PatientName");
        lastNric = value(M + "NationalId");
        lastToast = toasts();
        if (lastPatient.isEmpty()) problems.add("the search for MRN " + mrn + " filled no patient name" + (lastToast.isEmpty() ? "" : " (toast: \"" + lastToast + "\")"));
        else if (lastNric.isEmpty()) problems.add("the search for MRN " + mrn + " filled no NRIC / Passport");
        return ok();
    }

    // ---- Blacklist Reason and Location ---------------------------------------------------------------------------------------------------------

    /** The options of the dropdown with this ng-model as {text, value} (placeholder excluded). */
    public List<String[]> options(String ng) {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent))).map(o=>[norm(o.textContent), o.value]); }", ng);
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    public List<String> optionNames(String ng) {
        List<String> out = new ArrayList<>();
        for (String[] o : options(ng)) out.add(o[0]);
        return out;
    }

    /** Select the first option whose text matches one of the preferred names (exactly, ignoring case; or, for {@code contains} entries, as a part), else the first option; real selection events are used. */
    private String pick(String ng, List<String> preferred, boolean contains, String what) {
        List<String[]> opts = options(ng);
        if (opts.isEmpty()) { problems.add("the " + what + " dropdown has no options"); return ""; }
        String[] chosen = opts.get(0);
        outer:
        for (String p : preferred) for (String[] o : opts) if (contains ? o[0].toLowerCase().contains(p.toLowerCase()) : o[0].equalsIgnoreCase(p)) { chosen = o; break outer; }
        page.evaluate("(n) => { const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(e) e.id='__blSel'; }", ng);
        try {
            page.locator("#__blSel").selectOption(new com.microsoft.playwright.options.SelectOption().setValue(chosen[1]), new Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("the " + what + " \"" + chosen[0] + "\" could not be selected: " + e.getMessage().split("\n")[0]); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__blSel'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(600);
        Object shown = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); return e && e.selectedIndex>=0 ? norm(e.options[e.selectedIndex].textContent) : ''; }", ng);
        String m = model(ng);
        if (shown == null || !chosen[0].equals(shown.toString())) { problems.add("the " + what + " shows \"" + shown + "\" instead of \"" + chosen[0] + "\""); return ""; }
        if (m.isEmpty() || m.equals("0")) { problems.add("the " + what + " is not held by the model"); return ""; }
        return chosen[0];
    }

    public String selectBlacklistReason(List<String> preferred) { problems.clear(); lastReason = pick(M + "blacklistid", preferred, false, "Blacklist Reason"); return lastReason; }

    public String selectLocation(List<String> preferredParts) { problems.clear(); lastLocation = pick(M + "locationId", preferredParts, true, "Location"); return lastLocation; }

    // ---- Unblocked Reason and Blacklist Remark ------------------------------------------------------------------------------------------------

    private boolean type(String ng, String value, String what) {
        Locator in = page.locator("input[ng-model='" + ng + "']").first();
        try {
            in.scrollIntoViewIfNeeded();
            if (!in.isEnabled()) { problems.add("the " + what + " box is DISABLED (the screen disables it for the current Patient Blacklist tick state)"); return false; }
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill(value, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the " + what + " could not be typed: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(300);
        if (!value.equals(boxValue(ng))) problems.add("the " + what + " box shows \"" + boxValue(ng) + "\" instead of \"" + value + "\"");
        else if (!value.equals(model(ng))) problems.add("the " + what + " model holds \"" + model(ng) + "\" instead of \"" + value + "\"");
        return ok();
    }

    public boolean enterUnblockedReason(String text) { problems.clear(); lastUnblockedReason = text; return type(M + "unblockedreason", text, "Unblocked Reason"); }

    public boolean enterRemark(String text) { problems.clear(); lastRemark = text; return type(M + "Remark", text, "Blacklist Remark"); }

    /** The "Patient Blacklist" checkbox's state as text, for the report. */
    public String checkboxState() {
        Object r = page.evaluate("() => { const c=document.querySelector(\"input[ng-model='" + M + "isPatientBlackList']\"); const d=id=>{ const e=document.getElementById(id); return e? (e.disabled?'disabled':'enabled') : 'n/a'; };"
                + " return 'Patient Blacklist box '+(c? (c.checked?'ticked':'not ticked') : 'n/a')+'; Blacklist Reason '+d('drpBlackListReason')+', Unblocked Reason '+d('txtUnblockReason')+', Blacklist Remark '+d('txtBlacklistRemark'); }");
        return r == null ? "" : r.toString();
    }

    // ---- what the server holds -----------------------------------------------------------------------------------------------------------------------

    /** The records the server holds for this MRN as {id, mrn, blacklisted (true/false), reason, unblocked reason, remark, blocked date/time}; null when they could not be read. Asked with the call the grid makes. */
    public List<String[]> serverRows(String mrn) { return serverRows(mrn, "", ""); }

    /**
     * The same, with the grid's date filter ({@code fromdate} / {@code todate}, dd/MM/yyyy) when they are given. The server applies that filter to the Blocked Date/Time ONLY, so a record saved as an UNBLOCK
     * (no blocked date) is never returned for a date range - which is why the table stays blank when dates are entered.
     */
    public List<String[]> serverRows(String mrn, String fromdate, String todate) {
        Object r = page.evaluate("(a) => new Promise(res => { try { const h=angular.element(document.body).injector().get('$http');"
                + " const body={ExecFlag:'GRID', Id:0, mrn:a[0]}; if(a[1]) body.fromdate=a[1]; if(a[2]) body.todate=a[2];"
                + " h.post('/api/PatientBlackList/Fetch', body).then(x=>res(JSON.stringify((x.data||[]).map(d=>[d.Id,d.Mrn,d.IsBlacklist,d.BlacklistReasonName,d.UnblockedReason,d.BlacklistRemark,d.BlockedDatetime]))), e=>res('ERR '+(e&&e.status))); } catch(x){ res('ERR '+x); } })",
                Arrays.asList(mrn, fromdate == null ? "" : fromdate, todate == null ? "" : todate));
        String s = r == null ? "" : r.toString();
        if (s.isEmpty() || s.startsWith("ERR")) return null;
        List<String[]> out = new ArrayList<>();
        try {
            JsonArray a = JsonParser.parseString(s).getAsJsonArray();
            for (JsonElement row : a) {
                JsonArray c = row.getAsJsonArray();
                String[] v = new String[7];
                for (int i = 0; i < 7; i++) v[i] = c.get(i).isJsonNull() ? "" : c.get(i).getAsString().trim();
                out.add(v);
            }
        } catch (Exception e) { return null; }
        return out;
    }

    /** The server's record for this MRN carrying this unblocked reason and remark (read afresh), or null. */
    public String[] serverHolds(String mrn, String unblockedReason, String remark) {
        List<String[]> rows = serverRows(mrn);
        if (rows == null) return null;
        for (String[] r : rows) if (r[4].equalsIgnoreCase(unblockedReason.trim()) && r[5].equalsIgnoreCase(remark.trim())) return r;
        return null;
    }

    // ---- Submit ----------------------------------------------------------------------------------------------------------------------------------------

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(fnIUD|fnClear)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__blDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__blDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("PatientBlackList.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__blDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Submit, answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String submitAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^fnIUD\\s*\\(/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__blSubmit'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Submit button is not on screen"); return ""; }
        try { page.locator("#__blSubmit").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Submit button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__blSubmit'); if(e) e.removeAttribute('id'); }"); }
        long end = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < end) {
            String d = answerDialog();
            if (!d.isEmpty()) lastDialog = lastDialog.isEmpty() ? d : lastDialog + " > " + d;
            lastToast = toasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(350);
        }
        page.waitForTimeout(800);
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

    public boolean rejectedAsDuplicate() {
        String t = (lastToast + " " + lastSaveMessage).toLowerCase();
        return t.contains("already") || t.contains("exist") || t.contains("duplicate");
    }

    public boolean savedByServer() { return lastSaveStatus == 1; }

    /** The toast is a success message AND the server did not refuse the save. The server spells several messages wrongly (Succesfully, sucessfully), so those are accepted too. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated|blacklisted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !rejectedAsDuplicate();
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    // ---- the table ------------------------------------------------------------------------------------------------------------------------------------

    /** The rows the grid shows, each as its cells ({@code MR No, Patient Name, NRIC, Blacklisted, Reason, Blocked By, Blocked Date/Time, Unblocked By, Unblocked Date/Time, Unblocked Reason, Remark, Location}). */
    public List<String[]> tableRows() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#grid .ui-grid-canvas .ui-grid-row')].filter(r=>r.textContent.trim().length>0)"
                + ".map(r=>[...r.querySelectorAll('.ui-grid-cell')].slice(0,12).map(c=>norm(c.textContent))); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) {
            List<?> c = (List<?>) row;
            String[] v = new String[12];
            for (int i = 0; i < 12; i++) v[i] = i < c.size() ? String.valueOf(c.get(i)) : "";
            out.add(v);
        }
        return out;
    }

    /** The grid row for this MRN carrying this unblocked reason and remark, or null. */
    public String[] findInTable(String mrn, String unblockedReason, String remark) {
        for (String[] r : tableRows()) if (r[0].contains(mrn) && r[9].equalsIgnoreCase(unblockedReason.trim()) && r[10].equalsIgnoreCase(remark.trim())) return r;
        return null;
    }

    /** Waits up to {@code ms} for the grid to show the record. */
    public String[] awaitInTable(String mrn, String unblockedReason, String remark, int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { String[] r = findInTable(mrn, unblockedReason, remark); if (r != null) return r; page.waitForTimeout(500); }
        return findInTable(mrn, unblockedReason, remark);
    }

    /** Click the screen's own Search ({@code SearchBtnClick(2)}) and wait for the server's answer; returns the number of rows the table shows afterwards, -1 when Search could not be clicked. */
    public int clickSearch() {
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^SearchBtnClick/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__blSearch'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) return -1;
        try {
            page.waitForResponse(x -> x.url().contains("/PatientBlackList/Fetch"), new Page.WaitForResponseOptions().setTimeout(25000),
                    () -> page.locator("#__blSearch").click(new Locator.ClickOptions().setTimeout(6000)));
        } catch (Exception e) { System.out.println("PatientBlackList.clickSearch: " + e.getMessage().split("\n")[0]); }
        finally { page.evaluate("() => { const e=document.getElementById('__blSearch'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(2000);
        return tableRows().size();
    }

    /** The pager's "1 - 10 of 29 items" text. */
    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    /** The number of records the screen's own list ({@code PatientBlackList}) holds, "?" when unreadable. */
    public String scopeRows() {
        Object r = page.evaluate("() => { try{ const e=document.querySelector(\"[ng-model='" + M + "MRNO']\"); const sc=angular.element(e).scope(); return String(sc.PatientBlackList.length); }catch(x){ return '?'; } }");
        return r == null ? "?" : r.toString();
    }
}
