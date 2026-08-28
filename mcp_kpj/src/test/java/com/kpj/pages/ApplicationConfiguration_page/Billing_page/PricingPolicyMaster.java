package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Pricing Policy Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Pricing Policy Master</b> (route
 * {@code #/tariff}) → <b>Add</b> ({@code #/add-tariff}) → enter <b>Code</b>, <b>Remark</b>, tick a
 * <b>Location Details</b> checkbox → select <b>Class</b>, <b>Group</b>, <b>Subgroup</b>, enter <b>Discount</b> →
 * click <b>Add</b> ({@code AddTariffDetails}) → <b>Submit</b> ({@code fnIUDtariffDetails('Discount')}) → toast.</p>
 *
 * <p>Same {@code #/add-tariff} form as [[devhis-price-revision-applicability]] (that one is reached via
 * "Price Revision/ Applicability" → {@code #/ServiceApplicableList}); this is reached via "Pricing Policy Master"
 * → {@code #/tariff}. Here the full detail line (Class/Group/Subgroup/Discount + Add) is exercised.</p>
 */
public class PricingPolicyMaster extends BasePage {

    public PricingPolicyMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/tariff";
    public static String ADD_ROUTE = "#/add-tariff";
    public String lastCode = "";
    public String lastRemark = "";

    /** Realistic Remark values — a fresh one is picked per retry attempt after "already exists". */
    private static final String[] REMARKS = {
            "Standard Pricing Policy", "Corporate Discount Policy", "Insurance Panel Pricing", "Government Scheme Pricing",
            "Staff Concession Policy", "Senior Citizen Discount Policy", "Promotional Pricing Policy",
            "Package Rate Policy", "Special Panel Pricing", "Annual Tariff Policy"
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
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*pricing\\s*policy\\s*master\\s*$/i.test(norm(x.textContent)) || /#\\/tariff\\b/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__ppMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__ppMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("PricingPolicyMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("PricingPolicyMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        String u = page.url().toLowerCase();
        return (u.contains("#/tariff") || u.contains("add-tariff")) && !u.contains("serviceapplicable");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-tariff}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__ppAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("PricingPolicyMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__ppAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("PricingPolicyMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            // wait for the Code input, a Location checkbox, AND Class/Group options (all load async)
            page.waitForFunction("() => { const code=[...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='tariffDetails.Code');"
                    + " const loc=[...document.querySelectorAll(\"input[type=checkbox][ng-model='loc.IsSelected']\")].some(e=>e.offsetParent!==null);"
                    + " const hasOpt=ng=>{ const e=document.querySelector(\"select[ng-model='\"+ng+\"']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); };"
                    + " return code && loc && hasOpt('tariffDetails.classid') && hasOpt('tariffDetails.groupid'); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(18000));
        } catch (Exception ignore) { System.out.println("PricingPolicyMaster.clickAdd: add form/options not fully ready"); }
        waitForAngular(1000);
        return true;
    }

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    private int selectableCount(String selNg) {
        Object c = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return 0; return [...e.options].filter(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())).length; }",
                "select[ng-model='" + selNg + "']");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    private String selectOrdinal(String selNg, int ordinal) {
        String sel = "select[ng-model='" + selNg + "']";
        Object idx = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return -1; const reals=[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && (x.o.textContent||'').trim() && !/^-*\\s*select/i.test((x.o.textContent||'').trim())); return reals.length>=a.ord ? reals[a.ord-1].i : -1; }",
                java.util.Map.of("s", sel, "ord", ordinal));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-opt)";
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("selectOrdinal " + selNg + " failed: " + e.getMessage()); return "(err)"; }
        waitForAngular(500);
        return chosen;
    }

    /** Enter Code + Remark and tick a Location Details checkbox. {@code attempt} shifts BOTH Code and Remark
     *  so a retry after an "already exists" toast submits genuinely different details. */
    public String fillHeader(int attempt) {
        String code = "PP" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        setNg("tariffDetails.Code", code);
        setNg("tariffDetails.Description", lastRemark);
        waitForAngular(200);
        String loc = "(none)";
        Object tagged = page.evaluate("() => { const cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='loc.IsSelected']\")].find(x=>x.offsetParent!==null && !x.checked); if(!cb) return false; cb.id='__ppLoc'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__ppLoc").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(4000)); loc = "ticked 1 location"; }
            catch (Exception e) { try { page.locator("#__ppLoc").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); loc = "clicked 1 location"; } catch (Exception e2) { System.out.println("fillHeader: location tick failed - " + e2.getMessage()); } }
            page.evaluate("() => { const e=document.getElementById('__ppLoc'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(300);
        return "Code=" + code + " | Remark=" + lastRemark + " | Location=" + loc;
    }

    /** Select Class, then search Group→Subgroup for a group that has subgroups, set Discount, click <b>Add</b>
     *  ({@code AddTariffDetails}) to add the detail line. */
    public String addDetailLine() {
        String klass = selectOrdinal("tariffDetails.classid", 1);
        // Group (first real). Subgroup is OPTIONAL on this form (loads for some groups only) — best-effort.
        String group = selectOrdinal("tariffDetails.groupid", 1);
        waitForAngular(700);
        String sub;
        try {
            page.waitForFunction("() => { const e=document.querySelector(\"select[ng-model='tariffDetails.subgroupid']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(4000));
        } catch (Exception ignore) { }
        if (selectableCount("tariffDetails.subgroupid") > 0) sub = selectOrdinal("tariffDetails.subgroupid", 1);
        else sub = "[optional-none]";
        setNg("tariffDetails.discount", "5");
        waitForAngular(300);
        int before = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        page.evaluate("() => { window.__ppToasts=[]; if(window.__ppObs) window.__ppObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ppToasts.includes(t)) window.__ppToasts.push(t); }); };"
                + " window.__ppObs=new MutationObserver(grab); window.__ppObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddTariffDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object toast = page.evaluate("() => { const a=window.__ppToasts||[]; return a.find(x=>/tariff|detail|added|success|please|select|enter|discount|group|class/i.test(x)) || a[0] || ''; }");
        String t = toast == null ? "" : toast.toString().trim();
        int after = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        String rowMsg = (after > before) ? "(detail row added)" : (t.isEmpty() ? "(no row/toast)" : t);
        return "Class=" + klass + " | Group=" + group + " | Subgroup=" + sub + " | Discount=5 | " + rowMsg;
    }

    /** Click <b>Submit</b> ({@code fnIUDtariffDetails}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__ppToasts=[]; if(window.__ppObs) window.__ppObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__ppToasts.includes(t)) window.__ppToasts.push(t); }); };"
                + " window.__ppObs=new MutationObserver(grab); window.__ppObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDtariffDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__ppSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__ppSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__ppToasts||[]).some(a=>/tariff|pricing|policy|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__ppToasts||[]).includes(t)) (window.__ppToasts=window.__ppToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__ppToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
