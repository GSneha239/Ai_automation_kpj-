package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Nurse Roster</b> ({@code #/NurseRoster}) — Page Object.
 *
 * <p>Flow: <b>Nursing Station</b> → <b>NurseRoster</b> → <b>New Roster</b> → select <b>Department</b> and
 * <b>Nurse</b> → enter <b>Staff No</b> → enter <b>From Date</b> / <b>To Date</b> → select <b>Nursing
 * Station</b>, <b>Team</b> and <b>Shift</b> → <b>Add</b> → verify the row → <b>Save</b> → toast.</p>
 *
 * <p><b>Navigation escalates</b> (menu click → hash route → full page load) — several Nursing Station menu
 * links render an empty shell.</p>
 *
 * <p><b>Handles both widget styles.</b> The sibling Doctor Roster screens put the person picker in an
 * AngularJS <b>ui-select</b> typeahead (no {@code <select>} to set, and its choices only render once the
 * search box receives input) while others use a plain dropdown — {@link #selectNurse()} tries the
 * dropdown first and falls back to the typeahead, so either shape works.</p>
 */
public class NurseRoster extends BasePage {

    public NurseRoster(Page page) { super(page); }

    public static final String ROUTE = "#/NurseRoster";

    public String lastControls = "", lastBodyText = "";
    public String lastDepartment = "", lastNurse = "", lastStaffNo = "";
    public String lastDates = "", lastStation = "", lastTeam = "", lastShift = "";
    public String lastAdd = "", lastSaveDiagnostics = "";
    public int lastRowsAdded = -1;

    private static final String JS = ""
            + "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + "const vis=e=>e && e.offsetParent!==null;"
            + "const A=window.angular; const $=window.jQuery;"
            + "const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
            + "const real=o=>o.value && !/^-*\\s*select|^\\s*$|^--/i.test(norm(o.textContent));"
            + "const attrs=e=>[e.getAttribute('ng-model'),e.getAttribute('id'),e.getAttribute('name')].filter(Boolean).join(' ');"
            + "const labelOf=e=>{ const g=e.closest('.form-group,.row,td,div'); const gl=g?g.querySelector('label'):null;"
            + "  return norm((gl?gl.textContent:'')||e.getAttribute('placeholder')||e.getAttribute('title')||''); };"
            + "const setEl=(e,v)=>{ if(!e) return '(no-field)';"
            + "  if(e.readOnly||e.disabled) return '(read-only)';"
            + "  const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
            + "  e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "  if($){ try{ $(e).trigger('change'); }catch(x){} } return v; };"
            + "const pickEl=async(e)=>{ if(!e) return '(no-field)';"
            + "  for(let k=0;k<12;k++){ const i=[...e.options].findIndex(real);"
            + "    if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "      try{ A.element(e).triggerHandler('change'); }catch(x){}"
            + "      if($){ try{ $(e).trigger('change'); }catch(x){} }"
            + "      return norm(e.options[i].textContent); }"
            + "    await sleep(400); }"
            + "  return '(no-option)'; };"
            + "const findInput=rx=>{ const re=new RegExp(rx,'i');"
            + "  return [...document.querySelectorAll('input[type=text], input:not([type]), input[type=date], input[type=number]')]"
            + "    .filter(vis).filter(e=>!/colFilter|pagination/i.test(attrs(e)))"
            + "    .find(e=>re.test(attrs(e)+' '+labelOf(e))) || null; };"
            + "const findSelect=(rx,avoid)=>{ const re=new RegExp(rx,'i'); const bad=avoid?new RegExp(avoid,'i'):null;"
            + "  return [...document.querySelectorAll('select')].filter(vis)"
            + "    .filter(e=>!/pagination/i.test(attrs(e)))"
            + "    .find(e=>re.test(attrs(e)+' '+labelOf(e)) && (!bad||!bad.test(attrs(e)+' '+labelOf(e)))) || null; };";

    private static final String TOAST_ELS = ""
            + "const _tv=e=>e && e.offsetParent!==null;"
            + "const toastEls=()=>{ let n=[...document.querySelectorAll('.toast-message')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('.toast')].filter(_tv);"
            + "  if(!n.length) n=[...document.querySelectorAll('[id^=toast]')].filter(_tv);"
            + "  return n; };";

    // ---- navigation --------------------------------------------------------

    /** Navigate to Nurse Roster, escalating menu click → hash route → full page load. */
    public boolean navigateViaMenu(String baseUrl) {
        try {
            page.waitForFunction("() => document.querySelectorAll('a').length > 20",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { }

        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(1200);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^nurse\\s*roster$/i.test(norm(x.textContent))"
                + "        || /#\\/NurseRoster/i.test(x.getAttribute('href')||''));"
                + " if(a) a.click(); }");
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("NurseRoster.nav: menu click left the screen blank — trying the hash route");
        try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
        waitForAngular(2500);
        if (screenRendered()) return true;

        System.out.println("NurseRoster.nav: hash route left the screen blank — trying a full page load");
        try {
            page.navigate(baseUrl + "/" + ROUTE);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        } catch (Exception e) { System.out.println("NurseRoster.nav: full load failed - " + e.getMessage()); }
        waitForAngular(4000);
        return screenRendered();
    }

    public boolean screenRendered() {
        if (!page.url().toLowerCase().contains("nurseroster")) return false;
        Object n = page.evaluate("() => document.querySelectorAll('select, input, button').length");
        return n != null && Integer.parseInt(n.toString()) > 2;
    }

    /** Diagnostics: body text, every control, and any ui-select widgets. */
    public String describeControls() {
        Object r = page.evaluate("() => {" + JS
                + " const sel=[...document.querySelectorAll('select')].filter(vis)"
                + "   .map(e=>'SELECT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] opts='+e.options.length);"
                + " const uis=[...document.querySelectorAll('.ui-select-container,[ui-select],ui-select')].filter(vis)"
                + "   .map(e=>'UISELECT \"'+labelOf(e)+'\" text=\"'+norm(e.textContent).slice(0,30)+'\"');"
                + " const inp=[...document.querySelectorAll('input')].filter(vis)"
                + "   .filter(e=>!/colFilter|pagination/.test(e.getAttribute('ng-model')||''))"
                + "   .map(e=>'INPUT \"'+labelOf(e)+'\" [ng='+(e.getAttribute('ng-model')||'?')+'] type='+e.type);"
                + " const btn=[...document.querySelectorAll('button,a,input[type=button]')].filter(vis)"
                + "   .filter(e=>e.getAttribute('ng-click') && !/setDatepickerDay|Month|YearsPagination|page(First|Previous|Next|Last)/.test(e.getAttribute('ng-click')))"
                + "   .map(e=>'BTN \"'+norm(e.textContent||e.value).slice(0,20)+'\" [ng-click='+e.getAttribute('ng-click')+']');"
                + " return sel.concat(uis).concat(inp).concat(btn).join('\\n'); }");
        lastControls = r == null ? "" : r.toString();
        Object b = page.evaluate("() => (document.body.innerText||'').replace(/\\s+/g,' ').trim().slice(0,220)");
        lastBodyText = b == null ? "" : b.toString();
        System.out.println("NurseRoster CONTROLS:\n" + lastControls);
        return lastControls;
    }

    /** Click <b>New Roster</b> to open the roster form. */
    public boolean clickNewRoster() {
        boolean tagged = false;
        for (int i = 0; i < 20 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const c=[...document.querySelectorAll('button,a,i,span,input[type=button]')].filter(x=>x.offsetParent!==null);"
                    + " const b=c.find(x=>/fnGoToNewRoster|newroster/i.test(x.getAttribute('ng-click')||''))"
                    + "   || c.find(x=>/^\\s*new\\s*roster\\s*$/i.test(norm(x.textContent||x.value)))"
                    + "   || c.find(x=>/^\\s*(new|add)\\s*$/i.test(norm(x.textContent||x.value)));"
                    + " if(!b) return false; b.id='__nrNew'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("NurseRoster.clickNewRoster: New Roster not found"); return false; }
        try { page.locator("#__nrNew").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("NurseRoster.clickNewRoster: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__nrNew'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => document.querySelectorAll('select').length > 1",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("NurseRoster.clickNewRoster: form did not render"); }
        waitForAngular(1500);
        return true;
    }

    // ---- fields ------------------------------------------------------------

    /** Select <b>Department</b>. */
    public String selectDepartment() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const e=findSelect('department', 'sub');"
                + " if(!e){ resolve('(no-field)'); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " resolve((await pickEl(e))+' '+which); })");
        lastDepartment = r == null ? "" : r.toString();
        waitForAngular(1200);   // department may cascade into the nurse list
        System.out.println("NurseRoster: Department = " + lastDepartment);
        return lastDepartment;
    }

    /**
     * Select the <b>Nurse</b>.
     *
     * <p>Tries a plain dropdown first; if the picker is a ui-select the choices do not render on open, so
     * it clicks the search box and types — the technique the Doctor Roster screens needed.</p>
     */
    public String selectNurse() {
        Object viaSelect = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const e=findSelect('nurse|employee|staffname', 'station|team|shift|department');"
                + " if(!e){ resolve(''); return; }"
                + " const which='['+(e.getAttribute('ng-model')||'?')+']';"
                + " resolve((await pickEl(e))+' '+which); })");
        String s = viaSelect == null ? "" : viaSelect.toString();
        if (!s.isEmpty() && !s.startsWith("(")) {
            lastNurse = s;
            waitForAngular(800);
            System.out.println("NurseRoster: Nurse = " + lastNurse + " (dropdown)");
            return lastNurse;
        }

        // ui-select typeahead fallback
        Object opened = page.evaluate("() => {" + JS
                + " const conts=[...document.querySelectorAll('.ui-select-container,[ui-select],ui-select')].filter(vis);"
                + " if(!conts.length) return '';"
                + " const c=conts.find(x=>/nurse|employee|staff/i.test(labelOf(x))) || conts[0];"
                + " c.setAttribute('data-nr','nurse'); return labelOf(c)||'(unlabelled ui-select)'; }");
        if (opened == null || opened.toString().isEmpty()) {
            lastNurse = "(no-field)";
            System.out.println("NurseRoster: no nurse dropdown or ui-select found");
            return lastNurse;
        }
        try {
            com.microsoft.playwright.Locator search = page.locator("[data-nr='nurse'] input").first();
            search.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            waitForAngular(500);
            String picked = pickChoice();
            if (picked == null) { search.type("a"); waitForAngular(900); picked = pickChoice(); }
            if (picked == null) { search.fill(""); waitForAngular(900); picked = pickChoice(); }
            lastNurse = picked != null ? picked + " (ui-select)" : "(no-choices)";
        } catch (Exception e) {
            lastNurse = "(ui-select failed: " + e.getMessage() + ")";
        }
        page.evaluate("() => { document.querySelectorAll('[data-nr]').forEach(e=>e.removeAttribute('data-nr')); }");
        System.out.println("NurseRoster: Nurse = " + lastNurse);
        return lastNurse;
    }

    private String pickChoice() {
        for (int i = 0; i < 8; i++) {
            Object chosen = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const vis=e=>e && e.offsetParent!==null;"
                    + " const rows=[...document.querySelectorAll('.ui-select-choices-row')].filter(vis)"
                    + "   .filter(r=>norm(r.textContent) && !/^-*\\s*select|^--/i.test(norm(r.textContent)));"
                    + " if(!rows.length) return '';"
                    + " const r=rows[0]; const t=norm(r.textContent);"
                    + " (r.querySelector('.ui-select-choices-row-inner,a,div')||r).click(); return t; }");
            if (chosen != null && !chosen.toString().isEmpty()) return chosen.toString();
            page.waitForTimeout(400);
        }
        return null;
    }

    /** Enter the <b>Staff No</b>. */
    public String enterStaffNo(String staffNo) {
        Object r = page.evaluate("(v) => {" + JS
                + " const e=findInput('staffno|staff\\\\s*no|employeeno|empno');"
                + " if(!e) return '(no-field)';"
                + " return setEl(e, v)+' ['+(e.getAttribute('ng-model')||'?')+']'; }", staffNo);
        lastStaffNo = r == null ? "" : r.toString();
        waitForAngular(400);
        System.out.println("NurseRoster: Staff No = " + lastStaffNo);
        return lastStaffNo;
    }

    /**
     * Enter <b>From Date</b> and <b>To Date</b>.
     *
     * <p>Writes a native {@code type=date} field as {@code yyyy-MM-dd} and a text field in the screen's own
     * format — the Doctor Roster screens use native date inputs, others use masked text, and getting this
     * wrong leaves the value silently unbound.</p>
     */
    public String enterDates(String isoFrom, String isoTo, String textFrom, String textTo) {
        Object r = page.evaluate("(args) => {" + JS
                + " const [isoF, isoT, txtF, txtT] = args;"
                + " const f=findInput('fromdate|from\\\\s*date');"
                + " const t=findInput('todate|to\\\\s*date');"
                + " const put=(e,iso,txt)=>{ if(!e) return '(no-field)';"
                + "   const v = (e.type==='date') ? iso : txt;"
                + "   if(e.type==='date'){ e.value=v; e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "     e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "     if($){ try{ $(e).trigger('change'); }catch(x){} } return v+' [type=date]'; }"
                + "   return setEl(e,v)+' [text]'; };"
                + " return 'From='+put(f,isoF,txtF)+' | To='+put(t,isoT,txtT); }",
                java.util.List.of(isoFrom, isoTo, textFrom, textTo));
        lastDates = r == null ? "" : r.toString();
        waitForAngular(600);
        System.out.println("NurseRoster: " + lastDates);
        return lastDates;
    }

    public boolean datesSet() { return lastDates != null && !lastDates.contains("(no-field)"); }

    /** Select <b>Nursing Station</b>, <b>Team</b> and <b>Shift</b>. */
    public String selectStationTeamShift() {
        Object r = page.evaluate("() => new Promise(async resolve => {" + JS
                + " const out={};"
                + " const grab=async(key,rx,avoid)=>{ const e=findSelect(rx,avoid);"
                + "   out[key]= e? (await pickEl(e))+' ['+(e.getAttribute('ng-model')||'?')+']' : '(no-field)';"
                + "   await sleep(400); };"
                + " await grab('station','nursingstation|nursing\\\\s*station|stationid', 'team|shift');"
                + " await grab('team','team', 'station|shift');"
                + " await grab('shift','shift', 'station|team');"
                + " resolve('NursingStation='+out.station+' | Team='+out.team+' | Shift='+out.shift); })");
        String out = r == null ? "" : r.toString();
        for (String p : out.split("\\|")) {
            String t = p.trim();
            if (t.startsWith("NursingStation=")) lastStation = t.substring(15).trim();
            else if (t.startsWith("Team=")) lastTeam = t.substring(5).trim();
            else if (t.startsWith("Shift=")) lastShift = t.substring(6).trim();
        }
        waitForAngular(600);
        System.out.println("NurseRoster: " + out);
        return out;
    }

    public boolean stationTeamShiftSelected() {
        return isReal(lastStation) && isReal(lastTeam) && isReal(lastShift);
    }

    // ---- add + save --------------------------------------------------------

    /** Click <b>Add</b> and report the row delta — the added row is the thing to verify. */
    public String clickAdd() {
        Object before = page.evaluate("() => document.querySelectorAll('table tbody tr, .ui-grid-row').length");
        int rowsBefore = before == null ? 0 : Integer.parseInt(before.toString());

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const c=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/fnAddRow|addrow/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__nrAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("NurseRoster.clickAdd: Add not found"); lastRowsAdded = -1; return "(no Add button)"; }
        try { page.locator("#__nrAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("NurseRoster.clickAdd: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__nrAdd'); if(e) e.removeAttribute('id'); }");
        waitForAngular(1500);

        Object r = page.evaluate("(rowsBefore) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const rows=[...document.querySelectorAll('table tbody tr, .ui-grid-row')];"
                + " const added=rows.length-rowsBefore;"
                + " const last=rows.length? norm(rows[rows.length-1].textContent).slice(0,90) : '';"
                + " const msg=[...document.querySelectorAll('.toast-message')].map(x=>norm(x.textContent)).join(' | ');"
                + " return 'rowsAdded='+added+' total='+rows.length+(last?' | lastRow=\"'+last+'\"':'')"
                + "   +(msg?' | msg='+msg:''); }", rowsBefore);
        lastAdd = r == null ? "" : r.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("rowsAdded=(-?\\d+)").matcher(lastAdd);
        lastRowsAdded = m.find() ? Integer.parseInt(m.group(1)) : -1;
        System.out.println("NurseRoster: Add -> " + lastAdd);
        return lastAdd;
    }

    /** True when Add actually appended a row. */
    public boolean rowAdded() { return lastRowsAdded > 0; }

    /** Click <b>Save</b> and return the toast ("" if none appeared). */
    public String saveAndGetToast() {
        try {
            page.waitForFunction("() => ![...document.querySelectorAll('.toast-message,.toast,[id^=toast]')]"
                    + ".some(e=>e.offsetParent!==null && (e.textContent||'').trim())",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }

        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();" + TOAST_ELS
                + " window.__nrToasts=[]; if(window.__nrObs) window.__nrObs.disconnect();"
                + " const grab=()=>{ toastEls().forEach(el=>{ const t=norm(el.textContent);"
                + "   if(t && !window.__nrToasts.includes(t)) window.__nrToasts.push(t); }); };"
                + " window.__nrObs=new MutationObserver(grab); window.__nrObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " window.__nrPrior=(window.__nrToasts||[]).slice();"
                + " const c=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " const b=c.find(x=>/fnSaveRoster|saveroster/i.test(x.getAttribute('ng-click')||''))"
                + "   || c.find(x=>/^\\s*save\\s*$/i.test(norm(x.textContent||x.value)))"
                + "   || c.find(x=>/save|IUD/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) return false; b.id='__nrSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("NurseRoster.save: Save not found"); return ""; }

        try { page.locator("#__nrSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("NurseRoster.save: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__nrSave'); if(e) e.removeAttribute('id'); }");
        waitForAngular(900);
        acceptSaveDialog();
        waitForAngular(500);

        try {
            page.waitForFunction("() => (window.__nrToasts||[]).some(a=>/saved|success|added|updated|please|fill|enter|select|required|exist|error|failed/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { page.waitForTimeout(2500); }

        Object r = page.evaluate("() => { const prior=window.__nrPrior||[];"
                + " const a=(window.__nrToasts||[]).filter(x=>!prior.includes(x));"
                + " return a.find(x=>/(saved|added|updated)\\s*success|success/i.test(x)) || a.find(x=>x) || ''; }");
        String toast = r == null ? "" : r.toString().trim();

        if (toast.isEmpty()) {
            Object diag = page.evaluate("() => { const seen=(window.__nrToasts||[]).join(' ; ');"
                    + " const invalid=[...document.querySelectorAll('.ng-invalid, .has-error')]"
                    + "   .filter(e=>e.offsetParent!==null && e.tagName!=='FORM').length;"
                    + " return 'url='+location.hash+' invalidFields='+invalid"
                    + "   +(seen?' | toastsSeen='+seen:' | toastsSeen=none'); }");
            lastSaveDiagnostics = diag == null ? "" : diag.toString();
            System.out.println("NurseRoster.save: no toast — " + lastSaveDiagnostics);
        }
        waitForAngular(400);
        return toast;
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }
}
