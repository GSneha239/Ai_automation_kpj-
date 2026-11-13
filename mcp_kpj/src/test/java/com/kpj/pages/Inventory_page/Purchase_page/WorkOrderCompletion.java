package com.kpj.pages.Inventory_page.Purchase_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

/**
 * Inventory &gt; Purchase &gt; <b>Work Order Completion</b> — Page Object (read-only use).
 *
 * <p>Locators from the live DOM (hisdemo v2026.11.13.455, manual test 13/11/2026):</p>
 * <ul>
 *   <li>List {@code #/WorkOrderCompletionList}: {@code WorkOrderCompletion.FromDate / .ToDate / .StoreID /
 *       .SupplierID}, Search = {@code SearchWO();}, Print = {@code printReport()}, New =
 *       {@code AddNewInventoryWorkOrder();}. Tables: list (WOC No, Invoice Date, Invoice No., Remark) and details.
 *       API {@code api/InventoryWorkOrder/Fetch} ExecFlag GetWorkOrderCompletionList.</li>
 *   <li>New {@code #/add-WorkOrderGrn} (WorkOrderGrnController): plain selects {@code WorkOrderCompletion.StoreID /
 *       .SupplierID / .WorkOrder}, inputs {@code .WorkOrderDate / .InvoiceNo / .InvoiceDate / .Remark}; Save =
 *       {@code IUDWorkOrder()} (ExecFlag UpdateWO - blocked by the read-only guard), Back = {@code closeForm()}.</li>
 * </ul>
 */
public class WorkOrderCompletion extends ReadOnlyScreen {

    public static final String LIST_ROUTE = "#/WorkOrderCompletionList";
    public static final String ADD_ROUTE = "#/add-WorkOrderGrn";
    public static final String LIST_READY = "[ng-click='SearchWO();']";
    public static final String FETCH_API = "api/InventoryWorkOrder/Fetch";

    public static final String FROM = "WorkOrderCompletion.FromDate";
    public static final String TO = "WorkOrderCompletion.ToDate";
    public static final String STORE = "WorkOrderCompletion.StoreID";
    public static final String SUPPLIER = "WorkOrderCompletion.SupplierID";
    public static final String WORK_ORDER = "WorkOrderCompletion.WorkOrder";
    public static final String INVOICE_NO = "WorkOrderCompletion.InvoiceNo";

    public String lastRequestBody = "";
    public String lastResponseBody = "";

    public WorkOrderCompletion(Page page) { super(page); }

    public boolean openList() {
        if (isOnRoute(ADD_ROUTE)) { clickNg("closeForm()"); waitForAngular(1500); }
        return openViaMenu("^\\s*Purchase\\s*$", LIST_ROUTE, LIST_READY);
    }

    public void setDates(String from, String to) {
        setNgInput(FROM, from);
        setNgInput(TO, to);
    }

    /** Search; returns HTTP status (0 = no response). */
    public int search() {
        Response r = clickAndWaitForApi("SearchWO();", FETCH_API, 30000);
        waitForAngular(1000);
        if (r == null) return 0;
        try { lastRequestBody = String.valueOf(r.request().postData()); } catch (Exception e) { lastRequestBody = ""; }
        try { lastResponseBody = r.text(); } catch (Exception e) { lastResponseBody = ""; }
        return r.status();
    }

    /** Number of records the last search returned (from the JSON body). */
    public int lastRecordCount() {
        String b = lastResponseBody == null ? "" : lastResponseBody.trim();
        if (b.isEmpty() || b.equals("[]")) return 0;
        int n = 0, i = -1;
        while ((i = b.indexOf("{", i + 1)) >= 0) n++;
        return n;
    }

    public void clickPrint() { clearCaptured(); clickNg("printReport()"); waitForAngular(1500); }

    public boolean openNewForm() {
        if (!openList()) return false;
        clickNg("AddNewInventoryWorkOrder();");
        try {
            page.waitForFunction("() => !!document.querySelector('[ng-click=\"IUDWorkOrder()\"]')", null,
                    new Page.WaitForFunctionOptions().setTimeout(60000));
        } catch (Exception ignore) { }
        waitForAngular(2500);   // Store / Supplier / Work Order lists load after the template
        hookAlerts();
        return isOnRoute(ADD_ROUTE);
    }

    public boolean fieldPresent(String ngModel) {
        Object r = page.evaluate("(ng) => { const e = document.querySelector('[ng-model=\"' + ng + '\"]'); return !!e && e.offsetParent !== null; }", ngModel);
        return Boolean.TRUE.equals(r);
    }

    public void clickSave() { clearCaptured(); clickNg("IUDWorkOrder()"); waitForAngular(1500); }

    public void clickBack() { clickNg("closeForm()"); waitForAngular(2000); }
}
