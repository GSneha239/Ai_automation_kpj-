package com.kpj.pages.Finance_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Finance &gt; <b>Sap Integration</b> — Page Object ({@code #/sapintegration}).
 *
 * <p>Checked by hand and against SapIntegrationController.js / Billing/Load/SapIntegration (build v2026.11.17.488):</p>
 * <ul>
 *   <li>Filters: <b>Location</b> ({@code SapIntegrationData.LocationID}), <b>From Date</b> (today by default; changing it copies
 *       the date into To Date), <b>To Date</b> (always disabled — one day at a time), <b>Store</b>
 *       ({@code SapIntegrationData.StoreID}); Patient Type is hidden.</li>
 *   <li><b>Search</b> ({@code Searchsapintegration()}) posts {@code /api/SapIntegration/Fetch} and fills the
 *       <b>Sap Integration Detail List</b> grid ({@code gridOptions}, ~30 SAP columns, server paging 25–125) and the
 *       <b>Error Log</b> tab's two grids: <b>Cr-Dr Mismatch</b> ({@code gridErrorOptions}) and <b>Blank GL Code/Posting Key</b>
 *       ({@code gridErrorDetailOptions}).</li>
 *   <li><b>Save</b> ({@code fnSaveData()}) — found broken by hand: it calls {@code AccountingDataFactory}, which this screen does not
 *       load ("AccountingDataFactory is not defined"). <b>Clear</b> ({@code resetForm()}) resets {@code BillData}, an object this
 *       screen does not use, so nothing is cleared.</li>
 * </ul>
 */
public class SapIntegration extends BasePage {

    private static final String HREF = "#/sapintegration";
    private static final String FORM = "#frmAccountingInterface";
    private final java.util.List<String> consoleErrors = new java.util.ArrayList<>();

    public SapIntegration(Page page) {
        super(page);
        page.onConsoleMessage(m -> { if ("error".equals(m.type())) consoleErrors.add(m.text()); });
    }

    // ---- navigation -------------------------------------------------------

    /** Finance &gt; Sap Integration through the sidebar. True only when the screen is really on view. */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*finance\\s*$");
            Object tagged = page.evaluate("(h) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*finance\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const root=top ? top.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase()===h);"
                    + " if(!a) return false; a.id='__sapLink'; return true; }", HREF);
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__sapLink").click(new Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("SapIntegration.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__sapLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try { page.waitForSelector(FORM, new Page.WaitForSelectorOptions().setTimeout(30000)); }
        catch (Exception ignore) { System.out.println("SapIntegration.nav: the form is not up"); }
        if (onScreen()) installHelpers();
        return onScreen();
    }

    /** On this screen — the route AND its form. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains(HREF)) return false;
        return Boolean.TRUE.equals(page.evaluate("(s) => !!document.querySelector(s)", FORM));
    }

    private void installHelpers() {
        page.evaluate("() => {"
                + "window.__alerts = window.__alerts || [];"
                + "['JAlert','jAlert','alert'].forEach(n => { if (window[n] && !window['__o_'+n]) { window['__o_'+n]=window[n];"
                + "  window[n]=function(m){ try{window.__alerts.push(String(m));}catch(e){} return window['__o_'+n].apply(this,arguments); }; } });"
                + "window.__sapScope = function(){ const f=document.querySelector('" + FORM + "'); if(!f||!window.angular) return null; let s=angular.element(f).scope();"
                + "  for(let i=0;i<10&&s;i++){ if(s.SapIntegrationData!==undefined && s.gridOptions) return s; s=s.$parent; } return null; };"
                + "}");
        waitForAngular(1500);
    }

    // ---- filters ----------------------------------------------------------

    /** {location, fromDate, toDate, toDateDisabled, store} as shown on the screen. */
    public String[] filters() {
        Object r = page.evaluate("() => { const sel=ng=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); return e && e.selectedIndex>=0 ? e.options[e.selectedIndex].text.trim() : ''; };"
                + " const inp=ng=>[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng);"
                + " const f=inp('SapIntegrationData.FromDate'), t=inp('SapIntegrationData.ToDate');"
                + " return [sel('SapIntegrationData.LocationID'), f?f.value:'', t?t.value:'', String(!!(t&&t.disabled)), sel('SapIntegrationData.StoreID')]; }");
        String[] out = {"", "", "", "", ""};
        if (r instanceof java.util.List) {
            java.util.List<?> l = (java.util.List<?>) r;
            for (int i = 0; i < out.length && i < l.size(); i++) out[i] = String.valueOf(l.get(i));
        }
        return out;
    }

    /** Make sure a Location is chosen (the login location when none is). Returns the Location shown. */
    public String ensureLocation() {
        page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='SapIntegrationData.LocationID');"
                + " if(!e || e.selectedIndex>0 || e.options.length<2) return; e.selectedIndex=1; e.dispatchEvent(new Event('change',{bubbles:true})); }");
        waitForAngular(600);
        return filters()[0];
    }

    /** Set From Date (dd/MM/yyyy) through its ng-model so the screen's own ng-change (copy to To Date) runs. Returns {from, to}. */
    public String[] setFromDate(String ddMMyyyy) {
        page.evaluate("(v) => { const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='SapIntegrationData.FromDate'); if(!e) return;"
                + " const c=angular.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
                + " e.dispatchEvent(new Event('change',{bubbles:true})); document.body.click(); }", ddMMyyyy);
        waitForAngular(800);
        String[] f = filters();
        return new String[]{f[1], f[2]};
    }

    /** Choose the first real Store. Returns the Store chosen ("" when the list is empty). */
    public String chooseFirstStore() {
        Object r = page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='SapIntegrationData.StoreID');"
                + " if(!e || e.options.length<2) return ''; e.selectedIndex=1; e.dispatchEvent(new Event('change',{bubbles:true})); return e.options[1].text.trim(); }");
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    // ---- search / grids ---------------------------------------------------

    /**
     * Click Search (real click) and wait for {@code /api/SapIntegration/Fetch}. Returns the request body that was sent
     * (so a caller can see which filters went to the server), or null when no fetch answered.
     */
    public String search() {
        try {
            com.microsoft.playwright.Response resp = page.waitForResponse(r -> r.url().contains("/api/SapIntegration/Fetch"),
                    new Page.WaitForResponseOptions().setTimeout(60000),
                    () -> page.locator(FORM + " button[ng-click^='Searchsapintegration']").first().click(new Locator.ClickOptions().setTimeout(8000)));
            waitForAngular(1500);
            return resp == null ? null : String.valueOf(resp.request().postData());
        } catch (Exception e) {
            System.out.println("SapIntegration.search: " + String.valueOf(e.getMessage()).split("\n")[0]);
            return null;
        }
    }

    /** Rows held by a grid: "detail", "mismatch" (Cr-Dr Mismatch) or "blankgl" (Blank GL Code/Posting Key). -1 when not loaded. */
    public int gridRows(String which) {
        String model = "mismatch".equals(which) ? "gridErrorOptions" : "blankgl".equals(which) ? "gridErrorDetailOptions" : "gridOptions";
        Object r = page.evaluate("(m) => { const s=window.__sapScope(); return s && s[m] && Array.isArray(s[m].data) ? s[m].data.length : -1; }", model);
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Total records the server reported for the Detail List (paging). */
    public int totalRecords() {
        Object r = page.evaluate("() => { const s=window.__sapScope(); return s && s.gridOptions ? (s.gridOptions.totalItems||0) : 0; }");
        return r instanceof Number ? ((Number) r).intValue() : 0;
    }

    /** First Detail List row as "DocGroupNo | Document Header | Ref Doc | Cr/Dr | amount" (empty when there is none). */
    public String firstDetailRow() {
        Object r = page.evaluate("() => { const s=window.__sapScope(); const d=s && s.gridOptions && s.gridOptions.data ? s.gridOptions.data[0] : null; if(!d) return '';"
                + " return [d.Document_Grouping_Number, d.Document_Header, d.Reference_Document_Number, d.Cr_Dr, d.Amount_in_local_currency].join(' | '); }");
        return r == null ? "" : r.toString();
    }

    /** Column headers drawn on the Detail List grid. */
    @SuppressWarnings("unchecked")
    public java.util.List<String> detailColumns() {
        Object r = page.evaluate("() => { const s=window.__sapScope(); return s && s.gridOptions && s.gridOptions.columnDefs ? s.gridOptions.columnDefs.filter(c=>c.visible!==false).map(c=>String(c.displayName||c.field)) : []; }");
        return r instanceof java.util.List ? (java.util.List<String>) r : new java.util.ArrayList<>();
    }

    /** Open a tab by its label ("Sap Integration Detail List" / "Error Log") with a real click. */
    public void openTab(String label) {
        page.locator(".nav-tabs a").filter(new Locator.FilterOptions().setHasText(label)).first().click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(1200);
    }

    /** True when a panel heading with this text is on view (e.g. "Cr-Dr Mismatch"). */
    public boolean headingShown(String text) {
        return Boolean.TRUE.equals(page.evaluate("(t) => [...document.querySelectorAll('.panel-heading')].some(h=>h.offsetParent!==null && h.textContent.trim()===t)", text));
    }

    // ---- save / clear -----------------------------------------------------

    /** Click Save (real click). Returns what happened: the alert(s) shown and/or the console error(s) raised. */
    public String clickSave() {
        return clickFooter("fnSaveData");
    }

    /** Click Clear (real click). Returns any alert / console error (normally none). */
    public String clickClear() {
        return clickFooter("resetForm");
    }

    private String clickFooter(String fn) {
        page.evaluate("() => { window.__alerts=[]; }");
        int before = consoleErrors.size();
        page.locator("button[ng-click^='" + fn + "']").first().click(new Locator.ClickOptions().setTimeout(8000));
        waitForAngular(2500);
        java.util.List<String> out = new java.util.ArrayList<>();
        Object a = page.evaluate("() => (window.__alerts||[]).filter(m=>!/^(error|success|warning|info)$/i.test(String(m).trim()))");
        if (a instanceof java.util.List) for (Object o : (java.util.List<?>) a) out.add("message: " + o);
        for (String e : consoleErrors.subList(before, consoleErrors.size())) {
            if (!e.contains("Unexpected token")) out.add("console error: " + e.split("\n")[0]);
        }
        return String.join(" | ", out);
    }
}
