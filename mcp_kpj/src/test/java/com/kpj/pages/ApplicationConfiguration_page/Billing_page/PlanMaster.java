package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Plan</b> (Plan Master) — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Plan</b> (route {@code #/PlanMaster}) →
 * <b>Add</b> ({@code #/add-PlanMaster}) → enter <b>Location</b>, <b>Plan Code</b>, <b>Plan Name</b>,
 * <b>Pricing Policy</b>, <b>Group</b>, <b>Sub Group</b>, <b>Services</b>, <b>Class</b>, <b>Co-Pay(%)</b>,
 * <b>Discount</b> → click <b>Add</b> ({@code fnAddListOfServices}) to add the detail line → <b>Submit</b>
 * ({@code fnIUDPlanMaster}) → toast.</p>
 *
 * <p>Fields: {@code PlanMaster.LocationID}, {@code .Code}, {@code .Description}, {@code .TariffID} (Pricing Policy),
 * cascade {@code .groupid}→{@code .subgroupid}→{@code .serviceitemid} (real selectOption fires ng-change; not every
 * Group has Sub Groups/Services so the cascade is searched), {@code .classid}, {@code .copayper}, {@code .discper}.</p>
 */
public class PlanMaster extends BasePage {

    public PlanMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/PlanMaster";
    public static String ADD_ROUTE = "#/add-PlanMaster";
    public String lastCode = "";
    public String lastName = "";
    // Captured header MODEL values (LocationID / TariffID) — re-applied before Submit since Add clears the header.
    private Object locModel = null, tarModel = null;

    /** Realistic Plan names — a fresh one is picked per retry attempt after "already exists". */
    private static final String[] PLAN_NAMES = {
            "Executive Health Plan", "Standard Medical Plan", "Premium Wellness Plan", "Basic Coverage Plan",
            "Family Care Plan", "Corporate Health Plan", "Senior Citizen Plan", "Maternity Care Plan",
            "Comprehensive Health Plan", "Employee Benefit Plan"
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
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*plan\\s*$/i.test(norm(x.textContent)) && /planmaster/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__planMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__planMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("PlanMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("PlanMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("planmaster");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-PlanMaster}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__planAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("PlanMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__planAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("PlanMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='PlanMaster.Code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("PlanMaster.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /** Enter Location, Plan Code, Plan Name, Pricing Policy; search Group→SubGroup→Service cascade; Class;
     *  Co-Pay(%) + Discount. Returns a summary. {@code attempt} shifts BOTH Plan Code and Plan Name so a retry
     *  after an "already exists" toast submits genuinely different details. */
    public String fillDetails(int attempt) {
        String code = "PL" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastName = PLAN_NAMES[attempt % PLAN_NAMES.length] + (attempt >= PLAN_NAMES.length ? " " + (attempt / PLAN_NAMES.length + 1) : "");
        // Text fields.
        page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('PlanMaster.Code',a.code); set('PlanMaster.Description',a.name); }",
                java.util.Map.of("code", code, "name", lastName));
        waitForAngular(300);
        // Location + Pricing Policy: select and VERIFY the model committed (ng-options can race — the option renders
        // but the model stays null); retry until PlanMaster.LocationID / .TariffID are set. Capture the values so
        // reassertHeader can force them back before Submit.
        String location = "(no-opt)", pricing = "(no-opt)";
        for (int a = 0; a < 5; a++) {
            pollHasReal("PlanMaster.LocationID", 10000);
            location = selectRealOrdinal("PlanMaster.LocationID", 1);
            locModel = readModel("LocationID");
            if (locModel != null) break;
            waitForAngular(700);
        }
        for (int a = 0; a < 5; a++) {
            pollHasReal("PlanMaster.TariffID", 8000);
            pricing = selectRealOrdinal("PlanMaster.TariffID", 1);
            tarModel = readModel("TariffID");
            if (tarModel != null) break;
            waitForAngular(700);
        }
        waitForAngular(400);
        // Group (required). Sub Group is OPTIONAL (loads for some groups only). Services is INDEPENDENT (always has
        // its own options). Class (required). No cascade search needed.
        String group = selectRealOrdinal("PlanMaster.groupid", 1);
        waitForAngular(700);
        String subgroup;
        pollHasReal("PlanMaster.subgroupid", 4000);
        if (realCount("PlanMaster.subgroupid") > 0) subgroup = selectRealOrdinal("PlanMaster.subgroupid", 1);
        else subgroup = "[optional-skip]";
        waitForAngular(400);
        String service = selectRealOrdinal("PlanMaster.serviceitemid", 1);
        String klass = selectRealOrdinal("PlanMaster.classid", 1);
        // Co-Pay(%) + Discount.
        page.evaluate("() => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('PlanMaster.copayper','10'); set('PlanMaster.discper','5'); }");
        waitForAngular(400);
        return "Location=" + location + " | PlanCode=" + code + " | PlanName=" + lastName + " | PricingPolicy=" + pricing
                + " | Group=" + group + " | SubGroup=" + subgroup + " | Services=" + service + " | Class=" + klass
                + " | CoPay=10 | Discount=5";
    }

    /** Re-assert Co-Pay(%) + Discount (guards a mislabeled/late-reset validation before Add). */
    private void setCopayDiscount() {
        page.evaluate("() => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " set('PlanMaster.copayper','10'); set('PlanMaster.discper','5'); }");
        waitForAngular(200);
    }

    private int realCount(String selNg) {
        Object c = page.evaluate("(s) => { const e=document.querySelector(s); if(!e) return 0; return [...e.options].filter(o=>o.value && o.value!=='?' && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())).length; }",
                "select[ng-model='" + selNg + "']");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    private void pollHasReal(String selNg, int timeoutMs) {
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && o.value!=='?' && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    "select[ng-model='" + selNg + "']", new Page.WaitForFunctionOptions().setTimeout(timeoutMs));
        } catch (Exception ignore) { }
    }

    private String selectRealOrdinal(String selNg, int ordinal) {
        // Some ng-models (serviceitemid, subgroupid) appear TWICE (header form + grid template) → use .first() to
        // avoid strict-mode violations; the header/first one is the active add-form control.
        com.microsoft.playwright.Locator loc = page.locator("select[ng-model='" + selNg + "']").first();
        Object idx;
        try {
            idx = loc.evaluate("(e, ord) => { const reals=[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && x.o.value!=='?' && (x.o.textContent||'').trim() && !/^-*\\s*select/i.test((x.o.textContent||'').trim())); return reals.length>=ord ? reals[ord-1].i : -1; }", ordinal);
        } catch (Exception e) { System.out.println("selectRealOrdinal: no element for " + selNg); return "(no-opt)"; }
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "(no-opt)";
        String chosen = "(no)";
        try {
            loc.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            waitForAngular(200);
            chosen = String.valueOf(loc.evaluate("e => (e.options[e.selectedIndex]||{}).textContent||''")).trim();
            if (chosen.isEmpty()) chosen = String.valueOf(loc.evaluate("e => (e.options[e.selectedIndex]||{}).textContent||''")).trim();
        } catch (Exception e) { System.out.println("selectRealOrdinal: selectOption failed for " + selNg + " - " + e.getMessage()); return "(err)"; }
        waitForAngular(400);
        return chosen;
    }

    /** Click <b>Add</b> ({@code fnAddListOfServices}) to add the detail line. Returns the toast, or "(detail row
     *  added)" if a grid row appeared, else "". */
    public String addDetailLine() {
        setCopayDiscount();
        int before = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        page.evaluate("() => { window.__plToasts=[]; if(window.__plObs) window.__plObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__plToasts.includes(t)) window.__plToasts.push(t); }); };"
                + " window.__plObs=new MutationObserver(grab); window.__plObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/fnAddListOfServices/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1200);
        Object toast = page.evaluate("() => { const a=window.__plToasts||[]; return a.find(x=>/detail|added|success|please|select|enter|required|copay|discount|group|service|class/i.test(x)) || a[0] || ''; }");
        String t = toast == null ? "" : toast.toString().trim();
        int after = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        if ((t.isEmpty() || t.toLowerCase().contains("added")) && after > before) t = t.isEmpty() ? "(detail row added)" : t;
        return t;
    }

    /** Read {@code PlanMaster.<field>} off the Angular scope (the model value, e.g. 1 for KPJ). */
    private Object readModel(String field) {
        return page.evaluate("(f) => { let v=null; document.querySelectorAll('*').forEach(el=>{ if(v!==null) return; try{ let s=angular.element(el).scope(); for(let i=0;i<20&&s;i++){ if(s.PlanMaster && s.PlanMaster[f]!=null && s.PlanMaster[f]!==''){ v=s.PlanMaster[f]; break; } s=s.$parent; } }catch(e){} }); return v; }", field);
    }

    /** Re-select Location + Pricing Policy in the DOM before Submit — the <b>Add</b> (fnAddListOfServices) clears the
     *  header selects, and merely setting the scope model doesn't stick (the ng-model digest re-syncs from the reset
     *  {@code <select>} and reverts it). So re-run the real DOM selectOption and VERIFY the model committed; retry. */
    public String reassertHeader() {
        String res = "";
        for (int a = 0; a < 5; a++) {
            pollHasReal("PlanMaster.LocationID", 5000);
            selectRealOrdinal("PlanMaster.LocationID", 1);
            pollHasReal("PlanMaster.TariffID", 5000);
            selectRealOrdinal("PlanMaster.TariffID", 1);
            waitForAngular(400);
            Object lm = readModel("LocationID"), tm = readModel("TariffID");
            res = "attempt " + (a + 1) + " Location=" + lm + " Pricing=" + tm;
            if (lm != null && tm != null) return "reassert(DOM) " + res;
            waitForAngular(600);
        }
        return "reassert(DOM) FAILED " + res;
    }

    /** Click <b>Submit</b> ({@code fnIUDPlanMaster}) and return the success toast (toastr cleared first). */
    public String submitAndGetToast() {
        reassertHeader();
        Object tagged = page.evaluate("() => { window.__plToasts=[]; if(window.__plObs) window.__plObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__plToasts.includes(t)) window.__plToasts.push(t); }); };"
                + " window.__plObs=new MutationObserver(grab); window.__plObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDPlanMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__planSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__planSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__plToasts||[]).some(a=>/plan|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__plToasts||[]).includes(t)) (window.__plToasts=window.__plToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__plToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** After Submit, open the list ({@code #/PlanMaster}) and confirm a grid row matches the saved Plan Code.
     *  Returns the matched row text, or "". */
    public String findAddedInList() {
        if (!onScreen() || page.url().toLowerCase().contains("add-")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, table tr')].length > 1",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        Object r = page.evaluate("(code) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase(); const cc=up(code);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tr')];"
                + " for(const row of rows){ const cells=[...row.querySelectorAll('.ui-grid-cell-contents, td')].map(c=>up(c.textContent)); if(cells.some(c=>c===cc || c.includes(cc))) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                + " return ''; }", lastCode);
        return r == null ? "" : r.toString().trim();
    }
}
