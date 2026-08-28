package com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Diet and Nutrition &gt; <b>Consistency Modified Diet Master</b> — configuration
 * screen Page Object (inline-add commonmaster).
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Diet and Nutrition</b> (submenu) → <b>Consistency Modified Diet
 * Master</b> → fill Code + Remark (Form Name disabled) → <b>Submit</b> → success toast.</p>
 */
public class ConsistencyModifiedDietMaster extends BasePage {

    public ConsistencyModifiedDietMaster(Page page) { super(page); }

    public String route = "", lastCode = "", lastRemark = "";
    /** ng-model prefix of the master (discovered), e.g. {@code ConsistencyModifiedDiet}. */
    public String prefix = "";

    /** Realistic consistency/modified-diet names, not "Auto consistency diet &lt;code&gt;" filler — cycled per retry attempt. */
    private static final String[] REMARKS = {
            "Pureed Diet", "Minced and Moist Diet", "Soft Diet", "Regular Diet", "Liquidized Diet",
            "Thickened Fluids", "Fork Mashable Diet", "Bite-Sized Diet", "Smooth Pureed Diet", "Easy to Chew Diet"
    };

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/diet\\s*(and|&)?\\s*nutrition/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/consistency/i.test(norm(x.textContent)) || /consistency/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__cmMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__cmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("ConsistencyDiet.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        } else { System.out.println("ConsistencyDiet.nav: menu link not found"); }
        waitForAngular(1200);
        return page.url().length() > 0;
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("consistency"); }

    /** Discover the master ng-model prefix (the object holding a {@code .Code} field on this screen). */
    public String detectPrefix() {
        Object p = page.evaluate("() => { const e=[...document.querySelectorAll('input')].find(x=>x.offsetParent!==null && /\\.Code$/i.test(x.getAttribute('ng-model')||'')); return e?(e.getAttribute('ng-model')||'').replace(/\\.Code$/i,''):''; }");
        prefix = p == null ? "" : p.toString();
        return prefix;
    }

    /**
     * Wait for the form, then enter Code + Remark. {@code attempt} shifts BOTH values so a retry after an
     * "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && /\\.Code$/i.test(e.getAttribute('ng-model')||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("fillDetails: Code input not ready"); }
        detectPrefix();
        if (prefix.isEmpty()) return "(no Code field)";
        String code = "CMD" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(a.p+'.Code',a.code); let rm='(n/a)'; ['.Remark','.Description'].forEach(f=>{ const e=document.querySelector(\"[ng-model='\"+a.p+f+\"']\"); if(e && rm==='(n/a)') rm=set(a.p+f,a.remark); }); return 'Code='+cd+' | Remark='+rm; }",
                java.util.Map.of("p", prefix, "code", code, "remark", lastRemark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> (IUD handler / text Submit) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__cmToasts=[]; if(window.__cmObs) window.__cmObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__cmToasts.includes(t)) window.__cmToasts.push(t); }); };"
                + " window.__cmObs=new MutationObserver(grab); window.__cmObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const cand=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " let b=cand.find(x=>/IUD.*Diet|IUDConsistency|Diet.*Master/i.test(x.getAttribute('ng-click')||''))"
                + " || cand.find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()))"
                + " || cand.find(x=>/IUD|SaveCommon|fnSave|fnIUD/i.test(x.getAttribute('ng-click')||'') && !/clear|cancel|openMic|pagination|edit|search/i.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__cmSubmit'; return b.getAttribute('ng-click')||'text'; }");
        if (!Boolean.TRUE.equals(tagged) && (tagged == null || tagged.toString().isEmpty())) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        System.out.println("Consistency submit handler => " + tagged);
        try { page.locator("#__cmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__cmToasts||[]).some(a=>/diet|saved|success|added|updated|please|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__cmToasts||[]).includes(t)) (window.__cmToasts=window.__cmToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__cmToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
