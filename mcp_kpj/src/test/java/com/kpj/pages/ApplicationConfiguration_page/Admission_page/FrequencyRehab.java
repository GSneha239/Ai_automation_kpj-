package com.kpj.pages.ApplicationConfiguration_page.Admission_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Admission &gt; <b>Frequency Rehab</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → (Admission) → <b>Frequency Rehab</b> → enter <b>Code</b> +
 * <b>Remark</b> → <b>Submit</b> ("Master Saved Successfully.") → in the grid, change a record's <b>Status</b> to
 * true ("Status updated successfully.").</p>
 *
 * <p>New screen — route + field ng-models discovered at runtime. A `commonmaster` screen like
 * {@code RoomAmenities}/{@code BedSideAmenities}; the grid row checkbox is a Status toggle.</p>
 */
public class FrequencyRehab extends BasePage {

    public FrequencyRehab(Page page) { super(page); }

    /** Route — captured at runtime by mining the menu href (config menu links are often hidden). */
    public static String ROUTE = "";
    public String lastCode = "";
    public String lastRemark = "";
    private String codeNg = "", remNg = "";

    /** Realistic rehab-frequency schedule names — a retry after "already exists" must still read like real data,
     *  never "Auto frequency rehab &lt;CODE&gt;". */
    private static final String[] REMARKS = {
            "Daily Physiotherapy", "Twice Weekly Rehab", "Alternate Day Therapy", "Weekly Occupational Therapy",
            "Post-Op Mobility Plan", "Speech Therapy Session", "Thrice Weekly Physiotherapy", "Monthly Review Therapy"
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
            catch (Exception e) { System.out.println("FrequencyRehab.nav: Application Configuration click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && !(x.getAttribute('href')||'').includes('#/')); if(a) a.click(); }");
        waitForAngular(700);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*frequency\\s*rehab/i.test(norm(x.textContent)) && x.offsetParent!==null); if(!a) return ''; a.id='__freqMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__freqMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("FrequencyRehab.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
        }
        if (ROUTE.isEmpty()) {
            Object mined = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*frequency\\s*rehab/i.test(norm(x.textContent)) || /frequencyrehab|freqrehab|rehab/i.test(x.getAttribute('href')||'')); return a ? (a.getAttribute('href')||'') : ''; }");
            System.out.println("FrequencyRehab.nav: mined href => " + mined);
            if (mined != null && mined.toString().contains("#/")) ROUTE = mined.toString().substring(mined.toString().indexOf("#/"));
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("FrequencyRehab.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return !ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase());
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button if the screen gates the form behind it (best-effort). */
    public boolean clickAddIfPresent() {
        Object t = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__frAdd'; return true; }");
        if (!Boolean.TRUE.equals(t)) return false;
        try { page.locator("#__frAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("FrequencyRehab.clickAddIfPresent: click failed - " + e.getMessage()); }
        waitForAngular(1000);
        return true;
    }

    /** Enter <b>Code</b> + <b>Remark</b> (commonmaster ng-models first, else by label; polls for the async form).
     *  {@code attempt} shifts both values so a retry after an "already exists" toast submits genuinely different
     *  details. */
    public String fillCodeAndRemark(int attempt) {
        String code = "FR" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input[ng-model],textarea[ng-model]')].some(e=>e.offsetParent!==null && /code|commonmaster|frequency|name/i.test(e.getAttribute('ng-model')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) {
            System.out.println("FrequencyRehab inputs => " + page.evaluate("() => [...document.querySelectorAll('input,textarea,select')].filter(e=>e.offsetParent!==null).map(e=>e.tagName+' ng=\"'+(e.getAttribute('ng-model')||'')+'\"').join(' | ')"));
        }
        Object r = page.evaluate("(a) => { const code=a.code; const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular;"
                + " const setNg=(ng,v)=>{ const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\"); if(!e) return null; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const labelField=(labels)=>{ for(const label of labels){ const want=label.toLowerCase();"
                + "   const lbls=[...document.querySelectorAll('label,.control-label,span,th,td,b')].filter(e=>e.offsetParent!==null && norm(e.textContent).replace(/\\*/g,'').trim().toLowerCase()===want);"
                + "   for(const l of lbls){ const g=l.closest('.form-group,.row,.col-sm-6,.col-md-6,.col-sm-4,td,tr,div'); const f=g?g.querySelector('input:not([type=hidden]),textarea'):null; if(f&&f.offsetParent!==null) return f; } } return null; };"
                + " const setInp=(e,v)=>{ if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " let cd=setNg('commonmaster.Code', code); let cn='commonmaster.Code'; if(cd===null){ const f=labelField(['Code','Frequency Rehab Code']); cd=setInp(f, code); cn=f?f.getAttribute('ng-model'):'-'; }"
                + " let rm=setNg('commonmaster.Description', a.remark); let rn='commonmaster.Description'; if(rm===null){ const f=labelField(['Remark','Remarks','Description']); rm=setInp(f, a.remark); rn=f?f.getAttribute('ng-model'):'-'; }"
                + " return 'Code='+cd+' | Remark='+rm+' | codeNg='+cn+' | remNg='+rn; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        String res = r == null ? "" : r.toString();
        java.util.regex.Matcher mc = java.util.regex.Pattern.compile("codeNg=([^|]+)").matcher(res);
        java.util.regex.Matcher mn = java.util.regex.Pattern.compile("remNg=([^|]+)").matcher(res);
        if (mc.find()) codeNg = mc.group(1).trim();
        if (mn.find()) remNg = mn.group(1).trim();
        waitForAngular(400);
        return res;
    }

    /** Click <b>Submit</b> (re-asserts Code + Remark first — guards the flaky "* mark fields") and return the toast. */
    public String submitAndGetToast() {
        if (!codeNg.isEmpty() && !codeNg.equals("-")) {
            page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ if(!ng||ng==='-') return; const e=document.querySelector(\"input[ng-model='\"+ng+\"'],textarea[ng-model='\"+ng+\"']\"); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    + " set(a.codeNg, a.code); set(a.remNg, a.remark); }",
                    java.util.Map.of("codeNg", codeNg, "remNg", remNg, "code", lastCode, "remark", lastRemark));
            waitForAngular(300);
        }
        Object tagged = page.evaluate("() => { window.__frToasts=[]; if(window.__frObs) window.__frObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__frToasts.includes(t)) window.__frToasts.push(t); }); };"
                + " window.__frObs=new MutationObserver(grab); window.__frObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__frSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__frSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__frToasts||[]).some(a=>/master|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__frToasts||[]).includes(t)) (window.__frToasts=window.__frToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__frToasts||[]; return a.find(x=>/master saved|added successfully|saved successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /**
     * In the grid, change a record's <b>Status</b> to TRUE and return the toast ("Status updated successfully."). The
     * grid is a virtualized ui-grid — scroll into view, poll for rows, then click a row's status checkbox that is
     * currently UNCHECKED (so it flips to true). Returns the toast.
     */
    public String changeStatusToTrue() {
        // reset toast capture
        page.evaluate("() => { window.__frToasts=[]; }");
        String rowText = null;
        for (int i = 0; i < 20 && rowText == null; i++) {
            Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const g=document.querySelector('.ui-grid-viewport,.ui-grid,[ui-grid]'); if(g && g.scrollIntoView) g.scrollIntoView({block:'center'});"
                    + " const rows=[...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row, table tbody tr')].filter(r=>r.offsetParent!==null && norm(r.textContent) && !/no records|no data/i.test(r.textContent));"
                    + " if(!rows.length) return null;"
                    // prefer a row whose status checkbox is UNCHECKED (so ticking flips to TRUE); else first row
                    + " let row=rows.find(r=>{ const c=r.querySelector('input[type=checkbox]'); return c && !c.checked; }) || rows[0];"
                    + " const cb=row.querySelector('input[type=checkbox]'); if(!cb) return null; cb.id='__frStatusCb';"
                    + " return norm(([...row.querySelectorAll('.ui-grid-cell-contents,td')].map(c=>norm(c.textContent)).find(x=>x && !/^\\d+$/.test(x))) || norm(row.textContent)).slice(0,40)+' | was='+(cb.checked?'true':'false'); }");
            if (r != null) rowText = r.toString();
            else page.waitForTimeout(600);
        }
        if (rowText == null) { System.out.println("changeStatusToTrue: no grid rows"); return ""; }
        try { page.locator("#__frStatusCb").click(new com.microsoft.playwright.Locator.ClickOptions().setForce(true).setTimeout(5000)); }
        catch (Exception e) { System.out.println("changeStatusToTrue: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__frToasts||[]).some(a=>/status updated|updated successfully|status|updated|success/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) {
            page.waitForTimeout(2000);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__frToasts||[]).includes(t)) (window.__frToasts=window.__frToasts||[]).push(t); }); }");
        }
        Object t = page.evaluate("() => { const a=window.__frToasts||[]; return a.find(x=>/status updated|updated successfully|updated/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return t == null ? "" : t.toString().trim();
    }
}
