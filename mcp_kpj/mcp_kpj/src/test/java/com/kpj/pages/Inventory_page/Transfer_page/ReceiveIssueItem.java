package com.kpj.pages.Inventory_page.Transfer_page;

import com.kpj.pages.Inventory_page.InventoryScreen;
import com.microsoft.playwright.Page;

import java.util.Arrays;
import java.util.List;

/**
 * Page object: Inventory &gt; <b>Receive Issue Item</b> — list screen {@code #/ReceiveIssueItemList} (state ReceiveIssueItemList)
 * and its add form {@code #/add-ReceiveIssueItem} (state addReceiveIssueItem).
 *
 * <p>The same href also exists under Ancillary Services &gt; CSSD (state ReceiveIssueItemListCSSD); navigation is scoped to the
 * Inventory menu and the Inventory state is asserted, so the CSSD screen is never tested by mistake.</p>
 *
 * <p>Locators were read from the live screen (hisdemo, build v2026.11.11.439). READ-ONLY: Save only through
 * {@link InventoryScreen#clickSaveForValidation(String)} on a form verified to be incomplete.</p>
 */
public class ReceiveIssueItem extends InventoryScreen {

    // ---------------------------------------------------------------- list screen
    public static final String MENU_HREF = "#/ReceiveIssueItemList";
    public static final String LIST_STATE = "ReceiveIssueItemList";
    public static final String LIST_TITLE = "Receive Issue Item";
    public static final String FROM_STORE = "ReceiveIssueItem.FromStore";     // "Issued From Store"
    public static final String TO_STORE = "ReceiveIssueItem.ToStore";         // "Received To Store"
    public static final String STATUS = "ReceiveIssueItem.Status";
    public static final String FROM_DATE = "ReceiveIssueItem.FromDate";
    public static final String TO_DATE = "ReceiveIssueItem.ToDate";
    public static final String MASTER_GRID = grid("gridOptions");
    public static final String DETAIL_GRID = grid("gridOptions1");
    /** List search and row details post here (ExecFlag GetReceiveIssueItemSearch for the search) - verified live. */
    public static final String FETCH_API = "/api/IssueToStore/Fetch";
    public static final String SEARCH_FLAG = "GetReceiveIssueItemSearch";
    public static final String STATUS_IN_TRANSIT = "Issued/In-transit";
    public static final String STATUS_DELIVERED = "Delivered";
    public static final String BTN_SEARCH = "Search", BTN_PRINT = "Print", BTN_NEW = "New";

    public static final List<String> MASTER_HEADERS = Arrays.asList("Transfer Number", "Receive Datetime", "From Store Name", "To Store Name",
            "Received By", "Receive Issued Quantity", "Total Amount");
    public static final List<String> DETAIL_HEADERS = Arrays.asList("Item Code", "Item Name", "Batch Code", "Expiry Date", "Indent Quantity",
            "Transfer Quantity", "Received Quantity", "Total Selling Price", "Total Amount", "Status");

    public static final String C_NO = "Transfer Number", C_DATE = "Receive Datetime", C_FROM = "From Store Name", C_TO = "To Store Name",
            C_BY = "Received By", C_QTY = "Receive Issued Quantity", C_AMT = "Total Amount";
    public static final String D_CODE = "Item Code", D_NAME = "Item Name", D_TRF_QTY = "Transfer Quantity", D_RCV_QTY = "Received Quantity",
            D_AMT = "Total Amount", D_STATUS = "Status";

    // ---------------------------------------------------------------- add form
    public static final String ADD_STATE = "addReceiveIssueItem";
    public static final String ADD_ROUTE = "#/add-ReceiveIssueItem";
    public static final String ADD_TRANSFERRED_FROM = "ReceiveIssueItem.SearchStoreID";
    public static final String ADD_RECEIVED_TO = "ReceiveIssueItem.FromStore";
    public static final String ADD_GROUP = "ReceiveIssueItem.GroupID";
    public static final String ADD_SUBCATEGORY = "ReceiveIssueItem.SubCategoryID";
    public static final String ADD_GRID = grid("gridOptions");
    public static final String BTN_SAVE = "Save", BTN_BACK = "Back";
    public static final List<String> ADD_GRID_HEADERS = Arrays.asList("Transfer Number", "Transfer DateTime", "From Store Name", "To Store Name", "Net Amt", "Remarks");

    public ReceiveIssueItem(Page page) { super(page); }

    /** Inventory &gt; Receive Issue Item via the sidebar (never the CSSD entry). @return "" on success */
    public String open() { return navigateInventory(MENU_HREF, LIST_STATE); }

    public String reopen() {
        page.evaluate("() => { try { angular.element(document.body).injector().get('$state').go('LandingPage'); } catch(e) { location.hash='#/LandingPage'; } }");
        page.waitForTimeout(800);
        return open();
    }

    public void dates(String from, String to) { setDate(FROM_DATE, from); setDate(TO_DATE, to); }

    public Call search() { return awaitApi(FETCH_API, 20000, () -> clickSafe(BTN_SEARCH)); }

    public Call searchExpectingNoRequest(int ms) { return awaitApi(FETCH_API, ms, () -> clickSafe(BTN_SEARCH)); }

    public Call selectTransfer(String transferNumber) {
        final String[] err = {""};
        Call c = awaitApi(FETCH_API, 15000, () -> err[0] = clickRow(MASTER_GRID, C_NO, transferNumber));
        if (!err[0].isEmpty()) c.error = err[0];
        try { page.waitForFunction("(g) => { const el=document.querySelector(g); const s=el && angular.element(el).isolateScope(); return s && s.grid && (s.grid.options.data||[]).length>0; }",
                DETAIL_GRID, new Page.WaitForFunctionOptions().setTimeout(5000)); } catch (Exception none) { }
        return c;
    }

    public String openAddForm() {
        clickSafe(BTN_NEW);
        return waitForState(ADD_STATE, 30000) ? "" : "after New the screen is \"" + currentState() + "\" (" + route() + ")";
    }

    /** Add form Search (pending transfers). {@code from} null = leave "Transferred From" as it is. */
    public Call addFormSearch(String from) {
        String e = from == null ? "" : choose(ADD_TRANSFERRED_FROM, from);
        // the add form's search endpoint is not assumed: the first /api/ response after the click is taken
        Call c = awaitApi("/api/", 15000, () -> clickSafe(BTN_SEARCH));
        if (!e.isEmpty()) c.error = e + (c.error.isEmpty() ? "" : "; " + c.error);
        return c;
    }

    public String back() {
        clickSafe(BTN_BACK);
        return waitForState(LIST_STATE, 30000) ? "" : "after Back the screen is \"" + currentState() + "\" (" + route() + ")";
    }
}
