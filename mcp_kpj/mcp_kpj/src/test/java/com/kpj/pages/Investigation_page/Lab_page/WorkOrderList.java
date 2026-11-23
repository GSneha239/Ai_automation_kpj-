package com.kpj.pages.Investigation_page.Lab_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Investigation &gt; Lab &gt; <b>Work Order List</b> — Page Object ({@code #/DoctorWorkOrderL}, header "Work Order List"; the Radiology twin is {@code #/DoctorWorkOrderR}).
 *
 * <p>Confirmed live in a Playwright probe (devhis build v2026.11.03.387). One screen:</p>
 * <ul>
 *   <li><b>Date Range</b> (ticked) with <b>From Date</b> / <b>To Date</b> ({@code #txtFromDate}, {@code #txtToDate}, {@code PathologyWorkOrder.FromDate} / {@code .ToDate}, dd/MM/yyyy, today by default);
 *       <b>MRN</b> box ({@code #textinput}) with the <b>Find Patient</b> button ({@code SearchPatientByMRNo()}), OPD / IPD, Priority, Location, Group (Laboratory), Sub Group, Order No, Payor,
 *       Department, Doctor, <b>Order Status</b> (Pending by default) and <b>Search</b> ({@code fnSearchorderbookingdetails(1)});</li>
 *   <li><b>Test Details</b> (ui-grid {@code gridOptions1}: Sel | Use Same Sample | Sample No | Sample Type | Order Date &amp; Time | Audit Trail | Patient Name | MRN No. | DOB | Service | Remark/Indication |
 *       Doctor | Payor | GL Status) — filled by Search through {@code POST /API/DoctorWorkOrder/fetch} (ExecFlag {@code GetServicesFromEMRForDoctorOrder}); the screen opens with today's Pending tests already in it;</li>
 *   <li><b>List of Orders</b> (ui-grid {@code gridOptions}: Order Date &amp; Time | MRN No. | Date of Birth | Mob No | Consent | Patient Name | Referral Number | Department | Consultant | Referring Dr | Location) —
 *       holds the chosen patient's order only after <b>Find Patient</b> ({@code GetPatientDetailsEMRForDoctorOrder}); a plain date-range Search leaves it empty;</li>
 *   <li>buttons <b>Appointment</b> ({@code fnGetPatientInfoforAppointment()} — with a Test Details record ticked it opens Book Appointment {@code #/New} for that patient and test; with none ticked nothing happens),
 *       <b>Accept</b> ({@code addsampleAccept()}), <b>Reject</b> ({@code openRejectRemark()}), <b>Print</b> ({@code PrintSampleBarcode()}) — Accept / Reject / Print are NOT used here.</li>
 * </ul>
 */
public class WorkOrderList extends BasePage {

    public WorkOrderList(Page page) { super(page); }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastFrom = "", lastTo = "";
    public int lastSearchStatus = 0, lastFindStatus = 0;

    private static final String FROM_NG = "PathologyWorkOrder.FromDate", TO_NG = "PathologyWorkOrder.ToDate", MRN_NG = "PathologyWorkOrder.MRNO";
    private static final String HREF = "#/doctorworkorderl";
    public static final String ORDERS = "gridOptions", TESTS = "gridOptions1";

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const grid=n=>document.querySelector(\"[ui-grid='\"+n+\"']\");"
          + "const data=n=>{ try{ return angular.element(grid(n)).scope().$eval(n+'.data')||[]; }catch(e){ return []; } };"
          + "const btn=re=>[...document.querySelectorAll('button')].filter(vis).find(x=>re.test(x.getAttribute('ng-click')||''));";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** Investigation &gt; Lab &gt; Work Order List — each level opened only if shut, the leaf looked up by its exact route inside the Investigation group (Radiology has a same-named leaf). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*investigation\\s*$");
            expandMenuEntry("^\\s*investigation\\s*$", "^\\s*lab\\s*$");
            Object tagged = page.evaluate("(h) => {" + JS
                    + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*investigation\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const root=top ? top.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__wolLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__wolLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("WorkOrderList.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__wolLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try {
            page.waitForFunction("() => !!document.getElementById('txtFromDate') && !!document.getElementById('txtToDate')", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("WorkOrderList.nav: the date boxes are not ready"); }
        // the screen fires its own opening search (today's Pending tests): let it land
        waitForAngular(2500);
        return onScreen();
    }

    /** On this screen — the route AND the Search button the screen carries, so a bounce to the dashboard cannot pass. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().matches(".*#/doctorworkorderl/?$")) return false;
        Object b = page.evaluate("() => [...document.querySelectorAll('button')].some(x=>/fnSearchorderbookingdetails[(]/.test(x.getAttribute('ng-click')||''))");
        return Boolean.TRUE.equals(b);
    }

    protected String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    /** The text of the option a native select currently holds (the select2 container in front of it shows the same). */
    public String chosen(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e || e.selectedIndex<0) return '';"
                + " return (e.options[e.selectedIndex].textContent||'').replace(/\\s+/g,' ').trim(); }", ng);
        return v == null ? "" : v.toString();
    }

    // ---- what the screen opens with ------------------------------------------------------------------------------

    /** Date Range ticked? */
    public boolean dateRangeTicked() {
        Object v = page.evaluate("() => { const e=document.getElementById('IsDateRange'); return !!e && e.checked; }");
        return Boolean.TRUE.equals(v);
    }

    public String group() { return chosen("PathologyWorkOrder.groupid"); }
    public String orderStatus() { return chosen("PathologyWorkOrder.isorderacceptreject"); }

    /** The labels of the action buttons the screen shows (Accept / Reject / Print) — read only, never clicked. */
    public List<String> actionButtons() {
        Object r = page.evaluate("() => {" + JS + " const out=[];"
                + " [['Accept',/addsampleAccept[(]/],['Reject',/openRejectRemark[(]/],['Print',/PrintSampleBarcode[(]/]].forEach(p=>{ if(btn(p[1])) out.push(p[0]); });"
                + " return out; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    // ---- dates -----------------------------------------------------------------------------------------------

    /** Enter the <b>From Date</b> and <b>To Date</b> (dd/MM/yyyy) — typed first, set through the box's own model when the keystrokes are discarded. */
    public String enterDateRange(String from, String to) {
        problems.clear();
        String a = typeDate("txtFromDate", FROM_NG, from);
        String b = typeDate("txtToDate", TO_NG, to);
        lastFrom = model(FROM_NG);
        lastTo = model(TO_NG);
        if (!from.equals(lastFrom)) problems.add("From Date holds \"" + lastFrom + "\" instead of " + from);
        if (!to.equals(lastTo)) problems.add("To Date holds \"" + lastTo + "\" instead of " + to);
        String r = "From Date " + a + " | To Date " + b;
        System.out.println("WorkOrderList: " + r);
        return r;
    }

    private String typeDate(String id, String ng, String value) {
        try {
            Locator box = page.locator("#" + id);
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.press("Control+a");
            box.press("Delete");
            box.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(90));
            page.evaluate("() => { const e=document.activeElement; if(e && e.blur) e.blur(); }");
        } catch (Exception e) { return "(typing failed: " + String.valueOf(e.getMessage()).split("\n")[0] + ")"; }
        waitForAngular(400);
        String via = "typed";
        if (!value.equals(model(ng))) {
            // The box is a picker box (class txtDate): when its typed keystrokes are discarded, set the value through the box's own model.
            page.evaluate("(a) => { const e=document.getElementById(a.id); if(!e) return; e.value=a.v;"
                    + " try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(a.v); c.$render(); } }catch(x){}"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true})); }",
                    Map.of("id", id, "v", value));
            waitForAngular(400);
            via = "set through the model";
        }
        dismissDatePicker();
        return value + " (" + via + ")";
    }

    // ---- search + Test Details ---------------------------------------------------------------------------------

    /** Click <b>Search</b>, wait for the answer ({@code DoctorWorkOrder/fetch}, GetServicesFromEMRForDoctorOrder) and return how many tests Test Details holds. */
    public int clickSearch() {
        problems.clear();
        lastSearchStatus = 0;
        Object tagged = page.evaluate("() => {" + JS + " const b=btn(/fnSearchorderbookingdetails[(]1[)]/);"
                + " if(!b) return false; b.scrollIntoView({block:'center'}); b.id='__wolSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Search button is not on screen"); return 0; }
        boolean answered = false;
        try {
            Response r = page.waitForResponse(x -> x.url().toLowerCase().contains("/api/doctorworkorder/fetch")
                            && String.valueOf(x.request().postData()).contains("GetServicesFromEMRForDoctorOrder"),
                    new Page.WaitForResponseOptions().setTimeout(30000), () -> page.locator("#__wolSearch").click(new Locator.ClickOptions().setTimeout(6000)));
            lastSearchStatus = r.status();
            answered = true;
            if (r.status() >= 400) problems.add("Search answered HTTP " + r.status());
        } catch (Exception e) {
            problems.add("Search did not get an answer from /API/DoctorWorkOrder/fetch: " + String.valueOf(e.getMessage()).split("\n")[0]);
        } finally {
            page.evaluate("() => { const e=document.getElementById('__wolSearch'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(1200);
        int n = rowCount(TESTS);
        for (int i = 0; answered && i < 10 && n == 0; i++) { page.waitForTimeout(300); n = rowCount(TESTS); }
        return n;
    }

    /** The records the grid holds (its own data list — the DOM only draws the rows that fit, and draws a pinned row twice). */
    public int rowCount(String grid) {
        Object n = page.evaluate("(g) => {" + JS + " return data(g).length; }", grid);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** The grid's column headers (the sort marker the grid appends is dropped). */
    public List<String> headers(String grid) {
        Object r = page.evaluate("(g) => {" + JS + " const e=grid(g); return e? [...e.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent).replace(/\\s+\\d+$/,'')).filter(Boolean) : []; }", grid);
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** One text list per record of the grid (its own data): MRN | order date | patient | service | status. */
    @SuppressWarnings("unchecked")
    public List<List<String>> records(String grid) {
        Object r = page.evaluate("(g) => {" + JS + " return data(g).map(x=>[String(x.mrno||'').trim(), String(x.date||'').trim(), norm(x.patientName||x.patientname||''),"
                + " norm(x.tariffservicename||x.serviceitemshortname||''), String(x.orderstatus1||'').trim()]); }", grid);
        List<List<String>> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) {
            List<String> cells = new ArrayList<>();
            if (o instanceof List) for (Object c : (List<Object>) o) cells.add(String.valueOf(c));
            out.add(cells);
        }
        return out;
    }

    /** The distinct MRNs of the grid's records, in listed order. */
    public List<String> mrns(String grid) {
        Set<String> s = new LinkedHashSet<>();
        for (List<String> r : records(grid)) if (!r.get(0).isEmpty()) s.add(r.get(0));
        return new ArrayList<>(s);
    }

    /** The records of the grid that belong to one MRN. */
    public int recordsOf(String grid, String mrn) {
        int n = 0;
        for (List<String> r : records(grid)) if (r.get(0).equals(mrn)) n++;
        return n;
    }

    /** The distinct order statuses of the grid's records (e.g. Pending). */
    public List<String> statuses(String grid) {
        Set<String> s = new LinkedHashSet<>();
        for (List<String> r : records(grid)) if (!r.get(4).isEmpty()) s.add(r.get(4));
        return new ArrayList<>(s);
    }

    /** "MRN x | Patient y | Service z | Ordered dd-MMM-yy HH:mm" for the first record of a grid — a readable sample for the report. */
    public String sample(String grid) {
        List<List<String>> rs = records(grid);
        if (rs.isEmpty()) return "";
        List<String> r = rs.get(0);
        return "MRN " + r.get(0) + " | " + r.get(2) + " | " + r.get(3) + " | ordered " + r.get(1) + " | " + r.get(4);
    }

    /**
     * The records of the grid whose order date ("03-Nov-26 09:47") is NOT inside {@code from}..{@code to} (dd/MM/yyyy, time ignored): the rows that break the date range the Search was asked for.
     * Empty = every record lies in the range.
     */
    public List<String> recordsOutsideRange(String grid, String from, String to) {
        List<String> bad = new ArrayList<>();
        java.time.format.DateTimeFormatter dmy = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.format.DateTimeFormatter shown = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yy", java.util.Locale.ENGLISH);
        java.time.LocalDate a = java.time.LocalDate.parse(from, dmy), b = java.time.LocalDate.parse(to, dmy);
        for (List<String> r : records(grid)) {
            String d = r.get(1);
            try {
                java.time.LocalDate day = java.time.LocalDate.parse(d.substring(0, 9), shown);
                if (day.isBefore(a) || day.isAfter(b)) bad.add("MRN " + r.get(0) + " " + d);
            } catch (Exception e) { bad.add("MRN " + r.get(0) + " \"" + d + "\" (not a date)"); }
        }
        return bad;
    }

    // ---- one patient --------------------------------------------------------------------------------------------

    /** Enter the MRN and press <b>Find Patient</b> (the magnifier beside the MRN box); returns how many orders List of Orders then holds. */
    public int findPatient(String mrn) {
        problems.clear();
        lastFindStatus = 0;
        page.evaluate("(a) => { const e=document.querySelector(\"[ng-model='\"+a.ng+\"']\"); if(!e) return; e.value=a.v;"
                + " try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(a.v); c.$render(); } }catch(x){}"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", Map.of("ng", MRN_NG, "v", mrn));
        waitForAngular(400);
        if (!mrn.equals(model(MRN_NG))) { problems.add("the MRN box holds \"" + model(MRN_NG) + "\" instead of " + mrn); return 0; }
        Object tagged = page.evaluate("() => {" + JS + " const b=btn(/SearchPatientByMRNo[(][)]/); if(!b) return false; b.id='__wolFind'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Find Patient button is not on screen"); return 0; }
        boolean answered = false;
        try {
            Response r = page.waitForResponse(x -> x.url().toLowerCase().contains("/api/doctorworkorder/fetch")
                            && String.valueOf(x.request().postData()).contains("GetPatientDetailsEMRForDoctorOrder"),
                    new Page.WaitForResponseOptions().setTimeout(30000), () -> page.locator("#__wolFind").click(new Locator.ClickOptions().setTimeout(6000)));
            lastFindStatus = r.status();
            answered = true;
            if (r.status() >= 400) problems.add("Find Patient answered HTTP " + r.status());
        } catch (Exception e) {
            problems.add("Find Patient got no order list from /API/DoctorWorkOrder/fetch: " + String.valueOf(e.getMessage()).split("\n")[0]);
        } finally {
            page.evaluate("() => { const e=document.getElementById('__wolFind'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(1500);
        int n = rowCount(ORDERS);
        for (int i = 0; answered && i < 10 && n == 0; i++) { page.waitForTimeout(300); n = rowCount(ORDERS); }
        return n;
    }

    // ---- Appointment ---------------------------------------------------------------------------------------------

    /**
     * Tick the row-select symbol of every Test Details record of the MRN (the tick sits in the grid's pinned left column, so it is pressed with a real mouse click) and return how many of the
     * patient's records are then selected.
     */
    public int selectPatient(String mrn) {
        problems.clear();
        Object ticks = page.evaluate("(m) => {" + JS + " const out=[]; const g=grid('" + TESTS + "'); if(!g) return out;"
                + " [...g.querySelectorAll('[ng-click*=selectButtonClick]')].forEach(b=>{ try{ const row=angular.element(b).scope().row;"
                + "   if(row && String(row.entity.mrno||'').trim()===m){ b.scrollIntoView({block:'center'}); const q=b.getBoundingClientRect(); out.push({x:q.left+q.width/2,y:q.top+q.height/2,sel:!!row.isSelected}); } }catch(e){} });"
                + " return out; }", mrn);
        if (!(ticks instanceof List) || ((List<?>) ticks).isEmpty()) { problems.add("no tick symbol found in Test Details for MRN " + mrn); return 0; }
        for (Object o : (List<?>) ticks) {
            Map<?, ?> t = (Map<?, ?>) o;
            if (Boolean.TRUE.equals(t.get("sel"))) continue;
            page.mouse().click(((Number) t.get("x")).doubleValue(), ((Number) t.get("y")).doubleValue());
            waitForAngular(600);
        }
        Object n = page.evaluate("(m) => {" + JS + " const g=grid('" + TESTS + "'); if(!g) return 0; let c=0;"
                + " [...g.querySelectorAll('[ng-click*=selectButtonClick]')].forEach(b=>{ try{ const row=angular.element(b).scope().row; if(row && String(row.entity.mrno||'').trim()===m && row.isSelected) c++; }catch(e){} });"
                + " return c; }", mrn);
        int sel = n instanceof Number ? ((Number) n).intValue() : 0;
        if (sel == 0) problems.add("the tick on the MRN " + mrn + " record did not select it");
        return sel;
    }

    /**
     * Click <b>Appointment</b> ({@code fnGetPatientInfoforAppointment()}) and wait for the Book Appointment form ({@code #/New}) to open with the selected patient loaded (MRN and Name filled).
     * Nothing happens on the screen when no record is ticked.
     */
    public boolean clickAppointment(String mrn) {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=btn(/fnGetPatientInfoforAppointment[(][)]/); if(!b) return false; b.scrollIntoView({block:'center'}); b.id='__wolApp'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Appointment button is not on screen"); return false; }
        try { page.locator("#__wolApp").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Appointment button could not be clicked: " + String.valueOf(e.getMessage()).split("\n")[0]); return false; }
        try {
            page.waitForFunction("(m) => /#[/]new[/]?$/i.test(location.href) && ((document.getElementById('txtMRNo')||{}).value||'').trim()===m"
                            + " && ((document.getElementById('txtFirstName')||{}).value||'').trim().length>0",
                    mrn, new Page.WaitForFunctionOptions().setTimeout(40000));
        } catch (Exception e) {
            problems.add("the Book Appointment form did not open with MRN " + mrn + " loaded (the app is on " + page.url() + ")");
            return false;
        }
        waitForAngular(1500);
        return true;
    }

    /**
     * Start recording every toast / dialog the app puts on screen from now on (the app is a single page, so the recorder survives the move to Book Appointment): one entry per message,
     * "success toast: text" / "error toast: text" / "dialog: text", read back with {@link #messagesSeen()}. A toast lives only a few seconds and the Save sequence outlasts it, so the screen alone cannot be read afterwards.
     */
    public void watchMessages() {
        page.evaluate("() => { window.__wolMsgs = []; if (window.__wolObs) window.__wolObs.disconnect();"
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const kind=x=>{ if(!x.matches('.toast')) return 'dialog'; const m=/toast-(success|error|warning|info)/.exec((x.className||'').toString()); return m ? m[1]+' toast' : 'toast'; };"
                + " const body=x=>{ const m=x.matches('.toast') ? x.querySelector('.toast-message') : null; return norm(m ? m.textContent : x.textContent).replace(/^[^A-Za-z0-9]+/,''); };"
                + " const take=n=>{ if(!n || n.nodeType!==1) return; const hit=[]; if(n.matches && n.matches('.toast,.ng-confirm-box,.jconfirm-box,.modal-content')) hit.push(n);"
                + "   if(n.querySelectorAll) n.querySelectorAll('.toast,.ng-confirm-box,.jconfirm-box,.modal-content').forEach(x=>hit.push(x));"
                + "   hit.forEach(x=>{ const t=body(x); if(t) window.__wolMsgs.push(kind(x)+': '+t.slice(0,200)); }); };"
                + " window.__wolObs=new MutationObserver(ms=>ms.forEach(m=>{ m.addedNodes.forEach(take); if(m.type==='characterData' && m.target.parentElement) take(m.target.parentElement.closest('.toast')); }));"
                + " window.__wolObs.observe(document.body,{childList:true,subtree:true,characterData:true}); }");
    }

    /** What {@link #watchMessages()} recorded, each message once, in the order it appeared. */
    @SuppressWarnings("unchecked")
    public List<String> messagesSeen() {
        Object r = page.evaluate("() => [...new Set(window.__wolMsgs||[])]");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The value a Book Appointment form box holds (by id), blank when it is not there. */
    public String formValue(String id) {
        Object v = page.evaluate("(i) => { const e=document.getElementById(i); return e ? String(e.value||'').trim() : ''; }", id);
        return v == null ? "" : v.toString();
    }

    /** The value of the Book Appointment form's Service Name box (the ordered test the appointment is for). */
    public String serviceName() {
        Object v = page.evaluate("() => { const e=document.querySelector(\"[ng-model='PatientData.ServiceName']\"); return e ? String(e.value||'').trim() : ''; }");
        return v == null ? "" : v.toString();
    }

    /** Open the <b>List of Orders</b> panel (it stays collapsed after Find Patient) and return how many rows its grid draws on screen. */
    public int showOrders() {
        page.evaluate("() => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>/fnSearchorderbooking[(][)]/.test(x.getAttribute('ng-click')||''));"
                + " if(a && /collapsed/.test(a.className)) a.click(); }");
        waitForAngular(1200);
        Object n = page.evaluate("() => {" + JS + " const g=grid('" + ORDERS + "'); return vis(g) ? g.querySelectorAll('.ui-grid-row').length : 0; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** The MRN column of every record of List of Orders (the patient's orders). */
    public List<String> orderMrns() {
        Object r = page.evaluate("() => {" + JS + " return data('" + ORDERS + "').map(x=>String(x.mrno||'').trim()).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }
}
