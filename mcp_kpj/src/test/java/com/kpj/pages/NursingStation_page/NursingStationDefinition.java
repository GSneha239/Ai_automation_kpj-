package com.kpj.pages.NursingStation_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Nursing Station &gt; <b>Nursing Station Definition</b> (route {@code #/NursingStationDefinationList}) — Page Object.
 *
 * <p>Flow: list screen → top <b>Add</b> ({@code AddNursingStationDefination}) → enter Code
 * ({@code NursingStationDefination.code}), Remark ({@code .description}), Location ({@code .unitid}), Floor
 * ({@code .floorid}), Ward ({@code .wardid}) → tick the Department / Room List / Bed List selections → inner
 * <b>Add</b> ({@code AddDetails}) → <b>Save</b> ({@code IUDSaveTree}) → success toast.</p>
 */
public class NursingStationDefinition extends BasePage {

    public NursingStationDefinition(Page page) { super(page); }

    public String route = "", lastCode = "", lastFloor = "", lastWard = "", lastToast = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateTo(String baseUrl) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('a,li>a')].find(x=>/^\\s*nursing\\s*station\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/nursing\\s*station\\s*defin/i.test(norm(x.textContent)) || /stationdefination|nursingstationdef/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__nsdMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__nsdMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("NSDef.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        }
        if (!page.url().toLowerCase().contains("nursingstationdefination")) {
            try { page.evaluate("() => { window.location.hash = '#/NursingStationDefinationList'; }"); } catch (Exception ignore) { }
            waitForAngular(2000);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('[ng-click]')].some(e=>/AddNursingStationDefination/.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("NSDef.nav: Add button not ready"); }
        waitForAngular(800);
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("nursingstationdefination"); }

    // ---- helpers ---------------------------------------------------------

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    private String realSelectFirst(String selNg, int timeoutMs) {
        String sel = "select[ng-model='" + selNg + "']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception e) { return "(no-opt)"; }
        Object idx = page.evaluate("(s) => { const e=document.querySelector(s); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
        int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("realSelectFirst " + selNg + " failed: " + e.getMessage()); }
        waitForAngular(700);
        return chosen;
    }

    // ---- flow ------------------------------------------------------------

    /** Click the top <b>Add</b> ({@code AddNursingStationDefination}) to open the definition form. */
    public boolean clickTopAdd() {
        page.evaluate("() => { const b=[...document.querySelectorAll('[ng-click]')].find(x=>/AddNursingStationDefination/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.id='__nsdTopAdd'; }");
        try { page.locator("#__nsdTopAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("clickTopAdd: " + e.getMessage()); return false; }
        page.evaluate("() => { const e=document.getElementById('__nsdTopAdd'); if(e) e.removeAttribute('id'); }");
        try { page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>(e.getAttribute('ng-model')||'')==='NursingStationDefination.code' && e.offsetParent!==null)", null, new Page.WaitForFunctionOptions().setTimeout(10000)); } catch (Exception ignore) { }
        waitForAngular(800);
        return true;
    }

    /** Click the <b>IPD</b> PatientType radio (real click so ng-change loads the IPD ward/room/bed lists). */
    public String clickIpd() {
        Object tagged = page.evaluate("() => { const r=[...document.querySelectorAll(\"input[type=radio][ng-model='PatientType']\")].find(x=>/ipd/i.test(((x.closest('label')||x.parentElement||{}).textContent)||'') || x.value==='1'); if(!r) return ''; r.id='__ipdRadio'; return (r.closest('label')||r.parentElement||{}).textContent||'IPD'; }");
        if (tagged != null && !tagged.toString().isEmpty()) {
            try { page.locator("#__ipdRadio").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(4000)); }
            catch (Exception e) { try { page.locator("#__ipdRadio").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); } catch (Exception ignore) { } }
            page.evaluate("() => { const e=document.getElementById('__ipdRadio'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(1500);
        return tagged == null ? "" : tagged.toString().replaceAll("\\s+", " ").trim();
    }

    /** Enter Code, Remark and select Location -> Floor -> Ward (cascade). */
    public String fillHeader() {
        clickIpd();
        String code = "NSD" + String.format("%05d", Math.abs(System.nanoTime() % 100000));
        lastCode = code;
        setNg("NursingStationDefination.code", code);
        setNg("NursingStationDefination.description", "Auto nursing station " + code);
        realSelectFirst("NursingStationDefination.unitid", 8000);   // Location*
        waitForAngular(800);
        lastFloor = realSelectFirst("NursingStationDefination.floorid", 8000);   // Floor*
        waitForAngular(1200);
        lastWard = realSelectFirst("NursingStationDefination.wardid", 8000);     // Ward* (cascade)
        waitForAngular(800);
        return "Code=" + code + " | Floor=" + lastFloor + " | Ward=" + lastWard;
    }

    // ---- section (Room List / Bed List) detection by DOM-order heading ---

    /** Count enabled item checkboxes (excluding Check-All) that fall under the given section heading, in DOM order. */
    public int sectionItemCount(String sectionRe) {
        Object n = page.evaluate("(re) => { const rx=new RegExp(re,'i'); const norm=t=>(t||'').replace(/\\s+/g,' ').trim(); const all=[...document.querySelectorAll('h3,h4,h5,legend,input[type=checkbox]')]; let inSec=false, cnt=0; for(const el of all){ if(/^(H3|H4|H5|LEGEND)$/.test(el.tagName)){ const t=norm(el.textContent); if(/department list|room list|bed list/i.test(t)){ inSec=rx.test(t); } continue; } if(inSec && el.type==='checkbox'){ const ng=el.getAttribute('ng-model')||''; if(!/checkall/i.test(ng) && !el.disabled && el.offsetParent!==null) cnt++; } } return cnt; }", sectionRe);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** After selecting a Floor, iterate Wards (then other Floors) until the <b>Room List</b> has item checkboxes.
     *  Leaves that Floor+Ward selected. Returns "Floor / Ward" (or empty if none found). */
    public String findWardWithRoomList(int maxFloors) {
        // try current floor's wards first, then iterate floors
        java.util.List<Integer> floorIdx = new java.util.ArrayList<>();
        int floorCount = optionCount("NursingStationDefination.floorid");
        for (int f = 0; f < Math.min(floorCount, maxFloors); f++) floorIdx.add(f);
        for (int f : floorIdx) {
            selectByIndex("NursingStationDefination.floorid", f);
            waitForAngular(1200);
            lastFloor = selectedText("NursingStationDefination.floorid");
            int wardCount = optionCount("NursingStationDefination.wardid");
            for (int w = 0; w < wardCount; w++) {
                selectByIndex("NursingStationDefination.wardid", w);
                waitForAngular(1100);
                if (sectionItemCount("room list") > 0) {
                    lastWard = selectedText("NursingStationDefination.wardid");
                    System.out.println("findWardWithRoomList: rooms at Floor=" + lastFloor + " Ward=" + lastWard);
                    return lastFloor + " / " + lastWard;
                }
            }
        }
        return "";
    }

    private int optionCount(String selNg) {
        Object n = page.evaluate("(s) => { const e=document.querySelector(\"select[ng-model='\"+s+\"']\"); return e?[...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())).length:0; }", selNg);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }
    private void selectByIndex(String selNg, int realIdx) {
        page.evaluate("(a) => { const e=document.querySelector(\"select[ng-model='\"+a.s+\"']\"); if(!e) return; const reals=[...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); const o=reals[a.i]; if(o){ e.value=o.value; e.dispatchEvent(new Event('change',{bubbles:true})); } }",
                java.util.Map.of("s", selNg, "i", realIdx));
    }
    private String selectedText(String selNg) {
        Object t = page.evaluate("(s) => { const e=document.querySelector(\"select[ng-model='\"+s+\"']\"); return e?(e.options[e.selectedIndex]||{}).textContent||'':''; }", selNg);
        return t == null ? "" : t.toString().trim();
    }

    /** Tick a whole section (Room List / Bed List): its Check-All + up to {@code maxItems} item checkboxes. */
    private int tickSection(String sectionRe, int maxItems) {
        // native-click the section's check-all if present
        page.evaluate("(re) => { const rx=new RegExp(re,'i'); const norm=t=>(t||'').replace(/\\s+/g,' ').trim(); const all=[...document.querySelectorAll('h3,h4,h5,legend,input[type=checkbox]')]; let inSec=false; for(const el of all){ if(/^(H3|H4|H5|LEGEND)$/.test(el.tagName)){ const t=norm(el.textContent); if(/department list|room list|bed list/i.test(t)) inSec=rx.test(t); continue; } if(inSec && el.type==='checkbox' && /checkall/i.test(el.getAttribute('ng-model')||'') && !el.disabled && el.offsetParent!==null){ if(!el.checked) el.click(); let s; try{ s=angular.element(el).scope(); s.$apply(); }catch(e){} } } }", sectionRe);
        waitForAngular(900);
        Object checked = page.evaluate("(a) => { const rx=new RegExp(a.re,'i'); const norm=t=>(t||'').replace(/\\s+/g,' ').trim(); const all=[...document.querySelectorAll('h3,h4,h5,legend,input[type=checkbox]')]; let inSec=false, done=0, sc=null; for(const el of all){ if(/^(H3|H4|H5|LEGEND)$/.test(el.tagName)){ const t=norm(el.textContent); if(/department list|room list|bed list/i.test(t)) inSec=rx.test(t); continue; } if(inSec && el.type==='checkbox' && !/checkall/i.test(el.getAttribute('ng-model')||'') && !el.disabled && el.offsetParent!==null){ if(!el.checked && done<a.max){ el.click(); const l=el.closest('label'); if(l && !el.checked) l.click(); } if(el.checked) done++; sc=angular.element(el).scope(); } } try{ if(sc) sc.$apply(); }catch(e){} let total=0; { let inSec2=false; for(const el of all){ if(/^(H3|H4|H5|LEGEND)$/.test(el.tagName)){ const t=norm(el.textContent); if(/department list|room list|bed list/i.test(t)) inSec2=rx.test(t); continue; } if(inSec2 && el.type==='checkbox' && !/checkall/i.test(el.getAttribute('ng-model')||'') && el.checked) total++; } } return total; }",
                java.util.Map.of("re", sectionRe, "max", maxItems));
        waitForAngular(400);
        return checked instanceof Number ? ((Number) checked).intValue() : 0;
    }

    /** Count a section's item checkboxes that are present but <b>disabled</b> (nothing can be ticked there). */
    public int sectionDisabledCount(String sectionRe) {
        Object n = page.evaluate("(re) => { const rx=new RegExp(re,'i'); const norm=t=>(t||'').replace(/\\s+/g,' ').trim(); const all=[...document.querySelectorAll('h3,h4,h5,legend,input[type=checkbox]')]; let inSec=false, cnt=0; for(const el of all){ if(/^(H3|H4|H5|LEGEND)$/.test(el.tagName)){ const t=norm(el.textContent); if(/department list|room list|bed list/i.test(t)){ inSec=rx.test(t); } continue; } if(inSec && el.type==='checkbox'){ const ng=el.getAttribute('ng-model')||''; if(!/checkall/i.test(ng) && el.disabled) cnt++; } } return cnt; }", sectionRe);
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /** Human-readable state of one section: how many were ticked, or why none could be. */
    private String sectionResult(String label, String sectionRe, int maxItems) {
        int available = sectionItemCount(sectionRe);
        if (available == 0) {
            int disabled = sectionDisabledCount(sectionRe);
            return label + "=0 (" + (disabled > 0 ? disabled + " item(s) disabled" : "no items") + ")";
        }
        return label + "=" + tickSection(sectionRe, maxItems);
    }

    /** Tick the Department List, Room List and Bed List sections.
     *  Sections that are empty or disabled for the current patient type are reported, not forced. */
    public String selectDepartmentRoomBed() {
        String dept = sectionResult("DeptChecked", "department list", 3);
        String room = sectionResult("RoomChecked", "room list", 3);
        String bed = sectionResult("BedChecked", "bed list", 3);
        waitForAngular(500);
        return dept + " | " + room + " | " + bed;
    }

    /** Tick the Room List and Bed List sections. */
    public String selectRoomBed() {
        int room = tickSection("room list", 3);
        int bed = tickSection("bed list", 3);
        waitForAngular(500);
        return "RoomChecked=" + room + " | BedChecked=" + bed;
    }

    /** Count rows currently in the tree/detail grid (to confirm AddDetails added a node). */
    public int treeRows() {
        Object c = page.evaluate("() => { let n=0; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(!s) return; ['treelist','TreeList','nursingtree','detaillist','DetailsList','griddata'].forEach(k=>{ if(Array.isArray(s[k])) n=Math.max(n,s[k].length); }); if(s.grid&&s.grid.options&&Array.isArray(s.grid.options.data)) n=Math.max(n,s.grid.options.data.length); }catch(e){} }); return n; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Click the inner <b>Add</b> ({@code AddDetails}); capture any toast. */
    public String clickAddDetails() {
        return clickAndToast("AddDetails", "add");
    }

    /** Click <b>Save</b> ({@code IUDSaveTree}); capture the success toast. */
    public String clickSave() {
        lastToast = clickAndToast("IUDSaveTree", "save");
        return lastToast;
    }

    private String clickAndToast(String handlerRe, String kind) {
        // capture native alert/confirm dialogs too (JAlert style)
        final java.util.List<String> dialogMsgs = new java.util.ArrayList<>();
        java.util.function.Consumer<com.microsoft.playwright.Dialog> dh = d -> {
            try { dialogMsgs.add(d.message().replaceAll("\\s+", " ").trim()); } catch (Exception ignore) { }
            try { d.accept(); } catch (Exception ignore) { try { d.dismiss(); } catch (Exception ignore2) { } }
        };
        page.onDialog(dh);
        page.evaluate("() => { window.__nsdToasts=[]; if(window.__nsdObs) window.__nsdObs.disconnect(); try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast,.toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast],.sweet-alert,.bootbox,#jAlert_dialog,.jAlert,.modal .modal-body').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && el.offsetParent!==null && !window.__nsdToasts.includes(t)) window.__nsdToasts.push(t); }); };"
                + " window.__nsdObs=new MutationObserver(grab); window.__nsdObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        page.evaluate("(re) => { const rx=new RegExp(re); const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].find(x=>rx.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.id='__nsdBtn'; }", handlerRe);
        try { page.locator("#__nsdBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); } catch (Exception e) { System.out.println(kind + " click: " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__nsdBtn'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__nsdToasts||[]).some(a=>/saved|succes|added|please|enter|select|required|mandatory|fill|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { page.waitForTimeout(1500); }
        page.offDialog(dh);
        Object r = page.evaluate("() => { const a=window.__nsdToasts||[]; return a.find(x=>/saved|succes|added/i.test(x)) || a.find(x=>/please|select|enter|required|already|error/i.test(x)) || a.find(x=>x) || ''; }");
        String toast = r == null ? "" : r.toString().trim();
        if (toast.isEmpty() && !dialogMsgs.isEmpty()) toast = dialogMsgs.get(dialogMsgs.size() - 1);
        if (!dialogMsgs.isEmpty()) System.out.println(kind + " dialog(s): " + dialogMsgs);
        waitForAngular(400);
        return toast;
    }
}
