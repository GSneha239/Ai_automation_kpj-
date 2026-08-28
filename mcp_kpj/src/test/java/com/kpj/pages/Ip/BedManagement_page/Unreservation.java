package com.kpj.pages.Ip.BedManagement_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * IP &gt; Bed Management &gt; <b>Unreservation</b> — Page Object.
 *
 * <p>Flow: enter a reserved MRN → Search → the reservation shows in the <b>List of Reserved Beds</b> table →
 * select it → <b>Unreserve</b> → verify it appears in the <b>List of Unreserved Beds</b> table.</p>
 *
 * <p>NEW screen — navigation + fields discovered at runtime by the dump methods, then hard-wired.</p>
 */
public class Unreservation extends BasePage {

    public Unreservation(Page page) { super(page); }

    // ---- discovery (temporary) -------------------------------------------

    public String dumpMenuTree() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const items=[...document.querySelectorAll('a')].map(a=>({t:norm(a.textContent), h:a.getAttribute('href')||''})).filter(x=>x.t && x.t.length<45);"
                + " const seen=new Set(); const out=[]; for(const x of items){ const k=x.t+'|'+x.h; if(seen.has(k)) continue; seen.add(k); if(/bed manage|reserv|admission/i.test(x.t) || (x.h&&/#\\//.test(x.h))) out.push(x.t+'  ->  '+x.h); }"
                + " return out.slice(0,140).join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " const labelOf=e=>{ let t=''; if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=l.textContent; } if(!t){ const g=e.closest('.form-group,.row,.col-sm-6,.col-md-6,td,div'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t=l.textContent; } } return norm(t).slice(0,26); };"
                + " const inps=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden').slice(0,30).map(e=>e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"');"
                + " out.push('fields=\\n  '+inps.join('\\n  '));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>'\"'+norm(b.textContent||b.value)+'\" {'+(b.getAttribute('ng-click')||'')+'}').filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,35).join('\\n  '));"
                + " const tbls=[...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null).map(t=>norm((t.querySelector('thead')||{}).innerText||'').slice(0,90));"
                + " out.push('tables=\\n  '+tbls.join('\\n  '));"
                + " const panels=[...document.querySelectorAll('.panel-heading,.box-header,legend,h3,h4')].map(e=>norm(e.textContent).slice(0,40)).filter(t=>/reserv/i.test(t)).slice(0,6);"
                + " out.push('reserv-panels='+JSON.stringify(panels));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- unreserve flow --------------------------------------------------

    /** Enter the reserved MRN ({@code UnReservation.MRNO}) and click <b>Search</b> ({@code fnSearch()}). Returns the
     *  number of rows in the <b>List of Reserved Beds</b> table after the search. */
    public int enterMrnAndSearch(String mrn) {
        page.evaluate("(mrn)=>{ const e=document.querySelector('#mrn') || [...document.querySelectorAll(\"input[ng-model='UnReservation.MRNO']\")][0]; if(e){ const c=angular.element(e).controller('ngModel'); e.value=mrn; if(c){c.$setViewValue(mrn);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); } const b=[...document.querySelectorAll('button')].find(x=>/fnSearch/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }", mrn);
        page.waitForTimeout(3000);
        return reservedRowCount();
    }

    /** The "List of Reserved Beds" table = has a SELECT column + a REMARK column but NOT "unreserved". */
    private static final String RESERVED_TABLE_JS =
            "[...document.querySelectorAll('table')].find(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /select/.test(h) && /remark/.test(h) && !/unreserved/.test(h); })";
    /** The "List of Unreserved Beds" table = has "unreserved by". */
    private static final String UNRESERVED_TABLE_JS =
            "[...document.querySelectorAll('table')].find(x=>/unreserved by/i.test(((x.querySelector('thead')||{}).innerText||'')))";

    public int reservedRowCount() {
        Object r = page.evaluate("() => { const t=" + RESERVED_TABLE_JS + "; if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>{ const tx=(r.textContent||'').replace(/\\s+/g,' ').trim(); return tx && !/no records|no data/i.test(tx) && [...r.querySelectorAll('td')].length>3; }).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Tick the SELECT checkbox of the first row in the <b>List of Reserved Beds</b> table (real click). Returns the
     *  patient name of that row (for verifying it later in the unreserved list), or null. */
    public String selectFirstReservedBed() {
        // Set the checkbox's ng-model + fire change/triggerHandler (the reliable "select" mechanism — the validation
        // reads the model, and the styled checkbox often isn't directly clickable). Also tag it for a force click.
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const t=" + RESERVED_TABLE_JS + "; if(!t) return null;"
                + " const row=[...t.querySelectorAll('tbody tr')].find(r=>[...r.querySelectorAll('td')].length>3 && norm(r.textContent) && !/no records/i.test(r.textContent)); if(!row) return null;"
                + " const cells=[...row.querySelectorAll('td')].map(td=>norm(td.textContent)); const name=cells[1]||cells.find(c=>c && !/^\\d+$/.test(c))||'';"
                + " const cb=row.querySelector('input[type=checkbox],input[type=radio]'); if(!cb) return name+'||nocb';"
                + " cb.id='__unresSel'; const c=A.element(cb).controller('ngModel'); cb.checked=true; if(c){ c.$setViewValue(true); c.$render(); } cb.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(cb).triggerHandler('change');}catch(e){} if(window.jQuery){try{jQuery(cb).trigger('change');}catch(e){}}"
                + " return name+'||ticked='+cb.checked; }");
        if (r == null) { System.out.println("selectFirstReservedBed: no reserved row"); return null; }
        waitForAngular(500);
        return r.toString().split("\\|\\|")[0];
    }

    /** Click <b>Unreserve</b> ({@code SaveUnreserve()}), answer any confirm Yes/OK, and return the success toast. */
    public String clickUnreserveAndGetToast() {
        page.evaluate("() => { window.__unrToasts=[]; if(window.__unrObs) window.__unrObs.disconnect();"
                + " window.__unrObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__unrToasts.includes(t)) window.__unrToasts.push(t); }); });"
                + " window.__unrObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/SaveUnreserve/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*unreserve\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 30; i++) {
            page.evaluate("() => { const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')].find(m=>m.getBoundingClientRect().width>0 && /unreserve|are you sure|do you want|confirm|proceed/i.test(m.textContent||''));"
                    + " if(box){ const y=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|unreserve|continue|proceed)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(y) y.click(); }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t){ (window.__unrToasts=window.__unrToasts||[]); if(!window.__unrToasts.includes(t)) window.__unrToasts.push(t); } }); }");
            boolean done = Boolean.TRUE.equals(page.evaluate("() => (window.__unrToasts||[]).some(a=>/unreserv|success|saved|please|select|enter/i.test(a))"));
            if (done) break;
            page.waitForTimeout(600);
        }
        Object r = page.evaluate("() => { const a=window.__unrToasts||[]; return a.find(x=>/unreserv.*success|unreserved|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(500);
        return r == null ? "" : r.toString().trim();
    }

    /** Verify the given patient appears in the <b>List of Unreserved Beds</b> table.
     *  Re-runs Search (no MRN filter) partway through the poll: the table can be showing the DOM state from before
     *  Unreserve was clicked, and only a fresh search reloads it with the server's current data. */
    public boolean verifyInUnreservedList(String patientName) {
        if (patientName == null || patientName.isEmpty()) return false;
        String key = patientName.replaceAll("\\s+", "").toLowerCase();
        for (int i = 0; i < 12; i++) {
            boolean found = Boolean.TRUE.equals(page.evaluate("(key) => { const t=" + UNRESERVED_TABLE_JS + "; if(!t) return false; return [...t.querySelectorAll('tbody tr')].some(r=>(r.textContent||'').replace(/\\s+/g,'').toLowerCase().includes(key)); }", key));
            if (found) return true;
            if (i == 3 || i == 7) enterMrnAndSearch("");   // force a reload partway through the wait
            page.waitForTimeout(700);
        }
        return false;
    }

    // ---- navigation ------------------------------------------------------

    /** Open IP → Bed Management → Unreservation (real clicks + direct-route fallback discovered after first run). */
    public boolean navigateViaMenu() {
        page.evaluate("() => { const $=window.jQuery; try{ if($) [...document.querySelectorAll('.modal.in,.modal.show')].forEach(m=>{try{$(m).modal('hide');}catch(e){}}); }catch(e){} [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        try { for (Page pg : page.context().pages()) { if (pg != page && !pg.isClosed()) pg.close(); } } catch (Exception ignore) {}
        try { page.bringToFront(); } catch (Exception ignore) {}
        waitForAngular(400);
        // IP
        Object ip = page.evaluate("() => { const lis=[...document.querySelectorAll('li')]; const ipLi=lis.find(li=>{ const a=li.querySelector(':scope > a'); return a && /^\\s*IP\\s*$/i.test((a.textContent||'').trim()); }); const a=ipLi&&ipLi.querySelector(':scope > a'); if(!a) return false; a.id='__ipMenuTab'; return true; }");
        if (Boolean.TRUE.equals(ip)) { try { page.locator("#__ipMenuTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) {} waitForAngular(800); }
        // Bed Management
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*bed\\s*management\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // Unreservation
        Object un = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*un[-\\s]?reservation\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__unresvMenu'; return true; }");
        if (Boolean.TRUE.equals(un)) { try { page.locator("#__unresvMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) {} }
        else System.out.println("Unreservation.nav: 'Unreservation' link not found — using direct route");
        waitForAngular(1200);
        // Fallback: hash-navigate directly to #/UnReservation.
        if (!page.url().toLowerCase().contains("unreservation")) {
            try { page.evaluate("() => { window.location.hash = '#/UnReservation'; }"); } catch (Exception ignore) {}
            waitForAngular(1200);
        }
        try {
            page.waitForFunction("() => /unreservation/i.test(location.hash) && [...document.querySelectorAll('button')].some(b=>/fnSearch/.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("Unreservation.nav: screen not ready in time"); }
        waitForAngular(600);
        return page.url().toLowerCase().contains("unreservation");
    }
}
