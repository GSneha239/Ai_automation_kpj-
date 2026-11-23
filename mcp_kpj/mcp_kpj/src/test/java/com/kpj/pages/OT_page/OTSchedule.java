package com.kpj.pages.OT_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.PatientSearchPopup;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OT &gt; <b>OT Schedule</b> — Page Object ({@code #/OTScheduleList} → New → {@code #/add-OTSchedule}).
 *
 * <p>Confirmed live (devhis build v2026.10.21.266). The list screen's <b>New</b> ({@code AddOTSchedule()}) opens one long
 * form, which needs a patient first (<b>MRN*</b>, with a search button and the shared Patient Search popup):</p>
 * <ul>
 *   <li><b>Procedure Details</b> — <b>Get Procedure Details</b> opens the "Procedures List" popup ({@code #getprcdrDtl}): a
 *       Procedure Type select, <b>Search</b> ({@code fnSearch()}; about 1,900 rows for Minor/Major, none for Medium and
 *       most "Auto procedure type"s), a results table whose Select checkbox is {@code GrdServiceDetails[$index].isselected},
 *       and <b>Add</b> ({@code fnAdd()}), which closes the popup and lists the procedure in the Procedure Name grid.</li>
 *   <li><b>OT Details</b> sub-tab ({@code <a data-toggle="tab" data-target="#OTprcdrDtl2">} — the OT menu has a hidden
 *       link of the same name, so the tab is found by {@code data-toggle}) — OT Date (default today), <b>OT Theatre</b>
 *       and <b>OT Table</b> (the table list loads after the theatre). Choosing the table fills the <b>Current Schedule</b>
 *       with its time slots ({@code fnGetCurrentSchedule()}); a slot's checkbox ({@code FindTimeDuration(list)}) is DISABLED
 *       when the slot is booked or elapsed ({@code list.isslotbook == true || list.iselapsed == true}), and ticking a free one
 *       fills <b>From Time</b>, <b>To Time</b> and the duration. The "test" table has four 3-minute slots a day, so every run
 *       uses one up: the date can be moved on (changing the OT Date needs the table re-selected before its slots reload).</li>
 *   <li><b>Doctor</b> block — Department, Specialty ({@code classificationid}: Specialist / Medical Officer), Doctor (loads
 *       after the specialty), Procedure (the procedures added above) and <b>Add</b> ({@code AddDoctorDetailList()}).</li>
 *   <li><b>Staff</b> block — Designation (loads the staff list: {@code GetGetStaff()}), Procedure, Staff, <b>Add</b>
 *       ({@code AddStaffDetails()}).</li>
 *   <li><b>Save</b> ({@code fnIUDOTSchedules()}; the same handler also backs <b>Modify</b>, shown when editing).</li>
 * </ul>
 *
 * <p>The dropdowns are select2-enhanced, so selections are made on the underlying select with a change event and the
 * select2 display is synced; each choice is then read back from the Angular model.</p>
 */
public class OTSchedule extends BasePage {

    public OTSchedule(Page page) { super(page); }

    public String lastRoute = "", lastPatient = "", lastProcedureType = "", lastProcedure = "", lastTheatre = "", lastTable = "",
            lastDate = "", lastFrom = "", lastTo = "", lastSlot = "", lastDoctor = "", lastDoctorDept = "", lastDoctorSpec = "",
            lastDesignation = "", lastStaff = "", lastSave = "", lastToast = "";
    /** What the last fill step could not verify (empty = everything held). */
    public final List<String> problems = new ArrayList<>();
    public boolean ok() { return problems.isEmpty(); }

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String PANE = "#OTprcdrDtl2";
    private static final String MODAL = "#getprcdrDtl";

    // ---- navigation ------------------------------------------------------------------------------------------

    /** OT &gt; OT Schedule. The nav menu is built seconds after login, so its OT entry is awaited first. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>x.textContent.trim()==='OT')",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int i = 0; i < 4 && !onList(); i++) {
            Object r = page.evaluate("() => {" + JS
                    + " const link=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>/^#\\/otschedulelist$/i.test(x.getAttribute('href')||''));"
                    + " if(link){ link.id='__otLink'; return 'link:'+link.getAttribute('href'); }"
                    + " const ot=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)==='OT');"
                    + " if(ot){ ot.click(); return 'opened'; } return ''; }");
            String s = r == null ? "" : r.toString();
            if (s.startsWith("link:")) {
                lastRoute = s.substring(5);
                try { page.locator("#__otLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("OTSchedule.nav: click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__otLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else waitForAngular(1300);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/AddOTSchedule/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("OTSchedule.nav: the New button is not ready"); }
        waitForAngular(800);
        return onList();
    }

    public boolean onList() { return page.url().toLowerCase().matches(".*#/otschedulelist/?$"); }
    public boolean onForm() { return page.url().toLowerCase().contains("add-otschedule"); }

    /** Click <b>New</b> ({@code AddOTSchedule()}) and wait for the form (MRN box). */
    public boolean clickNew() {
        if (!clickByHandler("AddOTSchedule", null)) return false;
        try {
            page.waitForFunction("() => /add-otschedule/i.test(location.hash) && document.querySelector(\"input[ng-model='otdetail.MRNo']\")",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(1500);
        return onForm();
    }

    // ---- low-level helpers -----------------------------------------------------------------------------------

    /** Real-click a visible button found by its ng-click (and optionally its text). */
    private boolean clickByHandler(String handlerRe, String textRe) {
        Object tag = page.evaluate("(a) => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis)"
                + "   .find(x=>new RegExp(a.h).test(x.getAttribute('ng-click')||'') && (!a.t || new RegExp(a.t,'i').test(norm(x.textContent))));"
                + " if(!b) return ''; b.id='__otBtn'; return 'ok'; }", Map.of("h", handlerRe, "t", textRe == null ? "" : textRe));
        if (tag == null || tag.toString().isEmpty()) { System.out.println("OTSchedule: no visible button for " + handlerRe); return false; }
        try { page.locator("#__otBtn").scrollIntoViewIfNeeded(); page.locator("#__otBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("OTSchedule: click failed (" + handlerRe + ") - " + e.getMessage()); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__otBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    /** An Angular expression evaluated in the scope of the element matching {@code css}. */
    private String model(String css, String expr) {
        Object v = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return '';"
                + " try{ const v=angular.element(e).scope().$eval(a.x); return v==null?'':String(v); }catch(x){ return ''; } }",
                Map.of("s", css, "x", expr));
        return v == null ? "" : v.toString().trim();
    }

    private String sel(String ng) { return "select[ng-model='" + ng + "']"; }

    private int optionCount(String ng) {
        Object n = page.evaluate("(s) => { const e=document.querySelector(s); return e? [...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())).length : 0; }", sel(ng));
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    private void waitOptions(String ng, int timeoutMs) {
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())).length>0; }",
                    sel(ng), new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception ignore) { }
    }

    /**
     * Choose an option of a (select2-enhanced) select: set the underlying select, fire its change event and sync the
     * select2 display. {@code want} matches the option text exactly (case-insensitive); else {@code fallbackRe}; else, when
     * {@code anyIfMissing}, the first real option. Returns the option text, "" when none could be chosen; the model is
     * checked afterwards.
     */
    private String choose(String ng, String want, String fallbackRe, boolean anyIfMissing) {
        waitOptions(ng, 10000);
        Object t = page.evaluate("(a) => {" + JS
                + " const e=document.querySelector(a.s); if(!e) return '';"
                + " const o=[...e.options].map(x=>({t:norm(x.textContent),v:x.value})).filter(x=>x.v && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " let p=a.w? o.find(x=>x.t.toLowerCase()===a.w.toLowerCase()) : null;"
                + " if(!p && a.re) p=o.find(x=>new RegExp(a.re,'i').test(x.t));"
                + " if(!p && a.any) p=o[0];"
                + " if(!p) return '';"
                + " e.value=p.v; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const $=window.jQuery; if($){ try{ $(e).select2('val', p.v); }catch(x){} }"
                + " return p.t; }",
                Map.of("s", sel(ng), "w", want == null ? "" : want, "re", fallbackRe == null ? "" : fallbackRe, "any", anyIfMissing));
        String chosen = t == null ? "" : t.toString();
        if (chosen.isEmpty()) { problems.add(ng + " has no option" + (want == null ? "" : " \"" + want + "\"")); return ""; }
        waitForAngular(900);
        if (model(sel(ng), ng).isEmpty()) problems.add(ng + " is not held by the model");
        return chosen;
    }

    private void scrollToCss(String css) {
        page.evaluate("(c) => { const e=document.querySelector(c); if(e) e.scrollIntoView({block:'center'}); }", css);
        page.waitForTimeout(250);
    }

    /** Scroll the first visible element whose text matches {@code re} (a label/heading) to the middle of the screen. */
    public void scrollToText(String re) {
        page.evaluate("(re) => {" + JS
                + " const e=[...document.querySelectorAll('label,.panel-heading,th,h3,h4,button')].filter(vis).find(x=>new RegExp(re,'i').test(norm(x.textContent)));"
                + " if(e) e.scrollIntoView({block:'center'}); }", re);
        page.waitForTimeout(250);
    }

    // ---- patient ----------------------------------------------------------------------------------------------

    /** MRNs to try, from the shared Patient Search popup beside the MRN box (no MRN is hardcoded). */
    public List<String> candidateMrns(int target) {
        return new PatientSearchPopup(page).collectMrns(target, 2);
    }

    /**
     * Enter an MRN and click the search button; the patient is accepted only when the screen loaded THAT patient's visit
     * ({@code visitadmissionid}) — a stale previous patient must not be taken for it. Returns true when accepted.
     */
    public boolean searchPatient(String mrn) {
        // a leftover toast across the top of the page would sit on top of the MRN box
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>{ try{ t.remove(); }catch(e){} }); }");
        try {
            Locator in = page.locator("input[ng-model='otdetail.MRNo']").first();
            in.scrollIntoViewIfNeeded();
            in.click(new Locator.ClickOptions().setTimeout(5000));
            in.fill(mrn);
        } catch (Exception e) { System.out.println("OTSchedule: the MRN could not be typed - " + e.getMessage()); return false; }
        page.evaluate("() => {" + JS
                + " const b=[...document.querySelectorAll('button')].filter(vis).find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||''));"
                + " if(b) b.id='__otSearch'; }");
        try { page.locator("#__otSearch").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__otSearch'); if(e) e.removeAttribute('id'); }"); }
        try {
            page.waitForFunction("(m) => { const e=document.querySelector(\"input[ng-model='otdetail.MRNo']\"); if(!e) return false;"
                    + " try{ const o=angular.element(e).scope().otdetail; return !!o && String(o.MRNo).trim()===m && Number(o.visitadmissionid)>0 && String(o.PatientFullDtl||'').indexOf(m)>=0; }catch(x){ return false; } }",
                    mrn, new Page.WaitForFunctionOptions().setTimeout(9000));
        } catch (Exception e) { return false; }
        waitForAngular(800);
        lastPatient = model("input[ng-model='otdetail.MRNo']", "otdetail.PatientFullDtl").replaceAll("\\s+", " ");
        return true;
    }

    // ---- Procedure Details ------------------------------------------------------------------------------------

    /** Click <b>Get Procedure Details</b> and wait for the "Procedures List" popup. */
    public boolean clickGetProcedureDetails() {
        // the button has no ng-click (it opens the popup through data-toggle="modal"), so it is found by its text
        if (!clickByHandler("^$", "get procedure details")) return false;
        try {
            // (a Bootstrap modal is position:fixed, so offsetParent is null even while it is open — its "in" class is the state)
            page.waitForFunction("() => { const m=document.getElementById('getprcdrDtl'); return !!m && m.classList.contains('in') && !!(m.offsetWidth||m.offsetHeight); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { return false; }
        waitForAngular(800);
        return true;
    }

    private int procedureRows() {
        Object n = page.evaluate("() => document.querySelectorAll('#getprcdrDtl table tbody tr input[type=checkbox]').length");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /**
     * Select a procedure type and click <b>Search</b>. "Minor" is tried first, then the other types in list order, until a
     * type returns procedures (Medium and most "Auto procedure type"s return none). The result list is large (about 1,900
     * rows, a 5 MB answer), so the wait is generous. Returns "type — N procedures".
     */
    public String searchProcedures(String preferredType) {
        problems.clear();
        String s = MODAL + " " + sel("OTSchedule1.proceduretypeid");
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1; }", s, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add("no procedure types loaded"); return ""; }
        @SuppressWarnings("unchecked")
        List<Object> types = (List<Object>) page.evaluate("(s) => [...document.querySelector(s).options].filter(o=>o.value).map(o=>o.textContent.replace(/\\s+/g,' ').trim())", s);
        List<String> order = new ArrayList<>();
        for (Object t : types) if (String.valueOf(t).equalsIgnoreCase(preferredType)) order.add(String.valueOf(t));
        for (Object t : types) if (!order.contains(String.valueOf(t))) order.add(String.valueOf(t));
        boolean first = true;
        for (String type : order) {
            page.evaluate("(a) => { const e=document.querySelector(a.s); const o=[...e.options].find(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()===a.t);"
                    + " if(!o) return; e.value=o.value; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){ try{ $(e).select2('val', o.value); }catch(x){} } }",
                    Map.of("s", s, "t", type));
            waitForAngular(500);
            clickModalButton("fnSearch\\(\\)");
            // the first search is a 5 MB answer, so it gets longer; a type with no procedures answers quickly and is moved on from
            long end = System.currentTimeMillis() + (first ? 25000 : 9000);
            first = false;
            while (System.currentTimeMillis() < end && procedureRows() == 0) page.waitForTimeout(500);
            if (procedureRows() > 0) { lastProcedureType = type; return type + " — " + procedureRows() + " procedures listed"; }
        }
        problems.add("no procedure type returned any procedure");
        return "";
    }

    private boolean clickModalButton(String handlerRe) {
        Object tag = page.evaluate("(h) => {" + JS
                + " const m=document.getElementById('getprcdrDtl'); const b=[...m.querySelectorAll('button')].filter(vis).find(x=>new RegExp(h).test(x.getAttribute('ng-click')||''));"
                + " if(!b) return ''; b.id='__otMBtn'; return 'ok'; }", handlerRe);
        if (tag == null || tag.toString().isEmpty()) return false;
        try { page.locator("#__otMBtn").click(new Locator.ClickOptions().setTimeout(6000)); return true; }
        catch (Exception e) { System.out.println("OTSchedule: popup click failed (" + handlerRe + ") - " + e.getMessage()); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__otMBtn'); if(e) e.removeAttribute('id'); }"); }
    }

    /** Tick the first procedure's checkbox (a real click) and read it back. Returns the procedure's name ("" if not ticked). */
    public String tickFirstProcedure() {
        problems.clear();
        Locator cb = page.locator(MODAL + " table tbody tr input[type=checkbox]").first();
        try {
            cb.scrollIntoViewIfNeeded();
            cb.click(new Locator.ClickOptions().setTimeout(6000));
        } catch (Exception e) { problems.add("the procedure checkbox could not be clicked"); return ""; }
        waitForAngular(400);
        Object r = page.evaluate("() => { const c=document.querySelector('#getprcdrDtl table tbody tr input[type=checkbox]'); if(!c) return '';"
                + " if(!c.checked) return ''; const tds=c.closest('tr').querySelectorAll('td'); return (tds[1]?tds[1].textContent:'').replace(/\\s+/g,' ').trim(); }");
        lastProcedure = r == null ? "" : r.toString();
        if (lastProcedure.isEmpty()) problems.add("the procedure tick did not stick");
        return lastProcedure;
    }

    /** Click the popup's <b>Add</b> ({@code fnAdd()}), wait for it to close, and confirm the procedure is listed on the form. */
    public boolean clickAddProcedure() {
        problems.clear();
        if (!clickModalButton("fnAdd\\(\\)")) { problems.add("the popup's Add button was not found"); return false; }
        try {
            page.waitForFunction("() => { const m=document.getElementById('getprcdrDtl'); return !m || !m.classList.contains('in'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add("the popup did not close after Add"); }
        waitForAngular(1000);
        boolean listed = gridRows("procedure name", "delete").stream().anyMatch(r -> r.contains(lastProcedure));
        if (!listed) problems.add("\"" + lastProcedure + "\" is not in the Procedure Name grid");
        return listed;
    }

    /** Rows of the first visible table outside any popup whose header matches both regexes. */
    @SuppressWarnings("unchecked")
    public List<String> gridRows(String headRe1, String headRe2) {
        Object r = page.evaluate("(a) => {" + JS
                + " const t=[...document.querySelectorAll('table')].filter(x=>vis(x) && !x.closest('.modal'))"
                + "   .find(x=>{ const h=norm((x.querySelector('thead')||{}).textContent||''); return new RegExp(a.a,'i').test(h) && new RegExp(a.b,'i').test(h); });"
                + " return t? [...t.querySelectorAll('tbody tr')].map(r=>norm(r.textContent)).filter(Boolean) : []; }",
                Map.of("a", headRe1, "b", headRe2));
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    // ---- OT Details ----------------------------------------------------------------------------------------------

    /** Click the <b>OT Details</b> sub-tab (by its data-toggle, not the hidden menu link of the same name). */
    public boolean openOtDetailsTab() {
        problems.clear();
        Object tag = page.evaluate("() => {" + JS
                + " const a=[...document.querySelectorAll('a[data-toggle=\"tab\"]')].filter(vis).find(x=>norm(x.textContent)==='OT Details');"
                + " if(!a) return ''; a.id='__otTab'; return 'ok'; }");
        if (tag == null || tag.toString().isEmpty()) { problems.add("the OT Details tab was not found"); return false; }
        try { page.locator("#__otTab").scrollIntoViewIfNeeded(); page.locator("#__otTab").click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("the OT Details tab could not be clicked"); return false; }
        finally { page.evaluate("() => { const e=document.getElementById('__otTab'); if(e) e.removeAttribute('id'); }"); }
        try {
            page.waitForFunction("() => { const p=document.querySelector('#OTprcdrDtl2'); return !!p && p.offsetParent!==null; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception e) { problems.add("the OT Details pane did not show"); return false; }
        waitForAngular(800);
        return true;
    }

    public String selectTheatre(String want) {
        problems.clear();
        lastTheatre = choose("otdetails.ottheaterid", want, null, true);
        return lastTheatre;
    }

    public String selectTable(String want) {
        problems.clear();
        lastTable = choose("otdetails.ottableid", want, null, true);
        try {   // choosing the table fills the Current Schedule
            page.waitForFunction("() => { const p=document.querySelector('#OTprcdrDtl2'); return !!p && p.querySelectorAll('table tbody tr input[type=checkbox]').length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception e) { problems.add("the Current Schedule has no slots for table \"" + lastTable + "\""); }
        waitForAngular(600);
        return lastTable;
    }

    /** The slots of the Current Schedule as "From To [booked/elapsed]". */
    @SuppressWarnings("unchecked")
    public List<String> slots() {
        Object r = page.evaluate("() => {" + JS
                + " const p=document.querySelector('#OTprcdrDtl2'); if(!p) return [];"
                + " const t=[...p.querySelectorAll('table')].filter(vis).find(x=>/from time/i.test(norm(x.textContent)));"
                + " return t? [...t.querySelectorAll('tbody tr')].map(r=>{ const c=r.querySelector('input[type=checkbox]');"
                + "   return norm(r.textContent) + (c && c.disabled ? ' [booked/elapsed]' : ''); }) : []; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    private boolean tagFreeSlot() {
        Object tag = page.evaluate("() => {" + JS
                + " const p=document.querySelector('#OTprcdrDtl2'); if(!p) return '';"
                + " const t=[...p.querySelectorAll('table')].filter(vis).find(x=>/from time/i.test(norm(x.textContent)));"
                + " if(!t) return '';"
                + " const c=[...t.querySelectorAll('tbody tr input[type=checkbox]')].find(x=>!x.disabled);"
                + " if(!c) return ''; c.id='__otSlot'; return norm(c.closest('tr').textContent); }");
        lastSlot = tag == null ? "" : tag.toString();
        return !lastSlot.isEmpty();
    }

    private void setOtDate(String dmy) {
        Locator d = page.locator(PANE + " input[ng-model='otdetails.scheduledate']").first();
        try { d.click(new Locator.ClickOptions().setTimeout(5000)); d.fill(dmy); } catch (Exception e) { System.out.println("OTSchedule: the OT Date could not be typed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.querySelector(\"#OTprcdrDtl2 input[ng-model='otdetails.scheduledate']\"); if(e){ e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true})); } }");
        waitForAngular(900);
        // the slots of the NEW date load when the table is chosen again
        page.evaluate("() => { const e=document.querySelector(\"select[ng-model='otdetails.ottableid']\"); if(e){ const v=e.value; e.selectedIndex=0; e.dispatchEvent(new Event('change',{bubbles:true})); } }");
        waitForAngular(900);
        choose("otdetails.ottableid", lastTable, null, true);
        try {
            page.waitForFunction("() => { const p=document.querySelector('#OTprcdrDtl2'); return !!p && p.querySelectorAll('table tbody tr input[type=checkbox]').length>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(700);
    }

    /**
     * Tick a FREE slot in the Current Schedule (a real click; booked and elapsed slots are disabled) so From Time and To Time
     * fill in. The search starts {@code startDay} days from today (0 = today) and, when a day has no free slot left, moves on a
     * day at a time (up to {@code maxDays}). Returns a description; {@link #lastDate}, {@link #lastFrom}, {@link #lastTo} are set.
     */
    public String tickFreeSlot(int startDay, int maxDays) {
        problems.clear();
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        int skippedDays = 0;
        for (int d = startDay; d <= maxDays; d++) {
            if (d > 0) {
                setOtDate(java.time.LocalDate.now().plusDays(d).format(f));
                if (d > startDay) skippedDays++;
            }
            if (tagFreeSlot()) {
                try {
                    page.locator("#__otSlot").scrollIntoViewIfNeeded();
                    page.locator("#__otSlot").click(new Locator.ClickOptions().setTimeout(6000));
                } catch (Exception e) { problems.add("the slot checkbox could not be clicked"); return ""; }
                finally { page.evaluate("() => { const e=document.getElementById('__otSlot'); if(e) e.removeAttribute('id'); }"); }
                waitForAngular(1000);
                lastDate = model(PANE + " input[ng-model='otdetails.scheduledate']", "otdetails.scheduledate");
                Object times = page.evaluate("() => [...document.querySelectorAll('#OTprcdrDtl2 input[ng-model=\"inputTime\"]')].map(e=>(e.value||'').replace(/\\s+/g,' ').trim())");
                List<?> tl = times instanceof List ? (List<?>) times : new ArrayList<>();
                lastFrom = tl.size() > 0 ? String.valueOf(tl.get(0)) : "";
                lastTo = tl.size() > 1 ? String.valueOf(tl.get(1)) : "";
                if (lastFrom.isEmpty() || lastTo.isEmpty()) problems.add("ticking the slot did not fill From Time / To Time");
                return "ticked the free slot " + lastSlot + " on " + lastDate + " — From Time " + lastFrom + ", To Time " + lastTo
                        + (startDay > 0 ? " (the search was started " + startDay + " day(s) from today, as asked)" : "")
                        + (skippedDays > 0 ? " (moved on " + skippedDays + " day(s): the earlier day(s) had no free slot)" : "");
            }
        }
        problems.add("no free slot in the next " + maxDays + " days for \"" + lastTheatre + " / " + lastTable + "\"");
        return "";
    }

    // ---- doctor / staff ---------------------------------------------------------------------------------------------

    /** Department (PHYSICIAN), Specialty (Specialist), the first Doctor and the first Procedure. */
    public String selectDoctorDetails(String dept, String specialtyRe) {
        problems.clear();
        scrollToCss(sel("doctordetails.departmentid"));
        lastDoctorDept = choose("doctordetails.departmentid", dept, null, true);
        lastDoctorSpec = choose("doctordetails.classificationid", null, specialtyRe, true);
        waitOptions("doctordetails.payableid", 10000);
        lastDoctor = choose("doctordetails.payableid", null, null, true);
        String proc = choose("doctordetails.procedureid", null, null, true);
        return "Department " + lastDoctorDept + ", Specialty " + lastDoctorSpec + ", Doctor " + lastDoctor + ", Procedure " + proc;
    }

    public boolean clickAddDoctor() {
        problems.clear();
        if (!clickByHandler("AddDoctorDetailList", null)) { problems.add("the doctor Add button was not found"); return false; }
        waitForAngular(1200);
        boolean listed = gridRows("doctor type", "doctor").stream().anyMatch(r -> r.contains(lastDoctor));
        if (!listed) problems.add("\"" + lastDoctor + "\" is not in the Doctor grid");
        return listed;
    }

    /**
     * Designation → Procedure → Staff. The Staff list loads after the Designation, and some designations have no staff, so
     * designations are tried in list order until one offers staff.
     */
    public String selectStaffDetails() {
        problems.clear();
        scrollToCss(sel("staffdetails.designationid"));
        @SuppressWarnings("unchecked")
        List<Object> designations = (List<Object>) page.evaluate("(s) => [...document.querySelector(s).options].filter(o=>o.value).map(o=>o.textContent.replace(/\\s+/g,' ').trim())",
                sel("staffdetails.designationid"));
        for (Object d : designations) {
            String name = String.valueOf(d);
            lastDesignation = choose("staffdetails.designationid", name, null, false);
            if (lastDesignation.isEmpty()) continue;
            try {   // the staff list reloads for this designation
                page.waitForFunction("(s) => { const e=document.querySelector(s); return e && e.options.length>1 && e.options.length<2000; }",
                        sel("staffdetails.staffid"), new Page.WaitForFunctionOptions().setTimeout(6000));
            } catch (Exception ignore) { }
            if (optionCount("staffdetails.staffid") > 0 && optionCount("staffdetails.staffid") < 2000) break;
        }
        problems.removeIf(p -> p.startsWith("staffdetails.designationid has no option"));
        String proc = choose("staffdetails.procedureid", null, null, true);
        lastStaff = choose("staffdetails.staffid", null, null, true);
        return "Designation " + lastDesignation + ", Procedure " + proc + ", Staff " + lastStaff;
    }

    public boolean clickAddStaff() {
        problems.clear();
        if (!clickByHandler("AddStaffDetails", null)) { problems.add("the staff Add button was not found"); return false; }
        waitForAngular(1200);
        boolean listed = gridRows("designation", "staff").stream().anyMatch(r -> r.contains(lastStaff));
        if (!listed) problems.add("\"" + lastStaff + "\" is not in the Staff grid");
        return listed;
    }

    // ---- today's schedule list, and booking one when there is none -----------------------------------------------

    /**
     * The MRNs on the OT Schedule LIST for today: a Search with the screen's default date range (today to today), read
     * from the grid's data ({@code mrno}). Must be called on the list screen. OT Details uses these patients — it can
     * only pre-fill the OT sheet for a patient who has an OT schedule today.
     */
    @SuppressWarnings("unchecked")
    public List<String> scheduledMrnsToday() {
        clickByHandler("fngetOTScheduleGrid", "^\\s*search\\s*$");
        try {
            page.waitForFunction("() => { let ok=false; document.querySelectorAll('*').forEach(el=>{ if(ok) return; try{ const s=angular.element(el).scope();"
                    + " if(s && s.gridOptions && s.gridOptions.data && s.gridOptions.data.length) ok=true; }catch(e){} }); return ok; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { /* an empty list is a valid answer */ }
        waitForAngular(800);
        Object r = page.evaluate("() => { let data=null; document.querySelectorAll('*').forEach(el=>{ if(data) return; try{ const s=angular.element(el).scope();"
                + " if(s && s.gridOptions && s.gridOptions.data && s.gridOptions.data.length) data=s.gridOptions.data; }catch(e){} });"
                + " return data? [...new Set(data.map(d=>String(d.mrno||d.MRNo||'').trim()).filter(Boolean))] : []; }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<Object>) r) out.add(String.valueOf(o));
        return out;
    }

    /**
     * Book an OT schedule for TODAY (the whole OT Schedule flow, with the first patient Patient Search offers and the first
     * free slot today — no rolling on to another day, because OT Details only sees today's schedule). Must start on the list
     * screen. Returns the patient's MRN, or "" with {@link #scheduleProblem} saying why not.
     */
    public String scheduleForToday(String theatre, String table) {
        scheduleProblem = "";
        if (!clickNew()) { scheduleProblem = "New did not open the form"; return ""; }
        String mrn = null;
        for (String c : candidateMrns(10)) if (searchPatient(c)) { mrn = c; break; }
        if (mrn == null) { scheduleProblem = "no patient with a visit could be loaded"; return ""; }
        if (!clickGetProcedureDetails()) { scheduleProblem = "the Procedures List popup did not open"; return ""; }
        if (searchProcedures("Minor").isEmpty()) { scheduleProblem = "no procedures listed: " + problems; return ""; }
        if (tickFirstProcedure().isEmpty()) { scheduleProblem = "no procedure could be ticked"; return ""; }
        if (!clickAddProcedure()) { scheduleProblem = "the procedure was not added: " + problems; return ""; }
        if (!openOtDetailsTab()) { scheduleProblem = "the OT Details tab did not open"; return ""; }
        if (selectTheatre(theatre).isEmpty()) { scheduleProblem = "no theatre could be selected"; return ""; }
        selectTable(table);
        if (!ok()) { scheduleProblem = "no table / slots: " + problems; return ""; }
        if (tickFreeSlot(0, 0).isEmpty()) { scheduleProblem = "no free slot left today: " + problems; return ""; }
        selectDoctorDetails("PHYSICIAN", "specialist");
        if (!ok() || !clickAddDoctor()) { scheduleProblem = "doctor details: " + problems; return ""; }
        selectStaffDetails();
        if (!ok() || !clickAddStaff()) { scheduleProblem = "staff details: " + problems; return ""; }
        clickSaveAndGetToast();
        if (!saveSucceeded()) { scheduleProblem = "Save answered \"" + lastToast + "\""; return ""; }
        waitForAngular(1500);
        return mrn;
    }

    /** Why {@link #scheduleForToday} gave up ("" = it did not). */
    public String scheduleProblem = "";

    // ---- Save ---------------------------------------------------------------------------------------------------

    /**
     * Click <b>Save</b> ({@code fnIUDOTSchedules()}), answer a confirmation dialog if one appears, and return the toast.
     * Toasts are harvested with a MutationObserver (they fade), after clearing stale ones.
     */
    public String clickSaveAndGetToast() {
        page.evaluate("() => { window.__otToasts=[]; if(window.__otObs) window.__otObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('#toast-container .toast,.toast-message,.toast').forEach(t=>{ try{ t.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                + "   const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__otToasts.includes(t)) window.__otToasts.push(t); }); };"
                + " window.__otObs=new MutationObserver(grab); window.__otObs.observe(document.body,{childList:true,subtree:true}); }");
        if (!clickByHandler("fnIUDOTSchedules", "^\\s*save\\s*$")) System.out.println("OTSchedule: the Save button was not found");
        String confirm = "";
        for (int i = 0; i < 30; i++) {
            Object ans = page.evaluate("() => {" + JS
                    + " const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal,.bootbox,.sweet-alert')].filter(vis)"
                    + "   .find(m=>/save|submit|confirm|sure|proceed/i.test(norm(m.textContent)));"
                    + " if(box){ const y=[...box.querySelectorAll('button')].filter(vis)"
                    + "     .find(b=>/^\\s*(yes|ok|save|submit|confirm)\\s*$/i.test(norm(b.textContent)));"
                    + "   if(y){ const t=norm(box.textContent).slice(0,90); y.click(); return t; } }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{"
                    + "   const t=norm(el.textContent); window.__otToasts=window.__otToasts||[];"
                    + "   if(t && !window.__otToasts.includes(t)) window.__otToasts.push(t); });"
                    + " return ''; }");
            if (ans != null && !ans.toString().isEmpty()) confirm = ans.toString();
            boolean got = Boolean.TRUE.equals(page.evaluate(
                    "() => (window.__otToasts||[]).some(a=>/succe|saved|scheduled|booked|created|added|updated|please|error|fail|invalid|required|mandatory|already|select|enter/i.test(a))"));
            if (got) break;
            page.waitForTimeout(500);
        }
        lastSave = "clicked Save [fnIUDOTSchedules]" + (confirm.isEmpty() ? " (no confirmation dialog appeared)"
                : ", then confirmed the dialog (\"" + confirm + "\")");
        waitForAngular(800);
        Object r = page.evaluate("() => { const a=window.__otToasts||[];"
                + " return a.find(x=>/succe|saved|scheduled|booked|created|added|updated/i.test(x))"
                + "   || a.find(x=>/please|error|fail|invalid|required|mandatory|already|select|enter/i.test(x)) || a[0] || ''; }");
        lastToast = r == null ? "" : r.toString().replaceAll("^[^A-Za-z0-9]+", "").replaceAll("^KPJ\\s*Portal\\s*", "").trim();
        System.out.println("OTSchedule: save -> " + lastSave + "; toast: \"" + lastToast + "\"");
        return lastToast;
    }

    /** The save answered with a success message (and not a validation/error one). */
    public boolean saveSucceeded() {
        String t = lastToast == null ? "" : lastToast.toLowerCase();
        return t.matches(".*(succe|saved|scheduled|booked|created|added|updated).*")
                && !t.matches(".*(please|error|fail|invalid|not\\s*saved|mandatory|required|already).*");
    }
}
