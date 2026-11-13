package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Inventory_page.Purchase_page.ExpiredItemReturn;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Inventory &gt; Purchase &gt; <b>Expired Item Return</b> — READ-ONLY automated checks (EIR-01 … EIR-20).
 *
 * <p>Built from the manual execution of 13/11/2026 (Expired_Item_Return_Manual_Report.html). Nothing is saved:
 * a browser-side guard aborts any write call, and the "save a complete return" scenario is reported SKIPPED.
 * Each case opens the screen it needs through the sidebar, so cases do not rely on one another's state.
 * Expected results describe CORRECT behaviour - known application defects (supplier/store filters, stale details,
 * stale print) are left to FAIL, not hidden.</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Inventory_page.Purchase_page.ExpiredItemReturnTest" -Ddevhis.postrun=off
 *   report: test-output/reports/Inventory_Purchase_ExpiredItemReturn.html
 * </pre>
 * Needs DEVHIS_URL / DEVHIS_USER / DEVHIS_PASS (or -Ddevhis.url / -Ddevhis.user / -Ddevhis.pass).
 */
public class ExpiredItemReturnTest extends DevHisBase {

    private static final String WIDE_FROM = "01/01/2020";
    private int pass, fail, skipped, blocked;

    public ExpiredItemReturnTest() { super("Inventory_Purchase_ExpiredItemReturn"); }

    public static void main(String[] args) {
        ExpiredItemReturnTest t = new ExpiredItemReturnTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** One test case: returns {STATUS, actual}. Any exception is reported as FAIL with its message. */
    private interface Check { String[] run() throws Exception; }

    private void tc(String id, String name, String desc, String expected, Check c) {
        String status, actual;
        try {
            String[] r = c.run();
            status = r[0];
            actual = r[1];
        } catch (Throwable e) {
            status = "FAIL";
            actual = "Exception: " + String.valueOf(e.getMessage()).split("\\R")[0];
        }
        step(page, id + " " + name, desc, expected, actual, status);
        switch (status) {
            case "PASS": pass++; break;
            case "SKIPPED": skipped++; break;
            case "BLOCKED": blocked++; break;
            default: fail++;
        }
    }

    private static String[] r(boolean ok, String actual) { return new String[]{ok ? "PASS" : "FAIL", actual}; }

    @Override
    protected void body() {
        meta("Inventory - Purchase - Expired Item Return (read-only)",
                "Inventory > Purchase > Expired Item Return",
                "Read-only checks derived from the 13/11/2026 manual execution. Write calls are blocked in the browser; "
                        + "saving a return is SKIPPED. Known defects are expected to FAIL.");

        ExpiredItemReturn eir = new ExpiredItemReturn(page);
        eir.installGuards();
        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login (existing LoginPage)", "Authenticated", "Logged in at " + page.url(), "PASS");

        tc("EIR-01", "Page navigation", "Inventory > Purchase > Expired Item Return (sidebar)",
                "List screen #/ExpiredItemReturnList opens with its grid",
                () -> { boolean ok = eir.openList(); return r(ok, ok ? "Opened " + page.url() : "Screen did not open; at " + page.url()); });

        tc("EIR-02", "Page title / header", "Read the page header",
                "Header contains 'EXPIRED ITEM RETURN'",
                () -> {
                    eir.openList();
                    assertThat(page.locator(".page_title").first()).containsText("EXPIRED ITEM RETURN",
                            new com.microsoft.playwright.assertions.LocatorAssertions.ContainsTextOptions().setIgnoreCase(true).setTimeout(15000));
                    return r(true, "Header: " + eir.headerText() + " | title: " + page.title());
                });

        tc("EIR-03", "Filters and dropdowns load", "From/To dates, Store and Supplier dropdowns",
                "Dates default to today; Store has a default; Store and Supplier lists are populated",
                () -> {
                    eir.reopenList();
                    String from = eir.inputValue(ExpiredItemReturn.FROM), to = eir.inputValue(ExpiredItemReturn.TO);
                    int stores = eir.optionCount(ExpiredItemReturn.STORE), sups = eir.optionCount(ExpiredItemReturn.SUPPLIER);
                    String store = eir.selectedText(ExpiredItemReturn.STORE);
                    boolean ok = ExpiredItemReturn.today().equals(from) && ExpiredItemReturn.today().equals(to) && stores > 1 && sups > 1 && !store.isEmpty();
                    return r(ok, "From=" + from + " To=" + to + " | Store='" + store + "' (" + stores + " options) | Supplier options=" + sups);
                });

        tc("EIR-04", "Date filter validation", "From Date later than To Date, then Search",
                "Message 'To Date should be greater than or equal to From Date'",
                () -> {
                    eir.reopenList();
                    eir.clearCaptured();
                    eir.setDates(ExpiredItemReturn.today(), "01/01/2020");
                    eir.search();
                    List<String> a = eir.waitForAlert(3000);
                    boolean ok = a.stream().anyMatch(x -> x.toLowerCase().contains("to date should be greater"));
                    return r(ok, "Messages: " + a + " | dates now " + eir.inputValue(ExpiredItemReturn.FROM) + " - " + eir.inputValue(ExpiredItemReturn.TO));
                });

        tc("EIR-05", "Search / grid loading", "Search " + WIDE_FROM + " to today",
                "API 200; list grid shows the returns in range",
                () -> {
                    eir.reopenList();
                    eir.setDates(WIDE_FROM, ExpiredItemReturn.today());
                    int st = eir.search();
                    int rows = eir.listRows();
                    if (st == 200 && rows == 0) return new String[]{"BLOCKED", "API 200 but no Expired Item Returns exist in range - no data"};
                    return r(st == 200 && rows > 0, "API " + st + ", rows=" + rows + " | first: " + firstOrNone(eir.uiGridColumn("gridOptions", "grnreturnno")));
                });

        tc("EIR-06", "Supplier filter", "Search with a supplier different from the first listed return's supplier",
                "Only returns of the chosen supplier are listed (or none)",
                () -> {
                    eir.reopenList();
                    eir.setDates(WIDE_FROM, ExpiredItemReturn.today());
                    eir.search();
                    if (eir.listRows() == 0) return new String[]{"BLOCKED", "No returns in range to filter"};
                    String recSupplier = firstOrNone(eir.uiGridColumn("gridOptions", "supplier"));
                    String chosen = eir.pickOptionAt(ExpiredItemReturn.SUPPLIER, 1);
                    if (chosen.equalsIgnoreCase(recSupplier)) chosen = eir.pickOptionAt(ExpiredItemReturn.SUPPLIER, 2);
                    eir.search();
                    List<String> sups = eir.uiGridColumn("gridOptions", "supplier");
                    final String pick = chosen;
                    boolean ok = sups.stream().allMatch(s -> s.equalsIgnoreCase(pick));
                    return r(ok, "Chosen '" + chosen + "' (records belong to '" + recSupplier + "'); request " + eir.lastRequestBody
                            + " | returned suppliers " + sups);
                });

        tc("EIR-07", "Store filter", "Search with a store other than the default",
                "The store is sent and only that store's returns are listed",
                () -> {
                    eir.reopenList();
                    eir.setDates(WIDE_FROM, ExpiredItemReturn.today());
                    String def = eir.selectedText(ExpiredItemReturn.STORE);
                    String chosen = eir.pickOptionAt(ExpiredItemReturn.STORE, 1);
                    if (chosen.equalsIgnoreCase(def)) chosen = eir.pickOptionAt(ExpiredItemReturn.STORE, 2);
                    eir.search();
                    List<String> stores = eir.uiGridColumn("gridOptions", "store");
                    final String pick = chosen;
                    boolean sent = eir.lastRequestBody.toLowerCase().contains("store");
                    boolean ok = sent && stores.stream().allMatch(s -> s.equalsIgnoreCase(pick));
                    return r(ok, "Chosen '" + chosen + "'; request " + eir.lastRequestBody + " (store sent=" + sent + ") | returned stores " + stores);
                });

        tc("EIR-08", "Row selection loads details", "Click the first return in the list",
                "Details API 200 and 'Expired Return Items Details' shows its lines",
                () -> {
                    eir.reopenList();
                    eir.setDates(WIDE_FROM, ExpiredItemReturn.today());
                    eir.search();
                    if (eir.listRows() == 0) return new String[]{"BLOCKED", "No return to select"};
                    int st = eir.selectFirstRow();
                    int d = eir.detailRows();
                    return r(st == 200 && d > 0, "Details API " + st + ", detail rows=" + d + " | item: "
                            + firstOrNone(eir.uiGridColumn("gridOptions1", "serviceitemname")));
                });

        tc("EIR-09", "Search / filter in grid", "Type into the Return Number column filter",
                "Non-matching text hides all rows; matching text keeps them",
                () -> {
                    eir.reopenList();
                    eir.setDates(WIDE_FROM, ExpiredItemReturn.today());
                    eir.search();
                    if (eir.listRows() == 0) return new String[]{"BLOCKED", "No rows to filter"};
                    eir.filterListColumn(1, "ZZZ_NO_MATCH");
                    int none = eir.renderedListRows();
                    eir.filterListColumn(1, "EIRN");
                    int some = eir.renderedListRows();
                    eir.filterListColumn(1, "");
                    return r(none == 0 && some > 0, "Rendered rows: 'ZZZ_NO_MATCH'=" + none + ", 'EIRN'=" + some);
                });

        tc("EIR-10", "Pagination controls", "Inspect the list pager",
                "Page-size selector and First/Prev/Next/Last controls are present",
                () -> {
                    eir.reopenList();
                    Object info = page.evaluate("() => { const g = document.querySelector(\"[ui-grid='gridOptions']\"); const s = angular.element(g).scope();"
                            + " return { sizes: (s.gridOptions.paginationPageSizes || []).join('/'), btns: g.querySelectorAll('.ui-grid-pager-control button').length,"
                            + " sorting: !!s.gridOptions.enableSorting }; }");
                    String txt = String.valueOf(info);
                    return r(txt.contains("25") && !txt.contains("btns=0"), "Pager: " + txt);
                });

        tc("EIR-11", "Empty-state behaviour", "Select a return, then search a range with no returns",
                "Both the list and the details grid are empty",
                () -> {
                    eir.reopenList();
                    eir.setDates(WIDE_FROM, ExpiredItemReturn.today());
                    eir.search();
                    if (eir.listRows() > 0) eir.selectFirstRow();
                    eir.setDates("01/01/2000", "02/01/2000");
                    eir.search();
                    int l = eir.listRows(), d = eir.detailRows();
                    return r(l == 0 && d == 0, "List rows=" + l + ", detail rows=" + d + (d > 0 ? " (details of the previously selected return are still shown)" : ""));
                });

        tc("EIR-12", "Print without a selection", "Fresh screen, click Print",
                "Confirm dialog asks to select an Expired Item Return; no report opens",
                () -> {
                    eir.reopenList();
                    eir.clickPrint();
                    String dlg = eir.confirmDialogText();
                    List<String> opened = eir.openedUrls();
                    eir.closeConfirmDialog();
                    return r(dlg.toLowerCase().contains("please select") && opened.isEmpty(), "Dialog: '" + dlg + "' | report URLs: " + opened);
                });

        tc("EIR-13", "Print functionality", "Select a return, click Print, fetch the report",
                "frmExpiredItemReturn.aspx returns a PDF (HTTP 200)",
                () -> {
                    eir.reopenList();
                    eir.setDates(WIDE_FROM, ExpiredItemReturn.today());
                    eir.search();
                    if (eir.listRows() == 0) return new String[]{"BLOCKED", "No return to print"};
                    eir.selectFirstRow();
                    eir.clickPrint();
                    List<String> opened = eir.openedUrls();
                    if (opened.isEmpty()) return r(false, "Print opened nothing. Dialog: '" + eir.confirmDialogText() + "'");
                    String probe = eir.probeReport(opened.get(0));
                    return r(opened.get(0).contains("frmExpiredItemReturn") && probe.startsWith("200") && probe.contains("PDF"),
                            "Report " + opened.get(0) + " -> " + probe);
                });

        tc("EIR-14", "Print after the list is emptied", "Select a return, search an empty range, click Print",
                "Nothing is printed (selection cleared) - a 'please select' prompt instead",
                () -> {
                    eir.reopenList();
                    eir.setDates(WIDE_FROM, ExpiredItemReturn.today());
                    eir.search();
                    if (eir.listRows() == 0) return new String[]{"BLOCKED", "No return to select first"};
                    eir.selectFirstRow();
                    eir.setDates("01/01/2000", "02/01/2000");
                    eir.search();
                    eir.clickPrint();
                    List<String> opened = eir.openedUrls();
                    String dlg = eir.confirmDialogText();
                    eir.closeConfirmDialog();
                    return r(opened.isEmpty(), opened.isEmpty() ? "No report; dialog '" + dlg + "'"
                            : "Stale record printed although the list is empty: " + opened);
                });

        tc("EIR-15", "New button / form fields", "Click New",
                "#/add-ExpiredItemReturn with Supplier, Store, Get Items, Save and Back",
                () -> {
                    boolean ok = eir.openNewForm();
                    boolean fields = eir.optionCount(ExpiredItemReturn.SUPPLIER) > 1 && eir.optionCount(ExpiredItemReturn.STORE) > 1
                            && eir.isVisible("[ng-click='ExpiredItemReturnSearchList();']") && eir.isVisible("[ng-click='IUDSave();']");
                    return r(ok && fields, "At " + page.url() + " | supplier options=" + eir.optionCount(ExpiredItemReturn.SUPPLIER)
                            + ", store='" + eir.selectedText(ExpiredItemReturn.STORE) + "'");
                });

        tc("EIR-16", "Get Items without Supplier", "New form, Supplier left at --Select--, click Get Items",
                "Message 'Please Select Supplier'; no item popup",
                () -> {
                    eir.openNewForm();
                    eir.clickGetItems();
                    List<String> a = eir.waitForAlert(3000);
                    int modals = eir.openModals();
                    eir.dismissModals();
                    return r(a.stream().anyMatch(x -> x.toLowerCase().contains("select supplier")) && modals == 0,
                            "Messages: " + a + " | popups open: " + modals);
                });

        tc("EIR-17", "Get Items with Supplier / expired-stock lookup", "Pick a supplier, Get Items, search " + WIDE_FROM + "-31/12/" + java.time.LocalDate.now().getYear(),
                "Search popup opens and the expired-stock API answers 200",
                () -> {
                    eir.openNewForm();
                    String sup = eir.pickOptionAt(ExpiredItemReturn.SUPPLIER, 1);
                    eir.clickGetItems();
                    String title = eir.popupTitle();
                    int[] res = eir.searchExpiredStock(WIDE_FROM, "31/12/" + java.time.LocalDate.now().getYear());
                    eir.dismissModals();
                    if (res == null) return r(false, "Popup '" + title + "' - search gave no response");
                    if (res[0] == 200 && res[1] <= 0)
                        return new String[]{"BLOCKED", "Popup '" + title + "' opened for " + sup + "; API 200 but no expired stock in store '"
                                + eir.selectedText(ExpiredItemReturn.STORE) + "' - item add / quantity / totals cannot be tested"};
                    return r(res[0] == 200 && "Search".equalsIgnoreCase(title), "Popup '" + title + "', API " + res[0] + ", items=" + res[1]);
                });

        tc("EIR-18", "Mandatory validation on Save", "New form with no items, click Save",
                "Message 'Please add at least one item'; no write call",
                () -> {
                    eir.openNewForm();
                    int before = eir.blockedWrites.size();
                    eir.clickSave();
                    List<String> a = eir.waitForAlert(3000);
                    boolean noWrite = eir.blockedWrites.size() == before;
                    return r(a.stream().anyMatch(x -> x.toLowerCase().contains("at least one item")) && noWrite,
                            "Messages: " + a + " | write attempted: " + !noWrite);
                });

        tc("EIR-19", "Save a complete Expired Item Return", "Would create a return and credit note",
                "(write operation - not executed)",
                () -> new String[]{"SKIPPED", "Read-only run: saving a return is a data modification. Covered as MANUAL."});

        tc("EIR-20", "Back button", "New form, click Back", "Returns to #/ExpiredItemReturnList",
                () -> {
                    eir.openNewForm();
                    eir.clickBack();
                    return r(eir.isOnRoute(ExpiredItemReturn.LIST_ROUTE), "At " + page.url());
                });

        tc("EIR-21", "Console errors and failed API requests", "Everything logged during this run",
                "No HTTP >= 400 and no console / page errors; no write call attempted",
                () -> r(eir.apiFailures.isEmpty() && eir.consoleErrors.isEmpty() && eir.blockedWrites.isEmpty(),
                        "HTTP>=400: " + eir.apiFailures + " | console: " + eir.consoleErrors + " | blocked writes: " + eir.blockedWrites
                                + " | API calls logged: " + eir.apiLog.size()));

        addSummary("Mode", "READ-ONLY (write calls aborted in the browser)");
        addSummary("Test cases", (pass + fail + skipped + blocked) + " - PASS " + pass + " / FAIL " + fail + " / SKIPPED " + skipped + " / BLOCKED " + blocked);
        addSummary("API calls logged", String.valueOf(eir.apiLog.size()));
        addSummary("Failed API responses", eir.apiFailures.isEmpty() ? "none" : String.join(" ; ", eir.apiFailures));
        addSummary("Console errors", eir.consoleErrors.isEmpty() ? "none" : String.join(" ; ", eir.consoleErrors));
        addSummary("Blocked write calls", eir.blockedWrites.isEmpty() ? "none" : String.join(" ; ", eir.blockedWrites));
    }

    private static String firstOrNone(List<String> l) { return l == null || l.isEmpty() ? "(none)" : l.get(0); }
}
