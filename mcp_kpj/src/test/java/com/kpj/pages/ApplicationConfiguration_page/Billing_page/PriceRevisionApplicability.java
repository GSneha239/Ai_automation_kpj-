package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Price Revision/ Applicability</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Price Revision/ Applicability</b> (route
 * {@code #/ServiceApplicableList}) → <b>Add</b> ({@code #/add-tariff}) → enter <b>Code</b>, <b>Remark</b>
 * (+ tick a mandatory <b>Location</b>) → <b>Submit</b> ({@code fnIUDtariffDetails('Discount')}) → toast.</p>
 *
 * <p>Fields: {@code tariffDetails.Code}, {@code tariffDetails.Description} (Remark). <b>Location Details*</b> is
 * mandatory — tick a {@code loc.IsSelected} checkbox (not in the user's field list but required for the save).
 * Base Pricing Policy / Class / Group / Subgroup / Discount% are optional.</p>
 */
public class PriceRevisionApplicability extends BasePage {

    public PriceRevisionApplicability(Page page) { super(page); }

    public static String LIST_ROUTE = "#/ServiceApplicableList";
    public static String ADD_ROUTE = "#/add-tariff";
    public String lastCode = "";
    public String lastRemark = "";

    /** Realistic Remark values — a fresh one is picked per retry attempt after "already exists". */
    private static final String[] REMARKS = {
            "Standard Tariff Revision", "Annual Price Update", "Insurance Tariff Schedule", "Corporate Rate Revision",
            "Panel Price Adjustment", "Government Scheme Tariff", "Self-Pay Rate Update", "Special Package Pricing",
            "Promotional Rate Schedule", "Quarterly Price Review"
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
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/price\\s*revision|applicab/i.test(norm(x.textContent)) || /serviceapplicable/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__prMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__prMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("PriceRevision.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("PriceRevision.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("serviceapplicable");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-tariff}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__prAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("PriceRevision.clickAdd: Add button not found"); return false; }
        try { page.locator("#__prAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("PriceRevision.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='tariffDetails.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("PriceRevision.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /** Enter Code + Remark, and tick a mandatory Location checkbox. Returns a summary. {@code attempt} shifts
     *  BOTH Code and Remark so a retry after an "already exists" toast submits genuinely different details. */
    public String fillDetails(int attempt) {
        String code = "PR" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('tariffDetails.Code',a.code); set('tariffDetails.Description',a.remark); }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(300);
        // Location Details* — tick the first location checkbox (real click so ng-change fires).
        String loc = "(none)";
        Object tagged = page.evaluate("() => { const cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='loc.IsSelected']\")].find(x=>x.offsetParent!==null && !x.checked); if(!cb) return false; cb.id='__prLoc'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__prLoc").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); loc = "ticked 1 location"; }
            catch (Exception e) { System.out.println("fillDetails: location tick failed - " + e.getMessage()); }
            page.evaluate("() => { const e=document.getElementById('__prLoc'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(300);
        return "Code=" + code + " | Remark=" + lastRemark + " | Location=" + loc;
    }

    /** Click <b>Submit</b> ({@code fnIUDtariffDetails}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__prToasts=[]; if(window.__prObs) window.__prObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__prToasts.includes(t)) window.__prToasts.push(t); }); };"
                + " window.__prObs=new MutationObserver(grab); window.__prObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDtariffDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__prSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__prSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__prToasts||[]).some(a=>/tariff|price|revision|applicab|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__prToasts||[]).includes(t)) (window.__prToasts=window.__prToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__prToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** After Submit, open the list ({@code #/ServiceApplicableList}) and confirm a grid row matches the saved Code. */
    public String findAddedInList() {
        if (!onScreen() || page.url().toLowerCase().contains("add-")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, table tr')].length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        // if there's a column filter, type the code to load the row
        page.evaluate("(code) => { const f=document.querySelector('.ui-grid-filter-input'); if(f){ const c=window.angular&&angular.element(f).controller('ngModel'); f.value=code; if(c){c.$setViewValue(code);c.$render();} f.dispatchEvent(new Event('input',{bubbles:true})); f.dispatchEvent(new Event('keyup',{bubbles:true})); f.dispatchEvent(new Event('change',{bubbles:true})); } }", lastCode);
        waitForAngular(1200);
        try {
            page.waitForFunction("(code) => { const up=s=>(s||'').toUpperCase(); const cc=up(code); return [...document.querySelectorAll('.ui-grid-row, table tbody tr')].some(r=>up(r.textContent||'').includes(cc)); }",
                    lastCode, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(600);
        Object r = page.evaluate("(code) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase(); const cc=up(code);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tr')];"
                + " for(const row of rows){ const cells=[...row.querySelectorAll('.ui-grid-cell-contents, td')].map(c=>up(c.textContent)); if(cells.some(c=>c===cc || c.includes(cc))) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                + " let found=''; document.querySelectorAll('*').forEach(el=>{ if(found) return; try{ const s=angular.element(el).scope(); const d=(s&&s.grid&&s.grid.options&&s.grid.options.data)||(s&&s.gridOptions&&s.gridOptions.data); if(d){ const m=d.find(x=>JSON.stringify(x).toUpperCase().includes(cc)); if(m) found=Object.values(m).filter(v=>v!=null && typeof v!=='object').join(' | ').slice(0,160); } }catch(e){} }); return found; }", lastCode);
        return r == null ? "" : r.toString().trim();
    }
}
