package com.kpj.pages.AncillaryServices_page.DoctorRoster_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Doctor Roster &gt; <b>Consultant On Call</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Doctor Roster</b> → <b>ConsultantOnCall</b>
 * ({@code #/ConsultantOnCall}, a filter/list screen) → <b>NewRoster</b> ({@code fnGoToNewRoster()}) →
 * the roster form ({@code #/add-ConsultantOnCall}) → <b>From Date</b> / <b>To Date</b> / <b>Department</b>
 * → <b>Doctor</b> / <b>Duty</b> / <b>Consultation Room</b> → <b>Add</b> ({@code fnAddRow()}) →
 * <b>Save</b> ({@code fnSaveRoster()}) → popup + toast.</p>
 *
 * <p><b>Fields (discovered live 2026-08-06):</b> list screen — {@code filter.DepartmentID},
 * {@code filter.DoctorID}, {@code filter.WeekDate}, buttons {@code fnGoToNewRoster()} and
 * {@code fnGoToCalendar()}. Form — {@code form.FromDate} and {@code form.ToDate} (native
 * {@code type=date}), {@code form.DepartmentID}, {@code form.DoctorID}, {@code form.ConsultationRoomID}
 * (selects), plus a <b>ui-select</b> typeahead for Duty; buttons {@code fnAddRow()} (Add),
 * {@code fnSaveRoster()} (Save), {@code fnBack()} (Back).</p>
 *
 * <p><b>Two widgets this screen uses that the rest of the suite does not.</b></p>
 * <ul>
 *   <li><b>Native date inputs.</b> {@code FromDate}/{@code ToDate} are {@code <input type="date">}, so the
 *       value must be written as {@code yyyy-MM-dd} and left for Angular's own date directive to parse —
 *       calling {@code $setViewValue} with a string writes a string where a {@code Date} is expected.</li>
 *   <li><b>Duty is an AngularJS ui-select</b>, not a {@code <select>}: there is no element to set
 *       {@code selectedIndex} on. It is opened by clicking its match/toggle, then a
 *       {@code .ui-select-choices-row} is clicked once the list renders.</li>
 * </ul>
 *
 * <p><b>Login note.</b> Needs a session with a navigation menu — log in with an outpatient counter,
 * e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class ConsultantOnCall extends BasePage {

    public ConsultantOnCall(Page page) { super(page); }

    public static final String LIST_ROUTE = "#/ConsultantOnCall";
    public static final String ADD_ROUTE = "#/add-ConsultantOnCall";

    public String lastControls = "";
    public String lastFromDate = "", lastToDate = "", lastDepartment = "";
    public String lastDoctor = "", lastDuty = "", lastRoom = "";
    public String lastPopup = "";

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
            // native <input type=date>: write yyyy-MM-dd and let Angular's date directive parse it —
            // $setViewValue with a string would put a string where the model expects a Date.
            + "const setDate=(ng,v)=>{ const e=byNg(ng); if(!e) return '(no-field)';"
            + "  e.value=v; e.dispatchEvent(new Event('input',{bubbles:true}));"
            + "  e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "  return e.value||v; };";

    private static final String ARM_TOASTS = ""
            + " window.__cocToasts=[]; if(window.__cocObs) window.__cocObs.disconnect();"
            + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
            + "   const t=norm(el.textContent); if(t && !window.__cocToasts.includes(t)) window.__cocToasts.push(t); }); };"
            + " window.__cocObs=new MutationObserver(grab); window.__cocObs.observe(document.body,{childList:true,subtree:true}); grab();";

    // ---- navigation --------------------------------------------------------

    /** <b>Ancillary Services</b> → <b>Doctor Roster</b> → <b>ConsultantOnCall</b>, else the direct route. */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            System.out.println("ConsultantOnCall.nav: nav menu never appeared — is this session on an OPD counter?");
        }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/ancillary\\s*services?/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*doctor\\s*roster\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1000);

        // The menu label is one word, "ConsultantOnCall" — allow optional spaces either way.
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const links=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null);"
                + " const a=links.find(x=>/^consultant\\s*on\\s*call$/i.test(norm(x.textContent)))"
                + "   || links.find(x=>/#\\/ConsultantOnCall/i.test(x.getAttribute('href')||''));"
                + " if(!a) return false; a.id='__cocMenu'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__cocMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("ConsultantOnCall.nav: menu click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__cocMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(1500);
        }
        if (!onListScreen()) {
            System.out.println("ConsultantOnCall.nav: falling back to direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1800);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/fnGoToNewRoster/i.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("ConsultantOnCall.nav: list screen did not finish rendering"); }
        return onListScreen();
    }

    public boolean onListScreen() {
        String url = page.url().toLowerCase();
        return url.contains("consultantoncall") && !url.contains("add-");
    }

    public boolean onRosterForm() {
        if (page.url().toLowerCase().contains("add-consultantoncall")) return true;
        return Boolean.TRUE.equals(page.evaluate(
                "() => !![...document.querySelectorAll(\"[ng-model='form.FromDate']\")].find(e=>e.offsetParent!==null)"));
    }

    /**
     * Click <b>New Roster</b> ({@code fnGoToNewRoster()}) and wait for the form.
     *
     * <p>Polled — the button renders after the list screen's filters settle.</p>
     */
    public boolean clickNewRoster() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/fnGoToNewRoster/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*new\\s*roster\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__cocNew'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("ConsultantOnCall.clickNewRoster: New Roster button not found"); return false; }
        try { page.locator("#__cocNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ConsultantOnCall.clickNewRoster: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cocNew'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='form.FromDate']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("ConsultantOnCall.clickNewRoster: roster form did not render"); }
        waitForAngular(1200);
        return onRosterForm();
    }

    /** Diagnostics: selects, inputs AND ui-select widgets with their labels. Never fails the flow. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||''); };"
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const uis=[...document.querySelectorAll('.ui-select-container,[ui-select],ui-select')].filter(vis)"
                + "   .map(e=>'UISELECT \"'+labelOf(e)+'\" [ng-model='+(e.getAttribute('ng-model')||'?')+'] text=\"'+norm(e.textContent).slice(0,40)+'\"');"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] type='+e.type);"
                + " return sel.concat(uis).concat(inp).join(' || '); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("ConsultantOnCall controls: " + lastControls);
        return lastControls;
    }

    // ---- step 1: dates + department ----------------------------------------

    /**
     * Set <b>From Date</b> / <b>To Date</b> and select <b>Department</b>.
     *
     * @param fromDate {@code yyyy-MM-dd}
     * @param toDate   {@code yyyy-MM-dd}
     */
    public String selectDatesAndDepartment(String fromDate, String toDate) {
        Object r = page.evaluate("(args) => new Promise(async resolve => {" + JS
                + " const [from, to] = args;"
                + " const f=setDate('form.FromDate', from); const t=setDate('form.ToDate', to);"
                + " await sleep(400);"
                + " const d=await pick('form.DepartmentID');"
                + " resolve('FromDate='+f+' | ToDate='+t+' | Department='+d); })",
                java.util.List.of(fromDate, toDate));
        String out = r == null ? "" : r.toString();
        for (String part : out.split("\\|")) {
            String p = part.trim();
            if (p.startsWith("FromDate=")) lastFromDate = p.substring(9).trim();
            else if (p.startsWith("ToDate=")) lastToDate = p.substring(7).trim();
            else if (p.startsWith("Department=")) lastDepartment = p.substring(11).trim();
        }
        waitForAngular(1500);   // Department cascades into the Doctor list
        System.out.println("ConsultantOnCall: " + out);
        return out;
    }

    public boolean datesAndDepartmentSet() {
        return isReal(lastFromDate) && isReal(lastToDate) && isReal(lastDepartment);
    }

    // ---- step 2: doctor + duty + consultation room -------------------------

    /**
     * Select <b>Doctor</b>, <b>Duty</b> and <b>Consultation Room</b>.
     *
     * <p>Doctor is polled because its list loads off the back of Department. <b>Duty</b> is an
     * AngularJS <b>ui-select</b>, so it is opened by clicking its match/toggle and then choosing a
     * {@code .ui-select-choices-row} — there is no {@code <select>} to set.</p>
     */
    public String selectDoctorDutyAndRoom() {
        Object doc = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('form.DoctorID')); })");
        lastDoctor = doc == null ? "" : doc.toString();
        waitForAngular(600);

        lastDuty = selectDuty();
        waitForAngular(400);

        Object room = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('form.ConsultationRoomID')); })");
        lastRoom = room == null ? "" : room.toString();
        waitForAngular(400);

        String out = "Doctor=" + lastDoctor + " | Duty=" + lastDuty + " | ConsultationRoom=" + lastRoom;
        System.out.println("ConsultantOnCall: " + out);
        return out;
    }

    /**
     * Choose a value in the <b>Duty</b> ui-select: click its toggle, wait for the choices list to render,
     * then click the first real row.
     *
     * @return the chosen text, or {@code (no-uiselect)} / {@code (no-choices)}
     */
    private String selectDuty() {
        // Open the ui-select — prefer the one labelled Duty, else the only one on the form. The toggle is
        // clicked via a real Playwright click (not el.click()) so Angular's focus/open handlers all run.
        Object opened = page.evaluate("() => {" + JS
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
                + "   return norm(gl?gl.textContent:''); };"
                + " const conts=[...document.querySelectorAll('.ui-select-container,[ui-select],ui-select')].filter(vis);"
                + " if(!conts.length) return '';"
                + " const c=conts.find(x=>/duty/i.test(labelOf(x))) || conts[0];"
                + " c.setAttribute('data-coc','duty');"
                + " const t=c.querySelector('.ui-select-toggle,.ui-select-match')||c.querySelector('input');"
                + " if(t) t.setAttribute('data-coc','dutyToggle');"
                + " return labelOf(c)||'(unlabelled ui-select)'; }");
        if (opened == null || opened.toString().isEmpty()) {
            System.out.println("ConsultantOnCall.selectDuty: no ui-select found for Duty");
            return "(no-uiselect)";
        }
        // This ui-select is a TYPEAHEAD: its choices do not render on open, only once the search box has
        // received input. So click the search box (which also opens the widget) and type, rather than
        // clicking the match/toggle and waiting for a list that never comes.
        String chosen = null;
        try {
            com.microsoft.playwright.Locator search = page.locator("[data-coc='duty'] input").first();
            search.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            waitForAngular(500);
            chosen = pickDutyChoice();
            if (chosen != null) return chosen;

            search.type("a");
            waitForAngular(900);
            chosen = pickDutyChoice();
            if (chosen != null) return chosen;

            search.fill("");            // clearing usually reveals the full list
            waitForAngular(900);
            chosen = pickDutyChoice();
            if (chosen != null) return chosen;
        } catch (Exception e) {
            System.out.println("ConsultantOnCall.selectDuty: typeahead attempt failed - " + e.getMessage());
        }

        // Last resort: the match/toggle element.
        try {
            page.locator("[data-coc='dutyToggle']").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            waitForAngular(800);
            chosen = pickDutyChoice();
            if (chosen != null) return chosen;
        } catch (Exception e) {
            System.out.println("ConsultantOnCall.selectDuty: toggle click failed - " + e.getMessage());
        }

        // Still nothing — dump the widget so the report explains WHY rather than just "(no-choices)".
        Object dump = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=document.querySelector(\"[data-coc='duty']\");"
                + " const rowsAll=document.querySelectorAll('.ui-select-choices-row').length;"
                + " const dd=document.querySelectorAll('.ui-select-choices,.ui-select-dropdown').length;"
                + " return 'choicesRowsInDoc='+rowsAll+' dropdowns='+dd"
                + "   +' | html='+(c?norm(c.outerHTML).slice(0,300):'(container gone)'); }");
        System.out.println("ConsultantOnCall.selectDuty: no choices rendered. " + dump);
        return "(no-choices)";
    }

    /** Click the first real ui-select choice if the list is rendered; null when it is not. */
    private String pickDutyChoice() {
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
                System.out.println("ConsultantOnCall: Duty = " + chosen);
                return chosen.toString();
            }
            page.waitForTimeout(400);
        }
        return null;
    }

    public boolean doctorDutyRoomSelected() {
        return isReal(lastDoctor) && isReal(lastDuty) && isReal(lastRoom);
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
                + " if(!b) return false; b.id='__cocAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("ConsultantOnCall.clickAdd: Add button not found"); return "(no Add button)"; }
        try { page.locator("#__cocAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ConsultantOnCall.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cocAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1200);

        Object r = page.evaluate("(rowsBefore) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=document.querySelectorAll('table tbody tr, .ui-grid-row').length;"
                + " const msg=[...document.querySelectorAll('.toast-message,.toast')].map(x=>norm(x.textContent)).join(' ');"
                + " return 'rowsAdded='+(rows-rowsBefore)+' total='+rows+(msg?' | msg='+msg:''); }", rowsBefore);
        String out = r == null ? "" : r.toString();
        System.out.println("ConsultantOnCall: Add -> " + out);
        return out;
    }

    // ---- step 4: save + popup + toast --------------------------------------

    /**
     * Click <b>Save</b> ({@code fnSaveRoster()}), capture any popup it raises, accept it, and return the
     * toast. The popup text is left in {@link #lastPopup}.
     */
    public String saveAndGetToast() {
        waitForToastsToClear();

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + ARM_TOASTS
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/fnSaveRoster/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__cocSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("ConsultantOnCall.save: Save button not found"); return ""; }

        try { page.locator("#__cocSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ConsultantOnCall.save: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cocSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);

        // Capture the confirmation popup BEFORE accepting it — once accepted its text is gone.
        Object popup = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const d=[...document.querySelectorAll('.modal,.modal-dialog,.jAlert,[role=dialog],.sweet-alert,.ui-dialog')]"
                + "   .filter(e=>e.offsetParent!==null).map(e=>norm(e.textContent)).filter(t=>t);"
                + " return d.length ? d[0].slice(0,200) : ''; }");
        lastPopup = popup == null ? "" : popup.toString();
        if (!lastPopup.isEmpty()) System.out.println("ConsultantOnCall: popup -> " + lastPopup);

        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__cocToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + " const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                    + " if(t && !(window.__cocToasts||[]).includes(t)) (window.__cocToasts=window.__cocToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__cocToasts||[];"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- roster calendar + print ------------------------------------------

    /** The calendar view opened by the list screen's <b>RosterCalendar</b> button. */
    public static final String CALENDAR_ROUTE = "#/calendar-ConsultantOnCall";

    /** Text of the popup raised by Print, and whether it closed after Cancel. */
    public String lastPrintPopup = "";

    /** True on the roster calendar view ({@code #/calendar-ConsultantOnCall}). */
    public boolean onCalendar() {
        if (page.url().toLowerCase().contains("calendar-consultantoncall")) return true;
        return Boolean.TRUE.equals(page.evaluate(
                "() => !![...document.querySelectorAll(\"[ng-model='calFilter.Month']\")].find(e=>e.offsetParent!==null)"));
    }

    /** Click <b>Roster Calendar</b> ({@code fnGoToCalendar()}) and wait for the calendar to render. */
    public boolean clickRosterCalendar() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/fnGoToCalendar/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*roster\\s*calendar\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__cocCal'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("ConsultantOnCall.clickRosterCalendar: RosterCalendar button not found"); return false; }
        try { page.locator("#__cocCal").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ConsultantOnCall.clickRosterCalendar: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cocCal'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => !![...document.querySelectorAll(\"[ng-model='calFilter.Month']\")].find(e=>e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
        } catch (Exception ignore) { System.out.println("ConsultantOnCall.clickRosterCalendar: calendar did not render"); }
        waitForAngular(1200);
        return onCalendar();
    }

    /** Select a Department on the calendar ({@code calFilter.DepartmentID}) so the grid populates. */
    public String selectCalendarDepartment() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " resolve(await pick('calFilter.DepartmentID')); })");
        waitForAngular(1500);
        String out = r == null ? "" : r.toString();
        System.out.println("ConsultantOnCall: calendar Department = " + out);
        return out;
    }

    /**
     * Diagnostics for the calendar: EVERY clickable carrying an ng-click (any tag, so icon
     * {@code <i>}/{@code <span>}/{@code <a>} print symbols are included) plus anything that merely looks
     * print-related by class/title. The plain button dump misses icon controls entirely.
     */
    public String describeCalendarControls() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>e && e.offsetParent!==null;"
                + " const clickable=[...document.querySelectorAll('[ng-click]')].filter(vis)"
                + "   .map(e=>e.tagName+' \"'+norm(e.textContent||e.value).slice(0,25)+'\" [ng-click='+e.getAttribute('ng-click')+']');"
                + " const printish=[...document.querySelectorAll('i,span,a,button,img')].filter(vis)"
                + "   .filter(e=>/print/i.test((e.className||'')+' '+(e.getAttribute('title')||'')+' '+(e.getAttribute('ng-click')||'')))"
                + "   .map(e=>'PRINTISH '+e.tagName+' class='+(e.className||'-')+' title='+(e.getAttribute('title')||'-')"
                + "       +' ng-click='+(e.getAttribute('ng-click')||'-'));"
                + " return clickable.concat(printish).join(' || '); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("ConsultantOnCall calendar controls: " + lastControls);
        return lastControls;
    }

    /**
     * Click the <b>print</b> symbol on the calendar.
     *
     * <p>Matched across ALL tags (not just {@code <button>}) by {@code ng-click} containing "print", or a
     * class/title that names print — the control is an icon, so a button-only search finds nothing.</p>
     *
     * @return a description of the element clicked, or "" when none was found
     */
    public String clickPrint() {
        for (int i = 0; i < 20; i++) {
            Object found = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>e && e.offsetParent!==null;"
                    + " const c=[...document.querySelectorAll('button,a,i,span,img,input[type=button]')].filter(vis);"
                    + " const b=c.find(x=>/print/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/(^|[^a-z])print/i.test(x.getAttribute('title')||''))"
                    + "   || c.find(x=>/fa-print|glyphicon-print|print/i.test(x.className||''))"
                    + "   || c.find(x=>/^\\s*print\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return ''; b.id='__cocPrint';"
                    + " return b.tagName+' \"'+norm(b.textContent||b.value).slice(0,25)+'\" class='+(b.className||'-')"
                    + "   +' ng-click='+(b.getAttribute('ng-click')||'-'); }");
            String what = found == null ? "" : found.toString();
            if (!what.isEmpty()) {
                try { page.locator("#__cocPrint").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("ConsultantOnCall.clickPrint: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__cocPrint'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2000);
                System.out.println("ConsultantOnCall: Print -> " + what);
                return what;
            }
            page.waitForTimeout(500);
        }
        System.out.println("ConsultantOnCall.clickPrint: no print control found. " + describeCalendarControls());
        return "";
    }

    /**
     * Capture whatever Print raised: an in-page modal, or a new tab (DevHIS opens its SQLReport
     * {@code .aspx} outputs in one).
     *
     * <p>Note a NATIVE Chromium print dialog cannot be seen or dismissed by Playwright — it is browser
     * chrome, not DOM. If nothing is found here, that is the likely explanation, and the browser is
     * launched with {@code --kiosk-printing}, which suppresses that dialog anyway.</p>
     */
    public String capturePrintPopup() {
        for (int i = 0; i < 12; i++) {
            // A new tab counts as the "print popup" — that is how DevHIS shows its reports.
            for (Page p : page.context().pages()) {
                if (p != page && !p.isClosed()) {
                    String u = p.url();
                    if (u != null && !u.isBlank() && !"about:blank".equals(u)) {
                        lastPrintPopup = "new tab: " + u;
                        System.out.println("ConsultantOnCall: print popup -> " + lastPrintPopup);
                        return lastPrintPopup;
                    }
                }
            }
            Object modal = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const d=[...document.querySelectorAll('.modal,.modal-dialog,.jAlert,[role=dialog],.sweet-alert,.ui-dialog,.print-preview')]"
                    + "   .filter(e=>e.offsetParent!==null).map(e=>norm(e.textContent)).filter(t=>t);"
                    + " return d.length ? d[0].slice(0,200) : ''; }");
            if (modal != null && !modal.toString().isEmpty()) {
                lastPrintPopup = "modal: " + modal;
                System.out.println("ConsultantOnCall: print popup -> " + lastPrintPopup);
                return lastPrintPopup;
            }
            page.waitForTimeout(500);
        }
        lastPrintPopup = "";
        System.out.println("ConsultantOnCall.capturePrintPopup: no in-page popup and no new tab appeared");
        return "";
    }

    /** Dismiss the print popup via its <b>Cancel</b> (closing the report tab if that is what opened). */
    public String cancelPrintPopup() {
        // A report tab is "cancelled" by closing it.
        for (Page p : page.context().pages()) {
            if (p != page && !p.isClosed()) {
                String u = p.url();
                if (u != null && !u.isBlank() && !"about:blank".equals(u)) {
                    try { p.close(); } catch (Exception ignore) { }
                    waitForAngular(600);
                    return "closed the report tab";
                }
            }
        }
        Object clicked = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const vis=e=>e && e.offsetParent!==null;"
                + " const c=[...document.querySelectorAll('button,a,input[type=button],span,i')].filter(vis);"
                + " const b=c.find(x=>/^\\s*(cancel|close|no)\\s*$/i.test(norm(x.textContent||x.value)))"
                + "   || c.find(x=>/cancel|close|dismiss/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*×\\s*$/.test(norm(x.textContent)));"
                + " if(!b) return ''; const t=norm(b.textContent||b.value)||b.tagName; b.click(); return t||'(icon)'; }");
        waitForAngular(1000);
        String out = clicked == null ? "" : clicked.toString();
        System.out.println("ConsultantOnCall: cancel -> " + (out.isEmpty() ? "no Cancel control found" : out));
        return out;
    }

    /** True when no print popup remains: no extra report tab AND no visible modal. */
    public boolean printPopupClosed() {
        for (Page p : page.context().pages()) {
            if (p != page && !p.isClosed()) {
                String u = p.url();
                if (u != null && !u.isBlank() && !"about:blank".equals(u)) return false;
            }
        }
        Object modal = page.evaluate("() => [...document.querySelectorAll('.modal,.modal-dialog,.jAlert,[role=dialog],.sweet-alert,.ui-dialog,.print-preview')]"
                + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())");
        return !Boolean.TRUE.equals(modal);
    }

    /** Click <b>Back</b> ({@code fnBack()}) and confirm the calendar was left. */
    public boolean clickBack() {
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/fnBack/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*back\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__cocBack'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("ConsultantOnCall.clickBack: Back button not found"); return false; }
        try { page.locator("#__cocBack").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("ConsultantOnCall.clickBack: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__cocBack'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1800);
        return !onCalendar();
    }

    /** Block until no toast is visible, so a capture cannot pick up a previous one. */
    private void waitForToastsToClear() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) {
            System.out.println("ConsultantOnCall: a previous toast is still on screen — the next capture may be stale");
        }
    }

    // Failure placeholders returned by the JS helpers above always read "(no-...)" — e.g. "(no-field)",
    // "(no-option)". Rejecting anything merely STARTING with "(" is too broad: a real department here is
    // named "(NAMA DR) MR C/N", and that literal leading "(" was false-failing this step even though the
    // department had genuinely been selected.
    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(no-");
    }
}
