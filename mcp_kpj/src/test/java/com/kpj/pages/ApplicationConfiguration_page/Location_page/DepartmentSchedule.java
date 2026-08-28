package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Department Schedule</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Department Schedule</b> → <b>Add</b> →
 * <b>Department</b>, <b>Start Time</b>, <b>End Time</b>, <b>Days</b> → inner <b>Add</b> → <b>Submit</b> →
 * success toast.</p>
 *
 * <p>Sibling of {@link PayableScheduleMaster} and wired the same way: the times are timepicker SPINNERS (not the
 * text box), the days come from one ng-repeat, and a retry re-opens the form so a stale schedule id cannot turn
 * Submit into an update of live configuration.</p>
 */
public class DepartmentSchedule extends BasePage {

    public DepartmentSchedule(Page page) { super(page); }

    /** Route — mined from the menu at runtime. */
    public static String ROUTE = "";

    public String lastDepartment = "", lastStart = "", lastEnd = "", lastDays = "";
    public String lastAddToasts = "[]", lastToasts = "[]", lastSaveApi = "";
    public int lastDepartmentIndex = -1;
    /** The screen while the message is up — the checks that follow outlive the dialog. */
    public byte[] toastPng;

    // ---- probes ----------------------------------------------------------

    /** Every anchor mentioning "department"/"schedule", with the submenu it sits under. */
    public String findScheduleLinks() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const parentOf=a=>{ let p=a.parentElement, hop=0;"
                + "   while(p && hop++<6){ const t=p.previousElementSibling; if(t && /^A$/i.test(t.tagName)) return norm(t.textContent).slice(0,24);"
                + "     const anc=p.parentElement && p.parentElement.querySelector(':scope > a'); if(anc && anc!==a) return norm(anc.textContent).slice(0,24); p=p.parentElement; }"
                + "   return '?'; };"
                + " return [...document.querySelectorAll('a[href]')].filter(a=>/schedule/i.test(norm(a.textContent)) || /schedule/i.test(a.getAttribute('href')||''))"
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
            System.out.println("DepartmentSchedule.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*department\\s*schedule\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want)"
                + "   || [...document.querySelectorAll('a[href]')].find(x=>/departmentschedule/i.test(x.getAttribute('href')||''));"
                + " if(!a) return ''; a.id='__dsMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__dsMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("DepartmentSchedule.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__dsMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__dsMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("DepartmentSchedule.nav: menu link not found. schedule links => " + findScheduleLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("DepartmentSchedule.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /department\\s*schedule/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    /** Real click with a DOM-click fallback for elements the sticky header overlays. */
    private void robustClick(String id, String what) {
        page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.scrollIntoView({block:'center'}); }", id);
        try { page.locator("#" + id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); return; }
        catch (Exception e) { System.out.println("DepartmentSchedule." + what + ": real click intercepted — DOM click fallback"); }
        try { page.evaluate("(i) => { const e=document.getElementById(i); if(e) e.click(); }", id); }
        catch (Exception e) { System.out.println("DepartmentSchedule." + what + ": DOM click failed - " + e.getMessage()); }
    }

    /** Click the list screen's <b>Add</b>. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=[...document.querySelectorAll('button,a,input[type=button],span,i,[ng-click]')].filter(x=>x.offsetParent!==null);"
                    + " const b=vis.find(x=>/add.*schedule|addschedule|adddept/i.test(x.getAttribute('ng-click')||''))"
                    + "   || vis.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__dsAdd'; b.scrollIntoView({block:'center'}); return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("DepartmentSchedule.clickAdd: Add button not found"); return false; }
        robustClick("__dsAdd", "clickAdd");
        waitForAngular(1800);
        return true;
    }

    /** True once the add form (its Department select) is on screen. */
    public boolean addFormOpen() {
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('select')]"
                + " .some(s=>/deptScheduleInfo\\.Departmentid/i.test(s.getAttribute('ng-model')||''))"));
    }

    /**
     * The schedule id the form holds. 0 on a FRESH Add form; once the app has loaded an existing schedule (or a
     * save has happened) it stays set and Submit UPDATES that schedule instead of adding one — so a retry has to
     * re-open the form. Same trap as {@link PayableScheduleMaster}.
     */
    public int scheduleId() {
        Object r = page.evaluate("() => { const A=window.angular;"
                + " const s=[...document.querySelectorAll('select')].find(x=>/deptScheduleInfo\\.Departmentid/i.test(x.getAttribute('ng-model')||''));"
                + " if(!s) return -1; try { const sc=A.element(s).scope(); const info=sc && sc.deptScheduleInfo; if(!info) return -1;"
                + "   const k=Object.keys(info).find(x=>/scheduleid$/i.test(x)); return k ? (info[k]||0) : 0; } catch(e){ return -1; } }");
        try { return (int) Double.parseDouble(String.valueOf(r)); } catch (Exception e) { return -1; }
    }

    /** Choose the {@code index}-th real option of a select (0 = first non-placeholder). */
    private String selectNth(String ngModel, int index) {
        Object idx = page.evaluate("([m,n]) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + " if(!s) return -1; s.id='__dsSel';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(!real.length) return -1; return real[Math.min(n, real.length-1)].i; }",
                java.util.Arrays.asList(ngModel, index));
        int i;
        try { i = (int) Double.parseDouble(String.valueOf(idx)); } catch (Exception e) { i = -1; }
        if (i < 0) { page.evaluate("() => { const e=document.getElementById('__dsSel'); if(e) e.removeAttribute('id'); }"); return ""; }
        try {
            page.locator("#__dsSel").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("selectNth(" + ngModel + "): real select failed - " + e.getMessage());
            page.evaluate("(k) => { const e=document.getElementById('__dsSel'); if(!e) return; const A=window.angular;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", i);
        }
        waitForAngular(800);
        Object t = page.evaluate("() => { const e=document.getElementById('__dsSel');"
                + " const v=(e && e.selectedIndex>=0)?(e.options[e.selectedIndex].textContent||'').trim():'';"
                + " if(e) e.removeAttribute('id'); return v; }");
        return t == null ? "" : t.toString();
    }

    /** Select the mandatory <b>Location</b> (the flow does not name it, but Submit needs it). */
    public String selectLocation() { return selectNth("deptScheduleInfo.Locationid", 0); }

    /** Select the {@code index}-th <b>Department</b>. */
    public String selectDepartment(int index) {
        lastDepartment = selectNth("deptScheduleInfo.Departmentid", index);
        if (!lastDepartment.isEmpty()) lastDepartmentIndex = index;
        return lastDepartment;
    }

    /**
     * Enter <b>Start Time</b> and <b>End Time</b>. Each field is a timepicker: a text box (ng-model
     * {@code inputTime}) plus <b>hours</b>/<b>minutes</b> spinners and an AM/PM button — and it is the SPINNERS
     * the schedule row is built from, so both are set and the spinners are re-asserted afterwards.
     */
    public String fillTimes(String start, String end) {
        setTimeWidget(0, start);
        setTimeWidget(1, end);
        lastStart = typeTime(0, start);
        lastEnd = typeTime(1, end);
        setTimeWidget(0, start);
        setTimeWidget(1, end);
        return "Start Time=" + (lastStart.isEmpty() ? "(not set)" : lastStart)
                + " | End Time=" + (lastEnd.isEmpty() ? "(not set)" : lastEnd) + " | widgets: " + readTimeWidgets();
    }

    /** Set the {@code idx}-th timepicker (0 = Start, 1 = End) from a "hh:mm AM/PM" string. */
    private void setTimeWidget(int idx, String hhmm) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?").matcher(hhmm);
        if (!m.find()) { System.out.println("setTimeWidget: cannot parse '" + hhmm + "'"); return; }
        page.evaluate("([i,hh,mm,mer]) => { const A=window.angular;"
                + " const hours=[...document.querySelectorAll(\"input[ng-model='hours']\")].filter(e=>e.offsetParent!==null);"
                + " const mins=[...document.querySelectorAll(\"input[ng-model='minutes']\")].filter(e=>e.offsetParent!==null);"
                + " const set=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v;"
                + "   if(c){ c.$setViewValue(v); c.$render(); }"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true})); };"
                + " set(hours[i], hh); set(mins[i], mm);"
                + " if(mer && hours[i]){ let p=hours[i].parentElement, hop=0, btn=null;"
                + "   while(p && hop++<6 && !btn){ btn=[...p.querySelectorAll('button,a')].find(b=>/^(am|pm)$/i.test((b.textContent||'').trim())); p=p.parentElement; }"
                + "   if(btn && (btn.textContent||'').trim().toUpperCase()!==mer) btn.click(); } }",
                java.util.Arrays.asList(String.valueOf(idx), m.group(1), m.group(2),
                        m.group(3) == null ? "" : m.group(3).toUpperCase()));
        waitForAngular(400);
    }

    /** Type into the {@code idx}-th time text box (0 = Start, 1 = End); returns the value it kept. */
    private String typeTime(int idx, String value) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("(i) => {"
                + " const boxes=[...document.querySelectorAll(\"input[ng-model='inputTime']\")].filter(e=>e.offsetParent!==null);"
                + " const f=boxes[i]; if(!f) return false; f.id='__dsTime'; f.scrollIntoView({block:'center'}); return true; }", idx));
        if (!tagged) { System.out.println("typeTime: no time box at index " + idx); return ""; }
        try {
            page.locator("#__dsTime").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            page.locator("#__dsTime").fill("");
            page.locator("#__dsTime").type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(90));
            page.locator("#__dsTime").press("Tab");
        } catch (Exception e) { System.out.println("typeTime(" + idx + "): typing failed - " + e.getMessage()); }
        waitForAngular(500);
        Object v = page.evaluate("() => { const e=document.getElementById('__dsTime'); const val=e?(e.value||''):'';"
                + " if(e) e.removeAttribute('id'); return val; }");
        return v == null ? "" : v.toString().trim();
    }

    /** What the two timepickers hold — straight off the spinners. */
    private String readTimeWidgets() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hours=[...document.querySelectorAll(\"input[ng-model='hours']\")].filter(e=>e.offsetParent!==null);"
                + " const mins=[...document.querySelectorAll(\"input[ng-model='minutes']\")].filter(e=>e.offsetParent!==null);"
                + " return hours.map((h,i)=>{ let p=h.parentElement, hop=0, btn=null;"
                + "   while(p && hop++<6 && !btn){ btn=[...p.querySelectorAll('button,a')].find(b=>/^(am|pm)$/i.test((b.textContent||'').trim())); p=p.parentElement; }"
                + "   return (h.value||'?')+':'+((mins[i]||{}).value||'?')+' '+(btn?norm(btn.textContent):''); }).join(' / '); }");
        return r == null ? "" : r.toString();
    }

    /** Tick the <b>Days</b> — all seven boxes share ng-model {@code todo.done}, so each is matched on its own text. */
    public String tickDays(String... wanted) {
        StringBuilder ticked = new StringBuilder();
        for (String day : wanted) {
            Object r = page.evaluate("(day) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null && !/row\\.entity/i.test(c.getAttribute('ng-model')||''));"
                    + " const cb=cbs.find(c=>new RegExp(day,'i').test(norm((c.closest('label')||c.parentElement||{}).textContent||'')));"
                    + " if(!cb) return ''; if(cb.checked) return 'already'; cb.id='__dsDay'; return 'tag'; }", day);
            String how = String.valueOf(r);
            if (how.isEmpty()) { System.out.println("tickDays: no checkbox for " + day); continue; }
            if ("tag".equals(how)) robustClick("__dsDay", "tickDays");
            boolean on = Boolean.TRUE.equals(page.evaluate("() => { const c=document.getElementById('__dsDay');"
                    + " const v=!!(c && c.checked); if(c) c.removeAttribute('id'); return v; }"));
            if (on || "already".equals(how)) ticked.append(ticked.length() == 0 ? "" : ", ").append(day);
            waitForAngular(200);
        }
        lastDays = ticked.toString();
        return lastDays;
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
                + " return [...tbl.querySelectorAll('tbody tr')].map(r=>norm(r.textContent).slice(0,60)).filter(t=>t).slice(0,8).join(' ;; '); }");
        return r == null ? "" : r.toString();
    }

    /** Click the form's <b>Add</b> ({@code fnAddDeptSchedule()}) — exactly once, and report any message it raised. */
    public String clickInnerAdd() {
        int before = scheduleRowCount();
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " window.__dsAddToasts=[]; if(window.__dsAddObs) window.__dsAddObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dsAddToasts.includes(t)) window.__dsAddToasts.push(t); }); };"
                + " window.__dsAddObs=new MutationObserver(grab); window.__dsAddObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/fnadddeptschedule/i.test(x.getAttribute('ng-click')||'') || /^\\s*add\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__dsAddRow'; b.scrollIntoView({block:'center'}); return true; }"));
        if (!tagged) return "inner Add button not found";
        boolean realClicked = true;
        try { page.locator("#__dsAddRow").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); }
        catch (Exception e) { realClicked = false; System.out.println("clickInnerAdd: real click intercepted"); }
        waitForAngular(1200);
        int after = scheduleRowCount();
        if (after == before && !realClicked) {
            System.out.println("clickInnerAdd: DOM click fallback");
            page.evaluate("() => { const e=document.getElementById('__dsAddRow'); if(e) e.click(); }");
            waitForAngular(1200);
            after = scheduleRowCount();
        }
        page.evaluate("() => { const e=document.getElementById('__dsAddRow'); if(e) e.removeAttribute('id'); }");
        Object t = page.evaluate("() => JSON.stringify(window.__dsAddToasts||[])");
        lastAddToasts = t == null ? "[]" : t.toString();
        return "schedule rows " + before + " -> " + after
                + ("[]".equals(lastAddToasts) ? "" : "  | message: " + lastAddToasts);
    }

    /** Leave the form via <b>Back</b> so a retry starts from a fresh (schedule-id-free) form. */
    public boolean clickBack() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>x.offsetParent!==null"
                + "   && (/closeform/i.test(x.getAttribute('ng-click')||'') || /^\\s*back\\s*$/i.test(norm(x.textContent||x.value))));"
                + " if(!b) return false; b.id='__dsBack'; return true; }"));
        if (!tagged) return false;
        robustClick("__dsBack", "clickBack");
        waitForAngular(2000);
        return true;
    }

    /** Fill the whole form on a fresh Add — used by the retry, which must re-enter every field. */
    public String fillAll(int deptIndex, String start, String end, String... days) {
        String loc = selectLocation();
        String dept = selectDepartment(deptIndex);
        String times = fillTimes(start, end);
        String d = tickDays(days);
        String added = clickInnerAdd();
        return "Location=" + loc + " | Department=" + dept + " | " + times + " | Days=" + d + " | " + added;
    }

    /** Click <b>Submit</b> and return the toast; screenshots the message the moment it appears. */
    public String submitAndGetToast() {
        com.microsoft.playwright.Response[] hold = new com.microsoft.playwright.Response[1];
        page.onResponse(resp -> { if (resp.url().toLowerCase().contains("deptschedule/iud")
                || resp.url().toLowerCase().contains("departmentschedule/iud")) hold[0] = resp; });

        Object tagged = page.evaluate("() => { window.__dsToasts=[]; if(window.__dsObs) window.__dsObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dsToasts.includes(t)) window.__dsToasts.push(t); }); };"
                + " window.__dsObs=new MutationObserver(grab); window.__dsObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()));"
                + " if(!b) return false; b.id='__dsSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        robustClick("__dsSubmit", "submit");

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
        page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__dsToasts||[]).includes(t)) (window.__dsToasts=window.__dsToasts||[]).push(t); }); }");
        Object all = page.evaluate("() => JSON.stringify(window.__dsToasts||[])");
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
        Object r = page.evaluate("() => { const a=window.__dsToasts||[]; return a.find(x=>/saved|success|added|updated/i.test(x))"
                + " || a.find(x=>/exist|error|please|select|enter|required|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
