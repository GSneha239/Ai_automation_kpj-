package com.kpj.pages.ApplicationConfiguration_page.Location_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Location &gt; <b>Payable Roster</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Location</b> → <b>Payable Roster</b> → select
 * <b>Location</b> + <b>Department</b> → <b>Search</b> → the results table populates (if it doesn't for the
 * current Department, try the next Department) → tick a row's checkbox → <b>Submit</b> → success toast.</p>
 *
 * <p>NEW screen — Location/Department selects, the Search button, the results grid and Submit are all
 * DISCOVERED at runtime (by label text / ng-click / button text), matching the runtime-discovery convention
 * used for other brand-new screens in this codebase.</p>
 */
public class PayableRoster extends BasePage {

    public PayableRoster(Page page) { super(page); }

    public static String ROUTE = "";
    public String lastLocation = "", lastDepartment = "";
    public String lastToasts = "[]", lastSaveHttp = "";
    public byte[] toastPng;

    // ---- navigation --------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        try {
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
            waitForAngular(1200);
            // EXACT "Location" — a prefix match can expand a different (similarly named) menu.
            page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const a=[...document.querySelectorAll('a')].find(x=>/^\\s*locations?\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null && (x.getAttribute('href')||'')==='#'); if(a) a.click(); }");
            waitForAngular(1500);
        } catch (Exception e) {
            System.out.println("PayableRoster.nav: menu entry raced the SPA route change - " + e.getMessage());
        }
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const want=x=>/^\\s*payable\\s*roster\\s*$/i.test(norm(x.textContent));"
                + " const a=[...document.querySelectorAll('a[href]')].find(x=>want(x) && x.offsetParent!==null)"
                + "   || [...document.querySelectorAll('a[href]')].find(want);"
                + " if(!a) return ''; a.id='__prMenu'; return a.getAttribute('href')||'link'; }");
        if (href != null && !href.toString().isEmpty()) {
            if (href.toString().contains("#/")) ROUTE = href.toString().substring(href.toString().indexOf("#/"));
            try { page.locator("#__prMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) {
                System.out.println("PayableRoster.nav: real click failed, dispatching in-page click");
                try { page.evaluate("() => { const a=document.getElementById('__prMenu'); if(a) a.click(); }"); } catch (Exception ignore) { }
            }
            try { page.evaluate("() => { const e=document.getElementById('__prMenu'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
            waitForAngular(2500);
        } else {
            System.out.println("PayableRoster.nav: menu link not found. links => " + describeVisibleLinks());
        }
        if (!onScreen() && !ROUTE.isEmpty()) {
            System.out.println("PayableRoster.nav: direct route " + ROUTE);
            try { page.evaluate("(h) => { window.location.hash = h; }", ROUTE); } catch (Exception ignore) { }
            waitForAngular(2500);
        }
        for (int i = 0; i < 8 && !onScreen(); i++) page.waitForTimeout(1000);
        return onScreen();
    }

    public boolean onScreen() {
        if (!ROUTE.isEmpty() && page.url().toLowerCase().contains(ROUTE.replace("#/", "").toLowerCase())) return true;
        return Boolean.TRUE.equals(page.evaluate("() => /payable\\s*roster/i.test(document.body.innerText||'')"));
    }

    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,section.content-header,h1')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,60); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    private String describeVisibleLinks() {
        Object r = page.evaluate("() => [...document.querySelectorAll('a[href]')].filter(x=>x.offsetParent!==null)"
                + " .map(x=>'\"'+(x.textContent||'').replace(/\\s+/g,' ').trim()+'\" -> '+x.getAttribute('href')).slice(0,60).join(' | ')");
        return r == null ? "" : r.toString();
    }

    public String describeForm() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const lbl=e=>{ let t='', p=e.parentElement, h=0;"
                + "   while(p && h++<4 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t.slice(0,30); };"
                + " const f=[...document.querySelectorAll('input,textarea')].filter(e=>e.offsetParent!==null && e.type!=='hidden'"
                + "     && !/colFilter|pagination/i.test(e.getAttribute('ng-model')||'')).slice(0,20)"
                + "   .map(e=>e.tagName.toLowerCase()+':'+(e.type||'')+' ng=\"'+(e.getAttribute('ng-model')||'')+'\" lbl=\"'+lbl(e)+'\"');"
                + " const s=[...document.querySelectorAll('select')].slice(0,10)"
                + "   .map(e=>'select ng=\"'+(e.getAttribute('ng-model')||e.id||'?')+'\" opts='+e.options.length+' lbl=\"'+lbl(e)+'\"');"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=button],input[type=submit]')].filter(e=>e.offsetParent!==null)"
                + "   .map(e=>norm(e.textContent||e.value)+(e.getAttribute('ng-click')?(' {'+e.getAttribute('ng-click')+'}'):'')).filter(t=>t).slice(0,20);"
                + " let heads=[...document.querySelectorAll('.ui-grid-header-cell')].map(h=>norm(h.textContent));"
                + " if(!heads.length) heads=[...document.querySelectorAll('table thead th')].map(h=>norm(h.textContent));"
                + " return 'FIELDS:\\n  '+f.join('\\n  ')+'\\nSELECTS:\\n  '+s.join('\\n  ')"
                + "   +'\\nGRID HEADERS: '+heads.join(' | ')+'\\nBUTTONS: '+b.join(' , '); }");
        return r == null ? "" : r.toString();
    }

    // ---- filters -----------------------------------------------------------

    /** JS: the two selects labelled "Location" / "Department" — resolved by nearby label text, not ng-model
     *  (this screen's exact ng-model names were not yet confirmed live). */
    private static final String FIND_SELECTS_JS =
            " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
            + " const labelNear=e=>{ let t='', p=e.parentElement, hop=0; while(p && hop++<5 && !t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } return t; };"
            + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null && !/pagination|colFilter/i.test(e.getAttribute('ng-model')||''));"
            + " const locEl = sels.find(e=>/location/i.test(labelNear(e)) || /location/i.test(e.getAttribute('ng-model')||''));"
            + " const deptEl = sels.find(e=>e!==locEl && (/depart?ment/i.test(labelNear(e)) || /depart?ment/i.test(e.getAttribute('ng-model')||'')));";

    private int optionCount(boolean location) {
        Object n = page.evaluate("(loc) => {" + FIND_SELECTS_JS + " const s=loc?locEl:deptEl; if(!s) return 0;"
                + " return [...s.options].filter(o=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim())).length; }", location);
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /** Choose the {@code index}-th real option of Location ({@code location=true}) or Department, via JS
     *  {@code selectedIndex} + {@code dispatchEvent('change')} + Angular {@code triggerHandler('change')}. */
    private String selectNth(boolean location, int index) {
        for (int w = 0; w < 12 && optionCount(location) == 0; w++) page.waitForTimeout(600);
        Object r = page.evaluate("([loc,n]) => { const A=window.angular;" + FIND_SELECTS_JS
                + " const s=loc?locEl:deptEl; if(!s) return '';"
                + " const real=[...s.options].map((o,i)=>({o,i})).filter(({o})=>o.value && !/^-*\\s*select|^\\s*$/i.test((o.textContent||'').trim()));"
                + " if(n>=real.length) return '';"
                + " const i=real[n].i; s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{ A.element(s).triggerHandler('change'); }catch(x){}"
                + " return s.options[s.selectedIndex].textContent.trim(); }", java.util.Arrays.asList(location, index));
        waitForAngular(900);
        return r == null ? "" : r.toString();
    }

    /** Select the {@code index}-th <b>Location</b> option. */
    public String selectLocation(int index) { lastLocation = selectNth(true, index); return lastLocation; }

    /** Select the {@code index}-th <b>Department</b> option — may be dependent on Location. */
    public String selectDepartment(int index) { lastDepartment = selectNth(false, index); return lastDepartment; }

    /** How many real Department options are currently offered (for the "try a different Department" retry). */
    public int departmentOptionCount() { return optionCount(false); }

    // ---- search / grid -------------------------------------------------------

    /** Click <b>Search</b> ({@code fnSearch}-style ng-click, or button text "Search"). */
    public void clickSearch() {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a,input[type=button]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/search/i.test(x.getAttribute('ng-click')||'') || /^\\s*search\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__prSearch'; return true; }"));
        if (!tagged) { System.out.println("PayableRoster.clickSearch: Search button not found"); return; }
        try { page.locator("#__prSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("PayableRoster.clickSearch: real click failed, dispatching in-page click - " + e.getMessage());
            try { page.evaluate("() => { const b=document.getElementById('__prSearch'); if(b) b.click(); }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const b=document.getElementById('__prSearch'); if(b) b.removeAttribute('id'); }");
        waitForAngular(2500);
    }

    /** The results table = the one with a checkbox column somewhere in its body rows. */
    private static final String GRID_TABLE_JS =
            "[...document.querySelectorAll('table')].filter(t=>t.offsetParent!==null).find(t=>t.querySelector('tbody tr input[type=checkbox]'))"
            + " || [...document.querySelectorAll('table')].find(t=>t.offsetParent!==null && t.querySelectorAll('tbody tr').length)";

    /** Number of real (non "no records") rows currently in the results grid. */
    public int resultRowCount() {
        Object r = page.evaluate("() => { const t=" + GRID_TABLE_JS + "; if(!t) return 0;"
                + " return [...t.querySelectorAll('tbody tr')].filter(r=>{ const tx=(r.textContent||'').replace(/\\s+/g,' ').trim(); return tx && !/no (records|data)/i.test(tx); }).length; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** Tick the checkbox of the first real row in the results grid (Angular ngModel + change/triggerHandler,
     *  same recipe [[devhis-shared-occupancy-actions]]/Unreservation proved reliable for DevHIS grid checkboxes). */
    public boolean selectFirstRow() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const t=" + GRID_TABLE_JS + "; if(!t) return false;"
                + " const row=[...t.querySelectorAll('tbody tr')].find(r=>{ const tx=norm(r.textContent); return tx && !/no (records|data)/i.test(tx); }); if(!row) return false;"
                + " const cb=row.querySelector('input[type=checkbox],input[type=radio]'); if(!cb) return false;"
                + " const c=A.element(cb).controller('ngModel'); cb.checked=true; if(c){ c.$setViewValue(true); c.$render(); }"
                + " cb.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(cb).triggerHandler('change');}catch(e){}"
                + " if(window.jQuery){try{jQuery(cb).trigger('change');}catch(e){}} return true; }");
        waitForAngular(500);
        return Boolean.TRUE.equals(r);
    }

    // ---- submit --------------------------------------------------------------

    /** Click <b>Submit</b> and return the toast; captures the save HTTP response for diagnosing a bare error. */
    public String submitAndGetToast() {
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                if (!(resp.url().toLowerCase().contains("payable") || resp.url().toLowerCase().contains("roster")
                        || resp.url().toLowerCase().contains("commonmaster") || resp.status() >= 400)) return;
                String body = "";
                try { body = resp.text(); } catch (Exception ignore) { body = "(body unavailable)"; }
                if (body.length() > 300) body = body.substring(0, 300) + "…";
                http.add(resp.status() + " " + resp.url() + " => " + body.replaceAll("\\s+", " ").trim());
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);

        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { window.__prToasts=[]; if(window.__prObs) window.__prObs.disconnect();"
                + " try{ if(window.toastr && toastr.options){ toastr.options.timeOut=0; toastr.options.extendedTimeOut=0; toastr.options.fadeOut=0; toastr.options.hideDuration=0; } }catch(e){}"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__prToasts.includes(t)) window.__prToasts.push(t); }); };"
                + " window.__prObs=new MutationObserver(grab); window.__prObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const b=[...document.querySelectorAll('button,a.btn,input[type=submit],input[type=button]')].filter(x=>x.offsetParent!==null)"
                + "   .find(x=>/^\\s*(submit|save)\\s*$/i.test(norm(x.textContent||x.value)));"
                + " if(!b) return false; b.id='__prSubmit'; return true; }"));
        if (!tagged) { System.out.println("PayableRoster.submitAndGetToast: Submit button not found"); page.offResponse(onResp); return ""; }
        try { page.locator("#__prSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("PayableRoster.submitAndGetToast: real click failed, dispatching in-page click - " + e.getMessage());
            try { page.evaluate("() => { const b=document.getElementById('__prSubmit'); if(b) b.click(); }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const b=document.getElementById('__prSubmit'); if(b) b.removeAttribute('id'); }");

        try {
            page.waitForFunction("() => [...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].some(el=>el.offsetParent!==null && /saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(el.textContent||''))"
                    + " || (window.__prToasts||[]).some(a=>/saved|success|added|updated|please|enter|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { page.waitForTimeout(2000); }
        waitForAngular(500);
        try { toastPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(6000)); }
        catch (Exception e) { System.out.println("PayableRoster.submitAndGetToast: toast screenshot failed - " + e.getMessage()); }
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastSaveHttp = http.isEmpty() ? "(no matching / failing HTTP call seen)" : String.join(" ;; ", http);
        System.out.println("PayableRoster save HTTP => " + lastSaveHttp);
        Object all = page.evaluate("() => JSON.stringify(window.__prToasts||[])");
        lastToasts = all == null ? "[]" : all.toString();
        Object t = page.evaluate("() => { const a=window.__prToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        return t == null ? "" : t.toString().trim();
    }
}
