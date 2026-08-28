package com.kpj.pages.AncillaryServices_page.Ambulance_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Ambulance &gt; <b>AmbulanceStaff</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Ambulance</b> → <b>AmbulanceStaff</b> ({@code #/AmbulanceStaff})
 * → select <b>Staff Type</b> + <b>Staff Name</b> → <b>Add</b> → toast → tick the row's <b>select</b> box →
 * <b>Edit</b> → change <b>Staff Name</b> → <b>Save</b> → toast.</p>
 *
 * <p><b>A third, different shape.</b> Unlike Ambulance Booking / Requisition (one shared controller) and
 * Ambulance Usage (its own {@code AmbulanceUsage.*} model behind an Add form), this is an <b>inline roster
 * grid</b>: there is no separate add form and no model prefix at all. Verified live 2026-08-06 the models
 * are bare scope properties — {@code selectedStaffTypeId}, {@code selectedStaffNameId}, {@code selectedDate}
 * and a per-row {@code staff.selected} checkbox. Buttons: {@code fetchStaffRosterData()} (Search),
 * {@code addStaffRoster()} (Add), {@code saveStaffRoster()} (Save).</p>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class AmbulanceStaff extends BasePage {

    public AmbulanceStaff(Page page) { super(page); }

    public static final String ROUTE = "#/AmbulanceStaff";

    public String lastControls = "";
    public String lastStaffType = "", lastStaffName = "", lastStaffNameEdited = "";

    // ---- shared JS ---------------------------------------------------------

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const A=window.angular; const $=window.jQuery;"
            + "const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            // pick the first real option, or (skipCurrent) the first real option that is NOT already selected
            + "const pick=async(ng,skipCurrent)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  for(let k=0;k<15;k++){ const opts=[...e.options];"
            + "    let i=opts.findIndex((o,n)=>real(o) && (!skipCurrent || n!==e.selectedIndex));"
            + "    if(i<0 && skipCurrent) i=opts.findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(opts[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };";

    /** Installs a fresh toast MutationObserver. Must run BEFORE the click — toasts fade in ~1s. */
    private static final String ARM_TOASTS = ""
            + " window.__stfToasts=[]; if(window.__stfObs) window.__stfObs.disconnect();"
            + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__stfToasts.includes(t)) window.__stfToasts.push(t); }); };"
            + " window.__stfObs=new MutationObserver(grab); window.__stfObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation --------------------------------------------------------

    /** <b>Ancillary Services</b> → <b>Ambulance</b> → <b>AmbulanceStaff</b>, else the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("AmbulanceStaff.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*ambulance\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);

        // The menu label is one word, "AmbulanceStaff" — match with an optional space so either spelling works.
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^ambulance\\s*staff$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/AmbulanceStaff/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__stfMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__stfMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("AmbulanceStaff.nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__stfMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("AmbulanceStaff.nav: falling back to direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        // Wait for the roster controls, not a fixed nap.
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/addStaffRoster/i.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("AmbulanceStaff.nav: screen did not finish rendering"); }
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("ambulancestaff");
    }

    /** Diagnostics: every visible control AND button with its ng-model / ng-click. Never fails the flow. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .map(e=>'INPUT [ng='+(e.getAttribute('ng-model')||'?')+'] type='+e.type);"
                + " const btn=[...document.querySelectorAll('button,a,input[type=button],i,span')].filter(vis)"
                + "   .filter(e=>e.getAttribute('ng-click'))"
                + "   .map(e=>'BTN \"'+norm(e.textContent||e.value)+'\" [ng-click='+e.getAttribute('ng-click')+']');"
                + " return sel.concat(inp).concat(btn).join(' || '); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("AmbulanceStaff controls: " + lastControls);
        return lastControls;
    }

    // ---- step 1: staff type + staff name -----------------------------------

    /** Select <b>Staff Type</b> ({@code selectedStaffTypeId}) then <b>Staff Name</b>
     *  ({@code selectedStaffNameId}) — the name list is filtered by the type, so it is polled after. */
    public String selectStaffTypeAndName() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const t=await pick('selectedStaffTypeId', false); await sleep(800);"
                + " const n=await pick('selectedStaffNameId', false);"
                + " resolve('StaffType='+t+' | StaffName='+n); })");
        String out = r == null ? "" : r.toString();
        for (String part : out.split("\\|")) {
            String p = part.trim();
            if (p.startsWith("StaffType=")) lastStaffType = p.substring(10).trim();
            else if (p.startsWith("StaffName=")) lastStaffName = p.substring(10).trim();
        }
        waitForAngular(400);
        System.out.println("AmbulanceStaff: " + out);
        return out;
    }

    public boolean staffTypeAndNameSelected() {
        return isReal(lastStaffType) && isReal(lastStaffName);
    }

    // ---- step 2: add -------------------------------------------------------

    /** Click <b>Add</b> ({@code addStaffRoster()}) and return the toast ("" if none appeared). */
    public String clickAddAndGetToast() {
        return clickAndCaptureToast("addStaffRoster", "Add", "__stfAdd");
    }

    // ---- step 3: tick the row checkbox ------------------------------------

    /**
     * Tick the roster row's <b>select</b> box ({@code staff.selected}).
     *
     * <p>Polled: the row is appended by Add, so the checkbox does not exist the instant Add returns.
     * The LAST checkbox is ticked — that is the row just added.</p>
     *
     * @return description of what was ticked, or {@code (none)}
     */
    public String tickSelectCheckbox() {
        for (int i = 0; i < 20; i++) {
            // Prefer the row whose text contains the staff name just added. Blindly ticking the last row is
            // wrong here: this grid shows the roster for the selected date, and the added row is not
            // necessarily appended to it (observed ticking an unrelated "Doctor" row). When no row matches,
            // fall back to the last row but SAY SO, so the report never implies the added row was ticked.
            Object r = page.evaluate("(want) => {" + JS
                    + " const boxes=[...document.querySelectorAll(\"input[type=checkbox][ng-model='staff.selected']\")].filter(vis);"
                    + " if(!boxes.length) return '';"
                    + " const rowText=b=>{ const row=b.closest('tr,.ui-grid-row'); return row?norm(row.textContent):''; };"
                    + " let b=null, matched=false;"
                    + " if(want){ b=boxes.find(x=>rowText(x).toLowerCase().includes(String(want).toLowerCase())); matched=!!b; }"
                    + " if(!b) b=boxes[boxes.length-1];"
                    + " if(!b.checked) b.click();"
                    + " return (matched?'ticked the added row':'WARNING no row matched \"'+want+'\" - ticked the last row instead')"
                    + "   +' ('+(boxes.indexOf(b)+1)+'/'+boxes.length+') ['+rowText(b).slice(0,90)+']'; }",
                    lastStaffName);
            if (r != null && !r.toString().isEmpty()) {
                waitForAngular(600);
                System.out.println("AmbulanceStaff: " + r);
                return r.toString();
            }
            page.waitForTimeout(500);
        }
        System.out.println("AmbulanceStaff.tickSelectCheckbox: no staff.selected checkbox found");
        return "(none)";
    }

    // ---- step 4: edit ------------------------------------------------------

    /**
     * Click <b>Edit</b>. The button is not on the screen until a row is ticked, so it is polled for by
     * {@code ng-click} containing "edit" or by visible text/title/class.
     *
     * @return true when an Edit control was found and clicked
     */
    public String clickEdit() {
        for (int i = 0; i < 20; i++) {
            // Match ONLY on an explicit edit signal — ng-click handler, exact text, or title/aria-label.
            // A loose className match (e.g. anything containing "editable") silently clicks the wrong
            // element and reports a pass, so it is deliberately not used.
            Object found = page.evaluate("() => {" + JS
                    + " const c=[...document.querySelectorAll('button,a,input[type=button],i,span,img')].filter(vis);"
                    + " const b=c.find(x=>/edit/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*edit\\s*$/i.test(norm(x.textContent||x.value)))"
                    + "   || c.find(x=>/^\\s*edit\\s*$/i.test(norm(x.getAttribute('title')||x.getAttribute('aria-label')||'')));"
                    + " if(!b) return ''; b.id='__stfEdit';"
                    + " return b.tagName+' \"'+norm(b.textContent||b.value)+'\"'"
                    + "   +' [ng-click='+(b.getAttribute('ng-click')||'-')+']'"
                    + "   +' [title='+(b.getAttribute('title')||'-')+']'; }");
            String what = found == null ? "" : found.toString();
            if (!what.isEmpty()) {
                try { page.locator("#__stfEdit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("AmbulanceStaff.clickEdit: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__stfEdit'); if(e) e.removeAttribute('id'); }");
                waitForAngular(1000);
                System.out.println("AmbulanceStaff: Edit -> " + what);
                return what;
            }
            page.waitForTimeout(500);
        }
        // Not found — say so, and dump what IS clickable so the report explains why.
        System.out.println("AmbulanceStaff.clickEdit: no Edit control found. Available: " + describeControls());
        return "";
    }

    // ---- step 5: change staff name ----------------------------------------

    /** Re-select <b>Staff Name</b>, preferring an option different from the one already chosen. */
    public String selectStaffNameForEdit() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('selectedStaffNameId', true)); })");
        lastStaffNameEdited = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("AmbulanceStaff: StaffName (edit) = " + lastStaffNameEdited);
        return lastStaffNameEdited;
    }

    // ---- step 6: save ------------------------------------------------------

    /** Click <b>Save</b> ({@code saveStaffRoster()}) and return the toast ("" if none appeared). */
    public String clickSaveAndGetToast() {
        String toast = clickAndCaptureToast("saveStaffRoster", "Save", "__stfSave");
        acceptSaveDialog();   // harmless if this screen raises no confirm
        return toast;
    }

    // ---- shared click+toast -------------------------------------------------

    /**
     * Arm the toast observer, click the control (matched by {@code ng-click} handler first, then by exact
     * visible text), and return the first meaningful toast.
     */
    private String clickAndCaptureToast(String handler, String text, String tagId) {
        // Wait for any PREVIOUS toast to leave the DOM first. Without this the Add toast is still on screen
        // when Save arms its observer, gets grabbed immediately, and waitForFunction returns on it — Save
        // would then report the Add toast as its own result (observed: both steps showing the same text).
        // Waiting for a clear DOM also keeps working when two actions produce the IDENTICAL text, which
        // filtering by text would not.
        waitForToastsToClear();

        Object tagged = page.evaluate("(args) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const [handler, text, tagId] = args;"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                + "   .filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>new RegExp(handler,'i').test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>new RegExp('^\\\\s*'+text+'\\\\s*$','i').test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id=tagId; return true; }",
                java.util.List.of(handler, text, tagId));
        if (!Boolean.TRUE.equals(tagged)) {
            System.out.println("AmbulanceStaff: " + text + " button not found (" + handler + ")");
            return "";
        }
        try { page.locator("#" + tagId).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("AmbulanceStaff: " + text + " click failed - " + e.getMessage()); }
        page.evaluate("(id) => { const e=document.getElementById(id); if(e) e.removeAttribute('id'); }", tagId);

        try {
            page.waitForFunction("() => (window.__stfToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__stfToasts||[]).includes(t)) (window.__stfToasts=window.__stfToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__stfToasts||[];"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /**
     * Block until no toast is visible (max ~8s), so the next capture cannot pick up the previous one.
     * Returns quietly if a toast lingers — the caller still reports whatever it then observes.
     */
    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("AmbulanceStaff: a previous toast is still on screen — the next capture may be stale");
        }
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }
}
