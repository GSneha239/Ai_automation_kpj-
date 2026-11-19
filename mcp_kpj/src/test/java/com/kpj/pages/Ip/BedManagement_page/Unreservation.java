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
        // The screen defaults From = To = today, but a reservation is listed by its valid-until date (usually tomorrow+),
        // so widen the range (-30d .. +60d) — otherwise freshly reserved beds are never listed.
        Object dm = page.evaluate("() => { const A=window.angular; const fmt=d=>String(d.getDate()).padStart(2,'0')+'/'+String(d.getMonth()+1).padStart(2,'0')+'/'+d.getFullYear();"
                + " const ins=[...document.querySelectorAll('input')].filter(i=>i.offsetParent!==null && /date/i.test((i.getAttribute('ng-model')||'')+(i.id||'')+(i.name||'')));"
                + " const day=n=>{const d=new Date(); d.setDate(d.getDate()+n); return fmt(d);};"
                + " const set=(e,v)=>{ const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " if(ins.length>=2){ set(ins[0],day(-30)); set(ins[1],day(60)); }"
                + " return ins.map(i=>i.getAttribute('ng-model')).join(','); }");
        System.out.println("Unreservation date inputs widened => " + dm);
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
    public String selectFirstReservedBed() { return selectReservedBed(0); }

    /** Tick the SELECT checkbox of row {@code idx} (0-based, among the real rows) of the <b>List of Reserved Beds</b> and UNtick every other row; same return as {@link #selectFirstReservedBed()}. */
    public String selectReservedBed(final int idx) {
        // Set the checkbox's ng-model + fire change/triggerHandler (the reliable "select" mechanism — the validation
        // reads the model, and the styled checkbox often isn't directly clickable). Also tag it for a force click.
        Object r = page.evaluate("(idx) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const t=" + RESERVED_TABLE_JS + "; if(!t) return null;"
                + " const rows=[...t.querySelectorAll('tbody tr')].filter(r=>[...r.querySelectorAll('td')].length>3 && norm(r.textContent) && !/no records/i.test(r.textContent)); const row=rows[idx]; if(!row) return null;"
                + " rows.forEach(x=>{ if(x===row) return; const o=x.querySelector('input[type=checkbox],input[type=radio]'); if(o && o.checked){ const oc=A.element(o).controller('ngModel'); o.checked=false; if(oc){ oc.$setViewValue(false); oc.$render(); } o.dispatchEvent(new Event('change',{bubbles:true})); } });"
                + " const cells=[...row.querySelectorAll('td')].map(td=>norm(td.textContent)); const name=cells[1]||cells.find(c=>c && !/^\\d+$/.test(c))||'';"
                + " const cb=row.querySelector('input[type=checkbox],input[type=radio]'); if(!cb) return name+'||nocb';"
                + " cb.id='__unresSel'; const c=A.element(cb).controller('ngModel'); cb.checked=true; if(c){ c.$setViewValue(true); c.$render(); } cb.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(cb).triggerHandler('change');}catch(e){} if(window.jQuery){try{jQuery(cb).trigger('change');}catch(e){}}"
                + " return name+'||ticked='+cb.checked+'||'+(cells[4]||'')+'||'+(cells[5]||''); }", idx);
        if (r == null) { System.out.println("selectFirstReservedBed: no reserved row"); return null; }
        waitForAngular(500);
        String[] parts = r.toString().split("\\|\\|");
        lastBed = parts.length > 2 ? parts[2].trim() : "";
        lastFromDate = parts.length > 3 ? parts[3].trim() : "";
        return parts[0];
    }

    /** The Bed and From Date of the row ticked by {@link #selectFirstReservedBed()} - what the unreserved list must show for it. */
    public String lastBed = "", lastFromDate = "";

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
            String bedKey = lastBed.replaceAll("\\s+", "").toLowerCase(), dateKey = lastFromDate.replaceAll("\\s+", "").toLowerCase();
            // the patient's NAME alone also matches an OLD unreservation of the same patient (found on 2026-11-18: 'John Peter' of 24/10 in CICU matched a refused unreserve of his PREMIER WARD bed), so the
            // row must show the bed and the From Date of the reservation that was ticked as well
            boolean found = Boolean.TRUE.equals(page.evaluate("(k) => { const t=" + UNRESERVED_TABLE_JS + "; if(!t) return false; return [...t.querySelectorAll('tbody tr')].some(r=>{ const x=(r.textContent||'').replace(/\\s+/g,'').toLowerCase(); return x.includes(k[0]) && (!k[1] || x.includes(k[1])) && (!k[2] || x.includes(k[2])); }); }", java.util.Arrays.asList(key, bedKey, dateKey)));
            if (found) return true;
            if (i == 3 || i == 7) enterMrnAndSearch("");   // force a reload partway through the wait
            page.waitForTimeout(700);
        }
        return false;
    }

    // ---- navigation ------------------------------------------------------

    private static final String NJS =
            "const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
          + "const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);";

    /** 1 = the group is open (its li carries menu-open), 0 = closed, -1 = not on screen. {@code group} null = the IP entry itself. */
    private int menuState(String group) {
        Object r = page.evaluate("(g) => {" + NJS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)==='IP'); if(!pa) return -1; const pli=pa.closest('li'); if(!pli) return -1;"
                + " if(!g) return pli.classList.contains('menu-open')?1:0;"
                + " const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===g); if(!c) return -1; const li=c.closest('li'); return li && li.classList.contains('menu-open')?1:0; }", group);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    private boolean clickMenu(String group) {
        Object r = page.evaluate("(g) => {" + NJS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)==='IP'); if(!pa) return false;"
                + " if(!g){ pa.click(); return true; } const pli=pa.closest('li'); const c=[...pli.querySelectorAll('a')].filter(vis).find(x=>x!==pa && norm(x.textContent)===g); if(!c) return false; c.click(); return true; }", group);
        return Boolean.TRUE.equals(r);
    }

    /** Tag the "UnReservation" link inside the IP entry (visible only once Bed Management is open). */
    private boolean tagUnreservationLink() {
        Object r = page.evaluate("() => {" + NJS + " const pa=[...document.querySelectorAll('a')].filter(vis).find(x=>norm(x.textContent)==='IP'); if(!pa) return false; const pli=pa.closest('li');"
                + " const l=[...pli.querySelectorAll('a[href]')].filter(vis).find(x=>/^un[-\\s]?reservation$/i.test(norm(x.textContent))); if(!l) return false; l.id='__unresvMenu'; return true; }");
        return Boolean.TRUE.equals(r);
    }

    private boolean untagProbe() {
        boolean t = tagUnreservationLink();
        page.evaluate("() => { const e=document.getElementById('__unresvMenu'); if(e) e.removeAttribute('id'); }");
        return t;
    }

    private boolean waitFor(java.util.function.BooleanSupplier cond, int ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (cond.getAsBoolean()) return true; page.waitForTimeout(250); }
        return cond.getAsBoolean();
    }

    /**
     * Open IP → Bed Management → UnReservation. The menu builds each level lazily (the Bed Management children took seconds to appear on build .488, so the old 0.7 s wait found no link and fell back to a hash change
     * that raised the "Reload page" prompt - same fault as Under Maintenance, fixed the same way): the IP entry and the Bed Management group are each opened ONCE and awaited before the link is looked for.
     */
    public boolean navigateViaMenu() {
        page.evaluate("() => { const $=window.jQuery; try{ if($) [...document.querySelectorAll('.modal.in,.modal.show')].forEach(m=>{try{$(m).modal('hide');}catch(e){}}); }catch(e){} [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        try { for (Page pg : page.context().pages()) { if (pg != page && !pg.isClosed()) pg.close(); } } catch (Exception ignore) {}
        try { page.bringToFront(); } catch (Exception ignore) {}
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='IP')",
                null, new Page.WaitForFunctionOptions().setTimeout(45000)); } catch (Exception ignore) { }
        waitForAngular(1200);
        for (int attempt = 0; attempt < 3 && !page.url().toLowerCase().contains("unreservation"); attempt++) {
            if (!untagProbe()) {
                if (menuState(null) != 1) clickMenu(null);
                waitFor(() -> menuState("Bed Management") >= 0, 15000);
                if (menuState("Bed Management") == 0) clickMenu("Bed Management");
                waitFor(this::untagProbe, 15000);
            }
            if (tagUnreservationLink()) {
                try { page.locator("#__unresvMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("Unreservation.nav: click failed - " + e.getMessage().split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__unresvMenu'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else System.out.println("Unreservation.nav: 'UnReservation' link not found (attempt " + (attempt + 1) + ")");
        }
        try {
            page.waitForFunction("() => /unreservation/i.test(location.hash) && [...document.querySelectorAll('button')].some(b=>/fnSearch/.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("Unreservation.nav: screen not ready in time"); }
        waitForAngular(600);
        return page.url().toLowerCase().contains("unreservation");
    }
}
