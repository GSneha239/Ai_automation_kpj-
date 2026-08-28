package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Payor</b> (Receivable Master) — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Payor</b> (route {@code #/receivableMaster}) →
 * <b>Add</b> ({@code #/add-receivable}) → enter <b>Payor Type</b>, <b>Payable Code</b>, <b>Payor</b>,
 * <b>Primary Phone No</b>, <b>E-mail</b>, <b>Primary Contact Person</b> → <b>Submit</b>
 * ({@code fnIUDReceivable}) → toast → verify in the list table.</p>
 *
 * <p>Fields (the required *): {@code receivable.ReceivableTypeID} (Payor Type select), {@code .ReceivableCode}
 * (Payable Code), {@code .ReceivableName} (Payor), {@code .PrimaryPhoneNo}, {@code .EmailID},
 * {@code .PrimaryContactPerson}. (Many other optional fields — Address/Country/City/etc. — are left blank.)
 * NOTE: several menu links read "Payor"; the master is the one whose href is {@code #/receivableMaster}
 * (NOT {@code associatereceivableMaster}, {@code CompanyPaymentSettlement} or {@code COMPANY}).</p>
 */
public class Payor extends BasePage {

    public Payor(Page page) { super(page); }

    public static String LIST_ROUTE = "#/receivableMaster";
    public static String ADD_ROUTE = "#/add-receivable";
    public String lastCode = "";
    public String lastName = "";

    /** Realistic Payor (insurer / company) names — a fresh one is picked per retry attempt after "already exists". */
    private static final String[] PAYOR_NAMES = {
            "Allianz General Insurance", "AIA Malaysia", "Great Eastern Life Assurance", "Prudential Assurance",
            "AXA Affin General Insurance", "Zurich Insurance Malaysia", "Etiqa Insurance", "Tokio Marine Life Insurance",
            "MSIG Insurance", "Manulife Insurance"
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
        // The Payor master link — href is exactly #/receivableMaster (NOT associatereceivableMaster).
        Object href = page.evaluate("() => { const a=[...document.querySelectorAll('a[href]')].find(x=>{ const h=x.getAttribute('href')||''; return /(^|[^e])receivableMaster/i.test(h) && !/associate/i.test(h); }); if(!a) return ''; a.id='__payorMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__payorMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("Payor.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("Payor.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        String u = page.url().toLowerCase();
        return u.contains("receivablemaster") && !u.contains("associate");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-receivable}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__payorAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("Payor.clickAdd: Add button not found"); return false; }
        try { page.locator("#__payorAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("Payor.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input,select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='receivable.ReceivableCode')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("Payor.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /**
     * Enter Payor Type (select, first real), Payable Code, Payor, Primary Phone No, E-mail, Primary Contact Person.
     * {@code attempt} shifts BOTH Payable Code and Payor so a retry after an "already exists" toast submits
     * genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "PY" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastName = PAYOR_NAMES[attempt % PAYOR_NAMES.length] + (attempt >= PAYOR_NAMES.length ? " " + (attempt / PAYOR_NAMES.length + 1) : "");
        // Payor Type — real selectOption (first real).
        String payorType = "(no-opt)";
        try {
            page.waitForFunction("() => { const e=document.querySelector(\"select[ng-model='receivable.ReceivableTypeID']\"); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
            Object idx = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='receivable.ReceivableTypeID']\"); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }");
            int i = idx instanceof Number ? ((Number) idx).intValue() : 1;
            page.locator("select[ng-model='receivable.ReceivableTypeID']").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            waitForAngular(400);
            payorType = String.valueOf(page.evaluate("() => { const e=document.querySelector(\"select[ng-model='receivable.ReceivableTypeID']\"); return (e.options[e.selectedIndex]||{}).textContent||''; }")).trim();
        } catch (Exception e) { System.out.println("fillDetails: Payor Type select failed - " + e.getMessage()); }
        // Text fields — use a REAL Playwright .fill() (types + fires proper events); JS $setViewValue did not commit
        // the model for PrimaryContactPerson (an input directive on that field).
        String pc = fillText("receivable.ReceivableCode", code);
        String pn = fillText("receivable.ReceivableName", lastName);
        String ph = fillText("receivable.PrimaryPhoneNo", "0123456789");
        String em = fillText("receivable.EmailID", "auto" + code.toLowerCase() + "@test.com");
        String cp = fillText("receivable.PrimaryContactPerson", "Auto Contact Person");
        waitForAngular(400);
        return "PayorType=" + payorType + " | PayableCode=" + pc + " | Payor=" + pn
                + " | PrimaryPhoneNo=" + ph + " | Email=" + em + " | PrimaryContactPerson=" + cp;
    }

    /** Real Playwright fill of the (first visible) input bound to {@code ng-model}. Returns the value, or "(no)". */
    private String fillText(String ng, String value) {
        com.microsoft.playwright.Locator loc = page.locator("[ng-model='" + ng + "']").first();
        try {
            loc.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            loc.fill("");
            loc.fill(value);
            loc.blur();
            waitForAngular(150);
            return value;
        } catch (Exception e) { System.out.println("fillText: failed for " + ng + " - " + e.getMessage()); return "(no)"; }
    }

    /** Click <b>Submit</b> ({@code fnIUDReceivable}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__pyToasts=[]; if(window.__pyObs) window.__pyObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__pyToasts.includes(t)) window.__pyToasts.push(t); }); };"
                + " window.__pyObs=new MutationObserver(grab); window.__pyObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDReceivable/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__payorSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__payorSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__pyToasts||[]).some(a=>/payor|receivable|saved|success|added|updated|please|enter|select|valid|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__pyToasts||[]).includes(t)) (window.__pyToasts=window.__pyToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__pyToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** After Submit, open the list ({@code #/receivableMaster}) and confirm the added Payor is there. The list grid
     *  is EMPTY until filtered — type the Payable Code into the <b>Payor Code</b> column filter (the first
     *  {@code .ui-grid-filter-input}), which loads the matching row(s). Returns the matched row text, or "". */
    public String findAddedInList() {
        if (!onScreen() || page.url().toLowerCase().contains("add-")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        waitForAngular(1000);
        // Set the Payor Code column filter (first .ui-grid-filter-input) via JS input events — ui-grid picks it up.
        page.evaluate("(code) => { const f=document.querySelector('.ui-grid-filter-input'); if(f){ const c=window.angular&&angular.element(f).controller('ngModel'); f.value=code; if(c){c.$setViewValue(code);c.$render();} f.dispatchEvent(new Event('input',{bubbles:true})); f.dispatchEvent(new Event('keyup',{bubbles:true})); f.dispatchEvent(new Event('change',{bubbles:true})); } }", lastCode);
        waitForAngular(1200);
        // Click the top Search button (the list grid is server-loaded — Search fetches the filtered rows).
        page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button],input[type=submit]')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||x.value||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null); if(b) b.click(); }");
        // wait for a matching row/data to appear
        try {
            page.waitForFunction("(code) => { const up=s=>(s||'').toUpperCase(); const cc=up(code);"
                    + " if([...document.querySelectorAll('.ui-grid-row')].some(r=>up(r.textContent||'').includes(cc))) return true;"
                    + " let hit=false; document.querySelectorAll('*').forEach(el=>{ if(hit) return; try{ const s=angular.element(el).scope(); const d=(s&&s.grid&&s.grid.options&&s.grid.options.data)||(s&&s.gridOptions&&s.gridOptions.data); if(d&&d.some(x=>JSON.stringify(x).toUpperCase().includes(cc))) hit=true; }catch(e){} }); return hit; }",
                    lastCode, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        waitForAngular(800);
        Object r = page.evaluate("(code) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase(); const cc=up(code);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')];"
                + " for(const row of rows){ const cells=[...row.querySelectorAll('.ui-grid-cell-contents, td')].map(c=>up(c.textContent)); if(cells.some(c=>c===cc || c.includes(cc))) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                // fall back to the grid data array (avoids virtualization)
                + " let found=''; document.querySelectorAll('*').forEach(el=>{ if(found) return; try{ const s=angular.element(el).scope(); const d=(s&&s.grid&&s.grid.options&&s.grid.options.data)||(s&&s.gridOptions&&s.gridOptions.data); if(d){ const m=d.find(x=>JSON.stringify(x).toUpperCase().includes(cc)); if(m){ found=Object.values(m).filter(v=>v!=null && typeof v!=='object').join(' | ').slice(0,160); } } }catch(e){} }); return found; }", lastCode);
        return r == null ? "" : r.toString().trim();
    }
}
