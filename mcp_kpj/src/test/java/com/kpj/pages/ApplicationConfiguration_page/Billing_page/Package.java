package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; Billing &gt; <b>Package</b> — configuration screen Page Object.
 *
 * <p>Read from the screen's own views and controllers (BillingConfig/Load/PackageList + PackageListController.js,
 * BillingConfig/Load/Package + PackageController.js, build v2026.11.18.491).</p>
 * <ul>
 *   <li><b>List</b> ({@code #/Package}): Class + Pricing Policy filters, Search ({@code fngetPackageGrid}), Add
 *       ({@code addPackage} → {@code #/add-Package}); grid: Package Code, Package Name, Validity From/To, Tariff, Rate,
 *       Status tick ({@code changeStatus}), Edit ({@code editPackage} → {@code #/edit-Package<id>}), Apply Tariff popup.</li>
 *   <li><b>Form</b>: OP / IP Package, Applicable Tariff ticks; tabs Package Definition (Code, Description, Validity From/To,
 *       Service Name), Services (Add Services popup, Quantity, class rates), Group (IP only), Item; buttons Import / Export
 *       Excel, ManualRate, AutoRate, Submit ({@code fnIUDPackage}), Back ({@code closeForm}).</li>
 * </ul>
 */
public class Package extends BasePage {

    public static final String ROUTE = "#/Package";
    public String lastCode = "", lastDescription = "";

    public Package(Page page) { super(page); }

    // ---- navigation ------------------------------------------------------

    /** Application Configuration → Billing → Package through the sidebar (exact href inside the Application Configuration group). */
    public boolean navigateViaMenu() {
        return navigateVia("application configuration", "billing");
    }

    /**
     * Ancillary Services → CRM → Package through the sidebar. The CRM menu links to the SAME screen ({@code #/Package}) as
     * Application Configuration → Billing → Package, so every other method of this page object works the same after it.
     */
    public boolean navigateViaCrmMenu() {
        return navigateVia("ancillary services", "crm");
    }

    /** Open {@code top} → {@code group} in the sidebar and click the Package link (exact href) inside {@code top}'s own entry. */
    private boolean navigateVia(String top, String group) {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        String topRe = "^\\s*" + top + "\\s*$";
        for (int attempt = 1; attempt <= 3 && !onList(); attempt++) {
            expandMenuEntry(null, topRe);
            expandMenuEntry(topRe, "^\\s*" + group + "\\s*$");
            Object tagged = page.evaluate("([h,t]) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const top=[...document.querySelectorAll('a')].find(x=>norm(x.textContent).toLowerCase()===t && vis(x));"
                    + " const root=top ? top.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__pkLink'; return true; }", java.util.Arrays.asList(ROUTE.toLowerCase(), top));
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__pkLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("Package.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__pkLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                System.out.println("Package.nav: menu link " + ROUTE + " not found (attempt " + attempt + ")");
                page.waitForTimeout(1500);
            }
        }
        prepare();
        try { page.waitForFunction("() => { const s=window.__pkScope('fngetPackageGrid'); return !!s; }", null, new Page.WaitForFunctionOptions().setTimeout(30000)); }
        catch (Exception ignore) { System.out.println("Package.nav: the list is not up"); }
        return onList();
    }

    /** On the Package LIST screen (route + its Search). */
    public boolean onList() {
        if (!page.url().toLowerCase().matches(".*#/package/?$")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => !![...document.querySelectorAll('button')].find(b=>/fngetPackageGrid/.test(b.getAttribute('ng-click')||''))"));
    }

    /** On the Add / Edit Package FORM (its Submit). */
    public boolean onForm() {
        return Boolean.TRUE.equals(page.evaluate("() => !![...document.querySelectorAll('button')].find(b=>/fnIUDPackage/.test(b.getAttribute('ng-click')||'') && b.offsetParent!==null)"));
    }

    /** What the app actually rendered — header + URL, for reporting a wrong page. */
    public String currentScreen() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const hdr=norm((document.querySelector('.content-header,.main-header,h1,section.content-header')||{}).textContent||'');"
                + " return (hdr||'(no header)').slice(0,60); }");
        return (r == null ? "(unknown)" : r.toString()) + " @ " + page.url();
    }

    /** Message hook (JAlert) + scope finder. Safe to call more than once. */
    public void prepare() {
        page.evaluate("() => {"
                + "window.__alerts = window.__alerts || [];"
                + "['JAlert','jAlert','alert'].forEach(n => { if (window[n] && !window['__o_'+n]) { window['__o_'+n]=window[n];"
                + "  window[n]=function(m){ try{window.__alerts.push(String(m).replace(/<br\\s*\\/?>/gi,' '));}catch(e){} return window['__o_'+n].apply(this,arguments); }; } });"
                + "window.__pkScope = function(fn){ for (const el of document.querySelectorAll('[ng-controller],.ng-scope')) { let s=angular.element(el).scope();"
                + "  for(let i=0;i<8&&s;i++){ if(s[fn]) return s; s=s.$parent; } } return null; };"
                + "}");
    }

    /** Messages shown since the last reset. */
    public String messages() {
        Object r = page.evaluate("() => (window.__alerts||[]).join(' | ')");
        return r == null ? "" : r.toString().trim();
    }

    public void resetMessages() { page.evaluate("() => { window.__alerts=[]; }"); }

    /** Wait (up to {@code ms}) for a message, then return all messages shown. */
    public String waitMessage(int ms) {
        try { page.waitForFunction("() => (window.__alerts||[]).length > 0", null, new Page.WaitForFunctionOptions().setTimeout(ms)); } catch (Exception ignore) { }
        waitForAngular(500);
        return messages();
    }

    // ---- list ------------------------------------------------------------

    /** Real-click the list's Add button. Lands on the Add Package form. */
    public boolean clickAdd() {
        page.locator("a[ng-click='addPackage()'], button[ng-click='addPackage()']").first().click(new Locator.ClickOptions().setTimeout(8000));
        try { page.waitForSelector("button[ng-click='fnIUDPackage()']", new Page.WaitForSelectorOptions().setTimeout(30000)); } catch (Exception ignore) { }
        prepare();
        try { page.waitForFunction("() => { const s=window.__pkScope('fnIUDPackage'); return s && (s.servicename||[]).length > 0; }", null, new Page.WaitForFunctionOptions().setTimeout(30000)); }
        catch (Exception ignore) { System.out.println("Package.add: Service Name list did not load"); }
        return onForm();
    }

    // ---- form ------------------------------------------------------------

    /**
     * Service Names that have NO package yet — asked the same way the screen asks when one is picked (SERVICEPRESENT check),
     * stopping after {@code max} free ones. Returns their option texts.
     */
    @SuppressWarnings("unchecked")
    public java.util.List<String> freeServiceNames(int max) {
        Object r = page.evaluate("async (max) => { const s=window.__pkScope('fnIUDPackage'); if(!s) return []; const $http=angular.element(document.body).injector().get('$http');"
                + " const out=[]; for (const o of (s.servicename||[])) { if (out.length>=max) break;"
                + "  try { const res=await $http.post('/api/Package/IUD', {serviceitemid:o.value, ExecFlag:'SERVICEPRESENT'}); if (res.data && res.data[0] && res.data[0].ResultStatus==1) out.push(o.text); } catch(e) {} }"
                + " return out; }", max);
        return r instanceof java.util.List ? (java.util.List<String>) r : new java.util.ArrayList<>();
    }

    /** Pick a Service Name through its select2 box (real clicks + typing). Returns the messages it raised (waits up to 8 s). */
    public String chooseServiceName(String optionText) {
        openTab("(Package Definition|Health Plan)");   // the Service Name box lives on this tab
        resetMessages();
        Locator box = page.locator("select[ng-model='Package.serviceitemid']").locator("xpath=preceding-sibling::div[contains(@class,'select2-container')][1]");
        box.locator("a.select2-choice").click(new Locator.ClickOptions().setTimeout(6000));
        Locator input = page.locator(".select2-drop-active input.select2-input");
        String code = optionText.split(" - ")[0].trim();
        input.fill(code);
        waitForAngular(800);
        Locator hit = page.locator(".select2-drop-active .select2-results li.select2-result-selectable").filter(new Locator.FilterOptions().setHasText(optionText.length() > 40 ? optionText.substring(0, 40) : optionText)).first();
        if (hit.count() > 0) hit.click(new Locator.ClickOptions().setTimeout(5000)); else page.keyboard().press("Enter");
        try { page.waitForFunction("() => { const s=window.__pkScope('fnIUDPackage'); return (s && (s.checkboxes||[]).length>0) || (window.__alerts||[]).length>0; }", null,
                new Page.WaitForFunctionOptions().setTimeout(8000)); } catch (Exception ignore) { }
        waitForAngular(800);
        return messages();
    }

    /** The Applicable Tariff ticks shown on the left, as the user sees them: "text | ticked | enabled". */
    public java.util.List<String> tariffs() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"[ng-repeat='checkbox in checkboxes']\")].map(d=>{ const c=d.querySelector('input[type=checkbox]');"
                + " return d.innerText.replace(/\\s+/g,' ').trim()+' | ticked='+(c&&c.checked)+' | enabled='+(c&&!c.disabled); })");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (r instanceof java.util.List) for (Object o : (java.util.List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** How many Applicable Tariffs can be ticked (the service is configured in them). */
    public int tickableTariffs() {
        Object r = page.evaluate("() => [...document.querySelectorAll(\"[ng-repeat='checkbox in checkboxes'] input[type=checkbox]\")].filter(c=>!c.disabled && c.offsetParent!==null).length");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /**
     * Tick an Applicable Tariff that can be ticked (real click) — the one whose text matches {@code prefer} when given, else the
     * first. Returns its text, or "" when none can be ticked.
     */
    public String tickTariff(String prefer) {
        Locator rows = page.locator("[ng-repeat='checkbox in checkboxes']");
        int pick = -1;
        for (int i = 0; i < rows.count(); i++) {
            Locator c = rows.nth(i).locator("input[type=checkbox]");
            if (!c.isVisible() || !c.isEnabled()) continue;
            if (pick < 0) pick = i;
            if (prefer != null && rows.nth(i).innerText().toUpperCase().contains(prefer.toUpperCase())) { pick = i; break; }
        }
        if (pick < 0) return "";
        Locator c = rows.nth(pick).locator("input[type=checkbox]");
        if (!c.isChecked()) c.click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(400);
        return rows.nth(pick).innerText().replaceAll("\\s+", " ").trim();
    }

    /** Open a form tab by its text (real click). */
    public void openTab(String text) {
        page.locator("[data-toggle=tab]:visible").filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^\\s*" + text + "\\s*$"))).first()
                .click(new Locator.ClickOptions().setTimeout(5000));
        waitForAngular(600);
    }

    // ---- Services tab / Add Services popup ---------------------------------

    /** Services tab → click Add Services (real click). Returns the message it raised, or "" when the popup opened. */
    public String openAddServices() {
        openTab("Services");
        resetMessages();
        page.locator("button[ng-click='fnIUDServices()']:visible").first().click(new Locator.ClickOptions().setTimeout(6000));
        try { page.waitForSelector("button[ng-click='fnSearch()']", new Page.WaitForSelectorOptions().setTimeout(10000)); } catch (Exception ignore) { }
        waitForAngular(1000);
        return popupOpen() ? "" : messages();
    }

    public boolean popupOpen() {
        return page.locator("button[ng-click='fnSearch()']:visible").count() > 0;
    }

    /** In the popup: type a Name (real typing), click Search, wait for the rows. Returns how many rows are listed. */
    public int searchServices(String name) {
        Locator nm = page.locator("input[ng-model='Services.ServiceName']:visible").first();
        nm.fill(name);
        page.locator("button[ng-click='fnSearch()']:visible").first().click(new Locator.ClickOptions().setTimeout(6000));
        try { page.waitForFunction("() => { const s=window.__pkScope('fnSearch'); return s && (s.ServiceDtlList||[]).length > 0; }", null, new Page.WaitForFunctionOptions().setTimeout(40000)); }
        catch (Exception ignore) { }
        waitForAngular(1200);
        Object r = page.evaluate("() => { const s=window.__pkScope('fnSearch'); return s ? (s.ServiceDtlList||[]).length : -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /**
     * In the popup: tick the first listed service (preferring one with a Rate above 0) by a real click on its Select box.
     * Returns "code - name | rate" of the ticked service, or "" when none is listed.
     */
    public String tickFirstService() {
        Object idx = page.evaluate("() => { const rows=[...document.querySelectorAll(\"[ng-repeat^='Ser in ServiceDtlList']\")].filter(r=>r.offsetParent!==null);"
                + " let i=rows.findIndex(r=>{ const sc=angular.element(r).scope(); return sc && sc.Ser && parseFloat(sc.Ser.rate)>0; }); if(i<0 && rows.length) i=0;"
                + " if(i<0) return -1; rows.forEach(r=>r.removeAttribute('data-pk')); rows[i].setAttribute('data-pk','1'); return i; }");
        if (!(idx instanceof Number) || ((Number) idx).intValue() < 0) return "";
        Locator row = page.locator("[data-pk='1']");
        resetMessages();
        row.locator("input[type=checkbox][ng-model='Ser.ChkServiceList']").click(new Locator.ClickOptions().setTimeout(6000));
        waitForAngular(1500);
        Object d = page.evaluate("() => { const r=document.querySelector(\"[data-pk='1']\"); const sc=r && angular.element(r).scope(); return sc && sc.Ser ? sc.Ser.tariffservicename + ' | rate ' + sc.Ser.rate : ''; }");
        return d == null ? "" : d.toString();
    }

    /** In the popup: click Add (real click). Returns the messages shown. */
    public String addTickedServices() {
        resetMessages();
        page.locator("button[ng-click='fnAdd()']:visible").first().click(new Locator.ClickOptions().setTimeout(6000));
        try { page.waitForFunction("() => { const s=window.__pkScope('fnIUDPackage'); return (s && (s.servicelist||[]).length>0) || (window.__alerts||[]).length>0; }", null,
                new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        waitForAngular(1500);
        return messages();
    }

    /** Close the popup if it is still open (its Close button). */
    public void closePopup() {
        Locator c = page.locator(".modal-footer button[ng-click='resetForm()']:visible");
        if (c.count() > 0) { c.first().click(new Locator.ClickOptions().setTimeout(5000)); waitForAngular(800); }
    }

    /** The services added to the package (Services tab table): "code - name | qty | class rates". */
    public String addedServices() {
        return scopeJson("fnIUDPackage", "(s.servicelist||[]).map(x=>(x.tariffservicename||x.servicename||x.serviceitemid)+' | qty '+x.quantity+' | '+(x.Packagelist||[]).map(p=>(p.classname||p.classid)+'='+p.Packagerate).join(', '))");
    }

    public int addedServiceCount() {
        Object r = page.evaluate("() => { const s=window.__pkScope('fnIUDPackage'); return s ? (s.servicelist||[]).length : -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** The class totals table (Unit Purchase Price per class): "class=rate". */
    public String classTotals() {
        return scopeJson("fnIUDPackage", "(s.servicelist1||[]).map(x=>(x.classname||x.classid)+'='+x.rate).join(', ')");
    }

    // ---- Package Definition / Submit / Back ----------------------------------

    /** Type the Code and Description (real typing). Code is at most 10 characters on this screen. */
    public void fillCodeAndDescription(String code, String description) {
        openTab("(Package Definition|Health Plan)");
        lastCode = code; lastDescription = description;
        page.locator("input[ng-model='Package.code']:visible").first().fill(code);
        page.locator("input[ng-model='Package.description']:visible").first().fill(description);
        waitForAngular(400);
    }

    /** The form's Code / Description / Validity From / Validity To as shown. */
    public String[] formValues() {
        Object r = page.evaluate("() => { const v=s=>{ const e=[...document.querySelectorAll(s)].find(x=>x.offsetParent!==null)||document.querySelector(s); return e ? e.value : ''; };"
                + " return [v(\"input[ng-model='Package.code']\"), v(\"input[ng-model='Package.description']\"), v(\"input[ng-model='Package2.validityfrom']\"), v(\"input[ng-model='Package2.validityto']\")]; }");
        String[] out = {"", "", "", ""};
        if (r instanceof java.util.List) { java.util.List<?> l = (java.util.List<?>) r; for (int i = 0; i < 4 && i < l.size(); i++) out[i] = String.valueOf(l.get(i)); }
        return out;
    }

    /**
     * Click Submit (real click) and return what the screen said (waits up to 20 s). When the save request fails on the server,
     * its answer is appended ("[server 500: …]").
     */
    public String submit() {
        resetMessages();
        final String[] server = {""};
        java.util.function.Consumer<com.microsoft.playwright.Response> grab = r -> {
            if (r.url().toLowerCase().contains("/api/package/iud") && r.status() >= 400) {
                String body; try { body = r.text(); } catch (Exception e) { body = ""; }
                java.util.regex.Matcher ex = java.util.regex.Pattern.compile("\"ExceptionMessage\":\"(.*?)\",\"").matcher(body == null ? "" : body);
                String why = ex.find() ? ex.group(1) : (body == null ? "" : body.replaceAll("\\s+", " "));
                server[0] = "server " + r.status() + ": " + (why.length() > 300 ? why.substring(0, 300) + "…" : why);
            }
        };
        page.onResponse(grab);
        try {
            page.locator("button[ng-click='fnIUDPackage()']:visible").first().click(new Locator.ClickOptions().setTimeout(8000));
            String said = waitMessage(20000);
            return server[0].isEmpty() ? said : (said + " [" + server[0] + "]").trim();
        } finally { page.offResponse(grab); }
    }

    /** Click Back (real click) — returns to the list. */
    public boolean back() {
        Locator b = page.locator("button[ng-click='closeForm()']:visible");
        if (b.count() == 0) return onList();   // not on the form
        b.first().click(new Locator.ClickOptions().setTimeout(6000));
        try { page.waitForFunction("() => /#\\/package\\/?$/i.test(location.hash)", null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception ignore) { }
        waitForAngular(1500);
        prepare();
        return onList();
    }

    // ---- list grid -------------------------------------------------------

    /** Click the list's Search (real click) and wait for the rows. Returns how many packages are listed. */
    public int searchList() {
        prepare();
        // the screen runs its own (empty) load when it opens; let that answer arrive first, or it can land after the
        // Search results and empty the grid again — so Search is clicked a second time if the first came back empty
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 2 && listCount() <= 0; attempt++) {
            page.locator("button[ng-click^='fngetPackageGrid']:visible").first().click(new Locator.ClickOptions().setTimeout(6000));
            try { page.waitForFunction("() => { const s=window.__pkScope('fngetPackageGrid'); return s && (s.gridOptions.data||[]).length > 0; }", null, new Page.WaitForFunctionOptions().setTimeout(30000)); }
            catch (Exception ignore) { }
            waitForAngular(1500);
        }
        return listCount();
    }

    public int listCount() {
        Object r = page.evaluate("() => { const s=window.__pkScope && window.__pkScope('fngetPackageGrid'); return s ? (s.gridOptions.data||[]).length : -1; }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** The list row of this code: "code | name | from - to | pricing policy | rate | active", or null when not listed. */
    public String listRecord(String code) {
        Object r = page.evaluate("(c) => { const s=window.__pkScope && window.__pkScope('fngetPackageGrid'); const d=s ? (s.gridOptions.data||[]) : [];"
                + " const x=d.find(e=>String(e.code).trim().toLowerCase()===String(c).trim().toLowerCase());"
                + " return x ? [x.code, x.description, x.validityfrom + ' - ' + x.validityto, x.text, x.Packagerate, (x.Status ? 'active' : 'inactive')].join(' | ') : null; }", code);
        return r == null ? null : r.toString();
    }

    /** Type the code into the list's Package Code column filter (real typing), so only that package is drawn. */
    public void filterListByCode(String code) {
        Locator f = page.locator(".ui-grid-header-cell").filter(new Locator.FilterOptions().setHasText("Package Code")).locator("input.ui-grid-filter-input");
        if (f.count() > 0) { f.first().fill(code); waitForAngular(1200); }
        else System.out.println("Package.grid: no Package Code filter box");
    }

    /** Bring the grid row of this code into view (filter the list by its code first) and tag one of its controls. */
    private Locator gridControl(String code, String css) {
        filterListByCode(code);
        page.evaluate("(c) => { const s=window.__pkScope('fngetPackageGrid'); if(!s) return; const d=s.gridOptions.data||[];"
                + " const x=d.find(e=>String(e.code).trim().toLowerCase()===c.toLowerCase()); const api=s.gridApi||(s.gridOptions&&s.gridOptions.gridApi);"
                + " try{ if(api && api.core && x) api.core.scrollTo(x, null); }catch(e){} }", code);
        waitForAngular(1200);
        Object ok = page.evaluate("([c,css]) => { document.querySelectorAll('#__pkCtl').forEach(e=>e.removeAttribute('id'));"
                + " const rows=[...document.querySelectorAll('.ui-grid-row')].filter(r=>r.offsetParent!==null);"
                + " const cells=rows.filter(r=>[...r.querySelectorAll('.ui-grid-cell-contents')].some(x=>x.textContent.trim().toLowerCase()===c.toLowerCase()));"
                + " for (const r of cells) { const e=r.querySelector(css); if(e){ e.id='__pkCtl'; e.scrollIntoView({block:'center'}); return true; } }"
                + " if (cells.length) { const top=cells[0].getBoundingClientRect().top;"   // pinned / split containers: same height, other half of the row
                + "   for (const r of rows) { if (Math.abs(r.getBoundingClientRect().top-top)<3) { const e=r.querySelector(css); if(e){ e.id='__pkCtl'; return true; } } } }"
                + " return false; }", java.util.Arrays.asList(code, css));
        if (!Boolean.TRUE.equals(ok)) System.out.println("Package.grid: " + code + " / " + css + " not drawn");
        return Boolean.TRUE.equals(ok) ? page.locator("#__pkCtl") : null;
    }

    /** Click the record's Edit button (real click). Returns {code, description, from, to} loaded into the form. */
    public String[] editRecord(String code) {
        Locator b = gridControl(code, "button[ng-click*='editPackage']");
        if (b == null) return new String[]{"", "", "", ""};
        resetMessages();
        b.click(new Locator.ClickOptions().setTimeout(6000));
        try { page.waitForSelector("button[ng-click='fnIUDPackage()']", new Page.WaitForSelectorOptions().setTimeout(30000)); } catch (Exception ignore) { }
        prepare();
        try { page.waitForFunction("(c) => { const e=document.querySelector(\"input[ng-model='Package.code']\"); return e && e.value.toLowerCase()===c.toLowerCase(); }", code,
                new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        waitForAngular(1500);
        return formValues();
    }

    /** Untick / tick the record's Status in the list (real click). Returns the message shown. */
    public String toggleStatus(String code) {
        Locator cb = gridControl(code, "input[type=checkbox][ng-click*='changeStatus']");
        if (cb == null) return "status tick not found";
        if (!cb.isVisible() || !cb.isEnabled())
            return "The Status tick is hidden or disabled in the list (visible=" + cb.isVisible() + ", enabled=" + cb.isEnabled() + ")";
        resetMessages();
        cb.click(new Locator.ClickOptions().setTimeout(6000));
        return waitMessage(15000);
    }

    /** Evaluate a JS expression with {@code s} = the scope that owns {@code fn}. Returns its JSON string. */
    public String scopeJson(String fn, String expr) {
        Object r = page.evaluate("([fn,expr]) => { const s=window.__pkScope && window.__pkScope(fn); if(!s) return 'no scope';"
                + " try { return JSON.stringify((new Function('s', 'return (' + expr + ')'))(s)); } catch(e) { return 'ERR ' + e.message; } }",
                java.util.Arrays.asList(fn, expr));
        return r == null ? "" : r.toString();
    }
}
