package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Sub Group Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Sub Group</b> (route {@code #/SubGroup}) →
 * <b>Add</b> ({@code #/add-SubGroup}) → enter <b>Location</b>, <b>Group</b>, <b>Sub Group Code</b>,
 * <b>Sub Group</b> → enter <b>Depreciation Field</b> + <b>Value</b> → click <b>Add</b>
 * ({@code AddDepreciationDetails}) → <b>Submit</b> ({@code fnIUDSubGroup}) → toast → verify in the table.</p>
 *
 * <p>Fields: {@code subgroup.LocationID} (Location select), {@code subgroup.GroupID} (Group select),
 * {@code subgroup.SubGroupCode}, {@code subgroup.SubGroupName}; depreciation detail
 * {@code subgroup1.DepreciationFieldID} (select) + {@code subgroup1.Value}.</p>
 */
public class SubGroupMaster extends BasePage {

    public SubGroupMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/SubGroup";
    public static String ADD_ROUTE = "#/add-SubGroup";
    public String lastCode = "", lastGroup = "";

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
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*sub\\s*group\\s*$/i.test(norm(x.textContent)) || /#\\/subgroup\\b/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__sgMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__sgMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("SubGroupMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("SubGroupMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("subgroup");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-SubGroup}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__sgAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("SubGroupMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__sgAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("SubGroupMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            // wait for the SubGroupCode input AND the Group select's real options (loads async)
            page.waitForFunction("() => { const c=[...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='subgroup.SubGroupCode');"
                    + " const g=document.querySelector(\"select[ng-model='subgroup.GroupID']\"); const gok=g && [...g.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); return c && gok; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("SubGroupMaster.clickAdd: add form/Group options not ready"); }
        waitForAngular(1000);
        return true;
    }

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    private String realSelectFirst(String selNg) {
        String sel = "select[ng-model='" + selNg + "']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(10000));
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
     * Realistic Sub Group names — not generic filler text; only the Code stays a generated unique value.
     */
    private static final String[] SUBGROUP_NAMES = {
            "Consumables", "Pharmacy Items", "Surgical Supplies", "Laboratory Reagents",
            "Radiology Consumables", "Medical Devices", "Dietary Supplies", "Housekeeping Items",
            "Maintenance Parts", "Office Supplies", "IT Equipment", "Linen and Bedding"
    };

    /**
     * Enter Location, Group, Sub Group Code, Sub Group name. {@code attempt} shifts BOTH the code and the name
     * so a retry after an "already exists" toast submits genuinely different details.
     */
    public String fillHeader(int attempt) {
        String code = "SG" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        String name = SUBGROUP_NAMES[attempt % SUBGROUP_NAMES.length] + (attempt >= SUBGROUP_NAMES.length ? " " + (attempt / SUBGROUP_NAMES.length + 1) : "");
        String loc = realSelectFirst("subgroup.LocationID");
        String grp = realSelectFirst("subgroup.GroupID");
        for (int a = 0; a < 3 && grp.startsWith("("); a++) { waitForAngular(700); grp = realSelectFirst("subgroup.GroupID"); }
        lastGroup = grp;
        setNg("subgroup.SubGroupCode", code);
        setNg("subgroup.SubGroupName", name);
        waitForAngular(300);
        return "Location=" + loc + " | Group=" + grp + " | SubGroupCode=" + code + " | SubGroup=" + name;
    }

    /** Enter Depreciation Field + Value, then click <b>Add</b> ({@code AddDepreciationDetails}) to add the line. */
    public String addDepreciationDetail() {
        String field = realSelectFirst("subgroup1.DepreciationFieldID");
        setNg("subgroup1.Value", "100");
        waitForAngular(300);
        int before = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        page.evaluate("() => { window.__sgToasts=[]; if(window.__sgObs) window.__sgObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__sgToasts.includes(t)) window.__sgToasts.push(t); }); };"
                + " window.__sgObs=new MutationObserver(grab); window.__sgObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/AddDepreciationDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(b) b.click(); }");
        waitForAngular(1000);
        Object toast = page.evaluate("() => { const a=window.__sgToasts||[]; return a.find(x=>/deprecia|detail|added|success|please|select|enter|value|field/i.test(x)) || a[0] || ''; }");
        String t = toast == null ? "" : toast.toString().trim();
        int after = ((Number) page.evaluate("() => document.querySelectorAll('.ui-grid-row, table tbody tr').length")).intValue();
        String rowMsg = (after > before) ? "(detail row added)" : (t.isEmpty() ? "(no row/toast)" : t);
        return "DepreciationField=" + field + " | Value=100 | " + rowMsg;
    }

    /** Click <b>Submit</b> ({@code fnIUDSubGroup}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__sgToasts=[]; if(window.__sgObs) window.__sgObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__sgToasts.includes(t)) window.__sgToasts.push(t); }); };"
                + " window.__sgObs=new MutationObserver(grab); window.__sgObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDSubGroup/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__sgSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__sgSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__sgToasts||[]).some(a=>/sub\\s*group|subgroup|saved|success|added|updated|please|select|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__sgToasts||[]).includes(t)) (window.__sgToasts=window.__sgToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__sgToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** After Submit, open the list ({@code #/SubGroup}) and confirm a grid row matches the saved Sub Group Code. */
    public String findAddedInList() {
        if (!onScreen() || page.url().toLowerCase().contains("add-")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, table tr')].length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        page.evaluate("(code) => { const f=document.querySelector('.ui-grid-filter-input'); if(f){ const c=window.angular&&angular.element(f).controller('ngModel'); f.value=code; if(c){c.$setViewValue(code);c.$render();} f.dispatchEvent(new Event('input',{bubbles:true})); f.dispatchEvent(new Event('keyup',{bubbles:true})); f.dispatchEvent(new Event('change',{bubbles:true})); } }", lastCode);
        try {
            page.waitForFunction("(code) => { const up=s=>(s||'').toUpperCase(); const cc=up(code); return [...document.querySelectorAll('.ui-grid-row, table tbody tr')].some(r=>up(r.textContent||'').includes(cc)); }",
                    lastCode, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        waitForAngular(700);
        Object r = page.evaluate("(code) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase(); const cc=up(code);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tbody tr')];"
                + " for(const row of rows){ const cells=[...row.querySelectorAll('.ui-grid-cell-contents, td')].map(c=>up(c.textContent)); if(cells.some(c=>c===cc || c.includes(cc))) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                + " let found=''; document.querySelectorAll('*').forEach(el=>{ if(found) return; try{ const s=angular.element(el).scope(); const d=(s&&s.grid&&s.grid.options&&s.grid.options.data)||(s&&s.gridOptions&&s.gridOptions.data); if(d){ const m=d.find(x=>up(JSON.stringify(x)).includes(cc)); if(m) found=Object.values(m).filter(v=>v!=null && typeof v!=='object').join(' | ').slice(0,160); } }catch(e){} }); return found; }", lastCode);
        return r == null ? "" : r.toString().trim();
    }
}
