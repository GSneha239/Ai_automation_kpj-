package com.kpj.pages.ApplicationConfiguration_page.CSSD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Dialysis Configuration &gt; <b>Dialysis Measure</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Dialysis Configuration</b> (submenu) → <b>Dialysis Measure</b>
 * (route {@code #/DialysisMeasure}) → enter <b>Code</b>, <b>Remark</b> → <b>Submit</b> → toast. Sibling of
 * {@link DialysisParameter} — same submenu, same INLINE-ADD commonmaster shape (Submit + Clear on the list
 * page, no Add button).</p>
 *
 * <p>Fields: {@code commonmaster.Code} (Code), {@code commonmaster.Description} (Remark). (A {@code StoreId}
 * dropdown may render — filled if it has a real option.)</p>
 */
public class DialysisMeasure extends BasePage {

    public DialysisMeasure(Page page) { super(page); }

    public static String LIST_ROUTE = "#/DialysisMeasure";
    public String lastCode = "", lastRemark = "";

    /** Realistic dialysis-measure names, not "Auto measure &lt;code&gt;" filler — cycled per retry attempt. */
    private static final String[] REMARKS = {
            "Blood Pressure", "Pulse Rate", "Body Temperature", "Body Weight", "Ultrafiltration Volume",
            "Blood Flow Rate", "Dialysate Flow Rate", "Conductivity", "Venous Pressure", "Arterial Pressure"
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
                + " const a=[...document.querySelectorAll('a')].find(x=>/dialysis/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*dialysis\\s*measure\\s*$/i.test(norm(x.textContent)) || /dialysismeasure/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__dmMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__dmMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("DialysisMeasure.nav: click failed - " + e.getMessage()); }
            waitForAngular(2000);
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='commonmaster.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("DialysisMeasure.nav: Code input not ready"); }
        waitForAngular(800);
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("dialysismeasure");
    }

    // ---- actions ---------------------------------------------------------

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    /**
     * Enter Code, Remark (+ pick a Store option if the dropdown has a real one). {@code attempt} shifts BOTH
     * values so a retry after an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "DM" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        setNg("commonmaster.Code", code);
        setNg("commonmaster.Description", lastRemark);
        // Store dropdown — select a real option if present (may be required).
        String store = "(n/a)";
        try {
            Object has = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='StoreId']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }");
            if (Boolean.TRUE.equals(has)) {
                Object idx = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='StoreId']\"); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }");
                int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
                page.locator("select[ng-model='StoreId']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
                store = String.valueOf(page.evaluate("() => { const e=document.querySelector(\"select[ng-model='StoreId']\"); return (e.options[e.selectedIndex]||{}).textContent||''; }")).trim();
            }
        } catch (Exception ignore) { }
        waitForAngular(400);
        return "Code=" + code + " | Remark=" + lastRemark + " | Store=" + store;
    }

    /** Click <b>Submit</b> (text Submit / commonmaster save handler) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__dmToasts=[]; if(window.__dmObs) window.__dmObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__dmToasts.includes(t)) window.__dmToasts.push(t); }); };"
                + " window.__dmObs=new MutationObserver(grab); window.__dmObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const cand=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null);"
                + " let b=cand.find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim())) || cand.find(x=>/^\\s*save\\s*$/i.test((x.textContent||x.value||'').trim()))"
                + " || cand.find(x=>/IUD|SaveCommon|fnAdd|fnSave|Submit/i.test(x.getAttribute('ng-click')||'') && !/clear|cancel|openMic|pagination|edit/i.test(x.getAttribute('ng-click')||'')); if(!b) return false; b.id='__dmSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__dmSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__dmToasts||[]).some(a=>/dialysis|measure|master|saved|success|added|updated|please|enter|select|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__dmToasts||[]).includes(t)) (window.__dmToasts=window.__dmToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__dmToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
