package com.kpj.pages.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Blood Bag Component</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Blood Bank</b> (submenu) → <b>Blood Bag Component</b>
 * (route {@code #/BloodBagComponent}) → <b>Add</b> ({@code AddBloodBagComponent()} → {@code #/add-BloodBagComponent})
 * → enter <b>Code</b>, <b>Remark</b>, <b>Expiry</b>, <b>Quantity</b>, select <b>Service</b> +
 * <b>Cross Match Service</b> → <b>Submit</b> → toast.</p>
 *
 * <p>NOTE: this screen must be reached via the MENU — a direct hash navigation leaves the controller
 * uninitialised (empty screen, no Add button). {@code ServiceID} is a HUGE dropdown (~7868 opts) that loads async.</p>
 */
public class BloodBagComponent extends BasePage {

    public BloodBagComponent(Page page) { super(page); }

    public static String LIST_ROUTE = "#/BloodBagComponent";
    public static String ADD_ROUTE = "#/add-BloodBagComponent";
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
        // Blood Bank submenu parent → expand
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*blood\\s*bank\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/blood\\s*bag\\s*component/i.test(norm(x.textContent)) || /bloodbagcomponent/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__bbcMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__bbcMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("BloodBagComponent.nav: click failed - " + e.getMessage()); }
            waitForAngular(1800);
        }
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("bloodbagcomponent");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button ({@code AddBloodBagComponent()}). Lands on {@code #/add-BloodBagComponent}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddBloodBagComponent/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__bbcAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("BloodBagComponent.clickAdd: Add button not found"); return false; }
        try { page.locator("#__bbcAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("BloodBagComponent.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='bloodbagcomponent.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("BloodBagComponent.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    /** Real selectOption of the first real option of {@code selNg} (polls — the Service list loads async & is huge). */
    private String realSelectFirst(String selNg, int timeoutMs) {
        String sel = "select[ng-model='" + selNg + "']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception e) { return "(no-opt)"; }
        Object idx = page.evaluate("(s) => { const e=document.querySelector(s); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", sel);
        int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
        String chosen = "(no)";
        try {
            page.locator(sel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            chosen = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("realSelectFirst " + selNg + " failed: " + e.getMessage()); }
        waitForAngular(500);
        return chosen;
    }

    /**
     * Realistic blood bag component names (and matching short forms) — not generic filler text; only the Code
     * stays a generated unique value.
     */
    private static final String[] COMPONENT_NAMES = {
            "Packed Red Blood Cells", "Fresh Frozen Plasma", "Platelet Concentrate", "Cryoprecipitate",
            "Whole Blood", "Leukoreduced Red Cells", "Irradiated Red Cells", "Washed Red Cells",
            "Apheresis Platelets", "Granulocyte Concentrate", "Plasma Reduced Platelets", "Single Donor Platelets"
    };
    private static final String[] COMPONENT_SHORT = {
            "PRBC", "FFP", "PC", "Cryo", "WB", "LR-RBC", "Irr-RBC", "Wash-RBC", "Apheresis-PLT", "Gran", "PR-PLT", "SDP"
    };

    /**
     * Enter Code, Remark, Expiry, Quantity, select Service + Cross Match Service. {@code attempt} shifts the
     * code, description and short description so a retry after an "already exists" toast submits genuinely
     * different details.
     */
    public String fillDetails(int attempt) {
        String code = "BC" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        String desc = COMPONENT_NAMES[attempt % COMPONENT_NAMES.length] + (attempt >= COMPONENT_NAMES.length ? " " + (attempt / COMPONENT_NAMES.length + 1) : "");
        String shortDesc = COMPONENT_SHORT[attempt % COMPONENT_SHORT.length] + (attempt >= COMPONENT_SHORT.length ? " " + (attempt / COMPONENT_SHORT.length + 1) : "");
        setNg("bloodbagcomponent.Code", code);
        setNg("bloodbagcomponent.Description", desc);
        setNg("bloodbagcomponent.ShortDescription", shortDesc);
        setNg("bloodbagcomponent.Expiry", "35");
        setNg("bloodbagcomponent.Quantity", "1");
        waitForAngular(300);
        String service = realSelectFirst("bloodbagcomponent.ServiceID", 15000);
        waitForAngular(600);
        String cross;
        Object hasCross = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='bloodbagcomponent.CrossmatchServiceID']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }");
        if (Boolean.TRUE.equals(hasCross)) cross = realSelectFirst("bloodbagcomponent.CrossmatchServiceID", 8000);
        else cross = realSelectFirst("bloodbagcomponent.CrossmatchServiceID", 12000);
        return "Code=" + code + " | Remark=" + desc + " | Expiry=35 | Quantity=1 | Service=" + service + " | CrossMatchService=" + cross;
    }

    /** Click <b>Submit</b> ({@code fnIUD*BloodBagComponent} / text Submit) and return the toast (toastr cleared). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__bbToasts=[]; if(window.__bbObs) window.__bbObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__bbToasts.includes(t)) window.__bbToasts.push(t); }); };"
                + " window.__bbObs=new MutationObserver(grab); window.__bbObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/BloodBagComponent/i.test(x.getAttribute('ng-click')||'') && /IUD|save|submit/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__bbSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__bbSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__bbToasts||[]).some(a=>/blood|bag|component|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__bbToasts||[]).includes(t)) (window.__bbToasts=window.__bbToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__bbToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
