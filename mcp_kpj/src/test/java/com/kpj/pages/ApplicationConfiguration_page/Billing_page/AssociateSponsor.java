package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Associate Sponsor</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> (submenu) → <b>Associate Sponsor</b> (route
 * {@code #/associatereceivableMaster}) → <b>Add</b> ({@code #/add-associatereceivable}) → select
 * <b>Location</b>, <b>Pricing Policy</b>, <b>Payor</b>, <b>Associate Sponsor</b> → <b>Submit</b> → toast.</p>
 *
 * <p>The four fields are a CASCADE of {@code <select>}s (each loads after its parent):
 * Location {@code associatereceivable.Locationid} → Pricing Policy {@code associatereceivable.Tariffid} →
 * Payor {@code associatereceivable.Receivableid} → Associate Sponsor {@code associatereceivable.ReceivableAssociateid}.
 * If Submit reports the combination already exists, pick a DIFFERENT Pricing Policy (next Tariff option) and
 * Submit again — {@link #fillCascade(int)} takes the 1-based Tariff ordinal for that retry.</p>
 */
public class AssociateSponsor extends BasePage {

    public AssociateSponsor(Page page) { super(page); }

    public static String LIST_ROUTE = "#/associatereceivableMaster";
    public static String ADD_ROUTE = "#/add-associatereceivable";

    // Values chosen in the last fillCascade — used to locate the new row in the list after saving.
    public String lastLocation = "", lastPricingPolicy = "", lastPayor = "", lastAssociate = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // Billing (submenu parent) → expand
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        // Associate Sponsor child link
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/associate\\s*sponsor/i.test(norm(x.textContent)) || /associatereceivable/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__assocMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__assocMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("AssociateSponsor.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("AssociateSponsor.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("associatereceivable");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-associatereceivable}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__assocAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("AssociateSponsor.clickAdd: Add button not found"); return false; }
        try { page.locator("#__assocAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("AssociateSponsor.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='associatereceivable.Locationid')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("AssociateSponsor.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /** How many real (non "-Select-") Pricing Policy (Tariff) options exist after a Location is chosen. */
    public int tariffOptionCount() {
        // ensure a Location is picked first (Tariff loads after)
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const e=document.querySelector(\"select[ng-model='associatereceivable.Locationid']\"); if(e && (e.selectedIndex<=0 || /^-*\\s*select/i.test(norm((e.options[e.selectedIndex]||{}).text)))){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} } } }");
        waitForAngular(1200);
        Object c = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const e=document.querySelector(\"select[ng-model='associatereceivable.Tariffid']\"); if(!e) return 0; return [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))).length; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Convenience: the {@code tariffOrdinal}-th Pricing Policy with the first Payor / Associate Sponsor. */
    public String fillCascade(int tariffOrdinal) { return fillCascade(tariffOrdinal, 1, 1); }

    /**
     * Fill the cascade choosing the {@code tariffOrdinal}-th Pricing Policy, {@code payorOrdinal}-th Payor and
     * {@code assocOrdinal}-th Associate Sponsor (all 1-based, over the real non "-Select-" options). Location =
     * first real option. Each child list is polled after its parent changes, then set to the requested ordinal —
     * uniqueness is on the whole Location/Tariff/Payor/Associate combination, so every axis has to be steerable
     * (fixing Payor + Associate to the first option only explores one row of the space and hits "already exists").
     * Returns a summary; a field reads {@code (exhausted)} when that ordinal is past the end of its list.
     */
    public String fillCascade(int tariffOrdinal, int payorOrdinal, int assocOrdinal) {
        Object r = page.evaluate("(o) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const sel=ng=>document.querySelector(\"select[ng-model='\"+ng+\"']\");"
                + " const real=e=>[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.textContent)));"
                + " const fire=e=>{ e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} };"
                // Poll for the (async) child list, then FORCE-select the requested ordinal.
                + " const pickPoll=async(ng,ordinal)=>{ for(let k=0;k<15;k++){ const e=sel(ng); if(e){ const rs=real(e); if(rs.length){ if(rs.length<ordinal) return '(exhausted)'; const t=rs[ordinal-1]; e.selectedIndex=t.i; fire(e); return norm(t.o.textContent); } } await sleep(400); } return '(no-opt)'; };"
                + " const loc=await pickPoll('associatereceivable.Locationid', 1); await sleep(1000);"
                + " const tar=await pickPoll('associatereceivable.Tariffid', o.tar); await sleep(1000);"
                + " const pay=await pickPoll('associatereceivable.Receivableid', o.pay); await sleep(800);"
                + " const asc=await pickPoll('associatereceivable.ReceivableAssociateid', o.asc); await sleep(400);"
                + " resolve('Location='+loc+' | PricingPolicy='+tar+' | Payor='+pay+' | AssociateSponsor='+asc); })",
                java.util.Map.of("tar", tariffOrdinal, "pay", payorOrdinal, "asc", assocOrdinal));
        waitForAngular(400);
        String res = r == null ? "" : r.toString();
        lastLocation = grab(res, "Location");
        lastPricingPolicy = grab(res, "PricingPolicy");
        lastPayor = grab(res, "Payor");
        lastAssociate = grab(res, "AssociateSponsor");
        return res;
    }

    private static String grab(String s, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(key + "=([^|]+)").matcher(s);
        return m.find() ? m.group(1).trim() : "";
    }

    /**
     * After a successful Save, open the Associate Sponsor list ({@code #/associatereceivableMaster}) and confirm the
     * new record appears — a grid row whose Location / Sponsor(Payor) / Associate Sponsor / Pricing Policy cells
     * match what was just saved. Returns the matched row text (Location | Sponsor | Associate | PricingPolicy), or "".
     */
    public String findAddedInList() {
        if (!onScreen() || page.url().toLowerCase().contains("add-")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, table tr')].length > 1",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        Object r = page.evaluate("(v) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const up=s=>norm(s).toUpperCase();"
                + " const loc=up(v.loc), pol=up(v.pol), pay=up(v.pay), asc=up(v.asc);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tr')];"
                + " for(const row of rows){ const cells=[...row.querySelectorAll('.ui-grid-cell-contents, td')].map(c=>up(c.textContent)); const joined=cells.join(' | ');"
                + "   const has=x=>x && cells.some(c=>c===x || c.includes(x));"
                + "   if(has(pol) && has(pay) && has(asc) && (has(loc)||!loc)){ return norm(row.textContent).slice(0,180); } }"
                + " return ''; }", java.util.Map.of("loc", lastLocation, "pol", lastPricingPolicy, "pay", lastPayor, "asc", lastAssociate));
        return r == null ? "" : r.toString().trim();
    }

    /** Click <b>Submit</b> and return the toast. */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__asToasts=[]; if(window.__asObs) window.__asObs.disconnect();"
                // clear any lingering toast from a previous attempt so the captured toast is the CURRENT submit's.
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__asToasts.includes(t)) window.__asToasts.push(t); }); };"
                + " window.__asObs=new MutationObserver(grab); window.__asObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__assocSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__assocSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__asToasts||[]).some(a=>/associate|receivable|sponsor|saved|success|added|updated|please|select|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__asToasts||[]).includes(t)) (window.__asToasts=window.__asToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__asToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
