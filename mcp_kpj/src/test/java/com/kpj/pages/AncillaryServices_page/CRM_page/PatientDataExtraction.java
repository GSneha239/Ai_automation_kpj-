package com.kpj.pages.AncillaryServices_page.CRM_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ancillary Services &gt; CRM &gt; <b>Patient Data Extraction</b> — Page Object (route {@code #/PatientAnalysisReport}; the entry is also listed under Report Console).
 *
 * <p>Confirmed live (devhis build v2026.11.11.438). The screen:</p>
 * <ul>
 *   <li><b>Patient Data extraction</b> filters ({@code PatientDataAnalysis.*}): Reg. Type OPD / IPD / ALL, blood group, gender, MRN (+ search buttons), country, state, city, location, company type, payor, and four
 *       date rows, each a TICK BOX with From / To pickers: Age, Reg. Date, <b>Visit Date</b> ({@code .ChkVisitDate}, UNTICKED at first, its From {@code .VisitFromDate} / To {@code .VisitToDate} are disabled until it is
 *       ticked; 720kb pickers, typed text discarded, both today by default) and Admission Date. <b>Search</b> is {@code SearchPatient();} ({@code POST /api/PatientAnalysisReport/Fetch ExecFlag PatientSearch}, dates
 *       in dd/MM/yyyy). Do not confuse it with {@code SearchPatientByMRNo()}, the magnifier beside the MRN box.</li>
 *   <li>The <b>Clinical Filter</b> grid ({@code ui-grid #grid1}, 10 per page): a pinned left <b>Sel</b> column whose cells hold a ✔ button ({@code .ui-grid-selection-row-header-buttons}, {@code selectButtonClick(row)}),
 *       then EMR (eye) | MRN No. | Name | NRIC | Gender | Date of Birth | Doctor | Diagnosis | PDPA Consent | Blood Group | Reg Date | Mobile No | E-mail | Marital Status.</li>
 *   <li><b>Save Data</b> ({@code data-toggle=modal data-target=#Extractiondtls}, disabled until a row is selected), Send Mail, Send SMS.</li>
 *   <li>The popup <b>Extraction Details</b> ({@code #Extractiondtls}): <b>Motive of Extraction*</b> ({@code #MotiveOfExtraction}, {@code ExtractionDetails.MotiveOfExtraction}), <b>Consent given*</b> checkbox
 *       ({@code .ConsentGiven}), the Document List (File Name | View | Remove), <b>Attach File</b> (a read-only name box, <b>Choose File</b> {@code triggerFileInputClick()} which clicks the hidden
 *       {@code #SelectExtractionFile}), <b>Add</b> ({@code AddExtractionDocList(...)}), <b>OK</b> ({@code fnSaveExtractionDetails()}) and Close.</li>
 * </ul>
 *
 * <p>The screen's own rules: OK is refused with "Document is required" while Consent given is ticked and the Document List is empty; a file is accepted when its name (split at the dots, the SECOND part) is pdf,
 * txt, png, jpg, jpeg, xlsx, docx or doc - so a name must contain exactly one dot - and it is under 1 MB; it is uploaded at once ({@code POST /api/patientanalysisreport/getdocumentdata}) and Add only works after that
 * answer. A successful OK shows "Saved Successfully.", hides the popup and opens {@code #/PatientExtractionReport} in a NEW TAB.</p>
 *
 * <p>Saving is REAL: an extraction record with its document is created for the chosen patient and stays.</p>
 */
public class PatientDataExtraction extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall") && !low.contains("getmaster") && !low.contains("getuser") && !low.contains("getdocumentdata");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body,
                    r.request().postData() == null ? "" : r.request().postData()};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public PatientDataExtraction(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastFrom = "", lastTo = "", lastPager = "", lastPatient = "", lastMotive = "", lastFileName = "", lastAddedFile = "", lastToast = "", lastSaveMessage = "", lastDialog = "", lastSaveRequest = "", lastFileVia = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String M = "PatientDataAnalysis.";
    private static final String HREF = "#/patientanalysisreport";
    private static final String PARENT = "Ancillary Services", GROUP = "CRM";
    private static final String MODAL = "Extractiondtls";
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ---- navigation ------------------------------------------------------------------------------------------

    /** The link to the screen INSIDE the Ancillary Services > CRM group (the same screen is also listed under Report Console). Tags it and returns true when it is on screen. */
    private boolean tagCrmLink() {
        Object r = page.evaluate("(a) => {" + JS + " const top=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===a[0]); if(!top) return false; const tli=top.closest('li'); if(!tli) return false;"
                + " const g=[...tli.querySelectorAll('a')].find(x=>x!==top && norm(x.textContent)===a[1]); if(!g) return false; const gli=g.closest('li'); if(!gli) return false;"
                + " const l=[...gli.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===a[2]); if(!l) return false; l.id='__pdLink'; return true; }", Arrays.asList(PARENT, GROUP, HREF));
        return Boolean.TRUE.equals(r);
    }

    private boolean waitCrmLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (tagCrmLink()) return true; page.waitForTimeout(250); }
        return tagCrmLink();
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

    /** Ancillary Services &gt; CRM &gt; Patient Data Extraction. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group again). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='" + PARENT + "')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!tagCrmLink()) {
                if (innerState(PARENT, GROUP) < 0) {
                    if (topState(PARENT) != 1) clickTop(PARENT);
                    for (int i = 0; i < 48 && innerState(PARENT, GROUP) < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (innerState(PARENT, GROUP) == 0 && !tagCrmLink()) clickInner(PARENT, GROUP);
                waitCrmLink(12000);
            }
            if (tagCrmLink()) {
                try { page.locator("#__pdLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("PatientDataExtraction.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__pdLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !onScreen(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    /** On the screen: the route AND the screen's own MRN box (a bounce to the dashboard has neither). */
    public boolean onScreen() {
        return page.url().toLowerCase().matches(".*#/patientanalysisreport/?$")
                && Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll('input')].some(e=>vis(e) && (e.getAttribute('ng-model')||'')==='" + M + "MRNo'); }"));
    }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__pdToasts=[]; if(window.__pdObs) window.__pdObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__pdToasts.includes(m)) window.__pdToasts.push(m); });"
                + " window.__pdObs=new MutationObserver(add); window.__pdObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__pdToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- Visit Date (tick box + the 720kb date pickers) -------------------------------------------------------------------------------

    private String boxValue(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e? (e.value||'') : ''; }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); try{ const m=angular.element(e).scope().$eval(n); return m==null? '' : String(m); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** A value of the screen's own scope that no box is bound to (e.g. {@code mySelectedRows[0].MRNo}); "" when unset. */
    private String scopeValue(String expr) {
        Object v = page.evaluate("(x) => { try{ const e=document.querySelector(\"input[ng-model='" + M + "MRNo']\"); const m=angular.element(e).scope().$eval(x); return m==null? '' : String(m); }catch(err){ return ''; } }", expr);
        return v == null ? "" : v.toString().trim();
    }

    public boolean visitDateTicked() {
        Object r = page.evaluate("(n) => { const c=document.querySelector(\"input[ng-model='\"+n+\"']\"); return !!c && c.checked && !c.disabled; }", M + "ChkVisitDate");
        return Boolean.TRUE.equals(r);
    }

    /** Tick the Visit Date tick box (it starts unticked) so that its From / To boxes become enabled. */
    public boolean tickVisitDate() {
        problems.clear();
        if (visitDateTicked()) return true;
        try { page.locator("input[ng-model='" + M + "ChkVisitDate']").first().check(new Locator.CheckOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the Visit Date tick box could not be ticked: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(600);
        if (!visitDateTicked()) problems.add("the Visit Date tick box is not ticked afterwards");
        return ok();
    }

    /** The date the Visit Date To box holds (today on the application's clock); today on the browser's clock when it cannot be read. */
    public LocalDate defaultToDate() {
        try { return LocalDate.parse(boxValue(M + "VisitToDate"), DMY); } catch (Exception e) { return LocalDate.now(); }
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
            Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); const l=w && w.querySelector('a[ng-click=\"'+a[1]+'\"]'); if(!l) return false; l.id='__pdNav'; return true; }", Arrays.asList(sel, link));
            if (!Boolean.TRUE.equals(tagged)) { problems.add("the " + what + " calendar has no " + link + " link"); return false; }
            try { page.locator("#__pdNav").click(new Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { problems.add("the " + what + " calendar's " + link + " link could not be clicked"); return false; }
            finally { page.evaluate("() => { const e=document.getElementById('__pdNav'); if(e) e.removeAttribute('id'); }"); }
            page.waitForTimeout(500);
        }
        int[] now = shownMonth(sel);
        if (now == null || now[0] != target.getYear() || now[1] != target.getMonthValue()) { problems.add("the " + what + " calendar did not reach " + MONTHS[target.getMonthValue() - 1] + " " + target.getYear()); return false; }
        Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); if(!w) return false;"
                + " const d=[...w.querySelectorAll('._720kb-datepicker-calendar-day')].filter(x=>x.offsetWidth && !/disabled/.test(x.className) && x.textContent.trim()===a[1]);"
                + " if(!d.length) return false; const x=(parseInt(a[1],10)<=15)? d[0] : d[d.length-1]; x.id='__pdDay'; return true; }", Arrays.asList(sel, String.valueOf(target.getDayOfMonth())));
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the day " + target.getDayOfMonth() + " is not offered by the " + what + " calendar"); return false; }
        try { page.locator("#__pdDay").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the day " + target.getDayOfMonth() + " could not be clicked in the " + what + " calendar"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__pdDay'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(600);
        String want = target.format(DMY);
        String box = boxValue(ng), mod = model(ng);
        if (!want.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + want + "\"");
        else if (!want.equals(mod)) problems.add("the " + what + " is not held by the model (\"" + mod + "\")");
        if (calendarOpen(sel)) dismissDatePicker();
        return ok();
    }

    /** Enter the Visit Date From through its calendar; To is left as it is (today). */
    public boolean enterVisitFrom(LocalDate from) {
        problems.clear();
        boolean ok = pickDate(M + "VisitFromDate", from, "Visit Date From");
        if (ok) { lastFrom = boxValue(M + "VisitFromDate"); lastTo = boxValue(M + "VisitToDate"); }
        return ok;
    }

    // ---- Search and the Clinical Filter grid ----------------------------------------------------------------------------------------------

    /** Click Search ({@code SearchPatient();}) and wait for the server's answer. */
    public boolean clickSearch() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>(x.getAttribute('ng-click')||'').replace(/\\s+/g,'')==='SearchPatient();'); if(!b) return false; b.id='__pdSearch'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Search button is not on screen"); return false; }
        try {
            page.waitForResponse(x -> x.url().contains("/PatientAnalysisReport/Fetch") && String.valueOf(x.request().postData()).contains("PatientSearch"), new Page.WaitForResponseOptions().setTimeout(40000),
                    () -> page.locator("#__pdSearch").click(new Locator.ClickOptions().setTimeout(6000)));
        } catch (Exception e) { problems.add("Search did not reach the server: " + e.getMessage().split("\n")[0]); }
        finally { page.evaluate("() => { const e=document.getElementById('__pdSearch'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        for (int i = 0; i < 40 && rows().isEmpty(); i++) page.waitForTimeout(250);
        lastPager = pagerText();
        return ok();
    }

    /** The rows the grid shows as {MRN No., Name, Gender}. */
    public List<String[]> rows() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#grid1 .ui-grid-canvas .ui-grid-row')].filter(r=>r.textContent.trim().length>0)"
                + ".map(r=>{ const c=[...r.querySelectorAll('.ui-grid-cell')].map(x=>norm(x.textContent)); return [c[1]||'', c[2]||'', c[4]||'']; }); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2))}); }
        return out;
    }

    /** The pager's "1 - 10 of 63 items" text. */
    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    /** The column headings of the grid. */
    public List<String> headings() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#grid1 .ui-grid-header-cell')].map(h=>norm(h.textContent).replace(/\\s*\\d+$/,'')).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The first row (0-based) whose name does not read "Unknown" (those are emergency placeholders); 0 when every row does. */
    public int preferredRow() {
        List<String[]> rs = rows();
        for (int i = 0; i < rs.size(); i++) if (!rs.get(i)[1].toLowerCase().contains("unknown")) return i;
        return 0;
    }

    public boolean saveDataEnabled() {
        Object r = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>norm(x.textContent)==='Save Data'); return !!b && !b.disabled; }");
        return Boolean.TRUE.equals(r);
    }

    /** Click the ✔ (tick symbol) of the row's Sel cell: the row becomes selected and Save Data is enabled. The patient is left in {@link #lastPatient}. */
    public boolean tickRow(int index) {
        problems.clear();
        if (saveDataEnabled()) problems.add("Save Data was already enabled before a row was ticked");
        Object tagged = page.evaluate("(i) => {" + JS + " const rows=[...document.querySelectorAll('#grid1 .ui-grid-pinned-container .ui-grid-row')]; const r=rows[i]; if(!r) return false;"
                + " const b=r.querySelector('.ui-grid-selection-row-header-buttons'); if(!b) return false; b.id='__pdTick'; b.scrollIntoView({block:'center'}); return true; }", index);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("row " + (index + 1) + " has no tick symbol on screen"); return false; }
        try { page.locator("#__pdTick").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the tick symbol could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__pdTick'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 24 && !saveDataEnabled(); i++) page.waitForTimeout(250);
        waitForAngular(500);
        String mrn = scopeValue("mySelectedRows[0].MRNo"), name = scopeValue("mySelectedRows[0].FirstName");
        lastPatient = mrn + (name.isEmpty() ? "" : " " + name.trim());
        if (!saveDataEnabled()) problems.add("Save Data is still disabled after the tick symbol was clicked");
        else if (mrn.isEmpty()) problems.add("the screen holds no selected patient after the tick symbol was clicked");
        return ok();
    }

    // ---- the Extraction Details popup ----------------------------------------------------------------------------------------------------------

    public boolean popupOpen() {
        Object r = page.evaluate("(id) => {" + JS + " const m=document.getElementById(id); return !!m && vis(m) && /Document List/i.test(m.textContent||''); }", MODAL);
        return Boolean.TRUE.equals(r);
    }

    /** Click Save Data and wait for the Extraction Details popup. */
    public boolean clickSaveData() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>norm(x.textContent)==='Save Data' && !x.disabled); if(!b) return false; b.id='__pdSaveData'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("Save Data is not enabled on screen"); return false; }
        try { page.locator("#__pdSaveData").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Save Data could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__pdSaveData'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 40 && !popupOpen(); i++) page.waitForTimeout(250);
        if (!popupOpen()) { problems.add("the Extraction Details popup did not open"); return false; }
        waitForAngular(800);
        return true;
    }

    public boolean enterMotive(String text) {
        problems.clear();
        lastMotive = text;
        Locator t = page.locator("#MotiveOfExtraction").first();
        try {
            t.click(new Locator.ClickOptions().setTimeout(5000));
            t.fill(text, new Locator.FillOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("the motive could not be typed: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(400);
        if (!text.equals(boxValue("ExtractionDetails.MotiveOfExtraction"))) problems.add("the Motive box shows \"" + boxValue("ExtractionDetails.MotiveOfExtraction") + "\" instead of the motive typed");
        else if (!text.equals(model("ExtractionDetails.MotiveOfExtraction"))) problems.add("the motive is not held by the model");
        return ok();
    }

    public boolean consentTicked() {
        Object r = page.evaluate("() => { const c=document.querySelector(\"input[ng-model='ExtractionDetails.ConsentGiven']\"); return !!c && c.checked; }");
        return Boolean.TRUE.equals(r);
    }

    public boolean tickConsent() {
        problems.clear();
        try { page.locator("input[ng-model='ExtractionDetails.ConsentGiven']").first().check(new Locator.CheckOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the Consent given box could not be ticked: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(400);
        if (!consentTicked()) problems.add("the Consent given box is not ticked afterwards");
        else if (!"true".equals(model("ExtractionDetails.ConsentGiven"))) problems.add("the consent is not held by the model (\"" + model("ExtractionDetails.ConsentGiven") + "\")");
        return ok();
    }

    /** The files the popup's Document List holds. */
    public List<String> documentNames() {
        Object r = page.evaluate("(id) => {" + JS + " const m=document.getElementById(id); if(!m) return []; const t=[...m.querySelectorAll('table')].find(x=>/File Name/i.test(x.textContent)); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(tr=>norm((tr.querySelector('td')||{}).textContent)).filter(Boolean); }", MODAL);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    private boolean clickModalButton(String ngPart, String id) {
        Object tagged = page.evaluate("(a) => {" + JS + " const m=document.getElementById(a[2]); if(!m) return false; const b=[...m.querySelectorAll('button')].filter(vis).find(x=>(x.getAttribute('ng-click')||'').indexOf(a[0])===0 || norm(x.textContent)===a[0]); if(!b) return false; b.id=a[1]; b.scrollIntoView({block:'center'}); return true; }",
                Arrays.asList(ngPart, id, MODAL));
        if (!Boolean.TRUE.equals(tagged)) return false;
        try { page.locator("#" + id).click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("PatientDataExtraction: " + ngPart + " click failed - " + e.getMessage().split("\n")[0]); return false; }
        finally { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.removeAttribute('id'); }", id); }
    }

    /** The text of the popup's read-only file-name box. */
    public String fileNameBox() {
        Object r = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='ExtractionDocument.DocumentName']\"); return e? e.value : ''; }");
        return r == null ? "" : r.toString().trim();
    }

    /**
     * Click Choose File and hand the file to the browser's file chooser it opens (the screen's {@code ExtractionfileChanged} then runs and uploads it at once); waits for the upload's answer. Falls back to the hidden
     * file input when no chooser opens. Returns true when the name box shows the file's name afterwards.
     */
    public boolean chooseFile(Path file) {
        problems.clear();
        lastFileName = file.getFileName().toString();
        Object tagged = page.evaluate("(id) => {" + JS + " const m=document.getElementById(id); if(!m) return false; const b=[...m.querySelectorAll('button')].filter(vis).find(x=>/^triggerFileInputClick/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__pdChoose'; return true; }", MODAL);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Choose File button is not in the popup"); return false; }
        armToasts();
        boolean viaChooser = false;
        try {
            page.waitForResponse(x -> x.url().toLowerCase().contains("getdocumentdata"), new Page.WaitForResponseOptions().setTimeout(30000), () -> {
                FileChooser fc = page.waitForFileChooser(new Page.WaitForFileChooserOptions().setTimeout(8000), () -> page.locator("#__pdChoose").click(new Locator.ClickOptions().setTimeout(6000)));
                fc.setFiles(file);
            });
            viaChooser = true;
        } catch (Exception e) {
            System.out.println("PatientDataExtraction: file chooser/upload path failed (" + e.getMessage().split("\n")[0] + ") - using the file input");
            try { page.locator("#SelectExtractionFile").first().setInputFiles(file, new Locator.SetInputFilesOptions().setTimeout(10000)); }
            catch (Exception e2) { problems.add("the file could not be chosen: " + e2.getMessage().split("\n")[0]); return false; }
            page.waitForTimeout(3000);
        } finally { page.evaluate("() => { const e=document.getElementById('__pdChoose'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        for (int i = 0; i < 20 && fileNameBox().isEmpty(); i++) page.waitForTimeout(250);
        page.waitForTimeout(1500);                                         // the upload answer arms the screen's file object, which Add needs
        lastFileVia = viaChooser ? "through the file chooser the button opened, uploaded at once" : "through the hidden file input (no chooser opened)";
        String shown = fileNameBox();
        lastToast = toasts();
        if (!lastFileName.equalsIgnoreCase(shown)) { problems.add("the name box shows \"" + shown + "\" instead of \"" + lastFileName + "\"" + (lastToast.isEmpty() ? "" : " (toast: \"" + lastToast + "\")")); return false; }
        return true;
    }

    /** Click Add: the chosen file should join the Document List. Returns false (toast in {@link #lastToast}) when it did not; the add is tried again once when the upload answer was not ready. */
    public boolean clickAdd() {
        problems.clear();
        lastAddedFile = "";
        List<String> before = documentNames();
        List<String> after = before;
        for (int attempt = 0; attempt < 2 && after.size() <= before.size(); attempt++) {
            armToasts();
            if (!clickModalButton("AddExtractionDocList", "__pdAdd")) { problems.add("the popup's Add button could not be clicked"); return false; }
            waitForAngular(1200);
            for (int i = 0; i < 16; i++) { after = documentNames(); if (after.size() > before.size()) break; page.waitForTimeout(250); }
            lastToast = toasts();
            if (after.size() <= before.size() && attempt == 0) { page.waitForTimeout(3000); if (fileNameBox().isEmpty()) break; }
        }
        if (after.size() <= before.size()) { problems.add("Add put no file into the Document List" + (lastToast.isEmpty() ? "" : " (toast: \"" + lastToast + "\")")); return false; }
        for (String f : after) if (!before.contains(f)) lastAddedFile = f;
        if (lastAddedFile.isEmpty()) lastAddedFile = after.get(after.size() - 1);
        return true;
    }

    /** If a confirmation dialog (a fixed overlay with a Save / Yes / OK / Confirm button that is NOT one of the popup's own) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(fnSaveExtractionDetails|AddExtractionDocList|triggerFileInputClick)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__pdDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__pdDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("PatientDataExtraction.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__pdDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click the popup's OK ({@code fnSaveExtractionDetails()}) and wait for the toast. Returns the toast text ("" if none appeared); the server's answer is in {@link #lastSaveStatus} / {@link #lastSaveMessage}. */
    public String clickOkAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        lastSaveRequest = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        if (!clickModalButton("fnSaveExtractionDetails", "__pdOk")) { problems.add("the popup's OK button could not be clicked"); return ""; }
        long end = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < end) {
            String d = answerDialog();
            if (!d.isEmpty()) lastDialog = lastDialog.isEmpty() ? d : lastDialog + " > " + d;
            lastToast = toasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(350);
        }
        page.waitForTimeout(1200);
        lastToast = toasts();
        parseSaveAnswer();
        return lastToast;
    }

    /** Wait for the Patient Extraction Report screen to open in a NEW TAB (one beyond {@code tabsBefore}); null when none came. */
    public Page awaitReportTab(int tabsBefore, int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            List<Page> pages = page.context().pages();
            for (int i = tabsBefore; i < pages.size(); i++) {
                Page t = pages.get(i);
                try { if (t.url() != null && t.url().toLowerCase().contains("patientextractionreport")) return t; } catch (Exception ignore) { }
            }
            page.waitForTimeout(400);
        }
        return null;
    }

    /** True when the popup is gone. */
    public boolean popupClosed() {
        for (int i = 0; i < 24 && popupOpen(); i++) page.waitForTimeout(250);
        return !popupOpen();
    }

    // ---- the save answer --------------------------------------------------------------------------------------------------------------------

    private void parseSaveAnswer() {
        lastSaveStatus = -1;
        lastSaveMessage = "";
        String[] c = null;
        synchronized (saveCalls) {
            if (!saveCalls.isEmpty()) c = saveCalls.get(saveCalls.size() - 1);
            else if (!failedCalls.isEmpty()) c = failedCalls.get(failedCalls.size() - 1);
        }
        if (c == null) return;
        lastSaveRequest = c[4].length() > 300 ? c[4].substring(0, 300) + "..." : c[4];
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
                && !t.contains("required");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
