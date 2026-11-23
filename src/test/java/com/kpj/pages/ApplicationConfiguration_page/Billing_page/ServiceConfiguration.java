package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Service Configuration</b> — configuration screen Page Object.
 *
 * <p>Flow: <b>Application Configuration</b> → <b>Billing</b> → <b>Service Configuration</b> (route
 * {@code #/ServiceConfiguration}) → <b>Add</b> ({@code #/add-ServiceConfiguration}) → select <b>Service</b>
 * ({@code ServiceConfiguration.serviceitemid}) → tick the <b>Select</b> checkbox ({@code Ser.ChkServiceList}) and
 * choose a <b>Processing Location</b> ({@code Ser.processinglocationid}) → <b>Submit</b> ({@code fnIUDServices})
 * → toast → verify in the list table.</p>
 *
 * <p>Selecting a Service populates a per-<b>Registration Location</b> grid; each row has a Select checkbox +
 * a Processing Location dropdown.</p>
 */
public class ServiceConfiguration extends BasePage {

    public ServiceConfiguration(Page page) { super(page); }

    public static String LIST_ROUTE = "#/ServiceConfiguration";
    public static String ADD_ROUTE = "#/add-ServiceConfiguration";
    public String lastService = "", lastLocation = "";

    // ---- navigation ------------------------------------------------------

    /** Menu entries on screen when the walk was made — dumped into the report if the screen is not reached. */
    public String lastMenu = "";
    public String lastRoute = "";
    /** Failing / Service Configuration HTTP calls seen while the menu click loaded the screen. */
    public String lastNavHttp = "";

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
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*service\\s*configuration\\s*$/i.test(norm(x.textContent)));"
                + " if(!a) return ''; a.id='__scMenu'; return a.getAttribute('href')||'link'; }");
        lastRoute = href == null ? "" : href.toString();
        // What the browser asked for while the screen was loading — a view/API that 404s or 500s explains a route that resolves but never renders.
        final java.util.List<String> http = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                String u = resp.url();
                if (resp.status() >= 400 || u.toLowerCase().contains("serviceconfig")) {
                    String body = "";
                    if (resp.status() >= 400) { try { body = resp.text(); } catch (Exception ignore) { } }
                    if (body.length() > 200) body = body.substring(0, 200) + "…";
                    http.add(resp.status() + " " + u + (body.isEmpty() ? "" : " => " + body.replaceAll("\\s+", " ").trim()));
                }
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);
        if (!lastRoute.isEmpty()) {
            try { page.locator("#__scMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
            catch (Exception e) { System.out.println("ServiceConfiguration.nav: click failed - " + e.getMessage().split("\n")[0]); }
            page.evaluate("() => { const e=document.getElementById('__scMenu'); if(e) e.removeAttribute('id'); }");
            waitForAngular(2500);
        }
        boolean reached = onScreen();
        try { page.offResponse(onResp); } catch (Exception ignore) { }
        lastNavHttp = http.isEmpty() ? "(no failing or ServiceConfiguration HTTP call seen)" : String.join(" ;; ", http);
        // Direct hash navigation is bounced to #/PatientDashboard by the app, so it is NOT a way in — only the menu link is.
        if (!reached) {
            lastMenu = String.valueOf(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " return [...new Set([...document.querySelectorAll('a')].filter(a=>a.offsetParent!==null)"
                    + "   .map(a=>norm(a.textContent)+' -> '+(a.getAttribute('href')||'')).filter(t=>t.length>4&&t.length<70))].slice(0,140).join('\\n'); }"));
            return false;
        }
        return true;
    }

    /** On the Service Configuration list/add screen — the route AND the screen's own control must be there (a bounce to the dashboard fails). */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("serviceconfiguration")) return false;
        try {
            page.waitForFunction("() => [...document.querySelectorAll('button,a,input[type=button]')].some(x=>x.offsetParent!==null && /^\\s*add\\s*$/i.test(((x.textContent||x.value)||'').replace(/\\s+/g,' ').trim()))"
                    + " || [...document.querySelectorAll('select')].some(e=>e.offsetParent!==null && (e.getAttribute('ng-model')||'')==='ServiceConfiguration.serviceitemid')",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));   // the list is large; one run took >15 s to render
        } catch (Exception e) { return false; }
        return page.url().toLowerCase().contains("serviceconfiguration");
    }

    // ---- actions ---------------------------------------------------------

    /** Real-click the <b>Add</b> button (polls). Lands on {@code #/add-ServiceConfiguration}. */
    public boolean clickAdd() {
        boolean tagged = false;
        for (int i = 0; i < 15 && !tagged; i++) {
            tagged = Boolean.TRUE.equals(page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/^\\s*add\\s*$/i.test(norm(x.textContent||x.value)) && x.offsetParent!==null); if(!b) return false; b.id='__scAdd'; return true; }"));
            if (!tagged) page.waitForTimeout(500);
        }
        if (!tagged) {
            System.out.println("ServiceConfiguration.clickAdd: Add button not found");
            Object dbg = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                    + " const btns=[...document.querySelectorAll('button,a,input[type=button]')].filter(e=>e.offsetParent!==null).map(e=>norm(e.textContent||e.value)).filter(Boolean).slice(0,25);"
                    + " return 'header=\"'+hdr+'\" url='+location.href+' buttons=['+btns.join(' , ')+']'; }");
            System.out.println("ServiceConfiguration.clickAdd DEBUG: " + dbg);
            return false;
        }
        try { page.locator("#__scAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("ServiceConfiguration.clickAdd: click failed - " + e.getMessage()); }
        try {
            // wait for the Service select to actually have a REAL option (loads async after Add)
            page.waitForFunction("() => { const e=document.querySelector(\"select[ng-model='ServiceConfiguration.serviceitemid']\"); return e && e.offsetParent!==null && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("ServiceConfiguration.clickAdd: Service options not ready"); }
        waitForAngular(800);
        return true;
    }

    /** Number of real Service options. */
    public int serviceCount() {
        Object c = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='ServiceConfiguration.serviceitemid']\"); if(!e) return 0; return [...e.options].filter(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())).length; }");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Select the {@code ordinal}-th (1-based) real Service (real selectOption — fires ng-change to load the
     *  location grid), tick the first Select checkbox and pick the first Processing Location. Returns a summary. */
    public String selectServiceAndConfigure(int ordinal) {
        // Service (real selectOption).
        String sel = "select[ng-model='ServiceConfiguration.serviceitemid']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    sel, new Page.WaitForFunctionOptions().setTimeout(10000));
        } catch (Exception ignore) { }
        Object idx = page.evaluate("(a) => { const e=document.querySelector(a.s); if(!e) return -1; const reals=[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && (x.o.textContent||'').trim() && !/^-*\\s*select/i.test((x.o.textContent||'').trim())); return reals.length>=a.ord ? reals[a.ord-1].i : -1; }",
                java.util.Map.of("s", sel, "ord", ordinal));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) return "Service=(exhausted)";
        try {
            page.locator(sel).selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i));
            lastService = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", sel)).trim();
        } catch (Exception e) { System.out.println("selectServiceAndConfigure: service select failed - " + e.getMessage()); return "Service=(err)"; }
        waitForAngular(1000);
        // Tick the first Select checkbox (real click so ng-change fires).
        String chk = "(none)";
        try {
            page.waitForFunction("() => [...document.querySelectorAll(\"input[type=checkbox][ng-model='Ser.ChkServiceList']\")].some(x=>x.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
        } catch (Exception ignore) { }
        Object tagged = page.evaluate("() => { const cb=[...document.querySelectorAll(\"input[type=checkbox][ng-model='Ser.ChkServiceList']\")].find(x=>x.offsetParent!==null); if(!cb) return false; cb.id='__scChk'; return true; }");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__scChk").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(4000)); chk = "ticked"; }
            catch (Exception e) { try { page.locator("#__scChk").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000)); chk = "clicked"; } catch (Exception e2) { System.out.println("selectServiceAndConfigure: checkbox click failed - " + e2.getMessage()); } }
            page.evaluate("() => { const e=document.getElementById('__scChk'); if(e) e.removeAttribute('id'); }");
        }
        waitForAngular(500);
        // Processing Location — first real option (real selectOption).
        String locSel = "select[ng-model='Ser.processinglocationid']";
        try {
            page.waitForFunction("(s) => { const e=document.querySelector(s); return e && [...e.options].some(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }",
                    locSel, new Page.WaitForFunctionOptions().setTimeout(8000));
            Object li = page.evaluate("(s) => { const e=document.querySelector(s); return [...e.options].findIndex(o=>o.value && (o.textContent||'').trim() && !/^-*\\s*select/i.test((o.textContent||'').trim())); }", locSel);
            int lidx = li instanceof Number ? ((Number) li).intValue() : 1;
            page.locator(locSel).first().selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(lidx));
            lastLocation = String.valueOf(page.evaluate("(s) => { const e=document.querySelector(s); return (e.options[e.selectedIndex]||{}).textContent||''; }", locSel)).trim();
        } catch (Exception e) { System.out.println("selectServiceAndConfigure: location select failed - " + e.getMessage()); lastLocation = "(no-opt)"; }
        waitForAngular(400);
        return "Service=" + lastService + " | SelectCheckbox=" + chk + " | ProcessingLocation=" + lastLocation;
    }

    /** Click <b>Submit</b> ({@code fnIUDServices}) and return the toast (toastr cleared first). */
    public String submitAndGetToast() {
        Object tagged = page.evaluate("() => { window.__scToasts=[]; if(window.__scObs) window.__scObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){} document.querySelectorAll('#toast-container .toast, .toast-message').forEach(t=>t.remove());"
                // Read the message line of each toast; the container ("[id^=toast]") also holds the PREVIOUS attempt's still-fading toast, so its
                // text came back glued together ("Failed!×KPJ PortalService Saved Successfully."). It is only a fallback when no .toast-message exists.
                + " const grab=()=>{ const msgs=[...document.querySelectorAll('.toast-message')]; const els=msgs.length?msgs:[...document.querySelectorAll('.toast,[id^=toast]')];"
                + "   els.forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__scToasts.includes(t)) window.__scToasts.push(t); }); };"
                + " window.__scObs=new MutationObserver(grab); window.__scObs.observe(document.body,{childList:true,subtree:true}); grab();"
                + " const b=[...document.querySelectorAll('button,a,input[type=submit]')].find(x=>/fnIUDServices/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('button,input[type=submit]')].find(x=>/^\\s*submit\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__scSubmit'; return true; }");
        if (!Boolean.TRUE.equals(tagged)) { System.out.println("submitAndGetToast: Submit button not found"); return ""; }
        try { page.locator("#__scSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { System.out.println("submitAndGetToast: Submit click failed - " + e.getMessage()); }
        try {
            page.waitForFunction("() => (window.__scToasts||[]).some(a=>/service|config|saved|success|added|updated|please|select|required|exist|already|error/i.test(a))",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) {
            page.waitForTimeout(2500);
            page.evaluate("() => { document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !(window.__scToasts||[]).includes(t)) (window.__scToasts=window.__scToasts||[]).push(t); }); }");
        }
        Object r = page.evaluate("() => { const a=window.__scToasts||[]; return a.find(x=>/saved|added|success/i.test(x)) || a.find(x=>/exist|already/i.test(x)) || a.find(x=>x) || ''; }");
        waitForAngular(400);
        // The toast's title ("KPJ Portal", with the close "×") is glued to its message in textContent — keep only the message.
        return r == null ? "" : r.toString().replaceFirst("^\\s*×?\\s*KPJ Portal\\s*", "").trim();
    }

    /** After Submit, open the list ({@code #/ServiceConfiguration}) and confirm a grid row matches the saved
     *  Service (filtered via the first column filter if present). Returns the matched row text, or "". */
    public String findAddedInList() {
        // The app returns to the list by itself shortly after a save; give it a moment before deciding it did not.
        for (int i = 0; i < 8 && page.url().toLowerCase().contains("add-"); i++) page.waitForTimeout(500);
        if (page.url().toLowerCase().contains("add-")) {
            // Setting location.hash from the form raises the app's "Reload page" popup, which the framework answers No — so leave
            // with the form's own Back button instead.
            Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')]"
                    + ".find(x=>/^\\s*back\\s*$/i.test((x.textContent||x.value||'').replace(/\\s+/g,' ').trim()) && x.offsetParent!==null);"
                    + " if(!b) return false; b.id='__scBack'; return true; }");
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__scBack").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
                catch (Exception e) { System.out.println("ServiceConfiguration.findAddedInList: Back click failed - " + e.getMessage().split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__scBack'); if(e) e.removeAttribute('id'); }");
                waitForAngular(1500);
            }
        } else if (!page.url().toLowerCase().contains("serviceconfiguration")) {
            try { page.evaluate("(h) => { window.location.hash = h; }", LIST_ROUTE); } catch (Exception ignore) { }
        }
        try {
            page.waitForFunction("() => [...document.querySelectorAll('.ui-grid-row, table tr')].length > 0",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { }
        waitForAngular(1000);
        // Match on a distinctive token of the service name (first word) in the column filter.
        String token = lastService.trim().split("\\s+").length > 0 ? lastService.trim().split("\\s+")[0] : lastService.trim();
        page.evaluate("(t) => { const f=document.querySelector('.ui-grid-filter-input'); if(f){ const c=window.angular&&angular.element(f).controller('ngModel'); f.value=t; if(c){c.$setViewValue(t);c.$render();} f.dispatchEvent(new Event('input',{bubbles:true})); f.dispatchEvent(new Event('keyup',{bubbles:true})); f.dispatchEvent(new Event('change',{bubbles:true})); } }", token);
        waitForAngular(1500);
        Object r = page.evaluate("(svc) => { const up=s=>(s||'').replace(/\\s+/g,' ').trim().toUpperCase(); const ss=up(svc);"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].length ? [...document.querySelectorAll('.ui-grid-row')] : [...document.querySelectorAll('table tbody tr')];"
                + " for(const row of rows){ const t=up(row.textContent||''); if(ss && t.includes(ss.slice(0,Math.min(ss.length,14)))) return (row.textContent||'').replace(/\\s+/g,' ').trim().slice(0,160); }"
                + " let found=''; document.querySelectorAll('*').forEach(el=>{ if(found) return; try{ const s=angular.element(el).scope(); const d=(s&&s.grid&&s.grid.options&&s.grid.options.data)||(s&&s.gridOptions&&s.gridOptions.data); if(d){ const m=d.find(x=>up(JSON.stringify(x)).includes(ss.slice(0,Math.min(ss.length,14)))); if(m) found=Object.values(m).filter(v=>v!=null && typeof v!=='object').join(' | ').slice(0,160); } }catch(e){} }); return found; }", lastService);
        return r == null ? "" : r.toString().trim();
    }
}
