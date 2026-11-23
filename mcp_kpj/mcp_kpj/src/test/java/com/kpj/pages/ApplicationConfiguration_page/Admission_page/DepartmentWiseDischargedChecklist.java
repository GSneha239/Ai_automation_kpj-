package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application Configuration &gt; Admission &gt; <b>Department Wise Discharged Checklist</b> — Page Object ({@code #/DepartmentDischargeChecklistMaster}, form {@code #/add-DepartmentDischargeChecklist}).
 *
 * <p>Confirmed live (devhis build v2026.10.27.303). The list is a ui-grid ({@code #grid1}: Code, Department, Remark, Status, Edit; EMPTY at the time) with an <b>Add</b> link
 * ({@code AddDepartmentDischargeChecklist()}). The form ({@code DepartmentDischargeChecklist.*}) has <b>Code*</b> ({@code .code}), <b>Department*</b> ({@code .departmentid}, a select2 over 188
 * departments), <b>Remark*</b> ({@code .remark}) and the buttons <b>Submit</b> ({@code fnIUDDepartmentDischargeChecklist()}) and Back.</p>
 *
 * <p>The code and remark must be new: the existing rows are read from the grid's data array (ALL rows, not only the current page) and the new values are chosen so that neither collides with one of them
 * ignoring case. The remark is written for the department chosen (for example Billing: "Final bill settled or guarantee letter confirmed"). Saving is REAL: a checklist item is added to the master and
 * stays (it can only be made inactive with its Status switch, which this page never touches).</p>
 */
public class DepartmentWiseDischargedChecklist extends BasePage {

    private final List<String[]> saveCalls = new ArrayList<>();
    private final List<String[]> failedCalls = new ArrayList<>();
    private final Consumer<Response> listener = r -> {
        try {
            String u = r.url(), low = u.toLowerCase();
            if (!low.contains("/api/")) return;
            boolean write = "POST".equalsIgnoreCase(r.request().method()) && !low.contains("fetch") && !low.contains("commondropdown") && !low.contains("getinternalmsg") && !low.contains("/auth/");
            boolean bad = r.status() >= 400;
            if (!write && !bad) return;
            String body = "";
            try { body = r.text(); } catch (Exception ignore) { }
            String[] rec = {String.valueOf(r.status()), r.request().method(), URLDecoder.decode(u, StandardCharsets.UTF_8), body.length() > 800 ? body.substring(0, 800) : body};
            synchronized (saveCalls) { if (write) saveCalls.add(rec); if (bad) failedCalls.add(rec); }
        } catch (Exception ignore) { }
    };

    public DepartmentWiseDischargedChecklist(Page page) {
        super(page);
        page.onResponse(listener);
    }

    /** What the last step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    public String lastCode = "", lastDepartment = "", lastRemark = "", lastToast = "", lastSaveMessage = "";
    public int lastSaveStatus = -1;

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String CODE_NG = "DepartmentDischargeChecklist.code", DEPT_NG = "DepartmentDischargeChecklist.departmentid", REMARK_NG = "DepartmentDischargeChecklist.remark";
    private static final String HREF = "#/departmentdischargechecklistmaster";

    /** Departments to choose from, in order of preference, each with real discharge-clearance remarks written for it (the master keeps whatever is saved, so no "Auto…" junk). */
    private static final String[][] DEPARTMENTS = {
            {"BILLING DEPARTMENT", "Final bill settled or guarantee letter confirmed", "Outstanding deposit refunded or adjusted against the final bill"},
            {"NURSING DEPARTMENT", "Discharge instructions and follow-up date explained to the patient", "Patient's belongings returned and wristband removed"},
            {"MEDICAL RECORD", "Discharge summary completed and filed in the patient record", "Medical certificate and discharge documents issued"},
            {"DIETICIAN", "Diet advice for home given to the patient", "Special diet plan explained to the patient and carer"},
            {"PHARMACY", "Discharge medications dispensed and counselling given", "Medication list reconciled and explained at discharge"}
    };
    /** Used when none of the preferred departments exists (the first real option is then taken). */
    private static final String[] GENERIC_REMARKS = {"Departmental clearance completed before discharge", "All outstanding department requests closed before discharge"};

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

    /** Is the menu group with this label expanded? {@code 1} open, {@code 0} shut, {@code -1} not on screen. */
    private int groupState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickGroup(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    /**
     * Application Configuration &gt; Admission &gt; Department Wise Discharged Checklist. Each group is opened ONCE and awaited (the menu builds each level lazily; a second click while it is still
     * building closes the group again, and a click on Admission straight after Application Configuration is lost), so the first group gets a settling pause before the next one is clicked.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='Application Configuration')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onList(); attempt++) {
            if (!linkVisible()) {
                if (groupState("Admission") < 0) {                              // Admission is not on screen: Application Configuration is shut
                    if (groupState("Application Configuration") != 1) clickGroup("Application Configuration");
                    for (int i = 0; i < 48 && groupState("Admission") < 0; i++) page.waitForTimeout(250);
                    page.waitForTimeout(3000);
                }
                if (groupState("Admission") == 0 && !linkVisible()) clickGroup("Admission");
                waitLink(12000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__dwdLink'; }", HREF);
                try { page.locator("#__dwdLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("DepartmentWiseDischargedChecklist.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__dwdLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => !![...document.querySelectorAll('a')].find(b=>/AddDepartmentDischargeChecklist/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("DepartmentWiseDischargedChecklist.nav: the Add link is not ready"); }
        waitForAngular(1000);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/departmentdischargechecklistmaster/?$"); }

    public boolean onForm() {
        return page.url().toLowerCase().matches(".*#/add-departmentdischargechecklist/?$")
                && Boolean.TRUE.equals(page.evaluate("(n) => {" + JS + " return !![...document.querySelectorAll(\"[ng-model='\"+n+\"']\")].find(vis); }", CODE_NG));
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    /**
     * Every row the list holds (all pages), as {code, remark, status, department}; read from the grid's data array. The row's own field names are tolerated in turn ({@code remark} / {@code description};
     * {@code departmentname} / {@code department} / {@code Department}) because the screen was empty when it was inspected. Empty when the grid has none.
     */
    public List<String[]> existing() {
        Object r = page.evaluate("() => { try{ const g=document.getElementById('grid1'); const d=angular.element(g).scope().gridOptions.data; const s=v=>v==null? '' : String(v);"
                + " return d.map(x=>[s(x.code), s(x.remark!=null? x.remark : x.description), s(x.Status), s(x.departmentname!=null? x.departmentname : (x.department!=null? x.department : x.Department))]); }catch(e){ return []; } }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2)), String.valueOf(c.get(3))}); }
        return out;
    }

    /** True once the list's grid exists with a data array (it can be empty). */
    public boolean gridReady() {
        Object r = page.evaluate("() => { try{ const g=document.getElementById('grid1'); return Array.isArray(angular.element(g).scope().gridOptions.data); }catch(e){ return false; } }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Read the rows the list holds. The grid can legitimately be EMPTY, so rows are waited for only for a limited time: until the grid is ready, then up to 8 s more for rows to arrive; an empty answer
     * after that is taken as "the table is empty".
     */
    public List<String[]> awaitExisting() {
        for (int i = 0; i < 80 && !gridReady(); i++) page.waitForTimeout(250);
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < 32 && rows.isEmpty(); i++) { rows = existing(); if (rows.isEmpty()) page.waitForTimeout(250); }
        return rows;
    }

    /** The pager text ("1 - 5 of 12 items"), "" when there is none. */
    public String pagerText() {
        Object r = page.evaluate("() => { const m=(document.body.innerText||'').match(/\\d+\\s*-\\s*\\d+\\s*of\\s*\\d+\\s*items?/i); return m? m[0].replace(/\\s+/g,' ') : ''; }");
        return r == null ? "" : r.toString();
    }

    private static boolean taken(List<String[]> rows, String code, String remark) {
        for (String[] r : rows) {
            if (!code.isEmpty() && r[0].trim().equalsIgnoreCase(code.trim())) return true;
            if (!remark.isEmpty() && r[1].trim().equalsIgnoreCase(remark.trim())) return true;
        }
        return false;
    }

    /** Choose a Code that collides with no existing row, ignoring case: {@code DDC} plus three digits, stepped until unused (shifted by {@code skip} for a retry). Sets {@link #lastCode}. */
    public String pickCode(List<String[]> rows, int skip) {
        String code = "";
        int seen = 0;
        for (int n = 1; n < 1000 && code.isEmpty(); n++) {
            String cand = "DDC" + String.format("%03d", n);
            if (taken(rows, cand, "")) continue;
            if (seen++ < skip) continue;
            code = cand;
        }
        lastCode = code;
        return code;
    }

    /**
     * Choose a Remark written for {@code department} that collides with no existing row, ignoring case: the department's own remarks first (shifted by {@code skip} for a retry), then the generic
     * ones, then a numeral is appended. Sets {@link #lastRemark}.
     */
    public String pickRemark(List<String[]> rows, String department, int skip) {
        List<String> pool = new ArrayList<>();
        for (String[] d : DEPARTMENTS) if (d[0].equalsIgnoreCase(department.trim())) for (int i = 1; i < d.length; i++) pool.add(d[i]);
        for (String g : GENERIC_REMARKS) pool.add(g);
        String remark = "";
        int seen = 0;
        for (int round = 0; round < 50 && remark.isEmpty(); round++) {
            for (String base : pool) {
                String cand = round == 0 ? base : base + " (" + (round + 1) + ")";
                if (taken(rows, "", cand)) continue;
                if (seen++ < skip) continue;
                remark = cand;
                break;
            }
        }
        lastRemark = remark;
        return remark;
    }

    // ---- add form ----------------------------------------------------------------------------------------------

    /** Click Add on the list and wait for the form. */
    public boolean clickAdd() {
        problems.clear();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('a,button')].filter(vis).find(x=>/AddDepartmentDischargeChecklist/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__dwdAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Add button is not on screen"); return false; }
        try { page.locator("#__dwdAdd").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Add button could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__dwdAdd'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80 && !onForm(); i++) page.waitForTimeout(250);
        waitForAngular(1000);
        if (!onForm()) problems.add("the Department Wise Discharged Checklist form did not open (" + page.url() + ")");
        return ok();
    }

    private String model(String ng) {
        Object v = page.evaluate("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(n); return v==null?'':String(v); }catch(x){ return ''; } }", ng);
        return v == null ? "" : v.toString().trim();
    }

    private boolean type(String ng, String value, String what) {
        Locator in = page.locator("input[ng-model='" + ng + "']").first();
        try {
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill("", new Locator.FillOptions().setTimeout(5000));
            in.pressSequentially(value, new Locator.PressSequentiallyOptions().setDelay(25));
        } catch (Exception e) { problems.add("the " + what + " could not be typed"); return false; }
        waitForAngular(400);
        String box = String.valueOf(page.evaluate("(n) => { const e=document.querySelector(\"input[ng-model='\"+n+\"']\"); return e? e.value : ''; }", ng)).trim();
        if (!value.equals(box)) problems.add("the " + what + " box shows \"" + box + "\" instead of \"" + value + "\"");
        if (!value.equals(model(ng))) problems.add("the " + what + " is not held by the model");
        return ok();
    }

    public boolean enterCode(String code) { problems.clear(); return type(CODE_NG, code, "Code"); }

    public boolean enterRemark(String remark) { problems.clear(); return type(REMARK_NG, remark, "Remark"); }

    /**
     * Select the Department: the first of {@code DEPARTMENTS}' names (in the order of preference) that the list holds — matched ignoring case and spaces, exact first, then by containing — else the
     * first real option. Returns the option chosen ({@link #lastDepartment}), "" with a problem when the list has none.
     */
    public String selectDepartment() {
        problems.clear();
        try { page.waitForFunction("(n) => { const e=document.querySelector(\"[ng-model='\"+n+\"']\"); return e && e.options.length>1; }", DEPT_NG, new Page.WaitForFunctionOptions().setTimeout(10000)); }
        catch (Exception e) { problems.add("the Department list has no options"); return ""; }
        List<String> wants = new ArrayList<>();
        for (String[] d : DEPARTMENTS) wants.add(d[0]);
        Object t = page.evaluate("(a) => {" + JS
                + " const e=[...document.querySelectorAll(\"[ng-model='\"+a.n+\"']\")].find(vis)||document.querySelector(\"[ng-model='\"+a.n+\"']\"); if(!e) return '';"
                + " const k=s=>norm(s).toLowerCase().replace(/\\s+/g,'');"
                + " const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value,i:x.index})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=null; for(const w of a.w){ p=o.find(x=>k(x.t)===k(w)); if(p) break; }"
                + " if(!p) for(const w of a.w){ p=o.find(x=>k(x.t).indexOf(k(w))>=0); if(p) break; }"
                + " if(!p) p=o[0]; if(!p) return '';"
                + " e.selectedIndex=p.i; e.dispatchEvent(new Event('change',{bubbles:true})); try{ angular.element(e).triggerHandler('change'); }catch(x){}"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', e.value); }catch(x){} try{ $(e).trigger('change'); }catch(x){} } return p.t; }", Map.of("n", DEPT_NG, "w", wants));
        lastDepartment = t == null ? "" : t.toString();
        if (lastDepartment.isEmpty()) { problems.add("no Department could be selected"); return ""; }
        waitForAngular(800);
        String held = model(DEPT_NG);
        if (held.isEmpty() || "0".equals(held)) problems.add("the Department is not held by the model");
        return ok() ? lastDepartment : "";
    }

    // ---- submit ------------------------------------------------------------------------------------------------

    public String toastsNow() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('#toast-container .toast, .toast-message')].filter(vis)"
                + "   .map(t=>norm(t.textContent).replace(/^[^A-Za-z0-9]+/,'').replace(/^KPJ\\s*Portal\\s*/,'')).filter(Boolean).join(' | '); }");
        return r == null ? "" : r.toString();
    }

    private void installToastWatch() {
        page.evaluate("() => { window.__dwdToasts=[]; if(window.__dwdObs) window.__dwdObs.disconnect();"
                + " const add=()=>document.querySelectorAll('.toast-message').forEach(e=>{ const m=(e.textContent||'').replace(/\\s+/g,' ').trim(); if(m && !window.__dwdToasts.includes(m)) window.__dwdToasts.push(m); });"
                + " window.__dwdObs=new MutationObserver(add); window.__dwdObs.observe(document.body,{childList:true,subtree:true,characterData:true}); add(); }");
    }

    private String watchedToasts() {
        Object r = page.evaluate("() => (window.__dwdToasts||[]).join(' | ')");
        return r == null ? "" : r.toString();
    }

    /** Click Submit and wait for the toast. Returns the toast text ("" if none appeared); the server's answer is in {@link #lastSaveStatus} / {@link #lastSaveMessage}. */
    public String submitAndGetToast() {
        problems.clear();
        lastToast = "";
        synchronized (saveCalls) { saveCalls.clear(); failedCalls.clear(); }
        installToastWatch();
        Object tagged = page.evaluate("() => {" + JS + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/fnIUDDepartmentDischargeChecklist/.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__dwdSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { problems.add("the Submit button is not on screen"); return ""; }
        try { page.locator("#__dwdSubmit").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the Submit button could not be clicked"); return ""; }
        finally { page.evaluate("() => { const e=document.getElementById('__dwdSubmit'); if(e) e.removeAttribute('id'); }"); }
        for (int i = 0; i < 80; i++) {                                           // up to ~20 s for the toast
            lastToast = watchedToasts();
            if (!lastToast.isEmpty()) break;
            page.waitForTimeout(250);
        }
        page.waitForTimeout(800);
        lastToast = watchedToasts();
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

    /** The screen refused because the code or remark already exists. */
    public boolean rejectedAsDuplicate() {
        String t = (lastToast + " " + lastSaveMessage).toLowerCase();
        return t.contains("already") || t.contains("exist") || t.contains("duplicate");
    }

    /** The toast is a success message AND the server did not refuse the save. */
    public boolean saveSucceeded() {
        String t = lastToast.toLowerCase();
        boolean toastOk = Pattern.compile("su(c)?cess|saved|added|created|submitted").matcher(t).find() && !t.contains("error") && !t.contains("fail") && !t.contains("please") && !t.contains("not ")
                && !rejectedAsDuplicate();
        return toastOk && (lastSaveStatus == 1 || lastSaveStatus == -1);
    }
}
