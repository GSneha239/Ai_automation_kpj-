package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>SubTax Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>SubTax Master</b> (route
 * {@code #/SubTaxMasterList}) → <b>Add</b> → fill the details → <b>Save</b> → success toast.</p>
 */
public class SubTaxMaster extends BasePage {

    public SubTaxMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/SubTaxMasterList";
    public String lastCode = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*sub\\s*tax(\\s*master)?\\s*$/i.test(norm(x.textContent)) || /#\\/SubTaxMasterList/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__stMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__stMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("SubTaxMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("SubTaxMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("subtaxmaster"); }

    /** Real-click the <b>Add</b> button (polls). */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__stAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("SubTaxMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__stAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("SubTaxMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='SubTaxMaster.code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("SubTaxMaster.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /**
     * Realistic SubTax names — not generic filler text; only the Code stays a generated unique value.
     */
    private static final String[] SUBTAX_NAMES = {
            "State Tax", "Federal Tax", "Local Tax", "Sales Levy", "Consumption Tax",
            "Tourism Levy", "Environmental Levy", "Health Levy", "Education Cess",
            "Infrastructure Cess", "Digital Levy", "Service Charge"
    };

    /**
     * Enter Code, Remark, Base Tax (first real option), Percentage. {@code attempt} shifts BOTH the code and
     * the remark so a retry after an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "STX" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        String remark = SUBTAX_NAMES[attempt % SUBTAX_NAMES.length] + (attempt >= SUBTAX_NAMES.length ? " " + (attempt / SUBTAX_NAMES.length + 1) : "");
        // text fields
        page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }; set('SubTaxMaster.code',a.code); set('SubTaxMaster.description',a.remark); set('SubTaxMaster.percentage','3'); }",
                java.util.Map.of("code", code, "remark", remark));
        // Base Tax* dropdown — first real option
        String baseTax = "(n/a)";
        try {
            String sel = "select[ng-model='SubTaxMaster.basetaxid']";
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(8000));
            Object idx = page.evaluate("(s) => { const e=document.querySelector(s); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
            int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            baseTax = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("fillDetails: Base Tax select failed - " + e.getMessage()); }
        waitForAngular(400);
        return "Code=" + code + " | Remark=" + remark + " | BaseTax=" + baseTax + " | Percentage=3";
    }

    /** Click <b>Save</b> ({@code fnIUDSubTaxMaster}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__stToasts=[]; if(window.__stObs) window.__stObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__stToasts.includes(t)) window.__stToasts.push(t); }); };"
                + " window.__stObs=new MutationObserver(grab); window.__stObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDSubTaxMaster/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__stSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__stSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__stToasts||[]).some(a=>/tax|saved|success|added|updated|please|enter|required|exist|error/i.test(a))",
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
