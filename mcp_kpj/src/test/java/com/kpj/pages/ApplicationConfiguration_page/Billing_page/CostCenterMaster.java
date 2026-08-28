package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Cost Centre</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Cost Centre</b> (route
 * {@code #/CostCenterMaster}) → <b>Add</b> ({@code #/add-CostCenterMaster}) → select <b>Location</b>, enter
 * <b>Cost Centre Code</b>, <b>Cost Centre Name</b>, <b>Profit Centre</b> → <b>Submit</b>
 * ({@code fnIUDCostCenterMaster}) → toast.</p>
 *
 * <p>Fields: {@code CostCenter.locationid} (Location select), {@code CostCenter.costcentercode},
 * {@code CostCenter.costcentername}, {@code CostCenter.profitcenter}.</p>
 */
public class CostCenterMaster extends BasePage {

    public CostCenterMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/CostCenterMaster";
    public static String ADD_ROUTE = "#/add-CostCenterMaster";
    public String lastCode = "";
    public String lastName = "", lastProfitCentre = "";

    /** Realistic Cost Centre names — a fresh one is picked per retry attempt after "already exists". */
    private static final String[] COST_CENTRE_NAMES = {
            "Emergency Department", "Radiology Unit", "Pharmacy Services", "Laboratory Services",
            "Physiotherapy Unit", "Intensive Care Unit", "Outpatient Clinic", "Administration Office",
            "Housekeeping Services", "Nursing Station", "Medical Records Unit", "Dietary Services"
    };

    /** Realistic Profit Centre names — likewise varied per attempt. */
    private static final String[] PROFIT_CENTRE_NAMES = {
            "Inpatient Services", "Outpatient Services", "Diagnostic Services", "Surgical Services",
            "Ancillary Services", "Consultation Services", "Day Care Services", "Rehabilitation Services",
            "Specialist Clinic Services", "Wellness Programs"
    };

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/cost\\s*cent(er|re)/i.test(norm(x.textContent)) || /costcenter/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__ccMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__ccMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("CostCenterMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("CostCenterMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("costcenter");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-CostCenterMaster}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__ccAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("CostCenterMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__ccAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("CostCenterMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='CostCenter.costcentercode')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("CostCenterMaster.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /**
     * Select Location (first real) and enter Cost Centre Code, Cost Centre Name, Profit Centre. {@code attempt}
     * shifts Code/Name/Profit Centre so a retry after an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "CC" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastName = COST_CENTRE_NAMES[attempt % COST_CENTRE_NAMES.length] + (attempt >= COST_CENTRE_NAMES.length ? " " + (attempt / COST_CENTRE_NAMES.length + 1) : "");
        lastProfitCentre = PROFIT_CENTRE_NAMES[attempt % PROFIT_CENTRE_NAMES.length] + (attempt >= PROFIT_CENTRE_NAMES.length ? " " + (attempt / PROFIT_CENTRE_NAMES.length + 1) : "");
        // Location — real selectOption (first real).
        String loc = "(no-opt)";
        try {
            page.waitForFunction("() => { const e=document.querySelector(\"select[ng-model='CostCenter.locationid']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
            Object idx = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='CostCenter.locationid']\"); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }");
            int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
            page.locator("select[ng-model='CostCenter.locationid']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            waitForAngular(400);
            loc = String.valueOf(page.evaluate("() => { const e=document.querySelector(\"select[ng-model='CostCenter.locationid']\"); return (e.options[e.selectedIndex]||{}).textContent||''; }")).trim();
        } catch (Exception e) { System.out.println("fillDetails: Location select failed - " + e.getMessage()); }
        Object r = page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cc=set('CostCenter.costcentercode',a.code); const nm=set('CostCenter.costcentername',a.name); const pc=set('CostCenter.profitcenter',a.profit);"
                + " return 'CostCentreCode='+cc+' | CostCentreName='+nm+' | ProfitCentre='+pc; }",
                java.util.Map.of("code", code, "name", lastName, "profit", lastProfitCentre));
        waitForAngular(400);
        return "Location=" + loc + " | " + (r == null ? "" : r.toString());
    }

    /** Click <b>Submit</b> ({@code fnIUDCostCenterMaster}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__ccToasts=[]; if(window.__ccObs) window.__ccObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ccToasts.includes(t)) window.__ccToasts.push(t); }); };"
                + " window.__ccObs=new MutationObserver(grab); window.__ccObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDCostCenterMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__ccSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__ccSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__ccToasts||[]).some(a=>/cost|centre|center|profit|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__ccToasts||[]).includes(t)) (window.__ccToasts=window.__ccToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__ccToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
