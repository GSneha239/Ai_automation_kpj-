package com.kpj.pages.AncillaryServices_page.DMSViewer_page;

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
 * Ancillary Services &gt; <b>DMS Viewer</b> — Page Object (route {@code #/DMSViewer}; the entry sits directly under Ancillary Services, in no sub-group).
 *
 * <p>Confirmed live (devhis build v2026.11.11.438). The screen:</p>
 * <ul>
 *   <li>An <b>MRN</b> box ({@code DMSViewer.MRNO}) with TWO buttons beside it: the left one ({@code #btnSearch}, {@code SearchPatientByMRNo()}) looks the typed MRN up; the right one
 *       ({@code #btnSearchPatient}, tooltip "Search Patient", {@code OpenPopupScreen()}) opens the <b>Patient Search</b> popup. A <b>Reg. Type</b> OPD / IPD radio.</li>
 *   <li>The <b>Patient Search</b> popup (shared with many screens; {@code PatientData.*}): MRN, name, ... and the date filters Reg Date / <b>Visit Date</b> / Admission Date / Discharge Date, each a tick box with a
 *       From and a To date picker ({@code PatientData.VisitFromDate|VisitToDate}: 720kb pickers, typed text discarded, both today by default; Visit Date is ticked and enabled from the start, the others are
 *       disabled). <b>Search</b> ({@code SearchPatient(0)}, {@code POST /api/patientregistration/FetchPatientData}) fills the <b>Advance Filters</b> table (Select | Photo | MRN | Prefix | Name | Date of Birth |
 *       Age | Reg Date | ...); a patient may be listed once per visit. The Select cell holds a tick-box-like BUTTON ({@code SetSearchPatient(PR)}), which closes the popup and loads the patient.</li>
 *   <li>Loading a patient fills the MRN box, shows the "Name : ... MRN: ... Mob. No ... Gen ... Age ... Passport/NRIC No" line, fills the <b>DMS Folder</b> tree (OPD / IPD, visits, folders with an upload button
 *       {@code ClickAttachment(...)} that opens the Add Document popup) and the patient's saved remark ({@code GetRemark()}).</li>
 *   <li><b>Files Found</b> (File Name | Date) and below it <b>Remarks</b> ({@code DMSViewer.remark}, a textarea) with <b>Save</b> ({@code SaveRemark()}): an empty remark only warns "Please Enter Remark"; otherwise it sends
 *       {@code {mrno, Id: patientid, remark, ExecFlag: 'INSERTREMARK'}} and, on ResultStatus 1, shows "Remark saved successfully".</li>
 * </ul>
 *
 * <p>Saving is REAL: it replaces the remark held for the patient.</p>
 */
public class DMSViewer extends BasePage {

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
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body,
                    r.request().postData() == null ? "" : r.request().postData()};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public DMSViewer(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastFrom = "", lastTo = "", lastMrn = "", lastBanner = "", lastRemarkBefore = "", lastRemark = "", lastToast = "", lastSaveMessage = "", lastDialog = "", lastSaveRequest = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String FROM = "PatientData.VisitFromDate", TO = "PatientData.VisitToDate", CHK = "PatientData.ChkVisitDate";
    private static final String HREF = "#/dmsviewer";
    private static final String TOP = "Ancillary Services";
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

    private int topState() {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", TOP);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickTop() {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", TOP);
        return Boolean.TRUE.equals(r);
    }

    /** Ancillary Services &gt; DMS Viewer. The top-level entry is opened ONCE (the menu builds lazily; a second click while it is still building closes it again) and the link is found by its exact route. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='" + TOP + "')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (topState() != 1) clickTop();
                waitLink(15000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__dvLink'; }", HREF);
                try { page.locator("#__dvLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("DMSViewer.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__dvLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !onScreen(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    /** On the screen: the route AND the screen's own MRN box (a bounce to the dashboard has neither). */
    public boolean onScreen() {
        return page.url().toLowerCase().matches(".*#/dmsviewer/?$")
                && Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll('input')].some(e=>vis(e) && (e.getAttribute('ng-model')||'')==='DMSViewer.MRNO'); }"));
    }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__dvToasts=[]; if(window.__dvObs) window.__dvObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__dvToasts.includes(m)) window.__dvToasts.push(m); });"
                + " window.__dvObs=new MutationObserver(add); window.__dvObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__dvToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- the two buttons beside the MRN box and the Patient Search popup ----------------------------------------------------------

    /** The buttons beside the MRN box, left to right, as "ng-click (id)" - for the report. */
    public List<String> mrnButtons() {
        Object r = page.evaluate("() => {" + JS + " const m=document.querySelector(\"input[ng-model='DMSViewer.MRNO']\"); if(!m) return []; const g=m.closest('.input-group, .form-group, div'); const row=g? g.parentElement : null;"
                + " return [...(row||document).querySelectorAll('button')].filter(vis).filter(b=>/SearchPatientByMRNo|OpenPopupScreen/.test(b.getAttribute('ng-click')||''))"
                + ".sort((a,b)=>a.getBoundingClientRect().left-b.getBoundingClientRect().left).map(b=>(b.getAttribute('ng-click')||'')+' ('+(b.id||'-')+')'); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    public boolean popupOpen() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('.modal')].some(m=>vis(m) && /Patient Search/i.test(m.textContent||'') && [...m.querySelectorAll('button')].some(b=>/^SearchPatient\\s*\\(/.test(b.getAttribute('ng-click')||''))); }");
        return Boolean.TRUE.equals(r);
    }

    /** Click the SECOND (right-hand) button beside the MRN box - the patient-search symbol - and wait for the Patient Search popup. */
    public boolean openPatientSearch() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^OpenPopupScreen/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__dvPop'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the patient-search symbol is not on screen"); return false; }
        try { page.locator("#__dvPop").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the patient-search symbol could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__dvPop'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 60 && !popupOpen(); i++) page.waitForTimeout(250);
        if (!popupOpen()) { problems.add("the Patient Search popup did not open"); return false; }
        waitForAngular(1500);
        return true;
    }

    // ---- Visit Date: tick box, From and To -------------------------------------------------------------------------------------------

    private String boxValue(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e? (e.value||'') : ''; }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); try{ const m=angular.element(e).scope().$eval(n); return m==null? '' : String(m); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** True when the Visit Date tick box is ticked (and enabled). */
    public boolean visitDateTicked() {
        Object r = page.evaluate("(n) => { const c=document.querySelector(\"input[ng-model='\"+n+\"']\"); return !!c && c.checked && !c.disabled; }", CHK);
        return Boolean.TRUE.equals(r);
    }

    /** Make sure the Visit Date tick box is ticked (it is from the start); returns true when it is. */
    public boolean ensureVisitDateTicked() {
        if (visitDateTicked()) return true;
        try { page.locator("input[ng-model='" + CHK + "']").first().check(new Locator.CheckOptions().setTimeout(5000)); } catch (Exception e) { problems.add("the Visit Date tick box could not be ticked"); }
        waitForAngular(500);
        return visitDateTicked();
    }

    /** The date the Visit Date To box holds as the popup opens (today on the application's clock); today on the browser's clock when it cannot be read. */
    public LocalDate defaultToDate() {
        try { return LocalDate.parse(boxValue(TO), DMY); } catch (Exception e) { return LocalDate.now(); }
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
            Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); const l=w && w.querySelector('a[ng-click=\"'+a[1]+'\"]'); if(!l) return false; l.id='__dvNav'; return true; }", Arrays.asList(sel, link));
            if (!Boolean.TRUE.equals(tagged)) { problems.add("the " + what + " calendar has no " + link + " link"); return false; }
            try { page.locator("#__dvNav").click(new Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { problems.add("the " + what + " calendar's " + link + " link could not be clicked"); return false; }
            finally { page.evaluate("() => { const e=document.getElementById('__dvNav'); if(e) e.removeAttribute('id'); }"); }
            page.waitForTimeout(500);
        }
        int[] now = shownMonth(sel);
        if (now == null || now[0] != target.getYear() || now[1] != target.getMonthValue()) { problems.add("the " + what + " calendar did not reach " + MONTHS[target.getMonthValue() - 1] + " " + target.getYear()); return false; }
        Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); if(!w) return false;"
                + " const d=[...w.querySelectorAll('._720kb-datepicker-calendar-day')].filter(x=>x.offsetWidth && !/disabled/.test(x.className) && x.textContent.trim()===a[1]);"
                + " if(!d.length) return false; const x=(parseInt(a[1],10)<=15)? d[0] : d[d.length-1]; x.id='__dvDay'; return true; }", Arrays.asList(sel, String.valueOf(target.getDayOfMonth())));
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the day " + target.getDayOfMonth() + " is not offered by the " + what + " calendar"); return false; }
        try { page.locator("#__dvDay").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the day " + target.getDayOfMonth() + " could not be clicked in the " + what + " calendar"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__dvDay'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(600);
        String want = target.format(DMY);
        String box = boxValue(ng), mod = model(ng);
        if (!want.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + want + "\"");
        else if (!want.equals(mod)) problems.add("the " + what + " is not held by the model (\"" + mod + "\")");
        if (calendarOpen(sel)) dismissDatePicker();
        return ok();
    }

    /** Enter the Visit Date From date through its calendar; To is left as it is (today). */
    public boolean enterVisitFrom(LocalDate from) {
        problems.clear();
        boolean ok = pickDate(FROM, from, "Visit Date From");
        if (ok) { lastFrom = boxValue(FROM); lastTo = boxValue(TO); }
        return ok;
    }

    // ---- Search and the Advance Filters table --------------------------------------------------------------------------------------------------

    /** Click the popup's Search and wait for the server's answer. */
    public boolean clickSearch() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const m=[...document.querySelectorAll('.modal')].filter(vis).pop(); if(!m) return false; const b=[...m.querySelectorAll('button')].filter(vis).find(x=>/^SearchPatient\\s*\\(/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__dvSearch'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the popup's Search button is not on screen"); return false; }
        try {
            page.waitForResponse(x -> x.url().contains("/patientregistration/FetchPatientData"), new Page.WaitForResponseOptions().setTimeout(30000),
                    () -> page.locator("#__dvSearch").click(new Locator.ClickOptions().setTimeout(6000)));
        } catch (Exception e) { problems.add("Search did not reach the server: " + e.getMessage().split("\n")[0]); }
        finally { page.evaluate("() => { const e=document.getElementById('__dvSearch'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        for (int i = 0; i < 24 && resultRows().isEmpty(); i++) page.waitForTimeout(250);
        return ok();
    }

    /** The rows of the Advance Filters table as {MRN, Prefix, Name, Date of Birth, Age, Reg Date}. */
    public List<String[]> resultRows() {
        Object r = page.evaluate("() => {" + JS + " const m=[...document.querySelectorAll('.modal')].filter(vis).pop(); if(!m) return [];"
                + " return [...m.querySelectorAll('table tbody tr')].filter(r=>r.textContent.trim().length>0 && r.querySelector('button[ng-click^=SetSearchPatient]'))"
                + ".map(r=>[...r.querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean).slice(0,6)); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) {
            List<?> c = (List<?>) row;
            String[] v = new String[6];
            for (int i = 0; i < 6; i++) v[i] = i < c.size() ? String.valueOf(c.get(i)) : "";
            out.add(v);
        }
        return out;
    }

    /** The "1 - 10 ..." style paging text is not shown by this popup; the number of Select buttons is the number of rows. */
    public int selectButtons() {
        Object r = page.evaluate("() => {" + JS + " const m=[...document.querySelectorAll('.modal')].filter(vis).pop(); return m? [...m.querySelectorAll('button[ng-click^=SetSearchPatient]')].length : 0; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * Tick the Select box (a tick-box-like button) of the row (0-based) and wait for the popup to close and the patient to load: the MRN box fills and the "Name : ..." line shows. The row's text is left in
     * {@link #lastBanner} as the banner the screen shows afterwards, the MRN in {@link #lastMrn}.
     */
    public boolean selectRow(int index) {
        problems.clear();
        Object tagged = page.evaluate("(i) => {" + JS + " const m=[...document.querySelectorAll('.modal')].filter(vis).pop(); if(!m) return false;"
                + " const rows=[...m.querySelectorAll('table tbody tr')].filter(r=>r.textContent.trim().length>0 && r.querySelector('button[ng-click^=SetSearchPatient]')); const r=rows[i]; if(!r) return false;"
                + " const b=r.querySelector('button[ng-click^=SetSearchPatient]'); b.id='__dvPick'; b.scrollIntoView({block:'center'}); return true; }", index);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("row " + (index + 1) + " has no Select box on screen"); return false; }
        armToasts();
        try { page.locator("#__dvPick").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Select box could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__dvPick'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 40 && popupOpen(); i++) page.waitForTimeout(250);
        if (popupOpen()) { problems.add("the Patient Search popup stayed open after the Select box was clicked"); return false; }
        for (int i = 0; i < 40 && boxValue("DMSViewer.MRNO").isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(2500);
        lastMrn = boxValue("DMSViewer.MRNO");
        lastBanner = banner();
        if (lastMrn.isEmpty()) problems.add("the MRN box is still empty after the patient was selected");
        else if (lastBanner.isEmpty()) problems.add("the patient's details line did not appear");
        return ok();
    }

    /** The "Name : ... MRN: ... Mob. No : ... Gen : ... Age : ..." line the screen shows for the loaded patient ("" when none). */
    public String banner() {
        Object r = page.evaluate("() => {" + JS + " const e=[...document.querySelectorAll('div,span,label,b,strong,p')].filter(vis).filter(x=>/^Name\\s*:/.test(norm(x.textContent)) && /MRN\\s*:/.test(norm(x.textContent)))"
                + ".sort((a,b)=>norm(a.textContent).length-norm(b.textContent).length)[0]; return e? norm(e.textContent) : ''; }");
        return r == null ? "" : r.toString();
    }

    // ---- Files Found: the remark and Save ---------------------------------------------------------------------------------------------------------

    /** The remark the screen shows for the loaded patient (the textarea under Files Found). */
    public String remarkBox() {
        Object r = page.evaluate("() => { const t=document.querySelector(\"textarea[ng-model='DMSViewer.remark']\"); return t? t.value : ''; }");
        return r == null ? "" : r.toString().trim();
    }

    /** Replace the remark in the Remarks box with this text. The remark held before is left in {@link #lastRemarkBefore}. */
    public boolean enterRemark(String text) {
        problems.clear();
        lastRemark = text;
        lastRemarkBefore = remarkBox();
        Locator t = page.locator("textarea[ng-model='DMSViewer.remark']").first();
        try {
            t.scrollIntoViewIfNeeded();
            if (!t.isEnabled()) { problems.add("the Remarks box is disabled"); return false; }
            t.click(new Locator.ClickOptions().setTimeout(5000));
            t.fill(text, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the remark could not be typed: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(400);
        if (!text.equals(remarkBox())) problems.add("the Remarks box shows \"" + remarkBox() + "\" instead of the remark typed");
        else if (!text.equals(model("DMSViewer.remark"))) problems.add("the remark is not held by the model (\"" + model("DMSViewer.remark") + "\")");
        return ok();
    }

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^SaveRemark/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__dvDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__dvDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("DMSViewer.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__dvDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Save (under Files Found), answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String saveAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        lastSaveRequest = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^SaveRemark\\s*\\(/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__dvSave'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Save button is not on screen"); return ""; }
        try { page.locator("#__dvSave").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__dvSave'); if(e) e.removeAttribute('id'); }"); }
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
        lastSaveRequest = c[4].length() > 300 ? c[4].substring(0, 300) : c[4];
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
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    /**
     * The remark the SERVER holds for the loaded patient, read with the call the screen itself makes when a patient loads ({@code POST /api/DMSViewer/Fetch {ExecFlag:'FetchRemark', Id:<patient id>}}); the form is not
     * touched. "(unreadable)" when the call failed.
     */
    public String savedRemark() {
        Object r = page.evaluate("() => new Promise(res => { try { const e=document.querySelector(\"input[ng-model='DMSViewer.MRNO']\"); const sc=angular.element(e).scope();"
                + " const h=angular.element(document.body).injector().get('$http');"
                + " h.post('/api/DMSViewer/Fetch', {ExecFlag:'FetchRemark', Id: sc.DMSViewer.patientid}).then(x=>{ const d=x.data||[]; const t=d.map(v=>v.remark).filter(v=>v); res(t.length? String(t[0]).trim() : ''); }, er=>res('(unreadable)')); } catch(x){ res('(unreadable)'); } })");
        return r == null ? "(unreadable)" : r.toString();
    }
}
