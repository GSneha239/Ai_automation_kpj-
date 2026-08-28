package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Tax</b> (Tax Master) — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Tax</b> (route {@code #/TaxMaster}) → <b>Add</b>
 * ({@code #/add-TaxMaster}) → enter <b>Code</b>, <b>Remark</b>, <b>Percentage</b>, <b>Ledger Name</b> →
 * <b>Save</b> ({@code IUDTaxMaster} — api/TaxMaster/IUD) → success toast.</p>
 *
 * <p>Fields (per Cortex — {@code TaxMaster} controller): {@code TaxMaster.code}, {@code TaxMaster.description}
 * (Remark), {@code TaxMaster.percentage}, {@code TaxMaster.ledgername}.</p>
 */
public class TaxMaster extends BasePage {

    public TaxMaster(Page page) { super(page); }

    public static String LIST_ROUTE = "#/TaxMasterList";
    public String lastCode = "";

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*tax(\\s*master)?\\s*$/i.test(norm(x.textContent)) || /#\\/TaxMaster\\b/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__taxMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__taxMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("TaxMaster.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("TaxMaster.nav: direct route " + LIST_ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        return true;
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("taxmaster"); }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-TaxMaster}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__taxAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) { System.out.println("TaxMaster.clickAdd: Add button not found"); return false; }
        try { page.locator("#__taxAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("TaxMaster.clickAdd: click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='TaxMaster.code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("TaxMaster.clickAdd: add form not ready"); }
        waitForAngular(800);
        return true;
    }

    /**
     * Realistic Tax names — not generic filler text; only the Code stays a generated unique value. The Ledger
     * Name is derived from the same realistic name (a real ledger a tax would post to), not "Auto Ledger <code>".
     */
    private static final String[] TAX_NAMES = {
            "Service Tax", "Sales Tax", "Value Added Tax", "Goods and Services Tax", "Excise Duty",
            "Stamp Duty", "Withholding Tax", "Import Duty", "Luxury Tax", "Tourism Tax",
            "Digital Service Tax", "Entertainment Tax"
    };

    /**
     * Enter Code, Remark, Percentage, Ledger Name (by exact ng-model). {@code attempt} shifts the code, remark
     * and ledger name so a retry after an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "TAX" + String.format("%05d", Math.abs((System.nanoTime() + attempt * 7919L) % 100000));
        lastCode = code;
        String remark = TAX_NAMES[attempt % TAX_NAMES.length] + (attempt >= TAX_NAMES.length ? " " + (attempt / TAX_NAMES.length + 1) : "");
        String ledger = remark + " Ledger";
        Object r = page.evaluate("(a) => { const A=window.angular; const set=(ng,v)=>{ const e=[...document.querySelectorAll(\"[ng-model='\"+ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return '(no)'; const c=A.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return v; };"
                + " const cd=set('TaxMaster.code',a.code); const rm=set('TaxMaster.description',a.remark); const pc=set('TaxMaster.percentage','6'); const lg=set('TaxMaster.ledgername',a.ledger);"
                + " return 'Code='+cd+' | Remark='+rm+' | Percentage='+pc+' | LedgerName='+lg; }",
                java.util.Map.of("code", code, "remark", remark, "ledger", ledger));
        waitForAngular(400);
        return r == null ? "" : r.toString();
    }

    /** Click <b>Save/Submit</b> ({@code IUDTaxMaster}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__taxToasts=[]; if(window.__taxObs) window.__taxObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__taxToasts.includes(t)) window.__taxToasts.push(t); }); };"
                + " window.__taxObs=new MutationObserver(grab); window.__taxObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/IUDTaxMaster/i.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*(submit|save)\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__taxSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__taxSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__taxToasts||[]).some(a=>/tax|saved|success|added|updated|please|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__taxToasts||[]).includes(t)) (window.__taxToasts=window.__taxToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__taxToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
