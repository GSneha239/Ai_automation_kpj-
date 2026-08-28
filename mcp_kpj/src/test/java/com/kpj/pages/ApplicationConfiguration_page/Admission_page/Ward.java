package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Ward</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → (Admission) → <b>Ward</b> → <b>Add</b> → enter <b>Code</b>,
 * <b>Remark</b>, <b>Floor</b> → <b>Submit</b> → toast.</p>
 *
 * <p>New screen — route, field ng-models and the Submit handler are discovered at runtime and then wired.</p>
 */
public class Ward extends BasePage {

    public Ward(Page page) { super(page); }

    /** Route — captured at runtime by mining the menu href (config menu links are often hidden). */
    public static String ROUTE = "";
    public String lastCode = "";
    public String lastRemark = "";
    private String codeNg = "", remNg = "", floorNg = "";

    /** Realistic ward names — a Remark reads as data a person would type, not "Auto <thing> <CODE>". */
    private static final String[] REMARKS = {
            "General Ward", "Intensive Care Ward", "Maternity Ward", "Pediatric Ward", "Surgical Ward",
            "Orthopedic Ward", "Cardiac Care Ward", "Isolation Ward", "Oncology Ward", "Rehabilitation Ward"
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
            catch (Exception e) { System.out.println("Ward.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // real-click the visible "Ward" link (exact text) if present (capture its route)
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*ward\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__wardMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__wardMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Ward.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        }
        // mine ANY anchor (even hidden) whose text is exactly "Ward" or href mentions ward master
        if (ROUTE.isEmpty()) {
            Object mined = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*ward\\s*$/i.test(norm(x.textContent)) || /wardmaster|#\\/ward/i.test(x.getAttribute('href')||'')); return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("Ward.nav: mined href => " + mined);
            if (mined != null && mined.toString().contains("#/")) ROUTE = mined.toString().substring(mined.toString().indexOf("#/"));
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("Ward.nav: direct route " + ROUTE);
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
                    + " || [...document.querySelectorAll('[ng-click]')].find(x=>/add/i.test(x.getAttribute('ng-click')||'') && /add/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!b) return false; b.id='__wardAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("Ward.clickAdd: Add button not found"); return false; }
        try { page.locator("#__wardAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("Ward.clickAdd: click failed - " + e.getMessage()); }
        waitForAngular(1200);
        return true;
    }

    /** Enter <b>Code</b>, <b>Remark</b> (text, by label) + <b>Floor</b> (dropdown → first real option, else text).
     *  Records code/remark ng-models for the pre-submit re-assert. {@code attempt} shifts Code and Remark so a
     *  retry after an "already exists" toast submits genuinely different details. Returns a summary. */
    public String fillDetails(int attempt) {
        String code = "WD" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input[ng-model],textarea[ng-model],select[ng-model]')].some(e=>e.offsetParent!==null && /code|floor|ward|commonmaster|name/i.test(e.getAttribute('ng-model')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) {
            System.out.println("Ward inputs => " + page.evaluate("() => [...document.querySelectorAll('input,textarea,select')].filter(e=>e.offsetParent!==null).map(e=>e.tagName+' ng=\"'+(e.getAttribute('ng-model')||'')+'\"').join(' | ')"));
        }
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const labelField=(labels)=>{ for(const label of labels){ const want=label.toLowerCase();"
                + "   const lbls=[...document.querySelectorAll('label,.control-label,span,th,td,b')].filter(e=>e.offsetParent!==null && norm(e.textContent).replace(/\\*/g,'').trim().toLowerCase()===want);"
                + "   for(const l of lbls){ const g=l.closest('.form-group,.row,.col-sm-6,.col-md-6,.col-sm-4,td,tr,div'); const f=g?g.querySelector('input:not([type=hidden]),textarea,select'):null; if(f&&f.offsetParent!==null) return f; } } return null; };"
                + " const setInp=(e,v)=>{ if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const setSelPoll=async (e)=>{ if(!e) return '(no)'; for(let k=0;k<12;k++){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); } await sleep(400); } return '(no-opt)'; };"
                + " const codeF=labelField(['Code','Ward Code']);"
                + " const remF=labelField(['Remark','Remarks','Description']);"
                + " const flF=labelField(['Floor','Floor No','Floor Number']);"
                + " const cd=setInp(codeF, a.code); const rm=setInp(remF, a.remark);"
                + " let fl; if(flF && flF.tagName==='SELECT') fl=await setSelPoll(flF); else fl=setInp(flF, '1');"
                + " resolve('Code='+cd+' | Remark='+rm+' | Floor='+fl+' | codeNg='+(codeF?codeF.getAttribute('ng-model'):'-')+' | remNg='+(remF?remF.getAttribute('ng-model'):'-')+' | floorNg='+(flF?flF.getAttribute('ng-model'):'-')+' | floorTag='+(flF?flF.tagName:'-')); })",
                java.util.Map.of("code", code, "remark", lastRemark));
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher mc = java.util.regex.Pattern.compile("codeNg=([^|]+)").matcher(res);
        java.util.regex.Matcher mn = java.util.regex.Pattern.compile("remNg=([^|]+)").matcher(res);
        java.util.regex.Matcher mf = java.util.regex.Pattern.compile("floorNg=([^|]+)").matcher(res);
        if (mc.find()) codeNg = mc.group(1).trim();
        if (mn.find()) remNg = mn.group(1).trim();
        if (mf.find()) floorNg = mf.group(1).trim();
        waitForAngular(400);
        return res;
    }

    /** Click <b>Submit</b> (re-asserts Code + Remark first — guards the flaky "* mark fields") and return the toast. */
    public String submitAndGetToast() {
        if (!codeNg.isEmpty() && !codeNg.equals("-")) {
            page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                    + " const set=(ng,v)=>{ if(!ng||ng==='-') return; const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\"); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    // re-assert the Floor select too — a late digest clears it → "Please Select Floor!"
                    + " const setSel=(ng)=>{ if(!ng||ng==='-') return; const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!e) return; if(e.selectedIndex>0 && !/^-*\\s*select/i.test(norm((e.options[e.selectedIndex]||{}).text))) return; const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} };"
                    + " set(a.codeNg, a.code); set(a.remNg, a.remark); setSel(a.floorNg); }",
                    java.util.Map.of("codeNg", codeNg, "remNg", remNg, "floorNg", floorNg.isEmpty() ? "-" : floorNg, "code", lastCode, "remark", lastRemark));
            waitForAngular(300);
        }
        Object tagged = page.evaluate("() => { window.__wdToasts=[]; if(window.__wdObs) window.__wdObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__wdToasts.includes(t)) window.__wdToasts.push(t); }); };"
                + " window.__wdObs=new MutationObserver(grab); window.__wdObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__wardSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__wardSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__wdToasts||[]).some(a=>/ward|master|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__wdToasts||[]).includes(t)) (window.__wdToasts=window.__wdToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__wdToasts||[]; return a.find(x=>/ward.*(saved|added)|master.*(saved|added)|added successfully|saved successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
