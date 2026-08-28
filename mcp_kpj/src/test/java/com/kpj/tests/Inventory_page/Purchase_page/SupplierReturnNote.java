package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SupplierReturnNote — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Supplier Return Note</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Supplier Return Note</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>Click <b>Search Item</b> (opens the "Search" dialog).</li>
 *   <li>In the dialog: enter the <b>From Date</b>/<b>To Date</b>, click <b>Search</b>.</li>
 *   <li>In <b>Search Details</b>, tick a transaction row's checkbox.</li>
 *   <li>Its items populate <b>Item List</b>; tick each item's checkbox.</li>
 *   <li>Click <b>OK</b>.</li>
 *   <li>For each item added to the main form, enter the <b>Return Qty</b>.</li>
 *   <li>Click <b>Save</b> → verify report generation → verify the success toast.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live except for its "Search" dialog (confirmed via
 * screenshots) — see {@link com.kpj.pages.Inventory_page.Purchase_page.SupplierReturnNote} for why that
 * dialog needed its own selectors rather than reusing the shared "Item Search" picker from
 * {@link com.kpj.pages.Inventory_page.Purchase_page.ItemEnquiry}/{@link
 * com.kpj.pages.Inventory_page.Purchase_page.Quotation}/{@link
 * com.kpj.pages.Inventory_page.Purchase_page.PurchaseRequest}/{@link
 * com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrder} in the same module — it lists purchase
 * TRANSACTIONS rather than a flat item list, and selecting one populates a second grid of its own
 * items. Everything after the dialog is found by FUZZY matching, following the same atomic-JS checkbox
 * pattern and exploratory "verify report generation" check (mirrors
 * {@link com.kpj.pages.Inventory_page.Purchase_page.PurchaseOrderApproval}) used elsewhere in this
 * module. {@code describeControls()} is dumped into the report at each stage so anything still fuzzy
 * can be pinned exactly once this has run against the live screen.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=}, {@code -DreturnQty=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL supplier return note in the target environment.</p>
 */
public class SupplierReturnNote extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public SupplierReturnNote() { super("Inventory_Purchase_SupplierReturnNote"); }

    public static void main(String[] args) {
        SupplierReturnNote t = new SupplierReturnNote();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Supplier Return Note", "Inventory > Purchase > Supplier Return Note",
                "&#9888; Creates a REAL supplier return note: New, Search Item, search a transaction "
                        + "date range, select a transaction and its items, enter Return Qty, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");
        String returnQty = System.getProperty("returnQty", "1");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.SupplierReturnNote srn =
                new com.kpj.pages.Inventory_page.Purchase_page.SupplierReturnNote(page);

        // 1) Navigate
        boolean rendered = srn.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", srn.lastMenu);
        step(page, "Open Supplier Return Note screen",
                "Click Inventory -> Purchase -> Supplier Return Note",
                "The Supplier Return Note screen is shown",
                rendered ? "Opened " + page.url()
                           + (srn.lastRoute.isEmpty() ? "" : " (menu route " + srn.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        String newResult = srn.clickNew();
        boolean newOk = srn.newClicked();
        step(page, "Click New", "Click New",
                "The entry form opens",
                newOk ? "PASSES: " + newResult : "FAILS: " + newResult,
                newOk ? "PASS" : "FAIL");
        if (!newOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        addSummary("Form controls", srn.describeControls());

        // 3) Click Search Item
        String searchItem = srn.clickSearchItem();
        boolean dialogOpen = srn.dialogOpen();
        step(page, "Click Search Item", "Click Search Item",
                "The Search dialog opens",
                dialogOpen ? "PASSES: " + searchItem : "FAILS: " + searchItem,
                dialogOpen ? "PASS" : "FAIL");
        if (!dialogOpen) { addSummary("Result", "FAILED — the Search dialog never opened"); return; }

        addSummary("Search dialog controls", srn.describeControls());

        // 4) In the dialog: From Date / To Date, Search
        String dialogDates = srn.enterDialogDateRange(from, to);
        boolean dialogDatesOk = srn.dialogDatesEntered(from, to);
        step(page, "In dialog box - Search: enter From Date and To Date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                dialogDatesOk ? "PASSES: " + dialogDates : "FAILS: " + dialogDates,
                dialogDatesOk ? "PASS" : "FAIL");

        String dialogSearch = srn.clickSearchInDialog();
        boolean transactionRowsOk = srn.transactionRowsFound();
        step(page, "Click Search", "Click Search",
                "Transaction rows appear in Search Details",
                transactionRowsOk ? "PASSES: " + dialogSearch : "FAILS: " + dialogSearch,
                transactionRowsOk ? "PASS" : "FAIL");
        if (!transactionRowsOk) { addSummary("Result", "FAILED — no transaction to select"); return; }

        // 5) Tick a transaction row
        String transactionTick = srn.tickFirstTransaction();
        boolean transactionTickOk = srn.transactionTicked();
        step(page, "In Search Details, tick checkbox for the respective transaction",
                "Tick the checkbox for the transaction to be chosen",
                "The transaction is selected and its items populate Item List",
                transactionTickOk ? "PASSES: " + transactionTick : "FAILS: " + transactionTick,
                transactionTickOk ? "PASS" : "FAIL");
        if (!transactionTickOk) { addSummary("Result", "FAILED — no transaction was selected"); return; }

        addSummary("Dialog controls after ticking the transaction", srn.describeControls());
        boolean itemListOk = srn.itemListPopulated();
        step(page, "Verify Item List populates", "Observe Item List after ticking the transaction",
                "The transaction's items appear in Item List",
                itemListOk ? "PASSES: " + srn.itemListRowCount() + " item row(s)"
                           : "FAILS: 0 item rows appeared",
                itemListOk ? "PASS" : "FAIL");
        if (!itemListOk) { addSummary("Result", "FAILED — no items to return"); return; }

        // 6) Tick each item's checkbox
        String itemTick = srn.tickAllItemsInList();
        boolean itemTickOk = srn.itemsTicked();
        step(page, "In Item List, tick checkbox for the respective item(s)",
                "Tick the checkbox for each item to be chosen",
                "The item(s) are selected",
                itemTickOk ? "PASSES: " + itemTick : "FAILS: " + itemTick,
                itemTickOk ? "PASS" : "FAIL");
        if (!itemTickOk) { addSummary("Result", "FAILED — no items were selected"); return; }

        // 7) Click OK
        String ok = srn.clickOkInDialog();
        boolean okOk = srn.okClicked();
        step(page, "Click OK", "Click OK",
                "The selected items are added to the main form",
                okOk ? "PASSES: " + ok : "FAILS: " + ok,
                okOk ? "PASS" : "FAIL");
        if (!okOk) { addSummary("Result", "FAILED — OK did not confirm the selection"); return; }

        addSummary("Main form controls after OK", srn.describeControls());

        // 8) For each item, enter Return Qty
        String returnQtyResult = srn.enterReturnQtyForAllItems(returnQty);
        boolean returnQtyOk = srn.returnQtyEntered();
        step(page, "For each item, enter Return Qty", "Enter the Return Qty " + returnQty + " for each item",
                "Every item's Return Qty is entered",
                returnQtyOk ? "PASSES: " + returnQtyResult : "FAILS: " + returnQtyResult,
                returnQtyOk ? "PASS" : "FAIL");

        // 9) Save; verify report generation
        String reportResult = srn.saveAndVerifyReportGeneration();
        boolean saveClickedOk = srn.saveClicked();
        step(page, "Click Save; verify report generation", "Click Save",
                "The Save action fires and a report is generated",
                saveClickedOk ? "PASSES: " + reportResult : "FAILS: " + reportResult,
                saveClickedOk ? "PASS" : "FAIL");
        if (!saveClickedOk) { addSummary("Result", "FAILED — the Save button was not found"); return; }

        // 10) Verify success toast
        String toast = srn.waitForSaveToast();
        boolean success = com.kpj.pages.Inventory_page.Purchase_page.SupplierReturnNote.isSuccess(toast);
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + srn.lastSaveDiagnostics
                : (success ? toast : "Save not confirmed — the screen answered: \"" + toast + "\"");
        step(page, "Verify success toast message", "Wait for the success toast",
                "'... saved successfully' toast", actual, success ? "PASS" : "FAIL");

        addSummary("Transaction selected", srn.lastTransactionTick);
        addSummary("Items ticked", srn.lastItemTick);
        addSummary("Return Qty", srn.lastReturnQty);
        addSummary("Result", success ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
