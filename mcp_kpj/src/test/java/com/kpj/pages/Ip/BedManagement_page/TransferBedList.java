package com.kpj.pages.Ip.BedManagement_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * IP &gt; Bed Management &gt; <b>Transfer Bed List</b> — Page Object.
 *
 * <p>Per the M6 Bed Planning FSD: filter transferred-bed records by From Date / To Date (both mandatory) + MRN,
 * showing the <b>List of Transferred Beds</b>. Flow: enter date range → Search → select a record →
 * <b>Transfer Checklist</b> → tick a checklist item → enter Remark → Save → success toast.</p>
 *
 * <p>NEW screen — navigation + fields discovered at runtime by the dump methods, then hard-wired.</p>
 */
public class TransferBedList extends BasePage {

    public TransferBedList(Page page) { super(page); }

    // ---- discovery (temporary) -------------------------------------------

    public String dumpMenuTree() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const items=[...document.querySelectorAll('a')].map(a=>({t:norm(a.textContent), h:a.getAttribute('href')||''})).filter(x=>x.t && x.t.length<45);"
                + " const seen=new Set(); const out=[]; for(const x of items){ const k=x.t+'|'+x.h; if(seen.has(k)) continue; seen.add(k); if(/bed manage|transfer|reserv/i.test(x.t) || (x.h&&/#\\//.test(x.h))) out.push(x.t+'  ->  '+x.h); }"
                + " return out.slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " const labelOf=e=>{ let t=''; if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=l.textContent; } if(!t){ const g=e.closest('.form-group,.row,.col-sm-6,.col-md-6,.col-sm-4,td,div'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t=l.textContent; } } return norm(t).slice(0,26); };"
                + " const scope=[...document.querySelectorAll('.modal,[role=dialog]')].find(m=>m.getBoundingClientRect().width>0) || document;"
                + " const inps=[...scope.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden').slice(0,40).map(e=>e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'+(e.tagName==='SELECT'?(' opts='+e.options.length):''));"
                + " out.push('fields=\\n  '+inps.join('\\n  '));"
                + " const btns=[...scope.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>'\"'+norm(b.textContent||b.value)+'\" {'+(b.getAttribute('ng-click')||'')+'}').filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,35).join('\\n  '));"
                + " const tbls=[...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null).map(t=>norm((t.querySelector('thead')||{}).innerText||'').slice(0,90));"
                + " out.push('tables=\\n  '+tbls.join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- search / grid / transfer checklist ------------------------------

    public static final String ROUTE = "#/TransferBedList";

    /** Set the date range ({@code Reservation.fromdate}/{@code Reservation.todate}) and click <b>Search</b>
     *  ({@code fetchgrid()}). Returns the number of rows in the List Of Transferred Beds. */
    public int setDateRangeAndSearch(String from, String to) {
        page.evaluate("(a) => { const set=(ng,v)=>{ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }; set('Reservation.fromdate', a.from); set('Reservation.todate', a.to); }",
                java.util.Map.of("from", from, "to", to));
        waitForAngular(400);
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/fetchgrid/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        page.waitForTimeout(3000);
        return transferRowCount();
    }

    private static final String TRANSFER_TABLE_JS =
            "[...document.querySelectorAll('table')].find(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /select/.test(h) && /transfer/.test(h) && /patient name/.test(h); })";

    /** Read the MRN column values from the List Of Transferred Beds (these patients have/had beds → good transfer
     *  candidates). Call after a Search. */
    public java.util.List<String> getTransferredMrns() {
        Object r = page.evaluate("() => { const t=" + TRANSFER_TABLE_JS + "; if(!t) return []; const heads=[...t.querySelectorAll('thead th,thead td')].map(h=>(h.textContent||'').replace(/\\s+/g,' ').trim().toLowerCase()); const mi=heads.findIndex(h=>h==='mrn'); const out=[]; [...t.querySelectorAll('tbody tr')].forEach(r=>{ const tds=[...r.querySelectorAll('td')]; if(mi>=0 && tds[mi]){ const m=(tds[mi].textContent||'').trim(); if(/^[0-9A-Za-z]{5,}$/.test(m)) out.push(m); } }); return [...new Set(out)]; }");
        java.util.List<String> list = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) if (o != null) list.add(o.toString());
        return list;
    }

    public int transferRowCount() {
        Object r = page.evaluate("() => { const t=" + TRANSFER_TABLE_JS + "; if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>{ const tx=(r.textContent||'').replace(/\\s+/g,' ').trim(); return tx && !/no data|no records/i.test(tx) && [...r.querySelectorAll('td')].length>3; }).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Tick the SELECT checkbox of the first row in the List Of Transferred Beds (via its Angular model). Returns text. */
    public String selectFirstTransferRow() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const t=" + TRANSFER_TABLE_JS + "; if(!t) return null;"
                + " const row=[...t.querySelectorAll('tbody tr')].find(r=>[...r.querySelectorAll('td')].length>3 && norm(r.textContent) && !/no data|no records/i.test(r.textContent)); if(!row) return null;"
                + " const cb=row.querySelector('input[type=checkbox],input[type=radio]'); if(cb){ cb.id='__tbSel'; const c=A.element(cb).controller('ngModel'); cb.checked=true; if(c){ c.$setViewValue(true); c.$render(); } cb.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(cb).triggerHandler('change');}catch(e){} if(window.jQuery){try{jQuery(cb).trigger('change');}catch(e){}} }"
                + " return norm(row.textContent).slice(0,60); }");
        if (r == null) { System.out.println("selectFirstTransferRow: no row"); return null; }
        waitForAngular(500);
        return r.toString();
    }

    /** Click the footer <b>Transfer Checklist</b> button. Returns true once a popup opens. */
    public boolean clickTransferChecklist() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^\\s*transfer checklist\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null) || [...document.querySelectorAll('button,a')].find(x=>/transferchecklist|TransferCheckList/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /checklist/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("clickTransferChecklist: checklist popup did not open"); return false; }
        waitForAngular(600);
        return true;
    }

    /** Click the footer <b>New</b> button to open the new bed-transfer form. Returns true once it opens. */
    public boolean clickNew() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^\\s*new\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null) || [...document.querySelectorAll('button,a')].find(x=>/AddTransferBed|OpenTransfer|transferbed|AddBedTransfer/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(2000);
        return true;
    }

    /** Get a FRESH New transfer form: click Back ({@code closeForm1()}) to the list, then New again. More reliable
     *  than Clear (which left later MRN searches not loading the patient). Returns true if back on the add form. */
    public boolean freshNewForm() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/closeForm1/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*back\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1200);
        return clickNew() && page.url().toLowerCase().contains("addtransferbed");
    }

    /** True once a vacant bed is actually selected — its {@code BedList.isbedselect} checkbox is checked (the
     *  {@code SelectedBedCensus(BedList)} handler ticks it). */
    public boolean isTransferBedSet() {
        return Boolean.TRUE.equals(page.evaluate("() => { const t=" + VACANT_BED_TABLE_JS + "; if(!t) return false; return [...t.querySelectorAll('tbody tr')].some(r=>{ const c=r.querySelector(\"input[ng-model='BedList.isbedselect'],input[type=checkbox]\"); return c && c.checked; }); }"));
    }

    /** On the New transfer form: enter MRN ({@code BedTransfer.MRNO}) + click search ({@code SearchPatientByMRNo()}).
     *  Returns the current Ward ({@code BedTransfer.WardName}) — non-empty means an admitted patient with a bed loaded. */
    public String transferEnterMrnAndSearch(String mrn) {
        page.evaluate("(mrn)=>{ const e=document.querySelector('#multiBedAllocationMrn') || [...document.querySelectorAll(\"input[ng-model='BedTransfer.MRNO']\")].find(x=>x.offsetParent!==null); if(e){ const c=angular.element(e).controller('ngModel'); e.value=mrn; if(c){c.$setViewValue(mrn);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } const b=[...document.querySelectorAll('button,a')].find(x=>/SearchPatientByMRNo/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }", mrn);
        page.waitForTimeout(3500);
        Object r = page.evaluate("()=>{ const e=[...document.querySelectorAll(\"input[ng-model='BedTransfer.WardName']\")].find(x=>x.offsetParent!==null); return e?(e.value||''):''; }");
        return r == null ? "" : r.toString();
    }

    /** True if a transferable inpatient loaded (current bed / Ward auto-populated). */
    public boolean isTransferPatientLoaded() {
        Object r = page.evaluate("()=>{ const e=[...document.querySelectorAll(\"input[ng-model='BedTransfer.WardName']\")][0]; return !!(e && (e.value||'').trim()); }");
        return Boolean.TRUE.equals(r);
    }

    private static final String VACANT_BED_TABLE_JS =
            "[...document.querySelectorAll('table')].find(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /select/.test(h) && /facilities/.test(h) && /\\bbed\\b/.test(h) && !/transfer date/.test(h); })";

    /** Predicate: is this vacant-bed row an <b>available (GREEN)</b> bed? A RED bed cell = occupied (not selectable);
     *  a GREEN bed cell = available. Falls back to the checkbox's enabled state when no cell is colour-coded. */
    private static final String BED_AVAIL_JS =
            "(r=>{ for(const td of r.querySelectorAll('td')){ const m=getComputedStyle(td).backgroundColor.match(/rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)/); if(m){ const R=+m[1],G=+m[2],B=+m[3]; if(R>150&&G<120&&B<120) return false; if(G>130&&R<150) return true; } } const c=r.querySelector('input[type=checkbox],input[type=radio]'); return !!(c&&!c.disabled); })";

    public int transferBedCount() {
        Object r = page.evaluate("() => { const t=" + VACANT_BED_TABLE_JS + "; if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox],input[type=radio]') && !/no records|no data/i.test(r.textContent||'') && " + BED_AVAIL_JS + "(r)).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Select target Ward + Room Type + Bed Type combos until the vacant bed list generates. Returns "Ward=.. | ..". */
    public String selectTransferTargetUntilBeds() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const setSel=(ng,idx)=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return null; e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} return norm(e.options[idx].textContent); };"
                + " const realOpts=sel=>[...sel.options].map((o,i)=>({i,t:norm(o.textContent),v:o.value})).filter(o=>o.v && !/^-*\\s*select/i.test(o.t));"
                + " const bedCount=()=>{ const t=" + VACANT_BED_TABLE_JS + "; if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox],input[type=radio]') && !/no records|no data/i.test(r.textContent||'')).length; };"
                + " const btSel=document.querySelector(\"select[ng-model='BedTransfer.BedTypeID']\"); if(btSel){ const b=realOpts(btSel)[0]; if(b) setSel('BedTransfer.BedTypeID', b.i); }"
                + " const wardSel=document.querySelector(\"select[ng-model='BedTransfer.WardID']\"); const rtSel=document.querySelector(\"select[ng-model='BedTransfer.BedClassID']\"); if(!wardSel||!rtSel) return '(no selects)';"
                + " const wardOpts=realOpts(wardSel); const rtOpts=realOpts(rtSel); let tries=0;"
                + " for(const w of wardOpts){ setSel('BedTransfer.WardID', w.i); await sleep(400);"
                + "   for(const rt of rtOpts){ setSel('BedTransfer.BedClassID', rt.i); await sleep(750); tries++;"
                + "     const n=bedCount(); if(n>0) return 'Ward='+w.t+' | RoomType='+rt.t+' | beds='+n;"
                + "     if(tries>=45) return '(no beds after '+tries+' combos)'; } }"
                + " return '(no beds in any combo)'; }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** The target-Ward ({@code BedTransfer.WardID}) option texts (real options only). */
    public java.util.List<String> transferWardTexts() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const e=document.querySelector(\"select[ng-model='BedTransfer.WardID']\"); if(!e) return []; return [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))).map(o=>norm(o.textContent)); }");
        java.util.List<String> list = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) if (o != null) list.add(o.toString());
        return list;
    }

    /** Set the target <b>Ward</b> ({@code BedTransfer.WardID}) to {@code wardText} (e.g. the patient's current ward,
     *  CCU) ONLY. Bed Type is intentionally left unset; Room Type is set separately via {@link #applyTransferRoomType}. */
    public void setTransferWardOnly(String wardText) {
        page.evaluate("(wardText) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const setSelByText=(ng,txt)=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return; let i=[...e.options].findIndex(o=>norm(o.textContent)===txt); if(i<0) i=[...e.options].findIndex(o=>norm(o.textContent).toLowerCase()===(''+txt).toLowerCase()); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} };"
                + " setSelByText('BedTransfer.WardID', wardText); }", wardText);
        waitForAngular(700);
    }

    /** The target-Room-Type ({@code BedTransfer.BedClassID}) option texts (real options only). */
    public java.util.List<String> transferRoomTypeTexts() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const e=document.querySelector(\"select[ng-model='BedTransfer.BedClassID']\"); if(!e) return []; return [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))).map(o=>norm(o.textContent)); }");
        java.util.List<String> list = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) if (o != null) list.add(o.toString());
        return list;
    }

    /** Set the <b>Room Type</b> ({@code BedTransfer.BedClassID}) to {@code roomText} and wait for the vacant bed list. */
    public int applyTransferRoomType(String roomText) {
        page.evaluate("(roomText) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const e=document.querySelector(\"select[ng-model='BedTransfer.BedClassID']\"); if(!e) return; let i=[...e.options].findIndex(o=>norm(o.textContent)===roomText); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} }", roomText);
        for (int i = 0; i < 12; i++) { int n = transferBedCount(); if (n > 0) return n; page.waitForTimeout(500); }
        return transferBedCount();
    }

    /** Set the target <b>Ward</b> ({@code BedTransfer.WardID}) to {@code wardText} + Room Type/Bed Type to their first
     *  real option, then wait for the vacant bed list to generate. Returns the number of beds. */
    public int applyTransferWard(String wardText) {
        page.evaluate("(wardText) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const setSelByText=(ng,txt)=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return; let i=[...e.options].findIndex(o=>norm(o.textContent)===txt); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} };"
                + " const setSelFirst=(ng)=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return; let i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} };"
                + " setSelFirst('BedTransfer.BedTypeID'); setSelByText('BedTransfer.WardID', wardText); setSelFirst('BedTransfer.BedClassID'); }", wardText);
        for (int i = 0; i < 15; i++) { int n = transferBedCount(); if (n > 0) return n; page.waitForTimeout(500); }
        return transferBedCount();
    }

    /** Diagnostic: the vacant bed table's first row (cells + the SELECT control's tag/type/ng-model/ng-click). */
    public String dumpVacantBedRow() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const t=" + VACANT_BED_TABLE_JS + "; if(!t) return '(no table)';"
                + " const rows=[...t.querySelectorAll('tbody tr')].filter(r=>[...r.querySelectorAll('td')].length>2 && !/no records|no data/i.test(r.textContent||'')); if(!rows.length) return 'rows=0';"
                + " const row=rows[0]; const cells=[...row.querySelectorAll('td')].map(td=>norm(td.textContent)).slice(0,6);"
                + " const ctrl=row.querySelector('input[type=checkbox],input[type=radio]') || row.querySelector('[ng-click]') || row.querySelector('a,button,i,span');"
                + " const ci=ctrl?(ctrl.tagName+'['+(ctrl.type||'')+'] ngm=\"'+(ctrl.getAttribute('ng-model')||'')+'\" ngc=\"'+(ctrl.getAttribute('ng-click')||'')+'\" cls=\"'+(ctrl.className||'').slice(0,30)+'\"'):'(no ctrl)';"
                + " return 'rows='+rows.length+' cells=['+cells.join(' | ')+'] ctrl='+ci; }");
        return r == null ? "" : r.toString();
    }

    /** Why the last {@link #selectFirstTransferBed()} call returned null — distinguishes "the table has no rows
     *  at all" from "the table has GREEN rows, but every one of them has a disabled checkbox" (confirmed live:
     *  {@code ng-disabled="BedList.isbedoccupied==true"} — a row can render green/available while the server
     *  still flags it occupied, so it looks selectable but genuinely is not). Empty string on success. */
    public String lastNoBedReason = "";

    /** Select the first vacant bed that is BOTH visually available (green) AND actually selectable (its
     *  checkbox is not {@code disabled}) — confirmed live: a row can be green-colored yet have
     *  {@code disabled="disabled"} on its checkbox ({@code ng-disabled="BedList.isbedoccupied==true"|}), so
     *  color alone is not enough to tell whether a row can genuinely be selected. Tries EVERY candidate row
     *  in DOM order (not just the first) until one's selection actually commits (verified via
     *  {@code BedList.isbedselect}), so a stale/disabled-looking-available row doesn't get silently reported
     *  as "selected" only to fail Save later with "Please Select Bed!" for a bed that was never really ticked.
     *  Returns the row text on success, or null (with the reason in {@link #lastNoBedReason}) if none of the
     *  candidate rows could actually be selected. */
    public String selectFirstTransferBed() {
        lastNoBedReason = "";
        Object diag = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const t=" + VACANT_BED_TABLE_JS + ";"
                + " if(!t) return { totalRows:0, greenRows:0, selectableRows:0 };"
                + " const rows=[...t.querySelectorAll('tbody tr')].filter(r=>[...r.querySelectorAll('td')].length>2 && !/no records|no data/i.test(r.textContent||''));"
                + " const green=rows.filter(r=>" + BED_AVAIL_JS + "(r));"
                + " const selectable=green.filter(r=>{ const c=r.querySelector('input[type=checkbox],input[type=radio]'); return c && !c.disabled; });"
                + " return { totalRows: rows.length, greenRows: green.length, selectableRows: selectable.length }; }");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> d = diag instanceof java.util.Map ? (java.util.Map<String, Object>) diag : java.util.Collections.emptyMap();
        long totalRows = d.get("totalRows") instanceof Number ? ((Number) d.get("totalRows")).longValue() : 0;
        long greenRows = d.get("greenRows") instanceof Number ? ((Number) d.get("greenRows")).longValue() : 0;
        long selectableRows = d.get("selectableRows") instanceof Number ? ((Number) d.get("selectableRows")).longValue() : 0;

        for (int attempt = 0; attempt < Math.max(1, (int) selectableRows); attempt++) {
            Object r = page.evaluate("(skip) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const t=" + VACANT_BED_TABLE_JS + "; if(!t) return null;"
                    // A row must be GREEN *and* have a genuinely enabled checkbox — a green row with a disabled
                    // checkbox (isbedoccupied==true server-side) cannot actually be selected despite looking available.
                    + " const candidates=[...t.querySelectorAll('tbody tr')].filter(r=>[...r.querySelectorAll('td')].length>2 && !/no records|no data/i.test(r.textContent||'') && " + BED_AVAIL_JS + "(r)"
                    + "   && (()=>{ const c=r.querySelector('input[type=checkbox],input[type=radio]'); return c && !c.disabled; })());"
                    + " const row=candidates[skip]; if(!row) return null;"
                    + " row.scrollIntoView({block:'center'});"
                    + " try{ const sc=A.element(row).scope(); if(sc && typeof sc.SelectedBedCensus==='function' && sc.BedList){ sc.$apply(()=>sc.SelectedBedCensus(sc.BedList)); } }catch(e){}"
                    + " const cell=[...row.querySelectorAll('[ng-click]')].find(e=>/SelectedBedCensus/i.test(e.getAttribute('ng-click')||'')) || row.querySelector(\"input[ng-model='BedList.isbedselect'],input[type=checkbox]\") || row.querySelector('td');"
                    + " if(cell) cell.id='__tbBed';"
                    + " return norm(row.textContent).slice(0,50); }", attempt);
            if (r == null) break;   // ran out of candidate rows
            if (!isTransferBedSet()) {
                try { page.locator("#__tbBed").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(1500)); }
                catch (Exception e) { /* row re-rendered — scope-call may have already committed the bed */ }
                waitForAngular(400);
            }
            if (isTransferBedSet()) return r.toString();
            System.out.println("selectFirstTransferBed: row " + attempt + " (" + r + ") looked selectable but did not commit — trying the next row");
        }
        lastNoBedReason = totalRows == 0
                ? "no bed list in the table for this Ward/Room Type"
                : greenRows == 0
                    ? "the bed list table has " + totalRows + " row(s) but none are available (green)"
                    : "the bed list table shows " + greenRows + " available (green) row(s), but every one has a"
                        + " disabled checkbox (server-side flagged occupied) — none could actually be selected";
        System.out.println("selectFirstTransferBed: " + lastNoBedReason);
        return null;
    }

    /** Fill the transfer extras AFTER the bed is selected: Transfer by, the <b>Billing Class</b>
     *  ({@code BedTransfer.billingBedClassID} — its options load only after a bed is picked, so WAIT for them), and
     *  the Remark. Returns a summary. */
    public String fillTransferExtras(String remark) {
        Object r = page.evaluate("async (remark) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const realIdx=e=>[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
                + " const setSel=ng=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return '(no)'; if(e.selectedIndex>0 && !/^-*\\s*select/i.test(norm((e.options[e.selectedIndex]||{}).text))) return norm(e.options[e.selectedIndex].text)+'(kept)'; const i=realIdx(e); if(i<0) return '(no-opt)'; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); };"
                + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " const tb=setSel('BedTransfer.Transferby');"
                // Billing Class: its options populate AFTER the bed is selected — wait for a real option, then set it.
                + " let bc='(no-billing)'; for(let k=0;k<16;k++){ const e=document.querySelector(\"select[ng-model='BedTransfer.billingBedClassID']\"); if(e && realIdx(e)>=0){ bc=setSel('BedTransfer.billingBedClassID'); break; } await sleep(400); }"
                + " setInp('BedTransfer.Remark',remark); return 'Transferby='+tb+' | BillingClass='+bc; }", remark);
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Save</b> ({@code savebedtransfer()}), answer any confirm Yes/OK, and return the success toast. */
    public String saveTransferAndGetToast() {
        page.evaluate("() => { window.__btToasts=[]; if(window.__btObs) window.__btObs.disconnect();"
                + " window.__btObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__btToasts.includes(t)) window.__btToasts.push(t); }); });"
                + " window.__btObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/savebedtransfer/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 30; i++) {
            // Click Yes/OK on ANY visible confirm dialog (the transfer confirm wording varies), then collect toasts.
            page.evaluate("() => { const boxes=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')].filter(m=>m.getBoundingClientRect().width>0 && getComputedStyle(m).display!=='none' && !/toast/i.test(m.className||''));"
                    + " for(const box of boxes){ const y=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|save|continue|proceed|confirm)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(y){ y.click(); break; } }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t){ (window.__btToasts=window.__btToasts||[]); if(!window.__btToasts.includes(t)) window.__btToasts.push(t); } }); }");
            boolean done = Boolean.TRUE.equals(page.evaluate("() => (window.__btToasts||[]).some(a=>/transfer|success|saved|please|select|enter|already/i.test(a))"));
            if (done) break;
            page.waitForTimeout(600);
        }
        Object r = page.evaluate("() => { const a=window.__btToasts||[]; return a.find(x=>/transfer.*(success|saved)|bed.*transfer|saved successfully|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(500);
        return r == null ? "" : r.toString().trim();
    }

    /** Dump ALL buttons (text + ng-click) — to pin down the New form's Save/Reserve control + bed checkboxes. */
    public String dumpAllButtons() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const btns=[...document.querySelectorAll('button,a,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>'\"'+norm(b.textContent||b.value)+'\" {'+(b.getAttribute('ng-click')||'')+'}').filter((v,i,a)=>a.indexOf(v)===i && /transfer|bed|save|search|reserve|add/i.test(v));"
                + " return btns.slice(0,30).join('\\n  '); }");
        return r == null ? "" : r.toString();
    }

    /** Tick the first <b>ENABLED</b> checklist item ({@code item.IsSelected}) in the Transfer Checklist popup (via its
     *  Angular model) and set that item's per-row {@code item.Remark}. Disabled rows are skipped (they can't be ticked).
     *  Returns the item text, or null. */
    public String selectFirstChecklistItem() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " let cbs=[...document.querySelectorAll(\"input[type=checkbox][ng-model='item.IsSelected']\")].filter(c=>c.offsetParent!==null);"
                + " const enabled=cbs.filter(c=>!c.disabled); if(enabled.length) cbs=enabled;"   // prefer clickable rows; fall back to any visible
                + " if(!cbs.length) return null;"
                + " const cb=cbs.find(c=>!c.checked)||cbs[0]; const c=A.element(cb).controller('ngModel'); cb.checked=true; if(c){ c.$setViewValue(true); c.$render(); } cb.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(cb).triggerHandler('change');}catch(e){} if(window.jQuery){try{jQuery(cb).trigger('change');}catch(e){}}"
                + " const row=cb.closest('tr'); const inp=row?row.querySelector(\"input[ng-model='item.Remark']\"):null; if(inp){ const ic=A.element(inp).controller('ngModel'); inp.value='Checklist verified'; if(ic){ ic.$setViewValue('Checklist verified'); ic.$render(); } inp.dispatchEvent(new Event('input',{bubbles:true})); inp.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " return row?norm(row.textContent).slice(0,40):'item'; }");
        waitForAngular(400);
        return r == null ? null : r.toString();
    }

    /** Enter the overall <b>Remark</b> ({@code checklistNarration}) in the Transfer Checklist popup. */
    public void enterChecklistRemark(String remark) {
        page.evaluate("(v)=>{ const e=[...document.querySelectorAll('textarea,input')].find(x=>x.getAttribute('ng-model')==='checklistNarration'); if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", remark);
        waitForAngular(300);
    }

    /** Click <b>Save</b> ({@code fnSaveTransferChecklist()}), answer any confirm Yes/OK, and return the success toast. */
    public String saveChecklistAndGetToast() {
        page.evaluate("() => { window.__tcToasts=[]; if(window.__tcObs) window.__tcObs.disconnect();"
                + " window.__tcObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__tcToasts.includes(t)) window.__tcToasts.push(t); }); });"
                + " window.__tcObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/fnSaveTransferChecklist/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 25; i++) {
            page.evaluate("() => { const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')].find(m=>m.getBoundingClientRect().width>0 && /checklist|are you sure|do you want|confirm|proceed|save/i.test(m.textContent||''));"
                    + " if(box){ const y=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|save|continue|proceed)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(y) y.click(); }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t){ (window.__tcToasts=window.__tcToasts||[]); if(!window.__tcToasts.includes(t)) window.__tcToasts.push(t); } }); }");
            boolean done = Boolean.TRUE.equals(page.evaluate("() => (window.__tcToasts||[]).some(a=>/checklist|success|saved|updated|please|select|remark/i.test(a))"));
            if (done) break;
            page.waitForTimeout(600);
        }
        Object r = page.evaluate("() => { const a=window.__tcToasts||[]; return a.find(x=>/checklist.*(saved|success|updated)|saved successfully|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(500);
        return r == null ? "" : r.toString().trim();
    }

    // ---- auto-pick-and-transfer (shared by every caller: the standalone Transfer Bed List "New" flow AND
    // the Graphical View "occupied bed -> Transfer Bed" action) ------------------------------------------

    /** Candidate admitted-patient MRNs for a bed transfer — auto-picked/shuffled (a transfer needs an inpatient
     *  with a current bed; a fixed MRN may not be admitted). Shared so every caller draws from the same pool
     *  instead of maintaining its own copy. */
    public static final String[] TRANSFER_MRN_POOL = {
            "100000998", "100001005", "100000982", "100000944", "100000969", "100001022",
            "100000930", "100000955", "100001010", "100000975", "100001000", "100000960"};

    public static java.util.List<String> shuffledTransferMrns() {
        java.util.List<String> list = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(java.util.Arrays.asList(TRANSFER_MRN_POOL)));
        java.util.Collections.shuffle(list);
        return list;
    }

    /** Try CCU first (it reliably has vacant/green beds), then the patient's current ward, then everything else. */
    private static int wardRank(String ward, String currentWard) {
        String w = ward == null ? "" : ward.trim().toLowerCase();
        if (w.equals("ccu")) return 0;
        if (!currentWard.isEmpty() && w.equals(currentWard.toLowerCase())) return 1;
        return 2;
    }

    /** Try room types that tend to have many vacant beds first: Five Bed, then Double Bed, then the rest. */
    private static int roomRank(String rt) {
        String r = rt == null ? "" : rt.toLowerCase();
        if (r.contains("five")) return 0;
        if (r.contains("double")) return 1;
        return 2;
    }

    /** Result of {@link #autoTransferAdmittedPatient}: everything a caller needs to report each step, plus a
     *  ready-made {@link #failReason} distinguishing WHY it failed (pending-transfer-blocked vs. no vacant
     *  bed vs. a specific Save rejection) instead of a generic "no toast appeared". */
    public static final class TransferResult {
        public String usedMrn, currentWard, target, bed, toast, extras, tried = "";
        public boolean ok, blocked;
        public String failReason = "";
        /** Set when a ward/room combo had GREEN bed rows but none were genuinely selectable (a disabled
         *  checkbox behind a green row — see {@link #selectFirstTransferBed}'s Javadoc) — distinguishes "no
         *  bed list in the table at all" from "the table had rows that turned out not to be real". */
        public String unselectableReason = "";
    }

    /** Auto-pick an admitted patient from {@code candidates} and complete a bed transfer: search MRN → pick a
     *  target Ward/Room Type with a vacant (green) bed → select the bed → fill Remark/Billing Class → Save.
     *
     * <p>If Save is rejected specifically because "a pending transfer request already exists for this
     * patient" (a PATIENT-scoped block — retrying the SAME patient can never clear it), moves on to a
     * DIFFERENT candidate's bed instead of giving up, up to {@code maxBlockedRetries} times. Any other
     * failure (no vacant bed found, a different Save rejection) stops after that one candidate, same as
     * before. Assumes the caller is already on the {@code #/addTransferBed} form.</p>
     */
    public TransferResult autoTransferAdmittedPatient(java.util.List<String> candidates, int maxBlockedRetries) {
        TransferResult res = new TransferResult();
        StringBuilder tried = new StringBuilder();
        boolean first = true;
        int blockedAttempts = 0;
        for (String cand : candidates) {
            if (!first) { if (!freshNewForm()) { if (!clickNew()) break; } }  // fresh form (Back->New, else full re-nav)
            first = false;
            res.currentWard = transferEnterMrnAndSearch(cand);
            if (!isTransferPatientLoaded()) { tried.append(cand).append("(not-admitted) "); continue; }  // not admitted → keep looking
            res.usedMrn = cand;
            res.blocked = false;
            res.target = null; res.bed = null; res.toast = null; res.extras = "";  // reset per candidate — don't leak an EARLIER candidate's bed/toast into this one's report

            // Target ward is INDEPENDENT of the patient's current ward — pick any ward with a vacant (green) bed.
            // CCU + Five Bed reliably has green beds, so try CCU first, then the patient's current ward, then the rest.
            java.util.List<String> wards = transferWardTexts();
            final String cw = (res.currentWard == null ? "" : res.currentWard.trim());
            wards.sort((a, b) -> Integer.compare(wardRank(a, cw), wardRank(b, cw)));
            int comboTries = 0;
            outer:
            for (String w : wards) {
                setTransferWardOnly(w);
                java.util.List<String> roomTypes = transferRoomTypeTexts();
                roomTypes.sort((a, b) -> Integer.compare(roomRank(a), roomRank(b)));
                for (String rt : roomTypes) {
                    if (comboTries++ >= 10) break outer;                 // cap ward×room combos (each is slow)
                    int beds = applyTransferRoomType(rt);
                    if (beds == 0) continue;                             // no GREEN (vacant) bed for this combo
                    String bed = selectFirstTransferBed();
                    if (bed == null) {
                        if (!lastNoBedReason.isEmpty()) res.unselectableReason = "Ward=" + w + " | RoomType=" + rt + ": " + lastNoBedReason;
                        continue;                                        // no selectable green bed row — try the next combo
                    }
                    res.bed = bed;
                    res.target = "Ward=" + w + " | RoomType=" + rt + " | greenBeds=" + beds;
                    res.extras = fillTransferExtras("Bed transfer - automated test");
                    res.toast = saveTransferAndGetToast();               // the SAVE toast is the source of truth
                    String tl = res.toast == null ? "" : res.toast.toLowerCase();
                    // Success ONLY on the explicit success toast — NOT the mere word "transfer" (which also appears
                    // in "A pending transfer request already exists ...").
                    res.ok = tl.contains("saved successfully") || tl.contains("transfer saved")
                            || (tl.contains("transfer") && tl.contains("success") && !tl.contains("cannot") && !tl.contains("already"));
                    if (res.ok) break outer;
                    if (tl.contains("already exists") || tl.contains("pending transfer") || tl.contains("cannot raise") || tl.contains("cannot be raised")) {
                        res.blocked = true; break outer;
                    }
                    // "Please Select Bed!" → the bed didn't commit for this combo → try the next ward/room
                }
            }
            tried.append(cand).append(res.ok ? ("(SAVED " + (res.target == null ? "" : res.target) + ") ")
                    : (res.blocked ? "(pending-transfer exists) " : "(no green bed to transfer to) "));
            if (res.ok) break;
            if (res.blocked && ++blockedAttempts < maxBlockedRetries) continue;   // try a different patient's bed
            break;   // success, a non-blocked failure, or the blocked-retry budget is spent
        }
        res.tried = tried.toString();
        if (res.usedMrn == null) {
            res.failReason = "No admitted patient found among the candidates tried: " + res.tried;
        } else if (!res.ok) {
            if (res.bed == null) {
                res.failReason = res.blocked
                        ? "Every admittable candidate tried had a pending transfer already raised — none could be used: " + res.tried
                        : !res.unselectableReason.isEmpty()
                            ? "MRN " + res.usedMrn + " is admitted, but every vacant bed found turned out unselectable — " + res.unselectableReason
                            : "MRN " + res.usedMrn + " is admitted but no ward/room-type combo tried had a vacant (green) bed to transfer into: " + res.tried;
            } else if (res.toast == null || res.toast.isEmpty()) {
                res.failReason = "Save (savebedtransfer()) produced no toast at all for MRN " + res.usedMrn + " / bed " + res.bed;
            } else {
                res.failReason = "Save rejected MRN " + res.usedMrn + ": \"" + res.toast + "\"";
            }
        }
        return res;
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        page.evaluate("() => { const $=window.jQuery; try{ if($) [...document.querySelectorAll('.modal.in,.modal.show')].forEach(m=>{try{$(m).modal('hide');}catch(e){}}); }catch(e){} [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        try { for (Page pg : page.context().pages()) { if (pg != page && !pg.isClosed()) pg.close(); } } catch (Exception ignore) {}
        try { page.bringToFront(); } catch (Exception ignore) {}
        waitForAngular(400);
        Object ip = page.evaluate("() => { const lis=[...document.querySelectorAll('li')]; const ipLi=lis.find(li=>{ const a=li.querySelector(':scope > a'); return a && /^\\s*IP\\s*$/i.test((a.textContent||'').trim()); }); const a=ipLi&&ipLi.querySelector(':scope > a'); if(!a) return false; a.id='__ipMenuTab'; return true; }");
        if (Boolean.TRUE.equals(ip)) { try { page.locator("#__ipMenuTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) {} waitForAngular(800); }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*bed\\s*management\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        Object tb = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/transfer\\s*bed/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__tbMenu'; return true; }");
        if (Boolean.TRUE.equals(tb)) { try { page.locator("#__tbMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) {} }
        else System.out.println("TransferBedList.nav: 'Transfer Bed List' link not found — using direct route");
        waitForAngular(1200);
        if (!page.url().toLowerCase().contains("transferbedlist")) {
            try { page.evaluate("() => { window.location.hash = '#/TransferBedList'; }"); } catch (Exception ignore) {}
            waitForAngular(1200);
        }
        try {
            page.waitForFunction("() => /transferbedlist/i.test(location.hash) && [...document.querySelectorAll('button')].some(b=>/fetchgrid/.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("TransferBedList.nav: screen not ready in time"); }
        waitForAngular(600);
        return page.url().toLowerCase().contains("transferbedlist");
    }
}
