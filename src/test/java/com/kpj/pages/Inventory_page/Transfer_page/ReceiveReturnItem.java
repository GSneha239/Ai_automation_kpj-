package com.kpj.pages.Inventory_page.Transfer_page;

import com.kpj.pages.Inventory_page.InventoryScreen;
import com.microsoft.playwright.Page;

import java.util.Arrays;
import java.util.List;

/**
 * Page object: Inventory &gt; <b>Receive Return Item</b> — list screen {@code #/ReceiveReturnItemList} (state ReceiveReturnItemList)
 * and its add form {@code #/add-ReceiveReturnItem} (state addReceiveReturnItem).
 *
 * <p>Locators were read from the live screen (hisdemo, build v2026.11.11.439). READ-ONLY: this class never clicks Save except through
 * {@link InventoryScreen#clickSaveForValidation(String)}, which the test uses only on a form it has verified to be incomplete.</p>
 */
public class ReceiveReturnItem extends InventoryScreen {

    // ---------------------------------------------------------------- list screen
    public static final String MENU_HREF = "#/ReceiveReturnItemList";
    public static final String LIST_STATE = "ReceiveReturnItemList";
    public static final String LIST_TITLE = "Receive Return Item";
    public static final String FROM_STORE = "ReceiveReturnItem.FromStore";
    public static final String TO_STORE = "ReceiveReturnItem.ToStore";
    public static final String STATUS = "ReceiveReturnItem.Status";
    public static final String FROM_DATE = "ReceiveReturnItem.FromDate";
    public static final String TO_DATE = "ReceiveReturnItem.ToDate";
    public static final String MASTER_GRID = grid("gridOptions");
    public static final String DETAIL_GRID = grid("gridOptions1");
    /** Search, row-click details and the add-form search all post to this endpoint. */
    public static final String FETCH_API = "/api/ReturnItem/Fetch";
    public static final String STATUS_IN_TRANSIT = "Issued/In-transit";
    public static final String STATUS_DELIVERED = "Delivered";
    public static final String BTN_SEARCH = "Search", BTN_PRINT = "Print", BTN_PRINT_ITEM = "Print Item", BTN_NEW = "New";

    public static final List<String> MASTER_HEADERS = Arrays.asList("Return Number", "Return Date", "From Store Name", "To Store Name",
            "Total Items", "Total Selling Price", "Total Amount");
    public static final List<String> DETAIL_HEADERS = Arrays.asList("Item Code", "Item Name", "Batch Code", "Expiry Date", "Return Quantity",
            "Received Quantity", "Unit Purchase Rate", "Total Amount", "Unit Selling Price", "Total Selling Price", "Status");

    // master / detail column names used by the checks
    public static final String C_NO = "Return Number", C_DATE = "Return Date", C_FROM = "From Store Name", C_TO = "To Store Name",
            C_ITEMS = "Total Items", C_SELL = "Total Selling Price", C_AMT = "Total Amount";
    public static final String D_CODE = "Item Code", D_NAME = "Item Name", D_BATCH = "Batch Code", D_RET_QTY = "Return Quantity",
            D_RCV_QTY = "Received Quantity", D_RATE = "Unit Purchase Rate", D_AMT = "Total Amount", D_SELL = "Total Selling Price", D_STATUS = "Status";

    // ---------------------------------------------------------------- add form
    public static final String ADD_STATE = "addReceiveReturnItem";
    public static final String ADD_ROUTE = "#/add-ReceiveReturnItem";
    public static final String ADD_FROM_STORE = "ReceiveReturnItem.FromStore";     // "Received From"
    public static final String ADD_TO_STORE = "ReceiveReturnItem.ToStore";         // "Received To"
    public static final String ADD_GROUP = "ReceiveReturnItem.GroupID";
    public static final String ADD_SUBCATEGORY = "ReceiveReturnItem.SubCategoryID";
    public static final String ADD_RECEIVED_DATE = "ReceiveReturnItem.ReceivedDate";
    public static final String ADD_TOTAL_ITEMS = "#newTAreceivItem";
    public static final String ADD_GRID = grid("gridOptions");
    public static final String ADD_SEARCH_FLAG = "GetReturndItemSearch";
    public static final String BTN_SAVE = "Save", BTN_BACK = "Back";
    public static final List<String> ADD_GRID_HEADERS = Arrays.asList("Return Number", "Return Date", "From Store Name", "To Store Name",
            "Total Amount", "Total Selling Price", "Received By", "Remarks");

    public ReceiveReturnItem(Page page) { super(page); }

    /** Inventory &gt; Receive Return Item via the sidebar. @return "" on success */
    public String open() { return navigateInventory(MENU_HREF, LIST_STATE); }

    /** Fresh screen (new controller: filters, grids and selection reset) through the menu. */
    public String reopen() {
        page.evaluate("() => { try { angular.element(document.body).injector().get('$state').go('LandingPage'); } catch(e) { location.hash='#/LandingPage'; } }");
        page.waitForTimeout(800);
        return open();
    }

    public void dates(String from, String to) { setDate(FROM_DATE, from); setDate(TO_DATE, to); }

    /** Clicks Search and waits for the ReturnItem/Fetch response. */
    public Call search() { return awaitApi(FETCH_API, 20000, () -> clickSafe(BTN_SEARCH)); }

    /** Clicks Search expecting validation to stop it: waits only {@code ms} for a request. */
    public Call searchExpectingNoRequest(int ms) { return awaitApi(FETCH_API, ms, () -> clickSafe(BTN_SEARCH)); }

    /** Selects a return (row click) and waits for its item details. */
    public Call selectReturn(String returnNumber) {
        final String[] err = {""};
        Call c = awaitApi(FETCH_API, 15000, () -> err[0] = clickRow(MASTER_GRID, C_NO, returnNumber));
        if (!err[0].isEmpty()) c.error = err[0];
        try { page.waitForFunction("(g) => { const el=document.querySelector(g); const s=el && angular.element(el).isolateScope(); return s && s.grid && (s.grid.options.data||[]).length>0; }",
                DETAIL_GRID, new Page.WaitForFunctionOptions().setTimeout(5000)); } catch (Exception none) { }
        return c;
    }

    /** New -&gt; add form. @return "" when the add form's state is active */
    public String openAddForm() {
        clickSafe(BTN_NEW);
        return waitForState(ADD_STATE, 30000) ? "" : "after New the screen is \"" + currentState() + "\" (" + route() + ")";
    }

    /** Add form: Search for pending returns from {@code from} to {@code to}. */
    public Call addFormSearch(String from, String to) {
        String e1 = from == null ? "" : choose(ADD_FROM_STORE, from);
        String e2 = to == null ? "" : choose(ADD_TO_STORE, to);
        Call c = awaitApi(FETCH_API, 20000, () -> clickSafe(BTN_SEARCH));
        if (!e1.isEmpty() || !e2.isEmpty()) c.error = (e1 + " " + e2).trim() + (c.error.isEmpty() ? "" : "; " + c.error);
        return c;
    }

    public String back() {
        clickSafe(BTN_BACK);
        return waitForState(LIST_STATE, 30000) ? "" : "after Back the screen is \"" + currentState() + "\" (" + route() + ")";
    }
}
