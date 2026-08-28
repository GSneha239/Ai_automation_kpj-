package com.kpj.pages.Ip.BedManagement_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * IP &gt; Bed Management &gt; <b>Preadmission</b> — Page Object.
 *
 * <p>Flow: search by an admission date (retry other dates if the grid is empty) → select a patient row →
 * <b>Request MRD File</b>; if the patient has no MRD file, confirm the "create one?" popup, fill the MRD Details
 * popup → <b>Save</b> → success toast.</p>
 *
 * <p>NEW screen — navigation + fields discovered at runtime by the dump methods, then hard-wired.</p>
 */
public class Preadmission extends BasePage {

    public Preadmission(Page page) { super(page); }

    // ---- discovery (temporary) -------------------------------------------

    public String dumpMenuTree() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const items=[...document.querySelectorAll('a')].map(a=>({t:norm(a.textContent), h:a.getAttribute('href')||''})).filter(x=>x.t && x.t.length<45);"
                + " const seen=new Set(); const out=[];"
                + " for(const x of items){ const k=x.t+'|'+x.h; if(seen.has(k)) continue; seen.add(k); if(/bed manage|preadmission|pre-admission|pre admission|admission|mrd/i.test(x.t) || (x.h&&/#\\//.test(x.h))) out.push(x.t+'  ->  '+x.h); }"
                + " return out.slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " const dates=[...document.querySelectorAll('input')].filter(e=>e.offsetParent!==null && (/date/i.test(e.getAttribute('ng-model')||'') || /date/i.test(e.getAttribute('placeholder')||'') || e.type==='date')).map(e=>'ng=\"'+(e.getAttribute('ng-model')||'')+'\" ph=\"'+(e.getAttribute('placeholder')||'')+'\"');"
                + " out.push('date-inputs=\\n  '+dates.join('\\n  '));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+(b.getAttribute('ng-click')?(' {'+b.getAttribute('ng-click')+'}'):'')).filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,40).join('\\n  '));"
                + " const tbls=[...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null);"
                + " tbls.slice(0,2).forEach((t,i)=>{ const h=norm((t.querySelector('thead')||{}).innerText||'').slice(0,120); const rows=[...t.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent) && !/no records|no data/i.test(r.textContent)).length; out.push('table'+i+' rows='+rows+' headers=['+h+']'); });"
                + " const ug=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(r=>r.offsetParent!==null).length; out.push('ui-grid-rows='+ug);"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- search / grid ---------------------------------------------------

    /** Number of real data rows in the reservation table. */
    public int gridRowCount() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/patient name/i.test((x.querySelector('thead')||{}).innerText||'') && /admission date/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>{ const tx=norm(r.textContent); return tx && !/no records|no data|nothing found/i.test(tx) && [...r.querySelectorAll('td')].length>3; }).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Set the admission date ({@code Reservation.fromdate}, and {@code Reservation.todate} if present) and click
     *  <b>Search</b> ({@code fetchbedreservation()}). Returns the row count after the grid settles. */
    public int setDateAndSearch(String date) {
        page.evaluate("(d) => { const set=ng=>{ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=d; if(c){c.$setViewValue(d);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('Reservation.fromdate'); set('Reservation.todate'); }", date);
        waitForAngular(400);
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/fetchbedreservation/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        page.waitForTimeout(2500);
        return gridRowCount();
    }

    /** Try each candidate date until the grid has rows. Returns the date that yielded data, or null. */
    public String searchUntilData(java.util.List<String> dates) {
        for (String d : dates) {
            int n = setDateAndSearch(d);
            System.out.println("Preadmission.search: " + d + " -> rows=" + n);
            if (n > 0) return d;
        }
        return null;
    }

    /** Dump the first data row: cells + row ng-click + any mrdfileno on the row scope. */
    public String dumpFirstRow() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/patient name/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(!t) return '(no table)'; const row=[...t.querySelectorAll('tbody tr')].find(r=>[...r.querySelectorAll('td')].length>3 && norm(r.textContent) && !/no records/i.test(r.textContent)); if(!row) return '(no row)';"
                + " const cells=[...row.querySelectorAll('td')].map(td=>norm(td.textContent)).slice(0,14);"
                + " const ngc=row.getAttribute('ng-click')||(row.querySelector('[ng-click]')?row.querySelector('[ng-click]').getAttribute('ng-click'):'');"
                + " const radio=!!row.querySelector('input[type=radio],input[type=checkbox]');"
                + " let mrd=''; try{ const sc=angular.element(row).scope(); if(sc){ for(const k of ['res','item','row','data','reservation']){ if(sc[k]&&sc[k].mrdfileno!==undefined){ mrd=k+'.mrdfileno='+sc[k].mrdfileno; } } if(!mrd && sc.$parent){} } }catch(e){}"
                + " return 'cells=['+cells.join(' | ')+']\\n ng-click='+ngc+' hasRadio/cb='+radio+' '+mrd; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Select an <b>admittable</b> patient row — one whose status is <b>Pending</b> (not already Admitted/Cancelled),
     * so the Admission action can proceed (an already-admitted reservation makes the Admission form not open). Real
     * click on the row's selection control. Returns the row text, or null if no Pending row exists.
     */
    public String selectPendingPatientRow() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/patient name/i.test((x.querySelector('thead')||{}).innerText||'')); if(!t) return null;"
                + " const rows=[...t.querySelectorAll('tbody tr')].filter(r=>[...r.querySelectorAll('td')].length>3 && norm(r.textContent) && !/no records/i.test(r.textContent));"
                + " const row=rows.find(r=>/pending/i.test(r.textContent) && !/admitted|cancel/i.test(r.textContent)) || rows.find(r=>!/admitted|cancel/i.test(r.textContent)); if(!row) return null;"
                + " let target=row.querySelector('input[type=radio],input[type=checkbox]'); if(target){ target.id='__preRowSel'; } else { const cell=[...row.querySelectorAll('td')].find(td=>td.offsetParent!==null && norm(td.textContent) && !td.querySelector('button,a')); if(cell){ cell.id='__preRowSel'; } else { row.id='__preRowSel'; } }"
                + " return norm(row.textContent).slice(0,60); }");
        if (r == null) { System.out.println("selectPendingPatientRow: no Pending/admittable row"); return null; }
        try { page.locator("#__preRowSel").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectPendingPatientRow: click failed - " + e.getMessage()); }
        waitForAngular(500);
        return r.toString();
    }

    /** Select the Pending reservation row whose text contains {@code mrn} (the just-reserved patient) so the Admission
     *  acts on that exact patient. Falls back to {@link #selectPendingPatientRow()} if the MRN row isn't found. */
    /** Select the first admittable (Pending) patient row whose text does NOT contain any of {@code excludeMrns}
     *  (so a retry admits a DIFFERENT patient than the ones already tried). Returns the row text, or null. */
    public String selectPendingPatientRowExcluding(java.util.Collection<String> excludeMrns) {
        java.util.List<String> ex = new java.util.ArrayList<>(excludeMrns == null ? java.util.Collections.emptyList() : excludeMrns);
        Object r = page.evaluate("(ex) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const flat=s=>(s||'').replace(/\\s+/g,'');"
                + " const t=[...document.querySelectorAll('table')].find(x=>/patient name/i.test((x.querySelector('thead')||{}).innerText||'')); if(!t) return null;"
                + " const rows=[...t.querySelectorAll('tbody tr')].filter(r=>[...r.querySelectorAll('td')].length>3 && norm(r.textContent) && !/no records/i.test(r.textContent));"
                + " const notEx=r=>{ const txt=flat(r.textContent); return !ex.some(m=>m && txt.includes(m)); };"
                + " const row=rows.find(r=>/pending/i.test(r.textContent) && !/admitted|cancel/i.test(r.textContent) && notEx(r)) || rows.find(r=>!/admitted|cancel/i.test(r.textContent) && notEx(r)); if(!row) return null;"
                + " let target=row.querySelector('input[type=radio],input[type=checkbox]'); if(target){ target.id='__preRowSel'; } else { const cell=[...row.querySelectorAll('td')].find(td=>td.offsetParent!==null && norm(td.textContent) && !td.querySelector('button,a')); if(cell){ cell.id='__preRowSel'; } else { row.id='__preRowSel'; } }"
                + " return norm(row.textContent).slice(0,80); }", ex);
        if (r == null) { System.out.println("selectPendingPatientRowExcluding: no un-tried Pending row"); return null; }
        try { page.locator("#__preRowSel").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectPendingPatientRowExcluding: click failed - " + e.getMessage()); }
        waitForAngular(500);
        return r.toString();
    }

    public String selectPendingPatientRowByMrn(String mrn) {
        if (mrn == null || mrn.isEmpty()) return selectPendingPatientRow();
        Object r = page.evaluate("(mrn) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/patient name/i.test((x.querySelector('thead')||{}).innerText||'')); if(!t) return null;"
                + " const rows=[...t.querySelectorAll('tbody tr')].filter(r=>[...r.querySelectorAll('td')].length>3 && norm(r.textContent) && !/no records/i.test(r.textContent));"
                + " const row=rows.find(r=>(r.textContent||'').replace(/\\s+/g,'').includes(mrn) && /pending/i.test(r.textContent) && !/admitted|cancel/i.test(r.textContent)) || rows.find(r=>(r.textContent||'').replace(/\\s+/g,'').includes(mrn) && !/admitted|cancel/i.test(r.textContent)); if(!row) return null;"
                + " let target=row.querySelector('input[type=radio],input[type=checkbox]'); if(target){ target.id='__preRowSel'; } else { const cell=[...row.querySelectorAll('td')].find(td=>td.offsetParent!==null && norm(td.textContent) && !td.querySelector('button,a')); if(cell){ cell.id='__preRowSel'; } else { row.id='__preRowSel'; } }"
                + " return norm(row.textContent).slice(0,60); }", mrn);
        if (r == null) { System.out.println("selectPendingPatientRowByMrn: MRN " + mrn + " not found as Pending — falling back"); return selectPendingPatientRow(); }
        try { page.locator("#__preRowSel").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectPendingPatientRowByMrn: click failed - " + e.getMessage()); }
        waitForAngular(500);
        return r.toString();
    }

    /** Select the first patient row (real click so the Angular row-select handler fires). Returns MRN/name text. */
    public String selectFirstPatientRow() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/patient name/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(!t) return null; const row=[...t.querySelectorAll('tbody tr')].find(r=>[...r.querySelectorAll('td')].length>3 && norm(r.textContent) && !/no records/i.test(r.textContent)); if(!row) return null;"
                // prefer a radio/checkbox in the row, else a non-button cell
                + " let target=row.querySelector('input[type=radio],input[type=checkbox]'); if(target){ target.id='__preRowSel'; return norm(row.textContent).slice(0,60); }"
                + " const cell=[...row.querySelectorAll('td')].find(td=>td.offsetParent!==null && norm(td.textContent) && !td.querySelector('button,a')); if(cell){ cell.id='__preRowSel'; } else { row.id='__preRowSel'; }"
                + " return norm(row.textContent).slice(0,60); }");
        if (r == null) { System.out.println("selectFirstPatientRow: no row"); return null; }
        try { page.locator("#__preRowSel").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectFirstPatientRow: click failed - " + e.getMessage()); }
        waitForAngular(500);
        return r.toString();
    }

    // ---- Request MRD File (shared MRD component) -------------------------

    /** Click <b>Request MRD File</b> ({@code fnRequestMRDFile()}). Returns "confirm" (the "File is not generated…
     *  create one?" dialog appeared — no file), "report" (a report tab opened — patient already has a file), or
     *  "none". */
    public String clickRequestMrdFile() {
        int tabsBefore = page.context().pages().size();
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnRequestMRDFile()' && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/request mrd file/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 60; i++) {
            boolean confirm = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,[class*=confirm]')].some(m=>m.getBoundingClientRect().width>0 && /file is not generated|do you want to create/i.test(m.textContent||''))"));
            if (confirm) return "confirm";
            if (page.context().pages().size() > tabsBefore) return "report";
            page.waitForTimeout(800);
        }
        page.evaluate("() => { const no=[...document.querySelectorAll('.jconfirm button, button')].find(b=>/^no$/i.test((b.textContent||'').trim()) && b.offsetParent!==null); if(no) no.click(); }");
        return "none";
    }

    /** Click <b>Yes</b> on the "…create one?" confirm, then wait for the <b>MRD Details</b> modal. */
    public boolean confirmCreateMrdFile() {
        page.evaluate("() => { const y=[...document.querySelectorAll('.jconfirm button, .modal button, button')].find(b=>/^yes$/i.test((b.textContent||'').trim()) && b.offsetParent!==null); if(y) y.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /mrd details/i.test(m.textContent||'') && m.querySelector(\"button[ng-click='fnIUDAllocation()']\"))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
            waitForAngular(500);
            return true;
        } catch (Exception e) { System.out.println("confirmCreateMrdFile: MRD Details modal did not open"); return false; }
    }

    /**
     * Fill the <b>MRD Details</b> popup (all fields mandatory) and <b>Save</b> ({@code fnIUDAllocation()}). Sets each
     * ng-options {@code <select>} via selectedIndex + change (honouring the Rack → Row → Box cascade), plus the
     * text fields. Returns the toast (expected <b>"File Allocated Successfully."</b>).
     */
    public String fillMrdDetailsAndSave() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /mrd details/i.test(x.textContent||''));"
                + " const pick=(ng)=>{ const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const idx=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())); if(idx<0) return; e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " const setInp=(ng,v)=>{ const e=[...m.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " pick('MRDAllocation.mrdpatienttypeid'); pick('MRDAllocation.allocationlocationid'); pick('MRDAllocation.mrdfiletypeid'); pick('MRDAllocation.rackid');"
                + " setInp('MRDAllocation.filetypeno','1'); setInp('MRDAllocation.classificationtag','1'); }");
        waitForAngular(1200);
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /mrd details/i.test(x.textContent||''));"
                + " const pick=(ng)=>{ const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const idx=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())); if(idx<0) return; e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " pick('MRDAllocation.rowid'); }");
        waitForAngular(1200);
        page.evaluate("() => { window.__mrdToasts=[]; if(window.__mrdObs) window.__mrdObs.disconnect();"
                + " window.__mrdObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__mrdToasts.includes(t)) window.__mrdToasts.push(t); }); });"
                + " window.__mrdObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /mrd details/i.test(x.textContent||''));"
                + " const be=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='MRDAllocation.boxid'); if(be){ const idx=[...be.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.text||'').trim())); if(idx>=0){ be.selectedIndex=idx; be.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(be).trigger('change');}catch(err){}} } }"
                + " const save=[...m.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='fnIUDAllocation()' && x.offsetParent!==null); if(save) save.click(); }");
        try {
            page.waitForFunction("() => (window.__mrdToasts||[]).some(a=>/allocated|success|fill all/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("fillMrdDetailsAndSave: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__mrdToasts||[]; return a.find(x=>/allocated successfully/i.test(x)) || a.find(x=>/allocated|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- New bed reservation (New → search MRN → fill → bed → Reserve) --

    /** Click the <b>New</b> button ({@code OpenPopupScreen()}) to open the new bed-reservation popup. Returns true
     *  once a reservation popup/form with an MRN field is shown. */
    public boolean clickNew() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/OpenPopupScreen/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button,a')].find(x=>/^\\s*new\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1500);
        return Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0) || /reserv/i.test(location.hash)"));
    }

    /** In the patient-search popup, enter the MRN ({@code PatientData.MRNo}) and click <b>Search</b>
     *  ({@code SearchPatient(0)}). Then dump the resulting state (search results grid / reservation form). */
    public String searchPatientByMRN(String mrn) {
        page.evaluate("(mrn) => { const set=ng=>{ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null); if(e){ const c=angular.element(e).controller('ngModel'); e.value=mrn; if(c){c.$setViewValue(mrn);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } }; set('PatientData.MRNo'); set('Search.MRNo'); }", mrn);
        waitForAngular(400);
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/SearchPatient\\(/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        page.waitForTimeout(3500);
        return dumpReserveForm();
    }

    /** Enter the <b>Admission Date</b> ({@code Reservation.fromdate}) on the main pre-admission screen (before clicking
     *  New). Returns the value set. */
    public String enterAdmissionDate(String date) {
        page.evaluate("(d) => { const e=[...document.querySelectorAll(\"input[ng-model='Reservation.fromdate']\")].find(x=>x.offsetParent!==null) || document.querySelector(\"input[ng-model='Reservation.fromdate']\"); if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=d; if(c){c.$setViewValue(d);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", date);
        waitForAngular(400);
        Object r = page.evaluate("() => { const e=[...document.querySelectorAll(\"input[ng-model='Reservation.fromdate']\")].find(x=>x.offsetParent!==null); return e?e.value:''; }");
        return r == null ? "" : r.toString();
    }

    /** On the reserve form: enter MRN ({@code Reservation.mrno}) and click the search icon ({@code SearchPatientByMRNo()})
     *  to load the patient. Returns the MRN value now in the field. */
    public String reserveEnterMrnAndSearch(String mrn) {
        page.evaluate("(mrn)=>{ const e=document.querySelector('#addReservationMrn') || [...document.querySelectorAll(\"input[ng-model='Reservation.mrno']\")].find(x=>x.offsetParent!==null); if(e){ const c=angular.element(e).controller('ngModel'); e.value=mrn; if(c){c.$setViewValue(mrn);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } const b=[...document.querySelectorAll('button,a')].find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }", mrn);
        page.waitForTimeout(3500);
        // The MRN search can open a patient-results popup (SetSearchPatient) that must be selected, else Reserve
        // toasts "Please Select Patient!" — if a results row is present, click it to bind the patient. (Rule: after
        // entering an MRN you must Search AND select the resulting patient.)
        for (int i = 0; i < 3; i++) {
            Object picked = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const t=[...document.querySelectorAll('table')].find(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /name/.test(h) && (/blood group/.test(h)||/date of birth/.test(h)) && x.offsetParent!==null; }); if(!t) return 'no-popup';"
                    + " const row=[...t.querySelectorAll('tbody tr')].find(r=>[...r.querySelectorAll('td')].length>3 && norm(r.textContent) && !/no records/i.test(r.textContent)); if(!row) return 'no-row';"
                    + " const sel=[...row.querySelectorAll('[ng-click]')].find(e=>/SetSearchPatient|SelectPatient/i.test(e.getAttribute('ng-click')||'')) || row.querySelector('input[type=radio],input[type=checkbox],a,button') || row.querySelector('td'); if(!sel) return 'no-ctrl'; sel.id='__resSrchSel'; return 'ok'; }");
            if ("ok".equals(String.valueOf(picked))) {
                try { page.locator("#__resSrchSel").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) {}
                page.waitForTimeout(2000);
                break;
            }
            if ("no-popup".equals(String.valueOf(picked))) break; // patient auto-loaded, no popup
            page.waitForTimeout(700);
        }
        Object r = page.evaluate("()=>{ const e=[...document.querySelectorAll(\"input[ng-model='Reservation.mrno']\")].find(x=>x.offsetParent!==null); return e?e.value:''; }");
        return r == null ? "" : r.toString();
    }

    /** True if a patient is loaded/bound on the reserve form (the "Name : &lt;name&gt; MRN: &lt;mrn&gt;" banner shows
     *  after a successful MRN search). Used to skip MRNs that don't resolve to a reservable patient. */
    public boolean isPatientBound() {
        return Boolean.TRUE.equals(page.evaluate("() => { const b=(document.body&&document.body.innerText)||''; const m=b.match(/Name\\s*:\\s*([A-Za-z][^\\n]{1,45}?)\\s+MRN\\s*:/i); return !!(m && m[1] && m[1].trim().length>1); }"));
    }

    /** Fill the reserve form's details: Admission Date + all the mandatory selects (Room Type, Ward, Payor Status,
     *  Department, Sub Dept, Admitting Doctor, Admission Type, Pricing Policy — first real option, waiting for each
     *  to populate) + GL Approved Amount + Admitting Diagnosis. Returns a JSON-ish summary. */
    public String fillReserveDetails(String date) {
        Object r = page.evaluate("async (date)=>{ const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const sleep=ms=>new Promise(r=>setTimeout(r,ms)); const out={};"
                + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null) || [...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const realIdx=e=>[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
                + " const waitSel=async ng=>{ for(let k=0;k<20;k++){ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(e&&realIdx(e)>=0) return e; await sleep(300);} return document.querySelector(\"select[ng-model='\"+ng+\"']\"); };"
                + " const setSel=async ng=>{ const e=await waitSel(ng); if(!e) return '(no)'; const i=realIdx(e); if(i<0) return '(no-opt)'; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} await sleep(350); return norm(e.options[i].textContent); };"
                + " out.Date=setInp('Reservation.fromdate',date);"
                + " out.RoomType=await setSel('Reservation.classid');"
                // Ward: take the LAST real option and skip the auto-generated "auto…" wards (see selectWardFromEnd).
                + " out.Ward=await (async()=>{ const e=await waitSel('Reservation.wardid'); if(!e) return '(no)';"
                + "   const reals=[...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)) && !/^auto/i.test(norm(o.textContent)));"
                + "   if(!reals.length) return '(no-opt)'; const o=reals[reals.length-1]; e.value=o.value;"
                + "   e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}}"
                + "   await sleep(350); return norm(o.textContent); })();"
                // PAYOR is deliberately left untouched — the flow must not pick a payor (e.g. "MALAYAN RACING
                // ASSOCIATION"). Only Payor STATUS is set; Reservation.receivableid is never selected.
                + " out.PayorStatus=await setSel('Reservation.PayerStatusID');"
                + " out.Payor='(not selected - left as-is)';"
                + " out.Dept=await setSel('Reservation.DepartmentID'); out.SubDept=await setSel('Reservation.SubDepartmentID');"
                + " out.Doctor=await setSel('Reservation.DoctorID'); out.AdmType=await setSel('Reservation.AdmissionTypeID'); out.Pricing=await setSel('Reservation.tariffid');"
                // GL Approved Amt. + GL Limit are deliberately left untouched — the flow must not enter amounts.
                + " out.GL='(not entered - left as-is)'; out.GLLimit='(not entered - left as-is)';"
                + " out.Diag=setInp('Reservation.AdmittingDiagnosis','Fever');"
                + " return JSON.stringify(out); }", date);
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /**
     * Re-assert Department / Sub Dept / Doctor / Admission Type / Pricing — ONLY if currently empty — after
     * {@link #loadReserveBedsIterating}. That search changes Room Type (and Ward) repeatedly while hunting for a
     * combination with a free bed, and Room Type's own ng-change cascade clears these department-dependent
     * selects each time it fires — so a field {@link #fillReserveDetails} correctly filled against the FIRST Room
     * Type can end up empty by the time a bed is finally found under a LATER one. Confirmed live as the same
     * "filled, then silently cleared" symptom already fixed once for Admission's Sub Department. Never overwrites
     * a value that survived.
     */
    public String reassertDependentFields() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const fixed=[];"
                + " const ensure=async ng=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return;"
                + "   const c=A.element(e).controller('ngModel'); if(c && c.$modelValue!=null && (''+c.$modelValue).trim()!=='' && e.selectedIndex>0) return;"
                + "   const reals=[...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(!reals.length) return;"
                + "   e.value=reals[0].value; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}}"
                + "   await sleep(300); fixed.push(ng+' -> '+norm(reals[0].textContent)); };"
                + " for(const ng of ['Reservation.DepartmentID','Reservation.SubDepartmentID','Reservation.DoctorID','Reservation.AdmissionTypeID','Reservation.tariffid']) await ensure(ng);"
                + " return fixed.join(', '); }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Verify Room Type ({@code Reservation.classid}) + Ward ({@code Reservation.wardid}) actually committed on the
     *  reserve form (they're select2s that load late and can race the fill) — if a model is empty, wait for options
     *  and re-select the first real one, with retries. Returns the committed values. Prevents "Please select room type!". */
    public String ensureRoomTypeAndWard() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                // isWard: the re-select fallback must follow the same rule as the initial pick — skip the
                // auto-generated "auto…" wards and take the LAST real one, not the first.
                + " const ensure=async (ng,isWard)=>{ for(let a=0;a<6;a++){ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e){ await sleep(400); continue; } const c=A.element(e).controller('ngModel'); const ok=c && c.$modelValue!=null && (''+c.$modelValue).trim()!=='' && e.selectedIndex>0; if(ok) return norm(e.options[e.selectedIndex].textContent);"
                + "   const reals=[...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)) && (!isWard || !/^auto/i.test(norm(o.textContent))));"
                + "   const o=isWard ? reals[reals.length-1] : reals[0];"
                + "   if(o){ e.value=o.value; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} } await sleep(500); }"
                + "   const e2=document.querySelector(\"select[ng-model='\"+ng+\"']\"); const c2=e2?A.element(e2).controller('ngModel'):null; return (c2&&c2.$modelValue!=null&&(''+c2.$modelValue).trim()!=='')?norm(e2.options[e2.selectedIndex].textContent):'(empty)'; };"
                + " const rt=await ensure('Reservation.classid',false); const wd=await ensure('Reservation.wardid',true); return 'RoomType='+rt+' | Ward='+wd; }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Search</b> ({@code fetchCensusBedList();fetchnonCensusBedList()}) to load the available beds, then wait
     *  for the bed checkboxes ({@code nres.AddAdvanceDtl}) to appear. Returns the number of beds loaded. */
    public int loadReserveBeds() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/fetchCensusBedList|fetchnonCensusBedList/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 20; i++) {
            int n = bedCount();
            if (n > 0) return n;
            page.waitForTimeout(600);
        }
        return bedCount();
    }

    private int bedCount() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"input[type=checkbox][ng-model='nres.AddAdvanceDtl']\")].filter(c=>c.offsetParent!==null).length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Click Search and wait up to ~5s for beds (quick variant used when iterating wards). */
    private int loadBedsQuick() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/fetchCensusBedList|fetchnonCensusBedList/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 9; i++) { int n = bedCount(); if (n > 0) return n; page.waitForTimeout(600); }
        return bedCount();
    }

    /** Load beds, iterating Room Type × Ward combinations until vacant beds appear (bed availability depends on the
     *  Ward/Room Type we pick, not the patient). Leaves a combo with beds selected. Returns the bed count (0 if none).
     *  {@code maxRoomTypes} / {@code maxWards} <= 0 means NO CAP — walk every Room Type and, within each, every
     *  Ward, before giving up. A hardcoded cap here is exactly what leaves an empty Census/Non-Census Bed List on
     *  screen: if beds only exist under a Room Type past the cap, the loop stops before ever trying it. */
    public int loadReserveBedsIterating(int maxRoomTypes, int maxWards) {
        int n = loadReserveBeds();
        if (n > 0) return n;
        Object rtCountO = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='Reservation.classid']\"); return e?[...e.options].filter(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())).length:0; }");
        int rtCount = rtCountO instanceof Number ? ((Number) rtCountO).intValue() : 0;
        int roomTypes = maxRoomTypes > 0 ? Math.min(rtCount, maxRoomTypes) : rtCount;
        for (int rt = 0; rt < roomTypes; rt++) {
            String rtName = selectRealOption("Reservation.classid", rt);
            waitForAngular(700);
            // Walk the Ward list CCU-first (see selectWardFromEnd), then every other ward from the LAST entry
            // backwards, skipping the auto-generated "auto…" wards left by earlier runs — those are not real
            // wards and never have beds. If the chosen ward has NO free bed we move to the next one, so the flow
            // only settles on a ward that can actually be reserved.
            int wCount = wardOptionCount();
            int wards = maxWards > 0 ? Math.min(wCount, maxWards) : wCount;
            for (int w = 0; w < wards; w++) {
                String wName = selectWardFromEnd(w);
                waitForAngular(700);
                n = loadBedsQuick();
                System.out.println("loadReserveBedsIterating: RoomType=" + rtName + " Ward=" + wName + " beds=" + n);
                if (n > 0) return n;
            }
        }
        return 0;
    }

    /**
     * Ward options that are REAL wards — i.e. everything except the auto-generated test entries.
     *
     * <p>The Ward list is polluted with rows created by earlier automated runs (names beginning "auto"). Those are
     * not usable wards, so they are excluded and the list is walked from the END, where the genuine wards sit
     * (Ward 7C/8C/9C, NICU, Nursery, ICU, HDU, Labour &amp; Delivery, Daycare, Endoscopy, …).</p>
     */
    private static final String WARD_REALS_JS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + " const wardReals=e=>[...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))"
          + "   && !/^auto/i.test(norm(o.textContent)));";

    /** How many selectable (non-"auto") wards the Ward dropdown currently offers. */
    public int wardOptionCount() {
        Object n = page.evaluate("() => { " + WARD_REALS_JS
                + " const e=document.querySelector(\"select[ng-model='Reservation.wardid']\"); return e ? wardReals(e).length : 0; }");
        return n instanceof Number ? ((Number) n).intValue() : 0;
    }

    /**
     * Select a Ward by PRIORITY ({@code idx} 0 = highest priority), skipping any option whose name starts with
     * "auto". <b>CCU is tried first</b> — it has reliably had free beds across runs, so trying it before the rest
     * of the (mostly full) wards saves walking 20+ empty ones. Any remaining ward (excluding CCU) follows, counted
     * back from the LAST option, on the same "genuine wards sit at the end" heuristic as before. Returns the
     * chosen ward name.
     */
    public String selectWardFromEnd(int idx) {
        Object r = page.evaluate("(i) => { " + WARD_REALS_JS
                + " const A=window.angular; const e=document.querySelector(\"select[ng-model='Reservation.wardid']\"); if(!e) return '(no)';"
                + " const all=wardReals(e); if(!all.length) return '(no-opt)';"
                + " const ccu=all.filter(o=>/ccu/i.test(norm(o.textContent)));"
                + " const rest=all.filter(o=>!/ccu/i.test(norm(o.textContent))).reverse();"
                + " const order=[...ccu, ...rest];"
                + " const o=order[i]; if(!o) return '(no-opt)';"
                + " e.value=o.value; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}}"
                + " return norm(o.textContent); }", idx);
        return r == null ? "" : r.toString();
    }

    /** Select the Nth real option of a select (fires change + jQuery trigger); returns the chosen text. */
    private String selectRealOption(String ng, int realIdx) {
        Object r = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const e=document.querySelector(\"select[ng-model='\"+a.ng+\"']\"); if(!e) return '(no)'; const reals=[...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); const o=reals[a.i]; if(!o) return '(no-opt)'; e.value=o.value; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} return norm(o.textContent); }",
                java.util.Map.of("ng", ng, "i", realIdx));
        return r == null ? "" : r.toString();
    }

    /** Tick the first available bed (a {@code nres.AddAdvanceDtl} checkbox) with a REAL click. Returns the bed row text.
     *
     * <p>Confirmed live (Chrome DevTools on the actual app): the bed checkbox has NO {@code id} attribute at all —
     * {@code #__resBed} (the id a previous version of this method injected via JS, then tried to click via a
     * separate {@code page.locator("#__resBed")} call) never exists as a real element, which is why every prior
     * attempt hung on "waiting for locator #__resBed" until Playwright's timeout fired. The bed list is populated
     * by TWO async calls ({@code fetchCensusBedList()} + {@code fetchnonCensusBedList()}); if the second resolves
     * after the id was injected, AngularJS's {@code ng-repeat} can rebuild the row's DOM node (reassigning the
     * bound array tears down and recreates the repeated elements), silently dropping the injected id before the
     * separate locator call gets to it. Fix: find the checkbox AND click it inside the SAME synchronous JS
     * evaluation — no id, no round trip back to a Playwright locator, so there is no window for Angular to swap
     * the node out from under us. */
    public String selectFirstReserveBed() {
        // A STALE dialog from a PRIOR candidate MRN's rejected attempt (e.g. "Please Select Patient!") can still
        // be sitting on top of the page here, intercepting pointer events on the bed checkbox — confirmed live as
        // "<div class='ng-confirm...'> subtree intercepts pointer events", retried 3x, then the row detaches from
        // the DOM entirely. Dismiss any leftover confirm/alert FIRST so the click actually lands.
        page.evaluate("() => { [...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')].filter(m=>m.getBoundingClientRect().width>0).forEach(box=>{"
                + " const btn=[...box.querySelectorAll('button,a')].find(x=>/^(ok|yes|close|cancel)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(btn) btn.click(); }); }");
        waitForAngular(300);
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const find=()=>[...document.querySelectorAll(\"input[type=checkbox][ng-model='nres.AddAdvanceDtl']\")].find(x=>x.offsetParent!==null && !x.disabled && !x.checked)"
                + "   || [...document.querySelectorAll(\"input[type=checkbox][ng-model='nres.AddAdvanceDtl']\")].find(x=>x.offsetParent!==null && !x.disabled);"
                + " let c=null; for(let i=0;i<20;i++){ c=find(); if(c) break; await new Promise(res=>setTimeout(res,250)); }"
                + " if(!c) return null;"
                + " const row=c.closest('tr'); const rowText=row?norm(row.textContent).slice(0,50):'bed';"
                + " c.click(); try{ if(window.angular) angular.element(c).triggerHandler('click'); }catch(e){}"
                + " return rowText; }");
        if (r == null) { System.out.println("selectFirstReserveBed: no bed"); return null; }
        waitForAngular(400);
        return r.toString();
    }

    /** Click <b>Reserve</b> ({@code savebedreservation()}), answer <b>Yes/OK</b> on any confirm dialog, and return the
     *  success toast (MutationObserver pattern). */
    public String clickReserveAndGetToast() {
        page.evaluate("() => { window.__rsvToasts=[]; if(window.__rsvObs) window.__rsvObs.disconnect();"
                + " window.__rsvObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rsvToasts.includes(t)) window.__rsvToasts.push(t); }); });"
                + " window.__rsvObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/savebedreservation/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*reserve\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        // The Reserve raises a confirm ("Are you sure / Do you want to reserve?") — click Yes/OK while awaiting the toast.
        for (int i = 0; i < 30; i++) {
            page.evaluate("() => { const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')].find(m=>m.getBoundingClientRect().width>0 && /reserve|are you sure|do you want|confirm|proceed/i.test(m.textContent||''));"
                    + " if(box){ const y=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|reserve|continue|proceed)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(y) y.click(); }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t){ (window.__rsvToasts=window.__rsvToasts||[]); if(!window.__rsvToasts.includes(t)) window.__rsvToasts.push(t); } }); }");
            boolean done = Boolean.TRUE.equals(page.evaluate("() => (window.__rsvToasts||[]).some(a=>/reserv|success|saved|added|please|select|enter|already/i.test(a))"));
            if (done) break;
            page.waitForTimeout(600);
        }
        Object r = page.evaluate("() => { const a=window.__rsvToasts||[]; return a.find(x=>/reserv.*success|reserved|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(500);
        return r == null ? "" : r.toString().trim();
    }

    /** After reserving, verify the MRN appears in the <b>List of Reserved Beds</b> table (back on the main screen). */
    public boolean verifyReservedInList(String mrn) {
        // The "List of Reserved Beds" table only populates after a date Search — the reservation is dated TODAY,
        // so search today (retry a couple of times for the async grid) before checking for the MRN.
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        for (int attempt = 0; attempt < 3; attempt++) {
            try { setDateAndSearch(today); } catch (Exception ignore) { }
            for (int i = 0; i < 8; i++) {
                boolean found = Boolean.TRUE.equals(page.evaluate("(mrn) => { const norm=s=>(s||'').replace(/\\s+/g,''); const tables=[...document.querySelectorAll('table')].filter(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /patient name/.test(h) && (/admission date/.test(h) || /reserv/.test(h)); }); for(const t of tables){ if([...t.querySelectorAll('tbody tr')].some(r=>norm(r.textContent).includes(mrn))) return true; } return false; }", mrn));
                if (found) return true;
                page.waitForTimeout(600);
            }
        }
        return false;
    }

    /**
     * Set Room Type ({@code Reservation.classid}) to its first real option and Ward ({@code Reservation.wardid})
     * to its LAST real option, skipping the auto-generated "auto…" wards.
     */
    public String setRoomTypeAndWard() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const setSel=ng=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return '(no)'; let i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return '(no-opt)'; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); };"
                + " return 'RoomType='+setSel('Reservation.classid'); }");
        String rt = r == null ? "" : r.toString();
        waitForAngular(700);
        String ward = selectWardFromEnd(0);
        waitForAngular(1000);
        return rt + " | Ward=" + ward;
    }

    /** Click the bottom <b>New</b> button ({@code AddBedReservation()}) to open the new bed-reservation form. */
    public boolean clickReserveNew() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/AddBedReservation/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1800);
        return true;
    }

    /** Dump every button (text + ng-click) + the bed checkboxes, to pin down the Reserve control. */
    public String dumpAllButtons() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const btns=[...document.querySelectorAll('button,a,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>'\"'+norm(b.textContent||b.value)+'\" {'+(b.getAttribute('ng-click')||'')+'}').filter((v,i,a)=>a.indexOf(v)===i);"
                + " const beds=[...document.querySelectorAll(\"input[type=checkbox][ng-model='res.isselected']\")].filter(c=>c.offsetParent!==null).length;"
                + " return 'bed-checkboxes='+beds+'\\nbuttons=\\n  '+btns.join('\\n  '); }");
        return r == null ? "" : r.toString();
    }

    /** Select the first patient from the search-results table ({@code SetSearchPatient(...)}) — this loads the patient
     *  into the reservation form. Returns a dump of the resulting reservation form. */
    public String selectSearchResult() {
        Object tag = page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /name/.test(h) && /blood group/.test(h); }); if(!t) return 'no-table';"
                + " const row=[...t.querySelectorAll('tbody tr')].find(r=>[...r.querySelectorAll('td')].length>3 && !/no records/i.test(r.textContent||'')); if(!row) return 'no-row';"
                + " const sel=[...row.querySelectorAll('[ng-click]')].find(e=>/SetSearchPatient|SelectPatient/i.test(e.getAttribute('ng-click')||'')) || row.querySelector('input[type=radio],input[type=checkbox],a,button') || row.querySelector('td'); if(!sel) return 'no-ctrl'; sel.id='__srchSel'; return 'ok'; }");
        if (!"ok".equals(String.valueOf(tag))) { System.out.println("selectSearchResult: " + tag); return "(no result: " + tag + ")"; }
        try { page.locator("#__srchSel").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectSearchResult: click failed - " + e.getMessage()); }
        waitForAngular(2500);
        return dumpReserveForm();
    }

    /** Dump the new-reservation popup/form: MRN field, search button, all inputs/selects (ng-model + label), any
     *  bed grid, the Reserve button, and whether a "List of Reserved Beds" table is present. */
    public String dumpReserveForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " const scope=[...document.querySelectorAll('.modal,[role=dialog]')].find(m=>m.getBoundingClientRect().width>0) || document;"
                + " const labelOf=e=>{ let t=''; if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=l.textContent; } if(!t){ const g=e.closest('.form-group,.row,.col-sm-6,.col-md-6,.col-sm-4,td,div'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t=l.textContent; } } return norm(t).slice(0,26); };"
                + " const fields=[...scope.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden').slice(0,40).map(e=>e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'+(e.tagName==='SELECT'?(' opts='+e.options.length):''));"
                + " out.push('fields=\\n  '+fields.join('\\n  '));"
                + " const btns=[...scope.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+(b.getAttribute('ng-click')?(' {'+b.getAttribute('ng-click')+'}'):'')).filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,30).join('\\n  '));"
                + " const tbls=[...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null).map(t=>norm((t.querySelector('thead')||{}).innerText||'').slice(0,80));"
                + " out.push('tables=\\n  '+tbls.join('\\n  '));"
                + " out.push('hasReservedBedsTable='+/list of reserved beds/i.test(document.body.innerText||''));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- Print (bed reservation report) ----------------------------------

    /**
     * Click the footer <b>Print</b> button ({@code PrintBedReservation()}) for the selected row and return any report
     * tab(s) it opens (the bed-reservation report). Waits a few seconds for the new tab(s) to appear.
     */
    public java.util.List<Page> clickPrintAndGetTabs() {
        int before = page.context().pages().size();
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/PrintBedReservation/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button')].find(x=>/^\\s*print\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 30 && page.context().pages().size() <= before; i++) page.waitForTimeout(600);
        java.util.List<Page> tabs = new java.util.ArrayList<>();
        java.util.List<Page> all = page.context().pages();
        for (int i = before; i < all.size(); i++) tabs.add(all.get(i));
        return tabs;
    }

    // ---- Admission (carry the selected patient into IP Admission) --------

    /**
     * Click the footer <b>Admission</b> button ({@code getPatientDataforAdmission()}) — it carries the selected
     * pre-admission patient into the <b>IP Admission</b> screen ({@code #/Admission}), pre-filled. Dismisses any
     * confirm dialog, then waits for the Admission form (Save button {@code IUDAdmission();}). Returns true once the
     * Admission form is ready, so the caller can reuse the {@code com.kpj.pages.Ip.Admission} fill/save methods.
     */
    public boolean clickAdmission() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/getPatientDataforAdmission/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*admission\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        // A confirm ("Do you want to admit …?" / "Are you sure") can appear — click Yes/OK.
        for (int i = 0; i < 6; i++) {
            waitForAngular(500);
            Boolean clicked = (Boolean) page.evaluate("() => { const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')].find(m=>m.getBoundingClientRect().width>0 && /admit|are you sure|do you want/i.test(m.textContent||'')); if(!box) return false; const b=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|continue|proceed)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b){ b.click(); return true; } return false; }");
            if (Boolean.TRUE.equals(page.evaluate("() => window.angular && !!document.querySelector(\"button[ng-click='IUDAdmission();']\") && /#\\/Admission\\b/i.test(location.hash)"))) break;
        }
        try {
            page.waitForFunction("() => window.angular && document.querySelector(\"button[ng-click='IUDAdmission();']\") && /#\\/Admission\\b/i.test(location.hash)",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("clickAdmission: Admission form did not open - " + e.getMessage()); return false; }
        waitForAngular(1500);
        return page.url().toLowerCase().contains("admission");
    }

    /**
     * Clear the "Please update the mandatory details in NOK for &lt;name&gt;" gate that blocks the admission Save for a
     * carried/existing patient. The kin's mandatory fields get reset by the later sections' re-renders, so right
     * before Save: click the kin grid row (EditKinDetails loads it into the form) → fill the empty mandatory fields
     * (Relationship/Occupation/Family Name/Nationality/Country/Mobile Code) → Modify (ModifyKinDetails). Per the
     * Angular-digest gotcha, the fill and the Modify are done in SEPARATE evaluates with a wait between them.
     */
    public String ensureKinComplete() {
        // 0) If the NOK grid has MORE THAN ONE kin row (an existing kin + the one we added → one is incomplete and
        //    triggers "Please update the mandatory details in NOK"), DELETE the extras so a single row remains.
        for (int guard = 0; guard < 4; guard++) {
            Object more = page.evaluate("() => { const kt=[...document.querySelectorAll('table')].find(x=>/relation|nationality|guarantor/i.test((x.querySelector('thead')||{}).innerText||'')); if(!kt) return 0;"
                    + " const rows=[...kt.querySelectorAll('tbody tr')].filter(r=>{ const t=(r.textContent||'').trim(); return t && !/no records|no data/i.test(t) && [...r.querySelectorAll('td')].length>1; });"
                    + " if(rows.length<=1) return rows.length; const row=rows[rows.length-1];"   // delete the last extra row
                    + " try{ const sc=angular.element(row).scope(); if(sc && typeof sc.RemoveKinDetails==='function'){ sc.$apply(()=>sc.RemoveKinDetails(sc.$index)); return rows.length; } }catch(e){}"
                    + " const del=[...row.querySelectorAll('[ng-click]')].find(e=>/RemoveKinDetails|DeleteKin|removeKin/i.test(e.getAttribute('ng-click')||'')) || row.querySelector('.fa-trash,.glyphicon-trash,button.btn-danger,a.btn-danger'); if(del){ del.click(); } return rows.length; }");
            int n = more instanceof Number ? ((Number) more).intValue() : 0;
            if (n <= 1) break;
            waitForAngular(500);
            // dismiss any delete-confirm (Yes/OK)
            page.evaluate("() => { const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert')].find(m=>m.getBoundingClientRect().width>0 && /delete|remove|are you sure|do you want/i.test(m.textContent||'')); if(box){ const y=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|delete|confirm|proceed)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(y) y.click(); } }");
            waitForAngular(500);
        }
        // 1) Click the remaining kin row (EditKinDetails) to load it into the edit form.
        // REAL-CLICK the row's EDIT control — that is what loads the existing kin's values into the form fields.
        // Invoking EditKinDetails through the scope alone does not always populate them (the button's own handler
        // does more than the scope call), so tag the edit icon/button and click it for real; fall back to the
        // scope call only if no edit control can be found.
        Object edit = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const kt=[...document.querySelectorAll('table')].find(x=>/relation|nationality|guarantor|kin/i.test(((x.querySelector('thead')||{}).innerText)||''));"
                + " if(!kt) return '(no kin table)';"
                + " const row=[...kt.querySelectorAll('tbody tr')].find(r=>{ const t=norm(r.textContent); return t && !/no records|no data/i.test(t); });"
                + " if(!row) return '(no kin row)';"
                // The edit control is usually an <a>/<i>/<button> whose ng-click calls EditKinDetails.
                + " let b=[...row.querySelectorAll('[ng-click],a,button,i,span')].find(x=>/EditKinDetails/i.test(x.getAttribute('ng-click')||''));"
                + " if(!b) b=[...row.querySelectorAll('a,button,i,span')].find(x=>/edit|pencil|fa-edit/i.test((x.className||'')+' '+(x.title||'')+' '+norm(x.textContent)));"
                + " if(b){ b.id='__paKinEdit'; return 'click'; }"
                + " try{ const sc=angular.element(row).scope(); if(sc && typeof sc.EditKinDetails==='function'){ sc.$apply(()=>sc.EditKinDetails(sc.$index)); return 'scope-call'; } }catch(e){}"
                + " return '(no edit control)'; }");
        if ("click".equals(String.valueOf(edit))) {
            try { page.locator("#__paKinEdit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("kin Edit click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__paKinEdit'); if(e) e.removeAttribute('id'); }");
        }
        System.out.println("kin Edit: " + edit);
        waitForAngular(1000);
        // 2) Fill any empty mandatory kin fields (SEPARATE from the Modify — digest gotcha).
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const setSel=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const cur=(e.options[e.selectedIndex]||{}).text||''; if(e.selectedIndex>0 && !/^-*\\s*select/i.test(norm(cur))) return; let i=[...e.options].findIndex(o=>norm(o.textContent).toLowerCase()===txt.toLowerCase()); if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(x){}} };"
                + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng); if(!e || (e.value||'').trim()) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                // FORCE the kin's Nationality/Country to Malaysia — setSel() deliberately keeps an existing value,
                // so a carried kin stayed on whatever the record held (Afghanistan was seen), and when the exact
                // text "Malaysian" is not in the list setSel falls back to the FIRST real option — which is
                // alphabetically Afghanistan. This matches /malaysia/ and overrides regardless of what is set.
                + " const forceMalaysia=(ng)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return '(no field)';"
                + "   const i=[...e.options].findIndex(o=>o.value && /malaysia/i.test(norm(o.textContent))); if(i<0) return '(no Malaysia option)';"
                + "   e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{ angular.element(e).triggerHandler('change'); }catch(x){} const $=window.jQuery; if($){try{$(e).trigger('change');}catch(x){}}"
                + "   return norm(e.options[i].textContent); };"
                + " setSel('Registration.KinRelationID','Father'); setSel('Registration.KinOccupationID','Education');"
                + " const kinNat=forceMalaysia('Registration.NOKnationalid'); const kinCountry=forceMalaysia('Registration.KinCountryID');"
                + " console.log('NOK nationality='+kinNat+' country='+kinCountry);"
                + " setSel('Registration.KinMobileCountryCode','60');"
                // Kin mobile must be 10 digits — '123456789' (9) is rejected with
                // "Mobile number must be at least 10 digits!".
                + " setInp('Registration.KinFamilyName','Kin'); setInp('Registration.KinName','Test Kin'); setInp('Registration.KinMobileNo','1234567890');"
                // The kin's NRIC is MANDATORY (starred) and was never filled — the ng-model name varies, so match
                // any kin-ish NRIC/identity input, and fall back to an input labelled "NRIC" inside the kin form.
                + " const kinNric='850101101234';"
                + " const labelNear=e=>{ let n=e; for(let i=0;i<5&&n&&n.parentElement;i++){ n=n.parentElement; const l=n.querySelector('label'); if(l) return norm(l.textContent); } return ''; };"
                + " let nricFilled=[];"
                + " [...document.querySelectorAll('input')].forEach(e=>{ const ty=(e.type||'').toLowerCase();"
                + "   if(ty==='file'||ty==='date'||ty==='checkbox'||ty==='radio'||ty==='hidden') return;"
                + "   const ng=e.getAttribute('ng-model')||''; const lb=labelNear(e);"
                + "   const isKinNric=/kin|nok/i.test(ng) && /nric|nationalid|identity|icno|ic_no/i.test(ng);"
                + "   if(!isKinNric) return; if((e.value||'').trim()) { nricFilled.push(ng+'(kept)'); return; }"
                + "   const c=angular.element(e).controller('ngModel'); e.value=kinNric; if(c){c.$setViewValue(kinNric);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   nricFilled.push(ng+'='+kinNric); });"
                // FALLBACK when no ng-model matched: the kin NRIC is starred and MUST be filled for a Malaysian
                // (NRIC-based identity), so find it by its "NRIC" LABEL inside the kin form — located by walking up
                // from a known kin field (KinName) to the container that holds the kin inputs.
                + " if(!nricFilled.length){"
                + "   const anchor=[...document.querySelectorAll('input')].find(e=>(e.getAttribute('ng-model')||'')==='Registration.KinName');"
                + "   let box=anchor; for(let i=0;i<6&&box&&box.parentElement;i++){ box=box.parentElement;"
                + "     const cands=[...box.querySelectorAll('input')].filter(e=>{ const ty=(e.type||'').toLowerCase();"
                + "       if(ty==='file'||ty==='date'||ty==='checkbox'||ty==='radio'||ty==='hidden') return false;"
                + "       return /^\\s*nric\\s*\\*?\\s*$/i.test(labelNear(e)) || /nric/i.test(e.placeholder||''); });"
                + "     if(cands.length){ cands.forEach(e=>{ if((e.value||'').trim()){ nricFilled.push('label-NRIC(kept)'); return; }"
                + "       const c=angular.element(e).controller('ngModel'); e.value=kinNric; if(c){c.$setViewValue(kinNric);c.$render();}"
                + "       e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "       nricFilled.push('label-NRIC['+(e.getAttribute('ng-model')||'?')+']='+kinNric); }); break; } } }"
                + " console.log('kin NRIC: '+(nricFilled.length?nricFilled.join(', '):'(no kin NRIC field matched)')); }");
        waitForAngular(800);
        // 2a) Fill the kin the SAME WAY IP Admission does (Ip/Admission.fillNokSection), which admits successfully:
        // exact ng-models rather than guessed matchers — Title, the kin NRIC (Registration.KinNationalId) and
        // Email — then tick "Same As Patient Address" (chkSameasPatAddr), which is how that flow gets the kin's
        // State / City / address filled instead of driving the State→City cascade by hand.
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const setSel=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return '(no)';"
                + "   let i=[...e.options].findIndex(o=>norm(o.textContent).toLowerCase()===txt.toLowerCase());"
                + "   if(i<0) i=[...e.options].findIndex(o=>o.value && new RegExp(txt,'i').test(norm(o.textContent)));"
                + "   if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
                + "   if(i<0) return '(no opt)'; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); };"
                + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return '(no)';"
                + "   if((e.value||'').trim()) return e.value+'(kept)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const out=[];"
                + " out.push('KinTitle='+setSel('Registration.KinTitleID','Mr.'));"
                + " out.push('KinNRIC='+setInp('Registration.KinNationalId','900101071234'));"
                + " out.push('KinEmail='+setInp('Registration.KinEmail','kin.test@example.com'));"
                + " console.log('kin (IP-Admission style): '+out.join(', ')); }");
        waitForAngular(500);
        // "Same As Patient Address" — REAL click (a synthetic one does not fire Angular), exactly as IP Admission
        // does it. This is what fills the kin's State / City / District, which are starred and were left empty.
        Object sameAddr = page.evaluate("() => { const c=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.getAttribute('ng-model')==='chkSameasPatAddr');"
                + " if(!c) return '(no chkSameasPatAddr)'; if(c.checked) return 'already ticked'; c.id='__paSameAddr'; return 'tick'; }");
        if ("tick".equals(String.valueOf(sameAddr))) {
            try { page.locator("#__paSameAddr").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Same As Patient Address click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__paSameAddr'); if(e) e.removeAttribute('id'); }");
        }
        System.out.println("kin Same-As-Patient-Address: " + sameAddr);
        waitForAngular(800);
        // 2b) Fallback for kin TITLE + STATE + CITY if "Same As Patient Address" is absent on this screen. State is
        // starred and City/District hangs off it, so this is a CASCADE: set State, WAIT for the City list, set City.
        page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const kinSel=re=>[...document.querySelectorAll('select')].find(e=>{ const ng=e.getAttribute('ng-model')||'';"
                + "   return /kin|nok/i.test(ng) && re.test(ng) && e.offsetParent!==null; });"
                + " const real=e=>e?[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.textContent))):[];"
                + " const isEmpty=e=>{ if(!e) return true; const o=e.options[e.selectedIndex]; return !o||!o.value||/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)); };"
                + " const pick=(e,want)=>{ if(!e) return '(no field)'; const rs=real(e); if(!rs.length) return '(no options)';"
                + "   let t=want?rs.find(x=>new RegExp(want,'i').test(norm(x.o.textContent))):null; if(!t) t=rs[0];"
                + "   e.selectedIndex=t.i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "   return norm(t.o.textContent); };"
                + " const out=[];"
                + " const ti=kinSel(/title|prefix/i); out.push('Title='+(isEmpty(ti)?pick(ti,null):'(kept)'));"
                + " const st=kinSel(/state/i); out.push('State='+(isEmpty(st)?pick(st,null):'(kept)'));"
                // City only populates AFTER the State change round-trips — poll for its options.
                + " let ct=kinSel(/city|district/i);"
                + " for(let k=0;k<15;k++){ ct=kinSel(/city|district/i); if(ct && real(ct).length) break; await sleep(400); }"
                + " out.push('City='+(isEmpty(ct)?pick(ct,null):'(kept)'));"
                + " console.log('kin address: '+out.join(', ')); resolve(out.join(', ')); })");
        waitForAngular(800);
        // 3) Modify to commit the completed kin back into the grid.
        Object r = page.evaluate("() => { let done=false; document.querySelectorAll('*').forEach(el=>{ if(done)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.ModifyKinDetails==='function'){ x.$apply(()=>{ try{ x.ModifyKinDetails(); }catch(e){} }); done=true; break; } x=x.$parent; } }catch(e){} });"
                + " if(!done){ const b=[...document.querySelectorAll('button,a')].find(x=>/ModifyKinDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b){ b.click(); done=true; } } return done?'modified':'no-modify'; }");
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /** Re-set the admitting <b>Doctor</b> ({@code Admission.DoctorID}/{@code AttendingDoctorID}) — it gets cleared when
     *  {@code ensureKinComplete()} re-renders the form. Ensures Location + Department are set (so the doctor list
     *  {@code drpDoctor} is loaded), then picks the first doctor. Mirrors the reference fillAdmissionSection. */
    public String ensureAdmissionDoctor() {
        page.evaluate("() => { let adm=null, sc=null; document.querySelectorAll('*').forEach(el=>{ if(adm)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(x.Admission && typeof x.Admission==='object'){ adm=x.Admission; sc=x; break;} x=x.$parent; } }catch(e){} }); if(sc&&adm){ sc.$apply(()=>{ if(!adm.AdmissionLocationID) adm.AdmissionLocationID=1; if(!adm.DepartmentID) adm.DepartmentID=1; if(typeof sc.fnSetDoctor==='function'){ try{sc.fnSetDoctor();}catch(e){} } }); } }");
        waitForAngular(1500); // drpDoctor loads after Department
        Object r = page.evaluate("() => { let drd=null, adm=null, sc=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(!drd && s && Array.isArray(s.drpDoctor) && s.drpDoctor.length) drd=s.drpDoctor; let x=s; for(let i=0;i<15&&x;i++){ if(x.Admission && typeof x.Admission==='object' && !adm){ adm=x.Admission; sc=x; } x=x.$parent; } }catch(e){} }); if(!sc||!adm) return '(no scope)';"
                + " if(adm.DoctorID) return 'kept '+adm.DoctorID; if(!drd||!drd.length) return '(no doctors)'; const d=drd[0]; const pick=d.value!==undefined?d.value:(d.id!==undefined?d.id:d.DoctorID); sc.$apply(()=>{ adm.DoctorID=pick; adm.AttendingDoctorID=pick; }); return 'set '+pick; }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Diagnostic: the admission form's bed state — {@code Admission.BedID} + any bed grids (census/reserved). */
    public String dumpAdmissionBedState() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[];"
                + " let bedId='(none)'; document.querySelectorAll('*').forEach(el=>{ if(bedId!=='(none)')return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(x.Admission && x.Admission.BedID!==undefined && x.Admission.BedID!==null && (''+x.Admission.BedID)!==''){ bedId=''+x.Admission.BedID; break;} x=x.$parent; } }catch(e){} });"
                + " out.push('Admission.BedID='+bedId);"
                + " const grids=[...document.querySelectorAll('table')].filter(t=>/bed/i.test((t.querySelector('thead')||{}).innerText||'') && t.offsetParent!==null).map(t=>'['+norm((t.querySelector('thead')||{}).innerText).slice(0,50)+'] rows='+[...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox],input[type=radio]') && !/no records/i.test(r.textContent||'')).length);"
                + " out.push('bed-grids='+JSON.stringify(grids));"
                + " return out.join(' | '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Ensure the <b>Date Of Birth</b> is set on the Admission form — the patient carried from preadmission can arrive
     * without a committed DOB, which blocks Save with "Please Enter Date Of Birth !". If empty, set a valid date.
     * Returns the DOB value now in the field.
     */
    public String ensureDateOfBirth() {
        Object r = page.evaluate("() => { const e=document.querySelector('#txtDateOfBirth') || [...document.querySelectorAll('input')].find(x=>/DateOfBirth|(^|\\.)dob$/i.test(x.getAttribute('ng-model')||'')); if(!e) return '(no-field)';"
                + " if((e.value||'').trim()) return e.value+' (kept)'; const c=angular.element(e).controller('ngModel'); const v='01/01/1990'; e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return (e.value||'')+' (set)'; }");
        waitForAngular(300);
        return r == null ? "" : r.toString();
    }

    /**
     * Ensure the patient <b>NRIC</b> ({@code Registration.NationalId}) is set — the patient carried from preadmission
     * can arrive without it, blocking Save with "Please Enter NRIC!". If empty, set a valid, unique NRIC whose
     * YYMMDD matches the DOB fallback (01/01/1990 → 900101) + a valid place code + a unique serial. Returns the value.
     */
    public String ensureNRIC() { return ensurePatientIdentity(); }

    /**
     * Ensure the patient's identity is complete so Save isn't blocked by "Please Enter NRIC!/Passport No.!". The
     * carried preadmission patient arrives with <b>Identification Type</b> ({@code Registration.ICCardTypeID}) unset
     * (model "0") — so the form can't decide NRIC-vs-Passport and flags both. Set ICCardType = <b>New IC</b> (via
     * Angular's {@code triggerHandler('change')}, since the field is hidden) and commit a valid <b>NRIC</b> on the
     * main {@code Registration.NationalId} inputs. Returns a diagnostic string.
     */
    public String ensurePatientIdentity() {
        String nric = "900101" + "10" + String.format("%04d", Math.abs(System.nanoTime() % 10000));
        Object r = page.evaluate("(nric) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const out=[];"
                // Identification Type = New IC (hidden select → drive Angular's change listener directly).
                + " const ic=[...document.querySelectorAll(\"select[ng-model='Registration.ICCardTypeID']\")][0];"
                + " if(ic){ let i=[...ic.options].findIndex(o=>/new\\s*ic/i.test(o.textContent||'')); if(i<0) i=[...ic.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)) && !/(^|:)0$/.test(o.value)); if(i>=0){ ic.selectedIndex=i; ic.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(ic).triggerHandler('change');}catch(x){} const cc=A.element(ic).controller('ngModel'); out.push('ICType='+norm(ic.options[i].textContent)+'(model='+(cc?cc.$modelValue:'?')+')'); } } else out.push('ICType(no-field)');"
                // NRIC — commit on every main Registration.NationalId input (skip the mother/kin variants).
                + " const cands=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")]; let done=0;"
                + " cands.forEach(e=>{ const c=A.element(e).controller('ngModel'); const v=(e.value||'').trim()||nric; try{e.removeAttribute('disabled');e.disabled=false;e.removeAttribute('readonly');}catch(x){} e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); done++; });"
                + " const first=cands[0]; const c2=first?A.element(first).controller('ngModel'):null; out.push('NRIC set on '+done+' input(s), model='+(c2&&c2.$modelValue!=null?c2.$modelValue:'empty'));"
                // Mobile No (+ country code) — carried patient can arrive without it → "Please Enter Mobile No!".
                + " const setF=(sel,v)=>{ const e=[...document.querySelectorAll(sel)][0]; if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); const cur=(e.value||'').trim(); const modelOk=c && c.$modelValue!=null && (''+c.$modelValue).trim()!=='' && (''+c.$modelValue).trim()!=='0'; if(cur && cur!=='0' && modelOk) return cur+'(kept)'; try{e.removeAttribute('disabled');e.disabled=false;}catch(x){} e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); const c2=A.element(e).controller('ngModel'); return v+'(set,model='+(c2&&c2.$modelValue!=null?c2.$modelValue:'empty')+')'; };"
                + " out.push('MobileCC='+setF(\"select[ng-model='Registration.MobileCountryCode'],input[ng-model='Registration.MobileCountryCode']\",'60'));"
                + " out.push('Mobile='+setF(\"input[ng-model='Registration.MobileNo']\",'0123456789'));"
                // NATIONALITY = MALAYSIAN, ALWAYS. Carried preadmission patients arrive with whatever their record
                // holds (Myanmar / Afghanistan were both seen), which drags the City/State/Country cascade with it.
                + " const natEl=[...document.querySelectorAll(\"select[ng-model='Registration.NationalityID']\")][0];"
                + " if(natEl){ const mi=[...natEl.options].findIndex(o=>o.value && /malaysia/i.test(norm(o.textContent)));"
                + "   if(mi>=0 && natEl.selectedIndex!==mi){ natEl.selectedIndex=mi; natEl.dispatchEvent(new Event('change',{bubbles:true}));"
                + "     try{A.element(natEl).triggerHandler('change');}catch(x){} if($){try{$(natEl).trigger('change');}catch(x){}} } }"
                + " const natTxt=natEl?norm((natEl.options[natEl.selectedIndex]||{}).textContent||''):'';"
                // A MALAYSIAN IS IDENTIFIED BY NRIC, NOT A PASSPORT — so Passport No. is CLEARED here and the NRIC
                // is what gets filled (see the identity block above). The screen stars Passport No. even for a
                // Malaysian, but that is an APP DEFECT (the Registration flow asserts the same thing) and is not
                // worked around by inventing a passport number.
                + " if(true){"
                // Match the Passport No. input by its VISIBLE LABEL (the ng-model name is unknown/varies) — walk up to
                // the nearest label; also check placeholder + ng-model. Exclude the Passport EXPIRY/date field.
                + "   const labelOf=e=>{ let n=e; for(let i=0;i<5&&n&&n.parentElement;i++){ n=n.parentElement; const l=n.querySelector('label'); if(l) return norm(l.textContent); } return ''; };"
                // EXCLUDE file/date/checkbox inputs. One of the /passport/ matches is a FILE input (passport scan):
                // assigning a value to it throws "This input element accepts a filename, which may only be
                // programmatically set to the empty string", which aborted the whole Admission attempt. The old
                // code only ever set '' — legal on a file input — which is why clearing never hit this.
                + "   const passInputs=[...document.querySelectorAll('input')].filter(e=>{ const ty=(e.type||'').toLowerCase();"
                + "     if(ty==='file'||ty==='date'||ty==='checkbox'||ty==='radio'||ty==='hidden') return false;"
                // Match on the FIELD'S OWN identity (ng-model / placeholder), or a label that IS "Passport …".
                // The old test ORed in labelOf(e), which walks UP to any nearby label — so a kin field sitting
                // near a "Passport No." label was treated as a passport input and Registration.KinFamilyName got
                // blanked right after the kin fill set it. Never touch kin/NOK fields here.
                + "     const ng=(e.getAttribute('ng-model')||'').toLowerCase(); if(/kin|nok/.test(ng)) return false;"
                + "     const own=(ng+' '+(e.getAttribute('placeholder')||'')).toLowerCase();"
                + "     const lab=labelOf(e).toLowerCase();"
                + "     const hit=/passport/.test(own) || /^passport/.test(lab);"
                + "     return hit && !/expir|date/.test(own+' '+lab); });"
                + "   let cleared=[]; passInputs.forEach(e=>{ const c=A.element(e).controller('ngModel'); const ng=e.getAttribute('ng-model')||''; const had=(e.value||'').trim();"
                + "     try{e.removeAttribute('disabled');e.disabled=false;e.removeAttribute('readonly');}catch(x){}"
                + "     e.value=''; if(c){c.$setViewValue('');c.$render();} e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "     e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('keyup',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true}));"
                + "     if(had) cleared.push((ng||labelOf(e))+':\"'+had+'\"->\"\"'); });"
                // Null the passport on every Registration scope too (covers whatever key it binds to).
                + "   const seen=new Set(); const passKeys=new Set(passInputs.map(e=>{ const ng=e.getAttribute('ng-model')||''; return ng.indexOf('.')>=0?ng.slice(ng.lastIndexOf('.')+1):ng; }).filter(Boolean));"
                + "   document.querySelectorAll('*').forEach(el=>{ try{ const s=A.element(el).scope(); let x=s; for(let i=0;i<12&&x;i++){ const reg=x.Registration; if(reg&&typeof reg==='object'&&!seen.has(reg)){ seen.add(reg); passKeys.forEach(k=>{ if(reg[k]!=null&&(''+reg[k]).trim()!=='') reg[k]=''; }); Object.keys(reg).forEach(k=>{ if(/passport/i.test(k)&&!/expir|date/i.test(k)&&reg[k]!=null&&(''+reg[k]).trim()!=='') reg[k]=''; }); } x=x.$parent; } }catch(e){} });"
                + "   out.push('Nationality='+(natTxt||'?')+' (Malaysian -> NRIC, no passport) passportInputs='+passInputs.length+' cleared='+JSON.stringify(cleared)); }"
                + " return out.join(' | '); }", nric);
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /**
     * BEFORE Save: ensure the flaky mandatory fields are actually filled — especially <b>City</b>
     * ({@code Registration.ResCityID}), which loads ASYNC from the postcode/State and is often left empty (→ the
     * Save silently no-ops / "Please Select City"). Iterates States to load a City if empty (like OP Registration),
     * then reports every mandatory field's value + any still MISSING. Returns "FILLED: ... | MISSING: ...".
     */
    /**
     * SELECT a Kin row in the NOK/Guarantor grid. The Admission Save is rejected with <i>"Please Select Kin!"</i>
     * when no row is SELECTED — filling the kin form and having a row in the grid is not enough, the row's own
     * radio/checkbox has to be ticked. Real-clicked so the row's ng-click/ng-change fires.
     *
     * @return what was selected, for the report
     */
    public String selectKinRow() {
        try {
            Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const kt=[...document.querySelectorAll('table')].find(x=>/relation|nationality|guarantor|kin/i.test(((x.querySelector('thead')||{}).innerText)||''));"
                    // Not finding a row is NOT a fault — the kin can already be committed (e.g. via "Same As
                    // Patient Address" on an existing kin), and the save then succeeds without a row to tick.
                    // Only "Please Select Kin!" indicates it was actually needed.
                    + " if(!kt) return 'n/a (no kin table on this screen)';"
                    + " const rows=[...kt.querySelectorAll('tbody tr')].filter(r=>{ const t=norm(r.textContent); return t && !/no records|no data/i.test(t); });"
                    + " if(!rows.length) return 'n/a (kin already committed - no row to tick)';"
                    + " for(const r of rows){ const box=r.querySelector('input[type=radio],input[type=checkbox]');"
                    + "   if(box){ if(box.checked) return 'already selected: '+norm(r.textContent).slice(0,40);"
                    + "     box.id='__paKin'; return 'tick:'+norm(r.textContent).slice(0,40); } }"
                    // No checkbox — selecting the row itself is how some builds mark the kin.
                    + " rows[0].id='__paKinRow'; return 'row:'+norm(rows[0].textContent).slice(0,40); }");
            String how = tagged == null ? "" : tagged.toString();
            if (how.startsWith("tick:")) {
                try { page.locator("#__paKin").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
                catch (Exception e) { System.out.println("selectKinRow: tick failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__paKin'); if(e) e.removeAttribute('id'); }");
            } else if (how.startsWith("row:")) {
                try { page.locator("#__paKinRow").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
                catch (Exception e) { System.out.println("selectKinRow: row click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__paKinRow'); if(e) e.removeAttribute('id'); }");
            }
            waitForAngular(600);
            System.out.println("selectKinRow: " + how);
            return how;
        } catch (Exception e) {
            System.out.println("selectKinRow: " + e.getMessage());
            return "";
        }
    }

    /**
     * Verify EVERY mandatory NOK field and fill whatever is missing, just before Save.
     *
     * <p>Mandatory on the kin form: <b>Name, Nationality, Relationship, Address Line 1, State, Country,
     * Mobile No</b>, plus <b>NRIC or Passport depending on Nationality</b> — a Malaysian is identified by NRIC, a
     * non-Malaysian by Passport. Values were going missing on different runs (the kin form re-renders as later
     * sections load), so this re-checks the lot and back-fills rather than assuming the earlier fill survived.</p>
     *
     * @return "KIN FILLED: … | KIN MISSING: …" for the report
     */
    public String ensureKinMandatoryComplete() {
        Object r = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(x=>setTimeout(x,ms));"
                + " const kinNg=re=>[...document.querySelectorAll('input,select,textarea')].find(e=>{ const ng=e.getAttribute('ng-model')||'';"
                + "   return /kin|nok/i.test(ng) && re.test(ng) && e.offsetParent!==null; });"
                + " const isEmpty=e=>{ if(!e) return true; if(e.tagName==='SELECT'){ const o=e.options[e.selectedIndex]; return !o||!o.value||/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)); } return !(e.value||'').trim(); };"
                + " const val=e=>{ if(!e) return ''; return e.tagName==='SELECT'? norm((e.options[e.selectedIndex]||{}).textContent||'') : (e.value||'').trim(); };"
                + " const setTxt=(e,v)=>{ if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();}"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " const setSel=(e,want)=>{ if(!e) return; const rs=[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.textContent)));"
                + "   if(!rs.length) return; let t=want?rs.find(x=>new RegExp(want,'i').test(norm(x.o.textContent))):null; if(!t) t=rs[0];"
                + "   e.selectedIndex=t.i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} };"
                + " const filled=[], missing=[];"
                + " const name=kinNg(/name$/i)||kinNg(/kinname/i); if(isEmpty(name)) setTxt(name,'Test Kin');"
                + " const rel=kinNg(/relation/i); if(isEmpty(rel)) setSel(rel,'Father');"
                + " const nat=kinNg(/national(id)?$|nationality/i); if(isEmpty(nat)) setSel(nat,'Malaysia');"
                + " const ctry=kinNg(/country(id)?$/i); if(isEmpty(ctry)) setSel(ctry,'Malaysia');"
                + " const addr=kinNg(/address\\s*1|address1|addressline1|add1|address$/i); if(isEmpty(addr)) setTxt(addr,'1 Jalan Test');"
                + " const st=kinNg(/state/i); if(isEmpty(st)) setSel(st,null); await sleep(700);"
                + " const mob=kinNg(/mobile(no)?$/i); if(isEmpty(mob)) setTxt(mob,'1234567890');"
                // NRIC vs Passport depends on Nationality: Malaysian -> NRIC, otherwise -> Passport.
                + " const natTxt=val(kinNg(/national(id)?$|nationality/i));"
                + " const malaysian=/malaysia/i.test(natTxt);"
                + " const nric=kinNg(/nationalid$|nric|icno/i); const pass=kinNg(/passport/i);"
                + " if(malaysian){ if(isEmpty(nric)) setTxt(nric,'900101071234'); }"
                + " else { if(isEmpty(pass)) setTxt(pass,'A'+String(Date.now()).slice(-7)); }"
                + " await sleep(300);"
                + " const checks={ Name:name, Relationship:rel, Nationality:nat, Country:ctry, Address1:addr, State:st, MobileNo:mob };"
                + " checks[malaysian?'NRIC':'Passport'] = malaysian?nric:pass;"
                // A field that is not on screen is NOT "filled" — report it as absent instead of silently passing.
                // Reporting "MISSING: none" when NOTHING was found is what made this check green while the kin
                // form was not even open.
                + " const absent=[];"
                + " Object.keys(checks).forEach(k=>{ const e=checks[k]; if(!e){ absent.push(k); return; } if(isEmpty(e)) missing.push(k); else filled.push(k+'='+val(e)); });"
                + " if(!filled.length && !missing.length){ resolve('KIN FIELDS NOT VISIBLE (click Edit in the kin grid first) - not checked: '+absent.join(', ')); return; }"
                + " resolve('KIN FILLED: '+filled.join(', ')"
                + "   +' | KIN MISSING: '+(missing.length?missing.join(', '):'none')"
                + "   +(absent.length?' | NOT ON SCREEN: '+absent.join(', '):'')); })");
        String s = r == null ? "" : r.toString();
        System.out.println("ensureKinMandatoryComplete: " + s);
        waitForAngular(400);
        return s;
    }

    public String ensureMandatoryBeforeSave() {
        // NOK Nationality/Country = MALAYSIA, forced HERE as well as in the NOK step — that step reports MANUAL on
        // a carried patient and may not run, leaving the kin on whatever the record held (Afghanistan was seen).
        Object kinNat = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const force=(ng)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return '(no field)';"
                + "   const i=[...e.options].findIndex(o=>o.value && /malaysia/i.test(norm(o.textContent))); if(i<0) return '(no Malaysia option)';"
                + "   e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "   return norm(e.options[i].textContent); };"
                + " return 'NOKnationality='+force('Registration.NOKnationalid')+' KinCountry='+force('Registration.KinCountryID'); }");
        System.out.println("ensureMandatoryBeforeSave: " + kinNat);
        waitForAngular(400);
        // The Save is rejected with "Please Select Kin!" unless a kin ROW is selected — do that next.
        String kin = selectKinRow();
        // 1) Ensure Country/State/City are selected; if City is empty, walk States until one yields a City.
        page.evaluate("async () => { const sleep=ms=>new Promise(r=>setTimeout(r,ms)); const A=window.angular;"
                + " const sel=ng=>[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + " const emptyModel=e=>{ if(!e) return true; const c=A.element(e).controller('ngModel'); const m=c&&c.$modelValue!=null?(''+c.$modelValue).trim():''; return m===''||m==='0'||m==='null'||e.selectedIndex<=0; };"
                + " const ensureFirst=ng=>{ const e=sel(ng); if(e && emptyModel(e) && e.options.length>1){ e.selectedIndex=1; e.dispatchEvent(new Event('change',{bubbles:true})); if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} } };"
                // Gender is checked below and was reported MISSING on a run that nonetheless admitted — FILL it
                // here rather than only reporting it, so the check reflects the form and does not fail a run the
                // app accepted.
                + " ensureFirst('Registration.GenderID');"
                + " ensureFirst('Registration.ResCountryID'); await sleep(300); ensureFirst('Registration.ResStateID'); await sleep(500);"
                // RE-QUERY the City select every time. Changing State makes Angular re-render it, so a reference
                // captured once goes stale and its options never appear to grow — the walk could then never find a
                // city and gave up, leaving a patient on a state with NO cities (e.g. "Luar Negeri" = overseas)
                // reported as "not admittable". City is mandatory, so that silently blocked the whole save.
                + " const city=()=>sel('Registration.ResCityID');"
                + " const cityEmpty=()=>{ const c=city(); return !c || emptyModel(c) || c.options.length<=1; };"
                + " if(cityEmpty()){ const st=sel('Registration.ResStateID');"
                + "   if(st){ const start=st.selectedIndex;"
                + "     for(let i=1;i<st.options.length;i++){ if(i===start) continue;"
                + "       st.selectedIndex=i; st.dispatchEvent(new Event('change',{bubbles:true}));"
                + "       try{A.element(st).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(st).trigger('change');}catch(x){}}"
                // Poll for the city list rather than a single fixed wait — it loads asynchronously per state.
                + "       let c=null; for(let k=0;k<8;k++){ await sleep(400); c=city(); if(c && c.options.length>1) break; }"
                + "       if(c && c.options.length>1){ c.selectedIndex=1; c.dispatchEvent(new Event('change',{bubbles:true}));"
                + "         try{A.element(c).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(c).trigger('change');}catch(x){}}"
                + "         await sleep(300);"
                + "         if(!cityEmpty()){ console.log('State changed to \"'+(st.options[i]||{}).text+'\" to get a City'); break; } } } } }"
                + " else { ensureFirst('Registration.ResCityID'); } }");
        waitForAngular(500);
        // 2) Report every mandatory field's value + which are still missing.
        Object r = page.evaluate("() => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const selVal=ng=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return null; const c=A.element(e).controller('ngModel'); const m=c&&c.$modelValue!=null?(''+c.$modelValue).trim():''; const ok=m!==''&&m!=='0'&&m!=='null'&&e.selectedIndex>0; return ok?norm((e.options[e.selectedIndex]||{}).textContent):''; };"
                + " const inpVal=ng=>{ const e=[...document.querySelectorAll('input')].filter(x=>x.getAttribute('ng-model')===ng).find(x=>x.offsetParent!==null)||[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return null; return (e.value||'').trim(); };"
                + " const checks={ City:selVal('Registration.ResCityID'), State:selVal('Registration.ResStateID'), Country:selVal('Registration.ResCountryID'), Gender:selVal('Registration.GenderID'), Nationality:selVal('Registration.NationalityID'), ICType:selVal('Registration.ICCardTypeID'), NRIC:inpVal('Registration.NationalId'), DOB:inpVal('Registration.DateOfBirth'), Mobile:inpVal('Registration.MobileNo') };"
                + " const present=Object.keys(checks).filter(k=>checks[k]!==null); const missing=present.filter(k=>checks[k]===''); const filled=present.filter(k=>checks[k]!=='').map(k=>k+'='+checks[k]);"
                + " return 'FILLED: '+filled.join(', ')+' | MISSING: '+(missing.length?missing.join(', '):'none'); }");
        return (r == null ? "" : r.toString()) + " | " + kinNat
                + " | Kin selected: " + (kin.isEmpty() ? "(not selected)" : kin);
    }

    /** Dump the identity-related fields on the Admission form (ng-model, value, model value, visible) to find the
     *  exact NRIC / Identification Type / Passport controls the Save validation reads. */
    public String dumpIdentityFields() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[];"
                + " const els=[...document.querySelectorAll('input,select')].filter(e=>{ const ng=(e.getAttribute('ng-model')||'').toLowerCase(); const id=(e.id||'').toLowerCase(); return /nric|national|passport|iccard|identification|cardtype|nationality/.test(ng+id); });"
                + " els.forEach(e=>{ const c=window.angular?angular.element(e).controller('ngModel'):null; out.push(e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" val=\"'+(e.value||'').slice(0,20)+'\" model=\"'+(c&&c.$modelValue!=null?c.$modelValue:'')+'\" vis='+(e.offsetParent!==null)+(e.tagName==='SELECT'?(' opts='+e.options.length):'')); });"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    /**
     * After clicking Save, the admission raises a confirm dialog (e.g. "Do you want to Save" / "Patient has previous
     * unclosed episode" / "Are you sure") that must be answered <b>Yes/OK</b>. Poll and click it, watching for the
     * "Patient Admitted Successfully." toast or a new report tab. Returns the admit toast (or the last toast seen).
     */
    public String confirmAdmissionAndAwaitToast(int tabsBefore) {
        for (int i = 0; i < 45; i++) {
            page.evaluate("() => { const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')].find(m=>m.getBoundingClientRect().width>0 && /save|admit|are you sure|do you want|unclosed|proceed|previous/i.test(m.textContent||''));"
                    + " if(box){ const b=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|save|continue|proceed)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t){ (window.__adToasts=window.__adToasts||[]); if(!window.__adToasts.includes(t)) window.__adToasts.push(t); } }); }");
            Object t = page.evaluate("() => { const a=window.__adToasts||[]; return a.find(x=>/admitted/i.test(x)) || ''; }");
            if (t != null && !t.toString().isEmpty()) return t.toString();
            if (page.context().pages().size() > tabsBefore) return "Patient Admitted Successfully. (report tab opened)";
            page.waitForTimeout(700);
        }
        Object r = page.evaluate("() => { const a=window.__adToasts||[]; return a.length?a[a.length-1]:''; }");
        return r == null ? "" : r.toString();
    }

    // ---- navigation ------------------------------------------------------

    public static final String ROUTE_HINT = "preadmission";
    public static final String ROUTE = "#/ReservationList";

    /**
     * Open Preadmission via IP → Bed Management → Preadmission. First cleans up any leftover modal/backdrop and
     * extra tabs from a prior action (they intercept the menu click), then hash-navigates directly to
     * {@code #/ReservationList} as a reliable fallback. Returns true once the reservation list is ready.
     */
    public boolean navigateViaMenu() {
        // Clean up leftover modal/backdrop + close extra tabs so the menu is clickable (a prior MRD popup can block it).
        page.evaluate("() => { const $=window.jQuery; try{ if($) [...document.querySelectorAll('.modal.in,.modal.show,.modal[style*=\"display: block\"]')].forEach(m=>{try{$(m).modal('hide');}catch(e){}}); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open');"
                + " [...document.querySelectorAll('.jconfirm,.ng-confirm')].forEach(d=>{try{d.remove();}catch(e){}}); }");
        try { for (Page pg : page.context().pages()) { if (pg != page && !pg.isClosed()) pg.close(); } } catch (Exception ignore) {}
        try { page.bringToFront(); } catch (Exception ignore) {}
        // SHORT-CIRCUIT: buttons like Request MRD File / Print / Export stay on this page, so if we're ALREADY on the
        // Pre-Admission list there's no need to re-open it through the menu (that's what makes the left menu keep
        // flashing open). Only Admission (#/Admission) and New/Reserve (#/addReservation) navigate away.
        if (page.url().toLowerCase().contains("reservationlist")) {
            waitForAngular(300);
            return true;
        }
        waitForAngular(400);
        // IP top-level
        Object ip = page.evaluate("() => { const lis=[...document.querySelectorAll('li')]; const ipLi=lis.find(li=>{ const a=li.querySelector(':scope > a'); return a && /^\\s*IP\\s*$/i.test((a.textContent||'').trim()); }); const a=ipLi&&ipLi.querySelector(':scope > a'); if(!a) return false; a.id='__ipMenuTab'; return true; }");
        if (Boolean.TRUE.equals(ip)) {
            try { page.locator("#__ipMenuTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("Preadmission.nav: IP click failed - " + e.getMessage()); }
            waitForAngular(800);
        }
        // Bed Management group
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*bed\\s*management\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // Preadmission link
        Object pa = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*pre[-\\s]?admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__preadmMenu'; return true; }");
        if (Boolean.TRUE.equals(pa)) {
            try { page.locator("#__preadmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) { System.out.println("Preadmission.nav: Preadmission click failed - " + e.getMessage()); }
        } else { System.out.println("Preadmission.nav: 'Preadmission' link not found — using direct route"); }
        waitForAngular(1200);
        // Fallback: if the menu path didn't land us on the reservation list, hash-navigate directly.
        if (!page.url().toLowerCase().contains("reservationlist")) {
            try { page.evaluate("() => { window.location.hash = '#/ReservationList'; }"); } catch (Exception ignore) {}
            waitForAngular(1200);
        }
        try {
            page.waitForFunction("() => /reservationlist/i.test(location.hash) && [...document.querySelectorAll('button')].some(b=>/fetchbedreservation/.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("Preadmission.nav: reservation list not ready in time"); }
        waitForAngular(600);
        return page.url().toLowerCase().contains("reservationlist");
    }
}
