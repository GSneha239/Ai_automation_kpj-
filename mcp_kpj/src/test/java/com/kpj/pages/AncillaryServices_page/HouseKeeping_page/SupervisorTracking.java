package com.kpj.pages.AncillaryServices_page.HouseKeeping_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Ancillary Services &gt; House Keeping &gt; <b>Supervisor Tracking</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>House Keeping</b> (flyout) → <b>Supervisor Tracking</b>
 * ({@code #/SupervisorTracking}) → select <b>Employee Name</b> ({@code SupervisorTracking.employeeid}) —
 * this alone fetches that employee's assigned activities into the <b>Activities Assigned</b> grid, since
 * the select carries {@code ng-change="GetActivityDetails()"} — enter <b>From Date</b> / <b>To Date</b>
 * → select <b>Activity</b> ({@code SupervisorTracking.activityid}) + <b>Status</b>
 * ({@code SupervisorTracking.activitystatus}) + <b>Remark</b> ({@code SupervisorTracking.remarks}) →
 * <b>Change</b> ({@code Updatedata()}) → toast.</p>
 *
 * <p><b>Fields (discovered live 2026-09-08):</b> {@code SupervisorTracking.employeeid} (id
 * {@code etEmployeeName}), {@code SupervisorTracking.fromdate} / {@code .todate} (datepickers,
 * {@code dd/MM/yyyy}), {@code SupervisorTracking.activityid} (id {@code stActivity}; options: Cleaning,
 * Sanitizing, Swipping), {@code SupervisorTracking.activitystatus} (id {@code stStatus}; options:
 * Completed, In Progress, Not completed), {@code SupervisorTracking.remarks} (textarea, id
 * {@code STRemarks}). Button: {@code Updatedata()} (Change).</p>
 *
 * <p><b>From Date / To Date are not sent to the server — verified live.</b> The XHR
 * {@code POST /api/SupervisorTracking/Fetch} that {@code GetActivityDetails()} fires carries only
 * {@code {employeeid, ExecFlag:"GetEmployeeTrackingSchedule"}}; the date fields are not in the payload.
 * Selecting the Employee is what populates the grid — no separate Search click is needed (there IS a
 * Search button, {@code GetActivityDetails()} again, but it is redundant with the select's own
 * {@code ng-change}).</p>
 *
 * <p><b>Not every employee has tracking data.</b> Only employees with an actual assigned schedule (see
 * {@link HouseKeepingSchedule}'s Assigning Schedule tab) return rows — most of the ~1300 employees in the
 * dropdown return an empty grid, and some (e.g. the logged-in admin's own "Sancy Admin" employee record)
 * returned empty even after being assigned a schedule in this same session, suggesting Supervisor Tracking
 * is scoped to real housekeeping staff. {@code selectEmployeeWithData} scans forward through the dropdown
 * (skipping the given number of leading admin-like entries) until one returns a non-empty grid.</p>
 *
 * <p><b>Each grid row ALSO carries its own inline Status/Remark/Completed controls</b>
 * ({@code link.activitystatus}, {@code link.remark}, {@code link.completed}, disabled once completed) —
 * distinct from the {@code SupervisorTracking.*} fields below the grid that {@code Updatedata()} reads.
 * This page object only drives the latter (Activity/Status/Remark + Change), matching the requested flow.</p>
 *
 * <p><b>Change resets the whole form</b> (verified live: after a successful Change, Employee Name reverts
 * to "-Select-" and the grid empties) — the same "form clears on save" behavior as the Schedule Template
 * and Assigning Schedule tabs, so this reads the grid's rows BEFORE clicking Change, not after.</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with any counter (verified live with
 * the CASHIER counter on KPJ Damansara Specialist Hospital).</p>
 */
public class SupervisorTracking extends BasePage {

    public SupervisorTracking(Page page) { super(page); }

    public static final String ROUTE = "#/SupervisorTracking";
    private static final String M = "SupervisorTracking.";

    public String lastEmployee = "", lastFromDate = "", lastToDate = "";
    public String lastActivity = "", lastStatus = "", lastRemark = "";
    /** Employees tried before one with tracking data was found (see {@link #selectEmployeeWithData}). */
    public int employeesTried = 0;

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
            + "const rowsOf=(tbl)=>tbl ? [...tbl.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(t=>t) : [];";

    private static final String ARM_TOASTS = ""
            + " window.__stToasts=[]; if(window.__stObs) window.__stObs.disconnect();"
            + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__stToasts.includes(t)) window.__stToasts.push(t); }); };"
            + " window.__stObs=new MutationObserver(grab); window.__stObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation --------------------------------------------------------

    /** <b>Ancillary Services</b> → <b>House Keeping</b> (flyout) → <b>Supervisor Tracking</b>, else the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("SupervisorTracking.nav: nav menu never appeared");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^house\\s*keeping$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^supervisor\\s*tracking$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/SupervisorTracking/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__stMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__stMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("SupervisorTracking.nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__stMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("SupervisorTracking.nav: falling back to direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        waitForAngular(800);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("supervisortracking"); }

    // ---- Employee + dates ---------------------------------------------------

    /** Select the <b>Employee Name</b> ({@code SupervisorTracking.employeeid}) — this alone fetches the grid. */
    public String selectEmployee(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('" + M + "employeeid', t); }", text);
        lastEmployee = r == null ? "" : r.toString();
        waitForAngular(800);
        System.out.println("SupervisorTracking: Employee = " + lastEmployee);
        return lastEmployee;
    }

    /**
     * Select employees in dropdown order (skipping the given number of leading entries — the earliest
     * options are admin/system-seeded employees that carry no real tracking data) until one whose
     * Activities Assigned grid comes back non-empty. Leaves that employee selected.
     *
     * @return the employee name that worked, or {@code ""} if none of the {@code maxEmployees} tried had data
     */
    public String selectEmployeeWithData(int skipLeading, int maxEmployees) {
        employeesTried = 0;
        for (int i = 1 + skipLeading; employeesTried < maxEmployees; i++) {
            Object countObj = page.evaluate("() => {" + JS
                    + " const e=byNg('" + M + "employeeid'); return e? e.options.length : 0; }");
            int total = countObj == null ? 0 : Integer.parseInt(countObj.toString());
            if (i >= total) {
                System.out.println("SupervisorTracking: employee list exhausted at index " + i + " of " + total);
                break;
            }
            Object r = page.evaluate("(idx) => {" + JS
                    + " const e=byNg('" + M + "employeeid'); if(!e||idx>=e.options.length) return '';"
                    + " const o=e.options[idx]; if(!o.value || /^-*\\s*select/i.test(norm(o.textContent))) return '';"
                    + " e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                    + " return norm(o.textContent); }", i);
            String name = r == null ? "" : r.toString();
            if (name.isEmpty()) continue;
            employeesTried++;
            lastEmployee = name;
            waitForAngular(900);
            int rows = activitiesAssignedRowCount();
            if (rows > 0) {
                System.out.println("SupervisorTracking: employee \"" + name + "\" has " + rows + " Activities Assigned row(s)");
                return name;
            }
        }
        System.out.println("SupervisorTracking: no employee among the " + employeesTried + " tried had Activities Assigned data");
        return "";
    }

    /** Enter today's date (dd/MM/yyyy) into <b>From Date</b>. */
    public String enterFromDate() { return enterFromDate(todayStr()); }

    /** Enter the <b>From Date</b> ({@code SupervisorTracking.fromdate}). Not sent to the server — see class javadoc. */
    public String enterFromDate(String date) {
        Object r = page.evaluate("(v) => {" + JS + " return setInp('" + M + "fromdate', v); }", date);
        lastFromDate = r == null ? "" : r.toString();
        waitForAngular(200);
        System.out.println("SupervisorTracking: From Date = " + lastFromDate);
        return lastFromDate;
    }

    /** Enter today's date (dd/MM/yyyy) into <b>To Date</b>. */
    public String enterToDate() { return enterToDate(todayStr()); }

    /** Enter the <b>To Date</b> ({@code SupervisorTracking.todate}). Not sent to the server — see class javadoc. */
    public String enterToDate(String date) {
        Object r = page.evaluate("(v) => {" + JS + " return setInp('" + M + "todate', v); }", date);
        lastToDate = r == null ? "" : r.toString();
        waitForAngular(200);
        System.out.println("SupervisorTracking: To Date = " + lastToDate);
        return lastToDate;
    }

    private static String todayStr() {
        java.time.LocalDate today = java.time.LocalDate.now();
        return String.format("%02d/%02d/%04d", today.getDayOfMonth(), today.getMonthValue(), today.getYear());
    }

    // ---- Activities Assigned -------------------------------------------------

    /** Row count currently in the <b>Activities Assigned</b> grid. */
    public int activitiesAssignedRowCount() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf(tableByCaption('Activities Assigned')).length; }");
        return r == null ? 0 : Integer.parseInt(r.toString());
    }

    /** The <b>Activities Assigned</b> grid rows, as rendered text (one entry per row). */
    public List<String> activitiesAssignedRows() {
        Object r = page.evaluate("() => {" + JS + " return rowsOf(tableByCaption('Activities Assigned')); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) if (o != null) out.add(o.toString());
        return out;
    }

    // ---- Activity / Status / Remark / Change --------------------------------

    /** Select the <b>Activity</b> ({@code SupervisorTracking.activityid}). */
    public String selectActivity(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('" + M + "activityid', t); }", text);
        lastActivity = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("SupervisorTracking: Activity = " + lastActivity);
        return lastActivity;
    }

    /** Select the <b>Status</b> ({@code SupervisorTracking.activitystatus}). */
    public String selectStatus(String text) {
        Object r = page.evaluate("(t) => {" + JS + " return pick('" + M + "activitystatus', t); }", text);
        lastStatus = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("SupervisorTracking: Status = " + lastStatus);
        return lastStatus;
    }

    /** Enter the <b>Remark</b> ({@code SupervisorTracking.remarks}). */
    public String enterRemark(String text) {
        Object r = page.evaluate("(v) => {" + JS + " return setInp('" + M + "remarks', v); }", text);
        lastRemark = r == null ? "" : r.toString();
        waitForAngular(300);
        System.out.println("SupervisorTracking: Remark = " + lastRemark);
        return lastRemark;
    }

    /** Click <b>Change</b> ({@code Updatedata()}) and return the toast ("" if none). */
    public String clickChangeAndGetToast() {
        waitForToastsToClear();

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const c=[...document.querySelectorAll('button')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/Updatedata/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__stChange'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("SupervisorTracking.clickChange: Change button not found"); return ""; }

        try { page.locator("#__stChange").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("SupervisorTracking.clickChange: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__stChange'); if(e) e.removeAttribute('id'); }");
        waitForAngular(500);

        acceptSaveDialog();
        waitForAngular(400);

        try {
            page.waitForFunction("() => (window.__stToasts||[]).some(a=>/updated|saved|success|please|select|required|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__stToasts||[]).includes(t)) (window.__stToasts=window.__stToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__stToasts||[];"
                + " return a.find(x=>/updated\\s*success|saved\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("SupervisorTracking: a previous toast is still on screen — the next capture may be stale");
        }
    }
}
