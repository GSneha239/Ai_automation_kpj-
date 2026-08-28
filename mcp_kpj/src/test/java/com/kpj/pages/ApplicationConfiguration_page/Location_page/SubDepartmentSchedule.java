package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Sub Department Schedule</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Sub Department Schedule</b>
 * ({@code #/SubDepartmentScheduleDatewiseList}) → <b>Add</b> ({@code AddSubDepartmentScheduleDatewise()}, opens
 * {@code #/add-SubDepartmentScheduleDatewise}) → <b>Department</b>, <b>Sub Department</b>, <b>Payable</b>,
 * <b>Start Time</b>, <b>End Time</b>, <b>Days</b> → inner <b>Add</b> ({@code fnAddSubDepartmentScheduleDatewise()})
 * → <b>Submit</b> ({@code fnSubmit()}) → success toast.</p>
 *
 * <p><b>There is no Code field anywhere on this form</b> — verified against the live DOM (no input with "code" in
 * its ng-model/name/id) and against the Angular scope object itself
 * ({@code {PayableScheduleDetails, txtStartTime, txtEndTime, FromDate, ToDate, Locationid, Departmentid,
 * SubDepartmentid}}, no code key). If a flow calls for entering a Code here, it does not match this screen; do
 * not invent one.</p>
 *
 * <p>Cascade is THREE deep: <b>Department</b> → <b>Sub Department</b> → <b>Payable</b> (unlike
 * [[devhis-payable-schedule-master]]'s two-deep Department → Payable). A (Department, Sub Department) pair can
 * offer zero Payables — checked live: "Breast & Endocrine Surgery" alone looked empty on a slow read, but with a
 * full ~900ms settle between each cascade step it lists 200+ names. <b>Poll after each cascade step</b> rather
 * than reading immediately, same lesson as [[devhis-payable-schedule-master]]'s Department→Payable cascade.</p>
 *
 * <p><b>Location is a REAL, enabled select here</b> (unlike [[devhis-modality-schedule]]'s hard-disabled proxy) —
 * a normal {@code selectOption} works. <b>Start/End Time reuse the exact same spinner-only {@code uib-timepicker}
 * widget as ModalitySchedule</b> — the visible text box is cosmetic, the row is built from the hours/minutes
 * spinner, and the two fields share one popup (open, set, close before the next). <b>From Date / To Date default
 * to today already filled in</b> — left alone rather than re-typed, since they carry real values before any
 * field is touched.</p>
 */
public class SubDepartmentSchedule extends BasePage {

    public SubDepartmentSchedule(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastLocation = "", lastDepartment = "", lastSubDepartment = "", lastPayable = "";
    public String lastStart = "", lastEnd = "", lastDay = "";
    public String lastAddToasts = "[]", lastToasts = "[]", lastSaveApi = "";
    public int lastDepartmentIndex = -1;
    /** The screen while the message is up — the checks that follow outlive the dialog. */
    public byte[] toastPng;

    // ---- probes ----------------------------------------------------------

    public String findSubDepartmentScheduleLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/sub\\s*department\\s*schedule/i.test(norm(a.textContent)) || /subdepartmentschedule/i.test(a.getAttribute('href')||''))"
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
            System.out.println("SubDepartmentSchedule.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*sub\\s*department\\s*schedule\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/subdepartmentscheduledatewiselist/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__sdsMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__sdsMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("SubDepartmentSchedule.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__sdsMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__sdsMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("SubDepartmentSchedule.nav: menu link not found. links => " + findSubDepartmentScheduleLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("SubDepartmentSchedule.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /sub\\s*department\\s*schedule/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("SubDepartmentSchedule." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("SubDepartmentSchedule." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Click the list screen's <b>Add</b> ({@code AddSubDepartmentScheduleDatewise()}). Polls generously — the
     *  async render can lag well past the route change. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 25 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/^addsubdepartmentscheduledatewise/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__sdsAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(700);
        }
        if (!tagged) { System.out.println("SubDepartmentSchedule.clickAdd: Add button not found"); return false; }
        robustClick("__sdsAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    /** True once the add form (its Department select) is on screen. */
    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('select')]"
                + " .some(s=>/SubDepartmentScheduleDatewise\\.Departmentid/i.test(s.getAttribute('ng-model')||''))"));
    }

    /** Leave the form via <b>Back</b> ({@code closeForm()}). */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__sdsBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__sdsBack", "clickBack");
        waitForAngular(2000);
        return true;
    }

    /** How many real (non-placeholder) options a select currently offers. */
    private int optionCount(String ngModel) {
        Object n = page.evaluate("(m) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return 0;"
                + " return [...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())).length; }", ngModel);
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /**
     * Choose the {@code index}-th real option of the select bound to {@code ngModel}, via a JS
     * {@code selectedIndex} + {@code dispatchEvent('change')} + Angular {@code triggerHandler('change')} —
     * NOT a real Playwright {@code selectOption}.
     *
     * <p>Verified live: a real {@code selectOption} on Department then Sub Department left EVERY one of the 68
     * departments' Payable lists empty — a full scan found zero working (Department, Sub Department) pairs. The
     * exact same cascade driven by this JS-dispatch recipe instead found a working pair (Breast &amp; Endocrine
     * Surgery / Endo, 200+ payables) on the very first department tried. Something about how the real select's
     * change event propagates does not reach whatever loads the dependent Payable list here — unlike the
     * hard-disabled Location proxy on [[devhis-modality-schedule]] where JS-dispatch was needed because
     * {@code selectOption} could not interact with a disabled control at all, THIS select is fully enabled and
     * {@code selectOption} "succeeds" with no exception — it just doesn't trigger the cascade correctly. Do not
     * "simplify" this back to a real select — reintroducing it silently breaks the Sub Department / Payable
     * cascade without throwing anything to catch.</p>
     */
    private String selectNth(String ngModel, int index) {
        for (int w = 0; w < 12 && optionCount(ngModel) == 0; w++) page.waitForTimeout(600);
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

    /** Select the mandatory <b>Location</b> (a real, enabled select on this form). */
    public String selectLocation() { lastLocation = selectNth("SubDepartmentScheduleDatewise.Locationid", 0); return lastLocation; }

    /**
     * Select <b>Department</b>, then the first <b>Sub Department</b> it offers, then the first <b>Payable</b> the
     * pair offers. THREE-deep cascade — a (Department, Sub Department) pair can offer zero Payables, so this
     * walks Department forward until one commits all three (same shape as
     * {@link PayableScheduleMaster#selectDepartmentAndPayable}).
     */
    public String selectCascade(int from) {
        int departments = optionCount("SubDepartmentScheduleDatewise.Departmentid");
        for (int d = from; d < Math.max(1, Math.min(departments, 70)); d++) {
            String dept = selectNth("SubDepartmentScheduleDatewise.Departmentid", d);
            if (dept.isEmpty()) break;
            int subDepts = -1;
            for (int w = 0; w < 12; w++) {
                subDepts = optionCount("SubDepartmentScheduleDatewise.SubDepartmentid");
                if (subDepts > 0) break;
                page.waitForTimeout(500);
            }
            if (subDepts <= 0) { System.out.println("selectCascade: '" + dept + "' offers no Sub Department — next department"); continue; }
            String subDept = selectNth("SubDepartmentScheduleDatewise.SubDepartmentid", 0);
            if (subDept.isEmpty()) continue;
            int payables = -1;
            for (int w = 0; w < 12; w++) {
                payables = optionCount("SubDepartmentScheduleDatewise.Payableid");
                if (payables > 0) break;
                page.waitForTimeout(500);
            }
            if (payables <= 0) { System.out.println("selectCascade: '" + dept + "' / '" + subDept + "' offers no Payable — next department"); continue; }
            String payable = selectNth("SubDepartmentScheduleDatewise.Payableid", 0);
            if (payable.isEmpty()) continue;
            lastDepartment = dept; lastSubDepartment = subDept; lastPayable = payable; lastDepartmentIndex = d;
            return "Department=" + dept + " | Sub Department=" + subDept + " | Payable=" + payable;
        }
        return "Department=(none committed)";
    }

    /**
     * Enter <b>Start Time</b> and <b>End Time</b>. Same {@code uib-timepicker} widget as
     * {@link ModalitySchedule#fillTimes} — the row is built from the hidden spinner (hours/minutes/AM-PM), not
     * the visible text box, and the two fields share one popup so each is opened, set, and closed before the
     * next.
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
                + " const f=boxes[i]; if(!f) return false; f.id='__sdsTimeBox'; f.scrollIntoView({block:'center'}); return true; }", idx));
        if (!tagged) { System.out.println("setTimeWidget: no time box at index " + idx); return ""; }
        try { page.locator("#__sdsTimeBox").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { System.out.println("setTimeWidget: opening the popup failed - " + e.getMessage()); return ""; }
        waitForAngular(300);

        boolean spinnerTagged = Boolean.TRUE.equals(page.evaluate("() => { const hours=[...document.querySelectorAll(\"input[ng-model='hours']\")].filter(e=>e.offsetParent!==null)[0];"
                + " const mins=[...document.querySelectorAll(\"input[ng-model='minutes']\")].filter(e=>e.offsetParent!==null)[0];"
                + " if(!hours || !mins) return false; hours.id='__sdsHourBox'; mins.id='__sdsMinBox'; return true; }"));
        if (!spinnerTagged) { System.out.println("setTimeWidget: spinner did not open for index " + idx); return ""; }
        try {
            page.locator("#__sdsHourBox").fill(hh);
            page.locator("#__sdsMinBox").fill(mm);
        } catch (Exception e) { System.out.println("setTimeWidget: filling the spinner failed - " + e.getMessage()); }
        waitForAngular(300);

        Object cur = page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.offsetParent!==null && /^(am|pm)$/i.test((x.textContent||'').trim()));"
                + " if(b) b.id='__sdsMeridianBox'; return b ? b.textContent.trim().toUpperCase() : ''; }");
        if (!meridian.equals(String.valueOf(cur))) {
            try { page.locator("#__sdsMeridianBox").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(3000)); }
            catch (Exception e) { System.out.println("setTimeWidget: meridian toggle failed - " + e.getMessage()); }
            waitForAngular(300);
        }
        Object v = page.evaluate("() => { const e=document.getElementById('__sdsTimeBox'); const val=e?(e.value||''):'';"
                + " ['__sdsTimeBox','__sdsHourBox','__sdsMinBox','__sdsMeridianBox'].forEach(id=>{ const el=document.getElementById(id); if(el) el.removeAttribute('id'); });"
                + " return val; }");
        return v == null ? "" : v.toString().trim();
    }

    /** Tick ONE <b>Day</b>. All seven boxes share ng-model {@code todo.done}, matched by nearby text. */
    public String tickDay(String day) {
        Object r = page.evaluate("(d) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null && !/row\\.entity/i.test(c.getAttribute('ng-model')||''));"
                + " const cb=cbs.find(c=>new RegExp(d,'i').test(norm((c.closest('label')||c.parentElement||{}).textContent||'')));"
                + " if(!cb) return ''; if(cb.checked) return 'already'; cb.id='__sdsDay'; return 'tag'; }", day);
        String how = String.valueOf(r);
        if (how.isEmpty()) { System.out.println("tickDay: no checkbox for " + day); lastDay = ""; return ""; }
        if ("tag".equals(how)) robustClick("__sdsDay", "tickDay");
        boolean on = Boolean.TRUE.equals(page.evaluate("() => { const c=document.getElementById('__sdsDay');"
                + " const v=!!(c && c.checked); if(c) c.removeAttribute('id'); return v; }"));
        waitForAngular(200);
        lastDay = (on || "already".equals(how)) ? day : "";
        return lastDay;
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

    /** Click the form's inner <b>Add</b> ({@code fnAddSubDepartmentScheduleDatewise()}) — commits the day/time
     *  line, distinct from the list's Add (that one is {@code AddSubDepartmentScheduleDatewise()}, no "fn" prefix). */
    public String clickInnerAdd() {
        int before = scheduleRowCount();
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " window.__sdsAddToasts=[]; if(window.__sdsAddObs) window.__sdsAddObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__sdsAddToasts.includes(t)) window.__sdsAddToasts.push(t); }); };"
                + " window.__sdsAddObs=new MutationObserver(grab); window.__sdsAddObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null && /^fnaddsubdepartmentscheduledatewise/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__sdsAddRow'; b.scrollIntoView({block:'center'}); return true; }"));
        if (!tagged) return "inner Add button not found";
        boolean realClicked = true;
        try { page.locator("#__sdsAddRow").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { realClicked = false; System.out.println("clickInnerAdd: real click intercepted"); }
        waitForAngular(1200);
        int after = scheduleRowCount();
        if (after == before && !realClicked) {
            System.out.println("clickInnerAdd: DOM click fallback");
            page.evaluate("() => { const e=document.getElementById('__sdsAddRow'); if(e) e.click(); }");
            waitForAngular(1200);
            after = scheduleRowCount();
        }
        page.evaluate("() => { const e=document.getElementById('__sdsAddRow'); if(e) e.removeAttribute('id'); }");
        Object t = page.evaluate("() => JSON.stringify(window.__sdsAddToasts||[])");
        lastAddToasts = t == null ? "[]" : t.toString();
        return "schedule rows " + before + " -> " + after
                + ("[]".equals(lastAddToasts) ? "" : "  | message: " + lastAddToasts);
    }

    /** Fill the whole form on a fresh Add — used by the retry, which must re-enter every field. */
    public String fillAll(int deptFrom, String start, String end, String day) {
        String loc = selectLocation();
        String cascade = selectCascade(deptFrom);
        String times = fillTimes(start, end);
        String d = tickDay(day);
        String added = clickInnerAdd();
        return "Location=" + loc + " | " + cascade + " | " + times + " | Day=" + d + " | " + added;
    }

    /** Click <b>Submit</b> ({@code fnSubmit()}) and return the toast; screenshots the message the moment it
     *  appears. */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("subdepartmentschedule")
                && resp.url().toLowerCase().contains("iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__sdsToasts=[]; if(window.__sdsObs) window.__sdsObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__sdsToasts.includes(t)) window.__sdsToasts.push(t); }); };"
                + " window.__sdsObs=new MutationObserver(grab); window.__sdsObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__sdsSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__sdsSubmit", "submit");

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
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__sdsToasts||[]).includes(t)) (window.__sdsToasts=window.__sdsToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__sdsToasts||[])");
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
        Object r = page.evaluate("() => { const a=window.__sdsToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
