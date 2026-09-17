package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PurchaseOrderApproval — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Purchase Order Approval</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Purchase Order Approval</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Click <b>Search</b>; verify a row of data exists and appears.</li>
 *   <li>Open <b>Modify</b>.</li>
 *   <li>On a row, click <b>Modify</b> to open it for editing.</li>
 *   <li>Select the <b>Delivery Place</b>.</li>
 *   <li>Enter the <b>Planned Delivery Date</b>.</li>
 *   <li>In Purchase Order Details, enter the <b>PO Quantity</b>, <b>Free Qty</b>, <b>Unit Price</b> and
 *       <b>Net Unit Purchase Price</b>.</li>
 *   <li>Click <b>Modify</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live, and the request itself is ambiguous in a few places —
 * "add new tab - modify" does not confirm a real tab exists, and "the row of data you want to select" does
 * not name its target. Both are handled the same defensive way as the sibling
 * {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequestApproval} screen: every ambiguous step
 * is a best-effort, non-fatal attempt that reports plainly what it found (or did not find) rather than
 * assuming a specific structure, and nothing that could be the real "Modify" action button is clicked
 * before the item fields are entered. See
 * {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrderApproval} for the fuzzy-matching approach
 * and the fields pinned from {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrder} (same
 * Delivery Place / Planned Delivery Date / PO Quantity / Free Qty / Unit Price / Net Unit Purchase Price
 * models, on the assumption Modify reuses the identical edit form New does).
 * {@code describeControls()} is dumped into the report at each stage so anything still fuzzy — including
 * the ambiguous steps above — can be pinned exactly once this has run.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=}, {@code -DpoQty=}, {@code -DfreeQty=},
 * {@code -DunitPrice=}, {@code -DnetUnitPrice=}, {@code -DplannedDeliveryDate=}.</p>
 *
 * <p>&#9888; A successful run MODIFIES a REAL purchase order in the target environment.</p>
 */
public class PurchaseOrderApproval extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public PurchaseOrderApproval() { super("Inventory_Purchase_PurchaseOrderApproval"); }

    public static void main(String[] args) {
        PurchaseOrderApproval t = new PurchaseOrderApproval();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Purchase - Purchase Order Approval", "Inventory > Purchase > Purchase Order Approval",
                "&#9888; MODIFIES a REAL purchase order: search by date range, open Modify on a row, "
                        + "select Delivery Place, enter Planned Delivery Date, enter PO Quantity/Free "
                        + "Qty/Unit Price/Net Unit Purchase Price, click Modify.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");
        String poQty = System.getProperty("poQty", "10");
        String freeQty = System.getProperty("freeQty", "1");
        String unitPrice = System.getProperty("unitPrice", "5.00");
        String netUnitPrice = System.getProperty("netUnitPrice", "4.50");
        String plannedDeliveryDate = System.getProperty("plannedDeliveryDate", "31/12/2026");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrderApproval poa =
                new com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrderApproval(page);

        // 1) Navigate
        boolean rendered = poa.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", poa.lastMenu);
        step(page, "Open Purchase Order Approval screen",
                "Click Inventory -> Purchase -> Purchase Order Approval",
                "The Purchase Order Approval screen is shown",
                rendered ? "Opened " + page.url()
                           + (poa.lastRoute.isEmpty() ? "" : " (menu route " + poa.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("Screen controls", poa.describeControls());

        // 2) Date range
        String dates = poa.enterDateRange(from, to);
        boolean datesOk = poa.datesEntered(from, to);
        step(page, "Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                datesOk ? "PASSES: " + dates : "FAILS: " + dates,
                datesOk ? "PASS" : "FAIL");

        // 3) Search + verify a row of data exists and appears
        String search = poa.clickSearch();
        boolean rowsOk = poa.rowsFound();
        step(page, "Click Search; verify row of data exists and appears", "Click Search",
                "A row of data appears in the list",
                rowsOk ? "PASSES: " + search : "FAILS: " + search,
                rowsOk ? "PASS" : "FAIL");
        if (!rowsOk) { addSummary("Result", "FAILED — no purchase order to modify"); return; }

        addSummary("Results", poa.describeRows());

        // ==================== [Modify] tab ====================

        // 4) "Add new tab - Modify" — reported plainly; the sibling PurchaseRequestApproval screen's
        // identical "Add a new tab - Approve PR" turned out to be just the action button, so this is not
        // assumed to be a real tab either.
        String modifyTab = poa.clickModifyTab();
        addSummary("\"Add new tab - Modify\"", modifyTab);

        // 5) On a row, click Modify
        String rowModify = poa.clickModifyOnRow();
        boolean rowModifyOk = poa.rowModifyClicked();
        step(page, "[Modify] On a row, click Modify", "Click Modify on the row you want to select",
                "The purchase order opens for editing",
                rowModifyOk ? "PASSES: " + rowModify : "FAILS: " + rowModify,
                rowModifyOk ? "PASS" : "FAIL");
        if (!rowModifyOk) { addSummary("Modify result", "FAILED — no row could be opened for Modify"); return; }

        addSummary("Controls after Modify", poa.describeControls());

        // 6) Select Delivery Place
        String deliveryPlace = poa.selectDeliveryPlace();
        boolean deliveryPlaceOk = poa.deliveryPlaceSelected();
        step(page, "[Modify] Select Delivery Place", "Select the Delivery Place",
                "A delivery place is selected",
                deliveryPlaceOk ? "PASSES: " + deliveryPlace : "FAILS: " + deliveryPlace,
                deliveryPlaceOk ? "PASS" : "FAIL");

        // 7) Enter Planned Delivery Date
        String plannedDate = poa.enterPlannedDeliveryDate(plannedDeliveryDate);
        boolean plannedDateOk = poa.plannedDeliveryDateEntered();
        step(page, "[Modify] Enter Planned Delivery Date", "Enter the Planned Delivery Date " + plannedDeliveryDate,
                "The Planned Delivery Date is entered",
                plannedDateOk ? "PASSES: " + plannedDate : "FAILS: " + plannedDate,
                plannedDateOk ? "PASS" : "FAIL");

        // 8) Purchase Order Details: PO Quantity / Free Qty / Unit Price / Net Unit Purchase Price
        String itemFields = poa.enterItemFields(poQty, freeQty, unitPrice, netUnitPrice);
        boolean itemFieldsOk = poa.itemFieldsEntered();
        step(page, "[Modify] Enter PO Quantity, Free Qty, Unit Price and Net Unit Purchase Price",
                "Enter PO Quantity " + poQty + ", Free Qty " + freeQty + ", Unit Price " + unitPrice
                        + " and Net Unit Purchase Price " + netUnitPrice,
                "All four are entered",
                itemFieldsOk ? "PASSES: " + itemFields : "FAILS — a field was not found: " + itemFields,
                itemFieldsOk ? "PASS" : "FAIL");

        // 9) Click Modify -> toast
        String toast = poa.clickModifyAndGetToast();
        boolean modifyClicked = poa.modifyClicked();
        boolean success = com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrderApproval.isSuccess(toast);
        String actual = !modifyClicked
                ? "The Modify button was not found — " + poa.lastSaveDiagnostics
                : (toast == null || toast.isEmpty()
                    ? "No message appeared — " + poa.lastSaveDiagnostics
                    : (success ? toast : "Modify not confirmed — the screen answered: \"" + toast + "\""));
        step(page, "[Modify] Click Modify & verify success toast", "Click Modify; wait for the success toast",
                "'... modified successfully' toast", actual, (modifyClicked && success) ? "PASS" : "FAIL");

        addSummary("Date range", from + " to " + to);
        addSummary("Row Modified", poa.lastRowModify);
        addSummary("Delivery Place", poa.lastDeliveryPlace);
        addSummary("Planned Delivery Date", poa.lastPlannedDeliveryDate);
        addSummary("Item fields", poa.lastItemFields);
        addSummary("Modify result", (modifyClicked && success) ? toast : "Not confirmed (\"" + toast + "\")");

        // ==================== [Approve] tab (same page, a second tab) ====================

        // 10) Re-enter the date range and search fresh for this tab.
        String approveDates = poa.enterDateRange(from, to);
        addSummary("[Approve] date range", approveDates);

        // Restrict the search to still-pending POs, if the filter exists — sidesteps needing to detect
        // "is this row already decided" per row (the fix PurchaseRequestApproval needed after ticking an
        // already-approved row silently failed there).
        String pendingFilter = poa.tickPendingPoFilter();
        addSummary("[Approve] Pending PO filter", pendingFilter);

        // 11) Click Search
        String approveSearch = poa.clickSearch();
        boolean approveRowsOk = poa.rowsFound();
        step(page, "[Approve] Click Search", "Click Search",
                "Results appear in the list",
                approveRowsOk ? "PASSES: " + approveSearch : "FAILS: " + approveSearch,
                approveRowsOk ? "PASS" : "FAIL");
        if (!approveRowsOk) { addSummary("Approve result", "FAILED — no purchase order to approve"); return; }

        addSummary("[Approve] Results", poa.describeRows());

        // 12) "Add another tab - Approve" — reported plainly; not assumed to be a real tab.
        String approveTab = poa.clickApprovePoTab();
        addSummary("\"Add another tab - Approve\"", approveTab);

        // 13) In list of purchase order, tick checkbox for Approved PO - for the respective items only
        String approveTick = poa.tickApprovedPoCheckboxes();
        boolean approveTickOk = poa.approvedPoTicked();
        step(page, "[Approve] Tick the checkbox for Approved PO",
                "Tick the Approved PO checkbox for the respective item(s)",
                "The item(s) are ticked",
                approveTickOk ? "PASSES: " + approveTick : "FAILS: " + approveTick,
                approveTickOk ? "PASS" : "FAIL");
        if (!approveTickOk) { addSummary("Approve result", "FAILED — no item was ticked"); return; }

        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get("test-output", "PurchaseOrderApproval-beforeApprove-diagnostic.png"))
                    .setFullPage(true));
        } catch (Exception e) { System.out.println("diagnostic screenshot failed: " + e.getMessage()); }

        // 14) Click Approve; verify report generation
        String reportResult = poa.clickApproveAndVerifyReportGeneration();
        boolean approveClickedOk = poa.approveClicked();
        step(page, "[Approve] Click Approve; verify report generation", "Click Approve",
                "The Approve action fires and a report is generated",
                approveClickedOk ? "PASSES: " + reportResult : "FAILS: " + reportResult,
                approveClickedOk ? "PASS" : "FAIL");
        if (!approveClickedOk) { addSummary("Approve result", "FAILED — the Approve button was not found"); return; }

        addSummary("[Approve] Controls right after clicking Approve", poa.describeControls());
        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get("test-output", "PurchaseOrderApproval-afterApprove-diagnostic.png"))
                    .setFullPage(true));
        } catch (Exception e) { System.out.println("diagnostic screenshot failed: " + e.getMessage()); }

        // 15) In dialog pop up - confirmation: click OK
        boolean confirmOpen = poa.confirmationDialogOpen();
        if (confirmOpen) addSummary("[Approve] Confirmation dialog controls", poa.describeControls());
        String confirmOk = confirmOpen ? poa.clickOkInConfirmationDialog()
                                        : "(no confirmation dialog appeared after Approve)";
        boolean confirmOkOk = confirmOpen && poa.confirmOkClicked();
        step(page, "[Approve] In dialog pop up - confirmation: click OK", "Click OK in the confirmation dialog",
                "The confirmation dialog is dismissed",
                confirmOkOk ? "PASSES: " + confirmOk : "FAILS: " + confirmOk,
                confirmOkOk ? "PASS" : "FAIL");

        // 16) Verify success toast message
        String approveToast = poa.waitForApproveToast();
        boolean approveSuccess =
                com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrderApproval.isSuccess(approveToast);
        String approveActual = approveToast == null || approveToast.isEmpty()
                ? "No message appeared — " + poa.lastApproveDiagnostics
                : (approveSuccess ? approveToast : "Approval not confirmed — the screen answered: \"" + approveToast + "\"");
        step(page, "[Approve] Verify success toast message", "Wait for the success toast",
                "'... approved successfully' toast", approveActual, approveSuccess ? "PASS" : "FAIL");

        addSummary("Approved PO ticks", poa.lastApprovedPoTick);
        addSummary("Approve result", approveSuccess ? approveToast : "Not confirmed (\"" + approveToast + "\")");
    }
}
