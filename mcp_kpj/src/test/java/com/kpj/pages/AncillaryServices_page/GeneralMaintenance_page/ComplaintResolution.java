package com.kpj.pages.AncillaryServices_page.GeneralMaintenance_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ancillary Services &gt; General Maintenance &gt; <b>Complaint Resolution</b> — Page Object (screen {@code #/MainComplaintResolution}, resolution form {@code #/edit-MainComplaintResolution/<complaintId>/<locationId>}).
 *
 * <p>NOT the Equipment/Asset "Complaint Resolution" ({@code #/complaintresolution}, a read-only search screen): this resolves the General Maintenance complaints that the sibling Complaint Details screen creates.</p>
 *
 * <p>Confirmed live (devhis build v2026.10.29.330). The screen has filters (From/To Date = today, Status, Location, Department, In house / Service Agent, Employee, <b>Search</b> {@code GetComplaintGrid()}) and two
 * plain tables: <b>Complaint</b> on the left (Complaint No. | Complaint Date | Location Name | Department; one row per complaint, fetched by {@code POST /api/MainComplaintResolution/Fetch}) and
 * <b>Complaint Details</b> on the right (eye | Complaint Type | Problem Date | Reported Date | Reported By | Resolved Status | Complaint Details) which fills with the details of the complaint whose row is clicked.
 * The eye button ({@code editComplaintResolutionDetails(CMD)}) opens the <b>Complaint Resolution Detail</b> form (model {@code ComplaintResolutionDetails.*}): read-only <b>Resolve No.</b>
 * ({@code RESOLVENO-n}), <b>Complaint Resolution Date*</b> (today, left alone), <b>Resolved Status*</b> ({@code .resolvedstatusid}), <b>Completed By*</b> ({@code .completedby}), <b>Verified By*</b>
 * ({@code .verifiedby}), <b>Reported By*</b> ({@code .reportedby}), optional Work Done / Remarks (left alone), <b>Amount Incurred*</b> ({@code .amountincurred}, default 0), the allocation (In house by default)
 * with <b>Employee*</b> ({@code .employeeid}) and the <b>Down From</b> block: the <b>Down</b> tick ({@code .isdownfrom}), <b>Down Date*</b> ({@code .downdate}, text, placeholder yyyy-MM-dd) and <b>Down Time*</b>
 * (a time box with a clock button, {@code ng-model="inputTime"}). Buttons: <b>Save</b> and <b>Modify</b> (both {@code IUDComplaintResolutionDetails()}; Modify is disabled for a new resolution) and Cancel.</p>
 *
 * <p>Which complaint: the NEWEST one that is still unresolved (its details row has no Resolved Status), so a repeat run does not reopen the one it resolved before; if every complaint is resolved the newest is
 * used. The option choices mirror the Complaint Details test: the same three real-named people in their natural roles, and for Resolved Status (only generated "Auto complaint status …" names exist) the
 * highest-numbered one, which differs from the initial status the complaint was created with.</p>
 *
 * <p>Saving is REAL: the complaint is resolved (and its Resolved Status changes in the table).</p>
 */
public class ComplaintResolution extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/")
                    && !low.contains("getgriddata") && !low.contains("getlist") && !low.contains("getall") && !low.contains("getmaster");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) {
                if (write) { saveCalls.add(rec); String pd = r.request().postData(); lastSaveRequest = pd == null ? "" : pd; }
                if (bad) failedCalls.add(rec);
            }
        } catch (Exception ignore) { }
    };

    public ComplaintResolution(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastComplaintNo = "", lastResolveNo = "", lastCompletedBy = "", lastVerifiedBy = "", lastResolvedStatus = "", lastReportedBy = "", lastAmount = "", lastEmployee = "";
    public String lastDownDate = "", lastDownTime = "", lastResolutionDate = "", lastFormUrl = "", lastSaveButton = "";
    public String lastToast = "", lastSaveMessage = "", lastDialog = "";
    /** The body of the last save POST as the form sent it (for diagnosing a server refusal). */
    public volatile String lastSaveRequest = "";

    /** The date/time-looking fields of the last save request, e.g. {@code resolvedate=29/10/2026, downdate=29/10/2026, downtime=11:40 AM}; "" when none was captured. */
    public String saveRequestDates() {
        String b = lastSaveRequest;
        if (b == null || b.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("\"([A-Za-z_]*(?:date|time)[A-Za-z_]*)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|null|[0-9.]+)", Pattern.CASE_INSENSITIVE).matcher(b);
        while (m.find()) sb.append(sb.length() == 0 ? "" : ", ").append(m.group(1)).append('=').append(m.group(2));
        return sb.toString();
    }
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String M = "ComplaintResolutionDetails.";
    private static final String HREF = "#/maincomplaintresolution";
    private static final String PARENT = "Ancillary Services", GROUP = "General Maintenance";

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
     * Ancillary Services &gt; General Maintenance &gt; Complaint Resolution. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group
     * again, and a click on General Maintenance straight after Ancillary Services is lost). The link is found by its exact route, so the Equipment/Asset "Complaint Resolution" is never taken by mistake.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Ancillary Services')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__crLink'; }", HREF);
                try { page.locator("#__crLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("ComplaintResolution(GM).nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__crLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        waitComplaintTable();
        waitForAngular(1000);
        return onList();
    }

    private void waitComplaintTable() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('table')].some(t=>/Complaint No\\./.test(t.textContent) && /Department/.test(t.textContent) && t.offsetParent!==null)", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("ComplaintResolution(GM).nav: the Complaint table is not ready"); }
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/maincomplaintresolution/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/edit-maincomplaintresolution/\\d+/\\d+/?$")
                && Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"select[ng-model='" + M + "completedby']\")"));
    }

    // ---- the two tables --------------------------------------------------------------------------------------------

    /** The rows of the left "Complaint" table as {complaint no, date, location, department}. */
    public List<String[]> complaints() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>/Complaint No\\./.test(x.textContent) && /Department/.test(x.textContent) && !/Resolved Status/.test(x.textContent));"
                + " if(!t) return []; return [...t.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>norm(td.textContent))).filter(c=>c.length>=4 && c[0]); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; String[] a = new String[c.size()]; for (int i = 0; i < a.length; i++) a[i] = String.valueOf(c.get(i)); out.add(a); }
        return out;
    }

    public List<String[]> awaitComplaints() {
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < 60 && rows.isEmpty(); i++) { rows = complaints(); if (rows.isEmpty()) page.waitForTimeout(250); }
        return rows;
    }

    /** The rows of the right "Complaint Details" table as {type, problem date, reported date, reported by, resolved status, complaint details} (the eye column is dropped). */
    public List<String[]> detailRows() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>/Complaint Type/.test(x.textContent) && /Resolved Status/.test(x.textContent));"
                + " if(!t) return []; return [...t.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>norm(td.textContent)).slice(1)).filter(c=>c.length>=6 && (c[0]||c[5])); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; String[] a = new String[c.size()]; for (int i = 0; i < a.length; i++) a[i] = String.valueOf(c.get(i)); out.add(a); }
        return out;
    }

    /** Click the row of this complaint number in the Complaint table and wait for the details table to fill. Returns the detail rows (empty if nothing came). */
    public List<String[]> clickComplaint(String complaintNo) {
        problems.clear();
        Object tagged = page.evaluate("(n) => {" + JS + " const td=[...document.querySelectorAll('td')].filter(vis).find(x=>norm(x.textContent)===n); if(!td) return false; td.id='__crRow'; td.scrollIntoView({block:'center'}); return true; }", complaintNo);
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the row " + complaintNo + " is not in the Complaint table"); return List.of(); }
        List<String[]> before = detailRows();
        try { page.locator("#__crRow").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the row " + complaintNo + " could not be clicked"); return List.of(); }
        finally { page.evaluate("() => { const e=document.getElementById('__crRow'); if(e) e.removeAttribute('id'); }"); }
        lastComplaintNo = complaintNo;
        waitForAngular(1500);
        List<String[]> rows = List.of();
        for (int i = 0; i < 24; i++) {                                           // up to ~6 s for the detail rows to change/fill
            rows = detailRows();
            if (!rows.isEmpty() && (before.isEmpty() || !sameRows(before, rows) || i >= 6)) break;
            page.waitForTimeout(250);
        }
        return rows;
    }

    private static boolean sameRows(List<String[]> a, List<String[]> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!Arrays.equals(a.get(i), b.get(i))) return false;
        return true;
    }

    /** Is the row of this complaint number highlighted as the selected one? */
    public boolean rowSelected(String complaintNo) {
        Object r = page.evaluate("(n) => {" + JS + " const td=[...document.querySelectorAll('td')].filter(vis).find(x=>norm(x.textContent)===n); return !!td && /activeRow|selected/i.test(td.parentElement.className); }", complaintNo);
        return Boolean.TRUE.equals(r);
    }

    private static int numberOf(String complaintNo) {
        Matcher m = Pattern.compile("(\\d+)\\s*$").matcher(complaintNo);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /**
     * Choose the complaint to resolve: the NEWEST (highest complaint number) whose details row has no Resolved Status yet; if all are resolved, the newest. Clicks it and returns its detail rows; the chosen
     * number is in {@link #lastComplaintNo}. {@code triedNote} receives what was looked at.
     */
    public List<String[]> pickAndClickComplaint(List<String[]> complaintRows, StringBuilder triedNote) {
        List<String[]> sorted = new ArrayList<>(complaintRows);
        sorted.sort((x, y) -> Integer.compare(numberOf(y[0]), numberOf(x[0])));
        List<String[]> firstRows = null;
        String first = "";
        for (String[] c : sorted) {
            List<String[]> rows = clickComplaint(c[0]);
            if (firstRows == null) { firstRows = rows; first = c[0]; }
            boolean unresolved = !rows.isEmpty() && rows.get(0)[4].isEmpty();
            triedNote.append(triedNote.length() == 0 ? "" : "; ").append(c[0]).append(unresolved ? " unresolved" : rows.isEmpty() ? " no details" : " already resolved (" + rows.get(0)[4] + ")");
            if (unresolved) return rows;
        }
        if (firstRows == null) return List.of();
        return clickComplaint(first);                                            // every complaint is already resolved: take the newest
    }

    // ---- the eye -------------------------------------------------------------------------------------------------------

    /** Click the view (eye) symbol of the first detail row and wait for the Complaint Resolution Detail form with its dropdowns filled. */
    public boolean clickEye() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,a')].filter(vis).find(x=>/^editComplaintResolutionDetails/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__crEye'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the view (eye) symbol is not on screen"); return false; }
        try { page.locator("#__crEye").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the view (eye) symbol could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__crEye'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        if (!onForm()) { problems.add("the Complaint Resolution Detail form did not open (" + page.url() + ")"); return false; }
        lastFormUrl = page.url();
        for (String ng : new String[]{"completedby", "verifiedby", "resolvedstatusid", "reportedby", "employeeid"}) {
            for (int i = 0; i < 60 && options(M + ng).size() < 2; i++) page.waitForTimeout(250);
            if (options(M + ng).size() < 2) problems.add("the " + ng + " dropdown has no options");
        }
        waitForAngular(800);
        lastResolveNo = readValue("input[ng-model='" + M + "resolveno']");
        lastResolutionDate = readValue("input[ng-model='" + M + "resolvedate']");
        Object b = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('button')].filter(x=>/^IUDComplaintResolutionDetails/.test(x.getAttribute('ng-click')||'')).map(x=>norm(x.textContent)+(x.disabled?' (disabled)':'')).join(' / '); }");
        lastSaveButton = b == null ? "" : b.toString();
        return ok();
    }

    private String readValue(String selector) {
        Object v = page.evaluate("(s) => { const e=document.querySelector(s); return e? e.value : ''; }", selector);
        return v == null ? "" : v.toString().trim();
    }

    // ---- selects ---------------------------------------------------------------------------------------------------------

    /** The options of the dropdown with this ng-model as {text, value} (placeholder excluded). */
    @SuppressWarnings("unchecked")
    public List<String[]> options(String ng) {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent))).map(o=>[norm(o.textContent), o.value]); }", ng);
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    /** Select the option with this exact text (ignoring case) and make angular see the change; returns the selected text, "" if it could not be selected. */
    private String choose(String ng, String text, String what) {
        Object r = page.evaluate("(a) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+a[0]+\"']\"); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()===a[1].toLowerCase()); if(i<0) return '';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} }"
                + " return norm(e.options[e.selectedIndex].textContent); }", Arrays.asList(ng, text));
        String got = r == null ? "" : r.toString();
        waitForAngular(500);
        if (got.isEmpty()) { problems.add("the " + what + " \"" + text + "\" could not be selected"); return ""; }
        Object model = page.evaluate("(n) => { const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return ''; try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        if (model == null || model.toString().trim().isEmpty()) problems.add("the " + what + " is not held by the model after selecting \"" + got + "\"");
        return got;
    }

    /** A real person's name: not a placeholder, test, generic "Employee n" or admin account. */
    private static boolean named(String t) { return !t.matches("(?i)^(test|employee|auto|sancy)\\b.*") && !t.toLowerCase().contains("admin"); }

    /** The {@code n}-th (0-based) real-named person of the staff dropdown {@code ng}. */
    private String person(String ng, int n) {
        int seen = 0;
        for (String[] x : options(ng)) {
            if (!named(x[0])) continue;
            if (seen++ == n) return x[0];
        }
        return "";
    }

    private String choosePerson(String field, int n, String what) {
        problems.clear();
        String p = person(M + field, n);
        if (p.isEmpty()) { problems.add("no real-named person is listed under " + what); return ""; }
        return choose(M + field, p, what);
    }

    /** Completed By: the 2nd real-named person (the one the complaint was reported to in the Complaint Details test). */
    public String selectCompletedBy() { return lastCompletedBy = choosePerson("completedby", 1, "Completed By"); }

    /** Verified By: the 3rd real-named person (the verifier of the Complaint Details test). */
    public String selectVerifiedBy() { return lastVerifiedBy = choosePerson("verifiedby", 2, "Verified By"); }

    /** Reported By: the 1st real-named person (the reporter of the Complaint Details test). */
    public String selectReportedBy() { return lastReportedBy = choosePerson("reportedby", 0, "Reported By"); }

    /** Employee: the person who completed the work (the 2nd real-named person), the natural in-house assignee. */
    public String selectEmployee() { return lastEmployee = choosePerson("employeeid", 1, "Employee"); }

    /**
     * Resolved Status: a "resolved / closed / completed / done / fixed"-like name if there is one, else the highest-numbered option (all are generated "Auto complaint status …" names; the complaint was
     * created with the lowest-numbered one, so this one differs from it).
     */
    public String selectResolvedStatus() {
        problems.clear();
        List<String[]> o = options(M + "resolvedstatusid");
        String pick = "";
        for (String[] x : o) if (x[0].matches("(?i).*\\b(resolved|closed|completed|done|fixed)\\b.*")) { pick = x[0]; break; }
        if (pick.isEmpty()) {
            long best = -1;
            for (String[] x : o) {
                Matcher m = Pattern.compile("(\\d+)$").matcher(x[1]);
                long v = m.find() ? Long.parseLong(m.group(1)) : -1;
                if (v > best) { best = v; pick = x[0]; }
            }
        }
        if (pick.isEmpty()) { problems.add("the Resolved Status dropdown has no options"); return ""; }
        return lastResolvedStatus = choose(M + "resolvedstatusid", pick, "Resolved Status");
    }

    // ---- text boxes ------------------------------------------------------------------------------------------------------

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private boolean type(String selector, String ng, String value, String what) {
        Locator in = page.locator(selector).first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(30));
            in.press("Tab");
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
        waitForAngular(400);
        String box = readValue(selector);
        if (!value.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + value + "\"");
        if (ng != null && !value.equals(model(ng))) problems.add("the " + what + " is not held by the model (\"" + model(ng) + "\")");
        return ok();
    }

    public boolean enterAmountIncurred(String amount) {
        problems.clear();
        boolean ok = type("input[ng-model='" + M + "amountincurred']", M + "amountincurred", amount, "Amount Incurred");
        if (ok) lastAmount = amount;
        return ok;
    }

    /** Tick the Down box (it belongs to the Down From block that holds the date and time) if it is not ticked. */
    public boolean tickDown() {
        Object r = page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); if(!e) return 'none'; if(!e.checked) e.click(); return e.checked ? 'on' : 'off'; }", M + "isdownfrom");
        waitForAngular(500);
        return "on".equals(String.valueOf(r));
    }

    /**
     * The form's own Complaint Resolution Date (dd/MM/yyyy, i.e. today on the application's clock) for the Down Date; "" if unreadable. The Down Date box says "yyyy-MM-dd" as its placeholder but its date
     * picker really works in dd/MM/yyyy (and keeps only 10 characters), so a value typed or set in the placeholder's format is mangled ("20/26/").
     */
    public String todayForDownDate() {
        return Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$").matcher(lastResolutionDate).matches() ? lastResolutionDate : "";
    }

    /**
     * Open the date picker of the box {@code selector} and click the given day (dd/MM/yyyy) in the calendar, which must be showing that month; the picker discards typed characters, so this is the only way a value
     * sticks. Verifies the box and the model.
     */
    private boolean pickDate(String selector, String ng, String ddMMyyyy, String what) {
        Matcher m = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})$").matcher(ddMMyyyy);
        if (!m.matches()) { problems.add("the " + what + " \"" + ddMMyyyy + "\" is not a dd/MM/yyyy date"); return false; }
        int day = Integer.parseInt(m.group(1));
        try { page.locator(selector).first().click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the " + what + " box could not be clicked"); return false; }
        for (int i = 0; i < 20; i++) {
            Object open = page.evaluate("(s) => { const e=document.querySelector(s); const w=e && e.closest('.datepicker, datepicker'); return !!w && [...w.querySelectorAll('._720kb-datepicker-calendar-day')].some(x=>x.offsetWidth); }", selector);
            if (Boolean.TRUE.equals(open)) break;
            page.waitForTimeout(250);
        }
        Object tagged = page.evaluate("(a) => { const e=document.querySelector(a[0]); const w=e && e.closest('.datepicker, datepicker'); if(!w) return false;"
                + " const d=[...w.querySelectorAll('._720kb-datepicker-calendar-day')].filter(x=>x.offsetWidth && !/disabled/.test(x.className) && x.textContent.trim()===a[1]);"
                + " if(!d.length) return false; d[0].id='__crDay'; return true; }", Arrays.asList(selector, String.valueOf(day)));
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the day " + day + " is not offered by the " + what + " calendar"); return false; }
        try { page.locator("#__crDay").click(new Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { problems.add("the day " + day + " could not be clicked in the " + what + " calendar"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__crDay'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(600);
        String box = readValue(selector);
        if (!ddMMyyyy.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + ddMMyyyy + "\"");
        if (ng != null && !ddMMyyyy.equals(model(ng))) problems.add("the " + what + " is not held by the model (\"" + model(ng) + "\")");
        return ok();
    }

    /** The browser clock as hh:mm AM/PM, the format the time boxes of this application show. */
    public String nowAsTime() {
        Object r = page.evaluate("() => { const d=new Date(); let h=d.getHours(); const ap=h>=12?'PM':'AM'; h=h%12; if(h===0) h=12; return String(h).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0')+' '+ap; }");
        return String.valueOf(r);
    }

    /** Tick Down and enter the Down Date. */
    public boolean enterDownDate(String date) {
        problems.clear();
        if (!tickDown()) { problems.add("the Down box could not be ticked"); return false; }
        boolean ok = pickDate("input[ng-model='" + M + "downdate']", M + "downdate", date, "Down Date");
        if (ok) lastDownDate = date;
        return ok;
    }

    /** The browser clock as HH:mm (24 hour). */
    public String nowAs24h() {
        Object r = page.evaluate("() => { const d=new Date(); return String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0'); }");
        return String.valueOf(r);
    }

    /** dd/MM/yyyy as yyyy-MM-dd ("" if it is not a dd/MM/yyyy date). */
    public static String iso(String ddMMyyyy) {
        Matcher m = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})$").matcher(ddMMyyyy);
        return m.matches() ? m.group(3) + "-" + m.group(2) + "-" + m.group(1) : "";
    }

    /**
     * Put the Down Date straight into the model (e.g. yyyy-MM-dd), bypassing the picker, which would turn it into dd/MM/yyyy. Only the model is verified: the box's own mask may show it differently.
     * Used when the server refuses the date in the picker's format.
     */
    public boolean setDownDateModel(String value) {
        problems.clear();
        Object r = page.evaluate("(a) => { const e=document.querySelector(\"input[ng-model='\"+a[0]+\"']\"); if(!e) return 'none'; const s=angular.element(e).scope();"
                + " s.$apply(function(){ s.$eval(a[0]+\"='\"+a[1]+\"'\"); }); return String(s.$eval(a[0])); }", Arrays.asList(M + "downdate", value));
        waitForAngular(400);
        if (!value.equals(String.valueOf(r))) { problems.add("the Down Date model holds \"" + r + "\" instead of \"" + value + "\""); return false; }
        lastDownDate = value;
        return true;
    }

    /** True when the last save was refused by the server with a date/time conversion error (a date or time was sent in a format it cannot read). */
    public boolean refusedDateConversion() {
        synchronized (saveCalls) {
            for (String[] c : saveCalls) if (c[3].toLowerCase().contains("smalldatetime") || c[3].toLowerCase().contains("conversion of a nvarchar")) return true;
            for (String[] c : failedCalls) if (c[3].toLowerCase().contains("smalldatetime") || c[3].toLowerCase().contains("conversion of a nvarchar")) return true;
        }
        return false;
    }

    /** Enter the Down Time into the time box ({@code ng-model="inputTime"}, the only one on this form). */
    public boolean enterDownTime(String time) {
        problems.clear();
        boolean ok = type("input[ng-model='inputTime']", null, time, "Down Time");
        if (!ok) {                                                               // the picker may reformat typed text: set it through angular instead
            problems.clear();
            page.evaluate("(t) => { const e=document.querySelector(\"input[ng-model='inputTime']\"); const c=angular.element(e).controller('ngModel'); e.value=t; if(c){ c.$setViewValue(t); c.$render(); }"
                    + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", time);
            waitForAngular(400);
            if (!time.equals(readValue("input[ng-model='inputTime']"))) problems.add("the Down Time box shows \"" + readValue("input[ng-model='inputTime']") + "\" instead of \"" + time + "\"");
        }
        if (ok()) lastDownTime = time;
        return ok();
    }

    // ---- save ----------------------------------------------------------------------------------------------------------------

    private void installToastWatch() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__crToasts=[]; if(window.__crObs) window.__crObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__crToasts.includes(m)) window.__crToasts.push(m); });"
                + " window.__crObs=new MutationObserver(add); window.__crObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__crToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(IUDComplaintResolutionDetails|editComplaintResolutionDetails)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__crDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__crDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("ComplaintResolution(GM).answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__crDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Save (the enabled one; Modify only if Save is disabled), answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String saveAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).filter(x=>/^IUDComplaintResolutionDetails/.test(x.getAttribute('ng-click')||'') && !x.disabled);"
                + " const s=b.find(x=>/^save$/i.test(norm(x.textContent))) || b[0]; if(!s) return ''; s.id='__crSave'; s.scrollIntoView({block:'center'}); return norm(s.textContent); }");
        String name = tagged == null ? "" : tagged.toString();
        if (name.isEmpty()) { problems.add("no enabled Save button is on screen"); return ""; }
        lastSaveButton = name;
        try { page.locator("#__crSave").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the " + name + " button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__crSave'); if(e) e.removeAttribute('id'); }"); }
        long end = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < end) {
            String d = answerDialog();
            if (!d.isEmpty()) lastDialog = lastDialog.isEmpty() ? d : lastDialog + " > " + d;
            lastToast = watchedToasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(350);
        }
        page.waitForTimeout(800);
        lastToast = watchedToasts();
        parseSaveAnswer();
        return lastToast;
    }

    /** After a successful save the screen should return to the list; if it stays on the form, click Cancel. */
    public boolean returnToList() {
        for (int i = 0; i < 32 && !onList(); i++) page.waitForTimeout(250);
        if (!onList()) {
            Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,a')].filter(vis).find(x=>/^Cancel/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__crBack'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__crBack").click(new Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
                page.evaluate("() => { const e=document.getElementById('__crBack'); if(e) e.removeAttribute('id'); }");
            }
            for (int i = 0; i < 32 && !onList(); i++) page.waitForTimeout(250);
        }
        waitComplaintTable();
        waitForAngular(1000);
        return onList();
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
        String dates = saveRequestDates();
        if (!dates.isEmpty()) sb.append(" [request dates: ").append(dates).append("]");
        return sb.toString().trim();
    }

    /** The toast is a success message AND the server did not refuse the save. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        // the server's own spelling is "Save Succesfully" (sic), so accept success / sucess / succes... as well as saved / added / updated
        boolean toastOk = Pattern.compile("suc{1,2}es{1,2}|saved|added|updated|created|submitted|resolved").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
