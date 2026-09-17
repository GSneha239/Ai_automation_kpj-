package com.kpj.pages.Emergency_page;

import com.kpj.pages.Op_page.OutPatientQueueManagementPage;
import com.microsoft.playwright.Page;

/**
 * Emergency &gt; <b>Emergency Visits</b> — Page Object (route {@code #/QueueManagement}).
 *
 * <p>Emergency Visits is the SAME queue screen as OP Outpatient Queue Management — identical {@code queue.*}
 * search models, the same ui-grid, and the same footer actions (Generate Queue, New Case, Patient Task, Change
 * Doctor, Close/Revoke Visit, Assign Triage, Call Patient …). It is just reached from the <b>Emergency</b> menu.
 * This page therefore <b>extends {@link OutPatientQueueManagementPage}</b> to inherit every queue method, and only
 * overrides {@link #navigateTo(String)} to enter via the Emergency menu (falling back to the direct route).</p>
 */
public class EmergencyVisits extends OutPatientQueueManagementPage {

    public EmergencyVisits(Page page) { super(page); }

    /**
     * Click Emergency → Emergency Visits (menu), retrying up to 3 times.
     *
     * <p><b>Do NOT fall back to {@link OutPatientQueueManagementPage#navigateTo}</b> (a direct hash
     * navigate to {@code #/queueManagement}) — verified live that the SAME route renders a completely
     * DIFFERENT, generic screen when reached that way: {@code document.title} is
     * "OUTPATIENT QUEUE MANAGEMENT" instead of "Emergency Visits", the columns are the generic OP set
     * (MRN No./Prefix/Passport No/... — no Triage/EMR/Waiting Time), and it returns <b>0 rows</b> for
     * every search regardless of real data. The old fallback silently landed tests on this wrong screen
     * whenever the menu click was slow, which is what produced misleading "no queue activity" results
     * even though the real Emergency Visits screen had matching rows all along. So: only "search button
     * present" is not enough to confirm success (both screens have one) — check {@code document.title}
     * instead, retry the menu click on a failure, and fail loudly if it never lands correctly.</p>
     */
    @Override
    public void navigateTo(String baseUrl) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (attempt > 1) {
                try {
                    page.navigate(baseUrl, new Page.NavigateOptions()
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(60000));
                } catch (Exception e) { System.out.println("EmergencyVisits.navigateTo: retry " + attempt + " reload slow — continuing"); }
                waitForAngular(1500);
            }
            try {
                page.waitForFunction("() => window.angular && [...document.querySelectorAll('li > a')].some(a=>/^\\s*Emergency\\s*$/i.test((a.textContent||'').trim()))",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
            } catch (Exception ignore) { }
            // Expand the Emergency menu, then click the Emergency Visits link.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const em=[...document.querySelectorAll('li > a')].find(a=>/^\\s*Emergency\\s*$/i.test(norm(a.textContent))); if(em) em.click(); }");
            waitForAngular(800);
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/emergency visits/i.test(norm(x.textContent)) && x.offsetParent!==null)"
                    + "   || [...document.querySelectorAll('a')].find(x=>/queuemanagement/i.test(x.getAttribute('href')||'') && x.offsetParent!==null); if(a) a.click(); }");
            try {
                page.waitForFunction(
                        "() => window.angular && /queuemanagement/i.test(location.hash) && /emergency visits/i.test(document.title||'')",
                        null, new Page.WaitForFunctionOptions().setTimeout(20000));
                waitForAngular(1500);
                return; // confirmed on the real Emergency Visits screen, not the generic lookalike
            } catch (Exception e) {
                String title = String.valueOf(page.evaluate("() => document.title"));
                System.out.println("EmergencyVisits.navigateTo: attempt " + attempt + "/3 landed on \"" + title
                        + "\" instead of Emergency Visits — retrying");
                lastFailure = new RuntimeException("EmergencyVisits.navigateTo: could not reach the Emergency Visits screen "
                        + "after 3 attempts (kept landing on \"" + title + "\" instead). This is the generic Queue Management "
                        + "view of the same route and always returns 0 rows for this search — do not treat that as a data gap.");
            }
        }
        throw lastFailure;
    }

    // ---- Return MRD File (Emergency Visits) -------------------------------

    /** Select the first patient that HAS an MRD file ({@code mrdfileno} set) so Return MRD File opens its popup.
     *  Falls back to the first row. Returns the name, or null. */
    public String selectQueueRowWithMrdFile() {
        Object r = page.evaluate("() => { const skip=/dummy|test\\s*emr/i; let gridApi=null, data=null;"
                + " document.querySelectorAll('*').forEach(el=>{ if(gridApi&&data) return; try{ const s=angular.element(el).scope(); if(s&&s.grid&&s.grid.api&&s.grid.options&&s.grid.options.data&&s.grid.options.data.length){gridApi=s.grid.api;data=s.grid.options.data;} }catch(e){} });"
                + " if(!gridApi||!data||!data.length) return null; const nm=x=>((x.PatientName||x.patientname||'')+'').trim();"
                + " const p=data.find(x=>nm(x)&&!skip.test(nm(x)) && x.mrdfileno && ((''+x.mrdfileno).trim())) || data.find(x=>nm(x)&&!skip.test(nm(x))) || data[0];"
                + " try{gridApi.selection.clearSelectedRows();}catch(e){} gridApi.selection.selectRow(p); return nm(p); }");
        waitForAngular(500);
        return r == null ? null : r.toString();
    }

    /** Click the <b>Return MRD File</b> footer button ({@code fnReturnMRDFile()}) and wait for the MRD Return
     *  popup (its OK button is {@code fnIUDReturn()}). Returns true if it opened (needs a patient WITH a file). */
    public boolean clickReturnMrdFile() {
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('fnReturnMRDFile')>=0 && x.offsetParent!==null); if(b) b.click(); }");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.modal,[role=dialog]')].some(m=>m.getBoundingClientRect().width>0 && [...m.querySelectorAll('button')].some(b=>/fnIUDReturn/.test(b.getAttribute('ng-click')||'')))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { System.out.println("clickReturnMrdFile: MRD Return modal did not open (patient may have no file to return)"); return false; }
        waitForAngular(600);
        return true;
    }

    /**
     * Fill the <b>MRD Return</b> popup — From Department / To Department / User (first real option each) + a
     * Remark — then click <b>OK</b> ({@code fnIUDReturn()}) and return the success toast (verified live:
     * <b>"File Return successfully."</b>).
     */
    public String fillReturnMrdAndGetToast() {
        page.evaluate("() => { const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && [...x.querySelectorAll('button')].some(b=>/fnIUDReturn/.test(b.getAttribute('ng-click')||''))); if(!m) return;"
                + " const pick=(ng)=>{ const e=[...m.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(x){}} };"
                + " pick('MRDReturn.fromdepartmentid'); pick('MRDReturn.todepartmentid'); pick('MRDReturn.userid');"
                + " const rem=[...m.querySelectorAll('textarea,input')].find(x=>x.getAttribute('ng-model')==='MRDReturn.remark'); if(rem){ const c=angular.element(rem).controller('ngModel'); rem.value='Returned - automated test'; if(c){c.$setViewValue('Returned - automated test');c.$render();} rem.dispatchEvent(new Event('input',{bubbles:true})); rem.dispatchEvent(new Event('change',{bubbles:true})); } }");
        waitForAngular(500);
        page.evaluate("() => { window.__rmToasts=[]; if(window.__rmObs) window.__rmObs.disconnect();"
                + " window.__rmObs=new MutationObserver(()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rmToasts.includes(t)) window.__rmToasts.push(t); }); }); window.__rmObs.observe(document.body,{childList:true,subtree:true});"
                + " const m=[...document.querySelectorAll('.modal,[role=dialog]')].find(x=>x.getBoundingClientRect().width>0 && [...x.querySelectorAll('button')].some(b=>/fnIUDReturn/.test(b.getAttribute('ng-click')||'')));"
                + " const ok=m&&[...m.querySelectorAll('button')].find(x=>/fnIUDReturn/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(ok) ok.click(); }");
        for (int i = 0; i < 4; i++) {
            boolean c = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want|are you sure/i.test(m.textContent||''))"));
            if (!c) break;
            page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want|are you sure/i.test(m.textContent||'')); const b=[...(box||document).querySelectorAll('button')].find(x=>/^(yes|ok)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(b) b.click(); }");
            waitForAngular(500);
        }
        try {
            page.waitForFunction("() => (window.__rmToasts||[]).some(a=>/return|success|saved/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("fillReturnMrdAndGetToast: no toast observed"); }
        Object r = page.evaluate("() => { const a=window.__rmToasts||[]; return a.find(x=>/return|success|saved/i.test(x)) || a[0] || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
