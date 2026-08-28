package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Currency Master</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Currency Master</b> (route
 * {@code #/CurrencyMaster}) → <b>Add</b> ({@code #/add-CurrencyMaster}) → enter <b>Code</b>, <b>Remark</b>,
 * <b>Percentage</b>, <b>Higher Denomination</b>, <b>Lower Denomination</b> → <b>Submit</b>
 * ({@code fnIUDCurrencyMaster}) → toast → verify in the list table.</p>
 *
 * <p>Fields (discovered, all plain text): {@code CurrencyMaster.code}, {@code .description} (Remark),
 * {@code .percentage}, {@code .higherdenomination}, {@code .lowerdenomination}. (Attach Photo
 * {@code CurrencyMaster.PhotoFileData} is optional and not filled.)</p>
 */
public class CurrencyMaster extends BasePage {

    public CurrencyMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/CurrencyMaster";
    public static String ADD_ROUTE = "#/add-CurrencyMaster";
    public String lastCode = "";
    public String lastRemark = "";

    /** Realistic currency names — a fresh one is picked per retry attempt after "already exists". */
    private static final String[] REMARKS = {
            "US Dollar", "Malaysian Ringgit", "Singapore Dollar", "Euro", "British Pound Sterling",
            "Japanese Yen", "Australian Dollar", "Thai Baht", "Indonesian Rupiah", "Chinese Yuan",
            "Hong Kong Dollar", "Indian Rupee"
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
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/currency\\s*master/i.test(norm(x.textContent)) || /currencymaster/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__curMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__curMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("CurrencyMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("CurrencyMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("currencymaster");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-CurrencyMaster}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__curAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("CurrencyMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__curAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("CurrencyMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='CurrencyMaster.code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("CurrencyMaster.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /**
     * Enter Code, Remark, Percentage, Higher Denomination, Lower Denomination (all by exact ng-model).
     * {@code attempt} shifts BOTH Code and Remark so a retry after an "already exists" toast submits genuinely
     * different details.
     */
    public String fillDetails(int attempt) {
        String code = "CUR" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        lastRemark = REMARKS[attempt % REMARKS.length] + (attempt >= REMARKS.length ? " " + (attempt / REMARKS.length + 1) : "");
        Object r = page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('CurrencyMaster.code',a.code); const rm=set('CurrencyMaster.description',a.remark); const pc=set('CurrencyMaster.percentage','10'); const hd=set('CurrencyMaster.higherdenomination','100'); const ld=set('CurrencyMaster.lowerdenomination','1');"
                + " return 'Code='+cd+' | Remark='+rm+' | Percentage='+pc+' | HigherDenomination='+hd+' | LowerDenomination='+ld; }",
                java.util.Map.of("code", code, "remark", lastRemark));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Submit</b> ({@code fnIUDCurrencyMaster}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__curToasts=[]; if(window.__curObs) window.__curObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__curToasts.includes(t)) window.__curToasts.push(t); }); };"
                + " window.__curObs=new MutationObserver(grab); window.__curObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDCurrencyMaster/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__curSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__curSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__curToasts||[]).some(a=>/currency|saved|success|added|updated|please|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__curToasts||[]).includes(t)) (window.__curToasts=window.__curToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__curToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }

    /** After Submit, open the list ({@code #/CurrencyMaster}) and confirm a grid row matches the saved Code.
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
