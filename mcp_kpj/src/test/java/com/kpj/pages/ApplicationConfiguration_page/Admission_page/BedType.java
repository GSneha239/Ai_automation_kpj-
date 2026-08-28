package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Bed Type</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → (Admission) → <b>Bed Type</b> → <b>Add</b> → enter <b>Code</b> +
 * <b>Bed Type</b> → <b>Submit</b> → toast.</p>
 *
 * <p>New screen — route, field ng-models and the Submit handler are discovered at runtime and then wired.</p>
 */
public class BedType extends BasePage {

    public BedType(Page page) { super(page); }

    /** Route — captured at runtime by mining the menu href (config menu links are often hidden). */
    public static String ROUTE = "";
    public String lastCode = "";

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
                + " inputs.slice(0,30).forEach(e=>{ out.push(e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" id=\"'+(e.id||'')+'\" label=\"'+labelOf(e)+'\"'+(e.tagName==='SELECT'?(' opts='+e.options.length):'')); });"
                + " const subs=[...document.querySelectorAll('button,input[type=submit]')].filter(b=>b.offsetParent!==null && /submit|save/i.test(b.textContent||b.value||'')).map(b=>norm(b.textContent||b.value)+' {'+(b.getAttribute('ng-click')||'')+'}');"
                + " out.push('submit/save buttons=\\n  '+subs.join('\\n  '));"
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
            catch (Exception e) { System.out.println("BedType.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // real-click the visible "Bed Type" link (exact text) if present (capture its route)
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*bed\\s*type\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__bedTypeMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__bedTypeMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BedType.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        }
        // mine ANY anchor (even hidden) whose text is exactly "Bed Type" or href mentions bedtype
        if (ROUTE.isEmpty()) {
            Object mined = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*bed\\s*type\\s*$/i.test(norm(x.textContent)) || /bedtype/i.test(x.getAttribute('href')||'')); return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("BedType.nav: mined href => " + mined);
            if (mined != null && mined.toString().contains("#/")) ROUTE = mined.toString().substring(mined.toString().indexOf("#/"));
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("BedType.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return !ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase());
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls — it may render after the grid). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button],span,i')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('[ng-click]')].find(x=>/add/i.test(x.getAttribute('ng-click')||'') && /add/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__btAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("BedType.clickAdd: Add button not found"); return false; }
        try { page.locator("#__btAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("BedType.clickAdd: click failed - " + e.getMessage()); }
        waitForAngular(1200);
        return true;
    }

    /** Realistic bed-type names — a retry after "already exists" must still read like real data, never
     *  "AutoBedType&lt;CODE&gt;". */
    private static final String[] BED_TYPES = {
            "General Ward Bed", "ICU Bed", "Isolation Bed", "Pediatric Bed",
            "Maternity Bed", "Recovery Bed", "High Dependency Bed", "Day Care Bed"
    };
    public String lastBedType = "";

    /** Enter <b>Code</b> (unique) + <b>Bed Type</b> (name). Located by label (polls for the form to render). Returns
     *  a summary incl. the discovered ng-models. {@code attempt} shifts both values so a retry after an "already
     *  exists" toast submits genuinely different details. */
    public String fillCodeAndBedType(int attempt) {
        String code = "BT" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastBedType = BED_TYPES[attempt % BED_TYPES.length] + (attempt >= BED_TYPES.length ? " " + (attempt / BED_TYPES.length + 1) : "");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input[ng-model],textarea[ng-model]')].some(e=>e.offsetParent!==null && /code|bedtype|commonmaster/i.test(e.getAttribute('ng-model')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) {
            System.out.println("BedType inputs => " + page.evaluate("() => [...document.querySelectorAll('input,textarea,select')].filter(e=>e.offsetParent!==null).map(e=>e.tagName+' ng=\"'+(e.getAttribute('ng-model')||'')+'\"').join(' | ')"));
        }
        // Set the exact ng-models (Code = BedTypeMaster.code, Bed Type = BedTypeMaster.name) + any textAngular body
        // (a mandatory * field intermittently triggers "Please fill * mark fields!").
        Object r = page.evaluate("(a) => { const code=a.code; const A=window.angular;"
                + " const setNg=(ng,v)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\"); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=setNg('BedTypeMaster.code', code); const bt=setNg('BedTypeMaster.name', a.bedType);"
                // fill any textAngular / contenteditable / CKEditor body (a mandatory Description* field)
                + " let txtWhere='(none)';"
                + " if(window.CKEDITOR){ const insts=Object.values(CKEDITOR.instances||{}); const inst=insts.find(e=>{try{return e.container&&e.container.$&&e.container.$.offsetParent!==null;}catch(x){return false;}})||insts[0]; if(inst){ try{ inst.setData('<p>Auto bed type</p>'); txtWhere='CKEDITOR'; }catch(x){} } }"
                + " const eds=[...document.querySelectorAll('.ta-bind,.ta-editor,div[contenteditable=true],[contenteditable=true]')].filter(e=>e.offsetParent!==null);"
                + " for(const ce of eds){ ce.innerHTML='<p>Auto bed type</p>'; ['input','keyup','change','blur'].forEach(ev=>ce.dispatchEvent(new Event(ev,{bubbles:true}))); }"
                + " if(txtWhere==='(none)' && eds.length) txtWhere='contenteditable('+eds.length+')';"
                // also set the textAngular host's ng-model directly (it holds the persisted value)
                + " let taHostNg='-'; const host=[...document.querySelectorAll('[ng-model]')].find(e=>e.hasAttribute('ta-bind')||/ta-root|ta-editor|ta-bind|text-angular/i.test(e.className||'')||e.tagName==='TEXT-ANGULAR');"
                + " if(host){ taHostNg=host.getAttribute('ng-model'); const c=A.element(host).controller('ngModel'); if(c){ c.$setViewValue('<p>Auto bed type</p>'); c.$render(); host.dispatchEvent(new Event('change',{bubbles:true})); } }"
                + " return 'Code='+cd+' | BedType='+bt+' | codeNg=BedTypeMaster.code | btNg=BedTypeMaster.name | text='+txtWhere+' | taHostNg='+taHostNg; }",
                java.util.Map.of("code", code, "bedType", lastBedType));
        waitForAngular(500);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> and return the toast (success or a server error). */
    public String submitAndGetToast() {
        // Re-assert Code + Bed Type right before Submit — they can be cleared by a late digest (intermittent
        // "Please fill * mark fields!"). Poll until the model actually holds them.
        page.evaluate("(a) => { const A=window.angular;"
                + " const set=(ng,v)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"']\"); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('BedTypeMaster.code', a.code); set('BedTypeMaster.name', a.bedType); }",
                java.util.Map.of("code", lastCode, "bedType", lastBedType));
        waitForAngular(300);
        Object tagged = page.evaluate("() => { window.__btToasts2=[]; if(window.__btObs2) window.__btObs2.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__btToasts2.includes(t)) window.__btToasts2.push(t); }); };"
                + " window.__btObs2=new MutationObserver(grab); window.__btObs2.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDBedTypeMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__btSubmit2'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__btSubmit2").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__btToasts2||[]).some(a=>/bed\\s*type|master|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__btToasts2||[]).includes(t)) (window.__btToasts2=window.__btToasts2||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__btToasts2||[]; return a.find(x=>/bed\\s*type.*(saved|added)|master saved|added successfully|saved successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
