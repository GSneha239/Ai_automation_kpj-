package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Therapy Type</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → (Admission) → <b>Therapy Type</b> → enter <b>Code</b> +
 * <b>Therapy Type</b> → <b>Submit</b> → toast.</p>
 *
 * <p>New screen — route, field ng-models and the Submit handler are discovered at runtime and then wired.</p>
 */
public class TherapyType extends BasePage {

    public TherapyType(Page page) { super(page); }

    /** Route — captured at runtime by mining the menu href (config menu links are often hidden). */
    public static String ROUTE = "";
    public String lastCode = "";
    public String lastType = "";
    /** ng-models discovered by {@link #fillCodeAndType} — reused to re-assert before Submit. */
    private String codeNg = "", nameNg = "";

    /** Realistic therapy-type names — a Type reads as data a person would type, not "Auto <thing> <CODE>". */
    private static final String[] TYPES = {
            "Physiotherapy", "Occupational Therapy", "Speech Therapy", "Respiratory Therapy",
            "Hydrotherapy", "Rehabilitation Therapy", "Massage Therapy", "Cardiac Rehabilitation"
    };

    // ---- discovery -------------------------------------------------------

    public String dumpScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[]; out.push('URL='+location.href);"
                + " out.push('tables='+document.querySelectorAll('table').length+' ui-grids='+document.querySelectorAll('[ui-grid],.ui-grid').length);"
                + " const labelOf=e=>{ const g=e.closest('.form-group,.row,.col-sm-6,.col-md-6,td,div'); const l=g?g.querySelector('label,.control-label'):null; return norm(l?l.textContent:'').slice(0,30); };"
                + " const inputs=[...document.querySelectorAll('input,textarea,select')].filter(e=>e.offsetParent!==null && e.type!=='hidden' && !/filter|colfilter|logbook/i.test(e.getAttribute('ng-model')||''));"
                + " out.push('fields=\\n  '+inputs.slice(0,20).map(e=>e.tagName+'['+(e.type||'')+'] ng=\"'+(e.getAttribute('ng-model')||'')+'\" label=\"'+labelOf(e)+'\"').join('\\n  '));"
                + " const btns=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(b=>b.offsetParent!==null).map(b=>norm(b.textContent||b.value)+(b.getAttribute('ng-click')?(' {'+b.getAttribute('ng-click')+'}'):'')).filter((v,i,a)=>v && a.indexOf(v)===i);"
                + " out.push('buttons=\\n  '+btns.slice(0,30).join('\\n  '));"
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
            catch (Exception e) { System.out.println("TherapyType.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        // real-click the visible "Therapy Type" link (exact text) if present (capture its route)
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*therapy\\s*type\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__therapyMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__therapyMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("TherapyType.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        }
        // mine ANY anchor (even hidden) whose text is "Therapy Type" or href mentions therapy
        if (ROUTE.isEmpty()) {
            Object mined = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*therapy\\s*type\\s*$/i.test(norm(x.textContent)) || /therapytype|therapy/i.test(x.getAttribute('href')||'')); return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("TherapyType.nav: mined href => " + mined);
            if (mined != null && mined.toString().contains("#/")) ROUTE = mined.toString().substring(mined.toString().indexOf("#/"));
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("TherapyType.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return !ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase());
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button if the screen has one (some master screens gate the form behind Add). */
    public boolean clickAddIfPresent() {
        Object t = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__ttAdd'; return true; }");
        if (!Boolean.TRUE.equals(t)) return false;
        try { page.locator("#__ttAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("TherapyType.clickAddIfPresent: click failed - " + e.getMessage()); }
        waitForAngular(1000);
        return true;
    }

    /** Enter <b>Code</b> (unique) + <b>Therapy Type</b> (name), by label/ng-model (polls for the async form). Records
     *  the discovered ng-models. {@code attempt} shifts BOTH values so a retry after an "already exists" toast
     *  submits genuinely different details. Returns a summary. */
    public String fillCodeAndType(int attempt) {
        String code = "TT" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastType = TYPES[attempt % TYPES.length] + (attempt >= TYPES.length ? " " + (attempt / TYPES.length + 1) : "");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input[ng-model],textarea[ng-model]')].some(e=>e.offsetParent!==null && /code|commonmaster|therapy|name/i.test(e.getAttribute('ng-model')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) {
            System.out.println("TherapyType inputs => " + page.evaluate("() => [...document.querySelectorAll('input,textarea,select')].filter(e=>e.offsetParent!==null).map(e=>e.tagName+' ng=\"'+(e.getAttribute('ng-model')||'')+'\"').join(' | ')"));
        }
        Object r = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const setNg=(ng,v)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\"); if(!e) return null; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const labelField=(labels)=>{ for(const label of labels){ const want=label.toLowerCase();"
                + "   const lbls=[...document.querySelectorAll('label,.control-label,span,th,td,b')].filter(e=>e.offsetParent!==null && norm(e.textContent).replace(/\\*/g,'').trim().toLowerCase()===want);"
                + "   for(const l of lbls){ const g=l.closest('.form-group,.row,.col-sm-6,.col-md-6,.col-sm-4,td,tr,div'); const f=g?g.querySelector('input:not([type=hidden]),textarea'):null; if(f&&f.offsetParent!==null) return f; } } return null; };"
                + " const setInp=(e,v)=>{ if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                // try the shared commonmaster ng-models first, else by label
                + " let cd=setNg('commonmaster.Code', a.code); let cn='commonmaster.Code'; if(cd===null){ const f=labelField(['Code','Therapy Type Code']); cd=setInp(f, a.code); cn=f?f.getAttribute('ng-model'):'-'; }"
                + " let nm=setNg('commonmaster.Description', a.type); let nn='commonmaster.Description'; if(nm===null){ const f=labelField(['Therapy Type','TherapyType','Bed Type','Name','Description','Remark','Remarks']); nm=setInp(f, a.type); nn=f?f.getAttribute('ng-model'):'-'; }"
                + " return 'Code='+cd+' | Type='+nm+' | codeNg='+cn+' | nameNg='+nn; }",
                java.util.Map.of("code", code, "type", lastType));
        String res = r == null ? "" : r.toString();
        // capture the discovered ng-models for the pre-submit re-assert
        java.util.regex.Matcher mc = java.util.regex.Pattern.compile("codeNg=([^|]+)").matcher(res);
        java.util.regex.Matcher mn = java.util.regex.Pattern.compile("nameNg=([^|]+)").matcher(res);
        if (mc.find()) codeNg = mc.group(1).trim();
        if (mn.find()) nameNg = mn.group(1).trim();
        waitForAngular(400);
        return res;
    }

    /** Click <b>Submit</b> and return the toast. Re-asserts Code + Type right before Submit (guards the intermittent
     *  "Please fill * mark fields!" race seen on these master screens). */
    public String submitAndGetToast() {
        if (!codeNg.isEmpty() && !codeNg.equals("-")) {
            page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ if(!ng||ng==='-') return; const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\"); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    + " set(a.codeNg, a.code); set(a.nameNg, a.type); }",
                    java.util.Map.of("codeNg", codeNg, "nameNg", nameNg, "code", lastCode, "type", lastType));
            waitForAngular(300);
        }
        Object tagged = page.evaluate("() => { window.__ttToasts=[]; if(window.__ttObs) window.__ttObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ttToasts.includes(t)) window.__ttToasts.push(t); }); };"
                + " window.__ttObs=new MutationObserver(grab); window.__ttObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__ttSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__ttSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__ttToasts||[]).some(a=>/therapy|master|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__ttToasts||[]).includes(t)) (window.__ttToasts=window.__ttToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__ttToasts||[]; return a.find(x=>/therapy.*(saved|added)|master.*(saved|added)|added successfully|saved successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
