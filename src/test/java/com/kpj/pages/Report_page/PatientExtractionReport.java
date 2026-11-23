package com.kpj.pages.Report_page;

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
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Report &gt; <b>Patient Extraction Report</b> — Page Object (route {@code #/PatientExtractionReport}).
 *
 * <p>Confirmed live (devhis build v2026.11.03.392). The sidebar's top-level <b>Report</b> holds Report Console, Patient Data Extraction and Patient Extraction Report. The screen has:</p>
 * <ul>
 *   <li><b>Patient Extraction List</b>: <b>From</b> ({@code PatientDataAnalysis.EnquiryFromDate}) and <b>To</b> ({@code .EnquiryToDate}), both {@code dd/MM/yyyy} boxes of a 720kb date picker (typed text is
 *       discarded: the day is clicked in the calendar, whose {@code prevMonth()} / {@code nextMonth()} links move between months; both boxes start at today) and <b>Search</b>
 *       ({@code fnSeachExtraction()}, {@code POST /api/PatientAnalysisReport/GetExtractionList} with {@code ExecFlag GRID} and the two dates in dd/MM/yyyy). Today alone usually holds nothing.</li>
 *   <li><b>List Of Item Enquiry</b> ({@code ui-grid #grid1}, 10 per page): Sel (a tick on every row) | Date/Time | Extracted By | Motive Of Extraction | Consent Given | <b>Attachment</b> (a link button,
 *       {@code OpenExtractionDocument(row)}) | <b>Print</b> (a printer button, {@code PrintExtraction(row)}). "Attachment" and "Print" are COLUMNS of the grid, not tabs.</li>
 *   <li>The Attachment symbol opens the <b>Extraction Details</b> popup ({@code #Extractiondtls}): a <b>Document List</b> (File Name | View | Remove), <b>Attach File</b> = a read-only name box
 *       ({@code ExtractionDocument.DocumentName}) beside <b>Choose File</b> ({@code triggerFileInputClick()}, which clicks a hidden {@code input[type=file]} whose change runs {@code ExtractionfileChanged(files)}),
 *       <b>Add</b> ({@code AddExtractionDocList(...)}, puts the chosen file in the Document List), <b>OK</b> ({@code fnSaveExtractionDetails()}, saves) and Close.</li>
 *   <li>The Print symbol opens the report (a PDF) in another browser tab.</li>
 * </ul>
 *
 * <p>Attaching is REAL: OK saves the file against the extraction record; it stays (it can be removed in the popup, which this page does not do).</p>
 */
public class PatientExtractionReport extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getextractionlist") && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall") && !low.contains("getmaster");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public PatientExtractionReport(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastFrom = "", lastTo = "", lastSearchRequest = "", lastToast = "", lastSaveMessage = "", lastDialog = "", lastFileName = "", lastRow = "", lastAddedFile = "", lastSelection = "";
    public int lastSearchRows = -1, lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String FROM = "PatientDataAnalysis.EnquiryFromDate", TO = "PatientDataAnalysis.EnquiryToDate";
    private static final String HREF = "#/patientextractionreport";
    private static final String TOP = "Report";
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

    /** Report &gt; Patient Extraction Report. The top-level entry is opened ONCE (the menu builds lazily; a second click while it is still building closes it again) and the link is found by its exact route. */
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__peLink'; }", HREF);
                try { page.locator("#__peLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("PatientExtractionReport.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__peLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        for (int i = 0; i < 60 && !onScreen(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return onScreen();
    }

    /** On the screen: the route AND the screen's own From box (a bounce to the dashboard has neither). */
    public boolean onScreen() {
        return page.url().toLowerCase().matches(".*#/patientextractionreport/?$")
                && Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return [...document.querySelectorAll('input')].some(e=>vis(e) && (e.getAttribute('ng-model')||'')==='" + FROM + "'); }"));
    }

    // ---- toasts ----------------------------------------------------------------------------------------------------

    /** Clear the toasts still on screen and start recording new ones (a toast of an earlier action must not be read as the answer to the next). */
    public void armToasts() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__peToasts=[]; if(window.__peObs) window.__peObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__peToasts.includes(m)) window.__peToasts.push(m); });"
                + " window.__peObs=new MutationObserver(add); window.__peObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    public String toasts() {
        Object r = page.evaluate("() => (window.__peToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    // ---- From / To (the 720kb date pickers) --------------------------------------------------------------------------------------

    private String boxValue(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); try{ const m=angular.element(e).scope().$eval(n); return m==null? '' : String(m); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** The date the To box holds as the screen opens (today on the application's clock); today on the browser's clock when it cannot be read. */
    public LocalDate defaultToDate() {
        try { return LocalDate.parse(boxValue(TO), DMY); } catch (Exception e) { return LocalDate.now(); }
    }

    private boolean calendarOpen(String sel) {
        Object r = page.evaluate("(s) => {" + JS + " const e=document.querySelector(s); const w=e && e.closest('.datepicker, datepicker'); const c=w && w.querySelector('._720kb-datepicker-calendar'); return !!c && vis(c) && [...c.querySelectorAll('._720kb-datepicker-calendar-day')].some(vis); }", sel);
        return Boolean.TRUE.equals(r);
    }

    /** The month the open calendar shows as {year, month 1-12}; null when its header cannot be read. */
    private int[] shownMonth(String sel) {
        Object r = page.evaluate("(s) => { const e=document.querySelector(s); const w=e && e.closest('.datepicker, datepicker'); const h=w && w.querySelector('._720kb-datepicker-calendar-header-middle'); return h? h.textContent : ''; }", sel);
        String t = r == null ? "" : r.toString();
        Matcher y = Pattern.compile("(\\d{4})").matcher(t);
        if (!y.find()) return null;
        for (int i = 0; i < 12; i++) if (t.contains(MONTHS[i])) return new int[]{Integer.parseInt(y.group(1)), i + 1};
        return null;
    }

    /**
     * Open the date picker of the box with this ng-model, move its calendar to the target month with the Prev / Next links and click the target day. The picker discards typed characters, so this is the only way
     * a value sticks. The days of the neighbouring months that fill the grid share their numbers with the real ones, so a day up to the 15th is the FIRST cell with that number in the grid and a later day is the
     * LAST. Verifies the box and the model.
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
            Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); const l=w && w.querySelector('a[ng-click=\"'+a[1]+'\"]'); if(!l) return false; l.id='__peNav'; return true; }", java.util.Arrays.asList(sel, link));
            if (!Boolean.TRUE.equals(tagged)) { problems.add("the " + what + " calendar has no " + link + " link"); return false; }
            try { page.locator("#__peNav").click(new Locator.ClickOptions().setTimeout(4000)); }
            catch (Exception e) { problems.add("the " + what + " calendar's " + link + " link could not be clicked"); return false; }
            finally { page.evaluate("() => { const e=document.getElementById('__peNav'); if(e) e.removeAttribute('id'); }"); }
            page.waitForTimeout(500);
        }
        int[] now = shownMonth(sel);
        if (now == null || now[0] != target.getYear() || now[1] != target.getMonthValue()) { problems.add("the " + what + " calendar did not reach " + MONTHS[target.getMonthValue() - 1] + " " + target.getYear()); return false; }
        Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); if(!w) return false;"
                + " const d=[...w.querySelectorAll('._720kb-datepicker-calendar-day')].filter(x=>x.offsetWidth && !/disabled/.test(x.className) && x.textContent.trim()===a[1]);"
                + " if(!d.length) return false; const x=(parseInt(a[1],10)<=15)? d[0] : d[d.length-1]; x.id='__peDay'; return true; }", java.util.Arrays.asList(sel, String.valueOf(target.getDayOfMonth())));
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the day " + target.getDayOfMonth() + " is not offered by the " + what + " calendar"); return false; }
        try { page.locator("#__peDay").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the day " + target.getDayOfMonth() + " could not be clicked in the " + what + " calendar"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__peDay'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(600);
        String want = target.format(DMY);
        String box = boxValue(ng), mod = model(ng);
        if (!want.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + want + "\"");
        else if (!want.equals(mod)) problems.add("the " + what + " is not held by the model (\"" + mod + "\")");
        if (calendarOpen(sel)) dismissDatePicker();
        return ok();
    }

    /** Enter From, then To. */
    public boolean enterDates(LocalDate from, LocalDate to) {
        problems.clear();
        if (!pickDate(FROM, from, "From date")) return false;
        lastFrom = boxValue(FROM);
        if (!pickDate(TO, to, "To date")) return false;
        lastTo = boxValue(TO);
        if (!lastFrom.equals(from.format(DMY)) || !lastTo.equals(to.format(DMY))) problems.add("the boxes show " + lastFrom + " - " + lastTo + " after the second date was picked");
        return ok();
    }

    // ---- Search and the list ------------------------------------------------------------------------------------------------------------

    /** Click Search and wait for the server's answer (an empty answer is a valid one). */
    public boolean clickSearch() {
        problems.clear();
        lastSearchRequest = "";
        lastSearchRows = -1;
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fnSeachExtraction/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__peSearch'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Search button is not on screen"); return false; }
        try {
            Response r = page.waitForResponse(x -> x.url().contains("/GetExtractionList"), new Page.WaitForResponseOptions().setTimeout(25000),
                    () -> page.locator("#__peSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            String post = r.request().postData();
            lastSearchRequest = post == null ? "" : post;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            Matcher m = Pattern.compile("\"ExtractionID\"").matcher(body);
            int n = 0;
            while (m.find()) n++;
            lastSearchRows = n;
            if (r.status() >= 400) problems.add("the server answered HTTP " + r.status() + " to the search");
        } catch (Exception e) { problems.add("Search did not reach the server: " + e.getMessage().split("\n")[0]); }
        finally { page.evaluate("() => { const e=document.getElementById('__peSearch'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        return ok();
    }

    /** The rows the list shows, each as {Date/Time, Extracted By, Motive Of Extraction, Consent Given}. */
    public List<String[]> rows() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#grid1 .ui-grid-canvas .ui-grid-row')].filter(r=>r.textContent.trim().length>0)"
                + ".map(r=>[...r.querySelectorAll('.ui-grid-cell')].slice(0,4).map(c=>norm(c.textContent))); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) {
            List<?> c = (List<?>) row;
            String[] v = new String[4];
            for (int i = 0; i < 4; i++) v[i] = i < c.size() ? String.valueOf(c.get(i)) : "";
            out.add(v);
        }
        return out;
    }

    /** The pager's "1 - 8 of 8 items" text. */
    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    /** The column headings of the list. */
    public List<String> headings() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#grid1 .ui-grid-header-cell')].map(h=>norm(h.textContent).replace(/\\s*\\d+$/,'')).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The rows whose Date/Time ("16 Oct 2026 08:41") is outside From..To (both dd/MM/yyyy, inclusive). */
    public List<String> outsideRange(LocalDate from, LocalDate to) {
        List<String> out = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);
        for (String[] r : rows()) {
            try {
                LocalDate d = java.time.LocalDateTime.parse(r[0], f).toLocalDate();
                if (d.isBefore(from) || d.isAfter(to)) out.add(r[0]);
            } catch (Exception e) { out.add(r[0] + " (unreadable date)"); }
        }
        return out;
    }

    // ---- selecting a row ---------------------------------------------------------------------------------------------------------------------

    /** Click the row (0-based among the rows shown) on its Extracted By cell; its text is left in {@link #lastRow}, what the screen's own row state says in {@link #lastSelection}. */
    public boolean clickRow(int index) {
        problems.clear();
        Object tagged = page.evaluate("(i) => {" + JS + " const rows=[...document.querySelectorAll('#grid1 .ui-grid-canvas .ui-grid-row')].filter(r=>r.textContent.trim().length>0); const r=rows[i]; if(!r) return '';"
                + " const c=r.querySelectorAll('.ui-grid-cell')[1]; c.id='__peCell'; c.scrollIntoView({block:'center'}); return [...r.querySelectorAll('.ui-grid-cell')].slice(0,4).map(x=>norm(x.textContent)).join(' | '); }", index);
        String text = tagged == null ? "" : tagged.toString();
        if (text.isEmpty()) { problems.add("row " + (index + 1) + " is not on screen"); return false; }
        lastRow = text;
        try { page.locator("#__peCell").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the row could not be clicked: " + e.getMessage().split("\n")[0]); return false; }
        waitForAngular(800);
        Object st = page.evaluate("() => { const c=document.getElementById('__peCell'); if(!c) return 'cell gone'; let s='none'; try{ const sc=angular.element(c).scope(); s='row.isSelected='+String(sc.row && sc.row.isSelected); }catch(e){} const r=c.closest('.ui-grid-row'); return s+'; row class=\"'+(r?r.className:'')+'\"'; }");
        lastSelection = st == null ? "" : st.toString();
        page.evaluate("() => { const e=document.getElementById('__peCell'); if(e) e.removeAttribute('id'); }");
        return true;
    }

    // ---- the Attachment popup ----------------------------------------------------------------------------------------------------------------------

    public boolean modalOpen() {
        Object r = page.evaluate("() => {" + JS + " const m=document.getElementById('Extractiondtls'); return !!m && vis(m) && /Document List/i.test(m.textContent||''); }");
        return Boolean.TRUE.equals(r);
    }

    /** The files the popup's Document List holds. */
    public List<String> documentNames() {
        Object r = page.evaluate("() => {" + JS + " const m=document.getElementById('Extractiondtls'); if(!m) return []; const t=[...m.querySelectorAll('table')].find(x=>/File Name/i.test(x.textContent)); if(!t) return [];"
                + " return [...t.querySelectorAll('tbody tr')].map(tr=>norm((tr.querySelector('td')||{}).textContent)).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    private boolean clickRowButton(int index, String ngPart, String id) {
        Object tagged = page.evaluate("(a) => {" + JS + " const rows=[...document.querySelectorAll('#grid1 .ui-grid-canvas .ui-grid-row')].filter(r=>r.textContent.trim().length>0); const r=rows[a[0]]; if(!r) return false;"
                + " const b=r.querySelector('button[ng-click*=\"'+a[1]+'\"]'); if(!b) return false; b.id=a[2]; b.scrollIntoView({block:'center'}); return true; }", java.util.Arrays.asList(String.valueOf(index), ngPart, id));
        if (!Boolean.TRUE.equals(tagged)) return false;
        try { page.locator("#" + id).click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("PatientExtractionReport: " + ngPart + " click failed - " + e.getMessage().split("\n")[0]); return false; }
        finally { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.removeAttribute('id'); }", id); }
    }

    /** Click the row's Attachment symbol and wait for the Extraction Details popup. */
    public boolean clickAttachment(int index) {
        problems.clear();
        if (!clickRowButton(index, "OpenExtractionDocument", "__peAtt")) { problems.add("the row's Attachment symbol could not be clicked"); return false; }
        for (int i = 0; i < 40 && !modalOpen(); i++) page.waitForTimeout(250);
        if (!modalOpen()) { problems.add("the Extraction Details popup did not open"); return false; }
        waitForAngular(800);
        return true;
    }

    private boolean clickModalButton(String ngPart, String id) {
        Object tagged = page.evaluate("(a) => {" + JS + " const m=document.getElementById('Extractiondtls'); if(!m) return false; const b=[...m.querySelectorAll('button')].filter(vis).find(x=>(x.getAttribute('ng-click')||'').indexOf(a[0])===0 || norm(x.textContent)===a[0]); if(!b) return false; b.id=a[1]; return true; }",
                java.util.Arrays.asList(ngPart, id));
        if (!Boolean.TRUE.equals(tagged)) return false;
        try { page.locator("#" + id).click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("PatientExtractionReport: " + ngPart + " click failed - " + e.getMessage().split("\n")[0]); return false; }
        finally { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.removeAttribute('id'); }", id); }
    }

    /** The text of the popup's read-only name box. */
    public String fileNameBox() {
        Object r = page.evaluate("() => { const e=document.querySelector(\"input[ng-model='ExtractionDocument.DocumentName']\"); return e? e.value : ''; }");
        return r == null ? "" : r.toString().trim();
    }

    /**
     * Click Choose File and hand the file to the browser's file chooser it opens (the screen's own {@code ExtractionfileChanged} then runs). Falls back to the hidden file input when no chooser opens. Returns
     * true when the name box shows the file's name afterwards.
     */
    public boolean chooseFile(Path file) {
        problems.clear();
        lastFileName = file.getFileName().toString();
        Object tagged = page.evaluate("() => {" + JS + " const m=document.getElementById('Extractiondtls'); if(!m) return false; const b=[...m.querySelectorAll('button')].filter(vis).find(x=>/^triggerFileInputClick/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__peChoose'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Choose File button is not in the popup"); return false; }
        boolean viaChooser = false;
        try {
            FileChooser fc = page.waitForFileChooser(new Page.WaitForFileChooserOptions().setTimeout(8000), () -> page.locator("#__peChoose").click(new Locator.ClickOptions().setTimeout(6000)));
            fc.setFiles(file);
            viaChooser = true;
        } catch (Exception e) {
            System.out.println("PatientExtractionReport: no file chooser opened (" + e.getMessage().split("\n")[0] + ") - using the file input");
            try { page.locator("input[type='file'][ng-model='ExtractionDocument.DocumentFile']").first().setInputFiles(file, new Locator.SetInputFilesOptions().setTimeout(10000)); }
            catch (Exception e2) { problems.add("the file could not be chosen: " + e2.getMessage().split("\n")[0]); return false; }
        } finally { page.evaluate("() => { const e=document.getElementById('__peChoose'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        for (int i = 0; i < 20 && fileNameBox().isEmpty(); i++) page.waitForTimeout(250);
        String shown = fileNameBox();
        if (!lastFileName.equalsIgnoreCase(shown)) { problems.add("the name box shows \"" + shown + "\" instead of \"" + lastFileName + "\""); return false; }
        lastSelection = viaChooser ? "through the file chooser the button opened" : "through the hidden file input (no chooser opened)";
        return true;
    }

    /** Click Add: the chosen file should join the popup's Document List. Returns false (toast in {@link #lastToast}) when it did not. */
    public boolean clickAdd() {
        problems.clear();
        lastAddedFile = "";
        List<String> before = documentNames();
        armToasts();
        if (!clickModalButton("AddExtractionDocList", "__peAdd")) { problems.add("the popup's Add button could not be clicked"); return false; }
        waitForAngular(1200);
        List<String> after = before;
        for (int i = 0; i < 24; i++) { after = documentNames(); if (after.size() > before.size()) break; page.waitForTimeout(250); }
        lastToast = toasts();
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
                + "   && !/^(fnSaveExtractionDetails|AddExtractionDocList|triggerFileInputClick|RemoveExtraction|ViewData)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__peDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__peDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("PatientExtractionReport.answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__peDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click the popup's OK (saves), answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String clickOkAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        armToasts();
        if (!clickModalButton("fnSaveExtractionDetails", "__peOk")) { problems.add("the popup's OK button could not be clicked"); return ""; }
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

    /** Close the popup with its Close button when it is still open. */
    public boolean closePopup() {
        if (!modalOpen()) return true;
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove()); }");
        clickModalButton("Close", "__peClose");
        for (int i = 0; i < 24 && modalOpen(); i++) page.waitForTimeout(250);
        waitForAngular(600);
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
            for (String[] c : saveCalls) { String b = c[3].replaceAll("\\s+", " "); sb.append("[").append(c[0]).append(" ").append(c[1]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append(" -> ").append(b, 0, Math.min(220, b.length())).append("] "); }
            for (String[] c : failedCalls) if (!saveCalls.contains(c)) sb.append("[FAILED ").append(c[0]).append(" ").append(c[2].replaceAll("^https?://[^/]+", "")).append("] ");
        }
        return sb.toString().trim();
    }

    public boolean savedByServer() { return lastSaveStatus == 1; }

    /** The toast is a success message AND the server did not refuse the save. The server spells several messages wrongly (Succesfully, sucessfully), so those are accepted too. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|created|submitted|updated|uploaded|attached").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !t.contains("already");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }

    // ---- Print -----------------------------------------------------------------------------------------------------------------------------

    /** Click the row's Print symbol; returns the number of tabs that were open before (for {@code PdfReport.capture}), -1 when it could not be clicked. */
    public int clickPrint(int index) {
        problems.clear();
        int before = page.context().pages().size();
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove()); }");
        if (!clickRowButton(index, "PrintExtraction", "__pePrint")) { problems.add("the row's Print symbol could not be clicked"); return -1; }
        waitForAngular(1500);
        return before;
    }
}
