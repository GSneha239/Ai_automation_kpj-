package com.kpj.pages.AncillaryServices_page.HouseKeeping_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Ancillary Services &gt; House Keeping &gt; <b>House Keeping Schedule</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>House Keeping</b> (flyout) → <b>House Keeping Schedule</b>
 * ({@code #/HouseKeepingSchedule}) → <b>Schedule Template</b> tab (the default/active one; a second tab,
 * <b>Assigning Schedule</b>, exists alongside it) → enter <b>Name of Schedule Template</b>
 * ({@code ScheduleTemplate.templatename}) → select <b>Activity</b> ({@code ScheduleTemplate.activityid}) +
 * <b>Day</b> ({@code ScheduleTemplate.dayid}) → <b>Add</b> ({@code AddSchedule()}) — appends a row to the
 * <b>Activities Performed</b> grid → <b>Save Template</b> ({@code fnAddUpdateScheduleTemplate()}) → toast →
 * the new name appears as a row in the <b>Schedule Template Name</b> list on the left.</p>
 *
 * <p><b>Fields (discovered live 2026-09-08):</b> {@code ScheduleTemplate.templatename} (id {@code tmplname}),
 * {@code ScheduleTemplate.activityid} (options: Cleaning, Sanitizing, Swipping),
 * {@code ScheduleTemplate.dayid} (Sunday..Saturday), {@code ScheduleTemplate.requiredtime} (Duration, minutes,
 * optional), {@code ScheduleTemplate.repeateafter} (To be repeated after, minutes, optional), and the Start
 * Time picker {@code inputTime} (defaults to "now" — not touched by this flow). Buttons:
 * {@code AddSchedule()} (Add), {@code fnAddUpdateScheduleTemplate()} (Save Template). <b>Add works with only
 * Activity + Day set</b> — Start Time keeps its default and Duration/Repeat are optional, verified live.</p>
 *
 * <p><b>Two same-shaped tables, told apart by their captions.</b> {@code Activities Performed} is a
 * {@code <caption>} on the grid the Add button fills (Activity/Day/Time/Duration/To be Repeated
 * After/Delete). {@code Schedule Template Name} is a {@code <th>} on the separate list of already-saved
 * templates, which gains a row with the new template's name only after Save Template succeeds.</p>
 *
 * <p><b>Assigning Schedule tab</b> ({@code AssignSchedule.*} model): select <b>Schedule</b>
 * ({@code AssignSchedule.scheduleid}, the templates saved on the other tab) + <b>Employee Name</b>
 * ({@code AssignSchedule.employeeid}) + <b>Assigned Date</b> ({@code AssignSchedule.assigndate}, a
 * datepicker pre-filled with today) → <b>Add</b> ({@code AddEmployeeActivity()}, shared with the manual
 * row below it) → select <b>Activity</b> ({@code AssignSchedule.activityid}) + <b>Day</b>
 * ({@code AssignSchedule.dayid}) + <b>Duration</b> ({@code AssignSchedule.duration}, minutes) → <b>Add</b>
 * again → <b>Save Schedule</b> ({@code fnAddUpdateEmployeeSchedule()}) → toast.
 * <b>{@code AddEmployeeActivity()} only requires Employee to be set</b> — verified live: with the
 * Activity/Day selects still at "-Select-" it pulls the chosen Schedule's OWN activity/day straight from
 * the template into a new <b>Assigned/Change Activities</b> row (its {@code <caption>}); Duration is then
 * 0 unless entered first. So the two-Add flow this test follows makes one row from the template
 * (Employee + Date only) and a second, fully custom row (Activity + Day + Duration set explicitly) —
 * clicking without an Employee selected rejects with "Please Select Employee!".</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with any counter (verified live with
 * the CASHIER counter on KPJ Damansara Specialist Hospital; unlike some Equipment/Asset screens this one does
 * not require an OPD counter).</p>
 */
public class HouseKeepingSchedule extends BasePage {

    public HouseKeepingSchedule(Page page) { super(page); }

    public static final String ROUTE = "#/HouseKeepingSchedule";
    private static final String M = "ScheduleTemplate.";
    private static final String AM = "AssignSchedule.";

    public String lastTemplateName = "";
    public String lastActivity = "", lastDay = "";
    public String lastSchedule = "", lastEmployee = "", lastAssignedDate = "";
    public String lastAssignActivity = "", lastAssignDay = "", lastDuration = "";

    // ---- shared JS ---------------------------------------------------------

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const A=window.angular; const $=window.jQuery;"
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const setInp=(ng,v)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
            + "const pick=(ng,text)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  const i=text ? [...e.options].findIndex(o=>norm(o.textContent).toLowerCase()===String(text).toLowerCase())"
            + "               : [...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
            + "  if(i<0) return '(no-option)';"
            + "  e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "  return norm(e.options[i].textContent); };"
            + "const tableByCaption=(re)=>{ const rx=new RegExp(re,'i');"
            + "  const cap=[...document.querySelectorAll('caption')].find(c=>rx.test(norm(c.textContent)));"
            + "  return cap ? cap.closest('table') : null; };"
            + "const tableByHeader=(re)=>{ const rx=new RegExp(re,'i');"
            + "  const th=[...document.querySelectorAll('th')].find(c=>rx.test(norm(c.textContent)));"
            + "  return th ? th.closest('table') : null; };"
            + "const rowsOf=(tbl)=>tbl ? [...tbl.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(t=>t) : [];";

    private static final String ARM_TOASTS = ""
            + " window.__hkToasts=[]; if(window.__hkObs) window.__hkObs.disconnect();"
            + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__hkToasts.includes(t)) window.__hkToasts.push(t); }); };"
            + " window.__hkObs=new MutationObserver(grab); window.__hkObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation --------------------------------------------------------

    /** <b>Ancillary Services</b> → <b>House Keeping</b> (flyout) → <b>House Keeping Schedule</b>, else the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("HouseKeepingSchedule.nav: nav menu never appeared");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^house\\s*keeping$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^house\\s*keeping\\s*schedule$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/HouseKeepingSchedule/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__hkMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__hkMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("HouseKeepingSchedule.nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__hkMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("HouseKeepingSchedule.nav: falling back to direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        waitForAngular(800);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("housekeepingschedule"); }

    // ---- Schedule Template tab ---------------------------------------------

    /** Click the <b>Schedule Template</b> tab (bootstrap tab, {@code data-target="#schdulTemp"}). */
    public boolean clickScheduleTemplateTab() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[data-toggle=tab]')]"
                + "   .find(x=>/^schedule\\s*template$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!a) return false; a.id='__hkTab'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("HouseKeepingSchedule.clickScheduleTemplateTab: tab not found"); return false; }
        try { page.locator("#__hkTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("HouseKeepingSchedule.clickScheduleTemplateTab: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__hkTab'); if(e) e.removeAttribute('id'); }");
        waitForAngular(600);
        try {
            page.waitForFunction("(m) => !![...document.querySelectorAll(\"[ng-model='\"+m+\"templatename']\")].find(e=>e.offsetParent!==null)", M,
                    new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { System.out.println("HouseKeepingSchedule.clickScheduleTemplateTab: form did not render"); }
        return onScheduleTemplateForm();
    }

    public boolean onScheduleTemplateForm() {
        return Boolean.TRUE.equals(page.evaluate(
                "(m) => !![...document.querySelectorAll(\"[ng-model='\"+m+\"templatename']\")].find(e=>e.offsetParent!==null)", M));
    }

    // ---- template name / activity / day ------------------------------------

    /** Enter the <b>Name of Schedule Template</b> ({@code ScheduleTemplate.templatename}). */
    public String enterTemplateName(String name) {
        Object r = page.evaluate("(v) => {" + JS + " return setInp('" + M + "templatename', v); }", name);
        lastTemplateName = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("HouseKeepingSchedule: Template name = " + lastTemplateName);
        return lastTemplateName;
    }

    /** Select the <b>Activity</b> ({@code ScheduleTemplate.activityid}) — the first real option. */
    public String selectActivity() { return selectActivity(null); }

    /** Select the <b>Activity</b>, preferring the option whose text equals {@code text} (case-insensitive). */
    public String selectActivity(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('" + M + "activityid', t); }", text);
        lastActivity = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("HouseKeepingSchedule: Activity = " + lastActivity);
        return lastActivity;
    }

    /** Select the <b>Day</b> ({@code ScheduleTemplate.dayid}) — the first real option. */
    public String selectDay() { return selectDay(null); }

    /** Select the <b>Day</b>, preferring the option whose text equals {@code text} (case-insensitive). */
    public String selectDay(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('" + M + "dayid', t); }", text);
        lastDay = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("HouseKeepingSchedule: Day = " + lastDay);
        return lastDay;
    }

    public boolean activitySelected() { return isReal(lastActivity); }
    public boolean daySelected() { return isReal(lastDay); }

    // ---- Add -> Activities Performed ---------------------------------------

    /** Row count currently in the <b>Activities Performed</b> grid. */
    public int activitiesPerformedRowCount() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf(tableByCaption('Activities Performed')).length; }");
        return r == null ? 0 : Integer.parseInt(r.toString());
    }

    /** The <b>Activities Performed</b> grid rows, as rendered text (one entry per row). */
    public List<String> activitiesPerformedRows() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf(tableByCaption('Activities Performed')); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }

    /**
     * Click <b>Add</b> ({@code AddSchedule()}), which appends the chosen Activity + Day (+ the Start Time
     * field's current value) as a new row in the <b>Activities Performed</b> grid, then clears the
     * Activity/Day selects back to "-Select-".
     *
     * @return the new row's text, or {@code ""} if the row count did not increase
     */
    public String clickAdd() {
        int before = activitiesPerformedRowCount();

        Object tagged = page.evaluate("() => { const c=[...document.querySelectorAll('button')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/AddSchedule\\(\\)/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__hkAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("HouseKeepingSchedule.clickAdd: Add button not found"); return ""; }
        try { page.locator("#__hkAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("HouseKeepingSchedule.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__hkAdd'); if(e) e.removeAttribute('id'); }");

        try {
            page.waitForFunction("(n) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const cap=[...document.querySelectorAll('caption')].find(c=>/Activities Performed/i.test(norm(c.textContent)));"
                    + " const t=cap?cap.closest('table'):null; const rows=t?t.querySelectorAll('tbody tr').length:0; return rows>n; }",
                    before, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { System.out.println("HouseKeepingSchedule.clickAdd: row count did not increase"); }
        waitForAngular(400);

        List<String> rows = activitiesPerformedRows();
        String newRow = rows.isEmpty() ? "" : rows.get(rows.size() - 1);
        System.out.println("HouseKeepingSchedule: Add -> " + (newRow.isEmpty() ? "(no new row)" : newRow));
        return newRow;
    }

    // ---- Save Template -> Schedule Template Name list ----------------------

    /** The <b>Schedule Template Name</b> list rows (already-saved templates), as rendered text. */
    public List<String> scheduleTemplateNames() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf(tableByHeader('Schedule Template Name')); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }

    /** Click <b>Save Template</b> ({@code fnAddUpdateScheduleTemplate()}) and return the toast ("" if none). */
    public String saveAndGetToast() {
        waitForToastsToClear();

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const c=[...document.querySelectorAll('button')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/fnAddUpdateScheduleTemplate/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__hkSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("HouseKeepingSchedule.save: Save Template button not found"); return ""; }

        try { page.locator("#__hkSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("HouseKeepingSchedule.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__hkSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(500);

        acceptSaveDialog();
        waitForAngular(400);

        try {
            page.waitForFunction("() => (window.__hkToasts||[]).some(a=>/saved|success|please|fill|enter|select|required|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__hkToasts||[]).includes(t)) (window.__hkToasts=window.__hkToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__hkToasts||[];"
                + " return a.find(x=>/saved\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Assigning Schedule tab ---------------------------------------------

    /** Click the <b>Assigning Schedule</b> tab (bootstrap tab, {@code data-toggle=tab}). */
    public boolean clickAssigningScheduleTab() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[data-toggle=tab]')]"
                + "   .find(x=>/^assigning\\s*schedule$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " if(!a) return false; a.id='__hkTab2'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("HouseKeepingSchedule.clickAssigningScheduleTab: tab not found"); return false; }
        try { page.locator("#__hkTab2").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("HouseKeepingSchedule.clickAssigningScheduleTab: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__hkTab2'); if(e) e.removeAttribute('id'); }");
        waitForAngular(600);
        try {
            page.waitForFunction("(m) => !![...document.querySelectorAll(\"[ng-model='\"+m+\"scheduleid']\")].find(e=>e.offsetParent!==null)", AM,
                    new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { System.out.println("HouseKeepingSchedule.clickAssigningScheduleTab: form did not render"); }
        return onAssigningScheduleForm();
    }

    public boolean onAssigningScheduleForm() {
        return Boolean.TRUE.equals(page.evaluate(
                "(m) => !![...document.querySelectorAll(\"[ng-model='\"+m+\"scheduleid']\")].find(e=>e.offsetParent!==null)", AM));
    }

    /** Select the <b>Schedule</b> ({@code AssignSchedule.scheduleid}) — a saved Schedule Template — the first real option. */
    public String selectSchedule() { return selectSchedule(null); }

    /** Select the <b>Schedule</b>, preferring the option whose text equals {@code text} (case-insensitive). */
    public String selectSchedule(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('" + AM + "scheduleid', t); }", text);
        lastSchedule = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("HouseKeepingSchedule: Schedule = " + lastSchedule);
        return lastSchedule;
    }

    /** Select the <b>Employee Name</b> ({@code AssignSchedule.employeeid}) — the first real option. */
    public String selectEmployee() { return selectEmployee(null); }

    /** Select the <b>Employee Name</b>, preferring the option whose text equals {@code text} (case-insensitive). */
    public String selectEmployee(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('" + AM + "employeeid', t); }", text);
        lastEmployee = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("HouseKeepingSchedule: Employee = " + lastEmployee);
        return lastEmployee;
    }

    /** Enter today's date (dd/MM/yyyy, matching the field's own default) into <b>Assigned Date</b>. */
    public String enterAssignedDate() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return enterAssignedDate(String.format("%02d/%02d/%04d", today.getDayOfMonth(), today.getMonthValue(), today.getYear()));
    }

    /** Enter the <b>Assigned Date</b> ({@code AssignSchedule.assigndate}, a datepicker pre-filled with today). */
    public String enterAssignedDate(String date) {
        Object r = page.evaluate("(v) => {" + JS + " return setInp('" + AM + "assigndate', v); }", date);
        lastAssignedDate = r == null ? "" : r.toString();
        waitForAngular(300);
        try { page.locator("[ng-model='" + AM + "assigndate']").first().blur(); } catch (Exception ignore) { }
        waitForAngular(300);
        System.out.println("HouseKeepingSchedule: Assigned Date = " + lastAssignedDate);
        return lastAssignedDate;
    }

    public boolean scheduleSelected() { return isReal(lastSchedule); }
    public boolean employeeSelected() { return isReal(lastEmployee); }
    public boolean assignedDateEntered() { return isReal(lastAssignedDate); }

    /** Select the <b>Activity</b> ({@code AssignSchedule.activityid}) on the Assigning Schedule tab. */
    public String selectAssignActivity(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('" + AM + "activityid', t); }", text);
        lastAssignActivity = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("HouseKeepingSchedule: (Assign) Activity = " + lastAssignActivity);
        return lastAssignActivity;
    }

    /** Select the <b>Day</b> ({@code AssignSchedule.dayid}) on the Assigning Schedule tab. */
    public String selectAssignDay(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('" + AM + "dayid', t); }", text);
        lastAssignDay = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("HouseKeepingSchedule: (Assign) Day = " + lastAssignDay);
        return lastAssignDay;
    }

    /** Enter the <b>Duration</b> ({@code AssignSchedule.duration}, minutes) on the Assigning Schedule tab. */
    public String enterDuration(String minutes) {
        Object r = page.evaluate("(v) => {" + JS + " return setInp('" + AM + "duration', v); }", minutes);
        lastDuration = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("HouseKeepingSchedule: Duration = " + lastDuration);
        return lastDuration;
    }

    /** Row count currently in the <b>Assigned/Change Activities</b> grid. */
    public int assignedActivitiesRowCount() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf(tableByCaption('Assigned/Change Activities')).length; }");
        return r == null ? 0 : Integer.parseInt(r.toString());
    }

    /** The <b>Assigned/Change Activities</b> grid rows, as rendered text (one entry per row). */
    public List<String> assignedActivitiesRows() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf(tableByCaption('Assigned/Change Activities')); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }

    /**
     * Click <b>Add</b> ({@code AddEmployeeActivity()}) on the Assigning Schedule tab — appends a row to
     * <b>Assigned/Change Activities</b>. With Activity/Day left at "-Select-" it pulls the chosen
     * Schedule's own activity/day; with them set explicitly it uses those values instead. Only Employee
     * is strictly required (verified live: rejects with "Please Select Employee!" otherwise).
     *
     * @return the new row's text, or {@code ""} if the row count did not increase
     */
    public String clickAddEmployeeActivity() {
        int before = assignedActivitiesRowCount();

        Object tagged = page.evaluate("() => { const c=[...document.querySelectorAll('button')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/AddEmployeeActivity\\(\\)/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__hkAdd2'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("HouseKeepingSchedule.clickAddEmployeeActivity: Add button not found"); return ""; }
        try { page.locator("#__hkAdd2").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("HouseKeepingSchedule.clickAddEmployeeActivity: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__hkAdd2'); if(e) e.removeAttribute('id'); }");

        try {
            page.waitForFunction("(n) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const cap=[...document.querySelectorAll('caption')].find(c=>/Assigned\\/Change Activities/i.test(norm(c.textContent)));"
                    + " const t=cap?cap.closest('table'):null; const rows=t?t.querySelectorAll('tbody tr').length:0; return rows>n; }",
                    before, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { System.out.println("HouseKeepingSchedule.clickAddEmployeeActivity: row count did not increase"); }
        waitForAngular(400);

        List<String> rows = assignedActivitiesRows();
        String newRow = rows.isEmpty() ? "" : rows.get(rows.size() - 1);
        System.out.println("HouseKeepingSchedule: AddEmployeeActivity -> " + (newRow.isEmpty() ? "(no new row)" : newRow));
        return newRow;
    }

    /** Click <b>Save Schedule</b> ({@code fnAddUpdateEmployeeSchedule()}) and return the toast ("" if none). */
    public String saveScheduleAndGetToast() {
        waitForToastsToClear();

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const c=[...document.querySelectorAll('button')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/fnAddUpdateEmployeeSchedule/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__hkSave2'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("HouseKeepingSchedule.saveSchedule: Save Schedule button not found"); return ""; }

        try { page.locator("#__hkSave2").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("HouseKeepingSchedule.saveSchedule: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__hkSave2'); if(e) e.removeAttribute('id'); }");
        waitForAngular(500);

        acceptSaveDialog();
        waitForAngular(400);

        try {
            page.waitForFunction("() => (window.__hkToasts||[]).some(a=>/saved|success|please|fill|enter|select|required|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__hkToasts||[]).includes(t)) (window.__hkToasts=window.__hkToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__hkToasts||[];"
                + " return a.find(x=>/saved\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("HouseKeepingSchedule: a previous toast is still on screen — the next capture may be stale");
        }
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }
}
