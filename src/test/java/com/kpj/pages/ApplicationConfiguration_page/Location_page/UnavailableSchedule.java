package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Unavailable Schedule</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Unavailable Schedule</b>
 * ({@code #/UnavailableSchedule}) → select the list's own <b>Schedule Type</b> ({@code unavailableSched.SType}) →
 * <b>Add</b> ({@code fnAddPaySchedule()} — a name borrowed from [[devhis-payable-schedule-master]], reused on
 * BOTH the list's Add and this form's inner Add) → opens {@code #/add-UnavailableSchedule} → the form's OWN
 * <b>Schedule Type</b> ({@code unavailableSched.scheduletypeid} — a DIFFERENT, 7-option select from the list's
 * 4-option one), <b>Location</b>, <b>Department</b>, <b>Payable</b>, <b>Modality</b>, <b>Start Time</b>,
 * <b>End Time</b> → inner <b>Add</b> → <b>Submit</b> → success toast.</p>
 *
 * <p><b>Two "Schedule Type" selects exist on this feature</b> — the list's ({@code SType}: -Select-/Department/
 * Doctor/Modality) filters the list-screen grid, and the add form's own ({@code scheduletypeid}: --Select--/
 * Department/Doctor/Modality/Procedure/Sub-Department/Vaccination) is a separate field on the row being created.
 * Both must be picked per the flow — do not assume the list's choice carries into the form.</p>
 *
 * <p><b>Payable ({@code unavailableSched.Payableid}) starts with a full unfiltered list (265 options) and
 * NARROWS once Department commits</b> (confirmed live: 265 → 10 after picking "Administration") — so Department
 * must be selected and settled before Payable is read, same shape as the Department→Payable cascade in
 * [[devhis-payable-schedule-master]] and [[devhis-sub-department-schedule]]. <b>Modality does not depend on
 * anything</b> — its 60 options stayed constant across every Department/Payable combination tried.</p>
 *
 * <p>Same {@code uib-timepicker} spinner-only widget as [[devhis-modality-schedule]] / [[devhis-sub-department-schedule]]
 * for Start/End Time — the visible text box is cosmetic, the two fields share one popup (open, set, close before
 * the next). Selection here is via JS {@code selectedIndex} + {@code dispatchEvent('change')} + Angular
 * {@code triggerHandler('change')} throughout, matching [[devhis-sub-department-schedule]]'s proven recipe for
 * this app family rather than a real {@code selectOption} (which silently broke that screen's cascade).</p>
 */
public class UnavailableSchedule extends BasePage {

    public UnavailableSchedule(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastListScheduleType = "", lastFormScheduleType = "", lastLocation = "";
    public String lastDepartment = "", lastPayable = "", lastModality = "", lastStart = "", lastEnd = "";
    public String lastAddToasts = "[]", lastToasts = "[]", lastSaveApi = "";
    public int lastDepartmentIndex = -1;
    /** The screen while the message is up — the checks that follow outlive the dialog. */
    public byte[] toastPng;

    // ---- probes ----------------------------------------------------------

    public String findUnavailableScheduleLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/unavailable/i.test(norm(a.textContent)) || /unavailable/i.test(a.getAttribute('href')||''))"
                + "   .map(a=>parentOf(a)+' > '+norm(a.textContent).slice(0,36)+' -> '+(a.getAttribute('href')||'')+(a.offsetParent===null?' (collapsed)':''))"
                + "   .filter((v,i,arr)=>v && arr.indexOf(v)===i).slice(0,40).join('  |  '); }");
        return r == null ? "" : r.toString();
    }

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
            System.out.println("UnavailableSchedule.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*unavailable\\s*schedule\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/unavailableschedule/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__usMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__usMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("UnavailableSchedule.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__usMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__usMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("UnavailableSchedule.nav: menu link not found. links => " + findUnavailableScheduleLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("UnavailableSchedule.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /unavailable\\s*schedule/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("UnavailableSchedule." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("UnavailableSchedule." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** How many real (non-placeholder) options a select currently offers. */
    private int optionCount(String ngModel) {
        Object n = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return 0;"
                + " return [...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())).length; }", ngModel);
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /**
     * Choose the {@code index}-th real option of the select bound to {@code ngModel} via JS
     * {@code selectedIndex} + {@code dispatchEvent('change')} + Angular {@code triggerHandler('change')} — NOT a
     * real Playwright {@code selectOption}, the recipe proven for this cascade family in
     * [[devhis-sub-department-schedule]].
     */
    private String selectNth(String ngModel, int index) {
        // Generous poll — the list screen's own Schedule Type select has been seen to render 15-20s after the
        // route change completes, same class of async lag as the Add button on [[devhis-payable-type]].
        for (int w = 0; w < 30 && optionCount(ngModel) == 0; w++) page.waitForTimeout(700);
        Object r = page.evaluate("([m,n]) => { const A=window.angular;"
                + " const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return '';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(n>=real.length) return '';"
                + " const i=real[n].i; s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ A.element(s).triggerHandler('change'); }catch(x){}"
                + " return s.options[s.selectedIndex].textContent.trim(); }", java.util.Arrays.asList(ngModel, index));
        waitForAngular(900);
        return r == null ? "" : r.toString();
    }

    // ---- list screen -------------------------------------------------------

    /** Select the list screen's own <b>Schedule Type</b> filter ({@code unavailableSched.SType}). */
    public String selectListScheduleType(int index) { lastListScheduleType = selectNth("unavailableSched.SType", index); return lastListScheduleType; }

    /** Click the list screen's <b>Add</b> ({@code fnAddPaySchedule()}). Polls generously — the async render can
     *  lag well past the route change. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/^fnaddpayschedule/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__usAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(700);
        }
        if (!tagged) { System.out.println("UnavailableSchedule.clickAdd: Add button not found"); return false; }
        robustClick("__usAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    /** True once the add form (its Department select) is on screen — polled, the SPA route change can leave
     *  stale list-screen DOM behind for a beat. */
    public boolean addFormOpen() {
        for (int i = 0; i < 15; i++) {
            if (Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('select')]"
                    + " .some(s=>/unavailableSched\\.Departmentid/i.test(s.getAttribute('ng-model')||''))"))) return true;
            page.waitForTimeout(500);
        }
        return false;
    }

    /** Leave the form via <b>Back</b> ({@code closeForm()}). */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__usBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__usBack", "clickBack");
        waitForAngular(2000);
        return true;
    }

    // ---- add form ------------------------------------------------------------

    /** Select the ADD FORM's own <b>Schedule Type</b> ({@code unavailableSched.scheduletypeid}) — a different,
     *  7-option select from the list's own {@code SType}. */
    public String selectFormScheduleType(int index) { lastFormScheduleType = selectNth("unavailableSched.scheduletypeid", index); return lastFormScheduleType; }

    /** Select the mandatory <b>Location</b>. */
    public String selectLocation() { lastLocation = selectNth("unavailableSched.Locationid", 0); return lastLocation; }

    /** Select the {@code index}-th <b>Department</b>. */
    public String selectDepartment(int index) {
        lastDepartment = selectNth("unavailableSched.Departmentid", index);
        if (!lastDepartment.isEmpty()) lastDepartmentIndex = index;
        return lastDepartment;
    }

    /**
     * Select the {@code index}-th <b>Payable</b> — narrows from a full unfiltered list once Department commits,
     * so this waits for the option count to settle before reading it.
     */
    public String selectPayable(int index) {
        int before = optionCount("unavailableSched.Payableid");
        for (int w = 0; w < 10; w++) {
            page.waitForTimeout(400);
            int now = optionCount("unavailableSched.Payableid");
            if (now == before && now > 0) break;
            before = now;
        }
        lastPayable = selectNth("unavailableSched.Payableid", index);
        return lastPayable;
    }

    /** Select the {@code index}-th <b>Modality</b> — independent of Department/Payable. */
    public String selectModality(int index) { lastModality = selectNth("unavailableSched.modalityid", index); return lastModality; }

    /**
     * Enter <b>Start Time</b> and <b>End Time</b>. Same spinner-only {@code uib-timepicker} widget as
     * [[devhis-modality-schedule]] / [[devhis-sub-department-schedule]] — the row is built from the hidden
     * hours/minutes spinner, not the visible text box, and the two fields share one popup.
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

    private void closePopup() {
        page.evaluate("() => { const h=document.querySelector('.content-header,section.content-header,h1')||document.body; h.click(); }");
        waitForAngular(300);
    }

    private String setTimeWidget(int idx, String hh, String mm, String meridian) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("(i) => { const boxes=[...document.querySelectorAll(\"input[ng-model='inputTime']\")].filter(e=>e.offsetParent!==null);"
                + " const f=boxes[i]; if(!f) return false; f.id='__usTimeBox'; f.scrollIntoView({block:'center'}); return true; }", idx));
        if (!tagged) { System.out.println("setTimeWidget: no time box at index " + idx); return ""; }
        try { page.locator("#__usTimeBox").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("setTimeWidget: opening the popup failed - " + e.getMessage()); return ""; }
        waitForAngular(300);

        boolean spinnerTagged = Boolean.TRUE.equals(page.evaluate("() => { const hours=[...document.querySelectorAll(\"input[ng-model='hours']\")].filter(e=>e.offsetParent!==null)[0];"
                + " const mins=[...document.querySelectorAll(\"input[ng-model='minutes']\")].filter(e=>e.offsetParent!==null)[0];"
                + " if(!hours || !mins) return false; hours.id='__usHourBox'; mins.id='__usMinBox'; return true; }"));
        if (!spinnerTagged) { System.out.println("setTimeWidget: spinner did not open for index " + idx); return ""; }
        try {
            page.locator("#__usHourBox").fill(hh);
            page.locator("#__usMinBox").fill(mm);
        } catch (Exception e) { System.out.println("setTimeWidget: filling the spinner failed - " + e.getMessage()); }
        waitForAngular(300);

        Object cur = page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(am|pm)$/i.test((x.textContent||'').trim()));"
                + " if(b) b.id='__usMeridianBox'; return b ? b.textContent.trim().toUpperCase() : ''; }");
        if (!meridian.equals(String.valueOf(cur))) {
            try { page.locator("#__usMeridianBox").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(3000)); }
            catch (Exception e) { System.out.println("setTimeWidget: meridian toggle failed - " + e.getMessage()); }
            waitForAngular(300);
        }
        Object v = page.evaluate("() => { const e=document.getElementById('__usTimeBox'); const val=e?(e.value||''):'';"
                + " ['__usTimeBox','__usHourBox','__usMinBox','__usMeridianBox'].forEach(id=>{ const el=document.getElementById(id); if(el) el.removeAttribute('id'); });"
                + " return val; }");
        return v == null ? "" : v.toString().trim();
    }

    /** How many rows the schedule detail grid holds. */
    public int scheduleRowCount() {
        Object n = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/start\\s*time/i.test(norm((t.querySelector('thead')||{}).innerText||'')));"
                + " if(!tbl) return 0;"
                + " return [...tbl.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent) && !/no records|no data/i.test(r.textContent)).length; }");
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /** PROBE — what the detail grid holds. */
    public String scheduleRows() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const tbl=[...document.querySelectorAll('table')].find(t=>/start\\s*time/i.test(norm((t.querySelector('thead')||{}).innerText||'')));"
                + " if(!tbl) return '(no schedule table)';"
                + " return [...tbl.querySelectorAll('tbody tr')].map(r=>norm(r.textContent).slice(0,80)).filter(t=>t).slice(0,8).join(' ;; '); }");
        return r == null ? "" : r.toString();
    }

    /** Click the form's inner <b>Add</b> ({@code fnAddPaySchedule()} — the same name as the list's Add, but the
     *  list is long gone by the time this runs since the SPA has navigated to the add-form route). */
    public String clickInnerAdd() {
        int before = scheduleRowCount();
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " window.__usAddToasts=[]; if(window.__usAddObs) window.__usAddObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__usAddToasts.includes(t)) window.__usAddToasts.push(t); }); };"
                + " window.__usAddObs=new MutationObserver(grab); window.__usAddObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null && /^fnaddpayschedule/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__usAddRow'; b.scrollIntoView({block:'center'}); return true; }"));
        if (!tagged) return "inner Add button not found";
        boolean realClicked = true;
        try { page.locator("#__usAddRow").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { realClicked = false; System.out.println("clickInnerAdd: real click intercepted"); }
        waitForAngular(1200);
        int after = scheduleRowCount();
        if (after == before && !realClicked) {
            System.out.println("clickInnerAdd: DOM click fallback");
            page.evaluate("() => { const e=document.getElementById('__usAddRow'); if(e) e.click(); }");
            waitForAngular(1200);
            after = scheduleRowCount();
        }
        page.evaluate("() => { const e=document.getElementById('__usAddRow'); if(e) e.removeAttribute('id'); }");
        Object t = page.evaluate("() => JSON.stringify(window.__usAddToasts||[])");
        lastAddToasts = t == null ? "[]" : t.toString();
        return "schedule rows " + before + " -> " + after
                + ("[]".equals(lastAddToasts) ? "" : "  | message: " + lastAddToasts);
    }

    /** Fill the whole form on a fresh Add — used by the retry, which must re-enter every field. */
    public String fillAll(int deptIndex, String start, String end) {
        String st = selectFormScheduleType(0);
        String loc = selectLocation();
        String dept = selectDepartment(deptIndex);
        String pay = selectPayable(0);
        String mod = selectModality(0);
        String times = fillTimes(start, end);
        String added = clickInnerAdd();
        return "Schedule Type=" + st + " | Location=" + loc + " | Department=" + dept + " | Payable=" + pay
                + " | Modality=" + mod + " | " + times + " | " + added;
    }

    /** Click <b>Submit</b> and return the toast; screenshots the message the moment it appears. */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("unavailableschedule")
                && resp.url().toLowerCase().contains("iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__usToasts=[]; if(window.__usObs) window.__usObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__usToasts.includes(t)) window.__usToasts.push(t); }); };"
                + " window.__usObs=new MutationObserver(grab); window.__usObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__usSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__usSubmit", "submit");

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
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__usToasts||[]).includes(t)) (window.__usToasts=window.__usToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__usToasts||[])");
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
        Object r = page.evaluate("() => { const a=window.__usToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
