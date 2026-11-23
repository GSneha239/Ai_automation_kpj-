package com.kpj.pages.Report_page.Report_Console;

import com.kpj.core.PdfUtil;
import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Report &gt; <b>Report Console</b> &gt; <b>Investigation Reports</b> - Page Object (route {@code #/reportconsole}).
 *
 * <p>The console has one button per report category on the left ({@code #btnli0InvestigationReports} "Investigation Reports", ...). A category fills the <b>Reports</b> dropdown
 * ({@code RptColsole.Report}); a report fills the <b>Filter</b> dropdown ({@code RptColsole.Parameter}); <b>Add</b> ({@code AddFilter()}) puts the filter in the table (Filter Name | Filter Value, one row per filter, each
 * with a <b>Add Value</b> button) and <b>Add Value</b> opens that filter's popup:</p>
 * <ul>
 *   <li><b>From Date_To Date</b> - {@code RptColsole.FromDate} / {@code RptColsole.ToDate} (720kb pickers; To defaults to today), OK {@code fnAdd('FromToDate')};</li>
 *   <li><b>Ordering Doctor</b> ({@code #ModalDoctorList}) - Location {@code RptColsole.SelectedLocationID}, Payable Type {@code RptColsole.SelectedPayableType}, the "Select User" table with a checkbox per user
 *       ({@code DL.IsSelected}), OK {@code fnAdd('Doctor')};</li>
 *   <li><b>Diagnosis</b> ({@code #ModalDiagnosis}) - smart search box {@code searchText} + search button {@code fnGetDiagnosis(searchText);}, the "Select Diagnosis" table with a checkbox per row ({@code G.IsSelected}),
 *       OK {@code fnAdd('Diagnosis')}.</li>
 * </ul>
 * <p><b>Show Report</b> ({@code ShowReport($event)}) opens the report in another tab (a PDF); {@link #captureReport} reads it, including its text (PDFBox), so a test can check which report it is.</p>
 *
 * <p>Built from a report-by-report script (Cancer Summary Report first); further Investigation reports reuse the same generic methods.</p>
 */
public class Investigation_Reports extends BasePage {

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const modal=()=>[...document.querySelectorAll('.modal')].filter(m=>m.classList.contains('in') && vis(m)).pop()||null;";

    public static final String CATEGORY = "Investigation Reports";
    private static final String ROUTE = "#/reportconsole";

    public Investigation_Reports(Page page) { super(page); }

    /** What the last action could not do (empty = fine). */
    public final List<String> problems = new ArrayList<>();

    private static String firstLine(Exception e) {
        String m = String.valueOf(e.getMessage()).replace((char) 10, (char) 32).replace((char) 13, (char) 32);
        return m.length() > 220 ? m.substring(0, 220) : m;
    }

    // ---- navigation: Report > Report Console ---------------------------------------------------------------------------------------------------

    /** Report &gt; Report Console through the sidebar (the "Report" group is opened only when it is closed; the same walk as the Sample Rejection report's). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Report')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            page.evaluate("() => {" + JS + " const top=[...document.querySelectorAll('a')].find(a=>/^\\s*report\\s*$/i.test(norm(a.textContent)) && vis(a) && !(a.getAttribute('href')||'').toLowerCase().includes('reportconsole'));"
                    + " const li=top&&top.closest('li'); if(top && !(li && li.classList.contains('menu-open'))) top.click(); }");
            page.waitForTimeout(2500);
            page.evaluate("() => {" + JS + " const a=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('href')||'').toLowerCase()==='#/reportconsole' && vis(x)); if(a) a.click(); }");
            try {
                page.waitForFunction("() => !!document.querySelector(\"[ng-model='RptColsole.Report']\")", null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains(ROUTE) && Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"[ng-model='RptColsole.Report']\")"));
    }

    // ---- category -> report -> filter --------------------------------------------------------------------------------------------------------------

    /** The category buttons of the console's left side, in order. */
    public List<String> categories() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#divbtnGeneration button')].filter(vis).map(b=>norm(b.textContent)); }");
        return strings(r);
    }

    /** Click the category button (e.g. "Investigation Reports") and wait for the Reports dropdown to be filled. */
    public boolean selectCategory(String label) {
        problems.clear();
        try {
            // the buttons are {@code <button id="btnli0InvestigationReports">} etc. (the label without spaces)
            String id = "btnli0" + label.replaceAll("\\s+", "");
            Locator b = page.locator("#" + id).first();
            try {
                b.click(new Locator.ClickOptions().setTimeout(12000));
            } catch (Exception covered) {
                // a loader / overlay can sit over the button for a while: the button's own ng-click is what the app listens to
                Object js = page.evaluate("(i) => { const e=document.getElementById(i); if(!e) return false; e.click(); return true; }", id);
                if (!Boolean.TRUE.equals(js)) throw covered;
                System.out.println("Investigation_Reports: the category button was clicked through the DOM (" + firstLine(covered) + ")");
            }
        } catch (Exception e) { problems.add("category \"" + label + "\": " + firstLine(e)); return false; }
        for (int i = 0; i < 40 && reportOptions().isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(1500);
        if (reportOptions().isEmpty()) problems.add("the Reports dropdown stayed empty after clicking \"" + label + "\"");
        return problems.isEmpty();
    }

    private static List<String> strings(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    private List<String> options(String ngModel) {
        Object r = page.evaluate("(m) => {" + JS + " const s=document.querySelector(\"[ng-model='\"+m+\"']\"); return s? [...s.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*\\s*select/i.test(t)) : []; }", ngModel);
        return strings(r);
    }

    /** The Reports dropdown's options for the chosen category (placeholder excluded). */
    public List<String> reportOptions() { return options("RptColsole.Report"); }

    /** The Filter dropdown's options for the chosen report (placeholder excluded). */
    public List<String> filterOptions() { return options("RptColsole.Parameter"); }

    private String shown(String ngModel) {
        Object r = page.evaluate("(m) => {" + JS + " const s=document.querySelector(\"[ng-model='\"+m+\"']\"); return s && s.selectedIndex>=0 ? norm(s.options[s.selectedIndex].textContent) : ''; }", ngModel);
        return r == null ? "" : r.toString();
    }

    private String select(String ngModel, String what, String label) {
        problems.clear();
        if (!options(ngModel).contains(label)) { problems.add("\"" + label + "\" is not offered by " + what + " (" + options(ngModel) + ")"); return ""; }
        try { page.locator("select[ng-model='" + ngModel + "']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(label), new Locator.SelectOptionOptions().setTimeout(4000)); }
        catch (Exception notActionable) {
            // some of the console's selects (e.g. the popups') are never "actionable" for Playwright although they work: pick the option the way the page does, with the change event
            Object js = page.evaluate("(a) => { const s=document.querySelector(\"select[ng-model='\"+a[0]+\"']\"); if(!s) return false; const o=[...s.options].find(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()===a[1]); if(!o) return false;"
                    + " s.value=o.value; s.dispatchEvent(new Event('change',{bubbles:true})); return true; }", Arrays.asList(ngModel, label));
            if (!Boolean.TRUE.equals(js)) { problems.add("\"" + label + "\" could not be selected in " + what + ": " + firstLine(notActionable)); return ""; }
        }
        waitForAngular(1500);
        String s = shown(ngModel);
        if (!label.equals(s)) { problems.add(what + " shows \"" + s + "\" instead of \"" + label + "\""); return ""; }
        return s;
    }

    /** Choose a report in the Reports dropdown; returns the option shown ("" + {@link #problems} on failure). The Filter dropdown fills from it. */
    public String selectReport(String name) {
        String s = select("RptColsole.Report", "Reports", name);
        for (int i = 0; i < 24 && !s.isEmpty() && filterOptions().isEmpty(); i++) page.waitForTimeout(250);
        return s;
    }

    /** Choose a filter in the Filter dropdown (e.g. "From Date_To Date"). */
    public String selectFilter(String name) { return select("RptColsole.Parameter", "Filter", name); }

    /** Click <b>Add</b> ({@code AddFilter()}) and wait for the filter's row in the table. */
    public boolean clickAdd(String filterName) {
        problems.clear();
        int before = filterRows().size();
        try { page.locator("button[ng-click='AddFilter()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Add: " + firstLine(e)); return false; }
        for (int i = 0; i < 24 && !hasFilter(filterName) && filterRows().size() <= before; i++) page.waitForTimeout(250);
        waitForAngular(800);
        return problems.isEmpty();
    }

    /** The filter table's rows (not the popups') as {Filter Name, Filter Value}. */
    public List<String[]> filterRows() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].filter(vis).find(x=>!x.closest('.modal') && /Filter Name/i.test(norm(x.rows[0]&&x.rows[0].textContent)) && /Filter Value/i.test(norm(x.rows[0].textContent)));"
                + " if(!t) return []; return [...t.rows].slice(1).map(r=>{ const c=[...r.cells]; const name=norm((c[1]||{}).textContent); const val=norm((c[2]||{}).textContent).replace(/\\s*Add Value\\s*$/i,''); return [name,val]; }); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    /** Click the console's <b>Clear</b> ({@code ClearParameters()}): empties the filter table so the next report starts clean. */
    public boolean clickClear() {
        problems.clear();
        try { page.locator("button[ng-click='ClearParameters()']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Clear: " + firstLine(e)); return false; }
        waitForAngular(1200);
        return true;
    }

    public boolean hasFilter(String name) {
        for (String[] r : filterRows()) if (r[0].equals(name)) return true;
        return false;
    }

    /** The Filter Value cell of a filter ("" when none yet). */
    public String filterValue(String name) {
        for (String[] r : filterRows()) if (r[0].equals(name)) return r[1];
        return "";
    }

    // ---- the popups -----------------------------------------------------------------------------------------------------------------------------

    public boolean modalOpen() { return Boolean.TRUE.equals(page.evaluate("() => {" + JS + " return !!modal(); }")); }

    /** Click <b>Add Value</b> on the row of the filter and wait for its popup. */
    public boolean clickAddValue(String filterName) {
        problems.clear();
        int idx = -1;
        List<String[]> rows = filterRows();
        for (int i = 0; i < rows.size(); i++) if (rows.get(i)[0].equals(filterName)) idx = i;
        if (idx < 0) { problems.add("no row for the filter \"" + filterName + "\" (" + rows.size() + " row(s))"); return false; }
        try { page.locator("[ng-model='RptColsole.AddValue[$index]']").nth(idx).click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Add Value: " + firstLine(e)); return false; }
        for (int i = 0; i < 40 && !modalOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1500);
        if (!modalOpen()) problems.add("the popup of \"" + filterName + "\" did not open");
        return problems.isEmpty();
    }

    /** Click the popup's OK ({@code fnAdd('<kind>')}: FromToDate / Doctor / Diagnosis) and wait for it to close. */
    public boolean clickOk(String kind) {
        problems.clear();
        try { page.locator(".modal.in button[ng-click=\"fnAdd('" + kind + "')\"]").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("OK (" + kind + "): " + firstLine(e)); return false; }
        for (int i = 0; i < 40 && modalOpen(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        if (modalOpen()) problems.add("the popup is still open after OK (the screen said: " + toast() + ")");
        return problems.isEmpty();
    }

    /** The warning / success toast text on screen (""). */
    public String toast() {
        try {
            Object r = page.evaluate("() => [...document.querySelectorAll('.toast-message')].map(e=>(e.textContent||'').replace(/\\s+/g,' ').trim()).join(' | ')");
            return r == null ? "" : r.toString();
        } catch (Exception e) { return ""; }
    }

    // -- From Date_To Date popup

    private String val(String ngModel) {
        Object r = page.evaluate("(m) => { const e=document.querySelector(\"[ng-model='\"+m+\"']\"); return e? e.value : ''; }", ngModel);
        return r == null ? "" : r.toString();
    }

    public String fromDate() { return val("RptColsole.FromDate"); }
    public String toDate() { return val("RptColsole.ToDate"); }

    /**
     * From Date: open the picker, move to the month of {@code target} from the month the From box currently shows (the console keeps the dates of the previous report, so the box is not always today's)
     * with the previous / next month arrows, and click the day. Returns the From box's value.
     */
    public String pickFromDate(java.time.LocalDate target) {
        problems.clear();
        try {
            page.locator("[ng-model='RptColsole.FromDate']").first().click(new Locator.ClickOptions().setTimeout(5000));
            page.waitForTimeout(700);
            // the picker keeps the month it was last left on (not the From box's), so it is read from its own header ("September 2026") before every arrow click
            for (int i = 0; i < 30; i++) {
                java.time.LocalDate shown = shownMonth();
                if (shown == null) { problems.add("the picker's month could not be read"); break; }
                int diff = (shown.getYear() - target.getYear()) * 12 + (shown.getMonthValue() - target.getMonthValue());
                if (diff == 0) break;
                page.locator("a[ng-click='" + (diff > 0 ? "prevMonth()" : "nextMonth()") + "']:visible").first().click(new Locator.ClickOptions().setTimeout(5000));
                page.waitForTimeout(400);
            }
            page.locator("a[ng-click='setDatepickerDay(item)']:visible").filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^" + target.getDayOfMonth() + "$")))
                    .first().click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("From Date: " + firstLine(e)); }
        waitForAngular(600);
        return fromDate();
    }

    /** The first day of the month the open date picker shows (from its header), null when it cannot be read. */
    private java.time.LocalDate shownMonth() {
        Object hdr = page.evaluate("() => { const e=document.querySelector('.modal.in ._720kb-datepicker-open ._720kb-datepicker-calendar-header') || document.querySelector('._720kb-datepicker-open ._720kb-datepicker-calendar-header'); return e? (e.textContent||'') : ''; }");
        java.util.regex.Matcher hm = java.util.regex.Pattern.compile("(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{4})").matcher(String.valueOf(hdr).replace(' ', ' '));   // the header uses a non-breaking space, which Java's \s does not match
        if (!hm.find()) return null;
        return java.time.LocalDate.of(Integer.parseInt(hm.group(2)), java.time.Month.valueOf(hm.group(1).toUpperCase()).getValue(), 1);
    }

    /** From Date: open the picker, go back {@code monthsBack} month(s) with the previous-month arrow and click day {@code day}. Returns the From box's value. */
    public String pickFromDate(int monthsBack, int day) {
        problems.clear();
        try {
            page.locator("[ng-model='RptColsole.FromDate']").first().click(new Locator.ClickOptions().setTimeout(5000));
            page.waitForTimeout(600);
            for (int i = 0; i < monthsBack; i++) {
                page.locator("a[ng-click='prevMonth()']:visible").first().click(new Locator.ClickOptions().setTimeout(5000));
                page.waitForTimeout(350);
            }
            page.locator("a[ng-click='setDatepickerDay(item)']:visible").filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^" + day + "$")))
                    .first().click(new Locator.ClickOptions().setTimeout(5000));
        } catch (Exception e) { problems.add("From Date: " + firstLine(e)); }
        waitForAngular(600);
        return fromDate();
    }

    // -- Ordering Doctor popup

    public List<String> popupOptions(String ngModel) { return options(ngModel); }

    public String selectLocation(String name) { return select("RptColsole.SelectedLocationID", "Location", name); }

    public String selectPayableType(String name) {
        String s = select("RptColsole.SelectedPayableType", "Payable Type", name);
        for (int i = 0; i < 24 && !s.isEmpty() && users().isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(800);
        return s;
    }

    /** The "Select User" rows of the Ordering Doctor popup (names, in order). */
    public List<String> users() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); if(!m) return []; return [...m.querySelectorAll(\"input[ng-model='DL.IsSelected']\")].map(c=>norm((c.closest('tr')||{}).textContent)); }");
        return strings(r);
    }

    /** Tick the {@code idx}-th user checkbox; returns its name ("" on failure). */
    public String tickUser(int idx) { return tickIn("DL.IsSelected", idx); }

    // -- Diagnosis popup

    /** Type {@code text} in the smart search box and click the search button; returns how many diagnoses are listed. */
    public int smartSearch(String text) {
        problems.clear();
        try {
            page.locator(".modal.in input[ng-model='searchText']").first().fill(text, new Locator.FillOptions().setTimeout(6000));
            page.locator(".modal.in button[ng-click='fnGetDiagnosis(searchText);']").first().click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("smart search: " + firstLine(e)); return 0; }
        for (int i = 0; i < 60 && diagnoses().isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        return diagnoses().size();
    }

    public List<String> diagnoses() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); if(!m) return []; return [...m.querySelectorAll(\"input[ng-model='G.IsSelected']\")].map(c=>norm((c.closest('tr')||{}).textContent)); }");
        return strings(r);
    }

    public String tickDiagnosis(int idx) { return tickIn("G.IsSelected", idx); }

    // -- "Select ..." popups made of a table of checkboxes ({@code G.IsSelected}): Department, SubGroup (after its Group), Diagnosis ...

    /** The rows (names) of the popup's checkbox table, in order. */
    public List<String> checkRows() { return diagnoses(); }

    /** Tick the {@code idx}-th row of the popup's checkbox table; returns its name ("" on failure). */
    public String tickRow(int idx) { return tickIn("G.IsSelected", idx); }

    /** SubGroup popup: choose the <b>Group</b> ({@code RptColsole.SelectedgrpID}, e.g. "Bed"); the sub groups of that group are then listed. Returns the group shown ("" on failure). */
    public String selectGroup(String name) {
        String s = select("RptColsole.SelectedgrpID", "Group", name);
        for (int i = 0; i < 40 && !s.isEmpty() && checkRows().isEmpty(); i++) page.waitForTimeout(250);
        waitForAngular(800);
        return s;
    }

    // -- OPD/IPD/All popup (radios {@code RptColsole.OPDIPDALL}, ids OPD / IPD / EXT / ALL)

    /** The radio buttons of the popup (their ids). */
    public List<String> radioOptions() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); return m? [...m.querySelectorAll(\"input[type=radio][ng-model='RptColsole.OPDIPDALL']\")].map(x=>x.id) : []; }");
        return strings(r);
    }

    /** Choose the radio button with this id; returns the id once it is checked and held by the screen's model ("" on failure). */
    public String chooseRadio(String id) {
        problems.clear();
        try { page.locator(".modal.in input[type=radio]#" + id).first().check(new Locator.CheckOptions().setTimeout(6000)); }
        catch (Exception e) {
            Object js = page.evaluate("(i) => { const e=document.querySelector(\".modal.in input[type=radio]#\"+i); if(!e) return false; e.click(); return true; }", id);
            if (!Boolean.TRUE.equals(js)) { problems.add("the option " + id + " could not be chosen: " + firstLine(e)); return ""; }
        }
        waitForAngular(500);
        Object held = page.evaluate("(i) => { const e=document.querySelector(\".modal.in input[type=radio]#\"+i); if(!e) return ''; let v=''; try{ v=String(angular.element(e).scope().RptColsole.OPDIPDALL); }catch(x){} return e.checked+'/'+v; }", id);
        String h = String.valueOf(held);
        if (!h.startsWith("true/")) { problems.add("the option " + id + " is not checked (" + h + ")"); return ""; }
        return id;
    }

    // -- From Time_To Time popup ({@code timepicker-pop}: a box + a toggle button that opens hour / minute boxes and an AM/PM button)

    /** The two time boxes as shown ("09:30 AM", ""). */
    public List<String> timeBoxes() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); return m? [...m.querySelectorAll(\"input[ng-model='inputTime']\")].map(i=>i.value) : []; }");
        return strings(r);
    }

    /** Fill one time box (0 = From Time, 1 = To Time): open its picker, type the hour and minutes, set AM/PM, close it. Returns the box's text afterwards. */
    public String enterTime(int box, int hour12, int minute, boolean pm) {
        problems.clear();
        try {
            Locator toggle = page.locator(".modal.in button[ng-click='toggle()']").nth(box);
            toggle.click(new Locator.ClickOptions().setTimeout(6000));
            page.waitForTimeout(800);
            Locator hh = page.locator(".modal.in input[ng-model='hours']:visible").first();
            Locator mm = page.locator(".modal.in input[ng-model='minutes']:visible").first();
            hh.fill(String.valueOf(hour12), new Locator.FillOptions().setTimeout(5000));
            mm.fill(String.format("%02d", minute), new Locator.FillOptions().setTimeout(5000));
            mm.press("Tab", new Locator.PressOptions().setTimeout(5000));
            page.waitForTimeout(500);
            Locator mer = page.locator(".modal.in button[ng-click='toggleMeridian()']:visible").first();
            String now = mer.textContent() == null ? "" : mer.textContent().trim();
            if (pm != now.toUpperCase().startsWith("PM")) { mer.click(new Locator.ClickOptions().setTimeout(5000)); page.waitForTimeout(500); }
            toggle.click(new Locator.ClickOptions().setTimeout(6000));   // close the picker
            page.waitForTimeout(600);
        } catch (Exception e) { problems.add("time box " + (box + 1) + ": " + firstLine(e)); }
        List<String> boxes = timeBoxes();
        return box < boxes.size() ? boxes.get(box) : "";
    }

    /** Tick the idx-th checkbox of the popup's table (a real click) and check it is ticked and the row's data holds it. Returns the row text, "" on failure. */
    private String tickIn(String ngModel, int idx) {
        problems.clear();
        Object name = page.evaluate("([m,i]) => {" + JS + " const dlg=modal(); const c=dlg? [...dlg.querySelectorAll(\"input[ng-model='\"+m+\"']\")][i] : null; if(!c) return ''; c.id='__rcTick'; c.scrollIntoView({block:'center'}); return norm((c.closest('tr')||{}).textContent); }", Arrays.asList(ngModel, idx));
        if (name == null || name.toString().isEmpty()) { problems.add("row " + (idx + 1) + " of the popup's table is not on screen"); return ""; }
        try { page.locator("#__rcTick").check(new Locator.CheckOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the checkbox could not be ticked: " + firstLine(e)); }
        waitForAngular(500);
        Object held = page.evaluate("(m) => { const e=document.getElementById('__rcTick'); if(!e) return ''; let v=''; try{ v=String(angular.element(e).scope()[m.split('.')[0]].IsSelected); }catch(x){} const r=e.checked+'/'+v; e.removeAttribute('id'); return r; }", ngModel);
        if (held == null || !"true/true".equals(held.toString())) problems.add("the tick is not held (checked/model " + held + ")");
        return problems.isEmpty() ? name.toString() : "";
    }

    // ---- Show Report -------------------------------------------------------------------------------------------------------------------------------

    /** What Show Report produced. */
    public static final class ReportResult {
        public String url = "";
        public boolean isPdf;
        public int bytes, pdfPages;
        /** The text of the first pages of the PDF (single spaces). */
        public String text = "";
        public byte[] png;
        public String diagnostics = "";
    }

    /** Number of tabs open right now (take it just before Show Report). */
    public int tabCount() { return page.context().pages().size(); }

    public boolean clickShowReport() {
        problems.clear();
        try { page.locator("button[ng-click='ShowReport($event)']").first().click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Show Report: " + firstLine(e)); return false; }
        return true;
    }

    /** Wait for the tab Show Report opened and read what it holds (the PDF's bytes are fetched from inside the tab; its text is read with PDFBox). */
    public ReportResult captureReport(int tabsBefore, int waitMs) {
        ReportResult r = new ReportResult();
        Page tab = null;
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            List<Page> pages = page.context().pages();
            if (pages.size() > tabsBefore) {
                Page t = pages.get(pages.size() - 1);
                try {
                    String u = t.url() == null ? "" : t.url();
                    if (!u.isEmpty() && !u.startsWith("about:")) {
                        tab = t; r.url = u;
                        try { t.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(10000)); } catch (Exception ignore) { }
                        break;
                    }
                } catch (Exception ignore) { }
            }
            page.waitForTimeout(500);
        }
        if (tab == null) { r.diagnostics = "no report tab opened at all"; return r; }
        try {
            Object o = tab.evaluate("async () => { const res = await fetch(location.href, {credentials:'include'}); const buf = await res.arrayBuffer(); const u8 = new Uint8Array(buf);"
                    + " let head=''; for(let i=0;i<Math.min(8,u8.length);i++) head += String.fromCharCode(u8[i]);"
                    + " let bin=''; const chunk=0x8000; for(let i=0;i<u8.length;i+=chunk) bin += String.fromCharCode.apply(null, u8.subarray(i,i+chunk));"
                    + " return {len:u8.length, head:head, status:res.status, ct:res.headers.get('content-type')||'', b64:btoa(bin)}; }");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> m = (java.util.Map<String, Object>) o;
            r.bytes = ((Number) m.get("len")).intValue();
            r.isPdf = String.valueOf(m.get("head")).startsWith("%PDF");
            if (r.isPdf) {
                byte[] pdf = java.util.Base64.getDecoder().decode(String.valueOf(m.get("b64")));
                try { r.png = PdfUtil.firstPageToPng(pdf); } catch (Exception e) { System.out.println("Investigation_Reports: could not render the PDF - " + firstLine(e)); }
                try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
                    r.pdfPages = doc.getNumberOfPages();
                    org.apache.pdfbox.text.PDFTextStripper st = new org.apache.pdfbox.text.PDFTextStripper();
                    st.setStartPage(1); st.setEndPage(Math.min(2, Math.max(1, r.pdfPages)));
                    r.text = st.getText(doc).replaceAll("\\s+", " ").trim();
                }
            }
            r.diagnostics = "the report opened at " + r.url + " - HTTP " + m.get("status") + ", " + m.get("ct") + ", " + r.bytes + " bytes, "
                    + (r.isPdf ? "a PDF of " + r.pdfPages + " page(s)" : "NOT a PDF (starts with \"" + String.valueOf(m.get("head")).trim() + "\")");
        } catch (Throwable e) {
            r.diagnostics = "a tab opened at " + r.url + " but its content could not be read: " + String.valueOf(e.getMessage()).split("\n")[0];
        }
        return r;
    }
}
