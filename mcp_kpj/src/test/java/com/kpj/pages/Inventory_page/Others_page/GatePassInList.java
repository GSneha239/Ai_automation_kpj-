package com.kpj.pages.Inventory_page.Others_page;

import com.kpj.pages.Inventory_page.InventoryScreen;
import com.microsoft.playwright.Page;

import java.util.Arrays;
import java.util.List;

/**
 * Page object: Inventory &gt; <b>Gate Pass In List</b> — list screen {@code #/GatePassInList} (state GatePassInList) and its add form,
 * registered as ui-router state {@code GatePassIn} at {@code #/add-GatePass}.
 *
 * <p>Locators were read from the live screen (hisdemo, build v2026.11.11.439). READ-ONLY: Save only through
 * {@link InventoryScreen#clickSaveForValidation(String)} on a form verified to be incomplete; no Gate Pass transaction is ever completed.</p>
 */
public class GatePassInList extends InventoryScreen {

    // ---------------------------------------------------------------- list screen
    public static final String MENU_HREF = "#/GatePassInList";
    public static final String LIST_STATE = "GatePassInList";
    public static final String LIST_TITLE = "Gate Pass In List";
    public static final String SELECT_DATE = "GatePassIn.SelectDate";
    public static final String FROM_DATE = "GatePassIn.fromdate";
    public static final String TO_DATE = "GatePassIn.todate";
    public static final String SELECT_RANGE = "GatePassIn.SelectRange";
    public static final String STORE = "GatePassIn.Store";
    public static final String SUPPLIER = "GatePassIn.Supplier";
    public static final String MASTER_GRID = grid("gridOptions");
    public static final String DETAIL_GRID = grid("gridOptions1");
    public static final String FETCH_API = "/api/GatePassIn/Fetch";
    public static final String LIST_FLAG = "GETPASSINHEADERLIST";
    public static final String BTN_SEARCH = "Search", BTN_PRINT = "Print", BTN_NEW = "New";

    public static final List<String> MASTER_HEADERS = Arrays.asList("GP In No.", "Date", "Supplier Name", "Invoice No.", "GP Out No", "GP Out Date", "Approved");
    public static final String C_NO = "GP In No.", C_DATE = "Date", C_SUPPLIER = "Supplier Name";

    // ---------------------------------------------------------------- add form
    /** The add form's REGISTERED ui-router state and route (the New button must lead here). */
    public static final String ADD_STATE = "GatePassIn";
    public static final String ADD_ROUTE = "#/add-GatePass";
    public static final String ADD_STORE = "GatePassIn.Store";
    public static final String ADD_SUPPLIER = "GatePassIn.Supplier";
    public static final String ADD_GP_OUT = "GatePassIn.GPOutNo";
    public static final String ADD_INVOICE = "GetPassIn.InvoiceNo";
    public static final String ADD_ENTRY = "GetPassIn.EntryNo";
    public static final String ADD_GRID = grid("gridOptions");
    public static final String BTN_SAVE = "Save", BTN_BACK = "Back";

    // ---------------------------------------------------------------- Gate Pass Out list (read only, to check data availability)
    public static final String GPO_MENU_HREF = "#/GatePassOutList";
    public static final String GPO_STATE = "GatePassOutList";
    public static final String GPO_FETCH_API = "/api/GatePassOut/Fetch";
    /** Flags verified live: picking a supplier sends GETPASSOUTDATANumber, the add-form Search sends GETPASSOUTDATA. */
    public static final String GP_OUT_LIST_FLAG = "GETPASSOUTDATANumber", ADD_SEARCH_FLAG = "GETPASSOUTDATA";

    public GatePassInList(Page page) { super(page); }

    public String open() { return navigateInventory(MENU_HREF, LIST_STATE); }

    public String reopen() {
        page.evaluate("() => { try { angular.element(document.body).injector().get('$state').go('LandingPage'); } catch(e) { location.hash='#/LandingPage'; } }");
        page.waitForTimeout(800);
        return open();
    }

    /** Ticks "Select Date" and types the range. */
    public void dateRange(String from, String to) {
        setChecked(SELECT_DATE, true);
        setDate(FROM_DATE, from);
        setDate(TO_DATE, to);
    }

    public boolean dateInputsEnabled() {
        return !inputDisabled("[ng-model='" + FROM_DATE + "']") && !inputDisabled("[ng-model='" + TO_DATE + "']");
    }

    /** The From / To number boxes of the "Number" panel (they carry no ng-model): CSS selectors, tagged on the fly. */
    public String[] rangeInputs() {
        page.evaluate("(cb) => { const c=document.querySelector(\"[ng-model='\"+cb+\"']\"); if(!c) return;"
                + " let p=c.parentElement; for(let i=0;i<8 && p;i++){ const t=[...p.querySelectorAll('input[type=text],input[type=number],input:not([type])')].filter(x=>x!==c && !/date/i.test(x.getAttribute('ng-model')||'')); if(t.length>=2){ t[0].id=t[0].id||'__gpRangeFrom'; t[1].id=t[1].id||'__gpRangeTo'; return; } p=p.parentElement; } }",
                SELECT_RANGE);
        Object ids = page.evaluate("(cb) => { const c=document.querySelector(\"[ng-model='\"+cb+\"']\"); let p=c && c.parentElement;"
                + " for(let i=0;i<8 && p;i++){ const t=[...p.querySelectorAll('input[type=text],input[type=number],input:not([type])')].filter(x=>x!==c && !/date/i.test(x.getAttribute('ng-model')||'')); if(t.length>=2) return [t[0].id, t[1].id]; p=p.parentElement; } return []; }", SELECT_RANGE);
        List<String> l = strings(ids);
        return l.size() < 2 ? new String[0] : new String[]{"#" + l.get(0), "#" + l.get(1)};
    }

    public Call search() { return awaitApi(FETCH_API, 20000, () -> clickSafe(BTN_SEARCH)); }

    public Call searchExpectingNoRequest(int ms) { return awaitApi(FETCH_API, ms, () -> clickSafe(BTN_SEARCH)); }

    /** Clicks New. @return the ui-router state reached ("" when none) */
    public String clickNew() {
        clickSafe(BTN_NEW);
        try { page.waitForFunction("() => { try { const n=angular.element(document.body).injector().get('$state').current.name; return n && n!=='" + LIST_STATE + "'; } catch(e) { return false; } }",
                null, new Page.WaitForFunctionOptions().setTimeout(15000)); } catch (Exception stayed) { }
        page.waitForLoadState();
        return currentState();
    }

    /** Picks a supplier on the add form and waits for the GP Out list request it triggers (if any). */
    public Call pickSupplier(String supplier) {
        final String[] err = {""};
        Call c = awaitApi(FETCH_API, 10000, () -> err[0] = choose(ADD_SUPPLIER, supplier));
        if (!err[0].isEmpty()) c.error = err[0];
        page.waitForTimeout(300);
        return c;
    }

    public Call addFormSearch() { return awaitApi(FETCH_API, 10000, () -> clickSafe(BTN_SEARCH)); }

    public Call searchGatePassOut() { return awaitApi(GPO_FETCH_API, 20000, () -> clickSafe(BTN_SEARCH)); }

    public String back() {
        clickSafe(BTN_BACK);
        return waitForState(LIST_STATE, 20000) ? "" : "after Back the screen is \"" + currentState() + "\" (" + route() + ")";
    }

    /** Inventory &gt; Gate Pass Out List (read only) — used to check whether Gate Pass Out records exist at all. */
    public String openGatePassOut() { return navigateInventory(GPO_MENU_HREF, GPO_STATE); }
}
