package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Inventory_page.Purchase_page.WorkOrderCompletion;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Inventory &gt; Purchase &gt; <b>Work Order Completion</b> — READ-ONLY automated checks (WOC-01 … WOC-17).
 *
 * <p>Built from the manual execution of 13/11/2026 (Work_Order_Completion_Manual_Report.html). No work order is
 * created or completed: write calls are aborted in the browser and "complete a work order" is SKIPPED. Cases that
 * need data the environment does not have (completion records, open work orders) report BLOCKED. Known defects
 * (Save crashes with ReferenceError IschkGrdValidation, Invoice No.* not validated) are expected to FAIL.</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Inventory_page.Purchase_page.WorkOrderCompletionTest" -Ddevhis.postrun=off
 *   report: test-output/reports/Inventory_Purchase_WorkOrderCompletion.html
 * </pre>
 */
public class WorkOrderCompletionTest extends DevHisBase {

    private static final String WIDE_FROM = "01/01/2020";
    private int pass, fail, skipped, blocked;

    public WorkOrderCompletionTest() { super("Inventory_Purchase_WorkOrderCompletion"); }

    public static void main(String[] args) {
        WorkOrderCompletionTest t = new WorkOrderCompletionTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

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
        meta("Inventory - Purchase - Work Order Completion (read-only)",
                "Inventory > Purchase > Work Order Completion",
                "Read-only checks derived from the 13/11/2026 manual execution. Write calls are blocked in the browser; "
                        + "completing a work order is SKIPPED. Missing test data is reported BLOCKED.");

        WorkOrderCompletion woc = new WorkOrderCompletion(page);
        woc.installGuards();
        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login (existing LoginPage)", "Authenticated", "Logged in at " + page.url(), "PASS");

        tc("WOC-01", "Page navigation", "Inventory > Purchase > Work Order Completion (sidebar)",
                "List screen #/WorkOrderCompletionList opens",
                () -> { boolean ok = woc.openList(); return r(ok, ok ? "Opened " + page.url() : "Screen did not open; at " + page.url()); });

        tc("WOC-02", "Page title / header", "Read the header", "Header contains 'WORK ORDER COMPLETION'",
                () -> {
                    woc.openList();
                    assertThat(page.locator(".page_title").first()).containsText("WORK ORDER COMPLETION",
                            new com.microsoft.playwright.assertions.LocatorAssertions.ContainsTextOptions().setIgnoreCase(true).setTimeout(15000));
                    return r(true, "Header: " + woc.headerText());
                });

        tc("WOC-03", "Fields and dropdowns", "From/To Date, Store, Supplier on the list",
                "Dates default to today; Store defaults; Store and Supplier lists populated",
                () -> {
                    woc.openList();
                    String from = woc.inputValue(WorkOrderCompletion.FROM), to = woc.inputValue(WorkOrderCompletion.TO);
                    int st = woc.optionCount(WorkOrderCompletion.STORE), su = woc.optionCount(WorkOrderCompletion.SUPPLIER);
                    boolean ok = from != null && to != null && st > 1 && su > 1 && !woc.selectedText(WorkOrderCompletion.STORE).isEmpty();
                    return r(ok, "From=" + from + " To=" + to + " | Store='" + woc.selectedText(WorkOrderCompletion.STORE) + "' (" + st + ") | Supplier options=" + su);
                });

        tc("WOC-04", "Table / grid columns", "Inspect the list and details tables",
                "List: WOC No, Invoice Date, Invoice No., Remark; details include Item Code, WO Quantity, Net Amount",
                () -> {
                    woc.openList();
                    List<String> list = woc.tableHeaders(0), det = woc.tableHeaders(1);
                    boolean ok = list.contains("WOC No") && list.contains("Invoice No.") && det.contains("Item Code") && det.contains("Net Amount");
                    return r(ok, "List " + list + " | Details " + det);
                });

        tc("WOC-05", "Search with default dates", "Click Search", "API 200",
                () -> {
                    woc.openList();
                    int s = woc.search();
                    return r(s == 200, "API " + s + ", records=" + woc.lastRecordCount() + ", request " + woc.lastRequestBody);
                });

        tc("WOC-06", "Data availability for a wide date range", "Search " + WIDE_FROM + " to today (default store)",
                "API 200 and completed work orders are listed",
                () -> {
                    woc.openList();
                    woc.setDates(WIDE_FROM, WorkOrderCompletion.today());
                    int s = woc.search();
                    int n = woc.lastRecordCount();
                    if (s == 200 && n == 0) return new String[]{"BLOCKED", "API 200, no Work Order Completion records exist for store '"
                            + woc.selectedText(WorkOrderCompletion.STORE) + "' since " + WIDE_FROM + " - no data"};
                    return r(s == 200 && n > 0, "API " + s + ", records=" + n + ", table rows=" + woc.tableRows(0));
                });

        tc("WOC-07", "Supplier filter", "Pick a supplier, Search", "SupplierID is sent with the search",
                () -> {
                    woc.openList();
                    woc.setDates(WIDE_FROM, WorkOrderCompletion.today());
                    String sup = woc.pickOptionAt(WorkOrderCompletion.SUPPLIER, 1);
                    String id = woc.selectedModelValue(WorkOrderCompletion.SUPPLIER);
                    int s = woc.search();
                    woc.pickOptionAt(WorkOrderCompletion.SUPPLIER, 0);
                    return r(s == 200 && woc.lastRequestBody.contains("\"SupplierID\":" + id),
                            "Supplier '" + sup + "' (id " + id + "); request " + woc.lastRequestBody + "; records=" + woc.lastRecordCount());
                });

        tc("WOC-08", "Date filter validation", "From Date later than To Date, Search",
                "Invalid range is not searched as entered (message or corrected range)",
                () -> {
                    woc.openList();
                    woc.clearCaptured();
                    woc.setDates(WorkOrderCompletion.today(), "01/01/2020");
                    woc.search();
                    List<String> a = woc.alerts();
                    boolean prevented = !woc.lastRequestBody.contains("\"ToDate\":\"01/01/2020\"");
                    return r(prevented, "Messages: " + (a.isEmpty() ? "(none)" : a) + " | request " + woc.lastRequestBody
                            + (a.isEmpty() ? " | NOTE: no message shown; To Date was changed silently" : ""));
                });

        tc("WOC-09", "Empty-state behaviour", "Search a range with no completions (01/01/2000-02/01/2000)",
                "Both tables are empty",
                () -> {
                    woc.openList();
                    woc.setDates("01/01/2000", "02/01/2000");
                    int s = woc.search();
                    return r(s == 200 && woc.tableRows(0) <= 0 && woc.tableRows(1) <= 0,
                            "API " + s + ", list rows=" + woc.tableRows(0) + ", detail rows=" + woc.tableRows(1));
                });

        tc("WOC-10", "Print without a selection", "Click Print with no WOC selected",
                "Confirm dialog asks to select a WOC; no report opens",
                () -> {
                    woc.openList();
                    woc.clickPrint();
                    String dlg = woc.confirmDialogText();
                    List<String> opened = woc.openedUrls();
                    woc.closeConfirmDialog();
                    return r(dlg.toLowerCase().contains("please select") && opened.isEmpty(), "Dialog: '" + dlg + "' | report URLs: " + opened);
                });

        tc("WOC-11", "New button / form fields", "Click New",
                "#/add-WorkOrderGrn with Store, Supplier, Work Order, Invoice No.*, Invoice Date, Save, Back",
                () -> {
                    boolean ok = woc.openNewForm();
                    boolean fields = woc.fieldPresent(WorkOrderCompletion.STORE) && woc.fieldPresent(WorkOrderCompletion.SUPPLIER)
                            && woc.fieldPresent(WorkOrderCompletion.WORK_ORDER) && woc.fieldPresent(WorkOrderCompletion.INVOICE_NO)
                            && woc.fieldPresent("WorkOrderCompletion.InvoiceDate");
                    return r(ok && fields, "At " + page.url() + " | headers " + woc.tableHeaders(0));
                });

        tc("WOC-12", "Work Order lookup", "Open the Work Order dropdown on the New form",
                "Open work orders are listed for selection",
                () -> {
                    woc.openNewForm();
                    int n = woc.optionCount(WorkOrderCompletion.WORK_ORDER);
                    if (n <= 1) return new String[]{"BLOCKED", "Work Order dropdown has only '-Select-' (" + n
                            + " option): no open work orders exist in this environment - selection, item loading and totals cannot be tested"};
                    return r(true, (n - 1) + " work orders listed");
                });

        tc("WOC-13", "Validation: Save without Store", "New form, Store = -Select-, click Save",
                "Message 'Please Select Store'; no write call",
                () -> {
                    woc.openNewForm();
                    woc.pickOptionAt(WorkOrderCompletion.STORE, 0);
                    int before = woc.blockedWrites.size();
                    woc.clickSave();
                    List<String> a = woc.waitForAlert(3000);
                    return r(a.stream().anyMatch(x -> x.toLowerCase().contains("select store")) && woc.blockedWrites.size() == before, "Messages: " + a);
                });

        tc("WOC-14", "Validation: Save without Supplier", "New form, Store set, Supplier = -Select-, click Save",
                "Message 'Please Select Supplier'; no write call",
                () -> {
                    woc.openNewForm();
                    woc.pickOptionAt(WorkOrderCompletion.SUPPLIER, 0);
                    int before = woc.blockedWrites.size();
                    woc.clickSave();
                    List<String> a = woc.waitForAlert(3000);
                    return r(a.stream().anyMatch(x -> x.toLowerCase().contains("select supplier")) && woc.blockedWrites.size() == before, "Messages: " + a);
                });

        tc("WOC-15", "Validation: Work Order / Invoice No.* missing", "Store and Supplier set, Work Order and Invoice No. empty, click Save",
                "A validation message for the missing Work Order / Invoice No.; no script error; no write call",
                () -> {
                    woc.openNewForm();
                    woc.pickOptionAt(WorkOrderCompletion.SUPPLIER, 1);
                    int errBefore = woc.consoleErrors.size(), wBefore = woc.blockedWrites.size();
                    woc.clickSave();
                    List<String> a = woc.waitForAlert(3000);
                    List<String> newErr = woc.consoleErrors.subList(errBefore, woc.consoleErrors.size());
                    boolean ok = !a.isEmpty() && newErr.isEmpty() && woc.blockedWrites.size() == wBefore;
                    return r(ok, "Messages: " + (a.isEmpty() ? "(none)" : a) + " | new console errors: " + newErr
                            + " | write attempted: " + (woc.blockedWrites.size() != wBefore));
                });

        tc("WOC-16", "Complete a work order (Save)", "Would mark a work order completed",
                "(write operation - not executed)",
                () -> new String[]{"SKIPPED", "Read-only run: completing a work order modifies data (and no open work order exists). Covered as MANUAL."});

        tc("WOC-17", "Back button / navigation", "New form, click Back", "Returns to #/WorkOrderCompletionList",
                () -> {
                    woc.openNewForm();
                    woc.clickBack();
                    return r(woc.isOnRoute(WorkOrderCompletion.LIST_ROUTE), "At " + page.url());
                });

        tc("WOC-18", "Console errors and failed API requests", "Everything logged during this run",
                "No HTTP >= 400, no console / page errors, no write call attempted",
                () -> r(woc.apiFailures.isEmpty() && woc.consoleErrors.isEmpty() && woc.blockedWrites.isEmpty(),
                        "HTTP>=400: " + woc.apiFailures + " | console: " + woc.consoleErrors + " | blocked writes: " + woc.blockedWrites
                                + " | API calls logged: " + woc.apiLog.size()));

        addSummary("Mode", "READ-ONLY (write calls aborted in the browser)");
        addSummary("Test cases", (pass + fail + skipped + blocked) + " - PASS " + pass + " / FAIL " + fail + " / SKIPPED " + skipped + " / BLOCKED " + blocked);
        addSummary("API calls logged", String.valueOf(woc.apiLog.size()));
        addSummary("Failed API responses", woc.apiFailures.isEmpty() ? "none" : String.join(" ; ", woc.apiFailures));
        addSummary("Console errors", woc.consoleErrors.isEmpty() ? "none" : String.join(" ; ", woc.consoleErrors));
        addSummary("Blocked write calls", woc.blockedWrites.isEmpty() ? "none" : String.join(" ; ", woc.blockedWrites));
    }
}
