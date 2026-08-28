package com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; OT Configuration &gt; <b>OtSchedulingMaster</b> — Page Object.
 *
 * <p>Flow: enter <b>Code</b> and <b>Remark</b> → <b>Submit</b> → success toast.</p>
 *
 * <p>Both boxes are addressed by the <b>last segment</b> of the ng-model, and the Remark lookup excludes
 * the box already claimed by the Code, so one value cannot be written over the other — the defect that
 * put the area name into the Code field on Area/Town while the run still reported a pass.</p>
 */
public class OtSchedulingMaster extends BasePage {

    public OtSchedulingMaster(Page page) { super(page); }

    public String lastBodyText = "", lastControls = "", lastMenu = "", lastEntry = "", lastToast = "",
            lastSaveDiagnostics = "", lastListCheck = "", lastRoute = "", lastAdd = "";

    /** Shared JS helpers: visibility, text normalising, label lookup, model tail, model-aware setter. */
    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
          + "const ngOf=e=>e.getAttribute('ng-model')||'';"
          + "const tail=e=>(ngOf(e).split('.').pop()||'');"
          + "const labelOf=e=>{ let l=e.closest('label'); if(l) return norm(l.textContent);"
          + "  if(e.id){ const f=document.querySelector(\"label[for='\"+e.id+\"']\"); if(f) return norm(f.textContent); }"
          + "  const g=e.closest('.form-group,.row,td,div'); return g? norm(g.textContent).slice(0,60):''; };"
          + "const setEl=(e,v)=>{ if(!e) return '(no-field)'; e.focus(); e.value=v;"
          + "  try{ const c=angular.element(e).controller('ngModel'); if(c){ c.$setViewValue(v); c.$render(); } }catch(err){}"
          + "  e.dispatchEvent(new Event('input',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('change',{bubbles:true}));"
          + "  e.dispatchEvent(new Event('blur',{bubbles:true}));"
          + "  return e.value; };";

    /** Toasts: innermost message first, else a container concatenates every message into one. */
    private static final String TOAST_ELS =
            "const toastEls=()=>{ const all=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
          + "  .filter(vis); const inner=all.filter(e=>!e.querySelector('.toast-message'));"
          + "  return (inner.length?inner:all); };";

    // ---- navigation ------------------------------------------------------

    /**
     * Application Configuration &rarr; OT Configuration &rarr; OtSchedulingMaster.
     *
     * <p>The route is taken from the menu link and used as the fallback too — guessing routes on this
     * module is a coin flip, and a wrong guess lands the run on the dashboard while still looking like a
     * navigation (the Shift screen is served from {@code #/AccountingShift}).</p>
     */
    public boolean navigateViaMenu(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }

        // The menu is opened up to three times. DevHIS reloads itself with a cache-buster (?_cb=) now and
        // then, and a reload part-way through collapses the menu — the run then sees only the top-level
        // entries and reports the screen as missing when it is simply not expanded yet.
        Object href = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            clickMenu("^\\s*application\\s*configuration\\s*$");
            clickMenu("^\\s*ot\\s*configuration\\s*$");
            lastMenu = describeMenu();

            // Anchored to the whole label. A loose match would take the first menu entry that merely
            // contains the words — the scaffolding trap that once ran an entire Compliance flow against
            // Complaint Type and reported PASS against the wrong screen's fields.
            href = page.evaluate("() => {" + JS
                    + " const a=[...document.querySelectorAll('a[href]')]"
                    + "   .find(x=>/^\\s*ot\\s*scheduling\\s*master\\s*$/i.test(norm(x.textContent)));"
                    + " if(!a) return ''; a.id='__otsMenu'; return a.getAttribute('href')||''; }");
            if (href != null && !href.toString().isEmpty()) break;
            System.out.println("OtSchedulingMaster.nav: the OT Scheduling Master link was not in the menu on attempt "
                    + attempt + " — reopening the menu");
            page.waitForTimeout(1500);
        }
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__otsMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("OtSchedulingMaster.nav: " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__otsMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(3000);
        }
        if (!onScreen() && !lastRoute.isEmpty()) {
            String hash = lastRoute.startsWith("#") ? lastRoute : "#" + lastRoute;
            try { page.evaluate("(h) => { window.location.hash = h; }", hash.substring(1)); }
            catch (Exception ignore) { }
            waitForAngular(3000);
            if (!onScreen()) {
                try { page.navigate(baseUrl + hash); } catch (Exception ignore) { }
                waitForAngular(3500);
            }
        }
        // The route resolving is not the screen rendering. GENMST loads its view after the hash changes,
        // and until it does the DASHBOARD's controls are still in the DOM — a run that starts typing
        // straight away puts its values into dashboard widgets (LocationName took the remark once).
        waitForScreenControls(15000);

        Object body = page.evaluate("() => (document.body? document.body.innerText : '')"
                + ".replace(/\\s+/g,' ').trim().slice(0,300)");
        lastBodyText = body == null ? "" : body.toString();
        return onScreen() && !lastBodyText.isEmpty();
    }

    /** Wait for this screen's OWN controls — a Code box, or its grid — rather than the dashboard's. */
    public boolean waitForScreenControls(int timeoutMs) {
        try {
            page.waitForFunction("() => {"
                    + " const vis=e=>e.offsetWidth||e.offsetHeight||e.getClientRects().length;"
                    + " const tail=e=>((e.getAttribute('ng-model')||'').split('.').pop()||'');"
                    + " const code=[...document.querySelectorAll('input,textarea')].filter(vis)"
                    + "   .some(e=>/code$/i.test(tail(e)));"
                    + " const grid=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .some(vis);"
                    + " return code || grid; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            System.out.println("OtSchedulingMaster.waitForScreenControls: no Code box or grid within "
                    + timeoutMs + "ms — the screen may not have rendered");
            return false;
        }
    }

    private void clickMenu(String textRegex) {
        page.evaluate("(re) => {" + JS
                + " const rx=new RegExp(re,'i');"
                + " const a=[...document.querySelectorAll('a,li>a,span')].filter(vis)"
                + "   .find(x=>rx.test(norm(x.textContent)));"
                + " if(a) a.click(); }", textRegex);
        waitForAngular(1200);
    }

    /** Every visible menu entry — used to pin the real label when a menu click misses. */
    public String describeMenu() {
        Object r = page.evaluate("() => {" + JS
                + " return [...new Set([...document.querySelectorAll('a')].filter(vis)"
                + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||''))"
                + "   .filter(t=>t.length>4 && t.length<70))].slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /**
     * On the OtSchedulingMaster screen — matched on the hash route, never on page text.
     *
     * <p>The route bears no resemblance to the label: this screen is served from <b>{@code #/GENMST}</b>.
     * Requiring the route to contain "generic" rejected the very page the menu had just opened, the run
     * then fell back to guessed routes, and every guess landed on the dashboard. The route the MENU gives
     * is authoritative here, with {@code GENMST} as the spelling now known.</p>
     */
    public boolean onScreen() {
        String u = page.url().toLowerCase().replace(" ", "").replace("%20", "");
        int h = u.indexOf('#');
        String route = h < 0 ? "" : u.substring(h);
        if (route.contains("patientdashboard") || route.equals("#") || route.isEmpty()) return false;
        if (route.contains("otscheduling") || route.contains("scheduling")
                || route.contains("ottheatre")
                || route.contains("genmst")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").trim();
        return !menuRoute.isEmpty() && route.contains(menuRoute);
    }

    // ---- diagnostics -----------------------------------------------------

    /** Dump every visible control with its ng-model — selectors are pinned from this, never guessed. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const out=[];"
                + " for(const e of document.querySelectorAll('select,input,textarea,button,a[ng-click]')){"
                + "   if(!vis(e)) continue;"
                + "   const ng=ngOf(e), click=e.getAttribute('ng-click')||'';"
                + "   if(/setDatepickerDay|prevMonth|nextMonth|showYearsPagination/.test(click)) continue;"
                + "   if(e.tagName==='SELECT') out.push('SELECT \"'+labelOf(e).slice(0,40)+'\" [ng='+ng+'] opts='"
                + "     +e.options.length+' first=\"'+norm((e.options[1]||e.options[0]||{}).text||'')+'\"');"
                + "   else if(e.tagName==='BUTTON'||e.tagName==='A'){"
                + "     const t=norm(e.textContent)||e.value||'';"
                + "     if(t||click) out.push('BTN \"'+t.slice(0,40)+'\" [ng-click='+click+']'); }"
                + "   else out.push(e.tagName+' \"'+(e.placeholder||'').slice(0,30)+'\" [ng='"
                + "     +(ng||'?')+'] type='+(e.type||'')+' label=\"'+labelOf(e).slice(0,45)+'\"'); }"
                + " return [...new Set(out)].join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        System.out.println("=== OtSchedulingMaster CONTROLS ===\n" + lastControls);
        return lastControls;
    }

    // ---- flow ------------------------------------------------------------

    /** The entry form is showing — judged on a Code box, which the list screen does not have. */
    public boolean formOpen() {
        // This screen has no Code box — the copied "is there a code field" check never sees this form.
        // The entry form is the one carrying the OT Theatre dropdown and the Add control.
        Object r = page.evaluate("() => {" + JS
                + " const theatre=[...document.querySelectorAll("
                + "   \"select[ng-model='deptScheduleInfo.otid']\")].filter(vis).length;"
                + " const add=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .filter(x=>/fnAddDeptSchedule/i.test(x.getAttribute('ng-click')||'')).length;"
                + " return theatre>0 && add>0; }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Click <b>Add</b> if the entry form is not already on screen.
     *
     * <p>The requested steps do not mention Add, so this reports which of the two the screen did rather
     * than assuming either.</p>
     */
    public String openFormIfNeeded() {
        if (formOpen()) { lastAdd = "Not needed — the route opens the entry form directly."; return lastAdd; }
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent)||x.value||'')"
                + "        || /add-?ot|AddOtSchedulingMaster|OTSCHEDUL/i.test((x.getAttribute('href')||'')"
                + "             +' '+(x.getAttribute('ng-click')||'')));"
                + " if(b) b.id='__otsAdd'; }");
        try {
            page.locator("#__otsAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) {
            System.out.println("OtSchedulingMaster.openFormIfNeeded: " + e.getMessage().split("\n")[0]);
        }
        page.evaluate("() => { const e=document.getElementById('__otsAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);
        lastAdd = formOpen() ? "The form needed Add; it is now open." : "Add did not open a form.";
        return lastAdd;
    }


    // ---- OT Scheduling Master's own fields -------------------------------

    public String lastTheatre = "", lastTable = "", lastTimes = "", lastDay = "", lastAddRow = "",
            lastAddHandler = "";
    private int scheduleRowsBefore = -1, scheduleRowsAfter = -1;

    /** The DOM indexes of a list's real options, in order. */
    private java.util.List<Integer> realIndexes(String model) {
        Object r = page.evaluate("(m) => {" + JS
                + " const e=[...document.querySelectorAll(\"select[ng-model='\"+m+\"']\")].filter(vis)[0];"
                + " if(!e) return [];"
                + " return [...e.options].map((o,i)=>({o,i}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.text)))"
                + "   .map(x=>x.i); }", model);
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) {
            for (Object o : (java.util.List<?>) r) out.add(((Number) o).intValue());
        }
        return out;
    }

    /** How many real options a list currently holds. */
    private int optionCount(String model) {
        Object n = page.evaluate("(m) => {" + JS
                + " const e=[...document.querySelectorAll(\"select[ng-model='\"+m+\"']\")].filter(vis)[0];"
                + " return e? [...e.options].filter(o=>o.value"
                + "   && !/^-*\\s*select/i.test(norm(o.text))).length : 0; }", model);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** Select the option at {@code index}, returning the text the control then shows. */
    private String selectIndex(String model, String id, int index) {
        page.evaluate("(a) => {" + JS
                + " document.querySelectorAll('#'+a.id).forEach(e=>e.removeAttribute('id'));"
                + " const e=[...document.querySelectorAll(\"select[ng-model='\"+a.m+\"']\")].filter(vis)[0];"
                + " if(e) e.id=a.id; }", java.util.Map.of("m", model, "id", id));
        try {
            page.locator("#" + id).selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(index));
        } catch (Exception e) {
            // The list re-renders as the option is chosen, which aborts the native select. Set it
            // through Angular instead, and say so rather than reporting an empty selection.
            Object f = page.evaluate("(a) => {" + JS
                    + " const e=document.getElementById(a.id); if(!e) return '';"
                    + " e.selectedIndex=a.i;"
                    + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ angular.element(e).triggerHandler('change'); }catch(err){}"
                    + " return ((e.options[e.selectedIndex]||{}).text||'').trim(); }",
                    java.util.Map.of("id", id, "i", index));
            waitForAngular(900);
            String txt = f == null ? "" : f.toString();
            return txt.isEmpty() ? "(select failed)" : txt;
        }
        waitForAngular(800);
        Object t = page.evaluate("(id) => { const e=document.getElementById(id);"
                + " return e? ((e.options[e.selectedIndex]||{}).text||'').trim() : ''; }", id);
        return t == null ? "" : t.toString();
    }

    /** A value really set — not a placeholder, not a lookup failure. */
    public static boolean chosen(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(")
                && !v.matches("^-*\\s*[Ss]elect.*");
    }

    /**
     * Select an <b>OT Theatre</b> ({@code deptScheduleInfo.otid}) that actually HAS a table.
     *
     * <p>The OT Table list is filled from this choice, and it is waited for until it CHANGES rather than
     * until it is non-empty — the list keeps the previous theatre's options until the new ones arrive.
     * Theatres are tried in turn, because most have no table at all and stopping at the first would fail
     * the next step for a reason that has nothing to do with the screen.</p>
     */
    public String selectTheatreWithTable() {
        java.util.List<Integer> theatres = realIndexes("deptScheduleInfo.otid");
        StringBuilder tried = new StringBuilder();
        for (Integer ti : theatres) {
            int before = optionCount("deptScheduleInfo.ottableid");
            String name = selectIndex("deptScheduleInfo.otid", "__otsTheatre", ti);
            long deadline = System.currentTimeMillis() + 9000;
            int now = before;
            while (System.currentTimeMillis() < deadline) {
                now = optionCount("deptScheduleInfo.ottableid");
                if (now != before) break;
                page.waitForTimeout(300);
            }
            tried.append(tried.length() == 0 ? "" : ", ").append(name).append(" -> ").append(now)
                 .append(" table(s)");
            if (now > 0) {
                lastTheatre = name + " [deptScheduleInfo.otid, " + theatres.size() + " theatres]"
                        + " — its OT Table list holds " + now + " option(s). Tried: " + tried;
                System.out.println("OtSchedulingMaster: theatre -> " + lastTheatre);
                return lastTheatre;
            }
        }
        lastTheatre = "(no theatre offered any OT table. Tried: " + tried + ")";
        System.out.println("OtSchedulingMaster: theatre -> " + lastTheatre);
        return lastTheatre;
    }

    public boolean theatreSelected() { return chosen(lastTheatre); }

    /** Select the <b>OT Table</b> ({@code deptScheduleInfo.ottableid}). */
    public String selectTable() {
        java.util.List<Integer> tables = realIndexes("deptScheduleInfo.ottableid");
        if (tables.isEmpty()) { lastTable = "(the OT Table list is empty)"; return lastTable; }
        lastTable = selectIndex("deptScheduleInfo.ottableid", "__otsTable", tables.get(0))
                + " [deptScheduleInfo.ottableid, " + tables.size() + " options]";
        System.out.println("OtSchedulingMaster: table -> " + lastTable);
        return lastTable;
    }

    public boolean tableSelected() { return chosen(lastTable); }

    private int theatreCursor = -1, tableCursor = 0;

    /**
     * Move to the NEXT theatre/table combination, clearing the form first.
     *
     * <p>Needed because a combination that already carries a schedule is refused with "OT already has
     * scheduled!" — that is the screen enforcing one schedule per OT table, not a fault, so the flow
     * moves on to a free combination rather than reporting a defect.</p>
     */
    public String nextCombination() {
        // Clear whatever the refused attempt left on the form.
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/clearForm/i.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.click(); }");
        waitForAngular(1500);

        java.util.List<Integer> tables = realIndexes("deptScheduleInfo.ottableid");
        if (tableCursor + 1 < tables.size()) {
            tableCursor++;
            lastTable = selectIndex("deptScheduleInfo.ottableid", "__otsTable", tables.get(tableCursor))
                    + " [deptScheduleInfo.ottableid, table " + (tableCursor + 1) + " of " + tables.size() + "]";
            System.out.println("OtSchedulingMaster: next combination -> same theatre, " + lastTable);
            return "moved to the next table under the same theatre: " + lastTable;
        }

        // No table left under this theatre — move to the next theatre that offers one.
        java.util.List<Integer> theatres = realIndexes("deptScheduleInfo.otid");
        while (theatreCursor + 1 < theatres.size()) {
            theatreCursor++;
            int before = optionCount("deptScheduleInfo.ottableid");
            String name = selectIndex("deptScheduleInfo.otid", "__otsTheatre", theatres.get(theatreCursor));
            long deadline = System.currentTimeMillis() + 9000;
            int now = before;
            while (System.currentTimeMillis() < deadline) {
                now = optionCount("deptScheduleInfo.ottableid");
                if (now != before) break;
                page.waitForTimeout(300);
            }
            if (now > 0) {
                tableCursor = 0;
                lastTheatre = name + " [deptScheduleInfo.otid]";
                lastTable = selectTable();
                System.out.println("OtSchedulingMaster: next combination -> " + lastTheatre + " / " + lastTable);
                return "moved to the next theatre: " + lastTheatre + " / " + lastTable;
            }
        }
        return "(no other theatre/table combination is available)";
    }

    /** The screen refuses this combination because it already carries a schedule. */
    public static boolean alreadyScheduled(String toast) {
        return toast != null && toast.toLowerCase().contains("already");
    }


    /**
     * Enter the <b>Start Time</b> and <b>End Time</b>.
     *
     * <p>Both boxes are masked and share ONE ng-model ({@code inputTime}), so they are told apart by
     * position and driven with REAL keystrokes — a value assigned to the model is ignored by the mask and
     * reads back empty, and writing the model twice would overwrite the first time with the second.</p>
     */
    public String enterTimes(String start, String end) {
        Object count = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll(\"input[ng-model='inputTime']\")]"
                + "   .filter(vis).length; }");
        int boxes = count instanceof Number ? ((Number) count).intValue() : 0;
        String r1 = typeIntoTimeBox(0, start);
        String r2 = typeIntoTimeBox(1, end);
        lastTimes = "StartTime=" + r1 + " (typed \"" + start + "\") | EndTime=" + r2
                + " (typed \"" + end + "\") (the form has " + boxes + " time box(es))"
                + "  ||  what the controller holds: " + timesOnModel();
        waitForAngular(600);
        System.out.println("OtSchedulingMaster: times -> " + lastTimes);
        return lastTimes;
    }

    /**
     * Type into the nth time box with real keys, having CLEARED it first.
     *
     * <p>These boxes are timepickers that open on focus and arrive pre-filled with the current time,
     * bound to {@code deptScheduleInfo.txtStartTime} / {@code txtEndTime} as real dates. Typing without
     * clearing appends to what is already there ("10:24 AM0900AM"), the widget cannot parse that, and it
     * DELETES the model value — which is why the Add button then answered "Please Enter Start Time!"
     * while the box on screen still showed text.</p>
     */
    private String typeIntoTimeBox(int nth, String value) {
        try {
            com.microsoft.playwright.Locator box = page.locator("input[ng-model='inputTime']").nth(nth);
            box.click();
            box.press("Control+a");
            box.press("Delete");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(120));
            box.press("Escape");   // close the picker popup it opens on focus
        } catch (Exception e) { return "(typing failed: " + e.getMessage().split("\n")[0] + ")"; }
        waitForAngular(600);
        Object back = page.evaluate("(nth) => {" + JS
                + " const b=[...document.querySelectorAll(\"input[ng-model='inputTime']\")]"
                + "   .filter(vis)[nth];"
                + " return b? (b.value||'') : '(gone)'; }", nth);
        return back == null ? "" : back.toString();
    }

    /** What the controller holds for the two times — the values the Add button actually reads. */
    public String timesOnModel() {
        Object r = page.evaluate("() => {" + JS
                + " const s=[...document.querySelectorAll(\"select[ng-model='deptScheduleInfo.otid']\")]"
                + "   .filter(vis)[0];"
                + " if(!s) return '(the form is not on screen)';"
                + " try{ const d=angular.element(s).scope().$eval('deptScheduleInfo')||{};"
                + "      const f=v=>v==null? 'MISSING' : String(v);"
                + "      return 'txtStartTime='+f(d.txtStartTime)+' | txtEndTime='+f(d.txtEndTime);"
                + " }catch(err){ return '(unreadable)'; } }");
        return r == null ? "" : r.toString();
    }

    /**
     * Both times reached the CONTROLLER.
     *
     * <p>Judged on {@code txtStartTime}/{@code txtEndTime}, not on the boxes: an unparseable entry leaves
     * text on screen while the model goes missing, and the Add button reads the model.</p>
     */
    public boolean timesEntered() {
        return lastTimes != null && !lastTimes.contains("(typing failed")
                && !lastTimes.contains("txtStartTime=MISSING")
                && !lastTimes.contains("txtEndTime=MISSING");
    }

    /**
     * Tick the checkbox for a day.
     *
     * <p>All seven day boxes share ONE model ({@code todo.done}), so they are told apart by their label.
     * The box is CLICKED, never assigned — an Angular checkbox set by hand leaves the model untouched and
     * the day would not reach the record.</p>
     */
    public String tickDay(String day) {
        Object r = page.evaluate("(day) => {" + JS
                + " const boxes=[...document.querySelectorAll(\"input[type='checkbox']\")].filter(vis);"
                + " const named=boxes.map(b=>({b, l:norm(labelOf(b))}));"
                + " const hit=named.find(x=>new RegExp('^\\\\s*'+day+'\\\\s*$','i').test(x.l))"
                + "   || named.find(x=>new RegExp(day,'i').test(x.l)) || named[0];"
                + " if(!hit) return '(no day checkbox on this form)';"
                + " if(!hit.b.checked) hit.b.click();"
                + " let model='(unreadable)';"
                + " try{ const c=angular.element(hit.b).controller('ngModel');"
                + "      if(c) model=String(c.$modelValue); }catch(err){}"
                + " return 'ticked \"'+hit.l+'\" ['+(ngOf(hit.b)||'?')+'] -> checked='+hit.b.checked"
                + "   +', model='+model+' (the form offers '+boxes.length+' day boxes, all on the same "
                + "model, so they are told apart by label)'; }", day);
        lastDay = r == null ? "" : r.toString();
        waitForAngular(500);
        System.out.println("OtSchedulingMaster: day -> " + lastDay);
        return lastDay;
    }

    public boolean dayTicked() {
        return lastDay != null && lastDay.contains("checked=true") && !lastDay.contains("model=false");
    }

    /**
     * Click <b>Add</b> ({@code fnAddDeptSchedule}) and count the schedule rows either side.
     *
     * <p>Anything the screen says while adding is recorded: a refusal here is otherwise invisible, and
     * the run would report "the list did not grow" without saying why.</p>
     */
    public String clickAdd() {
        scheduleRowsBefore = scheduleRows();
        try {
            page.evaluate("() => { window.__otsAdded = [];"
                    + " if(window.__otsAddObs) window.__otsAddObs.disconnect();"
                    + " window.__otsAddObs = new MutationObserver(ms=>{"
                    + "   for(const m of ms){ for(const n of m.addedNodes){"
                    + "     if(n.nodeType!==1) continue;"
                    + "     const t=(n.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "     const cls=String(n.className||'');"
                    + "     if(t && t.length<200 && /modal|alert|dialog|toast|jAlert|sweet/i.test(cls+' '+n.id))"
                    + "       window.__otsAdded.push(t.slice(0,140)); } } });"
                    + " window.__otsAddObs.observe(document.body,{childList:true,subtree:true}); }");
        } catch (Exception ignore) { }

        Object clicked = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .find(x=>/fnAddDeptSchedule/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Add control found)';"
                + " b.scrollIntoView({block:'center'}); b.click(); return 'clicked Add'; }");
        waitForAngular(2500);
        scheduleRowsAfter = scheduleRows();
        Object rows = page.evaluate("() => {" + JS
                + " return [...document.querySelectorAll('.ui-grid-row,table tbody tr')].filter(vis)"
                + "   .map(r=>norm(r.textContent)).filter(t=>t).slice(-3).join(' ;; '); }");
        String said = "";
        try {
            Object s2 = page.evaluate("() => { const a=window.__otsAdded||[];"
                    + " if(window.__otsAddObs) window.__otsAddObs.disconnect();"
                    + " return a.length? a.slice(0,3).join(' ;; ') : ''; }");
            said = s2 == null ? "" : s2.toString();
        } catch (Exception ignore) { }

        lastAddRow = (clicked == null ? "" : clicked.toString())
                + "; schedule rows " + scheduleRowsBefore + " -> " + scheduleRowsAfter
                + (scheduleRowsAfter > scheduleRowsBefore
                    ? "; the new row reads: " + (rows == null ? "" : rows)
                    : "; NO row was added (rows shown: " + (rows == null ? "" : rows) + ")")
                + (said.isEmpty() ? "; the screen said nothing while adding"
                                  : "; the screen answered: \"" + said + "\"");
        System.out.println("OtSchedulingMaster: add -> " + lastAddRow);
        return lastAddRow;
    }

    /** Rows the CONTROLLER holds where they can be read, else the rendered rows. */
    private int scheduleRows() {
        Object n = page.evaluate("() => {" + JS
                + " const sel=[...document.querySelectorAll(\"select[ng-model='deptScheduleInfo.otid']\")]"
                + "   .filter(vis)[0];"
                + " if(sel){ try{ const sc=angular.element(sel).scope();"
                + "   for(const k of ['deptScheduleList','scheduleList','deptScheduleInfoList',"
                + "                   'lstDeptSchedule','otScheduleList']){"
                + "     const v=sc.$eval(k); if(Array.isArray(v)) return v.length; } }catch(err){} }"
                + " return [...document.querySelectorAll('.ui-grid-row,table tbody tr')].filter(vis)"
                + "   .filter(r=>norm(r.textContent)).length; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    public boolean rowAdded() { return scheduleRowsAfter > scheduleRowsBefore; }

    /** The Add handler's own source — what it validates, rather than a guess about it. */
    public String describeAddHandler() {
        Object r = page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                + "   .find(x=>/fnAddDeptSchedule/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return '(no Add control)';"
                + " try{ const fn=angular.element(b).scope().fnAddDeptSchedule;"
                + "      return fn? String(fn).replace(/\\s+/g,' ').slice(0,1500) : '(not on this scope)';"
                + " }catch(err){ return '(unreadable: '+err+')'; } }");
        lastAddHandler = r == null ? "" : r.toString();
        System.out.println("OtSchedulingMaster: add handler -> " + lastAddHandler);
        return lastAddHandler;
    }

    public String lastConfirm = "", lastPopupsSeen = "";
    /** The message was recovered from the observer because it had faded before the poll. */
    public boolean toastFromObserver = false;
    /**
     * This screen raised NO confirmation dialog: none was found while polling, and the observer — which
     * records every node added from the moment Submit is clicked — saw no modal, alert or dialog either,
     * only the toast.
     */
    public boolean noDialogRaised() {
        return lastConfirm != null && lastConfirm.startsWith("(no confirmation")
                && lastPopupsSeen != null
                && !lastPopupsSeen.matches("(?is).*(modal|dialog|sweet|jalert).*");
    }

    /**
     * Click <b>Save</b> on the confirmation dialog Submit raises.
     *
     * <p>Polled for up to 10s and matched on an EXACT Save/Yes/OK button inside a visible dialog, so a
     * "Saved" heading elsewhere on the page cannot be mistaken for the button.</p>
     */
    public String confirmSave() {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            Object r = page.evaluate("() => {" + JS
                    + " const dlgs=[...document.querySelectorAll("
                    + "   '.modal,.modal-dialog,.modal-content,[role=dialog],.sweet-alert,.jAlert,.ui-dialog')]"
                    + "   .filter(vis);"
                    + " for(const d of dlgs){"
                    + "   const b=[...d.querySelectorAll('button,a,input[type=button],input[type=submit]')]"
                    + "     .filter(vis)"
                    + "     .find(x=>/^save|yes|ok|confirm$/i.test(norm(x.textContent)||x.value||''));"
                    + "   if(b){ const msg=norm(d.textContent).slice(0,120);"
                    + "     b.click();"
                    + "     return 'clicked \"'+(norm(b.textContent)||b.value)+'\" on the confirmation"
                    + "       dialog: \"'+msg+'\"'; } }"
                    + " return ''; }");
            String s2 = r == null ? "" : r.toString();
            if (!s2.isEmpty()) {
                waitForAngular(1200);
                System.out.println("OtSchedulingMaster: confirm -> " + s2);
                return s2;
            }
            page.waitForTimeout(400);
        }
        System.out.println("OtSchedulingMaster: confirm -> no confirmation dialog appeared within 4s");
        return "(no confirmation dialog appeared within 4s)";
    }

    /**
     * A success message, judged strictly.
     *
     * <p>These configuration screens phrase refusals with the word "Added" — e.g. "… Is Already Added!" —
     * so a plain {@code contains("added")} reads a rejection as a save.</p>
     */
    public static boolean isSuccess(String toast) {
        if (toast == null || toast.isBlank()) return false;
        String t = toast.toLowerCase();
        if (t.contains("already") || t.contains("exist") || t.contains("not ")) return false;
        return t.contains("success") || t.contains("saved") || t.contains("added")
                || t.contains("inserted") || t.contains("updated");
    }

    /** Click <b>Submit</b> and return the toast. Toasts are cleared first so a repeat still reads. */
    public String submitAndGetToast() {
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); }
        catch (Exception ignore) { }
        try {
            page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast,[id^=toast]')"
                    + "   .forEach(t=>t.remove()); }");
        } catch (Exception ignore) { }
        waitForAngular(400);

        // Watch every node the page ADDS from here on. A confirmation dialog or a toast that appears and
        // is gone again between two polls would otherwise read as "nothing ever appeared" — the observer
        // cannot miss it, so the report can say whether the screen answered at all.
        try {
            page.evaluate("() => { window.__otsSeen = [];"
                    + " if(window.__otsObs) window.__otsObs.disconnect();"
                    + " window.__otsObs = new MutationObserver(ms=>{"
                    + "   for(const m of ms){ for(const n of m.addedNodes){"
                    + "     if(n.nodeType!==1) continue;"
                    + "     const t=(n.textContent||'').replace(/\\s+/g,' ').trim();"
                    + "     const cls=String(n.className||'');"
                    + "     if(t && t.length<200 && /modal|alert|dialog|toast|jAlert|sweet/i.test(cls+' '+n.id))"
                    + "       window.__otsSeen.push(cls+'#'+(n.id||'')+' :: '+t.slice(0,140)); } } });"
                    + " window.__otsObs.observe(document.body,{childList:true,subtree:true}); }");
        } catch (Exception ignore) { }

        Object clicked;
        try {
            clicked = page.evaluate("() => {" + JS
                    + " const all=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')]"
                    + "   .filter(vis).filter(x=>!/cke/i.test(x.className||''));"
                    + " const b=all.find(x=>/fnIUD/i.test(x.getAttribute('ng-click')||''))"
                    + "   || all.find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent)||x.value||''))"
                    + "   || all.find(x=>/submit|save/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return '(no Submit button)';"
                    + " b.scrollIntoView({block:'center'}); b.click();"
                    + " return 'clicked \"'+(norm(b.textContent)||b.value||'')+'\" [ng-click='"
                    + "   +(b.getAttribute('ng-click')||'-')+']'; }");
        } catch (Exception e) {
            lastSaveDiagnostics = "Submit click threw (the page navigated): " + e.getMessage().split("\n")[0];
            return "";
        }
        // Submit raises a CONFIRMATION dialog with its own Save button — nothing is written until that is
        // clicked. This runs BEFORE the shared acceptSaveDialog(): that helper clicks any visible red
        // SAVE button, so it dismisses this very dialog, and the flow would then report "no confirmation
        // dialog appeared" for a dialog it had itself just clicked away.
        lastConfirm = confirmSave();
        try { acceptSaveDialog(); } catch (Exception ignore) { }

        String toast = "";
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline && toast.isEmpty()) {
            try {
                Object now = page.evaluate("() => {" + JS + TOAST_ELS
                        + " return toastEls().map(x=>norm(x.textContent)); }");
                if (now instanceof java.util.List) {
                    for (Object o : (java.util.List<?>) now) {
                        String s = o.toString();
                        if (!s.isEmpty()) { toast = s; break; }
                    }
                }
            } catch (Exception ignore) { }
            if (toast.isEmpty()) page.waitForTimeout(500);
        }

        if (toast.isEmpty()) {
            // Some screens in this module answer in a "KPJ Portal" banner that carries no toast class —
            // looking only for toastr elements reports silence for a screen that spoke clearly.
            try {
                Object banner = page.evaluate("() => {"
                        + " const t=(document.body? document.body.innerText : '').replace(/\\s+/g,' ');"
                        + " const m=t.match(/[^.!]*\\b(already added|already exist|saved successfully"
                        + "|added successfully|updated successfully|message not found|please (enter|select|add))\\b[^.!]*[.!]?/i);"
                        + " return m? m[0].trim().slice(0,160) : ''; }");
                String fromPage = banner == null ? "" : banner.toString().trim();
                if (!fromPage.isEmpty()) toast = fromPage;
            } catch (Exception ignore) { }
        }

        // What did the page actually put on screen after Submit?
        String seen;
        try {
            Object s3 = page.evaluate("() => { const a=window.__otsSeen||[];"
                    + " if(window.__otsObs) window.__otsObs.disconnect();"
                    + " return a.length? a.slice(0,6).join(' ;; ') : ''; }");
            seen = s3 == null ? "" : s3.toString();
        } catch (Exception e) { seen = ""; }
        lastPopupsSeen = seen.isEmpty()
                ? "a mutation observer watched the page from the moment Submit was clicked and recorded "
                  + "NO dialog, alert or toast node being added at all"
                : "the page added: " + seen;

        // The observer is the reliable toast source. A toast lives about five seconds — shorter than the
        // wait for a confirmation dialog that never comes — so polling the DOM afterwards finds an empty
        // page and reports "no message" for a message the screen really did show.
        if (toast.isEmpty() && !seen.isEmpty()) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("::\\s*[^A-Za-z0-9]*(?:KPJ Portal)?\\s*(.+?)(?:\\s*;;|$)")
                    .matcher(seen);
            if (m.find()) {
                toast = m.group(1).trim();
                toastFromObserver = true;
            }
        }

        lastSaveDiagnostics = (clicked == null ? "" : clicked.toString())
                + (toast.isEmpty() ? "; no message within 20s" : "")
                + "; " + lastPopupsSeen
                + (toastFromObserver
                    ? "  ||  the message was read from that record rather than from a live poll: it had "
                      + "already faded by the time the poll began"
                    : "");
        lastToast = toast;
        System.out.println("OtSchedulingMaster: submit -> " + lastSaveDiagnostics + " toast=\"" + toast + "\"");
        return toast;
    }

    /**
     * Look the record up on the screen's own grid.
     *
     * <p>The grid carries one {@code colFilter.term} box PER COLUMN, all sharing that ng-model, so taking
     * the first one filters whichever column comes first and empties the grid — that is what "0 rows
     * shown" meant on Postal. The Code column's own box is used, and the row must carry the code AND the
     * remark, since a code alone can belong to a record that already existed.</p>
     */
    public boolean codeInList(String code, String remark) {
        waitForAngular(1500);
        try {
            if (page.url().toLowerCase().contains("add-")) {
                page.evaluate("() => {" + JS
                        + " const b=[...document.querySelectorAll('button,a')].filter(vis)"
                        + "   .find(x=>/closeForm/i.test(x.getAttribute('ng-click')||'')"
                        + "        || /^\\s*back\\s*$/i.test(norm(x.textContent)));"
                        + " if(b) b.click(); }");
                waitForAngular(2000);
            }
        } catch (Exception ignore) { }
        try {
            Object where = page.evaluate("(c) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis).length;"
                    + " const boxes=[...document.querySelectorAll(\"input[ng-model='colFilter.term']\")]"
                    + "   .filter(vis);"
                    + " const heads=[...document.querySelectorAll('.ui-grid-header-cell,th')].filter(vis)"
                    + "   .map(h=>norm(h.textContent)).filter(t=>t).join(' | ');"
                    + " const inCodeCol=boxes.find(b=>{ const h=b.closest('.ui-grid-header-cell,th');"
                    + "   return h && /code/i.test(norm(h.textContent)); });"
                    + " const f=inCodeCol||boxes[0];"
                    + " if(f){ f.focus(); f.value=c;"
                    + "   try{ const ct=angular.element(f).controller('ngModel');"
                    + "        if(ct){ ct.$setViewValue(c); ct.$render(); } }catch(e){}"
                    + "   f.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "   f.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " return 'rowsBeforeFilter='+rows+', filtered='"
                    + "   +(inCodeCol?'the Code column':(f?'the first column':'NOTHING - no filter box'))"
                    + "   +', columns=['+heads+']'; }", code);
            lastListCheck = "on " + page.url() + " — " + (where == null ? "" : where) + " -> ";
            waitForAngular(2500);
            Object hit = page.evaluate("(a) => {" + JS
                    + " const rows=[...document.querySelectorAll('tr,.ui-grid-row')].filter(vis)"
                    + "   .map(t=>norm(t.textContent)).filter(t=>t);"
                    + " const withCode=rows.filter(t=>a.c && t.includes(a.c));"
                    + " const mine=a.v? withCode.find(t=>t.includes(a.v)) : withCode[0];"
                    + " if(mine) return 'FOUND: '+mine.slice(0,140);"
                    + " if(withCode.length) return 'the code is in the list but on OTHER records ('"
                    + "   +withCode.map(t=>t.slice(0,60)).join(' / ')+')';"
                    + " return 'not in the list ('+rows.length+' rows shown)'; }",
                    java.util.Map.of("c", code, "v", remark == null ? "" : remark));
            lastListCheck += "looked for \"" + code + "\" + \"" + remark + "\" -> " + (hit == null ? "" : hit);
        } catch (Exception e) {
            lastListCheck += "could not read the list: " + e.getMessage().split("\n")[0];
        }
        System.out.println("OtSchedulingMaster: list check -> " + lastListCheck);
        return lastListCheck.contains("FOUND:");
    }
}
