package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Bed Released Checklist</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → (Admission) → <b>Bed Released Checklist</b> → select any record in the
 * table → <b>Add</b> → enter <b>Code</b> + <b>Remark</b> → tick the mandatory checkbox → <b>Submit</b> → toast.</p>
 *
 * <p>New screen — route, field ng-models and the Submit handler are discovered at runtime and then wired.</p>
 */
public class BedReleasedChecklist extends BasePage {

    public BedReleasedChecklist(Page page) { super(page); }

    /** Route — captured at runtime by mining the menu href (config menu links are often hidden). */
    public static String ROUTE = "";
    public String lastCode = "";

    // ---- discovery -------------------------------------------------------

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " const grids=[...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null);"
                + " out.push('tables='+grids.length+' ui-grids='+document.querySelectorAll('[ui-grid],.ui-grid').length);"
                + " grids.slice(0,4).forEach((t,i)=>{ const h=norm((t.querySelector('thead')||{}).innerText||'').slice(0,120); const rows=[...t.querySelectorAll('tbody tr')].filter(r=>norm(r.textContent) && !/no records|no data/i.test(r.textContent)).length; out.push(' grid'+i+' rows='+rows+' headers=['+h+']'); });"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+(b.getAttribute('ng-click')?(' {'+b.getAttribute('ng-click')+'}'):'')).filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,40).join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public String dumpAddForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let t=''; if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=l.textContent; } if(!t){ const g=e.closest('.form-group,.row,.form-line,.col-sm-6,.col-md-6,.col-sm-4,td,div'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t=l.textContent; } } return norm(t).slice(0,30); };"
                + " const out=[];"
                + " const inputs=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden');"
                + " inputs.slice(0,40).forEach(e=>{ out.push(e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'); });"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null).map(c=>(c.getAttribute('ng-model')||'?')+' lbl=\"'+labelOf(c)+'\"').filter((v,i,a)=>a.indexOf(v)===i);"
                + " out.push('checkboxes=\\n  '+cbs.slice(0,10).join('\\n  '));"
                + " const subs=[...document.querySelectorAll('button,input[type=submit]')].filter(b=>b.offsetParent!==null && /submit|save/i.test(b.textContent||b.value||'')).map(b=>norm(b.textContent||b.value)+' {'+(b.getAttribute('ng-click')||'')+'}');"
                + " out.push('submit/save buttons=\\n  '+subs.join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- navigation ------------------------------------------------------

    /** Expand <b>Application Configuration</b> → (Admission) → <b>Bed Released Checklist</b>; recovers the route by
     *  mining the menu href when the link is hidden (collapsed sidebar). */
    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        Object appCfg = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__appCfgMenu'; return true; }");
        if (Boolean.TRUE.equals(appCfg)) {
            try { page.locator("#__appCfgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BedReleasedChecklist.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // real-click the visible "Bed Released Checklist" link if present (also capture its route)
        Object bcHref = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/bed\\s*released?\\s*check\\s*list/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__brcMenu'; return a.getAttribute('href')||'link'; }");
        if (bcHref != null && !bcHref.toString().isEmpty()) {
            try { page.locator("#__brcMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BedReleasedChecklist.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
            if (bcHref.toString().contains("#/")) ROUTE = bcHref.toString().substring(bcHref.toString().indexOf("#/"));
        }
        // mine ANY anchor (even hidden) whose text/href matches released/checklist
        if (ROUTE.isEmpty()) {
            Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/bed\\s*released?\\s*check|releasechecklist|releasedchecklist|bedrelease/i.test(norm(x.textContent)) || /release.*check|bedrelease/i.test(x.getAttribute('href')||''));"
                    + " return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("BedReleasedChecklist.nav: mined href => " + href);
            if (href != null && href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        }
        // direct-route fallback
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("BedReleasedChecklist.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /bed\\s*released?\\s*check/i.test(document.body.innerText||'')"));
    }

    // ---- actions ---------------------------------------------------------

    /** Select any one record in the (possibly virtualized ui-grid) table — scroll into view, poll for rows, then tick
     *  the row checkbox or real-click the row. Returns the row label, or "" if none. */
    public String selectFirstRecord() {
        String rowText = null;
        for (int i = 0; i < 20 && rowText == null; i++) {
            Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const g=document.querySelector('.ui-grid-viewport,.ui-grid,[ui-grid]'); if(g && g.scrollIntoView) g.scrollIntoView({block:'center'});"
                    + " const rows=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row, table tbody tr')].filter(r=>r.offsetParent!==null && norm(r.textContent) && !/no records|no data/i.test(r.textContent));"
                    + " if(!rows.length) return null;"
                    + " const row=rows[0];"
                    + " const cb=row.querySelector('input[type=checkbox],input[type=radio]'); if(cb){ cb.id='__brcRowCb'; } else { const cell=row.querySelector('.ui-grid-cell-contents,td'); if(cell) cell.id='__brcRowCell'; }"
                    + " return norm(([...row.querySelectorAll('.ui-grid-cell-contents,td')].map(c=>norm(c.textContent)).find(x=>x && !/^\\d+$/.test(x))) || norm(row.textContent)).slice(0,40); }");
            if (r != null) rowText = r.toString();
            else page.waitForTimeout(600);
        }
        if (rowText == null) { System.out.println("selectFirstRecord: no grid rows rendered"); return ""; }
        boolean cb = Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector('#__brcRowCb')"));
        String sel = cb ? "#__brcRowCb" : "#__brcRowCell";
        try { page.locator(sel).click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectFirstRecord: click failed - " + e.getMessage()); }
        waitForAngular(300);
        return rowText;
    }

    /** Real-click the <b>Add</b> button (polls — it may render after the grid). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button],span,i')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('[ng-click]')].find(x=>/add/i.test(x.getAttribute('ng-click')||'') && /add/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__brcAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("BedReleasedChecklist.clickAdd: Add button not found"); return false; }
        try { page.locator("#__brcAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("BedReleasedChecklist.clickAdd: click failed - " + e.getMessage()); }
        waitForAngular(1200);
        return true;
    }

    /** Realistic checklist-item names — a retry after "already exists" must still read like real data, never
     *  "Auto checklist &lt;CODE&gt;". */
    private static final String[] REMARKS = {
            "Housekeeping Cleared", "Linen Changed", "Equipment Removed", "Biomedical Waste Disposed",
            "Room Sanitized", "Maintenance Checked", "Nurse Sign-off Completed", "Discharge Summary Filed"
    };
    public String lastRemark = "";

    /** Enter <b>Code</b> (unique) + <b>Remark</b> (by label). Returns a summary incl. the discovered ng-models.
     *  {@code attempt} shifts both values so a retry after an "already exists" toast submits genuinely different
     *  details. */
    public String fillCodeAndRemark(int attempt) {
        String code = "RC" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        Object r = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const labelField=(labels)=>{ for(const label of labels){ const want=label.toLowerCase();"
                + "   const lbls=[...document.querySelectorAll('label,.control-label,span,th,td,b')].filter(e=>e.offsetParent!==null && norm(e.textContent).replace(/\\*/g,'').trim().toLowerCase()===want);"
                + "   for(const l of lbls){ const g=l.closest('.form-group,.row,.col-sm-6,.col-md-6,.col-sm-4,td,tr,div'); const f=g?g.querySelector('input:not([type=hidden]),textarea'):null; if(f&&f.offsetParent!==null) return f; const p=l.parentElement?l.parentElement.querySelector('input:not([type=hidden]),textarea'):null; if(p&&p.offsetParent!==null) return p; } } return null; };"
                + " const setInp=(e,v)=>{ if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const codeF=labelField(['Code','Checklist Code','Bed Released Checklist Code']);"
                + " const remF=labelField(['Remark','Remarks','Description']);"
                + " const cd=setInp(codeF, a.code); const rm=setInp(remF, a.remark);"
                + " return 'Code='+cd+' | Remark='+rm+' | codeNg='+(codeF?codeF.getAttribute('ng-model'):'-')+' | remNg='+(remF?remF.getAttribute('ng-model'):'-'); }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Tick the mandatory checkbox on the Add form (a required/labelled "Mandatory" checkbox, else the first visible
     *  form checkbox). Real-click so the Angular binding fires. Returns the checkbox label / ng-model, or "". */
    public String selectMandatoryCheckbox() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let t=''; const g=e.closest('.form-group,.row,.col-sm-6,.col-md-6,td,div,label'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t=l.textContent; } return norm(t||(e.parentElement?e.parentElement.textContent:'')).slice(0,30); };"
                + " const cbs=[...document.querySelectorAll('input[type=checkbox]')].filter(c=>c.offsetParent!==null && !/logbookfilters|colfilter/i.test(c.getAttribute('ng-model')||''));"
                + " if(!cbs.length) return 'none';"
                + " let cb=cbs.find(c=>/mandat|require/i.test(labelOf(c)+' '+(c.getAttribute('ng-model')||''))) || cbs.find(c=>!c.checked) || cbs[0];"
                + " cb.id='__brcMandCb'; return (labelOf(cb)||(cb.getAttribute('ng-model')||'checkbox')); }");
        String label = String.valueOf(r);
        if ("none".equals(label)) { System.out.println("selectMandatoryCheckbox: no form checkbox found"); return ""; }
        try { page.locator("#__brcMandCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("selectMandatoryCheckbox: click failed - " + e.getMessage()); }
        waitForAngular(300);
        boolean checked = Boolean.TRUE.equals(page.evaluate("() => { const c=document.querySelector('#__brcMandCb'); return !!(c && c.checked); }"));
        return checked ? label : "";
    }

    /** Click <b>Submit</b> and return the toast (success or a server error). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__brcToasts=[]; if(window.__brcObs) window.__brcObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__brcToasts.includes(t)) window.__brcToasts.push(t); }); };"
                + " window.__brcObs=new MutationObserver(grab); window.__brcObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__brcSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__brcSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__brcToasts||[]).some(a=>/checklist|master|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__brcToasts||[]).includes(t)) (window.__brcToasts=window.__brcToasts||[]).push(t); }); }");
        }
        // prefer the Submit's own save toast ("... details added successfully.") over the row-select "Status updated".
        Object r = page.evaluate("() => { const a=window.__brcToasts||[]; return a.find(x=>/details added|added successfully|master saved|saved successfully/i.test(x)) || a.find(x=>/status updated|success|added|updated/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
