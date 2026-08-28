package com.kpj.pages.ApplicationConfiguration_page.CSSD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; CSSD Configuration &gt; <b>Sterilization Type</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>CSSD Configuration</b> (submenu) → <b>Sterilization Type</b>
 * (route {@code #/SterilizationType}) → enter <b>Code</b>, <b>Remark</b>, <b>Alert Threshold (%)</b>,
 * <b>Standard TAT (Mins)</b> → <b>Submit</b> → toast. INLINE-ADD screen (inputs on the list page, no Add button).</p>
 *
 * <p>Fields: {@code commonmaster.Code} (Code), {@code commonmaster.Description} (Remark), {@code inputTime}
 * (Standard TAT (Mins)), {@code commonmaster.threshold} (Alert Threshold (%)).</p>
 */
public class SterilizationType extends BasePage {

    public SterilizationType(Page page) { super(page); }

    public static String LIST_ROUTE = "#/SterilizationType";
    public String lastCode = "", lastRemark = "";

    /** Realistic sterilization-type names, not "Auto sterilization type &lt;code&gt;" filler — cycled per retry attempt. */
    private static final String[] REMARKS = {
            "Steam Sterilization", "ETO Sterilization", "Plasma Sterilization", "Dry Heat Sterilization",
            "Low Temperature Sterilization", "Formaldehyde Sterilization", "Radiation Sterilization",
            "Chemical Sterilization", "Ozone Sterilization", "Flash Sterilization"
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
                + " const a=[...document.querySelectorAll('a')].find(x=>/cssd/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*sterilization\\s*type\\s*$/i.test(norm(x.textContent)) || /sterilizationtype/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__stMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__stMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("SterilizationType.nav: click failed - " + e.getMessage()); }
            waitForAngular(1800);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='commonmaster.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("SterilizationType.nav: Code input not ready"); }
        waitForAngular(600);
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("sterilizationtype");
    }

    // ---- actions ---------------------------------------------------------

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    /**
     * Enter Code, Remark, Standard TAT (Mins), Alert Threshold (%). {@code attempt} shifts Code and Remark so a
     * retry after an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "ST" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        setNg("commonmaster.Code", code);
        setNg("commonmaster.Description", lastRemark);
        // Standard TAT (Mins) = the scope var `inputTime` (NOT in commonmaster). Real fill + set the scope var
        // directly (else Submit → "Standard TAT is mandatory!").
        try {
            com.microsoft.playwright.Locator tat = page.locator("[ng-model='inputTime']").first();
            tat.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            tat.fill("30");
            tat.blur();
        } catch (Exception e) { System.out.println("fillDetails: TAT fill failed - " + e.getMessage()); }
        setScopeInputTime("30");
        setNg("commonmaster.threshold", "80"); // Alert Threshold (%)
        waitForAngular(400);
        return "Code=" + code + " | Remark=" + lastRemark + " | StandardTAT=30 | AlertThreshold=80";
    }

    /** Set the scope var {@code inputTime} (Standard TAT) on every scope that has it, then $apply. */
    private void setScopeInputTime(String v) {
        page.evaluate("(v) => { let sc0=null; document.querySelectorAll('*').forEach(el=>{ try{ let s=angular.element(el).scope(); for(let i=0;i<20&&s;i++){ if(Object.prototype.hasOwnProperty.call(s,'inputTime')){ s.inputTime=v; if(!sc0) sc0=s; } s=s.$parent; } }catch(e){} }); if(sc0){ try{ sc0.$apply(); }catch(e){} } }", v);
        waitForAngular(200);
    }

    /** Click <b>Submit</b> (commonmaster save handler / text Submit) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        setScopeInputTime("30"); // re-assert Standard TAT right before Submit
        Object tagged = page.evaluate("() => { window.__stToasts=[]; if(window.__stObs) window.__stObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__stToasts.includes(t)) window.__stToasts.push(t); }); };"
                + " window.__stObs=new MutationObserver(grab); window.__stObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const cand=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " let b=cand.find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim())) || cand.find(x=>/^\\s*save\\s*$/i.test((x.textContent||x.value||'').trim()))"
                + " || cand.find(x=>/IUD|SaveCommon|fnAdd|fnSave|Submit/i.test(x.getAttribute('ng-click')||'') && !/clear|cancel|openMic|pagination|edit/i.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__stSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__stSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__stToasts||[]).some(a=>/sterilization|master|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__stToasts||[]).includes(t)) (window.__stToasts=window.__stToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__stToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
