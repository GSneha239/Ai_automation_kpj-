package com.kpj.pages.OT_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * OT &gt; <b>OT Graphical View</b> - Page Object ({@code #/OTGraphicalView}).
 *
 * <p>Built from a recording of a person doing the flow in the built-in browser (devhis build v2026.11.18.491): sidebar OT &gt; OT Graphical View; the <b>Operation Theatre</b> select2
 * ({@code OTGraphicalView.operationtheatreid}) - choosing a theatre lists its OT tables as tiles ({@code li ng-click="GetScheduleDetails(OTList)"}; a green "Vacant" icon when
 * {@code OTList.OTTableStatus==0}, a red "Scheduled" icon when {@code ==1}); the legend counts Vacant / Scheduled / Total Tables; clicking a table fills the <b>Current Schedule</b> grid (From Time / To Time rows,
 * each with a checkbox {@code list.isselected} -&gt; {@code GetPatientDetails(list)}). Ticking a slot with no patient raises the warning "Patient Not Found!"; ticking a booked slot shows the patient details
 * ({@code PatientDetails.mrno / PatientName / PatientAge / Gender / DoctorName / SurgeryName}).</p>
 */
public class OTGraphicalView extends BasePage {

    private static final String JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    private static final String HREF = "#/otgraphicalview";
    private static final String TOP = "OT";
    private static final String THEATRE = "OTGraphicalView.operationtheatreid";

    public OTGraphicalView(Page page) { super(page); }

    /** What the last action could not do (empty = fine). */
    public final List<String> problems = new ArrayList<>();

    private static String firstLine(Exception e) {
        String m = String.valueOf(e.getMessage()).replace((char) 10, (char) 32).replace((char) 13, (char) 32);
        return m.length() > 220 ? m.substring(0, 220) : m;
    }

    // ---- navigation (the sidebar builds each level lazily: open the group ONCE and await it) ----------------------

    private boolean linkVisible() {
        Object r = page.evaluate("(h) => {" + JS + " return !![...document.querySelectorAll('a[href]')].find(x=>vis(x) && (x.getAttribute('href')||'').toLowerCase()===h); }", HREF);
        return Boolean.TRUE.equals(r);
    }

    private boolean waitLink(int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (linkVisible()) return true; page.waitForTimeout(250); }
        return linkVisible();
    }

    private int topState(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return -1;"
                + " const li=a.closest('li'); return li && li.classList.contains('menu-open') ? 1 : 0; }", label);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickTop(String label) {
        Object r = page.evaluate("(t) => {" + JS + " const a=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)===t); if(!a) return false; a.click(); return true; }", label);
        return Boolean.TRUE.equals(r);
    }

    /** OT &gt; OT Graphical View. Never types a hash (that raises the "Reload page" prompt and is bounced right after login). */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='OT')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        page.waitForTimeout(1500);
        for (int attempt = 0; attempt < 3 && !onScreen(); attempt++) {
            if (!linkVisible()) {
                if (topState(TOP) != 1) clickTop(TOP);
                waitLink(15000);
            }
            if (linkVisible()) {
                page.evaluate("(h) => {" + JS + " const l=[...document.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h); if(l) l.id='__otgLink'; }", HREF);
                try { page.locator("#__otgLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("OTGraphicalView.nav: click failed - " + firstLine(e)); }
                page.evaluate("() => { const e=document.getElementById('__otgLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            }
        }
        try {
            page.waitForFunction("() => !!document.querySelector(\"[ng-model='" + THEATRE + "']\")", null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("OTGraphicalView.nav: the screen is not ready"); }
        waitForAngular(1000);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().matches(".*#/otgraphicalview/?$"); }

    // ---- the Operation Theatre filter ------------------------------------------------------------------------------

    /** The real Operation Theatre names offered (no blank / "--Select--"). */
    public List<String> theatreNames() {
        List<String> out = new ArrayList<>();
        for (String n : Select2Pick.names(page, THEATRE)) { if (n != null && !n.trim().isEmpty() && !n.contains("--Select--")) out.add(n.trim()); }
        return out;
    }

    /** Choose an Operation Theatre (select2); returns "" when shown and held by the model, else the reason. Then waits for the table tiles to load. */
    public String pickTheatre(String name) {
        String r = Select2Pick.pick(page, THEATRE, name);
        page.waitForTimeout(1800);
        waitForAngular(800);
        return r;
    }

    // ---- the table tiles + legend -----------------------------------------------------------------------------------

    /** The legend counts {vacant, scheduled, total} (the text "4 Vacant 0 Scheduled 4 Total Tables"); -1 when not found. */
    public int[] legend() {
        Object r = page.evaluate("() => {" + JS + " const t=norm(document.body.innerText); const g=re=>{ const m=t.match(re); return m?parseInt(m[1],10):-1; };"
                + " return [g(/(\\d+)\\s*Vacant/i), g(/(\\d+)\\s*Scheduled/i), g(/(\\d+)\\s*Total Tables/i)]; }");
        int[] out = {-1, -1, -1};
        if (r instanceof List) { List<?> l = (List<?>) r; for (int i = 0; i < 3 && i < l.size(); i++) out[i] = ((Number) l.get(i)).intValue(); }
        return out;
    }

    /** The table tiles as {name, status} where status is 0 = vacant (green), 1 = scheduled (red). */
    public List<String[]> tables() {
        Object r = page.evaluate("() => {" + JS + " return [...document.querySelectorAll('li[ng-click*=\"GetScheduleDetails\"]')].map(l=>{ let st=''; try{ st=String(angular.element(l).scope().OTList.OTTableStatus); }catch(e){}"
                + " const red=[...l.querySelectorAll('img')].filter(vis).map(i=>i.getAttribute('src')||'').join(',');"
                + " return [norm(l.textContent), st, red.indexOf('Sheduled')>=0||red.indexOf('Scheduled')>=0?'red':(red.indexOf('Vacant')>=0?'green':'?')]; }); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; out.add(new String[]{String.valueOf(c.get(0)), String.valueOf(c.get(1)), String.valueOf(c.get(2))}); }
        return out;
    }

    /** Click the {@code idx}-th table tile ({@code GetScheduleDetails(OTList)}) and wait for the schedule to load. */
    public boolean clickTable(int idx) {
        try { page.locator("li[ng-click*='GetScheduleDetails']").nth(idx).click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Table tile: " + firstLine(e)); return false; }
        page.waitForTimeout(1800);
        waitForAngular(800);
        return true;
    }

    /** The "Current Schedule" rows (text, e.g. "08:00 09:00"), in order. */
    public List<String> scheduleRows() {
        Object r = page.evaluate("() => {" + JS + " const h=[...document.querySelectorAll('h1,h2,h3,h4,h5,.panel-title,.panel-heading,.box-title')].find(e=>/current schedule/i.test(e.textContent||'')); const box=h?(h.closest('.panel,.box,.card,fieldset')||h.parentElement):document;"
                + " return [...box.querySelectorAll('table tbody tr')].filter(vis).map(r=>norm(r.textContent)); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** The "Current Schedule" slots as {from-to text, disabled, background colour of the row, ServiceColor of the slot, patient name of the slot}. */
    public List<String[]> slotInfo() {
        Object r = page.evaluate("() => {" + JS + " const h=[...document.querySelectorAll('h1,h2,h3,h4,h5,.panel-title,.panel-heading,.box-title')].find(e=>/current schedule/i.test(e.textContent||'')); const box=h?(h.closest('.panel,.box,.card,fieldset')||h.parentElement):document;"
                + " return [...box.querySelectorAll('table tbody tr')].filter(vis).map(tr=>{ const cb=tr.querySelector(\"input[ng-model='list.isselected']\"); let item={}; try{ item=angular.element(tr).scope().list||{}; }catch(e){}"
                + " const bg=getComputedStyle(tr).backgroundColor; const cell=tr.cells[1]?getComputedStyle(tr.cells[1]).backgroundColor:'';"
                + " return [norm(tr.textContent), cb&&cb.disabled?'disabled':'enabled', bg+' | '+cell, String(item.ServiceColor||''), String(item.patientname||'')]; }); }");
        List<String[]> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) { List<?> c = (List<?>) row; String[] a = new String[5]; for (int i = 0; i < 5; i++) a[i] = i < c.size() && c.get(i) != null ? c.get(i).toString() : ""; out.add(a); }
        return out;
    }

    // ---- ticking a schedule slot -> patient details / "Patient Not Found!" ------------------------------------------------------

    private void armToasts() {
        page.evaluate("() => { window.__otT=[]; if(window.__otObs) window.__otObs.disconnect();"
                + " const grab=()=>document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__otT.includes(t)) window.__otT.push(t); });"
                + " window.__otObs=new MutationObserver(grab); window.__otObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
    }

    private void clearToasts() {
        page.evaluate("() => { try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast').forEach(t=>t.remove()); }");
    }

    /** Number of schedule checkboxes ({@code list.isselected}). */
    public int slotCheckboxes() {
        Object r = page.evaluate("() => document.querySelectorAll(\"input[ng-model='list.isselected']\").length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Tick the {@code idx}-th schedule checkbox ({@code GetPatientDetails(list)}); returns the toast text shown ("" when none within ~4 s). */
    public String tickSlot(int idx) {
        clearToasts();
        armToasts();
        try { page.locator("input[ng-model='list.isselected']").nth(idx).click(new Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { problems.add("Slot checkbox: " + firstLine(e)); return ""; }
        String found = "";
        for (int i = 0; i < 8 && found.isEmpty(); i++) {
            page.waitForTimeout(500);
            Object t = page.evaluate("() => (window.__otT||[]).join(' | ')");
            if (t != null && !t.toString().isEmpty()) found = t.toString();
        }
        page.evaluate("() => { if(window.__otObs) window.__otObs.disconnect(); }");   // do not leave a body-wide observer running
        return found;
    }

    /** The patient detail boxes {MRN, Name, Age, Gender, Doctor, Surgery} (blank when hidden / empty). */
    public String[] patientDetails() {
        Object r = page.evaluate("() => { const f=n=>{ const e=document.querySelector(\"[ng-model='PatientDetails.\"+n+\"']\"); return e ? e.value : ''; }; return [f('mrno'),f('PatientName'),f('PatientAge'),f('Gender'),f('DoctorName'),f('SurgeryName')]; }");
        String[] out = new String[6];
        List<?> l = r instanceof List ? (List<?>) r : new ArrayList<>();
        for (int i = 0; i < 6; i++) out[i] = i < l.size() && l.get(i) != null ? l.get(i).toString() : "";
        return out;
    }

    /** Whether a patient detail box ({@code PatientDetails.<field>}) can be edited (visible, not read-only, not disabled). */
    public boolean detailEditable(String field) {
        Object r = page.evaluate("(f) => {" + JS + " const e=document.querySelector(\"[ng-model='PatientDetails.\"+f+\"']\"); return !!e && vis(e) && !e.readOnly && !e.disabled; }", field);
        return Boolean.TRUE.equals(r);
    }

    /** Type {@code value} into a patient detail box (client side only - the screen has no Save) and read it back. */
    public String setDetail(String field, String value) {
        String sel = "[ng-model='PatientDetails." + field + "']";
        try {
            Locator box = page.locator(sel + ":visible").first();
            box.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(5000));
            box.click(new Locator.ClickOptions().setTimeout(5000));
            box.fill(value, new Locator.FillOptions().setTimeout(5000));
            box.dispatchEvent("change");
        } catch (Exception e) { problems.add("PatientDetails." + field + ": " + firstLine(e)); return ""; }
        waitForAngular(400);
        Object r = page.evaluate("(s) => { const e=document.querySelector(s); return e ? e.value : ''; }", sel);
        return r == null ? "" : r.toString();
    }

    /** True when the patient detail boxes are shown on the page. */
    public boolean patientDetailsVisible() {
        Object r = page.evaluate("() => {" + JS + " const e=document.querySelector(\"[ng-model='PatientDetails.mrno']\"); return !!e && vis(e); }");
        return Boolean.TRUE.equals(r);
    }

    /** Diagnostic: every visible control with its model / click handler / label / options. */
    public String dumpControls() {
        Object r = page.evaluate("() => {" + JS + " const f=[...document.querySelectorAll('input,select,textarea,button,a[ng-click],li[ng-click]')].filter(e=>vis(e) && !e.closest('.sidebar,.main-sidebar,.sidebar-menu') && !/setDatepickerDay|setNewYear|paginateYears|prevMonth|nextMonth|showYearsPagination/.test(e.getAttribute('ng-click')||''))"
                + ".map(e=>e.tagName.toLowerCase()+(e.type?'['+e.type+']':'')+' ngm='+(e.getAttribute('ng-model')||'')+' ngc='+(e.getAttribute('ng-click')||'')+' id='+(e.id||'')+(e.tagName==='SELECT'?' opts='+e.options.length:'')+(e.tagName==='BUTTON'||e.tagName==='A'||e.tagName==='LI'?' text='+norm(e.textContent).slice(0,30):'')+(e.disabled?' [disabled]':'')+(e.value&&e.tagName==='INPUT'&&e.type==='text'?' val='+e.value.slice(0,25):''));"
                + " return f.join('\\n'); }");
        return r == null ? "" : r.toString();
    }
}
