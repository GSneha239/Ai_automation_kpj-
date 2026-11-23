package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Modality Schedule</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Modality Schedule</b> ({@code #/modalitySchedule})
 * → <b>Add</b> ({@code fnAddModSchedule()} on the list, opens {@code #/add-modalitySchedule}) → <b>Department</b>,
 * <b>Modality</b>, <b>Start Time</b>, <b>End Time</b>, <b>Days</b> → inner <b>Add</b> (same {@code fnAddModSchedule()}
 * name, now the row-add) → <b>Submit</b> → success toast <i>"Modality Schedule Saved Successfully."</i>.</p>
 *
 * <p>Sibling of {@link DepartmentSchedule} / {@link PayableScheduleMaster}, with three differences discovered
 * live:</p>
 * <ul>
 *   <li><b>Location</b> ({@code modScheduleInfo.Locationid}) is a HARD-disabled proxy select
 *       ({@code ng-disabled="true"}, not a scope flag) — Playwright's real {@code selectOption} times out waiting
 *       for it to become enabled. It only moves via a JS {@code selectedIndex} + {@code dispatchEvent('change')} +
 *       Angular {@code triggerHandler('change')}, same recipe as the select2 fields elsewhere.</li>
 *   <li><b>Start/End Time are NOT settable through the visible text box.</b> The row that lands in the detail grid
 *       is built from the {@code uib-timepicker} SPINNER (hours/minutes inputs + AM/PM button), which is
 *       conditionally rendered — {@code offsetParent===null} until the text box is clicked to open its popup, and
 *       reused/shared: opening the SECOND field's popup without first closing the first one lands the edits back
 *       on the first field. Typing "05:00 PM" straight into the End Time text box visibly showed "05:00 PM" and
 *       still saved the row as "08:00 AM" — the default the spinner never left.</li>
 *   <li><b>A duplicate rejection ("Modality already has scheduled!") fully wipes the form</b> (Location/Department/
 *       Modality all revert to "--Select--") AND leaves Department stuck {@code disabled} — re-driving Location's
 *       JS dispatch in place does not recover it. The only reliable retry is Back → Add (a real SPA
 *       navigation), which reinitializes the controller from scratch; DO NOT try to recover in place.</li>
 * </ul>
 */
public class ModalitySchedule extends BasePage {

    public ModalitySchedule(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastLocation = "", lastDepartment = "", lastModality = "", lastStart = "", lastEnd = "", lastDay = "";
    public String lastAddToasts = "[]", lastToasts = "[]", lastSaveApi = "";
    public int lastDepartmentIndex = -1, lastModalityIndex = -1;
    /** The screen while the message is up — the checks that follow outlive the dialog. */
    public byte[] toastPng;

    // ---- probes ----------------------------------------------------------

    /** Every anchor mentioning "modality", with the submenu it sits under. */
    public String findModalityLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/modality/i.test(norm(a.textContent)) || /modality/i.test(a.getAttribute('href')||''))"
                + "   .map(a=>parentOf(a)+' > '+norm(a.textContent).slice(0,36)+' -> '+(a.getAttribute('href')||'')+(a.offsetParent===null?' (collapsed)':''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,40).join('  |  '); }");
        return r == null ? "" : r.toString();
    }

    /** Every visible field / select / checkbox / button with its ng-model or ng-click. */
    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='', p=e.parentElement, h=0;"
                + "   while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t.slice(0,34); };"
                + " const f=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden'"
                + "     && !/colFilter|paginationCurrentPage|row\\.entity/i.test(e.getAttribute('ng-model')||'')).slice(0,30)"
                + "   .map(e=>e.tagName.toLowerCase()+':'+(e.type||'')+' ng=\"'+(e.getAttribute('ng-model')||'')+'\" lbl=\"'+lbl(e)+'\"');"
                + " const s=[...document.querySelectorAll('select')].slice(0,20)"
                + "   .map(e=>'select ng=\"'+(e.getAttribute('ng-model')||e.id||'?')+'\" opts='+e.options.length+(e.offsetParent===null?' (hidden)':'')+' lbl=\"'+lbl(e)+'\"');"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " let heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + " if(!heads.length) heads=[...document.querySelectorAll('table thead th')].map(h=>norm(h.textContent));"
                + " return 'FIELDS:\\n  '+f.join('\\n  ')+'\\nSELECTS:\\n  '+s.join('\\n  ')"
                + "   +'\\nGRID HEADERS: '+heads.join(' | ')+'\\nBUTTONS: '+b.join(' , '); }");
        return r == null ? "" : r.toString();
    }

    /** What the app actually rendered — header + URL. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,section.content-header,h1')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,60); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        try {
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*locations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
        } catch (Exception e) {
            System.out.println("ModalitySchedule.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*modality\\s*schedule\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/modalityschedule/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__msMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__msMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("ModalitySchedule.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__msMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__msMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("ModalitySchedule.nav: menu link not found. modality links => " + findModalityLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("ModalitySchedule.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /modality\\s*schedule/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    /** Real click with a DOM-click fallback for elements the sticky header overlays. */
    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("ModalitySchedule." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("ModalitySchedule." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Click the list screen's <b>Add</b> ({@code fnAddModSchedule()}). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/fnaddmodschedule/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__msAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("ModalitySchedule.clickAdd: Add button not found"); return false; }
        robustClick("__msAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    /** True once the add form (its Department select) is on screen. */
    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('select')]"
                + " .some(s=>/modScheduleInfo\\.Departmentid/i.test(s.getAttribute('ng-model')||''))"));
    }

    /** Leave the form via <b>Back</b> ({@code closeForm()}). */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__msBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__msBack", "clickBack");
        waitForAngular(2000);
        return true;
    }

    /**
     * Select the mandatory <b>Location</b>. The select is HARD-disabled ({@code ng-disabled="true"}) so real
     * {@code selectOption} times out — it only moves via {@code selectedIndex} + a JS {@code change} dispatch +
     * Angular {@code triggerHandler}, which also fires {@code fngetDepartment()} and unlocks Department.
     */
    public String selectLocation() {
        Object r = page.evaluate("() => { const A=window.angular;"
                + " const s=[...document.querySelectorAll('select')].find(x=>/modScheduleInfo\\.Locationid/i.test(x.getAttribute('ng-model')||''));"
                + " if(!s) return '';"
                + " const real=[...s.options].findIndex(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(real<0) return ''; s.selectedIndex=real; s.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ A.element(s).triggerHandler('change'); }catch(x){}"
                + " return s.options[s.selectedIndex].textContent.trim(); }");
        lastLocation = r == null ? "" : r.toString();
        waitForAngular(800);
        return lastLocation;
    }

    /** How many real (non-placeholder) options a select currently offers. */
    private int optionCount(String ngModel) {
        Object n = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return 0;"
                + " return [...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())).length; }", ngModel);
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /** Choose the {@code index}-th real option of the select bound to {@code ngModel} (0 = first non-placeholder). */
    private String selectNth(String ngModel, int index) {
        for (int w = 0; w < 12 && optionCount(ngModel) == 0; w++) page.waitForTimeout(600);
        Object idx = page.evaluate("([m,n]) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1; s.id='__msSel';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(n>=real.length) return -1; return real[n].i; }", java.util.Arrays.asList(ngModel, index));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__msSel'); if(e) e.removeAttribute('id'); }"); return ""; }
        try {
            page.locator("#__msSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectNth(" + ngModel + "): real select failed - " + e.getMessage());
            page.evaluate("(k) => { const e=document.getElementById('__msSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(900);
        Object t = page.evaluate("() => { const e=document.getElementById('__msSel');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /** Select the {@code index}-th <b>Department</b>. Real select, but only enabled once Location has committed. */
    public String selectDepartment(int index) {
        lastDepartment = selectNth("modScheduleInfo.Departmentid", index);
        if (!lastDepartment.isEmpty()) lastDepartmentIndex = index;
        return lastDepartment;
    }

    /** Select the {@code index}-th <b>Modality</b> — DEPENDENT on Department, refilled by its ng-change. */
    public String selectModality(int index) {
        lastModality = selectNth("modScheduleInfo.Modalityid", index);
        if (!lastModality.isEmpty()) lastModalityIndex = index;
        return lastModality;
    }

    /**
     * Enter <b>Start Time</b> and <b>End Time</b>.
     *
     * <p>The schedule row is built from the SPINNER (hours/minutes/AM-PM), not the visible text box — typing a
     * full "05:00 PM" into the box showed exactly that text but saved as the spinner's untouched default. Each
     * field's spinner is conditionally rendered (hidden until the box is clicked to open it) and the two fields
     * SHARE it: opening the second field without closing the first lands the edits back on the first. So each
     * field is opened, set, and explicitly closed (a click elsewhere) before the next one opens.</p>
     */
    public String fillTimes(String start, String end) {
        java.util.regex.Matcher sm = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])").matcher(start);
        java.util.regex.Matcher em = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])").matcher(end);
        if (!sm.find() || !em.find()) { System.out.println("fillTimes: cannot parse '" + start + "' / '" + end + "'"); return ""; }
        lastStart = setTimeWidget(0, sm.group(1), sm.group(2), sm.group(3).toUpperCase());
        closePopup();
        lastEnd = setTimeWidget(1, em.group(1), em.group(2), em.group(3).toUpperCase());
        closePopup();
        return "Start Time=" + (lastStart.isEmpty() ? "(not set)" : lastStart)
                + " | End Time=" + (lastEnd.isEmpty() ? "(not set)" : lastEnd);
    }

    /** Click elsewhere so an open timepicker popup releases its hours/minutes inputs before the next one opens. */
    private void closePopup() {
        page.evaluate("() => { const h=document.querySelector('.content-header,section.content-header,h1')||document.body; h.click(); }");
        waitForAngular(300);
    }

    /**
     * Open the {@code idx}-th time box (0 = Start, 1 = End), set hours/minutes via a REAL Playwright fill (a JS
     * value-set + dispatched events did NOT propagate to the outer model in testing — only genuine
     * user-equivalent input did), toggle AM/PM with a real click if needed, and return what the box displays.
     */
    private String setTimeWidget(int idx, String hh, String mm, String meridian) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("(i) => { const boxes=[...document.querySelectorAll(\"input[ng-model='inputTime']\")].filter(e=>e.offsetParent!==null);"
                + " const f=boxes[i]; if(!f) return false; f.id='__msTimeBox'; f.scrollIntoView({block:'center'}); return true; }", idx));
        if (!tagged) { System.out.println("setTimeWidget: no time box at index " + idx); return ""; }
        try { page.locator("#__msTimeBox").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("setTimeWidget: opening the popup failed - " + e.getMessage()); return ""; }
        waitForAngular(300);

        boolean spinnerTagged = Boolean.TRUE.equals(page.evaluate("() => { const hours=[...document.querySelectorAll(\"input[ng-model='hours']\")].filter(e=>e.offsetParent!==null)[0];"
                + " const mins=[...document.querySelectorAll(\"input[ng-model='minutes']\")].filter(e=>e.offsetParent!==null)[0];"
                + " if(!hours || !mins) return false; hours.id='__msHourBox'; mins.id='__msMinBox'; return true; }"));
        if (!spinnerTagged) { System.out.println("setTimeWidget: spinner did not open for index " + idx); return ""; }
        try {
            page.locator("#__msHourBox").fill(hh);
            page.locator("#__msMinBox").fill(mm);
        } catch (Exception e) { System.out.println("setTimeWidget: filling the spinner failed - " + e.getMessage()); }
        waitForAngular(300);

        Object cur = page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(am|pm)$/i.test((x.textContent||'').trim()));"
                + " if(b) b.id='__msMeridianBox'; return b ? b.textContent.trim().toUpperCase() : ''; }");
        if (!meridian.equals(String.valueOf(cur))) {
            try { page.locator("#__msMeridianBox").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(3000)); }
            catch (Exception e) { System.out.println("setTimeWidget: meridian toggle failed - " + e.getMessage()); }
            waitForAngular(300);
        }
        Object v = page.evaluate("() => { const e=document.getElementById('__msTimeBox'); const val=e?(e.value||''):'';"
                + " ['__msTimeBox','__msHourBox','__msMinBox','__msMeridianBox'].forEach(id=>{ const el=document.getElementById(id); if(el) el.removeAttribute('id'); });"
                + " return val; }");
        return v == null ? "" : v.toString().trim();
    }

    /** Tick ONE <b>Day</b>. All seven boxes share ng-model {@code todo.done}, matched by nearby text. */
    public String tickDay(String day) {
        Object r = page.evaluate("(d) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null && !/row\\.entity/i.test(c.getAttribute('ng-model')||''));"
                + " const cb=cbs.find(c=>new RegExp(d,'i').test(norm((c.closest('label')||c.parentElement||{}).textContent||'')));"
                + " if(!cb) return ''; if(cb.checked) return 'already'; cb.id='__msDay'; return 'tag'; }", day);
        String how = String.valueOf(r);
        if (how.isEmpty()) { System.out.println("tickDay: no checkbox for " + day); lastDay = ""; return ""; }
        if ("tag".equals(how)) robustClick("__msDay", "tickDay");
        boolean on = Boolean.TRUE.equals(page.evaluate("() => { const c=document.getElementById('__msDay');"
                + " const v=!!(c && c.checked); if(c) c.removeAttribute('id'); return v; }"));
        waitForAngular(200);
        lastDay = (on || "already".equals(how)) ? day : "";
        return lastDay;
    }

    /** How many rows the Days detail grid holds. */
    public int scheduleRowCount() {
        Object n = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/start\\s*time/i.test(norm((t.querySelector('thead')||{}).innerText||'')));"
                + " if(!tbl) return 0;"
                + " return [...tbl.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent) && !/no records|no data/i.test(r.textContent)).length; }");
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /** PROBE — what the Days grid holds. */
    public String scheduleRows() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/start\\s*time/i.test(norm((t.querySelector('thead')||{}).innerText||'')));"
                + " if(!tbl) return '(no schedule table)';"
                + " return [...tbl.querySelectorAll('tbody tr')].map(r=>norm(r.textContent).slice(0,80)).filter(t=>t).slice(0,8).join(' ;; '); }");
        return r == null ? "" : r.toString();
    }

    /** Click the form's inner <b>Add</b> ({@code fnAddModSchedule()}) — commits the day/time line to the grid. */
    public String clickInnerAdd() {
        int before = scheduleRowCount();
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " window.__msAddToasts=[]; if(window.__msAddObs) window.__msAddObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__msAddToasts.includes(t)) window.__msAddToasts.push(t); }); };"
                + " window.__msAddObs=new MutationObserver(grab); window.__msAddObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null && /fnaddmodschedule/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__msAddRow'; b.scrollIntoView({block:'center'}); return true; }"));
        if (!tagged) return "inner Add button not found";
        boolean realClicked = true;
        try { page.locator("#__msAddRow").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { realClicked = false; System.out.println("clickInnerAdd: real click intercepted"); }
        waitForAngular(1200);
        int after = scheduleRowCount();
        if (after == before && !realClicked) {
            System.out.println("clickInnerAdd: DOM click fallback");
            page.evaluate("() => { const e=document.getElementById('__msAddRow'); if(e) e.click(); }");
            waitForAngular(1200);
            after = scheduleRowCount();
        }
        page.evaluate("() => { const e=document.getElementById('__msAddRow'); if(e) e.removeAttribute('id'); }");
        Object t = page.evaluate("() => JSON.stringify(window.__msAddToasts||[])");
        lastAddToasts = t == null ? "[]" : t.toString();
        return "schedule rows " + before + " -> " + after
                + ("[]".equals(lastAddToasts) ? "" : "  | message: " + lastAddToasts);
    }

    /**
     * Fill the whole form on a fresh Add: Location, Department/Modality from {@code deptIndex}/{@code modIndex},
     * the times, one Day, then the inner Add. Used by the retry, which must re-enter every field on a genuinely
     * fresh form (see the class doc — recovering the old form in place after a duplicate does not work).
     */
    public String fillAll(int deptIndex, int modIndex, String start, String end, String day) {
        String loc = selectLocation();
        String dept = selectDepartment(deptIndex);
        String mod = selectModality(modIndex);
        String times = fillTimes(start, end);
        String d = tickDay(day);
        String added = clickInnerAdd();
        return "Location=" + loc + " | Department=" + dept + " | Modality=" + mod + " | " + times
                + " | Day=" + d + " | " + added;
    }

    /** Click <b>Submit</b> and return the toast; screenshots the message the moment it appears. */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("modalityschedule")
                && resp.url().toLowerCase().contains("iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__msToasts=[]; if(window.__msObs) window.__msObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__msToasts.includes(t)) window.__msToasts.push(t); }); };"
                + " window.__msObs=new MutationObserver(grab); window.__msObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__msSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__msSubmit", "submit");

        toastPng = null;
        for (int i = 0; i < 100 && toastPng == null; i++) {
            boolean showing = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast],.jAlert,.modal')]"
                    + " .some(el=>el.offsetParent!==null && (el.textContent||'').trim())"));
            if (showing) {
                try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); }
                catch (Exception e) { System.out.println("submit: toast screenshot failed - " + e.getMessage()); break; }
            } else page.waitForTimeout(200);
        }
        if (toastPng == null) System.out.println("submit: no message was ever on screen to screenshot");
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__msToasts||[]).includes(t)) (window.__msToasts=window.__msToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__msToasts||[])");
        lastToasts = all == null ? "[]" : all.toString();
        System.out.println("submit: toasts => " + lastToasts);
        try {
            if (hold[0] != null) {
                String body = new String(hold[0].body()).replaceAll("\\s+", " ").trim();
                System.out.println("submit: save API => " + hold[0].status() + " " + hold[0].url() + " => " + body);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"Message\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
                java.util.regex.Matcher rs = java.util.regex.Pattern.compile("\"ResultStatus\"\\s*:\\s*(\\d+)").matcher(body);
                lastSaveApi = "HTTP " + hold[0].status() + (rs.find() ? ", ResultStatus=" + rs.group(1) : "")
                        + (m.find() ? ", Message=\"" + m.group(1) + "\"" : "");
            }
        } catch (Exception e) { System.out.println("submit: could not read the save response - " + e.getMessage()); }
        Object r = page.evaluate("() => { const a=window.__msToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already|scheduled/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(600);
        return r == null ? "" : r.toString().trim();
    }
}
