package com.kpj.pages.Ip.BedManagement_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * IP &gt; Bed Management &gt; <b>Under Maintenance</b> — Page Object.
 *
 * <p>Flow: Add → select Ward → select Room Type → (if the bed list isn't generated, change the dropdown values) →
 * select a bed → enter a Remark → Save → success toast.</p>
 *
 * <p>NEW screen — navigation + fields discovered at runtime by the dump methods, then hard-wired.</p>
 */
public class UnderMaintenance extends BasePage {

    public UnderMaintenance(Page page) { super(page); }

    // ---- discovery (temporary) -------------------------------------------

    public String dumpMenuTree() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const items=[...document.querySelectorAll('a')].map(a=>({t:norm(a.textContent), h:a.getAttribute('href')||''})).filter(x=>x.t && x.t.length<45);"
                + " const seen=new Set(); const out=[]; for(const x of items){ const k=x.t+'|'+x.h; if(seen.has(k)) continue; seen.add(k); if(/bed manage|mainten|reserv|admission/i.test(x.t) || (x.h&&/#\\//.test(x.h))) out.push(x.t+'  ->  '+x.h); }"
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

    public static final String ROUTE = "#/underMaintenanceList";

    /** Click <b>Add</b> ({@code AddUnderMaintence()}) → the add form ({@code #/add-undermaintenance}). */
    public boolean clickAdd() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/AddUnderMaintence/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button,a')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => /add-undermaintenance/i.test(location.hash)",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception e) { System.out.println("clickAdd: add form did not open - " + e.getMessage()); }
        waitForAngular(1500);
        return page.url().toLowerCase().contains("add-undermaintenance");
    }

    /** Number of bed rows in the generated bed list (SELECT | WARD | ROOM TYPE | BED table with checkboxes). */
    public int bedCount() {
        Object r = page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /select/.test(h) && /\\bbed\\b/.test(h) && /ward/.test(h); }); if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox],input[type=radio]') && !/no records|no data/i.test(r.textContent||'')).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * Select a <b>Ward</b> ({@code undermaintenance.WardID}) + <b>Room Type</b> ({@code undermaintenance.BedClassID})
     * combination for which the system generates a bed list. Per the FSD the bed list is generated from these
     * filters, so if it's empty we change the dropdown values (iterate ward × room-type) until beds appear. Returns
     * "Ward=.. | RoomType=.. | beds=N" for the first combo that generates beds, else a diagnostic.
     */
    public String selectWardRoomTypeUntilBeds() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const setSel=(ng,idx)=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return null; e.selectedIndex=idx; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if(window.jQuery){try{jQuery(e).trigger('change');}catch(x){}} return norm(e.options[idx].textContent); };"
                + " const bedCount=()=>{ const t=[...document.querySelectorAll('table')].find(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /select/.test(h) && /\\bbed\\b/.test(h) && /ward/.test(h); }); if(!t) return 0; return [...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox],input[type=radio]') && !/no records|no data/i.test(r.textContent||'')).length; };"
                + " const wardSel=document.querySelector(\"select[ng-model='undermaintenance.WardID']\"); const rtSel=document.querySelector(\"select[ng-model='undermaintenance.BedClassID']\"); if(!wardSel||!rtSel) return '(no selects)';"
                + " const realOpts=sel=>[...sel.options].map((o,i)=>({i,t:norm(o.textContent),v:o.value})).filter(o=>o.v && !/^-*\\s*select/i.test(o.t));"
                + " const wardOpts=realOpts(wardSel); const rtOpts=realOpts(rtSel); let tries=0;"
                + " for(const ward of wardOpts){ setSel('undermaintenance.WardID', ward.i); await sleep(600);"
                + "   for(const rt of rtOpts){ setSel('undermaintenance.BedClassID', rt.i);"
                + "     for(let poll=0; poll<8; poll++){ await sleep(1500); const n=bedCount(); if(n>0) return 'Ward='+ward.t+' | RoomType='+rt.t+' | beds='+n; }"
                + "     tries++; if(tries>=45) return '(no beds after '+tries+' combos)'; } }"
                + " return '(no beds in any combo)'; }");
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Why the last {@link #selectFirstBed()} call returned null — e.g. every candidate row was rejected by
     *  the app's own gender-compatibility check. Empty string on success. */
    public String lastNoBedReason = "";

    /** Dismiss any visible generic alert/confirm modal (OK/Yes/Close) — confirmed live: ticking a bed's
     *  checkbox can pop an "Alert" modal ("Assignment not allowed... incompatible gender designation. Please
     *  select an appropriate bed.") that NOTHING in the prior code dismissed, leaving the run stuck with no
     *  further progress and no error either. Returns the alert's text if one was found and dismissed, else "". */
    private String dismissAnyAlert() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm],[class*=alert]')]"
                + "   .find(m=>m.getBoundingClientRect().width>0 && getComputedStyle(m).display!=='none');"
                + " if(!box) return '';"
                + " const text=norm(box.textContent).slice(0,200);"
                + " const btn=[...box.querySelectorAll('button,a')].find(x=>/^(ok|yes|close|cancel)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                + " if(btn) btn.click();"
                + " return text; }");
        return r == null ? "" : r.toString();
    }

    /** Select a bed from the generated list, trying EVERY candidate row (not just the first) until one is
     *  actually accepted by the app — confirmed live: ticking a bed's checkbox can trigger a client-side
     *  "Assignment not allowed... incompatible gender designation" alert that rejects THAT specific bed; the
     *  previous single-row version had no way to recover from this (no dismissal, no fallback), so the run
     *  just stalled with no error and no progress. Dismisses any alert that appears and moves to the next row
     *  when one does. Returns the bed row text on success, or null (with the reason in
     *  {@link #lastNoBedReason}) if every candidate row was rejected. */
    public String selectFirstBed() {
        lastNoBedReason = "";
        int rowCount = bedCount();
        StringBuilder rejected = new StringBuilder();
        for (int attempt = 0; attempt < Math.max(1, rowCount); attempt++) {
            Object r = page.evaluate("(skip) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                    + " const t=[...document.querySelectorAll('table')].find(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /select/.test(h) && /\\bbed\\b/.test(h) && /ward/.test(h); }); if(!t) return null;"
                    + " const rows=[...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox],input[type=radio]') && !/no records/i.test(r.textContent||''));"
                    + " const row=rows[skip]; if(!row) return null;"
                    + " const cb=row.querySelector('input[type=checkbox],input[type=radio]');"
                    + " if(cb.disabled) return { skipped:true, text: norm(row.textContent).slice(0,50) };"
                    + " const c=A.element(cb).controller('ngModel'); cb.checked=true; if(c){ c.$setViewValue(true); c.$render(); } cb.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(cb).triggerHandler('change');}catch(e){} if(window.jQuery){try{jQuery(cb).trigger('change');}catch(e){}}"
                    + " return { skipped:false, text: norm(row.textContent).slice(0,50) }; }", attempt);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> m = r instanceof java.util.Map ? (java.util.Map<String, Object>) r : null;
            if (m == null) break;   // ran out of rows
            String text = String.valueOf(m.get("text"));
            if (Boolean.TRUE.equals(m.get("skipped"))) { rejected.append(text).append("(disabled) "); continue; }
            waitForAngular(500);
            String alert = dismissAnyAlert();
            if (alert.isEmpty()) return text;   // no alert popped — the bed was accepted
            waitForAngular(400);
            rejected.append(text).append("(rejected: ").append(alert.length() > 80 ? alert.substring(0, 80) : alert).append(") ");
        }
        lastNoBedReason = rejected.length() == 0
                ? "no bed row found in the generated list"
                : "every candidate bed row was rejected: " + rejected;
        System.out.println("selectFirstBed: " + lastNoBedReason);
        return null;
    }

    /**
     * Select a SPECIFIC bed by name (e.g. "DC-26") rather than accepting whatever bed a blind combo
     * search finds first — for the Graphical View entry point, where a specific bed was already clicked
     * on the board. Keeps the pre-filled Ward as-is and iterates ONLY Room Type options until
     * {@code targetBedName} appears in either the Census or Non-Census list, then ticks its checkbox.
     *
     * <p>Confirmed live: Graphical View's "Under Maintenance" action pre-fills Ward correctly (it matches
     * the clicked bed) but Room Type does NOT — e.g. clicking bed DC-26 (tooltip "Room Type: DAY CARE,
     * Ward: DAY CARE") landed on this form with Room Type pre-set to an unrelated value, whose combo
     * legitimately returned zero beds. DC-26 only appears once Room Type is corrected to "DAY CARE" — the
     * bed's actual room type. {@link #selectWardRoomTypeUntilBeds()} + {@link #selectFirstBed()} (built for
     * the standalone screen, which starts with an EMPTY Ward/Room Type) would happily search past this and
     * mark a completely different bed under maintenance instead of the one actually clicked.</p>
     *
     * <p>Returns the selected row's text on success, or null (see {@link #lastNoBedReason}) if the target
     * bed never appeared in any Room Type list for the pre-filled Ward, or was rejected when selected.</p>
     */
    public String selectSpecificBed(String targetBedName) {
        lastNoBedReason = "";
        if (targetBedName == null || targetBedName.isBlank()) {
            lastNoBedReason = "no target bed name given";
            return null;
        }
        Object found = page.evaluate("async (target) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const rtSel=document.querySelector(\"select[ng-model='undermaintenance.BedClassID']\"); if(!rtSel) return {found:false, error:'no Room Type select'};"
                + " const opts=[...rtSel.options].map((o,i)=>({i,t:norm(o.textContent)})).filter(o=>o.t && o.t!=='--Select--');"
                + " const findRow=()=>{ const tables=[...document.querySelectorAll('table')].filter(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /select/.test(h) && /\\bbed\\b/.test(h) && /ward/.test(h); });"
                + "   for(const t of tables){ const rows=[...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox],input[type=radio]'));"
                + "     for(const row of rows){ if(norm(row.textContent).toUpperCase().includes(target.toUpperCase())) return row; } } return null; };"
                + " for(const rt of opts){ rtSel.selectedIndex=rt.i; rtSel.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(rtSel).triggerHandler('change');}catch(e){} if(window.jQuery){try{jQuery(rtSel).trigger('change');}catch(e){}}"
                + "   for(let poll=0; poll<8; poll++){ await new Promise(res=>setTimeout(res,700)); if(findRow()) return {found:true, roomType:rt.t}; } }"
                + " return {found:false, tried:opts.length}; }", targetBedName);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> fm = found instanceof java.util.Map ? (java.util.Map<String, Object>) found : java.util.Collections.emptyMap();
        if (!Boolean.TRUE.equals(fm.get("found"))) {
            lastNoBedReason = "bed '" + targetBedName + "' did not appear in any Room Type list for the pre-filled Ward ("
                    + fm.getOrDefault("tried", "?") + " room type(s) tried)"
                    + (fm.get("error") != null ? " — " + fm.get("error") : "");
            System.out.println("selectSpecificBed: " + lastNoBedReason);
            return null;
        }
        // Room Type is now the one that surfaces the target bed — tick its checkbox.
        Object r = page.evaluate("(target) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const tables=[...document.querySelectorAll('table')].filter(x=>{ const h=((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /select/.test(h) && /\\bbed\\b/.test(h) && /ward/.test(h); });"
                + " for(const t of tables){ const rows=[...t.querySelectorAll('tbody tr')].filter(r=>r.querySelector('input[type=checkbox],input[type=radio]'));"
                + "   const row=rows.find(r=>norm(r.textContent).toUpperCase().includes(target.toUpperCase())); if(!row) continue;"
                + "   const cb=row.querySelector('input[type=checkbox],input[type=radio]');"
                + "   if(cb.disabled) return { skipped:true, text: norm(row.textContent).slice(0,60) };"
                + "   const c=A.element(cb).controller('ngModel'); cb.checked=true; if(c){ c.$setViewValue(true); c.$render(); } cb.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(cb).triggerHandler('change');}catch(e){} if(window.jQuery){try{jQuery(cb).trigger('change');}catch(e){}}"
                + "   return { skipped:false, text: norm(row.textContent).slice(0,60) }; }"
                + " return null; }", targetBedName);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = r instanceof java.util.Map ? (java.util.Map<String, Object>) r : null;
        if (m == null) {
            lastNoBedReason = "bed '" + targetBedName + "' row disappeared before it could be selected";
            System.out.println("selectSpecificBed: " + lastNoBedReason);
            return null;
        }
        String text = String.valueOf(m.get("text"));
        if (Boolean.TRUE.equals(m.get("skipped"))) {
            lastNoBedReason = "bed '" + targetBedName + "' row is disabled: " + text;
            System.out.println("selectSpecificBed: " + lastNoBedReason);
            return null;
        }
        waitForAngular(500);
        String alert = dismissAnyAlert();
        if (!alert.isEmpty()) {
            lastNoBedReason = "bed '" + targetBedName + "' rejected: " + (alert.length() > 100 ? alert.substring(0, 100) : alert);
            System.out.println("selectSpecificBed: " + lastNoBedReason);
            return null;
        }
        return text;
    }

    /** Enter the <b>Remark</b> ({@code undermaintenance.remark}). */
    public void enterRemark(String remark) {
        page.evaluate("(v) => { const e=document.querySelector('#multiBedAllocationRemarks') || [...document.querySelectorAll('textarea,input')].find(x=>x.getAttribute('ng-model')==='undermaintenance.remark'); if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", remark);
        waitForAngular(300);
    }

    /** Set the maintenance <b>Date</b> ({@code undermaintenance.date}) and <b>Expected date</b>
     *  ({@code undermaintenance.excepteddate}) if empty (they can be mandatory). */
    public void ensureDates(String from, String expected) {
        page.evaluate("(a) => { const set=(ng,v)=>{ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; if((e.value||'').trim()) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }; set('undermaintenance.date', a.from); set('undermaintenance.excepteddate', a.exp); }",
                java.util.Map.of("from", from, "exp", expected));
        waitForAngular(300);
    }

    /** Click <b>Save</b> ({@code IUDBedUnderMaitenance()}), answer any confirm Yes/OK, return the success toast. */
    public String saveAndGetToast() {
        page.evaluate("() => { window.__umToasts=[]; if(window.__umObs) window.__umObs.disconnect();"
                + " window.__umObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__umToasts.includes(t)) window.__umToasts.push(t); }); });"
                + " window.__umObs.observe(document.body,{childList:true,subtree:true});"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>/IUDBedUnderMaitenance/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...document.querySelectorAll('button')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        for (int i = 0; i < 25; i++) {
            // No content filter on the box's text — broadened after confirming live that a plain "Alert" modal
            // (e.g. a gender-compatibility rejection) can appear here too, and the previous
            // mainten/confirm/proceed-only filter would silently never dismiss it, stalling the run.
            page.evaluate("() => { const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm],[class*=alert]')].find(m=>m.getBoundingClientRect().width>0 && getComputedStyle(m).display!=='none');"
                    + " if(box){ const y=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|save|continue|proceed|close|cancel)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(y) y.click(); }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t){ (window.__umToasts=window.__umToasts||[]); if(!window.__umToasts.includes(t)) window.__umToasts.push(t); } }); }");
            boolean done = Boolean.TRUE.equals(page.evaluate("() => (window.__umToasts||[]).some(a=>/mainten|success|saved|added|please|select|enter|remark/i.test(a))"));
            if (done) break;
            page.waitForTimeout(600);
        }
        Object r = page.evaluate("() => { const a=window.__umToasts||[]; return a.find(x=>/mainten.*success|success|saved|added/i.test(x)) || a[0] || ''; }");
        waitForAngular(500);
        return r == null ? "" : r.toString().trim();
    }

    // ---- auto-pick-and-mark-under-maintenance (shared by every caller: the standalone Under Maintenance
    // "Add" flow AND the Graphical View "vacant bed -> Under Maintenance" action) --------------------------

    /** Result of {@link #markBedUnderMaintenance}: everything a caller needs to report each step, plus a
     *  ready-made {@link #failReason}. */
    public static final class MaintenanceResult {
        public String combo, bed, toast;
        public boolean bedsOk, ok;
        public String failReason = "";
    }

    /** Select Ward + Room Type (changing the combo until a bed list generates) → select a bed → enter a
     *  Remark → Save. Assumes the caller is already on the {@code #/add-undermaintenance} form (either via
     *  the standalone screen's own "Add" button, or via Graphical View's "Under Maintenance" toolbar button
     *  on a selected vacant bed — both land on the SAME form). */
    public MaintenanceResult markBedUnderMaintenance(String dateFrom, String dateExpected, String remark) {
        MaintenanceResult res = new MaintenanceResult();
        ensureDates(dateFrom, dateExpected);

        res.combo = selectWardRoomTypeUntilBeds();
        int beds = bedCount();
        res.bedsOk = beds > 0;
        if (!res.bedsOk) {
            res.failReason = "No bed list generated for any Ward/Room Type combo tried: " + res.combo;
            return res;
        }

        res.bed = selectFirstBed();
        if (res.bed == null) {
            res.failReason = "A bed list generated (" + res.combo + ") but no bed row could be selected from it"
                    + (lastNoBedReason.isEmpty() ? "" : " — " + lastNoBedReason);
            return res;
        }

        enterRemark(remark);
        res.toast = saveAndGetToast();
        res.ok = res.toast != null && (res.toast.toLowerCase().contains("mainten") || res.toast.toLowerCase().contains("success")
                || res.toast.toLowerCase().contains("saved") || res.toast.toLowerCase().contains("added"));
        if (!res.ok) {
            res.failReason = res.toast == null || res.toast.isEmpty()
                    ? "Save (IUDBedUnderMaitenance()) produced no toast at all for bed " + res.bed
                    : "Save rejected bed " + res.bed + ": \"" + res.toast + "\"";
        }
        return res;
    }

    /**
     * Same outcome as {@link #markBedUnderMaintenance}, but for the Graphical View entry point: marks the
     * SPECIFIC bed the caller already picked on the board ({@code targetBedName}, e.g. "DC-26") under
     * maintenance, instead of accepting whichever bed a blind Ward/Room Type combo search happens to find
     * first. See {@link #selectSpecificBed} for why the blind search is unsafe here — Room Type is
     * pre-filled WRONG for the clicked bed, so it can find and mark a different bed entirely.
     */
    public MaintenanceResult markSpecificBedUnderMaintenance(String targetBedName, String dateFrom, String dateExpected, String remark) {
        MaintenanceResult res = new MaintenanceResult();
        ensureDates(dateFrom, dateExpected);

        res.bed = selectSpecificBed(targetBedName);
        res.bedsOk = res.bed != null;
        res.combo = "target bed=" + targetBedName;
        if (!res.bedsOk) {
            res.failReason = "Bed '" + targetBedName + "' could not be selected: "
                    + (lastNoBedReason.isEmpty() ? "not found in any Room Type list for the pre-filled Ward" : lastNoBedReason);
            return res;
        }

        enterRemark(remark);
        res.toast = saveAndGetToast();
        res.ok = res.toast != null && (res.toast.toLowerCase().contains("mainten") || res.toast.toLowerCase().contains("success")
                || res.toast.toLowerCase().contains("saved") || res.toast.toLowerCase().contains("added"));
        if (!res.ok) {
            res.failReason = res.toast == null || res.toast.isEmpty()
                    ? "Save (IUDBedUnderMaitenance()) produced no toast at all for bed " + res.bed
                    : "Save rejected bed " + res.bed + ": \"" + res.toast + "\"";
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
        Object um = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/under\\s*mainten|bed\\s*mainten/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__umMenu'; return true; }");
        if (Boolean.TRUE.equals(um)) { try { page.locator("#__umMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception e) {} }
        else System.out.println("UnderMaintenance.nav: 'Under Maintenance' link not found — using direct route");
        waitForAngular(1200);
        if (!page.url().toLowerCase().contains("undermaintenance")) {
            try { page.evaluate("() => { window.location.hash = '#/underMaintenanceList'; }"); } catch (Exception ignore) {}
            waitForAngular(1200);
        }
        try {
            page.waitForFunction("() => /undermaintenance/i.test(location.hash) && [...document.querySelectorAll('button,a')].some(b=>/AddUnderMaintence/.test(b.getAttribute('ng-click')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("UnderMaintenance.nav: list not ready in time"); }
        waitForAngular(600);
        return page.url().toLowerCase().contains("undermaintenance");
    }
}
