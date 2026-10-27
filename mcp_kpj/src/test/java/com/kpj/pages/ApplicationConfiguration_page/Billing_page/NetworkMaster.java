package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Network Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Network Master</b> (route
 * {@code #/NetworkMaster}) → <b>Add</b> ({@code #/add-NetworkMaster}) → select <b>Location</b>,
 * <b>Pricing Policy</b>, <b>Payor</b>, <b>Network</b> → <b>Submit</b> ({@code fnIUDNetworkMaster}) → toast.</p>
 *
 * <p>Same cascade shape as {@link AssociateSponsor}: Location {@code network.locationid} → Pricing Policy
 * {@code network.tariffid} → Payor {@code network.receivableid} → Network {@code network.receivableassociateid}
 * (each loads after its parent). On "already exists" pick a different Pricing Policy (next Tariff) and Submit
 * again — {@link #fillCascade(int)} takes the 1-based Tariff ordinal.</p>
 */
public class NetworkMaster extends BasePage {

    public NetworkMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/NetworkMaster";
    public static String ADD_ROUTE = "#/add-NetworkMaster";
    public String lastLocation = "", lastPricingPolicy = "", lastPayor = "", lastNetwork = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        // Open only what is still CLOSED: the sidebar items are toggles, so re-clicking an already-open one collapses it and the
        // link vanishes (then the direct-hash fallback is bounced to the dashboard). All lookups stay inside the Application
        // Configuration entry — "Billing" is also a top-level module, and a "Network Master" link can exist in another module.
        Object href = "";
        for (int step = 0; step < 4; step++) {
            href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=x=>x.offsetParent!==null;"
                    + " document.querySelectorAll('#__nwMenu,#__nwTop,#__nwBilling').forEach(e=>e.removeAttribute('id'));"
                    + " const top=[...document.querySelectorAll('.sidebar-menu > li > a, li > a')].find(x=>/^\\s*application\\s*config/i.test(norm(x.textContent)) && vis(x));"
                    + " if(!top) return 'no-top'; const scope=top.closest('li')||document;"
                    + " const nm=[...scope.querySelectorAll('a[href]')].find(x=>vis(x) && (/^\\s*network\\s*master\\s*$/i.test(norm(x.textContent)) || /networkmaster/i.test(x.getAttribute('href')||'')));"
                    + " if(nm){ nm.id='__nwMenu'; return 'link:'+(nm.getAttribute('href')||''); }"
                    + " const bill=[...scope.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && vis(x) && !(x.getAttribute('href')||'').includes('#/'));"
                    + " if(bill){ bill.id='__nwBilling'; return 'billing'; }"
                    + " top.id='__nwTop'; return 'top'; }");
            String what = String.valueOf(href);
            if (what.startsWith("link:") || "no-top".equals(what)) break;
            String id = "billing".equals(what) ? "#__nwBilling" : "#__nwTop";
            try { page.locator(id).click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("NetworkMaster.nav: " + what + " click failed - " + e.getMessage()); }
            waitForAngular(900);
        }
        if (href != null && String.valueOf(href).startsWith("link:")) {
            try { page.locator("#__nwMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("NetworkMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("NetworkMaster.nav: last menu step = " + href + " | url = " + page.url());
            System.out.println("NetworkMaster.nav: links mentioning 'network' => " + page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " return JSON.stringify([...document.querySelectorAll('a')].filter(a=>/network/i.test(norm(a.textContent))||/network/i.test(a.getAttribute('href')||''))"
                    + "  .map(a=>{ const ch=[]; for(let li=a.closest('li');li;li=li.parentElement&&li.parentElement.closest('li')){ const t=li.querySelector(':scope > a'); if(t&&t!==a) ch.unshift(norm(t.textContent)); }"
                    + "   return norm(a.textContent)+' | '+(a.getAttribute('href')||'')+' | '+ch.join(' > ')+' | vis='+(a.offsetParent!==null); })); }"));
            System.out.println("NetworkMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("networkmaster");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-NetworkMaster}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__nwAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("NetworkMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__nwAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("NetworkMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='network.locationid')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("NetworkMaster.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /** How many real Pricing Policy (Tariff) options exist after a Location is chosen. */
    public int tariffOptionCount() {
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const e=document.querySelector(\"select[ng-model='network.locationid']\"); if(e && (e.selectedIndex<=0 || /^-*\\s*select/i.test(norm((e.options[e.selectedIndex]||{}).text)))){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} } } }");
        waitForAngular(1200);
        Object c = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const e=document.querySelector(\"select[ng-model='network.tariffid']\"); if(!e) return 0; return [...e.options].filter(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))).length; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Fill the cascade choosing the {@code tariffOrdinal}-th (1-based) Pricing Policy, then Payor and Network
     *  (first real of each, re-polled). Location = first real. Returns a summary (contains {@code (exhausted)} if
     *  the ordinal has no option). */
    public String fillCascade(int tariffOrdinal) { return fillCascade(tariffOrdinal, 1, 1); }

    /** Same, but also choosing the {@code payorOrdinal}-th Payor and {@code networkOrdinal}-th Network (1-based) — used once
     *  every Pricing Policy for the first Payor/Network already exists. {@code (exhausted)} marks an ordinal with no option. */
    public String fillCascade(int tariffOrdinal, int payorOrdinal, int networkOrdinal) {
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const ord=a[0], payN=a[1], nwN=a[2]; const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const sel=ng=>document.querySelector(\"select[ng-model='\"+ng+\"']\");"
                + " const real=e=>[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select/i.test(norm(x.o.textContent)));"
                + " const pickIdx=(ng,ordinal)=>{ const e=sel(ng); if(!e) return '(no-el)'; const rs=real(e); if(rs.length<ordinal) return '(exhausted)'; const t=rs[ordinal-1]; e.selectedIndex=t.i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(t.o.textContent); };"
                + " const pickFirstPoll=async(ng)=>{ for(let k=0;k<15;k++){ const e=sel(ng); if(e){ const rs=real(e); if(rs.length){ const cur=e.options[e.selectedIndex]; if(!(cur && cur.value && !/^-*\\s*select/i.test(norm(cur.textContent)))){ e.selectedIndex=rs[0].i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} } return norm((e.options[e.selectedIndex]||{}).textContent); } } await sleep(400); } return '(no-opt)'; };"
                + " const loc=await pickFirstPoll('network.locationid'); await sleep(1000);"
                + " const tar=pickIdx('network.tariffid', ord); await sleep(1000);"
                + " const pickNthPoll=async(ng,n)=>{ if(n<=1) return await pickFirstPoll(ng); for(let k=0;k<15;k++){ const e=sel(ng); if(e){ const rs=real(e); if(rs.length){ if(rs.length<n) return '(exhausted)'; const t=rs[n-1]; e.selectedIndex=t.i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(t.o.textContent); } } await sleep(400); } return '(no-opt)'; };"
                + " const pay=await pickNthPoll('network.receivableid', payN); await sleep(800);"
                + " const nw=pay==='(exhausted)' ? '(skipped)' : await pickNthPoll('network.receivableassociateid', nwN); await sleep(400);"
                + " resolve('Location='+loc+' | PricingPolicy='+tar+' | Payor='+pay+' | Network='+nw); })", java.util.Arrays.asList(tariffOrdinal, payorOrdinal, networkOrdinal));
        waitForAngular(400);
        String res = r == null ? "" : r.toString();
        lastLocation = grab(res, "Location");
        lastPricingPolicy = grab(res, "PricingPolicy");
        lastPayor = grab(res, "Payor");
        lastNetwork = grab(res, "Network");
        return res;
    }

    private static String grab(String s, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(key + "=([^|]+)").matcher(s);
        return m.find() ? m.group(1).trim() : "";
    }

    /** Click <b>Submit</b> ({@code fnIUDNetworkMaster}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__nwToasts=[]; if(window.__nwObs) window.__nwObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__nwToasts.includes(t)) window.__nwToasts.push(t); }); };"
                + " window.__nwObs=new MutationObserver(grab); window.__nwObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDNetworkMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__nwSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__nwSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__nwToasts||[]).some(a=>/network|receivable|saved|success|added|updated|please|select|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__nwToasts||[]).includes(t)) (window.__nwToasts=window.__nwToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__nwToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** After Submit, open the list ({@code #/NetworkMaster}) and confirm a grid row matches the saved Location /
     *  Payor / Network / Pricing Policy. Returns the matched row text, or "". */
    public String findAddedInList() {
        if (!onScreen() || page.url().toLowerCase().contains("add-")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, table tr')].length > 1",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        Object r = page.evaluate("(v) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase();"
                + " const loc=up(v.loc), pol=up(v.pol), pay=up(v.pay), nw=up(v.nw);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tr')];"
                + " for(const row of rows){ const cells=[...row.querySelectorAll('.ui-grid-cell-contents, td')].map(c=>up(c.textContent)); const has=x=>x && cells.some(c=>c===x || c.includes(x));"
                + "   if(has(pol) && has(pay) && has(nw) && (has(loc)||!loc)) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                + " return ''; }", java.util.Map.of("loc", lastLocation, "pol", lastPricingPolicy, "pay", lastPayor, "nw", lastNetwork));
        return r == null ? "" : r.toString().trim();
    }
}
