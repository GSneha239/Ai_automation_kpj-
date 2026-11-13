package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

/**
 * Inventory &gt; Purchase &gt; <b>Expired Item Return</b> — Page Object (read-only use).
 *
 * <p>Locators come from the live DOM inspected on hisdemo build v2026.11.13.455 (manual test, 13/11/2026):</p>
 * <ul>
 *   <li>List {@code #/ExpiredItemReturnList} (ExpiredItemReturnListController): {@code ExpiredItemReturn.FromDate /
 *       .ToDate} (ids txtFromDate / txtToDate), select2 {@code ExpiredItemReturn.SearchStoreID} and
 *       {@code ExpiredItemReturn.SupplierIDs}; Search = {@code getQuotationlistgridSearch();}, New =
 *       {@code AddExpiredItemReturn();}, Print = {@code printReport()}; ui-grids {@code gridOptions} (list) and
 *       {@code gridOptions1} (details). Search API: {@code api/ExpiredItemReturn/fetch} ExecFlag
 *       GetExpiredItemReturnList; details: ExecFlag ExpiredItemReturnItemList.</li>
 *   <li>New {@code #/add-ExpiredItemReturn}: Supplier* {@code ExpiredItemReturn.SupplierIDs}, Store
 *       {@code ExpiredItemReturn.SearchStoreID}, Get Items = {@code ExpiredItemReturnSearchList();} (opens the "Search"
 *       popup whose Search = {@code CommonSearch();}, API ExecFlag ExpiredItemReturnSearchs), Save = {@code IUDSave();},
 *       Back = {@code closeForm();}.</li>
 * </ul>
 */
public class ExpiredItemReturn extends ReadOnlyScreen {

    public static final String LIST_ROUTE = "#/ExpiredItemReturnList";
    public static final String ADD_ROUTE = "#/add-ExpiredItemReturn";
    public static final String LIST_READY = "[ng-controller='ExpiredItemReturnListController'] [ui-grid='gridOptions']";
    public static final String FETCH_API = "api/ExpiredItemReturn/fetch";

    public static final String FROM = "ExpiredItemReturn.FromDate";
    public static final String TO = "ExpiredItemReturn.ToDate";
    public static final String STORE = "ExpiredItemReturn.SearchStoreID";
    public static final String SUPPLIER = "ExpiredItemReturn.SupplierIDs";
    public static final String LIST_GRID = "gridOptions";
    public static final String DETAIL_GRID = "gridOptions1";

    public String lastRequestBody = "";
    public int lastStatus = 0;

    public ExpiredItemReturn(Page page) { super(page); }

    /** Inventory &gt; Purchase &gt; Expired Item Return via the sidebar. */
    public boolean openList() {
        if (isOnRoute(ADD_ROUTE)) { clickNg("closeForm();"); waitForAngular(1500); }
        return openViaMenu("^\\s*Purchase\\s*$", LIST_ROUTE, LIST_READY);
    }

    /** Re-open the list from a clean scope (menu away and back), so no earlier selection survives. */
    public boolean reopenList() {
        page.evaluate("() => { const a = document.querySelector('a[href=\"#/LandingPage\"]') || [...document.querySelectorAll('a')].find(x => (x.textContent || '').trim() === 'Home'); if (a) a.click(); }");
        waitForAngular(1500);
        return openList();
    }

    public void setDates(String from, String to) {
        setNgInput(FROM, from);
        setNgInput(TO, to);
    }

    /** Click Search and wait for the list API; returns the HTTP status (0 = no response). */
    public int search() {
        Response r = clickAndWaitForApi("getQuotationlistgridSearch();", FETCH_API, 30000);
        waitForAngular(1200);
        lastStatus = r == null ? 0 : r.status();
        try { lastRequestBody = r == null ? "" : String.valueOf(r.request().postData()); } catch (Exception e) { lastRequestBody = ""; }
        return lastStatus;
    }

    public int listRows() { return uiGridRows(LIST_GRID); }
    public int detailRows() { return uiGridRows(DETAIL_GRID); }

    /** Real click on the first list row; returns the details API status (0 = none). */
    public int selectFirstRow() {
        try {
            Response r = page.waitForResponse(x -> x.url().contains(FETCH_API) && String.valueOf(x.request().postData()).contains("ExpiredItemReturnItemList"),
                    new Page.WaitForResponseOptions().setTimeout(20000),
                    () -> page.locator("[ui-grid='" + LIST_GRID + "'] .ui-grid-render-container-body .ui-grid-row .ui-grid-cell").first().click());
            waitForAngular(1000);
            return r.status();
        } catch (Exception e) {
            System.out.println("ExpiredItemReturn.selectFirstRow: " + trim(e.getMessage(), 160));
            return 0;
        }
    }

    /** Type into the n-th column filter box of the list grid. */
    public void filterListColumn(int col, String text) {
        page.locator("[ui-grid='" + LIST_GRID + "'] input[ng-model='colFilter.term']").nth(col).fill(text);
        waitForAngular(1200);
    }

    public int renderedListRows() {
        Object r = page.evaluate("() => document.querySelectorAll(\"[ui-grid='gridOptions'] .ui-grid-render-container-body .ui-grid-row\").length");
        return r instanceof Number ? ((Number) r).intValue() : -1;
    }

    public void clickPrint() { clearCaptured(); clickNg("printReport()"); waitForAngular(1500); }

    // ---- New form -----------------------------------------------------------

    public boolean openNewForm() {
        if (!openList()) return false;
        clickNg("AddExpiredItemReturn();");
        try {
            page.waitForFunction("() => !!document.querySelector('[ng-click=\"ExpiredItemReturnSearchList();\"]')", null,
                    new Page.WaitForFunctionOptions().setTimeout(60000));
        } catch (Exception ignore) { }
        waitForAngular(1500);
        hookAlerts();
        return isOnRoute(ADD_ROUTE);
    }

    public void clickGetItems() { clearCaptured(); clickNg("ExpiredItemReturnSearchList();"); waitForAngular(2500); }

    /** In the open "Search" popup: widen the expiry range and search. Returns {status, rows} or null. */
    public int[] searchExpiredStock(String from, String to) {
        page.evaluate("([f, t]) => { const m = [...document.querySelectorAll('.modal.in')].pop(); if (!m) return;"
                + " for (const [ng, v] of [['InventorySearch.FromDate', f], ['InventorySearch.ToDate', t]]) {"
                + "   const e = m.querySelector('[ng-model=\"' + ng + '\"]'); if (!e) continue; const c = angular.element(e).controller('ngModel');"
                + "   e.value = v; if (c) { c.$setViewValue(v); c.$render(); } } }", java.util.Arrays.asList(from, to));
        try {
            Response r = page.waitForResponse(x -> x.url().contains(FETCH_API) && String.valueOf(x.request().postData()).contains("ExpiredItemReturnSearchs"),
                    new Page.WaitForResponseOptions().setTimeout(20000),
                    () -> page.evaluate("() => { const m = [...document.querySelectorAll('.modal.in')].pop();"
                            + " const b = m && [...m.querySelectorAll('button')].find(x => x.getAttribute('ng-click') === 'CommonSearch();'); if (b) b.click(); }"));
            waitForAngular(1200);
            Object rows = page.evaluate("() => { const m = [...document.querySelectorAll('.modal.in')].pop();"
                    + " return m ? [...m.querySelectorAll('tbody tr')].filter(x => x.innerText.trim()).length : -1; }");
            return new int[]{r.status(), rows instanceof Number ? ((Number) rows).intValue() : -1};
        } catch (Exception e) {
            System.out.println("searchExpiredStock: " + trim(e.getMessage(), 160));
            return null;
        }
    }

    public String popupTitle() {
        Object r = page.evaluate("() => { const m = [...document.querySelectorAll('.modal.in')].pop(); const t = m && m.querySelector('.modal-title,h4');"
                + " return t ? t.innerText.trim() : ''; }");
        return r == null ? "" : r.toString();
    }

    public void clickSave() { clearCaptured(); clickNg("IUDSave();"); waitForAngular(1500); }

    public void clickBack() { clickNg("closeForm();"); waitForAngular(2000); }
}
