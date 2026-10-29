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
 * Ancillary Services &gt; General Maintenance &gt; <b>Complaint Details</b> — Page Object (list {@code #/MainComplaintDetail}, form {@code #/addMainComplaintDetails}).
 *
 * <p>NOT the Equipment/Asset "Complaint Details" ({@code #/ComplaintDetails}, see {@code Equipment_page.ComplaintDetails}): this is the maintenance complaint of a DEPARTMENT, not of an equipment item.</p>
 *
 * <p>Confirmed live (devhis build v2026.10.29.330). The list ({@code ui-grid #grid1}: Complaint Code, Department Name, Entry Date, View, Print; filters Location / Department / Complaint Type / Status and a
 * <b>Search</b> button; data from {@code POST /api/MainComplaintDetails/fetch}) has a <b>New</b> button ({@code AddComplaint()}). The form (model {@code ComplaintDetails.*}) has the read-only
 * <b>Complaint No.</b> ({@code #newComplaintNo}, e.g. MCOMPNO-2), <b>Complaint Type*</b> ({@code #ComplaintType}, {@code .complainttype}), <b>Department*</b> ({@code #Department}, {@code .departmentid},
 * {@code GetMasterResult9()} on change) and a "Complaint Details" block: Entry / Problem / Reported dates and times (default to now, left alone), <b>Reported By*</b> ({@code .reportedby}),
 * <b>Reported To*</b> ({@code .reportedto}), <b>Status*</b> ({@code #newstatus}, {@code .complaintstatus}), <b>Verified By*</b> ({@code .verifiedby}), <b>Complaint Details*</b>
 * ({@code textarea #newComplaintDtls}) and an optional allocation (In house by default + Employee, left alone). <b>Add</b> ({@code AddDetails(linklist)}) puts the entry into the table at the top right
 * (Complaint Type, Problem Date, Reported By, Reported To, Status); <b>Save</b> ({@code IUDOperations()}) saves it; Back is {@code closeForm()}.</p>
 *
 * <p>Option facts at the time: Complaint Type has 36 generated "Auto complaint type …" names plus "burn" and "Damage"; Status is ONLY generated "Auto complaint status …" names; Department has 194
 * entries (real wards/departments); the three staff lists hold ~1,600 people, ~240 with real names. The choice rules are therefore: Complaint Type "Damage" (else the first non-generated one), Department
 * "MEDICAL WARD" (a maintenance complaint raised from a ward), staff = the first three real-named people (all different), Status = an "open/new/pending"-like name if one exists, else the
 * lowest-numbered one (the oldest = initial status).</p>
 *
 * <p>Saving is REAL: a maintenance complaint is created and stays (it can only be dealt with on the Complaint Resolution screen).</p>
 */
public class ComplaintDetails extends BasePage {

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
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public ComplaintDetails(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastComplaintNo = "", lastType = "", lastDepartment = "", lastReportedTo = "", lastVerifiedBy = "", lastReportedBy = "", lastStatus = "", lastDetails = "";
    public String lastAddedRow = "", lastToast = "", lastSaveMessage = "", lastDialog = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String M = "ComplaintDetails.";
    private static final String HREF = "#/maincomplaintdetail";
    private static final String PARENT = "Ancillary Services", GROUP = "General Maintenance";

    public static final String DEFAULT_TYPE = "Damage", DEFAULT_DEPARTMENT = "MEDICAL WARD";
    public static final String DEFAULT_DETAILS = "Water is leaking from the ceiling above the nurses' station; the ceiling panel is stained and sagging and needs inspection and repair.";

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

    /** Open/shut state of the group {@code label} looked up INSIDE the entry {@code parent}: 1 open, 0 shut, -1 not on screen. */
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
     * Ancillary Services &gt; General Maintenance &gt; Complaint Details. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still building closes the group
     * again, and a click on General Maintenance straight after Ancillary Services is lost). The link is found by its exact route, so the Equipment/Asset "Complaint Details" is never taken by mistake.
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
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__gmLink'; }", HREF);
                try { page.locator("#__gmLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("ComplaintDetails(GM).nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__gmLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        waitNewButton();
        waitForAngular(1000);
        return onList();
    }

    private void waitNewButton() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button,a')].some(x=>/^AddComplaint/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("ComplaintDetails(GM).nav: the New button is not ready"); }
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/maincomplaintdetail/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/addmaincomplaintdetails/?$")
                && Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"select[ng-model='" + M + "complainttype']\")"));
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    private static final String GRID =
            "const g=document.querySelector('.ui-grid'); const sc=g? angular.element(g).scope() : null; const go=sc? (sc.gridOptions || (sc.$parent && sc.$parent.gridOptions)) : null;";

    /** Every complaint the list holds, as {complaint no, department, entry date}; read from the grid's data array (columns found by their header). */
    public List<String[]> existing() {
        Object r = page.evaluate("() => { try{ " + GRID + " if(!go||!go.data) return []; const s=v=>v==null? '' : String(v);"
                + " const cd=go.columnDefs||[]; const f=(re,defs)=>{ const c=cd.find(c=>re.test(String(c.displayName||c.name||c.field||'').trim())); const k=c? (c.field||c.name) : null;"
                + "   return k && go.data.some(x=>x[k]!=null) ? k : (defs.find(d=>go.data.some(x=>x[d]!=null)) || k || defs[0]); };"
                + " const fc=f(/complaint code|complaint no/i,['complaintno']), fd=f(/department/i,['departmentname','department']), fe=f(/entry date/i,['entrydate']);"
                + " return go.data.map(x=>[s(x[fc]), s(x[fd]), s(x[fe])]); }catch(e){ return []; } }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2))}); }
        return out;
    }

    public boolean gridReady() {
        Object r = page.evaluate("() => { try{ " + GRID + " return !!go && Array.isArray(go.data); }catch(e){ return false; } }");
        return Boolean.TRUE.equals(r);
    }

    /** Read the rows the list holds: wait until the grid is ready, then up to 8 s more for rows (an empty answer after that is taken as an empty list). */
    public List<String[]> awaitExisting() {
        for (int i = 0; i < 80 && !gridReady(); i++) page.waitForTimeout(250);
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < 32 && rows.isEmpty(); i++) { rows = existing(); if (rows.isEmpty()) page.waitForTimeout(250); }
        return rows;
    }

    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    /** Click the list's Search button (it re-reads the list) and wait for the answer. */
    public void clickSearch() {
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^GetComplaintsdetails/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__gmSearch'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) return;
        try { page.locator("#__gmSearch").click(new Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
        page.evaluate("() => { const e=document.getElementById('__gmSearch'); if(e) e.removeAttribute('id'); }");
        waitForAngular(2500);
    }

    // ---- the form ----------------------------------------------------------------------------------------------

    /** Click New on the list and wait for the form with its dropdowns filled. */
    public boolean clickNew() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,a')].filter(vis).find(x=>/^AddComplaint/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__gmNew'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the New button is not on screen"); return false; }
        try { page.locator("#__gmNew").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the New button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__gmNew'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        if (!onForm()) { problems.add("the Complaint Details form did not open (" + page.url() + ")"); return false; }
        // the dropdowns are filled asynchronously: wait until Complaint Type, Department and the staff lists have options
        for (String ng : new String[]{"complainttype", "departmentid", "reportedby", "reportedto", "verifiedby", "complaintstatus"}) {
            for (int i = 0; i < 60 && options(M + ng).size() < 2; i++) page.waitForTimeout(250);
            if (options(M + ng).size() < 2) problems.add("the " + ng + " dropdown has no options");
        }
        waitForAngular(800);
        Object no = page.evaluate("() => { const e=document.getElementById('newComplaintNo'); return e? e.value : ''; }");
        lastComplaintNo = no == null ? "" : no.toString().trim();
        return ok();
    }

    /** The options of the dropdown with this ng-model as {text, value} (placeholder excluded). */
    @SuppressWarnings("unchecked")
    public List<String[]> options(String ng) {
        Object r = page.evaluate("(n) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return [];"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent))).map(o=>[norm(o.textContent), o.value]); }", ng);
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1))}); }
        return out;
    }

    /** Select the option with this exact text (ignoring case) and make angular/select2 see the change; returns the selected text, "" if it could not be selected. */
    private String choose(String ng, String text, String what) {
        Object r = page.evaluate("(a) => {" + JS + " const e=document.querySelector(\"select[ng-model='\"+a[0]+\"']\"); if(!e) return '';"
                + " const i=[...e.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()===a[1].toLowerCase()); if(i<0) return '';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " if(window.jQuery){ try{ jQuery(e).trigger('change'); }catch(x){} }"
                + " return norm(e.options[e.selectedIndex].textContent); }", Arrays.asList(ng, text));
        String got = r == null ? "" : r.toString();
        waitForAngular(600);
        if (got.isEmpty()) { problems.add("the " + what + " \"" + text + "\" could not be selected"); return ""; }
        Object model = page.evaluate("(n) => { const e=document.querySelector(\"select[ng-model='\"+n+\"']\"); if(!e) return ''; try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        if (model == null || model.toString().trim().isEmpty()) problems.add("the " + what + " is not held by the model after selecting \"" + got + "\"");
        return got;
    }

    private static boolean generated(String t) { return t.matches("(?i)^(auto|test)\\b.*"); }

    /** A real person's name: not a placeholder, test, generic "Employee n" or admin account. */
    private static boolean named(String t) { return !t.matches("(?i)^(test|employee|auto|sancy)\\b.*") && !t.toLowerCase().contains("admin"); }

    private String preferred(String ng, String wanted, boolean skipGenerated) {
        List<String[]> o = options(ng);
        for (String[] x : o) if (x[0].equalsIgnoreCase(wanted)) return x[0];
        if (skipGenerated) for (String[] x : o) if (!generated(x[0])) return x[0];
        return o.isEmpty() ? "" : o.get(0)[0];
    }

    public String selectComplaintType(String wanted) {
        problems.clear();
        lastType = choose(M + "complainttype", preferred(M + "complainttype", wanted, true), "Complaint Type");
        return lastType;
    }

    /** Department also fires GetMasterResult9(): give it time to refill whatever depends on it. */
    public String selectDepartment(String wanted) {
        problems.clear();
        lastDepartment = choose(M + "departmentid", preferred(M + "departmentid", wanted, false), "Department");
        waitForAngular(1500);
        page.waitForTimeout(1000);
        return lastDepartment;
    }

    /** The {@code n}-th (0-based) real-named person of the staff dropdown {@code ng}, skipping any name in {@code except}. */
    private String person(String ng, int n, List<String> except) {
        int seen = 0;
        for (String[] x : options(ng)) {
            if (!named(x[0]) || except.contains(x[0])) continue;
            if (seen++ == n) return x[0];
        }
        return "";
    }

    public String selectReportedTo() {
        problems.clear();
        String p = person(M + "reportedto", 1, List.of());
        if (p.isEmpty()) { problems.add("no real-named person is listed under Reported To"); return ""; }
        lastReportedTo = choose(M + "reportedto", p, "Reported To");
        return lastReportedTo;
    }

    public String selectVerifiedBy() {
        problems.clear();
        String p = person(M + "verifiedby", 2, List.of());
        if (p.isEmpty()) { problems.add("no real-named person is listed under Verified By"); return ""; }
        lastVerifiedBy = choose(M + "verifiedby", p, "Verified By");
        return lastVerifiedBy;
    }

    public String selectReportedBy() {
        problems.clear();
        String p = person(M + "reportedby", 0, List.of());
        if (p.isEmpty()) { problems.add("no real-named person is listed under Reported By"); return ""; }
        lastReportedBy = choose(M + "reportedby", p, "Reported By");
        return lastReportedBy;
    }

    /** An "open / new / pending"-like status if there is one, else the lowest-numbered (oldest) one. */
    public String selectStatus() {
        problems.clear();
        List<String[]> o = options(M + "complaintstatus");
        String pick = "";
        for (String[] x : o) if (x[0].matches("(?i).*\\b(open|new|pending|registered|raised|reported)\\b.*")) { pick = x[0]; break; }
        if (pick.isEmpty()) {
            long best = Long.MAX_VALUE;
            for (String[] x : o) {
                Matcher m = Pattern.compile("(\\d+)$").matcher(x[1]);
                long v = m.find() ? Long.parseLong(m.group(1)) : Long.MAX_VALUE;
                if (v < best) { best = v; pick = x[0]; }
            }
        }
        if (pick.isEmpty()) { problems.add("the Status dropdown has no options"); return ""; }
        lastStatus = choose(M + "complaintstatus", pick, "Status");
        return lastStatus;
    }

    /** Type the complaint text into the Complaint Details box. */
    public boolean enterDetails(String text) {
        problems.clear();
        Locator ta = page.locator("textarea[ng-model='" + M + "complaintdetails']").first();
        try {
            ta.scrollIntoViewIfNeeded();
            ta.click(new Locator.ClickOptions().setTimeout(5000));
            ta.fill("", new Locator.FillOptions().setTimeout(5000));
            ta.pressSequentially(text, new Locator.PressSequentiallyOptions().setDelay(8));
        } catch (Exception e) { problems.add("the Complaint Details could not be typed"); return false; }
        waitForAngular(400);
        String box = String.valueOf(page.evaluate("() => { const e=document.getElementById('newComplaintDtls'); return e? e.value : ''; }")).trim();
        if (!text.equals(box)) problems.add("the Complaint Details box shows \"" + box + "\" instead of the entered text");
        Object model = page.evaluate("(n) => { const e=document.querySelector(\"textarea[ng-model='\"+n+\"']\"); if(!e) return ''; try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", M + "complaintdetails");
        if (model == null || !text.equals(model.toString().trim())) problems.add("the Complaint Details are not held by the model");
        if (ok()) lastDetails = text;
        return ok();
    }

    // ---- Add ---------------------------------------------------------------------------------------------------

    /** The rows of the "Complaint Details" table at the top right (Complaint Type | Problem Date | Reported By | Reported To | Status), each as one text. */
    public List<String> addedRows() {
        Object r = page.evaluate("() => {" + JS + " const t=[...document.querySelectorAll('table')].find(x=>/Complaint Type/.test(x.textContent) && /Reported To/.test(x.textContent));"
                + " if(!t) return []; return [...t.querySelectorAll('tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>norm(td.textContent)).filter(Boolean).join(' | ')).filter(Boolean); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Click Add; it should put the entry into the Complaint Details table. */
    public boolean clickAdd() {
        problems.clear();
        lastAddedRow = "";
        List<String> before = addedRows();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^AddDetails/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__gmAdd'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add button is not on screen"); return false; }
        installToastWatch();
        try { page.locator("#__gmAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__gmAdd'); if(e) e.removeAttribute('id'); }"); }
        waitForAngular(1500);
        List<String> after = new ArrayList<>();
        for (int i = 0; i < 20; i++) { after = addedRows(); if (after.size() > before.size()) break; page.waitForTimeout(250); }
        if (after.size() <= before.size()) problems.add("Add put no row into the Complaint Details table" + (watchedToasts().isEmpty() ? "" : " (toast: \"" + watchedToasts() + "\")"));
        else lastAddedRow = after.get(after.size() - 1);
        return ok();
    }

    // ---- Save --------------------------------------------------------------------------------------------------

    private void installToastWatch() {
        page.evaluate("() => { document.querySelectorAll('#toast-container .toast').forEach(e=>e.remove());"
                + " window.__gmToasts=[]; if(window.__gmObs) window.__gmObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__gmToasts.includes(m)) window.__gmToasts.push(m); });"
                + " window.__gmObs=new MutationObserver(add); window.__gmObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__gmToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** If a confirmation dialog (any fixed overlay with a Save / Yes / OK / Confirm button) is on screen, click the button that is on top; returns its text, "" when none. */
    private String answerDialog() {
        Object t = page.evaluate("() => {" + JS
                + " const inOverlay=e=>{ for(let n=e.parentElement; n && n!==document.body; n=n.parentElement){ const p=getComputedStyle(n).position; if(p==='fixed') return true; } return false; };"
                + " const c=[...document.querySelectorAll('button,a.btn,input[type=button]')].filter(vis).filter(b=>/^\\s*(save|yes|ok|confirm|proceed)\\s*$/i.test(norm(b.textContent||b.value))"
                + "   && !/^(IUDOperations|AddDetails)/.test(b.getAttribute('ng-click')||'') && inOverlay(b));"
                + " const top=c.filter(b=>{ const r=b.getBoundingClientRect(); const el=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2); return el===b||b.contains(el); });"
                + " if(!top.length) return ''; const b=top[top.length-1]; b.id='__gmDlg'; return norm(b.textContent||b.value); }");
        String name = t == null ? "" : t.toString();
        if (name.isEmpty()) return "";
        try { page.locator("#__gmDlg").click(new Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("ComplaintDetails(GM).answerDialog: " + e.getMessage().split("\n")[0]); }
        page.evaluate("() => { const e=document.getElementById('__gmDlg'); if(e) e.removeAttribute('id'); }");
        return name;
    }

    /** Click Save, answer a confirmation if one appears, and wait for the toast. Returns the toast text ("" if none appeared). */
    public String saveAndGetToast() {
        problems.clear();
        lastToast = "";
        lastDialog = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/^IUDOperations/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__gmSave'; b.scrollIntoView({block:'center'}); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Save button is not on screen"); return ""; }
        try { page.locator("#__gmSave").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Save button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__gmSave'); if(e) e.removeAttribute('id'); }"); }
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

    /** After a successful save the screen should return to the list; if it stays on the form, click Back. */
    public boolean returnToList() {
        for (int i = 0; i < 32 && !onList(); i++) page.waitForTimeout(250);
        if (!onList()) {
            Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button,a')].filter(vis).find(x=>/^closeForm/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__gmBack'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__gmBack").click(new Locator.ClickOptions().setTimeout(6000)); } catch (Exception ignore) { }
                page.evaluate("() => { const e=document.getElementById('__gmBack'); if(e) e.removeAttribute('id'); }");
            }
            for (int i = 0; i < 32 && !onList(); i++) page.waitForTimeout(250);
        }
        waitNewButton();
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
        return sb.toString().trim();
    }

    /** The toast is a success message AND the server did not refuse the save. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("su(c)?cess|saved|added|created|submitted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ");
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
