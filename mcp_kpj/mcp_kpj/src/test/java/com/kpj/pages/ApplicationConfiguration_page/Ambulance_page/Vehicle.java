package com.kpj.pages.ApplicationConfiguration_page.Ambulance_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Ambulance &gt; <b>Vehicle</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Ambulance</b> (submenu) → <b>Vehicle</b> (route
 * {@code #/Vehicle}) → <b>Add</b> ({@code #/add-Vehicle}) → enter <b>Vehicle Type</b>, <b>Vehicle Name</b>,
 * <b>Vehicle Make</b>, <b>Vehicle No</b>, <b>Chassis No</b>, <b>Invoice No</b>, <b>Purchase Amount</b> →
 * <b>Submit</b> ({@code fnIUDVehicle()}) → toast.</p>
 *
 * <p>Fields (discovered): {@code Vehicle.vehicleTypeID} (select), {@code Vehicle.vehiclename},
 * {@code Vehicle.vehiclemake}, {@code Vehicle.vehicleno}, {@code Vehicle.chasyno},
 * {@code Vehicle1.invoiceno}, {@code Vehicle1.purchaseamount}. The payment mode is a
 * {@code Vehicle.cash_lease_donated} radio (Cash / Lease / Donated) — under <b>Cash</b> the Invoice No,
 * Purchase Date and Purchase Amount fields are the visible ones (each ng-model also has a hidden Lease/Donated
 * twin, so always target the visible element). Vehicle Location and Purchase Date are filled defensively too.</p>
 */
public class Vehicle extends BasePage {

    public Vehicle(Page page) { super(page); }

    public static String LIST_ROUTE = "#/Vehicle";
    public static String ADD_ROUTE = "#/add-Vehicle";
    public String lastVehicleNo = "";
    public String lastVehicleName = "";

    /** Realistic ambulance/vehicle names — a Name reads as data a person would type, not "Auto <thing> <CODE>". */
    private static final String[] NAMES = {
            "City Care Ambulance", "Rapid Response Unit", "Mercy Transport Van", "Community Ambulance Service",
            "Sunrise Medical Transport", "Regional Emergency Ambulance", "Coastal Patient Transport",
            "Riverside Ambulance Unit"
    };

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        // Application Configuration
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // Ambulance (submenu parent, href=#) → expand
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*ambulance\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        // Vehicle child link (route #/Vehicle) — real click + capture route
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*vehicle\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && /#\\/vehicle\\b/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__vehMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__vehMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Vehicle.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("Vehicle.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("vehicle");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls — renders after the grid). Lands on {@code #/add-Vehicle}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__vehAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("Vehicle.clickAdd: Add button not found"); return false; }
        try { page.locator("#__vehAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("Vehicle.clickAdd: click failed - " + e.getMessage()); }
        // wait for the add form (Vehicle.vehicleno input present)
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='Vehicle.vehicleno')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("Vehicle.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /** Enter Vehicle Type (select → first real option), Vehicle Name/Make/No, Chassis No, Invoice No,
     *  Purchase Amount (+ Vehicle Location + Purchase Date defensively). Targets VISIBLE fields only.
     *  {@code attempt} shifts the Vehicle No and Vehicle Name so a retry after an "already exists" toast
     *  submits genuinely different details. */
    public String fillDetails(int attempt) {
        String no = "VH" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastVehicleNo = no;
        lastVehicleName = NAMES[attempt % NAMES.length] + (attempt >= NAMES.length ? " " + (attempt / NAMES.length + 1) : "");
        Object r = page.evaluate("(a) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const vis=e=>e && e.offsetParent!==null;"
                + " const byNg=ng=>[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(vis);"
                + " const setInp=(ng,v)=>{ const e=byNg(ng); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const setSelPoll=async(ng)=>{ const e=byNg(ng); if(!e) return '(no)'; for(let k=0;k<12;k++){ const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i>=0){ e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} return norm(e.options[i].textContent); } await sleep(400); } return '(no-opt)'; };"
                // ensure the Cash payment radio is selected so Invoice/Amount are the active (visible) fields
                + " const cash=[...document.querySelectorAll(\"input[type=radio][ng-model='Vehicle.cash_lease_donated']\")].find(vis); if(cash && !cash.checked){ cash.click(); } await sleep(400);"
                + " const vt=await setSelPoll('Vehicle.vehicleTypeID');"
                + " const nm=setInp('Vehicle.vehiclename',a.name); const mk=setInp('Vehicle.vehiclemake','Toyota');"
                + " const vn=setInp('Vehicle.vehicleno',a.no); const ch=setInp('Vehicle.chasyno','CH'+a.no);"
                + " const loc=setInp('Vehicle.vehiclelocation','Main'); const pd=setInp('Vehicle.purchasedate','01/01/2026');"
                + " const inv=setInp('Vehicle1.invoiceno','INV'+a.no); const amt=setInp('Vehicle1.purchaseamount','50000');"
                + " resolve('VehicleType='+vt+' | Name='+nm+' | Make='+mk+' | No='+vn+' | Chassis='+ch+' | Invoice='+inv+' | Amount='+amt+' | Location='+loc+' | PurchaseDate='+pd); })",
                java.util.Map.of("no", no, "name", lastVehicleName));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> ({@code fnIUDVehicle()}) and return the toast. */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__vehToasts=[]; if(window.__vehObs) window.__vehObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__vehToasts.includes(t)) window.__vehToasts.push(t); }); };"
                + " window.__vehObs=new MutationObserver(grab); window.__vehObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>x.getAttribute('ng-click')==='fnIUDVehicle()' && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__vehSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__vehSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__vehToasts||[]).some(a=>/vehicle|saved|success|added|updated|please|fill|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__vehToasts||[]).includes(t)) (window.__vehToasts=window.__vehToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__vehToasts||[]; return a.find(x=>/vehicle.*(saved|added)|saved successfully|added successfully|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
