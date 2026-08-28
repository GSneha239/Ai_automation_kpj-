package com.kpj.pages.ApplicationConfiguration_page.CSSD_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; CSSD Configuration &gt; <b>Recall Reason</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>CSSD Configuration</b> (submenu) → <b>Recall Reason</b>
 * (route {@code #/RecallReasonList}) → enter <b>Code</b>, <b>Recall Reason</b> → click <b>Save</b>
 * ({@code fnAddRecallReason()}) → toast. This is an INLINE-ADD screen — the Code/Recall Reason inputs are on
 * the list page itself (no separate Add button/route).</p>
 *
 * <p>Fields: {@code RecallReason.reasoncode} (Code), {@code RecallReason.reasonname} (Recall Reason).</p>
 */
public class RecallReason extends BasePage {

    public RecallReason(Page page) { super(page); }

    public static String LIST_ROUTE = "#/RecallReasonList";
    public String lastCode = "", lastReasonName = "";

    /** Realistic CSSD recall-reason names, not "Auto recall reason &lt;code&gt;" filler — cycled per retry attempt. */
    private static final String[] REASONS = {
            "Equipment Malfunction", "Incomplete Sterilization", "Packaging Damage", "Expired Indicator",
            "Chemical Indicator Failure", "Biological Indicator Failure", "Loading Error", "Missing Documentation",
            "Seal Breach", "Contamination Suspected"
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
        // CSSD Configuration submenu parent → expand
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a')].find(x=>/cssd/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>/^\\s*recall\\s*reason\\s*$/i.test(norm(x.textContent)) || /recallreason/i.test(x.getAttribute('href')||'')); if(!a) return ''; a.id='__rrMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            try { page.locator("#__rrMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("RecallReason.nav: click failed - " + e.getMessage()); }
            waitForAngular(1800);
        }
        // inline-add: wait for the Code input to be present
        try {
            page.waitForFunction("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='RecallReason.reasoncode')",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("RecallReason.nav: Code input not ready"); }
        waitForAngular(600);
        return onScreen();
    }

    public boolean onScreen() {
        return page.url().toLowerCase().contains("recallreason");
    }

    // ---- actions ---------------------------------------------------------

    private void setNg(String ng, String v) {
        page.evaluate("(a) => { const A=window.angular; const e=[...document.querySelectorAll(\"[ng-model='\"+a.ng+\"']\")].find(x=>x.offsetParent!==null); if(!e) return; const c=A.element(e).controller('ngModel'); e.value=a.v; if(c){c.$setViewValue(a.v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Map.of("ng", ng, "v", v));
    }

    /**
     * Enter Code + Recall Reason (inline on the list page). {@code attempt} shifts BOTH values so a retry after
     * an "already exists" toast submits genuinely different details.
     */
    public String fillDetails(int attempt) {
        String code = "RR" + String.format("%06d", Math.abs((System.nanoTime() + attempt * 7919L) % 1000000));
        lastCode = code;
        lastReasonName = REASONS[attempt % REASONS.length] + (attempt >= REASONS.length ? " " + (attempt / REASONS.length + 1) : "");
        setNg("RecallReason.reasoncode", code);
        setNg("RecallReason.reasonname", lastReasonName);
        waitForAngular(300);
        return "Code=" + code + " | RecallReason=" + lastReasonName;
    }

    /** Click <b>Save</b> ({@code fnAddRecallReason}) and return the toast (toastr cleared first). */
    public String saveAndGetToast() {
        Object tagged = page.evaluate("() => { window.__rrToasts=[]; if(window.__rrObs) window.__rrObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__rrToasts.includes(t)) window.__rrToasts.push(t); }); };"
                + " window.__rrObs=new MutationObserver(grab); window.__rrObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit],input[type=button]')].find(x=>/fnAddRecallReason/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*save\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__rrSave'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("saveAndGetToast: Save button not found"); return ""; }
        try { page.locator("#__rrSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("saveAndGetToast: Save click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__rrToasts||[]).some(a=>/recall|reason|saved|success|added|updated|please|enter|required|exist|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__rrToasts||[]).includes(t)) (window.__rrToasts=window.__rrToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__rrToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        return r == null ? "" : r.toString().trim();
    }
}
