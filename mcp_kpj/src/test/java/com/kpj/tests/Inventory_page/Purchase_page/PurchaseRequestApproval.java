package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PurchaseRequestApproval — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Purchase Request Approval</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Purchase Request Approval</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Click <b>Search</b>; verify results appear.</li>
 *   <li><b>[Approve PR]</b> Click on a still-pending result row to open it; if a <b>Get Items</b> button
 *       is present, click it, click <b>Search</b> and verify rows; open <b>Approve PR</b>; tick the
 *       <b>Approved PR</b> checkbox for the respective item; click <b>Approve PR</b> → verify the success
 *       toast.</li>
 *   <li><b>[Reject PR]</b> Same page, a second tab: click on another still-pending result row (excluding
 *       whichever PR the Approve PR tab just processed); open <b>Reject PR</b>; tick the <b>Reject</b>
 *       checkbox for the respective item; click <b>Reject PR</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live, and the request itself is ambiguous in a few places —
 * "click on the" does not name its target, and it is unconfirmed whether "Get Items" and an "Approve
 * PR" tab literally exist here as opposed to being reused terminology from the sibling New/Quotation/
 * Purchase Request screens. Every ambiguous step is a best-effort, non-fatal attempt: it reports plainly
 * what it found (or did not find) rather than assuming a specific structure, the same way "add a new
 * tab" was handled on {@link com.kpj.pages.Inventory_page.Purchase_page.Quotation} (which turned out to
 * have no separate tab either). See
 * {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequestApproval} for the fuzzy-matching
 * approach and the proven fixes (atomic-JS checkbox ticks, the shared "Item Search" picker and its
 * blank-search fallback) reused from {@link com.kpj.pages.Inventory_page.Purchase_page.ItemEnquiry},
 * {@link com.kpj.pages.Inventory_page.Purchase_page.Quotation} and
 * {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequest} in this same module.
 * {@code describeControls()} is dumped into the report at each stage so anything still fuzzy — including
 * the ambiguous steps above — can be pinned exactly once this has run.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=}.</p>
 *
 * <p>&#9888; A successful run APPROVES one REAL purchase request and REJECTS a different one, in the
 * target environment.</p>
 */
public class PurchaseRequestApproval extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public PurchaseRequestApproval() { super("Inventory_Purchase_PurchaseRequestApproval"); }

    public static void main(String[] args) {
        PurchaseRequestApproval t = new PurchaseRequestApproval();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Purchase - Purchase Request Approval", "Inventory > Purchase > Purchase Request Approval",
                "&#9888; APPROVES one REAL purchase request and REJECTS a different one: search by date "
                        + "range, then [Approve PR] tick Approved PR for an item and Approve PR, then "
                        + "[Reject PR] tick Reject for another item and Reject PR.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequestApproval pra =
                new com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequestApproval(page);

        // 1) Navigate
        boolean rendered = pra.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", pra.lastMenu);
        step(page, "Open Purchase Request Approval screen",
                "Click Inventory -> Purchase -> Purchase Request Approval",
                "The Purchase Request Approval screen is shown",
                rendered ? "Opened " + page.url()
                           + (pra.lastRoute.isEmpty() ? "" : " (menu route " + pra.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("Screen controls", pra.describeControls());

        // 2) Date range
        String dates = pra.enterDateRange(from, to);
        boolean datesOk = pra.datesEntered(from, to);
        step(page, "Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                datesOk ? "PASSES: " + dates : "FAILS: " + dates,
                datesOk ? "PASS" : "FAIL");

        // 3) Search + verify results
        String search = pra.clickSearch();
        int rows = pra.rowCount();
        step(page, "Click Search; verify results appear", "Click Search",
                "Results appear in the list",
                rows > 0 ? "PASSES because the search returned rows: " + search
                          : "FAILS because the search returned nothing: " + search,
                rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("Result", "FAILED — no purchase request to open"); return; }

        addSummary("Results", pra.describeRows());

        // ==================== [Approve PR] tab ====================

        // 4) "Click on the [row]" — the target was not named in the request; the first pending result row
        // is opened.
        String rowClick = pra.clickFirstRow();
        boolean rowClickOk = pra.rowClicked();
        step(page, "[Approve PR] Click on the result row", "Click the first result row to open it",
                "The purchase request opens",
                rowClickOk ? "PASSES: " + rowClick : "FAILS: " + rowClick,
                rowClickOk ? "PASS" : "FAIL");
        if (!rowClickOk) { addSummary("Result", "FAILED — no row could be opened"); return; }

        addSummary("Controls after opening the row", pra.describeControls());

        // Opening a row shows a read-only "Purchase Request Status" modal (Close button only). Close it
        // so it doesn't sit over the list and block the later checkbox/Approve PR clicks.
        addSummary("Status modal", pra.closeStatusModalIfOpen());

        // 5) Get Items — only if this screen actually has one; not assumed.
        boolean hasGetItems = pra.getItemsButtonPresent();
        if (hasGetItems) {
            String getItems = pra.clickGetItems();
            boolean pickerOpen = pra.itemPickerOpen();
            step(page, "[Approve PR] In Get Items, click Get Items", "Click Get Items",
                    "The item-picker dialog opens",
                    pickerOpen ? "PASSES: " + getItems : "FAILS: " + getItems,
                    pickerOpen ? "PASS" : "FAIL");

            if (pickerOpen) {
                addSummary("Item picker controls", pra.describeControls());
                String pickerRows = pra.clickSearchAndVerifyRowsInPicker();
                boolean pickerRowsOk = pra.pickerRowsFound();
                step(page, "[Approve PR] Click Search; verify rows of data (item picker)", "Click Search",
                        "The item picker lists results",
                        pickerRowsOk ? "PASSES: " + pickerRows + "  ||  " + pra.lastPickerSearch
                                     : "FAILS: " + pickerRows + "  ||  " + pra.lastPickerSearch,
                        pickerRowsOk ? "PASS" : "FAIL");
            }
        } else {
            addSummary("Get Items", "Not present on this screen — no Get Items button was found after "
                    + "opening the row, so this step was skipped rather than forced.");
        }

        // 6) "Add a new tab - Approve PR" — reported plainly; the sibling Quotation screen's identical
        // "New Item" tab turned out to be just a button, so this is not assumed to be a real tab either.
        String approveTab = pra.clickApprovePrTab();
        addSummary("\"Add a new tab - Approve PR\"", approveTab);

        // 7) Tick Approved PR for the respective item
        String tick = pra.tickApprovedPrCheckboxes();
        boolean tickOk = pra.approvedPrTicked();
        step(page, "[Approve PR] Tick the checkbox for Approved PR",
                "Tick the Approved PR checkbox for the respective item(s)",
                "The item(s) are ticked",
                tickOk ? "PASSES: " + tick : "FAILS: " + tick,
                tickOk ? "PASS" : "FAIL");

        // 8) Click Approve PR -> toast
        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get("test-output", "PurchaseRequestApproval-beforeApprove-diagnostic.png"))
                    .setFullPage(true));
        } catch (Exception e) { System.out.println("diagnostic screenshot failed: " + e.getMessage()); }

        String toast = pra.clickApprovePrAndGetToast();
        boolean approveClicked = pra.approvePrClicked();
        boolean success = com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequestApproval.isSuccess(toast);
        String actual = !approveClicked
                ? "The Approve PR button was not found — " + pra.lastSaveDiagnostics
                : (toast == null || toast.isEmpty()
                    ? "No message appeared — " + pra.lastSaveDiagnostics
                    : (success ? toast : "Approval not confirmed — the screen answered: \"" + toast + "\""));
        step(page, "[Approve PR] Click Approve PR & verify success toast",
                "Click Approve PR; wait for the success toast",
                "'... approved successfully' toast", actual, (approveClicked && success) ? "PASS" : "FAIL");

        addSummary("Date range", from + " to " + to);
        addSummary("Approved PR ticks", pra.lastApproveTick);
        addSummary("Approve PR result", (approveClicked && success) ? toast : "Not confirmed (\"" + toast + "\")");

        // ==================== [Reject PR] tab (same page, a second tab) ====================

        // The PR the Approve PR tab just processed — excluded so Reject PR targets a different, still
        // pending PR rather than the one just approved.
        String approvedPr = rowClick.contains(": ")
                ? rowClick.substring(rowClick.indexOf(": ") + 2).trim().split("\\s+")[0] : "";

        // Confirmed live: a successful Approve PR clears the results grid back to empty (the screen
        // resets after a save), so the list must be searched again before the Reject PR tab can pick a row.
        String rejectSearch = pra.clickSearch();
        addSummary("Re-search before Reject PR tab", rejectSearch);

        // 9) "Add another tab - Reject PR"
        String rejectTab = pra.clickRejectPrTab();
        addSummary("\"Add another tab - Reject PR\"", rejectTab);

        // 10) Click on another (still-pending) result row
        String rejectRowClick = pra.clickAnotherPendingRow(approvedPr);
        boolean rejectRowClickOk = pra.rejectRowClicked();
        step(page, "[Reject PR] Click on another result row", "Click another result row to open it",
                "The purchase request opens",
                rejectRowClickOk ? "PASSES: " + rejectRowClick : "FAILS: " + rejectRowClick,
                rejectRowClickOk ? "PASS" : "FAIL");
        if (!rejectRowClickOk) { addSummary("Reject PR result", "FAILED — no row could be opened"); return; }

        addSummary("Status modal (Reject PR row)", pra.closeStatusModalIfOpen());

        // 11) Tick the Reject checkbox for the respective item
        String rejectTick = pra.tickRejectCheckbox(approvedPr);
        boolean rejectTickOk = pra.rejectTicked();
        step(page, "[Reject PR] Tick the checkbox for Reject",
                "Tick the Reject checkbox for the respective item(s)",
                "The item(s) are ticked",
                rejectTickOk ? "PASSES: " + rejectTick : "FAILS: " + rejectTick,
                rejectTickOk ? "PASS" : "FAIL");

        // 12) Click Reject PR -> toast
        String rejectToast = pra.clickRejectPrAndGetToast();
        boolean rejectClicked = pra.rejectPrClicked();
        boolean rejectSuccess =
                com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequestApproval.isSuccess(rejectToast);
        String rejectActual = !rejectClicked
                ? "The Reject PR button was not found — " + pra.lastRejectDiagnostics
                : (rejectToast == null || rejectToast.isEmpty()
                    ? "No message appeared — " + pra.lastRejectDiagnostics
                    : (rejectSuccess ? rejectToast
                        : "Rejection not confirmed — the screen answered: \"" + rejectToast + "\""));
        step(page, "[Reject PR] Click Reject PR & verify success toast",
                "Click Reject PR; wait for the success toast",
                "'... rejected successfully' toast", rejectActual,
                (rejectClicked && rejectSuccess) ? "PASS" : "FAIL");

        addSummary("Reject checkbox ticks", pra.lastRejectTick);
        addSummary("Reject PR result",
                (rejectClicked && rejectSuccess) ? rejectToast : "Not confirmed (\"" + rejectToast + "\")");
    }
}
