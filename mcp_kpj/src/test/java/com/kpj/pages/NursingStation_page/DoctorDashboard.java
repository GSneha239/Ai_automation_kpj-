package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Nursing Station &gt; <b>Doctor Dashboard</b> ({@code #/DoctorDashboard}) — Page Object.
 *
 * <p>The screen is a tabbed dashboard (<i>OP List, IP List, Bed Availability, Investigation List, Discharge List,
 * Medical Report, Overdue Medication, Quality Indicator</i>). The <b>OP List</b> tab holds a filter panel (From/To
 * date = {@code queue.fromdate} / {@code queue.todate}, calendar widgets), a <b>Search</b> button
 * ({@code onclick="fnQueueGrid(2)"}) and a ui-grid of visits ({@code gridOptions.data}: {@code MRNo, patientname,
 * OPDNO, visitid, consultant ...}) with a row-select button column. Under the grid: <b>New Case</b>
 * ({@code fnDashNewCase()}), <b>Forms</b> ({@code fnDashForms()}), <b>View Forms</b> ({@code fnDashViewForms()}).</p>
 *
 * <ul>
 *   <li><b>New Case</b> opens the <i>View Case</i> popup: MRN / Episode No / NRIC / Patient Name (read-only),
 *       <i>Department</i> ({@code queue.departmentidcase}, select2), <i>Case Against Doctor</i>
 *       ({@code queue.doctoridcase}, select2, reloaded when the Department changes), Case Diagnosis, <b>Save</b>
 *       ({@code SaveQueueNewCaseDetails()}) -&gt; toast "KPJ Portal EMR Case added successfully.".</li>
 *   <li><b>Forms</b> opens <i>Forms Details</i>: a type-ahead <i>Forms</i> box ({@code consent.consentname}, list
 *       {@code .auto-complete-container.txtQueueConsentSearch}, ~440 forms loaded from the server), and <b>Add</b>
 *       ({@code ShowSignTextbox()}). For a form mapped to an external form (nearly all of them) Add opens the form
 *       on {@code nhisformstest...} in a NEW TAB: <b>Submit</b> -&gt; "Are you sure you want to continue?"
 *       ({@code #btnConfirmYes}) -&gt; "Success - Form Submitted Successfully!" ({@code #messageModalOk}); the tab is then
 *       the read-only Edit page (only Print / Back).</li>
 *   <li><b>View Forms</b> opens <i>View Consent/Forms</i> (filter panel + "N Records" list) with a Close button.</li>
 * </ul>
 *
 * <p>Confirmed live on build v2026.11.05.421. Quirks handled here: the route's template loads slowly (the hash changes
 * before the screen exists); the grid <b>auto-refreshes</b> ("Refreshing in Ns") and drops the row selection, so the
 * patient is re-selected by visit id before Forms / View Forms; the grid's row-select button is sometimes covered by
 * the page layout (a real click is tried first, the grid's own selection API is the fallback and is reported).</p>
 */
public class DoctorDashboard extends BasePage {

    public DoctorDashboard(Page page) { super(page); }

    public static final String ROUTE = "#/DoctorDashboard";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December"};
    private static final int TOAST_SETTLE_MS = 2500;

    /** Screenshot taken while the New Case save toast was on screen. */
    public byte[] caseToastPng;
    /** Screenshot of the filled View Case pop up, taken just before the last Save. */
    public byte[] caseFilledPng;
    /** The toast text / kind ("success", "error" ...) seen after the last New Case Save. */
    public String caseToast = "";
    public String caseToastKind = "";
    /** True when the View Case popup was gone after Save. */
    public boolean caseClosed;

    /** The patient chosen in the grid (kept so it can be re-selected after the grid's auto-refresh). */
    public String selectedMrn = "", selectedName = "", selectedOpdNo = "", selectedVisitId = "";
    /** How the last selection was made ("clicked the row's select button" / "grid API"). */
    public String selectedHow = "";
    /** Date range last set. */
    public String lastFrom = "", lastTo = "";
    /** Result of the last Search: rows loaded and the "1 - 25 of 179 items" text. */
    public int lastRowCount = -1;
    public String lastTotalText = "";

    /** JS helpers shared by every snippet (no backslashes: normalisation is built from char codes). */
    private static final String JS =
            "const norm=s=>(s||'').replace(new RegExp('['+String.fromCharCode(9,10,13,160)+' ]+','g'),' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const A=window.angular;"
          + "const byNg=ng=>[...document.querySelectorAll('[ng-model]')].filter(e=>e.getAttribute('ng-model')===ng);"
          + "const byClick=(txt,scope)=>[...(scope||document).querySelectorAll('[ng-click]')].filter(e=>(e.getAttribute('ng-click')||'').indexOf(txt)>=0);"
          + "const modal=()=>[...document.querySelectorAll('.modal.in')].find(vis)||null;"
          + "const grid=()=>{ const g=[...document.querySelectorAll('[ui-grid]')].find(vis); return g?{g:g,s:A.element(g).scope()}:null; };";

    // ---- small helpers -----------------------------------------------------

    /** Tag the first matching visible element with a temporary id and click it with a real mouse click. */
    private boolean clickTagged(String findJs, String id) {
        Object tag = page.evaluate("() => {" + JS + findJs + " if(!el) return ''; el.id='" + id + "'; return 'ok'; }");
        if (tag == null || tag.toString().isEmpty()) return false;
        try { page.locator("#" + id).click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) {
            System.out.println("DoctorDashboard: click on " + id + " failed (" + firstLine(e) + ") - trying an in-page click");
            try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); return true; }
            catch (Exception e2) { return false; }
        } finally {
            try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.removeAttribute('id'); }", id); } catch (Exception ignore) { }
        }
    }

    private static String firstLine(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        int nl = m.indexOf('\n');
        return nl < 0 ? m : m.substring(0, nl);
    }

    private Page.WaitForFunctionOptions waitOpts(int ms) { return new Page.WaitForFunctionOptions().setTimeout(ms); }

    public void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
    }

    private static boolean same(String a, String b) { return a != null && b != null && a.trim().equalsIgnoreCase(b.trim()); }

    private List<String> toList(Object r) {
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    // ---- navigation --------------------------------------------------------

    /** On the real screen: the route AND the OP List tab + the From Date box (the hash changes long before the template arrives). */
    public boolean onScreen() {
        Object r = page.evaluate("() => {" + JS
                + " return location.hash.toLowerCase().indexOf('doctordashboard')>=0"
                + "   && byClick('OPList').some(vis) && byNg('queue.fromdate').some(vis); }");
        return Boolean.TRUE.equals(r);
    }

    /** Nursing Station -&gt; Doctor Dashboard through the sidebar, the leaf looked up by its exact href inside the Nursing Station entry. */
    public boolean navigateViaMenu() {
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            try {
                expandMenuEntry(null, "^ *nursing station *$");
                boolean clicked = clickTagged(
                        " const top=[...document.querySelectorAll('a')].find(x=>/^nursing station$/i.test(norm(x.textContent)));"
                      + " const li=top?top.closest('li'):null;"
                      + " const el=li?[...li.querySelectorAll('a[href]')].find(x=>x.getAttribute('href')==='" + ROUTE + "'):null;",
                        "__ddMenu");
                if (!clicked) { System.out.println("DoctorDashboard.nav: menu link not found (attempt " + (attempt + 1) + ")"); waitForAngular(1500); continue; }
                try {
                    page.waitForFunction("() => { const norm=s=>(s||'').trim(); return location.hash.toLowerCase().indexOf('doctordashboard')>=0"
                            + " && [...document.querySelectorAll('[ng-click]')].some(e=>(e.getAttribute('ng-click')||'').indexOf('OPList')>=0 && e.offsetParent!==null); }",
                            null, waitOpts(40000));
                } catch (Exception ignore) { System.out.println("DoctorDashboard.nav: the screen did not show (attempt " + (attempt + 1) + ")"); }
                waitForAngular(1500);
            } catch (Exception e) {
                System.out.println("DoctorDashboard.nav: raced the route change (attempt " + (attempt + 1) + ") - " + firstLine(e));
                waitForAngular(2000);
            }
        }
        return onScreen();
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => {" + JS
                + " const h=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (h||'(no header)').slice(0,40); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** Scroll the page back to the top, so a screenshot shows the filter panel and the first rows of the table. */
    public void scrollTop() {
        try { page.evaluate("() => { window.scrollTo(0,0); const s=document.scrollingElement; if(s) s.scrollTop=0; }"); } catch (Exception ignore) { }
        waitForAngular(400);
    }

    /** The names of the dashboard's tabs, in order. */
    public String tabNames() {
        Object r = page.evaluate("() => {" + JS
                + " return byClick('fnSetTab').filter(vis).map(e=>norm(e.textContent)).join(', '); }");
        return r == null ? "" : r.toString();
    }

    /** Click the <b>OP List</b> tab (the "OP" tab) and wait for its filter panel. */
    public boolean clickOpTab() {
        boolean clicked = clickTagged(" const el=byClick('OPList').find(vis);", "__ddOpTab");
        waitForAngular(1500);
        try {
            page.waitForFunction("() => [...document.querySelectorAll('[ng-model]')].some(e=>e.getAttribute('ng-model')==='queue.fromdate' && e.offsetParent!==null)",
                    null, waitOpts(10000));
        } catch (Exception e) { return false; }
        return clicked;
    }

    // ---- date range --------------------------------------------------------

    public String readDate(String ngModel) {
        Object r = page.evaluate("(n) => {" + JS + " const e=byNg(n).find(vis)||byNg(n)[0]; return e?e.value:''; }", ngModel);
        return r == null ? "" : r.toString().trim();
    }

    /**
     * Pick {@code target} in the calendar widget of the date box {@code ngModel}: open it, move to the month with the
     * arrows, click the day. Returns "" when the box then holds the date, otherwise a sentence saying what went wrong.
     */
    private String pickDate(String ngModel, LocalDate target) {
        if (!clickTagged(" const el=byNg('" + ngModel + "').find(vis);", "__ddDate")) return "the date box was not found";
        try {
            page.waitForFunction("() => [...document.querySelectorAll('._720kb-datepicker-calendar._720kb-datepicker-open')].some(c=>c.offsetWidth||c.offsetHeight)",
                    null, waitOpts(6000));
        } catch (Exception e) { return "the calendar did not open"; }
        waitForAngular(400);
        Object cur = page.evaluate("() => {" + JS
                + " const c=[...document.querySelectorAll('._720kb-datepicker-calendar._720kb-datepicker-open')].find(vis); if(!c) return '';"
                + " const m=norm(c.textContent).match(/(January|February|March|April|May|June|July|August|September|October|November|December) *([0-9]{4})/);"
                + " return m ? m[1]+' '+m[2] : ''; }");
        String shown = cur == null ? "" : cur.toString();
        if (shown.isEmpty()) return "the calendar's month could not be read";
        String[] mp = shown.split(" ");
        int month = 0;
        for (int i = 0; i < MONTHS.length; i++) if (MONTHS[i].equals(mp[0])) month = i + 1;
        int year = Integer.parseInt(mp[1]);
        int delta = (target.getYear() * 12 + target.getMonthValue()) - (year * 12 + month);
        String arrow = delta < 0 ? "prevMonth()" : "nextMonth()";
        for (int i = 0; i < Math.abs(delta); i++) {
            boolean ok = clickTagged(" const c=[...document.querySelectorAll('._720kb-datepicker-calendar._720kb-datepicker-open')].find(vis);"
                    + " const el=c?[...c.querySelectorAll('a')].find(a=>(a.getAttribute('ng-click')||'')==='" + arrow + "'):null;", "__ddArrow");
            if (!ok) return "the calendar's month arrow was not found";
            waitForAngular(350);
        }
        boolean day = clickTagged(" const c=[...document.querySelectorAll('._720kb-datepicker-calendar._720kb-datepicker-open')].find(vis);"
                + " const el=c?[...c.querySelectorAll('._720kb-datepicker-calendar-body a')].find(a=>norm(a.textContent)==='" + target.getDayOfMonth()
                + "' && !/disabled/.test(a.className)):null;", "__ddDay");
        if (!day) return "day " + target.getDayOfMonth() + " was not in the calendar";
        waitForAngular(600);
        String now = readDate(ngModel);
        String want = DMY.format(target);
        return now.equals(want) ? "" : "the box shows " + now + " instead of " + want;
    }

    /** Select the From Date (one month before the To Date) and the To Date (today) in the calendars. */
    public String setOneMonthRange() {
        LocalDate to;
        try { to = LocalDate.parse(readDate("queue.todate"), DMY); } catch (Exception e) { to = LocalDate.now(); }
        LocalDate from = to.minusMonths(1);
        String e1 = pickDate("queue.fromdate", from);
        if (!e1.isEmpty()) return "(From Date: " + e1 + ")";
        String e2 = pickDate("queue.todate", to);
        if (!e2.isEmpty()) return "(To Date: " + e2 + ")";
        lastFrom = readDate("queue.fromdate");
        lastTo = readDate("queue.todate");
        return "From Date " + lastFrom + " and To Date " + lastTo + " (1 month)";
    }

    // ---- search + grid -----------------------------------------------------

    private int gridCount() {
        Object r = page.evaluate("() => {" + JS + " const o=grid(); return o && o.s.gridOptions && Array.isArray(o.s.gridOptions.data) ? o.s.gridOptions.data.length : -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Click <b>Search</b> and wait for the grid to be refilled. Returns the number of rows loaded (-1 when the button was not found). */
    public int clickSearch() {
        page.evaluate("() => {" + JS + " const o=grid(); window.__ddOldData = o && o.s.gridOptions ? o.s.gridOptions.data : null; }");
        boolean clicked = clickTagged(" const el=[...document.querySelectorAll('button')].find(b=>vis(b) && (b.getAttribute('onclick')||'').indexOf('fnQueueGrid')>=0"
                + " && norm(b.textContent).toLowerCase()==='search');", "__ddSearch");
        if (!clicked) return -1;
        try {
            page.waitForFunction("() => {" + JS + " const o=grid(); return !!o && o.s.gridOptions.data !== window.__ddOldData; }", null, waitOpts(30000));
        } catch (Exception ignore) { System.out.println("DoctorDashboard: the grid data was not replaced after Search - reading what is there"); }
        int last = -2, stable = 0;
        for (int i = 0; i < 12; i++) {
            waitForAngular(600);
            int n = gridCount();
            if (n == last) { if (++stable >= 2) break; } else stable = 0;
            last = n;
        }
        lastRowCount = gridCount();
        Object t = page.evaluate("() => {" + JS + " const m=norm(document.body.innerText).match(/[0-9]+ - [0-9]+ of [0-9]+ items/); return m?m[0]:''; }");
        lastTotalText = t == null ? "" : t.toString();
        return lastRowCount;
    }

    /** The grid's rows (the loaded page): {MRN, Patient Name, OPD No, Visit id, Consultant}. */
    public List<String[]> gridRows() {
        Object r = page.evaluate("() => {" + JS + " const o=grid(); if(!o||!o.s.gridOptions) return [];"
                + " return (o.s.gridOptions.data||[]).map(x=>[String(x.MRNo||''), norm(x.patientname), String(x.OPDNO||''), String(x.visitid||''), norm(x.consultant)]); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) {
            for (Object o : (List<?>) r) {
                if (o instanceof List) {
                    List<?> l = (List<?>) o;
                    out.add(new String[]{String.valueOf(l.get(0)), String.valueOf(l.get(1)), String.valueOf(l.get(2)), String.valueOf(l.get(3)), String.valueOf(l.get(4))});
                }
            }
        }
        return out;
    }

    private boolean isSelected(String visitId) {
        Object r = page.evaluate("(v) => {" + JS + " const o=grid(); if(!o||!o.s.gridApi) return false;"
                + " return o.s.gridApi.selection.getSelectedRows().some(r=>String(r.visitid)===v); }", visitId);
        return Boolean.TRUE.equals(r);
    }

    private boolean inGrid(String visitId) {
        Object r = page.evaluate("(v) => {" + JS + " const o=grid(); if(!o||!o.s.gridOptions) return false;"
                + " return (o.s.gridOptions.data||[]).some(r=>String(r.visitid)===v); }", visitId);
        return Boolean.TRUE.equals(r);
    }

    /** Select the grid row of {@code visitId}: a real click on its select button, else the grid's own selection API. Returns how, or "" on failure. */
    private String selectByVisit(String visitId) {
        if (isSelected(visitId)) return "already selected";
        boolean tagged = Boolean.TRUE.equals(page.evaluate("(v) => {" + JS
                + " const o=grid(); if(!o) return false; const rows=[...o.g.querySelectorAll('.ui-grid-pinned-container .ui-grid-row')];"
                + " const r=rows.find(x=>{ const sc=A.element(x).scope(); return sc && sc.row && sc.row.entity && String(sc.row.entity.visitid)===v; });"
                + " const b=r?r.querySelector('.ui-grid-selection-row-header-buttons'):null; if(!b) return false; b.id='__ddSel'; return true; }", visitId));
        String how = "";
        if (tagged) {
            try {
                page.locator("#__ddSel").click(new Locator.ClickOptions().setTimeout(4000));
                how = "clicked the row's select button";
            } catch (Exception e) {
                System.out.println("DoctorDashboard: the row's select button could not be clicked (" + firstLine(e) + ")");
            } finally {
                try { page.evaluate("() => { const e=document.getElementById('__ddSel'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            }
            for (int i = 0; i < 4 && !isSelected(visitId); i++) waitForAngular(250);
        }
        if (!isSelected(visitId)) {
            page.evaluate("(v) => {" + JS + " const o=grid(); if(!o) return; const e=(o.s.gridOptions.data||[]).find(r=>String(r.visitid)===v); if(!e) return;"
                    + " o.s.gridApi.selection.clearSelectedRows(); o.s.gridApi.selection.selectRow(e); o.s.$applyAsync(); }", visitId);
            for (int i = 0; i < 4 && !isSelected(visitId); i++) waitForAngular(250);
            how = "selected through the grid's own selection API (the row's select button could not be clicked)";
        }
        return isSelected(visitId) ? how : "";
    }

    /**
     * Select a patient from the table: the row of {@code wantMrn}, or the first row when it is null/empty. Returns what was
     * selected; a value starting with "(" means it failed.
     */
    public String selectPatient(String wantMrn) {
        Object info = page.evaluate("(w) => {" + JS
                + " const o=grid(); if(!o||!o.s.gridOptions) return ''; const d=o.s.gridOptions.data||[];"
                + " const e=w ? d.find(r=>String(r.MRNo)===w) : d[0]; if(!e) return '';"
                + " return [e.MRNo, norm(e.patientname), e.OPDNO||'', e.visitid].join('|'); }", wantMrn == null ? "" : wantMrn);
        String s = info == null ? "" : info.toString();
        if (s.isEmpty()) return wantMrn == null || wantMrn.isEmpty() ? "(the table has no rows)" : "(MRN " + wantMrn + " is not in the table)";
        String[] f = s.split(Pattern.quote("|"), -1);
        selectedMrn = f[0]; selectedName = f[1]; selectedOpdNo = f[2]; selectedVisitId = f[3];
        String how = selectByVisit(selectedVisitId);
        if (how.isEmpty()) return "(the row of " + selectedName + " (MRN " + selectedMrn + ") could not be selected)";
        selectedHow = how;
        return describeSelection() + " - " + how;
    }

    public String describeSelection() {
        return "MRN " + selectedMrn + " | " + selectedName + " | " + selectedOpdNo + " (visit " + selectedVisitId + ")";
    }

    /**
     * Make sure the chosen patient is still selected — the grid auto-refreshes and drops the selection. Returns "" when nothing
     * had to be done, a sentence when it was re-selected, or a value starting with "(" when that failed.
     */
    public String ensureSelected() {
        if (selectedVisitId.isEmpty()) return "(no patient has been selected yet)";
        if (isSelected(selectedVisitId)) return "";
        String note = "the grid refreshed and dropped the selection; ";
        if (!inGrid(selectedVisitId)) {
            int n = clickSearch();
            note += "searched again (" + n + " rows); ";
            if (!inGrid(selectedVisitId)) return "(the visit " + selectedOpdNo + " is no longer in the table after the refresh)";
        }
        String how = selectByVisit(selectedVisitId);
        if (how.isEmpty()) return "(the patient could not be selected again)";
        return note + describeSelection() + " re-selected";
    }

    // ---- select2 / small popup helpers ------------------------------------

    /** What a select2 box shows now ("" when nothing is chosen). */
    public String s2Chosen(String ngModel) {
        Object r = page.evaluate("(n) => {" + JS + " const e=byNg(n).find(x=>x.closest('.modal.in'))||byNg(n)[0]; if(!e) return '';"
                + " const c=e.previousElementSibling; const ch=c && c.querySelector('.select2-chosen'); return ch ? norm(ch.textContent) : ''; }", ngModel);
        String s = r == null ? "" : r.toString();
        return s.matches("(?i)-*\\s*select\\s*-*") ? "" : s;
    }

    /** Every entry the select offers, in list order ("-Select-" left out). */
    public List<String> s2Options(String ngModel) {
        Object r = page.evaluate("(n) => {" + JS + " const e=byNg(n).find(x=>x.closest('.modal.in'))||byNg(n)[0]; if(!e) return [];"
                + " return [...e.options].map(o=>norm(o.textContent)).filter(t=>t && !/^-*select-*$/i.test(t.split(' ').join(''))); }", ngModel);
        return toList(r);
    }

    private String s2Model(String ngModel) {
        Object v = page.evaluate("(n) => {" + JS + " const e=byNg(n).find(x=>x.closest('.modal.in'))||byNg(n)[0]; if(!e) return '';"
                + " try{ const v=A.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ngModel);
        return v == null ? "" : v.toString().trim();
    }

    /** Open the select2 box of {@code ngModel} (inside the open popup) and click {@code name} in its VISIBLE list. "" = done, else what went wrong. */
    private String s2Pick(String ngModel, String name) {
        Object tagged = page.evaluate("(n) => {" + JS + " const e=byNg(n).find(x=>x.closest('.modal.in'))||byNg(n)[0]; if(!e) return false;"
                + " const c=e.previousElementSibling; const b=c && (c.querySelector('.select2-choice')||c); if(!b) return false; b.id='__ddS2Box'; return true; }", ngModel);
        if (!Boolean.TRUE.equals(tagged)) return "the box for " + ngModel + " is not on screen";
        try {
            page.locator("#__ddS2Box").click(new Locator.ClickOptions().setTimeout(6000));
            page.waitForFunction("() => [...document.querySelectorAll('.select2-drop-active li.select2-result-selectable')].filter(l=>l.offsetWidth||l.offsetHeight).length>0",
                    null, waitOpts(6000));
        } catch (Exception e) { return "the list of " + ngModel + " did not open"; }
        finally { page.evaluate("() => { const e=document.getElementById('__ddS2Box'); if(e) e.removeAttribute('id'); }"); }
        Object found = page.evaluate("(w) => {" + JS
                + " const li=[...document.querySelectorAll('.select2-drop-active li.select2-result-selectable')].filter(vis).find(x=>norm(x.textContent)===w);"
                + " if(!li) return false; li.id='__ddS2Opt'; return true; }", name);
        if (!Boolean.TRUE.equals(found)) {
            try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
            return "\"" + name + "\" is not in the dropdown list";
        }
        try { page.locator("#__ddS2Opt").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { return "\"" + name + "\" could not be clicked in the list"; }
        finally { page.evaluate("() => { const e=document.getElementById('__ddS2Opt'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 20 && !name.equals(s2Chosen(ngModel)); i++) page.waitForTimeout(150);
        String shown = s2Chosen(ngModel);
        if (!name.equals(shown)) return "the box shows \"" + shown + "\" instead of \"" + name + "\"";
        String held = s2Model(ngModel);
        if (held.isEmpty() || held.equals("null")) return "\"" + name + "\" is shown but the screen's model does not hold it";
        return "";
    }

    /** Wait until the entries of a select stop changing (the doctor list reloads when the Department changes). */
    private List<String> settledOptions(String ngModel) {
        List<String> last = new ArrayList<>();
        int stable = 0;
        for (int i = 0; i < 14; i++) {
            waitForAngular(600);
            List<String> now = s2Options(ngModel);
            if (now.equals(last)) { if (++stable >= 2) return now; } else stable = 0;
            last = now;
        }
        return last;
    }

    // ---- New Case ----------------------------------------------------------

    /** Click <b>New Case</b> and wait for the <i>View Case</i> popup. */
    public boolean clickNewCase() {
        clearToasts();
        if (!clickTagged(" const el=byClick('fnDashNewCase').find(vis);", "__ddNew")) return false;
        try {
            page.waitForFunction("() => {" + JS + " const m=modal(); return !!m && [...m.querySelectorAll('[ng-model]')].some(e=>e.getAttribute('ng-model')==='queue.departmentidcase'); }",
                    null, waitOpts(20000));
        } catch (Exception e) { return false; }
        waitForAngular(1500);
        return true;
    }

    /** What the View Case popup shows: MRN, Episode No, NRIC, Patient Name, Department, Case Against Doctor. */
    public String caseFormSummary() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); if(!m) return '';"
                + " const v=n=>{ const e=[...m.querySelectorAll('[ng-model]')].find(x=>x.getAttribute('ng-model')===n); return e?norm(e.value):''; };"
                + " return 'MRN '+v('queue.mrno')+' | Episode '+v('queue.EPisodeNo')+' | NRIC '+v('queue.NRIC')+' | '+v('queue.PatientName'); }");
        return (r == null ? "" : r.toString()) + " | Department " + s2Chosen("queue.departmentidcase") + " | Doctor " + s2Chosen("queue.doctoridcase");
    }

    public String caseFormMrn() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); if(!m) return '';"
                + " const e=[...m.querySelectorAll('[ng-model]')].find(x=>x.getAttribute('ng-model')==='queue.mrno'); return e?norm(e.value):''; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Change the <i>Department</i> and then the <i>Case Against Doctor</i> in the View Case popup. The first department
     * different from the current one is tried; the doctor list reloads for it and the first doctor different from the current
     * one is picked. Returns "Department A -> B; Doctor C -> D", or a value starting with "(" on failure.
     */
    public String changeDepartmentAndDoctor() {
        String d0 = s2Chosen("queue.departmentidcase"), c0 = s2Chosen("queue.doctoridcase");
        String lastErr = "no other department is offered";
        // The app allows ONE case per visit for each Department + Doctor pair ("Case alredy generated with same department and
        // doctor!"), and every run saves one — so the pair is picked at random, never from a fixed list, and not repeated within a run.
        List<String> depts = new ArrayList<>();
        for (String x : s2Options("queue.departmentidcase")) if (!same(x, d0)) depts.add(x);
        java.util.Collections.shuffle(depts, random);
        int tries = 0;
        for (String cand : depts) {
            if (++tries > 5) break;
            String e = s2Pick("queue.departmentidcase", cand);
            if (!e.isEmpty()) { lastErr = "Department " + cand + ": " + e; continue; }
            List<String> docs = new ArrayList<>();
            for (String dname : settledOptions("queue.doctoridcase")) if (!same(dname, c0) && !triedCases.contains(cand + "|" + dname)) docs.add(dname);
            if (docs.isEmpty()) { lastErr = "no other doctor is offered for " + cand; continue; }
            String doc = docs.get(random.nextInt(docs.size()));
            String e2 = s2Pick("queue.doctoridcase", doc);
            if (!e2.isEmpty()) { lastErr = "Doctor " + doc + ": " + e2; continue; }
            triedCases.add(cand + "|" + doc);
            return "Department " + (d0.isEmpty() ? "(none)" : d0) + " -> " + cand + "; Case Against Doctor " + (c0.isEmpty() ? "(none)" : c0) + " -> " + doc;
        }
        return "(" + lastErr + ")";
    }

    /** Department + Doctor pairs already tried in this run. */
    private final java.util.Set<String> triedCases = new java.util.HashSet<>();

    /**
     * Click <b>Save</b> in the View Case popup and return the toast. The toast is kept on screen and photographed
     * ({@link #caseToastPng}); {@link #caseClosed} says whether the popup closed.
     */
    public String saveCaseAndGetToast() {
        caseToast = ""; caseToastKind = ""; caseToastPng = null; caseClosed = false;
        // the filled View Case pop up, just BEFORE Save (a successful save closes it)
        try { caseFilledPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("DoctorDashboard: filled-popup screenshot failed - " + firstLine(e)); }
        page.evaluate("() => { window.__ddToasts=[]; if(window.__ddObs) window.__ddObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('#toast-container .toast').forEach(el=>{ const ti=el.querySelector('.toast-title'); const me=el.querySelector('.toast-message');"
                + "   const kind=((el.className.match(/toast-(success|error|warning|info)/)||[])[1])||''; const t=((ti?ti.textContent:'')+' '+(me?me.textContent:el.textContent)).split(' ').filter(x=>x).join(' ');"
                + "   const s=kind+'|'+t; if(t && window.__ddToasts.indexOf(s)<0) window.__ddToasts.push(s); }); };"
                + " window.__ddObs=new MutationObserver(grab); window.__ddObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        boolean clicked = clickTagged(" const m=modal(); const el=m?byClick('SaveQueueNewCaseDetails',m).find(vis):null;", "__ddCaseSave");
        if (!clicked) return "";
        try {
            page.waitForFunction("() => (window.__ddToasts||[]).length>0", null, waitOpts(25000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }
        waitForAngular(TOAST_SETTLE_MS);
        try { caseToastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("DoctorDashboard: toast screenshot failed - " + firstLine(e)); }
        Object t = page.evaluate("() => { const a=window.__ddToasts||[]; return a.find(x=>/success/.test(x.split('|')[0])) || a.find(x=>x) || ''; }");
        String raw = t == null ? "" : t.toString();
        int bar = raw.indexOf('|');
        caseToastKind = bar < 0 ? "" : raw.substring(0, bar);
        caseToast = bar < 0 ? raw : raw.substring(bar + 1);
        for (int i = 0; i < 10; i++) {
            Object open = page.evaluate("() => !!([...document.querySelectorAll('.modal.in')].find(m=>m.offsetWidth||m.offsetHeight))");
            if (!Boolean.TRUE.equals(open)) { caseClosed = true; break; }
            waitForAngular(400);
        }
        clearToasts();
        return caseToast;
    }

    /** Close whatever popup is open with its Close button (used to recover). */
    public boolean closeOpenPopup() {
        clickTagged(" const m=modal(); const el=m?[...m.querySelectorAll('button')].filter(vis).find(x=>norm(x.textContent).toLowerCase()==='close'):null;", "__ddClose");
        for (int i = 0; i < 10; i++) {
            waitForAngular(300);
            Object open = page.evaluate("() => !!([...document.querySelectorAll('.modal.in')].find(m=>m.offsetWidth||m.offsetHeight))");
            if (!Boolean.TRUE.equals(open)) return true;
        }
        return false;
    }

    // ---- Forms -------------------------------------------------------------

    /** Click <b>Forms</b> and wait for the <i>Forms Details</i> popup (its Forms box). */
    public boolean clickForms() {
        clearToasts();
        if (!clickTagged(" const el=byClick('fnDashForms').find(vis);", "__ddForms")) return false;
        try {
            page.waitForFunction("() => {" + JS + " const m=modal(); return !!m && [...m.querySelectorAll('input')].some(e=>e.getAttribute('ng-model')==='consent.consentname' && vis(e)); }",
                    null, waitOpts(20000));
        } catch (Exception e) { return false; }
        waitForAngular(1200);
        return true;
    }

    /**
     * Pick {@code name} from the Forms drop down: focus the box, put the form's name in it in ONE input event (the list is a
     * server call of ~3 s, typing key by key lets answers arrive out of order), wait for the list and click the entry.
     * Returns the chosen form with its id, or a value starting with "(" on failure.
     */
    public String pickForm(String name) {
        if (!clickTagged(" const m=modal(); const el=m?[...m.querySelectorAll('input')].find(e=>e.getAttribute('ng-model')==='consent.consentname' && vis(e)):null;", "__ddForm"))
            return "(the Forms box was not found in the popup)";
        waitForAngular(1200);                       // the box's focus handler writes a blank into it first
        boolean listed = false;
        for (int attempt = 1; attempt <= 3 && !listed; attempt++) {
            page.evaluate("(n) => {" + JS + " const m=modal(); const i=m?[...m.querySelectorAll('input')].find(e=>e.getAttribute('ng-model')==='consent.consentname'):null; if(!i) return;"
                    + " i.focus(); const set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set; set.call(i,n);"
                    + " i.dispatchEvent(new Event('input',{bubbles:true})); i.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'r'})); }", name);
            try {
                page.waitForFunction("(n) => {" + JS + " const c=document.querySelector('.auto-complete-container.txtQueueConsentSearch'); if(!c || c.classList.contains('ng-hide')) return false;"
                        + " return [...c.querySelectorAll('li')].some(l=>norm(l.innerText).toLowerCase()===n.toLowerCase()); }", name, waitOpts(attempt == 1 ? 25000 : 15000));
                listed = true;
            } catch (Exception e) {
                System.out.println("DoctorDashboard: the Forms list did not show \"" + name + "\" (attempt " + attempt + ")");
            }
        }
        if (!listed) {
            Object seen = page.evaluate("() => {" + JS + " const c=document.querySelector('.auto-complete-container.txtQueueConsentSearch'); if(!c) return '(no list)';"
                    + " return c.classList.contains('ng-hide') ? '(list hidden)' : [...c.querySelectorAll('li')].slice(0,6).map(l=>norm(l.innerText)).join(' | '); }");
            return "(\"" + name + "\" did not appear in the Forms drop down - it showed: " + seen + ")";
        }
        boolean clicked = clickTagged(" const c=document.querySelector('.auto-complete-container.txtQueueConsentSearch');"
                + " const el=c?[...c.querySelectorAll('li')].find(l=>norm(l.innerText).toLowerCase()==='" + name.toLowerCase().replace("'", "") + "'):null;", "__ddFormLi");
        if (!clicked) return "(the entry \"" + name + "\" could not be clicked)";
        waitForAngular(2500);
        Object r = page.evaluate("() => {" + JS + " const m=modal(); const i=m?[...m.querySelectorAll('input')].find(e=>e.getAttribute('ng-model')==='consent.consentname'):null; if(!i) return '';"
                + " const sc=A.element(i).scope(); const c=sc.consent||{}; return (c.consentid||'')+'|'+(c.consentname||'')+'|'+(sc.externalformmappingid==null?'':sc.externalformmappingid); }");
        String s = r == null ? "" : r.toString();
        String[] f = s.split(Pattern.quote("|"), -1);
        if (f.length < 3 || f[0].isEmpty()) return "(clicking the entry did not select the form - the box holds \"" + s + "\")";
        return f[1] + " (form id " + f[0] + (f[2].isEmpty() ? "" : ", external form " + f[2]) + ")";
    }

    /** Click <b>Add</b> in the Forms Details popup; the form opens in a NEW TAB. Returns that tab (null when none opened). */
    public Page clickAddOpenFormTab() {
        Page np;
        try {
            np = page.context().waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(90000), () ->
                    clickTagged(" const m=modal(); const el=m?byClick('ShowSignTextbox',m).find(vis):null;", "__ddAdd"));
        } catch (Exception e) {
            System.out.println("DoctorDashboard: no new tab opened after Add - " + firstLine(e));
            return null;
        }
        try { np.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(60000)); } catch (Exception ignore) { }
        try {
            np.waitForSelector("input[type='submit'][value='Submit']", new Page.WaitForSelectorOptions().setTimeout(45000));
        } catch (Exception ignore) { System.out.println("DoctorDashboard: the form tab shows no Submit button yet (" + np.url() + ")"); }
        return np;
    }

    /** The form tab's facts: {MRN printed on the form, first line of the patient banner, "true" when the form's name is on the page}. */
    public String[] formTabFacts(Page np, String formName) {
        try {
            Object r = np.evaluate("(n) => { const norm=s=>(s||'').split(String.fromCharCode(10)).join(' ').split(String.fromCharCode(9)).join(' ').split(' ').filter(x=>x).join(' ');"
                    + " const t=document.body?document.body.innerText:''; const m=t.match(/MRN[^0-9A-Za-z]{0,4}([0-9][0-9A-Za-z]*)/);"
                    + " const first=norm(t.split(String.fromCharCode(10))[0]||'');"
                    + " return [m?m[1]:'', first, norm(t).toLowerCase().indexOf(n.toLowerCase())>=0 ? 'true' : 'false'].join('~~'); }", formName);
            String[] f = (r == null ? "" : r.toString()).split("~~", -1);
            if (f.length == 3) return f;
        } catch (Exception e) { System.out.println("DoctorDashboard: could not read the form tab - " + firstLine(e)); }
        return new String[]{"", "", "false"};
    }

    public byte[] shot(Page p) {
        try { p.bringToFront(); } catch (Exception ignore) { }
        try { return p.screenshot(new Page.ScreenshotOptions().setTimeout(10000)); }
        catch (Exception e) { System.out.println("DoctorDashboard: screenshot failed - " + firstLine(e)); return null; }
    }

    /** True when the form tab offers a visible <b>Submit</b> button. */
    public boolean formSubmitOffered(Page np) {
        try { return np.locator("input[type='submit'][value='Submit']").first().isVisible(); } catch (Exception e) { return false; }
    }

    /** The form tab's visible buttons, for reporting. */
    public String formTabButtons(Page np) {
        try {
            Object r = np.evaluate("() => [...document.querySelectorAll('input[type=submit],input[type=button],button,a.btn')].filter(e=>e.offsetParent!==null)"
                    + ".map(e=>((e.value||e.textContent)||'').split(' ').filter(x=>x).join(' ').trim()).filter(x=>x && x!=='...').join(', ')");
            return r == null ? "" : r.toString();
        } catch (Exception e) { return ""; }
    }

    /** Click <b>Submit</b> on the form tab; returns the confirmation pop up's text, or a value starting with "(" when it did not appear. */
    public String clickFormSubmit(Page np) {
        try {
            Locator b = np.locator("input[type='submit'][value='Submit']").first();
            b.scrollIntoViewIfNeeded();
            b.click(new Locator.ClickOptions().setTimeout(10000));
        } catch (Exception e) { return "(Submit could not be clicked: " + firstLine(e) + ")"; }
        try {
            np.waitForSelector("#btnConfirmYes", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        } catch (Exception e) { return "(no confirmation pop up appeared after Submit)"; }
        np.waitForTimeout(700);
        return popupText(np, "btnConfirmYes");
    }

    private String popupText(Page np, String buttonId) {
        try {
            Object r = np.evaluate("(id) => { const b=document.getElementById(id); const m=b?b.closest('.modal'):null; if(!m) return '';"
                    + " return (m.innerText||'').split(String.fromCharCode(10)).join(' ').split(String.fromCharCode(9)).join(' ').split(' ').filter(x=>x).join(' '); }", buttonId);
            return r == null ? "" : r.toString();
        } catch (Exception e) { return ""; }
    }

    /** Click <b>Yes</b> in the confirmation; returns the success pop up's text (the form is saved and the page moves to its Edit view), or "(...)" if none appeared. */
    public String clickConfirmYes(Page np) {
        try { np.locator("#btnConfirmYes").click(new Locator.ClickOptions().setTimeout(10000)); }
        catch (Exception e) { return "(Yes could not be clicked: " + firstLine(e) + ")"; }
        try {
            np.waitForSelector("#messageModalOk", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(60000));
        } catch (Exception e) { return "(no message pop up appeared after Yes)"; }
        np.waitForTimeout(800);
        return popupText(np, "messageModalOk");
    }

    /** Click <b>OK</b> in the success pop up; returns the tab's URL afterwards ("" when the pop up stayed). */
    public String clickMessageOk(Page np) {
        try { np.locator("#messageModalOk").click(new Locator.ClickOptions().setTimeout(10000)); }
        catch (Exception e) { return ""; }
        try {
            np.waitForSelector("#messageModalOk", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(15000));
        } catch (Exception e) { return ""; }
        np.waitForTimeout(1200);
        return np.url();
    }

    /** Leave the Forms Details popup (Close) once the form work is done. */
    public boolean closeFormsPopup() { return closeOpenPopup(); }

    // ---- View Forms --------------------------------------------------------

    /** Click <b>View Forms</b> and wait for the <i>View Consent/Forms</i> popup and its list. Returns false when the popup did not open. */
    public boolean clickViewForms() {
        clearToasts();
        if (!clickTagged(" const el=byClick('fnDashViewForms').find(vis);", "__ddViewForms")) return false;
        try {
            page.waitForFunction("() => { const m=[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight); return !!m && (m.innerText||'').toLowerCase().indexOf('view consent')>=0; }",
                    null, waitOpts(20000));
        } catch (Exception e) { return false; }
        try {
            page.waitForFunction("() => { const m=[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight); return !!m && /[0-9]+ Record/.test(m.innerText||''); }",
                    null, waitOpts(20000));
        } catch (Exception ignore) { System.out.println("DoctorDashboard: the View Consent/Forms list did not report a record count"); }
        waitForAngular(1000);
        return true;
    }

    /** What the View Consent/Forms popup shows: {title, "N Records" text, rows joined by " ## "}. */
    public String[] viewFormsFacts() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); if(!m) return '';"
                + " const t=norm(m.innerText); const rec=t.match(/[0-9]+ Records?/);"
                + " const title=t.toLowerCase().indexOf('view consent/forms')>=0 ? 'View Consent/Forms' : t.slice(0,30);"
                + " const rows=[...m.querySelectorAll('table tbody tr')].filter(vis).map(x=>norm(x.innerText)).filter(x=>x);"
                + " return [title, rec?rec[0]:'', rows.join(' ## ')].join('~~'); }");
        String[] f = (r == null ? "" : r.toString()).split("~~", -1);
        return f.length == 3 ? f : new String[]{"", "", ""};
    }

    /** Close the View Consent/Forms popup. */
    public boolean closeViewForms() { return closeOpenPopup(); }

    // ---- Referred Patients -------------------------------------------------

    /** The Referred Patients button's text, e.g. "Referred Patients (1)". */
    public String referredButtonText() {
        Object r = page.evaluate("() => {" + JS + " const b=byClick('fnDashReferredPatients').find(vis); return b?norm(b.textContent):''; }");
        return r == null ? "" : r.toString();
    }

    /** Click <b>Referred Patients</b> and wait for the popup and its list. Returns false when the popup did not open. */
    public boolean clickReferredPatients() {
        clearToasts();
        if (!clickTagged(" const el=byClick('fnDashReferredPatients').find(vis);", "__ddRef")) return false;
        try {
            page.waitForFunction("() => { const m=[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight); return !!m && (m.innerText||'').toLowerCase().indexOf('referred patients')>=0; }",
                    null, waitOpts(20000));
        } catch (Exception e) { return false; }
        try {
            page.waitForFunction("() => { const m=[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight); return !!m && [...m.querySelectorAll('table tbody tr')].some(r=>(r.innerText||'').trim()); }",
                    null, waitOpts(15000));
        } catch (Exception ignore) { System.out.println("DoctorDashboard: the Referred Patients list stayed empty"); }
        waitForAngular(800);
        return true;
    }

    /** The referral rows of the open popup, each keyed by its column header (MRN, Patient Name, Category, Referred By, Department From, Referred To, Department To ...). */
    public List<java.util.Map<String, String>> referralRows() {
        Object r = page.evaluate("() => {" + JS + " const m=modal(); if(!m) return []; const t=m.querySelector('table'); if(!t) return [];"
                + " const heads=[...t.querySelectorAll('thead th')].map(h=>norm(h.innerText));"
                + " return [...t.querySelectorAll('tbody tr')].filter(vis).map(r=>{ const o={}; [...r.querySelectorAll('td')].forEach((c,i)=>{ o[heads[i]||('c'+i)]=norm(c.innerText); }); return o; })"
                + "   .filter(o=>Object.keys(o).some(k=>o[k])); }");
        List<java.util.Map<String, String>> out = new ArrayList<>();
        if (r instanceof List) {
            for (Object o : (List<?>) r) {
                if (!(o instanceof java.util.Map)) continue;
                java.util.Map<String, String> row = new java.util.LinkedHashMap<>();
                for (java.util.Map.Entry<?, ?> e : ((java.util.Map<?, ?>) o).entrySet()) row.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
                out.add(row);
            }
        }
        return out;
    }

    /** Tick the Select checkbox of the referral row with {@code mrn}. Returns true when it is ticked. */
    public boolean tickReferral(String mrn) {
        Object tagged = page.evaluate("(w) => {" + JS + " const m=modal(); if(!m) return false; const t=m.querySelector('table'); if(!t) return false;"
                + " const heads=[...t.querySelectorAll('thead th')].map(h=>norm(h.innerText)); const ci=heads.indexOf('MRN');"
                + " const row=[...t.querySelectorAll('tbody tr')].filter(vis).find(r=>{ const c=r.querySelectorAll('td')[ci]; return !!c && norm(c.innerText)===w; });"
                + " const cb=row?row.querySelector('input[type=checkbox]'):null; if(!cb) return false; cb.id='__ddTick'; return true; }", mrn);
        if (!Boolean.TRUE.equals(tagged)) return false;
        try { page.locator("#__ddTick").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("DoctorDashboard: the referral's checkbox could not be clicked (" + firstLine(e) + ") - in-page click");
            try { page.evaluate("() => { const e=document.getElementById('__ddTick'); if(e) e.click(); }"); } catch (Exception ignore) { }
        }
        waitForAngular(400);
        Object ok = false;
        try { ok = page.evaluate("() => { const e=document.getElementById('__ddTick'); const c=!!(e&&e.checked); if(e) e.removeAttribute('id'); return c; }"); }
        catch (Exception ignore) { }
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Choose the popup's <i>Referred To Department</i> and <i>Referred To</i> doctor the way the referral names them (department by its
     * name, doctor by the end of the option text, e.g. "Tan Sri Dato Seri Doctor_Tieba" for "Doctor_Tieba"). Returns what was chosen,
     * or a value starting with "(".
     */
    public String chooseReferredTo(String deptName, String doctorName) {
        Object d = page.evaluate("(w) => {" + JS + " const m=modal(); const s=m?[...m.querySelectorAll('select')].find(e=>e.getAttribute('ng-model')==='RefToDepartmentID'):null;"
                + " if(!s) return '(no Referred To Department box)';"
                + " const o=[...s.options].find(x=>norm(x.text).toLowerCase()===String(w).toLowerCase());"
                + " if(!o) return '(\"'+w+'\" is not in the Referred To Department list)';"
                + " s.value=o.value; s.dispatchEvent(new Event('change',{bubbles:true})); return norm(o.text); }", deptName);
        String dept = d == null ? "" : d.toString();
        if (dept.startsWith("(")) return dept;
        if (doctorName == null || doctorName.isEmpty()) return "Referred To Department " + dept;
        try {
            page.waitForFunction("() => { const m=[...document.querySelectorAll('.modal.in')].find(x=>x.offsetWidth||x.offsetHeight); const s=m?[...m.querySelectorAll('select')].find(e=>e.getAttribute('ng-model')==='RefToDoctorID'):null;"
                    + " return !!s && !s.disabled && s.options.length>1; }", null, waitOpts(12000));
        } catch (Exception e) { return "(the Referred To doctor list did not load for " + dept + ")"; }
        waitForAngular(600);
        Object c = page.evaluate("(w) => {" + JS + " const m=modal(); const s=m?[...m.querySelectorAll('select')].find(e=>e.getAttribute('ng-model')==='RefToDoctorID'):null; if(!s) return '(no Referred To box)';"
                + " const o=[...s.options].find(x=>norm(x.text).toLowerCase().endsWith(String(w).toLowerCase()));"
                + " if(!o) return '(\"'+w+'\" is not in the Referred To list for that department)';"
                + " s.value=o.value; s.dispatchEvent(new Event('change',{bubbles:true})); return norm(o.text); }", doctorName);
        String doc = c == null ? "" : c.toString();
        if (doc.startsWith("(")) return doc;
        waitForAngular(500);
        return "Referred To Department " + dept + ", Referred To " + doc;
    }

    private void startToastWatch() {
        page.evaluate("() => { window.__ddT2=[]; if(window.__ddObs2) window.__ddObs2.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('#toast-container .toast').forEach(el=>{ const t=(el.textContent||'').split(' ').filter(x=>x).join(' ').trim(); if(t && window.__ddT2.indexOf(t)<0) window.__ddT2.push(t); }); };"
                + " window.__ddObs2=new MutationObserver(grab); window.__ddObs2.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    private String readToasts() {
        try {
            Object r = page.evaluate("() => (window.__ddT2||[]).join(' | ')");
            return r == null ? "" : r.toString();
        } catch (Exception e) { return ""; }
    }

    /**
     * Click one of the popup's action buttons ({@code fnBookAppointment}, {@code fnPatientRegistration}, {@code fnPatientAdmission}).
     * Returns {message the app showed ("" when none), the URL hash afterwards, error ("" when the button was clicked)}.
     */
    public String[] clickReferralButton(String ngClickKey) {
        startToastWatch();
        String before = String.valueOf(page.evaluate("() => location.hash"));
        boolean clicked = clickTagged(" const m=modal(); const el=m?byClick('" + ngClickKey + "',m).find(vis):null;", "__ddRefBtn");
        if (!clicked) return new String[]{"", before, "the button was not found in the pop up"};
        waitForAngular(2500);
        String toast = readToasts();
        String after;
        try { after = String.valueOf(page.evaluate("() => location.hash")); } catch (Exception e) { after = before; }
        return new String[]{toast, after, ""};
    }

    private String[] readScreen(String mrnNg, String nameNg, String deptNg, String doctorNgs) {
        try {
            Object r = page.evaluate("(a) => {" + JS
                    + " const val=n=>{ const e=byNg(n)[0]; return e ? norm(e.value) : ''; };"
                    + " const sel=n=>{ const e=byNg(n)[0]; if(!e) return ''; if(e.tagName!=='SELECT') return norm(e.value);"
                    + "   const o=e.options[e.selectedIndex]; const t=norm(o?o.text:''); return /^-*select-*$/i.test(t.split(' ').join('')) ? '' : t; };"
                    + " const docs=String(a[3]).split(',').map(x=>sel(x.trim())).filter(x=>x).join(' / ');"
                    + " const toasts=[...document.querySelectorAll('#toast-container .toast')].map(t=>norm(t.textContent)).join(' | ');"
                    + " return [val(a[0]), val(a[1]), sel(a[2]), docs, toasts].join('~~'); }", java.util.Arrays.asList(mrnNg, nameNg, deptNg, doctorNgs));
            String[] f = (r == null ? "" : r.toString()).split("~~", -1);
            if (f.length == 5) return f;
        } catch (Exception e) { /* the page is changing */ }
        return new String[]{"", "", "", "", ""};
    }

    /**
     * After a Book Appointment / Registration / Admission click: wait for the screen's route, for its MRN box to fill, and for the
     * department / doctor boxes to settle (the Appointment screen replaces them asynchronously). Returns
     * {state, MRN, patient, department ("" = none), doctor ("" = none), toasts} or a one-element "(...)" array when the screen did not open.
     */
    public String[] screenFacts(String stateName, String mrnNg, String nameNg, String deptNg, String doctorNgs) {
        try {
            page.waitForFunction("(s) => window.angular.element(document.body).injector().get('$state').current.name===s", stateName, waitOpts(60000));
        } catch (Exception e) {
            Object now = page.evaluate("() => window.angular.element(document.body).injector().get('$state').current.name + ' @ ' + location.hash");
            return new String[]{"(the " + stateName + " screen did not open - the app is on " + now + ")"};
        }
        try {
            page.waitForFunction("(n) => {" + JS + " const e=byNg(n)[0]; return !!e && !!(e.value||'').trim(); }", mrnNg, waitOpts(30000));
        } catch (Exception ignore) { System.out.println("DoctorDashboard: the " + stateName + " screen's MRN box stayed empty"); }
        String[] last = null;
        int same = 0;
        for (int i = 0; i < 20; i++) {
            waitForAngular(900);
            String[] now = readScreen(mrnNg, nameNg, deptNg, doctorNgs);
            if (last != null && java.util.Arrays.equals(now, last)) { if (++same >= 3 && i >= 4) break; } else same = 0;
            last = now;
        }
        String[] out = new String[6];
        out[0] = stateName;
        System.arraycopy(last == null ? new String[]{"", "", "", "", ""} : last, 0, out, 1, 5);
        return out;
    }

    /** Scroll the field {@code ngModel} (or its visible wrapper) into the middle of the window, so a screenshot shows it. */
    public void revealField(String ngModel) {
        try {
            page.evaluate("(n) => { const e=[...document.querySelectorAll('[ng-model]')].find(x=>x.getAttribute('ng-model')===n); if(!e) return;"
                    + " let t=e; for(let i=0;i<4 && t && !(t.offsetWidth||t.offsetHeight);i++) t=t.parentElement; if(t) t.scrollIntoView({block:'center'}); }", ngModel);
        } catch (Exception ignore) { }
        waitForAngular(600);
    }

    /** Go back to the Doctor Dashboard (menu first, the router as a fallback). */
    public boolean backToDashboard() {
        clearToasts();
        if (navigateViaMenu()) return true;
        try { page.evaluate("() => window.angular.element(document.body).injector().get('$state').go('DoctorDashboard')"); } catch (Exception ignore) { }
        for (int i = 0; i < 30 && !onScreen(); i++) waitForAngular(1000);
        return onScreen();
    }

    // ---- helpers for the pages a referral opens ---------------------------

    /** The app's own warning dialog ("Warning! ... OK"), normalised; "" when none is showing. */
    public String visibleAlertText() {
        try {
            Object r = page.evaluate("() => {" + JS
                    + " const c=[...document.querySelectorAll('div')].filter(d=>vis(d) && /^ *warning/i.test(norm(d.innerText)) && norm(d.innerText).length<500"
                    + "   && [...d.querySelectorAll('button')].some(b=>/^ok$/i.test(norm(b.textContent))));"
                    + " if(!c.length) return ''; c.sort((a,b)=>norm(a.innerText).length-norm(b.innerText).length); return norm(c[0].innerText); }");
            return r == null ? "" : r.toString();
        } catch (Exception e) { return ""; }
    }

    /** Click OK on the app's warning dialog. */
    public boolean dismissAlert() {
        boolean ok = clickTagged(" const c=[...document.querySelectorAll('div')].filter(d=>vis(d) && /^ *warning/i.test(norm(d.innerText)) && norm(d.innerText).length<500"
                + " && [...d.querySelectorAll('button')].some(b=>/^ok$/i.test(norm(b.textContent))));"
                + " c.sort((a,b)=>norm(a.innerText).length-norm(b.innerText).length);"
                + " const el=c.length?[...c[0].querySelectorAll('button')].find(b=>/^ok$/i.test(norm(b.textContent))):null;", "__ddAlertOk");
        waitForAngular(500);
        return ok;
    }

    /**
     * Registration and Admission refuse to save a patient whose Next of Kin mobile is not 10 digits ("Please update the mandatory details in
     * NOK for X - NOK Mobile number must be 10 digits"). Every kin row on the page whose mobile is not 10 digits is loaded with its Edit
     * button, given a new 10-digit mobile and put back with Update. Returns "" when no kin needed it, what was corrected, or "(...)" on failure.
     */
    public String fixNextOfKinMobile() {
        Object r = page.evaluate("() => {" + JS
                + " const b=document.querySelector(\"button[ng-click*='AddKinDetails']\"); if(!b) return [];"
                + " let sc=A.element(b).scope(), list=null; for(let i=0;i<12&&sc;i++){ if(Array.isArray(sc.KinDetailsList)){ list=sc.KinDetailsList; break; } sc=sc.$parent; }"
                + " if(!list) return [];"
                + " return list.map((k,i)=>[i, String(k.kinname||''), String(k.kinmobileno==null?'':k.kinmobileno)]).filter(o=>o[2].split('').filter(c=>c>='0'&&c<='9').length!==10); }");
        if (!(r instanceof List) || ((List<?>) r).isEmpty()) return "";
        StringBuilder done = new StringBuilder();
        for (Object o : (List<?>) r) {
            List<?> kin = (List<?>) o;
            int idx = ((Number) kin.get(0)).intValue();
            String kname = String.valueOf(kin.get(1)), old = String.valueOf(kin.get(2));
            String fresh = "1" + Long.toString(System.currentTimeMillis()).substring(4);
            boolean edit = clickTagged(" const eds=[...document.querySelectorAll('button')].filter(x=>(x.getAttribute('ng-click')||'').indexOf('EditKinDetails')>=0 && vis(x));"
                    + " const el=eds[" + idx + "];", "__ddKinEdit");
            if (!edit) return "(the Edit button of next of kin " + kname + " was not found)";
            waitForAngular(1000);
            Object set = page.evaluate("(v) => {" + JS + " const e=byNg('Registration.KinMobileNo').find(vis); if(!e) return false; const c=A.element(e).controller('ngModel');"
                    + " e.value=v; if(c){ c.$setViewValue(v); c.$render(); } e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return true; }", fresh);
            if (!Boolean.TRUE.equals(set)) return "(the kin form's Mobile box was not found after Edit for " + kname + ")";
            boolean upd = clickTagged(" const el=[...document.querySelectorAll('button')].find(x=>(x.getAttribute('ng-click')||'').indexOf('ModifyKinDetails')>=0 && vis(x));", "__ddKinUpd");
            if (!upd) return "(the kin form's Update button was not found for " + kname + ")";
            waitForAngular(1200);
            if (done.length() > 0) done.append("; ");
            done.append(kname).append(": mobile ").append(old).append(" -> ").append(fresh);
        }
        Object left = page.evaluate("() => {" + JS
                + " const b=document.querySelector(\"button[ng-click*='AddKinDetails']\"); if(!b) return 0; let sc=A.element(b).scope(), list=null;"
                + " for(let i=0;i<12&&sc;i++){ if(Array.isArray(sc.KinDetailsList)){ list=sc.KinDetailsList; break; } sc=sc.$parent; }"
                + " return list ? list.filter(k=>String(k.kinmobileno==null?'':k.kinmobileno).split('').filter(c=>c>='0'&&c<='9').length!==10).length : 0; }");
        if (left instanceof Number && ((Number) left).intValue() > 0) return "(" + done + " - but " + left + " kin row(s) still have a mobile that is not 10 digits)";
        return done.toString();
    }

    /**
     * Appointment page: walk the Departments (in list order) until one offers a doctor AND a free slot in the Next Schedule, leaving that
     * Department and its first Doctor selected. Returns "Department X | Doctor Y" or "(...)".
     */
    /** The appointment date chosen by {@link #walkAppointmentDepartments} when the first dates had no clinic ("" = the page's own date was used). */
    public String walkDate = "";

    /** Put {@code d} in the Appointment Date box and leave it, so the page reloads the schedule for that date. */
    private void setAppointmentDateBox(java.time.LocalDate d) {
        String v = d.format(DMY);
        page.evaluate("(v) => { document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); const e=document.getElementById('txtAppointmentDate'); if(!e) return;"
                + " const c=window.angular.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true})); }", v);
    }

    private boolean nextScheduleHasSlot() {
        Object s = page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/Next Schedule/.test((x.caption&&x.caption.textContent)||''));"
                + " return !!t && [...t.querySelectorAll('tbody tr input[type=checkbox]')].some(c=>!c.disabled); }");
        return Boolean.TRUE.equals(s);
    }

    /**
     * Appointment Type box: choose "Department" (the Doctor box is then disabled and the page books against the Department's own schedule).
     * Returns the text the box shows afterwards ("Department" when it took, anything else when it did not, "" when the box is missing).
     */
    public String selectAppointmentTypeDepartment() {
        Object r = page.evaluate("() => {" + JS
                + " const e=byNg('PatientData.AppointmentTypeID').find(x=>x.tagName==='SELECT'); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>norm(o.text).toLowerCase()==='department'); if(i<0) return '(no Department in the list: '+[...e.options].map(o=>norm(o.text)).join(', ')+')';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){ try{ $(e).trigger('change'); }catch(x){} }"
                + " return norm(e.options[e.selectedIndex].text); }");
        waitForAngular(1500);   // the Department list reloads for this type
        return r == null ? "" : r.toString();
    }

    /** Appointment Type as the page shows it now ("" when the box is missing). */
    public String appointmentTypeText() {
        Object r = page.evaluate("() => {" + JS + " const e=byNg('PatientData.AppointmentTypeID').find(x=>x.tagName==='SELECT'); return e&&e.options[e.selectedIndex]?norm(e.options[e.selectedIndex].text):''; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Days box (PatientData.AgeDays). The page refuses to save an age with 0 days ("Please Enter Days"), and the box is disabled for an existing
     * patient, so it is filled through the form model. Enters {@code days} when the box is blank or 0 and leaves a real value alone.
     * Returns what happened, e.g. "was 0 -> entered 1 (model 1)", "already 4", or "(...)" when the box is not there.
     */
    public String enterDays(int days) {
        Object r = page.evaluate("(n) => {" + JS
                + " const e=byNg('PatientData.AgeDays')[0]; if(!e) return '(the Days box was not found)';"
                + " const was=norm(e.value); if(was && was!=='0') return 'already '+was;"
                + " const c=A.element(e).controller('ngModel'); const sc=A.element(e).scope();"
                + " e.value=String(n); if(c){ c.$setViewValue(String(n)); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const model=sc&&sc.PatientData?sc.PatientData.AgeDays:'?';"
                + " return 'was '+(was||'blank')+' -> entered '+norm(e.value)+' (model '+model+')'+(e.disabled?' - the box is disabled for an existing patient, so the value went in through the form model':''); }", days);
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    public String walkAppointmentDepartments(int maxDepts) { return walkAppointmentDepartments(maxDepts, true, ""); }

    /**
     * Same walk. {@code needDoctor=false} (Appointment Type Department) takes the Department's own schedule and picks no Doctor;
     * {@code preferredDept} (the referral's Department) is tried first.
     */
    public String walkAppointmentDepartments(int maxDepts, boolean needDoctor, String preferredDept) {
        walkDate = "";
        Object names = page.evaluate("() => {" + JS + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='PatientData.DepartmentID'); if(!e) return [];"
                + " return [...e.options].map((o,i)=>[i,norm(o.text)]).filter(o=>o[1] && !/^-*select-*$/i.test(o[1].split(' ').join(''))); }");
        if (!(names instanceof List) || ((List<?>) names).isEmpty()) return "(the Department list is empty)";
        java.util.List<Object> ordered = new java.util.ArrayList<>((List<?>) names);
        final String pref = preferredDept == null ? "" : preferredDept.trim();
        if (!pref.isEmpty()) ordered.sort(java.util.Comparator.comparingInt(o -> String.valueOf(((List<?>) o).get(1)).trim().equalsIgnoreCase(pref) ? 0 : 1));
        int tried = 0;
        for (Object o : ordered) {
            if (++tried > maxDepts) break;
            List<?> d = (List<?>) o;
            int di = ((Number) d.get(0)).intValue();
            String dname = String.valueOf(d.get(1));
            page.evaluate("(i) => {" + JS + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='PatientData.DepartmentID');"
                    + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){ try{ $(e).trigger('change'); }catch(x){} } }", di);
            String doc = needDoctor ? "" : "(none needed)";
            for (int w = 0; needDoctor && w < 14 && doc.isEmpty(); w++) {
                waitForAngular(600);
                Object dn = page.evaluate("() => {" + JS + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='PatientData.DoctorModalityID'); if(!e) return '';"
                        + " const i=[...e.options].findIndex(o=>o.value && o.value!=='?' && norm(o.text) && !/^-*select-*$/i.test(norm(o.text).split(' ').join('')));"
                        + " if(i<0) return ''; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){ try{ $(e).trigger('change'); }catch(x){} } return norm(e.options[i].text); }");
                doc = dn == null ? "" : dn.toString();
            }
            if (doc.isEmpty()) { System.out.println("DoctorDashboard.appt: " + dname + " offers no doctor"); continue; }
            boolean slot = false;
            for (int w = 0; w < 12 && !slot; w++) {
                waitForAngular(600);
                Object s = page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/Next Schedule/.test((x.caption&&x.caption.textContent)||''));"
                        + " return !!t && [...t.querySelectorAll('tbody tr input[type=checkbox]')].some(c=>!c.disabled); }");
                slot = Boolean.TRUE.equals(s);
            }
            if (!slot && visibleToastTexts().toLowerCase().contains("no clinic")) {
                // "No clinic on this date.!" - this doctor has no clinic on that date: try the next few dates before leaving the department
                for (int off = 2; off <= 7 && !slot; off++) {
                    java.time.LocalDate tryDate = java.time.LocalDate.now().plusDays(off - 1);
                    setAppointmentDateBox(tryDate);
                    for (int w = 0; w < 6 && !slot; w++) { waitForAngular(600); slot = nextScheduleHasSlot(); }
                    if (slot) walkDate = tryDate.format(DMY);
                    else System.out.println("DoctorDashboard.appt: " + dname + " / " + doc + " - no clinic from " + tryDate.format(DMY));
                }
            }
            if (slot) return "Department " + dname + (needDoctor ? " | Doctor " + doc : " | no Doctor needed (Appointment Type Department)") + (walkDate.isEmpty() ? "" : " | Appointment Date " + walkDate + " (earlier dates had no clinic)");
            System.out.println("DoctorDashboard.appt: " + dname + " / " + doc + " has no free slot tomorrow");
        }
        return "(none of the first " + Math.min(tried, maxDepts) + " departments offered a doctor with a free slot)";
    }

    /** Every toast on screen right now, joined with " | ". */
    public String visibleToastTexts() {
        try {
            Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast')].map(t=>norm(t.textContent)).join(' | '); }");
            return r == null ? "" : r.toString();
        } catch (Exception e) { return ""; }
    }

    /** The age the Appointment page holds, e.g. "62Y 1M 0D" ("" when the boxes are not there). */
    public String ageText() {
        try {
            Object r = page.evaluate("() => {" + JS + " const g=n=>{ const e=byNg(n)[0]; return e ? norm(e.value) : ''; }; const y=g('PatientData.AgeYear'), m=g('PatientData.AgeMonth'), d=g('PatientData.AgeDays'); return (y||m||d) ? (y||'0')+'Y '+(m||'0')+'M '+(d||'0')+'D' : ''; }");
            return r == null ? "" : r.toString();
        } catch (Exception e) { return ""; }
    }

    /** What the screen offers — for a FAIL message when something is not where it was expected. */
    public String describeScreen() {
        Object r = page.evaluate("() => {" + JS
                + " const m=modal(); return (m?'popup: '+norm(m.innerText).slice(0,120)+' ## ':'')+[...document.querySelectorAll('button')].filter(vis).slice(0,14).map(e=>norm(e.textContent).slice(0,20)).join(' ; '); }");
        return r == null ? "" : r.toString();
    }
}
