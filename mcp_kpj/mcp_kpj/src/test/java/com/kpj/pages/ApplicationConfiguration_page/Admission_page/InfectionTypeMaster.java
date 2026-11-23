package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Infection Type Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → (Admission) → <b>Infection Type Master</b> → <b>Add</b> → enter
 * <b>Code</b>, <b>Remark</b>, <b>Field name</b>, <b>Control Binding</b> → <b>Add</b> (append the field row) →
 * <b>Submit</b> → toast.</p>
 *
 * <p>New screen — route, field ng-models and the Submit/Add handlers are discovered at runtime and then wired.</p>
 */
public class InfectionTypeMaster extends BasePage {

    public InfectionTypeMaster(Page page) { super(page); }

    /** Route — captured at runtime by mining the menu href (config menu links are often hidden). */
    public static String ROUTE = "";
    public String lastCode = "";
    public String lastRemark = "";

    /** Realistic infection/precaution-type names — a retry after "already exists" must still read like real data,
     *  never "Auto infection &lt;CODE&gt;". */
    private static final String[] REMARKS = {
            "MRSA Precaution", "Airborne Isolation", "Droplet Precaution", "Contact Precaution",
            "Neutropenic Precaution", "TB Isolation Protocol", "C. Diff Isolation", "VRE Precaution"
    };

    // ---- discovery -------------------------------------------------------

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " out.push('tables='+document.querySelectorAll('table').length+' ui-grids='+document.querySelectorAll('[ui-grid],.ui-grid').length);"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+(b.getAttribute('ng-click')?(' {'+b.getAttribute('ng-click')+'}'):'')).filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,30).join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    public String dumpAddForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,.form-line,.col-sm-6,.col-md-6,.col-sm-4,td,div'); const l=g?g.querySelector('label,.control-label'):null; return norm(l?l.textContent:'').slice(0,30); };"
                + " const out=[];"
                + " const inputs=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden');"
                + " inputs.slice(0,40).forEach(e=>{ out.push(e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'+(e.tagName==='SELECT'?(' opts='+e.options.length):'')); });"
                + " const btns=[...document.querySelectorAll('button,a,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+' {'+(b.getAttribute('ng-click')||'')+'}').filter((v,i,a)=>v && a.indexOf(v)===i && /add|submit|save/i.test(v));"
                + " out.push('add/submit buttons=\\n  '+btns.slice(0,15).join('\\n  '));"
                + " return out.join('\\n'); }");
        return r == null ? "" : r.toString();
    }

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        Object appCfg = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return false; a.id='__appCfgMenu'; return true; }");
        if (Boolean.TRUE.equals(appCfg)) {
            try { page.locator("#__appCfgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("InfectionTypeMaster.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*infection\\s*type/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__infMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__infMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("InfectionTypeMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        }
        if (ROUTE.isEmpty()) {
            Object mined = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*infection\\s*type/i.test(norm(x.textContent)) || /infectiontype|infection/i.test(x.getAttribute('href')||'')); return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("InfectionTypeMaster.nav: mined href => " + mined);
            if (mined != null && mined.toString().contains("#/")) ROUTE = mined.toString().substring(mined.toString().indexOf("#/"));
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("InfectionTypeMaster.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return !ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase());
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the top <b>Add</b> button (opens the add form; polls). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button],span,i')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('[ng-click]')].find(x=>/add/i.test(x.getAttribute('ng-click')||'') && /add/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__itmAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("InfectionTypeMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__itmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("InfectionTypeMaster.clickAdd: click failed - " + e.getMessage()); }
        waitForAngular(1200);
        return true;
    }

    /**
     * Fill <b>Code</b>, <b>Remark</b>, <b>Field name</b>, <b>Control Binding</b> (all by label; polls for the async
     * form). Control Binding may be a dropdown (pick first real option) or a text input. Returns a summary with the
     * discovered ng-models.
     */
    public String fillDetails(int attempt) {
        String code = "IT" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input[ng-model],textarea[ng-model],select[ng-model]')].some(e=>e.offsetParent!==null && /code|field|binding|commonmaster|infection/i.test(e.getAttribute('ng-model')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) {
            System.out.println("InfectionTypeMaster inputs => " + page.evaluate("() => [...document.querySelectorAll('input,textarea,select')].filter(e=>e.offsetParent!==null).map(e=>e.tagName+' ng=\"'+(e.getAttribute('ng-model')||'')+'\"').join(' | ')"));
        }
        // Exact ng-models: infection.code / .description / .fieldname (text) + .bindingcontrol (select — options load
        // ASYNC, so POLL for a real option before selecting, else Submit says "Please Enter Control Binding!").
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const code=a.code; const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const setNg=(ng,v)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\"); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=setNg('infection.code', code); const rm=setNg('infection.description', a.remark); const fn=setNg('infection.fieldname', 'AutoField'+code.slice(-4));"
                + " let cb='(no)'; for(let k=0;k<15;k++){ const e=document.querySelector(\"select[ng-model='infection.bindingcontrol']\"); if(e){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} cb=norm(e.options[i].textContent); break; } } await sleep(400); }"
                + " resolve('Code='+cd+' | Remark='+rm+' | Field='+fn+' | Binding='+cb+' | codeNg=infection.code | remNg=infection.description | fieldNg=infection.fieldname | bindNg=infection.bindingcontrol'); })",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click the detail <b>Add</b> button (appends the Field name/Control Binding row to the list). Returns the list
     *  row count after. Targets an Add button near the Field name/Control Binding fields (not the top list Add). */
    public String clickAddDetail() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                // find an Add button whose ng-click looks like an add-to-list handler, else any visible Add after the fields
                + " const adds=[...document.querySelectorAll('button,a,input[type=button]')].filter(b=>b.offsetParent!==null && /^\\s*add\\s*$/i.test(norm(b.textContent||b.value)));"
                + " const b=adds.find(x=>/adddetail|addfield|addlist|addrow|additem/i.test(x.getAttribute('ng-click')||'')) || adds[adds.length-1];"
                + " if(!b) return 'no-add'; b.id='__itmAddDetail'; return (b.getAttribute('ng-click')||'add'); }");
        String how = String.valueOf(r);
        if ("no-add".equals(how)) { System.out.println("clickAddDetail: no detail Add button"); return ""; }
        try { page.locator("#__itmAddDetail").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("clickAddDetail: click failed - " + e.getMessage()); }
        waitForAngular(600);
        return how;
    }

    /** Click <b>Submit</b> and return the toast (success or a server error). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__itToasts=[]; if(window.__itObs) window.__itObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__itToasts.includes(t)) window.__itToasts.push(t); }); };"
                + " window.__itObs=new MutationObserver(grab); window.__itObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__itmSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__itmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__itToasts||[]).some(a=>/infection|master|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__itToasts||[]).includes(t)) (window.__itToasts=window.__itToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__itToasts||[]; return a.find(x=>/infection.*(saved|added)|master.*(saved|added)|added successfully|saved successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
