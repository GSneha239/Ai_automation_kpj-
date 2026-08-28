package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Bed</b> — configuration screen Page Object.
 *
 * <p>Flow: expand the <b>Application Configuration</b> menu → (Admission) → <b>Bed</b>; select a row; click
 * <b>Add</b>; fill the details; tick a checkbox; <b>Submit</b> → success toast.</p>
 *
 * <p>This is a NEW screen — the navigation + field details are discovered at runtime by {@link #dumpMenuTree()}
 * and {@link #dumpScreen()} and then hard-wired.</p>
 */
public class Bed extends BasePage {

    public Bed(Page page) { super(page); }

    // ---- discovery (temporary — used to learn the screen, then removed) --

    /** Dump every menu link (text + href) so we can find the exact "Application Configuration" / "Bed" wording. */
    public String dumpMenuTree() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const items=[...document.querySelectorAll('a')].map(a=>({t:norm(a.textContent), h:a.getAttribute('href')||''}))"
                + "   .filter(x=>x.t && x.t.length<40);"
                + " const seen=new Set(); const out=[];"
                + " for(const x of items){ const k=x.t+'|'+x.h; if(seen.has(k)) continue; seen.add(k); if(/config|admission|bed|master|setup/i.test(x.t) || (x.h&&/#\\//.test(x.h))) out.push(x.t+'  ->  '+x.h); }"
                + " return out.slice(0,120).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** Dump the current screen: URL/hash, grid rows, buttons (text + ng-click), and visible form fields. */
    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const out=[]; out.push('URL='+location.href);"
                + " const grids=[...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null);"
                + " out.push('tables='+grids.length);"
                + " grids.slice(0,3).forEach((t,i)=>{ const h=norm((t.querySelector('thead')||{}).innerText||'').slice(0,120); const rows=[...t.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent) && !/no records|no data/i.test(r.textContent)).length; out.push(' grid'+i+' rows='+rows+' headers=['+h+']'); });"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+(b.getAttribute('ng-click')?(' {'+b.getAttribute('ng-click')+'}'):'')).filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,40).join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** Dump the Add form/modal: inputs, selects (ng-model + label), checkboxes, and the Submit button. */
    public String dumpAddForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let t=''; if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=l.textContent; } if(!t){ const g=e.closest('.form-group,.row,.form-line,.col-sm-6,.col-md-6,.col-sm-4,td,div'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t=l.textContent; } } return norm(t).slice(0,30); };"
                + " const out=[];"
                + " const inputs=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden');"
                + " inputs.slice(0,50).forEach(e=>{ out.push(e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'+(e.tagName==='SELECT'?(' opts='+e.options.length):'')); });"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null);"
                + " out.push('checkboxes='+cbs.length);"
                + " const subs=[...document.querySelectorAll('button,input[type=submit]')].filter(b=>b.offsetParent!==null && /submit|save/i.test(b.textContent||b.value||'')).map(b=>norm(b.textContent||b.value)+' {'+(b.getAttribute('ng-click')||'')+'}');"
                + " out.push('submit/save buttons=\\n  '+subs.join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public static final String ROUTE = "#/BedMaster";

    /** Dump ui-grid rows (headers + first row cells) since the grid isn't a plain &lt;table&gt;. */
    public String dumpGrid() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const heads=[...document.querySelectorAll('.ui-grid-header-cell .ui-grid-cell-contents, .ui-grid-header-cell')].map(h=>norm(h.textContent)).filter(t=>t).filter((v,i,a)=>a.indexOf(v)===i);"
                + " const rows=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(r=>r.offsetParent!==null);"
                + " const first=rows[0]?[...rows[0].querySelectorAll('.ui-grid-cell-contents')].map(c=>norm(c.textContent)).slice(0,12):[];"
                + " return 'ui-grid rows='+rows.length+'\\n headers=['+heads.slice(0,15).join(' | ')+']\\n row0=['+first.join(' | ')+']'; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Fill the Bed Master Add form: a unique <b>Code</b> + <b>Bed Number</b> (description), and the mandatory
     * dropdowns (Location, Ward, Room Type, Bed Class, Department) to their first real option. Returns a summary.
     * The generated code is returned via {@link #lastCode}.
     */
    public String lastCode = "";
    public String lastBedNumber = "";

    /** Realistic bed-number/description values — a retry after "already exists" must still read like real data,
     *  never "Auto Bed &lt;CODE&gt;". */
    private static final String[] BED_NUMBERS = {
            "Ward A Bed 12", "ICU Bed 4", "General Ward Bed 7", "Maternity Bed 3",
            "Isolation Bed 2", "Recovery Bed 9", "Pediatric Bed 5", "Surgical Ward Bed 8"
    };

    /** {@code attempt} shifts both Code and Bed Number so a retry after an "already exists" toast submits
     *  genuinely different details. */
    public String fillBedDetails(int attempt) {
        String code = "AB" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastBedNumber = BED_NUMBERS[attempt % BED_NUMBERS.length] + (attempt >= BED_NUMBERS.length ? " " + (attempt / BED_NUMBERS.length + 1) : "");
        // These selects are select2-offscreen / cascade-dependent (per M6 Bed Planning FSD: Bed Class drives the Room
        // Type list; Location reloads Ward). selectedIndex+change alone doesn't commit them and setting a dependent
        // before its parent leaves it empty ("Please Select Ward!"). So: set in cascade order, POLL for each
        // dependent list to populate, and commit via Angular's triggerHandler('change') (fires the real ng listener).
        Object r = page.evaluate("async (a) => { const code=a.code; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const A=window.angular; const $=window.jQuery;"
                + " const setInp=(ng,v)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\"); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const realIdx=e=>[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " const waitOpts=async ng=>{ for(let k=0;k<25;k++){ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(e && realIdx(e)>=0) return e; await sleep(300); } return document.querySelector(\"select[ng-model='\"+ng+\"']\"); };"
                + " const setSel=async ng=>{ const e=await waitOpts(ng); if(!e) return '(no)'; const i=realIdx(e); if(i<0) return '(no-opt)'; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{ A.element(e).triggerHandler('change'); }catch(x){} if($){try{$(e).trigger('change');}catch(x){}} await sleep(250); const c=A.element(e).controller('ngModel'); const okv=(c && c.$modelValue!=null && c.$modelValue!==''); return (okv?'':'(set?)')+norm(e.options[i].textContent); };"
                + " const out={};"
                + " out.Code=setInp('BedMaster.code',code); out.BedNumber=setInp('BedMaster.description',a.bedNumber); setInp('BedMaster.BedType','AUTO');"
                // cascade order: Location -> Ward ; Bed Class -> Room Type ; Department -> Sub Dept
                + " out.Location=await setSel('BedMaster.Locationid'); out.Ward=await setSel('BedMaster.wardid');"
                + " out.BedClass=await setSel('BedMaster.bedclassid'); out.RoomType=await setSel('BedMaster.roomtypeid');"
                + " out.Department=await setSel('BedMaster.departmentid'); out.SubDept=await setSel('BedMaster.subdepartmentid');"
                + " out.BedType=await setSel('BedMaster.bedtypeid'); out.Room=await setSel('BedMaster.roomid');"
                + " out.BedCharge=await setSel('BedMaster.bedchargeserviceid'); out.NursingCharge=await setSel('BedMaster.nursingchargeserviceid');"
                + " return 'Code='+out.Code+' | BedNo='+out.BedNumber+' | Location='+out.Location+' | Ward='+out.Ward+' | BedClass='+out.BedClass+' | RoomType='+out.RoomType+' | Dept='+out.Department+' | SubDept='+out.SubDept+' | BedType='+out.BedType+' | Room='+out.Room+' | BedCharge='+out.BedCharge+' | NursingCharge='+out.NursingCharge; }",
                java.util.Map.of("code", code, "bedNumber", lastBedNumber));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Tick the <b>Non-Census (Lodger)</b> checkbox ({@code #chkIsNonCensus} / {@code BedMaster.isnoncensus}) with a
     *  REAL click so its Angular binding fires. Returns true if it ended up checked. */
    public boolean tickNonCensusCheckbox() {
        Object t = page.evaluate("() => { const c=document.querySelector('#chkIsNonCensus') || [...document.querySelectorAll('input[type=checkbox]')].find(x=>x.getAttribute('ng-model')==='BedMaster.isnoncensus') || [...document.querySelectorAll('input[type=checkbox]')].find(x=>x.offsetParent!==null); if(!c) return 'none'; c.id=c.id||'__bedCb'; return c.checked?'already':'togag'; }");
        if ("none".equals(String.valueOf(t))) { System.out.println("tickNonCensusCheckbox: no checkbox found"); return false; }
        String id = Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector('#chkIsNonCensus')")) ? "#chkIsNonCensus" : "#__bedCb";
        if (!"already".equals(String.valueOf(t))) {
            try { page.locator(id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("tickNonCensusCheckbox: click failed - " + e.getMessage()); }
        }
        waitForAngular(300);
        return Boolean.TRUE.equals(page.evaluate("(id) => { const c=document.querySelector(id); return !!(c && c.checked); }", id));
    }

    /**
     * Tick <b>any one amenity</b> — the Bed Side / Room Amenities are an ng-repeat checkbox list
     * ({@code AmenitiesList[$index].isselected}). Real-click the first un-checked amenity so its Angular binding
     * fires. Returns the ticked amenity's row label (empty if none / not ticked).
     */
    public String selectAmenity() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const cbs=[...document.querySelectorAll(\"input[type=checkbox][ng-model^='AmenitiesList']\")].filter(c=>c.offsetParent!==null); if(!cbs.length) return 'none';"
                + " const c=cbs.find(x=>!x.checked)||cbs[0]; c.setAttribute('id','__amenCb');"
                + " const row=c.closest('tr,li,label,div'); const lab=row?norm(row.textContent).slice(0,30):''; return lab||'amenity'; }");
        String label = String.valueOf(r);
        if ("none".equals(label)) { System.out.println("selectAmenity: no amenity checkbox found"); return ""; }
        try { page.locator("#__amenCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectAmenity: click failed - " + e.getMessage()); }
        waitForAngular(300);
        boolean checked = Boolean.TRUE.equals(page.evaluate("() => { const c=document.querySelector('#__amenCb'); return !!(c && c.checked); }"));
        return checked ? label : "";
    }

    /** Click <b>Submit</b> ({@code fnIUDBedMaster()}) and return the success toast (MutationObserver slow-toast
     *  pattern). Expected e.g. "Bed Saved Successfully.". */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__bedToasts=[]; if(window.__bedObs) window.__bedObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__bedToasts.includes(t)) window.__bedToasts.push(t); }); };"
                + " window.__bedObs=new MutationObserver(grab); window.__bedObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDBedMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__bedSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__bedSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__bedToasts||[]).some(a=>/bed|saved|success|added|updated|please|select|enter|required|exist/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__bedToasts||[]).includes(t)) (window.__bedToasts=window.__bedToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__bedToasts||[]; return a.find(x=>/bed .*(saved|added|updated)|saved successfully|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Dump anything amenities-related on the Add form (labels, ng-models, multi-selects, checkbox groups). */
    public String dumpAmenities() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[];"
                + " out.push('amenity-text='+JSON.stringify([...document.querySelectorAll('label,.control-label,legend,th,td,span,a,button,h3,h4')].filter(e=>/amenit/i.test(e.textContent||'') && norm(e.textContent).length<60).map(e=>e.tagName+':'+norm(e.textContent).slice(0,40)).filter((v,i,a)=>a.indexOf(v)===i).slice(0,10)));"
                + " out.push('amenity-ngmodels='+JSON.stringify([...document.querySelectorAll('[ng-model]')].filter(e=>/amenit/i.test(e.getAttribute('ng-model')||'')).map(e=>e.tagName+'['+(e.type||'')+'] '+e.getAttribute('ng-model'))));"
                + " out.push('multiselects='+JSON.stringify([...document.querySelectorAll('select[multiple]')].map(s=>(s.getAttribute('ng-model')||'?')+' opts='+s.options.length)));"
                + " out.push('ng-repeat-checkboxes='+JSON.stringify([...document.querySelectorAll('input[type=checkbox][ng-model]')].map(c=>c.getAttribute('ng-model')).filter((v,i,a)=>a.indexOf(v)===i).slice(0,15)));"
                + " out.push('select2-multi='+JSON.stringify([...document.querySelectorAll('.select2-container--multiple,.ui-select-multiple,ui-select[multiple]')].map(e=>norm(e.className).slice(0,40)).slice(0,5)));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /** Real-click the <b>Add</b> button ({@code AddBedMaster()}). */
    public boolean clickAdd() {
        Object t = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/AddBedMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__bedAdd'; return true; }");
        if (!Boolean.TRUE.equals(t)) { System.out.println("Bed.clickAdd: Add button not found"); return false; }
        try { page.locator("#__bedAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("Bed.clickAdd: click failed - " + e.getMessage()); }
        waitForAngular(1200);
        return true;
    }

    // ---- navigation ------------------------------------------------------

    /**
     * Expand the <b>Application Configuration</b> menu and open the <b>Bed</b> screen (via the Admission submenu if
     * present). Returns true once a config screen (grid/Add) is shown. Uses real clicks so AngularJS menu handlers
     * fire.
     */
    public boolean navigateViaMenu() {
        // 1) find + real-click the top-level "Application Configuration" menu.
        Object appCfg = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__appCfgMenu'; return true; }");
        if (Boolean.TRUE.equals(appCfg)) {
            try { page.locator("#__appCfgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Bed.navigateViaMenu: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        } else {
            System.out.println("Bed.navigateViaMenu: 'Application Configuration' menu not found");
        }
        // 2) if there's an "Admission" submenu group, expand it.
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // 3) click the "Bed" link.
        Object bed = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*bed\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__bedMenu'; return true; }");
        if (Boolean.TRUE.equals(bed)) {
            try { page.locator("#__bedMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Bed.navigateViaMenu: Bed click failed - " + e.getMessage()); }
        } else {
            System.out.println("Bed.navigateViaMenu: 'Bed' menu link not found");
        }
        waitForAngular(1500);
        return true;
    }
}
