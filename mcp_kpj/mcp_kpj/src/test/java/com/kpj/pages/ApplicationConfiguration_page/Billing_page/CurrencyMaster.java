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

    /** Menu entries on screen when the walk was made — dumped into the report if the screen is not reached. */
    public String lastMenu = "";
    public String lastRoute = "";

    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        // The sidebar is built lazily and "Billing" is ALSO a top-level module listed before Application Configuration. Open each
        // level only if it is closed (a second click on an open entry CLOSES it) and look the next one up INSIDE its parent's li.
        expandMenuEntry(null, "^\\s*application\\s*config(uration)?\\s*$");
        expandMenuEntry("^\\s*application\\s*config(uration)?\\s*$", "^\\s*billing\\s*$");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*application\\s*config(uration)?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " const topLi=(top&&top.closest('li'))||document;"
                + " const bill=[...topLi.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null);"
                + " const scope=(bill&&bill.closest('li'))||topLi;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*currency\\s*master\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__curMenu'; return a.getAttribute('href')||'link'; }");
        lastRoute = href == null ? "" : href.toString();
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__curMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("CurrencyMaster.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__curMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        // Direct hash navigation is bounced to #/PatientDashboard by the app, so it is NOT a way in — only the menu link is.
        if (!onScreen()) {
            lastMenu = String.valueOf(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " return [...new Set([...document.querySelectorAll('a')].filter(a=>a.offsetParent!==null)"
                    + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||'')).filter(t=>t.length>4&&t.length<70))].slice(0,140).join('\\n'); }"));
            return false;
        }
        return true;
    }

    /** On the Currency Master list/add screen — the route AND the screen's own control must be there (a bounce to the dashboard fails). */
    public boolean onScreen() {
        String u = page.url().toLowerCase();
        if (!u.contains("currencymaster")) return false;
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button,a,input[type=button]')].some(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(((x.textContent||x.value)||'').replace(/\\s+/g,' ').trim()))"
                    + " || [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='CurrencyMaster.code')",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception e) { return false; }
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
        // Stamped from the wall clock: System.nanoTime() ticks in 100 ns steps on this machine, so its remainders end in "00" and a code
        // taken from them came from only ~1,000 values — too few for a master list that keeps every record ever saved.
        String code = "CUR" + String.format("%05d", (System.currentTimeMillis() + attempt * 7919L) % 100000);
        lastCode = code;
        // The Description (Remark) is UNIQUE per run: the shared QA environment keeps every currency ever saved, so a bare pool
        // name such as "US Dollar" collides with earlier runs. A realistic name + the run time (+ the retry number) never repeats.
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        lastRemark = REMARKS[attempt % REMARKS.length] + " " + stamp + (attempt > 0 ? "-" + attempt : "");
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
        // The toast's title ("KPJ Portal", with the close "×") is glued to its message in textContent — keep only the message.
        return r == null ? "" : r.toString().replaceFirst("^\\s*×?\\s*KPJ Portal\\s*", "").trim();
    }

    /** After Submit, open the list ({@code #/CurrencyMaster}) and confirm a grid row matches the saved Code.
     *  Returns the matched row text, or "". */
    public String findAddedInList() {
        if (page.url().toLowerCase().contains("add-")) {
            // The screen stays on the (now cleared) Add form after a save. Setting location.hash from here raises the app's
            // "Reload page" popup, which the framework answers No - so the page never left the form and no row was ever seen.
            // Go back with the form's own Back button instead.
            Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')]"
                    + ".find(x=>/^\\s*back\\s*$/i.test((x.textContent||x.value||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                    + " if(!b) return false; b.id='__curBack'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__curBack").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
                catch (Exception e) { System.out.println("CurrencyMaster.findAddedInList: Back click failed - " + e.getMessage()); }
                page.evaluate("() => { const e=document.getElementById('__curBack'); if(e) e.removeAttribute('id'); }");
                waitForAngular(1500);
            }
        } else if (!onScreen()) {
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
