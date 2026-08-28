package com.kpj.pages.AncillaryServices_page.DoctorRoster_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Doctor Roster &gt; <b>MO Roster</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Doctor Roster</b> → <b>MO Roster</b> ({@code #/MORoster}, a
 * filter/list screen) → <b>NewRoster</b> ({@code fnGoToNewRoster()}) → the roster form
 * ({@code #/add-MORoster}) → <b>From Date</b> / <b>To Date</b> → <b>Department</b> / <b>Shift</b> /
 * <b>Medical Officer</b> → <b>Add</b> ({@code fnAddRow()}) → <b>Save</b> ({@code fnSaveRoster()}) → toast.</p>
 *
 * <p><b>Fields (discovered live 2026-08-06):</b> {@code form.FromDate} / {@code form.ToDate} (native
 * {@code type=date}), {@code form.departmentid} (176 options), {@code form.shiftid} (10 options), plus a
 * <b>ui-select</b> typeahead for the Medical Officer. Buttons: {@code fnAddRow()} (Add),
 * {@code fnSaveRoster()} (Save), {@code fnClear()} (Clear), {@code fnBack()} (Back).</p>
 *
 * <p><b>Related but NOT the same as {@link ConsultantOnCall}.</b> Both share the roster handler names
 * ({@code fnGoToNewRoster} / {@code fnAddRow} / {@code fnSaveRoster} / {@code fnBack}), but the model
 * fields differ — this screen uses lower-case {@code form.departmentid} where Consultant On Call uses
 * {@code form.DepartmentID}, adds {@code form.shiftid}, and has no Doctor or Consultation Room select. So
 * it is a separate Page Object rather than a subclass.</p>
 *
 * <p><b>Two widgets worth knowing about</b> (same as Consultant On Call):</p>
 * <ul>
 *   <li><b>Native date inputs</b> — write {@code yyyy-MM-dd} and let Angular's date directive parse it;
 *       {@code $setViewValue} with a string would store a string where a {@code Date} is expected.</li>
 *   <li><b>The Medical Officer is a ui-select TYPEAHEAD</b> — no {@code <select>} to set, and its choices
 *       do not render on open. It is driven by clicking the search box and typing.</li>
 * </ul>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class MORoster extends BasePage {

    public MORoster(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/MORoster";
    public static final String ADD_ROUTE = "#/add-MORoster";

    public String lastControls = "";
    public String lastFromDate = "", lastToDate = "";
    public String lastDepartment = "", lastShift = "", lastMedicalOfficer = "";

    // ---- shared JS ---------------------------------------------------------

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const A=window.angular; const $=window.jQuery;"
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "const pick=async(ng)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  for(let k=0;k<15;k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };"
            // native <input type=date>: write yyyy-MM-dd and let Angular's date directive parse it.
            + "const setDate=(ng,v)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  e.value=v; e.dispatchEvent(new Event('input',{bubbles:true}));"
            + "  e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "  return e.value||v; };";

    private static final String ARM_TOASTS = ""
            + " window.__moToasts=[]; if(window.__moObs) window.__moObs.disconnect();"
            + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__moToasts.includes(t)) window.__moToasts.push(t); }); };"
            + " window.__moObs=new MutationObserver(grab); window.__moObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation --------------------------------------------------------

    /** <b>Ancillary Services</b> → <b>Doctor Roster</b> → <b>MO Roster</b>, else the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("MORoster.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*doctor\\s*roster\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);

        // Exact match first — "MO Roster" sits beside "ConsultantOnCall" in the same submenu.
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^mo\\s*roster$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/MORoster/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__moMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__moMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("MORoster.nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__moMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onListScreen()) {
            System.out.println("MORoster.nav: falling back to direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/fnGoToNewRoster/i.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("MORoster.nav: list screen did not finish rendering"); }
        return onListScreen();
    }

    public boolean onListScreen() {
        String url = page.url().toLowerCase();
        return url.contains("moroster") && !url.contains("add-");
    }

    public boolean onRosterForm() {
        if (page.url().toLowerCase().contains("add-moroster")) return true;
        return Boolean.TRUE.equals(page.evaluate(
                "() => !![...document.querySelectorAll(\"[ng-model='form.shiftid']\")].find(e=>e.offsetParent!==null)"));
    }

    /** Click <b>New Roster</b> ({@code fnGoToNewRoster()}); polled, as it renders after the filters. */
    public boolean clickNewRoster() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/fnGoToNewRoster/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*new\\s*roster\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__moNew'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("MORoster.clickNewRoster: New Roster button not found"); return false; }
        try { page.locator("#__moNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("MORoster.clickNewRoster: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__moNew'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='form.shiftid']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("MORoster.clickNewRoster: roster form did not render"); }
        waitForAngular(1200);
        return onRosterForm();
    }

    /** Diagnostics: selects, ui-selects and inputs with their labels. Never fails the flow. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||''); };"
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const uis=[...document.querySelectorAll('.ui-select-container,[ui-select],ui-select')].filter(vis)"
                + "   .map(e=>'UISELECT \"'+labelOf(e)+'\" text=\"'+norm(e.textContent).slice(0,40)+'\"');"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] type='+e.type);"
                + " return sel.concat(uis).concat(inp).join(' || '); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("MORoster controls: " + lastControls);
        return lastControls;
    }

    // ---- step 1: dates -----------------------------------------------------

    /**
     * Set <b>From Date</b> and <b>To Date</b>.
     *
     * @param fromDate {@code yyyy-MM-dd}
     * @param toDate   {@code yyyy-MM-dd}
     */
    public String selectDates(String fromDate, String toDate) {
        Object r = page.evaluate("(args) => {" + JS
                + " const [from, to] = args;"
                + " const f=setDate('form.FromDate', from); const t=setDate('form.ToDate', to);"
                + " return 'FromDate='+f+' | ToDate='+t; }", java.util.List.of(fromDate, toDate));
        String out = r == null ? "" : r.toString();
        for (String part : out.split("\\|")) {
            String p = part.trim();
            if (p.startsWith("FromDate=")) lastFromDate = p.substring(9).trim();
            else if (p.startsWith("ToDate=")) lastToDate = p.substring(7).trim();
        }
        waitForAngular(600);
        System.out.println("MORoster: " + out);
        return out;
    }

    public boolean datesSet() { return isReal(lastFromDate) && isReal(lastToDate); }

    // ---- step 2: department + shift + medical officer ----------------------

    /**
     * Select <b>Department</b> ({@code form.departmentid}), <b>Shift</b> ({@code form.shiftid}) and the
     * <b>Medical Officer</b> (ui-select typeahead).
     */
    public String selectDepartmentShiftAndOfficer() {
        return selectDepartmentShiftAndOfficer(null, null);
    }

    /**
     * As above, but preferring the Department / Shift option whose text matches (case-insensitive).
     * Pass null for either to take its first real option.
     */
    public String selectDepartmentShiftAndOfficer(String deptPrefer, String shiftPrefer) {
        lastDepartment = pickPreferred("form.departmentid", deptPrefer);
        waitForAngular(1200);   // department may cascade into the officer list

        lastShift = pickPreferred("form.shiftid", shiftPrefer);
        waitForAngular(600);

        lastMedicalOfficer = selectMedicalOfficer();

        String out = "Department=" + lastDepartment + " | Shift=" + lastShift
                + " | MedicalOfficer=" + lastMedicalOfficer;
        System.out.println("MORoster: " + out);
        return out;
    }

    /**
     * Select an option on {@code ngModel}: the first whose text matches {@code preferText}, else the first
     * real option. Fires change through Angular and jQuery, polling while the list still loads.
     */
    private String pickPreferred(String ngModel, String preferText) {
        if (preferText != null && !preferText.isBlank()) {
            Object r = page.evaluate("(args) => {" + JS
                    + " const [ng, want] = args;"
                    + " const e=byNg(ng); if(!e) return '(no-field)';"
                    + " const i=[...e.options].findIndex(o=>real(o) && new RegExp(String(want),'i').test(norm(o.textContent)));"
                    + " if(i<0) return '(no-match)';"
                    + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){}"
                    + " if($){ try{ $(e).trigger('change'); }catch(x){} }"
                    + " return norm(e.options[i].textContent); }", java.util.List.of(ngModel, preferText));
            String picked = r == null ? "" : r.toString();
            if (!picked.startsWith("(")) return picked;
            System.out.println("MORoster: no " + ngModel + " option matching \"" + preferText + "\" — using the first");
        }
        Object r = page.evaluate("(ng) => new Promise(async resolve => {" + JS
                + " resolve(await pick(ng)); })", ngModel);
        return r == null ? "" : r.toString();
    }

    /**
     * Choose the <b>Medical Officer</b> in the ui-select.
     *
     * <p>It is a typeahead: opening it renders no choices, so the search box is clicked and typed into,
     * then cleared to reveal the full list. Falls back to the match/toggle element.</p>
     */
    private String selectMedicalOfficer() {
        Object opened = page.evaluate("() => {" + JS
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm(gl?gl.textContent:''); };"
                + " const conts=[...document.querySelectorAll('.ui-select-container,[ui-select],ui-select')].filter(vis);"
                + " if(!conts.length) return '';"
                + " const c=conts.find(x=>/medical\\s*officer|officer|mo\\b/i.test(labelOf(x))) || conts[0];"
                + " c.setAttribute('data-mo','officer');"
                + " const t=c.querySelector('.ui-select-toggle,.ui-select-match')||c.querySelector('input');"
                + " if(t) t.setAttribute('data-mo','officerToggle');"
                + " return labelOf(c)||'(unlabelled ui-select)'; }");
        if (opened == null || opened.toString().isEmpty()) {
            System.out.println("MORoster.selectMedicalOfficer: no ui-select found");
            return "(no-uiselect)";
        }

        String chosen;
        try {
            com.microsoft.playwright.Locator search = page.locator("[data-mo='officer'] input").first();
            search.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            waitForAngular(500);
            chosen = pickOfficerChoice();
            if (chosen != null) return chosen;

            search.type("a");
            waitForAngular(900);
            chosen = pickOfficerChoice();
            if (chosen != null) return chosen;

            search.fill("");
            waitForAngular(900);
            chosen = pickOfficerChoice();
            if (chosen != null) return chosen;
        } catch (Exception e) {
            System.out.println("MORoster.selectMedicalOfficer: typeahead attempt failed - " + e.getMessage());
        }

        try {
            page.locator("[data-mo='officerToggle']").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            waitForAngular(800);
            chosen = pickOfficerChoice();
            if (chosen != null) return chosen;
        } catch (Exception e) {
            System.out.println("MORoster.selectMedicalOfficer: toggle click failed - " + e.getMessage());
        }

        Object dump = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=document.querySelector(\"[data-mo='officer']\");"
                + " return 'choicesRowsInDoc='+document.querySelectorAll('.ui-select-choices-row').length"
                + "   +' | html='+(c?norm(c.outerHTML).slice(0,300):'(container gone)'); }");
        System.out.println("MORoster.selectMedicalOfficer: no choices rendered. " + dump);
        return "(no-choices)";
    }

    private String pickOfficerChoice() {
        for (int i = 0; i < 8; i++) {
            Object chosen = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>e && e.offsetParent!==null;"
                    + " const rows=[...document.querySelectorAll('.ui-select-choices-row')].filter(vis)"
                    + "   .filter(r=>norm(r.textContent) && !/^-*\\s*select|^--/i.test(norm(r.textContent)));"
                    + " if(!rows.length) return '';"
                    + " const r=rows[0]; const t=norm(r.textContent);"
                    + " const clickable=r.querySelector('.ui-select-choices-row-inner,a,div')||r;"
                    + " clickable.click(); return t; }");
            if (chosen != null && !chosen.toString().isEmpty()) {
                System.out.println("MORoster: Medical Officer = " + chosen);
                return chosen.toString();
            }
            page.waitForTimeout(400);
        }
        return null;
    }

    public boolean departmentShiftOfficerSelected() {
        return isReal(lastDepartment) && isReal(lastShift) && isReal(lastMedicalOfficer);
    }

    // ---- step 3: add row ---------------------------------------------------

    /** Click <b>Add</b> ({@code fnAddRow()}) and report how many roster rows the grid then holds. */
    public String clickAdd() {
        Object before = page.evaluate("() => document.querySelectorAll('table tbody tr, .ui-grid-row').length");
        int rowsBefore = before == null ? 0 : Integer.parseInt(before.toString());

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/fnAddRow/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__moAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("MORoster.clickAdd: Add button not found"); return "(no Add button)"; }
        try { page.locator("#__moAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("MORoster.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__moAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);

        Object r = page.evaluate("(rowsBefore) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=document.querySelectorAll('table tbody tr, .ui-grid-row').length;"
                + " const msg=[...document.querySelectorAll('.toast-message,.toast')].map(x=>norm(x.textContent)).join(' ');"
                + " return 'rowsAdded='+(rows-rowsBefore)+' total='+rows+(msg?' | msg='+msg:''); }", rowsBefore);
        String out = r == null ? "" : r.toString();
        System.out.println("MORoster: Add -> " + out);
        return out;
    }

    // ---- step 4: save ------------------------------------------------------

    /** Click <b>Save</b> ({@code fnSaveRoster()}) and return the toast ("" if none appeared). */
    public String saveAndGetToast() {
        waitForToastsToClear();

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/fnSaveRoster/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__moSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("MORoster.save: Save button not found"); return ""; }

        try { page.locator("#__moSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("MORoster.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__moSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);

        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__moToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__moToasts||[]).includes(t)) (window.__moToasts=window.__moToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__moToasts||[];"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("MORoster: a previous toast is still on screen — the next capture may be stale");
        }
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }
}
