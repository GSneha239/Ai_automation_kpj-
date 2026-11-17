package com.kpj.pages.ApplicationConfiguration_page.Billing_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Select2Pick;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Application Configuration &gt; Billing &gt; <b>Pricing Policy Service</b> (route {@code #/TariffServiceMaster}) — Page Object.
 *
 * <p>The screen is a read-only lookup: pick a <b>Pricing Policy</b> (select2 over {@code TariffServiceMst.TariffId}, ~300 policies) →
 * <b>Search</b> ({@code fnFetchTariffServiceMasterGrid()}) → a grid lists that policy's services: Pricing Policy Service Code, Pricing Policy Name,
 * Pricing Policy Service Name, Group Name, Sub Group Name, Status, Effective Date, Edit.</p>
 */
public class PricingPolicyService extends BasePage {

    public PricingPolicyService(Page page) { super(page); }

    public static final String POLICY_NG = "TariffServiceMst.TariffId";

    public String routeSeen = "";
    /** What the Search call(s) returned: "METHOD path status -> n row(s)". */
    public String searchHttp = "";
    /** Row count the server returned for the last Search (-1 when it could not be read). */
    public int serverRows = -1;
    public byte[] searchPng;

    // ---- navigation ------------------------------------------------------

    public boolean navigateViaMenu() {
        try {
            page.waitForFunction("() => [...document.querySelectorAll('a')].some(a=>/application\\s*config/i.test((a.textContent||'')) && a.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { }
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(900);
        // "Billing" is also a top-level module: look only inside the Application Configuration entry.
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const top=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); const scope=(top&&top.closest('li'))||document; const a=[...scope.querySelectorAll('a')].find(x=>/^\\s*billing\\s*$/i.test(norm(x.textContent)) && x.offsetParent!==null); if(a) a.click(); }");
        waitForAngular(800);
        Object href = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const top=[...document.querySelectorAll('a')].find(x=>/application\\s*config/i.test(norm(x.textContent)) && x.offsetParent!==null); const scope=(top&&top.closest('li'))||document;"
                + " const a=[...scope.querySelectorAll('a[href]')].find(x=>/^\\s*pricing\\s*policy\\s*service\\s*$/i.test(norm(x.textContent))); if(!a) return '';"
                + " a.id='__ppsMenu'; return a.getAttribute('href')||'link'; }");
        routeSeen = href == null ? "" : href.toString();
        if (!routeSeen.isEmpty()) {
            try { page.locator("#__ppsMenu").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("PricingPolicyService.nav: click failed - " + e.getMessage()); }
            waitForAngular(1500);
        }
        if (!onScreen()) {
            System.out.println("PricingPolicyService.nav: direct route #/TariffServiceMaster");
            try { page.evaluate("() => { window.location.hash = '#/TariffServiceMaster'; }"); } catch (Exception ignore) { }
            waitForAngular(1600);
        }
        try {
            page.waitForFunction("() => { const e=document.querySelector(\"select[ng-model='TariffServiceMst.TariffId']\"); return e && e.options.length>5; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("PricingPolicyService.nav: Pricing Policy list not loaded"); }
        return onScreen();
    }

    public boolean onScreen() { return page.url().toLowerCase().contains("tariffservicemaster"); }

    // ---- Pricing Policy dropdown ----------------------------------------

    /** Every Pricing Policy the dropdown offers (unique names, list order). */
    public List<String> policyNames() {
        List<String> all = Select2Pick.names(page, POLICY_NG);
        List<String> out = new ArrayList<>();
        for (String n : all) if (!out.contains(n)) out.add(n);
        return out;
    }

    /** Pick the policy from the select2 list like a user; "" = chosen, otherwise the reason it was not. */
    public String selectPolicy(String name) { return Select2Pick.pick(page, POLICY_NG, name); }

    // ---- Search + table ---------------------------------------------------

    /** Click <b>Search</b> and wait for the grid to settle; records what the server returned. */
    public boolean clickSearch() {
        final List<String> http = Collections.synchronizedList(new ArrayList<>());
        final int[] rows = {-1};
        java.util.function.Consumer<com.microsoft.playwright.Response> onResp = resp -> {
            try {
                String u = resp.url();
                if (!u.toLowerCase().contains("tariffservice")) return;
                String rb = ""; try { rb = resp.text(); } catch (Exception ignore) { }
                int n = -1;
                if (rb.trim().equals("[]")) n = 0;
                else if (rb.trim().startsWith("[")) n = rb.split("\\},\\s*\\{", -1).length;
                else { java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"TariffServiceMasterID\"|\"TariffServiceID\"").matcher(rb); int c = 0; while (m.find()) c++; if (c > 0) n = c; }
                http.add(resp.request().method() + " " + u.replaceFirst("^https?://[^/]+", "") + " " + resp.status() + " -> " + (n >= 0 ? n + " row(s)" : "(" + rb.length() + " chars)"));
                if (n >= 0) rows[0] = n;
            } catch (Exception ignore) { }
        };
        page.onResponse(onResp);
        try {
            Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>/fnFetchTariffServiceMasterGrid/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null)"
                    + " || [...document.querySelectorAll('button,input[type=button]')].find(x=>/^\\s*search\\s*$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(!b) return false; b.id='__ppsSearch'; return true; }");
            if (!Boolean.TRUE.equals(tagged)) { System.out.println("PricingPolicyService.clickSearch: Search button not found"); return false; }
            try { page.locator("#__ppsSearch").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("PricingPolicyService.clickSearch: click failed - " + e.getMessage()); return false; }
            finally { page.evaluate("() => { const e=document.getElementById('__ppsSearch'); if(e) e.removeAttribute('id'); }"); }
            // wait for the call, then for the rows to render (or settle at none)
            for (int i = 0; i < 40 && http.isEmpty(); i++) page.waitForTimeout(250);
            int last = -2;
            for (int i = 0; i < 40; i++) {
                int now = gridRowCount();
                if (now == last && (now > 0 || i > 8)) break;
                last = now;
                page.waitForTimeout(400);
            }
            waitForAngular(500);
            try { searchPng = page.screenshot(new Page.ScreenshotOptions().setTimeout(8000)); } catch (Exception ignore) { searchPng = null; }
        } finally {
            try { page.offResponse(onResp); } catch (Exception ignore) { }
        }
        searchHttp = http.isEmpty() ? "(no TariffService call seen)" : String.join(" ;; ", http);
        serverRows = rows[0];
        return true;
    }

    /** Rows currently drawn in the table body. */
    public int gridRowCount() {
        Object c = page.evaluate("() => [...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(r=>r.offsetParent!==null).length");
        return c instanceof Number ? ((Number) c).intValue() : 0;
    }

    /** Header texts of the table, in order (sort arrows / blanks left out). */
    public List<String> headers() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " return [...document.querySelectorAll('.ui-grid-header-cell')].filter(e=>e.offsetParent!==null).map(e=>norm(e.textContent).replace(/\\s*\\d+$/,'')).filter(t=>t); }");
        List<String> out = new ArrayList<>();
        if (r instanceof List) for (Object o : (List<?>) r) out.add(String.valueOf(o));
        return out;
    }

    /** Body rows as cell-text lists (the cell texts of each drawn row, empty cells kept so columns stay aligned). */
    public List<List<String>> rows() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " return [...document.querySelectorAll('.ui-grid-render-container-body .ui-grid-row')].filter(x=>x.offsetParent!==null)"
                + "   .map(row=>[...row.querySelectorAll('.ui-grid-cell')].map(c=>norm(c.textContent))); }");
        List<List<String>> out = new ArrayList<>();
        if (r instanceof List) for (Object row : (List<?>) r) {
            List<String> cells = new ArrayList<>();
            if (row instanceof List) for (Object c : (List<?>) row) cells.add(String.valueOf(c));
            out.add(cells);
        }
        return out;
    }

    /** The grid's total item count when the screen exposes it (pager text / grid data), else -1. */
    public int totalItems() {
        Object r = page.evaluate("() => { try { const el=document.querySelector('.ui-grid, [ui-grid]'); if(!el) return -1;"
                + " const sc=angular.element(el).isolateScope()||angular.element(el).scope(); const g=sc.grid; if(g&&g.options&&g.options.data) return g.options.data.length;"
                + " const o=sc.gridOptions||(sc.$parent&&sc.$parent.gridOptions); if(o&&o.data) return o.data.length; return -1; } catch(e){ return -1; } }");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    /** Text the screen shows instead of rows (e.g. "No data"), if any. */
    public String emptyMessage() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('.ui-grid-empty-message,.no-data,.noData,[ng-show*=\"length==0\"],[ng-show*=\"length===0\"]')].find(x=>x.offsetParent!==null && norm(x.textContent));"
                + " return e? norm(e.textContent) : ''; }");
        return r == null ? "" : r.toString();
    }

    /** What the Pricing Policy box shows now. */
    public String shownPolicy() { return Select2Pick.chosen(page, POLICY_NG); }
}
