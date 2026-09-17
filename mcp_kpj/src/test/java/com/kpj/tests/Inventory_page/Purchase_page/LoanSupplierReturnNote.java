package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named LoanSupplierReturnNote — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Loan Supplier Return Note</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Loan Supplier Return Note</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>Select the <b>Supplier</b>.</li>
 *   <li>Click <b>Search Item</b> (opens the "Search" dialog).</li>
 *   <li>In the dialog: enter the <b>From Date</b>/<b>To Date</b>, click <b>Search</b>.</li>
 *   <li>In <b>Search Details</b>, tick the checkbox for any item to be chosen.</li>
 *   <li>In <b>Item List</b>, verify a record is shown.</li>
 *   <li>Click <b>OK</b>.</li>
 *   <li>Enter the <b>Return Qty (Pack)</b>.</li>
 *   <li>Click <b>Save</b> → verify report generation → verify the success toast.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live except for its "Search" dialog and <b>Item List</b> (which
 * arrives already ticked — confirmed via a screenshot, unlike the sibling
 * {@link com.kpj.pages.Inventory_page.Purchase_page.SupplierReturnNote} screen). See
 * {@link com.kpj.pages.Inventory_page.Purchase_page.LoanSupplierReturnNote} for the fuzzy-matching
 * approach, reusing every proven pattern from that sibling screen: the transaction-tick-until-one-has-
 * items fallback, blurring dialog date fields via JS instead of Escape, and the exploratory "verify
 * report generation" check. {@code describeControls()} is dumped into the report at each stage so
 * anything still fuzzy can be pinned exactly once this has run against the live screen.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=}, {@code -DreturnQty=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL loan supplier return note in the target environment.</p>
 */
public class LoanSupplierReturnNote extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public LoanSupplierReturnNote() { super("Inventory_Purchase_LoanSupplierReturnNote"); }

    public static void main(String[] args) {
        LoanSupplierReturnNote t = new LoanSupplierReturnNote();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Purchase - Loan Supplier Return Note", "Inventory > Purchase > Loan Supplier Return Note",
                "&#9888; Creates a REAL loan supplier return note: New, select Supplier, Search Item, "
                        + "search a transaction date range, select a transaction and its items, enter "
                        + "Return Qty (Pack), Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");
        String returnQty = System.getProperty("returnQty", "1");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.LoanSupplierReturnNote lsrn =
                new com.kpj.pages.Inventory_page.Purchase_page.LoanSupplierReturnNote(page);

        // 1) Navigate
        boolean rendered = lsrn.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", lsrn.lastMenu);
        step(page, "Open Loan Supplier Return Note screen",
                "Click Inventory -> Purchase -> Loan Supplier Return Note",
                "The Loan Supplier Return Note screen is shown",
                rendered ? "Opened " + page.url()
                           + (lsrn.lastRoute.isEmpty() ? "" : " (menu route " + lsrn.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        String newResult = lsrn.clickNew();
        boolean newOk = lsrn.newClicked();
        step(page, "Click New", "Click New",
                "The entry form opens",
                newOk ? "PASSES: " + newResult : "FAILS: " + newResult,
                newOk ? "PASS" : "FAIL");
        if (!newOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        addSummary("Form controls", lsrn.describeControls());

        // 3) Select Supplier
        String supplier = lsrn.selectSupplier();
        boolean supplierOk = lsrn.supplierSelected();
        step(page, "Select Supplier", "Select the Supplier",
                "A supplier is selected",
                supplierOk ? "PASSES: " + supplier : "FAILS: " + supplier,
                supplierOk ? "PASS" : "FAIL");

        // 4) Click Search Item
        String searchItem = lsrn.clickSearchItem();
        boolean dialogOpen = lsrn.dialogOpen();
        step(page, "Click Search Item", "Click Search Item",
                "The Search dialog opens",
                dialogOpen ? "PASSES: " + searchItem : "FAILS: " + searchItem,
                dialogOpen ? "PASS" : "FAIL");
        if (!dialogOpen) { addSummary("Result", "FAILED — the Search dialog never opened"); return; }

        addSummary("Search dialog controls", lsrn.describeControls());

        // 5) In the dialog: From Date / To Date, Search
        String dialogDates = lsrn.enterDialogDateRange(from, to);
        boolean dialogDatesOk = lsrn.dialogDatesEntered(from, to);
        step(page, "In dialog box - Search: enter From Date and To Date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                dialogDatesOk ? "PASSES: " + dialogDates : "FAILS: " + dialogDates,
                dialogDatesOk ? "PASS" : "FAIL");

        String dialogSearch = lsrn.clickSearchInDialog();
        boolean transactionRowsOk = lsrn.transactionRowsFound();
        step(page, "Click Search", "Click Search",
                "Transaction rows appear in Search Details",
                transactionRowsOk ? "PASSES: " + dialogSearch : "FAILS: " + dialogSearch,
                transactionRowsOk ? "PASS" : "FAIL");
        if (!transactionRowsOk) { addSummary("Result", "FAILED — no transaction to select"); return; }

        // 6) In Search Details, tick the checkbox for any item to be chosen
        String transactionTick = lsrn.tickFirstTransaction();
        boolean transactionTickOk = lsrn.transactionTicked();
        step(page, "In Search Details, tick the checkbox for any items to be chosen",
                "Tick the checkbox for a transaction to be chosen",
                "The transaction is selected and its items populate Item List",
                transactionTickOk ? "PASSES: " + transactionTick : "FAILS: " + transactionTick,
                transactionTickOk ? "PASS" : "FAIL");
        if (!transactionTickOk) { addSummary("Result", "FAILED — no transaction was selected"); return; }

        addSummary("Dialog controls after ticking the transaction", lsrn.describeControls());

        // 7) In Item List, verify record shown
        boolean itemListOk = lsrn.itemListPopulated();
        step(page, "In Item List, verify record shown", "Observe Item List after ticking the transaction",
                "The transaction's items appear in Item List",
                itemListOk ? "PASSES: " + lsrn.itemListRowCount() + " item row(s)"
                           : "FAILS: 0 item rows appeared",
                itemListOk ? "PASS" : "FAIL");
        if (!itemListOk) { addSummary("Result", "FAILED — no items to return"); return; }

        // Confirmed via screenshot: Item List's own checkboxes arrive already ticked once a transaction
        // is selected. Ensure they actually are (defensive, not itself a requested step) before OK.
        addSummary("Item List ticks", lsrn.ensureItemsTicked());

        // Confirmed live: a transaction can list the same item code across more than one row, and OK
        // refuses outright ("You Select Duplicate Item !!!!") if two ticked rows share a code.
        addSummary("Duplicate item codes", lsrn.deduplicateItemsByCode());

        // 8) Click OK
        String ok = lsrn.clickOkInDialog();
        boolean okOk = lsrn.okClicked();
        step(page, "Click OK", "Click OK",
                "The selected items are added to the main form",
                okOk ? "PASSES: " + ok : "FAILS: " + ok,
                okOk ? "PASS" : "FAIL");
        if (!okOk) { addSummary("Result", "FAILED — OK did not confirm the selection"); return; }

        addSummary("Main form controls after OK", lsrn.describeControls());

        // 9) Enter Return Qty (Pack)
        String returnQtyResult = lsrn.enterReturnQtyPackForAllItems(returnQty);
        boolean returnQtyOk = lsrn.returnQtyEntered();
        step(page, "Enter Return Qty (Pack)", "Enter the Return Qty (Pack) " + returnQty + " for each item",
                "Every item's Return Qty (Pack) is entered",
                returnQtyOk ? "PASSES: " + returnQtyResult : "FAILS: " + returnQtyResult,
                returnQtyOk ? "PASS" : "FAIL");

        // 10) Save; verify report generation
        String reportResult = lsrn.saveAndVerifyReportGeneration();
        boolean saveClickedOk = lsrn.saveClicked();
        step(page, "Click Save; verify report generation", "Click Save",
                "The Save action fires and a report is generated",
                saveClickedOk ? "PASSES: " + reportResult : "FAILS: " + reportResult,
                saveClickedOk ? "PASS" : "FAIL");
        if (!saveClickedOk) { addSummary("Result", "FAILED — the Save button was not found"); return; }

        // 11) Verify success toast
        String toast = lsrn.waitForSaveToast();
        boolean success = com.kpj.pages.Inventory_page.Purchase_page.LoanSupplierReturnNote.isSuccess(toast);
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + lsrn.lastSaveDiagnostics
                : (success ? toast : "Save not confirmed — the screen answered: \"" + toast + "\"");
        step(page, "Verify success toast message", "Wait for the success toast",
                "'... saved successfully' toast", actual, success ? "PASS" : "FAIL");

        addSummary("Supplier", lsrn.lastSupplier);
        addSummary("Transaction selected", lsrn.lastTransactionTick);
        addSummary("Return Qty (Pack)", lsrn.lastReturnQty);
        addSummary("Result", success ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
