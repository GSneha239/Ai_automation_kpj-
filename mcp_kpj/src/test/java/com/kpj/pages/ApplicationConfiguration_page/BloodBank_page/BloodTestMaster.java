package com.kpj.pages.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Blood Test Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Blood Bank</b> (submenu) → <b>Blood Test Master</b>
 * (route {@code #/BloodTestMaster}) → <b>Add</b> ({@code AddBloodTestMaster()} → {@code #/add-BloodTestMaster})
 * → enter <b>Code</b>, <b>Remark</b>, select <b>Test Result</b> → <b>Submit</b> → toast.</p>
 *
 * <p>Blood Bank screens must be reached via the MENU (direct hash = empty screen).</p>
 */
public class BloodTestMaster extends BasePage {

    public BloodTestMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/BloodTestMaster";
    public static String ADD_ROUTE = "#/add-BloodTestMaster";
    public String lastCode = "";

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
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*blood\\s*bank\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*blood\\s*test\\s*master\\s*$/i.test(norm(x.textContent)) || /bloodtestmaster/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__btmMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__btmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BloodTestMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1800);
        }
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("bloodtestmaster");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button ({@code AddBloodTestMaster()}). Lands on {@code #/add-BloodTestMaster}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddBloodTestMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__btmAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("BloodTestMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__btmAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("BloodTestMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='bloodtest.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("BloodTestMaster.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    /**
     * Realistic blood test names — not generic filler text; only the Code stays a generated unique value.
     */
    private static final String[] TEST_NAMES = {
            "HIV Antibody", "Hepatitis B Surface Antigen", "Hepatitis C Antibody", "Syphilis (VDRL)",
            "Malaria Antigen", "ABO Grouping", "Rh Typing", "Direct Antiglobulin Test",
            "Antibody Screening", "Crossmatch", "HTLV Antibody", "NAT Screening"
    };

    /**
     * Enter Code, Remark, select Test Result. {@code attempt} shifts the code and remark so a retry after an
     * "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "TM" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        String desc = TEST_NAMES[attempt % TEST_NAMES.length] + (attempt >= TEST_NAMES.length ? " " + (attempt / TEST_NAMES.length + 1) : "");
        setNg("bloodtest.Code", code);
        setNg("bloodtest.Description", desc);
        waitForAngular(300);
        String test = "(no-opt)";
        String sel = "select[ng-model='bloodtest.TestID']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(10000));
            Object idx = page.evaluate("(s) => { const e=document.querySelector(s); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
            int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            waitForAngular(400);
            test = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("fillDetails: Test Result select failed - " + e.getMessage()); }
        return "Code=" + code + " | Remark=" + desc + " | TestResult=" + test;
    }

    /** Click <b>Submit</b> and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__btToasts=[]; if(window.__btObs) window.__btObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__btToasts.includes(t)) window.__btToasts.push(t); }); };"
                + " window.__btObs=new MutationObserver(grab); window.__btObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/BloodTest/i.test(x.getAttribute('ng-click')||'') && /IUD|save|submit/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__btmSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__btmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__btToasts||[]).some(a=>/blood|test|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__btToasts||[]).includes(t)) (window.__btToasts=window.__btToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__btToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
