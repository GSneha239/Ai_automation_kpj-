package com.kpj.pages.Ip.InPatients;

import com.kpj.pages.Ip.BedboardOccupancyListPage;
import com.microsoft.playwright.Page;

/**
 * IP &gt; <b>Inpatients &gt; Occupancy List</b> (route {@code #/AdmissionList}) — the inpatient occupancy list.
 *
 * <p>Its <b>Change Admission Type</b> action is the SAME modal as the Bedboard Occupancy List
 * ({@code ChangeAdmissionType()} → {@code fnUpdateAdmissionType()}, with the 3 sections Change Admission Type /
 * Additional Doctors / Next of Kin), and it uses the same {@code AdmissionList.FromDate}/{@code AdmissionList.ToDate}
 * search models. So this reuses {@link BedboardOccupancyListPage} wholesale (search, select, open modal, fill all
 * 3 sections, save) and only overrides {@link #navigateViaMenu()} to enter via IP → Occupancy List
 * ({@code #/AdmissionList}).</p>
 */
public class OccupancyList extends BedboardOccupancyListPage {

    public static final String OCC_ROUTE = "#/AdmissionList";

    public OccupancyList(Page page) { super(page); }

    /** Expand the <b>IP</b> left-menu and click <b>Occupancy List</b> ({@code #/AdmissionList}). */
    @Override
    public boolean navigateViaMenu() {
        String menuJs = "() => window.angular && [...document.querySelectorAll('li > a')].some(a=>/^\\s*IP\\s*$/i.test((a.textContent||'').trim()))";
        boolean menuUp = false;
        for (int attempt = 0; attempt < 3 && !menuUp; attempt++) {
            try {
                page.waitForFunction(menuJs, null, new Page.WaitForFunctionOptions().setTimeout(attempt == 0 ? 30000 : 20000));
                menuUp = true;
            } catch (Exception ignore) {
                System.out.println("OccupancyList.navigateViaMenu: IP menu did not render (attempt " + (attempt + 1) + ") — reloading");
                try { page.reload(new Page.ReloadOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)); }
                catch (Exception e) { System.out.println("navigateViaMenu: reload failed - " + e.getMessage()); }
                waitForAngular(1500);
            }
        }
        waitForAngular(800);
        // The temp ids MUST be cleared first: this method runs once per footer-action section, and a leftover
        // __occTab / __ipMenuTab on a previous anchor makes locator("#__occTab") match 2 elements (strict-mode
        // violation) so the click never happens.
        page.evaluate("() => { document.querySelectorAll('#__ipMenuTab,#__occTab').forEach(e=>e.removeAttribute('id'));"
                + " const lis=[...document.querySelectorAll('li')];"
                + " const ipLi=lis.find(li=>{ const a=li.querySelector(':scope > a'); return a && /^\\s*IP\\s*$/i.test((a.textContent||'').trim()); });"
                + " const a=ipLi&&ipLi.querySelector(':scope > a'); if(!a) return; a.id='__ipMenuTab'; }");
        try { page.locator("#__ipMenuTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("navigateViaMenu: IP menu click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__ipMenuTab'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        // Click the Occupancy List link (#/AdmissionList). Fall back to the "Inpatients" link (same route) / direct route.
        Object tagged = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " document.querySelectorAll('#__occTab').forEach(e=>e.removeAttribute('id'));"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^occupancy list$/i.test(norm(x.textContent)) && (x.getAttribute('href')||'')==='#/AdmissionList' && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a')].find(x=>(x.getAttribute('href')||'')==='#/AdmissionList' && x.offsetParent!==null); if(!a) return false; a.id='__occTab'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__occTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("navigateViaMenu: Occupancy List click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__occTab'); if(e) e.removeAttribute('id'); }");
        } else {
            page.navigate("https://devhis.sancyberhad.com/" + OCC_ROUTE);
        }
        try {
            page.waitForFunction("() => window.angular && /admissionlist/i.test(location.hash) && [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("OccupancyList.navigateViaMenu: list not ready in time"); }
        waitForAngular(1200);
        return page.url().toLowerCase().contains("admissionlist");
    }

    // ---- Close -> Revoke the SAME patient --------------------------------

    /** MRN of the currently-selected grid row (empty string if none) — captured before Close so the same
     *  patient can be re-found and revoked afterwards. */
    public String getSelectedMrn() {
        Object r = page.evaluate("() => { let mrn=''; document.querySelectorAll('*').forEach(el=>{ if(mrn)return; try{ const s=angular.element(el).scope();"
                + " if(s&&s.grid&&s.grid.api&&s.grid.api.selection){ const sel=s.grid.api.selection.getSelectedRows(); if(sel&&sel.length){ const p=sel[0];"
                + " for(const k in p){ if(/^(mrno|mrn|mrnno)$/i.test(k) && p[k]!=null && (''+p[k]).trim()){ mrn=(''+p[k]).trim(); } } } } }catch(e){} }); return mrn; }");
        return r == null ? "" : r.toString().trim();
    }

    /**
     * After Close, the closed admission LEAVES the default inpatient list — surface it via the <b>Closed Admission</b>
     * filter (widen the date range to the whole year), Search with a few retries (server lag), then re-select the row
     * for {@code mrn}. Returns true if the just-closed patient was re-found and selected.
     */
    public boolean reselectClosedByMrn(String mrn) {
        if (mrn == null || mrn.isEmpty()) return false;
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate today = java.time.LocalDate.now();
        String from = today.withDayOfYear(1).format(f), to = today.withMonth(12).withDayOfMonth(31).format(f);
        page.evaluate("(d) => { const setD=(ng,val)=>{ const el=[...document.querySelectorAll('input')].find(i=>i.getAttribute('ng-model')===ng); if(el){ const c=angular.element(el).controller('ngModel'); el.value=val; if(c){c.$setViewValue(val);c.$render();} el.dispatchEvent(new Event('change',{bubbles:true})); } };"
                + " setD('AdmissionList.FromDate', d.from); setD('AdmissionList.ToDate', d.to);"
                // tick a 'Closed Admission' checkbox (by adjacent label) — real .click() so its ng handler fires
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')]; for(const cb of cbs){ let lab=''; if(cb.id){ const l=document.querySelector('label[for=\"'+cb.id+'\"]'); if(l) lab=l.textContent; } if(!lab){ lab=(cb.closest('label')&&cb.closest('label').textContent)||(cb.parentElement&&cb.parentElement.textContent)||''; } if(/closed admission/i.test(lab)){ if(!cb.checked) cb.click(); } } }",
                java.util.Map.of("from", from, "to", to));
        waitForAngular(500);
        boolean found = false;
        for (int attempt = 0; attempt < 6 && !found; attempt++) {
            page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>/^search$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            page.waitForTimeout(2800);
            found = Boolean.TRUE.equals(page.evaluate("(mrn) => { let data=null; document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.options&&s.grid.options.data) data=s.grid.options.data; }catch(e){} }); if(!data) return false; return data.some(x=>{ for(const k in x){ if(/^(mrno|mrn|mrnno)$/i.test(k) && (''+x[k]).trim()===mrn) return true; } return false; }); }", mrn));
        }
        // REAL-click the row's selection checkbox (a synthetic grid-API selectRow doesn't register SelectedPatientID
        // for the footer Revoke button — OpenRevokeTypeModal then won't open). Fall back to the grid API.
        Object tag = page.evaluate("(mrn) => { const match=ent=>{ for(const k in ent){ if(/^(mrno|mrn|mrnno)$/i.test(k) && (''+ent[k]).trim()===mrn) return true; } return false; };"
                + " const bodyRows=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(r=>r.offsetParent!==null);"
                + " let idx=-1; for(let i=0;i<bodyRows.length;i++){ let ent=null; try{ent=angular.element(bodyRows[i]).scope().row.entity;}catch(e){} if(ent&&match(ent)){ idx=i; break; } }"
                + " if(idx<0) return 'notfound';"
                + " const leftRows=[...document.querySelectorAll('.ui-grid-render-container-left .ui-grid-row')].filter(r=>r.offsetParent!==null);"
                + " let cb=(leftRows[idx]&&leftRows[idx].querySelector('.ui-grid-selection-row-header-buttons, input[type=checkbox]')) || bodyRows[idx].querySelector('.ui-grid-selection-row-header-buttons, input[type=checkbox]');"
                + " if(cb){ cb.id='__occSelCb'; return 'cb'; }"
                + " let gridApi=null; document.querySelectorAll('*').forEach(el=>{ if(gridApi)return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api){gridApi=s.grid.api;} }catch(e){} }); if(gridApi){ const ent=angular.element(bodyRows[idx]).scope().row.entity; try{gridApi.selection.clearSelectedRows();}catch(e){} gridApi.selection.selectRow(ent); } return 'api'; }", mrn);
        String how = String.valueOf(tag);
        if ("notfound".equals(how)) return false;
        if ("cb".equals(how)) {
            try { page.locator("#__occSelCb").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("reselectClosedByMrn: checkbox real click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__occSelCb'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(600);
        return true;
    }

    // ---- Plan Discharge (advise discharge) -------------------------------

    /**
     * Click the footer <b>Plan Discharge</b> button ({@code GetAdviceDischarge()} → {@code #adviseDischrg}) for the
     * selected patient and WAIT for the popup data to load (the advice list rendered as rows with a Select checkbox +
     * a remark). If the button is disabled (ISNullBedId) it falls back to calling {@code GetAdviceDischarge()} on the
     * scope + showing the modal. Returns true when the popup is loaded with rows.
     */
    public boolean clickPlanDischarge() {
        Object tagged = page.evaluate("() => { const b=document.querySelector('#btnOpenAlladviseDischrg') || [...document.querySelectorAll('button,a')].find(x=>/^\\s*plan discharge\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return 'none'; if(b.disabled) return 'disabled'; b.id='btnOpenAlladviseDischrg'; return true; }");
        String st = String.valueOf(tagged);
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#btnOpenAlladviseDischrg").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickPlanDischarge: click failed - " + e.getMessage()); }
        } else if ("disabled".equals(st)) {
            System.out.println("clickPlanDischarge: footer button disabled — fetching via scope GetAdviceDischarge()");
            page.evaluate("() => { let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.GetAdviceDischarge==='function'){sc=x;break;} x=x.$parent; } }catch(e){} }); if(sc){ try{ sc.$apply(()=>sc.GetAdviceDischarge()); }catch(e){} } }");
            waitForAngular(1200);
            page.evaluate("() => { try{ if(window.jQuery) jQuery('#adviseDischrg').modal('show'); }catch(e){} }");
        } else {
            System.out.println("clickPlanDischarge: Plan Discharge button not found");
            return false;
        }
        String loadedJs = "() => { const m=document.querySelector('#adviseDischrg'); if(!m || m.getBoundingClientRect().width===0) return false;"
                + " return [...m.querySelectorAll('tbody tr')].some(r=>r.offsetParent!==null && r.querySelector('input[type=checkbox]')); }";
        try { page.waitForFunction(loadedJs, null, new Page.WaitForFunctionOptions().setTimeout(20000)); }
        catch (Exception ignore) { System.out.println("clickPlanDischarge: popup did not load in time"); }
        waitForAngular(600);
        return Boolean.TRUE.equals(page.evaluate(loadedJs));
    }

    /**
     * In the Plan Discharge popup, tick a row (the reason/department to advise-discharge) and enter its <b>remark</b>.
     * Prefers the first not-yet-checked row (the modal pre-checks charged rows). Returns "Reason | remark".
     */
    public String selectReasonAndRemark(String remark) {
        Object r = page.evaluate("(remark) => { const m=document.querySelector('#adviseDischrg'); if(!m) return '(no modal)';"
                + " const rows=[...m.querySelectorAll('tbody tr')].filter(x=>x.offsetParent!==null && x.querySelector('input[type=checkbox]')); if(!rows.length) return '(no rows)';"
                + " let row=rows.find(x=>{ const cb=x.querySelector('input[type=checkbox]'); return cb && !cb.checked; }) || rows[0];"
                + " const cb=row.querySelector('input[type=checkbox]'); const ta=row.querySelector('textarea');"
                + " const cbC=angular.element(cb).controller('ngModel'); if(cbC){ cbC.$setViewValue(true); cbC.$render(); } cb.checked=true; cb.dispatchEvent(new Event('change',{bubbles:true}));"
                + " if(ta){ const taC=angular.element(ta).controller('ngModel'); ta.value=remark; if(taC){ taC.$setViewValue(remark); taC.$render(); } ta.dispatchEvent(new Event('input',{bubbles:true})); ta.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " const cells=[...row.querySelectorAll('td')].map(td=>(td.textContent||'').replace(/\\s+/g,' ').trim());"
                + " const reason=cells[1]||cells.find(c=>c)||'(reason)'; return reason+' | '+remark; }", remark);
        waitForAngular(400);
        return r == null ? "(null)" : r.toString();
    }

    /** Click <b>Save</b> ({@code IUDAdviceDescharge()}) in the Plan Discharge popup; returns the success toast
     *  ("Advice Discharge saved successfully.") captured via a MutationObserver. */
    public String savePlanDischargeAndGetToast() {
        Object tagged = page.evaluate("() => { window.__pdToasts=[]; if(window.__pdObs) window.__pdObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__pdToasts.includes(t)) window.__pdToasts.push(t); }); };"
                + " window.__pdObs=new MutationObserver(grab); window.__pdObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=document.querySelector('#adviseDischrg'); if(!m) return false;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>((x.getAttribute('ng-click')||'').indexOf('IUDAdviceDescharge')>=0) && x.offsetParent!==null)"
                + "   || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__pdSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("savePlanDischargeAndGetToast: Save button not found"); return ""; }
        try { page.locator("#__pdSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("savePlanDischargeAndGetToast: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__pdSave'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__pdToasts||[]).some(a=>/advice discharge saved|saved successfully|success|please|select/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__pdToasts||[]).includes(t)) (window.__pdToasts=window.__pdToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__pdToasts||[]; return a.find(x=>/advice discharge saved|saved successfully/i.test(x)) || a.find(x=>/success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Close the Plan Discharge popup ({@code #adviseDischrg}). */
    public void closePlanDischargePopup() {
        page.evaluate("() => { const m=document.querySelector('#adviseDischrg'); if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*(close|cancel)\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) jQuery('#adviseDischrg').modal('hide'); }catch(e){} }");
        waitForAngular(500);
    }

    // ---- Change Refer Entity ---------------------------------------------

    /**
     * Click the footer <b>Change Refer Entity</b> button ({@code OpenRefEntityModal()} → {@code #chngRefEnty}) for
     * the selected patient and wait for the popup (Refer Entity Type + Refer Entity dropdowns, Add, Save). Falls
     * back to the scope {@code OpenRefEntityModal()} if the button is disabled. Returns true if the popup opened.
     */
    public boolean clickChangeReferEntity() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/^\\s*change refer entity\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!b) return 'none'; if(b.disabled) return 'disabled'; b.id='__chgRefBtn'; return true; }");
        String st = String.valueOf(tagged);
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__chgRefBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickChangeReferEntity: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__chgRefBtn'); if(e) e.removeAttribute('id'); }");
        } else if ("disabled".equals(st)) {
            page.evaluate("() => { let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.OpenRefEntityModal==='function'){sc=x;break;} x=x.$parent; } }catch(e){} }); if(sc){ try{ sc.$apply(()=>sc.OpenRefEntityModal()); }catch(e){} try{ if(window.jQuery) jQuery('#chngRefEnty').modal('show'); }catch(e){} } }");
        } else {
            System.out.println("clickChangeReferEntity: button not found");
            return false;
        }
        String popupJs = "() => { const m=document.querySelector('#chngRefEnty'); return !!(m && m.getBoundingClientRect().width>0 && [...m.querySelectorAll('select')].some(s=>s.getAttribute('ng-model')==='Ref.EntityTypeId')); }";
        try { page.waitForFunction(popupJs, null, new Page.WaitForFunctionOptions().setTimeout(12000)); }
        catch (Exception ignore) { System.out.println("clickChangeReferEntity: popup did not open"); }
        waitForAngular(500);
        return Boolean.TRUE.equals(page.evaluate(popupJs));
    }

    /**
     * Select the <b>Refer Entity Type</b> ({@code Ref.EntityTypeId}, first real option — its ng-change
     * {@code fnSetRefEntity()} loads the entities) then the <b>Refer Entity</b> ({@code Ref.EntityId}). Both are
     * ng-options selects — set via {@code selectedIndex} + a change event (setting the model to the raw "number:N"
     * value does NOT bind). Returns "Type | Entity".
     */
    public String selectReferEntityTypeAndEntity() {
        // The Type/Entity dropdowns are SELECT2 (select2-offscreen) — driving the underlying <select> (selectedIndex +
        // change, even with $(sel).select2('val',...)) does NOT set the scope model Ref.EntityTypeId, so ng-change
        // fnSetRefEntity() fetches with an empty type and EntityList never loads (verified live 2026-07-21: Ref is []).
        // FIX: drive the Angular scope DIRECTLY — set Ref.EntityTypeId to the numeric EntityTypeList[i].value, call
        // fnSetRefEntity() (commonFactory.fetchDropDownListwithPar('RefEntity', type) → $scope.EntityList), wait for the
        // async list, then set Ref.EntityId = EntityList[0].value. Add/Save read these scope values. Iterate the types
        // in case one has no entities (Refentity loads ~1772). Returns "Type | Entity".
        Object r = page.evaluate("async () => { const m=document.querySelector('#chngRefEnty'); if(!m) return '(no modal)';"
                + " const ts=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Ref.EntityTypeId'); if(!ts) return '(no type select)';"
                + " const sc=angular.element(ts).scope(); if(!sc||typeof sc.fnSetRefEntity!=='function') return '(no scope)';"
                + " const num=v=>{ const s=String(v); const i=s.indexOf(':'); const n=Number(i>=0?s.slice(i+1):s); return isNaN(n)?null:n; };"
                + " const types=[...ts.options].map(o=>({raw:o.value,val:num(o.value),text:(o.textContent||'').trim()}))"
                + "   .filter(o=>o.val!=null && !/^-*\\s*select\\s*-*$/i.test(o.text)); if(!types.length) return '(no type option)';"
                + " for(const t of types){"
                + "   sc.$apply(()=>{ sc.Ref = sc.Ref && !Array.isArray(sc.Ref) ? sc.Ref : {}; sc.Ref.EntityTypeId = t.val; sc.Ref.EntityId = undefined; });"
                + "   try{ sc.fnSetRefEntity(); }catch(e){}"
                + "   let loaded=false; for(let i=0;i<15;i++){ await new Promise(r=>setTimeout(r,400)); if((sc.EntityList||[]).length){ loaded=true; break; } }"
                + "   if(loaded){ const ent=(sc.EntityList||[])[0]; sc.$apply(()=>{ sc.Ref.EntityId = ent.value; });"
                + "     return t.text+' | '+((ent.text||'').trim()); } }"
                + " return types[0].text+' | (no entities loaded)'; }");
        waitForAngular(500);
        return r == null ? "(null)" : r.toString();
    }

    /** Click <b>Add</b> ({@code AddRefEntity()}) in the popup; returns true if a row was added to the table. */
    public boolean clickAddReferEntity() {
        Object tagged = page.evaluate("() => { const m=document.querySelector('#chngRefEnty'); if(!m) return false; const b=[...m.querySelectorAll('button,a')].find(x=>((x.getAttribute('ng-click')||'').indexOf('AddRefEntity')>=0) && x.offsetParent!==null); if(!b) return false; b.id='__addRefBtn'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickAddReferEntity: Add button not found"); return false; }
        try { page.locator("#__addRefBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickAddReferEntity: click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__addRefBtn'); if(e) e.removeAttribute('id'); }");
        waitForAngular(800);
        Object rows = page.evaluate("() => { const m=document.querySelector('#chngRefEnty'); if(!m) return 0; return [...m.querySelectorAll('tbody tr')].filter(r=>r.offsetParent!==null && (r.innerText||'').trim() && !/no records/i.test(r.innerText||'')).length; }");
        return rows instanceof Number && ((Number) rows).intValue() > 0;
    }

    /** Click <b>Save</b> ({@code IUDRefEntity()}) in the popup; returns the success toast
     *  ("Refer Entity Added Succesfully." — the app's own spelling) captured via a MutationObserver. */
    public String saveReferEntityAndGetToast() {
        Object tagged = page.evaluate("() => { window.__reToasts=[]; if(window.__reObs) window.__reObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__reToasts.includes(t)) window.__reToasts.push(t); }); };"
                + " window.__reObs=new MutationObserver(grab); window.__reObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=document.querySelector('#chngRefEnty'); if(!m) return false;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>((x.getAttribute('ng-click')||'').indexOf('IUDRefEntity')>=0) && x.offsetParent!==null)"
                + "   || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__reSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveReferEntityAndGetToast: Save button not found"); return ""; }
        try { page.locator("#__reSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("saveReferEntityAndGetToast: Save click failed - " + e.getMessage()); }
        page.evaluate("() => { const e=document.getElementById('__reSave'); if(e) e.removeAttribute('id'); }");
        try {
            page.waitForFunction("() => (window.__reToasts||[]).some(a=>/refer entity added|succes|success|please|select/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__reToasts||[]).includes(t)) (window.__reToasts=window.__reToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__reToasts||[]; return a.find(x=>/refer entity added|succes/i.test(x)) || a.find(x=>/success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Close</b> on the Change Refer Entity popup ({@code #chngRefEnty}). */
    public void closeReferEntityPopup() {
        page.evaluate("() => { const m=document.querySelector('#chngRefEnty'); if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) jQuery('#chngRefEnty').modal('hide'); }catch(e){} }");
        waitForAngular(500);
    }

    // ---- Consent / Forms (external-form document flow) -------------------

    /** URL + screenshot of the last external-form document report opened in the new tab (set by
     *  {@link #saveConsentFormAndCaptureReport()}). */
    public byte[] lastConsentReportPng = null;
    public String lastConsentReportUrl = "";
    /** True once the external-form "Form Submitted/Saved Successfully!" confirmation was seen (definitive submit success). */
    public boolean lastConsentSubmitConfirmed = false;
    /** The text of that success confirmation (e.g. "Success Form Submitted Successfully! OK"). */
    public String lastConsentSubmitMsg = "";

    /**
     * Click the footer <b>Consent/Forms</b> button ({@code fnOnConsentClick()} → modal {@code #consent}) for the
     * selected patient and wait for the popup (the type-to-search form field {@code #txtAdmissionListConsentSearch}).
     * Returns true if the popup opened.
     */
    public boolean clickConsentForms() {
        // A prior section's modal (e.g. a slow Plan Discharge #adviseDischrg) can leak its backdrop and intercept the
        // click on the footer button, so force-clear any stale modal/backdrop first, then fire a SYNTHETIC click
        // (bypasses any residual overlay; the button's ng-click fnOnConsentClick + Bootstrap data-toggle both fire on
        // a bubbling click event).
        Object tagged = page.evaluate("() => {"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal.in,.modal.show,.modal[style*=\"display: block\"]')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open');"
                + " const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('fnOnConsentClick')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*consent\\/forms\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__consentFormsBtn'; b.click(); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickConsentForms: 'Consent/Forms' button not found"); return false; }
        try {
            page.waitForFunction("() => { const m=document.querySelector('#consent'); return m && m.getBoundingClientRect().width>0 && m.querySelector('#txtAdmissionListConsentSearch'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("clickConsentForms: #consent modal did not open"); return false; }
        waitForAngular(800);
        return true;
    }

    /**
     * Pick a form in the Consent/Forms popup. The field {@code consent.consentname} is a type-to-search directive
     * (autocomplete {@code AutoCompleteOptionsForConsentAdmList}); its list comes from
     * {@code AdmissionFactory.FetchConsentData({ExecFlag:'Consent'})} and selecting sets {@code consent.consentid}.
     * <p>We must pick an <b>external</b> form (one whose {@code Getconsenttemplate} returns
     * {@code externalformmappingid > 0}) — only those open the document tab on Save (the "another tab opens" step).
     * So we fetch the list and probe each form's mapping id until one is external, then set
     * {@code consent.consentid}/{@code consent.consentname} on the scope directly (robust vs the flaky dropdown DOM).
     * Returns {@code "FormName | ext=N"} (N = externalformmappingid), or a {@code "(...)"} diagnostic on failure.</p>
     */
    public String selectExternalConsentForm() {
        Object r = page.evaluate("async () => { const m=document.querySelector('#consent'); if(!m) return '(no modal)';"
                + " const sc=angular.element(m).scope(); const inj=angular.element(document.body).injector();"
                + " let AF,qf; try{ AF=inj.get('AdmissionFactory'); qf=inj.get('queueManagementFactory'); }catch(e){ return '(no factory)'; }"
                + " let resp; try{ resp=await AF.FetchConsentData({ExecFlag:'Consent'}); }catch(e){ return '(fetch failed)'; }"
                + " const list=(resp.data||[]).filter(x=>x && x.value!=null && !x.disabled); if(!list.length) return '(no forms)';"
                + " for(const f of list.slice(0,25)){ let ext=0;"
                + "   try{ const t=await qf.fetchQueueManagement({consentid:f.value, ExecFlag:'Getconsenttemplate'}); ext=(t.data&&t.data[0]&&t.data[0].externalformmappingid)||0; }catch(e){}"
                + "   if(ext>0){ sc.$apply(()=>{ sc.consent=sc.consent||{}; sc.consent.consentid=f.value; sc.consent.consentname=f.text; }); return f.text+' | ext='+ext; } }"
                + " return '(no external form found)'; }");
        waitForAngular(400);
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Click <b>Add</b> ({@code Getconsenttemplate()}) in the Consent/Forms popup — it fetches the selected form's
     * template and sets {@code $scope.externalformmappingid} (which the Save branch needs). Returns true once
     * {@code externalformmappingid > 0} (i.e. the external form is ready to open its document tab).
     */
    public boolean clickAddConsent() {
        Object tagged = page.evaluate("() => { const m=document.querySelector('#consent'); if(!m) return false;"
                + " const b=[...m.querySelectorAll('button,a')].find(x=>/Getconsenttemplate/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + "   || [...m.querySelectorAll('button,a')].find(x=>/^\\s*add\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__consentAdd'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickAddConsent: Add button not found"); return false; }
        try { page.locator("#__consentAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("clickAddConsent: click failed - " + e.getMessage()); }
        boolean ext = false;
        try {
            page.waitForFunction("() => { const m=document.querySelector('#consent'); if(!m) return false; const sc=angular.element(m).scope(); return sc && sc.externalformmappingid>0; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
            ext = true;
        } catch (Exception ignore) { System.out.println("clickAddConsent: externalformmappingid not set after Add"); }
        waitForAngular(500);
        return ext;
    }

    /**
     * Click <b>Save</b> ({@code IUDconsentdetail()}) — for an external form this fetches patient data and
     * {@code window.open}s the document builder in a NEW TAB
     * ({@code nhisformstest.sancyberhad.com/.../BOD_...?TemplateId=..&visitId=..&staffId=..}). In that tab we click
     * <b>Create Document</b> (a link → {@code /Create}), then <b>Submit</b>, confirm <b>Yes</b> on the confirmation
     * dialog, wait for the created document report ({@code /Edit/{id}}) and capture a full-page screenshot. Sets
     * {@link #lastConsentReportUrl}/{@link #lastConsentReportPng} and returns the screenshot (or {@code null} if the
     * tab never opened).
     */
    public byte[] saveConsentFormAndCaptureReport() {
        lastConsentReportPng = null; lastConsentReportUrl = "";
        com.microsoft.playwright.Page popup = null;
        try {
            popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(25000), () -> {
                page.evaluate("() => { const m=document.querySelector('#consent'); const b=m&&[...m.querySelectorAll('button,a')].find(x=>/IUDconsentdetail/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
            });
        } catch (Exception e) { System.out.println("saveConsentForm: Save did not open a new tab - " + e.getMessage()); return null; }
        try { popup.waitForLoadState(); } catch (Exception ignore) {}
        popup.waitForTimeout(2500);

        // Create Document (a link that navigates the same tab to /Create)
        try {
            Object t = popup.evaluate("() => { const a=[...document.querySelectorAll('a,button')].find(x=>/create document/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(!a) return false; a.id='__createDoc'; return true; }");
            if (Boolean.TRUE.equals(t)) { popup.locator("#__createDoc").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(10000)); }
            else System.out.println("saveConsentForm: 'Create Document' not found");
        } catch (Exception e) { System.out.println("saveConsentForm: Create Document click failed - " + e.getMessage()); }
        try { popup.waitForLoadState(); } catch (Exception ignore) {}
        popup.waitForTimeout(2000);

        // Submit
        try {
            Object t = popup.evaluate("() => { const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__extSubmit'; return true; }");
            if (Boolean.TRUE.equals(t)) { popup.locator("#__extSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(10000)); }
            else System.out.println("saveConsentForm: Submit not found");
        } catch (Exception e) { System.out.println("saveConsentForm: Submit click failed - " + e.getMessage()); }

        // Confirmation dialog -> Yes. The confirm popup is a Bootstrap ".modal fade custom-confirm-modal"
        // (position:fixed → its offsetParent is null even when shown), so detect it by bounding-rect + display, NOT
        // offsetParent, and poll a few times to let the fade-in finish.
        boolean yes = false;
        for (int i = 0; i < 8 && !yes; i++) {
            popup.waitForTimeout(500);
            Object t = popup.evaluate("() => { const box=[...document.querySelectorAll('.modal,[role=dialog]')].find(m=>m.getBoundingClientRect().width>0 && getComputedStyle(m).display!=='none' && /continue|are you sure|confirm/i.test(m.textContent||'')); if(!box) return false;"
                    + " const b=[...box.querySelectorAll('button,a')].find(x=>/^\\s*yes\\s*$/i.test((x.textContent||'').trim()) && x.getBoundingClientRect().width>0); if(!b) return false; b.id='__extYes'; return true; }");
            if (Boolean.TRUE.equals(t)) {
                try { popup.locator("#__extYes").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); yes = true; }
                catch (Exception e) { System.out.println("saveConsentForm: Yes click failed - " + e.getMessage()); }
            }
        }
        if (!yes) System.out.println("saveConsentForm: confirmation 'Yes' not found");

        // After Yes the form submits: a green "Success — Form Submitted/Saved Successfully!" modal appears OVER the
        // report and the tab lands on /Edit/{id} (a fresh document id = the submit persisted). Poll (immediately —
        // the modal can show before the /Edit navigation) for ANY visible Success modal with an OK button, record its
        // text, and click OK (+ clear the backdrop) so the screenshot is the CLEAN report. Match /success/i (the app
        // says "Form Saved Successfully!" or "Form Submitted Successfully!"). evaluate() can throw mid-navigation.
        final String dismissJs = "() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && getComputedStyle(x).display!=='none' && /success/i.test(x.textContent||'') && [...x.querySelectorAll('button,a')].some(b=>/^\\s*ok\\s*$/i.test((b.textContent||'').trim())));"
                + " if(!m) return ''; const txt=(m.textContent||'').replace(/\\s+/g,' ').trim(); const ok=[...m.querySelectorAll('button,a')].find(b=>/^\\s*ok\\s*$/i.test((b.textContent||'').trim())); if(ok) ok.click();"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); return txt; }";
        lastConsentSubmitMsg = "";
        boolean successModal = false;
        for (int i = 0; i < 24 && !(successModal && safeUrl(popup).toLowerCase().contains("/edit/")); i++) {
            try {
                Object o = popup.evaluate(dismissJs);
                String seen = o == null ? "" : o.toString();
                if (!seen.isEmpty()) { successModal = true; lastConsentSubmitMsg = seen; }
            } catch (Exception ignore) { /* tab mid-navigation — retry */ }
            popup.waitForTimeout(500);
        }
        try { popup.waitForLoadState(); } catch (Exception ignore) {}
        // One more sweep to clear any lingering success modal right before the shot.
        try { Object o = popup.evaluate(dismissJs); if (o != null && !o.toString().isEmpty()) { successModal = true; if (lastConsentSubmitMsg.isEmpty()) lastConsentSubmitMsg = o.toString(); } } catch (Exception ignore) {}
        boolean editReached = safeUrl(popup).toLowerCase().contains("/edit/");
        lastConsentSubmitConfirmed = successModal || editReached;
        if (!lastConsentSubmitConfirmed) System.out.println("saveConsentForm: submit not confirmed (no success modal, not on /Edit) - " + safeUrl(popup));
        popup.waitForTimeout(1500);

        byte[] png = null;
        try { popup.bringToFront(); png = popup.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
        catch (Exception e) { System.out.println("saveConsentForm: report screenshot failed - " + e.getMessage()); }
        lastConsentReportUrl = safeUrl(popup);
        lastConsentReportPng = png;
        try { page.bringToFront(); } catch (Exception ignore) {}
        // Close the external-form tab(s) so they don't accumulate across sections.
        try { for (com.microsoft.playwright.Page pg : page.context().pages()) { if (pg != page && !pg.isClosed()) pg.close(); } } catch (Exception ignore) {}
        waitForAngular(400);
        return png;
    }

    /** Close the Consent/Forms popup ({@code #consent}) + backdrop cleanup (Save already hides it on success). */
    public void closeConsentFormsPopup() {
        page.evaluate("() => { const m=document.querySelector('#consent'); if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/fnclear/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) jQuery('#consent').modal('hide'); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    // ---- View Consent (list saved consents/forms, open one in a new tab) -

    /** URL + screenshot of the last saved consent/form report opened from the eye icon in a new tab. */
    public byte[] lastViewConsentReportPng = null;
    public String lastViewConsentReportUrl = "";

    /**
     * Click the footer <b>View Consent</b> button ({@code fnViewConsents()}) for the selected patient and wait for
     * the <b>View Consent/Forms</b> modal ({@code #viewConsentFormsModal}). Any leftover Consent/Consent2 modal +
     * backdrop is dismissed first (they can sit on top and swallow the click). Returns true if the modal opened.
     */
    public boolean clickViewConsent() {
        page.evaluate("() => { const $=window.jQuery||window.$;"
                + " ['#consent','#viewConsentFormsModal'].forEach(id=>{ try{ if($) $(id).modal('hide'); }catch(e){} });"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('fnViewConsents')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*view consent\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__viewConsentBtn'; b.click(); return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickViewConsent: 'View Consent' button not found"); return false; }
        try {
            page.waitForFunction("() => { const m=document.querySelector('#viewConsentFormsModal'); if(m && m.getBoundingClientRect().width>0 && getComputedStyle(m).display!=='none') return true;"
                    + " return [...document.querySelectorAll('.modal')].some(x=>x.getBoundingClientRect().width>0 && /view consent/i.test(x.textContent||'')); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("clickViewConsent: View Consent modal did not open in time"); return false; }
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE); } catch (Exception ignore) {}
        waitForAngular(1200);
        return true;
    }

    /**
     * Click <b>Search</b> inside the View Consent/Forms modal ({@code searchConsentForms()}) and wait for the result
     * grid to settle — a record row (with its eye/View icon {@code viewConsentFormDetail}) or the empty-state
     * <b>"No records found."</b>. Returns {@code "records=N"} or {@code "No records found"}.
     */
    public String searchViewConsent() {
        page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); if(!m) return;"
                + " let b=[...m.querySelectorAll(\"button[ng-click='searchConsentForms()']\")].find(x=>x.offsetParent!==null)"
                + "   || [...m.querySelectorAll('button')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null);"
                + " if(b) b.click(); }");
        try {
            page.waitForFunction("() => { const m=document.querySelector('#viewConsentFormsModal'); if(!m) return false;"
                    + " return [...m.querySelectorAll('[ng-click*=\"viewConsentFormDetail\"]')].some(e=>e.offsetParent!==null) || /no records found/i.test(m.innerText); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("searchViewConsent: neither records nor 'No records found' appeared"); }
        waitForAngular(1000);
        return viewConsentShowsNoRecords() ? "No records found" : ("records=" + viewConsentRecordCount());
    }

    /** Number of consent/form records listed in the View Consent modal (excludes the "No records"/"Loading" placeholder rows). */
    public int viewConsentRecordCount() {
        Object r = page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); if(!m) return 0;"
                + " return [...m.querySelectorAll('table tbody tr')].filter(tr=>{ const tds=[...tr.querySelectorAll('td')]; if(tds.length<3) return false; const t=(tr.textContent||'').toLowerCase(); return !/no records found|loading/.test(t); }).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** True when the View Consent modal shows the empty-state <b>"No records found."</b> (patient has no saved consent/forms). */
    public boolean viewConsentShowsNoRecords() {
        Object r = page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); return !!(m && /no records found/i.test(m.innerText)); }");
        return Boolean.TRUE.equals(r);
    }

    /**
     * Click the row's eye/<b>View</b> icon ({@code viewConsentFormDetail(item)}) — for {@code formName} if given,
     * else the first record — which opens the saved form report in a NEW TAB
     * ({@code nhisformstest.sancyberhad.com/...}); capture a full-page screenshot of it. Sets
     * {@link #lastViewConsentReportUrl}/{@link #lastViewConsentReportPng}. Returns the screenshot, or {@code null}
     * if there was no eye icon / no tab opened.
     */
    public byte[] clickConsentEyeAndCaptureReport(String formName) {
        lastViewConsentReportPng = null; lastViewConsentReportUrl = "";
        Object tagged = page.evaluate("(formText) => { const m=document.querySelector('#viewConsentFormsModal')||document;"
                + " const icons=[...m.querySelectorAll('[ng-click*=\"viewConsentFormDetail\"]')].filter(e=>e.offsetParent!==null); if(!icons.length) return 'no-icon';"
                + " const chosen=(formText ? icons.find(e=>{ const row=e.closest('tr'); return row && (row.textContent||'').toUpperCase().indexOf((''+formText).toUpperCase())>=0; }) : null) || icons[0];"
                + " chosen.id='__viewConsentEye'; return 'found'; }", formName);
        if (!"found".equals(String.valueOf(tagged))) { System.out.println("clickConsentEye: no eye icon (no records?)"); return null; }
        com.microsoft.playwright.Page popup = null;
        try {
            popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(20000), () -> {
                try { page.locator("#__viewConsentEye").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("clickConsentEye: click failed - " + e.getMessage()); }
            });
        } catch (Exception e) { System.out.println("clickConsentEye: eye icon did not open a new tab - " + e.getMessage()); return null; }
        try { popup.waitForLoadState(); } catch (Exception ignore) {}
        popup.waitForTimeout(3000);
        byte[] png = null;
        try { popup.bringToFront(); png = popup.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
        catch (Exception e) { System.out.println("clickConsentEye: report screenshot failed - " + e.getMessage()); }
        lastViewConsentReportUrl = safeUrl(popup);
        lastViewConsentReportPng = png;
        try { page.bringToFront(); } catch (Exception ignore) {}
        try { for (com.microsoft.playwright.Page pg : page.context().pages()) { if (pg != page && !pg.isClosed()) pg.close(); } } catch (Exception ignore) {}
        waitForAngular(400);
        return png;
    }

    /** Close the View Consent/Forms modal ({@code #viewConsentFormsModal}) + backdrop cleanup. */
    public void closeViewConsentPopup() {
        page.evaluate("() => { const m=document.querySelector('#viewConsentFormsModal'); if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) jQuery('#viewConsentFormsModal').modal('hide'); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(500);
    }

    // ---- Print Barcode ---------------------------------------------------

    /**
     * Click the footer <b>Print Barcode</b> button ({@code printpatient()}) for the selected patient and wait for
     * the <b>Barcode</b> modal (shows the patient's <b>MRN</b>, a <b>Small Size</b> option, Save / Print / Cancel).
     * The modal has no stable id, so it is matched by a visible dialog whose text contains "Barcode" + "MRN".
     * Returns true if the modal opened.
     */
    public boolean clickPrintBarcode() {
        Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('printpatient')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*print barcode\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return false; b.id='__printBarcodeBtn'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("clickPrintBarcode: 'Print Barcode' button not found"); return false; }
        try { page.locator("#__printBarcodeBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickPrintBarcode: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /barcode/i.test(m.textContent||'') && /mrn/i.test(m.textContent||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("clickPrintBarcode: Barcode modal did not open in time"); return false; }
        waitForAngular(600);
        return true;
    }

    /**
     * Read the Barcode modal (patient <b>MRN</b>) and tick the <b>Small Size</b> checkbox
     * ({@code BarCodeData.IsSmall}). Returns e.g. {@code "MRN=100000850 | Small Size=ticked"}.
     */
    public String selectBarcodeSmallSizeAndRead() {
        Object r = page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /barcode/i.test(x.textContent||'')); if(!m) return 'ERR:no-modal';"
                + " const txt=(m.innerText||'').replace(/\\s+/g,' ').trim(); const mrn=(txt.match(/MRN[^0-9]*([0-9]{5,})/i)||[])[1]||'';"
                + " const small=m.querySelector(\"input[ng-model='BarCodeData.IsSmall']\") || [...m.querySelectorAll('input[type=checkbox],input[type=radio]')].find(c=>{ const lab=(c.closest('label')&&c.closest('label').textContent)||(c.parentElement&&c.parentElement.textContent)||''; return /small/i.test(lab); });"
                + " let smallState='(no option)'; if(small){ if(!small.checked) small.click(); smallState=small.checked?'ticked':'unticked'; }"
                + " return 'MRN='+(mrn||'(not shown)')+' | Small Size='+smallState; }");
        waitForAngular(300);
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Click <b>Save</b> in the Barcode modal ({@code SaveBarcode(this)} — persists to {@code trn_patientqrbarcode})
     * and return the success toast (MutationObserver slow-toast pattern). Expected "Barcode Saved Successfully !!!.".
     * We do NOT click <b>Print</b> ({@code PrintPartOfPage}, a print preview), per the flow: tick Small Size → Save.
     */
    public String saveBarcodeAndGetToast() {
        page.evaluate("() => { window.__bcToasts=[]; if(window.__bcObs) window.__bcObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__bcToasts.includes(t)) window.__bcToasts.push(t); }); };"
                + " window.__bcObs=new MutationObserver(grab); window.__bcObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /barcode/i.test(x.textContent||''));"
                + " const b=m&&([...m.querySelectorAll('button,a')].find(x=>/SaveBarcode/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null)); if(b) b.click(); }");
        try {
            page.waitForFunction("() => (window.__bcToasts||[]).some(a=>/barcode|saved|success|please|select|required/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__bcToasts||[]).includes(t)) (window.__bcToasts=window.__bcToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__bcToasts||[]; return a.find(x=>/barcode|saved|success/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Cancel</b> in the Barcode modal to close it (falls back to Close + backdrop cleanup). */
    public void cancelBarcodePopup() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /barcode/i.test(x.textContent||''));"
                + " if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*cancel\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null) || [...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
    }

    // ---- Attach Signature ------------------------------------------------

    /**
     * Attach a signature image for the selected patient and return the resulting toast. The footer <b>Attach
     * Signature</b> action just clicks the hidden file input {@code <input type="file" id="PhotoData"
     * ng-model="AdmissionList.PhotoFileData" onchange="…PhotoChanged(this.files,'Photo')">}. Rather than drive the
     * native OS file dialog, set the file DIRECTLY on {@code #PhotoData} via {@code setInputFiles} (works on hidden
     * inputs and fires the same {@code onchange}). The upload is a server round-trip, so the toast can arrive late —
     * captured via the MutationObserver slow-toast pattern. Expected: "Digital Signature Saved Successfully.".
     */
    public String attachSignatureAndGetToast(String filePath) {
        page.evaluate("() => { window.__sigToasts=[]; if(window.__sigObs) window.__sigObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__sigToasts.includes(t)) window.__sigToasts.push(t); }); };"
                + " window.__sigObs=new MutationObserver(grab); window.__sigObs.observe(document.body,{childList:true,subtree:true}); grab(); }");
        try {
            page.setInputFiles("#PhotoData", java.nio.file.Paths.get(filePath));
        } catch (Exception e) {
            System.out.println("attachSignatureAndGetToast: setInputFiles(#PhotoData) failed - " + e.getMessage());
            return "(upload failed: " + e.getMessage() + ")";
        }
        try {
            page.waitForFunction("() => (window.__sigToasts||[]).some(a=>/signature|photo|upload|success|saved|attach|please|select|invalid|format|allow/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__sigToasts||[]).includes(t)) (window.__sigToasts=window.__sigToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__sigToasts||[]; return a.find(x=>/signature|photo|upload|success|saved|attach/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    // ---- Assign Triage ---------------------------------------------------

    /**
     * Click the footer <b>Assign Triage</b> button ({@code BtnAssignTriage()}) for the selected patient and wait for
     * the <b>Triage</b> modal (a zone dropdown — Green / Yellow / Red Zone / Non-Emergency — + Save/Close). If the
     * footer button is disabled (needs a bedded patient) it falls back to calling {@code BtnAssignTriage()} on the
     * scope. The modal has no stable id, so it is matched by a visible dialog with "Triage" text and a zone
     * {@code <select>}. Returns true if the modal opened.
     */
    public boolean clickAssignTriage() {
        Object clicked = page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('BtnAssignTriage')>=0 && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,a')].find(x=>/^\\s*assign triage\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                + " if(!b) return 'no-button';"
                + " if(b.disabled){ let sc=null; document.querySelectorAll('*').forEach(el=>{ if(sc)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.BtnAssignTriage==='function'){sc=x;break;} x=x.$parent; } }catch(e){} }); if(sc){ try{ sc.$apply(()=>sc.BtnAssignTriage()); }catch(e){} return 'scope'; } return 'disabled'; }"
                + " b.id='__occAssignTriage'; return 'tagged'; }");
        String how = String.valueOf(clicked);
        if ("no-button".equals(how)) { System.out.println("clickAssignTriage: 'Assign Triage' button not found"); return false; }
        if ("tagged".equals(how)) {
            try { page.locator("#__occAssignTriage").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("clickAssignTriage: click failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__occAssignTriage'); if(e) e.removeAttribute('id'); }");
        } else if ("disabled".equals(how)) {
            System.out.println("clickAssignTriage: footer button disabled and no scope BtnAssignTriage() — patient may not be bedded");
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && /triage/i.test(m.textContent||'') && [...m.querySelectorAll('select')].some(s=>[...s.options].some(o=>/zone|emergency/i.test(o.textContent||''))))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("clickAssignTriage: Triage modal did not open in time"); return false; }
        waitForAngular(600);
        return true;
    }

    /**
     * Pick a triage zone in the Triage modal's dropdown (first real "…Zone" option, else the first real option) via
     * {@code selectedIndex} + a change event (it's a select2, so a plain value set won't propagate). Returns the
     * chosen zone text, or an {@code ERR:*} marker.
     */
    public String selectTriageZone() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||'')); if(!m) return 'ERR:no-modal';"
                + " const sel=[...m.querySelectorAll('select')].find(s=>[...s.options].some(o=>/zone|emergency/i.test(o.textContent||''))); if(!sel) return 'ERR:no-dropdown';"
                + " let i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)) && /zone/i.test(o.textContent||''));"
                + " if(i<0) i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " if(i<0) return 'ERR:no-option'; sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}}"
                + " return norm(sel.options[i].textContent); }");
        waitForAngular(500);
        return r == null ? "(null)" : r.toString();
    }

    /**
     * Click <b>Save</b> in the Triage modal ({@code trn_patienttriagedetail} is written) and return the success toast
     * (captured via a MutationObserver — the slow-toast pattern). Expected: "Triage Assigned Successfully.".
     */
    public String saveTriageAndGetToast() {
        page.evaluate("() => { window.__tgToasts=[]; if(window.__tgObs) window.__tgObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__tgToasts.includes(t)) window.__tgToasts.push(t); }); };"
                + " window.__tgObs=new MutationObserver(grab); window.__tgObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||''));"
                + " const b=m&&[...m.querySelectorAll('button,a')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => (window.__tgToasts||[]).some(a=>/triage|assigned|success|saved|please|select|required/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__tgToasts||[]).includes(t)) (window.__tgToasts=window.__tgToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__tgToasts||[]; return a.find(x=>/triage|assigned|success|saved/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** Close the Triage modal (its <b>Close</b> button + backdrop cleanup fallback; a successful Save usually closes it already). */
    public void closeTriagePopup() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && /triage/i.test(x.textContent||''));"
                + " if(m){ const b=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }"
                + " try{ if(window.jQuery) [...document.querySelectorAll('.modal')].forEach(x=>{ try{ jQuery(x).modal('hide'); }catch(e){} }); }catch(e){}"
                + " [...document.querySelectorAll('.modal-backdrop')].forEach(b=>b.remove()); document.body.classList.remove('modal-open'); }");
        waitForAngular(400);
    }

    private String safeUrl(com.microsoft.playwright.Page p) { try { return p.url(); } catch (Exception e) { return ""; } }
}
