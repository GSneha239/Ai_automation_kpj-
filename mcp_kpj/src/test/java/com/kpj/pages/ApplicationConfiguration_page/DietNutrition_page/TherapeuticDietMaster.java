package com.kpj.pages.ApplicationConfiguration_page.DietNutrition_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Diet and Nutrition &gt; <b>Therapeutic Diet Master</b> — configuration screen Page
 * Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Diet and Nutrition</b> (submenu) → <b>Therapeutic Diet Master</b>
 * → fill the details → <b>Submit</b> → success toast.</p>
 */
public class TherapeuticDietMaster extends BasePage {

    public TherapeuticDietMaster(Page page) { super(page); }

    public String route = "", lastCode = "", lastRemark = "";

    /** Realistic therapeutic-diet names, not "Auto therapeutic diet &lt;code&gt;" filler — cycled per retry attempt. */
    private static final String[] REMARKS = {
            "Diabetic Diet", "Renal Diet", "Low Sodium Diet", "High Protein Diet", "Low Fat Diet",
            "Gluten Free Diet", "Soft Diet", "Liquid Diet", "Low Fiber Diet", "Cardiac Diet"
    };

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // expand the "Diet and Nutrition" submenu parent
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/diet\\s*(and|&)?\\s*nutrition/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/therapeutic\\s*diet/i.test(norm(x.textContent)) || /therapeuticdiet/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__tdMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            route = href.toString();
            try { page.locator("#__tdMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("TherapeuticDiet.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        } else { System.out.println("TherapeuticDiet.nav: menu link not found"); }
        waitForAngular(1200);
        return page.url().length() > 0;
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("therapeuticdiet"); }

    /** JS helper: the visible Code / Remark inputs, whatever they are called on this screen — DISCOVERED at
     *  runtime rather than hard-coded. Confirmed live: this screen actually uses {@code formData.Code} /
     *  {@code formData.Remark}, not {@code TherapeuticDiet.Code}/{@code .Remark} — matching by ng-model SUFFIX
     *  (same shape as the other generic Code+Remark masters) survives that kind of drift. */
    private static final String FIND_FIELDS =
            " const skip=m=>!m || /colFilter|paginationCurrentPage|textAngular|^q$/i.test(m);"
            + " const boxes=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && !skip(e.getAttribute('ng-model')));"
            + " const byModel=re=>boxes.find(e=>re.test(e.getAttribute('ng-model')||''));"
            + " const codeEl = byModel(/\\.code(id)?$/i);"
            + " const remEl  = byModel(/\\.(remark|description)$/i);";

    /**
     * Wait for the form, then enter Code + Remark (Form Name is disabled/auto). {@code attempt} shifts BOTH
     * values so a retry after an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        try {
            page.waitForFunction("() => {" + FIND_FIELDS + " return !!(codeEl && remEl); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) {
            System.out.println("fillDetails: Code/Remark input not ready");
            Object dbg = page.evaluate("() => [...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null).slice(0,40).map(e=>e.tagName+'['+(e.getAttribute('ng-model')||e.id||e.name||'?')+']').join(' ; ')");
            System.out.println("fillDetails DEBUG visible fields: " + dbg);
            System.out.println("fillDetails DEBUG url: " + page.url());
        }
        String code = "TD" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular;" + FIND_FIELDS
                + " const set=(e,v)=>{ if(!e) return '(no field)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set(codeEl,a.code); const rm=set(remEl,a.remark);"
                + " return 'Code='+cd+' | Remark='+rm+' | [models: '+(codeEl?(codeEl.getAttribute('ng-model')||'?'):'none')+' / '+(remEl?(remEl.getAttribute('ng-model')||'?'):'none')+']'; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> ({@code IUDTherapeuticDietMaster}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__tdToasts=[]; if(window.__tdObs) window.__tdObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__tdToasts.includes(t)) window.__tdToasts.push(t); }); };"
                + " window.__tdObs=new MutationObserver(grab); window.__tdObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/IUDTherapeuticDietMaster/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__tdSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__tdSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__tdToasts||[]).some(a=>/diet|saved|success|added|updated|please|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__tdToasts||[]).includes(t)) (window.__tdToasts=window.__tdToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__tdToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
